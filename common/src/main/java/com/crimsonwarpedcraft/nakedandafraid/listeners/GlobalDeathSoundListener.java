package com.crimsonwarpedcraft.nakedandafraid.listeners;

import com.crimsonwarpedcraft.nakedandafraid.NakedAndAfraid;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public class GlobalDeathSoundListener implements Listener {

    private final NakedAndAfraid plugin;

    public GlobalDeathSoundListener(NakedAndAfraid plugin) {
        this.plugin = plugin;
        plugin.debugLog("[GlobalDeathSoundListener] Initialized GlobalDeathSoundListener for Bukkit version " +
                Bukkit.getBukkitVersion());
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player deceased = event.getEntity();
        plugin.debugLog("[GlobalDeathSoundListener] Player " + deceased.getName() + " died");

        if (!plugin.isWorldEnabled(deceased.getWorld().getName())) {
            plugin.debugLog("[GlobalDeathSoundListener] Skipped death sound for " + deceased.getName() +
                    " in disabled world " + deceased.getWorld().getName());
            return;
        }

        if (!plugin.getConfig().getBoolean("death-sound", true)) {
            plugin.debugLog("[GlobalDeathSoundListener] Death sound disabled in config, skipping sound for " +
                    deceased.getName());
            return;
        }

        plugin.debugLog("[GlobalDeathSoundListener] Playing death sound for all online players due to " +
                deceased.getName() + "'s death");
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!plugin.isWorldEnabled(player.getWorld().getName())) {
                plugin.debugLog("[GlobalDeathSoundListener] Skipped playing sound for " + player.getName() +
                        " in disabled world " + player.getWorld().getName());
                continue;
            }

            try {
                player.playSound(
                        player.getLocation(),
                        Sound.ENTITY_IRON_GOLEM_DEATH,
                        100.0f,
                        0.1f
                );
                plugin.debugLog("[GlobalDeathSoundListener] Played sound ENTITY_IRON_GOLEM_DEATH for " +
                        player.getName() + " at " + formatLocation(player.getLocation()) +
                        " (volume=100.0, pitch=0.1)");
            } catch (Exception e) {
                plugin.debugLog("[GlobalDeathSoundListener] Failed to play sound for " + player.getName() +
                        ": " + e.getMessage());
                try {
                    player.playSound(
                            player.getLocation(),
                            Sound.BLOCK_ANVIL_DESTROY,
                            100.0f,
                            0.1f
                    );
                    plugin.debugLog("[GlobalDeathSoundListener] Played fallback sound BLOCK_ANVIL_DESTROY for " +
                            player.getName() + " at " + formatLocation(player.getLocation()) +
                            " (volume=100.0, pitch=0.1)");
                } catch (Exception fallbackEx) {
                    plugin.debugLog("[GlobalDeathSoundListener] Failed to play fallback sound for " +
                            player.getName() + ": " + fallbackEx.getMessage());
                }
            }
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