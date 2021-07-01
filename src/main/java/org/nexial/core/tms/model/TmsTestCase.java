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
