/*
 * Copyright 2002-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.integration.ip.tcp.connection;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.serializer.Deserializer;
import org.springframework.core.serializer.Serializer;
import org.springframework.integration.Message;
import org.springframework.integration.ip.IpHeaders;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.util.Assert;

/**
 * Given a list of connection factories, serves up {@link TcpConnection}s
 * that can iterate over a connection from each factory until the write
 * succeeds or the list is exhausted.
 * @author Gary Russell
 * @since 2.2
 *
 */
public class FailoverClientConnectionFactory extends AbstractClientConnectionFactory {

	private static final Log logger = LogFactory.getLog(FailoverClientConnectionFactory.class);

	private final List<AbstractClientConnectionFactory> factories;

	public FailoverClientConnectionFactory(List<AbstractClientConnectionFactory> factories) {
		super("", 0);
		Assert.notEmpty(factories, "At least one factory is required");
		this.factories = factories;
	}

	@Override
	protected void onInit() throws Exception {
		super.onInit();
		for (AbstractClientConnectionFactory factory : factories) {
			Assert.isTrue(!(this.isSingleUse() ^ factory.isSingleUse()),
				"Inconsistent singleUse - delegate factories must match this one");
		}
	}

	@Override
	public void registerListener(TcpListener listener) {
		super.registerListener(listener);
		for (AbstractClientConnectionFactory factory : this.factories) {
			// register a dummy listener; will be replaced in connection later
			factory.registerListener(new TcpListener() {
				public boolean onMessage(Message<?> message) {
					throw new UnsupportedOperationException("This should never be called");
				}
			});
		}
	}

	@Override
	public void registerSender(TcpSender sender) {
		for (AbstractClientConnectionFactory factory : this.factories) {
			factory.registerSender(sender);
		}
	}

	public void run() {
		// This component is passive.
	}

	@Override
	protected TcpConnection getOrMakeConnection() throws Exception {
		TcpConnection connection = this.getTheConnection();
		if (connection != null && connection.isOpen()) {
			return connection;
		}
		return new FailoverTcpConnection(this.factories);
	}

	@Override
	public void close() {
		for (AbstractClientConnectionFactory factory : this.factories) {
			factory.close();
		}
	}

	@Override
	public void start() {
		for (AbstractClientConnectionFactory factory : this.factories) {
			factory.start();
		}
		this.setActive(true);
	}

	@Override
	public void stop() {
		this.setActive(false);
		for (AbstractClientConnectionFactory factory : this.factories) {
			factory.stop();
		}
	}

	/**
	 * Returns true if all factories are running
	 */
	@Override
	public boolean isRunning() {
		boolean isRunning = true;
		for (AbstractClientConnectionFactory factory : this.factories) {
			isRunning = !isRunning ? false : factory.isRunning();
		}
		return isRunning;
	}

	/**
	 * Wrapper for a list of factories; delegates to a connection from
	 * one of those factories and fails over to another if necessary.
	 * @author Gary Russell
	 * @since 2.2
	 *
	 */
	private class FailoverTcpConnection implements TcpConnection, TcpListener {

		private final List<AbstractClientConnectionFactory> factories;

		private final String connectionId;

		private volatile Iterator<AbstractClientConnectionFactory> factoryIterator;

		private volatile AbstractClientConnectionFactory currentFactory;

		private volatile TcpConnection delegate;

		private volatile boolean open = true;

		public FailoverTcpConnection(List<AbstractClientConnectionFactory> factories) throws Exception {
			this.factories = factories;
			this.factoryIterator = factories.iterator();
			Assert.isTrue(this.factoryIterator.hasNext(), "At least one factory required");
			findAConnection();
			this.connectionId = UUID.randomUUID().toString();
		}

		/**
		 * Finds a connection from the underlying list of factories. If necessary,
		 * each factory is tried; including the current one if we wrap around.
		 * This allows for the condition where the current connection is closed,
		 * the current factory can serve up a new connection, but all other
		 * factories are down.
		 * @throws Exception
		 */
		private synchronized void findAConnection() throws Exception {
			boolean success = false;
			AbstractClientConnectionFactory lastFactoryToTry = this.currentFactory;
			AbstractClientConnectionFactory nextFactory = null;
			if (!this.factoryIterator.hasNext()) {
				this.factoryIterator = this.factories.iterator();
			}
			boolean retried = false;
			while (!success) {
				try {
					nextFactory = this.factoryIterator.next();
					this.delegate = nextFactory.getConnection();
					this.delegate.registerListener(this);
					this.currentFactory = nextFactory;
					success = this.delegate.isOpen();
				}
				catch (IOException e) {
					if (!this.factoryIterator.hasNext()) {
						if (retried && lastFactoryToTry == null || lastFactoryToTry == nextFactory) {
							/*
							 *  We've tried every factory including the
							 *  one the current connection was on.
							 */
							this.open = false;
							throw e;
						}
						this.factoryIterator = this.factories.iterator();
						retried = true;
					}
				}
			}
		}

		public void close() {
			this.delegate.close();
			this.open = false;
		}

		public boolean isOpen() {
			return this.open;
		}

		/**
		 * Sends to the current connection; if it fails, attempts to
		 * send to a new connection obtained from {@link #findAConnection()}.
		 * If send fails on a connection from every factory, we give up.
		 */
		public synchronized void send(Message<?> message) throws Exception {
			boolean success = false;
			AbstractClientConnectionFactory lastFactoryToTry = this.currentFactory;
			AbstractClientConnectionFactory lastFactoryTried = null;
			boolean retried = false;
			while (!success) {
				try {
					lastFactoryTried = this.currentFactory;
					this.delegate.send(message);
					success = true;
				}
				catch (IOException e) {
					if (retried && lastFactoryTried == lastFactoryToTry) {
						logger.error("All connection factories exhausted", e);
						this.open = false;
						throw e;
					}
					retried = true;
					if (logger.isDebugEnabled()) {
						logger.debug("Send to " + this.delegate.getConnectionId() + " failed; attempting failover", e);
					}
					this.delegate.close();
					findAConnection();
					if (logger.isDebugEnabled()) {
						logger.debug("Failing over to " + this.delegate.getConnectionId());
					}
				}
			}
		}

		public Object getPayload() throws Exception {
			return this.delegate.getPayload();
		}

		public void run() {
			throw new UnsupportedOperationException("Not supported on FailoverTcpConnection");
		}

		public String getHostName() {
			return this.delegate.getHostName();
		}

		public String getHostAddress() {
			return this.delegate.getHostAddress();
		}

		public int getPort() {
			return this.delegate.getPort();
		}

		public void registerListener(TcpListener listener) {
			this.delegate.registerListener(listener);
		}

		public void registerSender(TcpSender sender) {
			this.delegate.registerSender(sender);
		}

		public String getConnectionId() {
			return this.connectionId;
		}

		public void setSingleUse(boolean singleUse) {
			this.delegate.setSingleUse(singleUse);
		}

		public boolean isSingleUse() {
			return this.delegate.isSingleUse();
		}

		public boolean isServer() {
			return this.delegate.isServer();
		}

		public void setMapper(TcpMessageMapper mapper) {
			this.delegate.setMapper(mapper);
		}

		public Deserializer<?> getDeserializer() {
			return this.delegate.getDeserializer();
		}

		public void setDeserializer(Deserializer<?> deserializer) {
			this.delegate.setDeserializer(deserializer);
		}

		public Serializer<?> getSerializer() {
			return this.delegate.getSerializer();
		}

		public void setSerializer(Serializer<?> serializer) {
			this.delegate.setSerializer(serializer);
		}

		public TcpListener getListener() {
			return this.delegate.getListener();
		}

		public long incrementAndGetConnectionSequence() {
			return this.delegate.incrementAndGetConnectionSequence();
		}

		/**
		 * We have to intercept the message to replace the connectionId header with
		 * ours so the listener can correlate a response with a request. We supply
		 * the actual connectionId in another header for convenience and tracing
		 * purposes.
		 */
		public boolean onMessage(Message<?> message) {
			return FailoverClientConnectionFactory.this.getListener().onMessage(MessageBuilder.fromMessage(message)
					.setHeader(IpHeaders.CONNECTION_ID, this.getConnectionId())
					.setHeader(IpHeaders.ACTUAL_CONNECTION_ID, message.getHeaders().get(IpHeaders.CONNECTION_ID))
					.build());
		}

	}
}
