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

package org.nexial.core.reports;

import org.apache.commons.lang3.StringUtils;
import org.nexial.core.model.ExecutionContext;

import static org.nexial.core.NexialConst.Jenkins.*;
import static org.nexial.core.NexialConst.*;

/**
 *

 */
public final class JenkinsVariables {
    private static JenkinsVariables self;

    private ExecutionContext context;
    private String buildUrl;
    private final String buildNumber;
    private final String buildUser;
    private final String buildUserId;
    private final String jobName;
    private String testScript;
    private boolean invokedFromJenkins;

    private JenkinsVariables(ExecutionContext context) {
        this.context = context;

        jobName = System.getProperty(OPT_JOB_NAME);
        buildNumber = System.getProperty(OPT_BUILD_NUMBER);
        buildUserId = System.getProperty(OPT_BUILD_USER_ID);
        buildUrl = System.getProperty(OPT_BUILD_URL);
        buildUser = System.getProperty(OPT_BUILD_USER);

        // favor nexial.suite, then nexial.inputExcel, then nexial.excel
        // nexial.excel most likely modified to the output version by this point
        testScript = System.getProperty(OPT_SUITE_PROP,
                                        System.getProperty(OPT_INPUT_EXCEL_FILE, System.getProperty(OPT_EXCEL_FILE)));

        invokedFromJenkins = StringUtils.isNotBlank(buildUserId) &&
                             StringUtils.isNotBlank(buildUser) &&
                             StringUtils.isNotBlank(buildNumber) &&
                             StringUtils.isNotBlank(buildUrl);
    }

    public static JenkinsVariables getInstance(ExecutionContext context) {
        if (self == null) { self = new JenkinsVariables(context); }
        return self;
    }

    public String getBuildUrl() { return buildUrl; }

    public String getBuildNumber() { return buildNumber; }

    public String getBuildUser() { return buildUser; }

    public String getBuildUserId() { return buildUserId; }

    public String getJobName() { return jobName; }

    public String getTestScript() { return testScript; }

    public boolean isInvokedFromJenkins() { return invokedFromJenkins; }

    public boolean isNotInvokedFromJenkins() { return !isInvokedFromJenkins(); }
}
