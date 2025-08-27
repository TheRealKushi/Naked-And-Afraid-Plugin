package com.crimsonwarpedcraft.nakedandafraid.listeners;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerItemBreakEvent;
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

        // Periodic task to check online players
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (hasAnyArmor(player)) {
                        if (!damageTasks.containsKey(player.getUniqueId())) {
                            startDamageTask(player);
                        }
                    } else {
                        cancelDamageTask(player);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); // checks every second
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // Check if player is changing armor
        if (event.getSlotType() == InventoryType.SlotType.ARMOR) {
            Player player = (Player) event.getWhoClicked();
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (hasAnyArmor(player)) {
                    startDamageTask(player);
                } else {
                    cancelDamageTask(player);
                }
            });
        }
    }

    @EventHandler
    public void onItemBreak(PlayerItemBreakEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!hasAnyArmor(player)) {
                cancelDamageTask(player);
            }
        });
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
        if (damageTasks.containsKey(player.getUniqueId())) return;

        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || player.getGameMode() == GameMode.CREATIVE) {
                    cancel();
                    damageTasks.remove(player.getUniqueId());
                    return;
                }

                double damageInHP = damageAmount * 2;
                player.damage(damageInHP);

                if (plugin.getConfig().getBoolean("debug-mode", false)) {
                    plugin.getLogger().info("Applying armor damage to player " + player.getName());
                }
            }
        };
        task.runTaskTimer(plugin, 0L, damageIntervalTicks);
        damageTasks.put(player.getUniqueId(), task);
    }

    private void cancelDamageTask(final Player player) {
        BukkitRunnable task = damageTasks.remove(player.getUniqueId());
        if (task != null) task.cancel();
    }
}
