package com.authplugin.security;

import com.authplugin.config.ConfigurationManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SecurityManager
 * Tests password hashing, validation, and rate limiting functionality
 */
@ExtendWith(MockitoExtension.class)
class SecurityManagerTest {

    @Mock
    private ConfigurationManager configManager;
    
    private SecurityManager securityManager;
    
    @BeforeEach
    void setUp() {
        // Set up default mock behavior with lenient stubbing
        lenient().when(configManager.getMinPasswordLength()).thenReturn(6);
        lenient().when(configManager.getMaxFailedAttempts()).thenReturn(3);
        lenient().when(configManager.getLockoutDurationSeconds()).thenReturn(300);
        lenient().when(configManager.isIpRateLimitingEnabled()).thenReturn(true);
        
        securityManager = new SecurityManager(configManager);
    }
    
    @Test
    void shouldHashPasswordWithBcrypt() {
        // Given
        String plainPassword = "testPassword123";
        
        // When
        String hashedPassword = securityManager.hashPassword(plainPassword);
        
        // Then
        assertThat(hashedPassword).isNotNull();
        assertThat(hashedPassword).isNotEqualTo(plainPassword);
        assertThat(hashedPassword).startsWith("$2a$12$"); // BCrypt format with 12 rounds
    }
    
    @Test
    void shouldVerifyCorrectPassword() {
        // Given
        String plainPassword = "testPassword123";
        String hashedPassword = securityManager.hashPassword(plainPassword);
        
        // When
        boolean isValid = securityManager.verifyPassword(plainPassword, hashedPassword);
        
        // Then
        assertThat(isValid).isTrue();
    }
    
    @Test
    void shouldRejectIncorrectPassword() {
        // Given
        String plainPassword = "testPassword123";
        String wrongPassword = "wrongPassword456";
        String hashedPassword = securityManager.hashPassword(plainPassword);
        
        // When
        boolean isValid = securityManager.verifyPassword(wrongPassword, hashedPassword);
        
        // Then
        assertThat(isValid).isFalse();
    }
    
    @Test
    void shouldHandleInvalidHashGracefully() {
        // Given
        String plainPassword = "testPassword123";
        String invalidHash = "invalid_hash";
        
        // When
        boolean isValid = securityManager.verifyPassword(plainPassword, invalidHash);
        
        // Then
        assertThat(isValid).isFalse();
    }
    
    @Test
    void shouldValidateUsernameCorrectly() {
        // Given
        String validUsername = "TestUser123";
        String invalidUsername = "Test@User!";
        String tooShortUsername = "ab";
        String tooLongUsername = "ThisUsernameIsTooLongForValidation";
        
        // When & Then
        assertThat(securityManager.validateInput(validUsername, SecurityManager.InputType.USERNAME)).isTrue();
        assertThat(securityManager.validateInput(invalidUsername, SecurityManager.InputType.USERNAME)).isFalse();
        assertThat(securityManager.validateInput(tooShortUsername, SecurityManager.InputType.USERNAME)).isFalse();
        assertThat(securityManager.validateInput(tooLongUsername, SecurityManager.InputType.USERNAME)).isFalse();
    }
    
    @Test
    void shouldValidatePasswordLength() {
        // Given
        String validPassword = "password123";
        String tooShortPassword = "pass";
        
        // When & Then
        assertThat(securityManager.validateInput(validPassword, SecurityManager.InputType.PASSWORD)).isTrue();
        assertThat(securityManager.validateInput(tooShortPassword, SecurityManager.InputType.PASSWORD)).isFalse();
    }
    
    @Test
    void shouldDetectSqlInjectionInPassword() {
        // Given
        String maliciousPassword = "password'; DROP TABLE users; --";
        String safePassword = "safePassword123";
        
        // When & Then
        assertThat(securityManager.validateInput(maliciousPassword, SecurityManager.InputType.PASSWORD)).isFalse();
        assertThat(securityManager.validateInput(safePassword, SecurityManager.InputType.PASSWORD)).isTrue();
    }
    
    @Test
    void shouldNotRateLimitWhenDisabled() {
        // Given
        when(configManager.isIpRateLimitingEnabled()).thenReturn(false);
        SecurityManager disabledRateLimitManager = new SecurityManager(configManager);
        String ipAddress = "192.168.1.1";
        
        // When
        disabledRateLimitManager.recordFailedAttempt(ipAddress);
        disabledRateLimitManager.recordFailedAttempt(ipAddress);
        disabledRateLimitManager.recordFailedAttempt(ipAddress);
        disabledRateLimitManager.recordFailedAttempt(ipAddress);
        
        // Then
        assertThat(disabledRateLimitManager.isRateLimited(ipAddress)).isFalse();
    }
    
    @Test
    void shouldRateLimitAfterMaxFailedAttempts() {
        // Given
        String ipAddress = "192.168.1.1";
        
        // When
        securityManager.recordFailedAttempt(ipAddress);
        securityManager.recordFailedAttempt(ipAddress);
        assertThat(securityManager.isRateLimited(ipAddress)).isFalse();
        
        securityManager.recordFailedAttempt(ipAddress);
        
        // Then
        assertThat(securityManager.isRateLimited(ipAddress)).isTrue();
    }
    
    @Test
    void shouldClearRateLimitOnSuccessfulLogin() {
        // Given
        String ipAddress = "192.168.1.1";
        
        // When
        securityManager.recordFailedAttempt(ipAddress);
        securityManager.recordFailedAttempt(ipAddress);
        securityManager.recordFailedAttempt(ipAddress);
        assertThat(securityManager.isRateLimited(ipAddress)).isTrue();
        
        securityManager.clearRateLimit(ipAddress);
        
        // Then
        assertThat(securityManager.isRateLimited(ipAddress)).isFalse();
    }
    
    @Test
    void shouldCalculatePasswordStrength() {
        // Given
        String weakPassword = "pass";
        String fairPassword = "password123";
        String goodPassword = "Password123";
        String strongPassword = "P@ssw0rd123!";
        
        // When & Then
        assertThat(securityManager.calculatePasswordStrength(weakPassword)).isLessThan(30);
        assertThat(securityManager.calculatePasswordStrength(fairPassword)).isBetween(30, 59);
        assertThat(securityManager.calculatePasswordStrength(goodPassword)).isBetween(60, 79);
        assertThat(securityManager.calculatePasswordStrength(strongPassword)).isGreaterThanOrEqualTo(80);
    }
    
    @Test
    void shouldProvidePasswordStrengthDescriptions() {
        // When & Then
        assertThat(securityManager.getPasswordStrengthDescription(20)).isEqualTo("Weak");
        assertThat(securityManager.getPasswordStrengthDescription(45)).isEqualTo("Fair");
        assertThat(securityManager.getPasswordStrengthDescription(70)).isEqualTo("Good");
        assertThat(securityManager.getPasswordStrengthDescription(90)).isEqualTo("Strong");
    }
    
    @Test
    void shouldHandleNullAndEmptyInputs() {
        // When & Then
        assertThat(securityManager.validateInput(null, SecurityManager.InputType.USERNAME)).isFalse();
        assertThat(securityManager.validateInput("", SecurityManager.InputType.USERNAME)).isFalse();
        assertThat(securityManager.validateInput("   ", SecurityManager.InputType.USERNAME)).isFalse();
        
        assertThat(securityManager.calculatePasswordStrength(null)).isEqualTo(0);
        assertThat(securityManager.calculatePasswordStrength("")).isEqualTo(0);
    }
    
    @Test
    void shouldCleanupExpiredRateLimits() {
        // Given
        String ipAddress = "192.168.1.1";
        when(configManager.getLockoutDurationSeconds()).thenReturn(1); // 1 second lockout
        SecurityManager shortLockoutManager = new SecurityManager(configManager);
        
        // When
        shortLockoutManager.recordFailedAttempt(ipAddress);
        shortLockoutManager.recordFailedAttempt(ipAddress);
        shortLockoutManager.recordFailedAttempt(ipAddress);
        assertThat(shortLockoutManager.isRateLimited(ipAddress)).isTrue();
        
        // Wait for lockout to expire
        try {
            Thread.sleep(1100); // Wait slightly longer than lockout duration
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        shortLockoutManager.cleanupExpiredRateLimits();
        
        // Then
        assertThat(shortLockoutManager.isRateLimited(ipAddress)).isFalse();
    }
}