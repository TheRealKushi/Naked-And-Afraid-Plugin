// V1_8/TabListClearer

package com.crimsonwarpedcraft.nakedandafraid.v1_8.listeners;

import com.crimsonwarpedcraft.nakedandafraid.v1_8.NakedAndAfraid;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class TabListClearer implements Listener {

    private final NakedAndAfraid nakedAndAfraid;
    private final Plugin plugin;
    private boolean enabled;

    // Cached NMS version string (e.g. "v1_8_R3")
    private final String nmsVersion;

    public TabListClearer(NakedAndAfraid plugin, Plugin pluginVar) {
        this.nakedAndAfraid = plugin;
        this.plugin = pluginVar;
        // Derive NMS version from the CraftPlayer class package
        this.nmsVersion = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
        plugin.debugLog("[TabListClearer] Initializing TabListClearer for Bukkit version "
                + Bukkit.getBukkitVersion() + ", NMS version: " + nmsVersion);
        Bukkit.getPluginManager().registerEvents(this, nakedAndAfraid.getPlugin());
        plugin.debugLog("[TabListClearer] Registered event listeners for TabListClearer");
    }

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
                nakedAndAfraid.debugLog("[TabListClearer] Hid tab list for player " + player.getName()
                        + " in world " + player.getWorld().getName());
            } else {
                nakedAndAfraid.debugLog("[TabListClearer] Skipped hiding tab list for player " + player.getName()
                        + " in disabled world " + player.getWorld().getName());
            }
        }
        nakedAndAfraid.debugLog("[TabListClearer] Applied tab list hiding to " + playerCount + " players");

        enabled = true;
        plugin.getLogger().info("Naked And Afraid - Tab Hider Enabled.");
        nakedAndAfraid.debugLog("[TabListClearer] Tab list hiding enabled successfully");
    }

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
                nakedAndAfraid.debugLog("[TabListClearer] Restored tab list for player " + player.getName()
                        + " in world " + player.getWorld().getName());
            }
        }
        nakedAndAfraid.debugLog("[TabListClearer] Restored tab list for " + playerCount + " players");

        enabled = false;
        plugin.getLogger().info("Naked And Afraid - Tab Hider Disabled.");
        nakedAndAfraid.debugLog("[TabListClearer] Tab list hiding disabled successfully");
    }

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

    public boolean isEnabled() {
        nakedAndAfraid.debugLog("[TabListClearer] Checked tab list hiding status: enabled=" + enabled);
        return enabled;
    }

    public void applyToPlayer(Player player) {
        if (!enabled) {
            nakedAndAfraid.debugLog("[TabListClearer] Tab list hiding not enabled, skipping applyToPlayer for "
                    + player.getName());
            return;
        }
        if (!nakedAndAfraid.isWorldEnabled(player.getWorld().getName())) {
            nakedAndAfraid.debugLog("[TabListClearer] Skipped applying tab list hiding for player "
                    + player.getName() + " in disabled world " + player.getWorld().getName());
            restoreTabListForPlayer(player);
            return;
        }

        nakedAndAfraid.debugLog("[TabListClearer] Applying tab list hiding to player " + player.getName()
                + " in world " + player.getWorld().getName());
        updateTabListForPlayer(player);
        nakedAndAfraid.debugLog("[TabListClearer] Successfully applied tab list hiding to " + player.getName());
    }

    /**
     * Sends a PacketPlayOutPlayerInfo packet to recipient about subject.
     * action must be a field name on EnumPlayerInfoAction: "ADD_PLAYER" or "REMOVE_PLAYER".
     */
    private void sendPlayerInfoPacket(Player recipient, Player subject, String actionName) throws Exception {
        String nmsBase = "net.minecraft.server." + nmsVersion + ".";

        Class<?> packetClass = Class.forName(nmsBase + "PacketPlayOutPlayerInfo");
        Class<?> actionClass = Class.forName(nmsBase + "PacketPlayOutPlayerInfo$EnumPlayerInfoAction");
        Class<?> entityPlayerClass = Class.forName(nmsBase + "EntityPlayer");

        Object action = actionClass.getField(actionName).get(null);
        Object entityPlayer = subject.getClass().getMethod("getHandle").invoke(subject);

        // PacketPlayOutPlayerInfo(EnumPlayerInfoAction, EntityPlayer...)
        // In 1.8 the varargs constructor takes the action and an Iterable or varargs of EntityPlayer
        Constructor<?> constructor = packetClass.getDeclaredConstructor(actionClass, Iterable.class);
        List<Object> players = new ArrayList<Object>();
        players.add(entityPlayer);
        Object packet = constructor.newInstance(action, players);

        sendNmsPacket(recipient, packet);
    }

    private void sendNmsPacket(Player recipient, Object nmsPacket) throws Exception {
        nakedAndAfraid.debugLog("[TabListClearer] Sending NMS packet "
                + nmsPacket.getClass().getSimpleName() + " to " + recipient.getName());

        String nmsBase = "net.minecraft.server." + nmsVersion + ".";
        Object entityPlayer = recipient.getClass().getMethod("getHandle").invoke(recipient);
        Field connectionField = entityPlayer.getClass().getField("playerConnection");
        Object playerConnection = connectionField.get(entityPlayer);

        Class<?> packetInterface = Class.forName(nmsBase + "Packet");
        Method sendPacket = playerConnection.getClass().getMethod("sendPacket", packetInterface);
        sendPacket.invoke(playerConnection, nmsPacket);

        nakedAndAfraid.debugLog("[TabListClearer] NMS packet sent successfully to " + recipient.getName());
    }

    private void updateTabListForPlayer(Player player) {
        nakedAndAfraid.debugLog("[TabListClearer] Hiding other players from tab list for " + player.getName());
        List<Player> others = new ArrayList<Player>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.equals(player) && nakedAndAfraid.isWorldEnabled(p.getWorld().getName())) {
                others.add(p);
            }
        }
        for (Player other : others) {
            try {
                sendPlayerInfoPacket(player, other, "REMOVE_PLAYER");
            } catch (Exception e) {
                plugin.getLogger().severe("[TabListClearer] Failed to hide " + other.getName()
                        + " from " + player.getName() + ": " + e.getMessage());
            }
        }
        nakedAndAfraid.debugLog("[TabListClearer] Sent REMOVE_PLAYER for " + others.size()
                + " players to " + player.getName());
    }

    private void restoreTabListForPlayer(Player player) {
        nakedAndAfraid.debugLog("[TabListClearer] Restoring full tab list for " + player.getName());
        List<Player> others = new ArrayList<Player>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.equals(player) && nakedAndAfraid.isWorldEnabled(p.getWorld().getName())) {
                others.add(p);
            }
        }
        for (Player other : others) {
            try {
                sendPlayerInfoPacket(player, other, "ADD_PLAYER");
            } catch (Exception e) {
                plugin.getLogger().severe("[TabListClearer] Failed to restore " + other.getName()
                        + " for " + player.getName() + ": " + e.getMessage());
            }
        }
        nakedAndAfraid.debugLog("[TabListClearer] Successfully restored tab list for " + player.getName());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        final Player joining = event.getPlayer();
        nakedAndAfraid.debugLog("[TabListClearer] PlayerJoinEvent triggered for " + joining.getName());
        Bukkit.getScheduler().runTaskLater(nakedAndAfraid.getPlugin(), new Runnable() {
            @Override
            public void run() {
                applyToPlayer(joining);
                for (Player other : Bukkit.getOnlinePlayers()) {
                    if (!other.equals(joining) && nakedAndAfraid.isWorldEnabled(other.getWorld().getName())) {
                        applyToPlayer(other);
                    }
                }
                nakedAndAfraid.debugLog("[TabListClearer] Processed PlayerJoinEvent for " + joining.getName());
            }
        }, 1L);
        nakedAndAfraid.debugLog("[TabListClearer] Scheduled tab list update for " + joining.getName());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        nakedAndAfraid.debugLog("[TabListClearer] PlayerQuitEvent triggered for " + event.getPlayer().getName());
        nakedAndAfraid.debugLog("[TabListClearer] No tab list cleanup needed for "
                + event.getPlayer().getName() + " on quit");
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        final Player player = event.getPlayer();
        nakedAndAfraid.debugLog("[TabListClearer] PlayerChangedWorldEvent triggered for " + player.getName()
                + " from world " + event.getFrom().getName() + " to " + player.getWorld().getName());
        Bukkit.getScheduler().runTaskLater(nakedAndAfraid.getPlugin(), new Runnable() {
            @Override
            public void run() {
                applyToPlayer(player);
                nakedAndAfraid.debugLog("[TabListClearer] Processed PlayerChangedWorldEvent for "
                        + player.getName());
            }
        }, 1L);
        nakedAndAfraid.debugLog("[TabListClearer] Scheduled tab list update for " + player.getName());
    }
}