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

package org.nexial.core.plugins.mobile

import org.junit.Assert.assertEquals
import org.junit.Test
import org.nexial.core.plugins.mobile.MobileLocatorHelper.Companion.handleNearbyLocator
import org.nexial.core.plugins.mobile.MobileType.ANDROID
import org.nexial.core.plugins.mobile.MobileType.IOS

class MobileLocatorHelperTest {
    private val xpathStringPrefix = "By.xpath: "

    @Test
    fun simple_nearby_test() {

        assertEquals("//*[@enabled='true' " +
                     "and @displayed='true' " +
                     "and following-sibling::*[1][@text='None of these'] " +
                     "and @clickable='true']",
                     handleNearbyLocator(ANDROID,
                                         "{left-of=None of these}{clickable}")
                         .toString().substringAfter(xpathStringPrefix))

        assertEquals("//*[@enabled='true' " +
                     "and @visible='true' " +
                     "and preceding-sibling::*[1][@text='All of the above'] " +
                     "and @clickable='true' " +
                     "and @class='UIRadioButton']",
                     handleNearbyLocator(IOS,
                                         "{right-of=All of the above}{clickable,class=UIRadioButton}")
                         .toString().substringAfter(xpathStringPrefix))

        // spaces, tabs and NL are welcomed
        assertEquals("//*[@enabled='true' " +
                     "and @visible='true' " +
                     "and preceding-sibling::*[1][@text='A = B'] " +
                     "and @clickable='true' " +
                     "and @class='android.widget.TextView']",
                     handleNearbyLocator(IOS,
                                         " { right-of = A = B } \n" +
                                         " { clickable,\tclass=android.widget.TextView }")
                         .toString().substringAfter(xpathStringPrefix))

        // special case: text with single and double quotes
        assertEquals("//*[@enabled='true' " +
                     "and @visible='true' " +
                     "and preceding-sibling::*[1][@text=concat('My ','\"','special','\"',' event',\"'\",'s date')] " +
                     "and @clickable='true']",
                     handleNearbyLocator(IOS, " { right-of = My \"special\" event's date }  { clickable }")
                         .toString().substringAfter(xpathStringPrefix))
    }
}