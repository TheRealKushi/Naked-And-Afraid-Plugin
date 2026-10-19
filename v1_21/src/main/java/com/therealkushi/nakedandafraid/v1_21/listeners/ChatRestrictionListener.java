// V1_21/ChatRestrictionListener
package com.therealkushi.nakedandafraid.v1_21.listeners;

import com.therealkushi.nakedandafraid.v1_21.NakedAndAfraid;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/**
 * Restricts chat to server operators only.
 * <p>
 * The Paper-only handler for {@code AsyncChatEvent} now lives in
 * {@link PaperAsyncChatListener}, a separate class only ever loaded/registered
 * once {@link #isPaperChatSupported()} confirms Paper's chat API exists.
 * Keeping it out of this class means CraftBukkit/Spigot never has to resolve
 * that type at all, instead of failing to register it with a warning.
 */
public class ChatRestrictionListener implements Listener {

    private final NakedAndAfraid plugin;
    private PaperAsyncChatListener paperCompanion;

    /**
     * Constructs a new ChatRestrictionListener with the given plugin instance for debug logging and world checks.
     *
     * @param plugin the NakedAndAfraid plugin instance
     */
    public ChatRestrictionListener(NakedAndAfraid plugin) {
        this.plugin = plugin;
        plugin.debugLog("[ChatRestrictionListener] Initialized for Bukkit version " +
                Bukkit.getBukkitVersion() + ", PaperChatSupported: " + isPaperChatSupported());

        if (isPaperChatSupported()) {
            paperCompanion = new PaperAsyncChatListener(plugin);
            Bukkit.getPluginManager().registerEvents(paperCompanion, plugin.getPlugin());
            plugin.debugLog("[ChatRestrictionListener] Registered PaperAsyncChatListener companion");
        }
    }

    /** Unregisters the Paper companion, if one was created. Call this before discarding this listener. */
    public void shutdownPaperCompanion() {
        if (paperCompanion != null) {
            HandlerList.unregisterAll(paperCompanion);
            plugin.debugLog("[ChatRestrictionListener] Unregistered PaperAsyncChatListener companion");
            paperCompanion = null;
        }
    }

    private static Boolean paperChatSupported;

    private boolean isPaperChatSupported() {
        if (paperChatSupported == null) {
            try {
                Class.forName("io.papermc.paper.event.player.AsyncChatEvent");
                paperChatSupported = true;
            } catch (Throwable t) {
                plugin.debugLog("[ChatRestrictionListener] AsyncChatEvent not present: " + t.getMessage());
                paperChatSupported = false;
            }
        }
        return paperChatSupported;
    }

    /** Handles chat on Bukkit/Spigot/pre-Paper-API servers, and as a Paper fallback (Paper still fires this too). */
    @EventHandler
    public void onPlayerChatLegacy(AsyncPlayerChatEvent event) {
        if (isPaperChatSupported()) {
            // Paper fires both events; PaperAsyncChatListener already handled it.
            return;
        }
        Player player = event.getPlayer();
        if (!plugin.isWorldEnabled(player.getWorld().getName())) {
            plugin.debugLog("[ChatRestrictionListener] Skipped AsyncPlayerChatEvent for " + player.getName() +
                    " in disabled world " + player.getWorld().getName());
            return;
        }
        plugin.debugLog("[ChatRestrictionListener] Processing AsyncPlayerChatEvent for " + player.getName());

        if (!player.isOp()) {
            event.setCancelled(true);
            plugin.debugLog("[ChatRestrictionListener] Cancelled AsyncPlayerChatEvent for non-op " + player.getName());
        } else {
            plugin.debugLog("[ChatRestrictionListener] Allowed AsyncPlayerChatEvent for op " + player.getName());
        }
    }
}