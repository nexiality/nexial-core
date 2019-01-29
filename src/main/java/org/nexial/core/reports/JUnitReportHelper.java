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
 */

package org.nexial.core.reports;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import javax.validation.constraints.NotNull;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.jdom2.Document;
import org.jdom2.Element;
import org.nexial.commons.utils.DateUtility;
import org.nexial.commons.utils.EnvUtils;
import org.nexial.commons.utils.FileUtil;
import org.nexial.core.model.ExecutionSummary;

import static org.nexial.core.NexialConst.Data.NEXIAL_LOG_PREFIX;
import static org.nexial.core.NexialConst.PRETTY_XML_OUTPUTTER;

public class JUnitReportHelper {
    private static final NumberFormat JUNIT_TIME_FORMATTER = new DecimalFormat("0.000");

    private static final String TESTSUITES = "testsuites";
    private static final String TESTSUITE = "testsuite";
    private static final String PROPERTIES = "properties";
    private static final String PROPERTY = "property";
    private static final String TESTCASE = "testcase";
    private static final String FAILURE = "failure";
    private static final String SYSTEM_OUT = "system-out";

    private static final String ID = "id";
    private static final String TIMESTAMP = "timestamp";
    private static final String TIME = "time";
    private static final String TESTS = "tests";
    private static final String ERRORS = "errors";
    private static final String PACKAGE = "package";
    private static final String HOSTNAME = "hostname";
    private static final String RUN_HOST = "Run From";
    private static final String RUN_HOST_OS = "Run OS";
    private static final String RUN_USER = "Run User";
    private static final String NAME = "name";
    private static final String VALUE = "value";
    private static final String CLASSNAME = "classname";
    private static final String MESSAGE = "message";
    private static final String TYPE = "type";

    private static final String UNKNOWN = "UNKNOWN";
    private static final String TYPE_ACTIVITY_FAILURE = "failure found in test activity";

    static void toJUnitXml(ExecutionSummary summary, File output) throws IOException {
        Document document = new Document();

        Element testsuites = toTestSuites(summary);
        document.setRootElement(testsuites);

        // script
        summary.getNestedExecutions().forEach(scriptExec -> addTo(testsuites, toTestSuiteList(scriptExec, summary)));

        try (FileOutputStream out = new FileOutputStream(output)) { PRETTY_XML_OUTPUTTER.output(document, out); }
    }

    @NotNull
    private static Element toTestSuites(ExecutionSummary summary) {
        Element testsuites = new Element(TESTSUITES);
        testsuites.setAttribute(NAME, summary.getName());
        testsuites.setAttribute(TIMESTAMP, DateUtility.formatISO8601(summary.getStartTime()));
        testsuites.setAttribute(TIME, toJUnitTime(summary.getEndTime() - summary.getStartTime()));
        testsuites.setAttribute(TESTS, summary.getTotalSteps() + "");
        testsuites.setAttribute(ERRORS, summary.getFailCount() + "");
        return testsuites;
    }

    private static List<Element> toTestSuiteList(ExecutionSummary scriptExec, ExecutionSummary planExec) {
        int idBase = scriptExec.getPlanSequence() * 1000;
        String nameBase = (scriptExec.getPlanSequence() > 0 ? scriptExec.getPlanSequence() + " " : "") +
                          toTestSuiteName(scriptExec);

        List<Element> testsuites = new ArrayList<>();

        // all script has at least 1 iteration (Default)
        if (CollectionUtils.isNotEmpty(scriptExec.getNestedExecutions())) {
            scriptExec.getNestedExecutions().forEach(iteration -> {
                int iterationTotal = iteration.getIterationTotal();
                int currIteration = iteration.getIterationIndex();
                String iterationText = currIteration + " of " + iterationTotal;

                Element testsuite = new Element(TESTSUITE);
                testsuite.setAttribute(ID, (idBase + currIteration) + "");
                testsuite.setAttribute(NAME, iterationTotal < 2 ?
                                             nameBase :
                                             nameBase + ", Iteration " + iterationText);
                testsuite.setAttribute(PACKAGE, nameBase);
                testsuite.setAttribute(HOSTNAME, EnvUtils.getHostName());
                testsuite.setAttribute(TIMESTAMP, DateUtility.formatISO8601(iteration.getStartTime()));
                testsuite.setAttribute(TIME, toJUnitTime(iteration.getEndTime() - iteration.getStartTime()));
                testsuite.setAttribute(TESTS, iteration.getTotalSteps() + "");
                testsuite.setAttribute(ERRORS, iteration.getFailCount() + "");

                Element properties = new Element(PROPERTIES);
                addTo(properties, newProperty(RUN_HOST, iteration.getRunHost()));
                addTo(properties, newProperty(RUN_HOST_OS, iteration.getRunHostOs()));
                addTo(properties, newProperty(RUN_USER, iteration.getRunUser()));
                addTo(properties, newProperty("Iteration", iterationText));
                addTo(properties, newProperty("Script", FileUtil.extractFilename(iteration.getScriptFile())));
                addTo(properties, newProperty("Data", iteration.getDataFile()));

                if (MapUtils.isNotEmpty(iteration.getLogs())) {
                    iteration.getLogs()
                             .forEach((name, value) -> {
                                 String logName = StringUtils.startsWith(name, NEXIAL_LOG_PREFIX) ? "Logs" : name;
                                 addTo(properties, newProperty(logName, value));
                             });
                }

                if (MapUtils.isNotEmpty(iteration.getReferenceData())) {
                    iteration.getReferenceData()
                             .forEach((name, value) -> addTo(properties, newProperty(name, value)));
                }

                testsuite.addContent(properties);

                iteration.getNestedExecutions()
                         .forEach(scenario -> addTo(testsuite, toTestCases(scenario, iteration)));

                testsuites.add(testsuite);
            });
        }

        return testsuites;
    }

    private static void addTo(Element parent, Element childElement) {
        if (parent == null || childElement == null) { return; }
        parent.addContent(childElement);
    }

    private static void addTo(Element parent, List<Element> elements) {
        if (parent == null || CollectionUtils.isEmpty(elements)) { return; }
        parent.addContent(elements);
    }

    private static List<Element> toTestCases(ExecutionSummary scenario, ExecutionSummary iteration) {
        String scenarioName = scenario.getName();
        String outputLink = iteration.getTestScriptLink();
        String executionLog = iteration.getExecutionLog();

        List<Element> testcases = new ArrayList<>();

        if (CollectionUtils.isNotEmpty(scenario.getNestedExecutions())) {
            scenario.getNestedExecutions().forEach(activity -> {

                Element testcase = new Element(TESTCASE);
                testcase.setAttribute(ID, activity.getName());
                testcase.setAttribute(NAME, activity.getName());
                testcase.setAttribute(CLASSNAME, scenarioName);
                testcase.setAttribute(TIMESTAMP, DateUtility.formatISO8601(activity.getStartTime()));
                testcase.setAttribute(TIME, toJUnitTime(iteration.getEndTime() - activity.getStartTime()));

                if (activity.getFailCount() > 0) {
                    testcase.addContent(new Element(FAILURE)
                                            .setAttribute(TYPE, TYPE_ACTIVITY_FAILURE)
                                            .setAttribute(MESSAGE, activity.getFailCount() + " of " +
                                                                   activity.getTotalSteps() + " FAILED. " +
                                                                   "Check " + outputLink + " for execution result, " +
                                                                   executionLog + " for execution logs"));
                }

                testcase.addContent(new Element(SYSTEM_OUT)
                                        .addContent("Total : " + activity.getTotalSteps() + "\n" +
                                                    "Passed: " + activity.getPassCount() + "\n" +
                                                    "Failed: " + activity.getFailCount() + "\n" +
                                                    "Output: " + outputLink + "\n" +
                                                    "Logs  : " + executionLog + "\n"));

                testcases.add(testcase);
            });
        }

        return testcases;
    }

    /** format: [script name]|[script name] [iteration total] */
    private static String toTestSuiteName(ExecutionSummary execution) {
        if (execution == null) { return UNKNOWN; }

        String name = execution.getName();
        if (StringUtils.isBlank(name)) { return UNKNOWN; }

        // so "2 iteration-showcase (4)" --> "2 iteration-showcase"
        return StringUtils.substringBeforeLast(name, " (");
    }

    private static Element newProperty(String name, String value) {
        if (StringUtils.isBlank(name) || StringUtils.isBlank(value)) { return null; }
        return new Element(PROPERTY).setAttribute(NAME, name).setAttribute(VALUE, value);
    }

    private static String toJUnitTime(long milliseconds) {
        return JUNIT_TIME_FORMATTER.format(milliseconds / (double) 1000);
    }

    // NOT USED
    /** format/formula: [plan sequence * 1000] + [iteration index] */
    // private static String toTestSuiteId(ExecutionSummary execution) {
    //     if (execution == null) { return "-1"; }
    //     return ((execution.getPlanSequence() * 1000) + execution.getIterationIndex()) + "";
    // }

    // NOT USED
    /** format/formula: [plan sequence] [script name], [iteration index] of [iteration total] */
    // private static String toTestSuitePackage(ExecutionSummary execution) {
    //     if (execution == null) { return UNKNOWN; }
    //
    //     String name = execution.getName();
    //     if (StringUtils.isBlank(name)) { return UNKNOWN; }
    //
    //     String packageName = "";
    //
    //     if (execution.getPlanSequence() > 0) { packageName = execution.getPlanSequence() + " "; }
    //
    //     packageName += name;
    //
    //     if (execution.getIterationTotal() > 1 & execution.getIterationIndex() > 0) {
    //         packageName += ", Iteration " + execution.getIterationIndex() + " of " + execution.getIterationTotal();
    //     }
    //
    //     return packageName;
    // }
}
