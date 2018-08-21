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
import java.io.IOException;
import javax.annotation.Nullable;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.nexial.core.NexialConst;
import org.nexial.core.plugins.filevalidation.validators.DelimitedFileValidator;
import org.nexial.core.plugins.filevalidation.validators.FixedLengthFileValidator;
import org.nexial.core.plugins.filevalidation.validators.MasterFileValidator;
import org.nexial.core.utils.CheckUtils;
import org.nexial.core.utils.JSONPath;
import org.nexial.core.utils.JsonUtils;

import static org.nexial.core.utils.CheckUtils.requiresNotBlank;
import static org.nexial.core.utils.CheckUtils.requiresReadableFile;

public class FileParserFactory {

    private static final String FIXED_LENGTH = "FIXED_LENGTH";
    private static final String DELIMITER_SEPARATED = "DELIMITER_SEPARATED";
    private static final String MAPPING_FILE_TYPE = "mapping-config.file-type";
    private static final String MAPPING_SPEC_TYPE = "mapping-config.spec-type";

    public static MasterFileValidator getFileParser(String jsonConfig, @Nullable String mappingFile) {
        requiresReadableFile(jsonConfig);
        JSONObject json;

        try {
            json = JsonUtils.toJSONObject(FileUtils
                                              .readFileToString(new File(jsonConfig), NexialConst.DEF_FILE_ENCODING));
        } catch (IOException e) {
            throw new IllegalArgumentException(" JSON path specified '" + jsonConfig + "' is not found or invalid");
        }

        String fileType = JSONPath.find(json, MAPPING_FILE_TYPE);

        if (!StringUtils.equalsAnyIgnoreCase(fileType, FIXED_LENGTH, DELIMITER_SEPARATED)) {
            CheckUtils.fail("Invalid file-type '" + fileType + "'. It must be one of [" + FIXED_LENGTH + "," +
                            DELIMITER_SEPARATED + "]");
        }
        MasterFileValidator fileParser = null;
        if (StringUtils.equals(fileType, FIXED_LENGTH)) {
            fileParser = new FixedLengthFileValidator();
        }

        if (StringUtils.equals(fileType, DELIMITER_SEPARATED)) {
            fileParser = new DelimitedFileValidator();
        }

        if (fileParser != null) {

            String specType = JSONPath.find(json, MAPPING_SPEC_TYPE);
            if (!StringUtils.equalsAnyIgnoreCase(specType, "JSON", "EXCEL")) {

                CheckUtils.fail("Invalid spec-type '" + specType + "'. It must be one among [JSON, EXCEL]");
            }
            if (StringUtils.equalsIgnoreCase(specType, "EXCEL")) {
                requiresNotBlank(mappingFile,
                                 "profile not set any value with .mappingExcel or invalid mapping file path",
                                 mappingFile);
                fileParser.setMasterConfig(new ExcelSpecFileParser(jsonConfig, mappingFile).parseMappingFile());
            }

            if (StringUtils.equalsIgnoreCase(specType, "JSON")) {
                fileParser.setMasterConfig(new JsonSpecFileParser(jsonConfig).parseMappingFile());
            }
        }
        return fileParser;
    }


}
