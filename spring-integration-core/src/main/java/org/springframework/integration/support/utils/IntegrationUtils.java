/*
 * Copyright 2014-2016 the original author or authors.
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

package org.springframework.integration.support.utils;

import java.io.UnsupportedEncodingException;
import java.util.function.Supplier;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.core.convert.ConversionService;
import org.springframework.integration.support.DefaultMessageBuilderFactory;
import org.springframework.integration.support.MessageBuilderFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.MessageHandlingException;
import org.springframework.messaging.MessagingException;
import org.springframework.util.Assert;

/**
 * General utility methods.
 *
 * @author Gary Russell
 * @author Marius Bogoevici
 * @since 4.0
 *
 */
public final class IntegrationUtils {

	private static final Log logger = LogFactory.getLog(IntegrationUtils.class);

	public static final String INTEGRATION_CONVERSION_SERVICE_BEAN_NAME = "integrationConversionService";

	public static final String INTEGRATION_MESSAGE_BUILDER_FACTORY_BEAN_NAME = "messageBuilderFactory";


	/**
	 * Should be set to TRUE on CI plans and framework developer systems.
	 */
	public static final boolean fatalWhenNoBeanFactory = Boolean.valueOf(System.getenv("SI_FATAL_WHEN_NO_BEANFACTORY"));

	private IntegrationUtils() {
		super();
	}

	/**
	 * @param beanFactory BeanFactory for lookup, must not be null.
	 * @return The {@link ConversionService} bean whose name is "integrationConversionService" if available.
	 */
	public static ConversionService getConversionService(BeanFactory beanFactory) {
		return getBeanOfType(beanFactory, INTEGRATION_CONVERSION_SERVICE_BEAN_NAME, ConversionService.class);
	}

	/**
	 * Returns the context-wide `messageBuilderFactory` bean from the beanFactory,
	 * or a {@link DefaultMessageBuilderFactory} if not found or the beanFactory is null.
	 * @param beanFactory The bean factory.
	 * @return The message builder factory.
	 */
	public static MessageBuilderFactory getMessageBuilderFactory(BeanFactory beanFactory) {
		MessageBuilderFactory messageBuilderFactory = null;
		if (beanFactory != null) {
			try {
				messageBuilderFactory = beanFactory.getBean(
						INTEGRATION_MESSAGE_BUILDER_FACTORY_BEAN_NAME, MessageBuilderFactory.class);
			}
			catch (Exception e) {
				if (logger.isDebugEnabled()) {
					logger.debug("No MessageBuilderFactory with name '"
							+ INTEGRATION_MESSAGE_BUILDER_FACTORY_BEAN_NAME
							+ "' found: " + e.getMessage()
							+ ", using default.");
				}
			}
		}
		else {
			if (logger.isDebugEnabled()) {
				logger.debug("No 'beanFactory' supplied; cannot find MessageBuilderFactory, using default.");
			}
			if (fatalWhenNoBeanFactory) {
				throw new RuntimeException("All Message creators need a BeanFactory");
			}
		}
		if (messageBuilderFactory == null) {
			messageBuilderFactory = new DefaultMessageBuilderFactory();
		}
		return messageBuilderFactory;
	}

	private static <T> T getBeanOfType(BeanFactory beanFactory, String beanName, Class<T> type) {
		Assert.notNull(beanFactory, "BeanFactory must not be null");
		if (!beanFactory.containsBean(beanName)) {
			return null;
		}
		return beanFactory.getBean(beanName, type);
	}

	/**
	 * Utility method for null-safe conversion from String to byte[]
	 *
	 * @param value the String to be converted
	 * @param encoding the encoding
	 * @return the byte[] corresponding to the given String and encoding, null if provided String argument was null
	 * @throws IllegalArgumentException if the encoding is not supported
	 */
	public static byte[] stringToBytes(String value, String encoding) {
		try {
			return value != null ? value.getBytes(encoding) : null;
		}
		catch (UnsupportedEncodingException e) {
			throw new IllegalArgumentException(e);
		}
	}

	/**
	 * Utility method for null-safe conversion from byte[] to String
	 *
	 * @param bytes the byte[] to be converted
	 * @param encoding the encoding
	 * @return the String corresponding to the given byte[] and encoding, null if provided byte[] argument was null
	 * @throws IllegalArgumentException if the encoding is not supported
	 */
	public static String bytesToString(byte[] bytes, String encoding) {
		try {
			return bytes == null ? null : new String(bytes, encoding);
		}
		catch (UnsupportedEncodingException e) {
			throw new IllegalArgumentException(e);
		}
	}

	/**
	 * If the exception is not a {@link MessagingException} or does not have
	 * a {@link MessagingException#getFailedMessage() failedMessage}, wrap it
	 * in a new {@link MessageDeliveryException} with the message.
	 * @param message the message.
	 * @param text a Supplier for the new exception's message text.
	 * @param e the exception.
	 * @return the wrapper, if necessary, or the original exception.
	 * @since 5.0.4
	 */
	public static RuntimeException wrapInDeliveryExceptionIfNecessary(Message<?> message, Supplier<String> text, Exception e) {
		RuntimeException runtimeException = (e instanceof RuntimeException)
				? (RuntimeException) e
				: new MessageDeliveryException(message, text.get(), e);
		if (!(e instanceof MessagingException) ||
				((MessagingException) e).getFailedMessage() == null) {
			runtimeException = new MessageDeliveryException(message, text.get(), e);
		}
		return runtimeException;
	}

	/**
	 * If the exception is not a {@link MessagingException} or does not have
	 * a {@link MessagingException#getFailedMessage() failedMessage}, wrap it
	 * in a new {@link MessageHandlingException} with the message.
	 * @param message the message.
	 * @param text a Supplier for the new exception's message text.
	 * @param e the exception.
	 * @return the wrapper, if necessary, or the original exception.
	 * @since 5.0.4
	 */
	public static RuntimeException wrapInHandlingExceptionIfNecessary(Message<?> message, Supplier<String> text, Exception e) {
		RuntimeException runtimeException = (e instanceof RuntimeException)
				? (RuntimeException) e
				: new MessageHandlingException(message, text.get(), e);
		if (!(e instanceof MessagingException) ||
				((MessagingException) e).getFailedMessage() == null) {
			runtimeException = new MessageHandlingException(message, text.get(), e);
		}
		return runtimeException;
	}

}
