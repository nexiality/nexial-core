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

import org.nexial.core.NexialConst.NAMESPACE
import org.nexial.core.NexialConst.Project.USER_NEXIAL_HOME
import org.nexial.core.SystemVariables.registerSysVar
import java.io.File.separator

object Tn5250Helper {

    const val KEY_PRESSED = 401
    const val KEY_TYPED = 400
    const val KEY_RELEASED = 402
    const val DEF_TITLE_LINES = 3

    // https://www.ibm.com/support/knowledgecenter/ssw_ibm_i_74/apis/dsm1f.htm
    const val ATTR_GREEN_UNDERSCORE = 36
    const val ATTR_GREEN_READ_ONLY = 32

    const val REGEX_LABEL_START = "^[\\x00][0-9A-Za-z/ ]+$"

    // label that ends with ":"
    const val REGEX_LABEL = "[\\x00]([^:]+)\\s*:[\\x00]"

    // label that DOES NOT end with ":", but contains ". " or " ." and then ends with \0
    const val REGEX_LABEL2 = "[\\x00]([^:]+)[ |\\x00]\\.[\\x00]"

    // label that DOES NOT end with ":", but contains "." then ends with \0
    const val REGEX_LABEL3 = "[\\x00]([^:]+)\\. [\\x00]"

    private const val NS = NAMESPACE + "tn5250."
    val FILLER = registerSysVar(NS + "filler", " .")
    val USE_FIRST_TABLE = registerSysVar(NS + "useFirstTable", true)
    val SCAN_BROADCAST_TITLE = registerSysVar(NS + "detectBroadcastTitle")
    val SCAN_BROADCAST_TEXT = registerSysVar(NS + "detectBroadcastText")
    val DISMISS_BROADCAST = registerSysVar(NS + "dismissBroadcastMessage")

    init {
        System.setProperty("TN5250_CONFIG", USER_NEXIAL_HOME + separator + "tn5250")
    }
}