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
import org.apache.commons.lang3.StringUtils
import java.io.File
import java.util.*

data class DataVariableEntity(val projectHome: File) : TreeMap<String, TreeSet<DataVariableAtom>>()

/**
 * data class to store the data variable in terms of
 * <ul>
 * <li>(WHO)   - its name</li>
 * <li>(WHAT)  - its defined value</li>
 * <li>(WHERE) - its location</li>
 * <li>(WHICH) - its type </li>
 * </ul>
 *
 * @property name String
 * @property definedAs String
 * @property location String
 * @property type DataVariableLocationType
 * @constructor
 */
data class DataVariableAtom(@Expose val name: String,
                            @Expose val definedAs: String,
                            @Expose val location: String,
                            @Expose val dataSheet: String? = "",
                            @Expose val position: String? = "",
                            @Expose(deserialize = true) val type: DataVariableLocationType) :
        Comparable<DataVariableAtom> {

    @Expose
    val advices: MutableList<String> = mutableListOf()

    override fun equals(other: Any?): Boolean = other != null && super.equals(other)

    override fun hashCode(): Int = type.order

    override fun compareTo(other: DataVariableAtom) = when {
        type.order != other.type.order -> type.order.compareTo(other.type.order)
        location != other.location     -> location.compareTo(other.location)
        dataSheet != other.dataSheet   -> StringUtils.defaultString(dataSheet).compareTo(StringUtils.defaultString(other.dataSheet))
        position != other.position     -> StringUtils.defaultString(position).compareTo(StringUtils.defaultString(other.position))
        else                           -> definedAs.compareTo(other.definedAs)
    }
}

data class DataVariableLocationType(@Expose val order: Int, @Expose val name: String) {
    companion object {
        internal val StepOverride = DataVariableLocationType(0, "step")
        internal val CommandLineOverride = DataVariableLocationType(1, "commandline")
        internal val ProjectProperties = DataVariableLocationType(2, "project.properties")
        internal val ScenarioDataSheet = DataVariableLocationType(3, "datasheet")
        internal val DefaultDataSheet = DataVariableLocationType(4, "#default")
    }
}
