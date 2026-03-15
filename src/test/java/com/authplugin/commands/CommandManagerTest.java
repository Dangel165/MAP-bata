package com.authplugin.commands;

import com.authplugin.config.ConfigurationManager;
import com.authplugin.database.DatabaseManager;
import com.authplugin.security.SecurityManager;
import com.authplugin.session.SessionManager;
import com.authplugin.utils.AuthLogger;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CommandManager
 */
class CommandManagerTest {
    
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
    private PluginCommand loginCommand;
    
    @Mock
    private PluginCommand registerCommand;
    
    private CommandManager commandManager;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Mock plugin commands
        when(plugin.getCommand("login")).thenReturn(loginCommand);
        when(plugin.getCommand("register")).thenReturn(registerCommand);
        when(plugin.getCommand("authreload")).thenReturn(mock(PluginCommand.class));
        when(plugin.getCommand("authunregister")).thenReturn(mock(PluginCommand.class));
        when(plugin.getCommand("authchangepass")).thenReturn(mock(PluginCommand.class));
        when(plugin.getCommand("authinfo")).thenReturn(mock(PluginCommand.class));
        when(plugin.getCommand("authstats")).thenReturn(mock(PluginCommand.class));
        
        commandManager = new CommandManager(plugin, databaseManager, sessionManager, 
            securityManager, configManager, logger);
    }
    
    @Test
    void testCommandManagerInitialization() {
        assertNotNull(commandManager);
        verify(logger).debug("Command handlers initialized");
    }
    
    @Test
    void testRegisterCommands() {
        commandManager.registerCommands();
        
        // Verify that commands were registered
        verify(loginCommand).setExecutor(any(LoginCommand.class));
        verify(registerCommand).setExecutor(any(RegisterCommand.class));
        verify(logger).info("All commands registered successfully");
    }
    
    @Test
    void testGetCommandHandler() {
        BaseCommand loginHandler = commandManager.getCommandHandler("login");
        assertNotNull(loginHandler);
        assertTrue(loginHandler instanceof LoginCommand);
        
        BaseCommand registerHandler = commandManager.getCommandHandler("register");
        assertNotNull(registerHandler);
        assertTrue(registerHandler instanceof RegisterCommand);
        
        BaseCommand adminHandler = commandManager.getCommandHandler("authreload");
        assertNotNull(adminHandler);
        assertTrue(adminHandler instanceof AdminCommand);
    }
    
    @Test
    void testValidateCommandRegistration() {
        // Set up mock executors for the commands
        when(loginCommand.getExecutor()).thenReturn(mock(LoginCommand.class));
        when(registerCommand.getExecutor()).thenReturn(mock(RegisterCommand.class));
        
        PluginCommand authReloadCmd = mock(PluginCommand.class);
        when(authReloadCmd.getExecutor()).thenReturn(mock(AdminCommand.class));
        when(plugin.getCommand("authreload")).thenReturn(authReloadCmd);
        
        PluginCommand authUnregisterCmd = mock(PluginCommand.class);
        when(authUnregisterCmd.getExecutor()).thenReturn(mock(AdminCommand.class));
        when(plugin.getCommand("authunregister")).thenReturn(authUnregisterCmd);
        
        PluginCommand authChangePassCmd = mock(PluginCommand.class);
        when(authChangePassCmd.getExecutor()).thenReturn(mock(AdminCommand.class));
        when(plugin.getCommand("authchangepass")).thenReturn(authChangePassCmd);
        
        PluginCommand authInfoCmd = mock(PluginCommand.class);
        when(authInfoCmd.getExecutor()).thenReturn(mock(AdminCommand.class));
        when(plugin.getCommand("authinfo")).thenReturn(authInfoCmd);
        
        PluginCommand authStatsCmd = mock(PluginCommand.class);
        when(authStatsCmd.getExecutor()).thenReturn(mock(AdminCommand.class));
        when(plugin.getCommand("authstats")).thenReturn(authStatsCmd);
        
        // Register commands first
        commandManager.registerCommands();
        
        boolean isValid = commandManager.validateCommandRegistration();
        assertTrue(isValid);
        verify(logger).info("Command registration validation passed");
    }
    
    @Test
    void testUpdateConfiguration() {
        ConfigurationManager newConfigManager = mock(ConfigurationManager.class);
        commandManager.updateConfiguration(newConfigManager);
        
        verify(logger).debug("Updated configuration for command handlers");
    }
    
    @Test
    void testGetCommandStatistics() {
        var stats = commandManager.getCommandStatistics();
        assertNotNull(stats);
        assertTrue(stats.isEmpty()); // Empty for now as per implementation
    }
}