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
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.utils.FileUtil;
import org.nexial.commons.utils.RegexUtils;
import org.nexial.core.ExecutionThread;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.utils.ConsoleUtils;

import com.mchange.v2.util.CollectionUtils;

import static java.io.File.separator;
import static org.nexial.core.NexialConst.*;

/**
 * built-in function to handle project-specific files and file content.
 */
public class ProjectFile {

    public static final String REGEX_LOG_FORMAT = "^\\[(.+)\\]" +   // log date/time
                                                  "\\[(.+?)\\]" +    // script
                                                  "\\[(.+?)\\]" +    // scenario
                                                  "\\[(.+?)\\]" +    // activity
                                                  "\\[(.+)\\]" +    // step
                                                  "\\[(.+)\\]" +    // command type
                                                  "\\[(.+)\\]" +    // command
                                                  "\\[(.+)\\]" +    // error screenshot
                                                  "\\[(.+?)\\]" +   // detailed log
                                                  "\\:?\\s*(.*)$";  // log message

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
        StringBuilder html = new StringBuilder((fullHtml ? generateHtmlHeader() : "") +
                                               "<table width=\"100%\" class=\"execution-errors\" cellspacing=\"0\">" +
                                               "<thead><tr>" +
                                               "<th nowrap>date/time</th>" +
                                               "<th nowrap>script</th>" +
                                               "<th nowrap>scenario</th>" +
                                               "<th nowrap>activity</th>" +
                                               "<th nowrap>step</th>" +
                                               "<th nowrap>command</th>" +
                                               "<th>screenshot</th>" +
                                               "<th>details</th>" +
                                               "<th>message</th>" +
                                               "</tr></thead>" +
                                               "<tbody>");

        try {
            String dateTime = "";
            String script = "";
            String scenario = "";
            String activity = "";
            String step = "";
            String commandType = "";
            String commandName = "";
            String screenshot = "";
            String detailedLog = "";
            String message = "";

            List<String> logContent = FileUtils.readLines(log, DEF_FILE_ENCODING);
            for (String line : logContent) {
                List<String> logParts = RegexUtils.collectGroups(line, REGEX_LOG_FORMAT);
                if (CollectionUtils.size(logParts) != 10) {
                    // not fitting to the expected log format: we'd assume this line is the bleed-over from previous log
                    message += line + NL;
                } else {
                    // before parsing, let's store away previously parsed log info.
                    if (StringUtils.isNotBlank(message)) {
                        String screenshotLink = StringUtils.equals(screenshot, "-") || StringUtils.isBlank(screenshot) ?
                                                "&nbsp;" : "<a href=\"" + screenshot + "\">screenshot</a>";
                        String detailedLogLink = StringUtils.equals(detailedLog, "-") ||
                                                 StringUtils.isBlank(detailedLog) ?
                                                 "&nbsp;" : "<a href=\"" + detailedLog + "\">details</a>";
                        html.append("<tr>")
                            .append("<td nowrap>").append(dateTime).append("</td>")
                            .append("<td nowrap>").append(script).append("</td>")
                            .append("<td nowrap>").append(scenario).append("</td>")
                            .append("<td nowrap>").append(activity).append("</td>")
                            .append("<td nowrap>").append(step).append("</td>")
                            .append("<td nowrap>").append(commandType).append(" &raquo; ").append(commandName).append("</td>")
                            .append("<td>").append(screenshotLink).append("</td>")
                            .append("<td>").append(detailedLogLink).append("</td>")
                            .append("<td><pre>").append(message).append("</pre></td>")
                            .append("</tr>");
                    }

                    dateTime = logParts.get(0);
                    script = extractScriptName(logParts.get(1));
                    scenario = logParts.get(2);
                    activity = logParts.get(3);
                    step = logParts.get(4);
                    commandType = logParts.get(5);
                    commandName = logParts.get(6);
                    screenshot = logParts.get(7);
                    detailedLog = logParts.get(8);
                    message = StringUtils.removeStart(logParts.get(9), ": ");
                }
            }

            if (StringUtils.isNotBlank(message)) {
                String screenshotLink = StringUtils.equals(screenshot, "-") || StringUtils.isBlank(screenshot) ?
                                        "&nbsp;" : "<a href=\"" + screenshot + "\">screenshot</a>";
                String detailedLogLink = StringUtils.equals(detailedLog, "-") || StringUtils.isBlank(detailedLog) ?
                                         "&nbsp;" : "<a href=\"" + detailedLog + "\">details</a>";
                html.append("<tr>")
                    .append("<td>").append(dateTime).append("</td>")
                    .append("<td>").append(script).append("</td>")
                    .append("<td>").append(scenario).append("</td>")
                    .append("<td>").append(activity).append("</td>")
                    .append("<td>").append(step).append("</td>")
                    .append("<td>").append(commandType).append(" &raquo; ").append(commandName).append("</td>")
                    .append("<td>").append(screenshotLink).append("</td>")
                    .append("<td>").append(detailedLogLink).append("</td>")
                    .append("<td><pre>").append(message).append("</pre></td>")
                    .append("</tr>");
            }

            html.append("</tbody></table>");

            return html.toString() + (fullHtml ? generateHtmlFooter() : "");
        } catch (IOException e) {
            ConsoleUtils.error("Unable to read content from '" + logFile + "': " + e.getMessage());
            return "";
        }
    }

    private String extractScriptName(String scriptFileName) {
        scriptFileName = StringUtils.removeEnd(scriptFileName, ".xlsx");
        scriptFileName = RegexUtils.replace(scriptFileName, "\\.\\d{8}_\\d{6}\\.\\d+$", "");
        return StringUtils.trim(scriptFileName);
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
        String fullpath = projectBase + file;

        if (!FileUtil.isFileReadable(fullpath)) { return null; }

        return new File(fullpath);
    }
}
