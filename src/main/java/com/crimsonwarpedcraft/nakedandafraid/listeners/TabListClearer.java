package com.crimsonwarpedcraft.nakedandafraid.listeners;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.scheduler.BukkitRunnable;

public class TabListClearer implements Listener {

    private final JavaPlugin plugin;

    public TabListClearer(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void startClearing() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    clearTabList(player);
                }
            }
        }.runTaskLater(plugin, 20L);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        clearTabList(event.getPlayer());
    }

    public void clearTabList(Player player) {
        player.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
    }
}
