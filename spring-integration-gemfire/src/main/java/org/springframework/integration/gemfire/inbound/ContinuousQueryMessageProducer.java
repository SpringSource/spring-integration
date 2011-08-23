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

package org.springframework.integration.gemfire.inbound;

import org.springframework.data.gemfire.listener.CqQueryDefinition;
import org.springframework.data.gemfire.listener.QueryListener;
import org.springframework.data.gemfire.listener.QueryListenerContainer;
import org.springframework.integration.Message;
import org.springframework.integration.endpoint.MessageProducerSupport;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.util.Assert;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import com.gemstone.gemfire.cache.query.CqEvent;

/**
 * Responds to a Gemfire continuous query (set using the #query field) that is
 * constantly evaluated against a cache
 * {@link com.gemstone.gemfire.cache.Region}. This is much faster than
 * re-querying the cache manually.
 * 
 * @author Josh Long
 * @author David Turanski
 * @since 2.1
 * 
 */
public class ContinuousQueryMessageProducer extends MessageProducerSupport implements QueryListener {
	private static Log logger = LogFactory.getLog(ContinuousQueryMessageProducer.class);
	
	private final String query;
	private final QueryListenerContainer queryListenerContainer;
	private volatile String queryName;
	private boolean durable;
	
	/**
	 * 
	 * @param queryListenerContainer a {@link org.springframework.data.gemfire.listener.QueryListenerContainer}
	 * @param query the query string
	 */
	public ContinuousQueryMessageProducer(QueryListenerContainer queryListenerContainer, String query) {
		Assert.notNull(queryListenerContainer, "'queryListenerContainer' cannot be null");
		Assert.notNull(query, "'query' cannot be null");
		this.queryListenerContainer = queryListenerContainer;
		this.query = query;
	}
	
	/**
	 * 
	 * @param queryName optional query name
	 */
	public void setQueryName(String queryName) {
		this.queryName = queryName;
	}
	
	/**
	 * 
	 * @param durable true if the query is a durable subscription
	 */
	public void setDurable(boolean durable) {
		this.durable = durable;
	}

	@Override
	protected void onInit() {
		super.onInit();
		if (queryName == null){
			queryListenerContainer.addListener(new CqQueryDefinition(this.query, this, this.durable));
		} else {
			queryListenerContainer.addListener(new CqQueryDefinition(this.queryName, this.query, this, this.durable));
		}
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.springframework.data.gemfire.listener.QueryListener#onEvent(com.gemstone
	 * .gemfire.cache.query.CqEvent)
	 */
	public void onEvent(CqEvent event) {
		if (logger.isDebugEnabled()){
			logger.debug(String.format("processing cq event key [%s] event [%s]",event.getBaseOperation().toString(),event.getKey()));
		}
		Message<CqEvent> cqEventMessage = MessageBuilder.withPayload(event).build();
		sendMessage(cqEventMessage);
	}

}