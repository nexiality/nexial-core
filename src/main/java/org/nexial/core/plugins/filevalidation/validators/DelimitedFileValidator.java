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

import java.io.BufferedOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.map.ListOrderedMap;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.apache.commons.lang3.StringUtils;
import org.nexial.core.plugins.filevalidation.FieldBean;
import org.nexial.core.plugins.filevalidation.RecordBean;
import org.nexial.core.plugins.filevalidation.RecordData;
import org.nexial.core.plugins.filevalidation.config.FieldConfig;
import org.nexial.core.plugins.filevalidation.config.MasterConfig;
import org.nexial.core.plugins.filevalidation.config.RecordConfig;
import org.nexial.core.utils.ConsoleUtils;

import static org.nexial.core.utils.CheckUtils.requiresNotNull;
import static org.nexial.core.utils.CheckUtils.requiresReadableFile;

public class DelimitedFileValidator implements MasterFileValidator {
    private List<RecordConfig> configs;

    @Override
    public void setMasterConfig(MasterConfig masterConfig) {
        requiresNotNull(masterConfig, "Failed to create master config.");
        this.configs = masterConfig.getConfigs(masterConfig);
    }

    @Override
    public RecordData parseAndValidate(String targetFilePath) {
        requiresReadableFile(targetFilePath);
        File targetFile = new File(targetFilePath);
        RecordData recordData = new RecordData();
        ValidationsExecutor validationsExecutor = new ValidationsExecutor();
        File csvOutputFile = validationsExecutor.resolveCsvOutputFile();
        Map<String, Number> mapValues = new ListOrderedMap<>();
        Map<String, Object> tempDupValues = validationsExecutor.moveDupValuesFromContext(configs);
        int i = 0;
        int processedLines = 0;
        try (BufferedOutputStream outputStream = new BufferedOutputStream(FileUtils.openOutputStream(csvOutputFile))) {
            LineIterator iterator = FileUtils.lineIterator(targetFile, "UTF-8");
            while (iterator.hasNext()) {
                String targetLine = iterator.next();
                List<FieldBean> fields = new ArrayList<>();
                for (RecordConfig recordConfig : configs) {
                    if (recordConfig == null || !recordConfig.isValid()) { continue; }
                    List<FieldConfig> configs = recordConfig.getFieldConfigList();
                    String[] fieldValues =
                        StringUtils.splitByWholeSeparatorPreserveAllTokens(targetLine,
                                                                           recordConfig.getFieldSeparator());

                    // find recordId position to get actual recordID value
                    String expectedRecordIdValue = recordConfig.getRecordId();
                    int recordIdPosition = 0;
                    for (int n = 0; n < configs.size(); n++) {
                        if (configs.get(n).getFieldname().equals(recordConfig.getRecordIdField())) {
                            recordIdPosition = n;
                            break;
                        }
                    }

                    // condition to identify the record with config
                    if (fieldValues[recordIdPosition].equals(expectedRecordIdValue)) {
                        RecordBean recordBean = new RecordBean();
                        recordBean.setRecordNumber(i);
                        int expectedRecords = configs.size() + 1;
                        if (fieldValues.length != configs.size() + 1) {
                            int totalSkipped = recordData.getTotalRecordsSkipped();
                            String msg = "Skipped:" + i + "," + expectedRecordIdValue + ",Expected records "
                                         + expectedRecords + ". But Actual records found "
                                         + fieldValues.length;
                            ConsoleUtils.log(msg);
                            recordBean.setSkippedMsg(msg);
                            recordData.setTotalRecordsSkipped(++totalSkipped);
                            validationsExecutor.writeReportToFile(outputStream, recordBean);
                            continue;
                        }
                        ConsoleUtils.log("validating line number: " + i + " Record ID: " + expectedRecordIdValue);
                        processedLines++;
                        for (int j = 0; j < configs.size(); j++) {
                            FieldBean field = new FieldBean(configs.get(j), fieldValues[j]);
                            field.setRecord(recordBean);
                            fields.add(field);
                        }
                        recordBean.setFields(fields);
                        validationsExecutor.doBasicValidations(recordBean);
                        recordBean.setRecordData(recordData);
                        mapValues = validationsExecutor.collectMapValues(recordConfig, recordBean, mapValues);
                        recordData.setMapValues(mapValues);
                        validationsExecutor.executeValidations(outputStream, recordBean);
                        break;
                    }
                }
                i++;
            }
        } catch (Exception e) {
            ConsoleUtils.log("File validation failed. " + e.getMessage());
        } finally {
            validationsExecutor.restoreValuesToContext(tempDupValues);
        }
        recordData.printMapFunctionValues();
        recordData.setTotalRecordsProcessed(processedLines);
        recordData.calculateTotalPassed();
        return recordData;
    }
}