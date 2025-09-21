package com.crimsonwarpedcraft.nakedandafraid.v1_17.util;

import com.crimsonwarpedcraft.nakedandafraid.v1_17.NakedAndAfraid;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.plugin.Plugin;

import java.util.Set;

/**
 * Extension of TeleportHelper to handle PlayerSwapHandItemsEvent for Minecraft 1.12+.
 */
public class TeleportHelperExtension implements Listener {

    private final NakedAndAfraid nakedAndAfraid;
    private final Set<Player> frozenPlayers;

    public TeleportHelperExtension(Plugin plugin, NakedAndAfraid nakedAndAfraid, TeleportHelper teleportHelper) {
        this.nakedAndAfraid = nakedAndAfraid;
        this.frozenPlayers = teleportHelper.getFrozenPlayers();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        nakedAndAfraid.debugLog("[TeleportHelperExtension] Initialized for Bukkit version " + Bukkit.getBukkitVersion());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        if (frozenPlayers.contains(event.getPlayer())) {
            event.setCancelled(true);
            nakedAndAfraid.debugLog("[TeleportHelperExtension] Cancelled hand swap for " + event.getPlayer().getName());
        }
    }
}