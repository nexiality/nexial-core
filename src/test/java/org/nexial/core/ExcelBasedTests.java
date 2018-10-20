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
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;

import org.nexial.commons.utils.TextUtils;
import org.nexial.core.model.ExecutionSummary;

import static org.nexial.core.NexialConst.Data.FAIL_FAST;
import static org.nexial.core.NexialConst.*;
import static org.nexial.core.model.ExecutionSummary.ExecutionLevel.ITERATION;
import static java.io.File.separator;

public abstract class ExcelBasedTests {
    protected String projectBase;
    protected String resourcePath = "unittesting/artifact/script/";
    protected String dataResourcePath = "unittesting/artifact/data/";
    protected String planResourcePath = "unittesting/artifact/plan/";
    protected Nexial nexial;

    public static class TestOutcomeStats {
        private static final TestOutcomeStats ALL_PASSED = new TestOutcomeStats(0, -1);
        private static final TestOutcomeStats ALL_FAILED = new TestOutcomeStats(-1, 0);
        private int failCount;
        private int passCount;
        private int unaccounted;
        private String iteration;
        private String scenario;

        public TestOutcomeStats(int failCount, int passCount) {
            this.failCount = failCount;
            this.passCount = passCount;
        }

        public static TestOutcomeStats allPassed() { return ALL_PASSED; }

        public static TestOutcomeStats allFailed() { return ALL_FAILED; }

        public int getUnaccounted() { return unaccounted; }

        public void setUnaccounted(int unaccounted) { this.unaccounted = unaccounted; }

        public String getIteration() { return iteration; }

        public void setIteration(String iteration) { this.iteration = iteration; }

        public String getScenario() { return scenario; }

        public void setScenario(String scenario) { this.scenario = scenario; }
    }

    public class ExcelBasedTestBuilder {
        private String script;
        private List<String> scenarios;
        private String dataFile;
        private List<String> dataSheets;
        private String plan;

        public ExcelBasedTestBuilder setScript(String script) {
            if (StringUtils.isNotBlank(plan)) { throw new RuntimeException("script mixed with plan?"); }
            this.script = script;
            return this;
        }

        public ExcelBasedTestBuilder setScenarios(List<String> scenarios) {
            if (StringUtils.isNotBlank(plan)) { throw new RuntimeException("scenarios mixed with plan?"); }
            this.scenarios = scenarios;
            return this;
        }

        public ExcelBasedTestBuilder setDataFile(String dataFile) {
            if (StringUtils.isNotBlank(plan)) { throw new RuntimeException("data file mixed with plan?"); }
            this.dataFile = dataFile;
            return this;
        }

        public ExcelBasedTestBuilder setDataSheets(List<String> dataSheets) {
            if (StringUtils.isNotBlank(plan)) { throw new RuntimeException("datasheets mixed with plan?"); }
            this.dataSheets = dataSheets;
            return this;
        }

        public ExcelBasedTestBuilder setPlan(String plan) {
            if (StringUtils.isNotBlank(script)) { throw new RuntimeException("plan mixed with script?"); }
            if (CollectionUtils.isNotEmpty(scenarios)) { throw new RuntimeException("plan mixed with scenarios?"); }
            if (StringUtils.isNotBlank(dataFile)) { throw new RuntimeException("plan mixed with data file?"); }
            if (CollectionUtils.isNotEmpty(dataSheets)) { throw new RuntimeException("plan mixed with datasheets?"); }
            this.plan = plan;
            return this;
        }

        public ExecutionSummary execute() throws Exception {
            // is plan or script?
            if (StringUtils.isNotBlank(plan)) { return testPlanViaExcel(plan); }

            // script
            if (StringUtils.isBlank(script)) { throw new RuntimeException("script not specified!"); }

            File script = resolveFile(resourcePath + this.script);
            if (script == null) { Assert.fail("The required script does not exist"); }

            // e.g. script = /workspace/nexial-core.dist/build/resources/test/showcase/artifact/script/base-showcase.xlsx
            // so classesRoot should be the same as project base, which would be
            // /workspace/nexial-core.dist/build/resources/test/showcase
            NexialTestUtils.setupCommonProps(script, script.getParentFile().getParentFile().getParent());
            System.clearProperty(FAIL_FAST);

            System.out.println();
            System.out.println();
            System.out.println("script = " + script);

            // building arguments...
            List<String> arguments = new ArrayList<>();
            arguments.add("-script");
            arguments.add(script.getAbsolutePath());

            // scenario
            if (CollectionUtils.isNotEmpty(scenarios)) {
                System.out.println("scenarios = " + scenarios);
                arguments.add("-scenario");
                arguments.add(TextUtils.toString(scenarios, ","));
            }

            // data file
            if (StringUtils.isNotBlank(dataFile)) {
                File dataFile = resolveFile(dataResourcePath + this.dataFile);
                if (dataFile == null) { Assert.fail("The specified data file does not exists"); }

                System.out.println("dataFile = " + dataFile);
                arguments.add("-data");
                arguments.add(dataFile.getAbsolutePath());
            }

            // data sheets
            if (CollectionUtils.isNotEmpty(dataSheets)) {
                System.out.println("dataSheets = " + dataSheets);
                arguments.add("-datasheets");
                arguments.add(TextUtils.toString(dataSheets, ","));
            }

            Nexial main = getNexial();
            main.init(arguments.toArray(new String[arguments.size()]));

            try {
                return main.execute();
            } finally {
                if (ShutdownAdvisor.mustForcefullyTerminate()) { ShutdownAdvisor.forcefullyTerminate(); }
            }
        }
    }

    @BeforeClass
    public static void prep() {
        // NexialTestUtils.setupCommonProps();
    }

    @Before
    public void init() throws Exception {
        File projectRoot = resolveFile("showcase");
        if (projectRoot == null) { Assert.fail("Unable to determine project root"); }

        projectBase = projectRoot.getAbsolutePath();

        File outputBase = new File(StringUtils.appendIfMissing(projectBase, separator) + "output");
        FileUtils.deleteQuietly(outputBase);
        FileUtils.forceMkdir(outputBase);
    }

    @After
    public void cleanup() {
        System.clearProperty(OUTPUT_TO_CLOUD);
        System.clearProperty(OPT_RUN_ID);
        System.clearProperty(OPT_RUN_ID_PREFIX);
    }

    protected ExecutionSummary testViaExcel(String scriptFile) throws Exception {
        return testViaExcel(scriptFile, new String[]{});
    }

    protected ExecutionSummary testViaExcel(String scriptFile, String... scenarios) throws Exception {
        File script = resolveFile(resourcePath + scriptFile);
        if (script == null) { Assert.fail("The required script does not exist"); }

        // e.g. script = /workspace/nexial-core.dist/build/resources/test/showcase/artifact/script/base-showcase.xlsx
        // so classesRoot should be the same as project base, which would be
        // /workspace/nexial-core.dist/build/resources/test/showcase
        NexialTestUtils.setupCommonProps(script, script.getParentFile().getParentFile().getParent());
        System.setProperty(FAIL_FAST, "false");

        System.out.println();
        System.out.println();
        System.out.println("script = " + script);

        Nexial main = getNexial();
        if (ArrayUtils.isEmpty(scenarios)) {
            main.init(new String[]{"-script", script.getAbsolutePath()});
        } else {
            main.init(new String[]{"-script", script.getAbsolutePath(),
                                   "-scenario", TextUtils.toString(scenarios, ",", null, null)});
        }

        try {
            return main.execute();
        } finally {
            if (ShutdownAdvisor.mustForcefullyTerminate()) { ShutdownAdvisor.forcefullyTerminate(); }
        }
    }

    @NotNull
    private Nexial getNexial() {
        if (nexial == null) { nexial = new Nexial(); }
        return nexial;
    }

    protected ExecutionSummary testPlanViaExcel(String planFile) throws Exception {
        File plan = resolveFile(planResourcePath + planFile);
        if (plan == null) { Assert.fail("The required plan does not exist"); }

        // e.g. plan = /workspace/nexial-core.dist/build/resources/test/showcase/artifact/plan/myplan.xlsx
        // so classesRoot should be the same as project base, which would be
        // /workspace/nexial-core.dist/build/resources/test/showcase
        NexialTestUtils.setupCommonProps(plan, plan.getParentFile().getParentFile().getParent());
        System.clearProperty(FAIL_FAST);

        System.out.println();
        System.out.println();
        System.out.println("plan = " + plan);

        Nexial main = getNexial();
        main.init(new String[]{"-plan", plan.getAbsolutePath()});

        try {
            return main.execute();
        } finally {
            if (ShutdownAdvisor.mustForcefullyTerminate()) { ShutdownAdvisor.forcefullyTerminate(); }
        }
    }

    protected File resolveFile(String resource) {
        if (StringUtils.isBlank(resource)) { return null; }

        URL classpathResource = this.getClass().getResource(StringUtils.prependIfMissing(resource, "/"));
        if (classpathResource == null) { return null; }

        String path = classpathResource.getFile();
        if (StringUtils.isBlank(path)) { return null; }

        return new File(path);
    }

    protected void assertPassFail(ExecutionSummary testSummary, String testScenario, TestOutcomeStats expected) {
        expected.setIteration(testSummary.getName());
        expected.setScenario(testScenario);
        compare(expected, getActualStats(testSummary, testScenario));
    }

    protected void assertScenarioNotExecuted(ExecutionSummary testSummary, String testScenario) {
        List<ExecutionSummary> scenarios = retrieveExecutedScenarios(testSummary);

        for (ExecutionSummary summary : scenarios) {
            if (summary == null || StringUtils.isEmpty(summary.getName())) { continue; }
            if (StringUtils.equals(summary.getName(), testScenario)) {
                // failed!
                Assert.fail("test scenario '" + testScenario + "' NOT expected to be executed but was: " + summary);
            }
        }

        // passed!
    }

    protected TestOutcomeStats getActualStats(ExecutionSummary testSummary, String testScenario) {
        String currentIteration = testSummary.getName();
        List<ExecutionSummary> scenarios = retrieveExecutedScenarios(testSummary);

        for (ExecutionSummary summary : scenarios) {
            if (summary != null && StringUtils.equals(summary.getName(), testScenario)) {
                int failCount = summary.getFailCount();
                int passCount = summary.getPassCount();

                TestOutcomeStats stats = new TestOutcomeStats(failCount, passCount);
                stats.setUnaccounted(summary.getTotalSteps() - passCount - failCount);
                return stats;
            }
        }

        Assert.fail("Cannot find result for Test Scenario '" + testScenario + "' for Iteration " + currentIteration);
        return null;
    }

    protected void compare(TestOutcomeStats expected, TestOutcomeStats actual) {
        String msgPrefix = "[" + expected.getIteration() + "][" + expected.getScenario() + "] ";
        Assert.assertEquals(msgPrefix + "Expects fail count to be " + expected.failCount,
                            expected.failCount, actual.failCount);

        if (expected.passCount == -1) {
            Assert.assertTrue(msgPrefix + "Expects all steps to pass",
                              actual.failCount == 0 && actual.getUnaccounted() == 0);
        } else {
            Assert.assertEquals(msgPrefix + "Expects pass count to be " + expected.passCount,
                                expected.passCount, actual.passCount);
        }
    }

    protected List<ExecutionSummary> retrieveExecutedScenarios(ExecutionSummary testSummary) {
        List<ExecutionSummary> scenarios;
        if (testSummary.getExecutionLevel() == ITERATION) {
            scenarios = testSummary.getNestedExecutions();
        } else {
            // assumes first iteration
            scenarios = testSummary.getNestedExecutions().get(0).getNestedExecutions().get(0).getNestedExecutions();
        }
        return scenarios;
    }
}
