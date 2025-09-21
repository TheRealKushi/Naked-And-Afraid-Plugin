// V1_17/TabListClearer

package com.crimsonwarpedcraft.nakedandafraid.v1_17.listeners;

import com.crimsonwarpedcraft.nakedandafraid.v1_17.NakedAndAfraid;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public class TabListClearer {

    private final NakedAndAfraid plugin;
    private Scoreboard tabListScoreboard;
    private Team hiddenTeam;
    private boolean enabled;

    public TabListClearer(NakedAndAfraid plugin) {
        this.plugin = plugin;
        plugin.debugLog("[TabListClearer] Initialized TabListClearer for Bukkit version " + Bukkit.getBukkitVersion());
    }

    /**
     * Enables tab list hiding by creating a scoreboard team with hidden nametags.
     */
    public void enable() {
        if (enabled) {
            plugin.debugLog("[TabListClearer] Tab list hiding already enabled");
            return;
        }

        var scoreboardManager = Bukkit.getScoreboardManager();
        if (scoreboardManager == null) {
            plugin.debugLog("[TabListClearer] Cannot enable tab list hiding: ScoreboardManager not available");
            return;
        }

        tabListScoreboard = scoreboardManager.getNewScoreboard();
        hiddenTeam = tabListScoreboard.registerNewTeam("TabListHidden");
        hiddenTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);

        for (var player : Bukkit.getOnlinePlayers()) {
            if (plugin.isWorldEnabled(player.getWorld().getName())) {
                hiddenTeam.addEntry(player.getName());
                player.setScoreboard(tabListScoreboard);
                plugin.debugLog("[TabListClearer] Added " + player.getName() +
                        " to hidden tab list team in world " + player.getWorld().getName());
            } else {
                plugin.debugLog("[TabListClearer] Skipped adding " + player.getName() +
                        " to hidden tab list team in disabled world " + player.getWorld().getName());
            }
        }

        enabled = true;
        plugin.debugLog("[TabListClearer] Enabled tab list hiding");
    }

    /**
     * Applies tab list hiding to a specific player.
     */
    public void applyToPlayer(Player player) {
        if (!enabled) {
            plugin.debugLog("[TabListClearer] Tab list hiding not enabled, skipping applyToPlayer for " +
                    player.getName());
            return;
        }
        if (tabListScoreboard == null || hiddenTeam == null) {
            plugin.debugLog("[TabListClearer] Cannot apply tab list hiding to " + player.getName() +
                    ": Scoreboard or team not initialized");
            return;
        }
        if (!plugin.isWorldEnabled(player.getWorld().getName())) {
            plugin.debugLog("[TabListClearer] Skipped applying tab list hiding for player " + player.getName() +
                    " in disabled world " + player.getWorld().getName());
            return;
        }

        hiddenTeam.addEntry(player.getName());
        player.setScoreboard(tabListScoreboard);
        plugin.debugLog("[TabListClearer] Applied tab list hiding to " + player.getName() +
                " in world " + player.getWorld().getName());
    }

    /**
     * Disables tab list hiding by removing players from the hidden team and resetting scoreboards.
     */
    public void disable() {
        if (!enabled) {
            plugin.debugLog("[TabListClearer] Tab list hiding already disabled");
            return;
        }
        if (tabListScoreboard == null || hiddenTeam == null) {
            plugin.debugLog("[TabListClearer] Cannot disable tab list hiding: Scoreboard or team not initialized");
            return;
        }

        for (var player : Bukkit.getOnlinePlayers()) {
            if (hiddenTeam.hasEntry(player.getName())) {
                hiddenTeam.removeEntry(player.getName());
                var mainScoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
                player.setScoreboard(mainScoreboard);
                plugin.debugLog("[TabListClearer] Removed " + player.getName() +
                        " from hidden tab list team and reset scoreboard");
            }
        }

        hiddenTeam.unregister();
        tabListScoreboard = null;
        hiddenTeam = null;
        enabled = false;
        plugin.debugLog("[TabListClearer] Disabled tab list hiding");
    }

    /**
     * Toggles tab list hiding.
     */
    public void toggle() {
        if (enabled) {
            disable();
            plugin.debugLog("[TabListClearer] Toggled tab list hiding to disabled");
        } else {
            enable();
            plugin.debugLog("[TabListClearer] Toggled tab list hiding to enabled");
        }
    }

    /**
     * Returns current status.
     */
    public boolean isEnabled() {
        return enabled;
    }
}