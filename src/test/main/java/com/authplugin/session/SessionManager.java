package com.authplugin.session;

import com.authplugin.config.ConfigurationManager;
import com.authplugin.models.AuthSession;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages player authentication sessions and auto-reauth functionality
 * Handles session creation, validation, cleanup, and timeout management
 */
public class SessionManager {
    
    private final Plugin plugin;
    private ConfigurationManager configManager;
    private final ConcurrentHashMap<UUID, AuthSession> activeSessions;
    private final ConcurrentHashMap<String, Long> ipLastLogin;
    
    public SessionManager(Plugin plugin, ConfigurationManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.activeSessions = new ConcurrentHashMap<>();
        this.ipLastLogin = new ConcurrentHashMap<>();
    }
    
    /**
     * Check if a player is authenticated
     */
    public boolean isAuthenticated(Player player) {
        AuthSession session = activeSessions.get(player.getUniqueId());
        return session != null && session.isAuthenticated();
    }
    
    /**
     * Create a new authentication session for a player
     */
    public void createSession(Player player) {
        AuthSession session = new AuthSession(
            player.getUniqueId(),
            player.getName(),
            getPlayerIP(player),
            System.currentTimeMillis(),
            System.currentTimeMillis(),
            true
        );
        
        activeSessions.put(player.getUniqueId(), session);
        ipLastLogin.put(getPlayerIP(player), System.currentTimeMillis());
    }
    
    /**
     * Invalidate a player's session
     */
    public void invalidateSession(Player player) {
        activeSessions.remove(player.getUniqueId());
    }
    
    /**
     * Check if a player can auto-reauth based on IP and time window
     */
    public boolean canAutoReauth(Player player, String ipAddress) {
        if (!configManager.isAutoReauthEnabled()) {
            return false;
        }
        
        Long lastLogin = ipLastLogin.get(ipAddress);
        if (lastLogin == null) {
            return false;
        }
        
        long timeSinceLastLogin = System.currentTimeMillis() - lastLogin;
        long autoReauthWindow = configManager.getAutoReauthTimeSeconds() * 1000L;
        
        return timeSinceLastLogin <= autoReauthWindow;
    }
    
    /**
     * Update session activity timestamp
     */
    public void updateActivity(Player player) {
        AuthSession session = activeSessions.get(player.getUniqueId());
        if (session != null) {
            session.updateActivity();
        }
    }
    
    /**
     * Get session for a player
     */
    public AuthSession getSession(Player player) {
        return activeSessions.get(player.getUniqueId());
    }
    
    /**
     * Clean up expired sessions
     */
    public int cleanupExpiredSessions() {
        long currentTime = System.currentTimeMillis();
        long sessionTimeout = configManager.getAuthTimeoutSeconds() * 1000L;
        
        // Use atomic integer to track cleaned sessions in lambda
        final int[] cleaned = {0};
        activeSessions.entrySet().removeIf(entry -> {
            AuthSession session = entry.getValue();
            if (currentTime - session.getLastActivity() > sessionTimeout) {
                cleaned[0]++;
                return true;
            }
            return false;
        });
        
        // Also clean up old IP login records
        long autoReauthWindow = configManager.getAutoReauthTimeSeconds() * 1000L;
        ipLastLogin.entrySet().removeIf(entry -> 
            currentTime - entry.getValue() > autoReauthWindow);
        
        return cleaned[0];
    }

    
    /**
     * Clear all active sessions (used on plugin disable)
     */
    public void clearAllSessions() {
        activeSessions.clear();
        ipLastLogin.clear();
    }
    
    /**
     * Get total number of active sessions
     */
    public int getActiveSessionCount() {
        return activeSessions.size();
    }
    
    /**
     * Update configuration reference
     */
    public void updateConfiguration(ConfigurationManager configManager) {
        this.configManager = configManager;
    }
    
    /**
     * Get player's IP address safely
     */
    private String getPlayerIP(Player player) {
        return player.getAddress() != null ? 
            player.getAddress().getAddress().getHostAddress() : "unknown";
    }
}