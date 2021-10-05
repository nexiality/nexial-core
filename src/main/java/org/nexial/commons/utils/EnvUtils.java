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

package org.nexial.commons.utils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import javax.validation.constraints.NotNull;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.nexial.core.utils.ConsoleUtils;

import static java.lang.System.lineSeparator;
import static org.apache.commons.lang3.SystemUtils.IS_OS_WINDOWS;
import static org.apache.commons.lang3.SystemUtils.OS_ARCH;

/**
 * environment-specific utility.
 */
public final class EnvUtils {
    private final static String hostname;

    private EnvUtils() {}

    /**
     * return the hostname of current host.  If no host can be found, IP address will be returned instead.
     */
    @NotNull
    public static String getHostName() { return hostname; }

    public static boolean isRunningWindows64bit() {
        return IS_OS_WINDOWS &&
               (StringUtils.contains(OS_ARCH, "64") || StringUtils.isNotBlank(System.getenv("ProgramFiles(x86)")));
    }

    public static int getOsArchBit() { return NumberUtils.toInt(System.getProperty("sun.arch.data.model"), -1); }

    @NotNull
    public static String platformSpecificEOL(String text) {
        if (lineSeparator().equals("\n")) { return enforceUnixEOL(text); }
        if (lineSeparator().equals("\r\n")) { return enforceWindowsEOL(text); }
        return text;
    }

    @NotNull
    public static String enforceUnixEOL(String text) {
        text = StringUtils.replace(text, "\r\n", "\n");
        return StringUtils.replace(text, "\r", "\n");
    }

    @NotNull
    public static String enforceWindowsEOL(String text) {
        text = StringUtils.replace(text, "\r\n", "\n");
        text = StringUtils.replace(text, "\r", "\n");
        return StringUtils.replace(text, "\n", "\r\n");
    }

    /**
     * gather all system properties whose key matches to specified {@code prefix}.
     */
    @NotNull
    public static Map<String, String> getSysPropsByPrefix(String prefix) {
        Map<String, String> props = new LinkedHashMap<>();
        System.getProperties().forEach((key, value) -> {
            String sKey = key.toString();
            if (StringUtils.startsWith(sKey, prefix)) {
                props.put(StringUtils.substringAfter(sKey, prefix), Objects.toString(value));
            }
        });
        return props;
    }

    static {
        String host;
        try {
            InetAddress localhost = InetAddress.getLocalHost();
            host = StringUtils.defaultIfBlank(localhost.getHostName(), localhost.getHostAddress());
        } catch (UnknownHostException e) {
            ConsoleUtils.error("Unable to determine host name of current host: " + e.getMessage() + ". " +
                               "Default to 'localhost'");
            host = "localhost";
        }

        hostname = StringUtils.upperCase(host);
    }
}
