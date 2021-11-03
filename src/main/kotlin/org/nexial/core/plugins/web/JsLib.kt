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

package org.nexial.core.plugins.web

import org.apache.commons.lang3.StringUtils
import org.openqa.selenium.JavascriptExecutor

object JsLib {
    private const val jsNoArgReturnFalse = "if (!arguments || !arguments[0]) { return false; } "
    private const val jsNoArgReturn = "if (!arguments || !arguments[0]) { return; } "

    @JvmStatic
    fun isVisible() = "var style = window.getComputedStyle(arguments[0]);" +
                      "return style.visibility === 'visible' && style.display !== 'none';"

    @JvmStatic
    fun isHidden() = "var style = window.getComputedStyle(arguments[0]);" +
                     "return style.visibility !== 'visible' || style.display === 'none';"

    @JvmStatic
    fun selectText() = "window.getSelection().selectAllChildren(arguments[0]);"

    @JvmStatic
    fun unselectText() = "window.getSelection().removeAllRanges();"

    @JvmStatic
    fun openWindow() = "window.open(arguments[0], arguments[1]);"

    @JvmStatic
    fun createLink(url: String) = "var a = document.createElement(\"a\");" +
                                  "var linkText = document.createTextNode(\"" + url + "\");" +
                                  "a.appendChild(linkText);" +
                                  "a.title = \"" + url + "\";" +
                                  "a.href = \"" + url + "\";" +
                                  "document.body.appendChild(a);"

    @JvmStatic
    fun clearValue() = jsNoArgReturn + 
                       " if (arguments[0].setAttribute) { arguments[0].setAttribute('value', ''); } " +
                       "arguments[0].value = '';"

    // @JvmStatic
    // fun scrollLeft(pixel: String) = "arguments[0].scrollBy($pixel,0)"
    //
    // @JvmStatic
    // fun scrollRight(pixel: String) = "arguments[0].scrollBy($pixel,0)"

    @JvmStatic
    fun scrollBy(xOffset: String, yOffset: String) = "arguments[0].scrollBy($xOffset,$yOffset)"

    @JvmStatic
    fun windowScrollBy(xOffset: String, yOffset: String) = "window.scrollBy($xOffset,$yOffset)"

    @JvmStatic
    fun scrollIntoView() = "if (arguments[0]) {" +
                           "   if (arguments[0].scrollIntoViewIfNeeded) {" +
                           "       arguments[0].scrollIntoViewIfNeeded();" +
                           "   } else {" +
                           "       arguments[0].scrollIntoView(false);" +
                           "   }" +
                           "}"

    // todo: no longer in use; candidate for deletion
    @JvmStatic
    fun highlight(waitMs: Int) = "var elem = arguments[0];" +
                                 "var oldBgColor = elem.style.backgroundColor || '';" +
                                 "elem.style.backgroundColor = arguments[1];" +
                                 "setTimeout(function () { elem.style.backgroundColor = oldBgColor; }, " + waitMs + ");"

    @JvmStatic
    fun highlight(exec: JavascriptExecutor, js: () -> String, vararg arguments: Any?): Any? =
        exec.executeScript(js(), arguments)

    @JvmStatic
    fun isTrue(jsObject: Any?) = jsObject != null && StringUtils.equals(jsObject.toString(), "true")

    private const val isCheckboxOrRadio =
        "(arguments[0].hasAttribute('type','checkbox') || arguments[0].hasAttribute('type','radio'))"

    @JvmStatic
    fun check() = "if ($isCheckboxOrRadio && !arguments[0].checked) { arguments[0].click(); }"

    @JvmStatic
    fun uncheck() = "if ($isCheckboxOrRadio && arguments[0].checked) { arguments[0].click(); }"

    @JvmStatic
    fun useReact() = "!!document.React || !!document.querySelector('[data-reactroot], [data-reactid], [class^=react]');"
    // !!window.next

    @JvmStatic
    fun useAngular() = "!!window.angular || " +
                       "!!document.querySelector('.ng-binding, [ng-app], [data-ng-app], [ng-controller]," +
                       " [data-ng-controller], [ng-repeat], [data-ng-repeat], script[src*=\"angular.js\"]," +
                       " script[src*=\"angular.min.js\"], [ng-version], [class^=ng-]')"

    @JvmStatic
    fun useJQuery() = "!!window.jQuery"

    @JvmStatic
    fun getComputedCssValue(property: String) =
        "return window.getComputedStyle(arguments[0]).getPropertyValue('$property')"

    @JvmStatic
    fun findCssMatchingElements(attribute: String, value: String) =
        "var targets = Array(); " +
        "document.querySelectorAll(\"*\").forEach(function(elem, index) {" +
        "   if (elem.style.$attribute === '$value' || " +
        "       window.getComputedStyle(elem).getPropertyValue(\"$attribute\") === '$value') {" +
        "       targets.push(elem);" +
        "   }" +
        "});" +
        "return targets;"

    @JvmStatic
    fun toHexColor(colorName: String) =
        "let canvas = document.createElement('canvas'); " +
        "let ctx = canvas.getContext('2d'); " +
        "ctx.fillStyle = '" + colorName + "'; " +
        "let hexColor = ctx.fillStyle; " +
        "delete canvas; " +
        "return hexColor;"

    @JvmStatic
    fun isDisabled() = "return arguments[0] && arguments[0].disabled;"

    @JvmStatic
    fun click() = "$jsNoArgReturnFalse arguments[0].click(); return true;"

    @JvmStatic
    fun doubleClick() =
        jsNoArgReturnFalse +
        "var evt  = document.createEvent('MouseEvents');" +
        "evt.initEvent('dblclick', true, true);" +
        "return arguments[0].dispatchEvent(evt);"

    @JvmStatic
    fun hasHScrollbar() = "return document.documentElement.clientWidth < document.documentElement.scrollWidth"

    @JvmStatic
    fun hasVScrollbar() = "return document.documentElement.clientHeight < document.documentElement.scrollHeight"

    @JvmStatic
    fun mouseOut() = "$jsNoArgReturn arguments[0].mouseout();"

    @JvmStatic
    fun userAgent() = "return navigator.userAgent;"

    @JvmStatic
    fun removeAttr() = "if (arguments && arguments.length > 1) arguments[0].removeAttribute(arguments[1])"

    @JvmStatic
    fun updateAttr() = "if (arguments && arguments.length > 2) arguments[0].setAttribute(arguments[1], arguments[2])"

    @JvmStatic
    fun updateAttr(attribute: String, value: String) =
        "arguments[0].forEach(function(elem,index) { elem.style.$attribute = '$value'; });"

    object LocalStorage {
        @JvmStatic
        fun getValue(key: String) = "return window.localStorage.getItem('$key')"

        @JvmStatic
        fun remove(key: String) = "window.localStorage.removeItem('$key');"

        @JvmStatic
        fun update(key: String, value: String) =
            "window.localStorage.setItem('$key','${StringUtils.replace(value, "'", "\\'")}');"

        @JvmStatic
        fun clear() = "window.localStorage.clear();"
    }
}
