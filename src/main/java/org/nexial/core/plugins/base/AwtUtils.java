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

package org.nexial.core.plugins.base;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.core.utils.ConsoleUtils;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;

import static java.awt.event.KeyEvent.*;

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

    @Nullable
    public static Dimension getScreenDimension(int monitorIndex) {
        GraphicsDevice[] screens = getAvailableScreens();
        if (screens == null) { return null; }
        if (ArrayUtils.isEmpty(screens)) { return null; }
        if (monitorIndex < 0 || screens.length <= monitorIndex) { monitorIndex = 0; }

        GraphicsConfiguration[] screenConfig = screens[monitorIndex].getConfigurations();

        Dimension allBounds = new Dimension();
        allBounds.width = screenConfig[0].getBounds().width;
        allBounds.height = screenConfig[0].getBounds().height;
        return allBounds;
    }

    @Nullable
    public static GraphicsDevice[] getAvailableScreens() {
        try {
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            return ge == null ? null : ge.getScreenDevices();
        } catch (Throwable e) {
            ConsoleUtils.error("Error when determining available screen(s): " + e.getMessage());
            return null;
        }
    }


    // DOESN'T WORK?
    /**
     * change the application's starting position (x, y) based on specific monitor ({@literal screenIndex}) and the
     * starting position of that screen ({@literal screenStartingPosition}).
     */
    // @NotNull
    // public static Point adjustForScreen(Point appStartingPosition, int screenIndex, Point screenStartingPosition) {
    //     GraphicsDevice[] screens = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
    //
    //     // default to 1st
    //     if (screenIndex < 1) { screenIndex = 0; }
    //
    //     // default to last
    //     if (screenIndex >= screens.length) { screenIndex = screens.length - 1; }
    //
    //     // target screen dimension
    //     Rectangle bounds = screens[screenIndex].getDefaultConfiguration().getBounds();
    //
    //     // Is double?
    //     double x = screenStartingPosition.x;
    //     // Decimal -> percentage
    //     if (x == Math.floor(x) && !Double.isInfinite(x)) { x *= bounds.x; }
    //
    //     // Decimal -> percentage
    //     double y = screenStartingPosition.y;
    //     if (y == Math.floor(y) && !Double.isInfinite(y)) { y *= bounds.y; }
    //
    //     x = bounds.x + x;
    //     y = appStartingPosition.y + y;
    //
    //     // make sure we stay within target screen bounds
    //     if (x > bounds.x) { x = bounds.x; }
    //     if (y > bounds.y) { y = bounds.y; }
    //
    //     return new Point((int) x, (int) y);
    // }

    public static void typeKey(String text) {
        if (StringUtils.isEmpty(text)) { throw new IllegalArgumentException("text is blank/null"); }

        Robot robot = getRobotInstance();

        text = StringUtils.replace(text, "[\\n]", "\n");
        text = StringUtils.replace(text, "[\\t]", "\t");

        char[] chars = text.toCharArray();
        for (char ch : chars) {

            Integer keyCode = SHIFT_NEEDED.get(ch);
            if (keyCode != null) {
                robot.keyPress(VK_SHIFT);
                robot.keyPress(keyCode);
                robot.keyRelease(keyCode);
                robot.keyRelease(VK_SHIFT);
                continue;
            }

            keyCode = TRANSLATION_NEEDED.get(ch);
            if (keyCode != null) {
                robot.keyPress(keyCode);
                robot.keyRelease(keyCode);
                continue;
            }

            boolean isUpper = ch >= 'A' && ch <= 'Z';
            if (isUpper) { robot.keyPress(VK_SHIFT); }
            robot.keyPress(Character.toUpperCase(ch));
            robot.keyRelease(Character.toUpperCase(ch));
            if (isUpper) { robot.keyRelease(VK_SHIFT); }

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
                robot.keyPress(VK_SHIFT);
            } else if (StringUtils.equals(fnKey, FN_CONTROL)) {
                robot.keyPress(VK_CONTROL);
            } else if (StringUtils.equals(fnKey, FN_ALT)) {
                robot.keyPress(VK_ALT);
            } else {
                ConsoleUtils.error("Unknown function key: " + fnKey);
            }
        }

        robot.keyPress(Character.toUpperCase(key.charAt(0)));
        robot.keyRelease(Character.toUpperCase(key.charAt(0)));

        ArrayUtils.reverse(fnKeys);
        for (String fnKey : fnKeys) {
            if (StringUtils.equals(fnKey, FN_SHIFT)) {
                robot.keyRelease(VK_SHIFT);
            } else if (StringUtils.equals(fnKey, FN_CONTROL)) {
                robot.keyRelease(VK_CONTROL);
            } else if (StringUtils.equals(fnKey, FN_ALT)) {
                robot.keyRelease(VK_ALT);
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
        if (window == null) { return false; }

        Graphics graphics = window.getGraphics();
        if (graphics != null) { graphics.dispose(); }

        Window owner = window.getOwner();
        if (owner != null) { owner.dispose(); }

        window.dispose();
        return true;
    }

    private static Map<Character, Integer> initShiftNeededMapping() {
        HashMap<Character, Integer> map = new HashMap<>();
        map.put('~', VK_BACK_QUOTE);
        map.put('!', VK_1);
        map.put('@', VK_2);
        map.put('#', VK_3);
        map.put('$', VK_4);
        map.put('%', VK_5);
        map.put('^', VK_6);
        map.put('&', VK_7);
        map.put('*', VK_8);
        map.put('(', VK_9);
        map.put(')', VK_0);
        map.put('_', VK_MINUS);
        map.put('+', VK_EQUALS);
        map.put('{', VK_OPEN_BRACKET);
        map.put('}', VK_CLOSE_BRACKET);
        map.put('|', VK_BACK_SLASH);
        map.put(':', VK_SEMICOLON);
        map.put('"', VK_QUOTE);
        map.put('<', VK_COMMA);
        map.put('>', VK_PERIOD);
        map.put('?', VK_SLASH);
        return map;
    }

    private static Map<Character, Integer> initTranslationNeededMapping() {
        HashMap<Character, Integer> map = new HashMap<>();
        map.put('\\', VK_BACK_SLASH);
        map.put('/', VK_SLASH);
        map.put('.', VK_PERIOD);
        map.put('-', VK_MINUS);
        map.put('`', VK_BACK_QUOTE);
        map.put('=', VK_EQUALS);
        map.put('[', VK_OPEN_BRACKET);
        map.put(']', VK_CLOSE_BRACKET);
        map.put(';', VK_SEMICOLON);
        map.put('\'', VK_QUOTE);
        map.put(',', VK_COMMA);
        map.put(' ', VK_SPACE);
        map.put('\n', VK_ENTER);
        map.put('\t', VK_TAB);
        return map;
    }

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
                    // robot.setAutoDelay(20);
                } catch (AWTException e) {
                    throw new RuntimeException("Invalid (and possibly headless) environment found: " + e.getMessage());
                }
            }
        }
    }
}
