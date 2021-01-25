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

import com.google.common.collect.ImmutableList;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.tuple.MutablePair;
import org.nexial.commons.proc.ProcessInvoker;
import org.nexial.commons.proc.RuntimeUtils;
import org.nexial.commons.utils.FileUtil;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.ExecutionThread;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.utils.ConsoleUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.net.PortProber;
import org.openqa.selenium.winium.DesktopOptions;
import org.openqa.selenium.winium.WiniumDriver;
import org.openqa.selenium.winium.WiniumDriverService;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;

import static java.io.File.separator;
import static org.apache.commons.lang3.SystemUtils.IS_OS_WINDOWS;
import static org.nexial.commons.proc.ProcessInvoker.WORKING_DIRECTORY;
import static org.nexial.core.NexialConst.*;
import static org.nexial.core.NexialConst.Data.WIN32_CMD;
import static org.nexial.core.NexialConst.Desktop.*;
import static org.nexial.core.NexialConst.Project.NEXIAL_HOME;
import static org.nexial.core.NexialConst.Project.NEXIAL_WINDOWS_BIN_REL_PATH;
import static org.nexial.core.SystemVariables.getDefaultBool;
import static org.nexial.core.utils.WebDriverUtils.*;
import static org.openqa.selenium.winium.WiniumDriverService.*;

public final class WiniumUtils {
    private static String winiumPort;
    private static WiniumDriverService winiumDriverService;

    private WiniumUtils() { }

    public static WiniumDriverService getWiniumService() {
        if (winiumDriverService == null) { winiumDriverService = newWiniumService(); }
        return winiumDriverService;
    }

    public static WiniumDriver joinCurrentWiniumSession(int port) throws IOException {
        DesktopOptions options = new DesktopOptions();
        return new WiniumDriver(new URL("http://localhost:" + port), options);
    }

    public static WiniumDriver joinCurrentWiniumSession(int port, String exePath, String arguments) throws IOException {
        DesktopOptions options = resolveDesktopOptions(exePath, arguments);
        options.setDebugConnectToRunningApp(true);
        return new WiniumDriver(new URL("http://localhost:" + port), options);
    }

    public static WiniumDriver joinCurrentWiniumSession() throws IOException {
        DesktopOptions options = new DesktopOptions();
        //options.setKeyboardSimulator(BasedOnWindowsFormsSendKeysClass);
        return new WiniumDriver(new URL("http://localhost:" + winiumPort), options);
    }

    // currently only used by playground
    public static WiniumDriver newWiniumInstance(String autCmd) throws IOException {
        if (StringUtils.isBlank(autCmd)) { throw new IOException("EXE path for AUT is missing"); }
        MutablePair<String, String> exeAndArguments = toExeAndArgs(autCmd);
        return newWiniumInstance(exeAndArguments.getLeft(), exeAndArguments.getRight());
    }

    public static WiniumDriver newWiniumInstance(Aut aut) throws IOException {
        if (!IS_OS_WINDOWS) {
            ConsoleUtils.log("Winium requires Windows OS");
            return null;
        }

        if (aut == null) { throw new IOException("No AUT defined"); }
        if (StringUtils.isBlank(aut.getPath())) { throw new IOException("EXE path for AUT is missing"); }
        if (StringUtils.isBlank(aut.getExe())) { throw new IOException("EXE name for AUT is missing"); }

        String autCmd = StringUtils.appendIfMissing(aut.getPath(), separator) + aut.getExe();
        String args = aut.getArgs();

        if (joinExistingWiniumSession()) {return joinCurrentWiniumSession(NumberUtils.toInt(winiumPort), autCmd, args);}

        // terminate existing instances, if so configured.
        // we shouldn't have multiple instance of AUT running at the same time.. could get into unexpected result
        if (aut.isTerminateExisting()) { terminateRunningInstance(aut.getExe()); }

        List<Integer> runningInstances = RuntimeUtils.findRunningInstances(aut.getExe());
        if (CollectionUtils.isEmpty(runningInstances)) {
            // nope.. can't find AUT instance.  Let's start it now
            ConsoleUtils.log("starting new AUT instance via " + autCmd);

            String exePath;
            List<String> exeArgs = new ArrayList<>();

            Map<String, String> autEnv = new HashMap<>();
            boolean hasValidWorkingDirectory = false;
            if (FileUtil.isDirectoryReadable(aut.getWorkingDirectory())) {
                hasValidWorkingDirectory = true;
                autEnv.put(WORKING_DIRECTORY, aut.getWorkingDirectory());
            }

            if (aut.isRunFromWorkingDirectory() && hasValidWorkingDirectory) {
                ConsoleUtils.log("Executing AUT from working directory " + aut.getWorkingDirectory());

                // this flag means to first CD into the working directory and then execute program
                exePath = WIN32_CMD;
                exeArgs.add("/C");
                exeArgs.add("cd " + aut.getWorkingDirectory());
                exeArgs.add(" & ");

                if (StringUtils.equals(aut.getPath(), aut.getWorkingDirectory())) {
                    exeArgs.add(aut.getExe());
                } else {
                    exeArgs.add(autCmd);
                }
                // } else {
                // 	exePath = autCmd;
                // 	ConsoleUtils.log("Executing AUT from via " + exePath);
                // }

                // program cmdline args always come last
                if (StringUtils.isNotBlank(aut.getArgs())) {
                    exeArgs.addAll(TextUtils.toList(aut.getArgs(), " ", true));
                }

                try {
                    ProcessInvoker.invokeNoWait(exePath, exeArgs, autEnv);
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    throw new IOException("Unable to start new app: " + e.getMessage(), e);
                }

                runningInstances = RuntimeUtils.findRunningInstances(aut.getExe());
                ConsoleUtils.log("Found running instance of " + aut.getExe() + ": " + runningInstances);
            }
        }

        // now, we need to determine if AUT has been instantiated (such as first instantiation of the driver), or
        // if AUT instance needs to be instantiated yet (such as switching between multiple AUT).
        // if (!aut.isTerminateExisting()) {  }

        // if there are running instances of AUT now, then we should join to such instance (the first one)
        // and use it as _THE_ AUT instance
        if (CollectionUtils.isNotEmpty(runningInstances)) {
            // but we don't know if winium is still running... let's make sure
            List<Integer> runningWiniums = RuntimeUtils.findRunningInstances(WINIUM_EXE);
            if (CollectionUtils.isNotEmpty(runningWiniums) && StringUtils.isNotBlank(winiumPort)) {
                ConsoleUtils.log("joining existing Winium session over port " + winiumPort);
                return joinCurrentWiniumSession(NumberUtils.toInt(winiumPort), autCmd, args);
            }

            ConsoleUtils.log("create new Winium session, destroying instance " + runningWiniums);
            DesktopOptions options = new DesktopOptions();
            options.setDebugConnectToRunningApp(true);
            return new WiniumDriver(getWiniumService(), options);
        }

        ConsoleUtils.log("create new Winium session");
        return new WiniumDriver(getWiniumService(), resolveDesktopOptions(autCmd, args));
    }

    public static WiniumDriver joinRunningApp() {
        if (!IS_OS_WINDOWS) {
            ConsoleUtils.log("Winium requires Windows OS");
            return null;
        }

        DesktopOptions options = new DesktopOptions();
        options.setDebugConnectToRunningApp(true);
        return new WiniumDriver(getWiniumService(), options);
    }

    public static WiniumDriver newWiniumInstance(String exePath, String arguments) throws IOException {
        if (!IS_OS_WINDOWS) {
            ConsoleUtils.log("Winium requires Windows OS");
            return null;
        }

        if (joinExistingWiniumSession()) {
            return joinCurrentWiniumSession(NumberUtils.toInt(winiumPort), exePath, arguments);
        }

        // we shouldn't have multiple instance of AUT running at the same time.. could get into unexpected result
        if (StringUtils.isNotBlank(exePath)) {
            terminateRunningInstance(StringUtils.substringAfterLast(exePath, separator));
        }

        return new WiniumDriver(getWiniumService(), resolveDesktopOptions(exePath, arguments));
    }

    public static void sendKey(WiniumDriver driver, WebElement elem, String keystrokes, boolean useAscii) {
        if (driver == null) { return; }
        if (StringUtils.isEmpty(keystrokes)) { return; }

        Map<String, CharSequence> controlKeyMapping = useAscii ? AsciiKeyMapping.CONTROL_KEY_MAPPING : CONTROL_KEY_MAPPING;
        Map<String, CharSequence> keyMapping = useAscii ? AsciiKeyMapping.KEY_MAPPING : KEY_MAPPING;

        Actions actions = new Actions(driver);
        Stack<CharSequence> controlKeys = new Stack<>();

        while (StringUtils.isNotEmpty(keystrokes)) {
            String nextKeyStroke = TextUtils.substringBetweenFirstPair(keystrokes, CTRL_KEY_START, CTRL_KEY_END);
            if (StringUtils.isBlank(nextKeyStroke)) {
                // 2. if none (or no more) {..} found, gather remaining string and create sendKey() action
                actions = addReleaseControlKeys(actions, elem, controlKeys);
                actions.perform();
                actions = new Actions(driver);

                if (elem == null) {
                    String[] keys = TextUtils.toOneCharArray(keystrokes);
                    actions.sendKeys(keys);
                } else {
                    elem.sendKeys(keystrokes);
                }
                break;
            }

            String keystrokeId = CTRL_KEY_START + nextKeyStroke + CTRL_KEY_END;

            // 3. if {..} found, let's push all the keystrokes before the found {..} to action
            String text = StringUtils.substringBefore(keystrokes, keystrokeId);
            if (StringUtils.isNotEmpty(text)) {
                actions = addReleaseControlKeys(actions, elem, controlKeys);
                actions.perform();
                actions = new Actions(driver);

                if (elem == null) {
                    String[] keys = TextUtils.toOneCharArray(text);
                    actions.sendKeys(keys);
                } else {
                    elem.sendKeys(text);
                }
            }

            // 4. keystrokes now contain the rest of the key strokes after the found {..}
            keystrokes = StringUtils.substringAfter(keystrokes, keystrokeId);

            // 5. if the found {..} is a single key, just add it as such (i.e. {CONTROL}{C})
            if (StringUtils.length(nextKeyStroke) == 1 && StringUtils.isAlphanumeric(nextKeyStroke)) {
                actions = elem == null ? actions.sendKeys(nextKeyStroke) : actions.sendKeys(elem, nextKeyStroke);
                actions = addReleaseControlKeys(actions, elem, controlKeys);
                actions.perform();
                actions = new Actions(driver);
            } else {
                if (controlKeyMapping.containsKey(keystrokeId)) {
                    // 6. is the found {..} one of the control keys (CTRL, SHIFT, ALT)?
                    CharSequence control = controlKeyMapping.get(keystrokeId);
                    controlKeys.push(control);
                    actions = elem == null ? actions.keyDown(control) : actions.keyDown(elem, control);
                } else {
                    // 7. if not, then it must one of the non-printable character
                    CharSequence keystroke = keyMapping.get(keystrokeId);
                    if (keystroke == null) { throw new RuntimeException("Unsupported/unknown key " + keystrokeId); }

                    actions = elem == null ? actions.sendKeys(keystroke) : actions.sendKeys(elem, keystroke);
                    actions = addReleaseControlKeys(actions, elem, controlKeys);
                    actions.perform();
                    actions = new Actions(driver);
                }
            }

            // 8. loop back
        }

        // 9. just in case user put a control character at the end (not sure why though)
        actions = addReleaseControlKeys(actions, elem, controlKeys);

        // 10. finally, all done!
        actions.perform();
    }

    public static void shutdownWinium(WiniumDriverService service, WiniumDriver driver) {
        ConsoleUtils.log("shutdown Winium...");
        if (driver != null) {
            try {
                driver.close();
            } catch (Throwable e) {
                ConsoleUtils.log("Unable to close desktop application:" + NL + e.getMessage() + NL +
                                 "Continue on quit Winium Driver");
            }

            try {
                driver.quit();
            } catch (Throwable e) {
                ConsoleUtils.log("Unable to close Winium Driver:" + NL + e.getMessage() + NL +
                                 "Continue on shut down Winium Driver Service");
            } finally {
                driver = null;
            }
        }

        if (service != null) {
            if (winiumDriverService != null) {
                if (service == winiumDriverService) {
                    shutdownDriverService(winiumDriverService);
                } else {
                    shutdownDriverService(service);
                }
                winiumDriverService = null;
            } else {
                shutdownDriverService(service);
            }
            service = null;
        } else if (winiumDriverService != null) {
            shutdownDriverService(winiumDriverService);
            winiumDriverService = null;
        }
    }

    public static void clickAppTopLeft(WiniumDriver driver, String xpath) {
        if (driver == null || StringUtils.isBlank(xpath)) { return; }

        try {
            WebElement element = driver.findElement(By.xpath(xpath));
            if (element == null) { return; }

            new Actions(driver).moveToElement(element, 3, 5).click().pause(750).build().perform();
        } catch (WebDriverException e) {
            // don't cause trouble.. this click is meant as a safeguard
            ConsoleUtils.log("Error when trying to focus on application: " +
                             StringUtils.substringBefore(e.getMessage(), e.getSupportUrl()));
        } catch (Throwable e) {
            // don't cause trouble.. this click is meant as a safeguard
            ConsoleUtils.log("Error when trying to focus on application: " + e.getMessage());
        }
    }

    protected static boolean joinExistingWiniumSession() {
        ExecutionContext context = ExecutionThread.get();
        boolean joinExisting = ExecutionContext.getSystemThenContextBooleanData(WINIUM_JOIN,
                                                                                context,
                                                                                getDefaultBool(WINIUM_JOIN));
        if (!joinExisting) { return false; }

        deriveWiniumPort(context);
        return true;
    }

    protected static boolean isSoloMode() {
        return ExecutionContext.getSystemThenContextBooleanData(WINIUM_SOLO_MODE,
                                                                ExecutionThread.get(),
                                                                getDefaultBool(WINIUM_SOLO_MODE));
    }

    protected static boolean terminateRunningInstance(String exeName) {
        if (!isSoloMode()) {
            ConsoleUtils.log("not terminating " + exeName + " since !isSoloMode()=" + (!isSoloMode()));
            return false;
        }

        return RuntimeUtils.terminateInstance(exeName);
    }

    protected static void shutdownDriverService(WiniumDriverService service) {
        try {
            service.stop();
        } catch (Throwable e) {
            ConsoleUtils.log("Unable to close Winium Driver Service:" + NL + e.getMessage());
        } finally {
            service = null;
        }
    }

    protected static WiniumDriverService newWiniumService() {
        if (!IS_OS_WINDOWS) {
            ConsoleUtils.log("Winium requires Windows OS");
            return null;
        }

        File nexialHomeDir = new File(System.getProperty(NEXIAL_HOME));
        String winiumExePath = StringUtils.appendIfMissing(nexialHomeDir.getAbsolutePath(), separator) +
                               NEXIAL_WINDOWS_BIN_REL_PATH + WINIUM_EXE;
        if (!FileUtil.isFileExecutable(winiumExePath)) {
            throw new RuntimeException(winiumExePath + " is not valid winium executable. Unable to proceed");
        }

        if (terminateRunningInstance(WINIUM_EXE)) {
            try { Thread.sleep(3000); } catch (InterruptedException e) { }
        }

        ExecutionContext context = ExecutionThread.get();

        // log
        String winiumLogPath = System.getProperty(WINIUM_LOG_PATH,
                                                  context == null ? null : context.getStringData(WINIUM_LOG_PATH));
        File logFile = null;
        if (StringUtils.isNotBlank(winiumLogPath)) { logFile = new File(winiumLogPath); }

        // port
        deriveWiniumPort(context);
        ConsoleUtils.log("Assigning to Winium Driver Service port " + winiumPort);
        if (context != null) {
            try {
                File portFile = new File(StringUtils.appendIfMissing(context.getProject().getOutPath(), separator) +
                                         "winium-port.txt");
                FileUtils.write(portFile, winiumPort, DEF_CHARSET);
            } catch (IOException e) {
                ConsoleUtils.log("Unable to write 'winium-port.txt to output dir: " + e.getMessage());
            }
        }

        WiniumDriverService.Builder winiumBuilder = new WiniumDriverService.Builder() {
            /** workaround to honor another port not 9999 */
            @Override
            protected ImmutableList<String> createArgs() {
                if (getLogFile() == null) {
                    String logFilePath = System.getProperty(WINIUM_DRIVER_LOG_PATH_PROPERTY);
                    if (logFilePath != null) { withLogFile(new File(logFilePath)); }
                }

                ImmutableList.Builder<String> argsBuidler = new ImmutableList.Builder<>();
                if (Boolean.getBoolean(WINIUM_DRIVER_SILENT)) { argsBuidler.add("--silent"); }
                if (Boolean.getBoolean(WINIUM_DRIVER_VERBOSE_LOG)) { argsBuidler.add("--verbose"); }
                if (getLogFile() != null) {
                    argsBuidler.add(String.format("--log-path=%s", getLogFile().getAbsolutePath()));
                }
                argsBuidler.add("--port=" + winiumPort);

                return argsBuidler.build();
            }
        }.usingDriverExecutable(new File(winiumExePath))
         .usingPort(NumberUtils.toInt(winiumPort));

        if (logFile != null) { winiumBuilder = winiumBuilder.withLogFile(logFile); }

        WiniumDriverService winiumService = winiumBuilder.buildDesktopService();
        startWiniumService(winiumService);

        if (context != null) { context.setData(WINIUM_SERVICE_RUNNING, true); }

        return winiumService;
    }

    protected static void deriveWiniumPort(ExecutionContext context) {
        winiumPort = ExecutionContext.getSystemThenContextStringData(WINIUM_PORT, context, "");
        if (!NumberUtils.isDigits(winiumPort)) { winiumPort = PortProber.findFreePort() + ""; }
    }

    protected static void startWiniumService(WiniumDriverService winiumService) {
        if (!winiumService.isRunning()) {
            String msgPrefix = "Winium Driver Service ";

            //try { Thread.sleep(10000); } catch (InterruptedException e) { }
            ConsoleUtils.log(msgPrefix + "starting...");

            try {
                winiumService.start();
            } catch (IOException e) {
                String msg = msgPrefix + "failed to start due to " + e.getMessage();
                ConsoleUtils.error(msg);
                throw new RuntimeException(msg, e);
            }

            ConsoleUtils.log(msgPrefix + "started.");
            //			try { Thread.sleep(1500); } catch (InterruptedException e) { }
        }
    }

    protected static DesktopOptions resolveDesktopOptions(String exePath, String arguments) throws IOException {
        // assert StringUtils.isNotBlank(exePath);

        DesktopOptions options = new DesktopOptions();

        if (StringUtils.isNotBlank(exePath)) {
            if (!FileUtil.isFileExecutable(exePath)) { throw new IOException("AUT EXE is not executable: " + exePath); }

            ConsoleUtils.log("determined that AUT exe is found at " + exePath);
            options.setApplicationPath(exePath);

            if (StringUtils.isNotBlank(arguments)) {
                ConsoleUtils.log("command line arguments = " + arguments);
                options.setArguments(arguments);
            }
        }

        //options.setKeyboardSimulator(BasedOnWindowsFormsSendKeysClass);
        ExecutionContext context = ExecutionThread.get();
        boolean joinExisting = ExecutionContext.getSystemThenContextBooleanData(WINIUM_JOIN, context,
                                                                                getDefaultBool(WINIUM_JOIN));
        options.setDebugConnectToRunningApp(joinExisting);

        return options;
    }

    protected static MutablePair<String, String> toExeAndArgs(String autCmd) throws IOException {
        MutablePair<String, String> exeAndArgument = new MutablePair<>();

        // aut command might contain arguments?
        autCmd = StringUtils.trim(autCmd);
        if (StringUtils.startsWith(autCmd, "\"")) {
            int indexNextDoubleQuote = StringUtils.indexOf(autCmd, "\"", 3);
            if (indexNextDoubleQuote == -1) { throw new IOException("Invalid EXE path AUT: " + autCmd); }

            // aut command might be appended with cmdline arguments (i.e. "C:\blah\yada\My Program.exe" -t 1 -d true)
            String exePath = StringUtils.trim(StringUtils.substring(autCmd, 1, indexNextDoubleQuote));
            String arguments = StringUtils.trim(StringUtils.substringAfter(autCmd, exePath));
            arguments = StringUtils.trim(StringUtils.removeStart(arguments, "\""));

            exeAndArgument.setLeft(exePath);
            exeAndArgument.setRight(arguments);
        } else {
            // no double quote, then exe and arguments are separate by space
            String exePath = StringUtils.trim(StringUtils.substringBefore(autCmd, " "));
            String arguments = StringUtils.trim(StringUtils.substringAfter(autCmd, exePath));
            exeAndArgument.setLeft(exePath);
            exeAndArgument.setRight(arguments);
        }
        return exeAndArgument;
    }
}