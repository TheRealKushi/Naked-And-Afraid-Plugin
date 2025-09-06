package com.crimsonwarpedcraft.nakedandafraid.listeners;

import com.crimsonwarpedcraft.nakedandafraid.NakedAndAfraid;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Listener to suppress join and quit messages.
 */
public class JoinQuitMessageSuppressor implements Listener {
    private final NakedAndAfraid plugin;

    /**
     * Constructor to initialize with the plugin instance.
     *
     * @param plugin The NakedAndAfraid plugin instance.
     */
    public JoinQuitMessageSuppressor(NakedAndAfraid plugin) {
        this.plugin = plugin;
        plugin.debugLog("[JoinQuitMessageSuppressor] Initialized JoinQuitMessageSuppressor for Bukkit version " +
                Bukkit.getBukkitVersion());
    }

    /**
     * Checks if the server is pre-1.12.2 (Minecraft 1.12–1.12.1).
     */
    private boolean isPre1122() {
        try {
            String version = Bukkit.getBukkitVersion().split("-")[0];
            String[] parts = version.split("\\.");
            int major = Integer.parseInt(parts[1]);
            int minor = parts.length > 2 ? Integer.parseInt(parts[2].split("[^0-9]")[0]) : 0;
            return major == 12 && minor < 2;
        } catch (Exception e) {
            plugin.debugLog("[JoinQuitMessageSuppressor] Failed to parse Bukkit version: " + e.getMessage());
            return true; // Fallback to legacy methods if version parsing fails
        }
    }

    /**
     * Suppress join messages.
     *
     * @param event Player join event.
     */
    @EventHandler
    public void onPlayerJoin(final PlayerJoinEvent event) {
        String playerName = event.getPlayer().getName();
        plugin.debugLog("[JoinQuitMessageSuppressor] Suppressing join message for player " + playerName);

        if (isPre1122()) {
            event.setJoinMessage(null);
            plugin.debugLog("[JoinQuitMessageSuppressor] Used setJoinMessage(null) for " + playerName + " (pre-1.12.2)");
        } else {
            event.joinMessage(null);
            plugin.debugLog("[JoinQuitMessageSuppressor] Used joinMessage(null) for " + playerName + " (1.12.2+)");
        }
    }

    /**
     * Suppress quit messages.
     *
     * @param event Player quit event.
     */
    @EventHandler
    public void onPlayerQuit(final PlayerQuitEvent event) {
        String playerName = event.getPlayer().getName();
        plugin.debugLog("[JoinQuitMessageSuppressor] Suppressing quit message for player " + playerName);

        if (isPre1122()) {
            event.setQuitMessage(null);
            plugin.debugLog("[JoinQuitMessageSuppressor] Used setQuitMessage(null) for " + playerName + " (pre-1.12.2)");
        } else {
            event.quitMessage(null);
            plugin.debugLog("[JoinQuitMessageSuppressor] Used quitMessage(null) for " + playerName + " (1.12.2+)");
        }
    }
}