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

package org.nexial.core.plugins.filevalidation.validators;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.map.ListOrderedMap;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import org.nexial.core.plugins.filevalidation.FieldBean;
import org.nexial.core.plugins.filevalidation.RecordBean;
import org.nexial.core.plugins.filevalidation.RecordData;
import org.nexial.core.plugins.filevalidation.config.FieldConfig;
import org.nexial.core.plugins.filevalidation.config.MasterConfig;
import org.nexial.core.plugins.filevalidation.config.RecordConfig;

import static org.nexial.core.utils.CheckUtils.requiresNotNull;
import static org.nexial.core.utils.CheckUtils.requiresReadableFile;

public class FixedLengthFileValidator implements MasterFileValidator {

    private List<RecordConfig> configs;

    @Override
    public void setMasterConfig(MasterConfig masterConfig) {
        requiresNotNull(masterConfig, "Failed to create master config");
        this.configs = masterConfig.getConfigs(masterConfig);
    }

    public RecordData parseAndValidate(String targetFilePath) {
        requiresReadableFile(targetFilePath);
        File targetFile = new File(targetFilePath);
        List<String> targetLines;
        try {
            targetLines = FileUtils.readLines(targetFile, "UTF-8");
        } catch (IOException e) {
            throw new IllegalStateException("Unable to locate targetFile " + targetFilePath);
        }
        RecordData recordData = new RecordData();
        Map<Integer, RecordBean> recordSet = new HashMap<>();
        ValidationsExecutor validationsExecutor = new ValidationsExecutor();
        Map<String, Object> mapValues = new ListOrderedMap<>();
        Map<Integer, String> skippedRecords = new ListOrderedMap<>();

        for (int i = 0; i < targetLines.size(); i++) {
            RecordBean recordBean = new RecordBean();
            String targetLine = targetLines.get(i);

            List<FieldBean> fields = new ArrayList<>();
            for (RecordConfig recordConfig : configs) {

                if (recordConfig != null) {
                    List<FieldConfig> configs = recordConfig.getFieldConfigList();
                    String expectedRecordId = recordConfig.getRecordId();
                    String actualRecordId = null;
                    for (FieldConfig config : configs) {
                        if (config.getFieldname().equals(recordConfig.getRecordIdFiled())) {
                            actualRecordId = StringUtils.substring(targetLine, config.getPositionfrom() - 1,
                                                                   config.getPositionto());
                            break;
                        }
                    }

                    // condition to identify the record with config
                    if (expectedRecordId.equals(actualRecordId)) {
                        int expectedLength = configs.get(configs.size() - 1).getPositionto();
                        if (targetLine.length() != expectedLength) {
                            String msg = "Record ID: " +
                                         expectedRecordId +
                                         " - Skipped Validation; Expected record length: " +
                                         expectedLength+". But Actual length found: "+targetLine.length();
                            skippedRecords.put(i, msg);
                            continue;
                        }
                        for (FieldConfig config : configs) {
                            String fieldValue = StringUtils.substring(targetLine,
                                                                      config.getPositionfrom() - 1,
                                                                      config.getPositionto());
                            FieldBean field = new FieldBean(config, fieldValue);
                            field.setRecord(recordBean);
                            fields.add(field);
                        }

                        recordBean.setFields(fields);
                        validationsExecutor.doBasicValidations(recordBean);
                        recordBean.setRecordData(recordData);

                        mapValues = validationsExecutor.collectMapValues(recordConfig, recordBean, mapValues);
                        recordData.setMapValues(mapValues);
                        recordSet.put(i, recordBean);
                        recordData.setRecords(recordSet);
                        break;
                    }

                }

                // todo: (V2) error tolerance to control the parsing of number of lines
            }


        }

        recordData.setSkippedRecords(skippedRecords);
        recordData.setTotalRecordsProcessed(recordSet.entrySet().size());
        validationsExecutor.executeValidations(recordData);
        return recordData;
    }



}