package org.nexial.core.plugins.db;

import java.sql.*;
import java.util.Properties;
import java.util.logging.Logger;

public class DBDriver implements Driver {

    private Driver driver;

    public DBDriver(Driver d) {
        this.driver = d;
    }

    @Override
    public boolean acceptsURL(String url) throws SQLException {
        return this.driver.acceptsURL(url);
    }

    @Override
    public Connection connect(String u, Properties p) throws SQLException {
        return this.driver.connect(u, p);
    }

    @Override
    public int getMajorVersion() {
        return this.driver.getMajorVersion();
    }

    @Override
    public int getMinorVersion() {
        return this.driver.getMinorVersion();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String u, Properties p) throws SQLException {
        return this.driver.getPropertyInfo(u, p);
    }

    @Override
    public boolean jdbcCompliant() {
        return this.driver.jdbcCompliant();
    }

}
