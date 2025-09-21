package com.crimsonwarpedcraft.nakedandafraid.v1_17.team;

import com.crimsonwarpedcraft.nakedandafraid.v1_17.util.MaterialCompat;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class TeamsManager {
    private final Plugin plugin;
    private final File teamsFile;
    private FileConfiguration teamsConfig;
    private int maxTeams;
    private Material teamBlockMaterial;
    private final Map<String, Team> teams = new HashMap<>();

    private static final List<String> VALID_COLORS = List.of(
            "RED", "BLUE", "GREEN", "YELLOW", "AQUA",
            "DARK_PURPLE", "GOLD", "LIGHT_PURPLE", "WHITE"
    );

    public TeamsManager(Plugin plugin) {
        this.plugin = plugin;
        this.teamsFile = new File(plugin.getDataFolder(), "teams.yml");
        debugLog("Initialized TeamsManager for Bukkit version " + Bukkit.getBukkitVersion() +
                ", teams file: " + teamsFile.getPath());
        loadConfig();
    }

    private void debugLog(String message) {
        if (plugin.getConfig().getBoolean("debug-mode", false)) {
            plugin.getLogger().info("[TeamsManager] " + message);
        }
    }

    /**
     * Checks if the server is pre-1.16 (Minecraft 1.12–1.15.2).
     */
    private boolean isPre116() {
        return !Bukkit.getBukkitVersion().matches(".*1\\.(1[6-9]|2[0-1]).*");
    }

    public void loadConfig() {
        debugLog("Loading teams.yml configuration");
        if (!teamsFile.exists()) {
            plugin.saveResource("teams.yml", false);
            debugLog("Created new teams.yml");
        }
        teamsConfig = YamlConfiguration.loadConfiguration(teamsFile);
        debugLog("Loaded teams.yml configuration");

        maxTeams = plugin.getConfig().getInt("max-teams", 10);
        debugLog("Loaded max-teams: " + maxTeams);

        String blockName = plugin.getConfig().getString("team-block", "LODESTONE").toUpperCase();
        teamBlockMaterial = MaterialCompat.getMaterial(blockName);
        if (teamBlockMaterial == null) {
            teamBlockMaterial = isPre116() ? Material.OBSIDIAN : MaterialCompat.getMaterial("LODESTONE");
            String defaultMaterial = teamBlockMaterial != null ? teamBlockMaterial.name() : "null";
            debugLog("Invalid team-block '" + blockName + "', defaulting to " + defaultMaterial);
            plugin.getLogger().warning("Invalid team-block material in config.yml, defaulting to " + defaultMaterial);
        } else {
            debugLog("Loaded team-block: " + teamBlockMaterial.name());
        }

        teams.clear();
        debugLog("Cleared existing teams map");

        if (teamsConfig.contains("teams")) {
            for (String teamName : Objects.requireNonNull(teamsConfig.getConfigurationSection("teams")).getKeys(false)) {
                String path = "teams." + teamName;

                String colorName = teamsConfig.getString(path + ".color", "WHITE").toUpperCase();
                String color = VALID_COLORS.contains(colorName) ? colorName : "WHITE";
                if (!colorName.equals(color)) {
                    debugLog("Invalid color '" + colorName + "' for team '" + teamName + "', defaulting to WHITE");
                }

                List<String> memberUUIDs = teamsConfig.getStringList(path + ".members");
                Set<UUID> members = new HashSet<>();
                for (String s : memberUUIDs) {
                    try {
                        members.add(UUID.fromString(s));
                    } catch (IllegalArgumentException ignored) {
                        debugLog("Invalid UUID '" + s + "' for team '" + teamName + "', skipping");
                    }
                }

                Location lodestone = null;
                if (teamsConfig.contains(path + ".lodestone.world")) {
                    World world = Bukkit.getWorld(Objects.requireNonNull(teamsConfig.getString(path + ".lodestone.world")));
                    if (world != null) {
                        double x = teamsConfig.getDouble(path + ".lodestone.x");
                        double y = teamsConfig.getDouble(path + ".lodestone.y");
                        double z = teamsConfig.getDouble(path + ".lodestone.z");
                        lodestone = new Location(world, x, y, z);
                        debugLog("Loaded lodestone for team '" + teamName + "' at " + formatLocation(lodestone));
                    } else {
                        debugLog("Invalid world for lodestone of team '" + teamName + "', skipping");
                    }
                }

                Team team = new Team(teamName, color, members, lodestone);
                teams.put(teamName.toLowerCase(), team);
                debugLog("Loaded team '" + teamName + "' with color " + color + ", " + members.size() + " members");
            }
        } else {
            debugLog("No teams section found in teams.yml");
        }
    }

    public void saveConfig() {
        debugLog("Saving teams to teams.yml");
        teamsConfig.set("max-teams", maxTeams);
        teamsConfig.set("team-block", teamBlockMaterial != null ? teamBlockMaterial.name() : "LODESTONE");

        for (Team team : teams.values()) {
            String path = "teams." + team.getName();

            teamsConfig.set(path + ".color", team.getColor());

            List<String> memberUUIDs = new ArrayList<>();
            for (UUID uuid : team.getMembers()) {
                memberUUIDs.add(uuid.toString());
            }
            teamsConfig.set(path + ".members", memberUUIDs);

            Location lodestone = team.getLodestone();
            if (lodestone != null) {
                teamsConfig.set(path + ".lodestone.world", lodestone.getWorld().getName());
                teamsConfig.set(path + ".lodestone.x", lodestone.getX());
                teamsConfig.set(path + ".lodestone.y", lodestone.getY());
                teamsConfig.set(path + ".lodestone.z", lodestone.getZ());
                debugLog("Saved lodestone for team '" + team.getName() + "' at " + formatLocation(lodestone));
            } else {
                teamsConfig.set(path + ".lodestone", null);
                debugLog("Cleared lodestone for team '" + team.getName() + "'");
            }

            debugLog("Saved team '" + team.getName() + "' with color " + team.getColor() + ", " + memberUUIDs.size() + " members");
        }

        try {
            teamsConfig.save(teamsFile);
            debugLog("Successfully saved teams.yml");
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save teams.yml");
            debugLog("Failed to save teams.yml: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void refreshWorlds() {
        debugLog("Refreshing worlds for teams");
        List<String> toRemove = new ArrayList<>();
        for (Team team : teams.values()) {
            Location lodestone = team.getLodestone();
            if (lodestone != null) {
                String worldName = lodestone.getWorld().getName();
                if (!plugin.getConfig().getBoolean("enabled-worlds." + worldName, true)) {
                    toRemove.add(team.getName().toLowerCase());
                    debugLog("Marking team '" + team.getName() + "' for removal due to disabled world: " + worldName);
                }
            }
        }
        for (String teamName : toRemove) {
            teams.remove(teamName);
            teamsConfig.set("teams." + teamName, null);
            debugLog("Removed team '" + teamName + "' from disabled world");
        }
        if (!toRemove.isEmpty()) {
            try {
                teamsConfig.save(teamsFile);
                debugLog("Saved teams.yml after removing teams from disabled worlds");
            } catch (IOException e) {
                plugin.getLogger().severe("Could not save teams.yml after world refresh");
                debugLog("Failed to save teams.yml after world refresh: " + e.getMessage());
                e.printStackTrace();
            }
        }
        debugLog("World refresh completed, removed " + toRemove.size() + " teams");
    }

    public boolean removeTeam(String teamName) {
        debugLog("Attempting to remove team '" + teamName + "'");
        String teamKey = teamName.toLowerCase();
        if (!teams.containsKey(teamKey)) {
            debugLog("Team '" + teamName + "' does not exist, cannot remove");
            return false;
        }
        teams.remove(teamKey);
        teamsConfig.set("teams." + teamName, null);
        saveConfig();
        debugLog("Removed team '" + teamName + "'");
        return true;
    }

    public int getMaxTeams() {
        debugLog("Retrieved max-teams: " + maxTeams);
        return maxTeams;
    }

    public Material getTeamBlockMaterial() {
        debugLog("Retrieved team-block material: " + (teamBlockMaterial != null ? teamBlockMaterial.name() : "null"));
        return teamBlockMaterial;
    }

    public boolean teamExists(String teamName) {
        boolean exists = teams.containsKey(teamName.toLowerCase());
        debugLog("Checked team existence for '" + teamName + "': " + exists);
        return exists;
    }

    public Team getTeam(String teamName) {
        Team team = teams.get(teamName.toLowerCase());
        debugLog("Retrieved team '" + teamName + "': " + (team != null ? team.getName() : "null"));
        return team;
    }

    public boolean createTeam(String teamName, String color) {
        debugLog("Attempting to create team '" + teamName + "' with color " + color);
        if (teams.size() >= maxTeams) {
            debugLog("Cannot create team '" + teamName + "', max teams reached (" + teams.size() + "/" + maxTeams + ")");
            return false;
        }
        if (teamExists(teamName)) {
            debugLog("Cannot create team '" + teamName + "', already exists");
            return false;
        }
        String validatedColor = VALID_COLORS.contains(color.toUpperCase()) ? color.toUpperCase() : "WHITE";
        if (!color.toUpperCase().equals(validatedColor)) {
            debugLog("Invalid color '" + color + "' for team '" + teamName + "', defaulting to WHITE");
        }
        teams.put(teamName.toLowerCase(), new Team(teamName, validatedColor, new HashSet<>(), null));
        saveConfig();
        debugLog("Created team '" + teamName + "' with color " + validatedColor);
        return true;
    }

    public boolean addMember(String teamName, UUID playerUUID) {
        debugLog("Attempting to add player " + playerUUID + " to team '" + teamName + "'");
        Team team = getTeam(teamName);
        if (team == null) {
            debugLog("Team '" + teamName + "' does not exist, cannot add member");
            return false;
        }
        if (team.getMembers().contains(playerUUID)) {
            debugLog("Player " + playerUUID + " already in team '" + teamName + "'");
            return false;
        }
        team.getMembers().add(playerUUID);
        saveConfig();
        debugLog("Added player " + playerUUID + " to team '" + teamName + "'");
        return true;
    }

    public boolean removeMember(String teamName, UUID playerUUID) {
        debugLog("Attempting to remove player " + playerUUID + " from team '" + teamName + "'");
        Team team = getTeam(teamName);
        if (team == null) {
            debugLog("Team '" + teamName + "' does not exist, cannot remove member");
            return false;
        }
        if (!team.getMembers().contains(playerUUID)) {
            debugLog("Player " + playerUUID + " not in team '" + teamName + "'");
            return false;
        }
        team.getMembers().remove(playerUUID);
        saveConfig();
        debugLog("Removed player " + playerUUID + " from team '" + teamName + "'");
        return true;
    }

    public Set<UUID> getTeamMembers(String teamName) {
        Team team = getTeam(teamName);
        if (team == null) {
            debugLog("Team '" + teamName + "' does not exist, returning empty members set");
            return Collections.emptySet();
        }
        debugLog("Retrieved " + team.getMembers().size() + " members for team '" + teamName + "'");
        return Collections.unmodifiableSet(team.getMembers());
    }

    public Collection<Team> getTeams() {
        debugLog("Retrieved " + teams.size() + " teams");
        return Collections.unmodifiableCollection(teams.values());
    }

    public void setLodestone(String teamName, Location lodestone) {
        debugLog("Setting lodestone for team '" + teamName + "' to " + (lodestone != null ? formatLocation(lodestone) : "null"));
        Team team = getTeam(teamName);
        if (team == null) {
            debugLog("Team '" + teamName + "' does not exist, skipping lodestone set");
            return;
        }
        team.setLodestone(lodestone);
        saveConfig();
    }

    public Location getLodestone(String teamName) {
        Team team = getTeam(teamName);
        if (team == null) {
            debugLog("Team '" + teamName + "' does not exist, returning null lodestone");
            return null;
        }
        Location lodestone = team.getLodestone();
        debugLog("Retrieved lodestone for team '" + teamName + "': " + (lodestone != null ? formatLocation(lodestone) : "null"));
        return lodestone;
    }

    public Optional<Team> getTeamByLodestone(Location location) {
        debugLog("Looking for team with lodestone at " + formatLocation(location));
        for (Team team : teams.values()) {
            Location l = team.getLodestone();
            if (l != null && l.getWorld().equals(location.getWorld())) {
                if (l.getBlockX() == location.getBlockX() &&
                        l.getBlockY() == location.getBlockY() &&
                        l.getBlockZ() == location.getBlockZ()) {
                    debugLog("Found team '" + team.getName() + "' for lodestone at " + formatLocation(location));
                    return Optional.of(team);
                }
            }
        }
        debugLog("No team found for lodestone at " + formatLocation(location));
        return Optional.empty();
    }

    private String formatLocation(Location location) {
        if (location == null) return "null";
        return String.format("(world=%s, x=%.2f, y=%.2f, z=%.2f)",
                location.getWorld() != null ? location.getWorld().getName() : "null",
                location.getX(), location.getY(), location.getZ());
    }

    public class Team {
        private final String name;
        private String color;
        private final Set<UUID> members;
        private Location lodestone;

        public Team(String name, String color, Set<UUID> members, Location lodestone) {
            this.name = name;
            this.color = color;
            this.members = members;
            this.lodestone = lodestone;
            debugLog("Created team '" + name + "' with color " + color +
                    (lodestone != null ? ", lodestone at " + formatLocation(lodestone) : ""));
        }

        public String getName() {
            debugLog("Retrieved name for team '" + name + "'");
            return name;
        }

        public String getColor() {
            debugLog("Retrieved color " + color + " for team '" + name + "'");
            return color;
        }

        public Object getNamedTextColor() {
            debugLog("Adventure API not supported, returning null for NamedTextColor for team '" + name + "'");
            return null;
        }

        public void setColor(String color) {
            debugLog("Setting color for team '" + name + "' to " + color);
            this.color = VALID_COLORS.contains(color.toUpperCase()) ? color.toUpperCase() : "WHITE";
            if (!color.toUpperCase().equals(this.color)) {
                debugLog("Invalid color '" + color + "' for team '" + name + "', defaulting to WHITE");
            }
        }

        public Set<UUID> getMembers() {
            debugLog("Retrieved " + members.size() + " members for team '" + name + "'");
            return members;
        }

        public Location getLodestone() {
            debugLog("Retrieved lodestone for team '" + name + "': " +
                    (lodestone != null ? formatLocation(lodestone) : "null"));
            return lodestone;
        }

        public void setLodestone(Location lodestone) {
            debugLog("Setting lodestone for team '" + name + "' to " +
                    (lodestone != null ? formatLocation(lodestone) : "null"));
            this.lodestone = lodestone;
        }
    }
}