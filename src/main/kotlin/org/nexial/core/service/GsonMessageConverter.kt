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

package org.nexial.core.service

import com.google.gson.Gson
import com.google.gson.JsonElement
import org.springframework.lang.Nullable
import org.springframework.messaging.Message
import org.springframework.messaging.MessageHeaders
import org.springframework.messaging.converter.AbstractMessageConverter
import org.springframework.messaging.converter.ContentTypeResolver
import org.springframework.messaging.converter.DefaultContentTypeResolver
import org.springframework.messaging.converter.MessageConversionException
import org.springframework.util.MimeType
import org.springframework.util.MimeTypeUtils
import java.io.IOException
import java.io.Reader

class GsonMessageConverter : AbstractMessageConverter {
    lateinit var gson: Gson
    var resolver: DefaultContentTypeResolver = DefaultContentTypeResolver()

    constructor(supportedMimeType: MimeType) : super(supportedMimeType)
    constructor(supportedMimeTypes: Collection<MimeType>) : super(supportedMimeTypes)

    init {
        resolver.defaultMimeType = MimeTypeUtils.APPLICATION_JSON
    }

    override fun getContentTypeResolver(): ContentTypeResolver = resolver

    override fun supports(clazz: Class<*>) = true

    @Throws(MessageConversionException::class)
    @Nullable
    override fun convertFromInternal(message: Message<*>, targetClass: Class<*>, @Nullable conversionHint: Any?): Any? {
        val payload = message.payload
        try {
            return when (payload) {
                is ByteArray   -> gson.fromJson(String(payload), targetClass)
                is Reader      -> gson.fromJson(payload, targetClass)
                is JsonElement -> gson.fromJson(payload, targetClass)
                else           -> gson.fromJson(payload.toString(), targetClass)
            }
        } catch (ex: IOException) {
            throw MessageConversionException(message, "Could not read JSON: " + ex.message, ex)
        }
    }

    @Nullable
    override fun convertToInternal(payload: Any, @Nullable headers: MessageHeaders?, @Nullable conversionHint: Any?) =
            gson.toJson(payload).toByteArray()
}