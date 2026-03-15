package com.authplugin.models;

/**
 * Configuration data model containing all plugin settings
 * Provides structured access to database, security, message, and location configurations
 */
public class AuthConfig {
    
    private final DatabaseConfig database;
    private final SecurityConfig security;
    private final MessageConfig messages;
    private final LocationConfig spawnLocation;
    private final PerformanceConfig performance;
    private final FeatureConfig features;
    
    public AuthConfig(DatabaseConfig database, SecurityConfig security, MessageConfig messages,
                     LocationConfig spawnLocation, PerformanceConfig performance, FeatureConfig features) {
        this.database = database;
        this.security = security;
        this.messages = messages;
        this.spawnLocation = spawnLocation;
        this.performance = performance;
        this.features = features;
    }
    
    public DatabaseConfig getDatabase() {
        return database;
    }
    
    public SecurityConfig getSecurity() {
        return security;
    }
    
    public MessageConfig getMessages() {
        return messages;
    }
    
    public LocationConfig getSpawnLocation() {
        return spawnLocation;
    }
    
    public PerformanceConfig getPerformance() {
        return performance;
    }
    
    public FeatureConfig getFeatures() {
        return features;
    }
    
    /**
     * Database configuration settings
     */
    public static class DatabaseConfig {
        private final String type; // "sqlite" or "mysql"
        private final String host;
        private final int port;
        private final String database;
        private final String username;
        private final String password;
        private final String sqliteFilename;
        private final boolean useSSL;
        private final ConnectionPoolConfig connectionPool;
        
        public DatabaseConfig(String type, String host, int port, String database, String username,
                            String password, String sqliteFilename, boolean useSSL, ConnectionPoolConfig connectionPool) {
            this.type = type;
            this.host = host;
            this.port = port;
            this.database = database;
            this.username = username;
            this.password = password;
            this.sqliteFilename = sqliteFilename;
            this.useSSL = useSSL;
            this.connectionPool = connectionPool;
        }
        
        public String getType() { return type; }
        public String getHost() { return host; }
        public int getPort() { return port; }
        public String getDatabase() { return database; }
        public String getUsername() { return username; }
        public String getPassword() { return password; }
        public String getSqliteFilename() { return sqliteFilename; }
        public boolean isUseSSL() { return useSSL; }
        public ConnectionPoolConfig getConnectionPool() { return connectionPool; }
        
        public boolean isSQLite() { return "sqlite".equalsIgnoreCase(type); }
        public boolean isMySQL() { return "mysql".equalsIgnoreCase(type); }
    }
    
    /**
     * Connection pool configuration
     */
    public static class ConnectionPoolConfig {
        private final int maximumPoolSize;
        private final int minimumIdle;
        private final long connectionTimeout;
        
        public ConnectionPoolConfig(int maximumPoolSize, int minimumIdle, long connectionTimeout) {
            this.maximumPoolSize = maximumPoolSize;
            this.minimumIdle = minimumIdle;
            this.connectionTimeout = connectionTimeout;
        }
        
        public int getMaximumPoolSize() { return maximumPoolSize; }
        public int getMinimumIdle() { return minimumIdle; }
        public long getConnectionTimeout() { return connectionTimeout; }
    }
    
    /**
     * Security configuration settings
     */
    public static class SecurityConfig {
        private final int minPasswordLength;
        private final int maxFailedAttempts;
        private final int lockoutDuration;
        private final int authTimeout;
        private final int movementRadius;
        private final int autoReauthTime;
        private final boolean enableIpRateLimiting;
        private final boolean enableUsernameSimilarityCheck;
        
        public SecurityConfig(int minPasswordLength, int maxFailedAttempts, int lockoutDuration,
                            int authTimeout, int movementRadius, int autoReauthTime,
                            boolean enableIpRateLimiting, boolean enableUsernameSimilarityCheck) {
            this.minPasswordLength = minPasswordLength;
            this.maxFailedAttempts = maxFailedAttempts;
            this.lockoutDuration = lockoutDuration;
            this.authTimeout = authTimeout;
            this.movementRadius = movementRadius;
            this.autoReauthTime = autoReauthTime;
            this.enableIpRateLimiting = enableIpRateLimiting;
            this.enableUsernameSimilarityCheck = enableUsernameSimilarityCheck;
        }
        
        public int getMinPasswordLength() { return minPasswordLength; }
        public int getMaxFailedAttempts() { return maxFailedAttempts; }
        public int getLockoutDuration() { return lockoutDuration; }
        public int getAuthTimeout() { return authTimeout; }
        public int getMovementRadius() { return movementRadius; }
        public int getAutoReauthTime() { return autoReauthTime; }
        public boolean isEnableIpRateLimiting() { return enableIpRateLimiting; }
        public boolean isEnableUsernameSimilarityCheck() { return enableUsernameSimilarityCheck; }
    }
    
    /**
     * Message configuration settings
     */
    public static class MessageConfig {
        private final String language;
        private final boolean enableCustomMessages;
        
        public MessageConfig(String language, boolean enableCustomMessages) {
            this.language = language;
            this.enableCustomMessages = enableCustomMessages;
        }
        
        public String getLanguage() { return language; }
        public boolean isEnableCustomMessages() { return enableCustomMessages; }
        public boolean isKorean() { return "ko".equalsIgnoreCase(language) || "kr".equalsIgnoreCase(language); }
        public boolean isEnglish() { return "en".equalsIgnoreCase(language); }
    }
    
    /**
     * Spawn location configuration
     */
    public static class LocationConfig {
        private final String world;
        private final double x;
        private final double y;
        private final double z;
        private final float yaw;
        private final float pitch;
        
        public LocationConfig(String world, double x, double y, double z, float yaw, float pitch) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }
        
        public String getWorld() { return world; }
        public double getX() { return x; }
        public double getY() { return y; }
        public double getZ() { return z; }
        public float getYaw() { return yaw; }
        public float getPitch() { return pitch; }
    }
    
    /**
     * Performance configuration settings
     */
    public static class PerformanceConfig {
        private final boolean enableDebugMode;
        private final boolean enablePerformanceLogging;
        private final int sessionCleanupIntervalMinutes;
        private final boolean databaseBackupEnabled;
        
        public PerformanceConfig(boolean enableDebugMode, boolean enablePerformanceLogging,
                               int sessionCleanupIntervalMinutes, boolean databaseBackupEnabled) {
            this.enableDebugMode = enableDebugMode;
            this.enablePerformanceLogging = enablePerformanceLogging;
            this.sessionCleanupIntervalMinutes = sessionCleanupIntervalMinutes;
            this.databaseBackupEnabled = databaseBackupEnabled;
        }
        
        public boolean isEnableDebugMode() { return enableDebugMode; }
        public boolean isEnablePerformanceLogging() { return enablePerformanceLogging; }
        public int getSessionCleanupIntervalMinutes() { return sessionCleanupIntervalMinutes; }
        public boolean isDatabaseBackupEnabled() { return databaseBackupEnabled; }
    }
    
    /**
     * Feature toggle configuration
     */
    public static class FeatureConfig {
        private final boolean enableAutoReauth;
        private final boolean enablePlayerHiding;
        private final boolean enableTimeoutWarnings;
        private final boolean enablePasswordStrengthIndicator;
        
        public FeatureConfig(boolean enableAutoReauth, boolean enablePlayerHiding,
                           boolean enableTimeoutWarnings, boolean enablePasswordStrengthIndicator) {
            this.enableAutoReauth = enableAutoReauth;
            this.enablePlayerHiding = enablePlayerHiding;
            this.enableTimeoutWarnings = enableTimeoutWarnings;
            this.enablePasswordStrengthIndicator = enablePasswordStrengthIndicator;
        }
        
        public boolean isEnableAutoReauth() { return enableAutoReauth; }
        public boolean isEnablePlayerHiding() { return enablePlayerHiding; }
        public boolean isEnableTimeoutWarnings() { return enableTimeoutWarnings; }
        public boolean isEnablePasswordStrengthIndicator() { return enablePasswordStrengthIndicator; }
    }
    
    @Override
    public String toString() {
        return "AuthConfig{" +
                "database=" + database.getType() +
                ", security.minPasswordLength=" + security.getMinPasswordLength() +
                ", security.authTimeout=" + security.getAuthTimeout() +
                ", messages.language='" + messages.getLanguage() + '\'' +
                ", spawnLocation.world='" + spawnLocation.getWorld() + '\'' +
                '}';
    }
}