package com.authplugin.session;

import com.authplugin.config.ConfigurationManager;
import com.authplugin.models.AuthSession;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;

import java.net.InetSocketAddress;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for SessionManager
 * Tests universal properties that should hold across all valid inputs
 */
class SessionManagerPropertyTest {

    private Plugin plugin;
    private ConfigurationManager configManager;
    private SessionManager sessionManager;

    @BeforeEach
    void setUp() {
        plugin = mock(Plugin.class);
        configManager = mock(ConfigurationManager.class);
        
        // Set up default configuration behavior
        lenient().when(configManager.isAutoReauthEnabled()).thenReturn(true);
        lenient().when(configManager.getAutoReauthTimeSeconds()).thenReturn(600);
        lenient().when(configManager.getAuthTimeoutSeconds()).thenReturn(300);
        
        sessionManager = new SessionManager(plugin, configManager);
    }

    /**
     * Property 10: Session Persistence
     * For any successfully authenticated player, the authentication session should persist until logout or disconnection.
     * Validates: Requirements 4.1
     */
    @Property
    @Tag("Property 10: Session Persistence")
    void sessionPersistenceProperty(@ForAll("validPlayers") Player player) {
        // Given - create a session for the player
        sessionManager.createSession(player);
        
        // Then - session should persist and be accessible
        assertThat(sessionManager.isAuthenticated(player)).isTrue();
        
        AuthSession session = sessionManager.getSession(player);
        assertThat(session).isNotNull();
        assertThat(session.getPlayerId()).isEqualTo(player.getUniqueId());
        assertThat(session.getUsername()).isEqualTo(player.getName());
        assertThat(session.isAuthenticated()).isTrue();
        
        // Session should remain valid after activity updates
        sessionManager.updateActivity(player);
        assertThat(sessionManager.isAuthenticated(player)).isTrue();
    }

    /**
     * Property 11: Session Cleanup on Disconnect
     * For any player who disconnects from the server, their authentication session should be immediately invalidated.
     * Validates: Requirements 4.2
     */
    @Property
    @Tag("Property 11: Session Cleanup on Disconnect")
    void sessionCleanupOnDisconnectProperty(@ForAll("validPlayers") Player player) {
        // Given - create a session for the player
        sessionManager.createSession(player);
        assertThat(sessionManager.isAuthenticated(player)).isTrue();
        
        // When - player disconnects (session is invalidated)
        sessionManager.invalidateSession(player);
        
        // Then - session should be immediately removed
        assertThat(sessionManager.isAuthenticated(player)).isFalse();
        assertThat(sessionManager.getSession(player)).isNull();
    }

    /**
     * Property 12: Auto-Reauth Within Time Window
     * For any player reconnecting within the configured time window from the same IP address, 
     * automatic re-authentication should be allowed.
     * Validates: Requirements 4.3
     */
    @Property
    @Tag("Property 12: Auto-Reauth Within Time Window")
    void autoReauthWithinTimeWindowProperty(
            @ForAll("validPlayers") Player player,
            @ForAll @IntRange(min = 60, max = 3600) int autoReauthTimeSeconds) {
        
        // Given - configure auto-reauth time window
        lenient().when(configManager.getAutoReauthTimeSeconds()).thenReturn(autoReauthTimeSeconds);
        sessionManager.updateConfiguration(configManager);
        
        // Create session to establish IP login record
        sessionManager.createSession(player);
        String playerIP = getPlayerIP(player);
        
        // When - check auto-reauth within time window
        boolean canAutoReauth = sessionManager.canAutoReauth(player, playerIP);
        
        // Then - auto-reauth should be allowed for same IP within time window
        assertThat(canAutoReauth).isTrue();
    }

    /**
     * Property 13: Server Restart Authentication Reset
     * For any set of previously authenticated players, a server restart should require all players to re-authenticate.
     * Validates: Requirements 4.5
     */
    @Property
    @Tag("Property 13: Server Restart Authentication Reset")
    void serverRestartAuthenticationResetProperty(@ForAll("playerList") java.util.List<Player> players) {
        // Given - multiple authenticated players
        for (Player player : players) {
            sessionManager.createSession(player);
            assertThat(sessionManager.isAuthenticated(player)).isTrue();
        }
        
        int initialSessionCount = sessionManager.getActiveSessionCount();
        assertThat(initialSessionCount).isEqualTo(players.size());
        
        // When - server restart occurs (simulated by clearing all sessions)
        sessionManager.clearAllSessions();
        
        // Then - all sessions should be cleared
        assertThat(sessionManager.getActiveSessionCount()).isEqualTo(0);
        for (Player player : players) {
            assertThat(sessionManager.isAuthenticated(player)).isFalse();
            assertThat(sessionManager.getSession(player)).isNull();
        }
    }

    /**
     * Property 29: Session Cleanup Automation
     * For any expired session data in the system, automatic cleanup should remove the data to prevent memory leaks.
     * Validates: Requirements 10.5
     */
    @Property
    @Tag("Property 29: Session Cleanup Automation")
    void sessionCleanupAutomationProperty(
            @ForAll("validPlayers") Player player,
            @ForAll @IntRange(min = 1, max = 10) int timeoutSeconds) {
        
        // Given - configure short timeout for testing
        lenient().when(configManager.getAuthTimeoutSeconds()).thenReturn(timeoutSeconds);
        sessionManager.updateConfiguration(configManager);
        
        // Create session
        sessionManager.createSession(player);
        assertThat(sessionManager.isAuthenticated(player)).isTrue();
        
        // Simulate time passing by manually setting last activity to past
        AuthSession session = sessionManager.getSession(player);
        long expiredTime = System.currentTimeMillis() - (timeoutSeconds * 1000L + 1000L);
        
        // We can't directly modify the session's lastActivity, so we'll test the cleanup logic
        // by waiting for the timeout period
        try {
            Thread.sleep((timeoutSeconds + 1) * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return; // Skip test if interrupted
        }
        
        // When - cleanup is performed
        int cleanedCount = sessionManager.cleanupExpiredSessions();
        
        // Then - expired session should be cleaned up
        assertThat(cleanedCount).isGreaterThanOrEqualTo(1);
        assertThat(sessionManager.isAuthenticated(player)).isFalse();
    }

    /**
     * Property: Session Consistency
     * For any player, session operations should be consistent and thread-safe
     */
    @Property
    @Tag("Session Consistency")
    void sessionConsistencyProperty(@ForAll("validPlayers") Player player) {
        // Test that session operations are consistent
        
        // Initially no session
        assertThat(sessionManager.isAuthenticated(player)).isFalse();
        assertThat(sessionManager.getSession(player)).isNull();
        
        // Create session
        sessionManager.createSession(player);
        assertThat(sessionManager.isAuthenticated(player)).isTrue();
        assertThat(sessionManager.getSession(player)).isNotNull();
        
        // Update activity doesn't break authentication
        sessionManager.updateActivity(player);
        assertThat(sessionManager.isAuthenticated(player)).isTrue();
        
        // Invalidate session
        sessionManager.invalidateSession(player);
        assertThat(sessionManager.isAuthenticated(player)).isFalse();
        assertThat(sessionManager.getSession(player)).isNull();
    }

    /**
     * Property: Auto-Reauth IP Consistency
     * For any player, auto-reauth should only work with the correct IP address
     */
    @Property
    @Tag("Auto-Reauth IP Consistency")
    void autoReauthIPConsistencyProperty(
            @ForAll("validPlayers") Player player,
            @ForAll("validIPAddress") String wrongIP) {
        
        // Given - create session to establish IP login record
        sessionManager.createSession(player);
        String correctIP = getPlayerIP(player);
        
        // Ensure we're testing with a different IP
        Assume.that(!correctIP.equals(wrongIP));
        
        // When & Then
        assertThat(sessionManager.canAutoReauth(player, correctIP)).isTrue();
        assertThat(sessionManager.canAutoReauth(player, wrongIP)).isFalse();
    }

    // Data providers for property tests

    @Provide
    Arbitrary<Player> validPlayers() {
        return Arbitraries.create(() -> {
            Player player = mock(Player.class);
            UUID playerId = UUID.randomUUID();
            String playerName = "Player" + Math.abs(playerId.hashCode() % 10000);
            String playerIP = generateRandomIP();
            
            when(player.getUniqueId()).thenReturn(playerId);
            when(player.getName()).thenReturn(playerName);
            when(player.getAddress()).thenReturn(new InetSocketAddress(playerIP, 25565));
            
            return player;
        });
    }

    @Provide
    Arbitrary<java.util.List<Player>> playerList() {
        return validPlayers().list().ofMinSize(1).ofMaxSize(10);
    }

    @Provide
    Arbitrary<String> validIPAddress() {
        return Arbitraries.integers().between(1, 254)
                .tuple4()
                .map(tuple -> tuple.get1() + "." + tuple.get2() + "." + tuple.get3() + "." + tuple.get4());
    }

    @Provide
    Arbitrary<String> validPlayerNames() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(3)
                .ofMaxLength(16);
    }

    // Helper methods

    private String getPlayerIP(Player player) {
        return player.getAddress() != null ? 
            player.getAddress().getAddress().getHostAddress() : "unknown";
    }

    private String generateRandomIP() {
        return String.format("192.168.%d.%d", 
            (int)(Math.random() * 255) + 1, 
            (int)(Math.random() * 254) + 1);
    }
}