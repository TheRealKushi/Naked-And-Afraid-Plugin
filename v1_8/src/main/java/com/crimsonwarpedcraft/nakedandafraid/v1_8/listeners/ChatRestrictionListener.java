// V1_8/ChatRestrictionListener

package com.crimsonwarpedcraft.nakedandafraid.v1_8.listeners;

import com.crimsonwarpedcraft.nakedandafraid.v1_8.NakedAndAfraid;
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
                Bukkit.getBukkitVersion() + ", PaperChatSupported: false (legacy Spigot/1.16.5)");
    }

    /**
     * Cancels chat messages from players who are not server operators in enabled worlds (Spigot 1.12–1.16.5).
     *
     * @param event the AsyncPlayerChatEvent triggered when a player sends a chat message
     */
    @EventHandler
    public void onPlayerChatLegacy(AsyncPlayerChatEvent event) {
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
