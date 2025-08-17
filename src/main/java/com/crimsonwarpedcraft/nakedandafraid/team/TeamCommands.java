package com.crimsonwarpedcraft.nakedandafraid.team;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.*;

public class TeamCommands {

    private final TeamsManager teamsManager;
    private final JavaPlugin plugin;

    private static final String TEAM_BLOCK_SELECTOR_NAME = "Team Block Selector";

    private final Set<UUID> usedSelectorPlayers = new HashSet<>();

    public TeamCommands(TeamsManager teamsManager, JavaPlugin plugin) {
        this.teamsManager = teamsManager;
        this.plugin = plugin;
    }

    public boolean handleTeamCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /nf team <create|list|remove|block|setblock> ...").color(NamedTextColor.RED));
            return true;
        }

        String sub = args[1].toLowerCase();

        if (teamsManager.teamExists(sub)) {
            if (args.length >= 3) {
                if (args[2].equalsIgnoreCase("block")) {
                    return handleTeamBlockSelector(sender, args);
                }
                if (args[2].equalsIgnoreCase("setblock")) {
                    return handleTeamSetBlock(sender, args);
                }
            }
            sender.sendMessage(Component.text("Usage: /nf team <team-name> <block|setblock> ...").color(NamedTextColor.RED));
            return true;
        }

        switch (sub) {
            case "create":
                return handleTeamCreate(sender, args);
            case "list":
                return handleTeamList(sender);
            case "remove":
                return handleTeamRemove(sender, args);
            case "block":
                if (args.length >= 4 && teamsManager.teamExists(args[2].toLowerCase()) && args[3].equalsIgnoreCase("selector")) {
                    return handleTeamBlockSelector(sender, args);
                }
                sender.sendMessage(Component.text("Usage: /nf team <team-name> block selector <player>").color(NamedTextColor.RED));
                return true;
            case "setblock":
                if (args.length == 6 && teamsManager.teamExists(args[2].toLowerCase())) {
                    return handleTeamSetBlock(sender, args);
                }
                sender.sendMessage(Component.text("Usage: /nf team <team-name> setblock <x> <y> <z>").color(NamedTextColor.RED));
                return true;
        }
        sender.sendMessage(Component.text("Unknown team subcommand.").color(NamedTextColor.RED));
        return true;
    }

    public boolean handleUserCommand(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(Component.text("Usage: /nf user <player> team <add|remove|list> [team]").color(NamedTextColor.RED));
            return true;
        }

        String playerName = args[1];
        OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(playerName);
        if (target == null) target = Bukkit.getOfflinePlayer(playerName);

        String userSub = args[3].toLowerCase();

        switch (userSub) {
            case "add":
                if (args.length < 5) {
                    sender.sendMessage(Component.text("Usage: /nf user <player> team add <team>").color(NamedTextColor.RED));
                    return true;
                }
                return handleUserTeamAdd(sender, target, args[4]);
            case "remove":
                if (args.length < 5) {
                    sender.sendMessage(Component.text("Usage: /nf user <player> team remove <team>").color(NamedTextColor.RED));
                    return true;
                }
                return handleUserTeamRemove(sender, target, args[4]);
            case "list":
                return handleUserTeamList(sender, target);
            default:
                sender.sendMessage(Component.text("Unknown user team subcommand.").color(NamedTextColor.RED));
                return true;
        }
    }

    private boolean handleTeamCreate(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /nf team create <team>").color(NamedTextColor.RED));
            return true;
        }
        String teamName = args[2].toLowerCase();

        NamedTextColor color = pickColorForTeam();

        if (teamsManager.teamExists(teamName)) {
            sender.sendMessage(Component.text("Team already exists.").color(NamedTextColor.RED));
            return true;
        }
        if (!teamsManager.createTeam(teamName, color)) {
            sender.sendMessage(Component.text("Max teams reached!").color(NamedTextColor.RED));
            return true;
        }

        sender.sendMessage(Component.text("Team '" + teamName + "' created with color " + getColorName(color)).color(color));
        return true;
    }

    private NamedTextColor pickColorForTeam() {
        List<NamedTextColor> colors = List.of(
                NamedTextColor.RED, NamedTextColor.BLUE, NamedTextColor.GREEN,
                NamedTextColor.YELLOW, NamedTextColor.AQUA, NamedTextColor.DARK_PURPLE,
                NamedTextColor.GOLD, NamedTextColor.LIGHT_PURPLE
        );
        Set<NamedTextColor> usedColors = new HashSet<>();
        for (var team : teamsManager.getTeams()) {
            usedColors.add(team.getColor());
        }
        for (NamedTextColor color : colors) {
            if (!usedColors.contains(color)) return color;
        }
        return NamedTextColor.WHITE;
    }

    private boolean handleTeamList(CommandSender sender) {
        Collection<TeamsManager.Team> teams = teamsManager.getTeams();
        if (teams.isEmpty()) {
            sender.sendMessage(Component.text("No teams available.").color(NamedTextColor.YELLOW));
            return true;
        }
        sender.sendMessage(Component.text("Teams:").color(NamedTextColor.GOLD));
        for (TeamsManager.Team team : teams) {
            sender.sendMessage(Component.text("- " + team.getName()).color(team.getColor()));
        }
        return true;
    }

    private boolean handleTeamRemove(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /nf team remove <team>").color(NamedTextColor.RED));
            return true;
        }
        String teamName = args[2].toLowerCase();
        if (!teamsManager.teamExists(teamName)) {
            sender.sendMessage(Component.text("Team does not exist.").color(NamedTextColor.RED));
            return true;
        }
        teamsManager.removeTeam(teamName);
        sender.sendMessage(Component.text("Team '" + teamName + "' removed.").color(NamedTextColor.GREEN));
        return true;
    }

    private boolean handleTeamBlockSelector(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("Only players can use this command.").color(NamedTextColor.RED));
            return true;
        }
        if (args.length < 5) {
            sender.sendMessage(Component.text("Usage: /nf team <team-name> block selector <player>").color(NamedTextColor.RED));
            return true;
        }
        String teamName = args[1].toLowerCase();
        if (!teamsManager.teamExists(teamName)) {
            sender.sendMessage(Component.text("Team '" + teamName + "' does not exist.").color(NamedTextColor.RED));
            return true;
        }
        if (!args[2].equalsIgnoreCase("block") || !args[3].equalsIgnoreCase("selector")) {
            sender.sendMessage(Component.text("Usage: /nf team <team-name> block selector <player>").color(NamedTextColor.RED));
            return true;
        }
        Player target = Bukkit.getPlayer(args[4]);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found or offline.").color(NamedTextColor.RED));
            return true;
        }
        ItemStack axe = createTeamBlockSelector(teamName);
        target.getInventory().addItem(axe);
        sender.sendMessage(Component.text("Given team block selector for team '" + teamName + "' to " + target.getName()).color(NamedTextColor.GREEN));
        return true;
    }

    private ItemStack createTeamBlockSelector(String teamName) {
        ItemStack axe = new ItemStack(Material.IRON_AXE);
        ItemMeta meta = axe.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(TEAM_BLOCK_SELECTOR_NAME).color(NamedTextColor.GOLD));
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "teamSelector"), PersistentDataType.STRING, teamName);
            axe.setItemMeta(meta);
        }
        return axe;
    }

    private boolean handleTeamSetBlock(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("Only players can use this command.").color(NamedTextColor.RED));
            return true;
        }
        if (args.length != 6) {
            sender.sendMessage(Component.text("Usage: /nf team <team-name> setblock <x> <y> <z>").color(NamedTextColor.RED));
            return true;
        }
        String teamName = args[1].toLowerCase();
        if (!teamsManager.teamExists(teamName)) {
            sender.sendMessage(Component.text("Team '" + teamName + "' does not exist.").color(NamedTextColor.RED));
            return true;
        }
        Player player = (Player) sender;
        int x, y, z;
        try {
            x = Integer.parseInt(args[3]);
            y = Integer.parseInt(args[4]);
            z = Integer.parseInt(args[5]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Coordinates must be numbers.").color(NamedTextColor.RED));
            return true;
        }
        World world = player.getWorld();
        Location loc = new Location(world, x, y, z);
        Block block = world.getBlockAt(loc);
        if (block.getType() != teamsManager.getTeamBlockMaterial()) {
            sender.sendMessage(Component.text("Block at location is not the configured team block (" + teamsManager.getTeamBlockMaterial() + ").").color(NamedTextColor.RED));
            return true;
        }
        teamsManager.setLodestone(teamName, loc);
        player.sendMessage(Component.text("Lodestone set for team '" + teamName + "' at " + x + " " + y + " " + z).color(NamedTextColor.GREEN));
        return true;
    }

    public TeamsManager.Team getTeamForPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        for (TeamsManager.Team team : teamsManager.getTeams()) {
            if (team.getMembers().contains(uuid)) return team;
        }
        return null;
    }

    private boolean handleUserTeamAdd(CommandSender sender, OfflinePlayer target, String teamName) {
        if (!teamsManager.teamExists(teamName)) {
            sender.sendMessage(Component.text("Team does not exist.").color(NamedTextColor.RED));
            return true;
        }
        if (teamsManager.addMember(teamName, target.getUniqueId())) {
            sender.sendMessage(Component.text("Added " + target.getName() + " to team " + teamName).color(NamedTextColor.GREEN));
            if (target.isOnline()) {
                ((Player) target).sendMessage(Component.text("You have been added to team " + teamName).color(NamedTextColor.GREEN));
            }
        } else {
            sender.sendMessage(Component.text(target.getName() + " is already in the team or error occurred.").color(NamedTextColor.RED));
        }
        return true;
    }

    private boolean handleUserTeamRemove(CommandSender sender, OfflinePlayer target, String teamName) {
        if (!teamsManager.teamExists(teamName)) {
            sender.sendMessage(Component.text("Team does not exist.").color(NamedTextColor.RED));
            return true;
        }
        if (teamsManager.removeMember(teamName, target.getUniqueId())) {
            sender.sendMessage(Component.text("Removed " + target.getName() + " from team " + teamName).color(NamedTextColor.GREEN));
            if (target.isOnline()) {
                ((Player) target).sendMessage(Component.text("You have been removed from team " + teamName).color(NamedTextColor.RED));
            }
        } else {
            sender.sendMessage(Component.text(target.getName() + " is not in the team or error occurred.").color(NamedTextColor.RED));
        }
        return true;
    }

    private boolean handleUserTeamList(CommandSender sender, OfflinePlayer target) {
        UUID uuid = target.getUniqueId();
        List<String> playerTeams = new ArrayList<>();
        for (TeamsManager.Team team : teamsManager.getTeams()) {
            if (team.getMembers().contains(uuid)) {
                playerTeams.add(team.getName());
            }
        }

        if (playerTeams.isEmpty()) {
            sender.sendMessage(Component.text(target.getName() + " is not in any team.").color(NamedTextColor.YELLOW));
            return true;
        }

        sender.sendMessage(Component.text(target.getName() + "'s teams:").color(NamedTextColor.GOLD));
        for (String team : playerTeams) {
            sender.sendMessage(Component.text("- " + team).color(NamedTextColor.WHITE));
        }
        return true;
    }

    public void onTeamBlockSelectorUse(Player player, Block block) {
        if (block.getType() != teamsManager.getTeamBlockMaterial()) {
            player.sendMessage(Component.text("This block is not the configured team block (" + teamsManager.getTeamBlockMaterial() + ").").color(NamedTextColor.RED));
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        String teamName = meta.getPersistentDataContainer().get(new NamespacedKey(plugin, "teamSelector"), PersistentDataType.STRING);
        if (teamName == null) {
            player.sendMessage(Component.text("This selector is invalid (missing team info).").color(NamedTextColor.RED));
            return;
        }

        if (!teamsManager.teamExists(teamName)) {
            player.sendMessage(Component.text("Team '" + teamName + "' no longer exists.").color(NamedTextColor.RED));
            return;
        }

        teamsManager.setLodestone(teamName, block.getLocation());
        player.sendMessage(Component.text("Team lodestone for '" + teamName + "' set at " +
                block.getX() + ", " + block.getY() + ", " + block.getZ()).color(NamedTextColor.GREEN));

        updatePlayerNametagColor(player, teamName);

        // Optionally remove the selector after use by uncommenting the line below
        // player.getInventory().remove(item);
    }

    private void updatePlayerNametagColor(Player player, String teamName) {
        TeamsManager.Team team = teamsManager.getTeam(teamName);
        if (team == null) return;

        Scoreboard scoreboard = player.getScoreboard();
        Team scoreboardTeam = scoreboard.getTeam(teamName);

        if (scoreboardTeam == null) {
            scoreboardTeam = scoreboard.registerNewTeam(teamName);
            scoreboardTeam.color(team.getColor());
            scoreboardTeam.setDisplayName(teamName);
        }

        for (UUID memberUUID : team.getMembers()) {
            Player member = Bukkit.getPlayer(memberUUID);
            if (member != null && member.isOnline()) {
                scoreboardTeam.addEntry(member.getName());
            }
        }

        player.setScoreboard(scoreboard);
        player.sendMessage(Component.text("Your nametag color is now set to team '" + teamName + "'.").color(NamedTextColor.GREEN));
    }

    private String getColorName(NamedTextColor color) {
        if (color.equals(NamedTextColor.RED)) return "RED";
        if (color.equals(NamedTextColor.BLUE)) return "BLUE";
        if (color.equals(NamedTextColor.GREEN)) return "GREEN";
        if (color.equals(NamedTextColor.YELLOW)) return "YELLOW";
        if (color.equals(NamedTextColor.AQUA)) return "AQUA";
        if (color.equals(NamedTextColor.DARK_PURPLE)) return "DARK_PURPLE";
        if (color.equals(NamedTextColor.GOLD)) return "GOLD";
        if (color.equals(NamedTextColor.LIGHT_PURPLE)) return "LIGHT_PURPLE";
        if (color.equals(NamedTextColor.WHITE)) return "WHITE";
        return "UNKNOWN";
    }
}
