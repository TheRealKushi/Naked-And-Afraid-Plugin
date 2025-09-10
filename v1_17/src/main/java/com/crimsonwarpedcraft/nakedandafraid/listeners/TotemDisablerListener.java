package com.crimsonwarpedcraft.nakedandafraid.listeners;

import com.crimsonwarpedcraft.nakedandafraid.NakedAndAfraid;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.EventExecutor;

import java.lang.reflect.Method;

/**
 * Listener to disable the death-saving mechanic of Totems of Undying.
 * <p>
 * This listener prevents Totems of Undying from activating when a player would take fatal damage.
 * For Paper-based servers (Paper, Folia, Purpur) 1.17+, it uses reflection to register and cancel
 * PlayerTotemDeathEvent if available. For Spigot or pre-1.17, it uses EntityDamageEvent to detect
 * fatal damage and force death if a totem is present. Compatible with Minecraft 1.12–1.21.8.
 * <p>
 * Contributors:
 * - Config toggle: Enabled via "disable-totems" in config.yml (default: true).
 * - To consume totems without activation, uncomment the totem removal code in onEntityDamage.
 * - For custom conditions (e.g., disable only in certain worlds), add checks like
 *   !player.getWorld().getName().equals("allowed_world").
 * - Uses reflection to avoid direct import of Paper-specific PlayerTotemDeathEvent.
 * - If reflection fails, or you need separate versions, split into Paper (1.17+) and Spigot (1.12–1.16.5) classes.
 */
public class TotemDisablerListener implements Listener {

    private final NakedAndAfraid plugin;
    private boolean totemEventRegistered = false;

    public TotemDisablerListener(NakedAndAfraid plugin) {
        this.plugin = plugin;
        plugin.debugLog("[TotemDisablerListener] Initialized for Bukkit version " + Bukkit.getBukkitVersion());

        if (!plugin.getConfig().getBoolean("disable-totems", true)) {
            plugin.debugLog("[TotemDisablerListener] Totem disabling is turned off in config.");
            return;
        }

        if (registerTotemDeathEvent()) {
            totemEventRegistered = true;
            plugin.debugLog("[TotemDisablerListener] Successfully registered PlayerTotemDeathEvent (Paper 1.17+ detected).");
        } else {
            plugin.debugLog("[TotemDisablerListener] Using EntityDamageEvent fallback for Spigot or pre-1.17.");
        }

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Attempts to register PlayerTotemDeathEvent using reflection for Paper 1.17+ compatibility.
     * Returns true if registration succeeds.
     */
    private boolean registerTotemDeathEvent() {
        try {
            Class<?> totemEventClass = Class.forName("com.destroystokyo.paper.event.player.PlayerTotemDeathEvent");
            if (!Event.class.isAssignableFrom(totemEventClass)) {
                plugin.debugLog("[TotemDisablerListener] PlayerTotemDeathEvent does not extend org.bukkit.event.Event.");
                return false;
            }
            Method setCancelledMethod = totemEventClass.getMethod("setCancelled", boolean.class);
            Method getPlayerMethod = totemEventClass.getMethod("getPlayer");

            EventExecutor executor = (listener, event) -> {
                if (totemEventClass.isInstance(event)) {
                    try {
                        Player player = (Player) getPlayerMethod.invoke(event);
                        plugin.debugLog("[TotemDisablerListener] Detected totem activation for player " +
                                player.getName() + ", cancelling PlayerTotemDeathEvent.");
                        setCancelledMethod.invoke(event, true);
                    } catch (Exception e) {
                        plugin.debugLog("[TotemDisablerListener] Failed to cancel PlayerTotemDeathEvent: " + e.getMessage());
                    }
                }
            };

            plugin.getServer().getPluginManager().registerEvent(
                    (Class<? extends Event>) totemEventClass,
                    this,
                    EventPriority.NORMAL,
                    executor,
                    plugin,
                    false
            );
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            plugin.debugLog("[TotemDisablerListener] PlayerTotemDeathEvent not available: " + e.getMessage());
            return false;
        }
    }

    /**
     * Fallback for Spigot or pre-1.17 Paper: Cancel fatal damage if totem present and force death.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(EntityDamageEvent event) {
        // Skip if Paper event is registered (1.17+ Paper, Folia, Purpur)
        if (totemEventRegistered) {
            plugin.debugLog("[TotemDisablerListener] Skipping EntityDamageEvent due to PlayerTotemDeathEvent usage.");
            return;
        }

        if (!plugin.getConfig().getBoolean("disable-totems", true)) {
            plugin.debugLog("[TotemDisablerListener] Totem disabling is turned off in config.");
            return;
        }

        if (event.getEntity() instanceof Player player) {
            double finalDamage = event.getFinalDamage();
            double health = player.getHealth();
            plugin.debugLog("[TotemDisablerListener] Checking damage for player " + player.getName() +
                    ": finalDamage=" + finalDamage + ", health=" + health);

            // Optional: Uncomment to allow totems in specific worlds
            // if (player.getWorld().getName().equals("allowed_world")) {
            //     plugin.debugLog("[TotemDisablerListener] Allowing totem in world " + player.getWorld().getName());
            //     return;
            // }

            if (finalDamage >= health) {
                ItemStack mainHand = player.getInventory().getItemInMainHand();
                ItemStack offHand = player.getInventory().getItemInOffHand();
                if ((mainHand != null && mainHand.getType() == Material.TOTEM_OF_UNDYING) ||
                        (offHand != null && offHand.getType() == Material.TOTEM_OF_UNDYING)) {
                    plugin.debugLog("[TotemDisablerListener] Totem detected for player " + player.getName() +
                            ", disabling activation by cancelling damage and forcing death.");
                    event.setCancelled(true);
                    player.setHealth(0);

                    // Optional: Uncomment to consume the totem without activation
                    // if (mainHand != null && mainHand.getType() == Material.TOTEM_OF_UNDYING) {
                    //     player.getInventory().setItemInMainHand(null);
                    //     plugin.debugLog("[TotemDisablerListener] Consumed totem from main hand for " + player.getName());
                    // } else if (offHand != null && offHand.getType() == Material.TOTEM_OF_UNDYING) {
                    //     player.getInventory().setItemInOffHand(null);
                    //     plugin.debugLog("[TotemDisablerListener] Consumed totem from offhand for " + player.getName());
                    // }

                    // Optional: Uncomment to send a message (requires version-specific message handling)
                    // if (isAdventureSupported()) {
                    //     player.sendMessage(Component.text("Totems are disabled!").color(NamedTextColor.RED));
                    // } else {
                    //     player.sendMessage("§cTotems are disabled!");
                    // }
                }
            }
        }
    }

    /**
     * Checks if the server supports the Adventure API (1.19+).
     */
    private boolean isAdventureSupported() {
        try {
            String version = Bukkit.getBukkitVersion().split("-")[0];
            String[] parts = version.split("\\.");
            int major = Integer.parseInt(parts[1]);
            return major >= 19;
        } catch (Exception e) {
            plugin.debugLog("[TotemDisablerListener] Failed to parse Bukkit version: " + e.getMessage());
            return false;
        }
    }
}