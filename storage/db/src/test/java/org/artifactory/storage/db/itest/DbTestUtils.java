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

package org.artifactory.storage.db.itest;

import com.google.common.collect.Lists;
import org.apache.commons.io.FileUtils;
import org.artifactory.common.config.db.DbType;
import org.artifactory.util.CommonDbUtils;
import org.artifactory.util.ResourceUtils;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.*;
import java.util.Collections;
import java.util.List;

import static org.artifactory.storage.db.util.DbUtils.normalizedName;
import static org.testng.Assert.fail;

/**
 * A utility class for integration tests to clean and setup the database
 *
 * @author Yossi Shaul
 */
public class DbTestUtils {
    // BE CAREFUL do not have logger here. Tests not initialized correctly

    /**
     * A list of all the tables in the database
     */
    private static final List<String> tables = Collections.unmodifiableList(Lists.newArrayList(
            "db_properties", "artifactory_servers",
            "stats_remote", "stats", "watches", "node_props", "node_meta_infos", "nodes",
            "indexed_archives_entries", "archive_names", "archive_paths", "indexed_archives",
            "binary_blobs", "binaries",
            "aces", "acls", "users_groups", "groups", "user_props", "users",
            "permission_target_repos", "permission_targets",
            "configs", "tasks",
            "module_props", "build_props", "build_jsons", "build_promotions",
            "build_dependencies", "build_artifacts", "build_modules", "builds",
            "unique_ids"
    ));

    /**
     * A list of all the access server tables inside the artifactory database (since access server is embedded)
     */
    private static final List<String> accessServerTables = Collections.unmodifiableList(Lists.newArrayList(
            "access_tokens", "access_users", "access_servers"
    ));

    public static void refreshOrRecreateSchema(Logger log, Connection con, DbType dbType)
            throws IOException, SQLException {
        // to improve test speed, re-create the schema only if there's a missing table
        boolean recreateSchema = isTableMissing(con);
        if (recreateSchema) {
            log.info("Recreating test database schema for database: {}", dbType);
            dropAllExistingTables(con);
            createSchema(con, dbType);
        } else {
            log.info("Deleting database tables data from database: {}", dbType);
            deleteFromAllTables(con);
            deleteFromAccessServerTables(con);
        }
    }

    public static void dropAllExistingTables(Connection con) throws SQLException {
        for (String table : tables) {
            if (tableExists(con, table)) {
                try (Statement statement = con.createStatement()) {
                    statement.execute("DROP TABLE " + table);
                }
            }
        }
    }

    private static void deleteFromAllTables(Connection con) throws SQLException {
        for (String table : tables) {
            try (Statement statement = con.createStatement()) {
                statement.execute("DELETE FROM " + table);
            }
        }
    }

    private static void deleteFromAccessServerTables(Connection con) {
        for (String table : accessServerTables) {
            try (Statement statement = con.createStatement()) {
                statement.execute("DELETE FROM " + table);
            } catch (SQLException e) {
                //System.out.println("Failed to delete from access server table '" + table + "', ignoring. (" + e + ")");
            }
        }
    }

    public static boolean tableExists(Connection con, String tableName) throws SQLException {
        DatabaseMetaData metaData = con.getMetaData();
        tableName = normalizedName(tableName, metaData);
        try (ResultSet rs = metaData.getTables(null, null, tableName, new String[]{"TABLE"})) {
            return rs.next();
        }
    }

    public static boolean isTableMissing(Connection con) throws SQLException, IOException {
        for (String table : tables) {
            if (!tableExists(con, table)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isColumnExist(Connection con, String table, String column) throws SQLException, IOException {
        DatabaseMetaData metaData = con.getMetaData();
        String tableName = normalizedName(table, metaData);
        String columnName = normalizedName(column, metaData);
        try (ResultSet rs = metaData.getColumns(null, null, tableName, columnName)) {
            return rs.next();
        }
    }

    public static int getColumnSize(Connection con, String tableName, String columnName) throws SQLException {
        DatabaseMetaData metaData = con.getMetaData();
        tableName = normalizedName(tableName, metaData);
        columnName = normalizedName(columnName, metaData);

        try (Statement statement = con.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT * from " + tableName)) {
            ResultSetMetaData resultSetMetaData = resultSet.getMetaData();
            int columnCount = resultSetMetaData.getColumnCount();
            for (int i = 1; i <= columnCount; i++) {
                if (resultSetMetaData.getColumnName(i).equals(columnName)) {
                    return resultSetMetaData.getColumnDisplaySize(i);
                }
            }
        }

        return -1;
    }

    private static void createSchema(Connection con, DbType dbType) throws SQLException, IOException {
        // read ddl from file and execute
        CommonDbUtils.executeSqlStream(con, getDbSchemaSql(dbType));
    }

    private static InputStream getDbSchemaSql(DbType dbType) {
        String dbConfigDir = dbType.toString();
        return ResourceUtils.getResource("/" + dbConfigDir + "/" + dbConfigDir + ".sql");
    }

    public static void forceShutdownDerby(Logger log, String derbyDbHome) {
        if (derbyDbHome == null) {
            // Nothing to do
            return;
        }
        // Shutdown Jetty if needed
        File dbHome = new File(derbyDbHome);
        if (dbHome.exists()) {
            try {
                log.info("Shutting down Derby DB '{}'", dbHome.getAbsolutePath());
                // Forcing Derby shutdown
                DriverManager.getConnection("jdbc:derby:" + dbHome.getAbsolutePath() + ";shutdown=true");
            } catch (SQLException e) {
                // Good one should be ERROR 08006 for shutdown
                boolean containsGoodError = e instanceof SQLNonTransientConnectionException &&
                        e.getMessage().contains("shutdown");
                if (!containsGoodError) {
                    String msg = "Exception should have words 'Database shutdown' but we got: '" + e.getMessage() + "'";
                    log.error(msg, e);
                    fail(msg);
                } else {
                    log.info(e.getMessage());
                }
            }
            try {
                // Here we managed to shutdown Derby correctly. Let's remove the folder
                FileUtils.deleteDirectory(dbHome);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
