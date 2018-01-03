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

package org.springframework.integration.endpoint;

import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessageHandlingException;

/**
 * Operations to perform on some message source.
 *
 * @author Gary Russell
 * @since 5.0.1
 *
 */
public interface PollingOperations {

	/**
	 * Poll for a message.
	 * @param handler a message handler.
	 * @return the message
	 * @throws MessageHandlingException if the handler throws an exception.
	 */
	boolean poll(MessageHandler handler) throws MessageHandlingException;

}
