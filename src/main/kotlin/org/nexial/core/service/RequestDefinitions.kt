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

import org.nexial.core.tools.MacroUpdater
import org.nexial.core.tools.UpdateLog

/**
 * generic success response
 * @property timestamp Long
 * @property message String
 * @constructor
 */
data class SuccessResponse(val timestamp: Long = System.currentTimeMillis(), val message: String)

/**
 * corresponds to `nexial` batch file
 * @property script String
 * @property scenarios List<String>
 * @property dataFile String
 * @property dataSheets List<String>
 * @property output String
 * @property overrides MutableMap<String, String>
 * @property interactive Boolean
 * @constructor
 */
data class ExecuteScriptRequest(val script: String,
                                val scenarios: List<String> = listOf(),
                                val dataFile: String = "",
                                val dataSheets: List<String> = listOf(),
                                val output: String = "",
                                val overrides: MutableMap<String, String> = mutableMapOf(),
                                var interactive: Boolean)

data class ExecutePlanRequest(val plan: String,
                              val output: String = "",
                              val overrides: MutableMap<String, String> = mutableMapOf(),
                              var interactive: Boolean)

/**
 * corresponds to `nexial-crypt` batch file
 * @property secret String
 * @constructor
 */
data class EncryptRequest(val secret: String)

data class EncryptResponse(val encrypted: String)

/**
 * corresponds to `nexial-project` batch file
 * @property project String
 * @constructor
 */
data class ProjectRequest(val project: String, val scripts: List<String> = listOf())

data class ProjectResponse(val projectHome: String, val metaFile: String, val artifacts: List<String>)

/**
 * corresponds to `nexial-macro-update` batch file
 * @property project String
 * @property options List<MacroUpdateOptions>
 * @property preview Boolean true mean yes
 * @constructor
 */
data class MacroUpdateRequest(val project: String, val options: List<MacroUpdater.MacroChange>, val preview: Boolean)

// /**
//  * the transactional detail of updating 1 macro instance
//  * @property fromFile String
//  * @property toFile String
//  * @property fromSheet String
//  * @property toSheet String
//  * @property fromName String
//  * @property toName String
//  * @constructor
//  */
// data class MacroUpdateOption(val fromFile: String, val toFile: String,
//                              val fromSheet: String, val toSheet: String,
//                              val fromName: String, val toName: String)

data class MacroUpdateResponse(val updateLogs: List<UpdateLog>)

/**
 * corresponds to `nexial-variable-update` batch file
 * @property project String
 * @property changes Map<String, String>
 * @property preview Boolean
 * @constructor
 */
data class DataVariableUpdateRequest(val project: String, val changes: Map<String, String>, val preview: Boolean)

data class DataVariableUpdateResponse(val project: String, val updateLogs: List<UpdateLog>, val preview: Boolean)

// nexial-log-parser.sh
// nexial-macro-update.sh
// nexial-project-inspector.sh
// nexial-script-update.sh


