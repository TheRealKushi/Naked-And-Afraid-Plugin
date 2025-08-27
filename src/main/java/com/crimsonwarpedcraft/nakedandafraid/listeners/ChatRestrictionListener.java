package com.crimsonwarpedcraft.nakedandafraid.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import io.papermc.paper.event.player.AsyncChatEvent;

/**
 * Listener to restrict chat to server operators only.
 */
public class ChatRestrictionListener implements Listener {

    /**
     * Cancels chat messages from non-ops.
     *
     * @param event Player chat event.
     */
    @EventHandler
    public void onPlayerChat(final AsyncChatEvent event) {
        Player player = event.getPlayer();

        if (!player.isOp()) {
            event.setCancelled(true);
        }
    }
}
