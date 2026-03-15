package com.authplugin.commands;

import com.authplugin.config.ConfigurationManager;
import com.authplugin.database.DatabaseManager;
import com.authplugin.security.SecurityManager;
import com.authplugin.session.SessionManager;
import com.authplugin.utils.AuthLogger;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages command registration and delegation
 * Coordinates between different command handlers and provides validation
 */
public class CommandManager {
    
    private final JavaPlugin plugin;
    private final DatabaseManager databaseManager;
    private final SessionManager sessionManager;
    private final SecurityManager securityManager;
    private final AuthLogger logger;
    private ConfigurationManager configManager;
    
    // Command handlers
    private final Map<String, BaseCommand> commandHandlers;
    private LoginCommand loginCommand;
    private RegisterCommand registerCommand;
    private AdminCommand adminCommand;
    
    public CommandManager(JavaPlugin plugin, DatabaseManager databaseManager, 
                         SessionManager sessionManager, SecurityManager securityManager,
                         ConfigurationManager configManager, AuthLogger logger) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.sessionManager = sessionManager;
        this.securityManager = securityManager;
        this.configManager = configManager;
        this.logger = logger;
        this.commandHandlers = new HashMap<>();
        
        initializeCommands();
    }
    
    /**
     * Initialize all command handlers
     */
    private void initializeCommands() {
        // Initialize command handlers
        loginCommand = new LoginCommand(plugin, databaseManager, sessionManager, 
            securityManager, configManager, logger);
        registerCommand = new RegisterCommand(plugin, databaseManager, sessionManager, 
            securityManager, configManager, logger);
        adminCommand = new AdminCommand(plugin, databaseManager, sessionManager, 
            securityManager, configManager, logger);
        
        // Map commands to handlers
        commandHandlers.put("login", loginCommand);
        commandHandlers.put("register", registerCommand);
        commandHandlers.put("authreload", adminCommand);
        commandHandlers.put("authunregister", adminCommand);
        commandHandlers.put("authchangepass", adminCommand);
        commandHandlers.put("authinfo", adminCommand);
        commandHandlers.put("authstats", adminCommand);
        commandHandlers.put("authlang", adminCommand);
        
        logger.debug("Command handlers initialized");
    }
    
    /**
     * Register all plugin commands with the server
     */
    public void registerCommands() {
        try {
            // Register player commands
            registerCommand("login", loginCommand);
            registerCommand("register", registerCommand);
            
            // Register admin commands
            registerCommand("authreload", adminCommand);
            registerCommand("authunregister", adminCommand);
            registerCommand("authchangepass", adminCommand);
            registerCommand("authinfo", adminCommand);
            registerCommand("authstats", adminCommand);
            registerCommand("authlang", adminCommand);
            
            logger.info("All commands registered successfully");
            
        } catch (Exception e) {
            logger.severe("Failed to register commands: " + e.getMessage());
            throw new RuntimeException("Command registration failed", e);
        }
    }
    
    /**
     * Register a single command with validation
     */
    private void registerCommand(String commandName, BaseCommand handler) {
        PluginCommand command = plugin.getCommand(commandName);
        if (command == null) {
            logger.warning("Command '" + commandName + "' not found in plugin.yml");
            return;
        }
        
        command.setExecutor(handler);
        logger.debug("Registered command: " + commandName);
    }
    
    /**
     * Handle command execution with additional validation and logging
     */
    public boolean handleCommand(CommandSender sender, Command command, String label, String[] args) {
        String commandName = command.getName().toLowerCase();
        
        // Log command attempt
        String senderName = sender.getName();
        String senderType = sender instanceof org.bukkit.entity.Player ? "Player" : "Console";
        logger.debug("Command attempt: " + commandName + " by " + senderType + " " + senderName);
        
        // Get command handler
        BaseCommand handler = commandHandlers.get(commandName);
        if (handler == null) {
            logger.warning("No handler found for command: " + commandName);
            sender.sendMessage("§cUnknown command. Type /help for available commands.");
            return true;
        }
        
        // Validate command state
        if (!isCommandEnabled(commandName)) {
            sender.sendMessage(configManager.getMessage("command.disabled", 
                "§cThis command is currently disabled."));
            return true;
        }
        
        // Execute command through handler
        try {
            return handler.onCommand(sender, command, label, args);
        } catch (Exception e) {
            logger.severe("Error executing command " + commandName + ": " + e.getMessage());
            sender.sendMessage("§cAn error occurred while executing the command.");
            return true;
        }
    }
    
    /**
     * Check if a command is enabled in configuration
     */
    private boolean isCommandEnabled(String commandName) {
        // Check if command is disabled in configuration
        return configManager.isCommandEnabled(commandName);
    }
    
    /**
     * Update configuration reference and refresh command handlers
     */
    public void updateConfiguration(ConfigurationManager configManager) {
        this.configManager = configManager;
        
        // Update all command handlers with new configuration
        if (loginCommand != null) {
            // Command handlers will get updated configuration through their references
            logger.debug("Updated configuration for command handlers");
        }
    }
    
    /**
     * Get command handler for testing purposes
     */
    public BaseCommand getCommandHandler(String commandName) {
        return commandHandlers.get(commandName.toLowerCase());
    }
    
    /**
     * Check if all required commands are properly registered
     */
    public boolean validateCommandRegistration() {
        String[] requiredCommands = {"login", "register", "authreload", "authunregister", 
                                   "authchangepass", "authinfo", "authstats", "authlang"};
        
        for (String commandName : requiredCommands) {
            PluginCommand command = plugin.getCommand(commandName);
            if (command == null) {
                logger.severe("Required command not found in plugin.yml: " + commandName);
                return false;
            }
            
            if (command.getExecutor() == null) {
                logger.severe("Command executor not set for: " + commandName);
                return false;
            }
        }
        
        logger.info("Command registration validation passed");
        return true;
    }
    
    /**
     * Get statistics about command usage
     */
    public Map<String, Integer> getCommandStatistics() {
        // This would be implemented with actual usage tracking
        // For now, return empty map
        return new HashMap<>();
    }
}