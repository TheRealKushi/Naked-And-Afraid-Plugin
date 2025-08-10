package com.crimsonwarpedcraft.nakedandafraid.listeners;

import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.logging.Level;

public class ResourcePackListener implements Listener {

    private final JavaPlugin plugin;

    private final String PACK_URL = "https://github.com/TheRealKushi/Deathcraft-Assets/releases/download/release-1.0.2/NakedAndAfraid-1.0.2.zip";
    private static final String PACK_HASH = "8a27d0fe2d6f1b4d4ca5b40163b35280454b8ef4";

    public ResourcePackListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        try {
            ResourcePackInfo packInfo = ResourcePackInfo.resourcePackInfo()
                    .uri(new URI(PACK_URL))
                    .hash(PACK_HASH)
                    .build();

            ResourcePackRequest request = ResourcePackRequest.resourcePackRequest()
                    .packs(packInfo)
                    .replace(true)
                    .required(true)
                    .prompt(Component.text("Please accept the NakedAndAfraid resource pack!"))
                    .build();

            player.sendResourcePacks(request);
        } catch (URISyntaxException e) {
            plugin.getLogger().severe("Invalid resource pack URL: " + PACK_URL);
            plugin.getLogger().log(Level.SEVERE, "Exception while sending resource pack", e);
        }
    }
}
