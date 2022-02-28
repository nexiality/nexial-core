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

package org.nexial.core.model;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.utils.EnvUtils;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.ExecutionThread;
import org.nexial.core.aws.NexialS3Helper;
import org.nexial.core.logs.ExecutionLogger;
import org.nexial.core.plugins.NexialCommand;
import org.nexial.core.plugins.db.LocalDbCommand;
import org.nexial.core.plugins.web.Browser;
import org.nexial.core.utils.ExecUtils;
import org.nexial.core.variable.ExpressionProcessor;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static java.io.File.separator;
import static org.nexial.core.NexialConst.*;
import static org.nexial.core.NexialConst.Data.TEST_LOG_PATH;
import static org.nexial.core.NexialConst.Project.DEF_REL_LOC_TEST_SCRIPT;
import static org.nexial.core.NexialConst.Project.NEXIAL_HOME;
import static org.nexial.core.utils.ExecUtils.createUniqueTempDir;

public class MockExecutionContext extends ExecutionContext {
    protected String runId;
    protected Map<String, NexialCommand> plugins = new HashMap<>();
    protected String projectHome;
    public Browser mockBrowser;
    public LocalDbCommand localdb;
    public String result = "";

    public MockExecutionContext() { this(false); }

    public MockExecutionContext(boolean withSpring) {
        super();

        hostname = StringUtils.upperCase(EnvUtils.getHostName());
        runId = ExecUtils.createTimestampString(System.currentTimeMillis());
        System.setProperty(TEST_LOG_PATH, TEMP);
        System.setProperty(OPT_OUT_DIR, TEMP);
        executionLogger = new ExecutionLogger(this);

        if (withSpring) {
            springContext = new ClassPathXmlApplicationContext("classpath:/nexial.xml");
            otc = springContext.getBean("otc", NexialS3Helper.class);
            localdb = springContext.getBean("localdb", LocalDbCommand.class);
            mockBrowser = springContext.getBean("browserTemplate", Browser.class);
            failfastCommands = springContext.getBean("failfastCommands", new ArrayList<String>().getClass());
            builtinFunctions = springContext.getBean("builtinFunctions", new HashMap<String, Object>().getClass());
            readOnlyVars = springContext.getBean("readOnlyVars", new ArrayList<String>().getClass());
            defaultContextProps = springContext.getBean("defaultContextProps", new HashMap<String, String>().getClass());
            referenceDataForExecution =
                TextUtils.toList(defaultContextProps.get("nexial.referenceDataForExecution"), ",", true);
            webdriverHelperConfig =
                springContext.getBean("webdriverHelperConfig", new HashMap<BrowserType, String>().getClass());
            dbdriverHelperConfig =
                springContext.getBean("dbdriverHelperConfig", new HashMap<String, String>().getClass());
        } else {
            readOnlyVars = Arrays.asList("nexial.runID", "nexial.runID.prefix", "nexial.iterationEnded",
                                         "nexial.spreadsheet.program", "nexial.lastScreenshot", "nexial.lastOutcome",
                                         "nexial.minExecSuccessRate", "nexial.screenRecorder", "nexial.scope.iteration",
                                         "nexial.scope.fallbackToPrevious", "nexial.scope.currentIteration",
                                         "nexial.scope.lastIteration", "nexial.external.output",
                                         "nexial.browser.windowSize", "nexial.delayBrowser",
                                         "nexial.browser.ie.requireWindowFocus", "nexial.lastAlertText",
                                         "nexial.ignoreBrowserAlert", "nexial.lastAlertText",
                                         "nexial.browser.incognito", "nexial.browserstack.automatekey",
                                         "nexial.browserstack.username", "nexial.browserstack.browser",
                                         "nexial.browserstack.browser.version", "nexial.browserstack.debug",
                                         "nexial.browserstack.resolution", "nexial.browserstack.app.buildnumber",
                                         "nexial.browserstack.enablelocal", "nexial.browserstack.os",
                                         "nexial.browserstack.os.version", "nexial.browser.safari.cleanSession",
                                         "nexial.browser.safari.useTechPreview", "nexial.forceIE32",
                                         "webdriver.ie.driver", "webdriver.ie.driver.loglevel",
                                         "webdriver.ie.driver.logfile", "webdriver.ie.driver.silent",
                                         "file.separator", "java.home", "java.io.tmpdir", "java.version",
                                         "line.separator", "os.arch", "os.name", "os.version", "user.country",
                                         "user.dir", "user.home", "user.language", "user.name", "user.timezone");
        }

        expression = new ExpressionProcessor(this);

        try {
            newProject();
        } catch (IOException e) {
            throw new RuntimeException("Unable to create mock test script and project structure: " + e.getMessage(), e);
        }

        ExecutionThread.set(this);
    }

    public void addPlugin(String name, NexialCommand plugin) {
        plugin.init(this);
        plugins.put(name, plugin);
    }

    public void setTestProject(TestProject project) {
        if (this.execDef == null) { this.execDef = new ExecutionDefinition(); }
        this.execDef.setProject(project);
    }

    public void setExecDef(ExecutionDefinition execDef) {
        this.execDef = execDef;
        this.project = adjustPath(execDef);
    }

    @Override
    public String getRunId() { return runId; }

    @Override
    public String getId() { return "[" + getRunId() + "][" + this.getClass().getSimpleName() + "]"; }

    @Override
    public NexialCommand findPlugin(String target) { return plugins.get(target); }

    public void newProject() throws IOException {
        String nexialHome = System.getProperty(NEXIAL_HOME, "/NON_EXISTING_PATH/nexial");
        System.setProperty(NEXIAL_HOME, nexialHome);

        projectHome = createUniqueTempDir().getAbsolutePath() + separator;
        File testScript = new File(projectHome + DEF_REL_LOC_TEST_SCRIPT + "temp.xlsx");
        FileUtils.writeStringToFile(testScript, RandomStringUtils.random(100), DEF_CHARSET);
        project = TestProject.newInstance(testScript);
        project.setProjectHome(projectHome);
        setTestProject(project);
    }

    public void cleanProject() {
        if (projectHome != null) { FileUtils.deleteQuietly(new File(projectHome)); }
    }
}
