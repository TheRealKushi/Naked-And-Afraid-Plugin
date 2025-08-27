package com.crimsonwarpedcraft.nakedandafraid.listeners;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * A listener that restricts chat to server operators only, cancelling messages from non-op players.
 */
public class ChatRestrictionListener implements Listener {

    /**
     * Cancels chat messages from players who are not server operators.
     *
     * @param event the AsyncChatEvent triggered when a player sends a chat message
     */
    @EventHandler
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!player.isOp()) {
            event.setCancelled(true);
        }
    }
}