package org.nexial.core.tools;

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


import org.apache.commons.io.FileUtils;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.nexial.core.tools.swagger.*;
import org.springframework.util.ResourceUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import static java.io.File.separator;
import static org.apache.commons.lang3.StringUtils.*;
import static org.nexial.core.NexialConst.*;
import static org.nexial.core.NexialConst.Project.NEXIAL_HOME;

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
    public void generateFiles(String projectDirPath, NexialContents contents, String swaggerPrefix)
            throws IOException {
        createPropertiesFile(projectDirPath, contents.getDataVariables(), swaggerPrefix);
        createScriptAndDataFiles(contents, projectDirPath, swaggerPrefix);
        createBatchFile(projectDirPath, BATCH_FILE_CMD_TEMPLATE, BATCH_FILE_CMD_EXTENSION, swaggerPrefix);
        createBatchFile(projectDirPath, BATCH_FILE_SH_TEMPLATE, BATCH_FILE_SH_EXTENSION, swaggerPrefix);
    }

    /**
     * Creates a data property file in the format of <b>project.<swaggerPrefix>.properties</b>.
     *
     * @param projectDirPath the path of the Nexial project.
     * @param dataVariables  variables containing the content of the file.
     * @throws IOException in case of File operation failures.
     */
    private void createPropertiesFile(String projectDirPath, SwaggerDataVariables dataVariables,
                                      String swaggerPrefix)
            throws IOException {
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

        Map<String, List<String>> requestBodyVars = dataVariables.getRequestBodyVars();
        dataVariables.setRequestBodyVars(getNonEmptyVars(requestBodyVars));
        requestBodyVars = dataVariables.getRequestBodyVars();

        lines.add(join(NL, "#Request Body Variables"));
        for (String name : requestBodyVars.keySet()) {
            for (String var : requestBodyVars.get(name)) {
                lines.add(join(substringBeforeLast(substringAfter(var, TOKEN_START), TOKEN_END), "="));
            }
            lines.add(EMPTY);
        }

        dataVariables.setCookieParams(getNonEmptyVars(dataVariables.getCookieParams()));
        addLinesToPropertyFile(lines, dataVariables.getCookieParams(), "# Cookie Variables");

        dataVariables.setPathParams(getNonEmptyVars(dataVariables.getPathParams()));
        addLinesToPropertyFile(lines, dataVariables.getPathParams(), "# Path Variables");

        dataVariables.setHeaderParams(getNonEmptyVars(dataVariables.getHeaderParams()));
        addLinesToPropertyFile(lines, dataVariables.getHeaderParams(), "# Header Variables");

        dataVariables.setQueryParams(getNonEmptyVars(dataVariables.getQueryParams()));
        addLinesToPropertyFile(lines, dataVariables.getQueryParams(), "# Query Parameter Variables");

        dataVariables.setSecurityVars(getNonEmptyVars(dataVariables.getSecurityVars()));
        addLinesToPropertyFile(lines, dataVariables.getSecurityVars(), "# Authentication Variables");

        dataVariables.setStatusTextVars(getNonEmptyVars(dataVariables.getStatusTextVars()));
        addLinesToPropertyFile(lines, dataVariables.getStatusTextVars(), "# Status Text Variables");

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
        Path templateFilePath = ResourceUtils.getFile("classpath:swagger/" + templateFileName).toPath();
        String fileContent = MessageFormat.format(new String(Files.readAllBytes(templateFilePath)), projectDirPath,
                                                  swaggerPrefix);
        String filePath = joinWith(separator, projectDirPath, "artifact", "bin",
                                   join("run-", swaggerPrefix, fileExtension));
        File batchFile = new File(filePath);
        Files.write(batchFile.toPath(), fileContent.getBytes());
        batchFile.setExecutable(true, false);
    }

    private Map<String, List<String>> getNonEmptyVars(Map<String, List<String>> vars) {
        return vars.entrySet().stream().filter(e -> !e.getValue().isEmpty())
                   .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Add the comments and variables as lines in the properties file.
     *
     * @param lines   lines of the properties files.
     * @param vars    the key value pairs of the properties file.
     * @param comment the comment section of the properties file.
     */
    private void addLinesToPropertyFile(List<String> lines, Map<String, List<String>> vars, String comment) {
        lines.add(join(NL, comment));
        for (String name : vars.keySet()) {
            for (Object var : vars.get(name)) {
                lines.add(join(var, "="));
            }
            lines.add(EMPTY);
        }
    }

    /**
     * Creates the script in the artifact folder and also the corresponding data files.
     *
     * @param nexialContents the swagger swaggerContents content.
     * @param projectDirPath the Nexial project directory path.
     * @throws IOException in case of File operations failure.
     */
    private void createScriptAndDataFiles(NexialContents nexialContents, String projectDirPath,
                                          String swaggerPrefix) throws IOException {
        String scriptFileName = joinWith(separator, projectDirPath, "artifact", "script",
                                         join(swaggerPrefix, EXCEL_EXTENSION));

        String dataFileName = joinWith(separator, projectDirPath, "artifact", "data",
                                       join(swaggerPrefix, ".data", EXCEL_EXTENSION));

        String nexialHome = System.getProperty(NEXIAL_HOME);
        String nexialScriptTemplateFile = joinWith(separator, nexialHome, "template", "nexial-script.xlsx");
        String nexialDataTemplateFile = joinWith(separator, nexialHome, "template", "nexial-data.xlsx");

        FileUtils.copyFile(new File(nexialScriptTemplateFile), new File(scriptFileName));
        FileUtils.copyFile(new File(nexialDataTemplateFile), new File(dataFileName));

        // Logic for Script File creation.
        FileInputStream file = new FileInputStream(scriptFileName);
        XSSFWorkbook scriptWorkBook = new XSSFWorkbook(file);

        XSSFSheet sheet = scriptWorkBook.getSheet("Scenario");
        sheet.removeRow(sheet.getRow(4));
        int index = scriptWorkBook.getSheetIndex("Scenario");

        List<SwaggerScenario> scenarios = nexialContents.getScenarios();

        int scenariosLength = scenarios.size();
        for (int i = 0; i < scenariosLength; i++) {
            SwaggerScenario scenario = scenarios.get(i);
            String scenarioName = scenario.getName();

            if (scriptWorkBook.getSheetIndex(scenarioName) == -1) {
                sheet = scriptWorkBook.cloneSheet(index, scenarioName);
            } else {
                sheet = scriptWorkBook.cloneSheet(index, String.valueOf(i).concat(scenarioName));
            }

            sheet.getRow(1).getCell(0)
                 .setCellValue(scenarios.get(i).getDescription());

            List<SwaggerActivity> activities = scenario.getActivities();
            int rowCount = 3;
            for (SwaggerActivity activity : activities) {
                List<SwaggerStep> steps = activity.getSteps();
                for (SwaggerStep swaggerStep : steps) {
                    XSSFRow row = sheet.createRow(++rowCount);
                    int columnCount = 0;

                    String[] columns = {swaggerStep.getActivityName(), swaggerStep.getDescription(),
                                        swaggerStep.getCmdType(),
                                        swaggerStep.getCmd(), swaggerStep.getParam1(), swaggerStep.getParam2(),
                                        swaggerStep.getParam3(), swaggerStep.getParam4(), swaggerStep.getParam5(),
                                        swaggerStep.getFlowControl()};

                    for (String s : columns) {
                        XSSFCell cell = row.createCell(columnCount++);
                        if (isNotEmpty(s)) {cell.setCellValue(s);}
                    }
                }
            }
        }
        scriptWorkBook.removeSheetAt(index);

        file.close();
        FileOutputStream os = new FileOutputStream(scriptFileName);
        scriptWorkBook.write(os);
        scriptWorkBook.close();
        os.close();
    }
}
