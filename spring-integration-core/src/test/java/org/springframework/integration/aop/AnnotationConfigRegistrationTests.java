/*
 * Copyright 2002-2018 the original author or authors.
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

package org.springframework.integration.aop;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.integration.annotation.Publisher;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.test.util.TestUtils;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * @author Mark Fisher
 * @author Gunnar Hillert
 * @author Artem Bilan
 *
 * @since 2.0
 */
@ContextConfiguration
@RunWith(SpringJUnit4ClassRunner.class)
public class AnnotationConfigRegistrationTests {

	@Autowired
	private TestBean testBean;

	@Autowired
	private QueueChannel annotationConfigRegistrationTest;

	@Autowired
	private QueueChannel defaultChannel;

	@Autowired
	private PublisherAnnotationBeanPostProcessor publisherAnnotationBeanPostProcessor;


	@Test // INT-1200
	public void verifyInterception() {
		String name = testBean.setName("John", "Doe", 123);
		assertThat(name).isNotNull();
		Message<?> message = annotationConfigRegistrationTest.receive(0);
		assertThat(message).isNotNull();
		assertThat(message.getPayload()).isEqualTo("John DoeDoe");
		assertThat(message.getHeaders().get("x")).isEqualTo(123);

		assertThat(TestUtils.getPropertyValue(this.publisherAnnotationBeanPostProcessor,
				"advisor.metadataSource.metadataCacheLimit"))
				.isEqualTo(50);
	}

	@Test
	public void defaultChannel() {
		String result = testBean.exclaim("hello");
		assertThat(result).isEqualTo("HELLO!!!");
		Message<?> message = defaultChannel.receive(0);
		assertThat(message).isNotNull();
		assertThat(message.getPayload()).isEqualTo("HELLO!!!");
	}

	public static class TestBean {

		@Publisher(channel = "annotationConfigRegistrationTest")
		@Payload("#return + #args.lname")
		public String setName(String fname, String lname, @Header("x") int num) {
			return fname + " " + lname;
		}

		@Publisher
		public String exclaim(String s) {
			return s.toUpperCase() + "!!!";
		}

	}

}
