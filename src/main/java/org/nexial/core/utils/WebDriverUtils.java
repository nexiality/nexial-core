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

package org.nexial.core.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.utils.TextUtils;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;

import static org.nexial.core.NexialConst.CTRL_KEY_END;
import static org.nexial.core.NexialConst.CTRL_KEY_START;
import static org.openqa.selenium.Keys.*;

public final class WebDriverUtils {
    public static final Map<String, Keys> CONTROL_KEY_MAPPING = initControlKeyMapping();
    public static final Map<String, Keys> KEY_MAPPING = initKeyMapping();

    private WebDriverUtils() { }

    public static Actions toSendKeyAction(WebDriver driver, WebElement elem, String keystrokes) {
        if (driver == null) { return null; }
        if (StringUtils.isEmpty(keystrokes)) { return null; }

        Actions actions = new Actions(driver);
        Stack<Keys> controlKeys = new Stack<>();
        if (elem != null) { actions = actions.moveToElement(elem); }

        // 1. is there any {...}?
        while (StringUtils.isNotEmpty(keystrokes)) {
            String nextKeyStroke = TextUtils.substringBetweenFirstPair(keystrokes, CTRL_KEY_START, CTRL_KEY_END);
            if (StringUtils.isBlank(nextKeyStroke)) {
                // 2. if none (or no more) {..} found, gather remaining string and create sendKey() action
                actions = addReleaseControlKeys(actions, null, controlKeys);
                actions = actions.sendKeys(TextUtils.toOneCharArray(keystrokes));
                break;
            }

            String keystrokeId = CTRL_KEY_START + nextKeyStroke + CTRL_KEY_END;

            // 3. if {..} found, let's push all the keystrokes before the found {..} to action
            String text = StringUtils.substringBefore(keystrokes, keystrokeId);
            if (StringUtils.isNotEmpty(text)) {
                actions = addReleaseControlKeys(actions, null, controlKeys);
                // actions = elem == null ? actions.sendKeys(keys) : actions.sendKeys(elem, keys);
                actions = actions.sendKeys(TextUtils.toOneCharArray(text));
            }

            // 4. keystrokes now contain the rest of the key strokes after the found {..}
            keystrokes = StringUtils.substringAfter(keystrokes, keystrokeId);

            // 5. if the found {..} is a single key, just add it as such (i.e. {CONTROL}{C})
            if (StringUtils.length(nextKeyStroke) == 1 && StringUtils.isAlphanumeric(nextKeyStroke)) {
                actions = actions.sendKeys(nextKeyStroke);
                actions = addReleaseControlKeys(actions, null, controlKeys);
            } else if (CONTROL_KEY_MAPPING.containsKey(keystrokeId)) {
                // 6. is the found {..} one of the control keys (CTRL, SHIFT, ALT)?
                Keys control = CONTROL_KEY_MAPPING.get(keystrokeId);
                controlKeys.push(control);
                actions = actions.keyDown(control);
            } else {
                // 7. if not, then it must one of the non-printable character
                Keys keystroke = KEY_MAPPING.get(keystrokeId);
                if (keystroke == null) { throw new RuntimeException("Unsupported/unknown key " + keystrokeId); }

                actions = actions.sendKeys(keystroke);
                actions = addReleaseControlKeys(actions, null, controlKeys);
            }

            // 8. loop back
        }

        // 9. just in case user put a control character at the end (not sure why though)
        actions = addReleaseControlKeys(actions, null, controlKeys);

        // 10. finally, all done!
        return actions;
    }

    public static Actions addReleaseControlKeys(Actions actions, WebElement element, Stack<Keys> controlKeys) {
        while (CollectionUtils.isNotEmpty(controlKeys)) {
            Keys keys = controlKeys.pop();
            actions = element != null ? actions.keyUp(element, keys) : actions.keyUp(keys);
        }
        return actions;
    }

    public static Actions sendKeysActions(WebDriver driver, WebElement element, String keystrokes) {
        if (driver == null) { return null; }
        if (StringUtils.isEmpty(keystrokes)) { return null; }

        Actions actions = new Actions(driver);
        Stack<Keys> controlKeys = new Stack<>();

        if (element != null) { actions.moveToElement(element); }

        while (StringUtils.isNotEmpty(keystrokes)) {
            String nextKeyStroke = TextUtils.substringBetweenFirstPair(keystrokes, CTRL_KEY_START, CTRL_KEY_END);
            if (StringUtils.isBlank(nextKeyStroke)) { break; }

            String keystrokeId = CTRL_KEY_START + nextKeyStroke + CTRL_KEY_END;
            if (keystrokeId == null) { throw new RuntimeException("Unsupported/unknown key " + keystrokeId); }

            Keys controlKey = CONTROL_KEY_MAPPING.get(keystrokeId);
            if (controlKey == null) { throw new RuntimeException("Unsuppported/unknown key " + keystrokeId); }

            controlKeys.push(controlKey);
            actions.keyDown(controlKey);

            keystrokes = StringUtils.substringAfter(keystrokes, keystrokeId);

            // removing duplicate keys if any
            if (StringUtils.contains(keystrokes, keystrokeId)) {
                keystrokes = StringUtils.remove(keystrokes, keystrokeId);
            }
        }
        // click the locator with keydown
        actions.click();

        // release all the keys
        actions = addReleaseControlKeys(actions, null, controlKeys);
        return actions;
    }

    public static Map<String, Keys> initControlKeyMapping() {
        Map<String, Keys> map = new HashMap<>();
        map.put("{SHIFT}", SHIFT);
        map.put("{CONTROL}", CONTROL);
        map.put("{ALT}", ALT);
        map.put("{META}", META);
        map.put("{WIN}", META);
        map.put("{COMMAND}", META);
        return map;
    }

    public static Map<String, Keys> initKeyMapping() {
        Map<String, Keys> map = new HashMap<>();

        map.put("{TAB}", TAB);
        map.put("{BACKSPACE}", BACK_SPACE);
        map.put("{BKSP}", BACK_SPACE);
        map.put("{SPACE}", SPACE);
        map.put("{ENTER}", ENTER);
        map.put("{INSERT}", INSERT);
        map.put("{DELETE}", DELETE);
        map.put("{ESCAPE}", ESCAPE);

        map.put("{HOME}", HOME);
        map.put("{END}", END);
        map.put("{LEFT}", LEFT);
        map.put("{RIGHT}", RIGHT);
        map.put("{UP}", UP);
        map.put("{DOWN}", DOWN);
        map.put("{PAGEUP}", PAGE_UP);
        map.put("{PAGEDOWN}", PAGE_DOWN);

        map.put("{F1}", F1);
        map.put("{F2}", F2);
        map.put("{F3}", F3);
        map.put("{F4}", F4);
        map.put("{F5}", F5);
        map.put("{F6}", F6);
        map.put("{F7}", F7);
        map.put("{F8}", F8);
        map.put("{F9}", F9);
        map.put("{F10}", F10);
        map.put("{F11}", F11);
        map.put("{F12}", F12);

        return map;
    }
}
