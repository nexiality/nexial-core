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

package org.nexial.core.variable;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.utils.FileUtil;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.ExecutionThread;
import org.nexial.core.logs.StepErrors;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.Macro;
import org.nexial.core.utils.ConsoleUtils;
import org.nexial.core.utils.OutputFileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static java.io.File.separator;
import static java.lang.System.lineSeparator;
import static org.nexial.core.NexialConst.*;

/**
 * built-in function to handle project-specific files and file content.
 *
 * <b>NOTE</b>: unit tests added to unitTest_function.xlsx
 */
public class ProjectFile {
    private static final String PROJECT_PROP_BASE = "artifact/project";
    private static final String PROJECT_PROP_EXT = ".properties";
    private static final String ENV_WRAPPER = "::";

    /**
     * read the text of a file within current project directory. File content will be processed by Nexial for
     * token replacement.
     */
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

    /**
     * stringify a macro reference based on its file (relative or absolute), its library and macro name. For example,
     * <p>
     * <pre>${projectfile|macro|file|sheet|name)</pre>
     */
    public String macro(String file, String sheet, String name) { return new Macro(file, sheet, name).toString(); }

    public String executionErrorsAsHtml(String tableOnly) {
        String logFile = StringUtils.appendIfMissing(Syspath.getExecutionData(OPT_OUT_DIR), separator) +
                         SUBDIR_LOGS + separator + ERROR_TRACKER;
        File log = new File(logFile);
        if (!FileUtil.isFileReadable(log, 32)) { return ""; }

        boolean fullHtml = !BooleanUtils.toBoolean(tableOnly);
        StepErrors errors = new StepErrors(log);
        return errors.generateHTML(fullHtml ? generateHtmlHeader() : "", fullHtml ? generateHtmlFooter() : "");
    }

    /**
     * read project property from a `project.properties` file. Use `::ENV::` prefix in `name` parameter to point
     * to an environment specific project.properties file. For example,
     *
     * <pre>$(projectfile|projectProperty|::QA::base.url)</pre>
     * <p>
     * means to read "base.url" property from `project.QA.properties` file under the artifact directory.
     */
    public String projectProperty(String name) {
        // consider:
        // $(projectfile|projectProperty|name)
        // $(projectfile|projectProperty|::QA::name)

        if (StringUtils.isBlank(name)) { return null; }

        File propFile = deriveProjectProperties(name);
        if (propFile == null) { return null; }

        String linePrefix = deriveProjectPropertyName(name) + "=";
        try {
            return StringUtils.substringAfter(
                FileUtils.readLines(propFile, DEF_FILE_ENCODING).stream()
                         .filter(line -> StringUtils.startsWith(line, linePrefix))
                         .findFirst().orElse(""),
                linePrefix);
        } catch (IOException e) {
            // unable to read the file?
            ConsoleUtils.error("Unable to read " + propFile + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * write/override a project property from a `project.properties` file. Use `::ENV::` prefix in `name` parameter to
     * point to an environment specific project.properties file. For example,
     *
     * <pre>$(projectfile|projectProperty|::QA::base.url|https://does.not.exist)</pre>
     * <p>
     * means to write "base.url" property on `project.QA.properties` file under the artifact directory.
     */
    public String projectProperty(String name, String value) {
        // consider
        // $(projectfile|projectProperty|name|value)
        // $(projectfile|projectProperty|::QA::name|value)

        if (StringUtils.isBlank(name)) { return null; }

        File propFile = deriveProjectProperties(name);
        if (propFile == null) { return null; }

        List<String> mods = new ArrayList<>();
        AtomicReference<Boolean> modified = new AtomicReference<>(false);
        String nameMod = deriveProjectPropertyName(name);
        String linePrefix = nameMod + "=";

        List<String> newContent;
        try {
            newContent = FileUtils.readLines(propFile, DEF_FILE_ENCODING).stream()
                                  .map(line -> {
                                      if (StringUtils.startsWith(line, linePrefix)) {
                                          modified.set(true);
                                          mods.add(StringUtils.substringAfter(line, linePrefix));

                                          // need to handle null and empty value
                                          if (StringUtils.isEmpty(value)) {
                                              ConsoleUtils.log("removing properties " + nameMod);
                                              return null;
                                          } else {
                                              return linePrefix + value;
                                          }
                                      } else {
                                          return line;
                                      }
                                  })
                                  .filter(Objects::nonNull)
                                  .collect(Collectors.toList());
            // add new property to properties file?
            if (!modified.get()) { newContent.add(linePrefix + value); }
        } catch (IOException e) {
            ConsoleUtils.error("Error occurred while reading " + propFile + ": " + e.getMessage());
            ConsoleUtils.error(propFile + " not modified");
            return null;
        }

        // backup existing project properties
        ExecutionContext context = ExecutionThread.get();
        String backupFileName = StringUtils.substringBefore(propFile.getName(), PROJECT_PROP_EXT);
        if (context != null) {
            backupFileName += OutputFileUtils.generateOutputFilename(context.getCurrentTestStep(), PROJECT_PROP_EXT);
        } else {
            backupFileName += DF_TIMESTAMP.format(new Date()) + PROJECT_PROP_EXT;
        }
        String backup = StringUtils.appendIfMissing(new Syspath().out("fullpath"), "/") + backupFileName;

        try {
            FileUtils.copyFile(propFile, new File(backup));
            FileUtils.writeLines(propFile, newContent, lineSeparator());
        } catch (IOException e) {
            // could be reading properties file or backing up properties file
            ConsoleUtils.log("Error occurred while writing to " + propFile + ": " + e.getMessage());
        }

        return TextUtils.toString(mods, lineSeparator());
    }

    protected void init() { }

    private String deriveProjectPropertyName(String name) {
        String env = StringUtils.substringBetween(name, ENV_WRAPPER);
        return StringUtils.isEmpty(env) ? name : StringUtils.substringAfter(name, ENV_WRAPPER + env + ENV_WRAPPER);
    }

    /**
     * find the right project.properties based on "name", which might be prefixed with env. name (enclosed between "::")
     */
    private File deriveProjectProperties(String name) {
        String env = StringUtils.substringBetween(name, ENV_WRAPPER);
        return resolveProjectFile(PROJECT_PROP_BASE + (StringUtils.isBlank(env) ? "" : "." + env) + PROJECT_PROP_EXT);
    }

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

    private String generateHtmlHeader() {
        return "<html>" +
               "<header><title>Execution Error for " + System.getProperty(OPT_PROJECT_NAME) + "</title></head>" +
               "<body>";
    }

    private String generateHtmlFooter() {
        return "</body></html>";
    }
}
