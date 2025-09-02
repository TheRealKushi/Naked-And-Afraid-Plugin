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
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (!player.isOp()) return;

        String currentVersion = plugin.getDescription().getVersion();
        String latestVersion = VersionChecker.getLatestVersion();

        if (VersionChecker.isOutdated(currentVersion)) {
            player.sendMessage(Component.text("§c[NakedAndAfraid] You're using Version " + currentVersion +
                    ", however, Version " + latestVersion + " is available.").color(NamedTextColor.GOLD));

            // Second message with clickable download link
            player.sendMessage(
                    Component.text("§eDownload it here: ")
                            .append(Component.text("https://modrinth.com/plugin/naked-and-afraid-plugin/versions")
                                    .color(NamedTextColor.GOLD)
                                    .clickEvent(ClickEvent.openUrl("https://modrinth.com/plugin/naked-and-afraid-plugin/versions")))
            );
        }
    }
}
