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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.utils.DateUtility;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.plugins.io.ComparisonResult.ResultType;
import org.thymeleaf.context.Context;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import static org.nexial.core.plugins.io.ComparisonResult.EOL;
import static org.nexial.core.utils.ExecUtils.NEXIAL_MANIFEST;

public class FileComparisonReport implements Serializable {
    private static final Map<Integer, Integer> COLUMN_SIZES = initColumnSizes();
    private static final int FOOTER_HEADER = 10;
    private static final int CATEGRORY_HEADER = 18;
    static final Gson GSON = new GsonBuilder().setPrettyPrinting()
                                              .disableHtmlEscaping()
                                              .excludeFieldsWithoutExposeAnnotation()
                                              .disableInnerClassSerialization()
                                              .setLenient()
                                              .create();
    private int matchCount;
    private int mismatchCount;
    private List<ComparisonResult> lineMatches = new ArrayList<>();
    private List<ComparisonResult> fileMatches = new ArrayList<>();

    public int getMatchCount() { return matchCount; }

    public int getMismatchCount() { return mismatchCount; }

    public boolean hasMismatch() {
        return CollectionUtils.isNotEmpty(lineMatches) || CollectionUtils.isNotEmpty(fileMatches);
    }

    public void addLineMismatch(ComparisonResult mismatch) {
        mismatchCount++;
        if (mismatch != null) { lineMatches.add(mismatch); }
    }

    public void addLineMatch(ComparisonResult match) {
        matchCount++;
        if (match != null) { lineMatches.add(match); }
    }

    public void addFileMismatch(ComparisonResult mismatch) {
        if (mismatch != null) { fileMatches.add(mismatch); }
    }

    public List<ComparisonResult> getFileMatches() { return fileMatches; }

    public double getMatchPercent() {
        if (mismatchCount == 0) { return 1; }
        return ((double) matchCount / (double) (matchCount + mismatchCount));
    }

    public double getMismatchPercent() {
        if (matchCount == 0 && mismatchCount != 0) { return 1; }
        return ((double) mismatchCount / (double) (matchCount + mismatchCount));
    }

    public List<ComparisonResult> getLineMatches() { return lineMatches; }

    public String showDiffs() {
        StringBuilder buffer = new StringBuilder();

        if (CollectionUtils.isNotEmpty(lineMatches)) {
            lineMatches.forEach(result -> {
                ResultType type = result.getType();
                if (type != null) {
                    int expectedLine = result.getExpectedLine();
                    String expected = StringUtils.defaultIfEmpty(result.getExpected(), "");
                    int actualLine = result.getActualLine();
                    String actual = result.getActual();

                    String message;
                    switch (type) {
                        case MATCHED:
                        case MISMATCH:
                        case MISSING: {
                            message = "[" + expected + "]";
                            break;
                        }
                        case ADDED: {
                            message = "[" + actual + "]|missing in EXPECTED";
                            break;
                        }
                        default: {
                            throw new IllegalArgumentException("Unknown diff type " + type);
                        }
                    }

                    if (expectedLine != actualLine) { message += "|ACTUAL moved to line " + actualLine; }

                    buffer.append(StringUtils.leftPad(expectedLine + "", 4))
                          .append("|")
                          .append(StringUtils.leftPad(type + "", 10))
                          .append("|")
                          .append(message)
                          .append(EOL);
                }
            });
        } else {
            if (CollectionUtils.isNotEmpty(fileMatches)) {
                fileMatches.forEach(result -> buffer.append(result.getMessage()).append(EOL));
            }
        }

        // 7. output
        return buffer.toString();
    }

    public String toPlainTextReport() {
        StringBuilder buffer = new StringBuilder();

        // 1. header
        buffer.append(makeHeader()).append(EOL);

        // 2. file matches
        fileMatches.forEach(result -> {
            List<String> moreMsgs = result.getAdditionalMessages();

            buffer.append(makeReportColumn(1, result.getMessage()))
                  .append(makeReportColumn(2, "E"))
                  .append(makeReportColumn(3, ""))
                  .append(makeReportColumn(4, result.getExpected()))
                  .append(EOL)
                  .append(makeMessage2Column(1, CollectionUtils.size(moreMsgs) > 0 ? moreMsgs.get(0) : ""))
                  .append(makeReportColumn(2, "A"))
                  .append(makeReportColumn(3, ""))
                  .append(makeReportColumn(4, result.getActual()))
                  .append(EOL);
            if (CollectionUtils.size(moreMsgs) > 1) {
                for (int i = 1; i < moreMsgs.size(); i++) {
                    buffer.append(makeMessage2Column(1, moreMsgs.get(i)))
                          .append(makeReportColumn(2, ""))
                          .append(makeReportColumn(3, ""))
                          .append(makeReportColumn(4, ""))
                          .append(EOL);
                }
            }

            buffer.append(makeLine());
        });

        // 3. line matches
        lineMatches.forEach(result -> {
            List<String> moreMsgs = result.getAdditionalMessages();

            buffer.append(makeReportColumn(1, result.getMessage()))
                  .append(makeReportColumn(2, "E"))
                  .append(makeReportColumn(3, result.getExpectedLine()))
                  .append(makeReportData(4, result.getExpected()))
                  .append(EOL)
                  .append(makeMessage2Column(1, CollectionUtils.size(moreMsgs) > 0 ? moreMsgs.get(0) : ""))
                  .append(makeReportColumn(2, "A"))
                  .append(makeReportColumn(3, result.getActualLine()))
                  .append(makeReportData(4, result.getActual()))
                  .append(EOL);
            if (CollectionUtils.size(moreMsgs) > 1) {
                for (int i = 1; i < moreMsgs.size(); i++) {
                    buffer.append(makeMessage2Column(1, moreMsgs.get(i)))
                          .append(makeReportColumn(2, ""))
                          .append(makeReportColumn(3, ""))
                          .append(makeReportColumn(4, ""))
                          .append(EOL);
                }
            }

            buffer.append(makeLine());
        });

        // 4. legend
        buffer.append(EOL)
              .append(makeFooterHeader("Legend")).append("E=EXPECTED, A=ACTUAL")
              .append(EOL);

        // 5. stats
        if (CollectionUtils.isNotEmpty(lineMatches)) {
            buffer.append(makeFooterHeader("Summary"))
                  .append(makeCategoryHeader("Lines matched")).append(getMatchCount()).append(EOL)
                  .append(makeSpaces(FOOTER_HEADER))
                  .append(makeCategoryHeader("Lines mismatched")).append(getMismatchCount()).append(EOL)
                  .append(makeSpaces(FOOTER_HEADER))
                  .append(makeCategoryHeader("Match percentage"))
                  .append(IoCommand.formatPercent(getMatchPercent())).append(EOL);
        }

        // 6. sign off
        buffer.append(EOL)
              .append("***** generated on ").append(rightNow()).append(EOL)
              .append("***** powered by   ").append(NEXIAL_MANIFEST).append(EOL);

        // 7. output
        return buffer.toString();
    }

    public String toJsonReport() {
        JsonObject json = new JsonObject();

        JsonArray summaries = new JsonArray();
        fileMatches.forEach(result -> {
            if (result != null) {
                JsonObject summary = new JsonObject();
                summary.addProperty("message", StringUtils.trim(result.getMessage()));
                summary.addProperty("actual", StringUtils.trim(result.getActual()));
                summary.addProperty("expected", StringUtils.trim(result.getExpected()));
                summaries.add(summary);
            }
        });

        if (summaries.size() > 0) { json.add("summary", summaries); }

        if (CollectionUtils.isNotEmpty(lineMatches)) { json.add("details", GSON.toJsonTree(lineMatches)); }

        if (CollectionUtils.isNotEmpty(lineMatches)) {
            json.addProperty("statistics",
                             "Lines matched: " + getMatchCount() + ", " +
                             "Lines mismatched: " + getMismatchCount() + ". " +
                             "Match percentage: " + IoCommand.formatPercent(getMatchPercent()));
        }

        json.addProperty("generated-on", rightNow());
        json.addProperty("powered-by", NEXIAL_MANIFEST);

        return GSON.toJson(json);
    }

    public String toHtmlReport(ExecutionContext context) {
        if (context == null) { return null; }

        Context engineContext = new Context();
        engineContext.setVariable("report", this);
        return context.getTemplateEngine().process("compare", engineContext);
    }

    private String makeLine() {
        int[] totalLength = new int[]{0};
        COLUMN_SIZES.forEach((position, size) -> totalLength[0] += size + 1);
        return StringUtils.repeat("-", totalLength[0] - 1) + " " + EOL;
    }

    private static Map<Integer, Integer> initColumnSizes() {
        Map<Integer, Integer> map = new HashMap<>();
        map.put(1, 40);
        map.put(2, 2);
        map.put(3, 5);
        map.put(4, 50);
        return map;
    }

    private String makeMessage2Column(int index, String message) {
        return makeReportColumn(index, "  " + StringUtils.defaultIfEmpty(message, ""));
    }

    private String makeReportColumn(int index, String message) {
        return StringUtils.rightPad(StringUtils.defaultIfEmpty(message, ""), COLUMN_SIZES.get(index)) + " ";
    }

    private String makeReportColumn(int index, int number) {
        return StringUtils.leftPad(number + "", COLUMN_SIZES.get(index)) + " ";
    }

    private String makeReportData(int index, String message) {
        message = StringUtils.defaultIfEmpty(message, "");
        if (!StringUtils.equals(message, ComparisonResult.MISSING)) { message = "[" + message + "]";}
        return StringUtils.rightPad(message, COLUMN_SIZES.get(index)) + " ";
    }

    private String makeHeader() {
        return StringUtils.rightPad("MATCH", COLUMN_SIZES.get(1)) + " EA LINE# CONTENT" + EOL +
               StringUtils.repeat("=", COLUMN_SIZES.get(1)) + " " +
               StringUtils.repeat("=", COLUMN_SIZES.get(2)) + " " +
               StringUtils.repeat("=", COLUMN_SIZES.get(3)) + " " +
               StringUtils.repeat("=", COLUMN_SIZES.get(4)) + " ";
    }

    private String makeCategoryHeader(String title) { return StringUtils.rightPad(title + ": ", CATEGRORY_HEADER); }

    private String makeSpaces(int size) { return StringUtils.repeat(" ", size); }

    private String makeFooterHeader(String title) { return StringUtils.rightPad(title + ":", FOOTER_HEADER); }

    private String rightNow() { return DateUtility.format(System.currentTimeMillis(), "yyyy-MM-dd HH:mm:ss"); }
}
