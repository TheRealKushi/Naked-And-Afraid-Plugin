package com.crimsonwarpedcraft.nakedandafraid.listeners;

import com.crimsonwarpedcraft.nakedandafraid.NakedAndAfraid;
import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.UUID;

/**
 * A listener that applies periodic damage to players wearing armor, with full support for dynamic
 * enabling/disabling through config reloads.
 */
public class ArmorDamageListener implements Listener {

    private final NakedAndAfraid plugin;

    private final HashMap<UUID, BukkitRunnable> damageTasks = new HashMap<>();
    private double damageAmount;
    private long damageIntervalTicks;
    private boolean armorEnabled;

    public ArmorDamageListener(NakedAndAfraid plugin) {
        this.plugin = plugin;
        loadConfigValues();
    }

    /** Load configuration values (damage amount, interval, enabled) from plugin config */
    private void loadConfigValues() {
        this.armorEnabled = plugin.getConfig().getBoolean("armor-damage.enabled", true);
        this.damageAmount = plugin.getConfig().getDouble("armor-damage.damage-amount", 1.0);
        this.damageIntervalTicks = plugin.getConfig().getLong("armor-damage.damage-interval-ticks", 20L);
    }

    /** Refresh tasks for all online players according to current config */
    public void refreshArmorTasks() {
        loadConfigValues();
        plugin.debugLog("Refreshing armor tasks, armorEnabled: " + armorEnabled);

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            boolean hasArmor = hasAnyArmor(player);
            plugin.debugLog("Checking player " + player.getName() + ", hasArmor: " + hasArmor);

            if (!armorEnabled || !hasArmor) {
                if (damageTasks.containsKey(uuid)) {
                    cancelDamageTask(player);
                }
            } else if (hasArmor && armorEnabled && !damageTasks.containsKey(uuid)) {
                plugin.debugLog("Starting armor damage task for player " + player.getName());
                startDamageTask(player);
            }
        }
    }

    @EventHandler
    public void onPlayerArmorChange(PlayerArmorChangeEvent event) {
        plugin.debugLog("Armor change event fired for player " + event.getPlayer().getName() +
                ", New slot: " + event.getSlotType() +
                ", New item: " + event.getNewItem().getType() +
                ", Old item: " + event.getOldItem().getType());
        if (!armorEnabled) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (hasAnyArmor(player)) {
            if (!damageTasks.containsKey(uuid)) {
                plugin.debugLog("Starting armor damage task for player " + player.getName());
                startDamageTask(player);
            }
        } else {
            if (damageTasks.containsKey(uuid)) {
                plugin.debugLog("Cancelling armor damage task for player " + player.getName());
                cancelDamageTask(player);
            }
        }
    }

    /** Returns true if player has at least one armor piece */
    private boolean hasAnyArmor(Player player) {
        ItemStack[] armorContents = player.getInventory().getArmorContents();
        if (armorContents == null) return false;
        for (ItemStack armor : armorContents) {
            if (armor != null && armor.getType() != Material.AIR) {
                plugin.debugLog("Found armor: " + armor.getType() + " on player " + player.getName());
                return true;
            }
        }
        plugin.debugLog("No armor found on player " + player.getName());
        return false;
    }

    /** Start repeating task to damage player */
    private void startDamageTask(Player player) {
        UUID uuid = player.getUniqueId();
        if (damageTasks.containsKey(uuid)) {
            plugin.debugLog("Task already exists for player " + player.getName() + ", cancelling old task");
            cancelDamageTask(player);
        }

        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || player.isDead() || player.getGameMode() == GameMode.CREATIVE || !armorEnabled) {
                    cancel();
                    damageTasks.remove(player.getUniqueId());
                    plugin.debugLog("Task auto-cancelled for player " + player.getName() + ", reason: offline/dead/creative/disabled");
                    return;
                }
                if (!hasAnyArmor(player)) {
                    cancel();
                    damageTasks.remove(player.getUniqueId());
                    plugin.debugLog("Task auto-cancelled for player " + player.getName() + ", reason: no armor");
                    return;
                }

                double damageInHP = damageAmount * 2; // convert to HP
                player.damage(damageInHP);
                plugin.debugLog("Applied armor damage to player " + player.getName());
            }
        };
        task.runTaskTimer(plugin, 0L, damageIntervalTicks);
        damageTasks.put(uuid, task);
        plugin.debugLog("Started new armor damage task for player " + player.getName());
    }

    /** Cancel a player's damage task if it exists */
    private void cancelDamageTask(Player player) {
        UUID uuid = player.getUniqueId();
        BukkitRunnable task = damageTasks.remove(uuid);
        if (task != null) {
            task.cancel();
            plugin.debugLog("Cancelled armor damage task for player " + player.getName());
        } else {
            plugin.debugLog("No active damage task found for player " + player.getName());
        }
    }

    /** Cancel all running tasks (plugin disable, etc) */
    public void disableAllTasks() {
        for (BukkitRunnable task : damageTasks.values()) {
            task.cancel();
        }
        damageTasks.clear();
    }
}
