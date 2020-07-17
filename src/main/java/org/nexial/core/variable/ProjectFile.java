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

package org.nexial.core.variable;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.utils.FileUtil;
import org.nexial.core.ExecutionThread;
import org.nexial.core.logs.StepErrors;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.utils.ConsoleUtils;

import static java.io.File.separator;
import static org.nexial.core.NexialConst.*;

/**
 * built-in function to handle project-specific files and file content.
 */
public class ProjectFile {

    public String text(String file) {
        File f = resolveProjectFile(file);
        if (f == null) { return null; }

        try {
            String content = FileUtils.readFileToString(f, DEF_FILE_ENCODING);
            ExecutionContext context = ExecutionThread.get();
            if (context != null) { content = context.replaceTokens(content); }
            return content;
        } catch (IOException e) {
            ConsoleUtils.log("Unable to read " + f.getAbsolutePath() + ": " + e.getMessage());
            return null;
        }
    }

    public String executionErrorsAsHtml(String tableOnly) {
        String logFile = StringUtils.appendIfMissing(Syspath.getExecutionData(OPT_OUT_DIR), separator) +
                         SUBDIR_LOGS + separator + ERROR_TRACKER;
        File log = new File(logFile);
        if (!FileUtil.isFileReadable(log, 32)) { return ""; }

        boolean fullHtml = !BooleanUtils.toBoolean(tableOnly);
        StepErrors errors = new StepErrors(log);
        return errors.generateHTML(fullHtml ? generateHtmlHeader() : "", fullHtml ? generateHtmlFooter() : "");
    }

    private String generateHtmlHeader() {
        return "<html>" +
               "<header><title>Execution Error for " + System.getProperty(OPT_PROJECT_NAME) + "</title></head>" +
               "<body>";
    }

    private String generateHtmlFooter() {
        return "</body></html>";
    }

    protected void init() { }

    protected File resolveProjectFile(String file) {
        if (StringUtils.isBlank(file)) { return null; }

        String projectBase = Syspath.getExecutionData(OPT_PROJECT_BASE);
        if (StringUtils.isBlank(projectBase)) { return null; }
        if (!FileUtil.isDirectoryReadable(projectBase)) { return null; }

        projectBase = StringUtils.removeEnd(projectBase, separator);
        file = StringUtils.prependIfMissing(file, separator);

        File f = new File(projectBase + file);
        return !FileUtil.isFileReadable(f) ? null : f;
    }
}
