package com.crimsonwarpedcraft.nakedandafraid;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.crimsonwarpedcraft.nakedandafraid.v1_21.NakedAndAfraid;
import com.crimsonwarpedcraft.nakedandafraid.v1_21.listeners.TabListClearer;
import com.crimsonwarpedcraft.nakedandafraid.v1_21.spawn.SpawnManager;
import com.crimsonwarpedcraft.nakedandafraid.v1_21.team.TeamsManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.bukkit.World;
import org.bukkit.Location;
import org.bukkit.permissions.PermissionAttachment;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class NakedAndAfraidTest {

  private ServerMock server;
  private Plugin mockPlugin;
  private FileConfiguration mockConfig;
  private ConfigurationSection mockEnabledWorlds;
  private NakedAndAfraid pluginClass;
  private Logger testLogger;
  private static final boolean DEBUG_ENABLED = Boolean.getBoolean("test.debug");

  private void testDebugLog(String message) {
    if (DEBUG_ENABLED) {
      testLogger.info("[NakedAndAfraidTest] " + message);
    }
  }

  @BeforeEach
  public void setUp() {
    testLogger = Logger.getLogger("NakedAndAfraidTest");
    testDebugLog("Starting test setup");

    server = MockBukkit.mock();
    testDebugLog("MockBukkit server initialized with version " + server.getBukkitVersion());

    mockPlugin = spy(MockBukkit.createMockPlugin());
    testDebugLog("Mock plugin created with name " + mockPlugin.getName());

    mockConfig = mock(FileConfiguration.class);
    doReturn(mockConfig).when(mockPlugin).getConfig();
    doReturn(true).when(mockConfig).getBoolean("debug-mode", false);
    doReturn(true).when(mockConfig).getBoolean("disable-tab", true);  // Fixed stub to match code's default
    doReturn(false).when(mockConfig).getBoolean("disable-chat", true);  // Match code's default
    doReturn(false).when(mockConfig).getBoolean("armor-damage.enabled", true);  // Match code's default
    doReturn(false).when(mockConfig).getBoolean("disable-join-quit-messages", true);  // Match code's default
    doReturn(false).when(mockConfig).getBoolean("disable-totems", true);  // Match code's default
    doReturn("FIRST").when(mockConfig).getString("multiple-spawn-priority", "FIRST");
    doReturn(10).when(mockConfig).getInt("max-teams", 10);
    doReturn("LODESTONE").when(mockConfig).getString("team-block", "LODESTONE");
    doReturn(Collections.emptyList()).when(mockConfig).getStringList(anyString());
    doReturn(false).when(mockConfig).contains("teams");

    // Enhanced stub for enabled-worlds ConfigurationSection
    mockEnabledWorlds = mock(ConfigurationSection.class);
    doReturn(mockEnabledWorlds).when(mockConfig).getConfigurationSection("enabled-worlds");
    doReturn(mockEnabledWorlds).when(mockConfig).createSection("enabled-worlds");
    doReturn(true).when(mockConfig).isSet("enabled-worlds");
    doReturn(true).when(mockConfig).contains("enabled-worlds");
    doReturn(true).when(mockEnabledWorlds).getBoolean("world", true);
    doReturn(true).when(mockEnabledWorlds).getBoolean("world_nether", true);
    doReturn(true).when(mockConfig).getBoolean("enabled-worlds.world", true);
    doReturn(true).when(mockConfig).getBoolean("enabled-worlds.world_nether", true);
    doReturn(true).when(mockConfig).getBoolean("enabled-worlds.test_world", true);
    Set<String> worldKeys = new HashSet<>();
    worldKeys.add("world");
    worldKeys.add("world_nether");
    doReturn(worldKeys).when(mockEnabledWorlds).getKeys(false);
    doReturn(false).when(mockEnabledWorlds).contains("world");
    doReturn(false).when(mockEnabledWorlds).contains("world_nether");
    doNothing().when(mockEnabledWorlds).set(anyString(), anyBoolean());
    doReturn(mockEnabledWorlds).when(mockEnabledWorlds).getConfigurationSection(anyString());

    // Add worlds to the server
    server.addSimpleWorld("world");
    server.addSimpleWorld("world_nether");

    // Mock ProtocolLib for TabListClearer
    JavaPlugin mockProtocolLibPlugin = mock(JavaPlugin.class);
    doReturn("ProtocolLib").when(mockProtocolLibPlugin).getName();
    doReturn(true).when(mockProtocolLibPlugin).isEnabled();
    ProtocolManager mockProtocolManager = mock(ProtocolManager.class);

    // Spy on PluginManager to stub getPlugin
    PluginManager pluginManager = spy(server.getPluginManager());
    doReturn(mockProtocolLibPlugin).when(pluginManager).getPlugin("ProtocolLib");
    // Set the spied pluginManager back to the server
    try {
      Field pmField = ServerMock.class.getDeclaredField("pluginManager");
      pmField.setAccessible(true);
      pmField.set(server, pluginManager);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException("Failed to set pluginManager field", e);
    }

    pluginManager.enablePlugin(mockProtocolLibPlugin);

    // Mock ProtocolLibrary.getProtocolManager()
    try (MockedStatic<ProtocolLibrary> mockedProtocolLibrary = mockStatic(ProtocolLibrary.class)) {
      mockedProtocolLibrary.when(ProtocolLibrary::getProtocolManager).thenReturn(mockProtocolManager);

      doReturn(Logger.getLogger("NakedAndAfraidPlugin")).when(mockPlugin).getLogger();
      File mockDataFolder = new File("target/mockplugin");
      mockDataFolder.mkdirs();
      doReturn(mockDataFolder).when(mockPlugin).getDataFolder();
      doReturn(true).when(mockPlugin).isEnabled();
      doReturn(server).when(mockPlugin).getServer();
      doNothing().when(mockPlugin).saveDefaultConfig();
      doNothing().when(mockPlugin).saveConfig();  // Prevent actual file I/O
      doNothing().when(mockPlugin).reloadConfig();

      server.getPluginManager().enablePlugin(mockPlugin);
      testDebugLog("Mock plugin enabled");

      try {
        testDebugLog("Attempting to create NakedAndAfraid instance");
        pluginClass = spy(new NakedAndAfraid(mockPlugin));
        pluginClass.onEnable();
        testDebugLog("NakedAndAfraid plugin instance created successfully");
      } catch (Exception e) {
        testDebugLog("Failed to create NakedAndAfraid instance: " + e.getMessage());
        throw new RuntimeException("Failed to initialize NakedAndAfraid in setUp", e);
      }
    }
  }

  @AfterEach
  public void tearDown() {
    testDebugLog("Tearing down test environment");
    new File("target/mockplugin/teams.yml").delete();
    new File("target/mockplugin/spawns.yml").delete();
    MockBukkit.unmock();
    Mockito.framework().clearInlineMocks();
    testDebugLog("MockBukkit server unmocked");
  }

  @Test
  public void testGetPluginReturnsSameInstance() {
    testDebugLog("Starting testGetPluginReturnsSameInstance");
    testDebugLog("Verifying getPlugin returns the mocked plugin instance");
    assertSame(mockPlugin, pluginClass.getPlugin(),
            "getPlugin() must return the plugin provided in the constructor");
    testDebugLog("testGetPluginReturnsSameInstance passed: getPlugin returned expected instance");
  }

  @Test
  public void testTeleportOnCountdownDefaultFalse() {
    testDebugLog("Starting testTeleportOnCountdownDefaultFalse");
    testDebugLog("Checking default value of teleportOnCountdownEnd");
    assertFalse(pluginClass.isTeleportOnCountdownEnd(),
            "teleportOnCountdownEnd should default to false if not set");
    testDebugLog("testTeleportOnCountdownDefaultFalse passed: teleportOnCountdownEnd is false");
  }

  @Test
  public void testMultipleSpawnPriorityDefaultsToFirst() {
    testDebugLog("Starting testMultipleSpawnPriorityDefaultsToFirst");
    testDebugLog("Checking default value of multipleSpawnPriority");
    assertEquals("FIRST", pluginClass.getMultipleSpawnPriority(),
            "multipleSpawnPriority should default to 'FIRST' in uppercase");
    testDebugLog("testMultipleSpawnPriorityDefaultsToFirst passed: multipleSpawnPriority is 'FIRST'");
  }

  @Test
  public void testDebugLogOnlyLogsWhenEnabled() {
    testDebugLog("Starting testDebugLogOnlyLogsWhenEnabled");
    Logger spyLogger = spy(Logger.getLogger("NakedAndAfraidTestLogger"));
    doReturn(spyLogger).when(mockPlugin).getLogger();

    testDebugLog("Calling debugLog with message 'Debug message!'");
    pluginClass.debugLog("Debug message!");
    verify(spyLogger, atLeastOnce()).info("Debug message!");
    testDebugLog("Verified debug message was logged when debug-mode is true");

    doReturn(false).when(mockConfig).getBoolean("debug-mode", false);
    testDebugLog("Calling debugLog with debug-mode false");
    pluginClass.debugLog("Should not log");
    verify(spyLogger, never()).info("Should not log");
    testDebugLog("testDebugLogOnlyLogsWhenEnabled passed: debugLog respects debug-mode");
  }

  @Test
  public void testOnCommandInvalidLabelReturnsFalse() {
    testDebugLog("Starting testOnCommandInvalidLabelReturnsFalse");
    CommandSender sender = server.addPlayer();
    Command command = mock(Command.class);
    testDebugLog("Testing command with invalid label 'invalidcmd'");
    boolean result = pluginClass.onCommand(sender, command, "invalidcmd", new String[]{});
    assertFalse(result, "Invalid label should return false");
    testDebugLog("testOnCommandInvalidLabelReturnsFalse passed: returned false for invalid label");
  }

  @Test
  public void testOnCommandHelpPageParsing() {
    testDebugLog("Starting testOnCommandHelpPageParsing");
    PlayerMock sender = server.addPlayer();
    Command command = mock(Command.class);
    testDebugLog("Testing /nf help 2 command with server version " + server.getBukkitVersion());
    boolean result = pluginClass.onCommand(sender, command, "nf", new String[]{"help", "2"});
    assertTrue(result, "Help command should return true");
    testDebugLog("Verifying sender received help messages");
    assertNotNull(sender.nextComponentMessage(), "Sender should receive at least one message");
    testDebugLog("testOnCommandHelpPageParsing passed: help command returned true and sent messages");
  }

  @Test
  public void testOnTabCompleteRootCommand() {
    testDebugLog("Starting testOnTabCompleteRootCommand");
    CommandSender sender = server.addPlayer();
    Command command = mock(Command.class);
    when(command.getName()).thenReturn("nf");
    testDebugLog("Testing tab completion for /nf with empty argument");
    List<String> completions = pluginClass.onTabComplete(sender, command, "nf", new String[]{""});
    assertNotNull(completions, "Tab completion list should not be null");
    assertTrue(completions.contains("help"), "Tab completion should include 'help'");
    testDebugLog("testOnTabCompleteRootCommand passed: tab completion returned valid list");
  }

  @Test
  public void testIsWorldEnabledDefaultTrue() {
    testDebugLog("Starting testIsWorldEnabledDefaultTrue");
    String worldName = "test_world";
    testDebugLog("Checking if world '" + worldName + "' is enabled by default");
    boolean result = pluginClass.isWorldEnabled(worldName);
    assertTrue(result, "World should be enabled by default if not in config");
    testDebugLog("testIsWorldEnabledDefaultTrue passed: world '" + worldName + "' is enabled");
  }

  @Test
  public void testOnCommandReloadConfigWithPermission() {
    testDebugLog("Starting testOnCommandReloadConfigWithPermission");
    PlayerMock sender = server.addPlayer();
    World world = server.addSimpleWorld("world");
    PermissionAttachment attachment = sender.addAttachment(mockPlugin);
    attachment.setPermission("nakedandafraid.reload", true);
    Command command = mock(Command.class);

    SpawnManager mockSpawnManager = mock(SpawnManager.class);
    doNothing().when(mockSpawnManager).loadSpawns();
    try {
      Field spawnManagerField = NakedAndAfraid.class.getDeclaredField("spawnManager");
      spawnManagerField.setAccessible(true);
      spawnManagerField.set(pluginClass, mockSpawnManager);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      testDebugLog("Failed to set spawnManager via reflection: " + e.getMessage());
    }

    testDebugLog("Testing /nf reloadconfig with permission");
    boolean result = pluginClass.onCommand(sender, command, "nf", new String[]{"reloadconfig"});
    assertTrue(result, "Reloadconfig command should return true with permission");
    verify(mockPlugin, atLeastOnce()).reloadConfig();
    testDebugLog("Verifying sender received confirmation message");
    assertNotNull(sender.nextComponentMessage(), "Sender should receive reload confirmation");
    testDebugLog("testOnCommandReloadConfigWithPermission passed: config reloaded and message sent");
  }

  @Test
  public void testTabListClearerEnableAppliesToEnabledWorlds() {
    testDebugLog("Starting testTabListClearerEnableAppliesToEnabledWorlds");

    PlayerMock player = server.addPlayer();
    String worldName = "world";
    World world = server.addSimpleWorld(worldName);
    player.teleport(new Location(world, 0, 0, 0));

    // Re-run onEnable to ensure tabListClearer is initialized
    pluginClass.onEnable();

    testDebugLog("Retrieving TabListClearer for player " + player.getName());
    TabListClearer tabListClearer = null;
    try {
      Field tabListClearerField = NakedAndAfraid.class.getDeclaredField("tabListClearer");
      tabListClearerField.setAccessible(true);
      tabListClearer = (TabListClearer) tabListClearerField.get(pluginClass);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      testDebugLog("Failed to get tabListClearer via reflection: " + e.getMessage());
      fail("Failed to get tabListClearer: " + e.getMessage());
    }

    assertNotNull(tabListClearer, "TabListClearer should not be null after onEnable");
    testDebugLog("Verifying tab list hiding applied to player " + player.getName());
    assertTrue(tabListClearer.isEnabled(), "TabListClearer should be enabled after enable()");
    testDebugLog("testTabListClearerEnableAppliesToEnabledWorlds passed: tab list hiding applied");
  }

  @Test
  public void testTabListClearerHidesOtherPlayers() {
    testDebugLog("Starting testTabListClearerHidesOtherPlayers");

    PlayerMock playerA = server.addPlayer();
    PlayerMock playerB = server.addPlayer();
    World world = server.getWorld("world");
    playerA.teleport(new Location(world, 0, 0, 0));
    playerB.teleport(new Location(world, 0, 0, 0));

    // Tracked calls: removals and additions per recipient
    List<java.util.UUID> removedFromA = new java.util.ArrayList<>();
    List<java.util.UUID> removedFromB = new java.util.ArrayList<>();
    List<String> addedToA = new java.util.ArrayList<>();
    List<String> addedToB = new java.util.ArrayList<>();

    TabListClearer clearer = new TabListClearer(pluginClass, mockPlugin) {
      @Override
      protected void sendRemovePacket(Player recipient, List<java.util.UUID> uuids) {
        if (recipient.equals(playerA)) removedFromA.addAll(uuids);
        if (recipient.equals(playerB)) removedFromB.addAll(uuids);
      }
      @Override
      protected void sendAddPacket(Player recipient, Player subject, boolean addOnly) {
        if (recipient.equals(playerA)) addedToA.add(subject.getName());
        if (recipient.equals(playerB)) addedToB.add(subject.getName());
      }
    };

    clearer.enable();

    // PlayerA should have had PlayerB removed, and only PlayerA added back
    assertTrue(removedFromA.contains(playerB.getUniqueId()),
            "PlayerB's UUID should be in the remove packet sent to PlayerA");
    assertFalse(removedFromA.contains(playerA.getUniqueId()),
            "PlayerA's own UUID must not be in the remove packet sent to themselves");
    assertTrue(addedToA.contains(playerA.getName()),
            "PlayerA should be added back to their own tab list");
    assertFalse(addedToA.contains(playerB.getName()),
            "PlayerB must not be added back to PlayerA's tab list");

    // Symmetrically for PlayerB
    assertTrue(removedFromB.contains(playerA.getUniqueId()),
            "PlayerA's UUID should be in the remove packet sent to PlayerB");
    assertTrue(addedToB.contains(playerB.getName()),
            "PlayerB should be added back to their own tab list");

    testDebugLog("testTabListClearerHidesOtherPlayers passed");
  }

  @Test
  public void testTeamCreateCommandCreatesTeam() {
    testDebugLog("Starting testTeamCreateCommandCreatesTeam");

    PlayerMock sender = server.addPlayer();
    PermissionAttachment attachment = sender.addAttachment(mockPlugin);
    attachment.setPermission("nakedandafraid.team", true);
    Command command = mock(Command.class);

    // Stub max-teams and team-block so TeamsManager initialises cleanly
    doReturn(10).when(mockConfig).getInt("max-teams", 10);
    doReturn("LODESTONE").when(mockConfig).getString("team-block", "LODESTONE");
    doReturn(Collections.emptyList()).when(mockConfig).getStringList(anyString());
    doReturn(false).when(mockConfig).contains("teams");

    testDebugLog("Executing /nf teams create red-team RED");
    boolean result = pluginClass.onCommand(sender, command, "nf",
            new String[]{"teams", "create", "red-team", "RED"});

    assertTrue(result, "onCommand must return true (not fall through to Bukkit usage message)");

    // Retrieve the TeamsManager via reflection so we can assert the team exists
    TeamsManager teamsManager = null;
    try {
      Field tmField = NakedAndAfraid.class.getDeclaredField("teamsManager");
      tmField.setAccessible(true);
      teamsManager = (TeamsManager) tmField.get(pluginClass);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      fail("Could not retrieve teamsManager via reflection: " + e.getMessage());
    }

    assertNotNull(teamsManager, "TeamsManager must not be null after onEnable");
    assertTrue(teamsManager.teamExists("red-team"),
            "Team 'red-team' must exist after /nf teams create red-team RED");

    TeamsManager.Team team = teamsManager.getTeam("red-team");
    assertNotNull(team, "getTeam('red-team') must return a non-null Team object");
    assertEquals("RED", team.getColor(),
            "Team color must be RED as specified in the create command");

    // Verify the sender received a success message (not the help text)
    String msg = sender.nextMessage();
    assertNotNull(msg, "Sender must receive a confirmation message");
    assertFalse(msg.contains("/nf help"),
            "Confirmation message must not be the help usage string, got: " + msg);
    assertTrue(msg.contains("red-team"),
            "Confirmation message should mention the team name, got: " + msg);

    testDebugLog("testTeamCreateCommandCreatesTeam passed");
  }

  @Test
  public void testOnEnableInitializesListeners() {
    testDebugLog("Starting testOnEnableInitializesListeners");
    testDebugLog("Calling onEnable with mocked config");
    pluginClass.onEnable();
    verify(mockPlugin, atLeastOnce()).saveDefaultConfig();
    testDebugLog("Verifying plugin registered with server");
    assertNotNull(server.getPluginManager().getPlugin(mockPlugin.getName()), "Plugin should be registered");
    testDebugLog("testOnEnableInitializesListeners passed: default config saved and listeners registered");
  }

  @Test
  public void testSetWorldListUpdatesConfig() {
    testDebugLog("Starting testSetWorldListUpdatesConfig");
    server.addSimpleWorld("world");
    server.addSimpleWorld("world_nether");

    testDebugLog("Calling setWorldList to update config");
    pluginClass.setWorldList();
    verify(mockEnabledWorlds, atLeastOnce()).set("world", true);
    verify(mockEnabledWorlds, atLeastOnce()).set("world_nether", true);
    verify(mockPlugin, atLeastOnce()).saveConfig();
    testDebugLog("testSetWorldListUpdatesConfig passed: config updated with worlds");
  }
}