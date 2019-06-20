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

package org.nexial.core.excel;

import java.awt.*;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFSheet;

import static org.apache.poi.ss.usermodel.BorderStyle.THIN;
import static org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND;
import static org.apache.poi.ss.usermodel.VerticalAlignment.CENTER;
import static org.nexial.core.excel.ExcelConfig.StyleConfig.*;

public class ExcelConfig {

    // plan
    public static final ExcelAddress ADDR_PLAN_HEADER_EXEC_SUMMARY_HEADER = new ExcelAddress("K1");
    public static final ExcelAddress ADDR_PLAN_HEADER_SEQUENCE1 = new ExcelAddress("A1:A1");
    public static final ExcelAddress ADDR_PLAN_HEADER_SEQUENCE2 = new ExcelAddress("F1:H1");
    public static final ExcelAddress ADD_PLAN_HEADER_FEATURE_AND_TEST = new ExcelAddress("G1:H1");
    public static final String PLAN_HEADER_SUMMARY = "summary";
    public static final String PLAN_HEADER_AUTHOR = "author";
    public static final String PLAN_HEADER_FEATURE_OVERRIDE = "story / feature";
    public static final String PLAN_HEADER_TESTREF_OVERRIDE = "test id";
    public static final List<String> PLAN_HEADER_SEQUENCE = Arrays.asList(PLAN_HEADER_SUMMARY,
                                                                          PLAN_HEADER_AUTHOR,
                                                                          PLAN_HEADER_FEATURE_OVERRIDE,
                                                                          PLAN_HEADER_TESTREF_OVERRIDE);
    public static final List<String> PLAN_HEADER_SEQUENCE_V2 = Arrays.asList(PLAN_HEADER_SUMMARY,
                                                                             PLAN_HEADER_AUTHOR,
                                                                             "jira override",
                                                                             "zephyr override");

    public static final ExcelAddress ADDR_PLAN_HEADER_EXECUTION = new ExcelAddress("A4:M4");
    public static final String PLAN_HEADER_DESCRIPTION = "description";
    public static final String PLAN_HEADER_TEST_SCRIPT = "test script";
    public static final String PLAN_HEADER_TEST_SCENARIOS = "test scenarios";
    public static final String PLAN_HEADER_TEST_DATA = "test data";
    public static final String PLAN_HEADER_DATA_SHEETS = "data sheets";
    public static final String PLAN_HEADER_FAIL_FAST = "fail fast?";
    public static final String PLAN_HEADER_WAIT = "wait?";
    public static final String PLAN_HEADER_LOAD_TEST = "load test?";
    public static final String PLAN_HEADER_LOAD_TEST_SPEC = "min, max, ramp up sec, hold for sec";
    public static final String PLAN_HEADER_ELAPSED_MS = "elapsed ms";
    public static final String PLAN_HEADER_RESULT = "result";
    public static final String PLAN_HEADER_REASON = "reason";
    public static final List<String> PLAN_HEADER_EXECUTION = Arrays.asList(PLAN_HEADER_DESCRIPTION,
                                                                           PLAN_HEADER_TEST_SCRIPT,
                                                                           PLAN_HEADER_TEST_SCENARIOS,
                                                                           PLAN_HEADER_TEST_DATA,
                                                                           PLAN_HEADER_DATA_SHEETS,
                                                                           PLAN_HEADER_FAIL_FAST,
                                                                           PLAN_HEADER_WAIT,
                                                                           PLAN_HEADER_LOAD_TEST,
                                                                           PLAN_HEADER_LOAD_TEST_SPEC,
                                                                           "",
                                                                           PLAN_HEADER_ELAPSED_MS,
                                                                           PLAN_HEADER_RESULT,
                                                                           PLAN_HEADER_REASON);
    public static final ExcelAddress ADDR_PLAN_EXECUTION_START = new ExcelAddress("B5:B5");
    public static final int COL_IDX_PLAN_DESCRIPTION = 'A' - 'A';
    public static final int COL_IDX_PLAN_TEST_SCRIPT = 'B' - 'A';
    public static final int COL_IDX_PLAN_SCENARIOS = 'C' - 'A';
    public static final int COL_IDX_PLAN_TEST_DATA = 'D' - 'A';
    public static final int COL_IDX_PLAN_DATA_SHEETS = 'E' - 'A';
    public static final int COL_IDX_PLAN_FAIL_FAST = 'F' - 'A';
    public static final int COL_IDX_PLAN_WAIT = 'G' - 'A';
    public static final int COL_IDX_PLAN_LOAD_TEST = 'H' - 'A';
    public static final int COL_IDX_PLAN_LOAD_TEST_SPEC = 'I' - 'A';
    // public static final int COL_IDX_PLAN_ELAPSED_MS = 'K' - 'A';
    // public static final int COL_IDX_PLAN_RESULT = 'L' - 'A';
    // public static final int COL_IDX_PLAN_REASON = 'M' - 'A';

    // test script
    public static final ExcelAddress ADDR_SCENARIO_DESCRIPTION = new ExcelAddress("A2:D2");
    public static final ExcelAddress ADDR_HEADER_SCENARIO_INFO1 = new ExcelAddress("A1:A1");
    public static final ExcelAddress ADDR_HEADER_SCENARIO_INFO2 = new ExcelAddress("E1:I1");
    public static final String HEADER_SCENARIO_INFO_DESCRIPTION = "description";
    public static final String HEADER_SCENARIO_INFO_PROJECT = "project";
    public static final String HEADER_SCENARIO_INFO_RELEASE = "release";
    public static final String HEADER_SCENARIO_INFO_FEATURE = "story / feature";
    public static final String HEADER_SCENARIO_INFO_TESTREF = "test id";
    public static final String HEADER_SCENARIO_INFO_AUTHOR = "author";
    public static final List<String> HEADER_SCENARIO_INFO = Arrays.asList(HEADER_SCENARIO_INFO_DESCRIPTION,
                                                                          HEADER_SCENARIO_INFO_PROJECT,
                                                                          HEADER_SCENARIO_INFO_RELEASE,
                                                                          HEADER_SCENARIO_INFO_FEATURE,
                                                                          HEADER_SCENARIO_INFO_TESTREF,
                                                                          HEADER_SCENARIO_INFO_AUTHOR);

    public static final ExcelAddress ADDR_SCENARIO_EXEC_SUMMARY_HEADER = new ExcelAddress("L1");
    public static final ExcelAddress ADDR_SCENARIO_EXEC_SUMMARY = new ExcelAddress("L2");
    public static final String HEADER_EXEC_SUMMARY = "execution summary";

    public static final List<String> HEADER_SCENARIO_INFO_V1 = Arrays.asList(HEADER_SCENARIO_INFO_DESCRIPTION,
                                                                             HEADER_SCENARIO_INFO_PROJECT,
                                                                             HEADER_SCENARIO_INFO_RELEASE,
                                                                             "jira",
                                                                             "zephyr",
                                                                             HEADER_SCENARIO_INFO_AUTHOR);

    public static final ExcelAddress ADDR_HEADER_TEST_STEP = new ExcelAddress("A4:O4");

    // v1; to be removed
    public static final String HEADER_TEST_STEP_TESTCASE = "test case";
    public static final String HEADER_TEST_STEP_DESCRIPTION = "description";
    // v1; to be removed
    public static final String HEADER_TEST_STEP_TARGET = "target";
    public static final String HEADER_TEST_STEP_COMMAND = "command";
    public static final String HEADER_TEST_STEP_PARAM1 = "param 1";
    public static final String HEADER_TEST_STEP_PARAM2 = "param 2";
    public static final String HEADER_TEST_STEP_PARAM3 = "param 3";
    public static final String HEADER_TEST_STEP_PARAM4 = "param 4";
    public static final String HEADER_TEST_STEP_PARAM5 = "param 5";
    public static final String HEADER_TEST_STEP_FLOW_CONTROLS = "flow controls";
    public static final String HEADER_TEST_STEP_SCREENSHOT = "screenshot";
    public static final String HEADER_TEST_STEP_ELAPSED_MS = "elapsed ms";
    public static final String HEADER_TEST_STEP_RESULT = "result";
    public static final String HEADER_TEST_STEP_REASON = "reason";
    public static final List<String> HEADER_TEST_STEP_LIST_V1 = Arrays.asList(HEADER_TEST_STEP_TESTCASE,
                                                                              HEADER_TEST_STEP_DESCRIPTION,
                                                                              HEADER_TEST_STEP_TARGET,
                                                                              HEADER_TEST_STEP_COMMAND,
                                                                              HEADER_TEST_STEP_PARAM1,
                                                                              HEADER_TEST_STEP_PARAM2,
                                                                              HEADER_TEST_STEP_PARAM3,
                                                                              HEADER_TEST_STEP_PARAM4,
                                                                              HEADER_TEST_STEP_PARAM5,
                                                                              HEADER_TEST_STEP_FLOW_CONTROLS,
                                                                              "",
                                                                              HEADER_TEST_STEP_SCREENSHOT,
                                                                              HEADER_TEST_STEP_ELAPSED_MS,
                                                                              HEADER_TEST_STEP_RESULT,
                                                                              HEADER_TEST_STEP_REASON);
    public static final List<String> HEADER_TEST_STEP_LIST_V15 = Arrays.asList(HEADER_TEST_STEP_TESTCASE,
                                                                               HEADER_TEST_STEP_DESCRIPTION,
                                                                               HEADER_TEST_STEP_TARGET,
                                                                               HEADER_TEST_STEP_COMMAND,
                                                                               HEADER_TEST_STEP_PARAM1,
                                                                               HEADER_TEST_STEP_PARAM2,
                                                                               HEADER_TEST_STEP_PARAM3,
                                                                               HEADER_TEST_STEP_PARAM4,
                                                                               HEADER_TEST_STEP_PARAM5,
                                                                               "notes",
                                                                               "",
                                                                               HEADER_TEST_STEP_SCREENSHOT,
                                                                               HEADER_TEST_STEP_ELAPSED_MS,
                                                                               HEADER_TEST_STEP_RESULT,
                                                                               HEADER_TEST_STEP_REASON);
    public static final String HEADER_ACTIVITY = "activity";
    public static final String HEADER_COMMAND_TYPE = "cmd type";
    public static final List<String> HEADER_TEST_STEP_LIST_V2 = Arrays.asList(HEADER_ACTIVITY,
                                                                              HEADER_TEST_STEP_DESCRIPTION,
                                                                              HEADER_COMMAND_TYPE,
                                                                              HEADER_TEST_STEP_COMMAND,
                                                                              HEADER_TEST_STEP_PARAM1,
                                                                              HEADER_TEST_STEP_PARAM2,
                                                                              HEADER_TEST_STEP_PARAM3,
                                                                              HEADER_TEST_STEP_PARAM4,
                                                                              HEADER_TEST_STEP_PARAM5,
                                                                              HEADER_TEST_STEP_FLOW_CONTROLS,
                                                                              "",
                                                                              HEADER_TEST_STEP_SCREENSHOT,
                                                                              HEADER_TEST_STEP_ELAPSED_MS,
                                                                              HEADER_TEST_STEP_RESULT,
                                                                              HEADER_TEST_STEP_REASON);

    public static final ExcelAddress ADDR_HEADER_MACRO = new ExcelAddress("A1:L1");
    public static final ExcelAddress ADDR_MACRO_COMMAND_START = new ExcelAddress("C2:D2");
    public static final List<String> HEADER_MACRO_TEST_STEPS = Arrays.asList("macro",
                                                                             HEADER_TEST_STEP_DESCRIPTION,
                                                                             HEADER_COMMAND_TYPE,
                                                                             HEADER_TEST_STEP_COMMAND,
                                                                             HEADER_TEST_STEP_PARAM1,
                                                                             HEADER_TEST_STEP_PARAM2,
                                                                             HEADER_TEST_STEP_PARAM3,
                                                                             HEADER_TEST_STEP_PARAM4,
                                                                             HEADER_TEST_STEP_PARAM5,
                                                                             HEADER_TEST_STEP_FLOW_CONTROLS,
                                                                             "",
                                                                             HEADER_TEST_STEP_SCREENSHOT);

    public static final char COL_TEST_CASE = 'A';
    public static final char COL_TARGET = 'C';
    public static final char COL_COMMAND = 'D';
    public static final char COL_CAPTURE_SCREEN = 'L';
    public static final char COL_REASON = 'O';

    public static final int COL_IDX_TESTCASE = 'A' - COL_TEST_CASE;
    public static final int COL_IDX_DESCRIPTION = 'B' - COL_TEST_CASE;
    public static final int COL_IDX_TARGET = COL_TARGET - COL_TEST_CASE;
    public static final int COL_IDX_COMMAND = COL_COMMAND - COL_TEST_CASE;
    public static final int COL_IDX_PARAMS_START = 'E' - COL_TEST_CASE;
    public static final int COL_IDX_PARAMS_END = 'I' - COL_TEST_CASE;
    public static final int COL_IDX_FLOW_CONTROLS = 'J' - COL_TEST_CASE;
    public static final int COL_IDX_CAPTURE_SCREEN = COL_CAPTURE_SCREEN - COL_TEST_CASE;
    public static final int COL_IDX_ELAPSED_MS = 'M' - COL_TEST_CASE;
    public static final int COL_IDX_RESULT = 'N' - COL_TEST_CASE;
    public static final int COL_IDX_REASON = COL_REASON - COL_TEST_CASE;
    public static final int COL_IDX_MERGE_RESULT_START = COL_IDX_PARAMS_START;
    public static final int COL_IDX_MERGE_RESULT_END = COL_IDX_PARAMS_END;

    //testData
    public static final ExcelAddress ADDR_FIRST_DATA_COL = new ExcelAddress("A1");

    // extract
    public static final ExcelAddress ADDR_COMMAND_START = new ExcelAddress("C5:D5");
    public static final ExcelAddress ADDR_PARAMS_START = new ExcelAddress("E5:I5");
    public static final String FIRST_STEP_ROW = "" + COL_TEST_CASE + (ADDR_COMMAND_START.getRowStartIndex() + 1);

    // agenda
    public static final int ALPHABET_COUNT = 'Z' - 'A' + 1;

    // public static final short CELL_HEIGHT_DEFAULT = 480;
    public static final int MAX_CELL_WIDTH = 256 * 255;
    public static final double DEF_CHAR_WIDTH = 24.7;
    public static final float EXEC_SUMMARY_HEIGHT = 21f;

    // agenda metadata
    public static final String MSG_FAIL = "FAIL ";
    public static final String MSG_PASS = "PASS ";
    public static final String MSG_SCREENCAPTURE = "Click here";
    public static final String MSG_SKIPPED = "SKIPPED ";
    public static final String MSG_WARN = "WARN ";
    public static final String MSG_ABORT = "[ABORT] ";

    public static final String STYLE_TEST_CASE = "TEST_CASE";

    // style names
    public static final String STYLE_DESCRIPTION = "DESCRIPTION";
    public static final String STYLE_SECTION_DESCRIPTION = "SECTION_DESCRIPTION";
    public static final String STYLE_REPEAT_UNTIL_DESCRIPTION = "REPEAT_UNTIL_DESCRIPTION";
    public static final String STYLE_FAILED_STEP_DESCRIPTION = "FAILED_STEP_DESCRIPTION";
    // public static final String STYLE_SKIPPED_STEP_DESCRIPTION = "SKIPPED_STEP_DESCRIPTION";
    public static final String STYLE_TARGET = "TARGET";
    public static final String STYLE_COMMAND = "COMMAND";
    public static final String STYLE_PARAM = "PARAM";
    public static final String STYLE_TAINTED_PARAM = "TAINTED_PARAM";
    public static final String STYLE_SCREENSHOT = "SCREENSHOT";
    public static final String STYLE_ELAPSED_MS = "ELAPSED_MS";
    public static final String STYLE_ELAPSED_MS_BAD_SLA = "ELAPSED_MS_BAD_SLA";
    public static final String STYLE_FAILED_RESULT = "FAILED_RESULT";
    public static final String STYLE_MESSAGE = "MESSAGE_STYLE";
    public static final String STYLE_SKIPPED_RESULT = "SKIPPED_STYLE";
    public static final String STYLE_SUCCESS_RESULT = "SUCCESS_RESULT";
    public static final String STYLE_EXEC_SUMM_TITLE = "EXEC_SUMM_TITLE";
    public static final String STYLE_EXEC_SUMM_DATA_HEADER = "EXEC_SUMM_DATA_HEADER";
    public static final String STYLE_EXEC_SUMM_DATA_NAME = "EXEC_SUMM_DATA_NAME";
    public static final String STYLE_EXEC_SUMM_DATA_VALUE = "EXEC_SUMM_DATA_VALUE";
    public static final String STYLE_EXEC_SUMM_EXCEPTION = "EXEC_SUMM_EXCEPTION";
    public static final String STYLE_EXEC_SUMM_HEADER = "EXEC_SUMM_HEADER";
    public static final String STYLE_EXEC_SUMM_SCENARIO = "EXEC_SUMM_SCENARIO";
    public static final String STYLE_EXEC_SUMM_ACTIVITY = "EXEC_SUMM_ACTIVITY";
    public static final String STYLE_EXEC_SUMM_TIMESPAN = "EXEC_SUMM_TIMESPAN";
    public static final String STYLE_EXEC_SUMM_DURATION = "EXEC_SUMM_DURATION";
    public static final String STYLE_EXEC_SUMM_TOTAL = "EXEC_SUMM_TOTAL";
    public static final String STYLE_EXEC_SUMM_PASS = "EXEC_SUMM_PASS";
    public static final String STYLE_EXEC_SUMM_FAIL = "EXEC_SUMM_FAIL";
    public static final String STYLE_EXEC_SUMM_SUCCESS = "EXEC_SUMM_SUCCESS";
    public static final String STYLE_EXEC_SUMM_NOT_SUCCESS = "EXEC_SUMM_NOT_SUCCESS";
    public static final String STYLE_EXEC_SUMM_FINAL_SUCCESS = "EXEC_SUMM_FINAL_SUCCESS";
    public static final String STYLE_EXEC_SUMM_FINAL_NOT_SUCCESS = "EXEC_SUMM_FINAL_NOT_SUCCESS";
    public static final String STYLE_EXEC_SUMM_FINAL_TOTAL = "EXEC_SUMM_FINAL_TOTAL";

    // public static final StyleConfig UNSUPPORTED_COMMAND = newUnsupportedCommandStyle();
    public static final StyleConfig TESTCASE = newTestCaseStyle();
    public static final StyleConfig DESCRIPTION = newDescriptionStyle();
    public static final StyleConfig SECTION_DESCRIPTION = newSectionDescriptionStyle();
    public static final StyleConfig REPEAT_UNTIL_DESCRIPTION = newRepeatUntilDescriptionStyle();
    public static final StyleConfig FAILED_STEP_DESCRIPTION = newFailedStepDescriptionStyle();
    public static final StyleConfig TARGET = newTargetStyle();
    public static final StyleConfig COMMAND = newCommandStyle();
    public static final StyleConfig STRIKEOUT_COMMAND = newDisabledCommandStyle();
    public static final StyleConfig PARAM = newParamStyle();
    public static final StyleConfig TAINTED_PARAM = newTaintedParamStyle();
    public static final StyleConfig SCREENSHOT = newScreenshotStyle();
    public static final StyleConfig ELAPSED_MS = newElapsedMsStyle();
    public static final StyleConfig ELAPSED_MS_BAD_SLA = newElapsedMsBadSlaStyle();
    public static final StyleConfig SUCCESS = newSuccessStyle();
    public static final StyleConfig FAILED = newFailedStyle();
    public static final StyleConfig SKIPPED = newSkippedStyle();
    public static final StyleConfig RESULT = newResultStyle();
    public static final StyleConfig LINK = newLinkStyle();
    public static final StyleConfig MSG = newMsgStyle();

    public static final StyleConfig PREDEF_TEST_DATA_NAME = newPredefTestDataNameStyle();
    public static final StyleConfig TEST_DATA_NAME = newTestDataNameStyle();
    public static final StyleConfig TEST_DATA_VALUE = newTestDataValueStyle();

    public static final StyleConfig EXEC_SUMM_TITLE = newExecSummaryTitle();
    public static final StyleConfig EXEC_SUMM_DATA_HEADER = newExecSummaryDataHeader();
    public static final StyleConfig EXEC_SUMM_DATA_NAME = newExecSummaryDataName();
    public static final StyleConfig EXEC_SUMM_DATA_VALUE = newExecSummaryDataValue();
    public static final StyleConfig EXEC_SUMM_EXCEPTION = newExecSummaryException();
    public static final StyleConfig EXEC_SUMM_HEADER = newExecSummaryHeader();
    public static final StyleConfig EXEC_SUMM_SCENARIO = newExecSummaryScenario();
    public static final StyleConfig EXEC_SUMM_ACTIVITY = newExecSummaryActivity();
    public static final StyleConfig EXEC_SUMM_TIMESPAN = newExecSummaryTimespan();
    public static final StyleConfig EXEC_SUMM_DURATION = newExecSummaryDuration();
    public static final StyleConfig EXEC_SUMM_TOTAL = newExecSummaryTotal();
    public static final StyleConfig EXEC_SUMM_PASS = newExecSummaryPass();
    public static final StyleConfig EXEC_SUMM_FAIL = newExecSummaryFail();
    public static final StyleConfig EXEC_SUMM_SUCCESS = newExecSummarySuccess();
    public static final StyleConfig EXEC_SUMM_NOT_SUCCESS = newExecSummaryNotSuccess();
    public static final StyleConfig EXEC_SUMM_FINAL_SUCCESS = newExecSummaryFinalSuccess();
    public static final StyleConfig EXEC_SUMM_FINAL_NOT_SUCCESS = newExecSummaryFinalNotSuccess();
    public static final StyleConfig EXEC_SUMM_FINAL_TOTAL = newExecSummaryFinalTotal();

    public static class StyleConfig {
        public static final String FONT_NAME_DEFAULT = "Tahoma";
        public static final String FONT_NAME_FIXED_DEFAULT = "Consolas";

        public static final short FONT_HEIGHT_DEFAULT = (short) 11;
        public static final short FONT_HEIGHT_PARAM = (short) 10;
        public static final XSSFColor FG_FAIL = new XSSFColor(new Color(156, 0, 6));

        private static final short INDENT_1 = (short) 1;

        private XSSFColor backgroundColor;
        private XSSFColor backgroundFillColor;
        private FillPatternType backgroundFillPattern = SOLID_FOREGROUND;
        private boolean boldFont;
        private XSSFColor borderColor;
        private BorderStyle borderStyle = THIN;
        private XSSFColor fontColor;
        private short fontHeight;
        private String fontName = FONT_NAME_DEFAULT;
        private HorizontalAlignment horizontalAlignment;
        private short indention;
        private boolean italicFont;
        private byte underlineStyle = 0;
        private VerticalAlignment verticalAlignment;
        private boolean wrapText;
        private boolean strikeOut;
        private boolean useSpecialTopAndDoubleBottomBorder;

        public XSSFColor getBackgroundColor() { return backgroundColor; }

        public XSSFColor getBackgroundFillColor() { return backgroundFillColor; }

        public FillPatternType getBackgroundFillPattern() { return backgroundFillPattern; }

        public XSSFColor getBorderColor() { return borderColor; }

        public BorderStyle getBorderStyle() { return borderStyle; }

        public short getIndention() { return indention; }

        public String getFontName() { return fontName; }

        public short getFontHeight() { return fontHeight; }

        public XSSFColor getFontColor() { return fontColor; }

        public boolean isBoldFont() { return boldFont; }

        public boolean isItalicFont() { return italicFont; }

        public byte getUnderlineStyle() { return underlineStyle; }

        public HorizontalAlignment getHorizontalAlignment() { return horizontalAlignment; }

        public VerticalAlignment getVerticalAlignment() { return verticalAlignment; }

        public boolean isWrapText() { return wrapText; }

        public boolean isStrikeOut() { return strikeOut; }

        public boolean isUseSpecialTopAndDoubleBottomBorder() { return useSpecialTopAndDoubleBottomBorder; }

        public void setUseSpecialTopAndDoubleBottomBorder(boolean useSpecialTopAndDoubleBottomBorder) {
            this.useSpecialTopAndDoubleBottomBorder = useSpecialTopAndDoubleBottomBorder;
        }

        static StyleConfig newPredefTestDataNameStyle() {
            StyleConfig config = new StyleConfig();
            // DDEBF7
            config.backgroundColor = new XSSFColor(new Color(221, 235, 247));
            config.fontHeight = FONT_HEIGHT_DEFAULT;
            config.fontName = FONT_NAME_DEFAULT;
            // 2F75B5
            config.fontColor = new XSSFColor(new Color(47, 117, 181));
            config.boldFont = true;
            return config;
        }

        static StyleConfig newTestDataNameStyle() {
            StyleConfig config = new StyleConfig();
            // D9D9D9
            config.backgroundColor = new XSSFColor(new Color(217, 217, 217));
            config.fontHeight = FONT_HEIGHT_DEFAULT;
            config.fontName = FONT_NAME_DEFAULT;
            config.fontColor = new XSSFColor(new Color(0, 0, 0));
            config.boldFont = true;
            return config;
        }

        static StyleConfig newTestDataValueStyle() {
            StyleConfig config = new StyleConfig();
            config.fontHeight = FONT_HEIGHT_DEFAULT;
            config.fontName = FONT_NAME_FIXED_DEFAULT;
            config.fontColor = new XSSFColor(new Color(0, 0, 0));
            config.boldFont = false;
            return config;
        }

        static StyleConfig newTestCaseStyle() {
            StyleConfig config = new StyleConfig();
            config.backgroundColor = new XSSFColor(new Color(230, 239, 215));
            config.borderColor = new XSSFColor(new Color(200, 180, 180));
            config.fontHeight = FONT_HEIGHT_DEFAULT;
            config.fontColor = new XSSFColor(new Color(62, 81, 31));
            config.boldFont = true;
            config.verticalAlignment = CENTER;
            return config;
        }

        static StyleConfig newDescriptionStyle() {
            StyleConfig config = new StyleConfig();
            config.fontHeight = FONT_HEIGHT_DEFAULT;
            config.fontColor = new XSSFColor(new Color(40, 115, 137));
            config.wrapText = true;
            config.verticalAlignment = CENTER;
            return config;
        }

        static StyleConfig newSectionDescriptionStyle() {
            StyleConfig config = new StyleConfig();
            config.backgroundColor = new XSSFColor(new Color(220, 225, 230));
            config.borderColor = new XSSFColor(new Color(190, 200, 205));
            config.fontHeight = FONT_HEIGHT_DEFAULT;
            config.fontColor = new XSSFColor(new Color(40, 115, 137));
            config.indention = INDENT_1;
            config.wrapText = true;
            config.verticalAlignment = CENTER;
            return config;
        }

        static StyleConfig newRepeatUntilDescriptionStyle() {
            StyleConfig config = new StyleConfig();
            config.backgroundColor = new XSSFColor(new Color(240, 240, 225));
            config.borderColor = new XSSFColor(new Color(205, 205, 195));
            config.fontHeight = FONT_HEIGHT_DEFAULT;
            config.fontColor = new XSSFColor(new Color(110, 110, 60));
            config.indention = INDENT_1;
            config.wrapText = false;
            config.verticalAlignment = CENTER;
            return config;
        }

        // only additive style updates
        static StyleConfig newFailedStepDescriptionStyle() {
            StyleConfig config = new StyleConfig();
            config.borderColor = new XSSFColor(new Color(205, 190, 190));
            config.backgroundColor = new XSSFColor(new Color(255, 199, 206));
            config.fontHeight = FONT_HEIGHT_DEFAULT;
            config.fontColor = FG_FAIL;
            config.boldFont = true;
            config.verticalAlignment = CENTER;
            config.wrapText = true;
            config.borderColor = new XSSFColor(new Color(205, 195, 195));
            return config;
        }

        static StyleConfig newTargetStyle() {
            StyleConfig config = new StyleConfig();
            config.fontName = FONT_NAME_FIXED_DEFAULT;
            config.fontHeight = FONT_HEIGHT_DEFAULT;
            config.fontColor = new XSSFColor(new Color(5, 5, 5));
            config.verticalAlignment = CENTER;
            config.horizontalAlignment = HorizontalAlignment.RIGHT;
            return config;
        }

        static StyleConfig newCommandStyle() {
            StyleConfig config = new StyleConfig();
            config.fontName = FONT_NAME_FIXED_DEFAULT;
            config.fontHeight = FONT_HEIGHT_DEFAULT;
            config.fontColor = new XSSFColor(new Color(18, 40, 74));
            config.verticalAlignment = CENTER;
            return config;
        }

        static StyleConfig newDisabledCommandStyle() {
            StyleConfig config = newCommandStyle();
            config.fontColor = new XSSFColor(new Color(5, 5, 5));
            config.strikeOut = true;
            return config;
        }

        static StyleConfig newParamStyle() {
            StyleConfig config = new StyleConfig();
            config.backgroundColor = new XSSFColor(new Color(250, 250, 250));
            config.borderColor = new XSSFColor(new Color(220, 220, 220));
            config.fontName = FONT_NAME_FIXED_DEFAULT;
            config.fontHeight = FONT_HEIGHT_PARAM;
            config.fontColor = new XSSFColor(new Color(5, 5, 5));
            config.verticalAlignment = CENTER;
            return config;
        }

        static StyleConfig newTaintedParamStyle() {
            StyleConfig config = new StyleConfig();
            config.backgroundColor = new XSSFColor(new Color(240, 240, 225));
            config.borderColor = new XSSFColor(new Color(205, 205, 205));
            config.fontName = FONT_NAME_FIXED_DEFAULT;
            config.fontHeight = FONT_HEIGHT_PARAM;
            config.fontColor = new XSSFColor(new Color(5, 5, 5));
            config.verticalAlignment = CENTER;
            return config;
        }

        static StyleConfig newScreenshotStyle() {
            StyleConfig config = new StyleConfig();
            config.backgroundColor = new XSSFColor(new Color(255, 255, 255));
            config.borderColor = new XSSFColor(new Color(195, 195, 195));
            config.fontName = FONT_NAME_FIXED_DEFAULT;
            config.fontHeight = FONT_HEIGHT_PARAM;
            config.fontColor = new XSSFColor(new Color(0, 0, 255));
            config.boldFont = true;
            config.verticalAlignment = CENTER;
            return config;
        }

        static StyleConfig newElapsedMsStyle() {
            StyleConfig config = new StyleConfig();
            config.backgroundColor = new XSSFColor(new Color(255, 255, 255));
            config.borderColor = new XSSFColor(new Color(195, 195, 195));
            config.fontHeight = FONT_HEIGHT_DEFAULT;
            config.fontColor = new XSSFColor(new Color(5, 5, 5));
            config.verticalAlignment = CENTER;
            return config;
        }

        static StyleConfig newElapsedMsBadSlaStyle() { return newFailedStyle(); }

        static StyleConfig newSuccessStyle() {
            StyleConfig config = new StyleConfig();
            config.backgroundColor = new XSSFColor(new Color(198, 239, 206));
            config.fontHeight = FONT_HEIGHT_DEFAULT;
            config.fontColor = new XSSFColor(new Color(0, 97, 0));
            config.verticalAlignment = CENTER;
            return config;
        }

        static StyleConfig newFailedStyle() {
            StyleConfig config = new StyleConfig();
            config.backgroundColor = new XSSFColor(new Color(255, 199, 206));
            config.fontHeight = FONT_HEIGHT_PARAM;
            config.fontColor = FG_FAIL;
            config.boldFont = true;
            config.verticalAlignment = CENTER;
            return config;
        }

        static StyleConfig newSkippedStyle() {
            StyleConfig config = new StyleConfig();
            config.backgroundColor = new XSSFColor(new Color(230, 230, 230));
            config.fontName = FONT_NAME_DEFAULT;
            config.fontHeight = FONT_HEIGHT_DEFAULT;
            config.fontColor = new XSSFColor(new Color(128, 128, 128));
            config.boldFont = false;
            config.verticalAlignment = CENTER;
            return config;
        }

        static StyleConfig newResultStyle() {
            StyleConfig config = new StyleConfig();
            config.fontHeight = FONT_HEIGHT_DEFAULT;
            config.verticalAlignment = CENTER;
            return config;
        }

        static StyleConfig newMsgStyle() {
            StyleConfig config = new StyleConfig();
            config.backgroundColor = new XSSFColor(new Color(236, 236, 232));
            // config.backgroundFillColor = config.backgroundColor;
            // config.backgroundFillPattern = SOLID_FOREGROUND;
            config.indention = 1;
            config.fontName = FONT_NAME_FIXED_DEFAULT;
            config.fontHeight = FONT_HEIGHT_PARAM;
            config.fontColor = new XSSFColor(new Color(90, 90, 50));
            config.verticalAlignment = CENTER;
            config.wrapText = false;
            return config;
        }

        static StyleConfig newLinkStyle() {
            StyleConfig config = new StyleConfig();
            config.fontName = FONT_NAME_FIXED_DEFAULT;
            config.fontHeight = FONT_HEIGHT_PARAM;
            config.boldFont = true;
            config.fontColor = ExcelStyleHelper.BLUE;
            config.verticalAlignment = CENTER;
            return config;
        }

        static StyleConfig newExecSummaryTitle() {
            StyleConfig config = new StyleConfig();
            config.verticalAlignment = CENTER;
            config.horizontalAlignment = HorizontalAlignment.LEFT;
            config.fontName = FONT_NAME_DEFAULT;
            config.boldFont = true;
            config.fontHeight = 14;
            config.fontColor = new XSSFColor(new Color(0, 0, 0));
            config.borderColor = new XSSFColor(new Color(208, 208, 208));
            config.borderStyle = THIN;
            config.backgroundColor = new XSSFColor(new Color(225, 236, 244));
            return config;
        }

        static StyleConfig newExecSummaryDataHeader() {
            StyleConfig config = new StyleConfig();
            config.verticalAlignment = CENTER;
            config.horizontalAlignment = HorizontalAlignment.LEFT;
            config.fontName = FONT_NAME_DEFAULT;
            config.boldFont = true;
            config.fontHeight = FONT_HEIGHT_DEFAULT;
            config.fontColor = new XSSFColor(new Color(0, 0, 0));
            config.borderColor = new XSSFColor(new Color(208, 208, 208));
            config.borderStyle = THIN;
            config.backgroundColor = new XSSFColor(new Color(172, 185, 202));
            return config;
        }

        static StyleConfig newExecSummaryDataName() {
            StyleConfig config = new StyleConfig();
            config.verticalAlignment = CENTER;
            config.horizontalAlignment = HorizontalAlignment.LEFT;
            config.fontName = FONT_NAME_FIXED_DEFAULT;
            config.fontHeight = FONT_HEIGHT_DEFAULT;
            config.fontColor = new XSSFColor(new Color(64, 64, 64));
            config.borderColor = new XSSFColor(new Color(208, 208, 208));
            config.borderStyle = THIN;
            config.backgroundColor = new XSSFColor(new Color(239, 239, 239));
            return config;
        }

        static StyleConfig newExecSummaryDataValue() {
            StyleConfig config = new StyleConfig();
            config.verticalAlignment = CENTER;
            config.horizontalAlignment = HorizontalAlignment.LEFT;
            config.fontName = FONT_NAME_FIXED_DEFAULT;
            config.fontHeight = 12;
            config.fontColor = new XSSFColor(new Color(64, 64, 64));
            config.borderStyle = null;
            config.borderColor = null;
            config.backgroundColor = null;
            return config;
        }

        static StyleConfig newExecSummaryException() {
            StyleConfig config = new StyleConfig();
            config.verticalAlignment = CENTER;
            config.horizontalAlignment = HorizontalAlignment.LEFT;
            config.fontName = FONT_NAME_FIXED_DEFAULT;
            config.fontHeight = 12;
            config.fontColor = new XSSFColor(new Color(162, 0, 1));
            config.borderColor = new XSSFColor(new Color(208, 208, 208));
            config.borderStyle = THIN;
            config.backgroundColor = new XSSFColor(new Color(240, 210, 210));
            config.wrapText = false;
            return config;
        }

        static StyleConfig newExecSummaryHeader() {
            StyleConfig config = newExecSummaryDataHeader();
            config.horizontalAlignment = HorizontalAlignment.CENTER;
            return config;
        }

        static StyleConfig newExecSummaryScenario() {
            StyleConfig config = new StyleConfig();
            config.verticalAlignment = CENTER;
            config.horizontalAlignment = HorizontalAlignment.LEFT;
            config.fontName = FONT_NAME_DEFAULT;
            config.boldFont = true;
            config.fontHeight = 12;
            config.fontColor = new XSSFColor(new Color(117, 107, 38));
            config.borderColor = new XSSFColor(new Color(208, 208, 208));
            config.borderStyle = THIN;
            config.backgroundColor = new XSSFColor(new Color(238, 235, 212));
            return config;
        }

        static StyleConfig newExecSummaryActivity() { return newExecSummaryScenario(); }

        static StyleConfig newExecSummaryTimespan() {
            StyleConfig config = new StyleConfig();
            config.verticalAlignment = CENTER;
            config.horizontalAlignment = HorizontalAlignment.CENTER;
            config.fontName = FONT_NAME_DEFAULT;
            config.fontHeight = 12;
            config.fontColor = new XSSFColor(new Color(0, 0, 0));
            config.borderColor = new XSSFColor(new Color(208, 208, 208));
            config.borderStyle = THIN;
            config.backgroundColor = null;
            return config;
        }

        static StyleConfig newExecSummaryDuration() {
            StyleConfig config = newExecSummaryTimespan();
            config.horizontalAlignment = HorizontalAlignment.RIGHT;
            return config;
        }

        static StyleConfig newExecSummaryTotal() { return newExecSummaryDuration(); }

        static StyleConfig newExecSummaryPass() { return newExecSummaryDuration(); }

        static StyleConfig newExecSummaryFail() { return newExecSummaryDuration(); }

        static StyleConfig newExecSummarySuccess() {
            StyleConfig config = newExecSummaryTimespan();
            config.horizontalAlignment = HorizontalAlignment.RIGHT;
            config.fontColor = new XSSFColor(new Color(0, 100, 1));
            config.backgroundColor = new XSSFColor(new Color(220, 250, 220));
            return config;
        }

        static StyleConfig newExecSummaryNotSuccess() {
            StyleConfig config = newExecSummaryTimespan();
            config.horizontalAlignment = HorizontalAlignment.RIGHT;
            config.boldFont = true;
            config.fontColor = new XSSFColor(new Color(162, 0, 1));
            config.backgroundColor = new XSSFColor(new Color(240, 210, 210));
            return config;
        }

        static StyleConfig newExecSummaryFinalSuccess() {
            StyleConfig config = newExecSummarySuccess();
            config.boldFont = true;
            config.borderColor = new XSSFColor(new Color(0, 0, 0));
            config.setUseSpecialTopAndDoubleBottomBorder(true);
            return config;
        }

        static StyleConfig newExecSummaryFinalNotSuccess() {
            StyleConfig config = newExecSummaryNotSuccess();
            config.boldFont = true;
            config.borderColor = new XSSFColor(new Color(0, 0, 0));
            config.setUseSpecialTopAndDoubleBottomBorder(true);
            return config;
        }

        static StyleConfig newExecSummaryFinalTotal() {
            StyleConfig config = new StyleConfig();
            config.verticalAlignment = CENTER;
            config.horizontalAlignment = HorizontalAlignment.RIGHT;
            config.fontName = FONT_NAME_FIXED_DEFAULT;
            config.fontHeight = 12;
            config.boldFont = true;
            config.fontColor = new XSSFColor(new Color(0, 0, 0));
            config.borderColor = new XSSFColor(new Color(0, 0, 0));
            config.setUseSpecialTopAndDoubleBottomBorder(true);
            config.backgroundColor = null;
            return config;
        }

        // // only additive style updates
        // private static StyleConfig newSkippedStepDescriptionStyle() {
        //     StyleConfig config = new StyleConfig();
        //     // config.backgroundColor = new XSSFColor(new Color(245, 245, 245));
        //     config.fontColor = new XSSFColor(new Color(100, 100, 100));
        //     config.fontHeight = FONT_HEIGHT_DEFAULT;
        //     config.verticalAlignment = CENTER;
        //     config.italicFont = true;
        //     config.wrapText = true;
        //     // config.borderColor = new XSSFColor(new Color(100, 100, 100));
        //     return config;
        // }

        // static StyleConfig newSettingNameStyle() {
        //     StyleConfig config = new StyleConfig();
        //     // BDD7EE
        //     config.backgroundColor = new XSSFColor(new Color(189, 215, 238));
        //     config.fontHeight = FONT_HEIGHT_DEFAULT;
        //     config.fontName = FONT_NAME_DEFAULT;
        //     // 2F75B5
        //     config.fontColor = new XSSFColor(new Color(47, 117, 181));
        //     config.boldFont = true;
        //     return config;
        // }

        // static StyleConfig newSettingValueStyle() {
        //     StyleConfig config = new StyleConfig();
        //     // F2F2F2
        //     config.backgroundColor = new XSSFColor(new Color(242, 242, 242));
        //     config.fontHeight = FONT_HEIGHT_DEFAULT;
        //     config.fontName = FONT_NAME_FIXED_DEFAULT;
        //     // 808080
        //     config.fontColor = new XSSFColor(new Color(128, 128, 128));
        //     config.boldFont = true;
        //     config.italicFont = true;
        //     return config;
        // }

        // static StyleConfig newJenkinsRefLabelStyle() {
        //     StyleConfig config = new StyleConfig();
        //     config.backgroundColor = new XSSFColor(new Color(216, 229, 188));
        //     config.horizontalAlignment = HorizontalAlignment.LEFT;
        //     config.verticalAlignment = CENTER;
        //     config.borderColor = new XSSFColor(new Color(184, 208, 138));
        //     config.borderStyle = MEDIUM;
        //     config.fontName = FONT_NAME_FIXED_DEFAULT;
        //     config.fontHeight = FONT_HEIGHT_PARAM;
        //     config.fontColor = new XSSFColor(new Color(99, 131, 46));
        //     return config;
        // }

        // static StyleConfig newJenkinsRefLinkStyle() {
        //     StyleConfig config = new StyleConfig();
        //     config.backgroundColor = new XSSFColor(new Color(230, 238, 214));
        //     config.horizontalAlignment = HorizontalAlignment.LEFT;
        //     config.verticalAlignment = CENTER;
        //     config.borderColor = new XSSFColor(new Color(184, 208, 138));
        //     config.borderStyle = MEDIUM;
        //     config.fontName = FONT_NAME_FIXED_DEFAULT;
        //     config.fontHeight = FONT_HEIGHT_PARAM;
        //     config.fontColor = new XSSFColor(new Color(83, 141, 213));
        //     config.boldFont = true;
        //     return config;
        // }

        // static StyleConfig newJenkinsRefParamStyle() {
        //     StyleConfig config = new StyleConfig();
        //     config.backgroundColor = new XSSFColor(new Color(230, 238, 214));
        //     config.horizontalAlignment = HorizontalAlignment.LEFT;
        //     config.verticalAlignment = CENTER;
        //     config.indention = 1;
        //     config.wrapText = true;
        //     config.borderColor = new XSSFColor(new Color(184, 208, 138));
        //     config.borderStyle = MEDIUM;
        //     config.fontName = FONT_NAME_FIXED_DEFAULT;
        //     config.fontHeight = FONT_HEIGHT_PARAM;
        //     config.fontColor = new XSSFColor(new Color(64, 64, 64));
        //     config.boldFont = true;
        //     return config;
        // }

        // static StyleConfig newLogLabelStyle() {
        //     StyleConfig config = new StyleConfig();
        //     config.backgroundColor = new XSSFColor(new Color(240, 240, 220));
        //     config.fontHeight = FONT_HEIGHT_DEFAULT;
        //     return config;
        // }

        // static StyleConfig newLogLinkStyle() {
        //     StyleConfig config = new StyleConfig();
        //     config.backgroundColor = new XSSFColor(new Color(240, 240, 220));
        //     config.boldFont = true;
        //     config.fontHeight = FONT_HEIGHT_PARAM;
        //     config.fontColor = new XSSFColor(new Color(0, 0, 255));
        //     return config;
        // }

        // static StyleConfig newUnsupportedCommandStyle() {
        //     StyleConfig config = new StyleConfig();
        //     config.italicFont = true;
        //     config.boldFont = true;
        //     config.fontColor = FG_FAIL;
        //     return config;
        // }
    }

    private ExcelConfig() {}

    public static void fixCellWidth(XSSFSheet sheet, XSSFCell cell, int columnIndex, int charWidthFactor) {
        int actualCommandCellWidth = sheet.getColumnWidth(columnIndex);

        String cellValue = Excel.getCellValue(cell);
        int commandLength = StringUtils.length(cellValue);
        if (StringUtils.contains(cellValue, "\n")) {
            commandLength = Arrays.stream(StringUtils.split(cellValue, "\n"))
                                  .map(String::length)
                                  .sorted()
                                  .max(Integer::compareTo)
                                  .orElse(commandLength);
            ExcelStyleHelper.handleTextWrap(cell);
        }
        int expectedCommandCellWidth = commandLength * charWidthFactor;

        if (actualCommandCellWidth < expectedCommandCellWidth && expectedCommandCellWidth < MAX_CELL_WIDTH) {
            sheet.setColumnWidth(columnIndex, expectedCommandCellWidth);
        }
    }
}
