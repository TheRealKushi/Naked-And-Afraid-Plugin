package com.crimsonwarpedcraft.nakedandafraid.listeners;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.PacketType;
import com.crimsonwarpedcraft.nakedandafraid.NakedAndAfraid;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class TabListClearer {

    private final NakedAndAfraid plugin;
    private final ProtocolManager protocolManager;
    private PacketAdapter packetAdapter;
    private boolean enabled;

    public TabListClearer(NakedAndAfraid plugin) {
        this.plugin = plugin;
        this.protocolManager = isProtocolLibAvailable() ? ProtocolLibrary.getProtocolManager() : null;
        plugin.debugLog("[TabListClearer] Initialized TabListClearer for Bukkit version " + Bukkit.getBukkitVersion() +
                (protocolManager == null ? " (ProtocolLib not available)" : ""));
    }

    /**
     * Checks if ProtocolLib is available.
     */
    private boolean isProtocolLibAvailable() {
        try {
            return ProtocolLibrary.getProtocolManager() != null;
        } catch (Exception e) {
            plugin.debugLog("[TabListClearer] ProtocolLib not available: " + e.getMessage());
            return false;
        }
    }

    /**
     * Checks if the server is pre-1.13 (Minecraft 1.12).
     */
    private boolean isPre113() {
        return !Bukkit.getBukkitVersion().matches(".*1\\.(1[3-9]|2[0-1]).*");
    }

    /** Enables tablist hiding */
    public void enable() {
        if (enabled) {
            plugin.debugLog("[TabListClearer] Tab list hiding already enabled");
            return;
        }
        if (protocolManager == null) {
            plugin.debugLog("[TabListClearer] Cannot enable tab list hiding: ProtocolLib not available");
            return;
        }

        packetAdapter = new PacketAdapter(plugin, PacketType.Play.Server.PLAYER_INFO) {
            @Override
            public void onPacketSending(PacketEvent event) {
                TabListClearer.this.plugin.debugLog("[TabListClearer] Cancelling PLAYER_INFO packet for player " +
                        event.getPlayer().getName());
                event.setCancelled(true);
            }
        };

        protocolManager.addPacketListener(packetAdapter);
        enabled = true;
        plugin.debugLog("[TabListClearer] Enabled tab list hiding");
    }

    /** Applies tablist hiding function to a specific player */
    public void applyToPlayer(Player player) {
        if (!enabled) {
            plugin.debugLog("[TabListClearer] Tab list hiding not enabled, skipping applyToPlayer for " +
                    player.getName());
            return;
        }
        if (protocolManager == null) {
            plugin.debugLog("[TabListClearer] Cannot apply tab list hiding to " + player.getName() +
                    ": ProtocolLib not available");
            return;
        }

        try {
            if (isPre113()) {
                // In 1.12, send a PLAYER_INFO packet to clear tab list
                protocolManager.sendServerPacket(player,
                        protocolManager.createPacket(PacketType.Play.Server.PLAYER_INFO));
                plugin.debugLog("[TabListClearer] Sent PLAYER_INFO packet to clear tab list for " +
                        player.getName() + " (1.12)");
            } else {
                // For 1.13+, send empty PLAYER_INFO packet
                protocolManager.sendServerPacket(player,
                        protocolManager.createPacket(PacketType.Play.Server.PLAYER_INFO));
                plugin.debugLog("[TabListClearer] Sent PLAYER_INFO packet to clear tab list for " +
                        player.getName() + " (1.13+)");
            }
        } catch (Exception e) {
            plugin.debugLog("[TabListClearer] Failed to send PLAYER_INFO packet to " +
                    player.getName() + ": " + e.getMessage());
        }
    }

    /** Disables tablist hiding */
    public void disable() {
        if (!enabled) {
            plugin.debugLog("[TabListClearer] Tab list hiding already disabled");
            return;
        }
        if (protocolManager == null || packetAdapter == null) {
            plugin.debugLog("[TabListClearer] Cannot disable tab list hiding: ProtocolLib or packetAdapter not available");
            return;
        }

        protocolManager.removePacketListener(packetAdapter);
        packetAdapter = null;
        enabled = false;
        plugin.debugLog("[TabListClearer] Disabled tab list hiding");
    }

    /** Toggle tablist hiding */
    public void toggle() {
        if (enabled) {
            disable();
            plugin.debugLog("[TabListClearer] Toggled tab list hiding to disabled");
        } else {
            enable();
            plugin.debugLog("[TabListClearer] Toggled tab list hiding to enabled");
        }
    }

    /** Returns current status */
    public boolean isEnabled() {
        return enabled;
    }
}