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

package org.nexial.core.plugins.tn5250

import org.apache.commons.io.FileUtils
import org.nexial.commons.utils.EnvUtils
import org.nexial.core.NexialConst.DEF_FILE_ENCODING
import org.nexial.core.NexialConst.Project.USER_NEXIAL_HOME
import org.nexial.core.model.ServiceProfile
import java.io.File

class Tn5250ServiceProfile(name: String, profileData: Map<String, String>) : ServiceProfile(name, profileData) {
    val host = profileData.getOrElse("host") { throw IllegalArgumentException("Missing profile data: '${name}.host'") }
    val port = profileData.getOrDefault("port", "23").toInt()
    val codePage = profileData.getOrDefault("codePage", "Cp037")
    val display = assertValidDisplay(profileData.getOrDefault("displaySize", "24x80"))
    val deviceName = profileData.getOrDefault("deviceName", EnvUtils.getHostName())
    val windowWidth = "900"
    val windowHeight = "650"
    val titleLines = profileData.getOrDefault("titleLine", "3").toInt()
    val logInspection = profileData.getOrDefault("logInspection", "false").toBoolean()
    val inspectionLogFile = profileData["inspectionLogFile"]

    init {
        // write settings to tn5250 config
        FileUtils.write(File("${USER_NEXIAL_HOME}tn5250/sessions"),
                        "emul.restricted=\n" +
                        "emul.logLevel=2\n" +
                        "emul.view=-s $name \n" +
                        "emul.width=$windowWidth\n" +
                        "emul.height=$windowHeight\n" +
                        "emul.frameDefault=0,0,$windowWidth,$windowHeight\n" +
                        "emul.frame0=0,0,$windowWidth,$windowHeight\n" +
                        "emul.frame1=0,0,$windowWidth,$windowHeight\n" +
                        "emul.frame2=0,0,$windowWidth,$windowHeight\n" +
                        "$name=$host -p $port -cp $codePage -dn $deviceName -e -t -noembed -MDI -nc\n",
                        DEF_FILE_ENCODING
        )
    }

    fun toCmdLineArgs(): Array<String> = arrayOf("-s", name)

    companion object {
        private val VALID_DISPLAYS = listOf("24x80", "27x132")

        private fun assertValidDisplay(display: String): String {
            if (VALID_DISPLAYS.contains(display)) return display
            throw java.lang.IllegalArgumentException("Invalid display value: $display")
        }
    }
}
