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

import org.junit.Test
import org.nexial.core.model.MockExecutionContext
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SqliteTests {

    @Test
    fun testConnection() {
        val localDb = "nexial.localdb"
        val context = MockExecutionContext(true)
        val localdbObj = context.localdb

        for ((key, value) in localdbObj.connectionProps) {
            context.setData(key, value)
        }
        localdbObj.rdbms.dataAccess.setContext(context)
        val dao = localdbObj.rdbms.dataAccess.resolveDao(localDb)

        assertNotNull(dao)

        val statements: List<SqlComponent> =
            arrayListOf(SqlComponent("create table IF NOT EXISTS sample(id, name)"),
                        SqlComponent("delete from sample"),
                        SqlComponent("insert into sample values(1, 'Jimmy')"))

        statements.forEach { query: SqlComponent -> dao.executeSqls(listOf(query)) }

        val outcome = dao.executeSqls(listOf(SqlComponent("select count(id) as count from sample")))
        assertNotNull(outcome)
        assertTrue { outcome.size >= 1 }
    }
}