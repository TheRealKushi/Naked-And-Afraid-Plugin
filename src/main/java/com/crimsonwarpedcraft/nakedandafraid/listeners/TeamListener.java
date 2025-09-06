package com.crimsonwarpedcraft.nakedandafraid.listeners;

import com.crimsonwarpedcraft.nakedandafraid.NakedAndAfraid;
import com.crimsonwarpedcraft.nakedandafraid.team.TeamCommands;
import com.crimsonwarpedcraft.nakedandafraid.team.TeamsManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.UUID;

public class TeamListener implements Listener {

    private final NakedAndAfraid plugin;
    private final TeamsManager teamsManager;
    private final TeamCommands teamCommands;

    private static final String TEAM_BLOCK_SELECTOR_NAME = "Team Block Selector";

    public TeamListener(NakedAndAfraid plugin, TeamsManager teamsManager, TeamCommands teamCommands) {
        this.plugin = plugin;
        this.teamsManager = teamsManager;
        this.teamCommands = teamCommands;
        plugin.debugLog("[TeamListener] Initialized TeamListener for Bukkit version " + Bukkit.getBukkitVersion());
    }

    /**
     * Checks if the server supports the Adventure API (Minecraft 1.19+).
     */
    private boolean isAdventureSupported() {
        String version = Bukkit.getBukkitVersion().split("-")[0];
        try {
            String[] parts = version.split("\\.");
            int major = Integer.parseInt(parts[1]);
            return major >= 19;
        } catch (Exception e) {
            plugin.debugLog("[TeamListener] Failed to parse Bukkit version: " + version);
            return false;
        }
    }

    /**
     * Checks if the server is pre-1.13 (Minecraft 1.12).
     */
    private boolean isPre113() {
        return !Bukkit.getBukkitVersion().matches(".*1\\.(1[3-9]|2[0-1]).*");
    }

    /**
     * Checks if the server is pre-1.16 (Minecraft 1.12–1.15.2).
     */
    private boolean isPre116() {
        return !Bukkit.getBukkitVersion().matches(".*1\\.(1[6-9]|2[0-1]).*");
    }

    /**
     * Sends a message to the player, using Adventure API for 1.19+ or legacy chat for 1.12–1.18.2.
     */
    private void sendMessage(Player player, String message, String legacyColor) {
        if (isAdventureSupported()) {
            NamedTextColor color = parseNamedTextColor(legacyColor);
            player.sendMessage(Component.text(message).color(color != null ? color : NamedTextColor.WHITE));
        } else {
            player.sendMessage(legacyColor + message);
        }
    }

    /**
     * Parses a legacy color code to NamedTextColor for 1.19+.
     */
    private NamedTextColor parseNamedTextColor(String legacyColor) {
        return switch (legacyColor) {
            case "§c" -> NamedTextColor.RED;
            case "§a" -> NamedTextColor.GREEN;
            case "§f" -> NamedTextColor.WHITE;
            default -> null;
        };
    }

    /**
     * Parses a string color to ChatColor for 1.12.
     */
    private ChatColor parseChatColor(String colorName) {
        return switch (colorName.toUpperCase()) {
            case "RED" -> ChatColor.RED;
            case "GOLD" -> ChatColor.GOLD;
            case "YELLOW" -> ChatColor.YELLOW;
            case "GREEN" -> ChatColor.GREEN;
            case "AQUA" -> ChatColor.AQUA;
            case "BLUE" -> ChatColor.BLUE;
            case "DARK_PURPLE" -> ChatColor.DARK_PURPLE;
            case "LIGHT_PURPLE" -> ChatColor.LIGHT_PURPLE;
            case "WHITE" -> ChatColor.WHITE;
            default -> null;
        };
    }

    @EventHandler
    public void onPlayerUseSelector(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            plugin.debugLog("[TeamListener] Ignoring PlayerInteractEvent for " + event.getPlayer().getName() +
                    ", not main hand: " + event.getHand());
            return;
        }
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            plugin.debugLog("[TeamListener] Ignoring PlayerInteractEvent for " + event.getPlayer().getName() +
                    ", action not RIGHT_CLICK_BLOCK: " + event.getAction());
            return;
        }

        Player player = event.getPlayer();
        plugin.debugLog("[TeamListener] Processing selector use for " + player.getName());

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || !item.hasItemMeta()) {
            plugin.debugLog("[TeamListener] No item or no metadata for " + player.getName());
            return;
        }
        if (item.getType() != Material.IRON_AXE) {
            plugin.debugLog("[TeamListener] Item is not IRON_AXE for " + player.getName() +
                    ", found: " + item.getType());
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            plugin.debugLog("[TeamListener] No item meta for " + player.getName());
            return;
        }

        boolean isValidSelector;
        if (isAdventureSupported()) {
            Component displayName = meta.displayName();
            isValidSelector = displayName != null && displayName.equals(Component.text(TEAM_BLOCK_SELECTOR_NAME).color(NamedTextColor.GOLD));
        } else {
            String displayName = meta.getDisplayName();
            isValidSelector = displayName != null && displayName.equals("§6" + TEAM_BLOCK_SELECTOR_NAME);
        }

        if (!isValidSelector) {
            plugin.debugLog("[TeamListener] Item display name does not match Team Block Selector for " +
                    player.getName() + ", found: " + (isAdventureSupported() ? meta.displayName() : meta.getDisplayName()));
            return;
        }

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) {
            plugin.debugLog("[TeamListener] No block clicked by " + player.getName());
            return;
        }

        event.setCancelled(true);
        plugin.debugLog("[TeamListener] Cancelled PlayerInteractEvent for " + player.getName() +
                ", clicked block: " + formatBlockLocation(clickedBlock));
        teamCommands.onTeamBlockSelectorUse(player, clickedBlock);
        plugin.debugLog("[TeamListener] Called onTeamBlockSelectorUse for " + player.getName() +
                " on block: " + formatBlockLocation(clickedBlock));
    }

    @EventHandler
    public void onPlayerUseLocatorCompass(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            plugin.debugLog("[TeamListener] Ignoring compass use for " + event.getPlayer().getName() +
                    ", not main hand: " + event.getHand());
            return;
        }
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR &&
                event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            plugin.debugLog("[TeamListener] Ignoring compass use for " + event.getPlayer().getName() +
                    ", action not RIGHT_CLICK_AIR or RIGHT_CLICK_BLOCK: " + event.getAction());
            return;
        }

        Player player = event.getPlayer();
        plugin.debugLog("[TeamListener] Processing compass use for " + player.getName());

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || !item.hasItemMeta()) {
            plugin.debugLog("[TeamListener] No item or no metadata for compass use by " + player.getName());
            return;
        }
        if (item.getType() != Material.COMPASS) {
            plugin.debugLog("[TeamListener] Item is not COMPASS for " + player.getName() +
                    ", found: " + item.getType());
            return;
        }

        if (isPre116()) {
            plugin.debugLog("[TeamListener] CompassMeta not supported in pre-1.16, checking team lodestone for " + player.getName());
            TeamsManager.Team team = teamCommands.getTeamForPlayer(player);
            if (team == null) {
                plugin.debugLog("[TeamListener] No team found for " + player.getName());
                sendMessage(player, "You are not in a team.", "§c");
                return;
            }
            Location lodestone = teamsManager.getLodestone(team.getName());
            if (lodestone == null) {
                plugin.debugLog("[TeamListener] No lodestone set for team " + team.getName());
                sendMessage(player, "Your team does not have a lodestone set.", "§c");
                return;
            }
            double distance = player.getLocation().distance(lodestone);
            plugin.debugLog("[TeamListener] Distance from " + player.getName() +
                    " to lodestone: " + String.format("%.2f", distance));
            if (distance > 20) {
                plugin.debugLog("[TeamListener] " + player.getName() +
                        " is too far from lodestone (" + String.format("%.2f", distance) + " > 20)");
                sendMessage(player, "You are too far from your team's lodestone.", "§c");
                return;
            }
            updateTeamScoreboard(player, team);
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof CompassMeta compassMeta)) {
            plugin.debugLog("[TeamListener] Item meta is not CompassMeta for " + player.getName());
            return;
        }

        if (compassMeta.getLodestone() == null) {
            plugin.debugLog("[TeamListener] No lodestone set for compass used by " + player.getName());
            return;
        }

        TeamsManager.Team team = teamCommands.getTeamForPlayer(player);
        if (team == null) {
            plugin.debugLog("[TeamListener] No team found for " + player.getName());
            sendMessage(player, "You are not in a team.", "§c");
            return;
        }
        plugin.debugLog("[TeamListener] Found team " + team.getName() + " for " + player.getName());

        Location lodestone = teamsManager.getLodestone(team.getName());
        if (lodestone == null) {
            plugin.debugLog("[TeamListener] No lodestone set for team " + team.getName());
            sendMessage(player, "Your team does not have a lodestone set.", "§c");
            return;
        }
        plugin.debugLog("[TeamListener] Lodestone found for team " + team.getName() +
                " at " + formatLocation(lodestone));

        double distance = player.getLocation().distance(lodestone);
        plugin.debugLog("[TeamListener] Distance from " + player.getName() +
                " to lodestone: " + String.format("%.2f", distance));
        if (distance > 20) {
            plugin.debugLog("[TeamListener] " + player.getName() +
                    " is too far from lodestone (" + String.format("%.2f", distance) + " > 20)");
            sendMessage(player, "You are too far from your team's lodestone.", "§c");
            return;
        }

        updateTeamScoreboard(player, team);
    }

    private void updateTeamScoreboard(Player player, TeamsManager.Team team) {
        Scoreboard scoreboard = player.getScoreboard();
        Team scoreboardTeam = scoreboard.getTeam(team.getName());
        if (scoreboardTeam == null) {
            scoreboardTeam = scoreboard.registerNewTeam(team.getName());
            if (isPre113()) {
                ChatColor chatColor = parseChatColor(team.getColor());
                if (chatColor != null) {
                    scoreboardTeam.setColor(chatColor);
                    plugin.debugLog("[TeamListener] Set scoreboard team color to " + chatColor + " for team " + team.getName() + " (1.12)");
                }
                scoreboardTeam.setDisplayName(team.getName());
            } else {
                NamedTextColor namedColor = team.getNamedTextColor();
                if (namedColor != null) {
                    scoreboardTeam.color(namedColor);
                    plugin.debugLog("[TeamListener] Set scoreboard team color to " + namedColor + " for team " + team.getName() + " (1.13+)");
                }
                scoreboardTeam.displayName(Component.text(team.getName()));
            }
            plugin.debugLog("[TeamListener] Created new scoreboard team " + team.getName());
        } else {
            plugin.debugLog("[TeamListener] Found existing scoreboard team " + team.getName());
        }

        for (UUID memberUUID : team.getMembers()) {
            Player member = Bukkit.getPlayer(memberUUID);
            if (member != null && member.isOnline()) {
                scoreboardTeam.addEntry(member.getName());
                plugin.debugLog("[TeamListener] Added " + member.getName() +
                        " to scoreboard team " + team.getName());
            } else {
                plugin.debugLog("[TeamListener] Skipped adding offline/invalid member UUID " +
                        memberUUID + " to team " + team.getName());
            }
        }

        for (UUID memberUUID : team.getMembers()) {
            Player member = Bukkit.getPlayer(memberUUID);
            if (member != null && member.isOnline()) {
                member.setScoreboard(scoreboard);
                plugin.debugLog("[TeamListener] Set scoreboard for " + member.getName() +
                        " to team " + team.getName());
            } else {
                plugin.debugLog("[TeamListener] Skipped setting scoreboard for offline/invalid member UUID " +
                        memberUUID);
            }
        }

        sendMessage(player, "Your nametag has been colored for your team's color", "§a");
        plugin.debugLog("[TeamListener] Sent nametag color confirmation to " + player.getName());
    }

    /**
     * Helper method to format a Block location for debug logging.
     */
    private String formatBlockLocation(Block block) {
        return String.format("(world=%s, x=%d, y=%d, z=%d)",
                block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
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