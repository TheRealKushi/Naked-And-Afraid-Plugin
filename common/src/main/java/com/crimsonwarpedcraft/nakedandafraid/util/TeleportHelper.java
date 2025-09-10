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
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.HashSet;
import java.util.Set;

public class TeleportHelper implements Listener {

    private final NakedAndAfraid plugin;
    private final Set<Player> frozenPlayers = new HashSet<>();

    public TeleportHelper(NakedAndAfraid plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.debugLog("[TeleportHelper] Initialized TeleportHelper and registered events for Bukkit version " + Bukkit.getBukkitVersion());
    }

    /**
     * Checks if the server supports the Adventure API (Minecraft 1.19+).
     *
     * @return true if the server version is 1.19 or higher, false otherwise.
     */
    private boolean isAdventureSupported() {
        String version = Bukkit.getBukkitVersion().split("-")[0];
        try {
            String[] parts = version.split("\\.");
            int major = Integer.parseInt(parts[1]);
            return major >= 19;
        } catch (Exception e) {
            plugin.debugLog("[TeleportHelper] Failed to parse Bukkit version: " + version);
            return false;
        }
    }

    /**
     * Checks if the server is pre-1.13 (Minecraft 1.12).
     *
     * @return true if the server version is 1.12, false otherwise.
     */
    private boolean isPre113() {
        return !Bukkit.getBukkitVersion().matches(".*1\\.(1[3-9]|2[0-1]).*");
    }

    /**
     * Starts a countdown teleport.
     * If teleport-on-countdown-end is true, teleport happens after countdown.
     * Otherwise, teleport happens before countdown.
     */
    public void startCountdownTeleport(Player player, Location target) {
        plugin.debugLog("[TeleportHelper] Starting countdown teleport for player " + player.getName() +
                " to location " + formatLocation(target));

        boolean isAdventure = isAdventureSupported();

        if (!plugin.getConfig().getBoolean("enable-countdown", true)) {
            plugin.debugLog("[TeleportHelper] Countdown disabled, teleporting " + player.getName() + " immediately");
            player.teleport(target);
            if (isAdventure) {
                player.sendMessage(Component.text("Teleported!").color(NamedTextColor.GREEN));
            } else {
                player.sendMessage("§aTeleported!");
            }
            plugin.debugLog("[TeleportHelper] Teleported " + player.getName() + " to " + formatLocation(target));
            return;
        }

        if (frozenPlayers.contains(player)) {
            plugin.debugLog("[TeleportHelper] Player " + player.getName() + " is already teleporting, aborting");
            if (isAdventure) {
                player.sendMessage(Component.text("You are already teleporting!").color(NamedTextColor.RED));
            } else {
                player.sendMessage("§cYou are already teleporting!");
            }
            return;
        }

        int duration = plugin.getConfig().getInt("countdown-duration", 10);
        BarColor color;
        try {
            color = BarColor.valueOf(plugin.getConfig().getString("countdown-color", "RED").toUpperCase());
            plugin.debugLog("[TeleportHelper] Using boss bar color: " + color);
        } catch (IllegalArgumentException e) {
            color = BarColor.RED;
            plugin.debugLog("[TeleportHelper] Invalid color in config, defaulting to RED");
        }

        boolean teleportOnCountdownEnd = plugin.isTeleportOnCountdownEnd();
        plugin.debugLog("[TeleportHelper] Teleport on countdown end: " + teleportOnCountdownEnd);

        String messageTemplate = plugin.getConfig().getString("countdown-message", "Game starts in {time}");
        String formattedMessage = messageTemplate.replace("{time}", String.valueOf(duration));
        BossBar bossBar = Bukkit.createBossBar(formattedMessage, color, BarStyle.SOLID);
        bossBar.addPlayer(player);
        bossBar.setProgress(1);
        plugin.debugLog("[TeleportHelper] Created boss bar for " + player.getName() + " with message: " + formattedMessage);

        frozenPlayers.add(player);
        plugin.debugLog("[TeleportHelper] Added " + player.getName() + " to frozenPlayers");

        new BukkitRunnable() {
            int timeLeft = duration;

            @Override
            public void run() {
                if (timeLeft == duration) {
                    if (!teleportOnCountdownEnd) {
                        plugin.debugLog("[TeleportHelper] Teleporting " + player.getName() + " at countdown start");
                        player.teleport(target);
                        if (isAdventure) {
                            player.sendMessage(Component.text("Teleported!").color(NamedTextColor.GREEN));
                        } else {
                            player.sendMessage("§aTeleported!");
                        }
                        plugin.debugLog("[TeleportHelper] Teleported " + player.getName() + " to " + formatLocation(target));
                    }
                }

                if (timeLeft <= 0) {
                    bossBar.removePlayer(player);
                    frozenPlayers.remove(player);
                    plugin.debugLog("[TeleportHelper] Removed " + player.getName() + " from frozenPlayers and boss bar");
                    if (teleportOnCountdownEnd) {
                        plugin.debugLog("[TeleportHelper] Teleporting " + player.getName() + " at countdown end");
                        player.teleport(target);
                        if (isAdventure) {
                            player.sendMessage(Component.text("Teleported!").color(NamedTextColor.GREEN));
                        } else {
                            player.sendMessage("§aTeleported!");
                        }
                        plugin.debugLog("[TeleportHelper] Teleported " + player.getName() + " to " + formatLocation(target));
                    }
                    Sound plingSound = isPre113() ? Sound.valueOf("NOTE_PLING") : Sound.BLOCK_NOTE_BLOCK_PLING;
                    player.playSound(player.getLocation(), plingSound, 1, 1);
                    plugin.debugLog("[TeleportHelper] Played pling sound (" + plingSound + ") for " + player.getName());
                    this.cancel();
                    plugin.debugLog("[TeleportHelper] Cancelled countdown task for " + player.getName());
                    return;
                }

                String tickMessage = messageTemplate.replace("{time}", String.valueOf(timeLeft));
                bossBar.setTitle(tickMessage);
                bossBar.setProgress((double) timeLeft / duration);
                Sound bellSound = isPre113() ? Sound.valueOf("NOTE_BELL") : Sound.BLOCK_NOTE_BLOCK_BELL;
                player.playSound(player.getLocation(), bellSound, 1, 1);
                plugin.debugLog("[TeleportHelper] Updated boss bar for " + player.getName() +
                        ": timeLeft=" + timeLeft + ", progress=" + ((double) timeLeft / duration) +
                        ", message=" + tickMessage + ", sound=" + bellSound);

                timeLeft--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
        plugin.debugLog("[TeleportHelper] Started countdown task for " + player.getName() +
                " with duration " + duration + " seconds");
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (frozenPlayers.contains(event.getPlayer())) {
            if (event.getFrom().distanceSquared(event.getTo()) > 0) {
                event.setTo(event.getFrom());
                plugin.debugLog("[TeleportHelper] Cancelled move for " + event.getPlayer().getName() +
                        " from " + formatLocation(event.getFrom()) + " to " + formatLocation(event.getTo()));
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (frozenPlayers.contains(event.getPlayer())) {
            event.setCancelled(true);
            plugin.debugLog("[TeleportHelper] Cancelled interact event for " + event.getPlayer().getName() +
                    ", action: " + event.getAction());
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (frozenPlayers.contains(event.getPlayer())) {
            event.setCancelled(true);
            plugin.debugLog("[TeleportHelper] Cancelled item drop for " + event.getPlayer().getName());
        }
    }

    @EventHandler
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        if (!isPre113() && frozenPlayers.contains(event.getPlayer())) {
            event.setCancelled(true);
            plugin.debugLog("[TeleportHelper] Cancelled hand swap for " + event.getPlayer().getName());
        }
    }

    @EventHandler
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        if (frozenPlayers.contains(event.getPlayer())) {
            event.setCancelled(true);
            if (isAdventureSupported()) {
                event.getPlayer().sendMessage(Component.text("You cannot run commands while teleporting!").color(NamedTextColor.RED));
            } else {
                event.getPlayer().sendMessage("§cYou cannot run commands while teleporting!");
            }
            plugin.debugLog("[TeleportHelper] Cancelled command '" + event.getMessage() +
                    "' for " + event.getPlayer().getName());
        }
    }

    /**
     * Helper method to format a Location for debug logging.
     */
    private String formatLocation(Location location) {
        return String.format("(world=%s, x=%.2f, y=%.2f, z=%.2f)",
                location.getWorld() != null ? location.getWorld().getName() : "null",
                location.getX(), location.getY(), location.getZ());
    }
}