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
package org.nexial.core.plugins.web

import org.apache.commons.lang3.StringUtils
import org.nexial.core.NexialConst.BrowserType
import org.nexial.core.NexialConst.CloudWebTesting.*
import org.nexial.core.NexialConst.CrossBrowserTesting
import org.nexial.core.model.ExecutionContext
import org.nexial.core.model.ExecutionEvent
import org.nexial.core.model.ExecutionEvent.*
import org.nexial.core.model.ExecutionSummary
import org.nexial.core.plugins.browserstack.BrowserStack
import org.nexial.core.plugins.browserstack.BrowserStackHelper
import org.nexial.core.utils.ConsoleUtils
import org.openqa.selenium.WebDriver

abstract class CloudWebTestingPlatform protected constructor(protected var context: ExecutionContext) {
    var os: String? = null
        protected set

    var isRunningWindows = false
        protected set

    var isRunningOSX = false
        protected set

    @JvmField
    var browser: BrowserType? = null

    var browserVersion: String? = null
        protected set

    var browserName: String? = null
        protected set

    var isPageSourceSupported = false
        protected set

    var isMobile = false
        protected set

    var device: String? = null
        protected set

    var isRunningIOS = false
        protected set

    var isRunningAndroid = false
        protected set

    var isRunningLocal = false
        protected set

    var isTerminateLocal = false
        protected set

    var localExeName: String? = null
        protected set

    // @JvmStatic
    // protected resolveSessionId() = getSessionId(context)

    abstract fun initWebDriver(): WebDriver

    protected abstract fun terminateLocal()

    companion object {
        /**
         * report execution status to the on-demand, cloud-based browser execution service such as BrowserStack or
         * CrossBrowserTesting. The execution status can be scoped to the entire execution or per iteration.
         */
        @JvmStatic
        fun reportCloudBrowserStatus(context: ExecutionContext?,
                                     summary: ExecutionSummary?,
                                     targetScope: ExecutionEvent?) {
            if (context == null || summary == null || targetScope == null) return

            if (!isValidReportScope(targetScope)) {
                ConsoleUtils.error("Execution Scope '${targetScope.eventName}' currently not supported for status " +
                                   "report on cloud-based browser execution")
                return
            }

            if (!context.isPluginLoaded("web")) return

            // blank browser type means no browser init yet.
            val browserType = context.browserType
            if (StringUtils.isBlank(browserType)) return

            // special case for BrowserStack and CrossBrowserTesting
            // https://www.browserstack.com/automate/rest-api

            // this means we were running browser in this script... now let's report status
            val browser = BrowserType.valueOf(browserType)
            if (browser == BrowserType.browserstack &&
                isReportStatusMatchingScope(targetScope,
                                            context.getStringData(BrowserStack.KEY_STATUS_SCOPE, SCOPE_DEFAULT))) {
                BrowserStackHelper.reportExecutionStatus(context, summary)
            }

            if (browser == BrowserType.crossbrowsertesting &&
                isReportStatusMatchingScope(targetScope,
                                            context.getStringData(CrossBrowserTesting.KEY_STATUS_SCOPE, SCOPE_DEFAULT))) {
                CrossBrowserTestingHelper.reportExecutionStatus(context, summary)
            }
        }

        // protected fun saveSessionId(driver: RemoteWebDriver) {
        //     context.addScriptReferenceData(Web.SESSION_ID, driver.sessionId.toString())
        // }

        // @JvmStatic
        // protected fun getSessionId(context: ExecutionContext): String? {
        //     val sessionId = context.gatherScriptReferenceData()[Web.SESSION_ID]
        //     if (StringUtils.isBlank(sessionId)) {
        //         ConsoleUtils.error(
        //                 "Unable to report execution status since session id is blank or cannot be retrieved.")
        //         return null
        //     }
        //     return sessionId
        // }

        @JvmStatic
        fun formatStatusDescription(summary: ExecutionSummary): String {
            return "total: ${summary.totalSteps}, " +
                   "pass: ${summary.passCount}, " +
                   "fail: ${summary.failCount}, " +
                   "success%: ${summary.successRateString}"
        }

        private fun isReportStatusMatchingScope(targetScope: ExecutionEvent, scope: String): Boolean {
            return scope == SCOPE_EXECUTION && targetScope === ExecutionComplete ||
                   scope == SCOPE_SCRIPT && targetScope === ScriptComplete ||
                   scope == SCOPE_ITERATION && targetScope === IterationComplete
        }
    }
}