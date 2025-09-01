package com.crimsonwarpedcraft.nakedandafraid.listeners;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class TabListClearer {

    private static final Set<UUID> hiddenPlayers = new HashSet<>();

    public static void register(JavaPlugin plugin) {
        ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();

        // Intercept PLAYER_INFO packets
        protocolManager.addPacketListener(new PacketAdapter(plugin, ListenerPriority.HIGHEST, PacketType.Play.Server.PLAYER_INFO) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if (event.getPacket().getPlayerInfoDataLists().size() == 0) return;

                List<PlayerInfoData> dataList = event.getPacket().getPlayerInfoDataLists().read(0);
                if (dataList == null || dataList.isEmpty()) return;

                dataList.removeIf(infoData -> {
                    if (infoData == null) return true; // remove null entries
                    return hiddenPlayers.contains(infoData.getProfile().getId());
                });
            }
        });

        // Hide existing players
        Bukkit.getOnlinePlayers().forEach(player -> hidePlayer(player, plugin));

        // Hide players on join
        Bukkit.getServer().getPluginManager().registerEvents(new PlayerJoinListener(plugin), plugin);
    }

    public static void hidePlayer(Player player, JavaPlugin plugin) {
        hiddenPlayers.add(player.getUniqueId());
        Bukkit.getOnlinePlayers().forEach(other -> {
            if (!other.equals(player)) {
                other.hidePlayer(plugin, player);
                player.hidePlayer(plugin, other);
            }
        });
    }

    public static void showPlayer(Player player, JavaPlugin plugin) {
        hiddenPlayers.remove(player);
        Bukkit.getOnlinePlayers().forEach(other -> {
            if (!other.equals(player)) {
                other.showPlayer(plugin, player);
                player.showPlayer(plugin, other);
            }
        });
    }

    public static boolean isHidden(Player player) {
        return hiddenPlayers.contains(player);
    }
}
