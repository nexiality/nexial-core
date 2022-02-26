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

package org.nexial.core.tools.appium

import net.dongliu.apk.parser.ApkFile
import org.apache.commons.cli.CommandLine
import org.apache.commons.cli.Options
import org.apache.commons.lang3.StringUtils
import org.jdom2.Document
import org.jdom2.Namespace
import org.nexial.commons.utils.FileUtil
import org.nexial.commons.utils.XmlUtils
import org.nexial.core.NexialConst.Project.BATCH_EXT
import org.nexial.core.tools.CliUtils.getCommandLine
import org.nexial.core.tools.CliUtils.newArgOption
import org.nexial.core.utils.ConsoleUtils
import java.io.File
import kotlin.system.exitProcess

object ApkAnalyzer {

    private const val SCRIPT_NAME = "nexial-apk-manifest"

    private val NS = Namespace.getNamespace("android", "http://schemas.android.com/apk/res/android")
    private const val XPATH_ACTIVITY = "//application/activity"

    private const val MIN_APK_FILE_SIZE = 100 * 1024
    private const val MSG_MAIN_ACTIVITY_NOT_FOUND = "Main Activity information cannot be found via APK's Manifest XML"
    private const val MSG_APK_NOT_FOUND = "Specified APK not found: "
    private const val MSG_APK_LOCATION = "The location of the APK"

    fun deriveCommandLine(args: Array<String>): CommandLine {
        val cliOptions = Options()
        cliOptions.addOption(newArgOption("a", "apk", MSG_APK_LOCATION, true))
        return getCommandLine("$SCRIPT_NAME$BATCH_EXT", args, cliOptions)
    }

    @JvmStatic
    fun main(args: Array<String>) {
        // 1. read cli options
        val cmd = deriveCommandLine(args)

        // 2. read APK path
        val apkPath = cmd.getOptionValue("a")
        if (!FileUtil.isFileReadWritable(apkPath, MIN_APK_FILE_SIZE)) {
            ConsoleUtils.error("$MSG_APK_NOT_FOUND$apkPath")
            println()
            exitProcess(-1)
        }

        // 3. parse APK
        val apk = ApkFile(File(apkPath))
        val apkMeta = apk.apkMeta
        val xmlDoc = XmlUtils.parse(apk.manifestXml)
        val packageName = apkMeta.packageName
        val mainActivity = deriveMainActivity(packageName, xmlDoc)

        // 4. display
        println()
        println("app:           $apkPath")
        println("label:         ${apkMeta.label}")
        println("appId:         $packageName")
        println("appPackage:    $packageName")
        println("appActivity:   $mainActivity")
        println()
    }

    private fun deriveMainActivity(packageName: String, xmlDoc: Document?): String {
        val activityName = XmlUtils.findElement(xmlDoc, XPATH_ACTIVITY)?.getAttributeValue("name", NS)
        return if (StringUtils.isBlank(activityName))
            MSG_MAIN_ACTIVITY_NOT_FOUND
        else if (StringUtils.startsWith(activityName, "."))
            "$packageName.$activityName"
        else
            activityName!!
    }
}