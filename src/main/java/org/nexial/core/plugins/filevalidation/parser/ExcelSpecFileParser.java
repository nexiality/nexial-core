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

package org.nexial.core.plugins.filevalidation.parser;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.ss.util.CellUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.nexial.core.plugins.filevalidation.config.ExcelMappingConfig;
import org.nexial.core.plugins.filevalidation.config.ExcelMappingConfig.FilefooterrecordBean;
import org.nexial.core.plugins.filevalidation.config.ExcelMappingConfig.FileheaderrecordBean;
import org.nexial.core.plugins.filevalidation.config.ExcelMappingConfig.FilesectionsBean;
import org.nexial.core.plugins.filevalidation.config.ExcelMappingConfig.FilesectionsBean.BodyrecordsBean;
import org.nexial.core.plugins.filevalidation.config.ExcelMappingConfig.FilesectionsBean.FooterrecordBean;
import org.nexial.core.plugins.filevalidation.config.ExcelMappingConfig.FilesectionsBean.HeaderrecordBean;
import org.nexial.core.plugins.filevalidation.config.ExcelMappingConfig.MappingconfigBean;
import org.nexial.core.plugins.filevalidation.config.FieldConfig;
import org.nexial.core.plugins.filevalidation.config.MasterConfig;
import org.nexial.core.plugins.filevalidation.config.RecordConfig;
import org.nexial.core.plugins.filevalidation.config.RecordConfig.RecordConfigBuilder;
import org.nexial.core.plugins.filevalidation.config.SectionConfig;
import org.nexial.core.utils.CheckUtils;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import static org.apache.poi.ss.usermodel.CellType.BLANK;
import static org.nexial.core.NexialConst.GSON;
import static org.nexial.core.excel.ExcelConfig.ALPHABET_COUNT;

public class ExcelSpecFileParser extends RecordSpecFileParser {
    private DataFormatter dataFormatter = new DataFormatter();
    private String jsonConfig;
    private String mappingFilePath;
    private int previousFieldLength = 0;

    ExcelSpecFileParser(String jsonConfig, String mappingFilePath) {

        this.jsonConfig = jsonConfig;
        this.mappingFilePath = mappingFilePath;
    }

    public MasterConfig parseMappingFile() {
        CheckUtils.requiresReadableFile(mappingFilePath);
        try {
            JsonElement json = new JsonParser().parse(new FileReader(new File(jsonConfig)));

            ExcelMappingConfig excelMappingConfig = GSON.fromJson(json, ExcelMappingConfig.class);
            return parseExcelDescriptor(excelMappingConfig, mappingFilePath);
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to parse the given json config file " + jsonConfig);
        }
    }

    protected String getStringCellValue(Sheet sheet, String cellCol, int rowNum) {

        CellReference cellReference = new CellReference(cellCol + rowNum);
        Row row = sheet.getRow(cellReference.getRow());
        Cell cell = row.getCell(cellReference.getCol());
        return cell.getStringCellValue();
    }

    private MasterConfig parseExcelDescriptor(ExcelMappingConfig mappingConfig, String mappingFilePath) {
        try {
            // try use existing excel
            Workbook workbook = new XSSFWorkbook(new File(mappingFilePath));
            return mapMasterConfig(mappingConfig, workbook);
        } catch (IOException | InvalidFormatException e) {
            throw new IllegalStateException("Unable to read/ parse files:" + mappingFilePath);
        }

    }

    private static int fromColumnLettersToOrdinalNumber(String letters) {
        int columnNum = 0;
        for (char ch : letters.toCharArray()) { columnNum = columnNum * ALPHABET_COUNT + ch - 'A'; }
        return columnNum;
    }

    private MasterConfig mapMasterConfig(ExcelMappingConfig mappingConfig, Workbook workbook) {

        MasterConfig masterConfig = new MasterConfig();
        masterConfig.setFileHeader(parsefileHeaderFields(mappingConfig, workbook));
        masterConfig.setSectionConfigs(parseFilesections(mappingConfig, workbook));
        masterConfig.setFileFooter(parseFileFooterFields(mappingConfig, workbook));
        return masterConfig;
    }

    private RecordConfig parseFileFooterFields(ExcelMappingConfig mappingConfig, Workbook workbook) {
        List<FieldConfig> fileFooterConfigs = new ArrayList<>();
        FilefooterrecordBean fileFooter = mappingConfig.getFilefooterrecord();

        if (fileFooter == null || StringUtils.isBlank(fileFooter.getSheetname())) { return null; }
        String sheetName = fileFooter.getSheetname();
        boolean endsOnEmptyRow = fileFooter.isEndsonblankrow();
        Sheet fileFooterSheet = workbook.getSheet(sheetName);
        Map<String, Integer> startEndRowsFooter;
        if (!endsOnEmptyRow) {
            startEndRowsFooter = findStartEndRows(fileFooterSheet,
                                                  fileFooter.getPrecedeby().trim(),
                                                  fileFooter.getFollowby().trim(),
                                                  fileFooter.getCheckcolumn().trim());
        } else {
            startEndRowsFooter = findStartEndRows(fileFooterSheet,
                                                  fileFooter.getCheckcolumn().trim(),
                                                  fileFooter.getPrecedeby().trim()
                                                 );
        }
        int startRowNumber = startEndRowsFooter.get("startRowNumber");
        int endRowNumber = startEndRowsFooter.get("endRowNumber");
        FieldConfig fileFooterConfig;
        previousFieldLength = 0;
        for (int i = startRowNumber; i <= endRowNumber; i++) {
            fileFooterConfig = putFieldConfigValues(mappingConfig, fileFooterSheet, i);
            fileFooterConfigs.add(fileFooterConfig);
        }

        for (FieldConfig config : fileFooterConfigs) {
            parseValidationConfigs(config, mappingConfig.getFileheaderrecord().getValidations());
            mapFunctionsToField(config, mappingConfig.getFileheaderrecord().getMapfunctions());
        }

        return new RecordConfig.RecordConfigBuilder()
                   .fieldConfigList(fileFooterConfigs)
                   .mapFunctionConfigs(mapFunctionsToRecord(fileFooter.getMapfunctions()))
                   .fieldSeparator(fileFooter.getFieldseparator())
                   .recordIdField(fileFooter.getRecordidfield())
                   .recordId(fileFooter.getRecordid()).build();

    }

    private List<SectionConfig> parseFilesections(ExcelMappingConfig mappingConfig,
                                                  Workbook workbook) {

        List<SectionConfig> sectionConfigs = new ArrayList<>();
        List<FilesectionsBean> filesectionsBeanList = mappingConfig.getFilesections();
        if (filesectionsBeanList.isEmpty()) {
            throw new IllegalArgumentException("File section configs can't be empty.");
        }
        for (FilesectionsBean filesectionsBean : filesectionsBeanList) {
            SectionConfig sectionConfig = new SectionConfig();

            // add section header
            HeaderrecordBean headerrecordBean = filesectionsBean.getHeaderrecord();
            if (headerrecordBean != null && StringUtils.isNotBlank(headerrecordBean.getSheetname())) {
                Sheet headerSheet = workbook.getSheet(headerrecordBean.getSheetname());
                boolean endsOnEmptyRowHeader = headerrecordBean.isEndsonblankrow();
                Map<String, Integer> startEndRowsHeader;
                if (!endsOnEmptyRowHeader) {
                    startEndRowsHeader = findStartEndRows(headerSheet,
                                                          headerrecordBean.getPrecedeby(),
                                                          headerrecordBean.getFollowby(),
                                                          headerrecordBean.getCheckcolumn());
                } else {
                    startEndRowsHeader = findStartEndRows(headerSheet,
                                                          headerrecordBean.getCheckcolumn(),
                                                          headerrecordBean.getPrecedeby()
                                                         );
                }
                List<FieldConfig> headerConfigs = getConfigList(startEndRowsHeader, mappingConfig, headerSheet);
                RecordConfig headerRecordConfig = new RecordConfigBuilder()
                                                      .fieldConfigList(headerConfigs)
                                                      .mapFunctionConfigs(mapFunctionsToRecord(filesectionsBean
                                                                                                   .getHeaderrecord()
                                                                                                   .getMapfunctions()))
                                                      .fieldSeparator(headerrecordBean
                                                                          .getFieldseparator())
                                                      .recordIdField(headerrecordBean.getRecordidfield()).recordId(
                        headerrecordBean.getRecordid()).build();
                for (FieldConfig config : headerConfigs) {
                    parseValidationConfigs(config, filesectionsBean.getHeaderrecord().getValidations());
                    mapFunctionsToField(config, filesectionsBean.getHeaderrecord().getMapfunctions());
                }
                sectionConfig.setHeaderConfig(headerRecordConfig);
            }
            // add section body record(s)
            List<BodyrecordsBean> bodyrecordsBeansList = filesectionsBean.getBodyrecords();
            List<RecordConfig> bodyRecordConfigs = new ArrayList<>();
            for (BodyrecordsBean bodyrecordsBean : bodyrecordsBeansList) {
                if (bodyrecordsBean != null && StringUtils.isNotBlank(bodyrecordsBean.getSheetname())) {
                    String precedeBy = bodyrecordsBean.getPrecedeby();
                    boolean endsOnEmptyRowBody = bodyrecordsBean.isEndsonblankrow();
                    String followBy = bodyrecordsBean.getFollowby();
                    String checkColumn = bodyrecordsBean.getCheckcolumn().trim();
                    String sheetName = bodyrecordsBean.getSheetname();
                    Sheet sheet = workbook.getSheet(sheetName);
                    Map<String, Integer> startEndRows;
                    if (!endsOnEmptyRowBody) {
                        startEndRows = findStartEndRows(sheet, precedeBy, followBy, checkColumn);
                    } else { startEndRows = findStartEndRows(sheet, checkColumn, precedeBy); }

                    List<FieldConfig> bodyConfigs;
                    bodyConfigs = getConfigList(startEndRows, mappingConfig, sheet);
                    RecordConfig bodyRecordConfig = new RecordConfigBuilder()
                                                        .fieldConfigList(bodyConfigs).mapFunctionConfigs(
                            mapFunctionsToRecord(bodyrecordsBean.getMapfunctions())).fieldSeparator(bodyrecordsBean
                                                                                                        .getFieldseparator())
                                                        .recordIdField(bodyrecordsBean.getRecordidfield()).recordId(
                            bodyrecordsBean.getRecordid()).build();
                    for (FieldConfig config : bodyConfigs) {
                        parseValidationConfigs(config, bodyrecordsBean.getValidations());
                        mapFunctionsToField(config, bodyrecordsBean.getMapfunctions());
                    }
                    bodyRecordConfigs.add(bodyRecordConfig);
                }
            }
            sectionConfig.setBodyConfigs(bodyRecordConfigs);
            // add footer record
            FooterrecordBean footerrecordBean = filesectionsBean.getFooterrecord();
            if (footerrecordBean != null && StringUtils.isNotBlank(footerrecordBean.getSheetname())) {
                Sheet footerSheet = workbook.getSheet(footerrecordBean.getSheetname());
                boolean endsOnEmptyRowFooter = footerrecordBean.isEndsonblankrow();
                Map<String, Integer> startEndRowsFooter;
                if (!endsOnEmptyRowFooter) {
                    startEndRowsFooter = findStartEndRows(footerSheet,
                                                          footerrecordBean.getPrecedeby(),
                                                          footerrecordBean.getFollowby(),
                                                          footerrecordBean.getCheckcolumn());
                } else {
                    startEndRowsFooter = findStartEndRows(footerSheet,
                                                          footerrecordBean.getCheckcolumn(),
                                                          footerrecordBean.getPrecedeby()
                                                         );
                }
                List<FieldConfig> footerConfigs = getConfigList(startEndRowsFooter, mappingConfig, footerSheet);
                RecordConfig footerRecordConfig = new RecordConfigBuilder()
                                                      .fieldConfigList(footerConfigs).mapFunctionConfigs(
                        mapFunctionsToRecord(filesectionsBean.getFooterrecord()
                                                             .getMapfunctions())).fieldSeparator(footerrecordBean
                                                                                                     .getFieldseparator())
                                                      .recordIdField(footerrecordBean.getRecordidfield()).recordId(
                        footerrecordBean.getRecordid()).build();
                for (FieldConfig config : footerConfigs) {
                    parseValidationConfigs(config, filesectionsBean.getFooterrecord().getValidations());
                    mapFunctionsToField(config, filesectionsBean.getFooterrecord().getMapfunctions());
                }
                sectionConfig.setFooterConfig(footerRecordConfig);
            }
            sectionConfigs.add(sectionConfig);
        }
        return sectionConfigs;
    }

    private List<FieldConfig> getConfigList(Map<String, Integer> startEndRows,
                                            ExcelMappingConfig mappingConfig,
                                            Sheet sheet) {
        List<FieldConfig> configList = new ArrayList<>();
        int startRowNumber = startEndRows.get("startRowNumber");
        int endRowNumber = startEndRows.get("endRowNumber");
        previousFieldLength = 0;
        for (int k = startRowNumber; k <= endRowNumber; k++) {
            FieldConfig config = putFieldConfigValues(mappingConfig, sheet, k);
            configList.add(config);
        }
        return configList;
    }

    private RecordConfig parsefileHeaderFields(ExcelMappingConfig mappingConfig, Workbook workbook) {
        List<FieldConfig> fileHeaderConfigs = new ArrayList<>();
        FileheaderrecordBean fileHeader = mappingConfig.getFileheaderrecord();
        if (fileHeader == null || StringUtils.isBlank(fileHeader.getSheetname())) { return null; }
        String sheetName = StringUtils.trim(fileHeader.getSheetname());
        String precedeBy = fileHeader.getPrecedeby();
        boolean endsOnEmptyRow = fileHeader.isEndsonblankrow();
        String followBy = fileHeader.getFollowby().trim();
        String checkColumn = fileHeader.getCheckcolumn().trim();
        Sheet fileHeaderSheet = workbook.getSheet(sheetName);
        Map<String, Integer> startEndRowsFileHeader;

        if (!endsOnEmptyRow) {
            CheckUtils.requiresNotBlank(precedeBy, "Invalid 'precedeBy' key value ", precedeBy);
            CheckUtils.requiresNotBlank(followBy, "Invalid 'followBy' key value ", followBy);
            CheckUtils.requiresNotBlank(checkColumn, "Invalid 'checkColumn' key value ", checkColumn);
            startEndRowsFileHeader = findStartEndRows(fileHeaderSheet, precedeBy, followBy, checkColumn);
        } else {
            startEndRowsFileHeader = findStartEndRows(fileHeaderSheet,
                                                      fileHeader.getCheckcolumn(),
                                                      fileHeader.getPrecedeby()
                                                     );
        }

        int startRowNumber = startEndRowsFileHeader.get("startRowNumber");
        int endRowNumber = startEndRowsFileHeader.get("endRowNumber");
        previousFieldLength = 0;
        for (int i = startRowNumber; i <= endRowNumber; i++) {
            FieldConfig config = putFieldConfigValues(mappingConfig, fileHeaderSheet, i);
            fileHeaderConfigs.add(config);
            parseValidationConfigs(config, fileHeader.getValidations());
            mapFunctionsToField(config, fileHeader.getMapfunctions());
        }

        return new RecordConfig.RecordConfigBuilder()
                   .fieldConfigList(fileHeaderConfigs)
                   .mapFunctionConfigs(mapFunctionsToRecord(fileHeader.getMapfunctions()))
                   .fieldSeparator(fileHeader.getFieldseparator())
                   .recordIdField(fileHeader.getRecordidfield())
                   .recordId(fileHeader.getRecordid()).build();

    }

    private Map<String, Integer> findStartEndRows(Sheet sheet, String precedeBy, String followBy, String checkColumn) {
        Map<String, Integer> startEndRows = new HashMap<>();
        int startRowNumber = 0;
        int endRowNumber = 0;

        for (Row row : sheet) {
            Cell cell = CellUtil.getCell(row, fromColumnLettersToOrdinalNumber(checkColumn));

            if (StringUtils.containsIgnoreCase(dataFormatter.formatCellValue(cell), precedeBy)) {
                startRowNumber = cell.getRowIndex() + 2;
                continue;
            }

            if (StringUtils.containsIgnoreCase(dataFormatter.formatCellValue(cell), followBy)) {
                endRowNumber = cell.getRowIndex();
                break;
            }
        }

        startEndRows.put("startRowNumber", startRowNumber);
        startEndRows.put("endRowNumber", endRowNumber);
        return startEndRows;
    }

    private Map<String, Integer> findStartEndRows(Sheet sheet,
                                                  String checkColumn,
                                                  String precedeBy) {

        Map<String, Integer> startEndRows = new HashMap<>();
        int startRowNumber = 0;
        int endRowNumber = 0;
        for (Row row : sheet) {
            Cell cell = CellUtil.getCell(row, fromColumnLettersToOrdinalNumber(checkColumn));

            if (StringUtils.containsIgnoreCase(dataFormatter.formatCellValue(cell), precedeBy)) {

                startRowNumber = cell.getRowIndex() + 2;
                endRowNumber = findEmptyRow(sheet, startRowNumber);
                if (endRowNumber >= startRowNumber) { break; }
            }
        }

        startEndRows.put("startRowNumber", startRowNumber);
        startEndRows.put("endRowNumber", endRowNumber);
        return startEndRows;
    }

    private int findEmptyRow(Sheet sheet, int beginRow) {
        int endRow = sheet.getLastRowNum();
        for (int i = beginRow - 1; i < sheet.getLastRowNum(); i++) {
            if (checkIfRowIsEmpty(sheet.getRow(i))) { return i; }
        }
        return endRow + 1;
    }

    // todo: move this to Excel for reuse
    private boolean checkIfRowIsEmpty(Row row) {
        if (row == null) { return true; }
        if (row.getLastCellNum() <= 0) { return true; }
        for (int cellNum = row.getFirstCellNum(); cellNum < row.getLastCellNum(); cellNum++) {
            Cell cell = row.getCell(cellNum);
            if (cell != null && cell.getCellTypeEnum() != BLANK && StringUtils.isNotBlank(cell.toString())) {
                return false;
            }
        }
        return true;
    }

    private FieldConfig putFieldConfigValues(ExcelMappingConfig mappingConfig,
                                             Sheet sheet,
                                             int rowNumber) {

        FieldConfig fieldConfig = new FieldConfig();
        MappingconfigBean config = mappingConfig.getMappingconfig();

        String mapValueColumn = config.getMapvaluetocolumn();
        String filedName = getStringCellValue(sheet, mapValueColumn, rowNumber).trim();
        fieldConfig.setFieldname(filedName);

        String dataTypeColumn = config.getDatatypedefcolumn();
        String fieldDataType = getStringCellValue(sheet, dataTypeColumn, rowNumber).trim();
        fieldConfig.setDatatype(fieldDataType);

        String fixedLengthDefColumn = config.getFieldlengthdefcolumn();
        if (StringUtils.isNotBlank(fixedLengthDefColumn)) {
            fieldConfig.setFieldlength(getNumericCellValue(sheet, fixedLengthDefColumn, rowNumber));
        }
        String positionFromCol = config.getPositionfromdefcolumn();
        if (StringUtils.isNotBlank(positionFromCol)) {
            fieldConfig.setPositionfrom(getNumericCellValue(sheet, positionFromCol, rowNumber));
        }// compute this if not provided
        else if (StringUtils.isNotBlank(fixedLengthDefColumn)) {
            fieldConfig.setPositionfrom(previousFieldLength + 1);
        }
        String positionToCol = config.getPositiontodefcolumn();

        if (StringUtils.isNotBlank(positionToCol)) {
            fieldConfig.setPositionto(getNumericCellValue(sheet, positionToCol, rowNumber));
        } else if (StringUtils.isNotBlank(fixedLengthDefColumn)) {
            fieldConfig.setPositionto(previousFieldLength + fieldConfig.getFieldlength());
            previousFieldLength = previousFieldLength + fieldConfig.getFieldlength();
        }

        String alignmentCol = config.getAlignmentdefcolumn();
        if (StringUtils.isNotBlank(alignmentCol)) {
            fieldConfig.setAlignment(getStringCellValue(sheet, alignmentCol, rowNumber).trim());
        }
        return fieldConfig;
    }

    private int getNumericCellValue(Sheet sheet, String cellCol, int rowNum) {
        CellReference cellReference = new CellReference(cellCol + rowNum);
        Row row = sheet.getRow(cellReference.getRow());
        Cell cell = row.getCell(cellReference.getCol());
        return (int) cell.getNumericCellValue();
    }
}