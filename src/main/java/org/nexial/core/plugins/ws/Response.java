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
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.nexial.commons.utils.DateUtility;

import static org.nexial.core.NexialConst.COOKIE_DATE_FORMAT;
import static org.nexial.core.NexialConst.COOKIE_DATE_FORMAT2;

public class Response implements Serializable {
    protected int returnCode;
    protected String statusText;
    protected byte[] rawBody;
    protected long elapsedTime;
    protected long contentLength;
    protected Map<String, String> headers = new HashMap<>();
    protected Map<String, Cookie> cookies = new HashMap<>();
    protected String payloadLocation;

    public int getReturnCode() { return returnCode; }

    public void setReturnCode(int returnCode) { this.returnCode = returnCode; }

    public String getStatusText() { return statusText; }

    public void setStatusText(String statusText) { this.statusText = statusText; }

    public String getBody() { return rawBody == null ? null : new String(rawBody); }

    public byte[] getRawBody() { return rawBody; }

    public void setRawBody(byte[] rawBody) { this.rawBody = rawBody; }

    public long getElapsedTime() { return elapsedTime; }

    public void setElapsedTime(long elapsedTime) { this.elapsedTime = elapsedTime; }

    public Map<String, String> getHeaders() { return headers; }

    public String getPayloadLocation() { return payloadLocation; }

    public void setPayloadLocation(String payloadLocation) { this.payloadLocation = payloadLocation; }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;

        for (String name : headers.keySet()) {
            if (StringUtils.equalsIgnoreCase(name, "set-cookie")) {
                // 	token=vw7Ihx8J4KOlMiAxyJDmEjojUtlW; Max-Age=719; Path=/; Expires=Wed, 09 Nov 2016 16:16:21 GMT; HttpOnly; Secure;
                harvestCookies(StringUtils.split(headers.get(name), ";"));
            }
        }
    }

    public Map<String, Cookie> getCookies() { return cookies; }

    public long getContentLength() { return contentLength; }

    public void setContentLength(long contentLength) { this.contentLength = contentLength; }

    @Override
    public String toString() {
        return "returnCode=" + returnCode + "\n" +
               "statusText=" + statusText + "\n" +
               "headers=" + headers + "\n" +
               "contentLength=" + contentLength + "\n" +
               "elapsedTime=" + elapsedTime + "\n" +
               "body='" + (ArrayUtils.isEmpty(rawBody) ? "<NONE>" : StringUtils.left(getBody(), 500) + "...'");
    }

    protected void harvestCookies(String[] cookieParts) {
        BasicClientCookie cookie = null;
        for (String cookiePart : cookieParts) {
            Pair<String, String> nameValue;
            if (StringUtils.contains(cookiePart, "=")) {
                String[] pair = StringUtils.split(cookiePart, "=");
                nameValue = new ImmutablePair<>(StringUtils.trim(pair[0]), StringUtils.trim(pair[1]));
            } else {
                nameValue = new ImmutablePair<>(cookiePart, null);
            }

            String name = nameValue.getLeft();
            String value = nameValue.getRight();
            if (cookie == null) {
                cookie = new BasicClientCookie(name, value);
            } else {
                switch (name) {
                    case "Max-Age": {
                        cookie.setAttribute(name, value);
                        break;
                    }
                    case "Path": {
                        cookie.setPath(value);
                        break;
                    }
                    case "Expires": {
                        long expiryDate = DateUtility.formatTo(value, COOKIE_DATE_FORMAT);
                        if (expiryDate == -1) { expiryDate = DateUtility.formatTo(value, COOKIE_DATE_FORMAT2); }
                        cookie.setExpiryDate(new Date(expiryDate));
                        break;
                    }
                    case "HttpOnly": {
                        cookie.setAttribute(name, null);
                        break;
                    }
                    case "Secure": {
                        cookie.setSecure(true);
                        break;
                    }
                    default: {
                        // must be new cookie value=value pair
                        this.cookies.put(cookie.getName(), cookie);
                        cookie = new BasicClientCookie(name, value);
                    }
                }
            }
        }

        if (cookie != null) { this.cookies.put(cookie.getName(), cookie); }
    }
}
