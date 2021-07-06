package org.nexial.core.tools.appium

import org.apache.commons.cli.CommandLine
import org.apache.commons.cli.Options
import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.BooleanUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.SystemUtils
import org.apache.commons.lang3.SystemUtils.IS_OS_WINDOWS
import org.apache.commons.lang3.SystemUtils.JAVA_IO_TMPDIR
import org.nexial.commons.proc.ProcessInvoker.WORKING_DIRECTORY
import org.nexial.commons.proc.ProcessInvoker.invoke
import org.nexial.commons.utils.DateUtility
import org.nexial.commons.utils.EnvUtils
import org.nexial.commons.utils.FileUtil
import org.nexial.core.NexialConst.*
import org.nexial.core.NexialConst.Data.WIN32_CMD
import org.nexial.core.NexialConst.Mobile.Android.*
import org.nexial.core.plugins.ws.WebServiceClient
import org.nexial.core.tools.CliConst.OPT_VERBOSE
import org.nexial.core.tools.CliUtils
import org.nexial.core.tools.inspector.InspectorConst
import org.nexial.core.utils.ConsoleUtils
import java.io.File
import java.io.File.separator
import java.io.IOException
import java.util.*
import kotlin.system.exitProcess

object AndroidSetup {

    private val optionListSysImg = listOf("--sdk_root=$ANDROID_SDK_HOME", "--list")
    private val optionListInstalledSysImg = listOf("--sdk_root=$ANDROID_SDK_HOME", "--list_installed")
    private val optionInstall = listOf("--sdk_root=$ANDROID_SDK_HOME", "--install")
    private val optionInstallCommPkg = optionInstall.plus(listOf("\"extras;google;usb_driver\"",
                                                                 "\"extras;google;webdriver\"",
                                                                 "\"platform-tools\"",
                                                                 "\"emulator\"",
                                                                 "\"cmdline-tools;latest\"",
                                                                 "\"extras;intel;Hardware_Accelerated_Execution_Manager\"",
                                                                 "\"extras;google;Android_Emulator_Hypervisor_Driver\"",
                                                                 "\"platforms;android-30\"",
                                                                 "\"build-tools;30.0.3\""))
    private val optionRunEmulator = listOf(
        "orientation", "PORTRAIT",
        "camera-front", "Emulated",
        "camera-back", "virtualScene",
        "net-speed", "full",
        "net-latency", "none",
        "boot-option", "quick-boot",
        "multi-core", "4",
        "ram", "1536",
        "vm heap", "384mb",
        "internal-storage", "800mb",
        "sd-card", "512mb",
    )

    private var verbose = true
    private var override = true
    private var projectHome = ""

    @JvmStatic
    fun main(args: Array<String>) {
        deriveOptions(deriveCommandLine(args))

        // step 0: check existing ~/.nexial/android/sdk - ASK TO DELETE IT
        //  if yes, then delete recursively ~/.nexial/android/sdk
        if (!overrideExistingAndroidSDK()) return

        // step 1: download cmdlinetools.zip
        // step 2: unzip cmdlinetools.zip to %TEMP%/cmdline-tools
        println()
        val urlCmdlineTools = downloadRedirection(CMDLINE_TOOLS_REDIRECT_URL)
        val unzipped = downloadAndUnzip(urlCmdlineTools, File("$CMDLINE_TOOLS_PATH$separator.."))
                       ?: throw IOException("ERROR: Unable to unzip $urlCmdlineTools to $CMDLINE_TOOLS_PATH")
        unzipped.find { it.absolutePath.contains(AVD_MANAGER_REL_PATH) }
        ?: throw IOException("ERROR: Unable to find $AVD_MANAGER_REL_PATH from $CMDLINE_TOOLS_PATH")
        unzipped.find { it.absolutePath.contains(SDK_MANAGER_REL_PATH) }
        ?: throw IOException("ERROR: Unable to find $SDK_MANAGER_REL_PATH from $CMDLINE_TOOLS_PATH")

        // step 3: recreate ~/.nexial/android/sdk/license
        // step 4: copy pre-packaged files:
        //      unzip $DISTRO_URL_BASE/android_sdk_license.zip to ~/.nexial/android/sdk/license
        //      unzip $DISTRO_URL_BASE/android_skins.zip to ~/.nexial/android/sdk/skins
        println()
        downloadAndUnzip(ANDROID_SDK_LICENSE_ZIP_URL, File(LICENSE_PATH))

        println()
        downloadAndUnzip(ANDROID_SDK_SKINS_ZIP_URL, File(SKIN_PATH))

        // step 5: install command packages
        //  sdkmanager --sdk_root=~/.nexial/android/sdk --install "extras;google;usb_driver" "extras;google;webdriver"
        //      "platform-tools" "emulator" "cmdline-tools;latest" "extras;intel;Hardware_Accelerated_Execution_Manager"
        //      "extras;google;Android_Emulator_Hypervisor_Driver" "platforms;android-30" "build-tools;30.0.3"
        println()
        installCommonPackages(SDK_MANAGER)

        // step 6: show available system images for installation:
        println()
        installSystemImages(SDK_MANAGER)

        // step 7: copy $ANDROID_SDK_HOME/build-tools/x/lib/apksigner.jar to $ANDROID_SDK_HOME/tools/lib
        println()
        copyApkSigner()

        // step 8: install emulator
        //  avdmanager.bat list device -c
        println()
        val availableEmulators = retrieveAvailableEmulators(ANDROID_EMULATORS_URL)
        val installedSysImg = retrieveInstalledSysImg(SDK_MANAGER)
        installEmulator(availableEmulators, installedSysImg)

        // exitProcess(0)
    }

    private fun deriveCommandLine(args: Array<String>): CommandLine {
        val cmdOptions = Options()
        cmdOptions.addOption(OPT_VERBOSE)
        cmdOptions.addOption(CliUtils.newArgOption("t", "target", "[REQUIRED] The project home directory", true))
        cmdOptions.addOption(CliUtils.newArgOption("y", "override", "[optional] Always override", false))

        val programExt = if (SystemUtils.IS_OS_WINDOWS) ".cmd" else ".sh"
        val cmd = CliUtils.getCommandLine("android-setup$programExt", args, cmdOptions)
        if (cmd == null) {
            ConsoleUtils.error("unable to proceed... exiting")
            InspectorConst.exit(ExitStatus.RC_BAD_CLI_ARGS)
        }

        return cmd
    }

    private fun deriveOptions(cmd: CommandLine) {
        verbose = cmd.hasOption(OPT_VERBOSE.opt)
        override = cmd.hasOption("Y")
        val projectDir = cmd.getOptionValue("t")
        projectHome =
            if (IS_OS_WINDOWS) StringUtils.replace(projectDir, "/", "\\") else StringUtils.replace(projectDir, "\\",
                                                                                                   "/")
    }

    private fun retrieveInstalledSysImg(sdkManager: String): List<String> {
        val envRuntime = mapOf(WORKING_DIRECTORY to StringUtils.substringAfterLast(sdkManager, separator))
        val installedPackages = invoke(sdkManager, optionListInstalledSysImg, envRuntime)
        if (StringUtils.isNotBlank(installedPackages.stderr)) {
            System.err.println(installedPackages.stderr)
            exitProcess(-1)
        }

        return StringUtils.split(installedPackages.stdout, "\n")
            .filter { StringUtils.startsWith(StringUtils.trim(it), SYSTEM_IMAGES_PREFIX) }
            .map { StringUtils.substringBefore(StringUtils.trim(it), " ") }
            .sorted()
    }

    private fun overrideExistingAndroidSDK(): Boolean {
        if (FileUtil.isDirectoryReadWritable(ANDROID_SDK_HOME)) {
            // existing Android SDK exist
            print("An existing Android SDK is detected at $ANDROID_SDK_HOME.\nDo you want to install over it? ")
            val input = Scanner(System.`in`).nextLine()
            if (!BooleanUtils.toBoolean(input)) {
                log("Existing Android SDK found at $ANDROID_SDK_HOME; Setup cancelled.\n\n")
                return false
            }

            // DON'T DELETE... LET'S SEE IF ANDROID SDK WOULD SUPPORT UPDATES OVER EXISTING INSTALLATION
            // delete as user intended
            // log("Deleting existing Android SDK at $ANDROID_SDK_HOME")
            // FileUtils.deleteDirectory(File(ANDROID_SDK_HOME))
            return true
        }

        log("Creating Android SDK directory at $ANDROID_SDK_HOME")
        File(ANDROID_SDK_HOME).mkdirs()
        return true
    }

    private fun downloadRedirection(downloadUrl: String): String {
        val redirect = WebServiceClient(null)
            .configureAsQuiet()
            .disableContextConfiguration()
            .get(downloadUrl, null)
        if (redirect.returnCode != 200) {
            System.err.println("ERROR: Unable to read from $downloadUrl: " +
                               "${redirect.returnCode} ${redirect.statusText}\n" +
                               redirect.body)
            exitProcess(-1)
        }

        return StringUtils.trim(redirect.body)
    }

    private fun downloadAndUnzip(downloadUrl: String, unzipLocation: File): MutableList<File>? {
        val saveTo = JAVA_IO_TMPDIR + StringUtils.substringAfterLast(downloadUrl, "/")
        log("downloading from $downloadUrl...")
        val downloadResp = WebServiceClient(null)
            .configureAsQuiet()
            .disableContextConfiguration()
            .download(downloadUrl, null, saveTo)
        if (downloadResp.returnCode != 200) {
            System.err.println("ERROR: Unable to download from $downloadUrl: " +
                               "${downloadResp.returnCode} ${downloadResp.statusText}\n" +
                               downloadResp.body)
            exitProcess(-1)
        }

        log("downloaded to $saveTo")

        val downloaded = downloadResp.payloadLocation
        if (StringUtils.substringAfterLast(downloaded, ".").toLowerCase() != "zip") {
            System.err.println("ERROR: File downloaded from $downloadUrl is not a ZIP file as expected!")
            exitProcess(-1)
        }

        FileUtils.deleteDirectory(unzipLocation)
        unzipLocation.mkdirs()
        val unzipped = FileUtil.unzip(File(downloaded), unzipLocation)
        log("unzipped $downloaded to $unzipLocation")
        return unzipped
    }

    private fun installCommonPackages(sdkManager: String) {
        val envRuntime = mapOf(WORKING_DIRECTORY to StringUtils.substringAfterLast(sdkManager, separator))
        val outcome = invoke(sdkManager, optionInstallCommPkg, envRuntime)
        log(outcome.stdout + "\n")

        val runtimeError = outcome.stderr
        if (StringUtils.isNotBlank(runtimeError)) {
            System.err.println(runtimeError)
            exitProcess(-1)
        }
    }

    private fun installSystemImages(sdkManager: String) {
        val sdkManagerPath = StringUtils.substringBeforeLast(sdkManager, separator)
        val envRuntime = mapOf(WORKING_DIRECTORY to sdkManagerPath)

        val listPackages = invoke(sdkManager, optionListSysImg, envRuntime)

        val sysImages = StringUtils.split(listPackages.stdout, "\n")
            .filter { StringUtils.startsWith(StringUtils.trim(it), SYSTEM_IMAGES_PREFIX) }
            .map { StringUtils.substringBefore(StringUtils.trim(it), " ") }
            .sorted()

        val arch = EnvUtils.getOsArchBit()
        val defSysImg = if (arch == 64 && sysImages.contains(DEF_SYS_IMG_64)) DEF_SYS_IMG_64 else DEF_SYS_IMG_32

        print(StringUtils.repeat("-", 80) + "\n" +
              "Available System Images\n" +
              StringUtils.repeat("-", 80) + "\n")
        sysImages.forEach { println(it) }

        var stop = false
        do {
            print("\nEnter the system image to install (default: $defSysImg), QUIT to end: ")
            var userSysImg = Scanner(System.`in`).nextLine()
            if (StringUtils.equalsIgnoreCase(userSysImg, "QUIT"))
                stop = true
            else {
                if (StringUtils.isBlank(userSysImg)) userSysImg = defSysImg
                if (!sysImages.contains(userSysImg))
                    println("ERROR: Unknown system images specified - $userSysImg\n")
                else {
                    val outcome = invoke(sdkManager, optionInstall.plus(userSysImg), envRuntime)
                    log(outcome.stdout + "\n")

                    if (StringUtils.isNotBlank(outcome.stderr)) {
                        System.err.println(outcome.stderr)
                        exitProcess(-1)
                    }
                }
            }
        } while (!stop)
    }

    private fun copyApkSigner() {
        val apksignerFiles = FileUtil.listFiles(BUILD_TOOLS_PATH, APK_SIGNER_FILE, true)
        if (CollectionUtils.isEmpty(apksignerFiles)) {
            System.err.println("ERROR: Unable to find $APK_SIGNER_FILE under $BUILD_TOOLS_PATH")
            exitProcess(-1)
        }

        FileUtils.copyFile(apksignerFiles[0], File(APK_SIGNER_DEST))
        log("copied $APK_SIGNER_FILE to $APK_SIGNER_DEST")
    }

    private fun retrieveAvailableEmulators(url: String): AvailableEmulators {
        val response = WebServiceClient(null).configureAsQuiet().disableContextConfiguration().get(url, null)
        if (response.returnCode != 200) {
            System.err.println("ERROR: Unable to download from $url: " +
                               "${response.returnCode} ${response.statusText}\n" +
                               response.body)
            exitProcess(-1)
        }

        return GSON.fromJson(response.body, AvailableEmulators::class.java)
    }

    private fun showAvailableEmulators(availableEmulators: AvailableEmulators) {
        val banner = StringUtils.repeat("=", 80) + "\n"
        val banner2 = StringUtils.repeat("-", 80)
        availableEmulators.vendors.sortedBy { it.name }.forEach {
            print(banner + it.name + "\n" + banner)
            println(StringUtils.rightPad("[id]", 20) + " " +
                    StringUtils.rightPad("[name]", 20) + " " +
                    StringUtils.rightPad("[display]", 12) + " " +
                    StringUtils.rightPad("[resolution]", 12))
            println(banner2)
            it.products.sortedBy { it.id }.forEach {
                println(
                    StringUtils.rightPad(it.id, 21) +
                    StringUtils.rightPad(it.name, 21) +
                    StringUtils.rightPad(it.display, 13) +
                    StringUtils.rightPad(it.resolution, 13)
                )
            }
            println()
        }
    }

    private fun installEmulator(availableEmulators: AvailableEmulators, installedSysImg: List<String>) {

        val allEmulators = availableEmulators.vendors.map { it.products }.flatten()

        showAvailableEmulators(availableEmulators)
        val envRuntime = mapOf(WORKING_DIRECTORY to StringUtils.substringAfterLast(SDK_MANAGER, separator))

        var stop = false

        do {
            print("\nEnter the emulator id to install it, or QUIT to end: ")
            val userInput = Scanner(System.`in`).nextLine()
            if (StringUtils.equalsIgnoreCase(userInput, "QUIT"))
                stop = true
            else {
                if (StringUtils.isBlank(userInput))
                    println("ERROR: No valid input found\n")
                else {
                    val target = allEmulators.find { it.id == userInput }
                    if (target == null)
                        println("ERROR: Unrecognized emulator id specified - $userInput\n")
                    else {
                        val userSysImg = selectEmulatorSysImg(installedSysImg)
                        if (userSysImg == "")
                            stop = true
                        else {
                            val createOutcome = invoke(
                                WIN32_CMD,
                                listOf("/C", "echo.|$AVD_MANAGER", "create", "avd", "-n", userInput, "-k",
                                       "\"" + userSysImg + "\""),
                                envRuntime)
                            if (StringUtils.isNotBlank(createOutcome.stderr)) {
                                println("ERROR " + createOutcome.stderr + "\n")
                                exitProcess(-1)
                            }

                            val targetBatchFile = StringUtils.appendIfMissing(projectHome, separator) +
                                                  "artifact${separator}bin${separator}run-emulator-$userInput.bat"
                            val emulatorBatchFile =
                                "@echo off\n" +
                                "setlocal enableextensions\n" +
                                "set ANDROID_SDK_ROOT=%USERPROFILE%\\.nexial\\$SDK_REL_PATH\n" +
                                "cd %ANDROID_SDK_ROOT%\\emulator\n" +
                                "emulator -avd $userInput -skindir %ANDROID_SDK_ROOT%\\skins -skin ${target.skin} -memory 1536 -ranchu -phone-number 2136394251 -allow-host-audio\n" +
                                "endlocal\n" +
                                ":end\n"
                            FileUtils.write(File(targetBatchFile), emulatorBatchFile, DEF_CHARSET)
                            log("emulator batch file created: $targetBatchFile")
                        }
                    }
                }

            }

        } while (!stop)
    }

    private fun selectEmulatorSysImg(installedSysImg: List<String>): String {
        println("Installed System Images:")
        installedSysImg.forEach { println(it) }
        println()
        print("Enter the system image to use for this emulator, or QUIT to end: ")

        var userSysImg = ""
        var stop = false
        do {
            userSysImg = Scanner(System.`in`).nextLine()
            if (StringUtils.equalsIgnoreCase(userSysImg, "QUIT")) {
                userSysImg = ""
                stop = true
            } else {
                if (!installedSysImg.contains(userSysImg))
                    println("ERROR: Invalid system image - $userSysImg")
                else
                    stop = true
            }
        } while (!stop)

        return userSysImg
    }

    private fun log(message: String) {
        if (verbose) println("${DateUtility.getCurrentTimeForLogging()} >> $message")
    }
}

data class EmulatorConfig(val id: String, val name: String, val display: String, val resolution: String,
                          val skin: String)

data class EmulatorVendor(val name: String, val products: List<EmulatorConfig>)

data class AvailableEmulators(val vendors: List<EmulatorVendor>)

