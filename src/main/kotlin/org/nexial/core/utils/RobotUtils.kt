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

package org.nexial.core.utils

import java.awt.Robot
import java.awt.event.KeyEvent.*
import java.util.*

object RobotUtils {
    private val MODIFIERS =
        mapOf("{SHIFT}" to VK_SHIFT,
              "{CONTROL}" to VK_CONTROL,
              "{ALT}" to VK_ALT,
              "{WINDOWS}" to VK_WINDOWS, "{WIN}" to VK_WINDOWS,
              "{CONTEXT}" to VK_CONTEXT_MENU,
              "{META}" to VK_META, "{COMMAND}" to VK_META)

    private val FUNCTION_KEYS =
        mapOf("{ESCAPE}" to VK_ESCAPE, "{ESC}" to VK_ESCAPE,
              "{F1}" to VK_F1, "{F2}" to VK_F2, "{F3}" to VK_F3, "{F4}" to VK_F4, "{F5}" to VK_F5,
              "{F6}" to VK_F6, "{F7}" to VK_F7, "{F8}" to VK_F8, "{F9}" to VK_F9, "{F10}" to VK_F10,
              "{F11}" to VK_F11, "{F12}" to VK_F12,
              "{PRINTSCREEN}" to VK_PRINTSCREEN, "{PRNTSCN}" to VK_PRINTSCREEN,
              "{SCROLL_LOCK}" to VK_SCROLL_LOCK,
              "{PAUSE}" to VK_PAUSE,

              "{BACKSPACE}" to VK_BACK_SPACE, "{BKSP}" to VK_BACK_SPACE,
              "{INSERT}" to VK_INSERT,
              "{DELETE}" to VK_DELETE, "{DEL}" to VK_DELETE,
              "{HOME}" to VK_HOME, "{END}" to VK_END, "{PAGEUP}" to VK_PAGE_UP, "{PAGEDOWN}" to VK_PAGE_DOWN,
              "{UP}" to VK_UP, "{DOWN}" to VK_DOWN, "{LEFT}" to VK_LEFT, "{RIGHT}" to VK_RIGHT,
              "{NUMLOCK}" to VK_NUM_LOCK,
              "{TAB}" to VK_TAB,
              "{CAPSLOCK}" to VK_CAPS_LOCK,
              "{SPACE}" to VK_SPACE,
              "{ENTER}" to VK_ENTER)

    private val KEYS =
        mapOf("0" to VK_0, "1" to VK_1, "2" to VK_2, "3" to VK_3, "4" to VK_4,
              "5" to VK_5, "6" to VK_6, "7" to VK_7, "8" to VK_8, "9" to VK_9,
              "A" to VK_A, "B" to VK_B, "C" to VK_C, "D" to VK_D, "E" to VK_E, "F" to VK_F, "G" to VK_G, "H" to VK_H,
              "I" to VK_I, "J" to VK_J, "K" to VK_K, "L" to VK_L, "M" to VK_M, "N" to VK_N, "O" to VK_O, "P" to VK_P,
              "Q" to VK_Q, "R" to VK_R, "S" to VK_S, "T" to VK_T, "U" to VK_U, "V" to VK_V, "W" to VK_W, "X" to VK_X,
              "Y" to VK_Y, "Z" to VK_Z,

              "+" to VK_ADD, "-" to VK_SUBTRACT, "/" to VK_DIVIDE, "*" to VK_MULTIPLY, "." to VK_DECIMAL,

              "`" to VK_BACK_QUOTE, "~" to VK_DEAD_TILDE,
              "!" to VK_EXCLAMATION_MARK,
              "@" to VK_AT,
              "#" to VK_NUMBER_SIGN,
              "$" to VK_DOLLAR,
              "%" to VK_5,
              "^" to VK_CIRCUMFLEX,
              "&" to VK_AMPERSAND,
              "*" to VK_ASTERISK,
              "(" to VK_LEFT_PARENTHESIS,
              ")" to VK_RIGHT_PARENTHESIS,
              "-" to VK_MINUS, "_" to VK_UNDERSCORE,
              "+" to VK_PLUS, "=" to VK_EQUALS,

              "{" to VK_BRACELEFT, "[" to VK_OPEN_BRACKET,
              "}" to VK_BRACERIGHT, "]" to VK_CLOSE_BRACKET,
              "|" to VK_BACK_SLASH, "\\" to VK_BACK_SLASH,

              ":" to VK_COLON, ";" to VK_SEMICOLON,
              "\"" to VK_QUOTEDBL, "'" to VK_QUOTE,

              "<" to VK_LESS, "," to VK_COMMA,
              ">" to VK_GREATER, "." to VK_PERIOD,
              "?" to VK_SLASH, "/" to VK_SLASH,

              "€" to VK_EURO_SIGN, "¡" to VK_INVERTED_EXCLAMATION_MARK)

    private val NEED_SHIFT = listOf("%", "|", "?")

    private val ROBOT = run {
        val robot = Robot()
        robot.autoDelay = 100
        robot.isAutoWaitForIdle = true
        robot
    }

    @JvmStatic
    fun typeKeys(keyLines: List<String>) {
        keyLines.forEach {
            val modifiers = Stack<Int>()
            val keystrokes = toKeystrokes(it)
            keystrokes.forEach { key ->
                ConsoleUtils.log(key)
                when {
                    MODIFIERS.containsKey(key)     -> {
                        val keyCode = MODIFIERS[key] ?: error("Unknown/unsupported modifier: $key")
                        ConsoleUtils.log("keypress: $key")
                        ROBOT.keyPress(keyCode)
                        modifiers.add(keyCode)
                    }

                    FUNCTION_KEYS.containsKey(key) -> {
                        val keyCode = FUNCTION_KEYS[key] ?: error("Unknown/unsupported function key: $key")
                        ConsoleUtils.log("keypress/keyrelease: $key")
                        ROBOT.keyPress(keyCode)
                        ROBOT.keyRelease(keyCode)
                        while (modifiers.isNotEmpty()) ROBOT.keyRelease(modifiers.pop())
                    }

                    KEYS.containsKey(key)          -> {
                        val keyCode = KEYS[key] ?: error("Unknown/unsupported key: $key")
                        if (NEED_SHIFT.contains(key)) {
                            ConsoleUtils.log("keypress on SHIFT: $key")
                            ROBOT.keyPress(VK_SHIFT)
                            modifiers.add(VK_SHIFT)
                        }

                        ConsoleUtils.log("keypress/keyrelease: $key")
                        ROBOT.keyPress(keyCode)
                        ROBOT.keyRelease(keyCode)
                        while (modifiers.isNotEmpty()) ROBOT.keyRelease(modifiers.pop())
                    }

                    else                           -> {
                        error("Unknown/unsupported key: $key")
                    }
                }
            }

            while (modifiers.isNotEmpty()) ROBOT.keyRelease(modifiers.pop())
        }
    }

    internal fun toKeystrokes(keys: String): List<String> {

        fun toOneCharKeystrokes(characters: String) = characters.toCharArray().map { it + "" }

        val keystrokes = mutableListOf<String>()

        var allKeys = keys.toUpperCase()
        while (allKeys != "") {
            allKeys = if (allKeys.contains(Regex("\\{.+}"))) {
                val indexStart = allKeys.indexOf("{")
                val indexEnd = allKeys.indexOf("}", indexStart + 1)

                if (indexStart > 0) keystrokes += toOneCharKeystrokes(allKeys.substring(0, indexStart))
                keystrokes += allKeys.substring(indexStart, indexEnd + 1)

                allKeys.substring(indexEnd + 1)
            } else {
                keystrokes += toOneCharKeystrokes(allKeys)
                ""
            }
        }

        return keystrokes
    }
}