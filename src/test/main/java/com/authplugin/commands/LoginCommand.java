package com.authplugin.commands;

import com.authplugin.config.ConfigurationManager;
import com.authplugin.database.DatabaseManager;
import com.authplugin.models.PlayerData;
import com.authplugin.security.SecurityManager;
import com.authplugin.session.SessionManager;
import com.authplugin.utils.AuthLogger;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.CompletableFuture;

/**
 * Handles the /login command for player authentication
 */
public class LoginCommand extends BaseCommand {
    
    public LoginCommand(JavaPlugin plugin, DatabaseManager databaseManager, 
                       SessionManager sessionManager, SecurityManager securityManager,
                       ConfigurationManager configManager, AuthLogger logger) {
        super(plugin, databaseManager, sessionManager, securityManager, configManager, logger);
    }
    
    @Override
    protected boolean executeCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player = getPlayer(sender);
        String password = args[0];
        
        // Check if player is already authenticated
        if (isAuthenticated(player)) {
            sendMessage(sender, configManager.getMessage("login.already-authenticated", 
                "§eYou are already logged in."));
            return true;
        }
        
        // Check rate limiting
        String ipAddress = player.getAddress().getAddress().getHostAddress();
        if (securityManager.isRateLimited(ipAddress)) {
            sendMessage(sender, configManager.getMessage("login.rate-limited", 
                "§cToo many failed attempts. Please wait before trying again."));
            logCommandExecution(sender, "LOGIN_RATE_LIMITED", false);
            return true;
        }
        
        // Validate password input
        if (!isValidInput(password)) {
            sendMessage(sender, configManager.getMessage("login.invalid-input", 
                "§cInvalid password format."));
            logCommandExecution(sender, "LOGIN_INVALID_INPUT", false);
            return true;
        }
        
        // Perform authentication asynchronously
        CompletableFuture<PlayerData> playerDataFuture = databaseManager.getPlayerData(player.getName());
        
        playerDataFuture.thenAccept(playerData -> {
            if (playerData == null) {
                // Player not registered
                sendMessage(sender, configManager.getMessage("login.not-registered", 
                    "§cYou are not registered. Use /register <password> <confirmPassword> to create an account."));
                logCommandExecution(sender, "LOGIN_NOT_REGISTERED", false);
                return;
            }
            
            // Verify that the current nickname matches the registered nickname exactly
            if (!playerData.getUsername().equals(player.getName())) {
                sendMessage(sender, configManager.getMessage("login.name-mismatch",
                    "§c이 계정은 다른 닉네임으로 등록되어 있습니다. 등록 시 사용한 닉네임으로 접속하세요."));
                logCommandExecution(sender, "LOGIN_NAME_MISMATCH", false);
                return;
            }
            
            // Check if account is locked
            if (playerData.isLocked()) {
                long remainingTime = playerData.getLockoutRemaining();
                String timeMessage = formatTime(remainingTime);
                sendMessage(sender, configManager.getMessage("login.account-locked", 
                    "§cYour account is temporarily locked. Try again in " + timeMessage + "."));
                logCommandExecution(sender, "LOGIN_ACCOUNT_LOCKED", false);
                return;
            }
            
            // Verify password
            if (securityManager.verifyPassword(password, playerData.getPasswordHash())) {
                // Successful login
                handleSuccessfulLogin(player, playerData);
            } else {
                // Failed login
                handleFailedLogin(player, playerData);
            }
        }).exceptionally(throwable -> {
            logger.severe("Database error during login for " + player.getName() + ": " + throwable.getMessage());
            sendMessage(sender, configManager.getMessage("login.database-error", 
                "§cA database error occurred. Please try again later."));
            logCommandExecution(sender, "LOGIN_DATABASE_ERROR", false);
            return null;
        });
        
        return true;
    }
    
    private void handleSuccessfulLogin(Player player, PlayerData playerData) {
        // Kick any other online player already logged in with the same account name
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (Player online : plugin.getServer().getOnlinePlayers()) {
                if (!online.getUniqueId().equals(player.getUniqueId())
                        && online.getName().equalsIgnoreCase(player.getName())
                        && sessionManager.isAuthenticated(online)) {
                    online.kickPlayer("§c다른 곳에서 같은 계정으로 로그인되었습니다.");
                }
            }
        });

        // Reset failed attempts
        if (playerData.getFailedAttempts() > 0) {
            databaseManager.resetFailedAttempts(player.getName());
        }
        
        // Update last login
        databaseManager.updateLastLogin(player.getName(), 
            player.getAddress().getAddress().getHostAddress());
        
        // Create session
        sessionManager.createSession(player);

        // Cancel auth timeout and unlock movement
        if (plugin instanceof com.authplugin.MinecraftAuthPlugin) {
            com.authplugin.MinecraftAuthPlugin authPlugin = (com.authplugin.MinecraftAuthPlugin) plugin;
            if (authPlugin.getPlayerListener() != null) {
                authPlugin.getPlayerListener().onPlayerAuthenticated(player);
            }
        }
        
        // Send success message
        sendMessage(player, configManager.getMessage("login.success", 
            "§aSuccessfully logged in! Welcome back, " + player.getName() + ".")
            .replace("{name}", player.getName()));
        
        // Log successful login
        logCommandExecution(player, "LOGIN_SUCCESS", true);
        
        // Increment plugin statistics
        if (plugin instanceof com.authplugin.MinecraftAuthPlugin) {
            ((com.authplugin.MinecraftAuthPlugin) plugin).incrementLogins();
        }
        
        logger.info("Player " + player.getName() + " logged in successfully from " + 
            player.getAddress().getAddress().getHostAddress());
    }
    
    private void handleFailedLogin(Player player, PlayerData playerData) {
        // Increment failed attempts
        int newFailedAttempts = playerData.getFailedAttempts() + 1;
        int maxAttempts = configManager.getMaxFailedAttempts();
        
        if (newFailedAttempts >= maxAttempts) {
            // Lock account
            long lockoutDuration = configManager.getLockoutDuration();
            databaseManager.lockAccount(player.getName(), lockoutDuration);
            
            sendMessage(player, configManager.getMessage("login.account-locked-now", 
                "§cToo many failed attempts. Your account has been temporarily locked."));
            
            logCommandExecution(player, "LOGIN_ACCOUNT_LOCKED_NOW", false);
        } else {
            // Update failed attempts
            databaseManager.incrementFailedAttempts(player.getName());
            
            int remainingAttempts = maxAttempts - newFailedAttempts;
            sendMessage(player, configManager.getMessage("login.failed", 
                "§cIncorrect password. " + remainingAttempts + " attempts remaining.")
                .replace("{remaining}", String.valueOf(remainingAttempts)));
            
            logCommandExecution(player, "LOGIN_FAILED", false);
        }
        
        // Apply rate limiting
        String ipAddress = player.getAddress().getAddress().getHostAddress();
        securityManager.applyRateLimit(ipAddress);
        
        // Increment plugin statistics
        if (plugin instanceof com.authplugin.MinecraftAuthPlugin) {
            ((com.authplugin.MinecraftAuthPlugin) plugin).incrementFailedAttempts();
        }
        
        logger.warning("Failed login attempt for " + player.getName() + " from " + ipAddress);
    }
    
    private String formatTime(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        
        if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        } else {
            return seconds + "s";
        }
    }
    
    @Override
    protected String getRequiredPermission() {
        return "auth.login";
    }
    
    @Override
    protected boolean requiresPlayer() {
        return true;
    }
    
    @Override
    protected boolean validateArguments(String[] args) {
        return args.length == 1 && args[0] != null && !args[0].trim().isEmpty();
    }
}