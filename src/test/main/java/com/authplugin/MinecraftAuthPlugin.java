package com.authplugin;

import com.authplugin.config.ConfigurationManager;
import com.authplugin.database.BackupManager;
import com.authplugin.database.DatabaseManager;
import com.authplugin.database.MySQLManager;
import com.authplugin.database.SQLiteManager;
import com.authplugin.security.AuditLogger;
import com.authplugin.security.IpRateLimiter;
import com.authplugin.security.SecurityManager;
import com.authplugin.session.SessionManager;
import com.authplugin.commands.CommandManager;
import com.authplugin.listeners.PlayerEventListener;
import com.authplugin.utils.AuthLogger;
import com.authplugin.utils.CleanupScheduler;
import com.authplugin.utils.MessageManager;
import com.authplugin.utils.PerformanceMonitor;
import com.authplugin.utils.TimeoutDisplay;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.logging.Level;

/**
 * Main plugin class for Minecraft Authentication Plugin
 * 
 * This plugin provides secure authentication for Minecraft servers with:
 * - Player registration and login system
 * - BCrypt password hashing
 * - Session management
 * - Database support (SQLite/MySQL)
 * - Administrative commands
 * - Multi-language support
 */
public class MinecraftAuthPlugin extends JavaPlugin {
    
    private ConfigurationManager configManager;
    private DatabaseManager databaseManager;
    private SecurityManager securityManager;
    private SessionManager sessionManager;
    private CommandManager commandManager;
    private PlayerEventListener playerListener;
    private AuthLogger authLogger;
    private AuditLogger auditLogger;
    private MessageManager messageManager;
    private IpRateLimiter ipRateLimiter;
    private PerformanceMonitor performanceMonitor;
    private TimeoutDisplay timeoutDisplay;
    private BackupManager backupManager;
    private CleanupScheduler cleanupScheduler;

    // Plugin statistics
    private long startupTime;
    private int totalRegistrations = 0;
    private int totalLogins = 0;
    private int failedAttempts = 0;
    
    @Override
    public void onEnable() {
        startupTime = System.currentTimeMillis();
        
        try {
            // Initialize logger first
            authLogger = new AuthLogger(this);
            authLogger.info("Starting Minecraft Authentication Plugin v" + getDescription().getVersion());

            // Initialize configuration manager
            configManager = new ConfigurationManager(this);
            configManager.loadConfiguration();
            authLogger.info("Configuration loaded successfully");

            // Initialize audit logger and performance monitor
            auditLogger = new AuditLogger(this);
            performanceMonitor = new PerformanceMonitor();
            authLogger.setDebugMode(configManager.isDebugModeEnabled());

            // Initialize message manager
            messageManager = new MessageManager(this, configManager.getLanguage());
            authLogger.info("Message manager initialized (" + configManager.getLanguage() + ")");

            // Initialize IP rate limiter
            ipRateLimiter = new IpRateLimiter(
                    configManager.getMaxFailedAttempts() * 2,
                    configManager.getLockoutDurationSeconds() * 1000L);

            // Initialize security manager
            securityManager = new SecurityManager(configManager);
            authLogger.info("Security manager initialized");

            // Initialize database manager based on configuration
            initializeDatabaseManager();
            authLogger.info("Database manager initialized");

            // Initialize session manager
            sessionManager = new SessionManager(this, configManager);
            authLogger.info("Session manager initialized");

            // Initialize timeout display
            timeoutDisplay = new TimeoutDisplay(this, messageManager);

            // Initialize backup manager
            backupManager = new BackupManager(this);

            // Initialize command manager
            commandManager = new CommandManager(this, databaseManager, sessionManager, securityManager, configManager, authLogger);
            commandManager.registerCommands();
            authLogger.info("Commands registered successfully");

            // Initialize and register event listeners
            playerListener = new PlayerEventListener(this, sessionManager, configManager, securityManager);
            getServer().getPluginManager().registerEvents(playerListener, this);
            authLogger.info("Event listeners registered");

            // Start cleanup scheduler
            File dbFile = new File(getDataFolder(), configManager.getSQLiteFilename());
            cleanupScheduler = new CleanupScheduler(this, sessionManager, ipRateLimiter, backupManager, dbFile);
            cleanupScheduler.start(configManager.getSessionCleanupIntervalMinutes());
            
            authLogger.info("Plugin enabled successfully in " + (System.currentTimeMillis() - startupTime) + "ms");
            
        } catch (Exception e) {
            authLogger.severe("Failed to enable plugin: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }
    
    @Override
    public void onDisable() {
        authLogger.info("Disabling Minecraft Authentication Plugin...");
        
        try {
            // Cancel all running tasks
            getServer().getScheduler().cancelTasks(this);
            
            // Clear all active sessions
            if (sessionManager != null) {
                sessionManager.clearAllSessions();
                authLogger.info("All sessions cleared");
            }

            // Stop timeout displays
            if (timeoutDisplay != null) {
                timeoutDisplay.stopAll();
            }

            // Shutdown audit logger
            if (auditLogger != null) {
                auditLogger.shutdown();
            }

            // Close database connections
            if (databaseManager != null) {
                databaseManager.close();
                authLogger.info("Database connections closed");
            }
            
            authLogger.info("Plugin disabled successfully");
            
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error during plugin shutdown", e);
        }
    }
    
    /**
     * Reload the plugin configuration and reinitialize components
     */
    public void reloadPlugin() {
        authLogger.info("Reloading plugin configuration...");
        
        try {
            // Reload configuration
            configManager.reloadConfiguration();
            
            // Reinitialize security manager with new config
            securityManager = new SecurityManager(configManager);

            // Reload messages
            messageManager.reload(configManager.getLanguage());
            
            // Update session manager configuration
            sessionManager.updateConfiguration(configManager);
            
            // Update command manager configuration
            commandManager.updateConfiguration(configManager);
            
            // Update player listener configuration
            playerListener.updateConfiguration(configManager);

            auditLogger.logConfigReload("server");
            authLogger.info("Plugin configuration reloaded successfully");
            
        } catch (Exception e) {
            authLogger.severe("Failed to reload plugin configuration: " + e.getMessage());
            throw new RuntimeException("Configuration reload failed", e);
        }
    }
    
    /**
     * Initialize the appropriate database manager based on configuration
     */
    private void initializeDatabaseManager() {
        String databaseType = configManager.getDatabaseType();
        
        try {
            switch (databaseType.toLowerCase()) {
                case "mysql":
                    databaseManager = new MySQLManager(configManager, authLogger);
                    break;
                case "sqlite":
                default:
                    databaseManager = new SQLiteManager(this, authLogger);
                    break;
            }
            
            // Initialize database schema
            databaseManager.initializeDatabase().join();
            
        } catch (Exception e) {
            authLogger.severe("Failed to initialize database manager: " + e.getMessage());
            throw new RuntimeException("Database initialization failed", e);
        }
    }
    
    // Getters for other components to access managers
    public ConfigurationManager getConfigManager() {
        return configManager;
    }
    
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
    
    public SecurityManager getSecurityManager() {
        return securityManager;
    }
    
    public SessionManager getSessionManager() {
        return sessionManager;
    }
    
    public CommandManager getCommandManager() {
        return commandManager;
    }

    public PlayerEventListener getPlayerListener() {
        return playerListener;
    }
    
    public AuthLogger getAuthLogger() {
        return authLogger;
    }

    public AuditLogger getAuditLogger() {
        return auditLogger;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public IpRateLimiter getIpRateLimiter() {
        return ipRateLimiter;
    }

    public PerformanceMonitor getPerformanceMonitor() {
        return performanceMonitor;
    }

    public TimeoutDisplay getTimeoutDisplay() {
        return timeoutDisplay;
    }
    
    // Statistics methods
    public void incrementRegistrations() {
        totalRegistrations++;
    }
    
    public void incrementLogins() {
        totalLogins++;
    }
    
    public void incrementFailedAttempts() {
        failedAttempts++;
    }
    
    public int getTotalRegistrations() {
        return totalRegistrations;
    }
    
    public int getTotalLogins() {
        return totalLogins;
    }
    
    public int getFailedAttempts() {
        return failedAttempts;
    }
    
    public long getUptimeMillis() {
        return System.currentTimeMillis() - startupTime;
    }
}