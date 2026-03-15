package com.authplugin.commands;

import com.authplugin.config.ConfigurationManager;
import com.authplugin.database.DatabaseManager;
import com.authplugin.security.SecurityManager;
import com.authplugin.session.SessionManager;
import com.authplugin.utils.AuthLogger;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BaseCommand
 */
class BaseCommandTest {
    
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
    private ConsoleCommandSender console;
    
    @Mock
    private Command command;
    
    private TestCommand testCommand;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        when(configManager.getMessage(anyString(), anyString())).thenAnswer(invocation -> 
            invocation.getArgument(1)); // Return fallback message
        
        testCommand = new TestCommand(plugin, databaseManager, sessionManager, 
            securityManager, configManager, logger);
    }
    
    @Test
    void testPlayerOnlyCommandWithPlayer() {
        testCommand.setRequiresPlayer(true);
        testCommand.setRequiredPermission("test.permission");
        testCommand.setValidateArgs(true);
        
        when(player.hasPermission("test.permission")).thenReturn(true);
        
        boolean result = testCommand.onCommand(player, command, "test", new String[]{"arg1"});
        
        assertTrue(result);
        assertTrue(testCommand.wasExecuted());
    }
    
    @Test
    void testPlayerOnlyCommandWithConsole() {
        testCommand.setRequiresPlayer(true);
        
        boolean result = testCommand.onCommand(console, command, "test", new String[]{"arg1"});
        
        assertTrue(result);
        assertFalse(testCommand.wasExecuted());
        verify(console).sendMessage(anyString()); // Should send player-only message
    }
    
    @Test
    void testPermissionCheck() {
        testCommand.setRequiredPermission("test.permission");
        testCommand.setValidateArgs(true);
        
        when(player.hasPermission("test.permission")).thenReturn(false);
        
        boolean result = testCommand.onCommand(player, command, "test", new String[]{"arg1"});
        
        assertTrue(result);
        assertFalse(testCommand.wasExecuted());
        verify(player).sendMessage(anyString()); // Should send no permission message
    }
    
    @Test
    void testArgumentValidation() {
        testCommand.setRequiredPermission("test.permission");
        testCommand.setValidateArgs(false); // Invalid args
        
        when(player.hasPermission("test.permission")).thenReturn(true);
        when(command.getUsage()).thenReturn("/test <arg>");
        
        boolean result = testCommand.onCommand(player, command, "test", new String[]{"arg1"});
        
        assertTrue(result);
        assertFalse(testCommand.wasExecuted());
        verify(player).sendMessage(contains("Usage:")); // Should send usage message
    }
    
    @Test
    void testSuccessfulExecution() {
        testCommand.setRequiredPermission("test.permission");
        testCommand.setValidateArgs(true);
        
        when(player.hasPermission("test.permission")).thenReturn(true);
        
        boolean result = testCommand.onCommand(player, command, "test", new String[]{"arg1"});
        
        assertTrue(result);
        assertTrue(testCommand.wasExecuted());
    }
    
    @Test
    void testExceptionHandling() {
        testCommand.setRequiredPermission("test.permission");
        testCommand.setValidateArgs(true);
        testCommand.setThrowException(true);
        
        when(player.hasPermission("test.permission")).thenReturn(true);
        
        boolean result = testCommand.onCommand(player, command, "test", new String[]{"arg1"});
        
        assertTrue(result);
        assertFalse(testCommand.wasExecuted());
        verify(logger).severe(contains("Error executing command"));
        verify(player).sendMessage(anyString()); // Should send error message
    }
    
    /**
     * Test implementation of BaseCommand for testing purposes
     */
    private static class TestCommand extends BaseCommand {
        private boolean requiresPlayer = false;
        private String requiredPermission = null;
        private boolean validateArgs = true;
        private boolean executed = false;
        private boolean throwException = false;
        
        public TestCommand(JavaPlugin plugin, DatabaseManager databaseManager, 
                          SessionManager sessionManager, SecurityManager securityManager,
                          ConfigurationManager configManager, AuthLogger logger) {
            super(plugin, databaseManager, sessionManager, securityManager, configManager, logger);
        }
        
        @Override
        protected boolean executeCommand(CommandSender sender, Command command, String label, String[] args) {
            if (throwException) {
                throw new RuntimeException("Test exception");
            }
            executed = true;
            return true;
        }
        
        @Override
        protected String getRequiredPermission() {
            return requiredPermission;
        }
        
        @Override
        protected boolean requiresPlayer() {
            return requiresPlayer;
        }
        
        @Override
        protected boolean validateArguments(String[] args) {
            return validateArgs;
        }
        
        // Test helper methods
        public void setRequiresPlayer(boolean requiresPlayer) {
            this.requiresPlayer = requiresPlayer;
        }
        
        public void setRequiredPermission(String requiredPermission) {
            this.requiredPermission = requiredPermission;
        }
        
        public void setValidateArgs(boolean validateArgs) {
            this.validateArgs = validateArgs;
        }
        
        public void setThrowException(boolean throwException) {
            this.throwException = throwException;
        }
        
        public boolean wasExecuted() {
            return executed;
        }
    }
}