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

package org.nexial.core.integration

import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang3.StringUtils
import org.nexial.core.ExecutionThread
import org.nexial.core.model.ExecutionContext
import java.io.File
import java.io.FileOutputStream
import java.util.*
import java.util.stream.Collectors

abstract class IntegrationHelper {

    val jiraDefectMeta = "${ExecutionThread.get().project.projectHome}/jiraDefects.properties"

    abstract fun createDefect(profile: String): String?
    abstract fun addLink(url: String, linkBody: String)
    abstract fun addComment(url: String, commentBody: String)
    abstract fun addLabels(url: String, labelsBody: String)

    fun getActions(context: ExecutionContext, profile: String): List<String> {
        var actions = mutableListOf<String>()
        if (!StringUtils.isEmpty(profile)) {
            val data = context.getStringData("$INTEGRATION.$profile.actions")
            actions = Arrays.stream(StringUtils.split(data, ",")).collect(Collectors.toList())!!
        }
        return actions
    }

    fun updateJiraDefectHistory(defectData: MutableList<Pair<String, String>>) {
        val defectMeta = Properties()
        if (CollectionUtils.isNotEmpty(defectData)) {
            defectData.forEach { pair ->
                val key = pair.first
                // avoid overwriting
                if (!defectMeta.containsKey(key)) {
                    defectMeta.setProperty(key, pair.second)
                }
            }
            var outputStream: FileOutputStream? = null
            try {
                outputStream = FileOutputStream(File(jiraDefectMeta))
                defectMeta.store(outputStream, "Nexial Defect History")
            } finally {
                outputStream?.run {
                    flush()
                    close()
                }
            }
        }
    }


}