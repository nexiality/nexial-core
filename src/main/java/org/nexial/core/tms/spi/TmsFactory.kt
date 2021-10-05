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

import org.apache.commons.lang3.StringUtils
import org.nexial.core.NexialConst.TMSSettings.*
import org.nexial.core.tms.TMSOperation
import org.nexial.core.tms.model.TMSAccessData
import org.nexial.core.tms.spi.testrail.APIClient
import org.nexial.core.tms.spi.testrail.TestRailOperations
import org.springframework.context.support.ClassPathXmlApplicationContext

class TmsFactory {
    fun loadTmsData(): TMSAccessData {
        ClassPathXmlApplicationContext("classpath:/nexial-init.xml")
        val username = System.getProperty(TMS_USERNAME)
        val password = System.getProperty(TMS_PASSWORD)
        val url = System.getProperty(TMS_URL)
        val source = System.getProperty(TMS_SOURCE)
        val org = System.getProperty(TMS_ORG)
        return TMSAccessData(source, username, password, url, org)

    }

    fun getClient(data: TMSAccessData, urlSuffix: String = ""): APIClient {
        val url = StringUtils.appendIfMissing(data.url, "/") + urlSuffix
        val client = APIClient(url)
        client.user = data.user
        client.password = data.password
        return client
    }

    fun getTmsInstance(projectId: String): TMSOperation? {
        if (StringUtils.isEmpty(projectId)) return null

        val data = loadTmsData()
        return when (data.source) {
            "testrail" -> {
                //val client = getClient(data)
                TestRailOperations(projectId)
            }
            "azure"    -> {
                // check ready to use valid credentials
                val client = getClient(data, "${data.organisation}/$projectId/_apis/")
                // AzureDevopsOperation(projectId, client)
                null
            }
            else       -> null
        }
    }
}