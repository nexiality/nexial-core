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

package org.nexial.core.model

import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.nexial.core.NexialConst.Project
import org.springframework.util.ResourceUtils
import org.springframework.util.StringUtils
import java.io.File
import java.io.FileNotFoundException

class ExecutionVariableConsoleTest {
    val context: MockExecutionContext = MockExecutionContext()
    private val execVarConsole = ExecutionVariableConsole()

    @After
    fun tearDown() = context.cleanProject()

    @Before
    fun setUp() {
    }

    @Test
    @Throws(Exception::class)
    fun runtimeVariableWithFallbackToPrevious() {
        val excelFile: String = getPath("ExecVariableConsoleTest1" + Project.DEF_DATAFILE_SUFFIX)
        val testData = getTestData(excelFile)
        Assert.assertEquals("var1-iteration1", testData.getValue(1, "var1"))
        Assert.assertEquals("var1-iteration1", testData.getValue(2, "var1"))
        Assert.assertEquals("var1-iteration1", testData.getValue(3, "var1"))
        Assert.assertEquals("var2-iteration1", testData.getValue(1, "var2"))
        Assert.assertEquals("var2-iteration1", testData.getValue(2, "var2"))
        Assert.assertEquals("var2-iteration1", testData.getValue(3, "var2"))
        Assert.assertEquals("var3-iteration1", testData.getValue(1, "var3"))
        Assert.assertEquals("var3-iteration1", testData.getValue(2, "var3"))
        Assert.assertEquals("var3-iteration1", testData.getValue(2, "var3"))
    }

    @Test
    @Throws(Exception::class)
    fun runtimeVariableWithoutFallbackToPrevious() {
        val excelFile: String = getPath("ExecVariableConsoleTest2" + Project.DEF_DATAFILE_SUFFIX)
        val testData = getTestData(excelFile)
        Assert.assertEquals("var1-iteration1", testData.getValue(1, "var1"))
        Assert.assertEquals("var1-iteration2", testData.getValue(2, "var1"))
        Assert.assertEquals("var1-iteration3", testData.getValue(3, "var1"))
        Assert.assertEquals("var2-iteration1", testData.getValue(1, "var2"))
        Assert.assertEquals("var2-iteration2", testData.getValue(2, "var2"))
        Assert.assertEquals("var2-iteration3", testData.getValue(3, "var2"))
        Assert.assertEquals("var3-iteration1", testData.getValue(1, "var3"))
        Assert.assertEquals("var3-iteration2", testData.getValue(2, "var3"))
        Assert.assertEquals("var3-iteration3", testData.getValue(3, "var3"))
    }

    @Test
    @Throws(Exception::class)
    fun runtimeVariableRandomIterations() {
        // nexial.scope.iteration=1,3,2
        val excelFile: String = getPath("ExecVariableConsoleTest3" + Project.DEF_DATAFILE_SUFFIX)
        val testData = getTestData(excelFile)
        Assert.assertEquals("var1-iteration1", testData.getValue(1, "var1"))
        Assert.assertEquals("var1-iteration3", testData.getValue(2, "var1"))
        Assert.assertEquals("var1-iteration2", testData.getValue(3, "var1"))
        Assert.assertEquals("var2-iteration1", testData.getValue(1, "var2"))
        Assert.assertEquals("var2-iteration3", testData.getValue(2, "var2"))
        Assert.assertEquals("var2-iteration2", testData.getValue(3, "var2"))
        Assert.assertEquals("var3-iteration1", testData.getValue(1, "var3"))
        Assert.assertEquals("var3-iteration3", testData.getValue(2, "var3"))
        Assert.assertEquals("var3-iteration2", testData.getValue(3, "var3"))
    }


    @Test
    @Throws(Exception::class)
    fun runtimeVariableRandomIterations1() {
        // nexial.scope.iteration=3-4
        val excelFile: String = getPath("ExecVariableConsoleTest4" + Project.DEF_DATAFILE_SUFFIX)
        val testData = getTestData(excelFile)
        Assert.assertEquals("var1-iteration1", testData.getValue(3, "var1"))
        Assert.assertEquals("var1-iteration2", testData.getValue(4, "var1"))
        Assert.assertEquals("var2-iteration1", testData.getValue(3, "var2"))
        Assert.assertEquals("var2-iteration2", testData.getValue(4, "var2"))
        Assert.assertEquals("var3-iteration1", testData.getValue(3, "var3"))
        Assert.assertEquals("var3-iteration2", testData.getValue(4, "var3"))
    }

    @Test
    @Throws(Exception::class)
    fun runtimeVariableWithVariableDefined() {
        val excelFile: String = getPath("ExecVariableConsoleTest5" + Project.DEF_DATAFILE_SUFFIX)
        val testData = getTestData(excelFile)
        Assert.assertEquals("var1-iteration1", testData.getValue(1, "var1"))
        Assert.assertEquals("var1-iteration1", testData.getValue(2, "var1"))
        Assert.assertEquals("var3-iteration1", testData.getValue(1, "var3"))
        Assert.assertEquals("var3-iteration1", testData.getValue(2, "var3"))

        // var2 is defined in datafile as value2
        // nexial.scope.fallbackToPrevious=true
        Assert.assertEquals("value2", testData.getValue(1, "var2"))
        Assert.assertEquals("value2", testData.getValue(2, "var2"))
    }

    @Test
    @Throws(Exception::class)
    fun runtimeVariableWithSomeVariableDefined() {
        val excelFile: String = getPath("ExecVariableConsoleTest6" + Project.DEF_DATAFILE_SUFFIX)
        val testData = getTestData(excelFile)
        Assert.assertEquals("var1-iteration1", testData.getValue(1, "var1"))
        Assert.assertEquals("var1-iteration2", testData.getValue(2, "var1"))
        Assert.assertEquals("var3-iteration1", testData.getValue(1, "var3"))
        Assert.assertEquals("var3-iteration2", testData.getValue(2, "var3"))

        // var2 is defined in datafile as value2
        // nexial.scope.fallbackToPrevious = false
        Assert.assertEquals("value2", testData.getValue(1, "var2"))
        Assert.assertEquals("var2-iteration2", testData.getValue(2, "var2"))

        // var4 is defined in project.properties
        // so var4 is not defined in testData
        Assert.assertEquals("", testData.getValue(1, "var4"))
        Assert.assertEquals("", testData.getValue(2, "var4"))

        // var5 is defined as (empty) in datafile
        // so var5 is empty for first iteration and value assigned for second iteration only because
        // nexial.scope.fallbackToPrevious is false.
        Assert.assertEquals("", context.replaceTokens(testData.getValue(1, "var5")))
        Assert.assertEquals("var5-iteration2", testData.getValue(2, "var5"))
    }

    private fun getTestData(excelFile: String): TestData {
        println("excelFile = $excelFile")

        // get testData from datafile
        val execDef = context.execDef
        execDef.dataFile = File(excelFile)
        execDef.dataSheets = listOf("new_test1")
        execDef.project.projectHome = execDef.dataFile.parent
        execDef.project.loadProjectProperties()

        val testData = execDef.getTestData(true)
        val iterationCount = testData.iterationManager.iterationCount

        for (index in 0 until iterationCount) {
            val iterationRef = testData.iterationManager.getIterationRef(index)
            testData.addExistingRuntimeData(testData.runtimeDataMap)
            val variableList = execVarConsole.retrieveUndefinedVariables(testData, iterationRef)
            variableList.forEach {
                val newValue = "$it-iteration${index + 1}"
                testData.addRuntimeData(it, newValue, iterationRef)
            }
        }
        return testData
    }

    @Throws(FileNotFoundException::class)
    fun getPath(filename: String): String {
        return ResourceUtils.getFile("classpath:" +
                                     StringUtils.replace(this.javaClass.name, ".", "/") + "/" + filename).absolutePath
    }
}
