/*
 * Copyright 2002-2016 the original author or authors.
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

package org.springframework.integration.handler;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.context.Lifecycle;
import org.springframework.integration.core.MessageProducer;
import org.springframework.integration.filter.MessageFilter;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.util.Assert;

/**
 * A composite {@link MessageHandler} implementation that invokes a chain of
 * MessageHandler instances in order.
 * <p>
 * Each of the handlers except for the last one <b>must</b> implement the
 * {@link MessageProducer} interface. The last handler must also <b>if</b>
 * the chain itself has an output channel configured. No other assumptions
 * are made about the type of handler.
 * <p>
 * It is expected that each handler will produce reply messages and send them to
 * its output channel, although this is not enforced. It is possible to filter
 * messages in the middle of the chain, for example using a
 * {@link MessageFilter}. A {@link MessageHandler} returning null will have the
 * same effect, although this option is less expressive.
 * <p>
 * This component can be used from the namespace to improve the readability of
 * the configuration by removing channels that can be created implicitly.
 *
 * <pre class="code">
 * {@code
 * <chain>
 *     <filter ref="someFilter"/>
 *     <bean class="SomeMessageHandlerImplementation"/>
 *     <transformer ref="someTransformer"/>
 *     <aggregator ... />
 * </chain>
 * }
 * </pre>
 *
 * @author Mark Fisher
 * @author Iwein Fuld
 * @author Gary Russell
 * @author Artem Bilan
 */
public class MessageHandlerChain extends AbstractMessageProducingHandler implements MessageProducer,
		CompositeMessageHandler, Lifecycle {

	private volatile List<MessageHandler> handlers;

	private volatile boolean initialized;

	private final Object initializationMonitor = new Object();

	private volatile boolean running;

	private final ReentrantLock lifecycleLock = new ReentrantLock();

	public void setHandlers(List<MessageHandler> handlers) {
		this.handlers = handlers;
	}

	@Override
	public List<MessageHandler> getHandlers() {
		return Collections.unmodifiableList(this.handlers);
	}

	@Override
	public String getComponentType() {
		return "chain";
	}

	@Override
	protected void onInit() throws Exception {
		super.onInit();
		synchronized (this.initializationMonitor) {
			if (!this.initialized) {
				Assert.notEmpty(this.handlers, "handler list must not be empty");
				this.configureChain();
				this.initialized = true;
			}
		}
	}

	@Override
	protected void handleMessageInternal(Message<?> message) throws Exception {
		if (!this.initialized) {
			this.onInit();
		}
		this.handlers.get(0).handleMessage(message);
	}

	private void configureChain() {
		Assert.isTrue(this.handlers.size() == new HashSet<MessageHandler>(this.handlers).size(),
				"duplicate handlers are not allowed in a chain");
		for (int i = 0; i < this.handlers.size(); i++) {
			MessageHandler handler = this.handlers.get(i);
			if (i < this.handlers.size() - 1) { // not the last handler
				Assert.isInstanceOf(MessageProducer.class, handler, "All handlers except for " +
						"the last one in the chain must implement the MessageProducer interface.");
				final MessageHandler nextHandler = this.handlers.get(i + 1);
				final MessageChannel nextChannel = new MessageChannel() {
					@Override
					public boolean send(Message<?> message, long timeout) {
						return this.send(message);
					}
					@Override
					public boolean send(Message<?> message) {
						nextHandler.handleMessage(message);
						return true;
					}
				};
				((MessageProducer) handler).setOutputChannel(nextChannel);

				// If this 'handler' is a nested non-last &lt;chain&gt;, it is  necessary
				// to 'force' re-init it for check its configuration in conjunction with current MessageHandlerChain.
				if (handler instanceof MessageHandlerChain) {
					((MessageHandlerChain) handler).initialized = false;
					((MessageHandlerChain) handler).afterPropertiesSet();
				}
			}
			else if (handler instanceof MessageProducer) {
				MessageChannel replyChannel = new ReplyForwardingMessageChannel();
				((MessageProducer) handler).setOutputChannel(replyChannel);
			}
			else {
				Assert.isNull(getOutputChannel(),
						"An output channel was provided, but the final handler in " +
						"the chain does not implement the MessageProducer interface.");
			}
		}
	}

	@Override
	protected boolean shouldCopyRequestHeaders() {
		return false;
	}

	/**
	 * SmartLifecycle implementation (delegates to the {@link #handlers})
	 */

	@Override
	public final boolean isRunning() {
		this.lifecycleLock.lock();
		try {
			return this.running;
		}
		finally {
			this.lifecycleLock.unlock();
		}
	}

	@Override
	public final void start() {
		this.lifecycleLock.lock();
		try {
			if (!this.running) {
				this.doStart();
				this.running = true;
				if (logger.isInfoEnabled()) {
					logger.info("started " + this);
				}
			}
		}
		finally {
			this.lifecycleLock.unlock();
		}
	}

	@Override
	public final void stop() {
		this.lifecycleLock.lock();
		try {
			if (this.running) {
				this.doStop();
				this.running = false;
				if (logger.isInfoEnabled()) {
					logger.info("stopped " + this);
				}
			}
		}
		finally {
			this.lifecycleLock.unlock();
		}
	}

	public final void stop(Runnable callback) {
		this.lifecycleLock.lock();
		try {
			this.stop();
			callback.run();
		}
		finally {
			this.lifecycleLock.unlock();
		}
	}

	private void doStop() {
		for (MessageHandler handler : this.handlers) {
			if (handler instanceof Lifecycle) {
				((Lifecycle) handler).stop();
			}
		}
	}

	private void doStart() {
		for (MessageHandler handler : this.handlers) {
			if (handler instanceof Lifecycle) {
				((Lifecycle) handler).start();
			}
		}
	}

	private final class ReplyForwardingMessageChannel implements MessageChannel {

		ReplyForwardingMessageChannel() {
			super();
		}

		@Override
		public boolean send(Message<?> message) {
			produceOutput(message, message);
			return true;
		}

		@Override
		public boolean send(Message<?> message, long timeout) {
			return send(message);
		}

	}

}
