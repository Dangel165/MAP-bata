package com.authplugin.models;

import java.util.UUID;

/**
 * Represents an active authentication session for a player
 * Contains session data including timestamps and authentication status
 */
public class AuthSession {
    
    private final UUID playerId;
    private final String username;
    private final String ipAddress;
    private final long loginTime;
    private long lastActivity;
    private boolean authenticated;
    
    public AuthSession(UUID playerId, String username, String ipAddress, 
                      long loginTime, long lastActivity, boolean authenticated) {
        this.playerId = playerId;
        this.username = username;
        this.ipAddress = ipAddress;
        this.loginTime = loginTime;
        this.lastActivity = lastActivity;
        this.authenticated = authenticated;
    }
    
    public UUID getPlayerId() {
        return playerId;
    }
    
    public String getUsername() {
        return username;
    }
    
    public String getIpAddress() {
        return ipAddress;
    }
    
    public long getLoginTime() {
        return loginTime;
    }
    
    public long getLastActivity() {
        return lastActivity;
    }
    
    public boolean isAuthenticated() {
        return authenticated;
    }
    
    public void setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
    }
    
    public void updateActivity() {
        this.lastActivity = System.currentTimeMillis();
    }
    
    /**
     * Get session duration in milliseconds
     */
    public long getSessionDuration() {
        return System.currentTimeMillis() - loginTime;
    }
    
    /**
     * Get time since last activity in milliseconds
     */
    public long getTimeSinceLastActivity() {
        return System.currentTimeMillis() - lastActivity;
    }
    
    @Override
    public String toString() {
        return "AuthSession{" +
                "playerId=" + playerId +
                ", username='" + username + '\'' +
                ", ipAddress='" + ipAddress + '\'' +
                ", loginTime=" + loginTime +
                ", lastActivity=" + lastActivity +
                ", authenticated=" + authenticated +
                '}';
    }
}