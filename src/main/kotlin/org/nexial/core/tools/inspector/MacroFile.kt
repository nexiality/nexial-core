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
import com.google.gson.annotations.SerializedName
import org.apache.commons.lang3.StringUtils
import java.util.*

data class MacroFile(@Expose val location: String, @Expose val file: String) {

    @Expose
    val data: MutableList<MacroDef> = mutableListOf()

    @Expose
    val advices: MutableList<String> = mutableListOf()
}

data class MacroDef(@Expose val sheet: String, @Expose val macro: String) {

    @Expose
    var description: String? = null

    @Expose
    val expects: MutableList<Expects> = ArrayList()

    @Expose
    val produces: MutableList<Produces> = ArrayList()

    fun addDescription(description: String) {
        this.description = (if (StringUtils.isBlank(this.description)) "" else this.description + "\n") + description
    }

    fun addExpects(expect: Expects) { this.expects.add(expect) }

    fun addProduces(produce: Produces) { this.produces.add(produce) }
}

data class Expects(@Expose val description: String,
                   @Expose val name: String,
                   @Expose @SerializedName("default") var default: Any?)

data class Produces(@Expose val description: String, @Expose val name: String)