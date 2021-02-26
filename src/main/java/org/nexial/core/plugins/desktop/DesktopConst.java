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

package org.nexial.core.plugins.desktop;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.typeadapters.RuntimeTypeAdapterFactory;
import org.apache.commons.lang3.StringUtils;
import org.nexial.core.utils.ConsoleUtils;
import org.openqa.selenium.WebElement;

import java.util.Arrays;
import java.util.List;

import static org.nexial.core.NexialConst.NAMESPACE;
import static org.nexial.core.NexialConst.NL;
import static org.nexial.core.plugins.desktop.DesktopUtils.printDetails;
import static org.nexial.core.plugins.desktop.ElementType.*;

public class DesktopConst {
    // login
    public static final String BTN_LOGIN = "Login";
    public static final String USERNAME = "username";
    public static final String PASWORD = "password";
    public static final String BTN_CANCEL = "Cancel";

    // general elements
    public static final String FORM = "form";

    public static final String NS_DESKTOP = NAMESPACE + "desktop.";
    public static final String DESKTOP_APPLICATION = NS_DESKTOP + "application";
    public static final String DESKTOP_PROCESS_ID = NS_DESKTOP + "process.id";
    public static final String DESKTOP_APP_VERSION = NS_DESKTOP + "app.version";
    public static final String DESKTOP_APP_ID = "desktop.app";
    public static final String CURRENT_DESKTOP_SESSION = NS_DESKTOP + "session";
    public static final String CURRENT_DESKTOP_CONTAINER = NS_DESKTOP + "container";
    public static final String CURRENT_DESKTOP_CONTAINER_NAME = NS_DESKTOP + "container.name";
    public static final String CURRENT_DESKTOP_LIST = NS_DESKTOP + "list";
    public static final String CURRENT_DESKTOP_LIST_NAME = NS_DESKTOP + "list.name";
    public static final String CURRENT_DESKTOP_TABLE = NS_DESKTOP + "table";
    public static final String CURRENT_DESKTOP_TABLE_NAME = NS_DESKTOP + "table.name";
    public static final String CURRENT_DESKTOP_TABLE_ROW = NS_DESKTOP + "table.row";
    public static final String CURRENT_DESKTOP_TABLE_ROW_NAME = NS_DESKTOP + "table.row.name";
    public static final String CURRENT_DESKTOP_TABLE_EDITABLE_COLUMN_NAME =
        CURRENT_DESKTOP_TABLE + "editable.column.name";
    public static final String CURRENT_DESKTOP_TABLE_EDITABLE_COLUMN_FOUND =
        CURRENT_DESKTOP_TABLE + "editable.column.found";
    public static final String CURRENT_DESKTOP_TABLE_CLICK_BEFORE_EDIT = CURRENT_DESKTOP_TABLE + ".clickBeforeEdit";
    public static final boolean DEF_DESKTOP_TABLE_CLICK_BEFORE_EDIT = true;
    public static final String CURRENT_DESKTOP_HIER_TABLE = NS_DESKTOP + "hiertable";
    public static final String CURRENT_DESKTOP_HIER_TABLE_NAME = NS_DESKTOP + "hiertable.name";
    public static final String DESKTOP_SCREENSHOT_FULLSCREEN = NS_DESKTOP + "fullScreenCapture";
    public static final boolean DEF_DESKTOP_SCREENSHOT_FULLSCREEN = false;
    public static final String DESKTOP_AUTO_CLEAR_MODAL_DIALOG = NS_DESKTOP + "autoClearModalDialog";
    public static final boolean DEF_AUTO_CLEAR_MODAL_DIALOG = false;
    public static final String DESKTOP_DIALOG_LOOKUP = NS_DESKTOP + "dialogLookup";
    public static final boolean DEF_DESKTOP_DIALOG_LOOKUP = false;

    public static final String DESKTOP_USE_TYPE_KEYS = NS_DESKTOP + "useTypeKeys";
    public static final boolean DEF_DESKTOP_USE_TYPE_KEYS = false;

    public static final String DEF_CONFIG_HOME = "/desktop/";
    public static final String DEF_CONFIG_FILENAME = "application.json";
    public static final String OPT_CONFIG_LOCATION_PREFIX = NS_DESKTOP + "config.";

    // tab
    public static final String TAB_CONTAINER = "tabContainer";
    public static final String TAB_WITH_CLOSE_BUTTON = "tabsWithCloseButton";

    // attributes
    public static final String ATTR_NAME = "Name";
    public static final String ATTR_BOUNDING_RECTANGLE = "BoundingRectangle";
    public static final String ATTR_IS_VALUE_PATTERN_AVAILABLE = "IsValuePatternAvailable";

    public static final String LOCATOR_MODAL_DIALOG = "*[@IsModal='True']";
    public static final String LOCATOR_DIALOG_TEXT = "//*[@ControlType='" + LABEL + "']";
    public static final String LOCATOR_DIALOG_TITLE = "*[@ControlType='" + TITLE_BAR + "']";
    public static final String LOCATOR_CLOSE_BUTTON = LOCATOR_DIALOG_TITLE +
                                                      "/*[@ControlType='ControlType.Button' and @Name='Close']";
    public static final String LOCATOR_DIALOG_CLOSE_BUTTON = "/*[@ControlType='" + WINDOW + "' and @IsModal='True']/" +
                                                             LOCATOR_CLOSE_BUTTON;

    public static final String LOCATOR_BUTTON = "//*[@ControlType='" + BUTTON + "' and @Name='{button}']";

    public static final String DEFAULT_APP_LOCATOR = "/*[@ControlType='ControlType.Window']";

    //Editor Locator
    public static final String LOCATOR_EDITOR = "*[@ControlType='" + ElementType.EDIT + "']";
    public static final String LOCATOR_COMBOBOX = "*[@ControlType='" + ElementType.COMBO + "']";
    public static final String LOCATOR_LIST = "*[@ControlType='" + ElementType.LIST + "']";
    public static final String LOCATOR_DOCUMENT = "*[@ControlType='ControlType.Document']";
    public static final String LOCATOR_RADIO = "*[@ControlType='ControlType.RadioButton']";

    // support TreeView-based data grid
    public final static String LOCATOR_HEADER_COLUMNS =
        "*[substring(@Name, string-length(@Name) - string-length('row 1') + 1) = 'row 1']/*[@Name!='Column Headers']";
    public final static String LOCATOR_HIER_HEADER_COLUMNS =
        "*[@ControlType='ControlType.Header']/*[@ControlType='ControlType.HeaderItem']";
    public static final String LOCATOR_HIER_TABLE_ROWS = "*[@ControlType='ControlType.DataItem']";
    public static final String LOCATOR_HIER_CELLS = LOCATOR_HIER_TABLE_ROWS + "[{row}]/*";
    public static final String LOCATOR_HIER_CELL = LOCATOR_HIER_CELLS + "[@Name='{column}']";
    public static final String LOCATOR_HIER_FIRST_CELL = LOCATOR_HIER_CELLS + "[1]";

    public final static String LOCATOR_TABLE_DATA = "*[contains(@Name, 'row ')]";
    public final static String LOCATOR_ROW = "*[contains(@Name, 'row {row}')][1]";
    public final static String LOCATOR_CELLS = LOCATOR_ROW + "/*[@Name!='Column Headers']";
    public final static String LOCATOR_FIRST_CELL = LOCATOR_ROW + "/*[@Name!='Column Headers'][1]";
    public final static String LOCATOR_CELL = LOCATOR_ROW + "/*[@Name='{column}']";
    public static final String LOCATOR_NEW_ROW = "*[contains(@Name, 'Add Row')]";
    public final static String LOCATOR_NEW_ROW_CELL = LOCATOR_NEW_ROW + "/*[@Name='{column}']";
    public final static String LOCATOR_ISTOPMOST = " and @IsTopmost='True'";
    public static final String LOCATOR_CUSTOM = "*[@ControlType='ControlType.Custom']";
    public static final String LOCATOR_TEXTBOX = "*[@ControlType='" + EDIT + "']";
    public static final String LOCATOR_TAB_ITEMS = "*[@ControlType='" + TAB_ITEM + "']";
    public static final String LOCATOR_TAB_ITEM = "*[@ControlType='" + TAB_ITEM + "' and @Name='${tabName}']";
    public static final String XPATH_FIRST_HIER_ROW = "*[@ControlType='" + TREE_ITEM + "'][1]";
    public static final String XPATH_NORMALIZE_CELL = "*[normalize-space(@Name)='${column}']";
    public static final String XPATH_MATCHING_HIER_CELL = "*[@ControlType='" + TREE_ITEM + "' and " +
                                                          "./*[normalize-space(@Name)='${column}' and @Value='${value}']]";
    // either list of list-items or just list-items
    public static final String LOCATOR_LIST_ITEM = "*[@ControlType='" + LIST_ITEM + "' and contains(@Name,'{value}')]" +
                                                   "|" + LOCATOR_LIST +
                                                   "/*[@ControlType='" + LIST_ITEM + "' and contains(@Name,'{value}')]";
    public static final String XPATH_SCROLLBARS = "*[@ControlType='" + SCROLLBAR + "']";

    // public static final int SCROLL_TOLERANCE = 25;
    public static final int MIN_CACHE_FILE_SIZE = 200;
    public static final int UNDEFINED = -1;
    public static final int TABLE_ROW_X_OFFSET = 30;
    public static final int TABLE_HEADER_HEIGHT = 24;
    public static final int TABLE_ROW_HEIGHT = 19;

    public static final String TABLE_CELL_UNCHECK = "[UNCHECK]";
    public static final String TABLE_CELL_CHECK = "[CHECK]";
    public static final String TABLE_CELL_CLICK = "[CLICK]";
    public static final String TABLE_CELL_CLEAR = "[CLEAR]";

    public static final String COMPONENT_APP = "app";
    public static final String APP_TITLEBAR = "TitleBar";
    public static final String APP_MENUBAR = "MenuBar";
    public static final String APP_TOOLBAR = "Toolbar";
    public static final String APP_STATUSBAR = "StatusBar";
    public static final String APP_DIALOG = "Dialog";
    public static final List<String> COMMON_DESKTOP_COMPONENTS =
        Arrays.asList(APP_TITLEBAR, APP_MENUBAR, APP_TOOLBAR, APP_STATUSBAR, APP_DIALOG);

    // public static final String SYSTEM_MENU_BAR = "System Menu Bar";

    public static final int BOUND_GROUP_TOLERANCE = 10;
    public static final String NESTED_CONTAINER_SEP = " :: ";

    // shortcut functionality
    public static final String SHORTCUT_PREFIX = "<[";
    public static final String SHORTCUT_POSTFIX = "]>";
    public static final String TEXT_INPUT_PREFIX = "<[{";
    public static final String TEXT_INPUT_POSTFIX = "}]>";
    public static final String SCRIPT_PREFIX_SHORTCUT = "shortcut: ";
    public static final String SHORTCUT_EXPAND_HIER_ROW = "<[CTRL-SPACE]><[CTRL-RIGHT]><[DOWN]><[HOME]>";
    public static final String SHORTCUT_COLLAPSE_HIER_ROW = "<[CTRL-SPACE]><[CTRL-LEFT]><[DOWN]>";
    public static final String SHORTCUT_EDIT_HIER_CELL = SCRIPT_PREFIX_SHORTCUT + "<[BACKSPACE]><[{${data}}]><[ENTER]>";

    // layout
    public static final FormLayout FORM_LAYOUT_LEFT_TO_RIGHT = FormLayout.newLeftToRightLayout(15);
    public static final FormLayout FORM_LAYOUT_2_LINE = FormLayout.newTwoLineLayout(15);
    public static final FormLayout FORM_LAYOUT_DEFAULT = FORM_LAYOUT_LEFT_TO_RIGHT;
    public static final String LAYOUT_LEFT_2_RIGHT = "left2right";
    public static final String LAYOUT_2LINES = "2lines";

    // dynamic token placeholder :
    public static final String XPATH_APP_VER = "string(/configuration/appSettings/add[@key='AppVersionMarker']/@value)";

    // xpath generation
    /**
     * requires the use of @AutomationId only if its value is not numeric.  In some applications, numeric automation id
     * represents a dynamically generated id that cannot be reliably reused.
     */
    public static final String XPATH_GEN_IGNORE_NUMERIC_AUTOMATION_ID = "IGNORE_NUMERIC_AUTOMATION_ID";
    /**
     * use @Name only if @AutomationId is empty/blank.  In some applications, @Name value changes based on UI interaction,
     * and thus cannot be reliably reused.
     */
    public static final String XPATH_GEN_FAVOR_AUTOMATION_ID_OVER_NAME = "FAVOR_AUTOMATION_ID_OVER_NAME";
    /**
     * use @AutomationId, @Name, @ControlType (in that order) to generate XPATH, regardless of their values.
     */
    public static final String XPATH_GEN_USE_ALL = "USE_ALL";
    public static final String XPATH_GEN_DEFAULT = XPATH_GEN_IGNORE_NUMERIC_AUTOMATION_ID;

    public static final String GLOBAL_XPATH_GEN_OMIT_CONTROL_TYPE = "OMIT_CONTROL_TYPE";
    /**
     * the controls whose @Name attribute should NEVER be used to construct XPATH since such attribute is known to
     * contain dynamic values
     */
    public static final List<String> CONTROL_TYPES_WITH_UNRELIABLE_NAMES = Arrays.asList(EDIT, COMBO, DOCUMENT, TABLE);

    // seeknow
    public static final String SEEKNOW_CONFIG_PREFIX = "glyphs/";
    public static final String SEEKNOW_CONFIG_POSTFIX = "/seeknow.json";
    public static final String SEEKNOW_CONFIG_NUM_POSTFIX = "/seeknow-num.json";
    public static final String SEEKNOW_CONFIG_NUM_AND_UPPER_POSTFIX = "/seeknow-num-and-upper.json";
    public static final String DEF_SEEKNOW_FONT = "fixedsys";
    public static final String DESKTOP_CURRENT_TEXTPANE = NS_DESKTOP + "textpane";

    // ignore list
    public static final List IGNORE_CONTROL_TYPES = Arrays.asList(SEPARATOR, IMAGE, TITLE_BAR, MENU_BAR, MENU_ITEM,
                                                                  MENU, PROGRESS_BAR, TREE_ITEM);
    public static final List IGNORE_TREE_CLASSNAMES = Arrays.asList("SysTreeView32", "SaveDialogPreviewMetadataInner",
                                                                    "AppControlsModuleInner");
    public static final List IGNORE_PANE_CLASSNAMES = Arrays.asList("ProperTreeHost", "DUIListView", "ReBarWindow32",
                                                                    "ScrollBar");
    public static final List IGNORE_TOOLBAR_CLASSNAMES = Arrays.asList("FolderBandModuleInner");

    // testing/debugging purpose
    public static final int DEF_OUTPUT_LABEL_WIDTH = 30;

    public static final RuntimeTypeAdapterFactory<DesktopElement> GSON_RTAF =
        RuntimeTypeAdapterFactory.of(DesktopElement.class)
                                 .registerSubtype(DesktopElement.class)
                                 .registerSubtype(DesktopTable.class)
                                 .registerSubtype(DesktopHierTable.class)
                                 .registerSubtype(DesktopMenuBar.class)
                                 .registerSubtype(DesktopList.class)
                                 .registerSubtype(DesktopTabGroup.class);
    public static final ExclusionStrategy GSON_EXCLUDE_STRATEGY = new ExclusionStrategy() {
        @Override
        public boolean shouldSkipField(FieldAttributes f) {
            return StringUtils.equals(f.getName(), "xpathGenerationStrategy");
        }

        @Override
        public boolean shouldSkipClass(Class<?> clazz) { return false; }
    };

    public static final Gson GSON2 = new GsonBuilder().setPrettyPrinting()
                                                      .disableHtmlEscaping()
                                                      .disableInnerClassSerialization()
                                                      .setLenient()
                                                      .setExclusionStrategies(GSON_EXCLUDE_STRATEGY)
                                                      .registerTypeAdapterFactory(GSON_RTAF)
                                                      .create();

    public static final String UNMATCHED_LABEL_PREFIX = "[[UNMATCHED]]";
    public static boolean AUTOSCAN_DEBUG = false;
    public static final int POST_MENU_CLICK_WAIT_MS = 2000;

    public static final String CONTEXT_MENU_VIA_INDEX = "INDEX:";

    public static final String SCRIPT_SET_VALUE = "automation: ValuePattern.SetValue";
    public static final String SCRIPT_CLICK = "input: brc_click";
    public static final String SCRIPT_DATAGRID_ROW_COUNT = "datagrid: row-count";
    public static final String SCRIPT_TREE_EDIT_CELLS = "tree: editCells";
    public static final String SCRIPT_TREE_COLLAPSE_ALL = "tree: collapseAll";
    public static final String SCRIPT_DATAGRID_FETCH = "datagrid: fetch";
    public static final String SCRIPT_DATAGRID_FETCH_ALL = "datagrid: fetch-all";
    public static final String SRIPT_TREE_GETROW = "tree: getRowData";
    public static final String SCRIPT_TREE_GET_CHILD = "tree: getChildData";

    protected DesktopConst() { }

    public static void postMenuClickWait() {
        try {
            Thread.sleep(POST_MENU_CLICK_WAIT_MS);
        } catch (InterruptedException e) {
            ConsoleUtils.error("Error while waiting for application to stabilize after menu click: " + e.getMessage());
        }
    }

    public static void debug(String msg) { if (AUTOSCAN_DEBUG) { ConsoleUtils.log(msg); } }

    public static void debug(String msg, DesktopElement element) {
        if (AUTOSCAN_DEBUG) { ConsoleUtils.log(msg + NL + printDetails(element)); }
    }

    public static void debug(String msg, WebElement element) {
        if (AUTOSCAN_DEBUG) { ConsoleUtils.log(msg + NL + printDetails(element)); }
    }
}
