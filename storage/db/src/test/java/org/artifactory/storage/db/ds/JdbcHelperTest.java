package org.artifactory.storage.db.ds;

import org.artifactory.common.config.db.DbType;
import org.artifactory.storage.db.itest.DbBaseTest;
import org.artifactory.storage.db.util.DbUtils;
import org.artifactory.storage.db.util.JdbcHelper;
import org.artifactory.storage.db.util.ResultSetWrapper;
import org.artifactory.test.TestUtils;
import org.testng.annotations.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.testng.Assert.assertEquals;

/**
 * Tests the {@link JdbcHelper}.
 *
 * @author Yossi Shaul
 */
@Test
public class JdbcHelperTest extends DbBaseTest {

    public void testTxIsolationDefault() throws SQLException {
        ResultSet rs = jdbcHelper.executeSelect("select count(1) from nodes");
        Connection con = getConnection(rs);
        assertEquals(con.getTransactionIsolation(), Connection.TRANSACTION_READ_COMMITTED);
        DbUtils.close(rs);
    }

    public void testTxIsolationResetBackRoReadCommitted() throws SQLException {
        if (dbService.getDatabaseType().equals(DbType.ORACLE)) {
            return; // dirty reads are not supported by oracle driver
        }
        ResultSet rs = null;
        try {
            rs = jdbcHelper.executeSelect("select count(1) from nodes", true, new Object[]{});
            Connection con = getConnection(rs);
            assertEquals(con.getTransactionIsolation(), Connection.TRANSACTION_READ_UNCOMMITTED);
            // return it back to the pool and expect to get back the default TX isolation
            DbUtils.close(rs);
            rs = jdbcHelper.executeSelect("select count(1) from nodes");
            assertEquals(getConnection(rs).getTransactionIsolation(), Connection.TRANSACTION_READ_COMMITTED);
        } finally {
            DbUtils.close(rs);
        }
    }

    private Connection getConnection(ResultSet rs) {
        ResultSetWrapper rsw = (ResultSetWrapper) Proxy.getInvocationHandler(rs);
        return TestUtils.getField(rsw, "con", Connection.class);
    }

}
