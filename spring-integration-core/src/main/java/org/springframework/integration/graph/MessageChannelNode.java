/*
 * Copyright 2016-2019 the original author or authors.
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

import org.springframework.messaging.MessageChannel;

/**
 * Represents a message channel.
 *
 * @author Gary Russell
 *
 * @since 4.3
 *
 */
@SuppressWarnings("deprecation")
public class MessageChannelNode extends IntegrationNode {

	public MessageChannelNode(int nodeId, String name, MessageChannel channel) {
		super(nodeId, name, channel,
				channel instanceof org.springframework.integration.support.management.MessageChannelMetrics
						? new Stats((org.springframework.integration.support.management.MessageChannelMetrics) channel)
						: new IntegrationNode.Stats());
	}


	public static final class Stats extends IntegrationNode.Stats {

		private final org.springframework.integration.support.management.MessageChannelMetrics channel;

		Stats(org.springframework.integration.support.management.MessageChannelMetrics channel) {
			this.channel = channel;
		}

		@Override
		protected boolean isAvailable() {
			return this.channel.isCountsEnabled();
		}

		public boolean isCountsEnabled() {
			return this.channel.isCountsEnabled();
		}

		public boolean isLoggingEnabled() {
			return this.channel.isLoggingEnabled();
		}

		public long getSendCount() {
			return this.channel.getSendCountLong();
		}

		public long getSendErrorCount() {
			return this.channel.getSendErrorCountLong();
		}

		public double getTimeSinceLastSend() {
			return this.channel.getTimeSinceLastSend();
		}

		public double getMeanSendRate() {
			return this.channel.getMeanSendRate();
		}

		public double getMeanErrorRate() {
			return this.channel.getMeanErrorRate();
		}

		public double getMeanErrorRatio() {
			return this.channel.getMeanErrorRatio();
		}

		public double getMeanSendDuration() {
			return this.channel.getMeanSendDuration();
		}

		public double getMinSendDuration() {
			return this.channel.getMinSendDuration();
		}

		public double getMaxSendDuration() {
			return this.channel.getMaxSendDuration();
		}

		public double getStandardDeviationSendDuration() {
			return this.channel.getStandardDeviationSendDuration();
		}

		public org.springframework.integration.support.management.Statistics getSendDuration() {
			return this.channel.getSendDuration();
		}

		public org.springframework.integration.support.management.Statistics getSendRate() {
			return this.channel.getSendRate();
		}

		public org.springframework.integration.support.management.Statistics getErrorRate() {
			return this.channel.getErrorRate();
		}

		public boolean isStatsEnabled() {
			return this.channel.isStatsEnabled();
		}

	}

}
