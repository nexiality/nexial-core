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

import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang3.StringUtils
import org.nexial.core.ExecutionThread
import org.nexial.core.NexialConst.Notification.*
import org.nexial.core.model.ExecutionEvent.*
import org.nexial.core.service.EventTracker
import org.nexial.core.utils.ConsoleUtils

class ExecutionEventListener {
    lateinit var context: ExecutionContext
    var mailIncludeMeta: Boolean = false
    var smsIncludeMeta: Boolean = false

    fun onExecutionStart() = handleEvent(ExecutionStart)
    fun onExecutionComplete() = handleEvent(ExecutionComplete)
    fun onScriptStart() = handleEvent(ScriptStart)
    fun onScriptComplete() = handleEvent(ScriptComplete)
    fun onIterationStart() = handleEvent(IterationStart)
    fun onIterationComplete() = handleEvent(IterationComplete)
    fun onScenarioStart() = handleEvent(ScenarioStart)

    fun onScenarioComplete(executionSummary: ExecutionSummary) {
        handleEvent(ScenarioComplete)
        // summary and context are null in Interactive Mode
        if (ExecutionThread.get() != null) EventTracker.track(NexialScenarioCompleteEvent(executionSummary))
    }

    fun onError() = handleEvent(ErrorOccurred)
    fun onPause() = handleEvent(ExecutionPause)

    // nothing for now
    fun afterPause() {}

    private fun handleEvent(event: ExecutionEvent) {
        val notifyConfig = context.getStringData(event.variable)
        if (StringUtils.isBlank(notifyConfig) || !isConditionMatched(event)) return

        if (!StringUtils.contains(notifyConfig, ":")) {
            ConsoleUtils.error("Unknown notification for [" + event.eventName + "]: " + notifyConfig)
            return
        }

        val notifyPrefix = StringUtils.substringBefore(notifyConfig, ":") + ":"
        val notifyText = StringUtils.substringAfter(notifyConfig, ":")

        when (notifyPrefix) {
            TTS_PREFIX     -> TtsNotification(context, event, notifyText).perform()
            SMS_PREFIX     -> SmsNotification(context, event, notifyText)
                .includeExecMeta(smsIncludeMeta).invoke().perform()
            AUDIO_PREFIX   -> AudioNotification(context, event, notifyText).perform()
            EMAIL_PREFIX   -> EmailNotification(context, event, notifyText)
                .includeExecMeta(mailIncludeMeta).invoke().perform()
            CONSOLE_PREFIX -> ConsoleNotification(context, event, notifyText).perform()
            else           -> ConsoleUtils.error(context.runId, "Unknown event notification: " + notifyConfig!!)
        }
    }

    private fun isConditionMatched(event: ExecutionEvent): Boolean {
        val condition = context.getStringData(event.conditionalVar)
        if (StringUtils.isBlank(condition)) return true

        val filters = NexialFilterList(condition)
        return if (CollectionUtils.isEmpty(filters)) true
        else filters.isMatched(context, "handling event " + event.eventName)
    }
}
