package com.crimsonwarpedcraft.nakedandafraid.v1_8;

import com.crimsonwarpedcraft.nakedandafraid.v1_8.listeners.*;
import com.crimsonwarpedcraft.nakedandafraid.v1_8.spawn.SpawnManager;
import com.crimsonwarpedcraft.nakedandafraid.v1_8.team.TeamCommands;
import com.crimsonwarpedcraft.nakedandafraid.v1_8.team.TeamsManager;
import com.crimsonwarpedcraft.nakedandafraid.v1_8.util.TeleportHelper;
import com.crimsonwarpedcraft.nakedandafraid.v1_8.util.TeleportHelperExtension;
import com.crimsonwarpedcraft.nakedandafraid.v1_8.util.VersionChecker;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;

import java.io.*;
import java.util.*;

public class NakedAndAfraid {

  private static Plugin plugin;

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

  public NakedAndAfraid(Plugin plugin) {
    NakedAndAfraid.plugin = plugin;
  }

  public static Plugin getPlugin() {
    return plugin;
  }

  public void onEnable() {
    debugLog("[NakedAndAfraid] Starting plugin initialization for Bukkit version " + Bukkit.getBukkitVersion());

    // Loads config and initialize listeners
    debugLog("[NakedAndAfraid] Loading default config");
    saveDefaultConfig();

    // Adds worlds to config.yml
    debugLog("[NakedAndAfraid] Updating enabled-worlds in config");
    setWorldList();

    // Reloads all plugin listeners
    debugLog("[NakedAndAfraid] Reloading listeners");
    reloadListeners();

    teleportOnCountdownEnd = getConfig().getBoolean("teleport-on-countdown-end", false);
    multipleSpawnPriority = getConfig().getString("multiple-spawn-priority", "FIRST").toUpperCase();
    debugLog("[NakedAndAfraid] Loaded config: teleport-on-countdown-end=" + teleportOnCountdownEnd +
            ", multiple-spawn-priority=" + multipleSpawnPriority);

    teamsManager = new TeamsManager(plugin);
    teamCommands = new TeamCommands(teamsManager, this);
    debugLog("[NakedAndAfraid] Initialized TeamsManager and TeamCommands");

    plugin.getServer().getPluginManager().registerEvents(new GlobalDeathSoundListener(this), plugin);
    plugin.getServer().getPluginManager().registerEvents(new TeamListener(this, teamsManager, teamCommands), plugin);
    debugLog("[NakedAndAfraid] Registered GlobalDeathSoundListener and TeamListener");

    spawnManager = new SpawnManager(this);
    spawnManager.loadSpawns();
    debugLog("[NakedAndAfraid] Initialized SpawnManager and loaded spawns");

    teleportHelper = new TeleportHelper(plugin, this);
    new TeleportHelperExtension(plugin, this, teleportHelper);
    debugLog("[NakedAndAfraid] Initialized TeleportHelper");

    // Initialize tab hider if ProtocolLib is present
    boolean protocolLibPresent = Bukkit.getPluginManager().getPlugin("ProtocolLib") != null;
    if (getConfig().getBoolean("disable-tab", true) && protocolLibPresent) {
      tabListClearer = new TabListClearer(this);
      tabListClearer.enable();
      plugin.getLogger().info("Naked And Afraid - Tab Hider Enabled.");
      debugLog("[NakedAndAfraid] Tab hider enabled with ProtocolLib");
    } else {
      String reason = protocolLibPresent ? "disable-tab=false" : "ProtocolLib missing";
      plugin.getLogger().warning("Tab hider not enabled: " + reason + " (Server version: " + Bukkit.getBukkitVersion() + ")");
      debugLog("[NakedAndAfraid] Tab hider not enabled (" + reason + ")");
    }

    plugin.getServer().getPluginManager().registerEvents(new VersionNotifyListener(this), plugin);
    debugLog("[NakedAndAfraid] Registered VersionNotifyListener");

    logStartupInfo();
    debugLog("[NakedAndAfraid] Plugin initialization completed");
  }

  public void onDisable() {
    debugLog("[NakedAndAfraid] Shutting down plugin");

    if (tabListClearer != null && tabListClearer.isEnabled()) {
      tabListClearer.disable();
      debugLog("[NakedAndAfraid] Disabled TabListClearer");
    }
    if (armorDamageListener != null) {
      armorDamageListener.disableAllTasks();
      debugLog("[NakedAndAfraid] Disabled all tasks for ArmorDamageListener");
    }
    if (spawnManager != null) {
      spawnManager.saveSpawns(); // Ensure spawns are saved on shutdown
      debugLog("[NakedAndAfraid] Saved spawns");
    }
    HandlerList.unregisterAll(plugin); // Unregister all listeners for this plugin
    debugLog("[NakedAndAfraid] Unregistered all listeners");
  }

  /** Debug logger */
  public void debugLog(String message) {
    if (getConfig().getBoolean("debug-mode", false)) {
      plugin.getLogger().info(message);
    }
  }

  /**
   * Checks if a world is enabled for plugin features.
   * Defaults to true if not listed in config.
   *
   * @param worldName The world name to check.
   * @return true if enabled (or default), false if explicitly disabled.
   */
  public boolean isWorldEnabled(String worldName) {
    if (getConfig().contains("enabled-worlds." + worldName)) {
      boolean enabled = getConfig().getBoolean("enabled-worlds." + worldName, true);
      debugLog("[NakedAndAfraid] World '" + worldName + "' enabled: " + enabled);
      return enabled;
    }
    debugLog("[NakedAndAfraid] World '" + worldName + "' not in config, default enabled: true");
    return true;
  }

  /**
   * Reloads all listeners according to the plugin configuration.
   * Handles enabling/disabling chat, tab, armor, join/quit message, and totem features.
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
      plugin.getServer().getPluginManager().registerEvents(chatRestrictionListener, plugin);
      plugin.getLogger().info("Naked And Afraid - Chat Restriction Enabled.");
      debugLog("[NakedAndAfraid] Enabled ChatRestrictionListener");
    } else {
      plugin.getLogger().info("Naked And Afraid - Chat Restriction Disabled.");
      debugLog("[NakedAndAfraid] ChatRestrictionListener not enabled");
    }

    // Tab hiding
    boolean disableTab = getConfig().getBoolean("disable-tab", true);
    boolean protocolLibPresent = Bukkit.getPluginManager().getPlugin("ProtocolLib") != null;
    if (disableTab && protocolLibPresent) {
      tabListClearer = new TabListClearer(this);
      tabListClearer.enable();
      plugin.getLogger().info("Naked And Afraid - Tab Hider Enabled.");
      debugLog("[NakedAndAfraid] Enabled TabListClearer");
      for (Player player : Bukkit.getOnlinePlayers()) {
        if (isWorldEnabled(player.getWorld().getName())) {
          tabListClearer.applyToPlayer(player);
          debugLog("[NakedAndAfraid] Applied TabListClearer to player " + player.getName() + " in enabled world " + player.getWorld().getName());
        } else {
          debugLog("[NakedAndAfraid] Skipped TabListClearer for player " + player.getName() + " in disabled world " + player.getWorld().getName());
        }
      }
    } else {
      String reason = protocolLibPresent ? "disable-tab=false" : "ProtocolLib missing";
      debugLog("[NakedAndAfraid] Tab hider not enabled (" + reason + ")");
    }

    // Armor damage
    boolean armorDamage = getConfig().getBoolean("armor-damage.enabled", true);
    if (armorDamage) {
      armorDamageListener = new ArmorDamageListener(this);
      plugin.getServer().getPluginManager().registerEvents(armorDamageListener, plugin);
      armorDamageListener.refreshArmorTasks();
      plugin.getLogger().info("Naked And Afraid - Armor Damage Enabled.");
      debugLog("[NakedAndAfraid] Enabled ArmorDamageListener and refreshed armor tasks");
    } else {
      plugin.getLogger().info("Naked And Afraid - Armor Damage Disabled.");
      debugLog("[NakedAndAfraid] ArmorDamageListener not enabled");
    }

    // Join/quit message suppression
    if (getConfig().getBoolean("disable-join-quit-messages", true)) {
      joinQuitMessageSuppressor = new JoinQuitMessageSuppressor(this);
      plugin.getServer().getPluginManager().registerEvents(joinQuitMessageSuppressor, plugin);
      plugin.getLogger().info("Naked And Afraid - Message Disabling Enabled.");
      debugLog("[NakedAndAfraid] Enabled JoinQuitMessageSuppressor");
    } else {
      plugin.getLogger().info("Naked And Afraid - Message Disabling Disabled.");
      debugLog("[NakedAndAfraid] JoinQuitMessageSuppressor not enabled");
    }

    if (spawnManager != null) {
      spawnManager.refreshWorlds();
    }
    if (teamsManager != null) {
      teamsManager.refreshWorlds();
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
    File configFile = new File(plugin.getDataFolder(), "config.yml");
    if (!configFile.exists()) {
      debugLog("[NakedAndAfraid] Config file does not exist, skipping setWorldList");
      return;
    }

    try {
      List<String> lines = new ArrayList<>();
      boolean inEnabledWorlds = false;
      Map<String, Boolean> existingWorlds = new LinkedHashMap<>();
      List<Integer> enabledWorldsLineIndices = new ArrayList<>();
      int enabledWorldsIndex = -1;
      String enabledWorldsLine = null;

      debugLog("[NakedAndAfraid] Reading config file for enabled-worlds");
      try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
        int lineNum = 0;
        String line;
        while ((line = reader.readLine()) != null) {
          lines.add(line);
          String trimmed = line.trim();

          if (trimmed.startsWith("enabled-worlds:")) {
            inEnabledWorlds = true;
            enabledWorldsIndex = lineNum;
            enabledWorldsLine = line;
            debugLog("[NakedAndAfraid] Found enabled-worlds section at line " + lineNum + ", original line: '" + line + "'");
          }

          if (inEnabledWorlds) {
            if (!trimmed.startsWith("#") && !line.startsWith(" ") && trimmed.contains(": ")) {
              inEnabledWorlds = false;
              debugLog("[NakedAndAfraid] Exited enabled-worlds section at line " + lineNum + " (next key: " + trimmed + ")");
            } else if (inEnabledWorlds && trimmed.contains(":") && line.startsWith("  ") && !trimmed.startsWith("-")) {
              enabledWorldsLineIndices.add(lineNum);
              String[] parts = trimmed.split(":", 2);
              if (parts.length > 1) {
                String worldName = parts[0].trim();
                if (!worldName.matches(".*-.*") && !worldName.matches("\\d+") && worldName.length() > 0) {
                  boolean enabled = parts[1].trim().equalsIgnoreCase("true");
                  existingWorlds.put(worldName, enabled);
                  debugLog("[NakedAndAfraid] Found world " + worldName + " with enabled=" + enabled + " at line " + lineNum);
                }
              }
            }
          }
          lineNum++;
        }
      }

      debugLog("[NakedAndAfraid] Checking for new worlds");
      boolean hasWorlds = false;
      for (org.bukkit.World world : Bukkit.getWorlds()) {
        if (!existingWorlds.containsKey(world.getName())) {
          existingWorlds.put(world.getName(), true);
          debugLog("[NakedAndAfraid] Added new world " + world.getName() + " to enabled-worlds");
        }
        hasWorlds = true;
      }

      boolean needsRewrite = !enabledWorldsLineIndices.isEmpty() || hasWorlds || existingWorlds.isEmpty();
      if (enabledWorldsIndex == -1) {
        debugLog("[NakedAndAfraid] No enabled-worlds key found, skipping rewrite");
        return;
      }

      if (needsRewrite) {
        debugLog("[NakedAndAfraid] Rewriting enabled-worlds section (hasWorlds=" + hasWorlds + ", old indices=" + enabledWorldsLineIndices.size() + ")");

        for (int idx : enabledWorldsLineIndices) {
          if (idx < lines.size()) {
            lines.set(idx, "");
            debugLog("[NakedAndAfraid] Blanked old world line at index " + idx);
          }
        }

        if (hasWorlds && enabledWorldsLine != null && enabledWorldsLine.trim().endsWith("[]")) {
          String cleanKey = enabledWorldsLine.trim().replace("[]", "").trim();
          lines.set(enabledWorldsIndex, cleanKey);
          debugLog("[NakedAndAfraid] Cleaned key line to remove empty []: '" + cleanKey + "'");
        } else if (!hasWorlds && existingWorlds.isEmpty()) {
          if (!enabledWorldsLine.trim().endsWith("[]")) {
            lines.set(enabledWorldsIndex, enabledWorldsLine.trim() + " []");
            debugLog("[NakedAndAfraid] Set empty list for no worlds");
          }
        }

        List<String> worldLines = new ArrayList<>();
        for (Map.Entry<String, Boolean> entry : existingWorlds.entrySet()) {
          String indent = "  ";
          if (enabledWorldsLine != null) {
            int keyIndent = enabledWorldsLine.indexOf("enabled-worlds:");
            if (keyIndent > 0) indent = new String(new char[keyIndent + 2]).replace('\0', ' ');
          }
          worldLines.add(indent + entry.getKey() + ": " + entry.getValue());
        }
        lines.addAll(enabledWorldsIndex + 1, worldLines);
        debugLog("[NakedAndAfraid] Inserted " + worldLines.size() + " world lines");

        debugLog("[NakedAndAfraid] Writing updated config file");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(configFile))) {
          for (String l : lines) {
            if (!l.trim().isEmpty() || l.contains("#") || l.trim().isEmpty()) {
              writer.write(l);
              writer.newLine();
            }
          }
        }
        debugLog("[NakedAndAfraid] Enabled worlds updated successfully (brackets cleared if applicable)");
      } else {
        debugLog("[NakedAndAfraid] No changes needed for enabled-worlds section");
      }

    } catch (IOException e) {
      plugin.getLogger().severe("Failed to update enabled-worlds: " + e.getMessage());
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
  public String getMultipleSpawnPriority() {
    return multipleSpawnPriority;
  }

  /**
   * Defines commands and subcommands for the plugin.
   */
  public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
    debugLog("[NakedAndAfraid] Processing command: " + label + " " + String.join(" ", args));

    if (!(label.equalsIgnoreCase("nf") || label.equalsIgnoreCase("nakedafraid"))) {
      debugLog("[NakedAndAfraid] Invalid command label: " + label);
      return false;
    }

    boolean isPre113 = !Bukkit.getBukkitVersion().matches(".*1\\.(1[3-9]|2[0-1]).*");

    if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
      int page = 1;
      if (args.length >= 2) {
        try {
          page = Integer.parseInt(args[1]);
          debugLog("[NakedAndAfraid] Parsed help page number: " + page);
        } catch (NumberFormatException ignored) {
          sender.sendMessage("§cInvalid help page number. Showing page 1.");
          debugLog("[NakedAndAfraid] Invalid help page number: " + args[1]);
        }
      }
      sendHelpMessage(sender, page);
      debugLog("[NakedAndAfraid] Sent help message to " + sender.getName() + " for page " + page);
      return true;
    }

    String subCommand = args[0].toLowerCase();
    if (subCommand.equals("reloadconfig")) {
      if (!sender.hasPermission("nakedandafraid.reload")) {
        sender.sendMessage("§cYou don't have permission to execute this command.");
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
      sender.sendMessage("§aNaked and Afraid config reloaded.");
      debugLog("[NakedAndAfraid] Sent config reload confirmation to " + sender.getName());
      return true;
    } else if (subCommand.equals("spawn")) {
      if (!sender.hasPermission("nakedandafraid.spawn")) {
        sender.sendMessage("§cYou don't have permission to execute this command.");
        debugLog("[NakedAndAfraid] " + sender.getName() + " lacks permission for spawn command");
        return isPre113 ? false : true;
      }
      boolean result = spawnManager.handleCommand(sender, args);
      debugLog("[NakedAndAfraid] Spawn command result for " + sender.getName() + ": " + result);
      return result;
    } else if (subCommand.equals("team")) {
      if (!sender.hasPermission("nakedandafraid.team")) {
        sender.sendMessage("§cYou don't have permission to execute this command.");
        debugLog("[NakedAndAfraid] " + sender.getName() + " lacks permission for team command");
        return isPre113 ? false : true;
      }
      boolean result = teamCommands.handleTeamCommand(sender, args);
      debugLog("[NakedAndAfraid] Team command result for " + sender.getName() + ": " + result);
      return result;
    } else if (subCommand.equals("user")) {
      if (!sender.hasPermission("nakedandafraid.user")) {
        sender.sendMessage("§cYou don't have permission to execute this command.");
        debugLog("[NakedAndAfraid] " + sender.getName() + " lacks permission for user command");
        return isPre113 ? false : true;
      }
      boolean result = teamCommands.handleUserCommand(sender, args);
      debugLog("[NakedAndAfraid] User command result for " + sender.getName() + ": " + result);
      return result;
    } else {
      sender.sendMessage("§cUnknown subcommand. Use /nf help for commands.");
      debugLog("[NakedAndAfraid] Unknown subcommand from " + sender.getName() + ": " + args[0]);
      return isPre113 ? false : true;
    }
  }

  /**
   * Handles tab-completion for NakedAndAfraid's commands and subcommands.
   */
  public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
    debugLog("[NakedAndAfraid] Processing tab completion for " + sender.getName() +
            ": " + command.getName() + " " + String.join(" ", args));

    if (!(command.getName().equalsIgnoreCase("nf") || command.getName().equalsIgnoreCase("nakedafraid"))) {
      debugLog("[NakedAndAfraid] Invalid command for tab completion: " + command.getName());
      return null;
    }

    if (args.length == 1) {
      List<String> completions = Arrays.asList("help", "reloadconfig", "spawn", "team", "user");
      debugLog("[NakedAndAfraid] Tab completion for first arg: " + completions);
      return completions;
    }

    String subCommand = args[0].toLowerCase();
    if (subCommand.equals("spawn")) {
      if (args.length == 2) {
        List<String> completions = Arrays.asList("create", "rename", "remove", "list", "tp", "tpall");
        debugLog("[NakedAndAfraid] Tab completion for spawn subcommand: " + completions);
        return completions;
      }
      if (args.length == 3) {
        if (args[1].equalsIgnoreCase("create")) {
          debugLog("[NakedAndAfraid] Tab completion for spawn create: empty list");
          return new ArrayList<>();
        }
        if (args[1].equalsIgnoreCase("rename") || args[1].equalsIgnoreCase("remove") || args[1].equalsIgnoreCase("tp")) {
          List<String> completions = new ArrayList<>(spawnManager.getSpawns().keySet());
          debugLog("[NakedAndAfraid] Tab completion for spawn rename/remove/tp: " + completions);
          return completions;
        }
      }
      if (args.length == 4) {
        if (args[1].equalsIgnoreCase("create") || args[1].equalsIgnoreCase("tp")) {
          List<String> completions = new ArrayList<>();
          for (Player player : Bukkit.getOnlinePlayers()) {
            completions.add(player.getName());
          }
          debugLog("[NakedAndAfraid] Tab completion for spawn create/tp players: " + completions);
          return completions;
        }
        if (args[1].equalsIgnoreCase("rename")) {
          debugLog("[NakedAndAfraid] Tab completion for spawn rename: empty list");
          return new ArrayList<>();
        }
      }
    } else if (subCommand.equals("team")) {
      if (args.length == 2) {
        List<String> completions = Arrays.asList("create", "remove", "list", "block", "setblock", "meta");
        debugLog("[NakedAndAfraid] Tab completion for team subcommand: " + completions);
        return completions;
      }
      if (args.length == 3) {
        if (args[1].equalsIgnoreCase("remove") || args[1].equalsIgnoreCase("setblock") || args[1].equalsIgnoreCase("block")) {
          List<String> completions = new ArrayList<>();
          for (TeamsManager.Team team : teamsManager.getTeams()) {
            completions.add(team.getName());
          }
          debugLog("[NakedAndAfraid] Tab completion for team remove/setblock/block: " + completions);
          return completions;
        }
        if (args[1].equalsIgnoreCase("create") || args[1].equalsIgnoreCase("meta")) {
          debugLog("[NakedAndAfraid] Tab completion for team create/meta: empty list");
          return new ArrayList<>();
        }
      }
      if (args.length == 4) {
        if (args[1].equalsIgnoreCase("create")) {
          List<String> colors = validTeamColors();
          List<String> completions = new ArrayList<>();
          for (String c : colors) {
            if (c.startsWith(args[2].toUpperCase())) {
              completions.add(c);
            }
          }
          debugLog("[NakedAndAfraid] Tab completion for team create colors: " + completions);
          return completions;
        }
        if (args[1].equalsIgnoreCase("block") && args[2].equalsIgnoreCase("selector")) {
          List<String> completions = new ArrayList<>();
          for (Player player : Bukkit.getOnlinePlayers()) {
            completions.add(player.getName());
          }
          debugLog("[NakedAndAfraid] Tab completion for team block selector players: " + completions);
          return completions;
        }
        if (args[1].equalsIgnoreCase("setblock") && sender instanceof Player) {
          Player player = (Player) sender;
          List<String> completions = Collections.singletonList(String.valueOf(player.getLocation().getBlockX()));
          debugLog("[NakedAndAfraid] Tab completion for team setblock x: " + completions);
          return completions;
        }
        if (args[1].equalsIgnoreCase("meta") && args[2].equalsIgnoreCase("color")) {
          List<String> completions = Arrays.asList("get", "set");
          debugLog("[NakedAndAfraid] Tab completion for team meta color: " + completions);
          return completions;
        }
      }
      if (args.length == 5) {
        if (args[1].equalsIgnoreCase("setblock") && sender instanceof Player) {
          Player player = (Player) sender;
          List<String> completions = Collections.singletonList(String.valueOf(player.getLocation().getBlockY()));
          debugLog("[NakedAndAfraid] Tab completion for team setblock y: " + completions);
          return completions;
        }
        if (args[1].equalsIgnoreCase("meta") && args[2].equalsIgnoreCase("color") && args[3].equalsIgnoreCase("set")) {
          List<String> colors = validTeamColors();
          List<String> completions = new ArrayList<>();
          for (String c : colors) {
            if (c.startsWith(args[4].toUpperCase())) {
              completions.add(c);
            }
          }
          debugLog("[NakedAndAfraid] Tab completion for team meta color set: " + completions);
          return completions;
        }
      }
      if (args.length == 6 && args[1].equalsIgnoreCase("setblock") && sender instanceof Player) {
        Player player = (Player) sender;
        List<String> completions = Collections.singletonList(String.valueOf(player.getLocation().getBlockZ()));
        debugLog("[NakedAndAfraid] Tab completion for team setblock z: " + completions);
        return completions;
      }
    } else if (subCommand.equals("user")) {
      if (args.length == 2) {
        List<String> completions = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
          completions.add(player.getName());
        }
        debugLog("[NakedAndAfraid] Tab completion for user players: " + completions);
        return completions;
      }
      if (args.length == 3 && args[2].equalsIgnoreCase("team")) {
        List<String> completions = Arrays.asList("add", "remove", "list");
        debugLog("[NakedAndAfraid] Tab completion for user team subcommand: " + completions);
        return completions;
      }
      if (args.length == 4 && args[2].equalsIgnoreCase("team") &&
              (args[3].equalsIgnoreCase("add") || args[3].equalsIgnoreCase("remove"))) {
        List<String> completions = new ArrayList<>();
        for (TeamsManager.Team team : teamsManager.getTeams()) {
          completions.add(team.getName());
        }
        debugLog("[NakedAndAfraid] Tab completion for user team add/remove: " + completions);
        return completions;
      }
    }

    debugLog("[NakedAndAfraid] Tab completion returned empty list");
    return new ArrayList<>();
  }

  /**
   * Retrieves the teleport helper instance.
   *
   * @return The {@link TeleportHelper} for this plugin.
   */
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
  private void sendHelpMessage(CommandSender sender, int page) {
    debugLog("[NakedAndAfraid] Preparing help message for " + sender.getName() + ", page " + page);
    List<String> helpLines = Arrays.asList(
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

    sender.sendMessage("§6==== NakedAndAfraid Help ====");

    int startIndex = (page - 1) * HELP_LINES_PER_PAGE;
    int endIndex = Math.min(startIndex + HELP_LINES_PER_PAGE, helpLines.size());

    for (int i = startIndex; i < endIndex; i++) {
      sender.sendMessage(helpLines.get(i));
    }

    sender.sendMessage("§ePage " + page + " of " + totalPages);
    debugLog("[NakedAndAfraid] Sent help page " + page + " to " + sender.getName());
  }

  private List<String> validTeamColors() {
    debugLog("[NakedAndAfraid] Retrieving valid team colors");
    return Arrays.asList(
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

    console.sendMessage(" ");
    console.sendMessage("§6__   ____  _______ ");
    console.sendMessage("§6| \\ | | | | ____||");
    console.sendMessage("§6|  \\| | | | ||_   ");
    console.sendMessage("§6| |\\| | | | __||  ");
    console.sendMessage("§6|_| \\_|_| |_||    ");
    console.sendMessage("");
    console.sendMessage("§6NakedAndAfraid Plugin §4v" + plugin.getDescription().getVersion());
    console.sendMessage("§eRunning on §b" + Bukkit.getServer().getName() + " §f" + Bukkit.getServer().getVersion());
    console.sendMessage("");
    console.sendMessage("§aNakedAndAfraid Plugin enabled successfully!");
    console.sendMessage("");

    String currentVersion = plugin.getDescription().getVersion();
    debugLog("[NakedAndAfraid] Checking version: current=" + currentVersion);
    VersionChecker versionChecker = new VersionChecker(this);
    String latestVersion = versionChecker.getLatestVersion();

    if (latestVersion != null && versionChecker.isOutdated(currentVersion)) {
      plugin.getLogger().warning("[NakedAndAfraid] There is a new Naked And Afraid Plugin version available for download: " +
              latestVersion + " (Current: " + currentVersion + ")");
      console.sendMessage("§6[NakedAndAfraid] §cDownload it here: §chttps://modrinth.com/plugin/naked-and-afraid-plugin/versions");
      debugLog("[NakedAndAfraid] Notified console of outdated version: " + latestVersion);
    } else {
      debugLog("[NakedAndAfraid] Version is up to date or no latest version available");
    }
  }

  public org.bukkit.configuration.file.FileConfiguration getConfig() {
    return plugin.getConfig();
  }

  private void saveDefaultConfig() {
    plugin.saveDefaultConfig();
  }

  private void reloadConfig() {
    plugin.reloadConfig();
  }
}