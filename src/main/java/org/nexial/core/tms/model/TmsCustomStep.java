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
