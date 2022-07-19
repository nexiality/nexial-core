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

package org.nexial.core.tms.spi;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.utils.FileUtil;
import org.nexial.core.tms.model.TestFile;
import org.nexial.core.tms.model.TmsMeta;
import org.nexial.core.tms.model.TmsTestCase;
import org.nexial.core.tms.model.TmsTestFile;
import org.nexial.core.utils.ConsoleUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.nexial.core.NexialConst.DEF_CHARSET;
import static org.nexial.core.NexialConst.GSON;
import static org.nexial.core.NexialConst.Project.DEF_LOC_ARTIFACT;
import static org.nexial.core.NexialConst.Project.DEF_REL_PROJ_META_JSON;

/**
 * Deal with operations relating to project.tms.json file present inside .meta directory of the project
 */
public class TmsMetaJson {

    /**
     * Resolve the meta file and parse it based on the location of the test file passed in
     *
     * @param path the path of the test file
     * @return TmsMeta object containing the contents of json
     */
    public static TmsMeta retrieveMeta(String path) throws TmsException {
        File metaFile = resolveMetaFile(path);
        if (!FileUtil.isFileReadable(metaFile)) {
          /*  ConsoleUtils.error("TMS meta json file is not detected in the project.");
            System.exit(-1);*/
            throw new TmsException("TMS meta json file is not detected in the project.");
        }
        TmsMeta tmsMeta = null;
        try {
            tmsMeta = GSON.fromJson(FileUtils.readFileToString(metaFile, DEF_CHARSET), TmsMeta.class);
            if (tmsMeta == null) {
                /*ConsoleUtils.error("Could not read project TMS meta json file.");
                System.exit(-1);*/
                throw new TmsException("Could not read project TMS meta json file.");

            }
        } catch (Exception e) {
           /* ConsoleUtils.error("Unable to parse project TMS meta json file: " + e.getMessage());
            System.exit(-1);*/
            throw new TmsException("Unable to parse project TMS meta json file:"  + e.getMessage());
        }

        return tmsMeta;
    }

    /**
     * Update the {@link TmsMeta} instance by removing a {@link org.nexial.core.tms.model.TestFile} entry from the json and adding a new entry
     * and write in the json file
     *
     * @param testpath the path of the input file
     * @param file     the {@link TestFile} instance
     */
    public static void updateMeta(String testpath, TestFile file) throws TmsException {
        TmsMeta meta = retrieveMeta(testpath);
        meta.removeFile(file);
        meta.addFile(file);
        try {
            FileUtils.writeStringToFile(resolveMetaFile(testpath), GSON.toJson(meta), DEF_CHARSET);
        } catch (Exception e) {
           /* ConsoleUtils.error("Unable to write data into the " + DEF_REL_PROJ_META_JSON + " file: " + e.getMessage());
            System.exit(-1);*/
            throw new TmsException("Unable to write data into the " + DEF_REL_PROJ_META_JSON + " file: " + e.getMessage());

        }
    }

    /**
     * Retrieve the project id specified inside the tms meta json file
     *
     * @param filepath the path of the test file
     * @return the project id for the input file
     */
    public static String retrieveProjectId(String filepath) throws TmsException {
        TmsMeta meta = retrieveMeta(filepath);
        String projectId = meta.getProjectId();
        if (StringUtils.isEmpty(projectId)) {
            /*ConsoleUtils.error("Project id is not detected inside project TMS meta file.");
            System.exit(-1);*/
            throw new TmsException("Project id is not detected inside project TMS meta file.");

        }
        return meta.getProjectId();
    }

    /**
     * Get relative path of the test file based on the absolute path passed in
     *
     * @param filepath the absolute path of the test file
     * @return the relative path
     */
    public static String getRelativePath(String filepath) {
        return DEF_LOC_ARTIFACT + "/" + StringUtils.substringAfter(
                StringUtils.replace(filepath, "\\", "/"), DEF_LOC_ARTIFACT + "/");
    }

    /**
     * Get the corresponding json entry for the test file pointed to the by the input file path from the project.tms.json file
     *
     * @param filepath the path of the nexial test file
     * @param subplan the subplan name in case the test file is a plan
     * @return TmsTestFile instance representing a json entry in the project.tms.json file
     */
    public static TmsTestFile getJsonEntryForFile(String filepath, String subplan) throws TmsException {
        TmsMeta meta = retrieveMeta(filepath);
        List<TestFile> metaFiles = meta.getFiles();
        if (CollectionUtils.isEmpty(metaFiles)) {
            meta.setFiles(new ArrayList<>());
        } else {
            for (TestFile file : metaFiles) {
                if (StringUtils.isEmpty(subplan)) {
                    if (StringUtils.equals(file.getPath(), getRelativePath(filepath))) {
                        return new TmsTestFile(meta.getProjectId(), file);
                    }
                } else {
                    if (StringUtils.equals(file.getPath(), getRelativePath(filepath)) &&
                        StringUtils.equals(file.getSubplan(), subplan)) {
                        return new TmsTestFile(meta.getProjectId(), file);
                    }
                }
            }
        }
        return new TmsTestFile(meta.getProjectId(), null);
    }

    /**
     *
     * @param testCasesToPlanStep is {@link LinkedHashMap} of script step number to the list of {@link TmsTestCase}
     * @return {@link Map} of testcase name(scenario name) to the {@link TmsTestCase} cache(scenario worksheet)
    */
    public static Map<String, String> retrieveCache(LinkedHashMap<Integer, List<TmsTestCase>> testCasesToPlanStep) {
        List<TmsTestCase> testCases = new ArrayList<>();
        Map<String, String> testCaseCache = new LinkedHashMap<>();

        testCasesToPlanStep.forEach((key, value) -> testCases.addAll(value));
        testCases.forEach(testcase -> testCaseCache
                .put(StringUtils.substringBeforeLast(testcase.getTestCaseName(), "/"),
                        testcase.getCache()));
        return testCaseCache;
    }

    /**
     * Resolve the meta file based on the location of the Nexial test file
     *
     * @param path the path of the nexial test file
     * @return return the meta file
     */
    private static File resolveMetaFile(String path) {
        return new File(StringUtils.substringBefore(path, DEF_LOC_ARTIFACT) + DEF_REL_PROJ_META_JSON);
    }
}
