package com.crimsonwarpedcraft.nakedandafraid.listeners;

import com.crimsonwarpedcraft.nakedandafraid.NakedAndAfraid;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/**
 * A listener that restricts chat to server operators only, cancelling messages from non-op players
 * in worlds where the plugin is enabled.
 */
public class ChatRestrictionListener implements Listener {

    private final NakedAndAfraid plugin;

    /**
     * Constructs a new ChatRestrictionListener with the given plugin instance for debug logging and world checks.
     *
     * @param plugin the NakedAndAfraid plugin instance
     */
    public ChatRestrictionListener(NakedAndAfraid plugin) {
        this.plugin = plugin;
        plugin.debugLog("[ChatRestrictionListener] Initialized ChatRestrictionListener for Bukkit version " +
                Bukkit.getBukkitVersion() + ", PaperChatSupported: " + isPaperChatSupported());
    }

    /**
     * Checks if the server supports Paper's AsyncChatEvent (Paper 1.19+).
     */
    private boolean isPaperChatSupported() {
        try {
            String version = Bukkit.getBukkitVersion().split("-")[0];
            String[] parts = version.split("\\.");
            int major = Integer.parseInt(parts[1]);
            return major >= 19 &&
                    Class.forName("io.papermc.paper.event.player.AsyncChatEvent") != null;
        } catch (Exception e) {
            plugin.debugLog("[ChatRestrictionListener] Failed to detect AsyncChatEvent support: " + e.getMessage());
            return false;
        }
    }

    /**
     * Checks if the server is pre-1.19 (Minecraft 1.12–1.18.2).
     */
    private boolean isPre119() {
        return !Bukkit.getBukkitVersion().matches(".*1\\.(1[9]|2[0-1]).*");
    }

    /**
     * Cancels chat messages from players who are not server operators in enabled worlds (Paper 1.19+).
     *
     * @param event the AsyncChatEvent triggered when a player sends a chat message
     */
    @EventHandler
    public void onPlayerChat(AsyncChatEvent event) {
        if (!isPaperChatSupported()) {
            return;
        }
        Player player = event.getPlayer();
        if (!plugin.isWorldEnabled(player.getWorld().getName())) {
            plugin.debugLog("[ChatRestrictionListener] Skipped AsyncChatEvent for player " + player.getName() +
                    " in disabled world " + player.getWorld().getName());
            return;
        }
        plugin.debugLog("[ChatRestrictionListener] Processing AsyncChatEvent for " + player.getName());

        if (!player.isOp()) {
            event.setCancelled(true);
            plugin.debugLog("[ChatRestrictionListener] Cancelled AsyncChatEvent for non-op player " + player.getName());
        } else {
            plugin.debugLog("[ChatRestrictionListener] Allowed AsyncChatEvent for op player " + player.getName());
        }
    }

    /**
     * Cancels chat messages from players who are not server operators in enabled worlds (pre-1.19 or non-Paper).
     *
     * @param event the AsyncPlayerChatEvent triggered when a player sends a chat message
     */
    @EventHandler
    public void onPlayerChatLegacy(AsyncPlayerChatEvent event) {
        if (isPaperChatSupported()) {
            return;
        }
        Player player = event.getPlayer();
        if (!plugin.isWorldEnabled(player.getWorld().getName())) {
            plugin.debugLog("[ChatRestrictionListener] Skipped AsyncPlayerChatEvent for player " + player.getName() +
                    " in disabled world " + player.getWorld().getName());
            return;
        }
        plugin.debugLog("[ChatRestrictionListener] Processing AsyncPlayerChatEvent for " + player.getName());

        if (!player.isOp()) {
            event.setCancelled(true);
            plugin.debugLog("[ChatRestrictionListener] Cancelled AsyncPlayerChatEvent for non-op player " + player.getName());
        } else {
            plugin.debugLog("[ChatRestrictionListener] Allowed AsyncPlayerChatEvent for op player " + player.getName());
        }
    }
}