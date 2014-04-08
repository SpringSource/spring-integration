/*
 * Copyright 2002-2014 the original author or authors.
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
package org.springframework.integration.jdbc.store.channel;

import java.util.concurrent.ExecutionException;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 *
 * @author Gunnar Hillert
 * @author Artem Bilan
 *
 */
@ContextConfiguration
@RunWith(SpringJUnit4ClassRunner.class)
@DirtiesContext // close at the end after class
public class HsqlTxTimeoutMessageStoreTests extends AbstractTxTimeoutMessageStoreTests {

	@Test
	@Override
	public void test() throws InterruptedException {
		super.test();
	}

	@Test
	@Override
	public void testInt2993IdCacheConcurrency() throws InterruptedException, ExecutionException {
		super.testInt2993IdCacheConcurrency();
	}

	@Test
	@Override
	public void testInt3181ConcurrentPolling() throws InterruptedException {
		super.testInt3181ConcurrentPolling();
	}

	@Test
	@Override
	public void testPriorityChannel() throws Exception {
		super.testPriorityChannel();
	}

}
