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

package org.nexial.commons.proc

import org.nexial.core.plugins.ForcefulTerminate

class ExternalProcessTerminator(var externalProc: Process?) : ForcefulTerminate {
    override fun mustForcefullyTerminate() = externalProc != null && externalProc!!.isAlive

    override fun forcefulTerminate() {
        if (mustForcefullyTerminate()) {
            println("forcefully destroying process $externalProc...")
            externalProc!!.destroyForcibly()
        }
    }
}