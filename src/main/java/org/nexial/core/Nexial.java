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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.nexial.commons.utils.DateUtility;
import org.nexial.commons.utils.EnvUtils;
import org.nexial.commons.utils.FileUtil;
import org.nexial.commons.utils.RegexUtils;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.aws.NexialS3Helper;
import org.nexial.core.excel.Excel;
import org.nexial.core.excel.Excel.Worksheet;
import org.nexial.core.integration.IntegrationManager;
import org.nexial.core.model.*;
import org.nexial.core.reports.ExecutionMailConfig;
import org.nexial.core.reports.ExecutionNotifier;
import org.nexial.core.reports.NexialMailer;
import org.nexial.core.service.EventTracker;
import org.nexial.core.service.ServiceLauncher;
import org.nexial.core.utils.ConsoleUtils;
import org.nexial.core.utils.ExecUtil;
import org.nexial.core.utils.InputFileUtils;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static java.io.File.separator;
import static org.apache.commons.lang3.SystemUtils.USER_NAME;
import static org.nexial.core.NexialConst.CLI.*;
import static org.nexial.core.NexialConst.*;
import static org.nexial.core.NexialConst.Data.*;
import static org.nexial.core.NexialConst.ExitStatus.*;
import static org.nexial.core.NexialConst.Project.*;
import static org.nexial.core.excel.Excel.MIN_EXCEL_FILE_SIZE;
import static org.nexial.core.excel.ExcelConfig.*;
import static org.nexial.core.model.ExecutionSummary.ExecutionLevel.EXECUTION;

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
    private int listenPort = -1;
    private String listenerHandshake;
    private boolean integrationMode;

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
                // like from fail()
                ConsoleUtils.errorBeforeTerminate(e.getMessage());
            } else {
                e.printStackTrace();
                System.err.println(e.getMessage());
            }

            if (main != null) { main.trackEvent(new NexialCmdErrorEvent(Arrays.asList(args), e.getMessage())); }

            usage();
            System.exit(-1);
        }

        if (main.isIntegrationMode()) { return; }

        if (main.isListenMode()) {
            try {
                main.listen();
                ConsoleUtils.log("Nexial Services ready...");
            } catch (Throwable e) {
                e.printStackTrace();
            }
        } else {
            ExecutionSummary summary = null;
            try {
                MemManager.recordMemoryChanges("before execution");
                summary = main.execute();
                main.trackEvent(new NexialExecutionCompleteEvent(summary));
                MemManager.recordMemoryChanges("after execution");
            } catch (Throwable e) {
                ConsoleUtils.error("Unknown/unexpected error occurred: " + e.getMessage());
                e.printStackTrace();
            }

            ConsoleUtils.log("Exiting Nexial...");
            System.exit(beforeShutdown(summary));
        }
    }

    protected void listen() throws Exception { ServiceLauncher.main(new String[]{}); }

    protected boolean isListenMode() { return listenPort > 0 && StringUtils.isNotBlank(listenerHandshake); }

    protected boolean isIntegrationMode() { return integrationMode; }

    protected Options addMsaOptions(Options options) {
        Options optionsAdded = new Options();
        options.getOptions().forEach(optionsAdded::addOption);
        optionsAdded.addOption("listen", true, "start Nexial in listen mode on the specified port");
        optionsAdded.addOption("listenCode", true, "establish listener/receiver handshake");
        optionsAdded.addOption(ANNOUNCE, true, "the output directory path to announce the automation " +
                                               "report over collaboration tools");
        return optionsAdded;
    }

    /** read from the commandline and derive the intended execution order. */
    protected void init(String[] args) throws IOException, ParseException {
        ExecUtil.collectCliProps(args);

        // first things first -- do we have all the required system properties?
        String errPrefix = "System property " + NEXIAL_HOME;
        String nexialHome = System.getProperty(NEXIAL_HOME);
        if (StringUtils.isBlank(nexialHome)) { throw new RuntimeException(errPrefix + " missing; unable to proceed"); }
        if (!FileUtil.isDirectoryReadable(nexialHome)) {
            throw new RuntimeException(errPrefix + " does not refer to a valid directory (" + nexialHome + "); " +
                                       "unable to proceed");
        }

        ConsoleUtils.log(ExecUtil.deriveJarManifest() + " starting up...");

        CommandLine cmd = new DefaultParser().parse(addMsaOptions(OPTIONS), args);

        // msa treatment
        if (cmd.hasOption("listen") && cmd.hasOption("listenCode")) {
            listenPort = NumberUtils.toInt(cmd.getOptionValue("listen"));
            listenerHandshake = cmd.getOptionValue("listenCode");
            return;
        }

        if (cmd.hasOption(OUTPUT)) {
            // force logs to be pushed into the specified output directory (and not taint other past/concurrent runs)
            String logPath = cmd.getOptionValue(OUTPUT);
            System.setProperty(TEST_LOG_PATH, logPath);
            if (StringUtils.isBlank(System.getProperty(THIRD_PARTY_LOG_PATH))) {
                System.setProperty(THIRD_PARTY_LOG_PATH, logPath);
            }
        }

        // integration mode
        // integrate or announce or assimilate
        if (cmd.hasOption(ANNOUNCE)) {
            integrationMode = true;
            String outputDirPath = cmd.getOptionValue(ANNOUNCE);
            initSpringContext();
            IntegrationManager.Companion.manageIntegration(outputDirPath);
            return;
        }

        // plan or script?
        if (cmd.hasOption(PLAN)) {
            this.executions = parsePlanExecution(cmd);
            System.setProperty(NEXIAL_EXECUTION_TYPE, NEXIAL_EXECUTION_TYPE_PLAN);
        } else {
            if (!cmd.hasOption(SCRIPT)) { fail("test script is required but not specified."); }
            this.executions = parseScriptExecution(cmd);
            System.setProperty(NEXIAL_EXECUTION_TYPE, NEXIAL_EXECUTION_TYPE_SCRIPT);
        }

        // any variable override?
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

        trackExecution(new NexialEnv(cmd));
    }

    protected List<ExecutionDefinition> parsePlanExecution(CommandLine cmd) throws IOException {
        List<String> testPlanPathList = TextUtils.toList(cmd.getOptionValue(PLAN), DEF_TEXT_DELIM, true);
        List<ExecutionDefinition> executions = new ArrayList<>();

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
            Excel excel = new Excel(testPlanFile, DEF_OPEN_EXCEL_AS_DUP);
            List<Worksheet> plans = InputFileUtils.retrieveValidPlanSequence(excel);
            if (CollectionUtils.isEmpty(plans)) { return executions; }

            plans.forEach(testPlan -> {
                int rowStartIndex = ADDR_PLAN_EXECUTION_START.getRowStartIndex();
                int lastExecutionRow = testPlan.findLastDataRow(ADDR_PLAN_EXECUTION_START);

                for (int i = rowStartIndex; i < lastExecutionRow; i++) {
                    XSSFRow row = testPlan.getSheet().getRow(i);

                    File testScript = deriveScriptFromPlan(row, project, testPlanPath);
                    List<String> scenarios = deriveScenarioFromPlan(row, testScript);
                    Excel dataFile = deriveDataFileFromPlan(row, project, testPlanFile, testScript);
                    if (dataFile == null) {
                        fail("Unable to resolve data file for the test plan specified in ROW " +
                             (row.getRowNum() + 1) + " of " + testPlanFile + ".");
                    }
                    List<String> dataSheets = deriveDataSheetsFromPlan(row, scenarios);

                    // create new definition instance, based on various input and derived values
                    ExecutionDefinition exec = new ExecutionDefinition();
                    exec.setPlanFilename(StringUtils.substringBeforeLast(testPlanFile.getName(), "."));
                    exec.setPlanName(testPlan.getName());
                    exec.setPlanSequence((i - rowStartIndex + 1));
                    exec.setDescription(StringUtils.trim(readCellValue(row, COL_IDX_PLAN_DESCRIPTION)));
                    exec.setTestScript(testScript.getAbsolutePath());
                    exec.setScenarios(scenarios);
                    exec.setDataFile(dataFile);
                    exec.setDataSheets(dataSheets);
                    exec.setProject(project);

                    // 2.1 mark option for parallel run and fail fast
                    exec.setFailFast(BooleanUtils.toBoolean(
                        StringUtils.defaultIfBlank(readCellValue(row, COL_IDX_PLAN_FAIL_FAST), DEF_PLAN_FAIL_FAST)));
                    exec.setSerialMode(BooleanUtils.toBoolean(
                        StringUtils.defaultIfBlank(readCellValue(row, COL_IDX_PLAN_WAIT), DEF_PLAN_SERIAL_MODE)));
                    exec.setLoadTestMode(BooleanUtils.toBoolean(readCellValue(row, COL_IDX_PLAN_LOAD_TEST)));
                    if (exec.isLoadTestMode()) { fail("Sorry... load testing mode not yet ready for use."); }

                    // try {
                    // 3. for each row, parse script (and scenario) and data (and datasheet)
                    exec.parse();
                    executions.add(exec);
                    // } catch (IOException e) {
                    //     fail("Unable to parse successfully for the test plan specified in ROW " +
                    //          (row.getRowNum() + 1) + " of " + testPlanFile + ".");
                    // }
                }
            });

            if (DEF_OPEN_EXCEL_AS_DUP) { FileUtils.deleteQuietly(excel.getFile().getParentFile()); }
        }

        // 4. return a list of scripts mixin data
        return executions;
    }

    protected File deriveScriptFromPlan(XSSFRow row, TestProject project, String testPlan) {
        String testScriptPath = readCellValue(row, COL_IDX_PLAN_TEST_SCRIPT);
        if (StringUtils.isBlank(testScriptPath)) {
            fail("Invalid test script specified in ROW " + (row.getRowNum() + 1) + " of " + testPlan + ".");
        }

        testScriptPath = StringUtils.appendIfMissing(testScriptPath, ".xlsx");

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
            dataFilePath = StringUtils.appendIfMissing(dataFilePath, ".xlsx");

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
        if (!InputFileUtils.isValidScript(testScriptPath)) {
            fail("specified test script (" + testScriptPath + ") is not readable or does not contain valid format.");
        }

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
        List<String> targetScenarios = resolveScenarios(cmd, testScriptFile);

        // command line option - data. could be fully qualified or relative to script
        Excel dataFile = resolveDataFile(cmd, artifactPath, testScriptPath);
        if (dataFile == null) {
            String error = "Unable to successfully resolve appropriate data file";
            ConsoleUtils.error(error);
            throw new RuntimeException(error);
        }

        // command line option - data sheets
        List<String> dataSheets = resolveDataSheets(cmd, targetScenarios);

        deriveOutputDirectory(cmd, project);

        // create new definition instance, based on various input and derived values
        ExecutionDefinition exec = new ExecutionDefinition();
        exec.setDescription("Started via commandline inputs on " + DateUtility.getCurrentDateTime() +
                            " from " + EnvUtils.getHostName() + " via user " + USER_NAME);
        exec.setTestScript(testScriptPath);
        exec.setScenarios(targetScenarios);
        exec.setDataFile(dataFile);
        exec.setDataSheets(dataSheets);
        exec.setProject(project);
        exec.parse();

        List<ExecutionDefinition> executions = new ArrayList<>();
        executions.add(exec);
        return executions;
    }

    protected void deriveOutputDirectory(CommandLine cmd, TestProject project) throws IOException {
        // command line option - output
        String outputPath = cmd.hasOption(OUTPUT) ? cmd.getOptionValue(OUTPUT) : null;
        if (StringUtils.isNotBlank(outputPath)) { project.setOutPath(outputPath); }

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

        System.setProperty(TEST_LOG_PATH, outPath);
        if (StringUtils.isBlank(System.getProperty(THIRD_PARTY_LOG_PATH))) {
            System.setProperty(THIRD_PARTY_LOG_PATH, outPath);
        }
    }

    protected ExecutionSummary execute() {
        initSpringContext();

        // start of test suite (one per test plan in execution)
        String runId = ExecUtil.deriveRunId();

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
                if (BooleanUtils.toBoolean(System.getProperty(LAST_PLAN_STEP, DEF_LAST_PLAN_STEP))) { break; }

                exec.setRunId(runId);

                String msgPrefix = "[" + exec.getTestScript() + "] ";
                ConsoleUtils.log(runId, msgPrefix + "resolve RUN ID as " + runId);

                ExecutionThread launcherThread = ExecutionThread.newInstance(exec);
                if (i == 0) { launcherThread.setFirstUse(true); }
                if (i == lastUse) { launcherThread.setLastUse(true); }
                if (MapUtils.isNotEmpty(intraExecution)) { launcherThread.setIntraExecutionData(intraExecution); }
                executionThreads.add(launcherThread);

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
                            executionThreads.remove(launcherThread);
                            summary.addNestSummary(launcherThread.getExecutionSummary());
                            launcherThread = null;
                            break;
                        }
                    }
                } else {
                    ConsoleUtils.log(runId, msgPrefix + "in progress, progressing to next execution");
                }
            }

            while (true) {
                final boolean[] stillRunning = {false};
                executionThreads.forEach(t -> {
                    if (t != null) {
                        if (t.isAlive()) {
                            stillRunning[0] = true;
                        } else {
                            ExecutionSummary executionSummary = t.getExecutionSummary();
                            summary.addNestSummary(executionSummary);
                            t = null;
                        }
                    }
                });

                if (!stillRunning[0]) { break; }

                debugThread(runId, "waiting for execution thread(s) to complete...");
                Thread.yield();
                Thread.sleep(LAUNCHER_THREAD_COMPLETION_WAIT_MS);
            }

            ConsoleUtils.log(runId, "all execution thread(s) have terminated");
        } catch (Throwable e) {
            ConsoleUtils.error(e.toString());
            e.printStackTrace();
            summary.setError(e);
        } finally {
            onExecutionComplete(runId, summary);
        }

        return summary;
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

        summary.setExecutionLevel(EXECUTION);
        summary.setEndTime(stopTimeMs);
        summary.aggregatedNestedExecutions(null);

        boolean outputToCloud = BooleanUtils.toBoolean(System.getProperty(OUTPUT_TO_CLOUD, DEF_OUTPUT_TO_CLOUD + ""));
        boolean generateReport =
            outputToCloud ||
            BooleanUtils.toBoolean(System.getProperty(GENERATE_EXEC_REPORT, DEF_GENERATE_EXEC_REPORT + ""));

        if (generateReport) {
            String reportPath = StringUtils.appendIfMissing(System.getProperty(OPT_OUT_DIR, project.getOutPath()),
                                                            separator);
            if (!StringUtils.contains(reportPath, runId)) { reportPath += runId + separator; }
            String jsonDetailedReport = reportPath + "execution-detail.json";
            String jsonSummaryReport = reportPath + "execution-summary.json";
            // String htmlReport = reportPath + "execution-summary.html";

            try {
                summary.generateDetailedJson(jsonDetailedReport);
                summary.generateSummaryJson(jsonSummaryReport);
                // summary.generateHtmlReport(htmlReport);

                if (outputToCloud) {
                    // need to make sure nexial setup run (possibly again)...
                    ConsoleUtils.log("resolving Nexial Cloud Integration...");
                    springContext = new ClassPathXmlApplicationContext(SPRING_CONTEXT);

                    try {
                        NexialS3Helper otc = springContext.getBean("otc", NexialS3Helper.class);
                        if (otc == null || !otc.isReadyForUse()) {
                            // forget it...
                            String errorMessage = springContext.getBean("otcNotReadyMessage", String.class);
                            throw new IOException(errorMessage);
                        }

                        // can't use otc.resolveOutputDir() since we are out of context at this point in time
                        String outputDir =
                            System.getProperty(OPT_CLOUD_OUTPUT_BASE) + "/" + project.getName() + "/" + runId;

                        otc.importToS3(new File(jsonSummaryReport), outputDir, true);
                        otc.importToS3(new File(jsonDetailedReport), outputDir, true);
                        // otc.importToS3(new File(htmlReport), outputDir, true);

                        // push the latest logs to cloud...
                        String logPath = System.getProperty(TEST_LOG_PATH);
                        Collection<File> logFiles = FileUtils.listFiles(new File(logPath), new String[]{"log"}, false);
                        if (CollectionUtils.isNotEmpty(logFiles)) {
                            for (File log : logFiles) { otc.importLog(log, false); }
                        }

                    } catch (IOException e) {
                        ConsoleUtils.error("Unable to save to cloud storage due to " + e.getMessage());
                    }
                }
            } catch (IOException e) {
                ConsoleUtils.error(runId, "Unable to save execution summary due to " + e.getMessage(), e);
            }
        }

        ExecutionMailConfig mailConfig = ExecutionMailConfig.get();
        if (mailConfig != null && mailConfig.isReady()) {
            notifyCompletion(summary);
        } else {
            ConsoleUtils.log("skipped email notification as configured");
        }
    }

    protected void initSpringContext() {
        if (springContext == null || !springContext.isActive()) {
            springContext = new ClassPathXmlApplicationContext(SPRING_CONTEXT);
        }
    }

    /**
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
        // ConsoleUtils.error("ERROR: " + message);
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
            String dataPath = StringUtils.substringBefore(testScriptPath, DEF_REL_LOC_ARTIFACT) + DEF_REL_LOC_TEST_DATA;
            dataFilePath = dataPath +
                           StringUtils.substringBeforeLast(
                               StringUtils.substringAfter(testScriptPath, DEF_REL_LOC_TEST_SCRIPT),
                               ".") +
                           DEF_DATAFILE_SUFFIX;
            return validateDataFile(project, dataFilePath);
        }

        dataFilePath = StringUtils.appendIfMissing(dataFilePath, ".xlsx");

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
    private List<String> resolveScenarios(CommandLine cmd, File testScriptFile) throws IOException {
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
            Excel excel = new Excel(testScriptFile, DEF_OPEN_EXCEL_AS_DUP);
            List<Worksheet> allTestScripts = InputFileUtils.retrieveValidTestScenarios(excel);
            if (CollectionUtils.isNotEmpty(allTestScripts)) {
                allTestScripts.forEach(sheet -> targetScenarios.add(sheet.getName()));
            } else {
                fail("Unable to derive any valid test script from " + testScriptFile + ".");
            }

            if (DEF_OPEN_EXCEL_AS_DUP) { FileUtils.deleteQuietly(excel.getFile().getParentFile()); }
        }
        return targetScenarios;
    }

    private static int beforeShutdown(ExecutionSummary summary) {
        // need to kill JVM forcefully if awt was used during runtime
        // -- haven't found a way to do this more gracefully yet...
        if (ShutdownAdvisor.mustForcefullyTerminate()) { ShutdownAdvisor.forcefullyTerminate(); }

        int exitStatus;
        if (summary == null) {
            ConsoleUtils.error("Unable to cleanly execute tests; execution summary missing!");
            exitStatus = RC_EXECUTION_SUMMARY_MISSING;
        } else {
            double minExecSuccessRate =
                NumberUtils.toDouble(System.getProperty(MIN_EXEC_SUCCESS_RATE, DEF_MIN_EXEC_SUCCESS_RATE + ""));
            if (minExecSuccessRate < 0 || minExecSuccessRate > 100) { minExecSuccessRate = DEF_MIN_EXEC_SUCCESS_RATE; }
            minExecSuccessRate = minExecSuccessRate / 100;
            String minSuccessRateString = MessageFormat.format(RATE_FORMAT, minExecSuccessRate);

            double successRate = summary.getSuccessRate();
            String successRateString = MessageFormat.format(RATE_FORMAT, successRate);

            String manifest = StringUtils.leftPad(ExecUtil.deriveJarManifest(), 15, "-");
            ConsoleUtils.log(
                "\n\n" +
                "/-END OF EXECUTION--------------------------------------------------------------\n" +
                "| » Execution Time: " + (summary.getElapsedTime() / 1000) + " sec.\n" +
                "| » Test Steps:     " + summary.getExecuted() + "\n" +
                "| » Passed:         " + summary.getPassCount() + "\n" +
                "| » Failed:         " + summary.getFailCount() + "\n" +
                "| » Success Rate:   " + successRateString + "\n" +
                "\\---------------------------------------------------------------" + manifest + "-" + "\n");

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
                        exitStatus = 0;
                    }
                }
            }
        }

        File eventPath = new File(EventTracker.INSTANCE.getStorageLocation());
        if (FileUtil.isDirectoryReadable(eventPath)) {
            String[] ext = new String[]{StringUtils.removeStart(EventTracker.INSTANCE.getExtension(), ".")};
            Collection<File> eventFiles = FileUtils.listFiles(eventPath, ext, false);
            long sleepTime = 500;
            while (CollectionUtils.isNotEmpty(eventFiles)) {
                // don't sleep too long... 5 sec tops
                if (sleepTime > 5000) { break; }

                // sleep/wait
                try { Thread.sleep(sleepTime);} catch (InterruptedException e) { }

                // next sleep time will be doubled
                sleepTime += sleepTime;

                // check for event files again...
                eventFiles = FileUtils.listFiles(eventPath, ext, false);
            }
        }

        beforeShutdownMemUsage();

        return exitStatus;
    }

    private static void beforeShutdownMemUsage() {
        MemManager.gc((Object) Nexial.class);
        MemManager.recordMemoryChanges("just before exit");
        String memUsage = MemManager.showUsage("| »      ");
        if (StringUtils.isNotBlank(memUsage)) {
            ConsoleUtils.log("\n" +
                             "/-MEMORY-USAGE------------------------------------------------------------------\n" +
                             memUsage +
                             "\\-------------------------------------------------------------------------------");
        }
    }

    private static String readCellValue(XSSFRow row, int columnIndex) {
        if (row == null) { return ""; }
        XSSFCell cell = row.getCell(columnIndex);
        return cell == null ? "" : StringUtils.trim(cell.getStringCellValue());
    }

    private void notifyCompletion(ExecutionSummary summary) {
        try {
            ExecutionNotifier nexialMailer = springContext.getBean("nexialMailer", ExecutionNotifier.class);
            nexialMailer.notify(summary);
        } catch (IntegrationConfigException | IOException e) {
            ConsoleUtils.error("Unable to send out notification email: " + e.getMessage());
        }
    }

    private void trackEvent(NexialEvent event) { EventTracker.INSTANCE.track(event); }

    private void trackExecution(NexialEnv nexialEnv) { EventTracker.INSTANCE.track(nexialEnv); }

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
