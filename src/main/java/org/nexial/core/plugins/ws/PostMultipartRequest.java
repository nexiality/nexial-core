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

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpRequest;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.nexial.commons.utils.FileUtil;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.utils.ConsoleUtils;
import org.nexial.core.utils.OutputFileUtils;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nonnull;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.http.entity.ContentType.DEFAULT_BINARY;
import static org.apache.http.entity.mime.HttpMultipartMode.*;
import static org.nexial.core.NexialConst.Ws.*;
import static org.nexial.core.SystemVariables.getDefault;
import static org.nexial.core.SystemVariables.getDefaultBool;

public class PostMultipartRequest extends PostRequest {
    protected static final Map<String, HttpMultipartMode> MULTIPART_MODES = initMultipartModes();
    protected String logId;
    protected ExecutionContext context;
    protected HttpEntity entity;

    PostMultipartRequest(ExecutionContext context) {
        super(context);
        this.context = context;
        this.logId = "post-multipart";
    }

    /**
     * payload is a "stringify" map of request (i.e. key1=value1\nkey2=value2\n...). {@literal fileParams} is a list
     * of request parameter keys that should be considered as files to be specified as multipart.
     */
    public void setPayload(String payload, String... fileParams) {
        MultipartEntityBuilder multipartBuilder = newMultipartBuilder(logId, context, payload, fileParams);
        entity = multipartBuilder.build();
    }

    protected MultipartEntityBuilder newMultipartBuilder(String logId,
                                                         ExecutionContext context,
                                                         String payload,
                                                         String... fileParams) {
        boolean verbose = context != null && context.isVerbose();
        if (verbose) {
            ConsoleUtils.log("payload = " + payload);
            ConsoleUtils.log("fileParams = " + ArrayUtils.toString(fileParams));
        }

        String mpSpec = getDefault(WS_MULTIPART_MODE);
        String mpCharset = null;
        if (context != null) {
            mpSpec = context.getStringData(WS_MULTIPART_MODE, getDefault(WS_MULTIPART_MODE));
            mpCharset = context.getStringData(WS_MULTIPART_CHARSET);
        }

        MultipartEntityBuilder multipartBuilder =
            MultipartEntityBuilder.create().setMode(MapUtils.getObject(MULTIPART_MODES, mpSpec, RFC6532));

        if (StringUtils.isNotBlank(mpCharset)) {
            try {
                multipartBuilder.setCharset(Charset.forName(mpCharset));
            } catch (Exception e) {
                // bad charset defined
                ConsoleUtils.error(logId, "Invalid charset specified for multipart request - %s: %s",
                                   mpCharset, e.getMessage());
            }
        }

        // split payload into parts; parts are separated by newline
        Map<String, String> params = TextUtils.toMap(payload, "\n", "=");
        ContentType contentType = resolveContentTypeAndCharset(getHeaders().get(WS_CONTENT_TYPE), DEFAULT_BINARY);
        boolean rawContent = context != null &&
                             context.getBooleanData(WS_REQ_FILE_AS_RAW, getDefaultBool(WS_REQ_FILE_AS_RAW));

        if (ArrayUtils.isNotEmpty(fileParams)) {
            Arrays.stream(fileParams).forEach(name -> {
                String filePath = params.remove(name);
                if (FileUtil.isFileReadable(filePath)) {
                    if (verbose) { ConsoleUtils.log(logId, "adding %s as %s file", filePath, contentType); }
                    if(contentType == null) {
                        multipartBuilder.addBinaryBody(name, new File(filePath));
                    } else {
                        String content = OutputFileUtils.resolveContent(filePath, context, false, !rawContent);
                        byte[] contentByte = StringUtils.isNotEmpty(content) ? content.getBytes(UTF_8) : new byte[0];
                        multipartBuilder.addBinaryBody(name, contentByte, contentType, new File(filePath).getName());
                    }
                } else {
                    ConsoleUtils.error(logId,
                                       "Unable to resolve [%s=%s] as a multipart file; %s might not be a valid path",
                                       name, filePath, filePath);
                }
            });
        }

        if (MapUtils.isNotEmpty(params)) {
            if (verbose) {
                ConsoleUtils.log(logId,
                                 "setting the remaining payload (%s) as %s",
                                 TextUtils.toString(params.keySet(), ","), contentType.toString());
            }
            params.forEach((name, value) -> multipartBuilder.addTextBody(name, value, contentType));
        }

        return multipartBuilder;
    }

    @Override
    protected void prepPostRequest(RequestConfig requestConfig, HttpEntityEnclosingRequestBase http)
        throws UnsupportedEncodingException {

        boolean verbose = context != null && context.isVerbose();

        if (verbose) { ConsoleUtils.log(logId, "preparing multipart request for " + http.getURI()); }
        http.setConfig(requestConfig);
        http.setEntity(entity);
        setRequestHeaders(http);

        if (verbose) {
            Arrays.stream(http.getAllHeaders())
                  .forEach(header -> ConsoleUtils.log(logId, "http header %s=%s", header.getName(), header.getValue()));
        }
    }

    @Override
    protected void setRequestHeaders(HttpRequest http) {
        boolean verbose = context != null && context.isVerbose();

        Map<String, Object> requestHeaders = new HashMap<>(getHeaders());

        // must NOT forcefully set content type as multipart; HttpClient framework does the magic
        requestHeaders.remove(WS_CONTENT_TYPE);

        if (MapUtils.isEmpty(requestHeaders)) {
            if (verbose) { ConsoleUtils.log(logId, "no additional request headers to set"); }
        } else {
            requestHeaders.keySet().forEach(name -> {
                if (verbose) {
                    ConsoleUtils.log(logId, "setting request header %s=%s", name, requestHeaders.get(name));
                }
                setRequestHeader(http, name, requestHeaders);
            });
        }
    }

    protected HttpEntity getEntity() { return entity; }

    @Nonnull
    private static HashMap<String, HttpMultipartMode> initMultipartModes() {
        HashMap<String, HttpMultipartMode> map = new HashMap<>();
        map.put("standard", RFC6532);
        map.put("strict", STRICT);
        map.put("browser", BROWSER_COMPATIBLE);
        return map;
    }
}
