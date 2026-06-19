// V1_17/TabListClearer

package com.crimsonwarpedcraft.nakedandafraid.v1_17.listeners;

import com.crimsonwarpedcraft.nakedandafraid.v1_17.NakedAndAfraid;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class TabListClearer implements Listener {

    private final NakedAndAfraid nakedAndAfraid;
    private final Plugin plugin;
    private boolean enabled;

    // --- Lazily-resolved NMS reflection state ---
    private boolean nmsResolved;
    private boolean nmsAvailable;
    private Class<?> packetClass;
    private Class<?> actionClass;
    private Class<?> playerHandleClass;
    private String connectionFieldName;
    private String sendMethodName;

    /**
     * Candidate (packetClass, actionClass, playerHandleClass) triples, tried in order.
     * Index 0 matches Paper 1.17.1-1.19.x "legacy mapped" runtimes.
     * Index 1 matches fully Mojang-mapped runtimes (Paper 1.20.5+).
     */
    private static final String[][] NMS_CANDIDATES = {
            {
                    "net.minecraft.network.protocol.game.PacketPlayOutPlayerInfo",
                    "net.minecraft.network.protocol.game.PacketPlayOutPlayerInfo$EnumPlayerInfoAction",
                    "net.minecraft.server.level.EntityPlayer"
            },
            {
                    "net.minecraft.network.protocol.game.ClientboundPlayerInfoPacket",
                    "net.minecraft.network.protocol.game.ClientboundPlayerInfoPacket$Action",
                    "net.minecraft.server.level.ServerPlayer"
            }
    };

    private static final String[] CONNECTION_FIELD_CANDIDATES = {"playerConnection", "connection", "b"};
    private static final String[] SEND_METHOD_CANDIDATES = {"sendPacket", "send"};

    public TabListClearer(NakedAndAfraid plugin, Plugin pluginVar) {
        this.nakedAndAfraid = plugin;
        this.plugin = pluginVar;
        plugin.debugLog("[TabListClearer] Initializing TabListClearer for Bukkit version " + Bukkit.getBukkitVersion());
        Bukkit.getPluginManager().registerEvents(this, nakedAndAfraid.getPlugin());
        plugin.debugLog("[TabListClearer] Registered event listeners for TabListClearer");
    }

    /**
     * Enables tab list hiding for all online players in enabled worlds.
     */
    public void enable() {
        if (enabled) {
            nakedAndAfraid.debugLog("[TabListClearer] Tab list hiding already enabled, skipping enable");
            return;
        }

        nakedAndAfraid.debugLog("[TabListClearer] Enabling tab list hiding for all online players");
        int playerCount = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (nakedAndAfraid.isWorldEnabled(player.getWorld().getName())) {
                updateTabListForPlayer(player);
                playerCount++;
                nakedAndAfraid.debugLog("[TabListClearer] Hid tab list for player " + player.getName() +
                        " in world " + player.getWorld().getName());
            } else {
                nakedAndAfraid.debugLog("[TabListClearer] Skipped hiding tab list for player " + player.getName() +
                        " in disabled world " + player.getWorld().getName());
            }
        }
        nakedAndAfraid.debugLog("[TabListClearer] Applied tab list hiding to " + playerCount + " players");

        enabled = true;
        plugin.getLogger().info("Naked And Afraid - Tab Hider Enabled.");
        nakedAndAfraid.debugLog("[TabListClearer] Tab list hiding enabled successfully");
    }

    /**
     * Disables tab list hiding, restoring the full tab list for all players.
     */
    public void disable() {
        if (!enabled) {
            nakedAndAfraid.debugLog("[TabListClearer] Tab list hiding already disabled, skipping disable");
            return;
        }

        nakedAndAfraid.debugLog("[TabListClearer] Disabling tab list hiding for all online players");
        int playerCount = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (nakedAndAfraid.isWorldEnabled(player.getWorld().getName())) {
                restoreTabListForPlayer(player);
                playerCount++;
                nakedAndAfraid.debugLog("[TabListClearer] Restored tab list for player " + player.getName() +
                        " in world " + player.getWorld().getName());
            }
        }
        nakedAndAfraid.debugLog("[TabListClearer] Restored tab list for " + playerCount + " players");

        enabled = false;
        plugin.getLogger().info("Naked And Afraid - Tab Hider Disabled.");
        nakedAndAfraid.debugLog("[TabListClearer] Tab list hiding disabled successfully");
    }

    /**
     * Toggles tab list hiding.
     */
    public void toggle() {
        if (enabled) {
            nakedAndAfraid.debugLog("[TabListClearer] Toggling tab list hiding from enabled to disabled");
            disable();
        } else {
            nakedAndAfraid.debugLog("[TabListClearer] Toggling tab list hiding from disabled to enabled");
            enable();
        }
        nakedAndAfraid.debugLog("[TabListClearer] Toggled tab list hiding, new state: enabled=" + enabled);
    }

    /**
     * Returns current status.
     */
    public boolean isEnabled() {
        nakedAndAfraid.debugLog("[TabListClearer] Checked tab list hiding status: enabled=" + enabled);
        return enabled;
    }

    public void applyToPlayer(Player player) {
        if (!enabled) {
            nakedAndAfraid.debugLog("[TabListClearer] Tab list hiding not enabled, skipping applyToPlayer for " + player.getName());
            return;
        }
        if (!nakedAndAfraid.isWorldEnabled(player.getWorld().getName())) {
            nakedAndAfraid.debugLog("[TabListClearer] Skipped applying tab list hiding for player " + player.getName() +
                    " in disabled world " + player.getWorld().getName());
            restoreTabListForPlayer(player);
            return;
        }

        nakedAndAfraid.debugLog("[TabListClearer] Applying tab list hiding to player " + player.getName() +
                " in world " + player.getWorld().getName());
        updateTabListForPlayer(player);
        nakedAndAfraid.debugLog("[TabListClearer] Successfully applied tab list hiding to " + player.getName());
    }

    /**
     * Resolves the NMS classes/fields/methods needed for player-info packets, once.
     * Tries each candidate mapping set in {@link #NMS_CANDIDATES} until one works.
     */
    private synchronized boolean resolveNms(Player sample) {
        if (nmsResolved) {
            return nmsAvailable;
        }
        nmsResolved = true;

        try {
            Object handle = sample.getClass().getMethod("getHandle").invoke(sample);
            ClassLoader loader = handle.getClass().getClassLoader();

            for (String[] candidate : NMS_CANDIDATES) {
                try {
                    Class<?> pClass = Class.forName(candidate[0], true, loader);
                    Class<?> aClass = Class.forName(candidate[1], true, loader);
                    Class<?> plClass = Class.forName(candidate[2], true, loader);

                    // Make sure there's a (action, playerHandle[]) constructor (varargs-style)
                    Object arr = Array.newInstance(plClass, 0);
                    pClass.getDeclaredConstructor(aClass, arr.getClass());

                    this.packetClass = pClass;
                    this.actionClass = aClass;
                    this.playerHandleClass = plClass;
                    nakedAndAfraid.debugLog("[TabListClearer] Resolved NMS classes: packet=" + pClass.getName()
                            + ", action=" + aClass.getName() + ", player=" + plClass.getName());
                    break;
                } catch (Exception ignored) {
                    // Try next candidate
                }
            }

            if (packetClass == null) {
                plugin.getLogger().severe("[TabListClearer] Could not resolve the player-info packet classes for "
                        + "this server (Bukkit version: " + Bukkit.getBukkitVersion() + "). Tab list hiding will "
                        + "be disabled. Tried: net.minecraft.network.protocol.game.PacketPlayOutPlayerInfo and "
                        + "net.minecraft.network.protocol.game.ClientboundPlayerInfoPacket.");
                return false;
            }

            // Resolve the player connection field on the player handle
            for (String fieldName : CONNECTION_FIELD_CANDIDATES) {
                try {
                    handle.getClass().getField(fieldName);
                    connectionFieldName = fieldName;
                    break;
                } catch (NoSuchFieldException ignored) {
                    // try next
                }
            }

            if (connectionFieldName == null) {
                plugin.getLogger().severe("[TabListClearer] Could not resolve the player connection field on "
                        + handle.getClass().getName() + ". Tab list hiding will be disabled.");
                packetClass = null;
                return false;
            }

            // Resolve the send method on the connection object
            Object connection = handle.getClass().getField(connectionFieldName).get(handle);
            outer:
            for (String methodName : SEND_METHOD_CANDIDATES) {
                for (Method m : connection.getClass().getMethods()) {
                    if (m.getName().equals(methodName) && m.getParameterCount() == 1) {
                        sendMethodName = methodName;
                        break outer;
                    }
                }
            }

            if (sendMethodName == null) {
                plugin.getLogger().severe("[TabListClearer] Could not resolve a packet-send method on "
                        + connection.getClass().getName() + ". Tab list hiding will be disabled.");
                packetClass = null;
                return false;
            }

            nmsAvailable = true;
            nakedAndAfraid.debugLog("[TabListClearer] NMS reflection resolved: connectionField="
                    + connectionFieldName + ", sendMethod=" + sendMethodName);
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("[TabListClearer] Failed to resolve NMS reflection: " + e
                    + ". Tab list hiding will be disabled.");
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Sends a PacketPlayOutPlayerInfo REMOVE_PLAYER packet for each other player
     * to the given player, hiding them from the tab list.
     */
    private void updateTabListForPlayer(Player player) {
        nakedAndAfraid.debugLog("[TabListClearer] Hiding other players from tab list for " + player.getName());

        if (!resolveNms(player)) {
            return;
        }

        List<Player> others = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.equals(player) && nakedAndAfraid.isWorldEnabled(p.getWorld().getName())) {
                others.add(p);
            }
        }
        for (Player other : others) {
            try {
                sendPlayerInfoPacket(player, other, "REMOVE_PLAYER");
            } catch (Exception e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                plugin.getLogger().severe("[TabListClearer] Failed to hide " + other.getName()
                        + " from " + player.getName() + ": " + cause);
            }
        }
        nakedAndAfraid.debugLog("[TabListClearer] Sent REMOVE_PLAYER for " + others.size()
                + " players to " + player.getName());
    }

    /**
     * Sends a PacketPlayOutPlayerInfo ADD_PLAYER packet for each other player
     * to the given player, restoring them in the tab list.
     */
    private void restoreTabListForPlayer(Player player) {
        nakedAndAfraid.debugLog("[TabListClearer] Restoring full tab list for " + player.getName());

        if (!resolveNms(player)) {
            return;
        }

        List<Player> others = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.equals(player) && nakedAndAfraid.isWorldEnabled(p.getWorld().getName())) {
                others.add(p);
            }
        }
        for (Player other : others) {
            try {
                sendPlayerInfoPacket(player, other, "ADD_PLAYER");
            } catch (Exception e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                plugin.getLogger().severe("[TabListClearer] Failed to restore " + other.getName()
                        + " for " + player.getName() + ": " + cause);
            }
        }
        nakedAndAfraid.debugLog("[TabListClearer] Successfully restored tab list for " + player.getName());
    }

    /**
     * Sends a PacketPlayOutPlayerInfo packet using the previously resolved NMS classes.
     * actionName is "REMOVE_PLAYER" or "ADD_PLAYER".
     */
    private void sendPlayerInfoPacket(Player recipient, Player subject, String actionName) throws Exception {
        Object entityPlayer = subject.getClass().getMethod("getHandle").invoke(subject);

        Object action = null;
        for (Object constant : actionClass.getEnumConstants()) {
            if (((Enum<?>) constant).name().equals(actionName)) {
                action = constant;
                break;
            }
        }
        if (action == null) {
            throw new IllegalStateException("Action not found: " + actionName + " in " + actionClass.getName());
        }

        Object arr = Array.newInstance(playerHandleClass, 1);
        Array.set(arr, 0, entityPlayer);

        Object packet = packetClass
                .getDeclaredConstructor(actionClass, arr.getClass())
                .newInstance(action, arr);

        sendNmsPacket(recipient, packet);
    }

    private void sendNmsPacket(Player recipient, Object nmsPacket) throws Exception {
        Object entityPlayer = recipient.getClass().getMethod("getHandle").invoke(recipient);
        Object connection = entityPlayer.getClass().getField(connectionFieldName).get(entityPlayer);

        for (Method m : connection.getClass().getMethods()) {
            if (m.getName().equals(sendMethodName) && m.getParameterCount() == 1
                    && m.getParameterTypes()[0].isAssignableFrom(nmsPacket.getClass())) {
                m.invoke(connection, nmsPacket);
                return;
            }
        }
        throw new NoSuchMethodException("No matching " + sendMethodName + "(Packet) method found on "
                + connection.getClass().getName());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        nakedAndAfraid.debugLog("[TabListClearer] PlayerJoinEvent triggered for " + event.getPlayer().getName());
        Bukkit.getScheduler().runTaskLater(nakedAndAfraid.getPlugin(), () -> {
            applyToPlayer(event.getPlayer());
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.equals(event.getPlayer()) && nakedAndAfraid.isWorldEnabled(other.getWorld().getName())) {
                    applyToPlayer(other);
                }
            }
            nakedAndAfraid.debugLog("[TabListClearer] Processed PlayerJoinEvent for " + event.getPlayer().getName());
        }, 1L);
        nakedAndAfraid.debugLog("[TabListClearer] Scheduled tab list update for " + event.getPlayer().getName());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        nakedAndAfraid.debugLog("[TabListClearer] PlayerQuitEvent triggered for " + event.getPlayer().getName());
        nakedAndAfraid.debugLog("[TabListClearer] No tab list cleanup needed for " + event.getPlayer().getName() + " on quit");
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        nakedAndAfraid.debugLog("[TabListClearer] PlayerChangedWorldEvent triggered for " + event.getPlayer().getName() +
                " from world " + event.getFrom().getName() + " to " + event.getPlayer().getWorld().getName());
        Bukkit.getScheduler().runTaskLater(nakedAndAfraid.getPlugin(), () -> {
            applyToPlayer(event.getPlayer());
            nakedAndAfraid.debugLog("[TabListClearer] Processed PlayerChangedWorldEvent for " + event.getPlayer().getName());
        }, 1L);
        nakedAndAfraid.debugLog("[TabListClearer] Scheduled tab list update for " + event.getPlayer().getName());
    }
}