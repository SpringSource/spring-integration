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
package org.springframework.integration.file.filters;

import org.springframework.integration.metadata.MetadataStore;
import org.springframework.util.Assert;

/**
 * Stores "seen" files in a MetadataStore to survive application restarts.
 * The default key is 'prefix' plus the absolute file name; value is the timestamp of the file.
 * Files are deemed as already 'seen' if they exist in the store and have the
 * same modified time as the current file.
 *
 * @author Gary Russell
 * @since 3.0
 *
 */
public abstract class AbstractPersistentAcceptOnceFileListFilter<F> extends AbstractFileListFilter<F> {

	protected final MetadataStore store;

	protected final String prefix;

	private final Object monitor = new Object();

	public AbstractPersistentAcceptOnceFileListFilter(MetadataStore store, String prefix) {
		Assert.notNull(store, "'store' cannot be null");
		Assert.notNull(prefix, "'prefix' cannot be null");
		this.store = store;
		this.prefix = prefix;
	}

	@Override
	protected boolean accept(F file) {
		String key = buildKey(file);
		synchronized(monitor) {
			String value = store.get(key);
			if (value != null && isEqual(file, value)) {
				return false;
			}
			store.put(key, value(file));
		}
		return true;
	}

	/**
	 * The default value stored for the key is the last modified date.
	 * @param file The file.
	 * @return The value to store for the file.
	 */
	private String value(F file) {
		return Long.toString(this.modified(file));
	}

	/**
	 * Override this method if you wish to use something other than the
	 * modified timestamp to determine equality.
	 * @param file The file.
	 * @param value The current value for the key in the store.
	 * @return true if equal.
	 */
	protected boolean isEqual(F file, String value) {
		return Long.valueOf(value).longValue() == this.modified(file);
	}

	/**
	 * The default key is the {@link #prefix} plus the full filename.
	 * @param file The file.
	 * @return The key.
	 */
	protected String buildKey(F file) {
		return this.prefix + this.fileName(file);
	}

	protected abstract long modified(F file);

	protected abstract String fileName(F file);

}
