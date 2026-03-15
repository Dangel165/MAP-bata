package com.authplugin.database;

import com.authplugin.models.PlayerData;
import com.authplugin.utils.AuthLogger;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * SQLite implementation of DatabaseManager
 * Provides local file-based database storage with connection pooling
 */
public class SQLiteManager implements DatabaseManager {
    
    private final Plugin plugin;
    private final AuthLogger logger;
    private final File databaseFile;
    private HikariDataSource dataSource;
    private final DatabaseConnectionRecovery connectionRecovery;
    
    // SQL statements
    private static final String CREATE_PLAYERS_TABLE = """
        CREATE TABLE IF NOT EXISTS players (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            username VARCHAR(16) NOT NULL UNIQUE,
            password_hash VARCHAR(60) NOT NULL,
            registration_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            last_login TIMESTAMP,
            last_ip VARCHAR(45),
            failed_attempts INTEGER DEFAULT 0,
            lockout_until TIMESTAMP NULL
        )
        """;
    
    private static final String CREATE_AUTH_LOGS_TABLE = """
        CREATE TABLE IF NOT EXISTS auth_logs (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            username VARCHAR(16),
            ip_address VARCHAR(45),
            action VARCHAR(20),
            timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            success BOOLEAN
        )
        """;
    
    private static final String CREATE_USERNAME_INDEX = 
        "CREATE INDEX IF NOT EXISTS idx_username ON players(username)";
    
    private static final String CREATE_IP_INDEX = 
        "CREATE INDEX IF NOT EXISTS idx_last_ip ON players(last_ip)";
    
    private static final String CREATE_LOGS_TIMESTAMP_INDEX = 
        "CREATE INDEX IF NOT EXISTS idx_timestamp ON auth_logs(timestamp)";
    
    private static final String CREATE_LOGS_USERNAME_INDEX = 
        "CREATE INDEX IF NOT EXISTS idx_logs_username ON auth_logs(username)";
    
    public SQLiteManager(Plugin plugin, AuthLogger logger) {
        this.plugin = plugin;
        this.logger = logger;
        this.databaseFile = new File(plugin.getDataFolder(), "authdata.db");
        this.connectionRecovery = new DatabaseConnectionRecovery(logger);
    }
    
    @Override
    public CompletableFuture<Void> initializeDatabase() {
        return CompletableFuture.runAsync(() -> {
            try {
                // Create data folder if it doesn't exist
                if (!plugin.getDataFolder().exists()) {
                    plugin.getDataFolder().mkdirs();
                }
                
                // Explicitly load SQLite driver (required for Paper's isolated classloader)
                try {
                    Class.forName("org.sqlite.JDBC");
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException("SQLite JDBC driver not found in JAR", e);
                }
                
                // Initialize HikariCP connection pool
                HikariConfig config = new HikariConfig();
                config.setDriverClassName("org.sqlite.JDBC");
                config.setJdbcUrl("jdbc:sqlite:" + databaseFile.getAbsolutePath());
                config.setMaximumPoolSize(5);
                config.setMinimumIdle(1);
                config.setConnectionTimeout(30000);
                config.setIdleTimeout(600000);
                config.setMaxLifetime(1800000);
                
                // SQLite specific settings
                config.addDataSourceProperty("cachePrepStmts", "true");
                config.addDataSourceProperty("prepStmtCacheSize", "250");
                config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
                
                dataSource = new HikariDataSource(config);
                
                // Create tables and indexes
                try (Connection conn = dataSource.getConnection()) {
                    try (Statement stmt = conn.createStatement()) {
                        stmt.execute(CREATE_PLAYERS_TABLE);
                        stmt.execute(CREATE_AUTH_LOGS_TABLE);
                        stmt.execute(CREATE_USERNAME_INDEX);
                        stmt.execute(CREATE_IP_INDEX);
                        stmt.execute(CREATE_LOGS_TIMESTAMP_INDEX);
                        stmt.execute(CREATE_LOGS_USERNAME_INDEX);
                    }
                }
                
                logger.info("SQLite database initialized successfully");
                
            } catch (SQLException e) {
                logger.logError("SQLiteManager", e);
                throw new CompletionException("Failed to initialize SQLite database", e);
            }
        });
    }
    
    @Override
    public CompletableFuture<Boolean> registerPlayer(String username, String passwordHash) {
        return connectionRecovery.executeWithRetry(() -> {
            String sql = "INSERT INTO players (username, password_hash) VALUES (?, ?)";
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, username);
                stmt.setString(2, passwordHash);
                
                int rowsAffected = stmt.executeUpdate();
                return rowsAffected > 0;
                
            } catch (SQLException e) {
                logger.logError("SQLiteManager.registerPlayer", e);
                throw new RuntimeException("Failed to register player: " + username, e);
            }
        }, "registerPlayer");
    }
    
    @Override
    public CompletableFuture<PlayerData> getPlayerData(String username) {
        return connectionRecovery.executeWithRetry(() -> {
            String sql = "SELECT * FROM players WHERE username = ?";
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, username);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return new PlayerData(
                            rs.getString("username"),
                            rs.getString("password_hash"),
                            rs.getTimestamp("registration_date"),
                            rs.getTimestamp("last_login"),
                            rs.getString("last_ip"),
                            rs.getInt("failed_attempts"),
                            rs.getTimestamp("lockout_until") != null ? 
                                rs.getTimestamp("lockout_until").getTime() : 0
                        );
                    }
                }
                
                return null;
                
            } catch (SQLException e) {
                logger.logError("SQLiteManager.getPlayerData", e);
                throw new RuntimeException("Failed to get player data: " + username, e);
            }
        }, "getPlayerData");
    }
    
    @Override
    public CompletableFuture<Boolean> updatePassword(String username, String newPasswordHash) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "UPDATE players SET password_hash = ? WHERE username = ?";
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, newPasswordHash);
                stmt.setString(2, username);
                
                int rowsAffected = stmt.executeUpdate();
                return rowsAffected > 0;
                
            } catch (SQLException e) {
                logger.logError("SQLiteManager.updatePassword", e);
                return false;
            }
        });
    }
    
    @Override
    public CompletableFuture<Boolean> deletePlayer(String username) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM players WHERE username = ?";
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, username);
                
                int rowsAffected = stmt.executeUpdate();
                return rowsAffected > 0;
                
            } catch (SQLException e) {
                logger.logError("SQLiteManager.deletePlayer", e);
                return false;
            }
        });
    }
    
    @Override
    public CompletableFuture<Void> updateLastLogin(String username, String ipAddress) {
        return CompletableFuture.runAsync(() -> {
            String sql = "UPDATE players SET last_login = CURRENT_TIMESTAMP, last_ip = ? WHERE username = ?";
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, ipAddress);
                stmt.setString(2, username);
                
                stmt.executeUpdate();
                
            } catch (SQLException e) {
                logger.logError("SQLiteManager.updateLastLogin", e);
            }
        });
    }
    
    @Override
    public CompletableFuture<Void> updateFailedAttempts(String username, int failedAttempts, long lockoutUntil) {
        return CompletableFuture.runAsync(() -> {
            String sql = "UPDATE players SET failed_attempts = ?, lockout_until = ? WHERE username = ?";
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setInt(1, failedAttempts);
                if (lockoutUntil > 0) {
                    stmt.setTimestamp(2, new Timestamp(lockoutUntil));
                } else {
                    stmt.setNull(2, Types.TIMESTAMP);
                }
                stmt.setString(3, username);
                
                stmt.executeUpdate();
                
            } catch (SQLException e) {
                logger.logError("SQLiteManager.updateFailedAttempts", e);
            }
        });
    }
    
    @Override
    public CompletableFuture<Boolean> usernameExists(String username) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT 1 FROM players WHERE username = ?";
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, username);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next();
                }
                
            } catch (SQLException e) {
                logger.logError("SQLiteManager.usernameExists", e);
                return false;
            }
        });
    }
    
    @Override
    public CompletableFuture<List<String>> getSimilarUsernames(String username, double threshold) {
        return CompletableFuture.supplyAsync(() -> {
            List<String> similarUsernames = new ArrayList<>();
            String sql = "SELECT username FROM players";
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                
                while (rs.next()) {
                    String existingUsername = rs.getString("username");
                    if (calculateSimilarity(username, existingUsername) >= threshold) {
                        similarUsernames.add(existingUsername);
                    }
                }
                
            } catch (SQLException e) {
                logger.logError("SQLiteManager.getSimilarUsernames", e);
            }
            
            return similarUsernames;
        });
    }
    
    @Override
    public CompletableFuture<Void> logAuthAttempt(String username, String ipAddress, String action, boolean success) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO auth_logs (username, ip_address, action, success) VALUES (?, ?, ?, ?)";
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, username);
                stmt.setString(2, ipAddress);
                stmt.setString(3, action);
                stmt.setBoolean(4, success);
                
                stmt.executeUpdate();
                
            } catch (SQLException e) {
                logger.logError("SQLiteManager.logAuthAttempt", e);
            }
        });
    }
    
    @Override
    public CompletableFuture<AuthStats> getAuthStats() {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection()) {
                int totalRegistrations = 0;
                int totalLogins = 0;
                int failedAttempts = 0;
                int uniqueIPs = 0;
                
                // Get total registrations
                try (PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM players");
                     ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        totalRegistrations = rs.getInt(1);
                    }
                }
                
                // Get successful logins
                try (PreparedStatement stmt = conn.prepareStatement(
                        "SELECT COUNT(*) FROM auth_logs WHERE action = 'LOGIN' AND success = true");
                     ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        totalLogins = rs.getInt(1);
                    }
                }
                
                // Get failed attempts
                try (PreparedStatement stmt = conn.prepareStatement(
                        "SELECT COUNT(*) FROM auth_logs WHERE success = false");
                     ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        failedAttempts = rs.getInt(1);
                    }
                }
                
                // Get unique IPs
                try (PreparedStatement stmt = conn.prepareStatement(
                        "SELECT COUNT(DISTINCT last_ip) FROM players WHERE last_ip IS NOT NULL");
                     ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        uniqueIPs = rs.getInt(1);
                    }
                }
                
                return new AuthStats(totalRegistrations, totalLogins, failedAttempts, uniqueIPs);
                
            } catch (SQLException e) {
                logger.logError("SQLiteManager.getAuthStats", e);
                return new AuthStats(0, 0, 0, 0);
            }
        });
    }
    
    @Override
    public void performBackup() throws Exception {
        if (!databaseFile.exists()) {
            return;
        }
        
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        File backupFile = new File(plugin.getDataFolder(), "backups/authdata_" + timestamp + ".db");
        
        // Create backup directory if it doesn't exist
        if (!backupFile.getParentFile().exists()) {
            backupFile.getParentFile().mkdirs();
        }
        
        // Copy database file
        Files.copy(databaseFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        
        logger.info("Database backup created: " + backupFile.getName());
    }
    
    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("SQLite database connections closed");
        }
    }
    
    @Override
    public CompletableFuture<Boolean> registerPlayer(String username, String passwordHash, String ipAddress) {
        return registerPlayer(username, passwordHash).thenCompose(success -> {
            if (success) {
                return updateLastLogin(username, ipAddress).thenApply(v -> true);
            }
            return CompletableFuture.completedFuture(false);
        });
    }
    
    @Override
    public CompletableFuture<Boolean> playerExists(String username) {
        return usernameExists(username);
    }
    
    @Override
    public CompletableFuture<Void> resetFailedAttempts(String username) {
        return updateFailedAttempts(username, 0, 0);
    }
    
    @Override
    public CompletableFuture<Void> incrementFailedAttempts(String username) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "UPDATE players SET failed_attempts = failed_attempts + 1 WHERE username = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, username);
                stmt.executeUpdate();
                return null;
                
            } catch (SQLException e) {
                logger.severe("Failed to increment failed attempts for " + username + ": " + e.getMessage());
                throw new RuntimeException(e);
            }
        });
    }
    
    @Override
    public CompletableFuture<Void> lockAccount(String username, long lockoutDuration) {
        long lockoutUntil = System.currentTimeMillis() + lockoutDuration;
        return CompletableFuture.supplyAsync(() -> {
            String sql = "UPDATE players SET lockout_until = ? WHERE username = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setLong(1, lockoutUntil);
                stmt.setString(2, username);
                stmt.executeUpdate();
                return null;
                
            } catch (SQLException e) {
                logger.severe("Failed to lock account for " + username + ": " + e.getMessage());
                throw new RuntimeException(e);
            }
        });
    }
    
    @Override
    public CompletableFuture<Integer> getTotalPlayers() {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM players";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                
                if (rs.next()) {
                    return rs.getInt(1);
                }
                return 0;
                
            } catch (SQLException e) {
                logger.severe("Failed to get total players count: " + e.getMessage());
                throw new RuntimeException(e);
            }
        });
    }
    
    /**
     * Check database connection health
     */
    public CompletableFuture<DatabaseConnectionRecovery.DatabaseHealthStatus> checkHealth() {
        return connectionRecovery.getHealthStatus(() -> {
            try {
                return dataSource.getConnection();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to get connection for health check", e);
            }
        });
    }
    
    /**
     * Test database connectivity and perform basic operations
     */
    public CompletableFuture<Boolean> testConnectivity() {
        return connectionRecovery.testConnection(() -> {
            try {
                return dataSource.getConnection();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to get connection for connectivity test", e);
            }
        });
    }
    
    /**
     * Calculate similarity between two strings using Levenshtein distance
     */
    private double calculateSimilarity(String s1, String s2) {
        if (s1.equals(s2)) {
            return 1.0;
        }
        
        int maxLength = Math.max(s1.length(), s2.length());
        if (maxLength == 0) {
            return 1.0;
        }
        
        int distance = levenshteinDistance(s1.toLowerCase(), s2.toLowerCase());
        return 1.0 - (double) distance / maxLength;
    }
    
    /**
     * Calculate Levenshtein distance between two strings
     */
    private int levenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];
        
        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }
        
        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }
        
        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                int cost = s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }
        
        return dp[s1.length()][s2.length()];
    }
}