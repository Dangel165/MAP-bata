package com.authplugin.database;

import com.authplugin.models.PlayerData;
import com.authplugin.utils.AuthLogger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for database operations
 * Tests player registration, retrieval, password updates, and deletions
 * Requirements: 5.1, 5.2, 5.5
 */
class DatabaseOperationsTest {

    @Mock
    private org.bukkit.plugin.Plugin plugin;
    
    private AuthLogger logger;
    private SQLiteManager sqliteManager;
    
    @TempDir
    File tempDir;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Mock plugin data folder and logger BEFORE creating AuthLogger
        when(plugin.getDataFolder()).thenReturn(tempDir);
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("TestPlugin"));
        
        logger = new AuthLogger(plugin);
        sqliteManager = new SQLiteManager(plugin, logger);
    }
    
    @AfterEach
    void tearDown() {
        if (sqliteManager != null) {
            sqliteManager.close();
        }
    }

    @Test
    @DisplayName("Should initialize database successfully")
    void shouldInitializeDatabaseSuccessfully() throws ExecutionException, InterruptedException {
        // When
        sqliteManager.initializeDatabase().get();
        
        // Then
        boolean isHealthy = sqliteManager.testConnectivity().get();
        assertThat(isHealthy).isTrue();
    }

    @Test
    @DisplayName("Should register new player successfully")
    void shouldRegisterNewPlayerSuccessfully() throws ExecutionException, InterruptedException {
        // Given
        sqliteManager.initializeDatabase().get();
        String username = "testPlayer";
        String passwordHash = "$2a$10$abcdefghijklmnopqrstuvwxyz";
        
        // When
        boolean result = sqliteManager.registerPlayer(username, passwordHash).get();
        
        // Then
        assertThat(result).isTrue();
        
        // Verify player exists
        boolean exists = sqliteManager.usernameExists(username).get();
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("Should retrieve player data correctly")
    void shouldRetrievePlayerDataCorrectly() throws ExecutionException, InterruptedException {
        // Given
        sqliteManager.initializeDatabase().get();
        String username = "testPlayer";
        String passwordHash = "$2a$10$abcdefghijklmnopqrstuvwxyz";
        sqliteManager.registerPlayer(username, passwordHash).get();
        
        // When
        PlayerData playerData = sqliteManager.getPlayerData(username).get();
        
        // Then
        assertThat(playerData).isNotNull();
        assertThat(playerData.getUsername()).isEqualTo(username);
        assertThat(playerData.getPasswordHash()).isEqualTo(passwordHash);
        assertThat(playerData.getRegistrationDate()).isNotNull();
        assertThat(playerData.getFailedAttempts()).isEqualTo(0);
        assertThat(playerData.getLockoutUntil()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should return null for non-existent player")
    void shouldReturnNullForNonExistentPlayer() throws ExecutionException, InterruptedException {
        // Given
        sqliteManager.initializeDatabase().get();
        
        // When
        PlayerData playerData = sqliteManager.getPlayerData("nonExistentPlayer").get();
        
        // Then
        assertThat(playerData).isNull();
    }

    @Test
    @DisplayName("Should update player password successfully")
    void shouldUpdatePlayerPasswordSuccessfully() throws ExecutionException, InterruptedException {
        // Given
        sqliteManager.initializeDatabase().get();
        String username = "testPlayer";
        String originalPassword = "$2a$10$original";
        String newPassword = "$2a$10$newpassword";
        sqliteManager.registerPlayer(username, originalPassword).get();
        
        // When
        boolean result = sqliteManager.updatePassword(username, newPassword).get();
        
        // Then
        assertThat(result).isTrue();
        
        // Verify password was updated
        PlayerData updatedData = sqliteManager.getPlayerData(username).get();
        assertThat(updatedData.getPasswordHash()).isEqualTo(newPassword);
    }

    @Test
    @DisplayName("Should delete player successfully")
    void shouldDeletePlayerSuccessfully() throws ExecutionException, InterruptedException {
        // Given
        sqliteManager.initializeDatabase().get();
        String username = "testPlayer";
        String passwordHash = "$2a$10$abcdefghijklmnopqrstuvwxyz";
        sqliteManager.registerPlayer(username, passwordHash).get();
        
        // Verify player exists
        assertThat(sqliteManager.usernameExists(username).get()).isTrue();
        
        // When
        boolean result = sqliteManager.deletePlayer(username).get();
        
        // Then
        assertThat(result).isTrue();
        
        // Verify player no longer exists
        assertThat(sqliteManager.usernameExists(username).get()).isFalse();
        assertThat(sqliteManager.getPlayerData(username).get()).isNull();
    }

    @Test
    @DisplayName("Should update last login information")
    void shouldUpdateLastLoginInformation() throws ExecutionException, InterruptedException {
        // Given
        sqliteManager.initializeDatabase().get();
        String username = "testPlayer";
        String passwordHash = "$2a$10$abcdefghijklmnopqrstuvwxyz";
        String ipAddress = "192.168.1.100";
        sqliteManager.registerPlayer(username, passwordHash).get();
        
        // When
        sqliteManager.updateLastLogin(username, ipAddress).get();
        
        // Then
        PlayerData playerData = sqliteManager.getPlayerData(username).get();
        assertThat(playerData.getLastIpAddress()).isEqualTo(ipAddress);
        assertThat(playerData.getLastLogin()).isNotNull();
    }

    @Test
    @DisplayName("Should update failed attempts and lockout")
    void shouldUpdateFailedAttemptsAndLockout() throws ExecutionException, InterruptedException {
        // Given
        sqliteManager.initializeDatabase().get();
        String username = "testPlayer";
        String passwordHash = "$2a$10$abcdefghijklmnopqrstuvwxyz";
        sqliteManager.registerPlayer(username, passwordHash).get();
        
        int failedAttempts = 3;
        long lockoutUntil = System.currentTimeMillis() + 300000; // 5 minutes from now
        
        // When
        sqliteManager.updateFailedAttempts(username, failedAttempts, lockoutUntil).get();
        
        // Then
        PlayerData playerData = sqliteManager.getPlayerData(username).get();
        assertThat(playerData.getFailedAttempts()).isEqualTo(failedAttempts);
        assertThat(playerData.getLockoutUntil()).isEqualTo(lockoutUntil);
    }

    @Test
    @DisplayName("Should check username existence correctly")
    void shouldCheckUsernameExistenceCorrectly() throws ExecutionException, InterruptedException {
        // Given
        sqliteManager.initializeDatabase().get();
        String existingUsername = "existingPlayer";
        String nonExistingUsername = "nonExistingPlayer";
        String passwordHash = "$2a$10$abcdefghijklmnopqrstuvwxyz";
        
        sqliteManager.registerPlayer(existingUsername, passwordHash).get();
        
        // When & Then
        assertThat(sqliteManager.usernameExists(existingUsername).get()).isTrue();
        assertThat(sqliteManager.usernameExists(nonExistingUsername).get()).isFalse();
    }

    @Test
    @DisplayName("Should find similar usernames")
    void shouldFindSimilarUsernames() throws ExecutionException, InterruptedException {
        // Given
        sqliteManager.initializeDatabase().get();
        String passwordHash = "$2a$10$abcdefghijklmnopqrstuvwxyz";
        
        // Register similar usernames
        sqliteManager.registerPlayer("player1", passwordHash).get();
        sqliteManager.registerPlayer("player2", passwordHash).get();
        sqliteManager.registerPlayer("Player1", passwordHash).get();
        sqliteManager.registerPlayer("different", passwordHash).get();
        
        // When
        List<String> similarUsernames = sqliteManager.getSimilarUsernames("player1", 0.7).get();
        
        // Then
        assertThat(similarUsernames).isNotEmpty();
        assertThat(similarUsernames).contains("player1"); // Exact match
        // Note: The similarity algorithm may or may not include other similar names
        // depending on the threshold and implementation
    }

    @Test
    @DisplayName("Should log authentication attempts")
    void shouldLogAuthenticationAttempts() throws ExecutionException, InterruptedException {
        // Given
        sqliteManager.initializeDatabase().get();
        String username = "testPlayer";
        String ipAddress = "192.168.1.100";
        String action = "LOGIN";
        boolean success = true;
        
        // When
        sqliteManager.logAuthAttempt(username, ipAddress, action, success).get();
        
        // Then
        // Verify by checking auth stats
        DatabaseManager.AuthStats stats = sqliteManager.getAuthStats().get();
        assertThat(stats.totalLogins).isEqualTo(1);
    }

    @Test
    @DisplayName("Should get authentication statistics")
    void shouldGetAuthenticationStatistics() throws ExecutionException, InterruptedException {
        // Given
        sqliteManager.initializeDatabase().get();
        String username = "testPlayer";
        String passwordHash = "$2a$10$abcdefghijklmnopqrstuvwxyz";
        String ipAddress = "192.168.1.100";
        
        // Register a player
        sqliteManager.registerPlayer(username, passwordHash).get();
        sqliteManager.updateLastLogin(username, ipAddress).get();
        
        // Log some authentication attempts
        sqliteManager.logAuthAttempt(username, ipAddress, "LOGIN", true).get();
        sqliteManager.logAuthAttempt(username, ipAddress, "FAILED_LOGIN", false).get();
        
        // When
        DatabaseManager.AuthStats stats = sqliteManager.getAuthStats().get();
        
        // Then
        assertThat(stats.totalRegistrations).isEqualTo(1);
        assertThat(stats.totalLogins).isEqualTo(1);
        assertThat(stats.failedAttempts).isEqualTo(1);
        assertThat(stats.uniqueIPs).isEqualTo(1);
    }

    @Test
    @DisplayName("Should handle duplicate username registration")
    void shouldHandleDuplicateUsernameRegistration() throws ExecutionException, InterruptedException {
        // Given
        sqliteManager.initializeDatabase().get();
        String username = "testPlayer";
        String passwordHash = "$2a$10$abcdefghijklmnopqrstuvwxyz";
        
        // Register player first time
        boolean firstResult = sqliteManager.registerPlayer(username, passwordHash).get();
        assertThat(firstResult).isTrue();
        
        // When - try to register same username again
        boolean secondResult = sqliteManager.registerPlayer(username, passwordHash).get();
        
        // Then - should fail due to unique constraint
        assertThat(secondResult).isFalse();
    }

    @Test
    @DisplayName("Should handle operations on non-existent player gracefully")
    void shouldHandleOperationsOnNonExistentPlayerGracefully() throws ExecutionException, InterruptedException {
        // Given
        sqliteManager.initializeDatabase().get();
        String nonExistentUsername = "nonExistentPlayer";
        String newPassword = "$2a$10$newpassword";
        
        // When & Then
        // Update password on non-existent player should return false
        boolean updateResult = sqliteManager.updatePassword(nonExistentUsername, newPassword).get();
        assertThat(updateResult).isFalse();
        
        // Delete non-existent player should return false
        boolean deleteResult = sqliteManager.deletePlayer(nonExistentUsername).get();
        assertThat(deleteResult).isFalse();
        
        // Update last login should complete without error (void method)
        sqliteManager.updateLastLogin(nonExistentUsername, "192.168.1.1").get();
        
        // Update failed attempts should complete without error (void method)
        sqliteManager.updateFailedAttempts(nonExistentUsername, 1, 0).get();
    }

    @Test
    @DisplayName("Should perform database backup successfully")
    void shouldPerformDatabaseBackupSuccessfully() throws Exception {
        // Given
        sqliteManager.initializeDatabase().get();
        String username = "testPlayer";
        String passwordHash = "$2a$10$abcdefghijklmnopqrstuvwxyz";
        sqliteManager.registerPlayer(username, passwordHash).get();
        
        // When
        sqliteManager.performBackup();
        
        // Then
        // Check that backup directory exists
        File backupDir = new File(tempDir, "backups");
        assertThat(backupDir.exists()).isTrue();
        assertThat(backupDir.isDirectory()).isTrue();
        
        // Check that backup file was created
        File[] backupFiles = backupDir.listFiles((dir, name) -> name.startsWith("authdata_") && name.endsWith(".db"));
        assertThat(backupFiles).isNotNull();
        assertThat(backupFiles.length).isGreaterThan(0);
    }

    @Test
    @DisplayName("Should check database health")
    void shouldCheckDatabaseHealth() throws ExecutionException, InterruptedException {
        // Given
        sqliteManager.initializeDatabase().get();
        
        // When
        DatabaseConnectionRecovery.DatabaseHealthStatus healthStatus = sqliteManager.checkHealth().get();
        
        // Then
        assertThat(healthStatus.isHealthy()).isTrue();
        assertThat(healthStatus.getResponseTimeMs()).isGreaterThanOrEqualTo(0);
        assertThat(healthStatus.getLastError()).isNull();
    }

    @Test
    @DisplayName("Should test database connectivity")
    void shouldTestDatabaseConnectivity() throws ExecutionException, InterruptedException {
        // Given
        sqliteManager.initializeDatabase().get();
        
        // When
        boolean isConnected = sqliteManager.testConnectivity().get();
        
        // Then
        assertThat(isConnected).isTrue();
    }

    @Test
    @DisplayName("Should handle connection failure scenarios")
    void shouldHandleConnectionFailureScenarios() throws ExecutionException, InterruptedException {
        // Given
        sqliteManager.initializeDatabase().get();
        
        // Close the database to simulate connection failure
        sqliteManager.close();
        
        // When & Then
        // Operations should fail gracefully when database is closed
        boolean connectivityResult = sqliteManager.testConnectivity().get();
        assertThat(connectivityResult).isFalse();
        
        DatabaseConnectionRecovery.DatabaseHealthStatus healthStatus = sqliteManager.checkHealth().get();
        assertThat(healthStatus.isHealthy()).isFalse();
        assertThat(healthStatus.getLastError()).isNotNull();
    }
}