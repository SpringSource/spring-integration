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

package org.springframework.integration.mongodb.rules;

import org.junit.Rule;
import org.springframework.data.mongodb.MongoDbFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoDbFactory;

import com.mongodb.Mongo;

/**
 * Convenience base class that enables unit test methods to rely upon the {@link MongoDbAvailable} annotation.
 * 
 * @author Oleg Zhurakousky
 * @since 2.1
 */
public abstract class MongoDbAvailableTests {

	@Rule
	public MongoDbAvailableRule redisAvailableRule = new MongoDbAvailableRule();
	
	
	protected MongoDbFactory prepareMongoFactory() throws Exception{
		MongoDbFactory mongoDbFactory = new SimpleMongoDbFactory(new Mongo(), "test");
		MongoTemplate template = new MongoTemplate(mongoDbFactory);
		template.dropCollection("messages");
		return mongoDbFactory;
	}

}
