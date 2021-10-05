package org.nexial.core.tools.appium

import org.apache.commons.cli.CommandLine
import org.apache.commons.cli.Options
import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.io.FileUtils
import org.apache.commons.io.filefilter.DirectoryFileFilter.DIRECTORY
import org.apache.commons.io.filefilter.TrueFileFilter
import org.apache.commons.lang3.BooleanUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.SystemUtils.IS_OS_WINDOWS
import org.apache.commons.lang3.SystemUtils.JAVA_IO_TMPDIR
import org.nexial.commons.proc.ProcessInvoker
import org.nexial.commons.proc.ProcessInvoker.WORKING_DIRECTORY
import org.nexial.commons.utils.*
import org.nexial.core.NexialConst.DEF_CHARSET
import org.nexial.core.NexialConst.Data.WIN32_CMD
import org.nexial.core.NexialConst.ExitStatus.RC_BAD_CLI_ARGS
import org.nexial.core.NexialConst.GSON
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

    private const val signature = "Nexial Android Setup Helper"
    private const val cmdQuit = "QUIT"

    private val border1 = StringUtils.repeat("=", 100)
    private val border2 = StringUtils.repeat("-", 100)

    private val arch = EnvUtils.getOsArchBit()
    private val batchExt = if (IS_OS_WINDOWS) ".cmd" else ".sh"

    private const val resourceRoot = "/org/nexial/core/tools/appium/"
    private val runEmuTemplateCmd = ResourceUtils.loadResource("${resourceRoot}run-android-emulator.cmd")
    private val runEmuTemplateSh = ResourceUtils.loadResource("${resourceRoot}run-android-emulator.sh")

    private val projectBinRelPath = "artifact${separator}bin${separator}"
    private const val runEmuBatchPrefix = "run-android-emulator-"
    private val runEmuPrefix = "${projectBinRelPath}$runEmuBatchPrefix"

    private val optionListSysImg = listOf("--sdk_root=$ANDROID_SDK_HOME", "--list")
    private val optionListInstalledSysImg = listOf("--sdk_root=$ANDROID_SDK_HOME", "--list_installed")
    private val optionInstall = listOf("--sdk_root=$ANDROID_SDK_HOME", "--install")
    private val optionInstallCommPkg = optionInstall.plus(
        listOf("build-tools;30.0.3",
               "cmdline-tools;latest",
               "platform-tools", "platforms;android-30",
               "extras;google;webdriver", "extras;intel;Hardware_Accelerated_Execution_Manager"
        ).plus(
            if (IS_OS_WINDOWS)
                listOf("extras;google;usb_driver", "extras;google;Android_Emulator_Hypervisor_Driver")
            else
                listOf()
        ).map { if (IS_OS_WINDOWS) "\"$it\"" else it })


    private var defAvd = "Pixel_04a"
    private var defSysImg = if (arch == 64) DEF_SYS_IMG_64 else DEF_SYS_IMG_32
    private var verbose = true
    private var alwaysOverride = true
    private var projectHome = ""

    @JvmStatic
    fun main(args: Array<String>) {
        printBanner()

        deriveOptions(deriveCommandLine(args))

        // step 0: check existing ~/.nexial/android/sdk - ASK TO DELETE IT
        //  if yes, then delete recursively ~/.nexial/android/sdk
        println()
        if (!overrideExistingAndroidSDK()) return

        // step 1: download cmdlinetools.zip
        // step 2: unzip cmdlinetools.zip to %TEMP%/cmdline-tools
        log("\ninstalling Android SDK CommandLine Tools...")
        val urlCmdlineTools = downloadRedirection(CMDLINE_TOOLS_REDIRECT_URL)
        val unzipped = downloadAndUnzip(urlCmdlineTools, File("$CMDLINE_TOOLS_PATH$separator..").absoluteFile)
                       ?: throw IOException("ERROR: Unable to unzip $urlCmdlineTools to $CMDLINE_TOOLS_PATH")
        unzipped.find { it.absolutePath.contains(AVD_MANAGER_REL_PATH) }
        ?: throw IOException("ERROR: Unable to find $AVD_MANAGER_REL_PATH from $CMDLINE_TOOLS_PATH")
        unzipped.find { it.absolutePath.contains(SDK_MANAGER_REL_PATH) }
        ?: throw IOException("ERROR: Unable to find $SDK_MANAGER_REL_PATH from $CMDLINE_TOOLS_PATH")

        // step 3: recreate ~/.nexial/android/sdk/license
        // step 4: copy pre-packaged files:
        //      unzip $DISTRO_URL_BASE/android_sdk_license.zip to ~/.nexial/android/sdk/license
        //      unzip $DISTRO_URL_BASE/android_skins.zip to ~/.nexial/android/sdk/skins
        log("\ninstalling pre-accepted license agreements (and simplified installation process)...")
        downloadAndUnzip(ANDROID_SDK_LICENSE_ZIP_URL, File(LICENSE_PATH))

        log("\ninstalling pre-packaged Android emulator skins...")
        downloadAndUnzip(ANDROID_SDK_SKINS_ZIP_URL, File(SKIN_PATH))

        // before executing batch/shell scripts, let's make sure they are executable
        FileUtils.listFiles(File(CMDLINE_TOOLS_PATH + separator + "bin"), TrueFileFilter.TRUE, DIRECTORY)
            .forEach { file: File -> file.setExecutable(true, false) }

        // step 5: install command packages
        //  sdkmanager --sdk_root=~/.nexial/android/sdk --install "extras;google;usb_driver" "extras;google;webdriver"
        //      "platform-tools" "emulator" "cmdline-tools;latest" "extras;intel;Hardware_Accelerated_Execution_Manager"
        //      "extras;google;Android_Emulator_Hypervisor_Driver" "platforms;android-30" "build-tools;30.0.3"
        log("\ninstalling common Android SDK packages...")
        installCommonPackages(SDK_MANAGER)

        // step 6: show available system images for installation:
        log("\ninstalling Android SDK system images...")
        installSystemImages(SDK_MANAGER)

        // step 7: copy $ANDROID_SDK_HOME/build-tools/x/lib/apksigner.jar to $ANDROID_SDK_HOME/tools/lib
        log("\nsetting up apksigner...")
        copyApkSigner()

        // step 7: install emulator
        //  avdmanager.bat list device -c
        log("\ninstalling Android emulators...")
        val availableEmulators = retrieveAvailableEmulators(ANDROID_EMULATORS_URL)
        val installedSysImg = retrieveInstalledSysImg(SDK_MANAGER)
        installEmulator(availableEmulators, installedSysImg)

        log("\ninstallation complete")
    }

    private fun printBanner() {
        println()
        println()
        println("/${border2}\\")
        println("|${ConsoleUtils.centerPrompt(signature, 100)}|")
        println("\\${border2}/")
        println()
    }

    private fun deriveCommandLine(args: Array<String>): CommandLine {
        val cmdOptions = Options()
        cmdOptions.addOption(CliUtils.newArgOption("t", "target", "[REQUIRED] The project home directory", true))
        cmdOptions.addOption(OPT_VERBOSE)
        cmdOptions.addOption(CliUtils.newNonArgOption("y", "override", "Always override", false))

        val cmd = CliUtils.getCommandLine("android-setup$batchExt", args, cmdOptions)
        if (cmd == null) {
            ConsoleUtils.error("unable to proceed; exiting")
            InspectorConst.exit(RC_BAD_CLI_ARGS)
        }

        return cmd
    }

    private fun deriveOptions(cmd: CommandLine) {
        verbose = cmd.hasOption(OPT_VERBOSE.opt)
        alwaysOverride = cmd.hasOption("y")

        val projectDir = cmd.getOptionValue("t")
        projectHome =
            if (IS_OS_WINDOWS) StringUtils.replace(projectDir, "/", "\\")
            else StringUtils.replace(projectDir, "\\", "/")
    }

    private fun retrieveInstalledSysImg(sdkManager: String): List<String> {
        val envRuntime = mapOf(WORKING_DIRECTORY to StringUtils.substringAfterLast(sdkManager, separator))
        val installedPackages = ProcessInvoker.invoke(sdkManager, optionListInstalledSysImg, envRuntime)
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
            if (alwaysOverride) {
                verbose("(ALWAYS OVERRIDE): overriding existing Android SDK detected at $ANDROID_SDK_HOME.")
            } else {
                // existing Android SDK exist
                print("An existing Android SDK is detected at $ANDROID_SDK_HOME.\nDo you want to install over it? ")
                val input = Scanner(System.`in`).nextLine()
                if (!BooleanUtils.toBoolean(input)) {
                    verbose("\nexisting Android SDK found at $ANDROID_SDK_HOME; Setup cancelled.\n\n")
                    return false
                }
            }

            // DON'T DELETE... LET'S SEE IF ANDROID SDK WOULD SUPPORT UPDATES OVER EXISTING INSTALLATION
            // delete as user intended
            // log("Deleting existing Android SDK at $ANDROID_SDK_HOME")
            // FileUtils.deleteDirectory(File(ANDROID_SDK_HOME))
            return true
        }

        verbose("\ncreating Android SDK directory at $ANDROID_SDK_HOME")
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
        val saveTo = StringUtils.appendIfMissing(JAVA_IO_TMPDIR, separator) +
                     StringUtils.substringAfterLast(downloadUrl, "/")
        verbose("downloading from $downloadUrl...")
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

        val downloaded = downloadResp.payloadLocation
        if (StringUtils.substringAfterLast(downloaded, ".").lowercase() != "zip") {
            System.err.println("ERROR: File downloaded from $downloadUrl is not a ZIP file as expected!")
            exitProcess(-1)
        }
        verbose("downloaded to $downloaded")

        if (FileUtil.isDirectoryReadWritable(unzipLocation))
            try {
                FileUtils.deleteDirectory(unzipLocation)
            } catch (e: Exception) {
                // ignore
            }
        unzipLocation.mkdirs()
        val unzipped = FileUtil.unzip(File(downloaded), unzipLocation, verbose)
        verbose("unzipped $downloaded to $unzipLocation")
        File(downloaded).delete()
        return unzipped
    }

    private fun installCommonPackages(sdkManager: String) {
        val envRuntime = mapOf(WORKING_DIRECTORY to StringUtils.substringAfterLast(sdkManager, separator))
        val outcome = ProcessInvoker.invoke(sdkManager, optionInstallCommPkg, envRuntime)
        verbose(outcome.stdout)

        val runtimeError = outcome.stderr
        if (StringUtils.isNotBlank(runtimeError)) {
            System.err.println(runtimeError)
            exitProcess(-1)
        }
    }

    private fun installSystemImages(sdkManager: String) {
        val sdkManagerPath = StringUtils.substringBeforeLast(sdkManager, separator)
        val envRuntime = mapOf(WORKING_DIRECTORY to sdkManagerPath)

        val listPackages = ProcessInvoker.invoke(sdkManager, optionListSysImg, envRuntime)

        val sysImages = StringUtils.split(listPackages.stdout, "\n")
            .filter { StringUtils.startsWith(StringUtils.trim(it), SYSTEM_IMAGES_PREFIX) }
            .map { StringUtils.substringBefore(StringUtils.trim(it), " ") }
            .sorted()

        if (arch == 64 && !sysImages.contains(defSysImg)) defSysImg = DEF_SYS_IMG_32

        var stop = false
        do {
            var userSysImg = if (alwaysOverride) {
                log("(ALWAYS OVERRIDE): installing Android SDK System Image $defSysImg")
                defSysImg
            } else {
                println("$border2\nAvailable System Images\n$border2")
                sysImages.forEach { println(it) }
                print("\nEnter the system image to install (default: $defSysImg), $cmdQuit to end: ")
                Scanner(System.`in`).nextLine()
            }

            if (StringUtils.equalsIgnoreCase(userSysImg, cmdQuit))
                stop = true
            else {
                if (StringUtils.isBlank(userSysImg)) userSysImg = defSysImg
                if (!sysImages.contains(userSysImg))
                    println("ERROR: Unknown system images specified - $userSysImg\n")
                else {
                    val outcome = ProcessInvoker.invoke(sdkManager, optionInstall.plus(userSysImg), envRuntime)
                    verbose(outcome.stdout)

                    if (StringUtils.isNotBlank(outcome.stderr)) {
                        System.err.println(outcome.stderr)
                        exitProcess(-1)
                    }
                }

                // one time's enough for `alwaysOverride` mode
                if (alwaysOverride) stop = true
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
        verbose("copied $APK_SIGNER_FILE to $APK_SIGNER_DEST")
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
        availableEmulators.vendors.sortedBy { it.name }.forEach {
            println(border1 + "\n" + it.name + "\n" + border1)
            println(StringUtils.rightPad("[id]", 20) + " " +
                    StringUtils.rightPad("[name]", 20) + " " +
                    StringUtils.rightPad("[display]", 12) + " " +
                    StringUtils.rightPad("[resolution]", 12))
            println(border2)
            it.products.sortedBy { it.id }.forEach {
                println(StringUtils.rightPad(it.id, 21) +
                        StringUtils.rightPad(it.name, 21) +
                        StringUtils.rightPad(it.display, 13) +
                        StringUtils.rightPad(it.resolution, 13))
            }
            println()
        }
    }

    private fun installEmulator(availableEmulators: AvailableEmulators, installedSysImg: List<String>) {
        val allEmulators = availableEmulators.vendors.map { it.products }.flatten()

        var stop = false
        do {
            val userInput = if (alwaysOverride) {
                log("(ALWAYS OVERRIDE): installing emulator $defAvd...")
                defAvd
            } else {
                showAvailableEmulators(availableEmulators)
                print("\nEnter the emulator id to install it, or $cmdQuit to end: ")
                Scanner(System.`in`).nextLine()
            }

            if (StringUtils.equalsIgnoreCase(userInput, cmdQuit))
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
                        if (StringUtils.isBlank(userSysImg)) stop = true else createAvd(userInput, userSysImg, target)
                    }
                }
            }

            if (alwaysOverride) stop = true
        } while (!stop)

        val binPath = StringUtils.appendIfMissing(projectHome, separator) + projectBinRelPath
        val runEmuScripts = FileUtil.listFiles(binPath, "$runEmuBatchPrefix.+", false)
        if (CollectionUtils.isNotEmpty(runEmuScripts)) {
            log("\nhere are the batch files currently available to start your emulators:")
            runEmuScripts.forEach { log("\t$it") }
            println()
        }
    }

    private fun selectEmulatorSysImg(installedSysImg: List<String>): String {
        if (alwaysOverride) {
            log("installing $defSysImg for this emulator")
            return defSysImg
        }

        println()
        println("Installed System Images:")
        installedSysImg.forEach { println(it) }
        println()
        print("Enter the system image to use for this emulator, or $cmdQuit to end: ")

        var userSysImg: String
        var stop = false
        do {
            userSysImg = Scanner(System.`in`).nextLine()
            if (StringUtils.equalsIgnoreCase(userSysImg, cmdQuit))
                stop = true
            else
                if (!installedSysImg.contains(userSysImg))
                    println("ERROR: Invalid system image - $userSysImg")
                else
                    stop = true
        } while (!stop)

        return userSysImg
    }

    private fun createAvd(userAvd: String?, userSysImg: String, target: EmulatorConfig) {
        val processEnv = mapOf(WORKING_DIRECTORY to StringUtils.substringAfterLast(SDK_MANAGER, separator))
        val createOutcome = if (IS_OS_WINDOWS) {
            ProcessInvoker.invoke(
                WIN32_CMD,
                listOf("/C", "echo.|$AVD_MANAGER", "create", "avd", "-n", userAvd, "-k", "\"" + userSysImg + "\""),
                processEnv)
        } else
            ProcessInvoker.invoke(
                "bash",
                listOf("-c", "echo no | $AVD_MANAGER create avd -n $userAvd -k '$userSysImg'"),
                processEnv)

        if (StringUtils.isNotBlank(createOutcome.stderr)) {
            println("ERROR " + createOutcome.stderr + "\n")
            exitProcess(-1)
        }

        verbose("created avd $userAvd")

        val deleteList = listOf("hw.dPad", "hw.gpu.enabled", "hw.keyboard", "hw.lcd.density", "hw.lcd.height",
                                "hw.lcd.width", "hw.mainKeys", "hw.ramSize", "hw.trackBall",
                                "skin.dynamic", "skin.name", "skin.path")

        val defAvdRam = 1536
        val avdDensity = 480
        val avdWidth = StringUtils.substringBefore(target.resolution, "x")
        val avdHeight = StringUtils.substringAfter(target.resolution, "x")
        //  120, 140, 160, 180, 213, 240, 280, 320, 340, 360, 400, 420, 440, 480, 560, 640

        val addList = listOf("hw.dPad=no", "hw.gpu.enabled=yes", "hw.keyboard=yes", "hw.mainKeys=no",
                             "hw.trackBall=no", "skin.dynamic=yes",
                             "hw.lcd.density=$avdDensity", "hw.lcd.height=$avdHeight", "hw.lcd.width=$avdWidth",
                             "hw.ramSize=$defAvdRam",
                             "skin.name=${target.skin}", "skin.path=${SKIN_PATH}${separator}${target.skin}"
        )

        // edit config.ini
        val configIni = File("$ANDROID_AVD_HOME$separator${userAvd}.avd${separator}config.ini")
        val configIniContent = FileUtils.readLines(configIni, DEF_CHARSET)
        configIniContent.removeAll { deleteList.contains(StringUtils.trim(StringUtils.substringBefore(it, "="))) }
        addList.forEach { configIniContent.add(it) }
        FileUtils.writeStringToFile(configIni, configIniContent.joinToString(separator = "\n"), DEF_CHARSET)

        // generate emulator launch batch files
        val batchTemplateReplacements: MutableMap<Any, Any> = mutableMapOf(
            "\${avd.id}" to userAvd!!,
            "\${generator.signature}" to signature,
            "\${generated.timestamp}" to DateUtility.getCurrentTimestampForLogging()
        )
        val cmd = StringUtils.appendIfMissing(projectHome, separator) + "$runEmuPrefix${userAvd}.cmd"
        FileUtils.write(File(cmd), TextUtils.replace(runEmuTemplateCmd, batchTemplateReplacements), DEF_CHARSET)
        File(cmd).setExecutable(true, false)
        verbose("created emulator batch file: $cmd")

        val sh = StringUtils.appendIfMissing(projectHome, separator) + "$runEmuPrefix${userAvd}.sh"
        FileUtils.write(File(sh), TextUtils.replace(runEmuTemplateSh, batchTemplateReplacements), DEF_CHARSET)
        File(sh).setExecutable(true, false)
        verbose("created emulator batch file: $sh")
    }

    private fun log(message: String) {
        StringUtils.splitPreserveAllTokens(message, "\n").forEach {
            if (StringUtils.isBlank(it))
                println(it)
            else
                println("${DateUtility.getCurrentTimeForLogging()} >> $it")
        }
    }

    private fun verbose(message: String) {
        if (verbose) log(message)
    }
}

data class AvailableEmulators(val vendors: List<EmulatorVendor>)

data class EmulatorVendor(val name: String, val products: List<EmulatorConfig>)

data class EmulatorConfig(val id: String, val name: String, val display: String, val resolution: String,
                          val skin: String)
