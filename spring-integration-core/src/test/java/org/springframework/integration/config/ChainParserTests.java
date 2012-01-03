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

package org.springframework.integration.config;

import org.hamcrest.Factory;
import org.hamcrest.Matcher;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.BeansException;
import org.springframework.beans.DirectFieldAccessor;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.integration.Message;
import org.springframework.integration.MessageChannel;
import org.springframework.integration.core.PollableChannel;
import org.springframework.integration.handler.AbstractReplyProducingMessageHandler;
import org.springframework.integration.handler.MessageHandlerChain;
import org.springframework.integration.message.MessageMatcher;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.List;

import static org.junit.Assert.*;
import static org.junit.matchers.JUnitMatchers.both;
import static org.junit.matchers.JUnitMatchers.containsString;

/**
 * @author Mark Fisher
 * @author Iwein Fuld
 * @author Dave Turanski
 * @author Artem Bilan
 */
@ContextConfiguration
@RunWith(SpringJUnit4ClassRunner.class)
public class ChainParserTests {

	@Autowired
	@Qualifier("filterInput")
	private MessageChannel filterInput;

	@Autowired
	@Qualifier("pollableInput1")
	private MessageChannel pollableInput1;

	@Autowired
	@Qualifier("pollableInput2")
	private MessageChannel pollableInput2;

	@Autowired
	@Qualifier("headerEnricherInput")
	private MessageChannel headerEnricherInput;

	@Autowired
	@Qualifier("output")
	private PollableChannel output;

	@Autowired
	@Qualifier("replyOutput")
	private PollableChannel replyOutput;

	@Autowired
	@Qualifier("beanInput")
	private MessageChannel beanInput;

	@Autowired
	@Qualifier("aggregatorInput")
	private MessageChannel aggregatorInput;

	@Autowired
	private MessageChannel payloadTypeRouterInput;

	@Autowired
	private MessageChannel headerValueRouterInput;

	@Autowired
	private MessageChannel headerValueRouterWithMappingInput;

	@Autowired
	private MessageChannel loggingChannelAdapterChannel;

	@Autowired
	private MessageChannel outboundChannelAdapterChannel;

	@Autowired
	private TestConsumer testConsumer;

	@Autowired
	@Qualifier("claimCheckInput")
	private MessageChannel claimCheckInput;

	@Autowired
	@Qualifier("claimCheckOutput")
	private PollableChannel claimCheckOutput;

	@Autowired
	private PollableChannel strings;

	@Autowired
	private PollableChannel numbers;

	@Autowired
	private ApplicationContext context;

	public static Message<?> successMessage = MessageBuilder.withPayload("success").build();

	@Factory
	public static Matcher<Message<?>> sameExceptImmutableHeaders(Message<?> expected) {
		return new MessageMatcher(expected);
	}

	@Test
	public void chainWithAcceptingFilter() {
		Message<?> message = MessageBuilder.withPayload("test").build();
		this.filterInput.send(message);
		Message<?> reply = this.output.receive(0);
		assertNotNull(reply);
		assertEquals("foo", reply.getPayload());
	}

	@Test
	public void chainWithRejectingFilter() {
		Message<?> message = MessageBuilder.withPayload(123).build();
		this.filterInput.send(message);
		Message<?> reply = this.output.receive(0);
		assertNull(reply);
	}

	@Test
	public void chainWithHeaderEnricher() {
		Message<?> message = MessageBuilder.withPayload(123).build();
		this.headerEnricherInput.send(message);
		Message<?> reply = this.replyOutput.receive(0);
		assertNotNull(reply);
		assertEquals("foo", reply.getPayload());
		assertEquals("ABC", reply.getHeaders().getCorrelationId());
		assertEquals("XYZ", reply.getHeaders().get("testValue"));
		assertEquals(123, reply.getHeaders().get("testRef"));
	}

	@Test
	public void chainWithPollableInput() {
		Message<?> message = MessageBuilder.withPayload("test").build();
		this.pollableInput1.send(message);
		Message<?> reply = this.output.receive(3000);
		assertNotNull(reply);
		assertEquals("foo", reply.getPayload());
	}

	@Test
	public void chainWithPollerReference() {
		Message<?> message = MessageBuilder.withPayload("test").build();
		this.pollableInput2.send(message);
		Message<?> reply = this.output.receive(3000);
		assertNotNull(reply);
		assertEquals("foo", reply.getPayload());
	}

	@Test
	public void chainHandlerBean() throws Exception {
		Message<?> message = MessageBuilder.withPayload("test").build();
		this.beanInput.send(message);
		Message<?> reply = this.output.receive(3000);
		assertNotNull(reply);
		assertThat(reply, sameExceptImmutableHeaders(successMessage));
	}

	@SuppressWarnings("rawtypes")
	@Test
	public void chainNestingAndAggregation() throws Exception {
		Message<?> message = MessageBuilder.withPayload("test").setCorrelationId(1).setSequenceSize(1).build();
		this.aggregatorInput.send(message);
		Message reply = this.output.receive(3000);
		assertNotNull(reply);
		assertEquals("foo", reply.getPayload());
	}

	@Test
	public void chainWithPayloadTypeRouter() throws Exception {
		Message<?> message1 = MessageBuilder.withPayload("test").build();
		Message<?> message2 = MessageBuilder.withPayload(123).build();
		this.payloadTypeRouterInput.send(message1);
		this.payloadTypeRouterInput.send(message2);
		Message<?> reply1 = this.strings.receive(0);
		Message<?> reply2 = this.numbers.receive(0);
		assertNotNull(reply1);
		assertNotNull(reply2);
		assertEquals("test", reply1.getPayload());
		assertEquals(123, reply2.getPayload());
	}

	@Test // INT-2315
	public void chainWithHeaderValueRouter() throws Exception {
		Message<?> message1 = MessageBuilder.withPayload("test").setHeader("routingHeader", "strings").build();
		Message<?> message2 = MessageBuilder.withPayload(123).setHeader("routingHeader", "numbers").build();
		this.headerValueRouterInput.send(message1);
		this.headerValueRouterInput.send(message2);
		Message<?> reply1 = this.strings.receive(0);
		Message<?> reply2 = this.numbers.receive(0);
		assertNotNull(reply1);
		assertNotNull(reply2);
		assertEquals("test", reply1.getPayload());
		assertEquals(123, reply2.getPayload());
	}

	@Test // INT-2315
	public void chainWithHeaderValueRouterWithMapping() throws Exception {
		Message<?> message1 = MessageBuilder.withPayload("test").setHeader("routingHeader", "isString").build();
		Message<?> message2 = MessageBuilder.withPayload(123).setHeader("routingHeader", "isNumber").build();
		this.headerValueRouterWithMappingInput.send(message1);
		this.headerValueRouterWithMappingInput.send(message2);
		Message<?> reply1 = this.strings.receive(0);
		Message<?> reply2 = this.numbers.receive(0);
		assertNotNull(reply1);
		assertNotNull(reply2);
		assertEquals("test", reply1.getPayload());
		assertEquals(123, reply2.getPayload());
	}

	@Test // INT-1165
	public void chainWithSendTimeout() {
		Object endpoint = this.context.getBean("chainWithSendTimeout");
		MessageHandlerChain chain = (MessageHandlerChain) new DirectFieldAccessor(endpoint).getPropertyValue("handler");
		long sendTimeout = ((Long) new DirectFieldAccessor(chain).getPropertyValue("sendTimeout")).longValue();
		assertEquals(9876, sendTimeout);
	}

	@Test //INT-1622
	public void chainWithClaimChecks() {
		Message<?> message = MessageBuilder.withPayload("test").build();
		this.claimCheckInput.send(message);
		Message<?> reply = this.claimCheckOutput.receive(0);
		assertEquals(message.getPayload(), reply.getPayload());
	}

	@Test //INT-2275
	public void chainWithOutboundChannelAdapter() {
		this.outboundChannelAdapterChannel.send(successMessage);
		assertSame(successMessage, testConsumer.getLastMessage());
	}

	@Test //INT-2275
	public void chainWithLoggingChannelAdapter() {
		Message<?> message = MessageBuilder.withPayload("test").build();
		PrintStream realOut = System.out;
		try {
			OutputStream out = new ByteArrayOutputStream();
			System.setOut(new PrintStream(out));
			this.loggingChannelAdapterChannel.send(message);
			assertEquals("LoggingHandler: TEST", out.toString().trim());
		}
		finally {
			System.setOut(realOut);
		}
	}

	@Test(expected = BeanCreationException.class) //INT-2275
	public void invalidNestedChainWithLoggingChannelAdapter() {
		try {
			new ClassPathXmlApplicationContext("invalidNestedChainWithOutboundChannelAdapter-context.xml", this.getClass());
			fail("BeanCreationException is expected!");
		}
		catch (BeansException e) {
			assertEquals(IllegalArgumentException.class, e.getCause().getClass());
			assertThat(e.getMessage(), both(containsString("output channel was provided")).and(containsString("does not implement the MessageProducer")));
			throw e;
		}
	}

	public static class StubHandler extends AbstractReplyProducingMessageHandler {
		@Override
		protected Object handleRequestMessage(Message<?> requestMessage) {
			return successMessage;
		}

	}

	public static class StubAggregator {
		public String aggregate(List<String> strings) {
			return StringUtils.collectionToCommaDelimitedString(strings);
		}
	}
}
