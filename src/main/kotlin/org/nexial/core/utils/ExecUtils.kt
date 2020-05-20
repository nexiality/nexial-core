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
 */

package org.nexial.core.utils

import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.SystemUtils.IS_OS_WINDOWS
import org.nexial.commons.utils.DateUtility
import org.nexial.core.Nexial
import org.nexial.core.NexialConst.*
import org.nexial.core.NexialConst.Data.*
import org.nexial.core.NexialConst.Integration.*
import org.nexial.core.NexialConst.Jenkins.*
import org.nexial.core.NexialConst.Project.NEXIAL_HOME
import org.nexial.core.SystemVariables.getDefault
import java.io.IOException
import java.lang.management.ManagementFactory
import java.lang.reflect.InvocationTargetException
import java.util.*
import java.util.jar.Manifest
import java.util.stream.Collectors

object ExecUtils {
    const val PRODUCT = "nexial"
    const val JAVA_OPT = "JAVA_OPT"
    const val RUNTIME_ARGS = "runtime args"

    @JvmField
    val IGNORED_CLI_OPT = arrayListOf<String>(
        "awt.", "java.", "jdk.",
        "idea.test.", "intellij.debug",
        "org.gradle.", "org.apache.poi.util.POILogger",

        "file.encoding", "file.separator", "line.separator", "path.separator",

        "ftp.nonProxyHosts", "gopherProxySet", "http.nonProxyHosts", "socksNonProxyHosts",

        "nexial-mailer.", "nexial.3rdparty.logpath", "nexial.jdbc.", NEXIAL_HOME,
        OPT_OUT_DIR, OPT_PLAN_DIR, OPT_SCRIPT_DIR, OPT_DATA_DIR, OPT_DEF_OUT_DIR, OPT_CLOUD_OUTPUT_BASE,
        "site-name", SMS_PREFIX, MAIL_PREFIX, OTC_PREFIX, TTS_PREFIX, VISION_PREFIX,

        "sun.arch", "sun.boot", "sun.cpu", "sun.desktop", "sun.font", "sun.io", "sun.java", "sun.jnu",
        "sun.management", "sun.os", "sun.stderr.encoding", "sun.stdout.encoding",

        "jboss.modules",

        "user.country", "user.dir", "user.home", "user.language", "user.variant",

        "webdriver.",

        "nashorn."
    )

    @JvmField
    val JUNIT_CLASSES = arrayListOf("org.junit.runner.JUnitCore", "org.junit.runners.ParentRunner")

    @JvmField
    val BIN_SCRIPT_EXT = if (IS_OS_WINDOWS) ".cmd" else ".sh"

    @JvmField
    val NEXIAL_MANIFEST: String = deriveJarManifest()

    @JvmField
    var IS_RUNNING_IN_JUNIT = isRunningInJUnit()

    /** determine if we are running under CI (Jenkins) using current system properties  */
    @JvmStatic
    fun isRunningInCi(): Boolean = StringUtils.isNotBlank(System.getenv()[OPT_JENKINS_URL]) &&
                                   StringUtils.isNotBlank(System.getenv()[OPT_JENKINS_HOME]) &&
                                   StringUtils.isNotBlank(System.getenv()[OPT_BUILD_ID]) &&
                                   StringUtils.isNotBlank(System.getenv()[OPT_BUILD_URL])

    @JvmStatic
    fun isRunningInZeroTouchEnv(): Boolean = IS_RUNNING_IN_JUNIT || isRunningInCi()

    @JvmStatic
    fun currentCiBuildUrl() = if (!isRunningInCi()) "" else System.getenv(OPT_BUILD_URL)

    @JvmStatic
    fun currentCiBuildId() = if (!isRunningInCi()) "" else System.getenv(OPT_BUILD_ID)

    @JvmStatic
    fun currentCiBuildNumber() = if (!isRunningInCi()) "" else System.getenv(OPT_BUILD_NUMBER)

    @JvmStatic
    fun currentCiBuildUser() = if (!isRunningInCi()) "" else System.getenv(OPT_BUILD_USER_ID)

    @JvmStatic
    fun deriveRunId(): String {
        var runId = System.getProperty(OPT_RUN_ID)
        if (StringUtils.isNotBlank(runId)) return runId

        val rightNow = System.currentTimeMillis()
        runId = DateUtility.createTimestampString(rightNow)

        val runIdPrefix = StringUtils.defaultString(StringUtils.trim(System.getProperty(OPT_RUN_ID_PREFIX)))
        if (StringUtils.isNotBlank(runIdPrefix) && !StringUtils.startsWith(runId, "$runIdPrefix.")) {
            runId = "$runIdPrefix.$runId"
        }

        System.setProperty(OPT_RUN_ID, runId)
        System.setProperty(TEST_START_TS, rightNow.toString())

        return runId
    }

    @JvmStatic
    fun collectCliProps(args: Array<String>) {
        // collect execution-time arguments so that we can display them in output
        System.setProperty(SCRIPT_REF_PREFIX + RUNTIME_ARGS, args.joinToString(" "))

        val argsList = ManagementFactory.getRuntimeMXBean().inputArguments.stream()
            .filter { arg ->
                arg.startsWith("-D") &&
                IGNORED_CLI_OPT.none { StringUtils.startsWith(StringUtils.substring(arg, 2), it) }
            }.collect(Collectors.joining(getDefault(TEXT_DELIM)!!))

        if (argsList.isNotBlank()) System.setProperty(SCRIPT_REF_PREFIX + JAVA_OPT, argsList)
    }

    @JvmStatic
    fun deriveJavaOpts(): Map<String, String> {
        val javaOpts = TreeMap<String, String>()

        val javaOptsString = System.getProperty(SCRIPT_REF_PREFIX + JAVA_OPT)
        if (StringUtils.isBlank(javaOptsString)) return javaOpts

        javaOptsString.split(getDefault(TEXT_DELIM)!!)
            .filter { StringUtils.length(it) > 5 && StringUtils.contains(it, "=") }
            .forEach {
                val nameValue = it.removePrefix("-D").split("=")
                if (nameValue.size == 2) javaOpts[nameValue[0]] = nameValue[1]
            }

        return javaOpts
    }

    @JvmStatic
    fun isSystemVariable(varName: String): Boolean {
        for (ignored in IGNORED_CLI_OPT)
            if (StringUtils.startsWith(varName, ignored)) return true
        return false
    }

    /** determine if we are running under JUnit framework  */
    // am i running via junit?
    // probably not loaded... ignore error; it's probably not critical...
    // @JvmStatic
    private fun isRunningInJUnit(): Boolean {
        val cl = ClassLoader.getSystemClassLoader()
        for (junitClass in JUNIT_CLASSES) {
            try {
                val m = ClassLoader::class.java.getDeclaredMethod("findLoadedClass", String::class.java)
                m.isAccessible = true
                val loaded = m.invoke(cl, junitClass)
                if (loaded != null) return true
            } catch (e: NoSuchMethodException) {
            } catch (e: IllegalAccessException) {
            } catch (e: InvocationTargetException) {
            }
        }

        return false
    }

    private fun deriveJarManifest(): String {
        val pkg = Nexial::class.java.getPackage()

        // try by jar class loader
        if (pkg != null) {
            val implTitle = pkg.implementationTitle
            val implVersion = pkg.implementationVersion
            if (StringUtils.isNotBlank(implTitle) && StringUtils.isNotBlank(implVersion)) return "$implTitle $implVersion"
        }

        val cl = Nexial::class.java.classLoader
        try {
            val resources = cl.getResources("META-INF/MANIFEST.MF")
            while (resources.hasMoreElements()) {
                val url = resources.nextElement()
                val manifest = Manifest(url.openStream())
                val attributes = manifest.mainAttributes
                val product = attributes.getValue("Implementation-Title")
                if (StringUtils.equals(product, PRODUCT)) {
                    return "$PRODUCT ${StringUtils.remove(attributes.getValue("Implementation-Version"), "Build ")}"
                }
            }
        } catch (e: IOException) {
            ConsoleUtils.error("Unable to derive META-INF/MANIFEST.MF from classloader: " + e.message)
        }

        return "nexial-DEV"
    }
}
