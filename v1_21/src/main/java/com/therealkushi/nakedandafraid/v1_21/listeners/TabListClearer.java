package com.therealkushi.nakedandafraid.v1_21.listeners;

import com.therealkushi.nakedandafraid.v1_21.NakedAndAfraid;
import com.therealkushi.nakedandafraid.v1_21.util.SchedulerCompat;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.*;

/**
 * Hides other players from a player's client side tab list, using raw NMS
 * packets reached through reflection so this works without any external
 * dependency such as ProtocolLib.
 * <p>
 * Each player only ever sees their own name in the tab list while this
 * feature is enabled for the world they are in. When a world is disabled
 * for a player, or the feature itself is disabled, the full tab list is
 * restored for that player.
 * <p>
 * This listener has no external library dependency. It works purely with
 * java.lang.reflect against Bukkit and internal server classes, so it needs
 * no additional jar on the classpath. It has been written to run on
 * Bukkit, Spigot, Paper, and Folia, though raw NMS packet access is not
 * guaranteed to succeed on every possible server implementation, since
 * internal class names and remapping can differ between them. If packet
 * access fails, this class disables itself gracefully instead of crashing
 * or spamming errors.
 */
public class TabListClearer implements Listener {

    /** Reference to the main plugin instance, used for world checks and debug logging. */
    private final NakedAndAfraid nakedAndAfraid;

    /** The underlying Bukkit Plugin instance, used for registering events and logging to console. */
    private final Plugin plugin;

    /** Whether tab list hiding is currently turned on. */
    private boolean enabled;

    /**
     * Tracks whether raw NMS packet access has been confirmed to work on this server.
     * Null means it has not been tested yet. True means it works. False means a previous
     * attempt failed and the feature has disabled itself to avoid repeated errors.
     */
    private Boolean packetAccessSupported;

    /**
     * Creates a new TabListClearer and registers it as an event listener.
     *
     * @param plugin    the main NakedAndAfraid instance, used for world checks and debug logging
     * @param pluginVar the Bukkit Plugin instance, used for event registration and console logging
     */
    public TabListClearer(NakedAndAfraid plugin, Plugin pluginVar) {
        this.nakedAndAfraid = plugin;
        this.plugin = pluginVar;
        plugin.debugLog("[TabListClearer] Initializing for Bukkit version " + Bukkit.getBukkitVersion());
        Bukkit.getPluginManager().registerEvents(this, nakedAndAfraid.getPlugin());
        plugin.debugLog("[TabListClearer] Registered event listeners for TabListClearer");
    }

    // -------------------------------------------------------------------
    // NMS class and member resolution
    //
    // NMS classes belong to the server itself, not to any plugin, so they
    // must be resolved using the server's own classloader, which is
    // Bukkit.class.getClassLoader(). Calling Class.forName(name) directly
    // from inside a plugin resolves through
    // org.bukkit.plugin.java.PluginClassLoader, which has its own
    // plugin dependency search logic before it falls back to the parent
    // or server classloader. That is the wrong loader to ask for a
    // server internal class.
    //
    // A separate problem affects nested classes specifically, such as the
    // Action enum inside ClientboundPlayerInfoUpdatePacket, or its Entry
    // record. Mojang's official mappings, which modern CraftBukkit and
    // Paper both build against, do not necessarily assign a friendly name
    // to every nested type. Whether a given nested class keeps a readable
    // name such as Action, or ends up with an obfuscated single letter
    // name instead, can differ between build pipelines even when the
    // enclosing top level class name is identical and stable. Hardcoding
    // a nested class name by string is therefore fragile in a way that
    // hardcoding a top level class name is not.
    //
    // To avoid depending on the exact name of a nested class, this file
    // looks up top level classes by name as before, then finds the nested
    // class it actually needs by inspecting the declared nested classes
    // of that top level class and matching on their real structure, such
    // as which enum constants or record components they contain, rather
    // than assuming a specific nested class name.
    //
    // Every class lookup below is cached the first time it succeeds, so
    // this cost is paid once per server run rather than once per packet.
    // -------------------------------------------------------------------

    /**
     * Cache of resolved classes, keyed either by a fully qualified binary name for top
     * level classes, or by a composite key for nested classes found by structure.
     */
    private static final Map<String, Class<?>> classCache = new HashMap<>();

    /**
     * Resolves a top level class by binary name using the server's own classloader
     * instead of the calling plugin's classloader, and caches the result so repeated
     * calls are cheap.
     *
     * @param binaryName the fully qualified class name, for example
     *                    net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket
     * @return the resolved Class object
     * @throws ClassNotFoundException if the class truly does not exist on this server
     */
    private static Class<?> nmsClass(String binaryName) throws ClassNotFoundException {
        Class<?> cached = classCache.get(binaryName);
        if (cached != null) {
            return cached;
        }
        Class<?> resolved = Class.forName(binaryName, false, Bukkit.class.getClassLoader());
        classCache.put(binaryName, resolved);
        return resolved;
    }

    /**
     * Finds a nested enum class of the given container class by inspecting its actual
     * enum constants, rather than assuming the nested class has a specific name.
     * <p>
     * This exists because a nested type's real name is not guaranteed to be stable or
     * even human readable across every server build, even when the enclosing top level
     * class name is completely stable. Searching by the enum's real constant names is
     * immune to that, since the constant names themselves are part of the enum's public
     * contract and do not change between builds.
     *
     * @param container      the top level class whose nested classes should be searched
     * @param requiredNames  one or more enum constant names that the target enum must contain
     * @return the first nested enum class found that contains all of the required constant names
     * @throws ClassNotFoundException if no nested enum matching all required constants is found
     */
    private static Class<?> findEnumClassByConstantNames(Class<?> container, String... requiredNames)
            throws ClassNotFoundException {
        String cacheKey = container.getName() + "#enum#" + String.join(",", requiredNames);
        Class<?> cached = classCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        for (Class<?> nested : container.getDeclaredClasses()) {
            if (!nested.isEnum()) {
                continue;
            }
            Set<String> constantNames = new HashSet<>();
            for (Object constant : nested.getEnumConstants()) {
                constantNames.add(constant.toString());
            }
            boolean matchesAll = true;
            for (String required : requiredNames) {
                if (!constantNames.contains(required)) {
                    matchesAll = false;
                    break;
                }
            }
            if (matchesAll) {
                classCache.put(cacheKey, nested);
                return nested;
            }
        }

        throw new ClassNotFoundException("No nested enum class of " + container.getName()
                + " contains constants: " + String.join(", ", requiredNames));
    }

    /**
     * Finds a nested record class of the given container class by inspecting its actual
     * record components, rather than assuming the nested class has a specific name.
     * <p>
     * This follows the same reasoning as findEnumClassByConstantNames, applied to
     * records instead of enums. Record component names are part of the record's public
     * contract and are far more stable across builds than the record's own class name.
     *
     * @param container              the top level class whose nested classes should be searched
     * @param requiredComponentNames one or more record component names that the target record must contain
     * @return the first nested record class found that contains all of the required component names
     * @throws ClassNotFoundException if no nested record matching all required components is found
     */
    private static Class<?> findRecordClassByComponentNames(Class<?> container, String... requiredComponentNames)
            throws ClassNotFoundException {
        String cacheKey = container.getName() + "#record#" + String.join(",", requiredComponentNames);
        Class<?> cached = classCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        for (Class<?> nested : container.getDeclaredClasses()) {
            if (!nested.isRecord()) {
                continue;
            }
            Set<String> componentNames = new HashSet<>();
            for (RecordComponent component : nested.getRecordComponents()) {
                componentNames.add(component.getName());
            }
            boolean matchesAll = true;
            for (String required : requiredComponentNames) {
                if (!componentNames.contains(required)) {
                    matchesAll = false;
                    break;
                }
            }
            if (matchesAll) {
                classCache.put(cacheKey, nested);
                return nested;
            }
        }

        throw new ClassNotFoundException("No nested record class of " + container.getName()
                + " contains components: " + String.join(", ", requiredComponentNames));
    }

    /**
     * Enables tab list hiding for all currently online players in worlds where the
     * plugin is enabled. Does nothing if hiding is already enabled.
     */
    public void enable() {
        if (enabled) {
            nakedAndAfraid.debugLog("[TabListClearer] Already enabled, skipping enable");
            return;
        }

        nakedAndAfraid.debugLog("[TabListClearer] Enabling tab list hiding for all online players");
        int playerCount = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (nakedAndAfraid.isWorldEnabled(player.getWorld().getName())) {
                updateTabListForPlayer(player);
                playerCount++;
            } else {
                nakedAndAfraid.debugLog("[TabListClearer] Skipped " + player.getName()
                        + " in disabled world " + player.getWorld().getName());
            }
        }
        nakedAndAfraid.debugLog("[TabListClearer] Applied tab list hiding to " + playerCount + " players");

        enabled = true;
        plugin.getLogger().info("Naked And Afraid - Tab Hider Enabled.");
    }

    /**
     * Disables tab list hiding and restores the full tab list for all currently online
     * players in worlds where the plugin is enabled. Does nothing if hiding is already
     * disabled.
     */
    public void disable() {
        if (!enabled) {
            nakedAndAfraid.debugLog("[TabListClearer] Already disabled, skipping disable");
            return;
        }

        nakedAndAfraid.debugLog("[TabListClearer] Disabling tab list hiding for all online players");
        int playerCount = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (nakedAndAfraid.isWorldEnabled(player.getWorld().getName())) {
                restoreTabListForPlayer(player);
                playerCount++;
            }
        }
        nakedAndAfraid.debugLog("[TabListClearer] Restored tab list for " + playerCount + " players");

        enabled = false;
        plugin.getLogger().info("Naked And Afraid - Tab Hider Disabled.");
    }

    /**
     * Switches tab list hiding on if it is currently off, or off if it is currently on.
     */
    public void toggle() {
        if (enabled) {
            disable();
        } else {
            enable();
        }
    }

    /**
     * Returns whether tab list hiding is currently enabled.
     *
     * @return true if enabled, false otherwise
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Applies the correct tab list state to a single player, based on whether hiding is
     * enabled and whether the plugin is enabled in that player's current world.
     * <p>
     * If hiding is disabled globally, this does nothing. If the player's world has the
     * plugin disabled, the full tab list is restored for that player instead. If packet
     * access has previously failed on this server, this method exits early without
     * attempting anything further.
     *
     * @param player the player whose tab list should be updated
     */
    public void applyToPlayer(Player player) {
        if (!enabled) {
            return;
        }
        if (packetAccessSupported != null && !packetAccessSupported) {
            return;
        }

        if (!nakedAndAfraid.isWorldEnabled(player.getWorld().getName())) {
            restoreTabListForPlayer(player);
            return;
        }
        updateTabListForPlayer(player);
    }

    private void updateTabListForPlayer(Player player) {
        if (packetAccessSupported != null && !packetAccessSupported) {
            return;
        }

        nakedAndAfraid.debugLog("[TabListClearer] Unlisting other players for " + player.getName());
        try {
            List<Player> others = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.equals(player) && nakedAndAfraid.isWorldEnabled(p.getWorld().getName())) {
                    others.add(p);
                }
            }
            if (!others.isEmpty()) {
                // listed=false hides them from the tab menu but keeps the entries,
                // so the client can still render the player entities.
                sendListedPacket(player, others, false);
            }
            packetAccessSupported = true;
        } catch (Throwable e) {
            reportPacketFailure(player, e);
        }
    }

    private void restoreTabListForPlayer(Player player) {
        if (packetAccessSupported != null && !packetAccessSupported) {
            return;
        }

        nakedAndAfraid.debugLog("[TabListClearer] Relisting other players for " + player.getName());
        try {
            List<Player> others = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.equals(player) && nakedAndAfraid.isWorldEnabled(p.getWorld().getName())) {
                    others.add(p);
                }
            }
            if (!others.isEmpty()) {
                sendListedPacket(player, others, true);
            }
            packetAccessSupported = true;
        } catch (Throwable e) {
            reportPacketFailure(player, e);
        }
    }

    /**
     * Records that raw NMS packet access failed, logs the cause exactly once at severe
     * level, and disables all further packet attempts for the rest of the server's
     * lifetime. This avoids flooding the console with a full stack trace on every
     * player join.
     * <p>
     * If this triggers, it usually means this specific server implementation uses
     * different internal class or member layouts than expected. In that case, a
     * library such as ProtocolLib would be a more reliable long term solution than
     * raw reflection, though this class intentionally has no such dependency.
     *
     * @param player the player involved in the failed attempt, used only for logging
     * @param e      the exception that was thrown while trying to send a packet
     */
    private void reportPacketFailure(Player player, Throwable e) {
        if (packetAccessSupported == null) {
            plugin.getLogger().severe("[TabListClearer] Tab list packet access is not supported on this "
                    + "server implementation (" + Bukkit.getName() + " " + Bukkit.getBukkitVersion() + "). "
                    + "Disabling tab list hiding to avoid repeated errors. Cause: " + e);
            packetAccessSupported = false;
        } else {
            nakedAndAfraid.debugLog("[TabListClearer] Packet send failed for " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Finds and returns the internal network connection object for a given player,
     * walking up the class hierarchy of their NMS entity if needed.
     * <p>
     * This uses getDeclaredField rather than getField, because the connection field on
     * the NMS player class is not public. Class.getField only ever returns public
     * members, even inherited ones, so using it here would fail once the search moves
     * past the first player. getDeclaredField together with setAccessible(true)
     * correctly finds and unlocks the field regardless of its visibility.
     *
     * @param entityPlayer the NMS entity player object, obtained from the player's getHandle method
     * @return the connection object used to send packets to that player
     * @throws NoSuchFieldException if no connection field is found anywhere in the class hierarchy
     */
    private Object getConnection(Object entityPlayer) throws Exception {
        Class<?> clazz = entityPlayer.getClass();
        while (clazz != null) {
            try {
                Field field = clazz.getDeclaredField("connection");
                field.setAccessible(true);
                return field.get(entityPlayer);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new NoSuchFieldException("connection field not found in class hierarchy of " + entityPlayer.getClass());
    }

    /**
     * Builds a single tab list entry record for the given player, describing their
     * profile, ping, game mode placeholder, and whether they should appear as listed.
     * <p>
     * The entry type is a nested record on the packet class. Rather than assuming that
     * nested record has a specific name such as Entry, this method locates it by
     * scanning the packet class's nested classes for a record whose components include
     * profileId and listed, since a nested class's own name is not guaranteed to be
     * stable across every server build even though its component names are part of its
     * public structure. Fields such as display name and chat session are left null
     * since they are not needed for this purpose.
     *
     * @param subject the player this entry describes
     * @param listed  whether this player should appear as listed in the tab list
     * @return a constructed entry object ready to be included in an update packet
     */
    private Object createListedEntry(Player subject, boolean listed) throws Exception {
        Object entityPlayer = subject.getClass().getMethod("getHandle").invoke(subject);
        Object gameProfile = entityPlayer.getClass().getMethod("getGameProfile").invoke(entityPlayer);

        Class<?> packetClass = nmsClass("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket");
        Class<?> entryClass = findRecordClassByComponentNames(packetClass, "profileId", "listed");
        Class<?> gameTypeClass = nmsClass("net.minecraft.world.level.GameType");
        Object placeholderGameType = gameTypeClass.getEnumConstants()[0];

        RecordComponent[] components = entryClass.getRecordComponents();
        Class<?>[] paramTypes = new Class<?>[components.length];
        Object[] args = new Object[components.length];

        for (int i = 0; i < components.length; i++) {
            paramTypes[i] = components[i].getType();
            switch (components[i].getName()) {
                case "profileId" -> args[i] = subject.getUniqueId();
                case "profile" -> args[i] = gameProfile;
                case "listed" -> args[i] = listed;
                case "latency" -> args[i] = subject.getPing();
                case "gameMode" -> args[i] = placeholderGameType;
                case "showHat" -> args[i] = true;
                case "listOrder" -> args[i] = 0;
                default -> args[i] = null;
            }
        }

        return entryClass.getDeclaredConstructor(paramTypes).newInstance(args);
    }

    /**
     * Finds a specific enum constant by name from the given action enum class, used to
     * pick the correct update action such as ADD_PLAYER or UPDATE_LISTED from the
     * packet's internal action enum.
     *
     * @param actionClass the enum class to search
     * @param name        the exact name of the enum constant to find
     * @return the matching enum constant
     * @throws IllegalStateException if no constant with that name exists
     */
    private Object findAction(Class<?> actionClass, String name) throws Exception {
        return Arrays.stream(actionClass.getEnumConstants())
                .filter(a -> a.toString().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(name + " action not found"));
    }

    /**
     * Sends a raw constructed NMS packet to a recipient player through their network
     * connection. Different server builds expose either a send method or a sendPacket
     * method on the connection object, so this tries send first and falls back to
     * sendPacket if that method does not exist.
     *
     * @param recipient the player who should receive this packet
     * @param nmsPacket the already constructed packet object to send
     */
    private void sendNmsPacket(Player recipient, Object nmsPacket) throws Exception {
        Object entityPlayer = recipient.getClass().getMethod("getHandle").invoke(recipient);
        Object connection = getConnection(entityPlayer);
        Class<?> packetIfaceClass = nmsClass("net.minecraft.network.protocol.Packet");
        try {
            Method send = connection.getClass().getMethod("send", packetIfaceClass);
            send.invoke(connection, nmsPacket);
        } catch (NoSuchMethodException e) {
            Method sendPacket = connection.getClass().getMethod("sendPacket", packetIfaceClass);
            sendPacket.invoke(connection, nmsPacket);
        }
    }

    /**
     * Sets the "listed" flag of the given players in the recipient's tab list.
     * The entries stay in the client's player-info map, so the players remain
     * visible in the world; only the tab menu entry is shown or hidden.
     * Protected so tests can override it with a fake.
     */
    protected void sendListedPacket(Player recipient, List<Player> subjects, boolean listed) throws Exception {
        Class<?> packetClass = nmsClass("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket");
        Class<?> actionClass = findEnumClassByConstantNames(packetClass, "UPDATE_LISTED");

        @SuppressWarnings({"rawtypes", "unchecked"})
        EnumSet actions = EnumSet.of((Enum) findAction(actionClass, "UPDATE_LISTED"));

        List<Object> entries = new ArrayList<>();
        for (Player subject : subjects) {
            entries.add(createListedEntry(subject, listed));
        }

        // Private (EnumSet<Action>, List<Entry>) constructor: the public
        // (EnumSet, Collection<ServerPlayer>) one always builds entries with listed=true.
        Constructor<?> ctor = packetClass.getDeclaredConstructor(EnumSet.class, List.class);
        ctor.setAccessible(true);
        Object packet = ctor.newInstance(actions, entries);

        sendNmsPacket(recipient, packet);
    }

    /**
     * Handles a player joining the server. The update is delayed by one tick so the
     * server's own default tab list packets are sent first, and this listener's changes
     * are applied afterward instead of being overwritten by them.
     * <p>
     * The delayed work is scheduled through SchedulerCompat rather than the plain
     * Bukkit scheduler, since Folia requires per player scheduling to go through the
     * entity's own region scheduler. On non Folia servers, SchedulerCompat simply falls
     * back to the normal Bukkit scheduler.
     * <p>
     * Both the joining player's own tab list, and every other affected player's view of
     * the tab list, are updated, so the new player is hidden from everyone else as well.
     *
     * @param event the join event fired by Bukkit when a player connects
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        SchedulerCompat.runForEntity(nakedAndAfraid.getPlugin(), player, () -> {
            applyToPlayer(player);
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.equals(player) && nakedAndAfraid.isWorldEnabled(other.getWorld().getName())) {
                    SchedulerCompat.runForEntity(nakedAndAfraid.getPlugin(), other, () -> applyToPlayer(other), 0L);
                }
            }
        }, 1L);
    }

    /**
     * Handles a player leaving the server. No cleanup is required here, since the
     * server itself removes a disconnecting player from everyone else's tab list
     * automatically.
     *
     * @param event the quit event fired by Bukkit when a player disconnects
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // No cleanup needed on quit.
    }

    /**
     * Handles a player changing from one world to another. The player's tab list state
     * is reapplied after a short delay, so it reflects whichever world they have just
     * entered, whether that world has the plugin enabled or disabled.
     * <p>
     * As with onPlayerJoin, this work is scheduled through SchedulerCompat so it
     * remains safe on Folia.
     *
     * @param event the world change event fired by Bukkit when a player moves to a different world
     */
    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        SchedulerCompat.runForEntity(nakedAndAfraid.getPlugin(), player, () -> applyToPlayer(player), 1L);
    }
}