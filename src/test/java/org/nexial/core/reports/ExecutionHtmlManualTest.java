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
import java.util.Arrays;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.proc.ProcessInvoker;
import org.nexial.commons.utils.ResourceUtils;
import org.nexial.core.model.ExecutionSummary;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import static java.io.File.separator;
import static org.apache.commons.lang3.SystemUtils.JAVA_IO_TMPDIR;
import static org.nexial.core.NexialConst.DEF_FILE_ENCODING;
import static org.nexial.core.NexialConst.GSON;

public class ExecutionHtmlManualTest {
    private String resourcePath = "/" + StringUtils.replace(this.getClass().getPackage().getName(), ".", "/") + "/";
    // private String template = ResourceUtils.getResourceFilePath(resourcePath + "execution_summary.html");
    private String json = ResourceUtils.getResourceFilePath(resourcePath + "execution-detail.json");
    private File outputFile = new File(JAVA_IO_TMPDIR + separator + "test-execution.html");

    public static void main(String[] args) throws Throwable {
        ExecutionHtmlManualTest test = new ExecutionHtmlManualTest();
        test.generateHtml();
    }

    private void generateHtml() throws Exception {
        ClassLoaderTemplateResolver templateResolver = new ClassLoaderTemplateResolver();
        templateResolver.setTemplateMode("HTML5");
        templateResolver.setSuffix(".html");
        templateResolver.setPrefix("org/nexial/core/reports/");

        TemplateEngine templateEngine = new TemplateEngine();
        templateEngine.setTemplateResolver(templateResolver);

        ExecutionSummary summary = GSON.fromJson(FileUtils.readFileToString(new File(json), DEF_FILE_ENCODING),
                                                 ExecutionSummary.class);

        Context engineContext = new Context();
        engineContext.setVariable("execution", ExecutionSummary.gatherExecutionData(summary));
        engineContext.setVariable("summary", summary);

        String content = templateEngine.process("execution_summary", engineContext);
        FileUtils.writeStringToFile(outputFile, content, DEF_FILE_ENCODING);
        ProcessInvoker.invokeNoWait("open", Arrays.asList(outputFile.getAbsolutePath()), null);
    }
}
