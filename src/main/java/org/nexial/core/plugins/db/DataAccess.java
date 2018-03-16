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

package org.nexial.core.plugins.db;

import java.io.File;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.model.ExecutionContext;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import com.Connx.jdbc.TCJdbc.TCJdbcConnectionPoolDataSource;

import static java.sql.Connection.TRANSACTION_SERIALIZABLE;
import static org.nexial.core.NexialConst.*;
import static org.nexial.core.utils.CheckUtils.requiresNotBlank;
import static org.nexial.core.utils.CheckUtils.requiresNotNull;

public class DataAccess implements ApplicationContextAware {
    // protected List<String> validSQLStartWords;
    protected Map<String, Class> dbTypes;
    protected ApplicationContext spring;
    protected ExecutionContext context;

    @Override
    public void setApplicationContext(ApplicationContext ctx) throws BeansException { spring = ctx; }

    // we want to support ALL types of SQL, including those vendor-specific
    // so for that reason, we are no longer insisting on the use of standard ANSI sql
    // public void setValidSQLStartWords(List<String> validSQLStartWords) { this.validSQLStartWords = validSQLStartWords; }

    public void setContext(ExecutionContext context) { this.context = context; }

    public void setDbTypes(Map<String, Class> dbTypes) { this.dbTypes = dbTypes; }

    public Class getDriverClass(String db) { return dbTypes.get(context.getStringData(db + OPT_DB_TYPE)); }

    public JdbcResult execute(String query, SimpleExtractionDao dao) { return execute(query, dao, null); }

    public JdbcResult execute(String query, SimpleExtractionDao dao, File saveTo) {
        requiresNotBlank(query, "Unknown/unsupported query", query);
        requiresNotNull(dao, "Invalid data access object");
        return dao.executeSql(query, saveTo);
    }

    protected static boolean isStartedWith(String query, List<String> startWords) {
        if (CollectionUtils.isEmpty(startWords)) { return false; }

        String query1 = StringUtils.upperCase(query);
        for (String startWord : startWords) { if (StringUtils.startsWith(query1, startWord)) { return true; } }

        return false;
    }

    protected boolean requires(boolean condition, String errorMessage, Object... params) {
        if (!condition) { throw new IllegalArgumentException(errorMessage + ": " + ArrayUtils.toString(params)); }
        return true;
    }

    // we want to support ALL types of SQL, including those vendor-specific
    // so for that reason, we are no longer insisting on the use of standard ANSI sql
    // protected boolean validSQL(String query) { return validSQLStartWords.contains(fetchStartKeyword(query)); }

    protected String fetchStartKeyword(String query) {
        return
            StringUtils.trim(
                StringUtils.upperCase(StringUtils.substringBefore(StringUtils.replaceAll(query, "[\r\n]", " "), " "))) +
            " ";
    }

    protected SimpleExtractionDao resolveDao(String db) {
        String dbType = context.getStringData(db + OPT_DB_TYPE);
        Class clazz = dbTypes.get(dbType);
        requires(clazz != null, "no or invalid JDBC driver defined", dbType);

        String url = context.getStringData(db + OPT_DB_URL);
        requires(StringUtils.isNotBlank(url), "Invalid connection string", url);

        SimpleExtractionDao dao;
        if (StringUtils.equals(dbType, "isam")) {
            dao = resolveIsamDao(db);
        } else {
            BasicDataSource newDs = new BasicDataSource();
            newDs.setDriverClassName(clazz.getName());
            newDs.setAccessToUnderlyingConnectionAllowed(true);
            newDs.setPoolPreparedStatements(true);
            newDs.setUrl(url);

            String username = context.getStringData(db + OPT_DB_USER);
            if (StringUtils.isNotBlank(username)) { newDs.setUsername(username); }

            String password = context.getStringData(db + OPT_DB_PASSWORD);
            if (StringUtils.isNotBlank(password)) { newDs.setPassword(password); }

            // handle auto commit (single transaction or not)
            boolean autocommit = context.getBooleanData(db + OPT_DB_AUTOCOMMIT, DEF_AUTOCOMMIT);
            newDs.setDefaultAutoCommit(autocommit);
            newDs.setEnableAutoCommitOnReturn(autocommit);
            if (!autocommit) { newDs.setDefaultTransactionIsolation(TRANSACTION_SERIALIZABLE); }

            dao = new SimpleExtractionDao();
            dao.setDataSource(newDs);
        }

        // allow dao to treat 'true null' as empty string, or whatever user decides
        dao.setTreatNullAs(context.getStringData(db + OPT_TREAT_NULL_AS, DEF_TREAT_NULL_AS));
        dao.setContext(context);

        dao.afterPropertiesSet();

        return dao;
    }

    protected SimpleExtractionDao resolveIsamDao(String db) {
        String url = context.getStringData(db + OPT_DB_URL);
        Map<String, String> connInfo = TextUtils.toMap(url, ";", "=");
        if (MapUtils.isEmpty(connInfo)) {
            throw new IllegalArgumentException("Connection URL not provided or in expected format");
        }
        if (!connInfo.containsKey("jdbc:connx:DD")) {
            throw new IllegalArgumentException("Connection URL missing DSN information");
        }
        if (!connInfo.containsKey("GateWay")) {
            throw new IllegalArgumentException("Connection URL missing gateway or server information");
        }

        // load driver
        String dbType = context.getStringData(db + OPT_DB_TYPE);
        Class clazz = dbTypes.get(dbType);
        String className = clazz.getName();
        try {
            Class.forName(className).newInstance();
        } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
            throw new IllegalArgumentException("Unable to load ISAM driver: " + e.getMessage(), e);
        }

        TCJdbcConnectionPoolDataSource newDs = new TCJdbcConnectionPoolDataSource();
        newDs.setDSN(connInfo.get("jdbc:connx:DD"));
        newDs.setGateway(connInfo.get("GateWay"));

        String username = context.getStringData(db + OPT_DB_USER);
        if (StringUtils.isNotBlank(username)) { newDs.setUser(username); }

        String password = context.getStringData(db + OPT_DB_PASSWORD);
        if (StringUtils.isNotBlank(password)) { newDs.setPassword(password); }

        SimpleExtractionDao dao = new SimpleExtractionDao();
        dao.setDataSource(newDs);

        boolean autocommit = context.getBooleanData(db + OPT_DB_AUTOCOMMIT, DEF_AUTOCOMMIT);
        dao.setAutoCommit(autocommit);

        return dao;
    }
}
