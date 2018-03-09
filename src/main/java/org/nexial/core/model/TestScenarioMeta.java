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

package org.nexial.core.model;

import java.io.IOException;

import org.apache.poi.xssf.usermodel.XSSFCell;

import org.nexial.core.excel.Excel.Worksheet;
import org.nexial.core.excel.ExcelAddress;

import static org.apache.poi.ss.usermodel.CellType.STRING;

public class TestScenarioMeta {
    private static final ExcelAddress ADDR_DESCRIPTION = new ExcelAddress("A2");
    private static final ExcelAddress ADDR_PROJECT = new ExcelAddress("E2");
    private static final ExcelAddress ADDR_RELEASE = new ExcelAddress("F2");
    private static final ExcelAddress ADDR_JIRA = new ExcelAddress("G2");
    private static final ExcelAddress ADDR_ZEPHYR = new ExcelAddress("H2");
    private static final ExcelAddress ADDR_AUTHOR = new ExcelAddress("I2");

    private Worksheet worksheet;
    private String description;
    private String project;
    private String release;
    private String featureRef;
    private String testRef;
    private String author;

    private TestScenarioMeta(Worksheet worksheet) {
        this.worksheet = worksheet;
        parse();
    }

    public static TestScenarioMeta newInstance(Worksheet worksheet) { return new TestScenarioMeta(worksheet); }

    public String getDescription() { return description; }

    public void setDescription(String description) { this.description = description; }

    public String getProject() { return project; }

    public void setProject(String project) { this.project = project; }

    public String getRelease() { return release; }

    public void setRelease(String release) { this.release = release; }

    public String getFeatureRef() { return featureRef; }

    public void setFeatureRef(String featureRef) { this.featureRef = featureRef; }

    public String getTestRef() { return testRef; }

    public void setTestRef(String testRef) { this.testRef = testRef; }

    public String getAuthor() { return author; }

    public void setAuthor(String author) { this.author = author; }

    public void save() throws IOException {
        XSSFCell cell = worksheet.cell(ADDR_DESCRIPTION);
        if (cell != null) { cell.setCellValue(description); }

        cell = worksheet.cell(ADDR_PROJECT);
        if (cell != null) { cell.setCellValue(project); }

        cell = worksheet.cell(ADDR_RELEASE);
        if (cell != null) { cell.setCellValue(release); }

        cell = worksheet.cell(ADDR_JIRA);
        if (cell != null) { cell.setCellValue(featureRef); }

        cell = worksheet.cell(ADDR_ZEPHYR);
        if (cell != null) { cell.setCellValue(testRef); }

        cell = worksheet.cell(ADDR_AUTHOR);
        if (cell != null) { cell.setCellValue(author); }

        worksheet.save();
    }

    private void parse() {
        XSSFCell cell = worksheet.cell(ADDR_DESCRIPTION);
        if (cell != null) { description = readCellValueAsString(cell); }

        cell = worksheet.cell(ADDR_PROJECT);
        if (cell != null) { project = readCellValueAsString(cell); }

        cell = worksheet.cell(ADDR_RELEASE);
        if (cell != null) { release = readCellValueAsString(cell); }

        cell = worksheet.cell(ADDR_JIRA);
        if (cell != null) { featureRef = readCellValueAsString(cell); }

        cell = worksheet.cell(ADDR_ZEPHYR);
        if (cell != null) { testRef = readCellValueAsString(cell); }

        cell = worksheet.cell(ADDR_AUTHOR);
        if (cell != null) { author = readCellValueAsString(cell); }
    }

    private String readCellValueAsString(XSSFCell cell) {
        if (cell.getCellTypeEnum() == STRING) { return cell.getStringCellValue(); }
        return cell.getRawValue();
    }

}
