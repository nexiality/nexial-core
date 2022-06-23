/*
 * Copyright 2012-2022 the original author or authors.
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

package org.nexial.core.services

class SystemUpdateService {
    private val oracleJavaVersionUrl = "http://javadl-esd-secure.oracle.com/update/baseline.version"
    // val javaUrlMap = mapOf()
    //
    // fun findLatestOracleJava(baseVersion: Int): SoftwareLink {
    //     // curl -L -b "oraclelicense=a" http://download.oracle.com/otn-pub/java/jdk/7u79-b15/jdk-7u79-macosx-x64.dmg -O
    //    
    // }
    //
    // fun findLatestOpenJDK(baseVersion: Int): SoftwareLink {
    //    
    // }
    //
    // fun findLatestCorrettoJava(baseVersion: Int): SoftwareLink {
    //    
    // }

    private val nexialVersionsUrl = "https://api.github.com/repos/nexiality/nexial-core/releases?prerelease=true"
    // fun findLatestNexial(): SoftwareLink {
    //     val wsClient = WebServiceClient(null).configureAsQuiet().disableContextConfiguration()
    // }
    //
    // fun findLatestNexialInstaller(): SoftwareLink {
    //
    // }
    //
    // fun findLatestChromeDriver(): SoftwareLink {}
    // fun findLatestElectronDriver(): SoftwareLink {}
    // fun findLatestGeckoDriver(): SoftwareLink {}
}

class SoftwareLink(val version: String, val downloadLink: String) {

}