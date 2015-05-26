/*
 * Copyright 2015 the original author or authors.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.springframework.integration.stomp.event;

import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;

/**
 * The {@link StompIntegrationEvent} for the STOMP {@code RECEIPT} Frames or lost receipts.
 *
 * @author Artem Bilan
 * @since 4.2
 * @see org.springframework.integration.stomp.inbound.StompInboundChannelAdapter
 * @see org.springframework.integration.stomp.outbound.StompMessageHandler
 */
@SuppressWarnings("serial")
public class StompReceiptEvent extends StompIntegrationEvent {

	private final String destination;

	private final String receiptId;

	private final StompCommand stompCommand;

	private final boolean lost;

	private Message<?> message;

	public StompReceiptEvent(Object source, String destination, String receiptId, StompCommand stompCommand,
			boolean lost) {
		super(source);
		this.destination = destination;
		this.receiptId = receiptId;
		this.stompCommand = stompCommand;
		this.lost = lost;
	}

	public String getDestination() {
		return destination;
	}

	public String getReceiptId() {
		return receiptId;
	}

	public StompCommand getStompCommand() {
		return stompCommand;
	}

	public boolean isLost() {
		return lost;
	}

	public Message<?> getMessage() {
		return message;
	}

	public void setMessage(Message<?> message) {
		this.message = message;
	}
}
