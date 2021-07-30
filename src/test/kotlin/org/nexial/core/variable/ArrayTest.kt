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

import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test
import org.nexial.core.ExecutionThread
import org.nexial.core.NexialConst.Data.TEXT_DELIM
import org.nexial.core.model.MockExecutionContext

class ArrayTest {

    @Test
    fun pack() {
        val subject = Array()

        assertEquals("", subject.pack(null as String?))
        assertEquals("", subject.pack(""))
        assertEquals("", subject.pack(",,"))
        assertEquals(" ", subject.pack(", ,"))
        assertEquals(" ", subject.pack(", ,,"))
        assertEquals("a,b,c", subject.pack("a,b,c,,,,,,,"))
        assertEquals("a,b,c", subject.pack("a,b,,,,,c,,,"))
        assertEquals("a,b,c", subject.pack("a,b,,,,,,,,c"))
    }

    @Test
    fun replica() {
        val subject = Array()

        assertEquals("", subject.replica("", "5"))
        assertEquals(" , , , , ", subject.replica(" ", "5"))
        assertEquals("a,a,a,a,a", subject.replica("a", "5"))
        assertEquals("a,b,c,a,b,c,a,b,c,a,b,c,a,b,c", subject.replica("a,b,c", "5"))
        assertEquals("a, ,,b,c,a, ,,b,c,a, ,,b,c,a, ,,b,c", subject.replica("a, ,,b,c", "4"))
    }

    @Test
    fun replicaUntil() {
        val subject = Array()

        assertEquals("", subject.replicaUntil(null as String?, "5"))
        assertEquals("", subject.replicaUntil("", "5"))
        assertEquals("a,a,a,a,a", subject.replicaUntil("a", "5"))
        assertEquals("a,b,a,b,a", subject.replicaUntil("a,b", "5"))
        assertEquals("a,bc,d,a,bc", subject.replicaUntil("a,bc,d", "5"))
        assertEquals("a,bc,defg,a,bc", subject.replicaUntil("a,bc,defg", "5"))
    }

    @Test
    fun sort() {
        val subject = Array()

        assertEquals("a,d,t,z", subject.ascending("d,a,z,t"))
        assertEquals("ak,ap,d,t,z", subject.ascending("d,ak,z,t,ap"))
        assertEquals("10,13,20,50", subject.ascending("13,10,20,50"))
        assertEquals("2,5,10,13", subject.ascending("13,10,2,5"))

        assertEquals("z,t,d,a", subject.descending("d,a,z,t"))
        assertEquals("Z,T,D,AP,AK", subject.descending("D,AK,Z,T,AP"))
        assertEquals("50,20,13,10", subject.descending("13,10,20,50"))
        assertEquals("13,10,5,2", subject.descending("13,10,2,5"))
    }

    companion object {
        private var context: MockExecutionContext? = null

        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            val context = MockExecutionContext()
            context.setData(TEXT_DELIM, ",")
            ExecutionThread.set(context)
        }

        @AfterClass
        @JvmStatic
        fun afterClass() {
            context?.cleanProject()
        }
    }
}