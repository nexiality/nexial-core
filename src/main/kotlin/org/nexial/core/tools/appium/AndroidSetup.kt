package org.nexial.core.tools.appium

import com.mchange.v1.lang.BooleanUtils
import org.apache.commons.cli.CommandLine
import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.SystemUtils.JAVA_IO_TMPDIR
import org.nexial.commons.proc.ProcessInvoker.WORKING_DIRECTORY
import org.nexial.commons.proc.ProcessInvoker.invoke
import org.nexial.commons.utils.EnvUtils
import org.nexial.commons.utils.FileUtil
import org.nexial.core.NexialConst.Mobile.Android.*
import org.nexial.core.plugins.ws.WebServiceClient
import java.io.File
import java.io.File.separator
import java.io.IOException
import java.util.*
import kotlin.system.exitProcess

object AndroidSetup {

    private val optionListSysImg = listOf("--sdk_root=$ANDROID_SDK_HOME", "--list")
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
    private val optionRunEmulator = listOf("orientation", "PORTRAIT",
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

    @JvmStatic
    fun main(args: Array<String>) {
        // val options = deriveMacroOptions(deriveCommandLine(args))

        // step 0: check existing ~/.nexial/android/sdk - ASK TO DELETE IT
        //  if yes, then delete recursively ~/.nexial/android/sdk
        if (!overrideExistingAndroidSDK()) return

        // step 1: download cmdlinetools.zip
        // step 2: unzip cmdlinetools.zip to %TEMP%/cmdline-tools
        downloadAndUnzip(CMDLINE_TOOLS_URL, File(CMDLINE_TOOLS_UNZIP_LOCATION))

        // step 3: recreate ~/.nexial/android/sdk/license
        // step 4: copy pre-packaged files:
        //      unzip $DISTRO_URL_BASE/android_sdk_license.zip to ~/.nexial/android/sdk/license
        //      unzip $DISTRO_URL_BASE/android_skins.zip to ~/.nexial/android/sdk/skins
        downloadAndUnzip(ANDROID_SDK_LICENSE_ZIP_URL, File(LICENSE_PATH))
        downloadAndUnzip(ANDROID_SDK_SKINS_ZIP_URL, File(SKIN_PATH))

        // step 5: install command packages
        //  sdkmanager --sdk_root=~/.nexial/android/sdk --install "extras;google;usb_driver" "extras;google;webdriver"
        //      "platform-tools" "emulator" "cmdline-tools;latest" "extras;intel;Hardware_Accelerated_Execution_Manager"
        //      "extras;google;Android_Emulator_Hypervisor_Driver" "platforms;android-30" "build-tools;30.0.3"
        installCommonPackages(SDK_MANAGER)

        // step 6: show available system images:
        //  .. ...
        //  system-images;android-30;google_apis;x86               | 9            | Google APIs Intel x86 Atom System Image
        //  system-images;android-30;google_apis;x86_64            | 10           | Google APIs Intel x86 Atom_64 System Image
        //  .. ...
        //
        //  Enter the system image to install (default: system-images;android-30;google_apis_playstore;x86_64):
        //  Installing....
        //(repeat)
        installSystemImages(SDK_MANAGER)

        // step 7: copy $ANDROID_SDK_HOME/build-tools/x/lib/apksigner.jar to $ANDROID_SDK_HOME/tools/lib
        copyApkSigner()

        // step 8: install emulator
        //  avdmanager.bat list device -c

    }

    private fun overrideExistingAndroidSDK(): Boolean {
        if (FileUtil.isDirectoryReadWritable(ANDROID_SDK_HOME)) {
            // existing Android SDK exist
            print("An existing Android SDK is detected at $ANDROID_SDK_HOME. Do you want to install over it? ")
            val input = Scanner(System.`in`).nextLine()
            if (!BooleanUtils.parseBoolean(input)) {
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

    private fun downloadAndUnzip(downloadUrl: String, unzipLocation: File) {
        val downloadResp = WebServiceClient(null)
            .configureAsQuiet()
            .disableContextConfiguration()
            .download(downloadUrl, null, JAVA_IO_TMPDIR)
        if (downloadResp.returnCode != 200) {
            System.err.println("ERROR: Unable to download from $downloadUrl: " +
                               "${downloadResp.returnCode} ${downloadResp.statusText}\n" +
                               downloadResp.body)
            exitProcess(-1)
        }

        log("downloaded from $downloadUrl")

        val downloaded = downloadResp.payloadLocation
        if (StringUtils.substringAfterLast(downloaded, ".").toLowerCase() != "zip") {
            System.err.println("ERROR: File downloaded from $downloadUrl is not a ZIP file as expected!")
            exitProcess(-1)
        }

        unzipLocation.mkdirs()
        val unzipped = FileUtil.unzip(File(downloaded), unzipLocation)
        log("unzipped $downloadUrl to $unzipLocation")
        unzipped.find { it.absolutePath.contains(AVD_MANAGER_REL_PATH) }
        ?: throw IOException("ERROR: Unable to find $AVD_MANAGER_REL_PATH from $unzipLocation")

        unzipped.find { it.absolutePath.contains(SDK_MANAGER_REL_PATH) }
        ?: throw IOException("ERROR: Unable to find $SDK_MANAGER_REL_PATH from $unzipLocation")

    }

    private fun installCommonPackages(sdkManager: String) {
        val envRuntime = mapOf(WORKING_DIRECTORY to StringUtils.substringAfterLast(sdkManager, separator))
        val outcome = invoke(sdkManager, optionInstallCommPkg, envRuntime)
        log(outcome.stdout + "\n")

        val runtimeError = outcome.stderr
        if (runtimeError != null) {
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
            .map { StringUtils.substringBefore(it, " ") }
            .sorted()

        val arch = EnvUtils.getOsArchBit()
        val defSysImg = if (arch == 64 && sysImages.contains(DEF_SYS_IMG_64)) DEF_SYS_IMG_64 else DEF_SYS_IMG_32

        println(StringUtils.repeat("-", 80) + "\n" +
                "Available System Images\n" +
                StringUtils.repeat("-", 80) + "\n")
        sysImages.forEach { println(it) }

        var endInstallation = false
        do {
            print("\nEnter the system image to install (default: $defSysImg), QUIT to end: ")
            var userSysImg = Scanner(System.`in`).nextLine()
            if (StringUtils.equalsIgnoreCase(userSysImg, "QUIT"))
                endInstallation = true
            else {
                if (StringUtils.isBlank(userSysImg)) userSysImg = defSysImg
                if (!sysImages.contains(userSysImg))
                    println("ERROR: Unknown system images specified - $userSysImg\n")
                else {
                    val outcome = invoke(sdkManager, optionInstall.plus(userSysImg), envRuntime)
                    log(outcome.stdout + "\n")

                    if (outcome.stderr != null) {
                        System.err.println(outcome.stderr)
                        exitProcess(-1)
                    }
                }
            }
        } while (!endInstallation)
    }

    private fun copyApkSigner() {
        val apksignerFiles = FileUtil.listFiles(BUILD_TOOLS_PATH, APK_SIGNER_FILE, true)
        if (CollectionUtils.isEmpty(apksignerFiles)) {
            System.err.println("ERROR: Unable to find $APK_SIGNER_FILE under $BUILD_TOOLS_PATH")
            exitProcess(-1)
        }

        FileUtils.copyFile(apksignerFiles[0], File(APK_SIGNER_DEST))
    }

    private fun log(message: String) {
        if (verbose) println(message)
    }

    private fun deriveCommandLine(args: Array<String>): CommandLine {
        TODO("Not yet implemented")
    }
}