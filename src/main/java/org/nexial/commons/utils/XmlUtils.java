/*
 * Copyright 2012-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
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
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.jdom2.Attribute;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.filter.Filters;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.jdom2.xpath.XPathFactory;
import org.json.JSONException;
import org.json.XML;

/**
 * @author Mike Liu
 */
public final class XmlUtils {
    private static final SAXBuilder SAX_BUILDER = new SAXBuilder();
    private static final XMLOutputter PRETTY_XML_OUTPUTTER = new XMLOutputter(Format.getPrettyFormat());

    private XmlUtils() { }

    public static Document parse(File xmlFile) throws JDOMException, IOException {
        return parse(FileUtils.readFileToString(xmlFile, "UTF-8"));
    }

    public static Document parse(String xmlText) throws JDOMException, IOException {
        if (StringUtils.isEmpty(xmlText)) { return null; }
        return SAX_BUILDER.build(new StringReader(xmlText));
    }

    public static String getSoapRequestOpName(String soapRequest) throws JDOMException, IOException {
        Document doc = parse(soapRequest);
        if (doc == null) { return null; }

        Element soapOpNode = resolveSoapRequestOpNode(doc);
        if (soapOpNode == null) { return null; }

        return soapOpNode.getName();
    }

    /**
     * adding additional data to a request payload.  The target node is 2 levels below the "soap operation" node.  For
     * example: <pre > &lt;soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
     * xmlns:ter="http://www.adp.com/ter"&gt; &lt;soapenv:Header/&gt; &lt;soapenv:Body&gt; &lt;ter:setToSubmitForAudit&gt;
     * &lt;request&gt; &lt;params&gt; &lt;item key="key1"&gt; &lt;value&gt;value1&lt;/value&gt; &lt;/item&gt;
     * &lt;/params&gt; &lt;tracerNo&gt;1234567V1&lt;/tracerNo&gt; &lt;userId&gt;lium&lt;/userId&gt; <span
     * style="color:green">&lt;!-- inserted node below --&gt;</span> &lt;requestTimestamp&gt;123456&lt;/requestTimestamp&gt;
     * &lt;/request&gt; &lt;/ter:setToSubmitForAudit&gt; &lt;/soapenv:Body&gt; &lt;/soapenv:Envelope&gt; </pre>
     */
    public static String addToSoapRequestBody(String soapRequest, String nodeName, Object value, boolean overwrite)
        throws JDOMException, IOException {
        if (StringUtils.isBlank(soapRequest)) { return soapRequest; }
        if (StringUtils.isBlank(nodeName)) { return soapRequest; }

        String valueStr = value == null ? "" : value.toString();

        Document doc = parse(soapRequest);
        if (doc == null) { return null; }

        Element soapOpNode = resolveSoapRequestOpNode(doc);
        if (soapOpNode == null) { return null; }

        Element opRequestNode;
        List children = soapOpNode.getChildren();
        if (!CollectionUtils.isEmpty(children)) {
            opRequestNode = (Element) children.get(0);
        } else {
            opRequestNode = soapOpNode;
        }

        Element targetNode = opRequestNode.getChild(nodeName);
        if (targetNode == null) {
            // dont't have this node yet... add it
            opRequestNode.addContent(new Element(nodeName).setText(valueStr));
        } else {
            if (overwrite) { targetNode.setText(valueStr); }
        }

        StringWriter out = new StringWriter();
        PRETTY_XML_OUTPUTTER.output(doc, out);
        return out.toString();
    }

    public static String toPrettyXml(Element element) throws IOException {
        StringWriter out = new StringWriter();
        PRETTY_XML_OUTPUTTER.output(element, out);
        return out.toString();
    }

    public static Element findElement(Document doc, String xpath) {
        return XPathFactory.instance().compile(xpath, Filters.element()).evaluateFirst(doc);
    }

    public static List<Element> findElements(Document doc, String xpath) {
        return XPathFactory.instance().compile(xpath, Filters.element()).evaluate(doc);
    }

    public static Object findNode(Document doc, String xpath) {
        return XPathFactory.instance().compile(xpath).evaluateFirst(doc);
    }

    /** @return a list of the XPath results (XML nodes). */
    public static List findNodes(Document doc, String xpath) {
        return XPathFactory.instance().compile(xpath).evaluate(doc);
    }

    /**
     * this method parses through the soap response ({@code rawResponseText}) and extract the portion under the soap
     * response body.  The soap response body is considered as the XML content under 2 levels under the
     * {@code <soap:Body>} element.
     */
    public static String parseSoapResponseBody(String rawResponseText, boolean stripNamespaces)
        throws JDOMException, IOException {
        Document soapResponse = parse(rawResponseText);
        // find the element 2 level below soap:Body
        Element responseContent = findElement(soapResponse,
                                              "/soap:Envelope/soap:Body/*[position()=1]/*[position()=1]");
        String responseBody = toPrettyXml(responseContent);
        return stripNamespaces ? detachNamespaces(responseBody) : responseBody;
    }

    /**
     * detach namespace attributes from {@code xmlContent}.  Note that this method uses regex to strip off the
     * namespaces.
     */
    public static String detachNamespaces(String xmlContent) {
        xmlContent = RegexUtils.replace(xmlContent, "\\ [0-9A-Za-z]+\\:[0-9A-Za-z]+\\=\\\".*\\\"", "");
        xmlContent = RegexUtils.replace(xmlContent, "<([0-9A-Za-z]+\\:)([0-9A-Za-z]+)", "<$2");
        return xmlContent;
    }

    /**
     * Convert a well-formed (but not necessarily valid) XML string into a JSON string
     *
     * @param xmlPart <code>String</code> - XML
     * @return <code>String</code> - JSON string
     */
    public static String toJsonString(String xmlPart) throws JSONException {
        return XML.toJSONObject(xmlPart).toString();
    }

    public static String getAttributeValue(Document doc, String xpath, String attribName) throws JDOMException {
        Element element = findElement(doc, xpath);
        if (element == null) { return null; }

        Attribute attribute = element.getAttribute(attribName);
        if (attribute == null) { return null; }

        return attribute.getValue();
    }

    public static int count(Document doc, String xpath) throws JDOMException {
        List matches = findNodes(doc, xpath);
        return CollectionUtils.isNotEmpty(matches) ? matches.size() : 0;
    }

    private static Element resolveSoapRequestOpNode(Document doc) {
        if (doc == null) { return null; }

        Element root = doc.getRootElement();
        XPathFactory xPathFactory = XPathFactory.instance();
        Element body = xPathFactory.compile("soapenv:Body", Filters.element()).evaluateFirst(root);
        if (body == null) {
            body = xPathFactory.compile("soap:Body", Filters.element()).evaluateFirst(root);
            if (body == null) { return null; }
        }

        List children = body.getChildren();
        if (CollectionUtils.isEmpty(children)) { return null; }

        return (Element) children.get(0);
    }
}
