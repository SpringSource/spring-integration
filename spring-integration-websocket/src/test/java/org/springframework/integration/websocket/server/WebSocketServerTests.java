/*
 * Copyright 2014 the original author or authors.
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

package org.springframework.integration.websocket.server;

import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

import java.util.Collections;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.annotation.Transformer;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.core.MessageProducer;
import org.springframework.integration.transformer.ExpressionEvaluatingTransformer;
import org.springframework.integration.websocket.ClientWebSocketContainer;
import org.springframework.integration.websocket.IntegrationWebSocketContainer;
import org.springframework.integration.websocket.JettyWebSocketTestServer;
import org.springframework.integration.websocket.ServerWebSocketContainer;
import org.springframework.integration.websocket.inbound.WebSocketInboundChannelAdapter;
import org.springframework.integration.websocket.outbound.WebSocketOutboundMessageHandler;
import org.springframework.integration.websocket.support.SubProtocolHandlerRegistry;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.PollableChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.jetty.JettyWebSocketClient;
import org.springframework.web.socket.messaging.StompSubProtocolHandler;
import org.springframework.web.socket.messaging.SubProtocolHandler;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

/**
 * @author Artem Bilan
 * @since 4.1
 */
@ContextConfiguration(classes = WebSocketServerTests.ContextConfiguration.class)
@RunWith(SpringJUnit4ClassRunner.class)
@DirtiesContext
public class WebSocketServerTests {

	private final static SpelExpressionParser PARSER = new SpelExpressionParser();

	@Autowired
	@Qualifier("webSocketOutputChannel")
	private MessageChannel webSocketOutputChannel;

	@Autowired
	@Qualifier("webSocketInputChannel")
	private PollableChannel webSocketInputChannel;

	@Test
	public void testWebSocketOutboundMessageHandler() throws Exception {
		this.webSocketOutputChannel.send(new GenericMessage<String>("Spring"));

		Message<?> received = this.webSocketInputChannel.receive(10000);
		assertNotNull(received);
		StompHeaderAccessor stompHeaderAccessor = StompHeaderAccessor.wrap(received);
		assertEquals(StompCommand.MESSAGE.getMessageType(), stompHeaderAccessor.getMessageType());

		Object receivedPayload = received.getPayload();
		assertThat(receivedPayload, instanceOf(String.class));
		assertEquals("Hello Spring", receivedPayload);
	}


	@Configuration
	@EnableIntegration
	public static class ContextConfiguration {

		@Bean
		public JettyWebSocketTestServer server() {
			return new JettyWebSocketTestServer(ServerConfig.class);
		}

		@Bean
		public WebSocketClient webSocketClient() {
			return new SockJsClient(Collections.<Transport>singletonList(new WebSocketTransport(new JettyWebSocketClient())));
		}

		@Bean
		public IntegrationWebSocketContainer clientWebSocketContainer() {
			return new ClientWebSocketContainer(webSocketClient(), server().getWsBaseUrl() + "/ws");
		}

		@Bean
		public SubProtocolHandler stompSubProtocolHandler() {
			return new StompSubProtocolHandler();
		}

		@Bean
		public PollableChannel webSocketInputChannel() {
			return new QueueChannel();
		}

		@Bean
		public MessageChannel webSocketOutputChannel() {
			return new DirectChannel();
		}

		@Bean
		public MessageProducer webSocketInboundChannelAdapter() {
			WebSocketInboundChannelAdapter webSocketInboundChannelAdapter =
					new WebSocketInboundChannelAdapter(clientWebSocketContainer(),
							new SubProtocolHandlerRegistry(stompSubProtocolHandler()));
			webSocketInboundChannelAdapter.setOutputChannel(webSocketInputChannel());
			return webSocketInboundChannelAdapter;
		}

		@Bean
		@ServiceActivator(inputChannel = "webSocketOutputChannel")
		public MessageHandler webSocketOutboundMessageHandler() {
			return new WebSocketOutboundMessageHandler(clientWebSocketContainer(),
					new SubProtocolHandlerRegistry(stompSubProtocolHandler()));
		}

	}

	// WebSocket Server part

	@Configuration
	@EnableIntegration
	static class ServerConfig {

		@Bean
		public ServerWebSocketContainer serverWebSocketContainer() {
			return new ServerWebSocketContainer("/ws").withSockJs();
		}

		@Bean
		public SubProtocolHandler stompSubProtocolHandler() {
			return new StompSubProtocolHandler();
		}

		@Bean
		public MessageChannel webSocketInputChannel() {
			return new DirectChannel();
		}

		@Bean
		public MessageChannel webSocketOutputChannel() {
			return new DirectChannel();
		}

		@Bean
		public MessageProducer webSocketInboundChannelAdapter() {
			WebSocketInboundChannelAdapter webSocketInboundChannelAdapter =
					new WebSocketInboundChannelAdapter(serverWebSocketContainer(),
							new SubProtocolHandlerRegistry(stompSubProtocolHandler()));
			webSocketInboundChannelAdapter.setOutputChannel(webSocketInputChannel());
			return webSocketInboundChannelAdapter;
		}

		@Bean
		@Transformer(inputChannel = "webSocketInputChannel", outputChannel = "webSocketOutputChannel")
		public ExpressionEvaluatingTransformer transformer() {
			return new ExpressionEvaluatingTransformer(PARSER.parseExpression("'Hello ' + payload"));
		}

		@Bean
		@ServiceActivator(inputChannel = "webSocketOutputChannel")
		public MessageHandler webSocketOutboundMessageHandler() {
			return new WebSocketOutboundMessageHandler(serverWebSocketContainer(),
					new SubProtocolHandlerRegistry(stompSubProtocolHandler()));
		}

	}

}
