package org.artifactory.util;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.artifactory.common.config.db.BlobWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Dan Feldman
 */
//TODO [by dan]: Most of this stuff is already in the db-infra module, should switch to it soon.
public class CommonDbUtils {
    private static final Logger log = LoggerFactory.getLogger(CommonDbUtils.class);

    public static final String INSERT_INTO = "INSERT INTO ";
    public static final String VALUES = " VALUES";

    public static void executeSqlStream(Connection con, InputStream in) throws IOException, SQLException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, Charset.forName("UTF-8")));
        Statement stmt = con.createStatement();
        try {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (StringUtils.isNotBlank(line) && !line.startsWith("--") && !line.startsWith("#")) {
                    sb.append(line);
                    if (line.endsWith(";")) {
                        String query = sb.substring(0, sb.length() - 1);
                        if (query.startsWith(INSERT_INTO)) {
                            String databaseProductName = con.getMetaData().getDatabaseProductName();
                            if ("Oracle".equals(databaseProductName)) {
                                query = transformInsertIntoForOracle(query);
                            }
                        }
                        log.debug("Executing query:\n{}", query);
                        try {
                            stmt.executeUpdate(query);
                            if (!con.getAutoCommit()) {
                                con.commit();
                            }
                        } catch (SQLException e) {
                            log.error("Failed to execute query: {}:\n{}", e.getMessage(), query);
                            throw e;
                        }
                        sb = new StringBuilder();
                    } else {
                        sb.append("\n");
                    }
                }
            }
        } finally {
            IOUtils.closeQuietly(reader);
            close(stmt);
        }
    }

    public static String transformInsertIntoForOracle(String query) {
        int values = query.indexOf(VALUES);
        if (values == -1) {
            throw new IllegalArgumentException("Query " + query + " does not the keyword " + VALUES);
        }
        String tableName = query.substring(INSERT_INTO.length(), values).trim();
        log.info("Doing insert all in Oracle for table " + tableName);
        StringBuilder sb = new StringBuilder("INSERT ALL ");
        boolean inValue = false;
        for (char c : query.toCharArray()) {
            if (c == '(') {
                inValue = true;
                sb.append("INTO ").append(tableName).append(VALUES).append(" (");
            } else if (c == ')') {
                inValue = false;
                sb.append(")\n");
            } else if (inValue) {
                sb.append(c);
            }
        }
        sb.append("SELECT * FROM dual");
        return sb.toString();
    }


    public static String parseInListQuery(String sql, Object... params) {
        int idx = sql.indexOf("(#)");
        if (idx != -1) {
            List<Integer> iterableSizes = new ArrayList<>(1);
            for (Object param : params) {
                if (param instanceof Collection) {
                    int size = ((Collection) param).size();
                    if (size > 0) {
                        iterableSizes.add(size);
                    }
                }
            }
            if (iterableSizes.isEmpty()) {
                throw new IllegalArgumentException("Could not find collection in parameters needed for query " + sql);
            }

            StringBuilder builder = new StringBuilder(sql.substring(0, idx + 1));
            for (int i = 0; i < iterableSizes.get(0); i++) {
                if (i != 0) {
                    builder.append(',');
                }
                builder.append('?');
            }
            builder.append(sql.substring(idx + 2));
            return builder.toString();
        }
        return sql;
    }

    public static void setParamsToStmt(PreparedStatement pstmt, Object[] params) throws SQLException {
        int i = 1;
        for (Object param : params) {
            if (param instanceof Iterable) {
                for (Object p : (Iterable) param) {
                    pstmt.setObject(i++, p);
                }
            } else if (param instanceof BlobWrapper) {
                BlobWrapper blobWrapper = (BlobWrapper) param;
                if (blobWrapper.getLength() < 0) {
                    pstmt.setBinaryStream(i++, blobWrapper.getInputStream());
                } else {
                    pstmt.setBinaryStream(i++, blobWrapper.getInputStream(), blobWrapper.getLength());
                }
            } else {
                pstmt.setObject(i++, param);
            }
        }
    }

    /**
     * Closes the given statement and just logs any exception.
     *
     * @param stmt The {@link Statement} to close.
     */
    public static void close(@Nullable Statement stmt) {
        if (stmt != null) {
            try {
                stmt.close();
            } catch (SQLException e) {
                log.trace("Could not close JDBC statement", e);
            } catch (Exception e) {
                log.trace("Unexpected exception when closing JDBC statement", e);
            }
        }
    }

}
