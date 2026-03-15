package com.authplugin.commands;

import com.authplugin.config.ConfigurationManager;
import com.authplugin.database.DatabaseManager;
import com.authplugin.security.SecurityManager;
import com.authplugin.session.SessionManager;
import com.authplugin.utils.AuthLogger;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.CompletableFuture;

/**
 * Handles the /register command for player account creation
 */
public class RegisterCommand extends BaseCommand {
    
    public RegisterCommand(JavaPlugin plugin, DatabaseManager databaseManager, 
                          SessionManager sessionManager, SecurityManager securityManager,
                          ConfigurationManager configManager, AuthLogger logger) {
        super(plugin, databaseManager, sessionManager, securityManager, configManager, logger);
    }
    
    @Override
    protected boolean executeCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player = getPlayer(sender);
        String password = args[0];
        String confirmPassword = args[1];
        
        // Check if player is already authenticated
        if (isAuthenticated(player)) {
            sendMessage(sender, configManager.getMessage("register.already-authenticated", 
                "§eYou are already logged in."));
            return true;
        }
        
        // Check rate limiting
        String ipAddress = player.getAddress().getAddress().getHostAddress();
        if (securityManager.isRateLimited(ipAddress)) {
            sendMessage(sender, configManager.getMessage("register.rate-limited", 
                "§cToo many attempts. Please wait before trying again."));
            logCommandExecution(sender, "REGISTER_RATE_LIMITED", false);
            return true;
        }
        
        // Validate password confirmation
        if (!password.equals(confirmPassword)) {
            sendMessage(sender, configManager.getMessage("register.password-mismatch", 
                "§cPasswords do not match. Please try again."));
            logCommandExecution(sender, "REGISTER_PASSWORD_MISMATCH", false);
            return true;
        }
        
        // Validate password length
        int minLength = configManager.getMinPasswordLength();
        if (password.length() < minLength) {
            sendMessage(sender, configManager.getMessage("register.password-too-short", 
                "§cPassword must be at least " + minLength + " characters long."));
            logCommandExecution(sender, "REGISTER_PASSWORD_TOO_SHORT", false);
            return true;
        }
        
        // Validate password input
        if (!isValidInput(password)) {
            sendMessage(sender, configManager.getMessage("register.invalid-input", 
                "§cInvalid password format. Please use only alphanumeric characters."));
            logCommandExecution(sender, "REGISTER_INVALID_INPUT", false);
            return true;
        }
        
        // Check password strength and provide feedback
        String strengthMessage = getPasswordStrengthMessage(password);
        if (strengthMessage != null) {
            sendMessage(sender, strengthMessage);
        }
        
        // Check if player is already registered
        CompletableFuture<Boolean> existsFuture = databaseManager.playerExists(player.getName());
        
        existsFuture.thenAccept(exists -> {
            if (exists) {
                sendMessage(sender, configManager.getMessage("register.already-registered", 
                    "§c이미 가입된 계정입니다. /login <비밀번호> 로 로그인하세요."));
                logCommandExecution(sender, "REGISTER_ALREADY_EXISTS", false);
                return;
            }
            
            // Hash password and register player
            String passwordHash = securityManager.hashPassword(password);
            
            CompletableFuture<Boolean> registerFuture = databaseManager.registerPlayer(
                player.getName(), passwordHash, ipAddress);
            
            registerFuture.thenAccept(success -> {
                if (success) {
                    handleSuccessfulRegistration(player);
                } else {
                    handleFailedRegistration(player);
                }
            }).exceptionally(throwable -> {
                logger.severe("Database error during registration for " + player.getName() + ": " + throwable.getMessage());
                sendMessage(sender, configManager.getMessage("register.database-error", 
                    "§cA database error occurred. Please try again later."));
                logCommandExecution(sender, "REGISTER_DATABASE_ERROR", false);
                return null;
            });
            
        }).exceptionally(throwable -> {
            logger.severe("Database error checking player existence for " + player.getName() + ": " + throwable.getMessage());
            sendMessage(sender, configManager.getMessage("register.database-error", 
                "§cA database error occurred. Please try again later."));
            logCommandExecution(sender, "REGISTER_DATABASE_ERROR", false);
            return null;
        });
        
        return true;
    }
    
    private void handleSuccessfulRegistration(Player player) {
        // Create session (auto-login after registration)
        sessionManager.createSession(player);

        // Cancel auth timeout and unlock movement
        if (plugin instanceof com.authplugin.MinecraftAuthPlugin) {
            com.authplugin.MinecraftAuthPlugin authPlugin = (com.authplugin.MinecraftAuthPlugin) plugin;
            if (authPlugin.getPlayerListener() != null) {
                authPlugin.getPlayerListener().onPlayerAuthenticated(player);
            }
        }

        // Send success message
        sendMessage(player, configManager.getMessage("register.success", 
            "§aRegistration successful! You have been automatically logged in."));
        
        // Send welcome message
        String welcomeMessage = configManager.getMessage("register.welcome", 
            "§eWelcome to the server, " + player.getName() + "! Your account is now secure.")
            .replace("{name}", player.getName());
        sendMessage(player, welcomeMessage);
        
        // Log successful registration
        logCommandExecution(player, "REGISTER_SUCCESS", true);
        
        // Increment plugin statistics
        if (plugin instanceof com.authplugin.MinecraftAuthPlugin) {
            ((com.authplugin.MinecraftAuthPlugin) plugin).incrementRegistrations();
        }
        
        logger.info("Player " + player.getName() + " registered successfully from " + 
            player.getAddress().getAddress().getHostAddress());
    }
    
    private void handleFailedRegistration(Player player) {
        sendMessage(player, configManager.getMessage("register.failed", 
            "§cRegistration failed. Please try again."));
        
        logCommandExecution(player, "REGISTER_FAILED", false);
        
        logger.warning("Registration failed for " + player.getName() + " from " + 
            player.getAddress().getAddress().getHostAddress());
    }
    
    private String getPasswordStrengthMessage(String password) {
        int strength = calculatePasswordStrength(password);
        
        switch (strength) {
            case 1:
                return configManager.getMessage("register.password-weak", 
                    "§6Password strength: Weak. Consider adding numbers or special characters.");
            case 2:
                return configManager.getMessage("register.password-medium", 
                    "§ePassword strength: Medium. Good choice!");
            case 3:
                return configManager.getMessage("register.password-strong", 
                    "§aPassword strength: Strong. Excellent!");
            default:
                return null;
        }
    }
    
    private int calculatePasswordStrength(String password) {
        int strength = 0;
        
        // Check length
        if (password.length() >= 8) strength++;
        
        // Check for numbers
        if (password.matches(".*\\d.*")) strength++;
        
        // Check for mixed case or special characters
        if (password.matches(".*[A-Z].*") || password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*")) {
            strength++;
        }
        
        return Math.min(strength, 3);
    }
    
    @Override
    protected String getRequiredPermission() {
        return "auth.register";
    }
    
    @Override
    protected boolean requiresPlayer() {
        return true;
    }
    
    @Override
    protected boolean validateArguments(String[] args) {
        return args.length == 2 && 
               args[0] != null && !args[0].trim().isEmpty() &&
               args[1] != null && !args[1].trim().isEmpty();
    }
}