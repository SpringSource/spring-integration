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

package org.springframework.integration.config;

import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.expression.Expression;
import org.springframework.integration.MessageChannel;
import org.springframework.integration.core.MessageHandler;
import org.springframework.integration.router.AbstractMappingMessageRouter;
import org.springframework.integration.router.ExpressionEvaluatingRouter;
import org.springframework.integration.router.MethodInvokingRouter;
import org.springframework.integration.support.channel.ChannelResolver;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Factory bean for creating a Message Router.
 * 
 * @author Mark Fisher
 * @author Jonas Partner
 * @author Oleg Zhurakousky
 * @author Dave Syer
 */
public class RouterFactoryBean extends AbstractStandardMessageHandlerFactoryBean {
	
	private final Log logger = LogFactory.getLog(this.getClass());

	private volatile Map<String, String> channelMappings;

	private volatile MessageChannel defaultOutputChannel;

	private volatile Long timeout;

	private volatile Boolean resolutionRequired;

	private volatile Boolean applySequence;

	private volatile Boolean ignoreSendFailures;
	
	private volatile ChannelResolver channelResolver;


	public void setChannelResolver(ChannelResolver channelResolver) {
		this.channelResolver = channelResolver;
	}

	public void setDefaultOutputChannel(MessageChannel defaultOutputChannel) {
		this.defaultOutputChannel = defaultOutputChannel;
	}

	public void setTimeout(Long timeout) {
		this.timeout = timeout;
	}

	public void setResolutionRequired(Boolean resolutionRequired) {
		this.resolutionRequired = resolutionRequired;
	}

	public void setApplySequence(Boolean applySequence) {
		this.applySequence = applySequence;
	}

	public void setIgnoreSendFailures(Boolean ignoreSendFailures) {
		this.ignoreSendFailures = ignoreSendFailures;
	}
	
	public void setChannelMappings(Map<String, String> channelMappings) {
		this.channelMappings = channelMappings;
	}

	@Override
	MessageHandler createMethodInvokingHandler(Object targetObject, String targetMethodName) {
		Assert.notNull(targetObject, "target object must not be null");
		AbstractMappingMessageRouter router = this.extractTypeIfPossible(targetObject, AbstractMappingMessageRouter.class);
		if (this.channelResolver != null){
			logger.warn("'channel-resolver' attribute has been deprecated in favor of using SpEL via 'expression' attribute");
			router.setChannelResolver(this.channelResolver);
		}
		if (router == null) {
			router = this.createMethodInvokingRouter(targetObject, targetMethodName);
			this.configureRouter(router);
		}
		else {
			Assert.isTrue(!StringUtils.hasText(targetMethodName), "target method should not be provided when the target "
					+ "object is an implementation of AbstractMessageRouter");
			this.configureRouter(router);
			if (targetObject instanceof MessageHandler) {
				return (MessageHandler) targetObject;
			}
		}
		return router;
	}

	@Override
	MessageHandler createExpressionEvaluatingHandler(Expression expression) {
		return this.configureRouter(new ExpressionEvaluatingRouter(expression));
	}

	private AbstractMappingMessageRouter createMethodInvokingRouter(Object targetObject, String targetMethodName) {
		MethodInvokingRouter router = (StringUtils.hasText(targetMethodName)) 
				? new MethodInvokingRouter(targetObject, targetMethodName)
				: new MethodInvokingRouter(targetObject);
		return router;
	}

	private AbstractMappingMessageRouter configureRouter(AbstractMappingMessageRouter router) {
		if (this.channelMappings != null) {
			router.setChannelMappings(this.channelMappings);
		}
		if (this.defaultOutputChannel != null) {
			router.setDefaultOutputChannel(this.defaultOutputChannel);
		}
		if (this.timeout != null) {
			router.setTimeout(timeout.longValue());
		}
		if (this.applySequence != null) {
			router.setApplySequence(this.applySequence);
		}
		if (this.ignoreSendFailures != null) {
			router.setIgnoreSendFailures(this.ignoreSendFailures);
		}
		if (this.resolutionRequired != null) {
			router.setResolutionRequired(this.resolutionRequired);
		}
		return router;
	}

}
