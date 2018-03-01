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
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Action;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.remote.RemoteWebElement;
import org.openqa.selenium.winium.WiniumDriver;

import org.nexial.core.utils.WebDriverUtils;

import static java.io.File.separator;
import static org.openqa.selenium.OutputType.FILE;

public class WiniumPlayground {
	private static final String OPT1 = "1 ";
	private static final String OPT2 = "2 ";
	private static final String OPT3 = "3 ";
	private static final String OPT4 = "4 ";
	private static final String OPT5 = "5 ";
	private static final String OPT6 = "6 ";
	private static final String OPT7 = "7 ";
	private static final String OPT8 = "8 ";
	private static final String OPT9 = "9 ";
	private static final String OPT10 = "10 ";
	private static final String OPT11 = "11 ";
	private static final String OPT12 = "12 ";
	private static final String OPT13 = "13 ";
	private static final String OPT14 = "14 ";
	private static final String OPT15 = "15 ";
	private static final String OPT16 = "16 ";
	private static final String OPT17 = "17 ";
	private static final String OPT18 = "18 ";
	private static final String OPT19 = "19 ";
	private static final String OPT20 = "20 ";
	private static final String OPT21 = "21 ";
	private static final String OPT22 = "22 ";
	private static final String OPT23 = "23 ";

	private static final String XPATH_NAVIGATION_PANE =
		"/*[@AutomationId='PayrollApplicationClient']/*[@AutomationId='ultraExplorerBar_Main']";

	private static final NumberFormat SEC_FORMATTER = new DecimalFormat("#0.000");

	private static WiniumDriver driver;
	private static WebElement elem;

	public static void main(String[] args) throws Exception {
		if (ArrayUtils.isEmpty(args)) {
			System.err.println(
				"Specify either port to join existing Winium Service or specific AUT path to start new session");
			System.exit(-1);
		}

		if (NumberUtils.isDigits(args[0])) {
			WiniumPlayground.driver = WiniumUtils.joinCurrentWiniumSession(NumberUtils.toInt(args[0]));
		} else {
			String autInput = StringUtils.join(args, " ");
			WiniumPlayground.driver = WiniumUtils.newWiniumInstance(autInput);
		}

		Console console = System.console();
		System.out.println("Ready.\n");

		while (true) {
			String opt = console.readLine(OPT1 + "elemByClassName(name)\n" +
			                              OPT2 + "elemByName(name)\n" +
			                              OPT3 + "elementByXPath(xpath)\n" +
			                              OPT4 + "elementById(id)\n" +
			                              OPT5 + "findChildElements(xpath)\n" +
			                              OPT6 + "attribute(name)\n" +
			                              OPT7 + "sendKeys(text)\n" +
			                              OPT8 + "click()\n" +
			                              // OPT9 + "submit()\n" +
			                              OPT9 + "toggleTreeTab()\n" +
			                              OPT10 + "readText()\n" +
			                              OPT11 + "isEnabled()\n" +
			                              OPT12 + "screenshot(name)\n" +
			                              OPT13 + "typeOnActiveElement(text)\n" +
			                              OPT14 + "clickByXY(x,y)\n" +
			                              OPT15 + "toggleTreeExpansion()\n" +
			                              OPT16 + "setValue(value)\n" +
			                              OPT17 + "clickBrc()\n" +
			                              OPT18 + "collapseTreeNode()\n" +
			                              OPT19 + "expandTreeNode()\n" +
			                              OPT20 + "collapseTreeNodeByOffset(x,y)\n" +
			                              OPT21 + "expandTreeNodeByOffset(x,y)\n" +
			                              OPT22 + "collapseTreeLastNode()\n" +
			                              OPT23 + "clear()\n" +
			                              "Q quit\n" +
			                              "> ");

			if (StringUtils.equalsIgnoreCase(opt, "Q")) {
				// WiniumUtils.shutdownWinium(WiniumUtils.getWiniumService(), driver);
				driver.close();
				System.exit(0);
			}

			System.out.println("\nProcessing " + opt);

			StopWatch tickTock = new StopWatch();
			tickTock.start();

			try {
				if (StringUtils.startsWith(opt, OPT1)) { elemByClassName(StringUtils.substringAfter(opt, OPT1)); }
				if (StringUtils.startsWith(opt, OPT2)) { elemByName(StringUtils.substringAfter(opt, OPT2)); }
				if (StringUtils.startsWith(opt, OPT3)) { elemByXPath(StringUtils.substringAfter(opt, OPT3)); }
				if (StringUtils.startsWith(opt, OPT4)) { elemById(StringUtils.substringAfter(opt, OPT4)); }
				if (StringUtils.startsWith(opt, OPT5)) { findChildElements(StringUtils.substringAfter(opt, OPT5)); }
				if (StringUtils.startsWith(opt, OPT6)) { attribute(StringUtils.substringAfter(opt, OPT6)); }
				if (StringUtils.startsWith(opt, OPT7)) { sendKey(StringUtils.substringAfter(opt, OPT7)); }
				if (StringUtils.startsWith(opt, OPT8)) { click(); }
				//if (StringUtils.startsWith(opt, OPT9)) { submit(); }
				if (StringUtils.startsWith(opt, OPT9)) { toggleTreeTab(); }
				if (StringUtils.startsWith(opt, OPT10)) { readText(); }
				if (StringUtils.startsWith(opt, OPT11)) { isEnabled(); }
				if (StringUtils.startsWith(opt, OPT12)) { screenshot(StringUtils.substringAfter(opt, OPT12)); }
				if (StringUtils.startsWith(opt, OPT13)) { typeOnActiveElement(StringUtils.substringAfter(opt, OPT13)); }
				if (StringUtils.startsWith(opt, OPT14)) { clickByXY(StringUtils.substringAfter(opt, OPT14)); }
				if (StringUtils.startsWith(opt, OPT16)) { setValue(StringUtils.substringAfter(opt, OPT16)); }
				if (StringUtils.startsWith(opt, OPT17)) { clickBrc(); }
				if (StringUtils.startsWith(opt, OPT18)) { collapseTreeNode(); }
				if (StringUtils.startsWith(opt, OPT19)) { expandTreeNode(); }
				if (StringUtils.startsWith(opt, OPT20)) {
					collapseTreeNodeByOffset(StringUtils.substringAfter(opt, OPT20));
				}
				if (StringUtils.startsWith(opt, OPT21)) {
					expandTreeNodeByOffset(StringUtils.substringAfter(opt, OPT21));
				}
				if (StringUtils.startsWith(opt, OPT22)) { collapseTreeLastNode(); }
				if (StringUtils.startsWith(opt, OPT23)) { clear(); }
			} catch (Exception e) {
				e.printStackTrace();
			}

			tickTock.stop();
			System.out.println();
			System.out.println();
			System.out.println("Total Time: " + SEC_FORMATTER.format((double) tickTock.getTime() / 1000) + " seconds");
			System.out.println();
			System.out.println();
		}
	}

	private static void elemByClassName(String arg) {
		assert arg != null;
		arg = treatQuoteString(arg);
		if (StringUtils.isBlank(arg)) {
			elem = null;
		} else {
			elem = elem != null ? elem.findElement(By.className(arg)) : driver.findElementByClassName(arg);
		}
		System.out.println("element with class name " + arg + ": " + toString(elem));
	}

	private static void elemByName(String arg) {
		assert arg != null;
		arg = treatQuoteString(arg);
		if (StringUtils.isBlank(arg)) {
			elem = null;
		} else {
			elem = elem == null ? driver.findElementByName(arg) : elem.findElement(By.name(arg));
		}
		System.out.println("element with name " + arg + ": " + toString(elem));
	}

	private static void elemById(String arg) {
		assert arg != null;
		arg = treatQuoteString(arg);
		if (StringUtils.isBlank(arg)) {
			elem = null;
		} else {
			elem = elem == null ? driver.findElementById(arg) : elem.findElement(By.id(arg));
		}
		System.out.println("element with name " + arg + ": " + toString(elem));
	}

	private static void elemByXPath(String arg) {
		assert arg != null;
		arg = treatQuoteString(arg);
		if (StringUtils.isBlank(arg)) {
			elem = null;
		} else {
			elem = driver.findElementByXPath(arg);
			//WebDriverWait wait = new WebDriverWait(driver, 60);
			//WebElement waitFor = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(arg)));
			//System.out.println("waitFor = " + waitFor);
		}

		System.out.println("element with class name " + arg + ": " + toString(elem));
	}

	private static void findChildElements(String xpath) {
		assert elem != null;

		List<WebElement> children = elem.findElements(By.xpath(xpath));
		int numberOfChildren = CollectionUtils.size(children);
		System.out.println("Number of child elements: " + numberOfChildren);

		System.out.println("Child elements of element " + toString(elem) + " are ");
		if (CollectionUtils.isEmpty(children)) {
			System.out.println("NONE");
		} else {
			if (numberOfChildren <= 10) {
				for (WebElement child : children) { System.out.println(toString(child)); }
			}
		}
	}

	private static void sendKey(String arg) {
		assert elem != null;
		arg = treatQuoteString(arg);

		/*
		 * example:
		 * shortcut: <[{CTRL-C}]><[CTRL-A]><[CTRL-C]> - type CTRL-C and then copy the same to clipboard.
		 * shortcut: <[F2]> - press function key F2.
		 * shortcut: <[SHIFT-ESC]><[ALT-`]><[{SPACE SPACE}]><[CTRL-SHIFT-SPACE]> - press Shift-Escape key combination, press Alt-Tilde key combination, enter text SPACE SPACE, press Ctrl-Shift-Space key combination.
		 * shortcut: <[SHIFT-CTRL-ALT]> - press Shift-Ctrl-Alt key combination (probably nothing will happen to the target application).
		 * shortcut: <[CTRL-ALT-DEL]> - lock current screen.
		 * shortcut: <[CTRL-ALT-ab]> - ERROR; no such key combination.
		 * shortcut: <[CTRL-ALT--]> - press Ctrl-Alt-Dash key combination.
		 * shortcut: <[{hello world}]> - type hello world into target element.
	     */
		// elem.click();
		driver.executeScript("shortcut: " + arg, elem);

		//elem.clear();

		//elem.sendKeys(arg);
		//new Actions(driver).sendKeys(elem, TextUtils.toOneCharArray(arg)).perform();

		//Action action = WebDriverUtils.toSendKeyAction(driver, elem, arg);
		//if (action != null) { action.perform(); }

		System.out.println("send-key complete for element " + elem);
	}

	private static void attribute(String arg) {
		assert elem != null;
		arg = treatQuoteString(arg);
		String attrValue = elem.getAttribute(arg);
		System.out.println("element " + elem + " attribute " + arg + ":" + attrValue);
	}

	private static void click() {
		assert elem != null;
		elem.click();
		System.out.println("clicked element " + elem);
	}

	// private static void listComboOptions() {
	// 	assert elem != null;
	//
	// 	try {
	// 		String xpathInnerCombo = "*[@ControlType='ControlType.ComboBox']";
	// 		WebElement innerCombo = elem.findElement(By.xpath(xpathInnerCombo));
	// 		if (innerCombo == null) {
	// 			System.err.println("Unable to find inner combo element");
	// 			return;
	// 		}
	//
	// 		System.out.println("found inner combo, clicking it..");
	// 		innerCombo.click();
	//
	// 		String xpathInnerList = "*[@ControlType='ControlType.List']";
	// 		WebElement innerList = innerCombo.findElement(By.xpath(xpathInnerList));
	// 		if (innerList == null) {
	// 			System.err.println("Unable to find inner list element");
	// 			return;
	// 		}
	//
	// 		System.out.println("found inner list, retrieving options...");
	// 		List<WebElement> options = innerList.findElements(By.xpath("*"));
	// 		System.out.println("options = " + options);
	// 	} catch (Throwable e) {
	// 		e.printStackTrace();
	// 	}
	// }

	private static void clear() {
		elem.clear();
	}

	private static void toggleTreeTab() {
		WebElement treeTab = driver.findElementByXPath(XPATH_NAVIGATION_PANE);
		assert treeTab != null;

		// x, y, width, height
		String[] boundingRectangle = StringUtils.split(treeTab.getAttribute("BoundingRectangle"), ",");
		int width = NumberUtils.toInt(boundingRectangle[2]);
		if (width < 50) {
			System.out.println("Must already be collapsed");
		} else {
			System.out.println("Yet to collapse, doing it now");
		}

		new Actions(driver).moveToElement(treeTab, width - 10, 13).click().build().perform();
	}

	// private static void submit() {
	// 	assert elem != null;
	// 	elem.submit();
	// 	System.out.println("submit element " + elem);
	// }

	private static void readText() {
		assert elem != null;
		String text = elem.getText();
		System.out.println("element " + elem + " contains text " + text);
	}

	private static void isEnabled() {
		assert elem != null;
		System.out.println("Element " + elem + " is enabled? " + elem.isEnabled());
	}

	private static void screenshot(String name) {
		assert elem != null;
		File screenshot = driver.getScreenshotAs(FILE);
		String filename = SystemUtils.getJavaIoTmpDir() + separator + name + ".png";
		boolean moved = screenshot.renameTo(new File(filename));
		System.out.println("screen captured to " + filename + ": " + moved);
	}

	private static void typeOnActiveElement(String text) {
		assert StringUtils.isNotEmpty(text);
		text = treatQuoteString(text);

		WebElement target = elem;
		Action action = WebDriverUtils.toSendKeyAction(driver, target, text);
		if (action != null) {
			driver.switchTo().activeElement().click();
			action.perform();
		}

		System.out.println("send-key to active element: " + text);
	}

	private static void clickByXY(String args) {
		assert StringUtils.isNotBlank(args);
		assert elem != null;

		String[] xy = StringUtils.split(args, " ");
		Actions actions = new Actions(driver)
			                  .moveToElement(elem,
			                                 NumberUtils.toInt(StringUtils.trim(xy[0])),
			                                 NumberUtils.toInt(StringUtils.trim(xy[1])))
			                  .click();
		actions.perform();

		System.out.println("clicked on offset (" + args + ") from element " + toString(elem));
	}

	private static void expandTreeNode() {
		assert elem != null;
		elem.click();
		driver.executeScript("shortcut: <[HOME]><[CTRL-SPACE]><[RIGHT]>", elem);
	}

	private static void collapseTreeNode() {
		assert elem != null;
		elem.click();
		driver.executeScript("shortcut: <[HOME]><[CTRL-SPACE]><[LEFT]>", elem);
	}

	private static void expandTreeNodeByOffset(String args) {
		assert StringUtils.isNotBlank(args);
		assert elem != null;

		String[] xy = StringUtils.split(args, " ");
		Actions actions = new Actions(driver)
			                  .moveToElement(elem,
			                                 NumberUtils.toInt(StringUtils.trim(xy[0])),
			                                 NumberUtils.toInt(StringUtils.trim(xy[1])))
			                  .click();
		actions.perform();

		driver.executeScript("shortcut: <[HOME]><[CTRL-SPACE]><[RIGHT]>", elem);
	}

	private static void collapseTreeNodeByOffset(String args) {
		assert StringUtils.isNotBlank(args);
		assert elem != null;

		String[] xy = StringUtils.split(args, " ");
		Actions actions = new Actions(driver)
			                  .moveToElement(elem,
			                                 NumberUtils.toInt(StringUtils.trim(xy[0])),
			                                 NumberUtils.toInt(StringUtils.trim(xy[1])))
			                  .click();
		actions.perform();

		driver.executeScript("shortcut: <[HOME]><[CTRL-SPACE]><[LEFT]>", elem);
	}

	private static void collapseTreeLastNode() {
		assert elem != null;

		String keepGoingDown = StringUtils.repeat("<[DOWN]>", 2);
		driver.executeScript("shortcut: " + keepGoingDown + "<[HOME]><[CTRL-SPACE]><[LEFT]><[LEFT]><[HOME]>", elem);
	}

	private static void setValue(String value) {
		assert elem != null;
		driver.executeScript("automation: ValuePattern.SetValue", elem, value);
	}

	private static void clickBrc() {
		assert elem != null;
		driver.executeScript("input: brc_click", elem);
	}

	private static String treatQuoteString(String arg) {
		if (StringUtils.startsWith(arg, "\"") && StringUtils.endsWith(arg, "\"")) {
			arg = StringUtils.substringBetween(arg, "\"", "\"");
		}
		return arg;
	}

	private static String toString(WebElement elem) {
		if (elem == null) { return "(null)"; }
		StringBuilder buffer = new StringBuilder("\n");
		buffer.append(elem.toString())
		      .append("\n Java Class=").append(elem.getClass())
		      .append("\n AutomationId=").append(elem.getAttribute("AutomationId"))
		      .append("\n Name=").append(elem.getAttribute("Name"))
		      .append("\n ClassName=").append(elem.getAttribute("ClassName"))
		      .append("\n ControlType=").append(elem.getAttribute("ControlType"))
		      //.append("\n HasKeyboardFocus=").append(elem.getAttribute("HasKeyboardFocus"))
		      .append("\n BoundingRectangle=").append(elem.getAttribute("BoundingRectangle"))
		//.append("\n IsEnabled=").append(elem.getAttribute("IsEnabled"))
		;

		//String message;
		//try {
		//	message = elem.isEnabled() + "";
		//} catch (Exception e) {
		//	message = "[NOT SUPPORTED, " + e.getMessage() + "]";
		//}
		//buffer.append("\n isEnabled=").append(message);
		//
		//try {
		//	message = elem.isDisplayed() + "";
		//} catch (Exception e) {
		//	message = "[NOT SUPPORTED, " + e.getMessage() + "]";
		//}
		//buffer.append("\n isDisplay=").append(message);
		//
		//try {
		//	message = elem.isSelected() + "";
		//} catch (WebDriverException e) {
		//	String[] errDetails = StringUtils.splitByWholeSeparator(e.getMessage(), "\n");
		//	message = "[NOT SUPPORTED, " + (ArrayUtils.isNotEmpty(errDetails) ? errDetails[0] : e.getMessage()) + "]";
		//} catch (Exception e) {
		//	message = "[NOT SUPPORTED, " + e.getMessage() + "]";
		//}
		//buffer.append("\n isSelected=").append(message);

		if (elem instanceof RemoteWebElement) {
			RemoteWebElement rwe = ((RemoteWebElement) elem);
			buffer.append("\n Coordinates=").append(rwe.getCoordinates())
			      .append("\n Id=").append(rwe.getId());
		}

		return buffer.append("\n").toString();
	}
}
