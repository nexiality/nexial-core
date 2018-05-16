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

package org.nexial.commons.http;

/**
 *
 */
public enum HttpStatusCode {
    OK(200, false),
    NOT_CONTENT(204, false),
    PERMANENTLY_MOVED(301, true),
    NOT_MODIFIED(304, false),
    BAD_REQUEST(400, true),
    UNAUTHORIZED(401, true),
    NOT_FOUND(404, true),
    NOT_ACCEPTABLE(406, true),
    REQUEST_TIMEOUT(408, true),
    CONFLICT(409, true),
    GONE(410, true),
    PRECONDITION_FAILED(412, true),
    REQUEST_ENTITY_TOO_LARGE(413, true),
    REQUEST_URI_TOO_LONG(414, true),
    INTERNAL_SERVER_ERROR(500, true),
    NOT_IMPLEMENTED(501, true),
    SERVICE_UNAVAILABLE(503, true);

    private int statusCode;
    private boolean noMoreResponse;

    HttpStatusCode(int statusCode, boolean noMoreResponse) {
        this.statusCode = statusCode;
        this.noMoreResponse = noMoreResponse;
    }

    public int getStatusCode() { return statusCode; }

    public boolean isNoMoreResponse() { return noMoreResponse; }
}
