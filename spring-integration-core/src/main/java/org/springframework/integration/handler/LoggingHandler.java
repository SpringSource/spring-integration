/*
 * Copyright 2002-2018 the original author or authors.
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

package org.springframework.integration.handler;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.integration.dispatcher.AggregateMessageDeliveryException;
import org.springframework.integration.expression.ExpressionUtils;
import org.springframework.messaging.Message;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * MessageHandler implementation that simply logs the Message or its payload depending on the value of the
 * 'shouldLogFullMessage' or SpEL 'logExpression' property.
 * If logging the payload, and it is assignable to Throwable, it will log the stack
 * trace. By default, it will log the payload only.
 *
 * @author Mark Fisher
 * @author Gary Russell
 * @author Artem Bilan
 * @author Andriy Kryvtsun
 * @since 1.0.1
 */
public class LoggingHandler extends AbstractMessageHandler {

	public enum Level {

		FATAL, ERROR, WARN, INFO, DEBUG, TRACE

	}

	private volatile Level level;

	private volatile boolean expressionSet;

	private volatile Expression expression = EXPRESSION_PARSER.parseExpression("payload");

	private volatile EvaluationContext evaluationContext = ExpressionUtils.createStandardEvaluationContext();

	private volatile boolean shouldLogFullMessageSet;

	private volatile Log messageLogger = this.logger;

	/**
	 * Create a LoggingHandler with the given log level (case-insensitive).
	 * <p>
	 * The valid levels are: FATAL, ERROR, WARN, INFO, DEBUG, or TRACE
	 * </p>
	 * @param level The level.
	 * @see #LoggingHandler(Level)
	 */
	public LoggingHandler(String level) {
		this(convertLevel(level));
	}

	private static Level convertLevel(String level) {
		Assert.hasText(level, "'level' cannot be empty");
		try {
			return Level.valueOf(level.toUpperCase());
		}
		catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Invalid log level '" + level
					+ "'. The (case-insensitive) supported values are: "
					+ StringUtils.arrayToCommaDelimitedString(Level.values()));
		}
	}

	/**
	 * Create a {@link LoggingHandler} with the given log {@link Level}.
	 * @param level the {@link Level} to use.
	 * @since 4.3
	 */
	public LoggingHandler(Level level) {
		doSetLevel(level);
	}

	/**
	 * Set a SpEL expression string to use.
	 * @param expressionString the SpEL expression string to use.
	 * @since 4.3
	 * @see #setLogExpression(Expression)
	 */
	public void setLogExpressionString(String expressionString) {
		Assert.hasText(expressionString, "'expressionString' must not be empty");
		setLogExpression(EXPRESSION_PARSER.parseExpression(expressionString));
	}

	/**
	 * Set an {@link Expression} to evaluate a log entry at runtime against the request {@link Message}.
	 * @param expression the {@link Expression} to use.
	 * @since 4.3
	 * @see #setLogExpressionString(String)
	 */
	public void setLogExpression(Expression expression) {
		Assert.isTrue(!(this.shouldLogFullMessageSet),
				"Cannot set both 'expression' AND 'shouldLogFullMessage' properties");
		this.expressionSet = true;
		this.expression = expression;
	}

	/**
	 * @return The current logging {@link Level}.
	 */
	public Level getLevel() {
		return this.level;
	}

	/**
	 * Set the logging {@link Level} to change the behavior at runtime.
	 * @param level the level.
	 */
	public void setLevel(Level level) {
		doSetLevel(level);
	}

	private void doSetLevel(Level level) {
		Assert.notNull(level, "'level' cannot be null");
		this.level = level;
	}

	public void setLoggerName(String loggerName) {
		Assert.hasText(loggerName, "loggerName must not be empty");
		this.messageLogger = LogFactory.getLog(loggerName);
	}

	/**
	 * Specify whether to log the full Message. Otherwise, only the payload will be logged. This value is
	 * <code>false</code> by default.
	 * @param shouldLogFullMessage true if the complete message should be logged.
	 */
	public void setShouldLogFullMessage(boolean shouldLogFullMessage) {
		Assert.isTrue(!(this.expressionSet), "Cannot set both 'expression' AND 'shouldLogFullMessage' properties");
		this.shouldLogFullMessageSet = true;
		this.expression = shouldLogFullMessage
				? EXPRESSION_PARSER.parseExpression("#root")
				: EXPRESSION_PARSER.parseExpression("payload");
	}

	@Override
	public String getComponentType() {
		return "logging-channel-adapter";
	}

	@Override
	protected void onInit() {
		super.onInit();
		this.evaluationContext = ExpressionUtils.createStandardEvaluationContext(getBeanFactory());
	}

	@Override
	protected void handleMessageInternal(Message<?> message) throws Exception {
		switch (this.level) {
			case FATAL:
				if (this.messageLogger.isFatalEnabled()) {
					this.messageLogger.fatal(createLogMessage(message));
				}
				break;
			case ERROR:
				if (this.messageLogger.isErrorEnabled()) {
					this.messageLogger.error(createLogMessage(message));
				}
				break;
			case WARN:
				if (this.messageLogger.isWarnEnabled()) {
					this.messageLogger.warn(createLogMessage(message));
				}
				break;
			case INFO:
				if (this.messageLogger.isInfoEnabled()) {
					this.messageLogger.info(createLogMessage(message));
				}
				break;
			case DEBUG:
				if (this.messageLogger.isDebugEnabled()) {
					this.messageLogger.debug(createLogMessage(message));
				}
				break;
			case TRACE:
				if (this.messageLogger.isTraceEnabled()) {
					this.messageLogger.trace(createLogMessage(message));
				}
				break;
			default:
				throw new IllegalStateException("Level '" + this.level + "' is not supported");
		}
	}

	private Object createLogMessage(Message<?> message) {
		Object logMessage = this.expression.getValue(this.evaluationContext, message);
		return logMessage instanceof Throwable
				? createLogMessage((Throwable) logMessage)
				: logMessage;
	}

	private String createLogMessage(Throwable throwable) {
		StringWriter stringWriter = new StringWriter();
		if (throwable instanceof AggregateMessageDeliveryException) {
			stringWriter.append(throwable.getMessage());
			for (Exception exception : ((AggregateMessageDeliveryException) throwable).getAggregatedExceptions()) {
				printStackTrace(exception, stringWriter);
			}
		}
		else {
			printStackTrace(throwable, stringWriter);
		}
		return stringWriter.toString();
	}

	private void printStackTrace(Throwable throwable, Writer writer) {
		throwable.printStackTrace(new PrintWriter(writer, true));
	}

}
