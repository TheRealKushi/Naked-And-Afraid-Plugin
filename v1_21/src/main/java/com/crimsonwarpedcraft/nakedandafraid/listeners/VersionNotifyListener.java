package com.crimsonwarpedcraft.nakedandafraid.listeners;

import com.crimsonwarpedcraft.nakedandafraid.NakedAndAfraid;
import com.crimsonwarpedcraft.nakedandafraid.util.VersionChecker;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class VersionNotifyListener implements Listener {

    private final NakedAndAfraid plugin;

    public VersionNotifyListener(NakedAndAfraid plugin) {
        this.plugin = plugin;
        plugin.debugLog("[VersionNotifyListener] Initialized VersionNotifyListener for Bukkit version " + Bukkit.getBukkitVersion());
    }

    private boolean isAdventureSupported() {
        String version = Bukkit.getBukkitVersion().split("-")[0];
        try {
            String[] parts = version.split("\\.");
            int major = Integer.parseInt(parts[1]);
            return major >= 19;
        } catch (Exception e) {
            plugin.debugLog("[VersionNotifyListener] Failed to parse Bukkit version: " + version);
            return false;
        }
    }

    private void sendOutdatedMessage(Player player, String currentVersion, String latestVersion) {
        com.crimsonwarpedcraft.nakedandafraid.util.MessageSender.sendOutdated(player, currentVersion, latestVersion);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.debugLog("[VersionNotifyListener] Player " + player.getName() + " joined, checking if OP");

        if (!player.isOp()) {
            plugin.debugLog("[VersionNotifyListener] Player " + player.getName() + " is not an OP, skipping version check");
            return;
        }

        String currentVersion = plugin.getDescription().getVersion();
        VersionChecker versionChecker = new VersionChecker(plugin);
        String latestVersion = versionChecker.getLatestVersion();
        plugin.debugLog("[VersionNotifyListener] Current version: " + currentVersion + ", latest version: " + (latestVersion != null ? latestVersion : "null"));

        if (versionChecker.isOutdated(currentVersion)) {
            plugin.debugLog("[VersionNotifyListener] Notifying " + player.getName() + " of outdated version");
            sendOutdatedMessage(player, currentVersion, latestVersion);
            plugin.debugLog("[VersionNotifyListener] Sent update notification to " + player.getName());
        } else {
            plugin.debugLog("[VersionNotifyListener] Version is up to date or no latest version available for " + player.getName());
        }
    }
}