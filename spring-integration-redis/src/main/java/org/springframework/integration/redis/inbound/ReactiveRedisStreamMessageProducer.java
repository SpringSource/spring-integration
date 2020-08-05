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

import java.time.Duration;

import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamInfo;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveStreamOperations;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.data.redis.stream.StreamReceiver;
import org.springframework.integration.IntegrationMessageHeaderAccessor;
import org.springframework.integration.endpoint.MessageProducerSupport;
import org.springframework.integration.redis.support.RedisHeaders;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import reactor.core.publisher.Flux;

/**
 * A {@link MessageProducerSupport} for Redis that reads message from a Redis Stream and publishes it to the provided
 * output channel.
 * By default this adapter reads message as a standalone client {@code XREAD} (Redis command) but can be switched to a
 * Consumer Group feature {@code XREADGROUP} by setting {@link #consumerName} field. By default the Consumer Group name
 * is the id the bean {@link #getBeanName()}
 *
 * @author Attoumane Ahamadi
 *
 * @since 5.4
 */
public class ReactiveRedisStreamMessageProducer extends MessageProducerSupport {

	private final ReactiveRedisConnectionFactory reactiveConnectionFactory;

	private final String streamKey;

	private ReactiveRedisTemplate<String, ?> reactiveRedisTemplate;

	private ReactiveStreamOperations<String, ?, ?> reactiveStreamOperations;

	private StreamReceiver.StreamReceiverOptions<String, ?> streamReceiverOptions = StreamReceiver
			.StreamReceiverOptions.builder().pollTimeout(Duration.ZERO).build();

	private StreamReceiver<String, ?> streamReceiver;

	private ReadOffset readOffset = ReadOffset.lastConsumed();

	private boolean extractPayload = true;

	private boolean autoAck = true;

	@Nullable
	private String consumerGroup;

	@Nullable
	private String consumerName;

	private boolean createConsumerGroup;

	public ReactiveRedisStreamMessageProducer(ReactiveRedisConnectionFactory reactiveConnectionFactory,
			String streamKey) {
		Assert.notNull(reactiveConnectionFactory, "'connectionFactory' must not be null");
		Assert.hasText(streamKey, "'streamKey' must be set");
		this.reactiveConnectionFactory = reactiveConnectionFactory;
		this.streamKey = streamKey;
	}

	/**
	 * Define the offset from which we want to read message. By defaut the {@link ReadOffset#lastConsumed()} is used.
	 * @param readOffset the desired offset
	 */
	public void setReadOffset(ReadOffset readOffset) {
		this.readOffset = readOffset;
	}

	/**
	 * Configure this channel adapter to extract or not the message payload.
	 * @param extractPayload default true
	 */
	public void setExtractPayload(boolean extractPayload) {
		this.extractPayload = extractPayload;
	}

	/**
	 * Set whether or not acknowledge message read in the Consumer Group. {@code true} by default.
	 * @param autoAck the acknowledge option.
	 */
	public void setAutoAck(boolean autoAck) {
		this.autoAck = autoAck;
	}

	/**
	 * Set the name of the Consumer Group. It is possible to create that Consumer Group if desired, see:
	 * {@link #createConsumerGroup}. If not set, the defined bean name {@link #getBeanName()} is used.
	 * @param consumerGroup the Consumer Group on which this adapter should register to listen messages.
	 */
	public void setConsumerGroup(@Nullable String consumerGroup) {
		this.consumerGroup = consumerGroup;
	}

	/**
	 * Set the name of the consumer. When a consumer name is provided, this adapter is switched to the Consumer Group
	 * feature. Note that this value should be unique in the group
	 * @param consumerName the consumer name in the Consumer Group
	 */
	public void setConsumerName(@Nullable String consumerName) {
		this.consumerName = consumerName;
	}

	/**
	 * Create the Consumer Group if and only if it does not exist. During the
	 * creation we also create the stream, see {@code MKSTREAM}.
	 * @param createConsumerGroup specify if we should create the Consumer Group, {@code false} by default
	 */
	public void setCreateConsumerGroup(boolean createConsumerGroup) {
		this.createConsumerGroup = createConsumerGroup;
	}

	/**
	 * Set {@link StreamOperations} used to customize the {@link StreamReceiver}.
	 * It provides a way to set the polling timeout and the serialization context.
	 * By default the polling timeout is set to infinite and {@link StringRedisSerializer} is used.
	 * @param streamReceiverOptions the desired receiver options
	 * */
	public void setStreamReceiverOptions(@Nullable StreamReceiver.StreamReceiverOptions<String, ?> streamReceiverOptions) {
		this.streamReceiverOptions = streamReceiverOptions;
	}

	@Override
	public String getComponentType() {
		return "redis:stream-inbound-channel-adapter";
	}

	@Override
	protected void onInit() {
		super.onInit();
		this.streamReceiver = StreamReceiver.create(this.reactiveConnectionFactory, this.streamReceiverOptions);
		if (StringUtils.hasText(this.consumerName) && StringUtils.isEmpty(this.consumerGroup)) {
			this.consumerGroup = getBeanName();
		}
		this.reactiveRedisTemplate = new ReactiveRedisTemplate<>(this.reactiveConnectionFactory,
				RedisSerializationContext.string());
		this.reactiveStreamOperations = this.reactiveRedisTemplate.opsForStream();
	}

	@Override
	protected void doStart() {
		super.doStart();

		StreamOffset<String> offset = StreamOffset.create(this.streamKey, this.readOffset);

		Flux<Message<?>> messageFlux;

		if (StringUtils.isEmpty(this.consumerName)) {
			messageFlux = this.streamReceiver
					.receive(offset)
					.map(event -> getMessageBuilderFactory().withPayload(this.extractPayload ? event.getValue() : event)
							.setHeader(RedisHeaders.STREAM_KEY, event.getStream())
							.setHeader(RedisHeaders.STREAM_MESSAGE_ID, event.getId())
							.build());
		}
		else {
			Assert.notNull(this.consumerGroup, "'consumerGroup' must not be null");

			if (this.createConsumerGroup) {
				Assert.hasText(this.consumerName, "'consumerName' must be set");
				//We check that the group does not already exist before creating it
				this.reactiveRedisTemplate.hasKey(this.streamKey)
						.subscribe(keyExists -> {
							if (!keyExists) {
								this.reactiveStreamOperations.createGroup(this.streamKey, this.consumerGroup)
										.subscribe();
							}
							else {
								this.reactiveStreamOperations.groups(this.streamKey)
										.map(StreamInfo.XInfoGroup::groupName)
										.filter(groupName -> groupName.equals(this.consumerGroup))
										.switchIfEmpty(this.reactiveStreamOperations.createGroup(this.streamKey,
												this.consumerGroup))
										.subscribe();
							}
						});
			}

			Consumer consumer = Consumer.from(this.consumerGroup, this.consumerName);

			messageFlux = this.streamReceiver
					.receive(consumer, offset)
					.map(event -> {
						Message<?> message = getMessageBuilderFactory().withPayload(this.extractPayload ? event.getValue() : event)
								.setHeader(RedisHeaders.STREAM_KEY, event.getStream())
								.setHeader(RedisHeaders.STREAM_MESSAGE_ID, event.getId())
								.setHeader(RedisHeaders.CONSUMER_GROUP, this.consumerGroup)
								.setHeader(RedisHeaders.CONSUMER, this.consumerName)
								.build();
						if (this.autoAck) {
							message.getHeaders().put(IntegrationMessageHeaderAccessor.ACKNOWLEDGMENT_CALLBACK,
									this.reactiveStreamOperations.acknowledge(this.consumerGroup, event).subscribe());
						}
						return message;
					});
		}

		subscribeToPublisher(messageFlux);
	}
}
