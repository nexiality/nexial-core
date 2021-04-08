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
import org.nexial.core.utils.NativeInputHelper;
import org.nexial.core.utils.NativeInputParser;
import org.nexial.seeknow.SeeknowData;
import org.openqa.selenium.By;
import org.openqa.selenium.InvalidElementStateException;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.remote.RemoteWebElement;
import org.openqa.selenium.winium.WiniumDriver;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static org.nexial.core.NexialConst.Desktop.AUTOSCAN_INFRAGISTICS4_AWARE;
import static org.nexial.core.NexialConst.NL;
import static org.nexial.core.SystemVariables.getDefaultBool;
import static org.nexial.core.plugins.desktop.DesktopConst.*;
import static org.nexial.core.plugins.desktop.DesktopUtils.*;
import static org.nexial.core.plugins.desktop.ElementType.*;
import static org.nexial.core.plugins.web.WebDriverExceptionHelper.resolveErrorMessage;
import static org.nexial.core.utils.AssertUtils.requires;
import static org.openqa.selenium.Keys.ESCAPE;

/**
 * Object representation of a UI element in a desktop (native) application (Windows only).
 * <p>
 * A {@code DesktopElement} can be a textbox, checkbox, combo box, table, a container (see below) that can more
 * {@code DesktopElement}, etc.
 * <p>
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
     * determine the appropriate way to generate XPATH during autoscanning.  Copied from
     * {@link DesktopConfig#xpathGenerationStrategy}.
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
     * <p>
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
     * <li>label(index) - this refers to an indexed association of a series of form elements that are associated to 1
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

        // if combo box is disabled
        if (!element.isEnabled()) {return StepResult.fail("Text cannot be selected since '" + label + "' is disabled");}

        List<String> list = parseTextInputWithShortcuts(text, false);

        StringBuilder messages = new StringBuilder();
        boolean success = true;

        for (String item : list) {
            if (TextUtils.isBetween(item, SHORTCUT_PREFIX, SHORTCUT_POSTFIX)) {
                driver.executeScript(SCRIPT_PREFIX_SHORTCUT + item, element);
                messages.append("Shortcut key '").append(item).append("' pressed. ");
            } else {
                StepResult result = typeSpecificSelect(item);
                if (result == null) {
                    // unknown combo??
                    DesktopConst.debug("Unknown element type '" + elementType + "' for Combo '" + getLabel() +
                                       "', this might fail...");
                    element.clear();
                    element.sendKeys(item);
                } else {
                    if (result.failed()) { success = false; }
                    messages.append(result.getMessage()).append(" ");
                }
            }
        }

        autoClearModalDialog();
        return new StepResult(success, messages.toString().trim(), null);
    }

    public boolean isSelected() { return element != null && element.isSelected(); }

    protected static String getElementText(WebElement element) {
//        if (!isTextPatternAvailable(element)) { return null; }
        try {
            return element.getText();
        } catch (WebDriverException e) {
            return null;
        }
    }

    public String getText() {
        if (elementType == null || elementType == Any) { return getValue(element); }
        if (!element.isEnabled() && elementType.isTextPatternAvailable()) { return getValue(element); }

        if (elementType == SingleSelectList) {
            if (!controlType.equals(LIST_ITEM)) {
                // take the long way... find selected element (if any)
                WebElement selected = findFirstElement("*[@ControlType='" + LIST_ITEM + "' and @IsSelected='True']");
                if (selected != null) { return selected.getAttribute("Name"); }
            }

            String name = element.getAttribute("Name");
            String text = getElementText(element);
            return controlType.equals(LIST_ITEM) ?
                   StringUtils.defaultIfBlank(name, text) :
                   StringUtils.defaultIfBlank(text, name);
        }

        WebElement targetElement;
        if (elementType.isCombo()) {
            targetElement = resolveComboContentElement(element);
        } else if (elementType == TextArea) {
            String controlType = element.getAttribute("ControlType");
            if (StringUtils.endsWith(controlType, DOCUMENT)) {
                targetElement = element;
            } else {
                element.click();
                targetElement = element.findElement(By.xpath(LOCATOR_DOCUMENT));
            }
        } else if (elementType == Checkbox || elementType == Radio) {
            if (isTogglePatternAvailable(element)) {
                // todo: need to support tri-state checkboxes
                return isSelected() ? "True" : "False";
            } else {
                targetElement = element;
            }
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

    protected void inheritParentComponents(Map<String, DesktopElement> parentComponents) {
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
            if (elementType == DateTimeCombo && element != null) {
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
        if (StringUtils.isBlank(label)) { label = StringUtils.defaultIfBlank(name, APP_TOOLBAR); }
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
        if (!StringUtils.equals(controlType, RADIO)) { return false; }

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

        // we might have one of those IG anomalies, where
        //  ControlType='ControlType.Pane'
        //  IsValuePatternAvailable='True'
        //  has child of
        //      ControlType='ControlType.Custom' or 'ControlType.Edit'
        //      IsValuePatternAvailable='True'
        if (StringUtils.equals(controlType, PANE)) {
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
        } else if (StringUtils.equals(controlType, EDIT)) {
            this.elementType = Textbox;
            if (isEnabled(element)) { element.click(); }

            List<WebElement> formattedEdits = element.findElements(By.xpath(LOCATOR_TEXTBOX));
            // this looks like a masked/formatted text box since it contains multiple levels of edits
            if (CollectionUtils.isNotEmpty(formattedEdits)) { this.elementType = FormattedTextbox; }
        } else {
            // not EDIT or PANE control type
            return false;
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
        // - if 'ControlType.Table'    -> this is a table / data grid
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
        if (!StringUtils.equals(controlType, COMBO)) { return false; }

        // click element
        boolean isEnabled = isEnabled(element);
        if (isEnabled) { element.click(); }
        // if element is not enabled, we might not be able to accurately assess its underlying type; it could be single-select or type-ahead.

        this.editable = isKeyboardFocusable(element);

        List<WebElement> children = element.findElements(By.xpath("*"));
        if (CollectionUtils.isEmpty(children)) { return noChildElementFound(); }

        // re-click to un-display any dropdowns
        if (isEnabled) { element.click(); }

        ExecutionContext context = ExecutionThread.get();
        if (context.getBooleanData(AUTOSCAN_INFRAGISTICS4_AWARE, getDefaultBool(AUTOSCAN_INFRAGISTICS4_AWARE))) {
            return infragistics4AwareScan(children);
        }

        // good 'ole scan-and-determine like it was 2017
        // check for Edit
        children = element.findElements(By.xpath(LOCATOR_EDITOR));
        if (CollectionUtils.isNotEmpty(children)) {
            elementType = StringUtils.contains(automationId, "DateTime") ? DateTimeCombo : TypeAheadCombo;
            return true;
        }

        // check for child List
        children = element.findElements(By.xpath(LOCATOR_LIST));
        if (CollectionUtils.isNotEmpty(children)) {
            WebElement firstChild = children.get(0);
            if (isSelectionPatternAvailable(firstChild)) {
                elementType = BooleanUtils.toBoolean(firstChild.getAttribute("CanSelectMultiple")) ?
                              MultiSelectCombo : SingleSelectList;
            } else {
                DesktopConst.debug("WARNING: Found '" + LIST + "' child element that DOES NOT support " +
                                   "'SelectionPattern' for '" + getLabel() + "'; MIGHT NOT WORK...");
                elementType = SingleSelectList;
            }
            return true;
        }

        // check for child ComboBox
        children = element.findElements(By.xpath(LOCATOR_COMBOBOX));
        if (CollectionUtils.isNotEmpty(children)) {
            if (StringUtils.contains(automationId, "DateTime")) {
                elementType = DateTimeCombo;
                return true;
            }

            // check for Edit under Combo
            WebElement childCombo = children.get(0);
            List<WebElement> editors = childCombo.findElements(By.xpath(LOCATOR_EDITOR));
            if (CollectionUtils.isNotEmpty(editors)) {
                // then        -> this is a date/time combo
                elementType = DateTimeCombo;
            } else {
                elementType = isEnabled ? SingleSelectCombo : SingleSelectComboNotEditable;
            }
            return true;
        }

        // rare: check for nested radio button
        children = element.findElements(By.xpath(LOCATOR_RADIO));
        if (CollectionUtils.isNotEmpty(children)) {
            children.forEach(radio -> new DesktopElement(radio, this));
            elementType = null;
            return true;
        }

        throw new InvalidElementStateException("Found a " + controlType + " element with xpath " + xpath +
                                               " but does not contain any child elements of known type; " +
                                               "unknown/unsupported combo: " + printDetails(this.element));
    }

    private boolean infragistics4AwareScan(List<WebElement> children) {
        int radioFound = 0;
        int editFound = 0;
        int embeddedEditFound = 0;
        int listItemFound = 0;
        int listFound = 0;
        int comboFound = 0;

        // first pass: count the child element breakdown
        for (WebElement webElement : children) {
            String controlType = webElement.getAttribute("ControlType");
            String automationId = webElement.getAttribute("AutomationId");
            switch (controlType) {
                case EDIT: {
                    editFound++;
                    if (StringUtils.endsWith(automationId, "EmbeddableTextBox")) { embeddedEditFound++; }
                    break;
                }
                case LIST_ITEM:
                    listItemFound++;
                    break;
                case LIST: {
                    listFound++;
                    break;
                }
                case COMBO: {
                    comboFound++;
                    break;
                }
                case RADIO: {
                    radioFound++;
                    break;
                }
            }
        }

        // rare: check for nested radio button
        if (radioFound == children.size()) {
            // found all nested components are radio
            // this combo should be ignored but the radios should be included by its parent
            children.forEach(child -> {
                DesktopElement radio = new DesktopElement(child, this);
                components.put(radio.name, radio);
            });
            elementType = null;
            return true;
        }

        // check first child
        WebElement firstChild = children.get(0);
        String controlType = firstChild.getAttribute("ControlType");
        boolean isSelectable = isSelectionPatternAvailable(firstChild);
        boolean canSelectMultiple = BooleanUtils.toBoolean(firstChild.getAttribute("CanSelectMultiple"));
        boolean maybeDateTimeCombo = StringUtils.contains(automationId, "DateTime");

        if (embeddedEditFound > 0 && listItemFound < 1) {
            elementType = TypeAheadCombo;
            return true;
        }

        // if both EmbeddableTextBox and List are found, then we can just use the list
        if (listItemFound > 0 || listFound > 0) {
            if (!isSelectable) {
                DesktopConst.debug("WARNING: Found '" + LIST + "' child element that DOES NOT support " +
                                   "'SelectionPattern' for '" + getLabel() + "'; MIGHT NOT WORK...");
            }
            elementType = canSelectMultiple ? MultiSelectCombo : SingleSelectList;
            return true;
        }

        // check for Edit
        // special case for DateTime combo
        if (editFound > 0 && maybeDateTimeCombo) {
            this.elementType = DateTimeCombo;
            return true;
        }

        if (comboFound > 0) {
            if (maybeDateTimeCombo) {
                this.elementType = DateTimeCombo;
                return true;
            }

            List<WebElement> editors = firstChild.findElements(By.xpath(LOCATOR_EDITOR));
            if (CollectionUtils.isNotEmpty(editors)) {
                this.elementType = DateTimeCombo;
            } else {
                boolean isEnabled = isEnabled(element);
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

            // child could be a ControlType.Custom (IG).  We need to know editable and native control type
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
        if (StringUtils.isBlank(label) && StringUtils.isNotBlank(name)) { label = name; }
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

        //        return false;
        List<WebElement> children = element.findElements(By.xpath("*"));
        if (CollectionUtils.isEmpty(children)) { return false; }

        children.forEach(child -> {
            String controlType = child.getAttribute("ControlType");
            if (CUSTOM_NESTED_TYPES.contains(controlType)) {
                String label = StringUtils.defaultIfBlank(child.getAttribute("Name"),
                                                          child.getAttribute("AutomationId"));
                if (StringUtils.isNotBlank(label)) { components.put(label, new DesktopElement(child, this)); }
            }
        });

        return !components.isEmpty();
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
                         " without child elements" + NL + printDetails(element));
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

        DesktopConst.debug(format("[%s]: scanning...", label));

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

        List<WebElement> children = element.findElements(By.xpath("*"));
        if (CollectionUtils.isEmpty(children)) { return null; }

        List<DesktopElement> desktopElements = new ArrayList<>();
        for (WebElement child : children) {
            // ignore these..
            if (shouldSkipScanning(child)) { continue; }

            String childName = child.getAttribute("Name");
            String childAutomationId = child.getAttribute("AutomationId");
            String childControlType = child.getAttribute("ControlType");
            DesktopConst.debug(format("[%s]: scanning nested element [Name=%s, AutomationId=%s, ControlType=%s]...",
                                      label, childName, childAutomationId, childControlType));

            if (StringUtils.equals(childControlType, SCROLLBAR)) {
                handleScrollbar(child);
                continue;
            }

            DesktopElement desktopElement = new DesktopElement(child, this);
            if (desktopElement.elementType == null) {
                // maybe it contains child elements not to be ignored
                if (MapUtils.isNotEmpty(desktopElement.components)) {
                    desktopElement.components.forEach((name, element) -> addChildElement(element, desktopElements));
                }
                continue;
            }

            addChildElement(desktopElement, desktopElements);
        }

        return desktopElements;
    }

    protected void addChildElement(DesktopElement desktopElement, List<DesktopElement> desktopElements) {
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
            return;
        }

        overrideLabel(desktopElement);

        DesktopElement typeSpecificComponent = typeSpecificInspection(desktopElement);
        if (typeSpecificComponent != null) {
            desktopElements.add(typeSpecificComponent);
            return;
        }

        if (desktopElement.isContainer()) {
            if (StringUtils.isBlank(desktopElement.getLabel())) {
                DesktopConst.debug("NAMELESS CONTAINER: INSPECT DEEPER:" + NL + desktopElement.getXpath() + NL);
                // let this nameless container temporarily inherit the components of its parent so that we can
                // match up all the defined elements against discovered elements.
                desktopElement.inheritParentComponents(getComponents());
                List<DesktopElement> elements = findElementsFromNamelessContainer(desktopElement);
                if (CollectionUtils.isNotEmpty(elements)) { desktopElements.addAll(elements); }
                return;
            }

            // pane and form has no inherent label, we'd need to rely on json-level overrides
            desktopElement.inspect();
            promoteNestedComponents(desktopElement);
        }

        desktopElements.add(desktopElement);
    }

    protected List<DesktopElement> findElementsFromNamelessContainer(DesktopElement namelessContainer) {
        if (namelessContainer == null ||
            StringUtils.isBlank(namelessContainer.getXpath()) ||
            namelessContainer.getElement() == null) {
            return null;
        }

        WebElement containerElement = namelessContainer.getElement();
        DesktopConst.debug(format("collecting nested elements in container (AutomationId=%s,ClassName=%s)",
                                  containerElement.getAttribute("AutomationId"), containerElement.getAttribute("ClassName")));

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
                ConsoleUtils.error("NULL DESKTOP ELEMENT FOUND!");
            } else {
                if (desktopElement.getElementType() == null) {
                    ConsoleUtils.error("NULL ELEMENT TYPE FOUND: " + printDetails(desktopElement));
                } else {
                    if (!desktopElement.getElementType().isSelfLabel()) {
                        BoundingRectangle bound = BoundingRectangle.newInstance(desktopElement);
                        if (bound != null) {
                            if (layout.isLeftToRight()) {
                                boundGroups.computeIfAbsent(bound.getY(), list -> new ArrayList<>()).add(bound);
                            }
                            if (layout.isTwoLines()) {
                                boundGroups.computeIfAbsent(bound.getX(), list -> new ArrayList<>()).add(bound);
                            }
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
            //            DesktopConst.debug("comparing " + thisGroup + "," + nextGroup + " ...");
            if (Math.abs(thisGroup - nextGroup) <= BOUND_GROUP_TOLERANCE) {
                // put these 2 groups as 1
                //                DesktopConst.debug("combining");
                boundGroups.get(thisGroup).addAll(boundGroups.remove(nextGroup));
                groups.remove(nextGroup);
                i--;
                //            } else {
                //                DesktopConst.debug("kept separate");
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
                verticalGroup.sort(Comparator.comparing(BoundingRectangle::getY));
                boundGroups.put(0, verticalGroup);
            }
        }

        // sort bounds within the associated groups (either row group or column group)
        boundGroups.forEach((name, bounds) -> {
            if (layout.isLeftToRight()) { bounds.sort(Comparator.comparingInt(BoundingRectangle::getX)); }
            if (layout.isTwoLines()) { bounds.sort(Comparator.comparingInt(BoundingRectangle::getY)); }
        });

        //        groups.forEach(group -> DesktopConst.debug(NL + "Group [" + group + "] Bounds:" + NL + "\t" +
        //                                                   TextUtils.toString(boundGroups.get(group), NL + "\t")));

        return boundGroups;
    }

    protected void assignLabelToElements(Map<Integer, List<BoundingRectangle>> boundGroups) {
        // group label to one or more input widgets
        // note:
        // 1) checkbox might not be associated with a label (ControlType.Text), but itself has label (@Name)
        // 2) checkbox might be grouped for those that share the same x or the same y coordinates
        //    (BUT BETTER NOT TO GROUP THEM SINCE EACH CHECKBOX SHOULD BE INDIVIDUALLY IDENTIFIED)
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
        if (StringUtils.isNotBlank(xpath)) { setElement(driver.findElement(By.xpath(xpath)));}
    }

    /** meant for TextBox or TextArea */
    protected StepResult typeTextComponent(boolean useSendKeys, boolean append, String... text) {
        requires(ArrayUtils.isNotEmpty(text), "at least one text parameter is required");

        String currentText = getText();
        String combinedText = TextUtils.toString(text, "", "", "");

        // special case: if specified text and the current text is the same
        // make sure we don't have control/shortcut key here
        if (!append && !StringUtils.contains(combinedText, "[")) {
            if (StringUtils.isEmpty(combinedText)) {
                clearFormattedTextbox(driver, element);
                return StepResult.success("text cleared from element '%s'", label);
            }

            if (StringUtils.equals(currentText, combinedText)) {
                return StepResult.success("text already entered into element '%s'", label);
            }
        }

        ExecutionContext context = ExecutionThread.get();
        if (context != null && context.getBooleanData(DESKTOP_USE_TYPE_KEYS, DEF_DESKTOP_USE_TYPE_KEYS)) {
            String keystrokes = (append ? "[CTRL-END]\n" : "") + TextUtils.toString(text, "\n", "", "");
            keystrokes = NativeInputParser.handleKeys(keystrokes);
            NativeInputHelper.typeKeys(TextUtils.toList(StringUtils.remove(keystrokes, "\r"), "\n", false));
        } else {
            if (append) {
                try {
                    driver.executeScript(SCRIPT_PREFIX_SHORTCUT +
                                         forceShortcutSyntax("[CTRL-END]") + joinShortcuts(text),
                                         element);
                } catch (WebDriverException e) {
                    ConsoleUtils.error("Error when typing '%s' on '%s': %s",
                                       ArrayUtils.toString(text), label, resolveErrorMessage(e));
                }
            } else {
                // join text into 1 string, parse the entire combined string and loop through each token to type
                // parseTextInputWithShortcuts(TextUtils.toString(text, "", "", ""), true).forEach(txt -> type(txt, useSendKeys));

                // append=false means overwrite. Hence the HOME->SHIFT-END->DEL sequence
                if (StringUtils.isNotEmpty(currentText)) { clearFormattedTextbox(driver, element); }
                type(TextUtils.toString(parseTextInputWithShortcuts(combinedText, true), ""), useSendKeys);
            }
        }

        autoClearModalDialog();
        return StepResult.success("text %s into element '%s'", (append ? "appended" : "entered"), label);
    }

    protected static List<String> parseTextInputWithShortcuts(String text, boolean forceShortcuts) {
        List<String> list = new ArrayList<>();

        if (StringUtils.isEmpty(text)) { return list; }

        if (StringUtils.isBlank(text)) {
            if (forceShortcuts) { text = TextUtils.wrapIfMissing(text, TEXT_INPUT_PREFIX, TEXT_INPUT_POSTFIX); }
            list.add(text);
            return list;
        }

        text = StringUtils.remove(text, "\r");
        text = StringUtils.replace(text, "\n", "[ENTER]");

        String regex = "^.*?\\[.+].*?$";

        while (RegexUtils.match(text, regex, true)) {
            String beforeShortcut = StringUtils.substringBefore(text, "[");
            if (StringUtils.isNotEmpty(beforeShortcut)) {
                if (forceShortcuts) {
                    beforeShortcut = TextUtils.wrapIfMissing(beforeShortcut, TEXT_INPUT_PREFIX, TEXT_INPUT_POSTFIX);
                }
                list.add(beforeShortcut);

                text = "[" + StringUtils.substringAfter(text, "[");
            }

            if (StringUtils.contains(text, "]")) {
                list.add(SHORTCUT_PREFIX + StringUtils.substringBetween(text, "[", "]") + SHORTCUT_POSTFIX);
                text = StringUtils.substringAfter(text, "]");
            }
        }

        if (StringUtils.isNotEmpty(text)) {
            if (forceShortcuts) { text = TextUtils.wrapIfMissing(text, TEXT_INPUT_PREFIX, TEXT_INPUT_POSTFIX); }
            list.add(text);
        }

        return list;
    }

    //    protected void type(String text) { type(text, false); }

    protected void type(String text, boolean useSendKeys) {
        if (StringUtils.isEmpty(text)) { return; }

        if (!element.isEnabled()) { CheckUtils.fail("Text cannot be entered as it is disabled for input"); }

        // assuming that `text` has already been treated with the appropriate shortcut markings
        //        text = StringUtils.trim(treatShortcutSyntax(text));

        if (getElementType() == FormattedTextbox) {

            if (TextUtils.isBetween(text, SHORTCUT_PREFIX, SHORTCUT_POSTFIX)) {
                driver.executeScript(SCRIPT_PREFIX_SHORTCUT + text, element);
                return;
            }

            // click to be avoided here in formatted textbox before calling clear, which gives undesired results
            if (StringUtils.isNotEmpty(getElementText(element))) { element.clear(); }
            // setValue does not work for action driven components. sendKeys and shortcut script both works
            driver.executeScript(SCRIPT_PREFIX_SHORTCUT + TEXT_INPUT_PREFIX + text + TEXT_INPUT_POSTFIX, element);
            // only perform verification is current component is not a date/time editor (those things are unreliable)
            if (!StringUtils.contains(automationId, "DateTimeEditor")) { verifyAndTry(text); }
            return;
        }

        // click required to make sure that the focus is set
        // element.click();
        // (ABOVE): NO LONGER FORCEFULLY CLICK ON ELEMENT PRIOR TO TYPING, AS THIS BREAKS THE FLOW OF KEYSTROKE
        // SEQUENCES EXPRESSED VIA MULTIPLE PARAMETERS. CALLING METHOD SHOULD CONSIDER DOING SO IF NEEDED.

        if (StringUtils.contains(text, SHORTCUT_PREFIX) && StringUtils.contains(text, SHORTCUT_POSTFIX)) {
            try {
                driver.executeScript(SCRIPT_PREFIX_SHORTCUT + text, element);
            } catch (WebDriverException e) {
                ConsoleUtils.error("Error when executing shortcut '%s' on '%s': %s",
                                   text, label, resolveErrorMessage(e));
            }

            return;
        }

        if (setValue(useSendKeys, element, text)) { return; }

        element.clear();
        element.sendKeys(text);
    }

    protected void clearFormattedTextbox(WiniumDriver driver, WebElement target) {
        String targetAutomationId = target.getAttribute("AutomationId");
        boolean isMaskedTextbox = StringUtils.containsIgnoreCase(targetAutomationId, "MaskedText");

        // short-circuit with shortcut first
        // special case: masked editor should be cleared via ESCAPE
        driver.executeScript(isMaskedTextbox ?
                             DesktopUtils.toShortcuts("ESC") :
                             DesktopUtils.toShortcuts("HOME", "SHIFT-END", "DEL"),
                             target);

        String afterShortcutDelete = postClearFormattedTextbox(target);
        if (StringUtils.isEmpty(afterShortcutDelete)) { return; }

        // special case: masked editor and remaining value all zero's
        if (isMaskedTextbox && StringUtils.containsOnly(afterShortcutDelete, "0")) {
            return;
        }

        target.clear();
        afterShortcutDelete = postClearFormattedTextbox(target);
        if (StringUtils.isEmpty(afterShortcutDelete)) { return; }

        int previousValueCount;
        List<WebElement> editables = target.findElements(By.xpath(LOCATOR_TEXTBOX));
        if (CollectionUtils.isNotEmpty(editables)) {
            previousValueCount = StringUtils.length(postClearFormattedTextbox(target));
        } else {
            // find existing values found in textbox for delete
            previousValueCount = StringUtils.length(getElementText(target));
        }

        // attempt to clear off character in this field
        if (previousValueCount > 0) {
            driver.executeScript(DesktopUtils.toShortcuts("CTRL-HOME") +
                                 StringUtils.repeat("<[DEL]>", previousValueCount + 1),
                                 target);
        }
    }

    protected String postClearFormattedTextbox(WebElement element) {
        String text = getValue(element);
        if (StringUtils.isEmpty(text)) { return text; }

        if (elementType == DateTimeCombo) { return StringUtils.trim(StringUtils.replaceChars(text, " _/:", "")); }

        // todo currency sign should be externalized for extensibility
        if (StringUtils.startsWithAny(text, "$", "€")) { text = StringUtils.substring(text, 1); }
        return StringUtils.trim(StringUtils.replaceChars(text, " _.", ""));
    }

    /** This is to check the set value is equal with entered text **/
    protected boolean isActualAndTextMatched(WebElement element, String actual, String text) {
        if (StringUtils.isEmpty(actual)) { actual = element.getAttribute("Name"); }

        actual = StringUtils.remove(actual.trim(), '\r');
        text = StringUtils.remove(text.trim(), '\r');
        if (NumberUtils.isParsable(actual) && NumberUtils.isParsable(text)) {
            return NumberUtils.toDouble(actual) == NumberUtils.toDouble(text);
        }
        return StringUtils.equals(text, actual);
    }

    protected boolean setValue(WebElement element, String text) { return setValue(false, element, text); }

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
                driver.executeScript(SCRIPT_SET_VALUE, element, text);
            }
            String actual = getElementText(element);
            boolean matched = isActualAndTextMatched(element, actual, text);

            if (!matched) {
                //todo: apply some strategy to set the value when it is not matched
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
            driver.executeScript(SCRIPT_SET_VALUE, element, text);
            String actual = getElementText(element);
            boolean matched = isActualAndTextMatched(element, actual, text);
            if (!matched) {
                // try again... this time with extra space in the front to avoid autofill
                driver.executeScript(SCRIPT_SET_VALUE, element, " " + text);
                driver.executeScript(toShortcuts("CTRL-HOME", "DEL"), element);
                actual = getElementText(element);
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
        // todo: need to implement for multi select combo
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
        String currentSelection = getText();
        if (StringUtils.equals(currentSelection, text)) {
            return StepResult.success("Text '" + text + "' already entered into '" + label + "'");
        }

        driver.executeScript(SCRIPT_PREFIX_SHORTCUT +
                             (StringUtils.isNotEmpty(currentSelection) ? "<[HOME]><[SHIFT-END]><[DEL]>" : "") +
                             forceShortcutSyntax(text),
                             element);
        autoClearModalDialog();
        return StepResult.success("Text '" + text + "' entered into '" + label + "'");
    }

    protected StepResult clear() {
        ElementType elementType = getElementType();
        if (elementType.isCombo()) { return clearCombo(); }

        if (elementType == TextArea) {
            driver.executeScript(toShortcuts("CTRL-HOME", "CTRL-SHIFT-END", "DEL"), getElement());
            // } else if (elementType == FormattedTextbox) {
            //     clearFormattedTextbox(driver, element);
        } else {
            // turns out that `clearFormattedTextbox` works better for both normal edit and formatted textbox
            clearFormattedTextbox(driver, element);
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

        String msgSuccess = "Combo '" + label + "' cleared";

        if (elementType == DateTimeCombo || elementType == TypeAheadCombo || elementType == SingleSelectCombo) {
            driver.executeScript(elementType == SingleSelectCombo ?
                                 toShortcuts("ESC") : toShortcuts("HOME", "SHIFT-END", "DEL"),
                                 element);
            autoClearModalDialog();
            return StepResult.success(msgSuccess);
        }

        if (elementType == SingleSelectList) {
            String selectedText = getText();
            if (StringUtils.isEmpty(selectedText)) { return StepResult.success("Combo '%s' is already cleared", label);}

            driver.executeScript(toShortcuts("ESC", "TAB"), element);
            autoClearModalDialog();
            selectedText = getText();
            if (StringUtils.isBlank(selectedText)) { return StepResult.success(msgSuccess); }

            return StepResult.success("After attempting to clear Combo '" + label + "', " +
                                      "the currently selected option is '" + selectedText + "'");
        }

        return StepResult.fail("Unknown element type " + elementType);
    }

    protected StepResult selectSingleSelectList(String text) {
        String msgPostfix = " for '" + label + "'.";

        // we want blank?
        if (StringUtils.isBlank(text)) {
            element.clear();
            element.sendKeys(ESCAPE);
            return StepResult.success("Text cleared" + msgPostfix);
        }

        if (!isSelectionPatternAvailable(element)) {
            return StepResult.fail("Unable select since selection-pattern is not available" + msgPostfix);
        }

        List<WebElement> nestedElements = element.findElements(By.xpath("*"));
        if (CollectionUtils.isEmpty(nestedElements)) {
            return StepResult.fail("Unable to select %s in %s; no selection available", text, getLabel());
        }

        AtomicBoolean hasListItem = new AtomicBoolean(false);
        AtomicBoolean hasEditor = new AtomicBoolean(false);
        AtomicBoolean hasList = new AtomicBoolean(false);
        nestedElements.forEach(elem -> {
            String controlType = elem.getAttribute("ControlType");
            if (StringUtils.equals(controlType, EDIT)) { hasEditor.set(true); }
            if (StringUtils.equals(controlType, LIST)) { hasList.set(true); }
            if (StringUtils.equals(controlType, LIST_ITEM)) { hasListItem.set(true); }
        });

        String currentSelectedText = getText();
        if (StringUtils.isNotEmpty(currentSelectedText)) {
            // combo's text won't always be the same as the specified text because combo might display one text while
            // storing another.
            if (StringUtils.equals(currentSelectedText, text)) {
                return StepResult.success("Text '" + text + "' already selected" + msgPostfix);
            }

            // however we can still check to see if any selection has been made at this time, and if so, check that the
            // selected list item is the same as the specified text.
            if (hasListItem.get()) {
                WebElement targetItem = findFirstElement(LOCATOR_SELECTED_LIST_ITEM);
                if (targetItem != null && StringUtils.equals(targetItem.getAttribute("Name"), text)) {
                    return StepResult.success("Text '" + text + "' already selected" + msgPostfix);
                }
            }
        }

        if (isExpandCollapsePatternAvailable(element)) {
            element.click();

            // nested EmbeddableTextBox?
            WebElement embeddableTextBox = null;
            if (hasEditor.get()) {
                List<WebElement> nestedEditors = element.findElements(By.xpath(LOCATOR_EDITOR));
                // if size == 1 then unlikely to contain EmbeddableTextBox
                embeddableTextBox = CollectionUtils.size(nestedEditors) > 1 ?
                                    findFirstElement(LOCATOR_EDITOR + LOCATOR_EMBEDDABLE_TEXTBOX) : null;
            }

            if (embeddableTextBox != null) {
                // need to clear existing data first... unless it already contains the specified text
                String existingText = getElementText(embeddableTextBox);
                if (!StringUtils.equals(existingText, text)) {
                    driver.executeScript(SCRIPT_PREFIX_SHORTCUT +
                                         (StringUtils.isNotEmpty(existingText) ? "<[ESC]><[HOME]><[SHIFT-END]><[DEL]>" :
                                          "") +
                                         forceShortcutSyntax(text),
                                         embeddableTextBox);
                }
                return StepResult.success("Text '" + text + "' selected" + msgPostfix);
            } else {
                WebElement targetItem = null;
                if (hasList.get()) {
                    targetItem = findFirstElement(StringUtils.replace(LOCATOR_LIST_TO_ITEM, "{value}", text));
                }
                if (hasListItem.get()) {
                    targetItem = findFirstElement(StringUtils.replace(LOCATOR_LIST_ITEM_ONLY, "{value}", text));
                }
                if (targetItem != null) {
                    if (!targetItem.isSelected()) {
                        if (isInvokePatternAvailable(targetItem)) {
                            element.sendKeys(ESCAPE);
                            element.click();
                            targetItem.click();
                        } else {
                            try {
                                // try click anyways... if it works it's faster
                                targetItem.click();
                            } catch (WebDriverException e) {
                                // nope... no good, try the old fashion select via first char
                                // clear off any existing selection
                                if (StringUtils.isNotEmpty(currentSelectedText)) { execEscape(this.element); }
                                StepResult result = singleSelectViaFirstChar(text);
                                element.click();
                                return result;
                            }
                        }
                    } else {
                        // collapse dropdown
                        element.click();
                    }

                    return StepResult.success("Text '" + text + "' selected" + msgPostfix);
                }
            }
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
        String value = getValue(element);
        if (StringUtils.equals(text, value)) {
            return StepResult.success("Text '" + text + "' already selected for '" + label + "'");
        }

        boolean comboOpened = false;
        if (StringUtils.isNotEmpty(value)) {
            execEscape(element);
        } else {
            comboOpened = true;
            element.click();
        }

        try {
            List<WebElement> nestedElements = element.findElements(By.xpath("*"));
            if (CollectionUtils.isEmpty(nestedElements)) {
                return StepResult.fail("Unable to select %s in %s; no selection available", text, getLabel());
            }

            // does this combo contains a edit?
            boolean hasEdit = nestedElements.stream()
                                            .anyMatch(elem -> StringUtils.equals(elem.getAttribute("ControlType"), EDIT));
            if (hasEdit) {
                driver.executeScript(toShortcutText(text), element);
                return StepResult.success("Text '%s' entered into '%s'", text, label);
            }

            return singleSelectViaFirstChar(text);
        } finally {
            if (comboOpened) { element.click(); }
        }
    }

    protected void execEscape(WebElement elem) { driver.executeScript(SCRIPT_PREFIX_SHORTCUT + "<[ESC]>", elem); }

    @Nonnull
    protected List<String> listOptions() { return listComboOptions(element, getLabel()); }

    @Nonnull
    protected static List<String> listComboOptions(WebElement combo, String label) {
        List<WebElement> options = combo.findElements(By.xpath(LOCATOR_LIST_ITEMS));
        return CollectionUtils.isEmpty(options) ?
               new ArrayList<>() :
               options.stream().map(option -> getValue(option, label + " option")).collect(Collectors.toList());
    }

    @Nonnull
    private StepResult singleSelectViaFirstChar(String text) {
        String firstChar = "" + text.charAt(0);
        String selectionScript = toShortcutText(firstChar);

        List<WebElement> selectionCandidates = element.findElements(By.xpath("*[contains(@Name,'" + firstChar + "')]"));
        boolean useFirstChar = CollectionUtils.isEmpty(selectionCandidates);
        if (useFirstChar) {
            ConsoleUtils.log("No selections found under Combo '" + label + "'; " +
                             "elect 'useFirstChar' strategy to select '" + text + "'");
        }

        if (useFirstChar) {
            String value = normalizeUiText(element.getAttribute("Name"));
            String initialValue = value;

            // keep typing the first character until the selected text matches "text"
            while (!StringUtils.equals(value, text)) {
                driver.executeScript(selectionScript, element);
                // autoClearModalDialog();

                value = normalizeUiText(element.getAttribute("Name"));

                // probably means we've looped around.. so we are done
                if (StringUtils.equals(value, initialValue)) { break; }
            }

            if (!StringUtils.equals(value, text)) {
                return StepResult.fail("Unable to select in '" + label + "' the text '" + text + "': " + value);
            }
        } else {
            String isSelectedXpath = "*[@Name and @IsSelected='True']";
            String firstSelection = null;

            // fictitious upper limit based on specified text
            for (int i = 0; i < selectionCandidates.size(); i++) {
                driver.executeScript(selectionScript, element);

                WebElement selected = findFirstElement(isSelectedXpath);
                if (selected == null) { continue; }

                String selectedValue = selected.getAttribute("Name");
                if (StringUtils.equals(selectedValue, text)) { break; }

                if (firstSelection == null && StringUtils.isNotEmpty(selectedValue)) {
                    firstSelection = selectedValue;
                    continue;
                }

                // looped around already, we are done!
                if (StringUtils.equals(firstSelection, selectedValue)) {
                    return StepResult.fail("Unable to select text '%s' from '%s'", text, getLabel());
                }
            }
        }

        return StepResult.success("Text '%s' entered into '%s'", text, label);
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
        if (StringUtils.isBlank(controlType)) {
            // maybe it's an opaque custom type?
            List<WebElement> children = element.findElements(By.xpath("*"));
            // contains no children.. skip this one then
            if (CollectionUtils.isEmpty(children)) { return true; }
        }

        if (IGNORE_CONTROL_TYPES.contains(controlType)) { return true; }

        // ignore elements with empty ClassName, Name and AutomationId
        String className = element.getAttribute("ClassName");
        if (StringUtils.isBlank(className)) {
            String name = element.getAttribute("Name");
            String automationId = element.getAttribute("AutomationId");
            if (StringUtils.isBlank(name) && StringUtils.isBlank(automationId)) {
                return true;
            }
        }

        // Win32 standard Open/Save dialog's standard component...
        // we don't need these since we'd usually just specify full path in the 'File name' field
        if (StringUtils.equals(controlType, TREE) && IGNORE_TREE_CLASSNAMES.contains(className)) { return true; }
        if (StringUtils.equals(controlType, PANE) && IGNORE_PANE_CLASSNAMES.contains(className)) { return true; }
        return StringUtils.equals(controlType, TOOLBAR) && IGNORE_TOOLBAR_CLASSNAMES.contains(className);

        // all else
    }

    protected WebElement findFirstElement(String xpath) { return findFirstElement(element, xpath); }

    protected WebElement findFirstElement(WebElement element, String xpath) {
        if (element == null || StringUtils.isBlank(xpath)) { return null; }
        return CollectionUtil.getOrDefault(element.findElements(By.xpath(xpath)), 0, null);
    }

    private void verifyAndTry(String text) {
        if (verifyText(text, getElementText(element))) { return; }

        clearFormattedTextbox(driver, element);
        driver.executeScript(SCRIPT_PREFIX_SHORTCUT + TEXT_INPUT_PREFIX + text + TEXT_INPUT_POSTFIX, element);
        // trust text entered correctly after second try!!
    }

    private boolean verifyText(String text, String currentValue) {
        if (!NumberUtils.isCreatable(text)) { return isActualAndTextMatched(element, currentValue, text); }

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

        return isActualAndTextMatched(element, currentValue, text);
    }

    protected String getValue(WebElement element) { return getValue(element, getLabel()); }

    protected static String getValue(WebElement element, String label) {
        try {
            return StringUtils.trim(StringUtils.defaultIfBlank(getElementText(element), element.getAttribute("Name")));
        } catch (WebDriverException e) {
            ConsoleUtils.log("Unable to resolve text content for '" + label + "', retrying via @Name attribute...");
            return StringUtils.trim(element.getAttribute("Name"));
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
        //        if (StringUtils.isBlank(uiText)) { return StringUtils.trim(uiText); }

        // & is treated within windows desktop as a shortcut indicator - &F means Alt-F
        // return StringUtils.remove(StringUtils.trim(uiText), "&");

        // todo : not sure if we need to handle '&' or not
        return StringUtils.trim(uiText);
    }

}