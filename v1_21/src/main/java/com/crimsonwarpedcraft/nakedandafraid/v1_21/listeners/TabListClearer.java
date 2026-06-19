package com.crimsonwarpedcraft.nakedandafraid.v1_21.listeners;

import com.crimsonwarpedcraft.nakedandafraid.v1_21.NakedAndAfraid;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.*;

public class TabListClearer implements Listener {

    private final NakedAndAfraid nakedAndAfraid;
    private final Plugin plugin;

    private boolean enabled;

    public TabListClearer(NakedAndAfraid plugin, Plugin pluginVar) {
        this.nakedAndAfraid = plugin;
        this.plugin = pluginVar;
        plugin.debugLog("[TabListClearer] Initializing TabListClearer for Bukkit version " + Bukkit.getBukkitVersion());
        Bukkit.getPluginManager().registerEvents(this, nakedAndAfraid.getPlugin());
        plugin.debugLog("[TabListClearer] Registered event listeners for TabListClearer");
    }

    /**
     * Enables tab list hiding by removing other players from the tab list.
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
     * Disables tab list hiding by restoring the full tab list for all players.
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

    /**
     * Applies tab list hiding to a specific player.
     */
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

    private Object createListedEntry(Player subject, boolean listed) throws Exception {
        Object entityPlayer = subject.getClass().getMethod("getHandle").invoke(subject);
        Object gameProfile = entityPlayer.getClass().getMethod("getGameProfile").invoke(entityPlayer);

        Class<?> entryClass = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket$Entry");
        Class<?> gameTypeClass = Class.forName("net.minecraft.world.level.GameType");
        Object placeholderGameType = gameTypeClass.getEnumConstants()[0]; // not applied, action not included

        java.lang.reflect.RecordComponent[] components = entryClass.getRecordComponents();
        Class<?>[] paramTypes = new Class<?>[components.length];
        Object[] args = new Object[components.length];

        for (int i = 0; i < components.length; i++) {
            paramTypes[i] = components[i].getType();
            switch (components[i].getName()) {
                case "profileId" -> args[i] = subject.getUniqueId();
                case "profile" -> args[i] = gameProfile;
                case "listed" -> args[i] = listed;
                case "latency" -> args[i] = subject.getPing();
                case "gameMode" -> args[i] = placeholderGameType;
                case "showHat" -> args[i] = true;
                case "listOrder" -> args[i] = 0;
                default -> args[i] = null; // displayName, chatSession, etc.
            }
        }

        return entryClass.getDeclaredConstructor(paramTypes).newInstance(args);
    }

    private void sendListedUpdate(Player recipient, Player subject, boolean listed) throws Exception {
        Class<?> packetClass = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket");
        Class<?> actionClass = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket$Action");

        @SuppressWarnings({"rawtypes", "unchecked"})
        EnumSet actions = EnumSet.of(
                (Enum) Arrays.stream(actionClass.getEnumConstants())
                        .filter(a -> a.toString().equals("UPDATE_LISTED"))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("UPDATE_LISTED action not found"))
        );

        Object entry = createListedEntry(subject, listed);
        Object packet = packetClass.getDeclaredConstructor(EnumSet.class, List.class)
                .newInstance(actions, List.of(entry));

        sendNmsPacket(recipient, packet);
    }

    /**
     * Updates the tab list for a player to show only their own name.
     */
    private void updateTabListForPlayer(Player player) {
        nakedAndAfraid.debugLog("[TabListClearer] Hiding other players from tab list for " + player.getName());
        try {
            List<java.util.UUID> othersToRemove = Bukkit.getOnlinePlayers().stream()
                    .filter(p -> !p.equals(player)
                            && nakedAndAfraid.isWorldEnabled(p.getWorld().getName()))
                    .map(Player::getUniqueId)
                    .collect(java.util.stream.Collectors.toList());

            if (!othersToRemove.isEmpty()) {
                sendRemovePacket(player, othersToRemove);
                nakedAndAfraid.debugLog("[TabListClearer] Sent remove packet for "
                        + othersToRemove.size() + " players to " + player.getName());
            }

            // Add the player themselves back so they can see their own entry
            sendAddPacket(player, player, true);
            nakedAndAfraid.debugLog("[TabListClearer] Sent add-back packet for "
                    + player.getName() + " to themselves");

        } catch (Exception e) {
            plugin.getLogger().severe("[TabListClearer] Failed to update tab list for "
                    + player.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Restores the full tab list for a player.
     */
    private void restoreTabListForPlayer(Player player) {
        nakedAndAfraid.debugLog("[TabListClearer] Restoring full tab list for " + player.getName());
        try {
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.equals(player)
                        && nakedAndAfraid.isWorldEnabled(other.getWorld().getName())) {
                    sendAddPacket(player, other, false);
                }
            }
            nakedAndAfraid.debugLog("[TabListClearer] Restored tab list for " + player.getName());
        } catch (Exception e) {
            plugin.getLogger().severe("[TabListClearer] Failed to restore tab list for "
                    + player.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Sends a ClientboundPlayerInfoUpdatePacket for a single entry using NMS reflection.
     * @param recipient  the player who receives the packet
     * @param subject    the player whose info is being sent
     * @param addOnly    if true, uses only ADD_PLAYER action; if false, uses ADD_PLAYER | UPDATE_LISTED
     */
    private void sendPlayerInfoUpdate(Player recipient, Player subject, boolean addOnly) throws Exception {
        Class<?> craftPlayerClass = subject.getClass();
        Object entityPlayer = craftPlayerClass.getMethod("getHandle").invoke(subject);

        Class<?> packetClass = Class.forName(
                "net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket");
        Class<?> actionClass = Class.forName(
                "net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket$Action");

        // Build EnumSet of actions
        @SuppressWarnings({"rawtypes", "unchecked"})
        EnumSet actions = EnumSet.of(
                (Enum) Arrays.stream(actionClass.getEnumConstants())
                        .filter(a -> a.toString().equals("ADD_PLAYER"))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("ADD_PLAYER action not found"))
        );

        if (!addOnly) {
            Arrays.stream(actionClass.getEnumConstants())
                    .filter(a -> a.toString().equals("UPDATE_LISTED"))
                    .findFirst()
                    .ifPresent(actions::add);
        }

        // ClientboundPlayerInfoUpdatePacket(EnumSet<Action>, Collection<ServerPlayer>)
        Object packet = packetClass
                .getDeclaredConstructor(java.util.EnumSet.class, java.util.Collection.class)
                .newInstance(actions, List.of(entityPlayer));

        sendNmsPacket(recipient, packet);
    }

    /**
     * Sends a raw NMS packet to a player via their connection using reflection.
     */
    private void sendNmsPacket(Player recipient, Object nmsPacket) throws Exception {
        nakedAndAfraid.debugLog("[TabListClearer] Sending NMS packet " + nmsPacket.getClass().getSimpleName()
                + " to " + recipient.getName());
        Object entityPlayer = recipient.getClass().getMethod("getHandle").invoke(recipient);
        Object connection = entityPlayer.getClass().getField("connection").get(entityPlayer);
        // Paper 1.21 uses 'send', older builds use 'sendPacket'
        try {
            connection.getClass().getMethod("send", Class.forName("net.minecraft.network.protocol.Packet"))
                    .invoke(connection, nmsPacket);
        } catch (NoSuchMethodException e) {
            connection.getClass().getMethod("sendPacket", Class.forName("net.minecraft.network.protocol.Packet"))
                    .invoke(connection, nmsPacket);
        }
        nakedAndAfraid.debugLog("[TabListClearer] NMS packet sent successfully to " + recipient.getName());
    }

    /**
     * Hook for testing: called instead of real NMS send. Override in tests.
     */
    protected void sendRemovePacket(Player recipient, List<java.util.UUID> uuids) throws Exception {
        Object removePacket = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket")
                .getDeclaredConstructor(List.class)
                .newInstance(uuids);
        sendNmsPacket(recipient, removePacket);
    }

    /**
     * Hook for testing: called instead of real NMS send. Override in tests.
     */
    protected void sendAddPacket(Player recipient, Player subject, boolean addOnly) throws Exception {
        sendPlayerInfoUpdate(recipient, subject, addOnly);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        nakedAndAfraid.debugLog("[TabListClearer] PlayerJoinEvent triggered for " + event.getPlayer().getName());
        // Delay by 1 tick so server's own tab list packets are sent first
        Bukkit.getScheduler().runTaskLater(nakedAndAfraid.getPlugin(), () -> {
            // Update the joining player's own tab list
            applyToPlayer(event.getPlayer());
            // Also update all other online players to remove the new joiner from their tab lists
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