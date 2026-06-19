package com.crimsonwarpedcraft.nakedandafraid.v1_8;

import com.crimsonwarpedcraft.nakedandafraid.common.AbstractNakedAndAfraid;
import com.crimsonwarpedcraft.nakedandafraid.v1_8.listeners.ArmorDamageListener;
import com.crimsonwarpedcraft.nakedandafraid.v1_8.listeners.ChatRestrictionListener;
import com.crimsonwarpedcraft.nakedandafraid.v1_8.listeners.GlobalDeathSoundListener;
import com.crimsonwarpedcraft.nakedandafraid.v1_8.listeners.JoinQuitMessageSuppressor;
import com.crimsonwarpedcraft.nakedandafraid.v1_8.listeners.TabListClearer;
import com.crimsonwarpedcraft.nakedandafraid.v1_8.listeners.TeamListener;
import com.crimsonwarpedcraft.nakedandafraid.v1_8.listeners.VersionNotifyListener;
import com.crimsonwarpedcraft.nakedandafraid.v1_8.spawn.SpawnManager;
import com.crimsonwarpedcraft.nakedandafraid.v1_8.team.TeamCommands;
import com.crimsonwarpedcraft.nakedandafraid.v1_8.team.TeamsManager;
import com.crimsonwarpedcraft.nakedandafraid.v1_8.util.TeleportHelper;
import com.crimsonwarpedcraft.nakedandafraid.v1_8.util.TeleportHelperExtension;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.io.*;
import java.util.*;

/**
 * NakedAndAfraid implementation for Minecraft 1.8–1.16.
 * <p>
 * Differences from the shared base:
 * <ul>
 *   <li>No Adventure API - all messages use §-colour codes.</li>
 *   <li>No {@code TotemDisablerListener} (event doesn't exist pre-1.11).</li>
 *   <li>{@code TeleportHelperExtension} only registered on MC 1.9+.</li>
 *   <li>{@code setWorldList} uses manual file I/O (Bukkit API is too limited here).</li>
 *   <li>{@code ArmorDamageListener} takes {@code this} (NakedAndAfraid), not {@code plugin}.</li>
 * </ul>
 */
public class NakedAndAfraid extends AbstractNakedAndAfraid {

  // -----------------------------------------------------------------------
  // Typed listener / manager fields
  // -----------------------------------------------------------------------

  private ChatRestrictionListener chatRestrictionListener;
  private ArmorDamageListener armorDamageListener;
  private JoinQuitMessageSuppressor joinQuitMessageSuppressor;
  private TabListClearer tabListClearer;

  private SpawnManager spawnManager;
  private TeamsManager teamsManager;
  private TeamCommands teamCommands;
  private TeleportHelper teleportHelper;

  public NakedAndAfraid(Plugin plugin) {
    super(plugin);
  }

  // -----------------------------------------------------------------------
  // initManagers - registers the always-on listeners
  // -----------------------------------------------------------------------

  @Override
  protected void initManagers() {
    teamsManager = new TeamsManager(this);
    teamCommands = new TeamCommands(teamsManager, this);
    debugLog("[NakedAndAfraid] Initialized TeamsManager and TeamCommands");

    plugin.getServer().getPluginManager()
            .registerEvents(new GlobalDeathSoundListener(this), plugin);
    plugin.getServer().getPluginManager()
            .registerEvents(new TeamListener(this, teamsManager, teamCommands), plugin);
  }

  @Override
  protected void initSpawnManager() {
    spawnManager = new SpawnManager(this);
    spawnManager.loadSpawns();
    debugLog("[NakedAndAfraid] Initialized SpawnManager and loaded spawns");
  }

  @Override
  protected void initTeleportHelper() {
    teleportHelper = new TeleportHelper(plugin, this);
    // TeleportHelperExtension requires the 1.9+ elytra/off-hand API
    String mcMinor = Bukkit.getBukkitVersion().split("-")[0].split("\\.")[1];
    if (Integer.parseInt(mcMinor) >= 9) {
      new TeleportHelperExtension(plugin, this, teleportHelper);
    }
    debugLog("[NakedAndAfraid] Initialized TeleportHelper");
  }

  @Override
  protected Listener createVersionNotifyListener() {
    return new VersionNotifyListener(this);
  }

  // v1_8 has no always-on version-specific listeners beyond what initManagers covers
  @Override
  protected void registerVersionListeners() { }

  @Override
  protected void unregisterVersionListeners() { }

  // -----------------------------------------------------------------------
  // Toggleable listener factories / accessors
  // -----------------------------------------------------------------------

  @Override
  protected Listener createChatRestrictionListener() {
    return new ChatRestrictionListener(this);
  }

  @Override
  protected Listener getChatRestrictionListener() {
    return chatRestrictionListener;
  }

  @Override
  protected void setChatRestrictionListener(Listener l) {
    chatRestrictionListener = (ChatRestrictionListener) l;
  }

  @Override
  protected Listener createArmorDamageListener() {
    // v1_8 variant takes 'this' (NakedAndAfraid), not the Plugin
    return new ArmorDamageListener(this);
  }

  @Override
  protected Listener getArmorDamageListener() {
    return armorDamageListener;
  }

  @Override
  protected void setArmorDamageListener(Listener l) {
    armorDamageListener = (ArmorDamageListener) l;
  }

  @Override
  protected void refreshArmorTasks() {
    if (armorDamageListener != null) {
      armorDamageListener.refreshArmorTasks();
    }
  }

  @Override
  protected void disableArmorDamage() {
    if (armorDamageListener != null) {
      armorDamageListener.disableAllTasks();
      HandlerList.unregisterAll(armorDamageListener);
      debugLog("[NakedAndAfraid] Disabled/unregistered ArmorDamageListener");
      armorDamageListener = null;
    }
  }

  @Override
  protected Listener createJoinQuitSuppressor() {
    return new JoinQuitMessageSuppressor(this);
  }

  @Override
  protected Listener getJoinQuitSuppressor() {
    return joinQuitMessageSuppressor;
  }

  @Override
  protected void setJoinQuitSuppressor(Listener l) {
    joinQuitMessageSuppressor = (JoinQuitMessageSuppressor) l;
  }

  // -----------------------------------------------------------------------
  // Tab list clearer
  // -----------------------------------------------------------------------

  @Override
  protected void enableTabListClearer() {
    tabListClearer = new TabListClearer(this, plugin);
    tabListClearer.enable();
  }

  @Override
  protected void disableTabListClearer() {
    if (tabListClearer != null && tabListClearer.isEnabled()) {
      tabListClearer.disable();
      debugLog("[NakedAndAfraid] Disabled TabListClearer");
      tabListClearer = null;
    }
  }

  @Override
  protected void applyTabListClearerToPlayer(Player player) {
    if (tabListClearer != null) {
      tabListClearer.applyToPlayer(player);
    }
  }

  // -----------------------------------------------------------------------
  // Version listener reload hooks - no totem in 1.8
  // -----------------------------------------------------------------------

  @Override
  protected void doUnregisterVersionListeners() { }

  @Override
  protected void doReloadVersionListeners() { }

  // -----------------------------------------------------------------------
  // Command delegation
  // -----------------------------------------------------------------------

  @Override
  protected boolean handleSpawnCommand(CommandSender sender, String[] args) {
    return spawnManager.handleCommand(sender, args);
  }

  @Override
  protected boolean handleTeamsCommand(CommandSender sender, String[] args) {
    return teamCommands.handleTeamsCommand(sender, args);
  }

  @Override
  protected boolean handleTeamCommand(CommandSender sender, String[] args) {
    return teamCommands.handleTeamCommand(sender, args);
  }

  @Override
  protected boolean handleUserCommand(CommandSender sender, String[] args) {
    return teamCommands.handleUserCommand(sender, args);
  }

  // -----------------------------------------------------------------------
  // Tab-complete data sources
  // -----------------------------------------------------------------------

  @Override
  protected Set<String> getSpawnNames() {
    return spawnManager.getSpawns().keySet();
  }

  @Override
  protected List<String> getTeamNames() {
    List<String> names = new ArrayList<>();
    for (TeamsManager.Team t : teamsManager.getTeams()) {
      names.add(t.getName());
    }
    return names;
  }

  // -----------------------------------------------------------------------
  // Manager utilities
  // -----------------------------------------------------------------------

  @Override
  protected void refreshManagerWorlds() {
    if (spawnManager != null)  spawnManager.refreshWorlds();
    if (teamsManager != null)  teamsManager.refreshWorlds();
  }

  @Override
  protected void reloadSpawnManager() {
    spawnManager.loadSpawns();
    debugLog("[NakedAndAfraid] Reloaded spawns");
  }

  @Override
  protected void saveSpawnManager() {
    if (spawnManager != null) {
      spawnManager.saveSpawns();
      debugLog("[NakedAndAfraid] Saved spawns");
    }
  }

  public TeleportHelper getTeleportHelper() {
    return teleportHelper;
  }

  // -----------------------------------------------------------------------
  // setWorldList - manual file I/O (Bukkit 1.8 API limitation)
  // -----------------------------------------------------------------------

  /**
   * Adds every loaded world to {@code enabled-worlds} in config.yml.
   * Written with raw file I/O because {@code FileConfiguration.save()} was
   * unreliable for preserving comments and formatting in early Bukkit builds.
   */
  @Override
  protected void setWorldList() {
    debugLog("[NakedAndAfraid] Starting setWorldList");
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

      try (BufferedReader reader =
                   new BufferedReader(new FileReader(configFile))) {
        int lineNum = 0;
        String line;
        while ((line = reader.readLine()) != null) {
          lines.add(line);
          String trimmed = line.trim();

          if (trimmed.startsWith("enabled-worlds:")) {
            inEnabledWorlds = true;
            enabledWorldsIndex = lineNum;
            enabledWorldsLine = line;
          }

          if (inEnabledWorlds) {
            if (!trimmed.startsWith("#")
                    && !line.startsWith(" ")
                    && trimmed.contains(": ")
                    && lineNum > enabledWorldsIndex) {
              inEnabledWorlds = false;
            } else if (inEnabledWorlds
                    && trimmed.contains(":")
                    && line.startsWith("  ")
                    && !trimmed.startsWith("-")) {
              enabledWorldsLineIndices.add(lineNum);
              String[] parts = trimmed.split(":", 2);
              if (parts.length > 1) {
                String worldName = parts[0].trim();
                if (!worldName.isEmpty()) {
                  boolean enabled =
                          parts[1].trim().equalsIgnoreCase("true");
                  existingWorlds.put(worldName, enabled);
                }
              }
            }
          }
          lineNum++;
        }
      }

      boolean hasWorlds = false;
      for (org.bukkit.World world : Bukkit.getWorlds()) {
        if (!existingWorlds.containsKey(world.getName())) {
          existingWorlds.put(world.getName(), true);
          debugLog("[NakedAndAfraid] Added world " + world.getName());
        }
        hasWorlds = true;
      }

      if (enabledWorldsIndex == -1) return;

      boolean needsRewrite =
              !enabledWorldsLineIndices.isEmpty() || hasWorlds;
      if (!needsRewrite) return;

      for (int idx : enabledWorldsLineIndices) {
        if (idx < lines.size()) lines.set(idx, "");
      }

      if (hasWorlds && enabledWorldsLine != null
              && enabledWorldsLine.trim().endsWith("[]")) {
        lines.set(enabledWorldsIndex,
                enabledWorldsLine.trim().replace("[]", "").trim());
      } else if (!hasWorlds && existingWorlds.isEmpty()
              && enabledWorldsLine != null
              && !enabledWorldsLine.trim().endsWith("[]")) {
        lines.set(enabledWorldsIndex, enabledWorldsLine.trim() + " []");
      }

      List<String> worldLines = new ArrayList<>();
      for (Map.Entry<String, Boolean> entry : existingWorlds.entrySet()) {
        String indent = "  ";
        if (enabledWorldsLine != null) {
          int keyIndent = enabledWorldsLine.indexOf("enabled-worlds:");
          if (keyIndent > 0) {
            char[] spaces = new char[keyIndent + 2];
            Arrays.fill(spaces, ' ');
            indent = new String(spaces);
          }
        }
        worldLines.add(indent + entry.getKey() + ": " + entry.getValue());
      }
      lines.addAll(enabledWorldsIndex + 1, worldLines);

      try (BufferedWriter writer =
                   new BufferedWriter(new FileWriter(configFile))) {
        for (String l : lines) {
          if (!l.trim().isEmpty() || l.contains("#")) {
            writer.write(l);
            writer.newLine();
          }
        }
      }
      debugLog("[NakedAndAfraid] Enabled worlds updated successfully");

    } catch (IOException e) {
      plugin.getLogger().severe(
              "Failed to update enabled-worlds: " + e.getMessage());
    }
  }
}