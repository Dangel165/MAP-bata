package com.authplugin.utils;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Displays a countdown action-bar to unauthenticated players.
 * Cancels automatically when the player authenticates or disconnects.
 */
public class TimeoutDisplay {

    private final Plugin plugin;
    private final MessageManager messages;
    // playerUUID -> task
    private final Map<UUID, BukkitRunnable> tasks = new ConcurrentHashMap<>();

    public TimeoutDisplay(Plugin plugin, MessageManager messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    /**
     * Start a countdown display for the given player.
     * @param player         the unauthenticated player
     * @param timeoutSeconds total seconds before kick
     */
    public void start(Player player, int timeoutSeconds) {
        stop(player); // cancel any existing task

        BukkitRunnable task = new BukkitRunnable() {
            int remaining = timeoutSeconds;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    tasks.remove(player.getUniqueId());
                    return;
                }

                if (remaining <= 0) {
                    cancel();
                    tasks.remove(player.getUniqueId());
                    return;
                }

                // Show action bar countdown
                String bar = messages.get("auth.timeout-warning", "seconds", String.valueOf(remaining));
                player.sendTitle("", bar, 0, 25, 5);

                // Warn at 30s and 10s
                if (remaining == 30 || remaining == 10) {
                    player.sendMessage(bar);
                }

                remaining--;
            }
        };

        tasks.put(player.getUniqueId(), task);
        task.runTaskTimer(plugin, 0L, 20L); // every second
    }

    /** Stop the countdown for a player (called on successful auth or disconnect). */
    public void stop(Player player) {
        BukkitRunnable existing = tasks.remove(player.getUniqueId());
        if (existing != null) {
            try { existing.cancel(); } catch (IllegalStateException ignored) {}
        }
        // Clear action bar
        if (player.isOnline()) {
            player.sendTitle("", "", 0, 1, 0);
        }
    }

    /** Stop all active countdowns (e.g., on plugin disable). */
    public void stopAll() {
        tasks.values().forEach(t -> {
            try { t.cancel(); } catch (IllegalStateException ignored) {}
        });
        tasks.clear();
    }
}
