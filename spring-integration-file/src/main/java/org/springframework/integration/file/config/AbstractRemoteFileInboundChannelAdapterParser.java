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

package org.springframework.integration.file.config;

import org.w3c.dom.Element;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeanMetadataElement;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.integration.config.ExpressionFactoryBean;
import org.springframework.integration.config.xml.AbstractPollingInboundChannelAdapterParser;
import org.springframework.integration.config.xml.IntegrationNamespaceUtils;
import org.springframework.integration.file.remote.session.CachingSessionFactory;
import org.springframework.util.StringUtils;

/**
 * Abstract base class for parsing remote file inbound channel adapters.
 * 
 * @author Oleg Zhurakousky
 * @author Mark Fisher
 * @since 2.0
 */
public abstract class AbstractRemoteFileInboundChannelAdapterParser extends AbstractPollingInboundChannelAdapterParser {
	private final Log logger = LogFactory.getLog(this.getClass());
	
	@Override
	protected final BeanMetadataElement parseSource(Element element, ParserContext parserContext) {
		BeanDefinitionBuilder synchronizerBuilder = BeanDefinitionBuilder.genericBeanDefinition(
				this.getInboundFileSynchronizerClassname());

		// This whole block must be refactored once cache-session attribute is removed
		String sessionFactoryName = element.getAttribute("session-factory");
		BeanDefinition sessionFactoryDefinition = parserContext.getReaderContext().getRegistry().getBeanDefinition(sessionFactoryName);
		String sessionFactoryClassName = sessionFactoryDefinition.getBeanClassName();
		if (StringUtils.hasText(sessionFactoryClassName) && sessionFactoryClassName.endsWith(CachingSessionFactory.class.getName())){
			synchronizerBuilder.addConstructorArgValue(sessionFactoryDefinition);
		}
		else {
			String cacheSessions = element.getAttribute("cache-sessions");
			if (StringUtils.hasText(cacheSessions)){
				logger.warn("The 'cache-sessions' attribute is deprecated since v2.1. Consider configuring CachingSessionFactory explicitly");	
			}
			if ("false".equalsIgnoreCase(cacheSessions)) {
				synchronizerBuilder.addConstructorArgReference(element.getAttribute("session-factory"));
			}
			else {		
				BeanDefinitionBuilder sessionFactoryBuilder = BeanDefinitionBuilder.genericBeanDefinition(CachingSessionFactory.class);		
				sessionFactoryBuilder.addConstructorArgReference(sessionFactoryName);
				synchronizerBuilder.addConstructorArgValue(sessionFactoryBuilder.getBeanDefinition());
			}
		}
		// end of what needs to be refactored once cache-session is removed

		// configure the InboundFileSynchronizer properties
		IntegrationNamespaceUtils.setValueIfAttributeDefined(synchronizerBuilder, element, "remote-directory");
		IntegrationNamespaceUtils.setValueIfAttributeDefined(synchronizerBuilder, element, "delete-remote-files");
		String localFileGeneratorExpression = element.getAttribute("local-filename-generator-expression");
		
		if (StringUtils.hasText(localFileGeneratorExpression)){
			BeanDefinitionBuilder localFileGeneratorExpressionBuilder = BeanDefinitionBuilder.genericBeanDefinition(ExpressionFactoryBean.class);
			localFileGeneratorExpressionBuilder.addConstructorArgValue(localFileGeneratorExpression);
			synchronizerBuilder.addPropertyValue("localFilenameGeneratorExpression", localFileGeneratorExpressionBuilder.getBeanDefinition());
		}
		
		String remoteFileSeparator = element.getAttribute("remote-file-separator");
		synchronizerBuilder.addPropertyValue("remoteFileSeparator", remoteFileSeparator);
		IntegrationNamespaceUtils.setValueIfAttributeDefined(synchronizerBuilder, element, "temporary-file-suffix");
		this.configureFilter(synchronizerBuilder, element, parserContext);

		// build the MessageSource
		BeanDefinitionBuilder messageSourceBuilder = BeanDefinitionBuilder.genericBeanDefinition(this.getMessageSourceClassname());
		messageSourceBuilder.addConstructorArgValue(synchronizerBuilder.getBeanDefinition());
		String comparator = element.getAttribute("comparator");
		if (StringUtils.hasText(comparator)){
			messageSourceBuilder.addConstructorArgReference(comparator);
		}
		IntegrationNamespaceUtils.setValueIfAttributeDefined(messageSourceBuilder, element, "local-directory");
		IntegrationNamespaceUtils.setValueIfAttributeDefined(messageSourceBuilder, element, "auto-create-local-directory");
		return messageSourceBuilder.getBeanDefinition();
	}

	private void configureFilter(BeanDefinitionBuilder synchronizerBuilder, Element element, ParserContext parserContext) {
		String filter = element.getAttribute("filter");
		String fileNamePattern = element.getAttribute("filename-pattern");
		String fileNameRegex = element.getAttribute("filename-regex");
		boolean hasFilter = StringUtils.hasText(filter);
		boolean hasFileNamePattern = StringUtils.hasText(fileNamePattern);
		boolean hasFileNameRegex = StringUtils.hasText(fileNameRegex);
		if (hasFilter || hasFileNamePattern || hasFileNameRegex) {
			int count = 0;
			if (hasFilter) {
				count++;
			}
			if (hasFileNamePattern) {
				count++;
			}
			if (hasFileNameRegex) {
				count++;
			}
			if (count != 1) {
				parserContext.getReaderContext().error("at most one of 'filename-pattern', " +
						"'filename-regex', or 'filter' is allowed on remote file inbound adapter", element);
			}
			if (hasFilter) {
				synchronizerBuilder.addPropertyReference("filter", filter);
			}
			else if (hasFileNamePattern) {
				BeanDefinitionBuilder filterBuilder = BeanDefinitionBuilder.genericBeanDefinition(
						this.getSimplePatternFileListFilterClassname());
				filterBuilder.addConstructorArgValue(fileNamePattern);
				synchronizerBuilder.addPropertyValue("filter", filterBuilder.getBeanDefinition());
			}
			else if (hasFileNameRegex) {
				BeanDefinitionBuilder filterBuilder = BeanDefinitionBuilder.genericBeanDefinition(
						this.getRegexPatternFileListFilterClassname());
				filterBuilder.addConstructorArgValue(fileNameRegex);
				synchronizerBuilder.addPropertyValue("filter", filterBuilder.getBeanDefinition());
			}
		}
	}

	protected abstract String getMessageSourceClassname();

	protected abstract String getInboundFileSynchronizerClassname();

	protected abstract String getSimplePatternFileListFilterClassname();

	protected abstract String getRegexPatternFileListFilterClassname();

}
