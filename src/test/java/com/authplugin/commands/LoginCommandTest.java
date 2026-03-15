package com.authplugin.commands;

import com.authplugin.config.ConfigurationManager;
import com.authplugin.database.DatabaseManager;
import com.authplugin.models.PlayerData;
import com.authplugin.security.SecurityManager;
import com.authplugin.session.SessionManager;
import com.authplugin.utils.AuthLogger;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for LoginCommand
 */
@ExtendWith(MockitoExtension.class)
class LoginCommandTest {
    
    @Mock
    private JavaPlugin plugin;
    
    @Mock
    private DatabaseManager databaseManager;
    
    @Mock
    private SessionManager sessionManager;
    
    @Mock
    private SecurityManager securityManager;
    
    @Mock
    private ConfigurationManager configManager;
    
    @Mock
    private AuthLogger logger;
    
    @Mock
    private Player player;
    
    @Mock
    private Command command;
    
    @Mock
    private InetSocketAddress address;
    
    private LoginCommand loginCommand;
    
    @BeforeEach
    void setUp() {
        loginCommand = new LoginCommand(plugin, databaseManager, sessionManager, 
            securityManager, configManager, logger);
        
        // Setup common mocks
        lenient().when(player.getName()).thenReturn("TestPlayer");
        lenient().when(player.getAddress()).thenReturn(address);
        lenient().when(address.getAddress()).thenReturn(java.net.InetAddress.getLoopbackAddress());
        lenient().when(configManager.getMessage(anyString(), anyString())).thenAnswer(invocation -> invocation.getArgument(1));
        lenient().when(player.hasPermission("auth.login")).thenReturn(true);
    }
    
    @Test
    void shouldRejectAlreadyAuthenticatedPlayer() {
        // Given
        when(sessionManager.isAuthenticated(player)).thenReturn(true);
        String[] args = {"password123"};
        
        // When
        boolean result = loginCommand.onCommand(player, command, "login", args);
        
        // Then
        assertTrue(result);
        verify(player).sendMessage(contains("already logged in"));
        verify(databaseManager, never()).getPlayerData(anyString());
    }
    
    @Test
    void shouldRejectRateLimitedPlayer() {
        // Given
        when(sessionManager.isAuthenticated(player)).thenReturn(false);
        when(securityManager.isRateLimited(anyString())).thenReturn(true);
        when(securityManager.validateInput(anyString())).thenReturn(true);
        String[] args = {"password123"};
        
        // When
        boolean result = loginCommand.onCommand(player, command, "login", args);
        
        // Then
        assertTrue(result);
        verify(player).sendMessage(contains("Too many failed attempts"));
        verify(databaseManager, never()).getPlayerData(anyString());
    }
    
    @Test
    void shouldRejectInvalidPasswordInput() {
        // Given
        when(sessionManager.isAuthenticated(player)).thenReturn(false);
        when(securityManager.isRateLimited(anyString())).thenReturn(false);
        when(securityManager.validateInput(anyString())).thenReturn(false);
        String[] args = {"invalid<>password"};
        
        // When
        boolean result = loginCommand.onCommand(player, command, "login", args);
        
        // Then
        assertTrue(result);
        verify(player).sendMessage(contains("Invalid password format"));
        verify(databaseManager, never()).getPlayerData(anyString());
    }
    
    @Test
    void shouldHandleUnregisteredPlayer() {
        // Given
        when(sessionManager.isAuthenticated(player)).thenReturn(false);
        when(securityManager.isRateLimited(anyString())).thenReturn(false);
        when(securityManager.validateInput(anyString())).thenReturn(true);
        when(databaseManager.getPlayerData("TestPlayer")).thenReturn(CompletableFuture.completedFuture(null));
        String[] args = {"password123"};
        
        // When
        boolean result = loginCommand.onCommand(player, command, "login", args);
        
        // Then
        assertTrue(result);
        // Note: The actual message sending happens asynchronously, so we can't verify it directly
        verify(databaseManager).getPlayerData("TestPlayer");
    }
    
    @Test
    void shouldHandleLockedAccount() {
        // Given
        PlayerData playerData = mock(PlayerData.class);
        when(playerData.isLocked()).thenReturn(true);
        when(playerData.getLockoutRemaining()).thenReturn(300000L); // 5 minutes
        
        when(sessionManager.isAuthenticated(player)).thenReturn(false);
        when(securityManager.isRateLimited(anyString())).thenReturn(false);
        when(securityManager.validateInput(anyString())).thenReturn(true);
        when(databaseManager.getPlayerData("TestPlayer")).thenReturn(CompletableFuture.completedFuture(playerData));
        String[] args = {"password123"};
        
        // When
        boolean result = loginCommand.onCommand(player, command, "login", args);
        
        // Then
        assertTrue(result);
        verify(databaseManager).getPlayerData("TestPlayer");
    }
    
    @Test
    void shouldHandleSuccessfulLogin() {
        // Given
        PlayerData playerData = mock(PlayerData.class);
        when(playerData.isLocked()).thenReturn(false);
        when(playerData.getPasswordHash()).thenReturn("$2a$10$hashedPassword");
        when(playerData.getFailedAttempts()).thenReturn(0);
        
        when(sessionManager.isAuthenticated(player)).thenReturn(false);
        when(securityManager.isRateLimited(anyString())).thenReturn(false);
        when(securityManager.validateInput(anyString())).thenReturn(true);
        when(securityManager.verifyPassword("password123", "$2a$10$hashedPassword")).thenReturn(true);
        when(databaseManager.getPlayerData("TestPlayer")).thenReturn(CompletableFuture.completedFuture(playerData));
        when(databaseManager.updateLastLogin(anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
        String[] args = {"password123"};
        
        // When
        boolean result = loginCommand.onCommand(player, command, "login", args);
        
        // Then
        assertTrue(result);
        verify(databaseManager).getPlayerData("TestPlayer");
        verify(securityManager).verifyPassword("password123", "$2a$10$hashedPassword");
    }
    
    @Test
    void shouldHandleFailedLogin() {
        // Given
        PlayerData playerData = mock(PlayerData.class);
        when(playerData.isLocked()).thenReturn(false);
        when(playerData.getPasswordHash()).thenReturn("$2a$10$hashedPassword");
        when(playerData.getFailedAttempts()).thenReturn(1);
        
        when(sessionManager.isAuthenticated(player)).thenReturn(false);
        when(securityManager.isRateLimited(anyString())).thenReturn(false);
        when(securityManager.validateInput(anyString())).thenReturn(true);
        when(securityManager.verifyPassword("wrongpassword", "$2a$10$hashedPassword")).thenReturn(false);
        when(databaseManager.getPlayerData("TestPlayer")).thenReturn(CompletableFuture.completedFuture(playerData));
        when(databaseManager.incrementFailedAttempts(anyString())).thenReturn(CompletableFuture.completedFuture(null));
        when(configManager.getMaxFailedAttempts()).thenReturn(3);
        String[] args = {"wrongpassword"};
        
        // When
        boolean result = loginCommand.onCommand(player, command, "login", args);
        
        // Then
        assertTrue(result);
        verify(databaseManager).getPlayerData("TestPlayer");
        verify(securityManager).verifyPassword("wrongpassword", "$2a$10$hashedPassword");
    }
    
    @Test
    void shouldLockAccountAfterMaxFailedAttempts() {
        // Given
        PlayerData playerData = mock(PlayerData.class);
        when(playerData.isLocked()).thenReturn(false);
        when(playerData.getPasswordHash()).thenReturn("$2a$10$hashedPassword");
        when(playerData.getFailedAttempts()).thenReturn(2); // This will be the 3rd attempt
        
        when(sessionManager.isAuthenticated(player)).thenReturn(false);
        when(securityManager.isRateLimited(anyString())).thenReturn(false);
        when(securityManager.validateInput(anyString())).thenReturn(true);
        when(securityManager.verifyPassword("wrongpassword", "$2a$10$hashedPassword")).thenReturn(false);
        when(databaseManager.getPlayerData("TestPlayer")).thenReturn(CompletableFuture.completedFuture(playerData));
        when(databaseManager.lockAccount(anyString(), anyLong())).thenReturn(CompletableFuture.completedFuture(null));
        when(configManager.getMaxFailedAttempts()).thenReturn(3);
        when(configManager.getLockoutDuration()).thenReturn(300000L); // 5 minutes
        String[] args = {"wrongpassword"};
        
        // When
        boolean result = loginCommand.onCommand(player, command, "login", args);
        
        // Then
        assertTrue(result);
        verify(databaseManager).getPlayerData("TestPlayer");
        verify(databaseManager).lockAccount("TestPlayer", 300000L);
    }
    
    @Test
    void shouldValidateArgumentsCorrectly() {
        // Test with no arguments
        String[] noArgs = {};
        assertFalse(loginCommand.validateArguments(noArgs));
        
        // Test with empty argument
        String[] emptyArgs = {""};
        assertFalse(loginCommand.validateArguments(emptyArgs));
        
        // Test with null argument
        String[] nullArgs = {null};
        assertFalse(loginCommand.validateArguments(nullArgs));
        
        // Test with valid argument
        String[] validArgs = {"password123"};
        assertTrue(loginCommand.validateArguments(validArgs));
        
        // Test with too many arguments
        String[] tooManyArgs = {"password123", "extra"};
        assertFalse(loginCommand.validateArguments(tooManyArgs));
    }
    
    @Test
    void shouldRequirePlayerSender() {
        assertTrue(loginCommand.requiresPlayer());
    }
    
    @Test
    void shouldHaveCorrectPermission() {
        assertEquals("auth.login", loginCommand.getRequiredPermission());
    }
}