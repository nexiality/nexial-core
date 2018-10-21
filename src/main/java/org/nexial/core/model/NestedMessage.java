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

import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.nexial.core.excel.Excel.Worksheet;
import org.nexial.core.excel.ExcelConfig.*;
import org.nexial.core.utils.MessageUtils;

import static org.nexial.core.excel.ExcelConfig.*;
import static org.nexial.core.excel.ExcelConfig.StyleConfig.*;

/**
 * at times it is useful, and maybe even critical, to display multiple lines of information post execution of a
 * test step.  This class is used to capture the message and the presentation of such message, which would usually
 * render one line below the associated test step.
 */
public class NestedMessage {
    private String message;
    private boolean isPass;
    private String resultMessage;
    private XSSFFont font;
    private XSSFCellStyle style;
    private XSSFCellStyle resultStyle;

    public NestedMessage(Worksheet worksheet, String message) {
        style = worksheet.getStyle(STYLE_MESSAGE);
        font = worksheet.createFont();
        resultStyle = StyleDecorator.generate(worksheet, RESULT);

        this.message = StringUtils.defaultString(message, "");
        processMessage();
    }

    public String getMessage() { return message; }

    public void setMessage(String message) { this.message = message; }

    public boolean isPass() { return isPass; }

    public String getResultMessage() { return resultMessage; }

    public CellStyle getStyle() { return style; }

    public void markAsSkipped() { style = StyleDecorator.decorate(style, font, SKIPPED); }

    public void markAsFailure() { style = StyleDecorator.decorate(style, font, FAILED); }

    public void markAsDeprecated() { style = StyleDecorator.decorate(style, font, DEPRECATED); }

    public void markAsSuccess() { style = StyleDecorator.decorate(style, font, SUCCESS); }

    public void printTo(XSSFRow row) {
        XSSFCell cell = row.createCell(COL_IDX_MERGE_RESULT_START);
        cell.setCellValue(message);
        cell.setCellStyle(style);

        if (StringUtils.equals(resultMessage, MSG_DEPRECATED)) {
            row.setHeight(CELL_HEIGHT_DEPRECATED);
        } else {
            short actualHeight = row.getHeight();
            if (actualHeight < CELL_HEIGHT_DEFAULT) { row.setHeight(CELL_HEIGHT_DEFAULT); }

            if (StringUtils.isNotBlank(resultMessage)) {
                cell = row.createCell(COL_IDX_RESULT);
                cell.setCellValue(getResultMessage());
                cell.setCellStyle(resultStyle);
            }
        }
    }

    private void processMessage() {
        if (MessageUtils.isSkipped(message)) {
            isPass = false;
            resultMessage = MSG_SKIPPED;
            return;
        }

        if (MessageUtils.isTestResult(message)) {
            if (MessageUtils.isPass(message)) {
                isPass = true;
                resultMessage = MSG_PASS;
                return;
            }

            if (MessageUtils.isFail(message)) {
                resultMessage = MSG_FAIL;
                return;
            }

            if (MessageUtils.isWarn(message)) {
                resultMessage = MSG_WARN;
                return;
            }
        }

        if (MessageUtils.isDeprecated(message)) {
            resultMessage = MSG_DEPRECATED;
            markAsDeprecated();
            return;
        }
    }
}
