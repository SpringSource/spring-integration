/*
 * Copyright 2009-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.springframework.integration.channel.management;

import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.integration.support.management.ExponentialMovingAverage;
import org.springframework.integration.support.management.ExponentialMovingAverageRate;
import org.springframework.integration.support.management.ExponentialMovingAverageRatio;
import org.springframework.integration.support.management.MetricsContext;
import org.springframework.integration.support.management.Statistics;

/**
 * Default implementation; use the full constructor to customize the moving averages.
 *
 * @author Dave Syer
 * @author Helena Edelson
 * @author Gary Russell
 * @since 2.0
 */
public class DefaultMessageChannelMetrics extends AbstractMessageChannelMetrics {

	protected final Log logger = LogFactory.getLog(getClass());

	public static final long ONE_SECOND_SECONDS = 1;

	public static final long ONE_MINUTE_SECONDS = 60;

	public static final int DEFAULT_MOVING_AVERAGE_WINDOW = 10;

	private final ExponentialMovingAverage sendDuration;

	private final ExponentialMovingAverageRate sendErrorRate;

	private final ExponentialMovingAverageRatio sendSuccessRatio;

	private final ExponentialMovingAverageRate sendRate;

	private final AtomicLong sendCount = new AtomicLong();

	private final AtomicLong sendErrorCount = new AtomicLong();

	protected final AtomicLong receiveCount = new AtomicLong();

	protected final AtomicLong receiveErrorCount = new AtomicLong();

	public DefaultMessageChannelMetrics() {
		this(null);
	}

	/**
	 * Construct an instance with default metrics with {@code window=10, period=1 second,
	 * lapsePeriod=1 minute}.
	 * @param name the name.
	 */
	public DefaultMessageChannelMetrics(String name) {
		this(name, new ExponentialMovingAverage(DEFAULT_MOVING_AVERAGE_WINDOW, 1000000.),
		new ExponentialMovingAverageRate(
				ONE_SECOND_SECONDS, ONE_MINUTE_SECONDS, DEFAULT_MOVING_AVERAGE_WINDOW, true),
		new ExponentialMovingAverageRatio(
				ONE_MINUTE_SECONDS, DEFAULT_MOVING_AVERAGE_WINDOW, true),
		new ExponentialMovingAverageRate(
				ONE_SECOND_SECONDS, ONE_MINUTE_SECONDS, DEFAULT_MOVING_AVERAGE_WINDOW, true));
	}

	/**
	 * Construct an instance with the suppplied metrics. For proper representation of metrics, the
	 * supplied sendDuration must have a {@code factor=1000000.} and the the other arguments
	 * must be created with the {@code millis} constructor argument set to true.
	 * @param name the name.
	 * @param sendDuration an {@link ExponentialMovingAverage} for calculating the send duration.
	 * @param sendErrorRate an {@link ExponentialMovingAverageRate} for calculating the send error rate.
	 * @param sendSuccessRatio an {@link ExponentialMovingAverageRatio} for calculating the success ratio.
	 * @param sendRate an {@link ExponentialMovingAverageRate} for calculating the send rate.
	 */
	public DefaultMessageChannelMetrics(String name, ExponentialMovingAverage sendDuration,
			ExponentialMovingAverageRate sendErrorRate, ExponentialMovingAverageRatio sendSuccessRatio,
			ExponentialMovingAverageRate sendRate) {
		super(name);
		this.sendDuration = sendDuration;
		this.sendErrorRate = sendErrorRate;
		this.sendSuccessRatio = sendSuccessRatio;
		this.sendRate = sendRate;
	}

	public void destroy() {
		if (logger.isDebugEnabled()) {
			logger.debug(sendDuration);
		}
	}

	@Override
	public MetricsContext beforeSend() {
		if (logger.isTraceEnabled()) {
			logger.trace("Recording send on channel(" + this.name + ")");
		}

		long start = 0;
		if (isFullStatsEnabled()) {
			start = System.nanoTime();
			this.sendRate.increment(start);
		}
		this.sendCount.incrementAndGet();
		return new DefaultChannelMetricsContext(start);
	}

	@Override
	public void afterSend(MetricsContext context, boolean result) {
		if (result && isFullStatsEnabled()) {
			long now = System.nanoTime();
			this.sendSuccessRatio.success(now);
			this.sendDuration.append(now - ((DefaultChannelMetricsContext) context).start);
		}
		else {
			if (isFullStatsEnabled()) {
				long now = System.nanoTime();
				this.sendSuccessRatio.failure(now);
				this.sendErrorRate.increment(now);
			}
			this.sendErrorCount.incrementAndGet();
		}
		if (logger.isTraceEnabled()) {
			logger.trace("Elapsed: " + this.name
					+ ": " + ((System.nanoTime() / 1000000. - ((DefaultChannelMetricsContext) context).start)) + "ms");
		}
	}

	@Override
	public synchronized void reset() {
		this.sendDuration.reset();
		this.sendErrorRate.reset();
		this.sendSuccessRatio.reset();
		this.sendRate.reset();
		this.sendCount.set(0);
		this.sendErrorCount.set(0);
		this.receiveErrorCount.set(0);
		this.receiveCount.set(0);
	}

	@Override
	public int getSendCount() {
		return (int) this.sendCount.get();
	}

	@Override
	public long getSendCountLong() {
		return this.sendCount.get();
	}

	@Override
	public int getSendErrorCount() {
		return (int) this.sendErrorCount.get();
	}

	@Override
	public long getSendErrorCountLong() {
		return this.sendErrorCount.get();
	}

	@Override
	public double getTimeSinceLastSend() {
		return this.sendRate.getTimeSinceLastMeasurement();
	}

	@Override
	public double getMeanSendRate() {
		return this.sendRate.getMean();
	}

	@Override
	public double getMeanErrorRate() {
		return this.sendErrorRate.getMean();
	}

	@Override
	public double getMeanErrorRatio() {
		return 1 - this.sendSuccessRatio.getMean();
	}

	@Override
	public double getMeanSendDuration() {
		return this.sendDuration.getMean();
	}

	@Override
	public double getMinSendDuration() {
		return this.sendDuration.getMin();
	}

	@Override
	public double getMaxSendDuration() {
		return this.sendDuration.getMax();
	}

	@Override
	public double getStandardDeviationSendDuration() {
		return this.sendDuration.getStandardDeviation();
	}

	@Override
	public Statistics getSendDuration() {
		return this.sendDuration.getStatistics();
	}

	@Override
	public Statistics getSendRate() {
		return this.sendRate.getStatistics();
	}

	@Override
	public Statistics getErrorRate() {
		return this.sendErrorRate.getStatistics();
	}

	@Override
	public void afterReceive() {
		if (logger.isTraceEnabled()) {
			logger.trace("Recording receive on channel(" + this.name + ") ");
		}
		this.receiveCount.incrementAndGet();
	}

	@Override
	public void afterError() {
		this.receiveErrorCount.incrementAndGet();
	}

	@Override
	public int getReceiveCount() {
		return (int) this.receiveCount.get();
	}

	@Override
	public long getReceiveCountLong() {
		return this.receiveCount.get();
	}

	@Override
	public int getReceiveErrorCount() {
		return (int) this.receiveErrorCount.get();
	}

	@Override
	public long getReceiveErrorCountLong() {
		return this.receiveErrorCount.get();
	}

	@Override
	public String toString() {
		return String.format("MessageChannelMonitor: [name=%s, sends=%d"
				+ (this.receiveCount.get() == 0 ? "" : this.receiveCount.get())
				+ "]", name, sendCount.get());
	}

	private static class DefaultChannelMetricsContext implements MetricsContext {

		private final long start;

		public DefaultChannelMetricsContext(long start) {
			this.start = start;
		}

	}

}
