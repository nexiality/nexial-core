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

package org.nexial.core.model;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.nexial.core.model.FlowControl.Directive;

import java.util.List;
import java.util.Map;

import static org.apache.commons.lang3.builder.ToStringStyle.SIMPLE_STYLE;

/**
 * lightweight version of the {@link TestStep}, without references to POI objects
 */
public class TestStepManifest {

    // simplify and standardize logging by using prefix
    protected String messageId;

    protected String description;
    protected String target;
    protected String command;
    protected List<String> params;
    protected List<String> linkableParams;
    protected Map<Directive, FlowControl> flowControls;
    protected boolean captureScreen;
    protected boolean logToTestScript;
    // runtime row index (possibly tainted by macro expansion)
    protected int rowIndex;
    // original row index (without tainting from macro expansion)
    protected int scriptRowIndex;
    protected boolean isExternalProgram;

    public int getRowIndex() { return rowIndex; }

    protected void setRowIndex(int rowIndex) { this.rowIndex = rowIndex; }

    public int getScriptRowIndex() { return scriptRowIndex; }

    public String getDescription() { return description; }

    protected void setDescription(String description) { this.description = description;}

    public String getTarget() { return target; }

    protected void setTarget(String target) { this.target = target;}

    public String getCommand() { return command; }

    protected void setCommand(String command) { this.command = command;}

    public String getCommandFQN() { return target + "." + command; }

    public List<String> getParams() { return params; }

    protected void setParams(List<String> params) { this.params = params;}

    public List<String> getLinkableParams() { return linkableParams; }

    protected void setLinkableParams(List<String> linkableParams) { this.linkableParams = linkableParams;}

    public Map<Directive, FlowControl> getFlowControls() { return flowControls; }

    protected void setFlowControls(Map<Directive, FlowControl> flowControls) { this.flowControls = flowControls;}

    public boolean isCaptureScreen() { return captureScreen; }

    protected void setCaptureScreen(boolean captureScreen) { this.captureScreen = captureScreen;}

    public boolean isExternalProgram() { return isExternalProgram; }

    protected void setExternalProgram(boolean externalProgram) { isExternalProgram = externalProgram;}

    public String getMessageId() { return messageId; }

    protected void setMessageId(String messageId) { this.messageId = messageId;}

    public boolean isLogToTestScript() { return logToTestScript; }

    protected void setLogToTestScript(boolean logToTestScript) { this.logToTestScript = logToTestScript;}

    @Override
    public String toString() {
        return new ToStringBuilder(this, SIMPLE_STYLE)
                   .append("description", description)
                   .append("target", target)
                   .append("command", command)
                   .append("params", params)
                   .append("flowControls", flowControls)
                   .append("captureScreen", captureScreen)
                   .append("messageId", messageId)
                   .toString();
    }
}
