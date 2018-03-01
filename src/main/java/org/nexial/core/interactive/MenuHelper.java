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

package org.nexial.core.interactive;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import org.nexial.core.NexialConst;
import org.nexial.core.interactive.InteractiveConst.Menu;

import static org.nexial.core.NexialConst.FlowControls.ARG_PREFIX;
import static org.nexial.core.NexialConst.FlowControls.ARG_SUFFIX;
import static org.nexial.core.NexialConst.TOKEN_END;
import static org.nexial.core.NexialConst.TOKEN_START;
import static java.lang.System.lineSeparator;

/**
 *
 */
public class MenuHelper {
	private static final Map<String, Method> SUPPORTED_FUNCTIONS = initSupportedFunctions();

	private MenuHelper() { }

	public static String buildMenu(String filename) { return file(filename, ""); }

	public static String banner(String prefixChar) {
		String prefix = StringUtils.isEmpty(prefixChar) ? Menu.DOT : prefixChar;
		return StringUtils.repeat(prefix, Menu.PREFIX_COUNT) + Menu.TITLE +
		       StringUtils.repeat(prefix, Menu.BANNER_WIDTH - Menu.TITLE.length() - Menu.PREFIX_COUNT);
	}

	public static String title(String command, String prefixChar) {
		if (StringUtils.isBlank(command)) { return "COMMAND NOT SPECIFIED!"; }

		String prefix = StringUtils.isEmpty(prefixChar) ? Menu.DOT : prefixChar;
		String cmd = " [" + command + "] ";
		return StringUtils.repeat(prefix, Menu.PREFIX_COUNT) + cmd +
		       StringUtils.repeat(prefix, Menu.BANNER_WIDTH - cmd.length() - Menu.PREFIX_COUNT);
	}

	public static String file(String filename, String prefix) {
		try {
			return readResource(filename, prefix);
		} catch (IOException e) {
			return e.getMessage();
		}
	}

	public static String menu(String command) {
		if (StringUtils.isEmpty(command)) { return "COMMAND NOT SPECIFIED"; }

		try {
			String filename = command + Menu.EXT;
			return lineSeparator() + MenuHelper.title(command, Menu.BANNER_PREFIX) + lineSeparator() +
			       readResource(filename, Menu.MENU_PREFIX) + MenuHelper.repeat(Menu.BANNER_PREFIX,
			                                                                    Menu.BANNER_WIDTH_TEXT) +
			       lineSeparator();
		} catch (IOException e) {
			return e.getMessage();
		}
	}

	public static String repeat(String ch, String length) {
		if (StringUtils.isEmpty(ch)) { return "REPEATING CHARACTER NOT SPECIFIED!"; }
		if (!NumberUtils.isDigits(length)) { return "NON-NUMERIC LENGTH FOUND: " + length; }

		return StringUtils.repeat(ch, NumberUtils.toInt(length));
	}

	protected static String readResource(String filename, String decoratePrefix) throws IOException {
		if (StringUtils.isBlank(filename)) { throw new IOException("FILENAME NOT SPECIFIED!"); }

		InputStream resourceStream = MenuHelper.class.getResourceAsStream(filename);
		if (resourceStream == null) { throw new IOException("FILE '" + filename + "' NOT RESOLVED"); }

		List<String> helpContent;
		try {
			helpContent = IOUtils.readLines(resourceStream, NexialConst.DEF_CHARSET);
		} catch (IOException e) {
			throw new IOException("FILE '" + filename + "' NOT ACCESSIBLE: " + e.getMessage());
		}

		if (CollectionUtils.isEmpty(helpContent)) { throw new IOException("FILE '" + filename + "' IS EMPTY"); }

		decoratePrefix = StringUtils.defaultString(decoratePrefix);

		StringBuilder sb = new StringBuilder();
		for (String line : helpContent) {
			sb.append(decoratePrefix);

			String[] functions = StringUtils.substringsBetween(line, TOKEN_START, TOKEN_END);
			if (ArrayUtils.isEmpty(functions)) {
				sb.append(line);
			} else {
				for (String function : functions) {
					line = StringUtils.replace(line, TOKEN_START + function + TOKEN_END, evaluate(function));
				}
				sb.append(line);
			}

			sb.append(lineSeparator());
		}

		return sb.toString();
	}

	private static Map<String, Method> initSupportedFunctions() {
		Map<String, Method> supported = new HashMap<>();
		Method[] allMethods = MenuHelper.class.getMethods();
		for (Method method : allMethods) {
			if (Modifier.isPublic(method.getModifiers()) || Modifier.isStatic(method.getModifiers())) {
				supported.put(method.getName(), method);
			}
		}

		return supported;
	}

	private static String evaluate(String function) {
		String functionName = StringUtils.substringBefore(function, ARG_PREFIX);
		if (StringUtils.isBlank(functionName)) { return "FUNCTION NAME NOT SPECIFIED!"; }

		Method method = MenuHelper.SUPPORTED_FUNCTIONS.get(functionName);
		if (method == null) { return "FUNCTION '" + functionName + "' NOT FOUND/SUPPORTED"; }

		String[] arguments = StringUtils.split(StringUtils.substringBetween(function, ARG_PREFIX, ARG_SUFFIX), ",");

		Class<?>[] paramTypes = method.getParameterTypes();
		if (ArrayUtils.getLength(paramTypes) != ArrayUtils.getLength(arguments)) {
			return "FUNCTION '" + functionName + "' INCORRECTLY SPECIFIED; " +
			       "EXPECTS " + ArrayUtils.getLength(paramTypes) + " argument(s)";
		}

		String errPrefix = "FUNCTION '" + functionName + "' FAILED TO EXECUTE: ";
		try {
			Object result = method.invoke(null, (Object[]) arguments);
			return result == null ? null : result.toString();
		} catch (InvocationTargetException e) {
			return errPrefix + (e.getTargetException() != null ? e.getTargetException().getMessage() : e.getMessage());
		} catch (Exception e) {
			return errPrefix + e.getMessage();
		}
	}
}
