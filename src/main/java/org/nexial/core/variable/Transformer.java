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

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.nexial.commons.utils.FileUtil;
import org.nexial.commons.utils.RegexUtils;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.ExecutionThread;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.utils.ConsoleUtils;
import org.nexial.core.variable.Expression.ExpressionFunction;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.nexial.core.NexialConst.DEF_FILE_ENCODING;

public abstract class Transformer<T extends ExpressionDataType> {
    private static final String REGEX_CAMEL_CASE = "([0-9a-z])([A-Z])";

    public boolean isValidFunction(ExpressionFunction function) {
        if (function == null) { return false; }

        Map<String, Integer> validFunctions = listSupportedFunctions();
        if (MapUtils.isEmpty(validFunctions)) { return true; }

        if (!validFunctions.containsKey(function.getFunctionName())) { return false; }

        // varargs means paramCount < 0
        int paramCount = validFunctions.get(function.getFunctionName());
        int functionParamCount = CollectionUtils.size(function.getParams());

        if (paramCount < 0) { return functionParamCount >= 0; }

        // if user specified too many params, then it's error -> so return false
        if (paramCount < functionParamCount) { return false; }

        // if user didn't specify enough, then we can _supplement_ on behalf of user
        // this technique will allow us to add more params in the future without creating new methods
        if (paramCount > functionParamCount) {
            for (int i = functionParamCount; i < paramCount; i++) { function.getParams().add(null); }
        }

        // either paramCount > functionParamCount or paramCount == functionParamCount -> both are now considered valid
        return true;

    }

    ExpressionDataType transform(T data, ExpressionFunction function) throws ExpressionException {
        if (data == null || function == null) { return data; }

        String typeName = data.getName();
        String functionName = function.getFunctionName();
        String msgPrefix = StringUtils.rightPad(data.getName(), 7) + " => " + StringUtils.rightPad(functionName, 22);

        if (!isValidFunction(function)) {
            throw new ExpressionFunctionException(typeName, functionName, "Not valid function");
        }

        Method method = listSupportedMethods().get(functionName);
        if (method == null) { throw new ExpressionFunctionException(typeName, functionName, "Function not found"); }

        int paramCount = listSupportedFunctions().get(functionName);
        Object[] args;
        List<String> params = function.getParams();
        if (paramCount < 0) {
            // varargs means paramCount < 0
            int varargsParamCount = 1 + (paramCount * -1);
            args = new Object[varargsParamCount];
            for (int i = 0; i < varargsParamCount; i++) {
                if (i == 0) {
                    args[i] = data;
                } else if (i < varargsParamCount - 1) {
                    args[i] = params.get(i - 1);
                } else {
                    args[i] = params.subList(i - 1, params.size()).toArray(new String[0]);
                }
            }
        } else {
            args = new Object[paramCount + 1];
            args[0] = data;
            if (CollectionUtils.isNotEmpty(params)) {
                for (int i = 0; i < params.size(); i++) { args[i + 1] = params.get(i); }
            }
        }

        try {
            Object outcome = method.invoke(this, args);
            if (outcome == null) { return null; }
            if (!(outcome instanceof ExpressionDataType)) {
                throw new ExpressionFunctionException(typeName, functionName, "Invalid data type after transformation");
            }

            ExecutionContext context = ExecutionThread.get();
            if (context == null || StringUtils.isBlank(context.getRunId()) || context.isVerbose()) {
                ConsoleUtils.log(msgPrefix + outcome);
            }

            return (ExpressionDataType) outcome;
        } catch (IllegalAccessException | InvocationTargetException e) {
            ConsoleUtils.error(msgPrefix + e.getMessage());
            throw new ExpressionFunctionException(typeName, functionName, e.getMessage(), e);
        }
    }

    /**
     * a list of functions supported by this transformer, and the number of expected number of parameters per function.
     *
     * @return a map of function name (key) and the expected number of parameters (value)
     */
    abstract Map<String, Integer> listSupportedFunctions();

    abstract Map<String, Method> listSupportedMethods();

    protected ExpressionDataType save(ExpressionDataType data, String path, String append) {
        if (data == null || data.getValue() == null) { return data; }
        if (StringUtils.isBlank(path)) { throw new IllegalArgumentException("path is empty/blank"); }

        File target = FileUtil.makeParentDir(path);

        try {
            boolean shouldAppend = BooleanUtils.toBoolean(append);
            if (shouldAppend) {
                saveContentAsAppend(data, target);
            } else {
                saveContentAsOverwrite(data, target);
            }

            ConsoleUtils.log("content " + (shouldAppend ? "appended" : "saved") + " to '" + path + "'");
            return data;
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to write to " + path + ": " + e.getMessage(), e);
        }
    }

    protected void saveContentAsAppend(ExpressionDataType data, File target) throws IOException {
        FileUtils.writeStringToFile(target, data.getTextValue(), DEF_FILE_ENCODING, true);
    }

    protected void saveContentAsOverwrite(ExpressionDataType data, File target) throws IOException {
        FileUtils.writeStringToFile(target, data.getTextValue(), DEF_FILE_ENCODING);
    }

    protected TextDataType text(ExpressionDataType data) {
        TextDataType returnType;
        try {
            returnType = new TextDataType("");
        } catch (TypeConversionException e) {
            throw new IllegalArgumentException("Unable to extract text: " + e.getMessage(), e);
        }

        if (data == null || StringUtils.isEmpty(data.getTextValue())) { return returnType; }

        returnType.setValue(data.getTextValue());
        return returnType;
    }

    protected void snapshot(String var, ExpressionDataType data) {
        if (data == null) { return; }

        if (StringUtils.isBlank(var)) { throw new IllegalArgumentException("var is empty/blank"); }

        ExecutionContext context = ExecutionThread.get();
        if (context == null) { throw new IllegalStateException("Unable to reference execution context"); }

        context.setData(var, data.snapshot());
    }

    protected static Map<String, Integer> convertFunctionParamMap(String mapping) {
        Map<String, String> map = TextUtils.toMap(mapping, ",", "=");
        Map<String, Integer> newMap = new HashMap<>();
        map.forEach((function, paramCount) -> newMap.put(function, NumberUtils.toInt(paramCount)));
        return newMap;
    }

    protected static Map<String, Method> toFunctionMap(Map<String, Integer> functionToParamList,
                                                       Class<? extends Transformer> transformerClass,
                                                       Class<? extends ExpressionDataType> dataClass) {

        Map<String, Method> methodMap = new HashMap<>();

        functionToParamList.forEach((functionName, paramCount) -> {
            Class[] paramClasses;
            if (paramCount < 0) {
                // negative numbers means varargs
                // the position of varargs => (paramCount*-1)
                // e.g. paramCount=-2 => myMethod(dataClass,String,String...)
                int varargsParamCount = 1 + (paramCount * -1);
                paramClasses = new Class[varargsParamCount];
                for (int i = 0; i < varargsParamCount; i++) {
                    if (i == 0) {
                        paramClasses[i] = dataClass;
                    } else if (i != (varargsParamCount - 1)) {
                        paramClasses[i] = String.class;
                    } else {
                        paramClasses[i] = String[].class;
                    }
                }
            } else {
                paramClasses = new Class[paramCount + 1];
                paramClasses[0] = dataClass;
                for (int i = 1; i <= paramCount; i++) { paramClasses[i] = String.class; }
            }

            if (!StringUtils.contains(functionName, "-")) {
                try {
                    Method method = transformerClass.getMethod(functionName, paramClasses);
                    if (method != null) {
                        methodMap.put(functionName, method);
                        methodMap.put(expandMethodName(functionName), method);
                    }
                } catch (NoSuchMethodException e) {
                    String typeName = dataClass.getSimpleName();
                    String error = "Unable to resolve " + typeName + "." + functionName + "(): " + e.getMessage();
                    ConsoleUtils.log(error);
                    throw new IllegalArgumentException(error, e);
                }
            }
        });

        return methodMap;
    }

    protected static Map<String, Integer> discoverFunctions(Class<? extends Transformer> transformerClass) {
        if (transformerClass == null) { return null; }

        Class<ExpressionDataType> genericType = ExpressionDataType.class;

        Map<String, Integer> functions = new HashMap<>();
        Arrays.stream(transformerClass.getMethods()).forEach(method -> {
            // qualified method must be
            // (1) public
            // (2) return type must be a type of ExpressionDataType
            // (3) at least 1 param
            // (4) first param must be a type of ExpressionDataType
            // (5) all other param must be string
            if (isQualifiedMethod(method, genericType)) {

                String methodName = method.getName();
                String expandedMethodName = expandMethodName(methodName);

                int paramCount = method.getParameterCount() - 1;
                if (isStringVarArgMethod(method)) {
                    int varargsParamCount = (paramCount) * -1;
                    functions.put(methodName, varargsParamCount);
                    functions.put(expandedMethodName, varargsParamCount);
                } else {
                    boolean paramTypesAreString = true;
                    for (int i = 1; i < method.getParameterCount(); i++) {
                        if (method.getParameterTypes()[i] != String.class) {
                            paramTypesAreString = false;
                            break;
                        }
                    }

                    if (paramTypesAreString) {
                        functions.put(methodName, paramCount);
                        functions.put(expandedMethodName, paramCount);
                    }
                }
            }
        });

        return functions;
    }

    protected static String expandMethodName(String methodName) {
        return StringUtils.isBlank(methodName) ?
               methodName :
               StringUtils.lowerCase(RegexUtils.replace(methodName, REGEX_CAMEL_CASE, "$1-$2"));
    }

    protected static boolean isQualifiedMethod(Method method, Class<ExpressionDataType> genericType) {
        if (!Modifier.isPublic(method.getModifiers()) || !genericType.isAssignableFrom(method.getReturnType())) {
            return false;
        }

        int parameterCount = method.getParameterCount();
        Class<?>[] parameterTypes = method.getParameterTypes();
        if (parameterCount <= 0 || !genericType.isAssignableFrom(parameterTypes[0])) { return false; }

        if (isStringVarArgMethod(method)) { return parameterTypes[parameterCount - 1] == String[].class; }

        return true;
    }

    protected static boolean isStringVarArgMethod(Method method) {
        int parameterCount = method.getParameterCount();
        return parameterCount >= 2 &&
               method.isVarArgs() &&
               method.getParameterTypes()[parameterCount - 1] == String[].class;
    }
}
