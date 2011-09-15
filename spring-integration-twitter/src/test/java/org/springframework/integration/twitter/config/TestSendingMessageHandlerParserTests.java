/*
 * Copyright 2002-2011 the original author or authors.
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

package org.springframework.integration.twitter.config;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.integration.endpoint.EventDrivenConsumer;
import org.springframework.integration.test.util.TestUtils;
import org.springframework.integration.twitter.outbound.DirectMessageSendingMessageHandler;

/**
 * @author Oleg Zhurakousky
 * @author Gary Russell
 * @since 2.0
 */
public class TestSendingMessageHandlerParserTests {

//	@Test
//	public void testSendingMessageHandlerSuccessfulBootstrap(){
//		ApplicationContext ac = new ClassPathXmlApplicationContext("TestSendingMessageHandlerParser-context.xml", this.getClass());
//		EventDrivenConsumer dmAdapter = ac.getBean("dmAdapter", EventDrivenConsumer.class);
//		Object handler = TestUtils.getPropertyValue(dmAdapter, "handler");
//		assertEquals(DirectMessageSendingMessageHandler.class, handler.getClass());
//		assertEquals(23, TestUtils.getPropertyValue(handler, "order"));
//	}

}
