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
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.nexial.commons.utils.DateUtility;
import org.nexial.commons.utils.EnvUtils;
import org.nexial.core.ExecutionThread;
import org.nexial.core.aws.NexialS3Helper;
import org.nexial.core.plugins.NexialCommand;
import org.nexial.core.utils.ExecutionLogger;
import org.nexial.core.variable.ExpressionProcessor;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static java.io.File.separator;
import static org.nexial.core.NexialConst.DEF_CHARSET;
import static org.nexial.core.NexialConst.Project.DEF_REL_LOC_TEST_SCRIPT;
import static org.nexial.core.NexialConst.Project.NEXIAL_HOME;

public class MockExecutionContext extends ExecutionContext {
    protected String runId;
    protected Map<String, NexialCommand> plugins = new HashMap<>();
    protected String projectHome;

    public MockExecutionContext() { this(false); }

    public MockExecutionContext(boolean withSpring) {
        super();

        try {
            hostname = StringUtils.upperCase(EnvUtils.getHostName());
        } catch (UnknownHostException e) {
            throw new RuntimeException("Unable to determine host name of current host: " + e.getMessage());
        }

        executionLogger = new ExecutionLogger(getRunId());
        runId = DateUtility.createTimestampString(System.currentTimeMillis());

        if (withSpring) {
            this.springContext = new ClassPathXmlApplicationContext("classpath:/nexial.xml");
            this.failfastCommands = springContext.getBean("failfastCommands", new ArrayList<String>().getClass());
            this.builtinFunctions = springContext.getBean("builtinFunctions", new HashMap<String, Object>().getClass());
            this.otc = springContext.getBean("otc", NexialS3Helper.class);
        }

        expression = new ExpressionProcessor(this);

        try {
            newProject();
        } catch (IOException e) {
            throw new RuntimeException("Unable to ceate mock test script and project structure: " + e.getMessage(), e);
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

    @Override
    public String getRunId() { return runId; }

    @Override
    public String getId() { return "[" + getRunId() + "][" + this.getClass().getSimpleName() + "]"; }

    @Override
    public NexialCommand findPlugin(String target) { return plugins.get(target); }

    public void newProject() throws IOException {
        String nexialHome = System.getProperty(NEXIAL_HOME, "/Users/ml093043/projects/nexial/nexial-core");
        System.setProperty(NEXIAL_HOME, nexialHome);

        projectHome = SystemUtils.getJavaIoTmpDir().getAbsolutePath() + separator
                      + "_nexial_" + RandomStringUtils.randomAlphabetic(5) + separator;
        String tetsScriptPath = projectHome + DEF_REL_LOC_TEST_SCRIPT + "temp.xlsx";
        File testScript = new File(tetsScriptPath);
        FileUtils.writeStringToFile(testScript, RandomStringUtils.random(100), DEF_CHARSET);
        project = TestProject.newInstance(testScript, DEF_REL_LOC_TEST_SCRIPT);
        project.setProjectHome(projectHome);
        setTestProject(project);
    }

    public void cleanProject() {
        if (projectHome != null) { FileUtils.deleteQuietly(new File(projectHome)); }
    }
}
