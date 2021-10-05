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

import java.io.UnsupportedEncodingException;
import java.net.URI;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.nexial.core.model.ExecutionContext;

import static org.nexial.core.plugins.ws.DeleteWithPayloadRequest.HttpDeleteWithBody.METHOD_NAME;

public class DeleteWithPayloadRequest extends PostRequest {


    /**
     * Allows for HTTP DELETE requests to contain a body, which the HttpDelete class does not support.
     * Please see: http://stackoverflow.com/a/3820549/581722
     */
    public static class HttpDeleteWithBody extends HttpEntityEnclosingRequestBase {
        public final static String METHOD_NAME = "DELETE";

        public HttpDeleteWithBody() { super(); }

        public HttpDeleteWithBody(final URI uri) {
            super();
            setURI(uri);
        }

        /** @throws IllegalArgumentException if the uri is invalid. */
        public HttpDeleteWithBody(final String uri) {
            super();
            setURI(URI.create(uri));
        }

        @Override
        public String getMethod() { return METHOD_NAME; }
    }

    DeleteWithPayloadRequest(ExecutionContext context) {
        super(context);
        method = METHOD_NAME;
    }

    DeleteWithPayloadRequest(ExecutionContext context, String url, String payload, byte[] payloadBytes) {
        super(context, url, payload, payloadBytes);
        method = METHOD_NAME;
    }

    @Override
    protected HttpUriRequest prepRequest(RequestConfig requestConfig) throws UnsupportedEncodingException {
        HttpDeleteWithBody http = new HttpDeleteWithBody(url);
        prepPostRequest(requestConfig, http);
        return http;
    }
}
