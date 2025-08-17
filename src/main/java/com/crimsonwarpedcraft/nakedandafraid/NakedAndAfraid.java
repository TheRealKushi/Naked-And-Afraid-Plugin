package com.crimsonwarpedcraft.nakedandafraid;

import com.crimsonwarpedcraft.nakedandafraid.listeners.*;
import com.crimsonwarpedcraft.nakedandafraid.spawn.SpawnManager;
import com.crimsonwarpedcraft.nakedandafraid.team.TeamCommands;
import com.crimsonwarpedcraft.nakedandafraid.team.TeamsManager;
import com.crimsonwarpedcraft.nakedandafraid.util.TeleportHelper;
import io.papermc.lib.PaperLib;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

/**
 * Main plugin class for Naked And Afraid.
 */
public class NakedAndAfraid extends JavaPlugin {

  private ChatRestrictionListener chatRestrictionListener;
  private ArmorDamageListener armorDamageListener;
  private JoinQuitMessageSuppressor joinQuitMessageSuppressor;
  private TabListClearer tabListClearer;

  private final String PACK_URL = "https://github.com/TheRealKushi/Deathcraft-Assets/releases/download/release-1.0.2/NakedAndAfraid-1.0.2.zip";
  private static final String PACK_HASH = "3e930d7cbfbd7dcb113dcc79820abe13e1601835";

  private SpawnManager spawnManager;
  private TeleportHelper teleportHelper;
  private boolean teleportOnCountdownEnd;
  private String multipleSpawnPriority;
  private TeamsManager teamsManager;
  private TeamCommands teamCommands;

  @Override
  public void onEnable() {
    PaperLib.suggestPaper(this);
    saveDefaultConfig();
    reloadListeners();

    teleportOnCountdownEnd = getConfig().getBoolean("teleport-on-countdown-end", false);
    multipleSpawnPriority = getConfig().getString("multiple-spawn-priority", "FIRST").toUpperCase();
    teamsManager = new TeamsManager(this);
    teamCommands = new TeamCommands(teamsManager, this);

    getServer().getPluginManager().registerEvents(new GlobalDeathSoundListener(this), this);
    getServer().getPluginManager().registerEvents(new TeamListener(teamsManager, teamCommands), this);

    spawnManager = new SpawnManager(this);
    spawnManager.loadSpawns();

    teleportHelper = new TeleportHelper(this);

    if (getConfig().getBoolean("disable-tab", true)) {
      TabListClearer.register(this);
      getLogger().info("Naked And Afraid - Tab Hider Enabled.");
    }

    ConsoleCommandSender console = Bukkit.getServer().getConsoleSender();

    console.sendMessage(" ");
    console.sendMessage(Component.text("__   ____  _______ ").color(NamedTextColor.GOLD));
    console.sendMessage(Component.text("| \\ | | | | ____||").color(NamedTextColor.GOLD));
    console.sendMessage(Component.text("|  \\| | | | ||_   ").color(NamedTextColor.GOLD));
    console.sendMessage(Component.text("| |\\| | | | __||  ").color(NamedTextColor.GOLD));
    console.sendMessage(Component.text("|_| \\_|_| |_||    ").color(NamedTextColor.GOLD));
    console.sendMessage(Component.empty());
    console.sendMessage(Component.text("NakedAndAfraid Plugin ").color(NamedTextColor.GOLD)
            .append(Component.text("v" + this.getDescription().getVersion()).color(NamedTextColor.DARK_RED)));
    console.sendMessage(Component.text("Running on ").color(NamedTextColor.YELLOW)
            .append(Component.text(Bukkit.getServer().getName()).color(NamedTextColor.AQUA))
            .append(Component.text(" " + Bukkit.getServer().getVersion()).color(NamedTextColor.WHITE)));
    console.sendMessage(Component.empty());
    console.sendMessage(Component.text("Plugin enabled successfully!").color(NamedTextColor.GREEN));
    console.sendMessage(Component.empty());
  }

  /**
   * Reload listeners based on config settings.
   */
  public void reloadListeners() {
    // Unregister old listeners if any
    if (chatRestrictionListener != null) {
      getServer().getPluginManager().callEvent(
              new org.bukkit.event.server.PluginDisableEvent(this)
      );
    }
    if (armorDamageListener != null) {
      getServer().getPluginManager().callEvent(
              new org.bukkit.event.server.PluginDisableEvent(this)
      );
    }
    if (joinQuitMessageSuppressor != null) {
      getServer().getPluginManager().callEvent(
              new org.bukkit.event.server.PluginDisableEvent(this)
      );
    }

    // Register listeners based on config
    if (getConfig().getBoolean("disable-chat", true)) {
      chatRestrictionListener = new ChatRestrictionListener();
      getServer().getPluginManager().registerEvents(chatRestrictionListener, this);
      getLogger().info("Naked And Afraid - Chat Restriction Enabled.");
    } else {
      chatRestrictionListener = null;
    }

    if (getConfig().getBoolean("disable-tab", true)) {
      TabListClearer.register(this); // register the packet listener
      getLogger().info("Naked And Afraid - Tab Hider Enabled.");
    }

    if (getConfig().getBoolean("armor-damage.enabled", true)) {
      armorDamageListener = new ArmorDamageListener(this);
      getServer().getPluginManager().registerEvents(armorDamageListener, this);
      getLogger().info("Naked And Afraid - Armor Damage Enabled.");
    } else {
      armorDamageListener = null;
    }

    if (getConfig().getBoolean("disable-join-quit-messages", true)) {
      joinQuitMessageSuppressor = new JoinQuitMessageSuppressor();
      getServer().getPluginManager().registerEvents(joinQuitMessageSuppressor, this);
      getLogger().info("Naked And Afraid - Message Disabling Enabled.");
    } else {
      joinQuitMessageSuppressor = null;
    }
  }

  public boolean isTeleportOnCountdownEnd() {
    return teleportOnCountdownEnd;
  }

  public String getMultipleSpawnPriority() {
    return multipleSpawnPriority;
  }

  @EventHandler
  public void onPlayerJoin(PlayerJoinEvent event) {
    Player player = event.getPlayer();

    try {
      ResourcePackInfo packInfo = ResourcePackInfo.resourcePackInfo()
              .uri(new URI(PACK_URL))   // Set URL here
              .hash(PACK_HASH)          // Set hash here
              .build();

      ResourcePackRequest request = ResourcePackRequest.resourcePackRequest()
              .packs(packInfo)
              .replace(true)
              .required(true)
              .prompt(Component.text("Please accept the NakedAndAfraid resource pack!"))
              .build();

      player.sendResourcePacks(request);
    } catch (URISyntaxException e) {
      getLogger().severe("Invalid resource pack URL: " + PACK_URL);
      getLogger().log(Level.SEVERE, "Exception while sending resource pack", e);
    }
  }

  @Override
  public boolean onCommand(final @NotNull CommandSender sender,
                           final @NotNull Command command,
                           final String label,
                           final String @NotNull [] args) {

    if (!(label.equalsIgnoreCase("nf") || label.equalsIgnoreCase("nakedafraid"))) {
      return false;
    }

    if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
      int page = 1;
      if (args.length >= 2) {
        try {
          page = Integer.parseInt(args[1]);
        } catch (NumberFormatException ignored) {
          sender.sendMessage("§cInvalid help page number. Showing page 1.");
        }
      }
      sendHelpMessage(sender, page);
      return true;
    }

    if (args[0].equalsIgnoreCase("reloadconfig")) {
      if (!sender.hasPermission("nakedandafraid.reload")) {
        sender.sendMessage("§cYou don't have permission to do that.");
        return true;
      }
      reloadConfig();
      spawnManager.loadSpawns();
      sender.sendMessage("§aNaked and Afraid config reloaded.");
      return true;
    }

    if (args[0].equalsIgnoreCase("spawn")) {
      return spawnManager.handleCommand(sender, args);
    }

    if (args[0].equalsIgnoreCase("team")) {
      return teamCommands.handleTeamCommand(sender, args);
    }

    if (args[0].equalsIgnoreCase("user")) {
      return teamCommands.handleUserCommand(sender, args);
    }

    sender.sendMessage("§cUnknown subcommand. Use /nf help for commands.");
    return true;
  }

  public TeleportHelper getTeleportHelper() {
    return teleportHelper;
  }

  @Override
  public List<String> onTabComplete(@NotNull CommandSender sender,
                                    Command command,
                                    @NotNull String alias,
                                    String @NotNull [] args) {
    if (command.getName().equalsIgnoreCase("nf") || command.getName().equalsIgnoreCase("nakedafraid")) {
      if (args.length == 1) {
        // Root-level completions: help, reloadconfig, spawn, team, user
        List<String> completions = new ArrayList<>();
        completions.add("help");
        if (sender.hasPermission("nakedandafraid.reload")) {
          completions.add("reloadconfig");
        }
        completions.add("spawn");
        completions.add("team");
        completions.add("user");
        return completions;
      }

      // Handle tab completion for /nf spawn ...
      if (args[0].equalsIgnoreCase("spawn")) {
        List<String> spawnSubs = List.of("create", "rename", "remove", "list", "tp", "tpall");

        if (args.length == 2) {
          String partial = args[1].toLowerCase();
          List<String> result = new ArrayList<>();
          for (String sub : spawnSubs) {
            if (sub.startsWith(partial)) result.add(sub);
          }
          return result;
        }

        SpawnManager spawnManager = this.spawnManager;
        if (spawnManager == null) return Collections.emptyList();

        if (args.length == 3) {
          String sub = args[1].toLowerCase();

          if (sub.equals("rename") || sub.equals("remove") || sub.equals("tp")) {
            String partial = args[2].toLowerCase();
            List<String> matchingSpawns = new ArrayList<>();
            for (String spawnName : spawnManager.getSpawns().keySet()) {
              if (spawnName.toLowerCase().startsWith(partial)) matchingSpawns.add(spawnName);
            }
            return matchingSpawns;
          }
          if (sub.equals("create")) {
            return Collections.emptyList();
          }
        }

        if (args.length == 4) {
          String sub = args[1].toLowerCase();
          if (sub.equals("create") || sub.equals("tp")) {
            String partialPlayer = args[3].toLowerCase();
            List<String> players = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
              if (p.getName().toLowerCase().startsWith(partialPlayer)) {
                players.add(p.getName());
              }
            }
            return players;
          }
        }
        return Collections.emptyList();
      }

      // Tab completion for /nf team ...
      if (args[0].equalsIgnoreCase("team")) {
        List<String> teamSubs = List.of("create", "remove", "list", "block", "setblock");

        if (args.length == 2) {
          String partial = args[1].toLowerCase();
          List<String> result = new ArrayList<>();
          for (String sub : teamSubs) {
            if (sub.startsWith(partial)) result.add(sub);
          }
          // Also suggest team names for existing teams for 2nd arg if user is starting with team name (for block/setblock)
          for (var team : teamsManager.getTeams()) {
            if (team.getName().toLowerCase().startsWith(partial)) result.add(team.getName());
          }
          return result;
        }

        // /nf team <team-name> <subcommand>
        if (args.length == 3) {
          String possibleTeam = args[1].toLowerCase();
          // If user typed a team name, suggest block/setblock for 3rd arg
          if (teamsManager.teamExists(possibleTeam)) {
            List<String> result = new ArrayList<>();
            String partial = args[2].toLowerCase();
            for (String sub : List.of("block", "setblock")) {
              if (sub.startsWith(partial)) result.add(sub);
            }
            return result;
          }
          // If user is doing remove or setblock, suggest team names
          if (args[1].equalsIgnoreCase("remove") || args[1].equalsIgnoreCase("setblock")) {
            String partial = args[2].toLowerCase();
            List<String> matchingTeams = new ArrayList<>();
            for (var team : teamsManager.getTeams()) {
              if (team.getName().toLowerCase().startsWith(partial)) matchingTeams.add(team.getName());
            }
            return matchingTeams;
          }
          // If block, suggest team names
          if (args[1].equalsIgnoreCase("block")) {
            String partial = args[2].toLowerCase();
            List<String> matchingTeams = new ArrayList<>();
            for (var team : teamsManager.getTeams()) {
              if (team.getName().toLowerCase().startsWith(partial)) matchingTeams.add(team.getName());
            }
            return matchingTeams;
          }
        }

        // /nf team <team-name> block selector <player>
        if (args.length == 4 && teamsManager.teamExists(args[1].toLowerCase()) && args[2].equalsIgnoreCase("block")) {
          String partial = args[3].toLowerCase();
          if ("selector".startsWith(partial)) return List.of("selector");
        }
        if (args.length == 5 && teamsManager.teamExists(args[1].toLowerCase()) && args[2].equalsIgnoreCase("block") && args[3].equalsIgnoreCase("selector")) {
          String partialPlayer = args[4].toLowerCase();
          List<String> players = new ArrayList<>();
          for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().toLowerCase().startsWith(partialPlayer)) players.add(p.getName());
          }
          return players;
        }

        // /nf team <team-name> setblock <coords>
        if (args.length >= 4 && teamsManager.teamExists(args[1].toLowerCase()) && args[2].equalsIgnoreCase("setblock")) {
          // Tab complete nothing for coords
          return Collections.emptyList();
        }
      }

      // Tab completion for /nf user <player> team <add|remove|list> [team]
      if (args[0].equalsIgnoreCase("user")) {
        if (args.length == 2) {
          String partialPlayer = args[1].toLowerCase();
          List<String> players = new ArrayList<>();
          for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().toLowerCase().startsWith(partialPlayer)) players.add(p.getName());
          }
          return players;
        }
        if (args.length == 3) {
          if ("team".startsWith(args[2].toLowerCase())) return List.of("team");
        }
        if (args.length == 4 && args[2].equalsIgnoreCase("team")) {
          List<String> teamUserSubs = List.of("add", "remove", "list");
          String partial = args[3].toLowerCase();
          List<String> result = new ArrayList<>();
          for (String sub : teamUserSubs) {
            if (sub.startsWith(partial)) result.add(sub);
          }
          return result;
        }
        if (args.length == 5 && args[2].equalsIgnoreCase("team") && (
                args[3].equalsIgnoreCase("add") || args[3].equalsIgnoreCase("remove"))
        ) {
          String partial = args[4].toLowerCase();
          List<String> matchingTeams = new ArrayList<>();
          for (var team : teamsManager.getTeams()) {
            if (team.getName().toLowerCase().startsWith(partial)) matchingTeams.add(team.getName());
          }
          return matchingTeams;
        }
      }
    }
    return Collections.emptyList();
  }

  /**
   * Sends help message for /nf commands.
   *
   * @param sender The command sender.
   */
  private final int HELP_LINES_PER_PAGE = 6; // Customize as you want

  private void sendHelpMessage(CommandSender sender, int page) {
    List<String> helpLines = List.of(
            "§e/nf help §7- Show this help message",
            "§e/nf §7- Alias for /nf help",
            "§e/nf reloadconfig §7- Reload the plugin config",
            "§e/nf spawn create (spawn-name) (target-player) §7- Define a spawn for a player",
            "§e/nf spawn remove (spawn-name) §7- Delete a spawn",
            "§e/nf spawn list §7- List all spawns",
            "§e/nf spawn tp (spawn-name) (player) §7- Teleport a player to a spawn",
            "§e/nf spawn tpall §7- Teleport all players to their spawns at once",
            "§e/nf team create (team-name) §7- Define a new team",
            "§e/nf team remove (team-name) §7- Delete an existing team",
            "§e/nf team list §7- List all existing teams",
            "§e/nf user (player name) team add (team-name) §7- Add a player to a team",
            "§e/nf user (player name) team remove (team-name) §7- Remove a player from a team",
            "§e/nf user (player name) team list §7- List all teams a player is in"
    );

    int totalPages = (int) Math.ceil(helpLines.size() / (double) HELP_LINES_PER_PAGE);

    if (page < 1) page = 1;
    if (page > totalPages) page = totalPages;

    sender.sendMessage("§6==== NakedAndAfraid Help ====");

    int startIndex = (page - 1) * HELP_LINES_PER_PAGE;
    int endIndex = Math.min(startIndex + HELP_LINES_PER_PAGE, helpLines.size());

    for (int i = startIndex; i < endIndex; i++) {
      sender.sendMessage(helpLines.get(i));
    }

    sender.sendMessage("§7Page §e" + page + " §7of §e" + totalPages);
  }
}