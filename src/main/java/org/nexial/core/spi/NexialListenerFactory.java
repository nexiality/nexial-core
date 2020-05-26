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
            case NexialStart: {
                LISTENER.onNexialStart(event);
                break;
            }

            case NexialEnd: {
                LISTENER.onNexialEnd(event);
                break;
            }

            case CmdError: {
                LISTENER.onNexialCmdError(event);
                break;
            }

            case ExecutionStart: {
                LISTENER.onExecutionStart(event);
                break;
            }

            case ExecutionEnd: {
                LISTENER.onExecutionEnd(event);
                break;
            }

            case CommandListing: {
                LISTENER.onCommandListing(event);
                break;
            }

            case PlanStart:
            case PlanEnd:
            case SubPlanStart:
            case SubPlanEnd:
            case ScriptParsed:
            case DataFileParsed:
            case ProjectPropertiesParsed:
            case ScriptStart:
            case ScriptEnd:
            case IterationStart:
            case IterationEnd:
            case ScenarioStart:
            case ScenarioEnd:
            case ActivityStart:
            case ActivityEnd:
            case RepeatUntilStart:
            case RepeatUntilEnd:
            case StepStart:
            case StepSucceed:
            case StepFailed:
            case StepSkipped:
            case StepPaused:

            case ScreenshotCaptured:
            case RecordStart:
            case RecordEnd:
            case OutputCaptured:
            case EmailNotificationSent:
            case SmsNotificationSent:

            default: {

            }
        }

    }
}
