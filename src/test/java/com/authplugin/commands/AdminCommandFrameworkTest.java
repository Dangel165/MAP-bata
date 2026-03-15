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
 * Unit tests for AdminCommand framework functionality
 * Tests the admin command permission checking and basic structure
 */
class AdminCommandFrameworkTest {
    
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
    
    private AdminCommand adminCommand;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Set up default mock behavior
        when(configManager.getMessage(anyString(), anyString())).thenAnswer(invocation -> 
            invocation.getArgument(1)); // Return fallback message
        
        adminCommand = new AdminCommand(plugin, databaseManager, sessionManager, 
            securityManager, configManager, logger);
    }
    
    @Test
    void testAdminCommandDoesNotRequirePlayer() {
        // Admin commands should be usable from console
        assertFalse(adminCommand.requiresPlayer());
    }
    
    @Test
    void testAdminCommandPermissionMapping() {
        // Test that admin command has proper permission structure
        String permission = adminCommand.getRequiredPermission();
        assertEquals("auth.admin", permission);
    }
    
    @Test
    void testConsoleCanUseAdminCommands() {
        // Console should be able to use admin commands (no player requirement)
        when(command.getName()).thenReturn("authstats");
        
        boolean result = adminCommand.onCommand(console, command, "authstats", new String[]{});
        
        assertTrue(result);
        // Verify no player-only message was sent
        verify(console, never()).sendMessage(contains("only be used by players"));
    }
    
    @Test
    void testPlayerWithoutPermissionCannotUseAdminCommands() {
        when(command.getName()).thenReturn("authreload");
        when(player.hasPermission("auth.admin.reload")).thenReturn(false);
        when(player.hasPermission("auth.admin")).thenReturn(false);
        
        boolean result = adminCommand.onCommand(player, command, "authreload", new String[]{});
        
        assertTrue(result);
        verify(player).sendMessage(contains("do not have permission"));
    }
    
    @Test
    void testPlayerWithPermissionCanUseAdminCommands() {
        when(command.getName()).thenReturn("authreload");
        when(player.hasPermission(anyString())).thenReturn(true);
        
        boolean result = adminCommand.onCommand(player, command, "authreload", new String[]{});
        
        // The command should return true indicating it was handled
        assertTrue(result);
        
        // Verify that some permission was checked (this is the main framework functionality)
        verify(player, atLeastOnce()).hasPermission(anyString());
    }
    
    @Test
    void testUnknownAdminCommandHandling() {
        when(command.getName()).thenReturn("unknowncommand");
        when(console.hasPermission(anyString())).thenReturn(true);
        
        boolean result = adminCommand.onCommand(console, command, "unknowncommand", new String[]{});
        
        assertTrue(result);
        verify(console).sendMessage(contains("Unknown admin command"));
    }
    
    @Test
    void testArgumentValidation() {
        // Test that admin command validates arguments properly
        assertTrue(adminCommand.validateArguments(new String[]{})); // Should always return true for base validation
    }
    
    @Test
    void testAdminCommandStructure() {
        // Verify that AdminCommand extends BaseCommand properly
        assertTrue(adminCommand instanceof BaseCommand);
        
        // Verify that it implements the required abstract methods
        assertNotNull(adminCommand.getRequiredPermission());
        assertNotNull(adminCommand.requiresPlayer());
        assertNotNull(adminCommand.validateArguments(new String[]{}));
    }
}