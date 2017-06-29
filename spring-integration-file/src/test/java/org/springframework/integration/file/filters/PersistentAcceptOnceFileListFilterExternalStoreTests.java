/*
 * Copyright 2014-2017 the original author or authors.
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

package org.springframework.integration.file.filters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sql.DataSource;

import org.apache.geode.cache.CacheFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.integration.gemfire.metadata.GemfireMetadataStore;
import org.springframework.integration.jdbc.metadata.JdbcMetadataStore;
import org.springframework.integration.metadata.ConcurrentMetadataStore;
import org.springframework.integration.redis.metadata.RedisMetadataStore;
import org.springframework.integration.redis.rules.RedisAvailable;
import org.springframework.integration.redis.rules.RedisAvailableTests;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * @author Gary Russell
 * @author Artem Bilan
 * @since 4.0
 *
 */
@ContextConfiguration
@RunWith(SpringJUnit4ClassRunner.class)
@DirtiesContext // close at the end after class
public class PersistentAcceptOnceFileListFilterExternalStoreTests extends RedisAvailableTests {

	@Autowired
	private DataSource dataSource;

	@Test
	@RedisAvailable
	public void testFileSystemWithRedisMetadataStore() throws Exception {
		RedisTemplate<String, ?> template = new RedisTemplate<String, Object>();
		template.setConnectionFactory(this.getConnectionFactoryForTest());
		template.setKeySerializer(new StringRedisSerializer());
		template.afterPropertiesSet();
		template.delete("persistentAcceptOnceFileListFilterRedisTests");

		try {
			this.testFileSystem(new RedisMetadataStore(this.getConnectionFactoryForTest(),
					"persistentAcceptOnceFileListFilterRedisTests"));
		}
		finally {
			template.delete("persistentAcceptOnceFileListFilterRedisTests");
		}
	}

	@Test
	public void testFileSystemWithGemfireMetadataStore() throws Exception {
		this.testFileSystem(new GemfireMetadataStore(new CacheFactory().create()));
	}

	@Test
	public void testFileSystemWithJdbcMetadataStore() throws Exception {
		JdbcMetadataStore metadataStore = new JdbcMetadataStore(dataSource);
		metadataStore.afterPropertiesSet();
		this.testFileSystem(metadataStore);
	}

	private void testFileSystem(ConcurrentMetadataStore store) throws Exception {
		final AtomicBoolean suspend = new AtomicBoolean();

		final CountDownLatch latch1 = new CountDownLatch(1);
		final CountDownLatch latch2 = new CountDownLatch(1);

		store = Mockito.spy(store);

		Mockito.doAnswer(invocation -> {
			if (suspend.get()) {
				latch2.countDown();
				try {
					latch1.await(10, TimeUnit.SECONDS);
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
			return invocation.callRealMethod();
		}).when(store).replace(Mockito.anyString(), Mockito.anyString(), Mockito.anyString());

		final FileSystemPersistentAcceptOnceFileListFilter filter =
				new FileSystemPersistentAcceptOnceFileListFilter(store, "foo:");
		final File file = File.createTempFile("foo", ".txt");
		assertEquals(1, filter.filterFiles(new File[] { file }).size());
		String ts = store.get("foo:" + file.getAbsolutePath());
		assertEquals(String.valueOf(file.lastModified()), ts);
		assertEquals(0, filter.filterFiles(new File[] { file }).size());
		file.setLastModified(file.lastModified() + 5000L);
		assertEquals(1, filter.filterFiles(new File[] { file }).size());
		ts = store.get("foo:" + file.getAbsolutePath());
		assertEquals(String.valueOf(file.lastModified()), ts);
		assertEquals(0, filter.filterFiles(new File[] { file }).size());

		suspend.set(true);
		file.setLastModified(file.lastModified() + 5000L);

		Future<Integer> result = Executors.newSingleThreadExecutor()
				.submit(() -> filter.filterFiles(new File[] { file }).size());
		assertTrue(latch2.await(10, TimeUnit.SECONDS));
		store.put("foo:" + file.getAbsolutePath(), "43");
		latch1.countDown();
		Integer theResult = result.get(10, TimeUnit.SECONDS);
		assertEquals(Integer.valueOf(0), theResult); // lost the race, key changed

		file.delete();
		filter.close();
	}

}
