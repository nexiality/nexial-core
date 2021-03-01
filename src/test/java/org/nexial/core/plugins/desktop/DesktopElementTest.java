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

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;
import org.nexial.commons.utils.CollectionUtil;
import org.nexial.commons.utils.TextUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.nexial.core.plugins.desktop.ElementType.*;

public class DesktopElementTest {
    public static final String NO_LABEL = "-=-NO.LABEL-=-";

    @Test
    public void testNoLabelContainers() {
        DesktopElement elementA = newForm(null, "A");

        DesktopElement elementB = newForm(null, "B");
        addToContainer(elementA, elementB);
        addToContainer(elementB, newTextbox("C", "D"));
        addToContainer(elementB, newTextbox("E", "F"));
        addToContainer(elementB, newLabel(null, "G"));

        DesktopElement elementH = newGroup(null, "H");
        addToContainer(elementA, elementH);
        addToContainer(elementH, newTextbox("I", "J"));
        addToContainer(elementH, newTextbox(null, "K"));
        addToContainer(elementH, newCheckbox("L"));
        addToContainer(elementH, newCheckbox("M"));

        addToContainer(elementA, newTable(null, "N"));

        DesktopElement elementO = newForm(null, "O");
        addToContainer(elementA, elementO);
        addToContainer(elementO, newTable(null, "P"));

        DesktopElement elementQ = newForm(null, "Q");
        addToContainer(elementA, elementQ);
        addToContainer(elementQ, newTable(null, "R"));

        DesktopElement elementS = newForm(null, "S");
        addToContainer(elementA, elementS);
        addToContainer(elementS, newButton("T"));
        addToContainer(elementS, newButton("U"));

        DesktopElement elementV = newGroup(null, "V");
        addToContainer(elementS, elementV);
        addToContainer(elementV, newButton("W"));
        addToContainer(elementV, newButton("X"));

        addToContainer(elementS, newButton("Y"));

        System.out.println(elementA.components.keySet());
        elementA = collapseLabellessComponents(elementA);
        System.out.println(elementA.components.keySet());

        // [Table, C, E, I, L, M, Table(1), Table(2), Table(3), T, U, Y, W, X]
        System.out.println(elementA.components.get("Table"));
        System.out.println(elementA.components.get("Table(1)"));
        System.out.println(elementA.components.get("Table(2)"));
        // System.out.println(elementA.components.get("Table(3)"));
        System.out.println(elementA.components.get("C"));
        System.out.println(elementA.components.get("C(1)"));
        System.out.println(elementA.components.get("E"));
        System.out.println(elementA.components.get("I"));
        System.out.println(elementA.components.get("L"));
        System.out.println(elementA.components.get("M"));
        // System.out.println(GSON2.toJson(elementA));

        Assert.assertNotNull(elementA);
        Assert.assertEquals(elementA.components.size(), 14);
    }

    @Test
    public void testParseTextInputWithShortcuts() {
        List<String> expected = new ArrayList<>();

        expected.add("Hello");
        expected.add("<[CTRL-ALT-S]>");
        expected.add("Good[Bye");
        Assert.assertEquals(expected, DesktopElement.parseTextInputWithShortcuts("Hello[CTRL-ALT-S]Good[Bye", false));

        expected.clear();
        expected.add("<[CTRL-A]>");
        expected.add("Hello");
        expected.add("<[CTRL-C]>");
        Assert.assertEquals(expected, DesktopElement.parseTextInputWithShortcuts("[CTRL-A]Hello[CTRL-C]", false));

        expected.clear();
        expected.add("Hello");
        expected.add("<[CTRL-K]>");
        Assert.assertEquals(expected, DesktopElement.parseTextInputWithShortcuts("Hello[CTRL-K]", false));

        expected.clear();
        expected.add("Hello");
        expected.add("<[CTRL-ALT-S]>");
        expected.add("Bye]");
        Assert.assertEquals(expected, DesktopElement.parseTextInputWithShortcuts("Hello[CTRL-ALT-S]Bye]", false));

        expected.clear();
        expected.add("Hello");
        expected.add("<[CTRL-ALT-S]>");
        expected.add("Good[]Bye[");
        Assert.assertEquals(expected, DesktopElement.parseTextInputWithShortcuts("Hello[CTRL-ALT-S]Good[]Bye[", false));

        expected.clear();
        expected.add("Hello");
        Assert.assertEquals(expected, DesktopElement.parseTextInputWithShortcuts("Hello", false));

        expected.clear();
        expected.add("<[ALT-TAB]>");
        Assert.assertEquals(expected, DesktopElement.parseTextInputWithShortcuts("[ALT-TAB]", false));
    }

    @Test
    public void testParseTextInputWithShortcuts_repeated_shortcuts() {
        List<String> expected = new ArrayList<>();

        expected.add("Hello");
        expected.add("<[tab]>");
        expected.add("<[tab]>");
        Assert.assertEquals(expected, DesktopElement.parseTextInputWithShortcuts("Hello[tab][tab]", false));

        expected.clear();
        expected.add("<[TAB]>");
        expected.add("<[tab]>");
        expected.add("<[tab]>");
        Assert.assertEquals(expected, DesktopElement.parseTextInputWithShortcuts("[TAB][tab][tab]", false));

        expected.clear();
        expected.add("<[tab]>");
        expected.add(".");
        expected.add("<[tab]>");
        expected.add("tab");
        expected.add("<[ ]>");
        Assert.assertEquals(expected, DesktopElement.parseTextInputWithShortcuts("[tab].[tab]tab[ ]", false));

        expected.clear();
        expected.add("<[tab]>");
        expected.add("]");
        expected.add("<[tab]>");
        expected.add("tab");
        expected.add("<[ ]>");
        Assert.assertEquals(expected, DesktopElement.parseTextInputWithShortcuts("[tab]][tab]tab[ ]", false));

        expected.clear();
        expected.add("<[tab]>");
        expected.add("<[[tab]>");
        expected.add("tab");
        expected.add("<[ ]>");
        Assert.assertEquals(expected, DesktopElement.parseTextInputWithShortcuts("[tab][[tab]tab[ ]", false));

        expected.clear();
        expected.add("<[tab]>");
        expected.add(" ");
        expected.add("<[tab]>");
        expected.add("tab");
        expected.add("<[ ]>");
        Assert.assertEquals(expected, DesktopElement.parseTextInputWithShortcuts("[tab] [tab]tab[ ]", false));

        expected.clear();
        expected.add(" ");
        expected.add("<[tab]>");
        expected.add(" ");
        expected.add("<[tab]>");
        expected.add(" tab ");
        expected.add("<[ ]>");
        Assert.assertEquals(expected, DesktopElement.parseTextInputWithShortcuts(" [tab] [tab] tab [ ]", false));

        expected.clear();
        expected.add("\t");
        expected.add("<[tab]>");
        expected.add(" ");
        expected.add("<[enter]>");
        expected.add(" ");
        Assert.assertEquals(expected, DesktopElement.parseTextInputWithShortcuts("\t[tab] [enter] ", false));

    }

    @Test
    public void testParseTextInputWithShortcuts_combine_strings() {
        List<String> expected = new ArrayList<>();
        Assert.assertEquals(expected,
                            DesktopElement.parseTextInputWithShortcuts(
                                TextUtils.toString(new String[]{"", "", ""}, "", "", ""), false));

        expected.clear();
        expected.add(" ");
        Assert.assertEquals(expected,
                            DesktopElement.parseTextInputWithShortcuts(
                                TextUtils.toString(new String[]{"", " ", ""}, "", "", ""), false));

        expected.clear();
        expected.add("Hello");
        expected.add("<[tab]>");
        expected.add("<[tab]>");
        Assert.assertEquals(expected,
                            DesktopElement.parseTextInputWithShortcuts(
                                TextUtils.toString(new String[]{"Hello", "[tab]", "[tab]"}, "", "", ""), false));

        expected.clear();
        expected.add("Hello ");
        expected.add("<[tab]>");
        expected.add("<[ctrl-shift-F7]>");
        expected.add(" ");
        Assert.assertEquals(expected,
                            DesktopElement.parseTextInputWithShortcuts(
                                TextUtils.toString(new String[]{"Hello", " ", "[tab]", "[ctrl-shift-F7]", " "},
                                                   "", "", ""),
                                false));
    }

    @Test
    public void testForceParseTextInputWithShortcuts_combine_strings() {
        List<String> expected = new ArrayList<>();
        Assert.assertEquals(expected,
                            DesktopElement.parseTextInputWithShortcuts(
                                TextUtils.toString(new String[]{"", "", ""}, "", "", ""), true));

        expected.clear();
        expected.add("<[{ }]>");
        Assert.assertEquals(expected,
                            DesktopElement.parseTextInputWithShortcuts(
                                TextUtils.toString(new String[]{"", " ", ""}, "", "", ""), true));

        expected.clear();
        expected.add("<[{Hello}]>");
        expected.add("<[tab]>");
        expected.add("<[tab]>");
        Assert.assertEquals(expected,
                            DesktopElement.parseTextInputWithShortcuts(
                                TextUtils.toString(new String[]{"Hello", "[tab]", "[tab]"}, "", "", ""), true));

        expected.clear();
        expected.add("<[{Hello }]>");
        expected.add("<[tab]>");
        expected.add("<[ctrl-shift-F7]>");
        expected.add("<[{ }]>");
        Assert.assertEquals(expected,
                            DesktopElement.parseTextInputWithShortcuts(
                                TextUtils.toString(new String[]{"Hello", " ", "[tab]", "[ctrl-shift-F7]", " "},
                                                   "", "", ""),
                                true));

        expected.clear();
        expected.add("<[ctrl-end]>");
        expected.add("<[{\r\nThis is a test\r\nDo not be alarmed}]>");
        Assert.assertEquals(expected,
                            DesktopElement.parseTextInputWithShortcuts(
                                TextUtils.toString(new String[]{"[ctrl-end]", "\r\nThis is a test",
                                                                "\r\nDo not be alarmed"},
                                                   "", "", ""),
                                true));

    }

    protected DesktopElement newTextbox(String c, String id) {
        DesktopElement element = new DesktopElement();
        element.label = c;
        element.name = id;
        element.xpath = id;
        element.controlType = EDIT;
        element.elementType = Textbox;
        return element;
    }

    protected DesktopElement newForm(String label, String id) {
        DesktopElement element = new DesktopElement();
        element.label = label;
        element.name = id;
        element.xpath = id;
        element.controlType = PANE;
        element.elementType = Form;
        return element;
    }

    protected DesktopElement newGroup(String label, String id) {
        DesktopElement element = new DesktopElement();
        element.label = label;
        element.name = id;
        element.xpath = id;
        element.controlType = GROUP;
        element.elementType = LabelGrouping;
        return element;
    }

    protected DesktopElement newLabel(String label, String id) {
        DesktopElement element = new DesktopElement();
        element.label = label;
        element.name = id;
        element.xpath = id;
        element.controlType = LABEL;
        element.elementType = Label;
        return element;
    }

    protected DesktopElement newCheckbox(String label) {
        DesktopElement element = new DesktopElement();
        element.label = label;
        element.name = label;
        element.xpath = label;
        element.controlType = CHECK_BOX;
        element.elementType = Checkbox;
        return element;
    }

    protected DesktopElement newTable(String label, String id) {
        DesktopElement element = new DesktopElement();
        element.label = StringUtils.defaultIfBlank(label, "Table");
        element.name = id;
        element.xpath = id;
        element.controlType = TABLE;
        element.elementType = Table;
        return element;
    }

    private DesktopElement newButton(String label) {
        DesktopElement element = new DesktopElement();
        element.label = label;
        element.name = label;
        element.xpath = label;
        element.controlType = BUTTON;
        element.elementType = Button;
        return element;
    }

    private DesktopElement addToContainer(DesktopElement container, DesktopElement element) {
        element.container = container;
        element.xpath = container.getXpath() + "/" + element.xpath;
        String label = makeLabel(element);
        if (!container.components.containsKey(label)) {
            container.components.put(label, element);
            return container;
        }

        // we do not want to override existing label..
        // hence we need to rename label to the "label"(index), so forth
        int newLabelIndex = findNewLabelIndex(container, label);
        if (newLabelIndex == 1) {
            container.components.put(label + "(1)", container.components.get(label));
            newLabelIndex++;
        }

        String newLabel = label + "(" + newLabelIndex + ")";
        element.label = newLabel;
        container.components.put(newLabel, element);
        return container;
    }

    private int findNewLabelIndex(DesktopElement container, String label) {
        int lastIndex = 1;
        String newLabel = label + "(" + lastIndex + ")";
        while (container.components.containsKey(newLabel)) {
            lastIndex++;
            newLabel = label + "(" + lastIndex + ")";
        }
        return lastIndex;
    }

    private DesktopElement collapseLabellessComponents(DesktopElement element) {
        if (element == null || MapUtils.isEmpty(element.components)) { return element; }
        collapseOrPromote(element);
        return element;
    }

    private Map<String, DesktopElement> promoteInnerElements(DesktopElement element) {
        // todo apply override here; this is last hope to save this element from deletion
        if (element == null || MapUtils.isEmpty(element.components)) { return null; }
        collapseOrPromote(element);
        return element.components;
    }

    private void collapseOrPromote(DesktopElement element) {
        Map<String, DesktopElement> components = element.components;

        List<String> childLabels = CollectionUtil.toList(components.keySet());
        for (String childLabel : childLabels) {
            DesktopElement child = components.get(childLabel);
            if (!StringUtils.startsWith(childLabel, NO_LABEL)) {
                child = collapseLabellessComponents(child);
                components.put(child.label, child);
                continue;
            }

            components.remove(childLabel);
            Map<String, DesktopElement> promoted = promoteInnerElements(child);
            if (MapUtils.isEmpty(promoted)) { continue; }

            promoted.keySet().forEach(promotedKey -> {
                if (!components.containsKey(promotedKey)) {
                    components.put(promotedKey, promoted.get(promotedKey));
                } else {
                    int lastIndex = findNewLabelIndex(element, promotedKey);
                    if (lastIndex == 1) {
                        components.put(promotedKey + "(1)", components.get(promotedKey));
                        lastIndex++;
                    }

                    String newLabel = promotedKey + "(" + lastIndex + ")";
                    element.label = newLabel;
                    components.put(newLabel, element);
                }
            });
        }
    }

    private String makeLabel(DesktopElement element) {
        return StringUtils.defaultIfBlank(element.label, NO_LABEL + RandomUtils.nextInt(100, 999));
    }

}
