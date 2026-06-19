package com.crimsonwarpedcraft.nakedandafraid.v1_17;

import com.crimsonwarpedcraft.nakedandafraid.common.AbstractNakedAndAfraid;
import com.crimsonwarpedcraft.nakedandafraid.v1_17.listeners.ArmorDamageListener;
import com.crimsonwarpedcraft.nakedandafraid.v1_17.listeners.ChatRestrictionListener;
import com.crimsonwarpedcraft.nakedandafraid.v1_17.listeners.GlobalDeathSoundListener;
import com.crimsonwarpedcraft.nakedandafraid.v1_17.listeners.JoinQuitMessageSuppressor;
import com.crimsonwarpedcraft.nakedandafraid.v1_17.listeners.TabListClearer;
import com.crimsonwarpedcraft.nakedandafraid.v1_17.listeners.TeamListener;
import com.crimsonwarpedcraft.nakedandafraid.v1_17.listeners.TotemDisablerListener;
import com.crimsonwarpedcraft.nakedandafraid.v1_17.listeners.VersionNotifyListener;
import com.crimsonwarpedcraft.nakedandafraid.v1_17.spawn.SpawnManager;
import com.crimsonwarpedcraft.nakedandafraid.v1_17.team.TeamCommands;
import com.crimsonwarpedcraft.nakedandafraid.v1_17.team.TeamsManager;
import com.crimsonwarpedcraft.nakedandafraid.v1_17.util.TeleportHelper;
import com.crimsonwarpedcraft.nakedandafraid.v1_17.util.TeleportHelperExtension;
import io.papermc.lib.PaperLib;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.io.*;
import java.util.*;

/**
 * NakedAndAfraid implementation for Minecraft 1.17–1.18.
 * <p>
 * Differences from the shared base:
 * <ul>
 *   <li>Adventure API guarded by {@link #isAdventureSupported()} (true on 1.19+).</li>
 *   <li>{@code TotemDisablerListener} added as a toggleable listener.</li>
 *   <li>{@code TeleportHelperExtension} always registered (MC is always &gt;= 1.9 here).</li>
 *   <li>{@code setWorldList} uses the same manual file I/O as v1_8.</li>
 *   <li>{@code ArmorDamageListener} takes {@code plugin}, not {@code this}.</li>
 * </ul>
 */
public class NakedAndAfraid extends AbstractNakedAndAfraid {

  // -----------------------------------------------------------------------
  // Typed listener / manager fields
  // -----------------------------------------------------------------------

  private ChatRestrictionListener chatRestrictionListener;
  private ArmorDamageListener armorDamageListener;
  private JoinQuitMessageSuppressor joinQuitMessageSuppressor;
  private TotemDisablerListener totemDisablerListener;
  private TabListClearer tabListClearer;

  private SpawnManager spawnManager;
  private TeamsManager teamsManager;
  private TeamCommands teamCommands;
  private TeleportHelper teleportHelper;

  public NakedAndAfraid(Plugin plugin) {
    super(plugin);
  }

  // -----------------------------------------------------------------------
  // Adventure guard
  // -----------------------------------------------------------------------

  /**
   * Returns {@code true} when running on a server that natively ships the
   * Adventure API (Paper 1.19+).  On 1.17/1.18 the API is available as a
   * soft-dependency but we keep legacy codes for safety.
   */
  private boolean isAdventureSupported() {
    try {
      String minor = Bukkit.getBukkitVersion().split("-")[0].split("\\.")[1];
      return Integer.parseInt(minor) >= 19;
    } catch (Exception e) {
      return false;
    }
  }

  // -----------------------------------------------------------------------
  // initManagers
  // -----------------------------------------------------------------------

  @Override
  protected void initManagers() {
    PaperLib.suggestPaper(plugin);

    teamsManager = new TeamsManager(plugin);
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
    new TeleportHelperExtension(plugin, this, teleportHelper);
    debugLog("[NakedAndAfraid] Initialized TeleportHelper");
  }

  @Override
  protected Listener createVersionNotifyListener() {
    return new VersionNotifyListener(this);
  }

  // v1_17: TotemDisablerListener is registered via reloadListeners, not here
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
    // v1_17 variant takes Plugin, not NakedAndAfraid
    return new ArmorDamageListener(plugin);
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
  // Totem listener reload hooks
  // -----------------------------------------------------------------------

  @Override
  protected void doUnregisterVersionListeners() {
    if (totemDisablerListener != null) {
      HandlerList.unregisterAll(totemDisablerListener);
      debugLog("[NakedAndAfraid] Unregistered TotemDisablerListener");
      totemDisablerListener = null;
    }
  }

  @Override
  protected void doReloadVersionListeners() {
    if (plugin.getConfig().getBoolean("disable-totems", true)) {
      totemDisablerListener = new TotemDisablerListener(this);
      plugin.getServer().getPluginManager()
              .registerEvents(totemDisablerListener, plugin);
      plugin.getLogger().info("Naked And Afraid - Totem Disabling Enabled.");
      debugLog("[NakedAndAfraid] Enabled TotemDisablerListener");
    } else {
      plugin.getLogger().info("Naked And Afraid - Totem Disabling Disabled.");
      debugLog("[NakedAndAfraid] TotemDisablerListener not enabled");
    }
  }

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
    if (spawnManager != null) spawnManager.refreshWorlds();
    if (teamsManager  != null) teamsManager.refreshWorlds();
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
  // setWorldList - manual file I/O (same approach as v1_8)
  // -----------------------------------------------------------------------

  @Override
  public void setWorldList() {
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
            } else if (trimmed.contains(":") && line.startsWith("  ") && !trimmed.startsWith("-")) {
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

      if (hasWorlds && enabledWorldsLine.trim().endsWith("[]")) {
        lines.set(enabledWorldsIndex,
                enabledWorldsLine.trim().replace("[]", "").trim());
      } else if (!hasWorlds && existingWorlds.isEmpty() && !enabledWorldsLine.trim().endsWith("[]")) {
        lines.set(enabledWorldsIndex, enabledWorldsLine.trim() + " []");
      }

      final String fEnabledWorldsLine = enabledWorldsLine;
      List<String> worldLines = new ArrayList<>();
      for (Map.Entry<String, Boolean> entry : existingWorlds.entrySet()) {
        String indent = "  ";
          int keyIndent = fEnabledWorldsLine.indexOf("enabled-worlds:");
          if (keyIndent > 0) {
            indent = " ".repeat(keyIndent + 2);
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

  // -----------------------------------------------------------------------
  // Adventure-guarded message overrides
  // -----------------------------------------------------------------------

  @Override
  protected void sendErrorMessage(CommandSender sender, String text) {
    if (isAdventureSupported()) {
      sender.sendMessage(
              Component.text(text).color(NamedTextColor.RED));
    } else {
      sender.sendMessage("§c" + text);
    }
  }

  @Override
  protected void sendSuccessMessage(CommandSender sender, String text) {
    if (isAdventureSupported()) {
      sender.sendMessage(
              Component.text(text).color(NamedTextColor.GREEN));
    } else {
      sender.sendMessage("§a" + text);
    }
  }

  @Override
  protected void sendConsoleBanner(ConsoleCommandSender console) {
    if (isAdventureSupported()) {
      console.sendMessage(Component.text("__   ____  _______ ").color(NamedTextColor.GOLD));
      console.sendMessage(Component.text("| \\ | | | | ____||").color(NamedTextColor.GOLD));
      console.sendMessage(Component.text("|  \\| | | | ||_   ").color(NamedTextColor.GOLD));
      console.sendMessage(Component.text("| |\\| | | | __||  ").color(NamedTextColor.GOLD));
      console.sendMessage(Component.text("|_| \\_|_| |_||    ").color(NamedTextColor.GOLD));
      console.sendMessage(Component.empty());
      console.sendMessage(
              Component.text("NakedAndAfraid Plugin ").color(NamedTextColor.GOLD)
                      .append(Component.text(
                                      "v" + plugin.getDescription().getVersion())
                              .color(NamedTextColor.DARK_RED)));
      console.sendMessage(
              Component.text("Running on ").color(NamedTextColor.YELLOW)
                      .append(Component.text(
                              Bukkit.getServer().getName()).color(NamedTextColor.AQUA))
                      .append(Component.text(
                              " " + Bukkit.getServer().getVersion()).color(NamedTextColor.WHITE)));
      console.sendMessage(Component.empty());
      console.sendMessage(
              Component.text("NakedAndAfraid Plugin enabled successfully!")
                      .color(NamedTextColor.GREEN));
      console.sendMessage(Component.empty());
    } else {
      super.sendConsoleBanner(console);
    }
  }

  @Override
  protected void sendOutdatedWarning(ConsoleCommandSender console,
                                     String current, String latest) {
    if (isAdventureSupported()) {
      String warning = PlainTextComponentSerializer.plainText().serialize(
              Component.text("[NakedAndAfraid] ").color(NamedTextColor.GOLD)
                      .append(Component.text(
                                      "There is a new version available: "
                                              + latest + " (Current: " + current + ")")
                              .color(NamedTextColor.RED)));
      plugin.getLogger().warning(warning);
      console.sendMessage(
              Component.text("[NakedAndAfraid] ").color(NamedTextColor.GOLD)
                      .append(Component.text("Download it here: ").color(NamedTextColor.RED))
                      .append(Component.text(
                                      "https://modrinth.com/plugin/naked-and-afraid-plugin/versions")
                              .color(NamedTextColor.RED)
                              .clickEvent(ClickEvent.openUrl(
                                      "https://modrinth.com/plugin/naked-and-afraid-plugin/versions"))));
    } else {
      super.sendOutdatedWarning(console, current, latest);
    }
  }
}