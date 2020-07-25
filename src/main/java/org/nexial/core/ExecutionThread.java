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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.nexial.commons.logging.LogbackUtils;
import org.nexial.commons.utils.FileUtil;
import org.nexial.commons.utils.ResourceUtils;
import org.nexial.core.aws.NexialS3Helper;
import org.nexial.core.excel.Excel;
import org.nexial.core.model.*;
import org.nexial.core.plugins.web.CloudWebTestingPlatform;
import org.nexial.core.reports.ExecutionMailConfig;
import org.nexial.core.reports.ExecutionReporter;
import org.nexial.core.spi.NexialExecutionEvent;
import org.nexial.core.spi.NexialListenerFactory;
import org.nexial.core.utils.ConsoleUtils;
import org.nexial.core.logs.ExecutionLogger;
import org.nexial.core.variable.Syspath;

import static java.io.File.separator;
import static org.nexial.core.NexialConst.*;
import static org.nexial.core.NexialConst.Data.*;
import static org.nexial.core.NexialConst.Exec.*;
import static org.nexial.core.NexialConst.Iteration.*;
import static org.nexial.core.NexialConst.Project.appendLog;
import static org.nexial.core.NexialConst.Web.*;
import static org.nexial.core.SystemVariables.getDefault;
import static org.nexial.core.SystemVariables.getDefaultBool;
import static org.nexial.core.model.ExecutionEvent.*;
import static org.nexial.core.model.ExecutionSummary.ExecutionLevel.ITERATION;
import static org.nexial.core.model.ExecutionSummary.ExecutionLevel.SCRIPT;

/**
 * thread-bound test execution to support synchronous and asynchronous test executions.  The main driving force
 * behind this class is to allow {@link Nexial} the flexibility to execute tests either in succession or in
 * parallel, or both.  {@link Nexial} generates a series of {@link ExecutionDefinition} instances from commandline
 * arguments, and these {@link ExecutionDefinition} in turn represents one or more test execution, which may be
 * performed serially or in parallel.  This class runs a set of tests - meaning 1 or more {@link TestScenario}s,
 * with 1 or more {@link TestData} sheets over 1 or more iterations -- perpetually as a separate thread.  However
 * {@link Nexial} as the initiator has the option to wait on thread complete or to launch another set of
 * tests in parallel.  This class will track all the test artifacts such as test scenarios, test steps, test data
 * and test results within its own thread context.  No sharing of such data between parallel test executions.
 * However test results will be consolidated at the end of the entire run.
 *
 * @see Nexial
 * @see ExecutionDefinition
 */
public final class ExecutionThread extends Thread {
    private static final ThreadLocal<ExecutionContext> THREAD_LOCAL = new ThreadLocal<>();

    private ExecutionDefinition execDef;
    private final ExecutionSummary executionSummary = new ExecutionSummary();
    private final List<File> completedTests = new ArrayList<>();
    private boolean firstScript;
    private boolean lastScript;

    // capture the data after an execution run (all iteration, all scenarios within 1 file)
    private Map<String, Object> intraExecutionData = new HashMap<>();

    public static ExecutionContext get() { return THREAD_LOCAL.get(); }

    public static void set(ExecutionContext context) { THREAD_LOCAL.set(context); }

    public static void unset() {
        ExecutionContext context = THREAD_LOCAL.get();
        if (context != null) {
            context.endScript();
            THREAD_LOCAL.remove();
        }
    }

    public static ExecutionThread newInstance(ExecutionDefinition execDef) {
        ExecutionThread self = new ExecutionThread();
        self.execDef = execDef;
        return self;
    }

    public void setFirstScript(boolean firstScript) { this.firstScript = firstScript;}

    public void setLastScript(boolean lastScript) { this.lastScript = lastScript;}

    @Override
    public void run() {
        if (execDef == null) { throw new RuntimeException("No ExecutionContext instance in current thread context"); }

        String runId = execDef.getRunId();
        LogbackUtils.registerLogDirectory(appendLog(execDef));

        StopWatch ticktock = new StopWatch();
        ticktock.start();

        ExecutionContext context = MapUtils.isNotEmpty(intraExecutionData) ?
                                   new ExecutionContext(execDef, intraExecutionData) : new ExecutionContext(execDef);
        context.setCurrentActivity(null);
        context.removeDataForcefully(IS_LAST_ITERATION);
        context.removeDataForcefully(IS_FIRST_ITERATION);
        if (StringUtils.isNotBlank(execDef.getPlanFile())) {
            context.setData(OPT_INPUT_PLAN_FILE, execDef.getPlanFile());
        }

        ExecutionThread.set(context);

        // in case there were fail-immediate condition from previous script..
        if (shouldFailNow(context)) { return; }

        IterationManager iterationManager = execDef.getTestData().getIterationManager();
        int totalIterations = iterationManager.getIterationCount();

        String scriptLocation = execDef.getTestScript();
        ConsoleUtils.log(runId, "executing " + scriptLocation + " with " + totalIterations + " iteration(s)");

        String scriptName =
            StringUtils.substringBeforeLast(
                StringUtils.substringAfterLast(StringUtils.replace(scriptLocation, "\\", "/"), "/"), ".") +
            " (" + totalIterations + ")";
        executionSummary.setName(scriptName);
        executionSummary.setExecutionLevel(SCRIPT);
        executionSummary.setStartTime(System.currentTimeMillis());
        executionSummary.setScriptFile(scriptLocation);
        executionSummary.setDataFile(execDef.getDataFile().getAbsolutePath());
        executionSummary.setIterationTotal(totalIterations);
        executionSummary.setPlanSequence(execDef.getPlanSequence());
        executionSummary.setPlanName(execDef.getPlanName());
        executionSummary.setPlanFile(execDef.getPlanFile());
        executionSummary.setPlanDescription(execDef.getDescription());

        for (int iterationIndex = 1; iterationIndex <= totalIterations; iterationIndex++) {
            // SINGLE THREAD EXECUTION WITHIN FOR LOOP!

            int iterationRef = iterationManager.getIterationRef(iterationIndex - 1);
            Excel testScript = null;
            boolean allPass = true;

            // we need to infuse "between" #default and whatever data sheets is assigned for this test script
            execDef.infuseIntraExecutionData(intraExecutionData);

            ExecutionSummary iterSummary = new ExecutionSummary();
            iterSummary.setName(iterationIndex + " of " + totalIterations);
            iterSummary.setExecutionLevel(ITERATION);
            iterSummary.setStartTime(System.currentTimeMillis());
            iterSummary.setScriptFile(scriptLocation);
            iterSummary.setIterationIndex(iterationIndex);
            iterSummary.setIterationTotal(totalIterations);

            try {
                testScript = ExecutionInputPrep.prep(runId, execDef, iterationIndex);
                iterSummary.setTestScript(testScript.getOriginalFile());
                context.useTestScript(testScript);

                context.startIteration(iterationIndex, iterationRef, totalIterations, firstScript);

                ExecutionLogger logger = context.getLogger();
                logger.log(context, "executing iteration #" + iterationIndex + " of " + totalIterations +
                                    "; Iteration Id " + iterationRef);
                allPass = context.execute();

                onIterationComplete(context, iterSummary, iterationIndex);
                if (shouldStopNow(context, allPass)) { break; }
            } catch (Throwable e) {
                onIterationException(context, iterSummary, iterationIndex, e);
                if (shouldStopNow(context, allPass)) { break; }
            } finally {
                context.setData(ITERATION_ENDED, true);
                context.setCurrentActivity(null);

                File testScriptFile = null;
                if (testScript == null) {
                    // possibly the script prep/parsing routine failed (ie ExecutionInputPrep.prep()), but the output
                    // file might already generated. If so then we should use the generated output file and generate
                    // output (as much as possible).
                    String scriptOutputFullPath = context.getStringData(OPT_INPUT_EXCEL_FILE);
                    if (StringUtils.isNotBlank(scriptOutputFullPath)) {testScriptFile = new File(scriptOutputFullPath);}
                } else {
                    testScriptFile = testScript.getFile();
                    // sync #data sheet with context
                    ExecutionResultHelper.updateOutputDataSheet(context, testScript);
                }

                String testScriptFileName = "UNKNOWN TEST SCRIPT";

                if (FileUtil.isFileReadable(testScriptFile)) {
                    testScriptFileName = testScriptFile.getName();

                    // now the execution for this iteration is done. We'll add new execution summary page to its output.
                    iterSummary.setFailedFast(context.isFailFast());
                    iterSummary.setEndTime(System.currentTimeMillis());
                    iterSummary.aggregatedNestedExecutions(context);

                    if (testScript != null) {
                        iterSummary.generateExcelReport(testScript);
                    } else {
                        iterSummary.generateExcelReport(testScriptFile);
                    }

                    NexialListenerFactory.fireEvent(NexialExecutionEvent.newIterationEndEvent(scriptLocation,
                                                                                              iterationIndex,
                                                                                              iterSummary));
                    executionSummary.addNestSummary(iterSummary);

                    // report status at iteration level
                    CloudWebTestingPlatform.reportCloudBrowserStatus(context, iterSummary, IterationComplete);

                    ExecutionReporter.openExecutionResult(context, testScriptFile);

                    completedTests.add(testScriptFile);
                }

                collectIntraExecutionData(context, iterationRef);
                ExecutionMailConfig.configure(context);

                context.endIteration();

                MemManager.recordMemoryChanges(testScriptFileName + " completed");

                context.setData(ITERATION_ENDED, false);
            }
        }

        System.setProperty(OPT_OPEN_EXEC_REPORT,
                           context.getStringData(OPT_OPEN_EXEC_REPORT, getDefault(OPT_OPEN_EXEC_REPORT)));

        onScriptComplete(context, executionSummary, iterationManager, ticktock);

        ExecutionThread.unset();
        MemManager.recordMemoryChanges(scriptName + " completed");
    }

    public ExecutionSummary getExecutionSummary() { return executionSummary; }

    public List<File> getCompletedTests() { return completedTests; }

    public ExecutionDefinition getExecDef() { return execDef; }

    protected boolean shouldFailNow(ExecutionContext context) {
        if (context == null) { return true; }

        ExecutionLogger logger = context.getLogger();
        if (context.isFailImmediate()) {
            logger.log(context, MSG_SCENARIO_FAIL_IMMEDIATE);
            collectIntraExecutionData(context, 0);
            return true;
        }

        if (execDef.isFailFast() && !context.getBooleanData(OPT_LAST_OUTCOME)) {
            if (context.getBooleanData(RESET_FAIL_FAST, getDefaultBool(RESET_FAIL_FAST))) {
                // reset and pretend nothing's wrong.  Current script will be executed..
                context.setData(OPT_LAST_OUTCOME, true);
            } else {
                logger.log(context, MSG_SCENARIO_FAIL_FAST);
                collectIntraExecutionData(context, 0);
                return true;
            }
        }

        return false;
    }

    protected boolean shouldStopNow(ExecutionContext context, boolean allPass) {
        if (context == null) { return true; }

        ExecutionLogger logger = context.getLogger();
        if (!allPass && context.isFailFast()) {
            logger.log(context, MSG_EXEC_FAIL_FAST);
            return true;
        }

        if (context.isFailImmediate()) {
            logger.log(context, MSG_EXEC_FAIL_IMMEDIATE);
            return true;
        }

        if (context.isEndImmediate()) {
            logger.log(context, MSG_EXEC_END_IF);
            return true;
        }

        return false;
    }

    protected Map<String, Object> getIntraExecutionData() { return intraExecutionData; }

    protected void setIntraExecutionData(Map<String, Object> intraExecutionData) {
        this.intraExecutionData = intraExecutionData;
    }

    protected void collectIntraExecutionData(ExecutionContext context, int completeIteration) {
        if (context == null) { return; }

        context.fillIntraExecutionData(intraExecutionData);

        // override, if found, previous "last completed iteration count"
        intraExecutionData.put(LAST_ITERATION, completeIteration);

        // for last iteration, finalize the execution summary's custom header and footer
        if (context.getBooleanData(IS_LAST_ITERATION)) {
            if (context.hasData(SUMMARY_CUSTOM_HEADER)) {
                System.setProperty(SUMMARY_CUSTOM_HEADER, context.getStringData(SUMMARY_CUSTOM_HEADER));
            }
            if (context.hasData(SUMMARY_CUSTOM_FOOTER)) {
                System.setProperty(SUMMARY_CUSTOM_FOOTER, context.getStringData(SUMMARY_CUSTOM_FOOTER));
            }
        }
    }

    protected void onIterationException(ExecutionContext context,
                                        ExecutionSummary iterationSummary,
                                        int iteration,
                                        Throwable e) {
        if (context == null) { context = ExecutionThread.get(); }

        if (e != null) {
            iterationSummary.setError(e);
            context.setFailImmediate(true);
        }

        String testScript = null;
        if (context != null) {
            if (context.getTestScript() != null) {
                testScript = context.getTestScript().getFile().getAbsolutePath();
            } else {
                String testScriptFullpath = context.getStringData(OPT_INPUT_EXCEL_FILE);
                if (FileUtil.isFileReadable(testScriptFullpath)) { testScript = testScriptFullpath; }
            }
        }

        if (StringUtils.isBlank(testScript)) { testScript = execDef.getTestScript() + " (unparseable?)"; }

        String runId;
        if (context == null) {
            runId = "UNKNOWN";
        } else {
            runId = context.getRunId();
            if (CollectionUtils.isNotEmpty(context.getTestScenarios())) {
                context.getTestScenarios().forEach(testScenario -> {
                    if (testScenario != null && testScenario.getExecutionSummary() != null) {
                        iterationSummary.addNestSummary(testScenario.getExecutionSummary());
                    }
                });
            }
        }

        ConsoleUtils.error(runId,
                           NL +
                           "/-TEST FAILED!!-----------------------------------------------------------------" + NL +
                           "| Test Output:    " + testScript + NL +
                           "| Iteration:      " + iteration + NL +
                           "\\-------------------------------------------------------------------------------" + NL +
                           (e != null ? "» Error:          " + e.getMessage() : ""),
                           (e instanceof AssertionError) ? null : e);
    }

    protected void onIterationComplete(ExecutionContext context, ExecutionSummary iterationSummary, int iteration) {
        List<TestScenario> testScenarios = context.getTestScenarios();
        if (CollectionUtils.isEmpty(testScenarios)) { return; }

        context.removeData(BREAK_CURRENT_ITERATION);

        final int[] total = {0};
        final int[] failCount = {0};

        testScenarios.forEach(testScenario -> {
            ExecutionSummary executionSummary = testScenario.getExecutionSummary();
            iterationSummary.addNestSummary(executionSummary);
            total[0] += executionSummary.getTotalSteps();
            failCount[0] += executionSummary.getFailCount();
        });

        ConsoleUtils.log(context.getRunId(),
                         NL +
                         "/-TEST COMPLETE-----------------------------------------------------------------" + NL +
                         "| Test Output:    " + context.getTestScript().getFile() + NL +
                         "| Iteration:      " + iteration + NL +
                         "\\-------------------------------------------------------------------------------" + NL +
                         "» Execution Time: " + (context.getEndTimestamp() - context.getStartTimestamp()) + " ms" + NL +
                         "» Test Steps:     " + total[0] + NL +
                         "» Error(s):       " + failCount[0] + NL + NL + NL);
        MemManager.gc(this);
    }

    protected void onScriptComplete(ExecutionContext context,
                                    ExecutionSummary summary,
                                    IterationManager iterationManager,
                                    StopWatch ticktock) {
        ticktock.stop();
        summary.setEndTime(System.currentTimeMillis());
        summary.aggregatedNestedExecutions(context);
        NexialListenerFactory.fireEvent(NexialExecutionEvent.newScriptEndEvent(summary.getScriptFile(), summary));

        CloudWebTestingPlatform.reportCloudBrowserStatus(context, summary, ScriptComplete);

        StringBuilder cloudOutputBuffer = new StringBuilder();

        if (summary.getTestScript() != null) { handleTestScript(context, summary);}

        summary.getNestedExecutions().forEach(nested -> {
            handleTestScript(context, nested);
            cloudOutputBuffer.append("» Iteration ").append(nested.getName()).append(": ")
                             .append(nested.getTestScriptLink()).append(NL);
        });

        ConsoleUtils.log(context.getRunId(),
                         NL +
                         "/-TEST COMPLETE-----------------------------------------------------------------" + NL +
                         "| Test Script:    " + execDef.getTestScript() + NL +
                         "\\-------------------------------------------------------------------------------" + NL +
                         "» Execution Time: " + (ticktock.getTime()) + " ms" + NL +
                         "» Iterations:     " + iterationManager + NL +
                         "» Test Steps:     " + summary.getTotalSteps() + NL +
                         "» Passed:         " + summary.getPassCount() + NL +
                         "» Error(s):       " + summary.getFailCount() + NL +
                         //"» Warnings:       " + summary.getWarnCount() + NL +
                         StringUtils.defaultIfBlank(cloudOutputBuffer.toString(), "") + NL + NL);

        // special handling of mail config, make sure we get latest/greatest from context
        ExecutionMailConfig mailConfig = ExecutionMailConfig.get();
        if (mailConfig != null && mailConfig.isReady()) { ExecutionMailConfig.configure(context); }

        context.getExecutionEventListener().onScriptComplete();

        if (context.hasData(LAST_PLAN_STEP)) {
            System.setProperty(LAST_PLAN_STEP, context.getStringData(LAST_PLAN_STEP, getDefault(LAST_PLAN_STEP)));
        }

        if (MapUtils.isNotEmpty(intraExecutionData)) { intraExecutionData.remove(LAST_ITERATION); }

        if (lastScript) {
            CloudWebTestingPlatform.reportCloudBrowserStatus(context, executionSummary, ExecutionComplete);
            context.getExecutionEventListener().onExecutionComplete();
            handleBrowserMetrics(context);
        }

        // we don't want the reference data from this script to taint the next
        context.clearScenarioRefData();
        context.clearScriptRefData();

        MemManager.gc(execDef);
    }

    /** handle browser metrics */
    private void handleBrowserMetrics(ExecutionContext context) {
        System.clearProperty(WEB_METRICS_GENERATED);
        if (!context.getBooleanData(WEB_PERF_METRICS_ENABLED, getDefaultBool(WEB_PERF_METRICS_ENABLED))) { return; }
        if (context.isInteractiveMode()) { return; }

        String outputBase = (new Syspath().out("fullpath")) + separator;
        File metricsFile = new File(outputBase + WEB_METRICS_JSON);
        if (!FileUtil.isFileReadable(metricsFile, 100)) { return; }

        try {
            String json = FileUtils.readFileToString(metricsFile, DEF_FILE_ENCODING);
            String html = StringUtils.replace(ResourceUtils.loadResource(WEB_METRICS_HTML_LOC + WEB_METRICS_HTML),
                                              WEB_METRICS_TOKEN,
                                              json);
            File webMetricFile = new File(outputBase + WEB_METRICS_HTML);
            FileUtils.writeStringToFile(webMetricFile, html, DEF_FILE_ENCODING);
            System.setProperty(WEB_METRICS_GENERATED, "true");

            if (context.isOutputToCloud()) {
                NexialS3Helper otc = context.getOtc();
                if (otc != null && otc.isReadyForUse() && FileUtil.isFileReadable(webMetricFile, 1024)) {
                    // upload HTML report to cloud
                    String url = otc.importToS3(webMetricFile, otc.resolveOutputDir(), true);
                    ConsoleUtils.log("Web Performance Metric exported to " + url);
                }
            }
        } catch (IOException e) {
            // unable to read JSON, read HTML or write HTML to output
            ConsoleUtils.error("Unable to generate browser metrics HTML: " + e.getMessage());
        }
    }

    private static void handleTestScript(ExecutionContext context, ExecutionSummary execution) {
        // already done?
        if (StringUtils.isNotBlank(execution.getTestScriptLink()) || execution.getTestScript() == null) { return; }

        File testScript = execution.getTestScript();
        if (context.isOutputToCloud()) {
            try {
                // when saving test output to cloud, we might NOT want to remove it locally - esp. when open-result is on
                execution.setTestScriptLink(context.getOtc().importFile(testScript, !isAutoOpenResult()));
            } catch (IOException e) {
                ConsoleUtils.error(toCloudIntegrationNotReadyMessage(testScript.toString()) + ": " + e.getMessage());
            }
        } else {
            execution.setTestScriptLink(testScript.getAbsolutePath());
        }
    }
}
