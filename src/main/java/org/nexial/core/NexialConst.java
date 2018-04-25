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

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.commons.cli.Options;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.excel.ExcelConfig;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.ExecutionDefinition;
import org.nexial.core.model.TestProject;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import static java.awt.image.BufferedImage.*;
import static java.io.File.separator;
import static org.apache.commons.lang3.SystemUtils.IS_OS_WINDOWS;

/**
 * constants
 */
public final class NexialConst {

    // default values
    public static final String DATE_FORMAT_NOW = "yyyy-MM-dd-HH-mm-ss.S";
    public static final String COOKIE_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss ZZZ";
    public static final DateFormat DF_TIMESTAMP = new SimpleDateFormat("yyyyMMdd_HHmmss");
    public static final String EPOCH = "epoch";
    public static final String RATE_FORMAT = "{0,number,0.00%}";

    public static final int DEF_SLEEP_MS = 800;
    public static final int DEF_UI_RENDER_WAIT_MS = 3000;
    public static final int MIN_STABILITY_WAIT_MS = 400;
    public static final int LAUNCHER_THREAD_COMPLETION_WAIT_MS = 1000;
    public static final int ELEM_PRESENT_WAIT_MS = 1200;
    public static final int MIN_LOADING_WAIT_MS = 50;
    public static final long ONEDAY = 24 * 60 * 60 * 1000;
    public static final long MS_UNDEFINED = -1;
    public static final int UNDEFINED_INT_DATA = -1;
    public static final double UNDEFINED_DOUBLE_DATA = -1;

    public static final String OPT_SPRING_XML = "nexial.spring.config";
    public static final String DEF_SPRING_XML = "/nexial.xml";
    public static final String DELIM_EMAIL = ";";

    // public static final int MAX_COLUMNS = EXCEL2007.getMaxColumns();
    // public static final int MAX_ROWS = EXCEL2007.getMaxRows();

    // predefined variables/switches
    public static final String NAMESPACE = "nexial.";
    public static final String OPT_OUT_DIR = NAMESPACE + "outBase";
    public static final String OPT_PLAN_DIR = NAMESPACE + "planBase";
    public static final String OPT_DATA_DIR = NAMESPACE + "dataBase";
    public static final String OPT_SCRIPT_DIR = NAMESPACE + "scriptBase";
    public static final String OPT_PROJECT_BASE = NAMESPACE + "projectBase";
    public static final String OPT_PROJECT_NAME = NAMESPACE + "project";

    //public static final String OPT_CURRENT = NAMESPACE + "current.";
    //public static final String OPT_CURRENT_DATA_FILE = OPT_CURRENT + "dataFile";
    //public static final String OPT_CURRENT_TEST_SCRIPT = OPT_CURRENT + "testScript";
    //public static final String OPT_CURRENT_TEST_PLAN = OPT_CURRENT + "testPlan";

    // testcase specific
    public static final String TEST_START_TS = "testsuite.startTs";
    public static final String TEST_SUITE_NAME = NAMESPACE + "testsuite.name";
    public static final String TEST_NAME = "test.name";

    public static final String OPT_EXCEL_FILE = NAMESPACE + "excel";
    public static final String OPT_EXCEL_WORKSHEET = NAMESPACE + "worksheet";
    public static final String OPT_INPUT_EXCEL_FILE = NAMESPACE + "inputExcel";
    public static final String OPT_LAST_TEST_SCENARIO = NAMESPACE + "lastTestScenario";
    public static final String OPT_LAST_TEST_STEP = NAMESPACE + "lastTestStep";
    public static final String OPT_SUITE_PROP = NAMESPACE + "suite";
    public static final String OPT_MAILTO = NAMESPACE + "mailTo";

    // selenium specific
    public static final String SELENIUM_IE_DRIVER = "webdriver.ie.driver";
    public static final String SELENIUM_CHROME_DRIVER = "webdriver.chrome.driver";
    public static final String SELENIUM_GECKO_DRIVER = "webdriver.gecko.driver";
    public static final String SELENIUM_CHROME_BIN = "webdriver.chrome.bin";
    public static final String SELENIUM_FIREFOX_BIN = "webdriver.firefox.bin";
    public static final String SELENIUM_FIREFOX_PROFILE = "webdriver.firefox.profile";
    public static final String SELENIUM_IE_LOG_LEVEL = "webdriver.ie.driver.loglevel";
    public static final String SELENIUM_IE_LOG_LOGFILE = "webdriver.ie.driver.logfile";
    public static final String SELENIUM_IE_SILENT = "webdriver.ie.driver.silent";

    public static final String OPT_DELAY_BROWSER = NAMESPACE + "delayBrowser";
    public static final String OPT_CHROME_PROFILE = NAMESPACE + "chrome.profile";
    public static final String OPT_EASY_STRING_COMPARE = NAMESPACE + "lenientStringCompare";
    public static final String OPT_HTTP_TTL = NAMESPACE + "httpTTL";
    public static final String OPT_UI_RENDER_WAIT_MS = NAMESPACE + "uiRenderWaitMs";
    public static final String OPT_LAST_ALERT_TEXT = NAMESPACE + "lastAlertText";
    public static final String OPT_ALERT_IGNORE_FLAG = NAMESPACE + "ignoreBrowserAlert";
    public static final String OPT_WAIT_SPEED = NAMESPACE + "waitSpeed";
    public static final String OPT_FORCE_IE_32 = NAMESPACE + "forceIE32";
    public static final boolean DEFAULT_FORCE_IE_32 = false;
    public static final String WEB_ALWAYS_WAIT = NAMESPACE + "web.alwaysWait";
    public static final boolean DEF_WEB_ALWAYS_WAIT = false;

    // highlight
    public static final String OPT_DEBUG_HIGHLIGHT = NAMESPACE + "highlight";
    public static final String STYLE_HIGHLIGHT = "background:#faf557;";
    public static final String HIGHLIGHT_WAIT_MS = NAMESPACE + "highlightWaitMs";
    public static final int DEF_HIGHLIGHT_WAIT_MS = 250;

    // todo: need to evaluate how to use these 3 to modify the nexial result and excel output
    public static final String COMPARE_INCLUDE_MOVED = NAMESPACE + "io.compareIncludeMoved";
    public static final String COMPARE_INCLUDE_ADDED = NAMESPACE + "io.compareIncludeAdded";
    public static final String COMPARE_INCLUDE_DELETED = NAMESPACE + "io.compareIncludeRemoved";

    public static final String OPT_PROXY_USER = NAMESPACE + "proxy_user";
    public static final String OPT_PROXY_PASSWORD = NAMESPACE + "proxy_password";
    public static final String OPT_PROXY_REQUIRED = NAMESPACE + "proxyRequired";
    public static final String OPT_PROXY_DIRECT = NAMESPACE + "proxyDirect";
    public static final int PROXY_PORT = 19850;

    public static final String OPT_IMAGE_TOLERANCE = NAMESPACE + "imageTolerance";

    // derived from System property ONLY -- should be set to each automation server
    public static final String OPT_REPORT_SERVER_URL = NAMESPACE + "reportServerUrl";
    public static final String OPT_REPORT_SERVER_BASEDIR = NAMESPACE + "reportServerBaseDir";
    public static final String OPT_SCREENSHOT_ON_ERROR = NAMESPACE + "screenshotOnError";
    public static final String OPT_LAST_SCREENSHOT_NAME = NAMESPACE + "lastScreenshot";
    public static final String OPT_LAST_OUTCOME = NAMESPACE + "lastOutcome";
    public static final String OPT_LAST_NESTED_OUTPUT = NAMESPACE +"lastCommandOutput";

    // control verbosity of multi-step commands
    public static final String OPT_ELAPSED_TIME_SLA = NAMESPACE + "elapsedTimeSLA";

    // allow per-run override for the output directory name
    // nexial.runID is set by castle via Jenkin's internal BUILD_ID variable, which reflects build time in
    // the format of YYYY-MM-DD_hh-mm-ss
    public static final String OPT_RUN_ID = NAMESPACE + "runID";
    public static final String OPT_RUN_ID_PREFIX = OPT_RUN_ID + ".prefix";

    // plugin:external
    // store the file name of the output resulted from a `external.runProgram` command
    public static final String OPT_RUN_PROGRAM_OUTPUT = NAMESPACE + "external.output";

    // plugin:rdbms
    public static final String DAO_PREFIX = NAMESPACE + "dao.";
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
    public static final String OPT_INCLUDE_PACK_SINGLE_ROW = NAMESPACE + "rdbms.packSingleRow";
    public static final boolean DEF_INCLUDE_PACK_SINGLE_ROW = false;
    public static final String SQL_LINE_SEP = "\n";
    public static final String CSV_ROW_SEP = "\n";
    public static final String CSV_FIELD_DEIM = ",";

    public static final String WS_NAMESPACE = NAMESPACE + "ws.";
    public static final String WS_CONN_TIMEOUT = WS_NAMESPACE + "connectionTimeout";
    public static final String WS_READ_TIMEOUT = WS_NAMESPACE + "readTimeout";
    public static final String WS_ENABLE_REDIRECTS = WS_NAMESPACE + "enableRedirects";
    public static final String WS_ENABLE_EXPECT_CONTINUE = WS_NAMESPACE + "enableExpectContinue";
    public static final String WS_ALLOW_CIRCULAR_REDIRECTS = WS_NAMESPACE + "allowCircularRedirects";
    public static final String WS_ALLOW_RELATIVE_REDIRECTS = WS_NAMESPACE + "allowRelativeRedirects";
    public static final String WS_REQ_PAYLOAD_COMPACT = WS_NAMESPACE + "requestPayloadCompact";
    //public static final String WS_REQ_CONTENT_TYPE = WS_NAMESPACE + "requestContentType";
    //public static final String WS_RES_PAYLOAD_COMPACT = WS_NAMESPACE + "responsePayloadCompact";
    public static final String WS_REQ_HEADER_PREFIX = WS_NAMESPACE + "header.";
    public static final String WS_PROXY_REQUIRED = WS_NAMESPACE + "proxyRequired";
    public static final String WS_PROXY_HOST = WS_NAMESPACE + "proxyHost";
    public static final String WS_PROXY_PORT = WS_NAMESPACE + "proxyPort";
    public static final String WS_PROXY_USER = WS_NAMESPACE + "proxyUser";
    public static final String WS_PROXY_PWD = WS_NAMESPACE + "proxyPassword";
    public static final String WS_DIGEST_NAMESPACE = WS_NAMESPACE + "digest.";
    public static final String WS_DIGEST_USER = WS_DIGEST_NAMESPACE + "user";
    public static final String WS_DIGEST_PWD = WS_DIGEST_NAMESPACE + "password";
    public static final String WS_DIGEST_REALM = WS_DIGEST_NAMESPACE + "realm";
    public static final String WS_DIGEST_NONCE = WS_DIGEST_NAMESPACE + "nonce";
    public static final String WS_BASIC_NAMESPACE = WS_NAMESPACE + "basic.";
    public static final String WS_BASIC_USER = WS_BASIC_NAMESPACE + "user";
    public static final String WS_BASIC_PWD = WS_BASIC_NAMESPACE + "password";
    public static final String WS_USER_AGENT = "User-Agent";
    public static final String WS_CONTENT_TYPE = "Content-Type";
    public static final String WS_CONTENT_LENGTH = "Content-Length";
    public static final String WS_JSON_CONTENT_TYPE = "application/json";
    public static final String WS_JSON_CONTENT_TYPE2 = "application/json;charset=UTF-8";
    public static final String WS_SOAP_CONTENT_TYPE = "text/xml;charset=UTF-8";
    public static final String WS_FORM_CONTENT_TYPE = "application/x-www-form-urlencoded";
    public static final boolean DEF_WS_REQ_PAYLOAD_COMPACT = false;
    public static final int DEF_WS_CONN_TIMEOUT = 5 * 60 * 1000;
    public static final int DEF_WS_READ_TIMEOUT = 5 * 60 * 1000;
    public static final boolean DEF_WS_ENABLE_REDIRECTS = false;
    public static final boolean DEF_WS_ENABLE_EXPECT_CONTINUE = false;
    public static final boolean DEF_WS_CIRCULAR_REDIRECTS = false;
    public static final boolean DEF_WS_RELATIVE_REDIRECTS = false;

    // oauth
    public static final String OAUTH_CLIENT_ID = "client_id";
    public static final String OAUTH_CLIENT_SECRET = "client_secret";
    public static final String OAUTH_SCOPE = "scope";
    public static final String OAUTH_GRANT_TYPE = "grant_type";
    public static final List<String> OAUTH_REQUIRED_INPUTS = Arrays.asList(OAUTH_CLIENT_ID,
                                                                           OAUTH_CLIENT_SECRET,
                                                                           OAUTH_GRANT_TYPE);
    public static final String OAUTH_ACCESS_TOKEN = "access_token";
    public static final String OAUTH_TOKEN_TYPE = "token_type";
    public static final String OAUTH_TOKEN_TYPE_BEARER = "Bearer";
    public static final String OAUTH_TOKEN_TYPE_BEARER2 = "BearerToken";
    public static final String OAUTH_TOKEN_TYPE_MAC = "MAC";
    public static final String OAUTH_TOKEN_TYPE_BASIC = "Basic";

    public static final String DEF_FILE_ENCODING = "UTF-8";
    public static final String DEF_CHARSET = DEF_FILE_ENCODING;

    // predefined subdirectories
    public static final String SUBDIR_LOGS = "logs";
    public static final String SUBDIR_CAPTURES = "captures";

    // naming scheme
    public static final String PLAN_SCRIPT_SEP = ",";
    public static final String FILE_PART_SEP = ".";
    public static final String ITER_PREFIX = FILE_PART_SEP;
    public static final String REGEX_ITER_INDEX = ".+\\" + ITER_PREFIX + "([\\d]+)\\.xls.*";
    public static final String SCREENSHOT_EXT = ".png";
    public static final String DEF_PLAN_SERIAL_MODE = "true";
    public static final String DEF_PLAN_FAIL_FAST = "true";

    // predefined SMTP system properties as required by caps-framework
    public static final String MAIL_KEY_AUTH = "mail.smtp.auth";
    public static final String MAIL_KEY_USERNAME = "mail.smtp.username";
    public static final String MAIL_KEY_PASSWORD = "mail.smtp.password";
    public static final String MAIL_KEY_DEBUG = "mail.smtp.debug";
    public static final String MAIL_KEY_MAIL_HOST = "mail.smtp.host";
    public static final String MAIL_KEY_MAIL_PORT = "mail.smtp.port";
    public static final String MAIL_KEY_PROTOCOL = "mail.transport.protocol";
    public static final String MAIL_KEY_SMTP_LOCALHOST = "mail.smtp.localhost";
    public static final String MAIL_KEY_MAIL_JNDI_URL = "mail.jndi.url";
    public static final String MAIL_KEY_BUFF_SIZE = "mail.smtp.bufferSize";
    public static final String MAIL_KEY_TLS_ENABLE = "mail.smtp.starttls.enable";
    public static final String MAIL_KEY_CONTENT_TYPE = "mail.smtp.contentType";
    public static final List<String> MAIL_KEYS =
        Arrays.asList(MAIL_KEY_AUTH, MAIL_KEY_USERNAME, MAIL_KEY_PASSWORD, MAIL_KEY_DEBUG, MAIL_KEY_MAIL_HOST,
                      MAIL_KEY_MAIL_PORT, MAIL_KEY_PROTOCOL, MAIL_KEY_SMTP_LOCALHOST, MAIL_KEY_MAIL_JNDI_URL,
                      MAIL_KEY_BUFF_SIZE, MAIL_KEY_TLS_ENABLE, MAIL_KEY_CONTENT_TYPE);
    // mailer specific
    public static final String OPT_MAIL_FROM = "mail.smtp.from";
    public static final String OPT_MAIL_CC = "mail.smtp.cc";
    public static final String OPT_MAIL_BCC = "mail.smtp.bcc";
    public static final String OPT_MAIL_XMAILER = "mail.header.xmail";
    public static final String OPT_MAIL_CONTENT_TYPE = "mail.smtp.contentType";

    public static final String OPT_EXCEL_VER = NAMESPACE + "excelVer";
    public static final String OPT_INTERACTIVE = NAMESPACE + "interactive";
    public static final String OPT_INTERACTIVE_DEBUG = NAMESPACE + "interactive.debug";

    //browsermob proxy
    public static final String OPT_PROXY_ENABLE = "proxy.enable";
    public static final String OPT_PROXY_LOCALHOST = "localhost:98986";

    //browsermob har
    public static final String OPT_HAR_CURRENT = "_harCurrent";
    public static final String OPT_HAR_BASE = "_harBase";
    public static final String OPT_HAR_POST_DATA_BASE = "_postDataBase.csv";
    public static final String OPT_HAR_POST_DATA_CURRENT = "_postDataCurrent.csv";
    public static final String OPT_HAR_POST_DATA_INTERCEPTED = "_postDataIntercepted.csv";
    public static final String OPT_HAR_POST_DATA_RESULTS = "_postDataResults.csv";
    public static final String OPT_HAR_POST_COLUMN_NAMES = "Post Sequence,URL,Baseline,Current";

    //browsermob wsdl
    public static final String OPT_WSDL_BASE = "_WSDLBase.wsdl";
    public static final String OPT_WSDL_CURRENT = "_WSDLCurrent.wsdl";
    public static final String OPT_WSDL_RESULTS = "_WSDLResults.txt";

    // token specific
    public static final String TOKEN_START = "${";
    public static final String TOKEN_END = "}";
    public static final String TOKEN_FUNCTION_START = "$(";
    public static final String TOKEN_FUNCTION_END = ")";
    public static final String TOKEN_DEFUNC_START = "~!~5En7ryR0cks![[";
    public static final String TOKEN_DEFUNC_END = "]]~!~";
    public static final String TOKEN_ARRAY_START = "[";
    public static final String TOKEN_ARRAY_END = "]";
    public static final String DEFERED_TOKEN_START = "${";
    public static final String DEFERED_TOKEN_END = "}";
    public static final String CTRL_KEY_START = "{";
    public static final String CTRL_KEY_END = "}";
    // public static final String TEST_STEP_PREFIX = "test.";
    // public static final String REGEX_SCRIPT_WITH_STEPNAME = "(.*\\.xlsx)(\\" + TOKEN_ARRAY_START + ".+\\" + TOKEN_ARRAY_END + ")";

    // regex for built-in function
    public static final String REGEX_DYNAMIC_VARIABLE_VALUE = "([\\w]+)\\|([\\w]+)(\\|(.+)+)*";

    public static final String REGEX_VALID_WEB_PROTOCOL = "(http|https|ftp|file|about)\\:.+";
    public static final String CMD_VERBOSE = "base.verbose(text)";
    public static final List<String> MERGE_OUTPUTS = Arrays.asList(".", CMD_VERBOSE);
    public static final int MAX_VERBOSE_CHAR = 2000;

    // predefined messages
    public static final String MSG_PASS = ExcelConfig.MSG_PASS;
    public static final String MSG_FAIL = ExcelConfig.MSG_FAIL;
    public static final String MSG_WARN = ExcelConfig.MSG_WARN;
    public static final String MSG_SKIPPED = ExcelConfig.MSG_SKIPPED;
    public static final String MSG_DEPRECATED = ExcelConfig.MSG_DEPRECATED;
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
    public static final String OPT_CLOUD_OUTPUT_BASE = NAMESPACE + "outputCloudBase";
    public static final String OUTPUT_TO_CLOUD = NAMESPACE + "outputToCloud";
    public static final String S3_PATH_SEPARATOR = "/";
    public static final boolean DEF_OUTPUT_TO_CLOUD = false;

    public static final String OPT_MANAGE_MEM = NAMESPACE + "manageMemory";
    public static final String DEF_MANAGE_MEM = "false";

    public static final String USER_OS_HOME = "user.home";
    public static final String ENCRYPTION_ALGORITHM = "AES";
    public static final String SECRET_FILE = "config.data";
    public static final String SECRET_KEY_FILE = "config.key";
    public static final String SECRET_CONTENT_SEPARATOR = "########";
    public static final int RAND_SEED_SIZE = 5;

    public static final Gson GSON = new GsonBuilder().setPrettyPrinting()
                                                     .disableHtmlEscaping()
                                                     .disableInnerClassSerialization()
                                                     .setLenient()
                                                     .create();

    // browser types
    public enum BrowserType {
        firefox(true, true, true, true),
        firefoxheadless(true, true, true, true),
        safari(false, true, true, true),
        chrome(true, false, true, true),
        chromeheadless(true, false, true, true),
        ie(false, false, true, false),
        iphone(false, false, false, false),
        browserstack(false, false, false, true),
        chromeembedded(false, false, true, true);

        private boolean profileSupported;
        private boolean consoleLoggingEnabled;
        private boolean timeoutChangesEnabled;
        private boolean jsEventFavored;

        BrowserType(boolean profileSupported,
                    boolean consoleLoggingEnabled,
                    boolean timeoutChangesEnabled,
                    boolean jsEventFavored) {
            this.profileSupported = profileSupported;
            this.consoleLoggingEnabled = consoleLoggingEnabled;
            this.timeoutChangesEnabled = timeoutChangesEnabled;
            this.jsEventFavored = jsEventFavored;
        }

        public boolean isProfileSupported() { return profileSupported; }

        public boolean isConsoleLoggingEnabled() { return consoleLoggingEnabled; }

        public boolean isTimeoutChangesEnabled() { return timeoutChangesEnabled; }

        public boolean isJsEventFavored() { return jsEventFavored; }
    }

    public enum CrystalReportExportType {
        rpt(".rpt", "Crystal Reports (RPT)"),
        pdf(".pdf", "PDF"),
        xls_formatted(".xls", "Microsoft Excel (97-2003)"),
        xls(".xls", "Microsoft Excel (97-2003) Data-Only"),
        xlsx(".xlsx", "Microsoft Excel Workbook Data-only"),
        doc(".rtf", "Microsoft Word (97-2003)"),
        doc_editable(".rtf", "Microsoft Word (97-2003) - Editable"),
        rtf(".rtf", "Rich Text Format (RTF)"),
        csv(".csv", "Separated Values (CSV)"),
        xml(".xml", "XML"),
        NONE("", "");

        private String extension;
        private String description;

        CrystalReportExportType(String extension, String description) {
            this.extension = extension;
            this.description = description;
        }

        public String getExtension() { return extension; }

        public String getDescription() { return description; }
    }

    public enum ImageType {
        png(TYPE_INT_RGB),
        jpg(TYPE_INT_RGB),
        gif(TYPE_INT_ARGB),
        bmp(TYPE_INT_ARGB);

        private int imageType;

        ImageType(int imageType) { this.imageType = imageType; }

        public int getImageType() { return imageType; }
    }

    public static final class AwsSettings {
        public static final String AWS_ACCESS_KEY = "accessKey";
        public static final String AWS_SECRET_KEY = "secretKey";
        public static final String AWS_REGION = "region";
        public static final String SUFFIX = "aws";

        private AwsSettings() {}
    }

    // browserstack
    public static class BrowserStack {
        public static final String BASE_PROTOCOL = "http://";
        public static final String BASE_URL = "@hub.browserstack.com/wd/hub";
        public static final boolean DEF_ENABLE_LOCAL = false;
        public static final boolean DEF_DEBUG = true;
        private static final String NS = NAMESPACE + "browserstack.";
        public static final String KEY_USERNAME = NS + "username";
        public static final String KEY_AUTOMATEKEY = NS + "automatekey";
        public static final String KEY_BROWSER = NS + "browser";
        public static final String KEY_BROWSER_VER = NS + "browser.version";
        public static final String KEY_DEBUG = NS + "debug";
        public static final String KEY_RESOLUTION = NS + "resolution";
        public static final String KEY_BUILD_NUM = NS + "app.buildnumber";
        public static final String KEY_ENABLE_LOCAL = NS + "enablelocal";
        public static final String KEY_OS = NS + "os";
        public static final String KEY_OS_VER = NS + "os.version";
        public static final String KEY_CAPTURE_CRASH = NS + "captureCrash";

        private BrowserStack() {}
    }

    public static final class Project {
        public static final String DEF_REL_LOC_ARTIFACT = "artifact" + separator;
        public static final String DEF_LOC_TEST_DATA = "data";
        public static final String DEF_REL_LOC_TEST_PLAN = DEF_REL_LOC_ARTIFACT + "plan" + separator;
        public static final String DEF_REL_LOC_TEST_DATA = DEF_REL_LOC_ARTIFACT + DEF_LOC_TEST_DATA + separator;
        public static final String DEF_REL_LOC_TEST_SCRIPT = DEF_REL_LOC_ARTIFACT + "script" + separator;
        public static final String DEF_REL_LOC_OUTPUT = "output" + separator;
        public static final String DEF_PROJECT_PROPS = "project.properties";
        public static final String DEF_REL_PROJECT_PROPS = DEF_REL_LOC_ARTIFACT + DEF_PROJECT_PROPS;

        public static final String NEXIAL_HOME = NAMESPACE + "home";
        public static final String NEXIAL_BIN_REL_PATH = "bin" + separator;
        public static final String NEXIAL_MACOSX_BIN_REL_PATH = NEXIAL_BIN_REL_PATH + "macosx" + separator;
        public static final String NEXIAL_WINDOWS_BIN_REL_PATH = NEXIAL_BIN_REL_PATH + "windows" + separator;
        public static final String NEXIAL_LINUX_BIN_REL_PATH = NEXIAL_BIN_REL_PATH + "linux" + separator;

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

        public static String appendCommandJson(String homePath) {
            return appendSep(homePath) + "template" + separator + "nexial.script.metadata.json";
        }

        public static TestProject resolveStandardPaths(TestProject project) {
            if (project.isStandardStructure()) {
                String projectHome = project.getProjectHome();
                project.setName(StringUtils.substringAfterLast(projectHome, separator));
                project.setScriptPath(projectHome + separator + DEF_REL_LOC_TEST_SCRIPT);
                project.setArtifactPath(projectHome + separator + DEF_REL_LOC_ARTIFACT);
                project.setDataPath(projectHome + separator + DEF_REL_LOC_TEST_DATA);
                project.setPlanPath(projectHome + separator + DEF_REL_LOC_TEST_PLAN);
                project.setOutPath(projectHome + separator + DEF_REL_LOC_OUTPUT);
            }

            return project;
        }

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
        public static final String OUTPUT = "output";
        public static final Options OPTIONS = initCmdOptions();

        private CLI() { }

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
            cmdOptions.addOption(DATASHEETS, true, "[optional] Restricting to a comma-seperated list of data " +
                                                   "sheets for this test execution. Default is to utilize all " +
                                                   "the data sheets of the specified/implied data file.");
            cmdOptions.addOption(OUTPUT, true, "[optional] The output directory where results and execution " +
                                               "artifacts will be stored. Default is ../../output, relative to " +
                                               "the specified test script.");
            cmdOptions.addOption(PLAN, true, "[REQUIRED if -" + SCRIPT + " is missing] The fully qualified " +
                                             "path of a test plan.  The use of this argument will disable the " +
                                             "other arguments.");
            return cmdOptions;
        }
    }

    public static final class Data {
        public static final String SCOPE = NAMESPACE + "scope.";

        public static final String EXECUTION_MODE = SCOPE + "executionMode";
        public static final String EXECUTION_MODE_LOCAL = "local";
        public static final String EXECUTION_MODE_REMOTE = "remote";

        // iteration
        // predefined variable to define the iteration to use
        public static final String ITERATION = SCOPE + "iteration";
        // read-only: the iteration counter that just completed
        public static final String LAST_ITERATION = SCOPE + "lastIteration";
        // read-only: the currently in-progress iteration (doesn't mean it will or has completed successfully)
        public static final String CURR_ITERATION = SCOPE + "currentIteration";
        public static final String ITERATION_SEP = ",";
        public static final String ITERATION_RANGE_SEP = "-";
        public static final String FALLBACK_TO_PREVIOUS = SCOPE + "fallbackToPrevious";

        public static final String DELAY_BETWEEN_STEPS_MS = NAMESPACE + "delayBetweenStepsMs";
        public static final int DEF_DELAY_BETWEEN_STEPS_MS = 600;

        public static final String FAIL_FAST = NAMESPACE + "failFast";
        public static final boolean DEF_FAIL_FAST = false;

        public static final String FAIL_AFTER = NAMESPACE + "failAfter";
        public static final int DEF_FAIL_AFTER = -1;
        public static final String EXECUTION_FAIL_COUNT = NAMESPACE + "executionFailCount";

        public static final String MIN_EXEC_SUCCESS_RATE = NAMESPACE + "minExecSuccessRate";
        public static final double DEF_MIN_EXEC_SUCCESS_RATE = 100;
        // determine if we should clear off any fail-fast state at the end of each script
        public static final String RESET_FAIL_FAST = NAMESPACE + "resetFailFast";
        public static final boolean DEF_RESET_FAIL_FAST = false;

        public static final String VERBOSE = NAMESPACE + "verbose";
        public static final String DEF_VERBOSE = "false";

        public static final String NULL_VALUE = NAMESPACE + "nullValue";

        public static final String TEXT_DELIM = NAMESPACE + "textDelim";
        public static final String DEF_TEXT_DELIM = ",";

        public static final String POLL_WAIT_MS = NAMESPACE + "pollWaitMs";
        public static final int DEF_POLL_WAIT_MS = 30 * 1000;

        public static final String FAIL_IMMEDIATE = NAMESPACE + "failImmediate";
        public static final String END_IMMEDIATE = NAMESPACE + "endImmediate";
        public static final String BREAK_CURRENT_ITERATION = NAMESPACE + "breakCurrentIteration";

        public static final String COMMAND_DISCOVERY_MODE = NAMESPACE + "commandDiscovery";
        public static final String DEF_COMMAND_DISCOVERY_MODE = "false";

        // overly eager Nexial that opens up completed/failed excel output
        public static final String THIRD_PARTY_LOG_PATH = NAMESPACE + "3rdparty.logpath";
        public static final String TEST_LOG_PATH = NAMESPACE + "logpath";
        public static final String ASSISTANT_MODE = NAMESPACE + "assistantMode";
        // synomyous to `assistantMode`, but reads better
        public static final String OPT_OPEN_RESULT = NAMESPACE + "openResult";
        public static final String DEF_OPEN_RESULT = "off";
        public static final String SPREADSHEET_PROGRAM = NAMESPACE + "spreadsheet.program";
        public static final String SPREADSHEET_PROGRAM_EXCEL = "excel";
        public static final String SPREADSHEET_PROGRAM_WPS = "wps";
        public static final String DEF_SPREADSHEET = SPREADSHEET_PROGRAM_EXCEL;

        // system-wide enable/disable email notification
        public static final String MAIL_TO = SCOPE + "mailTo";
        public static final String ENABLE_EMAIL = NAMESPACE + "enableEmail";
        public static final String DEF_ENABLE_EMAIL = "false";

        public static final String GENERATE_EXEC_REPORT = NAMESPACE + "generateReport";
        public static final boolean DEF_GENERATE_EXEC_REPORT = false;

        public static final String WIN32_CMD = "C:\\Windows\\System32\\cmd.exe";

        // web
        public static final String BROWSER = NAMESPACE + "browser";
        public static final String DEF_BROWSER = "firefox";
        public static final String BROWSER_LANG = NAMESPACE + "browserLang";
        public static final String START_URL = NAMESPACE + "startUrl";
        public static final String BROWER_INCOGNITO = BROWSER + ".incognito";
        public static final boolean DEF_BROWSER_INCOGNITO = true;
        public static final String BROWSER_POST_CLOSE_WAIT = BROWSER + ".postCloseWaitMs";
        public static final int DEF_BROWSER_POST_CLOSE_WAIT = 3000;
        // public static final String BROWSER_HEADLESS = "headless";
        public static final String BROWSER_WINDOW_SIZE = BROWSER + ".windowSize";
        public static final String BROWSER_DEFAULT_WINDOW_SIZE = BROWSER + ".defaultWindowSize";
        public static final String ENFORCE_PAGE_SOURCE_STABILITY = NAMESPACE + "enforcePageSourceStability";
        public static final boolean DEF_ENFORCE_PAGE_SOURCE_STABILITY = true;
        public static final String FORCE_JS_CLICK = BROWSER + ".forceJSClick";
        public static final boolean DEF_FORCE_JS_CLICK = false;
        public static final String BROWSER_IE_REQUIRE_WINDOW_FOCUS = BROWSER + ".ie.requireWindowFocus";
        public static final boolean DEF_BROWSER_IE_REQUIRE_WINDOW_FOCUS = false;
        public static final int BROWSER_STABILITY_COMPARE_TOLERANCE = 3;
        public static final String OPT_BROWSER_CONSOLE_LOG = NAMESPACE + "browserConsoleLog";
        public static final String SAFARI_CLEAN_SESSION = BROWSER + ".safari.cleanSession";
        public static final boolean DEF_SAFARI_CLEAN_SESSION = true;
        public static final String SAFARI_USE_TECH_PREVIEW = BROWSER + ".safari.useTechPreview";
        public static final boolean DEF_SAFARI_USE_TECH_PREVIEW = false;
        public static final String CEF_CLIENT_LOCATION = BROWSER + ".embedded.appLocation";

        // desktop
        public static final String WINIUM_EXE = "Winium.Desktop.Driver.exe";
        public static final String WINIUM_PORT = NAMESPACE + "winiumPort";
        public static final String WINIUM_JOIN = NAMESPACE + "winiumJoinExisting";
        public static final String WINIUM_LOG_PATH = NAMESPACE + "winiumLogPath";
        public static final String WINIUM_SERVICE_RUNNING = NAMESPACE + "winiumServiceActive";
        public static final String WINIUM_SOLO_MODE = NAMESPACE + "winiumSoloMode";
        public static final boolean DEF_WINIUM_SOLO_MODE = true;
        public static final String DESKTOP_NOTIFY_WAITMS = NAMESPACE + "desktopNotifyWaitMs";
        public static final int DEF_DESKTOP_NOTIFY_WAITMS = 5000;

        // pdf
        public static final String PDF_USE_ASCII = NAMESPACE + "pdfUseAscii";
        public static final boolean DEF_PDF_USE_ASCII = true;
        public static final String PDFFORM_UNMATCHED_TEXT = "__UNMATCHED_TEXT";
        public static final String PDFFORM_PREFIX = NAMESPACE + "pdfFormStrategy.";
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
        public static final List<String> PDFFORM_VALID_OPTIONS = Arrays.asList(
            PDFFORM_BASEDON,
            PDFFORM_KEY_THEN_VALUE,
            PDFFORM_FALLBACK,
            PDFFORM_KEY_PATTERN, PDFFORM_KEY_VALUE_DELIM, PDFFORM_TRIM_KEY, PDFFORM_NORMALIZE_KEY,
            PDFFORM_SKIP_KEY_WITHOUT_DELIM,
            PDFFORM_TRIM_VALUE, PDFFORM_VALUE_AS_ONE_LINE, PDFFORM_NORMALIZE_VALUE);

        // excel
        public static final String SHEET_SYSTEM = "#system";
        public static final String SHEET_MERGED_DATA = "#data";
        public static final String SHEET_DEFAULT_DATA = "#default";
        public static final boolean DEF_OPEN_EXCEL_AS_DUP = IS_OS_WINDOWS;

        // command repeater
        public static final String CMD_COMMAND_REPEATER = "base.repeatUntil(steps,maxWaitMs)";

        // io
        public static final String COMPARE_LOG_PLAIN = "log";
        public static final String COMPARE_LOG_JSON = "json";
        public static final String GEN_COMPARE_LOG = NAMESPACE + "compare.textReport";
        public static final String GEN_COMPARE_JSON = NAMESPACE + "compare.jsonReport";
        // public static final String IO_VALIDATE_PREFIX = NAMESPACE + "io.validate";
        public static final String MAPPING_EXCEL = ".mappingExcel";
        public static final String CONFIG_JSON = ".configJson";
        public static final String REPORT_TYPE = ".reportType";
        // public static final String COMPARE_TOLERANCE = NAMESPACE + "compare.tolerance";
        public static final String LOG_MATCH = NAMESPACE + "compare.reportMatch";
        public static final boolean DEF_GEN_COMPARE_LOG = true;
        public static final boolean DEF_GEN_COMPARE_JSON = false;
        public static final boolean DEF_LOG_MATCH = false;
        // public static final double DEF_COMPARE_TOLERANCE = 0.6;

        public static final String SSH_CLIENT_PREFIX = NAMESPACE + "ssh.";
        public static final String SSH_USERNAME = "username";
        public static final String SSH_PASSWORD = "password";

        /**
         * special prefix to mark certain data as contextual to a test scenario execution.  Such data will be displayed
         * in the execution summary to provide as "reference" towards the associated scenario execution. E.g.
         * nexial.scenarioRef.browser=chrome
         * nexial.scenarioRef.envionment=QA
         * nexial.scenarioRef.appVersion=1.6.235.1
         *
         * Hence one could conclude that the associated test execution uses 'chrome' to test the application of
         * version '1.6.235.1' in the 'QA' environment.
         *
         * Note that such data is to be collected at the end of a test execution (not beginning), and all
         * in-execution changes will be reflected as such.
         */
        public static final String SCENARIO_REF_PREFIX = NAMESPACE + "scenarioRef.";
        /**
         * special prefix to mark certain data as contextual within the execution of a script.  Such data will be
         * displayed in the execution summary to provide as "reference" towards the execution of a script, and any
         * associated iterations.  E.g.
         * nexial.scriptRef.user=User1
         * nexial.scriptRef.taxState=CA
         * nexial.scriptRef.company=Johnson Co & Ltd
         *
         * Hence one could conclude that the execution of the associated script uses 'User1' to login to application,
         * and test 'CA' as the state for tax rules and 'Johnson Co & Ltd' as the target company.
         *
         * Note that such data is to be collected at the end of each iteration (not beginning), and thus all
         * in-iteration changes will be reflected as such.
         */
        public static final String SCRIPT_REF_PREFIX = NAMESPACE + "scriptRef.";
        public static final String BUILD_NO = "buildnum";

        //screen Recording
        // public static final String RECORDER_TYPE = "avi";
        public static final String RECORDER_TYPE = NAMESPACE + "screenRecorder";
        public static final String RECORDER_TYPE_MP4 = "mp4";
        public static final String RECORDER_TYPE_AVI = "avi";
        public static final String RECORDING_ENABLED = NAMESPACE + "recordingEnabled";
        public static final boolean DEF_RECORDING_ENABLED = true;

        public static final Map<String, String> SCOPE_SETTING_DEFAULTS = TextUtils.toMap(
            EXECUTION_MODE + "=" + EXECUTION_MODE_LOCAL + "\n" +
            ITERATION + "=1\n" +
            FALLBACK_TO_PREVIOUS + "=true\n" +
            MAIL_TO + "=\n",
            "\n", "=");

        // todo: find a way so that it is easy to manage this map and to publish this as part of standard documentation
        public static final Map<String, String> DEFAULTS = TextUtils.toMap(
            DELAY_BETWEEN_STEPS_MS + "=" + DEF_DELAY_BETWEEN_STEPS_MS + "\n" +
            FAIL_FAST + "=" + DEF_FAIL_FAST + "\n" +
            VERBOSE + "=" + DEF_VERBOSE + "\n" +
            TEXT_DELIM + "=" + DEF_TEXT_DELIM + "\n" +
            POLL_WAIT_MS + "=" + DEF_POLL_WAIT_MS + "\n" +
            FAIL_AFTER + "=" + DEF_FAIL_AFTER + "\n",
            "\n", "=");

        public static final String NULL = "(null)";
        public static final String EMPTY = "(empty)";
        public static final String BLANK = "(blank)";
        public static final String TAB = "(tab)";
        public static final String NL = "(eol)";
        public static final String SSH_HOST = "host";
        public static final String SSH_PORT = "port";
        public static final String SSH_HOST_KEY_CHECK = "strictHostKeyChecking";
        public static final String SSH_KNOWN_HOSTS = "knownHosts";
        public static final String DEF_SSH_PORT = "22";

        private Data() { }

        public static boolean isEmailEnabled() {
            return BooleanUtils.toBoolean(System.getProperty(ENABLE_EMAIL, DEF_ENABLE_EMAIL));
        }

        public static boolean isAutoOpenResult() {
            ExecutionContext context = ExecutionThread.get();
            if (context == null) {
                return BooleanUtils.toBoolean(System.getProperty(OPT_OPEN_RESULT, DEF_OPEN_RESULT)) ||
                       BooleanUtils.toBoolean(System.getProperty(ASSISTANT_MODE, DEF_OPEN_RESULT));
            } else {
                return context.getBooleanData(OPT_OPEN_RESULT, BooleanUtils.toBoolean(DEF_OPEN_RESULT)) ||
                       context.getBooleanData(ASSISTANT_MODE, BooleanUtils.toBoolean(DEF_OPEN_RESULT));
            }
        }

        public static String treatCommonValueShorthand(String text) {
            if (StringUtils.isBlank(text)) { return text; }
            if (StringUtils.equals(text, EMPTY)) { return ""; }
            if (StringUtils.equals(text, BLANK)) { return " "; }
            if (StringUtils.equals(text, TAB)) { return "\t"; }
            return text;
        }
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
    }

    // nexial main exit status
    public static final class ExitStatus {
        public static final int RC_EXECUTION_SUMMARY_MISSING = -15;
        public static final int RC_FAILURE_FOUND = -14;
        public static final int RC_NOT_PERFECT_SUCCESS_RATE = -13;
        public static final int RC_WARNING_FOUND = -12;
        public static final int RC_BAD_CLI_ARGS = -16;
        public static final int RC_EXCEL_IN_USE = -17;
        public static final int RC_FILE_GEN_FAILED = -18;

        private ExitStatus() { }
    }

    // directives on notes column
    public static final class FlowControls {
        public static final String OPT_STEP_BY_STEP = NAMESPACE + "stepByStep";
        public static final String OPT_INSPECT_ON_PAUSE = NAMESPACE + "inspectOnPause";
        public static final boolean DEF_INSPECT_ON_PAUSE = false;
        public static final String RESUME_FROM_PAUSE = ":resume";

        public static final String ARG_PREFIX = "(";
        public static final String ARG_SUFFIX = ")";
        public static final String REGEX_ARGS = "\\s*\\" + ARG_PREFIX + "(.*?)\\" + ARG_SUFFIX;
        public static final String DELIM_ARGS = "&";

        public static final String OPERATOR_IS = " is";
        public static final String IS_OPEN_TAG = "[";
        public static final String IS_CLOSE_TAG = "]";
        public static final String ANY_FIELD = "[ANY FIELD]";

        private FlowControls() {}
    }

    public final static class SoapUI {
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
    }

    public static class PdfMeta {
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

        private PdfMeta() {}
    }

    public static class SecretBeanNames {
        public static final String ACCESS_KEY_BEAN = "secret.s3.accessKey";
        public static final String SECRET_KEY_BEAN = "secret.s3.secretKey";
        public static final String JIRA_USERNAME_BEAN = "secret.jira.username";
        public static final String JIRA_PASSWORD_BEAN = "secret.jira.password";

        private SecretBeanNames() {}

    }

    private NexialConst() { }
}
