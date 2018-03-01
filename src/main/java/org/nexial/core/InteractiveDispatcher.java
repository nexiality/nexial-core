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

package org.nexial.core;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.openqa.selenium.WebDriver;

import org.nexial.core.excel.Excel;
import org.nexial.core.excel.Excel.Worksheet;
import org.nexial.core.excel.ext.CellTextReader;
import org.nexial.core.interactive.MenuHelper;
import org.nexial.core.interactive.TextDevice;
import org.nexial.core.interactive.TextDevices;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.TestCase;
import org.nexial.core.model.TestScenario;
import org.nexial.core.model.TestStep;
import org.nexial.core.plugins.ForcefulTerminate;

import static org.nexial.core.NexialConst.Data.DEF_FAIL_FAST;
import static org.nexial.core.NexialConst.Data.FAIL_FAST;
import static org.nexial.core.NexialConst.Data.SHEET_MERGED_DATA;
import static org.nexial.core.NexialConst.*;
import static org.nexial.core.interactive.InteractiveConst.*;
import static org.nexial.core.interactive.InteractiveConst.Command.*;
import static org.nexial.core.interactive.InteractiveConst.Menu.*;

/**
 *
 */
public class InteractiveDispatcher implements ForcefulTerminate {
	private PrintStream out;
	private TextDevice textDevice;
	private ExecutionContext context;
	private TestCase executor;
	private List<String> inputHistory = new ArrayList<>(HISTORY_SIZE);
	private boolean fullMenuShown = false;

	public InteractiveDispatcher(PrintStream out) {
		System.setProperty(OPT_INTERACTIVE, "true");
		this.out = out;
		textDevice = TextDevices.hasConsole() ?
		             TextDevices.defaultTextDevice() : TextDevices.streamDevice(System.in, out);
		ShutdownAdvisor.addAdvisor(this);
	}

	public InteractiveDispatcher(ExecutionContext context, PrintStream out) {
		this(out);
		this.context = context;
	}

	@Override
	public boolean mustForcefullyTerminate() {
		return StringUtils.equals(System.getProperty(OPT_INTERACTIVE), "true");
	}

	@Override
	public void forcefulTerminate() { }

	public void doPrompt() throws IOException {
		if (!fullMenuShown) {
			out.println();
			out.println(MenuHelper.banner(DOT));
			out.print(MenuHelper.file(COMMANDS_PAGE, DOT + " "));
			out.println(MenuHelper.repeat(DOT, BANNER_WIDTH_TEXT));

			out.println("Current Setting");
			out.println("\t" + StringUtils.rightPad(FAIL_FAST, 16) + " - " +
			            StringUtils.defaultString(context.getStringData(FAIL_FAST), DEF_FAIL_FAST + ""));
			out.println("\t" + StringUtils.rightPad(OPT_EXCEL_FILE, 16) + " - " +
			            context.getStringData(OPT_EXCEL_FILE));
			out.println("\t" + StringUtils.rightPad(OPT_EXCEL_WORKSHEET, 16) + " - " +
			            context.getStringData(OPT_EXCEL_WORKSHEET));
			out.println(MenuHelper.repeat(DOT, BANNER_WIDTH_TEXT));

			fullMenuShown = true;
		} else {
			out.println();
		}

		if (processCmd(textDevice.printf(PROMPT).readLine())) { doPrompt(); }
	}

	public void setContext(ExecutionContext context) { this.context = context; }

	public void setExecutor(TestCase executor) { this.executor = executor; }

	public static void main(String[] args) throws Exception {
		InteractiveDispatcher interactor = new InteractiveDispatcher(System.out);
		interactor.doPrompt();
	}

	protected boolean processCmd(String inputLine) {
		if (inputLine == null) {
			// no input probably means no stdin and/or stdout
			error("missing stdin; exiting...");
			return false;
		}

		if (StringUtils.isBlank(inputLine)) {
			error("no input\n");
			return true;
		}

		debug("COMMAND RECEIVED: " + inputLine);

		if (StringUtils.equalsIgnoreCase(inputLine, quit.name())) {
			quit();
			return false;
		}

		// check that current excel is set
		if (context == null) {
			error("context is null; skip command processing...");
			return true;
		}

		String methodName = StringUtils.substringBefore(inputLine, " ");
		try {
			Method method = this.getClass().getDeclaredMethod(methodName, String.class);
			if (method != null) {
				method.setAccessible(true);
				method.invoke(this, inputLine);
			} else {
				error("unknown command - " + methodName);
			}
		} catch (InvocationTargetException e) {
			error("failed to executing command - " + inputLine + ": " +
			      (e.getTargetException() != null ? e.getTargetException().getMessage() : e.getMessage()), e);
		} catch (NoSuchMethodException e) {
			error("unknown command - " + methodName);
		} catch (Exception e) {
			error("failed to executing command - " + inputLine + ": " + e.toString(), e);
		}
		return true;
	}

	protected void quit() {
		out.println("terminating interactive mode...");
		out.println("stopping Nexial...");
	}

	protected void help(String inputLine) {
		String command = StringUtils.trim(StringUtils.substringAfter(inputLine, help.name()));
		out.println(StringUtils.isEmpty(command) ? MenuHelper.buildMenu(MAN_PAGE) : MenuHelper.menu(command));
	}

	protected void set(String inputLine) {
		String argument = StringUtils.trim(StringUtils.substringAfter(inputLine, set.name()));
		debug("argument=" + argument);

		String key = StringUtils.trim(StringUtils.substringBefore(argument, ARG_SEP));
		if (StringUtils.isBlank(key)) {
			error("key is empty/blank; key/value not set");
			return;
		}

		String value = StringUtils.substringAfter(argument, ARG_SEP);
		debug(key + ARG_SEP + value);

		addHistory(inputLine);

		String resolvedKey = context.replaceTokens(key);
		out.println("key resolved to " + resolvedKey);

		String resolvedValue = context.replaceTokens(value);
		out.println("value resolved to '" + resolvedValue + "' (MAY CHANGE OVER TIME)");

		if (context.hasData(resolvedKey)) { debug("replacing existing key " + resolvedKey); }
		context.setData(resolvedKey, value);
	}

	protected void read(String inputLine) {
		addHistory(inputLine);

		// any property?
		String property = StringUtils.trim(StringUtils.substringAfter(inputLine, " "));
		if (StringUtils.isNotBlank(property)) {
			debug("property=" + property);
			out.println("property '" + property + "' has: " + CellTextReader
				                                                  .readValue(context.getStringData(property)));
			return;
		}

		String excelFile = context.getStringData(OPT_EXCEL_FILE);
		if (StringUtils.isBlank(excelFile)) {
			error("Current excel file NOT set via '" + OPT_EXCEL_FILE + "' property");
			return;
		}

		//out.println("reading '#data' worksheet in '" + excelFile + "'...");
		//TestData dataSheet = Excel.newTestDataInstance(excelFile);
		//Map<String, String> data = dataSheet.getData();
		//for (String key : data.keySet()) {
		//	String value = data.get(key);
		//	String filler = key.length() < 50 ? StringUtils.repeat(" ", 50 - key.length()) : "";
		//	out.println(key + filler + " = " + CellTextReader.readValue(value));
		//	context.setData(key, value);
		//}
		//
		//out.println(data.size() + " pairs of variables added (overlayed) to current context");
	}

	protected void run(String inputLine) throws IOException {
		if (StringUtils.isBlank(inputLine)) {
			error("INPUT NOT SPECIFIED!");
			return;
		}

		// need to support:
		//  run Test Case 1                 --> run the entire "Test Case 1" test component
		//  run 15                          --> run row 15
		//  run 15,29                       --> run row 15 AND 29
		//  run 15-29                       --> run row 15 through 29 (inclusive)
		//  run 15,29,Test Case 1,31-44,17  --> run row 15 AND 29, then run "Test Case 1", then run row 31 through 44 and row 17

		String argLine = StringUtils.trim(StringUtils.substringAfter(inputLine, run.name()));
		String[] args = StringUtils.split(argLine, RUN_ARG_SEPARATOR);
		if (ArrayUtils.isEmpty(args)) {
			error("INPUT NOT SPECIFIED!");
			return;
		}

		addHistory(inputLine);

		for (String arg : args) {
			arg = StringUtils.trim(arg);

			// argument is exactly 1 number
			if (NumberUtils.isDigits(arg)) {
				runRow(NumberUtils.toInt(arg));
			} else {
				if (StringUtils.contains(arg, RUN_ARG_RANGE_SEPARATOR)) {
					String[] range = StringUtils.split(arg, RUN_ARG_RANGE_SEPARATOR);
					// argument is a range of rows
					if (ArrayUtils.getLength(range) == 2 &&
					    NumberUtils.isDigits(StringUtils.trim(range[0])) &&
					    NumberUtils.isDigits(StringUtils.trim(range[1]))) {
						int startRow = NumberUtils.toInt(StringUtils.trim(range[0]));
						int endRow = NumberUtils.toInt(StringUtils.trim(range[1]));
						if (endRow < startRow) {
							int temp = startRow;
							startRow = endRow;
							endRow = temp;
						}
						runRows(startRow, endRow);
					} else {
						// not a range, run as "test step"
						runTestCase(StringUtils.trim(arg));
					}
				} else {
					// not a digit and not a range, run as "test step"
					runTestCase(StringUtils.trim(arg));
				}
			}
		}

		System.gc();
	}

	//protected void browser(String inputLine) throws IOException {
	//	addHistory(inputLine);
	//
	//	WebDriver webdriver = context.getDriver();
	//
	//	// any param?
	//	String param = StringUtils.trim(StringUtils.substringAfter(inputLine, " "));
	//	debug("param=" + param);
	//
	//	if (StringUtils.isEmpty(param)) {
	//		out.println("current browser is " + context.getBrowser());
	//		out.println("current webdriver is " + webdriver);
	//		return;
	//	}
	//
	//	boolean restarting = StringUtils.equals(param, "restart");
	//	BrowserType browserType = restarting ? null : BrowserType.valueOf(param);
	//	if (!restarting && browserType == null) {
	//		error("Unsupported browser: " + param);
	//		return;
	//	}
	//
	//	if (restarting) {
	//		out.println("restarting current browser...");
	//	} else {
	//		out.println("changing to browser to " + browserType);
	//		System.setProperty(OPT_BROWSER, param);
	//	}
	//
	//	closeWindows(webdriver);
	//
	//	if (restarting) {
	//		context.getDriver().quit();
	//		//context.driver = null;
	//		context.getDriver().quit();
	//	}
	//
	//	context.initSelenium();
	//	out.println("browser restarted.");
	//}

	protected void recall(String inputLine) {
		String argument = StringUtils.trim(StringUtils.substringAfter(inputLine, " "));
		String commandline;
		if (StringUtils.isBlank(argument)) {
			if (inputHistory.isEmpty()) {
				error("No previous history to recall");
				return;
			}
			commandline = recallLastFromHistory();
		} else {
			int historyIndex = NumberUtils.toInt(argument);
			if (historyIndex < 1 || historyIndex > inputHistory.size()) {
				error("Invalid history position - " + argument);
				return;
			}

			commandline = recallFromHistory(historyIndex);
		}

		processCmd(commandline);
	}

	protected void history(String inputLine) {
		out.println("HISTORY:");

		if (CollectionUtils.isEmpty(inputHistory)) {
			out.println("\tNONE");
		} else {
			for (int i = 0; i < inputHistory.size(); i++) {
				String history = inputHistory.get(i);
				out.println("\t" + StringUtils.leftPad((i + 1) + "", 2, " ") + ". " + history);
			}
		}
	}

	private void closeWindows(WebDriver webdriver) {
		try {
			Set<String> windowHandles = webdriver == null ? null : webdriver.getWindowHandles();
			if (CollectionUtils.isNotEmpty(windowHandles)) {
				out.println("closing any opened windows...");
				for (String handle : windowHandles) {
					try {
						WebDriver window = webdriver.switchTo().window(handle);
						window.close();
					} catch (Exception e) {
						error("Unable to close window '" + handle + "'. Exception: " + e.getMessage());
					}
				}
			}
		} catch (Exception e) {
			error("Unable to communicate with webdriver:" + e.getMessage() + "\ncontinuing...");
		}
	}

	private void runTestCase(String testcaseName) throws IOException {
		TestScenario testScenario = deriveTestScenario();
		if (testScenario == null) { return; }

		TestCase testCase = testScenario.getTestCase(testcaseName);
		if (testCase == null) {
			error("No test case (" + testcaseName + ") found in the specified test script.  Check your script.");
			return;
		}

		List<TestStep> testSteps = testCase.getTestSteps();
		if (CollectionUtils.isEmpty(testSteps)) {
			error("No test steps(s) found in the specified test case.  Check your Excel script.");
			return;
		}

		boolean pass = testCase.execute();
		debug(" all pass? " + pass);
	}

	private void runRow(int startRow) throws IOException { runRows(startRow, startRow); }

	private void runRows(int startRow, int endRow) throws IOException {
		TestScenario testScenario = deriveTestScenario();
		if (testScenario == null) { return; }

		List<TestStep> testSteps = testScenario.getTestStepsByRowRange(startRow, endRow);
		if (CollectionUtils.isEmpty(testSteps)) {
			error("No command(s) found in the specified position.  Check your Excel script.");
			return;
		}

		testSteps.forEach(TestStep::execute);
	}

	private TestScenario deriveTestScenario() throws IOException {
		if (context == null) {
			error("context is null; skip command processing...");
			return null;
		}

		if (executor == null) {
			error("executor not defined/set; skip command processing...");
			return null;
		}

		String worksheetName = context.getStringData(OPT_EXCEL_WORKSHEET);
		if (StringUtils.isBlank(worksheetName)) {
			error("UNABLE TO DETERMINE CURRENT WORKSHEET; " + OPT_EXCEL_WORKSHEET + " not set");
			return null;
		}

		String currentExcelFile = context.getStringData(OPT_EXCEL_FILE);
		if (StringUtils.isBlank(currentExcelFile)) {
			error("UNABLE TO DETERMINE CURRENT EXCEL FILE; " + OPT_EXCEL_FILE + " not set");
			return null;
		}

		File file = new File(currentExcelFile);
		if (!file.exists() || !file.canRead()) {
			error("UNABLE TO READ FILE '" + currentExcelFile + "'");
			return null;
		}

		Excel excel = new Excel(file);
		Worksheet worksheet = excel.worksheet(worksheetName);
		if (worksheet == null) {
			error("SPECIFIED WORKSHEET '" + worksheetName + "' NOT FOUND IN '" + currentExcelFile + "'");
			if (!StringUtils.startsWith(worksheetName, "@")) {
				error("Do you mean @" + worksheetName + " instead?");
			}
			return null;
		}

		Worksheet datasheet = excel.worksheet(SHEET_MERGED_DATA);
		if (datasheet == null) {
			error("EXPECTED #data WORKSHEET NOT FOUND IN '" + currentExcelFile + "'");
			return null;
		}

		return new TestScenario(context, worksheet);
	}

	private void debug(String msg) { if (DEBUG) { out.println("    DEBUG >> " + msg); } }

	private void error(String msg) { out.println("!!!ERROR >> " + msg); }

	private void error(String msg, Throwable e) {
		out.println("!!!ERROR >> " + msg);
		e.printStackTrace(out);
		out.println("\n");
	}

	private void addHistory(String inputLine) {
		if (StringUtils.isEmpty(inputLine)) { return; }
		while (inputHistory.size() >= HISTORY_SIZE) { inputHistory.remove(inputHistory.size() - 1); }
		inputHistory.add(0, inputLine);
	}

	private String recallFromHistory(int historyIndex) {
		return inputHistory.remove(historyIndex - 1);
	}

	private String recallLastFromHistory() { return inputHistory.remove(0); }
}
