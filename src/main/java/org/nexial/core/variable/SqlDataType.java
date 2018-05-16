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

package org.nexial.core.variable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.core.plugins.db.JdbcOutcome;
import org.nexial.core.plugins.db.JdbcResult;
import org.nexial.core.plugins.db.SqlComponent;

public class SqlDataType extends ExpressionDataType<JdbcResult> {
    private SqlTransformer transformer = new SqlTransformer();
    private String db;
    private List<SqlComponent> sqlStatements;
    private JdbcOutcome executionOutcome;
    private Map<String, JdbcResult> executionOutcomeMapping = new HashMap<>();

    private SqlDataType() {}

    public SqlDataType(String textValue) throws TypeConversionException { super(textValue); }

    @Override
    public String getName() { return "SQL"; }

    public String getDb() { return db; }

    public List<SqlComponent> getSqlStatements() { return sqlStatements; }

    public JdbcOutcome getExecutionOutcome() { return executionOutcome; }

    protected void setExecutionOutcome(JdbcOutcome executionOutcome) {
        this.executionOutcome = executionOutcome;
        executionOutcomeMapping = new HashMap<>();
        if (MapUtils.isNotEmpty(executionOutcome.getNamedOutcome())) {
            executionOutcome.getNamedOutcome().forEach(executionOutcomeMapping::put);
        }
        if (CollectionUtils.isNotEmpty(executionOutcome)) {
            IntStream.range(0, executionOutcome.size())
                     .forEach(i -> executionOutcomeMapping.put(i + "", executionOutcome.get(i)));
        }
    }

    public JdbcResult getResult(String name) {
        return executionOutcomeMapping == null || !executionOutcomeMapping.containsKey(name) ?
               null : executionOutcomeMapping.get(name);
    }

    @Override
    Transformer getTransformer() { return transformer; }

    @Override
    ExpressionDataType<JdbcResult> snapshot() {
        SqlDataType snapshot = new SqlDataType();
        snapshot.db = db;
        snapshot.transformer = transformer;
        snapshot.textValue = textValue;
        snapshot.value = value;
        snapshot.sqlStatements = new ArrayList<>(sqlStatements);
        snapshot.executionOutcome = executionOutcome;
        snapshot.executionOutcomeMapping = executionOutcomeMapping;
        return snapshot;
    }

    @Override
    protected void init() {
        if (StringUtils.isBlank(textValue)) { throw new IllegalArgumentException("No SQL statements provided"); }

        sqlStatements = SqlComponent.toList(textValue);
        if (CollectionUtils.isEmpty(sqlStatements)) {
            throw new IllegalArgumentException("No SQL statements found via expression input");
        }
    }
}
