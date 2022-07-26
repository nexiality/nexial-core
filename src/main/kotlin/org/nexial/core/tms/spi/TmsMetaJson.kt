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
package org.nexial.core.tms.spi

import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import org.nexial.commons.utils.FileUtil
import org.nexial.core.NexialConst.DEF_CHARSET
import org.nexial.core.NexialConst.GSON
import org.nexial.core.NexialConst.Project.DEF_LOC_ARTIFACT
import org.nexial.core.NexialConst.Project.DEF_REL_PROJ_META_JSON
import org.nexial.core.tms.model.TestFile
import org.nexial.core.tms.model.TmsMeta
import org.nexial.core.tms.model.TmsTestCase
import org.nexial.core.tms.model.TmsTestFile
import java.io.File

/**
 * Deal with operations relating to project.tms.json file present inside .meta directory of the project
 */
object TmsMetaJson {
    /**
     * Resolve the meta file and parse it based on the location of the test file passed in
     *
     * @param path the path of the test file
     * @return TmsMeta object containing the contents of json
     */
    @Throws(TmsException::class)
    fun retrieveMeta(path: String): TmsMeta {
        return try {
            val metaFile = resolveMetaFile(path)
            if (!FileUtil.isFileReadable(metaFile)) {
                throw TmsException("TMS meta json file is not detected in the project.")
            }
            val tmsMeta = GSON.fromJson(FileUtils.readFileToString(metaFile, DEF_CHARSET), TmsMeta::class.java)
                ?: throw TmsException("Could not read project TMS meta json file.")
            tmsMeta
        } catch (e: Exception) {
            throw TmsException("Unable to parse project TMS meta json file due to ${e.message}")
        }
    }

    /**
     * Update the [TmsMeta] instance by removing a [org.nexial.core.tms.model.TestFile] entry from the json and adding a new entry
     * and write in the json file
     *
     * @param testpath the path of the input file
     * @param file     the [TestFile] instance
     */
    @Throws(TmsException::class)
    fun updateMeta(testpath: String, file: TestFile?) {
        try {
            val meta = retrieveMeta(testpath)
            meta.removeFile(file!!)
            meta.addFile(file)
            FileUtils.writeStringToFile(resolveMetaFile(testpath), GSON.toJson(meta), DEF_CHARSET)
        } catch (e: Exception) {
            throw TmsException("Unable to write data into the $DEF_REL_PROJ_META_JSON file due to ${e.message}")
        }
    }

    /**
     * Retrieve the project id specified inside the tms meta json file
     *
     * @param filepath the path of the test file
     * @return the project id for the input file
     */
    @Throws(TmsException::class)
    fun retrieveProjectId(filepath: String): String {
        val meta = retrieveMeta(filepath)
        val projectId = meta.projectId
        if (StringUtils.isEmpty(projectId)) {
            throw TmsException("Project id is not detected inside project TMS meta file.")
        }
        return projectId
    }

    /**
     * Get relative path of the test file based on the absolute path passed in
     *
     * @param filepath the absolute path of the test file
     * @return the relative path
     */
    fun getRelativePath(filepath: String?): String = "$DEF_LOC_ARTIFACT/" +
            StringUtils.substringAfter(StringUtils.replace(filepath, "\\", "/"), "$DEF_LOC_ARTIFACT/")

    /**
     * Get the corresponding json entry for the test file pointed to the by the input file path from the project.tms.json file
     *
     * @param filepath the path of the nexial test file
     * @param subplan the subplan name in case the test file is a plan
     * @return TmsTestFile instance representing a json entry in the project.tms.json file
     */
    @Throws(TmsException::class)
    fun getJsonEntryForFile(filepath: String, subplan: String?): TmsTestFile {
        val meta = retrieveMeta(filepath)
        val metaFiles = meta.files
        val relativePath = getRelativePath(filepath)
        if (CollectionUtils.isEmpty(metaFiles)) {
            meta.files = mutableListOf()
        } else {
            metaFiles?.forEach { file ->
                if (StringUtils.isEmpty(subplan)) {
                    if (file.path == relativePath) { return TmsTestFile(meta.projectId, file) }
                } else {
                    if (file.path == relativePath && file.subplan == subplan) {
                        return TmsTestFile(meta.projectId, file)
                    }
                }
            }
        }
        return TmsTestFile(meta.projectId, null)
    }

    /**
     *
     * @param testCasesToPlanStep is [LinkedHashMap] of script step number to the list of [TmsTestCase]
     * @return [Map] of testcase name(scenario name) to the [TmsTestCase] cache(scenario worksheet)
     */
    fun retrieveCache(testCasesToPlanStep: LinkedHashMap<Int, List<TmsTestCase>>): MutableMap<String, String> {
        val testCases = mutableListOf<TmsTestCase>()
        val testCaseCache = mutableMapOf<String, String>()
        testCasesToPlanStep.forEach { (_, value) -> testCases.addAll(value) }

        testCases.forEach { testcase ->
            testCaseCache[StringUtils.substringBeforeLast(testcase.testCaseName, "/")] = testcase.cache!!
        }
        return testCaseCache
    }

    /**
     * Resolve the meta file based on the location of the Nexial test file
     *
     * @param path the path of the nexial test file
     * @return return the meta file
     */
    private fun resolveMetaFile(path: String) =
        File(StringUtils.substringBefore(path, DEF_LOC_ARTIFACT) + DEF_REL_PROJ_META_JSON)
}