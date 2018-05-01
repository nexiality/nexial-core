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

package org.nexial.core.aws;

import java.io.File;
import java.io.IOException;

import org.apache.commons.lang3.StringUtils;
import org.nexial.core.ExecutionThread;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.utils.ConsoleUtils;

import static com.amazonaws.regions.Regions.DEFAULT_REGION;
import static org.nexial.core.NexialConst.*;

/**
 * S3 helper specific to Nexial's internal use for transfering execution output to S3
 */
public class NexialS3Helper extends S3Support {
    protected ExecutionContext context;
    protected String outputBase;

    /** invoked by {@link ExecutionContext} for reference convenience */
    public void setContext(ExecutionContext context) { this.context = context; }

    public void setOutputBase(String outputBase) { this.outputBase = outputBase; }

    /**
     * resolve the appropriate S3 path prefix as the base of an execution output, which is based on
     * a common output prefix ({@link #outputBase}), current project name and current {@code Run ID}.
     */
    public String resolveOutputDir() {
        checkContext();
        return context.getStringData(OPT_CLOUD_OUTPUT_BASE, outputBase) + S3_PATH_SEPARATOR +
               context.getProject().getName() + S3_PATH_SEPARATOR +
               context.getRunId();
    }

    @Override
    public boolean isReadyForUse() { return super.isReadyForUse() && StringUtils.isNotBlank(outputBase); }

    public String importMedia(File media) throws IOException {
        checkContext();
        if (media == null || !context.isOutputToCloud()) { return null; }
        return importToS3(media, resolveCaptureDir());
    }

    public String importLog(File logFile, boolean removeLocal) throws IOException {
        return importToS3(logFile, resolveLogDir(), removeLocal);
    }

    public String importFile(File source, boolean removeLocal) throws IOException {
        return importToS3(source, resolveOutputDir(), removeLocal);
    }

    protected void init() {
        if (StringUtils.isBlank(outputBase)) { ConsoleUtils.log("outputBase not set; output-to-cloud WILL NOT WORK!"); }
        if (StringUtils.isBlank(accessKey)) { ConsoleUtils.log("accessKey not set; output-to-cloud WILL NOT WORK!"); }
        if (StringUtils.isBlank(secretKey)) { ConsoleUtils.log("secretKey not set; output-to-cloud WILL NOT WORK!"); }
        if (region == null) { ConsoleUtils.log("region not set; default to " + DEFAULT_REGION); }
    }

    protected String resolveCaptureDir() { return resolveOutputDir() + S3_PATH_SEPARATOR + SUBDIR_CAPTURES; }

    protected String resolveLogDir() { return resolveOutputDir() + S3_PATH_SEPARATOR + SUBDIR_LOGS; }

    protected void checkContext() {
        if (context == null) {
            context = ExecutionThread.get();
            // cannot be done!
            if (context == null) { throw new RuntimeException("Context is null or unavailable!"); }
        }
    }
}
