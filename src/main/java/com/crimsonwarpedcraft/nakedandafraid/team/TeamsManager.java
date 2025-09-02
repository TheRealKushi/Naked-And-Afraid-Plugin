package com.crimsonwarpedcraft.nakedandafraid.team;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
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

    public TeamsManager(JavaPlugin plugin) {
        this.plugin = plugin;
        teamsFile = new File(plugin.getDataFolder(), "teams.yml");

        loadConfig();
    }

    public void loadConfig() {
        if (!teamsFile.exists()) {
            plugin.saveResource("teams.yml", false);
        }
        teamsConfig = YamlConfiguration.loadConfiguration(teamsFile);

        maxTeams = plugin.getConfig().getInt("max-teams", 10);
        String blockName = plugin.getConfig().getString("team-block", "LODESTONE").toUpperCase();
        try {
            teamBlockMaterial = Material.valueOf(blockName);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid team-block material in config.yml, defaulting to LODESTONE");
            teamBlockMaterial = Material.LODESTONE;
        }

        teams.clear();

        if (teamsConfig.contains("teams")) {
            for (String teamName : teamsConfig.getConfigurationSection("teams").getKeys(false)) {
                String path = "teams." + teamName;

                String colorName = teamsConfig.getString(path + ".color", "WHITE");
                NamedTextColor color;
                try {
                    color = NamedTextColor.NAMES.value(colorName.toLowerCase());
                    if (color == null) color = NamedTextColor.WHITE;
                } catch (Exception e) {
                    color = NamedTextColor.WHITE;
                }

                List<String> memberUUIDs = teamsConfig.getStringList(path + ".members");
                Set<UUID> members = new HashSet<>();
                for (String s : memberUUIDs) {
                    try {
                        members.add(UUID.fromString(s));
                    } catch (IllegalArgumentException ignored) {
                    }
                }

                Location lodestone = null;
                if (teamsConfig.contains(path + ".lodestone.world")) {
                    World w = Bukkit.getWorld(Objects.requireNonNull(teamsConfig.getString(path + ".lodestone.world")));
                    if (w != null) {
                        double x = teamsConfig.getDouble(path + ".lodestone.x");
                        double y = teamsConfig.getDouble(path + ".lodestone.y");
                        double z = teamsConfig.getDouble(path + ".lodestone.z");
                        lodestone = new Location(w, x, y, z);
                    }
                }

                Team team = new Team(teamName, color, members, lodestone);
                teams.put(teamName.toLowerCase(), team);
            }
        }
    }

    public void saveConfig() {
        teamsConfig.set("max-teams", maxTeams);
        teamsConfig.set("team-block", teamBlockMaterial.name());

        Map<String, Object> teamsSection = new HashMap<>();
        for (Team team : teams.values()) {
            String path = "teams." + team.getName();

            teamsConfig.set(path + ".color", team.getColor().toString().toUpperCase());

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
            } else {
                teamsConfig.set(path + ".lodestone", null);
            }
        }

        try {
            teamsConfig.save(teamsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save teams.yml");
            e.printStackTrace();
        }
    }

    public int getMaxTeams() {
        return maxTeams;
    }

    public Material getTeamBlockMaterial() {
        return teamBlockMaterial;
    }

    public boolean teamExists(String teamName) {
        return teams.containsKey(teamName.toLowerCase());
    }

    public Team getTeam(String teamName) {
        return teams.get(teamName.toLowerCase());
    }

    public boolean createTeam(String teamName, NamedTextColor color) {
        if (teams.size() >= maxTeams) {
            return false;
        }
        if (teamExists(teamName)) return false;
        teams.put(teamName.toLowerCase(), new Team(teamName, color, new HashSet<>(), null));
        saveConfig();
        return true;
    }

    public void removeTeam(String teamName) {
        if (!teamExists(teamName)) return;
        teams.remove(teamName.toLowerCase());
        saveConfig();
    }

    public boolean addMember(String teamName, UUID playerUUID) {
        Team team = getTeam(teamName);
        if (team == null) return false;
        if (team.getMembers().contains(playerUUID)) return false;
        team.getMembers().add(playerUUID);
        saveConfig();
        return true;
    }

    public boolean removeMember(String teamName, UUID playerUUID) {
        Team team = getTeam(teamName);
        if (team == null) return false;
        if (!team.getMembers().contains(playerUUID)) return false;
        team.getMembers().remove(playerUUID);
        saveConfig();
        return true;
    }

    public Set<UUID> getTeamMembers(String teamName) {
        Team team = getTeam(teamName);
        if (team == null) return Collections.emptySet();
        return Collections.unmodifiableSet(team.getMembers());
    }

    public Collection<Team> getTeams() {
        return Collections.unmodifiableCollection(teams.values());
    }

    public void setLodestone(String teamName, Location lodestone) {
        Team team = getTeam(teamName);
        if (team == null) return;
        team.setLodestone(lodestone);
        saveConfig();
    }

    public Location getLodestone(String teamName) {
        Team team = getTeam(teamName);
        if (team == null) return null;
        return team.getLodestone();
    }

    public Optional<Team> getTeamByLodestone(Location location) {
        for (Team t : teams.values()) {
            Location l = t.getLodestone();
            if (l != null && l.getWorld().equals(location.getWorld())) {
                if (l.getBlockX() == location.getBlockX() &&
                        l.getBlockY() == location.getBlockY() &&
                        l.getBlockZ() == location.getBlockZ()) {
                    return Optional.of(t);
                }
            }
        }
        return Optional.empty();
    }

    public static class Team {
        private final String name;
        private NamedTextColor color;
        private final Set<UUID> members;
        private Location lodestone;

        public Team(String name, NamedTextColor color, Set<UUID> members, Location lodestone) {
            this.name = name;
            this.color = color;
            this.members = members;
            this.lodestone = lodestone;
        }

        public String getName() {
            return name;
        }

        public NamedTextColor getColor() {
            return color;
        }

        public void setColor(NamedTextColor color) {
            this.color = color;
        }

        public Set<UUID> getMembers() {
            return members;
        }

        public Location getLodestone() {
            return lodestone;
        }

        public void setLodestone(Location lodestone) {
            this.lodestone = lodestone;
        }
    }
}
