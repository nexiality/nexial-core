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
import java.net.URL;
import java.util.Enumeration;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.utils.DateUtility;
import org.nexial.core.Nexial;

import static org.nexial.core.NexialConst.*;

public final class ExecUtil {
    public static final String PRODUCT = "nexial";
    public static String manifest;

    private ExecUtil() {}

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
                        ExecUtil.manifest =
                            PRODUCT + " " + StringUtils.remove(attributes.getValue("Implementation-Version"), "Build ");
                        return ExecUtil.manifest;
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
}
