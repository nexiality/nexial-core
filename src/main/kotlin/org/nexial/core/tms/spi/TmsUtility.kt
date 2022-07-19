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

package org.nexial.core.tms.spi

import com.coremedia.iso.Hex
import org.apache.commons.lang3.StringUtils
import org.json.JSONArray
import org.json.JSONObject
import org.nexial.core.plugins.ws.Response
import org.nexial.core.tms.model.TmsTestCase
import org.nexial.core.tms.tools.TmsImporter
import java.security.MessageDigest

/**
 * Enum to store the TMS tool
 */
enum class TmsSource { AZURE, TESTRAIL, JIRA; }

/**
 * Class containing Tms tool api access credentials
 */
data class TMSAccessData(val source: String, val user: String?, val password: String, val url: String)


data class TestcaseOrder(val id: String, val testName: String, val sequenceNumber: Int, val suiteEntryType: String) {
    override fun toString() =
        "{ \"id\": $id , \"testName\" : \"$testName\", " +
        "\"sequenceNumber\": $sequenceNumber, \"suiteEntryType\": \"$suiteEntryType\" }"
}

object TmsMD5Cache {
    private const val CACHE_VALUE_SEPARATOR = "@#*&$#"
    private val md5 = MessageDigest.getInstance("MD5")

    /**
     * Returns cached [String] of MD5 hashed [TmsTestCase] containing scenario description, activity name and
     * activity description separated steps and BDDKeyword.
     *
     * @param testCase [TmsTestCase] of the suite. Represented by scenarios in Nexial
     * @return cached [String] of [TmsTestCase] using MD5 hashing
     */
    fun generateMD5(testCase: TmsTestCase): String {
        var tmsData = testCase.description
        testCase.testSteps?.forEach { testStep ->
            tmsData += CACHE_VALUE_SEPARATOR + testStep.name
            testStep.tmsCustomSteps.forEach { customSTep -> tmsData += CACHE_VALUE_SEPARATOR + customSTep.description }
        }
        // adding bddKeywords ti
        tmsData += CACHE_VALUE_SEPARATOR + TmsImporter.formatBddKeywords

        // applying md5
        md5.reset()
        md5.update(tmsData.toByteArray())
        return Hex.encodeHex(md5.digest())
    }
}

/**
 * Handles response from tms apis to validate and convert it to [JSONObject] or [JSONArray]
 */
object ResponseHandler {
    fun handleResponse(response: Response?): Any {
        val result: Any = if (response != null) {
            val responseBody = response.body
            if (StringUtils.isBlank(responseBody)) JSONObject() else {
                if (responseBody.startsWith("{")) JSONObject(responseBody)
                else if (responseBody.startsWith("[")) JSONArray(responseBody)
                else {
                    throw TmsException("${response.statusText} with status code ${response.returnCode};")
                }
            }
        } else {
            JSONObject()
        }
        val statusCode = response!!.returnCode

        if (statusCode == 200 || statusCode == 201 || statusCode == 204) return result
        val error =
            if (result is JSONObject) {
                if (result.has("error")) result.getString("error") else response.body
            } else {
                "no additional error message received"
            }
        throw TmsException("Operation failed with status code '$statusCode' with $error.")
    }
}

class TmsException(val msg: String) : Exception(msg)