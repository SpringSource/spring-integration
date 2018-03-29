/*
 * Copyright 2002-2018 the original author or authors.
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

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;

/**
 * Simple {@link FileListFilter} that predicates its matches against <b>all</b> of the
 * configured {@link FileListFilter}.
 * @param <F> The type that will be filtered.
 *
 * @author Iwein Fuld
 * @author Josh Long
 * @author Gary Russell
 * @author Artem Bilan
 *
 *
 */
public class CompositeFileListFilter<F>
		implements ReversibleFileListFilter<F>, ResettableFileListFilter<F>, DiscardAwareFileListFilter<F>, Closeable {

	protected final Set<FileListFilter<F>> fileFilters; // NOSONAR

	private DiscardCallback<F> discardCallback;


	public CompositeFileListFilter() {
		this.fileFilters = new LinkedHashSet<>();
	}

	public CompositeFileListFilter(Collection<? extends FileListFilter<F>> fileFilters) {
		this.fileFilters = new LinkedHashSet<>(fileFilters);
	}


	@Override
	public void close() throws IOException {
		for (FileListFilter<F> filter : this.fileFilters) {
			if (filter instanceof Closeable) {
				((Closeable) filter).close();
			}
		}
	}

	public CompositeFileListFilter<F> addFilter(FileListFilter<F> filter) {
		return addFilters(Collections.singletonList(filter));
	}

	/**
	 * @param filters one or more new filters to add
	 * @return this CompositeFileFilter instance with the added filters
	 * @see #addFilters(Collection)
	 */
	@SuppressWarnings("unchecked")
	public CompositeFileListFilter<F> addFilters(FileListFilter<F>... filters) {
		return addFilters(Arrays.asList(filters));
	}

	/**
	 * Not thread safe. Only a single thread may add filters at a time.
	 * <p>
	 * Add the new filters to this CompositeFileListFilter while maintaining the existing filters.
	 *
	 * @param filtersToAdd a list of filters to add
	 * @return this CompositeFileListFilter instance with the added filters
	 */
	public CompositeFileListFilter<F> addFilters(Collection<? extends FileListFilter<F>> filtersToAdd) {
		for (FileListFilter<F> elf : filtersToAdd) {
			if (elf instanceof DiscardAwareFileListFilter) {
				((DiscardAwareFileListFilter<F>) elf).addDiscardCallback(this.discardCallback);
			}
			if (elf instanceof InitializingBean) {
				try {
					((InitializingBean) elf).afterPropertiesSet();
				}
				catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		}
		this.fileFilters.addAll(filtersToAdd);
		return this;
	}

	@Override
	public void addDiscardCallback(DiscardCallback<F> discardCallback) {
		this.discardCallback = discardCallback;
		if (this.discardCallback != null) {
			this.fileFilters
					.stream()
					.filter(DiscardAwareFileListFilter.class::isInstance)
					.map(f -> (DiscardAwareFileListFilter<F>) f)
					.forEach(f -> f.addDiscardCallback(discardCallback));
		}
	}

	@Override
	public List<F> filterFiles(F[] files) {
		Assert.notNull(files, "'files' should not be null");
		List<F> results = new ArrayList<F>(Arrays.asList(files));
		for (FileListFilter<F> fileFilter : this.fileFilters) {
			List<F> currentResults = fileFilter.filterFiles(files);
			results.retainAll(currentResults);
		}
		return results;
	}

	@Override
	public void rollback(F file, List<F> files) {
		for (FileListFilter<F> fileFilter : this.fileFilters) {
			if (fileFilter instanceof ReversibleFileListFilter) {
				((ReversibleFileListFilter<F>) fileFilter).rollback(file, files);
			}
		}
	}

	@Override
	public boolean remove(F f) {
		boolean removed = false;
		for (FileListFilter<F> fileFilter : this.fileFilters) {
			if (fileFilter instanceof ResettableFileListFilter) {
				((ResettableFileListFilter<F>) fileFilter).remove(f);
				removed = true;
			}
		}
		return removed;
	}

}
