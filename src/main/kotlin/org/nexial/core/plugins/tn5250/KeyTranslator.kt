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
package org.nexial.core.plugins.tn5250

import org.apache.commons.lang3.StringUtils
import org.nexial.commons.utils.TextUtils
import org.tn5250j.keyboard.KeyMnemonic
import org.tn5250j.keyboard.KeyMnemonic.*

object KeyTranslator {
    private val KEYMAP = initKeyMaps()
    private val MODIFIERS = initModifiers()
    private val SYSKEYS = initSyskeys()
    private val PRIORITY_KEY_MNEMONICS = initPriorityKeyMnemonics()
    private val KEY_MNEMONICS = initKeyMnemonics()

    @JvmStatic
    fun isModifier(key: String) = MODIFIERS.containsKey(key)

    @JvmStatic
    fun isSpecialKey(key: String) = KEYMAP.containsKey(key)

    @JvmStatic
    fun isSystemKey(key: String) = SYSKEYS.containsKey(key)

    @JvmStatic
    fun getModifier(key: String) = MODIFIERS[key]

    @JvmStatic
    fun getSpecialKey(key: String) = KEYMAP[key]

    @JvmStatic
    fun getSystemKey(key: String) = SYSKEYS[key]

    @JvmStatic
    fun translateKeyMnemonics(text: String?): String =
        if (StringUtils.isEmpty(text)) ""
        else {
            val translated =
                TextUtils.replaceStrings(TextUtils.replaceStrings(text, PRIORITY_KEY_MNEMONICS), KEY_MNEMONICS)

            // special case: tn5250 will misinterpret this as a "partial/incomplete" mnemonics... we need to replace `[`
            if (StringUtils.contains(translated, "[") && !StringUtils.contains(translated, "]"))
                StringUtils.remove(translated, "[")
            else
                translated
        }

    class KeyMeta {
        var keyText: String? = null
        var modifier = 0
        var keyCode = 0
        var keyChar = '\uFFFF'
        var keyLocation = 1

        fun keyText(keyText: String?): KeyMeta {
            this.keyText = keyText
            return this
        }

        fun modifier(modifier: Int): KeyMeta {
            this.modifier = modifier
            return this
        }

        fun keyCode(keyCode: Int): KeyMeta {
            this.keyCode = keyCode
            return this
        }

        fun keyChar(keyChar: Char): KeyMeta {
            this.keyChar = keyChar
            return this
        }

        fun keyLocation(keyLocation: Int): KeyMeta {
            this.keyLocation = keyLocation
            return this
        }
    }

    private fun initModifiers(): Map<String, KeyMeta> {
        val map = mutableMapOf<String, KeyMeta>()
        map["{SHIFT}"] = KeyMeta().keyText("SHIFT").keyCode(16).modifier(65).keyLocation(2)
        map["{CTRL}"] = KeyMeta().keyText("CTRL").keyCode(17).modifier(130).keyLocation(2)
        map["{ALT}"] = KeyMeta().keyText("ALT").keyCode(18).modifier(520).keyLocation(2)
        map["{WIN}"] = KeyMeta().keyText("WIN").keyCode(524).modifier(0).keyLocation(2)
        return map
    }

    private fun initPriorityKeyMnemonics(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        map["{SHIFT}{F1}"] = PF13.mnemonic
        map["{SHIFT}{F2}"] = PF14.mnemonic
        map["{SHIFT}{F3}"] = PF15.mnemonic
        map["{SHIFT}{F4}"] = PF16.mnemonic
        map["{SHIFT}{F5}"] = PF17.mnemonic
        map["{SHIFT}{F6}"] = PF18.mnemonic
        map["{SHIFT}{F7}"] = PF19.mnemonic
        map["{SHIFT}{F8}"] = PF20.mnemonic
        map["{SHIFT}{F9}"] = PF21.mnemonic
        map["{SHIFT}{F10}"] = PF22.mnemonic
        map["{SHIFT}{F11}"] = PF23.mnemonic
        map["{SHIFT}{F12}"] = PF24.mnemonic
        map["{SHIFT}{TAB}"] = BACK_TAB.mnemonic
        return map
    }

    private fun initKeyMnemonics(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        map["{RESET}"] = RESET.mnemonic
        map["{SYSREQ}"] = SYSREQ.mnemonic
        map["{ATTN}"] = ATTN.mnemonic
        map["{F1}"] = PF1.mnemonic
        map["{F2}"] = PF2.mnemonic
        map["{F3}"] = PF3.mnemonic
        map["{F4}"] = PF4.mnemonic
        map["{F5}"] = PF5.mnemonic
        map["{F6}"] = PF6.mnemonic
        map["{F7}"] = PF7.mnemonic
        map["{F8}"] = PF8.mnemonic
        map["{F9}"] = PF9.mnemonic
        map["{F10}"] = PF10.mnemonic
        map["{F11}"] = PF11.mnemonic
        map["{F12}"] = PF12.mnemonic
        map["{F13}"] = PF13.mnemonic
        map["{F14}"] = PF14.mnemonic
        map["{F15}"] = PF15.mnemonic
        map["{F16}"] = PF16.mnemonic
        map["{F17}"] = PF17.mnemonic
        map["{F18}"] = PF18.mnemonic
        map["{F19}"] = PF19.mnemonic
        map["{F20}"] = PF20.mnemonic
        map["{F21}"] = PF21.mnemonic
        map["{F22}"] = PF22.mnemonic
        map["{F23}"] = PF23.mnemonic
        map["{F24}"] = PF24.mnemonic
        map["{PA1}"] = PA1.mnemonic
        map["{PA2}"] = PA2.mnemonic
        map["{PA3}"] = PA3.mnemonic
        map["{TAB}"] = TAB.mnemonic
        map["{ENTER}"] = ENTER.mnemonic
        map["{ESCAPE}"] = SYSREQ.mnemonic
        map["{ESC}"] = SYSREQ.mnemonic
        // KEYMNEMONICS.put("{ESCAPE}", KeyMnemonic.ESCAPE.mnemonic);
        // KEYMNEMONICS.put("{ESC}", KeyMnemonic.ESC.mnemonic);
        map["{BACKSPACE}"] = BACK_SPACE.mnemonic
        map["{BKSP}"] = BACK_SPACE.mnemonic
        map["{DELETE}"] = DELETE.mnemonic
        map["{DEL}"] = DELETE.mnemonic
        map["{CLEAR}"] = CLEAR.mnemonic
        map["{PRINTSCREEN}"] = PRINT_SCREEN.mnemonic
        map["{PRTSCN}"] = PRINT_SCREEN.mnemonic
        map["{INSERT}"] = INSERT.mnemonic
        map["{PAGEUP}"] = PAGE_UP.mnemonic
        map["{PGUP}"] = PAGE_UP.mnemonic
        map["{PAGEDOWN}"] = PAGE_DOWN.mnemonic
        map["{PGDN}"] = PAGE_DOWN.mnemonic
        map["{END}"] = END_OF_FIELD.mnemonic
        map["{HOME}"] = HOME.mnemonic
        map["{LEFT}"] = LEFT.mnemonic
        map["{UP}"] = UP.mnemonic
        map["{RIGHT}"] = RIGHT.mnemonic
        map["{RRIGHT}"] = ROLL_RIGHT.mnemonic
        map["{DOWN}"] = DOWN.mnemonic
        return map
    }

    private fun initSyskeys(): Map<String, KeyMnemonic> {
        val map = mutableMapOf<String, KeyMnemonic>()
        map["{RESET}"] = RESET
        map["{SYSREQ}"] = SYSREQ
        map["{ATTN}"] = ATTN
        map["{ESC}"] = ATTN
        map["{ESCAPE}"] = ATTN
        return map
    }

    private fun initKeyMaps(): Map<String, KeyMeta?> {
        val map = mutableMapOf<String, KeyMeta?>()
        map["{F1}"] = KeyMeta().keyText("F1").keyCode(112)
        map["{F2}"] = KeyMeta().keyText("F2").keyCode(113)
        map["{F3}"] = KeyMeta().keyText("F3").keyCode(114)
        map["{F4}"] = KeyMeta().keyText("F4").keyCode(115)
        map["{F5}"] = KeyMeta().keyText("F5").keyCode(116)
        map["{F6}"] = KeyMeta().keyText("F6").keyCode(117)
        map["{F7}"] = KeyMeta().keyText("F7").keyCode(118)
        map["{F8}"] = KeyMeta().keyText("F8").keyCode(119)
        map["{F9}"] = KeyMeta().keyText("F9").keyCode(120)
        map["{F10}"] = KeyMeta().keyText("F10").keyCode(121)
        map["{F11}"] = KeyMeta().keyText("F11").keyCode(122)
        map["{F12}"] = KeyMeta().keyText("F12").keyCode(123)
        map["{F13}"] = KeyMeta().keyText("F13").keyCode(112).modifier(16).keyLocation(2)
        map["{F14}"] = KeyMeta().keyText("F14").keyCode(113).modifier(16).keyLocation(2)
        map["{F15}"] = KeyMeta().keyText("F15").keyCode(114).modifier(16).keyLocation(2)
        map["{F16}"] = KeyMeta().keyText("F16").keyCode(115).modifier(16).keyLocation(2)
        map["{F17}"] = KeyMeta().keyText("F17").keyCode(116).modifier(16).keyLocation(2)
        map["{F18}"] = KeyMeta().keyText("F18").keyCode(117).modifier(16).keyLocation(2)
        map["{F19}"] = KeyMeta().keyText("F19").keyCode(118).modifier(16).keyLocation(2)
        map["{F20}"] = KeyMeta().keyText("F20").keyCode(119).modifier(16).keyLocation(2)
        map["{F21}"] = KeyMeta().keyText("F21").keyCode(120).modifier(16).keyLocation(2)
        map["{F22}"] = KeyMeta().keyText("F22").keyCode(121).modifier(16).keyLocation(2)
        map["{F23}"] = KeyMeta().keyText("F23").keyCode(122).modifier(16).keyLocation(2)
        map["{F24}"] = KeyMeta().keyText("F24").keyCode(123).modifier(16).keyLocation(2)
        map["{TAB}"] = KeyMeta().keyText("TAB").keyCode(9).keyChar('\t')
        map["{ENTER}"] = KeyMeta().keyText("ENTER").keyCode(10).keyChar('\n')
        map["{BACKSPACE}"] = KeyMeta().keyText("BACKSPACE").keyCode(8).keyChar('\b')
        map["{BKSP}"] = map["{BACKSPACE}"]
        map["{DELETE}"] = KeyMeta().keyText("DELETE").keyCode(127).keyChar('\u007F')
        map["{DEL}"] = map["{DELETE}"]
        map["{PRINTSCREEN}"] = KeyMeta().keyText("PRINTSCREEN").keyCode(154)
        map["{PRTSCN}"] = map["PRINTSCREEN"]
        map["{INSERT}"] = KeyMeta().keyText("INSERT").keyCode(155)
        // doesn't seem to work
        // map["{SPACE}"] = KeyMeta().keyText("SPACE").keyCode(32).keyChar(' ')
        map["{PAGEUP}"] = KeyMeta().keyText("PAGEUP").keyCode(33)
        map["{PGUP}"] = map["PAGEUP"]
        map["{PAGEDOWN}"] = KeyMeta().keyText("PAGEDOWN").keyCode(34)
        map["{PGDN}"] = map["{PAGEDOWN}"]
        map["{END}"] = KeyMeta().keyText("END").keyCode(35)
        map["{HOME}"] = KeyMeta().keyText("HOME").keyCode(36)
        map["{LEFT}"] = KeyMeta().keyText("LEFT").keyCode(37)
        map["{UP}"] = KeyMeta().keyText("UP").keyCode(38)
        map["{RIGHT}"] = KeyMeta().keyText("RIGHT").keyCode(39)
        map["{DOWN}"] = KeyMeta().keyText("DOWN").keyCode(40)
        return map
    }
}