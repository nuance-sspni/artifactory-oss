package org.artifactory.storage.db.util;


import org.artifactory.test.TestUtils;
import org.testng.annotations.Test;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.fest.assertions.Assertions.assertThat;
import static org.testng.Assert.*;

/**
 * Unit tests for the {@link SqlTracer}.
 *
 * @author Yossi Shaul
 */
@Test
public class SqlTracerTest {

    public void defaultConstructor() {
        SqlTracer tracer = new SqlTracer();
        assertEquals(tracer.getSelectQueriesCount(), 0);
        assertEquals(tracer.getUpdateQueriesCount(), 0);
        assertFalse(tracer.isTracingEnabled());
        assertEquals(getTraceStartTime(tracer), 0);
        assertEquals(getTraceEndTime(tracer), 0);
    }

    public void incrementSelect() {
        SqlTracer tracer = new SqlTracer();
        tracer.traceSelectQuery("xxx");
        assertEquals(tracer.getSelectQueriesCount(), 1);
        assertEquals(tracer.getUpdateQueriesCount(), 0);
    }

    public void incrementUpdate() {
        SqlTracer tracer = new SqlTracer();
        tracer.traceUpdateQuery("yyy");
        assertEquals(tracer.getSelectQueriesCount(), 0);
        assertEquals(tracer.getUpdateQueriesCount(), 1);
    }

    public void noQueryMetricsWhenDisabled() {
        SqlTracer tracer = new SqlTracer();
        tracer.traceUpdateQuery("xxx");
        tracer.traceSelectQuery("zzz");
        assertThat(tracer.getQueries()).hasSize(0);
    }

    public void enableDisableTracing() {
        SqlTracer tracer = new SqlTracer();
        tracer.enableTracing();
        assertTrue(tracer.isTracingEnabled());
        tracer.disableTracing();
        assertFalse(tracer.isTracingEnabled());
    }

    public void disableKeepsMetrics() {
        SqlTracer tracer = new SqlTracer(true);
        tracer.traceUpdateQuery("xxx");
        tracer.traceSelectQuery("zzz");
        tracer.disableTracing();
        assertEquals(tracer.getSelectQueriesCount(), 1);
        assertEquals(tracer.getUpdateQueriesCount(), 1);
        assertThat(tracer.getQueries()).hasSize(2);
    }

    public void traceTimes() {
        SqlTracer tracer = new SqlTracer();
        assertEquals(getTraceStartTime(tracer), 0);
        assertEquals(getTraceEndTime(tracer), 0);

        long beforeEnable = System.currentTimeMillis();
        tracer.enableTracing();
        assertTrue(getTraceStartTime(tracer) >= beforeEnable);
        assertEquals(getTraceEndTime(tracer), 0);

        long beforeDisable = System.currentTimeMillis();
        tracer.disableTracing();
        assertTrue(getTraceStartTime(tracer) <= beforeDisable);
        assertTrue(getTraceEndTime(tracer) >= beforeDisable);
    }

    public void traceTimesEnableDisable() {
        SqlTracer tracer = new SqlTracer(true);
        assertTrue(getTraceStartTime(tracer) <= System.currentTimeMillis());
        assertEquals(getTraceEndTime(tracer), 0);

        long beforeDisable = System.currentTimeMillis();
        tracer.disableTracing();
        assertTrue(getTraceEndTime(tracer) >= beforeDisable);
        long beforeEnable = System.currentTimeMillis();
        tracer.enableTracing();
        // should now reset the end time
        assertTrue(getTraceStartTime(tracer) >= beforeEnable);
        assertEquals(getTraceEndTime(tracer), 0);
    }

    public void resetMetrics() {
        SqlTracer tracer = new SqlTracer(true);
        tracer.traceUpdateQuery("xxx");
        tracer.traceSelectQuery("zzz");
        assertEquals(tracer.getSelectQueriesCount(), 1);
        assertEquals(tracer.getUpdateQueriesCount(), 1);
        assertThat(tracer.getQueries()).hasSize(2);
        assertTrue(getTraceStartTime(tracer) <= System.currentTimeMillis());
        assertEquals(getTraceEndTime(tracer), 0L);
        long beforeReset = System.currentTimeMillis();
        tracer.resetTracing();
        assertEquals(tracer.getSelectQueriesCount(), 0);
        assertEquals(tracer.getUpdateQueriesCount(), 0);
        assertThat(tracer.getQueries()).isEmpty();
        assertTrue(getTraceStartTime(tracer) >= beforeReset);
        assertEquals(getTraceEndTime(tracer), 0L);
    }

    private long getTraceEndTime(SqlTracer tracer) {
        return TestUtils.getField(tracer, "traceEndTime", Long.class).longValue();
    }

    private long getTraceStartTime(SqlTracer tracer) {
        return TestUtils.getField(tracer, "traceStartTime", Long.class).longValue();
    }

    public void queries() {
        SqlTracer tracer = new SqlTracer(true);
        tracer.traceUpdateQuery("xxx");
        tracer.traceSelectQuery("zzz");
        ConcurrentHashMap<String, AtomicLong> queries = tracer.getQueries();
        assertThat(queries).hasSize(2);
        assertTrue(queries.containsKey("xxx"));
        assertEquals(queries.get("xxx").get(), 1);
        assertEquals(queries.get("zzz").get(), 1);
    }
}