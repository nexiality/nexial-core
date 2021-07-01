package org.nexial.core.tms.model;

import java.util.List;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.nexial.core.excel.Excel;
import org.nexial.core.excel.Excel.Worksheet;

import static org.apache.commons.lang3.builder.ToStringStyle.SIMPLE_STYLE;
import static org.nexial.core.excel.ExcelConfig.COL_IDX_DESCRIPTION;

/**
 * Represents a single Nexial test step, to be imported into TMS as custom separated steps
 */
public class TmsCustomStep {
    protected Worksheet worksheet;
    protected List<XSSFCell> row;
    protected TmsTestStep testCase;
    protected String description;

    protected int rowIndex;
    // original row index (without tainting from macro expansion)
    protected int scriptRowIndex;

    public TmsCustomStep(Worksheet worksheet, List<XSSFCell> row, TmsTestStep testCase) {
        assert testCase != null;
        assert CollectionUtils.isNotEmpty(row);

        this.worksheet = worksheet;
        this.row       = row;
        this.testCase  = testCase;

        assert worksheet != null && worksheet.getFile() != null;
        rowIndex = row.get(0).getRowIndex();
        scriptRowIndex = rowIndex;
        readDescriptionCell(row);
    }

    public int getRowIndex()                          { return rowIndex; }

    public int getScriptRowIndex()                    { return scriptRowIndex; }

    public String getDescription()                    { return description; }

    @Override
    public String toString() {
        return new ToStringBuilder(this, SIMPLE_STYLE)
                       .append("description", description)
                       .toString();
    }

    /**
     * Read the description of the test step present in the provided row
     * @param row the row in which the test step is present
     */
    protected void readDescriptionCell(List<XSSFCell> row) {
        XSSFCell cell = row.get(COL_IDX_DESCRIPTION);
        description = Excel.getCellValue(cell);
    }
}
