package com.crimsonwarpedcraft.nakedandafraid.v1_21.util;

import com.crimsonwarpedcraft.nakedandafraid.v1_21.NakedAndAfraid;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Set;

/**
 * Utility class for handling countdown-based teleportation with player freezing.
 * Compatible with Minecraft 1.8.8-1.21.8 (Spigot 1.8.8+, Paper 1.16.5+).
 */
public class TeleportHelper implements Listener {

    private final Plugin plugin;
    private final NakedAndAfraid nakedAndAfraid;
    private final Set<Player> frozenPlayers = new HashSet<>();

    public TeleportHelper(Plugin plugin, NakedAndAfraid nakedAndAfraid) {
        this.plugin = plugin;
        this.nakedAndAfraid = nakedAndAfraid;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        nakedAndAfraid.debugLog("[TeleportHelper] Initialized TeleportHelper and registered events for Bukkit version " + Bukkit.getBukkitVersion());
    }

    /**
     * Gets the set of frozen players.
     *
     * @return The set of players currently frozen during teleportation.
     */
    public Set<Player> getFrozenPlayers() {
        return frozenPlayers;
    }

    /**
     * Checks if the server is pre-1.13 (Minecraft 1.12 or earlier).
     *
     * @return true if the server version is before 1.13, false otherwise.
     */
    private boolean isPre113() {
        return !Bukkit.getBukkitVersion().matches(".*1\\.(1[3-9]|2[0-1]).*");
    }

    /**
     * Starts a countdown teleport.
     * If teleport-on-countdown-end is true, teleport happens after countdown.
     * Otherwise, teleport happens before countdown.
     *
     * @param player The player to teleport.
     * @param target The target location for teleportation.
     */
    public void startCountdownTeleport(Player player, Location target) {
        nakedAndAfraid.debugLog("[TeleportHelper] Starting countdown teleport for player " + player.getName() +
                " to location " + formatLocation(target));

        if (!nakedAndAfraid.getConfig().getBoolean("enable-countdown", true)) {
            nakedAndAfraid.debugLog("[TeleportHelper] Countdown disabled, teleporting " + player.getName() + " immediately");
            player.teleport(target);
            player.sendMessage("§aTeleported!");
            nakedAndAfraid.debugLog("[TeleportHelper] Teleported " + player.getName() + " to " + formatLocation(target));
            return;
        }

        if (frozenPlayers.contains(player)) {
            nakedAndAfraid.debugLog("[TeleportHelper] Player " + player.getName() + " is already teleporting, aborting");
            player.sendMessage("§cYou are already teleporting!");
            return;
        }

        int duration = nakedAndAfraid.getConfig().getInt("countdown-duration", 10);
        String messageTemplate = nakedAndAfraid.getConfig().getString("countdown-message", "Game starts in {time}");
        boolean teleportOnCountdownEnd = nakedAndAfraid.isTeleportOnCountdownEnd();
        nakedAndAfraid.debugLog("[TeleportHelper] Teleport on countdown end: " + teleportOnCountdownEnd);

        sendCountdownMessages(player, duration, messageTemplate, target, teleportOnCountdownEnd);

        frozenPlayers.add(player);
        nakedAndAfraid.debugLog("[TeleportHelper] Added " + player.getName() + " to frozenPlayers");
    }

    private void sendCountdownMessages(Player player, int duration, String messageTemplate, Location target, boolean teleportOnCountdownEnd) {
        new BukkitRunnable() {
            int timeLeft = duration;

            @Override
            public void run() {
                if (timeLeft == duration) {
                    if (!teleportOnCountdownEnd) {
                        nakedAndAfraid.debugLog("[TeleportHelper] Teleporting " + player.getName() + " at countdown start");
                        player.teleport(target);
                        player.sendMessage("§aTeleported!");
                        nakedAndAfraid.debugLog("[TeleportHelper] Teleported " + player.getName() + " to " + formatLocation(target));
                    }
                }

                if (timeLeft <= 0) {
                    frozenPlayers.remove(player);
                    nakedAndAfraid.debugLog("[TeleportHelper] Removed " + player.getName() + " from frozenPlayers");
                    if (teleportOnCountdownEnd) {
                        nakedAndAfraid.debugLog("[TeleportHelper] Teleporting " + player.getName() + " at countdown end");
                        player.teleport(target);
                        player.sendMessage("§aTeleported!");
                        nakedAndAfraid.debugLog("[TeleportHelper] Teleported " + player.getName() + " to " + formatLocation(target));
                    }
                    Sound plingSound = Sound.BLOCK_NOTE_BLOCK_PLING;
                    player.playSound(player.getLocation(), plingSound, 1, 1);
                    nakedAndAfraid.debugLog("[TeleportHelper] Played pling sound (" + plingSound + ") for " + player.getName());
                    this.cancel();
                    nakedAndAfraid.debugLog("[TeleportHelper] Cancelled countdown task for " + player.getName());
                    return;
                }

                String tickMessage = messageTemplate.replace("{time}", String.valueOf(timeLeft));
                player.sendMessage("§e" + tickMessage);
                Sound bellSound = Sound.BLOCK_NOTE_BLOCK_BELL;
                player.playSound(player.getLocation(), bellSound, 1, 1);
                nakedAndAfraid.debugLog("[TeleportHelper] Updated countdown for " + player.getName() +
                        ": timeLeft=" + timeLeft + ", message=" + tickMessage + ", sound=" + bellSound);

                timeLeft--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
        nakedAndAfraid.debugLog("[TeleportHelper] Started countdown task for " + player.getName() +
                " with duration " + duration + " seconds");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (frozenPlayers.contains(event.getPlayer())) {
            if (event.getFrom().distanceSquared(event.getTo()) > 0) {
                event.setTo(event.getFrom());
                nakedAndAfraid.debugLog("[TeleportHelper] Cancelled move for " + event.getPlayer().getName() +
                        " from " + formatLocation(event.getFrom()) + " to " + formatLocation(event.getTo()));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (frozenPlayers.contains(event.getPlayer())) {
            event.setCancelled(true);
            nakedAndAfraid.debugLog("[TeleportHelper] Cancelled interact event for " + event.getPlayer().getName() +
                    ", action: " + event.getAction());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (frozenPlayers.contains(event.getPlayer())) {
            event.setCancelled(true);
            nakedAndAfraid.debugLog("[TeleportHelper] Cancelled item drop for " + event.getPlayer().getName());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        if (frozenPlayers.contains(event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cYou cannot run commands while teleporting!");
            nakedAndAfraid.debugLog("[TeleportHelper] Cancelled command '" + event.getMessage() +
                    "' for " + event.getPlayer().getName());
        }
    }

    /**
     * Helper method to format a Location for debug logging.
     */
    private String formatLocation(Location location) {
        if (location == null) return "null";
        return String.format("(world=%s, x=%.2f, y=%.2f, z=%.2f)",
                location.getWorld() != null ? location.getWorld().getName() : "null",
                location.getX(), location.getY(), location.getZ());
    }
}