package com.crimsonwarpedcraft.nakedandafraid.listeners;

import com.crimsonwarpedcraft.nakedandafraid.team.TeamCommands;
import com.crimsonwarpedcraft.nakedandafraid.team.TeamsManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
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

    private final TeamsManager teamsManager;
    private final TeamCommands teamCommands;

    private static final String TEAM_BLOCK_SELECTOR_NAME = "Team Block Selector";

    public TeamListener(TeamsManager teamsManager, TeamCommands teamCommands) {
        this.teamsManager = teamsManager;
        this.teamCommands = teamCommands;
    }

    @EventHandler
    public void onPlayerUseSelector(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return; // Right click lodestone, not left click

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item == null || !item.hasItemMeta()) return;
        if (item.getType() != Material.IRON_AXE) return;

        // Check item name and team selector metadata
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        Component displayName = meta.displayName();
        if (displayName == null) return;
        if (!displayName.equals(Component.text(TEAM_BLOCK_SELECTOR_NAME).color(NamedTextColor.GOLD))) return;

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) return;

        event.setCancelled(true);

        teamCommands.onTeamBlockSelectorUse(player, clickedBlock);
    }

    @EventHandler
    public void onPlayerUseLocatorCompass(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR && event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item == null || !item.hasItemMeta()) return;
        if (item.getType() != Material.COMPASS) return;

        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof CompassMeta)) return;

        CompassMeta compassMeta = (CompassMeta) meta;
        if (compassMeta.getLodestone() == null) {
            // Not a locator compass, ignore
            return;
        }

        TeamsManager.Team team = teamCommands.getTeamForPlayer(player);
        if (team == null) return;

        Location lodestone = teamsManager.getLodestone(team.getName());
        if (lodestone == null) {
            player.sendMessage(Component.text("Your team does not have a lodestone set.").color(NamedTextColor.RED));
            return;
        }

        double distance = player.getLocation().distance(lodestone);
        if (distance > 20) { // Within 20 blocks to activate coloring
            player.sendMessage(Component.text("You are too far from your team's lodestone.").color(NamedTextColor.RED));
            return;
        }

        // Apply nametag coloring via scoreboard team
        Scoreboard scoreboard = player.getScoreboard();
        Team scoreboardTeam = scoreboard.getTeam(team.getName());

        if (scoreboardTeam == null) {
            scoreboardTeam = scoreboard.registerNewTeam(team.getName());
            // Map your NamedTextColor to Bukkit ChatColor
            scoreboardTeam.color(team.getColor());
            scoreboardTeam.setDisplayName(team.getName());
        }

        // Add all online team members to this scoreboard team for the player
        for (UUID memberUUID : team.getMembers()) {
            Player member = Bukkit.getPlayer(memberUUID);
            if (member != null && member.isOnline()) {
                scoreboardTeam.addEntry(member.getName());
            }
        }

        player.setScoreboard(scoreboard);
        player.sendMessage(Component.text("Nametags colored for your team near lodestone!").color(NamedTextColor.GREEN));
    }

    private ChatColor mapNamedTextColorToChatColor(NamedTextColor color) {
        // Map common NamedTextColor to Bukkit ChatColor
        if (color.equals(NamedTextColor.RED)) return ChatColor.RED;
        if (color.equals(NamedTextColor.BLUE)) return ChatColor.BLUE;
        if (color.equals(NamedTextColor.GREEN)) return ChatColor.GREEN;
        if (color.equals(NamedTextColor.YELLOW)) return ChatColor.YELLOW;
        if (color.equals(NamedTextColor.AQUA)) return ChatColor.AQUA;
        if (color.equals(NamedTextColor.DARK_PURPLE)) return ChatColor.DARK_PURPLE;
        if (color.equals(NamedTextColor.GOLD)) return ChatColor.GOLD;
        if (color.equals(NamedTextColor.LIGHT_PURPLE)) return ChatColor.LIGHT_PURPLE;
        return ChatColor.WHITE;
    }
}
