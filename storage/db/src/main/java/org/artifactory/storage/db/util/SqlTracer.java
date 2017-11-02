package org.artifactory.storage.db.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Traces sql queries and optionally stores additional metrics.
 *
 * @author Yossi Shaul
 */
public class SqlTracer {

    private static final Logger log = LoggerFactory.getLogger(SqlTracer.class);

    private static final int REPORT_LIMIT = 100;

    private ConcurrentHashMap<String, AtomicLong> queries = new ConcurrentHashMap<>();
    private final AtomicLong selectQueriesCounter = new AtomicLong();

    private final AtomicLong updateQueriesCounter = new AtomicLong();
    private transient boolean enabled;
    /**
     * Time tracing has begun in milliseconds
     */
    private long traceStartTime;

    /**
     * Time tracing has finished in milliseconds
     */
    private long traceEndTime;

    public SqlTracer() {
        this(false);
    }

    SqlTracer(boolean enabled) {
        if (enabled) {
            traceStartTime = System.currentTimeMillis(); // first initialization
        }
        setEnabled(enabled);
    }

    private void setEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return;
        }
        if (enabled) {  // enabling -> set the trace start time and nullify end time
            traceStartTime = System.currentTimeMillis();
            traceEndTime = 0;
        } else {    // disabling -> set the trace end time
            traceEndTime = System.currentTimeMillis();
        }
        this.enabled = enabled;
    }

    public void traceSelectQuery(String query) {
        selectQueriesCounter.incrementAndGet();
        incrementQueryCount(query);
    }

    public void traceUpdateQuery(String query) {
        updateQueriesCounter.incrementAndGet();
        incrementQueryCount(query);
    }

    public long getSelectQueriesCount() {
        return selectQueriesCounter.get();
    }

    public long getUpdateQueriesCount() {
        return updateQueriesCounter.get();
    }

    /**
     * @return True if deeper sql queries tracing is enabled
     */
    public boolean isTracingEnabled() {
        return enabled;
    }

    /**
     * Enables deeper sql queries tracing. Has no affect if tracing is already enabled.
     */
    public void enableTracing() {
        setEnabled(true);
    }

    /**
     * Disables deeper sql queries tracing. Has no affect if tracing is already disabled.
     * Disabling the tracer keeps the current trace metrics. Call {@link SqlTracer#resetTracing()} in order to
     * remove tracing.
     */
    public void disableTracing() {
        setEnabled(false);
    }

    /**
     * Resets any tracing information.
     */
    public void resetTracing() {
        selectQueriesCounter.set(0);
        updateQueriesCounter.set(0);
        queries = new ConcurrentHashMap<>();
        traceEndTime = 0;
        traceStartTime = enabled ? traceStartTime = System.currentTimeMillis() : 0;
    }

    ConcurrentHashMap<String, AtomicLong> getQueries() {
        return queries;
    }

    public String report() {
        long reportTime = traceEndTime > 0 ? traceEndTime : System.currentTimeMillis();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        StringBuilder sb = new StringBuilder();
        long selectCount = selectQueriesCounter.get();
        long updateCount = updateQueriesCounter.get();
        sb.append("SQL tracing report\n");
        sb.append("Enabled: ").append(enabled).append("\n");
        sb.append("Report time: ").append(sdf.format(reportTime)).append("\n")
                .append("Period: ")
                .append(sdf.format(traceStartTime)).append(" - ")
                .append(sdf.format(reportTime))
                .append(" (").append(reportTime - traceStartTime).append(" ms)\n");
        sb.append("Total  queries: ").append(selectCount + updateCount).append("\n");
        sb.append("Select queries: ").append(selectCount).append("\n");
        sb.append("Update queries: ").append(updateCount).append("\n");
        sb.append("Top queries ordered by number of executions:\n");
        if (!enabled) {
            sb.append(" (tracing is currently disabled)");
        }
        queries.entrySet().stream()
                .sorted(Map.Entry.<String, AtomicLong>comparingByValue(
                        (x, y) -> (x.longValue() < y.longValue()) ? -1 : ((x.longValue() == y.longValue()) ? 0 : 1))
                        .reversed()).limit(REPORT_LIMIT)
                .forEach(e -> sb.append(formatQueryEntry(e)));

        String report = sb.toString();
        log.debug(report);
        return report;
    }

    private String formatQueryEntry(Map.Entry<String, AtomicLong> e) {
        return String.format("  %,8d %s%n", e.getValue().longValue(), e.getKey());
    }

    private void incrementQueryCount(String sql) {
        if (!enabled) {
            return;
        }
        AtomicLong count = queries.get(sql);
        if (count == null) {
            count = new AtomicLong(0);
            AtomicLong oldCount = queries.putIfAbsent(sql, count);  // try to initialize
            if (oldCount != null) {
                count = oldCount;   // someone else added the count
            }
        }
        count.incrementAndGet();
    }
}