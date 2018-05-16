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

package org.nexial.core.plugins;

import java.io.Serializable;

public class ThirdPartyDriverInfo implements Serializable {
    private String jarFilePattern;
    private String minDriverVersion;
    private String url;

    public ThirdPartyDriverInfo(String jarFilePattern, String minVersion, String url) {
        this.jarFilePattern = jarFilePattern;
        this.minDriverVersion = minVersion;
        this.url = url;
    }

    public String getJarFilePattern() { return jarFilePattern; }

    public String getMinDriverVersion() { return minDriverVersion; }

    public String getUrl() { return url; }

    @Override
    public String toString() {
        return "Unable to load the required driver/3rd-party library.\n" +
               "Make sure " +
               jarFilePattern +
               " (at least " +
               minDriverVersion +
               ") is/are added to lib/ directory.\n" +
               "For more information, visit " +
               url;
    }
}
