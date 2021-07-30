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

package org.nexial.core.variable

import org.junit.After
import org.junit.Before
import org.junit.Test
import org.nexial.core.ExecutionThread
import org.nexial.core.model.MockExecutionContext
import kotlin.test.assertEquals

class CsvTransformerTest {
    private val sampleCsvText1 =
        "cdatetime,address,district,beat,grid,ucr_ncic_code\n" +
        "1/1/06 0:00,3108 OCCIDENTAL DR,3,3C,1115,2404\n" +
        "1/1/06 0:00,2082 EXPEDITION WAY,5,5A,1512,2204\n" +
        "1/1/06 0:00,4 PALEN CT,2,2A,212,2404\n" +
        "1/1/06 0:00,22 BECKFORD CT,6,6C,1443,2501\n" +
        "1/1/06 0:00,3421 AUBURN BLVD,2,2A,508,2299\n" +
        "1/1/06 0:00,5301 BONNIEMAE WAY,6,6B,1084,2604\n" +
        "1/1/06 0:00,2217 16TH AVE,4,4A,957,2299\n" +
        "1/1/06 0:00,3547 P ST,3,3C,853,2308\n" +
        "1/1/06 0:00,3421 AUBURN BLVD,2,2A,508,2203\n" +
        "1/1/06 0:00,1326 HELMSMAN WAY,1,1B,444,2310\n" +
        "1/1/06 0:00,2315 STOCKTON BLVD,6,6B,1005,7000\n" +
        "1/1/06 0:00,5112 63RD ST,6,6B,1088,2604\n" +
        "1/1/06 0:00,6351 DRIFTWOOD ST,4,4C,1261,7000\n" +
        "1/1/06 0:00,7721 COLLEGE TOWN DR,3,3C,888,2604\n" +
        "1/1/06 0:00,8460 ROVANA CIR,6,6C,1447,2605\n" +
        "1/1/06 0:00,4856 11TH AVE,6,6B,1054,2303\n" +
        "1/1/06 0:00,6033 69TH ST,6,6C,1403,7000\n" +
        "1/1/06 0:00,547 L ST,3,3M,742,2308\n" +
        "1/1/06 0:00,3525 42ND ST,6,6A,1034,2604"

    @Before
    @Throws(Exception::class)
    fun setUp() {
        val context = MockExecutionContext()
        ExecutionThread.set(context)
    }

    @After
    @Throws(Exception::class)
    fun tearDown() {
    }

    @Test
    fun toIndices() {
        val subject = CsvTransformer<CsvDataType>()

        var fixture = CsvDataType(sampleCsvText1)
        fixture = subject.parse(fixture, "header=true")

        assertEquals(setOf(), subject.toIndices(fixture, ""))
        assertEquals(setOf(1, 4), subject.toIndices(fixture, "address", "grid"))
        assertEquals(setOf(4, 3), subject.toIndices(fixture, "grid", "beat"))
        assertEquals(setOf(4, 3), subject.toIndices(fixture, "grid", "grid", "beat", "beat"))
        assertEquals(setOf(0, 3, 2, 1), subject.toIndices(fixture, "0|3|2|address"))
    }

    @Test
    fun toRepeatableIndices() {
        val subject = CsvTransformer<CsvDataType>()

        var fixture = CsvDataType(sampleCsvText1)
        fixture = subject.parse(fixture, "header=true")

        assertEquals(listOf(), subject.toRepeatableIndices(fixture, ""))
        assertEquals(listOf(1, 4), subject.toRepeatableIndices(fixture, "address", "grid"))
        assertEquals(listOf(4, 3), subject.toRepeatableIndices(fixture, "grid", "beat"))
        assertEquals(listOf(4, 4, 3, 3), subject.toRepeatableIndices(fixture, "grid", "grid", "beat", "beat"))
        assertEquals(listOf(0, 3, 2, 1), subject.toRepeatableIndices(fixture, "0|3|2|address"))
        assertEquals(listOf(0, 3, 2, 1, 1, 1, 0), subject.toRepeatableIndices(fixture, "0|3|2|address|1|1|cdatetime"))
    }
}