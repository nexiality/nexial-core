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
import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;
import javax.sql.DataSource;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.plugins.ThirdPartyDriverInfo;
import org.nexial.core.utils.ConsoleUtils;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import static java.sql.Connection.TRANSACTION_SERIALIZABLE;
import static org.nexial.core.NexialConst.Rdbms.*;
import static org.nexial.core.utils.CheckUtils.requiresNotBlank;
import static org.nexial.core.utils.CheckUtils.requiresNotNull;

public class DataAccess implements ApplicationContextAware {
    private static final String ISAM_PROP_DD = "jdbc:connx:DD";
    private static final String ISAM_PROP_GW = "GateWay";

    protected Map<String, String> dbTypes;
    protected ApplicationContext spring;
    protected ExecutionContext context;
    protected Map<String, ThirdPartyDriverInfo> dbJarInfo;

    @Override
    public void setApplicationContext(ApplicationContext ctx) throws BeansException { spring = ctx; }

    // we want to support ALL types of SQL, including those vendor-specific
    // so for that reason, we are no longer insisting on the use of standard ANSI sql
    // protected List<String> validSQLStartWords;
    // public void setValidSQLStartWords(List<String> validSQLStartWords) { this.validSQLStartWords = validSQLStartWords; }
    // protected boolean validSQL(String query) { return validSQLStartWords.contains(fetchStartKeyword(query)); }

    public void setContext(ExecutionContext context) { this.context = context; }

    public void setDbTypes(Map<String, String> dbTypes) { this.dbTypes = dbTypes; }

    public void setDbJarInfo(Map<String, ThirdPartyDriverInfo> dbJarInfo) { this.dbJarInfo = dbJarInfo; }

    public JdbcResult execute(String query, SimpleExtractionDao dao) { return execute(query, dao, null); }

    public JdbcResult execute(String query, SimpleExtractionDao dao, File saveTo) {
        requiresNotBlank(query, "Unknown/unsupported query", query);
        requiresNotNull(dao, "Invalid data access object");
        return dao.executeSql(query, saveTo);
    }

    // public Class getDriverClass(String db) { return dbTypes.get(context.getStringData(db + OPT_DB_TYPE)); }
    //
    // protected static boolean isStartedWith(String query, List<String> startWords) {
    //     if (CollectionUtils.isEmpty(startWords)) { return false; }
    //
    //     String query1 = StringUtils.upperCase(query);
    //     for (String startWord : startWords) { if (StringUtils.startsWith(query1, startWord)) { return true; } }
    //
    //     return false;
    // }
    //
    // protected boolean requires(boolean condition, String errorMessage, Object... params) {
    //     if (!condition) { throw new IllegalArgumentException(errorMessage + ": " + ArrayUtils.toString(params)); }
    //     return true;
    // }
    //
    // protected String fetchStartKeyword(String query) {
    //     return
    //         StringUtils.trim(
    //             StringUtils.upperCase(StringUtils.substringBefore(StringUtils.replaceAll(query, "[\r\n]", " "), " "))) +
    //         " ";
    // }

    protected SimpleExtractionDao resolveDao(String db) {
        String dbType = context.getStringData(db + OPT_DB_TYPE);
        requiresNotBlank(dbType, "database '" + db + "' is not defined; unable to proceed");

        // support user-defined JDBC Driver ClassName via <db>.JavaClassName
        String className = dbTypes.containsKey(dbType) ?
                           dbTypes.get(dbType) : context.getStringData(db + OPT_DB_CLASSNAME);
        requiresNotBlank(className, "no or invalid JDBC driver defined", dbType);

        try {
            // make sure required class/jar exists
            Class.forName(className).getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            String message;
            ThirdPartyDriverInfo driverInfo = dbJarInfo.get(dbType);
            if (driverInfo != null) {
                message = driverInfo.toString();
            } else {
                message = "Fail to load the request database driver.  Make sure the appropriate jar is added to lib/.";
            }

            ConsoleUtils.showMissingLibraryError(message);
            context.setFailImmediate(true);
            throw new RuntimeException(message);
        }

        String url = context.getStringData(db + OPT_DB_URL);
        requiresNotBlank(url, "Invalid connection string", url);

        SimpleExtractionDao dao;
        if (StringUtils.equals(dbType, "isam")) {
            dao = resolveIsamDao(db);
        } else if (StringUtils.equals(dbType, "mongodb")) {
            dao = resolveMongoDao(db, className);
        } else {
            BasicDataSource newDs = new BasicDataSource();
            newDs.setDriverClassName(className);
            newDs.setAccessToUnderlyingConnectionAllowed(true);
            newDs.setPoolPreparedStatements(true);
            newDs.setUrl(url);

            // username/password are not required
            String username = context.getStringData(db + OPT_DB_USER);
            if (StringUtils.isNotBlank(username)) { newDs.setUsername(username); }

            String password = context.getStringData(db + OPT_DB_PASSWORD);
            if (StringUtils.isNotBlank(password)) { newDs.setPassword(password); }

            // handle auto commit (single transaction or not)
            boolean autocommit = context.getBooleanData(db + OPT_DB_AUTOCOMMIT, DEF_AUTOCOMMIT);
            newDs.setDefaultAutoCommit(autocommit);
            newDs.setAutoCommitOnReturn(autocommit);
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

        String errPrefix = "Connection URL missing property: ";
        if (MapUtils.isEmpty(connInfo)) { throw new IllegalArgumentException("Connection URL missing"); }
        if (!connInfo.containsKey(ISAM_PROP_DD)) { throw new IllegalArgumentException(errPrefix + ISAM_PROP_DD); }
        if (!connInfo.containsKey(ISAM_PROP_GW)) { throw new IllegalArgumentException(errPrefix + ISAM_PROP_GW); }

        String dsn = connInfo.get(ISAM_PROP_DD);
        String gateway = connInfo.get(ISAM_PROP_GW);
        String username = context.getStringData(db + OPT_DB_USER);
        String password = context.getStringData(db + OPT_DB_PASSWORD);

        DataSource newDs;
        try {
            newDs = (DataSource) Class.forName("com.Connx.jdbc.TCJdbc.TCJdbcConnectionPoolDataSource").newInstance();
            BeanUtils.setProperty(newDs, "DSN", dsn);
            BeanUtils.setProperty(newDs, "gateway", gateway);
            if (StringUtils.isNotBlank(username)) { BeanUtils.setProperty(newDs, "user", username); }
            if (StringUtils.isNotBlank(password)) { BeanUtils.setProperty(newDs, "password", password); }
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | ClassNotFoundException e) {
            throw new RuntimeException("Unable to set up Connx JDBC driver: " + e.getMessage());
        }

        SimpleExtractionDao dao = new SimpleExtractionDao();
        dao.setDataSource(newDs);
        dao.setAutoCommit(context.getBooleanData(db + OPT_DB_AUTOCOMMIT, DEF_AUTOCOMMIT));
        return dao;
    }

    protected SimpleExtractionDao resolveMongoDao(String db, String className) {
        String url = context.getStringData(db + OPT_DB_URL);
        url = StringUtils.prependIfMissing(url, "jdbc:");

        boolean expandDoc = BooleanUtils.toBoolean(context.getStringData(db + OPT_DB_EXPAND_DOC));
        if (expandDoc && !StringUtils.contains(url, URL_OPT_EXPAND_DOC)) {
            url = StringUtils.appendIfMissing(url, "?") + URL_OPT_EXPAND_DOC;
        }

        String username = context.getStringData(db + OPT_DB_USER);
        String password = context.getStringData(db + OPT_DB_PASSWORD);

        Properties props = new Properties();
        if (StringUtils.isNotBlank(username) && StringUtils.isNotBlank(password)) {
            props.put("user", username);
            props.put("password", password);
        }

        try {
            Connection connection = DriverManager.getConnection(url, props);
            connection.setAutoCommit(true);

            SingleConnectionDataSource newDs = new SingleConnectionDataSource(connection, true);
            newDs.setAutoCommit(connection.getAutoCommit());
            newDs.setCatalog(connection.getCatalog());
            newDs.setUrl(url);
            newDs.setUsername(username);
            newDs.setPassword(password);

            MongodbJdbcExtractionDao dao = new MongodbJdbcExtractionDao();
            dao.setDataSource(newDs);
            dao.setExpandDoc(expandDoc);
            dao.setAutoCommit(connection.getAutoCommit());
            return dao;
        } catch (SQLException e) {
            throw new RuntimeException("Unable to set up MongoDB connection: " + e.getMessage());
        }
    }
}
