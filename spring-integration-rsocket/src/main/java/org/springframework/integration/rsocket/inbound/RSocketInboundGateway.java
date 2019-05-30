/*
 * Copyright 2019 the original author or authors.
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

package org.springframework.integration.rsocket.inbound;

import java.util.Arrays;

import org.reactivestreams.Publisher;

import org.springframework.core.ReactiveAdapter;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.CharSequenceEncoder;
import org.springframework.core.codec.Decoder;
import org.springframework.core.codec.Encoder;
import org.springframework.core.codec.StringDecoder;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.core.io.buffer.NettyDataBuffer;
import org.springframework.integration.gateway.MessagingGatewaySupport;
import org.springframework.integration.rsocket.AbstractRSocketConnector;
import org.springframework.integration.rsocket.ClientRSocketConnector;
import org.springframework.integration.rsocket.IntegrationRSocketEndpoint;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.handler.invocation.reactive.HandlerMethodReturnValueHandler;
import org.springframework.messaging.rsocket.RSocketPayloadReturnValueHandler;
import org.springframework.messaging.rsocket.RSocketStrategies;
import org.springframework.util.Assert;
import org.springframework.util.MimeType;

import io.rsocket.Payload;
import io.rsocket.util.ByteBufPayload;
import io.rsocket.util.DefaultPayload;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoProcessor;

/**
 * The {@link MessagingGatewaySupport} implementation for the {@link IntegrationRSocketEndpoint}.
 * Represents an inbound endpoint for RSocket requests.
 * <p>
 * May be configured with the {@link AbstractRSocketConnector} for mapping registration.
 * Or existing {@link AbstractRSocketConnector} bean(s) will perform detection automatically.
 * <p>
 * An inbound {@link DataBuffer} (either single or as a {@link Publisher} element) is
 * converted to the target expected type which can be configured by the
 * {@link #setRequestElementClass} or {@link #setRequestElementType(ResolvableType)}.
 * If it is not configured, then target type is determined by the {@code contentType} header:
 * If it is a {@code text}, then target type is {@link String}, otherwise - {@code byte[]}.
 * <p>
 * An inbound {@link Publisher} is used as is in the message to send payload.
 * It is a target application responsibility to process that payload any possible way.
 * <p>
 * A reply payload is encoded to the {@link Flux} according a type of the payload or a
 * {@link Publisher} element type.
 *
 * @author Artem Bilan
 *
 * @since 5.2
 */
public class RSocketInboundGateway extends MessagingGatewaySupport implements IntegrationRSocketEndpoint {

	private final String[] path;

	private RSocketStrategies rsocketStrategies =
			RSocketStrategies.builder()
					.decoder(StringDecoder.allMimeTypes())
					.encoder(CharSequenceEncoder.allMimeTypes())
					.dataBufferFactory(new DefaultDataBufferFactory())
					.build();

	@Nullable
	private AbstractRSocketConnector rsocketConnector;

	@Nullable
	private ResolvableType requestElementType;

	/**
	 * Instantiate based on the provided Ant-style path patterns to map this endpoint for incoming RSocket requests.
	 * @param pathArg the mapping patterns to use.
	 */
	public RSocketInboundGateway(String... pathArg) {
		Assert.notNull(pathArg, "'pathArg' must not be null");
		this.path = Arrays.copyOf(pathArg, pathArg.length);
	}

	/**
	 * Configure {@link RSocketStrategies} instead of a default one.
	 * Note: if {@link AbstractRSocketConnector} ias provided, then its
	 * {@link RSocketStrategies} have a precedence.
	 * @param rsocketStrategies the {@link RSocketStrategies} to use.
	 * @see RSocketStrategies#builder
	 */
	public void setRSocketStrategies(RSocketStrategies rsocketStrategies) {
		Assert.notNull(rsocketStrategies, "'rsocketStrategies' must not be null");
		this.rsocketStrategies = rsocketStrategies;
	}

	/**
	 * Provide an {@link AbstractRSocketConnector} reference for an explicit endpoint mapping.
	 * @param rsocketConnector the {@link AbstractRSocketConnector} to use.
	 */
	public void setRSocketConnector(AbstractRSocketConnector rsocketConnector) {
		Assert.notNull(rsocketConnector, "'rsocketConnector' must not be null");
		this.rsocketConnector = rsocketConnector;
	}

	/**
	 * Get an array of the path patterns this endpoint is mapped onto.
	 * @return the mapping path
	 */
	public String[] getPath() {
		return Arrays.copyOf(this.path, this.path.length);
	}

	/**
	 * Specify the type of payload to be generated when the inbound RSocket request
	 * content is read by the encoders.
	 * By default this value is null which means at runtime any "text" Content-Type will
	 * result in String while all others default to <code>byte[].class</code>.
	 * @param requestElementClass The payload type.
	 */
	public void setRequestElementClass(Class<?> requestElementClass) {
		setRequestElementType(ResolvableType.forClass(requestElementClass));
	}

	/**
	 * Specify the type of payload to be generated when the inbound RSocket request
	 * content is read by the converters/encoders.
	 * By default this value is null which means at runtime any "text" Content-Type will
	 * result in String while all others default to <code>byte[].class</code>.
	 * @param requestElementType The payload type.
	 */
	public void setRequestElementType(ResolvableType requestElementType) {
		this.requestElementType = requestElementType;
	}

	@Override
	protected void onInit() {
		super.onInit();
		AbstractRSocketConnector rsocketConnectorToUse = this.rsocketConnector;
		if (rsocketConnectorToUse != null) {
			rsocketConnectorToUse.addEndpoint(this);
			this.rsocketStrategies = rsocketConnectorToUse.getRSocketStrategies();
		}
	}

	@Override
	protected void doStart() {
		super.doStart();
		if (this.rsocketConnector instanceof ClientRSocketConnector) {
			((ClientRSocketConnector) this.rsocketConnector).connect();
		}
	}

	@Override
	public Mono<Void> handleMessage(Message<?> requestMessage) {
		if (!isRunning()) {
			return Mono.error(new MessageDeliveryException(requestMessage,
					"The RSocket Inbound Gateway '" + getComponentName() + "' is stopped; " +
							"service for path(s) " + Arrays.toString(this.path) + " is not available at the moment."));
		}

		Mono<Message<?>> requestMono = decodeRequestMessage(requestMessage);
		MonoProcessor<Flux<Payload>> replyMono = getReplyMono(requestMessage);
		if (replyMono != null) {
			return requestMono
					.flatMap(this::sendAndReceiveMessageReactive)
					.doOnNext(replyMessage -> {
						replyMono.onNext(createReply(replyMessage.getPayload(), requestMessage));
						replyMono.onComplete();
					})
					.then();
		}
		else {
			return requestMono
					.doOnNext(this::send)
					.then();
		}
	}

	private Mono<Message<?>> decodeRequestMessage(Message<?> requestMessage) {
		return Mono.just(decodePayload(requestMessage))
				.map((payload) ->
						MessageBuilder.withPayload(payload)
								.copyHeaders(requestMessage.getHeaders())
								.build());
	}

	@SuppressWarnings("unchecked")
	private Object decodePayload(Message<?> requestMessage) {
		ResolvableType elementType = this.requestElementType;
		MimeType mimeType = requestMessage.getHeaders().get(MessageHeaders.CONTENT_TYPE, MimeType.class);
		if (elementType == null) {
			elementType =
					mimeType != null && "text".equals(mimeType.getType())
							? ResolvableType.forClass(String.class)
							: ResolvableType.forClass(byte[].class);
		}

		Object payload = requestMessage.getPayload();

		// The IntegrationRSocket logic ensures that we can have only a single DataBuffer payload or Flux<DataBuffer>.
		Decoder<Object> decoder = this.rsocketStrategies.decoder(elementType, mimeType);
		if (payload instanceof DataBuffer) {
			return decoder.decode((DataBuffer) payload, elementType, mimeType, null);
		}
		else {
			return decoder.decode((Publisher<DataBuffer>) payload, elementType, mimeType, null);
		}
	}

	private Flux<Payload> createReply(Object reply, Message<?> requestMessage) {
		MessageHeaders requestMessageHeaders = requestMessage.getHeaders();
		DataBufferFactory bufferFactory =
				requestMessageHeaders.get(HandlerMethodReturnValueHandler.DATA_BUFFER_FACTORY_HEADER,
						DataBufferFactory.class);

		MimeType mimeType = requestMessageHeaders.get(MessageHeaders.CONTENT_TYPE, MimeType.class);

		return encodeContent(reply, ResolvableType.forInstance(reply), bufferFactory, mimeType)
				.map(RSocketInboundGateway::createPayload);
	}

	private Flux<DataBuffer> encodeContent(Object content, ResolvableType returnValueType,
			DataBufferFactory bufferFactory, @Nullable MimeType mimeType) {

		ReactiveAdapter adapter =
				this.rsocketStrategies.reactiveAdapterRegistry()
						.getAdapter(returnValueType.resolve(), content);

		Publisher<?> publisher;
		if (adapter != null) {
			publisher = adapter.toPublisher(content);
		}
		else {
			publisher = Flux.just(content);
		}

		return Flux.from((Publisher<?>) publisher)
				.map((value) -> encodeValue(value, bufferFactory, mimeType));
	}

	private DataBuffer encodeValue(Object element, DataBufferFactory bufferFactory, @Nullable MimeType mimeType) {
		ResolvableType elementType = ResolvableType.forInstance(element);
		Encoder<Object> encoder = this.rsocketStrategies.encoder(elementType, mimeType);
		return encoder.encodeValue(element, bufferFactory, elementType, mimeType, null);
	}

	@Nullable
	@SuppressWarnings("unchecked")
	private static MonoProcessor<Flux<Payload>> getReplyMono(Message<?> message) {
		Object headerValue = message.getHeaders().get(RSocketPayloadReturnValueHandler.RESPONSE_HEADER);
		Assert.state(headerValue == null || headerValue instanceof MonoProcessor, "Expected MonoProcessor");
		return (MonoProcessor<Flux<Payload>>) headerValue;
	}

	private static Payload createPayload(DataBuffer data) {
		if (data instanceof NettyDataBuffer) {
			return ByteBufPayload.create(((NettyDataBuffer) data).getNativeBuffer());
		}
		else if (data instanceof DefaultDataBuffer) {
			return DefaultPayload.create(((DefaultDataBuffer) data).getNativeBuffer());
		}
		else {
			return DefaultPayload.create(data.asByteBuffer());
		}
	}

}
