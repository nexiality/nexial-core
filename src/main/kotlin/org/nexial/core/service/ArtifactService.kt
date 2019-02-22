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

package org.nexial.core.service

import com.google.gson.JsonObject
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import org.nexial.commons.spring.SpringUtils
import org.nexial.commons.utils.FileUtil
import org.nexial.core.NexialConst.Data.MIME_JSON
import org.nexial.core.NexialConst.Data.MIME_PLAIN
import org.nexial.core.excel.Excel
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod.POST
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController
import java.io.File
import java.io.FileNotFoundException
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy

@RestController("ArtifactService")
@RequestMapping(name = "ArtifactService",
                path = ["/artifact"],
                produces = [MIME_JSON],
                consumes = [MIME_JSON, MIME_PLAIN],
                method = [POST])
@ResponseBody
open class ArtifactService {
    private val logger = LoggerFactory.getLogger(this.javaClass)
    private val minExcelFileSize: Long = 5 * 1024

    @PostConstruct
    fun startup() {
    }

    @PreDestroy
    fun shutdown() {
        if (logger.isInfoEnabled) logger.info("${SpringUtils.getMappedUri(this)} shutting down...")
    }

    @RequestMapping(path = ["inspect"])
    fun inspect(@RequestBody payload: JsonObject): Any {
        val request = toRequest(payload)
        val path = request.path
        val returnList = ArrayList<String>()

        if (request.showFiles) {
            if (!FileUtil.isDirectoryReadable(path)) {
                throw IllegalArgumentException("path '$path' is not a readable directory")
            }

            FileUtils.listFiles(File(path), arrayOf("xlsx", "XLSX"), true).forEach { file ->
                if (!StringUtils.startsWith(file.name, "~")) returnList.add(file.absolutePath)
            }
        }

        if (request.showSheets) {
            if (!FileUtil.isFileReadable(path, minExcelFileSize)) {
                throw FileNotFoundException("path '$path' is not a readable file")
            }

            Excel(File(path)).getWorksheetsStartWith("").forEach { sheet ->
                if (!StringUtils.startsWith(sheet.name, "#")) returnList.add(sheet.name)
            }
        }

        returnList.sort()
        return returnList
    }

    @RequestMapping(path = ["open"])
    fun open(@RequestBody payload: JsonObject): Any {
        val request = toRequest(payload)
        val path = request.path

        if (!FileUtil.isFileReadable(path, minExcelFileSize)) {
            throw FileNotFoundException("path '$path' is not a readable file")
        }

        Excel.openExcel(File(path))
        return SuccessResponse(message = "ok")
    }

    private fun toRequest(payload: JsonObject): ArtifactRequest {
        if (logger.isDebugEnabled) logger.debug("payload received: " + payload)
        if (payload.isJsonNull || payload.size() == 0) throw NullPointerException("empty payload")

        val request = ServiceUtils.fromJson(payload,
                                            ArtifactRequest::class.java)
        if (logger.isDebugEnabled) logger.debug("request = $request")
        return request
    }
}

private data class ArtifactRequest(val path: String,
                                   val showFiles: Boolean = true,
                                   val showSheets: Boolean = false,
                                   val showVariables: Boolean = false)

