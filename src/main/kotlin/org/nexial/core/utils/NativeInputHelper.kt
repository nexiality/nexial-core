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

import org.nexial.core.utils.KeystrokeParser.MODIFIERS
import java.awt.Robot
import java.awt.event.KeyEvent.*
import java.util.*

class NativeInputHelper {
    private val robot = run {
        val robot = Robot()
        // robot.autoDelay = 80
        // robot.isAutoWaitForIdle = true
        robot
    }

    fun typeKeys(keyLines: List<String>) =
        keyLines.forEach { KeystrokeParser.handleKey(robot, KeystrokeParser.toKeystrokes(it)) }
    fun click(modifiers: List<String>, x: Int, y: Int) = Mousey.click(robot, BUTTON1_DOWN_MASK, modifiers, x, y)
    fun middleClick(modifiers: List<String>, x: Int, y: Int) = Mousey.click(robot, BUTTON2_DOWN_MASK, modifiers, x, y)
    fun rightClick(modifiers: List<String>, x: Int, y: Int) = Mousey.click(robot, BUTTON3_DOWN_MASK, modifiers, x, y)
    fun mouseWheel(amount: Int, modifiers: List<String>, x: Int, y: Int) = Mousey.wheel(robot, amount, modifiers, x, y)
}

internal object KeystrokeParser {
    internal val MODIFIERS =
        mapOf("{SHIFT}" to VK_SHIFT,
              "{CONTROL}" to VK_CONTROL,
              "{ALT}" to VK_ALT, "{OPTION}" to VK_ALT,
              "{WINDOWS}" to VK_WINDOWS, "{WIN}" to VK_WINDOWS,
              "{CONTEXT}" to VK_CONTEXT_MENU,
              "{META}" to VK_META, "{COMMAND}" to VK_META)

    internal val FUNCTION_KEYS =
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

    internal val KEYS =
        mapOf("0" to VK_0, "1" to VK_1, "2" to VK_2, "3" to VK_3, "4" to VK_4, "5" to VK_5, "6" to VK_6, "7" to VK_7,
              "8" to VK_8, "9" to VK_9,
              "A" to VK_A, "B" to VK_B, "C" to VK_C, "D" to VK_D, "E" to VK_E, "F" to VK_F, "G" to VK_G, "H" to VK_H,
              "I" to VK_I, "J" to VK_J, "K" to VK_K, "L" to VK_L, "M" to VK_M, "N" to VK_N, "O" to VK_O, "P" to VK_P,
              "Q" to VK_Q, "R" to VK_R, "S" to VK_S, "T" to VK_T, "U" to VK_U, "V" to VK_V, "W" to VK_W, "X" to VK_X,
              "Y" to VK_Y, "Z" to VK_Z,
              "a" to VK_A, "b" to VK_B, "c" to VK_C, "d" to VK_D, "e" to VK_E, "f" to VK_F, "g" to VK_G, "h" to VK_H,
              "i" to VK_I, "j" to VK_J, "k" to VK_K, "l" to VK_L, "m" to VK_M, "n" to VK_N, "o" to VK_O, "p" to VK_P,
              "q" to VK_Q, "r" to VK_R, "s" to VK_S, "t" to VK_T, "u" to VK_U, "v" to VK_V, "w" to VK_W, "x" to VK_X,
              "y" to VK_Y, "z" to VK_Z,

              "+" to VK_ADD, "-" to VK_SUBTRACT, "/" to VK_DIVIDE, "*" to VK_MULTIPLY, "." to VK_DECIMAL,

              "`" to VK_BACK_QUOTE, "~" to VK_BACK_QUOTE,
              "!" to VK_1,
              "@" to VK_2,
              "#" to VK_3,
              "$" to VK_4,
              "%" to VK_5,
              "^" to VK_6,
              "&" to VK_7,
              "*" to VK_8,
              "(" to VK_9,
              ")" to VK_0,
              "-" to VK_MINUS, "_" to VK_MINUS,
              "=" to VK_EQUALS, "+" to VK_EQUALS,

              "[" to VK_OPEN_BRACKET, "{" to VK_OPEN_BRACKET,
              "]" to VK_CLOSE_BRACKET, "}" to VK_CLOSE_BRACKET,
              "\\" to VK_BACK_SLASH, "|" to VK_BACK_SLASH,

              ";" to VK_SEMICOLON, ":" to VK_SEMICOLON,
              "'" to VK_QUOTE, "\"" to VK_QUOTE,

              "," to VK_COMMA, "<" to VK_COMMA,
              "." to VK_PERIOD, ">" to VK_PERIOD,
              "/" to VK_SLASH, "?" to VK_SLASH,
              " " to VK_SPACE,

              "€" to VK_EURO_SIGN, "¡" to VK_INVERTED_EXCLAMATION_MARK)

    internal val NEED_SHIFT = listOf("~", "!", "@", "#", "$", "%", "^", "&", "*", "(", ")", "_", "+",
                                     "{", "}", "|", ":", "\"", "<", ">", "?",
                                     "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M",
                                     "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z")

    fun handleKey(robot: Robot, keystrokes: List<String>) {
        val modifiers = Stack<Int>()
        keystrokes.forEach { key ->
            when {
                MODIFIERS.containsKey(key)     -> {
                    val keyCode = MODIFIERS[key] ?: error("Unknown/unsupported modifier: $key")
                    robot.keyPress(keyCode)
                    modifiers.add(keyCode)
                }

                FUNCTION_KEYS.containsKey(key) -> {
                    val keyCode = FUNCTION_KEYS[key] ?: error("Unknown/unsupported function key: $key")
                    robot.keyPress(keyCode)
                    robot.keyRelease(keyCode)
                    while (modifiers.isNotEmpty()) robot.keyRelease(modifiers.pop())
                }

                KEYS.containsKey(key)          -> {
                    val keyCode = KEYS[key] ?: error("Unknown/unsupported key: $key")
                    if (NEED_SHIFT.contains(key)) {
                        robot.keyPress(VK_SHIFT)
                        modifiers.add(VK_SHIFT)
                    }

                    robot.keyPress(keyCode)
                    robot.keyRelease(keyCode)
                    while (modifiers.isNotEmpty()) robot.keyRelease(modifiers.pop())
                }

                else                           -> {
                    error("Unknown/unsupported key: $key")
                }
            }
        }

        while (modifiers.isNotEmpty()) robot.keyRelease(modifiers.pop())
    }

    internal fun toKeystrokes(keys: String): List<String> {

        fun toOneCharKeystrokes(characters: String) = characters.toCharArray().map { it + "" }

        val keystrokes = mutableListOf<String>()

        var allKeys = keys
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

internal object Mousey {

    fun click(robot: Robot, button: Int, modifiers: List<String>, x: Int, y: Int) {
        val mods = Stack<Int>()

        modifiers.forEach { key ->
            if (MODIFIERS.containsKey(key)) {
                val keyCode = MODIFIERS[key] ?: error("Unknown/unsupported modifier: $key")
                robot.keyPress(keyCode)
                mods.add(keyCode)
            } else {
                error("Unsupported key/modifier for mouse-click: $key")
            }
        }

        robot.mouseMove(x, y)
        robot.mousePress(button)
        robot.mouseRelease(button)

        while (mods.isNotEmpty()) robot.keyRelease(mods.pop())
    }

    fun wheel(robot: Robot, amount: Int, modifiers: List<String>, x: Int, y: Int) {
        val mods = Stack<Int>()

        modifiers.forEach { key ->
            if (MODIFIERS.containsKey(key)) {
                val keyCode = MODIFIERS[key] ?: error("Unknown/unsupported modifier: $key")
                robot.keyPress(keyCode)
                mods.add(keyCode)
            } else {
                error("Unsupported key/modifier for mouse-click: $key")
            }
        }

        robot.mouseMove(x, y)
        robot.mouseWheel(amount)

        while (mods.isNotEmpty()) robot.keyRelease(mods.pop())
    }
}