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

package org.springframework.integration.rsocket;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import org.reactivestreams.Publisher;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.NettyDataBuffer;
import org.springframework.core.io.buffer.NettyDataBufferFactory;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.handler.DestinationPatternsMessageCondition;
import org.springframework.messaging.handler.invocation.reactive.HandlerMethodReturnValueHandler;
import org.springframework.messaging.rsocket.RSocketPayloadReturnValueHandler;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.messaging.rsocket.RSocketRequesterMethodArgumentResolver;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.util.Assert;
import org.springframework.util.MimeType;

import io.netty.buffer.ByteBuf;
import io.rsocket.AbstractRSocket;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoProcessor;

/**
 * Implementation of {@link RSocket} that wraps incoming requests with a
 * {@link Message}, delegates to a {@link Function} for handling, and then
 * obtains the response from a "reply" header.
 * <p>
 * Essentially, this is an adapted for Spring Integration copy
 * of the {@link org.springframework.messaging.rsocket.MessagingRSocket} because
 * that one is not public.
 *
 * @author Artem Bilan
 *
 * @since 5.2
 *
 * @see org.springframework.messaging.rsocket.MessagingRSocket
 */
class IntegrationRSocket extends AbstractRSocket {

	private final Function<Message<?>, Mono<Void>> handler;

	private final RSocketRequester requester;

	private final DataBufferFactory bufferFactory;

	@Nullable
	private MimeType dataMimeType;

	IntegrationRSocket(Function<Message<?>, Mono<Void>> handler, RSocketRequester requester,
			@Nullable MimeType defaultDataMimeType, DataBufferFactory bufferFactory) {

		Assert.notNull(handler, "'handler' is required");
		Assert.notNull(requester, "'requester' is required");
		this.handler = handler;
		this.requester = requester;
		this.dataMimeType = defaultDataMimeType;
		this.bufferFactory = bufferFactory;
	}

	public void setDataMimeType(MimeType dataMimeType) {
		this.dataMimeType = dataMimeType;
	}

	public RSocketRequester getRequester() {
		return this.requester;
	}

	@Override
	public Mono<Void> fireAndForget(Payload payload) {
		return handle(payload);
	}

	@Override
	public Mono<Payload> requestResponse(Payload payload) {
		return handleAndReply(payload, Flux.just(payload)).next();
	}

	@Override
	public Flux<Payload> requestStream(Payload payload) {
		return handleAndReply(payload, Flux.just(payload));
	}

	@Override
	public Flux<Payload> requestChannel(Publisher<Payload> payloads) {
		return Flux.from(payloads)
				.switchOnFirst((signal, innerFlux) -> {
					Payload firstPayload = signal.get();
					return firstPayload == null ? innerFlux : handleAndReply(firstPayload, innerFlux);
				});
	}

	@Override
	public Mono<Void> metadataPush(Payload payload) {
		// Not very useful until createHeaders does more with metadata
		return handle(payload);
	}


	private Mono<Void> handle(Payload payload) {
		String destination = getDestination(payload);
		MessageHeaders headers = createHeaders(destination, null);
		DataBuffer dataBuffer = retainDataAndReleasePayload(payload);
		int refCount = refCount(dataBuffer);
		Message<?> message = MessageBuilder.createMessage(dataBuffer, headers);
		return Mono.defer(() -> this.handler.apply(message))
				.doFinally(s -> {
					if (refCount(dataBuffer) == refCount) {
						DataBufferUtils.release(dataBuffer);
					}
				});
	}

	static int refCount(DataBuffer dataBuffer) {
		return dataBuffer instanceof NettyDataBuffer ?
				((NettyDataBuffer) dataBuffer).getNativeBuffer().refCnt() : 1;
	}

	private Flux<Payload> handleAndReply(Payload firstPayload, Flux<Payload> payloads) {
		MonoProcessor<Flux<Payload>> replyMono = MonoProcessor.create();
		String destination = getDestination(firstPayload);
		MessageHeaders headers = createHeaders(destination, replyMono);

		AtomicBoolean read = new AtomicBoolean();
		Flux<DataBuffer> buffers = payloads.map(this::retainDataAndReleasePayload).doOnSubscribe(s -> read.set(true));
		Message<Flux<DataBuffer>> message = MessageBuilder.createMessage(buffers, headers);

		return Mono.defer(() -> this.handler.apply(message))
				.doFinally(s -> {
					// Subscription should have happened by now due to ChannelSendOperator
					if (!read.get()) {
						buffers.subscribe(DataBufferUtils::release);
					}
				})
				.thenMany(Flux.defer(() -> replyMono.isTerminated() ?
						replyMono.flatMapMany(Function.identity()) :
						Mono.error(new IllegalStateException("Something went wrong: reply Mono not set"))));
	}

	private DataBuffer retainDataAndReleasePayload(Payload payload) {
		return payloadToDataBuffer(payload, this.bufferFactory);
	}

	private MessageHeaders createHeaders(String destination, @Nullable MonoProcessor<?> replyMono) {
		MessageHeaderAccessor headers = new MessageHeaderAccessor();
		headers.setLeaveMutable(true);
		headers.setHeader(DestinationPatternsMessageCondition.LOOKUP_DESTINATION_HEADER, destination);
		if (this.dataMimeType != null) {
			headers.setContentType(this.dataMimeType);
		}
		headers.setHeader(RSocketRequesterMethodArgumentResolver.RSOCKET_REQUESTER_HEADER, this.requester);
		if (replyMono != null) {
			headers.setHeader(RSocketPayloadReturnValueHandler.RESPONSE_HEADER, replyMono);
		}
		headers.setHeader(HandlerMethodReturnValueHandler.DATA_BUFFER_FACTORY_HEADER, this.bufferFactory);
		return headers.getMessageHeaders();
	}

	static String getDestination(Payload payload) {
		return payload.getMetadataUtf8();
	}

	static DataBuffer payloadToDataBuffer(Payload payload, DataBufferFactory bufferFactory) {
		try {
			if (bufferFactory instanceof NettyDataBufferFactory) {
				ByteBuf byteBuf = payload.sliceData().retain();
				return ((NettyDataBufferFactory) bufferFactory).wrap(byteBuf);
			}
			else {
				return bufferFactory.wrap(payload.getData());
			}
		}
		finally {
			if (payload.refCnt() > 0) {
				payload.release();
			}
		}
	}

}
