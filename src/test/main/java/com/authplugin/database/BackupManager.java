package com.authplugin.database;

import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.logging.Logger;

/**
 * Manages automatic database backups with rotation.
 * Keeps the last N backups and deletes older ones.
 */
public class BackupManager {

    private static final int MAX_BACKUPS = 7;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private final Plugin plugin;
    private final Logger logger;
    private final File backupDir;

    public BackupManager(Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.backupDir = new File(plugin.getDataFolder(), "backups");
        if (!backupDir.exists()) backupDir.mkdirs();
    }

    /**
     * Back up the given database file.
     * @param dbFile source database file to copy
     * @throws IOException if the copy fails
     */
    public void backup(File dbFile) throws IOException {
        if (!dbFile.exists()) {
            logger.warning("[Backup] Source file not found: " + dbFile.getAbsolutePath());
            return;
        }

        String timestamp = LocalDateTime.now().format(FMT);
        String baseName = dbFile.getName().replaceFirst("\\.[^.]+$", "");
        File dest = new File(backupDir, baseName + "_" + timestamp + ".db");

        Files.copy(dbFile.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        logger.info("[Backup] Created: " + dest.getName());

        rotateBackups(baseName);
    }

    /** Delete oldest backups, keeping only MAX_BACKUPS. */
    private void rotateBackups(String baseName) {
        File[] files = backupDir.listFiles(
                (dir, name) -> name.startsWith(baseName + "_") && name.endsWith(".db"));
        if (files == null || files.length <= MAX_BACKUPS) return;

        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        int toDelete = files.length - MAX_BACKUPS;
        for (int i = 0; i < toDelete; i++) {
            if (files[i].delete()) {
                logger.info("[Backup] Rotated out: " + files[i].getName());
            }
        }
    }

    public File getBackupDir() {
        return backupDir;
    }
}
