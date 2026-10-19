package com.therealkushi.nakedandafraid.v1_21.listeners;

import com.therealkushi.nakedandafraid.v1_21.NakedAndAfraid;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Handles Paper's AsyncChatEvent. Deliberately isolated in its own file - see
 * {@link ChatRestrictionListener}'s javadoc for why this class must only ever
 * be loaded once Paper's chat API is confirmed present.
 */
public class PaperAsyncChatListener implements Listener {

    private final NakedAndAfraid plugin;

    public PaperAsyncChatListener(NakedAndAfraid plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!plugin.isWorldEnabled(player.getWorld().getName())) {
            plugin.debugLog("[PaperAsyncChatListener] Skipped AsyncChatEvent for " + player.getName() +
                    " in disabled world " + player.getWorld().getName());
            return;
        }
        plugin.debugLog("[PaperAsyncChatListener] Processing AsyncChatEvent for " + player.getName());

        if (!player.isOp()) {
            event.setCancelled(true);
            plugin.debugLog("[PaperAsyncChatListener] Cancelled AsyncChatEvent for op " + player.getName());
        } else {
            plugin.debugLog("[PaperAsyncChatListener] Allowed AsyncChatEvent for op " + player.getName());
        }
    }
}