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

public class ArmorDamageListener implements Listener {

    private final JavaPlugin plugin;
    private final HashMap<UUID, BukkitRunnable> damageTasks = new HashMap<>();
    private final double damageAmount;
    private final long damageIntervalTicks;

    public ArmorDamageListener(final JavaPlugin plugin) {
        this.plugin = plugin;
        this.damageAmount = plugin.getConfig().getDouble("armor-damage.damage-amount", 1.0);
        this.damageIntervalTicks = plugin.getConfig().getLong("armor-damage.damage-interval-ticks", 20L);
    }

    private void debugLog(String message) {
        if (plugin.getConfig().getBoolean("debug-mode", false)) {
            plugin.getLogger().info(message);
        }
    }

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

    private boolean hasAnyArmor(final Player player) {
        for (ItemStack armorPiece : player.getInventory().getArmorContents()) {
            if (armorPiece != null && !armorPiece.getType().isAir()) {
                return true;
            }
        }
        return false;
    }

    private void startDamageTask(final Player player) {
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

    private void cancelDamageTask(final Player player) {
        BukkitRunnable task = damageTasks.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }
}

