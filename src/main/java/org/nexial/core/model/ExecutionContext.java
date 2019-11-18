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

package org.nexial.core.model;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.net.URLEncoder;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.beanutils.MethodUtils;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.IterableUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.collections4.map.ListOrderedMap;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.nexial.commons.utils.EnvUtils;
import org.nexial.commons.utils.RegexUtils;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.MemManager;
import org.nexial.core.TokenReplacementException;
import org.nexial.core.aws.NexialS3Helper;
import org.nexial.core.aws.SmsHelper;
import org.nexial.core.excel.Excel;
import org.nexial.core.excel.Excel.Worksheet;
import org.nexial.core.excel.ExcelAddress;
import org.nexial.core.excel.ext.CellTextReader;
import org.nexial.core.interactive.InteractiveSession;
import org.nexial.core.mail.NexialMailer;
import org.nexial.core.plugins.CanTakeScreenshot;
import org.nexial.core.plugins.NexialCommand;
import org.nexial.core.plugins.pdf.CommonKeyValueIdentStrategies;
import org.nexial.core.plugins.sound.SoundMachine;
import org.nexial.core.plugins.web.Browser;
import org.nexial.core.reports.ExecutionMailConfig;
import org.nexial.core.utils.ConsoleUtils;
import org.nexial.core.utils.ExecUtils;
import org.nexial.core.utils.ExecutionLogger;
import org.nexial.core.utils.OutputFileUtils;
import org.nexial.core.utils.TrackTimeLogs;
import org.nexial.core.variable.ExpressionException;
import org.nexial.core.variable.ExpressionProcessor;
import org.nexial.core.variable.Syspath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.thymeleaf.TemplateEngine;

import static java.io.File.separator;
import static java.lang.System.lineSeparator;
import static org.apache.commons.lang3.SystemUtils.IS_OS_WINDOWS;
import static org.apache.commons.lang3.SystemUtils.USER_NAME;
import static org.nexial.commons.utils.EnvUtils.enforceUnixEOL;
import static org.nexial.core.NexialConst.*;
import static org.nexial.core.NexialConst.Data.*;
import static org.nexial.core.NexialConst.Exec.*;
import static org.nexial.core.NexialConst.FlowControls.*;
import static org.nexial.core.NexialConst.Iteration.*;
import static org.nexial.core.NexialConst.Project.NEXIAL_HOME;
import static org.nexial.core.NexialConst.TimeTrack.TRACK_EXECUTION;
import static org.nexial.core.NexialConst.Web.*;
import static org.nexial.core.SystemVariables.*;
import static org.nexial.core.excel.ext.CipherHelper.CRYPT_IND;

/**
 * represent the state of an test execution.  Differ from {@link ExecutionDefinition}, it contains the derived
 * test script and test data details, along with the thread-bound test context whereby a test run is being
 * maintained.
 */
public class ExecutionContext {
    // data variables that are READ-ONLY and cannot be removed/altered
    private static final List<Class> SIMPLE_VALUES = Arrays.asList(Boolean.class, Byte.class, Short.class,
                                                                   Character.class, Integer.class, Long.class,
                                                                   Float.class, Double.class, String.class);
    private static final String DOT_LITERAL = "\\.";
    private static final String NAME_TOKEN = "TOKEN";
    private static final String NAME_POST_TOKEN = "POST_TOKEN";
    private static final String NAME_INITIAL = "INITIAL";
    private static final String NON_DELIM_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-";
    private static final String NAME_SPRING_CONTEXT = "nexialInternal.springContext";
    private static final String NAME_PLUGIN_MANAGER = "nexialInternal.pluginManager";
    private static final String NAME_TRACK_TIME_LOGS = "nexialInternal.trackTimeLogs";

    // function parsing
    private static final String ESCAPED_DOLLAR = "\\$";
    private static final String ESCAPED_OPEN_PARENTHESIS = "\\(";
    private static final String ESCAPED_CLOSE_PARENTHESIS = "\\)";
    private static final String ESCAPED_PIPE = "\\|";
    private static final String ALT_DOLLAR = "__<~*~>__";
    private static final String ALT_OPEN_PARENTHESIS = "__<?!?>__";
    private static final String ALT_CLOSE_PARENTHESIS = "__<@*@>__";
    private static final String ALT_PIPE = "__<%+%>__";
    private static final String ALT_GML_CLOSE_TAG = "__#~~^~~#__";
    private static final String ALT_GML_CLOSE_TAG2 = "__%&&*&&$__";
    private static final String ESCAPED_TOKEN_START = "\\$\\{";
    private static final String ESCAPED_TOKEN_END = "\\}";
    private static final String REGEX_CRYPT = "(crypt:[0-9A-Za-z]+)";

    protected Logger logger = LoggerFactory.getLogger(getClass());
    protected ExecutionDefinition execDef;
    protected TestProject project;
    protected Excel testScript;
    protected List<TestScenario> testScenarios;
    protected TestStep currentTestStep;

    protected String hostname;
    protected long startTimestamp;
    protected String startDateTime;
    protected long endTimestamp;
    protected String endDateTime;
    protected int scriptStepCount;
    protected int scriptPassCount;
    protected int scriptFailCount;
    protected int scriptWarnCount;

    protected Map<String, Object> builtinFunctions;
    protected List<String> failfastCommands = new ArrayList<>();
    protected ExecutionLogger executionLogger;
    // output-to-cloud (otc) via AWS S3
    protected NexialS3Helper otc;
    protected String otcNotReadyMessage;
    protected SoundMachine dj;
    protected SmsHelper smsHelper;
    protected NexialMailer nexialMailer;
    protected TemplateEngine templateEngine;
    protected Map<String, String> defaultContextProps;
    protected List<String> readOnlyVars;
    protected List<String> referenceDataForExecution = new ArrayList<>();
    protected ClassPathXmlApplicationContext springContext;
    protected PluginManager plugins;
    protected Map<String, Object> data = new ListOrderedMap<>();
    protected ExpressionProcessor expression;
    protected ExecutionEventListener executionEventListener;
    protected CanTakeScreenshot screenshotAgent;
    protected Syspath syspath;
    // protected ProfileHelper profileHelper;

    // spring-managed map of webdriver related configs.
    protected Map<BrowserType, String> webdriverHelperConfig;

    static final String KEY_COMPLEX = "__lAIxEn__";
    static final String DOT_LITERAL_REPLACER = "__53n7ry_4h34d__";

    class Function {
        String functionName;
        Object function;
        String operation;
        String[] parameters;

        void parse(String token) {
            // function object
            functionName = StringUtils.substringBefore(token, TOKEN_PARAM_SEP);
            Object functionObj = builtinFunctions.get(functionName);
            if (functionObj == null) {
                throw new IllegalArgumentException(functionName + " does not resolved to a function");
            }

            function = functionObj;

            // operation
            token = StringUtils.substringAfter(token, TOKEN_PARAM_SEP);
            operation = StringUtils.substringBefore(token, TOKEN_PARAM_SEP);

            // parameters
            // could be 1 or more.
            // e.g. a,b,c,d         => 1 param
            // e.g. a,b,c,d|2       => 2 params
            // e.g. a\|b\|\c|d|2|3  => 3 params, using pipe as nexial.textDelim

            // get the rest of token after the first pipe.  this should represent the entirety of all params
            // token = replaceTokens(StringUtils.substringAfter(token, TOKEN_PARAM_SEP));

            // relay token replacement until after `token` is split into its proper parts
            token = StringUtils.substringAfter(token, TOKEN_PARAM_SEP);

            // compensate the use of TOKEN_PARAM_SEP as delimiter
            String delim = getTextDelim();
            boolean escapedDelim = false;
            if (StringUtils.equals(delim, TOKEN_PARAM_SEP)) {
                delim = ESCAPED_PIPE;
                escapedDelim = true;
            } else {
                // if delim is not pipe, then we would want to preserve escaped pipe as part of argument
                token = StringUtils.replace(token, ESCAPED_PIPE, ALT_PIPE);
            }

            // replace delim with magic delim (not found in token)
            token = StringUtils.replace(token, delim, TOKEN_TEMP_DELIM);

            List<String> paramList = TextUtils.toList(token, TOKEN_PARAM_SEP, false);
            if (CollectionUtils.isEmpty(paramList)) {
                throw new IllegalArgumentException("None or insufficient parameters found");
            }

            parameters = new String[paramList.size()];
            for (int i = 0; i < paramList.size(); i++) {
                String param = paramList.get(i);
                // return the magic
                param = StringUtils.replace(param, TOKEN_TEMP_DELIM, escapedDelim ? TOKEN_PARAM_SEP : delim);
                param = StringUtils.replace(param, ALT_PIPE, "|");
                param = StringUtils.replace(param, ESCAPED_DOLLAR, "$");
                param = StringUtils.replace(param, ESCAPED_OPEN_PARENTHESIS, "(");
                param = StringUtils.replace(param, ESCAPED_CLOSE_PARENTHESIS, ")");
                param = replaceTokens(param);
                parameters[i] = param;
            }
        }
    }

    // support unit test and mocking
    protected ExecutionContext() { }

    public ExecutionContext(ExecutionDefinition execDef) { this(execDef, null); }

    public ExecutionContext(ExecutionDefinition execDef, Map<String, Object> intraExecutionData) {
        this.execDef = execDef;
        this.project = adjustPath(execDef);
        this.hostname = StringUtils.upperCase(EnvUtils.getHostName());
        setData(HOSTNAME, this.hostname, true);

        // init data map... something just doesn't make sense not to exist from the get-go
        setData(OPT_LAST_OUTCOME, true);
        setData(ITERATION_ENDED, false);

        if (MapUtils.isNotEmpty(intraExecutionData)) {
            // reuse existing spring context
            springContext = (ClassPathXmlApplicationContext) intraExecutionData.remove(NAME_SPRING_CONTEXT);
            initSpringBeans();

            plugins = (PluginManager) intraExecutionData.remove(NAME_PLUGIN_MANAGER);
            plugins.setContext(this);

            data.putAll(intraExecutionData);
            data.remove(BREAK_CURRENT_ITERATION);
            data.remove(LAST_ITERATION);
            data.remove(IS_FIRST_ITERATION);
            data.remove(IS_LAST_ITERATION);
            data.remove(OPT_LAST_SCREENSHOT_NAME);
            data.remove(OPT_LAST_ALERT_TEXT);
            data.remove(OPT_LAST_OUTPUT_LINK);
        } else {
            // init spring
            springContext = new ClassPathXmlApplicationContext("classpath:" +
                                                               System.getProperty(OPT_SPRING_XML, DEF_SPRING_XML));
            initSpringBeans();

            // init plugins
            plugins = springContext.getBean("pluginManager", PluginManager.class);
            plugins.setContext(this);
            plugins.init();
        }

        // some data can be overridden by System property
        overrideIfSysPropFound(NEXIAL_HOME);
        overrideIfSysPropFound(ENABLE_EMAIL);
        overrideIfSysPropFound(OPT_OPEN_RESULT);
        overrideIfSysPropFound(OPT_OPEN_EXEC_REPORT);
        overrideIfSysPropFound(ASSISTANT_MODE);
        overrideIfSysPropFound(OPT_TEXT_MATCH_LENIENT);
        overrideIfSysPropFound(OUTPUT_TO_CLOUD);
        overrideIfSysPropFound(FAIL_FAST);
        overrideIfSysPropFound(VERBOSE);
        overrideIfSysPropFound(TEXT_DELIM);

        expression = new ExpressionProcessor(this);
        executionLogger = new ExecutionLogger(this);
        // profileHelper = new ProfileHelper(this);

        if (!ExecUtils.isRunningInZeroTouchEnv() && getBooleanData(OPT_ODI_ENABLED, getDefaultBool(OPT_ODI_ENABLED))) {
            OnDemandInspectionDetector.getInstance(this);
            ConsoleUtils.log("On-Demand Inspection detection enabled");
        }
    }

    public void useTestScript(Excel testScript) throws IOException {
        this.testScript = testScript;
        setData(OPT_INPUT_EXCEL_FILE, testScript.getFile().getAbsoluteFile());

        MDC.put(TEST_SUITE_NAME, getRunId());
        MDC.put(TEST_NAME, getId());
        if (logger.isInfoEnabled()) { logger.info("STARTS"); }

        // parse merged test script
        parse();
    }

    public ExecutionLogger getLogger() { return executionLogger; }

    public void logCurrentStep(String message) {
        TestStep currentTestStep = getCurrentTestStep();
        executionLogger.log(currentTestStep, message);
        if (isVerbose()) { currentTestStep.addNestedMessage(message); }
    }

    public void logCurrentCommand(NexialCommand command, String message) { executionLogger.log(command, message); }

    public ExecutionDefinition getExecDef() { return execDef; }

    public String getRunId() { return execDef.getRunId(); }

    public String getId() {
        return "[" + getRunId() + "][" + (testScript == null ? "UNKNOWN SCRIPT" : testScript.getFile().getName()) + "]";
    }

    public boolean isFailFastCommand(String target, String command) {
        return failfastCommands.contains(target + "." + StringUtils.substringBefore(command, "("));
    }

    public boolean isFailFastCommand(TestStep testStep) {
        return isFailFastCommand(testStep.getTarget(), testStep.getCommand());
    }

    public String getHostname() { return hostname; }

    public String getUsername() { return USER_NAME; }

    public String getStartDateTime() { return startDateTime; }

    public long getStartTimestamp() { return startTimestamp; }

    public String getEndDateTime() { return endDateTime; }

    public long getEndTimestamp() { return endTimestamp; }

    public Map<BrowserType, String> getWebdriverHelperConfig() { return webdriverHelperConfig; }

    public NexialS3Helper getOtc() throws IOException {
        // check that the required properties are set
        if (otc == null || !otc.isReadyForUse()) { throw new IOException(otcNotReadyMessage); }
        return otc;
    }

    public SoundMachine getDj() {
        if (dj == null) { initDj(); }
        return dj;
    }

    public SmsHelper getSmsHelper() { return smsHelper; }

    // public ProfileHelper getProfileHelper() { return profileHelper; }

    public NexialMailer getNexialMailer() { return nexialMailer; }

    public TemplateEngine getTemplateEngine() { return templateEngine; }

    public boolean isPluginLoaded(String target) { return plugins.isPluginLoaded(target); }

    public NexialCommand findPlugin(String target) { return plugins.getPlugin(target); }

    public Browser getBrowser() { return plugins.getBrowser(); }

    @NotNull
    public String getBrowserType() {
        // gotta push default to context
        // String browser = System.getProperty(BROWSER);
        // String browser = StringUtils.remove(System.getProperty(BROWSER, getStringData(BROWSER, DEF_BROWSER)), ".");
        // setData(BROWSER, browser);
        // return browser;

        // DO NOT SET BROWSER TYPE TO SYSTEM PROPS, SINCE THIS WILL PREVENT ITERATION-LEVEL OVERRIDES
        // System.setProperty(BROWSER, browserType);

        return StringUtils.remove(System.getProperty(BROWSER, getStringData(BROWSER, getDefault(BROWSER))), ".");
    }

    public boolean isDelayBrowser() { return getBooleanData(OPT_DELAY_BROWSER, getDefaultBool(OPT_DELAY_BROWSER)); }

    public Excel getTestScript() { return testScript; }

    public List<TestScenario> getTestScenarios() { return testScenarios; }

    public ExecutionEventListener getExecutionEventListener() { return executionEventListener; }

    public boolean isScreenshotOnError() {
        return getBooleanData(OPT_SCREENSHOT_ON_ERROR, getDefaultBool(OPT_SCREENSHOT_ON_ERROR));
    }

    public void registerScreenshotAgent(CanTakeScreenshot agent) { screenshotAgent = agent; }

    public void clearScreenshotAgent() { screenshotAgent = null; }

    public CanTakeScreenshot findCurrentScreenshotAgent() { return screenshotAgent; }

    public boolean isInteractiveMode() {
        return getBooleanData(OPT_INTERACTIVE, false) && !ExecUtils.isRunningInZeroTouchEnv();
    }

    public boolean isFailFast() {return getBooleanData(FAIL_FAST, getDefaultBool(FAIL_FAST)) && !isInteractiveMode(); }

    /** Evaluate Page Source Stability Required */
    public boolean isPageSourceStabilityEnforced() {
        return getBooleanData(ENFORCE_PAGE_SOURCE_STABILITY, getDefaultBool(ENFORCE_PAGE_SOURCE_STABILITY));
    }

    public boolean isResolveTextAsURL() {
        return hasData(RESOLVE_TEXT_AS_URL) ? getBooleanData(RESOLVE_TEXT_AS_URL) :
               hasData(EXPRESSION_RESOLVE_URL) ? getBooleanData(EXPRESSION_RESOLVE_URL) :
               getDefaultBool(RESOLVE_TEXT_AS_URL);
    }

    public boolean isResolveTextAsIs() {
        return hasData(RESOLVE_TEXT_AS_IS) ? getBooleanData(RESOLVE_TEXT_AS_IS) :
               hasData(EXPRESSION_OPEN_FILE_AS_IS) ? getBooleanData(EXPRESSION_OPEN_FILE_AS_IS) :
               getDefaultBool(RESOLVE_TEXT_AS_IS);
    }

    /**
     * increment current failure count and evaluate if execution failure should be declared since we've passed the
     * failAfter threshold
     */
    public void incrementAndEvaluateFail(StepResult result) {
        // `isError()` ensure that the result is not SUCCESS, SKIP or WARN
        if (!result.isError()) { return; }

        // increment execution fail count
        int execFailCount = getIntData(FAIL_COUNT, 0) + 1;
        setData(FAIL_COUNT, execFailCount);

        // determine if fail-immediate is eminent
        int failAfter = getFailAfter();
        if (failAfter != -1 && execFailCount >= failAfter) {
            executionLogger.log(getCurrentTestStep(), failAfterReached(execFailCount, failAfter));
            setFailImmediate(true);
        }

        // in effect, the `onError()` event works the same way as `pauseOnError`. Nexial supports both stylistic variety
        executionEventListener.onError();

        if (isPauseOnError()) {
            ConsoleUtils.doPause(this, "[ERROR #" + execFailCount + "]: Error found " +
                                       ExecutionLogger.toHeader(getCurrentTestStep()) + " - " +
                                       result.getMessage());
        }
    }

    public void evaluateResult(StepResult result) {
        if (result.isSkipped()) {
            setData(SKIP_COUNT, getIntData(SKIP_COUNT, 0) + 1);
            return;
        }

        setData(EXEC_COUNT, getIntData(EXEC_COUNT, 0) + 1);
        if (result.isSuccess()) {
            setData(PASS_COUNT, getIntData(PASS_COUNT, 0) + 1);
        } else {
            incrementAndEvaluateFail(result);
        }
    }

    public int getFailAfter() { return getIntData(FAIL_AFTER, getDefaultInt(FAIL_AFTER)); }

    public boolean isStepByStep() { return getBooleanData(OPT_STEP_BY_STEP); }

    public boolean isVerbose() { return getBooleanData(VERBOSE); }

    public boolean isTextMatchLeniently() {
        return getBooleanData(OPT_TEXT_MATCH_LENIENT, getDefaultBool(OPT_TEXT_MATCH_LENIENT));
    }

    public boolean isTextMatchAsNumber() {
        return getBooleanData(OPT_TEXT_MATCH_AS_NUMBER, getDefaultBool(OPT_TEXT_MATCH_AS_NUMBER));
    }

    public boolean isTextMatchUseTrim() {
        return getBooleanData(OPT_TEXT_MATCH_USE_TRIM, getDefaultBool(OPT_TEXT_MATCH_USE_TRIM));
    }

    public boolean isTextMatchCaseInsensitive() {
        return getBooleanData(OPT_TEXT_MATCH_CASE_INSENSITIVE, getDefaultBool(OPT_TEXT_MATCH_CASE_INSENSITIVE));
    }

    public boolean isProxyRequired() { return getBooleanData(OPT_PROXY_REQUIRED, false); }

    public boolean isOutputToCloud() { return getBooleanData(OUTPUT_TO_CLOUD, getDefaultBool(OUTPUT_TO_CLOUD)); }

    public boolean isPauseOnError() {
        return getBooleanData(OPT_PAUSE_ON_ERROR, getDefaultBool(OPT_PAUSE_ON_ERROR)) &&
               !ExecUtils.isRunningInZeroTouchEnv();
    }

    @NotNull
    public String getTextDelim() { return getRawStringData(TEXT_DELIM, ","); }

    @NotNull
    public String getNullValueToken() { return getRawStringData(NULL_VALUE, NULL); }

    public long getPollWaitMs() { return getIntData(POLL_WAIT_MS, getDefaultInt(POLL_WAIT_MS)); }

    public long getSLAElapsedTimeMs() { return getIntData(OPT_ELAPSED_TIME_SLA); }

    public long getDelayBetweenStep() {
        return getIntData(DELAY_BETWEEN_STEPS_MS, getDefaultInt(DELAY_BETWEEN_STEPS_MS));
    }

    public boolean hasData(String name) { return getObjectData(name) != null; }

    @Nullable
    public Object getObjectData(String name) {
        if (StringUtils.isBlank(name)) { return null; }
        String sysProp = System.getProperty(name);
        if (StringUtils.isNotEmpty(sysProp)) { return sysProp; }
        return MapUtils.getObject(data, name);
    }

    public String getStringData(String name) {
        String rawValue = getRawStringData(name);
        if (StringUtils.isBlank(rawValue)) { return rawValue; }
        String resolved = replaceTokens(rawValue);
        if (StringUtils.startsWith(rawValue, CRYPT_IND)) { CellTextReader.registerCrypt(name, rawValue, resolved); }
        return resolved;
    }

    public String getStringData(String name, String def) {
        return StringUtils.isEmpty(name) ? def : StringUtils.defaultIfEmpty(getStringData(name), def);
    }

    public int getIntData(String name) {
        String value = getStringData(name);
        if (StringUtils.isBlank(value)) { return UNDEFINED_INT_DATA; }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Unable to parse data '" + name + "' as int (" + value + ")", e);
        }
    }

    public int getIntData(String name, int def) { return NumberUtils.toInt(getStringData(name), def); }

    public double getDoubleData(String name) {
        return ObjectUtils.defaultIfNull(MapUtils.getDouble(data, name), UNDEFINED_DOUBLE_DATA);
    }

    public double getDoubleData(String name, double def) { return NumberUtils.toDouble(getStringData(name), def); }

    public boolean getBooleanData(String name) {
        return Boolean.parseBoolean(StringUtils.lowerCase(getStringData(name)));
    }

    public boolean getBooleanData(String name, boolean def) {
        String value = getStringData(name);
        if (StringUtils.isBlank(value)) { return def; }

        try {
            return Boolean.parseBoolean(StringUtils.lowerCase(value));
        } catch (Exception e) {
            if (logger.isInfoEnabled()) {
                logger.info("Unable to parse property '" + name + "' as boolean (" + value + "): " + e);
                logger.info("use defaultValue instead");
            }
            return def;
        }
    }

    public Map getMapData(String name) {
        if (!data.containsKey(name)) { return null; }

        Object value = data.get(name);
        if (value == null) { return null; }

        if (value instanceof Map) { return (Map) value; }
        return null;
    }

    public List getListData(String name) {
        if (!data.containsKey(name)) { return null; }

        Object value = data.get(name);
        if (value == null) { return null; }

        // a little riskay..
        if (value instanceof List) { return (List) value; }
        return null;
    }

    /**
     * return a map of name-value pair where the names start with the specified {@code prefix}. Note that the returned
     * names have the {@code prefix} removed.
     */
    @NotNull
    public Map<String, String> getDataByPrefix(String prefix) {
        Map<String, String> props = new LinkedHashMap<>();

        data.forEach((key, value) -> {
            if (StringUtils.startsWith(key, prefix)) {
                props.put(StringUtils.substringAfter(key, prefix), replaceTokens(Objects.toString(value)));
            }
        });

        // scan system properties later so that they can override those also found in `data`
        System.getProperties().forEach((key, value) -> {
            String sKey = key.toString();
            if (StringUtils.startsWith(sKey, prefix)) {
                props.put(StringUtils.substringAfter(sKey, prefix), replaceTokens(Objects.toString(value)));
            }
        });

        return props;
    }

    @NotNull
    public Collection<String> getDataNames(String prefix) {
        // ordered data names during collection; we have no way to predetermined the right order
        Set<String> names = new TreeSet<>();

        names.addAll(data.keySet()
                         .stream()
                         .filter(name -> StringUtils.isEmpty(prefix) || StringUtils.startsWith(name, prefix))
                         .collect(Collectors.toList()));
        names.addAll(System.getProperties()
                           .stringPropertyNames()
                           .stream()
                           .filter(name -> StringUtils.isEmpty(prefix) || StringUtils.startsWith(name, prefix))
                           .collect(Collectors.toList()));

        return names;
    }

    @NotNull
    public Collection<String> getDataNamesByRegex(String regex) {
        // ordered data names during collection; we have no way to predetermined the right order
        Set<String> names = new TreeSet<>();

        names.addAll(data.keySet()
                         .stream()
                         .filter(name -> StringUtils.isEmpty(regex) || RegexUtils.match(name, regex))
                         .collect(Collectors.toList()));
        names.addAll(System.getProperties()
                           .stringPropertyNames()
                           .stream()
                           .filter(name -> StringUtils.isEmpty(regex) || RegexUtils.match(name, regex))
                           .collect(Collectors.toList()));

        return names;
    }

    @NotNull
    public Map<String, Object> getObjectByPrefix(String prefix) {
        Map<String, Object> props = new LinkedHashMap<>(EnvUtils.getSysPropsByPrefix(prefix));
        data.forEach((key, value) -> {
            if (StringUtils.startsWith(key, prefix)) { props.put(StringUtils.substringAfter(key, prefix), value); }
        });
        return props;
    }

    public boolean isReadOnlyData(String var) {
        return readOnlyVars.contains(var) || StringUtils.startsWith(var, "java.");
    }

    /**
     * remove data variable both from context and system
     */
    public String removeData(String name) {
        if (isReadOnlyData(name)) {
            ConsoleUtils.error("Removing READ-ONLY variable is not permitted: " + name);
            return null;
        }

        return removeDataForcefully(name);
    }

    public String removeDataForcefully(String name) {
        Object removedObj = data.remove(name);
        String removed;
        if (removedObj == null) {
            removed = null;
        } else {
            removed = Objects.toString(removedObj);
            // keep GC happy
            removedObj = null;
        }

        if (StringUtils.isBlank(name)) {
            return removed;
        } else {
            String removedFromSys = System.clearProperty(name);
            return StringUtils.isEmpty(removed) ? removedFromSys : removed;
        }
    }

    public void setData(String name, String value) {
        // some reference data are considered "special" and should be elevated to "execution" level so that they can be
        // used as such for Execution Dashboard
        setData(name, value, referenceDataForExecution.contains(name));
    }

    public void setData(String name, String value, boolean updateSysProps) {
        if (data.containsKey(name)) { CellTextReader.unsetValue(value); }
        if (isNullValue(value)) {
            // will remove from both `data` and sysprop
            removeData(name);
        } else {
            value = mergeProperty(value);
            data.put(name, value);

            // logic updated; see below
            // if (updateSysProps || referenceDataForExecution.contains(name)) { System.setProperty(name, value); }
            if (updateSysProps) { System.setProperty(name, value); }

            // we'll update the system property for the same data variable IFF
            // 1. `updateSysProps` is true  - OR -
            // 2. the data variable is found as a sysprop AND
            // 3. the same data variable is not considered as READ-ONLY
            // if (updateSysProps || (StringUtils.isNotEmpty(System.getProperty(name)) && !isReadOnlyData(name))) {
            //     System.setProperty(name, value);
            // }
        }
    }

    public void setData(String name, int value) { setDataOrSysProp(name, value); }

    public void setData(String name, Map<String, String> value) { setDataOrSysProp(name, value); }

    public void setData(String name, List value) { setDataOrSysProp(name, value); }

    public void setData(String name, Boolean value) { setDataOrSysProp(name, value); }

    public void setData(String name, Object value) { setDataOrSysProp(name, value); }

    public boolean isNullValue(String value) { return value == null || StringUtils.equals(value, getNullValueToken()); }

    public boolean isEmptyValue(String value) { return StringUtils.isEmpty(value) || StringUtils.equals(value, EMPTY); }

    public boolean isBlankValue(String value) { return StringUtils.isBlank(value) || StringUtils.equals(value, BLANK); }

    public boolean isNullOrEmptyValue(String value) { return isNullValue(value) || isEmptyValue(value); }

    public boolean isNullOrEmptyOrBlankValue(String value) {
        return isBlankValue(value) || isEmptyValue(value) || isNullValue(value);
    }

    public String replaceTokens(String text) { return replaceTokens(text, false); }

    public String replaceTokens(String text, boolean retainCrypt) {
        if (StringUtils.isBlank(text)) { return text; }
        if (StringUtils.equals(text, getNullValueToken())) { return null; }

        text = treatCommonValueShorthand(text);
        if (text == null) { return null; }

        // for portability
        text = StringUtils.replace(text, NL, lineSeparator());

        // pre-first pass  ;-)
        // substitute crypt value
        text = handleCryptValue(text);

        // first pass: cycle through the dyn var
        text = handleFunction(text);

        // second pass: simple value ONLY
        Map<String, Object> collectionValues = new HashMap<>();
        Map<String, Object> complexValues = new HashMap<>();
        Set<String> tokens = findTokens(text);

        List<String> ignoredVars = TextUtils.toList(getRawStringData(OPT_VAR_EXCLUDE_LIST), getTextDelim(), false);
        if (CollectionUtils.isNotEmpty(ignoredVars)) { tokens.removeAll(ignoredVars); }

        boolean allTokenResolvedToNull = CollectionUtils.isNotEmpty(tokens);
        boolean unresolvedAsIs = getDefaultBool(OPT_VAR_DEFAULT_AS_IS);
        if (hasData(OPT_VAR_DEFAULT_AS_IS)) {
            Object config = getObjectData(OPT_VAR_DEFAULT_AS_IS);
            if (config != null) { unresolvedAsIs = BooleanUtils.toBoolean(config.toString()); }
        }

        for (String token : tokens) {
            Object value = getObjectData(token);
            String tokenized = TOKEN_START + token + TOKEN_END;

            // special conditions: null or (null)
            if (value == null || value.equals(NULL)) {
                // if data contains a key (token) with value `null`, then we should just return null as is.
                if (data.containsKey(token)) {
                    // if this is the only token, then we are done
                    if (tokens.size() == 1 && StringUtils.equals(text, tokenized)) { return null; }

                    // if not, replace token with "" and continue.  Doesn't make sense to replace token with `null`
                    text = StringUtils.replace(text, tokenized, "");
                } else {
                    // otherwise, this token is not defined in context nor system prop.
                    // so we'll replace it with empty string
                    if (tokens.size() == 1 && StringUtils.equals(text, tokenized)) { return unresolvedAsIs ? text : "";}
                    if (!unresolvedAsIs) { text = StringUtils.replace(text, tokenized, ""); }
                }

                // NO LONGER APPLIES!! SEE CODE ABOVE
                // otherwise, we skip this token (meaning no value was assigned to this token).  Most likely this
                // token will not be replaced and remains intact.
                continue;
            }

            allTokenResolvedToNull = false;

            if (value.equals(NULL)) {
                text = StringUtils.replace(text, tokenized, "");
                continue;
            }

            if (NON_DISPLAYABLE_REPLACEMENTS.containsKey(value.toString())) {
                text = StringUtils.replace(text, tokenized, NON_DISPLAYABLE_REPLACEMENTS.get(value.toString()));
                continue;
            }

            Class valueType = value.getClass();
            if (valueType.isPrimitive() || SIMPLE_VALUES.contains(valueType)) {

                String stringValue = StringUtils.defaultString(getStringData(token));
                // if we need to conceal crypt and this token resolves from a crypt value,
                // then we'll revert back to its crypt form
                // stringValue = CellTextReader.readValue(stringValue);
                if (retainCrypt && CellTextReader.isCrypt(stringValue)) { stringValue = tokenized; }

                // it's possible that we might get a reference to an item of a string-based array (like "a,b,c,d")
                // check to see if there's any reference to the ${var}[index] pattern
                if (isListCompatible(stringValue)) {
                    String regexIndexRef = ESCAPED_TOKEN_START + token + ESCAPED_TOKEN_END + "\\[.+?\\]";
                    boolean hasIndexRef = RegexUtils.match(text, regexIndexRef, true);

                    // if there's substitution for ${...}[#] and the `value` can be treated as list
                    if (hasIndexRef) {
                        String beforeIndex = TOKEN_START + token + TOKEN_END + TOKEN_ARRAY_START;
                        String afterIndex = TOKEN_ARRAY_END;

                        String[] array = StringUtils.splitPreserveAllTokens(stringValue, getTextDelim());

                        List<String> indexRefs = RegexUtils.eagerCollectGroups(text, regexIndexRef, false, true);
                        for (String indexRef : indexRefs) {
                            String indexStr = StringUtils.substringBetween(indexRef, beforeIndex, afterIndex);
                            String indexStr2;

                            // in-place replacement for possible index value
                            if (TextUtils.isBetween(indexStr, TOKEN_START, TOKEN_END)) {
                                indexStr2 = replaceTokens(indexStr);
                            } else {
                                indexStr2 = indexStr;
                            }

                            if (!NumberUtils.isDigits(indexStr2)) { continue; }

                            int index = NumberUtils.toInt(indexStr2);
                            String itemValue;
                            if (ArrayUtils.isArrayIndexValid(array, index)) {
                                itemValue = array[index];
                            } else {
                                if (unresolvedAsIs) { continue; }
                                itemValue = "";
                            }
                            text = StringUtils.replace(text, beforeIndex + indexStr + afterIndex, itemValue);
                        }
                    }

                } else {
                    // then only ${var}[0] would make sense; single value is the same as the first item
                    String firstIndexRef = "${" + token + "}[0]";
                    text = StringUtils.replace(text, firstIndexRef, stringValue);
                }

                // finally replace token after all index ref's are handled
                text = StringUtils.replace(text, tokenized, stringValue);
            } else if (Collection.class.isAssignableFrom(valueType) || valueType.isArray()) {
                collectionValues.put(token, value);
            } else {
                complexValues.put(token, value);
            }
        }

        if (CollectionUtils.isNotEmpty(tokens) && allTokenResolvedToNull) {
            // we'll return null ONLY if:
            // at least one token was parsed out of `text`
            // all parsed tokens resolved to null
            // `text` contains ONLY tokens
            String tmp = text;
            for (String token : tokens) { tmp = StringUtils.remove(tmp, TOKEN_START + token + TOKEN_END); }

            // `tmp` == null means all its content were tokens
            if (tmp == null) { return null; }
        }

        // third pass: collection and array ONLY
        if (MapUtils.isNotEmpty(collectionValues)) {
            text = replaceCollectionTokens(text, collectionValues, complexValues);
        }

        // fourth pass: map and complex object type
        if (MapUtils.isNotEmpty(complexValues)) { text = replaceComplexTokens(text, complexValues); }

        // fifth pass: nexial expression
        text = handleExpression(text);

        // sixth pass: crypt
        if (StringUtils.startsWith(text, CRYPT_IND) && !retainCrypt) { text = CellTextReader.getText(text); }

        return enforceUnixEOL(text);
    }

    public String handleExpression(String text) {
        if (StringUtils.isBlank(text)) { return text; }
        if (expression == null) { expression = new ExpressionProcessor(this); }

        try {
            return expression.process(text);
        } catch (ExpressionException e) {
            String errorPrefix = "Unable to process expression due to ";
            for (Throwable err : ExceptionUtils.getThrowableList(e.getCause())) {
                // special handling for IOE; probably file/path related issues
                if (err instanceof IOException) { throw new IllegalArgumentException(errorPrefix + err.getMessage()); }
            }

            // not IOE
            String message = errorPrefix + e.getMessage();
            ExecutionLogger logger = getLogger();
            TestStep testStep = getCurrentTestStep();
            if (testStep == null) {
                logger.error(this, message);
            } else {
                logger.error(testStep, message);
            }

            return text;
        }
    }

    public String handleFunction(String text) {
        String functionToken = nextFunctionToken(text);
        while (StringUtils.isNotBlank(functionToken)) {
            // put back the temporarily replace $(...), we need invokeFunction() to work on the original data
            functionToken = StringUtils.replace(functionToken, TOKEN_DEFUNC_START, TOKEN_FUNCTION_START);
            functionToken = StringUtils.replace(functionToken, TOKEN_DEFUNC_END, TOKEN_FUNCTION_END);
            String value = invokeFunction(functionToken);

            // need to properly handle platform specific path... switch to use / or \ depending on the OS
            if (TextUtils.isBetween(functionToken, "syspath|", "|fullpath")) {
                String pathPart = StringUtils.substringAfter(text, functionToken);
                pathPart = StringUtils.removeStart(pathPart, ")");
                pathPart = StringUtils.substringBefore(pathPart, " ");

                if (StringUtils.isNotBlank(pathPart)) {
                    // need to skip `</...>` text for HTML/XML content
                    text = StringUtils.replace(text, "</", ALT_GML_CLOSE_TAG);
                    text = StringUtils.replace(text, "/>", ALT_GML_CLOSE_TAG2);

                    String pathPathReplace = IS_OS_WINDOWS ?
                                             StringUtils.replace(pathPart, "/", "\\") :
                                             StringUtils.replace(pathPart, "\\", "/");
                    text = StringUtils.replaceOnce(text, pathPart, pathPathReplace);

                    // put `</...>` back for HTML/XML content
                    text = StringUtils.replace(text, ALT_GML_CLOSE_TAG, "</");
                    text = StringUtils.replace(text, ALT_GML_CLOSE_TAG2, "/>");
                }
            }

            text = StringUtils.replaceOnce(text, TOKEN_FUNCTION_START + functionToken + TOKEN_FUNCTION_END, value);
            functionToken = nextFunctionToken(text);
        }

        return text;
    }

    public TestProject getProject() { return project; }

    public String getCurrentScenario() {
        String scenario = getStringData(OPT_CURRENT_SCENARIO);
        if (StringUtils.isNotBlank(scenario)) { return scenario; }

        try {
            return currentTestStep != null ? currentTestStep.getTestCase().getTestScenario().getName() : null;
        } catch (NullPointerException e) {
            ConsoleUtils.error("Unable to determine current scenario: " + e.getMessage());
            return null;
        }
    }

    public void setCurrentScenario(TestScenario scenario) {
        if (scenario == null) {
            removeDataForcefully(OPT_CURRENT_SCENARIO);
        } else {
            setData(OPT_CURRENT_SCENARIO, scenario.getName());
        }
    }

    public TestStep getCurrentTestStep() { return currentTestStep; }

    protected void setCurrentTestStep(TestStep testStep) { this.currentTestStep = testStep; }

    @NotNull
    public String generateTestStepOutput(String extension) {
        if (syspath == null) { syspath = new Syspath(); }
        return syspath.out("fullpath") + separator +
               OutputFileUtils.generateOutputFilename(getCurrentTestStep(), extension);
    }

    public void adjustForInteractive(InteractiveSession session) {
        String targetScenario = session.getScenario();
        Collection<String> targetActivities = session.getActivities();

        List<TestScenario> filteredScenarios = new ArrayList<>();
        testScenarios.forEach(scenario -> {
            if (StringUtils.equals(targetScenario, scenario.getName())) {
                if (CollectionUtils.isNotEmpty(targetActivities)) {
                    List<TestCase> filteredActivities = new ArrayList<>();
                    List<TestCase> activities = scenario.getTestCases();
                    targetActivities.forEach(targetActivity -> activities.forEach(activity -> {
                        if (StringUtils.equals(targetActivity, activity.getName())) {
                            filteredActivities.add(activity);
                        }
                    }));
                    activities.clear();
                    activities.addAll(filteredActivities);
                }

                filteredScenarios.add(scenario);
            }
        });

        testScenarios = filteredScenarios;
    }

    public String getCurrentActivity() { return getStringData(OPT_CURRENT_ACTIVITY); }

    public void setCurrentActivity(TestCase activity) {
        if (activity == null) {
            removeDataForcefully(OPT_CURRENT_ACTIVITY);
        } else {
            setData(OPT_CURRENT_ACTIVITY, activity.getName());
        }
    }

    public void fillIntraExecutionData(Map<String, Object> intraExecutionData) {
        intraExecutionData.putAll(getDataMap());
        intraExecutionData.put(NAME_PLUGIN_MANAGER, plugins);
        intraExecutionData.put(NAME_SPRING_CONTEXT, springContext);
        intraExecutionData.remove(IS_FIRST_ITERATION);
        intraExecutionData.remove(IS_LAST_ITERATION);
    }

    public Map<String, String> gatherScenarioReferenceData() { return gatherReferenceData(SCENARIO_REF_PREFIX); }

    public Map<String, String> gatherScriptReferenceData() { return gatherReferenceData(SCRIPT_REF_PREFIX); }

    public void addScenarioReferenceData(String name, String value) {
        addReferenceData(SCENARIO_REF_PREFIX, name, value);
    }

    public void clearScenarioRefData() { clearReferenceData(SCENARIO_REF_PREFIX); }

    public void addScriptReferenceData(String name, String value) { addReferenceData(SCRIPT_REF_PREFIX, name, value); }

    public void clearScriptRefData() { clearReferenceData(SCRIPT_REF_PREFIX); }

    public void startIteration(int iterationIndex, int iterationRef, int totalIterationCount, boolean firstUse) {
        getTrackTimeLogs();

        // remember whether we want to track execution completion as a time-track event or not
        System.setProperty(TRACK_EXECUTION, getStringData(TRACK_EXECUTION, getDefault(TRACK_EXECUTION)));

        setData(CURR_ITERATION, iterationIndex);
        if (iterationRef != -1) { setData(CURR_ITERATION_ID, iterationRef); }
        if (iterationIndex == 1) {
            setData(IS_FIRST_ITERATION, true);
        } else {
            removeDataForcefully(IS_FIRST_ITERATION);
        }

        if (iterationIndex == totalIterationCount) {
            setData(IS_LAST_ITERATION, true);
        } else {
            removeDataForcefully(IS_LAST_ITERATION);
        }

        // handling events
        ExecutionEventListener eventListener = getExecutionEventListener();
        if (firstUse) { eventListener.onExecutionStart(); }
        if (iterationIndex == 1) { eventListener.onScriptStart(); }
        eventListener.onIterationStart();
    }

    public void endIteration() {
        if (getBooleanData(OPT_INTERACTIVE, false)) { return; }

        if (CollectionUtils.isNotEmpty(testScenarios)) {
            for (TestScenario testScenario : testScenarios) {
                ExecutionSummary executionSummary = testScenario.getExecutionSummary();
                scriptStepCount += executionSummary.getTotalSteps();
                scriptPassCount += executionSummary.getPassCount();
                scriptWarnCount += executionSummary.getWarnCount();
                scriptFailCount += executionSummary.getFailCount();
            }

            testScenarios.forEach(TestScenario::close);
            testScenarios.clear();
            testScenarios = null;
        }

        currentTestStep = null;

        getExecutionEventListener().onIterationComplete();
        removeTrackTimeLogs();

        if (testScript != null) {
            try {
                // (2018/12/16,automike): memory consumption precaution
                testScript.close();
            } catch (IOException e) {
                ConsoleUtils.error("Unable to close Excel file (" + testScript + "): " + e.getMessage());
            }

            testScript = null;
        }
    }

    public void endScript() {
        if (getBooleanData(OPT_INTERACTIVE, false)) { return; }

        execDef = null;

        if (testScript != null) {
            try {
                testScript.close();
            } catch (IOException e) {
                ConsoleUtils.error("Unable to close script (" + testScript.getOriginalFile() + "): " + e.getMessage());
            }

            testScript = null;
        }

        if (CollectionUtils.isNotEmpty(testScenarios)) {
            testScenarios.forEach(TestScenario::close);
            testScenarios.clear();
            testScenarios = null;
        }

        currentTestStep = null;
    }

    public int getScriptStepCount() { return scriptStepCount; }

    public int getScriptPassCount() { return scriptPassCount; }

    public int getScriptFailCount() { return scriptFailCount; }

    public int getScriptWarnCount() { return scriptWarnCount; }

    // support flow controls - FailIf()
    public boolean isFailImmediate() { return getBooleanData(FAIL_IMMEDIATE, false); }

    public void setFailImmediate(boolean failImmediate) { data.put(FAIL_IMMEDIATE, failImmediate); }

    // support flow controls - EndIf()
    public boolean isEndImmediate() { return getBooleanData(END_IMMEDIATE, false); }

    public void setEndImmediate(boolean endImmediate) { data.put(END_IMMEDIATE, endImmediate); }

    // support flow control - EndLoopIf()
    public boolean isBreakCurrentIteration() {
        return getBooleanData(BREAK_CURRENT_ITERATION, false) || isFailImmediate();
    }

    public void setBreakCurrentIteration(boolean breakLoop) { setData(BREAK_CURRENT_ITERATION, breakLoop); }

    @NotNull
    public TrackTimeLogs getTrackTimeLogs() {
        TrackTimeLogs trackTimeLogs = null;
        Object obj = getObjectData(NAME_TRACK_TIME_LOGS);
        if (obj instanceof TrackTimeLogs) { trackTimeLogs = (TrackTimeLogs) obj; }

        if (trackTimeLogs == null) {
            trackTimeLogs = new TrackTimeLogs();
            setData(NAME_TRACK_TIME_LOGS, trackTimeLogs);
        }

        return trackTimeLogs;
    }

    public void removeTrackTimeLogs() { removeData(NAME_TRACK_TIME_LOGS); }

    /** iteration-scoped execution */
    public boolean execute() throws IOException {
        markExecutionStart();

        boolean allPass = true;

        // gather pre-execution reference data here, so that after the execution we can reset the reference data
        // set back to its pre-execution state
        Map<String, String> ref = gatherScenarioReferenceData();

        for (TestScenario testScenario : testScenarios) {
            // re-init scenario ref data
            clearScenarioRefData();
            ref.forEach((name, value) -> data.put(SCENARIO_REF_PREFIX + name, replaceTokens(value)));

            if (!testScenario.execute()) {
                allPass = false;

                if (isFailFast()) {
                    executionLogger.log(testScenario, MSG_SCENARIO_FAIL_FAST);
                    break;
                }
            }

            if (isFailImmediate()) {
                executionLogger.log(this, MSG_EXEC_FAIL_IMMEDIATE);
                break;
            }

            if (isEndImmediate()) {
                executionLogger.log(this, MSG_EXEC_END_IF);
                break;
            }

            if (isBreakCurrentIteration()) {
                executionLogger.log(this, MSG_EXEC_END_LOOP_IF);
                break;
            }
        }

        markExecutionEnd();

        MemManager.gc(this);

        return allPass;
    }

    public void markExecutionEnd() {
        endTimestamp = System.currentTimeMillis();
        endDateTime = DF_TIMESTAMP.format(endTimestamp);
    }

    public void markExecutionStart() {
        startTimestamp = System.currentTimeMillis();
        startDateTime = DF_TIMESTAMP.format(startTimestamp);
    }

    public boolean containsCrypt(String data) {
        try {
            if (StringUtils.contains(data, CRYPT_IND)) { return true; }

            if (TextUtils.isBetween(data, TOKEN_START, TOKEN_END)) {
                return StringUtils.contains(CellTextReader.readValue(replaceTokens(data)), CRYPT_IND);
            }

            // if the value can be mapped to a crypted value -OR-
            // if we still have ${...} pattern after token replacement,
            // then that's means one or more data variables are crypted (retainCrypt=true)

            // if (!StringUtils.equals(data, CellTextReader.readValue(data))) { return true; }
            if (CellTextReader.isCrypt(data)) { return true; }

            // no variables means no crypt
            Set<String> variables = findTokens(data);
            return CollectionUtils.isNotEmpty(variables) &&
                   variables.stream()
                            .anyMatch(var -> CellTextReader.isCrypt(replaceTokens(TOKEN_START + var + TOKEN_END)));
        } catch (RuntimeException e) {
            // don't worry it now.. Nexial will eventually barf at such exception later on (and probably at a better spot).
            return false;
        }
    }

    public static boolean getSystemThenContextBooleanData(String name, ExecutionContext context, boolean def) {
        return BooleanUtils.toBoolean(
            System.getProperty(name, (context == null ? def : context.getBooleanData(name, def)) + ""));
    }

    public static int getSystemThenContextIntData(String name, ExecutionContext context, int def) {
        return NumberUtils.toInt(
            System.getProperty(name, (context == null ? def : context.getIntData(name, def)) + ""));
    }

    public static String getSystemThenContextStringData(String name, ExecutionContext context, String def) {
        if (System.getProperty(name) == null) {
            String value = context == null ? def : context.getStringData(name, def);
            System.setProperty(name, value);
            return value;
        } else {
            return System.getProperty(name, def);
        }
    }

    public static String unescapeToken(String text) {
        return StringUtils.replace(StringUtils.replace(text, ESCAPED_TOKEN_START, TOKEN_START),
                                   ESCAPED_TOKEN_END, TOKEN_END);
    }

    public static String escapeToken(String text) {
        return StringUtils.replace(StringUtils.replace(text, TOKEN_START, ESCAPED_TOKEN_START),
                                   TOKEN_END, ESCAPED_TOKEN_END);
    }

    protected String handleCryptValue(String text) {
        if (StringUtils.isBlank(text)) { return text; }
        if (!StringUtils.contains(text, CRYPT_IND)) { return text; }

        String crypt = RegexUtils.firstMatches(text, REGEX_CRYPT);
        while (StringUtils.isNotBlank(crypt)) {
            String decrypt = CellTextReader.getText(crypt);
            if (StringUtils.isNotBlank(decrypt)) {
                text = StringUtils.replace(text, crypt, decrypt);
                crypt = RegexUtils.firstMatches(text, REGEX_CRYPT);
            } else {
                break;
            }
        }

        return text;
    }

    /**
     * updating corresponding System property when a data variable of the same name is updated. Note that the purpose of
     * this method is to keep in sync the data variable that might have been set as System variable with those defined
     * in data sheets. Data variables defined via command line (<code>-Dname=value</code>, for example) and
     * <code>project.properties</code> are kept as System properties. Suppose the same data variable is updated with
     * new value during execution, then we need to update both the data variable repo ({@link #data}) and System
     * properties to keep the same data variable in sync.
     *
     * Note that System properties will be updating <b>ONLY</b> IF:
     * <ol>
     * <li>{@code name} is valid (not null)</li>
     * <li>the same data variable is already defined as System properties (hence needing to overwrite)</li>
     * <li>execution is currently in progress</li>
     * </ol>
     */
    protected void setDataOrSysProp(String name, Object value) {
        // we will only update System properties IF:
        // 1) we have valid/non-empty name/value
        // 2) same property found from System (hence needing to overwrite)
        // 3) we are currently automating a test scenario/activity/step (ie. currentTestStep != null)
        if (StringUtils.isBlank(name)) { return; }

        boolean isNullValue = value == null || isNullValue(Objects.toString(value));

        if (data.containsKey(name) && isNullValue) { CellTextReader.unsetValue(Objects.toString(value)); }

        boolean remove = isNullValue;
        if (value instanceof Map && MapUtils.isEmpty((Map) value)) { remove = true; }
        if (value instanceof Collection && CollectionUtils.isEmpty((Collection) value)) { remove = true; }

        if (remove) {
            data.remove(name);
            System.clearProperty(name);
            return;
        }

        if (StringUtils.isEmpty(System.getProperty(name))) {
            data.put(name, value);

            // some reference data are considered "special" and should be elevated to "execution" level so that they
            // can be used as such for Execution Dashboard
            if (referenceDataForExecution.contains(name)) { System.setProperty(name, Objects.toString(value)); }
        } else {
            System.setProperty(name, Objects.toString(value));
        }
    }

    /**
     * perhaps it's a system property? first check System property, then internal map
     */
    protected String getRawStringData(String name) { return System.getProperty(name, MapUtils.getString(data, name)); }

    protected String getRawStringData(String name, String def) {
        return System.getProperty(name, MapUtils.getString(data, name, def));
    }

    protected boolean isListCompatible(String value) {
        String delim = getTextDelim();
        return StringUtils.isNotBlank(value) && !StringUtils.equals(value, delim) && StringUtils.contains(value, delim);
    }

    protected boolean containListAccess(String text, String tokenized) {
        if (StringUtils.isEmpty(text) || StringUtils.isEmpty(tokenized)) { return false; }
        return RegexUtils.match(text, escapeToken(tokenized) + "\\[[0-9]+\\]", true);
    }

    protected void initDj() {
        // text-to-speech (tts) via AWS Polly
        dj = springContext.getBean("soundMachine", SoundMachine.class);
    }

    protected void clearReferenceData(String prefix) {
        Object[] deleteCandidates = data.keySet().stream().filter(key -> StringUtils.startsWith(key, prefix)).toArray();
        for (Object deleteCandidate : deleteCandidates) { data.remove(Objects.toString(deleteCandidate)); }

        Object[] sysProps = System.getProperties().keySet().toArray();
        for (Object key : sysProps) {
            String sKey = Objects.toString(key);
            if (StringUtils.startsWith(sKey, prefix)) { System.clearProperty(sKey); }
        }
    }

    protected Map<String, String> gatherReferenceData(String prefix) { return getDataByPrefix(prefix); }

    protected void addReferenceData(String prefix, String name, String value) {
        if (StringUtils.isBlank(name)) { return; }
        setData(prefix + name, value);
    }

    protected static Set<String> findTokens(String text) {
        String[] tokenArray = StringUtils.substringsBetween(text, TOKEN_START, TOKEN_END);
        Set<String> tokens = new HashSet<>();
        if (tokenArray != null && tokenArray.length > 0) { Collections.addAll(tokens, tokenArray); }
        return tokens;
    }

    protected String replaceCollectionTokens(String text,
                                             Map<String, Object> collectionValues,
                                             Map<String, Object> complexValues) {

        for (String collectionToken : collectionValues.keySet()) {
            String token = TOKEN_START + collectionToken + TOKEN_END;
            Object value = collectionValues.get(collectionToken);
            boolean isValueArray = value.getClass().isArray();
            int arraySize = isValueArray ? ArrayUtils.getLength(value) : ((Collection) value).size();

            // no point continuing
            if (arraySize < 1) { continue; }

            while (StringUtils.contains(text, token)) {
                String postToken = StringUtils.substringAfter(text, token);
                if (!StringUtils.startsWith(postToken, TOKEN_ARRAY_START)) {

                    // can't find the ${data}[#] pattern.  so user wants _ALL_ rows of the same column?
                    if (StringUtils.startsWith(postToken, ".")) {
                        // this means that user wants the same column for _ALL_ rows
                        // right now, we only consider space or dot as the column delimiter
                        String propName = StringUtils.replaceChars(postToken.substring(1), ".;:|,\t\r\n", "        ");
                        propName = StringUtils.substringBefore(propName, " ");

                        String searchBy = token + "." + propName;
                        String replaceBy = isValueArray ?
                                           flattenArrayOfObject(value, propName) :
                                           flattenCollection((Collection) value, propName);
                        if (replaceBy == null) {
                            text = StringUtils.equals(text, searchBy) ?
                                   null : StringUtils.replace(text, searchBy, getNullValueToken());
                        } else {
                            text = StringUtils.replace(text, searchBy, replaceBy);
                        }
                    } else {
                        // just replace ${data} with value stringified
                        text = flattenAsString(text, token, value);
                    }

                    continue;
                }

                postToken = StringUtils.substring(postToken, 1);
                String indexStr = StringUtils.substringBefore(postToken, TOKEN_ARRAY_END);
                if (StringUtils.isBlank(indexStr) || !NumberUtils.isDigits(indexStr)) {
                    // can't find the ${data}[#] pattern.  just replace ${data} with value stringified
                    text = flattenAsString(text, token, value);
                    continue;
                }

                int index = NumberUtils.toInt(indexStr);
                String indexedToken = token + TOKEN_ARRAY_START + indexStr + TOKEN_ARRAY_END;

                // index out of bound means empty string
                Object indexedValue = arraySize <= index ?
                                      "" : isValueArray ? Array.get(value, index) : CollectionUtils.get(value, index);
                if (SIMPLE_VALUES.contains(indexedValue.getClass())) {
                    text = StringUtils.replace(text, indexedToken, String.valueOf(indexedValue));
                } else {
                    // keep this complex data in complexValues for now so that we can use it in the next pass
                    String complexValueKey =
                        KEY_COMPLEX + collectionToken + TOKEN_ARRAY_START + indexStr + TOKEN_ARRAY_END;
                    complexValues.put(complexValueKey, indexedValue);
                    text = StringUtils.replace(text, indexedToken, TOKEN_START + complexValueKey + TOKEN_END);
                }
            }
        }

        return text;
    }

    /**
     * strategy to replacing complex object structure via the textual notation in {@code text}.
     * <p>
     * we want to implement a dot-style object reference so that we can interrogate nested object property
     * of an object.  For example, a.b.c.d would be something like a.getB().getC().getD().
     * However, the dot character might be used as a part of the "text" parameter as a legitimate character.
     * We would use escape-dot ( \. ) to represent a dot literal.
     * <p>
     * Strategy:
     * 1) replace dot-literal with another character sequence (DOT_LITERAL_REPLACER)
     * 2) find next token prior to dot
     * 3) replace token with value
     * 4) recurse next token set with the derived object
     * 5) at the last recursion, replace DOT_LITERAL_REPLACER back to dot-literal
     */
    protected String replaceComplexTokens(String text, Map<String, Object> complexValues) {
        text = StringUtils.replace(text, DOT_LITERAL, DOT_LITERAL_REPLACER);

        // todo: we need to consider storing resulting object as complex type, while still returning string

        for (String objToken : complexValues.keySet()) {
            String token = TOKEN_START + objToken + TOKEN_END;
            while (StringUtils.contains(text, token)) {
                Map<String, String> data = new HashMap<>();
                data.put(NAME_TOKEN, token);
                data.put(NAME_POST_TOKEN, StringUtils.substringAfter(text, token));
                data.put(NAME_INITIAL, token);
                text = replaceNestedTokens(text, data, complexValues.get(objToken));
            }
        }

        // put back all the literals.
        return StringUtils.replace(text, DOT_LITERAL_REPLACER, ".");
    }

    protected String replaceNestedTokens(String text, Map<String, String> data, Object value) {
        // sanity check
        String token = data.get(NAME_TOKEN);
        if (value == null) { return StringUtils.replace(text, token, ""); }

        String postToken = data.get(NAME_POST_TOKEN);

        // if value is simple type, then no more introspection can be done.
        // can't find the ${data}.xyz or ${data}.[xyz] pattern.  just replace ${data} with value stringified
        if (isSimpleType(value)) { return flattenAsString(text, token, value); }

        // special treatment for index-reference (list or array)
        if ((value.getClass().isArray() || Collection.class.isAssignableFrom(value.getClass())) &&
            StringUtils.startsWith(postToken, TOKEN_ARRAY_START) && StringUtils.contains(postToken, TOKEN_ARRAY_END)) {

            String propName = StringUtils.substring(postToken, 1, StringUtils.indexOf(postToken, TOKEN_ARRAY_END, 1));
            if (!NumberUtils.isDigits(propName)) { return flattenArrayOfObject(value, propName); }

            token += TOKEN_ARRAY_START + propName + TOKEN_ARRAY_END;
            postToken = StringUtils.substringAfter(postToken, propName + TOKEN_ARRAY_END);

            Object newValue = null;
            int propIndex = NumberUtils.toInt(propName);
            if (Collection.class.isAssignableFrom(value.getClass())) {
                Collection collection = ((Collection) value);
                newValue = collection.size() > propIndex ? IterableUtils.get(collection, propIndex) : "";
            } else if (value.getClass().isArray()) {
                newValue = ArrayUtils.getLength(value) > propIndex ? Array.get(value, propIndex) : "";
            }

            // newValue could be null if the index is out of bound
            if (newValue == null) { return flattenAsString(text, data.get(NAME_TOKEN), value); }

            value = newValue;
            data.put(NAME_TOKEN, token);
            data.put(NAME_POST_TOKEN, postToken);
            return replaceNestedTokens(text, data, value);
        }

        // can't find the ${data}.xyz or ${data}.[xyz] pattern.  just replace ${data} with value stringified
        if (!StringUtils.startsWith(postToken, ".")) { return flattenAsString(text, token, value); }

        // at this point, we need to consider nested object reference

        // look for next prop
        String propName;
        if (StringUtils.startsWith(postToken, "." + TOKEN_ARRAY_START)) {
            // find next .[prop]
            propName = StringUtils.substring(postToken, 2, StringUtils.indexOf(postToken, TOKEN_ARRAY_END, 2));
            token += "." + TOKEN_ARRAY_START + propName + TOKEN_ARRAY_END;
            postToken = StringUtils.substringAfter(postToken, propName + TOKEN_ARRAY_END);
        } else {
            // find next .prop, which should ends before next dot or whitespace, or EOL.
            int propDelimIndex = StringUtils.indexOfAnyBut(StringUtils.substring(postToken, 1), NON_DELIM_CHARS);
            if (propDelimIndex == 0) { return flattenAsString(text, token, value); }
            if (propDelimIndex < 1) {
                propDelimIndex = postToken.length();
            } else {
                propDelimIndex++;
            }

            propName = StringUtils.substring(postToken, 1, propDelimIndex);
            if (StringUtils.isBlank(propName)) { return flattenAsString(text, data.get(NAME_TOKEN), value); }

            token += "." + propName;
            postToken = StringUtils.substring(postToken, propDelimIndex);
        }

        // array and collection can only be accessed via index
        if ((value.getClass().isArray() || Collection.class.isAssignableFrom(value.getClass()))
            && !NumberUtils.isDigits(propName)) {
            return flattenArrayOfObject(value, propName);
        }

        // match prop to value
        Object newValue;
        try {
            if (Map.class.isAssignableFrom(value.getClass())) {
                newValue = ObjectUtils.defaultIfNull(((Map) value).get(propName), "");
            } else {
                int index = NumberUtils.toInt(propName);
                if (Collection.class.isAssignableFrom(value.getClass())) {
                    Collection collection = (Collection) value;
                    newValue = collection.size() > index ?
                               ObjectUtils.defaultIfNull(IterableUtils.get(collection, index), "") : "";
                } else if (value.getClass().isArray()) {
                    newValue = ArrayUtils.getLength(value) > index ?
                               ObjectUtils.defaultIfNull(Array.get(value, index), "") : "";
                } else {
                    // object treatment
                    if (PropertyUtils.isReadable(value, propName)) {
                        newValue = ObjectUtils.defaultIfNull(PropertyUtils.getProperty(value, propName), "");
                    } else {
                        // last resort: treat 'propName' like a real method
                        String args = StringUtils.substringBetween(postToken, TOKEN_ARRAY_START, TOKEN_ARRAY_END);
                        newValue = ObjectUtils.defaultIfNull(
                            MethodUtils.invokeMethod(value, propName, StringUtils.split(args, getDefault(TEXT_DELIM))),
                            "");
                        if (StringUtils.isNotEmpty(args)) {
                            token += TOKEN_ARRAY_START + args + TOKEN_ARRAY_END;
                            postToken = StringUtils.substringAfter(postToken, args + TOKEN_ARRAY_END);
                        }
                    }
                }
            }
        } catch (IllegalAccessException | InvocationTargetException | IndexOutOfBoundsException | NoSuchMethodException e) {
            logger.warn("Unable to retrieve '" + propName + "' from " + value + ": " + e.getMessage());
            newValue = null;
        }

        if (newValue == null) { return flattenAsString(text, data.get(NAME_TOKEN), value); }

        value = newValue;
        data.put(NAME_TOKEN, token);
        data.put(NAME_POST_TOKEN, postToken);
        return replaceNestedTokens(text, data, value);
    }

    protected static boolean isSimpleType(Object value) {
        return value == null || SIMPLE_VALUES.contains(value.getClass());
    }

    /**
     * built-in function support; search for pattern of "blah|yada|stuff"
     */
    protected boolean isFunction(String token) {
        if (StringUtils.isBlank(token)) { return false; }
        List<String> groups = RegexUtils.collectGroups(token, REGEX_FUNCTION);
        boolean match = CollectionUtils.isNotEmpty(groups) && StringUtils.isNotBlank(groups.get(0));
        return match && builtinFunctions.containsKey(groups.get(0));
    }

    protected String invokeFunction(String token) {
        if (StringUtils.countMatches(token, TOKEN_PARAM_SEP) < 1) {
            throw new IllegalArgumentException("reference to a built-in function NOT shown via the $(...|...) format");
        }

        String errorPrefix = "Invalid built-in function " + TOKEN_FUNCTION_START + token + TOKEN_FUNCTION_END + " ";

        Function f = null;

        try {
            f = new Function();
            f.parse(token);

            errorPrefix += " with " + ArrayUtils.getLength(f.parameters) + " parameter(s) - ";

            return Objects.toString(MethodUtils.invokeExactMethod(f.function, f.operation, f.parameters), "");
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(errorPrefix + "cannot be resolved", e);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(errorPrefix + "invalid: '" + f.operation + "'", e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(errorPrefix + "inaccessible: '" + f.operation + "'", e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause != null) {
                throw new RuntimeException(errorPrefix + "error on '" + f.operation + "': " + cause.getMessage(), e);
            } else {
                throw new RuntimeException(errorPrefix + "error on '" + f.operation + "': " + e.getMessage(), e);
            }
        }
    }

    /** find the next built-in function in {@code text}. */
    protected String nextFunctionToken(String text) {
        if (StringUtils.isBlank(text)) { return text; }

        // circumvent the escaped conflicting character: $, ( and )
        text = preFunctionParsing(text);

        if (!StringUtils.contains(text, TOKEN_FUNCTION_START)) { return null; }

        String tokenStart = StringUtils.substringAfterLast(text, TOKEN_FUNCTION_START);
        if (!StringUtils.contains(tokenStart, TOKEN_FUNCTION_END)) { return null; }

        String token = StringUtils.substringBefore(tokenStart, TOKEN_FUNCTION_END);

        // if we found only `${ }` or `${<tab>}`, then forget it
        if (StringUtils.isBlank(token)) { return null; }

        // put the alt-replaced characters back
        token = postFunctionParsing(token);

        if (isFunction(token)) { return token; }

        // previously uncovered $(...) does not resolve to a function.
        // in order to prevent re-surfacing the bad $(...) pattern, we'll replace it with something else and switch
        // to its initial form during invokeFunction()

        // keep search for $(...) pattern
        return nextFunctionToken(text.replace(TOKEN_FUNCTION_START + token + TOKEN_FUNCTION_END,
                                              TOKEN_DEFUNC_START + token + TOKEN_DEFUNC_END));
    }

    protected String resolveDeferredTokens(String text) {
        String nullValue = getNullValueToken();
        String[] tokens = StringUtils.substringsBetween(text, DEFERRED_TOKEN_START, DEFERRED_TOKEN_END);

        if (ArrayUtils.isEmpty(tokens)) { return text; }

        for (String token : tokens) {
            String replaceToken = TOKEN_START + token + TOKEN_END;
            String searchToken = DEFERRED_TOKEN_START + token + DEFERRED_TOKEN_END;

            String replace = StringUtils.equals(searchToken, nullValue) ? null : replaceTokens(replaceToken);
            if (replace == null) {
                if (StringUtils.equals(text, searchToken)) {
                    text = null;
                    continue;
                } else {
                    text = nullValue;
                }
            }

            text = StringUtils.replace(text, searchToken, replace);
        }

        return text;
    }

    protected String[] resolveDeferredTokens(String[] text) {
        if (ArrayUtils.isEmpty(text)) { return text; }

        List<String> replaced = new ArrayList<>();
        for (String item : text) { replaced.add(resolveDeferredTokens(item)); }

        return replaced.toArray(new String[0]);
    }

    protected String mergeProperty(String value) {
        // find the last one first as a way to deal with nested tokens
        int startIdx = StringUtils.lastIndexOf(value, TOKEN_START);
        int endIdx = StringUtils.indexOf(value, TOKEN_END, startIdx + TOKEN_START.length());

        while (startIdx != -1 && endIdx != -1) {
            String var = StringUtils.substring(value, startIdx + TOKEN_START.length(), endIdx);
            String searchFor = TOKEN_START + var + TOKEN_END;
            if (data.containsKey(var)) {
                searchFor = searchFor + getIndexedRef(value, endIdx + TOKEN_END.length());
                value = StringUtils.replace(value, searchFor, replaceTokens(searchFor));
            } else {
                // mark the irreplaceable token so we can put things back later
                value = StringUtils.replace(value, searchFor, "~[[" + var + "]]~");
            }

            startIdx = StringUtils.lastIndexOf(value, TOKEN_START);
            if (startIdx == -1) { break; }

            endIdx = StringUtils.indexOf(value, TOKEN_END, startIdx + TOKEN_START.length());
        }

        value = StringUtils.replace(value, "~[[", TOKEN_START);
        value = StringUtils.replace(value, "]]~", TOKEN_END);

        return value;
    }

    protected static String getIndexedRef(String value, int expectedArrayStartIdx) {
        // find `[` and `]` after TOKEN End Index
        int startArrayIdx = StringUtils.indexOf(value, TOKEN_ARRAY_START, expectedArrayStartIdx);
        int endArrayIdx = StringUtils.indexOf(value, TOKEN_ARRAY_END, expectedArrayStartIdx);

        // Ignore index ref if is not after token
        if (startArrayIdx != -1 && endArrayIdx != -1 && expectedArrayStartIdx == startArrayIdx) {
            String listIndex = StringUtils.substring(value, startArrayIdx + TOKEN_ARRAY_START.length(), endArrayIdx);
            if (NumberUtils.isDigits(listIndex)) { return TOKEN_ARRAY_START + listIndex + TOKEN_ARRAY_END; }
        }
        return "";
    }

    protected String encodeForUrl(String filename) {
        try {
            String fileLocation = StringUtils.replace(filename, "\\", "/");
            String filePath = StringUtils.substringBeforeLast(fileLocation, "/");
            String fileNameOnly =
                URLEncoder.encode(
                    OutputFileUtils.webFriendly(StringUtils.substringAfterLast(fileLocation, "/")),
                    "UTF-8");

            fileLocation = filePath + "/" + fileNameOnly;
            if (File.separatorChar == '\\') { fileLocation = StringUtils.replace(fileLocation, "/", "\\"); }

            return fileLocation;
        } catch (UnsupportedEncodingException e) {
            // shouldn't have problems since the file
            ConsoleUtils.error("Unable to encode filename '" + filename + "': " + e.getMessage());
            return filename;
        }
    }

    protected void clearCurrentTestStep() { this.currentTestStep = null; }

    protected TestProject adjustPath(ExecutionDefinition execDef) {
        // run id is not determined earlier, hence we are considering it now as part of output path
        TestProject project = execDef.getProject().copy();
        project.setOutPath(StringUtils.appendIfMissing(project.getOutPath(), separator) + execDef.getRunId());

        // merge project meta data
        setData(OPT_PROJECT_NAME, project.getName(), true);
        setData(OPT_PROJECT_BASE, project.getProjectHome(), true);
        setData(OPT_OUT_DIR, project.getOutPath(), true);
        setData(OPT_SCRIPT_DIR, project.getScriptPath(), true);
        setData(OPT_DATA_DIR, project.getDataPath(), true);
        setData(OPT_PLAN_DIR, project.getPlanPath(), true);

        return project;
    }

    protected void parse() throws IOException {
        // parse and collect all relevant test data so we can merge then into iteration-bound test script

        // 1. make range for data
        Worksheet dataSheet = testScript.worksheet(SHEET_MERGED_DATA);

        ExcelAddress addr = new ExcelAddress("A1");
        XSSFCell firstCell = dataSheet.cell(addr);
        if (StringUtils.isBlank(Excel.getCellValue(firstCell))) {
            throw new IllegalArgumentException("File (" + testScript + "), Worksheet (" + dataSheet.getName() + "): " +
                                               "no test data defined");
        }

        // 2. retrieve all data in range
        int endRowIndex = dataSheet.findLastDataRow(addr) + 1;
        for (int i = 1; i < endRowIndex; i++) {
            ExcelAddress addrRow = new ExcelAddress("A" + i + ":B" + i);
            List<XSSFCell> row = dataSheet.cells(addrRow).get(0);
            String name = Excel.getCellValue(row.get(0));
            String value = Excel.getCellValue(row.get(1));
            // no longer checks for blank value, we'll accept data value as is since it's been dealt with in
            // ExecutionThread.prep()
            // if (StringUtils.isNotBlank(value)) {
            data.put(name, value);
            // }
        }

        // 3. parse test scenarios
        testScenarios = new ArrayList<>();
        for (int i = 0; i < execDef.getScenarios().size(); i++) {
            String scenario = execDef.getScenarios().get(i);
            Worksheet worksheet = testScript.worksheet(scenario);
            if (worksheet == null) {
                throw new IOException("Specified scenario '" + scenario + "' not found");
            } else {
                testScenarios.add(new TestScenario(this, worksheet));
            }
        }

        // 4. pdf specific parsing for custom key-value extraction strategy
        CommonKeyValueIdentStrategies.harvestStrategy(this);

        // 5. fill in to sys prop, if not defined - only for critical sys prop
        defaultContextProps.forEach((name, def) -> {
            if (StringUtils.isBlank(System.getProperty(name))) {
                String value = hasData(name) ? getStringData(name) : def;
                if (StringUtils.isNotEmpty(value)) { System.setProperty(name, value); }
            }
        });

        // support dynamic resolution of WPS executable path
        String spreadsheetProgram = getStringData(SPREADSHEET_PROGRAM, getDefault(SPREADSHEET_PROGRAM));
        if (StringUtils.equals(spreadsheetProgram, SPREADSHEET_PROGRAM_WPS) && IS_OS_WINDOWS) {
            setData(WPS_EXE_LOCATION, Excel.resolveWpsExecutablePath());
        }

        if (StringUtils.isBlank(System.getProperty(OPT_OPEN_RESULT))) {
            boolean openResult = MapUtils.getBoolean(data, OPT_OPEN_RESULT, getDefaultBool(OPT_OPEN_RESULT));
            System.setProperty(OPT_OPEN_RESULT, openResult + "");
        }

        // DO NOT SET BROWSER TYPE TO SYSTEM PROPS, SINCE THIS WILL PREVENT ITERATION-LEVEL OVERRIDES
        // System.setProperty(SPREADSHEET_PROGRAM, spreadsheetProgram);

        ExecutionMailConfig.configure(this);
    }

    private void initSpringBeans() {
        failfastCommands = springContext.getBean("failfastCommands", new ArrayList<String>().getClass());

        // init built-in variables
        builtinFunctions = springContext.getBean("builtinFunctions", new HashMap<String, Object>().getClass());

        // otc=output-to-cloud (S3)
        otc = springContext.getBean("otc", NexialS3Helper.class);
        otc.setContext(this);
        otcNotReadyMessage = springContext.getBean("otcNotReadyMessage", String.class);

        // AWS SNS
        smsHelper = springContext.getBean("smsHelper", SmsHelper.class);

        // mail notifier
        nexialMailer = springContext.getBean("nexialMailer", NexialMailer.class);
        nexialMailer.setContext(this);

        // event listener
        executionEventListener = springContext.getBean("executionEventListener", ExecutionEventListener.class);
        executionEventListener.setContext(this);

        // default / reference
        defaultContextProps = springContext.getBean("defaultContextProps", new HashMap<String, String>().getClass());
        referenceDataForExecution = springContext.getBean("nexial.referenceDataForExecution", List.class);
        readOnlyVars = springContext.getBean("readOnlyVars", new ArrayList<String>().getClass());

        // web driver
        webdriverHelperConfig =
            springContext.getBean("webdriverHelperConfig", new HashMap<BrowserType, String>().getClass());

        // thymeleaf template engine
        templateEngine = springContext.getBean("htmlTemplateEngine", TemplateEngine.class);
    }

    @NotNull
    private static String preFunctionParsing(String text) {
        text = StringUtils.replace(text, ESCAPED_DOLLAR, ALT_DOLLAR);
        text = StringUtils.replace(text, ESCAPED_OPEN_PARENTHESIS, ALT_OPEN_PARENTHESIS);
        text = StringUtils.replace(text, ESCAPED_CLOSE_PARENTHESIS, ALT_CLOSE_PARENTHESIS);
        return StringUtils.replace(text, ESCAPED_PIPE, ALT_PIPE);
    }

    @NotNull
    private static String postFunctionParsing(String text) {
        text = StringUtils.replace(text, ALT_PIPE, ESCAPED_PIPE);
        text = StringUtils.replace(text, ALT_CLOSE_PARENTHESIS, ESCAPED_CLOSE_PARENTHESIS);
        text = StringUtils.replace(text, ALT_OPEN_PARENTHESIS, ESCAPED_OPEN_PARENTHESIS);
        return StringUtils.replace(text, ALT_DOLLAR, ESCAPED_DOLLAR);
    }

    private Map<String, Object> getDataMap() { return data; }

    private void overrideIfSysPropFound(String propName) {
        if (StringUtils.isNotBlank(System.getProperty(propName))) { setData(propName, System.getProperty(propName)); }
    }

    private String flattenAsString(Object value) {
        if (value == null) { return ""; }

        String listSep = getTextDelim();
        String groupSep = "\n";
        String mapSep = "=";

        // object[][] should be flatten same as Map<>
        // object[] should be flatten same as Collection<>
        //
        // Collection<Map<>> would be flatten as
        //  key1=value1
        //  key2=value2, key3=value3
        //  key4=value4
        //
        // Map<Object,Collection<>> would be flatten as
        //  key1=value1,value2
        //  key2=value3,value4
        //
        // Collection<Collection<>> would be flatten as
        //  value1,value1a,value1b,value2,value2a,value2b

        StringBuilder buffer = new StringBuilder();
        if (value.getClass().isArray()) {
            int length = ArrayUtils.getLength(value);
            for (int i = 0; i < length; i++) {
                buffer.append(flattenAsString(Array.get(value, i))).append(listSep);
            }
            return StringUtils.removeEnd(buffer.toString(), listSep);
        }

        if (value instanceof Collection) {
            int size = CollectionUtils.size(value);
            for (int i = 0; i < size; i++) {
                buffer.append(flattenAsString(CollectionUtils.get(value, i))).append(listSep);
            }
            return StringUtils.removeEnd(buffer.toString(), listSep);
        }

        if (value instanceof Map) {
            Map map = ((Map) value);
            if (MapUtils.isNotEmpty(map)) {
                for (Object key : map.keySet()) {
                    buffer.append(flattenAsString(key)).append(mapSep).append(flattenAsString(map.get(key)))
                          .append(groupSep);
                }
            }
            return StringUtils.removeEnd(buffer.toString(), groupSep);
        }

        return Objects.toString(value);
    }

    private String flattenAsString(String text, String token, Object value) {
        return StringUtils.replaceOnce(text, token, flattenAsString(value));
    }

    private String flattenArrayOfObject(Object value, String property) {
        if (value == null) { return null; }

        String delim = getTextDelim();
        String nullValue = getNullValueToken();
        boolean onlyNull = true;

        StringBuilder buffer = new StringBuilder();
        boolean isArray = value.getClass().isArray();
        int size = isArray ? ArrayUtils.getLength(value) : CollectionUtils.size(value);
        for (int i = 0; i < size; i++) {
            Object item = isArray ? Array.get(value, i) : CollectionUtils.get(value, i);
            if (item == null) { continue; }

            try {
                // special treatment for null value(s)
                // we will return null value as is (not as string 'null');
                // if all value instances of the same prop are also null, then we will return just null as well
                Object prop = BeanUtils.getProperty(item, property);
                if (prop == null) { prop = nullValue; } else { onlyNull = false; }

                buffer.append(prop).append(delim);
            } catch (Exception e) {
                throw new TokenReplacementException("Unable to retrieve property '" + property + "' in " + item, e);
            }
        }

        // if there's no such value (based on prop) or all of them are null, then we'll just return null
        if (buffer.length() == 0 || onlyNull) { return null; }

        // if there's only 1 value that is also null (which should be caught by above statement),
        // then we'll return null as well
        String valueItem = buffer.substring(0, buffer.length() - 1);
        if (StringUtils.equals(valueItem, nullValue)) { return null; }

        // combined value string, which can contain nexial-null (something like (null) )
        return valueItem;
    }

    private String flattenCollection(Collection value, String name) {
        if (CollectionUtils.isEmpty(value)) { return null; }

        // it's possible that we are looking for a "object.prop" replacement. We should check if "value" is a
        // "Collection of map" or just a "collection with props" instance.
        Object testObject = IterableUtils.get(value, 0);
        boolean isMapType = Map.class.isAssignableFrom(testObject.getClass());
        if (isMapType) {
            String delim = getTextDelim();
            String nullValue = getNullValueToken();
            boolean onlyNull = true;

            StringBuilder buffer = new StringBuilder();

            for (Object item : value) {
                if (item == null) { continue; }

                // special treatment for null value(s)
                // we will return null value as is (not as string 'null');
                // if all value instances of the same key/name are also null, then we will return just null as well
                Object itemValue = ((Map) item).get(name);
                if (itemValue == null) {
                    // try again with simple bean prop
                    try {
                        itemValue = BeanUtils.getSimpleProperty(value, name);
                    } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                        // I guess we _REALLY_ don't have this one.. set it as null then
                        itemValue = nullValue;
                    }
                } else {
                    onlyNull = false;
                }

                // keep the series of values of the same key/name together (delimited)
                // because we are returning it as a 'combined' string
                buffer.append(itemValue).append(delim);
            }

            // if there's no such value (based on key/name) or all of them are null, then we'll just return null
            if (buffer.length() == 0 || onlyNull) { return null; }

            // if there's only 1 value that is also null (which should be caught by above statement),
            // then we'll return null as well
            String valueItem = buffer.substring(0, buffer.length() - 1);
            if (StringUtils.equals(valueItem, nullValue)) { return null; }

            // combined value string, which can contain nexial-null (something like (null) )
            return valueItem;
        }

        try {
            return BeanUtils.getProperty(value, name);
        } catch (Exception e) {
            // try again with just bean prop
            try {
                return BeanUtils.getSimpleProperty(value, name);
            } catch (Exception e1) {
                ConsoleUtils.error("Unable to derive '" + name + "' from value " + value + ": " + e.getMessage());
                return null;
            }
        }
    }
}
