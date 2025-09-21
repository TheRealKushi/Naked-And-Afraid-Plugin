// V1_17/VersionNotifyListener

package com.crimsonwarpedcraft.nakedandafraid.v1_17.listeners;

import com.crimsonwarpedcraft.nakedandafraid.v1_17.NakedAndAfraid;
import com.crimsonwarpedcraft.nakedandafraid.v1_17.util.VersionChecker;
import com.crimsonwarpedcraft.nakedandafraid.v1_17.util.MessageSender;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

public class VersionNotifyListener implements Listener {

    private final NakedAndAfraid nakedAndAfraid;
    private final Plugin plugin;

    public VersionNotifyListener(NakedAndAfraid nakedAndAfraid) {
        this.nakedAndAfraid = nakedAndAfraid;
        this.plugin = nakedAndAfraid.getPlugin();
        nakedAndAfraid.debugLog("[VersionNotifyListener] Initialized VersionNotifyListener for Bukkit version " + Bukkit.getBukkitVersion());
    }

    private boolean isAdventureSupported() {
        String version = Bukkit.getBukkitVersion().split("-")[0];
        try {
            String[] parts = version.split("\\.");
            int major = Integer.parseInt(parts[1]);
            return major >= 19;
        } catch (Exception e) {
            nakedAndAfraid.debugLog("[VersionNotifyListener] Failed to parse Bukkit version: " + version);
            return false;
        }
    }

    private void sendOutdatedMessage(Player player, String currentVersion, String latestVersion) {
        MessageSender.sendOutdated(player, currentVersion, latestVersion);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        nakedAndAfraid.debugLog("[VersionNotifyListener] Player " + player.getName() + " joined, checking if OP");

        if (!player.isOp()) {
            nakedAndAfraid.debugLog("[VersionNotifyListener] Player " + player.getName() + " is not an OP, skipping version check");
            return;
        }

        String currentVersion = plugin.getDescription().getVersion();
        VersionChecker versionChecker = new VersionChecker(nakedAndAfraid);
        String latestVersion = versionChecker.getLatestVersion();
        nakedAndAfraid.debugLog("[VersionNotifyListener] Current version: " + currentVersion + ", latest version: " + (latestVersion != null ? latestVersion : "null"));

        if (versionChecker.isOutdated(currentVersion)) {
            nakedAndAfraid.debugLog("[VersionNotifyListener] Notifying " + player.getName() + " of outdated version");
            sendOutdatedMessage(player, currentVersion, latestVersion);
            nakedAndAfraid.debugLog("[VersionNotifyListener] Sent update notification to " + player.getName());
        } else {
            nakedAndAfraid.debugLog("[VersionNotifyListener] Version is up to date or no latest version available for " + player.getName());
        }
    }
}