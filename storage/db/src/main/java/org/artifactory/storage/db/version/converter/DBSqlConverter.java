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

package org.artifactory.storage.db.version.converter;

import org.artifactory.common.config.db.DbType;
import org.artifactory.util.CommonDbUtils;
import org.artifactory.storage.db.util.DbUtils;
import org.artifactory.storage.db.util.JdbcHelper;
import org.artifactory.util.ResourceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Converts database by conversion sql script
 */
public class DBSqlConverter implements DBConverter {
    private static final Logger log = LoggerFactory.getLogger(DBSqlConverter.class);

    final String fromVersion;

    public DBSqlConverter(String fromVersion) {
        this.fromVersion = fromVersion;
    }

    @Override
    public void convert(JdbcHelper jdbcHelper, DbType dbType) {
        Connection con = null;
        try {
            // Build resource file name.
            String dbTypeName = dbType.toString();
            String resourcePath = "/conversion/" + dbTypeName + "/" + dbTypeName + "_" + fromVersion + ".sql";
            InputStream resource = ResourceUtils.getResource(resourcePath);
            if (resource == null) {
                throw new IOException("Database DDL resource not found at: '" + resourcePath + "'");
            }
            // Execute update
            log.info("Starting schema conversion: {}", resourcePath);
            con = jdbcHelper.getDataSource().getConnection();
            CommonDbUtils.executeSqlStream(con, resource);
            log.info("Finished schema conversion: {}", resourcePath);
        } catch (SQLException | IOException e) {
            String msg = "Could not convert DB using " + fromVersion + " converter";
            log.error(msg + " due to " + e.getMessage(), e);
            throw new RuntimeException(msg, e);
        } finally {
            DbUtils.close(con);
        }
    }
}
