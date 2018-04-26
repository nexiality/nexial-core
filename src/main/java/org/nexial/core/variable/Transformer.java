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

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import org.nexial.commons.utils.FileUtil;
import org.nexial.commons.utils.RegexUtils;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.ExecutionThread;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.utils.ConsoleUtils;
import org.nexial.core.variable.Expression.ExpressionFunction;

import static org.nexial.core.NexialConst.DEF_FILE_ENCODING;
import static java.lang.System.lineSeparator;

public abstract class Transformer<T extends ExpressionDataType> {
	private static final String REGEX_CAMEL_CASE = "([0-9a-z])([A-Z])";

	public boolean isValidFunction(ExpressionFunction function) {
		if (function == null) { return false; }

		Map<String, Integer> validFunctions = listSupportedFunctions();
		if (MapUtils.isEmpty(validFunctions)) { return true; }

		if (!validFunctions.containsKey(function.getFunctionName())) { return false; }

		// varargs means paramCount = -1
		int paramCount = validFunctions.get(function.getFunctionName());
		int functionParamCount = CollectionUtils.size(function.getParams());
		return (paramCount == -1 && functionParamCount >= 0) || paramCount == functionParamCount;
	}

	ExpressionDataType transform(T data, ExpressionFunction function) throws ExpressionException {
		if (data == null) { return data; }
		if (function == null) { return data; }

		String typeName = data.getName();
		String functionName = function.getFunctionName();
		String msgPrefix = StringUtils.rightPad(data.getName() + " => " + functionName + " ", 20);

		if (!isValidFunction(function)) {
			throw new ExpressionFunctionException(typeName, functionName, "Not valid function");
		}

		Method method = listSupportedMethods().get(functionName);
		if (method == null) { throw new ExpressionFunctionException(typeName, functionName, "Function not found"); }

		int paramCount = listSupportedFunctions().get(functionName);
		Object[] args;
		if (paramCount == -1) {
			// varargs means paramCount = -1
			args = new Object[]{data, function.getParams().toArray(new String[function.getParams().size()])};
		} else {
			args = new Object[paramCount + 1];
			args[0] = data;
			List<String> params = function.getParams();
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
				ConsoleUtils.log(msgPrefix + outcome + lineSeparator());
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
	 * @return a fileHeaderMap of function name (key) and the expected number of parameters (value)
	 */
	abstract Map<String, Integer> listSupportedFunctions();

	abstract Map<String, Method> listSupportedMethods();

	protected ExpressionDataType save(ExpressionDataType data, String path) {
		if (data == null || data.getValue() == null) { return data; }
		if (StringUtils.isBlank(path)) { throw new IllegalArgumentException("path is empty/blank"); }

		if (!FileUtil.isDirectoryReadable(path)) {
			try {
				FileUtils.forceMkdirParent(new File(path));
			} catch (IOException e) {
				throw new IllegalArgumentException("Unable to create directory for '" + path + "'");
			}
		}

		try {
			FileUtils.writeStringToFile(new File(path), data.getTextValue(), DEF_FILE_ENCODING);
			ConsoleUtils.log("content saved to '" + path + "'");
			return data;
		} catch (IOException e) {
			throw new IllegalArgumentException("Unable to write to " + path + ": " + e.getMessage(), e);
		}
	}

	protected TextDataType text(ExpressionDataType data) {
		TextDataType returnType;
		try {
			returnType = new TextDataType("");
		} catch (TypeConversionException e) {
			throw new IllegalArgumentException("Unable to extract text: " + e.getMessage(), e);
		}

		if (data == null || StringUtils.isBlank(data.getTextValue())) { return returnType; }

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
			if (paramCount == -1) {
				// string varargs
				paramClasses = new Class[]{dataClass, String[].class};
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

				if (isStringVarArgMethod(method)) {
					functions.put(methodName, -1);
					functions.put(expandedMethodName, -1);
				} else {
					boolean paramTypesAreString = true;
					for (int i = 1; i < method.getParameterCount(); i++) {
						if (method.getParameterTypes()[i] != String.class) {
							paramTypesAreString = false;
							break;
						}
					}

					if (paramTypesAreString) {
						functions.put(methodName, method.getParameterCount() - 1);
						functions.put(expandedMethodName, method.getParameterCount() - 1);
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

		if (isStringVarArgMethod(method)) {
			if (parameterCount != 2) { return false; }
            return parameterTypes[1] == String[].class;
		}

		return true;
	}

	protected static boolean isStringVarArgMethod(Method method) {
		return method.getParameterCount() == 2 && method.isVarArgs() && method.getParameterTypes()[1] == String[].class;
	}
}
