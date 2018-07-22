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

package org.nexial.core.utils;

/**
 * exception to be thrown when malformed JSON Path is encountered
 */
public class MalformedJsonPathException extends RuntimeException {
    private String jsonPath;

    public MalformedJsonPathException(String message, String jsonPath) {
        super(message);
        this.jsonPath = jsonPath;
    }

    public MalformedJsonPathException(String message, Throwable cause, String jsonPath) {
        super(message, cause);
        this.jsonPath = jsonPath;
    }

    public String getJsonPath() { return jsonPath; }
}
