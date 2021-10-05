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

package org.nexial.commons;

/**
 * Generalized exception for the business delegates ({@link BusinessDelegate}).
 * This class captures the action used when the exception occurs, so as to improve runtime analysis.
 */
public class BusinessException extends AppException {
    private static final long serialVersionUID = 2928940720516181063L;
    private String action;

    public BusinessException() { }

    public BusinessException(String action, String message) {
        super(message);
        this.action = action;
    }

    public BusinessException(String action, String message, Throwable _cause) {
        super(message, _cause);
        this.action = action;
    }

    public BusinessException(String action, String messageCode, String message, Throwable cause) {
        super(messageCode, message, cause);
        this.action = action;
    }

    public BusinessException(String action, String messageCode, String message) {
        super(messageCode, message, null);
        this.action = action;
    }

    public String getAction() { return action; }
}
