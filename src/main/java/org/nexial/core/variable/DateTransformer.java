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
import static org.nexial.core.NexialConst.EPOCH;

public class DateTransformer<T extends DateDataType> extends Transformer {
    private static final Map<String, Integer> FUNCTION_TO_PARAM = discoverFunctions(DateTransformer.class);
    private static final Map<String, Method> FUNCTIONS =
        toFunctionMap(FUNCTION_TO_PARAM, DateTransformer.class, DateDataType.class);
    private static final org.nexial.core.variable.Date dateHelper = new org.nexial.core.variable.Date();

    public TextDataType text(T data) { return super.text(data); }

    public TextDataType format(T data, String format) {
        TextDataType returnType;
        try {
            returnType = new TextDataType("");
        } catch (TypeConversionException e) {
            throw new IllegalArgumentException("Unable to extract text: " + e.getMessage(), e);
        }

        if (data == null || StringUtils.isBlank(data.getTextValue()) || StringUtils.isBlank(format)) {
            return returnType;
        }

        String formatted = StringUtils.equals(format, EPOCH) ?
                           data.getValue().getTime() + "" :
                           dateHelper.format(data.getTextValue(), data.getFormat(), format);
        returnType.setValue(formatted);
        return returnType;
    }

    public T addYear(T data, String years) { return addDateField(data, YEAR, years); }

    public T addMonth(T data, String months) {return addDateField(data, MONTH, months); }

    public T addDay(T data, String days) { return addDateField(data, DAY_OF_MONTH, days); }

    public T addHour(T data, String hours) { return addDateField(data, HOUR_OF_DAY, hours); }

    public T addMinute(T data, String minutes) { return addDateField(data, MINUTE, minutes); }

    public T addSecond(T data, String seconds) { return addDateField(data, SECOND, seconds); }

    public T setYear(T data, String years) { return setDateField(data, YEAR, years); }

    public T setMonth(T data, String months) { return setDateField(data, MONTH, months); }

    public T setDay(T data, String days) { return setDateField(data, DAY_OF_MONTH, days); }

    public T setDOW(T data, String days) { return setDateField(data, DAY_OF_WEEK, days); }

    public T setHour(T data, String hours) { return setDateField(data, HOUR_OF_DAY, hours); }

    public T setMinute(T data, String minutes) { return setDateField(data, MINUTE, minutes); }

    public T setSecond(T data, String seconds) { return setDateField(data, SECOND, seconds); }

    public T store(T data, String var) {
        snapshot(var, data);
        return data;
    }

    @Override
    Map<String, Integer> listSupportedFunctions() { return FUNCTION_TO_PARAM; }

    @Override
    Map<String, Method> listSupportedMethods() { return FUNCTIONS; }

    protected T setDateField(T data, int field, String value) {
        return handleDateField(data, field, value, false);
    }

    protected T addDateField(T data, int field, String value) {
        return handleDateField(data, field, value, true);
    }

    protected T handleDateField(T data, int field, String value, boolean add) {
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
