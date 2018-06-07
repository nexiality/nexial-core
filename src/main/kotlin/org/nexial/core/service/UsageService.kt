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

import org.nexial.core.IntegrationConfigException
import org.nexial.core.model.NexialEnv
import org.nexial.core.model.NexialEvent
import org.nexial.core.plugins.ws.WebServiceClient
import org.nexial.core.utils.ConsoleUtils
import java.io.IOException

class UsageService(val url: String) {
    var wsClient: WebServiceClient? = null

    fun send(usage: NexialEvent) = post(usage)

    fun send(nexialEnv: NexialEnv) = post(nexialEnv)

    private fun post(usage: NexialEvent) {
        if (wsClient == null) {
            throw IntegrationConfigException("web service client not ready")
        }

        try {
            // ws.invokeAsyncRequest(ws.toPostRequest(url, usage.toJson()), (response, error1) -> { ... });
            wsClient!!.post(url, usage.json())
        } catch (e: IOException) {
            ConsoleUtils.log(e.message)
        }
    }

    private fun post(usage: NexialEnv) {
        if (wsClient == null) {
            throw IntegrationConfigException("web service client not ready")
        }

        try {
            wsClient!!.post(url, usage.json())
        } catch (e: IOException) {
            ConsoleUtils.log(e.message)
        }
    }
}