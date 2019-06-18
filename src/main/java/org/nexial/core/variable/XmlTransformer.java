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

package org.nexial.core.variable;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jdom2.*;
import org.jdom2.output.XMLOutputter;
import org.nexial.commons.utils.TextUtils;
import org.nexial.commons.utils.XmlUtils;
import org.nexial.core.ExecutionThread;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.plugins.xml.Modification;
import org.nexial.core.utils.ConsoleUtils;

import static org.nexial.core.NexialConst.COMPRESSED_XML_OUTPUTTER;
import static org.nexial.core.NexialConst.Data.TEXT_DELIM;
import static org.nexial.core.NexialConst.PRETTY_XML_OUTPUTTER;
import static org.nexial.core.SystemVariables.getDefault;

public class XmlTransformer<T extends XmlDataType> extends Transformer {
    private static final Map<String, Integer> FUNCTION_TO_PARAM_LIST = discoverFunctions(XmlTransformer.class);
    private static final Map<String, Method> FUNCTIONS =
        toFunctionMap(FUNCTION_TO_PARAM_LIST, XmlTransformer.class, XmlDataType.class);
    private static final XMLOutputter OUTPUTTER = new XMLOutputter();
    private static final String NODE_EXTRACT = "extracted";

    public TextDataType text(T data) { return super.text(data); }

    public ExpressionDataType extract(T data, String xpath) {
        if (data == null || data.getDocument() == null || StringUtils.isBlank(xpath)) { return data; }

        try {
            List matches = XmlUtils.findNodes(data.getDocument(), xpath);
            if (CollectionUtils.isEmpty(matches)) { return null; }

            Object firstMatch = matches.get(0);
            if (matches.size() == 1) {
                if (firstMatch instanceof Element) {
                    data.setValue((Element) firstMatch);
                    data.setTextValue(toTextValue(data.getValue()));
                    data.parse();
                    return data;
                }

                TextDataType text = new TextDataType("");
                if (firstMatch instanceof Content) {
                    text.setValue(((Content) firstMatch).getValue());
                } else if (firstMatch instanceof Attribute) {
                    text.setValue(((Attribute) firstMatch).getValue());
                } else {
                    text.setValue(StringUtils.trim(firstMatch.toString()));
                }
                return text;
            }

            if (firstMatch instanceof Element) {
                Element root = new Element(NODE_EXTRACT);
                matches.forEach(instance -> root.addContent(((Element) instance).detach()));

                // data.setValue(root);
                data.setTextValue(toTextValue(root));
                data.parse();
                return data;
            }

            List<String> list = new ArrayList<>();
            if (firstMatch instanceof Content) {
                matches.forEach(instance -> list.add(((Content) instance).getValue()));
            } else if (firstMatch instanceof Attribute) {
                matches.forEach(instance -> list.add(((Attribute) instance).getValue()));
            } else {
                matches.forEach(instance -> list.add(instance.toString()));
            }

            ExecutionContext context = ExecutionThread.get();
            String delim = context != null ? context.getTextDelim() : getDefault(TEXT_DELIM);
            return new ListDataType(TextUtils.toString(list, delim), delim);
        } catch (TypeConversionException e) {
            throw new IllegalArgumentException("Unable to process XML: " + e.getMessage(), e);
        }
    }

    public T updateAttribute(T data, String xpath, String name, String value) throws TypeConversionException {
        if (data == null || data.getDocument() == null || StringUtils.isBlank(xpath) || StringUtils.isBlank(name)) {
            return data;
        }

        List matches = XmlUtils.findNodes(data.getDocument(), xpath);
        if (CollectionUtils.isEmpty(matches)) { return data; }

        matches.forEach(match -> {
            if (match instanceof Element) {
                Attribute attr = ((Element) match).getAttribute(name);
                if (attr != null) {
                    if (StringUtils.isEmpty(value)) {
                        ((Element) match).removeAttribute(name);
                    } else {
                        attr.setValue(value);
                    }
                }
            }
        });

        try {
            data.reset(data.getValue());
        } catch (IOException e) {
            throw new TypeConversionException(data.getName(), data.getValue().toString(), e.getMessage(), e);
        }

        return data;
    }

    public ExpressionDataType attribute(T data, String xpath, String name) throws TypeConversionException {
        if (data == null || data.getDocument() == null || StringUtils.isBlank(xpath) || StringUtils.isBlank(name)) {
            return null;
        }

        List matches = XmlUtils.findNodes(data.getDocument(), xpath);
        if (CollectionUtils.isEmpty(matches)) { return null; }

        if (matches.size() == 1) {
            Object match = matches.get(0);
            if (!(match instanceof Element)) { return null; }

            Attribute attr = ((Element) match).getAttribute(name);
            return attr == null ? null : new TextDataType(attr.getValue());
        }

        List<String> values = new ArrayList<>();
        matches.forEach(match -> {
            if (match instanceof Element) {
                Attribute attr = ((Element) match).getAttribute(name);
                if (attr != null) { values.add(attr.getValue()); }
            }
        });

        ExecutionContext context = ExecutionThread.get();
        String delim = context != null ? context.getTextDelim() : getDefault(TEXT_DELIM);
        return new ListDataType(TextUtils.toString(values, delim), delim);
    }

    public ExpressionDataType content(T data, String xpath) throws TypeConversionException {
        if (data == null || data.getDocument() == null || StringUtils.isBlank(xpath)) { return null; }

        List matches = XmlUtils.findNodes(data.getDocument(), xpath);
        if (CollectionUtils.isEmpty(matches)) { return null; }

        if (matches.size() == 1) {
            Object match = matches.get(0);
            String text = resolveContent(match);
            if (text != null) { return new TextDataType(text); }
            throw new TypeConversionException(data.getName(), xpath,
                                              "XPath '" + xpath + "' does not resolve to an XML node with content");
        }

        List<String> values = new ArrayList<>();
        matches.forEach(match -> {
            String text = resolveContent(match);
            if (text != null) { values.add(text); }
        });

        ExecutionContext context = ExecutionThread.get();
        String delim = context != null ? context.getTextDelim() : getDefault(TEXT_DELIM);
        return new ListDataType(TextUtils.toString(values, delim));
    }

    public T updateContent(T data, String xpath, String content) throws TypeConversionException {
        if (data == null || data.getDocument() == null || StringUtils.isBlank(xpath)) { return data; }

        List matches = XmlUtils.findNodes(data.getDocument(), xpath);
        if (CollectionUtils.isEmpty(matches)) { return data; }

        matches.forEach(match -> match = updateContent(match, content));

        try {
            data.reset(data.getValue());
        } catch (IOException e) {
            throw new TypeConversionException(data.getName(), data.getValue().toString(), e.getMessage(), e);
        }

        return data;
    }

    public T remove(T data, String xpath) throws TypeConversionException {
        if (data == null || data.getDocument() == null || StringUtils.isBlank(xpath)) { return data; }

        List matches = XmlUtils.findNodes(data.getDocument(), xpath);
        if (CollectionUtils.isEmpty(matches)) { return data; }

        matches.forEach(match -> { if (match instanceof Content) { ((Content) match).detach(); }});

        try {
            data.reset(data.getValue());
        } catch (IOException e) {
            throw new TypeConversionException(data.getName(), data.getValue().toString(), e.getMessage(), e);
        }

        return data;
    }

    public NumberDataType count(T data, String xpath) throws ExpressionException {
        if (data == null || data.getDocument() == null || StringUtils.isEmpty(xpath)) { return null; }
        try {
            return new NumberDataType(String.valueOf(XmlUtils.count(data.getDocument(), xpath)));
        } catch (JDOMException jdomException) {
            throw new TypeConversionException(data.getName(), data.getTextValue(),
                                              "Error converting to XML: " + jdomException.getMessage(), jdomException);
        }
    }

    public T beautify(T data) { return format(data, PRETTY_XML_OUTPUTTER); }

    public T minify(T data) { return format(data, COMPRESSED_XML_OUTPUTTER); }

    private T format(T data, XMLOutputter outputter) {
        if (data == null || data.getValue() == null) { return null; }

        String outputXml = outputter.outputString(data.getDocument());
        data.setTextValue(outputXml.trim());
        return data;
    }

    public T append(T data, String xpath, String content) throws IOException {
        return modify(data, xpath, content, Modification.Companion.getAppend());
    }

    public T prepend(T data, String xpath, String content) throws IOException {
        return modify(data, xpath, content, Modification.Companion.getPrepend());
    }

    public T insertBefore(T data, String xpath, String content) throws IOException {
        return modify(data, xpath, content, Modification.Companion.getInsertBefore());
    }

    public T insertAfter(T data, String xpath, String content) throws IOException {
        return modify(data, xpath, content, Modification.Companion.getInsertAfter());
    }

    public T replace(T data, String xpath, String content) throws IOException {
        return modify(data, xpath, content, Modification.Companion.getReplace());
    }

    public T replaceIn(T data, String xpath, String content) throws IOException {
        return modify(data, xpath, content, Modification.Companion.getReplaceIn());
    }

    public T delete(T data, String xpath) throws IOException {
        return modify(data, xpath, null, Modification.Companion.getDelete());
    }

    public T clear(T data, String xpath) throws IOException {
        return modify(data, xpath, null, Modification.Companion.getClear());
    }

    protected T modify(T data, String xpath, String content, Modification modification) throws IOException {
        if (data == null || data.getDocument() == null || StringUtils.isEmpty(xpath)) { return null; }

        Document doc = data.getDocument();
        List matches = XmlUtils.findNodes(doc, xpath);
        if (CollectionUtils.isEmpty(matches)) {
            ConsoleUtils.error("No matches found on target XML using xpath '" + xpath + "'");
            return null;
        }

        int edits = modification.modify(matches, content);
        ConsoleUtils.log(edits + " edit(s) made to XML");
        data.setTextValue(XmlUtils.toPrettyXml(doc.getRootElement()));
        return data;
    }

    public T store(T data, String var) {
        snapshot(var, data);
        return data;
    }

    public ExpressionDataType save(T data, String path, String append) { return super.save(data, path, append); }

    @Override
    Map<String, Integer> listSupportedFunctions() { return FUNCTION_TO_PARAM_LIST; }

    @Override
    Map<String, Method> listSupportedMethods() { return FUNCTIONS; }

    protected String resolveContent(Object xmlObject) {
        if (xmlObject instanceof Element) { return ((Element) xmlObject).getTextNormalize(); }
        if (xmlObject instanceof Text) { return ((Text) xmlObject).getTextNormalize(); }
        if (xmlObject instanceof Content) { return ((Content) xmlObject).getValue(); }
        return xmlObject.toString();
    }

    protected Object updateContent(Object xmlObject, String content) {
        // in case `content` is XML document or array
        List<Content> newElems = new ArrayList<>();
        if (TextUtils.isBetween(content, "<", ">")) {
            try {
                Document newDoc = XmlUtils.parse("<root>" + content + "</root>");
                if (newDoc != null) {
                    List<Content> contents = newDoc.detachRootElement().detach().getContent();
                    while (!contents.isEmpty()) {
                        Content detached = contents.get(0).detach().detach();
                        newElems.add(detached);
                    }
                }
            } catch (JDOMException | IOException e) {
                // i guess we'll resolve to just text
                ConsoleUtils.log("Not valid XML document: " + e.getMessage());
            }
        }

        if (xmlObject instanceof Element) {
            Element element = (Element) xmlObject;
            if (CollectionUtils.isNotEmpty(newElems)) {
                return element.setText(null).setContent(newElems);
            } else {
                return element.setText(content);
            }
        }

        if (xmlObject instanceof Text) { return ((Text) xmlObject).setText(content); }

        return xmlObject;
    }

    protected String toTextValue(Element value) {
        if (value == null) { return null; }
        return OUTPUTTER.outputString(value);
    }
}
