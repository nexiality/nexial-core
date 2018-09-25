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
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.nexial.commons.utils.TextUtils;
import org.springframework.web.util.UriUtils;

import static org.nexial.core.NexialConst.DEF_CHARSET;

public class URLEncodingUtilsTest {

    public static final Map<String, String> PLAIN_ENCODED =
        TextUtils.toMap("=",
                        "here's a space=here's%20a%20space",
                        "here are spaces and tabs\t\t  =here%20are%20spaces%20and%20tabs%09%09%20%20",
                        "hashes#$$#and-_-dashes=hashes%23$$%23and-_-dashes");

    @Test
    public void encodeQueryString() {
        Assert.assertNull(URLEncodingUtils.encodeQueryString(null));
        Assert.assertEquals("", URLEncodingUtils.encodeQueryString(""));
        Assert.assertEquals("Hello", URLEncodingUtils.encodeQueryString("Hello"));
        Assert.assertEquals("a=b&c=d&e=f", URLEncodingUtils.encodeQueryString("a=b&c=d&e=f"));
        Assert.assertEquals("Who=Me&Who+Else=%26+all+my+friends",
                            URLEncodingUtils.encodeQueryString("Who=Me&Who Else=&amp; all my friends"));
        Assert.assertEquals("Name=John+Doe&" +
                            "Favorite=Everything+warm+and+caffinated%21&" +
                            "Music=Jazz+%26+Blues",
                            URLEncodingUtils.encodeQueryString("Name=John Doe&" +
                                                               "Favorite=Everything warm and caffinated!&" +
                                                               "Music=Jazz &amp; Blues"));
    }

    @Test
    public void encode_many_ways() throws Exception {
        Assert.assertEquals("http://websitewithspace.com/here%27s%20a%20space",
                            "http://websitewithspace.com/" + UriUtils.encode("here's a space", DEF_CHARSET));

        PLAIN_ENCODED.forEach((plain, encoded) -> {
            try {
                Assert.assertEquals(encoded, UriUtils.encodeAuthority(plain, DEF_CHARSET));
            } catch (UnsupportedEncodingException e) {
                Assert.fail(e.getMessage());
            }
        });

        PLAIN_ENCODED.forEach((plain, encoded) -> {
            try {
                Assert.assertEquals(encoded, UriUtils.encodeQuery(plain, DEF_CHARSET));
            } catch (UnsupportedEncodingException e) {
                Assert.fail(e.getMessage());
            }
        });
    }

    @Test
    public void encodeAuth() {
        PLAIN_ENCODED.forEach((plain, encoded) -> Assert.assertEquals(encoded, URLEncodingUtils.encodeAuth(plain)));
    }

    @Test
    public void decodeAuth() {
        PLAIN_ENCODED.forEach((plain, encoded) -> Assert.assertEquals(plain, URLEncodingUtils.decodeAuth(encoded)));
    }
}