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

package org.nexial.commons.utils;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.apache.commons.lang3.StringUtils;

import static java.lang.System.lineSeparator;
import static org.apache.commons.lang3.SystemUtils.IS_OS_WINDOWS;
import static org.apache.commons.lang3.SystemUtils.OS_ARCH;

/**
 * environment-specific utility.
 */
public final class EnvUtils {

    private EnvUtils() {}

    /**
     * return the hostname of current host.  If no host can be found, IP address will be returned instead.
     *
     * @throws UnknownHostException if no host or IP can be found for this host.
     */
    public static String getHostName() throws UnknownHostException {
        InetAddress localhost = InetAddress.getLocalHost();
        return StringUtils.defaultIfBlank(localhost.getHostName(), localhost.getHostAddress());
    }

    public static boolean isRunningWindows64bit() {
        return IS_OS_WINDOWS &&
               (StringUtils.contains(OS_ARCH, "64") || StringUtils.isNotBlank(System.getenv("ProgramFiles(x86)")));
    }

    public static String platformSpecificEOL(String text) {
        if (lineSeparator().equals("\n")) { return enforceUnixEOL(text); }
        if (lineSeparator().equals("\r\n")) { return enforceWindowsEOL(text); }
        return text;
    }

    public static String enforceUnixEOL(String text) {
        text = StringUtils.replace(text, "\r\n", "\n");
        return StringUtils.replace(text, "\r", "\n");
    }

    public static String enforceWindowsEOL(String text) {
        text = StringUtils.replace(text, "\r\n", "\n");
        text = StringUtils.replace(text, "\r", "\n");
        return StringUtils.replace(text, "\n", "\r\n");
    }
}
