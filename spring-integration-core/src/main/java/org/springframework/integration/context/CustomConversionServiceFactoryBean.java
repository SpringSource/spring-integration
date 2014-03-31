/*
 * Copyright 2014 the original author or authors.
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

package org.springframework.integration.context;

import org.springframework.context.support.ConversionServiceFactoryBean;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.GenericConversionService;

/**
 * This is a workaround until we depend on Spring 3.1 and specifically when SPR-8818 is resolved.
 * See INT-2259 and INT-1893 for more detail.
 *
 * @author Oleg Zhurakousky
 * @author Mark Fisher
 * @since 2.0
 */
class CustomConversionServiceFactoryBean extends ConversionServiceFactoryBean {

	@Override
	public ConversionService getObject() {
		ConversionService service = super.getObject();
		if (service instanceof GenericConversionService) {
			((GenericConversionService) service).removeConvertible(Object.class, Object.class);
		}
		return service;
	}
}
