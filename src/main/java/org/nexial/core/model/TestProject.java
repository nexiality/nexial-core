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

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.utils.FileUtil;
import org.nexial.commons.utils.TextUtils;
import org.nexial.commons.utils.TextUtils.DuplicateKeyStrategy;
import org.nexial.core.utils.ConsoleUtils;
import org.nexial.core.utils.ExecUtils;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;

import static java.io.File.separator;
import static org.nexial.core.NexialConst.Data.*;
import static org.nexial.core.NexialConst.OPT_PROJECT_BASE;
import static org.nexial.core.NexialConst.Project.*;
import static org.nexial.core.SystemVariables.getDefault;

/**
 * object representation of the standard Nexial project structure.
 */
public class TestProject {
    private static final Map<String, String> PROJECT_PROPERTIES = new LinkedHashMap<>();

    private String nexialHome;
    private String name;
    private String projectHome;
    private String artifactPath;
    private String scriptPath;
    private String dataPath;
    private String planPath;
    private String outPath;
    private boolean isStandardStructure;
    private String projectProps;
    private boolean hasProjectProps;
    private String boundProjectId;

    public TestProject() { nexialHome = new File(System.getProperty(NEXIAL_HOME)).getAbsolutePath(); }

    public static TestProject newInstance(File inputFile) {
        // this file could be a script or a plan
        String inputFullPath = inputFile.getAbsolutePath();

        TestProject project = new TestProject();
        // is the test script confined within the standard project structure (i.e. artifact/script/[...].xlsx)?
        if (containsWithin(inputFullPath, DEF_REL_LOC_TEST_SCRIPT)) {
            // yes, standard project structure observed. proceed to resolve other directories
            project.setProjectHome(
                StringUtils.removeEnd(StringUtils.substringBefore(inputFullPath, DEF_REL_LOC_TEST_SCRIPT), separator));
            project.isStandardStructure = true;
        } else {
            // is the input file a test plan?
            if (containsWithin(inputFullPath, DEF_REL_LOC_TEST_PLAN)) {
                // yes, standard project structure observed. proceed to resolve other directories
                project.setProjectHome(StringUtils.removeEnd(
                    StringUtils.substringBefore(inputFullPath, DEF_REL_LOC_TEST_PLAN), separator));
                project.isStandardStructure = true;
            }
        }

        return resolveStandardPaths(project);
    }

    public String getName() { return name; }

    public void setName(String name) { this.name = name; }

    public String getNexialHome() { return nexialHome; }

    public String getBinHome() {
        return StringUtils.appendIfMissing(nexialHome, separator) + NEXIAL_BIN_REL_PATH;
    }

    public String getProjectHome() { return projectHome; }

    public void setProjectHome(String projectHome) {
        this.projectHome = projectHome;
        // note that this setter is called PRIOR to spring.
        projectProps = deriveProjectProperties(this.projectHome);
        loadProjectProperties();
        PROJECT_PROPERTIES.put(OPT_PROJECT_BASE, this.projectHome);
    }

    @Nonnull
    public static String deriveProjectProperties(String projectHome) {
        String runId = ExecUtils.deriveRunId();
        String propBase = StringUtils.appendIfMissing(projectHome, separator) + DEF_REL_LOC_ARTIFACT;

        // (v3.6): support env-specific project.properties
        String env = System.getProperty(ENV_NAME, "");
        if (StringUtils.isNotBlank(env)) {
            // test that the target project.properties exists
            // ie. check for $PROJECT/artifact/project.$ENV.properties
            String envProjectProperties = propBase + StringUtils.replace(DEF_PROJECT_PROPS, ".", "." + env + ".");
            if (FileUtil.isFileReadable(envProjectProperties, 1)) {
                // found env-specific project.properties
                ConsoleUtils.log(runId, "loading environment-specific project.properties: %s", envProjectProperties);
                return envProjectProperties;
            }

            ConsoleUtils.error(runId, "Unable to read %s; fall back to project.properties", envProjectProperties);
        }

        // if env not specified, or target project.properties file cannot be found...
        return propBase + DEF_PROJECT_PROPS;
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

    public boolean isHasProjectProps() { return hasProjectProps; }

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

    public String getBoundProjectId() { return boundProjectId; }

    public void setBoundProjectId(String boundProjectId) { this.boundProjectId = boundProjectId; }

    protected void loadProjectProperties() {
        Map<String, String> properties = new LinkedHashMap<>();

        boolean trimKey = BooleanUtils.toBoolean(System.getProperty(PROJ_PROP_TRIM_KEY, getDefault(PROJ_PROP_TRIM_KEY)));
        DuplicateKeyStrategy dupStrategy = 
            DuplicateKeyStrategy.toStrategy(System.getProperty(DUP_PROJ_PROP_KEYS, getDefault(DUP_PROJ_PROP_KEYS)));

        // if user specified env-specific properties, then we'll load default `project.properties` first
        if (!StringUtils.endsWith(projectProps, DEF_PROJECT_PROPS)) {
            String propBase = StringUtils.appendIfMissing(projectHome, separator) + DEF_REL_LOC_ARTIFACT;
            String defaultProjectProperties = propBase + DEF_PROJECT_PROPS;
            Map<String, String> props = TextUtils.loadProperties(defaultProjectProperties, trimKey);
            if (MapUtils.isNotEmpty(props)) { properties.putAll(props); }
        }

        // override anything found in env-specific properties
        Map<String, String> props = TextUtils.loadProperties(projectProps, trimKey, dupStrategy);
        if (MapUtils.isNotEmpty(props)) { properties.putAll(props); }

        if (MapUtils.isNotEmpty(properties)) {
            PROJECT_PROPERTIES.clear();
            PROJECT_PROPERTIES.putAll(properties);
            hasProjectProps = MapUtils.isNotEmpty(PROJECT_PROPERTIES);
        }
    }

    private static boolean containsWithin(String path, String substring) {
        return path.indexOf(
            StringUtils.appendIfMissing(StringUtils.prependIfMissing(substring, separator), separator)) > 1;
    }
}
