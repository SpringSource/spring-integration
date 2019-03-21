/*
 * Copyright 2002-2019 the original author or authors.
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

package org.springframework.integration.xml.transformer;

import static org.custommonkey.xmlunit.XMLAssert.assertXMLEqual;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import javax.xml.transform.Result;
import javax.xml.transform.Templates;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;
import org.w3c.dom.Document;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.integration.xml.result.StringResultFactory;
import org.springframework.integration.xml.util.XmlTestUtil;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.util.FileCopyUtils;
import org.springframework.xml.transform.StringResult;
import org.springframework.xml.transform.StringSource;


/**
 * @author Jonas Partner
 * @author Oleg Zhurakousky
 * @author Gunnar Hillert
 * @author Mike Bazos
 */
public class XsltPayloadTransformerTests {

	private XsltPayloadTransformer transformer;

	private final String docAsString = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><order><orderItem>test" +
			"</orderItem></order>";

	private final String outputAsString = "<bob>test</bob>";

	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	@Before
	public void setUp() throws Exception {
		this.transformer = new XsltPayloadTransformer(getXslTemplates());
		this.transformer.setBeanFactory(Mockito.mock(BeanFactory.class));
		this.transformer.setAlwaysUseResultFactory(false);
		this.transformer.afterPropertiesSet();
	}

	@Test
	public void testDocumentAsPayload() throws Exception {
		Object transformed =
				transformer.doTransform(new GenericMessage<>(XmlTestUtil.getDocumentForString(docAsString)));
		assertTrue("Wrong return type for document payload", Document.class
				.isAssignableFrom(transformed.getClass()));
		Document transformedDocument = (Document) transformed;
		assertXMLEqual(outputAsString, XmlTestUtil
				.docToString(transformedDocument));
	}

	@Test
	public void testSourceAsPayload() throws Exception {
		Object transformed = transformer
				.doTransform(new GenericMessage<>(new StringSource(docAsString)));
		assertEquals("Wrong return type for source payload", DOMResult.class,
				transformed.getClass());
		DOMResult result = (DOMResult) transformed;
		assertXMLEqual("Document incorrect after transformation", XmlTestUtil
				.getDocumentForString(outputAsString), (Document) result
				.getNode());
	}

	@Test
	public void testStringAsPayload() throws Exception {
		Object transformed = transformer.doTransform(new GenericMessage<>(docAsString));
		assertEquals("Wrong return type for string payload", String.class,
				transformed.getClass());
		String transformedString = (String) transformed;
		assertXMLEqual("String incorrect after transform", outputAsString,
				transformedString);
	}

	@Test
	public void testStringAsPayloadUseResultFactoryTrue() throws Exception {
		transformer.setAlwaysUseResultFactory(true);
		Object transformed = transformer.doTransform(new GenericMessage<>(docAsString));
		assertEquals("Wrong return type for useFactories true",
				DOMResult.class, transformed.getClass());
		DOMResult result = (DOMResult) transformed;
		assertXMLEqual("Document incorrect after transformation", XmlTestUtil
				.getDocumentForString(outputAsString), (Document) result
				.getNode());
	}

	@Test
	public void testSourceWithResultTransformer() throws Exception {
		Integer returnValue = 13;
		XsltPayloadTransformer transformer =
				new XsltPayloadTransformer(getXslTemplates(), new StubResultTransformer(returnValue));
		transformer.setBeanFactory(Mockito.mock(BeanFactory.class));
		transformer.afterPropertiesSet();
		Object transformed = transformer
				.doTransform(new GenericMessage<>(new StringSource(docAsString)));
		assertEquals("Wrong value from result conversion", returnValue,
				transformed);
	}

	@Test
	public void testXsltPayloadWithTransformerFactoryClassName() throws Exception {
		Integer returnValue = 13;
		XsltPayloadTransformer transformer =
				new XsltPayloadTransformer(getXslResourceThatOutputsText(), new StubResultTransformer(returnValue),
						"com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl");
		transformer.setBeanFactory(Mockito.mock(BeanFactory.class));
		transformer.afterPropertiesSet();
		Object transformed = transformer
				.doTransform(new GenericMessage<>(new StringSource(docAsString)));
		assertEquals("Wrong value from result conversion", returnValue,
				transformed);
	}

	@Test(expected = IllegalStateException.class)
	public void testXsltPayloadWithBadTransformerFactoryClassname() throws Exception {
		transformer = new XsltPayloadTransformer(getXslResourceThatOutputsText(), "foo.bar.Baz");
		transformer.setBeanFactory(Mockito.mock(BeanFactory.class));
		transformer.afterPropertiesSet();
	}

	@Test(expected = TransformerException.class)
	public void testNonXmlString() throws Exception {
		transformer.doTransform(new GenericMessage<>("test"));
	}

	@Test(expected = MessagingException.class)
	public void testUnsupportedPayloadType() throws Exception {
		transformer.doTransform(new GenericMessage<>(12L));
	}

	@Test
	public void testXsltWithImports() throws Exception {
		Resource resource = new ClassPathResource("transform-with-import.xsl",
				this.getClass());
		transformer = new XsltPayloadTransformer(resource);
		transformer.setBeanFactory(Mockito.mock(BeanFactory.class));
		transformer.afterPropertiesSet();
		assertEquals(transformer.doTransform(new GenericMessage<>(docAsString)),
				outputAsString);
	}


	@Test
	public void documentInStringResultOut() throws Exception {
		Resource resource = new ClassPathResource("transform-with-import.xsl",
				this.getClass());
		transformer = new XsltPayloadTransformer(resource);
		transformer.setResultFactory(new StringResultFactory());
		transformer.setAlwaysUseResultFactory(true);
		transformer.setBeanFactory(Mockito.mock(BeanFactory.class));
		transformer.afterPropertiesSet();
		Object returned = transformer.doTransform(new GenericMessage<>(XmlTestUtil.getDocumentForString(docAsString)));
		assertEquals("Wrong type of return ", StringResult.class, returned.getClass());
	}


	@Test
	public void stringInDomResultOut() throws Exception {
		Resource resource = new ClassPathResource("transform-with-import.xsl",
				this.getClass());
		transformer = new XsltPayloadTransformer(resource);
		transformer.setResultFactory(new StringResultFactory());
		transformer.setAlwaysUseResultFactory(true);
		transformer.setBeanFactory(Mockito.mock(BeanFactory.class));
		transformer.afterPropertiesSet();
		Object returned = transformer.doTransform(new GenericMessage<>(XmlTestUtil.getDocumentForString(docAsString)));
		assertEquals("Wrong type of return ", StringResult.class, returned.getClass());
	}

	@Test
	public void docInStringOut() throws Exception {
		transformer = new XsltPayloadTransformer(getXslResourceThatOutputsText());
		transformer.setResultFactory(new StringResultFactory());
		transformer.setAlwaysUseResultFactory(true);
		transformer.setBeanFactory(Mockito.mock(BeanFactory.class));
		transformer.afterPropertiesSet();
		Object returned = transformer.doTransform(new GenericMessage<>(XmlTestUtil.getDocumentForString(docAsString)));
		assertEquals("Wrong type of return ", StringResult.class, returned.getClass());
		assertEquals("Wrong content in string", "hello world", returned.toString());
	}

	private Templates getXslTemplates() throws Exception {
		TransformerFactory transformerFactory = TransformerFactory.newInstance();

		String xsl = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>" +
				"<xsl:stylesheet version=\"1.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">" +
				"   <xsl:template match=\"order\">" +
				"     <bob>test</bob>" +
				"   </xsl:template>" +
				"</xsl:stylesheet>";

		return transformerFactory.newTemplates(new StringSource(xsl));
	}

	private Resource getXslResourceThatOutputsText() throws IOException {
		String xsl = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>" +
				"<xsl:stylesheet version=\"1.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">" +
				"   <xsl:output method=\"text\" encoding=\"UTF-8\" />" +
				"   <xsl:template match=\"order\">hello world</xsl:template>" +
				"</xsl:stylesheet>";

		File xsltFile = this.temporaryFolder.newFile();
		FileCopyUtils.copy(xsl.getBytes(), xsltFile);
		return new FileSystemResource(xsltFile);
	}

	public static class StubResultTransformer implements ResultTransformer {

		private final Object objectToReturn;

		public StubResultTransformer(Object objectToReturn) {
			this.objectToReturn = objectToReturn;
		}

		@Override
		public Object transformResult(Result result) {
			return objectToReturn;
		}

	}

}
