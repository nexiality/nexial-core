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

package org.nexial.core.plugins.soapui;

import java.io.File;
import java.io.IOException;
import java.util.*;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.nexial.commons.utils.XmlUtils;
import org.nexial.core.NexialConst.SoapUI;

import static org.nexial.core.NexialConst.SoapUI.*;

public class SoapUITestResult {
    private String testSuiteName;
    private int testCount;
    // failures + errors
    private int failureCount;
    private String executionTime;
    private List<SoapUITestCase> testcases = new ArrayList<>();

    public static class SoapUITestCase {
        private String executionTime;
        private String name;
        private List<String> errors = new ArrayList<>();
        private Set<String> testSteps = new HashSet<>();
        private Map<String, File> testStepOutput = new LinkedHashMap<>();

        public String getExecutionTime() { return executionTime; }

        public String getName() { return name; }

        public List<String> getErrors() { return errors; }

        public void add(String testStep, File output) {
            testSteps.add(testStep);
            testStepOutput.put(testStep, output);
        }

        public File getTestStepOutput(String testStep) { return testStepOutput.get(testStep); }

        public Set<String> getTestSteps() { return testSteps; }
    }

    public String getTestSuiteName() { return testSuiteName; }

    public int getTestCount() { return testCount; }

    public int getFailureCount() { return failureCount; }

    public String getExecutionTime() { return executionTime; }

    public List<SoapUITestCase> getTestcases() { return testcases; }

    public static SoapUITestResult parse(Document project, File junitReport, List<File> outputs)
        throws JDOMException, IOException {

        Document doc = XmlUtils.parse(junitReport);
        SoapUITestResult result = newSoapUITestResult(doc);

        int numberOfTestCases = CollectionUtils.size(XmlUtils.findNodes(doc, XPATH_TESTCASE));
        result.testcases = new ArrayList<>(numberOfTestCases);
        for (int i = 1; i <= numberOfTestCases; i++) {
            SoapUITestCase testcase = new SoapUITestCase();
            String xpathThisTestCase = XPATH_TESTCASE + "[" + i + "]";
            testcase.name = XmlUtils.getAttributeValue(doc, xpathThisTestCase, "name");
            testcase.executionTime = XmlUtils.getAttributeValue(doc, xpathThisTestCase, "time");
            // todo deal with error tag as well?
            Element elemFailure = XmlUtils.findElement(doc, XPATH_TESTCASE_FAILURE);
            if (elemFailure != null) {
                String failureDetails = elemFailure.getValue();
                //testcase.label = StringUtils.trim(StringUtils.substringBetween(failureDetails, "<h3><b>", "</b></h3>"));
                testcase.errors = parseErrors(StringUtils.substringBetween(failureDetails, "<pre>", "</pre>"));
                //} else {
                // else, no failure
                //testcase.label = testcase.name;
            }

            // deal with output
            if (project != null) {
                String xpathTestSteps = deriveTestStepsXpath(result, testcase);
                List testSteps = XmlUtils.findNodes(project, xpathTestSteps);
                if (CollectionUtils.isNotEmpty(testSteps)) {
                    for (Object testStepNode : testSteps) {
                        if (!(testStepNode instanceof Element)) { continue; }
                        Element testStep = (Element) testStepNode;
                        String testStepName = testStep.getAttributeValue("name");
                        String testStepType = testStep.getAttributeValue("type");
                        if (SoapUI.TEST_STEP_REQUEST_TYPES.contains(testStepType)) {
                            testcase.add(
                                testStepName,
                                findMatchingOutput(outputs, result.testSuiteName, testcase.name, testStepName));
                        }
                    }
                }
            }
            result.testcases.add(testcase);
        }

        return result;
    }

    public static String handleSingleQuote(String xpathText) {
        //con:testSuite[@name=concat('Sample TestSuite fails if we don', "'", 't get faults')]
        if (xpathText == null) { return xpathText; }
        if (!StringUtils.contains(xpathText, "'")) { return "'" + xpathText + "'"; }
        return "concat('" + StringUtils.replace(xpathText, "'", "',\"'\",'") + "')";
    }

    public static File findMatchingOutput(List<File> outputs, String testSuite, String testCase, String testStep) {
        String outputFileName = StringUtils.replaceChars(testSuite, SOAPUI_FILENAME_REMOVE_CHARS, "") + "-" +
                                StringUtils.replaceChars(testCase, SOAPUI_FILENAME_REMOVE_CHARS, "") + "-" +
                                StringUtils.replaceChars(testStep, SOAPUI_FILENAME_REMOVE_CHARS, "");
        outputFileName = StringUtils.replace(outputFileName, " ", "_");
        for (File output : outputs) {
            if (StringUtils.startsWith(output.getName(), outputFileName)) { return output; }
        }

        return null;
    }

    protected static String deriveTestStepsXpath(SoapUITestResult result, SoapUITestCase testcase) {
        return
            StringUtils.replace(
                StringUtils
                    .replace(SoapUI.XPATH_TESTSTEPS_TEMPLATE, "${testsuite}", handleSingleQuote(result.testSuiteName)),
                "${testcase}",
                handleSingleQuote(testcase.name));
    }

    protected static SoapUITestResult newSoapUITestResult(Document doc) throws JDOMException {
        SoapUITestResult result = new SoapUITestResult();
        result.testSuiteName = XmlUtils.getAttributeValue(doc, XPATH_TESTSUITE, "name");
        // special handling due to how test suite name is saved in junit report... may need to revisit this one...
        result.testSuiteName = StringUtils.substringAfter(result.testSuiteName, ".");
        result.testCount = NumberUtils.toInt(XmlUtils.getAttributeValue(doc, XPATH_TESTSUITE, "tests"));
        result.failureCount += NumberUtils.toInt(XmlUtils.getAttributeValue(doc, XPATH_TESTSUITE, "failures"));
        result.failureCount += NumberUtils.toInt(XmlUtils.getAttributeValue(doc, XPATH_TESTSUITE, "errors"));
        result.executionTime = XmlUtils.getAttributeValue(doc, XPATH_TESTSUITE, "time");
        return result;
    }

    protected static List<String> parseErrors(String errors) {
        List<String> parsed = new ArrayList<>();

        String[] lines = StringUtils.split(errors, "\r\n");
        String currentError = "";
        for (String line : lines) {
            if (StringUtils.startsWith(line, "[")) {
                if (StringUtils.isNotBlank(currentError)) {
                    parsed.add(currentError);
                    currentError = "";
                }
            }
            currentError += line + "\n";
        }

        parsed.add(currentError);

        return parsed;
    }
}
