package com.authplugin.security;

import com.authplugin.config.ConfigurationManager;
import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.NumericChars;
import net.jqwik.api.constraints.StringLength;
import net.jqwik.api.lifecycle.BeforeTry;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for SecurityManager
 * Tests universal properties that should hold for all valid inputs
 */
class SecurityManagerPropertyTest {

    @Mock
    private ConfigurationManager configManager;
    
    private SecurityManager securityManager;
    
    @BeforeTry
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Set up default mock behavior
        when(configManager.getMinPasswordLength()).thenReturn(6);
        when(configManager.getMaxFailedAttempts()).thenReturn(3);
        when(configManager.getLockoutDurationSeconds()).thenReturn(300);
        when(configManager.isIpRateLimitingEnabled()).thenReturn(true);
        
        securityManager = new SecurityManager(configManager);
    }
    
    /**
     * Property 1: Password Hashing Consistency
     * For any password provided during registration, the stored password hash should be 
     * a valid bcrypt hash that can verify the original password.
     * 
     * Validates: Requirements 1.3, 2.5
     */
    @Property
    @Tag("Feature: minecraft-auth-plugin, Property 1: Password Hashing Consistency")
    void passwordHashingConsistency(@ForAll @StringLength(min = 1, max = 100) String password) {
        // When
        String hashedPassword = securityManager.hashPassword(password);
        
        // Then
        assertThat(hashedPassword).isNotNull();
        assertThat(hashedPassword).isNotEqualTo(password);
        assertThat(hashedPassword).startsWith("$2a$12$"); // BCrypt format
        
        // The hash should verify the original password
        assertThat(securityManager.verifyPassword(password, hashedPassword)).isTrue();
        
        // Different passwords should not verify against this hash
        if (password.length() > 1) {
            String differentPassword = password.substring(1) + "x";
            assertThat(securityManager.verifyPassword(differentPassword, hashedPassword)).isFalse();
        }
    }
    
    /**
     * Property 24: Input Validation Security
     * For any user input received by the system, validation should prevent 
     * injection attacks and malicious input processing.
     * 
     * Validates: Requirements 8.6
     */
    @Property
    @Tag("Feature: minecraft-auth-plugin, Property 24: Input Validation Security")
    void inputValidationSecurity(@ForAll String input) {
        // When validating as password
        boolean passwordValid = securityManager.validateInput(input, SecurityManager.InputType.PASSWORD);
        
        // Then - if input contains SQL injection patterns, it should be rejected
        if (input != null && containsSqlInjectionPatterns(input.toLowerCase())) {
            assertThat(passwordValid).isFalse();
        }
        
        // When validating as username
        boolean usernameValid = securityManager.validateInput(input, SecurityManager.InputType.USERNAME);
        
        // Then - username should only allow alphanumeric and underscore
        if (input != null && !input.matches("^[a-zA-Z0-9_]{3,16}$")) {
            assertThat(usernameValid).isFalse();
        }
    }
    
    @Property
    void passwordStrengthIsConsistent(@ForAll @StringLength(max = 50) String password) {
        // When
        int strength1 = securityManager.calculatePasswordStrength(password);
        int strength2 = securityManager.calculatePasswordStrength(password);
        
        // Then - strength calculation should be deterministic
        assertThat(strength1).isEqualTo(strength2);
        assertThat(strength1).isBetween(0, 100);
        
        // Strength description should be consistent
        String description1 = securityManager.getPasswordStrengthDescription(strength1);
        String description2 = securityManager.getPasswordStrengthDescription(strength1);
        assertThat(description1).isEqualTo(description2);
    }
    
    @Property
    void rateLimitingIsConsistent(@ForAll @AlphaChars @NumericChars @StringLength(min = 7, max = 15) String ipAddress) {
        // Assume valid IP format for this test
        String validIp = "192.168.1." + Math.abs(ipAddress.hashCode() % 255);
        
        // When - recording failed attempts up to the limit
        for (int i = 0; i < 2; i++) {
            securityManager.recordFailedAttempt(validIp);
            assertThat(securityManager.isRateLimited(validIp)).isFalse();
        }
        
        // When - exceeding the limit
        securityManager.recordFailedAttempt(validIp);
        
        // Then - should be rate limited
        assertThat(securityManager.isRateLimited(validIp)).isTrue();
        
        // When - clearing rate limit
        securityManager.clearRateLimit(validIp);
        
        // Then - should no longer be rate limited
        assertThat(securityManager.isRateLimited(validIp)).isFalse();
    }
    
    @Property
    void validUsernamesPassValidation(@ForAll("validUsernames") String username) {
        // When
        boolean isValid = securityManager.validateInput(username, SecurityManager.InputType.USERNAME);
        
        // Then
        assertThat(isValid).isTrue();
    }
    
    @Property
    void validPasswordsPassValidation(@ForAll("validPasswords") String password) {
        // When
        boolean isValid = securityManager.validateInput(password, SecurityManager.InputType.PASSWORD);
        
        // Then
        assertThat(isValid).isTrue();
    }
    
    @Property
    void hashingIsDeterministicForSameInput(@ForAll @StringLength(min = 1, max = 50) String password) {
        // When
        String hash1 = securityManager.hashPassword(password);
        String hash2 = securityManager.hashPassword(password);
        
        // Then - hashes should be different (due to salt) but both should verify
        assertThat(hash1).isNotEqualTo(hash2); // BCrypt uses random salt
        assertThat(securityManager.verifyPassword(password, hash1)).isTrue();
        assertThat(securityManager.verifyPassword(password, hash2)).isTrue();
    }
    
    @Property
    void passwordVerificationIsSymmetric(@ForAll @StringLength(min = 1, max = 50) String password) {
        // Given
        String hash = securityManager.hashPassword(password);
        
        // When & Then - verification should be consistent
        boolean result1 = securityManager.verifyPassword(password, hash);
        boolean result2 = securityManager.verifyPassword(password, hash);
        
        assertThat(result1).isEqualTo(result2);
        assertThat(result1).isTrue();
    }
    
    // Data providers for property tests
    
    @Provide
    Arbitrary<String> validUsernames() {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .withCharRange('A', 'Z')
            .withCharRange('0', '9')
            .withChars('_')
            .ofMinLength(3)
            .ofMaxLength(16);
    }
    
    @Provide
    Arbitrary<String> validPasswords() {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .withCharRange('A', 'Z')
            .withCharRange('0', '9')
            .withChars('!', '@', '#', '$', '%', '^', '&', '*', '(', ')', '-', '_', '+', '=')
            .ofMinLength(6)
            .ofMaxLength(50)
            .filter(s -> !containsSqlInjectionPatterns(s.toLowerCase()));
    }
    
    // Helper method to check for SQL injection patterns
    private boolean containsSqlInjectionPatterns(String input) {
        if (input == null) return false;
        
        String[] sqlPatterns = {
            "select", "insert", "update", "delete", "drop", "create", "alter",
            "union", "script", "javascript", "vbscript", "onload", "onerror",
            "'", "\"", ";", "--", "/*", "*/", "xp_", "sp_"
        };
        
        for (String pattern : sqlPatterns) {
            if (input.contains(pattern)) {
                return true;
            }
        }
        
        return false;
    }
}