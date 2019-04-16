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

import java.math.BigDecimal;

import org.apache.commons.lang3.RegExUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

public class NumberDataType extends ExpressionDataType<Number> {
    public NumberTransformer transformer = new NumberTransformer();

    public NumberDataType(String textValue) throws TypeConversionException { super(textValue); }

    private NumberDataType() { super(); }

    @Override
    public String getName() { return "NUMBER"; }

    public void setTextValue(Number value) {
        if (value instanceof Double || value instanceof Float) {
            setTextValue(BigDecimal.valueOf(value.doubleValue()).toPlainString());
        } else {
            setTextValue(value.longValue() + "");
        }
    }

    @Override
    Transformer getTransformer() { return transformer; }

    @Override
    NumberDataType snapshot() {
        NumberDataType snapshot = new NumberDataType();
        snapshot.transformer = transformer;
        snapshot.value = value;
        snapshot.textValue = textValue;
        return snapshot;
    }

    @Override
    protected void init() {
        String text = StringUtils.trim(textValue);
        if (StringUtils.isBlank(text)) {
            setToZero();
            return;
        }

        // in case start with - or +
        boolean isNegative = StringUtils.startsWith(text, "-");
        text = StringUtils.removeStart(text, "+");
        text = StringUtils.removeStart(text, "-");

        text = RegExUtils.removeFirst(text, "^0{1,}");
        if (StringUtils.isBlank(text)) {
            // all zeros means 0
            setToZero();
            return;
        }

        if (StringUtils.startsWithIgnoreCase(text, ".")) { text = "0" + text; }
        if (isNegative) { text = "-" + text; }

        if (StringUtils.contains(text, ".")) {
            setValue(NumberUtils.createDouble(text));
            setTextValue(BigDecimal.valueOf(value.doubleValue()).toPlainString());
        } else {
            setValue(NumberUtils.createLong(text));
            setTextValue(text);
        }

    }

    private void setToZero() {
        setValue(0);
        setTextValue("0");
    }
}
