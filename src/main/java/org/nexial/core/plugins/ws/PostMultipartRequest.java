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

package org.nexial.core.plugins.ws;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpRequest;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.nexial.commons.utils.FileUtil;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.utils.ConsoleUtils;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.apache.http.entity.ContentType.DEFAULT_BINARY;
import static org.apache.http.entity.mime.HttpMultipartMode.BROWSER_COMPATIBLE;
import static org.nexial.core.NexialConst.Ws.WS_CONTENT_TYPE;
import static org.nexial.core.NexialConst.Ws.WS_USER_AGENT;

public class PostMultipartRequest extends PostRequest {
    private static final String logId = "post-multipart";
    private final ExecutionContext context;
    private HttpEntity entity;

    PostMultipartRequest(ExecutionContext context) {
        super(context);
        this.context = context;
    }

    /**
     * payload is a "stringify" map of request (i.e. key1=value1\nkey2=value2\n...). {@literal fileParams} is a list
     * of request parameter keys that should be considered as files to be specified as multipart.
     */
    public void setPayload(String payload, String... fileParams) {
        boolean verbose = context.isVerbose();
        if (verbose) {
            ConsoleUtils.log("payload = " + payload);
            ConsoleUtils.log("fileParams = " + ArrayUtils.toString(fileParams));
        }

        MultipartEntityBuilder multipartEntityBuilder = MultipartEntityBuilder.create().setMode(BROWSER_COMPATIBLE);

        // split payload into parts; parts are separated by newline
        Map<String, String> params = TextUtils.toMap(payload, "\n", "=");
        if (ArrayUtils.isNotEmpty(fileParams)) {
            Arrays.stream(fileParams).forEach(name -> {
                String filePath = params.remove(name);
                if (FileUtil.isFileReadable(filePath)) {
                    if (verbose) { ConsoleUtils.log(logId, "adding %s as a multipart file", filePath); }
                    multipartEntityBuilder.addBinaryBody(name, new File(filePath));
                } else {
                    ConsoleUtils.error(logId,
                                       "Unable to resolve [%s=%s] as a multipart file; %s might not be a valid path",
                                       name, filePath, filePath);
                }
            });
        }

        ContentType contentType = getHeaders().containsKey(WS_CONTENT_TYPE) ?
                                  ContentType.create(String.valueOf(getHeaders().get(WS_CONTENT_TYPE))) :
                                  DEFAULT_BINARY;
        if (verbose) {
            ConsoleUtils.log(logId,
                             "setting the remaining payload (%s) as %s",
                             TextUtils.toString(params.keySet(), ","), contentType.toString());
        }
        params.forEach((name, value) -> multipartEntityBuilder.addTextBody(name, value, contentType));

        entity = multipartEntityBuilder.build();
    }

    @Override
    protected void prepPostRequest(RequestConfig requestConfig, HttpEntityEnclosingRequestBase http) {
        http.setConfig(requestConfig);
        http.setEntity(entity);
        setRequestHeaders(http);
    }

    @Override
    protected void setRequestHeaders(HttpRequest http) {
        Map<String, Object> requestHeaders = new HashMap<>(getHeaders());
        if (MapUtils.isEmpty(requestHeaders)) { return; }

        boolean verbose = context.isVerbose();

        // must NOT forcefully set content type as multipart; HttpClient framework does the magic
        requestHeaders.remove(WS_USER_AGENT);
        requestHeaders.remove(WS_CONTENT_TYPE);
        requestHeaders.keySet().forEach(name -> {
            if (verbose) { ConsoleUtils.log(logId, "setting request header %s=%s", name, requestHeaders.get(name)); }
            setRequestHeader(http, name, requestHeaders);
        });
    }
}
