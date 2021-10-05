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

package org.nexial.core.tms.model;

import java.util.List;
import org.apache.commons.io.FilenameUtils;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.nexial.core.excel.Excel.Worksheet;

import static org.nexial.core.excel.ExcelConfig.ADDR_TEST_ID;

/**
 * Represents a single TMS test case, mapped to a scenario in Nexial Script
 */
public class TmsTestCase {
    private final Worksheet worksheet;
    private final String name;
    private String tmsIdRef;
    private String testCaseName;
    private final String scriptName;
    private String row; // only to be used in case of plan import
    private List<TmsTestStep> testSteps;

    public String getTestCaseName() {
        return testCaseName;
    }

    public void setTestCaseName(String testCaseName) {
        this.testCaseName = testCaseName;
    }

    public TmsTestCase(Worksheet worksheet) {
        this.worksheet = worksheet;
        name           = worksheet.getName();
        scriptName = FilenameUtils.removeExtension(worksheet.getFile().getName());
        tmsIdRef   = worksheet.cell(ADDR_TEST_ID).getStringCellValue();
    }

    public Worksheet getWorksheet()                    { return worksheet; }

    public String getName()                            { return name; }

    public String getTmsIdRef()              { return tmsIdRef; }

    public void setTmsIdRef(String tmsIdRef) { this.tmsIdRef = tmsIdRef; }

    public String getScriptName()                      { return scriptName; }

    public String getRow()                             { return row; }

    public void setRow(String row)                     { this.row = row; }

    public List<TmsTestStep> getTestSteps() {
        return testSteps;
    }

    public void setTestSteps(List<TmsTestStep> testSteps) {
        this.testSteps = testSteps;
    }

    /**
     * Write the test case id into the Test Id cell of the current worksheet
     */
    public void writeToFile() {
        XSSFCellStyle xssfCellStyle = worksheet.newCellStyle();
        xssfCellStyle.setAlignment(HorizontalAlignment.CENTER);
        xssfCellStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        worksheet.cell(ADDR_TEST_ID).setCellStyle(xssfCellStyle);
        worksheet.cell(ADDR_TEST_ID).setCellValue(tmsIdRef);
    }
}
