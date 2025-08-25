package com.crimsonwarpedcraft.nakedandafraid.listeners;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class TabListClearer {

    public static void register(JavaPlugin plugin) {
        ProtocolLibrary.getProtocolManager().addPacketListener(
                new PacketAdapter(plugin, PacketType.Play.Server.PLAYER_INFO) {
                    @Override
                    public void onPacketSending(PacketEvent event) {
                        Player viewer = event.getPlayer();
                        PacketContainer packet = event.getPacket();

                        // Read the original actions and entries
                        Set<EnumWrappers.PlayerInfoAction> actions = packet.getPlayerInfoActions().read(0);
                        List<PlayerInfoData> originalList = packet.getPlayerInfoDataLists().read(0);

                        if (originalList == null || originalList.isEmpty()) return;

                        // Filter the entries: only include non-null entries for the viewer
                        List<PlayerInfoData> filteredList = originalList.stream()
                                .filter(info -> info != null && info.getProfile() != null)
                                .filter(info -> info.getProfile().getUUID().equals(viewer.getUniqueId()))
                                .collect(Collectors.toList());

                        try {
                            PacketContainer newPacket = new PacketContainer(PacketType.Play.Server.PLAYER_INFO);
                            newPacket.getPlayerInfoActions().write(0, actions);
                            newPacket.getPlayerInfoDataLists().write(0, filteredList);

                            ProtocolLibrary.getProtocolManager().sendServerPacket(viewer, newPacket);
                            event.setCancelled(true);
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }
                }
        );
    }
}
