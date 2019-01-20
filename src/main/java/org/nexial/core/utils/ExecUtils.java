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

package org.nexial.core.utils;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;

import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.utils.DateUtility;
import org.nexial.core.Nexial;

import static org.nexial.core.NexialConst.Data.DEF_TEXT_DELIM;
import static org.nexial.core.NexialConst.Data.SCRIPT_REF_PREFIX;
import static org.nexial.core.NexialConst.Integration.*;
import static org.nexial.core.NexialConst.Jenkins.*;
import static org.nexial.core.NexialConst.*;
import static org.nexial.core.NexialConst.Project.NEXIAL_HOME;

public final class ExecUtils {
    public static final String PRODUCT = "nexial";
    public static final String JAVA_OPT = "JAVA_OPT";
    public static final String RUNTIME_ARGS = "runtime args";
    public static final List<String> IGNORED_CLI_OPT = Arrays.asList(
        "awt.", "java.",
        "idea.test.", "intellij.debug",
        "org.gradle.",

        "file.encoding", "file.separator", "line.separator", "path.separator",

        "ftp.nonProxyHosts", "gopherProxySet", "http.nonProxyHosts", "socksNonProxyHosts",

        "nexial-mailer.", "nexial.3rdparty.logpath", "nexial.jdbc.", NEXIAL_HOME, OPT_DATA_DIR, OPT_DEF_OUT_DIR,
        OPT_CLOUD_OUTPUT_BASE, OPT_SCRIPT_DIR, OPT_PLAN_DIR, "site-name", SMS_PREFIX, MAIL_PREFIX, OTC_PREFIX,
        TTS_PREFIX,

        "sun.arch", "sun.boot", "sun.cpu", "sun.desktop", "sun.font", "sun.io", "sun.java", "sun.jnu", "sun.management",
        "sun.os", "sun.stderr.encoding", "sun.stdout.encoding",

        "jboss.modules",

        "user.country", "user.dir", "user.home", "user.language", "user.variant",

        "webdriver.");

    public static final List<String> JUNIT_CLASSES = Arrays.asList("org.junit.runner.JUnitCore",
                                                                   "org.junit.runners.ParentRunner");
    public static String manifest;

    private ExecUtils() {}

    @NotNull
    public static String deriveJarManifest() {
        if (manifest == null) {
            Package pkg = Nexial.class.getPackage();

            // try by jar class loader
            if (pkg != null) {
                String implTitle = pkg.getImplementationTitle();
                String implVersion = pkg.getImplementationVersion();
                if (StringUtils.isNotBlank(implTitle) && StringUtils.isNotBlank(implVersion)) {
                    manifest = implTitle + " " + implVersion;
                    return manifest;
                }
            }

            ClassLoader cl = Nexial.class.getClassLoader();
            try {
                Enumeration<URL> resources = cl.getResources("META-INF/MANIFEST.MF");
                while (resources.hasMoreElements()) {
                    URL url = resources.nextElement();
                    Manifest manifest = new Manifest(url.openStream());
                    Attributes attributes = manifest.getMainAttributes();
                    String product = attributes.getValue("Implementation-Title");
                    if (StringUtils.equals(product, PRODUCT)) {
                        ExecUtils.manifest =
                            PRODUCT + " " + StringUtils.remove(attributes.getValue("Implementation-Version"), "Build ");
                        return ExecUtils.manifest;
                    }
                }
            } catch (IOException e) {
                ConsoleUtils.error("Unable to derive META-INF/MANIFEST.MF from classloader: " + e.getMessage());
            }

            manifest = "nexial-DEV";
        }

        return manifest;
    }

    public static String deriveRunId() {
        String runIdPrefix = StringUtils.defaultString(StringUtils.trim(System.getProperty(OPT_RUN_ID_PREFIX)));

        long rightNow = System.currentTimeMillis();
        String runId = System.getProperty(OPT_RUN_ID);
        if (StringUtils.isEmpty(runId)) { runId = DateUtility.createTimestampString(rightNow); }
        if (StringUtils.isNotBlank(runIdPrefix) && !StringUtils.startsWith(runId, runIdPrefix + ".")) {
            runId = runIdPrefix + "." + runId;
        }

        System.setProperty(OPT_RUN_ID, runId);
        System.setProperty(TEST_START_TS, rightNow + "");

        return runId;
    }

    public static void collectCliProps(String[] args) {
        // collect execution-time arguments so that we can display them in output
        System.setProperty(SCRIPT_REF_PREFIX + RUNTIME_ARGS, String.join(" ", args));

        List<String> inputArgs = ManagementFactory.getRuntimeMXBean().getInputArguments();

        String argsList = inputArgs.stream().filter(
            arg -> arg.startsWith("-D") &&
                   IGNORED_CLI_OPT.stream()
                                  .noneMatch(ignored -> StringUtils.startsWith(StringUtils.substring(arg, 2), ignored))
                                                   ).collect(Collectors.joining(DEF_TEXT_DELIM));
        if (StringUtils.isNotBlank(argsList)) { System.setProperty(SCRIPT_REF_PREFIX + JAVA_OPT, argsList); }
    }

    public static Map<String, String> deriveJavaOpts() {
        Map<String, String> javaOpts = new TreeMap<>();

        String javaOptsString = System.getProperty(SCRIPT_REF_PREFIX + JAVA_OPT);
        if (StringUtils.isNotBlank(javaOptsString)) {
            Arrays.stream(StringUtils.split(javaOptsString, DEF_TEXT_DELIM)).forEach(opt -> {
                if (StringUtils.length(opt) > 5 && StringUtils.contains(opt, "=")) {
                    String[] nameValue = StringUtils.removeStart(opt, "-D").split("=");
                    if (nameValue.length == 2) { javaOpts.put(nameValue[0], nameValue[1]); }
                }
            });
        }

        return javaOpts;
    }

    /** determine if we are running under CI (Jenkins) using current system properties */
    public static boolean isRunningInCi() {
        Map<String, String> environments = System.getenv();
        return StringUtils.isNotBlank(environments.get(OPT_JENKINS_URL)) &&
               StringUtils.isNotBlank(environments.get(OPT_JENKINS_HOME)) &&
               StringUtils.isNotBlank(environments.get(OPT_BUILD_ID)) &&
               StringUtils.isNotBlank(environments.get(OPT_BUILD_URL));
    }

    /** determine if we are running under JUnit framework */
    public static boolean isRunningInJUnit() {
        ClassLoader cl = ClassLoader.getSystemClassLoader();

        // am i running via junit?
        for (String junitClass : JUNIT_CLASSES) {
            try {
                Method m = ClassLoader.class.getDeclaredMethod("findLoadedClass", String.class);
                m.setAccessible(true);
                Object loaded = m.invoke(cl, junitClass);
                if (loaded != null) { return true; }
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                // probably not loaded... ignore error; it's probably not critical...
            }
        }

        return false;
    }

    public static boolean isRunningInZeroTouchEnv() { return isRunningInCi() || isRunningInJUnit(); }
}
