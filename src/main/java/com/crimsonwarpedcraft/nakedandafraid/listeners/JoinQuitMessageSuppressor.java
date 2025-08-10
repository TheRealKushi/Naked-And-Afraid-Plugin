package com.crimsonwarpedcraft.nakedandafraid.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Listener to suppress join and quit messages.
 */
public class JoinQuitMessageSuppressor implements Listener {

    /**
     * Suppress join messages.
     *
     * @param event Player join event.
     */
    @EventHandler
    public void onPlayerJoin(final PlayerJoinEvent event) {
        event.joinMessage(null);
    }

    /**
     * Suppress quit messages.
     *
     * @param event Player quit event.
     */
    @EventHandler
    public void onPlayerQuit(final PlayerQuitEvent event) {
        event.quitMessage(null);
    }
}
