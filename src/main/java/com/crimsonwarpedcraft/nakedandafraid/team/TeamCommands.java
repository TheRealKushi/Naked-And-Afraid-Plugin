package com.crimsonwarpedcraft.nakedandafraid.team;

import com.crimsonwarpedcraft.nakedandafraid.NakedAndAfraid;
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
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Initialized TeamCommands");
    }

    public boolean handleTeamCommand(CommandSender sender, String[] args) {
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Processing team command for " + sender.getName() + ": " + String.join(" ", args));
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /nf team <create|list|remove|block|setblock> ...").color(NamedTextColor.RED));
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Invalid arguments for " + sender.getName() + ", expected at least 2");
            return true;
        }

        String sub = args[1].toLowerCase();

        if (teamsManager.teamExists(sub)) {
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Subcommand is a team name: " + sub);
            if (args.length >= 3) {
                if (args[2].equalsIgnoreCase("block")) {
                    return handleTeamBlockSelector(sender, args);
                }
                if (args[2].equalsIgnoreCase("setblock")) {
                    return handleTeamSetBlock(sender, args);
                }
            }
            sender.sendMessage(Component.text("Usage: /nf team <team-name> <block|setblock> ...").color(NamedTextColor.RED));
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Invalid subcommand for team '" + sub + "', expected block or setblock");
            return true;
        }

        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Subcommand: " + sub);
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
                ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Invalid arguments for block selector");
                return true;
            case "setblock":
                if (args.length == 6 && teamsManager.teamExists(args[2].toLowerCase())) {
                    return handleTeamSetBlock(sender, args);
                }
                sender.sendMessage(Component.text("Usage: /nf team <team-name> setblock <x> <y> <z>").color(NamedTextColor.RED));
                ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Invalid arguments for setblock");
                return true;
            case "meta":
                if (args.length < 4) {
                    sender.sendMessage(Component.text("Usage: /nf team meta <team-name> color <get|set> [color]").color(NamedTextColor.RED));
                    ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Invalid arguments for meta, expected at least 4");
                    return true;
                }
                if (args[3].equalsIgnoreCase("color")) {
                    return handleTeamMetaColor(sender, args);
                }
                sender.sendMessage(Component.text("Unknown meta subcommand.").color(NamedTextColor.RED));
                ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Unknown meta subcommand: " + args[3]);
                return true;
        }
        sender.sendMessage(Component.text("Unknown team subcommand.").color(NamedTextColor.RED));
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Unknown subcommand: " + sub);
        return true;
    }

    public boolean handleUserCommand(CommandSender sender, String[] args) {
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Processing user command for " + sender.getName() + ": " + String.join(" ", args));
        if (args.length < 4) {
            sender.sendMessage(Component.text("Usage: /nf user <player> team <add|remove|list> [team]").color(NamedTextColor.RED));
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Invalid arguments for user command, expected at least 4");
            return true;
        }

        String playerName = args[1];
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Target player: " + playerName);
        OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(playerName);
        if (target == null) target = Bukkit.getOfflinePlayer(playerName);

        String userSub = args[3].toLowerCase();
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] User subcommand: " + userSub);

        return switch (userSub) {
            case "add" -> {
                if (args.length < 5) {
                    sender.sendMessage(Component.text("Usage: /nf user <player> team add <team>").color(NamedTextColor.RED));
                    ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Invalid arguments for user add, expected 5");
                    yield true;
                }
                yield handleUserTeamAdd(sender, target, args[4]);
            }
            case "remove" -> {
                if (args.length < 5) {
                    sender.sendMessage(Component.text("Usage: /nf user <player> team remove <team>").color(NamedTextColor.RED));
                    ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Invalid arguments for user remove, expected 5");
                    yield true;
                }
                yield handleUserTeamRemove(sender, target, args[4]);
            }
            case "list" -> handleUserTeamList(sender, target);
            default -> {
                sender.sendMessage(Component.text("Unknown user team subcommand.").color(NamedTextColor.RED));
                ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Unknown user subcommand: " + userSub);
                yield true;
            }
        };
    }

    private boolean handleTeamCreate(CommandSender sender, String[] args) {
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Handling team create command for " + sender.getName() + ": " + String.join(" ", args));
        if (args.length < 4) {
            sender.sendMessage(Component.text("Usage: /nf team create <team-name> <team-color>").color(NamedTextColor.RED));
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Invalid arguments for create, expected at least 4");
            return true;
        }

        String teamName = args[2].toLowerCase();
        String colorName = args[3].toUpperCase();
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Team name: " + teamName + ", color: " + colorName);

        NamedTextColor color = parseColor(colorName);
        if (color == null) {
            sender.sendMessage(Component.text("Invalid color. Valid colors: RED, BLUE, GREEN, YELLOW, AQUA, DARK_PURPLE, GOLD, LIGHT_PURPLE, WHITE").color(NamedTextColor.RED));
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Invalid color: " + colorName);
            return true;
        }

        if (teamsManager.teamExists(teamName)) {
            sender.sendMessage(Component.text("Team already exists.").color(NamedTextColor.RED));
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Team '" + teamName + "' already exists");
            return true;
        }

        if (!teamsManager.createTeam(teamName, color)) {
            sender.sendMessage(Component.text("Max teams reached!").color(NamedTextColor.RED));
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Max teams reached for '" + teamName + "'");
            return true;
        }

        sender.sendMessage(Component.text("Team '" + teamName + "' created with color " + getColorName(color)).color(color));
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Created team '" + teamName + "' with color " + getColorName(color));
        return true;
    }

    private NamedTextColor parseColor(String colorName) {
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Parsing color: " + colorName);
        return switch (colorName) {
            case "RED" -> NamedTextColor.RED;
            case "GOLD" -> NamedTextColor.GOLD;
            case "YELLOW" -> NamedTextColor.YELLOW;
            case "GREEN" -> NamedTextColor.GREEN;
            case "AQUA" -> NamedTextColor.AQUA;
            case "BLUE" -> NamedTextColor.BLUE;
            case "DARK_PURPLE" -> NamedTextColor.DARK_PURPLE;
            case "LIGHT_PURPLE" -> NamedTextColor.LIGHT_PURPLE;
            case "WHITE" -> NamedTextColor.WHITE;
            default -> null;
        };
    }

    private boolean handleTeamList(CommandSender sender) {
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Handling team list command for " + sender.getName());
        Collection<TeamsManager.Team> teams = teamsManager.getTeams();
        if (teams.isEmpty()) {
            sender.sendMessage(Component.text("No teams available.").color(NamedTextColor.YELLOW));
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] No teams available for " + sender.getName());
            return true;
        }
        sender.sendMessage(Component.text("Teams:").color(NamedTextColor.GOLD));
        for (TeamsManager.Team team : teams) {
            sender.sendMessage(Component.text("- " + team.getName()).color(team.getColor()));
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Listed team '" + team.getName() + "' with color " + getColorName(team.getColor()));
        }
        return true;
    }

    private boolean handleTeamRemove(CommandSender sender, String[] args) {
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Handling team remove command for " + sender.getName() + ": " + String.join(" ", args));
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /nf team remove <team>").color(NamedTextColor.RED));
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Invalid arguments for remove, expected 3");
            return true;
        }
        String teamName = args[2].toLowerCase();
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Team name: " + teamName);
        if (!teamsManager.teamExists(teamName)) {
            sender.sendMessage(Component.text("Team does not exist.").color(NamedTextColor.RED));
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Team '" + teamName + "' does not exist");
            return true;
        }
        teamsManager.removeTeam(teamName);
        sender.sendMessage(Component.text("Team '" + teamName + "' removed.").color(NamedTextColor.GREEN));
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Removed team '" + teamName + "' for " + sender.getName());
        return true;
    }

    private boolean handleTeamBlockSelector(CommandSender sender, String[] args) {
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Handling team block selector command for " + sender.getName() + ": " + String.join(" ", args));
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("Only players can use this command.").color(NamedTextColor.RED));
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Non-player sender attempted block selector command");
            return true;
        }
        if (args.length < 5) {
            sender.sendMessage(Component.text("Usage: /nf team <team-name> block selector <player>").color(NamedTextColor.RED));
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Invalid arguments for block selector, expected 5");
            return true;
        }
        String teamName = args[1].toLowerCase();
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Team name: " + teamName);
        if (!teamsManager.teamExists(teamName)) {
            sender.sendMessage(Component.text("Team '" + teamName + "' does not exist.").color(NamedTextColor.RED));
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Team '" + teamName + "' does not exist");
            return true;
        }
        if (!args[2].equalsIgnoreCase("block") || !args[3].equalsIgnoreCase("selector")) {
            sender.sendMessage(Component.text("Usage: /nf team <team-name> block selector <player>").color(NamedTextColor.RED));
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Invalid block selector syntax");
            return true;
        }
        Player target = Bukkit.getPlayer(args[4]);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found or offline.").color(NamedTextColor.RED));
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Player '" + args[4] + "' not found or offline");
            return true;
        }
        ItemStack axe = createTeamBlockSelector(teamName);
        target.getInventory().addItem(axe);
        sender.sendMessage(Component.text("Given team block selector for team '" + teamName + "' to " + target.getName()).color(NamedTextColor.GREEN));
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Gave block selector for team '" + teamName + "' to " + target.getName());
        return true;
    }

    private ItemStack createTeamBlockSelector(String teamName) {
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Creating team block selector for team '" + teamName + "'");
        ItemStack axe = new ItemStack(Material.IRON_AXE);
        ItemMeta meta = axe.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(TEAM_BLOCK_SELECTOR_NAME).color(NamedTextColor.GOLD));
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "teamSelector"), PersistentDataType.STRING, teamName);
            axe.setItemMeta(meta);
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Set item meta for selector: team=" + teamName);
        }
        return axe;
    }

    private boolean handleTeamSetBlock(CommandSender sender, String[] args) {
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Handling team setblock command for " + sender.getName() + ": " + String.join(" ", args));
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.").color(NamedTextColor.RED));
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Non-player sender attempted setblock command");
            return true;
        }
        if (args.length != 6) {
            sender.sendMessage(Component.text("Usage: /nf team <team-name> setblock <x> <y> <z>").color(NamedTextColor.RED));
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Invalid arguments for setblock, expected 6");
            return true;
        }
        String teamName = args[1].toLowerCase();
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Team name: " + teamName);
        if (!teamsManager.teamExists(teamName)) {
            sender.sendMessage(Component.text("Team '" + teamName + "' does not exist.").color(NamedTextColor.RED));
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Team '" + teamName + "' does not exist");
            return true;
        }
        int x, y, z;
        try {
            x = Integer.parseInt(args[3]);
            y = Integer.parseInt(args[4]);
            z = Integer.parseInt(args[5]);
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Parsed coordinates: x=" + x + ", y=" + y + ", z=" + z);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Coordinates must be numbers.").color(NamedTextColor.RED));
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Invalid coordinates: " + String.join(" ", Arrays.copyOfRange(args, 3, args.length)));
            return true;
        }
        World world = player.getWorld();
        Location loc = new Location(world, x, y, z);
        Block block = world.getBlockAt(loc);
        if (block.getType() != teamsManager.getTeamBlockMaterial()) {
            sender.sendMessage(Component.text("Block at location is not the configured team block (" + teamsManager.getTeamBlockMaterial() + ").").color(NamedTextColor.RED));
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Block at " + formatLocation(loc) + " is " + block.getType() + ", expected " + teamsManager.getTeamBlockMaterial());
            return true;
        }
        teamsManager.setLodestone(teamName, loc);
        player.sendMessage(Component.text("Lodestone set for team '" + teamName + "' at " + x + " " + y + " " + z).color(NamedTextColor.GREEN));
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Set lodestone for team '" + teamName + "' at " + formatLocation(loc));
        return true;
    }

    public TeamsManager.Team getTeamForPlayer(Player player) {
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Getting team for player " + player.getName());
        UUID uuid = player.getUniqueId();
        for (TeamsManager.Team team : teamsManager.getTeams()) {
            if (team.getMembers().contains(uuid)) {
                ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Found team '" + team.getName() + "' for player " + player.getName());
                return team;
            }
        }
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] No team found for player " + player.getName());
        return null;
    }

    private boolean handleUserTeamAdd(CommandSender sender, OfflinePlayer target, String teamName) {
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Handling user team add for " + sender.getName() + ": player=" + target.getName() + ", team=" + teamName);
        if (!teamsManager.teamExists(teamName)) {
            sender.sendMessage(Component.text("Team does not exist.").color(NamedTextColor.RED));
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Team '" + teamName + "' does not exist");
            return true;
        }
        if (teamsManager.addMember(teamName, target.getUniqueId())) {
            sender.sendMessage(Component.text("Added " + target.getName() + " to team " + teamName).color(NamedTextColor.GREEN));
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Added " + target.getName() + " to team '" + teamName + "'");
            if (target.isOnline()) {
                ((Player) target).sendMessage(Component.text("You have been added to team " + teamName).color(NamedTextColor.GREEN));
                ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Notified " + target.getName() + " of team addition");
            }
        } else {
            sender.sendMessage(Component.text(target.getName() + " is already in the team or error occurred.").color(NamedTextColor.RED));
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Failed to add " + target.getName() + " to team '" + teamName + "', already in team or error");
        }
        return true;
    }

    private boolean handleUserTeamRemove(CommandSender sender, OfflinePlayer target, String teamName) {
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Handling user team remove for " + sender.getName() + ": player=" + target.getName() + ", team=" + teamName);
        if (!teamsManager.teamExists(teamName)) {
            sender.sendMessage(Component.text("Team does not exist.").color(NamedTextColor.RED));
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Team '" + teamName + "' does not exist");
            return true;
        }
        if (teamsManager.removeMember(teamName, target.getUniqueId())) {
            sender.sendMessage(Component.text("Removed " + target.getName() + " from team " + teamName).color(NamedTextColor.GREEN));
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Removed " + target.getName() + " from team '" + teamName + "'");
            if (target.isOnline()) {
                ((Player) target).sendMessage(Component.text("You have been removed from team " + teamName).color(NamedTextColor.RED));
                ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Notified " + target.getName() + " of team removal");
            }
        } else {
            sender.sendMessage(Component.text(target.getName() + " is not in the team or error occurred.").color(NamedTextColor.RED));
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Failed to remove " + target.getName() + " from team '" + teamName + "', not in team or error");
        }
        return true;
    }

    private boolean handleUserTeamList(CommandSender sender, OfflinePlayer target) {
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Handling user team list for " + sender.getName() + ": player=" + target.getName());
        UUID uuid = target.getUniqueId();
        List<String> playerTeams = new ArrayList<>();
        for (TeamsManager.Team team : teamsManager.getTeams()) {
            if (team.getMembers().contains(uuid)) {
                playerTeams.add(team.getName());
                ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Found team '" + team.getName() + "' for " + target.getName());
            }
        }

        if (playerTeams.isEmpty()) {
            sender.sendMessage(Component.text(target.getName() + " is not in any team.").color(NamedTextColor.YELLOW));
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] No teams found for " + target.getName());
            return true;
        }

        sender.sendMessage(Component.text(target.getName() + "'s teams:").color(NamedTextColor.GOLD));
        for (String team : playerTeams) {
            sender.sendMessage(Component.text("- " + team).color(NamedTextColor.WHITE));
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Listed team '" + team + "' for " + target.getName());
        }
        return true;
    }

    public void onTeamBlockSelectorUse(Player player, Block block) {
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Player " + player.getName() + " used team block selector on block at " + formatLocation(block.getLocation()));
        if (block.getType() != teamsManager.getTeamBlockMaterial()) {
            player.sendMessage(Component.text("This block is not the configured team block (" + teamsManager.getTeamBlockMaterial() + ").").color(NamedTextColor.RED));
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Block at " + formatLocation(block.getLocation()) + " is " + block.getType() + ", expected " + teamsManager.getTeamBlockMaterial());
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || !item.hasItemMeta()) {
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Invalid item: null or no meta");
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Item meta is null");
            return;
        }

        String teamName = meta.getPersistentDataContainer().get(new NamespacedKey(plugin, "teamSelector"), PersistentDataType.STRING);
        if (teamName == null) {
            player.sendMessage(Component.text("This selector is invalid (missing team info)").color(NamedTextColor.RED));
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Selector missing team info");
            return;
        }

        if (!teamsManager.teamExists(teamName)) {
            player.sendMessage(Component.text("Team '" + teamName + "' no longer exists").color(NamedTextColor.RED));
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Team '" + teamName + "' no longer exists");
            return;
        }

        teamsManager.setLodestone(teamName, block.getLocation());
        player.sendMessage(Component.text("Team lodestone for '" + teamName + "' set at " +
                block.getX() + ", " + block.getY() + ", " + block.getZ()).color(NamedTextColor.GREEN));
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Set lodestone for team '" + teamName + "' at " + formatLocation(block.getLocation()));

        updatePlayerNametagColor(player, teamName);
    }

    private void updatePlayerNametagColor(Player player, String teamName) {
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Updating nametag color for player " + player.getName() + " in team '" + teamName + "'");
        TeamsManager.Team team = teamsManager.getTeam(teamName);
        if (team == null) {
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Team '" + teamName + "' not found for nametag update");
            return;
        }

        Scoreboard scoreboard = player.getScoreboard();
        Team scoreboardTeam = scoreboard.getTeam(teamName);

        if (scoreboardTeam == null) {
            scoreboardTeam = scoreboard.registerNewTeam(teamName);
            scoreboardTeam.color(team.getColor());
            scoreboardTeam.setDisplayName(teamName);
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Registered new scoreboard team '" + teamName + "' with color " + getColorName(team.getColor()));
        }

        for (UUID memberUUID : team.getMembers()) {
            Player member = Bukkit.getPlayer(memberUUID);
            if (member != null && member.isOnline()) {
                scoreboardTeam.addEntry(member.getName());
                ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Added " + member.getName() + " to scoreboard team '" + teamName + "'");
            }
        }

        player.setScoreboard(scoreboard);
        player.sendMessage(Component.text("Nametag color updated for team '" + teamName + "'").color(NamedTextColor.GREEN));
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Updated nametag color for " + player.getName() + " in team '" + teamName + "'");
    }

    private boolean handleTeamMetaColor(CommandSender sender, String[] args) {
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Handling team meta color command for " + sender.getName() + ": " + String.join(" ", args));
        if (args.length < 5) {
            sender.sendMessage(Component.text("Usage: /nf team meta <team-name> color <get|set> [color]").color(NamedTextColor.RED));
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Invalid arguments for meta color, expected at least 5");
            return true;
        }

        String teamName = args[2].toLowerCase();
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Team name: " + teamName);
        if (!teamsManager.teamExists(teamName)) {
            sender.sendMessage(Component.text("Team '" + teamName + "' does not exist.").color(NamedTextColor.RED));
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Team '" + teamName + "' does not exist");
            return true;
        }

        String action = args[4].toLowerCase();
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Color action: " + action);
        TeamsManager.Team team = teamsManager.getTeam(teamName);

        switch (action) {
            case "get" -> {
                sender.sendMessage(Component.text("Team '" + teamName + "' color: " + getColorName(team.getColor())).color(team.getColor()));
                ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Retrieved color " + getColorName(team.getColor()) + " for team '" + teamName + "'");
                return true;
            }
            case "set" -> {
                if (args.length < 6) {
                    sender.sendMessage(Component.text("Usage: /nf team meta <team-name> color set <color>").color(NamedTextColor.RED));
                    ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Invalid arguments for meta color set, expected 6");
                    return true;
                }
                String newColorName = args[5].toUpperCase();
                ((NakedAndAfraid) plugin).debugLog("[TeamCommands] New color: " + newColorName);
                NamedTextColor newColor = parseColor(newColorName);
                if (newColor == null) {
                    sender.sendMessage(Component.text("Invalid color. Valid colors: RED, BLUE, GREEN, YELLOW, AQUA, DARK_PURPLE, GOLD, LIGHT_PURPLE, WHITE").color(NamedTextColor.RED));
                    ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Invalid color: " + newColorName);
                    return true;
                }
                team.setColor(newColor);
                sender.sendMessage(Component.text("Team '" + teamName + "' color updated to " + newColorName).color(newColor));
                ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Updated color for team '" + teamName + "' to " + newColorName);

                for (UUID memberUUID : team.getMembers()) {
                    Player member = Bukkit.getPlayer(memberUUID);
                    if (member != null && member.isOnline()) {
                        updatePlayerNametagColor(member, teamName);
                        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Updated nametag for " + member.getName() + " in team '" + teamName + "'");
                    }
                }
                return true;
            }
            default -> {
                sender.sendMessage(Component.text("Unknown color action. Use get or set.").color(NamedTextColor.RED));
                ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Unknown color action: " + action);
                return true;
            }
        }
    }

    private String getColorName(NamedTextColor color) {
        if (color.equals(NamedTextColor.RED)) return "RED";
        if (color.equals(NamedTextColor.GOLD)) return "GOLD";
        if (color.equals(NamedTextColor.YELLOW)) return "YELLOW";
        if (color.equals(NamedTextColor.GREEN)) return "GREEN";
        if (color.equals(NamedTextColor.AQUA)) return "AQUA";
        if (color.equals(NamedTextColor.BLUE)) return "BLUE";
        if (color.equals(NamedTextColor.DARK_PURPLE)) return "DARK_PURPLE";
        if (color.equals(NamedTextColor.LIGHT_PURPLE)) return "LIGHT_PURPLE";
        if (color.equals(NamedTextColor.WHITE)) return "WHITE";
        return "UNKNOWN";
    }

    /**
     * Helper method to format a Location for debug logging.
     */
    private String formatLocation(Location location) {
        if (location == null) return "null";
        return String.format("(world=%s, x=%.2f, y=%.2f, z=%.2f)",
                location.getWorld() != null ? location.getWorld().getName() : "null",
                location.getX(), location.getY(), location.getZ());
    }
}