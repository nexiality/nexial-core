/*
 * Copyright 2012-2018 the original author or authors.
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

import java.util.Iterator;
import java.util.ServiceLoader;

public class NexialListenerFactory {

    private static final ServiceLoader<NexialListenerProvider> LOADER =
        ServiceLoader.load(NexialListenerProvider.class);
    private static final NexialListener LISTENER = initLoad(LOADER);

    private static NexialListener initLoad(ServiceLoader<NexialListenerProvider> loader) {
        Iterator<NexialListenerProvider> iterator = loader.iterator();
        if (!iterator.hasNext()) { return null; }

        NexialListenerProvider provider = iterator.next();
        if (provider == null) { return null; }

        return provider.create();
    }

    public static void fireEvent(NexialExecutionEvent event) {
        if (event == null || LISTENER == null) { return; }

        NexialEventType eventType = event.getEventType();
        switch (eventType) {
            case NexialPreStart:
                LISTENER.onNexialPreStart(event);
                break;
            case NexialStart:
                LISTENER.onNexialStart(event);
                break;
            case NexialEnd:
                LISTENER.onNexialEnd(event);
                break;
            case CmdError:
                LISTENER.onNexialCmdError(event);
                break;
            case ExecutionStart:
                LISTENER.onExecutionStart(event);
                break;
            case ExecutionEnd:
                LISTENER.onExecutionEnd(event);
                break;
            case CommandListing:
                LISTENER.onCommandListing(event);
                break;
            case PlanStart:
                LISTENER.onPlanStart(event);
                break;
            case PlanEnd:
                LISTENER.onPlanEnd(event);
                break;
            case SubPlanStart:
                LISTENER.onSubPlanStart(event);
                break;
            case SubPlanEnd:
                LISTENER.onSubPlanEnd(event);
                break;
            case ScriptParsed:
                LISTENER.onScriptParsed(event);
                break;
            case DataFileParsed:
                LISTENER.onDataFileParsed(event);
                break;
            case ProjectPropertiesParsed:
                LISTENER.onProjectPropertiesParsed(event);
                break;
            case ScriptStart:
                LISTENER.onScriptStart(event);
                break;
            case ScriptEnd:
                LISTENER.onScriptEnd(event);
                break;
            case IterationStart:
                LISTENER.onIterationStart(event);
                break;
            case IterationEnd:
                LISTENER.onIterationEnd(event);
                break;
            case ScenarioStart:
                LISTENER.onScenarioStart(event);
                break;
            case ScenarioEnd:
                LISTENER.onScenarioEnd(event);
                break;
            case ActivityStart:
                LISTENER.onActivityStart(event);
                break;
            case ActivityEnd:
                LISTENER.onActivityEnd(event);
                break;
            case RepeatUntilStart:
                LISTENER.onRepeatUntilStart(event);
                break;
            case RepeatUntilEnd:
                LISTENER.onRepeatUntilEnd(event);
                break;
            case StepStart:
                LISTENER.onStepStart(event);
                break;
            case StepSucceed:
                LISTENER.onStepSucceed(event);
                break;
            case StepFailed:
                LISTENER.onStepFailed(event);
                break;
            case StepSkipped:
                LISTENER.onStepSkipped(event);
                break;
            case StepPaused:
                LISTENER.onStepPaused(event);
                break;
            case ScreenshotCaptured:
                LISTENER.onScreenshotCaptured(event);
                break;
            case RecordStart:
                LISTENER.onRecordStart(event);
                break;
            case RecordEnd:
                LISTENER.onRecordEnd(event);
                break;
            case OutputCaptured:
                LISTENER.onOutputCaptured(event);
                break;
            case EmailNotificationSent:
                LISTENER.onEmailNotificationSent(event);
                break;
            case SmsNotificationSent:
                LISTENER.onSmsNotificationSent(event);
                break;
            case BrowserEnd:
                LISTENER.onBrowserEnd(event);
                break;
            case UrlInvoked:
                LISTENER.onUrlInvoked(event);
                break;

            default:
        }
    }
}
