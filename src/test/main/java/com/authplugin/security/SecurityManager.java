package com.authplugin.security;

import com.authplugin.config.ConfigurationManager;
import org.mindrot.jbcrypt.BCrypt;

import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Manages security operations including password hashing, validation, and rate limiting
 * Provides bcrypt-based password security and input validation
 */
public class SecurityManager {
    
    private final ConfigurationManager configManager;
    private final ConcurrentHashMap<String, RateLimitInfo> rateLimitMap;
    
    // Password validation patterns
    private static final Pattern VALID_USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{3,16}$");
    private static final Pattern SAFE_INPUT_PATTERN = Pattern.compile("^[a-zA-Z0-9_\\-\\.\\s]{1,100}$");
    
    // BCrypt work factor (cost)
    private static final int BCRYPT_ROUNDS = 12;
    
    public SecurityManager(ConfigurationManager configManager) {
        this.configManager = configManager;
        this.rateLimitMap = new ConcurrentHashMap<>();
    }
    
    /**
     * Hash a password using BCrypt
     */
    public String hashPassword(String plainPassword) {
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt(BCRYPT_ROUNDS));
    }
    
    /**
     * Verify a password against its hash
     */
    public boolean verifyPassword(String plainPassword, String hashedPassword) {
        try {
            return BCrypt.checkpw(plainPassword, hashedPassword);
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Check if an IP address is rate limited
     */
    public boolean isRateLimited(String ipAddress) {
        if (!configManager.isIpRateLimitingEnabled()) {
            return false;
        }
        
        RateLimitInfo info = rateLimitMap.get(ipAddress);
        if (info == null) {
            return false;
        }
        
        // If not yet locked out, check attempt count
        if (info.lockoutUntil == 0) {
            return info.failedAttempts >= configManager.getMaxFailedAttempts();
        }
        
        // Check if lockout has expired
        if (System.currentTimeMillis() > info.lockoutUntil) {
            rateLimitMap.remove(ipAddress);
            return false;
        }
        
        return true;
    }
    
    /**
     * Record a failed authentication attempt for rate limiting
     */
    public void recordFailedAttempt(String ipAddress) {
        if (!configManager.isIpRateLimitingEnabled()) {
            return;
        }
        
        RateLimitInfo info = rateLimitMap.computeIfAbsent(ipAddress, k -> new RateLimitInfo());
        info.failedAttempts++;
        
        if (info.failedAttempts >= configManager.getMaxFailedAttempts()) {
            info.lockoutUntil = System.currentTimeMillis() + 
                (configManager.getLockoutDurationSeconds() * 1000L);
        }
    }
    
    /**
     * Clear rate limiting for an IP address (on successful login)
     */
    public void clearRateLimit(String ipAddress) {
        rateLimitMap.remove(ipAddress);
    }
    
    /**
     * Validate input to prevent injection attacks
     */
    public boolean validateInput(String input, InputType type) {
        if (input == null || input.trim().isEmpty()) {
            return false;
        }
        
        switch (type) {
            case USERNAME:
                return VALID_USERNAME_PATTERN.matcher(input).matches();
            case PASSWORD:
                return input.length() >= configManager.getMinPasswordLength() && 
                       input.length() <= 128 && // Reasonable max length
                       !containsSqlInjectionPatterns(input);
            case SAFE_TEXT:
                return SAFE_INPUT_PATTERN.matcher(input).matches();
            default:
                return false;
        }
    }
    
    /**
     * Check for common SQL injection patterns
     */
    private boolean containsSqlInjectionPatterns(String input) {
        String lowerInput = input.toLowerCase();
        String[] sqlPatterns = {
            "select", "insert", "update", "delete", "drop", "create", "alter",
            "union", "script", "javascript", "vbscript", "onload", "onerror",
            "'", "\"", ";", "--", "/*", "*/", "xp_", "sp_"
        };
        
        for (String pattern : sqlPatterns) {
            if (lowerInput.contains(pattern)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Calculate password strength score (0-100)
     * Weak: < 30, Fair: 30-59, Good: 60-79, Strong: >= 80
     */
    public int calculatePasswordStrength(String password) {
        if (password == null || password.isEmpty()) {
            return 0;
        }
        
        int score = 0;
        
        // Length bonus
        if (password.length() >= 8) score += 20;
        if (password.length() >= 12) score += 10;
        if (password.length() >= 16) score += 10;
        
        // Character variety bonus
        if (password.matches(".*[a-z].*")) score += 10; // lowercase
        if (password.matches(".*[A-Z].*")) score += 20; // uppercase
        if (password.matches(".*[0-9].*")) score += 10; // numbers
        if (password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*")) score += 25; // special chars
        
        return Math.min(100, score);
    }
    
    /**
     * Get password strength description
     */
    public String getPasswordStrengthDescription(int strength) {
        if (strength < 30) return "Weak";
        if (strength < 60) return "Fair";
        if (strength < 80) return "Good";
        return "Strong";
    }
    
    /**
     * Validate input to prevent injection attacks (overloaded method)
     */
    public boolean validateInput(String input) {
        return validateInput(input, InputType.SAFE_TEXT);
    }
    
    /**
     * Apply rate limiting after a failed attempt
     */
    public void applyRateLimit(String ipAddress) {
        recordFailedAttempt(ipAddress);
    }
    
    /**
     * Clean up expired rate limit entries
     */
    public void cleanupExpiredRateLimits() {
        long currentTime = System.currentTimeMillis();
        rateLimitMap.entrySet().removeIf(entry -> 
            currentTime > entry.getValue().lockoutUntil);
    }
    
    /**
     * Input validation types
     */
    public enum InputType {
        USERNAME,
        PASSWORD,
        SAFE_TEXT
    }
    
    /**
     * Rate limiting information for an IP address
     */
    private static class RateLimitInfo {
        int failedAttempts = 0;
        long lockoutUntil = 0;
    }
}