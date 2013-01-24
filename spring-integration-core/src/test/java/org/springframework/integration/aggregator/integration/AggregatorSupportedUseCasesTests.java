/*
 * Copyright 2002-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.springframework.integration.aggregator.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import org.springframework.beans.DirectFieldAccessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.integration.aggregator.AggregatingMessageHandler;
import org.springframework.integration.aggregator.DefaultAggregatingMessageGroupProcessor;
import org.springframework.integration.aggregator.ReleaseStrategy;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.store.MessageGroup;
import org.springframework.integration.store.MessageGroupStore;
import org.springframework.integration.store.SimpleMessageStore;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.integration.test.util.TestUtils;

/**
 * @author Oleg Zhurakousky
 * @author Artem Bilan
 *
 */
public class AggregatorSupportedUseCasesTests {

	private MessageGroupStore store = new SimpleMessageStore(100);

	private DefaultAggregatingMessageGroupProcessor processor = new DefaultAggregatingMessageGroupProcessor();

	private AggregatingMessageHandler defaultHandler = new AggregatingMessageHandler(processor, store);

	{
		defaultHandler.afterPropertiesSet();
	}

	@Test
	public void waitForAllDefaultReleaseStrategyWithLateArrivals(){
		QueueChannel outputChannel = new QueueChannel();
		QueueChannel discardChannel = new QueueChannel();
		defaultHandler.setOutputChannel(outputChannel);
		defaultHandler.setDiscardChannel(discardChannel);

		for (int i = 0; i < 5; i++) {
			defaultHandler.handleMessage(MessageBuilder.withPayload(i).setSequenceSize(5).setCorrelationId("A").setSequenceNumber(i).build());
		}
		assertEquals(5, ((List<?>)outputChannel.receive(0).getPayload()).size());
		assertNull(discardChannel.receive(0));
		assertEquals(0, store.getMessageGroup("A").getMessages().size());

		// send another message with the same correlation id and see it in the discard channel
		defaultHandler.handleMessage(MessageBuilder.withPayload("foo").setSequenceSize(5).setCorrelationId("A").setSequenceNumber(3).build());
		assertNotNull(discardChannel.receive(0));

		// set 'expireGroupsUponCompletion' to 'true' and the messages should start accumulating again
		defaultHandler.setExpireGroupsUponCompletion(true);
		defaultHandler.handleMessage(MessageBuilder.withPayload("foo").setSequenceSize(5).setCorrelationId("A").setSequenceNumber(3).build());
		assertNull(discardChannel.receive(0));
		assertEquals(1, store.getMessageGroup("A").getMessages().size());
	}

	@Test
	public void waitForAllCustomReleaseStrategyWithLateArrivals(){
		QueueChannel outputChannel = new QueueChannel();
		QueueChannel discardChannel = new QueueChannel();
		defaultHandler.setOutputChannel(outputChannel);
		defaultHandler.setDiscardChannel(discardChannel);
		defaultHandler.setReleaseStrategy(new SampleSizeReleaseStrategy());

		for (int i = 0; i < 5; i++) {
			defaultHandler.handleMessage(MessageBuilder.withPayload(i).setCorrelationId("A").build());
		}
		assertEquals(5, ((List<?>)outputChannel.receive(0).getPayload()).size());
		assertNull(discardChannel.receive(0));
		assertEquals(0, store.getMessageGroup("A").getMessages().size());

		// send another message with the same correlation id and see it in the discard channel
		defaultHandler.handleMessage(MessageBuilder.withPayload("foo").setCorrelationId("A").build());
		assertNotNull(discardChannel.receive(0));

		// set 'expireGroupsUponCompletion' to 'true' and the messages should start accumulating again
		defaultHandler.setExpireGroupsUponCompletion(true);
		defaultHandler.handleMessage(MessageBuilder.withPayload("foo").setCorrelationId("A").build());
		assertNull(discardChannel.receive(0));
		assertEquals(1, store.getMessageGroup("A").getMessages().size());
	}

	@Test
	public void firstBest(){
		QueueChannel outputChannel = new QueueChannel();
		QueueChannel discardChannel = new QueueChannel();
		defaultHandler.setOutputChannel(outputChannel);
		defaultHandler.setDiscardChannel(discardChannel);
		defaultHandler.setReleaseStrategy(new FirstBestReleaseStrategy());

		for (int i = 0; i < 5; i++) {
			defaultHandler.handleMessage(MessageBuilder.withPayload(i).setCorrelationId("A").build());
		}
		assertEquals(1, ((List<?>)outputChannel.receive(0).getPayload()).size());
		assertNotNull(discardChannel.receive(0));
		assertNotNull(discardChannel.receive(0));
		assertNotNull(discardChannel.receive(0));
		assertNotNull(discardChannel.receive(0));
	}

	@Test
	public void batchingWithoutLeftovers(){
		QueueChannel outputChannel = new QueueChannel();
		QueueChannel discardChannel = new QueueChannel();
		defaultHandler.setOutputChannel(outputChannel);
		defaultHandler.setDiscardChannel(discardChannel);
		defaultHandler.setReleaseStrategy(new SampleSizeReleaseStrategy());
		defaultHandler.setExpireGroupsUponCompletion(true);

		for (int i = 0; i < 10; i++) {
			defaultHandler.handleMessage(MessageBuilder.withPayload(i).setCorrelationId("A").build());
		}
		assertEquals(5, ((List<?>)outputChannel.receive(0).getPayload()).size());
		assertEquals(5, ((List<?>)outputChannel.receive(0).getPayload()).size());
		assertNull(discardChannel.receive(0));
	}

	@Test
	public void batchingWithLeftovers(){
		QueueChannel outputChannel = new QueueChannel();
		QueueChannel discardChannel = new QueueChannel();
		defaultHandler.setOutputChannel(outputChannel);
		defaultHandler.setDiscardChannel(discardChannel);
		defaultHandler.setReleaseStrategy(new SampleSizeReleaseStrategy());
		defaultHandler.setExpireGroupsUponCompletion(true);

		for (int i = 0; i < 12; i++) {
			defaultHandler.handleMessage(MessageBuilder.withPayload(i).setCorrelationId("A").build());
		}
		assertEquals(5, ((List<?>)outputChannel.receive(0).getPayload()).size());
		assertEquals(5, ((List<?>)outputChannel.receive(0).getPayload()).size());
		assertNull(discardChannel.receive(0));
		assertEquals(2, store.getMessageGroup("A").getMessages().size());
	}

	@Test
	public void testInt2899VerifyMessageStoreCalls() throws InterruptedException {
		MessageGroupStore store = Mockito.spy(this.store);
		AggregatingMessageHandler aggregator = new AggregatingMessageHandler(processor, store);
		ApplicationContext applicationContext = TestUtils.createTestApplicationContext();
		DirectFieldAccessor directFieldAccessor = new DirectFieldAccessor(aggregator);
		directFieldAccessor.setPropertyValue("beanFactory", applicationContext);

		final CountDownLatch latchForScheduledTask = new CountDownLatch(1);
		Mockito.doAnswer(new Answer() {
			public Object answer(InvocationOnMock invocation) throws Throwable {
				latchForScheduledTask.countDown();
				return invocation.callRealMethod();
			}
		}).when(store).iterator();

		aggregator.setExpireGroupsUponCompletion(true);
		Mockito.verify(store, Mockito.never()).iterator();

		aggregator.onApplicationEvent(new ContextRefreshedEvent(applicationContext));
		assertTrue(latchForScheduledTask.await(2, TimeUnit.SECONDS));
		Mockito.verify(store, Mockito.times(1)).iterator();
	}

	private class SampleSizeReleaseStrategy implements ReleaseStrategy {

		public boolean canRelease(MessageGroup group) {
			return group.getMessages().size() == 5;
		}

	}

	private class FirstBestReleaseStrategy implements ReleaseStrategy {

		public boolean canRelease(MessageGroup group) {
			return true;
		}

	}

}
