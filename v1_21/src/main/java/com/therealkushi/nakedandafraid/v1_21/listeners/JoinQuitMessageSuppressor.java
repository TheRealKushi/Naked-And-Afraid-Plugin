// V1_21/JoinQuitMessageSuppressor
package com.therealkushi.nakedandafraid.v1_21.listeners;

import com.therealkushi.nakedandafraid.v1_21.NakedAndAfraid;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.lang.reflect.Method;

/**
 * Listener to suppress join and quit messages in enabled worlds.
 */
public class JoinQuitMessageSuppressor implements Listener {
    private final NakedAndAfraid plugin;

    private static Boolean adventureJoinQuitSupported;
    private static Method joinMessageMethod;
    private static Method quitMessageMethod;

    public JoinQuitMessageSuppressor(NakedAndAfraid plugin) {
        this.plugin = plugin;
        plugin.debugLog("[JoinQuitMessageSuppressor] Initialized for Bukkit version " +
                Bukkit.getBukkitVersion() + ", adventureJoinQuitSupported=" + isAdventureJoinQuitSupported());
    }

    /**
     * Whether {@code PlayerJoinEvent#joinMessage(Component)} /
     * {@code PlayerQuitEvent#quitMessage(Component)} exist on this server.
     * This is a Paper API addition, NOT implied by the Minecraft version string -
     * a CraftBukkit/Spigot build of any MC version never has it. The previous
     * version-number heuristic here caused a NoSuchMethodError crash on join.
     * <p>
     * Resolved once, via reflection only, and invoked via {@link Method#'invoke'}
     * (never a direct call) so this class's bytecode never itself references
     * the Adventure {@code Component} type and stays loadable everywhere.
     */
    private boolean isAdventureJoinQuitSupported() {
        if (adventureJoinQuitSupported == null) {
            try {
                Class<?> componentClass = Class.forName("net.kyori.adventure.text.Component");
                joinMessageMethod = PlayerJoinEvent.class.getMethod("joinMessage", componentClass);
                quitMessageMethod = PlayerQuitEvent.class.getMethod("quitMessage", componentClass);
                adventureJoinQuitSupported = true;
            } catch (Throwable t) {
                plugin.debugLog("[JoinQuitMessageSuppressor] Adventure join/quit methods not present: " + t.getMessage());
                adventureJoinQuitSupported = false;
            }
        }
        return adventureJoinQuitSupported;
    }

    /**
     * Suppress join messages in enabled worlds.
     *
     * @param event Player join event.
     */
    @EventHandler
    public void onPlayerJoin(final PlayerJoinEvent event) {
        String playerName = event.getPlayer().getName();
        if (!plugin.isWorldEnabled(event.getPlayer().getWorld().getName())) {
            plugin.debugLog("[JoinQuitMessageSuppressor] Allowed join message for " + playerName +
                    " in disabled world " + event.getPlayer().getWorld().getName());
            return;
        }

        plugin.debugLog("[JoinQuitMessageSuppressor] Suppressing join message for " + playerName);

        if (isAdventureJoinQuitSupported()) {
            try {
                joinMessageMethod.invoke(event, (Object) null);
                plugin.debugLog("[JoinQuitMessageSuppressor] Used joinMessage(null) for " + playerName);
                return;
            } catch (Exception e) {
                plugin.debugLog("[JoinQuitMessageSuppressor] joinMessage invoke failed, falling back: " + e.getMessage());
            }
        }
        event.setJoinMessage(null);
        plugin.debugLog("[JoinQuitMessageSuppressor] Used setJoinMessage(null) for " + playerName);
    }

    /**
     * Suppress quit messages in enabled worlds.
     *
     * @param event Player quit event.
     */
    @EventHandler
    public void onPlayerQuit(final PlayerQuitEvent event) {
        String playerName = event.getPlayer().getName();
        if (!plugin.isWorldEnabled(event.getPlayer().getWorld().getName())) {
            plugin.debugLog("[JoinQuitMessageSuppressor] Allowed quit message for " + playerName +
                    " in disabled world " + event.getPlayer().getWorld().getName());
            return;
        }

        plugin.debugLog("[JoinQuitMessageSuppressor] Suppressing quit message for " + playerName);

        if (isAdventureJoinQuitSupported()) {
            try {
                quitMessageMethod.invoke(event, (Object) null);
                plugin.debugLog("[JoinQuitMessageSuppressor] Used quitMessage(null) for " + playerName);
                return;
            } catch (Exception e) {
                plugin.debugLog("[JoinQuitMessageSuppressor] quitMessage invoke failed, falling back: " + e.getMessage());
            }
        }
        event.setQuitMessage(null);
        plugin.debugLog("[JoinQuitMessageSuppressor] Used setQuitMessage(null) for " + playerName);
    }
}