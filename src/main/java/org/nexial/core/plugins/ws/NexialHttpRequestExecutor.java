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

package org.nexial.core.plugins.ws;

import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.protocol.HttpRequestExecutor;

/**
 * override Apache HttpClient's implement to support the retrieval response content as lng as the response status code
 * is 200 or greater. This means that even if the response status code is 205 (RESET CONTENT), HttpClient will still
 * attempt to retrieve response body.
 * <p>
 * This implementation allows Nexial to support non-conforming API implementation (such as those that sends response
 * body with status code 205) and a better feature parity with popular API testing tools like Postman.
 */
public class NexialHttpRequestExecutor extends HttpRequestExecutor {
    @Override
    protected boolean canResponseHaveBody(HttpRequest request, HttpResponse response) {
        return super.canResponseHaveBody(request, response) ||
               response.getStatusLine().getStatusCode() >= HttpStatus.SC_OK;
    }
}
