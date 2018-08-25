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

import java.util.Date;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.nexial.commons.utils.DateUtility;
import org.nexial.commons.utils.RegexUtils;

import static org.nexial.core.NexialConst.*;

public class DateDataType extends ExpressionDataType<Date> {
    private Transformer transformer = new DateTransformer();

    private String format;

    public DateDataType(String textValue) throws TypeConversionException { super(textValue); }

    private DateDataType() { super(); }

    public String getFormat() { return format; }

    @Override
    public String getName() { return "DATE"; }

    @Override
    Transformer getTransformer() { return transformer; }

    @Override
    DateDataType snapshot() {
        DateDataType snapshot = new DateDataType();
        snapshot.transformer = transformer;
        snapshot.value = value;
        snapshot.textValue = textValue;
        return snapshot;
    }

    @Override
    protected void init() { parse(); }

    protected void parse() {
        // expects textValue to be either empty/blank or date,format
        if (StringUtils.isBlank(textValue) || ALIAS_NOW.contains(StringUtils.lowerCase(textValue))) {
            // blank means use current date/time and default date format
            value = new Date();
            format = STD_DATE_FORMAT;
            textValue = DateUtility.format(value.getTime(), format);
        } else if (NumberUtils.isDigits(textValue)) {
            // epoch
            format = EPOCH;
            value = new Date(NumberUtils.toLong(textValue));
        } else {
            // assuming default date format
            format = STD_DATE_FORMAT;
            if (RegexUtils.isExact(textValue, "^.+\\,.+$")) {
                // use specified date and date format
                format = StringUtils.substringAfter(textValue, ",");
                textValue = StringUtils.substringBefore(textValue, ",");
            } else {
                // use specified date and default date format
                textValue = StringUtils.substringBefore(textValue, ",");
            }

            value = StringUtils.equals(format, EPOCH) ?
                    new Date(NumberUtils.toLong(textValue)) : new Date(DateUtility.formatTo(textValue, format));
        }
    }
}
