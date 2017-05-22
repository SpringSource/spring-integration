/*
 * Copyright 2016-2017 the original author or authors.
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

package org.springframework.integration.ip.dsl;

import java.util.Collections;
import java.util.Map;
import java.util.function.Function;

import org.springframework.integration.dsl.ComponentsRegistration;
import org.springframework.integration.dsl.MessageHandlerSpec;
import org.springframework.integration.expression.FunctionExpression;
import org.springframework.integration.ip.tcp.TcpOutboundGateway;
import org.springframework.integration.ip.tcp.connection.AbstractClientConnectionFactory;
import org.springframework.messaging.Message;

/**
 * A {@link MessageHandlerSpec} for {@link TcpOutboundGateway}s.
 *
 * @author Gary Russell
 * @author Artem Bilan
 *
 * @since 5.0
 *
 */
public class TcpOutboundGatewaySpec extends MessageHandlerSpec<TcpOutboundGatewaySpec, TcpOutboundGateway>
		implements ComponentsRegistration {

	private final AbstractClientConnectionFactory connectionFactory;

	/**
	 * Construct an instance using an existing spring-managed connection factory.
	 * @param connectionFactoryBean the spring-managed bean.
	 */
	public TcpOutboundGatewaySpec(AbstractClientConnectionFactory connectionFactoryBean) {
		this.target = new TcpOutboundGateway();
		this.connectionFactory = null;
		this.target.setConnectionFactory(connectionFactoryBean);
	}

	/**
	 * Construct an instance using the supplied connection factory spec.
	 * @param connectionFactorySpec the spec.
	 */
	public TcpOutboundGatewaySpec(TcpClientConnectionFactorySpec connectionFactorySpec) {
		this.target = new TcpOutboundGateway();
		this.connectionFactory = connectionFactorySpec.get();
		this.target.setConnectionFactory(this.connectionFactory);
	}

	/**
	 * @param remoteTimeout the remote timeout to set.
	 * @return the spec.
	 * @see TcpOutboundGateway#setRemoteTimeout(long)
	 */
	public TcpOutboundGatewaySpec remoteTimeout(long remoteTimeout) {
		this.target.setRemoteTimeout(remoteTimeout);
		return _this();
	}

	/**
	 * Configure a {@link Function} that will be invoked at runtime to determine the destination to
	 * which a message will be sent. Typically used with a Java 8 Lambda expression:
	 * <pre class="code">
	 * {@code
	 * .remoteTimeout(m -> m.getHeaders().get('rto'))
	 * }
	 * </pre>
	 * @param remoteTimeoutFunction the function.
	 * @param <P> the message payload type.
	 * @return the spec.
	 * @see TcpOutboundGateway#setRemoteTimeoutExpression(org.springframework.expression.Expression)
	 */
	public <P> TcpOutboundGatewaySpec remoteTimeout(Function<Message<P>, ?> remoteTimeoutFunction) {
		this.target.setRemoteTimeoutExpression(new FunctionExpression<>(remoteTimeoutFunction));
		return _this();
	}

	@Override
	public Map<Object, String> getComponentsToRegister() {
		return this.connectionFactory != null
				? Collections.singletonMap(this.connectionFactory, this.connectionFactory.getComponentName())
				: null;
	}

}
