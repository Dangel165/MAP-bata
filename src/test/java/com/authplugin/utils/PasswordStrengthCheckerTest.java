package com.authplugin.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PasswordStrengthChecker.
 */
class PasswordStrengthCheckerTest {

    @Test
    void nullOrShortPasswordIsWeak() {
        assertEquals(PasswordStrengthChecker.Strength.WEAK, PasswordStrengthChecker.evaluate(null));
        assertEquals(PasswordStrengthChecker.Strength.WEAK, PasswordStrengthChecker.evaluate("abc"));
    }

    @Test
    void simplePasswordIsFairOrWeak() {
        PasswordStrengthChecker.Strength s = PasswordStrengthChecker.evaluate("password");
        assertTrue(s == PasswordStrengthChecker.Strength.WEAK || s == PasswordStrengthChecker.Strength.FAIR);
    }

    @Test
    void complexPasswordIsStrongOrBetter() {
        PasswordStrengthChecker.Strength s = PasswordStrengthChecker.evaluate("P@ssw0rd!");
        assertTrue(s == PasswordStrengthChecker.Strength.STRONG
                || s == PasswordStrengthChecker.Strength.VERY_STRONG);
    }

    @Test
    void feedbackNotEmpty() {
        assertFalse(PasswordStrengthChecker.getFeedback("abc123").isEmpty());
    }

    @Test
    void veryStrongPassword() {
        assertEquals(PasswordStrengthChecker.Strength.VERY_STRONG,
                PasswordStrengthChecker.evaluate("Tr0ub4dor&3_secure!"));
    }
}
