/*
 * Copyright 2002-2011 the original author or authors.
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

package org.springframework.integration.gemfire.store;

import java.util.Iterator;
import java.util.UUID;

import org.springframework.data.gemfire.RegionFactoryBean;
import org.springframework.integration.Message;
import org.springframework.integration.store.AbstractMessageGroupStore;
import org.springframework.integration.store.MessageGroup;
import org.springframework.integration.store.MessageStore;
import org.springframework.integration.store.SimpleMessageGroup;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.util.Assert;

import com.gemstone.gemfire.cache.Cache;
import com.gemstone.gemfire.cache.Region;

/**
 * @author Mark Fisher
 * @author Oleg Zhurakousky
 * @since 2.1
 */
public class GemfireMessageStore extends AbstractMessageGroupStore implements MessageStore{

	private volatile Region<UUID, Message<?>> messageRegion;
	
	private volatile Region<Object, MessageGroup> messageGroupRegion;
	
	private final Cache cache;

	public GemfireMessageStore(Cache cache) {
		this.cache = cache;
		this.initRegions();
	}

	public Message<?> getMessage(UUID id) {
		return this.messageRegion.get(id);
	}

	public <T> Message<T> addMessage(Message<T> message) {
		this.messageRegion.put(message.getHeaders().getId(), message);
		return message;
	}

	public Message<?> removeMessage(UUID id) {
		Object removedMessage = this.messageRegion.remove(id);
		Assert.isInstanceOf(Message.class, removedMessage, "removed value is not an instance of Message: " + removedMessage);
		return (Message<?>) removedMessage;
	}

	@ManagedAttribute
	public long getMessageCount() {
		return this.messageRegion.size();
	}

	public MessageGroup getMessageGroup(Object groupId) {
		MessageGroup messageGroup = null;
		if (this.messageGroupRegion.containsKey(groupId)){
			messageGroup = this.messageGroupRegion.get(groupId);
		}
		else {
			messageGroup = new SimpleMessageGroup(groupId);
			this.messageGroupRegion.put(groupId, messageGroup);
		}
		return messageGroup;
	}

	public MessageGroup addMessageToGroup(Object groupId, Message<?> message) {
		SimpleMessageGroup messageGroup = (SimpleMessageGroup) this.getMessageGroup(groupId);
		messageGroup.add(message);
		this.messageGroupRegion.put(groupId, messageGroup);
		return messageGroup;
	}

	public MessageGroup markMessageGroup(MessageGroup group) {
		((SimpleMessageGroup)group).markAll();
		this.messageGroupRegion.put(group.getGroupId(), group);
		return group;
	}

	public MessageGroup removeMessageFromGroup(Object key, Message<?> messageToRemove) {
		SimpleMessageGroup messageGroup = (SimpleMessageGroup) this.getMessageGroup(key);
		messageGroup.remove(messageToRemove);
		this.messageGroupRegion.put(key, messageGroup);
		return messageGroup;
	}

	public MessageGroup markMessageFromGroup(Object key,
			Message<?> messageToMark) {
		SimpleMessageGroup messageGroup = (SimpleMessageGroup) this.getMessageGroup(key);
		messageGroup.mark(messageToMark);
		this.messageGroupRegion.put(key, messageGroup);
		return messageGroup;
	}

	public void removeMessageGroup(Object groupId) {
		this.messageGroupRegion.remove(groupId);
	}
	
	public Iterator<MessageGroup> iterator() {
		return this.messageGroupRegion.values().iterator();
	}

	public void initRegions()  {
		try {
			RegionFactoryBean<UUID, Message<?>> messageRegionFactoryBean = new RegionFactoryBean<UUID, Message<?>>();
			messageRegionFactoryBean.setBeanName("messageRegionFactoryBean");
			messageRegionFactoryBean.setCache(cache);
			messageRegionFactoryBean.afterPropertiesSet();
			this.messageRegion = messageRegionFactoryBean.getObject();
			
			RegionFactoryBean<Object, MessageGroup> messageGroupRegionFactoryBean = new RegionFactoryBean<Object, MessageGroup>();
			messageGroupRegionFactoryBean.setBeanName("messageGroupRegionFactoryBean");
			messageGroupRegionFactoryBean.setCache(cache);
			messageGroupRegionFactoryBean.afterPropertiesSet();
			this.messageGroupRegion = messageGroupRegionFactoryBean.getObject();
		} catch (Exception e) {
			e.printStackTrace();
			throw new IllegalArgumentException("Failed to initialize Gemfire Regions");
		}
		
	}

}
