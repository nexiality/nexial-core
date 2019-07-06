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

import java.io.IOException;
import java.util.Arrays;

import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.proc.ProcessInvoker;
import org.nexial.commons.utils.EnvUtils;
import org.nexial.commons.utils.FileUtil;
import org.nexial.core.ExecutionThread;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.TestProject;
import org.nexial.core.utils.ConsoleUtils;

import static java.io.File.separator;
import static org.apache.commons.lang3.SystemUtils.IS_OS_WINDOWS;
import static org.nexial.core.NexialConst.Project.NEXIAL_WINDOWS_BIN_REL_PATH;
import static org.nexial.core.plugins.desktop.DesktopNotification.NotificationLevel.info;

public class DesktopNotification {
    private static final String CALLSIGN = "nexial notifier";
    private static final long DEF_AUTO_DISMISS_MS = 5000;

    private static String notifierPath;

    public enum NotificationLevel {info, warn, error}

    private DesktopNotification() { }

    public static void notify(NotificationLevel level, String message, long autoDismissMs) {
        if (!IS_OS_WINDOWS) { return; }
        if (StringUtils.isBlank(message)) { return; }
        if (autoDismissMs < 500) { autoDismissMs = DEF_AUTO_DISMISS_MS; }

        init();
        if (StringUtils.isBlank(notifierPath)) { return; }

        try {
            ProcessInvoker.invokeNoWait(notifierPath,
                                        Arrays.asList("/p", CALLSIGN,
                                                      "/m", resolveMessage(message),
                                                      "/d", autoDismissMs + "",
                                                      "/t", level == null ? info.name() : level.name()),
                                        null);
        } catch (IOException e) {
            ConsoleUtils.error("Unable to send desktop notification: " + message + ". " + e.getMessage());
        }
    }

    public static void notifyNoAutoDismiss(NotificationLevel level, String message) {
        if (!IS_OS_WINDOWS) { return; }
        if (StringUtils.isBlank(message)) { return; }

        init();
        if (StringUtils.isBlank(notifierPath)) { return; }

        try {
            ProcessInvoker.invokeNoWait(notifierPath,
                                        Arrays.asList("/p", CALLSIGN,
                                                      "/m", resolveMessage(message),
                                                      "/d", "0",
                                                      "/t", level == null ? info.name() : level.name()),
                                        null);
        } catch (IOException e) {
            ConsoleUtils.error("Unable to send desktop notification: " + message + ". " + e.getMessage());
        }

    }

    private static String resolveMessage(String message) {
        return "\"" + StringUtils.replace(message, "\"", "\\\"") + "\"";
    }

    private static void init() {
        if (!IS_OS_WINDOWS) { return; }
        if (StringUtils.isNotBlank(DesktopNotification.notifierPath)) { return; }

        String msgPrefix = "Unable to determine notifier path since ";

        ExecutionContext context = ExecutionThread.get();
        if (context == null) {
            ConsoleUtils.error(msgPrefix + "execution context is not reachable");
            return;
        }

        TestProject project = context.getProject();
        if (project == null) {
            ConsoleUtils.error(msgPrefix + "test project is not found in current execution context");
            return;
        }

        String nexialHome = project.getNexialHome();
        if (StringUtils.isBlank(nexialHome)) {
            ConsoleUtils.error(msgPrefix + "'nexial.home' is not found in current execution context");
            return;
        }

        String notifierPath = StringUtils.appendIfMissing(nexialHome, separator) + NEXIAL_WINDOWS_BIN_REL_PATH +
                              "notifu" + (EnvUtils.isRunningWindows64bit() ? "64" : "") + ".exe";
        if (!FileUtil.isFileExecutable(notifierPath)) {
            ConsoleUtils.error(msgPrefix + "the resolved notifier path is invalid: " + notifierPath);
            return;
        }

        DesktopNotification.notifierPath = notifierPath;
    }
}
