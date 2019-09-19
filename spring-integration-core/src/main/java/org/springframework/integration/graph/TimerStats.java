/*
 * Copyright 2019 the original author or authors.
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

package org.springframework.integration.graph;

/**
 * Statistics captured from a timer meter.
 *
 * @author Gary Russell
 * @since 5.2
 *
 */
public class TimerStats {

	private final long count;

	private final double mean;

	private final double max;

	public TimerStats(long count, double mean, double max) {
		this.count = count;
		this.mean = mean;
		this.max = max;
	}

	public long getCount() {
		return this.count;
	}

	public double getMean() {
		return this.mean;
	}

	public double getMax() {
		return this.max;
	}

}
