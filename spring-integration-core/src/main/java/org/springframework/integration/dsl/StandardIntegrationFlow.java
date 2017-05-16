/*
 * Copyright 2016 the original author or authors.
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

import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.context.SmartLifecycle;

/**
 * The general implementation of the {@link IntegrationFlow} instantiated
 * by the Framework. Represents a logical container for the components configured
 * for the integration flow. Can be treated as a single component, especially
 * when the manual declaration with the
 * {@link org.springframework.integration.dsl.context.IntegrationFlowContext}.
 * <p>
 * Being logical container for the target integration component, this class controls
 * lifecycle of all components, when its {@code start()} and {@code stop()} are performed.
 * <p>
 * This component isn't {@code autoStartup} by default, because all the components are
 * registered as beans in the application context and their initial start up phase is
 * controlled from the lifecycle processor automatically.
 * <p>
 * When we register {@link IntegrationFlow} manually using
 * {@link org.springframework.integration.dsl.context.IntegrationFlowContext} API,
 * the lifecycle processor from the application context isn't involved any more,
 * therefore we should control {@code autoStartup} manually or reply on
 * {@link org.springframework.integration.dsl.context.IntegrationFlowContext} API.
 * <p>
 * This component doesn't track its {@code running} state during {@code stop()} action
 * and delegate directly to the registered components to avoid dangling processes
 * after removal manually registered {@link IntegrationFlow}.
 *
 * @author Artem Bilan
 *
 * @since 5.0
 *
 * @see IntegrationFlows
 * @see org.springframework.integration.config.dsl.IntegrationFlowBeanPostProcessor
 * @see org.springframework.integration.dsl.context.IntegrationFlowContext
 */
public class StandardIntegrationFlow implements IntegrationFlow, SmartLifecycle {

	private final List<Object> integrationComponents;

	private final List<SmartLifecycle> lifecycles = new LinkedList<>();

	private final boolean registerComponents = true; // NOSONAR

	private boolean running;

	StandardIntegrationFlow(Set<Object> integrationComponents) {
		this.integrationComponents = new LinkedList<>(integrationComponents);
	}

	//TODO Figure out some custom DestinationResolver when we don't register singletons - remove NOSONAR above when done
	/*public void setRegisterComponents(boolean registerComponents) {
		this.registerComponents = registerComponents;
	}*/

	public boolean isRegisterComponents() {
		return this.registerComponents;
	}

	public void setIntegrationComponents(List<Object> integrationComponents) {
		this.integrationComponents.clear();
		this.integrationComponents.addAll(integrationComponents);
	}

	public List<Object> getIntegrationComponents() {
		return this.integrationComponents;
	}

	@Override
	public void configure(IntegrationFlowDefinition<?> flow) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void start() {
		if (!this.running) {
			ListIterator<Object> iterator = this.integrationComponents.listIterator(this.integrationComponents.size());
			this.lifecycles.clear();
			while (iterator.hasPrevious()) {
				Object component = iterator.previous();
				if (component instanceof SmartLifecycle) {
					this.lifecycles.add((SmartLifecycle) component);
					((SmartLifecycle) component).start();
				}
			}
			this.running = true;
		}
	}

	@Override
	public void stop(Runnable callback) {
		AggregatingCallback aggregatingCallback = new AggregatingCallback(this.lifecycles.size(), callback);
		ListIterator<SmartLifecycle> iterator = this.lifecycles.listIterator(this.lifecycles.size());
		while (iterator.hasPrevious()) {
			SmartLifecycle lifecycle = iterator.previous();
			if (lifecycle.isRunning()) {
				lifecycle.stop(aggregatingCallback);
			}
			else {
				aggregatingCallback.run();
			}
		}
		this.running = false;
	}

	@Override
	public void stop() {
		ListIterator<SmartLifecycle> iterator = this.lifecycles.listIterator(this.lifecycles.size());
		while (iterator.hasPrevious()) {
			iterator.previous().stop();
		}
		this.running = false;
	}

	@Override
	public boolean isRunning() {
		return this.running;
	}

	@Override
	public boolean isAutoStartup() {
		return false;
	}

	@Override
	public int getPhase() {
		return 0;
	}

	private static final class AggregatingCallback implements Runnable {

		private final AtomicInteger count;

		private final Runnable finishCallback;

		AggregatingCallback(int count, Runnable finishCallback) {
			this.count = new AtomicInteger(count);
			this.finishCallback = finishCallback;
		}

		@Override
		public void run() {
			if (this.count.decrementAndGet() <= 0) {
				this.finishCallback.run();
			}
		}

	}


}
