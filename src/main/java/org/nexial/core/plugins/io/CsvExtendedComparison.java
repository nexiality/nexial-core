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

package org.nexial.core.plugins.io;

import java.io.IOException;
import java.io.Serializable;
import java.io.StringReader;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.collections4.map.ListOrderedMap;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import org.nexial.core.IntegrationConfigException;
import org.nexial.core.utils.ConsoleUtils;
import com.univocity.parsers.csv.CsvParser;

import static org.nexial.core.plugins.io.CsvExtendedComparison.ReportFormat.CSV;

class CsvExtendedComparison implements Serializable {
    private static final Map<String, ReportFormat> TYPES = new HashMap<>();

    private String expectedContent;
    private List<String> expectedIdentityColumns;
    private CsvParser expectedParser;
    private List<String[]> expectedRecords;
    private List<String> expectedHeaders;
    private String actualContent;
    private List<String> actualIdentityColumns;
    private CsvParser actualParser;
    private List<String[]> actualRecords;
    private List<String> actualHeaders;
    private Map<String, String> fieldMapping;
    private List<String> ignoreFields;
    private List<String> displayFields;
    private ReportFormat reportFormat;
    private String mismatchedField = "MISMATCHED FIELD";
    private String expectedField = "EXPECTED";
    private String actualField = "ACTUAL";
    private String identSeparator = "^";

    public enum ReportFormat {
        CSV(".csv"),
        CSV_DOUBLE_QUOTES(".csv"),
        HTML(".html"),
        PLAIN(".txt");

        private String ext;

        ReportFormat(String ext) {
            this.ext = ext;
            TYPES.put(this.name(), this);
        }

        public String getExt() { return ext; }

        public static ReportFormat toReportFormat(String name) { return TYPES.get(name); }
    }

    public String getExpectedContent() { return expectedContent; }

    public void setExpectedContent(String expectedContent) { this.expectedContent = expectedContent; }

    public String getActualContent() { return actualContent; }

    public void setActualContent(String actualContent) { this.actualContent = actualContent; }

    public Map<String, String> getFieldMapping() { return fieldMapping; }

    public void setFieldMapping(Map<String, String> fieldMapping) { this.fieldMapping = fieldMapping; }

    public List<String> getIgnoreFields() { return ignoreFields; }

    public void setIgnoreFields(List<String> ignoreFields) { this.ignoreFields = ignoreFields; }

    public List<String> getExpectedIdentityColumns() { return expectedIdentityColumns; }

    public void setExpectedIdentityColumns(List<String> expectedIdentityColumns) {
        this.expectedIdentityColumns = expectedIdentityColumns;
    }

    public List<String> getActualIdentityColumns() { return actualIdentityColumns; }

    public void setActualIdentityColumns(List<String> actualIdentityColumns) {
        this.actualIdentityColumns = actualIdentityColumns;
    }

    public CsvParser getExpectedParser() { return expectedParser;}

    public void setExpectedParser(CsvParser expectedParser) { this.expectedParser = expectedParser;}

    public CsvParser getActualParser() { return actualParser;}

    public void setActualParser(CsvParser actualParser) { this.actualParser = actualParser;}

    public List<String> getDisplayFields() { return displayFields; }

    public void setDisplayFields(List<String> displayFields) { this.displayFields = displayFields; }

    public ReportFormat getReportFormat() { return reportFormat;}

    public void setReportFormat(ReportFormat reportFormat) { this.reportFormat = reportFormat;}

    public String getMismatchedField() { return mismatchedField; }

    public void setMismatchedField(String mismatchedField) {
        if (StringUtils.isNotBlank(mismatchedField)) { this.mismatchedField = mismatchedField; }
    }

    public String getExpectedField() { return expectedField; }

    public void setExpectedField(String expectedField) {
        if (StringUtils.isNotBlank(expectedField)) { this.expectedField = expectedField; }
    }

    public String getActualField() { return actualField; }

    public void setActualField(String actualField) {
        if (StringUtils.isNotBlank(actualField)) { this.actualField = actualField; }
    }

    public String getIdentSeparator() { return identSeparator; }

    public void setIdentSeparator(String identSeparator) { this.identSeparator = identSeparator; }

    public CsvComparisonResult compare() throws IntegrationConfigException, IOException {
        sanityChecks();

        // parse and sort
        parseExpected();
        parseActual();

        // check all the identity and mapping headers
        validateHeaders();

        int expectedLineCount = expectedRecords.size();
        int expectedCurrentLine = 0;
        int actualLineCount = actualRecords.size();
        int actualCurrentLine = 0;

        CsvComparisonResult result = new CsvComparisonResult();
        result.setExpectedHeaders(expectedHeaders);
        result.setActualHeaders(actualHeaders);
        result.setIdentityFields(expectedIdentityColumns);
        result.setDisplayFields(displayFields);
        result.setMismatchedField(mismatchedField);
        result.setExpectedField(expectedField);
        result.setActualField(actualField);
        result.setActualRowCount(actualLineCount);
        result.setExpectedRowCount(expectedLineCount);

        // loop through expected
        ConsoleUtils.log("processing " + expectedLineCount + " rows in expected");
        ConsoleUtils.log("processing " + actualLineCount + " rows in actual");

        while (expectedCurrentLine < expectedLineCount && actualCurrentLine < actualLineCount) {
            String[] expectedRecord = expectedRecords.get(expectedCurrentLine);
            String[] actualRecord = actualRecords.get(actualCurrentLine);

            String expectedIdentity = expectedRecord[0];
            String actualIdentity = actualRecord[0];
            int identityCompared = expectedIdentity.compareTo(actualIdentity);

            // if identity matched
            if (identityCompared == 0) {
                // check all other mapped fields
                fieldMapping.forEach((expectedField, actualField) -> {
                    String expectedValue = expectedRecord[expectedHeaders.indexOf(expectedField) + 1];
                    String actualValue = actualRecord[actualHeaders.indexOf(actualField) + 1];
                    if (!StringUtils.equals(expectedValue, actualValue)) {
                        result.addMismatched(expectedRecord, expectedField, expectedValue, actualValue);
                    }
                });

                expectedCurrentLine++;
                actualCurrentLine++;
                continue;
            }

            // if expected identity > actual identity
            if (identityCompared > 0) {
                result.addMissingExpected(actualRecord);
                actualCurrentLine++;
                continue;
            }

            // if expected identity < actual identity
            if (identityCompared < 0) {
                result.addMissingActual(expectedRecord);
                expectedCurrentLine++;
                continue;
            }
        }

        if (expectedCurrentLine < expectedLineCount) {
            for (int i = expectedCurrentLine; i < expectedLineCount; i++) {
                result.addMissingActual(expectedRecords.get(i));
            }
        }

        if (actualCurrentLine < actualLineCount) {
            for (int i = actualCurrentLine; i < actualLineCount; i++) {
                result.addMissingExpected(actualRecords.get(i));
            }
        }

        return result;
    }

    private void validateHeaders() throws IntegrationConfigException {
        // check mapping
        for (String field : fieldMapping.keySet()) {
            if (!expectedHeaders.contains(field)) {
                throw new IntegrationConfigException("Invalid field name found for expected: " + field);
            }
        }

        for (String field : fieldMapping.values()) {
            if (!actualHeaders.contains(field)) {
                throw new IntegrationConfigException("Invalid field name found for actual: " + field);
            }
        }
    }

    private void parseExpected() throws IOException {
        expectedHeaders = new ArrayList<>();
        expectedRecords = new ArrayList<>();
        expectedRecords = parseContent(expectedParser, expectedContent, expectedHeaders, expectedIdentityColumns);
        if (MapUtils.isEmpty(fieldMapping)) {
            fieldMapping = new ListOrderedMap<>();
            expectedHeaders.forEach(header -> fieldMapping.put(header, header));
        }

        if (CollectionUtils.isNotEmpty(ignoreFields)) {
            ignoreFields.forEach(ignore -> fieldMapping.remove(ignore));
        }
    }

    private void parseActual() throws IOException {
        actualHeaders = new ArrayList<>();
        actualRecords = new ArrayList<>();
        actualRecords = parseContent(actualParser, actualContent, actualHeaders, actualIdentityColumns);
    }

    private List<String[]> parseContent(final CsvParser parser,
                                        final String content,
                                        final List<String> fileHeaders,
                                        final List<String> identityColumns)
        throws IOException {

        List<String[]> records = parser.parseAll(new StringReader(content));
        if (CollectionUtils.isEmpty(records)) { throw new IOException("No record parsed from content"); }

        if (parser.getRecordMetadata() != null) {
            fileHeaders.addAll(Arrays.asList(parser.getRecordMetadata().headers()));
        }
        if (CollectionUtils.isEmpty(fileHeaders)) {
            throw new IOException("Unable to derive column headers from content");
        }

        // check identity fields
        for (String identColumn : identityColumns) {
            if (!fileHeaders.contains(identColumn)) {
                throw new IOException("Expected identity column not found: " + identColumn);
            }
        }

        for (int i = 0; i < records.size(); i++) {
            String[] record = records.get(i);
            String identity = identityColumns.stream()
                                             .map(column -> record[parser.getRecordMetadata().indexOf(column)])
                                             .collect(Collectors.joining(identSeparator));
            records.set(i, ArrayUtils.insert(0, record, identity));
        }

        // position 0 is the identity value
        records.sort(Comparator.comparing(row -> row[0]));
        return records;
    }

    private void sanityChecks() throws IntegrationConfigException {
        if (StringUtils.isBlank(expectedContent)) { throw new IntegrationConfigException("No content for expected"); }

        if (StringUtils.isBlank(actualContent)) { throw new IntegrationConfigException("No content for actual"); }

        if (MapUtils.isEmpty(fieldMapping)) {
            ConsoleUtils.log("No field mapping found; ASSUME EXPECTED AND ACTUAL WITH SAME COLUMNS");
        }

        if (CollectionUtils.isEmpty(expectedIdentityColumns)) {
            throw new IntegrationConfigException("No identity column(s) specified for expected");
        }

        if (CollectionUtils.isEmpty(actualIdentityColumns)) {
            throw new IntegrationConfigException("No identity column(s) specified for actual");
        }

        if (expectedParser == null) { expectedParser = CsvCommand.newCsvParser(null, null, null, true); }

        if (actualParser == null) { actualParser = CsvCommand.newCsvParser(null, null, null, true); }

        if (reportFormat == null) { reportFormat = CSV; }
    }
}
