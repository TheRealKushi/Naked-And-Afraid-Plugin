package com.therealkushi.nakedandafraid.v1_21;

import com.therealkushi.nakedandafraid.common.AbstractNakedAndAfraid;
import com.therealkushi.nakedandafraid.v1_21.listeners.ArmorDamageListener;
import com.therealkushi.nakedandafraid.v1_21.listeners.ChatRestrictionListener;
import com.therealkushi.nakedandafraid.v1_21.listeners.GlobalDeathSoundListener;
import com.therealkushi.nakedandafraid.v1_21.listeners.JoinQuitMessageSuppressor;
import com.therealkushi.nakedandafraid.v1_21.listeners.TabListClearer;
import com.therealkushi.nakedandafraid.v1_21.listeners.TeamListener;
import com.therealkushi.nakedandafraid.v1_21.listeners.TotemDisablerListener;
import com.therealkushi.nakedandafraid.v1_21.listeners.VersionNotifyListener;
import com.therealkushi.nakedandafraid.v1_21.spawn.SpawnManager;
import com.therealkushi.nakedandafraid.v1_21.team.TeamCommands;
import com.therealkushi.nakedandafraid.v1_21.team.TeamsManager;
import com.therealkushi.nakedandafraid.v1_21.util.AdventureMessages;
import com.therealkushi.nakedandafraid.v1_21.util.TeleportHelper;
import com.therealkushi.nakedandafraid.v1_21.util.TeleportHelperExtension;
import io.papermc.lib.PaperLib;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * NakedAndAfraid implementation for Minecraft 1.19–1.21+.
 * <p>
 * Differences from the shared base:
 * <ul>
 *   <li>Adventure API used when present (Paper), with a legacy §-code fallback
 *       when absent (CraftBukkit/Spigot). All direct Adventure type references
 *       live in {@link AdventureMessages}, never in this class - see the
 *       javadoc on that class for why that separation matters.</li>
 *   <li>Additional {@code "teams"} subcommand (plural) for listing/creating teams.</li>
 *   <li>{@code setWorldList} uses the clean {@code ConfigurationSection} API.</li>
 *   <li>{@code TotemDisablerListener} present as a toggleable listener.</li>
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
  // Adventure guard - Paper bundles Adventure, CraftBukkit/Spigot do not.
  // Checking the MC version alone isn't enough since 1.19+ covers both.
  //
  // IMPORTANT: this class must never import or reference Adventure types
  // (Component, NamedTextColor, etc.) directly - not even inside a guarded
  // branch. The JVM verifier resolves every type referenced in a class's
  // method signatures/bodies at class-load time, so a single reference
  // anywhere in this file would make loading NakedAndAfraid itself throw
  // NoClassDefFoundError on servers without Adventure, regardless of any
  // runtime check. All Adventure usage must go through AdventureMessages.
  // -----------------------------------------------------------------------
  private static Boolean adventureAvailable;

  private static boolean isAdventureAvailable() {
    if (adventureAvailable == null) {
      try {
        Class<?> componentClass = Class.forName("net.kyori.adventure.text.Component");
        Player.class.getMethod("sendMessage", componentClass);
        adventureAvailable = true;
      } catch (Throwable t) {
        adventureAvailable = false;
      }
    }
    return adventureAvailable;
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
    if (chatRestrictionListener != null) {
      chatRestrictionListener.shutdownPaperCompanion();
    }
    chatRestrictionListener = (ChatRestrictionListener) l;
  }

  @Override
  protected Listener createArmorDamageListener() {
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
      armorDamageListener.shutdownPaperCompanion();
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
  // setWorldList - ConfigurationSection API (cleaner than manual file I/O)
  // -----------------------------------------------------------------------

  @Override
  public void setWorldList() {
    debugLog("[NakedAndAfraid] Starting setWorldList");
    org.bukkit.configuration.file.FileConfiguration config = plugin.getConfig();
    ConfigurationSection enabledWorlds =
            config.getConfigurationSection("enabled-worlds");
    if (enabledWorlds == null) {
      enabledWorlds = config.createSection("enabled-worlds");
      debugLog("[NakedAndAfraid] Created new enabled-worlds section");
    }

    boolean changed = false;
    for (World world : Bukkit.getWorlds()) {
      String name = world.getName();
      if (!enabledWorlds.contains(name)) {
        enabledWorlds.set(name, true);
        changed = true;
        debugLog("[NakedAndAfraid] Added world " + name);
      }
    }

    if (changed) {
      plugin.saveConfig();
      debugLog("[NakedAndAfraid] Saved updated config with new worlds");
    } else {
      debugLog("[NakedAndAfraid] No changes needed for enabled-worlds section");
    }
  }

  // -----------------------------------------------------------------------
  // Help lines - appends the v1_21-specific "teams" commands
  // -----------------------------------------------------------------------

  @Override
  protected List<String> getHelpLines() {
    List<String> lines = new ArrayList<>(super.getHelpLines());
    // Replace generic "team create/remove/list" entries with "teams" variants
    lines.add("§e/nf teams create (team-name) (team-color) §7- Define a new team");
    lines.add("§e/nf teams remove (team-name) §7- Delete an existing team");
    lines.add("§e/nf teams list §7- List all existing teams");
    lines.add("§e/nf team (team-name) setblock (x) (y) (z) §7- Set team block");
    lines.add("§e/nf team (team-name) block selector (player) §7- Set block by sight");
    return lines;
  }

  // -----------------------------------------------------------------------
  // Message overrides - Adventure when present, legacy §-codes otherwise.
  // All Adventure calls are delegated to AdventureMessages; this class
  // itself must never reference Adventure types directly (see the guard
  // comment above isAdventureAvailable()).
  // -----------------------------------------------------------------------

  @Override
  protected void sendErrorMessage(CommandSender sender, String text) {
    if (isAdventureAvailable()) {
      AdventureMessages.sendError(sender, text);
    } else {
      sender.sendMessage("§c" + text);
    }
  }

  @Override
  protected void sendSuccessMessage(CommandSender sender, String text) {
    if (isAdventureAvailable()) {
      AdventureMessages.sendSuccess(sender, text);
    } else {
      sender.sendMessage("§a" + text);
    }
  }

  @Override
  protected void sendRawMessage(CommandSender sender, String legacyText) {
    if (isAdventureAvailable()) {
      AdventureMessages.sendRaw(sender, legacyText);
    } else {
      sender.sendMessage(legacyText);
    }
  }

  @Override
  protected void sendConsoleBanner(ConsoleCommandSender console) {
    if (isAdventureAvailable()) {
      AdventureMessages.sendConsoleBanner(console, plugin);
    } else {
      super.sendConsoleBanner(console);
    }
  }

  @Override
  protected void sendOutdatedWarning(ConsoleCommandSender console,
                                     String current, String latest) {
    if (isAdventureAvailable()) {
      AdventureMessages.sendOutdatedWarning(console, plugin, current, latest);
    } else {
      super.sendOutdatedWarning(console, current, latest);
    }
  }
}