package com.crimsonwarpedcraft.nakedandafraid.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class GlobalDeathSoundListener implements Listener {

    private final JavaPlugin plugin;

    // Constructor to receive the main plugin instance
    public GlobalDeathSoundListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.ENTITY_IRON_GOLEM_DEATH, 100.0f, 0.1f);
            debugLog("Playing custom death sound for " + player.getName());
        }
    }

    private void debugLog(String message) {
        if (plugin.getConfig().getBoolean("debug-mode", false)) {
            plugin.getLogger().info(message);
        }
    }
}
