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
import org.nexial.commons.utils.TextUtils
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * sample test code for SQLite, particularly useful to prep' data for testing and POC.
 */
class SqliteSample {

    companion object {

        @Throws(Throwable::class)
        @JvmStatic
        fun main(args: Array<String>) {
            if (args.size < 2) throw Exception(
                "REQUIRED INPUT: [full path to sqlite db] [full path of image to import] [full path of image to export")

            // e.g. args[0]=$USER_HOME/.nexial/localdb/nexial
            val connection = "jdbc:sqlite:${args[0]}"

            // e.g. args[1]=$USER_HOME/Pictures/Screenshots/Screenshot (10).png
            val sourceFile = args[1]

            // e.g. args[2]=C:\temp\myImage.png
            val destinationFile = args[2]

            val conn = connect(connection)

            createRecord(conn, File(sourceFile), "SIS", "nexial", "workshop")
            saveImage(conn, 1, File(destinationFile))

            conn.close()
        }

        private fun readFile(file: File) = FileUtils.readFileToByteArray(file)

        private fun connect(connection: String) = DriverManager.getConnection(connection)

        fun createRecord(conn: Connection, file: File, vararg tags: String) {
            conn.use {
                it.prepareStatement("INSERT INTO pictures (image, tags) VALUES (?, ?)").use { statement ->
                    statement.setBytes(1, readFile(file))
                    statement.setString(2, TextUtils.toString(tags, ",", "", ""))
                    statement.executeUpdate()
                }
            }
        }

        fun saveImage(conn: Connection, id: Int, file: File) {
            conn.use {
                val statement = it.prepareStatement("SELECT * FROM pictures WHERE id = ?")
                statement.setInt(1, id)
                val resultSet = statement.executeQuery()
                FileUtils.writeByteArrayToFile(file, resultSet.getBytes("image"))
            }
        }
    }
}