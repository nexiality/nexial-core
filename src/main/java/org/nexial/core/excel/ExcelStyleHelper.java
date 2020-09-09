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

package org.nexial.core.excel;

import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.validation.constraints.NotNull;

import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.usermodel.*;
import org.nexial.core.excel.Excel.Worksheet;
import org.nexial.core.model.TestStep;
import org.nexial.core.utils.MessageUtils;

import static org.apache.poi.ss.usermodel.CellType.BLANK;
import static org.apache.poi.ss.usermodel.FillPatternType.NO_FILL;
import static org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND;
import static org.apache.poi.xssf.usermodel.extensions.XSSFCellBorder.BorderSide.*;
import static org.nexial.core.excel.ExcelConfig.*;
import static org.nexial.core.excel.ExcelConfig.StyleConfig.FONT_NAME_FIXED_DEFAULT;
import static org.nexial.core.excel.ExcelStyleHelper.StyleName.*;

public final class ExcelStyleHelper {

    // color
    public static final XSSFColor WHITE = new XSSFColor(new Color(255, 255, 255));
    public static final XSSFColor YELLOW = new XSSFColor(new Color(255, 255, 0));
    public static final XSSFColor GREEN = new XSSFColor(new Color(0, 255, 0));
    public static final XSSFColor BLACK = new XSSFColor(new Color(1, 1, 1));
    public static final XSSFColor BLUE = new XSSFColor(new Color(0, 0, 255));
    public static final XSSFColor INDIGO = new XSSFColor(new Color(0, 255, 255));
    public static final XSSFColor ORANGE = new XSSFColor(new Color(255, 128, 0));
    public static final XSSFColor PURPLE = new XSSFColor(new Color(255, 0, 255));
    public static final XSSFColor RED = new XSSFColor(new Color(255, 0, 0));

    private static final int DEF_CHAR_WIDTH_FACTOR_TAHOMA = 290;
    private static final int DEF_CHAR_WIDTH_FACTOR_TAHOMA_BOLD = 337;
    private static final int DEF_CHAR_WIDTH_FACTOR_CONSOLAS = 273;

    enum StyleName {
        ALIGNMENT("alignment", HorizontalAlignment.class),
        VERTICAL_ALIGNMENT("verticalAlignment", VerticalAlignment.class),

        BORDER_TOP("borderTop", Set.class),
        BORDER_BOTTOM("borderBottom", Set.class),
        BORDER_LEFT("borderLeft", Set.class),
        BORDER_RIGHT("borderRight", Set.class),

        TOP_BORDER_COLOR("topBorderColor", XSSFColor.class),
        BOTTOM_BORDER_COLOR("bottomBorderColor", XSSFColor.class),
        LEFT_BORDER_COLOR("leftBorderColor", XSSFColor.class),
        RIGHT_BORDER_COLOR("rightBorderColor", XSSFColor.class),

        FILL_BACKGROUND_COLOR("fillBackgroundColor", XSSFColor.class),
        FILL_FOREGROUND_COLOR("fillForegroundColor", XSSFColor.class),
        FILL_PATTERN("fillPattern", FillPatternType.class),

        FONT("font", XSSFFont.class),
        DATA_FORMAT("dataFormat", Short.class),
        WRAP_TEXT("wrapText", Boolean.class),
        INDENTION("indention", Short.class),
        ROTATION("rotation", Short.class),
        HIDDEN("hidden", Boolean.class),
        LOCKED("locked", Boolean.class);

        private final String name;
        private final Class<?> type;

        StyleName(String name, Class<?> type) {
            this.name = name;
            this.type = type;
        }

        public String getName() { return name; }

        public Class<?> getType() { return type; }
    }

    private ExcelStyleHelper() { }

    /**
     * adopted (like, heavily) from org.apache.poi.ss.util.CellUtil
     */
    public static void extendCellStyle(XSSFCell cell, Map<StyleName, Object> newProps) {
        XSSFCellStyle old = cell.getCellStyle();
        XSSFCellStyle new1 = cell.getSheet().getWorkbook().createCellStyle();

        new1.setFillBackgroundColor(
            (XSSFColor) newProps.getOrDefault(FILL_BACKGROUND_COLOR, old.getFillBackgroundXSSFColor()));
        new1.setFillForegroundColor(
            (XSSFColor) newProps.getOrDefault(FILL_FOREGROUND_COLOR, old.getFillForegroundXSSFColor()));
        new1.setFillPattern((FillPatternType) newProps.getOrDefault(FILL_PATTERN, old.getFillPatternEnum()));

        new1.setIndention((Short) newProps.getOrDefault(INDENTION, old.getIndention()));

        new1.setTopBorderColor((XSSFColor) newProps.getOrDefault(TOP_BORDER_COLOR, old.getTopBorderXSSFColor()));
        new1.setBottomBorderColor(
            (XSSFColor) newProps.getOrDefault(BOTTOM_BORDER_COLOR, old.getBottomBorderXSSFColor()));
        new1.setLeftBorderColor((XSSFColor) newProps.getOrDefault(LEFT_BORDER_COLOR, old.getLeftBorderXSSFColor()));
        new1.setRightBorderColor((XSSFColor) newProps.getOrDefault(RIGHT_BORDER_COLOR, old.getRightBorderXSSFColor()));
        new1.setBorderTop((BorderStyle) newProps.getOrDefault(BORDER_TOP, old.getBorderTopEnum()));
        new1.setBorderBottom((BorderStyle) newProps.getOrDefault(BORDER_BOTTOM, old.getBorderBottomEnum()));
        new1.setBorderLeft((BorderStyle) newProps.getOrDefault(BORDER_LEFT, old.getBorderLeftEnum()));
        new1.setBorderRight((BorderStyle) newProps.getOrDefault(BORDER_RIGHT, old.getBorderRightEnum()));

        new1.setAlignment((HorizontalAlignment) newProps.getOrDefault(ALIGNMENT, old.getAlignmentEnum()));
        new1.setVerticalAlignment(
            (VerticalAlignment) newProps.getOrDefault(VERTICAL_ALIGNMENT, old.getVerticalAlignmentEnum()));
        new1.setWrapText((Boolean) newProps.getOrDefault(WRAP_TEXT, old.getWrapText()));

        new1.setFont((XSSFFont) newProps.getOrDefault(FONT, old.getFont()));

        cell.setCellStyle(new1);
    }

    public static void handleTextWrap(XSSFCell cell) {
        if (cell == null) { return; }

        String cellValue = Excel.getCellValue(cell);
        if (StringUtils.isBlank(cellValue) || !StringUtils.contains(cellValue, "\n")) { return; }

        Map<StyleName, Object> props = new HashMap<>();
        props.put(WRAP_TEXT, true);
        extendCellStyle(cell, props);
    }

    public static void formatActivityCell(Worksheet worksheet, XSSFCell cell) {
        if (StringUtils.isBlank(Excel.getCellValue(cell))) { return; }
        cell.setCellStyle(worksheet.getStyle(STYLE_TEST_CASE));
        fixCellWidth(worksheet.getSheet(), cell, COL_IDX_TESTCASE, DEF_CHAR_WIDTH_FACTOR_TAHOMA_BOLD);
    }

    public static void formatDescription(Worksheet worksheet, XSSFCell cell) {
        cell.setCellStyle(worksheet.getStyle(STYLE_DESCRIPTION));
        fixCellWidth(cell.getSheet(), cell, COL_IDX_DESCRIPTION, DEF_CHAR_WIDTH_FACTOR_TAHOMA);
    }

    public static void formatSectionDescription(Worksheet worksheet, XSSFCell cell) {
        cell.setCellStyle(worksheet.getStyle(STYLE_SECTION_DESCRIPTION));
        fixCellWidth(cell.getSheet(), cell, COL_IDX_DESCRIPTION, DEF_CHAR_WIDTH_FACTOR_TAHOMA);
    }

    public static void formatSectionDescription(TestStep testStep) {
        enhanceDescriptionFormat(formatDescriptionCell(testStep, STYLE_SECTION_DESCRIPTION));
    }

    public static void formatRepeatUntilDescription(Worksheet worksheet, XSSFCell cell) {
        cell.setCellStyle(worksheet.getStyle(STYLE_REPEAT_UNTIL_DESCRIPTION));
        fixCellWidth(cell.getSheet(), cell, COL_IDX_DESCRIPTION, DEF_CHAR_WIDTH_FACTOR_TAHOMA);
    }

    public static TestStep formatRepeatUntilDescription(TestStep testStep) {
        return enhanceDescriptionFormat(formatDescriptionCell(testStep, STYLE_REPEAT_UNTIL_DESCRIPTION));
    }

    public static TestStep formatFailedStepDescription(TestStep testStep) {
        return formatDescriptionCell(testStep, STYLE_FAILED_STEP_DESCRIPTION);
    }

    public static void formatTargetCell(Worksheet worksheet, XSSFCell cell) {
        cell.setCellStyle(worksheet.getStyle(STYLE_TARGET));
        fixCellWidth(worksheet.getSheet(), cell, COL_IDX_TARGET, DEF_CHAR_WIDTH_FACTOR_CONSOLAS);
    }

    public static void formatCommandCell(Worksheet worksheet, XSSFCell cell) {
        cell.setCellStyle(worksheet.getStyle(STYLE_COMMAND));
        fixCellWidth(worksheet.getSheet(), cell, COL_IDX_COMMAND, DEF_CHAR_WIDTH_FACTOR_CONSOLAS);
    }

    public static void formatParams(TestStep testStep) {
        Worksheet worksheet = testStep.getWorksheet();
        XSSFCellStyle style = worksheet.getStyle(STYLE_PARAM);
        List<XSSFCell> row = testStep.getRow();
        for (int i = COL_IDX_PARAMS_START; i < COL_IDX_PARAMS_END; i++) {
            XSSFCell cell = row.get(i);
            if (isStepSkipped(cell.getRow())) {
                cell.setCellStyle(worksheet.getStyle(STYLE_PARAM_SKIPPED));
            } else if (StringUtils.isNotBlank(Excel.getCellValue(cell))) {
                cell.setCellStyle(style);
            }
        }
    }

    public static void formatFlowControlCell(Worksheet worksheet, XSSFCell cell) {
        if (cell == null) { return; }
        if (isStepSkipped(cell.getRow())) {
            cell.setCellStyle(worksheet.getStyle(STYLE_PARAM_SKIPPED));
        } else {
            String cellValue = Excel.getCellValue(cell);
            if (StringUtils.isNotBlank(cellValue)) {
                cell.setCellStyle(worksheet.getStyle(STYLE_PARAM));
                fixCellWidth(worksheet.getSheet(), cell, COL_IDX_FLOW_CONTROLS, DEF_CHAR_WIDTH_FACTOR_CONSOLAS);
            } else {
                cell.setCellType(BLANK);
            }
        }
    }

    public static boolean isStepSkipped(XSSFRow stepRow) {
        if (stepRow == null) { return false; }
        XSSFCell resultCell = stepRow.getCell(COL_IDX_RESULT);
        return resultCell != null && MessageUtils.isSkipped(Excel.getCellValue(resultCell));
    }

    public static XSSFCellStyle generate(XSSFWorkbook workbook, StyleConfig config) {
        assert workbook != null;
        assert config != null;
        return decorate(workbook.createCellStyle(), workbook.createFont(), config);
    }

    public static XSSFCellStyle generate(Worksheet worksheet, StyleConfig config) {
        assert worksheet != null;
        assert config != null;
        return decorate(worksheet, config);
    }

    public static XSSFCellStyle decorate(Worksheet worksheet, StyleConfig config) {
        assert worksheet != null;
        assert config != null;
        return decorate(worksheet.newCellStyle(), worksheet.createFont(), config);
    }

    public static XSSFCellStyle decorate(XSSFCellStyle style, XSSFFont font, StyleConfig config) {
        assert style != null;
        assert font != null;
        assert config != null;

        XSSFColor backgroundColor = config.getBackgroundColor();
        if (backgroundColor != null) { style.setFillBackgroundColor(backgroundColor); }

        FillPatternType backgroundFillPattern = config.getBackgroundFillPattern();
        if (backgroundFillPattern == SOLID_FOREGROUND || backgroundFillPattern == NO_FILL) {
            if (backgroundColor != null) {
                style.setFillForegroundColor(backgroundColor);
                style.setFillPattern(SOLID_FOREGROUND);
            }
        } else {
            XSSFColor backgroundFillColor = config.getBackgroundFillColor();
            if (backgroundFillColor != null) {
                style.setFillForegroundColor(backgroundFillColor);
                style.setFillPattern(backgroundFillPattern);
            }
        }

        short indention = config.getIndention();
        if (indention > 0) { style.setIndention(indention); }

        XSSFColor borderColor = config.getBorderColor();
        if (borderColor != null) {
            if (config.isUseSpecialTopAndDoubleBottomBorder()) {
                style.setBorderTop(BorderStyle.THIN);
                style.setBorderBottom(BorderStyle.DOUBLE);
                style.setBorderLeft(BorderStyle.NONE);
                style.setBorderRight(BorderStyle.NONE);
                style.setBorderColor(TOP, borderColor);
                style.setBorderColor(BOTTOM, borderColor);
            } else {
                setCellBorder(style, config.getBorderStyle(), borderColor);
            }
        }

        HorizontalAlignment horizontalAlignment = config.getHorizontalAlignment();
        if (horizontalAlignment != null) { style.setAlignment(horizontalAlignment); }

        VerticalAlignment verticalAlignment = config.getVerticalAlignment();
        if (verticalAlignment != null) { style.setVerticalAlignment(verticalAlignment); }

        if (config.isWrapText()) { style.setWrapText(true); }

        String fontName = config.getFontName();
        if (StringUtils.isNotBlank(fontName)) {
            font.setFontName(fontName);
            font.setFontHeightInPoints(config.getFontHeight());
            font.setColor(config.getFontColor());
            font.setBold(config.isBoldFont());
            font.setItalic(config.isItalicFont());
            font.setUnderline(config.getUnderlineStyle());
            font.setStrikeout(config.isStrikeOut());
            style.setFont(font);
        }

        return style;
    }

    public static void setCellBorder(XSSFCellStyle style, BorderStyle borderStyle, XSSFColor borderColor) {
        style.setBorderTop(borderStyle);
        style.setBorderBottom(borderStyle);
        style.setBorderLeft(borderStyle);
        style.setBorderRight(borderStyle);
        style.setBorderColor(TOP, borderColor);
        style.setBorderColor(BOTTOM, borderColor);
        style.setBorderColor(LEFT, borderColor);
        style.setBorderColor(RIGHT, borderColor);
    }

    public static XSSFCellStyle newDefaultHeaderStyle(XSSFWorkbook workbook) {
        XSSFCellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(new XSSFColor(new Color(64, 64, 64)));
        style.setFillPattern(SOLID_FOREGROUND);
        style.setFont(newDefaultHeaderFont(workbook));
        return style;
    }

    public static XSSFFont newDefaultFont(XSSFWorkbook workbook) {
        XSSFFont font = workbook.createFont();
        font.setFontName(FONT_NAME_FIXED_DEFAULT);
        font.setFontHeight(9);
        return font;
    }

    public static XSSFFont newDefaultHeaderFont(XSSFWorkbook workbook) {
        XSSFFont font = newDefaultFont(workbook);
        font.setBold(true);
        font.setColor(new XSSFColor(new Color(217, 217, 217)));
        return font;
    }

    @NotNull
    private static TestStep enhanceDescriptionFormat(TestStep testStep) {
        String result = Excel.getCellValue(testStep.getRow().get(COL_IDX_RESULT));
        XSSFCellStyle cellStyle = testStep.getRow().get(COL_IDX_DESCRIPTION).getCellStyle();
        XSSFFont font = cellStyle.getFont();

        if (StringUtils.startsWith(result, MSG_SKIPPED)) {
            font.setItalic(true);
            font.setColor(new XSSFColor(new Color(100, 100, 100)));
            return testStep;
        }

        if (StringUtils.startsWith(result, MSG_FAIL)) { return formatFailedStepDescription(testStep); }

        return testStep;
    }

    // add prefix to description
    public static void formatDescriptionCell(XSSFCell cell, String prefix) {
        if (StringUtils.isNotBlank(prefix)) {
            String descriptionText = Excel.getCellValue(cell);
            cell.setCellValue(prefix + descriptionText);
        }
    }

    @NotNull
    private static TestStep formatDescriptionCell(TestStep testStep, String styleName) {
        XSSFCell cell = testStep.getRow().get(COL_IDX_DESCRIPTION);
        Worksheet worksheet = testStep.getWorksheet();
        cell.setCellStyle(worksheet.getStyle(styleName));
        fixCellWidth(cell.getSheet(), cell, COL_IDX_DESCRIPTION, DEF_CHAR_WIDTH_FACTOR_TAHOMA);
        return testStep;
    }

    public static XSSFCellStyle cloneCellStyle(XSSFCellStyle oldCellStyle, XSSFWorkbook workbook) {
        XSSFCellStyle newCellStyle = workbook.createCellStyle();
        newCellStyle.setIndention(oldCellStyle.getIndention());
        newCellStyle.setWrapText(oldCellStyle.getWrapText());
        newCellStyle.setAlignment(oldCellStyle.getAlignmentEnum());
        newCellStyle.setVerticalAlignment(oldCellStyle.getVerticalAlignmentEnum());
        newCellStyle.setDataFormat(workbook.getCreationHelper().createDataFormat()
                                           .getFormat(oldCellStyle.getDataFormatString()));
        setBackgroundColor(newCellStyle, oldCellStyle);
        XSSFFont font = createFont(workbook.createFont(), oldCellStyle.getFont());
        newCellStyle.setFont(font);

        return newCellStyle;
    }

    public static XSSFCellStyle copyCellBorder(XSSFCellStyle newCellStyle, XSSFCellStyle oldCellStyle) {
        BorderStyle borderTopEnum = oldCellStyle.getBorderTopEnum();
        if (borderTopEnum != BorderStyle.NONE) {
            newCellStyle.setBorderTop(borderTopEnum);
        }
        BorderStyle borderBottomEnum = oldCellStyle.getBorderBottomEnum();
        if (borderBottomEnum != BorderStyle.NONE) {
            newCellStyle.setBorderBottom(borderBottomEnum);
        }
        BorderStyle borderLeftEnum = oldCellStyle.getBorderLeftEnum();
        if (borderLeftEnum != BorderStyle.NONE) {
            newCellStyle.setBorderLeft(borderLeftEnum);
        }

        BorderStyle borderRightEnum = oldCellStyle.getBorderRightEnum();
        if (borderRightEnum != BorderStyle.NONE) {
            newCellStyle.setBorderRight(borderRightEnum);
        }

        newCellStyle.setBorderColor(TOP, oldCellStyle.getTopBorderXSSFColor());
        newCellStyle.setBorderColor(BOTTOM, oldCellStyle.getBottomBorderXSSFColor());
        newCellStyle.setBorderColor(LEFT, oldCellStyle.getLeftBorderXSSFColor());
        newCellStyle.setBorderColor(RIGHT, oldCellStyle.getRightBorderXSSFColor());
        return newCellStyle;
    }

    private static void setBackgroundColor(XSSFCellStyle newCellStyle, XSSFCellStyle oldCellStyle) {
        XSSFColor backgroundColor = oldCellStyle.getFillBackgroundXSSFColor();
        if (backgroundColor != null) { newCellStyle.setFillBackgroundColor(backgroundColor); }

        FillPatternType backgroundFillPattern = oldCellStyle.getFillPatternEnum();
        if (backgroundFillPattern == SOLID_FOREGROUND || backgroundFillPattern == NO_FILL) {
            if (backgroundColor != null) {
                newCellStyle.setFillForegroundColor(backgroundColor);
                newCellStyle.setFillPattern(SOLID_FOREGROUND);
            }
        }

        XSSFColor foregroundXSSFColor = oldCellStyle.getFillForegroundXSSFColor();
        if (foregroundXSSFColor != null) {
            newCellStyle.setFillForegroundColor(foregroundXSSFColor);
            newCellStyle.setFillPattern(backgroundFillPattern);
        }
    }

    private static XSSFFont createFont(XSSFFont newFont, XSSFFont oldFont) {
        String fontName = oldFont.getFontName();
        newFont.setFontName(fontName);
        newFont.setFontHeight(oldFont.getFontHeight());
        newFont.setBold(oldFont.getBold());
        newFont.setUnderline(oldFont.getUnderline());
        newFont.setColor(oldFont.getXSSFColor());
        newFont.setFamily(oldFont.getFamily());
        newFont.setItalic(oldFont.getItalic());
        newFont.setStrikeout(oldFont.getStrikeout());
        return newFont;
    }
}
