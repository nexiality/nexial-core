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
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;
import javax.sql.DataSource;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.plugins.ThirdPartyDriverInfo;
import org.nexial.core.utils.ConsoleUtils;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import static java.io.File.separator;
import static java.sql.Connection.TRANSACTION_SERIALIZABLE;
import static org.apache.commons.lang3.SystemUtils.USER_HOME;
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

    protected String resolveDBDriverLocation(String dbType) throws IOException {
        DBDriverHelper helper = DBDriverHelper.newInstance(dbType, context);
        File driver = helper.resolveDriver();
        return driver.getPath();
    }

    protected SimpleExtractionDao resolveDao(String db) {
        Driver d = null;

        String dbType = context.getStringData(db + OPT_DB_TYPE);
        requiresNotBlank(dbType, "database '" + db + "' is not defined; unable to proceed");

        // support user-defined JDBC Driver ClassName via <db>.JavaClassName
        String className = dbTypes.containsKey(dbType) ?
                           dbTypes.get(dbType) : context.getStringData(db + OPT_DB_CLASSNAME);
        requiresNotBlank(className, "no or invalid JDBC driver defined", dbType);

        // boolean isCustomDB = !dbTypes.containsKey(dbType);
        Map<String, String> configs = context.getDbdriverHelperConfig();
        boolean isDriverConfiguredForDownload = StringUtils.isNotBlank(configs.get(dbType));

        try {
            try {
                Class.forName(className).getDeclaredConstructor().newInstance();
            } catch (ClassNotFoundException e) {
                if (isDriverConfiguredForDownload) {
                    String driverJarfilePath = resolveDBDriverLocation(dbType);
                    URLClassLoader ucl = new URLClassLoader(new URL[]{new URL("jar:file:" + driverJarfilePath + "!/")});
                    d = (Driver) Class.forName(className, true, ucl).getDeclaredConstructor().newInstance();
                    DriverManager.registerDriver(new DBDriver(d));
                } else { throw e; }
            }
        } catch (Exception e) {
            String message;
            ThirdPartyDriverInfo driverInfo = dbJarInfo.get(dbType);
            if (driverInfo != null) {
                message = driverInfo.toString();
            } else {
                message = "Unable to load the requested database driver (" + dbType + ") - " +
                          ExceptionUtils.getRootCauseMessage(e) + ".\n" +
                          "Please download the appropriate JDBC jar and place it in the " + USER_HOME + "/.nexial/jar/";
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

            //set driver from downloaded driver jar
            if (isDriverConfiguredForDownload) { newDs.setDriver(d); }

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

        // https://bitbucket.org/dbschema/mongodb-jdbc-driver/src/master/
        // handle credential
        Properties props = new Properties();
        String username = context.getStringData(db + OPT_DB_USER);
        String password = context.getStringData(db + OPT_DB_PASSWORD);
        if (StringUtils.isNotBlank(username) && StringUtils.isNotBlank(password)) {
            props.put("user", username);
            props.put("password", password);
        }

        Map<String, String> urlOptions = TextUtils.toMap(StringUtils.substringAfter(url, "?"), "&", "=");

        // special config to expand result JSON into columns
        boolean expandDoc = context.getBooleanData(db + OPT_DB_EXPAND_DOC, false);
        if (expandDoc) { urlOptions.put("expand", "true"); }

        // additional security consideration when connecting to database... mostly for cloud/SaaS -based db
        String trustStore = context.getStringData(OPT_DB_TRUST_STORE);
        String trustStorePassword = context.getStringData(OPT_DB_TRUST_STORE_PWD);

        // special treatment for AWS DocumentDB
        // https://docs.aws.amazon.com/documentdb/latest/developerguide/connect_programmatically.html#connect_programmatically-tls_enabled
        boolean isDocumentDB = context.getBooleanData(db + OPT_IS_DOCUMENTDB, false);
        if (isDocumentDB) {
            String jksLocation;
            String jksPassword;
            if (StringUtils.isNotBlank(trustStore)) {
                jksLocation = trustStore;
                jksPassword = trustStorePassword;
            } else {
                jksLocation = StringUtils.appendIfMissing(context.getProject().getNexialHome(), separator) +
                              "bin" + separator + "rds-truststore.jks";
                jksPassword = "nexial_mongo";
            }

            ConsoleUtils.log("detect use of DocumentDB; adding AWS JKS to trust store: " + jksLocation);
            System.setProperty("javax.net.ssl.trustStore", jksLocation);
            if (StringUtils.isNotBlank(jksPassword)) {
                System.setProperty("javax.net.ssl.trustStorePassword", jksPassword);
            }

            urlOptions.put("ssl", "true");
            urlOptions.put("sslInvalidHostNameAllowed", "true");
        } else if (StringUtils.isNotBlank(trustStore)) {
            ConsoleUtils.log("adding to trust store: " + trustStore);
            System.setProperty("javax.net.ssl.trustStore", trustStore);

            if (StringUtils.isNotBlank(trustStorePassword)) {
                System.setProperty("javax.net.ssl.trustStorePassword", trustStorePassword);
            }

            urlOptions.put("ssl", "true");
            urlOptions.put("sslInvalidHostNameAllowed", "true");
        }

        if (!urlOptions.isEmpty()) {
            url = StringUtils.substringBefore(url, "?") + "?" + TextUtils.toString(urlOptions, "&", "=");
        }

        ConsoleUtils.log("connecting to MongoDB: " + url);

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
