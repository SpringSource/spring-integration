/*
 * Copyright 2002-2014 the original author or authors.
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

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.integration.context.IntegrationObjectSupport;
import org.springframework.integration.core.MessageProducer;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;

/**
 * @author Mark Fisher
 * @author Iwein Fuld
 * @author Gary Russell
 * @author Artem Bilan
 */
public class MessageHandlerChainTests {

	private Message<String> message = MessageBuilder.withPayload("foo").build();

	@Mock
	private MessageChannel outputChannel = Mockito.mock(MessageChannel.class);

	{
		Mockito.when(outputChannel.send(Mockito.any(Message.class))).thenReturn(true);
		Mockito.when(outputChannel.send(Mockito.any(Message.class), Mockito.anyLong())).thenReturn(true);
	}

	@Mock
	private MessageHandler handler1 = Mockito.mock(MessageHandler.class);

	@Mock
	private MessageHandler handler2 = Mockito.mock(MessageHandler.class);

	@Mock
	private MessageHandler handler3 = Mockito.mock(MessageHandler.class);

	private ProducingHandlerStub producer1 = new ProducingHandlerStub(handler1);

	private ProducingHandlerStub producer2 = new ProducingHandlerStub(handler2);

	private ProducingHandlerStub producer3 = new ProducingHandlerStub(handler3);

	@Test
	public void chainWithOutputChannel() {
		List<MessageHandler> handlers = new ArrayList<MessageHandler>();
		handlers.add(producer1);
		handlers.add(producer2);
		handlers.add(producer3);
		MessageHandlerChain chain = new MessageHandlerChain();
		chain.setBeanName("testChain");
		chain.setHandlers(handlers);
		chain.setOutputChannel(outputChannel);
		chain.handleMessage(message);
		Mockito.verify(outputChannel).send(Mockito.eq(message), Mockito.eq(-1L));
	}

	@Test(expected = IllegalArgumentException.class)
	public void chainWithOutputChannelButLastHandlerDoesNotProduceReplies() {
		List<MessageHandler> handlers = new ArrayList<MessageHandler>();
		handlers.add(producer1);
		handlers.add(producer2);
		handlers.add(handler3);
		MessageHandlerChain chain = new MessageHandlerChain();
		chain.setBeanName("testChain");
		chain.setHandlers(handlers);
		chain.setOutputChannel(outputChannel);
		chain.afterPropertiesSet();
	}

	@Test
	public void chainWithoutOutputChannelButLastHandlerDoesNotProduceReplies() {
		List<MessageHandler> handlers = new ArrayList<MessageHandler>();
		handlers.add(producer1);
		handlers.add(producer2);
		handlers.add(handler3);
		MessageHandlerChain chain = new MessageHandlerChain();
		chain.setBeanName("testChain");
		chain.setHandlers(handlers);
		chain.handleMessage(message);
	}

	@Test
	public void chainForwardsToReplyChannel() {
		Message<String> message = MessageBuilder.withPayload("test").setReplyChannel(outputChannel).build();
		List<MessageHandler> handlers = new ArrayList<MessageHandler>();
		handlers.add(producer1);
		handlers.add(producer2);
		handlers.add(producer3);
		MessageHandlerChain chain = new MessageHandlerChain();
		chain.setBeanName("testChain");
		chain.setHandlers(handlers);
		chain.handleMessage(message);
		Mockito.verify(outputChannel).send(Mockito.any(Message.class));
	}

	@Test
	public void chainResolvesReplyChannelName() {
		Message<String> message = MessageBuilder.withPayload("test").setReplyChannelName("testChannel").build();
		DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
		beanFactory.registerSingleton("testChannel", outputChannel);
		List<MessageHandler> handlers = new ArrayList<MessageHandler>();
		handlers.add(producer1);
		handlers.add(producer2);
		handlers.add(producer3);
		MessageHandlerChain chain = new MessageHandlerChain();
		chain.setBeanName("testChain");
		chain.setHandlers(handlers);
		chain.setBeanFactory(beanFactory);
		chain.handleMessage(message);
		Mockito.verify(outputChannel).send(Mockito.eq(message));
	}

	@Test(expected = IllegalArgumentException.class) // INT-1175
	public void chainRejectsDuplicateHandlers() {
		DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
		beanFactory.registerSingleton("testChannel", outputChannel);
		List<MessageHandler> handlers = new ArrayList<MessageHandler>();
		handlers.add(producer1);
		handlers.add(producer2);
		handlers.add(producer1);
		MessageHandlerChain chain = new MessageHandlerChain();
		chain.setBeanName("testChain");
		chain.setHandlers(handlers);
		chain.setBeanFactory(beanFactory);
		chain.afterPropertiesSet();
	}

	private static class ProducingHandlerStub extends IntegrationObjectSupport implements MessageHandler, MessageProducer {

		private volatile MessageChannel output;

		private final MessageHandler messageHandler;

		public ProducingHandlerStub(MessageHandler handler) {
			this.messageHandler = handler;
		}

		public void setOutputChannel(MessageChannel channel) {
			this.output = channel;

		}

		public void handleMessage(Message<?> message) {
			messageHandler.handleMessage(message);
			output.send(message);
		}
	}

}
