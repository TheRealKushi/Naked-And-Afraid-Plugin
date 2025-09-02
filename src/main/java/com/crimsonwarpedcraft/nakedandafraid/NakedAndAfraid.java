package com.crimsonwarpedcraft.nakedandafraid;

import com.crimsonwarpedcraft.nakedandafraid.listeners.*;
import com.crimsonwarpedcraft.nakedandafraid.spawn.SpawnManager;
import com.crimsonwarpedcraft.nakedandafraid.team.TeamCommands;
import com.crimsonwarpedcraft.nakedandafraid.team.TeamsManager;
import com.crimsonwarpedcraft.nakedandafraid.util.TeleportHelper;
import com.crimsonwarpedcraft.nakedandafraid.util.VersionChecker;
import io.papermc.lib.PaperLib;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Main class for the NakedAndAfraid plugin.
 * <p>
 * This plugin handles features such as chat restriction, tab list hiding, armor damage,
 * spawn management, and team management. The plugin will automatically disable the tablist hiding
 * if ProtocolLib is missing.
 * </p>
 * <p>
 * Configuration options include enabling/disabling chat restriction, tab hiding, armor damage,
 * join/quit message suppression, teleport behavior, and spawn priority handling.
 * </p>
 */
public class NakedAndAfraid extends JavaPlugin {

  /** Listener for restricting global player chat, only allowing Server OPs to send messages. */
  private ChatRestrictionListener chatRestrictionListener;

  /** Listener for armor damage handling, which deals damage to a player when they wear any armor piece. */
  private ArmorDamageListener armorDamageListener;

  /** Listener for suppressing chat player join/quit messages. */
  private JoinQuitMessageSuppressor joinQuitMessageSuppressor;

  /** Manages player spawns saving, loading and commands. */
  private SpawnManager spawnManager;

  /** Utility for handling teleportation logic, including the countdown and freeze. */
  private TeleportHelper teleportHelper;

  /** Whether players should teleport when countdown ends or when command is ran. */
  private boolean teleportOnCountdownEnd;

  /** Priority when multiple spawns set for the same player exist. */
  private String multipleSpawnPriority;

  /** Manages team information. */
  private TeamsManager teamsManager;

  /** Handles team-related commands. */
  private TeamCommands teamCommands;

  private TabListClearer tabListClearer;

  @Override
  public void onEnable() {
    PaperLib.suggestPaper(this);

    // Load default config and initialize listeners
    saveDefaultConfig();

    // Add worlds to config.yml
    setWorldList();

    // Reloads all plugin listeners
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

    // Initialize tab hider if ProtocolLib is present
    if (getConfig().getBoolean("disable-tab", true) &&
            Bukkit.getPluginManager().getPlugin("ProtocolLib") != null) {
      tabListClearer = new TabListClearer(this);
      tabListClearer.enable();
      getLogger().info("Naked And Afraid - Tab Hider Enabled.");
    } else {
      getLogger().warning("ProtocolLib not found or tab hiding disabled.");
    }

    getServer().getPluginManager().registerEvents(new com.crimsonwarpedcraft.nakedandafraid.listeners.VersionNotifyListener(this), this);

    logStartupInfo();
  }

  /**
   * Reloads all listeners according to the plugin configuration.
   * Handles enabling/disabling chat, tab, armor, and join/quit message features.
   */
  public void reloadListeners() {
    if (chatRestrictionListener != null) {
      getServer().getPluginManager().callEvent(new org.bukkit.event.server.PluginDisableEvent(this));
    }
    if (armorDamageListener != null) {
      getServer().getPluginManager().callEvent(new org.bukkit.event.server.PluginDisableEvent(this));
    }
    if (joinQuitMessageSuppressor != null) {
      getServer().getPluginManager().callEvent(new org.bukkit.event.server.PluginDisableEvent(this));
    }

    // Chat restriction
    if (getConfig().getBoolean("disable-chat", true)) {
      chatRestrictionListener = new ChatRestrictionListener();
      getServer().getPluginManager().registerEvents(chatRestrictionListener, this);
      getLogger().info("Naked And Afraid - Chat Restriction Enabled.");
    } else {
      chatRestrictionListener = null;
    }

    boolean disableTab = getConfig().getBoolean("disable-tab", true);

    if (tabListClearer == null && Bukkit.getPluginManager().getPlugin("ProtocolLib") != null) {
      tabListClearer = new TabListClearer(this);
    }

    if (tabListClearer != null) {
      if (disableTab && !tabListClearer.isEnabled()) {
        tabListClearer.enable();
        getLogger().info("Naked And Afraid - Tab Hider Enabled.");
      } else if (!disableTab && tabListClearer.isEnabled()) {
        tabListClearer.disable();
        getLogger().info("Naked And Afraid - Tab Hider Disabled.");
      }
    } else if (disableTab) {
      getLogger().warning("ProtocolLib not found! Tab Hider is disabled.");
    }

    // Armor damage
    if (getConfig().getBoolean("armor-damage.enabled", true)) {
      armorDamageListener = new ArmorDamageListener(this);
      getServer().getPluginManager().registerEvents(armorDamageListener, this);
      getLogger().info("Naked And Afraid - Armor Damage Enabled.");
    } else {
      armorDamageListener = null;
    }

    // Join/quit message suppression
    if (getConfig().getBoolean("disable-join-quit-messages", true)) {
      joinQuitMessageSuppressor = new JoinQuitMessageSuppressor();
      getServer().getPluginManager().registerEvents(joinQuitMessageSuppressor, this);
      getLogger().info("Naked And Afraid - Message Disabling Enabled.");
    } else {
      joinQuitMessageSuppressor = null;
    }
  }

  /**
   * Automatically adds all existing worlds to the config under `enabled-worlds` variable if not already present.
   * Each world is enabled (true) by default.
   */
  private void setWorldList() {
    var configSection = getConfig().getConfigurationSection("enabled-worlds");

    if (configSection == null) {
      configSection = getConfig().createSection("enabled-worlds");
    }

    var configWorlds = configSection.getKeys(false);

    for (var world : Bukkit.getWorlds()) {
      String path = "enabled-worlds." + world.getName();
      if (!getConfig().contains(path)) {
        getConfig().set(path, true);
      }
    }

    for (String worldName : configWorlds) {
      if (Bukkit.getWorld(worldName) == null) {
        getConfig().set("enabled-worlds." + worldName, null);
      }
    }

    saveConfig();
  }

  /**
   * Checks if teleport should occur when countdown ends.
   *
   * @return true if teleport on countdown end is enabled, false otherwise.
   */
  public boolean isTeleportOnCountdownEnd() {
    return teleportOnCountdownEnd;
  }

  /**
   * Retrieves the configured multiple spawn priority.
   *
   * @return The spawn priority setting, e.g., "FIRST".
   */
  @NotNull
  public String getMultipleSpawnPriority() {
    return multipleSpawnPriority;
  }

  /**
   * Defines commands and subcommands for the plugin.
   */
  @Override
  public boolean onCommand(final @NotNull CommandSender sender,
                           final @NotNull Command command,
                           final @NotNull String label,
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

    switch (args[0].toLowerCase()) {
      case "reloadconfig" -> {
        if (!sender.hasPermission("nakedandafraid.reload")) {
          sender.sendMessage("§cYou don't have permission to execute this command.");
          return true;
        }
        reloadConfig();
        spawnManager.loadSpawns();
        sender.sendMessage("§aNaked and Afraid config reloaded.");
        return true;
      }
      case "spawn" -> {
        if (!sender.hasPermission("nakedandafraid.spawn")) {
          sender.sendMessage("§cYou don't have permission to execute this command.");
          return true;
        }
        return spawnManager.handleCommand(sender, args);
      }
      case "team" -> {
        if (!sender.hasPermission("nakedandafraid.team")) {
          sender.sendMessage("§cYou don't have permission to execute this command.");
          return true;
        }
        return teamCommands.handleTeamCommand(sender, args);
      }
      case "user" -> {
        if (!sender.hasPermission("nakedandafraid.user")) {
          sender.sendMessage("§cYou don't have permission to execute this command.");
          return true;
        }
        return teamCommands.handleUserCommand(sender, args);
      }
      default -> sender.sendMessage("§cUnknown subcommand. Use /nf help for commands.");
    }
    return true;
  }

  /**
   * Handles tab-completion for NakedAndAfraid's commands and subcommands.
   */
  @Override
  public List<String> onTabComplete(@NotNull CommandSender sender,
                                    @NotNull Command command,
                                    @NotNull String alias,
                                    String @NotNull [] args) {

    if (!(command.getName().equalsIgnoreCase("nf") || command.getName().equalsIgnoreCase("nakedafraid"))) {
      return null;
    }

    if (args.length == 1) {
      return List.of("help", "reloadconfig", "spawn", "team", "user");
    }

    switch (args[0].toLowerCase()) {
      case "spawn" -> {
        if (args.length == 2) {
          return List.of("create", "rename", "remove", "list", "tp", "tpall");
        }
        if (args.length == 3) {
          if (args[1].equalsIgnoreCase("create")) return List.of();
          if (args[1].equalsIgnoreCase("rename") || args[1].equalsIgnoreCase("remove") || args[1].equalsIgnoreCase("tp")) {
            return spawnManager.getSpawns().keySet().stream().toList();
          }
        }
        if (args.length == 4) {
          if (args[1].equalsIgnoreCase("create")) return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
          if (args[1].equalsIgnoreCase("rename")) return List.of();
          if (args[1].equalsIgnoreCase("tp")) return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
      }

      case "team" -> {
        if (args.length == 2) {
          return List.of("create", "remove", "list", "block", "setblock", "meta");
        }
        if (args.length == 3) {
          if (args[1].equalsIgnoreCase("remove") || args[1].equalsIgnoreCase("setblock") || args[1].equalsIgnoreCase("block")) {
            return teamsManager.getTeams().stream().map(TeamsManager.Team::getName).toList();
          }
          if (args[1].equalsIgnoreCase("create") || args[1].equalsIgnoreCase("meta")) {
            return List.of();
          }
        }
        if (args.length == 4) {
          if (args[1].equalsIgnoreCase("create")) {
            return ValidTeamColors().stream()
                    .filter(c -> c.startsWith(args[3].toUpperCase()))
                    .toList();
          }
          if (args[1].equalsIgnoreCase("block") && args[2].equalsIgnoreCase("selector")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
          }
          if (args[1].equalsIgnoreCase("setblock") && sender instanceof Player player) {
            return List.of(String.valueOf(player.getLocation().getBlockX()));
          }
          if (args[1].equalsIgnoreCase("meta") && args[3].equalsIgnoreCase("color")) {
            return List.of("get", "set");
          }
        }
        if (args.length == 5) {
          if (args[1].equalsIgnoreCase("setblock") && sender instanceof Player player) {
            return List.of(String.valueOf(player.getLocation().getBlockY()));
          }
          if (args[1].equalsIgnoreCase("meta") && args[3].equalsIgnoreCase("color") && args[4].equalsIgnoreCase("set")) {
            return ValidTeamColors().stream()
                    .filter(c -> c.startsWith(args[5].toUpperCase()))
                    .toList();
          }
        }
        if (args.length == 6 && args[1].equalsIgnoreCase("setblock") && sender instanceof Player player) {
          return List.of(String.valueOf(player.getLocation().getBlockZ()));
        }
      }

      case "user" -> {
        if (args.length == 2) {
          return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
        if (args.length == 3 && args[2].equalsIgnoreCase("team")) {
          return List.of("add", "remove", "list");
        }
        if (args.length == 4 && args[2].equalsIgnoreCase("team") &&
                (args[3].equalsIgnoreCase("add") || args[3].equalsIgnoreCase("remove"))) {
          return teamsManager.getTeams().stream().map(TeamsManager.Team::getName).toList();
        }
      }
    }

    return List.of();
  }

  /**
   * Retrieves the teleport helper instance.
   *
   * @return The {@link TeleportHelper} for this plugin.
   */
  @NotNull
  public TeleportHelper getTeleportHelper() {
    return teleportHelper;
  }

  /**
   * Sends paginated help message to the command sender.
   *
   * @param sender The command sender to display the help message to.
   * @param page   Page number to display (1-based).
   */
  private void sendHelpMessage(@NotNull CommandSender sender, int page) {
    List<String> helpLines = List.of(
            "§e/nf help §7- Show this help message",
            "§e/nf §7- Alias for /nf help",
            "§e/nf reloadconfig §7- Reload the plugin config",
            "§e/nf spawn create (spawn-name) (target-player) §7- Define a spawn for a player",
            "§e/nf spawn remove (spawn-name) §7- Delete a spawn",
            "§e/nf spawn list §7- List all spawns",
            "§e/nf spawn tp (spawn-name) (player) §7- Teleport a player to a spawn",
            "§e/nf spawn tpall §7- Teleport all players to their spawns at once",
            "§e/nf team create (team-name) (team-color) §7- Define a new team with a specific color",
            "§e/nf team remove (team-name) §7- Delete an existing team",
            "§e/nf team list §7- List all existing teams",
            "§e/nf team (team-name) meta color get §7- Get the current color of a team",
            "§e/nf team (team-name) meta color set (color) §7- Change the color of an existing team",
            "§e/nf user (player name) team add (team-name) §7- Add a player to a team",
            "§e/nf user (player name) team remove (team-name) §7- Remove a player from a team",
            "§e/nf user (player name) team list §7- List all teams a player is in"
    );

    int HELP_LINES_PER_PAGE = 6;
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

  private List<String> ValidTeamColors() {
    return List.of(
            "RED", "BLUE", "GREEN", "YELLOW", "AQUA",
            "DARK_PURPLE", "GOLD", "LIGHT_PURPLE", "WHITE"
    );
  }

  /**
   * Sends formatted plugin startup information to the console.
   */
  private void logStartupInfo() {
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
    console.sendMessage(Component.text("NakedAndAfraid Plugin enabled successfully!").color(NamedTextColor.GREEN));
    console.sendMessage(Component.empty());

    String currentVersion = this.getDescription().getVersion();
    String latestVersion = VersionChecker.getLatestVersion();

    if (latestVersion != null && !latestVersion.equalsIgnoreCase(currentVersion)) {
      this.getLogger().warning(
              Component.text("[NakedAndAfraid] ").color(NamedTextColor.GOLD)
                      .append(Component.text("There is a new Naked And Afraid Plugin version available for download: "
                                      + latestVersion + " (Current: " + currentVersion + ")")
                              .color(NamedTextColor.RED))
                      .toString()
      );

      console.sendMessage(
              Component.text("[NakedAndAfraid] ").color(NamedTextColor.GOLD)
                      .append(
                              Component.text("Download it here: ")
                                      .color(NamedTextColor.RED)
                      )
                      .append(
                              Component.text("https://modrinth.com/plugin/naked-and-afraid-plugin/versions")
                                      .color(NamedTextColor.RED)
                                      .clickEvent(ClickEvent.openUrl("https://modrinth.com/plugin/naked-and-afraid-plugin/versions"))
                      )
      );
    }
  }
}
