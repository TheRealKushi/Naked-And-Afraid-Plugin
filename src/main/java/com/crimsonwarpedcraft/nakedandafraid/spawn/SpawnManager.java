package com.crimsonwarpedcraft.nakedandafraid.spawn;

import com.crimsonwarpedcraft.nakedandafraid.NakedAndAfraid;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class SpawnManager {
    private final NakedAndAfraid plugin;
    private final File spawnsFile;
    private FileConfiguration spawnsConfig;

    private final Map<String, SpawnData> spawns = new HashMap<>();

    public SpawnManager(NakedAndAfraid plugin) {
        this.plugin = plugin;
        this.spawnsFile = new File(plugin.getDataFolder(), "spawns.yml");
        plugin.debugLog("[SpawnManager] Initialized SpawnManager for Bukkit version " + Bukkit.getBukkitVersion() +
                ", spawns file: " + spawnsFile.getPath());
        loadSpawnsFile();
    }

    /**
     * Checks if the server supports the Adventure API (Minecraft 1.19+).
     */
    private boolean isAdventureSupported() {
        try {
            String version = Bukkit.getBukkitVersion().split("-")[0];
            String[] parts = version.split("\\.");
            int major = Integer.parseInt(parts[1]);
            return major >= 19;
        } catch (Exception e) {
            plugin.debugLog("[SpawnManager] Failed to parse Bukkit version: " + e.getMessage());
            return false;
        }
    }

    /**
     * Sends a message to the sender, using Adventure API for 1.19+ or legacy chat for 1.12–1.18.2.
     */
    private void sendMessage(CommandSender sender, String message, String legacyColor) {
        if (isAdventureSupported()) {
            NamedTextColor color = parseNamedTextColor(legacyColor);
            if (sender instanceof Player player) {
                player.sendMessage(Component.text(message).color(color != null ? color : NamedTextColor.WHITE));
            } else {
                sender.sendMessage(message); // Console doesn't support Component
            }
        } else {
            sender.sendMessage(legacyColor + message);
        }
        plugin.debugLog("[SpawnManager] Sent message to " + sender.getName() + ": " + message);
    }

    /**
     * Parses a legacy color code to NamedTextColor for 1.19+.
     */
    private NamedTextColor parseNamedTextColor(String legacyColor) {
        return switch (legacyColor) {
            case "§c" -> NamedTextColor.RED;
            case "§e" -> NamedTextColor.YELLOW;
            case "§a" -> NamedTextColor.GREEN;
            case "§6" -> NamedTextColor.GOLD;
            case "§7" -> NamedTextColor.GRAY;
            case "§f" -> NamedTextColor.WHITE;
            default -> null;
        };
    }

    public void loadSpawns() {
        plugin.debugLog("[SpawnManager] Loading spawns from spawns.yml");
        loadSpawnsFile();
        spawns.clear();
        plugin.debugLog("[SpawnManager] Cleared existing spawns map");

        if (spawnsConfig.isConfigurationSection("spawns")) {
            for (String key : Objects.requireNonNull(spawnsConfig.getConfigurationSection("spawns")).getKeys(false)) {
                double x = spawnsConfig.getDouble("spawns." + key + ".x");
                double y = spawnsConfig.getDouble("spawns." + key + ".y");
                double z = spawnsConfig.getDouble("spawns." + key + ".z");
                String worldName = spawnsConfig.getString("spawns." + key + ".world");
                String target = spawnsConfig.getString("spawns." + key + ".targetPlayer", key);

                assert worldName != null;
                World world = Bukkit.getWorld(worldName);
                if (world != null) {
                    Location loc = new Location(world, x, y, z);
                    spawns.put(key.toLowerCase(), new SpawnData(loc, target));
                    plugin.debugLog("[SpawnManager] Loaded spawn '" + key + "' at " + formatLocation(loc) +
                            " for target player " + target);
                } else {
                    plugin.debugLog("[SpawnManager] Skipped spawn '" + key + "' due to invalid world: " + worldName);
                }
            }
        } else {
            plugin.debugLog("[SpawnManager] No spawns section found in spawns.yml");
        }
        plugin.debugLog("[SpawnManager] Loaded config: max-spawns=" + plugin.getConfig().getInt("max-spawns", 10) +
                ", multiple-spawn-priority=" + plugin.getMultipleSpawnPriority());
    }

    public void saveSpawns() {
        plugin.debugLog("[SpawnManager] Saving spawns to spawns.yml");
        spawnsConfig.set("spawns", null);

        for (Map.Entry<String, SpawnData> entry : spawns.entrySet()) {
            String key = entry.getKey();
            SpawnData data = entry.getValue();
            Location loc = data.location();

            spawnsConfig.set("spawns." + key + ".x", loc.getX());
            spawnsConfig.set("spawns." + key + ".y", loc.getY());
            spawnsConfig.set("spawns." + key + ".z", loc.getZ());
            spawnsConfig.set("spawns." + key + ".world", loc.getWorld().getName());
            spawnsConfig.set("spawns." + key + ".targetPlayer", data.targetPlayerName());
            plugin.debugLog("[SpawnManager] Saved spawn '" + key + "' at " + formatLocation(loc) +
                    " for target player " + data.targetPlayerName());
        }

        try {
            spawnsConfig.save(spawnsFile);
            plugin.debugLog("[SpawnManager] Successfully saved spawns.yml");
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save spawns.yml");
            plugin.debugLog("[SpawnManager] Failed to save spawns.yml: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void loadSpawnsFile() {
        if (!spawnsFile.exists()) {
            plugin.debugLog("[SpawnManager] spawns.yml does not exist, attempting to create");
            plugin.saveResource("spawns.yml", false);
            if (!spawnsFile.exists()) {
                try {
                    spawnsFile.createNewFile();
                    plugin.debugLog("[SpawnManager] Created new spawns.yml");
                } catch (IOException e) {
                    plugin.getLogger().severe("Could not create spawns.yml");
                    plugin.debugLog("[SpawnManager] Failed to create spawns.yml: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
        spawnsConfig = YamlConfiguration.loadConfiguration(spawnsFile);
        plugin.debugLog("[SpawnManager] Loaded spawns.yml configuration");
    }

    public boolean handleCommand(CommandSender sender, String[] args) {
        plugin.debugLog("[SpawnManager] Processing command for " + sender.getName() + ": " + String.join(" ", args));
        if (args.length < 2) {
            sendMessage(sender, "Usage: /nf spawn <create|rename|remove|list|tp|tpall> ...", "§c");
            plugin.debugLog("[SpawnManager] Invalid arguments for " + sender.getName() + ", expected at least 2");
            return true;
        }

        String sub = args[1].toLowerCase();
        plugin.debugLog("[SpawnManager] Subcommand: " + sub);

        return switch (sub) {
            case "create" -> handleCreate(sender, args);
            case "rename" -> handleRename(sender, args);
            case "remove" -> handleRemove(sender, args);
            case "list" -> handleList(sender);
            case "tp" -> handleTp(sender, args);
            case "tpall" -> handleTpAll(sender);
            default -> {
                sendMessage(sender, "Unknown spawn subcommand.", "§c");
                plugin.debugLog("[SpawnManager] Unknown subcommand for " + sender.getName() + ": " + sub);
                yield true;
            }
        };
    }

    private boolean handleCreate(CommandSender sender, String[] args) {
        plugin.debugLog("[SpawnManager] Handling create command for " + sender.getName() + ": " + String.join(" ", args));
        if (args.length < 3) {
            sendMessage(sender, "Usage: /nf spawn create <name> [x y z] [targetPlayer]", "§c");
            plugin.debugLog("[SpawnManager] Invalid arguments for create, expected at least 3");
            return true;
        }

        String spawnName = args[2].toLowerCase();
        plugin.debugLog("[SpawnManager] Spawn name: " + spawnName);
        if (spawns.containsKey(spawnName)) {
            sendMessage(sender, "Spawn '" + spawnName + "' already exists.", "§c");
            plugin.debugLog("[SpawnManager] Spawn '" + spawnName + "' already exists for " + sender.getName());
            return true;
        }
        int maxSpawns = plugin.getConfig().getInt("max-spawns", 10);
        if (spawns.size() >= maxSpawns) {
            sendMessage(sender, "You have reached the max number of spawns.", "§c");
            plugin.debugLog("[SpawnManager] Max spawns reached (" + spawns.size() + "/" + maxSpawns + ") for " + sender.getName());
            return true;
        }

        Location loc = null;
        String targetPlayerName = null;

        int remaining = args.length - 3;

        boolean hasCoords = false;
        if (remaining >= 3) {
            hasCoords = isDouble(args[3]) && isDouble(args[4]) && isDouble(args[5]);
            plugin.debugLog("[SpawnManager] Checking coordinates: hasCoords=" + hasCoords);
        }

        if (hasCoords) {
            try {
                double x = Double.parseDouble(args[3]);
                double y = Double.parseDouble(args[4]);
                double z = Double.parseDouble(args[5]);

                World world;
                if (sender instanceof Player p) {
                    world = p.getWorld();
                } else {
                    world = Bukkit.getWorlds().get(0); // Replaced getFirst with get(0) for Java 8 compatibility
                    plugin.debugLog("[SpawnManager] Using default world for console: " + world.getName());
                }
                loc = new Location(world, x, y, z);
                plugin.debugLog("[SpawnManager] Parsed coordinates for spawn: " + formatLocation(loc));

                if (args.length >= 7) {
                    targetPlayerName = args[6];
                    plugin.debugLog("[SpawnManager] Specified target player: " + targetPlayerName);
                }

            } catch (NumberFormatException e) {
                sendMessage(sender, "Invalid coordinates.", "§c");
                plugin.debugLog("[SpawnManager] Invalid coordinates for " + sender.getName() + ": " + String.join(" ", args));
                return true;
            }
        } else {
            if (args.length >= 4) {
                targetPlayerName = args[3];
                plugin.debugLog("[SpawnManager] Specified target player: " + targetPlayerName);
            }

            if (sender instanceof Player p) {
                loc = p.getLocation();
                plugin.debugLog("[SpawnManager] Using sender's location: " + formatLocation(loc));
            } else {
                sendMessage(sender, "Coordinates or player must be specified when run from console.", "§c");
                plugin.debugLog("[SpawnManager] No coordinates or player specified for console sender " + sender.getName());
                return true;
            }
        }

        if (targetPlayerName == null) {
            if (sender instanceof Player p) {
                targetPlayerName = p.getName();
                plugin.debugLog("[SpawnManager] Defaulting target player to sender: " + targetPlayerName);
            } else {
                targetPlayerName = spawnName;
                plugin.debugLog("[SpawnManager] Defaulting target player to spawn name: " + targetPlayerName);
            }
        }

        if (!Bukkit.getOfflinePlayer(targetPlayerName).hasPlayedBefore() && Bukkit.getPlayerExact(targetPlayerName) == null) {
            sendMessage(sender, "Warning: Player '" + targetPlayerName + "' has never joined the server before (still saved).", "§e");
            plugin.debugLog("[SpawnManager] Warning: Target player '" + targetPlayerName + "' has never joined");
        }

        spawns.put(spawnName, new SpawnData(loc, targetPlayerName));
        saveSpawns();
        sendMessage(sender, "Spawn '" + spawnName + "' created at " +
                loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() +
                " for player " + targetPlayerName, "§a");
        plugin.debugLog("[SpawnManager] Created spawn '" + spawnName + "' at " + formatLocation(loc) +
                " for player " + targetPlayerName + " by " + sender.getName());
        return true;
    }

    private static boolean isDouble(String s) {
        if (s == null) return false;
        try {
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean handleRename(CommandSender sender, String[] args) {
        plugin.debugLog("[SpawnManager] Handling rename command for " + sender.getName() + ": " + String.join(" ", args));
        if (args.length < 4) {
            sendMessage(sender, "Usage: /nf spawn rename <oldName> <newName>", "§c");
            plugin.debugLog("[SpawnManager] Invalid arguments for rename, expected 4");
            return true;
        }

        String oldName = args[2].toLowerCase();
        String newName = args[3].toLowerCase();
        plugin.debugLog("[SpawnManager] Renaming spawn from '" + oldName + "' to '" + newName + "'");

        if (!spawns.containsKey(oldName)) {
            sendMessage(sender, "Spawn '" + oldName + "' does not exist.", "§c");
            plugin.debugLog("[SpawnManager] Spawn '" + oldName + "' does not exist for " + sender.getName());
            return true;
        }
        if (spawns.containsKey(newName)) {
            sendMessage(sender, "Spawn '" + newName + "' already exists.", "§c");
            plugin.debugLog("[SpawnManager] Spawn '" + newName + "' already exists for " + sender.getName());
            return true;
        }

        SpawnData data = spawns.remove(oldName);
        spawns.put(newName, data);
        saveSpawns();

        sendMessage(sender, "Spawn '" + oldName + "' renamed to '" + newName + "'.", "§a");
        plugin.debugLog("[SpawnManager] Renamed spawn '" + oldName + "' to '" + newName + "' for " + sender.getName());
        return true;
    }

    private boolean handleRemove(CommandSender sender, String[] args) {
        plugin.debugLog("[SpawnManager] Handling remove command for " + sender.getName() + ": " + String.join(" ", args));
        if (args.length < 3) {
            sendMessage(sender, "Usage: /nf spawn remove <name>", "§c");
            plugin.debugLog("[SpawnManager] Invalid arguments for remove, expected 3");
            return true;
        }

        String name = args[2].toLowerCase();
        plugin.debugLog("[SpawnManager] Removing spawn '" + name + "'");
        if (!spawns.containsKey(name)) {
            sendMessage(sender, "Spawn '" + name + "' does not exist.", "§c");
            plugin.debugLog("[SpawnManager] Spawn '" + name + "' does not exist for " + sender.getName());
            return true;
        }

        spawns.remove(name);
        spawnsConfig.set("spawns." + name, null);
        try {
            spawnsConfig.save(spawnsFile);
            plugin.debugLog("[SpawnManager] Successfully removed spawn '" + name + "' and saved spawns.yml");
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save spawns.yml");
            plugin.debugLog("[SpawnManager] Failed to save spawns.yml after removing '" + name + "': " + e.getMessage());
            e.printStackTrace();
        }

        sendMessage(sender, "Spawn '" + name + "' removed.", "§a");
        plugin.debugLog("[SpawnManager] Removed spawn '" + name + "' for " + sender.getName());
        return true;
    }

    private boolean handleList(CommandSender sender) {
        plugin.debugLog("[SpawnManager] Handling list command for " + sender.getName());
        if (spawns.isEmpty()) {
            sendMessage(sender, "No spawns defined.", "§e");
            plugin.debugLog("[SpawnManager] No spawns defined for " + sender.getName());
            return true;
        }
        sendMessage(sender, "==== Spawns ====", "§6");
        for (Map.Entry<String, SpawnData> entry : spawns.entrySet()) {
            Location loc = entry.getValue().location();
            sendMessage(sender, entry.getKey() + " - " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() +
                    " (world: " + loc.getWorld().getName() + ")", "§e");
            plugin.debugLog("[SpawnManager] Listed spawn '" + entry.getKey() + "' at " + formatLocation(loc) +
                    " for " + sender.getName());
        }
        return true;
    }

    /**
     * Handles /nf spawn tp <name> [player]
     * Now supports multiple-spawn-priority for target player.
     */
    private boolean handleTp(CommandSender sender, String[] args) {
        plugin.debugLog("[SpawnManager] Handling tp command for " + sender.getName() + ": " + String.join(" ", args));
        if (args.length < 3) {
            sendMessage(sender, "Usage: /nf spawn tp <name> [player]", "§c");
            plugin.debugLog("[SpawnManager] Invalid arguments for tp, expected at least 3");
            return true;
        }

        String spawnName = args[2].toLowerCase();
        plugin.debugLog("[SpawnManager] Spawn name: " + spawnName);
        Player targetPlayer = null;

        if (args.length >= 4) {
            targetPlayer = Bukkit.getPlayerExact(args[3]);
            if (targetPlayer == null) {
                sendMessage(sender, "Player '" + args[3] + "' is not online.", "§c");
                plugin.debugLog("[SpawnManager] Player '" + args[3] + "' not online for " + sender.getName());
                return true;
            }
            plugin.debugLog("[SpawnManager] Target player: " + targetPlayer.getName());
        } else {
            if (sender instanceof Player p) {
                targetPlayer = p;
                plugin.debugLog("[SpawnManager] Defaulting target player to sender: " + targetPlayer.getName());
            } else {
                sendMessage(sender, "You must specify a player when using this command from console.", "§c");
                plugin.debugLog("[SpawnManager] No player specified for console sender " + sender.getName());
                return true;
            }
        }

        List<SpawnData> matchingSpawns = new ArrayList<>();
        for (Map.Entry<String, SpawnData> entry : spawns.entrySet()) {
            if (entry.getValue().targetPlayerName().equalsIgnoreCase(targetPlayer.getName())) {
                matchingSpawns.add(entry.getValue());
                plugin.debugLog("[SpawnManager] Found matching spawn '" + entry.getKey() + "' for " + targetPlayer.getName());
            }
        }

        if (spawns.containsKey(spawnName)) {
            SpawnData spawn = spawns.get(spawnName);
            plugin.getTeleportHelper().startCountdownTeleport(targetPlayer, spawn.location());
            sendMessage(sender, "Teleporting player " + targetPlayer.getName() + " to spawn '" + spawnName + "'...", "§a");
            plugin.debugLog("[SpawnManager] Teleporting " + targetPlayer.getName() + " to spawn '" + spawnName +
                    "' at " + formatLocation(spawn.location()));
            return true;
        }

        if (!matchingSpawns.isEmpty()) {
            String priority = plugin.getMultipleSpawnPriority();
            plugin.debugLog("[SpawnManager] Using multiple-spawn-priority: " + priority);
            SpawnData chosen = switch (priority) {
                case "FIRST" -> matchingSpawns.get(0); // Replaced getFirst with get(0) for Java 8 compatibility
                case "LAST" -> matchingSpawns.get(matchingSpawns.size() - 1);
                case "RANDOM" -> matchingSpawns.get(new Random().nextInt(matchingSpawns.size()));
                default -> matchingSpawns.get(0); // Default to first
            };
            plugin.getTeleportHelper().startCountdownTeleport(targetPlayer, chosen.location());
            sendMessage(sender, "Teleporting player " + targetPlayer.getName() + " to their spawn (" + priority + ")...", "§a");
            plugin.debugLog("[SpawnManager] Teleporting " + targetPlayer.getName() + " to their spawn (" + priority +
                    ") at " + formatLocation(chosen.location()));
            return true;
        }

        sendMessage(sender, "No spawn found for player " + targetPlayer.getName() + ".", "§c");
        plugin.debugLog("[SpawnManager] No spawn found for " + targetPlayer.getName());
        return true;
    }

    private boolean handleTpAll(CommandSender sender) {
        plugin.debugLog("[SpawnManager] Handling tpall command for " + sender.getName());
        if (spawns.isEmpty()) {
            sendMessage(sender, "No spawns defined.", "§e");
            plugin.debugLog("[SpawnManager] No spawns defined for " + sender.getName());
            return true;
        }

        for (Map.Entry<String, SpawnData> entry : spawns.entrySet()) {
            SpawnData spawn = entry.getValue();
            Player target = Bukkit.getPlayerExact(spawn.targetPlayerName());
            if (target == null) {
                sendMessage(sender, spawn.targetPlayerName() + " is not online!", "§c");
                plugin.debugLog("[SpawnManager] Skipped teleport for offline player " + spawn.targetPlayerName());
                continue;
            }
            plugin.getTeleportHelper().startCountdownTeleport(target, spawn.location());
            sendMessage(sender, "Teleporting " + target.getName() + " to their spawn...", "§a");
            plugin.debugLog("[SpawnManager] Teleporting " + target.getName() + " to spawn '" + entry.getKey() +
                    "' at " + formatLocation(spawn.location()));
        }
        return true;
    }

    public Map<String, SpawnData> getSpawns() {
        plugin.debugLog("[SpawnManager] Retrieved spawns map with " + spawns.size() + " entries");
        return Collections.unmodifiableMap(spawns);
    }

    /**
     * Helper method to format a Location for debug logging.
     */
    private String formatLocation(Location location) {
        return String.format("(world=%s, x=%.2f, y=%.2f, z=%.2f)",
                location.getWorld() != null ? location.getWorld().getName() : "null",
                location.getX(), location.getY(), location.getZ());
    }
}