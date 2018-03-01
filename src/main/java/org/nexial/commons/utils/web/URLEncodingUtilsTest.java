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

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class URLEncodingUtilsTest {

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    @Test
    public void encodeQueryString() {
        Assert.assertEquals(null, URLEncodingUtils.encodeQueryString(null));
        Assert.assertEquals("", URLEncodingUtils.encodeQueryString(""));
        Assert.assertEquals("Hello", URLEncodingUtils.encodeQueryString("Hello"));
        Assert.assertEquals("a=b&c=d&e=f", URLEncodingUtils.encodeQueryString("a=b&c=d&e=f"));
        Assert.assertEquals("Who=Me&Who+Else=&amp;+all+my+friends",
                            URLEncodingUtils.encodeQueryString("Who=Me&Who Else=&amp; all my friends"));
        Assert.assertEquals("Name=John+Doe&" +
                            "Favorite=Everything+warm+and+caffinated%21&" +
                            "Music=Jazz+&amp;+Blues",
                            URLEncodingUtils.encodeQueryString("Name=John Doe&" +
                                                               "Favorite=Everything warm and caffinated!&" +
                                                               "Music=Jazz &amp; Blues"));
    }
}