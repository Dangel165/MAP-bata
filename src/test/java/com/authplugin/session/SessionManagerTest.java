package com.authplugin.session;

import com.authplugin.config.ConfigurationManager;
import com.authplugin.models.AuthSession;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetSocketAddress;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SessionManager
 * Tests specific examples and edge cases for session management functionality
 */
@ExtendWith(MockitoExtension.class)
class SessionManagerTest {

    @Mock
    private Plugin plugin;
    
    @Mock
    private ConfigurationManager configManager;
    
    @Mock
    private Player player;
    
    private SessionManager sessionManager;
    private UUID playerId;
    private String playerName;
    private String playerIP;

    @BeforeEach
    void setUp() {
        playerId = UUID.randomUUID();
        playerName = "TestPlayer";
        playerIP = "192.168.1.100";
        
        // Mock player behavior
        lenient().when(player.getUniqueId()).thenReturn(playerId);
        lenient().when(player.getName()).thenReturn(playerName);
        lenient().when(player.getAddress()).thenReturn(new InetSocketAddress(playerIP, 25565));
        
        // Mock configuration defaults
        lenient().when(configManager.isAutoReauthEnabled()).thenReturn(true);
        lenient().when(configManager.getAutoReauthTimeSeconds()).thenReturn(600); // 10 minutes
        lenient().when(configManager.getAuthTimeoutSeconds()).thenReturn(300); // 5 minutes
        
        sessionManager = new SessionManager(plugin, configManager);
    }

    @Test
    void shouldCreateSessionForPlayer() {
        // When
        sessionManager.createSession(player);
        
        // Then
        assertThat(sessionManager.isAuthenticated(player)).isTrue();
        assertThat(sessionManager.getActiveSessionCount()).isEqualTo(1);
        
        AuthSession session = sessionManager.getSession(player);
        assertThat(session).isNotNull();
        assertThat(session.getPlayerId()).isEqualTo(playerId);
        assertThat(session.getUsername()).isEqualTo(playerName);
        assertThat(session.getIpAddress()).isEqualTo(playerIP);
        assertThat(session.isAuthenticated()).isTrue();
    }

    @Test
    void shouldReturnFalseForUnauthenticatedPlayer() {
        // Given - no session created
        
        // When & Then
        assertThat(sessionManager.isAuthenticated(player)).isFalse();
        assertThat(sessionManager.getSession(player)).isNull();
        assertThat(sessionManager.getActiveSessionCount()).isEqualTo(0);
    }

    @Test
    void shouldInvalidatePlayerSession() {
        // Given
        sessionManager.createSession(player);
        assertThat(sessionManager.isAuthenticated(player)).isTrue();
        
        // When
        sessionManager.invalidateSession(player);
        
        // Then
        assertThat(sessionManager.isAuthenticated(player)).isFalse();
        assertThat(sessionManager.getSession(player)).isNull();
        assertThat(sessionManager.getActiveSessionCount()).isEqualTo(0);
    }

    @Test
    void shouldUpdateSessionActivity() {
        // Given
        sessionManager.createSession(player);
        AuthSession session = sessionManager.getSession(player);
        long initialActivity = session.getLastActivity();
        
        // Wait a bit to ensure timestamp difference
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // When
        sessionManager.updateActivity(player);
        
        // Then
        assertThat(session.getLastActivity()).isGreaterThan(initialActivity);
    }

    @Test
    void shouldAllowAutoReauthWithinTimeWindow() {
        // Given
        sessionManager.createSession(player);
        
        // When
        boolean canAutoReauth = sessionManager.canAutoReauth(player, playerIP);
        
        // Then
        assertThat(canAutoReauth).isTrue();
    }

    @Test
    void shouldDenyAutoReauthWhenDisabled() {
        // Given
        lenient().when(configManager.isAutoReauthEnabled()).thenReturn(false);
        sessionManager.createSession(player);
        
        // When
        boolean canAutoReauth = sessionManager.canAutoReauth(player, playerIP);
        
        // Then
        assertThat(canAutoReauth).isFalse();
    }

    @Test
    void shouldDenyAutoReauthForUnknownIP() {
        // Given
        sessionManager.createSession(player);
        
        // When
        boolean canAutoReauth = sessionManager.canAutoReauth(player, "192.168.1.200");
        
        // Then
        assertThat(canAutoReauth).isFalse();
    }

    @Test
    void shouldCleanupExpiredSessions() throws InterruptedException {
        // Given
        lenient().when(configManager.getAuthTimeoutSeconds()).thenReturn(1); // 1 second timeout
        sessionManager.updateConfiguration(configManager);
        
        sessionManager.createSession(player);
        assertThat(sessionManager.getActiveSessionCount()).isEqualTo(1);
        
        // Wait for session to expire
        Thread.sleep(1100);
        
        // When
        int cleanedCount = sessionManager.cleanupExpiredSessions();
        
        // Then
        assertThat(cleanedCount).isEqualTo(1);
        assertThat(sessionManager.getActiveSessionCount()).isEqualTo(0);
        assertThat(sessionManager.isAuthenticated(player)).isFalse();
    }

    @Test
    void shouldNotCleanupActiveSession() {
        // Given
        sessionManager.createSession(player);
        sessionManager.updateActivity(player); // Update activity to keep session fresh
        
        // When
        int cleanedCount = sessionManager.cleanupExpiredSessions();
        
        // Then
        assertThat(cleanedCount).isEqualTo(0);
        assertThat(sessionManager.getActiveSessionCount()).isEqualTo(1);
        assertThat(sessionManager.isAuthenticated(player)).isTrue();
    }

    @Test
    void shouldClearAllSessions() {
        // Given
        Player player2 = mock(Player.class);
        UUID player2Id = UUID.randomUUID();
        when(player2.getUniqueId()).thenReturn(player2Id);
        when(player2.getName()).thenReturn("TestPlayer2");
        when(player2.getAddress()).thenReturn(new InetSocketAddress("192.168.1.101", 25565));
        
        sessionManager.createSession(player);
        sessionManager.createSession(player2);
        assertThat(sessionManager.getActiveSessionCount()).isEqualTo(2);
        
        // When
        sessionManager.clearAllSessions();
        
        // Then
        assertThat(sessionManager.getActiveSessionCount()).isEqualTo(0);
        assertThat(sessionManager.isAuthenticated(player)).isFalse();
        assertThat(sessionManager.isAuthenticated(player2)).isFalse();
    }

    @Test
    void shouldHandlePlayerWithNullAddress() {
        // Given
        Player playerWithNullAddress = mock(Player.class);
        UUID nullAddressPlayerId = UUID.randomUUID();
        when(playerWithNullAddress.getUniqueId()).thenReturn(nullAddressPlayerId);
        when(playerWithNullAddress.getName()).thenReturn("NullAddressPlayer");
        when(playerWithNullAddress.getAddress()).thenReturn(null);
        
        // When
        sessionManager.createSession(playerWithNullAddress);
        
        // Then
        assertThat(sessionManager.isAuthenticated(playerWithNullAddress)).isTrue();
        AuthSession session = sessionManager.getSession(playerWithNullAddress);
        assertThat(session.getIpAddress()).isEqualTo("unknown");
    }

    @Test
    void shouldUpdateConfigurationCorrectly() {
        // Given
        ConfigurationManager newConfigManager = mock(ConfigurationManager.class);
        lenient().when(newConfigManager.isAutoReauthEnabled()).thenReturn(false);
        lenient().when(newConfigManager.getAutoReauthTimeSeconds()).thenReturn(1200);
        lenient().when(newConfigManager.getAuthTimeoutSeconds()).thenReturn(600);
        
        // When
        sessionManager.updateConfiguration(newConfigManager);
        
        // Then
        // Test that new configuration is used
        sessionManager.createSession(player);
        boolean canAutoReauth = sessionManager.canAutoReauth(player, playerIP);
        assertThat(canAutoReauth).isFalse(); // Should be false due to disabled auto-reauth
    }

    @Test
    void shouldHandleMultipleConcurrentSessions() {
        // Given
        Player[] players = new Player[10];
        for (int i = 0; i < 10; i++) {
            players[i] = mock(Player.class);
            UUID playerId = UUID.randomUUID();
            when(players[i].getUniqueId()).thenReturn(playerId);
            when(players[i].getName()).thenReturn("Player" + i);
            when(players[i].getAddress()).thenReturn(new InetSocketAddress("192.168.1." + (100 + i), 25565));
        }
        
        // When
        for (Player p : players) {
            sessionManager.createSession(p);
        }
        
        // Then
        assertThat(sessionManager.getActiveSessionCount()).isEqualTo(10);
        for (Player p : players) {
            assertThat(sessionManager.isAuthenticated(p)).isTrue();
        }
    }

    @Test
    void shouldDenyAutoReauthAfterTimeWindowExpires() throws InterruptedException {
        // Given
        lenient().when(configManager.getAutoReauthTimeSeconds()).thenReturn(1); // 1 second window
        sessionManager.updateConfiguration(configManager);
        sessionManager.createSession(player);
        
        // Wait for auto-reauth window to expire
        Thread.sleep(1100);
        
        // When
        boolean canAutoReauth = sessionManager.canAutoReauth(player, playerIP);
        
        // Then
        assertThat(canAutoReauth).isFalse();
    }

    @Test
    void shouldCleanupExpiredIPLoginRecords() throws InterruptedException {
        // Given
        lenient().when(configManager.getAutoReauthTimeSeconds()).thenReturn(1); // 1 second window
        sessionManager.updateConfiguration(configManager);
        sessionManager.createSession(player);
        
        // Wait for IP login record to expire
        Thread.sleep(1100);
        
        // When
        sessionManager.cleanupExpiredSessions();
        
        // Then
        // After cleanup, auto-reauth should not be possible even with correct IP
        boolean canAutoReauth = sessionManager.canAutoReauth(player, playerIP);
        assertThat(canAutoReauth).isFalse();
    }
}