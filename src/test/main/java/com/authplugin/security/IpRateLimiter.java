package com.authplugin.security;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IP-based rate limiter for authentication endpoints.
 * Tracks attempt timestamps per IP and blocks IPs that exceed the threshold.
 */
public class IpRateLimiter {

    private final int maxAttempts;
    private final long windowMs;

    // IP -> list of attempt timestamps
    private final Map<String, List<Long>> attempts = new ConcurrentHashMap<>();
    // IP -> blocked-until timestamp
    private final Map<String, Long> blockedUntil = new ConcurrentHashMap<>();

    /**
     * @param maxAttempts max attempts allowed within the window
     * @param windowMs    sliding window in milliseconds
     */
    public IpRateLimiter(int maxAttempts, long windowMs) {
        this.maxAttempts = maxAttempts;
        this.windowMs = windowMs;
    }

    /**
     * Record an attempt from the given IP.
     * @return true if the IP is now rate-limited (should be blocked)
     */
    public boolean recordAttempt(String ip) {
        long now = System.currentTimeMillis();

        // Prune old entries
        List<Long> times = attempts.computeIfAbsent(ip, k -> new ArrayList<>());
        times.removeIf(t -> now - t > windowMs);
        times.add(now);

        if (times.size() >= maxAttempts) {
            blockedUntil.put(ip, now + windowMs);
            return true;
        }
        return false;
    }

    /**
     * Check if an IP is currently rate-limited without recording an attempt.
     */
    public boolean isBlocked(String ip) {
        Long until = blockedUntil.get(ip);
        if (until == null) return false;
        if (System.currentTimeMillis() >= until) {
            blockedUntil.remove(ip);
            attempts.remove(ip);
            return false;
        }
        return true;
    }

    /** Remaining block time in seconds, or 0 if not blocked. */
    public long getBlockRemainingSeconds(String ip) {
        Long until = blockedUntil.get(ip);
        if (until == null) return 0;
        long remaining = (until - System.currentTimeMillis()) / 1000;
        return Math.max(0, remaining);
    }

    /** Manually unblock an IP (e.g., admin action). */
    public void unblock(String ip) {
        blockedUntil.remove(ip);
        attempts.remove(ip);
    }

    /** Purge expired entries to free memory. Call periodically. */
    public void cleanup() {
        long now = System.currentTimeMillis();
        attempts.entrySet().removeIf(e -> {
            e.getValue().removeIf(t -> now - t > windowMs);
            return e.getValue().isEmpty();
        });
        blockedUntil.entrySet().removeIf(e -> now >= e.getValue());
    }
}
