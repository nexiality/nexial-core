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

package org.nexial.core.integration.connection

import org.apache.commons.lang3.StringUtils
import org.nexial.core.NexialConst.*
import org.nexial.core.NexialConst.Ws.*
import org.nexial.core.integration.INTEGRATION
import org.nexial.core.model.ExecutionContext
import org.nexial.core.plugins.ws.AsyncWebServiceClient
import org.nexial.core.plugins.ws.WebServiceClient

class ConnectionFactory {

    companion object {

        var context: ExecutionContext? = null
        fun getInstance(context: ExecutionContext): ConnectionFactory.Companion {
            this.context = context
            return this
        }

        fun getAsyncWsClient(profile: String): AsyncWebServiceClient {
            setWsData(profile)
            return AsyncWebServiceClient(context)
        }

        fun getWebServiceClient(profile: String): WebServiceClient {
            setWsData(profile)
            return WebServiceClient(context).disableContextConfiguration().configureAsQuiet();
        }

        private fun setWsData(profile: String) {
            val data = context!!.getDataByPrefix("$INTEGRATION.$profile")
            if (StringUtils.equalsIgnoreCase(profile, "Jira")) {
                context!!.setData(WS_BASIC_USER, data[".user"])
                context!!.setData(WS_BASIC_PWD, data[".password"])
            }
            if (StringUtils.equalsIgnoreCase(profile, "Slack")) {
                context!!.setData("slackCommentChannel", data[".channel"])
                context!!.setData(WS_REQ_HEADER_PREFIX + "Content-Type", WS_JSON_CONTENT_TYPE2)
                context!!.setData(WS_REQ_HEADER_PREFIX + "Authorization", data[".auth"])
            }
        }
    }
}