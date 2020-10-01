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

import org.apache.commons.cli.CommandLine;
import org.nexial.core.Nexial;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.ExecutionSummary;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import ch.qos.logback.classic.spi.ILoggingEvent;

import static org.nexial.core.spi.NexialEventType.*;

public class NexialExecutionEvent {
    private final NexialEventType eventType;
    private final long eventTime;
    private ExecutionContext context;
    private ClassPathXmlApplicationContext springContext;
    private ExecutionSummary executionSummary;

    private Nexial nexial;
    private String runId;
    private String[] args;
    private CommandLine commandLine;
    private Throwable error;

    private String script;
    private int iterationIndex;
    private String browser;
    private String targetUrl;
    private ILoggingEvent loggingEvent;

    public static NexialExecutionEvent newNexialPreStartEvent(Nexial nexial, String[] args) {
        NexialExecutionEvent event = new NexialExecutionEvent(NexialPreStart);
        event.nexial = nexial;
        event.args = args;
        return event;
    }

    public static NexialExecutionEvent newNexialStartEvent(Nexial nexial, String[] args, CommandLine commandLine) {
        NexialExecutionEvent event = new NexialExecutionEvent(NexialStart);
        event.nexial = nexial;
        event.args = args;
        event.commandLine = commandLine;
        return event;
    }

    public static NexialExecutionEvent newNexialEndEvent(Nexial nexial,
                                                         String runId,
                                                         ExecutionSummary executionSummary) {
        NexialExecutionEvent event = new NexialExecutionEvent(NexialEnd);
        event.runId = runId;
        event.nexial = nexial;
        event.executionSummary = executionSummary;
        return event;
    }

    public static NexialExecutionEvent newNexialCmdError(Nexial nexial, String[] args, Throwable error) {
        NexialExecutionEvent event = new NexialExecutionEvent(CmdError);
        event.nexial = nexial;
        event.args = args;
        event.error = error;
        return event;
    }

    public static NexialExecutionEvent newExecutionStartEvent(ExecutionContext context) {
        NexialExecutionEvent event = new NexialExecutionEvent(ExecutionStart);
        event.context = context;
        return event;
    }

    public static NexialExecutionEvent newExecutionEndEvent(String runId, ExecutionSummary executionSummary) {
        NexialExecutionEvent event = new NexialExecutionEvent(ExecutionEnd);
        event.runId = runId;
        event.executionSummary = executionSummary;
        return event;
    }

    public static NexialExecutionEvent newCommandListingEvent(ClassPathXmlApplicationContext springContext) {
        NexialExecutionEvent event = new NexialExecutionEvent(CommandListing);
        event.springContext = springContext;
        return event;
    }

    public static NexialExecutionEvent newScenarioEndEvent(ExecutionSummary executionSummary) {
        NexialExecutionEvent event = new NexialExecutionEvent(ScenarioEnd);
        event.executionSummary = executionSummary;
        return event;
    }

    public static NexialExecutionEvent newIterationEndEvent(String script,
                                                            int iterationIndex,
                                                            ExecutionSummary iterSummary) {
        NexialExecutionEvent event = new NexialExecutionEvent(IterationEnd);
        event.executionSummary = iterSummary;
        event.script = script;
        event.iterationIndex = iterationIndex;
        return event;
    }

    public static NexialExecutionEvent newScriptEndEvent(String scriptFile, ExecutionSummary summary) {
        NexialExecutionEvent event = new NexialExecutionEvent(ScriptEnd);
        event.script = scriptFile;
        event.executionSummary = summary;
        return event;
    }

    public static NexialExecutionEvent newBrowserEndEvent(String browser) {
        NexialExecutionEvent event = new NexialExecutionEvent(BrowserEnd);
        event.browser = browser;
        return event;
    }

    public static NexialExecutionEvent newUrlInvokedEvent(String browser, String url) {
        NexialExecutionEvent event = new NexialExecutionEvent(UrlInvoked);
        event.browser = browser;
        event.targetUrl = url;
        return event;
    }

    public NexialExecutionEvent(NexialEventType eventType) {
        eventTime = System.currentTimeMillis();
        this.eventType = eventType;
    }

    public static NexialExecutionEvent newLogInvokedEvent(ILoggingEvent event) {
        NexialExecutionEvent nexialExecutionEvent = new NexialExecutionEvent(LogInvoked);
        nexialExecutionEvent.loggingEvent = event;
        return nexialExecutionEvent;
    }

    public NexialEventType getEventType() { return eventType; }

    public ExecutionContext getContext() { return context; }

    public ExecutionSummary getExecutionSummary() { return executionSummary; }

    public Nexial getNexial() {return nexial;}

    public String getRunId() {return runId;}

    public String[] getArgs() {return args;}

    public CommandLine getCommandLine() {return commandLine;}

    public long getEventTime() {return eventTime;}

    public Throwable getError() {return error;}

    public String getScript() {return script;}

    public int getIterationIndex() {return iterationIndex;}

    public ClassPathXmlApplicationContext getSpringContext() {return springContext;}

    public String getBrowser() {return browser;}

    public String getTargetUrl() {return targetUrl;}

    public ILoggingEvent getLoggingEvent() {
        return loggingEvent;
    }
}
