// V1_8/TeamCommands

package com.crimsonwarpedcraft.nakedandafraid.v1_8.team;

import com.crimsonwarpedcraft.nakedandafraid.v1_8.NakedAndAfraid;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

public class TeamCommands {

    private final TeamsManager teamsManager;
    private final Plugin plugin;

    private static final String TEAM_BLOCK_SELECTOR_NAME = "Team Block Selector";

    private final Set<UUID> usedSelectorPlayers = new HashSet<>();

    public TeamCommands(TeamsManager teamsManager, NakedAndAfraid plugin) {
        this.plugin = NakedAndAfraid.getPlugin();
        this.teamsManager = teamsManager;
        (plugin).debugLog("[TeamCommands] Initialized TeamCommands for Bukkit version " + Bukkit.getBukkitVersion());
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


    private void setPersistentTeamName(ItemMeta meta, String teamName) {
        if (isPre114() || meta == null) return;
        try {
            // NamespacedKey key = new NamespacedKey(plugin, "teamSelector");
            Class<?> namespacedKeyClass = Class.forName("org.bukkit.NamespacedKey");
            Constructor<?> keyCtor = namespacedKeyClass.getConstructor(JavaPlugin.class, String.class);
            Object key = keyCtor.newInstance(plugin, "teamSelector");

            // PersistentDataContainer container = meta.getPersistentDataContainer();
            Method getContainer = ItemMeta.class.getMethod("getPersistentDataContainer");
            Object container = getContainer.invoke(meta);

            // PersistentDataType.STRING
            Class<?> pdtClass = Class.forName("org.bukkit.persistence.PersistentDataType");
            Field stringField = pdtClass.getField("STRING");
            Object stringType = stringField.get(null);

            // container.set(key, STRING, teamName);
            Method setMethod = container.getClass().getMethod("set", namespacedKeyClass, pdtClass, Object.class);
            setMethod.invoke(container, key, stringType, teamName);
        } catch (Throwable t) {
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Reflection setPersistentTeamName failed: " + t);
        }
    }

    private String getPersistentTeamName(ItemMeta meta) {
        if (isPre114() || meta == null) return null;
        try {
            Class<?> namespacedKeyClass = Class.forName("org.bukkit.NamespacedKey");
            Constructor<?> keyCtor = namespacedKeyClass.getConstructor(JavaPlugin.class, String.class);
            Object key = keyCtor.newInstance(plugin, "teamSelector");

            Method getContainer = ItemMeta.class.getMethod("getPersistentDataContainer");
            Object container = getContainer.invoke(meta);

            Class<?> pdtClass = Class.forName("org.bukkit.persistence.PersistentDataType");
            Field stringField = pdtClass.getField("STRING");
            Object stringType = stringField.get(null);

            Method getMethod = container.getClass().getMethod("get", namespacedKeyClass, pdtClass);
            Object result = getMethod.invoke(container, key, stringType);
            return (result instanceof String) ? (String) result : null;
        } catch (Throwable t) {
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Reflection getPersistentTeamName failed: " + t);
            return null;
        }
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
            sendMessage(sender, "Usage: /nf team <team-name> <block|setblock> ...", "§c");
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Invalid subcommand for team '" + sub + "', expected block or setblock");
            return true;
        }

        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Subcommand: " + sub);

        if (sub.equals("create")) {
            return handleTeamCreate(sender, args);
        } else if (sub.equals("list")) {
            return handleTeamList(sender);
        } else if (sub.equals("remove")) {
            return handleTeamRemove(sender, args);
        } else if (sub.equals("block")) {
            if (args.length >= 4 && teamsManager.teamExists(args[2].toLowerCase()) && args[3].equalsIgnoreCase("selector")) {
                return handleTeamBlockSelector(sender, args);
            }
            sendMessage(sender, "Usage: /nf team <team-name> block selector <player>", "§c");
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Invalid arguments for block selector");
            return true;
        } else if (sub.equals("setblock")) {
            if (args.length == 6 && teamsManager.teamExists(args[2].toLowerCase())) {
                return handleTeamSetBlock(sender, args);
            }
            sendMessage(sender, "Usage: /nf team <team-name> setblock <x> <y> <z>", "§c");
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Invalid arguments for setblock");
            return true;
        } else if (sub.equals("meta")) {
            if (args.length < 4) {
                sendMessage(sender, "Usage: /nf team meta <team-name> color <get|set> [color]", "§c");
                ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Invalid arguments for meta, expected at least 4");
                return true;
            }
            if (args[3].equalsIgnoreCase("color")) {
                return handleTeamMetaColor(sender, args);
            }
            sendMessage(sender, "Unknown meta subcommand.", "§c");
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Unknown meta subcommand: " + args[3]);
            return true;
        } else {
            sendMessage(sender, "Unknown team subcommand.", "§c");
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Unknown subcommand: " + sub);
            return true;
        }
    }

    public boolean handleUserCommand(CommandSender sender, String[] args) {
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Processing user command for " + sender.getName() + ": " + String.join(" ", args));
        if (args.length < 4) {
            sendMessage(sender, "Usage: /nf user <player> team <add|remove|list> [team]", "§c");
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Invalid arguments for user command, expected at least 4");
            return true;
        }

        String playerName = args[1];
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Target player: " + playerName);
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);

        String userSub = args[3].toLowerCase();
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] User subcommand: " + userSub);

        if (userSub.equals("add")) {
            if (args.length < 5) {
                sendMessage(sender, "Usage: /nf user <player> team add <team>", "§c");
                ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Invalid arguments for user add, expected 5");
                return true;
            }
            return handleUserTeamAdd(sender, target, args[4]);
        } else if (userSub.equals("remove")) {
            if (args.length < 5) {
                sendMessage(sender, "Usage: /nf user <player> team remove <team>", "§c");
                ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Invalid arguments for user remove, expected 5");
                return true;
            }
            return handleUserTeamRemove(sender, target, args[4]);
        } else if (userSub.equals("list")) {
            return handleUserTeamList(sender, target);
        } else {
            sendMessage(sender, "Unknown user team subcommand.", "§c");
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Unknown user subcommand: " + userSub);
            return true;
        }
    }

    private boolean handleTeamCreate(CommandSender sender, String[] args) {
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Handling team create command for " + sender.getName() + ": " + String.join(" ", args));
        if (args.length < 4) {
            sendMessage(sender, "Usage: /nf team create <team-name> <team-color>", "§c");
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Invalid arguments for create, expected at least 4");
            return true;
        }

        String teamName = args[2].toLowerCase();
        String colorName = args[3].toUpperCase();
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

        String message = "Team '" + teamName + "' created with color " + colorName;
        sendMessage(sender, message, getLegacyColor(colorName));
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Created team '" + teamName + "' with color " + colorName);
        return true;
    }

    private boolean isValidColor(String colorName) {
        List<String> validColors = Arrays.asList("RED", "BLUE", "GREEN", "YELLOW", "AQUA", "DARK_PURPLE", "GOLD", "LIGHT_PURPLE", "WHITE");
        return validColors.contains(colorName);
    }

    private String getLegacyColor(String color) {
        switch (color.toUpperCase()) {
            case "RED": return "§c";
            case "GOLD": return "§6";
            case "YELLOW": return "§e";
            case "GREEN": return "§a";
            case "AQUA": return "§b";
            case "BLUE": return "§9";
            case "DARK_PURPLE": return "§5";
            case "LIGHT_PURPLE": return "§d";
            case "WHITE": return "§f";
            default: return "§f";
        }
    }

    private boolean handleTeamList(CommandSender sender) {
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Handling team list command for " + sender.getName());
        List<TeamsManager.Team> teams = (List<TeamsManager.Team>) teamsManager.getTeams();
        if (teams.isEmpty()) {
            sendMessage(sender, "No teams available.", "§e");
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] No teams available for " + sender.getName());
            return true;
        }
        sendMessage(sender, "Teams:", "§6");
        for (TeamsManager.Team team : teams) {
            String message = "- " + team.getName();
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
        String teamName = args[2].toLowerCase();
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
        String teamName = args[1].toLowerCase();
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
        Player target = Bukkit.getPlayer(args[4]);
        if (target == null) {
            sendMessage(sender, "Player not found or offline.", "§c");
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Player '" + args[4] + "' not found or offline");
            return true;
        }
        ItemStack axe = createTeamBlockSelector(teamName);
        target.getInventory().addItem(axe);
        sendMessage(sender, "Given team block selector for team '" + teamName + "' to " + target.getName(), "§a");
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Gave block selector for team '" + teamName + "' to " + target.getName());
        return true;
    }

    private ItemStack createTeamBlockSelector(String teamName) {
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Creating team block selector for team '" + teamName + "'");
        ItemStack axe = new ItemStack(Material.IRON_AXE);
        ItemMeta meta = axe.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6" + TEAM_BLOCK_SELECTOR_NAME);
            if (isPre114()) {
                meta.setLore(Collections.singletonList("Team: " + teamName));
            } else {
                setPersistentTeamName(meta, teamName);
            }
            axe.setItemMeta(meta);
        }
        return axe;
    }

    private boolean handleTeamSetBlock(CommandSender sender, String[] args) {
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Handling team setblock command for " + sender.getName() + ": " + String.join(" ", args));
        if (!(sender instanceof Player)) {
            sendMessage(sender, "Only players can use this command.", "§c");
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Non-player sender attempted setblock command");
            return true;
        }
        Player player = (Player) sender;
        if (args.length != 6) {
            sendMessage(sender, "Usage: /nf team <team-name> setblock <x> <y> <z>", "§c");
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Invalid arguments for setblock, expected 6");
            return true;
        }
        String teamName = args[1].toLowerCase();
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
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Invalid coordinates: " + Arrays.toString(Arrays.copyOfRange(args, 3, args.length)));
            return true;
        }
        World world = player.getWorld();
        Location loc = new Location(world, x, y, z);
        Block block = world.getBlockAt(loc);
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
        UUID uuid = target.getUniqueId();
        ArrayList<String> playerTeams = new ArrayList<String>();
        for (TeamsManager.Team team : teamsManager.getTeams()) {
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
        for (String team : playerTeams) {
            sendMessage(sender, "- " + team, "§f");
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Listed team '" + team + "' for " + target.getName());
        }
        return true;
    }

    public void onTeamBlockSelectorUse(Player player, Block block) {
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Player " + player.getName() + " used team block selector on block at " + formatLocation(block.getLocation()));
        if (block.getType() != teamsManager.getTeamBlockMaterial()) {
            sendMessage(player, "This block is not the configured team block (" + teamsManager.getTeamBlockMaterial() + ").", "§c");
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        String teamName;
        if (isPre114()) {
            List<String> lore = meta.getLore();
            if (lore == null || lore.isEmpty() || !lore.get(0).startsWith("Team: ")) {
                sendMessage(player, "This selector is invalid (missing team info)", "§c");
                return;
            }
            teamName = lore.get(0).substring(6);
        } else {
            teamName = getPersistentTeamName(meta);
            if (teamName == null) {
                sendMessage(player, "This selector is invalid (missing team info)", "§c");
                return;
            }
        }

        if (!teamsManager.teamExists(teamName)) {
            sendMessage(player, "Team '" + teamName + "' no longer exists", "§c");
            return;
        }

        teamsManager.setLodestone(teamName, block.getLocation());
        sendMessage(player, "Team lodestone for '" + teamName + "' set at " +
                block.getX() + ", " + block.getY() + ", " + block.getZ(), "§a");
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
            scoreboardTeam.setDisplayName(teamName);
            ChatColor chatColor = parseChatColor(team.getColor());
            if (chatColor != null) {
                scoreboardTeam.setColor(chatColor);
                ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Set scoreboard team color to " + chatColor + " for team '" + teamName + "'");
            }
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Registered new scoreboard team '" + teamName + "' with color " + team.getColor());
        }

        for (UUID memberUUID : team.getMembers()) {
            Player member = Bukkit.getPlayer(memberUUID);
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
        if (colorName == null) return null;
        colorName = colorName.toUpperCase();

        if (colorName.equals("RED")) return ChatColor.RED;
        if (colorName.equals("GOLD")) return ChatColor.GOLD;
        if (colorName.equals("YELLOW")) return ChatColor.YELLOW;
        if (colorName.equals("GREEN")) return ChatColor.GREEN;
        if (colorName.equals("AQUA")) return ChatColor.AQUA;
        if (colorName.equals("BLUE")) return ChatColor.BLUE;
        if (colorName.equals("DARK_PURPLE")) return ChatColor.DARK_PURPLE;
        if (colorName.equals("LIGHT_PURPLE")) return ChatColor.LIGHT_PURPLE;
        if (colorName.equals("WHITE")) return ChatColor.WHITE;

        return null;
    }

    private boolean handleTeamMetaColor(CommandSender sender, String[] args) {
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Handling team meta color command for " + sender.getName() + ": " + String.join(" ", args));
        if (args.length < 5) {
            sendMessage(sender, "Usage: /nf team meta <team-name> color <get|set> [color]", "§c");
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Invalid arguments for meta color, expected at least 5");
            return true;
        }

        String teamName = args[2].toLowerCase();
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Team name: " + teamName);
        if (!teamsManager.teamExists(teamName)) {
            sendMessage(sender, "Team '" + teamName + "' does not exist.", "§c");
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Team '" + teamName + "' does not exist");
            return true;
        }

        String action = args[4].toLowerCase();
        ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Color action: " + action);
        TeamsManager.Team team = teamsManager.getTeam(teamName);

        if (action.equals("get")) {
            sendMessage(sender, "Team '" + teamName + "' color: " + team.getColor(), getLegacyColor(team.getColor()));
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Retrieved color " + team.getColor() + " for team '" + teamName + "'");
        } else if (action.equals("set")) {
            if (args.length < 6) {
                sendMessage(sender, "Usage: /nf team meta <team-name> color set <color>", "§c");
                ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Invalid arguments for meta color set, expected 6");
                return true;
            }

            String newColorName = args[5].toUpperCase();
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] New color: " + newColorName);

            if (!isValidColor(newColorName)) {
                sendMessage(sender, "Invalid color. Valid colors: RED, BLUE, GREEN, YELLOW, AQUA, DARK_PURPLE, GOLD, LIGHT_PURPLE, WHITE", "§c");
                ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Invalid color: " + newColorName);
                return true;
            }

            team.setColor(newColorName);
            sendMessage(sender, "Team '" + teamName + "' color updated to " + newColorName, getLegacyColor(newColorName));
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Updated color for team '" + teamName + "' to " + newColorName);

            for (UUID memberUUID : team.getMembers()) {
                Player member = Bukkit.getPlayer(memberUUID);
                if (member != null && member.isOnline()) {
                    updatePlayerNametagColor(member, teamName);
                    ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Updated nametag for " + member.getName() + " in team '" + teamName + "'");
                }
            }
        } else {
            sendMessage(sender, "Unknown color action. Use get or set.", "§c");
            ((NakedAndAfraid) plugin).debugLog("[TeamCommands] Unknown color action: " + action);
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