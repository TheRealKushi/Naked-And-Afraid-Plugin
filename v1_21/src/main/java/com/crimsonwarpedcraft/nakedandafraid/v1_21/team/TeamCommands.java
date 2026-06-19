// V1_21/TeamCommands

package com.crimsonwarpedcraft.nakedandafraid.v1_21.team;

import com.crimsonwarpedcraft.nakedandafraid.v1_21.NakedAndAfraid;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class TeamCommands {

    private final TeamsManager teamsManager;
    private final NakedAndAfraid plugin;

    private static final String TEAM_BLOCK_SELECTOR_NAME = "Team Block Selector";

    private final Set<UUID> usedSelectorPlayers = new HashSet<>();

    public TeamCommands(TeamsManager teamsManager, NakedAndAfraid plugin) {
        this.plugin = plugin;
        this.teamsManager = teamsManager;
        plugin.debugLog("[TeamCommands] Initialized TeamCommands for Bukkit version " + Bukkit.getBukkitVersion());
    }

    private boolean isPre113() {
        return !Bukkit.getBukkitVersion().matches(".*1\\.(1[3-9]|2[0-1]).*");
    }

    private boolean isPre114() {
        return !Bukkit.getBukkitVersion().matches(".*1\\.(1[4-9]|2[0-1]).*");
    }

    private void sendMessage(CommandSender sender, String message, String legacyColor) {
        sender.sendMessage(legacyColor + message);
    }

    /**
     * Handles /nf teams <create|remove|list> - management operations.
     */
    public boolean handleTeamsCommand(CommandSender sender, String[] args) {
        plugin.debugLog("[TeamCommands] Processing teams command for " + sender.getName() + ": " + String.join(" ", args));
        if (args.length < 2) {
            sendMessage(sender, "Usage: /nf teams <create|remove|list>", "§c");
            plugin.debugLog("[TeamCommands] Invalid arguments for " + sender.getName() + ", expected at least 2");
            return true;
        }

        var sub = args[1].toLowerCase();
        plugin.debugLog("[TeamCommands] Teams subcommand: " + sub);
        return switch (sub) {
            case "create" -> handleTeamCreate(sender, args);
            case "list" -> handleTeamList(sender);
            case "remove" -> handleTeamRemove(sender, args);
            default -> {
                sendMessage(sender, "Unknown subcommand. Usage: /nf teams <create|remove|list>", "§c");
                plugin.debugLog("[TeamCommands] Unknown teams subcommand: " + sub);
                yield true;
            }
        };
    }

    /**
     * Handles /nf team <team-name> <block|setblock|meta> - per-team operations.
     */
    public boolean handleTeamCommand(CommandSender sender, String[] args) {
        plugin.debugLog("[TeamCommands] Processing team command for " + sender.getName() + ": " + String.join(" ", args));
        if (args.length < 3) {
            sendMessage(sender, "Usage: /nf team <team-name> <block|setblock|meta> ...", "§c");
            plugin.debugLog("[TeamCommands] Invalid arguments for " + sender.getName() + ", expected at least 3");
            return true;
        }

        var teamName = args[1].toLowerCase();
        plugin.debugLog("[TeamCommands] Team name: " + teamName);

        if (!teamsManager.teamExists(teamName)) {
            sendMessage(sender, "Team '" + teamName + "' does not exist.", "§c");
            plugin.debugLog("[TeamCommands] Team '" + teamName + "' does not exist");
            return true;
        }

        return switch (args[2].toLowerCase()) {
            case "block" -> handleTeamBlockSelector(sender, args, teamName);
            case "setblock" -> handleTeamSetBlock(sender, args, teamName);
            case "meta" -> handleTeamMeta(sender, args, teamName);
            default -> {
                sendMessage(sender, "Unknown subcommand. Usage: /nf team <team-name> <block|setblock|meta>", "§c");
                plugin.debugLog("[TeamCommands] Unknown team subcommand: " + args[2]);
                yield true;
            }
        };
    }

    public boolean handleUserCommand(CommandSender sender, String[] args) {
        plugin.debugLog("[TeamCommands] Processing user command for " + sender.getName() + ": " + String.join(" ", args));
        if (args.length < 4) {
            sendMessage(sender, "Usage: /nf user <player> team <add|remove|list> [team]", "§c");
            plugin.debugLog("[TeamCommands] Invalid arguments for user command, expected at least 4");
            return true;
        }

        var playerName = args[1];
        plugin.debugLog("[TeamCommands] Target player: " + playerName);
        var target = Bukkit.getOfflinePlayerIfCached(playerName);
        if (target == null) target = Bukkit.getOfflinePlayer(playerName);

        var userSub = args[3].toLowerCase();
        plugin.debugLog("[TeamCommands] User subcommand: " + userSub);

        return switch (userSub) {
            case "add" -> {
                if (args.length < 5) {
                    sendMessage(sender, "Usage: /nf user <player> team add <team>", "§c");
                    plugin.debugLog("[TeamCommands] Invalid arguments for user add, expected 5");
                    yield true;
                }
                yield handleUserTeamAdd(sender, target, args[4]);
            }
            case "remove" -> {
                if (args.length < 5) {
                    sendMessage(sender, "Usage: /nf user <player> team remove <team>", "§c");
                    plugin.debugLog("[TeamCommands] Invalid arguments for user remove, expected 5");
                    yield true;
                }
                yield handleUserTeamRemove(sender, target, args[4]);
            }
            case "list" -> handleUserTeamList(sender, target);
            default -> {
                sendMessage(sender, "Unknown user team subcommand.", "§c");
                plugin.debugLog("[TeamCommands] Unknown user subcommand: " + userSub);
                yield true;
            }
        };
    }

    private boolean handleTeamCreate(CommandSender sender, String[] args) {
        plugin.debugLog("[TeamCommands] Handling team create for " + sender.getName() + ": " + String.join(" ", args));
        if (args.length < 4) {
            sendMessage(sender, "Usage: /nf team create <team-name> <team-color>", "§c");
            plugin.debugLog("[TeamCommands] Invalid arguments for create, expected at least 4");
            return true;
        }

        var teamName = args[2].toLowerCase();
        var colorName = args[3].toUpperCase();
        plugin.debugLog("[TeamCommands] Team name: " + teamName + ", color: " + colorName);

        if (isValidColor(colorName)) {
            sendMessage(sender, "Invalid color. Valid colors: RED, BLUE, GREEN, YELLOW, AQUA, DARK_PURPLE, GOLD, LIGHT_PURPLE, WHITE", "§c");
            plugin.debugLog("[TeamCommands] Invalid color: " + colorName);
            return true;
        }

        if (teamsManager.teamExists(teamName)) {
            sendMessage(sender, "Team already exists.", "§c");
            plugin.debugLog("[TeamCommands] Team '" + teamName + "' already exists");
            return true;
        }

        if (!teamsManager.createTeam(teamName, colorName)) {
            sendMessage(sender, "Max teams reached!", "§c");
            plugin.debugLog("[TeamCommands] Max teams reached for '" + teamName + "'");
            return true;
        }

        sendMessage(sender, "Team '" + teamName + "' created with color " + colorName, getLegacyColor(colorName));
        plugin.debugLog("[TeamCommands] Created team '" + teamName + "' with color " + colorName);
        return true;
    }

    private boolean handleTeamList(CommandSender sender) {
        plugin.debugLog("[TeamCommands] Handling team list for " + sender.getName());
        var teams = teamsManager.getTeams();
        if (teams.isEmpty()) {
            sendMessage(sender, "No teams available.", "§e");
            plugin.debugLog("[TeamCommands] No teams available");
            return true;
        }
        sendMessage(sender, "Teams:", "§6");
        for (var team : teams) {
            sendMessage(sender, "- " + team.getName(), getLegacyColor(team.getColor()));
            plugin.debugLog("[TeamCommands] Listed team '" + team.getName() + "'");
        }
        return true;
    }

    private boolean handleTeamRemove(CommandSender sender, String[] args) {
        plugin.debugLog("[TeamCommands] Handling team remove for " + sender.getName() + ": " + String.join(" ", args));
        if (args.length < 3) {
            sendMessage(sender, "Usage: /nf team remove <team>", "§c");
            plugin.debugLog("[TeamCommands] Invalid arguments for remove, expected 3");
            return true;
        }
        var teamName = args[2].toLowerCase();
        plugin.debugLog("[TeamCommands] Team name: " + teamName);
        if (!teamsManager.teamExists(teamName)) {
            sendMessage(sender, "Team does not exist.", "§c");
            plugin.debugLog("[TeamCommands] Team '" + teamName + "' does not exist");
            return true;
        }
        teamsManager.removeTeam(teamName);
        sendMessage(sender, "Team '" + teamName + "' removed.", "§a");
        plugin.debugLog("[TeamCommands] Removed team '" + teamName + "'");
        return true;
    }

    // /nf team <team-name> block selector <player>
    private boolean handleTeamBlockSelector(CommandSender sender, String[] args, String teamName) {
        plugin.debugLog("[TeamCommands] Handling block selector for team '" + teamName + "': " + String.join(" ", args));
        if (!(sender instanceof Player)) {
            sendMessage(sender, "Only players can use this command.", "§c");
            plugin.debugLog("[TeamCommands] Non-player sender attempted block selector command");
            return true;
        }
        // args: [0]=team [1]=<team-name> [2]=block [3]=selector [4]=<player>
        if (args.length < 5 || !args[3].equalsIgnoreCase("selector")) {
            sendMessage(sender, "Usage: /nf team <team-name> block selector <player>", "§c");
            plugin.debugLog("[TeamCommands] Invalid block selector syntax");
            return true;
        }
        var target = Bukkit.getPlayer(args[4]);
        if (target == null) {
            sendMessage(sender, "Player not found or offline.", "§c");
            plugin.debugLog("[TeamCommands] Player '" + args[4] + "' not found or offline");
            return true;
        }
        var axe = createTeamBlockSelector(teamName);
        target.getInventory().addItem(axe);
        sendMessage(sender, "Given team block selector for team '" + teamName + "' to " + target.getName(), "§a");
        plugin.debugLog("[TeamCommands] Gave block selector for team '" + teamName + "' to " + target.getName());
        return true;
    }

    private ItemStack createTeamBlockSelector(String teamName) {
        plugin.debugLog("[TeamCommands] Creating team block selector for team '" + teamName + "'");
        var axe = new ItemStack(Material.IRON_AXE);
        var meta = axe.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6" + TEAM_BLOCK_SELECTOR_NAME);
            if (isPre114()) {
                meta.setLore(List.of("Team: " + teamName));
            } else {
                meta.getPersistentDataContainer().set(
                        new NamespacedKey(plugin.getPlugin(), "teamSelector"),
                        PersistentDataType.STRING, teamName);
            }
            axe.setItemMeta(meta);
            plugin.debugLog("[TeamCommands] Set item meta for selector: team=" + teamName);
        }
        return axe;
    }

    // /nf team <team-name> setblock <x> <y> <z>
    private boolean handleTeamSetBlock(CommandSender sender, String[] args, String teamName) {
        plugin.debugLog("[TeamCommands] Handling setblock for team '" + teamName + "': " + String.join(" ", args));
        if (!(sender instanceof Player player)) {
            sendMessage(sender, "Only players can use this command.", "§c");
            plugin.debugLog("[TeamCommands] Non-player sender attempted setblock command");
            return true;
        }
        // args: [0]=team [1]=<team-name> [2]=setblock [3]=x [4]=y [5]=z
        if (args.length != 6) {
            sendMessage(sender, "Usage: /nf team <team-name> setblock <x> <y> <z>", "§c");
            plugin.debugLog("[TeamCommands] Invalid arguments for setblock, expected 6");
            return true;
        }
        int x, y, z;
        try {
            x = Integer.parseInt(args[3]);
            y = Integer.parseInt(args[4]);
            z = Integer.parseInt(args[5]);
            plugin.debugLog("[TeamCommands] Parsed coordinates: x=" + x + ", y=" + y + ", z=" + z);
        } catch (NumberFormatException e) {
            sendMessage(sender, "Coordinates must be numbers.", "§c");
            plugin.debugLog("[TeamCommands] Invalid coordinates");
            return true;
        }
        var world = player.getWorld();
        var loc = new Location(world, x, y, z);
        var block = world.getBlockAt(loc);
        if (block.getType() != teamsManager.getTeamBlockMaterial()) {
            sendMessage(sender, "Block at location is not the configured team block (" + teamsManager.getTeamBlockMaterial() + ").", "§c");
            plugin.debugLog("[TeamCommands] Wrong block type at " + formatLocation(loc));
            return true;
        }
        teamsManager.setLodestone(teamName, loc);
        sendMessage(sender, "Lodestone set for team '" + teamName + "' at " + x + " " + y + " " + z, "§a");
        plugin.debugLog("[TeamCommands] Set lodestone for team '" + teamName + "' at " + formatLocation(loc));
        return true;
    }

    // /nf team <team-name> meta color <get|set> [color]
    private boolean handleTeamMeta(CommandSender sender, String[] args, String teamName) {
        plugin.debugLog("[TeamCommands] Handling meta for team '" + teamName + "': " + String.join(" ", args));
        // args: [0]=team [1]=<team-name> [2]=meta [3]=color [4]=get|set [5]=<color>
        if (args.length < 5 || !args[3].equalsIgnoreCase("color")) {
            sendMessage(sender, "Usage: /nf team <team-name> meta color <get|set> [color]", "§c");
            plugin.debugLog("[TeamCommands] Invalid arguments for meta");
            return true;
        }

        var action = args[4].toLowerCase();
        var team = teamsManager.getTeam(teamName);

        switch (action) {
            case "get" -> {
                sendMessage(sender, "Team '" + teamName + "' color: " + team.getColor(), getLegacyColor(team.getColor()));
                plugin.debugLog("[TeamCommands] Retrieved color " + team.getColor() + " for team '" + teamName + "'");
            }
            case "set" -> {
                if (args.length < 6) {
                    sendMessage(sender, "Usage: /nf team <team-name> meta color set <color>", "§c");
                    plugin.debugLog("[TeamCommands] Missing color argument for meta color set");
                    break;
                }
                var newColorName = args[5].toUpperCase();
                if (isValidColor(newColorName)) {
                    sendMessage(sender, "Invalid color. Valid colors: RED, BLUE, GREEN, YELLOW, AQUA, DARK_PURPLE, GOLD, LIGHT_PURPLE, WHITE", "§c");
                    plugin.debugLog("[TeamCommands] Invalid color: " + newColorName);
                    break;
                }
                team.setColor(newColorName);
                sendMessage(sender, "Team '" + teamName + "' color updated to " + newColorName, getLegacyColor(newColorName));
                plugin.debugLog("[TeamCommands] Updated color for team '" + teamName + "' to " + newColorName);
                for (var memberUUID : team.getMembers()) {
                    var member = Bukkit.getPlayer(memberUUID);
                    if (member != null && member.isOnline()) {
                        updatePlayerNametagColor(member, teamName);
                    }
                }
            }
            default -> {
                sendMessage(sender, "Unknown color action. Use get or set.", "§c");
                plugin.debugLog("[TeamCommands] Unknown color action: " + action);
            }
        }
        return true;
    }

    public TeamsManager.Team getTeamForPlayer(Player player) {
        plugin.debugLog("[TeamCommands] Getting team for player " + player.getName());
        var uuid = player.getUniqueId();
        for (var team : teamsManager.getTeams()) {
            if (team.getMembers().contains(uuid)) {
                plugin.debugLog("[TeamCommands] Found team '" + team.getName() + "' for player " + player.getName());
                return team;
            }
        }
        plugin.debugLog("[TeamCommands] No team found for player " + player.getName());
        return null;
    }

    private boolean handleUserTeamAdd(CommandSender sender, OfflinePlayer target, String teamName) {
        plugin.debugLog("[TeamCommands] User team add: player=" + target.getName() + ", team=" + teamName);
        if (!teamsManager.teamExists(teamName)) {
            sendMessage(sender, "Team does not exist.", "§c");
            plugin.debugLog("[TeamCommands] Team '" + teamName + "' does not exist");
            return true;
        }
        if (teamsManager.addMember(teamName, target.getUniqueId())) {
            sendMessage(sender, "Added " + target.getName() + " to team " + teamName, "§a");
            plugin.debugLog("[TeamCommands] Added " + target.getName() + " to team '" + teamName + "'");
            if (target.isOnline()) {
                sendMessage((Player) target, "You have been added to team " + teamName, "§a");
                plugin.debugLog("[TeamCommands] Notified " + target.getName() + " of team addition");
            }
        } else {
            sendMessage(sender, target.getName() + " is already in the team or error occurred.", "§c");
            plugin.debugLog("[TeamCommands] Failed to add " + target.getName() + " to team '" + teamName + "', already in team or error");
        }
        return true;
    }

    private boolean handleUserTeamRemove(CommandSender sender, OfflinePlayer target, String teamName) {
        plugin.debugLog("[TeamCommands] User team remove: player=" + target.getName() + ", team=" + teamName);
        if (!teamsManager.teamExists(teamName)) {
            sendMessage(sender, "Team does not exist.", "§c");
            plugin.debugLog("[TeamCommands] Team '" + teamName + "' does not exist");
            return true;
        }
        if (teamsManager.removeMember(teamName, target.getUniqueId())) {
            sendMessage(sender, "Removed " + target.getName() + " from team " + teamName, "§a");
            plugin.debugLog("[TeamCommands] Removed " + target.getName() + " from team '" + teamName + "'");
            if (target.isOnline()) {
                sendMessage((Player) target, "You have been removed from team " + teamName, "§c");
                plugin.debugLog("[TeamCommands] Notified " + target.getName() + " of team removal");
            }
        } else {
            sendMessage(sender, target.getName() + " is not in the team or error occurred.", "§c");
            plugin.debugLog("[TeamCommands] Failed to remove " + target.getName() + " from team '" + teamName + "', not in team or error");
        }
        return true;
    }

    private boolean handleUserTeamList(CommandSender sender, OfflinePlayer target) {
        plugin.debugLog("[TeamCommands] User team list: player=" + target.getName());
        var uuid = target.getUniqueId();
        var playerTeams = new ArrayList<String>();
        for (var team : teamsManager.getTeams()) {
            if (team.getMembers().contains(uuid)) {
                playerTeams.add(team.getName());
                plugin.debugLog("[TeamCommands] Found team '" + team.getName() + "' for " + target.getName());
            }
        }
        if (playerTeams.isEmpty()) {
            sendMessage(sender, target.getName() + " is not in any team.", "§e");
            plugin.debugLog("[TeamCommands] No teams found for " + target.getName());
            return true;
        }
        sendMessage(sender, target.getName() + "'s teams:", "§6");
        for (var team : playerTeams) {
            sendMessage(sender, "- " + team, "§f");
            plugin.debugLog("[TeamCommands] Listed team '" + team + "' for " + target.getName());
        }
        return true;
    }

    public void onTeamBlockSelectorUse(Player player, Block block) {
        plugin.debugLog("[TeamCommands] Player " + player.getName() + " used team block selector on " + formatLocation(block.getLocation()));
        if (block.getType() != teamsManager.getTeamBlockMaterial()) {
            sendMessage(player, "This block is not the configured team block (" + teamsManager.getTeamBlockMaterial() + ").", "§c");
            plugin.debugLog("[TeamCommands] Block at " + formatLocation(block.getLocation()) + " is " + block.getType() + ", expected " + teamsManager.getTeamBlockMaterial());
            return;
        }

        var item = player.getInventory().getItemInMainHand();
        if (item == null || !item.hasItemMeta()) return;
        var meta = item.getItemMeta();
        if (meta == null) return;

        String teamName;
        if (isPre114()) {
            var lore = meta.getLore();
            if (lore == null || lore.isEmpty() || !lore.get(0).startsWith("Team: ")) {
                sendMessage(player, "This selector is invalid (missing team info)", "§c");
                plugin.debugLog("[TeamCommands] Selector missing team info in lore");
                return;
            }
            teamName = lore.getFirst().substring(6);
        } else {
            teamName = meta.getPersistentDataContainer().get(
                    new NamespacedKey(plugin.getPlugin(), "teamSelector"),
                    PersistentDataType.STRING);
            if (teamName == null) {
                sendMessage(player, "This selector is invalid (missing team info)", "§c");
                plugin.debugLog("[TeamCommands] Selector missing team info");
                return;
            }
        }

        if (!teamsManager.teamExists(teamName)) {
            sendMessage(player, "Team '" + teamName + "' no longer exists", "§c");
            plugin.debugLog("[TeamCommands] Team '" + teamName + "' no longer exists");
            return;
        }

        teamsManager.setLodestone(teamName, block.getLocation());
        sendMessage(player, "Team lodestone for '" + teamName + "' set at " +
                block.getX() + ", " + block.getY() + ", " + block.getZ(), "§a");
        plugin.debugLog("[TeamCommands] Set lodestone for team '" + teamName + "' at " + formatLocation(block.getLocation()));
        updatePlayerNametagColor(player, teamName);
    }

    private void updatePlayerNametagColor(Player player, String teamName) {
        plugin.debugLog("[TeamCommands] Updating nametag color for " + player.getName() + " in team '" + teamName + "'");
        var team = teamsManager.getTeam(teamName);
        if (team == null) return;

        var scoreboard = player.getScoreboard();
        var scoreboardTeam = scoreboard.getTeam(teamName);
        if (scoreboardTeam == null) {
            scoreboardTeam = scoreboard.registerNewTeam(teamName);
            scoreboardTeam.setDisplayName(teamName);
            var chatColor = parseChatColor(team.getColor());
            if (chatColor != null) {
                scoreboardTeam.setColor(chatColor);
                plugin.debugLog("[TeamCommands] Set scoreboard team color to " + chatColor + " for team '" + teamName + "'");
            }
            plugin.debugLog("[TeamCommands] Registered new scoreboard team '" + teamName + "' with color " + team.getColor());
        }
        for (var memberUUID : team.getMembers()) {
            var member = Bukkit.getPlayer(memberUUID);
            if (member != null && member.isOnline()) {
                scoreboardTeam.addEntry(member.getName());
                plugin.debugLog("[TeamCommands] Added " + member.getName() + " to scoreboard team '" + teamName + "'");
            }
        }
        player.setScoreboard(scoreboard);
        sendMessage(player, "Nametag color updated for team '" + teamName + "'", "§a");
    }

    private boolean isValidColor(String colorName) {
        return !List.of("RED", "BLUE", "GREEN", "YELLOW", "AQUA", "DARK_PURPLE", "GOLD", "LIGHT_PURPLE", "WHITE")
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