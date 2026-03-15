package com.authplugin.commands;

import com.authplugin.config.ConfigurationManager;
import com.authplugin.database.DatabaseManager;
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

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * Unit tests for RegisterCommand
 */
@ExtendWith(MockitoExtension.class)
class RegisterCommandTest {
    
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
    
    @Mock
    private InetAddress inetAddress;
    
    private RegisterCommand registerCommand;
    
    @BeforeEach
    void setUp() {
        // Setup default mock behaviors with lenient stubbing
        lenient().when(configManager.getMessage(anyString(), anyString())).thenAnswer(
            invocation -> invocation.getArgument(1)); // Return fallback message
        
        lenient().when(configManager.getMinPasswordLength()).thenReturn(6);
        lenient().when(player.getName()).thenReturn("TestPlayer");
        lenient().when(player.getAddress()).thenReturn(address);
        lenient().when(address.getAddress()).thenReturn(inetAddress);
        lenient().when(inetAddress.getHostAddress()).thenReturn("127.0.0.1");
        lenient().when(player.hasPermission("auth.register")).thenReturn(true);
        lenient().when(command.getUsage()).thenReturn("/register <password> <confirmPassword>");
        
        registerCommand = new RegisterCommand(plugin, databaseManager, sessionManager, 
            securityManager, configManager, logger);
    }
    
    @Test
    void testSuccessfulRegistration() {
        // Arrange
        String[] args = {"password123", "password123"};
        when(sessionManager.isAuthenticated(player)).thenReturn(false);
        when(securityManager.isRateLimited("127.0.0.1")).thenReturn(false);
        when(securityManager.validateInput("password123")).thenReturn(true);
        when(databaseManager.playerExists("TestPlayer")).thenReturn(CompletableFuture.completedFuture(false));
        when(securityManager.hashPassword("password123")).thenReturn("hashedPassword");
        when(databaseManager.registerPlayer("TestPlayer", "hashedPassword", "127.0.0.1"))
            .thenReturn(CompletableFuture.completedFuture(true));
        
        // Act
        boolean result = registerCommand.onCommand(player, command, "register", args);
        
        // Assert
        assertTrue(result);
        verify(sessionManager, timeout(1000)).createSession(player);
        verify(player, timeout(1000)).sendMessage(contains("Registration successful"));
    }
    
    @Test
    void testRegistrationWithMismatchedPasswords() {
        // Arrange
        String[] args = {"password123", "differentPassword"};
        when(sessionManager.isAuthenticated(player)).thenReturn(false);
        when(securityManager.isRateLimited("127.0.0.1")).thenReturn(false);
        
        // Act
        boolean result = registerCommand.onCommand(player, command, "register", args);
        
        // Assert
        assertTrue(result);
        verify(player).sendMessage(contains("Passwords do not match"));
        verify(databaseManager, never()).registerPlayer(anyString(), anyString(), anyString());
    }
    
    @Test
    void testRegistrationWithShortPassword() {
        // Arrange
        String[] args = {"123", "123"};
        when(sessionManager.isAuthenticated(player)).thenReturn(false);
        when(securityManager.isRateLimited("127.0.0.1")).thenReturn(false);
        
        // Act
        boolean result = registerCommand.onCommand(player, command, "register", args);
        
        // Assert
        assertTrue(result);
        verify(player).sendMessage(contains("Password must be at least 6 characters long"));
        verify(databaseManager, never()).registerPlayer(anyString(), anyString(), anyString());
    }
    
    @Test
    void testRegistrationWhenAlreadyAuthenticated() {
        // Arrange
        String[] args = {"password123", "password123"};
        when(sessionManager.isAuthenticated(player)).thenReturn(true);
        
        // Act
        boolean result = registerCommand.onCommand(player, command, "register", args);
        
        // Assert
        assertTrue(result);
        verify(player).sendMessage(contains("already logged in"));
        verify(databaseManager, never()).registerPlayer(anyString(), anyString(), anyString());
    }
    
    @Test
    void testRegistrationWhenRateLimited() {
        // Arrange
        String[] args = {"password123", "password123"};
        when(sessionManager.isAuthenticated(player)).thenReturn(false);
        when(securityManager.isRateLimited("127.0.0.1")).thenReturn(true);
        
        // Act
        boolean result = registerCommand.onCommand(player, command, "register", args);
        
        // Assert
        assertTrue(result);
        verify(player).sendMessage(contains("Too many attempts"));
        verify(databaseManager, never()).registerPlayer(anyString(), anyString(), anyString());
    }
    
    @Test
    void testRegistrationWithExistingPlayer() {
        // Arrange
        String[] args = {"password123", "password123"};
        when(sessionManager.isAuthenticated(player)).thenReturn(false);
        when(securityManager.isRateLimited("127.0.0.1")).thenReturn(false);
        when(securityManager.validateInput("password123")).thenReturn(true);
        when(databaseManager.playerExists("TestPlayer")).thenReturn(CompletableFuture.completedFuture(true));
        
        // Act
        boolean result = registerCommand.onCommand(player, command, "register", args);
        
        // Assert
        assertTrue(result);
        verify(player, timeout(1000)).sendMessage(contains("already registered"));
        verify(databaseManager, never()).registerPlayer(anyString(), anyString(), anyString());
    }
    
    @Test
    void testRegistrationWithInvalidInput() {
        // Arrange
        String[] args = {"password123", "password123"};
        when(sessionManager.isAuthenticated(player)).thenReturn(false);
        when(securityManager.isRateLimited("127.0.0.1")).thenReturn(false);
        when(securityManager.validateInput("password123")).thenReturn(false);
        
        // Act
        boolean result = registerCommand.onCommand(player, command, "register", args);
        
        // Assert
        assertTrue(result);
        verify(player).sendMessage(contains("Invalid password format"));
        verify(databaseManager, never()).registerPlayer(anyString(), anyString(), anyString());
    }
    
    @Test
    void testArgumentValidation() {
        // Test with insufficient arguments
        String[] args = {"password123"};
        
        boolean result = registerCommand.onCommand(player, command, "register", args);
        
        assertTrue(result);
        verify(player).sendMessage(contains("Usage:"));
    }
    
    @Test
    void testPermissionCheck() {
        // Arrange
        String[] args = {"password123", "password123"};
        when(player.hasPermission("auth.register")).thenReturn(false);
        
        // Act
        boolean result = registerCommand.onCommand(player, command, "register", args);
        
        // Assert
        assertTrue(result);
        verify(player).sendMessage(contains("permission"));
    }
}