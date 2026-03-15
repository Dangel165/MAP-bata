package com.authplugin.config;

import com.authplugin.models.AuthConfig;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Manages plugin configuration loading, validation, and reloading
 * Handles default configuration creation and settings validation
 */
public class ConfigurationManager {
    
    private final Plugin plugin;
    private FileConfiguration config;
    private File configFile;
    
    // Per-player language map (session-based, not persisted)
    private final java.util.Map<java.util.UUID, String> playerLanguages = new java.util.concurrent.ConcurrentHashMap<>();
    
    // Default configuration values
    private static final String DEFAULT_DATABASE_TYPE = "sqlite";
    private static final int DEFAULT_MIN_PASSWORD_LENGTH = 6;
    private static final int DEFAULT_MAX_FAILED_ATTEMPTS = 3;
    private static final int DEFAULT_LOCKOUT_DURATION = 300; // 5 minutes
    private static final int DEFAULT_AUTH_TIMEOUT = 300; // 5 minutes
    private static final int DEFAULT_MOVEMENT_RADIUS = 5;
    private static final int DEFAULT_AUTO_REAUTH_TIME = 600; // 10 minutes
    
    public ConfigurationManager(Plugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "config.yml");
    }
    
    /**
     * Load configuration from file, creating default if needed
     */
    public void loadConfiguration() {
        // Create plugin data folder if it doesn't exist
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        
        // Create default config if it doesn't exist
        if (!configFile.exists()) {
            createDefaultConfiguration();
        }
        
        // Load configuration
        config = YamlConfiguration.loadConfiguration(configFile);
        
        // Validate and set defaults for missing values
        validateAndSetDefaults();
        
        // Save any changes made during validation
        saveConfiguration();
    }
    
    /**
     * Reload configuration from file
     */
    public void reloadConfiguration() {
        config = YamlConfiguration.loadConfiguration(configFile);
        validateAndSetDefaults();
    }
    
    /**
     * Create default configuration file
     */
    private void createDefaultConfiguration() {
        try {
            // Try to copy from resources first
            InputStream defaultConfigStream = plugin.getResource("config.yml");
            if (defaultConfigStream != null) {
                Files.copy(defaultConfigStream, configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                defaultConfigStream.close();
            } else {
                // Create programmatically if resource doesn't exist
                createProgrammaticConfig();
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Could not create default config file: " + e.getMessage());
            createProgrammaticConfig();
        }
    }
    
    /**
     * Create configuration programmatically
     */
    private void createProgrammaticConfig() {
        config = new YamlConfiguration();
        setDefaultValues();
        saveConfiguration();
    }
    
    /**
     * Set all default configuration values
     */
    private void setDefaultValues() {
        // Database settings
        config.set("database.type", DEFAULT_DATABASE_TYPE);
        config.set("database.sqlite.filename", "authdata.db");
        config.set("database.mysql.host", "localhost");
        config.set("database.mysql.port", 3306);
        config.set("database.mysql.database", "minecraft_auth");
        config.set("database.mysql.username", "auth_user");
        config.set("database.mysql.password", "change_this_password");
        config.set("database.mysql.useSSL", false);
        config.set("database.connection-pool.maximum-pool-size", 10);
        config.set("database.connection-pool.minimum-idle", 2);
        config.set("database.connection-pool.connection-timeout", 30000);
        
        // Security settings
        config.set("security.min-password-length", DEFAULT_MIN_PASSWORD_LENGTH);
        config.set("security.max-failed-attempts", DEFAULT_MAX_FAILED_ATTEMPTS);
        config.set("security.lockout-duration-seconds", DEFAULT_LOCKOUT_DURATION);
        config.set("security.auth-timeout-seconds", DEFAULT_AUTH_TIMEOUT);
        config.set("security.movement-radius", DEFAULT_MOVEMENT_RADIUS);
        config.set("security.auto-reauth-time-seconds", DEFAULT_AUTO_REAUTH_TIME);
        config.set("security.enable-ip-rate-limiting", true);
        config.set("security.enable-username-similarity-check", true);
        
        // Spawn location settings
        config.set("spawn.world", "world");
        config.set("spawn.x", 0.0);
        config.set("spawn.y", 64.0);
        config.set("spawn.z", 0.0);
        config.set("spawn.yaw", 0.0f);
        config.set("spawn.pitch", 0.0f);
        
        // Message settings
        config.set("messages.language", "en");
        config.set("messages.enable-custom-messages", false);
        
        // Performance settings
        config.set("performance.enable-debug-mode", false);
        config.set("performance.enable-performance-logging", false);
        config.set("performance.session-cleanup-interval-minutes", 5);
        config.set("performance.database-backup-enabled", true);
        
        // Feature toggles
        config.set("features.enable-auto-reauth", true);
        config.set("features.enable-player-hiding", true);
        config.set("features.enable-timeout-warnings", true);
        config.set("features.enable-password-strength-indicator", true);
    }
    
    /**
     * Validate configuration and set defaults for missing values
     */
    private void validateAndSetDefaults() {
        boolean changed = false;
        
        // Validate database type
        String dbType = config.getString("database.type", DEFAULT_DATABASE_TYPE);
        if (!dbType.equals("sqlite") && !dbType.equals("mysql")) {
            config.set("database.type", DEFAULT_DATABASE_TYPE);
            plugin.getLogger().warning("Invalid database type '" + dbType + "', using default: " + DEFAULT_DATABASE_TYPE);
            changed = true;
        }
        
        // Validate numeric values
        if (config.getInt("security.min-password-length", -1) < 1) {
            config.set("security.min-password-length", DEFAULT_MIN_PASSWORD_LENGTH);
            plugin.getLogger().warning("Invalid min-password-length, using default: " + DEFAULT_MIN_PASSWORD_LENGTH);
            changed = true;
        }
        
        if (config.getInt("security.max-failed-attempts", -1) < 1) {
            config.set("security.max-failed-attempts", DEFAULT_MAX_FAILED_ATTEMPTS);
            plugin.getLogger().warning("Invalid max-failed-attempts, using default: " + DEFAULT_MAX_FAILED_ATTEMPTS);
            changed = true;
        }
        
        if (config.getInt("security.lockout-duration-seconds", -1) < 1) {
            config.set("security.lockout-duration-seconds", DEFAULT_LOCKOUT_DURATION);
            plugin.getLogger().warning("Invalid lockout-duration-seconds, using default: " + DEFAULT_LOCKOUT_DURATION);
            changed = true;
        }
        
        if (config.getInt("security.auth-timeout-seconds", -1) < 1) {
            config.set("security.auth-timeout-seconds", DEFAULT_AUTH_TIMEOUT);
            plugin.getLogger().warning("Invalid auth-timeout-seconds, using default: " + DEFAULT_AUTH_TIMEOUT);
            changed = true;
        }
        
        if (config.getInt("security.movement-radius", -1) < 0) {
            config.set("security.movement-radius", DEFAULT_MOVEMENT_RADIUS);
            plugin.getLogger().warning("Invalid movement-radius, using default: " + DEFAULT_MOVEMENT_RADIUS);
            changed = true;
        }
        
        // Validate language setting
        String language = config.getString("messages.language", "en");
        if (!language.equals("en") && !language.equals("ko") && !language.equals("kr")) {
            config.set("messages.language", "en");
            plugin.getLogger().warning("Invalid language '" + language + "', using default: en");
            changed = true;
        }
        
        // Set missing values with defaults
        if (!config.contains("spawn.world")) {
            config.set("spawn.world", "world");
            changed = true;
        }
        
        if (!config.contains("messages.language")) {
            config.set("messages.language", "en");
            changed = true;
        }
        
        // Validate MySQL settings if MySQL is selected
        if ("mysql".equals(config.getString("database.type"))) {
            if (!config.contains("database.mysql.host") || config.getString("database.mysql.host").isEmpty()) {
                plugin.getLogger().warning("MySQL host not configured, using default: localhost");
                config.set("database.mysql.host", "localhost");
                changed = true;
            }
            
            if (!config.contains("database.mysql.database") || config.getString("database.mysql.database").isEmpty()) {
                plugin.getLogger().warning("MySQL database name not configured, using default: minecraft_auth");
                config.set("database.mysql.database", "minecraft_auth");
                changed = true;
            }
        }
        
        if (changed) {
            plugin.getLogger().info("Configuration validated and missing values set to defaults");
        }
    }
    
    /**
     * Save configuration to file
     */
    private void saveConfiguration() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save configuration file: " + e.getMessage());
        }
    }
    
    // Getter methods for configuration values
    public String getDatabaseType() {
        return config.getString("database.type", DEFAULT_DATABASE_TYPE);
    }
    
    public String getSQLiteFilename() {
        return config.getString("database.sqlite.filename", "authdata.db");
    }
    
    public String getMySQLHost() {
        return config.getString("database.mysql.host", "localhost");
    }
    
    public int getMySQLPort() {
        return config.getInt("database.mysql.port", 3306);
    }
    
    public String getMySQLDatabase() {
        return config.getString("database.mysql.database", "minecraft_auth");
    }
    
    public String getMySQLUsername() {
        return config.getString("database.mysql.username", "auth_user");
    }
    
    public String getMySQLPassword() {
        return config.getString("database.mysql.password", "change_this_password");
    }
    
    public boolean getMySQLUseSSL() {
        return config.getBoolean("database.mysql.useSSL", false);
    }
    
    public int getMaximumPoolSize() {
        return config.getInt("database.connection-pool.maximum-pool-size", 10);
    }
    
    public int getMinimumIdle() {
        return config.getInt("database.connection-pool.minimum-idle", 2);
    }
    
    public long getConnectionTimeout() {
        return config.getLong("database.connection-pool.connection-timeout", 30000);
    }
    
    public int getMinPasswordLength() {
        return config.getInt("security.min-password-length", DEFAULT_MIN_PASSWORD_LENGTH);
    }
    
    public int getMaxFailedAttempts() {
        return config.getInt("security.max-failed-attempts", DEFAULT_MAX_FAILED_ATTEMPTS);
    }
    
    public int getLockoutDurationSeconds() {
        return config.getInt("security.lockout-duration-seconds", DEFAULT_LOCKOUT_DURATION);
    }
    
    public int getAuthTimeoutSeconds() {
        return config.getInt("security.auth-timeout-seconds", DEFAULT_AUTH_TIMEOUT);
    }
    
    public int getMovementRadius() {
        return config.getInt("security.movement-radius", DEFAULT_MOVEMENT_RADIUS);
    }
    
    public int getAutoReauthTimeSeconds() {
        return config.getInt("security.auto-reauth-time-seconds", DEFAULT_AUTO_REAUTH_TIME);
    }
    
    public boolean isIpRateLimitingEnabled() {
        return config.getBoolean("security.enable-ip-rate-limiting", true);
    }
    
    public boolean isUsernameSimilarityCheckEnabled() {
        return config.getBoolean("security.enable-username-similarity-check", true);
    }
    
    public String getSpawnWorld() {
        return config.getString("spawn.world", "world");
    }
    
    public double getSpawnX() {
        return config.getDouble("spawn.x", 0.0);
    }
    
    public double getSpawnY() {
        return config.getDouble("spawn.y", 64.0);
    }
    
    public double getSpawnZ() {
        return config.getDouble("spawn.z", 0.0);
    }
    
    public float getSpawnYaw() {
        return (float) config.getDouble("spawn.yaw", 0.0);
    }
    
    public float getSpawnPitch() {
        return (float) config.getDouble("spawn.pitch", 0.0);
    }
    
    public String getLanguage() {
        return config.getString("messages.language", "en");
    }
    
    /**
     * Set the language and persist it to config.yml
     */
    public void setLanguage(String language) {
        config.set("messages.language", language);
        saveConfiguration();
    }
    
    public boolean isCustomMessagesEnabled() {
        return config.getBoolean("messages.enable-custom-messages", false);
    }
    
    public boolean isDebugModeEnabled() {
        return config.getBoolean("performance.enable-debug-mode", false);
    }
    
    public boolean isPerformanceLoggingEnabled() {
        return config.getBoolean("performance.enable-performance-logging", false);
    }
    
    public int getSessionCleanupIntervalMinutes() {
        return config.getInt("performance.session-cleanup-interval-minutes", 5);
    }
    
    public boolean isDatabaseBackupEnabled() {
        return config.getBoolean("performance.database-backup-enabled", true);
    }
    
    public boolean isAutoReauthEnabled() {
        return config.getBoolean("features.enable-auto-reauth", true);
    }
    
    public boolean isPlayerHidingEnabled() {
        return config.getBoolean("features.enable-player-hiding", true);
    }
    
    public boolean isTimeoutWarningsEnabled() {
        return config.getBoolean("features.enable-timeout-warnings", true);
    }
    
    public boolean isPasswordStrengthIndicatorEnabled() {
        return config.getBoolean("features.enable-password-strength-indicator", true);
    }
    
    /**
     * Get the underlying FileConfiguration for advanced access
     */
    public FileConfiguration getConfig() {
        return config;
    }
    
    /**
     * Create an AuthConfig object from the current configuration
     */
    public AuthConfig createAuthConfig() {
        // Create database configuration
        AuthConfig.ConnectionPoolConfig connectionPool = new AuthConfig.ConnectionPoolConfig(
            getMaximumPoolSize(),
            getMinimumIdle(),
            getConnectionTimeout()
        );
        
        AuthConfig.DatabaseConfig database = new AuthConfig.DatabaseConfig(
            getDatabaseType(),
            getMySQLHost(),
            getMySQLPort(),
            getMySQLDatabase(),
            getMySQLUsername(),
            getMySQLPassword(),
            getSQLiteFilename(),
            getMySQLUseSSL(),
            connectionPool
        );
        
        // Create security configuration
        AuthConfig.SecurityConfig security = new AuthConfig.SecurityConfig(
            getMinPasswordLength(),
            getMaxFailedAttempts(),
            getLockoutDurationSeconds(),
            getAuthTimeoutSeconds(),
            getMovementRadius(),
            getAutoReauthTimeSeconds(),
            isIpRateLimitingEnabled(),
            isUsernameSimilarityCheckEnabled()
        );
        
        // Create message configuration
        AuthConfig.MessageConfig messages = new AuthConfig.MessageConfig(
            getLanguage(),
            isCustomMessagesEnabled()
        );
        
        // Create location configuration
        AuthConfig.LocationConfig spawnLocation = new AuthConfig.LocationConfig(
            getSpawnWorld(),
            getSpawnX(),
            getSpawnY(),
            getSpawnZ(),
            getSpawnYaw(),
            getSpawnPitch()
        );
        
        // Create performance configuration
        AuthConfig.PerformanceConfig performance = new AuthConfig.PerformanceConfig(
            isDebugModeEnabled(),
            isPerformanceLoggingEnabled(),
            getSessionCleanupIntervalMinutes(),
            isDatabaseBackupEnabled()
        );
        
        // Create feature configuration
        AuthConfig.FeatureConfig features = new AuthConfig.FeatureConfig(
            isAutoReauthEnabled(),
            isPlayerHidingEnabled(),
            isTimeoutWarningsEnabled(),
            isPasswordStrengthIndicatorEnabled()
        );
        
        return new AuthConfig(database, security, messages, spawnLocation, performance, features);
    }
    
    /**
     * Validate configuration integrity and return validation results
     */
    public ConfigValidationResult validateConfiguration() {
        ConfigValidationResult result = new ConfigValidationResult();
        
        // Validate database configuration
        String dbType = getDatabaseType();
        if ("mysql".equals(dbType)) {
            if (getMySQLHost().isEmpty()) {
                result.addError("MySQL host cannot be empty");
            }
            if (getMySQLDatabase().isEmpty()) {
                result.addError("MySQL database name cannot be empty");
            }
            if (getMySQLUsername().isEmpty()) {
                result.addError("MySQL username cannot be empty");
            }
            if (getMySQLPassword().equals("change_this_password")) {
                result.addWarning("MySQL password should be changed from default");
            }
        }
        
        // Validate security settings
        if (getMinPasswordLength() < 4) {
            result.addWarning("Minimum password length is very low (recommended: 6+)");
        }
        if (getMaxFailedAttempts() > 10) {
            result.addWarning("Max failed attempts is very high (recommended: 3-5)");
        }
        if (getAuthTimeoutSeconds() < 60) {
            result.addWarning("Auth timeout is very short (recommended: 300+ seconds)");
        }
        
        // Validate spawn location
        if (getSpawnWorld().isEmpty()) {
            result.addError("Spawn world cannot be empty");
        }
        
        return result;
    }
    
    /**
     * Get a localized message with fallback.
     * If language is "ko", returns Korean translation when available.
     */
    public String getMessage(String key, String fallback) {
        String lang = getLanguage();
        if ("ko".equals(lang) || "kr".equals(lang)) {
            String korean = KO_MESSAGES.get(key);
            if (korean != null) return korean;
        }
        return fallback;
    }

    /**
     * Get a localized message for a specific player (personal language setting).
     */
    public String getMessage(String key, String fallback, java.util.UUID playerUuid) {
        String lang = playerUuid != null
            ? playerLanguages.getOrDefault(playerUuid, getLanguage())
            : getLanguage();
        if ("ko".equals(lang) || "kr".equals(lang)) {
            String korean = KO_MESSAGES.get(key);
            if (korean != null) return korean;
        }
        return fallback;
    }

    /** Set language for a specific player (session-based). */
    public void setPlayerLanguage(java.util.UUID playerUuid, String language) {
        playerLanguages.put(playerUuid, language);
    }

    /** Remove player language setting (on logout/quit). */
    public void removePlayerLanguage(java.util.UUID playerUuid) {
        playerLanguages.remove(playerUuid);
    }

    /** Get language for a specific player. */
    public String getPlayerLanguage(java.util.UUID playerUuid) {
        return playerLanguages.getOrDefault(playerUuid, getLanguage());
    }

    /** Korean message table */
    private static final java.util.Map<String, String> KO_MESSAGES;
    static {
        java.util.Map<String, String> m = new java.util.HashMap<>();
        // Login
        m.put("login.already-authenticated",   "§e이미 로그인되어 있습니다.");
        m.put("login.rate-limited",             "§c로그인 시도가 너무 많습니다. 잠시 후 다시 시도하세요.");
        m.put("login.invalid-input",            "§c비밀번호 형식이 올바르지 않습니다.");
        m.put("login.not-registered",           "§c등록되지 않은 계정입니다. /register <비밀번호> <비밀번호확인> 으로 회원가입하세요.");
        m.put("login.account-locked",           "§c계정이 일시적으로 잠겼습니다. {time} 후 다시 시도하세요.");
        m.put("login.account-locked-now",       "§c로그인 시도 횟수를 초과했습니다. 계정이 일시적으로 잠겼습니다.");
        m.put("login.failed",                   "§c비밀번호가 틀렸습니다. 남은 시도 횟수: {remaining}회.");
        m.put("login.success",                  "§a로그인 성공! 어서오세요, {name}님.");
        m.put("login.database-error",           "§c데이터베이스 오류가 발생했습니다. 잠시 후 다시 시도하세요.");
        // Register
        m.put("register.already-authenticated", "§e이미 로그인되어 있습니다.");
        m.put("register.rate-limited",          "§c시도 횟수가 너무 많습니다. 잠시 후 다시 시도하세요.");
        m.put("register.password-mismatch",     "§c비밀번호가 일치하지 않습니다. 다시 시도하세요.");
        m.put("register.password-too-short",    "§c비밀번호는 최소 {min}자 이상이어야 합니다.");
        m.put("register.invalid-input",         "§c비밀번호 형식이 올바르지 않습니다. 영문자와 숫자만 사용하세요.");
        m.put("register.already-registered",    "§c이미 등록된 계정입니다. /login <비밀번호> 로 로그인하세요.");
        m.put("register.success",               "§a회원가입 성공! 자동으로 로그인되었습니다.");
        m.put("register.welcome",               "§e서버에 오신 것을 환영합니다, {name}님! 계정이 안전하게 생성되었습니다.");
        m.put("register.failed",                "§c회원가입에 실패했습니다. 다시 시도하세요.");
        m.put("register.database-error",        "§c데이터베이스 오류가 발생했습니다. 잠시 후 다시 시도하세요.");
        m.put("register.password-weak",         "§6비밀번호 강도: 약함. 숫자나 특수문자를 추가해보세요.");
        m.put("register.password-medium",       "§e비밀번호 강도: 보통. 괜찮은 선택입니다!");
        m.put("register.password-strong",       "§a비밀번호 강도: 강함. 훌륭합니다!");
        KO_MESSAGES = java.util.Collections.unmodifiableMap(m);
    }
    
    /**
     * Check if a command is enabled
     */
    public boolean isCommandEnabled(String commandName) {
        return config.getBoolean("commands." + commandName + ".enabled", true);
    }
    
    /**
     * Get lockout duration in milliseconds
     */
    public long getLockoutDuration() {
        return getLockoutDurationSeconds() * 1000L;
    }
    
    /**
     * Configuration validation result container
     */
    public static class ConfigValidationResult {
        private final java.util.List<String> errors = new java.util.ArrayList<>();
        private final java.util.List<String> warnings = new java.util.ArrayList<>();
        
        public void addError(String error) {
            errors.add(error);
        }
        
        public void addWarning(String warning) {
            warnings.add(warning);
        }
        
        public boolean hasErrors() {
            return !errors.isEmpty();
        }
        
        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }
        
        public java.util.List<String> getErrors() {
            return new java.util.ArrayList<>(errors);
        }
        
        public java.util.List<String> getWarnings() {
            return new java.util.ArrayList<>(warnings);
        }
        
        public boolean isValid() {
            return !hasErrors();
        }
    }
}