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
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import static java.util.Calendar.*;

public class DateTransformer extends Transformer<DateDataType> {
    private static final Map<String, Integer> FUNCTION_TO_PARAM = discoverFunctions(DateTransformer.class);
    private static final Map<String, Method> FUNCTIONS =
        toFunctionMap(FUNCTION_TO_PARAM, DateTransformer.class, DateDataType.class);
    private static final org.nexial.core.variable.Date dateHelper = new org.nexial.core.variable.Date();

    public TextDataType text(DateDataType data) { return super.text(data); }

    public TextDataType format(DateDataType data, String format) {
        TextDataType returnType;
        try {
            returnType = new TextDataType("");
        } catch (TypeConversionException e) {
            throw new IllegalArgumentException("Unable to extract text: " + e.getMessage(), e);
        }

        if (data == null || StringUtils.isBlank(data.getTextValue()) || StringUtils.isBlank(format)) {
            return returnType;
        }

        String formatted = StringUtils.equals(format, "epoch") ?
                           data.getValue().getTime() + "" :
                           dateHelper.format(data.getTextValue(), data.getFormat(), format);
        returnType.setValue(formatted);
        return returnType;
    }

    public DateDataType addYear(DateDataType data, String years) { return addDateField(data, YEAR, years); }

    public DateDataType addMonth(DateDataType data, String months) {return addDateField(data, MONTH, months); }

    public DateDataType addDay(DateDataType data, String days) { return addDateField(data, DAY_OF_MONTH, days); }

    public DateDataType addHour(DateDataType data, String hours) { return addDateField(data, HOUR_OF_DAY, hours); }

    public DateDataType addMinute(DateDataType data, String minutes) { return addDateField(data, MINUTE, minutes); }

    public DateDataType addSecond(DateDataType data, String seconds) { return addDateField(data, SECOND, seconds); }

    public DateDataType setYear(DateDataType data, String years) { return setDateField(data, YEAR, years); }

    public DateDataType setMonth(DateDataType data, String months) { return setDateField(data, MONTH, months); }

    public DateDataType setDay(DateDataType data, String days) { return setDateField(data, DAY_OF_MONTH, days); }

    public DateDataType setDOW(DateDataType data, String days) { return setDateField(data, DAY_OF_WEEK, days); }

    public DateDataType setHour(DateDataType data, String hours) { return setDateField(data, HOUR_OF_DAY, hours); }

    public DateDataType setMinute(DateDataType data, String minutes) { return setDateField(data, MINUTE, minutes); }

    public DateDataType setSecond(DateDataType data, String seconds) { return setDateField(data, SECOND, seconds); }

    public DateDataType store(DateDataType data, String var) {
        snapshot(var, data);
        return data;
    }

    @Override
    Map<String, Integer> listSupportedFunctions() { return FUNCTION_TO_PARAM; }

    @Override
    Map<String, Method> listSupportedMethods() { return FUNCTIONS; }

    protected DateDataType setDateField(DateDataType data, int field, String value) {
        return handleDateField(data, field, value, false);
    }

    protected DateDataType addDateField(DateDataType data, int field, String value) {
        return handleDateField(data, field, value, true);
    }

    protected DateDataType handleDateField(DateDataType data, int field, String value, boolean add) {
        if (data == null || StringUtils.isBlank(data.getTextValue())) { return data; }
        if (StringUtils.isBlank(value) || !NumberUtils.isDigits(value)) { return data; }

        int val = NumberUtils.toInt(value);

        Calendar c = Calendar.getInstance();
        c.setTime(data.getValue());
        if (add) {
            c.add(field, val);
        } else {
            if (field == MONTH) { val--; }
            c.set(field, val);
        }

        java.util.Date date = c.getTime();
        data.setValue(date);
        data.setTextValue(new SimpleDateFormat(data.getFormat()).format(date));
        return data;
    }
}
