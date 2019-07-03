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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.similarity.LevenshteinResults;
import org.nexial.commons.utils.TextUtils;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import static org.nexial.core.plugins.io.ComparisonResult.ResultType.*;

public class ComparisonResult implements Serializable {
    protected static final String EOL = "\r\n";
    protected static final String MISSING = "***** MISSING";
    protected static final String EOL_WIN = " CRLF (Windows)";
    protected static final String EOL_NIX = " LF (*NIX)";

    @SerializedName("expected-line")
    @Expose
    private int expectedLine;

    @Expose
    private String expected;

    @SerializedName("actual-line")
    @Expose
    private int actualLine;

    @Expose
    private String actual;

    @Expose
    private String message;

    @Expose
    private List<String> additionalMessages = new ArrayList<>();

    @Expose(serialize = false, deserialize = false)
    private ResultType type;

    enum ResultType {MATCHED, ADDED, MISSING, MISMATCH}

    private ComparisonResult() { }

    public static ComparisonResult line(int line, String message, String expected, String actual) {
        ComparisonResult result = new ComparisonResult();
        result.setLine(line);
        result.setExpected(expected);
        result.setActual(actual);
        result.setMessage(message);
        result.setType(MISMATCH);
        return result;
    }

    public static ComparisonResult lineMatched(int line, String message, String content) {
        ComparisonResult result = new ComparisonResult();
        result.setLine(line);
        result.setExpected(content);
        result.setActual(content);
        result.setMessage(StringUtils.defaultIfBlank(message, "perfect match"));
        result.setType(MATCHED);
        return result;
    }

    public static ComparisonResult lineMissing(int line, String expected, String actual) {
        return lineExtraFound(line, expected, actual);
    }

    public static ComparisonResult lineExtraFound(int line, String expected, String actual) {
        ComparisonResult result = new ComparisonResult();
        result.setLine(line);
        result.setExpected(expected);
        result.setActual(actual);
        if (expected == null) {
            result.setMessage("extra line found in ACTUAL");
            result.setType(ADDED);
        } else {
            result.setMessage("missing line in ACTUAL");
            result.setType(ResultType.MISSING);
        }

        return result;
    }

    public ComparisonResult maligned(int expectedLine, int actualLine) {
        if (expectedLine != actualLine) {
            setExpectedLine(expectedLine);
            setActualLine(actualLine);
            addMessages("(EXPECTED line " + expectedLine + ", ACTUAL line " + actualLine + ")");
        }
        return this;
    }

    public static ComparisonResult lineDiff(int line, LevenshteinResults lsr, String expected, String actual) {
        ComparisonResult result = new ComparisonResult();
        result.setLine(line);
        result.setExpected(expected);
        result.setActual(actual);
        result.setMessage(lsr.getDistance() + " mismatch(s) found in this line: ");

        if (lsr.getInsertCount() > 0) { result.addMessages(lsr.getInsertCount() + " added"); }
        if (lsr.getDeleteCount() > 0) { result.addMessages(lsr.getDeleteCount() + " removed"); }
        if (lsr.getSubstituteCount() > 0) { result.addMessages(lsr.getSubstituteCount() + " replaced"); }

        result.setType(MISMATCH);

        return result;
    }

    public static ComparisonResult file(String message) {
        ComparisonResult result = new ComparisonResult();
        result.setLine(-1);
        result.setMessage(message);
        return result;
    }

    public static ComparisonResult file(String message, String expected, String actual) {
        ComparisonResult result = new ComparisonResult();
        result.setLine(-1);
        result.setExpected(expected);
        result.setActual(actual);
        result.setMessage(message);
        return result;
    }

    public static ComparisonResult fileSizeDiff(long expected, long actual) {
        ComparisonResult result = new ComparisonResult();
        result.setLine(-1);
        result.setExpected(expected + " bytes");
        result.setActual(actual + " bytes");
        result.setMessage("EXPECTED and ACTUAL sizes are different");
        return result;
    }

    public static ComparisonResult contentEmpty(long expected, long actual) {
        ComparisonResult result = fileLineDiff(expected, actual);
        result.setMessage((expected < 1 ? "EXPECTED" : actual < 1 ? "ACTUAL" : "BOTH ") + " content is empty/blank");
        return result;
    }

    public static ComparisonResult fileLineDiff(long expected, long actual) {
        ComparisonResult result = new ComparisonResult();
        result.setLine(-1);
        result.setExpected(expected + " line" + (expected > 1 ? "s" : ""));
        result.setActual(actual + " line" + (actual > 1 ? "s" : ""));
        result.setMessage("number of lines are different");
        result.setType(MISMATCH);
        return result;
    }

    public static ComparisonResult fileEolDiff(int expectedNL, int expectedCR, int actualNL, int actualCR) {
        ComparisonResult result = new ComparisonResult();
        result.setLine(-1);
        result.setExpected(expectedNL == expectedCR ? "0" :
                           expectedNL == 0 ? expectedCR + EOL_WIN :
                           expectedCR == 0 ? expectedNL + EOL_NIX :
                           ("mixed/mostly " + (expectedCR > expectedNL ? EOL_WIN : EOL_NIX)));
        result.setActual(actualNL == actualCR ? "0" :
                         actualNL == 0 ? actualCR + EOL_WIN :
                         actualCR == 0 ? actualNL + EOL_NIX :
                         ("mixed/mostly " + (actualCR > actualNL ? EOL_WIN : EOL_NIX)));
        result.setMessage("difference in end-of-line characters");
        return result;
    }

    public int getLine() { return expectedLine; }

    private void setLine(int line) {
        this.expectedLine = line;
        this.actualLine = line;
    }

    public ResultType getType() { return type; }

    private void setType(ResultType type) { this.type = type; }

    public String getExpected() { return StringUtils.defaultString(expected); }

    private void setExpected(String expected) { this.expected = expected == null ? MISSING : expected; }

    public String getActual() { return StringUtils.defaultString(actual); }

    private void setActual(String actual) { this.actual = actual == null ? MISSING : actual; }

    @Override
    public String toString() {
        if (expectedLine == -1) {
            return "[files] " + message +
                   (StringUtils.isNotEmpty(expected) && StringUtils.isNotEmpty(actual) ?
                    EOL + "        (expected) " + formatForToString(expected) +
                    EOL + "        (  actual) " + formatForToString(actual) :
                    "");
        }

        return "[" + StringUtils.leftPad(expectedLine + "", 5) + "] " + message +
               EOL + "        (expected) " + formatForToString(expected) +
               EOL + "        (  actual) " + formatForToString(actual);
    }

    public String getMessage() { return message; }

    private void setMessage(String message) { this.message = message; }

    public List<String> getAdditionalMessages() { return additionalMessages; }

    public int getExpectedLine() { return expectedLine; }

    private void setExpectedLine(int expectedLine) { this.expectedLine = expectedLine; }

    public int getActualLine() { return actualLine; }

    private void setActualLine(int actualLine) { this.actualLine = actualLine; }

    protected String formatForToString(String line) {
        if (StringUtils.isBlank(line)) { return "[EMPTY/BLANK]"; }

        StringBuilder buffer = new StringBuilder();
        List<String> lines = TextUtils.toList(StringUtils.replace(line, "\r\n", "\n"), "\n", false);
        if (CollectionUtils.size(lines) < 2) { return line; }

        lines.forEach(text -> buffer.append(StringUtils.repeat(" ", 19)).append(text).append(EOL));
        return StringUtils.trim(buffer.toString());
    }

    private void addMessages(String message) {
        if (StringUtils.isEmpty(message)) { return; }
        this.additionalMessages.add(message);
    }
}
