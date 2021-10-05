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

import org.apache.commons.lang3.ArrayUtils;

public class AsyncResponse extends Response {
    private String body;

    public static AsyncResponse toAsyncResponse(Response response) {
        AsyncResponse asyncResponse = new AsyncResponse();
        asyncResponse.contentLength = response.contentLength;
        asyncResponse.elapsedTime = response.elapsedTime;
        asyncResponse.headers = response.headers;
        asyncResponse.returnCode = response.returnCode;
        asyncResponse.statusText = response.statusText;
        asyncResponse.payloadLocation = response.payloadLocation;
        asyncResponse.cookies = null;
        // not from a download request
        if (ArrayUtils.isNotEmpty(response.getRawBody())) { asyncResponse.body = response.getBody(); }

        return asyncResponse;
    }

    @Override
    public String getBody() { return body; }
}
