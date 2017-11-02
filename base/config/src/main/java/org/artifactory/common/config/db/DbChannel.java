package org.artifactory.common.config.db;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * @author gidis
 */
public interface DbChannel {

    ResultSet executeSelect(String query, Object... params) throws SQLException;

    int executeUpdate(String query, Object... params) throws SQLException;

    DbType getDbType();

    void close();
}
