/*
 * Copyright 2013 the original author or authors.
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
package org.springframework.integration.channel.registry;

import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.hamcrest.Matchers;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.integration.Message;
import org.springframework.integration.MessageChannel;
import org.springframework.integration.channel.DefaultHeaderChannelRegistry;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.MessagePublishingErrorHandler;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.core.MessagingTemplate;
import org.springframework.integration.handler.AbstractReplyProducingMessageHandler;
import org.springframework.integration.message.ErrorMessage;
import org.springframework.integration.message.GenericMessage;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * @author Gary Russell
 * @since 3.0
 *
 */
@ContextConfiguration
@RunWith(SpringJUnit4ClassRunner.class)
public class HeaderChannelRegistryTests {

	@Autowired
	MessageChannel input;

	@Autowired
	MessageChannel inputPolled;

	@Autowired
	QueueChannel alreadyAString;

	@Autowired
	TaskScheduler taskScheduler;

	@Autowired
	Gateway gatewayNoReplyChannel;

	@Autowired
	Gateway gatewayExplicitReplyChannel;

	@Test
	public void testReplace() {
		MessagingTemplate template = new MessagingTemplate();
		template.setDefaultChannel(this.input);
		Message<?> reply = template.sendAndReceive(new GenericMessage<String>("foo"));
		assertNotNull(reply);
		assertEquals("echo:foo", reply.getPayload());
	}

	@Test
	public void testReplaceGatewayWithNoReplyChannel() {
		String reply = this.gatewayNoReplyChannel.exchange("foo");
		assertNotNull(reply);
		assertEquals("echo:foo", reply);
	}

	@Test
	public void testReplaceGatewayWithExplicitReplyChannel() {
		String reply = this.gatewayExplicitReplyChannel.exchange("foo");
		assertNotNull(reply);
		assertEquals("echo:foo", reply);
	}

	/**
	 * MessagingTemplate sets the errorChannel to the replyChannel so it gets any async
	 * exceptions via the default {@link MessagePublishingErrorHandler}.
	 */
	@Test
	public void testReplaceError() {
		MessagingTemplate template = new MessagingTemplate();
		template.setDefaultChannel(this.inputPolled);
		Message<?> reply = template.sendAndReceive(new GenericMessage<String>("bar"));
		assertNotNull(reply);
		assertTrue(reply instanceof ErrorMessage);
	}

	@Test
	public void testAlreadyAString() {
		Message<String> requestMessage = MessageBuilder.withPayload("foo")
				.setReplyChannelName("alreadyAString")
				.setErrorChannelName("alreadyAnotherString")
				.build();
		this.input.send(requestMessage);
		Message<?> reply = alreadyAString.receive(0);
		assertNotNull(reply);
		assertEquals("echo:foo", reply.getPayload());
	}

	@Test
	public void testNull() {
		Message<String> requestMessage = MessageBuilder.withPayload("foo")
				.build();
		try {
			this.input.send(requestMessage);
			fail("expected exception");
		}
		catch (Exception e) {
			assertThat(e.getMessage(), Matchers.containsString("no output-channel or replyChannel"));
		}
	}

	@Test
	public void testExpire() throws Exception {
		DefaultHeaderChannelRegistry registry = new DefaultHeaderChannelRegistry(50);
		registry.setTaskScheduler(this.taskScheduler);
		registry.start();
		Thread.sleep(200);
		String id = (String) registry.channelToChannelName(new DirectChannel());
		Thread.sleep(300);
		assertNull(registry.channelNameToChannel(id));
		registry.stop();
	}

	public static class Foo extends AbstractReplyProducingMessageHandler {

		@Override
		protected Object handleRequestMessage(Message<?> requestMessage) {
			assertThat(requestMessage.getHeaders().getReplyChannel(),
					Matchers.anyOf(instanceOf(String.class), Matchers.nullValue()));
			assertThat(requestMessage.getHeaders().getErrorChannel(),
					Matchers.anyOf(instanceOf(String.class), Matchers.nullValue()));
			if (requestMessage.getPayload().equals("bar")) {
				throw new RuntimeException("intentional");
			}
			return "echo:" + requestMessage.getPayload();
		}

	}

	public interface Gateway {

		String exchange(String foo);

	}

}
