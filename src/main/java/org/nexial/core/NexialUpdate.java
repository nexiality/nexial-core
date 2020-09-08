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
                            ConsoleUtils.log("Install & Restart.");
                            saveUpdatePreferenceChoice(true);
                            System.exit(0);
                            // return; // exit the program
                        } else if (choice == 2) {
                            ConsoleUtils.log("Install only.");
                            saveUpdatePreferenceChoice(false);
                            System.exit(0);
                            // return; // exit the program
                        } else if (choice == 3) {
                            ConsoleUtils.log("Ignore this time, ask me next time.");
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
            ConsoleUtils.log("Nexial Installer is Present");
        } else {
            ConsoleUtils.log("Nexial Installer is not Present. Downloading..");
            try {
                // CompletableFuture
                //     .supplyAsync(NexialUpdate::downloadNexialInstaller)
                //     .thenAccept(NexialUpdate::installNexialInstaller)
                //     .get();

                String val = downloadNexialInstaller();
                installNexialInstaller(val);

            } catch (Exception e) {
                ConsoleUtils.log("Failed to download Nexial-Installer. Reason: " + e.getMessage());
            }
        }
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
        ConsoleUtils.log("Calling command " + command);
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
            ConsoleUtils.error("Cloud not load status of last update check. Reason: " + e.getMessage());
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
        //String userHomeProject = IS_OS_WINDOWS ? "C:\\projects" : USER_HOME +"/projects";
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