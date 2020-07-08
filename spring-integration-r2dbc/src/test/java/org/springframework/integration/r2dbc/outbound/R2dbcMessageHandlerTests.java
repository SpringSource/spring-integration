/*
 * Copyright 2020 the original author or authors.
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

package org.springframework.integration.r2dbc.outbound;


import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.r2dbc.core.DatabaseClient;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.integration.expression.FunctionExpression;
import org.springframework.integration.r2dbc.config.R2dbcDatabaseConfiguration;
import org.springframework.integration.r2dbc.entity.Person;
import org.springframework.integration.r2dbc.repository.PersonRepository;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * @author Rohan Mukesh
 *
 * @since 5.4
 */
@SpringJUnitConfig
@DirtiesContext
public class R2dbcMessageHandlerTests {

	@Autowired
	DatabaseClient client;

	@Autowired
	PersonRepository personRepository;

	@Autowired
	R2dbcMessageHandler r2dbcMessageHandler;

	@BeforeEach
	public void setup() {
		Hooks.onOperatorDebug();
		r2dbcMessageHandler.setQueryType(R2dbcMessageHandler.Type.INSERT);
		r2dbcMessageHandler.setTableNameExpression(null);
		List<String> statements = Arrays.asList(
				"DROP TABLE IF EXISTS person;",
				"CREATE table person (id INT AUTO_INCREMENT NOT NULL, name VARCHAR2, age INT NOT NULL);");

		statements.forEach(it -> client.execute(it)
				.fetch()
				.rowsUpdated()
				.as(StepVerifier::create)
				.expectNextCount(1)
				.verifyComplete());
	}

	@Test
	public void validateMessageHandlingWithDefaultInsertCollection() {
		Message<Person> message = MessageBuilder.withPayload(createPerson("Bob", 35)).build();
		waitFor(r2dbcMessageHandler.handleMessage(message));

		personRepository.findAll()
				.as(StepVerifier::create)
				.expectNextCount(1)
				.verifyComplete();
	}

	@Test
	public void validateMessageHandlingWithInsertQueryCollection() {
		r2dbcMessageHandler.setValuesExpression(new FunctionExpression<Message<?>>(Message::getPayload));
		r2dbcMessageHandler.setQueryType(R2dbcMessageHandler.Type.INSERT);
		r2dbcMessageHandler.setTableName("person");
		Map<String, Object> payload = new HashMap<>();
		payload.put("name", "rohan");
		payload.put("age", 35);
		Message<?> message = MessageBuilder.withPayload(payload).build();
		waitFor(r2dbcMessageHandler.handleMessage(message));

		Flux<?> all = client.execute("SELECT name, age FROM person")
				.fetch().all();
		all.as(StepVerifier::create)
				.expectNextCount(1)
				.verifyComplete();

	}

	@Test
	public void validateMessageHandlingWithDefaultUpdateCollection() {
		Message<Person> message = MessageBuilder.withPayload(createPerson("Bob", 35)).build();
		waitFor(r2dbcMessageHandler.handleMessage(message));

		r2dbcMessageHandler.setQueryType(R2dbcMessageHandler.Type.UPDATE);

		Person person = this.client.select()
				.from("person")
				.as(Person.class)
				.fetch()
				.first()
				.block();

		person.setAge(40);

		message = MessageBuilder.withPayload(person)
				.build();
		waitFor(r2dbcMessageHandler.handleMessage(message));

		personRepository.findAll()
				.as(StepVerifier::create)
				.consumeNextWith(p -> Assert.assertEquals(Optional.of(40), Optional.ofNullable(p.getAge())))
				.verifyComplete();
	}

	@Test
	public void validateMessageHandlingWithUpdateQueryCollection() {
		r2dbcMessageHandler.setValuesExpression(new FunctionExpression<Message<?>>(Message::getPayload));
		r2dbcMessageHandler.setQueryType(R2dbcMessageHandler.Type.INSERT);
		r2dbcMessageHandler.setTableName("person");
		Map<String, Object> payload = new HashMap<>();
		payload.put("name", "Bob");
		payload.put("age", 35);
		Message<?> message = MessageBuilder.withPayload(payload).build();
		waitFor(r2dbcMessageHandler.handleMessage(message));

		payload = new HashMap<>();
		payload.put("name", "Rob");
		payload.put("age", 43);
		message = MessageBuilder.withPayload(payload).build();
		waitFor(r2dbcMessageHandler.handleMessage(message));

		payload = new HashMap<>();
		r2dbcMessageHandler.setQueryType(R2dbcMessageHandler.Type.UPDATE);

		Object insertedId = client.execute("SELECT id FROM person")
				.fetch()
				.first()
				.block()
				.get("id");

		r2dbcMessageHandler.setCriteriaExpression(new FunctionExpression<Message<?>>((m) -> Criteria.where("id").is(insertedId)));
		payload.put("age", 40);

		message = MessageBuilder.withPayload(payload).build();
		waitFor(r2dbcMessageHandler.handleMessage(message));

		Flux<?> all = client.execute("SELECT age,name FROM person where age=40")
				.fetch().all();
		all.as(StepVerifier::create)
				.consumeNextWith(response -> Assert.assertEquals("{AGE=40, NAME=Bob}", response.toString()))
				.verifyComplete();

	}

	@Test
	public void validateMessageHandlingWithDefaultDeleteCollection() {
		Message<Person> message = MessageBuilder.withPayload(createPerson("Bob", 35)).build();
		waitFor(r2dbcMessageHandler.handleMessage(message));

		Person person = this.client
				.select()
				.from("person")
				.as(Person.class)
				.fetch()
				.first()
				.block();

		r2dbcMessageHandler.setQueryType(R2dbcMessageHandler.Type.DELETE);
		message = MessageBuilder.withPayload(person).build();
		waitFor(r2dbcMessageHandler.handleMessage(message));

		personRepository.findAll()
				.as(StepVerifier::create)
				.expectNextCount(0)
				.verifyComplete();
	}

	@Test
	public void validateMessageHandlingWithDeleteQueryCollection() {
		r2dbcMessageHandler.setValuesExpression(new FunctionExpression<Message<?>>(Message::getPayload));
		r2dbcMessageHandler.setQueryType(R2dbcMessageHandler.Type.INSERT);
		r2dbcMessageHandler.setTableName("person");
		Map<String, Object> payload = new HashMap<>();
		payload.put("name", "Bob");
		payload.put("age", 35);
		Message<?> message = MessageBuilder.withPayload(payload).build();
		waitFor(r2dbcMessageHandler.handleMessage(message));

		payload = new HashMap<>();
		r2dbcMessageHandler.setQueryType(R2dbcMessageHandler.Type.DELETE);

		Object insertedId = client.execute("SELECT id FROM person")
				.fetch()
				.first()
				.block()
				.get("id");

		r2dbcMessageHandler.setCriteriaExpression(new FunctionExpression<Message<?>>((m) -> Criteria.where("id").is(insertedId)));
		message = MessageBuilder.withPayload(payload).build();
		waitFor(r2dbcMessageHandler.handleMessage(message));

		Flux<?> all = client.execute("SELECT age,name FROM person where age=35")
				.fetch().all();
		all.as(StepVerifier::create)
				.expectNextCount(0)
				.verifyComplete();

	}

	@Test
	public void validateMessageHandlingWithDeleteQueryCollection_MultipleRows() {
		r2dbcMessageHandler.setValuesExpression(new FunctionExpression<Message<?>>(Message::getPayload));
		r2dbcMessageHandler.setQueryType(R2dbcMessageHandler.Type.INSERT);
		r2dbcMessageHandler.setTableName("person");
		Map<String, Object> payload = new HashMap<>();
		payload.put("name", "Bob");
		payload.put("age", 35);
		Message<?> message = MessageBuilder.withPayload(payload).build();
		waitFor(r2dbcMessageHandler.handleMessage(message));

		payload = new HashMap<>();
		payload.put("name", "Rob");
		payload.put("age", 40);
		message = MessageBuilder.withPayload(payload).build();
		waitFor(r2dbcMessageHandler.handleMessage(message));

		payload = new HashMap<>();
		r2dbcMessageHandler.setQueryType(R2dbcMessageHandler.Type.DELETE);

		Object insertedId = client.execute("SELECT id FROM person")
				.fetch()
				.first()
				.block()
				.get("id");

		r2dbcMessageHandler.setCriteriaExpression(new FunctionExpression<Message<?>>((m) -> Criteria.where("id").is(insertedId)));
		message = MessageBuilder.withPayload(payload).build();
		waitFor(r2dbcMessageHandler.handleMessage(message));

		Flux<?> all = client.execute("SELECT age,name FROM person where age=40")
				.fetch().all();
		all.as(StepVerifier::create)
				.expectNextCount(1)
				.verifyComplete();

	}


	private Person createPerson(String bob, Integer age) {
		return new Person(bob, age);
	}

	private static <T> T waitFor(Mono<T> mono) {
		return mono.block(Duration.ofSeconds(10));
	}


	@Configuration
	@Import(R2dbcDatabaseConfiguration.class)
	static class R2dbcMessageHandlerConfiguration {

		@Autowired
		DatabaseClient databaseClient;

		@Bean
		public R2dbcMessageHandler r2dbcMessageHandler() {
			R2dbcMessageHandler r2dbcMessageHandler = new R2dbcMessageHandler(new R2dbcEntityTemplate(databaseClient));
			return r2dbcMessageHandler;
		}
	}

}


