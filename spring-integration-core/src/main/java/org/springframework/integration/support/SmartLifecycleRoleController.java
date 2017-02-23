/*
 * Copyright 2015-2017 the original author or authors.
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

package org.springframework.integration.support;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.SmartLifecycle;
import org.springframework.integration.leader.event.AbstractLeaderEvent;
import org.springframework.integration.leader.event.OnGrantedEvent;
import org.springframework.integration.leader.event.OnRevokedEvent;
import org.springframework.integration.support.context.NamedComponent;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * Bulk start/stop {@link SmartLifecycle} in a particular role in phase order.
 *
 * @author Gary Russell
 * @author Artem Bilan
 *
 * @since 4.2
 *
 */
public class SmartLifecycleRoleController implements ApplicationListener<AbstractLeaderEvent>,
		ApplicationContextAware {

	private static final Log logger = LogFactory.getLog(SmartLifecycleRoleController.class);

	private final MultiValueMap<String, SmartLifecycle> lifecycles = new LinkedMultiValueMap<String, SmartLifecycle>();

	private final MultiValueMap<String, String> lazyLifecycles = new LinkedMultiValueMap<String, String>();

	private ApplicationContext applicationContext;

	/**
	 * Construct an instance with the provided lists of roles and lifecycles, which must be of equal length.
	 * @param roles the roles.
	 * @param lifecycles the lifecycles corresponding to the roles.
	 */
	public SmartLifecycleRoleController(List<String> roles, List<SmartLifecycle> lifecycles) {
		Assert.notNull(roles, "'roles' cannot be null");
		Assert.notNull(lifecycles, "'lifecycles' cannot be null");
		Assert.isTrue(roles.size() == lifecycles.size(), "'roles' and 'lifecycles' must be the same lenght");
		Iterator<SmartLifecycle> iterator = lifecycles.iterator();
		for (String role : roles) {
			SmartLifecycle lifecycle = iterator.next();
			addLifecycleToRole(role, lifecycle);
		}
	}

	/**
	 * Construct an instance with the provided map of roles/instances.
	 * @param lifcycles the {@link MultiValueMap} of beans in roles.
	 */
	public SmartLifecycleRoleController(MultiValueMap<String, SmartLifecycle> lifcycles) {
		for (Entry<String, List<SmartLifecycle>> lifecyclesInRole : lifcycles.entrySet()) {
			String role = lifecyclesInRole.getKey();
			for (SmartLifecycle lifecycle : lifecyclesInRole.getValue()) {
				addLifecycleToRole(role, lifecycle);
			}
		}
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}

	/**
	 * Add a {@link SmartLifecycle} to the role.
	 * @param role the role.
	 * @param lifecycle the {@link SmartLifecycle}.
	 */
	public final void addLifecycleToRole(String role, SmartLifecycle lifecycle) {
		this.lifecycles.add(role, lifecycle);
	}

	/**
	 * Add a {@link SmartLifecycle} bean to the role using its name.
	 * @param role the role.
	 * @param lifecycleBeanName the bean name of the {@link SmartLifecycle}.
	 */
	public void addLifecycleToRole(String role, String lifecycleBeanName) {
		Assert.state(this.applicationContext != null, "An application context is required to use this method");
		this.lazyLifecycles.add(role, lifecycleBeanName);
	}

	/**
	 * Add a {@link SmartLifecycle} beans to the role using their names.
	 * @param role the role.
	 * @param lifecycleBeanNames the bean names of the {@link SmartLifecycle}s.
	 */
	public void addLifecyclesToRole(String role, List<String> lifecycleBeanNames) {
		Assert.state(this.applicationContext != null, "An application context is required to use this method");
		for (String lifecycleBeanName : lifecycleBeanNames) {
			this.lazyLifecycles.add(role, lifecycleBeanName);
		}
	}

	/**
	 * Start all registered {@link SmartLifecycle}s in the role.
	 * @param role the role.
	 */
	public void startLifecyclesInRole(String role) {
		if (this.lazyLifecycles.size() > 0) {
			addLazyLifecycles();
		}
		List<SmartLifecycle> lifecycles = this.lifecycles.get(role);
		if (lifecycles != null) {
			lifecycles = new ArrayList<SmartLifecycle>(lifecycles);
			Collections.sort(lifecycles, new Comparator<SmartLifecycle>() {

				@Override
				public int compare(SmartLifecycle o1, SmartLifecycle o2) {
					return o1.getPhase() < o2.getPhase() ? -1
							: o1.getPhase() > o2.getPhase() ? 1 : 0;
				}

			});
			if (logger.isDebugEnabled()) {
				logger.debug("Starting " + lifecycles + " in role " + role);
			}
			for (SmartLifecycle lifecycle : lifecycles) {
				try {
					lifecycle.start();
				}
				catch (Exception e) {
					logger.error("Failed to start " + lifecycle + " in role " + role, e);
				}
			}
		}
		else {
			if (logger.isDebugEnabled()) {
				logger.debug("No components in role " + role + ". Nothing to start");
			}
		}
	}

	/**
	 * Stop all registered {@link SmartLifecycle}s in the role.
	 * @param role the role.
	 */
	public void stopLifecyclesInRole(String role) {
		if (this.lazyLifecycles.size() > 0) {
			addLazyLifecycles();
		}
		List<SmartLifecycle> lifecycles = this.lifecycles.get(role);
		if (lifecycles != null) {
			lifecycles = new ArrayList<SmartLifecycle>(lifecycles);
			Collections.sort(lifecycles, new Comparator<SmartLifecycle>() {

				@Override
				public int compare(SmartLifecycle o1, SmartLifecycle o2) {
					return o1.getPhase() < o2.getPhase() ? 1
							: o1.getPhase() > o2.getPhase() ? -1 : 0;
				}

			});
			if (logger.isDebugEnabled()) {
				logger.debug("Stopping " + lifecycles + " in role " + role);
			}
			for (SmartLifecycle lifecycle : lifecycles) {
				try {
					lifecycle.stop();
				}
				catch (Exception e) {
					logger.error("Failed to stop " + lifecycle + " in role " + role, e);
				}
			}
		}
		else {
			if (logger.isDebugEnabled()) {
				logger.debug("No components in role " + role + ". Nothing to stop");
			}
		}
	}

	/**
	 * Return a collection of the roles currently managed by this controller.
	 * @return the roles.
	 * @since 4.3.8
	 */
	public Collection<String> getRoles() {
		if (this.lazyLifecycles.size() > 0) {
			addLazyLifecycles();
		}
		return new ArrayList<String>(this.lifecycles.keySet());
	}

	/**
	 * Return true if all endpoints in the role are running.
	 * @param role the role.
	 * @return true if at least one endpoint in the role, and all are running.
	 * @since 4.3.8
	 */
	public boolean allEndpointsRunning(String role) {
		Map<String, Boolean> status = getEndpointsRunningStatus(role);
		if (status.isEmpty()) {
			return false;
		}
		else {
			for (Boolean isRunning : status.values()) {
				if (!isRunning) {
					return false;
				}
			}
			return true;
		}
	}

	/**
	 * Return true if none of the endpoints in the role are running or if
	 * there are no endpoints in the role.
	 * @param role the role.
	 * @return true if there are no endpoints or none are running.
	 * @since 4.3.8
	 */
	public boolean noEndpointsRunning(String role) {
		Map<String, Boolean> status = getEndpointsRunningStatus(role);
		for (Boolean isRunning : status.values()) {
			if (isRunning) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Return the running status of each endpoint in the role.
	 * @param role the role.
	 * @return A map of component names : running status
	 * @since 4.3.8
	 */
	public Map<String, Boolean> getEndpointsRunningStatus(String role) {
		if (this.lazyLifecycles.size() > 0) {
			addLazyLifecycles();
		}
		List<SmartLifecycle> endpoints = this.lifecycles.get(role);
		if (endpoints == null || endpoints.isEmpty()) {
			return Collections.emptyMap();
		}
		int index = 0;
		Map<String, Boolean> runners = new HashMap<String, Boolean>();
		for (SmartLifecycle lifecycle : endpoints) {
			runners.put(lifecycle instanceof NamedComponent
							? ((NamedComponent) lifecycle).getComponentName()
							: (lifecycle.getClass().getSimpleName() + "#" + index++),
						lifecycle.isRunning());
		}
		return runners;
	}

	private synchronized void addLazyLifecycles() {
		for (Entry<String, List<String>> entry : this.lazyLifecycles.entrySet()) {
			doAddLifecyclesToRole(entry.getKey(), entry.getValue());
		}
		this.lazyLifecycles.clear();
	}

	private void doAddLifecyclesToRole(String role, List<String> lifecycleBeanNames) {
		for (String lifecycleBeanName : lifecycleBeanNames) {
			try {
				SmartLifecycle lifecycle = this.applicationContext.getBean(lifecycleBeanName, SmartLifecycle.class);
				addLifecycleToRole(role, lifecycle);
			}
			catch (NoSuchBeanDefinitionException e) {
				logger.warn("Skipped; no such bean: " + lifecycleBeanName);
			}
		}
	}

	@Override
	public void onApplicationEvent(AbstractLeaderEvent event) {
		if (event instanceof OnGrantedEvent) {
			startLifecyclesInRole(event.getRole());
		}
		else if (event instanceof OnRevokedEvent) {
			stopLifecyclesInRole(event.getRole());
		}
	}

}
