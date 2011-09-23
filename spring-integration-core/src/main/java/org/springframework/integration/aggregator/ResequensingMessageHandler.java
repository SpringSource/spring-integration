/*
 * Copyright 2002-2011 the original author or authors.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.springframework.integration.aggregator;

import java.util.Collection;

import org.springframework.integration.Message;
import org.springframework.integration.store.MessageGroup;
import org.springframework.integration.store.MessageGroupStore;
import org.springframework.integration.store.SimpleMessageGroup;

/**
 * Resequencer specific implementation of {@link CorrelatingMessageHandler}. It takes into account the fact that 
 * it must remember the last released message sequence so it overrides {@link #cleanUpForReleasedGroup(MessageGroup, Collection)} method 
 *
 * @author Oleg Zhurakousky
 * @since 2.1
 */
public class ResequensingMessageHandler extends CorrelatingMessageHandler {

	
	public ResequensingMessageHandler(MessageGroupProcessor processor,
			MessageGroupStore store, CorrelationStrategy correlationStrategy,
			ReleaseStrategy releaseStrategy) {
		super(processor, store, correlationStrategy, releaseStrategy);
	}

	
	public ResequensingMessageHandler(MessageGroupProcessor processor,
			MessageGroupStore store) {
		super(processor, store);
	}

	
	public ResequensingMessageHandler(MessageGroupProcessor processor) {
		super(processor);
	}
	
	@SuppressWarnings("rawtypes")
	void cleanUpForReleasedGroup(SimpleMessageGroup group, Collection<Message> completedMessages) {
		if (group.isComplete() || group.getSequenceSize() == 0) {
			remove(group);
		}
		else {
			if (completedMessages == null) {
				mark(group);
			} 
			else {
				mark(group, completedMessages);
			}
		}
	}

}
