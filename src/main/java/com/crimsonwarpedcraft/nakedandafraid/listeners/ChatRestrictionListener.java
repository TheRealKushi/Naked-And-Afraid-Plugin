package com.crimsonwarpedcraft.nakedandafraid.listeners;

import com.crimsonwarpedcraft.nakedandafraid.NakedAndAfraid;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * A listener that restricts chat to server operators only, cancelling messages from non-op players.
 */
public class ChatRestrictionListener implements Listener {

    private final NakedAndAfraid plugin;

    /**
     * Constructs a new ChatRestrictionListener with the given plugin instance for debug logging.
     *
     * @param plugin the NakedAndAfraid plugin instance
     */
    public ChatRestrictionListener(NakedAndAfraid plugin) {
        this.plugin = plugin;
        plugin.debugLog("[ChatRestrictionListener] Initialized ChatRestrictionListener");
    }

    /**
     * Cancels chat messages from players who are not server operators.
     *
     * @param event the AsyncChatEvent triggered when a player sends a chat message
     */
    @EventHandler
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        plugin.debugLog("[ChatRestrictionListener] Processing chat event for " + player.getName());

        if (!player.isOp()) {
            event.setCancelled(true);
            plugin.debugLog("[ChatRestrictionListener] Cancelled chat for non-op player " + player.getName());
        } else {
            plugin.debugLog("[ChatRestrictionListener] Allowed chat for op player " + player.getName());
        }
    }
}