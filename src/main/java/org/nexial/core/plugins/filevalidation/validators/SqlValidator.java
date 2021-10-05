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

package org.nexial.core.plugins.filevalidation.validators;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.core.ExecutionThread;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.plugins.NexialCommand;
import org.nexial.core.plugins.db.JdbcResult;
import org.nexial.core.plugins.db.RdbmsCommand;
import org.nexial.core.plugins.filevalidation.FieldBean;
import org.nexial.core.plugins.filevalidation.RecordBean;
import org.nexial.core.plugins.filevalidation.config.ValidationConfig;
import org.nexial.core.plugins.filevalidation.config.ValidationsBean.ValidationmethodsBean.ConditionBean;
import org.nexial.core.plugins.filevalidation.validators.ValidationsExecutor.ValidationType;
import org.nexial.core.utils.ConsoleUtils;

import com.google.gson.Gson;
import com.google.gson.JsonArray;

import static org.nexial.core.NexialConst.TOKEN_END;
import static org.nexial.core.NexialConst.TOKEN_START;
import static org.nexial.core.plugins.filevalidation.validators.ValidationsExecutor.Severity.ERROR;
import static org.nexial.core.plugins.filevalidation.validators.ValidationsExecutor.buildError;
import static org.nexial.core.utils.CheckUtils.requiresNotBlank;

public class SqlValidator implements FieldValidator {

    private static final int DB_PROFILE_INDEX = 0;
    private static final int SQL_QUERY_INDEX = 1;
    FieldValidator nextValidator;

    @Override
    public FieldValidator setNextValidator(FieldValidator nextValidator) {
        this.nextValidator = nextValidator;
        return this.nextValidator;
    }

    @Override
    public void validateField(FieldBean field) {
        List<ValidationConfig> validationConfigs = field.getConfig().getValidationConfigs();
        if (validationConfigs == null || validationConfigs.isEmpty()) { return; }

        ExecutionContext context = ExecutionThread.get();
        NexialCommand rdbms = context.findPlugin("rdbms");
        for (ValidationConfig validationConfig : validationConfigs) {

            if (!resolveConditions(field, validationConfig)) { break; }
            if (validationConfig.getType().equals(ValidationType.SQL.toString())) {
                JsonArray listValues = (JsonArray) validationConfig.getParams();
                List stringList = new Gson().fromJson(listValues, ArrayList.class);
                String actual = field.getFieldValue().trim();

                String dbProfile = String.valueOf(stringList.get(DB_PROFILE_INDEX));
                requiresNotBlank(dbProfile, "invalid db", dbProfile);

                String sql = String.valueOf(stringList.get(SQL_QUERY_INDEX));
                requiresNotBlank(sql, "invalid sql", sql);
                sql = context.handleExpression(substituteFieldValues(sql, lookUpFieldNames(sql), field.getRecord()));

                String resultVar = getClass().getName() + System.currentTimeMillis() + "dbresult";

                try {
                    ((RdbmsCommand) rdbms).runSQL(resultVar, dbProfile, sql);
                    JdbcResult result = ((JdbcResult) context.getObjectData(resultVar));
                    if (result.getRowCount() < 1) {
                        String msg =
                            " No matched row(s) found. Executed query in " + result.getElapsedTime() + " ms with " +
                            (result.hasError() ?
                             "ERROR " + result.getError() :
                             result.getRowCount() + " row(s)");
                        logErrorMessage(field, msg, actual);
                    }
                } catch (IOException e) {
                    ConsoleUtils.error(e.getMessage());
                    logErrorMessage(field, e.getMessage(), "ERROR");
                }
            }
        }

        // set if next validator available
        // nextValidator.validateField(field);
    }

    private boolean resolveConditions(FieldBean field, ValidationConfig validationConfig) {
        if (validationConfig.getConditionBeans() == null) { return true; }

        int trueCount = 0;
        for (ConditionBean conditionBean : validationConfig.getConditionBeans()) {
            String fieldName = conditionBean.getFieldname();
            String fieldValue = conditionBean.getFieldvalue();

            for (FieldBean fieldBean : field.getRecord().getFields()) {
                if (fieldBean.getConfig().getFieldname().equals(fieldName) &&
                    fieldBean.getFieldValue().trim().equals(fieldValue)) {
                    trueCount++;
                    break;
                }
            }
        }

        return trueCount == validationConfig.getConditionBeans().size();
    }

    private String[] lookUpFieldNames(String sql) { return StringUtils.substringsBetween(sql, TOKEN_START, TOKEN_END); }

    private String substituteFieldValues(String query, String[] fields, RecordBean recordBean) {
        if (ArrayUtils.isEmpty(fields)) { return query; }

        for (String fieldName : fields) {
            for (FieldBean fieldBean : recordBean.getFields()) {
                if (fieldBean.getConfig().getFieldname().equals(fieldName.trim())) {
                    // query = StringUtils.replaceEach(query, new String[]{TOKEN_START, TOKEN_END}, new String[]{"", ""});
                    query = StringUtils.replace(query, TOKEN_START + fieldName + TOKEN_END, fieldBean.getFieldValue());
                    break;
                }
            }
        }

        return query;
    }

    private void logErrorMessage(FieldBean field, String msg, String actual) {
        String errorMessage = ErrorMessage.sqlCheckError(field, msg, actual);
        field.getErrors().add(buildError(field, ERROR, errorMessage, ValidationType.SQL.toString()));
    }

}
