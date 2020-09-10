package org.nexial.core;

import java.io.*;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.utils.FileUtil;
import org.nexial.core.utils.ConsoleUtils;
import org.nexial.core.utils.ExecUtils;

import com.google.gson.JsonArray;

import static java.io.File.separator;
import static org.nexial.core.NexialConst.GSON;
import static org.nexial.core.NexialConst.Project.*;
import static org.nexial.core.utils.ConsoleUtils.centerPrompt;

public class NexialUpdate {

    public static void checkAndrun() {
        if (!ExecUtils.isRunningInZeroTouchEnv()) {
            installNexialInstallerIfNotPresent();

            if (isUpdateReadyForInstallation()) {
                boolean shouldPrompt = true;
                while (shouldPrompt) {
                    try {
                        final int choice = Integer.parseInt(promptUserForInstallation());
                        if (choice > 0 && choice < 4) { shouldPrompt = false; }
                        if (choice == 1) {
                            saveUpdatePreferenceChoice(true);
                            System.exit(0);
                        } else if (choice == 2) {
                            saveUpdatePreferenceChoice(false);
                            System.exit(0);
                        } else if (choice == 3) {
                            return;
                        } else { throw new NumberFormatException(); }
                    } catch (NumberFormatException nfe) {
                        ConsoleUtils.error("Please choose valid option.");
                    }
                }
            }
            updateSilently();
        }
    }

    public static void installNexialInstallerIfNotPresent() {
        ConsoleUtils.log("Nexial Current Version: '" + getVersion(System.getProperty(NEXIAL_HOME)) + "'");

        if (isInstallerPresent()) {
            String nexialInstallerVersion = getNexialInstallerVersion();
            if (StringUtils.isBlank(nexialInstallerVersion)) {
                ConsoleUtils.log("Current Nexial Installer version is: " + nexialInstallerVersion);
            }
            if (isNexailInstallerOlder(nexialInstallerVersion)) {
                showNexialInstallerBanner(true);
            }
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

    ;

    public static void showNexialInstallerBanner(boolean isUpdate) {
        String message =
            isUpdate ? "Looks like you have an outdated version of Nexial Installer. Please install the latest" :
            "Looks like Nexial Installer is not found on your system. Please install the latest";
        System.err
            .println("/----------------------------------------------------------------------------------------\\");
        System.err.println("|" + centerPrompt("MISSING NEW VERSION OF NEXIAL-INSTALLER", 88) + "|");
        System.err
            .println("|----------------------------------------------------------------------------------------|");
        System.err.println("|" + centerPrompt(message, 88) + "|");
        System.err
            .println("\\----------------------------------------------------------------------------------------/");
    }

    public static String getNexialInstallerVersion() {
        Path nexialInstallerFingerprint = Paths.get(USER_PROJECTS_DIR, "nexial-installer", "version.txt");
        try {
            return new String(Files.readAllBytes(nexialInstallerFingerprint)).trim();
        } catch (Exception ignored) {
            return null;
        }
    }

    public static boolean isUpdateReadyForInstallation() {
        try {
            final Map<String, String> propertiesMap = new HashMap<>();
            Files.readAllLines(Paths.get(USER_NEXIAL_INSTALL_HOME + "update.nx"))
                 .stream().filter(l -> l.length() > 0).map(l -> l.split("="))
                 .forEach(s -> propertiesMap.put(s[0], s[1]));
            if (StringUtils.isNotEmpty(propertiesMap.get("updateLocation"))) {
                return true;
            }
        } catch (IOException e) {
            ConsoleUtils.log("Last update status not found.");
        }
        return false;
    }

    public static void updateSilently() {
        try {
            Files.list(Paths.get(USER_PROJECTS_DIR))
                 .map(Path::toFile)
                 .filter(File::isDirectory)
                 .filter(NexialUpdate::isInstallDirectory)
                 .findFirst()
                 .ifPresent(f -> CompletableFuture.runAsync(() -> triggerUpdateCheckProcess(f)));
        } catch (IOException e) {
            ConsoleUtils.error("Could not call nexial-installer to check for updates. Reason: " + e.getMessage());
        }
    }

    public static void triggerUpdateCheckProcess(File nexialInstallerDir) {
        final String nexialInstallerJar = nexialInstallerDir.getAbsolutePath() + separator + "lib"
                                          + separator + "nexial-installer.jar";
        final String command = "java -jar " + nexialInstallerJar + " SU";
        try {
            Runtime.getRuntime().exec(command);
        } catch (IOException e) {
            ConsoleUtils.error("Could not call nexial-installer to check for updates. Reason: " + e.getMessage());
        }
    }

    public static void saveUpdatePreferenceChoice(boolean resume) {
        try {
            FileUtils.touch(Paths.get(USER_NEXIAL_HOME, "install", "update-now").toFile());
            if (resume) { FileUtils.touch(Paths.get(USER_NEXIAL_HOME, "install", "resume-after-update").toFile()); }
        } catch (IOException e) {
            ConsoleUtils.error("Could not set user preference for installing updates.");
        }
    }

    private static final boolean isNexailInstallerOlder(String version) {
        String[] versionNumbers = version.split("v")[1].split("\\.");
        String[] minimumNumbers = NEXIAL_INSTALLER_MIN_VERSION.split("\\.");

        for (int i = 0; i < versionNumbers.length; i++) {
            if (Integer.parseInt(minimumNumbers[i]) > Integer.parseInt(versionNumbers[i])) { return true; }
        }
        return false;
    }

    public static String promptUserForInstallation() {
        String promptMessage = "A new version of Nexial-Core is ready to install.\n" +
                               "Please select a choice: \n" +
                               "\t 1. Install new updates & Restart current execution\n" +
                               "\t 2. Install new updates only\n" +
                               "\t 3. Don't install now, maybe later";
        try {
            return ConsoleUtils.pauseForInput(null, promptMessage, "Install Latest Version");
        } catch (InterruptedException e) {
            ConsoleUtils.error("Could not fetch user-input. Proceeding the execution.");
            return "3";
        }
    }

    public static String downloadNexialInstaller() {

        final String installUrl = "https://api.github.com/repos/nexiality/nexial-installer/releases?prerelease=true";

        try (InputStream is = new URL(installUrl).openStream()) {
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            StringBuilder jsonString = new StringBuilder();
            while (true) {
                Optional<String> chunk = Optional.ofNullable(br.readLine());
                if (chunk.isPresent()) {
                    jsonString.append(chunk.get());
                } else { break; }
            }

            String downloadUrl = GSON.fromJson(jsonString.toString(), JsonArray.class)
                                     .get(0).getAsJsonObject()
                                     .get("assets").getAsJsonArray()
                                     .get(0).getAsJsonObject()
                                     .get("browser_download_url").getAsString();

            String[] pathToken = downloadUrl.split("/");

            String installerName = pathToken[pathToken.length - 1];

            String NEXIAL_INSTALLER_PATH = USER_PROJECTS_DIR + "nexial-installer" + separator + installerName;

            // Checking If The File Exists At The Specified Location Or Not
            Path filePathObj = Paths.get(NEXIAL_INSTALLER_PATH);
            if (Files.notExists(filePathObj)) {
                try {
                    FileUtils.forceMkdir(new File(NEXIAL_INSTALLER_PATH).getParentFile());
                    Files.createFile(Paths.get(NEXIAL_INSTALLER_PATH));
                } catch (IOException e) {
                    ConsoleUtils.error("Could not get response. Reason: " + e.getMessage());
                }
            }

            try (ReadableByteChannel rbcObj = Channels.newChannel(new URL(downloadUrl).openStream());
                 FileOutputStream fOutStream = new FileOutputStream(NEXIAL_INSTALLER_PATH)) {
                fOutStream.getChannel().transferFrom(rbcObj, 0, Long.MAX_VALUE);
                ConsoleUtils.log("File Downloaded");
                return installerName;
            } catch (IOException e) {
                ConsoleUtils.error("Could not download installer. Reason: " + e.getMessage());
            }
        } catch (IOException e) {
            ConsoleUtils.error("Could not get response. Reason: " + e.getMessage());
        }
        return null;
    }

    public static boolean isInstallerPresent() {
        try {
            return Files.list(Paths.get(USER_PROJECTS_DIR))
                        .map(Path::toFile)
                        .filter(File::isDirectory)
                        .anyMatch(NexialUpdate::isInstallDirectory);
        } catch (IOException e) {
            ConsoleUtils.error("Unable to locate installer. Reason: " + e.getMessage());
            return false;
        }
    }

    public static void installNexialInstaller(String zipfileName) {
        String NEXIAL_INSTALLER_PATH = USER_PROJECTS_DIR + "nexial-installer" + separator;
        String fileZip = NEXIAL_INSTALLER_PATH + zipfileName;
        Path destDirStr = Paths.get(NEXIAL_INSTALLER_PATH);

        try {
            FileUtil.unzip(new File(fileZip), destDirStr.toFile());
            Files.list(Paths.get(String.valueOf(destDirStr), "bin"))
                 .map(Path::toFile).forEach(f -> f.setExecutable(true));
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // delete installer.zip
            new File(fileZip).delete();
        }
    }

    public static String getVersion(String nexialHome) {
        try {
            return new String(Files.readAllBytes(Paths.get(nexialHome + separator + "version.txt")));
        } catch (IOException e) {
            return "UNKNOWN";
        }
    }

    public static boolean isInstallDirectory(File f) {
        return f.getName().matches("nexial-installer");
    }

}