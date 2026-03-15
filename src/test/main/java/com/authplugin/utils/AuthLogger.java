package com.authplugin.utils;

import org.bukkit.plugin.Plugin;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Custom logger for authentication events and system messages
 * Provides structured logging with timestamps and categorization
 */
public class AuthLogger {
    
    private final Logger bukkitLogger;
    private final Plugin plugin;
    private final DateTimeFormatter timeFormatter;
    private boolean debugMode;
    
    public AuthLogger(Plugin plugin) {
        this.plugin = plugin;
        this.bukkitLogger = plugin.getLogger();
        this.timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        this.debugMode = false; // Will be set from configuration later
    }
    
    /**
     * Set debug mode for verbose logging
     */
    public void setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
    }
    
    /**
     * Log an info message
     */
    public void info(String message) {
        bukkitLogger.info(message);
    }
    
    /**
     * Log a warning message
     */
    public void warning(String message) {
        bukkitLogger.warning(message);
    }
    
    /**
     * Log a severe error message
     */
    public void severe(String message) {
        bukkitLogger.severe(message);
    }
    
    /**
     * Log a debug message (only if debug mode is enabled)
     */
    public void debug(String message) {
        if (debugMode) {
            bukkitLogger.info("[DEBUG] " + message);
        }
    }
    
    /**
     * Log an authentication attempt (overloaded method)
     */
    public void logAuthAttempt(String username, String ipAddress, String action, boolean success) {
        logAuthAttempt(username, ipAddress, success, action);
    }
    
    /**
     * Log an authentication attempt
     */
    public void logAuthAttempt(String username, String ipAddress, boolean success, String action) {
        String timestamp = LocalDateTime.now().format(timeFormatter);
        String status = success ? "SUCCESS" : "FAILED";
        String logMessage = String.format("[AUTH] %s - %s attempt by %s from %s: %s", 
            timestamp, action, username, ipAddress, status);
        
        if (success) {
            info(logMessage);
        } else {
            warning(logMessage);
        }
    }
    
    /**
     * Log a security event
     */
    public void logSecurityEvent(String event, String details) {
        String timestamp = LocalDateTime.now().format(timeFormatter);
        String logMessage = String.format("[SECURITY] %s - %s: %s", timestamp, event, details);
        warning(logMessage);
    }
    
    /**
     * Log an error with exception details
     */
    public void logError(String component, Exception error) {
        String timestamp = LocalDateTime.now().format(timeFormatter);
        String logMessage = String.format("[ERROR] %s - %s: %s", timestamp, component, error.getMessage());
        bukkitLogger.log(Level.SEVERE, logMessage, error);
    }
    
    /**
     * Log performance metrics
     */
    public void logPerformance(String operation, long durationMs) {
        if (debugMode) {
            String timestamp = LocalDateTime.now().format(timeFormatter);
            String logMessage = String.format("[PERFORMANCE] %s - %s completed in %dms", 
                timestamp, operation, durationMs);
            info(logMessage);
        }
    }
    
    /**
     * Log admin action
     */
    public void logAdminAction(String adminName, String action, String target) {
        String timestamp = LocalDateTime.now().format(timeFormatter);
        String logMessage = String.format("[ADMIN] %s - %s performed %s on %s", 
            timestamp, adminName, action, target);
        info(logMessage);
    }
}