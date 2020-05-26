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

import java.io.File;
import java.io.IOException;
import java.security.Security;
import java.text.MessageFormat;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.ParseException;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.nexial.commons.utils.*;
import org.nexial.core.aws.NexialS3Helper;
import org.nexial.core.excel.Excel;
import org.nexial.core.excel.Excel.Worksheet;
import org.nexial.core.interactive.NexialInteractive;
import org.nexial.core.mail.NexialMailer;
import org.nexial.core.model.ExecutionDefinition;
import org.nexial.core.model.ExecutionSummary;
import org.nexial.core.model.TestProject;
import org.nexial.core.reports.ExecutionMailConfig;
import org.nexial.core.reports.ExecutionNotifier;
import org.nexial.core.reports.ExecutionReporter;
import org.nexial.core.spi.NexialExecutionEvent;
import org.nexial.core.spi.NexialListenerFactory;
import org.nexial.core.utils.ConsoleUtils;
import org.nexial.core.utils.ExecUtils;
import org.nexial.core.utils.InputFileUtils;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static java.io.File.separator;
import static java.lang.System.lineSeparator;
import static org.apache.commons.lang3.SystemUtils.IS_OS_WINDOWS;
import static org.apache.commons.lang3.SystemUtils.USER_NAME;
import static org.nexial.core.Nexial.ExecutionMode.*;
import static org.nexial.core.NexialConst.CLI.INTERACTIVE;
import static org.nexial.core.NexialConst.CLI.*;
import static org.nexial.core.NexialConst.*;
import static org.nexial.core.NexialConst.Data.*;
import static org.nexial.core.NexialConst.Exec.*;
import static org.nexial.core.NexialConst.ExitStatus.*;
import static org.nexial.core.NexialConst.Project.*;
import static org.nexial.core.SystemVariables.getDefault;
import static org.nexial.core.SystemVariables.getDefaultInt;
import static org.nexial.core.excel.Excel.MIN_EXCEL_FILE_SIZE;
import static org.nexial.core.excel.ExcelConfig.*;
import static org.nexial.core.model.ExecutionSummary.ExecutionLevel.EXECUTION;
import static org.nexial.core.utils.ExecUtils.NEXIAL_MANIFEST;

/**
 * Main class to run nexial from command line:
 * <ol>
 * <li>
 * This is the simplest case, which will run all the test scenarios in the given script with all the
 * data specified in the data file by the same name in {@code data} directory.  For example,
 * <pre>
 * java org.nexial.core.Nexial -script mytest.xlsx
 * </pre>
 * <li>
 * This example is the same as the above (but more verbose):
 * <pre>
 * java org.nexial.core.Nexial -script mytest.xlsx -data mytest.xlsx
 * </pre>
 * Note that the {@code ${PROJECT_HOME}} is determined via the specified location of the test script (see below
 * for more).  The {@code script} directory and {@code data} directory are assumed as sibling directories to
 * each other, and the {@code output} is assumed to be parallel to the parent directory of the {@code script}
 * directory.
 * </li>
 * <li>
 * This example would be functionally equivalent to the one above:
 * <pre>
 * java org.nexial.core.Nexial -script mytest.xlsx -data ../data/mytest.xlsx -output ../../output
 * </pre>
 * </li>
 * <li>
 * This variation allows user to specify a data file and output directory that is outside of the
 * default/preferred project structure.  For example,
 * <pre>
 * java org.nexial.core.Nexial -script C:\projects\PROJECT_X\artifact\script\mytest.xlsx -data C:\my_private_stash\myowndata.xlsx -output C:\temp\dir1
 * </pre>
 * Note that only the {@code -script} commandline argument is required.  When other arguments are omitted, their
 * equivalent default is assumed.
 * </li>
 * <li>
 * This example allow user to specify the test scenarios to execute within the specified test script:
 * <pre>
 * java org.nexial.core.Nexial -script mytest.xlsx -scenarios Scenario1,Scenario2
 * </pre>
 * This will execute only {@code Scenario1} and {@code Scenario2}, in that order.  Scenarios are essentially
 * individual worksheet of the specified Excel file.  If any of the specified scenarios is not found, Nexial
 * will report the error and promptly exit.<br/>
 * Note that if the scenario contains spaces, use double quotes around the entire argument like so:
 * <pre>
 * java org.nexial.core.Nexial -script mytest.xlsx -scenarios "Scenario 1,Scenario 2"
 * </pre>
 * The comma is the delimiter for the scenarios.
 * </li>
 * <li>
 * This example shows how one can restrict data use by specifying the data sheets in the specified data file:
 * <pre>
 * java org.nexial.core.Nexial -script mytest.xlsx -data mytest.xlsx -datasheets "My Data,Last Sprint's Data"
 * </pre>
 * </li>
 * <li>
 * This example shows how to use another Excel file (template: nexial-plan.xlsx) to coordinate a more
 * complex test execution involving multiple test scripts, test scenarios and data files:
 * <pre>
 * java org.nexial.core.Nexial -plan mytestplan.xlsx
 * </pre>
 * See <a href="https://confluence.ep.com/display/QA/2016-09-13+Sentry+Sprint+0+Meeting+notes+-+Excel+template+and+Desktop+automation+strategy">Confluence page</a>
 * for more details.
 * </li>
 * </ol>
 *
 * <p>
 * This class will do the following:
 * <ol>
 * <li>
 * invoke {@link ExecutionInputPrep} to scan for iterations
 * <ul>
 * <li>current implementation based on {@code @includeSet()} in {@code #data} worksheet</li>
 * <li>create output directory structure and expand the specified excel file into the iteration-specific ones:
 * <pre>
 * ${nexial.outBase}/
 * +-- ${timestamp}-${nexial.excel}/
 *     +-- ${nexial.excel}.xlsx
 *     +-- summary/
 *     +-- iteration1/
 *         +-- logs/
 *         +-- captures/
 *         +-- ${nexial.excel}_1.xlsx
 *     +-- iteration2/
 *         +-- logs/
 *         +-- captures/
 *         +-- ${nexial.excel}_2.xlsx
 *     +-- iteration3/
 *         +-- logs/
 *         +-- captures/
 *         +-- ${nexial.excel}_3.xlsx
 * </pre></li>
 * </ul>
 * </li>
 * <li>
 * iterate through all the derived iterations and invoke ExecutionThreadTest accordingly<br/>
 * The test results will be saved to the {@code summary} directory, the logs and screencapture
 * saved to the respective {@code logs} and {@code captures} directories.
 * </li>
 * <li>
 * invoke {@link NexialMailer} to consolidate all the test results and email it to the specified
 * email addresses (based on {@code nexial.mailTo} variable).
 * </li>
 * </ol>
 */
public class Nexial {
    private static final int THREAD_WAIT_LOG_INTERVAL = 60;
    private static final String SPRING_CONTEXT = "classpath:/nexial-integration.xml";

    private ClassPathXmlApplicationContext springContext;
    private TestProject project;
    private List<ExecutionDefinition> executions;
    private int threadWaitCounter;
    private ExecutionMode executionMode;

    enum ExecutionMode {EXECUTE_SCRIPT, EXECUTE_PLAN, INTERACTIVE, INTEGRATION, READY}

    @SuppressWarnings("PMD.DoNotCallSystemExit")
    public static void main(String[] args) {
        // enforce "unlimited" crypto strength for NexialSetup
        Security.setProperty("crypto.policy", "unlimited");

        Nexial main = null;
        try {
            main = new Nexial();
            main.init(args);
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) {
                // adopted from fail()
                ConsoleUtils.errorBeforeTerminate(e.getMessage());
            } else {
                e.printStackTrace();
                System.err.println(e.getMessage());
            }

            if (main != null) {
                NexialListenerFactory.fireEvent(NexialExecutionEvent.newNexialCmdError(main, args, e));
            }

            usage();
            System.exit(-1);
        }

        ExecutionSummary summary = null;
        try {
            // shutdown hook for Control-C and SIGTERM
            Runtime.getRuntime().addShutdownHook(new Thread(ShutdownAdvisor::forcefullyTerminate));

            // interactive mode, only for stepwise or blockwise execution. to be integrated into studio
            if (main.isInteractiveMode()) {
                if (ExecUtils.isRunningInZeroTouchEnv()) {
                    ConsoleUtils.error(NL + NL + "Nexial Interactive is disabled unit testing or in CI/CD environment");
                    System.exit(RC_NOT_SUPPORT_ZERO_TOUCH_ENV);
                    return;
                }
                main.interact();
                return;
            }

            // normal execution
            if (isActiveExecution(main)) {
                MemManager.recordMemoryChanges("before execution");
                summary = main.execute();
                MemManager.recordMemoryChanges("after execution");
            }

        } catch (Throwable e) {
            ConsoleUtils.error("Unknown/unexpected error occurred: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (isActiveExecution(main)) {
                ConsoleUtils.log("exiting Nexial...");
                System.exit(main.beforeShutdown(summary));
            }
        }
    }

    protected void setExecutionMode(ExecutionMode mode) { this.executionMode = mode; }

    protected boolean isReadyMode() { return executionMode == ExecutionMode.READY; }

    protected boolean isIntegrationMode() { return executionMode == INTEGRATION; }

    protected boolean isInteractiveMode() { return executionMode == ExecutionMode.INTERACTIVE; }

    protected boolean isExecutePlan() { return executionMode == EXECUTE_PLAN; }

    protected boolean isExecuteScript() { return executionMode == EXECUTE_SCRIPT; }

    protected static boolean isActiveExecution(Nexial main) { return main.isExecutePlan() || main.isExecuteScript(); }

    /** read from the commandline and derive the intended execution order. */
    protected void init(String[] args) throws IOException, ParseException {
        ExecUtils.collectCliProps(args);

        // first things first -- do we have all the required system properties?
        String errPrefix = "System property " + NEXIAL_HOME;
        String nexialHome = System.getProperty(NEXIAL_HOME);
        if (StringUtils.isBlank(nexialHome)) { throw new RuntimeException(errPrefix + " missing; unable to proceed"); }
        if (!FileUtil.isDirectoryReadable(nexialHome)) {
            throw new RuntimeException(errPrefix + " does not refer to a valid directory (" + nexialHome + "); " +
                                       "unable to proceed");
        }

        ConsoleUtils.log(NEXIAL_MANIFEST + " starting up...");

        CommandLine cmd = new DefaultParser().parse(OPTIONS, args);

        if (cmd.hasOption(OUTPUT)) {
            // force logs to be pushed into the specified output directory (and not taint other past/concurrent runs)
            String logPath = cmd.getOptionValue(OUTPUT);
            System.setProperty(TEST_LOG_PATH, logPath);

            // we don't have run id at this time... this System property should be updated when run id becomes available
            if (StringUtils.isBlank(System.getProperty(THIRD_PARTY_LOG_PATH))) {
                System.setProperty(THIRD_PARTY_LOG_PATH, logPath);
            }
        }

        boolean isInteractive = cmd.hasOption(INTERACTIVE);

        // plan or script?
        if (cmd.hasOption(PLAN)) {
            // only -script will be supported
            if (isInteractive) {
                throw new ParseException("Interactive Mode is NOT supported with plan files. " +
                                         "Try specifying a script instead");
            }

            this.executions = parsePlanExecution(cmd);
            System.setProperty(NEXIAL_EXECUTION_TYPE, NEXIAL_EXECUTION_TYPE_PLAN);
            executionMode = EXECUTE_PLAN;
        } else if (cmd.hasOption(SCRIPT)) {
            this.executions = parseScriptExecution(cmd);
            System.setProperty(NEXIAL_EXECUTION_TYPE, NEXIAL_EXECUTION_TYPE_SCRIPT);

            if (isInteractive) {
                executionMode = ExecutionMode.INTERACTIVE;
                System.setProperty(OPT_INTERACTIVE, "true");
            } else {
                executionMode = EXECUTE_SCRIPT;
            }
        }

        // any variable override?
        // this is synonymous to using `JAVA_OPT=-D....` from console prior to executing Nexial
        if (cmd.hasOption(OVERRIDE)) {
            String[] overrides = cmd.getOptionValues(OVERRIDE);
            Arrays.stream(overrides).forEach(data -> {
                String[] pair = StringUtils.split(data, "=");
                if (ArrayUtils.getLength(pair) == 2) {
                    ConsoleUtils.log("adding/override data variable " + pair[0] + "=" + pair[1]);
                    System.setProperty(pair[0], pair[1]);
                }
            });
        }

        ConsoleUtils.log("input files and output directory resolved...");

        NexialListenerFactory.fireEvent(NexialExecutionEvent.newNexialStartEvent(this, args, cmd));
    }

    protected List<ExecutionDefinition> parsePlanExecution(CommandLine cmd) throws IOException {
        List<String> testPlanPathList = TextUtils.toList(cmd.getOptionValue(PLAN), getDefault(TEXT_DELIM), true);
        List<ExecutionDefinition> executions = new ArrayList<>();

        if (cmd.hasOption(SUBPLANS) && testPlanPathList.size() > 1) {
            fail("The " + SUBPLANS + " does't work with multiple test plans. Please try with single plan.");
        }

        List<String> subplans = cmd.hasOption(SUBPLANS) ?
                                TextUtils.toList(cmd.getOptionValue(SUBPLANS), ",", true) :
                                new ArrayList<>();

        for (String testPlanPath : testPlanPathList) {
            // String testPlanPath = cmd.getOptionValue(PLAN);
            // 1. based on plan location, determine script, data, output and artifact directory
            if (!InputFileUtils.isValidPlanFile(testPlanPath)) {
                fail("specified test plan (" + testPlanPath + ") is not readable or does not contain valid format.");
            }

            File testPlanFile = new File(testPlanPath);

            // resolve project directory structure based on the {@code testScriptFile} command line input
            project = TestProject.newInstance(testPlanFile);
            if (!project.isStandardStructure()) {
                ConsoleUtils.log("specified plan (" + testPlanFile + ") not following standard project " +
                                 "structure, related directories would not be resolved from commandline arguments.");
            }

            deriveOutputDirectory(cmd, project);

            // 2. parse plan file to determine number of executions (1 row per execution)
            Excel excel = new Excel(testPlanFile, DEF_OPEN_EXCEL_AS_DUP, false);
            List<Worksheet> plans = retrieveValidPlans(subplans, excel);

            plans.forEach(testPlan -> {
                int rowStartIndex = ADDR_PLAN_EXECUTION_START.getRowStartIndex();
                int lastExecutionRow = testPlan.findLastDataRow(ADDR_PLAN_EXECUTION_START);

                for (int i = rowStartIndex; i < lastExecutionRow; i++) {
                    XSSFRow row = testPlan.getSheet().getRow(i);

                    // check for disabled step
                    if (ExecutionInputPrep.isPlanStepDisabled(row)) { continue; }

                    File testScript = deriveScriptFromPlan(row, project, testPlanPath);
                    List<String> scenarios = deriveScenarioFromPlan(row, testScript);
                    Excel dataFile = deriveDataFileFromPlan(row, project, testPlanFile, testScript);
                    if (dataFile == null) {
                        fail("Unable to resolve data file for the test plan specified in ROW " +
                             (row.getRowNum() + 1) + " of " + testPlanFile + ".");
                    }
                    File dataFilePath = dataFile.getOriginalFile();

                    // (2018/12/16,automike): memory consumption precaution
                    try {
                        dataFile.close();
                    } catch (IOException e) {
                        ConsoleUtils.error("Unable to close data file (" + dataFilePath + "): " + e.getMessage());
                    } finally {
                        dataFile = null;
                    }

                    List<String> dataSheets = deriveDataSheetsFromPlan(row, scenarios);

                    // create new definition instance, based on various input and derived values
                    ExecutionDefinition exec = new ExecutionDefinition();
                    exec.setPlanFile(testPlanFile.getAbsolutePath());
                    exec.setPlanFilename(StringUtils.substringBeforeLast(testPlanFile.getName(), "."));
                    exec.setPlanName(testPlan.getName());
                    exec.setPlanSequence((i - rowStartIndex + 1));
                    exec.setDescription(StringUtils.trim(readCellValue(row, COL_IDX_PLAN_DESCRIPTION)));
                    exec.setTestScript(testScript.getAbsolutePath());
                    exec.setScenarios(scenarios);
                    exec.setDataFile(dataFilePath);
                    exec.setDataSheets(dataSheets);
                    exec.setProject(project);

                    // 2.1 mark option for parallel run and fail fast
                    exec.setFailFast(BooleanUtils.toBoolean(
                        StringUtils.defaultIfBlank(readCellValue(row, COL_IDX_PLAN_FAIL_FAST), DEF_PLAN_FAIL_FAST)));
                    exec.setSerialMode(BooleanUtils.toBoolean(
                        StringUtils.defaultIfBlank(readCellValue(row, COL_IDX_PLAN_WAIT), DEF_PLAN_SERIAL_MODE)));
                    exec.setLoadTestMode(BooleanUtils.toBoolean(readCellValue(row, COL_IDX_PLAN_LOAD_TEST)));
                    if (exec.isLoadTestMode()) { fail("Sorry... load testing mode not yet ready for use."); }

                    try {
                        // 3. for each row, parse script (and scenario) and data (and datasheet)
                        exec.parse();
                        executions.add(exec);
                    } catch (IOException e) {
                        fail("Unable to parse successfully for the test plan specified in ROW " +
                             (row.getRowNum() + 1) + " of " + testPlanFile + ".");
                    }
                }
            });

            if (DEF_OPEN_EXCEL_AS_DUP) { FileUtils.deleteQuietly(excel.getFile().getParentFile()); }

            // (2018/12/16,automike): memory consumption precaution
            excel.close();
        }

        // 4. return a list of scripts mixin data
        return executions;
    }

    private List<Worksheet> retrieveValidPlans(List<String> subplans, Excel excel) {
        List<Worksheet> plans = InputFileUtils.retrieveValidPlanSequence(excel);
        // system variable for subplans included

        // filter out subplans to execute if any
        if (CollectionUtils.isEmpty(subplans)) { return plans; }

        // checking for invalid subplans
        List<String> planNames = plans.stream().map(Worksheet::getName).collect(Collectors.toList());
        List<String> invalidSubplans = subplans.stream().filter(testPlan -> !planNames.contains(testPlan)).collect(
            Collectors.toList());

        // system variable for subplans ommitted
        System.setProperty(SUBPLANS_OMITTED, TextUtils.toString(invalidSubplans, ","));

        if (invalidSubplans.size() != 0) {
            fail("The plan " + excel.getFile().getAbsolutePath() + " doesn't contain worksheet/s named " +
                 invalidSubplans + ".");
        }

        List<Worksheet> validPlans = new ArrayList<>(Collections.nCopies(subplans.size(), null));

        plans.stream().filter(testPlan -> subplans.contains(testPlan.getName()))
             .forEach(testPlan -> validPlans.set(subplans.indexOf(testPlan.getName()), testPlan));

        // system variable for subplans included
        System.setProperty(SUBPLANS_INCLUDED, TextUtils.toString(subplans, ","));

        return validPlans;
    }

    protected File deriveScriptFromPlan(XSSFRow row, TestProject project, String testPlan) {
        String testScriptPath = readCellValue(row, COL_IDX_PLAN_TEST_SCRIPT);
        if (StringUtils.isBlank(testScriptPath)) {
            fail("Invalid test script specified in ROW " + (row.getRowNum() + 1) + " of " + testPlan + ".");
        }

        testScriptPath = StringUtils.appendIfMissing(testScriptPath, SCRIPT_FILE_EXT);

        // is the script specified as full path (local PC)?
        if (!RegexUtils.isExact(testScriptPath, "[A-Za-z]\\:\\\\.+") &&
            // is it on network drive (UNC)?
            !StringUtils.startsWith(testScriptPath, "\\\\") &&
            // is it on *NIX or Mac disk?
            !StringUtils.startsWith(testScriptPath, "/")) {

            // ok, not on local drive, not on network drive, not on *NIX or Mac disk
            // let's treat it as a project file
            String testScriptPath1 = project.getScriptPath() + testScriptPath;
            if (FileUtil.isFileReadable(testScriptPath1, MIN_EXCEL_FILE_SIZE)) {
                testScriptPath = testScriptPath1;
            } else {
                // hmm.. maybe it's a relative path based on current plan file?
                testScriptPath = project.getPlanPath() + testScriptPath;
            }
        }

        ConsoleUtils.log("validating test script as " + testScriptPath);
        if (!InputFileUtils.isValidScript(testScriptPath)) {
            // could be abs. path or relative path based on current project
            fail("Invalid/unreadable test script specified in ROW " + (row.getRowNum() + 1) + " of " + testPlan + ".");
        }

        return new File(testScriptPath);
    }

    protected List<String> deriveScenarioFromPlan(XSSFRow row, File testScript) {
        List<String> scenarios = TextUtils.toList(readCellValue(row, COL_IDX_PLAN_SCENARIOS), ",", true);
        if (CollectionUtils.isNotEmpty(scenarios)) { return scenarios; }

        Excel excel = null;

        try {
            excel = new Excel(testScript, DEF_OPEN_EXCEL_AS_DUP);
            List<Worksheet> validScenarios = InputFileUtils.retrieveValidTestScenarios(excel);
            if (CollectionUtils.isEmpty(validScenarios)) {
                fail("No valid scenario found in script " + testScript + ".");
            } else {
                scenarios = new ArrayList<>();
                for (Worksheet scenario : validScenarios) { scenarios.add(scenario.getName()); }
            }
        } catch (IOException e) {
            fail("Unable to collect scenarios from " + testScript + ".");
        } finally {
            if (DEF_OPEN_EXCEL_AS_DUP && excel != null) { FileUtils.deleteQuietly(excel.getFile().getParentFile()); }

            try {
                // (2018/12/16,automike): memory consumption precaution
                if (excel != null) { excel.close(); }
            } catch (IOException e) {
                ConsoleUtils.error("Unable to close Excel file (" + testScript + "): " + e.getMessage());
            }
        }

        return scenarios;
    }

    protected Excel deriveDataFileFromPlan(XSSFRow row, TestProject project, File testPlan, File testScript) {
        String dataFilePath = readCellValue(row, COL_IDX_PLAN_TEST_DATA);

        if (StringUtils.isBlank(dataFilePath)) {
            // since there's no data file specified, we'll assume standard path/file convention
            String testScriptPath = testScript.getAbsolutePath();
            String dataPath1 = StringUtils.substringBefore(testScriptPath, DEF_REL_LOC_ARTIFACT) +
                               DEF_REL_LOC_TEST_DATA;
            String dataFilePath1 = dataPath1 +
                                   StringUtils.substringBeforeLast(
                                       StringUtils.substringAfter(testScriptPath, DEF_REL_LOC_TEST_SCRIPT), ".") +
                                   DEF_DATAFILE_SUFFIX;
            if (!FileUtil.isFileReadable(dataFilePath1, MIN_EXCEL_FILE_SIZE)) {
                // else, don't know what to do...
                fail("Invalid/unreadable data file specified in ROW " + (row.getRowNum() + 1) + " of " + testPlan +
                     ". Data file not specified and cannot be resolved by standard convention (" + dataFilePath1 + ")");
                return null;
            }

            dataFilePath = dataFilePath1;
        } else {
            dataFilePath = StringUtils.appendIfMissing(dataFilePath, SCRIPT_FILE_EXT);

            // dataFile is specified as a fully qualified path
            if (!FileUtil.isFileReadable(dataFilePath)) {
                // first, check if data file exists in the similar rel. position as specified in script
                String dataPath = project.getDataPath();
                String scriptRelPath =
                    StringUtils.substringBeforeLast(
                        StringUtils.replace(readCellValue(row, COL_IDX_PLAN_TEST_SCRIPT), "\\", "/"), "/");
                String dataFilePath1 = StringUtils.appendIfMissing(dataPath, separator) +
                                       StringUtils.appendIfMissing(scriptRelPath, separator) +
                                       dataFilePath;
                if (!FileUtil.isFileReadable(dataFilePath1, MIN_EXCEL_FILE_SIZE)) {
                    // next, check again standard data directory
                    dataFilePath1 = StringUtils.appendIfMissing(dataPath, separator) + dataFilePath;
                    if (!FileUtil.isFileReadable(dataFilePath1, MIN_EXCEL_FILE_SIZE)) {
                        // next, maybe it's relative to the plan
                        dataFilePath1 = project.getPlanPath() + dataFilePath;
                        if (!FileUtil.isFileReadable(dataFilePath1, MIN_EXCEL_FILE_SIZE)) {
                            // next, maybe it's relative to `artifact`
                            dataFilePath1 = project.getArtifactPath() + dataFilePath;
                            if (!FileUtil.isFileReadable(dataFilePath1, MIN_EXCEL_FILE_SIZE)) {
                                fail("Invalid/unreadable data file specified in ROW " + (row.getRowNum() + 1) +
                                     " of " + testPlan + ". The specified data file (" + dataFilePath + ") cannot be " +
                                     "resolved to a valid data file.");
                                return null;
                            }
                        }
                    }
                }

                dataFilePath = dataFilePath1;
            }
        }

        ConsoleUtils.log("validating data file as " + dataFilePath);
        Excel dataFile = InputFileUtils.asDataFile(dataFilePath);
        if (dataFile != null) { return dataFile; }

        fail("Invalid/unreadable data file specified in ROW " + (row.getRowNum() + 1) + " of " + testPlan + ".");
        return null;
    }

    protected List<String> deriveDataSheetsFromPlan(XSSFRow row, List<String> scenarios) {
        String planDataSheets = readCellValue(row, COL_IDX_PLAN_DATA_SHEETS);
        return StringUtils.isBlank(planDataSheets) ? scenarios : TextUtils.toList(planDataSheets, ",", true);
    }

    protected List<ExecutionDefinition> parseScriptExecution(CommandLine cmd) throws IOException {
        // command line option - script
        String testScriptPath = cmd.getOptionValue(SCRIPT);
        Excel script = InputFileUtils.resolveValidScript(testScriptPath);
        if (script == null) { fail("Invalid test script - " + testScriptPath); }

        // resolve the standard project structure based on test script input
        File testScriptFile = new File(testScriptPath);

        // resolve project directory structure based on the {@code testScriptFile} command line input
        project = TestProject.newInstance(testScriptFile);
        if (!project.isStandardStructure()) {
            ConsoleUtils.log("specified test script (" + testScriptFile + ") not following standard project " +
                             "structure, related directories would not be resolved from commandline arguments.");
        }

        String artifactPath = project.isStandardStructure() ?
                              StringUtils.appendIfMissing(project.getArtifactPath(), separator) : null;

        // command line option - scenario
        List<String> targetScenarios = resolveScenarios(cmd, script);

        // command line option - data. could be fully qualified or relative to script
        Excel dataFile = resolveDataFile(cmd, artifactPath, testScriptPath);
        if (dataFile == null) {
            String error = "Unable to successfully resolve appropriate data file";
            ConsoleUtils.error(error);
            throw new RuntimeException(error);
        }

        File dataFilePath = dataFile.getOriginalFile();

        // (2018/12/16,automike): memory consumption precaution
        try {
            dataFile.close();
        } catch (IOException e) {
            ConsoleUtils.error("Unable to close data file (" + dataFilePath + "): " + e.getMessage());
        } finally {
            dataFile = null;
        }

        // command line option - data sheets
        List<String> dataSheets = resolveDataSheets(cmd, targetScenarios);

        deriveOutputDirectory(cmd, project);

        // create new definition instance, based on various input and derived values
        ExecutionDefinition exec = new ExecutionDefinition();
        exec.setDescription("Started on " + DateUtility.getCurrentDateTime() +
                            " from " + EnvUtils.getHostName() + " via user " + USER_NAME);
        exec.setTestScript(testScriptPath);
        exec.setScenarios(targetScenarios);
        exec.setDataFile(dataFilePath);
        exec.setDataSheets(dataSheets);
        exec.setProject(project);
        exec.parse();

        List<ExecutionDefinition> executions = new ArrayList<>();
        executions.add(exec);
        return executions;
    }

    protected void deriveOutputDirectory(CommandLine cmd, TestProject project) throws IOException {
        // this method is designed to be called after standard/conventional directories have been set to `project`
        // hence these are overrides

        // override default via command line option - output
        String outputPath = cmd.hasOption(OUTPUT) ? cmd.getOptionValue(OUTPUT) : null;
        if (StringUtils.isNotBlank(outputPath)) { project.setOutPath(outputPath); }

        // override via System properties (`-Dnexial.defaultOutBase=...`)
        String outPath = project.getOutPath();
        if (StringUtils.isBlank(outPath)) {
            // one last try: consider environment setup (NEXIAL_OUTPUT), if defined
            // nexial.cmd|sh converts NEXIAL_OUTPUT to -Dnexial.defaultOutBase
            if (StringUtils.isNotBlank(System.getProperty(OPT_DEF_OUT_DIR))) {
                outPath = System.getProperty(OPT_DEF_OUT_DIR);
                project.setOutPath(outPath);
            } else {
                fail("output location cannot be resolved.");
            }
        }

        if (FileUtil.isFileReadable(outPath)) {
            fail("output location (" + outPath + ") cannot be accessed as a directory.");
        }

        if (!FileUtil.isDirectoryReadable(outPath)) { FileUtils.forceMkdir(new File(outPath)); }

        // run id not yet determined... `outPath` does not contain run id in path
        System.setProperty(TEST_LOG_PATH, outPath);
        if (StringUtils.isBlank(System.getProperty(THIRD_PARTY_LOG_PATH))) {
            System.setProperty(THIRD_PARTY_LOG_PATH, outPath);
        }
    }

    protected void interact() {
        // there should only be 1 execution (ie script) since we've checked this earlier
        if (CollectionUtils.isEmpty(executions)) {
            throw new IllegalArgumentException("Interactive Mode requires 1 script to be specified via -script");
        }

        initSpringContext();

        NexialInteractive interactive = new NexialInteractive();
        interactive.setExecutionDefinition(executions.get(0));
        interactive.startSession();
    }

    protected ExecutionSummary execute() {
        initSpringContext();

        // start of test suite (one per test plan in execution)
        String runId = ExecUtils.deriveRunId();

        ExecutionSummary summary = new ExecutionSummary();
        summary.setName(runId);
        summary.setExecutionLevel(EXECUTION);
        summary.setStartTime(System.currentTimeMillis());

        List<ExecutionThread> executionThreads = new ArrayList<>();
        Map<String, Object> intraExecution = null;

        int lastUse = executions.size() - 1;
        try {
            for (int i = 0; i < executions.size(); i++) {
                ExecutionDefinition exec = executions.get(i);
                if (BooleanUtils.toBoolean(System.getProperty(LAST_PLAN_STEP, getDefault(LAST_PLAN_STEP)))) { break; }

                exec.setRunId(runId);

                String msgPrefix = "[" + exec.getTestScript() + "] ";
                ConsoleUtils.log(runId, msgPrefix + "resolve RUN ID as " + runId);

                ExecutionThread launcherThread = ExecutionThread.newInstance(exec);
                if (i == 0) { launcherThread.setFirstScript(true); }
                if (i == lastUse) { launcherThread.setLastScript(true); }
                if (MapUtils.isNotEmpty(intraExecution)) { launcherThread.setIntraExecutionData(intraExecution); }

                ConsoleUtils.log(runId, msgPrefix + "new thread started");
                launcherThread.start();

                if (exec.isSerialMode()) {
                    while (true) {
                        if (launcherThread.isAlive()) {
                            debugThread(runId, msgPrefix + "awaits execution thread to complete...");
                            Thread.sleep(LAUNCHER_THREAD_COMPLETION_WAIT_MS);
                        } else {
                            ConsoleUtils.log(runId, msgPrefix + "now completed");
                            // pass the post-execution state of data to the next execution
                            intraExecution = launcherThread.getIntraExecutionData();
                            summary.addNestSummary(launcherThread.getExecutionSummary());
                            launcherThread = null;
                            break;
                        }
                    }

                    executions.set(i, null);
                    exec = null;
                } else {
                    executionThreads.add(launcherThread);
                    ConsoleUtils.log(runId, msgPrefix + "in progress, proceed to next execution");
                }
            }

            while (true) {
                final boolean[] stillRunning = {false};
                for (int i = 0; i < executionThreads.size(); i++) {
                    ExecutionThread t = executionThreads.get(i);
                    if (t != null) {
                        if (t.isAlive()) {
                            stillRunning[0] = true;
                        } else {
                            summary.addNestSummary(t.getExecutionSummary());
                            // relinquish reference to completed/dead threads
                            executionThreads.set(i, null);
                        }
                    }
                }

                if (!stillRunning[0]) { break; }

                debugThread(runId, "waiting for execution thread(s) to complete...");
                Thread.yield();
                Thread.sleep(LAUNCHER_THREAD_COMPLETION_WAIT_MS);
            }

            ConsoleUtils.log(runId, "all execution thread(s) have terminated");
        } catch (Throwable e) {
            ConsoleUtils.error(e.getMessage());
            e.printStackTrace();
            summary.setError(e);
        } finally {
            onExecutionComplete(runId, summary);
        }

        return summary;
    }

    protected static void updateLogLocation(NexialS3Helper otc, ExecutionSummary summary) {
        // push the latest logs to cloud...
        if (otc == null || !otc.isReadyForUse()) {
            // forget it...
            ConsoleUtils.error(toCloudIntegrationNotReadyMessage("logs"));
            return;
        }

        Map<String, String> logs = new HashMap<>();

        uploadLogLocations(otc, summary, logs);

        if (CollectionUtils.isNotEmpty(summary.getNestedExecutions())) {
            summary.getNestedExecutions().forEach(execution -> uploadLogLocations(otc, execution, logs));
        }
    }

    protected static void uploadLogLocations(NexialS3Helper otc,
                                             ExecutionSummary summary,
                                             Map<String, String> cachedLogLocation) {

        String logName1 = uploadLogToCloud(otc, summary.getExecutionLog(), cachedLogLocation);
        if (StringUtils.isNotBlank(logName1) && StringUtils.isNotBlank(cachedLogLocation.get(logName1))) {
            summary.updateExecutionLogLocation(cachedLogLocation.get(logName1));
        }

        if (MapUtils.isNotEmpty(summary.getLogs())) {
            summary.getLogs().forEach((logName, logFile) -> {
                logFile = uploadLogToCloud(otc, logFile, cachedLogLocation);
                if (StringUtils.isNotBlank(logFile) && StringUtils.isNotBlank(cachedLogLocation.get(logName))) {
                    summary.getLogs().put(logName, cachedLogLocation.get(logName));
                }
            });
        }
    }

    protected static String uploadLogToCloud(NexialS3Helper otc, String logFile, Map<String, String> logs) {
        if (StringUtils.isBlank(logFile)) { return ""; }

        String logName = StringUtils.substringAfterLast(StringUtils.replace(logFile, "\\", "/"), "/");

        if (StringUtils.startsWith(logFile, "http") && !logs.containsKey(logName)) {
            logs.put(logName, logFile);
            return logName;
        }

        if (StringUtils.isBlank(logs.get(logName)) && FileUtil.isFileReadable(logFile, 1024)) {
            try {
                logs.put(logName, otc.importLog(new File(logFile), false));
                return logName;
            } catch (IOException e) {
                ConsoleUtils.error(toCloudIntegrationNotReadyMessage(logFile) + ": " + e.getMessage());
            }
        }

        return logName;
    }

    /**
     * this represents the end of an entire Nexial run, including all iterations and plan steps.
     */
    protected void onExecutionComplete(String runId, ExecutionSummary summary) {
        // end of test suite (one per test plan in execution)
        long startTimeMs = NumberUtils.toLong(System.getProperty(TEST_START_TS));
        long stopTimeMs = System.currentTimeMillis();
        long testSuiteElapsedTimeMs = stopTimeMs - startTimeMs;
        ConsoleUtils.log(runId, "test run completed in about " + (testSuiteElapsedTimeMs / 1000) + " seconds");

        summary.setEndTime(stopTimeMs);
        summary.aggregatedNestedExecutions(null);
        summary.setCustomHeader(System.getProperty(SUMMARY_CUSTOM_HEADER));
        summary.setCustomFooter(System.getProperty(SUMMARY_CUSTOM_FOOTER));

        if (MapUtils.isEmpty(summary.getLogs()) && CollectionUtils.isNotEmpty(summary.getNestedExecutions())) {
            summary.getLogs().putAll(summary.getNestedExecutions().get(0).getLogs());
        }

        String reportPath = StringUtils.appendIfMissing(System.getProperty(OPT_OUT_DIR, project.getOutPath()),
                                                        separator);
        if (!StringUtils.contains(reportPath, runId)) { reportPath += runId + separator; }

        springContext = new ClassPathXmlApplicationContext(SPRING_CONTEXT);
        ExecutionReporter reporter = springContext.getBean("executionResultHelper", ExecutionReporter.class);
        reporter.setReportPath(reportPath);

        NexialS3Helper otc = springContext.getBean("otc", NexialS3Helper.class);

        boolean outputToCloud = isOutputToCloud();
        if (outputToCloud) {
            // update log file path in execution summary BEFORE rendering any of the reports
            if (otc == null || !otc.isReadyForUse()) {
                // forget it...
                ConsoleUtils.error(toCloudIntegrationNotReadyMessage("logs"));
            } else {
                updateLogLocation(otc, summary);
            }
        }

        File htmlReport = null;
        try {
            htmlReport = reporter.generateHtml(summary);
            if (htmlReport != null) { System.setProperty(EXEC_OUTPUT_PATH, htmlReport.getAbsolutePath()); }
        } catch (IOException e) {
            ConsoleUtils.error(runId, "Unable to generate HTML report for this execution: " + e.getMessage());
        }

        File junitReport = null;
        try {
            junitReport = reporter.generateJUnitXml(summary);
            ConsoleUtils.log("generated JUnit report for this execution: " + junitReport);
            if (junitReport != null) { System.setProperty(JUNIT_XML_LOCATION, junitReport.getAbsolutePath()); }
        } catch (IOException e) {
            ConsoleUtils.error(runId, "Unable to generate JUnit report for this execution: " + e.getMessage());
        }

        List<File> generatedJsons = null;
        if (isGenerateExecReport()) {
            try {
                generatedJsons = reporter.generateJson(summary);
            } catch (IOException e) {
                ConsoleUtils.error(runId, "Unable to save execution summary due to " + e.getMessage(), e);
            }
        }

        if (outputToCloud) {
            // need to make sure nexial setup run (possibly again)...
            ConsoleUtils.log("resolving Nexial Cloud Integration...");

            try {
                if (otc == null || !otc.isReadyForUse()) {
                    // forget it...
                    throw new IOException(springContext.getBean("otcNotReadyMessage", String.class));
                }

                // can't use otc.resolveOutputDir() since we are out of context at this point in time
                // project.name could be affected by project.id, as designed
                String outputDir = NexialS3Helper.resolveOutputDir(project.getName(), runId);

                // upload HTML report to cloud
                if (FileUtil.isFileReadable(htmlReport, 1024)) {
                    String url = otc.importToS3(htmlReport, outputDir, true);
                    ConsoleUtils.log("HTML output for this execution exported to " + url);
                    System.setProperty(EXEC_OUTPUT_PATH, url);
                    if (StringUtils.isNotBlank(url)) { ExecutionReporter.openExecutionSummaryReport(url); }
                }

                // upload JSON reports
                if (CollectionUtils.isNotEmpty(generatedJsons)) {
                    for (File file : generatedJsons) { otc.importToS3(file, outputDir, true); }
                }

                // upload junit xml
                if (FileUtil.isFileReadable(junitReport, 100)) {
                    String url = otc.importToS3(junitReport, outputDir, true);
                    ConsoleUtils.log("JUnit XML output for this execution exported to " + url);
                    System.setProperty(JUNIT_XML_LOCATION, url);
                }
            } catch (IOException e) {
                ConsoleUtils.error(toCloudIntegrationNotReadyMessage("execution output") + ": " + e.getMessage());
            }
        } else {
            ExecutionReporter.openExecutionSummaryReport(htmlReport);
        }

        ExecutionMailConfig mailConfig = ExecutionMailConfig.get();
        if (mailConfig != null && mailConfig.isReady()) {
            notifyCompletion(summary);
        } else {
            ConsoleUtils.log("skipped email notification as configured");
        }

        NexialListenerFactory.fireEvent(NexialExecutionEvent.newExecutionEndEvent(runId, summary));
    }

    protected void initSpringContext() {
        if (springContext == null || !springContext.isActive()) {
            springContext = new ClassPathXmlApplicationContext(SPRING_CONTEXT);
        }
    }

    /**
     * todo: not used?
     * resolve data file and standard "data" directory based on either data-related commandline input or
     * {@code testScriptFile} commandline input.
     */
    protected Excel resolveDataFile(TestProject project, CommandLine cmd, File testScriptFile) {
        String artifactPath = null;
        if (project.isStandardStructure()) { artifactPath = project.getArtifactPath(); }

        String dataFile;
        if (cmd.hasOption(DATA)) {
            // could be fully qualified or relative to script
            dataFile = cmd.getOptionValue(DATA);
            if (!FileUtil.isFileReadable(dataFile)) {

                if (testScriptFile == null) {
                    fail("data file (" + cmd.getOptionValue(DATA) + ") cannot be resolved; test script not specified.");
                }

                // try again by relative path
                dataFile = artifactPath + dataFile;
                if (!FileUtil.isFileReadable(dataFile)) {
                    fail("data file (" + cmd.getOptionValue(DATA) + ") is not readable via absolute or relative path.");
                }
            } // else dataFile is specified as a fully qualified path
        } else {
            if (testScriptFile == null) {
                fail("data file cannot be resolved since test script is not specified.");
                return null;
            }

            dataFile = appendData(artifactPath) + separator +
                       (StringUtils.substringBeforeLast(testScriptFile.getName(), ".") + DEF_DATAFILE_SUFFIX);
        }

        return validateDataFile(project, dataFile);
    }

    protected List<String> resolveDataSheets(CommandLine cmd, List<String> targetScenarios) {
        List<String> dataSheets = new ArrayList<>();
        if (cmd.hasOption(DATASHEETS)) {
            List<String> dataSets = TextUtils.toList(cmd.getOptionValue(DATASHEETS), ",", true);
            if (CollectionUtils.isEmpty(dataSets)) {
                fail("Unable to derive any valid data sheet to use.");
            } else {
                dataSheets.addAll(dataSets);
            }
        }

        // data sheet names are the same as scenario if none is specifically specified
        if (CollectionUtils.isEmpty(dataSheets)) { dataSheets = targetScenarios; }
        return dataSheets;
    }

    protected static void fail(String message) {
        throw new IllegalArgumentException(message +
                                           " Possibly the required argument is missing or invalid." +
                                           " Check usage details.");
    }

    @SuppressWarnings("PMD.SystemPrintln")
    protected static void usage() {
        System.out.println();
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp(Nexial.class.getName(), OPTIONS, true);
        System.out.println();
    }

    protected static boolean mustTerminateForcefully() { return ShutdownAdvisor.mustForcefullyTerminate(); }

    @Nullable
    private Excel resolveDataFile(CommandLine cmd, String artifactPath, String testScriptPath) {
        return resolveDataFile(artifactPath, testScriptPath, cmd.hasOption(DATA) ? cmd.getOptionValue(DATA) : null);
    }

    private Excel resolveDataFile(String artifactPath, String testScriptPath, String dataFilePath) {
        if (StringUtils.isBlank(dataFilePath)) {
            // since there's no data file specified, we'll assume standard path/file convention
            dataFilePath = (StringUtils.substringBefore(testScriptPath, DEF_REL_LOC_ARTIFACT) + DEF_REL_LOC_TEST_DATA) +
                           StringUtils.substringBeforeLast(
                               StringUtils.substringAfter(testScriptPath, DEF_REL_LOC_TEST_SCRIPT), ".") +
                           DEF_DATAFILE_SUFFIX;
            return validateDataFile(project, dataFilePath);
        }

        dataFilePath = StringUtils.appendIfMissing(dataFilePath, SCRIPT_FILE_EXT);

        // dataFile is specified as a fully qualified path
        if (FileUtil.isFileReadable(dataFilePath)) { return validateDataFile(project, dataFilePath); }

        // could be fully qualified or relative to script.

        // let's resolve from closest point to script, then expand out
        String dataFile1 = StringUtils.appendIfMissing(new File(testScriptPath).getParent(), separator) + dataFilePath;
        if (FileUtil.isFileReadable(dataFile1)) { return validateDataFile(project, dataFile1); }

        // for path resolution, we'll try based on artifact location and script path
        if (StringUtils.isNotBlank(artifactPath)) {
            artifactPath = StringUtils.appendIfMissing(artifactPath, separator);

            // first try with `artifact/data`
            dataFile1 = artifactPath + DEF_LOC_TEST_DATA + separator + dataFilePath;
            if (FileUtil.isFileReadable(dataFile1)) { return validateDataFile(project, dataFile1); }

            // next, try with just `artifactPath`
            dataFile1 = artifactPath + dataFilePath;
            if (FileUtil.isFileReadable(dataFile1)) { return validateDataFile(project, dataFile1); }
        }

        // can't find it.. failed!
        fail("data file (" + dataFilePath + ") is not readable via absolute or relative path. Relative path is " +
             "based on either the specified script or the resolved artifact directory.");
        return null;
    }

    @NotNull
    private Excel validateDataFile(TestProject project, String dataFile) {
        Excel dataFileExcel = InputFileUtils.asDataFile(dataFile);

        if (dataFileExcel == null) {
            fail("data file (" + dataFile + ") does not contain valid data file format.");
            return null;
        }

        File file = dataFileExcel.getFile();
        if (!project.isStandardStructure()) { project.setDataPath(file.getParentFile().getAbsolutePath()); }
        ConsoleUtils.log("data file resolved as " + file.getAbsolutePath());

        return dataFileExcel;
    }

    @NotNull
    private List<String> resolveScenarios(CommandLine cmd, Excel script) {
        List<String> targetScenarios = new ArrayList<>();
        if (cmd.hasOption(SCENARIO)) {
            List<String> scenarios = TextUtils.toList(cmd.getOptionValue(SCENARIO), ",", true);
            if (CollectionUtils.isEmpty(scenarios)) {
                fail("Unable to derive any valid test script to run.");
            } else {
                targetScenarios.addAll(scenarios);
            }
        }

        // resolve scenario
        if (CollectionUtils.isEmpty(targetScenarios)) {
            targetScenarios.addAll(InputFileUtils.retrieveValidTestScenarioNames(script));
            if (targetScenarios.isEmpty()) { fail("No scenarios found in " + script.getFile().getAbsolutePath()); }
        }

        return targetScenarios;
    }

    private int beforeShutdown(ExecutionSummary summary) {
        // need to kill JVM forcefully if awt was used during runtime
        // -- haven't found a way to do this more gracefully yet...
        if (ShutdownAdvisor.mustForcefullyTerminate()) { ShutdownAdvisor.forcefullyTerminate(); }

        if (isIntegrationMode() || isReadyMode() || isInteractiveMode()) { return RC_NORMAL; }

        // only for normal execution mode
        int exitStatus;
        if (summary == null) {
            ConsoleUtils.error("Unable to cleanly execute tests; execution summary missing!");
            exitStatus = RC_EXECUTION_SUMMARY_MISSING;
        } else {
            int defaultSuccessRate = getDefaultInt(MIN_EXEC_SUCCESS_RATE);
            double minExecSuccessRate =
                NumberUtils.toDouble(System.getProperty(MIN_EXEC_SUCCESS_RATE, defaultSuccessRate + ""));
            if (minExecSuccessRate < 0 || minExecSuccessRate > 100) { minExecSuccessRate = defaultSuccessRate; }
            minExecSuccessRate = minExecSuccessRate / 100;
            String minSuccessRateString = MessageFormat.format(RATE_FORMAT, minExecSuccessRate);

            double successRate = summary.getSuccessRate();
            String successRateString = MessageFormat.format(RATE_FORMAT, successRate);
            System.setProperty(SUCCESS_RATE, successRateString);

            String manifest = NEXIAL_MANIFEST;
            ConsoleUtils.log(
                NL + NL +
                "/-END OF EXECUTION--------------------------------------------------------------" + NL +
                "| » Execution Time: " + (summary.getElapsedTime() / 1000) + " sec." + NL +
                "| » Test Steps....: " + summary.getExecuted() + NL +
                "| » Passed........: " + summary.getPassCount() + NL +
                "| » Failed........: " + summary.getFailCount() + NL +
                "| » Success Rate..: " + successRateString + NL +
                "\\" + StringUtils.repeat("-", 80 - 1 - manifest.length() - 1) + manifest + "-" + NL);

            if (successRate != 1) {
                if (successRate >= minExecSuccessRate) {
                    ConsoleUtils.log("PASSED - success rate greater than specified minimum success rate " +
                                     "(" + successRateString + " >= " + minSuccessRateString + ")");
                    exitStatus = 0;
                } else {
                    ConsoleUtils.error("failure found; success rate is " + successRateString);
                    exitStatus = RC_NOT_PERFECT_SUCCESS_RATE;
                }
            } else {
                int failCount = summary.getFailCount();
                if (failCount > 0) {
                    ConsoleUtils.error(failCount + " failure(s) found, although success rate is 100%");
                    exitStatus = RC_FAILURE_FOUND;
                } else {
                    int warnCount = summary.getWarnCount();
                    if (warnCount > 0) {
                        ConsoleUtils.error(warnCount + " warning(s) found, although success rate is 100%");
                        exitStatus = RC_WARNING_FOUND;
                    } else {
                        ConsoleUtils.log("ALL PASSED!");
                        exitStatus = RC_NORMAL;
                    }
                }
            }
        }

        NexialListenerFactory.fireEvent(NexialExecutionEvent.newNexialEndEvent(this, ExecUtils.deriveRunId(), summary));

        // not used at this time
        // File eventPath = new File(EventTracker.INSTANCE.getStorageLocation());
        // if (FileUtil.isDirectoryReadable(eventPath)) {
        //     String[] ext = new String[]{StringUtils.removeStart(EventTracker.INSTANCE.getExtension(), ".")};
        //     Collection<File> eventFiles = FileUtils.listFiles(eventPath, ext, false);
        //     long sleepTime = 500;
        //     while (CollectionUtils.isNotEmpty(eventFiles)) {
        //         // don't sleep too long... 5 sec tops
        //         if (sleepTime > 5000) { break; }
        //
        //         // sleep/wait
        //         try { Thread.sleep(sleepTime);} catch (InterruptedException e) { }
        //
        //         // next sleep time will be doubled
        //         sleepTime += sleepTime;
        //
        //         // check for event files again...
        //         eventFiles = FileUtils.listFiles(eventPath, ext, false);
        //     }
        // }

        beforeShutdownMemUsage();

        System.setProperty(EXIT_STATUS, exitStatus + "");
        ConsoleUtils.log("End of Execution:" + NL +
                         "NEXIAL_OUTPUT:         " + System.getProperty(OUTPUT_LOCATION) + NL +
                         "NEXIAL_EXECUTION_HTML: " + System.getProperty(EXEC_OUTPUT_PATH) + NL +
                         "NEXIAL_JUNIT_XML:      " + System.getProperty(JUNIT_XML_LOCATION) + NL +
                         "NEXIAL_SUCCESS_RATE:   " + System.getProperty(SUCCESS_RATE) + NL +
                         "NEXIAL_EXIT_STATUS:    " + exitStatus);

        postExecBatch(exitStatus);

        return exitStatus;
    }

    private void postExecBatch(int exitStatus) {
        String postExecScript = System.getProperty(POST_EXEC_SCRIPT);
        if (StringUtils.isNotBlank(postExecScript)) {
            Map<String, String> postExecVars =
                TextUtils.toMap("=",
                                "${NEXIAL_OUTPUT}=" + System.getProperty(OUTPUT_LOCATION),
                                "${NEXIAL_EXECUTION_HTML}=" + System.getProperty(EXEC_OUTPUT_PATH),
                                "${NEXIAL_JUNIT_XML}=" + System.getProperty(JUNIT_XML_LOCATION),
                                "${NEXIAL_SUCCESS_RATE}=" + System.getProperty(SUCCESS_RATE),
                                "${NEXIAL_EXIT_STATUS}=" + exitStatus);

            try {
                String batchTemplate =
                    ResourceUtils.loadResource("org/nexial/core/postExecution." + (IS_OS_WINDOWS ? "cmd" : "sh"));
                FileUtils.writeStringToFile(new File(postExecScript),
                                            TextUtils.replaceStrings(batchTemplate, postExecVars),
                                            DEF_FILE_ENCODING);
                ConsoleUtils.log("Nexial post-execution variables (above) are captured in:" +
                                 lineSeparator() + postExecScript);
            } catch (IOException e) {
                ConsoleUtils.error("FAILED to generate post-execution shell script: " + e.getMessage());
            }
        }
    }

    private static void beforeShutdownMemUsage() {
        MemManager.gc((Object) Nexial.class);
        MemManager.recordMemoryChanges("just before exit");
        String memUsage = MemManager.showUsage("| »      ");
        if (StringUtils.isNotBlank(memUsage)) {
            ConsoleUtils.log(NL +
                             "/-MEMORY-USAGE------------------------------------------------------------------" + NL +
                             memUsage +
                             "\\-------------------------------------------------------------------------------");
        }
    }

    private static String readCellValue(XSSFRow row, int columnIndex) {
        if (row == null) { return ""; }
        return StringUtils.trim(Excel.getCellValue(row.getCell(columnIndex)));
    }

    private void notifyCompletion(ExecutionSummary summary) {
        try {
            ExecutionNotifier nexialMailer = springContext.getBean("nexialMailer", ExecutionNotifier.class);
            nexialMailer.notify(summary);
        } catch (IntegrationConfigException | IOException e) {
            ConsoleUtils.error("Unable to send out notification email: " + e.getMessage());
        }
    }

    /**
     * log {@code msg} to console if the System property {@code nexial.devLogging} is {@code "true"}.
     */
    private void debugThread(String runId, String msg) {
        boolean debugOn = BooleanUtils.toBoolean(System.getProperty(OPT_DEVMODE_LOGGING, "false"));
        if (!debugOn) { return; }

        if (threadWaitCounter < THREAD_WAIT_LOG_INTERVAL) {
            threadWaitCounter++;
        } else if (threadWaitCounter == 60) {
            ConsoleUtils.log(runId, msg);
            threadWaitCounter++;
        } else {
            threadWaitCounter = 0;
        }
    }
}
