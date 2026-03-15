package com.authplugin;

import com.authplugin.config.ConfigurationManager;
import com.authplugin.database.SQLiteManager;
import com.authplugin.models.AuthConfig;
import com.authplugin.models.PlayerData;
import com.authplugin.utils.AuthLogger;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Basic system integration test to verify database and configuration systems work
 * This is a checkpoint test for task 4
 */
class BasicSystemTest {

    @Mock
    private Plugin mockPlugin;

    @TempDir
    File tempDir;

    private ConfigurationManager configManager;
    private AuthLogger authLogger;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        when(mockPlugin.getDataFolder()).thenReturn(tempDir);
        when(mockPlugin.getLogger()).thenReturn(Logger.getLogger("TestPlugin"));
        
        authLogger = new AuthLogger(mockPlugin);
        configManager = new ConfigurationManager(mockPlugin);
    }

    @Test
    void shouldLoadDefaultConfiguration() {
        // Test that configuration manager can load default configuration
        configManager.loadConfiguration();
        
        // Verify basic configuration values
        assertThat(configManager.getDatabaseType()).isEqualTo("sqlite");
        assertThat(configManager.getMinPasswordLength()).isEqualTo(6);
        assertThat(configManager.getMaxFailedAttempts()).isEqualTo(3);
        assertThat(configManager.getAuthTimeoutSeconds()).isEqualTo(300);
    }

    @Test
    void shouldCreateAuthConfigObject() {
        // Test that configuration can be converted to AuthConfig object
        configManager.loadConfiguration();
        
        AuthConfig authConfig = configManager.createAuthConfig();
        
        assertThat(authConfig).isNotNull();
        assertThat(authConfig.getDatabase()).isNotNull();
        assertThat(authConfig.getSecurity()).isNotNull();
        assertThat(authConfig.getMessages()).isNotNull();
        assertThat(authConfig.getSpawnLocation()).isNotNull();
    }

    @Test
    void shouldInitializeSQLiteDatabase() throws Exception {
        // Test that SQLite database can be initialized
        configManager.loadConfiguration();
        
        SQLiteManager sqliteManager = new SQLiteManager(mockPlugin, authLogger);
        
        // Initialize database (this should create tables)
        sqliteManager.initializeDatabase().get();
        
        // Verify database file was created
        File dbFile = new File(tempDir, "authdata.db");
        assertThat(dbFile).exists();
        
        // Clean up
        sqliteManager.close();
    }

    @Test
    void shouldCreatePlayerDataModel() {
        // Test that PlayerData model can be created with all required fields
        String username = "testuser";
        String passwordHash = "hashedpassword123456789012345678901234567890123456789012";
        java.sql.Timestamp registrationDate = new java.sql.Timestamp(System.currentTimeMillis());
        java.sql.Timestamp lastLogin = new java.sql.Timestamp(System.currentTimeMillis());
        String lastIpAddress = "127.0.0.1";
        int failedAttempts = 0;
        long lockoutUntil = 0;

        PlayerData playerData = new PlayerData(
            username, passwordHash, registrationDate, lastLogin,
            lastIpAddress, failedAttempts, lockoutUntil
        );

        // Verify all fields are properly set
        assertThat(playerData.getUsername()).isEqualTo(username);
        assertThat(playerData.getPasswordHash()).isEqualTo(passwordHash);
        assertThat(playerData.getRegistrationDate()).isEqualTo(registrationDate);
        assertThat(playerData.getLastLogin()).isEqualTo(lastLogin);
        assertThat(playerData.getLastIpAddress()).isEqualTo(lastIpAddress);
        assertThat(playerData.getFailedAttempts()).isEqualTo(failedAttempts);
        assertThat(playerData.getLockoutUntil()).isEqualTo(lockoutUntil);
    }

    @Test
    void shouldValidateConfiguration() {
        // Test that configuration validation works
        configManager.loadConfiguration();
        
        ConfigurationManager.ConfigValidationResult result = configManager.validateConfiguration();
        
        assertThat(result).isNotNull();
        assertThat(result.isValid()).isTrue();
        assertThat(result.hasErrors()).isFalse();
    }
}