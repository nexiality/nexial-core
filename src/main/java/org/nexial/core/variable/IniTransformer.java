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
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.io.Writer;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.ini4j.Ini;
import org.ini4j.Profile.Section;

import org.nexial.core.utils.ConsoleUtils;

import static org.nexial.core.variable.ExpressionUtils.handleExternal;

/**
 * Created by nv092106 on 7/19/2017.
 */
public class IniTransformer extends Transformer<IniDataType> {
	private static final Map<String, Integer> FUNCTION_TO_PARAM_LIST = discoverFunctions(IniTransformer.class);
	private static final Map<String, Method> FUNCTIONS =
		toFunctionMap(FUNCTION_TO_PARAM_LIST, IniTransformer.class, IniDataType.class);

	public TextDataType text(IniDataType data) { return super.text(data); }

	public TextDataType value(IniDataType data, String sectionName, String key) throws TypeConversionException {

		if (data == null || data.getValue() == null || StringUtils.isBlank(sectionName) || StringUtils.isBlank(key)) {
			return null;
		}
		try {
			Section section = data.getValue().get(sectionName);
			if (section != null) {
				return new TextDataType(section.get(key));
			} else { return null; }
		} catch (TypeConversionException e) {
			throw new TypeConversionException(data.getName(), data.getTextValue(),
			                                  "Error converting to TextDataType: " + e.getMessage(), e);
		}
	}

	public IniDataType set(IniDataType data, String sectionName, String key, String value) {
		if (data == null || data.getValue() == null || StringUtils.isBlank(sectionName) || StringUtils.isBlank(key)) {
			return null;
		}
		Ini ini = data.getValue();
		if (ini.containsKey(sectionName)) {
			Section section = ini.get(sectionName);
			if (value != null) { section.put(key, value); } else { section.remove(key); }
		} else {
			if (value != null) { ini.put(sectionName, key, value); }
		}
		data.setTextValue(ini.toString());
		return data;
	}

	public ListDataType values(IniDataType data, String section) throws TypeConversionException {
		if (data == null || data.getValue() == null || StringUtils.isBlank(section)) {return null;}

		try {
			Ini ini = data.getValue();
			Section targetSection = ini.get(section);
			if (targetSection == null) {
				return new ListDataType("");
			} else {
				List<String> list = (List<String>) ini.get(section).values();
				return new ListDataType(list.toString());
			}
		} catch (TypeConversionException typeConversionException) {
			throw new TypeConversionException(data.getName(),
			                                  data.getTextValue(),
			                                  "Error converting into ListDataType: " +
			                                  typeConversionException.getMessage(),
			                                  typeConversionException);
		}
	}

	public IniDataType remove(IniDataType data, String section, String key) {
		if (data == null || data.getValue() == null || StringUtils.isBlank(section) || StringUtils.isBlank(key)) {
			return null;
		}
		Ini ini = data.getValue();
		if (ini.containsKey(section)) {
			if (key.equals("*")) { ini.remove(section);} else { ini.get(section).remove(key); }
		} else {
			throw new IllegalArgumentException("Remove operation cannot be performed as Section name " +
			                                   section +
			                                   " does not exist");
		}
		data.setTextValue(ini.toString());
		return data;
	}

	public IniDataType newComment(IniDataType data, String comment) {
		if (data == null || data.getValue() == null || StringUtils.isBlank(comment)) {return null;}
		Ini ini = data.getValue();
		ini.setComment(comment);
		data.setTextValue(ini.toString());
		return data;
	}

	public TextDataType comment(IniDataType data) throws TypeConversionException {
		if (data == null || data.getValue() == null) {return null;}
		Ini ini = data.getValue();
		String comment = ini.getComment();
		return new TextDataType(comment);
	}

	public IniDataType merge(IniDataType data, String mergeFileOrContent) throws TypeConversionException {
		if (data == null || data.getValue() == null || StringUtils.isBlank(mergeFileOrContent)) {return null;}
		mergeFileOrContent = handleExternal("INI", mergeFileOrContent);
		if (mergeFileOrContent.isEmpty()) { return null; }
		try {
			mergeFileOrContent = mergeFileOrContent.replace("\\", "\\\\");
			Ini mergeIni = new Ini(new StringReader(mergeFileOrContent));
			Ini origIni = data.getValue();
			for (Section section : mergeIni.values()) {
				if (origIni.containsKey(section.getSimpleName())) {
					for (String key : section.keySet()) {
						origIni.get(section.getSimpleName()).put(key, section.get(key));
					}
				} else { origIni.put(section.getSimpleName(), section); }
			}
			data.setTextValue(origIni.toString());
			return data;
		} catch (IOException e) {
			throw new TypeConversionException(data.getName(),
			                                  data.getTextValue(),
			                                  "Error merging into INI: " + e.getMessage(),
			                                  e);
		}
	}

	public IniDataType save(IniDataType data, String filepath) throws IOException {

		if (data == null || data.getValue() == null) {return null;}
		File file = new File(filepath);
		if (!file.isFile() && !file.canRead()) {
			throw new IllegalStateException("Save cannot be performed as given filepath " +
			                                filepath +
			                                " is not a valid file");
		}
		Writer writer = null;
		Ini ini = data.getValue();
		try {
			writer = new FileWriter(file);
			ini.store(writer);
			data.setTextValue(ini.toString());
			return data;

		} finally {
			try {
				if (writer != null) { writer.close(); }
			} catch (IOException e) {
				ConsoleUtils.log("Error closing file resource");
			}
		}
	}

	public IniDataType store(IniDataType data, String var) {
		snapshot(var, data);
		return data;
	}

	@Override
	Map<String, Integer> listSupportedFunctions() {
		return FUNCTION_TO_PARAM_LIST;
	}

	@Override
	Map<String, Method> listSupportedMethods() {
		return FUNCTIONS;
	}
}
