// V1_17/SpawnManager

package com.crimsonwarpedcraft.nakedandafraid.v1_17.spawn;

import com.crimsonwarpedcraft.nakedandafraid.v1_17.NakedAndAfraid;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class SpawnManager {
    private final NakedAndAfraid nakedAndAfraid;
    private final Plugin plugin;
    private final File spawnsFile;
    private FileConfiguration spawnsConfig;

    private final Map<String, SpawnData> spawns = new HashMap<>();

    public SpawnManager(NakedAndAfraid nakedAndAfraid) {
        this.nakedAndAfraid = nakedAndAfraid;
        this.plugin = NakedAndAfraid.getPlugin();
        this.spawnsFile = new File(plugin.getDataFolder(), "spawns.yml");
        nakedAndAfraid.debugLog("[SpawnManager] Initialized SpawnManager for Bukkit version " + Bukkit.getBukkitVersion() +
                ", spawns file: " + spawnsFile.getPath());
        loadSpawnsFile();
    }

    /**
     * Sends a message to the sender using legacy chat formatting.
     */
    private void sendMessage(CommandSender sender, String message, String legacyColor) {
        sender.sendMessage(legacyColor + message);
        nakedAndAfraid.debugLog("[SpawnManager] Sent message to " + sender.getName() + ": " + message);
    }

    public void loadSpawns() {
        nakedAndAfraid.debugLog("[SpawnManager] Loading spawns from spawns.yml");
        loadSpawnsFile();
        spawns.clear();
        nakedAndAfraid.debugLog("[SpawnManager] Cleared existing spawns map");

        if (spawnsConfig.isConfigurationSection("spawns")) {
            for (var key : Objects.requireNonNull(spawnsConfig.getConfigurationSection("spawns")).getKeys(false)) {
                var x = spawnsConfig.getDouble("spawns." + key + ".x");
                var y = spawnsConfig.getDouble("spawns." + key + ".y");
                var z = spawnsConfig.getDouble("spawns." + key + ".z");
                var worldName = spawnsConfig.getString("spawns." + key + ".world");
                var target = spawnsConfig.getString("spawns." + key + ".targetPlayer", key);

                assert worldName != null;
                var world = Bukkit.getWorld(worldName);
                if (world != null) {
                    var loc = new Location(world, x, y, z);
                    spawns.put(key.toLowerCase(), new SpawnData(loc, target));
                    nakedAndAfraid.debugLog("[SpawnManager] Loaded spawn '" + key + "' at " + formatLocation(loc) +
                            " for target player " + target);
                } else {
                    nakedAndAfraid.debugLog("[SpawnManager] Skipped spawn '" + key + "' due to invalid world: " + worldName);
                }
            }
        } else {
            nakedAndAfraid.debugLog("[SpawnManager] No spawns section found in spawns.yml");
        }
        nakedAndAfraid.debugLog("[SpawnManager] Loaded config: max-spawns=" + plugin.getConfig().getInt("max-spawns", 10) +
                ", multiple-spawn-priority=" + nakedAndAfraid.getMultipleSpawnPriority());
    }

    public void saveSpawns() {
        nakedAndAfraid.debugLog("[SpawnManager] Saving spawns to spawns.yml");
        spawnsConfig.set("spawns", null);

        for (var entry : spawns.entrySet()) {
            var key = entry.getKey();
            var data = entry.getValue();
            var loc = data.location();

            spawnsConfig.set("spawns." + key + ".x", loc.getX());
            spawnsConfig.set("spawns." + key + ".y", loc.getY());
            spawnsConfig.set("spawns." + key + ".z", loc.getZ());
            spawnsConfig.set("spawns." + key + ".world", loc.getWorld().getName());
            spawnsConfig.set("spawns." + key + ".targetPlayer", data.targetPlayerName());
            nakedAndAfraid.debugLog("[SpawnManager] Saved spawn '" + key + "' at " + formatLocation(loc) +
                    " for target player " + data.targetPlayerName());
        }

        try {
            spawnsConfig.save(spawnsFile);
            nakedAndAfraid.debugLog("[SpawnManager] Successfully saved spawns.yml");
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save spawns.yml");
            nakedAndAfraid.debugLog("[SpawnManager] Failed to save spawns.yml: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void loadSpawnsFile() {
        if (!spawnsFile.exists()) {
            nakedAndAfraid.debugLog("[SpawnManager] spawns.yml does not exist, attempting to create");
            plugin.saveResource("spawns.yml", false);
            if (!spawnsFile.exists()) {
                try {
                    spawnsFile.createNewFile();
                    nakedAndAfraid.debugLog("[SpawnManager] Created new spawns.yml");
                } catch (IOException e) {
                    plugin.getLogger().severe("Could not create spawns.yml");
                    nakedAndAfraid.debugLog("[SpawnManager] Failed to create spawns.yml: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
        spawnsConfig = YamlConfiguration.loadConfiguration(spawnsFile);
        nakedAndAfraid.debugLog("[SpawnManager] Loaded spawns.yml configuration");
    }

    public void refreshWorlds() {
        nakedAndAfraid.debugLog("[SpawnManager] Refreshing worlds for spawns");
        var toRemove = new ArrayList<String>();
        for (var entry : spawns.entrySet()) {
            var spawnName = entry.getKey();
            var loc = entry.getValue().location();
            var worldName = loc.getWorld().getName();
            if (!nakedAndAfraid.isWorldEnabled(worldName)) {
                toRemove.add(spawnName);
                nakedAndAfraid.debugLog("[SpawnManager] Marking spawn '" + spawnName + "' for removal due to disabled world: " + worldName);
            }
        }
        for (var spawnName : toRemove) {
            spawns.remove(spawnName);
            spawnsConfig.set("spawns." + spawnName, null);
            nakedAndAfraid.debugLog("[SpawnManager] Removed spawn '" + spawnName + "' from disabled world");
        }
        if (!toRemove.isEmpty()) {
            try {
                spawnsConfig.save(spawnsFile);
                nakedAndAfraid.debugLog("[SpawnManager] Saved spawns.yml after removing spawns from disabled worlds");
            } catch (IOException e) {
                plugin.getLogger().severe("Could not save spawns.yml after world refresh");
                nakedAndAfraid.debugLog("[SpawnManager] Failed to save spawns.yml after world refresh: " + e.getMessage());
                e.printStackTrace();
            }
        }
        nakedAndAfraid.debugLog("[SpawnManager] World refresh completed, removed " + toRemove.size() + " spawns");
    }

    public boolean handleCommand(CommandSender sender, String[] args) {
        nakedAndAfraid.debugLog("[SpawnManager] Processing command for " + sender.getName() + ": " + String.join(" ", args));
        if (args.length < 2) {
            sendMessage(sender, "Usage: /nf spawn <create|rename|remove|list|tp|tpall> ...", "§c");
            nakedAndAfraid.debugLog("[SpawnManager] Invalid arguments for " + sender.getName() + ", expected at least 2");
            return true;
        }

        var sub = args[1].toLowerCase();
        nakedAndAfraid.debugLog("[SpawnManager] Subcommand: " + sub);

        return switch (sub) {
            case "create" -> handleCreate(sender, args);
            case "rename" -> handleRename(sender, args);
            case "remove" -> handleRemove(sender, args);
            case "list" -> handleList(sender);
            case "tp" -> handleTp(sender, args);
            case "tpall" -> handleTpAll(sender);
            default -> {
                sendMessage(sender, "Unknown spawn subcommand.", "§c");
                nakedAndAfraid.debugLog("[SpawnManager] Unknown subcommand for " + sender.getName() + ": " + sub);
                yield true;
            }
        };
    }

    private boolean handleCreate(CommandSender sender, String[] args) {
        nakedAndAfraid.debugLog("[SpawnManager] Handling create command for " + sender.getName() + ": " + String.join(" ", args));
        if (args.length < 3) {
            sendMessage(sender, "Usage: /nf spawn create <name> [x y z] [targetPlayer]", "§c");
            nakedAndAfraid.debugLog("[SpawnManager] Invalid arguments for create, expected at least 3");
            return true;
        }

        var spawnName = args[2].toLowerCase();
        nakedAndAfraid.debugLog("[SpawnManager] Spawn name: " + spawnName);
        if (spawns.containsKey(spawnName)) {
            sendMessage(sender, "Spawn '" + spawnName + "' already exists.", "§c");
            nakedAndAfraid.debugLog("[SpawnManager] Spawn '" + spawnName + "' already exists for " + sender.getName());
            return true;
        }
        var maxSpawns = plugin.getConfig().getInt("max-spawns", 10);
        if (spawns.size() >= maxSpawns) {
            sendMessage(sender, "You have reached the max number of spawns.", "§c");
            nakedAndAfraid.debugLog("[SpawnManager] Max spawns reached (" + spawns.size() + "/" + maxSpawns + ") for " + sender.getName());
            return true;
        }

        Location loc = null;
        String targetPlayerName = null;

        var remaining = args.length - 3;

        var hasCoords = false;
        if (remaining >= 3) {
            hasCoords = isDouble(args[3]) && isDouble(args[4]) && isDouble(args[5]);
            nakedAndAfraid.debugLog("[SpawnManager] Checking coordinates: hasCoords=" + hasCoords);
        }

        if (hasCoords) {
            try {
                var x = Double.parseDouble(args[3]);
                var y = Double.parseDouble(args[4]);
                var z = Double.parseDouble(args[5]);

                World world;
                if (sender instanceof Player p) {
                    world = p.getWorld();
                } else {
                    world = Bukkit.getWorlds().get(0);
                    nakedAndAfraid.debugLog("[SpawnManager] Using default world for console: " + world.getName());
                }
                loc = new Location(world, x, y, z);
                nakedAndAfraid.debugLog("[SpawnManager] Parsed coordinates for spawn: " + formatLocation(loc));

                if (args.length >= 7) {
                    targetPlayerName = args[6];
                    nakedAndAfraid.debugLog("[SpawnManager] Specified target player: " + targetPlayerName);
                }

            } catch (NumberFormatException e) {
                sendMessage(sender, "Invalid coordinates.", "§c");
                nakedAndAfraid.debugLog("[SpawnManager] Invalid coordinates for " + sender.getName() + ": " + String.join(" ", args));
                return true;
            }
        } else {
            if (args.length >= 4) {
                targetPlayerName = args[3];
                nakedAndAfraid.debugLog("[SpawnManager] Specified target player: " + targetPlayerName);
            }

            if (sender instanceof Player p) {
                loc = p.getLocation();
                nakedAndAfraid.debugLog("[SpawnManager] Using sender's location: " + formatLocation(loc));
            } else {
                sendMessage(sender, "Coordinates or player must be specified when run from console.", "§c");
                nakedAndAfraid.debugLog("[SpawnManager] No coordinates or player specified for console sender " + sender.getName());
                return true;
            }
        }

        if (targetPlayerName == null) {
            if (sender instanceof Player p) {
                targetPlayerName = p.getName();
                nakedAndAfraid.debugLog("[SpawnManager] Defaulting target player to sender: " + targetPlayerName);
            } else {
                targetPlayerName = spawnName;
                nakedAndAfraid.debugLog("[SpawnManager] Defaulting target player to spawn name: " + targetPlayerName);
            }
        }

        if (!Bukkit.getOfflinePlayer(targetPlayerName).hasPlayedBefore() && Bukkit.getPlayerExact(targetPlayerName) == null) {
            sendMessage(sender, "Warning: Player '" + targetPlayerName + "' has never joined the server before (still saved).", "§e");
            nakedAndAfraid.debugLog("[SpawnManager] Warning: Target player '" + targetPlayerName + "' has never joined");
        }

        spawns.put(spawnName, new SpawnData(loc, targetPlayerName));
        saveSpawns();
        sendMessage(sender, "Spawn '" + spawnName + "' created at " +
                loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() +
                " for player " + targetPlayerName, "§a");
        nakedAndAfraid.debugLog("[SpawnManager] Created spawn '" + spawnName + "' at " + formatLocation(loc) +
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
        nakedAndAfraid.debugLog("[SpawnManager] Handling rename command for " + sender.getName() + ": " + String.join(" ", args));
        if (args.length < 4) {
            sendMessage(sender, "Usage: /nf spawn rename <oldName> <newName>", "§c");
            nakedAndAfraid.debugLog("[SpawnManager] Invalid arguments for rename, expected 4");
            return true;
        }

        var oldName = args[2].toLowerCase();
        var newName = args[3].toLowerCase();
        nakedAndAfraid.debugLog("[SpawnManager] Renaming spawn from '" + oldName + "' to '" + newName + "'");

        if (!spawns.containsKey(oldName)) {
            sendMessage(sender, "Spawn '" + oldName + "' does not exist.", "§c");
            nakedAndAfraid.debugLog("[SpawnManager] Spawn '" + oldName + "' does not exist for " + sender.getName());
            return true;
        }
        if (spawns.containsKey(newName)) {
            sendMessage(sender, "Spawn '" + newName + "' already exists.", "§c");
            nakedAndAfraid.debugLog("[SpawnManager] Spawn '" + newName + "' already exists for " + sender.getName());
            return true;
        }

        var data = spawns.remove(oldName);
        spawns.put(newName, data);
        saveSpawns();

        sendMessage(sender, "Spawn '" + oldName + "' renamed to '" + newName + "'.", "§a");
        nakedAndAfraid.debugLog("[SpawnManager] Renamed spawn '" + oldName + "' to '" + newName + "' for " + sender.getName());
        return true;
    }

    private boolean handleRemove(CommandSender sender, String[] args) {
        nakedAndAfraid.debugLog("[SpawnManager] Handling remove command for " + sender.getName() + ": " + String.join(" ", args));
        if (args.length < 3) {
            sendMessage(sender, "Usage: /nf spawn remove <name>", "§c");
            nakedAndAfraid.debugLog("[SpawnManager] Invalid arguments for remove, expected 3");
            return true;
        }

        var name = args[2].toLowerCase();
        nakedAndAfraid.debugLog("[SpawnManager] Removing spawn '" + name + "'");
        if (!spawns.containsKey(name)) {
            sendMessage(sender, "Spawn '" + name + "' does not exist.", "§c");
            nakedAndAfraid.debugLog("[SpawnManager] Spawn '" + name + "' does not exist for " + sender.getName());
            return true;
        }

        spawns.remove(name);
        spawnsConfig.set("spawns." + name, null);
        try {
            spawnsConfig.save(spawnsFile);
            nakedAndAfraid.debugLog("[SpawnManager] Successfully removed spawn '" + name + "' and saved spawns.yml");
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save spawns.yml");
            nakedAndAfraid.debugLog("[SpawnManager] Failed to save spawns.yml after removing '" + name + "': " + e.getMessage());
            e.printStackTrace();
        }

        sendMessage(sender, "Spawn '" + name + "' removed.", "§a");
        nakedAndAfraid.debugLog("[SpawnManager] Removed spawn '" + name + "' for " + sender.getName());
        return true;
    }

    private boolean handleList(CommandSender sender) {
        nakedAndAfraid.debugLog("[SpawnManager] Handling list command for " + sender.getName());
        if (spawns.isEmpty()) {
            sendMessage(sender, "No spawns defined.", "§e");
            nakedAndAfraid.debugLog("[SpawnManager] No spawns defined for " + sender.getName());
            return true;
        }
        sendMessage(sender, "==== Spawns ====", "§6");
        for (var entry : spawns.entrySet()) {
            var loc = entry.getValue().location();
            sendMessage(sender, entry.getKey() + " - " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() +
                    " (world: " + loc.getWorld().getName() + ")", "§e");
            nakedAndAfraid.debugLog("[SpawnManager] Listed spawn '" + entry.getKey() + "' at " + formatLocation(loc) +
                    " for " + sender.getName());
        }
        return true;
    }

    /**
     * Handles /nf spawn tp <name> [player]
     * Now supports multiple-spawn-priority for target player.
     */
    private boolean handleTp(CommandSender sender, String[] args) {
        nakedAndAfraid.debugLog("[SpawnManager] Handling tp command for " + sender.getName() + ": " + String.join(" ", args));
        if (args.length < 3) {
            sendMessage(sender, "Usage: /nf spawn tp <name> [player]", "§c");
            nakedAndAfraid.debugLog("[SpawnManager] Invalid arguments for tp, expected at least 3");
            return true;
        }

        var spawnName = args[2].toLowerCase();
        nakedAndAfraid.debugLog("[SpawnManager] Spawn name: " + spawnName);
        Player targetPlayer = null;

        if (args.length >= 4) {
            targetPlayer = Bukkit.getPlayerExact(args[3]);
            if (targetPlayer == null) {
                sendMessage(sender, "Player '" + args[3] + "' is not online.", "§c");
                nakedAndAfraid.debugLog("[SpawnManager] Player '" + args[3] + "' not online for " + sender.getName());
                return true;
            }
            nakedAndAfraid.debugLog("[SpawnManager] Target player: " + targetPlayer.getName());
        } else {
            if (sender instanceof Player p) {
                targetPlayer = p;
                nakedAndAfraid.debugLog("[SpawnManager] Defaulting target player to sender: " + targetPlayer.getName());
            } else {
                sendMessage(sender, "You must specify a player when using this command from console.", "§c");
                nakedAndAfraid.debugLog("[SpawnManager] No player specified for console sender " + sender.getName());
                return true;
            }
        }

        var matchingSpawns = new ArrayList<SpawnData>();
        for (var entry : spawns.entrySet()) {
            if (entry.getValue().targetPlayerName().equalsIgnoreCase(targetPlayer.getName())) {
                matchingSpawns.add(entry.getValue());
                nakedAndAfraid.debugLog("[SpawnManager] Found matching spawn '" + entry.getKey() + "' for " + targetPlayer.getName());
            }
        }

        if (spawns.containsKey(spawnName)) {
            var spawn = spawns.get(spawnName);
            nakedAndAfraid.getTeleportHelper().startCountdownTeleport(targetPlayer, spawn.location());
            sendMessage(sender, "Teleporting player " + targetPlayer.getName() + " to spawn '" + spawnName + "'...", "§a");
            nakedAndAfraid.debugLog("[SpawnManager] Teleporting " + targetPlayer.getName() + " to spawn '" + spawnName +
                    "' at " + formatLocation(spawn.location()));
            return true;
        }

        if (!matchingSpawns.isEmpty()) {
            var priority = nakedAndAfraid.getMultipleSpawnPriority();
            nakedAndAfraid.debugLog("[SpawnManager] Using multiple-spawn-priority: " + priority);
            var chosen = switch (priority) {
                case "FIRST" -> matchingSpawns.get(0);
                case "LAST" -> matchingSpawns.get(matchingSpawns.size() - 1);
                case "RANDOM" -> matchingSpawns.get(new Random().nextInt(matchingSpawns.size()));
                default -> matchingSpawns.get(0);
            };
            nakedAndAfraid.getTeleportHelper().startCountdownTeleport(targetPlayer, chosen.location());
            sendMessage(sender, "Teleporting player " + targetPlayer.getName() + " to their spawn (" + priority + ")...", "§a");
            nakedAndAfraid.debugLog("[SpawnManager] Teleporting " + targetPlayer.getName() + " to their spawn (" + priority +
                    ") at " + formatLocation(chosen.location()));
            return true;
        }

        sendMessage(sender, "No spawn found for player " + targetPlayer.getName() + ".", "§c");
        nakedAndAfraid.debugLog("[SpawnManager] No spawn found for " + targetPlayer.getName());
        return true;
    }

    private boolean handleTpAll(CommandSender sender) {
        nakedAndAfraid.debugLog("[SpawnManager] Handling tpall command for " + sender.getName());
        if (spawns.isEmpty()) {
            sendMessage(sender, "No spawns defined.", "§e");
            nakedAndAfraid.debugLog("[SpawnManager] No spawns defined for " + sender.getName());
            return true;
        }

        for (var entry : spawns.entrySet()) {
            var spawn = entry.getValue();
            var target = Bukkit.getPlayerExact(spawn.targetPlayerName());
            if (target == null) {
                sendMessage(sender, spawn.targetPlayerName() + " is not online!", "§c");
                nakedAndAfraid.debugLog("[SpawnManager] Skipped teleport for offline player " + spawn.targetPlayerName());
                continue;
            }
            nakedAndAfraid.getTeleportHelper().startCountdownTeleport(target, spawn.location());
            sendMessage(sender, "Teleporting " + target.getName() + " to their spawn...", "§a");
            nakedAndAfraid.debugLog("[SpawnManager] Teleporting " + target.getName() + " to spawn '" + entry.getKey() +
                    "' at " + formatLocation(spawn.location()));
        }
        return true;
    }

    public Map<String, SpawnData> getSpawns() {
        nakedAndAfraid.debugLog("[SpawnManager] Retrieved spawns map with " + spawns.size() + " entries");
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