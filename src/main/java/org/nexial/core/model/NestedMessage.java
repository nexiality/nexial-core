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

import org.apache.commons.lang3.StringUtils;
import org.nexial.core.utils.MessageUtils;

import static org.nexial.core.excel.ExcelConfig.*;

/**
 * at times it is useful, and maybe even critical, to display multiple lines of information post execution of a
 * test step.  This class is used to capture the message and the presentation of such message, which would usually
 * render one line below the associated test step.
 */
public class NestedMessage {
    private String message;
    private boolean isPass;
    private String resultMessage;

    public NestedMessage(String message) {
        this.message = StringUtils.defaultString(message, "");
        processMessage();
    }

    public String getMessage() { return message; }

    public void setMessage(String message) { this.message = message; }

    public boolean isPass() { return isPass; }

    public String getResultMessage() { return resultMessage; }

    private void processMessage() {
        if (MessageUtils.isSkipped(message)) {
            isPass = false;
            resultMessage = MSG_SKIPPED;
            return;
        }

        if (MessageUtils.isTestResult(message)) {
            if (MessageUtils.isPass(message)) {
                isPass = true;
                resultMessage = MSG_PASS;
                return;
            }

            if (MessageUtils.isFail(message)) {
                resultMessage = MSG_FAIL;
                return;
            }

            if (MessageUtils.isWarn(message)) {
                resultMessage = MSG_WARN;
                return;
            }
        }
    }
}
