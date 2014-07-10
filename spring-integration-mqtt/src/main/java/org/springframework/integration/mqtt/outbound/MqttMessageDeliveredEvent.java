/*
 * Copyright 2014 the original author or authors.
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
package org.springframework.integration.mqtt.outbound;

import org.springframework.integration.mqtt.core.MqttIntegrationEvent;

/**
 * An event emitted (when using aysnc) when the client indicates the message
 * was delivered; contains the message id for correlation with the MqttMessageSentEvent.
 *
 * @author Gary Russell
 * @since 4.1
 *
 */
@SuppressWarnings("serial")
public class MqttMessageDeliveredEvent extends MqttIntegrationEvent {

	private final int messageId;

	public MqttMessageDeliveredEvent(Object source, int messageId) {
		super(source);
		this.messageId = messageId;
	}

	public int getMessageId() {
		return messageId;
	}

	@Override
	public String toString() {
		return "MqttMessageDeliveredEvent [messageId=" + messageId + "]";
	}

}
