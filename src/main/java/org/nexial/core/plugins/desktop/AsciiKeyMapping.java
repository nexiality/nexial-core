package org.nexial.core.plugins.desktop;

import org.openqa.selenium.Keys;

import java.util.HashMap;
import java.util.Map;

import static java.awt.event.KeyEvent.*;

/**
 * In some cases, and especially with older applications, the standard Selenium {@link Keys} doesn't work well.
 * Resorting to ASCII-mapped keycode might stand a better chance getting automation to work properly.
 * <p>
 * Specifically, the Selenium {@link Keys} uses the Unicode PUA (Private Use Area) code points, which ranges from
 * 0xE000-0xF8FF. Applications (again, mostly older ones) that favors the ASCII-based mapping do not recognize key code
 * at such a high range. Instead such applications recognize keycode ranges between 0x0001-0x00ff. This class provides
 * the ASCII-based mapped key code to support older applications.
 */
public class AsciiKeyMapping {

    public static final Map<String, CharSequence> CONTROL_KEY_MAPPING = initControlKeyMapping();
    public static final Map<String, CharSequence> KEY_MAPPING = initKeyMapping();

    private static Map<String, CharSequence> initControlKeyMapping() {
        Map<String, CharSequence> map = new HashMap<>();
        map.put("{SHIFT}", String.valueOf((char) VK_SHIFT));
        map.put("{CONTROL}", String.valueOf((char) VK_CONTROL));
        map.put("{CTRL}", String.valueOf((char) VK_CONTROL));
        map.put("{ALT}", String.valueOf((char) VK_ALT));
        map.put("{META}", String.valueOf((char) VK_META));
        map.put("{WIN}", String.valueOf((char) VK_WINDOWS));
        map.put("{COMMAND}", String.valueOf((char) VK_META));
        return map;
    }

    private static Map<String, CharSequence> initKeyMapping() {
        Map<String, CharSequence> map = new HashMap<>();

        map.put("{CONTEXT}", String.valueOf((char) VK_CONTEXT_MENU));

        map.put("{TAB}", String.valueOf((char) VK_TAB));
        map.put("{BACKSPACE}", String.valueOf((char) VK_BACK_SPACE));
        map.put("{BKSP}", String.valueOf((char) VK_BACK_SPACE));
        map.put("{SPACE}", String.valueOf((char) VK_SPACE));
        map.put("{ENTER}", String.valueOf((char) VK_ENTER));
        map.put("{INSERT}", String.valueOf((char) VK_INSERT));
        map.put("{DELETE}", String.valueOf((char) VK_DELETE));
        map.put("{DEL}", String.valueOf((char) VK_DELETE));
        map.put("{DECIMAL}", String.valueOf((char) VK_DECIMAL));
        map.put("{ESCAPE}", String.valueOf((char) VK_ESCAPE));

        map.put("{HOME}", String.valueOf((char) VK_HOME));
        map.put("{END}", String.valueOf((char) VK_END));
        map.put("{LEFT}", String.valueOf((char) VK_LEFT));
        map.put("{RIGHT}", String.valueOf((char) VK_RIGHT));
        map.put("{UP}", String.valueOf((char) VK_UP));
        map.put("{DOWN}", String.valueOf((char) VK_DOWN));
        map.put("{PAGEUP}", String.valueOf((char) VK_PAGE_UP));
        map.put("{PAGEDOWN}", String.valueOf((char) VK_PAGE_DOWN));

        map.put("{F1}", String.valueOf((char) VK_F1));
        map.put("{F2}", String.valueOf((char) VK_F2));
        map.put("{F3}", String.valueOf((char) VK_F3));
        map.put("{F4}", String.valueOf((char) VK_F4));
        map.put("{F5}", String.valueOf((char) VK_F5));
        map.put("{F6}", String.valueOf((char) VK_F6));
        map.put("{F7}", String.valueOf((char) VK_F7));
        map.put("{F8}", String.valueOf((char) VK_F8));
        map.put("{F9}", String.valueOf((char) VK_F9));
        map.put("{F10}", String.valueOf((char) VK_F10));
        map.put("{F11}", String.valueOf((char) VK_F11));
        map.put("{F12}", String.valueOf((char) VK_F12));

        map.put("{NUM_LOCK}", String.valueOf((char) VK_NUM_LOCK));
        map.put("{SCROLL_LOCK}", String.valueOf((char) VK_SCROLL_LOCK));
        map.put("{PRINT_SCREEN}", String.valueOf((char) VK_PRINTSCREEN));
        map.put("{PRNSCN}", String.valueOf((char) VK_PRINTSCREEN));
        map.put("{PRTSC}", String.valueOf((char) VK_PRINTSCREEN));

        return map;
    }


}
