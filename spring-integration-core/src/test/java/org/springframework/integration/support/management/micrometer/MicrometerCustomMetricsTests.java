/*
 * Copyright 2018 the original author or authors.
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

package org.springframework.integration.support.management.micrometer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.config.EnableIntegrationManagement;
import org.springframework.integration.support.management.metrics.MetricsCaptor;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.test.context.junit4.SpringRunner;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.MeterNotFoundException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * @author Gary Russell
 *
 * @since 5.1
 *
 */
@RunWith(SpringRunner.class)
public class MicrometerCustomMetricsTests {

	@Autowired
	private ConfigurableApplicationContext context;

	@Autowired
	private MeterRegistry meterRegistry;

	@Autowired
	private QueueChannel queue;

	@Test
	public void testSend() throws Exception {
		GenericMessage<String> message = new GenericMessage<>("foo");
		this.queue.send(message);
		this.queue.receive();
		MeterRegistry registry = this.meterRegistry;
		assertThat(registry.get("spring.integration.channels").gauge().value()).isEqualTo(3);

		assertThat(registry.get("myTimer")
				.tag("standardTimerName", "spring.integration.send")
				.tag("name", "queue")
				.tag("result", "success")
				.timer().count()).isEqualTo(1);

		assertThat(registry.get("myCounter")
				.tag("standardCounterName", "spring.integration.receive")
				.tag("name", "queue")
				.tag("result", "success")
				.counter().count()).isEqualTo(1);

		// Test meter removal
		this.context.close();
		try {
			registry.get("myTimer").timers();
			fail("Expected MeterNotFoundException");
		}
		catch (MeterNotFoundException e) {
			assertThat(e).hasMessageContaining("No meter with name 'myTimer' was found");
		}
		try {
			registry.get("myCounter").counters();
			fail("Expected MeterNotFoundException");
		}
		catch (MeterNotFoundException e) {
			assertThat(e).hasMessageContaining("No meter with name 'myCounter' was found");
		}
	}

	@Configuration
	@EnableIntegration
	@EnableIntegrationManagement
	public static class Config {

		@Bean
		public MeterRegistry meterRegistry() {
			return new SimpleMeterRegistry();
		}

		@Bean
		public QueueChannel queue() {
			return new QueueChannel();
		}

		@Bean(name = MicrometerMetricsCaptor.MICROMETER_CAPTOR_NAME)
		public MetricsCaptor captor() {
			return new CustomMetricsCaptor(meterRegistry());
		}
	}

	static class CustomMetricsCaptor extends MicrometerMetricsCaptor {

		CustomMetricsCaptor(MeterRegistry meterRegistry) {
			super(meterRegistry);
		}

		@Override
		public TimerBuilder timerBuilder(String name) {
			return super.timerBuilder("myTimer")
					.tag("standardTimerName", name);
		}

		@Override
		public CounterBuilder counterBuilder(String name) {
			return super.counterBuilder("myCounter")
					.tag("standardCounterName", name);
		}

	}

}

