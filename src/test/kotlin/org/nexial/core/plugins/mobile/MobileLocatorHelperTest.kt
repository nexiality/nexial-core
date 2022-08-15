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

@file:Suppress("invisible_reference", "invisible_member")
package org.nexial.core.plugins.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.nexial.core.plugins.mobile.MobileLocatorHelper.Companion.handleNearbyLocator
import org.nexial.core.plugins.mobile.MobileType.ANDROID
import org.nexial.core.plugins.mobile.MobileType.IOS

class MobileLocatorHelperTest {
    private val xpathPrefix = "By.xpath: "
    private val prefixIOS = "//*[@enabled='true' and @visible='true' "
    private val prefixAndroid = "//*[@enabled='true' and @displayed='true' "

    @Test
    fun simple_nearby_test() {
        assertEquals(xpathPrefix + "(" + prefixAndroid +
                     "and following-sibling::*[1][@text='None of these' or .//*[@text='None of these']] " +
                     "and @clickable='true'])[last()]",
                     handleNearbyLocator(ANDROID, "{left-of=None of these}{clickable}").toString())

        assertEquals(xpathPrefix + "(" + prefixIOS +
                     "and preceding-sibling::*[1][@label='All of the above' or .//*[@label='All of the above']] " +
                     "and @clickable='true' and @class='UIRadioButton'])[1]",
                     handleNearbyLocator(IOS, "{right-of=All of the above}{clickable,class=UIRadioButton}").toString())

        // spaces, tabs and NL are welcomed
        assertEquals(xpathPrefix + "(" + prefixIOS +
                     "and preceding-sibling::*[1][@label='A = B' or .//*[@label='A = B']] " +
                     "and @clickable='true' and @class='android.widget.TextView'])[1]",
                     handleNearbyLocator(IOS,
                                         " { right-of = A = B } \n" +
                                         " { clickable,\tclass=android.widget.TextView }").toString())

        // special case: text with single and double quotes
        assertEquals(xpathPrefix + "(" + prefixIOS +
                     "and preceding-sibling::*[1][" +
                     "@label=concat('My ','\"','special','\"',' event',\"'\",'s date') or " +
                     ".//*[@label=concat('My ','\"','special','\"',' event',\"'\",'s date')]] " +
                     "and @clickable='true'])[1]",
                     handleNearbyLocator(IOS, " { right-of = My \"special\" event's date }  { clickable }").toString())
    }

    @Test
    fun test_nearby_with_colon() {
        assertEquals(xpathPrefix + "(" + prefixAndroid +
                     "and preceding-sibling::*[1][@text='None of these' or .//*[@text='None of these']]])[1]",
                     handleNearbyLocator(ANDROID, "{below:None of these}").toString())

        assertEquals(xpathPrefix + "(" + prefixAndroid +
                     "and following-sibling::*[1][@text=concat('User',\"'\",'s preferences') or" +
                     " .//*[@text=concat('User',\"'\",'s preferences')]] " +
                     "and @clickable='true'])[last()]",
                     handleNearbyLocator(ANDROID, "{above:User's preferences}{clickable}").toString())

        assertEquals(xpathPrefix + "(" + prefixIOS +
                     "and preceding-sibling::*[1][@label='All of the above' or .//*[@label='All of the above']] " +
                     "and @clickable='true' and @class='Radio'])[1]",
                     handleNearbyLocator(IOS, "{right-of : All of the above}{clickable,class=Radio}").toString())
    }

    @Test
    fun test_nearby_with_index() {
        assertEquals(
            xpathPrefix + "($prefixAndroid" +
            "and preceding-sibling::*[1][@text='chicken' or .//*[@text='chicken']] " +
            "and @class='android.widget.ImageButton'])[2]",
            handleNearbyLocator(ANDROID, "{right-of:chicken}{class=android.widget.ImageButton}{item:2}").toString()
        )

        try {
            handleNearbyLocator(ANDROID, "{right-of:chicken}{class=android.widget.ImageButton}{item:0}")
            fail("Exception not throw on {item:0} as expected")
        } catch (e: Throwable) {
            // expected
        }

        try {
            handleNearbyLocator(ANDROID, "{right-of:chicken}{class=android.widget.ImageButton}{item:-1}")
            fail("Exception not throw on {item:-1} as expected")
        } catch (e: Throwable) {
            // expected
        }

        try {
            handleNearbyLocator(ANDROID, "{right-of:chicken}{class=android.widget.ImageButton}{item: first}")
            fail("Exception not throw on {item: first} as expected")
        } catch (e: Throwable) {
            // expected
        }
    }

    @Test
    fun test_nearby_with_polymatcher() {
        assertEquals(
            xpathPrefix + "($prefixAndroid" +
            "and preceding-sibling::*[1][@text='chicken' or .//*[@text='chicken']] " +
            "and contains(translate(@class,\"IMAGE\",\"image\"),'image')])[2]",
            handleNearbyLocator(ANDROID, "{right-of:chicken}{item:2}{class=CONTAIN_ANY_CASE:Image}").toString()
        )

        assertEquals(
            xpathPrefix + "($prefixAndroid" +
            "and preceding-sibling::*[1][ends-with(@text,'chicken') or .//*[ends-with(@text,'chicken')]]])[2]",
            handleNearbyLocator(ANDROID, "{right-of:END:chicken}{item:2}").toString()
        )
    }

    @Test
    fun test_nearby_with_ancestor() {
        assertEquals(xpathPrefix + "(" + prefixAndroid +
                     "and @text='chicken' and contains(translate(@class,\"IMAGE\",\"image\"),'image')]" +
                     "/ancestor::*[contains(translate(@class,\"GROUP\",\"group\"),'group')])[last()]",
                     handleNearbyLocator(ANDROID, "{container:chicken}{class=CONTAIN_ANY_CASE:Image}").toString()
        )

        assertEquals(
            xpathPrefix + "(" + prefixAndroid + "and @text='Primary Physician']" +
            "/ancestor::*[contains(translate(@class,\"SCROLL\",\"scroll\"),'scroll')])[last()]",
            handleNearbyLocator(ANDROID, "{scroll-container:Primary Physician}").toString()
        )


        assertEquals(
            xpathPrefix + "(" + prefixAndroid + "and @text=concat('Patron',\"'\",'s Preference')]" +
            "/ancestor::*[contains(translate(@class,\"SCROLL\",\"scroll\"),'scroll')])[last()-2]",
            handleNearbyLocator(ANDROID, "{scroll-container:Patron's Preference}{item:2}").toString()
        )

    }
}