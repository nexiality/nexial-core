/*
 * Copyright 2012-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.nexial.commons.utils;

import java.io.File;
import java.io.IOException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import static javax.xml.xpath.XPathConstants.NODESET;

/**
 * $
 */
public final class XPathUtils {
    private static final DocumentBuilderFactory DOCUMENT_BUILDER_FACTORY = DocumentBuilderFactory.newInstance();
    private static final XPathFactory XPATH_FACTORY = XPathFactory.newInstance();

    private XPathUtils() { }

    public static NodeList parse(File xmlFile, String xpath) throws IOException, XPathExpressionException {
        if (xmlFile == null) { throw new IOException("xmlFile is null"); }
        if (!xmlFile.canRead()) { throw new IOException("file '" + xmlFile + "' cannot be read/accessed."); }

        if (StringUtils.isBlank(xpath)) { throw new IOException("valid xpath not specified."); }

        // never forget this!
        //DOCUMENT_BUILDER_FACTORY.setNamespaceAware(false);
        DOCUMENT_BUILDER_FACTORY.setValidating(false);
        //DOCUMENT_BUILDER_FACTORY.setExpandEntityReferences(false);
        //DOCUMENT_BUILDER_FACTORY.setCoalescing(false);
        //DOCUMENT_BUILDER_FACTORY.setXIncludeAware(false);

        DocumentBuilder builder;
        try {
            DOCUMENT_BUILDER_FACTORY.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd",
                                                false);
            builder = DOCUMENT_BUILDER_FACTORY.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new IOException("Unable to obtain new document builder: " + e.getMessage(), e);
        }

        Document doc;
        try {
            doc = builder.parse(xmlFile);
        } catch (SAXException e) {
            throw new IOException("Unable to parse file '" + xmlFile + "': " + e.getMessage(), e);
        }

        XPath xp = XPATH_FACTORY.newXPath();
        XPathExpression expr = xp.compile(xpath);
        return (NodeList) expr.evaluate(doc, NODESET);
    }
}
