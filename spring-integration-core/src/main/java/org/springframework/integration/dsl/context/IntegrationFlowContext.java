/*
 * Copyright 2016-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.integration.dsl.context;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.integration.core.MessagingTemplate;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.support.context.NamedComponent;
import org.springframework.messaging.MessageChannel;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * A public API for dynamic (manual) registration of {@link IntegrationFlow}s,
 * not via standard bean registration phase.
 * <p>
 * The bean of this component is provided via framework automatically.
 * A bean name is based on the decapitalized class name.
 * It must be injected to the target service before use.
 * <p>
 * The typical use-case, and, therefore algorithm, is:
 * <ul>
 * <li> create an {@link IntegrationFlow} instance depending of the business logic
 * <li> register that {@link IntegrationFlow} in this {@link IntegrationFlowContext},
 * with optional {@code id} and {@code autoStartup} flag
 * <li> obtain a {@link MessagingTemplate} for that {@link IntegrationFlow}
 * (if it is started from the {@link MessageChannel}) and send (or send-and-receive)
 * messages to the {@link IntegrationFlow}
 * <li> remove the {@link IntegrationFlow} by its {@code id} from this {@link IntegrationFlowContext}
 * </ul>
 * <p>
 * For convenience an associated {@link IntegrationFlowRegistration} is returned after registration.
 * It can be used for access to the target {@link IntegrationFlow} or for manipulation with its lifecycle.
 *
 * @author Artem Bilan
 *
 * @since 5.0
 *
 * @see IntegrationFlowRegistration
 */
public final class IntegrationFlowContext implements BeanFactoryAware {

	private final Map<String, IntegrationFlowRegistration> registry = new ConcurrentHashMap<>();

	private final Map<String, Boolean> useFlowIdAsPrefix = new ConcurrentHashMap<>();

	private final Lock registerFlowsLock = new ReentrantLock();

	private ConfigurableListableBeanFactory beanFactory;

	private BeanDefinitionRegistry beanDefinitionRegistry;

	private IntegrationFlowContext() {
	}

	@Override
	public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
		Assert.isInstanceOf(ConfigurableListableBeanFactory.class, beanFactory,
				"To use Spring Integration Java DSL the 'beanFactory' has to be an instance of " +
						"'ConfigurableListableBeanFactory'. " +
						"Consider using 'GenericApplicationContext' implementation.");
		this.beanFactory = (ConfigurableListableBeanFactory) beanFactory;
		this.beanDefinitionRegistry = (BeanDefinitionRegistry) this.beanFactory;
	}

	/**
	 * Associate provided {@link IntegrationFlow} with an {@link IntegrationFlowRegistrationBuilder}
	 * for additional options and farther registration in the application context.
	 * @param integrationFlow the {@link IntegrationFlow} to register
	 * @return the IntegrationFlowRegistrationBuilder associated with the provided {@link IntegrationFlow}
	 */
	public IntegrationFlowRegistrationBuilder registration(IntegrationFlow integrationFlow) {
		return new IntegrationFlowRegistrationBuilder(integrationFlow);
	}

	/**
	 * Return true to prefix flow bean names with the flow id and a period.
	 * @param flowId the flow id.
	 * @return true to use as a prefix.
	 * @since 5.0.6
	 */
	public boolean isUseIdAsPrefix(String flowId) {
		return Boolean.TRUE.equals(this.useFlowIdAsPrefix.get(flowId));
	}

	private void register(IntegrationFlowRegistrationBuilder builder) {
		IntegrationFlow integrationFlow = builder.integrationFlowRegistration.getIntegrationFlow();
		String flowId = builder.integrationFlowRegistration.getId();
		Lock registerBeanLock = null;
		try {
			if (flowId == null) {
				registerBeanLock = this.registerFlowsLock;
				registerBeanLock.lock();
				flowId = generateBeanName(integrationFlow, null);
				builder.id(flowId);
			}
			else if (this.registry.containsKey(flowId)) {
				throw new IllegalArgumentException("An IntegrationFlow '" + this.registry.get(flowId) +
						"' with flowId '" + flowId + "' is already registered.\n" +
						"An existing IntegrationFlowRegistration must be destroyed before overriding.");
			}

			integrationFlow = (IntegrationFlow) registerBean(integrationFlow, flowId, null);
		}
		finally {
			if (registerBeanLock != null) {
				registerBeanLock.unlock();
			}
		}

		builder.integrationFlowRegistration.setIntegrationFlow(integrationFlow);

		final String theFlowId = flowId;
		builder.additionalBeans.forEach((key, value) -> registerBean(key, value, theFlowId));

		if (builder.autoStartup) {
			builder.integrationFlowRegistration.start();
		}
		this.registry.put(flowId, builder.integrationFlowRegistration);
	}

	@SuppressWarnings("unchecked")
	private Object registerBean(Object bean, String beanName, String parentName) {
		if (beanName == null) {
			beanName = generateBeanName(bean, parentName);
		}

		BeanDefinition beanDefinition =
				BeanDefinitionBuilder.genericBeanDefinition((Class<Object>) bean.getClass(), () -> bean)
						.getRawBeanDefinition();

		((BeanDefinitionRegistry) this.beanFactory).registerBeanDefinition(beanName, beanDefinition);

		if (parentName != null) {
			this.beanFactory.registerDependentBean(parentName, beanName);
		}

		return this.beanFactory.getBean(beanName);
	}

	/**
	 * Obtain an {@link IntegrationFlowRegistration} for the {@link IntegrationFlow}
	 * associated with the provided {@code flowId}.
	 * @param flowId the bean name to obtain
	 * @return the IntegrationFlowRegistration for provided {@code id} or {@code null}
	 */
	public IntegrationFlowRegistration getRegistrationById(String flowId) {
		return this.registry.get(flowId);
	}

	/**
	 * Destroy an {@link IntegrationFlow} bean (as well as all its dependant beans)
	 * for provided {@code flowId} and clean up all the local cache for it.
	 * @param flowId the bean name to destroy from
	 */
	public void remove(String flowId) {
		if (this.registry.containsKey(flowId)) {
			IntegrationFlowRegistration flowRegistration = this.registry.remove(flowId);
			flowRegistration.stop();

			removeDependantBeans(flowId);

			this.beanDefinitionRegistry.removeBeanDefinition(flowId);
		}
		else {
			throw new IllegalStateException("An IntegrationFlow with the id "
					+ "[" + flowId + "] doesn't exist in the registry.");
		}
	}

	private void removeDependantBeans(String parentName) {
		String[] dependentBeans = this.beanFactory.getDependentBeans(parentName);
		for (String beanName : dependentBeans) {
			removeDependantBeans(beanName);
			this.beanDefinitionRegistry.removeBeanDefinition(beanName);
			// TODO until https://jira.spring.io/browse/SPR-16837
			String[] aliases = this.beanDefinitionRegistry.getAliases(beanName);
			for (String alias : aliases) {
				this.beanDefinitionRegistry.removeAlias(alias);
			}
		}
	}

	/**
	 * Obtain a {@link MessagingTemplate} with its default destination set to the input channel
	 * of the {@link IntegrationFlow} for provided {@code flowId}.
	 * <p> Any {@link IntegrationFlow} bean (not only manually registered) can be used for this method.
	 * <p> If {@link IntegrationFlow} doesn't start with the {@link MessageChannel}, the
	 * {@link IllegalStateException} is thrown.
	 * @param flowId the bean name to obtain the input channel from
	 * @return the {@link MessagingTemplate} instance
	 */
	public MessagingTemplate messagingTemplateFor(String flowId) {
		return this.registry.get(flowId)
				.getMessagingTemplate();
	}

	/**
	 * Provide the state of the mapping of integration flow names to their
	 * {@link IntegrationFlowRegistration} instances.
	 * @return the registry of flow ids and their registration.
	 */
	public Map<String, IntegrationFlowRegistration> getRegistry() {
		return Collections.unmodifiableMap(this.registry);
	}

	private String generateBeanName(Object instance, String parentName) {
		if (instance instanceof NamedComponent && ((NamedComponent) instance).getComponentName() != null) {
			return ((NamedComponent) instance).getComponentName();
		}
		String generatedBeanName = (parentName != null ? parentName : "") + instance.getClass().getName();
		String id = generatedBeanName;
		int counter = -1;
		while (counter == -1 || this.beanFactory.containsBean(id)) {
			counter++;
			id = generatedBeanName + BeanFactoryUtils.GENERATED_BEAN_NAME_SEPARATOR + counter;
		}
		return id;
	}

	/**
	 * @author Gary Russell
	 * @since 5.1
	 *
	 * A Builder pattern implementation for the options to register {@link IntegrationFlow}
	 * in the application context.

	 */
	public final class IntegrationFlowRegistrationBuilder {

		private final Map<Object, String> additionalBeans = new HashMap<>();

		private final IntegrationFlowRegistration integrationFlowRegistration;

		private boolean autoStartup = true;

		private boolean idAsPrefix;

		IntegrationFlowRegistrationBuilder(IntegrationFlow integrationFlow) {
			this.integrationFlowRegistration = new IntegrationFlowRegistration(integrationFlow);
			this.integrationFlowRegistration.setBeanFactory(IntegrationFlowContext.this.beanFactory);
			this.integrationFlowRegistration.setIntegrationFlowContext(IntegrationFlowContext.this);
		}

		/**
		 * Specify an {@code id} for the {@link IntegrationFlow} to register.
		 * Must be unique per context.
		 * The registration with this {@code id} must be destroyed before reusing for
		 * a new {@link IntegrationFlow} instance.
		 * @param id the id for the {@link IntegrationFlow} to register
		 * @return the current builder instance
		 */
		public IntegrationFlowRegistrationBuilder id(String id) {
			this.integrationFlowRegistration.setId(id);
			return this;
		}

		/**
		 * The {@code boolean} flag to indication if an {@link IntegrationFlow} must be started
		 * automatically after registration. Defaults to {@code true}.
		 * @param autoStartup start or not the {@link IntegrationFlow} automatically after registration.
		 * @return the current builder instance
		 */
		public IntegrationFlowRegistrationBuilder autoStartup(boolean autoStartup) {
			this.autoStartup = autoStartup;
			return this;
		}

		/**
		 * Add an object which will be registered as an {@link IntegrationFlow} dependant bean in the
		 * application context. Usually it is some support component, which needs an application context.
		 * For example dynamically created connection factories or header mappers for AMQP, JMS, TCP etc.
		 * @param bean an additional arbitrary bean to register into the application context.
		 * @return the current builder instance
		 */
		public IntegrationFlowRegistrationBuilder addBean(Object bean) {
			return addBean(null, bean);
		}

		/**
		 * Add an object which will be registered as an {@link IntegrationFlow} dependant bean in the
		 * application context. Usually it is some support component, which needs an application context.
		 * For example dynamically created connection factories or header mappers for AMQP, JMS, TCP etc.
		 * @param name the name for the bean to register.
		 * @param bean an additional arbitrary bean to register into the application context.
		 * @return the current builder instance
		 */
		public IntegrationFlowRegistrationBuilder addBean(String name, Object bean) {
			this.additionalBeans.put(bean, name);
			return this;
		}

		/**
		 * Invoke this method to prefix bean names in the flow with the (required) flow id
		 * and a period. This is useful if you wish to register the same flow multiple times
		 * while retaining the ability to reference beans within the flow; adding the unique
		 * flow id to the bean name makes the name unique.
		 * @return the current builder instance.
		 * @see #id(String)
		 * @since 5.0.6
		 */
		public IntegrationFlowRegistrationBuilder useFlowIdAsPrefix() {
			this.idAsPrefix = true;
			return this;
		}

		/**
		 * Register an {@link IntegrationFlow} and all the dependant and support components
		 * in the application context and return an associated {@link IntegrationFlowRegistration}
		 * control object.
		 * @return the {@link IntegrationFlowRegistration} instance.
		 */
		public IntegrationFlowRegistration register() {
			String id = this.integrationFlowRegistration.getId();
			Assert.state(!this.idAsPrefix || StringUtils.hasText(id),
					"An 'id' must be present to use 'useFlowIdAsPrefix'");
			if (this.idAsPrefix) {
				IntegrationFlowContext.this.useFlowIdAsPrefix.put(id, this.idAsPrefix);
			}
			IntegrationFlowContext.this.register(this);
			return this.integrationFlowRegistration;
		}

	}

}
