/*
 * Copyright 2016-2019 the original author or authors.
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

package org.springframework.integration.channel.reactive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.mockito.Mockito;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.FluxMessageChannel;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.config.ConsumerEndpointFactoryBean;
import org.springframework.integration.endpoint.ReactiveStreamsConsumer;
import org.springframework.integration.handler.MethodInvokingMessageHandler;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.support.GenericMessage;

/**
 * @author Artem Bilan
 *
 * @since 5.0
 */
public class ReactiveStreamsConsumerTests {

	@Test
	public void testReactiveStreamsConsumerFluxMessageChannel() throws InterruptedException {
		FluxMessageChannel testChannel = new FluxMessageChannel();

		List<Message<?>> result = new LinkedList<>();
		CountDownLatch stopLatch = new CountDownLatch(2);

		MessageHandler messageHandler = m -> {
			result.add(m);
			stopLatch.countDown();
		};

		MessageHandler testSubscriber = new MethodInvokingMessageHandler(messageHandler, (String) null);
		((MethodInvokingMessageHandler) testSubscriber).setBeanFactory(mock(BeanFactory.class));
		ReactiveStreamsConsumer reactiveConsumer = new ReactiveStreamsConsumer(testChannel, testSubscriber);
		reactiveConsumer.setBeanFactory(mock(BeanFactory.class));
		reactiveConsumer.afterPropertiesSet();
		reactiveConsumer.start();

		Message<?> testMessage = new GenericMessage<>("test");
		testChannel.send(testMessage);

		reactiveConsumer.stop();

		assertThatExceptionOfType(MessageDeliveryException.class)
				.isThrownBy(() -> testChannel.send(testMessage))
				.withCauseInstanceOf(IllegalStateException.class)
				.withMessageContaining("doesn't have subscribers to accept messages");

		reactiveConsumer.start();

		Message<?> testMessage2 = new GenericMessage<>("test2");
		testChannel.send(testMessage2);

		assertThat(stopLatch.await(10, TimeUnit.SECONDS)).isTrue();
		assertThat(result).containsExactly(testMessage, testMessage2);
	}


	@Test
	public void testReactiveStreamsConsumerDirectChannel() throws InterruptedException {
		DirectChannel testChannel = new DirectChannel();

		BlockingQueue<Message<?>> messages = new LinkedBlockingQueue<>();

		Subscriber<Message<?>> testSubscriber = Mockito.spy(new Subscriber<Message<?>>() {

			@Override
			public void onSubscribe(Subscription subscription) {
				subscription.request(1);
			}

			@Override
			public void onNext(Message<?> message) {
				messages.offer(message);
			}

			@Override
			public void onError(Throwable t) {

			}

			@Override
			public void onComplete() {

			}

		});

		ReactiveStreamsConsumer reactiveConsumer = new ReactiveStreamsConsumer(testChannel, testSubscriber);
		reactiveConsumer.setBeanFactory(mock(BeanFactory.class));
		reactiveConsumer.afterPropertiesSet();
		reactiveConsumer.start();

		final Message<?> testMessage = new GenericMessage<>("test");
		testChannel.send(testMessage);

		Message<?> message = messages.poll(10, TimeUnit.SECONDS);
		assertThat(message).isSameAs(testMessage);

		reactiveConsumer.stop();

		assertThatExceptionOfType(MessageDeliveryException.class)
				.isThrownBy(() -> testChannel.send(testMessage));

		reactiveConsumer.start();

		testChannel.send(testMessage);

		message = messages.poll(10, TimeUnit.SECONDS);
		assertThat(message).isSameAs(testMessage);

		verify(testSubscriber, never()).onError(any(Throwable.class));
		verify(testSubscriber, never()).onComplete();

		assertThat(messages.isEmpty()).isTrue();

		reactiveConsumer.stop();
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testReactiveStreamsConsumerPollableChannel() throws InterruptedException {
		QueueChannel testChannel = new QueueChannel();

		BlockingQueue<Message<?>> messages = new LinkedBlockingQueue<>();

		Subscriber<Message<?>> testSubscriber = Mockito.spy(new Subscriber<Message<?>>() {

			@Override
			public void onSubscribe(Subscription subscription) {
				subscription.request(2);
			}

			@Override
			public void onNext(Message<?> message) {
				messages.offer(message);
			}

			@Override
			public void onError(Throwable t) {

			}

			@Override
			public void onComplete() {

			}

		});
		ReactiveStreamsConsumer reactiveConsumer = new ReactiveStreamsConsumer(testChannel, testSubscriber);
		reactiveConsumer.setBeanFactory(mock(BeanFactory.class));
		reactiveConsumer.afterPropertiesSet();
		reactiveConsumer.start();

		Message<?> testMessage = new GenericMessage<>("test");
		testChannel.send(testMessage);

		Message<?> message = messages.poll(10, TimeUnit.SECONDS);
		assertThat(message).isSameAs(testMessage);

		reactiveConsumer.stop();


		testChannel.send(testMessage);

		reactiveConsumer.start();

		Message<?> testMessage2 = new GenericMessage<>("test2");

		testChannel.send(testMessage2);

		message = messages.poll(10, TimeUnit.SECONDS);
		assertThat(message).isSameAs(testMessage);

		message = messages.poll(10, TimeUnit.SECONDS);
		assertThat(message).isSameAs(testMessage2);

		verify(testSubscriber, never()).onError(any(Throwable.class));
		verify(testSubscriber, never()).onComplete();

		assertThat(messages.isEmpty()).isTrue();
	}

	@Test
	public void testReactiveStreamsConsumerViaConsumerEndpointFactoryBean() throws Exception {
		FluxMessageChannel testChannel = new FluxMessageChannel();

		List<Message<?>> result = new LinkedList<>();
		CountDownLatch stopLatch = new CountDownLatch(3);

		MessageHandler messageHandler = m -> {
			result.add(m);
			stopLatch.countDown();
		};

		ConsumerEndpointFactoryBean endpointFactoryBean = new ConsumerEndpointFactoryBean();
		endpointFactoryBean.setBeanFactory(mock(ConfigurableBeanFactory.class));
		endpointFactoryBean.setInputChannel(testChannel);
		endpointFactoryBean.setHandler(messageHandler);
		endpointFactoryBean.setBeanName("reactiveConsumer");
		endpointFactoryBean.afterPropertiesSet();
		endpointFactoryBean.start();

		Message<?> testMessage = new GenericMessage<>("test");
		testChannel.send(testMessage);

		endpointFactoryBean.stop();

		assertThatExceptionOfType(MessageDeliveryException.class)
				.isThrownBy(() -> testChannel.send(testMessage))
				.withCauseInstanceOf(IllegalStateException.class)
				.withMessageContaining("doesn't have subscribers to accept messages");

		endpointFactoryBean.start();

		Message<?> testMessage2 = new GenericMessage<>("test2");

		testChannel.send(testMessage2);
		testChannel.send(testMessage2);

		assertThat(stopLatch.await(10, TimeUnit.SECONDS)).isTrue();
		assertThat(result.size()).isEqualTo(3);
		assertThat(result).containsExactly(testMessage, testMessage2, testMessage2);
	}

}
