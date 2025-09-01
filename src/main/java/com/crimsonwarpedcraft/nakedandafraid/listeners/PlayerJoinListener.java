package com.crimsonwarpedcraft.nakedandafraid.listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class PlayerJoinListener implements Listener {

    private final JavaPlugin plugin;

    public PlayerJoinListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player joiningPlayer = event.getPlayer();

        // Hide all currently hidden players from the joining player
        Bukkit.getOnlinePlayers().forEach(player -> {
            if (TabListClearer.isHidden(player) && !player.equals(joiningPlayer)) {
                joiningPlayer.hidePlayer(plugin, player);
            }
        });

        // If the joining player should be hidden, hide them from everyone else
        if (TabListClearer.isHidden(joiningPlayer)) {
            TabListClearer.hidePlayer(joiningPlayer, plugin);
        }
    }
}
