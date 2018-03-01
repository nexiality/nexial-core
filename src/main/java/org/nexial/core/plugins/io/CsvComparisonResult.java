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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import org.nexial.commons.utils.TextUtils;
import org.nexial.core.plugins.io.CsvExtendedComparison.ReportFormat;

import static org.nexial.core.plugins.io.CsvExtendedComparison.ReportFormat.*;
import static java.lang.System.lineSeparator;

public class CsvComparisonResult {
    private List<String> expectedHeaders;
    private List<String> actualHeaders;
    private List<String> identityFields;
    private List<String> displayFields;
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

    public void addMissingExpected(String[] actualRecord) {
        discrepancies.add(
            newDiscrepancy(actualRecord, "RECORD MISSING in '" + expectedField + "'", "", actualRecord[0]));
    }

    public void addMissingActual(String[] expectedRecord) {
        discrepancies.add(
            newDiscrepancy(expectedRecord, "RECORD MISSING in '" + actualField + "'", expectedRecord[0], ""));
    }

    @Override
    public String toString() {
        boolean readyForReport = CollectionUtils.isNotEmpty(discrepancies) &&
                                 CollectionUtils.isNotEmpty(failedIdentities);
        String readyVerbiage = readyForReport ? "READY" : "none";

        return "expectedHeaders=" + expectedHeaders + "\n" +
               "actualHeaders=" + actualHeaders + "\n" +
               "displayFields=" + displayFields + "\n" +
               "identityFields=" + identityFields + "\n" +
               "failedIdentities=<" + CollectionUtils.size(failedIdentities) + " found>\n" +
               "failCount=" + getFailCount() + "\n" +
               "expectedRowCount=" + expectedRowCount + "\n" +
               "actualRowCount=" + actualRowCount + "\n" +
               "successRate=" + getSuccessRate() + "\n" +
               "reportAsHTML=<" + readyVerbiage + ">\n" +
               "reportAsText=<" + readyVerbiage + ">\n" +
               "reportAsCSV=<" + readyVerbiage + ">\n" +
               "reportAsCSVWithQuotes=<" + readyVerbiage + ">\n";
    }

    public String reportAsHTML() { return externalizeReport(HTML); }

    public String reportAsText() { return externalizeReport(PLAIN); }

    public String reportAsCSV() { return externalizeReport(CSV); }

    public String reportAsCSVWithQuotes() { return externalizeReport(CSV_DOUBLE_QUOTES); }

    protected String externalizeReport(ReportFormat format) {

        String content = null;

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

        return content;
    }

    protected String toPlain() {
        List<String> headers = new ArrayList<>(displayFields);
        headers.add(mismatchedField);
        headers.add(expectedField);
        headers.add(actualField);
        return TextUtils.createAsciiTable(headers, discrepancies, List::get);
    }

    protected String toCSV(boolean wrapValueWithDoubleQuote) {
        StringBuilder buffer = new StringBuilder();

        // header
        buffer.append(TextUtils.toString(displayFields, ",")).append(",")
              .append(mismatchedField).append(",")
              .append(expectedField).append(",")
              .append(actualField)
              .append("\r\n");

        // content
        String wrapChar = wrapValueWithDoubleQuote ? "\"" : "";
        discrepancies.forEach(discrepancy -> buffer.append(TextUtils.toString(discrepancy, ",", wrapChar, wrapChar))
                                                   .append("\r\n"));
        return buffer.toString();
    }

    protected String toHTML() {
        StringBuilder buffer = new StringBuilder();

        // header
        buffer.append("<table class=\"compare-extended-result-table\">").append(lineSeparator());

        buffer.append("<thead><tr>");
        displayFields.forEach(display -> buffer.append("<th>").append(display).append("</th>"));
        buffer.append("<th>").append(mismatchedField).append("</th>")
              .append("<th>").append(expectedField).append("</th>")
              .append("<th>").append(actualField).append("</th>");
        buffer.append("</tr></thead>").append(lineSeparator());

        // content
        buffer.append("<tbody>").append(lineSeparator());
        discrepancies.forEach(discrepancy -> {
            buffer.append("<tr>");
            discrepancy.forEach(value -> buffer.append("<td>").append(value).append("</td>"));
            buffer.append("</tr>").append(lineSeparator());
        });
        buffer.append("</tbody>").append(lineSeparator());

        buffer.append("</table>").append(lineSeparator());

        return buffer.toString();
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
            discrepancy.add(displayIndex == -1 ? "" : record[displayIndex + 1]);
        });
        discrepancy.add(field);
        discrepancy.add(expected);
        discrepancy.add(actual);
        return discrepancy;
    }
}
