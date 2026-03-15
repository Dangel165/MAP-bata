package com.authplugin.utils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lightweight performance monitor that tracks operation counts and durations.
 * Thread-safe; designed for minimal overhead.
 */
public class PerformanceMonitor {

    private final Map<String, AtomicLong> counts = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> totalMs = new ConcurrentHashMap<>();

    /**
     * Record the duration of a completed operation.
     * @param operation name (e.g. "db.getPlayerData")
     * @param durationMs elapsed milliseconds
     */
    public void record(String operation, long durationMs) {
        counts.computeIfAbsent(operation, k -> new AtomicLong()).incrementAndGet();
        totalMs.computeIfAbsent(operation, k -> new AtomicLong()).addAndGet(durationMs);
    }

    /** Convenience: record using a start timestamp. */
    public void recordSince(String operation, long startMs) {
        record(operation, System.currentTimeMillis() - startMs);
    }

    /** Get total invocation count for an operation. */
    public long getCount(String operation) {
        AtomicLong c = counts.get(operation);
        return c == null ? 0 : c.get();
    }

    /** Get average duration in ms, or 0 if no data. */
    public double getAverageMs(String operation) {
        long c = getCount(operation);
        if (c == 0) return 0;
        AtomicLong total = totalMs.get(operation);
        return total == null ? 0 : (double) total.get() / c;
    }

    /** Build a human-readable summary of all tracked operations. */
    public String getSummary() {
        if (counts.isEmpty()) return "No performance data collected.";
        StringBuilder sb = new StringBuilder("Performance Summary:\n");
        counts.forEach((op, cnt) ->
            sb.append(String.format("  %-40s calls=%-6d avg=%.1fms%n",
                    op, cnt.get(), getAverageMs(op))));
        return sb.toString();
    }

    /** Reset all metrics. */
    public void reset() {
        counts.clear();
        totalMs.clear();
    }
}
