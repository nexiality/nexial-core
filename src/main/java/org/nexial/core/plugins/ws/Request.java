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

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpRequest;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ContentType;
import org.nexial.commons.utils.RegexUtils;
import org.nexial.commons.utils.web.URLEncodingUtils;
import org.nexial.core.model.ExecutionContext;
import org.openqa.selenium.Cookie;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static org.nexial.core.NexialConst.Ws.*;
import static org.nexial.core.SystemVariables.getDefaultBool;
import static org.nexial.core.SystemVariables.getDefaultInt;
import static org.nexial.core.plugins.ws.WebServiceClient.hideAuthDetails;

public abstract class Request implements Serializable {
    protected String url;
    protected int connectionTimeout;
    protected int socketTimeout;
    protected boolean keepAlive;
    protected boolean enableRedirects;
    protected boolean enableExpectContinue;
    protected boolean allowCircularRedirects;
    protected boolean allowRelativeRedirects;
    protected String method;
    protected Map<String, Object> headers = new HashMap<>();
    protected String contentType = WS_JSON_CONTENT_TYPE;

    Request(ExecutionContext context) {
        Map<String, Object> reqHeaders = new HashMap<>();

        int defaultWsConnTimeout = getDefaultInt(WS_CONN_TIMEOUT);
        int defaultWsReadTimeout = getDefaultInt(WS_READ_TIMEOUT);
        boolean defaultKeepAlive = getDefaultBool(WS_KEEP_ALIVE);
        boolean defaultEnableRedirect = getDefaultBool(WS_ENABLE_REDIRECTS);
        boolean defaultExpectContinue = getDefaultBool(WS_ENABLE_EXPECT_CONTINUE);
        boolean defaultWsCircularRedirect = getDefaultBool(WS_ALLOW_CIRCULAR_REDIRECTS);
        boolean defaultWsRelativeRedirects = getDefaultBool(WS_ALLOW_RELATIVE_REDIRECTS);
        if (context == null) {
            setConnectionTimeout(defaultWsConnTimeout);
            setSocketTimeout(defaultWsReadTimeout);
            setKeepAlive(defaultKeepAlive);
            setEnableExpectContinue(defaultExpectContinue);
            setEnableRedirects(defaultEnableRedirect);
            setAllowCircularRedirects(defaultWsCircularRedirect);
            setAllowRelativeRedirects(defaultWsRelativeRedirects);
        } else {
            setConnectionTimeout(context.getIntData(WS_CONN_TIMEOUT, defaultWsConnTimeout));
            setSocketTimeout(context.getIntData(WS_READ_TIMEOUT, defaultWsReadTimeout));
            setKeepAlive(context.getBooleanData(WS_KEEP_ALIVE, defaultKeepAlive));
            setEnableExpectContinue(context.getBooleanData(WS_ENABLE_EXPECT_CONTINUE, defaultExpectContinue));
            setEnableRedirects(context.getBooleanData(WS_ENABLE_REDIRECTS, defaultEnableRedirect));
            setAllowCircularRedirects(context.getBooleanData(WS_ALLOW_CIRCULAR_REDIRECTS, defaultWsCircularRedirect));
            setAllowRelativeRedirects(context.getBooleanData(WS_ALLOW_RELATIVE_REDIRECTS, defaultWsRelativeRedirects));

            // these are the properties we want... however they might not be in the correct object type
            // since execution.getDataByPrefix() converts them to string automatically.
            Map<String, String> candidateProps = context.getDataByPrefix(WS_REQ_HEADER_PREFIX);
            if (MapUtils.isNotEmpty(candidateProps)) {
                candidateProps.keySet().forEach(key -> {
                    Object value = context.getObjectData(WS_REQ_HEADER_PREFIX + key);
                    if (value instanceof String) {
                        // string values are for keeps since they have been treated with token replacement logic
                        reqHeaders.put(key, candidateProps.get(key));
                    } else {
                        reqHeaders.put(key, value);
                    }
                });
            }

            setHeaders(reqHeaders);
        }
    }

    public String getUrl() { return url; }

    public void setUrl(String url) {
        this.url = RegexUtils.match(url, "%[0-9A-F]{2}") ? url : URLEncodingUtils.encodePath(url);
    }

    public int getConnectionTimeout() { return connectionTimeout; }

    public void setConnectionTimeout(int connectionTimeout) { this.connectionTimeout = connectionTimeout; }

    public int getSocketTimeout() { return socketTimeout; }

    public void setSocketTimeout(int socketTimeout) { this.socketTimeout = socketTimeout; }

    public boolean isKeepAlive() { return keepAlive; }

    public void setKeepAlive(boolean keepAlive) { this.keepAlive = keepAlive; }

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

    protected ContentType resolveContentTypeAndCharset(Object header, String defaultContentType) {
        String contentTypeString = Objects.toString(header, defaultContentType);
        if (StringUtils.isBlank(contentTypeString)) { return null; }

        String charset = null;
        String contentType = StringUtils.trim(contentTypeString);
        if (StringUtils.contains(contentType, CONTENT_TYPE_CHARSET)) {
            charset = StringUtils.substringAfter(contentType, CONTENT_TYPE_CHARSET);
            if (StringUtils.contains(charset, "/")) { charset = null; }

            contentType = StringUtils.removeEnd(
                StringUtils.trim(StringUtils.substringBefore(contentType, CONTENT_TYPE_CHARSET)), ";");
        }

        if (StringUtils.isBlank(contentType)) { return null; }
        if (StringUtils.isBlank(charset)) { return ContentType.create(contentType); }
        return ContentType.create(contentType, charset);
    }

    protected void setRequestHeaders(HttpRequest http) {
        // must NOT forcefully set content type as multipart; HttpClient framework does the magic
        if (!(this instanceof PostMultipartRequest)) { addHeaderIfNotSpecified(WS_CONTENT_TYPE, getContentType()); }

        Map<String, Object> requestHeaders = getHeaders();
        if (MapUtils.isEmpty(requestHeaders)) { return; }

        // must NOT forcefully set content type as multipart; HttpClient framework does the magic
        if (this instanceof PostMultipartRequest) { requestHeaders.remove(WS_CONTENT_TYPE); }

        requestHeaders.keySet().forEach(name -> setRequestHeader(http, name, requestHeaders));
    }

    protected void setRequestHeader(HttpRequest http, String name, Map<String, Object> requestHeaders) {
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
            http.addHeader(name, toString(http.getHeaders(name)) + "; " + headerValue);
        } else {
            http.addHeader(name, headerValue);
        }
    }

    protected void addHeaderIfNotSpecified(String key, String value) {
        if (StringUtils.isNotBlank(value) && !headers.containsKey(key)) { headers.put(key, value); }
    }

    private String toString(Header[] headers) {
        if (ArrayUtils.isEmpty(headers)) { return ""; }

        StringBuilder buffer = new StringBuilder();
        for (Header header : headers) { buffer.append(header.getName()).append("=").append(header.getValue()); }
        return buffer.toString();
    }
}
