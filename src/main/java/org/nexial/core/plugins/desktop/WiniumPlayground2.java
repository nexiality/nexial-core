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

import java.io.Console;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.junit.Assert;
import org.nexial.core.model.StepResult;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.winium.WiniumDriver;

import org.nexial.commons.utils.TextUtils;
import org.nexial.core.plugins.desktop.TableData.TableRow;
import org.nexial.core.plugins.desktop.ig.IgExplorerBar;
import org.nexial.core.plugins.desktop.ig.IgRibbon;
import org.nexial.core.utils.ConsoleUtils;

import static org.nexial.core.plugins.desktop.DesktopConst.*;
import static org.nexial.core.plugins.desktop.DesktopUtils.printDetails;
import static org.nexial.core.plugins.desktop.DesktopUtils.toShortcuts;
import static org.nexial.core.plugins.desktop.ElementType.*;
import static java.lang.Integer.MAX_VALUE;

public class WiniumPlayground2 {
	public static final DecimalFormat AVG_FORMAT = new DecimalFormat("###.00");
	// private static final List<String> MANUAL_RUN_APPS = Arrays.asList("ngp", "smartaccounting");
	private static final List<String> COMMON_LABELS = Arrays.asList(APP_TITLEBAR,
	                                                                APP_TOOLBAR,
	                                                                APP_MENUBAR,
	                                                                APP_STATUSBAR);
	private static final NumberFormat SEC_FORMATTER = new DecimalFormat("#0.000");
	private static final int MENU_WIDTH = 34;
	private static final int SECTION_WIDTH = 15;

	private static DesktopSession session;
	private static String currentContainerName;
	private static DesktopElement currentContainer;
	private static DesktopList list;
	private static DesktopTable table;
	private static DesktopHierTable hiertable;
	private static boolean explorerBarExpanded = true;

	public static void main(String[] args) {
		if (ArrayUtils.isEmpty(args)) {
			System.err.println("Specify a port to join or a AUT path to start new session");
			System.exit(-1);
		}

		Console console = System.console();

		while (true) {
			String opt = console.readLine(
				makeMenuSection("COMMON") +
				makeMenuItem("useApp(app)") +
				makeMenuItem("useForm(name)") +

				makeMenuSection("DEBUG") +
				makeMenuItem("printComponents(name)") +
				makeMenuItem("refetchComponents(name)") +

				makeMenuSection("LOGIN") +
				makeMenuItem("login(name,password)") +

				makeMenuSection("EXPLORER BAR") +
				makeMenuItem("toggleExplorerBar()") +
				makeMenuItem("clickExplorerBar(group,item)") +
				makeMenuItem("typeExplorerBar(shortcuts)") +

				makeMenuSection("MENU/TOOLBAR") +
				makeMenuItem("clickIcon(label)") +
				makeMenuItem("clickMenu(menu :: menu :: ...)") +

				makeMenuSection("CONTROLS") +
				makeMenuItem("type(name,text)") +
				makeMenuItem("typeAppend(name,text)") +
				makeMenuItem("getText(name)") +
				makeMenuSection(" ") +
				makeMenuItem("select(name,value)") +
				makeMenuItem("isSelected(name)") +
				makeMenuItem("clearCombo(name)") +
				makeMenuSection(" ") +
				makeMenuItem("click(name)") +
				makeMenuItem("clickTab(name,tab)") +
				makeMenuItem("clear(name)") +

				makeMenuSection("LIST") +
				makeMenuItem("useList(name)") +
				makeMenuItem("clickList(index)") +
				makeMenuItem("clickFirstMatchedList(contains)") +

				makeMenuSection("OUTPUT") +
				makeMenuItem("clickTextPane(name,criteria)") +
				makeMenuSection(" ") +
				makeMenuItem("findTextPane(name,criteria)") +

				makeMenuSection("TABLE") +
				makeMenuItem("useTable(table)") +
				makeMenuItem("getTableRows(from,to)") +
				makeMenuItem("getTableRowsAll()") +
				makeMenuItem("clickTableRow(row)") +
				makeMenuSection(" ") +
				makeMenuItem("clickTableCell(row,name)") +
				makeMenuItem("editTableCells(row,nameValues)") +
				makeMenuItem("assertTableCell(rowColumnContains)") +
				makeMenuItem("readRows(begin,end)") +
				makeMenuSection(" ") +
				makeMenuItem("readMetadata()") +
				makeMenuItem("rowCount()") +
				makeMenuSection("HIERARCHICAL") +
				makeMenuItem("useHierTable(name)") +
				makeMenuItem("getHierRow(matchBy)") +
				makeMenuItem("collapseHierTable()") +
				makeMenuSection(" ") +
				makeMenuItem("getHierCellData(matchBy,column)") +
				makeMenuItem("assertHierCell(matchColumn,matchBy,column,expected)") +
				makeMenuItem("editHierCells(matchColumn,matchBy,nameValues)") +
				makeMenuSection("WINDOW") +
				makeMenuItem("clearModalDialog(button)") +
				makeMenuSection(" ") +
				makeMenuItem("maximize()") +
				makeMenuItem("minimize()") +
				makeMenuItem("resize(WIDTHxHEIGHT)") +
				makeMenuSection("Memory Usage") +
				makeMenuItem("memoryStats()") +

				"\n" +

				makeMenuItem("Q quit") +
				"\n\n" +
				"> ");

			if (StringUtils.equalsIgnoreCase(opt, "Q")) {
				if (session != null) {session.terminateSession(); }
				System.exit(0);
			}

			System.out.println("\nProcessing " + opt);
			String command = StringUtils.substringBefore(opt, " ");
			String params = StringUtils.substringAfter(opt, " ");

			StopWatch tickTock = new StopWatch();
			tickTock.start();

			try {
				switch (command) {
					case "useApp": {
						useApp(params);
						break;
					}
					case "useForm": {
						useForm(params);
						break;
					}

					case "printComponents": {
						printComponents(params);
						break;
					}
					case "refetchComponents": {
						refetchComponents(params);
						break;
					}

					case "login": {
						login(params);
						break;
					}

					case "toggleExplorerBar": {
						toggleExplorerBar();
						break;
					}
					case "clickExplorerBar": {
						clickExplorerBar(params);
						break;
					}
					case "typeExplorerBar": {
						typeExplorerBar(params);
						break;
					}

					case "clickIcon": {
						clickIcon(params);
						break;
					}
					case "clickMenu": {
						clickMenu(params);
						break;
					}

					case "click": {
						click(params);
						break;
					}
					case "type": {
						type(params);
						break;
					}
					case "typeAppend": {
						typeAppend(params);
						break;
					}
					case "getText": {
						getText(params);
						break;
					}
					case "select": {
						select(params);
						break;
					}
					case "isSelected": {
						isSelected(params);
						break;
					}
					case "clearCombo": {
						clearCombo(params);
						break;
					}

					case "clickTab": {
						clickTab(params);
						break;
					}
					case "clear": {
						clear(params);
						break;
					}

					case "useList": {
						useList(params);
						break;
					}
					case "clickList": {
						clickList(params);
						break;
					}
					case "clickFirstMatchedList": {
						clickFirstMatchedList(params);
						break;
					}

					case "clickTextPane": {
						clickTextPane(params);
						break;
					}
					case "findTextPane": {
						findTextPane(params);
						break;
					}

					case "useTable": {
						useTable(params);
						break;
					}
					case "getTableRows": {
						getTableRows(params);
						break;
					}
					case "clickTableRow": {
						clickTableRow(params);
						break;
					}
					case "clickTableCell": {
						clickTableCell(params);
						break;
					}
					case "editTableCells": {
						editTableCells(params);
						break;
					}

					case "useHierTable": {
						useHierTable(params);
						break;
					}

					case "getHierRow": {
						getHierRow(params);
						break;
					}
					case "typeHierTable": {
						typeHierTable(params);
						break;
					}
					case "editHierCells": {
						editHierCells(params);
						break;
					}
					case "getHierCellData": {
						getHierCellData(params);
						break;
					}
					case "assertHierCell": {
						assertHierCell(params);
						break;
					}

					case "clearModalDialog": {
						clearModalDialog(params);
						break;
					}
					case "maximize": {
						maximize();
						break;
					}
					case "minimize": {
						minimize();
						break;
					}
					case "resize": {
						resize(params);
						break;
					}

					case "assertTableCell": {
						assertTableCell(params);
						break;
					}

					case "collapseAll": {
						collapseAll();
						break;
					}

					case "getTableRowsAll": {
						getTableRowsAll();
						break;
					}

					case "rowCount": {
						rowCount();
						break;
					}

					default: {
						System.err.println("!!! UNKNOWN COMMAND: " + command + ".  Unable to process.");
					}
				}

			} catch (Throwable e) {
				e.printStackTrace();
			}

			tickTock.stop();
			System.out.println();
			System.out.println("Total Time: " + SEC_FORMATTER.format((double) tickTock.getTime() / 1000) + " seconds");
			System.out.println();
		}
	}

	private static String makeMenuItem(String menu) { return StringUtils.rightPad("» " + menu, MENU_WIDTH); }

	private static String makeMenuSection(String section) {
		return "\n" + (StringUtils.isBlank(section) ?
		               StringUtils.repeat(" ", SECTION_WIDTH) :
		               StringUtils.leftPad("[" + section + "] ", SECTION_WIDTH));
	}

	private static void useApp(String appId) throws Exception {
		assert StringUtils.isNotBlank(appId);

		Date startTime = new Date();

		// load application.json
		currentContainerName = null;
		session = DesktopSession.newInstance(appId, null);
		assert session != null;
		assert StringUtils.equals(session.getAppId(), appId);

		// basic validations
		DesktopConfig config = session.getConfig();
		assert config != null;
		assert StringUtils.equals(config.getAppId(), appId);
		assert config.getAppStartupWaitMs() > 1;
		assert config.getDefaultWaitMs() > 1;
		assert config.getAut() != null;

		Aut aut = config.getAut();
		assert aut != null;

		// tall xpath inspected
		int totalXpathInspected = 0;
		Map<String, DesktopElement> components = session.getApp().getComponents();
		Set<String> componentLabels = components.keySet();
		for (String label : componentLabels) {
			if (COMMON_LABELS.contains(label)) {
				totalXpathInspected += StringUtils.countMatches(GSON.toJson(components.get(label)), "\"xpath\"");
			}
		}

		// report to console
		reportScanStats(startTime, totalXpathInspected);
	}

	private static void useForm(String containerName) {
		assert StringUtils.isNotBlank(containerName);
		assert session != null;
		assert session.getApp() != null;

		Date startTime = new Date();

		DesktopElement container = session.findContainer(containerName, session.getApp());
		if (container == null) {
			System.err.println("No component named as " + containerName);
			return;
		}

		currentContainerName = containerName;
		currentContainer = container;

		int totalXpathInspected = StringUtils.countMatches(GSON.toJson(container), "\"xpath\"");

		// report to console
		reportScanStats(startTime, totalXpathInspected);
	}

	private static void printComponents(String name) {
		name = addContainerPrefix(name);
		DesktopElement component = session.getApp().getComponent(name);
		System.out.println("component labeled as " + name + ":\n" + component);
	}

	private static void refetchComponents(String containerName) {
		assert StringUtils.isNotBlank(containerName);
		assert session != null;
		assert session.getApp() != null;

		Date startTime = new Date();

		DesktopElement container = session.getApp().getComponents().get(containerName);
		if (container == null) {
			System.out.println("No container named as " + containerName);
			return;
		}

		container.refetchComponents();

		int totalElementFetched = 1 + container.components.size();
		Date endTime = new Date();
		long timeSpent = ((endTime.getTime() - startTime.getTime()));
		String avgScanTimeMs = AVG_FORMAT.format((double) timeSpent / (double) totalElementFetched);

		System.out.println(asOutputLabel("Start time") + startTime);
		System.out.println(asOutputLabel("End time") + endTime);
		System.out.println(asOutputLabel("Elapsed time (sec)") + Math.round((double) timeSpent / 1000));
		System.out.println(asOutputLabel("# element fetched") + totalElementFetched);
		System.out.println(asOutputLabel("Avg element fetch time (ms)") + avgScanTimeMs);
	}

	private static void login(String usernameAndPassword) {
		assert StringUtils.isNotBlank(usernameAndPassword);
		assert StringUtils.contains(usernameAndPassword, ",");

		String username = StringUtils.substringBefore(usernameAndPassword, ",");
		String password = StringUtils.substringAfter(usernameAndPassword, ",");

		LoginForm login = (LoginForm) session.findThirdPartyComponentByName("login");
		assert login != null;
		StepResult result = login.login(username, password);

		System.out.println();
		System.out.println(result);
	}

	private static void toggleExplorerBar() {
		IgExplorerBar explorerBar = session.findThirdPartyComponent(IgExplorerBar.class);
		if (explorerBarExpanded) {
			explorerBar.collapse();
			explorerBarExpanded = false;
		} else {
			explorerBar.expand();
			explorerBarExpanded = true;
		}
	}

	private static void clickExplorerBar(String groupAndItem) {
		assert StringUtils.isNotBlank(groupAndItem);
		assert StringUtils.contains(groupAndItem, ",");

		String group = StringUtils.substringBefore(groupAndItem, ",");
		String item = StringUtils.substringAfter(groupAndItem, ",");

		IgExplorerBar explorerBar = session.findThirdPartyComponent(IgExplorerBar.class);
		assert explorerBar != null;
		StepResult result = explorerBar.click(group, item);

		if (result.isSuccess()) {
			IgRibbon ribbon = session.findThirdPartyComponent(IgRibbon.class);
			if (ribbon != null) { ribbon.updateCurrentModule(group + NESTED_CONTAINER_SEP + item); }
		}

		System.out.println();
		System.out.println(result);
	}

	private static void typeExplorerBar(String shortcuts) {
		assert StringUtils.isNotBlank(shortcuts);
		assert StringUtils.contains(shortcuts, ",");

		String groupKey = StringUtils.substringBefore(shortcuts, ",");
		String itemKey = StringUtils.substringAfter(shortcuts, ",");

		IgExplorerBar explorerBar = session.findThirdPartyComponent(IgExplorerBar.class);
		assert explorerBar != null;
		WebElement explorerBarElement = explorerBar.getComponent().getElement();
		assert explorerBarElement != null;

		// Actions actions = new Actions(session.driver).moveToElement(explorerBarElement, 5, 5).click();
		// actions.perform();
		session.driver.executeScript("shortcut: <[{" + groupKey + itemKey + "}]>", explorerBarElement);
	}

	private static void clickIcon(String label) {
		assert StringUtils.isNotBlank(label);

		IgRibbon ribbon = session.findThirdPartyComponent(IgRibbon.class);
		if (ribbon == null) {
			System.err.println("Unable to find ribbon element");
		} else {
			DesktopElement app = session.getApp();
			new Actions(app.getDriver()).moveToElement(app.getElement()).perform();
			StepResult result = ribbon.click(label);
			System.out.println();
			System.out.println(result);
		}
	}

	private static void clickMenu(String menu) {
		assert StringUtils.isNotBlank(menu);

		DesktopElement app = session.getApp();
		DesktopElement menuBarElement = app.getComponent(APP_MENUBAR);
		assert menuBarElement != null;
		assert menuBarElement instanceof DesktopMenuBar;

		new Actions(app.getDriver()).moveToElement(app.getElement()).perform();

		DesktopMenuBar menuBar = (DesktopMenuBar) menuBarElement;
		System.out.println(menuBar.click(menu));

		System.out.println("done clicking " + menu);
	}

	private static void click(String name) {
		if (currentContainer == null) {
			System.err.println("no form loaded");
			return;
		}

		WebElement element = currentContainer.getElement(name);
		if (element == null) {
			System.err.println("No element found via label " + name);
		} else {
			System.out.println("Element labeled as " + name + ": clicking...");
			element.click();
		}
	}

	private static void type(String nameAndText) {
		assert StringUtils.isNotBlank(nameAndText);

		if (currentContainer == null) {
			System.err.println("no form loaded");
			return;
		}

		String[] parts = StringUtils.split(nameAndText, ",");
		String name = treatQuoteString(StringUtils.trim(parts[0]));
		String text = treatQuoteString(StringUtils.trim(parts[1]));
		DesktopElement component = currentContainer.getComponent(name);
		if (component == null) {
			System.err.println("No element found via label " + name);
			return;
		}

		System.out.println("Element labeled as " + name + ": typing " + text);

		ElementType elementType = component.getElementType();
		if (elementType.isCombo()) {
			System.out.println("component.select(text) = " + component.select(text));
			return;
		}

		if (elementType == Textbox || elementType == TextArea) {
			System.out.println(component.typeTextComponent(false, text));
			return;
		}

		component.type(text);
	}

	private static void typeAppend(String nameAndText) {
		assert StringUtils.isNotBlank(nameAndText);

		if (currentContainer == null) {
			System.err.println("no form loaded");
			return;
		}

		String[] parts = StringUtils.split(nameAndText, ",");
		String name = treatQuoteString(StringUtils.trim(parts[0]));
		String text = treatQuoteString(StringUtils.trim(parts[1]));
		DesktopElement component = currentContainer.getComponent(name);
		if (component == null) {
			System.err.println("No element found via label " + name);
			return;
		}

		System.out.println("Element labeled as " + name + ": typing " + text);

		ElementType elementType = component.getElementType();
		if (elementType == Textbox || elementType == TextArea) {
			System.out.println(component.typeTextComponent(true, text));
		} else {
			System.err.println("typeAppend is only supported on Textbox or TextArea");
		}
	}

	private static void clear(String name) {
		assert StringUtils.isNotBlank(name);

		if (currentContainer == null) {
			System.err.println("no form loaded");
			return;
		}

		DesktopElement component = currentContainer.getComponent(name);
		if (component == null) {
			System.err.println("No element found via label " + name);
			return;
		}

		System.out.println(component.clear());
	}

	private static void getText(String name) {

		if (currentContainer == null) {
			System.err.println("no form loaded");
			return;
		}

		DesktopElement component = currentContainer.getComponent(name);
		if (component == null) {
			System.err.println("No element found via label " + name);
			return;
		}

		System.out.println("Element labeled as " + name + " has text: " + component.getText());
	}

	private static void select(String nameAndValue) {
		assert StringUtils.isNotBlank(nameAndValue);

		if (currentContainer == null) {
			System.err.println("no form loaded");
			return;
		}

		String[] parts = StringUtils.split(nameAndValue, ",");
		String name = treatQuoteString(StringUtils.trim(parts[0]));
		String value = treatQuoteString(StringUtils.trim(parts[1]));
		DesktopElement component = currentContainer.getComponent(name);
		if (component == null) {
			System.err.println("No element found via label " + name);
			return;
		}

		ElementType elementType = component.getElementType();
		boolean watchForTypeChange = elementType == SingleSelectComboNotEditable;

		System.out.println("component.select(value) = " + component.select(value));

		if (watchForTypeChange && component.getElementType() != SingleSelectComboNotEditable) {
			System.out.println("component type updated to " + component.getElementType());
			// cache file ALWAYS follows ${directory of application.json}/${appId}.[commons | ${form}].json
			File cacheFile = DesktopSession.getCacheFile(session.getConfig(), currentContainerName);
			if (cacheFile != null) {
				try {
					DesktopElement container = currentContainer;
					String cache = GSON2.toJson(container, DesktopElement.class);
					FileUtils.writeStringToFile(cacheFile, cache, "UTF-8");
					System.out.println("cache updated at " + cacheFile);
				} catch (Exception e) {
					ConsoleUtils.error("Unable to update container metadata to " + cacheFile + ": " + e.getMessage());
				}
			}
		}
	}

	private static void clearCombo(String name) {
		assert StringUtils.isNotBlank(name);

		if (currentContainer == null) {
			System.err.println("no form loaded");
			return;
		}

		DesktopElement component = currentContainer.getComponent(name);
		if (component == null) {
			System.err.println("No element found via label " + name);
			return;
		}

		System.out.println("component.clearCombo() = " + component.clearCombo());
	}

	private static void isSelected(String name) {
		if (currentContainer == null) {
			System.err.println("no form loaded");
			return;
		}

		DesktopElement component = currentContainer.getComponent(name);
		if (component == null) {
			System.err.println("No element found via label " + name);
			return;
		}

		System.out.println("Element labeled as " + name + " is selected: " + component.isSelected());
	}

	private static void useList(String name) {
		assert StringUtils.isNotBlank(name);

		if (currentContainer == null) {
			System.err.println("no form loaded");
			return;
		}

		DesktopElement component = currentContainer.getComponent(name);
		if (component == null) {
			System.err.println("No element found via label " + name);
			return;
		}

		if (!(component instanceof DesktopList)) {
			System.err.println("Element not of type " + DesktopList.class.getSimpleName() + ", but " +
			                   component.getClass().getDeclaringClass().getSimpleName());
			return;
		}

		Date startTime = new Date();

		list = (DesktopList) component;
		list.inspect();

		System.out.println(StringUtils.rightPad("header count", DEF_OUTPUT_LABEL_WIDTH) + list.getColumnCount());
		System.out.println(StringUtils.rightPad("headers", DEF_OUTPUT_LABEL_WIDTH) + list.getHeaders());
		System.out.println(StringUtils.rightPad("row count", DEF_OUTPUT_LABEL_WIDTH) + list.getRowCount());

		Date endTime = new Date();
		long timeSpent = ((endTime.getTime() - startTime.getTime()));
		System.out.println(asOutputLabel("Start time") + startTime);
		System.out.println(asOutputLabel("End time") + endTime);
		System.out.println(asOutputLabel("Elapsed time (sec)") + Math.round((double) timeSpent / 1000));
	}

	private static void clickList(String index) {
		assert StringUtils.isNotBlank(index);
		int rowIndex = NumberUtils.toInt(index);

		if (list == null) {
			throw new IllegalArgumentException("ERROR: no List object found. Make sure to run useList() first");
		}

		WebElement row = list.getDataElement(rowIndex);
		assert row != null;

		row.click();
		System.out.println("done clicking on row " + index + " of List ");
	}

	private static void clickFirstMatchedList(String contains) {
		assert StringUtils.isNotBlank(contains);

		if (list == null) {
			throw new IllegalArgumentException("ERROR: no List object found. Make sure to run useList() first");
		}

		int rowIndex = list.findFirstMatchedRow(contains);
		if (rowIndex == UNDEFINED) {
			System.err.println("No list row found with text '" + contains + "'");
			return;
		}

		WebElement row = list.getDataElement(rowIndex);
		if (row == null) {
			System.err.println("Unable to click list: Null row element");
			return;
		}

		System.out.println("row = " + printDetails(row));

		boolean listContainsScrollbar = list.getVerticalScrollBar() != null;
		System.out.println(listContainsScrollbar ? "list has vertical scrollbar" : "");
		if (listContainsScrollbar) {
			WiniumDriver driver = list.getDriver();
			WebElement listElement = list.getElement();

			// click first shown option
			new Actions(driver).moveToElement(listElement, 5, 5).click().perform();

			List<String> shortcutSequence = new ArrayList<>();
			shortcutSequence.add("HOME");

			for (int i = 0; i < list.getRowCount(); i++) {
				WebElement elem = list.getDataElement(i);
				if (elem == row) { break; }
				shortcutSequence.add("DOWN");
			}

			driver.executeScript(toShortcuts(shortcutSequence.toArray(new String[shortcutSequence.size()])),
			                     listElement);

		} else {
			row.click();
		}

		System.out.println("done clicking on row " + contains + " of List");
	}

	private static void clickTextPane(String nameAndCriteria) throws IOException {
		assert StringUtils.isNotBlank(nameAndCriteria);

		if (currentContainer == null) {
			System.err.println("no form loaded");
			return;
		}

		String name = treatQuoteString(StringUtils.trim(StringUtils.substringBefore(nameAndCriteria, ",")));
		String criteria = treatQuoteString(StringUtils.trim(StringUtils.substringAfter(nameAndCriteria, ",")));
		Map<String, String> critieriaMap = TextUtils.toMap(criteria, ",", "=");

		DesktopElement component = currentContainer.getComponent(name);
		if (component == null) {
			System.err.println("No element found via label " + name);
			return;
		}

		ElementType elementType = component.getElementType();
		if (elementType != TextPane) {
			System.err.println("Element found, but not of expected type: " + elementType);
			return;
		}

		System.out.println(component.clickMatchedTextPane(critieriaMap));
	}

	private static void findTextPane(String nameAndCriteria) throws IOException {
		assert StringUtils.isNotBlank(nameAndCriteria);

		if (currentContainer == null) {
			System.err.println("no form loaded");
			return;
		}

		String name = treatQuoteString(StringUtils.trim(StringUtils.substringBefore(nameAndCriteria, ",")));
		String criteria = treatQuoteString(StringUtils.trim(StringUtils.substringAfter(nameAndCriteria, ",")));
		Map<String, String> critieriaMap = TextUtils.toMap(criteria, ",", "=");

		DesktopElement component = currentContainer.getComponent(name);
		if (component == null) {
			System.err.println("No element found via label " + name);
			return;
		}

		ElementType elementType = component.getElementType();
		if (elementType != TextPane) {
			System.err.println("Element found, but not of expected type: " + elementType);
			return;
		}

		component.findTextPaneMatches(critieriaMap).forEach(System.out::println);
	}

	private static void clickTab(String groupAndName) {
		assert StringUtils.isNotBlank(groupAndName);
		assert StringUtils.contains(groupAndName, ",");

		String[] params = StringUtils.split(groupAndName, ",");
		String group = params[0];
		assert StringUtils.isNotBlank(group);

		String tabName = params[1];
		assert StringUtils.isNotBlank(tabName);

		DesktopElement component = session.findContainer(group, session.getApp());
		assert component != null;
		assert (component instanceof DesktopTabGroup);

		DesktopTabGroup tabGroup = (DesktopTabGroup) component;
		System.out.println(tabGroup.clickTab(tabName));

		System.out.println("Tab '" + tabName + "' of Tab Group '" + group + "' clicked");
	}

	private static void useTable(String name) {
		assert StringUtils.isNotBlank(name);

		if (currentContainer == null) {
			System.err.println("no form loaded");
			return;
		}

		DesktopElement component = currentContainer.getComponent(name);
		if (component == null) {
			System.err.println("No element found via label " + name);
			return;
		}
		if (!(component instanceof DesktopTable)) {
			System.err.println("Element not of type " + DesktopTable.class.getSimpleName() +
			                   ", but " + component.getClass().getDeclaringClass().getSimpleName());
			return;
		}

		Date startTime = new Date();

		table = (DesktopTable) component;
		table.scanStructure();

		System.out.println(StringUtils.rightPad("header count", DEF_OUTPUT_LABEL_WIDTH) + table.getColumnCount());
		System.out.println(StringUtils.rightPad("headers", DEF_OUTPUT_LABEL_WIDTH) + table.getHeaders());
		System.out.println(StringUtils.rightPad("row count", DEF_OUTPUT_LABEL_WIDTH) +
		                   table.scanStructure().getRowCount());

		Date endTime = new Date();
		long timeSpent = ((endTime.getTime() - startTime.getTime()));
		System.out.println(asOutputLabel("Start time") + startTime);
		System.out.println(asOutputLabel("End time") + endTime);
		System.out.println(asOutputLabel("Elapsed time (sec)") + Math.round((double) timeSpent / 1000));
	}

	private static void getTableRowsAll() {

		if (table == null) {
			throw new IllegalArgumentException("ERROR: no Table object found. Make sure to run useTable() first");
		}

		Date startTime = new Date();

		table.scanAllRows();

		for (String header : table.getHeaders()) { System.out.print(StringUtils.rightPad(header, 12) + " "); }
		System.out.println();
		System.out.println(StringUtils.repeat("-", (15 * table.getColumnCount() + 1)));

		int totalCellCount = 0;
		for (List<String> row : table.fetchAll().getRows()) {
			if (row == null) { continue; }
			for (String cell : row) {
				System.out.print(StringUtils.rightPad(cell, 12) + " ");
				totalCellCount++;
			}
			System.out.println();
		}

		Date endTime = new Date();
		long timeSpent = ((endTime.getTime() - startTime.getTime()));
		String avgScanTimeMs = AVG_FORMAT.format((double) timeSpent / (double) totalCellCount);

		System.out.println(asOutputLabel("Start time") + startTime);
		System.out.println(asOutputLabel("End time") + endTime);
		System.out.println(asOutputLabel("Elapsed time (sec)") + Math.round((double) timeSpent / 1000));
		System.out.println(asOutputLabel("# cell fetched") + totalCellCount);
		System.out.println(asOutputLabel("Avg cell fetch time (ms)") + avgScanTimeMs);
	}

	private static void getTableRows(String rows) {
		assert StringUtils.isNotBlank(rows);

		if (table == null) {
			throw new IllegalArgumentException("ERROR: no Table object found. Make sure to run useTable() first");
		}

		Date startTime = new Date();

		String[] parts = StringUtils.split(rows, ",");
		int startRow = NumberUtils.toInt(parts[0]);
		int endRow = NumberUtils.toInt(parts[1]);
		// table.scanRows(startRow, endRow);

		for (String header : table.getHeaders()) { System.out.print(StringUtils.rightPad(header, 12) + " "); }
		System.out.println();
		System.out.println(StringUtils.repeat("-", (15 * table.getColumnCount() + 1)));

		int totalCellCount = 0;
		for (List<String> row : table.fetch(startRow, endRow).getRows()) {
			if (row == null) { continue; }
			for (String cell : row) {
				System.out.print(StringUtils.rightPad(cell, 12) + " ");
				totalCellCount++;
			}
			System.out.println();
		}

		Date endTime = new Date();
		long timeSpent = ((endTime.getTime() - startTime.getTime()));
		String avgScanTimeMs = AVG_FORMAT.format((double) timeSpent / (double) totalCellCount);

		System.out.println(asOutputLabel("Start time") + startTime);
		System.out.println(asOutputLabel("End time") + endTime);
		System.out.println(asOutputLabel("Elapsed time (sec)") + Math.round((double) timeSpent / 1000));
		System.out.println(asOutputLabel("# cell fetched") + totalCellCount);
		System.out.println(asOutputLabel("Avg cell fetch time (ms)") + avgScanTimeMs);
	}

	private static void clickTableRow(String row) {
		assert StringUtils.isNotBlank(row);

		if (table == null) {
			throw new IllegalArgumentException("ERROR: no Table object found. Make sure to run useTable() first");
		}

		Date startTime = new Date();

		int targetRow = NumberUtils.toInt(row);
		table.clickRow(targetRow);

		Date endTime = new Date();
		long timeSpent = ((endTime.getTime() - startTime.getTime()));

		System.out.println(asOutputLabel("Start time") + startTime);
		System.out.println(asOutputLabel("End time") + endTime);
		System.out.println(asOutputLabel("Elapsed time (sec)") + Math.round((double) timeSpent / 1000));
	}

	private static void clickTableCell(String rowAndColumn) {
		assert StringUtils.isNotBlank(rowAndColumn);

		if (table == null) {
			throw new IllegalArgumentException("ERROR: no Table object found. Make sure to run useTable() first");
		}

		Date startTime = new Date();

		String[] data = StringUtils.split(rowAndColumn, ",");
		int targetRow = NumberUtils.toInt(data[0]);
		String targetColumn = data[1];
		if (!table.clickCell(targetRow, targetColumn)) {
			System.err.println("Unable to click on row " + targetRow + " column '" + targetColumn + "'");
		}

		Date endTime = new Date();
		long timeSpent = ((endTime.getTime() - startTime.getTime()));

		System.out.println(asOutputLabel("Start time") + startTime);
		System.out.println(asOutputLabel("End time") + endTime);
		System.out.println(asOutputLabel("Elapsed time (sec)") + Math.round((double) timeSpent / 1000));
	}

	private static void readTable() {
		if (table == null) {
			throw new IllegalArgumentException("ERROR: no Table object found. Make sure to run useTable() first");
		}

		TableData outcome = table.fetchAll();
		System.out.println("Table data: " + outcome.toString());
	}

	private static void assertTableCell(String rowColumnContains) {
		assert StringUtils.isNotBlank(rowColumnContains);
		assert StringUtils.contains(rowColumnContains, ",");
		String[] args = rowColumnContains.split(",");
		int row = Integer.parseInt(args[0]);

		String column = args[1];
		String contains = args[2];

		System.out.println("Row: " + row + "  Column: " + column + "  Contains: " + contains);

		if (table == null) {
			throw new IllegalArgumentException("ERROR: no Table object found. Make sure to run useTable() first");
		}

		List<TableRow> oneRow = table.fetch(row, row).getData();
		if (oneRow.isEmpty()) { System.out.println(("No row " + row + " for Table")); }

		System.out.println("Fetch the row data: " + oneRow.get(0));
		Assert.assertEquals(contains, oneRow.get(0).get(column));
		System.out.println("validated text '" + oneRow + "' contains '" + contains + "'");
	}

	private static void readTable(String beginAndEnd) {
		assert StringUtils.isNotBlank(beginAndEnd);
		assert StringUtils.contains(beginAndEnd, ",");
		int begin = Integer.parseInt(StringUtils.substringBefore(beginAndEnd, ","));
		int end = Integer.parseInt(StringUtils.substringAfter(beginAndEnd, ","));

		if (table == null) {
			throw new IllegalArgumentException("ERROR: no Table object found. Make sure to run useTable() first");
		}

		TableData outcome = table.fetch(begin, end);
		System.out.println("Table data: " + outcome.toString());

	}

	private static void editTableCells(String rowAndNameValuePairs) {
		assert StringUtils.isNotBlank(rowAndNameValuePairs);
		assert StringUtils.contains(rowAndNameValuePairs, ",");
		assert StringUtils.contains(rowAndNameValuePairs, "=");
		if (table == null) {
			throw new IllegalArgumentException("ERROR: no Table object found. Make sure to run useTable() first");
		}

		String row = StringUtils.substringBefore(rowAndNameValuePairs, ",");
		System.out.println("row = " + row);
		assert StringUtils.equals(row, "*") || NumberUtils.isDigits(row);

		String nameValuePairs = StringUtils.substringAfter(rowAndNameValuePairs, ",");
		Map<String, String> nameValues = TextUtils.toMap(nameValuePairs, ",", "=");
		System.out.println("nameValues = " + nameValues);
		assert MapUtils.isNotEmpty(nameValues);

		StepResult outcome =
			table.editCells(StringUtils.equals(row, "*") ? MAX_VALUE : NumberUtils.toInt(row), nameValues);
		System.out.println("\nSUCCESS?\t" + outcome.isSuccess());
		List<String> output = TextUtils.toList(outcome.getMessage(), "\n", true);
		if (CollectionUtils.isEmpty(output)) {
			System.out.println("EMPTY LIST returned");
		} else {
			output.forEach(System.out::println);
		}
	}

	private static void useHierTable(String name) {
		assert StringUtils.isNotBlank(name);

		if (currentContainer == null) {
			System.err.println("no form loaded");
			return;
		}

		DesktopElement component = currentContainer.getComponent(name);
		if (component == null) {
			System.err.println("No element found via label " + name);
			return;
		}
		if (!(component instanceof DesktopHierTable)) {
			System.err.println("Element not of type " + DesktopHierTable.class.getSimpleName() +
			                   ", but " + component.getClass().getDeclaringClass().getSimpleName());
			return;
		}

		Date startTime = new Date();

		hiertable = (DesktopHierTable) component;
		hiertable.scanStructure();
		// hiertable.collapseFromTopLevel();

		System.out.println(StringUtils.rightPad("header count", DEF_OUTPUT_LABEL_WIDTH) + hiertable.getColumnCount());
		System.out.println(StringUtils.rightPad("headers", DEF_OUTPUT_LABEL_WIDTH) + hiertable.getHeaders());

		Date endTime = new Date();
		long timeSpent = ((endTime.getTime() - startTime.getTime()));
		System.out.println(asOutputLabel("Start time") + startTime);
		System.out.println(asOutputLabel("End time") + endTime);
		System.out.println(asOutputLabel("Elapsed time (sec)") + Math.round((double) timeSpent / 1000));
	}

	private static void collapseAll() {
		if (hiertable == null) {
			throw new IllegalArgumentException(
				"ERROR: no Hier Table object found. Make sure to run useHierTable() first");
		}

		StepResult outcome = hiertable.collapseAll();
		System.out.println("\nSUCCESS?\t" + outcome.isSuccess());
		List<String> output = TextUtils.toList(outcome.getMessage(), "\n", true);
		if (CollectionUtils.isEmpty(output)) {
			System.out.println("EMPTY LIST returned");
		} else {
			output.forEach(System.out::println);
		}
	}

	private static void rowCount() {
		if (table == null) {
			throw new IllegalArgumentException("ERROR: no Table object found. Make sure to run useTable() first");
		}

		// table.getTableRowCount();
		int count = table.getTableRowCount();
		System.out.println("Row count " + count);
		StepResult outcome = StepResult.success("Row count " + count);
		System.out.println("\nSUCCESS?\t" + outcome.isSuccess());
		List<String> output = TextUtils.toList(outcome.getMessage(), "\n", true);
		if (CollectionUtils.isEmpty(output)) {
			System.out.println("EMPTY LIST returned");
		} else {
			output.forEach(System.out::println);
		}
	}

	// private static void collapseHierTable() {
	// 	if (hiertable == null) {
	// 		throw new IllegalArgumentException("ERROR: no Hierarchical Table object found. " +
	// 		                                   "Make sure to run useHierTable() first");
	// 	}
	//
	// 	Date startTime = new Date();
	//
	// 	hiertable.collapseFromTopLevel();
	//
	// 	Date endTime = new Date();
	// 	long timeSpent = ((endTime.getTime() - startTime.getTime()));
	//
	// 	System.out.println(asOutputLabel("Start time") + startTime);
	// 	System.out.println(asOutputLabel("End time") + endTime);
	// 	System.out.println(asOutputLabel("Elapsed time (sec)") + Math.round((double) timeSpent / 1000));
	// }

	private static void getHierRow(String matchParams) {
		assert StringUtils.contains(matchParams, ",");
		// String matchColumn = StringUtils.substringBefore(matchParams, ",");
		List<String> matchData = TextUtils.toList(StringUtils.substringAfter(matchParams, ","), ",", true);

		// System.out.println("Parameters: "+matchColumn+"   matchData: "+matchData);
		if (hiertable == null) {
			throw new IllegalArgumentException("ERROR: no Hierarchical Table object found. " +
			                                   "Make sure to run useHierTable() first");
		}
		// assert hiertable.containsHeader(matchColumn);

		Date startTime = new Date();

		System.out.println();
		System.out.println(hiertable.getHierRow(matchData));

		Date endTime = new Date();
		long timeSpent = ((endTime.getTime() - startTime.getTime()));

		System.out.println();
		System.out.println(asOutputLabel("Start time") + startTime);
		System.out.println(asOutputLabel("End time") + endTime);
		System.out.println(asOutputLabel("Elapsed time (sec)") + Math.round((double) timeSpent / 1000));
	}

	private static void editHierCells(String matchesAndNameValues) {
		assert StringUtils.contains(matchesAndNameValues, ",");
		assert StringUtils.contains(matchesAndNameValues, "=");

		if (hiertable == null) {
			throw new IllegalArgumentException("ERROR: no Hierarchical Table object found. " +
			                                   "Make sure to run useHierTable() first");
		}

		String matchColumn = StringUtils.substringBefore(matchesAndNameValues, ",");
		matchesAndNameValues = StringUtils.substringAfter(matchesAndNameValues, matchColumn + ",");
		assert hiertable.containsHeader(matchColumn);

		String matchData = StringUtils.substringBefore(matchesAndNameValues, " ");
		List<String> matchBy = TextUtils.toList(matchData, ",", true);
		matchesAndNameValues = StringUtils.trim(StringUtils.substringAfter(matchesAndNameValues, matchData + " "));

		Map<String, String> nameValues = TextUtils.toMap(matchesAndNameValues, ",", "=");
		System.out.println("nameValues = " + nameValues);
		assert MapUtils.isNotEmpty(nameValues);

		Map<String, String> outcome = hiertable.editHierCell(matchBy, nameValues);
		// todo: what are we doing with `outcome`?
		// System.out.println("\nSUCCESS?\t" + outcome.isSuccess());
		/*List<String> output = TextUtils.toList(outcome.getMessage(), "\n", true);
		if (CollectionUtils.isEmpty(output)) {
			System.out.println("EMPTY LIST returned");
		} else {
			output.forEach(System.out::println);
		}*/
	}

	private static void getHierCellData(String matchesAndColumn) {
		assert StringUtils.contains(matchesAndColumn, ",");
		assert StringUtils.contains(matchesAndColumn, "=");

		if (hiertable == null) {
			throw new IllegalArgumentException("ERROR: no Hierarchical Table object found. " +
			                                   "Make sure to run useHierTable() first");
		}

		// String matchColumn = StringUtils.substringBefore(matchesAndColumn, ",");
		// matchesAndColumn = StringUtils.substringAfter(matchesAndColumn, matchColumn + ",");
		// assert hiertable.containsHeader(matchColumn);

		String column = StringUtils.substringAfterLast(matchesAndColumn, ",");
		// matchesAndColumn = StringUtils.substringBefore(matchColumn, "," + column);

		String matchData = StringUtils.substringBetween(matchesAndColumn, ",");
		List<String> matchBy = TextUtils.toList(StringUtils.substringAfter(matchData, ","), ",", true);

		System.out.println(hiertable.getHierCellChildData(matchBy, column));
	}

	private static void assertHierCell(String matchesColumnAndExpected) {
		assert StringUtils.contains(matchesColumnAndExpected, ",");
		assert StringUtils.contains(matchesColumnAndExpected, "=");

		if (hiertable == null) {
			throw new IllegalArgumentException("ERROR: no Hierarchical Table object found. " +
			                                   "Make sure to run useHierTable() first");
		}

		// String matchColumn = StringUtils.substringBefore(matchesColumnAndExpected, ",");
		// matchesColumnAndExpected = StringUtils.substringAfter(matchesColumnAndExpected, matchColumn + ",");
		// assert hiertable.containsHeader(matchColumn);

		String expected = StringUtils.substringAfterLast(matchesColumnAndExpected, ",");
		// matchesColumnAndExpected = StringUtils.substringBefore(matchColumn, "," + expected);

		String column = StringUtils.substringAfterLast(matchesColumnAndExpected, ",");
		// matchesColumnAndExpected = StringUtils.substringBefore(matchColumn, "," + expected);

		String matchData = StringUtils.substringBetween(matchesColumnAndExpected, ",");
		List<String> matchBy = TextUtils.toList(StringUtils.substringAfter(matchData, ","), ",", true);

		// todo: might not work... type differences
		Assert.assertEquals(hiertable.getHierCellChildData(matchBy, column), expected);
	}

	private static void typeHierTable(String shortcuts) {
		assert StringUtils.isNotBlank(shortcuts);

		if (hiertable == null) {
			throw new IllegalArgumentException("ERROR: no Hierarchical Table object found. " +
			                                   "Make sure to run useHierTable() first");
		}

		String[] keys = StringUtils.split(shortcuts, ",");
		WebElement element = hiertable.getElement();
		element.click();

		for (String keystroke : keys) {
			hiertable.getDriver().executeScript("shortcut: " + StringUtils.trim(keystroke), element);
		}
	}

	private static void clearModalDialog(String button) {
		assert StringUtils.isNotBlank(button);
		assert session != null;

		String dialogText = session.clearModalDialog(button);
		System.out.println("done clearing dialog box with content '" + dialogText + "'");
	}

	private static void maximize() {
		assert session != null;
		assert session.getApp() != null;

		if (session.getApp().getElement() == null) { session.getApp().refreshElement(); }
		session.driver.executeScript("automation: maximize", session.getApp().getElement());
	}

	private static void minimize() {
		assert session != null;
		assert session.getApp() != null;

		if (session.getApp().getElement() == null) { session.getApp().refreshElement(); }
		session.driver.executeScript("automation: minimize", session.getApp().getElement());
	}

	private static void resize(String widthAndHeight) {
		assert session != null;
		assert session.getApp() != null;
		assert StringUtils.isNotBlank(widthAndHeight);

		int width = NumberUtils.toInt(StringUtils.substringBefore(widthAndHeight, "x"));
		int height = NumberUtils.toInt(StringUtils.substringAfter(widthAndHeight, "x"));
		if (session.getApp().getElement() == null) { session.getApp().refreshElement(); }
		session.driver.executeScript("automation: resize", session.getApp().getElement(), width + "X" + height);
	}

	private static String addContainerPrefix(String name) {
		if (StringUtils.isBlank(currentContainerName)) {
			throw new IllegalArgumentException("no container name found; or no useForm() command yet invoked.");
		}

		return currentContainerName + " :: " + name;
	}

	private static String asOutputLabel(String label) {
		return StringUtils.rightPad(label, DEF_OUTPUT_LABEL_WIDTH, " ");
	}

	private static void reportScanStats(Date startTime, int xpathCount) {
		Date endTime = new Date();
		long timeSpent = ((endTime.getTime() - startTime.getTime()));
		String avgScanTimeMs = AVG_FORMAT.format((double) timeSpent / (double) xpathCount);

		System.out.println(asOutputLabel("Start time") + startTime);
		System.out.println(asOutputLabel("End time") + endTime);
		System.out.println(asOutputLabel("Elapsed time (sec)") + Math.round((double) timeSpent / 1000));
		System.out.println(asOutputLabel("# xpath resolved") + xpathCount);
		System.out.println(asOutputLabel("Avg element scan time (ms)") + avgScanTimeMs);
	}

	private static String treatQuoteString(String arg) {
		if (!TextUtils.isBetween(arg, "\"", "\"")) { return arg; }
		return StringUtils.substringBetween(arg, "\"", "\"");
	}
}
