package org.nexial.core.model

import org.apache.commons.collections4.CollectionUtils
import org.nexial.commons.utils.TextUtils
import org.nexial.core.NexialConst.Data.NS_REQUIRED_VAR
import org.nexial.core.utils.ConsoleUtils.*
import java.util.*

class ExecutionVariableConsole {

    fun processRuntimeVariables(testData: TestData, currIteration: Int): TestData {
        val iterationRef = testData.iterationManager.getIterationRef(currIteration - 1)
        val variableList = retrieveUndefinedVariables(testData, iterationRef)

        if (CollectionUtils.isEmpty(variableList)) return testData
        return assignValue(testData, variableList, iterationRef)
    }

    fun retrieveUndefinedVariables(testData: TestData, iterationRef: Int): List<String> {
        val runtimeVars = testData.getSetting(NS_REQUIRED_VAR) ?: return emptyList()
        return TextUtils.toList(runtimeVars, ",", false)
            .filterNot {
                testData.has(iterationRef, it) ||
                TestProject.isProjectProperty(it) ||
                System.getProperty(it) != null
            }
    }

    private fun assignValue(testData: TestData, variableList: List<String>, iterationRef: Int): TestData {
        val scanner = Scanner(System.`in`)
        variableList.forEach { key ->
            println("/------------------------------------------------------------------------------\\")
            println(MARGIN_RIGHT + centerPrompt("Missing Value For Variable [$key]", PROMPT_LINE_WIDTH - 2) +
                    MARGIN_RIGHT)
            println("\\------------------------------------------------------------------------------/")
            println("> Assign value and press ENTER")

            var input: String
            while (true) {
                print("> $key = ")
                input = scanner.nextLine()
                if (input != "") break
                println("> INVALID INPUT!! Please reassign value")
            }
            testData.addRuntimeData(key, input, iterationRef)
            println("\\------------------------------------------------------------------------------/")
        }
        return testData
    }

}
