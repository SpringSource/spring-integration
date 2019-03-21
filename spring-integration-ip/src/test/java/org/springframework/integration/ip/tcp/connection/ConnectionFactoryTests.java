/*
 * Copyright 2002-2013 the original author or authors.
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

import static org.hamcrest.Matchers.hasItem;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.contains;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import org.springframework.beans.DirectFieldAccessor;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.integration.ip.tcp.TcpReceivingChannelAdapter;
import org.springframework.integration.ip.util.TestingUtilities;
import org.springframework.integration.test.util.SocketUtils;
import org.springframework.integration.test.util.TestUtils;
import org.springframework.messaging.Message;

/**
 * @author Gary Russell
 * @author Artem Bilan
 * @since 3.0
 *
 */
public class ConnectionFactoryTests {

	@Test
	public void testObtainConnectionIds() throws Exception {
		final List<TcpConnectionEvent> events =
				Collections.synchronizedList(new ArrayList<TcpConnectionEvent>());
		ApplicationEventPublisher publisher = new ApplicationEventPublisher() {
			public void publishEvent(ApplicationEvent event) {
				events.add((TcpConnectionEvent) event);
			}
		};
		int port = SocketUtils.findAvailableServerSocket();
		TcpNetServerConnectionFactory serverFactory = new TcpNetServerConnectionFactory(port);
		serverFactory.setBeanName("serverFactory");
		serverFactory.setApplicationEventPublisher(publisher);
		serverFactory = spy(serverFactory);
		final CountDownLatch serverConnectionInitLatch = new CountDownLatch(1);
		doAnswer(new Answer<Object>() {
			public Object answer(InvocationOnMock invocation) throws Throwable {
				Object result = invocation.callRealMethod();
				serverConnectionInitLatch.countDown();
				return result;
			}
		}).when(serverFactory).wrapConnection(any(TcpConnectionSupport.class));
		TcpReceivingChannelAdapter adapter = new TcpReceivingChannelAdapter();
		adapter.setConnectionFactory(serverFactory);
		adapter.start();
		TestingUtilities.waitListening(serverFactory, null);
		TcpNetClientConnectionFactory clientFactory = new TcpNetClientConnectionFactory("localhost", port);
		clientFactory.registerListener(new TcpListener() {
			public boolean onMessage(Message<?> message) {
				return false;
			}
		});
		clientFactory.setBeanName("clientFactory");
		clientFactory.setApplicationEventPublisher(publisher);
		clientFactory.start();
		TcpConnectionSupport client = clientFactory.getConnection();
		List<String> clients = clientFactory.getOpenConnectionIds();
		assertEquals(1, clients.size());
		assertTrue(clients.contains(client.getConnectionId()));
		assertTrue("Server connection failed to register", serverConnectionInitLatch.await(1, TimeUnit.SECONDS));
		List<String> servers = serverFactory.getOpenConnectionIds();
		assertEquals(1, servers.size());
		assertTrue(serverFactory.closeConnection(servers.get(0)));
		servers = serverFactory.getOpenConnectionIds();
		assertEquals(0, servers.size());
		Thread.sleep(1000);
		clients = clientFactory.getOpenConnectionIds();
		assertEquals(0, clients.size());
		assertEquals(6, events.size()); // OPEN, CLOSE, EXCEPTION for each side

		FooEvent event = new FooEvent(client, "foo");
		client.publishEvent(event);
		assertEquals(7, events.size());

		try {
			event = new FooEvent(mock(TcpConnectionSupport.class), "foo");
			client.publishEvent(event);
			fail("Expected exception");
		}
		catch (IllegalArgumentException e) {
			assertTrue("Can only publish events with this as the source".equals(e.getMessage()));
		}
	}

	@Test
	public void testEarlyCloseNet() throws Exception {
		AbstractServerConnectionFactory factory = new TcpNetServerConnectionFactory(0);
		testEarlyClose(factory, "serverSocket", " stopped before accept");
	}

	@Test
	public void testEarlyCloseNio() throws Exception {
		AbstractServerConnectionFactory factory = new TcpNioServerConnectionFactory(0);
		testEarlyClose(factory, "serverChannel", " stopped before registering the server channel");
	}

	private void testEarlyClose(final AbstractServerConnectionFactory factory, String property,
			String message) throws Exception {
		factory.setApplicationEventPublisher(mock(ApplicationEventPublisher.class));
		factory.setBeanName("foo");
		factory.registerListener(mock(TcpListener.class));
		factory.setBeanFactory(mock(BeanFactory.class));
		factory.afterPropertiesSet();
		Log logger = spy(TestUtils.getPropertyValue(factory, "logger", Log.class));
		new DirectFieldAccessor(factory).setPropertyValue("logger", logger);
		final CountDownLatch latch1 = new CountDownLatch(1);
		final CountDownLatch latch2 = new CountDownLatch(1);
		final CountDownLatch latch3 = new CountDownLatch(1);
		when(logger.isInfoEnabled()).thenReturn(true);
		when(logger.isDebugEnabled()).thenReturn(true);
		doAnswer(new Answer<Void>() {

			@Override
			public Void answer(InvocationOnMock invocation) throws Throwable {
				latch1.countDown();
				// wait until the stop nulls the channel
				latch2.await(10, TimeUnit.SECONDS);
				return null;
			}
		}).when(logger).info(contains("Listening"));
		doAnswer(new Answer<Void>() {

			@Override
			public Void answer(InvocationOnMock invocation) throws Throwable {
				latch3.countDown();
				return null;
			}
		}).when(logger).debug(contains(message));
		factory.start();
		assertTrue("missing info log", latch1.await(10, TimeUnit.SECONDS));
		// stop on a different thread because it waits for the executor
		Executors.newSingleThreadExecutor().execute(new Runnable() {

			@Override
			public void run() {
				factory.stop();
			}
		});
		int n = 0;
		DirectFieldAccessor accessor = new DirectFieldAccessor(factory);
		while (n++ < 200 && accessor.getPropertyValue(property) != null) {
			Thread.sleep(100);
		}
		assertTrue("Stop was not invoked in time", n < 200);
		latch2.countDown();
		assertTrue("missing debug log", latch3.await(10, TimeUnit.SECONDS));
		String expected = "foo, port=" + factory.getPort() + message;
		ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
		verify(logger, atLeast(1)).debug(captor.capture());
		assertThat(captor.getAllValues(), hasItem(expected));
		factory.stop();
	}

	@SuppressWarnings("serial")
	private class FooEvent extends TcpConnectionOpenEvent {

		public FooEvent(TcpConnectionSupport connection, String connectionFactoryName) {
			super(connection, connectionFactoryName);
		}

	}

}
