/*
 * Copyright 2002-2013 the original author or authors.
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
package org.springframework.integration.jpa.config.xml;

import org.w3c.dom.Element;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.parsing.BeanComponentDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.integration.config.xml.IntegrationNamespaceUtils;
import org.springframework.integration.jpa.support.OutboundGatewayType;
import org.springframework.util.StringUtils;

/**
 * The Parser for the Retrieving Jpa Outbound Gateway.
 *
 * @author Amol Nayak
 * @author Gunnar Hillert
 * @since 2.2
 */
public class RetrievingJpaOutboundGatewayParser extends AbstractJpaOutboundGatewayParser {

	@Override
	protected BeanDefinitionBuilder parseHandler(Element gatewayElement, ParserContext parserContext) {

		final BeanDefinitionBuilder jpaOutboundGatewayBuilder = super.parseHandler(gatewayElement, parserContext);

		final BeanDefinitionBuilder jpaExecutorBuilder = JpaParserUtils.getOutboundGatewayJpaExecutorBuilder(gatewayElement, parserContext);

		BeanDefinition firstResultExpression = IntegrationNamespaceUtils
				.createExpressionDefinitionFromValueOrExpression("first-result", "first-result-expression",
						parserContext, gatewayElement, false);
		if (firstResultExpression != null) {
			jpaExecutorBuilder.addPropertyValue("firstResultExpression", firstResultExpression);
		}

		String maxNumberOfResults = gatewayElement.getAttribute("max-number-of-results");
		boolean hasMaxNumberOfResults = StringUtils.hasText(maxNumberOfResults);

		String maxResults = gatewayElement.getAttribute("max-results");
		boolean hasMaxResults = StringUtils.hasText(maxResults);

		if (hasMaxNumberOfResults) {
			parserContext.getReaderContext().warning("'max-number-of-results' is deprecated in favor of 'max-results'", gatewayElement);
			if (hasMaxResults) {
				parserContext.getReaderContext().error("'max-number-of-results' and 'max-results' are mutually exclusive", gatewayElement);
			}
			else {
				gatewayElement.setAttribute("max-results", maxNumberOfResults);
			}
		}

		BeanDefinition maxResultsExpression = IntegrationNamespaceUtils
				.createExpressionDefinitionFromValueOrExpression("max-results", "max-results-expression",
						parserContext, gatewayElement, false);
		if (maxResultsExpression != null) {
			jpaExecutorBuilder.addPropertyValue("maxResultsExpression", maxResultsExpression);
		}

		IntegrationNamespaceUtils.setValueIfAttributeDefined(jpaExecutorBuilder, gatewayElement, "delete-after-poll");
		IntegrationNamespaceUtils.setValueIfAttributeDefined(jpaExecutorBuilder, gatewayElement, "delete-in-batch");
		IntegrationNamespaceUtils.setValueIfAttributeDefined(jpaExecutorBuilder, gatewayElement, "expect-single-result");

		final BeanDefinition jpaExecutorBuilderBeanDefinition = jpaExecutorBuilder.getBeanDefinition();
		final String gatewayId = this.resolveId(gatewayElement, jpaOutboundGatewayBuilder.getRawBeanDefinition(), parserContext);
		final String jpaExecutorBeanName = gatewayId + ".jpaExecutor";

		parserContext.registerBeanComponent(new BeanComponentDefinition(jpaExecutorBuilderBeanDefinition, jpaExecutorBeanName));

		jpaOutboundGatewayBuilder.addConstructorArgReference(jpaExecutorBeanName);
		jpaOutboundGatewayBuilder.addPropertyValue("gatewayType", OutboundGatewayType.RETRIEVING);

		return jpaOutboundGatewayBuilder;
	}

}
