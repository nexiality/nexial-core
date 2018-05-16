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

package org.nexial.core.plugins.base;

import java.awt.*;
import java.awt.event.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.core.utils.ConsoleUtils;

/**
 *
 */
public final class AwtUtils {
    private static final Map<Character, Integer> SHIFT_NEEDED = initShiftNeededMapping();
    private static final Map<Character, Integer> TRANSLATION_NEEDED = initTranslationNeededMapping();
    private static final String FN_SHIFT = "Shift";
    private static final String FN_CONTROL = "Ctrl";
    private static final String FN_ALT = "Alt";

    private static final ThreadLocal<Integer> SEMAPHORE = initSemaphore();

    private static Window window;
    private static GraphicsDevice graphicDevice;
    private static Robot robot;
    private static GraphicsConfiguration graphicsConfig;
    private static boolean shouldForcefullyTerminate = false;

    //private enum MetaKey {
    //	CONTROL(KeyEvent.VK_CONTROL),
    //	SHIFT(KeyEvent.VK_SHIFT),
    //	ALT(ALT_DOWN_MASK),
    //	WIN(META_DOWN_MASK),
    //	COMMAND(META_DOWN_MASK);
    //
    //	private int mask;
    //
    //	MetaKey(int mask) { this.mask = mask; }
    //
    //	public int getMask() { return mask; }
    //}

    private AwtUtils() { }

    public static GraphicsDevice getGraphicsDevice() {
        ensureReady();
        return graphicDevice;
    }

    public static GraphicsConfiguration getGraphicsConfiguration() {
        ensureReady();
        return graphicsConfig;
    }

    public static Robot getRobotInstance() {
        ensureReady();
        return robot;
    }

    public static void typeKey(String text) {
        if (StringUtils.isEmpty(text)) { throw new IllegalArgumentException("text is blank/null"); }

        Robot robot = getRobotInstance();

        text = StringUtils.replace(text, "[\\n]", "\n");
        text = StringUtils.replace(text, "[\\t]", "\t");

        char[] chars = text.toCharArray();
        for (char ch : chars) {

            Integer keycode = SHIFT_NEEDED.get(ch);
            if (keycode != null) {
                robot.keyPress(KeyEvent.VK_SHIFT);
                robot.keyPress(keycode);
                robot.keyRelease(keycode);
                robot.keyRelease(KeyEvent.VK_SHIFT);
                continue;
            }

            keycode = TRANSLATION_NEEDED.get(ch);
            if (keycode != null) {
                robot.keyPress(keycode);
                robot.keyRelease(keycode);
                continue;
            }

            boolean isUpper = ch >= 'A' && ch <= 'Z';
            if (isUpper) { robot.keyPress(KeyEvent.VK_SHIFT); }
            robot.keyPress(Character.toUpperCase(ch));
            robot.keyRelease(Character.toUpperCase(ch));
            if (isUpper) { robot.keyRelease(KeyEvent.VK_SHIFT); }

        }
    }

    public static void typeShortcut(String functionKeys, String key) {
        if (StringUtils.isBlank(functionKeys)) { throw new IllegalArgumentException("functionKeys is blank/null"); }
        if (StringUtils.isEmpty(key)) { throw new IllegalArgumentException("key is blank/null"); }
        if (StringUtils.length(key) != 1) { throw new IllegalArgumentException("key must be exactly 1 character"); }

        String[] fnKeys = StringUtils.split(functionKeys, "-");
        if (ArrayUtils.isEmpty(fnKeys)) { fnKeys = new String[]{functionKeys}; }

        Robot robot = getRobotInstance();

        for (String fnKey : fnKeys) {
            if (StringUtils.equals(fnKey, FN_SHIFT)) {
                robot.keyPress(KeyEvent.VK_SHIFT);
            } else if (StringUtils.equals(fnKey, FN_CONTROL)) {
                robot.keyPress(KeyEvent.VK_CONTROL);
            } else if (StringUtils.equals(fnKey, FN_ALT)) {
                robot.keyPress(KeyEvent.VK_ALT);
            } else {
                ConsoleUtils.error("Unknown function key: " + fnKey);
            }
        }

        robot.keyPress(Character.toUpperCase(key.charAt(0)));
        robot.keyRelease(Character.toUpperCase(key.charAt(0)));

        ArrayUtils.reverse(fnKeys);
        for (String fnKey : fnKeys) {
            if (StringUtils.equals(fnKey, FN_SHIFT)) {
                robot.keyRelease(KeyEvent.VK_SHIFT);
            } else if (StringUtils.equals(fnKey, FN_CONTROL)) {
                robot.keyRelease(KeyEvent.VK_CONTROL);
            } else if (StringUtils.equals(fnKey, FN_ALT)) {
                robot.keyRelease(KeyEvent.VK_ALT);
            } else {
                ConsoleUtils.error("Unknown function key: " + fnKey);
            }
        }
    }

    public static void mouseMove(int x, int y) {
        Robot robot = getRobotInstance();
        robot.mouseMove(x, y);
    }

    /**
     * helper method to forcefully terminate a Nexial runtime in case gui/awt is used and AWTEvent/listener
     * is still running.
     */
    static boolean mustTerminateForcefully() {
        if (window != null) {
            Graphics graphics = window.getGraphics();
            if (graphics != null) { graphics.dispose(); }

            Window owner = window.getOwner();
            if (owner != null) { owner.dispose(); }

            window.dispose();
            return true;
        }

        return shouldForcefullyTerminate;
    }

    private static Map<Character, Integer> initShiftNeededMapping() {
        HashMap<Character, Integer> map = new HashMap<>();
        map.put(':', KeyEvent.VK_SEMICOLON);
        map.put('~', KeyEvent.VK_BACK_QUOTE);
        map.put('!', KeyEvent.VK_1);
        map.put('@', KeyEvent.VK_2);
        map.put('#', KeyEvent.VK_3);
        map.put('$', KeyEvent.VK_4);
        map.put('%', KeyEvent.VK_5);
        map.put('^', KeyEvent.VK_6);
        map.put('&', KeyEvent.VK_7);
        map.put('*', KeyEvent.VK_8);
        map.put('(', KeyEvent.VK_9);
        map.put(')', KeyEvent.VK_0);
        map.put('_', KeyEvent.VK_MINUS);
        map.put('+', KeyEvent.VK_EQUALS);
        map.put('{', KeyEvent.VK_OPEN_BRACKET);
        map.put('}', KeyEvent.VK_CLOSE_BRACKET);
        map.put('|', KeyEvent.VK_BACK_SLASH);
        map.put(':', KeyEvent.VK_SEMICOLON);
        map.put('"', KeyEvent.VK_QUOTE);
        map.put('<', KeyEvent.VK_COMMA);
        map.put('>', KeyEvent.VK_PERIOD);
        map.put('?', KeyEvent.VK_SLASH);
        return map;
    }

    private static Map<Character, Integer> initTranslationNeededMapping() {
        HashMap<Character, Integer> map = new HashMap<>();
        map.put('\\', KeyEvent.VK_BACK_SLASH);
        map.put('/', KeyEvent.VK_SLASH);
        map.put('.', KeyEvent.VK_PERIOD);
        map.put('-', KeyEvent.VK_MINUS);
        map.put('`', KeyEvent.VK_BACK_QUOTE);
        map.put('=', KeyEvent.VK_EQUALS);
        map.put('[', KeyEvent.VK_OPEN_BRACKET);
        map.put(']', KeyEvent.VK_CLOSE_BRACKET);
        map.put(';', KeyEvent.VK_SEMICOLON);
        map.put('\'', KeyEvent.VK_QUOTE);
        map.put(',', KeyEvent.VK_COMMA);
        map.put(' ', KeyEvent.VK_SPACE);
        map.put('\n', KeyEvent.VK_ENTER);
        map.put('\t', KeyEvent.VK_TAB);
        return map;
    }

    //public static void click() {
    //	Robot robot = getRobotInstance();
    //	robot.mousePress(InputEvent.BUTTON1_MASK);
    //	robot.mouseRelease(InputEvent.BUTTON1_MASK);
    //}

    //public static void forceTermination() { shouldForcefullyTerminate = true; }

    private static ThreadLocal<Integer> initSemaphore() {
        ThreadLocal<Integer> semaphore = new ThreadLocal<>();
        semaphore.set(new AtomicInteger(RandomUtils.nextInt(0, Integer.MAX_VALUE)).getAndDecrement());
        return semaphore;
    }

    private static void ensureReady() {
        synchronized (SEMAPHORE) {
            if (graphicDevice == null) {
                GraphicsEnvironment graphicsEnv = GraphicsEnvironment.getLocalGraphicsEnvironment();
                if (graphicsEnv.isHeadlessInstance()) {
                    throw new RuntimeException("Invalid environment - headless environment found.");
                }

                graphicDevice = graphicsEnv.getDefaultScreenDevice();
                window = graphicDevice.getFullScreenWindow();
                graphicsConfig = graphicDevice.getDefaultConfiguration();
                if (window == null) { window = new Window(new Frame(graphicsConfig), graphicsConfig); }
                try {
                    robot = new Robot(graphicDevice);
                    robot.setAutoDelay(20);
                } catch (AWTException e) {
                    throw new RuntimeException("Invalid (and possibly headless) environment found: " + e.getMessage());
                }
            }
        }
        //
        //if (graphicsConfig == null) {
        //	synchronized (SEMAPHORE) {
        //		graphicsConfig = graphicDevice.getDefaultConfiguration();
        //		robot = new Robot(graphicDevice);
        //		robot.setAutoDelay(50);
        //	}
        //}
        //
        //if (robot == null) {
        //	synchronized (SEMAPHORE) {
        //		robot = new Robot(graphicDevice);
        //		robot.setAutoDelay(50);
        //	}
        //}
    }
}
