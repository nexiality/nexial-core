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

package org.nexial.commons.utils.web;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Mike Liu
 */
public class ServletUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServletUtils.class);
    private static final String ATTR_FORM_LOGIN = "FORM_LOGIN";
    private static final String HOME_PAGE_URL = "/index.app";
    private static final String ATTR_REFERER = "Referer";
    private static final byte GZIP_MAGIC_1 = 31;
    private static final byte GZIP_MAGIC_2 = -117;
    private static final int DEF_BUFFER_SIZE = 8192;

    private ServletUtils() { }

    public static String harvestRequestPayload(HttpServletRequest request) throws IOException {
        byte[] payload = IOUtils.toByteArray(request.getInputStream());

        String contentEncoding = request.getHeader("Content-Encoding");
        String acceptEncoding = request.getHeader("accept-encoding");

        if (StringUtils.isBlank(contentEncoding)) {
            // no compression algo specified, this is plaintext
            String requestContent = new String(payload);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("handling request as uncompressed since Content-Encoding is missing in HTTP header");
                LOGGER.debug("requestContent = " + requestContent);
            }
            return requestContent;
        }

        if (StringUtils.contains(acceptEncoding, "gzip") || StringUtils.contains(acceptEncoding, "deflate")) {
            String requestContent = handleCompressPayload(request, payload);
            if (LOGGER.isDebugEnabled()) { LOGGER.debug("requestContent = " + requestContent); }
            return requestContent;
        }

        String requestContent = new String(payload);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("handling request as uncompressed since HTTP header contains inconsistent information");
            LOGGER.debug("requestContent = " + requestContent);
        }
        return requestContent;
    }

    public static String handleCompressPayload(HttpServletRequest request, byte[] payload) throws IOException {
        String contentEncoding = request.getHeader("Content-Encoding");
        String acceptEncoding = request.getHeader("accept-encoding");

        if (LOGGER.isDebugEnabled()) { LOGGER.debug("request stream should be treated as " + contentEncoding); }

        ByteArrayInputStream bais = new ByteArrayInputStream(payload);

        // check for standard HTTP header and gzip magic number
        if (StringUtils.contains(acceptEncoding, "gzip")
            && StringUtils.contains(contentEncoding, "gzip")
            && payload[0] == GZIP_MAGIC_1 && payload[1] == GZIP_MAGIC_2) {
            if (LOGGER.isDebugEnabled()) { LOGGER.debug("handling request as GZIP stream"); }
            return handleGZipStream(bais);
        }

        // might be deflate (rfc 1951)
        if (StringUtils.contains(contentEncoding, "deflate") && StringUtils.contains(acceptEncoding, "deflate")) {
            return handleDeflateStream(payload, bais);
        }

        // don't know this one... just default
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("handling request as uncompressed stream since HTTP header is inconsistent");
        }
        return new String(payload);
    }

    public static boolean getBooleanAttribute(ServletRequest request, String name, boolean defaultVal) {
        if (request == null) { throw new IllegalArgumentException("request is null"); }
        Object attribute = request.getAttribute(name);
        if (attribute == null) { return defaultVal; }
        return Boolean.parseBoolean(attribute.toString());
    }

    public static int getIntAttribute(ServletRequest request, String name, int defaultVal) {
        if (request == null) { throw new IllegalArgumentException("request is null"); }
        Object attribute = request.getAttribute(name);
        if (attribute == null) { return defaultVal; }
        return Integer.parseInt(attribute.toString());
    }

    public static String getStringAttribute(ServletRequest request, String name, String defaultVal) {
        if (request == null) { throw new IllegalArgumentException("request is null"); }
        Object attribute = request.getAttribute(name);
        if (attribute == null) { return defaultVal; }
        return attribute.toString();
    }

    public static boolean containsValidReferrer(HttpServletRequest request) {
        return StringUtils.contains(request.getHeader(ATTR_REFERER), HOME_PAGE_URL);
    }

    public static void markSessionTimedOut(HttpServletRequest request) {
        request.getSession().setAttribute(ATTR_FORM_LOGIN, true);
    }

    public static boolean handleSessionTimeOut(HttpServletRequest request, HttpServletResponse response)
        throws IOException {

        HttpSession session = request.getSession();
        if (session.getAttribute(ATTR_FORM_LOGIN) != null) {
            session.setAttribute(ATTR_FORM_LOGIN, null);
            response.sendRedirect(request.getContextPath() + HOME_PAGE_URL);
            return true;
        }
        return false;
    }

    public static Map<String, List<String>> decodeParamToMap(HttpServletRequest request, String key) {
        Map<String, List<String>> map = new HashMap<>();

        if (request == null) { return map; }

        String encoded = request.getParameter(key);
        if (StringUtils.isBlank(encoded)) { return map; }

        return toMap(encoded);
    }

    public static Map<String, List<String>> decodeQueryStringToMap(HttpServletRequest request) {
        Map<String, List<String>> map = new HashMap<>();

        if (request == null) { return map; }

        return toMap(request.getQueryString());
    }

    private static Map<String, List<String>> toMap(String encoded) {
        Map<String, List<String>> map = new HashMap<>();
        if (StringUtils.isBlank(encoded)) { return map; }

        String decoded = new String(Base64.decodeBase64(encoded.getBytes()));
        if (StringUtils.isBlank(decoded)) { return map; }

        String[] pairs = StringUtils.split(decoded, "&");
        if (pairs == null || pairs.length < 1) {
            map.put(decoded, null);
            return map;
        }

        for (String pair : pairs) {
            String name = StringUtils.substringBefore(pair, "=");
            String value = StringUtils.substringAfter(pair, "=");

            if (map.containsKey(name)) {
                map.get(name).add(value);
            } else {
                List<String> list = new ArrayList<>();
                list.add(value);
                map.put(name, list);
            }
        }

        return map;
    }

    private static String handleDeflateStream(byte[] payload, ByteArrayInputStream bais) throws IOException {
        Inflater inflater = new Inflater(true);
        inflater.setInput(payload);

        byte[] buffer = new byte[DEF_BUFFER_SIZE];
        StringBuilder sb = new StringBuilder();
        try {
            if (LOGGER.isDebugEnabled()) { LOGGER.debug("handling request as DEFLATE stream"); }

            int bytesInflated = inflater.inflate(buffer);
            sb.append(new String(buffer, 0, bytesInflated));

            while (inflater.getRemaining() > 0) {
                buffer = new byte[DEF_BUFFER_SIZE];
                bytesInflated = inflater.inflate(buffer);
                sb.append(new String(buffer, 0, bytesInflated));
            }

            return sb.toString();
        } catch (DataFormatException e) {
            return handleZLibStream(bais);
        } finally {
            inflater.end();
        }
    }

    private static String handleZLibStream(ByteArrayInputStream bais) throws IOException {
        // zlib (rfc 1950)
        if (LOGGER.isDebugEnabled()) { LOGGER.debug("retry... handling request as GLIB stream"); }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        InflaterInputStream iis = new InflaterInputStream(bais);
        byte[] buffer = new byte[DEF_BUFFER_SIZE];
        try {
            int count = iis.read(buffer);
            while (count != -1) {
                baos.write(buffer, 0, count);
                count = iis.read(buffer);
            }
        } finally {
            iis.close();
        }

        return new String(baos.toByteArray());
    }

    private static String handleGZipStream(ByteArrayInputStream bais) throws IOException {
        GZIPInputStream gzis = new GZIPInputStream(bais);
        InputStreamReader reader = new InputStreamReader(gzis);
        BufferedReader in = new BufferedReader(reader);

        StringBuilder sb = new StringBuilder();
        String chunk;
        while ((chunk = in.readLine()) != null) { sb.append(chunk); }

        return sb.toString();
    }
}
