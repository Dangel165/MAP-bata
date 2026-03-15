package com.authplugin.utils;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Centralised error handler for the auth plugin.
 * Provides user-friendly messages and structured logging for all error types.
 */
public class ErrorHandler {

    private final Logger logger;

    public ErrorHandler(Plugin plugin) {
        this.logger = plugin.getLogger();
    }

    /**
     * Handle a database error: log it and notify the sender.
     */
    public void handleDatabaseError(CommandSender sender, String operation, Exception e) {
        logger.log(Level.SEVERE, "[Auth] Database error during " + operation + ": " + e.getMessage(), e);
        sender.sendMessage("§c서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
    }

    /**
     * Handle an unexpected exception in a command.
     */
    public void handleCommandError(CommandSender sender, String command, Exception e) {
        logger.log(Level.SEVERE, "[Auth] Unexpected error in command /" + command + ": " + e.getMessage(), e);
        sender.sendMessage("§c명령어 실행 중 오류가 발생했습니다.");
    }

    /**
     * Handle an event listener error (non-fatal, just log).
     */
    public void handleEventError(String event, Exception e) {
        logger.log(Level.WARNING, "[Auth] Error in event handler " + event + ": " + e.getMessage(), e);
    }

    /**
     * Handle a configuration error.
     */
    public void handleConfigError(String key, Exception e) {
        logger.log(Level.WARNING, "[Auth] Configuration error for key '" + key + "': " + e.getMessage());
    }

    /**
     * Send a generic internal error message to a player.
     */
    public void sendInternalError(Player player) {
        player.sendMessage("§c내부 오류가 발생했습니다. 관리자에게 문의하세요.");
    }
}
