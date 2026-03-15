package com.authplugin.listeners;

import com.authplugin.config.ConfigurationManager;
import com.authplugin.security.SecurityManager;
import com.authplugin.session.SessionManager;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles player events for authentication and security.
 * Restricts unauthenticated players from moving, chatting, using commands, and interacting.
 */
public class PlayerEventListener implements Listener {

    private final Plugin plugin;
    private final SessionManager sessionManager;
    private ConfigurationManager configManager;
    private final SecurityManager securityManager;

    // Stores the spawn location each player was teleported to on join
    private final Map<UUID, Location> joinLocations = new HashMap<>();
    // Tracks timeout task IDs so we can cancel them on auth
    private final Map<UUID, Integer> timeoutTasks = new HashMap<>();

    public PlayerEventListener(Plugin plugin, SessionManager sessionManager,
                               ConfigurationManager configManager, SecurityManager securityManager) {
        this.plugin = plugin;
        this.sessionManager = sessionManager;
        this.configManager = configManager;
        this.securityManager = securityManager;
    }

    // -------------------------------------------------------------------------
    // Join
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Check auto-reauth first
        String ip = player.getAddress() != null
                ? player.getAddress().getAddress().getHostAddress() : "unknown";

        if (sessionManager.canAutoReauth(player, ip)) {
            sessionManager.createSession(player);
            player.sendMessage("§a[Auth] 자동 재인증되었습니다.");
            return;
        }

        // Force survival mode on join (so they're not in creative/spectator)
        player.setGameMode(GameMode.SURVIVAL);

        // Save join location for movement restriction
        joinLocations.put(uuid, player.getLocation().clone());

        // Send auth prompt
        if (player.hasPlayedBefore()) {
            player.sendMessage("§e[Auth] 서버에 접속하셨습니다. §f/login <비밀번호> §e로 로그인하세요.");
        } else {
            player.sendMessage("§e[Auth] 처음 접속하셨습니다. §f/register <비밀번호> <비밀번호확인> §e으로 회원가입하세요.");
        }
        player.sendMessage("§c[Auth] 인증하지 않으면 " + configManager.getAuthTimeoutSeconds() + "초 후 서버에서 퇴장됩니다.");

        // Start timeout task
        int taskId = new BukkitRunnable() {
            @Override
            public void run() {
                if (!sessionManager.isAuthenticated(player) && player.isOnline()) {
                    player.kickPlayer("§c인증 시간이 초과되었습니다. 다시 접속 후 인증해주세요.");
                }
            }
        }.runTaskLater(plugin, configManager.getAuthTimeoutSeconds() * 20L).getTaskId();

        timeoutTasks.put(uuid, taskId);
    }

    // -------------------------------------------------------------------------
    // Quit
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Cancel timeout task if still running
        Integer taskId = timeoutTasks.remove(uuid);
        if (taskId != null) {
            plugin.getServer().getScheduler().cancelTask(taskId);
        }

        // Clean up session and join location
        sessionManager.invalidateSession(player);
        joinLocations.remove(uuid);
        configManager.removePlayerLanguage(uuid);
    }

    // -------------------------------------------------------------------------
    // Movement restriction
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (sessionManager.isAuthenticated(player)) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        // Allow head rotation (only block actual position change)
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        // Teleport back to join location
        Location joinLoc = joinLocations.get(player.getUniqueId());
        if (joinLoc != null) {
            // Preserve yaw/pitch so it doesn't feel jarring
            joinLoc.setYaw(to.getYaw());
            joinLoc.setPitch(to.getPitch());
            event.setTo(joinLoc);
        } else {
            event.setCancelled(true);
        }
    }

    // -------------------------------------------------------------------------
    // Chat restriction
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (sessionManager.isAuthenticated(player)) return;

        event.setCancelled(true);
        player.sendMessage("§c[Auth] 채팅을 사용하려면 먼저 인증하세요.");
        if (player.hasPlayedBefore()) {
            player.sendMessage("§e/login <비밀번호>");
        } else {
            player.sendMessage("§e/register <비밀번호> <비밀번호확인>");
        }
    }

    // -------------------------------------------------------------------------
    // Command restriction
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (sessionManager.isAuthenticated(player)) return;

        String message = event.getMessage().toLowerCase();

        // Allow only /register and /login
        if (message.startsWith("/register") || message.startsWith("/login")) {
            return;
        }

        event.setCancelled(true);
        player.sendMessage("§c[Auth] 인증 후 명령어를 사용할 수 있습니다.");
        if (player.hasPlayedBefore()) {
            player.sendMessage("§e/login <비밀번호>");
        } else {
            player.sendMessage("§e/register <비밀번호> <비밀번호확인>");
        }
    }

    // -------------------------------------------------------------------------
    // Damage restriction (prevent mobs/environment from killing unauthenticated players)
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        if (sessionManager.isAuthenticated(player)) return;

        event.setCancelled(true);
    }

    // -------------------------------------------------------------------------
    // Interaction restriction
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (sessionManager.isAuthenticated(player)) return;

        event.setCancelled(true);
    }

    // -------------------------------------------------------------------------
    // Inventory restriction
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        if (sessionManager.isAuthenticated(player)) return;

        event.setCancelled(true);
    }

    // -------------------------------------------------------------------------
    // Called by command handlers after successful auth to cancel timeout
    // -------------------------------------------------------------------------

    /**
     * Cancel the timeout task for a player who has just authenticated.
     * Should be called from LoginCommand / RegisterCommand after session creation.
     */
    public void onPlayerAuthenticated(Player player) {
        Integer taskId = timeoutTasks.remove(player.getUniqueId());
        if (taskId != null) {
            plugin.getServer().getScheduler().cancelTask(taskId);
        }
        joinLocations.remove(player.getUniqueId());
    }

    // -------------------------------------------------------------------------
    // Config reload
    // -------------------------------------------------------------------------

    public void updateConfiguration(ConfigurationManager configManager) {
        this.configManager = configManager;
    }
}
