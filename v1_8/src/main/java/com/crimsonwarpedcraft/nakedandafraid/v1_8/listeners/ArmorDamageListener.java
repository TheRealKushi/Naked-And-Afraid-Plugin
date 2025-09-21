package com.crimsonwarpedcraft.nakedandafraid.v1_8.listeners;

import com.crimsonwarpedcraft.nakedandafraid.v1_8.NakedAndAfraid;
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
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A listener that applies periodic damage to players wearing armor,
 * accounting for specified slots or materials and holding items in the offhand.
 * Has compatibility for Minecraft 1.12–1.16.5 (Spigot).
 */
public class ArmorDamageListener implements Listener {

    private final NakedAndAfraid nakedAndAfraid;
    private final Plugin plugin;
    private final HashMap<UUID, BukkitRunnable> damageTasks = new HashMap<>();
    private double damageAmount;
    private long damageIntervalTicks;
    private boolean armorEnabled;
    private boolean includeOffhand;
    private Set<String> validArmorSlots;
    private Set<Material> validArmorMaterials;
    private BukkitRunnable pollingTask;

    public ArmorDamageListener(NakedAndAfraid nakedAndAfraid) {
        this.nakedAndAfraid = nakedAndAfraid;
        this.plugin = NakedAndAfraid.getPlugin();
        loadConfigValues();
        nakedAndAfraid.debugLog("[ArmorDamageListener] Initialized ArmorDamageListener for Bukkit version " +
                Bukkit.getBukkitVersion() + ", PaperArmorChangeSupported: false (legacy Spigot)");
        startPollingTask();
    }

    /** Checks if the server is pre-1.16 (Minecraft 1.12–1.15.2). */
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
                nakedAndAfraid.debugLog("[ArmorDamageListener] Invalid armor slot in config: " + slot);
            }
        }
        if (this.validArmorSlots.isEmpty()) {
            this.validArmorSlots = validSlots;
            nakedAndAfraid.debugLog("[ArmorDamageListener] No valid armor slots in config, using all: " + validSlots);
        }

        List<String> configMaterials = plugin.getConfig().getStringList("armor-damage.armor-material");
        this.validArmorMaterials = new HashSet<>();
        for (String material : configMaterials) {
            try {
                Material mat = Material.valueOf(material.toUpperCase());
                if (isArmorMaterial(mat)) {
                    this.validArmorMaterials.add(mat);
                } else {
                    nakedAndAfraid.debugLog("[ArmorDamageListener] Material is not armor: " + material);
                }
            } catch (IllegalArgumentException e) {
                nakedAndAfraid.debugLog("[ArmorDamageListener] Invalid armor material in config: " + material);
            }
        }
        if (this.validArmorMaterials.isEmpty()) {
            this.validArmorMaterials = new HashSet<>(Arrays.asList(
                    Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS,
                    Material.CHAINMAIL_HELMET, Material.CHAINMAIL_CHESTPLATE, Material.CHAINMAIL_LEGGINGS, Material.CHAINMAIL_BOOTS,
                    Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS,
                    Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS,
                    Material.GOLD_HELMET, Material.GOLD_CHESTPLATE, Material.GOLD_LEGGINGS, Material.GOLD_BOOTS // Use legacy names
            ));
            nakedAndAfraid.debugLog("[ArmorDamageListener] No valid armor materials in config, using defaults: " +
                    this.validArmorMaterials.stream().map(Material::name).collect(Collectors.toList()));
        }

        nakedAndAfraid.debugLog("[ArmorDamageListener] Loaded config: armorEnabled=" + armorEnabled +
                ", damageAmount=" + damageAmount + ", damageIntervalTicks=" + damageIntervalTicks +
                ", includeOffhand=" + includeOffhand + ", validArmorSlots=" + validArmorSlots +
                ", validArmorMaterials=" + validArmorMaterials.stream().map(Material::name).collect(Collectors.toList()));
    }

    /** Check if a material is an armor material */
    private boolean isArmorMaterial(Material material) {
        String name = material.name().toUpperCase();
        return name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS");
    }

    /** Refresh tasks for all online players according to current config */
    public void refreshArmorTasks() {
        loadConfigValues();
        nakedAndAfraid.debugLog("[ArmorDamageListener] Refreshing armor tasks, armorEnabled: " + armorEnabled);

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            boolean hasArmorOrOffhand = hasAnyArmorOrOffhand(player);
            nakedAndAfraid.debugLog("[ArmorDamageListener] Checking player " + player.getName() + ", hasArmorOrOffhand: " + hasArmorOrOffhand);

            if (!armorEnabled || !hasArmorOrOffhand) {
                if (damageTasks.containsKey(uuid)) cancelDamageTask(player);
            } else if (!damageTasks.containsKey(uuid)) {
                startDamageTask(player);
            }
        }
    }

    /** Start a polling task to check armor for all online players (pre-1.16.5 Spigot) */
    private void startPollingTask() {
        if (pollingTask != null) pollingTask.cancel();
        pollingTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    boolean hasArmorOrOffhand = hasAnyArmorOrOffhand(player);
                    UUID uuid = player.getUniqueId();
                    if (!armorEnabled || !hasArmorOrOffhand) {
                        if (damageTasks.containsKey(uuid)) cancelDamageTask(player);
                    } else if (!damageTasks.containsKey(uuid)) {
                        startDamageTask(player);
                    }
                }
            }
        };
        pollingTask.runTaskTimer(plugin, 0L, 20L);
        nakedAndAfraid.debugLog("[ArmorDamageListener] Started polling task for armor/offhand checks");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (armorEnabled && hasAnyArmorOrOffhand(player)) startDamageTask(player);
    }

    /** Returns true if player has armor in specified slots/materials or an offhand item (if enabled) */
    private boolean hasAnyArmorOrOffhand(Player player) {
        ItemStack[] armorContents = player.getInventory().getArmorContents();
        if (armorContents != null) {
            String[] slotNames = {"BOOTS", "LEGGINGS", "CHESTPLATE", "HELMET"};
            for (int i = 0; i < armorContents.length; i++) {
                ItemStack armor = armorContents[i];
                if (armor != null && armor.getType() != Material.AIR &&
                        validArmorSlots.contains(slotNames[i]) &&
                        validArmorMaterials.contains(armor.getType())) {
                    return true;
                }
            }
        }

        if (includeOffhand) {
            ItemStack offhand = isPre116() ? player.getInventory().getItemInHand() : player.getInventory().getItemInOffHand();
            if (offhand != null && offhand.getType() != Material.AIR) return true;
        }
        return false;
    }

    /** Start repeating task to damage player */
    private void startDamageTask(Player player) {
        UUID uuid = player.getUniqueId();
        if (damageTasks.containsKey(uuid)) cancelDamageTask(player);

        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || player.isDead() || player.getGameMode() == GameMode.CREATIVE || !armorEnabled || !hasAnyArmorOrOffhand(player)) {
                    cancel();
                    damageTasks.remove(player.getUniqueId());
                    return;
                }
                player.damage(damageAmount * 2);
            }
        };
        task.runTaskTimer(plugin, 0L, damageIntervalTicks);
        damageTasks.put(uuid, task);
    }

    /** Cancel a player's damage task if it exists */
    private void cancelDamageTask(Player player) {
        UUID uuid = player.getUniqueId();
        BukkitRunnable task = damageTasks.remove(uuid);
        if (task != null) task.cancel();
    }

    /** Cancel all running tasks (plugin disable, etc) */
    public void disableAllTasks() {
        for (BukkitRunnable task : damageTasks.values()) task.cancel();
        damageTasks.clear();
        if (pollingTask != null) pollingTask.cancel();
    }
}