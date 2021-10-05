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

package org.nexial.core.plugins.io;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.validation.constraints.NotNull;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.plugins.io.CsvExtendedComparison.ReportFormat;

import static org.nexial.core.NexialConst.NL;
import static org.nexial.core.plugins.io.CsvExtendedComparison.ReportFormat.*;

public class CsvComparisonResult {
    private List<String> expectedHeaders = new ArrayList<>();
    private List<String> actualHeaders = new ArrayList<>();
    private List<String> identityFields = new ArrayList<>();
    private List<String> displayFields = new ArrayList<>();
    private String mismatchedField = "MISMATCHED FIELD";
    private String expectedField = "EXPECTED";
    private String actualField = "ACTUAL";
    private List<List<String>> discrepancies = new ArrayList<>();
    private Set<String> failedIdentities = new HashSet<>();
    private int expectedRowCount;
    private int actualRowCount;

    public List<String> getExpectedHeaders() { return expectedHeaders; }

    public void setExpectedHeaders(List<String> expectedHeaders) { this.expectedHeaders = expectedHeaders; }

    public List<String> getActualHeaders() { return actualHeaders; }

    public void setActualHeaders(List<String> actualHeaders) { this.actualHeaders = actualHeaders; }

    public List<String> getIdentityFields() { return identityFields; }

    public void setIdentityFields(List<String> identityFields) { this.identityFields = identityFields; }

    public List<String> getDisplayFields() { return displayFields; }

    public void setDisplayFields(List<String> displayFields) { this.displayFields = displayFields; }

    public int getExpectedRowCount() { return expectedRowCount; }

    public void setExpectedRowCount(int expectedRowCount) { this.expectedRowCount = expectedRowCount; }

    public int getActualRowCount() { return actualRowCount; }

    public void setActualRowCount(int actualRowCount) { this.actualRowCount = actualRowCount; }

    public String getMismatchedField() { return mismatchedField; }

    public void setMismatchedField(String mismatchedField) { this.mismatchedField = mismatchedField; }

    public String getExpectedField() { return expectedField; }

    public void setExpectedField(String expectedField) { this.expectedField = expectedField; }

    public String getActualField() { return actualField; }

    public void setActualField(String actualField) { this.actualField = actualField; }

    public List<List<String>> getDiscrepancies() { return discrepancies; }

    public Set<String> getFailedIdentities() { return failedIdentities; }

    public int getFailCount() { return CollectionUtils.size(failedIdentities); }

    public double getSuccessRate() {
        if (failedIdentities.isEmpty()) { return 1; }
        if (actualRowCount <= 0) { return 0; }
        return (double) (expectedRowCount - failedIdentities.size()) / (double) expectedRowCount;
    }

    public void addMismatched(String[] record, String field, String expected, String actual) {
        discrepancies.add(newDiscrepancy(record, field, expected, actual));
    }

    public void addMissingExpected(String[] actual) {
        discrepancies.add(newDiscrepancy(actual, "RECORD MISSING in '" + expectedField + "'", "", actual[0]));
    }

    public void addMissingActual(String[] expected) {
        discrepancies.add(newDiscrepancy(expected, "RECORD MISSING in '" + actualField + "'", expected[0], ""));
    }

    @Override
    public String toString() {
        boolean readyForReport = CollectionUtils.isNotEmpty(discrepancies) &&
                                 CollectionUtils.isNotEmpty(failedIdentities);
        String readyVerbiage = readyForReport ? "READY" : "none";

        return "expectedHeaders=" + expectedHeaders + NL +
               "actualHeaders=" + actualHeaders + NL +
               "displayFields=" + displayFields + NL +
               "identityFields=" + identityFields + NL +
               "failedIdentities=<" + CollectionUtils.size(failedIdentities) + " found>" + NL +
               "failCount=" + getFailCount() + NL +
               "expectedRowCount=" + expectedRowCount + NL +
               "actualRowCount=" + actualRowCount + NL +
               "successRate=" + getSuccessRate() + NL +
               "reportAsHTML=<" + readyVerbiage + ">" + NL +
               "reportAsText=<" + readyVerbiage + ">" + NL +
               "reportAsCSV=<" + readyVerbiage + ">" + NL +
               "reportAsCSVWithQuotes=<" + readyVerbiage + ">" + NL;
    }

    public String reportAsHTML() { return externalizeReport(HTML); }

    public String reportAsText() { return externalizeReport(PLAIN); }

    public String reportAsCSV() { return externalizeReport(CSV); }

    public String reportAsCSVWithQuotes() { return externalizeReport(CSV_DOUBLE_QUOTES); }

    protected String externalizeReport(ReportFormat format) {
        switch (format) {
            case CSV:
                return toCSV(false);
            case CSV_DOUBLE_QUOTES:
                return toCSV(true);
            case HTML:
                return toHTML();
            case PLAIN:
                return toPlain();
        }
        return null;
    }

    protected String toPlain() {
        return TextUtils.createAsciiTable(resolveDisplayableHeaders(), discrepancies, List::get);
    }

    protected String toCSV(boolean wrapValueWithDoubleQuote) {
        return TextUtils.createCsv(resolveDisplayableHeaders(),
                                   discrepancies,
                                   "\r\n",
                                   ",",
                                   wrapValueWithDoubleQuote ? "\"" : "");
    }

    protected String toHTML() {
        return TextUtils.createHtmlTable(resolveDisplayableHeaders(), discrepancies, "compare-extended-result-table");
    }

    @NotNull
    private List<String> resolveDisplayableHeaders() {
        List<String> headers = new ArrayList<>(displayFields);
        headers.add(mismatchedField);
        headers.add(expectedField);
        headers.add(actualField);
        return headers;
    }

    private List<String> newDiscrepancy(String[] record, String field, String expected, String actual) {
        // first field is ALWAYS the identity
        failedIdentities.add(record[0]);

        // if 'expected' is empty and the 'actual' is really the identity field of the record, then this means we
        // are reporting on missing 'EXPECTED' record ==> we need to use 'ACTUAL' header in this case
        List<String> headers = StringUtils.isEmpty(expected) && StringUtils.equals(record[0], actual) ?
                               actualHeaders : expectedHeaders;

        List<String> discrepancy = new ArrayList<>();
        displayFields.forEach(display -> {
            int displayIndex = headers.indexOf(display);
            // not found -> display empty string
            discrepancy.add(displayIndex == -1 ? "" : record.length > displayIndex + 1 ? record[displayIndex + 1] : "");
        });
        discrepancy.add(field);
        discrepancy.add(expected);
        discrepancy.add(actual);
        return discrepancy;
    }
}
