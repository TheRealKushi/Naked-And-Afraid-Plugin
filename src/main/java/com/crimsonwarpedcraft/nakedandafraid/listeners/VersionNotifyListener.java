package com.crimsonwarpedcraft.nakedandafraid.listeners;

import com.crimsonwarpedcraft.nakedandafraid.NakedAndAfraid;
import com.crimsonwarpedcraft.nakedandafraid.util.VersionChecker;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.entity.Player;

public class VersionNotifyListener implements Listener {

    private final NakedAndAfraid plugin;

    public VersionNotifyListener(NakedAndAfraid plugin) {
        this.plugin = plugin;
        plugin.debugLog("[VersionNotifyListener] Initialized VersionNotifyListener");
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
            player.sendMessage(Component.text("§c[NakedAndAfraid] You're using Version " + currentVersion +
                    ", however, Version " + latestVersion + " is available.").color(NamedTextColor.GOLD));

            player.sendMessage(
                    Component.text("§eDownload it here: ")
                            .append(Component.text("https://modrinth.com/plugin/naked-and-afraid-plugin/versions")
                                    .color(NamedTextColor.GOLD)
                                    .clickEvent(ClickEvent.openUrl("https://modrinth.com/plugin/naked-and-afraid-plugin/versions")))
            );
            plugin.debugLog("[VersionNotifyListener] Sent update notification to " + player.getName());
        } else {
            plugin.debugLog("[VersionNotifyListener] Version is up to date or no latest version available for " + player.getName());
        }
    }
}