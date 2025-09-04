package com.crimsonwarpedcraft.nakedandafraid.listeners;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.PacketType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class TabListClearer {

    private final JavaPlugin plugin;
    private final ProtocolManager protocolManager;
    private PacketAdapter packetAdapter;
    private boolean enabled;

    public TabListClearer(JavaPlugin plugin) {
        this.plugin = plugin;
        this.protocolManager = ProtocolLibrary.getProtocolManager();
    }

    /** Enables tablist hiding */
    public void enable() {
        if (enabled) return;

        packetAdapter = new PacketAdapter(plugin, PacketType.Play.Server.PLAYER_INFO) {
            @Override
            public void onPacketSending(PacketEvent event) {
                event.setCancelled(true);
            }
        };

        protocolManager.addPacketListener(packetAdapter);
        enabled = true;
    }

    /** Applies tablist hiding function to a specific player */
    public void applyToPlayer(Player player) {
        if (!enabled) return;

        protocolManager.sendServerPacket(player, protocolManager.createPacket(PacketType.Play.Server.PLAYER_INFO));
    }

    /** Disables tablist hiding */
    public void disable() {
        if (!enabled) return;

        protocolManager.removePacketListener(packetAdapter);
        packetAdapter = null;
        enabled = false;
    }

    /** Toggle tablist hiding */
    public void toggle() {
        if (enabled) disable();
        else enable();
    }

    /** Returns current status */
    public boolean isEnabled() {
        return enabled;
    }
}
