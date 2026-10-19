// V1_21/ArmorDamageListener
package com.therealkushi.nakedandafraid.v1_21.listeners;

import com.therealkushi.nakedandafraid.v1_21.util.MaterialCompat;
import com.therealkushi.nakedandafraid.v1_21.util.SchedulerCompat;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Applies periodic damage to players wearing armor, accounting for specified
 * slots/materials and holding items in the offhand.
 * <p>
 * The Paper-only {@code PlayerArmorChangeEvent} handler now lives in
 * {@link PaperArmorChangeListener}, only created/registered once
 * {@link #isPaperArmorChangeSupported()} confirms it exists - see that
 * method's javadoc for a precedence bug that used to make this check lie
 * on CraftBukkit/Spigot for MC 1.17+.
 */
public class ArmorDamageListener implements Listener {

    private final Plugin plugin;
    private final HashMap<UUID, SchedulerCompat.TaskHandle> damageTasks = new HashMap<>();
    private double damageAmount;
    private long damageIntervalTicks;
    private boolean armorEnabled;
    private boolean includeOffhand;
    private Set<String> validArmorSlots;
    private Set<Material> validArmorMaterials;
    private SchedulerCompat.TaskHandle pollingTask;
    private PaperArmorChangeListener paperCompanion;

    public ArmorDamageListener(Plugin plugin) {
        this.plugin = plugin;
        loadConfigValues();
        debugLog("[ArmorDamageListener] Initialized for Bukkit version " +
                Bukkit.getBukkitVersion() + ", PaperArmorChangeSupported: " + isPaperArmorChangeSupported());

        if (isPaperArmorChangeSupported()) {
            paperCompanion = new PaperArmorChangeListener(this);
            Bukkit.getPluginManager().registerEvents(paperCompanion, plugin);
            debugLog("[ArmorDamageListener] Registered PaperArmorChangeListener companion");
        }
        if (shouldPoll()) {
            startPollingTask();
        }
    }

    /** Unregisters the Paper companion, if one was created. Call before discarding this listener. */
    public void shutdownPaperCompanion() {
        if (paperCompanion != null) {
            HandlerList.unregisterAll(paperCompanion);
            debugLog("[ArmorDamageListener] Unregistered PaperArmorChangeListener companion");
            paperCompanion = null;
        }
    }

    private void debugLog(String message) {
        if (plugin.getConfig().getBoolean("debug-mode", false)) {
            plugin.getLogger().info(message);
        }
    }

    private static Boolean paperArmorChangeSupported;

    /**
     * Whether Paper's {@code PlayerArmorChangeEvent} exists on this server.
     * <p>
     * Previously gated behind a Minecraft-version heuristic
     * ({@code major > 16 || (major == 16 && minor >= 5) && Class.forName(...)}).
     * Because {@code &&} binds tighter than {@code ||}, that expression is
     * {@code major > 16 || ((major == 16 && minor >= 5) && classExists)} -
     * for any MC version above 1.16 it short-circuits to {@code true}
     * WITHOUT ever calling {@code Class.forName}, so it wrongly reported
     * support on CraftBukkit/Spigot for MC 1.17+ (this event is a Paper API
     * addition, never implied by MC version). That silently disabled BOTH
     * the Paper event AND the polling fallback, so armor damage did nothing
     * at all. Replaced with a plain reflective existence check.
     */
    private boolean isPaperArmorChangeSupported() {
        if (paperArmorChangeSupported == null) {
            try {
                Class.forName("com.destroystokyo.paper.event.player.PlayerArmorChangeEvent");
                paperArmorChangeSupported = true;
            } catch (Throwable t) {
                debugLog("[ArmorDamageListener] PlayerArmorChangeEvent not present: " + t.getMessage());
                paperArmorChangeSupported = false;
            }
        }
        return paperArmorChangeSupported;
    }

    /** Determines if polling should be enabled (for pre-Paper or when offhand is included). */
    private boolean shouldPoll() {
        return !isPaperArmorChangeSupported() || includeOffhand;
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
            handleArmorOrOffhandChange(player);
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

    /**
     * Runs on the entity's own region thread (Folia-safe via SchedulerCompat) so this task
     * poll iterates the global player list but only touches each player's state from its
     * own region, instead of directly mutating them from a global-scheduler thread.
     */
    private void startPollingTask() {
        if (pollingTask != null) {
            pollingTask.cancel();
            debugLog("[ArmorDamageListener] Cancelled existing polling task");
        }
        pollingTask = SchedulerCompat.runGlobalRepeating(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                SchedulerCompat.runForEntity(plugin, player,
                        () -> handleArmorOrOffhandChange(player), 0L);
            }
        }, 0L, 20L);
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

    /**
     * Re-checks a player's armor/offhand state and starts or stops their damage task
     * accordingly. Called from the Paper armor-change event (via {@link PaperArmorChangeListener}),
     * from the polling fallback, and from {@link #refreshArmorTasks()}.
     */
    public void handleArmorOrOffhandChange(Player player) {
        if (!armorEnabled) {
            debugLog("[ArmorDamageListener] Armor damage disabled, ignoring change for " + player.getName());
            return;
        }
        UUID uuid = player.getUniqueId();
        boolean hasArmorOrOffhand = hasAnyArmorOrOffhand(player);
        debugLog("[ArmorDamageListener] Checking player " + player.getName() + ", hasArmorOrOffhand: " + hasArmorOrOffhand);

        if (!hasArmorOrOffhand) {
            if (damageTasks.containsKey(uuid)) {
                cancelDamageTask(player);
            }
        } else if (!damageTasks.containsKey(uuid)) {
            debugLog("[ArmorDamageListener] Starting armor/offhand damage task for player " + player.getName());
            startDamageTask(player);
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

    /** Start repeating task to damage player - bound to the player's own region on Folia. */
    private void startDamageTask(Player player) {
        UUID uuid = player.getUniqueId();
        if (damageTasks.containsKey(uuid)) {
            debugLog("[ArmorDamageListener] Task already exists for player " + player.getName() + ", cancelling old task");
            cancelDamageTask(player);
        }

        SchedulerCompat.TaskHandle handle = SchedulerCompat.runForEntityRepeating(plugin, player, () -> {
            if (!player.isOnline() || player.isDead() || player.getGameMode() == GameMode.CREATIVE || !armorEnabled) {
                SchedulerCompat.TaskHandle self = damageTasks.remove(player.getUniqueId());
                if (self != null) self.cancel();
                debugLog("[ArmorDamageListener] Task auto-cancelled for player " + player.getName() +
                        ", reason: offline/dead/creative/disabled");
                return;
            }
            if (!hasAnyArmorOrOffhand(player)) {
                SchedulerCompat.TaskHandle self = damageTasks.remove(player.getUniqueId());
                if (self != null) self.cancel();
                debugLog("[ArmorDamageListener] Task auto-cancelled for player " + player.getName() +
                        ", reason: no armor or offhand item");
                return;
            }

            double damageInHP = damageAmount * 2;
            player.damage(damageInHP);
            debugLog("[ArmorDamageListener] Applied armor/offhand damage to player " + player.getName() +
                    ", damage: " + damageInHP);
        }, 0L, damageIntervalTicks);

        damageTasks.put(uuid, handle);
        debugLog("[ArmorDamageListener] Started new armor/offhand damage task for player " + player.getName());
    }

    /** Cancel a player's damage task if it exists */
    private void cancelDamageTask(Player player) {
        UUID uuid = player.getUniqueId();
        SchedulerCompat.TaskHandle task = damageTasks.remove(uuid);
        if (task != null) {
            task.cancel();
            debugLog("[ArmorDamageListener] Cancelled armor/offhand damage task for player " + player.getName());
        } else {
            debugLog("[ArmorDamageListener] No active damage task found for player " + player.getName());
        }
    }

    /** Cancel all running tasks (plugin disable, etc) */
    public void disableAllTasks() {
        for (SchedulerCompat.TaskHandle task : damageTasks.values()) {
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