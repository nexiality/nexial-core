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

package org.nexial.commons.utils.web;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.utils.RegexUtils;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.utils.ConsoleUtils;
import org.springframework.web.util.UriUtils;

import static org.nexial.core.NexialConst.DEF_FILE_ENCODING;

public final class URLEncodingUtils {
    private static final List<Character> ACCEPTABLE_PATH_CHARS = Arrays.asList('$', '-', '_', '.', '+', '!', '*',
                                                                               '\'', '(', ')', ',', '/');
    private static final String TMP_AMPERSAND = "0-_-0";
    private static final String TMP_ENCODED_PREFIX = "$$--";
    private static final String TMP_ENCODED_POSTFIX = "--$$";
    private static final String REGEX_URI = "(.*)\\%([0-9]{2})(.*)";

    private URLEncodingUtils() { }

    public static String encodePath(String url) {
        if (StringUtils.isBlank(url)) { return url; }

        // 1. split off query string
        String query = StringUtils.substringAfter(url, "?");

        // 2. handle query string
        if (StringUtils.isNotEmpty(query)) {
            query = "?" + encodeQueryString(query);
            url = StringUtils.substringBefore(url, "?");
        }

        // 3. handle characters after http:// or https://
        String protocol = StringUtils.substringBefore(url, "://") + "://";
        url = StringUtils.substringAfter(url, "://");

        // 4. temp. replace %20 or similar so that we can zero in on % character
        String replacement = "$1" + TMP_ENCODED_PREFIX + "$2" + TMP_ENCODED_POSTFIX + "$3";
        while (RegexUtils.match(url, REGEX_URI)) { url = RegexUtils.replace(url, REGEX_URI, replacement); }

        StringBuilder encoded = new StringBuilder();
        char[] chars = url.toCharArray();
        for (char ch : chars) {
            if (Character.isLetterOrDigit(ch) || ACCEPTABLE_PATH_CHARS.contains(ch)) {
                encoded.append(ch);
            } else if (ch == ' ') {
                encoded.append("%20");
            } else {
                try {
                    encoded.append(URLEncoder.encode(ch + "", DEF_FILE_ENCODING));
                } catch (UnsupportedEncodingException e) {
                    ConsoleUtils.error("Unable to encode character '" + ch + "': " + e.getMessage());
                    encoded.append(ch);
                }
            }
        }

        url = StringUtils.removeEnd(StringUtils.replace(encoded.toString(), TMP_ENCODED_PREFIX, "%"),
                                    TMP_ENCODED_POSTFIX);

        // 4. join back
        return protocol + url + query;
    }

    public static String encodeQueryString(String queryString) {
        if (StringUtils.isBlank(queryString)) { return queryString; }

        StringBuilder encoded = new StringBuilder();
        if (StringUtils.startsWith(queryString, "?")) {
            encoded.append("?");
            queryString = StringUtils.removeStart(queryString, "?");
        }

        if (!StringUtils.contains(queryString, "=")) {
            try {
                return URLEncoder.encode(queryString, DEF_FILE_ENCODING);
            } catch (UnsupportedEncodingException e) {
                System.err.println("Unable to encode query string name '" + queryString + "': " + e.getMessage());
                return queryString;
            }
        }

        // temp replace &amp; with something else so that it won't get picked up by `TextUtils.toMap()`
        queryString = RegexUtils.replace(queryString, "(\\&[0-9A-Za-z]{2,}\\;)", TMP_AMPERSAND);

        Map<String, String> params = TextUtils.toMap(queryString, "&", "=");
        if (MapUtils.isEmpty(params)) { return encoded.toString(); }

        params.keySet().forEach(name -> {
            String value = params.get(name);
            try {
                value = StringUtils.replace(URLEncoder.encode(value, DEF_FILE_ENCODING), TMP_AMPERSAND, "%26");
            } catch (UnsupportedEncodingException e) {
                System.err.println("Unable to encode query string value '" + value + "': " + e.getMessage());
            }

            try {
                name = StringUtils.replace(URLEncoder.encode(name, DEF_FILE_ENCODING), TMP_AMPERSAND, "%26");
            } catch (UnsupportedEncodingException e) {
                System.err.println("Unable to encode query string name '" + value + "': " + e.getMessage());
            }

            encoded.append(name).append("=").append(value).append("&");
        });

        return StringUtils.removeEnd(encoded.toString(), "&");
    }

    public static String decodeString(String encoded) {
        try {
            return URLDecoder.decode(encoded, DEF_FILE_ENCODING);
        } catch (UnsupportedEncodingException e) {
            System.err.println("Unable to decode: " + e.getMessage());
            return encoded;
        }
    }

    public static String encodeAuth(String plain) { return UriUtils.encodeUserInfo(plain, DEF_FILE_ENCODING); }

    public static String decodeAuth(String encoded) { return UriUtils.decode(encoded, DEF_FILE_ENCODING); }
}
