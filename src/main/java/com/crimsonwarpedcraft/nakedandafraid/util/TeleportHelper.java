package com.crimsonwarpedcraft.nakedandafraid.util;

import com.crimsonwarpedcraft.nakedandafraid.NakedAndAfraid;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Set;

public class TeleportHelper implements Listener {

    private final NakedAndAfraid plugin;
    private final Set<Player> frozenPlayers = new HashSet<>();

    public TeleportHelper(NakedAndAfraid plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Starts a countdown teleport.
     * If teleport-on-countdown-end is true, teleport happens after countdown.
     * Otherwise, teleport happens before countdown.
     */
    public void startCountdownTeleport(Player player, Location target) {
        if (!plugin.getConfig().getBoolean("enable-countdown", true)) {
            player.teleport(target);
            return;
        }

        if (frozenPlayers.contains(player)) {
            player.sendMessage("§cYou are already teleporting!");
            return;
        }

        int duration = plugin.getConfig().getInt("countdown-duration", 10);
        BarColor color;
        try {
            color = BarColor.valueOf(plugin.getConfig().getString("countdown-color", "RED").toUpperCase());
        } catch (IllegalArgumentException e) {
            color = BarColor.RED;
        }

        boolean teleportOnCountdownEnd = plugin.isTeleportOnCountdownEnd();

        String messageTemplate = plugin.getConfig().getString("countdown-message", "Game starts in {time}");
        String formattedMessage = messageTemplate.replace("{time}", String.valueOf(duration));
        BossBar bossBar = Bukkit.createBossBar(formattedMessage, color, BarStyle.SOLID);
        bossBar.addPlayer(player);
        bossBar.setProgress(1);

        frozenPlayers.add(player);

        new BukkitRunnable() {
            int timeLeft = duration;

            @Override
            public void run() {
                if (timeLeft == duration) {
                    if (!teleportOnCountdownEnd) {
                        player.teleport(target);
                        player.sendMessage("§aTeleported!");
                    }
                }

                if (timeLeft <= 0) {
                    bossBar.removePlayer(player);
                    frozenPlayers.remove(player);
                    if (teleportOnCountdownEnd) {
                        player.teleport(target);
                        player.sendMessage("§aTeleported!");
                    }
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1, 1);
                    this.cancel();
                    return;
                }

                bossBar.setTitle("Deathcraft starts in " + timeLeft);
                bossBar.setProgress((double) timeLeft / duration);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1, 1);

                timeLeft--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (frozenPlayers.contains(event.getPlayer())) {
            if (event.getFrom().distanceSquared(event.getTo()) > 0) {
                event.setTo(event.getFrom());
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (frozenPlayers.contains(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (frozenPlayers.contains(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        if (frozenPlayers.contains(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        if (frozenPlayers.contains(event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cYou cannot run commands while teleporting!");
        }
    }
}