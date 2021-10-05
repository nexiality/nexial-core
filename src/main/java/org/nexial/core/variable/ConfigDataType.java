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

package org.nexial.core.variable;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

import org.apache.commons.lang3.StringUtils;
import org.nexial.core.utils.ConsoleUtils;

import static java.lang.System.lineSeparator;

/**
 * Created by nv092106 on 7/16/2017.
 */
public class ConfigDataType extends ExpressionDataType<OrderedKeyProperties> {
    private ConfigTransformer<ConfigDataType> transformer = new ConfigTransformer<>();
    private String eol;

    public ConfigDataType(String textValue) throws TypeConversionException { super(textValue); }

    private ConfigDataType() { super(); }

    @Override
    public String getName() { return "CONFIG"; }

    public String getEol() { return eol; }

    @Override
    ConfigTransformer<ConfigDataType> getTransformer() { return transformer; }

    @Override
    ConfigDataType snapshot() {
        ConfigDataType snapshot = new ConfigDataType();
        snapshot.transformer = transformer;
        snapshot.value = value;
        snapshot.textValue = textValue;
        snapshot.eol = eol;
        return snapshot;
    }

    @Override
    protected void init() throws TypeConversionException {
        Reader reader = null;
        try {
            this.value = new OrderedKeyProperties();

            if (StringUtils.isNotBlank(textValue)) {
                if (StringUtils.contains(textValue, "\r\n")) {
                    ConsoleUtils.log("determined current CONFIG content uses CRLF as end-of-line character");
                    eol = "\r\n";
                } else if (StringUtils.contains(textValue, "\n")) {
                    ConsoleUtils.log("determined current CONFIG content uses LF as end-of-line character");
                    eol = "\n";
                } else {
                    ConsoleUtils.log("determined current CONFIG content uses OS default as end-of-line character");
                    eol = lineSeparator();
                }

                reader = new StringReader(textValue);
                value.load(reader);
            }

        } catch (IOException ioException) {
            throw new TypeConversionException(getName(), getTextValue(), "Error when converting " + textValue);
        } finally {
            try {
                if (reader != null) { reader.close(); }
            } catch (IOException e) {
                ConsoleUtils.log("Unable to close the Reader source");
            }
        }
    }

    protected void reset() {
        StringBuilder text = new StringBuilder();
        value.stringPropertyNames().forEach(key ->
                                                text.append(key).append("=").append(value.getProperty(key))
                                                    .append(eol));
        textValue = text.toString();
    }
}
