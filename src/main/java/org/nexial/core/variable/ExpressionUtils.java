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

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.ExecutionThread;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.utils.CheckUtils;
import org.nexial.core.utils.ConsoleUtils;
import org.nexial.core.utils.OutputResolver;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.nexial.core.NexialConst.Data.RESOLVE_TEXT_AS_IS;
import static org.nexial.core.SystemVariables.getDefaultBool;
import static org.nexial.core.variable.BinaryDataType.BINARY;

public class ExpressionUtils {
    private ExpressionUtils() { }

    protected static String handleExternal(String dataType, String value) throws TypeConversionException {
        ExecutionContext context = ExecutionThread.get();
        if (StringUtils.equals(dataType, BINARY)) {
            // file content resolved by BinaryDataType itself
            return value;
        } else {
            boolean openFileAsIs = context != null ? context.isResolveTextAsIs() : getDefaultBool(RESOLVE_TEXT_AS_IS);
            return handleExternal(dataType, value, !openFileAsIs);
        }
    }

    protected static String handleExternal(String dataType, String value, boolean replaceTokens)
        throws TypeConversionException {
        ExecutionContext context = ExecutionThread.get();
        if (context == null) { return value; }

        try {
            // return OutputFileUtils.resolveContent(value, context, false, replaceTokens);
            return new OutputResolver(value, context, true, context.isResolveTextAsURL(), replaceTokens, false, false)
                       .getContent();
        } catch (Throwable e) {
            throw new TypeConversionException(dataType, value,
                                              "Error reading as file '" + value + "': " + e.getMessage(), e);
        }
    }

    protected static <T> T resumeExpression(String value, Class<T> dataType) {
        if (StringUtils.isBlank(value)) { return null; }
        if (!CheckUtils.isValidVariable(value)) { return null; }

        ExecutionContext context = ExecutionThread.get();
        if (context == null) { return null; }
        if (!context.hasData(value)) { return null; }

        // in order to consider 'value' as an expression snapshot, it must not be expressed as a variable,
        // but simply a variable name (without ${...})
        Object expressionSnapshot = context.getObjectData(value);
        if (expressionSnapshot == null) { return null; }

        // special rule for [LIST(...) => ...] to support array, list, collection data types
        if (dataType == ListDataType.class) {
            Class expressionSnapshotClass = expressionSnapshot.getClass();
            if (Collection.class.isAssignableFrom(expressionSnapshotClass)) {
                ConsoleUtils.log("converting COLLECTION data variable to LIST expression");
                Collection collection = (Collection) expressionSnapshot;
                List<String> list = new ArrayList<>();
                collection.forEach(item -> list.add(ObjectUtils.defaultIfNull(item, "") + ""));
                return (T) new ListDataType(list.toArray(new String[list.size()]));

            } else if (expressionSnapshotClass.isArray()) {
                ConsoleUtils.log("converting ARRAY data variable to LIST expression");
                int length = ArrayUtils.getLength(expressionSnapshot);
                String[] array = new String[length];
                for (int i = 0; i < length; i++) {
                    array[i] = ObjectUtils.defaultIfNull(java.lang.reflect.Array.get(expressionSnapshot, i), "") + "";
                }

                return (T) new ListDataType(array);
            }
        }

        if (!dataType.isAssignableFrom(expressionSnapshot.getClass())) { return null; }

        // return a copy, not the actual.  That way, changes made to this instance won't taint the original in context
        // explicit 'store()' expression is neeeded to override the context copy
        T original = dataType.cast(expressionSnapshot);
        if (original instanceof ExpressionDataType) {
            return (T) ((ExpressionDataType) original).snapshot();
        } else {
            return original;
        }
    }

    protected static String fixControlChars(String text) { return TextUtils.escapeLiterals(text); }

}
