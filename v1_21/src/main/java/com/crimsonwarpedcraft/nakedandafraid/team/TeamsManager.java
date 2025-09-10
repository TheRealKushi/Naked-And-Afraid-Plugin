package com.crimsonwarpedcraft.nakedandafraid.team;

import com.crimsonwarpedcraft.nakedandafraid.NakedAndAfraid;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class TeamsManager {

    private final JavaPlugin plugin;
    private final File teamsFile;
    private FileConfiguration teamsConfig;

    private int maxTeams;
    private Material teamBlockMaterial;

    private final Map<String, Team> teams = new HashMap<>();

    private static final List<String> VALID_COLORS = List.of(
            "RED", "BLUE", "GREEN", "YELLOW", "AQUA",
            "DARK_PURPLE", "GOLD", "LIGHT_PURPLE", "WHITE"
    );

    public TeamsManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.teamsFile = new File(plugin.getDataFolder(), "teams.yml");
        ((NakedAndAfraid) plugin).debugLog("[TeamsManager] Initialized TeamsManager for Bukkit version " + Bukkit.getBukkitVersion() +
                ", teams file: " + teamsFile.getPath());
        loadConfig();
    }

    /**
     * Checks if the server supports the Adventure API (Minecraft 1.19+).
     *
     * @return true if the server version is 1.19 or higher, false otherwise.
     */
    private boolean isAdventureSupported() {
        var version = Bukkit.getBukkitVersion().split("-")[0];
        try {
            var parts = version.split("\\.");
            int major = Integer.parseInt(parts[1]);
            return major >= 19;
        } catch (Exception e) {
            ((NakedAndAfraid) plugin).debugLog("[TeamsManager] Failed to parse Bukkit version: " + version);
            return false;
        }
    }

    /**
     * Checks if the server is pre-1.16 (Minecraft 1.12–1.15.2).
     *
     * @return true if the server version is before 1.16, false otherwise.
     */
    private boolean isPre116() {
        return !Bukkit.getBukkitVersion().matches(".*1\\.(1[6-9]|2[0-1]).*");
    }

    public void loadConfig() {
        ((NakedAndAfraid) plugin).debugLog("[TeamsManager] Loading teams.yml configuration");
        if (!teamsFile.exists()) {
            plugin.saveResource("teams.yml", false);
            ((NakedAndAfraid) plugin).debugLog("[TeamsManager] Created new teams.yml");
        }
        teamsConfig = YamlConfiguration.loadConfiguration(teamsFile);
        ((NakedAndAfraid) plugin).debugLog("[TeamsManager] Loaded teams.yml configuration");

        maxTeams = plugin.getConfig().getInt("max-teams", 10);
        ((NakedAndAfraid) plugin).debugLog("[TeamsManager] Loaded max-teams: " + maxTeams);

        var blockName = plugin.getConfig().getString("team-block", "LODESTONE").toUpperCase();
        try {
            if (isPre116() && blockName.equals("LODESTONE")) {
                teamBlockMaterial = Material.OBSIDIAN;
                ((NakedAndAfraid) plugin).debugLog("[TeamsManager] Pre-1.16 detected, defaulting team-block to OBSIDIAN (LODESTONE unavailable)");
            } else {
                teamBlockMaterial = Material.valueOf(blockName);
                ((NakedAndAfraid) plugin).debugLog("[TeamsManager] Loaded team-block: " + teamBlockMaterial.name());
            }
        } catch (IllegalArgumentException e) {
            teamBlockMaterial = isPre116() ? Material.OBSIDIAN : Material.LODESTONE;
            ((NakedAndAfraid) plugin).debugLog("[TeamsManager] Invalid team-block '" + blockName + "', defaulting to " + teamBlockMaterial.name());
            plugin.getLogger().warning("Invalid team-block material in config.yml, defaulting to " + teamBlockMaterial.name());
        }

        teams.clear();
        ((NakedAndAfraid) plugin).debugLog("[TeamsManager] Cleared existing teams map");

        if (teamsConfig.contains("teams")) {
            for (var teamName : Objects.requireNonNull(teamsConfig.getConfigurationSection("teams")).getKeys(false)) {
                var path = "teams." + teamName;

                var colorName = teamsConfig.getString(path + ".color", "WHITE").toUpperCase();
                var color = VALID_COLORS.contains(colorName) ? colorName : "WHITE";
                if (!colorName.equals(color)) {
                    ((NakedAndAfraid) plugin).debugLog("[TeamsManager] Invalid color '" + colorName + "' for team '" + teamName + "', defaulting to WHITE");
                }

                var memberUUIDs = teamsConfig.getStringList(path + ".members");
                var members = new HashSet<UUID>();
                for (var s : memberUUIDs) {
                    try {
                        members.add(UUID.fromString(s));
                    } catch (IllegalArgumentException ignored) {
                        ((NakedAndAfraid) plugin).debugLog("[TeamsManager] Invalid UUID '" + s + "' for team '" + teamName + "', skipping");
                    }
                }

                Location lodestone = null;
                if (teamsConfig.contains(path + ".lodestone.world")) {
                    var w = Bukkit.getWorld(Objects.requireNonNull(teamsConfig.getString(path + ".lodestone.world")));
                    if (w != null) {
                        double x = teamsConfig.getDouble(path + ".lodestone.x");
                        double y = teamsConfig.getDouble(path + ".lodestone.y");
                        double z = teamsConfig.getDouble(path + ".lodestone.z");
                        lodestone = new Location(w, x, y, z);
                        ((NakedAndAfraid) plugin).debugLog("[TeamsManager] Loaded lodestone for team '" + teamName + "' at " + formatLocation(lodestone));
                    } else {
                        ((NakedAndAfraid) plugin).debugLog("[TeamsManager] Invalid world for lodestone of team '" + teamName + "', skipping");
                    }
                }

                var team = new Team(teamName, color, members, lodestone);
                teams.put(teamName.toLowerCase(), team);
                ((NakedAndAfraid) plugin).debugLog("[TeamsManager] Loaded team '" + teamName + "' with color " + color + ", " + members.size() + " members");
            }
        } else {
            ((NakedAndAfraid) plugin).debugLog("[TeamsManager] No teams section found in teams.yml");
        }
    }

    public void saveConfig() {
        ((NakedAndAfraid) plugin).debugLog("[TeamsManager] Saving teams to teams.yml");
        teamsConfig.set("max-teams", maxTeams);
        teamsConfig.set("team-block", teamBlockMaterial.name());

        for (var team : teams.values()) {
            var path = "teams." + team.getName();

            teamsConfig.set(path + ".color", team.getColor());

            var memberUUIDs = new ArrayList<String>();
            for (var uuid : team.getMembers()) {
                memberUUIDs.add(uuid.toString());
            }
            teamsConfig.set(path + ".members", memberUUIDs);

            var lodestone = team.getLodestone();
            if (lodestone != null) {
                teamsConfig.set(path + ".lodestone.world", lodestone.getWorld().getName());
                teamsConfig.set(path + ".lodestone.x", lodestone.getX());
                teamsConfig.set(path + ".lodestone.y", lodestone.getY());
                teamsConfig.set(path + ".lodestone.z", lodestone.getZ());
                ((NakedAndAfraid) plugin).debugLog("[TeamsManager] Saved lodestone for team '" + team.getName() + "' at " + formatLocation(lodestone));
            } else {
                teamsConfig.set(path + ".lodestone", null);
                ((NakedAndAfraid) plugin).debugLog("[TeamsManager] Cleared lodestone for team '" + team.getName() + "'");
            }

            ((NakedAndAfraid) plugin).debugLog("[TeamsManager] Saved team '" + team.getName() + "' with color " + team.getColor() + ", " + memberUUIDs.size() + " members");
        }

        try {
            teamsConfig.save(teamsFile);
            ((NakedAndAfraid) plugin).debugLog("[TeamsManager] Successfully saved teams.yml");
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save teams.yml");
            ((NakedAndAfraid) plugin).debugLog("[TeamsManager] Failed to save teams.yml: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void refreshWorlds() {
        ((NakedAndAfraid) plugin).debugLog("[TeamsManager] Refreshing worlds for teams");
        var toRemove = new ArrayList<String>();
        for (var team : teams.values()) {
            var lodestone = team.getLodestone();
            if (lodestone != null) {
                var worldName = lodestone.getWorld().getName();
                if (!((NakedAndAfraid) plugin).isWorldEnabled(worldName)) {
                    toRemove.add(team.getName().toLowerCase());
                    ((NakedAndAfraid) plugin).debugLog("[TeamsManager] Marking team '" + team.getName() + "' for removal due to disabled world: " + worldName);
                }
            }
        }
        for (var teamName : toRemove) {
            teams.remove(teamName);
            teamsConfig.set("teams." + teamName, null);
            ((NakedAndAfraid) plugin).debugLog("[TeamsManager] Removed team '" + teamName + "' from disabled world");
        }
        if (!toRemove.isEmpty()) {
            try {
                teamsConfig.save(teamsFile);
                ((NakedAndAfraid) plugin).debugLog("[TeamsManager] Saved teams.yml after removing teams from disabled worlds");
            } catch (IOException e) {
                plugin.getLogger().severe("Could not save teams.yml after world refresh");
                ((NakedAndAfraid) plugin).debugLog("[TeamsManager] Failed to save teams.yml after world refresh: " + e.getMessage());
                e.printStackTrace();
            }
        }
        ((NakedAndAfraid) plugin).debugLog("[TeamsManager] World refresh completed, removed " + toRemove.size() + " teams");
    }

    public boolean removeTeam(String teamName) {
        ((NakedAndAfraid) plugin).debugLog("[TeamsManager] Attempting to remove team '" + teamName + "'");
        var teamKey = teamName.toLowerCase();
        if (!teams.containsKey(teamKey)) {
            ((NakedAndAfraid) plugin).debugLog("[TeamsManager] Team '" + teamName + "' does not exist, cannot remove");
            return false;
        }
        teams.remove(teamKey);
        teamsConfig.set("teams." + teamName, null);
        saveConfig();
        ((NakedAndAfraid) plugin).debugLog("[TeamsManager] Removed team '" + teamName + "'");
        return true;
    }

    public int getMaxTeams() {
        ((NakedAndAfraid) plugin).debugLog("[TeamsManager] Retrieved max-teams: " + maxTeams);
        return maxTeams;
    }

    public Material getTeamBlockMaterial() {
        ((NakedAndAfraid) plugin).debugLog("[TeamsManager] Retrieved team-block material: " + teamBlockMaterial.name());
        return teamBlockMaterial;
    }

    public boolean teamExists(String teamName) {
        var exists = teams.containsKey(teamName.toLowerCase());
        ((NakedAndAfraid) plugin).debugLog("[TeamsManager] Checked team existence for '" + teamName + "': " + exists);
        return exists;
    }

    public Team getTeam(String teamName) {
        var team = teams.get(teamName.toLowerCase());
        ((NakedAndAfraid) plugin).debugLog("[TeamsManager] Retrieved team '" + teamName + "': " + (team != null ? team.getName() : "null"));
        return team;
    }

    public boolean createTeam(String teamName, String color) {
        ((NakedAndAfraid) plugin).debugLog("[TeamsManager] Attempting to create team '" + teamName + "' with color " + color);
        if (teams.size() >= maxTeams) {
            ((NakedAndAfraid) plugin).debugLog("[TeamsManager] Cannot create team '" + teamName + "', max teams reached (" + teams.size() + "/" + maxTeams + ")");
            return false;
        }
        if (teamExists(teamName)) {
            ((NakedAndAfraid) plugin).debugLog("[TeamsManager] Cannot create team '" + teamName + "', already exists");
            return false;
        }
        var validatedColor = VALID_COLORS.contains(color.toUpperCase()) ? color.toUpperCase() : "WHITE";
        if (!color.toUpperCase().equals(validatedColor)) {
            ((NakedAndAfraid) plugin).debugLog("[TeamsManager] Invalid color '" + color + "' for team '" + teamName + "', defaulting to WHITE");
        }
        teams.put(teamName.toLowerCase(), new Team(teamName, validatedColor, new HashSet<>(), null));
        saveConfig();
        ((NakedAndAfraid) plugin).debugLog("[TeamsManager] Created team '" + teamName + "' with color " + validatedColor);
        return true;
    }

    public boolean addMember(String teamName, UUID playerUUID) {
        ((NakedAndAfraid) plugin).debugLog("[TeamsManager] Attempting to add player " + playerUUID + " to team '" + teamName + "'");
        var team = getTeam(teamName);
        if (team == null) {
            ((NakedAndAfraid) plugin).debugLog("[TeamsManager] Team '" + teamName + "' does not exist, cannot add member");
            return false;
        }
        if (team.getMembers().contains(playerUUID)) {
            ((NakedAndAfraid) plugin).debugLog("[TeamsManager] Player " + playerUUID + " already in team '" + teamName + "'");
            return false;
        }
        team.getMembers().add(playerUUID);
        saveConfig();
        ((NakedAndAfraid) plugin).debugLog("[TeamsManager] Added player " + playerUUID + " to team '" + teamName + "'");
        return true;
    }

    public boolean removeMember(String teamName, UUID playerUUID) {
        ((NakedAndAfraid) plugin).debugLog("[TeamsManager] Attempting to remove player " + playerUUID + " from team '" + teamName + "'");
        var team = getTeam(teamName);
        if (team == null) {
            ((NakedAndAfraid) plugin).debugLog("[TeamsManager] Team '" + teamName + "' does not exist, cannot remove member");
            return false;
        }
        if (!team.getMembers().contains(playerUUID)) {
            ((NakedAndAfraid) plugin).debugLog("[TeamsManager] Player " + playerUUID + " not in team '" + teamName + "'");
            return false;
        }
        team.getMembers().remove(playerUUID);
        saveConfig();
        ((NakedAndAfraid) plugin).debugLog("[TeamsManager] Removed player " + playerUUID + " from team '" + teamName + "'");
        return true;
    }

    public Set<UUID> getTeamMembers(String teamName) {
        var team = getTeam(teamName);
        if (team == null) {
            ((NakedAndAfraid) plugin).debugLog("[TeamsManager] Team '" + teamName + "' does not exist, returning empty members set");
            return Collections.emptySet();
        }
        ((NakedAndAfraid) plugin).debugLog("[TeamsManager] Retrieved " + team.getMembers().size() + " members for team '" + teamName + "'");
        return Collections.unmodifiableSet(team.getMembers());
    }

    public Collection<Team> getTeams() {
        ((NakedAndAfraid) plugin).debugLog("[TeamsManager] Retrieved " + teams.size() + " teams");
        return Collections.unmodifiableCollection(teams.values());
    }

    public void setLodestone(String teamName, Location lodestone) {
        ((NakedAndAfraid) plugin).debugLog("[TeamsManager] Setting lodestone for team '" + teamName + "' to " + (lodestone != null ? formatLocation(lodestone) : "null"));
        var team = getTeam(teamName);
        if (team == null) {
            ((NakedAndAfraid) plugin).debugLog("[TeamsManager] Team '" + teamName + "' does not exist, skipping lodestone set");
            return;
        }
        team.setLodestone(lodestone);
        saveConfig();
    }

    public Location getLodestone(String teamName) {
        var team = getTeam(teamName);
        if (team == null) {
            ((NakedAndAfraid) plugin).debugLog("[TeamsManager] Team '" + teamName + "' does not exist, returning null lodestone");
            return null;
        }
        var lodestone = team.getLodestone();
        ((NakedAndAfraid) plugin).debugLog("[TeamsManager] Retrieved lodestone for team '" + teamName + "': " + (lodestone != null ? formatLocation(lodestone) : "null"));
        return lodestone;
    }

    public Optional<Team> getTeamByLodestone(Location location) {
        ((NakedAndAfraid) plugin).debugLog("[TeamsManager] Looking for team with lodestone at " + formatLocation(location));
        for (var t : teams.values()) {
            var l = t.getLodestone();
            if (l != null && l.getWorld().equals(location.getWorld())) {
                if (l.getBlockX() == location.getBlockX() &&
                        l.getBlockY() == location.getBlockY() &&
                        l.getBlockZ() == location.getBlockZ()) {
                    ((NakedAndAfraid) plugin).debugLog("[TeamsManager] Found team '" + t.getName() + "' for lodestone at " + formatLocation(location));
                    return Optional.of(t);
                }
            }
        }
        ((NakedAndAfraid) plugin).debugLog("[TeamsManager] No team found for lodestone at " + formatLocation(location));
        return Optional.empty();
    }

    /**
     * Helper method to format a Location for debug logging.
     */
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
            ((NakedAndAfraid) getOuterInstance().plugin).debugLog("[TeamsManager] Created team '" + name + "' with color " + color +
                    (lodestone != null ? ", lodestone at " + getOuterInstance().formatLocation(lodestone) : ""));
        }

        private TeamsManager getOuterInstance() {
            return TeamsManager.this;
        }

        public String getName() {
            ((NakedAndAfraid) getOuterInstance().plugin).debugLog("[TeamsManager] Retrieved name for team '" + name + "'");
            return name;
        }

        public String getColor() {
            ((NakedAndAfraid) getOuterInstance().plugin).debugLog("[TeamsManager] Retrieved color " + color + " for team '" + name + "'");
            return color;
        }

        public Object getNamedTextColor() {
            ((NakedAndAfraid) getOuterInstance().plugin).debugLog("[TeamsManager] Adventure API not supported, returning null for NamedTextColor for team '" + name + "'");
            return null;
        }

        public void setColor(String color) {
            ((NakedAndAfraid) getOuterInstance().plugin).debugLog("[TeamsManager] Setting color for team '" + name + "' to " + color);
            this.color = VALID_COLORS.contains(color.toUpperCase()) ? color.toUpperCase() : "WHITE";
            if (!color.toUpperCase().equals(this.color)) {
                ((NakedAndAfraid) getOuterInstance().plugin).debugLog("[TeamsManager] Invalid color '" + color + "' for team '" + name + "', defaulting to WHITE");
            }
        }

        public Set<UUID> getMembers() {
            ((NakedAndAfraid) getOuterInstance().plugin).debugLog("[TeamsManager] Retrieved " + members.size() + " members for team '" + name + "'");
            return members;
        }

        public Location getLodestone() {
            ((NakedAndAfraid) getOuterInstance().plugin).debugLog("[TeamsManager] Retrieved lodestone for team '" + name + "': " +
                    (lodestone != null ? getOuterInstance().formatLocation(lodestone) : "null"));
            return lodestone;
        }

        public void setLodestone(Location lodestone) {
            ((NakedAndAfraid) getOuterInstance().plugin).debugLog("[TeamsManager] Setting lodestone for team '" + name + "' to " +
                    (lodestone != null ? getOuterInstance().formatLocation(lodestone) : "null"));
            this.lodestone = lodestone;
        }
    }
}