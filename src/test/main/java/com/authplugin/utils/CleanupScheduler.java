package com.authplugin.utils;

import com.authplugin.database.BackupManager;
import com.authplugin.security.IpRateLimiter;
import com.authplugin.session.SessionManager;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.util.logging.Logger;

/**
 * Schedules periodic maintenance tasks:
 *  - expired session cleanup
 *  - IP rate-limiter cache purge
 *  - daily database backup
 */
public class CleanupScheduler {

    private final Plugin plugin;
    private final Logger logger;
    private final SessionManager sessionManager;
    private final IpRateLimiter rateLimiter;
    private final BackupManager backupManager;
    private final File dbFile;

    public CleanupScheduler(Plugin plugin,
                            SessionManager sessionManager,
                            IpRateLimiter rateLimiter,
                            BackupManager backupManager,
                            File dbFile) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.sessionManager = sessionManager;
        this.rateLimiter = rateLimiter;
        this.backupManager = backupManager;
        this.dbFile = dbFile;
    }

    /** Start all scheduled tasks. */
    public void start(int sessionCleanupIntervalMinutes) {
        long sessionTicks = sessionCleanupIntervalMinutes * 60L * 20L;

        // Session + rate-limiter cleanup
        new BukkitRunnable() {
            @Override public void run() {
                int cleaned = sessionManager.cleanupExpiredSessions();
                if (cleaned > 0) logger.info("[Cleanup] Removed " + cleaned + " expired sessions.");
                rateLimiter.cleanup();
            }
        }.runTaskTimerAsynchronously(plugin, sessionTicks, sessionTicks);

        // Daily backup (24 h = 1 728 000 ticks)
        new BukkitRunnable() {
            @Override public void run() {
                try {
                    backupManager.backup(dbFile);
                } catch (Exception e) {
                    logger.warning("[Cleanup] Backup failed: " + e.getMessage());
                }
            }
        }.runTaskTimerAsynchronously(plugin, 1728000L, 1728000L);

        logger.info("[CleanupScheduler] Started (session interval: " + sessionCleanupIntervalMinutes + " min).");
    }
}
