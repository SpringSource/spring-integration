/*
 * Copyright 2002-2014 the original author or authors.
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

package org.springframework.integration;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandlingException;

/**
 * Exception that indicates a message has been rejected by a selector.
 *
 * @author Mark Fisher
 */
@SuppressWarnings("serial")
public class MessageRejectedException extends MessageHandlingException {

	/**
	 * @deprecated since 4.0 in favor of {@code MessageRejectedException(Message, String)}
	 */
	@Deprecated
	public MessageRejectedException(Message<?> failedMessage) {
		super(failedMessage, null);
	}

	public MessageRejectedException(Message<?> failedMessage, String description) {
		super(failedMessage, description);
	}

	public MessageRejectedException(Message<?> failedMessage, String description, Throwable cause) {
		super(failedMessage, description, cause);
	}

}
