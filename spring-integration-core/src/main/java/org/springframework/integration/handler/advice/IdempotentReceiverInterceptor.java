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

package org.springframework.integration.handler.advice;

import java.lang.reflect.Method;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.integration.IntegrationMessageHeaderAccessor;
import org.springframework.integration.MessageRejectedException;
import org.springframework.integration.core.MessageSelector;
import org.springframework.integration.core.MessagingTemplate;
import org.springframework.integration.support.MessageBuilderFactory;
import org.springframework.integration.support.utils.IntegrationUtils;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.util.Assert;

/**
 * The {@link MethodInterceptor} implementation for the
 * <a href="http://www.eaipatterns.com/IdempotentReceiver.html">Idempotent Receiver</a>
 * EIP pattern.
 * <p>
 * This {@link MethodInterceptor} works like {@code MessageFilter} if {@link #discardChannel}
 * is provided or {@link #throwExceptionOnRejection} is set to {@code true}.
 * However if those properties aren't provided, this interceptor will create an new {@link Message}
 * with {@link IntegrationMessageHeaderAccessor#DUPLICATE_MESSAGE} header in case of the
 * {@code requestMessage} isn't accepted by {@link MessageSelector}.
 * <p>
 * The {@code idempotent filtering} logic depends on the provided {@link MessageSelector}.
  * <p>
 * This class is designed to be used just only for the {@link MessageHandler#handleMessage},
 * as well as the entire {@code Idempotent Receiver} pattern.
 *
 * @author Artem Bilan
 * @since 4.1
 * @see org.springframework.integration.selector.MetadataStoreSelector
 * @see org.springframework.integration.config.IdempotentReceiverAutoProxyCreatorIntegrationConfigurationInitializer
 */
public class IdempotentReceiverInterceptor implements MethodInterceptor, BeanFactoryAware {

	protected final Log logger = LogFactory.getLog(this.getClass());

	private final MessagingTemplate messagingTemplate = new MessagingTemplate();

	private final MessageSelector messageSelector;

	private volatile MessageChannel discardChannel;

	private volatile boolean throwExceptionOnRejection;

	private MessageBuilderFactory messageBuilderFactory;

	public IdempotentReceiverInterceptor(MessageSelector messageSelector) {
		Assert.notNull(messageSelector);
		this.messageSelector = messageSelector;
	}

	/**
	 * Specify the timeout value for sending to the intercepting target.
	 * @param timeout the timeout in milliseconds
	 */
	public void setTimeout(long timeout) {
		this.messagingTemplate.setSendTimeout(timeout);
	}

	/**
	 * Specify whether this intercept should throw a
	 * {@link MessageRejectedException} when its selector does not accept a
	 * Message. The default value is <code>false</code> meaning that rejected
	 * Messages will be quietly dropped or sent to the discard channel if
	 * available. Typically this value would not be <code>true</code> when
	 * a discard channel is provided, but if so, it will still apply
	 * (in such a case, the Message will be sent to the discard channel,
	 * and <em>then</em> the exception will be thrown).
	 * @param throwExceptionOnRejection true if an exception should be thrown.
	 * @see #setDiscardChannel(MessageChannel)
	 */
	public void setThrowExceptionOnRejection(boolean throwExceptionOnRejection) {
		this.throwExceptionOnRejection = throwExceptionOnRejection;
	}

	/**
	 * Specify a channel where rejected Messages should be sent. If the discard
	 * channel is null (the default), rejected Messages will be dropped. However,
	 * the 'throwExceptionOnRejection' flag determines whether rejected Messages
	 * trigger an exception. That value is evaluated regardless of the presence
	 * of a discard channel.
	 * @param discardChannel The discard channel.
	 * @see #setThrowExceptionOnRejection(boolean)
	 */
	public void setDiscardChannel(MessageChannel discardChannel) {
		this.discardChannel = discardChannel;
	}

	@Override
	public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
		this.messageBuilderFactory = IntegrationUtils.getMessageBuilderFactory(beanFactory);
	}

	@Override
	public Object invoke(MethodInvocation invocation) throws Throwable {
		Method method = invocation.getMethod();
		Object invocationThis = invocation.getThis();
		Object[] arguments = invocation.getArguments();
		boolean isMessageHandler = invocationThis != null && invocationThis instanceof MessageHandler;
		boolean isMessageMethod = method.getName().equals("handleMessage")
				&& (arguments.length == 1 && arguments[0] instanceof Message);
		if (!isMessageHandler || !isMessageMethod) {
			if (logger.isWarnEnabled()) {
				String clazzName = invocationThis == null
						? method.getDeclaringClass().getName()
						: invocationThis.getClass().getName();
				logger.warn("This advice " + this.getClass().getName() +
						" can only be used for MessageHandlers; an attempt to advise method '"
						+ method.getName() + "' in '" + clazzName + "' is ignored");
			}
			return invocation.proceed();
		}

		Message<?> message = (Message<?>) arguments[0];
		boolean accept = this.messageSelector.accept(message);
		if (!accept) {
			boolean discarded = false;
			if (this.discardChannel != null) {
				this.messagingTemplate.send(this.discardChannel, message);
				discarded = true;
			}
			if (this.throwExceptionOnRejection) {
				throw new MessageRejectedException(message, "IdempotentReceiver '" + this
						+ "' rejected duplicate Message: " + message);
			}

			if (!discarded) {
				arguments[0] = this.messageBuilderFactory.fromMessage(message)
						.setHeader(IntegrationMessageHeaderAccessor.DUPLICATE_MESSAGE, true).build();
			}
			else {
				return null;
			}
		}
		return invocation.proceed();
	}

}
