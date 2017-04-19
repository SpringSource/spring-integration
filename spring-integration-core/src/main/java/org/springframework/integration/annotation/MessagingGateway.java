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

package org.springframework.integration.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A stereotype annotation to provide an Integration Messaging Gateway Proxy
 * ({@code <gateway/>}) as an abstraction over the messaging API. The target
 * application's business logic may be completely unaware of the Spring Integration
 * API, with the code interacting only via the interface.
 * <p>
 * Important: The {@link IntegrationComponentScan} annotation is required along with
 * {@link org.springframework.context.annotation.Configuration}
 * to scan interfaces annotated with {@link MessagingGateway}, because the
 * standard {@link org.springframework.context.annotation.ComponentScan}
 * ignores interfaces.
 *
 * @author Artem Bilan
 * @since 4.0
 *
 * @see IntegrationComponentScan
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MessagingGateway {

	/**
	 * The value may indicate a suggestion for a logical component name,
	 * to be turned into a Spring bean in case of an autodetected component.
	 * @return the suggested component name, if any
	 */
	String name() default "";

	/**
	 * Identifies the default channel to which messages will be sent upon invocation
	 * of methods of the gateway proxy.
	 * @return the suggested channel name, if any
	 */
	String defaultRequestChannel() default "";

	/**
	 * Identifies the default channel the gateway proxy will subscribe to, to receive reply
	 * {@code Message}s, the payloads of
	 * which will be converted to the return type of the method signature.
	 * @return the suggested channel name, if any
	 */
	String defaultReplyChannel() default "";

	/**
	 * Identifies a channel that error messages will be sent to if a failure occurs in the
	 * gateway's proxy invocation. If no {@code errorChannel} reference is provided, the gateway will
	 * propagate {@code Exception}s to the caller. To completely suppress {@code Exception}s, provide a
	 * reference to the {@code nullChannel} here.
	 * @return the suggested channel name, if any
	 */
	String errorChannel() default "errorChannel";

	/**
	 * Provides the amount of time dispatcher would wait to send a {@code Message}.
	 * This timeout would only apply if there is a potential to block in the send call.
	 * For example if this gateway is hooked up to a {@code QueueChannel}.
	 * @return the suggested timeout in milliseconds, if any
	 */
	String defaultRequestTimeout() default "-9223372036854775808";

	/**
	 * Allows to specify how long this gateway will wait for the reply {@code Message}
	 * before returning. By default it will wait indefinitely. {@code null} is returned
	 if the gateway times out.
	 * @return the suggested timeout in milliseconds, if any
	 */
	String defaultReplyTimeout() default "-9223372036854775808";

	/**
	 * Provide a reference to an implementation of {@link java.util.concurrent.Executor}
	 * to use for any of the interface methods that have a {@link java.util.concurrent.Future} return type.
	 * This {@code Executor} will only be used for those async methods; the sync methods
	 * will be invoked in the caller's thread.
	 * Use {@link AnnotationConstants#NULL} to specify no async executor - for example
	 * if your downstream flow returns a {@link java.util.concurrent.Future}.
	 * @return the suggested executor bean name, if any
	 */
	String asyncExecutor() default "";

	/**
	 * An expression that will be used to generate the {@code payload} for all methods in the service interface
	 * unless explicitly overridden by a method declaration. Variables include {@code #args}, {@code #methodName},
	 * {@code #methodString} and {@code #methodObject};
	 * a bean resolver is also available, enabling expressions like {@code @someBean(#args)}.
	 * @return the suggested payload expression, if any
	 */
	String defaultPayloadExpression() default "";

	/**
	 * Provides custom message headers. These default headers are created for
	 * all methods on the service-interface (unless overridden by a specific method).
	 * @return the suggested payload expression, if any
	 */
	GatewayHeader[] defaultHeaders() default {};

	/**
	 * An {@link org.springframework.integration.gateway.MethodArgsMessageMapper}
	 * to map the method arguments to a {@link org.springframework.messaging.Message}. When this
	 * is provided, no {@code payload-expression}s or {@code header}s are allowed; the custom mapper is
	 * responsible for creating the message.
	 * @return the suggested mapper bean name, if any
	 */
	String mapper() default "";

}
