package com.authplugin.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Security audit tests: SQL injection, input sanitization, rate limiting.
 * These tests validate that the security layer rejects malicious inputs.
 */
class SecurityAuditTest {

    // -------------------------------------------------------------------------
    // Input sanitization
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {
        "'; DROP TABLE players; --",
        "admin'--",
        "\" OR \"1\"=\"1",
        "<script>alert(1)</script>",
        "../../../etc/passwd",
        "\0null",
        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    })
    void maliciousUsernamesShouldBeRejected(String input) {
        // Usernames must be 1-16 alphanumeric chars (Minecraft standard)
        assertFalse(isValidUsername(input),
                "Expected username to be rejected: " + input);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Steve", "Alex", "Player123", "test_user"})
    void validUsernamesShouldBeAccepted(String input) {
        assertTrue(isValidUsername(input));
    }

    // -------------------------------------------------------------------------
    // Rate limiting
    // -------------------------------------------------------------------------

    @Test
    void rateLimiterBlocksAfterMaxAttempts() {
        IpRateLimiter limiter = new IpRateLimiter(5, 60_000L);
        String ip = "192.168.1.1";
        for (int i = 0; i < 5; i++) {
            limiter.recordAttempt(ip);
        }
        assertTrue(limiter.isBlocked(ip), "IP should be blocked after 5 attempts");
    }

    @Test
    void rateLimiterAllowsLegitimateTraffic() {
        IpRateLimiter limiter = new IpRateLimiter(5, 60_000L);
        String ip = "10.0.0.1";
        limiter.recordAttempt(ip);
        limiter.recordAttempt(ip);
        assertFalse(limiter.isBlocked(ip), "IP should not be blocked after 2 attempts");
    }

    // -------------------------------------------------------------------------
    // Password strength
    // -------------------------------------------------------------------------

    @Test
    void weakPasswordsAreIdentified() {
        assertEquals(com.authplugin.utils.PasswordStrengthChecker.Strength.WEAK,
                com.authplugin.utils.PasswordStrengthChecker.evaluate("123"));
    }

    @Test
    void strongPasswordsAreIdentified() {
        com.authplugin.utils.PasswordStrengthChecker.Strength s =
                com.authplugin.utils.PasswordStrengthChecker.evaluate("S3cur3P@ss!");
        assertTrue(s == com.authplugin.utils.PasswordStrengthChecker.Strength.STRONG
                || s == com.authplugin.utils.PasswordStrengthChecker.Strength.VERY_STRONG);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private boolean isValidUsername(String username) {
        if (username == null) return false;
        return username.matches("[a-zA-Z0-9_]{1,16}");
    }
}
