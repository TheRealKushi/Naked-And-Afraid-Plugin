package com.crimsonwarpedcraft.nakedandafraid.listeners;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.UUID;

/**
 * A listener that applies periodic damage to players wearing armor, based on configuration settings.
 */
public class ArmorDamageListener implements Listener {
    private final JavaPlugin plugin;
    private final HashMap<UUID, BukkitRunnable> damageTasks;
    private final double damageAmount;
    private final long damageIntervalTicks;

    /**
     * Constructs an ArmorDamageListener with configuration values from the plugin's config file.
     *
     * @param plugin the JavaPlugin instance to access configuration and scheduling
     */
    public ArmorDamageListener(JavaPlugin plugin) {
        this.plugin = plugin;
        this.damageTasks = new HashMap<>();
        this.damageAmount = plugin.getConfig().getDouble("armor-damage.damage-amount", 1.0);
        this.damageIntervalTicks = plugin.getConfig().getLong("armor-damage.damage-interval-ticks", 20L);
    }

    /**
     * Logs a debug message if debug mode is enabled in the plugin's configuration.
     *
     * @param message the debug message to log
     */
    private void debugLog(String message) {
        if (plugin.getConfig().getBoolean("debug-mode", false)) {
            plugin.getLogger().info(message);
        }
    }

    /**
     * Handles armor change events, starting or stopping damage tasks based on whether the player
     * is wearing armor.
     *
     * @param event the PlayerArmorChangeEvent triggered when a player's armor changes
     */
    @EventHandler
    public void onPlayerArmorChange(PlayerArmorChangeEvent event) {
        Player player = event.getPlayer();
        debugLog("Armor changed for player " + player.getName());

        if (hasAnyArmor(player)) {
            debugLog("Player " + player.getName() + " is wearing armor. Starting damage task.");
            if (!damageTasks.containsKey(player.getUniqueId())) {
                startDamageTask(player);
            }
        } else {
            debugLog("Player " + player.getName() + " has no armor. Cancelling damage task.");
            cancelDamageTask(player);
        }
    }

    /**
     * Checks if the player is wearing any non-empty armor pieces.
     *
     * @param player the player to check
     * @return true if the player has at least one armor piece equipped, false otherwise
     */
    private boolean hasAnyArmor(Player player) {
        for (ItemStack armorPiece : player.getInventory().getArmorContents()) {
            if (armorPiece != null && !armorPiece.getType().isAir()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Starts a repeating task to periodically damage the player while they wear armor.
     *
     * @param player the player to apply damage to
     */
    private void startDamageTask(Player player) {
        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || player.getGameMode().name().equalsIgnoreCase("CREATIVE")) {
                    cancel();
                    damageTasks.remove(player.getUniqueId());
                    return;
                }
                debugLog("Applying scheduled damage to player " + player.getName());
                double damageInHP = damageAmount * 2;
                player.damage(damageInHP);
            }
        };
        task.runTaskTimer(plugin, 0L, damageIntervalTicks);
        damageTasks.put(player.getUniqueId(), task);
    }

    /**
     * Cancels the damage task for a player, if it exists.
     *
     * @param player the player whose damage task should be cancelled
     */
    private void cancelDamageTask(Player player) {
        BukkitRunnable task = damageTasks.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }
}