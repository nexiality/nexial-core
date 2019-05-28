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

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.internal.bind.DateTypeAdapter
import org.apache.commons.lang3.ArrayUtils
import org.apache.commons.lang3.exception.ExceptionUtils
import org.json.JSONObject
import org.springframework.http.HttpStatus

sealed class ServiceUtils {

    companion object {
        val gson: Gson = GsonBuilder().setPrettyPrinting()
            .disableHtmlEscaping()
            .disableInnerClassSerialization()
            .setLenient()
            .registerTypeAdapterFactory(DateTypeAdapter.FACTORY)
            .create()

        fun toJson(obj: Any): String = gson.toJson(obj)

        fun <T> fromJson(payload: JsonObject, type: Class<T>): T =
            if (payload.isJsonNull || payload.size() == 0) {
                type.newInstance()
            } else {
                gson.fromJson(payload, type)
            }

        fun toJSONObject(obj: Any): JSONObject = JSONObject(gson.toJson(obj))

        fun toSuccessReponse(message: String) = toJSONObject(SuccessResponse(message = message))

        fun toErrorReponse(message: String) = toJSONObject(ServiceRequestError(message = message))

        fun toErrorReponse(status: HttpStatus, message: String) =
            toJSONObject(ServiceRequestError(status = status, message = message))

        fun toErrorReponse(status: HttpStatus, ex: Throwable) = toJSONObject(
            ServiceRequestError(status = status,
                                message = ex.message!!,
                                debugMessage = ArrayUtils.toString(ExceptionUtils.getRootCauseStackTrace(ex))))
    }
}
