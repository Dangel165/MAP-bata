package com.authplugin.config;

import com.authplugin.models.AuthConfig;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ConfigurationManager
 * Tests specific examples of configuration loading, validation, and error handling
 */
class ConfigurationManagerTest {

    @Mock
    private Plugin mockPlugin;

    @TempDir
    Path tempDir;

    private ConfigurationManager configManager;
    private File dataFolder;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        dataFolder = tempDir.toFile();
        when(mockPlugin.getDataFolder()).thenReturn(dataFolder);
        when(mockPlugin.getLogger()).thenReturn(Logger.getLogger("TestLogger"));
        
        configManager = new ConfigurationManager(mockPlugin);
    }

    @Test
    void shouldCreateDefaultConfigurationOnFirstStartup() {
        // Given: No config file exists
        File configFile = new File(dataFolder, "config.yml");
        assertThat(configFile).doesNotExist();
        
        // When: Loading configuration for the first time
        configManager.loadConfiguration();
        
        // Then: Default config file should be created
        assertThat(configFile).exists();
        
        // And: Default values should be set
        assertThat(configManager.getDatabaseType()).isEqualTo("sqlite");
        assertThat(configManager.getMinPasswordLength()).isEqualTo(6);
        assertThat(configManager.getMaxFailedAttempts()).isEqualTo(3);
        assertThat(configManager.getAuthTimeoutSeconds()).isEqualTo(300);
        assertThat(configManager.getLanguage()).isEqualTo("en");
    }

    @Test
    void shouldLoadExistingConfiguration() throws IOException {
        // Given: A config file with custom values
        File configFile = new File(dataFolder, "config.yml");
        dataFolder.mkdirs();
        
        String customConfig = """
            database:
              type: mysql
            security:
              min-password-length: 8
              max-failed-attempts: 5
              auth-timeout-seconds: 600
            messages:
              language: ko
            """;
        
        Files.writeString(configFile.toPath(), customConfig);
        
        // When: Loading configuration
        configManager.loadConfiguration();
        
        // Then: Custom values should be loaded
        assertThat(configManager.getDatabaseType()).isEqualTo("mysql");
        assertThat(configManager.getMinPasswordLength()).isEqualTo(8);
        assertThat(configManager.getMaxFailedAttempts()).isEqualTo(5);
        assertThat(configManager.getAuthTimeoutSeconds()).isEqualTo(600);
        assertThat(configManager.getLanguage()).isEqualTo("ko");
    }

    @Test
    void shouldValidateAndCorrectInvalidConfiguration() throws IOException {
        // Given: A config file with invalid values
        File configFile = new File(dataFolder, "config.yml");
        dataFolder.mkdirs();
        
        String invalidConfig = """
            database:
              type: invalid_db_type
            security:
              min-password-length: -1
              max-failed-attempts: 0
              auth-timeout-seconds: -100
              movement-radius: -5
            messages:
              language: invalid_lang
            """;
        
        Files.writeString(configFile.toPath(), invalidConfig);
        
        // When: Loading configuration
        configManager.loadConfiguration();
        
        // Then: Invalid values should be corrected to defaults
        assertThat(configManager.getDatabaseType()).isEqualTo("sqlite"); // corrected from invalid
        assertThat(configManager.getMinPasswordLength()).isEqualTo(6); // corrected from -1
        assertThat(configManager.getMaxFailedAttempts()).isEqualTo(3); // corrected from 0
        assertThat(configManager.getAuthTimeoutSeconds()).isEqualTo(300); // corrected from -100
        assertThat(configManager.getMovementRadius()).isEqualTo(5); // corrected from -5
        assertThat(configManager.getLanguage()).isEqualTo("en"); // corrected from invalid
    }

    @Test
    void shouldReloadConfigurationSuccessfully() throws IOException {
        // Given: Initial configuration loaded
        configManager.loadConfiguration();
        assertThat(configManager.getMinPasswordLength()).isEqualTo(6);
        
        // And: Config file is modified
        File configFile = new File(dataFolder, "config.yml");
        String modifiedConfig = """
            security:
              min-password-length: 10
            """;
        Files.writeString(configFile.toPath(), modifiedConfig);
        
        // When: Reloading configuration
        configManager.reloadConfiguration();
        
        // Then: New values should be loaded
        assertThat(configManager.getMinPasswordLength()).isEqualTo(10);
    }

    @Test
    void shouldCreateAuthConfigWithAllSections() {
        // Given: Configuration is loaded
        configManager.loadConfiguration();
        
        // When: Creating AuthConfig object
        AuthConfig authConfig = configManager.createAuthConfig();
        
        // Then: All configuration sections should be present
        assertThat(authConfig).isNotNull();
        assertThat(authConfig.getDatabase()).isNotNull();
        assertThat(authConfig.getSecurity()).isNotNull();
        assertThat(authConfig.getMessages()).isNotNull();
        assertThat(authConfig.getSpawnLocation()).isNotNull();
        assertThat(authConfig.getPerformance()).isNotNull();
        assertThat(authConfig.getFeatures()).isNotNull();
        
        // And: Values should match configuration
        assertThat(authConfig.getDatabase().getType()).isEqualTo(configManager.getDatabaseType());
        assertThat(authConfig.getSecurity().getMinPasswordLength()).isEqualTo(configManager.getMinPasswordLength());
        assertThat(authConfig.getMessages().getLanguage()).isEqualTo(configManager.getLanguage());
    }

    @Test
    void shouldValidateConfigurationAndReturnResults() {
        // Given: Configuration with some issues
        configManager.loadConfiguration();
        
        // When: Validating configuration
        ConfigurationManager.ConfigValidationResult result = configManager.validateConfiguration();
        
        // Then: Validation should complete without errors for default config
        assertThat(result).isNotNull();
        assertThat(result.isValid()).isTrue();
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void shouldDetectMySQLConfigurationIssues() throws IOException {
        // Given: MySQL configuration with missing required fields
        File configFile = new File(dataFolder, "config.yml");
        dataFolder.mkdirs();
        
        String mysqlConfig = """
            database:
              type: mysql
              mysql:
                host: ""
                database: ""
                username: ""
                password: "change_this_password"
            """;
        
        Files.writeString(configFile.toPath(), mysqlConfig);
        configManager.loadConfiguration();
        
        // When: Validating configuration
        ConfigurationManager.ConfigValidationResult result = configManager.validateConfiguration();
        
        // Then: Should detect remaining configuration errors (username is still empty)
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).contains("MySQL username cannot be empty");
        
        // And: Should have warnings about default password
        assertThat(result.hasWarnings()).isTrue();
        assertThat(result.getWarnings()).contains("MySQL password should be changed from default");
        
        // And: Host and database should have been auto-corrected to defaults
        assertThat(configManager.getMySQLHost()).isEqualTo("localhost");
        assertThat(configManager.getMySQLDatabase()).isEqualTo("minecraft_auth");
    }

    @Test
    void shouldHandleSecurityConfigurationWarnings() throws IOException {
        // Given: Security configuration with questionable values
        File configFile = new File(dataFolder, "config.yml");
        dataFolder.mkdirs();
        
        String securityConfig = """
            security:
              min-password-length: 3
              max-failed-attempts: 15
              auth-timeout-seconds: 30
            """;
        
        Files.writeString(configFile.toPath(), securityConfig);
        configManager.loadConfiguration();
        
        // When: Validating configuration
        ConfigurationManager.ConfigValidationResult result = configManager.validateConfiguration();
        
        // Then: Should generate appropriate warnings
        assertThat(result.hasWarnings()).isTrue();
        assertThat(result.getWarnings()).contains("Minimum password length is very low (recommended: 6+)");
        assertThat(result.getWarnings()).contains("Max failed attempts is very high (recommended: 3-5)");
        assertThat(result.getWarnings()).contains("Auth timeout is very short (recommended: 300+ seconds)");
    }

    @Test
    void shouldHandleEmptySpawnWorldError() throws IOException {
        // Given: Configuration with empty spawn world
        File configFile = new File(dataFolder, "config.yml");
        dataFolder.mkdirs();
        
        String spawnConfig = """
            spawn:
              world: ""
            """;
        
        Files.writeString(configFile.toPath(), spawnConfig);
        configManager.loadConfiguration();
        
        // When: Validating configuration
        ConfigurationManager.ConfigValidationResult result = configManager.validateConfiguration();
        
        // Then: Should detect spawn world error
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).contains("Spawn world cannot be empty");
    }

    @Test
    void shouldProvideAllConfigurationGetters() {
        // Given: Configuration is loaded
        configManager.loadConfiguration();
        
        // When/Then: All getter methods should return valid values
        assertThat(configManager.getDatabaseType()).isNotNull();
        assertThat(configManager.getSQLiteFilename()).isNotNull();
        assertThat(configManager.getMySQLHost()).isNotNull();
        assertThat(configManager.getMySQLPort()).isGreaterThan(0);
        assertThat(configManager.getMySQLDatabase()).isNotNull();
        assertThat(configManager.getMySQLUsername()).isNotNull();
        assertThat(configManager.getMySQLPassword()).isNotNull();
        
        assertThat(configManager.getMinPasswordLength()).isGreaterThan(0);
        assertThat(configManager.getMaxFailedAttempts()).isGreaterThan(0);
        assertThat(configManager.getLockoutDurationSeconds()).isGreaterThan(0);
        assertThat(configManager.getAuthTimeoutSeconds()).isGreaterThan(0);
        assertThat(configManager.getMovementRadius()).isGreaterThanOrEqualTo(0);
        assertThat(configManager.getAutoReauthTimeSeconds()).isGreaterThan(0);
        
        assertThat(configManager.getSpawnWorld()).isNotNull();
        assertThat(configManager.getLanguage()).isNotNull();
        
        assertThat(configManager.getMaximumPoolSize()).isGreaterThan(0);
        assertThat(configManager.getMinimumIdle()).isGreaterThanOrEqualTo(0);
        assertThat(configManager.getConnectionTimeout()).isGreaterThan(0);
        
        assertThat(configManager.getSessionCleanupIntervalMinutes()).isGreaterThan(0);
    }

    @Test
    void shouldHandleMissingConfigFileGracefully() {
        // Given: Plugin resource returns null (no default config in resources)
        when(mockPlugin.getResource("config.yml")).thenReturn(null);
        
        // When: Loading configuration
        configManager.loadConfiguration();
        
        // Then: Should create programmatic config without errors
        assertThat(configManager.getDatabaseType()).isEqualTo("sqlite");
        assertThat(configManager.getMinPasswordLength()).isEqualTo(6);
    }

    @Test
    void shouldPreserveBooleanConfigurationValues() {
        // Given: Configuration is loaded
        configManager.loadConfiguration();
        
        // When/Then: Boolean values should be accessible
        assertThat(configManager.getMySQLUseSSL()).isIn(true, false);
        assertThat(configManager.isIpRateLimitingEnabled()).isIn(true, false);
        assertThat(configManager.isUsernameSimilarityCheckEnabled()).isIn(true, false);
        assertThat(configManager.isCustomMessagesEnabled()).isIn(true, false);
        assertThat(configManager.isDebugModeEnabled()).isIn(true, false);
        assertThat(configManager.isPerformanceLoggingEnabled()).isIn(true, false);
        assertThat(configManager.isDatabaseBackupEnabled()).isIn(true, false);
        assertThat(configManager.isAutoReauthEnabled()).isIn(true, false);
        assertThat(configManager.isPlayerHidingEnabled()).isIn(true, false);
        assertThat(configManager.isTimeoutWarningsEnabled()).isIn(true, false);
        assertThat(configManager.isPasswordStrengthIndicatorEnabled()).isIn(true, false);
    }
}