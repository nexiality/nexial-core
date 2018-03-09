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
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.nexial.core.excel.Excel.Worksheet;

import static org.apache.poi.ss.usermodel.BorderStyle.MEDIUM;
import static org.apache.poi.ss.usermodel.BorderStyle.THIN;
import static org.apache.poi.ss.usermodel.FillPatternType.NO_FILL;
import static org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND;
import static org.apache.poi.ss.usermodel.VerticalAlignment.CENTER;
import static org.apache.poi.xssf.usermodel.extensions.XSSFCellBorder.BorderSide.*;

/**
 *
 */
public class ExcelConfig {

    // plan
    // public static final ExcelAddress ADDR_PLAN_HEADER_SUMMARY = new ExcelAddress("A1:A1");
    // public static final ExcelAddress ADDR_PLAN_HEADER_AUTHOR = new ExcelAddress("F1:F1");
    // public static final ExcelAddress ADDR_PLAN_HEADER_JIRA_OVERRIDE = new ExcelAddress("G1:G1");
    // public static final ExcelAddress ADDR_PLAN_HEADER_ZEPHYR_OVERRIDE = new ExcelAddress("H1:H1");
    // public static final ExcelAddress ADDR_PLAN_HEADER_NOTIFICATION_OVERRIDE = new ExcelAddress("I1:I1");
    public static final ExcelAddress ADDR_PLAN_HEADER_EXEC_SUMMARY_HEADER = new ExcelAddress("K1");
    // public static final ExcelAddress ADDR_PLAN_SUMMARY = new ExcelAddress("A2:A2");
    // public static final ExcelAddress ADDR_PLAN_AUTHOR = new ExcelAddress("F2:F2");
    // public static final ExcelAddress ADDR_PLAN_JIRA_OVERRIDE = new ExcelAddress("G2:G2");
    // public static final ExcelAddress ADDR_PLAN_ZEPHYR_OVERRIDE = new ExcelAddress("H2:H2");
    // public static final ExcelAddress ADDR_PLAN_NOTIFICATION_OVERRIDE = new ExcelAddress("I2:I2");
    public static final ExcelAddress ADDR_PLAN_HEADER_SEQUENCE1 = new ExcelAddress("A1:A1");
    public static final ExcelAddress ADDR_PLAN_HEADER_SEQUENCE2 = new ExcelAddress("F1:H1");
    // public static final ExcelAddress ADDR_PLAN_EXEC_SUMMARY = new ExcelAddress("K2");
    public static final String PLAN_HEADER_SUMMARY = "summary";
    public static final String PLAN_HEADER_AUTHOR = "author";
    public static final String PLAN_HEADER_FEATURE_OVERRIDE = "story / feature";
    public static final String PLAN_HEADER_TESTREF_OVERRIDE = "test id";
    // public static final String PLAN_HEADER_NOTIFICATION_OVERRIDE = "notification override";
    public static final List<String> PLAN_HEADER_SEQUENCE = Arrays.asList(PLAN_HEADER_SUMMARY,
                                                                          PLAN_HEADER_AUTHOR,
                                                                          PLAN_HEADER_FEATURE_OVERRIDE,
                                                                          PLAN_HEADER_TESTREF_OVERRIDE);

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
    public static final int COL_IDX_PLAN_ELAPSED_MS = 'K' - 'A';
    public static final int COL_IDX_PLAN_RESULT = 'L' - 'A';
    public static final int COL_IDX_PLAN_REASON = 'M' - 'A';

    // test script
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
    public static final List<String> HEADER_SCENARIO_INFO_V1 =
        Arrays.asList("description","project","release","jira","zephyr","author","execution summary");

    public static final ExcelAddress ADDR_SCENARIO_EXEC_SUMMARY_HEADER = new ExcelAddress("L1");
    public static final ExcelAddress ADDR_SCENARIO_EXEC_SUMMARY = new ExcelAddress("L2");
    public static final String HEADER_EXEC_SUMMARY = "execution summary";

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

    public static final ExcelAddress ADDR_COMMAND_START = new ExcelAddress("C5:D5");
    public static final ExcelAddress ADDR_PARAMS_START = new ExcelAddress("E5:I5");

    //
    // legacy stuff below
    //

    //public static final String AGENDA_CMD_COL = "C";
    //public static final String AGENDA_END_COL = "O";

    public static final ExcelAddress RESULT_SUMMARY_ADDR = new ExcelAddress("L2");
    public static final ExcelAddress META_ADDR = new ExcelAddress("A1:E4");

    // agenda
    public static final int ALPHABET_COUNT = 'Z' - 'A' + 1;
    public static final short CELL_HEIGHT_DEPRECATED = 440;

    // included data driver
    //public static final String CMD_INCL = "@include(";
    //public static final String CMD_INCL_SET = "@includeSet(";

    // test data
    //public static final String DATA_WORKSHEET_NAME = "#data";
    //public static final String INCL_SET_START_ADDR = "A2";
    //public static final String INCL_SET_START_HEADER = "A1";
    public static final int LOG_START_ROW = 2;

    // agenda metadata
    public static final String MSG_DEPRECATED = "DEPRECATED ";
    public static final String MSG_FAIL = "FAIL ";
    public static final String MSG_PASS = "PASS ";
    public static final String MSG_SCREENCAPTURE = "Click here";
    public static final String MSG_SKIPPED = "SKIPPED ";
    public static final String MSG_WARN = "WARN ";
    public static final String REGEX_INCL = "\\@include\\(\\s*(.+)\\s*\\,\\s*(.+)\\s*\\)";
    public static final String REGEX_INCL_SET = "\\@includeSet\\(\\s*(.+)\\s*\\,\\s*(.+)\\s*\\)";

    public static final String STYLE_TEST_CASE = "TEST_CASE";
    public static final String TESTDATA_END_COL = "B";
    public static final String TESTDATA_FILENAME = "filename";
    public static final String TESTDATA_HOSTNAME = "hostname";
    public static final String TESTDATA_OSNAME = "osname";
    public static final String TESTDATA_PROJECT = "project";
    public static final String TESTDATA_REGEX_FILEPATH = "\\w\\:[\\\\\\/][\\w]+[\\\\\\/]([\\w]+)[\\\\\\/].*";
    public static final String TESTDATA_START_COL = "A";
    public static final int TESTDATA_START_ROW = 2;
    public static final String TESTDATA_TESTSUITE = "testsuite";

    // test data
    public static final int TESTHEADER_START_ROW = 1;

    // style names
    public static final String STYLE_COMMAND = "COMMAND";
    public static final String STYLE_DESCRIPTION = "DESCRIPTION";
    public static final String STYLE_ELAPSED_MS = "ELAPSED_MS";
    public static final String STYLE_ELAPSED_MS_BAD_SLA = "ELAPSED_MS_BAD_SLA";
    public static final String STYLE_FAILED_RESULT = "FAILED_RESULT";
    public static final String STYLE_JENKINS_REF_LABEL = "JENKINS_REF_LABEL";
    public static final String STYLE_JENKINS_REF_LINK = "JENKINS_REF_LINK";
    public static final String STYLE_JENKINS_REF_PARAM = "JENKINS_REF_PARAM";
    public static final String STYLE_MESSAGE = "MESSAGE_STYLE";
    public static final String STYLE_PARAM = "PARAM";
    public static final String STYLE_TAINTED_PARAM = "TAINTED_PARAM";
    public static final String STYLE_SCREENSHOT = "SCREENSHOT";
    public static final String STYLE_LINK = "LINK";
    public static final String STYLE_SKIPPED_RESULT = "SKIPPED_STYLE";
    public static final String STYLE_SUCCESS_RESULT = "SUCCESS_RESULT";

    // color
    public static final XSSFColor WHITE = new XSSFColor(new Color(255, 255, 255));
    public static final XSSFColor YELLOW = new XSSFColor(new Color(255, 255, 0));
    public static final XSSFColor GREEN = new XSSFColor(new Color(0, 255, 0));
    public static final XSSFColor BLACK = new XSSFColor(new Color(1, 1, 1));
    public static final XSSFColor BLUE = new XSSFColor(new Color(0, 0, 255));
    public static final XSSFColor INDIGO = new XSSFColor(new Color(0, 255, 255));
    public static final XSSFColor ORANGE = new XSSFColor(new Color(255, 128, 0));
    public static final XSSFColor PURPLE = new XSSFColor(new Color(255, 0, 255));
    public static final XSSFColor RED = new XSSFColor(new Color(255, 0, 0));
    public static final double DEF_CHAR_WIDTH = 24.7;

    public static class StyleConfig {
        //public static final XSSFColor FG_NORMAL = new XSSFColor(new Color(10, 10, 10));
        //public static final XSSFColor BORDERCOLOR_DEFAULT = new XSSFColor(new Color(195, 195, 195));

        public static final String FONT_NAME_DEFAULT = "Tahoma";
        public static final String FONT_NAME_FIXED_DEFAULT = "Consolas";

        public static final short FONT_HEIGHT_DEFAULT = (short) 11;
        public static final XSSFColor FG_FAIL = new XSSFColor(new Color(156, 0, 6));

        //public static final StyleConfig LOG_LINK = newLogLinkStyle();
        public static final StyleConfig UNSUPPORTED_COMMAND = newUnsupportedCommandStyle();
        public static final StyleConfig ELAPSED_MS_BAD_SLA = newElapsedMsBadSlaStyle();
        public static final StyleConfig FAILED = newFailedStyle();
        public static final StyleConfig TESTCASE = newTestCaseStyle();
        public static final StyleConfig DESCRIPTION = newDescriptionStyl();
        public static final StyleConfig SCREENSHOT = newScreenshotStyle();
        public static final StyleConfig ELAPSED_MS = newElapsedMsStyle();
        public static final StyleConfig SUCCESS = newSuccessStyle();
        // public static final StyleConfig LINK = newLogLinkStyle();
        //public static final StyleConfig LOG_LABEL = newLogLabelStyle();
        public static final StyleConfig RESULT = newResultStyle();
        public static final StyleConfig SKIPPED = newSkippedStyle();
        public static final StyleConfig LINK = newLinkStyle();
        public static final StyleConfig COMMAND = newCommandStyle();
        public static final StyleConfig PARAM = newParamStyle();
        public static final StyleConfig TAINTED_PARAM = newTaintedParamStyle();
        public static final StyleConfig MSG = newMsgStyle();
        public static final StyleConfig DEPRECATED = newDeprecatedStyle();
        public static final StyleConfig JENKINS_REF_LABEL = newJenkinsRefLabelStyle();
        public static final StyleConfig JENKINS_REF_LINK = newJenkinsRefLinkStyle();
        public static final StyleConfig JENKINS_REF_PARAM = newJenkinsRefParamStyle();

        public static final StyleConfig SETTING_NAME = newSettingNameStyle();
        public static final StyleConfig SETTING_VALUE = newSettingValueStyle();
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

        public boolean isUseSpecialTopAndDoubleBottomBorder() { return useSpecialTopAndDoubleBottomBorder; }

        public void setUseSpecialTopAndDoubleBottomBorder(boolean useSpecialTopAndDoubleBottomBorder) {
            this.useSpecialTopAndDoubleBottomBorder = useSpecialTopAndDoubleBottomBorder;
        }

        private static StyleConfig newTestCaseStyle() {
            StyleConfig config = new StyleConfig();
            config.backgroundColor = new XSSFColor(new Color(230, 239, 215));
            config.borderColor = new XSSFColor(new Color(200, 180, 180));
            config.fontHeight = FONT_HEIGHT_DEFAULT;
            config.fontColor = new XSSFColor(new Color(62, 81, 31));
            config.boldFont = true;
            config.verticalAlignment = CENTER;
            return config;
        }

        private static StyleConfig newSettingNameStyle() {
            StyleConfig config = new StyleConfig();
            // BDD7EE
            config.backgroundColor = new XSSFColor(new Color(189, 215, 238));
            config.fontHeight = FONT_HEIGHT_DEFAULT;
            config.fontName = FONT_NAME_DEFAULT;
            // 2F75B5
            config.fontColor = new XSSFColor(new Color(47, 117, 181));
            config.boldFont = true;
            return config;
        }

        private static StyleConfig newPredefTestDataNameStyle() {
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

        private static StyleConfig newTestDataNameStyle() {
            StyleConfig config = new StyleConfig();
            // D9D9D9
            config.backgroundColor = new XSSFColor(new Color(217, 217, 217));
            config.fontHeight = FONT_HEIGHT_DEFAULT;
            config.fontName = FONT_NAME_DEFAULT;
            config.fontColor = new XSSFColor(new Color(0, 0, 0));
            config.boldFont = true;
            return config;
        }

        private static StyleConfig newSettingValueStyle() {
            StyleConfig config = new StyleConfig();
            // F2F2F2
            config.backgroundColor = new XSSFColor(new Color(242, 242, 242));
            config.fontHeight = FONT_HEIGHT_DEFAULT;
            config.fontName = FONT_NAME_FIXED_DEFAULT;
            // 808080
            config.fontColor = new XSSFColor(new Color(128, 128, 128));
            config.boldFont = true;
            config.italicFont = true;
            return config;
        }

        private static StyleConfig newTestDataValueStyle() {
            StyleConfig config = new StyleConfig();
            //config.backgroundColor = new XSSFColor(new Color(255, 255, 255));
            config.fontHeight = FONT_HEIGHT_DEFAULT;
            config.fontName = FONT_NAME_FIXED_DEFAULT;
            config.fontColor = new XSSFColor(new Color(0, 0, 0));
            config.boldFont = false;
            return config;
        }

        private static StyleConfig newCommandStyle() {
            StyleConfig config = new StyleConfig();
            //config.backgroundColor = new XSSFColor(new Color(221, 232, 247));
            //config.borderColor = new XSSFColor(new Color(40, 40, 40));
            //config.borderColor = new XSSFColor(new Color(220, 220, 220));
            config.fontName = FONT_NAME_FIXED_DEFAULT;
            config.fontHeight = FONT_HEIGHT_DEFAULT;
            config.fontColor = new XSSFColor(new Color(18, 40, 74));
            config.verticalAlignment = CENTER;
            return config;
        }

        private static StyleConfig newLinkStyle() {
            StyleConfig config = new StyleConfig();
            config.fontName = FONT_NAME_FIXED_DEFAULT;
            config.fontHeight = (short) 10;
            config.boldFont = true;
            config.fontColor = BLUE;
            config.verticalAlignment = CENTER;
            return config;
        }

        private static StyleConfig newDescriptionStyl() {
            StyleConfig config = new StyleConfig();
            //config.backgroundColor = new XSSFColor(new Color(230, 244, 248));
            //config.borderColor = new XSSFColor(new Color(40, 40, 40));
            config.fontHeight = FONT_HEIGHT_DEFAULT;
            config.fontColor = new XSSFColor(new Color(40, 115, 137));
            config.wrapText = true;
            config.verticalAlignment = CENTER;
            return config;
        }

        private static StyleConfig newParamStyle() {
            StyleConfig config = new StyleConfig();
            config.backgroundColor = new XSSFColor(new Color(250, 250, 250));
            config.borderColor = new XSSFColor(new Color(220, 220, 220));
            config.fontName = FONT_NAME_FIXED_DEFAULT;
            config.fontHeight = (short) 10;
            config.fontColor = new XSSFColor(new Color(5, 5, 5));
            config.verticalAlignment = CENTER;
            return config;
        }

        private static StyleConfig newTaintedParamStyle() {
            StyleConfig config = new StyleConfig();
            config.backgroundColor = new XSSFColor(new Color(240, 240, 225));
            config.borderColor = new XSSFColor(new Color(205, 205, 205));
            config.fontName = FONT_NAME_FIXED_DEFAULT;
            config.fontHeight = (short) 10;
            config.fontColor = new XSSFColor(new Color(5, 5, 5));
            config.verticalAlignment = CENTER;
            return config;
        }

        private static StyleConfig newScreenshotStyle() {
            StyleConfig config = new StyleConfig();
            config.backgroundColor = new XSSFColor(new Color(255, 255, 255));
            config.borderColor = new XSSFColor(new Color(195, 195, 195));
            config.fontName = FONT_NAME_FIXED_DEFAULT;
            config.fontHeight = (short) 10;
            config.fontColor = new XSSFColor(new Color(0, 0, 255));
            config.boldFont = true;
            config.verticalAlignment = CENTER;
            return config;
        }

        private static StyleConfig newElapsedMsStyle() {
            StyleConfig config = new StyleConfig();
            config.backgroundColor = new XSSFColor(new Color(255, 255, 255));
            config.borderColor = new XSSFColor(new Color(195, 195, 195));
            config.fontHeight = FONT_HEIGHT_DEFAULT;
            config.fontColor = new XSSFColor(new Color(5, 5, 5));
            config.verticalAlignment = CENTER;
            return config;
        }

        private static StyleConfig newElapsedMsBadSlaStyle() { return newFailedStyle(); }

        private static StyleConfig newMsgStyle() {
            StyleConfig config = new StyleConfig();
            config.backgroundColor = new XSSFColor(new Color(236, 236, 232));
            config.backgroundFillColor = config.backgroundColor;
            config.backgroundFillPattern = SOLID_FOREGROUND;
            config.indention = 1;
            config.fontName = FONT_NAME_FIXED_DEFAULT;
            config.fontHeight = (short) 10;
            config.fontColor = new XSSFColor(new Color(90, 90, 50));
            config.verticalAlignment = CENTER;
            config.wrapText = true;
            return config;
        }

        private static StyleConfig newSuccessStyle() {
            StyleConfig config = new StyleConfig();
            config.backgroundColor = new XSSFColor(new Color(198, 239, 206));
            config.fontHeight = FONT_HEIGHT_DEFAULT;
            config.fontColor = new XSSFColor(new Color(0, 97, 0));
            config.verticalAlignment = CENTER;
            return config;
        }

        private static StyleConfig newFailedStyle() {
            StyleConfig config = new StyleConfig();
            config.backgroundColor = new XSSFColor(new Color(255, 199, 206));
            config.fontHeight = (short) 10;
            config.fontColor = FG_FAIL;
            config.boldFont = true;
            config.verticalAlignment = CENTER;
            return config;
        }

        private static StyleConfig newSkippedStyle() {
            StyleConfig config = new StyleConfig();
            //config.backgroundColor = new XSSFColor(new Color(227, 227, 227));
            //config.indention = 1;
            config.fontName = FONT_NAME_DEFAULT;
            config.fontHeight = (short) 10;
            config.fontColor = new XSSFColor(new Color(128, 128, 128));
            config.boldFont = false;
            config.verticalAlignment = CENTER;
            return config;
        }

        private static StyleConfig newDeprecatedStyle() {
            StyleConfig config = new StyleConfig();
            config.backgroundColor = new XSSFColor(new Color(110, 30, 30));
            config.verticalAlignment = CENTER;
            config.wrapText = true;
            config.fontName = FONT_NAME_FIXED_DEFAULT;
            config.fontHeight = (short) 10;
            config.fontColor = new XSSFColor(new Color(210, 210, 210));
            return config;
        }

        private static StyleConfig newJenkinsRefLabelStyle() {
            StyleConfig config = new StyleConfig();
            config.backgroundColor = new XSSFColor(new Color(216, 229, 188));
            config.horizontalAlignment = HorizontalAlignment.LEFT;
            config.verticalAlignment = CENTER;
            config.borderColor = new XSSFColor(new Color(184, 208, 138));
            config.borderStyle = MEDIUM;
            config.fontName = FONT_NAME_FIXED_DEFAULT;
            config.fontHeight = (short) 10;
            config.fontColor = new XSSFColor(new Color(99, 131, 46));
            return config;
        }

        private static StyleConfig newJenkinsRefLinkStyle() {
            StyleConfig config = new StyleConfig();
            config.backgroundColor = new XSSFColor(new Color(230, 238, 214));
            config.horizontalAlignment = HorizontalAlignment.LEFT;
            config.verticalAlignment = CENTER;
            config.borderColor = new XSSFColor(new Color(184, 208, 138));
            config.borderStyle = MEDIUM;
            config.fontName = FONT_NAME_FIXED_DEFAULT;
            config.fontHeight = (short) 10;
            config.fontColor = new XSSFColor(new Color(83, 141, 213));
            config.boldFont = true;
            return config;
        }

        private static StyleConfig newJenkinsRefParamStyle() {
            StyleConfig config = new StyleConfig();
            config.backgroundColor = new XSSFColor(new Color(230, 238, 214));
            config.horizontalAlignment = HorizontalAlignment.LEFT;
            config.verticalAlignment = CENTER;
            config.indention = 1;
            config.wrapText = true;
            config.borderColor = new XSSFColor(new Color(184, 208, 138));
            config.borderStyle = MEDIUM;
            config.fontName = FONT_NAME_FIXED_DEFAULT;
            config.fontHeight = (short) 10;
            config.fontColor = new XSSFColor(new Color(64, 64, 64));
            config.boldFont = true;
            return config;
        }

        private static StyleConfig newLogLabelStyle() {
            StyleConfig config = new StyleConfig();
            config.backgroundColor = new XSSFColor(new Color(240, 240, 220));
            config.fontHeight = FONT_HEIGHT_DEFAULT;
            return config;
        }

        private static StyleConfig newLogLinkStyle() {
            StyleConfig config = new StyleConfig();
            config.backgroundColor = new XSSFColor(new Color(240, 240, 220));
            config.boldFont = true;
            config.fontHeight = (short) 10;
            config.fontColor = new XSSFColor(new Color(0, 0, 255));
            return config;
        }

        private static StyleConfig newResultStyle() {
            StyleConfig config = new StyleConfig();
            config.fontHeight = FONT_HEIGHT_DEFAULT;
            config.verticalAlignment = CENTER;
            return config;
        }

        private static StyleConfig newUnsupportedCommandStyle() {
            StyleConfig config = new StyleConfig();
            config.italicFont = true;
            config.boldFont = true;
            config.fontColor = FG_FAIL;
            return config;
        }

        private static StyleConfig newExecSummaryTitle() {
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

        private static StyleConfig newExecSummaryDataHeader() {
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

        private static StyleConfig newExecSummaryDataName() {
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

        private static StyleConfig newExecSummaryDataValue() {
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

        private static StyleConfig newExecSummaryException() {
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

        private static StyleConfig newExecSummaryHeader() {
            StyleConfig config = newExecSummaryDataHeader();
            config.horizontalAlignment = HorizontalAlignment.CENTER;
            return config;
        }

        private static StyleConfig newExecSummaryScenario() {
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

        private static StyleConfig newExecSummaryActivity() { return newExecSummaryScenario(); }

        private static StyleConfig newExecSummaryTimespan() {
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

        private static StyleConfig newExecSummaryDuration() {
            StyleConfig config = newExecSummaryTimespan();
            config.horizontalAlignment = HorizontalAlignment.RIGHT;
            return config;
        }

        private static StyleConfig newExecSummaryTotal() { return newExecSummaryDuration(); }

        private static StyleConfig newExecSummaryPass() { return newExecSummaryDuration(); }

        private static StyleConfig newExecSummaryFail() { return newExecSummaryDuration(); }

        private static StyleConfig newExecSummarySuccess() {
            StyleConfig config = newExecSummaryTimespan();
            config.horizontalAlignment = HorizontalAlignment.RIGHT;
            config.fontColor = new XSSFColor(new Color(0, 100, 1));
            config.backgroundColor = new XSSFColor(new Color(220, 250, 220));
            return config;
        }

        private static StyleConfig newExecSummaryNotSuccess() {
            StyleConfig config = newExecSummaryTimespan();
            config.horizontalAlignment = HorizontalAlignment.RIGHT;
            config.boldFont = true;
            config.fontColor = new XSSFColor(new Color(162, 0, 1));
            config.backgroundColor = new XSSFColor(new Color(240, 210, 210));
            return config;
        }

        private static StyleConfig newExecSummaryFinalSuccess() {
            StyleConfig config = newExecSummarySuccess();
            config.boldFont = true;
            config.borderColor = new XSSFColor(new Color(0, 0, 0));
            config.setUseSpecialTopAndDoubleBottomBorder(true);
            return config;
        }

        private static StyleConfig newExecSummaryFinalNotSuccess() {
            StyleConfig config = newExecSummaryNotSuccess();
            config.boldFont = true;
            config.borderColor = new XSSFColor(new Color(0, 0, 0));
            config.setUseSpecialTopAndDoubleBottomBorder(true);
            return config;
        }

        private static StyleConfig newExecSummaryFinalTotal() {
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
    }

    public static class StyleDecorator {
        private StyleDecorator() {}

        public static XSSFCellStyle generate(XSSFWorkbook workbook, StyleConfig config) {
            assert workbook != null;
            assert config != null;

            return decorate(workbook.createCellStyle(), workbook.createFont(), config);
        }

        public static XSSFCellStyle generate(Worksheet worksheet, StyleConfig config) {
            assert worksheet != null;
            assert config != null;

            return decorate(worksheet, config);
        }

        public static XSSFCellStyle decorate(Worksheet worksheet, StyleConfig config) {
            assert worksheet != null;
            assert config != null;

            return decorate(worksheet.newCellStyle(), worksheet.createFont(), config);
        }

        public static XSSFCellStyle decorate(XSSFCellStyle style, XSSFFont font, StyleConfig config) {
            assert style != null;
            assert font != null;
            assert config != null;

            XSSFColor backgroundColor = config.getBackgroundColor();
            if (backgroundColor != null) { style.setFillBackgroundColor(backgroundColor); }

            FillPatternType backgroundFillPattern = config.getBackgroundFillPattern();
            if (backgroundFillPattern == SOLID_FOREGROUND || backgroundFillPattern == NO_FILL) {
                if (backgroundColor != null) {
                    style.setFillForegroundColor(backgroundColor);
                    style.setFillPattern(SOLID_FOREGROUND);
                }
            } else {
                XSSFColor backgroundFillColor = config.getBackgroundFillColor();
                if (backgroundFillColor != null) {
                    style.setFillForegroundColor(backgroundFillColor);
                    style.setFillPattern(backgroundFillPattern);
                }
            }

            short indention = config.getIndention();
            if (indention > 0) { style.setIndention(indention); }

            XSSFColor borderColor = config.getBorderColor();
            if (borderColor != null) {
                if (config.isUseSpecialTopAndDoubleBottomBorder()) {
                    style.setBorderTop(BorderStyle.THIN);
                    style.setBorderBottom(BorderStyle.DOUBLE);
                    style.setBorderLeft(BorderStyle.NONE);
                    style.setBorderRight(BorderStyle.NONE);
                    style.setBorderColor(TOP, borderColor);
                    style.setBorderColor(BOTTOM, borderColor);
                } else {
                    BorderStyle borderStyle = config.getBorderStyle();
                    style.setBorderTop(borderStyle);
                    style.setBorderBottom(borderStyle);
                    style.setBorderLeft(borderStyle);
                    style.setBorderRight(borderStyle);
                    style.setBorderColor(TOP, borderColor);
                    style.setBorderColor(BOTTOM, borderColor);
                    style.setBorderColor(LEFT, borderColor);
                    style.setBorderColor(RIGHT, borderColor);
                }
            }

            HorizontalAlignment horizontalAlignment = config.getHorizontalAlignment();
            if (horizontalAlignment != null) { style.setAlignment(horizontalAlignment); }

            VerticalAlignment verticalAlignment = config.getVerticalAlignment();
            if (verticalAlignment != null) { style.setVerticalAlignment(verticalAlignment); }

            if (config.isWrapText()) { style.setWrapText(true); }

            String fontName = config.getFontName();
            if (StringUtils.isNotBlank(fontName)) {
                font.setFontName(fontName);
                font.setFontHeightInPoints(config.getFontHeight());
                font.setColor(config.getFontColor());
                font.setBold(config.isBoldFont());
                font.setItalic(config.isItalicFont());
                font.setUnderline(config.getUnderlineStyle());
                style.setFont(font);
            }

            return style;
        }

        public static XSSFFont newDefaultFont(XSSFWorkbook workbook) {
            XSSFFont font = workbook.createFont();
            font.setFontName("Consolas");
            font.setFontHeight(9);
            return font;
        }

        public static XSSFFont newDefaultHeaderFont(XSSFWorkbook workbook) {
            XSSFFont font = newDefaultFont(workbook);
            font.setBold(true);
            font.setColor(new XSSFColor(new Color(217, 217, 217)));
            return font;
        }

        public static XSSFCellStyle newDefaultHeaderStyle(XSSFWorkbook workbook) {
            XSSFCellStyle style = workbook.createCellStyle();
            style.setFillForegroundColor(new XSSFColor(new Color(64, 64, 64)));
            style.setFillPattern(SOLID_FOREGROUND);
            style.setFont(newDefaultHeaderFont(workbook));
            return style;
        }
    }

    private ExcelConfig() {}
}
