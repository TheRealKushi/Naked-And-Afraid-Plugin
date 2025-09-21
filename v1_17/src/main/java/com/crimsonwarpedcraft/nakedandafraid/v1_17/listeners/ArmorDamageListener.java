package com.crimsonwarpedcraft.nakedandafraid.v1_17.listeners;

import com.crimsonwarpedcraft.nakedandafraid.v1_17.util.MaterialCompat;
import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A listener that applies periodic damage to players wearing armor,
 * accounting for specified slots or materials and holding items in the offhand.
 * Has compatibility for Minecraft 1.17–1.18.2.
 */
public class ArmorDamageListener implements Listener {

    private final Plugin plugin;
    private final HashMap<UUID, BukkitRunnable> damageTasks = new HashMap<>();
    private double damageAmount;
    private long damageIntervalTicks;
    private boolean armorEnabled;
    private boolean includeOffhand;
    private Set<String> validArmorSlots;
    private Set<Material> validArmorMaterials;
    private BukkitRunnable pollingTask;

    public ArmorDamageListener(Plugin plugin) {
        this.plugin = plugin;
        loadConfigValues();
        debugLog("[ArmorDamageListener] Initialized ArmorDamageListener for Bukkit version " +
                Bukkit.getBukkitVersion() + ", PaperArmorChangeSupported: " + isPaperArmorChangeSupported());
        if (shouldPoll()) {
            startPollingTask();
        }
    }

    private void debugLog(String message) {
        if (plugin.getConfig().getBoolean("debug-mode", false)) {
            plugin.getLogger().info(message);
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
            debugLog("[ArmorDamageListener] Failed to detect PaperArmorChangeEvent support: " + e.getMessage());
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
            if (slot == null) {
                debugLog("[ArmorDamageListener] Null armor slot in config");
                continue;
            }
            String upperSlot = slot.toUpperCase();
            if (validSlots.contains(upperSlot)) {
                this.validArmorSlots.add(upperSlot);
            } else {
                debugLog("[ArmorDamageListener] Invalid armor slot in config: " + slot);
            }
        }
        if (this.validArmorSlots.isEmpty()) {
            this.validArmorSlots = validSlots;
            debugLog("[ArmorDamageListener] No valid armor slots in config, using all: " + validSlots);
        }

        List<String> configMaterials = plugin.getConfig().getStringList("armor-damage.armor-material");
        this.validArmorMaterials = new HashSet<>();
        for (String material : configMaterials) {
            if (material == null) {
                debugLog("[ArmorDamageListener] Null material in armor-damage.armor-material config");
                continue;
            }
            Material mat = MaterialCompat.getMaterial(material.toUpperCase());
            if (mat != null && isArmorMaterial(mat)) {
                this.validArmorMaterials.add(mat);
            } else {
                debugLog("[ArmorDamageListener] Invalid or non-armor material in config: " + material);
            }
        }
        if (this.validArmorMaterials.isEmpty()) {
            List<Material> defaultMaterials = Arrays.asList(
                    MaterialCompat.getMaterial("LEATHER_HELMET"),
                    MaterialCompat.getMaterial("LEATHER_CHESTPLATE"),
                    MaterialCompat.getMaterial("LEATHER_LEGGINGS"),
                    MaterialCompat.getMaterial("LEATHER_BOOTS"),
                    MaterialCompat.getMaterial("CHAINMAIL_HELMET"),
                    MaterialCompat.getMaterial("CHAINMAIL_CHESTPLATE"),
                    MaterialCompat.getMaterial("CHAINMAIL_LEGGINGS"),
                    MaterialCompat.getMaterial("CHAINMAIL_BOOTS"),
                    MaterialCompat.getMaterial("IRON_HELMET"),
                    MaterialCompat.getMaterial("IRON_CHESTPLATE"),
                    MaterialCompat.getMaterial("IRON_LEGGINGS"),
                    MaterialCompat.getMaterial("IRON_BOOTS"),
                    MaterialCompat.getMaterial("DIAMOND_HELMET"),
                    MaterialCompat.getMaterial("DIAMOND_CHESTPLATE"),
                    MaterialCompat.getMaterial("DIAMOND_LEGGINGS"),
                    MaterialCompat.getMaterial("DIAMOND_BOOTS"),
                    MaterialCompat.getMaterial("GOLD_HELMET", "GOLDEN_HELMET"),
                    MaterialCompat.getMaterial("GOLD_CHESTPLATE", "GOLDEN_CHESTPLATE"),
                    MaterialCompat.getMaterial("GOLD_LEGGINGS", "GOLDEN_LEGGINGS"),
                    MaterialCompat.getMaterial("GOLD_BOOTS", "GOLDEN_BOOTS"),
                    isPre116() ? null : MaterialCompat.getMaterial("NETHERITE_HELMET"),
                    isPre116() ? null : MaterialCompat.getMaterial("NETHERITE_CHESTPLATE"),
                    isPre116() ? null : MaterialCompat.getMaterial("NETHERITE_LEGGINGS"),
                    isPre116() ? null : MaterialCompat.getMaterial("NETHERITE_BOOTS")
            );
            this.validArmorMaterials = defaultMaterials.stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(HashSet::new));
            debugLog("[ArmorDamageListener] No valid armor materials in config, using defaults: " +
                    validArmorMaterials.stream()
                            .filter(Objects::nonNull)
                            .map(mat -> mat.name().toLowerCase())
                            .collect(Collectors.toList()));
        }

        debugLog("[ArmorDamageListener] Loaded config: armorEnabled=" + armorEnabled +
                ", damageAmount=" + damageAmount + ", damageIntervalTicks=" + damageIntervalTicks +
                ", includeOffhand=" + includeOffhand + ", validArmorSlots=" + validArmorSlots +
                ", validArmorMaterials=" + validArmorMaterials.stream()
                .filter(Objects::nonNull)
                .map(mat -> mat.name().toLowerCase())
                .collect(Collectors.toList()));
    }

    /** Check if a material is an armor material */
    private boolean isArmorMaterial(Material material) {
        if (material == null) return false;
        String name = material.name().toUpperCase();
        return name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS");
    }

    /** Refresh tasks for all online players according to current config */
    public void refreshArmorTasks() {
        loadConfigValues();
        debugLog("[ArmorDamageListener] Refreshing armor tasks, armorEnabled: " + armorEnabled);

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            boolean hasArmorOrOffhand = hasAnyArmorOrOffhand(player);
            debugLog("[ArmorDamageListener] Checking player " + player.getName() + ", hasArmorOrOffhand: " + hasArmorOrOffhand);

            if (!armorEnabled || !hasArmorOrOffhand) {
                if (damageTasks.containsKey(uuid)) {
                    cancelDamageTask(player);
                }
            } else if (hasArmorOrOffhand && armorEnabled && !damageTasks.containsKey(uuid)) {
                debugLog("[ArmorDamageListener] Starting armor/offhand damage task for player " + player.getName());
                startDamageTask(player);
            }
        }

        if (shouldPoll()) {
            if (pollingTask == null) {
                startPollingTask();
            }
        } else {
            if (pollingTask != null) {
                pollingTask.cancel();
                pollingTask = null;
                debugLog("[ArmorDamageListener] Stopped polling task after refresh");
            }
        }
    }

    /** Start a polling task to check armor for all online players (pre-Paper or when offhand is included) */
    private void startPollingTask() {
        if (pollingTask != null) {
            pollingTask.cancel();
            debugLog("[ArmorDamageListener] Cancelled existing polling task");
        }
        pollingTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    UUID uuid = player.getUniqueId();
                    boolean hasArmorOrOffhand = hasAnyArmorOrOffhand(player);
                    debugLog("[ArmorDamageListener] Polling player " + player.getName() + ", hasArmorOrOffhand: " + hasArmorOrOffhand);
                    if (!armorEnabled || !hasArmorOrOffhand) {
                        if (damageTasks.containsKey(uuid)) {
                            cancelDamageTask(player);
                        }
                    } else if (hasArmorOrOffhand && armorEnabled && !damageTasks.containsKey(uuid)) {
                        debugLog("[ArmorDamageListener] Starting armor/offhand damage task for player " + player.getName());
                        startDamageTask(player);
                    }
                }
            }
        };
        pollingTask.runTaskTimer(plugin, 0L, 20L);
        debugLog("[ArmorDamageListener] Started polling task for armor/offhand checks");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        debugLog("[ArmorDamageListener] Player " + player.getName() + " joined, checking armor/offhand");
        if (armorEnabled && hasAnyArmorOrOffhand(player)) {
            debugLog("[ArmorDamageListener] Starting armor/offhand damage task for joining player " + player.getName());
            startDamageTask(player);
        }
    }

    @EventHandler
    public void onPlayerArmorChange(PlayerArmorChangeEvent event) {
        if (!armorEnabled) {
            debugLog("[ArmorDamageListener] Armor damage disabled, ignoring armor change for " +
                    event.getPlayer().getName());
            return;
        }

        Player player = event.getPlayer();
        debugLog("[ArmorDamageListener] Armor change event fired for player " + player.getName() +
                ", Slot: " + event.getSlotType() + ", New item: " + event.getNewItem().getType() +
                ", Old item: " + event.getOldItem().getType());

        UUID uuid = player.getUniqueId();
        if (hasAnyArmorOrOffhand(player)) {
            if (!damageTasks.containsKey(uuid)) {
                debugLog("[ArmorDamageListener] Starting armor/offhand damage task for player " + player.getName());
                startDamageTask(player);
            }
        } else {
            if (damageTasks.containsKey(uuid)) {
                debugLog("[ArmorDamageListener] Cancelling armor/offhand damage task for player " + player.getName());
                cancelDamageTask(player);
            }
        }
    }

    /** Returns true if player has armor in specified slots/materials or an offhand item (if enabled) */
    private boolean hasAnyArmorOrOffhand(Player player) {
        ItemStack[] armorContents = player.getInventory().getArmorContents();
        if (armorContents == null) {
            debugLog("[ArmorDamageListener] Armor contents null for player " + player.getName());
            return false;
        }

        int slotIndex = 0;
        String[] slotNames = {"BOOTS", "LEGGINGS", "CHESTPLATE", "HELMET"};
        for (ItemStack armor : armorContents) {
            String slotName = slotNames[slotIndex];
            if (armor != null && armor.getType() != Material.AIR && validArmorSlots.contains(slotName) &&
                    validArmorMaterials.contains(armor.getType())) {
                debugLog("[ArmorDamageListener] Found armor: " + armor.getType() + " in slot " + slotName +
                        " for player " + player.getName());
                return true;
            }
            slotIndex++;
        }

        if (includeOffhand) {
            ItemStack offhand = isPre116() ? player.getInventory().getItemInHand() : player.getInventory().getItemInOffHand();
            if (offhand != null && offhand.getType() != Material.AIR) {
                debugLog("[ArmorDamageListener] Found offhand item: " + offhand.getType() + " for player " + player.getName());
                return true;
            }
        }

        debugLog("[ArmorDamageListener] No armor or offhand item found for player " + player.getName());
        return false;
    }

    /** Start repeating task to damage player */
    private void startDamageTask(Player player) {
        UUID uuid = player.getUniqueId();
        if (damageTasks.containsKey(uuid)) {
            debugLog("[ArmorDamageListener] Task already exists for player " + player.getName() + ", cancelling old task");
            cancelDamageTask(player);
        }

        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || player.isDead() || player.getGameMode() == GameMode.CREATIVE || !armorEnabled) {
                    cancel();
                    damageTasks.remove(player.getUniqueId());
                    debugLog("[ArmorDamageListener] Task auto-cancelled for player " + player.getName() +
                            ", reason: offline/dead/creative/disabled");
                    return;
                }
                if (!hasAnyArmorOrOffhand(player)) {
                    cancel();
                    damageTasks.remove(player.getUniqueId());
                    debugLog("[ArmorDamageListener] Task auto-cancelled for player " + player.getName() +
                            ", reason: no armor or offhand item");
                    return;
                }

                double damageInHP = damageAmount * 2;
                player.damage(damageInHP);
                debugLog("[ArmorDamageListener] Applied armor/offhand damage to player " + player.getName() +
                        ", damage: " + damageInHP);
            }
        };
        task.runTaskTimer(plugin, 0L, damageIntervalTicks);
        damageTasks.put(uuid, task);
        debugLog("[ArmorDamageListener] Started new armor/offhand damage task for player " + player.getName());
    }

    /** Cancel a player's damage task if it exists */
    private void cancelDamageTask(Player player) {
        UUID uuid = player.getUniqueId();
        BukkitRunnable task = damageTasks.remove(uuid);
        if (task != null) {
            task.cancel();
            debugLog("[ArmorDamageListener] Cancelled armor/offhand damage task for player " + player.getName());
        } else {
            debugLog("[ArmorDamageListener] No active damage task found for player " + player.getName());
        }
    }

    /** Cancel all running tasks (plugin disable, etc) */
    public void disableAllTasks() {
        for (BukkitRunnable task : damageTasks.values()) {
            task.cancel();
            debugLog("[ArmorDamageListener] Cancelled armor/offhand damage task during disableAllTasks");
        }
        damageTasks.clear();
        if (pollingTask != null) {
            pollingTask.cancel();
            pollingTask = null;
            debugLog("[ArmorDamageListener] Cancelled polling task during disableAllTasks");
        }
        debugLog("[ArmorDamageListener] All armor/offhand damage tasks and polling task cancelled");
    }
}