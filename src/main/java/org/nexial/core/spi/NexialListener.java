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

package org.nexial.core.spi;

public interface NexialListener {

    void onNexialPreStart(NexialExecutionEvent event);

    void onNexialStart(NexialExecutionEvent event);

    void onNexialEnd(NexialExecutionEvent event);

    void onNexialCmdError(NexialExecutionEvent event);

    void onExecutionStart(NexialExecutionEvent event);

    void onExecutionEnd(NexialExecutionEvent event);

    void onPlanStart(NexialExecutionEvent event);

    void onPlanEnd(NexialExecutionEvent event);

    void onSubPlanStart(NexialExecutionEvent event);

    void onSubPlanEnd(NexialExecutionEvent event);

    void onScriptParsed(NexialExecutionEvent event);

    void onDataFileParsed(NexialExecutionEvent event);

    void onProjectPropertiesParsed(NexialExecutionEvent event);

    void onScriptStart(NexialExecutionEvent event);

    void onScriptEnd(NexialExecutionEvent event);

    void onIterationStart(NexialExecutionEvent event);

    void onIterationEnd(NexialExecutionEvent event);

    void onScenarioStart(NexialExecutionEvent event);

    void onScenarioEnd(NexialExecutionEvent event);

    void onActivityStart(NexialExecutionEvent event);

    void onActivityEnd(NexialExecutionEvent event);

    void onRepeatUntilStart(NexialExecutionEvent event);

    void onRepeatUntilEnd(NexialExecutionEvent event);

    void onStepStart(NexialExecutionEvent event);

    void onStepSucceed(NexialExecutionEvent event);

    void onStepFailed(NexialExecutionEvent event);

    void onStepSkipped(NexialExecutionEvent event);

    void onStepPaused(NexialExecutionEvent event);

    void onScreenshotCaptured(NexialExecutionEvent event);

    void onRecordStart(NexialExecutionEvent event);

    void onRecordEnd(NexialExecutionEvent event);

    void onOutputCaptured(NexialExecutionEvent event);

    void onEmailNotificationSent(NexialExecutionEvent event);

    void onSmsNotificationSent(NexialExecutionEvent event);

    void onCommandListing(NexialExecutionEvent event);

    void onBrowserEnd(NexialExecutionEvent event);

    void onUrlInvoked(NexialExecutionEvent event);

    void onLogInvoked(NexialExecutionEvent event);
}