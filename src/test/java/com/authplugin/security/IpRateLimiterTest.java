package com.authplugin.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for IpRateLimiter.
 */
class IpRateLimiterTest {

    private IpRateLimiter limiter;

    @BeforeEach
    void setUp() {
        // 3 attempts per 5-second window
        limiter = new IpRateLimiter(3, 5_000L);
    }

    @Test
    void notBlockedInitially() {
        assertFalse(limiter.isBlocked("1.2.3.4"));
    }

    @Test
    void blockedAfterThreshold() {
        String ip = "1.2.3.4";
        limiter.recordAttempt(ip);
        limiter.recordAttempt(ip);
        boolean blocked = limiter.recordAttempt(ip); // 3rd attempt triggers block
        assertTrue(blocked);
        assertTrue(limiter.isBlocked(ip));
    }

    @Test
    void unblockClearsState() {
        String ip = "5.6.7.8";
        limiter.recordAttempt(ip);
        limiter.recordAttempt(ip);
        limiter.recordAttempt(ip);
        assertTrue(limiter.isBlocked(ip));

        limiter.unblock(ip);
        assertFalse(limiter.isBlocked(ip));
    }

    @Test
    void differentIpsAreIndependent() {
        String ip1 = "10.0.0.1";
        String ip2 = "10.0.0.2";
        limiter.recordAttempt(ip1);
        limiter.recordAttempt(ip1);
        limiter.recordAttempt(ip1);
        assertTrue(limiter.isBlocked(ip1));
        assertFalse(limiter.isBlocked(ip2));
    }

    @Test
    void blockRemainingSecondsPositiveWhenBlocked() {
        String ip = "9.9.9.9";
        limiter.recordAttempt(ip);
        limiter.recordAttempt(ip);
        limiter.recordAttempt(ip);
        assertTrue(limiter.getBlockRemainingSeconds(ip) > 0);
    }

    @Test
    void cleanupDoesNotThrow() {
        limiter.recordAttempt("1.1.1.1");
        assertDoesNotThrow(() -> limiter.cleanup());
    }
}
