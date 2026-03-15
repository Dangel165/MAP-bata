package com.authplugin.database;

import com.authplugin.models.PlayerData;

import java.util.concurrent.CompletableFuture;
import java.util.List;

/**
 * Interface for database operations supporting both SQLite and MySQL backends
 * All operations are asynchronous to prevent server lag
 */
public interface DatabaseManager {
    
    /**
     * Initialize the database schema and connections
     * @return CompletableFuture that completes when initialization is done
     */
    CompletableFuture<Void> initializeDatabase();
    
    /**
     * Register a new player account
     * @param username The player's username
     * @param passwordHash The bcrypt hashed password
     * @return CompletableFuture<Boolean> true if registration successful
     */
    CompletableFuture<Boolean> registerPlayer(String username, String passwordHash);
    
    /**
     * Register a new player account with IP address
     * @param username The player's username
     * @param passwordHash The bcrypt hashed password
     * @param ipAddress The player's IP address
     * @return CompletableFuture<Boolean> true if registration successful
     */
    CompletableFuture<Boolean> registerPlayer(String username, String passwordHash, String ipAddress);
    
    /**
     * Get player data by username
     * @param username The player's username
     * @return CompletableFuture<PlayerData> player data or null if not found
     */
    CompletableFuture<PlayerData> getPlayerData(String username);
    
    /**
     * Update a player's password
     * @param username The player's username
     * @param newPasswordHash The new bcrypt hashed password
     * @return CompletableFuture<Boolean> true if update successful
     */
    CompletableFuture<Boolean> updatePassword(String username, String newPasswordHash);
    
    /**
     * Delete a player account
     * @param username The player's username
     * @return CompletableFuture<Boolean> true if deletion successful
     */
    CompletableFuture<Boolean> deletePlayer(String username);
    
    /**
     * Update player's last login time and IP address
     * @param username The player's username
     * @param ipAddress The player's IP address
     * @return CompletableFuture<Void> completes when update is done
     */
    CompletableFuture<Void> updateLastLogin(String username, String ipAddress);
    
    /**
     * Update failed login attempts for a player
     * @param username The player's username
     * @param failedAttempts Number of failed attempts
     * @param lockoutUntil Timestamp when lockout expires (0 for no lockout)
     * @return CompletableFuture<Void> completes when update is done
     */
    CompletableFuture<Void> updateFailedAttempts(String username, int failedAttempts, long lockoutUntil);
    
    /**
     * Check if a username already exists
     * @param username The username to check
     * @return CompletableFuture<Boolean> true if username exists
     */
    CompletableFuture<Boolean> usernameExists(String username);
    
    /**
     * Check if a player exists (alias for usernameExists)
     * @param username The username to check
     * @return CompletableFuture<Boolean> true if player exists
     */
    CompletableFuture<Boolean> playerExists(String username);
    
    /**
     * Reset failed attempts for a player
     * @param username The player's username
     * @return CompletableFuture<Void> completes when reset is done
     */
    CompletableFuture<Void> resetFailedAttempts(String username);
    
    /**
     * Increment failed attempts for a player
     * @param username The player's username
     * @return CompletableFuture<Void> completes when increment is done
     */
    CompletableFuture<Void> incrementFailedAttempts(String username);
    
    /**
     * Lock an account for a specified duration
     * @param username The player's username
     * @param lockoutDuration Duration in milliseconds
     * @return CompletableFuture<Void> completes when lock is applied
     */
    CompletableFuture<Void> lockAccount(String username, long lockoutDuration);
    
    /**
     * Get total number of registered players
     * @return CompletableFuture<Integer> total player count
     */
    CompletableFuture<Integer> getTotalPlayers();
    
    /**
     * Get all usernames similar to the given username
     * @param username The username to compare against
     * @param threshold Similarity threshold (0.0 to 1.0)
     * @return CompletableFuture<List<String>> list of similar usernames
     */
    CompletableFuture<List<String>> getSimilarUsernames(String username, double threshold);
    
    /**
     * Log an authentication attempt
     * @param username The player's username
     * @param ipAddress The player's IP address
     * @param action The action performed (LOGIN, REGISTER, FAILED_LOGIN)
     * @param success Whether the action was successful
     * @return CompletableFuture<Void> completes when log is written
     */
    CompletableFuture<Void> logAuthAttempt(String username, String ipAddress, String action, boolean success);
    
    /**
     * Get authentication statistics
     * @return CompletableFuture<AuthStats> statistics about authentication attempts
     */
    CompletableFuture<AuthStats> getAuthStats();
    
    /**
     * Perform database backup
     * @throws Exception if backup fails
     */
    void performBackup() throws Exception;
    
    /**
     * Close database connections and cleanup resources
     */
    void close();
    
    /**
     * Statistics data class
     */
    class AuthStats {
        public final int totalRegistrations;
        public final int totalLogins;
        public final int failedAttempts;
        public final int uniqueIPs;
        
        public AuthStats(int totalRegistrations, int totalLogins, int failedAttempts, int uniqueIPs) {
            this.totalRegistrations = totalRegistrations;
            this.totalLogins = totalLogins;
            this.failedAttempts = failedAttempts;
            this.uniqueIPs = uniqueIPs;
        }
    }
}