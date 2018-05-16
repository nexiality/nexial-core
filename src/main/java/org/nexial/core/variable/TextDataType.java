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

public class TextDataType extends ExpressionDataType<String> {
    private Transformer transformer = new TextTransformer();

    public TextDataType(String textValue) throws TypeConversionException { super(textValue); }

    private TextDataType() { super(); }

    public static TextDataType newEmptyInstance() {
        try {
            return new TextDataType("");
        } catch (TypeConversionException e) {
            // really unlikely...
            throw new IllegalArgumentException("Unable to create empty text data: " + e.getMessage(), e);
        }
    }

    @Override
    public String getName() { return "TEXT"; }

    @Override
    public void setValue(String value) {
        super.setValue(value);
        setTextValue(value);
    }

    @Override
    TextDataType snapshot() {
        TextDataType snapshot = new TextDataType();
        snapshot.transformer = transformer;
        snapshot.value = value;
        snapshot.textValue = textValue;
        return snapshot;
    }

    @Override
    Transformer getTransformer() { return transformer; }

    protected void init() { this.value = textValue; }
}
