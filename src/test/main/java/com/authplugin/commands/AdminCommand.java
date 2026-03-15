package com.authplugin.commands;

import com.authplugin.config.ConfigurationManager;
import com.authplugin.database.DatabaseManager;
import com.authplugin.models.PlayerData;
import com.authplugin.security.SecurityManager;
import com.authplugin.session.SessionManager;
import com.authplugin.utils.AuthLogger;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.text.SimpleDateFormat;
import java.util.concurrent.CompletableFuture;

/**
 * Handles all administrative commands for the authentication plugin
 */
public class AdminCommand extends BaseCommand {
    
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    
    public AdminCommand(JavaPlugin plugin, DatabaseManager databaseManager, 
                       SessionManager sessionManager, SecurityManager securityManager,
                       ConfigurationManager configManager, AuthLogger logger) {
        super(plugin, databaseManager, sessionManager, securityManager, configManager, logger);
    }
    
    @Override
    protected boolean executeCommand(CommandSender sender, Command command, String label, String[] args) {
        String commandName = command.getName().toLowerCase();
        
        // Check specific admin permissions for each command
        String requiredPermission = getCommandPermission(commandName);
        if (!hasPermission(sender, requiredPermission)) {
            sendMessage(sender, getNoPermissionMessage());
            logCommandExecution(sender, "ADMIN_" + commandName.toUpperCase() + "_NO_PERMISSION", false);
            return true;
        }
        
        switch (commandName) {
            case "authreload":
                return handleReloadCommand(sender);
            case "authunregister":
                return handleUnregisterCommand(sender, args);
            case "authchangepass":
                return handleChangePasswordCommand(sender, args);
            case "authinfo":
                return handleInfoCommand(sender, args);
            case "authstats":
                return handleStatsCommand(sender);
            case "authlang":
                return handleLangCommand(sender, args);
            default:
                sendMessage(sender, "§cUnknown admin command: " + commandName);
                return true;
        }
    }
    
    private boolean handleReloadCommand(CommandSender sender) {
        try {
            if (plugin instanceof com.authplugin.MinecraftAuthPlugin) {
                ((com.authplugin.MinecraftAuthPlugin) plugin).reloadPlugin();
                sendMessage(sender, configManager.getMessage("admin.reload.success", 
                    "§aPlugin configuration reloaded successfully."));
                logCommandExecution(sender, "ADMIN_RELOAD_SUCCESS", true);
                logger.info("Plugin configuration reloaded by " + sender.getName());
            } else {
                sendMessage(sender, "§cFailed to reload plugin configuration.");
                logCommandExecution(sender, "ADMIN_RELOAD_FAILED", false);
            }
        } catch (Exception e) {
            sendMessage(sender, configManager.getMessage("admin.reload.failed", 
                "§cFailed to reload configuration: " + e.getMessage()));
            logCommandExecution(sender, "ADMIN_RELOAD_ERROR", false);
            logger.severe("Error reloading configuration: " + e.getMessage());
        }
        return true;
    }
    
    private boolean handleUnregisterCommand(CommandSender sender, String[] args) {
        if (!validateCommandArguments("authunregister", args)) {
            sendMessage(sender, getCommandUsage("authunregister"));
            return true;
        }
        
        String targetPlayer = args[0];
        
        CompletableFuture<Boolean> existsFuture = databaseManager.playerExists(targetPlayer);
        
        existsFuture.thenAccept(exists -> {
            if (!exists) {
                sendMessage(sender, configManager.getMessage("admin.unregister.not-found", 
                    "§cPlayer " + targetPlayer + " is not registered."));
                logCommandExecution(sender, "ADMIN_UNREGISTER_NOT_FOUND", false);
                return;
            }
            
            CompletableFuture<Boolean> deleteFuture = databaseManager.deletePlayer(targetPlayer);
            
            deleteFuture.thenAccept(success -> {
                if (success) {
                    // Invalidate session if player is online
                    Player onlinePlayer = Bukkit.getPlayer(targetPlayer);
                    if (onlinePlayer != null && sessionManager.isAuthenticated(onlinePlayer)) {
                        sessionManager.invalidateSession(onlinePlayer);
                    }
                    
                    sendMessage(sender, configManager.getMessage("admin.unregister.success", 
                        "§aPlayer " + targetPlayer + " has been unregistered successfully."));
                    logCommandExecution(sender, "ADMIN_UNREGISTER_SUCCESS", true);
                    logger.info("Player " + targetPlayer + " unregistered by " + sender.getName());
                } else {
                    sendMessage(sender, configManager.getMessage("admin.unregister.failed", 
                        "§cFailed to unregister player " + targetPlayer + "."));
                    logCommandExecution(sender, "ADMIN_UNREGISTER_FAILED", false);
                }
            }).exceptionally(throwable -> {
                sendMessage(sender, "§cDatabase error occurred while unregistering player.");
                logCommandExecution(sender, "ADMIN_UNREGISTER_ERROR", false);
                logger.severe("Database error during unregister: " + throwable.getMessage());
                return null;
            });
            
        }).exceptionally(throwable -> {
            sendMessage(sender, "§cDatabase error occurred while checking player.");
            logCommandExecution(sender, "ADMIN_UNREGISTER_ERROR", false);
            logger.severe("Database error during player check: " + throwable.getMessage());
            return null;
        });
        
        return true;
    }
    
    private boolean handleChangePasswordCommand(CommandSender sender, String[] args) {
        if (!validateCommandArguments("authchangepass", args)) {
            sendMessage(sender, getCommandUsage("authchangepass"));
            return true;
        }
        
        String targetPlayer = args[0];
        String newPassword = args[1];
        
        // Validate password length
        int minLength = configManager.getMinPasswordLength();
        if (newPassword.length() < minLength) {
            sendMessage(sender, configManager.getMessage("admin.changepass.too-short", 
                "§cPassword must be at least " + minLength + " characters long."));
            return true;
        }
        
        CompletableFuture<Boolean> existsFuture = databaseManager.playerExists(targetPlayer);
        
        existsFuture.thenAccept(exists -> {
            if (!exists) {
                sendMessage(sender, configManager.getMessage("admin.changepass.not-found", 
                    "§cPlayer " + targetPlayer + " is not registered."));
                logCommandExecution(sender, "ADMIN_CHANGEPASS_NOT_FOUND", false);
                return;
            }
            
            String passwordHash = securityManager.hashPassword(newPassword);
            CompletableFuture<Boolean> updateFuture = databaseManager.updatePassword(targetPlayer, passwordHash);
            
            updateFuture.thenAccept(success -> {
                if (success) {
                    // Invalidate session if player is online (force re-login)
                    Player onlinePlayer = Bukkit.getPlayer(targetPlayer);
                    if (onlinePlayer != null && sessionManager.isAuthenticated(onlinePlayer)) {
                        sessionManager.invalidateSession(onlinePlayer);
                        onlinePlayer.sendMessage(configManager.getMessage("admin.changepass.logout", 
                            "§eYour password has been changed by an administrator. Please log in again."));
                    }
                    
                    sendMessage(sender, configManager.getMessage("admin.changepass.success", 
                        "§aPassword for " + targetPlayer + " has been changed successfully."));
                    logCommandExecution(sender, "ADMIN_CHANGEPASS_SUCCESS", true);
                    logger.info("Password changed for " + targetPlayer + " by " + sender.getName());
                } else {
                    sendMessage(sender, configManager.getMessage("admin.changepass.failed", 
                        "§cFailed to change password for " + targetPlayer + "."));
                    logCommandExecution(sender, "ADMIN_CHANGEPASS_FAILED", false);
                }
            }).exceptionally(throwable -> {
                sendMessage(sender, "§cDatabase error occurred while changing password.");
                logCommandExecution(sender, "ADMIN_CHANGEPASS_ERROR", false);
                logger.severe("Database error during password change: " + throwable.getMessage());
                return null;
            });
            
        }).exceptionally(throwable -> {
            sendMessage(sender, "§cDatabase error occurred while checking player.");
            logCommandExecution(sender, "ADMIN_CHANGEPASS_ERROR", false);
            logger.severe("Database error during player check: " + throwable.getMessage());
            return null;
        });
        
        return true;
    }
    
    private boolean handleInfoCommand(CommandSender sender, String[] args) {
        if (!validateCommandArguments("authinfo", args)) {
            sendMessage(sender, getCommandUsage("authinfo"));
            return true;
        }
        
        String targetPlayer = args[0];
        
        CompletableFuture<PlayerData> playerDataFuture = databaseManager.getPlayerData(targetPlayer);
        
        playerDataFuture.thenAccept(playerData -> {
            if (playerData == null) {
                sendMessage(sender, configManager.getMessage("admin.info.not-found", 
                    "§cPlayer " + targetPlayer + " is not registered."));
                logCommandExecution(sender, "ADMIN_INFO_NOT_FOUND", false);
                return;
            }
            
            // Format player information
            sendMessage(sender, "§6=== Player Information: " + targetPlayer + " ===");
            sendMessage(sender, "§eRegistration Date: §f" + dateFormat.format(playerData.getRegistrationDate()));
            
            if (playerData.getLastLogin() != null) {
                sendMessage(sender, "§eLast Login: §f" + dateFormat.format(playerData.getLastLogin()));
            } else {
                sendMessage(sender, "§eLast Login: §fNever");
            }
            
            if (playerData.getLastIpAddress() != null) {
                sendMessage(sender, "§eLast IP: §f" + playerData.getLastIpAddress());
            }
            
            sendMessage(sender, "§eFailed Attempts: §f" + playerData.getFailedAttempts());
            
            if (playerData.isLocked()) {
                long remaining = playerData.getLockoutRemaining();
                sendMessage(sender, "§cAccount Status: §fLocked (" + formatTime(remaining) + " remaining)");
            } else {
                sendMessage(sender, "§aAccount Status: §fActive");
            }
            
            // Check if player is currently online and authenticated
            Player onlinePlayer = Bukkit.getPlayer(targetPlayer);
            if (onlinePlayer != null) {
                boolean authenticated = sessionManager.isAuthenticated(onlinePlayer);
                sendMessage(sender, "§eOnline Status: §fOnline " + (authenticated ? "(Authenticated)" : "(Not Authenticated)"));
            } else {
                sendMessage(sender, "§eOnline Status: §fOffline");
            }
            
            logCommandExecution(sender, "ADMIN_INFO_SUCCESS", true);
            
        }).exceptionally(throwable -> {
            sendMessage(sender, "§cDatabase error occurred while retrieving player information.");
            logCommandExecution(sender, "ADMIN_INFO_ERROR", false);
            logger.severe("Database error during info retrieval: " + throwable.getMessage());
            return null;
        });
        
        return true;
    }
    
    private boolean handleStatsCommand(CommandSender sender) {
        try {
            sendMessage(sender, "§6=== Authentication Plugin Statistics ===");
            
            if (plugin instanceof com.authplugin.MinecraftAuthPlugin) {
                com.authplugin.MinecraftAuthPlugin authPlugin = (com.authplugin.MinecraftAuthPlugin) plugin;
                
                sendMessage(sender, "§eTotal Registrations: §f" + authPlugin.getTotalRegistrations());
                sendMessage(sender, "§eTotal Logins: §f" + authPlugin.getTotalLogins());
                sendMessage(sender, "§eFailed Attempts: §f" + authPlugin.getFailedAttempts());
                
                long uptimeMillis = authPlugin.getUptimeMillis();
                String uptime = formatTime(uptimeMillis);
                sendMessage(sender, "§ePlugin Uptime: §f" + uptime);
                
                int activeSessions = sessionManager.getActiveSessionCount();
                sendMessage(sender, "§eActive Sessions: §f" + activeSessions);
                
                // Database statistics
                CompletableFuture<Integer> totalPlayersFuture = databaseManager.getTotalPlayers();
                totalPlayersFuture.thenAccept(totalPlayers -> {
                    sendMessage(sender, "§eRegistered Players: §f" + totalPlayers);
                }).exceptionally(throwable -> {
                    sendMessage(sender, "§cError retrieving database statistics.");
                    return null;
                });
            }
            
            logCommandExecution(sender, "ADMIN_STATS_SUCCESS", true);
            
        } catch (Exception e) {
            sendMessage(sender, "§cError retrieving plugin statistics.");
            logCommandExecution(sender, "ADMIN_STATS_ERROR", false);
            logger.severe("Error retrieving stats: " + e.getMessage());
        }
        
        return true;
    }
    
    private boolean handleLangCommand(CommandSender sender, String[] args) {
        if (!validateCommandArguments("authlang", args)) {
            sendMessage(sender, getCommandUsage("authlang"));
            return true;
        }

        String lang = args[0].toLowerCase();
        if (!lang.equals("en") && !lang.equals("ko")) {
            sendMessage(sender, "§c지원하지 않는 언어입니다. 사용 가능: en, ko");
            return true;
        }

        String langName = lang.equals("ko") ? "한국어" : "English";

        if (sender instanceof Player) {
            // Player: change only their own language (session-based)
            Player player = (Player) sender;
            configManager.setPlayerLanguage(player.getUniqueId(), lang);
            sendMessage(sender, "§a내 언어가 §e" + langName + " §a(" + lang + ")§a(으)로 변경되었습니다.");
            logCommandExecution(sender, "LANG_CHANGED_PERSONAL_" + lang.toUpperCase(), true);
        } else {
            // Console: change server-wide language
            configManager.setLanguage(lang);
            if (plugin instanceof com.authplugin.MinecraftAuthPlugin) {
                com.authplugin.MinecraftAuthPlugin authPlugin = (com.authplugin.MinecraftAuthPlugin) plugin;
                authPlugin.getMessageManager().reload(lang);
            }
            sendMessage(sender, "§a서버 언어가 §e" + langName + " §a(" + lang + ")§a(으)로 변경되었습니다.");
            logCommandExecution(sender, "ADMIN_LANG_CHANGED_" + lang.toUpperCase(), true);
            logger.info("Server language changed to " + lang + " by " + sender.getName());
        }
        return true;
    }

    private String formatTime(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        
        seconds = seconds % 60;
        minutes = minutes % 60;
        hours = hours % 24;
        
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (seconds > 0 || sb.length() == 0) sb.append(seconds).append("s");
        
        return sb.toString().trim();
    }
    
    @Override
    protected String getRequiredPermission() {
        // This method is not used since we check permissions per command
        return "auth.admin";
    }
    
    /**
     * Get the specific permission required for each admin command
     */
    private String getCommandPermission(String commandName) {
        switch (commandName.toLowerCase()) {
            case "authreload":
                return "auth.admin.reload";
            case "authunregister":
                return "auth.admin.unregister";
            case "authchangepass":
                return "auth.admin.changepass";
            case "authinfo":
                return "auth.admin.info";
            case "authstats":
                return "auth.admin.stats";
            case "authlang":
                return "auth.admin.lang";
            default:
                return "auth.admin";
        }
    }
    
    @Override
    protected boolean requiresPlayer() {
        return false; // Admin commands can be used from console
    }
    
    @Override
    protected boolean validateArguments(String[] args) {
        // Validation is done per command in the executeCommand method
        // This allows for command-specific argument validation
        return true;
    }
    
    /**
     * Validate arguments for specific admin commands
     */
    private boolean validateCommandArguments(String commandName, String[] args) {
        switch (commandName.toLowerCase()) {
            case "authreload":
            case "authstats":
                return args.length == 0;
            case "authunregister":
            case "authinfo":
                return args.length == 1 && isValidInput(args[0]);
            case "authchangepass":
                return args.length == 2 && isValidInput(args[0]) && isValidInput(args[1]);
            case "authlang":
                return args.length == 1;
            default:
                return true;
        }
    }
    
    /**
     * Check if the sender has admin privileges (either has auth.admin or is console)
     */
    private boolean hasAdminPrivileges(CommandSender sender) {
        // Console always has admin privileges
        if (!(sender instanceof Player)) {
            return true;
        }
        
        // Check for general admin permission or specific command permissions
        return sender.hasPermission("auth.admin") || 
               sender.hasPermission("auth.admin.reload") ||
               sender.hasPermission("auth.admin.unregister") ||
               sender.hasPermission("auth.admin.changepass") ||
               sender.hasPermission("auth.admin.info") ||
               sender.hasPermission("auth.admin.stats");
    }
    
    /**
     * Get usage message for specific admin commands
     */
    private String getCommandUsage(String commandName) {
        switch (commandName.toLowerCase()) {
            case "authreload":
                return "§cUsage: /authreload";
            case "authunregister":
                return "§cUsage: /authunregister <player>";
            case "authchangepass":
                return "§cUsage: /authchangepass <player> <newPassword>";
            case "authinfo":
                return "§cUsage: /authinfo <player>";
            case "authstats":
                return "§cUsage: /authstats";
            case "authlang":
                return "§cUsage: /authlang <en|ko>";
            default:
                return "§cInvalid command usage";
        }
    }

}