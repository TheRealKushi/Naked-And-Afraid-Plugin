package com.therealkushi.nakedandafraid.common;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Version-agnostic test suite for every {@code NakedAndAfraid} implementation.
 * <p>
 * Subclasses live in v1_8/v1_17/v1_21's own test source sets (so they can use
 * their own MockBukkit major version + Paper/ProtocolLib deps) and only need
 * to populate {@link #pluginClass}, {@link #mockPlugin}, {@link #mockConfig}
 * in a {@code @BeforeEach}, plus implement the two hooks below.
 * <p>
 */
public abstract class AbstractNakedAndAfraidTest {

    protected static final boolean DEBUG_ENABLED = Boolean.getBoolean("test.debug");

    protected AbstractNakedAndAfraid pluginClass;
    protected Plugin mockPlugin;
    protected FileConfiguration mockConfig;

    protected void testDebugLog(String message) {
        if (DEBUG_ENABLED) {
            Logger.getLogger("NakedAndAfraidTest").info("[" + getClass().getSimpleName() + "] " + message);
        }
    }

    // ---- hooks every version subclass must provide ------------------------

    /** Wraps {@code server.addPlayer()} for whichever MockBukkit is present. */
    protected abstract Player addPlayer();

    /** Wraps {@code server.addSimpleWorld(name)}. */
    protected abstract void addWorld(String name);

    protected Command mockCommand() {
        return mock(Command.class);
    }

    // ---- reflection helpers (field names match across every version) ------

    protected Object getField(String name) {
        try {
            Field f = pluginClass.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(pluginClass);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Could not read field '" + name + "'", e);
        }
    }

    protected void setField(String name, Object value) {
        try {
            Field f = pluginClass.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(pluginClass, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Could not set field '" + name + "'", e);
        }
    }

    protected Object invoke(Object target, String method, Class<?>[] types, Object... args) {
        try {
            Method m = target.getClass().getMethod(method, types);
            m.setAccessible(true);
            return m.invoke(target, args);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Could not invoke '" + method + "'", e);
        }
    }

    // ---- shared tests -------------------------------------------------------

    @Test
    public void testGetPluginReturnsSameInstance() {
        testDebugLog("Starting testGetPluginReturnsSameInstance");
        assertSame(mockPlugin, pluginClass.getPlugin());
    }

    @Test
    public void testTeleportOnCountdownDefaultFalse() {
        testDebugLog("Starting testTeleportOnCountdownDefaultFalse");
        assertFalse(pluginClass.isTeleportOnCountdownEnd());
    }

    @Test
    public void testMultipleSpawnPriorityDefaultsToFirst() {
        testDebugLog("Starting testMultipleSpawnPriorityDefaultsToFirst");
        assertEquals("FIRST", pluginClass.getMultipleSpawnPriority());
    }

    @Test
    public void testDebugLogOnlyLogsWhenEnabled() {
        testDebugLog("Starting testDebugLogOnlyLogsWhenEnabled");
        Logger spyLogger = spy(Logger.getLogger("NakedAndAfraidTestLogger-" + getClass().getSimpleName()));
        doReturn(spyLogger).when(mockPlugin).getLogger();

        pluginClass.debugLog("Debug message!");
        verify(spyLogger, atLeastOnce()).info("Debug message!");

        doReturn(false).when(mockConfig).getBoolean("debug-mode", false);
        pluginClass.debugLog("Should not log");
        verify(spyLogger, never()).info("Should not log");
    }

    @Test
    public void testOnCommandInvalidLabelReturnsFalse() {
        testDebugLog("Starting testOnCommandInvalidLabelReturnsFalse");
        CommandSender sender = addPlayer();
        assertFalse(pluginClass.onCommand(sender, mockCommand(), "invalidcmd", new String[]{}));
    }

    @Test
    public void testOnTabCompleteRootCommand() {
        testDebugLog("Starting testOnTabCompleteRootCommand");
        CommandSender sender = addPlayer();
        Command command = mockCommand();
        when(command.getName()).thenReturn("nf");
        List<String> completions = pluginClass.onTabComplete(sender, command, "nf", new String[]{""});
        assertNotNull(completions);
        assertTrue(completions.contains("help"));
    }

    @Test
    public void testIsWorldEnabledDefaultTrue() {
        testDebugLog("Starting testIsWorldEnabledDefaultTrue");
        assertTrue(pluginClass.isWorldEnabled("test_world"));
    }

    @Test
    public void testOnEnableInitializesListeners() {
        testDebugLog("Starting testOnEnableInitializesListeners");
        pluginClass.onEnable();
        verify(mockPlugin, atLeastOnce()).saveDefaultConfig();
    }

    @Test
    public void testOnCommandReloadConfigWithPermission() {
        testDebugLog("Starting testOnCommandReloadConfigWithPermission");
        Player sender = addPlayer();
        addWorld("world");
        PermissionAttachment attachment = sender.addAttachment(mockPlugin);
        attachment.setPermission("nakedandafraid.reload", true);

        Object realSpawnManager = getField("spawnManager");
        Object mockSpawnManager = mock(realSpawnManager.getClass());
        setField("spawnManager", mockSpawnManager);

        boolean result = pluginClass.onCommand(sender, mockCommand(), "nf", new String[]{"reloadconfig"});
        assertTrue(result);
        verify(mockPlugin, atLeastOnce()).reloadConfig();
    }

    @Test
    public void testTabListClearerEnableAppliesToEnabledWorlds() {
        testDebugLog("Starting testTabListClearerEnableAppliesToEnabledWorlds");
        addWorld("world");
        pluginClass.onEnable();

        Object tabListClearer = getField("tabListClearer");
        assertNotNull(tabListClearer, "TabListClearer should not be null after onEnable");
        assertEquals(Boolean.TRUE, invoke(tabListClearer, "isEnabled", new Class[]{}));
    }

    @Test
    public void testTeamCreateCommandCreatesTeam() throws Exception {
        testDebugLog("Starting testTeamCreateCommandCreatesTeam");
        Player sender = addPlayer();
        sender.addAttachment(mockPlugin).setPermission("nakedandafraid.team", true);

        boolean result = pluginClass.onCommand(sender, mockCommand(), "nf",
                new String[]{"teams", "create", "red-team", "RED"});
        assertTrue(result);

        Object teamsManager = getField("teamsManager");
        boolean exists = (Boolean) invoke(teamsManager, "teamExists", new Class[]{String.class}, "red-team");
        assertTrue(exists, "Team 'red-team' must exist after /nf teams create");

        Object team = invoke(teamsManager, "getTeam", new Class[]{String.class}, "red-team");
        assertNotNull(team);
        assertEquals("RED", invoke(team, "getColor", new Class[]{}));
    }
}