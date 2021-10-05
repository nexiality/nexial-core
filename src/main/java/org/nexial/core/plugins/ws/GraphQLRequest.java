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

import com.google.gson.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.entity.StringEntity;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.model.ExecutionContext;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.nexial.core.NexialConst.GSON_COMPRESSED;
import static org.nexial.core.NexialConst.Ws.WS_CONTENT_TYPE;
import static org.nexial.core.NexialConst.Ws.WS_JSON_CONTENT_TYPE;

public class GraphQLRequest extends PostRequest {
    private static final List<String> VALID_GRAPHQL_OPERATIONS = Arrays.asList("query", "mutation", "subscription");

    GraphQLRequest(ExecutionContext context, String url, String payload, byte[] payloadBytes) {
        super(context, url, payload, payloadBytes);
        prepPayload();
    }

    @Override
    public void setPayload(String payload) {
        this.payloadBytes = null;
        this.payload = payload;
        prepPayload();
    }

    @Override
    protected void prepPostRequest(RequestConfig requestConfig, HttpEntityEnclosingRequestBase http)
        throws UnsupportedEncodingException {
        http.setConfig(requestConfig);
        http.setEntity(new StringEntity(payload));

        Map<String, Object> requestHeaders = new HashMap<>(getHeaders());
        requestHeaders.put(WS_CONTENT_TYPE, WS_JSON_CONTENT_TYPE);
        requestHeaders.keySet().forEach(name -> setRequestHeader(http, name, requestHeaders));
    }

    private void prepPayload() {
        if (payloadBytes != null) {
            payload = new String(payloadBytes);
            payloadBytes = null;
        }

        if (StringUtils.isBlank(payload)) { return; }

        String operation = StringUtils.trim(TextUtils.substringBeforeWhitespace(payload));
        if (!VALID_GRAPHQL_OPERATIONS.contains(operation)) {
            throw new IllegalArgumentException("Invalid GraphQL operation found: " + operation);
        }

        JsonObject json = new JsonObject();
        json.addProperty("query", payload);
        payload = GSON_COMPRESSED.toJson(json);
    }
}
