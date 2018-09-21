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
import java.io.Writer;
import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.Map;
import java.util.Properties;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.utils.EnvUtils;
import org.nexial.commons.utils.FileUtil;
import org.nexial.core.utils.ConsoleUtils;

import static org.nexial.core.NexialConst.DEF_FILE_ENCODING;

/**
 * Created by nv092106 on 7/16/2017.
 */
public class ConfigTransformer<T extends ConfigDataType> extends Transformer {
    private static final Map<String, Integer> FUNCTION_TO_PARAM_LIST = discoverFunctions(ConfigTransformer.class);
    private static final Map<String, Method> FUNCTIONS =
        toFunctionMap(FUNCTION_TO_PARAM_LIST, ConfigTransformer.class, ConfigDataType.class);

    public TextDataType text(T data) { return super.text(data); }

    public ListDataType keys(T data) throws TypeConversionException {
        if (data == null || data.getValue() == null) { return null; }

        Properties value = data.getValue();
        try {
            return new ListDataType(value.keySet().toString());
        } catch (TypeConversionException e) {
            throw new TypeConversionException(data.getName(), data.getTextValue(),
                                              "Error converting to ListDataType: " + e.getMessage(), e);
        }
    }

    public TextDataType value(T data, String key) throws TypeConversionException {
        if (data == null || data.getValue() == null || StringUtils.isBlank(key)) { return null; }

        try {
            return new TextDataType(data.getValue().getProperty(key));
        } catch (TypeConversionException e) {
            throw new TypeConversionException(data.getName(), data.getTextValue(),
                                              "Error converting to TextDataType: " + e.getMessage(), e);
        }
    }

    public T set(T data, String key, String value) {
        if (data == null || data.getValue() == null || StringUtils.isBlank(key)) { return null; }

        data.getValue().put(key, value);
        data.reset();
        return data;
    }

    public T remove(T data, String key) {
        if (data == null || data.getValue() == null || StringUtils.isBlank(key)) { return null; }

        data.getValue().remove(key);
        data.reset();
        return data;
    }

    public T save(T data, String filepath) {
        if (data == null || data.getValue() == null || StringUtils.isBlank(filepath)) { return null; }

        File file = new File(filepath);
        if (!file.isFile() && !file.canRead()) {
            throw new IllegalArgumentException("Save cannot be performed as given file " + filepath +
                                               " is not a valid file");
        }

        Writer writer = null;
        Properties properties = data.getValue();
        try {
            writer = new FileWriter(file);
            properties.store(writer, "Saving test data");
        } catch (IOException e) {
            throw new IllegalArgumentException("Error while saving data to file: " + filepath);
        } finally {
            try {
                if (writer != null) { writer.close(); }
            } catch (IOException e) {
                ConsoleUtils.log("Error closing file resource");
            }
        }

        try {
            // Properties.store() uses OS-specific EOF, now let's change it back to what the input file has instead
            String saved = FileUtils.readFileToString(file, DEF_FILE_ENCODING);
            if (StringUtils.equals(data.getEol(), "\r\n")) {
                saved = EnvUtils.enforceWindowsEOL(saved);
            } else {
                saved = EnvUtils.enforceUnixEOL(saved);
            }

            FileUtils.writeStringToFile(file, saved, "UTF-8");
            data.setTextValue(saved);
            data.init();
            return data;
        } catch (TypeConversionException | IOException e) {
            ConsoleUtils.error("Unable to save CONFIG data to '" + filepath + "': " + e.getMessage());
            throw new IllegalArgumentException("Unable to save CONFIG data to '" + filepath + "'", e);
        }
    }

    public T store(T data, String var) {
        snapshot(var, data);
        return data;
    }

    public T descending(T data) throws TypeConversionException {
        return resortKeys(data, new TreeSet<>(Comparator.reverseOrder()));
    }

    public T ascending(T data) throws TypeConversionException { return resortKeys(data, new TreeSet<>()); }

    @Override
    Map<String, Integer> listSupportedFunctions() { return FUNCTION_TO_PARAM_LIST; }

    @Override
    Map<String, Method> listSupportedMethods() { return FUNCTIONS; }

    @Override
    protected void saveContentAsAppend(ExpressionDataType data, File target) throws IOException {
        if (FileUtil.isFileReadable(target, 1) && data instanceof ConfigDataType) {
            String currentContent = FileUtils.readFileToString(target, DEF_FILE_ENCODING);
            if (!StringUtils.endsWith(currentContent, "\n")) {
                FileUtils.writeStringToFile(target, ((ConfigDataType) data).getEol(), DEF_FILE_ENCODING, true);
            }
        }
        super.saveContentAsAppend(data, target);
    }

    protected T resortKeys(T data, SortedSet<String> sortedKeys) throws TypeConversionException {
        if (data == null || data.getValue() == null || data.getValue().isEmpty()) {
            return data;
        }

        Properties props = data.getValue();
        sortedKeys.addAll(props.stringPropertyNames());

        String eol = data.getEol();
        ConsoleUtils.log("resort keys using " + (StringUtils.equals(eol, "\r\n") ? "CRLF" : "LF") + " as end-of-line");

        StringBuilder text = new StringBuilder();
        sortedKeys.forEach(key -> text.append(key).append("=").append(props.getProperty(key)).append(eol));

        data.setTextValue(text.toString());
        data.init();
        return data;
    }
}
