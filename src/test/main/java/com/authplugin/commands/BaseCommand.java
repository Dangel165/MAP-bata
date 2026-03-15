package com.authplugin.commands;

import com.authplugin.config.ConfigurationManager;
import com.authplugin.database.DatabaseManager;
import com.authplugin.security.SecurityManager;
import com.authplugin.session.SessionManager;
import com.authplugin.utils.AuthLogger;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Base class for all authentication commands
 * Provides common functionality like permission checking, validation, and messaging
 */
public abstract class BaseCommand implements CommandExecutor {
    
    protected final JavaPlugin plugin;
    protected final DatabaseManager databaseManager;
    protected final SessionManager sessionManager;
    protected final SecurityManager securityManager;
    protected final ConfigurationManager configManager;
    protected final AuthLogger logger;
    
    public BaseCommand(JavaPlugin plugin, DatabaseManager databaseManager, 
                      SessionManager sessionManager, SecurityManager securityManager,
                      ConfigurationManager configManager, AuthLogger logger) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.sessionManager = sessionManager;
        this.securityManager = securityManager;
        this.configManager = configManager;
        this.logger = logger;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            // Validate sender type if command requires player
            if (requiresPlayer() && !(sender instanceof Player)) {
                sendMessage(sender, getPlayerOnlyMessage());
                return true;
            }
            
            // Check permission
            if (!hasPermission(sender, getRequiredPermission())) {
                sendMessage(sender, getNoPermissionMessage());
                return true;
            }
            
            // Validate arguments
            if (!validateArguments(args)) {
                sendMessage(sender, getUsageMessage(command));
                return true;
            }
            
            // Execute the command
            return executeCommand(sender, command, label, args);
            
        } catch (Exception e) {
            logger.severe("Error executing command " + command.getName() + ": " + e.getMessage());
            sendMessage(sender, getErrorMessage());
            return true;
        }
    }
    
    /**
     * Execute the specific command logic
     */
    protected abstract boolean executeCommand(CommandSender sender, Command command, String label, String[] args);
    
    /**
     * Get the required permission for this command
     */
    protected abstract String getRequiredPermission();
    
    /**
     * Check if this command requires a player sender
     */
    protected abstract boolean requiresPlayer();
    
    /**
     * Validate command arguments
     */
    protected abstract boolean validateArguments(String[] args);
    
    /**
     * Get the usage message for this command
     */
    protected String getUsageMessage(Command command) {
        return "§cUsage: " + command.getUsage();
    }
    
    /**
     * Get the player-only message
     */
    protected String getPlayerOnlyMessage() {
        return configManager.getMessage("command.player-only", "§cThis command can only be used by players.");
    }
    
    /**
     * Get the no permission message
     */
    protected String getNoPermissionMessage() {
        return configManager.getMessage("command.no-permission", "§cYou do not have permission to use this command.");
    }
    
    /**
     * Get the generic error message
     */
    protected String getErrorMessage() {
        return configManager.getMessage("command.error", "§cAn error occurred while executing the command.");
    }
    
    /**
     * Check if sender has the required permission
     */
    protected boolean hasPermission(CommandSender sender, String permission) {
        if (permission == null || permission.isEmpty()) {
            return true;
        }
        return sender.hasPermission(permission);
    }
    
    /**
     * Send a message to the command sender
     */
    protected void sendMessage(CommandSender sender, String message) {
        if (message != null && !message.isEmpty()) {
            sender.sendMessage(message);
        }
    }
    
    /**
     * Get player from sender if it's a player
     */
    protected Player getPlayer(CommandSender sender) {
        return sender instanceof Player ? (Player) sender : null;
    }
    
    /**
     * Validate input against security manager
     */
    protected boolean isValidInput(String input) {
        return securityManager.validateInput(input);
    }
    
    /**
     * Check if player is authenticated
     */
    protected boolean isAuthenticated(Player player) {
        return sessionManager.isAuthenticated(player);
    }
    
    /**
     * Log command execution
     */
    protected void logCommandExecution(CommandSender sender, String command, boolean success) {
        String senderName = sender instanceof Player ? sender.getName() : "Console";
        String ip = sender instanceof Player ? ((Player) sender).getAddress().getAddress().getHostAddress() : "localhost";
        logger.logAuthAttempt(senderName, ip, command, success);
    }
}