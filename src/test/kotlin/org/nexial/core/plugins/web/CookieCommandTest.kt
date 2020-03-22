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
 */
package org.nexial.core.plugins.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.nexial.core.model.ExecutionContext
import org.nexial.core.model.MockExecutionContext
import org.openqa.selenium.Cookie
import org.openqa.selenium.Cookie.Builder
import org.openqa.selenium.WebDriver.Options
import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat

class CookieCommandTest {
    private val dateFormat: DateFormat = SimpleDateFormat("yyyy/MM/dd")

    @Test
    @Throws(ParseException::class)
    fun saveAllAsText() {
        val context: ExecutionContext = MockExecutionContext(false)

        val fixtures = mutableSetOf<Cookie>(
                Builder("a", "b").expiresOn(dateFormat.parse("2021/02/21"))
                        .path("/")
                        .domain(".mysite.com")
                        .build(),
                Builder("c", "d").expiresOn(dateFormat.parse("2021/03/15"))
                        .path("/xyz")
                        .domain("mysite.com")
                        .isSecure(true)
                        .build(),
                Builder("e", "f").expiresOn(dateFormat.parse("2022/04/15"))
                        .path("/")
                        .domain(".com")
                        .isSecure(true)
                        .isHttpOnly(false)
                        .build())

        val fakeStore = object : Options {
            override fun addCookie(cookie: Cookie?) {}
            override fun deleteCookieNamed(name: String?) {}
            override fun ime() = null
            override fun logs() = null
            override fun getCookieNamed(name: String?) = null
            override fun deleteAllCookies() {}
            override fun deleteCookie(cookie: Cookie?) {}
            override fun timeouts() = null
            override fun window() = null
            override fun getCookies() = fixtures
        }

        val cookieCommand = object : CookieCommand() {
            override fun getContext() = context
            override fun deriveCookieStore() = fakeStore
            override fun init(context: ExecutionContext?) {
                this.context = context
            }
        }

        cookieCommand.init(context)
        assertThat(cookieCommand.saveAllAsText("all_cookies", ""))
                .isNotNull.hasFieldOrPropertyWithValue("success", true)
        assertThat(context.getStringData("all_cookies"))
                .contains("a=b", "c=d")
                .isEqualTo("a=b; expires=Sun, 21 Feb 2021 12:00:00 PST; path=/; domain=.mysite.com; " +
                           "c=d; expires=Mon, 15 Mar 2021 12:00:00 PDT; path=/xyz; domain=mysite.com;secure; " +
                           "e=f; expires=Fri, 15 Apr 2022 12:00:00 PDT; path=/; domain=.com;secure;")
    }
}