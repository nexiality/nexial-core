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

package org.nexial.core.plugins.soapui;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.jdom2.Document;
import org.jdom2.JDOMException;
import org.nexial.commons.proc.ProcessInvoker;
import org.nexial.commons.proc.ProcessOutcome;
import org.nexial.commons.utils.FileUtil;
import org.nexial.commons.utils.XmlUtils;
import org.nexial.core.model.StepResult;
import org.nexial.core.plugins.base.BaseCommand;
import org.nexial.core.plugins.soapui.SoapUITestResult.SoapUITestCase;
import org.nexial.core.utils.OutputFileUtils;

import static java.io.File.separator;
import static java.lang.System.lineSeparator;
import static org.nexial.commons.utils.FileUtil.SortBy.LASTMODIFIED_ASC;
import static org.nexial.core.NexialConst.DEF_CHARSET;
import static org.nexial.core.NexialConst.SoapUI.*;
import static org.nexial.core.utils.CheckUtils.requires;
import static org.nexial.core.utils.CheckUtils.requiresNotBlank;

public class SoapUICommand extends BaseCommand {

    @Override
    public String getTarget() { return "soap"; }

    public StepResult run(String project) throws Exception {
        requires(StringUtils.isNotBlank(project), "invalid project profile", project);

        // step 1: set up
        Map<String, String> props = context.getDataByPrefix(project + ".");
        requires(MapUtils.isNotEmpty(props), "no properties found for ", project);

        File f = extractProjectFile(props);
        String projectFullPath = f.getAbsolutePath();
        log("running soapUI test(s) via '" + projectFullPath + "'...");

        String runOutputDir = resolveOutputDir(f.getName());

        StringBuilder failReasons = new StringBuilder();

        // step 2 : run soapui testrunner
        ProcessOutcome outcome = runSoapUITests(props, projectFullPath, runOutputDir);
        int exitStatus = outcome.getExitStatus();
        if (exitStatus != 0) { failReasons.append("SoapUI TestRunner exits with ").append(exitStatus); }

        // step 3: scan for junit report(s)
        failReasons.append(processResult(f, runOutputDir));

        // step 4: all done!
        return failReasons.length() == 0 ?
               StepResult.success("SoapUI Test completes for '" + projectFullPath + "'") :
               StepResult.fail(failReasons.toString());
    }

    public StepResult runTestSuite(String project, String testSuite) throws Exception {
        requires(StringUtils.isNotBlank(testSuite), "invalid test suite", testSuite);
        context.setData(project + "." + SOAPUI_PARAM_TESTSUITE, testSuite);

        return run(project);
    }

    public StepResult runTestCase(String project, String testSuite, String testCase) throws Exception {
        requires(StringUtils.isNotBlank(testCase), "invalid test case", testCase);
        context.setData(project + "." + SOAPUI_PARAM_TESTCASE, testCase);

        return runTestSuite(project, testSuite);
    }

    /** pop up projectXml property and return it as {@link File} instance */
    protected File extractProjectFile(Map<String, String> props) {
        String projectXml = props.remove(VAR_PROJECT_XML);
        requiresNotBlank(projectXml, "invalid soapUI project file", projectXml);
        File f = new File(projectXml);
        requires(f.canRead(), "cannot read specified soapUI project file", projectXml);
        return f;
    }

    protected String resolveOutputDir(String projectFileName) throws IOException {
        String runOutputDir = context.getProject().getOutPath() + separator +
                              OutputFileUtils.webFriendly(projectFileName);
        FileUtils.forceMkdir(new File(runOutputDir));
        log("setting soapui test output to " + runOutputDir);
        return runOutputDir;
    }

    /** running soap ui testrunner and collect the output (std/stderr) */
    protected ProcessOutcome runSoapUITests(Map<String, String> props, String projectFullPath, String runOutputDir)
        throws IOException, InterruptedException {
        File soapuiTestRunner = resolveTestRunner(props);
        List<String> params = resolveParams(props, projectFullPath, runOutputDir);
        ProcessOutcome outcome = ProcessInvoker.invoke(soapuiTestRunner.getAbsolutePath(), params, null);

        // status != 0 should always be FAILURE
        int exitStatus = outcome.getExitStatus();
        log("exit status: " + exitStatus);

        String stdout = outcome.getStdout();
        log("output: " + stdout);

        String stderr = outcome.getStderr();
        log("error: " + stderr);

        return outcome;
    }

    /** derive the full path of the soapui testrunner script */
    protected File resolveTestRunner(Map<String, String> props) {
        String soapuiHome = props.remove(VAR_SOAPUI_HOME);
        requires(StringUtils.isNotBlank(soapuiHome), "invalid SoapUI home directory", soapuiHome);
        File soapuiTestRunner = new File(soapuiHome + TESTRUNNER_REL_PATH);
        requires(soapuiTestRunner.canRead(), "invalid SoapUI home directory", soapuiHome);
        requires(soapuiTestRunner.canExecute(), TESTRUNNER_REL_PATH + " under SoapUI home directory cannot be executed",
                 soapuiHome);
        return soapuiTestRunner;
    }

    protected String processResult(File projectXml, String outputDir) throws JDOMException, IOException {
        // step 1: sanity check
        List<File> junitReports = FileUtil.listFiles(outputDir, REGEX_JUNIT_REPORT_FILES, false, LASTMODIFIED_ASC);
        if (CollectionUtils.isEmpty(junitReports)) {
            return "No test report generated.  Check console output for more details";
        }

        StringBuilder failReasons = new StringBuilder();

        Document project = XmlUtils.parse(projectXml);

        // step 2: parse and log junit report(s)
        for (File junitReport : junitReports) {
            String testSuiteName = StringUtils.substringBetween(junitReport.getName(), "TEST-", ".xml");

            // no output means BAD RUN... don't care about junit report content
            List<File> outputs = FileUtil.listFiles(outputDir, testSuiteName + REGEX_OUTPUT_FILES, false);
            if (CollectionUtils.isEmpty(outputs)) {
                failReasons.append("Unable to retrieve output for test suite ").append(testSuiteName);
                continue;
            }

            boolean storeSoapUiResponse = context.getBooleanData(OPT_SOAPUI_STORE_RESP);

            SoapUITestResult result = SoapUITestResult.parse(project, junitReport, outputs);
            log("- total exec time: " + result.getExecutionTime() + " sec");

            List<SoapUITestCase> testcases = result.getTestcases();
            for (SoapUITestCase testCase : testcases) {
                log("- " + testCase.getName() + " - exec time: " + testCase.getExecutionTime() + " sec");

                // we DO NOT assume that there's only 1 output per junit report
                // step 3: handle test steps and endpoint response
                Set<String> testSteps = testCase.getTestSteps();
                for (String testStep : testSteps) {
                    File output = testCase.getTestStepOutput(testStep);
                    if (output == null) { continue; }
                    if (storeSoapUiResponse) {
                        String contextKey = result.getTestSuiteName() + "." + testCase.getName() + "." + testStep;
                        context.getCurrentTestStep().addNestedScreenCapture(output.getAbsolutePath(), contextKey);
                        updateDataVariable(contextKey, parseResponse(output));
                    }
                }

                // step 4: print error to report
                List<String> errors = testCase.getErrors();
                if (CollectionUtils.isEmpty(errors)) {
                    log(testCase.getName() + " validated");
                } else {
                    for (String error : errors) { error(error); }
                }
            }
        }

        return failReasons.toString();
    }

    protected String parseResponse(File output) {
        try {
            List<String> content = FileUtils.readLines(output, DEF_CHARSET);
            if (CollectionUtils.isEmpty(content)) { return ""; }

            int responseStartLine = -1;
            int payloadStartLine = -1;
            for (int i = 0; i < content.size(); i++) {
                String line = content.get(i);
                if (StringUtils.startsWith(line, "---------------- Response --------------------------")) {
                    responseStartLine = i;
                    continue;
                }

                if (responseStartLine != -1 && StringUtils.isBlank(line)) {
                    payloadStartLine = i + 1;
                    break;
                }
            }

            if (payloadStartLine != -1) {
                StringBuilder buffer = new StringBuilder();
                for (int i = payloadStartLine; i < content.size(); i++) {
                    String line = content.get(i);
                    if (StringUtils.isBlank(line)) { continue; }
                    buffer.append(line).append(lineSeparator());
                }

                return buffer.toString();
            }

            return "";
        } catch (IOException e) {
            return "";
        }
    }

    protected List<String> resolveParams(Map<String, String> props, String projectFullPath, String runOutputDir) {
        List<String> params = new ArrayList<>(STD_OPTS);

        if (MapUtils.isNotEmpty(props)) {
            if (props.containsKey(SOAPUI_PARAM_TESTSUITE)) {
                params.add("-s" + props.get(SOAPUI_PARAM_TESTSUITE));
                props.remove(SOAPUI_PARAM_TESTSUITE);
            }
            if (props.containsKey(SOAPUI_PARAM_TESTCASE)) {
                params.add("-c" + props.get(SOAPUI_PARAM_TESTCASE));
                props.remove(SOAPUI_PARAM_TESTCASE);
            }
        }

        params.add("-f" + runOutputDir);
        params.add(projectFullPath);

        if (MapUtils.isNotEmpty(props)) {
            Set<String> keys = props.keySet();
            params.addAll(keys.stream().map(key -> "-P" + key + "=" + props.get(key)).collect(Collectors.toList()));
        }

        return params;
    }
}
