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

import org.ini4j.Config;
import org.ini4j.Ini;
import org.nexial.core.utils.ConsoleUtils;

/**
 * Created by nv092106 on 7/18/2017.
 */
public class IniDataType extends ExpressionDataType<Ini> {
    private Transformer transformer = new IniTransformer();

    public IniDataType(String textValue) throws TypeConversionException { super(textValue); }

    private IniDataType() { super(); }

    @Override
    public String getName() { return "INI"; }

    @Override
    Transformer getTransformer() { return transformer; }

    @Override
    IniDataType snapshot() {
        IniDataType snapshot = new IniDataType();
        snapshot.transformer = transformer;
        snapshot.value = value;
        snapshot.textValue = textValue;
        return snapshot;
    }

    @Override
    protected void init() throws TypeConversionException {
        Reader reader = null;
        try {
            String newline = textValue.replace("\\", "\\\\");
            reader = new StringReader(newline);
            value = new Ini();
            value.load(reader);
            Config config = new Config();
            config.setEmptySection(true);
            config.setMultiSection(false);
            config.setMultiOption(false);
            config.setEscape(false);
            value.setConfig(config);
        } catch (IOException e) {
            throw new TypeConversionException(getName(), textValue, e.getMessage(), e.getCause());
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    ConsoleUtils.log("Unable to close the Reader Source");
                }
            }
        }
    }
}
