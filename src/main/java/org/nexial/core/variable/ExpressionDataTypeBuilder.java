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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.utils.RegexUtils;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.ExecutionThread;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.utils.ConsoleUtils;

import static org.nexial.core.NexialConst.Data.TEXT_DELIM;
import static org.nexial.core.NexialConst.getDefault;
import static org.nexial.core.variable.ExpressionConst.REGEX_VALID_TYPE_PREFIX;
import static org.nexial.core.variable.ExpressionConst.REGEX_VALID_TYPE_SUFFIX;
import static org.nexial.core.variable.ExpressionUtils.handleExternal;
import static org.nexial.core.variable.ExpressionUtils.resumeExpression;

final class ExpressionDataTypeBuilder {
    private static final Map<String, Method> NEW_INSTANCE_METHODS = initNewInstanceMethods();
    private static final String REGEX_VALID_TYPE = resolveValidTypeRegex(NEW_INSTANCE_METHODS);
    private ExecutionContext context;

    public ExpressionDataTypeBuilder(ExecutionContext context) { this.context = context; }

    boolean isValidType(String expression) { return RegexUtils.isExact(expression, REGEX_VALID_TYPE, true); }

    List<String> parseExpressionGroups(String expression) {
        return RegexUtils.collectGroups(expression, REGEX_VALID_TYPE, false, true);
    }

    ExpressionDataType newDataType(String dataType, String value) throws TypeConversionException {
        Method constr = NEW_INSTANCE_METHODS.get(dataType);
        if (constr == null) { return null; }

        try {
            return (ExpressionDataType) constr.invoke(this, value);
        } catch (IllegalAccessException | InvocationTargetException e) {
            ConsoleUtils.error("Unable to construct new instance of " + dataType + ": " + e.getMessage());
            Throwable ex = e instanceof InvocationTargetException ?
                           ((InvocationTargetException) e).getTargetException() : e;
            if (ex instanceof TypeConversionException) { throw (TypeConversionException) ex; }
            throw new TypeConversionException(dataType, value, ex.getMessage(), ex);
        }
    }

    Bai2DataType newBai2DataType(String value) throws TypeConversionException {
        Bai2DataType data = resumeExpression(value, Bai2DataType.class);
        return data != null ? data : new Bai2DataType(handleExternal("BAI2", value));
    }

    TextDataType newTextDataType(String value) throws TypeConversionException {
        TextDataType data = resumeExpression(value, TextDataType.class);
        return data != null ? data : new TextDataType(handleExternal("TEXT", value));
    }

    JsonDataType newJsonDataType(String value) throws TypeConversionException {
        JsonDataType data = resumeExpression(value, JsonDataType.class);
        return data != null ? data : new JsonDataType(handleExternal("JSON", value));
    }

    XmlDataType newXmlDataType(String value) throws TypeConversionException {
        XmlDataType data = resumeExpression(value, XmlDataType.class);
        return data != null ? data : new XmlDataType(handleExternal("XML", value));
    }

    NumberDataType newNumberDataType(String value) throws TypeConversionException {
        NumberDataType data = resumeExpression(value, NumberDataType.class);
        return data != null ? data : new NumberDataType(handleExternal("NUMBER", value));
    }

    ListDataType newListDataType(String value) {
        ListDataType data = resumeExpression(value, ListDataType.class);
        return data != null ?
               data : new ListDataType(value, context != null ? context.getTextDelim() : getDefault(TEXT_DELIM));
    }

    DateDataType newDateDataType(String value) throws TypeConversionException {
        DateDataType data = resumeExpression(value, DateDataType.class);
        return data != null ? data : new DateDataType(handleExternal("DATE", value));
    }

    ConfigDataType newConfigDataType(String value) throws TypeConversionException {
        ConfigDataType data = resumeExpression(value, ConfigDataType.class);
        return data != null ? data : new ConfigDataType(handleExternal("CONFIG", value));
    }

    IniDataType newIniDataType(String value) throws TypeConversionException {
        IniDataType data = resumeExpression(value, IniDataType.class);
        return data != null ? data : new IniDataType(handleExternal("INI", value));
    }

    CsvDataType newCsvDataType(String value) throws TypeConversionException {
        CsvDataType data = resumeExpression(value, CsvDataType.class);
        if (data != null) { return data; }

        ExecutionContext context = ExecutionThread.get();
        if (context != null && context.hasData(value)) {
            CsvDataType csvData = CsvTransformer.resolveExpressionTypeInContext(value);
            if (csvData != null) { return csvData; }
        }

        return new CsvDataType(handleExternal("CSV", value));
    }

    ExcelDataType newExcelDataType(String value) throws TypeConversionException {
        ExcelDataType data = resumeExpression(value, ExcelDataType.class);
        // STUPID MIKE, YOU CAN'T DO THIS!!! EXCEL IS NOT PLAIN TEXT!!!
        // return data != null ? data : new ExcelDataType(handleExternal("EXCEL", value));
        return data != null ? data : new ExcelDataType(value);
    }

    SqlDataType newSqlDataType(String value) throws TypeConversionException {
        SqlDataType data = resumeExpression(value, SqlDataType.class);
        return data != null ? data : new SqlDataType(handleExternal("SQL", value));
    }

    private static String resolveValidTypeRegex(Map<String, Method> newInstanceMethods) {
        if (MapUtils.isEmpty(newInstanceMethods)) { return ".+"; }

        String validTypes = TextUtils.toString(
            newInstanceMethods.keySet().toArray(new String[newInstanceMethods.size()]), "|", "", "");
        return REGEX_VALID_TYPE_PREFIX + validTypes + REGEX_VALID_TYPE_SUFFIX;
    }

    private static Map<String, Method> initNewInstanceMethods() {
        Map<String, Method> map = new HashMap<>();

        Method[] methods = ExpressionDataTypeBuilder.class.getDeclaredMethods();
        Arrays.stream(methods).forEach(method -> {
            String methodName = method.getName();
            if (RegexUtils.isExact(methodName, "^new.+DataType$") &&
                ExpressionDataType.class.isAssignableFrom(method.getReturnType()) &&
                method.getParameterCount() == 1 &&
                method.getParameterTypes()[0] == String.class) {
                map.put(StringUtils.upperCase(StringUtils.substringBetween(methodName, "new", "DataType")), method);
            }
        });

        return map;
    }

}
