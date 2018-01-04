/*
 * Copyright 2018 the original author or authors.
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

package org.springframework.integration.support;

import org.springframework.integration.support.AcknowledgmentCallback.Status;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;

/**
 * Utility methods for acting on {@link AcknowledgmentCallback} headers.
 *
 * @author Gary Russell
 * @since 5.0.1
 *
 */
public final class AckUtils {

	private AckUtils() {
		super();
	}

	/**
	 * Return the {@link AcknowledgmentCallback} header (if present).
	 * @param message the message.
	 * @return the callback, or null.
	 */
	@Nullable
	public static AcknowledgmentCallback getAckCallback(Message<?> message) {
		return StaticMessageHeaderAccessor.getAcknowledgmentCallback(message);
	}

	/**
	 * ACCEPT an {@link AcknowledgmentCallback} if it's not null, supports auto ack
	 * and is not already ack'd.
	 * @param ackCallback the callback.
	 */
	public static void autoAck(AcknowledgmentCallback ackCallback) {
		if (ackCallback != null && ackCallback.isAutoAck() && !ackCallback.isAcknowledged()) {
			ackCallback.acknowledge(Status.ACCEPT);
		}
	}

	/**
	 * REJECT an {@link AcknowledgmentCallback} if it's not null, supports auto ack
	 * and is not already ack'd.
	 * @param ackCallback the callback.
	 */
	public static void autoNack(AcknowledgmentCallback ackCallback) {
		if (ackCallback != null && ackCallback.isAutoAck() && !ackCallback.isAcknowledged()) {
			ackCallback.acknowledge(Status.REJECT);
		}
	}

	/**
	 * ACCEPT the associated message if the callback is not null.
	 * @param ackCallback the callback.
	 */
	public static void accept(@Nullable AcknowledgmentCallback ackCallback) {
		if (ackCallback != null) {
			ackCallback.acknowledge(Status.ACCEPT);
		}
	}

	/**
	 * REJECT the associated message if the callback is not null.
	 * @param ackCallback the callback.
	 */
	public static void reject(@Nullable AcknowledgmentCallback ackCallback) {
		if (ackCallback != null) {
			ackCallback.acknowledge(Status.REJECT);
		}
	}

	/**
	 * REQUEUE the associated message if the callback is not null.
	 * @param ackCallback the callback.
	 */
	public static void requeue(@Nullable AcknowledgmentCallback ackCallback) {
		if (ackCallback != null) {
			ackCallback.acknowledge(Status.REQUEUE);
		}
	}

}
