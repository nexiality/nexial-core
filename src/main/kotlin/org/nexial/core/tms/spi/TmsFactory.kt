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
import org.nexial.core.NexialConst.Project.DEF_REL_PROJ_META_JSON
import org.nexial.core.NexialConst.SPRING_INTEGRATION_CONTEXT
import org.nexial.core.NexialConst.TMSSettings.*
import org.nexial.core.tms.TmsConst.CLASSPATH_NEXIAL_INIT_XML
import org.nexial.core.tms.TmsConst.CONFIG_DOCUMENTATION_MSG
import org.nexial.core.tms.TmsConst.TMS_SOURCE_MISSING_MSG
import org.nexial.core.tms.TmsConst.TMS_URL_MISSING_MSG
import org.springframework.context.support.ClassPathXmlApplicationContext

/**
 * Create TMSOperation instance from setup.jar configuration
 */
object TmsFactory {
    fun getTmsInstance(projectId: String): TMSOperation {
        if (StringUtils.isEmpty(projectId)) {
            throw TmsException("ProjectId is not defined in $DEF_REL_PROJ_META_JSON; $CONFIG_DOCUMENTATION_MSG")
        }

        ClassPathXmlApplicationContext(CLASSPATH_NEXIAL_INIT_XML)
        val source = System.getProperty(TMS_SOURCE) ?: throw TmsException(TMS_SOURCE_MISSING_MSG)
        val context = ClassPathXmlApplicationContext(SPRING_INTEGRATION_CONTEXT)
        return context.getBean("tms-$source") as TMSOperation
    }

    fun loadTmsData(): TMSAccessData {
        val source = System.getProperty(TMS_SOURCE)
        val url = System.getProperty(TMS_URL)
        if (StringUtils.isEmpty(source)) { throw TmsException(TMS_SOURCE_MISSING_MSG) }
        if (StringUtils.isEmpty(url)) { throw TmsException(TMS_URL_MISSING_MSG) }

        val username = System.getProperty(TMS_USERNAME)
        val password = System.getProperty(TMS_ACCESS_TOKEN)
            ?: System.getProperty(TMS_PASSWORD)
            ?: throw TmsException("TMS password and accessToken both can not empty; " +
                    "Please provide one of the $TMS_PASSWORD or $TMS_ACCESS_TOKEN. $CONFIG_DOCUMENTATION_MSG")
        return TMSAccessData(source, username, password, url)
    }
}
