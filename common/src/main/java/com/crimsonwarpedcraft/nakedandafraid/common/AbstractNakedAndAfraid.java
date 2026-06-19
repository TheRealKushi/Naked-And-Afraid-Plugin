package com.crimsonwarpedcraft.nakedandafraid.common;

import com.crimsonwarpedcraft.nakedandafraid.common.util.PluginLogger;
import com.crimsonwarpedcraft.nakedandafraid.common.util.VersionChecker;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Version-independent core of NakedAndAfraid.
 * <p>
 * All three version modules extend this class. The class contains every method
 * whose logic is identical across versions. Methods that differ - listener
 * creation, message formatting, world-list persistence - are declared
 * {@code abstract} and implemented in the concrete subclass.
 * <p>
 * Compile target: Java 8, Spigot 1.12 API - no Adventure, no {@code var},
 * no text-block syntax.
 */
public abstract class AbstractNakedAndAfraid implements PluginDelegate, PluginLogger {

    // -----------------------------------------------------------------------
    // Fields shared by all versions
    // -----------------------------------------------------------------------

    protected final Plugin plugin;

    /** Whether players teleport the moment the countdown expires. */
    protected boolean teleportOnCountdownEnd;

    /** "FIRST" or "RANDOM" - governs which spawn wins when a player has several. */
    protected String multipleSpawnPriority;

    public Plugin getPlugin() {
        return plugin;
    }

    protected AbstractNakedAndAfraid(Plugin plugin) {
        this.plugin = plugin;
    }

    // -----------------------------------------------------------------------
    // PluginDelegate - onEnable / onDisable
    // -----------------------------------------------------------------------

    @Override
    public void onEnable() {
        debugLog("[NakedAndAfraid] Starting plugin initialization for Bukkit version "
                + Bukkit.getBukkitVersion());

        debugLog("[NakedAndAfraid] Loading default config");
        plugin.saveDefaultConfig();

        debugLog("[NakedAndAfraid] Updating enabled-worlds in config");
        setWorldList();

        debugLog("[NakedAndAfraid] Reloading listeners");
        reloadListeners();

        teleportOnCountdownEnd = plugin.getConfig().getBoolean("teleport-on-countdown-end", false);
        multipleSpawnPriority  = plugin.getConfig()
                .getString("multiple-spawn-priority", "FIRST").toUpperCase();
        debugLog("[NakedAndAfraid] Loaded config: teleport-on-countdown-end=" + teleportOnCountdownEnd
                + ", multiple-spawn-priority=" + multipleSpawnPriority);

        initManagers();

        debugLog("[NakedAndAfraid] Registered GlobalDeathSoundListener and TeamListener");

        initSpawnManager();

        initTeleportHelper();

        plugin.getServer().getPluginManager()
                .registerEvents(createVersionNotifyListener(), plugin);
        debugLog("[NakedAndAfraid] Registered VersionNotifyListener");

        // Version-specific listeners (e.g. TotemDisablerListener in 1.17+)
        registerVersionListeners();

        logStartupInfo();
        debugLog("[NakedAndAfraid] Plugin initialization completed");
    }

    @Override
    public void onDisable() {
        debugLog("[NakedAndAfraid] Shutting down plugin");
        disableTabListClearer();
        disableArmorDamage();
        saveSpawnManager();
        unregisterVersionListeners();
        HandlerList.unregisterAll(plugin);
        debugLog("[NakedAndAfraid] Unregistered all listeners");
    }

    // -----------------------------------------------------------------------
    // PluginLogger
    // -----------------------------------------------------------------------

    @Override
    public void debugLog(String message) {
        if (plugin.getConfig().getBoolean("debug-mode", false)) {
            plugin.getLogger().info(message);
        }
    }

    // -----------------------------------------------------------------------
    // Shared helpers
    // -----------------------------------------------------------------------

    public boolean isWorldEnabled(String worldName) {
        if (plugin.getConfig().contains("enabled-worlds." + worldName)) {
            boolean enabled = plugin.getConfig()
                    .getBoolean("enabled-worlds." + worldName, true);
            debugLog("[NakedAndAfraid] World '" + worldName + "' enabled: " + enabled);
            return enabled;
        }
        debugLog("[NakedAndAfraid] World '" + worldName
                + "' not in config, default enabled: true");
        return true;
    }

    public org.bukkit.configuration.file.FileConfiguration getConfig() {
        return plugin.getConfig();
    }

    public boolean isTeleportOnCountdownEnd() {
        return teleportOnCountdownEnd;
    }

    public String getMultipleSpawnPriority() {
        return multipleSpawnPriority;
    }

    // -----------------------------------------------------------------------
    // Shared reloadListeners skeleton
    // -----------------------------------------------------------------------

    /**
     * Tears down all toggleable listeners, then re-creates them from config.
     * Version subclasses override {@link #doReloadVersionListeners()} to handle
     * anything that doesn't exist in all versions (e.g. TotemDisablerListener).
     */
    public void reloadListeners() {
        debugLog("[NakedAndAfraid] Starting listener reload");

        // ---- tear-down phase ----
        disableTabListClearer();
        unregisterAndClearChatRestriction();
        disableArmorDamage();
        unregisterAndClearJoinQuit();
        doUnregisterVersionListeners();   // hook for totem etc.

        // ---- rebuild phase ----

        // Chat restriction
        if (plugin.getConfig().getBoolean("disable-chat", true)) {
            setChatRestrictionListener(createChatRestrictionListener());
            plugin.getServer().getPluginManager()
                    .registerEvents(getChatRestrictionListener(), plugin);
            plugin.getLogger().info("Naked And Afraid - Chat Restriction Enabled.");
            debugLog("[NakedAndAfraid] Enabled ChatRestrictionListener");
        } else {
            plugin.getLogger().info("Naked And Afraid - Chat Restriction Disabled.");
            debugLog("[NakedAndAfraid] ChatRestrictionListener not enabled");
        }

        // Tab hiding
        if (plugin.getConfig().getBoolean("disable-tab", true)) {
            enableTabListClearer();
            plugin.getLogger().info("Naked And Afraid - Tab Hider Enabled.");
            debugLog("[NakedAndAfraid] Enabled TabListClearer");
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (isWorldEnabled(player.getWorld().getName())) {
                    applyTabListClearerToPlayer(player);
                    debugLog("[NakedAndAfraid] Applied TabListClearer to " + player.getName());
                }
            }
        } else {
            debugLog("[NakedAndAfraid] Tab hider not enabled (disable-tab=false)");
        }

        // Armor damage
        if (plugin.getConfig().getBoolean("armor-damage.enabled", true)) {
            setArmorDamageListener(createArmorDamageListener());
            plugin.getServer().getPluginManager()
                    .registerEvents(getArmorDamageListener(), plugin);
            refreshArmorTasks();
            plugin.getLogger().info("Naked And Afraid - Armor Damage Enabled.");
            debugLog("[NakedAndAfraid] Enabled ArmorDamageListener and refreshed armor tasks");
        } else {
            plugin.getLogger().info("Naked And Afraid - Armor Damage Disabled.");
            debugLog("[NakedAndAfraid] ArmorDamageListener not enabled");
        }

        // Join/quit suppression
        if (plugin.getConfig().getBoolean("disable-join-quit-messages", true)) {
            setJoinQuitSuppressor(createJoinQuitSuppressor());
            plugin.getServer().getPluginManager()
                    .registerEvents(getJoinQuitSuppressor(), plugin);
            plugin.getLogger().info("Naked And Afraid - Message Disabling Enabled.");
            debugLog("[NakedAndAfraid] Enabled JoinQuitMessageSuppressor");
        } else {
            plugin.getLogger().info("Naked And Afraid - Message Disabling Disabled.");
            debugLog("[NakedAndAfraid] JoinQuitMessageSuppressor not enabled");
        }

        // Version-specific toggleable listeners (totem etc.)
        doReloadVersionListeners();

        refreshManagerWorlds();

        debugLog("[NakedAndAfraid] Listener reload completed");
    }

    // -----------------------------------------------------------------------
    // onCommand
    // -----------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        debugLog("[NakedAndAfraid] Processing command: " + label
                + " " + String.join(" ", args));

        if (!(label.equalsIgnoreCase("nf")
                || label.equalsIgnoreCase("nakedafraid"))) {
            debugLog("[NakedAndAfraid] Invalid command label: " + label);
            return false;
        }

        // Pre-1.13 servers treat `return false` as "show usage"; 1.13+ treat it as
        // "unknown command".  We preserve the original behaviour.
        boolean isPre113 = !Bukkit.getBukkitVersion()
                .matches(".*1\\.(1[3-9]|2[0-9]).*");

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            int page = 1;
            if (args.length >= 2) {
                try {
                    page = Integer.parseInt(args[1]);
                    debugLog("[NakedAndAfraid] Parsed help page: " + page);
                } catch (NumberFormatException ignored) {
                    sendErrorMessage(sender, "Invalid help page number. Showing page 1.");
                    debugLog("[NakedAndAfraid] Invalid help page: " + args[1]);
                }
            }
            sendHelpMessage(sender, page);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "reloadconfig":
                if (!sender.hasPermission("nakedandafraid.reload")) {
                    sendErrorMessage(sender,
                            "You don't have permission to execute this command.");
                    debugLog("[NakedAndAfraid] " + sender.getName()
                            + " lacks permission for reloadconfig");
                    return !isPre113;
                }
                plugin.reloadConfig();
                teleportOnCountdownEnd = plugin.getConfig()
                        .getBoolean("teleport-on-countdown-end", false);
                multipleSpawnPriority = plugin.getConfig()
                        .getString("multiple-spawn-priority", "FIRST").toUpperCase();
                debugLog("[NakedAndAfraid] Reloaded config");
                reloadListeners();
                if (getArmorDamageListener() != null) {
                    refreshArmorTasks();
                    debugLog("[NakedAndAfraid] Refreshed armor tasks after reload");
                }
                reloadSpawnManager();
                sendSuccessMessage(sender, "Naked and Afraid config reloaded.");
                return true;
            case "spawn":
                if (!sender.hasPermission("nakedandafraid.spawn")) {
                    sendErrorMessage(sender,
                            "You don't have permission to execute this command.");
                    return !isPre113;
                }
                return handleSpawnCommand(sender, args);
            case "teams":
                if (!sender.hasPermission("nakedandafraid.team")) {
                    sendErrorMessage(sender, "You don't have permission to execute this command.");
                    return !isPre113;
                }
                return handleTeamsCommand(sender, args);
            case "team":
                if (!sender.hasPermission("nakedandafraid.team")) {
                    sendErrorMessage(sender,
                            "You don't have permission to execute this command.");
                    return !isPre113;
                }
                return handleTeamCommand(sender, args);
            case "user":
                if (!sender.hasPermission("nakedandafraid.user")) {
                    sendErrorMessage(sender,
                            "You don't have permission to execute this command.");
                    return !isPre113;
                }
                return handleUserCommand(sender, args);
        }

        // Let version modules intercept extra subcommands (e.g. "teams" in v1_21)
        if (handleVersionCommand(sender, args, isPre113)) {
            return true;
        }

        sendErrorMessage(sender, "Unknown subcommand. Use /nf help for commands.");
        debugLog("[NakedAndAfraid] Unknown subcommand from "
                + sender.getName() + ": " + args[0]);
        return !isPre113;
    }

    // -----------------------------------------------------------------------
    // onTabComplete
    // -----------------------------------------------------------------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        debugLog("[NakedAndAfraid] Tab completion: " + command.getName()
                + " " + String.join(" ", args));

        if (!(command.getName().equalsIgnoreCase("nf")
                || command.getName().equalsIgnoreCase("nakedafraid"))) {
            return null;
        }

        if (args.length == 1) {
            List<String> base = Arrays.asList("help", "reloadconfig", "spawn", "team", "teams", "user");
            List<String> extra = getVersionSubcommands();
            if (extra.isEmpty()) {
                return base;
            }
            List<String> combined = new java.util.ArrayList<>(base);
            combined.addAll(extra);
            return combined;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "spawn":
                return tabCompleteSpawn(sender, args);
            case "team":
                return tabCompleteTeam(sender, args);
            case "teams":
                return tabCompleteTeams(sender, args);
            case "user":
                return tabCompleteUser(sender, args);
        }

        // Let version modules handle their own extra subcommands
        List<String> versionResult = tabCompleteVersion(sender, args);
        if (versionResult != null) {
            return versionResult;
        }

        return new java.util.ArrayList<>();
    }

    // -----------------------------------------------------------------------
    // Tab-complete helpers (shared across all versions)
    // -----------------------------------------------------------------------

    private List<String> tabCompleteSpawn(CommandSender sender, String[] args) {
        if (args.length == 2) {
            return Arrays.asList("create", "rename", "remove", "list", "tp", "tpall");
        }
        if (args.length == 3) {
            if (args[1].equalsIgnoreCase("create")) {
                return new java.util.ArrayList<>();
            }
            if (args[1].equalsIgnoreCase("rename")
                    || args[1].equalsIgnoreCase("remove")
                    || args[1].equalsIgnoreCase("tp")) {
                return new java.util.ArrayList<>(getSpawnNames());
            }
        }
        if (args.length == 4) {
            if (args[1].equalsIgnoreCase("create")
                    || args[1].equalsIgnoreCase("tp")) {
                return getOnlinePlayerNames();
            }
            if (args[1].equalsIgnoreCase("rename")) {
                return new java.util.ArrayList<>();
            }
        }
        return new java.util.ArrayList<>();
    }

    private List<String> tabCompleteTeams(CommandSender sender, String[] args) {
        if (args.length == 2) {
            return Arrays.asList("create", "remove", "list");
        }
        if (args.length == 3) {
            if (args[1].equalsIgnoreCase("remove")) {
                return new java.util.ArrayList<>(getTeamNames());
            }
            if (args[1].equalsIgnoreCase("create")) {
                return new java.util.ArrayList<>();
            }
        }
        if (args.length == 4 && args[1].equalsIgnoreCase("create")) {
            List<String> colors = new java.util.ArrayList<>();
            for (String c : validTeamColors()) {
                if (c.startsWith(args[2].toUpperCase())) colors.add(c);
            }
            return colors;
        }
        return new java.util.ArrayList<>();
    }

    private List<String> tabCompleteTeam(CommandSender sender, String[] args) {
        if (args.length == 2) {
            return Arrays.asList("block", "setblock", "meta");
        }
        if (args.length == 3) {
            if (args[1].equalsIgnoreCase("setblock")
                    || args[1].equalsIgnoreCase("block")) {
                return getTeamNames();
            }
            if (args[1].equalsIgnoreCase("meta")) {
                return new java.util.ArrayList<>();
            }
        }
        if (args.length == 5) {
            if (args[1].equalsIgnoreCase("setblock")
                    && sender instanceof Player) {
                return Collections.singletonList(
                        String.valueOf(((Player) sender).getLocation().getBlockY()));
            }
            if (args[1].equalsIgnoreCase("meta")
                    && args[2].equalsIgnoreCase("color")
                    && args[3].equalsIgnoreCase("set")) {
                return filterByPrefix(validTeamColors(), args[4].toUpperCase());
            }
        }
        if (args.length == 6 && args[1].equalsIgnoreCase("setblock")
                && sender instanceof Player) {
            return Collections.singletonList(
                    String.valueOf(((Player) sender).getLocation().getBlockZ()));
        }
        return new java.util.ArrayList<>();
    }

    private List<String> tabCompleteUser(CommandSender sender, String[] args) {
        if (args.length == 2) {
            return getOnlinePlayerNames();
        }
        if (args.length == 3 && args[2].equalsIgnoreCase("team")) {
            return Arrays.asList("add", "remove", "list");
        }
        if (args.length == 4 && args[2].equalsIgnoreCase("team")
                && (args[3].equalsIgnoreCase("add")
                || args[3].equalsIgnoreCase("remove"))) {
            return getTeamNames();
        }
        return new java.util.ArrayList<>();
    }

    // -----------------------------------------------------------------------
    // Shared help message
    // -----------------------------------------------------------------------

    private void sendHelpMessage(CommandSender sender, int page) {
        debugLog("[NakedAndAfraid] Preparing help message for "
                + sender.getName() + ", page " + page);
        List<String> helpLines = getHelpLines();

        int LINES_PER_PAGE = 6;
        int totalPages = (int) Math.ceil(helpLines.size() / (double) LINES_PER_PAGE);
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;

        sendRawMessage(sender, "§6==== NakedAndAfraid Help ====");

        int start = (page - 1) * LINES_PER_PAGE;
        int end   = Math.min(start + LINES_PER_PAGE, helpLines.size());
        for (int i = start; i < end; i++) {
            sender.sendMessage(helpLines.get(i));
        }

        sendRawMessage(sender, "§ePage " + page + " of " + totalPages);
        debugLog("[NakedAndAfraid] Sent help page " + page + " to " + sender.getName());
    }

    /**
     * The base help line list. Version subclasses may override to append
     * their extra commands (e.g. "teams" in v1_21).
     */
    protected List<String> getHelpLines() {
        return Arrays.asList(
                "§e/nf help §7- Show this help message",
                "§e/nf §7- Alias for /nf help",
                "§e/nf reloadconfig §7- Reload the plugin config",
                "§e/nf spawn create (spawn-name) (target-player) §7- Define a spawn",
                "§e/nf spawn remove (spawn-name) §7- Delete a spawn",
                "§e/nf spawn list §7- List all spawns",
                "§e/nf spawn tp (spawn-name) (player) §7- Teleport a player to a spawn",
                "§e/nf spawn tpall §7- Teleport all players to their spawns",
                "§e/nf teams create (team-name) (team-color) §7- Define a new team",
                "§e/nf teams remove (team-name) §7- Delete an existing team",
                "§e/nf teams list §7- List all existing teams",
                "§e/nf team (team-name) meta color get §7- Get a team's color",
                "§e/nf team (team-name) meta color set (color) §7- Change a team's color",
                "§e/nf team (team-name) setblock (y) (z) §7- Set a team's spawn block with coordinates",
                "§e/nf team (team-name) block selector (player) §7 - Give the Block Selector tool to a player",
                "§e/nf user (player) team add (team-name) §7- Add a player to a team",
                "§e/nf user (player) team remove (team-name) §7- Remove from team",
                "§e/nf user (player) team list §7- List a player's teams"
        );
    }

    // -----------------------------------------------------------------------
    // Shared startup banner
    // -----------------------------------------------------------------------

    protected void logStartupInfo() {
        debugLog("[NakedAndAfraid] Sending startup information to console");
        ConsoleCommandSender console = Bukkit.getServer().getConsoleSender();

        console.sendMessage(" ");
        sendConsoleBanner(console);
        console.sendMessage("");

        String currentVersion = plugin.getDescription().getVersion();
        debugLog("[NakedAndAfraid] Checking version: current=" + currentVersion);
        VersionChecker checker = new VersionChecker(
                this, plugin.getConfig().getString("github-api-token", null));
        String latestVersion = checker.getLatestVersion();

        if (latestVersion != null && checker.isOutdated(currentVersion)) {
            sendOutdatedWarning(console, currentVersion, latestVersion);
            debugLog("[NakedAndAfraid] Notified console of outdated version: "
                    + latestVersion);
        } else {
            debugLog("[NakedAndAfraid] Version is up to date");
        }
    }

    // -----------------------------------------------------------------------
    // Shared utility
    // -----------------------------------------------------------------------

    protected List<String> validTeamColors() {
        return Arrays.asList(
                "RED", "BLUE", "GREEN", "YELLOW", "AQUA",
                "DARK_PURPLE", "GOLD", "LIGHT_PURPLE", "WHITE");
    }

    private static List<String> filterByPrefix(List<String> list, String prefix) {
        List<String> result = new java.util.ArrayList<>();
        for (String s : list) {
            if (s.startsWith(prefix)) {
                result.add(s);
            }
        }
        return result;
    }

    private static List<String> getOnlinePlayerNames() {
        List<String> names = new java.util.ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            names.add(p.getName());
        }
        return names;
    }

    // -----------------------------------------------------------------------
    // Abstract template methods - version subclasses must implement these
    // -----------------------------------------------------------------------

    /**
     * Persists the world list to config.yml.
     * v1_8 and v1_17 do manual file I/O; v1_21 uses {@code ConfigurationSection}.
     */
    protected abstract void setWorldList();

    /**
     * Creates and registers version-specific listeners that are always on
     * (e.g. {@code GlobalDeathSoundListener}, {@code TeamListener}).
     * Called once from {@link #onEnable()}.
     */
    protected abstract void initManagers();

    /** Initialises the {@code SpawnManager} and loads persisted spawns. */
    protected abstract void initSpawnManager();

    /** Initialises {@code TeleportHelper} and any version-specific extension. */
    protected abstract void initTeleportHelper();

    /**
     * Registers version-specific listeners that are always on and aren't part
     * of the toggleable reload cycle (e.g. {@code VersionNotifyListener}).
     * Called at the end of {@link #onEnable()}.
     */
    protected abstract void registerVersionListeners();

    /**
     * Unregisters version-specific always-on listeners during {@link #onDisable()}.
     * Most subclasses delegate to {@code HandlerList.unregisterAll(plugin)}, but
     * the hook exists for finer-grained cleanup if needed.
     */
    protected abstract void unregisterVersionListeners();

    /**
     * Creates the version-appropriate {@code VersionNotifyListener}.
     * Returns a {@link Listener} so the abstract base can register it without
     * knowing the concrete type.
     */
    protected abstract Listener createVersionNotifyListener();

    // --- toggleable listener factories ---

    protected abstract Listener createChatRestrictionListener();
    protected abstract Listener createArmorDamageListener();
    protected abstract Listener createJoinQuitSuppressor();

    // --- toggleable listener accessors (subclass holds the typed field) ---

    protected abstract Listener getChatRestrictionListener();
    protected abstract void setChatRestrictionListener(Listener l);

    protected abstract Listener getArmorDamageListener();
    protected abstract void setArmorDamageListener(Listener l);
    protected abstract void refreshArmorTasks();
    protected abstract void disableArmorDamage();

    protected abstract Listener getJoinQuitSuppressor();
    protected abstract void setJoinQuitSuppressor(Listener l);

    // --- tab list clearer ---

    protected abstract void enableTabListClearer();
    protected abstract void disableTabListClearer();
    protected abstract void applyTabListClearerToPlayer(Player player);

    // --- version listener reload hooks ---

    /**
     * Called during the tear-down phase of {@link #reloadListeners()}.
     * Unregisters toggleable version-specific listeners (e.g. totem).
     */
    protected abstract void doUnregisterVersionListeners();

    /**
     * Called during the rebuild phase of {@link #reloadListeners()}.
     * Re-creates and registers toggleable version-specific listeners from config.
     */
    protected abstract void doReloadVersionListeners();

    // --- command delegation ---

    protected abstract boolean handleSpawnCommand(CommandSender sender, String[] args);
    protected abstract boolean handleTeamsCommand(CommandSender sender, String[] args);
    protected abstract boolean handleTeamCommand(CommandSender sender, String[] args);
    protected abstract boolean handleUserCommand(CommandSender sender, String[] args);

    /**
     * Hook for version-specific subcommands (e.g. {@code "teams"} in v1_21).
     * Return {@code true} if the subcommand was handled; {@code false} to let
     * the base show "unknown subcommand".
     */
    protected boolean handleVersionCommand(CommandSender sender,
                                           String[] args, boolean isPre113) {
        return false;   // no extra subcommands by default
    }

    /** Extra first-level tab-complete entries injected by version subclasses. */
    protected List<String> getVersionSubcommands() {
        return new java.util.ArrayList<>();
    }

    /**
     * Tab-complete hook for version-specific subcommands.
     * Return a list to provide completions, or {@code null} to fall through.
     */
    protected List<String> tabCompleteVersion(CommandSender sender, String[] args) {
        return null;
    }

    // --- spawn / team data for tab-complete ---

    protected abstract java.util.Set<String> getSpawnNames();
    protected abstract List<String> getTeamNames();

    // --- manager world refresh ---

    protected abstract void refreshManagerWorlds();

    /** Reloads spawns from disk (called by reloadconfig command). */
    protected abstract void reloadSpawnManager();

    /** Saves spawn data to disk (called on disable). */
    protected abstract void saveSpawnManager();

    // --- chat / manager listener cleanup ---

    private void unregisterAndClearChatRestriction() {
        Listener l = getChatRestrictionListener();
        if (l != null) {
            HandlerList.unregisterAll(l);
            debugLog("[NakedAndAfraid] Unregistered ChatRestrictionListener");
            setChatRestrictionListener(null);
        }
    }

    private void unregisterAndClearJoinQuit() {
        Listener l = getJoinQuitSuppressor();
        if (l != null) {
            HandlerList.unregisterAll(l);
            debugLog("[NakedAndAfraid] Unregistered JoinQuitMessageSuppressor");
            setJoinQuitSuppressor(null);
        }
    }

    // -----------------------------------------------------------------------
    // Abstract message-sending hooks
    //   Implementations send legacy §-codes on old servers or Adventure
    //   components on new ones. The base class always has a plain fallback.
    // -----------------------------------------------------------------------

    /**
     * Sends a red error message to {@code sender}.
     * Default: legacy colour codes. Override for Adventure.
     */
    protected void sendErrorMessage(CommandSender sender, String text) {
        sender.sendMessage("§c" + text);
    }

    /**
     * Sends a green success message to {@code sender}.
     * Default: legacy colour codes. Override for Adventure.
     */
    protected void sendSuccessMessage(CommandSender sender, String text) {
        sender.sendMessage("§a" + text);
    }

    /**
     * Sends a raw (already-formatted) message to {@code sender}.
     * Default: pass-through. Override to translate to Adventure if needed.
     */
    protected void sendRawMessage(CommandSender sender, String legacyText) {
        sender.sendMessage(legacyText);
    }

    /**
     * Prints the ASCII art banner to the console.
     * Default: legacy colour codes. Override for Adventure.
     */
    protected void sendConsoleBanner(ConsoleCommandSender console) {
        console.sendMessage("§6__   ____  _______ ");
        console.sendMessage("§6| \\ | | | | ____||");
        console.sendMessage("§6|  \\| | | | ||_   ");
        console.sendMessage("§6| |\\| | | | __||  ");
        console.sendMessage("§6|_| \\_|_| |_||    ");
        console.sendMessage("");
        console.sendMessage("§6NakedAndAfraid Plugin §4v"
                + plugin.getDescription().getVersion());
        console.sendMessage("§eRunning on §b" + Bukkit.getServer().getName()
                + " §f" + Bukkit.getServer().getVersion());
        console.sendMessage("");
        console.sendMessage("§aNakedAndAfraid Plugin enabled successfully!");
    }

    /**
     * Prints the outdated-version warning to the console.
     * Default: legacy colour codes. Override for Adventure.
     */
    protected void sendOutdatedWarning(ConsoleCommandSender console,
                                       String current, String latest) {
        plugin.getLogger().warning(
                "[NakedAndAfraid] There is a new Naked And Afraid Plugin version"
                        + " available: " + latest + " (Current: " + current + ")");
        console.sendMessage("§6[NakedAndAfraid] §cDownload it here: "
                + "§chttps://modrinth.com/plugin/naked-and-afraid-plugin/versions");
    }
}