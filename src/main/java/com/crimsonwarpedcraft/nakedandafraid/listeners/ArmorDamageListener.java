package com.crimsonwarpedcraft.nakedandafraid.listeners;

import com.crimsonwarpedcraft.nakedandafraid.NakedAndAfraid;
import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.UUID;

/**
 * A listener that applies periodic damage to players wearing armor, with full support for dynamic
 * enabling/disabling through config reloads and compatibility for Minecraft 1.12–1.21.8.
 */
public class ArmorDamageListener implements Listener {

    private final NakedAndAfraid plugin;
    private final HashMap<UUID, BukkitRunnable> damageTasks = new HashMap<>();
    private double damageAmount;
    private long damageIntervalTicks;
    private boolean armorEnabled;
    private BukkitRunnable pollingTask;

    public ArmorDamageListener(NakedAndAfraid plugin) {
        this.plugin = plugin;
        loadConfigValues();
        plugin.debugLog("[ArmorDamageListener] Initialized ArmorDamageListener for Bukkit version " +
                Bukkit.getBukkitVersion() + ", PaperArmorChangeSupported: " + isPaperArmorChangeSupported());
        if (!isPaperArmorChangeSupported()) {
            startPollingTask();
        }
    }

    /**
     * Checks if the server supports Paper's PlayerArmorChangeEvent (Paper 1.16.5+).
     */
    private boolean isPaperArmorChangeSupported() {
        try {
            String version = Bukkit.getBukkitVersion().split("-")[0];
            String[] parts = version.split("\\.");
            int major = Integer.parseInt(parts[1]);
            int minor = parts.length > 2 ? Integer.parseInt(parts[2].split("[^0-9]")[0]) : 0;
            return major > 16 || (major == 16 && minor >= 5) &&
                    Class.forName("com.destroystokyo.paper.event.player.PlayerArmorChangeEvent") != null;
        } catch (Exception e) {
            plugin.debugLog("[ArmorDamageListener] Failed to detect PaperArmorChangeEvent support: " + e.getMessage());
            return false;
        }
    }

    /**
     * Checks if the server is pre-1.16 (Minecraft 1.12–1.15.2).
     */
    private boolean isPre116() {
        return !Bukkit.getBukkitVersion().matches(".*1\\.(1[6-9]|2[0-1]).*");
    }

    /** Load configuration values (damage amount, interval, enabled) from plugin config */
    private void loadConfigValues() {
        this.armorEnabled = plugin.getConfig().getBoolean("armor-damage.enabled", true);
        this.damageAmount = plugin.getConfig().getDouble("armor-damage.damage-amount", 1.0);
        this.damageIntervalTicks = plugin.getConfig().getLong("armor-damage.damage-interval-ticks", 20L);
        plugin.debugLog("[ArmorDamageListener] Loaded config: armorEnabled=" + armorEnabled +
                ", damageAmount=" + damageAmount + ", damageIntervalTicks=" + damageIntervalTicks);
    }

    /** Refresh tasks for all online players according to current config */
    public void refreshArmorTasks() {
        loadConfigValues();
        plugin.debugLog("[ArmorDamageListener] Refreshing armor tasks, armorEnabled: " + armorEnabled);

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            boolean hasArmor = hasAnyArmor(player);
            plugin.debugLog("[ArmorDamageListener] Checking player " + player.getName() + ", hasArmor: " + hasArmor);

            if (!armorEnabled || !hasArmor) {
                if (damageTasks.containsKey(uuid)) {
                    cancelDamageTask(player);
                }
            } else if (hasArmor && armorEnabled && !damageTasks.containsKey(uuid)) {
                plugin.debugLog("[ArmorDamageListener] Starting armor damage task for player " + player.getName());
                startDamageTask(player);
            }
        }
    }

    /** Start a polling task to check armor for all online players (pre-1.16.5 or non-Paper) */
    private void startPollingTask() {
        if (pollingTask != null) {
            pollingTask.cancel();
            plugin.debugLog("[ArmorDamageListener] Cancelled existing polling task");
        }
        pollingTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    UUID uuid = player.getUniqueId();
                    boolean hasArmor = hasAnyArmor(player);
                    plugin.debugLog("[ArmorDamageListener] Polling player " + player.getName() + ", hasArmor: " + hasArmor);
                    if (!armorEnabled || !hasArmor) {
                        if (damageTasks.containsKey(uuid)) {
                            cancelDamageTask(player);
                        }
                    } else if (hasArmor && armorEnabled && !damageTasks.containsKey(uuid)) {
                        plugin.debugLog("[ArmorDamageListener] Starting armor damage task for player " + player.getName());
                        startDamageTask(player);
                    }
                }
            }
        };
        pollingTask.runTaskTimer(plugin, 0L, 20L); // Check every second (20 ticks)
        plugin.debugLog("[ArmorDamageListener] Started polling task for armor checks");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!isPaperArmorChangeSupported()) {
            Player player = event.getPlayer();
            plugin.debugLog("[ArmorDamageListener] Player " + player.getName() + " joined, checking armor (polling mode)");
            if (armorEnabled && hasAnyArmor(player) && !damageTasks.containsKey(player.getUniqueId())) {
                plugin.debugLog("[ArmorDamageListener] Starting armor damage task for joining player " + player.getName());
                startDamageTask(player);
            }
        }
    }

    @EventHandler
    public void onPlayerArmorChange(PlayerArmorChangeEvent event) {
        if (!armorEnabled) {
            plugin.debugLog("[ArmorDamageListener] Armor damage disabled, ignoring armor change for " +
                    event.getPlayer().getName());
            return;
        }

        Player player = event.getPlayer();
        plugin.debugLog("[ArmorDamageListener] Armor change event fired for player " + player.getName() +
                ", New slot: " + event.getSlotType() +
                ", New item: " + event.getNewItem().getType() +
                ", Old item: " + event.getOldItem().getType());

        UUID uuid = player.getUniqueId();
        if (hasAnyArmor(player)) {
            if (!damageTasks.containsKey(uuid)) {
                plugin.debugLog("[ArmorDamageListener] Starting armor damage task for player " + player.getName());
                startDamageTask(player);
            }
        } else {
            if (damageTasks.containsKey(uuid)) {
                plugin.debugLog("[ArmorDamageListener] Cancelling armor damage task for player " + player.getName());
                cancelDamageTask(player);
            }
        }
    }

    /** Returns true if player has at least one armor piece */
    private boolean hasAnyArmor(Player player) {
        ItemStack[] armorContents = player.getInventory().getArmorContents();
        if (armorContents == null) {
            plugin.debugLog("[ArmorDamageListener] Armor contents null for player " + player.getName());
            return false;
        }
        for (ItemStack armor : armorContents) {
            if (armor != null && armor.getType() != Material.AIR) {
                plugin.debugLog("[ArmorDamageListener] Found armor: " + armor.getType() + " on player " + player.getName());
                return true;
            }
        }
        plugin.debugLog("[ArmorDamageListener] No armor found on player " + player.getName());
        return false;
    }

    /** Start repeating task to damage player */
    private void startDamageTask(Player player) {
        UUID uuid = player.getUniqueId();
        if (damageTasks.containsKey(uuid)) {
            plugin.debugLog("[ArmorDamageListener] Task already exists for player " + player.getName() + ", cancelling old task");
            cancelDamageTask(player);
        }

        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || player.isDead() || player.getGameMode() == GameMode.CREATIVE || !armorEnabled) {
                    cancel();
                    damageTasks.remove(player.getUniqueId());
                    plugin.debugLog("[ArmorDamageListener] Task auto-cancelled for player " + player.getName() +
                            ", reason: offline/dead/creative/disabled");
                    return;
                }
                if (!hasAnyArmor(player)) {
                    cancel();
                    damageTasks.remove(player.getUniqueId());
                    plugin.debugLog("[ArmorDamageListener] Task auto-cancelled for player " + player.getName() +
                            ", reason: no armor");
                    return;
                }

                double damageInHP = damageAmount * 2; // convert to HP
                player.damage(damageInHP);
                plugin.debugLog("[ArmorDamageListener] Applied armor damage to player " + player.getName() +
                        ", damage: " + damageInHP);
            }
        };
        task.runTaskTimer(plugin, 0L, damageIntervalTicks);
        damageTasks.put(uuid, task);
        plugin.debugLog("[ArmorDamageListener] Started new armor damage task for player " + player.getName());
    }

    /** Cancel a player's damage task if it exists */
    private void cancelDamageTask(Player player) {
        UUID uuid = player.getUniqueId();
        BukkitRunnable task = damageTasks.remove(uuid);
        if (task != null) {
            task.cancel();
            plugin.debugLog("[ArmorDamageListener] Cancelled armor damage task for player " + player.getName());
        } else {
            plugin.debugLog("[ArmorDamageListener] No active damage task found for player " + player.getName());
        }
    }

    /** Cancel all running tasks (plugin disable, etc) */
    public void disableAllTasks() {
        for (BukkitRunnable task : damageTasks.values()) {
            task.cancel();
            plugin.debugLog("[ArmorDamageListener] Cancelled armor damage task during disableAllTasks");
        }
        damageTasks.clear();
        if (pollingTask != null) {
            pollingTask.cancel();
            pollingTask = null;
            plugin.debugLog("[ArmorDamageListener] Cancelled polling task during disableAllTasks");
        }
        plugin.debugLog("[ArmorDamageListener] All armor damage tasks and polling task cancelled");
    }
}