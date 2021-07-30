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

package org.nexial.core.plugins.db

import org.junit.Assert
import org.junit.Test
import org.nexial.core.NexialConst.GSON

class DBDriverConfigTest {

    @Test
    fun serialization() {
        val fixture = "{ " +
                      " \"home\": \"\${user.home}/.nexial/rdbms/oracle\"," +
                      " \"fileName\": \"oracle.jar\"," +
                      " \"checkUrlBase\" : \"https://anyurl\"" +
                      "}"

        val actual = GSON.fromJson(fixture, DBDriverConfig::class.java)
        Assert.assertNotNull(actual)

        actual.init()

        Assert.assertEquals("\${user.home}/.nexial/rdbms/oracle", actual.home)
        Assert.assertEquals("https://anyurl", actual.checkUrlBase)
        Assert.assertEquals("oracle.jar", actual.fileName)
    }
}