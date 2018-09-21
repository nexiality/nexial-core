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

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.nexial.commons.utils.CollectionUtil;
import org.nexial.commons.utils.RegexUtils;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.ExecutionThread;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.StepResult;
import org.nexial.core.utils.CheckUtils;
import org.nexial.core.utils.ConsoleUtils;
import org.nexial.seeknow.SeeknowData;
import org.openqa.selenium.By;
import org.openqa.selenium.InvalidElementStateException;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.remote.RemoteWebElement;
import org.openqa.selenium.winium.WiniumDriver;

import winium.elements.desktop.ComboBox;

import static org.nexial.core.plugins.desktop.DesktopConst.*;
import static org.nexial.core.plugins.desktop.DesktopUtils.*;
import static org.nexial.core.plugins.desktop.ElementType.*;
import static org.nexial.core.utils.AssertUtils.requires;

/**
 * Object representataion of a UI element in a desktop (native) application (Windows only).
 *
 * A {@code DesktopElement} can be a textbox, checkbox, combo box, table, a container (see below) that can more
 * {@code DesktopElement}, etc.
 *
 * A "container" may reference a UI element whose ControlType attribute could be <ul>
 * <li>ControlType.Pane</li>
 * <li>ControlType.Window</li>
 * <li>ControlType.Group</li>
 * </ul>
 */
public class DesktopElement {
    protected transient WiniumDriver driver;

    /** the container element of the underlying element */
    protected transient DesktopElement container;

    /** the underlying element of this form element */
    protected transient WebElement element;
    protected transient WebElement horizontalScrollBar;
    protected transient WebElement verticalScrollBar;

    /**
     * determine the appropriate way to generate XPATH during autoscan.  Copied from {@link DesktopConfig#xpathGenerationStrategy}.
     */
    protected String xpathGenerationStrategy;

    /** determine global XPATH generation strategy **/
    protected transient String globalXpathGenerationStrategy;
    /** xpath of this element */
    protected String xpath;

    /** the @ControlType of the underlying element */
    protected String controlType;

    /** indicate whether an element is editable or not at the time of it being scanned */
    protected transient boolean editable;

    /** the @Name of the underlying element */
    protected String name;

    /** the @AutomationId of the underlying element */
    protected String automationId;

    /** the resolved element type, which is derived via various attribute of the underlying element */
    protected ElementType elementType;

    /** preferred label of this element. Mostly for button, checkbox, and radio */
    protected String label;

    protected String layoutHint;
    protected transient FormLayout layout = FORM_LAYOUT_DEFAULT;

    /**
     * user-specified hint on whether a particular element should be given different treatment than the standard/common
     * one.  Most notably, some elements are rendered and internally managed by 3rd-party libraries such as
     * Infragistics.  These elements have corresponding classes that would need to be rendered in order to utilize
     * the automation.
     */
    protected String componentTypeHint;

    /**
     * extra details that are meaningful and useful towards the specific implementation of a desktop UI element.
     * Generally, this field shouldn't be used since most UI elements have fairly standard attributes and processing.
     * But in special cases such as ControlType.List, we need to define the XPATH for its header information, and the
     * appropriate regular expression to parse its data into contextually meaningful cells.
     */
    protected Map<String, String> extra = new LinkedHashMap<>();

    /**
     * may reference a mapping of a label (ControlType.Text) to one or more elements such as <ul>
     * <li>textbox (ControlType.Edit)</li>
     * <li>checkbox (ControlType.CheckBox)</li>
     * <li>radio (ControlType.RadioButton)</li>
     * <li>combo (ControlType.ComboBox or ControlType.List)</li>
     * <li>textarea (ControlType.Document)</li>
     * <li>table (ControlType.Table)</li>
     * <li>button (ControlType.Button)</li>
     * <li>another container (ControlType.Pane, ControlTypeGroup)</li>
     * <li>custom/dynamic element that mimic one of the above</li>
     * <li>a vertical or horizontal scrollbar (ControlType.ScrollBar)</li>
     * </ul>
     *
     * for dynamic element, its ControlType attribute will likely be ControlType.Custom or ControlType.Pane.  As such,
     * Nexial will derive the most appropriate type inference via inspection of its other attributes and inner elements
     */
    protected Map<String, DesktopElement> components = new LinkedHashMap<>();

    /**
     * exception list to allow user-specified labeling for specific component
     */
    protected transient Map<String, String> xpathToLabelExceptionList = new HashMap<>();

    protected DesktopElement() { }

    public DesktopElement(WebElement element, DesktopElement container) {
        collectLabelOverrides();

        setElement(element);
        if (container != null) { setContainer(container); }

        if (!resolveInputType()) {
            ConsoleUtils.error("Unresolved element type for element" +
                               ": @ControlType=" + controlType +
                               ", @Name=" + name +
                               ", @AutomationId=" + automationId);
        }
    }

    public String getXpathGenerationStrategy() { return xpathGenerationStrategy; }

    protected void setXpathGenerationStrategy(String xpathGenerationStrategy) {
        this.xpathGenerationStrategy = xpathGenerationStrategy;
    }

    public boolean isContainer() {
        if (elementType == null) { return false; }
        if (elementType.isContainer()) { return true; }

        // menu is a special case
        if (StringUtils.equals(controlType, MENU_ITEM)) {
            if (container != null && StringUtils.equals(container.controlType, MENU_BAR)) { return true; }
            return isExpandCollapsePatternAvailable(element);
        }

        return false;
    }

    public String getGlobalXpathGenerationStrategy() {
        return globalXpathGenerationStrategy;
    }

    public void setGlobalXpathGenerationStrategy(String globalXpathGenerationStrategy) {
        this.globalXpathGenerationStrategy = globalXpathGenerationStrategy;
    }

    public boolean isLabel() { return elementType == Label && StringUtils.isNotEmpty(label); }

    public String getLabel() { return label; }

    public void setLabel(String label) { this.label = label; }

    public boolean isEditable() { return editable; }

    public void setEditable(boolean editable) { this.editable = editable; }

    public DesktopElement getContainer() { return container; }

    public void setContainer(DesktopElement container) {
        this.container = container;

        setDriver(container.driver);
        inheritXPathGenerationStrategy(container);
        inheritLayout(container);

        String containerXpath = container.xpath;

        // workaround for the case when control type cannot be discovered via winium
        if (StringUtils.isBlank(controlType)) {
            String elementId = ((RemoteWebElement) element).getId();

            // check if this component is a 'custom'
            String xpathCustom = containerXpath + "/*[@ControlType='ControlType.Custom']";
            List<WebElement> customChildren = driver.findElements(By.xpath(xpathCustom));
            if (CollectionUtils.isNotEmpty(customChildren)) {
                for (WebElement customChild : customChildren) {
                    String customChildId = ((RemoteWebElement) customChild).getId();
                    if (StringUtils.equals(elementId, customChildId)) {
                        controlType = CUSTOM;
                        break;
                    }
                }
            }
        }

        if (StringUtils.isBlank(this.xpath)) {
            String relXpath = "";

            if (useAutomationIdForXpathGeneration(automationId)) {
                relXpath += "@AutomationId=" + xpathSafeValue(automationId) + " and ";
            }

            if (useNameForXpathGeneration(name, automationId)) {
                relXpath += "@Name=" + xpathSafeValue(name) + " and ";
            }

            if (StringUtils.isNotBlank(controlType)) {
                int position = StringUtils.isBlank(relXpath) ? findRelativePositionByType(this, container) : -1;
                if (position > 0) {
                    relXpath += "@ControlType=" + xpathSafeValue(controlType) + " and position()=" + position;
                } else {
                    if (!StringUtils.equals(globalXpathGenerationStrategy, GLOBAL_XPATH_GEN_OMIT_CONTROL_TYPE)) {
                        relXpath += "@ControlType=" + xpathSafeValue(controlType);
                    }
                }
            }

            relXpath = StringUtils.removeEnd(relXpath, " and ");

            if (StringUtils.isBlank(relXpath)) {
                // this means this element does not contains AutomationId, Name, or ControlType
                throw new InvalidElementStateException("Unable to construct xpath for this element since its " +
                                                       "@AutomationId, @Name and @ControlType are all empty/null: " +
                                                       "container=" + containerXpath);
            }

            this.xpath = containerXpath + "/*[" + relXpath + "]";
        } else {
            if (!StringUtils.startsWith(this.xpath, "/*")) {
                this.xpath = StringUtils.appendIfMissing(containerXpath, "/") +
                             StringUtils.prependIfMissing(StringUtils.trim(this.xpath), "*");
            }
        }

        // find matching component (via xpath) as configured and inherit the configured properties
        Set<String> childLabels = container.getComponents().keySet();
        for (String childLabel : childLabels) {
            DesktopElement childComponent = container.getComponents().get(childLabel);
            if (childComponent != null && StringUtils.equals(childComponent.getXpath(), getXpath())) {
                inheritXPathGenerationStrategy(childComponent);
                inheritLayout(childComponent);
                if (StringUtils.isNotBlank(childComponent.getName())) { name = childComponent.getName(); }
                if (StringUtils.isNotBlank(childComponent.getAutomationId())) {
                    automationId = childComponent.getAutomationId();
                }
                if (StringUtils.isNotBlank(childComponent.label)) { label = childComponent.label; }
                if (StringUtils.isNotBlank(childComponent.componentTypeHint)) {
                    componentTypeHint = childComponent.componentTypeHint;
                }
                if (MapUtils.isNotEmpty(childComponent.extra)) { extra = childComponent.extra; }
            }
        }
    }

    public boolean hasContainer() { return container != null; }

    public WiniumDriver getDriver() { return driver; }

    public void setDriver(WiniumDriver driver) { this.driver = driver; }

    public WebElement getElement() { return element; }

    public void setElement(WebElement element) {
        if (element == null) { return; }
        this.element = element;
        this.controlType = element.getAttribute("ControlType");
        if (this.layout == null) {
            this.layout = StringUtils.isBlank(layoutHint) ? FORM_LAYOUT_LEFT_TO_RIGHT : FormLayout.toLayout(layoutHint);
        }

        String automationId = element.getAttribute("AutomationId");
        String name = element.getAttribute("Name");

        if (StringUtils.equals(this.controlType, TITLE_BAR)) {
            this.elementType = TitleBar;
            if (this.label == null) { this.label = automationId; }
        }

        if (StringUtils.equals(this.controlType, MENU_ITEM)) {
            this.elementType = MenuItem;
            if (this.label == null) { this.label = formatLabel(name); }
        }

        if (StringUtils.equals(this.controlType, MENU)) {
            this.elementType = Menu;
            if (this.label == null) { this.label = formatLabel(name); }
        }

        if (StringUtils.equals(this.controlType, BUTTON) || StringUtils.equals(this.controlType, SPLIT_BUTTON)) {
            this.elementType = Button;
            if (this.label == null) { this.label = formatLabel(name); }
        }

        this.automationId = automationId;
        if (!CONTROL_TYPES_WITH_UNRELIABLE_NAMES.contains(controlType)) { this.name = name; }
    }

    public String getName() { return name; }

    public void setName(String name) { this.name = name; }

    public String getXpath() { return xpath; }

    public void setXpath(String xpath) { this.xpath = xpath; }

    public String getControlType() { return controlType; }

    public void setControlType(String controlType) { this.controlType = controlType; }

    public String getAutomationId() { return automationId; }

    public void setAutomationId(String automationId) { this.automationId = automationId; }

    public ElementType getElementType() { return elementType; }

    public void setElementType(ElementType elementType) { this.elementType = elementType; }

    public WebElement getHorizontalScrollBar() { return horizontalScrollBar; }

    public void setHorizontalScrollBar(WebElement horizontalScrollBar) {
        this.horizontalScrollBar = horizontalScrollBar;
    }

    public WebElement getVerticalScrollBar() { return verticalScrollBar; }

    public void setVerticalScrollBar(WebElement verticalScrollBar) { this.verticalScrollBar = verticalScrollBar; }

    public FormLayout getLayout() { return layout; }

    public void setLayout(FormLayout layout) { this.layout = layout; }

    public Map<String, DesktopElement> getComponents() { return components; }

    /**
     * possible reference to an element:
     * <ol>
     * <li>label - simplest form; this refers to an element directly placed within a form with a label
     * (ControlType.Text) associated to it</li>
     * <li>group :: label - this refers to an element that is placed inside of a "group", which is placed within a
     * form object.</li>
     * <li>label(index) - this refers to an indexed assocation of a series of form elements that are associated to 1
     * label.</li>
     * <li>group :: label(index) - complete long form; the combination of the above examples.</li>
     * </ol>
     */
    public DesktopElement getComponent(String name) {
        if (StringUtils.isBlank(name)) { return null; }
        if (MapUtils.isEmpty(components)) { return null; }

        if (!StringUtils.contains(name, NESTED_CONTAINER_SEP)) {
            DesktopElement component = components.get(StringUtils.trim(name));
            if (component != null && component.getElement() == null) { component.refetchComponents(); }
            return component;
        }

        String containerLabel = StringUtils.substringBefore(name, NESTED_CONTAINER_SEP);
        String componentLabel = StringUtils.substringAfter(name, NESTED_CONTAINER_SEP);

        DesktopElement matchedContainer = components.get(containerLabel);
        if (matchedContainer == null) {
            DesktopConst.debug("no nested container found via label " + containerLabel);
            return null;
        }

        return matchedContainer.getComponent(componentLabel);
    }

    /**
     * @see #getComponent(String)
     */
    public WebElement getElement(String name) {
        DesktopElement element = getComponent(name);
        return element == null ? null : element.element;
    }

    public DesktopElement inspectComponent(String componentLabel) {
        if (StringUtils.isBlank(componentLabel)) { return null; }
        if (MapUtils.isEmpty(components)) { return null; }

        DesktopElement component = components.get(componentLabel);
        if (component == null || StringUtils.isBlank(component.getXpath())) { return null; }

        component.inheritXPathGenerationStrategy(this);
        component.inheritLayout(this);
        component.setElement(driver.findElement(By.xpath(component.getXpath())));
        component.setContainer(this);
        component.collectLabelOverrides();
        overrideLabel(component);

        DesktopElement typeSpecificComponent = typeSpecificInspection(component);
        if (typeSpecificComponent != null) { return typeSpecificComponent; }

        if (!component.resolveInputType()) {
            ConsoleUtils.error("Unresolved element type for element (" + componentLabel + ")" +
                               ": @ControlType=" + controlType +
                               ", @Name=" + name +
                               ", @AutomationId=" + automationId);
        }

        component.setLabel(componentLabel);
        component.inspect();
        return component;
    }

    @Override
    public String toString() {
        return this.getClass() +
               "\n\t" + StringUtils.rightPad("label=", DEF_OUTPUT_LABEL_WIDTH) + label +
               "\n\t" + StringUtils.rightPad("xpath=", DEF_OUTPUT_LABEL_WIDTH) + xpath +
               "\n\t" + StringUtils.rightPad("automationId=", DEF_OUTPUT_LABEL_WIDTH) + automationId +
               "\n\t" + StringUtils.rightPad("name=", DEF_OUTPUT_LABEL_WIDTH) + name +
               "\n\t" + StringUtils.rightPad("editable=", DEF_OUTPUT_LABEL_WIDTH) + editable +
               "\n\t" + StringUtils.rightPad("controlType=", DEF_OUTPUT_LABEL_WIDTH) + controlType +
               "\n\t" + StringUtils.rightPad("elementType=", DEF_OUTPUT_LABEL_WIDTH) + elementType +
               "\n\t" + StringUtils.rightPad("extra=", DEF_OUTPUT_LABEL_WIDTH) + extra +
               "\n\t" + StringUtils.rightPad("components=", DEF_OUTPUT_LABEL_WIDTH) + components.keySet() +
               "\n";
    }

    public StepResult select(String text) {
        if (StringUtils.isEmpty(text)) { return StepResult.fail("text is null"); }
        if (elementType == null || !elementType.isCombo()) { return StepResult.fail("Element is not a Combo"); }
        if (element == null) {
            refreshElement();
            if (element == null) { return StepResult.fail("Element cannot be referenced for '" + label + "'"); }
        }

        if (elementType == SingleSelectComboNotEditable) { resolveRuntimeComboType(); }

        List<String> list = parseTextInputWithShortcuts(text);

        StepResult result = null;

        for (String item : list) {
            text = treatShortcutSyntax(item);

            if (TextUtils.isBetween(text, SHORTCUT_PREFIX, SHORTCUT_POSTFIX)) {
                driver.executeScript(SCRIPT_PREFIX_SHORTCUT + text, element);
                result = StepResult.success("Shortcut key '" + text + "' pressed");
                continue;
            }

            result = typeSpecificSelect(text);
            if (result == null) {
                // unknown combo??
                DesktopConst.debug("Unknown element type '" + elementType + "' for Combo '" + getLabel() +
                                   "', might fail...");
                element.clear();
                element.sendKeys(text);
            }
        }

        autoClearModalDialog();
        return result;
    }

    public boolean isSelected() { return element != null && element.isSelected(); }

    public String getText() {

        if (!element.isEnabled() && elementType == TextArea || elementType == TypeAheadCombo ||
            elementType == DateTimeCombo) {
            return getValue(element);
        }

        if (elementType == SingleSelectList) {
            ComboBox winiumComboBox = new ComboBox(element);
            RemoteWebElement selected = winiumComboBox.findSelected();
            if (selected == null) { return null; }
            return StringUtils.equals(selected.getAttribute("ControlType"), LIST_ITEM) ?
                   selected.getAttribute("Name") : selected.getText();
        }

        WebElement targetElement;
        if (elementType.isCombo()) {
            targetElement = resolveComboContentElement(element);
        } else if (elementType == TextArea) {
            element.click();
            targetElement = element.findElement(By.xpath(LOCATOR_DOCUMENT));
        } else {
            targetElement = element;
        }

        if (targetElement == null) {
            throw new IllegalArgumentException("Cannot resolve target element for '" + getLabel() + "'");
        }

        return getValue(targetElement);
    }

    public WebElement findModalDialog() {
        return DesktopUtils.findModalDialog(driver, "/" + StringUtils.substringBefore(getXpath().substring(1), "/"));
    }

    /**
     * click on the first matching row
     */
    public StepResult clickMatchedTextPane(Map<String, String> criteria) throws IOException {
        if (MapUtils.isEmpty(criteria)) { return StepResult.fail("criteria is null"); }
        if (elementType != TextPane) { return StepResult.fail("Element is not a " + OUTPUT_PANE); }
        if (element == null) {
            refreshElement();
            if (element == null) { return StepResult.fail("Element cannot be referenced for '" + label + "'"); }
        }

        BoundingRectangle bounds = BoundingRectangle.newInstance(element);
        bounds.adjust(1, 1, -1, 0);

        // force scanning to stop on first match
        criteria.put("stopOnMatch", "true");
        criteria.put("stopOnEmptyText", "true");
        if (criteria.containsKey("regex")) { criteria.put("limitMatch", "true"); }

        List<SeeknowData> dataList = SeeknowHelper.findMatches(extra.get("fontFamily"), bounds, criteria);
        if (CollectionUtils.isEmpty(dataList)) { return StepResult.fail("Unable to match against " + criteria); }

        SeeknowData data = dataList.get(0);
        if (data == null) { return StepResult.fail("Unable to match against " + criteria); }

        return clickMatchedTextPaneRow(getDriver(), element, data);
    }

    public List<SeeknowData> findTextPaneMatches(Map<String, String> criteria) throws IOException {
        if (MapUtils.isEmpty(criteria)) { return null; }
        if (elementType != TextPane) { return null; }
        if (element == null) {
            refreshElement();
            if (element == null) { return null; }
        }

        BoundingRectangle bounds = BoundingRectangle.newInstance(element);
        bounds.adjust(1, 1, -1, 0);

        if (criteria.containsKey("regex")) { criteria.put("limitMatch", "true"); }

        return SeeknowHelper.findMatches(extra.get("fontFamily"), bounds, criteria);
    }

    public static StepResult clickMatchedTextPaneRow(WiniumDriver driver, WebElement element, SeeknowData match) {
        int matchedLine = match.getLineNumber() + 1;
        int xOffset = match.getX() + match.getWidth() / 2;
        int yOffset = match.getY() + match.getHeight() / 2;
        int lineHeight = match.getHeight();

        Actions actions = new Actions(driver).moveToElement(element,
                                                            match.getWidth() / 2,
                                                            (matchedLine * lineHeight) - (lineHeight / 2)).click();
        actions.perform();

        return StepResult.success("Clicked on matched Line " + matchedLine + " (" + xOffset + "," + yOffset + ")");
    }

    /**
     * find the relative position of {@code target} within its {@code container} so that we can use this
     * position to formulate the XPATH expression {@code position()=...}
     */
    protected int findRelativePositionByType(DesktopElement target, DesktopElement container) {
        if (target == null || target.getElement() == null || container == null || container.getElement() == null) {
            return -1;
        }

        String xpath = "*[@ControlType='" + target.getControlType() + "']";
        List<WebElement> sameTypeElements = container.getElement().findElements(By.xpath(xpath));
        if (CollectionUtils.isEmpty(sameTypeElements)) { return -1; }

        String id = ((RemoteWebElement) target.getElement()).getId();
        for (int i = 0; i < sameTypeElements.size(); i++) {
            RemoteWebElement element = (RemoteWebElement) sameTypeElements.get(i);
            if (StringUtils.equals(element.getId(), id)) { return (i + 1); }
        }

        return -1;
    }

    protected void inheritXPathGenerationStrategy(DesktopElement source) {
        if (source == null) { return; }
        if (StringUtils.isNotBlank(source.getGlobalXpathGenerationStrategy())) {
            setGlobalXpathGenerationStrategy(source.getGlobalXpathGenerationStrategy());
        }
        if (xpathGenerationStrategy == null) {
            if (StringUtils.isBlank(source.getXpathGenerationStrategy())) {
                source.setXpathGenerationStrategy(XPATH_GEN_DEFAULT);
                setXpathGenerationStrategy(XPATH_GEN_DEFAULT);
            } else {
                setXpathGenerationStrategy(source.getXpathGenerationStrategy());
            }
        }
    }

    protected void inheritXPathGenerationStrategy(DesktopConfig config) {
        if (config == null) { return; }
        if (xpathGenerationStrategy == null && StringUtils.isNotBlank(config.getXpathGenerationStrategy())) {
            setXpathGenerationStrategy(config.getXpathGenerationStrategy());
        }
    }

    protected void inheritLayout(DesktopElement container) {
        if (container == null) { return; }
        if (StringUtils.isBlank(layoutHint) && StringUtils.isNotEmpty(container.layoutHint)) {
            layoutHint = container.layoutHint;
        }
        layout = StringUtils.isBlank(layoutHint) ? FORM_LAYOUT_DEFAULT : FormLayout.toLayout(layoutHint);
    }

    protected void inheritParentCompoents(Map<String, DesktopElement> parentComponents) {
        if (StringUtils.isBlank(xpath)) { return; }
        if (MapUtils.isEmpty(parentComponents)) { return; }

        parentComponents.forEach((label, definedComponent) -> {
            String definedXpath = definedComponent.getXpath();
            if (StringUtils.startsWith(definedXpath, xpath)) {
                // this means this component belongs to this container
                components.put(label, definedComponent);
            }
        });
    }

    protected WebElement resolveComboContentElement(WebElement targetElement) {
        if (targetElement == null) { return null; }

        targetElement.click();

        try {
            if (elementType == TypeAheadCombo) {
                return targetElement.findElement(By.xpath(LOCATOR_EDITOR));
            }

            // could be single select combo or date/time combo
            WebElement element = targetElement.findElement(By.xpath(LOCATOR_COMBOBOX));
            if (elementType == DateTimeCombo) {
                return element.findElement(By.xpath(LOCATOR_EDITOR));
            }

            return element;
        } catch (NoSuchElementException e) {
            ConsoleUtils.error("Unable to resolve content element of this Combo '" + getLabel() + "'");
            return null;
        }
    }

    protected DesktopElement typeSpecificInspection(DesktopElement component) {
        if (StringUtils.equals(component.getLabel(), APP_MENUBAR)) {
            component = DesktopMenuBar.toInstance(component);
            component.inspect();
            addComponent(component.getLabel(), component);
            return component;
        }

        if (component.getElementType() == ListGrouping) {
            component = DesktopList.toInstance(component);
            component.inspect();
            addComponent(component.getLabel(), component);
            return component;
        }

        if (component.getElementType() == Table || StringUtils.equals(component.componentTypeHint, "Table")) {
            component = DesktopTable.toInstance(component);
            addComponent(component.getLabel(), component);
            return component;
        }

        if (component.getElementType() == HierTable || StringUtils.equals(component.componentTypeHint, "HierTable")) {
            component = DesktopHierTable.toInstance(component);
            addComponent(component.getLabel(), component);
            return component;
        }

        if (component.getElementType() == Tab || StringUtils.equals(component.componentTypeHint, "TabGroup")) {
            component = DesktopTabGroup.toInstance(component);
            addComponent(component.getLabel(), component);
            return component;
        }

        return null;
    }

    protected void addComponent(String label, DesktopElement component, boolean overrideLabel) {
        if (component == null) { return; }

        component.setLabel(label);
        if (overrideLabel) { overrideLabel(component); }

        if (StringUtils.isBlank(component.getLabel())) {
            if (StringUtils.isBlank(label)) {
                ConsoleUtils.error("Found component with no resolved label: " + printDetails(component));
                return;
            }
            component.setLabel(label);
        } else {
            label = component.getLabel();
        }

        if (!useAutomationIdForXpathGeneration(component.getAutomationId())) { component.setAutomationId(null); }
        if (!useNameForXpathGeneration(component.getName(), component.getAutomationId())) { component.setName(null); }

        if (!components.containsKey(label)) {
            components.put(label, component);
            return;
        }

        DesktopElement existingComponent = components.get(label);
        if (StringUtils.equals(existingComponent.getXpath(), component.getXpath())) {
            components.put(label, component);
            return;
        }

        // we do not want to override existing label..
        // hence we need to rename label to the "label"(index), so forth
        int lastIndex = 1;
        String newLabel = label + "(" + lastIndex + ")";
        while (components.containsKey(newLabel)) {
            lastIndex++;
            newLabel = label + "(" + lastIndex + ")";
        }

        if (lastIndex == 1) {
            components.put(label + "(1)", existingComponent);
            lastIndex++;
            newLabel = label + "(" + lastIndex + ")";
        }

        components.put(newLabel, component);
    }

    protected void addComponent(String label, DesktopElement component) { addComponent(label, component, true); }

    protected boolean useAutomationIdForXpathGeneration(String automationId) {
        if (StringUtils.isBlank(automationId)) { return false; }
        if (!StringUtils.equals(getXpathGenerationStrategy(), XPATH_GEN_IGNORE_NUMERIC_AUTOMATION_ID)) { return true; }
        return !NumberUtils.isDigits(automationId) && !RegexUtils.isExact(automationId, "Item\\ \\d+");
    }

    protected boolean useNameForXpathGeneration(String name, String automationId) {
        if (StringUtils.isBlank(name)) { return false; }
        if (!StringUtils.equals(getXpathGenerationStrategy(), XPATH_GEN_FAVOR_AUTOMATION_ID_OVER_NAME)) { return true; }
        return StringUtils.isBlank(automationId);
    }

    protected void collectLabelOverrides() { collectLabelOverrides(components); }

    protected void collectLabelOverrides(Map<String, DesktopElement> components) {
        if (MapUtils.isEmpty(components)) { return; }

        components.forEach((label, desktopElement) -> {
            if (StringUtils.isNotBlank(label) && StringUtils.isNotBlank(desktopElement.getXpath())) {
                xpathToLabelExceptionList.put(desktopElement.getXpath(), label);
            }
            collectLabelOverrides(desktopElement.getComponents());
        });
    }

    protected void overrideLabel(DesktopElement component) {
        String labelOverride = xpathToLabelExceptionList.get(component.getXpath());
        if (StringUtils.isNotBlank(labelOverride)) { component.setLabel(labelOverride); }
    }

    protected void handleScrollbar(WebElement scrollbar) {
        if (scrollbar == null) { return; }
        BoundingRectangle bounds = BoundingRectangle.newInstance(scrollbar);
        if (bounds.getWidth() > bounds.getHeight()) {
            ConsoleUtils.log("setting horizontal scrollbar for '" + label + "'");
            setHorizontalScrollBar(scrollbar);
        }
        if (bounds.getHeight() > bounds.getWidth()) {
            ConsoleUtils.log("setting vertical scrollbar for '" + label + "'");
            setVerticalScrollBar(scrollbar);
        }
    }

    protected boolean resolveInputType() {
        if (resolvedAsTitle()) { return true; }
        if (resolvedAsStatusBar()) { return true; }
        if (resolvedAsToolBar()) { return true; }
        if (resolvedAsCheckbox()) { return true; }
        if (resolvedAsRadio()) { return true; }
        if (resolvedAsButton()) { return true; }
        if (resolvedAsGroup()) { return true; }
        if (resolvedAsLabel()) { return true; }
        if (resolvedAsDocument()) { return true; }
        if (resolvedAsTable()) { return true; }
        if (resolvedAsHierTable()) { return true; }
        if (resolvedAsListGroup()) { return true; }
        if (resolvedAsListItem()) { return true; }
        if (resolvedAsCombo()) { return true; }
        if (resolvedAsWindow()) { return true; }
        if (resolvedAsPane()) { return true; }
        if (resolvedAsTextbox()) { return true; }
        if (resolvedAsTab()) { return true; }
        if (resolvedAsTabItem()) { return true; }
        return resolvedAsCustom();
    }

    protected boolean resolvedAsTitle() {
        if (!StringUtils.equals(controlType, TITLE_BAR)) { return false; }
        this.elementType = TitleBar;
        return true;
    }

    protected boolean resolvedAsStatusBar() {
        if (!StringUtils.equals(controlType, STATUS_BAR)) { return false; }
        this.elementType = StatusBar;
        if (StringUtils.isBlank(label)) { label = APP_STATUSBAR; }
        return true;
    }

    protected boolean resolvedAsToolBar() {
        if (!StringUtils.equals(controlType, TOOLBAR)) { return false; }
        this.elementType = ToolBar;
        if (StringUtils.isBlank(label)) { label = APP_TOOLBAR; }
        return true;
    }

    protected boolean resolvedAsCheckbox() {
        // if 'ControlType.Checkbox' -> this is a checkbox
        if (!StringUtils.equals(controlType, CHECK_BOX)) { return false; }

        this.elementType = Checkbox;

        if (StringUtils.isBlank(label)) {
            WebElement matchedChild = findMatchingChildElement(element, CHECK_BOX, 1);
            String elemLabel = formatLabel(matchedChild != null ? matchedChild.getAttribute("Name") : name);
            if (StringUtils.isBlank(elemLabel)) {
                elemLabel = formatLabel(matchedChild != null ?
                                        matchedChild.getAttribute("AutomationId") : automationId);
            }
            if (StringUtils.isNotBlank(elemLabel)) { label = elemLabel; }
        }

        editable = isEnabled(element);
        return true;
    }

    protected boolean resolvedAsRadio() {
        if (!StringUtils.equals(controlType, RADIO)) {return false;}

        elementType = Radio;
        String elemLabel = formatLabel(name);
        if (StringUtils.isNotBlank(elemLabel)) { label = elemLabel; }

        editable = isEnabled(element);
        return true;
    }

    protected boolean resolvedAsButton() {
        // if 'ControlType.Button'   -> this is a button
        if (!StringUtils.equals(controlType, BUTTON) && !StringUtils.equals(controlType, SPLIT_BUTTON)) { return false;}

        elementType = Button;
        if (StringUtils.isBlank(label)) { label = formatLabel(name);}
        return true;
    }

    protected boolean resolvedAsGroup() {
        // if 'ControlType.Group'    -> this is a grouping, recurse into it
        if (!StringUtils.equals(controlType, GROUP)) { return false; }

        elementType = LabelGrouping;
        if (StringUtils.isBlank(label)) { label = formatLabel(name); }
        return true;
    }

    protected boolean resolvedAsLabel() {
        // if 'ControlType.Text'     -> this is a label
        if (!StringUtils.equals(controlType, LABEL)) { return false; }

        elementType = Label;
        label = formatLabel(name);
        return true;
    }

    protected boolean resolvedAsTextbox() {
        // - if 'ControlType.Edit'     -> this is a text box
        if (!StringUtils.equals(controlType, EDIT)) {
            // not so far.. we might have one of those IG anamalies, where
            //  ControlType='ControlType.Pane'
            //  IsValuePatternAvailable='True'
            //  has child of
            //      ControlType='ControlType.Custom' or 'ControlType.Edit'
            //      IsValuePatternAvailable='True'
            if (!StringUtils.equals(controlType, PANE)) { return false; }

            // so at this point, the control type is PANE

            // click element
            if (isEnabled(element)) { element.click(); }

            List<WebElement> children = element.findElements(By.xpath(LOCATOR_TEXTBOX));
            if (CollectionUtils.isEmpty(children)) {
                children = element.findElements(By.xpath(LOCATOR_CUSTOM));
                if (CollectionUtils.isEmpty(children)) { return false; }
                // else, we found a CUSTOM under PANE, good to go (fingers crossed!)
            }

            // else, we found textbox under PANE, good to go
            this.elementType = Textbox;
        } else {
            this.elementType = Textbox;
            if (isEnabled(element)) { element.click(); }

            String xpathFormattedEdits = "*[@ControlType='" + EDIT + "']/*[@ControlType='" + EDIT + "']";
            List<WebElement> formattedEdits = element.findElements(By.xpath(xpathFormattedEdits));
            // this looks like a masked/formatted text box since it contains multiple levels of edits
            if (CollectionUtils.isNotEmpty(formattedEdits)) { this.elementType = FormattedTextbox;}
        }

        this.editable = isKeyboardFocusable(element);
        return true;
    }

    protected boolean resolvedAsDocument() {
        if (!StringUtils.equals(controlType, DOCUMENT)) { return false; }

        this.elementType = TextArea;
        this.editable = isKeyboardFocusable(element);
        return true;
    }

    protected boolean resolvedAsTable() {
        // - if 'ControlType.Table'    -> this is a table / datagrid
        if (!StringUtils.equals(controlType, TABLE)) { return false; }

        this.elementType = Table;
        if (StringUtils.isBlank(this.label)) { this.label = "Table"; }
        if (StringUtils.isBlank(this.componentTypeHint)) { this.componentTypeHint = "Table"; }
        return true;
    }

    protected boolean resolvedAsHierTable() {
        if (!StringUtils.equals(controlType, TREE)) { return false; }

        this.elementType = HierTable;
        if (StringUtils.isBlank(this.label)) { this.label = "HierTable"; }
        if (StringUtils.isBlank(this.componentTypeHint)) { this.componentTypeHint = "HierTable"; }
        return true;
    }

    protected boolean resolvedAsListGroup() {
        // - if 'ControlType.List'    -> this is a list of form elements (recurse into it)
        if (!StringUtils.equals(controlType, LIST)) { return false; }

        if (StringUtils.contains(automationId, "OptionSet_")) {
            this.elementType = Form;
            if (StringUtils.isBlank(this.label)) { this.label = formatLabel(this.name); }
            return true;
        }

        this.elementType = ListGrouping;
        if (StringUtils.isBlank(this.label)) { this.label = formatLabel(this.name); }
        return true;
    }

    protected boolean resolvedAsListItem() {
        if (!StringUtils.equals(controlType, LIST_ITEM)) { return false; }

        this.elementType = ListItem;
        if (StringUtils.isBlank(this.label)) { this.label = formatLabel(this.name); }
        return true;
    }

    protected boolean resolvedAsCombo() {
        // if 'ControlType.Combo'    -> this is a combo, but what kind?
        if (!StringUtils.equals(controlType, ElementType.COMBO)) { return false; }

        // click element
        boolean isEnabled = isEnabled(element);
        if (isEnabled) { element.click(); }
        // if element is not enabled, we might not be able to accurately assess its underlying type; it could be single-select or type-ahead.

        this.editable = isKeyboardFocusable(element);

        List<WebElement> children = element.findElements(By.xpath("*"));
        if (CollectionUtils.isEmpty(children)) { return noChildElementFound(); }

        // re-click to un-display any dropdowns
        if (isEnabled) { element.click(); }

        // check for Edit
        children = this.element.findElements(By.xpath(LOCATOR_EDITOR));
        if (CollectionUtils.isNotEmpty(children)) {
            this.elementType = StringUtils.contains(automationId, "DateTime") ? DateTimeCombo : TypeAheadCombo;
            return true;
        }

        // check for child List
        children = element.findElements(By.xpath(LOCATOR_LIST));
        if (CollectionUtils.isNotEmpty(children)) {
            WebElement firstChild = children.get(0);
            if (isSelectionPatternAvailable(firstChild)) {
                this.elementType = BooleanUtils.toBoolean(firstChild.getAttribute("CanSelectMultiple")) ?
                                   MultiSelectCombo : SingleSelectList;
            } else {
                DesktopConst.debug("WARNING: Found '" + LIST + "' child element that DOES NOT support " +
                                   "'SelectionPattern' for '" + getLabel() + "'; MIGHT NOT WORK...");
                this.elementType = SingleSelectList;
            }
            return true;
        }

        // check for child ComboBox
        children = this.element.findElements(By.xpath(LOCATOR_COMBOBOX));
        if (CollectionUtils.isNotEmpty(children)) {
            if (StringUtils.contains(automationId, "DateTime")) {
                this.elementType = DateTimeCombo;
                return true;
            }

            // check for Edit under Combo
            WebElement childCombo = children.get(0);
            List<WebElement> editors = childCombo.findElements(By.xpath(LOCATOR_EDITOR));
            if (CollectionUtils.isNotEmpty(editors)) {
                // then        -> this is a date/time combo
                this.elementType = DateTimeCombo;
            } else {
                this.elementType = isEnabled ? SingleSelectCombo : SingleSelectComboNotEditable;
            }
            return true;
        }

        throw new InvalidElementStateException("Found a " + controlType + " element with xpath " + xpath +
                                               " but does not contain any child elements of known type; " +
                                               "unknown/unsupported combo: " + printDetails(this.element));
    }

    protected boolean resolvedAsWindow() {
        if (!StringUtils.equals(controlType, WINDOW)) { return false; }

        this.elementType = BooleanUtils.toBoolean(element.getAttribute("IsModal")) ? Dialog : Window;
        return true;
    }

    protected boolean resolvedAsPane() {
        // if 'ControlType.Pane'
        if (!StringUtils.equals(controlType, PANE)) { return false; }

        boolean isEnabled = isEnabled(element);
        if (isEnabled) { element.click(); }

        String className = element.getAttribute("ClassName");
        if (StringUtils.equals(className, OUTPUT_PANE)) {
            this.elementType = TextPane;
            if (StringUtils.isBlank(label)) { label = "TextPane"; }
            return true;
        }

        // click element
        List<WebElement> children = element.findElements(By.xpath("*"));
        if (CollectionUtils.isEmpty(children)) {
            ConsoleUtils.error("Encounter element of type " + PANE + " with no child elements; NOT USABLE");
            return false;
        }

        // could be 1 or 2 (2 would be the case where 1 is EDIT and the other CUSTOM)
        if (children.size() < 3) {
            String controlType1 = children.get(0).getAttribute("ControlType");

            String controlType2 = null;
            if (children.size() > 1) { controlType2 = children.get(1).getAttribute("ControlType"); }

            // if child control type = 'ControlType.Document'
            // then            -> this is a text area
            if (StringUtils.equals(controlType1, DOCUMENT) || StringUtils.equals(controlType2, DOCUMENT)) {
                this.elementType = TextArea;
                return true;
            }

            // if child control type = 'ControlType.Edit'
            // then            -> this is a text area
            if (StringUtils.equals(controlType1, EDIT) || StringUtils.equals(controlType2, EDIT)) {
                this.elementType = Textbox;
                this.editable = isKeyboardFocusable(this.element);
                return true;
            }

            // if child control type = 'ControlType.List'
            // then            -> this is a group of form element (recurse into it)
            if (StringUtils.equals(controlType1, LIST) || StringUtils.equals(controlType2, LIST)) {
                String automationId1 = children.get(0).getAttribute("AutomationId");
                if (StringUtils.contains(automationId1, "OptionSet_")) {
                    this.elementType = Form;
                    if (StringUtils.isBlank(this.label)) { this.label = formatLabel(this.name); }
                    return true;
                }

                this.elementType = ListGrouping;
                if (StringUtils.isBlank(this.label)) { this.label = formatLabel(this.name); }
                return true;
            }

            // child could be a ControlType.Custom (IG).  We need to khow editable and native control type
            if (StringUtils.equals(controlType1, CUSTOM) || StringUtils.equals(controlType2, CUSTOM)) {
                if (StringUtils.contains(automationId, "TextBox_")) {
                    this.elementType = Textbox;
                    return true;
                }

                ConsoleUtils.error("UNKNOWN child ControlType '" + CUSTOM + "' for Element '" + automationId + "'");
            }
        }

        // if @AutomationId contains 'TextBox' and
        if (StringUtils.contains(automationId, "TextBox_")) {
            this.elementType = Textbox;
            return true;
        }

        this.elementType = Form;
        this.label = formatLabel(this.name);
        return true;
    }

    protected boolean resolvedAsTab() {
        if (!StringUtils.equals(controlType, TAB_GROUP)) { return false; }
        this.elementType = Tab;
        return true;
    }

    protected boolean resolvedAsTabItem() {
        if (!StringUtils.equals(controlType, TAB_ITEM)) { return false; }
        this.elementType = TabItem;
        if (StringUtils.isBlank(label) && StringUtils.isNotBlank(name)) { label = name; }
        return true;
    }

    protected boolean resolvedAsCustom() {
        // if 'ControlType.Custom'
        if (StringUtils.equals(controlType, CUSTOM)) {
            if (StringUtils.equals(this.name, "Window.Grip")) {
                this.elementType = Grip;
                this.label = formatLabel(this.name);
                return true;
            }

            this.elementType = CustomGrouping;
            this.label = formatLabel(this.name);
            return true;
        }

        return false;
    }

    protected WebElement findMatchingChildElement(WebElement element, String expectedType, int expectedCount) {
        if (element == null) { return null; }
        List<WebElement> children = element.findElements(By.xpath("*"));

        if (CollectionUtils.isEmpty(children)) { return null; }
        if (expectedCount > 0 && CollectionUtils.size(children) != expectedCount) { return null; }
        return children.stream().filter(child -> isExpectedType(child, expectedType)).findFirst().orElse(null);
    }

    protected boolean noChildElementFound() {
        this.elementType = SingleSelectComboNotEditable;
        ConsoleUtils.log("Found a " + controlType + " element with xpath as " + xpath +
                         " without child elements: \n" + printDetails(element));
        return true;
    }

    protected boolean isExpectedType(WebElement element, String expectedType) {
        return element != null &&
               (StringUtils.isEmpty(expectedType) ||
                StringUtils.equals(element.getAttribute("ControlType"), expectedType));
    }

    protected static void copyTo(DesktopElement currentInstance, DesktopElement newInstance) {
        copyDefinitionTo(currentInstance.getLabel(), currentInstance, newInstance);

        newInstance.setDriver(currentInstance.getDriver());
        newInstance.setAutomationId(currentInstance.getAutomationId());
        newInstance.setName(currentInstance.getName());
        newInstance.setEditable(currentInstance.isEditable());
        newInstance.element = currentInstance.getElement();
        if (currentInstance.getContainer() != null) { newInstance.container = currentInstance.getContainer(); }

        newInstance.collectLabelOverrides();
    }

    protected static void copyDefinitionTo(String label, DesktopElement defined, DesktopElement target) {
        if (StringUtils.isNotBlank(label)) { target.setLabel(label); }
        if (StringUtils.isNotBlank(defined.getXpath())) { target.setXpath(defined.getXpath()); }
        target.inheritXPathGenerationStrategy(defined);
        target.inheritLayout(defined);
        if (MapUtils.isNotEmpty(defined.getComponents())) { target.components = defined.getComponents(); }
        if (MapUtils.isNotEmpty(defined.extra)) { target.extra = defined.extra; }
        if (StringUtils.isNotBlank(defined.getControlType())) { target.setControlType(defined.getControlType()); }
        if (defined.getElementType() != null) { target.setElementType(defined.getElementType()); }
    }

    protected void inspect() {
        if (driver == null) { throw new IllegalArgumentException("driver is null!"); }
        if (element == null) { throw new IllegalArgumentException("element is null!"); }
        if (StringUtils.isBlank(xpath)) { throw new IllegalArgumentException("xpath is not defined!"); }
        if (layout == null) { throw new IllegalArgumentException("layout is null!"); }

        DesktopConst.debug("scanning " + label + "...");

        // FIRST PASS: COLLECT ALL CHILD ELEMENTS
        List<DesktopElement> desktopElements = traverseChildElements();
        if (CollectionUtils.isEmpty(desktopElements)) { return; }

        // SECOND PASS: ARRANGE BY BOUNDS
        Map<Integer, List<BoundingRectangle>> boundGroups = groupByLayout(desktopElements);
        if (MapUtils.isEmpty(boundGroups)) { return; }

        // THIRD PASS: ASSIGN LABEL TO ELEMENT(S)
        assignLabelToElements(boundGroups);
    }

    /**
     * in order to map a label to one or more elements, we need to sift out inner container from element
     * element or element input.  For every inner container found, we will recurse the same inspection
     * logic so that element elements are appropriately arranged by their immediate container.
     */
    protected List<DesktopElement> traverseChildElements() {
        if (element == null) { return null; }

        DesktopConst.debug("collecting child elements for  " + ((RemoteWebElement) element).getId());

        List<WebElement> children = element.findElements(By.xpath("*"));
        if (CollectionUtils.isEmpty(children)) { return null; }

        List<DesktopElement> desktopElements = new ArrayList<>();
        for (WebElement child : children) {
            // ignore these..
            if (shouldSkipScanning(child)) { continue; }

            String childControlType = child.getAttribute("ControlType");
            if (StringUtils.equals(childControlType, SCROLLBAR)) {
                handleScrollbar(child);
                continue;
            }

            DesktopElement desktopElement = new DesktopElement(child, this);
            if (desktopElement.elementType == null) { continue; }

            // perhaps this same element has been partially defined in application.json.
            // in such case it would most likely be an example of label override
            getComponents().forEach((label, definedElement) -> {
                String definedXpath = definedElement.getXpath();
                if (StringUtils.equals(StringUtils.trim(definedXpath), StringUtils.trim(desktopElement.getXpath()))) {
                    copyDefinitionTo(label, definedElement, desktopElement);
                }
            });

            if (desktopElement.getElementType() == null && StringUtils.equals(desktopElement.getControlType(), PANE)) {
                DesktopConst.debug("SKIP element with unresolved elementType: " + desktopElement);
                continue;
            }

            overrideLabel(desktopElement);

            DesktopElement typeSpecificComponent = typeSpecificInspection(desktopElement);
            if (typeSpecificComponent != null) {
                desktopElements.add(typeSpecificComponent);
                continue;
            }

            if (desktopElement.isContainer()) {
                if (StringUtils.isBlank(desktopElement.getLabel())) {
                    DesktopConst.debug("NAMELESS CONTAINER: INSPECT DEEPER:\n" + desktopElement.getXpath() + "\n");
                    // let this nameless container temporarily inherit the components of its parent so that we can
                    // match up all the defined elements against disovered elements.
                    desktopElement.inheritParentCompoents(getComponents());
                    List<DesktopElement> elements = findElementsFromNamelessContainer(desktopElement);
                    if (CollectionUtils.isNotEmpty(elements)) { desktopElements.addAll(elements); }
                    continue;
                }

                // pane and form has no inherent label, we'd need to rely on json-level overrides
                desktopElement.inspect();
                promoteNestedComponents(desktopElement);
            }

            desktopElements.add(desktopElement);
        }

        return desktopElements;
    }

    protected List<DesktopElement> findElementsFromNamelessContainer(DesktopElement namelessContainer) {
        if (namelessContainer == null ||
            StringUtils.isBlank(namelessContainer.getXpath()) ||
            namelessContainer.getElement() == null) {
            return null;
        }

        WebElement containerElement = namelessContainer.getElement();
        DesktopConst.debug("collecting child elements for " + ((RemoteWebElement) containerElement).getId());

        // 1. check for child elements
        List<WebElement> children = containerElement.findElements(By.xpath("*"));
        if (CollectionUtils.isEmpty(children)) { return null; }

        namelessContainer.inspect();
        Collection<DesktopElement> innerElements = namelessContainer.getComponents().values();
        DesktopConst.debug("namelessContainer.getComponents() = " + namelessContainer.getComponents().keySet());

        // 2. for every known type, add them to return list
        List<DesktopElement> desktopElements = new ArrayList<>();
        for (DesktopElement child : innerElements) {
            if (StringUtils.startsWith(child.getLabel(), UNMATCHED_LABEL_PREFIX)) { child.setLabel(null); }
            desktopElements.add(child);
        }

        // 4. return final collection
        return desktopElements;
    }

    protected void promoteNestedComponents(DesktopElement desktopElement) {
        if (desktopElement == null) { return; }

        Map<String, DesktopElement> components = desktopElement.getComponents();
        if (MapUtils.isEmpty(components)) { return; }

        Set<String> componentLabels = components.keySet();
        for (String componentLabel : componentLabels) {
            DesktopElement component = components.get(componentLabel);
            if (component == null) { components.remove(componentLabel); }
        }
    }

    protected Map<Integer, List<BoundingRectangle>> groupByLayout(List<DesktopElement> desktopElements) {
        // collect labels, congregate bounds into either x-based or y-based grouping
        Map<Integer, List<BoundingRectangle>> boundGroups = new LinkedHashMap<>();

        desktopElements.forEach(desktopElement -> {
            if (desktopElement == null) {
                ConsoleUtils.error("NULL DESKTOPELEMENT FOUND!");
            } else {
                if (desktopElement.getElementType() == null) {
                    ConsoleUtils.error("NULL ELEMENTTYPE FOUND: " + printDetails(desktopElement));
                } else {
                    if (!desktopElement.getElementType().isSelfLabel()) {
                        BoundingRectangle bound = BoundingRectangle.newInstance(desktopElement);
                        if (layout.isLeftToRight()) {
                            boundGroups.computeIfAbsent(bound.getY(), list -> new ArrayList<>()).add(bound);
                        }
                        if (layout.isTwoLines()) {
                            boundGroups.computeIfAbsent(bound.getX(), list -> new ArrayList<>()).add(bound);
                        }
                    } else {
                        addComponent(desktopElement.label, desktopElement);
                    }
                }
            }
        });

        List<Integer> groups = CollectionUtil.toList(boundGroups.keySet());
        Collections.sort(groups);

        // we need grouping tolerance so that coordinates that are very close to each other are grouped together.
        // examples of such cases would be
        // (1) the label is slightly higher or lower than then input element
        // (2) the label is slightly out of alignment with the input element
        for (int i = 0; i < groups.size(); i++) {
            if (i + 1 >= groups.size()) { break; }

            Integer thisGroup = groups.get(i);
            Integer nextGroup = groups.get(i + 1);
            DesktopConst.debug("comparing " + thisGroup + "," + nextGroup + " ...");
            if (Math.abs(thisGroup - nextGroup) <= BOUND_GROUP_TOLERANCE) {
                // put these 2 groups as 1
                DesktopConst.debug("combining");
                boundGroups.get(thisGroup).addAll(boundGroups.remove(nextGroup));
                groups.remove(nextGroup);
                i--;
            } else {
                DesktopConst.debug("kept separate");
            }
        }

        // special handling for 2lines layout:
        // at times, a form might not be consistent in using the same alignment for a label and the corresponding UI element
        // some labels might be right justified, in which case, would case orphan label and orphan UI element
        // hence we need to re-join such left-out elements by their Y position
        if (layout.isTwoLines()) {
            Integer[] groupIds = boundGroups.keySet().toArray(new Integer[boundGroups.size()]);

            List<BoundingRectangle> verticalGroup = new ArrayList<>();
            for (Integer leftPosition : groupIds) {
                List<BoundingRectangle> group = boundGroups.get(leftPosition);

                if (CollectionUtils.size(group) == 1) {
                    // remove from existing group (which has no match) and join new right-most group
                    boundGroups.remove(leftPosition);
                    verticalGroup.add(group.get(0));
                } else {
                    boolean noLabelFound = true;
                    for (BoundingRectangle bounds : group) {
                        if (!bounds.getDesktopElement().isLabel()) {
                            noLabelFound = false;
                            break;
                        }
                    }

                    if (noLabelFound) {
                        boundGroups.remove(leftPosition);
                        verticalGroup.addAll(group);
                    }
                }
            }

            if (!verticalGroup.isEmpty()) {
                verticalGroup.sort(Comparator.comparing(o -> ((Integer) o.getY())));
                boundGroups.put(0, verticalGroup);
            }
        }

        // sort bounds within the associated groups (either row group or column group)
        boundGroups.forEach((name, bounds) -> {
            if (layout.isLeftToRight()) { bounds.sort((o1, o2) -> ((Integer) o1.getX()).compareTo(o2.getX())); }
            if (layout.isTwoLines()) { bounds.sort((o1, o2) -> ((Integer) o1.getY()).compareTo(o2.getY())); }
        });

        groups.forEach(group -> DesktopConst.debug("\nGroup [" + group + "] Bounds:\n\t" +
                                                   TextUtils.toString(boundGroups.get(group), "\n\t")));

        return boundGroups;
    }

    protected void assignLabelToElements(Map<Integer, List<BoundingRectangle>> boundGroups) {
        // group label to one or more input widgets
        // note:
        // 1) checkbox might not be associated with a label (ControlType.Text), but itself has label (@Name)
        // 2) checkbox might be grouped for those that share the same x or the same y coordinates
        //    (BUT BETTER NOT TO GROUP THEM SINCE EACH CHECKBOX SHOULD BE INDIVIDUALLY IDENTIIFIED)
        // 3)

        for (Integer group : boundGroups.keySet()) {

            String currentLabel = null;
            boolean expectsInput = false;
            DesktopElement element = null;

            List<BoundingRectangle> bounds = boundGroups.get(group);
            for (BoundingRectangle bound : bounds) {
                element = bound.getDesktopElement();
                String label = element.label;

                if (element.isLabel()) {
                    // some UI uses '____' as line breaks...
                    if (StringUtils.containsOnly(label, "_")) { continue; }

                    if (currentLabel == null) {
                        currentLabel = label;
                        expectsInput = true;
                        continue;
                    }

                    if (expectsInput) {
                        associateLabelToElement(currentLabel, element);
                        element = null;
                        continue;
                    }

                    currentLabel = label;
                    expectsInput = true;
                } else {
                    // honor pre-defined labels first
                    if (StringUtils.isNotBlank(label)) {
                        addComponent(label, element);
                        expectsInput = false;
                        continue;
                    }

                    if (element.elementType == Table || element.elementType == HierTable) {
                        currentLabel = name;
                    } else if (StringUtils.isNotEmpty(label)) {
                        currentLabel = label;
                    } else if (StringUtils.isBlank(currentLabel)) {
                        currentLabel = name;
                    }

                    // if currentLabel = null, use "temp" + automationId.
                    // otherwise "element" would be lost!
                    // this "temp" id is only use now in ensure element is added into "components".
                    // we'll resolve to proper label later
                    if (StringUtils.isEmpty(currentLabel)) {
                        currentLabel = UNMATCHED_LABEL_PREFIX + element.getAutomationId();
                    }

                    associateLabelToElement(currentLabel, element);
                    expectsInput = false;
                }
            }

            if (expectsInput && StringUtils.isNotBlank(currentLabel) && element != null) {
                associateLabelToElement(currentLabel, element);
            }
        }
    }

    protected void associateLabelToElement(String label, DesktopElement element) {
        if (StringUtils.isBlank(label) || element == null) { return; }

        if (element.getElementType() == Label) {
            // for text element that is referenced by another label, we need to ensure that the @Name attribute of
            // the target element is not persisted - in general such element will have dynamic @Name attribute values.
            if (StringUtils.isNotEmpty(element.getName())) {
                element.setName(null);
                String currentXpath = element.getXpath();
                int startPos = StringUtils.lastIndexOf(currentXpath, "@Name='");
                if (startPos != -1) {
                    int endPos = StringUtils.indexOf(currentXpath, "' and ", startPos);
                    if (endPos != -1) {
                        element.setXpath(StringUtils.substring(currentXpath, 0, startPos) +
                                         StringUtils.substring(currentXpath, endPos + "' and ".length()));
                        DesktopConst.debug("reducing the XPATH of a referenced text element to " + element.getXpath());
                    }
                }
            }
        }

        DesktopElement current = components.get(label);
        if (element.equals(current)) {
            DesktopConst.debug("override element for label " + label);
            addComponent(label, element, false);
            return;
        }

        // match by xpath
        String elementXpath = element.getXpath();
        Set<String> existingLabels = components.keySet();
        for (String existingLabel : existingLabels) {
            DesktopElement existingElement = components.get(existingLabel);
            if (existingElement != null && StringUtils.equals(existingElement.getXpath(), elementXpath)) {
                if (StringUtils.isEmpty(element.getLabel())) { element.setLabel(existingLabel); }
                addComponent(existingLabel, element, false);
                return;
            }
        }

        if (!components.containsKey(label)) {
            if (StringUtils.isEmpty(element.getLabel())) { element.setLabel(label); }
            addComponent(label, element, false);
            return;
        }

        // since "label" already exist, we need to associate this element as a indexed label
        // index 1 is reserved for the first instance
        int indexed = 2;
        String indexedLabel = label + "(" + indexed + ")";
        while (components.containsKey(indexedLabel)) {
            indexed++;
            indexedLabel = label + "(" + indexed + ")";
            element.setLabel(indexedLabel);
        }

        // need to create an alias label for index 1 (in case we've just created label for index 2)
        if (indexed == 2) { components.put(label + "(1)", current); }

        addComponent(indexedLabel, element);
    }

    protected void refetchComponents() {
        if (StringUtils.isNotBlank(xpath)) { element = driver.findElement(By.xpath(xpath)); }
        if (MapUtils.isEmpty(components)) { return; }
        components.forEach((label, component) -> {
            if (component.getElementType() != MenuItem) { component.refetchComponents(); }
        });
    }

    protected void refreshElement() {
        if (StringUtils.isBlank(xpath)) { return; }
        setElement(driver.findElement(By.xpath(xpath)));
    }

    protected StepResult typeTextComponent(boolean append, String... text) {
        return typeTextComponent(false, append, text);
    }

    /**
     * meant for TextBox or TextArea
     */
    protected StepResult typeTextComponent(boolean useSendKeys, boolean append, String... text) {
        requires(ArrayUtils.isNotEmpty(text), "at least one text parameter is required");

        if (append) {
            String shortcuts = forceShortcutSyntax("[CTRL-END]") + joinShortcuts(text);
            if (StringUtils.isNotEmpty(shortcuts)) {
                try {
                    driver.executeScript(SCRIPT_PREFIX_SHORTCUT + shortcuts, element);
                } catch (WebDriverException e) {
                    ConsoleUtils.error("Error when typing'" + ArrayUtils.toString(text) + "' on '" + label + "': " +
                                       e.getMessage());
                }
            }
        } else {
            // join text into 1 string, parse the entire combined string and loop through each token to type
            parseTextInputWithShortcuts(TextUtils.toString(text, "", "", "")).forEach(txt -> type(txt, useSendKeys));
        }

        autoClearModalDialog();

        return StepResult.success("text entered to element '" + label + "'");
    }

    protected List<String> parseTextInputWithShortcuts(String text) {
        List<String> list = new ArrayList<>();

        if (StringUtils.isEmpty(text)) { return list; }

        if (StringUtils.isBlank(text)) {
            list.add(text);
            return list;
        }

        String regex = "^.*?\\[.+].*?$";

        while (text.matches(regex)) {
            String beforeShortcut = StringUtils.substringBefore(text, "[");
            if (StringUtils.isNotEmpty(beforeShortcut)) {
                list.add(beforeShortcut);
                text = "[" + StringUtils.substringAfter(text, "[");
            }
            if (StringUtils.contains(text, "]")) {
                list.add("[" + StringUtils.substringBetween(text, "[", "]") + "]");
                text = StringUtils.substringAfter(text, "]");
            }
        }

        if (StringUtils.isNotEmpty(text)) { list.add(text); }
        return list;
    }

    protected void type(String text) {
        type(text, false);
    }

    protected void type(String text, boolean useSendKeys) {
        if (StringUtils.isEmpty(text)) { return; }

        if (!element.isEnabled()) { CheckUtils.fail("Text cannot be entered as it is disabled for input"); }

        text = StringUtils.trim(treatShortcutSyntax(text));

        if (getElementType() == FormattedTextbox) {

            if (TextUtils.isBetween(text, SHORTCUT_PREFIX, SHORTCUT_POSTFIX)) {
                driver.executeScript(SCRIPT_PREFIX_SHORTCUT + text, element);
                return;
            }

            // click to be avoided here in formatted textbox before calling clear, which gives undesired results
            element.clear();
            // setValue does not work for action driven components. sendKeys and shortcut script both works
            driver.executeScript(SCRIPT_PREFIX_SHORTCUT + TEXT_INPUT_PREFIX + text + TEXT_INPUT_POSTFIX, element);
            verifyAndTry(text);
            return;
        }

        // click required to make sure that the focus is set
        element.click();
        if (StringUtils.contains(text, SHORTCUT_PREFIX) && StringUtils.contains(text, SHORTCUT_POSTFIX)) {
            String keystrokes = toKeystrokes(text);

            try {
                driver.executeScript(SCRIPT_PREFIX_SHORTCUT + keystrokes, element);
            } catch (WebDriverException e) {
                ConsoleUtils.error("Error when executing shortcut '" + keystrokes + "' on '" + label + "': " +
                                   e.getMessage());
            }

            return;
        }

        if (setValue(useSendKeys, element, text)) { return; }

        element.clear();
        element.sendKeys(text);
    }

    protected void clearFormattedTextbox() { clearFormattedTextbox(driver, element); }

    protected static void clearFormattedTextbox(WiniumDriver driver, WebElement target) {
        int previousValueCount = 0;

        List<WebElement> editables = target.findElements(By.xpath(LOCATOR_TEXTBOX + "/" + LOCATOR_TEXTBOX));
        if (CollectionUtils.isNotEmpty(editables)) {
            WebElement editable = editables.get(0);
            String currentValue = StringUtils.trim(editable.getText());
            // todo currency sign should be externalized for extensibility
            if (StringUtils.startsWithAny(currentValue, "$", "€")) {
                currentValue = StringUtils.substring(currentValue, 1);
            }
            currentValue = StringUtils.trim(StringUtils.replaceChars(currentValue, " _", ""));
            previousValueCount = StringUtils.length(currentValue);
        }

        // attempt to clear off character in this field
        if (previousValueCount > 0) {
            driver.executeScript(DesktopUtils.toShortcuts("CTRL-HOME") +
                                 StringUtils.repeat("<[DEL]>", previousValueCount),
                                 target);
        }
    }

    /** This is to check the set value is equal with entered text **/
    protected boolean isActualAndTextMatched(WebElement element, String actual, String text) {
        if (actual.isEmpty()) {
            actual = element.getAttribute("Name");
        }
        return StringUtils.equals(text, actual.trim());
    }

    protected boolean setValue(WebElement element, String text) {
        return setValue(false, element, text);
    }

    /** This Method will set the value for TextBox Element **/
    protected boolean setValue(boolean useSendKeys, WebElement element, String text) {
        if (!isValuePatternAvailable(element)) {
            element.sendKeys(text);
            return isActualAndTextMatched(element, getText(), text);
        }

        String errPrefix = "Failed to execute ValuePattern.SetValue on '" + label + "': ";
        try {
            if (useSendKeys) {
                // for text fields where ctrl+A doesn't work, element.sendKeys becomes ineffective
                // good to use shortcut script with <[{text}]>, which gives the effect of element.sendKeys and also sets cursor to beginning of text
                driver.executeScript(SCRIPT_PREFIX_SHORTCUT + TEXT_INPUT_PREFIX + text + TEXT_INPUT_POSTFIX, element);
            } else {
                driver.executeScript("automation: ValuePattern.SetValue", element, text);
            }
            String actual = element.getText();
            boolean matched = isActualAndTextMatched(element, actual, text);

            if (!matched) {
                //todo: apply some strategy set the value when it is not matched
                ConsoleUtils.log(errPrefix +
                                 "actual is '" + actual + "' but expected is '" + text + "'; Trying other approach...");
            }
            return matched;
        } catch (Exception e) {
            String error = (e instanceof WebDriverException) ?
                           filterExceptionMessage((WebDriverException) e) : e.getMessage();
            ConsoleUtils.log(errPrefix + error + "; Trying other approach...");

            // probably worked anyways.. trust the value pattern available thingy
            return true;
        }
    }

    /** This method will set the value for ComboBox Element **/
    protected boolean setComboValue(WebElement element, String text) {
        if (!isValuePatternAvailable(element)) { return false; }

        String errPrefix = "Failed to execute ValuePattern.SetValue on '" + label + "': ";
        try {
            // click needs for combo that has child element as edit type
            element.click();
            driver.executeScript("automation: ValuePattern.SetValue", element, text);
            String actual = element.getText();
            boolean matched = isActualAndTextMatched(element, actual, text);
            if (!matched) {
                // try again... this time with extra space in the front to avoid autofill
                driver.executeScript("automation: ValuePattern.SetValue", element, " " + text);
                driver.executeScript(toShortcuts("CTRL-HOME", "DEL"), element);
                actual = element.getText();
                matched = isActualAndTextMatched(element, actual, text);
                if (!matched) {
                    ConsoleUtils.log(errPrefix + "actual is '" + actual + "' but expected is '" + text + "'; " +
                                     "Trying other approach...");
                }
            }

            return matched;
        } catch (Exception e) {
            String error = (e instanceof WebDriverException) ?
                           filterExceptionMessage((WebDriverException) e) : e.getMessage();
            ConsoleUtils.log(errPrefix + error + "; Trying other approach...");

            // probably worked anyways.. trust the value pattern available thingy
            return true;
        }
    }

    /**
     * selector method to redirect to the more appropriate select*() for the target combo component
     */
    protected StepResult typeSpecificSelect(String text) {
        if (elementType == SingleSelectList) { return selectSingleSelectList(text); }

        if (elementType == SingleSelectCombo || elementType == SingleSelectComboNotEditable) {
            return selectSingleSelectCombo(text);
        }

        if (elementType == TypeAheadCombo) { return selectTypeAheadCombo(text); }

        if (elementType == DateTimeCombo) { return selectDateTimeCombo(text); }

        return null;
    }

    protected void resolveRuntimeComboType() {
        // some combo elements are disabled at the time of scanning.. let's check again now
        boolean isEnabled = isEnabled(element);
        if (!isEnabled) { return; }

        element.click();

        // check for Edit
        // todo: need to implement for multiselectcombo
        List<WebElement> children = this.element.findElements(By.xpath(LOCATOR_EDITOR));
        if (CollectionUtils.isEmpty(children)) {
            elementType = SingleSelectCombo;
        } else {
            elementType = StringUtils.contains(automationId, "DateTime") ? DateTimeCombo : TypeAheadCombo;
        }

        ExecutionContext context = ExecutionThread.get();
        if (context == null ||
            !context.hasData(CURRENT_DESKTOP_SESSION) ||
            !context.hasData(CURRENT_DESKTOP_CONTAINER)) {
            return;
        }

        DesktopSession session = (DesktopSession) context.getObjectData(CURRENT_DESKTOP_SESSION);
        if (session == null) { return; }

        DesktopElement container = (DesktopElement) context.getObjectData(CURRENT_DESKTOP_CONTAINER);
        if (container == null) { return; }

        // cache file ALWAYS follows ${directory of application.json}/${appId}.[commons | ${form}].json
        File cacheFile = DesktopSession.getCacheFile(session.getConfig(), container.getLabel());
        if (cacheFile == null) {
            ConsoleUtils.error("Unable to update container metadata since derived file is null");
        } else {
            try {
                FileUtils.writeStringToFile(cacheFile, GSON2.toJson(container, DesktopElement.class), "UTF-8");
            } catch (IOException e) {
                ConsoleUtils.error("Unable to update container metadata to " + cacheFile + ": " + e.getMessage());
            }
        }
    }

    protected StepResult selectDateTimeCombo(String text) {
        driver.executeScript(toShortcutText("/" + text), element);
        autoClearModalDialog();
        return StepResult.success("Text '" + text + "' entered into '" + label + "'");
    }

    protected StepResult selectTypeAheadCombo(String text) {
        element.click();

        // get first element and handle exception in case element is not present
        WebElement editField;
        try {
            // using .findElement for better performance
            editField = element.findElement(By.xpath(LOCATOR_EDITOR));
        } catch (org.openqa.selenium.NoSuchElementException e) {
            List<WebElement> editFields = element.findElements(By.xpath(LOCATOR_EDITOR));
            if (CollectionUtils.isEmpty(editFields)) {
                return StepResult.fail("EXPECTED element '" + EDIT + "' NOT found for Combo '" + label + "'");
            }

            editField = editFields.get(0);
        }
        if (editField != null && setComboValue(editField, text)) {
            autoClearModalDialog();
            return StepResult.success("Text '" + text + "' entered into '" + label + "'");
        } else {
            return StepResult.fail("Error setting value to '" + label + "' via ValuePattern.SetValue");
        }
    }

    protected StepResult clear() {
        ElementType elementType = getElementType();
        if (elementType.isCombo()) { return clearCombo(); }

        WebElement elem = getElement();
        if (elementType == TextArea) {
            driver.executeScript(toShortcuts("CTRL-HOME", "CTRL-SHIFT-END", "DEL"), elem);
        } else if (elementType == FormattedTextbox) {
            clearFormattedTextbox();
        } else {
            element.clear();
        }

        autoClearModalDialog();
        return StepResult.success();
    }

    protected StepResult clearCombo() {
        if (!element.isEnabled()) {
            return StepResult.fail("Unable to clear Combo '" + label + "' since it is not currently not enabled.");
        }

        // if (elementType == TypeAheadCombo) { return clearViaChildElement(LOCATOR_EDITOR); }
        // if (elementType == SingleSelectCombo) { return clearViaChildElement(LOCATOR_COMBOBOX); }

        String msgSucess = "Combo '" + label + "' cleared";

        if (elementType == DateTimeCombo || elementType == TypeAheadCombo || elementType == SingleSelectCombo) {
            String script = toShortcuts("HOME", "SHIFT-END", "DEL");
            if (elementType == SingleSelectCombo) { script = toShortcuts("ESC"); }
            driver.executeScript(script, element);
            autoClearModalDialog();
            return StepResult.success(msgSucess);
        }

        if (elementType == SingleSelectList) {
            driver.executeScript(toShortcuts("HOME", "TAB"), element);
            autoClearModalDialog();

            ComboBox winiumComboBox = new ComboBox(element);
            RemoteWebElement selected = winiumComboBox.findSelected();
            if (selected == null) {
                return StepResult.fail("Unable to validate currently selected value for Combo '" + label + "'");
            }

            String selectedText = StringUtils.equals(selected.getAttribute("ControlType"), LIST_ITEM) ?
                                  selected.getAttribute("Name") : selected.getText();
            if (StringUtils.isBlank(selectedText)) { return StepResult.success(msgSucess); }

            return StepResult.fail("Unable to clear Combo '" + label + "'; possibly no such option available");
        }

        return StepResult.fail("Unknown element type " + elementType);
    }

    protected StepResult selectSingleSelectList(String text) {
        String msgPostfix = " for List '" + label + "'.";

        // we want blank?
        if (StringUtils.isBlank(text)) {
            element.clear();
            return StepResult.success("Text cleared" + msgPostfix);
        }

        if (isExpandCollapsePatternAvailable(element)) {
            String xpathListItem = StringUtils.replace(LOCATOR_LIST_ITEM, "{value}", text);
            List<WebElement> matched = element.findElements(By.xpath(xpathListItem));
            if (CollectionUtils.isNotEmpty(matched)) {
                element.click();
                matched.get(0).click();
                return StepResult.success("Text '" + text + "' selected" + msgPostfix);
            }
        }

        if (!isSelectionPatternAvailable(element)) {
            return StepResult.fail("Unable select since selection-pattern is not available" + msgPostfix);
        }

        // type by first char of text until the selected value matches text
        text = normalizeUiText(text);
        String firstChar = text.charAt(0) + "";
        String firstSelected = null;

        String script = toShortcutText(firstChar);

        // was doing a while(true) loop, but for-loop seems safer
        for (int i = 0; i < 50; i++) {
            driver.executeScript(script, element);
            autoClearModalDialog();

            String selected = normalizeUiText(getText());
            if (StringUtils.equals(selected, text)) {
                return StepResult.success("Text '" + text + "' selected" + msgPostfix);
            }

            ConsoleUtils.log("found '" + selected + "' selected" + msgPostfix);
            if (firstSelected == null) {
                // keep this to check for the next time around.. if the newly selected value is EXACTLY as this one,
                // then we know we did not progress on the selection list.. hence the select-by-first-char did not work
                firstSelected = selected;
            } else {
                // couldn't move the selection list to another value; hopeless...
                if (StringUtils.equals(firstSelected, selected)) {
                    return StepResult.fail("FAIL to enter/found text '" + text + "'" + msgPostfix);
                }
            }
        }

        return StepResult.fail("FAIL to enter/find text '" + text + "'" + msgPostfix);
    }

    protected StepResult selectSingleSelectCombo(String text) {
        text = normalizeUiText(text);
        String firstChar = text.charAt(0) + "";

        String value = normalizeUiText(element.getAttribute("Name"));
        // todo: double check - does not get the value with @Name, when actually exists
        if (StringUtils.equals(text, value)) {
            return StepResult.success("Text '" + text + "' selected for '" + label + "'");
        }

        driver.executeScript(appendShortcutText(toShortcuts("HOME"), firstChar), element);
        value = normalizeUiText(element.getAttribute("Name"));
        String initialValue = value;

        String script = toShortcutText(firstChar);

        // keep typing the first character until the selected text matches "text"
        while (!StringUtils.equals(value, text)) {
            driver.executeScript(script, element);
            autoClearModalDialog();

            value = normalizeUiText(element.getAttribute("Name"));

            // probably means we've looped around.. so we are done
            if (StringUtils.equals(value, initialValue)) { break; }
        }

        if (!StringUtils.equals(value, text)) {
            return StepResult.fail("Unable to select in '" + label + "' the text '" + text + "': " + value);
        }

        return StepResult.success("Text '" + text + "' entered into '" + label + "'");
    }

    protected void clearModalDialog() {
        DesktopUtils.clearModalDialog(driver, "/" + StringUtils.substringBefore(getXpath().substring(1), "/"));
    }

    protected void autoClearModalDialog() {
        ExecutionContext context = ExecutionThread.get();
        if (context == null) { return; }

        if (!context.getBooleanData(DESKTOP_AUTO_CLEAR_MODAL_DIALOG, DEF_AUTO_CLEAR_MODAL_DIALOG)) { return; }
        if (StringUtils.isBlank(xpath)) { return; }
        DesktopUtils.clearModalDialog(getDriver(), "/" + StringUtils.substringBefore(xpath.substring(1), "/"));
    }

    protected boolean shouldSkipScanning(WebElement element) {
        if (element == null) { return true; }

        String controlType = element.getAttribute("ControlType");
        if (StringUtils.isBlank(controlType)) { return true; }
        if (IGNORE_CONTROL_TYPES.contains(controlType)) { return true; }

        String className = element.getAttribute("ClassName");
        if (StringUtils.isBlank(className)) { return false; }

        // Win32 standard Open/Save dialog's standard component...
        // we don't need these since we'd usually just specify full path in the 'File name' field
        if (StringUtils.equals(controlType, TREE) && IGNORE_TREE_CLASSNAMES.contains(className)) { return true; }
        if (StringUtils.equals(controlType, PANE) && IGNORE_PANE_CLASSNAMES.contains(className)) { return true; }
        return StringUtils.equals(controlType, TOOLBAR) && IGNORE_TOOLBAR_CLASSNAMES.contains(className);

    }

    private void verifyAndTry(String text) {
        if (verifyText(text, element.getText())) { return; }

        clearFormattedTextbox();
        driver.executeScript(SCRIPT_PREFIX_SHORTCUT + TEXT_INPUT_PREFIX + text + TEXT_INPUT_POSTFIX, element);
        // trust text entered correctly after second try!!
    }

    private boolean verifyText(String text, String currentValue) {
        if (!NumberUtils.isCreatable(text)) { return isActualAndTextMatched(element, currentValue.trim(), text); }

        // todo currency sign should be externalized for extensibility
        // todo create utility method around formatted text handling to improve usability
        if (StringUtils.startsWithAny(currentValue, "$", "€")) {
            currentValue = StringUtils.substring(currentValue, 1).trim();
        }

        currentValue = StringUtils.trim(StringUtils.replaceChars(currentValue, ",%", ""));

        try {
            if (NumberUtils.isCreatable(currentValue)) {
                if (currentValue.contains(".")) {
                    BigDecimal actualValue = NumberUtils.createBigDecimal(currentValue);
                    BigDecimal expectedValue = NumberUtils.createBigDecimal(text);
                    return actualValue.equals(expectedValue);
                } else {
                    Long actualValue = NumberUtils.createLong(currentValue);
                    Long expectedValue = NumberUtils.createLong(text);
                    return actualValue.equals(expectedValue);
                }
            }
        } catch (NumberFormatException e) {
            ConsoleUtils.log("Invalid number format.. trying other way");
            return false;
        }

        return isActualAndTextMatched(element, currentValue.trim(), text);
    }

    private String getValue(WebElement element) {
        try {
            return StringUtils.defaultIfEmpty(element.getText(), element.getAttribute("Name"));
        } catch (WebDriverException e) {
            ConsoleUtils.error("Cannot resolve target content element for '" + getLabel() + "'");
            return element.getAttribute("Name");
        }
    }

    private String filterExceptionMessage(WebDriverException e) {
        String defaultError = "Unknown Winium Driver error";
        if (e == null) { return defaultError; }

        String[] exceptionMessages = StringUtils.splitByWholeSeparator(e.getMessage(), "\n");
        if (ArrayUtils.isEmpty(exceptionMessages)) { return defaultError; }

        StringBuilder error = new StringBuilder();
        for (String exceptionMessage : exceptionMessages) {
            String message = StringUtils.trim(exceptionMessage);
            if (StringUtils.startsWith(message, "Build info: version:")) {
                // got to the useless part... get out
                break;
            }
            if (TextUtils.isBetween(message, "(WARNING:", ")")) {
                // got to the useless part.. skip
                continue;
            }
            error.append(RegexUtils.replaceMultiLines(message, "^(.*)(\\(WARNING\\:.+\\))(.*)$", "$1 $3")).append(" ");
        }

        return StringUtils.trim(error.toString());
    }

    private String normalizeUiText(String uiText) {
        if (StringUtils.isBlank(uiText)) { return StringUtils.trim(uiText); }

        // & is treated within windows desktop as a shortcut indicator - &F means Alt-F
        // return StringUtils.remove(StringUtils.trim(uiText), "&");

        // todo : not sure if we need to handle '&' or not
        return StringUtils.trim(uiText);
    }

}