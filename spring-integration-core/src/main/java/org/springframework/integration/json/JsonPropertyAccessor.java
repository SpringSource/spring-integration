/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.integration.json;

import java.io.IOException;
import java.util.AbstractList;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;

import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.ConverterRegistry;
import org.springframework.core.convert.converter.GenericConverter;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.expression.AccessException;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.PropertyAccessor;
import org.springframework.expression.TypedValue;
import org.springframework.util.Assert;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ContainerNode;

/**
 * A SpEL {@link PropertyAccessor} that knows how to read properties from JSON objects.
 * Uses Jackson {@link JsonNode} API for nested properties access.
 *
 * @author Eric Bottard
 * @author Artem Bilan
 * @author Paul Martin
 * @author Gary Russell
 *
 * @since 3.0
 */
public class JsonPropertyAccessor implements PropertyAccessor {

	/**
	 * The kind of types this can work with.
	 */
	private static final Class<?>[] SUPPORTED_CLASSES =
			new Class<?>[] {
				String.class,
				JsonNodeWrapper.class,
				JsonNode.class
		};

	// Note: ObjectMapper is thread-safe
	private ObjectMapper objectMapper = new ObjectMapper();

	public JsonPropertyAccessor() {
		this((ConverterRegistry) DefaultConversionService.getSharedInstance());
	}

	public JsonPropertyAccessor(ConverterRegistry converterRegistry) {
		converterRegistry.addConverter(new GenericConverter() {
			@Override
			public Set<ConvertiblePair> getConvertibleTypes() {
				return Collections.singleton(new ConvertiblePair(JsonNodeWrapper.class, JsonNode.class));
			}

			@Override
			public Object convert(Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
				return targetType.getObjectType().cast(((JsonNodeWrapper<?>) source).getRealNode());
			}
		});
	}

	public void setObjectMapper(ObjectMapper objectMapper) {
		Assert.notNull(objectMapper, "'objectMapper' cannot be null");
		this.objectMapper = objectMapper;
	}

	@Override
	public Class<?>[] getSpecificTargetClasses() {
		return SUPPORTED_CLASSES; // NOSONAR - expose internals
	}

	@Override
	public boolean canRead(EvaluationContext context, Object target, String name) throws AccessException {
		JsonNode node = asJson(target);
		Integer index = maybeIndex(name);
		if (node instanceof ArrayNode) {
			return index != null;
		}
		return true;
	}

	private JsonNode asJson(Object target) throws AccessException {
		if (target instanceof ContainerNode) {
			return (ContainerNode<?>) target;
		}
		else if (target instanceof JsonNodeWrapper && ((JsonNodeWrapper<?>) target).getRealNode() instanceof JsonNode) {
			return ((JsonNodeWrapper<?>) target).getRealNode();
		}
		else if (target instanceof String) {
			try {
				return this.objectMapper.readTree((String) target);
			}
			catch (JsonProcessingException e) {
				throw new AccessException("Exception while trying to deserialize String", e);
			}
		}
		else {
			throw new IllegalStateException("Can't happen. Check SUPPORTED_CLASSES");
		}
	}

	/**
	 * Return an integer if the String property name can be parsed as an int, or null otherwise.
	 */
	private Integer maybeIndex(String name) {
		try {
			return Integer.valueOf(name);
		}
		catch (NumberFormatException e) {
			return null;
		}
	}

	@Override
	public TypedValue read(EvaluationContext context, Object target, String name) throws AccessException {
		JsonNode node = asJson(target);
		Integer index = maybeIndex(name);
		if (index != null && node.has(index)) {
			return typedValue(node.get(index));
		}
		else {
			return typedValue(node.get(name));
		}
	}

	@Override
	public boolean canWrite(EvaluationContext context, Object target, String name) {
		return false;
	}

	@Override
	public void write(EvaluationContext context, Object target, String name, Object newValue) {
		throw new UnsupportedOperationException("Write is not supported");
	}

	private static TypedValue typedValue(JsonNode json) throws AccessException {
		if (json == null) {
			return TypedValue.NULL;
		}
		else if (json.isValueNode()) {
			return new TypedValue(getValue(json));
		}
		return new TypedValue(wrap(json));
	}

	private static Object getValue(JsonNode json) throws AccessException {
		if (json.isTextual()) {
			return json.textValue();
		}
		else if (json.isNumber()) {
			return json.numberValue();
		}
		else if (json.isBoolean()) {
			return json.asBoolean();
		}
		else if (json.isNull()) {
			return null;
		}
		else if (json.isBinary()) {
			try {
				return json.binaryValue();
			}
			catch (IOException e) {
				throw new AccessException(
						"Can not get content of binary value : " + json);
			}
		}
		throw new IllegalArgumentException("Json is not ValueNode.");
	}

	public static Object wrap(JsonNode json) throws AccessException {
		if (json == null) {
			return null;
		}
		else if (json instanceof ArrayNode) {
			return new ArrayNodeAsList((ArrayNode) json);
		}
		else if (json.isValueNode()) {
			return getValue(json);
		}
		else {
			return new ComparableJsonNode(json);
		}
	}

	interface JsonNodeWrapper<T> extends Comparable<T> {

		String toString();

		JsonNode getRealNode();

	}

	static class ComparableJsonNode implements JsonNodeWrapper<ComparableJsonNode> {

		private JsonNode delegate;

		ComparableJsonNode(JsonNode delegate) {
			this.delegate = delegate;
		}

		@Override
		public JsonNode getRealNode() {
			return this.delegate;
		}

		@Override
		public String toString() {
			return this.delegate.toString();
		}

		@Override
		public int compareTo(ComparableJsonNode o) {
			return this.delegate.equals(o.delegate) ? 0 : 1;
		}
	}

	/**
	 * An {@link AbstractList} implementation around {@link ArrayNode} with {@link WrappedJsonNode} aspect.
	 * @since 5.0
	 */
	static class ArrayNodeAsList extends AbstractList<Object> implements JsonNodeWrapper<Object> {

		private final ArrayNode delegate;

		ArrayNodeAsList(ArrayNode node) {
			this.delegate = node;
		}

		@Override
		public JsonNode getRealNode() {
			return this.delegate;
		}

		@Override
		public String toString() {
			return this.delegate.toString();
		}

		@Override
		public Object get(int index) {
			if (index < 0) {
				// negative index can be handled with that conversion
				index = this.delegate.size() + index;
			}
			try {
				return wrap(this.delegate.get(index));
			}
			catch (AccessException e) {
				throw new IllegalArgumentException(e);
			}
		}

		@Override
		public int size() {
			return this.delegate.size();
		}

		@Override
		public Iterator<Object> iterator() {

			return new Iterator<Object>() {

				private final Iterator<JsonNode> it = ArrayNodeAsList.this.delegate.iterator();

				@Override
				public boolean hasNext() {
					return this.it.hasNext();
				}

				@Override
				public Object next() {
					try {
						return wrap(this.it.next());
					}
					catch (AccessException e) {
						throw new IllegalArgumentException(e);
					}
				}

			};
		}

		@Override
		public int compareTo(Object o) {
			if (o instanceof JsonNodeWrapper<?>) {
				return this.delegate.equals(((JsonNodeWrapper<?>) o).getRealNode()) ? 0 : 1;
			}
			return this.delegate.equals(o) ? 0 : 1;
		}
	}
}
