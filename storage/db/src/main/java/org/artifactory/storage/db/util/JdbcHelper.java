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

import org.apache.commons.lang.StringUtils;
import org.artifactory.common.config.db.BlobWrapper;
import org.artifactory.storage.db.DbService;
import org.artifactory.util.PerfTimer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Nonnull;
import javax.sql.DataSource;
import java.sql.*;

import static org.artifactory.util.CommonDbUtils.parseInListQuery;
import static org.artifactory.util.CommonDbUtils.setParamsToStmt;

/**
 * A helper class to execute jdbc queries.
 *
 * @author Yossi Shaul
 */
@Service
public class JdbcHelper {
    private static final Logger log = LoggerFactory.getLogger(JdbcHelper.class);

    private final DataSource dataSource;
    private boolean closed = false;

    private final SqlTracer tracer = new SqlTracer();
    @Autowired
    public JdbcHelper(@Qualifier("dataSource") DataSource dataSource) {
        this.dataSource = dataSource;
    }

    private void debugSql(String sql, Object[] params) {
        if (log.isDebugEnabled()) {
            String resolved = resolveQuery(sql, params);
            log.debug("Executing SQL: '" + resolved + "'.");
        }
    }

    public static String resolveQuery(String sql, Object[] params) {
        int expectedParamsCount = StringUtils.countMatches(sql, "?");
        int paramsLength = params == null ? 0 : params.length;
        if (expectedParamsCount != paramsLength) {
            log.warn("Unexpected parameters count. Expected {} but got {}", expectedParamsCount, paramsLength);
        }

        if (params == null || params.length == 0) {
            return sql;
        } else if (expectedParamsCount == paramsLength) {
            // replace placeholders in the query with matching parameter values
            // this method doesn't attempt to escape characters
            Object[] printParams = new Object[params.length];
            for (int i = 0; i < params.length; i++) {
                Object current = params[i];
                if (current == null) {
                    current = "NULL";
                } else if (current instanceof String) {
                    current = "'" + params[i] + "'";
                } else if (current instanceof BlobWrapper) {
                    current = "BLOB(length: " + ((BlobWrapper) params[i]).getLength() + ")";
                }
                printParams[i] = current;
            }
            //RTFACT-10291 escape % sent in the SQL like statement in order to avoid String.format
            // MissingFormatArgumentException
            sql = sql.replace("%", "%%").replaceAll("\\?", "%s");
            sql = String.format(sql, printParams);
        } else {    // params not null but there's a difference between actual and expected
            StringBuilder builder = new StringBuilder();
            builder.append("Executing SQL: '").append(sql).append("'");
            if (params.length > 0) {
                builder.append(" with params: ");
                for (int i = 0; i < params.length; i++) {
                    builder.append("'");
                    builder.append(params[i]);
                    builder.append("'");
                    if (i < params.length - 1) {
                        builder.append(", ");
                    }
                }
            }
            sql = builder.toString();
        }
        return sql;
    }

    /**
     * @return A transaction aware connection
     */
    private Connection getConnection() throws SQLException {
        if (closed) {
            throw new IllegalStateException("DataSource is closed");
        }
        return DataSourceUtils.doGetConnection(dataSource);
    }

    public DataSource getDataSource() {
        if (closed) {
            throw new IllegalStateException("DataSource is closed");
        }
        return dataSource;
    }

    @Nonnull
    public ResultSet executeSelect(String query, Object... params) throws SQLException {
        return executeSelect(query, false, params);
    }

    @Nonnull
    public ResultSet executeSelect(String query, boolean allowDirtyReads, Object... params) throws SQLException {
        if (closed) {
            throw new IllegalStateException("DataSource is closed cannot execute select query:\n'" + query + "'");
        }
        tracer.traceSelectQuery(query);
        debugSql(query, params);

        PerfTimer timer = null;
        if (log.isDebugEnabled()) {
            timer = new PerfTimer();
        }
        Connection con = null;
        Statement stmt = null;
        ResultSet rs = null;
        try {
            con = getConnection();
            // allow dirty reads in case of non transactional aql query
            // ConnectionState will reset to the default value next time this connection is borrowed
            //@Todo need to a fine better (elegant) way to force this
            allowDirtyReads(allowDirtyReads, con);
            if (params == null || params.length == 0) {
                stmt = con.createStatement();
                rs = stmt.executeQuery(query);
            } else {
                PreparedStatement pstmt = con.prepareStatement(parseInListQuery(query, params));
                stmt = pstmt;
                setParamsToStmt(pstmt, params);
                rs = pstmt.executeQuery();
            }
            if (!TxHelper.isInTransaction() && !con.getAutoCommit()) {
                con.commit();
            }
            if (timer != null && log.isDebugEnabled()) {
                timer.stop();
                log.debug("Query returned in {} : '{}'", timer, resolveQuery(query, params));
            }
            return ResultSetWrapper.newInstance(con, stmt, rs, dataSource);
        } catch (Exception e) {
            DbUtils.close(con, stmt, rs, dataSource);
            if (e instanceof SQLException) {
                throw (SQLException) e;
            } else {
                throw new SQLException("Unexpected exception: " + e.getMessage(), e);
            }
        }
    }

    public int executeUpdate(String query, Object... params) throws SQLException {
        if (closed) {
            throw new IllegalStateException("DataSource is closed cannot execute update query:\n'" + query + "'");
        }
        tracer.traceSelectQuery(query);
        debugSql(query, params);

        PerfTimer timer = null;
        if (log.isDebugEnabled()) {
            timer = new PerfTimer();
        }
        Connection con = null;
        Statement stmt = null;
        int results;
        try {
            con = getConnection();
            if (params == null || params.length == 0) {
                stmt = con.createStatement();
                results = stmt.executeUpdate(query);
            } else {
                PreparedStatement pstmt = con.prepareStatement(parseInListQuery(query, params));
                stmt = pstmt;
                setParamsToStmt(pstmt, params);
                results = pstmt.executeUpdate();
            }
            if (timer != null && log.isDebugEnabled()) {
                timer.stop();
                log.debug("Query returned with {} results in {} : '{}'",
                        results, timer, resolveQuery(query, params));
            }
            return results;
        } finally {
            DbUtils.close(con, stmt, null, dataSource);
        }
    }

    public int executeSelectCount(String query, Object... params) throws SQLException {
        try (ResultSet resultSet = executeSelect(query, params)) {
            int count = 0;
            if (resultSet.next()) {
                count = resultSet.getInt(1);
            }
            return count;
        }
    }

    /**
     * Helper method to select long value. This method expects long value returned as the first column.
     * It ignores multiple results.
     *
     * @param query  The select query to execute
     * @param params The query parameters
     * @return Long value if a result was found or {@link org.artifactory.storage.db.DbService#NO_DB_ID} if not found
     */
    public long executeSelectLong(String query, Object... params) throws SQLException {
        try (ResultSet resultSet = executeSelect(query, params)) {
            long result = DbService.NO_DB_ID;
            if (resultSet.next()) {
                result = resultSet.getLong(1);
            }
            return result;
        }
    }

    private void allowDirtyReads(boolean allowDirtyReads, Connection con) throws SQLException {
        if (allowDirtyReads && con != null) {
            con.setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED);
        }
    }

    public void destroy() {
        closed = true;
        DbUtils.closeDataSource(dataSource);
    }

    public boolean isClosed() {
        return closed;
    }

    public long getSelectQueriesCount() {
        return tracer.getSelectQueriesCount();
    }

    public long getUpdateQueriesCount() {
        return tracer.getUpdateQueriesCount();
    }

    public SqlTracer getTracer() {
        return tracer;
    }
}
