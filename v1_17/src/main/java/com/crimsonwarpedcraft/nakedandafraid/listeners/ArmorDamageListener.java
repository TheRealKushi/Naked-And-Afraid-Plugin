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
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A listener that applies periodic damage to players wearing armor,
 * accounting for specified slots or materials and holding items in the offhand.
 * Has compatibility for Minecraft 1.12–1.21.8.
 */
public class ArmorDamageListener implements Listener {

    private final NakedAndAfraid plugin;
    private final HashMap<UUID, BukkitRunnable> damageTasks = new HashMap<>();
    private double damageAmount;
    private long damageIntervalTicks;
    private boolean armorEnabled;
    private boolean includeOffhand;
    private Set<String> validArmorSlots;
    private Set<Material> validArmorMaterials;
    private BukkitRunnable pollingTask;

    public ArmorDamageListener(NakedAndAfraid plugin) {
        this.plugin = plugin;
        loadConfigValues();
        plugin.debugLog("[ArmorDamageListener] Initialized ArmorDamageListener for Bukkit version " +
                Bukkit.getBukkitVersion() + ", PaperArmorChangeSupported: " + isPaperArmorChangeSupported());
        if (shouldPoll()) {
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
     * Determines if polling should be enabled (for pre-Paper or when offhand is included).
     */
    private boolean shouldPoll() {
        return !isPaperArmorChangeSupported() || includeOffhand;
    }

    /**
     * Checks if the server is pre-1.16 (Minecraft 1.12–1.15.2).
     */
    private boolean isPre116() {
        return !Bukkit.getBukkitVersion().matches(".*1\\.(1[6-9]|2[0-1]).*");
    }

    /** Load configuration values from plugin config */
    private void loadConfigValues() {
        this.armorEnabled = plugin.getConfig().getBoolean("armor-damage.enabled", true);
        this.damageAmount = plugin.getConfig().getDouble("armor-damage.damage-amount", 1.0);
        this.damageIntervalTicks = plugin.getConfig().getLong("armor-damage.damage-interval-ticks", 20L);
        this.includeOffhand = plugin.getConfig().getBoolean("armor-damage.include-offhand", false);

        List<String> configSlots = plugin.getConfig().getStringList("armor-damage.armor-slot");
        Set<String> validSlots = new HashSet<>(Arrays.asList("HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS"));
        this.validArmorSlots = configSlots.isEmpty() ? validSlots : new HashSet<>();
        for (String slot : configSlots) {
            String upperSlot = slot.toUpperCase();
            if (validSlots.contains(upperSlot)) {
                this.validArmorSlots.add(upperSlot);
            } else {
                plugin.debugLog("[ArmorDamageListener] Invalid armor slot in config: " + slot);
            }
        }
        if (this.validArmorSlots.isEmpty()) {
            this.validArmorSlots = validSlots;
            plugin.debugLog("[ArmorDamageListener] No valid armor slots in config, using all: " + validSlots);
        }

        List<String> configMaterials = plugin.getConfig().getStringList("armor-damage.armor-material");
        this.validArmorMaterials = new HashSet<>();
        for (String material : configMaterials) {
            try {
                Material mat = Material.valueOf(material.toUpperCase());
                if (isArmorMaterial(mat)) {
                    this.validArmorMaterials.add(mat);
                } else {
                    plugin.debugLog("[ArmorDamageListener] Material is not armor: " + material);
                }
            } catch (IllegalArgumentException e) {
                plugin.debugLog("[ArmorDamageListener] Invalid armor material in config: " + material);
            }
        }
        if (this.validArmorMaterials.isEmpty()) {
            this.validArmorMaterials = new HashSet<>(Arrays.asList(
                    Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS,
                    Material.CHAINMAIL_HELMET, Material.CHAINMAIL_CHESTPLATE, Material.CHAINMAIL_LEGGINGS, Material.CHAINMAIL_BOOTS,
                    Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS,
                    Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS,
                    Material.GOLDEN_HELMET, Material.GOLDEN_CHESTPLATE, Material.GOLDEN_LEGGINGS, Material.GOLDEN_BOOTS,
                    Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE, Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS
            ));
            plugin.debugLog("[ArmorDamageListener] No valid armor materials in config, using defaults: " +
                    this.validArmorMaterials.stream().map(Material::name).toList());
        }

        plugin.debugLog("[ArmorDamageListener] Loaded config: armorEnabled=" + armorEnabled +
                ", damageAmount=" + damageAmount + ", damageIntervalTicks=" + damageIntervalTicks +
                ", includeOffhand=" + includeOffhand + ", validArmorSlots=" + validArmorSlots +
                ", validArmorMaterials=" + validArmorMaterials.stream().map(Material::name).toList());
    }

    /** Check if a material is an armor material */
    private boolean isArmorMaterial(Material material) {
        String name = material.name().toUpperCase();
        return name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS");
    }

    /** Refresh tasks for all online players according to current config */
    public void refreshArmorTasks() {
        loadConfigValues();
        plugin.debugLog("[ArmorDamageListener] Refreshing armor tasks, armorEnabled: " + armorEnabled);

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            boolean hasArmorOrOffhand = hasAnyArmorOrOffhand(player);
            plugin.debugLog("[ArmorDamageListener] Checking player " + player.getName() + ", hasArmorOrOffhand: " + hasArmorOrOffhand);

            if (!armorEnabled || !hasArmorOrOffhand) {
                if (damageTasks.containsKey(uuid)) {
                    cancelDamageTask(player);
                }
            } else if (hasArmorOrOffhand && armorEnabled && !damageTasks.containsKey(uuid)) {
                plugin.debugLog("[ArmorDamageListener] Starting armor/offhand damage task for player " + player.getName());
                startDamageTask(player);
            }
        }

        // Manage polling task based on current config
        if (shouldPoll()) {
            if (pollingTask == null) {
                startPollingTask();
            }
        } else {
            if (pollingTask != null) {
                pollingTask.cancel();
                pollingTask = null;
                plugin.debugLog("[ArmorDamageListener] Stopped polling task after refresh");
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
                    boolean hasArmorOrOffhand = hasAnyArmorOrOffhand(player);
                    plugin.debugLog("[ArmorDamageListener] Polling player " + player.getName() + ", hasArmorOrOffhand: " + hasArmorOrOffhand);
                    if (!armorEnabled || !hasArmorOrOffhand) {
                        if (damageTasks.containsKey(uuid)) {
                            cancelDamageTask(player);
                        }
                    } else if (hasArmorOrOffhand && armorEnabled && !damageTasks.containsKey(uuid)) {
                        plugin.debugLog("[ArmorDamageListener] Starting armor/offhand damage task for player " + player.getName());
                        startDamageTask(player);
                    }
                }
            }
        };
        pollingTask.runTaskTimer(plugin, 0L, 20L); // Check every second (20 ticks)
        plugin.debugLog("[ArmorDamageListener] Started polling task for armor/offhand checks");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.debugLog("[ArmorDamageListener] Player " + player.getName() + " joined, checking armor/offhand");
        if (armorEnabled && hasAnyArmorOrOffhand(player)) {
            plugin.debugLog("[ArmorDamageListener] Starting armor/offhand damage task for joining player " + player.getName());
            startDamageTask(player);
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
                ", Slot: " + event.getSlotType() + ", New item: " + event.getNewItem().getType() +
                ", Old item: " + event.getOldItem().getType());

        UUID uuid = player.getUniqueId();
        if (hasAnyArmorOrOffhand(player)) {
            if (!damageTasks.containsKey(uuid)) {
                plugin.debugLog("[ArmorDamageListener] Starting armor/offhand damage task for player " + player.getName());
                startDamageTask(player);
            }
        } else {
            if (damageTasks.containsKey(uuid)) {
                plugin.debugLog("[ArmorDamageListener] Cancelling armor/offhand damage task for player " + player.getName());
                cancelDamageTask(player);
            }
        }
    }

    /** Returns true if player has armor in specified slots/materials or an offhand item (if enabled) */
    private boolean hasAnyArmorOrOffhand(Player player) {
        ItemStack[] armorContents = player.getInventory().getArmorContents();
        if (armorContents == null) {
            plugin.debugLog("[ArmorDamageListener] Armor contents null for player " + player.getName());
            return false;
        }

        // Check armor slots
        int slotIndex = 0;
        String[] slotNames = {"BOOTS", "LEGGINGS", "CHESTPLATE", "HELMET"}; // Order matches armorContents
        for (ItemStack armor : armorContents) {
            String slotName = slotNames[slotIndex];
            if (armor != null && armor.getType() != Material.AIR && validArmorSlots.contains(slotName) &&
                    validArmorMaterials.contains(armor.getType())) {
                plugin.debugLog("[ArmorDamageListener] Found armor: " + armor.getType() + " in slot " + slotName +
                        " for player " + player.getName());
                return true;
            }
            slotIndex++;
        }

        // Check offhand if enabled
        if (includeOffhand) {
            ItemStack offhand = isPre116() ? player.getInventory().getItemInHand() : player.getInventory().getItemInOffHand();
            if (offhand != null && offhand.getType() != Material.AIR) {
                plugin.debugLog("[ArmorDamageListener] Found offhand item: " + offhand.getType() + " for player " + player.getName());
                return true;
            }
        }

        plugin.debugLog("[ArmorDamageListener] No armor or offhand item found for player " + player.getName());
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
                if (!hasAnyArmorOrOffhand(player)) {
                    cancel();
                    damageTasks.remove(player.getUniqueId());
                    plugin.debugLog("[ArmorDamageListener] Task auto-cancelled for player " + player.getName() +
                            ", reason: no armor or offhand item");
                    return;
                }

                double damageInHP = damageAmount * 2; // convert to HP
                player.damage(damageInHP);
                plugin.debugLog("[ArmorDamageListener] Applied armor/offhand damage to player " + player.getName() +
                        ", damage: " + damageInHP);
            }
        };
        task.runTaskTimer(plugin, 0L, damageIntervalTicks);
        damageTasks.put(uuid, task);
        plugin.debugLog("[ArmorDamageListener] Started new armor/offhand damage task for player " + player.getName());
    }

    /** Cancel a player's damage task if it exists */
    private void cancelDamageTask(Player player) {
        UUID uuid = player.getUniqueId();
        BukkitRunnable task = damageTasks.remove(uuid);
        if (task != null) {
            task.cancel();
            plugin.debugLog("[ArmorDamageListener] Cancelled armor/offhand damage task for player " + player.getName());
        } else {
            plugin.debugLog("[ArmorDamageListener] No active damage task found for player " + player.getName());
        }
    }

    /** Cancel all running tasks (plugin disable, etc) */
    public void disableAllTasks() {
        for (BukkitRunnable task : damageTasks.values()) {
            task.cancel();
            plugin.debugLog("[ArmorDamageListener] Cancelled armor/offhand damage task during disableAllTasks");
        }
        damageTasks.clear();
        if (pollingTask != null) {
            pollingTask.cancel();
            pollingTask = null;
            plugin.debugLog("[ArmorDamageListener] Cancelled polling task during disableAllTasks");
        }
        plugin.debugLog("[ArmorDamageListener] All armor/offhand damage tasks and polling task cancelled");
    }
}