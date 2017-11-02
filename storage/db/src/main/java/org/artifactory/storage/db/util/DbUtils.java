/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2016 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.storage.db.util;

import org.artifactory.util.CommonDbUtils;
import org.artifactory.storage.db.spring.ArtifactoryDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.DataSourceUtils;

import javax.annotation.Nullable;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * A utility class for common JDBC operations.
 *
 * @author Yossi Shaul
 */
public abstract class DbUtils {
    private static final Logger log = LoggerFactory.getLogger(DbUtils.class);
    public static final String INSERT_INTO = "INSERT INTO ";
    public static final String VALUES = " VALUES";

    /**
     * Closes the given resources. Exceptions are just logged.
     *
     * @param con  The {@link java.sql.Connection} to close
     * @param stmt The {@link java.sql.Statement} to close
     * @param rs   The {@link java.sql.ResultSet} to close
     */
    public static void close(@Nullable Connection con, @Nullable Statement stmt, @Nullable ResultSet rs) {
        try {
            close(rs);
        } finally {
            try {
                CommonDbUtils.close(stmt);
            } finally {
                close(con);
            }
        }
    }

    /**
     * Closes the given resources. Exceptions are just logged.
     *
     * @param con  The {@link java.sql.Connection} to close
     * @param stmt The {@link java.sql.Statement} to close
     * @param rs   The {@link java.sql.ResultSet} to close
     */
    public static void close(@Nullable Connection con, @Nullable Statement stmt, @Nullable ResultSet rs,
            @Nullable DataSource ds) {
        try {
            close(rs);
        } finally {
            try {
                CommonDbUtils.close(stmt);
            } finally {
                close(con, ds);
            }
        }
    }

    /**
     * Closes the given connection and just logs any exception.
     *
     * @param con The {@link java.sql.Connection} to close.
     */
    public static void close(@Nullable Connection con) {
        close(con, null);
    }

    /**
     * Closes the given connection and just logs any exception.
     *
     * @param con The {@link java.sql.Connection} to close.
     */
    public static void close(@Nullable Connection con, @Nullable DataSource ds) {
        if (con != null) {
            try {
                DataSourceUtils.doReleaseConnection(con, ds);
            } catch (SQLException e) {
                log.trace("Could not close JDBC connection", e);
            } catch (Exception e) {
                log.trace("Unexpected exception when closing JDBC connection", e);
            }
        }
    }

    /**
     * Closes the given result set and just logs any exception.
     *
     * @param rs The {@link java.sql.ResultSet} to close.
     */
    public static void close(@Nullable ResultSet rs) {
        if (rs != null) {
            try {
                rs.close();
            } catch (SQLException e) {
                log.trace("Could not close JDBC result set", e);
            } catch (Exception e) {
                log.trace("Unexpected exception when closing JDBC result set", e);
            }
        }
    }

    public static void closeDataSource(DataSource dataSource) {
        if (dataSource == null) {
            return;
        }
        if (dataSource instanceof ArtifactoryDataSource) {
            try {
                ((ArtifactoryDataSource) dataSource).close();
            } catch (Exception e) {
                String msg = "Error closing the data source " + dataSource + " due to:" + e.getMessage();
                if (log.isDebugEnabled()) {
                    log.error(msg, e);
                } else {
                    log.error(msg);
                }
            }
        }
    }

    public static boolean tableExists(DatabaseMetaData metaData, String tableName) throws SQLException {
        tableName = normalizedName(tableName, metaData);
        try (ResultSet rs = metaData.getTables(null, null, tableName, new String[]{"TABLE"})) {
            return rs.next();
        }
    }

    public static boolean columnExists(DatabaseMetaData metadata, String tableName, String columnName) throws SQLException {
        columnName = normalizedName(columnName, metadata);
        tableName = normalizedName(tableName, metadata);
        try (ResultSet rs = metadata.getColumns(null, null, tableName, columnName)) {
            return rs.next();
        }
    }

    public static String normalizedName(String name, DatabaseMetaData metaData) throws SQLException {
        if (metaData.storesLowerCaseIdentifiers()) {
            name = name.toLowerCase();
        } else if (metaData.storesUpperCaseIdentifiers()) {
            name = name.toUpperCase();
        }
        return name;
    }
}
