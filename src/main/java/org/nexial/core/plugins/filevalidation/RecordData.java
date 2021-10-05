/*
 * Copyright 2012-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.nexial.core.plugins.filevalidation;

import java.util.Map;

public class RecordData {

    private String startTime;
    private String processTime;
    private String inputFile;
    private String excelFile;
    private boolean hasError;
    private Map<String, Number> mapValues;
    private int totalRecordsProcessed;
    private int totalRecordsSkipped;
    private int totalRecordsPassed;
    private int totalRecordsFailed;
    private int totalRecordsWarning;

    public void calculateTotalPassed(){
        totalRecordsPassed = totalRecordsProcessed - totalRecordsFailed;
    }
    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public int getTotalRecordsProcessed() {
        return totalRecordsProcessed;
    }

    public void setTotalRecordsProcessed(int totalRecordsProcessed) {
        this.totalRecordsProcessed = totalRecordsProcessed;
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

    public Map<String, Number> getMapValues() {
        return mapValues;
    }

    public void setMapValues(Map<String, Number> mapValues) {
        this.mapValues = mapValues;
    }

    public int getTotalRecordsWarning() {
        return totalRecordsWarning;
    }

    public void setTotalRecordsWarning(int totalRecordsWarning) {
        this.totalRecordsWarning = totalRecordsWarning;
    }

    public void printMapFunctionValues() {
        mapValues.entrySet().removeIf(entry -> entry.getKey().contains("#"));
    }

    public int getTotalRecordsSkipped() {
        return totalRecordsSkipped;
    }

    public void setTotalRecordsSkipped(int totalRecordsSkipped) {
        this.totalRecordsSkipped = totalRecordsSkipped;
    }

    public int getTotalRecordsPassed() {
        return totalRecordsPassed;
    }

    public void setTotalRecordsPassed(int totalRecordsPassed) {
        this.totalRecordsPassed = totalRecordsPassed;
    }

    public int getTotalRecordsFailed() {
        return totalRecordsFailed;
    }

    public void setTotalRecordsFailed(int totalRecordsFailed) {
        this.totalRecordsFailed = totalRecordsFailed;
    }

    public int totalRecordsPassed() {
        return totalRecordsProcessed - getTotalRecordsFailed();
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
