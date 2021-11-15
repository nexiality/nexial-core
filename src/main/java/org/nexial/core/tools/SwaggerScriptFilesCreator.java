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

package org.nexial.core.tools;

import static java.io.File.separator;
import static org.apache.commons.lang3.StringUtils.*;
import static org.nexial.core.NexialConst.DEF_CHARSET;
import static org.nexial.core.NexialConst.NL;
import static org.nexial.core.NexialConst.Project.NEXIAL_HOME;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.nexial.commons.utils.ResourceUtils;
import org.nexial.core.tools.swagger.*;
import org.springframework.http.HttpStatus;

/**
 * This class is used to generate the files related to the Swagger script generation like the script, data and binary
 * files.
 *
 * @author Dhanapathi Marepalli
 */
public class SwaggerScriptFilesCreator {
    private static final String BATCH_FILE_CMD_EXTENSION = ".cmd";
    private static final String BATCH_FILE_SH_EXTENSION = ".sh";
    private static final String EXCEL_EXTENSION = ".xlsx";
    private static final String TIMESTAMP_FORMAT = "yyyy/MMM/dd hh:mm:ss.SSS a";
    private static final String BATCH_FILE_CMD_TEMPLATE = "nexial-swagger.cmd.txt";
    private static final String BATCH_FILE_SH_TEMPLATE = "nexial-swagger.sh.txt";

    /**
     * Generates various files related to the creation of the Swagger scripts for the
     *
     * @param projectDirPath the path of the Nexial project directory.
     * @param contents       the contents to be written to the project files.
     * @param swaggerPrefix  the swagger prefix
     * @throws IOException when the file operation fails.
     */
    public void generateFiles(String projectDirPath, NexialContents contents, String swaggerPrefix, String swaggerFile)
            throws IOException {
        String scriptFile = joinWith(separator, projectDirPath, "artifact", "script",
                                     join(swaggerPrefix, EXCEL_EXTENSION));
        String dataFile = joinWith(separator, projectDirPath, "artifact", "data", join(swaggerPrefix, ".data",
                                                                                       EXCEL_EXTENSION));
        createScriptFile(contents, new File(scriptFile));
        addContentToDataFile(new File(dataFile), contents);
        createPropertiesFile(projectDirPath, contents.getDataVariables(), swaggerPrefix);

        createBatchFile(projectDirPath, BATCH_FILE_CMD_TEMPLATE, BATCH_FILE_CMD_EXTENSION, swaggerPrefix);
        createBatchFile(projectDirPath, BATCH_FILE_SH_TEMPLATE, BATCH_FILE_SH_EXTENSION, swaggerPrefix);

        copySwaggerFileToProjectDir(swaggerPrefix, swaggerFile, projectDirPath);
        displayCreatedFileInfo(projectDirPath, swaggerPrefix, swaggerFile);
    }

    /**
     * Display the logs on the console, stating the files that got created and the further actions he/she needs
     * to perform.
     *
     * @param projectDirPath the Nexial project Directory path.
     * @param prefix         the Swagger prefix.
     */
    private void displayCreatedFileInfo(String projectDirPath, String prefix, String swaggerFile) {
        String projectName = substringAfterLast(projectDirPath, separator);
        System.out.println("Generation completed.\n");
        System.out.println("Nexial .meta directory generated in " + join(projectDirPath, separator, ".meta") + "\n");

        System.out.println("Nexial artifacts generated in " + join(projectDirPath, separator, "artifact"));
        System.out.println("\tbin");
        System.out.println("\t\trun-" + prefix + ".cmd");
        System.out.println("\t\trun-" + prefix + ".sh");

        System.out.println("\tdata");
        System.out.println(join("\t\t", prefix, ".data", EXCEL_EXTENSION));
        System.out.println(
                "\t\t" + joinWith(separator, prefix, join("swagger", ".", substringAfterLast(swaggerFile, "."))) +
                " (The Swagger file)");

        System.out.println("\t\t" + joinWith(separator, prefix, "Schema") + " (The Schema folder)");
        System.out.println("\t\t" + joinWith(separator, prefix, "payload") + " (The payload folder)");

        System.out.println("\tplan");
        System.out.println(join("\t\t", projectName, "-plan", EXCEL_EXTENSION));

        System.out.println("\tscript");
        System.out.println(join("\t\t", prefix, EXCEL_EXTENSION));
        System.out.println("\t" + join("project.", prefix, ".properties") + "\n");

        System.out.println("Before running the script,");
        System.out.println("\tupdate your test data in " + joinWith(separator, projectDirPath, "artifact", "data",
                                                                    join(prefix, ".data", EXCEL_EXTENSION)));

        System.out.println("\tupdate your data variables in " + joinWith(separator, projectDirPath, "artifact",
                                                                         join("project.", prefix, ".properties")));
        System.out.println("\tadjust the generated script as needed - "
                           + joinWith(separator, projectDirPath, "artifact", "script", join(prefix, EXCEL_EXTENSION)) +
                           "\n");

        System.out.println("To run the generated tests, use ");
        String osName = SystemUtils.OS_NAME.toLowerCase();
        System.out.println("\t" + joinWith(separator, projectDirPath, "artifact", "bin",
                                           join("run-", prefix, osName.startsWith("windows") ? ".cmd" : ".sh")));
    }

    /**
     * Validates the following:-
     * <ul>
     *     <li>The {prefix}.script file is open.</li>
     *     <li>The {prefix}.data file is open.</li>
     * </ul>
     * <p>
     * If any of the above validations failed, it aborts the program displaying the appropriate message.
     * <p>
     * In case the conditions are met, it adds necessary files from the templates.
     *
     * @param projectDirPath the Nexial project directory.
     * @param swaggerPrefix  the swagger prefix passed in.
     * @return true in case the validations conditions are met and false otherwise.
     * @throws IOException in case of File operation failures.
     */
    public boolean validateAndGenerateFiles(String projectDirPath, String swaggerPrefix) throws IOException {
        String dataFile = joinWith(separator, projectDirPath, "artifact", "data",
                                   join(swaggerPrefix, ".data", EXCEL_EXTENSION));
        boolean fileCopied = copyTemplateFile(dataFile, "nexial-data.xlsx");
        if (!fileCopied) {return false;}

        String scriptFile = joinWith(separator, projectDirPath, "artifact", "script",
                                     join(swaggerPrefix, EXCEL_EXTENSION));
        fileCopied = copyTemplateFile(scriptFile, "nexial-script.xlsx");
        if (!fileCopied) {return false;}

        File projectDir = new File(projectDirPath);
        addMissingProjectFiles(projectDir);
        return true;
    }

    /**
     * Creates the missing files/directories in the Nexial project directory.
     *
     * @param dir the Nexial project directory.
     * @throws IOException In case of File operation failures.
     */
    private void addMissingProjectFiles(File dir) throws IOException {
        File artifact = new File(joinWith(separator, dir.getAbsolutePath(), "artifact"));
        File propertiesFile = new File(joinWith(separator, artifact.getAbsolutePath(), "project.properties"));
        if (!propertiesFile.exists()) {
            boolean propertiesFileCreated = propertiesFile.createNewFile();
            if (!propertiesFileCreated) {
                System.err.println("Properties file" + propertiesFile.getAbsolutePath() + " failed to create.");
            }
        }

        File metaDir = new File(joinWith(separator, dir.getAbsolutePath(), ".meta"));
        if (!metaDir.exists()) {
            boolean dirCreated = metaDir.mkdir();
            if (dirCreated) {
                File projectIdFile = new File(joinWith(separator, metaDir.getAbsolutePath(), "project.id"));
                boolean fileCreated = projectIdFile.createNewFile();
                if (fileCreated) {
                    FileUtils.writeStringToFile(projectIdFile, substringAfterLast(dir.getAbsolutePath(), separator),
                                                DEF_CHARSET);
                } else {
                    System.err.println("Failed to created file " + projectIdFile.getAbsolutePath());
                }
            } else {
                System.err.println("Failed to created folder " + metaDir.getAbsolutePath());
            }
        }

        Arrays.stream(new String[]{"plan", "bin"})
              .forEach(folder -> {
                  File file = new File(joinWith(separator, artifact, folder));
                  if (!file.exists()) {
                      boolean fileCreated = file.mkdir();
                      if (!fileCreated) {
                          System.err.println("Failed to create directory " + file.getAbsolutePath());
                      }
                  }
              });

        createNexialProjectFile(artifact, "script", EXCEL_EXTENSION, "nexial-script.xlsx");
        createNexialProjectFile(artifact, "script", ".macro.xlsx", "nexial-macro.xlsx");
        createNexialProjectFile(artifact, "data", ".data.xlsx", "nexial-data.xlsx");
        createNexialProjectFile(artifact, "plan", "-plan.xlsx", "nexial-testplan.xlsx");
    }

    /**
     * Creates the missing Nexial project files.
     *
     * @param artifact         the artifact directory of the Nexial project.
     * @param directory        the target directory in the artifact folder.
     * @param extension        the extension of the file name.
     * @param templateFileName the template file name.
     * @throws IOException in case of File operation failures.
     */
    private void createNexialProjectFile(File artifact, String directory, String extension,
                                         String templateFileName) throws IOException {
        String templateDir = joinWith(separator, System.getProperty(NEXIAL_HOME), "template");
        String projectName = substringAfterLast(substringBeforeLast(artifact.getAbsolutePath(), separator), separator);

        File file = new File(joinWith(separator, artifact.getAbsolutePath(), directory, join(projectName, extension)));
        if (!file.exists()) {FileUtils.copyFile(new File(join(templateDir, separator, templateFileName)), file);}
    }

    /**
     * Replaces the current file with the template file passed in.
     *
     * @param file         the file to be replaced.
     * @param templateFile the template file which has to replace the actual file.
     * @return true/false based on the copy operation is successful or not.
     * @throws IOException in case the source file is open.
     */
    private boolean copyTemplateFile(String file, String templateFile) throws IOException {
        String nexialDataTemplateFile = joinWith(separator, System.getProperty(NEXIAL_HOME),
                                                 "template", templateFile);
        try {
            FileUtils.copyFile(new File(nexialDataTemplateFile), new File(file));
        } catch (IOException e) {
            if (e.getMessage()
                 .contains("The process cannot access the file because it is being used by another process")) {
                System.err.println(file + " is open. Please close it and run the command again.");
                return false;
            }
            throw e;
        }
        return true;
    }

    /**
     * Copy the Swagger file to the <b>Swagger</b> directory inside the <b>data</b> folder with the name in the format
     * <b>{swaggerPrefix}</b>.<b>{fileName}</b>
     *
     * @param swaggerPrefix  the Swagger prefix passed in.
     * @param swaggerFile    the Swagger file containing the definitions of the Rest API passed in.
     * @param projectDirPath the Nexial project directory passed in.
     * @throws IOException In case of File operations failure.
     */
    private void copySwaggerFileToProjectDir(String swaggerPrefix, String swaggerFile, String projectDirPath)
            throws IOException {
        File swaggerDefinitionsFile =
                new File(joinWith(separator, projectDirPath, "artifact", "data", swaggerPrefix),
                         join("swagger", ".", substringAfterLast(swaggerFile, ".")));
        FileUtils.copyFile(new File(swaggerFile), swaggerDefinitionsFile);
    }

    /**
     * Creates a data property file in the format of <b>project.<swaggerPrefix>.properties</b>.
     *
     * @param projectDirPath the path of the Nexial project.
     * @param dataVariables  variables containing the content of the file.
     * @throws IOException in case of File operation failures.
     */
    private void createPropertiesFile(String projectDirPath, SwaggerDataVariables dataVariables,
                                      String swaggerPrefix) throws IOException {
        File propertiesFile = new File(joinWith(separator,
                                                removeEnd(projectDirPath, separator), "artifact",
                                                joinWith(".", "project", swaggerPrefix, "properties")));
        String timestamp = new SimpleDateFormat(TIMESTAMP_FORMAT).format(Calendar.getInstance().getTime());

        List<String> lines = new ArrayList<>();
        lines.add("# This file is created on " + timestamp);
        lines.add("# Base url is " + dataVariables.getBaseUrl() + NL);

        Map<String, String> nexialProperties = new LinkedHashMap<String, String>() {{
            put("nexial.delayBetweenStepsMs", "0");
            put("nexial.ws.logDetail", "true");
            put("nexial.ws.logSummary", "true");
            put("nexial.ws.connectionTimeout", "30000");
            put("nexial.ws.readTimeout", "30000");
        }};

        nexialProperties.forEach((x, y) -> lines.add(join(x, "=", y)));
        lines.add(join(NL, "baseUrl=", dataVariables.getBaseUrl()));
        lines.add(join(NL, "payloadBase=", joinWith("/", "$(syspath|data|fullpath)", swaggerPrefix, "payload")));
        lines.add(join(NL, "schemaBase=", joinWith("/", "$(syspath|data|fullpath)", swaggerPrefix, "Schema")));

        dataVariables.setSecurityVars(getNonEmptyVars(dataVariables.getSecurityVars()));
        addLinesToPropertyFile(lines, dataVariables.getSecurityVars());

        StringBuilder fileContent = new StringBuilder(EMPTY);
        lines.forEach(line -> fileContent.append(line).append(NL));
        Files.write(propertiesFile.toPath(), fileContent.toString().getBytes());
    }

    /**
     * Create a cmd or sh file to run the script generated based on the operating system used.
     * The file name will be <b>run-{swaggerPrefix}.cmd</b> or <b>run-{swaggerPrefix}.sh</b>.
     *
     * @param projectDirPath   the Nexial project directory.
     * @param templateFileName the name of the template file located in the src/resources folder.
     * @param fileExtension    the file extension like ".cmd" or ".sh".
     * @throws IOException in case of File operation failures.
     */
    private void createBatchFile(String projectDirPath, String templateFileName, String fileExtension,
                                 String swaggerPrefix)
            throws IOException {

        String content = ResourceUtils.loadResource("/swagger/" + templateFileName);
        String fileContent = MessageFormat.format(Objects.requireNonNull(content), projectDirPath, swaggerPrefix);
        String filePath = joinWith(separator, projectDirPath, "artifact", "bin",
                                   join("run-", swaggerPrefix, fileExtension));
        File batchFile = new File(filePath);
        Files.write(batchFile.toPath(), fileContent.getBytes());
        boolean fileExecutable = batchFile.setExecutable(true, false);
        if (!fileExecutable) {
            System.err.println("Failed to make file as executable.");
        }
    }

    /**
     * Extract the non-empty variables in the map.
     *
     * @param vars variables passed in.
     * @return non empty variables.
     */
    private Map<String, List<String>> getNonEmptyVars(Map<String, List<String>> vars) {
        return vars.entrySet().stream().filter(e -> !e.getValue().isEmpty())
                   .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Add the comments and variables as lines in the properties file.
     *
     * @param lines lines of the properties files.
     * @param vars  the key value pairs of the properties file.
     */
    private void addLinesToPropertyFile(List<String> lines, Map<String, List<String>> vars) {
        lines.add(join(NL, "# Authentication Variables"));
        for (String name : vars.keySet()) {
            for (Object var : vars.get(name)) {
                lines.add(join(var, "="));
            }
            lines.add(EMPTY);
        }
    }

    /**
     * Creates the script in the artifact folder.
     *
     * @param nexialContents the swagger swaggerContents content.
     * @param scriptFile     the script file path.
     * @throws IOException in case of File operations failure.
     */
    private void createScriptFile(NexialContents nexialContents, File scriptFile) throws IOException {
        FileInputStream file = new FileInputStream(scriptFile);
        XSSFWorkbook scriptWorkBook = new XSSFWorkbook(file);

        XSSFSheet sheet;
        int index = scriptWorkBook.getSheetIndex("Scenario");
        List<SwaggerScenario> scenarios = nexialContents.getScenarios();

        for (SwaggerScenario scenario : scenarios) {
            String scenarioName = scenario.getName();
            sheet = scriptWorkBook.cloneSheet(index, scenarioName);

            sheet.getRow(1).getCell(0)
                 .setCellValue(scenario.getDescription());

            List<SwaggerActivity> activities = scenario.getActivities();
            int rowCount = 3;
            for (SwaggerActivity activity : activities) {
                List<SwaggerStep> steps = activity.getSteps();
                for (SwaggerStep swaggerStep : steps) {
                    XSSFRow row = sheet.getRow(++rowCount);
                    int columnCount = 0;

                    String[] columns = {swaggerStep.getActivityName(), swaggerStep.getDescription(),
                                        swaggerStep.getCmdType(),
                                        swaggerStep.getCmd(), swaggerStep.getParam1(), swaggerStep.getParam2(),
                                        swaggerStep.getParam3(), swaggerStep.getParam4(), swaggerStep.getParam5(),
                                        swaggerStep.getFlowControl()};

                    for (String s : columns) {
                        XSSFCell cell = row.getCell(columnCount++);
                        if (isNotEmpty(s)) {cell.setCellValue(s);}
                    }
                }
            }
        }
        scriptWorkBook.removeSheetAt(index);

        file.close();
        FileOutputStream os = new FileOutputStream(scriptFile);
        scriptWorkBook.write(os);
        scriptWorkBook.close();
        os.close();
    }

    /**
     * Adds the content(variables defined in the scenarioVars) to the data file passed in.
     *
     * @param dataFile       the data file to which contents needs to be written.
     * @param nexialContents the {@link NexialContents}.
     * @throws IOException in case of File operations failure.
     */
    private void addContentToDataFile(File dataFile, NexialContents nexialContents)
            throws IOException {
        FileInputStream file = new FileInputStream(dataFile);
        XSSFWorkbook scriptWorkBook = new XSSFWorkbook(file);
        int index = scriptWorkBook.getSheetIndex("#default");

        Map<String, List<SwaggerScenarioVars>> scenarioVars = nexialContents.getSwaggerScenarioVarsMap();
        for (String scenarioName : scenarioVars.keySet()) {
            XSSFSheet sheet = scriptWorkBook.cloneSheet(index, scenarioName);
            IntStream.rangeClosed(0, 6).forEach(x -> sheet.removeRow(sheet.getRow(x)));

            int rowCount = 0;
            for (SwaggerScenarioVars vars : scenarioVars.get(scenarioName)) {
                String activityName = vars.getActivityName();

                Map<String, String> content = new LinkedHashMap<String, String>() {{
                    if (isNotEmpty(vars.getJson())) {put(joinWith(".", activityName, "json"), vars.getJson());}

                    String statusText =
                            HttpStatus.valueOf(Integer.parseInt(activityName.split("\\.")[1])).getReasonPhrase();
                    put(joinWith(".", activityName, "statusText"), statusText);
                    put(joinWith(".", activityName, "url"), vars.getUrl());
                    if (isNotEmpty(vars.getSchema())) {put(joinWith(".", activityName, "schema"), vars.getSchema());}
                }};

                vars.getCookieParams().forEach(x -> content.put(x, ""));
                vars.getHeaderParams().forEach(x -> content.put(x, ""));
                vars.getQueryParams().forEach(x -> content.put(x, ""));
                vars.getPathParams().forEach(x -> content.put(x, ""));

                for (String key : content.keySet()) {
                    XSSFRow row = sheet.createRow(rowCount++);
                    row.createCell(0).setCellValue(key);
                    row.createCell(1).setCellValue(content.get(key));
                }
            }
        }

        file.close();
        FileOutputStream os = new FileOutputStream(dataFile);
        scriptWorkBook.write(os);
        scriptWorkBook.close();
        os.close();
    }
}
