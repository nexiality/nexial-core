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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.collections4.map.HashedMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.nexial.core.plugins.filevalidation.FieldBean;
import org.nexial.core.plugins.filevalidation.RecordBean;
import org.nexial.core.plugins.filevalidation.RecordData;
import org.nexial.core.plugins.filevalidation.config.FieldConfig;
import org.nexial.core.plugins.filevalidation.config.MapFunctionConfig;
import org.nexial.core.plugins.filevalidation.config.RecordConfig;
import org.nexial.core.plugins.filevalidation.config.ValidationConfig;
import org.nexial.core.plugins.filevalidation.validators.Error.ErrorBuilder;

public class ValidationsExecutor {

    private static final Map<String, DataType> ALL_DATA_TYPES = new HashedMap<>();
    private FieldValidator startValidator;

    public enum ValidationType {
        REGEX, EQUALS, SQL, API, DB, IN, DATE
    }

    public enum Severity {
        ERROR, WARNING
    }

    public enum DataType {
        // numeric, alphanumeric, alpha, any, blank, t/f
        NUMERIC("N", "Numeric", "Num", "Number"),
        ALPHANUMERIC("A/N", "Alphanumeric", "Alpha Numeric"),
        BLANK("Blank"),
        ANY("*", "any", " ");

        private Map<String, DataType> references = new HashMap<>();

        DataType(String... text) {
            Arrays.stream(text).forEach(key -> {
                ALL_DATA_TYPES.put(StringUtils.lowerCase(key), this);
                references.put(StringUtils.lowerCase(key), this);
            });
        }

        // before search for key, make sure you trim and lowercase
        public DataType toEnum(String text) {
            return ALL_DATA_TYPES.get(text);
        }

        public boolean isValidDataType(String text) { return ALL_DATA_TYPES.containsKey(text); }

        public boolean isNumeric(String text) {
            return NUMERIC.references.keySet().stream().anyMatch(text::equalsIgnoreCase);
        }

        public boolean isAlphaNumeric(String text) {
            return ALPHANUMERIC.references.keySet().stream().anyMatch(text::equalsIgnoreCase);
        }

        public boolean isBlank(String text) {
            return BLANK.references.keySet().stream().anyMatch(text::equalsIgnoreCase);
        }

        public boolean isAny(String text) {
            return ANY.references.keySet().stream().anyMatch(text::equalsIgnoreCase);
        }
    }

    public enum Alignment {
        L("L"),
        LEFT("Left"),
        R("R"),
        RIGHT("Right");
        private String alignmentType;

        Alignment(String alignmentType) {
            this.alignmentType = alignmentType;
        }

        public String toString() {
            return alignmentType;
        }
    }

    public ValidationsExecutor() {

        startValidator = new RegexValidator();
        startValidator.setNextValidator(new EqualsValidator()).setNextValidator(new InListValidator()).setNextValidator(
            new DateValidator()).setNextValidator(new SqlValidator());
    }

    public void executeValidations(RecordData recordData) {

        doFieldValidations(recordData);
        recordData.printMapFunctionValues();
        collectErrors(recordData);
    }

    public void doBasicValidations(RecordBean recordBean) {
        BasicValidator basicValidator = new BasicValidator();

        List<FieldBean> fields = recordBean.getFields();

        for (FieldBean field : fields) {

            basicValidator.validateField(field);
        }

    }

    public Map<String, Object> collectMapValues(RecordConfig recordConfig, RecordBean recordBean,
                                                Map<String, Object> mapValues) {

        List<MapFunctionConfig> mapFunctionConfigs = recordConfig.getMapFunctionConfigs();

        if (mapFunctionConfigs == null || mapFunctionConfigs.isEmpty()) { return mapValues; }
        for (MapFunctionConfig mapFunctionConfig : mapFunctionConfigs) {
            List<FieldBean> recordFields = recordBean.getFields();
            boolean skipFunction = false;
            for (FieldBean recordField : recordFields) {

                if (mapFunctionConfig.getFieldName().equals(recordField.getConfig().getFieldname())) {

                    FieldBean signField = recordBean.get(mapFunctionConfig.getSignField());
                    String mapTo = mapFunctionConfig.getMapTo();

                    if (recordField.isDataTypeError()) {
                        mapValues.put(mapTo, "Skipped due to validation error.");
                        skipFunction = true;
                        break;
                    }

                    // todo: instantiate with class

                    if (mapFunctionConfig.getFunction().equals("AVERAGE")) {

                        double value;
                        if (signField != null) {
                            value = NumberUtils.createDouble(signField.getFieldValue() +
                                                             recordField.getFieldValue());
                        } else { value = NumberUtils.createDouble(recordField.getFieldValue()); }
                        if (mapValues.containsKey(mapTo)) {
                            int counter = (Integer) mapValues.get(mapTo + "#Counter");
                            counter = ++counter;
                            double sum = value + (Double) mapValues.get(mapTo + "#Sum");
                            mapValues.put(mapTo, sum / counter);
                            mapValues.put(mapTo + "#Counter", counter);
                            mapValues.put(mapTo + "#Sum", sum);

                        } else {
                            mapValues.put(mapTo, value);
                            mapValues.put(mapTo + "#Counter", 1);
                            mapValues.put(mapTo + "#Sum", value);
                        }
                    }

                    if (mapFunctionConfig.getFunction().equals("AGGREGATE")) {
                        BigDecimal big;
                        if (signField != null) {
                            big = NumberUtils.createBigDecimal((signField.getFieldValue() +
                                                                recordField.getFieldValue()).trim());

                        } else {
                            big = NumberUtils.createBigDecimal((recordField.getFieldValue()).trim());
                        }

                        if (mapValues.containsKey(mapTo)) {
                            big = big.add((BigDecimal) mapValues.get(mapTo));
                            mapValues.put(mapTo, big);
                        } else { mapValues.put(mapTo, big); }

                    }
                    if (mapFunctionConfig.getFunction().equals("MIN")) {
                        double value;
                        if (signField != null) {
                            value = NumberUtils.createDouble(signField.getFieldValue() +
                                                             recordField.getFieldValue());
                        } else { value = NumberUtils.createDouble(recordField.getFieldValue()); }

                        if (mapValues.containsKey(mapTo)) {

                            if (value < (Double) mapValues.get(mapTo)) {
                                mapValues.put(mapTo, value);
                            }
                        } else {

                            mapValues.put(mapTo, value);
                        }
                    }
                    if (mapFunctionConfig.getFunction().equals("MAX")) {
                        double value;
                        if (signField != null) {
                            value = NumberUtils.createDouble(signField.getFieldValue() +
                                                             recordField.getFieldValue());
                        } else { value = NumberUtils.createDouble(recordField.getFieldValue()); }

                        if (mapValues.containsKey(mapTo)) {

                            if (value > (Double) mapValues.get(mapTo)) {
                                mapValues.put(mapTo, value);
                            }
                        } else { mapValues.put(mapTo, value); }
                    }

                    if (mapFunctionConfig.getFunction().equals("COUNT")) {

                        if (mapValues.containsKey(mapTo)) {
                            int counter = (Integer) mapValues.get(mapTo);
                            mapValues.put(mapTo, ++counter);
                        } else { mapValues.put(mapTo, 1); }
                    }
                }
            }
            if (skipFunction) { break; }
        }

        return mapValues;
    }

    public static Error buildError(FieldBean field, Severity severity, String errorMessage, String validationType) {
        FieldConfig config = field.getConfig();

        Error error = new ErrorBuilder().fieldName(config.getFieldname())
                                        .severity(severity.toString())
                                        .validationType(validationType)
                                        .errorMessage(errorMessage)
                                        .build();
        return error;
    }

    private void collectErrors(RecordData recordData) {

        List<Error> errors = new ArrayList<>();

        for (Entry<Integer, RecordBean> recordEntry : recordData.getRecords().entrySet()) {

            List<FieldBean> recordFields = recordEntry.getValue().getFields();

            for (FieldBean recordField : recordFields) {

                if (recordField.getErrors() != null && recordField.getErrors().size() >= 1) {
                    for (Error error : recordField.getErrors()) {

                        error.setRecordLine(String.valueOf(recordEntry.getKey() + 1));
                        if (error.getSeverity().equals(Severity.ERROR.toString())) {
                            recordData.setHasError(true);
                        }

                        errors.add(error);
                    }
                }
            }
        }

        recordData.setErrors(errors);
    }

    private void doFieldValidations(RecordData recordData) {
        Map<Integer, RecordBean> records = recordData.getRecords();
        for (Entry<Integer, RecordBean> recordEntry : records.entrySet()) {
            List<FieldBean> fields = (recordEntry.getValue()).getFields();

            for (FieldBean field : fields) {
                List<ValidationConfig> validationConfigs = field.getConfig().getValidationConfigs();
                if (validationConfigs != null || !validationConfigs.isEmpty()) {
                    startValidator.validateField(field);
                }
            }

        }
    }


}
