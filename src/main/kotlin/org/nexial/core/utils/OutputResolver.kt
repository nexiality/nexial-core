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
 */

package org.nexial.core.utils

import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.RandomStringUtils
import org.apache.commons.lang3.StringUtils
import org.nexial.commons.utils.FileUtil
import org.nexial.commons.utils.ResourceUtils
import org.nexial.core.NexialConst.TEMP
import org.nexial.core.model.ExecutionContext
import org.nexial.core.plugins.ws.WsCommand
import java.io.File

/**
 * Resolve "data" either as is or as a reference to local file or URL
 * @property data String the "data" in question (to resolve)
 * @property context ExecutionContext? must not be null if `replaceTokens` is `true`
 * @property resolveAsFile Boolean `true` if "data" should be tried as an fully qualified path
 * @property resolveUrl Boolean `true` if "data" should be tried as an URL
 * @property replaceTokens Boolean `true` if any data variable in the resolved content should be substituted. `context` must NOT be null
 * @property asBinary Boolean `true` if content content is to be treated as binary
 * @property compact Boolean `true` if content should be leading/trailing trimmed prior to return. Assume that the resolved content is TEXT.
 * @constructor
 */
class OutputResolver(val data: String?,
                     val context: ExecutionContext?,
                     val resolveAsFile: Boolean = true,
                     val resolveUrl: Boolean = false,
                     val replaceTokens: Boolean = true,
                     val asBinary: Boolean = false,
                     val compact: Boolean = false) {

    val content: String
    val bytes: ByteArray

    /**
     * convenience with `resolveAsFile` set to true, `replaceTokens` set to true, `asBinary` set to false and
     * `compact` set to false
     * @param data String?
     * @param context ExecutionContext
     * @constructor
     */
    constructor(data: String?, context: ExecutionContext) :
        this(data, context, true, context.isResolveTextAsURL, !context.isResolveTextAsIs, false, false)

    /**
     * convenience with `resolveAsFile` set to true, `asBinary` set to false and `compact` set to false.
     * @param data String?
     * @param context ExecutionContext
     * @param replaceTokens: Boolean
     * @constructor
     */
    constructor(data: String?, context: ExecutionContext, replaceTokens: Boolean = false) :
        this(data, context, true, context.isResolveTextAsURL, replaceTokens, false, false)

    constructor(data: String?, context: ExecutionContext, asBinary: Boolean = false, compact: Boolean = false) :
        this(data, context, true, context.isResolveTextAsURL, !context.isResolveTextAsIs, asBinary, compact)

    init {
        if (data == null || StringUtils.isBlank(data)) {
            content = StringUtils.defaultString(data, "")
            bytes = content.toByteArray()
        } else if (context != null && context.isNullOrEmptyOrBlankValue(data)) {
            content = StringUtils.defaultString(context.replaceTokens(data), "")
            bytes = content.toByteArray()
        } else {
            // could this be file? or could this be URL?
            val rawBytes = if (resolveAsFile && isContentReferencedAsFile(data)) {
                FileUtils.readFileToByteArray(File(data))
            } else if (resolveUrl && ResourceUtils.isWebResource(data)) {
                WsCommand.resolveWebContentBytes(data)
            } else {
                data.toByteArray()
            }

            if (asBinary) {
                content = String(rawBytes)
                bytes = rawBytes
            } else {
                content = String(rawBytes)
                    .let { if (replaceTokens && context != null) context.replaceTokens(it) else it }
                    .let { if (compact) it.trim() else it }
                bytes = content.toByteArray()
            }
        }
    }

    companion object {
        /** we can't have NL or CR or TAB character in filename  */
        @JvmStatic
        fun isContentReferencedAsFile(data: String, context: ExecutionContext?) =
            if (context != null && context.isNullOrEmptyOrBlankValue(data)) false
            else isContentReferencedAsFile(data)

        @JvmStatic
        fun isContentReferencedAsFile(data: String) =
            FileUtil.isSuitableAsPath(data) && File(data).isFile && File(data).canRead()

        @JvmStatic
        fun resolveFile(path: String): File? {
            if (StringUtils.isBlank(path)) return null

            if (ResourceUtils.isWebResource(path)) {
                val filename = RandomStringUtils.randomAlphabetic(5) + "_" + StringUtils.substringAfterLast(path, "/")
                return WsCommand.saveWebContent(path, File(TEMP, filename))
            }

            if (FileUtil.isFileReadable(path, 1)) return File(path)

            return null
        }
    }
}