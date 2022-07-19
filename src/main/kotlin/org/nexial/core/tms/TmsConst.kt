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
package org.nexial.core.tms

import org.nexial.core.NexialConst.STD_DATE_FORMAT
import java.io.File
import java.text.SimpleDateFormat

/**
 * Test Management System related constants
 */
object TmsConst {
    val simpleDateFormat = SimpleDateFormat(STD_DATE_FORMAT)
    val dataFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")

    const val CLASSPATH_NEXIAL_INIT_XML = "classpath:/nexial-init.xml"
    const val SCRIPT = "script"
    const val PLAN = "plan"
    const val SCRIPT_ARG = "-$SCRIPT"
    const val PLAN_ARG = "-$PLAN"
    const val OUTPUT = "output"
    const val ROW = "Row"
    const val SUBPLAN = "subplan"
    const val SCENARIO = "scenario"
    const val NAT = "(nat)"
    const val DESCRIPTION = "description"
    const val BDD_KEYWORDS = "bddKeywords"
    const val CLOSE_RUN = "closeRun"

    // TestRail Constants
    const val TESTRAIL_API_VERSION = "/api/v2/"

    const val DESCRIPTION1 = "Description"
    const val EXPECTED = "expected"
    const val NBSP = "&nbsp;"
    const val ID = "id"
    const val KEY = "key"
    const val CASE_IDS = "case_ids"
    const val RUN = "run"
    const val NAME = "name"
    const val TITLE = "title"
    const val TEMPLATE_ID = "template_id"
    const val SUITE_ID = "suite_id"
    const val SUCCESS_CODE = 1
    const val FAILED_CODE = 5

    // BDD  formatting related constants
    const val COMMENT_REGEX = "^\\s*\\(.+\\)\\s*$"
    const val DATA_VAR_REGEX = "(.*?)(\\$\\{.+?\\})(.*?)"
    const val COMMENT_COLOR = "#9c9797"
    const val DATA_VAR_BACKGROUND_COLOR = "#f9ebeb"

    // Azure Template const
    const val HTML_TEMPLATE = "/AzureHtmlTemplate.html"
    const val ACTIVITY_NAME = "{{ACTIVITY_NAME}}"
    const val WORK_ITEM = "workItem"
    const val API_VERSION_6_0_PREVIEW_1 = "api-version=6.0-preview.1"
    const val API_VERSION_6_0_PREVIEW_2 = "api-version=6.0-preview.2"
    const val API_VERSION_6_0 = "api-version=6.0"
    const val API_VERSION_5_0_PREVIEW = "api-version=5.0-preview.1"

    // JIRA CONSTANTS
    const val LATEST_FROM = "latestFrom"
    const val JIRA = "jira"
    const val EPIC = "Epic"
    const val STORY = "Story"
    const val SUBTASK = "Subtask"
    const val FIELDS = "fields"
    const val COLOR1 = "{color:#6554c0}"
    const val PASSED_COLOR = "{color:#2C5F2D}"
    const val FAILED_COLOR = "{color:#ff0000}"
    const val JIRA_COMMENT_COLOR = "{color:$COMMENT_COLOR}"
    const val COLOR_END_TEXT = "{color}"
    const val REGEX_PATTERN = "^\\d{8}_\\d{6}$"
    const val OUTPUT_EXCEL_PATTERN = "^(.*)\\.\\d{8}_\\d{6}\\.\\d{3,}\\.xlsx$"
    const val RESULT_LINK_TEXT = "[Click here to see complete Execution Result|"
    const val FOCUSSED_COMMENT_ID = "?focusedCommentId="
    val EXECUTION_OUTPUT_HTML = File.separator + "execution-output.html"
    val JUNIT_XML = File.separator + "junit.xml"
    const val EXECUTION_SUMMARY = "execution-detail.json"
    const val ACTIVE_USER = "active"
    const val ACCOUNT_ID = "accountId"
    const val EMAIL_ADDRESS = "emailAddress"
    const val DISPLAY_NAME = "displayName"

    // Azure pipeline trx constants
    const val TEST_RUN = "TestRun"
    const val RUN_USER = "runUser"

    const val TIMES = "Times"
    const val CREATION = "creation"
    const val START = "start"
    const val FINISH = "finish"

    const val RESULT_SUMMARY = "ResultSummary"
    const val RESULT_FILES = "ResultFiles"
    const val RESULT_FILE = "ResultFile"
    const val RESULTS = "Results"
    const val PATH = "path"
    const val OUTCOME = "outcome"

    const val UNIT_TEST_RESULT = "UnitTestResult"
    const val TEST_NAME_TMS = "testName"
    const val EXECUTION_ID = "executionId"
    const val TEST_ID = "testId"
    const val COMPUTER_NAME = "computerName"
    const val START_TIME = "startTime"
    const val DURATION = "duration"

    const val OUTPUT1 = "Output"
    const val STDOUT = "StdOut"
    const val ERROR_INFO = "ErrorInfo"
    const val MESSAGE = "message"

    const val TEST_DEFINITIONS = "TestDefinitions"
    const val UNIT_TEST = "UnitTest"
    const val STORAGE = "storage"

    const val FAILED = "Failed"
    const val PASSED = "Passed"

    val DATE_FORMAT1 = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
}