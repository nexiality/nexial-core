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

public enum ElementType {
    Checkbox(false, true),
    Radio(false, true),
    Button(false, true),
    Label(false, false),
    // masked to date or time format
    DateTimeCombo(false, false),
    // freeform, dropdown list typeahead
    TypeAheadCombo(false, false),
    MultiSelectCombo(false, false),
    // selection only, no freeform, dropdown is a Combo
    //    IsValuePatternAvailable:	"True"
    SingleSelectCombo(false, false),
    // sometimes a combo element is not editable during scanning. We marked it as so, so that we can rescan during runtime
    SingleSelectComboNotEditable(false, false),
    // selection only, no freeform, dropdown is a List (traditional)
    //    IsExpandCollapsePatternAvailable:	"True"
    //    IsSelectionPatternAvailable:	"True"
    SingleSelectList(false, false),
    Textbox(false, false),
    FormattedTextbox(false, false),
    TextArea(false, false),
    Table(false, true),
    HierTable(true, true),
    TreeItem(true, true),
    MenuItem(false, true),
    ListItem(false, false),
    Form(true, false),
    Window(true, true),
    Dialog(true, true),

    LabelGrouping(true, false),
    ListGrouping(true, false),
    CustomGrouping(true, false),
    TextPane(true, false),
    TitleBar(true, true),
    StatusBar(true, true),
    MenuBar(true, true),
    Menu(true, true),
    ToolBar(true, true),

    Tab(true, false),
    TabItem(false, false),

    Grip(false, true),

    // not for general use: this means type is not important or not known at the time of use
    Any(false, false);

    public static final String LABEL = "ControlType.Text";
    public static final String TITLE_BAR = "ControlType.TitleBar";
    public static final String STATUS_BAR = "ControlType.StatusBar";
    public static final String MENU_BAR = "ControlType.MenuBar";
    public static final String MENU_ITEM = "ControlType.MenuItem";
    public static final String MENU = "ControlType.Menu";
    public static final String CHECK_BOX = "ControlType.CheckBox";
    public static final String RADIO = "ControlType.RadioButton";
    public static final String BUTTON = "ControlType.Button";
    public static final String SPLIT_BUTTON = "ControlType.SplitButton";
    public static final String COMBO = "ControlType.ComboBox";
    public static final String LIST = "ControlType.List";
    public static final String LIST_ITEM = "ControlType.ListItem";
    public static final String EDIT = "ControlType.Edit";
    public static final String DOCUMENT = "ControlType.Document";
    public static final String TABLE = "ControlType.Table";
    public static final String TREE = "ControlType.Tree";
    // tree item with IsInvokePatternAvailable as false means it's a leaf item.  otherwise, expand/collapse is possible
    public static final String TREE_ITEM = "ControlType.TreeItem";
    public static final String GROUP = "ControlType.Group";
    public static final String PANE = "ControlType.Pane";
    public static final String CUSTOM = "ControlType.Custom";
    public static final String SCROLLBAR = "ControlType.ScrollBar";
    public static final String TOOLBAR = "ControlType.ToolBar";
    public static final String SEPARATOR = "ControlType.Separator";
    public static final String IMAGE = "ControlType.Image";
    public static final String WINDOW = "ControlType.Window";
    public static final String TAB_GROUP = "ControlType.Tab";
    public static final String TAB_ITEM = "ControlType.TabItem";
    public static final String PROGRESS_BAR = "ControlType.ProgressBar";
    public static final String OUTPUT_PANE = "OutpWndClass";

    private boolean container;
    private boolean selfLabel;

    ElementType(boolean container, boolean selfLabel) {
        this.container = container;
        this.selfLabel = selfLabel;
    }

    public boolean isContainer() { return container; }

    public boolean isSelfLabel() { return selfLabel; }

    public boolean isCombo() {
        return this == DateTimeCombo ||
               this == TypeAheadCombo ||
               this == MultiSelectCombo ||
               this == SingleSelectCombo ||
               this == SingleSelectComboNotEditable ||
               this == SingleSelectList;
    }

    public boolean isTextPatternAvailable() {
        return this == TextArea ||
               this == DateTimeCombo ||
               this == TypeAheadCombo ||
               this == SingleSelectCombo;
    }

    public boolean isTextbox() { return this == Textbox || this == FormattedTextbox; }
}
