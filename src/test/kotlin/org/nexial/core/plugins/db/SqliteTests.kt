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

import org.apache.commons.io.FileUtils
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Statement

class SqliteTests {

    @Test
    fun testConnection() {
        val conn: Connection = DriverManager.getConnection("jdbc:sqlite::memory:")
        val stmt: Statement = conn.createStatement()
        val statements: List<String> = arrayListOf("create table sample(id, name)",
                                                   "insert into sample values(1, \"Jimmy\")",
                                                   "insert into sample values(2, \"Bob\")",
                                                   "backup to backup.db")
        statements.forEach { statement: String -> stmt.execute(statement) }

        FileUtils.listFiles(File("."), arrayOf("db"), false).forEach { db ->
            println(db?.absolutePath)
            db?.delete()
        }
    }
}