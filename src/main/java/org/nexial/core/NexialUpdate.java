/*
 * Copyright 2012-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.nexial.core;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.nexial.commons.utils.FileUtil;
import org.nexial.core.utils.ConsoleUtils;
import org.nexial.core.utils.ExecUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static java.io.File.separator;
import static org.nexial.core.NexialConst.DEF_CHARSET;
import static org.nexial.core.NexialConst.Project.*;
import static org.nexial.core.utils.ConsoleUtils.centerPrompt;

public class NexialUpdate {

    private static final String REL_PATH_INSTALLER_JAR = separator + "lib" + separator + "nexial-installer.jar";
    private static final String INSTALLER_DIR_NAME = "nexial-installer";
    private static final long timeoutSeconds = 15L;

    public static void checkAndRun() {
        if (ExecUtils.isRunningInZeroTouchEnv()) { return; }

        installNexialInstallerIfNotPresent();

        if (isUpdateReadyForInstallation()) {
            while (true) {
                int choice = 3; // default is 'skip'
                try {
                    String strChoice = CompletableFuture.supplyAsync(NexialUpdate::promptUserForInstallation)
                                                        .get(timeoutSeconds, TimeUnit.SECONDS);
                    choice = Integer.parseInt(StringUtils.trim(strChoice));
                } catch (NumberFormatException nfe) {
                    choice = 0; // Invalid option, re-prompt
                } catch (InterruptedException | ExecutionException | TimeoutException e) {
                    System.out.println("No option selected.");
                }

                switch (choice) {
                    case 1 -> {
                        saveUpdatePreferenceChoice(true);
                        System.exit(0);
                    }
                    case 2 -> {
                        saveUpdatePreferenceChoice(false);
                        System.exit(0);
                    }
                    case 3 -> {
                        System.out.println("Skipping the installation...\n");
                        return;
                    }
                    default -> System.out.println("Please choose valid option.\n");
                }

            }
        }
    }

    public static void installNexialInstallerIfNotPresent() {
        if (isInstallerPresent()) {
            String nexialInstallerVersion = getNexialInstallerVersion();
            if (isNexialInstallerOlder(nexialInstallerVersion)) { showNexialInstallerBanner(true); }
        } else {
            showNexialInstallerBanner(false);
        }
    }

    private static void showNexialInstallerBanner(boolean isUpdate) {
        String message = isUpdate ?
                         "Looks like you have an outdated version of Nexial Installer." :
                         "Looks like Nexial Installer is not found on your system.";
        System.out.println(
            "\n\n" +
            "/------------------------------------------------------------------------------\\\n" +
            "| " + centerPrompt("WARNING :: NEXIAL INSTALLER", 76) + " |\n" +
            "|------------------------------------------------------------------------------|\n" +
            "| " + StringUtils.rightPad(message, 76, " ") + " |\n" +
            "| " + StringUtils.rightPad("Please install the latest version.", 76, " ") + " |\n" +
            "\\------------------------------------------------------------------------------/" +
            "\n\n");
    }

    private static String getNexialInstallerVersion() {
        File fingerPrint = Paths.get(USER_PROJECTS_DIR, "nexial-installer", "version.txt").toFile();
        try {
            return FileUtil.isFileReadable(fingerPrint) ?
                   StringUtils.trim(FileUtils.readFileToString(fingerPrint, DEF_CHARSET)) :
                   null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean isUpdateReadyForInstallation() {
        try {
            final Map<String, String> propertiesMap = new HashMap<>();
            Files.readAllLines(Paths.get(USER_NEXIAL_INSTALL_HOME + "update.nx"))
                 .stream().filter(l -> l.length() > 0).map(l -> l.split("="))
                 .forEach(s -> propertiesMap.put(s[0], s[1]));
            return StringUtils.isNotEmpty(propertiesMap.get("updateLocation"));
        } catch (IOException e) {
            return false;
        }
    }

    private static void updateSilently() {
        try {
            Files.list(Paths.get(USER_PROJECTS_DIR))
                 .map(Path::toFile)
                 .filter(File::isDirectory)
                 .filter(NexialUpdate::isInstallDirectory)
                 .findFirst()
                 .ifPresent(f -> CompletableFuture.runAsync(() -> triggerUpdateCheckProcess(f)));
        } catch (IOException e) {
            ConsoleUtils.error("Unable to check for updates via nexial installer: " + e.getMessage());
        }
    }

    private static void triggerUpdateCheckProcess(File nexialInstallerDir) {
        final String nexialInstallerJar = nexialInstallerDir.getAbsolutePath() + REL_PATH_INSTALLER_JAR;
        final String command = "java -jar " + nexialInstallerJar + " SU";
        try {
            Runtime.getRuntime().exec(command);
        } catch (IOException e) {
            ConsoleUtils.error("Unable to check for updates via nexial installer: " + e.getMessage());
        }
    }

    private static void saveUpdatePreferenceChoice(boolean resume) {
        try {
            FileUtils.touch(Paths.get(USER_NEXIAL_HOME, "install", "update-now").toFile());
            if (resume) { FileUtils.touch(Paths.get(USER_NEXIAL_HOME, "install", "resume-after-update").toFile()); }
        } catch (IOException e) {
            ConsoleUtils.error("Could not set user preference for installing updates.");
        }
    }

    private static boolean isNexialInstallerOlder(String version) {
        if (StringUtils.isBlank(version)) { return true; }

        String[] versionNumbers = StringUtils.split(version, ".");
        if (ArrayUtils.isEmpty(versionNumbers)) { return true; }

        String[] minimumNumbers = NEXIAL_INSTALLER_MIN_VERSION.split("\\.");
        // just in case...
        if (ArrayUtils.isEmpty(minimumNumbers)) { return false; }

        try {
            long currentVersionNum = (NumberUtils.toInt(versionNumbers[0]) * 10000L) +
                                     (versionNumbers.length > 1 ? NumberUtils.toInt(versionNumbers[1]) + 100 : 0) +
                                     (versionNumbers.length > 2 ? NumberUtils.toInt(versionNumbers[2]) : 0);
            long minVersionNum = (NumberUtils.toInt(minimumNumbers[0]) * 10000L) +
                                 (minimumNumbers.length > 1 ? NumberUtils.toInt(minimumNumbers[1]) + 100 : 0) +
                                 (minimumNumbers.length > 2 ? NumberUtils.toInt(minimumNumbers[2]) : 0);
            return currentVersionNum < minVersionNum;
        } catch (Exception e) {
            return false;
        }
    }

    private static String promptUserForInstallation() {
        String promptMessage = "A new version of Nexial is ready to install. Please select a choice (timeout in "
                               + timeoutSeconds + " seconds):\n" +
                               "\t1. Install new updates & restart current execution\n" +
                               "\t2. Install new updates only\n" +
                               "\t3. Don't install now, maybe later";
        return ConsoleUtils.pauseForInput(null, promptMessage, "Install Latest Nexial", true);
    }

    private static boolean isInstallerPresent() {
        try {
            return Files.list(Paths.get(USER_PROJECTS_DIR))
                        .map(Path::toFile)
                        .filter(File::isDirectory)
                        .anyMatch(NexialUpdate::isInstallDirectory);
        } catch (IOException e) {
            ConsoleUtils.error("Unable to locate nexial installer: " + e.getMessage());
            return false;
        }
    }

    private static boolean isInstallDirectory(File f) { return f.getName().toLowerCase().matches(INSTALLER_DIR_NAME); }
}