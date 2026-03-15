package com.authplugin.models;

import java.sql.Timestamp;
import java.util.Date;

/**
 * Data model representing a player's authentication information
 * Contains all database fields for a registered player
 */
public class PlayerData {
    
    private final String username;
    private final String passwordHash;
    private final Date registrationDate;
    private final Date lastLogin;
    private final String lastIpAddress;
    private final int failedAttempts;
    private final long lockoutUntil;
    
    public PlayerData(String username, String passwordHash, Timestamp registrationDate, 
                     Timestamp lastLogin, String lastIpAddress, int failedAttempts, long lockoutUntil) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.registrationDate = registrationDate != null ? new Date(registrationDate.getTime()) : null;
        this.lastLogin = lastLogin != null ? new Date(lastLogin.getTime()) : null;
        this.lastIpAddress = lastIpAddress;
        this.failedAttempts = failedAttempts;
        this.lockoutUntil = lockoutUntil;
    }
    
    public String getUsername() {
        return username;
    }
    
    public String getPasswordHash() {
        return passwordHash;
    }
    
    public Date getRegistrationDate() {
        return registrationDate;
    }
    
    public Date getLastLogin() {
        return lastLogin;
    }
    
    public String getLastIpAddress() {
        return lastIpAddress;
    }
    
    public int getFailedAttempts() {
        return failedAttempts;
    }
    
    public long getLockoutUntil() {
        return lockoutUntil;
    }
    
    /**
     * Check if the player is currently locked out
     */
    public boolean isLockedOut() {
        return lockoutUntil > 0 && System.currentTimeMillis() < lockoutUntil;
    }
    
    /**
     * Check if the player account is locked (alias for isLockedOut)
     */
    public boolean isLocked() {
        return isLockedOut();
    }
    
    /**
     * Get remaining lockout time in seconds
     */
    public long getRemainingLockoutSeconds() {
        if (!isLockedOut()) {
            return 0;
        }
        return (lockoutUntil - System.currentTimeMillis()) / 1000;
    }
    
    /**
     * Get remaining lockout time in milliseconds
     */
    public long getLockoutRemaining() {
        if (!isLockedOut()) {
            return 0;
        }
        return lockoutUntil - System.currentTimeMillis();
    }
    
    @Override
    public String toString() {
        return "PlayerData{" +
                "username='" + username + '\'' +
                ", registrationDate=" + registrationDate +
                ", lastLogin=" + lastLogin +
                ", lastIpAddress='" + lastIpAddress + '\'' +
                ", failedAttempts=" + failedAttempts +
                ", isLockedOut=" + isLockedOut() +
                '}';
    }
}