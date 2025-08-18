package com.crimsonwarpedcraft.nakedandafraid;

import com.crimsonwarpedcraft.nakedandafraid.listeners.*;
import com.crimsonwarpedcraft.nakedandafraid.spawn.SpawnManager;
import com.crimsonwarpedcraft.nakedandafraid.team.TeamCommands;
import com.crimsonwarpedcraft.nakedandafraid.team.TeamsManager;
import com.crimsonwarpedcraft.nakedandafraid.util.TeleportHelper;
import io.papermc.lib.PaperLib;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
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

  /** Utility class for hiding players from tab list using ProtocolLib. */
  private TabListClearer tabListClearer;

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

  @Override
  public void onEnable() {
    PaperLib.suggestPaper(this);

    // Load default config and initialize listeners
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

    // Initialize tab hider if ProtocolLib is present
    if (getConfig().getBoolean("disable-tab", true)) {
      if (Bukkit.getPluginManager().getPlugin("ProtocolLib") != null) {
        try {
          TabListClearer.register(this);
          getLogger().info("Naked And Afraid - Tab Hider Enabled.");
        } catch (Throwable t) {
          getLogger().warning("Failed to enable Tab Hider due to an error: " + t.getMessage());
        }
      } else {
        getLogger().warning("ProtocolLib not found! Tab Hider is disabled.");
      }
    }

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

    // Tab list hiding
    if (getConfig().getBoolean("disable-tab", true)) {
      if (Bukkit.getPluginManager().getPlugin("ProtocolLib") != null) {
        try {
          TabListClearer.register(this);
          getLogger().info("Naked And Afraid - Tab Hider Enabled.");
        } catch (Throwable t) {
          getLogger().warning("Failed to enable Tab Hider due to an error: " + t.getMessage());
        }
      } else {
        getLogger().warning("ProtocolLib not found! Tab Hider is disabled.");
      }
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
          sender.sendMessage("§cYou don't have permission to do that.");
          return true;
        }
        reloadConfig();
        spawnManager.loadSpawns();
        sender.sendMessage("§aNaked and Afraid config reloaded.");
        return true;
      }
      case "spawn" -> { return spawnManager.handleCommand(sender, args); }
      case "team" -> { return teamCommands.handleTeamCommand(sender, args); }
      case "user" -> { return teamCommands.handleUserCommand(sender, args); }
      default -> sender.sendMessage("§cUnknown subcommand. Use /nf help for commands.");
    }
    return true;
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
            "§e/nf team create (team-name) §7- Define a new team",
            "§e/nf team remove (team-name) §7- Delete an existing team",
            "§e/nf team list §7- List all existing teams",
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
    console.sendMessage(Component.text("Plugin enabled successfully!").color(NamedTextColor.GREEN));
    console.sendMessage(Component.empty());
  }
}
