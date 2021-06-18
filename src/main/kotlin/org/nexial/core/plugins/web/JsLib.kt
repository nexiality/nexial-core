package org.nexial.core.plugins.web

import org.apache.commons.lang3.StringUtils
import org.openqa.selenium.JavascriptExecutor

object JsLib {

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

    // @JvmStatic
    // fun scrollLeft(pixel: String) = "arguments[0].scrollBy($pixel,0)"
    //
    // @JvmStatic
    // fun scrollRight(pixel: String) = "arguments[0].scrollBy($pixel,0)"

    @JvmStatic
    fun scrollBy(xOffset:String, yOffset:String) = "arguments[0].scrollBy($xOffset,$yOffset)"

    @JvmStatic
    fun windowScrollBy(xOffset:String, yOffset:String) = "window.scrollBy($xOffset,$yOffset)"

    @JvmStatic
    fun clearValue() = "if (arguments && arguments[0]) {" +
                       "  if (arguments[0].setAttribute) { arguments[0].setAttribute('value', ''); }" +
                       "  arguments[0].value = '';" +
                       "}"

    @JvmStatic
    fun scrollIntoView() = "if (arguments[0]) {" +
                           "   if (arguments[0].scrollIntoViewIfNeeded) {" +
                           "       arguments[0].scrollIntoViewIfNeeded();" +
                           "   } else {" +
                           "       arguments[0].scrollIntoView(false);" +
                           "   }" +
                           "}"

    @JvmStatic
    fun highlight(waitMs: Int) = "var elem = arguments[0];" +
                                 "var oldBgColor = elem.style.backgroundColor || '';" +
                                 "elem.style.backgroundColor = arguments[1];" +
                                 "setTimeout(function () { elem.style.backgroundColor = oldBgColor; }, " + waitMs + ");"

    @JvmStatic
    fun highlight(exec:JavascriptExecutor, js: () -> String, vararg arguments: Any?): Any? =
        exec.executeScript(js(), arguments)

    @JvmStatic
    fun isTrue(jsObject: Any?) = jsObject != null && StringUtils.equals(jsObject.toString(), "true")
}
