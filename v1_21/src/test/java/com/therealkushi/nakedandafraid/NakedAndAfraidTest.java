package com.therealkushi.nakedandafraid;

import com.therealkushi.nakedandafraid.common.AbstractNakedAndAfraidTest;
import com.therealkushi.nakedandafraid.v1_21.NakedAndAfraid;
import com.therealkushi.nakedandafraid.v1_21.listeners.TabListClearer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.Mockito;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * v1_21-specific tests only. Everything that behaves identically across
 * versions (help/spawn/team/config-reload/tab-list plumbing, etc.) lives in
 * {@link AbstractNakedAndAfraidTest} and is inherited here.
 * <p>
 * What stays here and why:
 * <ul>
 *   <li>{@link #testOnCommandHelpPageParsing()} - v1_21 sends Adventure
 *       {@code Component}s, so assertions use {@code nextComponentMessage()}.</li>
 *   <li>{@link #testSetWorldListUpdatesConfig()} - v1_21's {@code setWorldList()}
 *       uses the {@code ConfigurationSection} API, unlike v1_8/v1_17's file I/O.</li>
 *   <li>{@link #testTabListClearerHidesOtherPlayers()} - exercises the packet-send
 *       hooks that only this version's {@code TabListClearer} exposes as
 *       overridable protected methods.</li>
 * </ul>
 */
public class NakedAndAfraidTest extends AbstractNakedAndAfraidTest {

  private ServerMock server;
  private ConfigurationSection mockEnabledWorlds;
  private NakedAndAfraid nakedAndAfraid;

  @Override
  protected Player addPlayer() {
    return server.addPlayer();
  }

  @Override
  protected void addWorld(String name) {
    server.addSimpleWorld(name);
  }

  @BeforeEach
  public void setUp() {
    testDebugLog("Starting test setup");

    server = MockBukkit.mock();
    testDebugLog("MockBukkit server initialized with version " + server.getBukkitVersion());

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
    doReturn(10).when(mockConfig).getInt("max-teams", 10);
    doReturn("LODESTONE").when(mockConfig).getString("team-block", "LODESTONE");
    doReturn(Collections.emptyList()).when(mockConfig).getStringList(anyString());
    doReturn(false).when(mockConfig).contains("teams");

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

    doReturn(Logger.getLogger("NakedAndAfraidPlugin")).when(mockPlugin).getLogger();

    File mockDataFolder = new File("target/mockplugin");
    if (!mockDataFolder.exists()) {
      assertTrue(mockDataFolder.mkdirs(), "Failed to create mock plugin data folder");
    }

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
      nakedAndAfraid = spy(new NakedAndAfraid(mockPlugin));
      pluginClass = nakedAndAfraid;
      pluginClass.onEnable();
      testDebugLog("NakedAndAfraid plugin instance created successfully");
    } catch (Exception e) {
      testDebugLog("Failed to create NakedAndAfraid instance: " + e.getMessage());
      throw new RuntimeException("Failed to initialize NakedAndAfraid in setUp", e);
    }
  }

  @AfterEach
  public void tearDown() {
    testDebugLog("Tearing down test environment");
    File teamsFile = new File("target/mockplugin/teams.yml");
    if (teamsFile.exists()) {
      assertTrue(teamsFile.delete(), "Failed to delete teams.yml");
    }

    File spawnsFile = new File("target/mockplugin/spawns.yml");
    if (spawnsFile.exists()) {
      assertTrue(spawnsFile.delete(), "Failed to delete spawns.yml");
    }
    MockBukkit.unmock();
    Mockito.framework().clearInlineMocks();
    testDebugLog("MockBukkit server unmocked");
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
  public void testTabListClearerHidesOtherPlayers() {
    testDebugLog("Starting testTabListClearerHidesOtherPlayers");

    PlayerMock playerA = server.addPlayer();
    PlayerMock playerB = server.addPlayer();
    World world = server.getWorld("world");
    playerA.teleport(new Location(world, 0, 0, 0));
    playerB.teleport(new Location(world, 0, 0, 0));

    List<String> unlistedForA = new ArrayList<>();
    List<String> unlistedForB = new ArrayList<>();
    List<String> relistedForA = new ArrayList<>();
    List<String> relistedForB = new ArrayList<>();

    TabListClearer clearer = new TabListClearer(nakedAndAfraid, mockPlugin) {
      @Override
      protected void sendListedPacket(Player recipient, List<Player> subjects, boolean listed) {
        List<String> target;
        if (recipient.equals(playerA)) {
          target = listed ? relistedForA : unlistedForA;
        } else if (recipient.equals(playerB)) {
          target = listed ? relistedForB : unlistedForB;
        } else {
          return;
        }
        for (Player subject : subjects) {
          target.add(subject.getName());
        }
      }
    };

    clearer.enable();

    assertTrue(unlistedForA.contains(playerB.getName()),
            "PlayerB should be unlisted in PlayerA's tab list");
    assertFalse(unlistedForA.contains(playerA.getName()),
            "PlayerA's own entry must not be unlisted for themselves");
    assertTrue(unlistedForB.contains(playerA.getName()),
            "PlayerA should be unlisted in PlayerB's tab list");
    assertFalse(unlistedForB.contains(playerB.getName()),
            "PlayerB's own entry must not be unlisted for themselves");
    assertTrue(relistedForA.isEmpty() && relistedForB.isEmpty(),
            "Nothing should be relisted while enabling");

    clearer.disable();

    assertTrue(relistedForA.contains(playerB.getName()),
            "PlayerB should be relisted for PlayerA after disable");
    assertTrue(relistedForB.contains(playerA.getName()),
            "PlayerA should be relisted for PlayerB after disable");
    assertFalse(relistedForA.contains(playerA.getName()),
            "PlayerA's own entry is never touched, so it must not be relisted");

    testDebugLog("testTabListClearerHidesOtherPlayers passed");
  }

  @Test
  public void testSetWorldListUpdatesConfig() {
    testDebugLog("Starting testSetWorldListUpdatesConfig");
    server.addSimpleWorld("world");
    server.addSimpleWorld("world_nether");

    testDebugLog("Calling setWorldList to update config");
    nakedAndAfraid.setWorldList();
    verify(mockEnabledWorlds, atLeastOnce()).set("world", true);
    verify(mockEnabledWorlds, atLeastOnce()).set("world_nether", true);
    verify(mockPlugin, atLeastOnce()).saveConfig();
    testDebugLog("testSetWorldListUpdatesConfig passed: config updated with worlds");
  }
}