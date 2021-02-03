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

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.nexial.core.plugins.filevalidation.config.*;
import org.nexial.core.plugins.filevalidation.config.JsonMappingConfig.FilefooterrecordBean;
import org.nexial.core.plugins.filevalidation.config.JsonMappingConfig.FileheaderrecordBean;
import org.nexial.core.plugins.filevalidation.config.JsonMappingConfig.FilesectionsBean;
import org.nexial.core.plugins.filevalidation.config.JsonMappingConfig.FilesectionsBean.BodyrecordsBean;
import org.nexial.core.plugins.filevalidation.config.JsonMappingConfig.FilesectionsBean.FooterrecordBean;
import org.nexial.core.plugins.filevalidation.config.JsonMappingConfig.FilesectionsBean.HeaderrecordBean;
import org.nexial.core.plugins.filevalidation.config.JsonMappingConfig.RecordspecBean;
import org.nexial.core.utils.CheckUtils;

import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.nexial.core.NexialConst.GSON;

public class JsonSpecFileParser extends RecordSpecFileParser {

    private final String jsonConfig;
    private int previousFieldLength;

    JsonSpecFileParser(String jsonConfig) {
        this.jsonConfig = jsonConfig;
        previousFieldLength = 0;
    }

    public MasterConfig parseMappingFile() {
        CheckUtils.requiresReadableFile(jsonConfig);
        try {
            JsonElement json = JsonParser.parseReader(new FileReader(jsonConfig));

            JsonMappingConfig jsonMappingConfig = GSON.fromJson(json, JsonMappingConfig.class);
            return parseJsonDescriptor(jsonMappingConfig);
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to parse the given json config file " + jsonConfig);
        }
    }

    private MasterConfig parseJsonDescriptor(JsonMappingConfig jsonMappingConfig) {

        MasterConfig masterConfig = new MasterConfig();
        RecordConfig fileHeaderConfig = parsefileHeaderFields(jsonMappingConfig.getFileheaderrecord());
        masterConfig.setFileHeader(fileHeaderConfig);
        masterConfig.setSectionConfigs(parseFilesections(jsonMappingConfig.getFilesections()));
        RecordConfig fileFooterConfig = parseFileFooterFields(jsonMappingConfig.getFilefooterrecord());
        masterConfig.setFileFooter(fileFooterConfig);
        return masterConfig;
    }

    private RecordConfig parsefileHeaderFields(FileheaderrecordBean fileheaderrecordBean) {

        if (fileheaderrecordBean == null) { return null; }
        List<FieldConfig> fieldConfigs;
        fieldConfigs = createFieldConfigs(fileheaderrecordBean.getRecordspec(),
                                          fileheaderrecordBean.getValidations(),
                                          fileheaderrecordBean.getMapfunctions());

        return new RecordConfig.RecordConfigBuilder()
                   .fieldConfigList(fieldConfigs)
                   .mapFunctionConfigs(mapFunctionsToRecord(fileheaderrecordBean.getMapfunctions()))
                   .fieldSeparator(fileheaderrecordBean.getFieldSeparator())
                   .recordIdField(fileheaderrecordBean.getRecordidfield())
                   .recordId(fileheaderrecordBean.getRecordid())
                   .build();

    }

    private List<SectionConfig> parseFilesections(List<FilesectionsBean> filesectionsBeans) {

        List<SectionConfig> sectionConfigs = new ArrayList<>();
        for (FilesectionsBean filesectionsBean : filesectionsBeans) {
            if (filesectionsBean != null) {
                SectionConfig sectionConfig = new SectionConfig();
                sectionConfig.setHeaderConfig(createHeaderRecordConfig(filesectionsBean.getHeaderrecord()));
                List<RecordConfig> bodyConfigs = new ArrayList<>();
                for (int j = 0; j < filesectionsBean.getBodyrecords().size(); j++) {

                    bodyConfigs.add(createBodyRecordConfig(filesectionsBean.getBodyrecords().get(j)));
                }
                sectionConfig.setBodyConfigs(bodyConfigs);
                sectionConfig.setFooterConfig(createFooterRecordConfig(filesectionsBean.getFooterrecord()));
                sectionConfigs.add(sectionConfig);
            }
        }
        return sectionConfigs;
    }

    private RecordConfig parseFileFooterFields(FilefooterrecordBean filefooterrecordBean) {

        if (filefooterrecordBean == null) { return null; }
        List<FieldConfig> fieldConfigs;
        fieldConfigs = createFieldConfigs(filefooterrecordBean.getRecordspec(),
                                          filefooterrecordBean.getValidations(),
                                          filefooterrecordBean.getMapfunctions());

        return new RecordConfig.RecordConfigBuilder()
                   .fieldConfigList(fieldConfigs)
                   .mapFunctionConfigs(mapFunctionsToRecord(filefooterrecordBean.getMapfunctions()))
                   .fieldSeparator(filefooterrecordBean.getFieldSeparator())
                   .recordIdField(filefooterrecordBean.getRecordidfield())
                   .recordId(filefooterrecordBean.getRecordid())
                   .build();

    }

    private RecordConfig createHeaderRecordConfig(HeaderrecordBean headerrecordBean) {
        if (headerrecordBean == null) { return null; }
        List<FieldConfig> fieldConfigs = createFieldConfigs(headerrecordBean.getRecordspec(),
                                                            headerrecordBean.getValidations(),
                                                            headerrecordBean.getMapfunctions());

        return new RecordConfig.RecordConfigBuilder()
                   .fieldConfigList(fieldConfigs)
                   .mapFunctionConfigs(mapFunctionsToRecord(headerrecordBean.getMapfunctions()))
                   .fieldSeparator(headerrecordBean.getFieldSeparator())
                   .recordIdField(headerrecordBean.getRecordidfield())
                   .recordId(headerrecordBean.getRecordid())
                   .build();
    }

    private RecordConfig createBodyRecordConfig(BodyrecordsBean bodyrecordsBean) {

        if (bodyrecordsBean == null) { return null; }
        List<FieldConfig> fieldConfigs = createFieldConfigs(bodyrecordsBean.getRecordspec(),
                                                            bodyrecordsBean.getValidations(),
                                                            bodyrecordsBean.getMapfunctions());

        return new RecordConfig.RecordConfigBuilder()
                   .fieldConfigList(fieldConfigs)
                   .mapFunctionConfigs(mapFunctionsToRecord(bodyrecordsBean.getMapfunctions()))
                   .fieldSeparator(bodyrecordsBean.getFieldSeparator())
                   .recordIdField(bodyrecordsBean.getRecordidfield())
                   .recordId(bodyrecordsBean.getRecordid())
                   .build();
    }

    private RecordConfig createFooterRecordConfig(FooterrecordBean footerrecordBean) {

        if (footerrecordBean == null) { return null; }
        List<FieldConfig> fieldConfigs = createFieldConfigs(footerrecordBean.getRecordspec(),
                                                            footerrecordBean.getValidations(),
                                                            footerrecordBean.getMapfunctions());

        return new RecordConfig.RecordConfigBuilder()
                   .fieldConfigList(fieldConfigs)
                   .mapFunctionConfigs(mapFunctionsToRecord(footerrecordBean.getMapfunctions()))
                   .fieldSeparator(footerrecordBean.getFieldSeparator())
                   .recordIdField(footerrecordBean.getRecordidfield())
                   .recordId(footerrecordBean.getRecordid()).build();
    }

    private List<FieldConfig> createFieldConfigs(List<RecordspecBean> recordSpecs,
                                                 List<ValidationsBean> validations,
                                                 List<MapfunctionsBean> mapFunctions) {
        List<FieldConfig> fieldConfigs = new ArrayList<>();
        if (recordSpecs != null) {

            previousFieldLength = 0;
            for (RecordspecBean recordspecBean : recordSpecs) {
                FieldConfig config = putFiledConfigs(recordspecBean);
                fieldConfigs.add(config);
                parseValidationConfigs(config, validations);
                mapFunctionsToField(config, mapFunctions);
            }
        }
        return fieldConfigs;
    }

    private FieldConfig putFiledConfigs(RecordspecBean recordspecBean) {
        FieldConfig fieldConfig = new FieldConfig();

        fieldConfig.setFieldname(recordspecBean.getFieldname());
        fieldConfig.setDatatype(recordspecBean.getDatatype());
        fieldConfig.setFieldlength(recordspecBean.getFieldlength());
        fieldConfig.setAlignment(recordspecBean.getAlignment());
        if (recordspecBean.getPositionfrom() != 0) {
            fieldConfig.setPositionfrom(recordspecBean.getPositionfrom());
        } else {
            if (previousFieldLength == 0) {
                fieldConfig.setPositionfrom(1);
            }
            fieldConfig.setPositionfrom(previousFieldLength + 1);
        }

        if (recordspecBean.getPositionto() != 0) {
            fieldConfig.setPositionto(recordspecBean.getPositionto());
        } else {
            if (previousFieldLength == 0) {
                fieldConfig.setPositionto(fieldConfig.getFieldlength());
            }
            fieldConfig.setPositionto(previousFieldLength + fieldConfig.getFieldlength());
            previousFieldLength = previousFieldLength + fieldConfig.getFieldlength();
        }
        return fieldConfig;
    }

}
