/*
 * Copyright 2002-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.integration.ip.tcp.connection;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.security.GeneralSecurityException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.util.Assert;

/**
 * Implementation of {@link TcpNioConnectionSupport} for SSL
 * NIO connections.
 * @author Gary Russell
 * @since 2.2
 *
 */
public class DefaultTcpNioSSLConnectionSupport implements TcpNioConnectionSupport {

	private final SSLContext sslContext;

	public DefaultTcpNioSSLConnectionSupport(TcpSSLContextSupport sslContextSupport) {
		Assert.notNull(sslContextSupport, "TcpSSLContextSupport must not be null");
		try {
			this.sslContext = sslContextSupport.getSSLContext();
		}
		catch (GeneralSecurityException e) {
			throw new IllegalArgumentException("Invalid TcpSSLContextSupport - it failed to provide an SSLContext", e);
		}
		catch (IOException e) {
			throw new IllegalArgumentException("Invalid TcpSSLContextSupport - it failed to provide an SSLContext", e);
		}
		Assert.notNull(this.sslContext, "SSLContext retrieved from context support must not be null");
	}

	/**
	 * Creates a {@link TcpNioSSLConnection}.
	 */
	@Override
	public TcpNioConnection createNewConnection(SocketChannel socketChannel, boolean server, boolean lookupHost,
			ApplicationEventPublisher applicationEventPublisher, String connectionFactoryName) throws Exception {
		SSLEngine sslEngine = this.sslContext.createSSLEngine();
		postProcessSSLEngine(sslEngine);
		TcpNioSSLConnection tcpNioSSLConnection = new TcpNioSSLConnection(socketChannel, server, lookupHost,
				applicationEventPublisher, connectionFactoryName, sslEngine);
		tcpNioSSLConnection.init();
		return tcpNioSSLConnection;
	}

	/**
	 * @deprecated without no-op, in favor of just constructor initialization
	 * @throws Exception an exception
	 */
	@Deprecated
	public void afterPropertiesSet() throws Exception {
		// NOSONAR (empty)
	}

	/**
	 * Subclasses can post-process the ssl engine (set properties).
	 * @param sslEngine the engine.
	 * @since 4.3.7
	 */
	protected void postProcessSSLEngine(SSLEngine sslEngine) {
		// NOSONAR (empty)
	}

}
