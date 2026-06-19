package com.crimsonwarpedcraft.nakedandafraid;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.crimsonwarpedcraft.nakedandafraid.v1_17.NakedAndAfraid;
import com.crimsonwarpedcraft.nakedandafraid.v1_17.listeners.TabListClearer;
import com.crimsonwarpedcraft.nakedandafraid.v1_17.spawn.SpawnManager;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.File;
import java.lang.reflect.Field;
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

    // Set server version to 1.17.1 for compatibility
    try {
      Field bukkitVersion = ServerMock.class.getDeclaredField("bukkitVersion");
      bukkitVersion.setAccessible(true);
      bukkitVersion.set(server, "1.17.1-R0.1-SNAPSHOT");
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException("Failed to set server version", e);
    }

    mockPlugin = spy(MockBukkit.createMockPlugin());
    testDebugLog("Mock plugin created with name " + mockPlugin.getName());

    mockConfig = mock(FileConfiguration.class);
    doReturn(mockConfig).when(mockPlugin).getConfig();
    doReturn(true).when(mockConfig).getBoolean("debug-mode", false);
    doReturn(true).when(mockConfig).getBoolean("disable-tab", true);
    doReturn(false).when(mockConfig).getBoolean("disable-chat", true);
    doReturn(false).when(mockConfig).getBoolean("armor-damage.enabled", true);
    doReturn(false).when(mockConfig).getBoolean("disable-join-quit-messages", true);
    doReturn(false).when(mockConfig).getBoolean("disable-totems", true);
    doReturn("FIRST").when(mockConfig).getString("multiple-spawn-priority", "FIRST");

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

    server.addSimpleWorld("world");
    server.addSimpleWorld("world_nether");

    JavaPlugin mockProtocolLibPlugin = mock(JavaPlugin.class);
    doReturn("ProtocolLib").when(mockProtocolLibPlugin).getName();
    doReturn(true).when(mockProtocolLibPlugin).isEnabled();
    ProtocolManager mockProtocolManager = mock(ProtocolManager.class);

    PluginManager pluginManager = spy(server.getPluginManager());
    doReturn(mockProtocolLibPlugin).when(pluginManager).getPlugin("ProtocolLib");
    try {
      Field pmField = ServerMock.class.getDeclaredField("pluginManager");
      pmField.setAccessible(true);
      pmField.set(server, pluginManager);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException("Failed to set pluginManager field", e);
    }

    pluginManager.enablePlugin(mockProtocolLibPlugin);

    try (MockedStatic<ProtocolLibrary> mockedProtocolLibrary = mockStatic(ProtocolLibrary.class)) {
      mockedProtocolLibrary.when(ProtocolLibrary::getProtocolManager).thenReturn(mockProtocolManager);

      doReturn(Logger.getLogger("NakedAndAfraidPlugin")).when(mockPlugin).getLogger();
      File mockDataFolder = new File("target/mockplugin");
      mockDataFolder.mkdirs();
      doReturn(mockDataFolder).when(mockPlugin).getDataFolder();
      doReturn(true).when(mockPlugin).isEnabled();
      doReturn(server).when(mockPlugin).getServer();
      doNothing().when(mockPlugin).saveDefaultConfig();
      doNothing().when(mockPlugin).saveConfig();
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
    MockBukkit.unmock();
    Mockito.framework().clearInlineMocks();
    testDebugLog("MockBukkit server unmocked");
  }

  @Test
  public void testGetPluginReturnsSameInstance() {
    testDebugLog("Starting testGetPluginReturnsSameInstance");
    testDebugLog("Verifying getPlugin returns the mocked plugin instance");
    assertSame(mockPlugin, NakedAndAfraid.getPlugin(),
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
    String message = sender.nextMessage();
    assertNotNull(message, "Sender should receive at least one message");
    assertTrue(message.contains("==== NakedAndAfraid Help ===="), "Help message should contain header");
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
    String message = sender.nextMessage();
    assertNotNull(message, "Sender should receive reload confirmation");
    assertEquals("§aNaked and Afraid config reloaded.", message, "Should receive config reloaded message");
    testDebugLog("testOnCommandReloadConfigWithPermission passed: config reloaded and message sent");
  }

  @Test
  public void testTabListClearerEnableAppliesToEnabledWorlds() {
    testDebugLog("Starting testTabListClearerEnableAppliesToEnabledWorlds");

    PlayerMock player = server.addPlayer();
    String worldName = "world";
    World world = server.addSimpleWorld(worldName);
    player.teleport(new Location(world, 0, 0, 0));

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