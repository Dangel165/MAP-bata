package com.authplugin.database;

import com.authplugin.utils.AuthLogger;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Utility class for database connection recovery and error handling
 * Provides automatic reconnection logic and connection health monitoring
 */
public class DatabaseConnectionRecovery {
    
    private final AuthLogger logger;
    private final int maxRetryAttempts;
    private final long retryDelayMs;
    private final long connectionTimeoutMs;
    
    public DatabaseConnectionRecovery(AuthLogger logger) {
        this(logger, 3, 5000, 30000);
    }
    
    public DatabaseConnectionRecovery(AuthLogger logger, int maxRetryAttempts, long retryDelayMs, long connectionTimeoutMs) {
        this.logger = logger;
        this.maxRetryAttempts = maxRetryAttempts;
        this.retryDelayMs = retryDelayMs;
        this.connectionTimeoutMs = connectionTimeoutMs;
    }
    
    /**
     * Execute a database operation with automatic retry on connection failure
     */
    public <T> CompletableFuture<T> executeWithRetry(Supplier<T> operation, String operationName) {
        return CompletableFuture.supplyAsync(() -> {
            Exception lastException = null;
            
            for (int attempt = 1; attempt <= maxRetryAttempts; attempt++) {
                try {
                    long startTime = System.currentTimeMillis();
                    T result = operation.get();
                    long duration = System.currentTimeMillis() - startTime;
                    
                    if (attempt > 1) {
                        logger.info(String.format("Database operation '%s' succeeded on attempt %d (took %dms)", 
                            operationName, attempt, duration));
                    }
                    
                    return result;
                    
                } catch (Exception e) {
                    lastException = e;
                    
                    if (isConnectionError(e)) {
                        logger.warning(String.format("Database connection error on attempt %d/%d for operation '%s': %s", 
                            attempt, maxRetryAttempts, operationName, e.getMessage()));
                        
                        if (attempt < maxRetryAttempts) {
                            try {
                                Thread.sleep(retryDelayMs * attempt); // Exponential backoff
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                throw new RuntimeException("Operation interrupted during retry", ie);
                            }
                        }
                    } else {
                        // Non-connection error, don't retry
                        logger.logError("DatabaseConnectionRecovery." + operationName, e);
                        throw new RuntimeException("Database operation failed: " + e.getMessage(), e);
                    }
                }
            }
            
            // All retry attempts failed
            logger.severe(String.format("Database operation '%s' failed after %d attempts", operationName, maxRetryAttempts));
            throw new RuntimeException("Database operation failed after " + maxRetryAttempts + " attempts", lastException);
        });
    }
    
    /**
     * Execute a void database operation with automatic retry on connection failure
     */
    public CompletableFuture<Void> executeVoidWithRetry(Runnable operation, String operationName) {
        return executeWithRetry(() -> {
            operation.run();
            return null;
        }, operationName).thenApply(result -> null);
    }
    
    /**
     * Test database connection health
     */
    public CompletableFuture<Boolean> testConnection(Supplier<Connection> connectionSupplier) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = connectionSupplier.get()) {
                if (conn == null || conn.isClosed()) {
                    return false;
                }
                
                // Test with a simple query
                try (var stmt = conn.createStatement()) {
                    stmt.setQueryTimeout((int) TimeUnit.MILLISECONDS.toSeconds(connectionTimeoutMs));
                    stmt.executeQuery("SELECT 1").close();
                    return true;
                }
                
            } catch (SQLException e) {
                logger.warning("Database connection health check failed: " + e.getMessage());
                return false;
            }
        });
    }
    
    /**
     * Monitor database connection health periodically
     */
    public CompletableFuture<Void> startConnectionMonitoring(Supplier<Connection> connectionSupplier, 
                                                           long intervalMs, 
                                                           Runnable onConnectionLost,
                                                           Runnable onConnectionRestored) {
        return CompletableFuture.runAsync(() -> {
            boolean wasConnected = true;
            
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    boolean isConnected = testConnection(connectionSupplier).get();
                    
                    if (!isConnected && wasConnected) {
                        logger.warning("Database connection lost, attempting recovery...");
                        if (onConnectionLost != null) {
                            onConnectionLost.run();
                        }
                        wasConnected = false;
                    } else if (isConnected && !wasConnected) {
                        logger.info("Database connection restored");
                        if (onConnectionRestored != null) {
                            onConnectionRestored.run();
                        }
                        wasConnected = true;
                    }
                    
                    Thread.sleep(intervalMs);
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.logError("DatabaseConnectionRecovery.monitoring", e);
                    try {
                        Thread.sleep(intervalMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        });
    }
    
    /**
     * Determine if an exception is a connection-related error that should trigger retry
     */
    private boolean isConnectionError(Exception e) {
        if (e instanceof SQLException) {
            SQLException sqlEx = (SQLException) e;
            String sqlState = sqlEx.getSQLState();
            int errorCode = sqlEx.getErrorCode();
            String message = sqlEx.getMessage().toLowerCase();
            
            // Common connection error patterns
            return sqlState != null && (
                sqlState.startsWith("08") || // Connection exception
                sqlState.equals("HY000") || // General error (often connection-related)
                sqlState.equals("HYT00")    // Timeout
            ) || 
            errorCode == 0 || // Often indicates connection issues
            message.contains("connection") ||
            message.contains("timeout") ||
            message.contains("network") ||
            message.contains("socket") ||
            message.contains("broken pipe") ||
            message.contains("connection reset") ||
            message.contains("no route to host") ||
            message.contains("connection refused");
        }
        
        // Check for common connection-related runtime exceptions
        String message = e.getMessage();
        if (message != null) {
            message = message.toLowerCase();
            return message.contains("connection") ||
                   message.contains("timeout") ||
                   message.contains("network") ||
                   message.contains("socket");
        }
        
        return false;
    }
    
    /**
     * Create a database operation wrapper that includes error handling and logging
     */
    public static class DatabaseOperation<T> {
        private final Supplier<T> operation;
        private final String operationName;
        private final AuthLogger logger;
        private boolean logPerformance = false;
        private long slowQueryThresholdMs = 1000;
        
        public DatabaseOperation(Supplier<T> operation, String operationName, AuthLogger logger) {
            this.operation = operation;
            this.operationName = operationName;
            this.logger = logger;
        }
        
        public DatabaseOperation<T> withPerformanceLogging(boolean enabled) {
            this.logPerformance = enabled;
            return this;
        }
        
        public DatabaseOperation<T> withSlowQueryThreshold(long thresholdMs) {
            this.slowQueryThresholdMs = thresholdMs;
            return this;
        }
        
        public T execute() {
            long startTime = System.currentTimeMillis();
            
            try {
                T result = operation.get();
                long duration = System.currentTimeMillis() - startTime;
                
                if (logPerformance) {
                    logger.logPerformance(operationName, duration);
                }
                
                if (duration > slowQueryThresholdMs) {
                    logger.warning(String.format("Slow database query detected: %s took %dms", operationName, duration));
                }
                
                return result;
                
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - startTime;
                logger.logError(operationName, e);
                logger.warning(String.format("Database operation '%s' failed after %dms", operationName, duration));
                throw e;
            }
        }
    }
    
    /**
     * Database health status information
     */
    public static class DatabaseHealthStatus {
        private final boolean isHealthy;
        private final long responseTimeMs;
        private final String lastError;
        private final long lastCheckTime;
        
        public DatabaseHealthStatus(boolean isHealthy, long responseTimeMs, String lastError) {
            this.isHealthy = isHealthy;
            this.responseTimeMs = responseTimeMs;
            this.lastError = lastError;
            this.lastCheckTime = System.currentTimeMillis();
        }
        
        public boolean isHealthy() {
            return isHealthy;
        }
        
        public long getResponseTimeMs() {
            return responseTimeMs;
        }
        
        public String getLastError() {
            return lastError;
        }
        
        public long getLastCheckTime() {
            return lastCheckTime;
        }
        
        @Override
        public String toString() {
            return String.format("DatabaseHealthStatus{healthy=%s, responseTime=%dms, lastError='%s', lastCheck=%d}", 
                isHealthy, responseTimeMs, lastError, lastCheckTime);
        }
    }
    
    /**
     * Get comprehensive database health status
     */
    public CompletableFuture<DatabaseHealthStatus> getHealthStatus(Supplier<Connection> connectionSupplier) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            
            try (Connection conn = connectionSupplier.get()) {
                if (conn == null || conn.isClosed()) {
                    return new DatabaseHealthStatus(false, 0, "Connection is null or closed");
                }
                
                // Test with a simple query
                try (var stmt = conn.createStatement()) {
                    stmt.setQueryTimeout((int) TimeUnit.MILLISECONDS.toSeconds(connectionTimeoutMs));
                    stmt.executeQuery("SELECT 1").close();
                    
                    long responseTime = System.currentTimeMillis() - startTime;
                    return new DatabaseHealthStatus(true, responseTime, null);
                }
                
            } catch (SQLException e) {
                long responseTime = System.currentTimeMillis() - startTime;
                return new DatabaseHealthStatus(false, responseTime, e.getMessage());
            }
        });
    }
}