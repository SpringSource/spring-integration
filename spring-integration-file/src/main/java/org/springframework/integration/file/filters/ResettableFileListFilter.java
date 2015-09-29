/*
 * Copyright 2015 the original author or authors.
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

/**
 * A {@link FileListFilter} that can be reset by removing a specific file from its
 * state.
 * @author Gary Russell
 * @since 4.1.7
 *
 */
public interface ResettableFileListFilter<F> extends FileListFilter<F> {

	/**
	 * Remove the specified element from the filter so it will pass on the next attempt.
	 * @param f the element to remove.
	 */
	void remove(F f);

}
