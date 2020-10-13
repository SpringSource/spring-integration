/*
 * Copyright 2020 the original author or authors.
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

package org.springframework.integration.redis.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessagesSummary;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamInfo;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.data.redis.stream.StreamReceiver;
import org.springframework.integration.IntegrationMessageHeaderAccessor;
import org.springframework.integration.StaticMessageHeaderAccessor;
import org.springframework.integration.acks.SimpleAcknowledgment;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.integration.channel.FluxMessageChannel;
import org.springframework.integration.channel.PublishSubscribeChannel;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.MessageChannels;
import org.springframework.integration.handler.ReactiveMessageHandlerAdapter;
import org.springframework.integration.redis.outbound.ReactiveRedisStreamMessageHandler;
import org.springframework.integration.redis.rules.RedisAvailable;
import org.springframework.integration.redis.rules.RedisAvailableRule;
import org.springframework.integration.redis.rules.RedisAvailableTests;
import org.springframework.integration.redis.support.RedisHeaders;
import org.springframework.integration.redis.util.Address;
import org.springframework.integration.redis.util.Person;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.support.ErrorMessage;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * @author Attoumane Ahamadi
 * @author Artem Bilan
 * @author Rohan Mukesh
 *
 * @since 5.4
 */
@ContextConfiguration
@RunWith(SpringRunner.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class ReactiveRedisStreamMessageProducerTests extends RedisAvailableTests {

	private static final String STREAM_KEY = "myStream";

	private static final String CONSUMER = "consumer";

	@Autowired
	FluxMessageChannel fluxMessageChannel;

	@Autowired
	ReactiveRedisStreamMessageProducer reactiveRedisStreamProducer;

	@Autowired
	ReactiveRedisStreamMessageProducer reactiveErrorRedisStreamProducer;

	@Autowired
	ReactiveRedisTemplate<String, ?> template;

	@Autowired
	ReactiveMessageHandlerAdapter messageHandler;

	@Before
	public void delKey() {
		this.template.hasKey(STREAM_KEY)
				.filter(Boolean::booleanValue)
				.flatMapMany(b ->
						this.template.opsForStream()
								.groups(STREAM_KEY)
								.map(StreamInfo.XInfoGroup::groupName)
								.flatMap(groupName ->
										this.template.opsForStream()
												.destroyGroup(STREAM_KEY, groupName)))
				.blockLast();
		this.template.delete(STREAM_KEY).block();
	}

	@After
	public void tearDown() {
		this.reactiveRedisStreamProducer.stop();
		RedisAvailableRule.connectionFactory.resetConnection();
	}

	@Test
	@RedisAvailable
	public void testConsumerGroupCreation() {
		this.reactiveRedisStreamProducer.setCreateConsumerGroup(true);
		this.reactiveRedisStreamProducer.setConsumerName(CONSUMER);
		this.reactiveRedisStreamProducer.afterPropertiesSet();

		Flux.from(this.fluxMessageChannel).subscribe();

		this.reactiveRedisStreamProducer.start();

		this.template.opsForStream()
				.groups(STREAM_KEY)
				.next()
				.as(StepVerifier::create)
				.assertNext((infoGroup) ->
						assertThat(infoGroup.groupName()).isEqualTo(this.reactiveRedisStreamProducer.getBeanName()))
				.thenCancel()
				.verify(Duration.ofSeconds(10));
	}

	@Test
	@RedisAvailable
	public void testReadingMessageAsStandaloneClient() {
		Address address = new Address("Rennes 3, France");
		Person person = new Person(address, "Attoumane");
		this.messageHandler.handleMessage(new GenericMessage<>(person));

		this.reactiveRedisStreamProducer.setCreateConsumerGroup(false);
		this.reactiveRedisStreamProducer.setConsumerName(null);
		this.reactiveRedisStreamProducer.setReadOffset(ReadOffset.from("0-0"));
		this.reactiveRedisStreamProducer.afterPropertiesSet();

		StepVerifier stepVerifier =
				Flux.from(this.fluxMessageChannel)
						.as(StepVerifier::create)
						.assertNext(message -> {
							assertThat(message.getPayload()).isEqualTo(person);
							assertThat(message.getHeaders()).containsKeys(RedisHeaders.STREAM_KEY,
									RedisHeaders.STREAM_MESSAGE_ID);
						})
						.thenCancel()
						.verifyLater();

		this.reactiveRedisStreamProducer.start();

		stepVerifier.verify(Duration.ofSeconds(10));
	}

	@Test
	@RedisAvailable
	public void testReadingMessageAsConsumerInConsumerGroup() {
		Address address = new Address("Winterfell, Westeros");
		Person person = new Person(address, "John Snow");

		this.template.opsForStream()
				.createGroup(STREAM_KEY, this.reactiveRedisStreamProducer.getBeanName())
				.as(StepVerifier::create)
				.assertNext(message -> assertThat(message).isEqualTo("OK"))
				.thenCancel()
				.verify(Duration.ofSeconds(10));

		this.reactiveRedisStreamProducer.setCreateConsumerGroup(false);
		this.reactiveRedisStreamProducer.setConsumerName(CONSUMER);
		this.reactiveRedisStreamProducer.setReadOffset(ReadOffset.latest());
		this.reactiveRedisStreamProducer.afterPropertiesSet();
		this.reactiveRedisStreamProducer.start();

		StepVerifier stepVerifier =
				Flux.from(this.fluxMessageChannel)
						.as(StepVerifier::create)
						.assertNext(message -> {
							assertThat(message.getPayload()).isEqualTo(person);
							assertThat(message.getHeaders()).containsKeys(RedisHeaders.CONSUMER_GROUP, RedisHeaders.CONSUMER);
						})
						.thenCancel()
						.verifyLater();

		this.messageHandler.handleMessage(new GenericMessage<>(person));

		stepVerifier.verify(Duration.ofSeconds(10));
	}

	@Test
	@RedisAvailable
	public void testReadingPendingMessageWithNoAutoACK() {
		Address address = new Address("Winterfell, Westeros");
		Person person = new Person(address, "John Snow");

		String consumerGroup = "testGroup";
		String consumerName = "testConsumer";

		this.reactiveRedisStreamProducer.setCreateConsumerGroup(true);
		this.reactiveRedisStreamProducer.setAutoAck(false);
		this.reactiveRedisStreamProducer.setConsumerGroup(consumerGroup);
		this.reactiveRedisStreamProducer.setConsumerName(consumerName);
		this.reactiveRedisStreamProducer.setReadOffset(ReadOffset.latest());
		this.reactiveRedisStreamProducer.afterPropertiesSet();
		this.reactiveRedisStreamProducer.start();

		AtomicReference<SimpleAcknowledgment> acknowledgmentReference = new AtomicReference<>();

		StepVerifier stepVerifier =
				Flux.from(this.fluxMessageChannel)
						.as(StepVerifier::create)
						.assertNext(message -> {
							assertThat(message.getPayload()).isEqualTo(person);
							assertThat(message.getHeaders())
									.containsKeys(IntegrationMessageHeaderAccessor.ACKNOWLEDGMENT_CALLBACK);
							acknowledgmentReference.set(StaticMessageHeaderAccessor.getAcknowledgment(message));
						})
						.thenCancel()
						.verifyLater();

		this.messageHandler.handleMessage(new GenericMessage<>(person));

		stepVerifier.verify(Duration.ofSeconds(10));

		await().until(() ->
				template.opsForStream()
						.pending(STREAM_KEY, consumerGroup)
						.block(Duration.ofMillis(100))
						.getTotalPendingMessages() == 1);

		acknowledgmentReference.get().acknowledge();

		Mono<PendingMessagesSummary> pendingZeroMessage =
				template.opsForStream().pending(STREAM_KEY, consumerGroup);

		StepVerifier.create(pendingZeroMessage)
				.assertNext(pendingMessagesSummary ->
						assertThat(pendingMessagesSummary.getTotalPendingMessages()).isEqualTo(0))
				.verifyComplete();
	}

	@Test
	@RedisAvailable
	public void testReadingNextMessagesWhenSerializationException() {
		Address address = new Address("Winterfell, Westeros");
		Person person = new Person(address, "John Snow");

		String consumerGroup = "testGroup";
		String consumerName = "testConsumer";

		this.reactiveErrorRedisStreamProducer.setCreateConsumerGroup(true);
		this.reactiveErrorRedisStreamProducer.setAutoAck(false);
		this.reactiveErrorRedisStreamProducer.setConsumerGroup(consumerGroup);
		this.reactiveErrorRedisStreamProducer.setConsumerName(consumerName);
		this.reactiveErrorRedisStreamProducer.setReadOffset(ReadOffset.latest());
		this.reactiveErrorRedisStreamProducer.afterPropertiesSet();
		this.reactiveErrorRedisStreamProducer.start();

		StepVerifier stepVerifier =
				Flux.from(this.fluxMessageChannel)
					.as(StepVerifier::create)
					.expectNextCount(0)
					.thenCancel()
					.verifyLater();

		this.messageHandler.handleMessage(new GenericMessage<>(person));

		stepVerifier.verify(Duration.ofSeconds(10));

		Mono<PendingMessagesSummary> pendingZeroMessage =
				template.opsForStream().pending(STREAM_KEY, consumerGroup);

		StepVerifier.create(pendingZeroMessage)
					.assertNext(pendingMessagesSummary ->
							assertThat(pendingMessagesSummary.getTotalPendingMessages()).isEqualTo(1L))
					.verifyComplete();
	}

	@Configuration
	@EnableIntegration
	@IntegrationComponentScan
	static class ContextConfig {
		private final Log logger = LogFactory.getLog(this.getClass());

		@Bean
		ReactiveRedisStreamMessageHandler redisStreamMessageHandler() {
			return new ReactiveRedisStreamMessageHandler(RedisAvailableRule.connectionFactory, STREAM_KEY);
		}

		@Bean
		public ReactiveMessageHandlerAdapter reactiveMessageHandlerAdapter() {
			return new ReactiveMessageHandlerAdapter(redisStreamMessageHandler());
		}

		@Bean
		ReactiveRedisTemplate<String, ?> reactiveStreamOperations() {
			return new ReactiveRedisTemplate<>(RedisAvailableRule.connectionFactory,
					RedisSerializationContext.string());
		}

		@Bean
		FluxMessageChannel fluxMessageChannel() {
			return new FluxMessageChannel();
		}

		@Bean
		PublishSubscribeChannel errorChannel() {
			return MessageChannels.publishSubscribe()
									.get();
		}

		@Bean
		ReactiveRedisStreamMessageProducer reactiveErrorRedisStreamProducer() {
			Jackson2JsonRedisSerializer jacksonSerializer = new Jackson2JsonRedisSerializer(Person.class);
			final StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();

			RedisSerializationContext<String, Object> serializationContext = RedisSerializationContext
					.<String, Object>newSerializationContext()
					.key(stringRedisSerializer)
					.value(jacksonSerializer)
					.hashKey(stringRedisSerializer)
					.hashValue(jacksonSerializer)
					.build();
			ReactiveRedisStreamMessageProducer messageProducer =
					new ReactiveRedisStreamMessageProducer(RedisAvailableRule.connectionFactory, STREAM_KEY);
			messageProducer.setStreamReceiverOptions(
					StreamReceiver.StreamReceiverOptions.builder()
														.pollTimeout(Duration.ofMillis(100))
														.serializer(serializationContext)
														.build());
			messageProducer.setAutoStartup(false);
			messageProducer.setOutputChannel(fluxMessageChannel());
			messageProducer.setErrorChannel(errorChannel());
			return messageProducer;
		}

		@Bean
		ReactiveRedisStreamMessageProducer reactiveRedisStreamProducer() {
			ReactiveRedisStreamMessageProducer messageProducer =
					new ReactiveRedisStreamMessageProducer(RedisAvailableRule.connectionFactory, STREAM_KEY);
			messageProducer.setStreamReceiverOptions(
					StreamReceiver.StreamReceiverOptions.builder()
							.pollTimeout(Duration.ofMillis(100))
							.targetType(Person.class)
							.build());
			messageProducer.setAutoStartup(false);
			messageProducer.setOutputChannel(fluxMessageChannel());
			return messageProducer;
		}

		@Bean
		public IntegrationFlow errorHandlingFlow(ReactiveRedisTemplate<String, ?> reactiveStreamOperations) {
			return IntegrationFlows.from("errorChannel")
									.handle(message -> {
										reactiveStreamOperations.opsForStream()
																.acknowledge("testGroup",
																((MapRecord) ((ErrorMessage) message)
																.getOriginalMessage().getPayload()))
																.block();
										logger.info("Error Message:" + ((MessagingException) message.getPayload()).fillInStackTrace().getMessage());
									})
									.get();
		}

	}

}
