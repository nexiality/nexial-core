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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.utils.TextUtils;

import static java.io.File.separator;
import static org.nexial.core.NexialConst.OPT_PROJECT_BASE;
import static org.nexial.core.NexialConst.Project.*;

/**
 * object representation of the standard Nexial project structure.
 */
public class TestProject {
    private static final Map<String, String> PROJECT_PROPERTIES = new LinkedHashMap<>();

    private File nexialHome;
    private String name;
    private String projectHome;
    private String artifactPath;
    private String scriptPath;
    private String dataPath;
    private String planPath;
    private String outPath;
    private boolean isStandardStructure;
    private String projectProps;

    public TestProject() { nexialHome = new File(System.getProperty(NEXIAL_HOME)); }

    public static TestProject newInstance(File inputFile, String relativePath) {
        String inputFullPath = inputFile.getAbsolutePath();

        // is the test script confined within the standard project structure (i.e. artifact/script/[...].xlsx)?
        String inputFileRelPath = relativePath + inputFile.getName();

        TestProject project = new TestProject();
        if (StringUtils.contains(inputFullPath, inputFileRelPath)) {
            // yes, standard project structure observed. proceed to resolve other directories
            project.setProjectHome(
                StringUtils.removeEnd(StringUtils.substringBefore(inputFullPath, inputFileRelPath), separator));
            project.isStandardStructure = true;
        } else {
            // is the input file a test plan?
            inputFileRelPath = DEF_REL_LOC_TEST_PLAN + inputFile.getName();
            if (StringUtils.contains(inputFullPath, inputFileRelPath)) {
                // yes, standard project structure observed. proceed to resolve other directories
                project.setProjectHome(
                    StringUtils.removeEnd(StringUtils.substringBefore(inputFullPath, inputFileRelPath), separator));
                project.isStandardStructure = true;
            }
        }

        return resolveStandardPaths(project);
    }

    public String getName() { return name; }

    public void setName(String name) { this.name = name; }

    public String getNexialHome() { return nexialHome.getAbsolutePath(); }

    public String getBinHome() {
        return StringUtils.appendIfMissing(nexialHome.getAbsolutePath(), separator) + NEXIAL_BIN_REL_PATH;
    }

    public String getProjectHome() { return projectHome; }

    public void setProjectHome(String projectHome) {
        this.projectHome = projectHome;

        // note that this setter is called PRIOR to spring.
        projectProps = StringUtils.appendIfMissing(this.projectHome, separator) + DEF_REL_PROJECT_PROPS;
        loadProjectProperties();
        PROJECT_PROPERTIES.put(OPT_PROJECT_BASE, this.projectHome);
    }

    public static boolean isProjectProperty(String name) { return PROJECT_PROPERTIES.containsKey(name); }

    public static String getProjectProperty(String name) { return PROJECT_PROPERTIES.get(name); }

    public static Set<String> listProjectPropertyKeys() { return PROJECT_PROPERTIES.keySet(); }

    public String getArtifactPath() { return artifactPath; }

    public void setArtifactPath(String artifactPath) { this.artifactPath = artifactPath; }

    public String getScriptPath() { return scriptPath; }

    public void setScriptPath(String scriptPath) { this.scriptPath = scriptPath; }

    public String getDataPath() { return dataPath; }

    public void setDataPath(String dataPath) { this.dataPath = dataPath; }

    public String getPlanPath() { return planPath; }

    public void setPlanPath(String planPath) { this.planPath = planPath; }

    public String getOutPath() { return outPath; }

    public void setOutPath(String outPath) { this.outPath = outPath; }

    public String getScreenCaptureDir() { return appendCapture(getOutPath()); }

    public String getLogsDir() { return appendLog(getOutPath()); }

    public boolean isStandardStructure() { return isStandardStructure; }

    public void setStandardStructure(boolean standardStructure) { isStandardStructure = standardStructure; }

    public String getProjectProps() { return projectProps; }

    public TestProject copy() {
        TestProject project = new TestProject();
        project.nexialHome = nexialHome;
        project.name = name;
        project.projectHome = projectHome;
        project.artifactPath = artifactPath;
        project.scriptPath = scriptPath;
        project.dataPath = dataPath;
        project.planPath = planPath;
        project.outPath = outPath;
        project.isStandardStructure = isStandardStructure;
        project.projectProps = projectProps;
        return project;
    }

    protected void loadProjectProperties() {
        Map<String, String> properties = TextUtils.loadProperties(projectProps);
        if (MapUtils.isNotEmpty(properties)) {
            PROJECT_PROPERTIES.clear();
            properties.forEach(PROJECT_PROPERTIES::put);
        }
    }
}
