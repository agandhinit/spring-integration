/*
 * Copyright 2002-2008 the original author or authors.
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

package org.springframework.integration.config;

import org.w3c.dom.Element;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.beans.factory.xml.AbstractSingleBeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.integration.endpoint.config.ConsumerEndpointFactoryBean;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.util.xml.DomUtils;

/**
 * Base class parser for elements that create Message Endpoints.
 * 
 * @author Mark Fisher
 */
public abstract class AbstractEndpointParser extends AbstractSingleBeanDefinitionParser {

	protected static final String REF_ATTRIBUTE = "ref";

	protected static final String METHOD_ATTRIBUTE = "method";

	protected static final String OUTPUT_CHANNEL_ATTRIBUTE = "output-channel";

	private static final String POLLER_ELEMENT = "poller";

	private static final String SELECTOR_ATTRIBUTE = "selector";

	private static final String ERROR_HANDLER_ATTRIBUTE = "error-handler";


	@Override
	protected final Class<?> getBeanClass(Element element) {
		return ConsumerEndpointFactoryBean.class;
	}

	@Override
	protected boolean shouldGenerateId() {
		return false;
	}

	@Override
	protected boolean shouldGenerateIdAsFallback() {
		return true;
	}

	/**
	 * Parse the MessageConsumer.
	 */
	protected abstract BeanDefinitionBuilder parseConsumer(Element element, ParserContext parserContext);

	protected String getInputChannelAttributeName() {
		return "input-channel";
	}

	protected String parseAdapter(Element element, ParserContext parserContext, Class<?> adapterClass) {
		String ref = element.getAttribute(REF_ATTRIBUTE);
		Assert.hasText(ref, "The '" + REF_ATTRIBUTE + "' attribute is required.");
		if (StringUtils.hasText(element.getAttribute(METHOD_ATTRIBUTE))) {
			BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(adapterClass);
			String method = element.getAttribute(METHOD_ATTRIBUTE);
			builder.addConstructorArgReference(ref);
			builder.addConstructorArgValue(method);
			return BeanDefinitionReaderUtils.registerWithGeneratedName(
					builder.getBeanDefinition(), parserContext.getRegistry());
		}
		return ref;
	}

	@Override
	protected final void doParse(Element element, ParserContext parserContext, BeanDefinitionBuilder builder) {
		BeanDefinitionBuilder consumerBuilder = this.parseConsumer(element, parserContext);
		IntegrationNamespaceUtils.setReferenceIfAttributeDefined(consumerBuilder, element, OUTPUT_CHANNEL_ATTRIBUTE);
		IntegrationNamespaceUtils.setReferenceIfAttributeDefined(consumerBuilder, element, SELECTOR_ATTRIBUTE);
		IntegrationNamespaceUtils.setReferenceIfAttributeDefined(consumerBuilder, element, ERROR_HANDLER_ATTRIBUTE);
		String consumerBeanName = BeanDefinitionReaderUtils.registerWithGeneratedName(
				consumerBuilder.getBeanDefinition(), parserContext.getRegistry());
		builder.addConstructorArgReference(consumerBeanName);
		String inputChannelAttributeName = this.getInputChannelAttributeName();
		String inputChannelName = element.getAttribute(inputChannelAttributeName);
		Assert.hasText(inputChannelName, "the '" + inputChannelAttributeName + "' attribute is required");
		builder.addPropertyValue("inputChannelName", inputChannelName);
		Element pollerElement = DomUtils.getChildElementByTagName(element, POLLER_ELEMENT);
		if (pollerElement != null) {
			IntegrationNamespaceUtils.configureTrigger(pollerElement, builder);
			Element txElement = DomUtils.getChildElementByTagName(pollerElement, "transactional");
			if (txElement != null) {
				IntegrationNamespaceUtils.configureTransactionAttributes(txElement, builder);
			}
			IntegrationNamespaceUtils.setReferenceIfAttributeDefined(builder, pollerElement, "task-executor");
		}
		this.postProcess(element, parserContext, builder);
	}

	/**
	 * Subclasses may implement this method to provide additional configuration.
	 */
	protected void postProcess(Element element, ParserContext parserContext, BeanDefinitionBuilder builder) {
	}

}
