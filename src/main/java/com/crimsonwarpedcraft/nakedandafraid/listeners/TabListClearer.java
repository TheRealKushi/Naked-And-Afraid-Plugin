package com.crimsonwarpedcraft.nakedandafraid.listeners;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.stream.Collectors;

public class TabListClearer {
    public static void register(JavaPlugin plugin) {
        ProtocolLibrary.getProtocolManager().addPacketListener(
                new PacketAdapter(plugin, PacketType.Play.Server.PLAYER_INFO) {
                    @Override
                    public void onPacketSending(PacketEvent event) {
                        Player viewer = event.getPlayer();
                        PacketContainer packet = event.getPacket();

                        // Get the list of player info entries
                        List<PlayerInfoData> originalList = packet.getPlayerInfoDataLists().read(0);

                        // Keep only the viewer in the list
                        List<PlayerInfoData> filteredList = originalList.stream()
                                .filter(info -> info.getProfile().getUUID().equals(viewer.getUniqueId()))
                                .collect(Collectors.toList());

                        packet.getPlayerInfoDataLists().write(0, filteredList);
                    }
                }
        );
    }
}
