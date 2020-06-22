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

package org.nexial.core.plugins.web

import java.io.Serializable

class WebDriverManifest : Serializable {
    // version of downloaded driver
    var driverVersion: String? = null
        set(driverVersion) {
            field = driverVersion
            this.driverVersionExpanded = WebDriverHelper.expandVersion(driverVersion!!)
        }

    @Transient
    var driverVersionExpanded: Double = 0.toDouble()
        private set

    // epoch of last check for new driver
    var lastChecked: Long = 0

    // should include nexial manifest
    var downloadAgent: String? = null

    var neverCheck: Boolean = false

    var compatibleDriverVersion: String? = null

    @Transient
    var driverUrl: String? = null

    fun init() {
        this.driverVersionExpanded = WebDriverHelper.expandVersion(driverVersion)
    }
}
