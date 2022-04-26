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
package org.nexial.core.model

import org.apache.commons.lang3.StringUtils
import java.util.*

/**
 * lightweight version of the [TestStep], without references to POI objects
 */
data class StepDetails(var rowNum: Int,
                       var description: String,
                       var nestedMessages: LinkedList<StepMessage>,
                       var isPass: Boolean)

data class StepMessage(var message: String, var file: String) {
    val fileType = resolveFileType()

    private fun resolveFileType() = if (StringUtils.isNotEmpty(file)) {
        when (file.substringAfterLast('.').lowercase()) {
            "csv"                -> "csv"
            "pdf"                -> "pdf"
            "png", "jpg", "jpeg" -> "image"
            "mp4"                -> "video"
            "xls", "xlsx"        -> "excel"
            else                 -> "alt"
        }
    } else "alt"
}