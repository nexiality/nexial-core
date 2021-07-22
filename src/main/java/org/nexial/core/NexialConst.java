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

package org.nexial.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.nexial.commons.utils.FileUtil;
import org.nexial.commons.utils.RegexUtils;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.excel.ExcelConfig;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.ExecutionDefinition;
import org.nexial.core.model.ExecutionEvent;
import org.nexial.core.model.TestProject;
import org.nexial.core.utils.ConsoleUtils;
import org.nexial.core.utils.ExecUtils;

import javax.validation.constraints.NotNull;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.math.RoundingMode;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.*;

import static java.awt.Color.*;
import static java.awt.image.BufferedImage.TYPE_INT_ARGB;
import static java.awt.image.BufferedImage.TYPE_INT_RGB;
import static java.io.File.separator;
import static java.math.RoundingMode.*;
import static javax.naming.Context.*;
import static org.apache.commons.lang3.SystemUtils.*;
import static org.nexial.core.NexialConst.AwsSettings.*;
import static org.nexial.core.NexialConst.CommonColor.COLOR_NAMES;
import static org.nexial.core.NexialConst.Data.*;
import static org.nexial.core.NexialConst.Doc.DOCUMENTATION_URL;
import static org.nexial.core.NexialConst.Exec.*;
import static org.nexial.core.NexialConst.Image.OPT_IMAGE_DIFF_COLOR;
import static org.nexial.core.NexialConst.Integration.MAIL_PREFIX;
import static org.nexial.core.NexialConst.Iteration.*;
import static org.nexial.core.NexialConst.Project.USER_NEXIAL_HOME;
import static org.nexial.core.NexialConst.Web.NS_BROWSER;
import static org.nexial.core.NexialConst.Web.NS_WEB;
import static org.nexial.core.NexialConst.Ws.WS_JSON_CONTENT_TYPE;
import static org.nexial.core.SystemVariables.*;
import static org.nexial.core.model.ExecutionEvent.*;

/**
 * constants
 */
public final class NexialConst {
    // @formatter:off

    public static final String DATE_FORMAT_NOW = "yyyy-MM-dd-HH-mm-ss.S";
    public static final String COOKIE_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss ZZZ";
    public static final String COOKIE_DATE_FORMAT2 = "EEE, dd-MMM-yyyy HH:mm:ss ZZZ";
    public static final DateFormat DF_TIMESTAMP = new SimpleDateFormat("yyyyMMdd_HHmmss");
    public static final String STD_DATE_FORMAT = "MM/dd/yyyy HH:mm:ss";
    public static final String STD_JUST_DATE_FORMAT = "MM/dd/yyyy";
    public static final String EPOCH = "epoch";
    public static final String HUMAN_DATE = "informal";
    public static final String TIME_IN_TENS = "base10time";
    public static final List<String> ALIAS_NOW = Arrays.asList("now", "right now", "today", "rightnow");

    public static final String RATE_FORMAT = "{0,number,0.00%}";

    public static final int DEF_SLEEP_MS = 250;
    public static final int MIN_STABILITY_WAIT_MS = 400;
    public static final int LAUNCHER_THREAD_COMPLETION_WAIT_MS = 1000;
    // public static final int ELEM_PRESENT_WAIT_MS = 1200;
    // public static final int MIN_LOADING_WAIT_MS = 50;
    public static final long ONEDAY = 24 * 60 * 60 * 1000;
    public static final long THIRTYDAYS = ONEDAY * 30;
    public static final long ONEYEAR = ONEDAY * 365;
    public static final long MS_UNDEFINED = -1;
    public static final int UNDEFINED_INT_DATA = -1;
    public static final double UNDEFINED_DOUBLE_DATA = -1;

    public static final String OPT_SPRING_XML = "nexial.spring.config";
    public static final String DEF_SPRING_XML = "/nexial.xml";
    public static final String ENV_NEXIAL_LIB = "NEXIAL_LIB";
    public static final String ENV_NEXIAL_HOME = "NEXIAL_HOME";
    public static final String TEMP = StringUtils.appendIfMissing(System.getProperty("java.io.tmpdir"), separator);
    public static final String USERNAME = System.getProperty("user.name");

    // predefined variables/switches
    public static final String NAMESPACE = "nexial.";
    public static final String OPT_OUT_DIR = NAMESPACE + "outBase";
    public static final String OPT_PLAN_DIR = NAMESPACE + "planBase";
    public static final String OPT_DATA_DIR = NAMESPACE + "dataBase";
    public static final String OPT_SCRIPT_DIR = NAMESPACE + "scriptBase";
    public static final String OPT_PROJECT_BASE = NAMESPACE + "projectBase";
    public static final String OPT_PROJECT_NAME = NAMESPACE + "project";

    public static final String SUBPLANS_OMITTED = NAMESPACE + "subplansOmitted";
    public static final String SUBPLANS_INCLUDED = NAMESPACE + "subplansIncluded";

    // predefined subdirectories
    public static final String SUBDIR_LOGS = "logs";
    public static final String SUBDIR_CAPTURES = "captures";

    // special System variable to allow user-defined output directory for ALL executions, except those explicitly
    // stated as commandline argument during execution (i.e. -output ...)
    // this System variable is being set up in nexial.sh|cmd upon detecting an Environment variable named as
    // `NEXIAL_OUTPUT`
    public static final String OPT_DEF_OUT_DIR = NAMESPACE + "defaultOutBase";

    // testcase specific
    public static final String TEST_START_TS = "testsuite.startTs";
    public static final String TEST_SUITE_NAME = NAMESPACE + "testsuite.name";
    public static final String TEST_NAME = "test.name";
    public static final String OPT_EXCEL_FILE = NAMESPACE + "excel";
    public static final String OPT_INPUT_EXCEL_FILE = NAMESPACE + "inputExcel";
    public static final String OPT_INPUT_PLAN_FILE = NAMESPACE + "planFile";

    public static final String OPT_HTTP_TTL = NAMESPACE + "httpTTL";
    public static final String OPT_PROXY_USER = registerSysVar(NAMESPACE + "proxy_user");
    public static final String OPT_PROXY_PASSWORD = registerSysVar(NAMESPACE + "proxy_password");
    public static final String OPT_PROXY_REQUIRED = registerSysVar(NAMESPACE + "proxyRequired");
    public static final String OPT_PROXY_DIRECT = registerSysVar(NAMESPACE + "proxyDirect");
    public static final int PROXY_PORT = 19850;

    // text-based assertion and text compare
    public static final String NS_TEXT_MATCH = NAMESPACE + "assert.";
    public static final String OPT_TEXT_MATCH_LENIENT = registerSysVar(NS_TEXT_MATCH + "lenient", true);
    public static final String OPT_TEXT_MATCH_AS_NUMBER = registerSysVar(NS_TEXT_MATCH + "asNumber", true);
    public static final String OPT_TEXT_MATCH_USE_TRIM = registerSysVar(NS_TEXT_MATCH + "useTrim", false);
    public static final String OPT_TEXT_MATCH_CASE_INSENSITIVE = registerSysVar(NS_TEXT_MATCH + "caseInsensitive",
                                                                                false);

    // number related operations
    public static final String NS_NUMBER = NAMESPACE + "number.";
    public static final String DEF_ROUNDING_MODE = "ROUND_UP";
    public static final Map<String, RoundingMode> VALID_ROUNDING_MODES = initValidRoundingModes();
    public static final String OPT_ROUNDING_MODE = registerSysVar(NS_NUMBER + "rounding", DEF_ROUNDING_MODE);

    // screenshots
    public static final String OPT_SCREENSHOT_ON_ERROR = registerSysVar(NAMESPACE + "screenshotOnError", false);
    public static final String OPT_LAST_SCREENSHOT_NAME = registerSysVar(NAMESPACE + "lastScreenshot");
    public static final String OPT_NATIVE_SCREENSHOT = registerSysVar(NAMESPACE + "screenshotAsDesktop", false);
    public static final String OPT_SCREENSHOT_FULL = registerSysVar(NAMESPACE + "screenshotInFull", false);
    public static final String OPT_SCREENSHOT_FULL_TIMEOUT = registerSysVar(NAMESPACE + "screenshotInFullTimeout",
                                                                            5000);
    public static final String SCREENSHOT_EXT = ".png";
    public static final String OPT_SCREENSHOT_ENABLED = registerSysVar(NAMESPACE + "screenshotEnabled", true);

    // outcome
    public static final String OPT_LAST_OUTCOME = registerSysVar(NAMESPACE + "lastOutcome");
    public static final String OPT_LAST_OUTPUT_LINK = registerSysVar(NAMESPACE + "lastOutputLink");
    public static final String OPT_LAST_OUTPUT_PATH = registerSysVar(NAMESPACE + "lastOutputPath");
    public static final String OPT_PRINT_ERROR_DETAIL = registerSysVar(NAMESPACE + "printErrorDetails", false);
    public static final String OPT_LAST_ERROR = registerSysVar(NAMESPACE + "lastError");
    public static final String OPT_ERROR_TRACKER = registerSysVar(NAMESPACE + "trackErrors", false);
    public static final String ERROR_TRACKER = NEXIAL_LOG_PREFIX + "execution-errors.log";

    // csv | [CSV(...) => ...]
    public static final String CSV_MAX_COLUMNS = registerSysVar(NAMESPACE + "csv.maxColumns", 512);
    public static final String CSV_MAX_COLUMN_WIDTH = registerSysVar(NAMESPACE + "csv.maxColumnWidth", 4096);

    //plugin: xml
    public static XMLOutputter COMPRESSED_XML_OUTPUTTER = new XMLOutputter(Format.getCompactFormat());
    public static XMLOutputter PRETTY_XML_OUTPUTTER = new XMLOutputter(Format.getPrettyFormat());

    public static final String DEF_FILE_ENCODING = "UTF-8";
    public static final String DEF_CHARSET = DEF_FILE_ENCODING;

    // naming scheme
    public static final String PLAN_SCRIPT_SEP = ",";
    public static final String FILE_PART_SEP = ".";
    public static final String ITER_PREFIX = FILE_PART_SEP;
    public static final String REGEX_ITER_INDEX = ".+\\" + ITER_PREFIX + "([\\d]+)\\.xls.*";
    public static final String DEF_PLAN_SERIAL_MODE = "true";
    public static final String DEF_PLAN_FAIL_FAST = "true";
    public static final String OPT_EXCEL_VER = NAMESPACE + "excelVer";
    public static final String OPT_INTERACTIVE = NAMESPACE + "interactive";

    // proxy code not ready for prime time...
    //browsermob proxy
    // public static final String OPT_PROXY_ENABLE = "proxy.enable";
    // public static final String OPT_PROXY_LOCALHOST = "localhost:98986";

    // proxy code not ready for prime time...
    //browsermob har
    // public static final String OPT_HAR_CURRENT = "_harCurrent";
    // public static final String OPT_HAR_BASE = "_harBase";
    // public static final String OPT_HAR_POST_DATA_BASE = "_postDataBase.csv";
    // public static final String OPT_HAR_POST_DATA_CURRENT = "_postDataCurrent.csv";
    // public static final String OPT_HAR_POST_DATA_INTERCEPTED = "_postDataIntercepted.csv";
    // public static final String OPT_HAR_POST_DATA_RESULTS = "_postDataResults.csv";
    // public static final String OPT_HAR_POST_COLUMN_NAMES = "Post Sequence,URL,Baseline,Current";

    // proxy code not ready for prime time...
    //browsermob wsdl
    // public static final String OPT_WSDL_BASE = "_WSDLBase.wsdl";
    // public static final String OPT_WSDL_CURRENT = "_WSDLCurrent.wsdl";
    // public static final String OPT_WSDL_RESULTS = "_WSDLResults.txt";

    // token specific
    public static final String TOKEN_START = "${";
    public static final String TOKEN_END = "}";
    public static final String TOKEN_ARRAY_START = "[";
    public static final String TOKEN_ARRAY_END = "]";
    public static final String DEFERRED_TOKEN_START = "${";
    public static final String DEFERRED_TOKEN_END = "}";
    public static final String CTRL_KEY_START = "{";
    public static final String CTRL_KEY_END = "}";

    // filter specific
    public static final String FILTER_REGEX_PATTERN = "\\s+(not in|in|between|match|is|is not)\\s+(\\[.+?\\])";
    public static final String FILTER_TEMP_DELIM1 = "~!2I3&f6n@S#*!~";
    public static final String FILTER_TEMP_DELIM2 = "~!#*2!Fn3&6g@!~";

    // regex for built-in function
    public static final String TOKEN_FUNCTION_START = "$(";
    public static final String TOKEN_FUNCTION_END = ")";
    public static final String TOKEN_DEFUNC_START = "~!~n3x!4LR0cks![[";
    public static final String TOKEN_DEFUNC_END = "]]~!~";
    public static final String TOKEN_PARAM_SEP = "|";
    public static final String REGEX_FUNCTION = "([\\w]+)\\" + TOKEN_PARAM_SEP +
                                                "([\\w]+)(\\" + TOKEN_PARAM_SEP + "(.+)+)*";
    public static final String TOKEN_TEMP_DELIM = "~!@1I4n3x@!~";
    public static final String REGEX_VALID_WEB_PROTOCOL = "(http|https|ftp|file|about)\\:.+";
    // public static final int MAX_VERBOSE_CHAR = 2000;
    public static final int MAX_VERBOSE_CHAR = 32760;
    public static final int MAX_FORMULA_CHAR = 8192;
    public static final long MIN_JSON_FILE_SIZE = 1024L;

    public static class PolyMatcher {
        public static final List<String> MATCHES = new ArrayList<>();
        public static final String CONTAIN = register("CONTAIN:");
        public static final String CONTAIN_ANY_CASE = register("CONTAIN_ANY_CASE:");
        public static final String START = register("START:");
        public static final String START_ANY_CASE = register("START_ANY_CASE:");
        public static final String END = register("END:");
        public static final String END_ANY_CASE = register("END_ANY_CASE:");
        public static final String REGEX = register("REGEX:");
        public static final String EXACT = register("EXACT:");
        public static final String EMPTY = register("EMPTY:");
        public static final String BLANK = register("BLANK:");
        public static final String LENGTH = register("LENGTH:");
        public static final String NUMERIC = register("NUMERIC:");
        public static final String REGEX_NUMERIC_COMPARE = "^\\s*([><=!]+)\\s*([\\d\\-\\.]+)\\s*$";

        private PolyMatcher() {}

        private static String register(String keyword) {
            MATCHES.add(keyword);
            return keyword;
        }

        public static boolean isPolyMatcher(String text) {
            return MATCHES.stream().anyMatch(match -> StringUtils.startsWith(text, match));
        }

    }

    // Macro Flex var prefix
    public static final String MACRO_FLEX_PREFIX = "MACRO::";

    // predefined messages
    public static final String MSG_PASS = ExcelConfig.MSG_PASS;
    public static final String MSG_FAIL = ExcelConfig.MSG_FAIL;
    public static final String MSG_WARN = ExcelConfig.MSG_WARN;
    public static final String MSG_SKIPPED = ExcelConfig.MSG_SKIPPED;
    public static final String MSG_TERMINATED = ExcelConfig.MSG_ENDED;
    public static final String MSG_CHECK_SUPPORT = "Check with Nexial Support Group for details.";
    public static final String MSG_SCRIPT_UPDATE_ERR = "ERROR: Failed to update scripts due to command metadata " +
                                                       "missing; " + MSG_CHECK_SUPPORT;
    public static final String MSG_SKIP_AUTO_OPEN_RESULT = "SKIPPING auto-open-result since Nexial is currently " +
                                                           "running in non-interactive environment";

    public static final String MSG_ABORT = "[ABORT] ";
    public static final String MSG_ALL_SCENARIOS_SKIPPED = " - subsequent test scenarios will be skipped";
    public static final String MSG_EXEC_STOP = " - test execution will stop now.";
    public static final String MSG_ITERATION_STOP = " - current iteration will stop now.";
    public static final String MSG_STEP_FAIL_FAST = MSG_ABORT + "due to execution failure and fail-fast in effect";
    public static final String MSG_ACTIVITY_FAIL_FAST = MSG_ABORT + "skipping test activity due to previous failure";
    public static final String MSG_ACTIVITY_FAIL_END = MSG_ABORT + "skipping test activity due to previous end";
    public static final String MSG_ACTIVITY_FAIL_END_LOOP =
        MSG_ABORT + "skipping test activity due to break-loop in effect";
    public static final String MSG_ACTIVITY_ENDING_IF =
        MSG_ABORT + "activity ending due to EndIf() flow control activated.";
    public static final String MSG_ACTIVITY_ENDING_LOOP_IF =
        MSG_ABORT + "activity ending due to EndLoopIf() flow control activated or unrecoverable execution failure.";
    public static final String MSG_SCENARIO_FAIL_FAST =
        MSG_ABORT + "scenario failed and fail-fast is in effect" + MSG_ALL_SCENARIOS_SKIPPED;
    public static final String MSG_SCENARIO_FAIL_IMMEDIATE =
        MSG_ABORT + "scenario failed and fail-immediate is in effect" + MSG_ALL_SCENARIOS_SKIPPED;
    public static final String MSG_SCENARIO_END_IF = MSG_ABORT + "scenario ended due to EndIf() flow control";
    public static final String MSG_SCRIPT_END_IF = "script execution ended due to end immediate in effect";
    public static final String MSG_SCENARIO_END_LOOP_IF = MSG_ABORT + "scenario ended due to EndLoopIf() flow control";
    public static final String MSG_EXEC_FAIL_FAST =
        MSG_ABORT + "failure found and fail-fast is in effect" + MSG_EXEC_STOP;
    public static final String MSG_EXEC_FAIL_IMMEDIATE = MSG_ABORT + "fail-immediate in effect" + MSG_EXEC_STOP;
    public static final String MSG_EXEC_END_IF = MSG_ABORT + "EndIf() flow control activated" + MSG_EXEC_STOP;
    public static final String MSG_EXEC_END_LOOP_IF =
        MSG_ABORT + "EndLoopIf() flow control activated" + MSG_ITERATION_STOP;
    public static final String MSG_CRITICAL_COMMAND_FAIL = MSG_ABORT + "due to failure on fail-fast command: ";
    public static final String MSG_REPEAT_UNTIL = "[repeat-until] ";
    public static final String MSG_REPEAT_UNTIL_BREAK =
        MSG_REPEAT_UNTIL + "loop terminating due to break-loop condition";
    public static final String NESTED_SECTION_STEP_SKIPPED =
        "current step skipped due to the enclosing section command being skipped";
    public static final String MSG_PROBLMATIC_NAME = "leading/trailing non-printable characters (whitespaces, tabs " +
                                                     "or newlines) found in %s name '%s' will likely cause " +
                                                     "execution-time issue.";

    public static final String COMMENT_AUTHOR = "NexialBot";

    public static final String PREFIX_JAR = "jar:";

    // text matching rules
    public static final String MATCH_BY_EXACT = "EXACT";
    public static final String MATCH_BY_CONTAINS = "CONTAINS";
    public static final String MATCH_BY_STARTS_WITH = "STARTS-WITH";
    public static final String MATCH_BY_ENDS_WITH = "ENDS-WITH";
    public static final List<String> MATCH_BY_RULES =
        Arrays.asList(MATCH_BY_EXACT, MATCH_BY_CONTAINS, MATCH_BY_STARTS_WITH, MATCH_BY_ENDS_WITH);

    // dev-focused logging
    public static final String OPT_DEVMODE_LOGGING = NAMESPACE + "devLogging";

    // aws related config
    public static final String S3_PUBLIC_URL = "public_url";
    // s3 output directory mapped to setup.properties
    public static final String OPT_CLOUD_OUTPUT_BASE = registerSysVar(NAMESPACE + "outputCloudBase");
    public static final String OUTPUT_TO_CLOUD = registerSysVar(NAMESPACE + "outputToCloud", false);
    public static final String S3_PATH_SEP = "/";

    // mem mgmt
    public static final String OPT_MANAGE_MEM = registerSysVar(NAMESPACE + "manageMemory", false);

    public static final Gson GSON = new GsonBuilder().setPrettyPrinting()
                                                     .disableHtmlEscaping()
                                                     .disableInnerClassSerialization()
                                                     .setLenient()
                                                     .create();
    public static final Gson GSON_COMPRESSED = new GsonBuilder().disableHtmlEscaping()
                                                                .disableInnerClassSerialization()
                                                                .enableComplexMapKeySerialization()
                                                                .setLenient()
                                                                .create();

    // convert windows characters to ascii characters
    public static final Map<String, String> replaceWindowsChars =
        TextUtils.toMap("=",
                        "\\u2013=-",
                        "\\u2018='",
                        "\\u2019='",
                        "\\u2026=...",
                        "\\u201C=\"",
                        "\\u201D=\"",
                        "‘='", "’='",
                        "–=-",
                        "“=\"", "”=\"",
                        "…=..."
                       );
    // we MUST not use OS-specific separator because the same log file MUST be usable for all OS;
    // the same log file may be viewed on different systems. As such, the `\n` approach seems to be the best option
    // since it's usable on all OSes. On Windows, user should be using notepad++ (or similar) instead of the standard
    // notepad so that they can get the same viewing experience as Mac or *NIX users.
    //public static final String NL = System.lineSeparator();
    public static final String NL = "\n";

    // browser types
    public enum BrowserType {
        firefox(true, true, true, true, true),
        firefoxheadless(true, true, true, true, true),
        safari(false, true, true, true, true),
        chrome(true, false, true, true, true),
        chromeheadless(true, false, true, true, true),
        ie(false, false, true, false, true),
        edge(false, false, true, false, false),
        edgechrome(true, false, true, true, true),
        iphone(false, false, false, false, true),
        browserstack(false, false, false, true, true),
        chromeembedded(false, false, true, true, true),
        electron(false, false, true, false, true),
        crossbrowsertesting(false, false, false, true, true);

        private final boolean profileSupported;
        private final boolean consoleLoggingEnabled;
        private final boolean timeoutChangesEnabled;
        private final boolean jsEventFavored;
        private final boolean switchWindowSupported;

        BrowserType(boolean profileSupported,
                    boolean consoleLoggingEnabled,
                    boolean timeoutChangesEnabled,
                    boolean jsEventFavored,
                    boolean switchWindowSupported) {
            this.profileSupported = profileSupported;
            this.consoleLoggingEnabled = consoleLoggingEnabled;
            this.timeoutChangesEnabled = timeoutChangesEnabled;
            this.jsEventFavored = jsEventFavored;
            this.switchWindowSupported = switchWindowSupported;
        }

        public boolean isProfileSupported() { return profileSupported; }

        public boolean isConsoleLoggingEnabled() { return consoleLoggingEnabled; }

        public boolean isTimeoutChangesEnabled() { return timeoutChangesEnabled; }

        public boolean isJsEventFavored() { return jsEventFavored; }

        public boolean isSwitchWindowSupported() { return switchWindowSupported; }

        public boolean isHeadless() { return this == firefoxheadless || this == chromeheadless; }

        @Override
        public String toString() { return (isHeadless() ? TextUtils.insertBefore(name(), "headless", ".") : name()); }
    }

    // @formatter:on

    public static final class Data {
        public static final String SCOPE = registerSysVarGroup(NAMESPACE + "scope.");

        public static final String NEXIAL_VERSION = NAMESPACE + "version";
        public static final String HOSTNAME = "os.hostname";
        public static final String ENV_NAME = NAMESPACE + "env";

        // allow per-run override for the output directory name
        // nexial.runID is set by castle via Jenkin internal BUILD_ID variable, which reflects build time in
        // the format of YYYY-MM-DD_hh-mm-ss
        public static final String OPT_RUN_ID = registerSysVar(NAMESPACE + "runID");
        public static final String OPT_RUN_ID_PREFIX = registerSysVar(OPT_RUN_ID + ".prefix");

        /* ----- TIMES ---------- */
        public static final String OPT_UI_RENDER_WAIT_MS = registerSysVar(NAMESPACE + "uiRenderWaitMs", 1500);
        public static final String OPT_WAIT_SPEED = registerSysVar(NAMESPACE + "waitSpeed", 3);

        // control verbosity of multi-step commands
        public static final String OPT_ELAPSED_TIME_SLA = registerSysVar(NAMESPACE + "elapsedTimeSLA");
        public static final String OPT_LAST_ELAPSED_TIME = registerSysVar(NAMESPACE + "lastElapsedTime");
        public static final String DELAY_BETWEEN_STEPS_MS = registerSysVar(NAMESPACE + "delayBetweenStepsMs", 600);
        public static final String POLL_WAIT_MS = registerSysVar(NAMESPACE + "pollWaitMs", 30 * 1000);

        // determined by nexial.[sh|cmd] file for the purpose of allowing nexial to generate a post-execution shell
        // script that can expose execution-level environment variables such as output file location, success rate, etc.
        public static final String POST_EXEC_SCRIPT = registerSysVar(NAMESPACE + "postExecEnv");

        public static final String FAIL_FAST = registerSysVar(NAMESPACE + "failFast", false);
        public static final String FAIL_AFTER = registerSysVar(NAMESPACE + "failAfter", -1);

        // determine if we should clear off any fail-fast state at the end of each script
        public static final String RESET_FAIL_FAST = registerSysVar(NAMESPACE + "resetFailFast", false);
        public static final String VERBOSE = registerSysVar(NAMESPACE + "verbose", false);
        public static final String QUIET = registerSysVar(NAMESPACE + "quiet", false);
        public static final String NULL_VALUE = registerSysVar(NAMESPACE + "nullValue", "(null)");
        public static final String TEXT_DELIM = registerSysVar(NAMESPACE + "textDelim", ",");
        public static final String PROJ_PROP_TRIM_KEY = registerSysVar(NAMESPACE + "projectProperties.trimKey", false);

        public static final String FAIL_IMMEDIATE = registerSysVar(NAMESPACE + "failImmediate", false);
        public static final String END_IMMEDIATE = registerSysVar(NAMESPACE + "endImmediate");
        public static final String END_SCRIPT_IMMEDIATE = registerSysVar(NAMESPACE + "endScriptImmediate");
        public static final String BREAK_CURRENT_ITERATION = registerSysVar(NAMESPACE + "breakCurrentIteration");
        public static final String MACRO_STEP_FAILED = registerSysVar(NAMESPACE + "macroStepFailed");
        public static final String MACRO_BREAK_CURRENT_ITERATION = registerSysVar(NAMESPACE +
                                                                                  "macroBreakCurrentIteration");
        public static final String MACRO_INVOKED_FROM = registerSysVar(NAMESPACE + "macro.invokedFrom");
        public static final String REPEAT_UNTIL_LOOP_INDEX = registerSysVar(NAMESPACE + "repeatUntil.index");
        public static final String REPEAT_UNTIL_LOOP_IN = registerSysVar(NAMESPACE + "repeatUntil");
        public static final String REPEAT_UNTIL_START_TIME = registerSysVar(NAMESPACE + "repeatUntil.startTime");
        public static final String REPEAT_UNTIL_END_TIME = registerSysVar(NAMESPACE + "repeatUntil.endTime");

        // controlled by user's script/data to end plan earlier than designed.
        public static final String LAST_PLAN_STEP = registerSysVar(NAMESPACE + "lastPlanStep", false);
        public static final String OPT_CURRENT_ACTIVITY = registerSysVar(NAMESPACE + "currentActivity");
        public static final String OPT_CURRENT_SCENARIO = registerSysVar(NAMESPACE + "currentScenario");

        // data/variable
        public static final String NS_VAR = NAMESPACE + "var.";
        public static final String OPT_VAR_EXCLUDE_LIST = registerSysVar(NS_VAR + "ignored");
        public static final String OPT_VAR_DEFAULT_AS_IS = registerSysVar(NS_VAR + "defaultAsIs", false);

        //runtime data variables
        public static final String NS_REQUIRED_VAR = SCOPE + "required.variables";

        // supersede `OPT_EXPRESSION_RESOLVE_URL` for wider coverage
        public static final String RESOLVE_TEXT_AS_URL = registerSysVar(NAMESPACE + "resolveTextAsURL", false);
        // supersede `OPT_EXPRESSION_READ_FILE_AS_IS` for wider coverage
        public static final String RESOLVE_TEXT_AS_IS = registerSysVar(NAMESPACE + "resolveTextAsIs", false);
        public static final String NS_EXPRESSION = NAMESPACE + "expression.";
        // outdated - use `RESOLVE_TEXT_AS_IS` instead
        public static final String EXPRESSION_OPEN_FILE_AS_IS = registerSysVar(NS_EXPRESSION + "OpenFileAsIs", false);
        // outdated - use `RESOLVE_TEXT_AS_URL` instead
        public static final String EXPRESSION_RESOLVE_URL = registerSysVar(NS_EXPRESSION + "resolveURL", false);

        public static final String COMMAND_DISCOVERY_MODE = NAMESPACE + "commandDiscovery";
        public static final String DEF_COMMAND_DISCOVERY_MODE = "false";

        // overly eager Nexial that opens up completed/failed excel output
        public static final String THIRD_PARTY_LOG_PATH = NAMESPACE + "3rdparty.logpath";
        public static final String TEST_LOG_PATH = NAMESPACE + "logpath";

        public static final String WIN32_CMD = "C:\\Windows\\System32\\cmd.exe";

        // excel
        public static final String SUMMARY_TAB_NAME = "#summary";
        public static final String SHEET_SYSTEM = "#system";
        public static final String SHEET_MERGED_DATA = "#data";
        public static final String SHEET_DEFAULT_DATA = "#default";
        public static final boolean DEF_OPEN_EXCEL_AS_DUP = IS_OS_WINDOWS;
        // maximum row-column limit to read/write data
        public static final int EXCEL_ROW_COL_MAX_LIMIT = 10000;

        // excel command
        public static final String OPT_RECALC_BEFORE_SAVE = registerSysVar(NAMESPACE + "excel.recalcBeforeSave", false);
        public static final String OPT_RETAIN_CELL_TYPE = registerSysVar(NAMESPACE + "excel.retainCellType", false);

        // step
        public static final String STEP_RESPONSE = NAMESPACE + "step.response";
        public static final String STEP_COMMENT = NAMESPACE + "step.comment";

        // section
        public static final String SECTION_DESCRIPTION_PREFIX = "► ";
        public static final String REPEAT_CHECK_DESCRIPTION_PREFIX = "✔ ";
        public static final String REPEAT_DESCRIPTION_PREFIX = "▼ ";

        // json
        public static final String NS_JSON = NAMESPACE + "json.";
        public static final String LAST_JSON_COMPARE_RESULT = registerSysVar(NS_JSON + "lastCompareResults");
        public static final String COMPARE_RESULT_AS_JSON = registerSysVar(NS_JSON + "compareResultsAsJSON", true);
        public static final String COMPARE_RESULT_AS_CSV = registerSysVar(NS_JSON + "compareResultsAsCSV", false);
        public static final String COMPARE_RESULT_AS_HTML = registerSysVar(NS_JSON + "compareResultsAsHTML", false);
        public static final String TREAT_JSON_AS_IS = registerSysVar(NS_JSON + "treatJsonAsIs", true);

        /**
         * special prefix to mark certain data as contextual to a test scenario execution.  Such data will be displayed
         * in the execution summary to provide as "reference" towards the associated scenario execution. E.g.
         * nexial.scenarioRef.browser=chrome
         * nexial.scenarioRef.environment=QA
         * nexial.scenarioRef.appVersion=1.6.235.1
         * <p>
         * Hence one could conclude that the associated test execution uses 'chrome' to test the application of
         * version '1.6.235.1' in the 'QA' environment.
         * <p>
         * Note that such data is to be collected at the end of a test execution (not beginning), and all
         * in-execution changes will be reflected as such.
         */
        public static final String SCENARIO_REF_PREFIX = registerSysVarGroup(NAMESPACE + "scenarioRef.");

        /**
         * special prefix to mark certain data as contextual within the execution of a script.  Such data will be
         * displayed in the execution summary to provide as "reference" towards the execution of a script, and any
         * associated iterations.  E.g.<pre>
         * nexial.scriptRef.user=User1
         * nexial.scriptRef.taxState=CA
         * nexial.scriptRef.company=Johnson Co & Ltd</pre>
         * <p>
         * Hence one could conclude that the execution of the associated script uses 'User1' to login to application,
         * and test 'CA' as the state for tax rules and 'Johnson Co & Ltd' as the target company.
         * <p>
         * Note that such data is to be collected at the end of each iteration (not beginning), and thus all
         * in-iteration changes will be reflected as such.
         */
        public static final String SCRIPT_REF_PREFIX = registerSysVarGroup(NAMESPACE + "scriptRef.");
        public static final String EXEC_REF_PREFIX = registerSysVarGroup(NAMESPACE + "executionRef.");
        public static final String BUILD_NO = "buildnum";
        public static final String DATA_FILE = "Data File";
        public static final String DATA_SHEETS = "DataSheet(s)";
        public static final String ITERATION_ENDED = NAMESPACE + "iterationEnded";

        //screen recording
        public static final String RECORDER_TYPE_MP4 = "mp4";
        public static final String RECORDER_TYPE_AVI = "avi";
        public static final String RECORDER_TYPE = registerSysVar(NAMESPACE + "screenRecorder", RECORDER_TYPE_MP4);
        public static final String RECORDING_ENABLED = registerSysVar(NAMESPACE + "recordingEnabled", true);

        public static final int MAX_TTS_LENGTH = 500;
        public static final String NEXIAL_LOG_PREFIX = "nexial-";
        public static final String EVENT_CONFIG_SEP = "|";

        // common mime types
        public static final String MIME_PLAIN = "text/plain";
        public static final String MIME_HTML = "text/html";
        public static final String MIME_JSON = WS_JSON_CONTENT_TYPE;

        public static final List<String> APIKEYS_OCRSPACE = Arrays.asList("5a64d478-9c89-43d8-88e3-c65de9999580",
                                                                          "f2c4a3c04788957",
                                                                          "cfe984f88088957",
                                                                          "cedc8a5d2d88957",
                                                                          "e5f2913cc388957");
        public static final List<String> APIKEYS_ZZ = Arrays.asList("e565eaec441c9ddbfae4134c73aa45ecf3802df1",
                                                                    "234e2cbc99948665f561e1f7b4ee6f4b47da709e",
                                                                    "a64410bb3e41caa19d2460936487382b77893818");
        public static final List<String> HOSTS_ZZ = Arrays.asList("sandbox.zamzar.com", "api.zamzar.com");

        // overrun text
        public static final String MAX_CONSOLE_DISPLAY = registerSysVar(NAMESPACE + "maxConsoleDisplay", 500);

        // nexial.scope.*
        public static final Map<String, String> SCOPE_SETTING_DEFAULTS = TextUtils.toMap("=",
                                                                                         ITERATION + "=1",
                                                                                         FALLBACK_TO_PREVIOUS + "=true",
                                                                                         REFETCH_DATA_FILE + "=true",
                                                                                         POST_EXEC_MAIL_TO + "=");
        public static final String NULL = "(null)";
        public static final String EMPTY = "(empty)";
        public static final String BLANK = "(blank)";
        public static final String TAB = "(tab)";
        public static final String EOL = "(eol)";
        public static final String REGEX_ONLY_NON_DISPLAYABLES = "(\\(blank\\)|\\(empty\\)|\\(tab\\)|\\(eol\\))+";
        public static final Map<String, String> NON_DISPLAYABLE_REPLACEMENTS = TextUtils.toMap("=",
                                                                                               EMPTY + "=",
                                                                                               BLANK + "= ",
                                                                                               TAB + "=\t",
                                                                                               EOL + "=\n");
        public static final String CMD_PROFILE_SEP = "::";
        public static final String CMD_PROFILE_DEFAULT = "DEFAULT";

        // saveTableAsCSV and saveDivsAsCSV
        public static final class SaveGridAsCSV {
            private static final String _NS = registerSysVarGroup(NS_WEB + "saveGrid.");

            public static final String DEEP_SCAN = registerSysVar(_NS + "deepScan", false);
            public static final String HEADER_INPUT = registerSysVar(_NS + "header.input", InputOptions.name.name());
            public static final String HEADER_IMAGE = registerSysVar(_NS + "header.image", ImageOptions.type.name());
            public static final String DATA_INPUT = registerSysVar(_NS + "data.input", InputOptions.state.name());
            public static final String DATA_IMAGE = registerSysVar(_NS + "data.image", ImageOptions.type.name());
            public static final String DATA_TRIM = registerSysVar(_NS + "data.trim", true);
            public static final String END_TRIM = registerSysVar(_NS + "end.trim", false);

            public enum InputOptions {
                name, type, value, id, state;

                @Override
                public String toString() { return name(); }
            }

            public enum ImageOptions {
                type, alt, id, filename;

                @Override
                public String toString() { return name(); }
            }

            // reference by enclosing class to force initialization (possibly prior to any reference at runtime)
            static void init() {}
        }

        private Data() { }

        public static String withProfile(String profile, String key) {
            if (StringUtils.isBlank(profile) || StringUtils.equals(profile, CMD_PROFILE_DEFAULT)) { return key; }
            if (StringUtils.startsWith(key, NS_BROWSER)) {return TextUtils.insertAfter(key, NS_BROWSER, "." + profile);}
            if (StringUtils.startsWith(key, NS_WEB)) { return TextUtils.insertAfter(key, NS_WEB, "." + profile); }
            return key + CMD_PROFILE_SEP + profile;
        }

        // reference by enclosing class to force initialization (possibly prior to any reference at runtime)
        static void init() {}
    }

    public static final class LogMessage {
        public static final String EXECUTING_ITERATION = "executing iteration #";
        public static final String EXECUTING_TEST_SCENARIO = "executing test scenario";
        public static final String EXECUTING_ACTIVITY = "executing activity";
        public static final String ENDING_ACTIVITY = "ending activity";
        public static final String SAVING_TEST_SCENARIO = "saving test scenario";
        public static final String TEST_COMPLETE = "TEST COMPLETE";
        public static final String ITERATION_COMPLETE = "ITERATION COMPLETE";
        public static final String SCRIPT_COMPLETE = "SCRIPT COMPLETE";
        public static final String END_OF_EXECUTION = "END OF EXECUTION";
        public static final String END_OF_EXECUTION2 = "End of Execution";
        public static final String TEST_FAILED = "TEST FAILED";
        public static final String STARTS = "STARTS";
        public static final String FOUND_PLANS = "found plans in ";
        public static final String VALIDATE_TEST_SCRIPT = "validating test script as ";
        public static final String MSG_THREAD_TERMINATED = "all execution thread(s) have terminated";
        public static final String MSG_CLEANUP = "cleaning up outdated temp files...";
        public static final String RESOLVE_RUN_ID = "resolve RUN ID as ";
        public static final String ERROR_LOG = "Error log: ";
        public static final String SCREENSHOT_CAPTURED_LOG = "output/screenshot captured to file ";
        public static final String CURRENT_SCRIPT = "current script";
    }

    public static final class Iteration {
        // predefined variable to define the iteration to use
        public static final String ITERATION = SCOPE + "iteration";

        // read-only iteration-related variables:
        // the iteration counter that just completed
        public static final String LAST_ITERATION = SCOPE + "lastIteration";

        // the currently in-progress iteration index (doesn't mean it will or has completed successfully)
        public static final String CURR_ITERATION = SCOPE + "currentIteration";

        // current iteration id (not index, this is the actual column position on datasheet)
        public static final String CURR_ITERATION_ID = SCOPE + "currentIterationId";

        // is current iteration the first?
        public static final String IS_FIRST_ITERATION = SCOPE + "isFirstIteration";

        // is current iteration the last?
        public static final String IS_LAST_ITERATION = SCOPE + "isLastIteration";

        // read-only: reload data file between iteration or not
        public static final String REFETCH_DATA_FILE = SCOPE + "refetchDataFile";
        public static final String FALLBACK_TO_PREVIOUS = SCOPE + "fallbackToPrevious";
        public static final String ITERATION_SEP = ",";
        public static final String ITERATION_RANGE_SEP = "-";

        private Iteration() {}

        // reference by enclosing class to force initialization (possibly prior to any reference at runtime)
        static void init() {}
    }

    // directives on notes column
    public static final class FlowControls {
        public static final String OPT_STEP_BY_STEP = registerSysVar(NAMESPACE + "stepByStep", false);
        public static final String OPT_INSPECT_ON_PAUSE = registerSysVar(NAMESPACE + "inspectOnPause", false);
        public static final String RESUME_FROM_PAUSE = ":resume";
        public static final String OPT_PAUSE_ON_ERROR = registerSysVar(NAMESPACE + "pauseOnError", false);
        public static final String OPT_ODI_ENABLED = registerSysVar(NAMESPACE + "odiEnabled", true);
        public static final String OPT_ODI_TIMER = registerSysVar(NAMESPACE + "odiTimer", 500);
        public static final String OPT_ODI_KEYS = registerSysVar(NAMESPACE + "odiKeys", "!!!");

        public static final String ARG_PREFIX = "(";
        public static final String ARG_SUFFIX = ")\\s*$";
        public static final String REGEX_ARGS = "\\s*\\" + ARG_PREFIX + "(.*?)\\" + ARG_SUFFIX;
        public static final String FILTER_CHAINING_SEP = " & ";

        public static final String IS_OPEN_TAG = "[";
        public static final String IS_CLOSE_TAG = "]";
        public static final String ANY_FIELD = "[ANY FIELD]";
        public static final String CONDITION_DISABLE = "SkipIf(true) ";

        public static final String REGEX_IS_UNARY_FILTER =
            "(true|false|\\$\\{[^\\}]+\\}|\\!\\$\\{[^\\}]+\\}|not\\s+\\$\\{[^\\}]+\\})";

        private FlowControls() {}

        // reference by enclosing class to force initialization (possibly prior to any reference at runtime)
        static void init() {}
    }

    public static final class Integration {
        public static final String OTC_PREFIX = NAMESPACE + "otc.";
        public static final String TTS_PREFIX = NAMESPACE + "tts.";
        public static final String MAIL_PREFIX = NAMESPACE + "mail.";
        public static final String SMS_PREFIX = NAMESPACE + "sms.";
        public static final String VISION_PREFIX = NAMESPACE + "vision.";

        private Integration() {}

        // reference by enclosing class to force initialization (possibly prior to any reference at runtime)
        static void init() {}
    }

    public static final class CommonColor {
        static final Map<String, Color> COLOR_NAMES = new HashMap<>();
        static final Map<Color, Color> BACKGROUND_COLOR_NAMES = new HashMap<>();

        private CommonColor() {}

        public static Map<String, Color> getColorNames() { return COLOR_NAMES; }

        public static Color toColor(String colorName) {
            return MapUtils.getObject(COLOR_NAMES, StringUtils.lowerCase(colorName));
        }

        public static Color toComplementaryBackgroundColor(Color color) {
            return MapUtils.getObject(BACKGROUND_COLOR_NAMES, color);
        }

        static {
            COLOR_NAMES.put("red", RED);
            COLOR_NAMES.put("orange", ORANGE);
            COLOR_NAMES.put("yellow", YELLOW);
            COLOR_NAMES.put("green", GREEN);
            COLOR_NAMES.put("blue", BLUE);
            COLOR_NAMES.put("cyan", CYAN);
            COLOR_NAMES.put("black", BLACK);
            COLOR_NAMES.put("gray", GRAY);
            COLOR_NAMES.put("white", WHITE);
            COLOR_NAMES.put("pink", PINK);
            COLOR_NAMES.put("magenta", MAGENTA);

            BACKGROUND_COLOR_NAMES.put(RED, new Color(255, 255, 150, 230));
            BACKGROUND_COLOR_NAMES.put(ORANGE, new Color(0, 10, 35, 220));
            BACKGROUND_COLOR_NAMES.put(YELLOW, new Color(75, 20, 50, 230));
            BACKGROUND_COLOR_NAMES.put(GREEN, new Color(0, 0, 0, 220));
            BACKGROUND_COLOR_NAMES.put(BLUE, new Color(255, 255, 255, 220));
            BACKGROUND_COLOR_NAMES.put(CYAN, new Color(0, 0, 0, 220));
            BACKGROUND_COLOR_NAMES.put(BLACK, new Color(255, 255, 255, 220));
            BACKGROUND_COLOR_NAMES.put(GRAY, new Color(0, 0, 40, 240));
            BACKGROUND_COLOR_NAMES.put(WHITE, new Color(0, 0, 0, 200));
            BACKGROUND_COLOR_NAMES.put(PINK, new Color(0, 0, 0, 220));
            BACKGROUND_COLOR_NAMES.put(MAGENTA, new Color(255, 255, 255, 240));
        }
    }

    public static final class External {
        private static final String NS = NAMESPACE + "external.";

        // plugin:external
        // store the file name of the output resulted from a `external.runProgram` command
        public static final String OPT_RUN_PROGRAM_OUTPUT = registerSysVar(NS + "output");
        public static final String OPT_RUN_PROGRAM_CONSOLE = registerSysVar(NS + "console", false);
        public static final String OPT_RUN_FROM = registerSysVar(NS + "workingDirectory");
    }

    public static final class ImageDiffColor {
        private ImageDiffColor() {}

        /** default to red */
        public static Color toColor(String colorName) {
            return MapUtils.getObject(COLOR_NAMES, colorName, COLOR_NAMES.get(getDefault(OPT_IMAGE_DIFF_COLOR)));
        }
    }

    public static final class Image {
        // image
        public static final String NS_IMAGE = NAMESPACE + "image.";
        public static final String OPT_TRIM_BEFORE_DIFF = registerSysVar(NS_IMAGE + "trimBeforeDiff", false);
        public static final String OPT_IMAGE_TRIM_COLOR = registerSysVar(NS_IMAGE + "trimColor", "255,255,255");
        public static final String OPT_LAST_IMAGES_DIFF = registerSysVar(NS_IMAGE + "lastImagesDiff");
        public static final String OPT_IMAGE_TOLERANCE = registerSysVar(NAMESPACE + "imageTolerance", 0);
        public static final String OPT_IMAGE_DIFF_COLOR = registerSysVar(NAMESPACE + "imageDiffColor", "red");
        public static final int MIN_TRIM_SPACES = 3;

        public enum ImageType {
            png(TYPE_INT_RGB),
            jpg(TYPE_INT_RGB),
            gif(TYPE_INT_ARGB),
            bmp(TYPE_INT_ARGB);

            private final int imageType;

            ImageType(int imageType) { this.imageType = imageType; }

            public int getImageType() { return imageType; }
        }

        private Image() {}

        // reference by enclosing class to force initialization (possibly prior to any reference at runtime)
        static void init() {}
    }

    public static final class ImageCaption {
        public static final String SCREENSHOT_CAPTION = registerSysVar(NAMESPACE + "screenshot.caption");
        public static final String SCREENSHOT_CAPTION_COLOR = registerSysVar(SCREENSHOT_CAPTION + ".color");
        public static final String SCREENSHOT_CAPTION_POSITION = registerSysVar(SCREENSHOT_CAPTION + ".position");
        public static final String SCREENSHOT_CAPTION_WRAP = registerSysVar(SCREENSHOT_CAPTION + ".wrap");
        public static final String SCREENSHOT_CAPTION_ALPHA = registerSysVar(SCREENSHOT_CAPTION + ".alpha");
        public static final String SCREENSHOT_CAPTION_NO_BKGRD = registerSysVar(SCREENSHOT_CAPTION + ".noBackground");

        public static final String DEF_FONT_FACE = "Arial";
        public static final int DEF_FONT_SIZE = 14;
        public static final float DEF_ALPHA = 1f;
        public static final int MIN_WIDTH = 320;
        public static final int MIN_HEIGHT = 200;
        public static final int TOP_PADDING = 15;
        public static final int BOTTOM_PADDING = 10;
        public static final int LEFT_PADDING = 10;
        public static final int RIGHT_PADDING = 10;
        public static final int LINE_PADDING = 2;

        // Image caption
        public enum CaptionPositions {
            TOP_LEFT(true, false, false, true, false, false),
            TOP_CENTER(true, false, false, false, true, false),
            TOP_RIGHT(true, false, false, false, false, true),
            MIDDLE_LEFT(false, true, false, true, false, false),
            MIDDLE_CENTER(false, true, false, false, true, false),
            MIDDLE_RIGHT(false, true, false, false, false, true),
            BOTTOM_LEFT(false, false, true, true, false, false),
            BOTTOM_CENTER(false, false, true, false, true, false),
            BOTTOM_RIGHT(false, false, true, false, false, true);

            private final boolean top;
            private final boolean middle;
            private final boolean bottom;
            private final boolean left;
            private final boolean center;
            private final boolean right;

            CaptionPositions(boolean top, boolean middle, boolean bottom, boolean left, boolean center, boolean right) {
                this.top = top;
                this.middle = middle;
                this.bottom = bottom;
                this.left = left;
                this.center = center;
                this.right = right;
            }

            public boolean isTop() { return top; }

            public boolean isMiddle() { return middle; }

            public boolean isBottom() { return bottom; }

            public boolean isLeft() { return left; }

            public boolean isCenter() { return center; }

            public boolean isRight() { return right; }

            public static CaptionPositions toCaptionPosition(String vertical, String horizontal) {
                if (StringUtils.isBlank(vertical) || StringUtils.isBlank(horizontal)) { return null; }

                String position = vertical.toUpperCase() + "_" + horizontal.toUpperCase();
                try {
                    return CaptionPositions.valueOf(position);
                } catch (IllegalArgumentException e) {
                    ConsoleUtils.error("No caption position named as " + position);
                    return null;
                }
            }
        }

        private ImageCaption() {}

        // reference by enclosing class to force initialization (possibly prior to any reference at runtime)
        static void init() {}
    }

    public static final class AwsSettings {
        public static final String SUFFIX = "aws";

        public static final String AWS_URL = "url";
        public static final String AWS_ACCESS_KEY = "accessKey";
        public static final String AWS_SECRET_KEY = "secretKey";
        public static final String AWS_REGION = "region";
        public static final String AWS_SES_FROM = "from";
        public static final String AWS_SES_REPLY_TO = "replyTo";
        public static final String AWS_SES_CC = "cc";
        public static final String AWS_SES_BCC = "bcc";
        public static final String AWS_SES_CONFIG_SET = "configurationSetName";
        public static final String AWS_XMAILER = "xmailer";

        // sts / assume-role config
        public static final String AWS_STS_ROLE_ARN = "assumeRoleArn";
        public static final String AWS_STS_ROLE_SESSION = "assumeRoleSession";
        public static final String AWS_STS_ROLE_DURATION = "assumeRoleDuration";

        // sqs
        public static final String AWS_SQS_ASYNC = "async";
        public static final String AWS_SQS_WAIT_TIME_MS = "waitTimeMs";
        public static final String AWS_SQS_VISIBILITY_TIMEOUT_MS = "visibilityTimeoutMs";

        private AwsSettings() {}

        // reference by enclosing class to force initialization (possibly prior to any reference at runtime)
        static void init() {}
    }

    public static final class TMSSettings {
        public static final String TMS = "tms.";
        public static final String NEXIAL_TMS = NAMESPACE + TMS;
        public static final String TMS_URL = NEXIAL_TMS + "url";
        public static final String TMS_SOURCE = NEXIAL_TMS + "source";
        public static final String TMS_USERNAME = NEXIAL_TMS + "username";
        public static final String TMS_PASSWORD = NEXIAL_TMS + "password";
        public static final String TMS_ORG = NEXIAL_TMS + "organization";

        private TMSSettings() {}

        // reference by enclosing class to force initialization (possibly prior to any reference at runtime)
        static void init() {}
    }

    public static final class Project {
        public static final String DEF_LOC_ARTIFACT = "artifact";
        public static final String DEF_REL_LOC_ARTIFACT = DEF_LOC_ARTIFACT + separator;
        public static final String DEF_LOC_TEST_DATA = "data";
        public static final String DEF_REL_LOC_BIN = DEF_REL_LOC_ARTIFACT + "bin" + separator;
        public static final String DEF_REL_LOC_TEST_PLAN = DEF_REL_LOC_ARTIFACT + "plan" + separator;
        public static final String DEF_REL_LOC_TEST_DATA = DEF_REL_LOC_ARTIFACT + DEF_LOC_TEST_DATA + separator;
        public static final String DEF_REL_LOC_TEST_SCRIPT = DEF_REL_LOC_ARTIFACT + "script" + separator;
        public static final String DEF_REL_LOC_OUTPUT = "output" + separator;
        public static final String DEF_PROJECT_PROPS = "project.properties";
        public static final String DEF_REL_PROJECT_PROPS = DEF_REL_LOC_ARTIFACT + DEF_PROJECT_PROPS;
        public static final String DEF_REL_META = ".meta" + separator;
        public static final String DEF_REL_META_PROJ_ID = DEF_REL_META + "project.id";
        public static final String DEF_REL_META_EXECUTION = DEF_REL_META + "execution";

        public static final String NEXIAL_HOME = NAMESPACE + "home";
        public static final String NEXIAL_BIN_REL_PATH = "bin" + separator;
        public static final String NEXIAL_MACOSX_BIN_REL_PATH = NEXIAL_BIN_REL_PATH + "macosx" + separator;
        public static final String NEXIAL_WINDOWS_BIN_REL_PATH = NEXIAL_BIN_REL_PATH + "windows" + separator;
        public static final String NEXIAL_LINUX_BIN_REL_PATH = NEXIAL_BIN_REL_PATH + "linux" + separator;
        public static final String NEXIAL_EXECUTION_TYPE = NAMESPACE + "executionType";
        public static final String NEXIAL_EXECUTION_TYPE_SCRIPT = "script";
        public static final String NEXIAL_EXECUTION_TYPE_PLAN = "plan";

        public static final String SCRIPT_FILE_SUFFIX = "xlsx";
        public static final String SCRIPT_FILE_EXT = "." + SCRIPT_FILE_SUFFIX;
        public static final String DATA_FILE_SUFFIX = "data." + SCRIPT_FILE_SUFFIX;
        public static final String DEF_DATAFILE_SUFFIX = "." + DATA_FILE_SUFFIX;

        // for command json metadata
        public static final String COMMAND_JSON_FILE_NAME = NAMESPACE + "script.metadata.json";
        public static final String VAR_CMD_JSON = NAMESPACE + "var.command.json";
        public static final String JSON_FOLDER = TEMP + "nexial-json" + separator;
        public static final File COMMAND_JSON_FILE = new File(JSON_FOLDER + COMMAND_JSON_FILE_NAME);
        public static final File COMMAND_VAR_JSON_FILE = new File(JSON_FOLDER + VAR_CMD_JSON);
        public static final File TEMP_JSON_JAR = new File(TEMP + "nexial-json-jar/nexial-json.jar");

        public static final String USER_NEXIAL_HOME =
            StringUtils.appendIfMissing(new File(USER_HOME).getAbsolutePath(), separator) + ".nexial" + separator;
        public static final String USER_NEXIAL_INSTALL_HOME = USER_NEXIAL_HOME + "install" + separator;
        public static final String USER_PROJECTS_DIR = IS_OS_WINDOWS ?
                                                       "C:\\projects" + separator :
                                                       USER_HOME + "/projects" + separator;
        public static final String NEXIAL_INSTALLER_MIN_VERSION = "1.4.5";
        public static final String PROJECT_CACHE_LOCATION = USER_NEXIAL_HOME + "projectCache" + separator;
        public static final String BROWSER_META_CACHE_PATH = USER_NEXIAL_HOME + "browser-meta.json";

        private Project() { }

        public static String appendCapture(String dir) { return appendSep(dir) + SUBDIR_CAPTURES; }

        public static String appendLog(String dir) { return appendSep(dir) + SUBDIR_LOGS; }

        public static String appendLog(ExecutionDefinition execDef) {
            if (execDef == null) { return null; }
            return appendLog(StringUtils.defaultString(execDef.getOutPath()) + separator +
                             StringUtils.defaultString(execDef.getRunId()));
        }

        public static String appendData(String dir) { return appendSep(dir) + DEF_LOC_TEST_DATA; }

        public static String appendScript(String dir) { return appendSep(dir) + DEF_REL_LOC_TEST_SCRIPT; }

        public static String appendOutput(String dir) { return appendSep(dir) + DEF_REL_LOC_OUTPUT; }

        public static TestProject resolveStandardPaths(TestProject project) {
            if (project.isStandardStructure()) {
                String projectHome = project.getProjectHome();
                String projectIdFile = projectHome + separator + DEF_REL_META_PROJ_ID;

                String projectId = null;
                File metaProjectIdFile = new File(projectIdFile);
                if (FileUtil.isFileReadable(metaProjectIdFile)) {
                    try {
                        projectId = StringUtils.trim(FileUtils.readFileToString(metaProjectIdFile, DEF_FILE_ENCODING));
                    } catch (IOException e) {
                        ConsoleUtils.error("Unable to read " + DEF_REL_META_PROJ_ID + ": " + e.getMessage());
                    }
                }

                if (StringUtils.isNotBlank(projectId)) {
                    if (StringUtils.containsIgnoreCase(projectId, "echo is off")) {
                        // need to notify user of this... they would need to upgrade to more recent version of Nexial
                        // (with fix)
                        String error = NL + NL +
                                       StringUtils.repeat("-", 80) + NL +
                                       "!!!!! ERROR !!!!!" + NL +
                                       "The file " + projectIdFile + " contains INCORRECT project id:" + NL +
                                       "\t" + projectId + NL + NL +
                                       "Please fix this issue by:" + NL +
                                       "1. Run the 'nexial-project' batch:" + NL +
                                       "   cd " + System.getProperty(NEXIAL_HOME) + separator + "bin" + NL +
                                       "   " + (IS_OS_WINDOWS ? "nexial-project.cmd" : "./nexial-project.sh") + " " +
                                       projectHome + NL + NL +
                                       "2. Update " + projectIdFile + " with the appropriate project ID." + NL +
                                       "   Project ID should be a single word, without spaces." + NL + NL +
                                       "Nexial execution will stop now." + NL + NL +
                                       StringUtils.repeat("-", 80) + NL + NL;
                        throw new ServiceConfigurationError(error);
                    }

                    project.setName(projectId);
                    project.setBoundProjectId(projectId);
                } else {
                    project.setName(StringUtils.substringAfterLast(projectHome, separator));
                }

                project.setArtifactPath(projectHome + separator + DEF_REL_LOC_ARTIFACT);
                project.setScriptPath(projectHome + separator + DEF_REL_LOC_TEST_SCRIPT);
                project.setDataPath(projectHome + separator + DEF_REL_LOC_TEST_DATA);
                project.setPlanPath(projectHome + separator + DEF_REL_LOC_TEST_PLAN);

                // local output path is not affected by project.id
                project.setOutPath(StringUtils.defaultIfBlank(System.getProperty(OPT_DEF_OUT_DIR),
                                                              projectHome + separator + DEF_REL_LOC_OUTPUT));
            }

            return project;
        }

        // reference by enclosing class to force initialization (possibly prior to any reference at runtime)
        static void init() {}

        private static String appendSep(String dir) {
            return StringUtils.appendIfMissing(StringUtils.defaultString(dir), separator);
        }
    }

    public static final class CLI {
        public static final String SCRIPT = "script";
        public static final String SCENARIO = "scenario";
        public static final String DATA = "data";
        public static final String DATASHEETS = "datasheets";
        public static final String PLAN = "plan";
        public static final String SUBPLANS = "subplans";
        public static final String OUTPUT = "output";
        public static final String OVERRIDE = "override";
        public static final String ANNOUNCE = "announce";
        public static final String INTERACTIVE = "interactive";
        public static final Options OPTIONS = initCmdOptions();

        private CLI() { }

        // reference by enclosing class to force initialization (possibly prior to any reference at runtime)
        static void init() {}

        private static Options initCmdOptions() {
            Options cmdOptions = new Options();
            cmdOptions.addOption(SCRIPT, true, "[REQUIRED] if -" + PLAN + " is missing] The fully qualified " +
                                               "path of the test script.");
            cmdOptions.addOption(SCENARIO, true, "[optional] One or more test scenarios, separated by commas, " +
                                                 "to execute. Default is to execute all scenarios.");
            cmdOptions.addOption(DATA, true, "[optional] The data file in ../data directory (relative to test " +
                                             "script, or fully qualified path, for this test execution.  " +
                                             "Default is the data file in the ../data directory with the same " +
                                             "name of the test script.");
            cmdOptions.addOption(DATASHEETS, true, "[optional] Restricting to a comma-separated list of data " +
                                                   "sheets for this test execution. Default is to utilize all " +
                                                   "the data sheets of the specified/implied data file.");
            cmdOptions.addOption(OUTPUT, true, "[optional] The output directory where results and execution " +
                                               "artifacts will be stored. Default is ../../output, relative to " +
                                               "the specified test script.");
            cmdOptions.addOption(PLAN, true, "[REQUIRED if -" + SCRIPT + " is missing] The fully qualified path of a " +
                                             "test plan (or plans). Multiple plans can be specified using comma as " +
                                             "separator. The use of this argument will disable the other arguments.");
            cmdOptions.addOption(SUBPLANS, true, "[optional] Comma separated list of the subplans" +
                                                 "(worksheets) of a test plan. The use of this argument will be only " +
                                                 " enabled for single " + PLAN + " execution.");
            cmdOptions.addOption(OVERRIDE, true, "[optional] Add or override data variables in the form of " +
                                                 "name=value. Multiple overrides are supported via multiple " +
                                                 "-" + OVERRIDE + " name=value declarations. Note that variable name " +
                                                 "or value with spaces must be enclosed in double quotes.");
            cmdOptions.addOption(INTERACTIVE, false, "[optional] Run Nexial in Interactive Mode.");
            return cmdOptions;
        }
    }

    public static final class Exec {
        public static final String FAIL_COUNT = registerSysVar(NAMESPACE + "executionFailCount");
        public static final String SKIP_COUNT = registerSysVar(NAMESPACE + "executionSkipCount");
        public static final String PASS_COUNT = registerSysVar(NAMESPACE + "executionPassCount");
        public static final String EXEC_COUNT = registerSysVar(NAMESPACE + "executionCount");
        public static final String EXEC_SYNOPSIS = registerSysVar(NAMESPACE + "executionSynopsis");
        public static final String MIN_EXEC_SUCCESS_RATE = registerSysVar(NAMESPACE + "minExecSuccessRate", 100);

        // system-wide enable/disable email notification
        public static final String ENABLE_EMAIL = registerSysVar(NAMESPACE + "enableEmail", false);
        public static final String POST_EXEC_MAIL_TO_OLD = SCOPE + "mailTo";
        public static final String POST_EXEC_MAIL_TO = registerSysVar(NAMESPACE + "mailTo");
        public static final String POST_EXEC_EMAIL_SUBJECT = registerSysVar(NAMESPACE + "mailSubject");
        public static final String POST_EXEC_EMAIL_HEADER = registerSysVar(NAMESPACE + "mailHeader");
        public static final String POST_EXEC_EMAIL_FOOTER = registerSysVar(NAMESPACE + "mailFooter");
        public static final String POST_EXEC_WITH_SYNOPSIS = registerSysVar(NAMESPACE + "mailSubject.withSynopsis",
                                                                            true);

        // email subject prefix only for event notification
        public static final String MAIL_NOTIF_SUBJECT_PREFIX = "[nexial-notification] ";

        // email subject prefix  only for post-exec result notification
        public static final String MAIL_RESULT_SUBJECT_PREFIX = "[nexial] ";

        // system-wide execution summary related props
        public static final String NS_SUMMARY = NAMESPACE + "summary.";
        public static final String SUMMARY_CUSTOM_FOOTER = registerSysVar(NS_SUMMARY + "footer");
        public static final String SUMMARY_CUSTOM_HEADER = registerSysVar(NS_SUMMARY + "header");

        // note: only consider sysprop, not data variable
        public static final String GENERATE_EXEC_REPORT = registerSysVar(NAMESPACE + "generateReport", false);

        public static final String ASSISTANT_MODE = registerSysVar(NAMESPACE + "assistantMode", false);
        // synonymous to `assistantMode`, but reads better
        public static final String OPT_OPEN_RESULT = registerSysVar(NAMESPACE + "openResult", false);
        public static final String OPT_OPEN_EXEC_REPORT = registerSysVar(NAMESPACE + "openExecutionReport", false);
        public static final String SPREADSHEET_PROGRAM_EXCEL = "excel";
        public static final String SPREADSHEET_PROGRAM = registerSysVar(NAMESPACE + "spreadsheet.program",
                                                                        SPREADSHEET_PROGRAM_EXCEL);
        public static final String SPREADSHEET_PROGRAM_WPS = "wps";
        public static final String WPS_EXE_LOCATION = "nexialInternal.wpsLocation";

        private Exec() {}

        // reference by enclosing class to force initialization (possibly prior to any reference at runtime)
        static void init() {}
    }

    public static final class Mailer {
        public static final String MAIL_KEY_AUTH = registerSysVar(MAIL_PREFIX + "smtp.auth");
        public static final String MAIL_KEY_BCC = registerSysVar(MAIL_PREFIX + "smtp.bcc");
        public static final String MAIL_KEY_BUFF_SIZE = registerSysVar(MAIL_PREFIX + "smtp.bufferSize");
        public static final String MAIL_KEY_CC = registerSysVar(MAIL_PREFIX + "smtp.cc");
        public static final String MAIL_KEY_CONTENT_TYPE = registerSysVar(MAIL_PREFIX + "smtp.contentType");
        public static final String MAIL_KEY_DEBUG = registerSysVar(MAIL_PREFIX + "smtp.debug");
        public static final String MAIL_KEY_FROM = registerSysVar(MAIL_PREFIX + "smtp.from");
        public static final String MAIL_KEY_FROM_DEF = registerSysVar(MAIL_PREFIX + "smtp.from.default");
        public static final String MAIL_KEY_LOCALHOST = registerSysVar(MAIL_PREFIX + "smtp.localhost");
        public static final String MAIL_KEY_MAIL_HOST = registerSysVar(MAIL_PREFIX + "smtp.host");
        public static final String MAIL_KEY_MAIL_PORT = registerSysVar(MAIL_PREFIX + "smtp.port");
        public static final String MAIL_KEY_PASSWORD = registerSysVar(MAIL_PREFIX + "smtp.password");
        public static final String MAIL_KEY_PROTOCOL = registerSysVar(MAIL_PREFIX + "transport.protocol");
        public static final String MAIL_KEY_TLS_ENABLE = registerSysVar(MAIL_PREFIX + "smtp.starttls.enable");
        public static final String MAIL_KEY_USERNAME = registerSysVar(MAIL_PREFIX + "smtp.username");
        public static final String MAIL_KEY_XMAILER = registerSysVar(MAIL_PREFIX + "header.xmail");

        // standalone smtp config
        public static final List<String> SMTP_KEYS = Arrays.asList(
            MAIL_KEY_BUFF_SIZE, MAIL_KEY_PROTOCOL, MAIL_KEY_MAIL_HOST, MAIL_KEY_MAIL_PORT, MAIL_KEY_TLS_ENABLE,
            MAIL_KEY_AUTH, MAIL_KEY_DEBUG, MAIL_KEY_CONTENT_TYPE, MAIL_KEY_USERNAME, MAIL_KEY_PASSWORD,
            MAIL_KEY_FROM, MAIL_KEY_FROM_DEF, MAIL_KEY_CC, MAIL_KEY_BCC, MAIL_KEY_XMAILER);

        public static final String MAIL_KEY_MAIL_JNDI_URL = registerSysVar(MAIL_PREFIX + "jndi.url");
        // jndi smtp config
        public static final List<String> JNDI_KEYS = Arrays.asList(
            MAIL_KEY_MAIL_JNDI_URL, INITIAL_CONTEXT_FACTORY, OBJECT_FACTORIES, STATE_FACTORIES,
            URL_PKG_PREFIXES, PROVIDER_URL, DNS_URL, AUTHORITATIVE, BATCHSIZE, REFERRAL, SECURITY_PROTOCOL,
            SECURITY_AUTHENTICATION, SECURITY_PRINCIPAL, SECURITY_CREDENTIALS, LANGUAGE);

        // ses api config
        public static final String SES_PREFIX = "nexial-mailer." + SUFFIX + ".";
        public static final List<String> SES_KEYS = Arrays.asList(
            SES_PREFIX + AWS_ACCESS_KEY,
            SES_PREFIX + AWS_SECRET_KEY,
            SES_PREFIX + AWS_REGION,
            SES_PREFIX + AWS_STS_ROLE_ARN,
            SES_PREFIX + AWS_STS_ROLE_SESSION,
            SES_PREFIX + AWS_STS_ROLE_DURATION,
            SES_PREFIX + AWS_SES_FROM,
            SES_PREFIX + AWS_SES_REPLY_TO,
            SES_PREFIX + AWS_SES_CC,
            SES_PREFIX + AWS_SES_BCC,
            SES_PREFIX + AWS_SES_CONFIG_SET,
            SES_PREFIX + AWS_XMAILER);
        public static final String SES_ENFORCE_NO_CERT = registerSysVar(NAMESPACE + "sesNoCert", true);

        // enable for email notification?
        public static final List<String> MAILER_KEYS =
            ListUtils.sum(ListUtils.sum(ListUtils.sum(Arrays.asList(ENABLE_EMAIL,
                                                                    POST_EXEC_MAIL_TO,
                                                                    POST_EXEC_EMAIL_SUBJECT,
                                                                    POST_EXEC_EMAIL_HEADER,
                                                                    POST_EXEC_EMAIL_FOOTER),
                                                      SMTP_KEYS), JNDI_KEYS), SES_KEYS);

        public static final String NOT_READY_PREFIX = "nexial mailer not enabled: ";
        public static final String DOC_REF_SUFFIX = " Please check " + DOCUMENTATION_URL +
                                                    "/tipsandtricks/IntegratingNexialWithEmail.html for more details";

        public static final String JNDI_NOT_READY = NOT_READY_PREFIX +
                                                    "missing required JNDI configurations." +
                                                    DOC_REF_SUFFIX;
        public static final String SMTP_NOT_READY = NOT_READY_PREFIX +
                                                    "missing required smtp/imap configurations." +
                                                    DOC_REF_SUFFIX;
        public static final String SES_NOT_READY = NOT_READY_PREFIX +
                                                   "missing required AWS SES configurations." +
                                                   DOC_REF_SUFFIX;
        public static final String MAILER_NOT_READY = NOT_READY_PREFIX +
                                                      "unable to resolve any valid mailer configurations." +
                                                      DOC_REF_SUFFIX;

        private Mailer() {}

        // reference by enclosing class to force initialization (possibly prior to any reference at runtime)
        static void init() {}
    }

    public static final class Desktop {
        // desktop
        public static final String WINIUM_EXE = "Winium.Desktop.Driver.exe";
        public static final String WINIUM_PORT = registerSysVar(NAMESPACE + "winiumPort");
        public static final String WINIUM_JOIN = registerSysVar(NAMESPACE + "winiumJoinExisting", false);
        public static final String WINIUM_LOG_PATH = registerSysVar(NAMESPACE + "winiumLogPath");
        public static final String WINIUM_SERVICE_RUNNING = registerSysVar(NAMESPACE + "winiumServiceActive");
        public static final String WINIUM_SOLO_MODE = registerSysVar(NAMESPACE + "winiumSoloMode", true);
        // deprecating...
        public static final String DESKTOP_NOTIFY_WAITMS = registerSysVar(NAMESPACE + "desktopNotifyWaitMs", 5000);

        public static final String CLEAR_TABLE_CELL_BEFORE_EDIT = registerSysVar(NAMESPACE + "clearTableCellBeforeEdit",
                                                                                 false);

        private static final String NS = NAMESPACE + "desktop.";
        public static final String NOTIFY_WAITMS = registerSysVar(NS + "notifyWaitMs", 5000);
        public static final String USE_ASCII_KEY_MAPPING = registerSysVar(NS + "useAsciiKey", false);
        public static final String AUTOSCAN_INFRAGISTICS4_AWARE = registerSysVar(NS + "infragistics4Aware", false);
        // additional console logging during autoscanning
        public static final String OPT_AUTOSCAN_VERBOSE = registerSysVar(NS + "autoscan.verbose", false);

        private Desktop() {}

        // reference by enclosing class to force initialization (possibly prior to any reference at runtime)
        static void init() {}
    }

    public static final class Compare {
        // io
        public static final String NS_COMPARE = NAMESPACE + "compare.";
        public static final String LOG_MATCH = registerSysVar(NS_COMPARE + "reportMatch", false);
        public static final String GEN_COMPARE_HTML = registerSysVar(NS_COMPARE + "htmlReport", false);
        public static final String GEN_COMPARE_JSON = registerSysVar(NS_COMPARE + "jsonReport", false);
        public static final String GEN_COMPARE_LOG = registerSysVar(NS_COMPARE + "textReport", true);
        public static final String MAPPING_EXCEL = ".mappingExcel";
        public static final String CONFIG_JSON = ".configJson";
        public static final String REPORT_TYPE = ".reportType";

        public static final String EOL_CONFIG_AS_IS = "as is";
        public static final String EOL_CONFIG_PLATFORM = "platform";
        public static final String EOL_CONFIG_DEF = EOL_CONFIG_PLATFORM;
        public static final String EOL_CONFIG_WINDOWS = "windows";
        public static final String EOL_CONFIG_UNIX = "unix";
        public static final String COPY_CONFIG_OVERRIDE = "override";
        public static final String COPY_CONFIG_BACKUP = "backup";
        public static final String COPY_CONFIG_KEEP_ORIGINAL = "keepOriginal";
        public static final String COPY_CONFIG_DEF = COPY_CONFIG_KEEP_ORIGINAL;

        public static final String NS_IO = NAMESPACE + "io.";
        public static final String OPT_IO_EOL_CONFIG = registerSysVar(NS_IO + "eolConfig", EOL_CONFIG_DEF);
        public static final String OPT_IO_MATCH_RECURSIVE = registerSysVar(NS_IO + "matchRecursive", true);
        public static final String OPT_IO_MATCH_INCL_SUBDIR = registerSysVar(NS_IO + "matchIncludeDirectories", false);
        public static final String OPT_IO_MATCH_EXACT = registerSysVar(NS_IO + "matchExact", false);
        public static final String COMPARE_INCLUDE_DELETED = registerSysVar(NS_IO + "compareIncludeRemoved");
        public static final String COMPARE_INCLUDE_ADDED = registerSysVar(NS_IO + "compareIncludeAdded");
        // todo: need to evaluate how to use these 3 to modify the nexial result and excel output
        public static final String COMPARE_INCLUDE_MOVED = registerSysVar(NS_IO + "compareIncludeMoved");
        public static final String OPT_IO_COPY_CONFIG = registerSysVar(NS_IO + "copyConfig", COPY_CONFIG_DEF);

        private Compare() {}

        // reference by enclosing class to force initialization (possibly prior to any reference at runtime)
        static void init() {}
    }

    public static final class SoapUI {
        public static final String TESTRUNNER_REL_PATH = separator + "bin" + separator + "testrunner.bat";
        // r : Turns on printing of a small summary report
        // a : Turns on exporting of all test results, not only errors
        // I : Do not stop if error occurs, ignore them
        // j : Turns on exporting of JUnit-compatible reports
        // P : Sets project property with name=value, e.g. -Pendpoint=Value1 -PsomeOtherProperty=value2
        public static final List<String> STD_OPTS = TextUtils.toList("-r,-a,-j", ",", true);

        public static final String SOAPUI_FILENAME_REMOVE_CHARS = "!@#$%^&*()_-+~`={}|[]:\\\";'<>?,./'";
        public static final String XPATH_TESTSUITE = "//testsuite";
        public static final String XPATH_TESTCASE = XPATH_TESTSUITE + "/testcase";
        public static final String XPATH_TESTCASE_FAILURE = XPATH_TESTCASE + "/failure";

        // specialized xpath to handle any namespace
        public static final String XPATH_TESTSTEPS_TEMPLATE = "//*[local-name()='testSuite'][@name=${testsuite}]" +
                                                              "/*[local-name()='testCase'][@name=${testcase}]" +
                                                              "/*[local-name()='testStep']";
        public static final List<String> TEST_STEP_REQUEST_TYPES = Arrays.asList("request", "restrequest");

        public static final String VAR_PROJECT_XML = "projectXml";
        public static final String VAR_SOAPUI_HOME = "soapuiHome";

        public static final String REGEX_JUNIT_REPORT_FILES = "TEST\\-.+\\.xml";
        public static final String REGEX_OUTPUT_FILES = "\\-.+\\.txt";
        public static final String SOAPUI_PARAM_TESTSUITE = "testsuite";
        public static final String SOAPUI_PARAM_TESTCASE = "testcase";

        public static final String OPT_SOAPUI_STORE_RESP = NAMESPACE + "soapui.storeResponse";

        private SoapUI() {}

        // reference by enclosing class to force initialization (possibly prior to any reference at runtime)
        static void init() {}
    }

    // set by jenkins
    public static final class Jenkins {
        public static final String OPT_JOB_NAME = "JOB_NAME";
        public static final String OPT_BUILD_NUMBER = "BUILD_NUMBER";
        public static final String OPT_BUILD_URL = "BUILD_URL";
        public static final String OPT_BUILD_ID = "BUILD_ID";
        public static final String OPT_BUILD_USER_ID = "BUILD_USER_ID";
        public static final String OPT_BUILD_USER = "BUILD_USER";
        public static final String OPT_JENKINS_URL = "JENKINS_URL";
        public static final String OPT_JENKINS_HOME = "JENKINS_HOME";

        private Jenkins() { }

        // reference by enclosing class to force initialization (possibly prior to any reference at runtime)
        static void init() {}
    }

    // nexial main exit status
    public static final class ExitStatus {
        public static final int RC_NORMAL = 0;
        public static final int RC_WARNING_FOUND = -12;
        public static final int RC_NOT_PERFECT_SUCCESS_RATE = -13;
        public static final int RC_FAILURE_FOUND = -14;
        public static final int RC_EXECUTION_SUMMARY_MISSING = -15;
        public static final int RC_BAD_CLI_ARGS = -16;
        public static final int RC_EXCEL_IN_USE = -17;
        public static final int RC_FILE_GEN_FAILED = -18;
        public static final int RC_FILE_NOT_FOUND = -19;
        public static final int RC_BAD_BATCH_FILE = -20;
        public static final int RC_NOT_SUPPORT_ZERO_TOUCH_ENV = -21;

        // env. properties (mainly to export to)
        public static final String OUTPUT_LOCATION = NAMESPACE + "output";
        public static final String JUNIT_XML_LOCATION = NAMESPACE + "junitxml";
        public static final String EXEC_OUTPUT_PATH = NAMESPACE + "execution.output";
        public static final String SUCCESS_RATE = NAMESPACE + "success.rate";
        public static final String EXIT_STATUS = NAMESPACE + "exit.status";

        private ExitStatus() { }

        // reference by enclosing class to force initialization (possibly prior to any reference at runtime)
        static void init() {}
    }

    public static final class OS {
        private static final String OS_WINDOWS = "WINDOWS";
        private static final String OS_MAC = "MAC";
        private static final String OS_MACOS = "MACOS";
        private static final String OS_MACOSX = "MACOSX";
        private static final String OS_LINUX = "LINUX";

        private static final List<String> VALID_OS = Arrays.asList(OS_WINDOWS, OS_MAC, OS_MACOS, OS_MACOSX, OS_LINUX);

        public static boolean isValid(String os) { return StringUtils.equals(os, "*") || VALID_OS.contains(os); }

        public static boolean isWindows(String os) {
            return StringUtils.equals(os, "*") || StringUtils.equals(OS_WINDOWS, os);
        }

        public static boolean isMac(String os) {
            return StringUtils.equals(os, "*") ||
                   StringUtils.equals(OS_MAC, os) ||
                   StringUtils.equals(OS_MACOS, os) ||
                   StringUtils.equals(OS_MACOSX, os);
        }

        public static boolean isLinux(String os) {
            return StringUtils.equals(os, "*") || StringUtils.equals(OS_LINUX, os);
        }
    }

    public static final class Notification {
        public static final String SMS_PREFIX = "sms:";
        public static final String AUDIO_PREFIX = "audio:";
        public static final String EMAIL_PREFIX = "email:";
        public static final String CONSOLE_PREFIX = "console:";
        public static final String TTS_PREFIX = "tts:";

        // event notification
        public static final String ON_EXEC_START = registerSysVar(NAMESPACE + "notifyOnExecutionStart");
        public static final String ON_EXEC_COMPLETE = registerSysVar(NAMESPACE + "notifyOnExecutionComplete");
        public static final String ON_SCRIPT_START = registerSysVar(NAMESPACE + "notifyOnScriptStart");
        public static final String ON_SCRIPT_COMPLETE = registerSysVar(NAMESPACE + "notifyOnScriptComplete");
        public static final String ON_ITER_START = registerSysVar(NAMESPACE + "notifyOnIterationStart");
        public static final String ON_ITER_COMPLETE = registerSysVar(NAMESPACE + "notifyOnIterationComplete");
        public static final String ON_SCN_START = registerSysVar(NAMESPACE + "notifyOnScenarioStart");
        public static final String ON_SCN_COMPLETE = registerSysVar(NAMESPACE + "notifyOnScenarioComplete");
        public static final String ON_ERROR = registerSysVar(NAMESPACE + "notifyOnError");
        public static final String ON_PAUSE = registerSysVar(NAMESPACE + "notifyOnPause");
        public static final String ON_USE_APP = registerSysVar(NAMESPACE + "notifyOnDesktopUseApp");
        public static final String ON_USE_FORM = registerSysVar(NAMESPACE + "notifyOnDesktopUseForm");
        public static final String ON_USE_TABLE = registerSysVar(NAMESPACE + "notifyOnDesktopUseTable");
        public static final String ON_USE_LIST = registerSysVar(NAMESPACE + "notifyOnDesktopUseList");
        public static final String ON_WS_START = registerSysVar(NAMESPACE + "notifyOnWsStart");
        public static final String ON_WS_COMPLETE = registerSysVar(NAMESPACE + "notifyOnWsComplete");
        public static final String ON_RDBMS_START = registerSysVar(NAMESPACE + "notifyOnRdbmsStart");
        public static final String ON_RDBMS_COMPLETE = registerSysVar(NAMESPACE + "notifyOnRdbmsComplete");
        public static final String ON_WEB_OPEN = registerSysVar(NAMESPACE + "notifyOnWebOpen");
        public static final String ON_BROWSER_COMPLETE = registerSysVar(NAMESPACE + "notifyOnBrowserComplete");

        private Notification() {}

        // reference by enclosing class to force initialization (possibly prior to any reference at runtime)
        static void init() {}
    }

    public static final class TimeTrack {
        // predefined variable for time tracking of execution levels
        public static final String TIMETRACK = NAMESPACE + "timetrack.";
        public static final String TIMETRACK_FORMAT = registerSysVar(TIMETRACK + "format",
                                                                     "START_DATE|" +
                                                                     "START_TIME|" +
                                                                     "END_DATE|" +
                                                                     "END_TIME|" +
                                                                     "ELAPSED_TIME|" +
                                                                     "THREAD_NAME|" +
                                                                     "LABEL|" +
                                                                     "REMARK");
        public static final String TRACK_SCENARIO = registerSysVar(TIMETRACK + "trackScenario");
        public static final String TRACK_ITERATION = registerSysVar(TIMETRACK + "trackIteration");
        public static final String TRACK_SCRIPT = registerSysVar(TIMETRACK + "trackScript");
        public static final String TRACK_EXECUTION = registerSysVar(TIMETRACK + "trackExecution", false);
        public static final String[] TRACKING_DETAIL_TOKENS = new String[]{"START_DATE",
                                                                           "START_TIME",
                                                                           "END_DATE",
                                                                           "END_TIME",
                                                                           "ELAPSED_TIME",
                                                                           "THREAD_NAME",
                                                                           "LABEL",
                                                                           "REMARK"};

        private TimeTrack() {}

        // reference by enclosing class to force initialization (possibly prior to any reference at runtime)
        static void init() {}
    }

    public static final class Ssh {
        // ssh
        public static final String SSH_CLIENT_PREFIX = registerSysVarGroup(NAMESPACE + "ssh.");
        public static final String SSH_USERNAME = "username";
        public static final String SSH_PASSWORD = "password";
        public static final String SSH_HOST = "host";
        public static final String SSH_PORT = "port";
        public static final String SSH_HOST_KEY_CHECK = "strictHostKeyChecking";
        public static final String SSH_KNOWN_HOSTS = "knownHosts";
        public static final String DEF_SSH_PORT = "22";

        private Ssh() {}

        // reference by enclosing class to force initialization (possibly prior to any reference at runtime)
        static void init() {}
    }

    public static final class Rdbms {
        // plugin:rdbms
        public static final String DAO_PREFIX = NAMESPACE + "dao.";
        public static final String OPT_INCLUDE_PACK_SINGLE_ROW = registerSysVar(NAMESPACE + "rdbms.packSingleRow",
                                                                                false);
        public static final String SQL_DELIM = ";";
        public static final String SQL_COMMENT = "--";
        public static final String SQL_VAR = "nexial:";
        public static final String OPT_DB_URL = ".url";
        public static final String OPT_DB_USER = ".user";
        public static final String OPT_DB_PASSWORD = ".password";
        public static final String OPT_DB_TYPE = ".type";
        public static final String OPT_DB_AUTOCOMMIT = ".autocommit";
        public static final boolean DEF_AUTOCOMMIT = true;
        public static final String OPT_TREAT_NULL_AS = ".treatNullAs";
        public static final String DEF_TREAT_NULL_AS = "";
        public static final String SQL_LINE_SEP = "\n";
        public static final String CSV_ROW_SEP = "\n";
        public static final String CSV_FIELD_DEIM = ",";
        public static final String IMPORT_BUFFER_SIZE = registerSysVar(NAMESPACE + "rdbms.importBufferSize", 100);
        // to overcome unknown but valid JDBC drivers
        public static final String OPT_DB_CLASSNAME = ".JavaClassName";
        // for mongodb jdbc connection only
        public static final String OPT_DB_EXPAND_DOC = ".expandDocument";
        public static final String URL_OPT_EXPAND_DOC = "expand=true";
        public static final String OPT_DB_TRUST_STORE = ".trustStore";
        public static final String OPT_DB_TRUST_STORE_PWD = ".trustStorePassword";
        public static final String OPT_IS_DOCUMENTDB = ".isDocumentDB";
        public static final String DEF_DOCUMENTDB_JKS_RELPATH = "bin" + separator + "rds-truststore.jks";
        public static final String DEF_DOCUMENTDB_JKS_PWD = "nexial_mongo";

        private Rdbms() {}

        // reference by enclosing class to force initialization (possibly prior to any reference at runtime)
        static void init() {}
    }

    public static final class Ws {
        // ws
        public static final String NS_WS = NAMESPACE + "ws.";
        public static final String NS_WS_ASYNC = NS_WS + "async.";

        // default to 3 minutes
        public static final String WS_ASYNC_SHUTDOWN_TIMEOUT = registerSysVar(NS_WS_ASYNC + "shutdownWaitMs",
                                                                              3 * 60 * 1000);
        public static final String WS_BASIC_NAMESPACE = NS_WS + "basic.";
        public static final String WS_BASIC_PWD = registerSysVar(WS_BASIC_NAMESPACE + "password");
        public static final String WS_BASIC_USER = registerSysVar(WS_BASIC_NAMESPACE + "user");
        public static final String WS_DIGEST_NAMESPACE = NS_WS + "digest.";
        public static final String WS_DIGEST_NONCE = registerSysVar(WS_DIGEST_NAMESPACE + "nonce");
        public static final String WS_DIGEST_REALM = registerSysVar(WS_DIGEST_NAMESPACE + "realm");
        public static final String WS_DIGEST_PWD = registerSysVar(WS_DIGEST_NAMESPACE + "password");
        public static final String WS_DIGEST_USER = registerSysVar(WS_DIGEST_NAMESPACE + "user");
        public static final String WS_PROXY_PWD = registerSysVar(NS_WS + "proxyPassword");
        public static final String WS_PROXY_USER = registerSysVar(NS_WS + "proxyUser");
        public static final String WS_PROXY_PORT = registerSysVar(NS_WS + "proxyPort");
        public static final String WS_PROXY_HOST = registerSysVar(NS_WS + "proxyHost");
        public static final String WS_PROXY_REQUIRED = registerSysVar(NS_WS + "proxyRequired");
        public static final String WS_REQ_HEADER_PREFIX = NS_WS + "header.";
        public static final String WS_LOG_SUMMARY = registerSysVar(NS_WS + "logSummary", false);
        public static final String WS_LOG_DETAIL = registerSysVar(NS_WS + "logDetail", false);
        public static final String WS_REQ_FILE_AS_RAW = registerSysVar(NS_WS + "requestPayloadAsRaw", false);
        public static final String WS_REQ_PAYLOAD_COMPACT = registerSysVar(NS_WS + "requestPayloadCompact", false);
        public static final String WS_KEEP_ALIVE = registerSysVar(NS_WS + "keepAlive", true);
        public static final String WS_ALLOW_RELATIVE_REDIRECTS = registerSysVar(NS_WS + "allowRelativeRedirects", true);
        public static final String WS_ALLOW_CIRCULAR_REDIRECTS = registerSysVar(NS_WS + "allowCircularRedirects",
                                                                                false);
        public static final String WS_ENABLE_EXPECT_CONTINUE = registerSysVar(NS_WS + "enableExpectContinue", true);
        public static final String WS_ENABLE_REDIRECTS = registerSysVar(NS_WS + "enableRedirects", true);
        public static final String WS_READ_TIMEOUT = registerSysVar(NS_WS + "readTimeout", 5 * 60 * 1000);
        public static final String WS_CONN_TIMEOUT = registerSysVar(NS_WS + "connectionTimeout", 5 * 60 * 1000);
        public static final String WS_USER_AGENT = "User-Agent";
        public static final String WS_CONTENT_TYPE = "Content-Type";
        public static final String WS_CONTENT_LENGTH = "Content-Length";
        public static final String CONTENT_TYPE_CHARSET = "charset=";
        public static final String WS_SOAP_CONTENT_TYPE = "text/xml;" + CONTENT_TYPE_CHARSET + "UTF-8";
        public static final String WS_JSON_CONTENT_TYPE = "application/json";
        public static final String WS_JSON_CONTENT_TYPE2 = WS_JSON_CONTENT_TYPE + ";" + CONTENT_TYPE_CHARSET + "UTF-8";
        public static final String WS_FORM_CONTENT_TYPE = "application/x-www-form-urlencoded";
        // corresponds to the various multipart header settings supported by Apache HttpClient
        // STRICT               - RFC 822, RFC 2045, RFC 2046 compliant
        // BROWSER_COMPATIBLE   - browser-compatible mode, i.e. only write Content-Disposition; use content charset;
        //                        meant for IE-5 or earlier
        // RFC6532              - RFC 6532 compliant; essentially implementing the strict (RFC 822, RFC 2045, RFC
        //                        2046 compliant) interpretation of the spec, BUT with the exception of allowing
        //                        UTF-8 headers, as per RFC6532.
        // possible choices: "standard", "strict", "browser"
        public static final String WS_MULTIPART_MODE = registerSysVar(NS_WS + "multipart.spec", "standard");
        public static final String WS_MULTIPART_CHARSET = registerSysVar(NS_WS + "multipart.charset");

        // oauth
        public static final String OAUTH_CLIENT_ID = "client_id";
        public static final String OAUTH_CLIENT_SECRET = "client_secret";
        public static final String OAUTH_SCOPE = "scope";
        public static final String OAUTH_GRANT_TYPE = "grant_type";
        public static final List<String> OAUTH_REQUIRED_INPUTS = Arrays.asList(OAUTH_CLIENT_ID, OAUTH_GRANT_TYPE);
        public static final String OAUTH_ACCESS_TOKEN = "access_token";
        public static final String OAUTH_TOKEN_TYPE = "token_type";
        public static final String OAUTH_TOKEN_TYPE_BEARER = "Bearer";
        public static final String OAUTH_TOKEN_TYPE_BEARER2 = "BearerToken";
        public static final String OAUTH_TOKEN_TYPE_MAC = "MAC";
        public static final String OAUTH_TOKEN_TYPE_BASIC = "Basic";
        public static final String OAUTH_BASIC_AUTH_USERNAME_KEY = "basicAuth.usernameKey";
        public static final String OAUTH_BASIC_AUTH_PASSWORD_KEY = "basicAuth.passwordKey";
        public static final String OAUTH_URL_PALCEHOLDER = "{0}";
        public static final String OAUTH_CUSTOM_TYPE = "custom";

        private Ws() {}

        // reference by enclosing class to force initialization (possibly prior to any reference at runtime)
        static void init() {}
    }

    private static Map<String, RoundingMode> initValidRoundingModes() {
        Map<String, RoundingMode> map = new HashMap<>();
        map.put(DEF_ROUNDING_MODE, HALF_UP);
        map.put("ROUND_DOWN", HALF_DOWN);
        map.put("ROUND_EVEN", HALF_EVEN);
        return map;
    }

    public static final class WebMail {
        public static final String BROWSER_CONFIG = "nexial.browser=chrome.headless\n" +
                                                    "nexial.pollWaitMs=5000\n" +
                                                    "nexial.delayBrowser=true\n" +
                                                    "nexial.web.alwaysWait=true\n" +
                                                    "nexial.web.preemptiveAlertCheck=true\n";
        public static final long MAX_DURATION = 24 * 60;

        // Mailinator Constants.
        public static final String WEBMAIL_MAILINATOR = "mailinator";
        public static final String MAILINATOR_BROWSER_PROFILE = NAMESPACE + "mailinator-browser";

        // Temporary Mail Constants.
        public static final String WEBMAIL_TEMPORARYMAIL = "temporary-mail";

        private WebMail() { }

        // reference by enclosing class to force initialization (possibly prior to any reference at runtime)
        static void init() { }
    }

    public static class CloudWebTesting {
        public static final String BASE_PROTOCOL = "http://";

        public static final String SESSION_ID = "sessionId";

        public static final String SCOPE_ITERATION = "iteration";
        public static final String SCOPE_SCRIPT = "script";
        public static final String SCOPE_EXECUTION = "execution";
        public static final String SCOPE_DEFAULT = SCOPE_EXECUTION;
        public static final List<ExecutionEvent> SUPPORTED_SCOPES =
            Arrays.asList(IterationComplete, ScriptComplete, ExecutionComplete);

        public static boolean isValidReportScope(String scope) {
            return StringUtils.equals(scope, SCOPE_EXECUTION) || StringUtils.equals(scope, SCOPE_ITERATION);
        }

        public static boolean isValidReportScope(ExecutionEvent scope) { return SUPPORTED_SCOPES.contains(scope); }
    }

    // browserstack
    public static final class BrowserStack extends CloudWebTesting {
        public static final String BASE_URL = "@hub.browserstack.com/wd/hub";
        public static final String SESSION_URL =
            "https://${username}:${automatekey}@api.browserstack.com/automate/sessions/${sessionId}.json";

        private static final String NS = NAMESPACE + "browserstack.";

        public static final String KEY_USERNAME = registerSysVar(NS + "username");
        public static final String AUTOMATEKEY = registerSysVar(NS + "automatekey");

        public static final String KEY_BROWSER = registerSysVar(NS + "browser");
        public static final String KEY_BROWSER_VER = registerSysVar(NS + "browser.version");
        public static final String KEY_DEBUG = registerSysVar(NS + "debug", true);
        public static final String KEY_RESOLUTION = registerSysVar(NS + "resolution");
        public static final String KEY_BUILD_NUM = registerSysVar(NS + "app.buildnumber");
        public static final String KEY_ENABLE_LOCAL = registerSysVar(NS + "enablelocal", false);
        public static final String KEY_OS = registerSysVar(NS + "os");
        public static final String KEY_OS_VER = registerSysVar(NS + "os.version");
        public static final String KEY_TERMINATE_LOCAL = registerSysVar(NS + "terminatelocal", true);

        public static final String KEY_CAPTURE_CRASH = registerSysVar(NS + "captureCrash");

        // status report
        public static final String KEY_STATUS_SCOPE = registerSysVar(NS + "reportStatus");

        private BrowserStack() {}

        // reference by enclosing class to force initialization (possibly prior to any reference at runtime)
        static void init() {}
    }

    public static final class CrossBrowserTesting extends CloudWebTesting {
        public static final String BASE_URL = "@hub.crossbrowsertesting.com:80/wd/hub";
        public static final String SESSION_URL = "http://crossbrowsertesting.com/api/v3/selenium/${seleniumTestId}";

        public static final String NS = "cbt.";

        // https://help.crossbrowsertesting.com/selenium-testing/tutorials/crossbrowsertesting-automation-capabilities/
        public static final String REFERENCE_URL =
            "https://help.crossbrowsertesting.com/selenium-testing/tutorials/crossbrowsertesting-automation-capabilities/";

        // credential
        public static final String KEY_USERNAME = "username";
        public static final String KEY_AUTHKEY = "authkey";

        public static final String KEY_ENABLE_LOCAL = "enablelocal";
        public static final String KEY_TERMINATE_LOCAL = "terminatelocal";
        public static final String KEY_LOCAL_START_WAITMS = "localStartWaitMs";
        public static final long DEF_LOCAL_START_WAITMS = 5000;
        public static final long MAX_LOCAL_START_WAITMS = 20000;
        public static final String AUTO_LOCAL_START_WAIT = "auto";
        public static final String LOCAL_READY_FILE = TEMP + NAMESPACE + "cbtlocal.ready";

        // project meta
        public static final String KEY_NAME = "name";
        public static final String KEY_BUILD = "build";

        public static final String KEY_ENABLE_VIDEO = "record_video";
        public static final String DEF_ENABLE_VIDEO = "true";

        public static final String KEY_RECORD_NETWORK = "record_network";
        public static final String DEF_RECORD_NETWORK = "false";

        public static final String KEY_MAX_DURATION = "max_duration";

        // https://help.crossbrowsertesting.com/selenium-testing/resources/list-of-timezones/
        public static final String KEY_TZ = "timezone";

        // browser
        public static final String KEY_BROWSER = "browserName";
        public static final String KEY_BROWSER_VER = "version";
        public static final String KEY_RESOLUTION = "screenResolution";

        // os/platform
        public static final String KEY_PLATFORM = "platform";

        // mobile
        public static final String KEY_MOBILE_PLATFORM = "platformName";
        public static final String KEY_MOBILE_PLATFORM_VER = "platformVersion";
        public static final String KEY_DEVICE = "deviceName";
        public static final String KEY_DEVICE_ORIENTATION = "deviceOrientation";
        public static final String DEF_DEVICE_ORIENTATION = "portrait";

        // status report
        public static final String KEY_STATUS_SCOPE = NS + "reportStatus";

        private CrossBrowserTesting() {}

        // reference by enclosing class to force initialization (possibly prior to any reference at runtime)
        static void init() {}
    }

    public static final class Pdf {
        /* pdf*/
        public static final String PDF_USE_ASCII = registerSysVar(NAMESPACE + "pdfUseAscii", true);
        public static final String PDFFORM_UNMATCHED_TEXT = "__UNMATCHED_TEXT";
        public static final String PDFFORM_PREFIX = registerSysVarGroup(NAMESPACE + "pdfFormStrategy.");
        public static final String PDFFORM_BASEDON = "basedOn";
        public static final String PDFFORM_KEY_THEN_VALUE = "keyThenValue";
        public static final String PDFFORM_KEY_PATTERN = "keyPattern";
        public static final String PDFFORM_KEY_VALUE_DELIM = "keyValueDelimiter";
        public static final String PDFFORM_TRIM_KEY = "trimKey";
        public static final String PDFFORM_NORMALIZE_KEY = "normalizeKey";
        public static final String PDFFORM_SKIP_KEY_WITHOUT_DELIM = "skipKeyWithoutDelim";
        public static final String PDFFORM_TRIM_VALUE = "trimValue";
        public static final String PDFFORM_VALUE_AS_ONE_LINE = "valueAsOneLine";
        public static final String PDFFORM_NORMALIZE_VALUE = "normalizeValue";
        public static final String PDFFORM_FALLBACK = "fallback";
        public static final List<String> PDFFORM_VALID_OPTIONS = Arrays.asList(PDFFORM_BASEDON,
                                                                               PDFFORM_KEY_THEN_VALUE,
                                                                               PDFFORM_FALLBACK,
                                                                               PDFFORM_KEY_PATTERN,
                                                                               PDFFORM_KEY_VALUE_DELIM,
                                                                               PDFFORM_TRIM_KEY,
                                                                               PDFFORM_NORMALIZE_KEY,
                                                                               PDFFORM_SKIP_KEY_WITHOUT_DELIM,
                                                                               PDFFORM_TRIM_VALUE,
                                                                               PDFFORM_VALUE_AS_ONE_LINE,
                                                                               PDFFORM_NORMALIZE_VALUE);

        public static final String MIME_PDF = "application/pdf";
        public static final String TITLE = "Title";
        public static final String SUBJECT = "Subject";
        public static final String AUTHOR = "Author";
        public static final String CREATOR = "Creator";
        public static final String PRODUCER = "Producer";
        public static final String KEYWORDS = "Keywords";
        public static final String VERSION = "PDFVersion";
        public static final String CREATE_DATE = "CreateDate";
        public static final String MODIFY_DATE = "ModifyDate";
        public static final String CREATOR_TOOL = "CreatorTool";
        public static final String SUBJECTS = "Subjects";
        public static final String DC_DATES = "Dates";
        public static final String DESCRIPTION = "Description";

        private Pdf() {}

        // reference by enclosing class to force initialization (possibly prior to any reference at runtime)
        static void init() {}
    }

    public static final class Web {
        // custom namespace
        public static final String NS_BROWSER = NAMESPACE + "browser";
        public static final String NS_EMU = NS_BROWSER + ".emulation.";
        public static final String NS_ELECTRON = NS_BROWSER + ".electron.";
        public static final String NS_SAFARI = NS_BROWSER + ".safari.";
        public static final String NS_IE = NS_BROWSER + ".ie.";
        public static final String NS_WEB = NAMESPACE + "web.";

        // default browser
        public static final String BROWSER = registerSysVar(NS_BROWSER, "firefox");

        // emulation
        public static final String EMU_USER_AGENT = registerSysVar(NS_EMU + "userAgent");

        // browser
        public static final String EMU_TOUCH = registerSysVar(NS_EMU + "touch", true);
        public static final String EMU_PIXEL_RATIO = registerSysVar(NS_EMU + "pixelRatio", 3.0);
        public static final String EMU_HEIGHT = registerSysVar(NS_EMU + "height", 850);
        public static final String EMU_WIDTH = registerSysVar(NS_EMU + "width", 400);
        public static final String EMU_DEVICE_NAME = registerSysVar(NS_EMU + "deviceName");

        // chrome
        public static final String CHROME_REMOTE_PORT = registerSysVar(NS_BROWSER + ".chrome.remote.port");
        public static final String CHROME_LOG_ENABLED = registerSysVar(NS_BROWSER + ".logChrome", false);
        public static final String CHROME_ENABLE_EXTENSION =
            registerSysVar(NS_BROWSER + ".chrome.enableExtension", false);

        // edge
        public static final String EDGE_LOG_ENABLED = registerSysVar(NS_BROWSER + ".logEdge", false);

        // advanced/selenium logging
        public static final String CAPTURE_BROWSER_LOGS = registerSysVar(NS_WEB + "log.includeBrowser", false);

        // electron
        public static final String ELECTRON_FORCE_TERMINATE = registerSysVar(NS_ELECTRON + "forceTerminate", false);
        public static final String ELECTRON_CLIENT_LOCATION = registerSysVar(NS_ELECTRON + "appLocation");
        public static final String ELECTRON_LOG_VERBOSE = registerSysVar(NS_BROWSER + ".logElectronVerbose", false);
        public static final String ELECTRON_LOG_ENABLED = registerSysVar(NS_BROWSER + ".logElectron", false);

        // chrome or edgechrome embedded
        public static final String CEF_CLIENT_LOCATION = registerSysVar(NS_BROWSER + ".embedded.appLocation");

        // safari
        public static final String SAFARI_RESIZED = registerSysVar(NS_SAFARI + "resizedAfterOpen", false);
        public static final String SAFARI_USE_TECH_PREVIEW = registerSysVar(NS_SAFARI + "useTechPreview", false);

        // ie
        public static final String IE_REQUIRE_WINDOW_FOCUS = registerSysVar(NS_IE + "requireWindowFocus", false);
        public static final String OPT_FORCE_IE_32 = registerSysVar(NAMESPACE + "forceIE32", false);

        // specify user profile per browser instance
        public static final String BROWSER_USER_DATA = registerSysVar(NS_BROWSER + ".userData");
        public static final String OPT_CHROME_PROFILE = registerSysVar(NAMESPACE + "chrome.profile");
        public static final String OPT_DOWNLOAD_TO = registerSysVar(NS_BROWSER + ".downloadTo");
        public static final String OPT_DOWNLOAD_PDF = registerSysVar(NS_BROWSER + ".downloadPdf", true);
        public static final String OPT_POSITION = registerSysVar(NS_BROWSER + ".position");

        // web: scroll into view
        public static final String SCROLL_INTO_VIEW = registerSysVar(NS_WEB + "scrollIntoView", true);

        public static final String GROUP_LOCATOR_SUFFIX = ".locator";

        // various browser behavior/settings
        public static final String FORCE_JS_CLICK = registerSysVar(NS_BROWSER + ".forceJSClick", false);
        public static final String BROWSER_ACCEPT_INVALID_CERTS =
            registerSysVar(NS_BROWSER + ".acceptInsecureCerts", false);
        public static final String BROWSER_POST_CLOSE_WAIT = registerSysVar(NS_BROWSER + ".postCloseWaitMs", 2000);
        // default to false to improve performance
        public static final String ENFORCE_PAGE_SOURCE_STABILITY =
            registerSysVar(NAMESPACE + "enforcePageSourceStability", false);
        public static final String OPT_DELAY_BROWSER = registerSysVar(NAMESPACE + "delayBrowser", false);
        public static final String BROWSER_DEFAULT_WINDOW_SIZE = registerSysVar(NS_BROWSER + ".defaultWindowSize");
        public static final String BROWSER_WINDOW_SIZE = registerSysVar(NS_BROWSER + ".windowSize");
        public static final String BROWSER_LANG = registerSysVar(NAMESPACE + "browserLang");
        public static final String OPT_BROWSER_CONSOLE_LOG = registerSysVar(NAMESPACE + "browserConsoleLog", false);
        public static final String KEY_INCOGNITO = "incognito";
        public static final String BROWSER_INCOGNITO = registerSysVar(NS_BROWSER + "." + KEY_INCOGNITO, true);
        public static final String OPT_LAST_ALERT_TEXT = registerSysVar(NAMESPACE + "lastAlertText");
        public static final String OPT_ALERT_IGNORE_FLAG = registerSysVar(NAMESPACE + "ignoreBrowserAlert", false);
        public static final String BROWSER_META = registerSysVar(NS_BROWSER + ".meta");
        public static final String PROFILE_WEB_COMMAND = NS_BROWSER + ".command";

        public static final String BROWSER_OPENED = registerSysVar(NS_BROWSER + ".isOpen", false);
        public static final String CURRENT_BROWSER = registerSysVar(NS_BROWSER + ".current");

        // geolocation
        public static final String GEOLOCATION = registerSysVar(NS_BROWSER + ".geolocation", false);
        public static final String GEO_LONGITUDE = registerSysVar(GEOLOCATION + ".longitude", 0);
        public static final String GEO_LATITUDE = registerSysVar(GEOLOCATION + ".latitude", 0);

        // metrics
        public static final String NS_WEB_METRICS = registerSysVarGroup(NS_WEB + "metrics.");
        public static final String WEB_METRICS_GENERATED = NS_WEB_METRICS + "generated";
        public static final String WEB_PERF_METRICS_ENABLED = registerSysVar(NS_WEB_METRICS + "enabled", false);
        public static final String WEB_CLEAR_WITH_BACKSPACE = registerSysVar(NS_WEB + "clearWithBackspace", false);
        public static final String WEB_PREEMPTIVE_ALERT_CHECK = registerSysVar(NS_WEB + "preemptiveAlertCheck", false);
        public static final String WEB_ALWAYS_WAIT = registerSysVar(NS_WEB + "alwaysWait", false);
        // `nexial.web.explicitWait` replaces `nexial.web.alwaysWait` -- it's more meaningful
        public static final String WEB_EXPLICIT_WAIT = registerSysVar(NS_WEB + "explicitWait", true);
        public static final String WEB_UNFOCUS_AFTER_TYPE = registerSysVar(NS_WEB + "unfocusAfterType", false);
        public static final String WEB_PAGE_LOAD_WAIT_MS = registerSysVar(NS_WEB + "pageLoadWaitMs", 15000);
        public static final String DROPDOWN_SELECT_ALL = "{ALL}";
        public static final String WEB_METRICS_JSON = "browser-metrics.json";
        public static final String WEB_METRICS_HTML = "browser-metrics.html";
        public static final String WEB_METRICS_TOKEN = "{METRICS}";
        public static final String WEB_METRICS_HTML_LOC = "/org/nexial/core/reports/";
        public static final String[] NON_PERF_METRICS_COMMAND_PREFIXES = {
            "assert", "saveAll", "saveBrowserVersion", "saveCount", "saveLoc", "saveSelected", "saveText", "saveVal",
            "screenshot", "scroll", "selectFrame", "selectText", "selectWindow", "switchBrowser", "unselectAll",
            "update", "verify", "close", "closeAll"
        };

        // web element highlight
        public static final String NS_HIGHLIGHT = NS_WEB + "highlight";
        public static final String OPT_DEBUG_HIGHLIGHT = registerSysVar(NS_HIGHLIGHT, false);
        public static final String HIGHLIGHT_STYLE = registerSysVar(NS_HIGHLIGHT + ".style", "background:#faf557;");
        public static final String HIGHLIGHT_WAIT_MS = registerSysVar(NS_HIGHLIGHT + ".waitMs", 250);
        public static final String OPT_DEBUG_HIGHLIGHT_OLD = registerSysVar(NAMESPACE + "highlight", false);
        public static final String HIGHLIGHT_WAIT_MS_OLD = registerSysVar(NAMESPACE + "highlightWaitMs", 250);

        // web drag-and-move config
        public static final String OPT_DRAG_FROM_LEFT_CORNER = "left";
        public static final String OPT_DRAG_FROM_RIGHT_CORNER = "right";
        public static final String OPT_DRAG_FROM_TOP_CORNER = "top";
        public static final String OPT_DRAG_FROM_BOTTOM_CORNER = "bottom";
        public static final String OPT_DRAG_FROM_MIDDLE = "middle";
        public static final String OPT_DRAG_FROM = registerSysVar(NS_WEB + "dragFrom", OPT_DRAG_FROM_MIDDLE);
        public static final List<String> OPT_DRAG_FROMS = Arrays.asList(OPT_DRAG_FROM_LEFT_CORNER,
                                                                        OPT_DRAG_FROM_RIGHT_CORNER,
                                                                        OPT_DRAG_FROM_TOP_CORNER,
                                                                        OPT_DRAG_FROM_BOTTOM_CORNER,
                                                                        OPT_DRAG_FROM_MIDDLE);
        public static final String OPT_DND_NATIVE = registerSysVar(NS_WEB + "dragNative", "false");
        public static final String OPT_DND_NATIVE_X_OFFSET = registerSysVar(NS_WEB + "dragNativeXOffset", "false");
        public static final String OPT_DND_NATIVE_Y_OFFSET = registerSysVar(NS_WEB + "dragNativeYOffset", "false");

        // selenium specific
        public static final String SELENIUM_CHROME_DRIVER = "webdriver.chrome.driver";
        public static final String SELENIUM_CHROME_BIN = "webdriver.chrome.bin";
        public static final String SELENIUM_GECKO_DRIVER = "webdriver.gecko.driver";
        public static final String SELENIUM_FIREFOX_BIN = "webdriver.firefox.bin";
        public static final String SELENIUM_FIREFOX_PROFILE = "webdriver.firefox.profile";
        public static final String SELENIUM_EDGE_BIN = "webdriver.edge.bin";
        public static final String SELENIUM_EDGE_DRIVER = "webdriver.edge.driver";
        public static final String SELENIUM_IE_DRIVER = "webdriver.ie.driver";
        public static final String SELENIUM_IE_LOG_LEVEL = "webdriver.ie.driver.loglevel";
        public static final String SELENIUM_IE_LOG_LOGFILE = "webdriver.ie.driver.logfile";
        public static final String SELENIUM_IE_SILENT = "webdriver.ie.driver.silent";
        public static final String RGBA_TRANSPARENT = "rgba(0, 0, 0, 0)";
        public static final String RGBA_TRANSPARENT2 = "rgba(0,0,0,0)";
        public static final String REGEX_IS_RGBA = "rgba\\([0-9,\\ ]+\\)";
        public static final String REGEX_IS_HEX_COLOR = "^#([0-9a-f]{3}|[0-9a-f]{6})$";

        private Web() {}

        // reference by enclosing class to force initialization (possibly prior to any reference at runtime)
        static void init() {}
    }

    public static final class Mobile {
        public static final String NS_MOBILE = NAMESPACE + "mobile";

        public static final String TYPE = "type";
        public static final String URL = "url";

        // default 5 second time out for explicit wait
        public static final String EXPLICIT_WAIT_MS = registerSysVar(NS_MOBILE + ".explicitWaitMs", 5000);

        // default 2 second time out for implicit wait
        public static final String IMPLICIT_WAIT_MS = registerSysVar(NS_MOBILE + ".implicitWaitMs", 0);

        // default 0 means no time out
        public static final String SESSION_TIMEOUT_MS = registerSysVar(NS_MOBILE + ".sessionTimeoutMs", 0);

        // delay between click, type, swipe, tab, etc. actions
        public static final String POST_ACTION_WAIT_MS = registerSysVar(NS_MOBILE + ".postActionWaitMs", 0);

        // high keyboard before typing
        public static final String HIDE_KEYBOARD = registerSysVar(NS_MOBILE + ".hideKeyboard", true);

        public static final int MIN_WAIT_MS = 200;

        public static final String ERR_NO_SERVICE =
            "No mobile driver available at this time. Please be sure to invoke use(profile) command prior to other " +
            "mobile commands. See https://nexiality.github.io/documentation/commands/mobile/use(profile) for details";

        // avoid pinching, zooming or dragging from screen edges
        public static final int EDGE_WIDTH = 10;

        // log level to use when configuring appium to send log to external file
        public static final String FILE_CONSOLE_LOG_LEVEL = "warn:debug";

        protected static final String SCRIPT_EXT = (IS_OS_WINDOWS ? "bat" : "sh");

        public static final class Android {
            public static final String RESOURCE_BASE = "https://nexiality.github.io/documentation" +
                                                       "/commands/mobile/resources";
            public static final String CMDLINE_TOOLS_REDIRECT_URL = RESOURCE_BASE + "/android_cmdtools.url";
            public static final String ANDROID_SDK_LICENSE_ZIP_URL = RESOURCE_BASE + "/android_sdk_licenses.zip";
            public static final String ANDROID_SDK_SKINS_ZIP_URL = RESOURCE_BASE + "/android_sdk_skins.zip";
            public static final String ANDROID_EMULATORS_URL = RESOURCE_BASE + "/android_emulators.json";

            public static final String AVD_MANAGER_REL_PATH = "bin" + separator + "avdmanager." + SCRIPT_EXT;
            public static final String SDK_MANAGER_REL_PATH = "bin" + separator + "sdkmanager." + SCRIPT_EXT;
            public static final String SDK_REL_PATH = "android" + separator + "sdk";

            public static final String ANDROID_AVD_HOME =
                StringUtils.appendIfMissing(new File(USER_HOME).getAbsolutePath(), separator) +
                ".android" + separator + "avd";

            public static final String ANDROID_SDK_HOME = USER_NEXIAL_HOME + SDK_REL_PATH;
            public static final String LICENSE_PATH = ANDROID_SDK_HOME + separator + "licenses";
            public static final String SKIN_PATH = ANDROID_SDK_HOME + separator + "skins";
            public static final String EMULATOR_PATH = ANDROID_SDK_HOME + separator + "emulator";
            public static final String BUILD_TOOLS_PATH = ANDROID_SDK_HOME + separator + "build-tools";
            public static final String AVD_MANAGER = ANDROID_SDK_HOME + separator +
                                                     "cmdline-tools" +  separator + "latest" + separator +
                                                     AVD_MANAGER_REL_PATH;
            public static final String APK_SIGNER_FILE = "apksigner.jar";
            public static final String APK_SIGNER_DEST = ANDROID_SDK_HOME + separator + "tools" + separator + "lib" +
                                                         separator + APK_SIGNER_FILE;
            public static final String SYSTEM_IMAGES_PREFIX = "system-images;";
            public static final String DEF_SYS_IMG_64 = SYSTEM_IMAGES_PREFIX + "android-30;google_apis;x86_64";
            public static final String DEF_SYS_IMG_32 = SYSTEM_IMAGES_PREFIX + "android-30;google_apis;x86";

            public static final String CMDLINE_TOOLS_PATH = StringUtils.appendIfMissing(JAVA_IO_TMPDIR, separator) +
                                                            "nexial" + separator + "android" + separator + "cmdline-tools";
            public static final String SDK_MANAGER = CMDLINE_TOOLS_PATH + separator + SDK_MANAGER_REL_PATH;


        }
    }

    private NexialConst() { }

    public static String handleWindowsChar(String name) {
        Set<String> keys = replaceWindowsChars.keySet();
        for (String key : keys) { name = StringUtils.replace(name, key, replaceWindowsChars.get(key)); }
        return name;
    }

    @NotNull
    public static String failAfterReached(int failCount, int failAfter) {
        return MSG_ABORT + "execution fail count (" + failCount + ") exceeds fail-after limit (" + failAfter + "); " +
               "setting fail-immediate to true";
    }

    public static boolean isKnownTextContentType(String contentType) {
        if (StringUtils.isBlank(contentType) || StringUtils.containsAny(contentType, "stream", "bin", "octet") ||
            StringUtils.startsWithAny(contentType, "audio", "font", "image", "video")) {
            return false;
        }

        return StringUtils.startsWithAny(contentType, "text") ||
               RegexUtils.match(contentType, ".+(xml|XML|json|sgml|html|script|jwt|)$");
    }

    public static boolean isAutoOpenExecResult() {
        if (ExecUtils.isRunningInZeroTouchEnv()) {
            ConsoleUtils.log(MSG_SKIP_AUTO_OPEN_RESULT);
            return false;
        }

        // if we are showing Excel, then we should also show HTML
        if (isAutoOpenResult()) { return true; }

        ExecutionContext context = ExecutionThread.get();
        return context != null ?
               context.getBooleanData(OPT_OPEN_EXEC_REPORT, getDefaultBool(OPT_OPEN_EXEC_REPORT)) :
               BooleanUtils.toBoolean(System.getProperty(OPT_OPEN_EXEC_REPORT, getDefault(OPT_OPEN_EXEC_REPORT)));
    }

    public static boolean isAutoOpenResult() {
        if (ExecUtils.isRunningInZeroTouchEnv()) { return false; }

        ExecutionContext context = ExecutionThread.get();
        boolean def = getDefaultBool(ASSISTANT_MODE);
        if (context != null) {
            return context.getBooleanData(OPT_OPEN_RESULT, context.getBooleanData(ASSISTANT_MODE, def));
        } else {
            return BooleanUtils.toBoolean(System.getProperty(OPT_OPEN_RESULT, System.getProperty(ASSISTANT_MODE,
                                                                                                 def + "")));
        }
    }

    public static boolean isOutputToCloud() {
        return BooleanUtils.toBoolean(System.getProperty(OUTPUT_TO_CLOUD, getDefaultBool(OUTPUT_TO_CLOUD) + ""));
    }

    public static boolean isGenerateExecReport() {
        return isOutputToCloud() ||
               BooleanUtils.toBoolean(System.getProperty(GENERATE_EXEC_REPORT, getDefault(GENERATE_EXEC_REPORT)));
    }

    public static String treatCommonValueShorthand(String text) {
        if (StringUtils.isBlank(text)) { return text; }

        String[] text1 = new String[]{text};

        // only `EMPTY`, `BLANK`, `TAB` or `NL` or combination of these are found
        if (RegexUtils.match(text1[0], REGEX_ONLY_NON_DISPLAYABLES)) {
            NON_DISPLAYABLE_REPLACEMENTS.forEach(
                (shorthand, replacement) -> text1[0] = StringUtils.replace(text1[0], shorthand, replacement));
        }

        return text1[0];
    }

    public static String toCloudIntegrationNotReadyMessage(String data) {
        return "Unable to save " + (StringUtils.isBlank(data) ? "content" : data) + " to cloud storage since " +
               "Nexial Cloud Integration is not properly configured. See " +
               DOCUMENTATION_URL + "/systemvars/index.html#nexial.outputToCloud " +
               "for more details.";
    }

    public class Doc {
        private Doc() { }

        // default values
        public static final String FUNCTIONS = "functions";
        public static final String EXPRESSIONS = "expressions";
        public static final String SYSTEMVARS = "systemvars";

        public static final String DOCUMENTATION_URL = "https://nexiality.github.io/documentation";
        public static final String SYSVAR_DOCS_URL = DOCUMENTATION_URL + "/" + SYSTEMVARS + "/";
        public static final String EXPRESSIONS_DOCS_URL = DOCUMENTATION_URL + "/" + EXPRESSIONS + "/";
        public static final String FUNCTIONS_DOCS_URL = DOCUMENTATION_URL + "/" + FUNCTIONS + "/";
        public static final String MD_EXTENSION = ".md";
        public static final String HTML_EXTENSION = ".html";
        public static final String MINI = ".mini";
        public static final String MINI_HTML = MINI + HTML_EXTENSION;

    }

    static {
        // warm up constant classes
        Data.init();
        Iteration.init();
        FlowControls.init();
        Integration.init();
        Image.init();
        AwsSettings.init();
        Project.init();
        CLI.init();
        Exec.init();
        Mailer.init();
        Desktop.init();
        Compare.init();
        SoapUI.init();
        Jenkins.init();
        ExitStatus.init();
        Notification.init();
        TMSSettings.init();
        TimeTrack.init();
        Ssh.init();
        Rdbms.init();
        Ws.init();
        Web.init();
        NexialConst.BrowserStack.init();
        NexialConst.CrossBrowserTesting.init();
        Pdf.init();
        ImageCaption.init();
        SaveGridAsCSV.init();

        // don't need this unnecessary noise
        // BUT WE CANNOT TURN THIS ONE FOR JAVA 8 (IT DOESN'T KNOW ANYTHING ABOUT THIS ARG!)
        if (!StringUtils.contains(SystemUtils.JAVA_VERSION, "8")) {
            System.setProperty("nashorn.args", "--no-deprecation-warning");
        }
    }

}