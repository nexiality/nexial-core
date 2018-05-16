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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jdom2.Attribute;
import org.jdom2.Content;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.output.XMLOutputter;
import org.nexial.commons.utils.TextUtils;
import org.nexial.commons.utils.XmlUtils;

public class XmlTransformer extends Transformer<XmlDataType> {
    private static final Map<String, Integer> FUNCTION_TO_PARAM_LIST = discoverFunctions(XmlTransformer.class);
    private static final Map<String, Method> FUNCTIONS =
        toFunctionMap(FUNCTION_TO_PARAM_LIST, XmlTransformer.class, XmlDataType.class);
    private static final XMLOutputter outputter = new XMLOutputter();
    private static final String NODE_EXTRACT = "extracted";

    public TextDataType text(XmlDataType data) { return super.text(data); }

    public ExpressionDataType extract(XmlDataType data, String xpath) {
        if (data == null || data.getDocument() == null || StringUtils.isBlank(xpath)) { return data; }

        try {
            List match = XmlUtils.findNodes(data.getDocument(), xpath);
            if (CollectionUtils.isEmpty(match)) { return null; }

            Object firstMatch = match.get(0);
            if (match.size() == 1) {
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
            } else {
                if (firstMatch instanceof Element) {
                    Element root = new Element(NODE_EXTRACT);
                    match.forEach(instance -> root.addContent(((Element) instance).detach()));

                    // data.setValue(root);
                    data.setTextValue(toTextValue(root));
                    data.parse();
                    return data;
                }

                List<String> list = new ArrayList<>();
                if (firstMatch instanceof Content) {
                    match.forEach(instance -> list.add(((Content) instance).getValue()));
                } else if (firstMatch instanceof Attribute) {
                    match.forEach(instance -> list.add(((Attribute) instance).getValue()));
                } else {
                    match.forEach(instance -> list.add(instance.toString()));
                }

                return new ListDataType(TextUtils.toString(list, ","), ",");
            }

        } catch (TypeConversionException e) {
            throw new IllegalArgumentException("Unable to process XML: " + e.getMessage(), e);
        }
    }

    public NumberDataType count(XmlDataType data, String xpath) throws ExpressionException {
        if (data == null || data.getDocument() == null || StringUtils.isEmpty(xpath)) { return null; }
        try {
            return new NumberDataType(String.valueOf(XmlUtils.count(data.getDocument(), xpath)));
        } catch (JDOMException jdomException) {
            throw new TypeConversionException(data.getName(), data.getTextValue(),
                                              "Error converting to XML: " + jdomException.getMessage(), jdomException);
        }
    }

    public XmlDataType store(XmlDataType data, String var) {
        snapshot(var, data);
        return data;
    }

    @Override
    Map<String, Integer> listSupportedFunctions() { return FUNCTION_TO_PARAM_LIST; }

    @Override
    Map<String, Method> listSupportedMethods() { return FUNCTIONS; }

    protected String toTextValue(Element value) {
        if (value == null) { return null; }
        return outputter.outputString(value);
    }
}
