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

    public GlobalDeathSoundListener(JavaPlugin plugin) {
        this.plugin = plugin;
        debugLog("[GlobalDeathSoundListener] Initialized GlobalDeathSoundListener");
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player deceased = event.getEntity();
        debugLog("[GlobalDeathSoundListener] Player " + deceased.getName() + " died");

        if (!plugin.getConfig().getBoolean("death-sound", true)) {
            debugLog("[GlobalDeathSoundListener] Death sound disabled in config, skipping sound for " + deceased.getName());
            return;
        }

        debugLog("[GlobalDeathSoundListener] Playing death sound for all online players due to " + deceased.getName() + "'s death");
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(
                    player.getLocation(),
                    Sound.ENTITY_IRON_GOLEM_DEATH,
                    100.0f,
                    0.1f
            );
            debugLog("[GlobalDeathSoundListener] Played sound ENTITY_IRON_GOLEM_DEATH for " +
                    player.getName() + " at " + formatLocation(player.getLocation()) +
                    " (volume=100.0, pitch=0.1)");
        }
    }

    private void debugLog(String message) {
        if (plugin.getConfig().getBoolean("debug-mode", false)) {
            plugin.getLogger().info(message);
        }
    }

    /**
     * Helper method to format a Location for debug logging.
     */
    private String formatLocation(org.bukkit.Location location) {
        return String.format("(world=%s, x=%.2f, y=%.2f, z=%.2f)",
                location.getWorld() != null ? location.getWorld().getName() : "null",
                location.getX(), location.getY(), location.getZ());
    }
}