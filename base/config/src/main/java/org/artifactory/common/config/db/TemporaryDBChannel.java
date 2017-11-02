package org.artifactory.common.config.db;

import org.artifactory.util.CommonDbUtils;

import java.sql.*;
import java.util.Properties;

import static org.artifactory.util.CommonDbUtils.parseInListQuery;
import static org.artifactory.util.CommonDbUtils.setParamsToStmt;

/**
 * @author gidis
 */
public class TemporaryDBChannel implements DbChannel {

    private final Driver driver;
    private final DbType dbType;
    private Connection conn = null;

    public TemporaryDBChannel(ArtifactoryDbProperties dbProperties) {
        try {
            String password = dbProperties.getPassword();
            String userName = dbProperties.getUsername();
            String url = dbProperties.getConnectionUrl();
            String driverClass = dbProperties.getDriverClass();
            Class<?> aClass = Class.forName(driverClass);
            driver = (Driver) aClass.newInstance();
            Properties info = new Properties();
            info.put("user", userName == null ? "" : userName);
            info.put("password", password == null ? "" : password);
            this.conn = driver.connect(url, info);
            this.conn.setAutoCommit(true);
            this.dbType = dbProperties.getDbType();
        } catch (Exception e) {
            throw new RuntimeException("Could't establish connection with db: " + dbProperties.getConnectionUrl(), e);
        }
    }

    @Override
    public ResultSet executeSelect(String query, Object... params) throws SQLException {
        PreparedStatement preparedStatement;
        if (params == null || params.length == 0) {
            preparedStatement = conn.prepareStatement(query);
        } else {
            preparedStatement = conn.prepareStatement(parseInListQuery(query, params));
            setParamsToStmt(preparedStatement, params);
        }
        // As long as the result set is closed by the caller the statement will be closed along with it.
        return preparedStatement.executeQuery();
    }

    @Override
    public int executeUpdate(String query, Object... params) throws SQLException {
        PreparedStatement preparedStatement = null;
        try {
            if (params == null || params.length == 0) {
                preparedStatement = conn.prepareStatement(query);
            } else {
                preparedStatement = conn.prepareStatement(parseInListQuery(query, params));
                setParamsToStmt(preparedStatement, params);
            }
            return preparedStatement.executeUpdate();
        } finally {
            CommonDbUtils.close(preparedStatement);
        }
    }

    public DbType getDbType() {
        return dbType;
    }

    public Connection getConnection() {
        return conn;
    }

    @Override
    public void close() {
        try {
            conn.close();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to close temporary DB channel connection", e);
        }
    }
}
