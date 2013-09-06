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

package org.springframework.integration.config.xml;

import java.util.List;

import org.w3c.dom.Element;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.beans.factory.support.ManagedList;
import org.springframework.beans.factory.xml.BeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;

/**
 * Parser for the &lt;spel-property-accessors&gt; element.
 *
 * @author Artem Bilan
 * @since 3.0
 */
public class SpelPropertyAccessorsParser implements BeanDefinitionParser {

	private final List<Object> propertyAccessors = new ManagedList<Object>();

	private volatile boolean initialized;

	@Override
	public BeanDefinition parse(Element element, ParserContext parserContext) {
		this.initializeSpelPropertyAccessorRegistrarIfNecessary(parserContext);

		this.propertyAccessors.addAll(parserContext.getDelegate().parseListElement(element, null));

		return null;
	}

	private synchronized void initializeSpelPropertyAccessorRegistrarIfNecessary(ParserContext parserContext) {
		if (!this.initialized) {
			BeanDefinitionBuilder registrarBuilder = BeanDefinitionBuilder
					.genericBeanDefinition(IntegrationNamespaceUtils.BASE_PACKAGE + ".config.SpelPropertyAccessorRegistrar")
					.setRole(BeanDefinition.ROLE_INFRASTRUCTURE)
					.addConstructorArgValue(this.propertyAccessors);
			BeanDefinitionReaderUtils.registerWithGeneratedName(registrarBuilder.getBeanDefinition(),
					parserContext.getRegistry());
			this.initialized = true;
		}
	}

}
