package org.nexial.core.integration

import org.apache.commons.io.FileUtils
import org.json.JSONObject
import org.nexial.core.utils.CheckUtils
import org.nexial.core.utils.JsonUtils
import java.io.File
import java.nio.charset.Charset
import java.util.*

const val executionSummaryFile = "execution-summary.json"

open class ExecutionOutput {
    var jsonSummary: JSONObject? = null
    var runUser = ""
    var runHost = ""
    var runHostOs = ""
    var startTime = ""
    var totalSteps = 0
    var passCount = 0
    var failCount = 0
    var warnCount = 0
    var executed = 0
    var iterations = mutableListOf<IterationOutput>()

    fun readExecutionSummary(dirPath: String): ExecutionOutput{

        val filePath = "$dirPath${File.separator}$executionSummaryFile"
        CheckUtils.requiresReadableFile(filePath)
        this.jsonSummary = JsonUtils.toJSONObject(FileUtils.readFileToString(File(filePath), Charset.defaultCharset()))
        runUser = jsonSummary!!.getString("runUser")
        runHost = jsonSummary!!.getString("runHost")
        runHostOs = jsonSummary!!.getString("runHostOs")
        startTime = Date(jsonSummary!!.getLong("startTime")).toString()
        totalSteps = jsonSummary!!.getInt("totalSteps")
        passCount = jsonSummary!!.getInt("passCount")
        failCount = jsonSummary!!.getInt("failCount")
        warnCount = jsonSummary!!.getInt("warnCount")
        executed = jsonSummary!!.getInt("executed")
        return this

    }
}