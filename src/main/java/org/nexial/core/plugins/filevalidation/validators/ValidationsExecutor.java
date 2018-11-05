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

package org.nexial.core.plugins.filevalidation.validators;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.map.HashedMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.nexial.commons.utils.FileUtil;
import org.nexial.core.ExecutionThread;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.NexialFilterList;
import org.nexial.core.plugins.filevalidation.FieldBean;
import org.nexial.core.plugins.filevalidation.RecordBean;
import org.nexial.core.plugins.filevalidation.RecordData;
import org.nexial.core.plugins.filevalidation.config.FieldConfig;
import org.nexial.core.plugins.filevalidation.config.MapFunctionConfig;
import org.nexial.core.plugins.filevalidation.config.RecordConfig;
import org.nexial.core.plugins.filevalidation.validators.Error.ErrorBuilder;
import org.nexial.core.utils.ConsoleUtils;

import static java.math.RoundingMode.UP;

public class ValidationsExecutor {

    private static final Map<String, DataType> ALL_DATA_TYPES = new HashedMap<>();
    private static Map<String, Alignment> ALL_ALIGNMENTS = new HashMap<>();
    private static final int DEC_SCALE = 25;
    private static final RoundingMode ROUND = UP;
    private FieldValidator startValidator;
    private ExecutionContext context;

    public enum ValidationType {
        REGEX, EQUALS, SQL, API, DB, IN, DATE
    }

    public enum Severity {
        ERROR, WARNING
    }

    public enum DataType {
        // numeric, alphanumeric, alpha, alphaupper, alphalower, any, blank, t/f
        REGEX,
        PERSONNAME("Person Name", "First Name", "Last Name"),
        NUMERIC("N", "Numeric", "Num", "Number"),
        ALPHANUMERIC("A/N", "AN", "Alphanumeric", "Alpha Numeric", "Alphanum", "Alpha Num"),
        ALPHA("Alpha", "A"),
        ALPHAUPPER("Alpha Upper", "Alphaupper"),
        ALPHALOWER("Alpha Lower", "Alphalower"),
        BLANK("Blank"),
        ANY("*", "any", " ", "");

        DataType(String... text) {
            Arrays.stream(text).forEach(key -> ALL_DATA_TYPES.put(StringUtils.lowerCase(key), this));
        }

        // before search for key, make sure you trim and lowercase
        public static DataType toEnum(String text) {
            return ALL_DATA_TYPES.get(text.toLowerCase());
        }

        public boolean isValidDataType(String text) { return ALL_DATA_TYPES.containsKey(text); }

    }

    public enum Alignment {

        LEFT("Left", "L"),
        RIGHT("Right", "R");

        Alignment(String... alignmentType) {
            Arrays.stream(alignmentType).forEach(key -> ALL_ALIGNMENTS.put(StringUtils.lowerCase(key), this));
        }

        public static Alignment toEnum(String alignmentType) {

            return ALL_ALIGNMENTS.get(alignmentType.toLowerCase());
        }
    }

    public ValidationsExecutor() {
        context = ExecutionThread.get();
        startValidator = new RegexValidator();
        startValidator.setNextValidator(new EqualsValidator()).setNextValidator(new InListValidator()).setNextValidator(
            new DateValidator()).setNextValidator(new SqlValidator());
    }

    public void max(Map<String, Number> mapValues, String mapTo, BigDecimal big) {
        BigDecimal mapValue = mapValues.containsKey(mapTo) ?
                              big.max((BigDecimal) mapValues.get(mapTo)) :
                              big;
        mapValues.put(mapTo, mapValue);
    }

    public void min(Map<String, Number> mapValues, String mapTo, BigDecimal big) {
        BigDecimal mapValue = mapValues.containsKey(mapTo) ?
                              big.min((BigDecimal) mapValues.get(mapTo)) :
                              big;
        mapValues.put(mapTo, mapValue);
    }

    public static Error buildError(FieldBean field, Severity severity, String errorMessage, String validationType) {
        FieldConfig config = field.getConfig();

        return new ErrorBuilder().fieldName(config.getFieldname())
                                 .severity(severity.toString())
                                 .validationType(validationType)
                                 .errorMessage(errorMessage)
                                 .build();
    }

    File resolveCsvOutputFile() {
        String file = context.generateTestStepOutput("csv");
        if (FileUtil.isFileReadable(file)) { throw new IllegalArgumentException("Unable to create a csv output file.");}
        return new File(file);
    }

    // todo: make all number functions as generic methods

    void executeValidations(OutputStream outputStream, RecordBean recordBean) {

        RecordData recordData = recordBean.getRecordData();
        int totalFailed = recordData.getTotalRecordsFailed();
        doFieldValidations(recordBean);
        if (recordBean.isFailed()) {
            recordData.setTotalRecordsFailed(++totalFailed);
        }
        writeReportToFile(outputStream, recordBean);

        // TODO: refactor field validations to take the advantage of Nexial filter
    }

    void doBasicValidations(RecordBean recordBean) {
        BasicValidator basicValidator = new BasicValidator();
        List<FieldBean> fields = recordBean.getFields();
        for (FieldBean field : fields) {
            basicValidator.validateField(field);
        }
    }

    Map<String, Number> collectMapValues(RecordConfig recordConfig, RecordBean recordBean,
                                         Map<String, Number> mapValues) {

        List<MapFunctionConfig> mapFunctionConfigs = recordConfig.getMapFunctionConfigs();
        if (mapFunctionConfigs == null || mapFunctionConfigs.isEmpty()) { return mapValues; }
        updateValuesToContext(recordConfig, recordBean, mapValues);
        for (MapFunctionConfig mapFunctionConfig : mapFunctionConfigs) {
            List<FieldBean> recordFields = recordBean.getFields();
            String function = mapFunctionConfig.getFunction();
            FieldBean signField = recordBean.get(mapFunctionConfig.getSignField());
            String mapTo = mapFunctionConfig.getMapTo();

            //initialize count as 0
            if (!mapValues.containsKey(mapTo) && StringUtils.equalsIgnoreCase(function, "COUNT")) {
                mapValues.put(mapTo, 0);
            }
            for (FieldBean recordField : recordFields) {
                String fieldName = recordField.getConfig().getFieldname();
                if (!mapFunctionConfig.getFieldName().equals(fieldName)) {continue;}
                if (recordField.isDataTypeError()) {
                    context.logCurrentStep("skipped map function '" + function +
                                           "' due to validation error at field '" +
                                           fieldName + "'");
                    break;
                }
                BigDecimal big = null;
                if (!StringUtils.equalsAnyIgnoreCase(function, "COUNT", "SAVE")) {
                    String fieldValue = recordField.getFieldValue().trim();
                    fieldValue = (signField != null) ? signField.getFieldValue().trim() + fieldValue : fieldValue;
                    big = NumberUtils.createBigDecimal((fieldValue));
                }

                String condition = mapFunctionConfig.getCondition();
                if (condition != null && !isMatch(condition)) {
                    context.logCurrentStep("skipped map function for record number '" +
                                           recordBean.getRecordNumber() +
                                           "' due to failed condition '" +
                                           condition + "'");
                    continue;
                }
                switch (function.toUpperCase()) {
                    /*case "SAVE": {
                        if (mapValues.containsKey("FILE#" + mapTo)) {
                            int counter = mapValues.get("FILE#" + mapTo).intValue();
                            mapValues.put("FILE#" + mapTo, ++counter);
                        } else { mapValues.put("FILE#" + mapTo, 1);}
                        break;

                    }*/
                    case "AVERAGE": {
                        average(mapValues, mapTo, big);
                        break;
                    }
                    case "AGGREGATE": {
                        aggregate(mapValues, mapTo, big);
                        break;
                    }
                    case "MIN": {
                        min(mapValues, mapTo, big);
                        break;
                    }
                    case "MAX": {
                        max(mapValues, mapTo, big);
                        break;
                    }
                    case "COUNT": {
                        if (mapValues.containsKey(mapTo)) {
                            int counter = mapValues.get(mapTo).intValue();
                            mapValues.put(mapTo, ++counter);
                        } else {
                            mapValues.put(mapTo, 1);
                            break;
                        }
                    }
                }
            }
        }

        cleanValuesFromContext(recordBean);
        return mapValues;
    }

    void restoreValuesToContext(Map<String, Object> tempDupValues) {
        if (tempDupValues.isEmpty()) { return; }
        for (Entry<String, Object> stringObjectEntry : tempDupValues.entrySet()) {
            context.setData(stringObjectEntry.getKey(), stringObjectEntry.getValue());
            context.logCurrentStep("var '" +
                                   stringObjectEntry.getKey() +
                                   "' is restored to context with value '" +
                                   context.getObjectData(stringObjectEntry.getKey()) + "'");
        }
    }

    Map<String, Object> moveDupValuesFromContext(List<RecordConfig> configs) {
        Map<String, Object> tempDupValues = new HashMap<>();

        for (RecordConfig config : configs) {
            if (config != null && CollectionUtils.isNotEmpty(config.getMapFunctionConfigs())) {
                for (MapFunctionConfig mapFunctionConfig : config.getMapFunctionConfigs()) {
                    String mapTo = mapFunctionConfig.getMapTo();
                    String fieldName = mapFunctionConfig.getFieldName();
                    if (mapTo != null) {
                        if (context.hasData(mapTo)) { tempDupValues.put(mapTo, context.getObjectData(mapTo)); }
                        if (context.hasData(fieldName)) {
                            tempDupValues.put(fieldName,
                                              context.getObjectData(fieldName));
                        }
                    }
                }
            }
        }
        return tempDupValues;
    }

    void writeReportToFile(OutputStream outputStream, RecordBean recordBean) {
        try {
            String msg = null;
            if (recordBean.isFailed()) {
                msg = ErrorReport.createCSV(recordBean.getErrors());
            } else if (recordBean.isSkipped()) {
                msg = recordBean.getSkippedMsg();
            }
            if (outputStream != null && msg != null) {
                outputStream.write(msg.getBytes());
            }
        } catch (IOException e) {
            ConsoleUtils.log("Failed to write errors to csv file: " + e.getMessage());
        }
    }

    private void aggregate(Map<String, Number> mapValues, String mapTo, BigDecimal big) {
        BigDecimal mapValue = mapValues.containsKey(mapTo) ?
                              big.add((BigDecimal) mapValues.get(mapTo)) :
                              big;
        mapValues.put(mapTo, mapValue);
    }

    private void average(Map<String, Number> mapValues, String mapTo, BigDecimal big) {

        if (mapValues.containsKey(mapTo)) {
            BigDecimal counter = new BigDecimal(mapValues.get(mapTo + "#Counter").intValue() + 1);
            BigDecimal sum = big.add((BigDecimal) mapValues.get(mapTo + "#Sum"));
            mapValues.put(mapTo + "#Counter", counter);
            mapValues.put(mapTo + "#Sum", sum);
            mapValues.put(mapTo, sum.divide(counter, DEC_SCALE, ROUND));

        } else {
            mapValues.put(mapTo + "#Counter", 1);
            mapValues.put(mapTo + "#Sum", big);
            mapValues.put(mapTo, big);
        }
    }

    private void updateValuesToContext(RecordConfig recordConfig,
                                       RecordBean recordBean,
                                       Map<String, Number> mapValues) {
        List<MapFunctionConfig> mapFunctionConfigs = recordConfig.getMapFunctionConfigs();

        if (mapFunctionConfigs == null || mapFunctionConfigs.isEmpty()) { return; }

        for (MapFunctionConfig mapFunctionConfig : mapFunctionConfigs) {

            String mapTo = mapFunctionConfig.getMapTo();
            if (mapTo != null) {
                Number mapValue = (mapValues.get(mapTo) == null) ? 0 : mapValues.get(mapTo);
                context.setData(mapTo, mapValue);
            }
        }
        List<FieldBean> recordFields = recordBean.getFields();

        for (FieldBean recordField : recordFields) {
            String fName = recordField.getConfig().getFieldname();
            String fieldValue = recordField.getFieldValue();

            if (fieldValue == null) {
                context.removeData(fName);
            } else {
                if (!(DataType.toEnum(recordField.getConfig().getDatatype()) == DataType.NUMERIC)) {
                    context.setData(fName, truncateLeadingZeroes(fieldValue));
                } else {
                    context.setData(fName, fieldValue);
                }

            }
        }


    }

    private void cleanValuesFromContext(RecordBean recordBean) {
        for (FieldBean fieldBean : recordBean.getFields()) {
            context.removeData(fieldBean.getConfig().getFieldname());
        }
    }

    private String truncateLeadingZeroes(String text) {
        // in case start with - or +
        boolean isNegative = StringUtils.startsWith(text, "-");
        text = StringUtils.removeStart(text, "+");
        text = StringUtils.removeStart(text, "-");

        text = StringUtils.removeFirst(text, "^0{1,}");
        if (StringUtils.isBlank(text)) {
            return null;
        }

        if (StringUtils.startsWithIgnoreCase(text, ".")) { text = "0" + text; }
        if (isNegative) { text = "-" + text; }
        return text;
    }

    private boolean isMatch(String condition) {
        NexialFilterList filters = new NexialFilterList(condition);
        return filters.isMatched(context, "filtering records with");
    }

    private void doFieldValidations(RecordBean recordBean) {
        List<FieldBean> fields = recordBean.getFields();

        for (FieldBean field : fields) {
            if (CollectionUtils.isNotEmpty(field.getConfig().getValidationConfigs())) {
                startValidator.validateField(field);
            }
        }
        recordBean.collectErrors();
    }
}
