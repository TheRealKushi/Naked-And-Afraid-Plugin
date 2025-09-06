package com.crimsonwarpedcraft.nakedandafraid.listeners;

import com.crimsonwarpedcraft.nakedandafraid.NakedAndAfraid;
import com.crimsonwarpedcraft.nakedandafraid.util.VersionChecker;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.entity.Player;

public class VersionNotifyListener implements Listener {

    private final NakedAndAfraid plugin;

    public VersionNotifyListener(NakedAndAfraid plugin) {
        this.plugin = plugin;
        plugin.debugLog("[VersionNotifyListener] Initialized VersionNotifyListener for Bukkit version " + Bukkit.getBukkitVersion());
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
            plugin.debugLog("[VersionNotifyListener] Failed to parse Bukkit version: " + version);
            return false;
        }
    }

    /**
     * Sends a message to the player, using Adventure API for 1.19+ or legacy chat for 1.12–1.18.2.
     *
     * @param player  The player to send the message to.
     * @param message The message content.
     * @param isUpdateMessage Whether this is the update notification (affects clickable URL for 1.19+).
     */
    private void sendMessage(Player player, String message, boolean isUpdateMessage) {
        if (isAdventureSupported()) {
            Component component = Component.text(message).color(NamedTextColor.GOLD);
            if (isUpdateMessage) {
                component = Component.text("Download it here: ")
                        .append(Component.text("https://modrinth.com/plugin/naked-and-afraid-plugin/versions")
                                .color(NamedTextColor.GOLD)
                                .clickEvent(ClickEvent.openUrl("https://modrinth.com/plugin/naked-and-afraid-plugin/versions")));
            }
            player.sendMessage(component);
        } else {
            player.sendMessage("§6" + message);
            if (isUpdateMessage) {
                player.sendMessage("§6Download it at: https://modrinth.com/plugin/naked-and-afraid-plugin/versions");
            }
        }
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
            sendMessage(player, "[NakedAndAfraid] You're using Version " + currentVersion +
                    ", however, Version " + latestVersion + " is available.", true);
            plugin.debugLog("[VersionNotifyListener] Sent update notification to " + player.getName());
        } else {
            plugin.debugLog("[VersionNotifyListener] Version is up to date or no latest version available for " + player.getName());
        }
    }
}