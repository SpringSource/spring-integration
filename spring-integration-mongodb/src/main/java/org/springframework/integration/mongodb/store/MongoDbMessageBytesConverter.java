/*
 * Copyright 2013 the original author or authors.
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

package org.springframework.integration.mongodb.store;

import java.util.HashSet;
import java.util.Set;

import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.GenericConverter;
import org.springframework.core.serializer.support.DeserializingConverter;
import org.springframework.core.serializer.support.SerializingConverter;
import org.springframework.integration.Message;

/**
 * @author Artem Bilan
 * @since 3.0
 */
public class MongoDbMessageBytesConverter implements GenericConverter {

	private final Converter<Object, byte[]> serializingConverter = new SerializingConverter();

	private final Converter<byte[], Object> deserializingConverter = new DeserializingConverter();

	@Override
	public Set<ConvertiblePair> getConvertibleTypes() {
		Set<ConvertiblePair> convertiblePairs = new HashSet<ConvertiblePair>();
		convertiblePairs.add(new ConvertiblePair(Message.class, byte[].class));
		convertiblePairs.add(new ConvertiblePair(byte[].class, Message.class));
		return convertiblePairs;
	}

	@Override
	public Object convert(Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
		if (Message.class.isAssignableFrom(sourceType.getObjectType())) {
			return serializingConverter.convert(source);
		}
		else {
			return deserializingConverter.convert((byte[]) source);
		}
	}

}
