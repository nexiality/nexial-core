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

package org.nexial.core.plugins.filevalidation.parser;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;

import org.nexial.core.plugins.filevalidation.validators.DelimitedFileValidator;
import org.nexial.core.plugins.filevalidation.validators.FixedLengthFileValidator;
import org.nexial.core.plugins.filevalidation.validators.MasterFileValidator;
import org.nexial.core.utils.CheckUtils;
import org.nexial.core.utils.JSONPath;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import static org.nexial.core.utils.CheckUtils.requiresNotBlank;
import static org.nexial.core.utils.CheckUtils.requiresNotNull;

public class FileParserFactory {

    private static final String FIXED_LENGTH = "FIXED_LENGTH";
    private static final String DELIMITER_SEPARATED = "DELIMITER_SEPARATED";
    private static final String MAPPING_FILE_TYPE = "mapping-config.file-type";
    private static final String MAPPING_SPEC_TYPE = "mapping-config.spec-type";

    public static MasterFileValidator getFileParser(String jsonConfig, @Nullable String mappingFile) {
        requiresNotBlank(jsonConfig, "Invalid config json path", jsonConfig);
        JsonElement json;
        try {
            json = new JsonParser().parse(new FileReader(new File(jsonConfig)));
        } catch (FileNotFoundException | JsonParseException e) {
            throw new IllegalArgumentException(" JSON path specified '" + jsonConfig + "' is not found or invalid");
        }

        MasterFileValidator fileParser = null;

        String fileType = JSONPath.find(new JSONObject(json.toString()), MAPPING_FILE_TYPE);

        if (!StringUtils.equalsAnyIgnoreCase(fileType, FIXED_LENGTH, DELIMITER_SEPARATED)) {
            CheckUtils.fail("Invalid file-type '" + fileType + "'. It must be one of [" + FIXED_LENGTH + "," +
                            DELIMITER_SEPARATED + "]");
        }
        if (StringUtils.equals(fileType, FIXED_LENGTH)) {
            fileParser = new FixedLengthFileValidator();
        }

        if (StringUtils.equals(fileType, DELIMITER_SEPARATED)) {
            fileParser = new DelimitedFileValidator();
        }

        if (fileParser != null) {

            requiresNotNull(fileParser, "Failed to provide file parser");
            String specType = JSONPath.find(new JSONObject(json.toString()), MAPPING_SPEC_TYPE);
            if (!StringUtils.equalsAnyIgnoreCase(specType, "JSON", "EXCEL")) {
                requiresNotBlank(mappingFile, "Invalid mapping file path", mappingFile);
                CheckUtils.fail("Invalid spec-type '" + specType + "'. It must be one among [JSON, EXCEL]");
            }
            if (StringUtils.equalsIgnoreCase(specType, "EXCEL")) {
                fileParser.setMasterConfig(new ExcelSpecFileParser(jsonConfig, mappingFile).parseMappingFile());
            }

            if (StringUtils.equalsIgnoreCase(specType, "JSON")) {
                fileParser.setMasterConfig(new JsonSpecFileParser(jsonConfig).parseMappingFile());
            }
        }
        return fileParser;
    }


}
