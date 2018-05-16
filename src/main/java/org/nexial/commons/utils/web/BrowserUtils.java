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

package org.nexial.commons.utils.web;

import javax.servlet.http.HttpServletRequest;

/**
 *

 */
public class BrowserUtils {
    private BrowserUtils() {}

    /** not an ideal solution but for now */
    public static boolean isIE7(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");

        /**
         * notes:
         * Compatibility View: http://blogs.msdn.com/b/ie/archive/2008/08/27/introducing-compatibility-view.aspx.
         * IE9's User Agent String: http://blogs.msdn.com/b/ie/archive/2010/03/23/introducing-ie9-s-user-agent-string.aspx.
         */
        if (!userAgent.contains("MSIE 7.0")) {
            return false;
        } else {
            return !(userAgent.contains("Trident/5.0") ||
                     userAgent.contains("Trident/4.0") ||
                     userAgent.contains("Mozilla/5.0 (compatible; MSIE 7.0; Windows NT 5.0;"));
        }
    }
}
