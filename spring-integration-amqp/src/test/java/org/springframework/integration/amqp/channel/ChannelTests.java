/*
 * Copyright 2014-2015 the original author or authors.
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

package org.springframework.integration.amqp.channel;

import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Collection;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.integration.amqp.config.AmqpChannelFactoryBean;
import org.springframework.integration.amqp.rule.BrokerRunning;
import org.springframework.integration.test.util.TestUtils;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * @author Gary Russell
 * @author Artem Bilan
 * @since 4.0
 *
 */
@ContextConfiguration
@RunWith(SpringJUnit4ClassRunner.class)
@DirtiesContext(classMode=ClassMode.AFTER_EACH_TEST_METHOD)
public class ChannelTests {

	@ClassRule
	public static final BrokerRunning brokerIsRunning = BrokerRunning.isRunning();

	@Autowired
	private PublishSubscribeAmqpChannel channel;

	@Autowired
	private CachingConnectionFactory factory;

	@Test
	public void pubSubLostConnectionTest() throws Exception {
		final CyclicBarrier latch = new CyclicBarrier(2);
		channel.subscribe(new MessageHandler() {

			@Override
			public void handleMessage(Message<?> message) throws MessagingException {
				try {
					latch.await(10, TimeUnit.SECONDS);
				}
				catch (Exception e) {
				}
			}
		});
		channel.send(new GenericMessage<String>("foo"));
		latch.await(10, TimeUnit.SECONDS);
		latch.reset();
		factory.destroy();
		channel.send(new GenericMessage<String>("bar"));
		latch.await(10, TimeUnit.SECONDS);
		channel.destroy();
		assertEquals(0, TestUtils.getPropertyValue(factory, "connectionListener.delegates", Collection.class).size());
	}

	@Test
	public void testAmqpChannelFactoryBean() throws Exception {
		AmqpChannelFactoryBean channelFactoryBean = new AmqpChannelFactoryBean();
		channelFactoryBean.setBeanFactory(mock(BeanFactory.class));
		channelFactoryBean.setConnectionFactory(this.factory);
		channelFactoryBean.setBeanName("testChannel");
		channelFactoryBean.afterPropertiesSet();
		AbstractAmqpChannel channel = channelFactoryBean.getObject();
		assertThat(channel, instanceOf(PointToPointSubscribableAmqpChannel.class));

		channelFactoryBean = new AmqpChannelFactoryBean();
		channelFactoryBean.setBeanFactory(mock(BeanFactory.class));
		channelFactoryBean.setConnectionFactory(this.factory);
		channelFactoryBean.setBeanName("testChannel");
		channelFactoryBean.setPubSub(true);
		channelFactoryBean.afterPropertiesSet();
		channel = channelFactoryBean.getObject();
		assertThat(channel, instanceOf(PublishSubscribeAmqpChannel.class));
	}

}
