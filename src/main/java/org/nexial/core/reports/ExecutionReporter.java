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

package org.nexial.core.reports;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.proc.ProcessInvoker;
import org.nexial.core.model.ExecutionSummary;
import org.nexial.core.utils.ConsoleUtils;
import org.nexial.core.utils.ExecUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import static org.apache.commons.lang3.SystemUtils.IS_OS_MAC;
import static org.apache.commons.lang3.SystemUtils.IS_OS_WINDOWS;
import static org.nexial.core.NexialConst.*;
import static org.nexial.core.NexialConst.Data.WIN32_CMD;

public class ExecutionReporter {
    private TemplateEngine templateEngine;
    private String executionTemplate;
    private String reportPath;
    private String htmlOutputFile;
    private String detailJsonFile;
    private String summaryJsonFile;
    private String junitFile;

    public void setTemplateEngine(TemplateEngine templateEngine) { this.templateEngine = templateEngine; }

    public void setExecutionTemplate(String executionTemplate) { this.executionTemplate = executionTemplate; }

    public void setReportPath(String reportPath) { this.reportPath = reportPath; }

    public void setHtmlOutputFile(String htmlOutputFile) { this.htmlOutputFile = htmlOutputFile;}

    public void setDetailJsonFile(String detailJsonFile) { this.detailJsonFile = detailJsonFile;}

    public void setSummaryJsonFile(String summaryJsonFile) { this.summaryJsonFile = summaryJsonFile;}

    public void setJunitFile(String junitFile) { this.junitFile = junitFile; }

    public File generateHtml(ExecutionSummary summary) throws IOException {
        if (summary == null) { return null; }

        File output = new File(reportPath + htmlOutputFile);
        ConsoleUtils.log("generating HTML output for this execution to " + output.getAbsolutePath());

        Context engineContext = new Context();
        engineContext.setVariable("execution", ExecutionSummary.gatherExecutionData(summary));
        engineContext.setVariable("summary", summary);

        String content = templateEngine.process(executionTemplate, engineContext);
        if (StringUtils.isNotBlank(content)) {
            FileUtils.writeStringToFile(output, content, DEF_FILE_ENCODING);
            return output;
        } else {
            ConsoleUtils.error("No HTML content generated for this execution...");
            return null;
        }
    }

    public List<File> generateJson(ExecutionSummary summary) throws IOException {
        List<File> jsons = new ArrayList<>();

        File detailJson = new File(reportPath + detailJsonFile);
        FileUtils.writeStringToFile(detailJson, GSON_COMPRESSED.toJson(summary), DEF_CHARSET);
        jsons.add(detailJson);

        ExecutionSummary report = summary.toSummary();
        if (report != null) {
            File summaryJson = new File(reportPath + summaryJsonFile);
            FileUtils.writeStringToFile(summaryJson, GSON_COMPRESSED.toJson(report), DEF_CHARSET);
            jsons.add(summaryJson);
        }

        return jsons;
    }

    public File generateJUnitXml(ExecutionSummary summary) throws IOException {
        if (summary == null) { return null; }

        File output = new File(reportPath + junitFile);
        ConsoleUtils.log("generating JUnit XML output for this execution to " + output.getAbsolutePath());

        // convert to JUnit XML
        JUnitReportHelper.toJUnitXml(summary, output);

        return output;
    }

    public void openReport(File report) { if (report != null) { openReport(report.getAbsolutePath()); } }

    public void openReport(String reportFile) {
        if (StringUtils.isBlank(reportFile)) { return; }
        if (ExecUtils.isRunningInZeroTouchEnv()) { return; }

        try {
            if (IS_OS_MAC) {
                ProcessInvoker.invokeNoWait("open", Collections.singletonList(reportFile), null);
                return;
            }

            if (IS_OS_WINDOWS) {
                // https://superuser.com/questions/198525/how-can-i-execute-a-windows-command-line-in-background
                // start "" [program]... will cause CMD to exit before program executes.. sorta like running program in background
                ProcessInvoker.invokeNoWait(WIN32_CMD, Arrays.asList("/C", "\"\"", "\"" + reportFile + "\""), null);
            }
        } catch (IOException e) {
            ConsoleUtils.error("ERROR!!! Can't open " + reportFile + ": " + e.getMessage());
        }
    }
}
