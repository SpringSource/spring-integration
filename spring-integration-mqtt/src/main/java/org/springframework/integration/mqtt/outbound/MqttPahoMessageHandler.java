/*
 * Copyright 2002-2014 the original author or authors.
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
package org.springframework.integration.mqtt.outbound;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import org.springframework.integration.core.MessagingTemplate;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.integration.mqtt.support.MqttMessageConverter;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.util.Assert;

/**
 * Eclipse Paho implementation.
 *
 * @author Gary Russell
 * @since 4.0
 *
 */
public class MqttPahoMessageHandler extends AbstractMqttMessageHandler
		implements MqttCallback {

	private static final int DEFAULT_COMPLETION_TIMEOUT = 30000;

	private volatile int completionTimeout = DEFAULT_COMPLETION_TIMEOUT;

	private final MqttPahoClientFactory clientFactory;

	private volatile MqttAsyncClient client;

	private volatile boolean async;

	private final MessagingTemplate deliveryCompleteTemplate = new MessagingTemplate();

	private volatile MessageChannel deliveryCompleteChannel;

	/**
	 * Use this constructor for a single url (although it may be overridden
	 * if the server URI(s) are provided by the {@link MqttConnectOptions#getServerURIs()}
	 * provided by the {@link MqttPahoClientFactory}).
	 * @param url the URL.
	 * @param clientId The client id.
	 * @param clientFactory The client factory.
	 */
	public MqttPahoMessageHandler(String url, String clientId, MqttPahoClientFactory clientFactory) {
		super(url, clientId);
		this.clientFactory = clientFactory;
	}

	/**
	 * Use this constructor if the server URI(s) are provided by the {@link MqttConnectOptions#getServerURIs()}
	 * provided by the {@link MqttPahoClientFactory}.
	 * @param clientId The client id.
	 * @param clientFactory The client factory.
	 * @since 4.1
	 */
	public MqttPahoMessageHandler(String clientId, MqttPahoClientFactory clientFactory) {
		super(null, clientId);
		this.clientFactory = clientFactory;
	}

	/**
	 * Use this URL when you don't need additional {@link MqttConnectOptions}.
	 * @param url The URL.
	 * @param clientId The client id.
	 */
	public MqttPahoMessageHandler(String url, String clientId) {
		this(url, clientId, new DefaultMqttPahoClientFactory());
	}

	/**
	 * Set to true if you don't want to block when sending messages. Default false.
	 * When true, delivery complete messages will be sent to the delivery complete
	 * channel (if specified).
	 * @param async true for async.
	 * @since 4.1
	 */
	public void setAsync(boolean async) {
		this.async = async;
	}

	/**
	 * Set the completion timeout for async operations. Not settable using the namespace.
	 * Default 30000 milliseconds.
	 * @param completionTimeout The timeout.
	 * @since 4.1
	 */
	public void setCompletionTimeout(int completionTimeout) {
		this.completionTimeout = completionTimeout;
	}

	public void setDeliveryCompleteChannel(MessageChannel deliveryCompleteChannel) {
		Assert.notNull(deliveryCompleteChannel, "'deliveryCompleteChannel' cannot be null");
		this.deliveryCompleteChannel = deliveryCompleteChannel;
	}

	public void setSendTimeout(long sendTimeout) {
		this.deliveryCompleteTemplate.setSendTimeout(sendTimeout);
	}

	@Override
	protected void onInit() throws Exception {
		super.onInit();
		Assert.state(getConverter() instanceof MqttMessageConverter,
				"MessageConverter must be an MqttMessageConverter");
	}

	@Override
	protected void doStart() {
	}

	@Override
	protected void doStop() {
		try {
			if (this.client != null) {
				this.client.disconnect()
						.waitForCompletion(this.completionTimeout);
				this.client.close();
				this.client = null;
			}
		}
		catch (MqttException e) {
			logger.error("Failed to disconnect", e);
		}
	}

	private synchronized void doConnect() throws MqttException {
		if (this.client != null && !this.client.isConnected()) {
			this.client.close();
			this.client = null;
		}
		if (this.client == null) {
			MqttConnectOptions connectionOptions = this.clientFactory.getConnectionOptions();
			Assert.state(this.getUrl() != null || connectionOptions.getServerURIs() != null,
					"If no 'url' provided, connectionOptions.getServerURIs() must not be null");
			this.client = this.clientFactory.getAsyncClientInstance(this.getUrl(), this.getClientId());
			this.client.setCallback(this);
			this.client.connect(connectionOptions)
			.waitForCompletion(this.completionTimeout);
			if (logger.isDebugEnabled()) {
				logger.debug("Client connected");
			}
		}
	}

	@Override
	protected void connectIfNeeded() {
		if (this.client == null || !this.client.isConnected()) {
			try {
				this.doConnect();
			}
			catch (MqttException e) {
				throw new MessagingException("Failed to connect", e);
			}
		}
	}

	@Override
	protected void publish(String topic, Object mqttMessage, Message<?> message) throws Exception {
		Assert.isInstanceOf(MqttMessage.class, mqttMessage);
		IMqttDeliveryToken token = this.client.publish(topic, (MqttMessage) mqttMessage);
		if (!this.async) {
			token.waitForCompletion(this.completionTimeout);
		}
		else if (this.deliveryCompleteChannel != null) {
			Message<?> pendingMessage = getMessageBuilderFactory().fromMessage(message)
					.setHeader(MqttHeaders.MESSAGE_ID, token.getMessageId())
					.setHeader(MqttHeaders.TOPIC, topic)
					.build();
			try {
				this.deliveryCompleteTemplate.send(this.deliveryCompleteChannel, pendingMessage);
			}
			catch (MessagingException e) {
				logger.error("Failed to send pending delivery message " + message, e);
			}
		}
	}

	private void sendDeliveryComplete(IMqttDeliveryToken token) {
		if (this.deliveryCompleteChannel != null) {
			Message<?> message = null;
			try {
				String[] topics = token.getTopics();
				String topic = topics != null && topics.length > 0 ? topics[0] : "unknown";
				this.deliveryCompleteTemplate.send(this.deliveryCompleteChannel,
						getMessageBuilderFactory().withPayload(token.getMessageId())
							.setHeader(MqttHeaders.MESSAGE_ID, token.getMessageId())
							.setHeader(MqttHeaders.TOPIC, topic)
							.build());
			}
			catch (MessagingException e) {
				logger.error("Failed to send delivery complete message " + message, e);
			}
		}
	}

	@Override
	public void connectionLost(Throwable cause) {
		logger.error("Lost connection; will attempt reconnect on next request");
		this.client = null;
	}

	@Override
	public void messageArrived(String topic, MqttMessage message) throws Exception {

	}

	@Override
	public void deliveryComplete(IMqttDeliveryToken token) {
		sendDeliveryComplete(token);
	}

}
