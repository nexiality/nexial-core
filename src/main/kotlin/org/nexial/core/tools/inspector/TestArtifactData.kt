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

package org.nexial.core.tools.inspector

import com.google.gson.annotations.Expose
import org.nexial.core.tools.inspector.ArtifactType.DATA
import org.nexial.core.tools.inspector.ArtifactType.PLAN
import java.io.File

enum class ArtifactType { PLAN, SCRIPT, MACRO, ACTIVITY, DATA }

// for plan
data class PlanCollectionCache(@Expose val file: File,
                               @Expose val type: ArtifactType = PLAN,
                               @Expose var scenarios: List<PlanCache> = arrayListOf())

data class PlanCache(@Expose val name: String,
                     @Expose var sequences: List<PlanItemCache> = arrayListOf())

data class PlanItemCache(@Expose val row: Int,
                         @Expose val description: String,
                         @Expose val script: String,
                         @Expose val scenarios: List<String>,
                         @Expose val dataFile: String,
                         @Expose val dataSheets: List<String>,
                         @Expose val failFast: Boolean,
                         @Expose val wait: Boolean)

// for test script and macro
data class ScriptCache(@Expose val filename: String,
                       @Expose val type: ArtifactType,
                       @Expose var scenarios: List<ScenarioCache> = arrayListOf())

data class ScenarioCache(@Expose val name: String,
                         @Expose val type: ArtifactType,
                         @Expose var sequences: List<SequenceCache> = arrayListOf())

data class SequenceCache(@Expose val name: String,
                         @Expose val type: ArtifactType,
                         @Expose val row: Int,
                         @Expose var steps: List<StepCache> = arrayListOf())

data class StepCache(@Expose val row: Int,
                     @Expose val description: String,
                     @Expose val cmdType: String,
                     @Expose val command: String,
                     @Expose val param1: String,
                     @Expose val param2: String,
                     @Expose val param3: String,
                     @Expose val param4: String,
                     @Expose val param5: String,
                     @Expose val flowControl: String,
                     @Expose val screenshot: Boolean = false)

// for test data
data class DataFileCache(@Expose val filename: String,
                         @Expose val type: ArtifactType = DATA,
                         @Expose var dataSheets: List<DataSheetCache> = arrayListOf())

data class DataSheetCache(@Expose val name: String,
                          @Expose var data: List<DataCache> = arrayListOf())

data class DataCache(@Expose val position: String,
                     @Expose val iteration: Int = 1,
                     @Expose val name: String,
                     @Expose val value: String)

// collections
data class MacroCache(@Expose val name: String,
                      @Expose val location: String,
                      @Expose val script: ScriptCache,
                      @Expose var macros: List<MacroDef> = arrayListOf(),
                      @Expose var dataVariables: List<DataVariableAtom> = arrayListOf())

data class DataVariableCache(@Expose val name: String,
                             @Expose val location: String,
                             @Expose val dataFile: DataFileCache,
                             @Expose var dataVariables: List<DataVariableAtom> = arrayListOf())

data class ScriptSuiteCache(@Expose val name: String,
                            @Expose val location: String,
                            @Expose val script: ScriptCache,
                            @Expose var dataVariables: List<DataVariableAtom> = arrayListOf())
