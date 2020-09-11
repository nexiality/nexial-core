package org.nexial.core;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.nexial.commons.utils.FileUtil;
import org.nexial.core.utils.ConsoleUtils;
import org.nexial.core.utils.ExecUtils;

import static java.io.File.separator;
import static org.nexial.core.NexialConst.DEF_CHARSET;
import static org.nexial.core.NexialConst.Project.*;
import static org.nexial.core.utils.ConsoleUtils.centerPrompt;

public class NexialUpdate {

    private static final String REL_PATH_INSTALLER_JAR = separator + "lib" + separator + "nexial-installer.jar";
    private static final String INSTALLER_DIR_NAME = "nexial-installer";

    public static void checkAndRun() {
        if (ExecUtils.isRunningInZeroTouchEnv()) { return; }

        installNexialInstallerIfNotPresent();

        if (isUpdateReadyForInstallation()) {
            while (true) {
                try {
                    final int choice = Integer.parseInt(StringUtils.trim(promptUserForInstallation()));
                    switch (choice) {
                        case 1: {
                            saveUpdatePreferenceChoice(true);
                            System.exit(0);
                        }
                        case 2: {
                            saveUpdatePreferenceChoice(false);
                            System.exit(0);
                        }
                        case 3:
                            return;
                        default:
                            System.out.println("Please choose valid option.\n");
                    }
                } catch (NumberFormatException nfe) {
                    System.out.println("Please choose valid option.\n");
                }
            }
        }

        updateSilently();
    }

    public static void installNexialInstallerIfNotPresent() {
        if (isInstallerPresent()) {
            String nexialInstallerVersion = getNexialInstallerVersion();
            if (isNexialInstallerOlder(nexialInstallerVersion)) { showNexialInstallerBanner(true); }
        } else {
            showNexialInstallerBanner(false);
            /*try {
                String version = downloadNexialInstaller();
                installNexialInstaller(version);
            } catch (Exception e) {
                ConsoleUtils.log("Failed to download Nexial-Installer. Reason: " + e.getMessage());
            }*/
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
            long currentVersionNum = (NumberUtils.toInt(versionNumbers[0]) * 10000) +
                                     (versionNumbers.length > 1 ? NumberUtils.toInt(versionNumbers[1]) + 100 : 0) +
                                     (versionNumbers.length > 2 ? NumberUtils.toInt(versionNumbers[2]) : 0);
            long minVersionNum = (NumberUtils.toInt(minimumNumbers[0]) * 10000) +
                                 (minimumNumbers.length > 1 ? NumberUtils.toInt(minimumNumbers[1]) + 100 : 0) +
                                 (minimumNumbers.length > 2 ? NumberUtils.toInt(minimumNumbers[2]) : 0);
            return currentVersionNum < minVersionNum;
        } catch (Exception e) {
            return false;
        }
    }

    private static String promptUserForInstallation() {
        String promptMessage = "A new version of Nexial is ready to install. Please select a choice:\n" +
                               "\t1. Install new updates & restart current execution\n" +
                               "\t2. Install new updates only\n" +
                               "\t3. Don't install now, maybe later";
        try {
            return ConsoleUtils.pauseForInput(null, promptMessage, "Install Latest Nexial");
        } catch (InterruptedException e) {
            ConsoleUtils.error("No user input available. Proceed the execution...");
            return "3";
        }
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

    private static boolean isInstallDirectory(File f) { return f.getName().matches(INSTALLER_DIR_NAME); }

    // private static String downloadNexialInstaller() {
    //
    //     final String installUrl = "https://api.github.com/repos/nexiality/nexial-installer/releases?prerelease=true";
    //
    //     try (InputStream is = new URL(installUrl).openStream()) {
    //         BufferedReader br = new BufferedReader(new InputStreamReader(is));
    //         StringBuilder jsonString = new StringBuilder();
    //         while (true) {
    //             Optional<String> chunk = Optional.ofNullable(br.readLine());
    //             if (chunk.isPresent()) {
    //                 jsonString.append(chunk.get());
    //             } else { break; }
    //         }
    //
    //         String downloadUrl = GSON.fromJson(jsonString.toString(), JsonArray.class)
    //                                  .get(0).getAsJsonObject()
    //                                  .get("assets").getAsJsonArray()
    //                                  .get(0).getAsJsonObject()
    //                                  .get("browser_download_url").getAsString();
    //
    //         String[] pathToken = downloadUrl.split("/");
    //
    //         String installerName = pathToken[pathToken.length - 1];
    //
    //         String NEXIAL_INSTALLER_PATH = USER_PROJECTS_DIR + "nexial-installer" + separator + installerName;
    //
    //         // Checking If The File Exists At The Specified Location Or Not
    //         Path filePathObj = Paths.get(NEXIAL_INSTALLER_PATH);
    //         if (Files.notExists(filePathObj)) {
    //             try {
    //                 FileUtils.forceMkdir(new File(NEXIAL_INSTALLER_PATH).getParentFile());
    //                 Files.createFile(Paths.get(NEXIAL_INSTALLER_PATH));
    //             } catch (IOException e) {
    //                 ConsoleUtils.error("Could not get response. Reason: " + e.getMessage());
    //             }
    //         }
    //
    //         try (ReadableByteChannel rbcObj = Channels.newChannel(new URL(downloadUrl).openStream());
    //              FileOutputStream fOutStream = new FileOutputStream(NEXIAL_INSTALLER_PATH)) {
    //             fOutStream.getChannel().transferFrom(rbcObj, 0, Long.MAX_VALUE);
    //             ConsoleUtils.log("File Downloaded");
    //             return installerName;
    //         } catch (IOException e) {
    //             ConsoleUtils.error("Could not download installer. Reason: " + e.getMessage());
    //         }
    //     } catch (IOException e) {
    //         ConsoleUtils.error("Could not get response. Reason: " + e.getMessage());
    //     }
    //     return null;
    // }

    // public static void installNexialInstaller(String zipfileName) {
    //     String NEXIAL_INSTALLER_PATH = USER_PROJECTS_DIR + "nexial-installer" + separator;
    //     String fileZip = NEXIAL_INSTALLER_PATH + zipfileName;
    //     Path destDirStr = Paths.get(NEXIAL_INSTALLER_PATH);
    //
    //     try {
    //         FileUtil.unzip(new File(fileZip), destDirStr.toFile());
    //         Files.list(Paths.get(String.valueOf(destDirStr), "bin"))
    //              .map(Path::toFile).forEach(f -> f.setExecutable(true));
    //     } catch (IOException e) {
    //         e.printStackTrace();
    //     } finally {
    //         // delete installer.zip
    //         new File(fileZip).delete();
    //     }
    // }

    // public static String getVersion(String nexialHome) {
    //     try {
    //         return new String(Files.readAllBytes(Paths.get(nexialHome + separator + "version.txt")));
    //     } catch (IOException e) {
    //         return "UNKNOWN";
    //     }
    // }

}