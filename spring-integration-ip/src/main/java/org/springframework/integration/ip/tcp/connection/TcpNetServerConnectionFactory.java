/*
 * Copyright 2002-2015 the original author or authors.
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
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import javax.net.ServerSocketFactory;

import org.springframework.util.Assert;

/**
 * Implements a server connection factory that produces {@link TcpNetConnection}s using
 * a {@link ServerSocket}. Must have a {@link TcpListener} registered.
 *
 * @author Gary Russell
 * @since 2.0
 *
 */
public class TcpNetServerConnectionFactory extends AbstractServerConnectionFactory {

	private volatile ServerSocket serverSocket;

	private volatile TcpSocketFactorySupport tcpSocketFactorySupport = new DefaultTcpNetSocketFactorySupport();

	/**
	 * Listens for incoming connections on the port.
	 * @param port The port.
	 */
	public TcpNetServerConnectionFactory(int port) {
		super(port);
	}

	@Override
	public String getComponentType() {
		return "tcp-net-server-connection-factory";
	}

	@Override
	public int getPort() {
		int port = super.getPort();
		ServerSocket serverSocket = this.serverSocket;
		if (port == 0 && serverSocket != null) {
			port = serverSocket.getLocalPort();
		}
		return port;
	}

	@Override
	public SocketAddress getServerSocketAddress() {
		if (this.serverSocket != null) {
			return this.serverSocket.getLocalSocketAddress();
		}
		else {
			return null;
		}
	}

	/**
	 * If no listener registers, exits.
	 * Accepts incoming connections and creates TcpConnections for each new connection.
	 * Invokes {{@link #initializeConnection(TcpConnectionSupport, Socket)} and executes the
	 * connection {@link TcpConnection#run()} using the task executor.
	 * I/O errors on the server socket/channel are logged and the factory is stopped.
	 */
	@Override
	public void run() {
		ServerSocket theServerSocket = null;
		if (getListener() == null) {
			logger.info(this + " No listener bound to server connection factory; will not read; exiting...");
			return;
		}
		try {
			if (getLocalAddress() == null) {
				theServerSocket = createServerSocket(super.getPort(), getBacklog(), null);
			}
			else {
				InetAddress whichNic = InetAddress.getByName(getLocalAddress());
				theServerSocket = createServerSocket(super.getPort(), getBacklog(), whichNic);
			}
			getTcpSocketSupport().postProcessServerSocket(theServerSocket);
			this.serverSocket = theServerSocket;
			setListening(true);
			logger.info(this + " Listening");
			while (true) {
				final Socket socket;
				/*
				 *  User hooks in the TcpSocketSupport may have set the server socket SO_TIMEOUT.
				 *  Not fatal.
				 */
				try {
					if (this.serverSocket == null) {
						if (logger.isDebugEnabled()) {
							logger.debug(this + " stopped before accept");
						}
						throw new IOException(this + " stopped before accept");
					}
					else {
						publishServerListeningEvent(getPort());
						socket = this.serverSocket.accept();
					}
				}
				catch (SocketTimeoutException ste) {
					if (logger.isDebugEnabled()) {
						logger.debug("Timed out on accept; continuing");
					}
					continue;
				}
				if (isShuttingDown()) {
					if (logger.isInfoEnabled()) {
						logger.info("New connection from " + socket.getInetAddress().getHostAddress()
								+ " rejected; the server is in the process of shutting down.");
					}
					socket.close();
				}
				else {
					if (logger.isDebugEnabled()) {
						logger.debug("Accepted connection from " + socket.getInetAddress().getHostAddress());
					}
					setSocketAttributes(socket);
					TcpConnectionSupport connection = new TcpNetConnection(socket, true, isLookupHost(),
							getApplicationEventPublisher(), getComponentName());
					connection = wrapConnection(connection);
					initializeConnection(connection, socket);
					getTaskExecutor().execute(connection);
					harvestClosedConnections();
					connection.publishConnectionOpenEvent();
				}
			}
		}
		catch (Exception e) {
			// don't log an error if we had a good socket once and now it's closed
			if (e instanceof SocketException && theServerSocket != null) {
				logger.info("Server Socket closed");
			}
			else if (isActive()) {
				logger.error("Error on ServerSocket; port = " + getPort(), e);
				publishServerExceptionEvent(e);
			}
		}
		finally {
			setListening(false);
			setActive(false);
		}
	}

	/**
	 * Create a new {@link ServerSocket}. This default implementation uses the default
	 * {@link ServerSocketFactory}. Override to use some other mechanism
	 * @param port The port.
	 * @param backlog The server socket backlog.
	 * @param whichNic An InetAddress if binding to a specific network interface. Set to
	 * null when configured to bind to all interfaces.
	 * @return The Server Socket.
	 * @throws IOException Any IOException.
	 */
	protected ServerSocket createServerSocket(int port, int backlog, InetAddress whichNic) throws IOException {
		ServerSocketFactory serverSocketFactory = this.tcpSocketFactorySupport.getServerSocketFactory();
		if (whichNic == null) {
			return serverSocketFactory.createServerSocket(port, Math.abs(backlog));
		}
		else {
			return serverSocketFactory.createServerSocket(port, Math.abs(backlog), whichNic);
		}
	}

	@Override
	public void stop() {
		if (this.serverSocket == null) {
			return;
		}
		try {
			this.serverSocket.close();
		}
		catch (IOException e) {}
		this.serverSocket = null;
		super.stop();
	}

	/**
	 * @return the serverSocket
	 */
	protected ServerSocket getServerSocket() {
		return serverSocket;
	}

	protected TcpSocketFactorySupport getTcpSocketFactorySupport() {
		return tcpSocketFactorySupport;
	}

	public void setTcpSocketFactorySupport(TcpSocketFactorySupport tcpSocketFactorySupport) {
		Assert.notNull(tcpSocketFactorySupport, "TcpSocketFactorySupport may not be null");
		this.tcpSocketFactorySupport = tcpSocketFactorySupport;
	}

}
