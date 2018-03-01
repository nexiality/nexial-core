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

package org.nexial.core.plugins.filevalidation;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.nexial.core.plugins.filevalidation.validators.Error;
import org.nexial.core.plugins.filevalidation.validators.ValidationsExecutor.Severity;

public class RecordData {

    private String startTime;
    private String processTime;
    private int totalRecordsProcessed;
    private String inputFile;
    private String excelFile;
    private transient Map<Integer, RecordBean> records;
    private transient Map<Integer, String> skippedRecords;
    private boolean hasError;
    private Map<String, Object> mapValues;
    private List<Error> errors;

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public List<Error> getErrors() {
        return errors;
    }

    public void setErrors(List<Error> errors) {
        this.errors = errors;
    }

    public int getTotalRecordsProcessed() {
        return totalRecordsProcessed;
    }

    public void setTotalRecordsProcessed(int totalRecordsProcessed) {
        this.totalRecordsProcessed = totalRecordsProcessed;
    }

    public Map<Integer, String> getSkippedRecords() {
        return skippedRecords;
    }

    public void setSkippedRecords(Map<Integer, String> skippedRecords) {
        this.skippedRecords = skippedRecords;
    }

    public String getInputFile() {
        return inputFile;
    }

    public void setInputFile(String inputFile) {
        this.inputFile = inputFile;
    }

    public String getExcelFile() {
        return excelFile;
    }

    public void setExcelFile(String excelFile) {
        this.excelFile = excelFile;
    }

    public String getProcessTime() {
        return processTime;
    }

    public void setProcessTime(String processTime) {
        this.processTime = processTime;
    }

    public boolean isHasError() {
        return hasError;
    }

    public void setHasError(boolean hasError) {
        this.hasError = hasError;
    }

    public Map<String, Object> getMapValues() {
        return mapValues;
    }

    public void setMapValues(Map<String, Object> mapValues) {
        this.mapValues = mapValues;
    }

    public Map<Integer, RecordBean> getRecords() {
        return records;
    }

    public void setRecords(Map<Integer, RecordBean> records) {
        this.records = records;
    }

    public void printMapFunctionValues() {

        mapValues.entrySet().removeIf(entry -> entry.getKey().contains("#"));

    }

    public int totalRecordsFailed() {
        Set<String> set = new HashSet<>();
        for (Error error : errors) {
            if (error.getSeverity().equals(Severity.ERROR.toString())) {
                set.add(error.getRecordLine());
            }

        }
        return set.size();
    }

    public int totalRecordsPassed() {
        return totalRecordsProcessed - totalRecordsFailed();
    }

    public int totalWarnings() {
        Set<String> set = new HashSet<>();
        for (Error error : errors) {
            if (error.getSeverity().equals(Severity.WARNING.toString())) {
                set.add(error.getRecordLine());
            }

        }
        return set.size();
    }

    @Override
    public String toString() {
        return "RecordData{processTime='" + processTime + '\'' +
               ", totalRecordsProcessed=" + totalRecordsProcessed +
               ", inputFile='" + inputFile + '\'' +
               ", excelFile='" + excelFile + '\'' +
               ", hasError=" + hasError +
               '}';
    }
}
