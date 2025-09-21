package com.crimsonwarpedcraft.nakedandafraid.v1_8.listeners;

import com.crimsonwarpedcraft.nakedandafraid.v1_8.NakedAndAfraid;
import com.crimsonwarpedcraft.nakedandafraid.v1_8.team.TeamCommands;
import com.crimsonwarpedcraft.nakedandafraid.v1_8.team.TeamsManager;
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
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

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

    private boolean isPre113() {
        return !Bukkit.getBukkitVersion().matches(".*1\\.(1[3-9]|2[0-1]).*");
    }

    private boolean isPre116() {
        return !Bukkit.getBukkitVersion().matches(".*1\\.(1[6-9]|2[0-1]).*");
    }

    private void sendMessage(Player player, String message, String legacyColor) {
        player.sendMessage(legacyColor + message);
        plugin.debugLog("[TeamListener] Sent message to " + player.getName() + ": " + message);
    }

    private ChatColor parseChatColor(String colorName) {
        if (colorName == null) return null;
        colorName = colorName.toUpperCase();
        switch (colorName) {
            case "RED": return ChatColor.RED;
            case "GOLD": return ChatColor.GOLD;
            case "YELLOW": return ChatColor.YELLOW;
            case "GREEN": return ChatColor.GREEN;
            case "AQUA": return ChatColor.AQUA;
            case "BLUE": return ChatColor.BLUE;
            case "DARK_PURPLE": return ChatColor.DARK_PURPLE;
            case "LIGHT_PURPLE": return ChatColor.LIGHT_PURPLE;
            case "WHITE": return ChatColor.WHITE;
            default: return null;
        }
    }

    @EventHandler
    public void onPlayerUseSelector(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || !item.hasItemMeta()) return;
        if (item.getType() != Material.IRON_AXE) return;
        if (item.getItemMeta() == null) return;

        String displayName = item.getItemMeta().getDisplayName();
        boolean isValidSelector = displayName != null && displayName.equals("§6" + TEAM_BLOCK_SELECTOR_NAME);
        if (!isValidSelector) return;

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) return;

        event.setCancelled(true);
        teamCommands.onTeamBlockSelectorUse(player, clickedBlock);
    }

    @EventHandler
    public void onPlayerUseLocatorCompass(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR &&
                event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || !item.hasItemMeta()) return;
        if (item.getType() != Material.COMPASS) return;

        if (isPre116()) {
            TeamsManager.Team team = teamCommands.getTeamForPlayer(player);
            if (team == null) {
                sendMessage(player, "You are not in a team.", "§c");
                return;
            }
            Location lodestone = teamsManager.getLodestone(team.getName());
            if (lodestone == null) {
                sendMessage(player, "Your team does not have a lodestone set.", "§c");
                return;
            }
            double distance = player.getLocation().distance(lodestone);
            if (distance > 20) {
                sendMessage(player, "You are too far from your team's lodestone.", "§c");
                return;
            }
            updateTeamScoreboard(player, team);
        }
    }

    private void updateTeamScoreboard(Player player, TeamsManager.Team team) {
        Scoreboard scoreboard = player.getScoreboard();
        Team scoreboardTeam = scoreboard.getTeam(team.getName());
        if (scoreboardTeam == null) {
            scoreboardTeam = scoreboard.registerNewTeam(team.getName());
            ChatColor chatColor = parseChatColor(team.getColor());
            if (chatColor != null) scoreboardTeam.setColor(chatColor);
            scoreboardTeam.setDisplayName(team.getName());
        }

        for (java.util.UUID memberUUID : team.getMembers()) {
            Player member = Bukkit.getPlayer(memberUUID);
            if (member != null && member.isOnline()) scoreboardTeam.addEntry(member.getName());
        }

        for (java.util.UUID memberUUID : team.getMembers()) {
            Player member = Bukkit.getPlayer(memberUUID);
            if (member != null && member.isOnline()) member.setScoreboard(scoreboard);
        }

        sendMessage(player, "Your nametag has been colored for your team's color", "§a");
    }

    private String formatBlockLocation(Block block) {
        return String.format("(world=%s, x=%d, y=%d, z=%d)",
                block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    private String formatLocation(Location location) {
        return String.format("(world=%s, x=%.2f, y=%.2f, z=%.2f)",
                location.getWorld() != null ? location.getWorld().getName() : "null",
                location.getX(), location.getY(), location.getZ());
    }
}
