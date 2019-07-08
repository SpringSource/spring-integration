/*
 * Copyright 2019 the original author or authors.
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

package org.springframework.integration.transformer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import org.apache.avro.generic.GenericContainer;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.avro.specific.SpecificRecordBase;

import org.springframework.messaging.Message;
import org.springframework.util.Assert;

/**
 * An avro transformer for generated {@link GenericContainer} objects.
 *
 * @author Gary Russell
 * @since 5.2
 *
 */
public class SimpleToAvroTransformer extends AbstractTransformer {

	private final EncoderFactory encoderFactory = new EncoderFactory();

	@Override
	protected Object doTransform(Message<?> message) {
		Assert.state(message.getPayload() instanceof SpecificRecordBase,
				"Payload must be a subclass of 'SpecificRecordBase'");
		GenericContainer specific = (SpecificRecordBase) message.getPayload();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		BinaryEncoder encoder = this.encoderFactory.directBinaryEncoder(out, null);
		DatumWriter<Object> writer = new SpecificDatumWriter<>(specific.getSchema());
		try {
			writer.write(specific, encoder);
			encoder.flush();
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return out.toByteArray();
	}

}