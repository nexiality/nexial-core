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
 *
 */

package org.nexial.core.model;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.nexial.core.utils.MessageUtils;
import org.openqa.selenium.WebDriver;

import static org.apache.commons.lang3.builder.ToStringStyle.SHORT_PREFIX_STYLE;
import static org.nexial.core.NexialConst.Data.OPT_ELAPSED_TIME_SLA;
import static org.nexial.core.NexialConst.*;

public class StepResult {
    private static final String NOT_SUPPORTED = "()' is not supported by current version of automation driver: ";

    private boolean success;
    private String message;
    private final Throwable exception;
    private Object[] paramValues;
    private String detailedLogLink;

    public StepResult(boolean success) { this(success, null, null); }

    public StepResult(boolean success, String message, Throwable exception) {
        this.success = success;
        this.message = message;
        this.exception = exception;
    }

    public static StepResult success() { return new StepResult(true, null, null); }

    public static StepResult success(String message) { return new StepResult(true, message, null); }

    public static StepResult success(String format, Object... args) {
        return new StepResult(true, String.format(format, args), null);
    }

    public static StepResult fail(String message) { return new StepResult(false, message, null); }

    public static StepResult fail(String format, Object... args) {
        return new StepResult(false, String.format(format, args), null);
    }

    public static StepResult fail(String message, Throwable exception) {
        return new StepResult(false, message, exception);
    }

    public static StepResult warn(String message) {
        return new StepResult(false,
                              !StringUtils.startsWith(message, MSG_WARN) ? MSG_WARN + message : message,
                              null);
    }

    public static StepResult warnUnsupportedFeature(String command, WebDriver driver) {
        return new StepResult(false, MSG_WARN + "Command '" + command + NOT_SUPPORTED + driver, null);
    }

    public static StepResult skipped(String message) {
        return new StepResult(false,
                              !StringUtils.startsWith(message, MSG_SKIPPED) ? MSG_SKIPPED + message : message,
                              null);
    }

    public static StepResult ended(String message) {
        return new StepResult(false,
                              !StringUtils.startsWith(message, MSG_TERMINATED) ? MSG_TERMINATED + message : message,
                              null);
    }

    public boolean isSuccess() { return success; }

    public boolean failed() { return !success; }

    public boolean isSkipped() { return MessageUtils.isSkipped(message); }

    public boolean isEnded() { return MessageUtils.isEnded(message); }

    public boolean isWarn() { return MessageUtils.isWarn(message); }

    public boolean isError() { return !success && !isSkipped() && !isWarn() && !isEnded(); }

    public String getMessage() { return StringUtils.defaultString(message, ""); }

    public Throwable getException() { return exception; }

    public Object[] getParamValues() { return paramValues; }

    public void setParamValues(Object[] paramValues) { this.paramValues = paramValues; }

    public void markElapsedTimeSlaNotMet() {
        success = false;
        // exception = new AssertionError("Elapsed time violated SLA specified via '" + OPT_ELAPSED_TIME_SLA + "'.");
        if (StringUtils.isNotBlank(message)) {
            message += (StringUtils.endsWith(message, ".") ? " But " : " but ") +
                       "the elapsed time violated the SLA specified via '" + OPT_ELAPSED_TIME_SLA + "'.";
        } else {
            message = "Elapsed time violated SLA specified via '" + OPT_ELAPSED_TIME_SLA + "'.";
        }
    }

    public String getDetailedLogLink() { return detailedLogLink; }

    public void setDetailedLogLink(String detailedLogLink) { this.detailedLogLink = detailedLogLink; }

    @Override
    public String toString() {
        return new ToStringBuilder(this, SHORT_PREFIX_STYLE)
                   .append("success", success)
                   .append("message", message)
                   .append("exception", exception)
                   .append("paramValues", ArrayUtils.toString(paramValues))
                   .toString();
    }

    /** allow existing result to be updated/influenced by new result that is derived from the current step */
    public void update(StepResult newResult) {
        if (newResult == null) { return; }

        String newMessage = newResult.getMessage();
        if (StringUtils.isNotBlank(newMessage)) {
            this.message = StringUtils.appendIfMissing(this.message, " ") + newMessage;
        }

        if (newResult.failed()) { success = false; }
    }
}
