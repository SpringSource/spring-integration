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

package org.springframework.integration.dsl;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.context.IntegrationObjectSupport;
import org.springframework.integration.router.AbstractMappingMessageRouter;
import org.springframework.integration.support.context.NamedComponent;
import org.springframework.integration.support.management.MappingMessageRouterManagement;
import org.springframework.messaging.MessagingException;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * The {@link AbstractRouterSpec} for an {@link AbstractMappingMessageRouter}.
 *
 * @param <K> the key type.
 * @param <R> the {@link AbstractMappingMessageRouter} implementation type.
 *
 * @author Artem Bilan
 *
 * @since 5.0
 */
public final class RouterSpec<K, R extends AbstractMappingMessageRouter>
		extends AbstractRouterSpec<RouterSpec<K, R>, R>
		implements ComponentsRegistration {

	private final RouterMappingProvider mappingProvider;

	private String prefix;

	private String suffix;

	private boolean mappingProviderRegistered;

	RouterSpec(R router) {
		super(router);
		this.mappingProvider = new RouterMappingProvider(this.handler);
	}

	/**
	 * @param resolutionRequired the resolutionRequired.
	 * @return the router spec.
	 * @see AbstractMappingMessageRouter#setResolutionRequired(boolean)
	 */
	public RouterSpec<K, R> resolutionRequired(boolean resolutionRequired) {
		this.handler.setResolutionRequired(resolutionRequired);
		return _this();
	}

	/**
	 * Set a limit for how many dynamic channels are retained (for reporting purposes).
	 * When the limit is exceeded, the oldest channel is discarded.
	 * <p><b>NOTE: this does not affect routing, just the reporting which dynamically
	 * resolved channels have been routed to.</b> Default {@code 100}.
	 * @param dynamicChannelLimit the limit.
	 * @return the router spec.
	 * @see AbstractMappingMessageRouter#setDynamicChannelLimit(int)
	 */
	public RouterSpec<K, R> dynamicChannelLimit(int dynamicChannelLimit) {
		this.handler.setDynamicChannelLimit(dynamicChannelLimit);
		return _this();
	}

	/**
	 * Cannot be invoked if {@link #subFlowMapping(Object, IntegrationFlow)} is used.
	 * @param prefix the prefix.
	 * @return the router spec.
	 * @see AbstractMappingMessageRouter#setPrefix(String)
	 */
	public RouterSpec<K, R> prefix(String prefix) {
		Assert.state(this.componentsToRegister.isEmpty(),
				"The 'prefix'('suffix') and 'subFlowMapping' are mutually exclusive");
		this.prefix = prefix;
		this.handler.setPrefix(prefix);
		return _this();
	}

	/**
	 * Cannot be invoked if {@link #subFlowMapping(Object, IntegrationFlow)} is used.
	 * @param suffix the suffix to set.
	 * @return the router spec.
	 * @see AbstractMappingMessageRouter#setSuffix(String)
	 */
	public RouterSpec<K, R> suffix(String suffix) {
		Assert.state(this.componentsToRegister.isEmpty(),
				"The 'prefix'('suffix') and 'subFlowMapping' are mutually exclusive");
		this.suffix = suffix;
		this.handler.setSuffix(suffix);
		return _this();
	}

	/**
	 * @param key the key.
	 * @param channelName the channelName.
	 * @return the router spec.
	 * @see AbstractMappingMessageRouter#setChannelMapping(String, String)
	 */
	public RouterSpec<K, R> channelMapping(K key, final String channelName) {
		Assert.notNull(key, "'key' must not be null");
		Assert.hasText(channelName, "'channelName' must not be null");
		if (key instanceof String) {
			this.handler.setChannelMapping((String) key, channelName);
		}
		else {
			this.mappingProvider.addMapping(key, new NamedComponent() {

				@Override
				public String getComponentName() {
					return channelName;
				}

				@Override
				public String getComponentType() {
					return "channel";
				}

			});
		}
		return _this();
	}

	/**
	 * Add a subflow as an alternative to a {@link #channelMapping(Object, String)}.
	 * {@link #prefix(String)} and {@link #suffix(String)} cannot be used when subflow
	 * mappings are used.
	 * @param key the key.
	 * @param subFlow the subFlow.
	 * @return the router spec.
	 */
	public RouterSpec<K, R> subFlowMapping(K key, IntegrationFlow subFlow) {
		Assert.notNull(key, "'key' must not be null");
		Assert.notNull(subFlow, "'subFlow' must not be null");
		Assert.state(!(StringUtils.hasText(this.prefix) || StringUtils.hasText(this.suffix)),
				"The 'prefix'('suffix') and 'subFlowMapping' are mutually exclusive");

		DirectChannel channel = new DirectChannel();
		IntegrationFlowBuilder flowBuilder = IntegrationFlows.from(channel);
		subFlow.configure(flowBuilder);

		this.componentsToRegister.add(flowBuilder);

		this.mappingProvider.addMapping(key, channel);
		return _this();
	}

	@Override
	public Collection<Object> getComponentsToRegister() {
		// The 'mappingProvider' must be added to the 'componentsToRegister' in the end to
		// let all other components to be registered before the 'RouterMappingProvider.onInit()' logic.
		if (!this.mappingProviderRegistered) {
			if (!this.mappingProvider.mapping.isEmpty()) {
				this.componentsToRegister.add(this.mappingProvider);
			}
			this.mappingProviderRegistered = true;
		}
		return super.getComponentsToRegister();
	}

	private static class RouterMappingProvider extends IntegrationObjectSupport
			implements ApplicationListener<ContextRefreshedEvent> {

		private final AtomicBoolean initialized = new AtomicBoolean();

		private final MappingMessageRouterManagement router;

		private final Map<Object, NamedComponent> mapping = new HashMap<Object, NamedComponent>();

		RouterMappingProvider(MappingMessageRouterManagement router) {
			this.router = router;
		}

		void addMapping(Object key, NamedComponent channel) {
			this.mapping.put(key, channel);
		}

		@Override
		public void onApplicationEvent(ContextRefreshedEvent event) {
			if (event.getApplicationContext() == getApplicationContext() && !this.initialized.getAndSet(true)) {
				ConversionService conversionService = getConversionService();
				if (conversionService == null) {
					conversionService = DefaultConversionService.getSharedInstance();
				}
				for (Map.Entry<Object, NamedComponent> entry : this.mapping.entrySet()) {
					Object key = entry.getKey();
					String channelKey;
					if (key instanceof String) {
						channelKey = (String) key;
					}
					else if (key instanceof Class) {
						channelKey = ((Class<?>) key).getName();
					}
					else if (conversionService.canConvert(key.getClass(), String.class)) {
						channelKey = conversionService.convert(key, String.class);
					}
					else {
						throw new MessagingException("Unsupported channel mapping type for router ["
								+ key.getClass() + "]");
					}

					this.router.setChannelMapping(channelKey, entry.getValue().getComponentName());
				}
			}
		}

	}

}
