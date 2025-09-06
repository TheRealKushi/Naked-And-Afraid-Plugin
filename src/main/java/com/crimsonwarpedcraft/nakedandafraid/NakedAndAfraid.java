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
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.*;

/**
 * Main class for the NakedAndAfraid plugin.
 * <p>
 * This plugin handles features such as chat restriction, tab list hiding, armor damage,
 * spawn management, and team management. The plugin will automatically disable the tablist hiding
 * if ProtocolLib is missing.
 * </p>
 * <p>
 * Configuration options include enabling/disabling chat, tab, armor damage,
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

  /**
   * Checks if the server supports the Adventure API (Minecraft 1.19+).
   *
   * @return true if the server version is 1.19 or higher, false otherwise.
   */
  private boolean isAdventureSupported() {
    String version = Bukkit.getBukkitVersion().split("-")[0];
    try {
      String[] parts = version.split("\\.");
      int major = Integer.parseInt(parts[1]);
      return major >= 19;
    } catch (Exception e) {
      debugLog("[NakedAndAfraid] Failed to parse Bukkit version: " + version);
      return false;
    }
  }

  @Override
  public void onEnable() {
    debugLog("[NakedAndAfraid] Starting plugin initialization for Bukkit version " + Bukkit.getBukkitVersion());
    PaperLib.suggestPaper(this);

    // Load default config and initialize listeners
    debugLog("[NakedAndAfraid] Loading default config");
    saveDefaultConfig();

    // Add worlds to config.yml
    debugLog("[NakedAndAfraid] Updating enabled-worlds in config");
    setWorldList();

    // Reloads all plugin listeners
    debugLog("[NakedAndAfraid] Reloading listeners");
    reloadListeners();

    teleportOnCountdownEnd = getConfig().getBoolean("teleport-on-countdown-end", false);
    multipleSpawnPriority = getConfig().getString("multiple-spawn-priority", "FIRST").toUpperCase();
    debugLog("[NakedAndAfraid] Loaded config: teleport-on-countdown-end=" + teleportOnCountdownEnd +
            ", multiple-spawn-priority=" + multipleSpawnPriority);

    teamsManager = new TeamsManager(this);
    teamCommands = new TeamCommands(teamsManager, this);
    debugLog("[NakedAndAfraid] Initialized TeamsManager and TeamCommands");

    getServer().getPluginManager().registerEvents(new GlobalDeathSoundListener(this), this);
    getServer().getPluginManager().registerEvents(new TeamListener(this, teamsManager, teamCommands), this);
    debugLog("[NakedAndAfraid] Registered GlobalDeathSoundListener and TeamListener");

    spawnManager = new SpawnManager(this);
    spawnManager.loadSpawns();
    debugLog("[NakedAndAfraid] Initialized SpawnManager and loaded spawns");

    teleportHelper = new TeleportHelper(this);
    debugLog("[NakedAndAfraid] Initialized TeleportHelper");

    // Initialize tab hider if ProtocolLib is present
    boolean protocolLibPresent = Bukkit.getPluginManager().getPlugin("ProtocolLib") != null;
    if (getConfig().getBoolean("disable-tab", true) && protocolLibPresent) {
      tabListClearer = new TabListClearer(this);
      tabListClearer.enable();
      getLogger().info("Naked And Afraid - Tab Hider Enabled.");
      debugLog("[NakedAndAfraid] Tab hider enabled with ProtocolLib");
    } else {
      String reason = protocolLibPresent ? "disable-tab=false" : "ProtocolLib missing";
      getLogger().warning("Tab hider not enabled: " + reason + " (Server version: " + Bukkit.getBukkitVersion() + ")");
      debugLog("[NakedAndAfraid] Tab hider not enabled (" + reason + ")");
    }

    getServer().getPluginManager().registerEvents(new com.crimsonwarpedcraft.nakedandafraid.listeners.VersionNotifyListener(this), this);
    debugLog("[NakedAndAfraid] Registered VersionNotifyListener");

    logStartupInfo();
    debugLog("[NakedAndAfraid] Plugin initialization completed");
  }

  /** Debug logger */
  public void debugLog(String message) {
    if (getConfig().getBoolean("debug-mode", false)) {
      getLogger().info(message);
    }
  }

  /**
   * Reloads all listeners according to the plugin configuration.
   * Handles enabling/disabling chat, tab, armor, and join/quit message features.
   */
  public void reloadListeners() {
    debugLog("[NakedAndAfraid] Starting listener reload");
    if (tabListClearer != null && tabListClearer.isEnabled()) {
      tabListClearer.disable();
      debugLog("[NakedAndAfraid] Disabled TabListClearer");
      tabListClearer = null;
    }
    if (chatRestrictionListener != null) {
      HandlerList.unregisterAll(chatRestrictionListener);
      debugLog("[NakedAndAfraid] Unregistered ChatRestrictionListener");
      chatRestrictionListener = null;
    }
    if (armorDamageListener != null) {
      armorDamageListener.disableAllTasks();
      debugLog("[NakedAndAfraid] Disabled all tasks for ArmorDamageListener");
      HandlerList.unregisterAll(armorDamageListener);
      debugLog("[NakedAndAfraid] Unregistered ArmorDamageListener");
      armorDamageListener = null;
    }
    if (joinQuitMessageSuppressor != null) {
      HandlerList.unregisterAll(joinQuitMessageSuppressor);
      debugLog("[NakedAndAfraid] Unregistered JoinQuitMessageSuppressor");
      joinQuitMessageSuppressor = null;
    }

    // Chat restriction
    if (getConfig().getBoolean("disable-chat", true)) {
      chatRestrictionListener = new ChatRestrictionListener(this);
      getServer().getPluginManager().registerEvents(chatRestrictionListener, this);
      getLogger().info("Naked And Afraid - Chat Restriction Enabled.");
      debugLog("[NakedAndAfraid] Enabled ChatRestrictionListener");
    }

    // Tab hiding
    boolean disableTab = getConfig().getBoolean("disable-tab", true);
    boolean protocolLibPresent = Bukkit.getPluginManager().getPlugin("ProtocolLib") != null;
    if (disableTab && protocolLibPresent) {
      tabListClearer = new TabListClearer(this);
      tabListClearer.enable();
      getLogger().info("Naked And Afraid - Tab Hider Enabled.");
      debugLog("[NakedAndAfraid] Enabled TabListClearer");

      // Update for all online players
      for (Player player : Bukkit.getOnlinePlayers()) {
        tabListClearer.applyToPlayer(player);
        debugLog("[NakedAndAfraid] Applied TabListClearer to player " + player.getName());
      }
    } else {
      String reason = protocolLibPresent ? "disable-tab=false" : "ProtocolLib missing";
      debugLog("[NakedAndAfraid] Tab hider not enabled (" + reason + ")");
    }

    // Armor damage
    boolean armorDamage = getConfig().getBoolean("armor-damage.enabled", true);
    if (armorDamage) {
      armorDamageListener = new ArmorDamageListener(this);
      getServer().getPluginManager().registerEvents(armorDamageListener, this);
      armorDamageListener.refreshArmorTasks();
      getLogger().info("Naked And Afraid - Armor Damage Enabled.");
      debugLog("[NakedAndAfraid] Enabled ArmorDamageListener and refreshed armor tasks");
    } else {
      getLogger().info("Naked And Afraid - Armor Damage Disabled.");
      debugLog("[NakedAndAfraid] ArmorDamageListener not enabled");
    }

    // Join/quit message suppression
    if (getConfig().getBoolean("disable-join-quit-messages", true)) {
      joinQuitMessageSuppressor = new JoinQuitMessageSuppressor(this);
      getServer().getPluginManager().registerEvents(joinQuitMessageSuppressor, this);
      getLogger().info("Naked And Afraid - Message Disabling Enabled.");
      debugLog("[NakedAndAfraid] Enabled JoinQuitMessageSuppressor");
    }
    debugLog("[NakedAndAfraid] Listener reload completed");
  }

  /**
   * Automatically adds all existing worlds to the config under `enabled-worlds` variable if not already present,
   * ensuring no duplicate keys are created.
   * Each new world is enabled (true) by default.
   */
  private void setWorldList() {
    debugLog("[NakedAndAfraid] Starting setWorldList to update enabled-worlds");
    File configFile = new File(getDataFolder(), "config.yml");
    if (!configFile.exists()) {
      debugLog("[NakedAndAfraid] Config file does not exist, skipping setWorldList");
      return;
    }

    try {
      List<String> lines = new ArrayList<>();
      boolean inEnabledWorlds = false;
      Map<String, Boolean> existingWorlds = new LinkedHashMap<>();
      List<String> enabledWorldsLines = new ArrayList<>();

      // Read the config file and collect enabled-worlds entries
      debugLog("[NakedAndAfraid] Reading config file for enabled-worlds");
      try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
        String line;
        while ((line = reader.readLine()) != null) {
          String trimmed = line.trim();

          if (trimmed.startsWith("enabled-worlds:")) {
            inEnabledWorlds = true;
            lines.add(line);
            enabledWorldsLines.add(line);
            debugLog("[NakedAndAfraid] Found enabled-worlds section");
            continue;
          }

          if (inEnabledWorlds && trimmed.matches("^[^\\s].*:$")) {
            inEnabledWorlds = false;
            debugLog("[NakedAndAfraid] Exited enabled-worlds section");
          }

          if (inEnabledWorlds && !trimmed.isEmpty() && !trimmed.startsWith("#")) {
            enabledWorldsLines.add(line);
            if (trimmed.contains(":")) {
              String[] parts = trimmed.split(":", 2);
              String worldName = parts[0].trim();
              boolean enabled = parts.length > 1 && parts[1].trim().equalsIgnoreCase("true");
              existingWorlds.put(worldName, enabled);
              debugLog("[NakedAndAfraid] Found world " + worldName + " with enabled=" + enabled);
            }
          } else {
            lines.add(line);
          }
        }
      }

      // Get current worlds and check for new ones
      debugLog("[NakedAndAfraid] Checking for new worlds");
      boolean modified = false;
      List<String> newWorldLines = new ArrayList<>();
      for (var world : Bukkit.getWorlds()) {
        if (!existingWorlds.containsKey(world.getName())) {
          newWorldLines.add("  " + world.getName() + ": true");
          existingWorlds.put(world.getName(), true);
          modified = true;
          debugLog("[NakedAndAfraid] Added new world " + world.getName() + " to enabled-worlds");
        }
      }

      // If there are duplicates or new worlds, rewrite the enabled-worlds section
      if (modified || existingWorlds.size() < enabledWorldsLines.size() - 1) {
        debugLog("[NakedAndAfraid] Rewriting enabled-worlds section (modified=" + modified +
                ", duplicates detected=" + (existingWorlds.size() < enabledWorldsLines.size() - 1) + ")");
        List<String> updatedLines = new ArrayList<>();
        boolean inEnabledWorldsSection = false;

        for (String line : lines) {
          String trimmed = line.trim();
          if (trimmed.startsWith("enabled-worlds:")) {
            inEnabledWorldsSection = true;
            updatedLines.add(line);
            for (Map.Entry<String, Boolean> entry : existingWorlds.entrySet()) {
              updatedLines.add("  " + entry.getKey() + ": " + entry.getValue());
            }
            debugLog("[NakedAndAfraid] Wrote deduplicated enabled-worlds section");
            continue;
          }
          if (inEnabledWorldsSection && trimmed.matches("^[^\\s].*:$")) {
            inEnabledWorldsSection = false;
          }
          if (!inEnabledWorldsSection) {
            updatedLines.add(line);
          }
        }

        // Write the updated config file
        debugLog("[NakedAndAfraid] Writing updated config file");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(configFile))) {
          for (String l : updatedLines) {
            writer.write(l);
            writer.newLine();
          }
        }
        debugLog("[NakedAndAfraid] Enabled worlds updated, duplicate keys were removed");
      } else {
        debugLog("[NakedAndAfraid] No changes needed for enabled-worlds section");
      }

    } catch (IOException e) {
      getLogger().severe("Failed to update enabled-worlds: " + e.getMessage());
      debugLog("[NakedAndAfraid] Failed to update enabled-worlds: " + e.getMessage());
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

  /**
   * Defines commands and subcommands for the plugin.
   */
  @Override
  public boolean onCommand(final @NotNull CommandSender sender,
                           final @NotNull Command command,
                           final @NotNull String label,
                           final String @NotNull [] args) {
    debugLog("[NakedAndAfraid] Processing command: " + label + " " + String.join(" ", args));

    if (!(label.equalsIgnoreCase("nf") || label.equalsIgnoreCase("nakedafraid"))) {
      debugLog("[NakedAndAfraid] Invalid command label: " + label);
      return false;
    }

    boolean isAdventure = isAdventureSupported();
    boolean isPre113 = !Bukkit.getBukkitVersion().matches(".*1\\.(1[3-9]|2[0-1]).*");

    if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
      int page = 1;
      if (args.length >= 2) {
        try {
          page = Integer.parseInt(args[1]);
          debugLog("[NakedAndAfraid] Parsed help page number: " + page);
        } catch (NumberFormatException ignored) {
          if (isAdventure) {
            sender.sendMessage(Component.text("Invalid help page number. Showing page 1.").color(NamedTextColor.RED));
          } else {
            sender.sendMessage("§cInvalid help page number. Showing page 1.");
          }
          debugLog("[NakedAndAfraid] Invalid help page number: " + args[1]);
        }
      }
      sendHelpMessage(sender, page);
      debugLog("[NakedAndAfraid] Sent help message to " + sender.getName() + " for page " + page);
      return true;
    }

    switch (args[0].toLowerCase()) {
      case "reloadconfig" -> {
        if (!sender.hasPermission("nakedandafraid.reload")) {
          if (isAdventure) {
            sender.sendMessage(Component.text("You don't have permission to execute this command.").color(NamedTextColor.RED));
          } else {
            sender.sendMessage("§cYou don't have permission to execute this command.");
          }
          debugLog("[NakedAndAfraid] " + sender.getName() + " lacks permission for reloadconfig");
          return isPre113 ? false : true;
        }

        debugLog("[NakedAndAfraid] Reloading config for " + sender.getName());
        reloadConfig();
        teleportOnCountdownEnd = getConfig().getBoolean("teleport-on-countdown-end", false);
        multipleSpawnPriority = getConfig().getString("multiple-spawn-priority", "FIRST").toUpperCase();
        debugLog("[NakedAndAfraid] Reloaded config: teleport-on-countdown-end=" + teleportOnCountdownEnd +
                ", multiple-spawn-priority=" + multipleSpawnPriority);
        reloadListeners();
        if (armorDamageListener != null) {
          armorDamageListener.refreshArmorTasks();
          debugLog("[NakedAndAfraid] Refreshed armor tasks after reload");
        }

        spawnManager.loadSpawns();
        debugLog("[NakedAndAfraid] Reloaded spawns");
        if (isAdventure) {
          sender.sendMessage(Component.text("Naked and Afraid config reloaded.").color(NamedTextColor.GREEN));
        } else {
          sender.sendMessage("§aNaked and Afraid config reloaded.");
        }
        debugLog("[NakedAndAfraid] Sent config reload confirmation to " + sender.getName());
        return true;
      }
      case "spawn" -> {
        if (!sender.hasPermission("nakedandafraid.spawn")) {
          if (isAdventure) {
            sender.sendMessage(Component.text("You don't have permission to execute this command.").color(NamedTextColor.RED));
          } else {
            sender.sendMessage("§cYou don't have permission to execute this command.");
          }
          debugLog("[NakedAndAfraid] " + sender.getName() + " lacks permission for spawn command");
          return isPre113 ? false : true;
        }
        boolean result = spawnManager.handleCommand(sender, args);
        debugLog("[NakedAndAfraid] Spawn command result for " + sender.getName() + ": " + result);
        return result;
      }
      case "team" -> {
        if (!sender.hasPermission("nakedandafraid.team")) {
          if (isAdventure) {
            sender.sendMessage(Component.text("You don't have permission to execute this command.").color(NamedTextColor.RED));
          } else {
            sender.sendMessage("§cYou don't have permission to execute this command.");
          }
          debugLog("[NakedAndAfraid] " + sender.getName() + " lacks permission for team command");
          return isPre113 ? false : true;
        }
        boolean result = teamCommands.handleTeamCommand(sender, args);
        debugLog("[NakedAndAfraid] Team command result for " + sender.getName() + ": " + result);
        return result;
      }
      case "user" -> {
        if (!sender.hasPermission("nakedandafraid.user")) {
          if (isAdventure) {
            sender.sendMessage(Component.text("You don't have permission to execute this command.").color(NamedTextColor.RED));
          } else {
            sender.sendMessage("§cYou don't have permission to execute this command.");
          }
          debugLog("[NakedAndAfraid] " + sender.getName() + " lacks permission for user command");
          return isPre113 ? false : true;
        }
        boolean result = teamCommands.handleUserCommand(sender, args);
        debugLog("[NakedAndAfraid] User command result for " + sender.getName() + ": " + result);
        return result;
      }
      default -> {
        if (isAdventure) {
          sender.sendMessage(Component.text("Unknown subcommand. Use /nf help for commands.").color(NamedTextColor.RED));
        } else {
          sender.sendMessage("§cUnknown subcommand. Use /nf help for commands.");
        }
        debugLog("[NakedAndAfraid] Unknown subcommand from " + sender.getName() + ": " + args[0]);
        return isPre113 ? false : true;
      }
    }
  }

  /**
   * Handles tab-completion for NakedAndAfraid's commands and subcommands.
   */
  @Override
  public List<String> onTabComplete(@NotNull CommandSender sender,
                                    @NotNull Command command,
                                    @NotNull String alias,
                                    String @NotNull [] args) {
    debugLog("[NakedAndAfraid] Processing tab completion for " + sender.getName() +
            ": " + command.getName() + " " + String.join(" ", args));

    if (!(command.getName().equalsIgnoreCase("nf") || command.getName().equalsIgnoreCase("nakedafraid"))) {
      debugLog("[NakedAndAfraid] Invalid command for tab completion: " + command.getName());
      return null;
    }

    if (args.length == 1) {
      List<String> completions = List.of("help", "reloadconfig", "spawn", "team", "user");
      debugLog("[NakedAndAfraid] Tab completion for first arg: " + completions);
      return completions;
    }

    switch (args[0].toLowerCase()) {
      case "spawn" -> {
        if (args.length == 2) {
          List<String> completions = List.of("create", "rename", "remove", "list", "tp", "tpall");
          debugLog("[NakedAndAfraid] Tab completion for spawn subcommand: " + completions);
          return completions;
        }
        if (args.length == 3) {
          if (args[1].equalsIgnoreCase("create")) {
            debugLog("[NakedAndAfraid] Tab completion for spawn create: empty list");
            return List.of();
          }
          if (args[1].equalsIgnoreCase("rename") || args[1].equalsIgnoreCase("remove") || args[1].equalsIgnoreCase("tp")) {
            List<String> completions = spawnManager.getSpawns().keySet().stream().toList();
            debugLog("[NakedAndAfraid] Tab completion for spawn rename/remove/tp: " + completions);
            return completions;
          }
        }
        if (args.length == 4) {
          if (args[1].equalsIgnoreCase("create") || args[1].equalsIgnoreCase("tp")) {
            List<String> completions = Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
            debugLog("[NakedAndAfraid] Tab completion for spawn create/tp players: " + completions);
            return completions;
          }
          if (args[1].equalsIgnoreCase("rename")) {
            debugLog("[NakedAndAfraid] Tab completion for spawn rename: empty list");
            return List.of();
          }
        }
      }

      case "team" -> {
        if (args.length == 2) {
          List<String> completions = List.of("create", "remove", "list", "block", "setblock", "meta");
          debugLog("[NakedAndAfraid] Tab completion for team subcommand: " + completions);
          return completions;
        }
        if (args.length == 3) {
          if (args[1].equalsIgnoreCase("remove") || args[1].equalsIgnoreCase("setblock") || args[1].equalsIgnoreCase("block")) {
            List<String> completions = teamsManager.getTeams().stream().map(TeamsManager.Team::getName).toList();
            debugLog("[NakedAndAfraid] Tab completion for team remove/setblock/block: " + completions);
            return completions;
          }
          if (args[1].equalsIgnoreCase("create") || args[1].equalsIgnoreCase("meta")) {
            debugLog("[NakedAndAfraid] Tab completion for team create/meta: empty list");
            return List.of();
          }
        }
        if (args.length == 4) {
          if (args[1].equalsIgnoreCase("create")) {
            List<String> completions = ValidTeamColors().stream()
                    .filter(c -> c.startsWith(args[2].toUpperCase()))
                    .toList();
            debugLog("[NakedAndAfraid] Tab completion for team create colors: " + completions);
            return completions;
          }
          if (args[1].equalsIgnoreCase("block") && args[2].equalsIgnoreCase("selector")) {
            List<String> completions = Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
            debugLog("[NakedAndAfraid] Tab completion for team block selector players: " + completions);
            return completions;
          }
          if (args[1].equalsIgnoreCase("setblock") && sender instanceof Player player) {
            List<String> completions = List.of(String.valueOf(player.getLocation().getBlockX()));
            debugLog("[NakedAndAfraid] Tab completion for team setblock x: " + completions);
            return completions;
          }
          if (args[1].equalsIgnoreCase("meta") && args[2].equalsIgnoreCase("color")) {
            List<String> completions = List.of("get", "set");
            debugLog("[NakedAndAfraid] Tab completion for team meta color: " + completions);
            return completions;
          }
        }
        if (args.length == 5) {
          if (args[1].equalsIgnoreCase("setblock") && sender instanceof Player player) {
            List<String> completions = List.of(String.valueOf(player.getLocation().getBlockY()));
            debugLog("[NakedAndAfraid] Tab completion for team setblock y: " + completions);
            return completions;
          }
          if (args[1].equalsIgnoreCase("meta") && args[2].equalsIgnoreCase("color") && args[3].equalsIgnoreCase("set")) {
            List<String> completions = ValidTeamColors().stream()
                    .filter(c -> c.startsWith(args[4].toUpperCase()))
                    .toList();
            debugLog("[NakedAndAfraid] Tab completion for team meta color set: " + completions);
            return completions;
          }
        }
        if (args.length == 6 && args[1].equalsIgnoreCase("setblock") && sender instanceof Player player) {
          List<String> completions = List.of(String.valueOf(player.getLocation().getBlockZ()));
          debugLog("[NakedAndAfraid] Tab completion for team setblock z: " + completions);
          return completions;
        }
      }

      case "user" -> {
        if (args.length == 2) {
          List<String> completions = Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
          debugLog("[NakedAndAfraid] Tab completion for user players: " + completions);
          return completions;
        }
        if (args.length == 3 && args[2].equalsIgnoreCase("team")) {
          List<String> completions = List.of("add", "remove", "list");
          debugLog("[NakedAndAfraid] Tab completion for user team subcommand: " + completions);
          return completions;
        }
        if (args.length == 4 && args[2].equalsIgnoreCase("team") &&
                (args[3].equalsIgnoreCase("add") || args[3].equalsIgnoreCase("remove"))) {
          List<String> completions = teamsManager.getTeams().stream().map(TeamsManager.Team::getName).toList();
          debugLog("[NakedAndAfraid] Tab completion for user team add/remove: " + completions);
          return completions;
        }
      }
    }

    debugLog("[NakedAndAfraid] Tab completion returned empty list");
    return List.of();
  }

  /**
   * Retrieves the teleport helper instance.
   *
   * @return The {@link TeleportHelper} for this plugin.
   */
  @NotNull
  public TeleportHelper getTeleportHelper() {
    debugLog("[NakedAndAfraid] Retrieved TeleportHelper instance");
    return teleportHelper;
  }

  /**
   * Sends paginated help message to the command sender.
   *
   * @param sender The command sender to display the help message to.
   * @param page   Page number to display (1-based).
   */
  private void sendHelpMessage(@NotNull CommandSender sender, int page) {
    debugLog("[NakedAndAfraid] Preparing help message for " + sender.getName() + ", page " + page);
    boolean isAdventure = isAdventureSupported();
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
    debugLog("[NakedAndAfraid] Adjusted help page to " + page + " (total pages: " + totalPages + ")");

    if (isAdventure) {
      sender.sendMessage(Component.text("==== NakedAndAfraid Help ====").color(NamedTextColor.GOLD));
    } else {
      sender.sendMessage("§6==== NakedAndAfraid Help ====");
    }

    int startIndex = (page - 1) * HELP_LINES_PER_PAGE;
    int endIndex = Math.min(startIndex + HELP_LINES_PER_PAGE, helpLines.size());

    for (int i = startIndex; i < endIndex; i++) {
      sender.sendMessage(helpLines.get(i));
    }

    if (isAdventure) {
      sender.sendMessage(Component.text("Page " + page + " of " + totalPages).color(NamedTextColor.YELLOW));
    } else {
      sender.sendMessage("§ePage " + page + " of " + totalPages);
    }
    debugLog("[NakedAndAfraid] Sent help page " + page + " to " + sender.getName());
  }

  private List<String> ValidTeamColors() {
    debugLog("[NakedAndAfraid] Retrieving valid team colors");
    return List.of(
            "RED", "BLUE", "GREEN", "YELLOW", "AQUA",
            "DARK_PURPLE", "GOLD", "LIGHT_PURPLE", "WHITE"
    );
  }

  /**
   * Sends formatted plugin startup information to the console.
   */
  private void logStartupInfo() {
    debugLog("[NakedAndAfraid] Sending startup information to console");
    ConsoleCommandSender console = Bukkit.getServer().getConsoleSender();
    boolean isAdventure = isAdventureSupported();

    if (isAdventure) {
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
    } else {
      console.sendMessage(" ");
      console.sendMessage("§6__   ____  _______ ");
      console.sendMessage("§6| \\ | | | | ____||");
      console.sendMessage("§6|  \\| | | | ||_   ");
      console.sendMessage("§6| |\\| | | | __||  ");
      console.sendMessage("§6|_| \\_|_| |_||    ");
      console.sendMessage("");
      console.sendMessage("§6NakedAndAfraid Plugin §4v" + this.getDescription().getVersion());
      console.sendMessage("§eRunning on §b" + Bukkit.getServer().getName() + " §f" + Bukkit.getServer().getVersion());
      console.sendMessage("");
      console.sendMessage("§aNakedAndAfraid Plugin enabled successfully!");
      console.sendMessage("");
    }

    String currentVersion = this.getDescription().getVersion();
    debugLog("[NakedAndAfraid] Checking version: current=" + currentVersion);
    VersionChecker versionChecker = new VersionChecker(this);
    String latestVersion = versionChecker.getLatestVersion();

    if (latestVersion != null && versionChecker.isOutdated(currentVersion)) {
      if (isAdventure) {
        String warningText = PlainTextComponentSerializer.plainText().serialize(
                Component.text("[NakedAndAfraid] ").color(NamedTextColor.GOLD)
                        .append(Component.text("There is a new Naked And Afraid Plugin version available for download: "
                                        + latestVersion + " (Current: " + currentVersion + ")")
                                .color(NamedTextColor.RED))
        );
        this.getLogger().warning(warningText);

        console.sendMessage(
                Component.text("[NakedAndAfraid] ").color(NamedTextColor.GOLD)
                        .append(Component.text("Download it here: ").color(NamedTextColor.RED))
                        .append(Component.text("https://modrinth.com/plugin/naked-and-afraid-plugin/versions")
                                .color(NamedTextColor.RED)
                                .clickEvent(ClickEvent.openUrl("https://modrinth.com/plugin/naked-and-afraid-plugin/versions")))
        );
      } else {
        this.getLogger().warning("[NakedAndAfraid] There is a new Naked And Afraid Plugin version available for download: " +
                latestVersion + " (Current: " + currentVersion + ")");
        console.sendMessage("§6[NakedAndAfraid] §cDownload it here: §chttps://modrinth.com/plugin/naked-and-afraid-plugin/versions");
      }
      debugLog("[NakedAndAfraid] Notified console of outdated version: " + latestVersion);
    } else {
      debugLog("[NakedAndAfraid] Version is up to date or no latest version available");
    }
  }
}