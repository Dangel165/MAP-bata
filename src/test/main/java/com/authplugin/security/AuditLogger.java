package com.authplugin.security;

import org.bukkit.plugin.Plugin;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * Audit logger for authentication events, admin actions, and security incidents.
 * Writes to both the server log and a dedicated audit file.
 */
public class AuditLogger {

    private final Plugin plugin;
    private final Logger logger;
    private final ExecutorService writer;
    private final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private File auditFile;

    public AuditLogger(Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.writer = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "AuthPlugin-AuditLog");
            t.setDaemon(true);
            return t;
        });
        initAuditFile();
    }

    private void initAuditFile() {
        File dir = plugin.getDataFolder();
        if (!dir.exists()) dir.mkdirs();
        auditFile = new File(dir, "audit.log");
    }

    /** Log a player authentication attempt (login/register). */
    public void logAuthAttempt(String username, String ip, String action, boolean success) {
        String line = String.format("[%s] AUTH %s | user=%s ip=%s success=%b",
                now(), action, username, ip, success);
        writeAsync(line);
        if (!success) logger.warning(line);
    }

    /** Log an admin action (forcelogin, resetpassword, unban, etc.). */
    public void logAdminAction(String admin, String action, String target) {
        String line = String.format("[%s] ADMIN %s | by=%s target=%s",
                now(), action, admin, target);
        writeAsync(line);
        logger.info(line);
    }

    /** Log a security event (brute-force detected, account locked, etc.). */
    public void logSecurityEvent(String event, String detail) {
        String line = String.format("[%s] SECURITY %s | %s", now(), event, detail);
        writeAsync(line);
        logger.warning(line);
    }

    /** Log a configuration reload. */
    public void logConfigReload(String initiator) {
        String line = String.format("[%s] CONFIG RELOAD | by=%s", now(), initiator);
        writeAsync(line);
        logger.info(line);
    }

    private void writeAsync(String line) {
        writer.submit(() -> {
            try (FileWriter fw = new FileWriter(auditFile, true);
                 BufferedWriter bw = new BufferedWriter(fw)) {
                bw.write(line);
                bw.newLine();
            } catch (IOException e) {
                logger.warning("[AuditLogger] Failed to write: " + e.getMessage());
            }
        });
    }

    private String now() {
        return LocalDateTime.now().format(fmt);
    }

    public void shutdown() {
        writer.shutdown();
    }
}
