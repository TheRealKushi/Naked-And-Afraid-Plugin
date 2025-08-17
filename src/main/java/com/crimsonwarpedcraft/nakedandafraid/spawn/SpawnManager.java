package com.crimsonwarpedcraft.nakedandafraid.spawn;

import com.crimsonwarpedcraft.nakedandafraid.NakedAndAfraid;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public class SpawnManager {
    private final NakedAndAfraid plugin;
    private final File spawnsFile;
    private FileConfiguration spawnsConfig;

    private final Map<String, SpawnData> spawns = new HashMap<>();

    public SpawnManager(NakedAndAfraid plugin) {
        this.plugin = plugin;

        // Define the spawns.yml file inside plugin data folder
        spawnsFile = new File(plugin.getDataFolder(), "spawns.yml");
        loadSpawnsFile();
    }

    public void loadSpawns() {
        loadSpawnsFile();
        spawns.clear();

        if (spawnsConfig.isConfigurationSection("spawns")) {
            for (String key : Objects.requireNonNull(spawnsConfig.getConfigurationSection("spawns")).getKeys(false)) {
                double x = spawnsConfig.getDouble("spawns." + key + ".x");
                double y = spawnsConfig.getDouble("spawns." + key + ".y");
                double z = spawnsConfig.getDouble("spawns." + key + ".z");
                String worldName = spawnsConfig.getString("spawns." + key + ".world");
                String target = spawnsConfig.getString("spawns." + key + ".targetPlayer", key);

                World world = Bukkit.getWorld(worldName);
                if (world != null) {
                    Location loc = new Location(world, x, y, z);
                    spawns.put(key.toLowerCase(), new SpawnData(loc, target));
                }
            }
        }
    }

    public void saveSpawns() {
        // Clear existing spawn section to avoid stale data
        spawnsConfig.set("spawns", null);

        for (Map.Entry<String, SpawnData> entry : spawns.entrySet()) {
            String key = entry.getKey();
            SpawnData data = entry.getValue();
            Location loc = data.getLocation();

            spawnsConfig.set("spawns." + key + ".x", loc.getX());
            spawnsConfig.set("spawns." + key + ".y", loc.getY());
            spawnsConfig.set("spawns." + key + ".z", loc.getZ());
            spawnsConfig.set("spawns." + key + ".world", loc.getWorld().getName());
            spawnsConfig.set("spawns." + key + ".targetPlayer", data.getTargetPlayerName());
        }

        try {
            spawnsConfig.save(spawnsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save spawns.yml");
            e.printStackTrace();
        }
    }

    private void loadSpawnsFile() {
        if (!spawnsFile.exists()) {
            plugin.saveResource("spawns.yml", false);
            // Or create empty file if you don't ship one:
            if (!spawnsFile.exists()) {
                try {
                    spawnsFile.createNewFile();
                } catch (IOException e) {
                    plugin.getLogger().severe("Could not create spawns.yml");
                    e.printStackTrace();
                }
            }
        }
        spawnsConfig = YamlConfiguration.loadConfiguration(spawnsFile);
    }

    public boolean handleCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /nf spawn <create|rename|remove|list|tp|tpall> ...");
            return true;
        }

        String sub = args[1].toLowerCase();

        return switch (sub) {
            case "create" -> handleCreate(sender, args);
            case "rename" -> handleRename(sender, args);
            case "remove" -> handleRemove(sender, args);
            case "list" -> handleList(sender);
            case "tp" -> handleTp(sender, args);
            case "tpall" -> handleTpAll(sender);
            default -> {
                sender.sendMessage("§cUnknown spawn subcommand.");
                yield true;
            }
        };
    }

    private boolean handleCreate(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /nf spawn create <name> [x y z] [targetPlayer]");
            return true;
        }

        String spawnName = args[2].toLowerCase();
        if (spawns.containsKey(spawnName)) {
            sender.sendMessage("§cSpawn '" + spawnName + "' already exists.");
            return true;
        }
        if (spawns.size() >= plugin.getConfig().getInt("max-spawns", 10)) {
            sender.sendMessage("§cYou have reached the max number of spawns.");
            return true;
        }

        Location loc = null;
        String targetPlayerName = null;

        // Remaining args start at index 3
        int remaining = args.length - 3;

        // Helper to test if a string is a double
        boolean hasCoords = false;
        if (remaining >= 3) {
            hasCoords = isDouble(args[3]) && isDouble(args[4]) && isDouble(args[5]);
        }

        if (hasCoords) {
            // parse coords from args[3..5]
            try {
                double x = Double.parseDouble(args[3]);
                double y = Double.parseDouble(args[4]);
                double z = Double.parseDouble(args[5]);

                World world;
                if (sender instanceof Player p) {
                    world = p.getWorld();
                } else {
                    // default world if console used
                    world = Bukkit.getWorlds().getFirst();
                }
                loc = new Location(world, x, y, z);

                // optional target after coords
                if (args.length >= 7) {
                    targetPlayerName = args[6];
                }

            } catch (NumberFormatException e) {
                sender.sendMessage("§cInvalid coordinates.");
                return true;
            }
        } else {
            // No coords: maybe the user provided only targetPlayer (args[3])
            if (args.length >= 4) {
                targetPlayerName = args[3];
            }

            if (sender instanceof Player p) {
                loc = p.getLocation();
            } else {
                // console must provide coords if no player location available
                sender.sendMessage("§cCoordinates or player must be specified when run from console.");
                return true;
            }
        }

        // If still null, default to command sender (if player) or spawn name
        if (targetPlayerName == null) {
            if (sender instanceof Player p) {
                targetPlayerName = p.getName();
            } else {
                targetPlayerName = spawnName; // fallback
            }
        }

        // Optional: warn if the named player has never joined
        if (!Bukkit.getOfflinePlayer(targetPlayerName).hasPlayedBefore() && Bukkit.getPlayerExact(targetPlayerName) == null) {
            sender.sendMessage("§eWarning: Player '" + targetPlayerName + "' has never joined the server before (still saved).");
        }

        spawns.put(spawnName, new SpawnData(loc, targetPlayerName));
        saveSpawns();

        sender.sendMessage("§aSpawn '" + spawnName + "' created at " +
                loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() +
                " for player " + targetPlayerName);
        return true;
    }

    /* add this helper somewhere in the class (private static or private) */
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
        if (args.length < 4) {
            sender.sendMessage("§cUsage: /nf spawn rename <oldName> <newName>");
            return true;
        }

        String oldName = args[2].toLowerCase();
        String newName = args[3].toLowerCase();

        if (!spawns.containsKey(oldName)) {
            sender.sendMessage("§cSpawn '" + oldName + "' does not exist.");
            return true;
        }
        if (spawns.containsKey(newName)) {
            sender.sendMessage("§cSpawn '" + newName + "' already exists.");
            return true;
        }

        SpawnData data = spawns.remove(oldName);
        spawns.put(newName, data);
        saveSpawns();

        sender.sendMessage("§aSpawn '" + oldName + "' renamed to '" + newName + "'.");
        return true;
    }

    private boolean handleRemove(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /nf spawn remove <name>");
            return true;
        }

        String name = args[2].toLowerCase();
        if (!spawns.containsKey(name)) {
            sender.sendMessage("§cSpawn '" + name + "' does not exist.");
            return true;
        }

        spawns.remove(name);
        spawnsConfig.set("spawns." + name, null);
        try {
            spawnsConfig.save(spawnsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save spawns.yml");
            e.printStackTrace();
        }

        sender.sendMessage("§aSpawn '" + name + "' removed.");
        return true;
    }

    private boolean handleList(CommandSender sender) {
        if (spawns.isEmpty()) {
            sender.sendMessage("§eNo spawns defined.");
            return true;
        }
        sender.sendMessage("§6==== Spawns ====");
        for (Map.Entry<String, SpawnData> entry : spawns.entrySet()) {
            Location loc = entry.getValue().getLocation();
            sender.sendMessage("§e" + entry.getKey() + " §7- " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + " §f(world: " + loc.getWorld().getName() + ")");
        }
        return true;
    }

    /**
     * Handles /nf spawn tp <name> [player]
     * Now supports multiple-spawn-priority for target player.
     */
    private boolean handleTp(CommandSender sender, String[] args) {
        Player targetPlayer = null;
        String spawnName = null;

        if (args.length < 3) {
            sender.sendMessage("§cUsage: /nf spawn tp <name> [player]");
            return true;
        }

        spawnName = args[2].toLowerCase();

        if (args.length >= 4) {
            targetPlayer = Bukkit.getPlayerExact(args[3]);
            if (targetPlayer == null) {
                sender.sendMessage("§cPlayer '" + args[3] + "' is not online.");
                return true;
            }
        } else {
            if (sender instanceof Player p) {
                targetPlayer = p;
            } else {
                sender.sendMessage("§cYou must specify a player when using this command from console.");
                return true;
            }
        }

        // Find all spawns for this target player
        List<SpawnData> matchingSpawns = new ArrayList<>();
        for (Map.Entry<String, SpawnData> entry : spawns.entrySet()) {
            if (entry.getValue().getTargetPlayerName().equalsIgnoreCase(targetPlayer.getName())) {
                matchingSpawns.add(entry.getValue());
            }
        }

        // If user specified spawnName, use that spawn
        if (spawns.containsKey(spawnName)) {
            SpawnData spawn = spawns.get(spawnName);
            plugin.getTeleportHelper().startCountdownTeleport(targetPlayer, spawn.getLocation());
            sender.sendMessage("§aTeleporting player " + targetPlayer.getName() + " to spawn '" + spawnName + "'...");
            return true;
        }

        // If no spawn of that name, but there are multiple spawns for the player, apply priority
        if (!matchingSpawns.isEmpty()) {
            String priority = plugin.getMultipleSpawnPriority();
            SpawnData chosen = null;
            switch (priority) {
                case "FIRST":
                    chosen = matchingSpawns.getFirst();
                    break;
                case "LAST":
                    chosen = matchingSpawns.get(matchingSpawns.size() - 1);
                    break;
                case "RANDOM":
                    chosen = matchingSpawns.get(new Random().nextInt(matchingSpawns.size()));
                    break;
                default:
                    chosen = matchingSpawns.getFirst(); // fallback
            }
            plugin.getTeleportHelper().startCountdownTeleport(targetPlayer, chosen.getLocation());
            sender.sendMessage("§aTeleporting player " + targetPlayer.getName() + " to their spawn (" + priority + ")...");
            return true;
        }

        sender.sendMessage("§cNo spawn found for player " + targetPlayer.getName() + ".");
        return true;
    }

    private boolean handleTpAll(CommandSender sender) {
        if (spawns.isEmpty()) {
            sender.sendMessage("§eNo spawns defined.");
            return true;
        }

        for (SpawnData spawn : spawns.values()) {
            Player target = Bukkit.getPlayerExact(spawn.getTargetPlayerName());
            if (target == null) {
                sender.sendMessage("§c" + spawn.getTargetPlayerName() + " is not online!");
                continue;
            }
            plugin.getTeleportHelper().startCountdownTeleport(target, spawn.getLocation());
            sender.sendMessage("§aTeleporting " + target.getName() + " to their spawn...");
        }
        return true;
    }

    public Map<String, SpawnData> getSpawns() {
        return Collections.unmodifiableMap(spawns);
    }
}