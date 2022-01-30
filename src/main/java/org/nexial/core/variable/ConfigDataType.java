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

import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.utils.FileUtil;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.utils.ConsoleUtils;

import java.util.Arrays;
import java.util.Map;

import static java.lang.System.lineSeparator;
import static org.nexial.commons.utils.TextUtils.DuplicateKeyStrategy.FavorLast;

/**
 * Created by nv092106 on 7/16/2017.
 */
public class ConfigDataType extends ExpressionDataType<OrderedKeyProperties> {
    private ConfigTransformer<ConfigDataType> transformer = new ConfigTransformer<>();
    private String eol;
    private String file;

    public ConfigDataType(String textValue) throws TypeConversionException { super(textValue); }

    private ConfigDataType() { super(); }

    @Override
    public String getName() { return "CONFIG"; }

    public String getEol() { return eol; }

    public String getFile() { return file; }

    public void setFile(String file) { this.file = file; }

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
        if (FileUtil.isFileReadable(file)) {
            Map<String, Map<String, String>> props = TextUtils.loadProperties(file, false, false, FavorLast);
            if (props != null) {
                this.value = new OrderedKeyProperties();
                props.values().forEach(this.value::putAll);
            }
            return;
        }

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

            Arrays.stream(StringUtils.split(textValue, eol)).forEach(line -> {
                if (StringUtils.isNotBlank(line) &&
                    !StringUtils.startsWithAny(line, "#", "!") &&
                    StringUtils.containsAny(line, "=", ":")) {

                    int indexColon = StringUtils.indexOf(line, ":");
                    int indexEqual = StringUtils.indexOf(line, "=");
                    String separator = indexColon == -1 ? "=" :
                                       indexEqual == -1 ? ":" :
                                       indexColon > indexEqual ? "=" : ":";
                    String key = StringUtils.substringBefore(line, separator);
                    String val = StringUtils.substringAfter(line, separator);
                    value.setProperty(key, val);
                }
            });

        }
    }

    protected void reset() {
        StringBuilder text = new StringBuilder();
        value.stringPropertyNames()
             .forEach(key -> text.append(key).append("=").append(value.getProperty(key)).append(eol));
        textValue = text.toString();
    }
}
