/*
 * Copyright 2002-2015 the original author or authors.
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

package org.springframework.integration.security.channel;

import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.integration.security.SecurityTestUtils;
import org.springframework.integration.security.TestHandler;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.PollableChannel;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * @author Mark Fisher
 * @author Oleg Zhurakousky
 * @author Artem Bilan
 */
@ContextConfiguration
@RunWith(SpringJUnit4ClassRunner.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class ChannelAdapterSecurityIntegrationTests {

	@Autowired
	@Qualifier("securedChannelAdapter")
	MessageChannel securedChannelAdapter;

	@Autowired
	@Qualifier("securedChannelAdapter")
	MessageChannel securedChannelAdapter2;

	@Autowired
	@Qualifier("unsecuredChannelAdapter")
	MessageChannel unsecuredChannelAdapter;

	@Autowired
	@Qualifier("securedChannelQueue")
	MessageChannel securedChannelQueue;

	@Autowired
	@Qualifier("resultChannel")
	PollableChannel resultChannel;

	@Autowired
	TestHandler testConsumer;


	@After
	public void tearDown() {
		SecurityContextHolder.clearContext();
	}


	@Test(expected = AccessDeniedException.class)
	public void testSecuredWithNotEnoughPermission() {
		login("bob", "bobspassword", "ROLE_ADMINA");
		securedChannelAdapter.send(new GenericMessage<String>("test"));
	}

	@Test
	public void testSecuredWithPermission() {
		login("bob", "bobspassword", "ROLE_ADMIN", "ROLE_PRESIDENT");
		securedChannelAdapter.send(new GenericMessage<String>("test"));
		securedChannelAdapter2.send(new GenericMessage<String>("test"));
		assertEquals("Wrong size of message list in target", 2, testConsumer.sentMessages.size());
	}

	@Test
	public void testSecurityContextPropagation() {
		login("bob", "bobspassword", "ROLE_ADMIN", "ROLE_PRESIDENT");
		this.securedChannelQueue.send(new GenericMessage<String>("test"));
		Message<?> receive = this.resultChannel.receive(10000);
		assertNotNull(receive);

		assertNull(SecurityContextHolder.getContext().getAuthentication());

		assertNotEquals(Thread.currentThread().getId(), receive.getHeaders().get("threadId"));

		// Without SecurityContext propagation we end up here with: AuthenticationCredentialsNotFoundException
		assertEquals(1, testConsumer.sentMessages.size());

		assertThat(receive.getPayload(), instanceOf(SecurityContext.class));
		SecurityContext securityContext = (SecurityContext) receive.getPayload();
		// Without SecurityContext cleanup we don't get here an empty SecurityContext from the taskScheduler Thread
		assertNull(securityContext.getAuthentication());
	}

	@Test(expected = AccessDeniedException.class)
	public void testSecuredWithoutPermission() {
		login("bob", "bobspassword", "ROLE_USER");
		securedChannelAdapter.send(new GenericMessage<String>("test"));
	}

	@Test(expected = AccessDeniedException.class)
	public void testSecured2WithoutPermission() {
		login("bob", "bobspassword", "ROLE_USER");
		securedChannelAdapter2.send(new GenericMessage<String>("test"));
	}

	@Test(expected = AuthenticationException.class)
	public void testSecuredWithoutAuthenticating() {
		securedChannelAdapter.send(new GenericMessage<String>("test"));
	}

	@Test
	public void testUnsecuredAsAdmin() {
		login("bob", "bobspassword", "ROLE_ADMIN");
		unsecuredChannelAdapter.send(new GenericMessage<String>("test"));
		assertEquals("Wrong size of message list in target", 1, testConsumer.sentMessages.size());
	}

	@Test
	public void testUnsecuredAsUser() {
		login("bob", "bobspassword", "ROLE_USER");
		unsecuredChannelAdapter.send(new GenericMessage<String>("test"));
		assertEquals("Wrong size of message list in target", 1, testConsumer.sentMessages.size());
	}

	@Test
	public void testUnsecuredWithoutAuthenticating() {
		unsecuredChannelAdapter.send(new GenericMessage<String>("test"));
		assertEquals("Wrong size of message list in target", 1, testConsumer.sentMessages.size());
	}


	private void login(String username, String password, String... roles) {
		SecurityContext context = SecurityTestUtils.createContext(username, password, roles);
		SecurityContextHolder.setContext(context);
	}

}
