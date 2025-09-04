package com.crimsonwarpedcraft.nakedandafraid.listeners;

import com.crimsonwarpedcraft.nakedandafraid.NakedAndAfraid;
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
        plugin.debugLog("[JoinQuitMessageSuppressor] Initialized JoinQuitMessageSuppressor");
    }

    /**
     * Suppress join messages.
     *
     * @param event Player join event.
     */
    @EventHandler
    public void onPlayerJoin(final PlayerJoinEvent event) {
        plugin.debugLog("[JoinQuitMessageSuppressor] Suppressing join message for player " + event.getPlayer().getName());
        event.joinMessage(null);
    }

    /**
     * Suppress quit messages.
     *
     * @param event Player quit event.
     */
    @EventHandler
    public void onPlayerQuit(final PlayerQuitEvent event) {
        plugin.debugLog("[JoinQuitMessageSuppressor] Suppressing quit message for player " + event.getPlayer().getName());
        event.quitMessage(null);
    }
}