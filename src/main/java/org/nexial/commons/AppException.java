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

package org.nexial.commons;

/**
 *

 */
public class AppException extends Exception {
    protected String errorCode;

    public AppException() { super(); }

    public AppException(String message) { super(message); }

    public AppException(String message, Throwable cause) { super(message, cause); }

    /**
     * @param errorCode Sets a new value to error Code
     * @param errorMsg  Sets a new value to error Message
     */
    public AppException(String errorCode, String errorMsg, Throwable cause) {
        super(errorMsg, cause);
        this.errorCode = errorCode;
    }

    /** @return The Exception value of error code. */
    public String getErrorCode() { return errorCode; }

    /** @param errorCode Sets a new value to error code */
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }

    /** @return The Exception value of error message. */
    public String getErrorMsg() { return getMessage(); }

    @Override
    public String toString() {
        StringBuilder msg = new StringBuilder();
        if (errorCode != null) { msg.append("Error code: [").append(errorCode).append("]\n"); }
        if (getMessage() != null) { msg.append("Error message: [").append(getMessage()).append("]\n"); }
        if (getCause() != null) { msg.append("Caused by: ").append(getCause()).append("\n"); }
        return msg.toString();
    }
}
