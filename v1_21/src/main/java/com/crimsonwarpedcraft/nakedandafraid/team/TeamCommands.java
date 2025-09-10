package com.crimsonwarpedcraft.nakedandafraid.team;

import com.crimsonwarpedcraft.nakedandafraid.NakedAndAfraid;
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
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Initialized TeamCommands for Bukkit version " + Bukkit.getBukkitVersion());
    }

    /**
     * Checks if the server is pre-1.13 (Minecraft 1.12).
     */
    private boolean isPre113() {
        return !Bukkit.getBukkitVersion().matches(".*1\\.(1[3-9]|2[0-1]).*");
    }

    /**
     * Checks if the server is pre-1.14 (Minecraft 1.12–1.13.2).
     */
    private boolean isPre114() {
        return !Bukkit.getBukkitVersion().matches(".*1\\.(1[4-9]|2[0-1]).*");
    }

    /**
     * Sends a message to the sender using legacy chat formatting.
     */
    private void sendMessage(CommandSender sender, String message, String legacyColor) {
        sender.sendMessage(legacyColor + message);
    }

    public boolean handleTeamCommand(CommandSender sender, String[] args) {
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Processing team command for " + sender.getName() + ": " + String.join(" ", args));
        if (args.length < 2) {
            sendMessage(sender, "Usage: /nf team <create|list|remove|block|setblock> ...", "§c");
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Invalid arguments for " + sender.getName() + ", expected at least 2");
            return true;
        }

        var sub = args[1].toLowerCase();

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
            sendMessage(sender, "Usage: /nf team <team-name> <block|setblock> ...", "§c");
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Invalid subcommand for team '" + sub + "', expected block or setblock");
            return true;
        }

        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Subcommand: " + sub);
        return switch (sub) {
            case "create" -> handleTeamCreate(sender, args);
            case "list" -> handleTeamList(sender);
            case "remove" -> handleTeamRemove(sender, args);
            case "block" -> {
                if (args.length >= 4 && teamsManager.teamExists(args[2].toLowerCase()) && args[3].equalsIgnoreCase("selector")) {
                    yield handleTeamBlockSelector(sender, args);
                }
                sendMessage(sender, "Usage: /nf team <team-name> block selector <player>", "§c");
                ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Invalid arguments for block selector");
                yield true;
            }
            case "setblock" -> {
                if (args.length == 6 && teamsManager.teamExists(args[2].toLowerCase())) {
                    yield handleTeamSetBlock(sender, args);
                }
                sendMessage(sender, "Usage: /nf team <team-name> setblock <x> <y> <z>", "§c");
                ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Invalid arguments for setblock");
                yield true;
            }
            case "meta" -> {
                if (args.length < 4) {
                    sendMessage(sender, "Usage: /nf team meta <team-name> color <get|set> [color]", "§c");
                    ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Invalid arguments for meta, expected at least 4");
                    yield true;
                }
                if (args[3].equalsIgnoreCase("color")) {
                    yield handleTeamMetaColor(sender, args);
                }
                sendMessage(sender, "Unknown meta subcommand.", "§c");
                ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Unknown meta subcommand: " + args[3]);
                yield true;
            }
            default -> {
                sendMessage(sender, "Unknown team subcommand.", "§c");
                ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Unknown subcommand: " + sub);
                yield true;
            }
        };
    }

    public boolean handleUserCommand(CommandSender sender, String[] args) {
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Processing user command for " + sender.getName() + ": " + String.join(" ", args));
        if (args.length < 4) {
            sendMessage(sender, "Usage: /nf user <player> team <add|remove|list> [team]", "§c");
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Invalid arguments for user command, expected at least 4");
            return true;
        }

        var playerName = args[1];
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Target player: " + playerName);
        var target = Bukkit.getOfflinePlayerIfCached(playerName);
        if (target == null) target = Bukkit.getOfflinePlayer(playerName);

        var userSub = args[3].toLowerCase();
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] User subcommand: " + userSub);

        return switch (userSub) {
            case "add" -> {
                if (args.length < 5) {
                    sendMessage(sender, "Usage: /nf user <player> team add <team>", "§c");
                    ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Invalid arguments for user add, expected 5");
                    yield true;
                }
                yield handleUserTeamAdd(sender, target, args[4]);
            }
            case "remove" -> {
                if (args.length < 5) {
                    sendMessage(sender, "Usage: /nf user <player> team remove <team>", "§c");
                    ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Invalid arguments for user remove, expected 5");
                    yield true;
                }
                yield handleUserTeamRemove(sender, target, args[4]);
            }
            case "list" -> handleUserTeamList(sender, target);
            default -> {
                sendMessage(sender, "Unknown user team subcommand.", "§c");
                ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Unknown user subcommand: " + userSub);
                yield true;
            }
        };
    }

    private boolean handleTeamCreate(CommandSender sender, String[] args) {
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Handling team create command for " + sender.getName() + ": " + String.join(" ", args));
        if (args.length < 4) {
            sendMessage(sender, "Usage: /nf team create <team-name> <team-color>", "§c");
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Invalid arguments for create, expected at least 4");
            return true;
        }

        var teamName = args[2].toLowerCase();
        var colorName = args[3].toUpperCase();
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Team name: " + teamName + ", color: " + colorName);

        if (!isValidColor(colorName)) {
            sendMessage(sender, "Invalid color. Valid colors: RED, BLUE, GREEN, YELLOW, AQUA, DARK_PURPLE, GOLD, LIGHT_PURPLE, WHITE", "§c");
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Invalid color: " + colorName);
            return true;
        }

        if (teamsManager.teamExists(teamName)) {
            sendMessage(sender, "Team already exists.", "§c");
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Team '" + teamName + "' already exists");
            return true;
        }

        if (!teamsManager.createTeam(teamName, colorName)) {
            sendMessage(sender, "Max teams reached!", "§c");
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Max teams reached for '" + teamName + "'");
            return true;
        }

        var message = "Team '" + teamName + "' created with color " + colorName;
        sendMessage(sender, message, getLegacyColor(colorName));
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Created team '" + teamName + "' with color " + colorName);
        return true;
    }

    private boolean isValidColor(String colorName) {
        return List.of("RED", "BLUE", "GREEN", "YELLOW", "AQUA", "DARK_PURPLE", "GOLD", "LIGHT_PURPLE", "WHITE")
                .contains(colorName);
    }

    private String getLegacyColor(String color) {
        return switch (color.toUpperCase()) {
            case "RED" -> "§c";
            case "GOLD" -> "§6";
            case "YELLOW" -> "§e";
            case "GREEN" -> "§a";
            case "AQUA" -> "§b";
            case "BLUE" -> "§9";
            case "DARK_PURPLE" -> "§5";
            case "LIGHT_PURPLE" -> "§d";
            case "WHITE" -> "§f";
            default -> "§f";
        };
    }

    private boolean handleTeamList(CommandSender sender) {
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Handling team list command for " + sender.getName());
        var teams = teamsManager.getTeams();
        if (teams.isEmpty()) {
            sendMessage(sender, "No teams available.", "§e");
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] No teams available for " + sender.getName());
            return true;
        }
        sendMessage(sender, "Teams:", "§6");
        for (var team : teams) {
            var message = "- " + team.getName();
            sendMessage(sender, message, getLegacyColor(team.getColor()));
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Listed team '" + team.getName() + "' with color " + team.getColor());
        }
        return true;
    }

    private boolean handleTeamRemove(CommandSender sender, String[] args) {
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Handling team remove command for " + sender.getName() + ": " + String.join(" ", args));
        if (args.length < 3) {
            sendMessage(sender, "Usage: /nf team remove <team>", "§c");
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Invalid arguments for remove, expected 3");
            return true;
        }
        var teamName = args[2].toLowerCase();
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Team name: " + teamName);
        if (!teamsManager.teamExists(teamName)) {
            sendMessage(sender, "Team does not exist.", "§c");
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Team '" + teamName + "' does not exist");
            return true;
        }
        teamsManager.removeTeam(teamName);
        sendMessage(sender, "Team '" + teamName + "' removed.", "§a");
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Removed team '" + teamName + "' for " + sender.getName());
        return true;
    }

    private boolean handleTeamBlockSelector(CommandSender sender, String[] args) {
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Handling team block selector command for " + sender.getName() + ": " + String.join(" ", args));
        if (!(sender instanceof Player)) {
            sendMessage(sender, "Only players can use this command.", "§c");
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Non-player sender attempted block selector command");
            return true;
        }
        if (args.length < 5) {
            sendMessage(sender, "Usage: /nf team <team-name> block selector <player>", "§c");
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Invalid arguments for block selector, expected 5");
            return true;
        }
        var teamName = args[1].toLowerCase();
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Team name: " + teamName);
        if (!teamsManager.teamExists(teamName)) {
            sendMessage(sender, "Team '" + teamName + "' does not exist.", "§c");
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Team '" + teamName + "' does not exist");
            return true;
        }
        if (!args[2].equalsIgnoreCase("block") || !args[3].equalsIgnoreCase("selector")) {
            sendMessage(sender, "Usage: /nf team <team-name> block selector <player>", "§c");
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Invalid block selector syntax");
            return true;
        }
        var target = Bukkit.getPlayer(args[4]);
        if (target == null) {
            sendMessage(sender, "Player not found or offline.", "§c");
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Player '" + args[4] + "' not found or offline");
            return true;
        }
        var axe = createTeamBlockSelector(teamName);
        target.getInventory().addItem(axe);
        sendMessage(sender, "Given team block selector for team '" + teamName + "' to " + target.getName(), "§a");
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Gave block selector for team '" + teamName + "' to " + target.getName());
        return true;
    }

    private ItemStack createTeamBlockSelector(String teamName) {
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Creating team block selector for team '" + teamName + "'");
        var axe = new ItemStack(Material.IRON_AXE);
        var meta = axe.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6" + TEAM_BLOCK_SELECTOR_NAME);
            if (isPre114()) {
                meta.setLore(List.of("Team: " + teamName));
            } else {
                meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "teamSelector"), PersistentDataType.STRING, teamName);
            }
            axe.setItemMeta(meta);
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Set item meta for selector: team=" + teamName);
        }
        return axe;
    }

    private boolean handleTeamSetBlock(CommandSender sender, String[] args) {
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Handling team setblock command for " + sender.getName() + ": " + String.join(" ", args));
        if (!(sender instanceof Player player)) {
            sendMessage(sender, "Only players can use this command.", "§c");
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Non-player sender attempted setblock command");
            return true;
        }
        if (args.length != 6) {
            sendMessage(sender, "Usage: /nf team <team-name> setblock <x> <y> <z>", "§c");
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Invalid arguments for setblock, expected 6");
            return true;
        }
        var teamName = args[1].toLowerCase();
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Team name: " + teamName);
        if (!teamsManager.teamExists(teamName)) {
            sendMessage(sender, "Team '" + teamName + "' does not exist.", "§c");
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
            sendMessage(sender, "Coordinates must be numbers.", "§c");
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Invalid coordinates: " + String.join(" ", Arrays.copyOfRange(args, 3, args.length)));
            return true;
        }
        var world = player.getWorld();
        var loc = new Location(world, x, y, z);
        var block = world.getBlockAt(loc);
        if (block.getType() != teamsManager.getTeamBlockMaterial()) {
            sendMessage(sender, "Block at location is not the configured team block (" + teamsManager.getTeamBlockMaterial() + ").", "§c");
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Block at " + formatLocation(loc) + " is " + block.getType() + ", expected " + teamsManager.getTeamBlockMaterial());
            return true;
        }
        teamsManager.setLodestone(teamName, loc);
        sendMessage(sender, "Lodestone set for team '" + teamName + "' at " + x + " " + y + " " + z, "§a");
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Set lodestone for team '" + teamName + "' at " + formatLocation(loc));
        return true;
    }

    public TeamsManager.Team getTeamForPlayer(Player player) {
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Getting team for player " + player.getName());
        var uuid = player.getUniqueId();
        for (var team : teamsManager.getTeams()) {
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
            sendMessage(sender, "Team does not exist.", "§c");
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Team '" + teamName + "' does not exist");
            return true;
        }
        if (teamsManager.addMember(teamName, target.getUniqueId())) {
            sendMessage(sender, "Added " + target.getName() + " to team " + teamName, "§a");
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Added " + target.getName() + " to team '" + teamName + "'");
            if (target.isOnline()) {
                sendMessage((Player) target, "You have been added to team " + teamName, "§a");
                ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Notified " + target.getName() + " of team addition");
            }
        } else {
            sendMessage(sender, target.getName() + " is already in the team or error occurred.", "§c");
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Failed to add " + target.getName() + " to team '" + teamName + "', already in team or error");
        }
        return true;
    }

    private boolean handleUserTeamRemove(CommandSender sender, OfflinePlayer target, String teamName) {
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Handling user team remove for " + sender.getName() + ": player=" + target.getName() + ", team=" + teamName);
        if (!teamsManager.teamExists(teamName)) {
            sendMessage(sender, "Team does not exist.", "§c");
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Team '" + teamName + "' does not exist");
            return true;
        }
        if (teamsManager.removeMember(teamName, target.getUniqueId())) {
            sendMessage(sender, "Removed " + target.getName() + " from team " + teamName, "§a");
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Removed " + target.getName() + " from team '" + teamName + "'");
            if (target.isOnline()) {
                sendMessage((Player) target, "You have been removed from team " + teamName, "§c");
                ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Notified " + target.getName() + " of team removal");
            }
        } else {
            sendMessage(sender, target.getName() + " is not in the team or error occurred.", "§c");
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Failed to remove " + target.getName() + " from team '" + teamName + "', not in team or error");
        }
        return true;
    }

    private boolean handleUserTeamList(CommandSender sender, OfflinePlayer target) {
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Handling user team list for " + sender.getName() + ": player=" + target.getName());
        var uuid = target.getUniqueId();
        var playerTeams = new ArrayList<String>();
        for (var team : teamsManager.getTeams()) {
            if (team.getMembers().contains(uuid)) {
                playerTeams.add(team.getName());
                ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Found team '" + team.getName() + "' for " + target.getName());
            }
        }

        if (playerTeams.isEmpty()) {
            sendMessage(sender, target.getName() + " is not in any team.", "§e");
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] No teams found for " + target.getName());
            return true;
        }

        sendMessage(sender, target.getName() + "'s teams:", "§6");
        for (var team : playerTeams) {
            sendMessage(sender, "- " + team, "§f");
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Listed team '" + team + "' for " + target.getName());
        }
        return true;
    }

    public void onTeamBlockSelectorUse(Player player, Block block) {
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Player " + player.getName() + " used team block selector on block at " + formatLocation(block.getLocation()));
        if (block.getType() != teamsManager.getTeamBlockMaterial()) {
            sendMessage(player, "This block is not the configured team block (" + teamsManager.getTeamBlockMaterial() + ").", "§c");
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Block at " + formatLocation(block.getLocation()) + " is " + block.getType() + ", expected " + teamsManager.getTeamBlockMaterial());
            return;
        }

        var item = player.getInventory().getItemInMainHand();
        if (item == null || !item.hasItemMeta()) {
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Invalid item: null or no meta");
            return;
        }
        var meta = item.getItemMeta();
        if (meta == null) {
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Item meta is null");
            return;
        }

        String teamName;
        if (isPre114()) {
            var lore = meta.getLore();
            if (lore == null || lore.isEmpty() || !lore.get(0).startsWith("Team: ")) {
                sendMessage(player, "This selector is invalid (missing team info)", "§c");
                ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Selector missing team info in lore");
                return;
            }
            teamName = lore.get(0).substring(6);
        } else {
            teamName = meta.getPersistentDataContainer().get(new NamespacedKey(plugin, "teamSelector"), PersistentDataType.STRING);
            if (teamName == null) {
                sendMessage(player, "This selector is invalid (missing team info)", "§c");
                ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Selector missing team info");
                return;
            }
        }

        if (!teamsManager.teamExists(teamName)) {
            sendMessage(player, "Team '" + teamName + "' no longer exists", "§c");
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Team '" + teamName + "' no longer exists");
            return;
        }

        teamsManager.setLodestone(teamName, block.getLocation());
        sendMessage(player, "Team lodestone for '" + teamName + "' set at " +
                block.getX() + ", " + block.getY() + ", " + block.getZ(), "§a");
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Set lodestone for team '" + teamName + "' at " + formatLocation(block.getLocation()));

        updatePlayerNametagColor(player, teamName);
    }

    private void updatePlayerNametagColor(Player player, String teamName) {
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Updating nametag color for player " + player.getName() + " in team '" + teamName + "'");
        var team = teamsManager.getTeam(teamName);
        if (team == null) {
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Team '" + teamName + "' not found for nametag update");
            return;
        }

        var scoreboard = player.getScoreboard();
        var scoreboardTeam = scoreboard.getTeam(teamName);

        if (scoreboardTeam == null) {
            scoreboardTeam = scoreboard.registerNewTeam(teamName);
            scoreboardTeam.setDisplayName(teamName);
            var chatColor = parseChatColor(team.getColor());
            if (chatColor != null) {
                scoreboardTeam.setColor(chatColor);
                ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Set scoreboard team color to " + chatColor + " for team '" + teamName + "'");
            }
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Registered new scoreboard team '" + teamName + "' with color " + team.getColor());
        }

        for (var memberUUID : team.getMembers()) {
            var member = Bukkit.getPlayer(memberUUID);
            if (member != null && member.isOnline()) {
                scoreboardTeam.addEntry(member.getName());
                ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Added " + member.getName() + " to scoreboard team '" + teamName + "'");
            }
        }

        player.setScoreboard(scoreboard);
        sendMessage(player, "Nametag color updated for team '" + teamName + "'", "§a");
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Updated nametag color for " + player.getName() + " in team '" + teamName + "'");
    }

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

    private boolean handleTeamMetaColor(CommandSender sender, String[] args) {
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Handling team meta color command for " + sender.getName() + ": " + String.join(" ", args));
        if (args.length < 5) {
            sendMessage(sender, "Usage: /nf team meta <team-name> color <get|set> [color]", "§c");
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Invalid arguments for meta color, expected at least 5");
            return true;
        }

        var teamName = args[2].toLowerCase();
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Team name: " + teamName);
        if (!teamsManager.teamExists(teamName)) {
            sendMessage(sender, "Team '" + teamName + "' does not exist.", "§c");
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Team '" + teamName + "' does not exist");
            return true;
        }

        var action = args[4].toLowerCase();
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Color action: " + action);
        var team = teamsManager.getTeam(teamName);

        switch (action) {
            case "get" -> {
                sendMessage(sender, "Team '" + teamName + "' color: " + team.getColor(), getLegacyColor(team.getColor()));
                ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Retrieved color " + team.getColor() + " for team '" + teamName + "'");
            }
            case "set" -> {
                if (args.length < 6) {
                    sendMessage(sender, "Usage: /nf team meta <team-name> color set <color>", "§c");
                    ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Invalid arguments for meta color set, expected 6");
                    break;
                }
                var newColorName = args[5].toUpperCase();
                ((NakedAndAfraid) plugin).debugLog("[TeamCommands] New color: " + newColorName);
                if (!isValidColor(newColorName)) {
                    sendMessage(sender, "Invalid color. Valid colors: RED, BLUE, GREEN, YELLOW, AQUA, DARK_PURPLE, GOLD, LIGHT_PURPLE, WHITE", "§c");
                    ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Invalid color: " + newColorName);
                    break;
                }
                team.setColor(newColorName);
                sendMessage(sender, "Team '" + teamName + "' color updated to " + newColorName, getLegacyColor(newColorName));
                ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Updated color for team '" + teamName + "' to " + newColorName);

                for (var memberUUID : team.getMembers()) {
                    var member = Bukkit.getPlayer(memberUUID);
                    if (member != null && member.isOnline()) {
                        updatePlayerNametagColor(member, teamName);
                        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Updated nametag for " + member.getName() + " in team '" + teamName + "'");
                    }
                }
            }
            default -> {
                sendMessage(sender, "Unknown color action. Use get or set.", "§c");
                ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Unknown color action: " + action);
            }
        }
        return true;
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