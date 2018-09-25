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

package org.nexial.core.plugins.ws;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpRequest;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpUriRequest;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.utils.ExecUtil;
import org.openqa.selenium.Cookie;

import static org.nexial.core.NexialConst.*;
import static org.nexial.core.plugins.ws.WebServiceClient.hideAuthDetails;

public abstract class Request implements Serializable {
    protected String url;
    protected int connectionTimeout;
    protected int socketTimeout;
    protected boolean enableRedirects;
    protected boolean enableExpectContinue;
    protected boolean allowCircularRedirects;
    protected boolean allowRelativeRedirects;
    protected String method;
    protected Map<String, Object> headers = new HashMap<>();
    protected String contentType = "application/json";

    Request(ExecutionContext context) {
        Map<String, Object> reqHeaders = new HashMap<>();

        if (context == null) {
            setConnectionTimeout(DEF_WS_CONN_TIMEOUT);
            setSocketTimeout(DEF_WS_READ_TIMEOUT);
            setEnableExpectContinue(DEF_WS_ENABLE_EXPECT_CONTINUE);
            setEnableRedirects(DEF_WS_ENABLE_REDIRECTS);
            setAllowCircularRedirects(DEF_WS_CIRCULAR_REDIRECTS);
            setAllowRelativeRedirects(DEF_WS_RELATIVE_REDIRECTS);
            setHeaders(reqHeaders);
        } else {
            setConnectionTimeout(context.getIntData(WS_CONN_TIMEOUT, DEF_WS_CONN_TIMEOUT));
            setSocketTimeout(context.getIntData(WS_READ_TIMEOUT, DEF_WS_READ_TIMEOUT));
            setEnableExpectContinue(context.getBooleanData(WS_ENABLE_EXPECT_CONTINUE, DEF_WS_ENABLE_EXPECT_CONTINUE));
            setEnableRedirects(context.getBooleanData(WS_ENABLE_REDIRECTS, DEF_WS_ENABLE_REDIRECTS));
            setAllowCircularRedirects(context.getBooleanData(WS_ALLOW_CIRCULAR_REDIRECTS, DEF_WS_CIRCULAR_REDIRECTS));
            setAllowRelativeRedirects(context.getBooleanData(WS_ALLOW_RELATIVE_REDIRECTS, DEF_WS_RELATIVE_REDIRECTS));

            // these are the properties we want... however they might not be in the correct object type
            // since execution.getDataByPrefix() converts them to string automatically.
            Map<String, String> candidateProps = context.getDataByPrefix(WS_REQ_HEADER_PREFIX);
            if (MapUtils.isNotEmpty(candidateProps)) {
                Set<String> candidatePropKeys = candidateProps.keySet();
                for (String key : candidatePropKeys) {
                    Object value = context.getObjectData(WS_REQ_HEADER_PREFIX + key);
                    if (value instanceof String) {
                        // string values are for keeps since they have been treated with token replacement logic
                        reqHeaders.put(key, candidateProps.get(key));
                    } else {
                        reqHeaders.put(key, value);
                    }
                }
            }

            setHeaders(reqHeaders);
        }
    }

    public String getUrl() { return url; }

    public void setUrl(String url) { this.url = url; }

    public int getConnectionTimeout() { return connectionTimeout; }

    public void setConnectionTimeout(int connectionTimeout) { this.connectionTimeout = connectionTimeout; }

    public int getSocketTimeout() { return socketTimeout; }

    public void setSocketTimeout(int socketTimeout) { this.socketTimeout = socketTimeout; }

    public boolean isEnableRedirects() { return enableRedirects; }

    public void setEnableRedirects(boolean enableRedirects) { this.enableRedirects = enableRedirects; }

    public boolean isEnableExpectContinue() { return enableExpectContinue; }

    public void setEnableExpectContinue(boolean enableExpectContinue) {
        this.enableExpectContinue = enableExpectContinue;
    }

    public boolean isAllowCircularRedirects() { return allowCircularRedirects; }

    public void setAllowCircularRedirects(boolean allowCircularRedirects) {
        this.allowCircularRedirects = allowCircularRedirects;
    }

    public boolean isAllowRelativeRedirects() { return allowRelativeRedirects; }

    public void setAllowRelativeRedirects(boolean allowRelativeRedirects) {
        this.allowRelativeRedirects = allowRelativeRedirects;
    }

    public String getMethod() { return method; }

    public void setMethod(String method) { this.method = method; }

    public Map<String, Object> getHeaders() { return headers; }

    public void setHeaders(Map<String, Object> headers) { this.headers = headers; }

    public String getContentType() { return contentType; }

    public void setContentType(String contentType) { this.contentType = contentType; }

    @Override
    public String toString() {
        return "Request{" +
               "url='" + hideAuthDetails(url) + "', " +
               "method='" + method + "', " +
               "headers=" + headers + ", " +
               "contentType='" + contentType + "', " +
               "connectionTimeout=" + connectionTimeout + ", " +
               "socketTimeout=" + socketTimeout +
               '}';
    }

    protected abstract HttpUriRequest prepRequest(RequestConfig requestConfig) throws UnsupportedEncodingException;

    protected void setRequestHeaders(HttpRequest http) {
        addHeaderIfNotSpecified(WS_CONTENT_TYPE, getContentType());
        addHeaderIfNotSpecified(WS_USER_AGENT, ExecUtil.deriveJarManifest());

        Map<String, Object> requestHeaders = getHeaders();
        if (MapUtils.isNotEmpty(requestHeaders)) {
            for (String name : requestHeaders.keySet()) {
                Object value = requestHeaders.get(name);
                String headerValue;
                if (value instanceof Collection) {
                    Collection values = (Collection) value;
                    StringBuilder headerValues = new StringBuilder();
                    for (Object val : values) {
                        if (val instanceof Cookie) {
                            Cookie cookie = (Cookie) val;
                            headerValues.append(cookie.getName()).append("=").append(cookie.getValue()).append("; ");
                        } else {
                            headerValues.append(val.toString()).append("; ");
                        }
                    }

                    headerValue = StringUtils.removeEnd(headerValues.toString(), "; ");
                } else {
                    headerValue = value.toString();
                }

                if (http.containsHeader(name)) {
                    http.addHeader(name, http.getHeaders(name) + "; " + headerValue);
                } else {
                    http.addHeader(name, headerValue);
                }
            }
        }

    }

    protected void addHeaderIfNotSpecified(String key, String value) {
        if (StringUtils.isNotBlank(value) && !headers.containsKey(key)) { headers.put(key, value); }
    }
}
