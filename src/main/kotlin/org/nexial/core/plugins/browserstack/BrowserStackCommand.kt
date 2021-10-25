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

package org.nexial.core.plugins.browserstack

import org.nexial.commons.utils.FileUtil
import org.nexial.core.NexialConst.RB
import org.nexial.core.model.ExecutionContext
import org.nexial.core.model.StepResult
import org.nexial.core.plugins.base.BaseCommand
import org.nexial.core.plugins.browserstack.BrowserStack.MIN_APP_SIZE
import org.nexial.core.utils.CheckUtils.*
import org.nexial.core.utils.JSONPath
import org.nexial.core.utils.JsonUtils
import org.nexial.core.utils.WebDriverUtils

class BrowserStackCommand : BaseCommand() {
    private lateinit var helper: BrowserStackHelper

    override fun getTarget() = "browserstack"

    override fun init(context: ExecutionContext) {
        super.init(context)
        helper = BrowserStackHelper(context)
    }

    fun uploadApp(profile: String, app: String, customId: String?, resultVar: String): StepResult {
        requiresValidAndNotReadOnlyVariableName(resultVar)
        requiresReadableFileOrValidUrl(app)
        if (!FileUtil.isFileReadable(app, MIN_APP_SIZE))
            throw IllegalArgumentException(RB.BrowserStack.text("invalid.app", profile, app))

        val outcome = helper.uploadApp(BrowserStackHelper.newConfig(context, profile), app, customId)
        context.setData(resultVar, outcome)

        return StepResult.success("upload of '$app' via profile '$profile' completed SUCCESSFULLY:\n${outcome}")
    }

    fun saveUploadApps(profile: String, customId: String?, resultVar: String): StepResult {
        requiresValidAndNotReadOnlyVariableName(resultVar)

        val response = helper.saveUploadApps(BrowserStackHelper.newConfig(context, profile), customId)
        context.setData(resultVar, response)
        return StepResult.success("recently uploaded apps are stored to '${resultVar}'")
    }

    fun deleteApp(profile: String, appId: String, resultVar: String): StepResult {
        requiresValidAndNotReadOnlyVariableName(resultVar)
        requiresNotBlank(appId, "invalid appId", appId)

        val response = helper.deleteUploadedApp(BrowserStackHelper.newConfig(context, profile), appId)
        context.setData(resultVar, response)
        val outcome = JSONPath.find(JsonUtils.toJSONObject(response), "success")
        return if (!outcome.toBoolean())
            StepResult.fail("Unable to delete app '$appId' via profile '$profile': $response")
        else
            StepResult.success("SUCCESSFULLY deleted app '$appId' via profile '$profile': $response")
    }

    fun listBrowsers(profile: String, resultVar: String): StepResult {
        requiresValidAndNotReadOnlyVariableName(resultVar)
        context.setData(resultVar, helper.listBrowsers(BrowserStackHelper.newConfig(context, profile)))
        return StepResult.success("BrowserStack-supported browsers saved to '$resultVar")
    }

    fun listDevices(profile: String, resultVar: String): StepResult {
        requiresValidAndNotReadOnlyVariableName(resultVar)
        context.setData(resultVar, helper.listDevices(BrowserStackHelper.newConfig(context, profile)))
        return StepResult.success("BrowserStack-supported devices saved to '$resultVar")
    }

    fun updateSessionStatus(profile: String, status: String, reason: String, resultVar: String): StepResult {
        requiresValidAndNotReadOnlyVariableName(resultVar)
        requiresOneOf(status, "passed", "failed")
        requiresNotBlank(reason, "invalid reason", reason)

        val sessionId = WebDriverUtils.getSessionId(context)
                        ?: throw IllegalArgumentException(RB.BrowserStack.text("session.not.found", profile))

        val config = BrowserStackHelper.newConfig(context, profile)
        val response = when {
            config.useBrowser -> helper.updateAutomateSessionStatus(config, sessionId, status, reason)
            config.useMobile  -> helper.updateAppAutomateSessionStatus(config, sessionId, status, reason)
            else              -> try {
                // find out the hard way
                helper.getAppAutomateSessionDetail(config, sessionId)
                helper.updateAppAutomateSessionStatus(config, sessionId, status, reason)
            } catch (e: IllegalArgumentException) {
                helper.getAutomateSessionDetail(config, sessionId)
                helper.updateAutomateSessionStatus(config, sessionId, status, reason)
            }
        }
        context.setData(resultVar, response)
        return StepResult.success("SUCCESSFULLY updated session '$sessionId' via profile '$profile': $response")
    }
}