// V1_17/VersionChecker

package com.crimsonwarpedcraft.nakedandafraid.v1_21.util;

import com.crimsonwarpedcraft.nakedandafraid.v1_21.NakedAndAfraid;
import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;

public class VersionChecker {

    private static final String GITHUB_API_LATEST_RELEASE = "https://api.github.com/repos/TheRealKushi/Naked-And-Afraid-Plugin/releases/latest";
    private static final String MODRINTH_API_LATEST_VERSION = "https://api.modrinth.com/v2/project/naked-and-afraid-plugin/version";
    private final NakedAndAfraid plugin;
    private final String githubToken;

    public VersionChecker(NakedAndAfraid plugin) {
        this.plugin = plugin;
        this.githubToken = plugin.getConfig().getString("github-api-token", null);
        plugin.debugLog("[VersionChecker] Initialized VersionChecker" +
                (githubToken != null ? " with GitHub API token" : ""));
    }

    public String getLatestVersion() {
        var version = getLatestVersionFromGitHub();
        if (version != null) {
            return version;
        }
        return getLatestVersionFromModrinth();
    }

    private String getLatestVersionFromGitHub() {
        plugin.debugLog("[VersionChecker] Starting GitHub version check, querying URL: " + GITHUB_API_LATEST_RELEASE);
        try {
            var url = URI.create(GITHUB_API_LATEST_RELEASE).toURL();
            var connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("User-Agent", "NakedAndAfraid-Plugin");
            if (githubToken != null) {
                connection.setRequestProperty("Authorization", "Bearer " + githubToken);
                plugin.debugLog("[VersionChecker] Using GitHub API token for authentication");
            }
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            int responseCode = connection.getResponseCode();
            plugin.debugLog("[VersionChecker] GitHub HTTP response code: " + responseCode);

            if (responseCode != 200) {
                plugin.debugLog("[VersionChecker] Failed to fetch GitHub version, non-200 response: " + responseCode);
                return null;
            }

            var json = JsonParser.parseReader(new InputStreamReader(connection.getInputStream()));
            var version = json.getAsJsonObject().get("tag_name").getAsString();
            plugin.debugLog("[VersionChecker] Successfully parsed GitHub latest version: " + version);
            return version;
        } catch (Exception e) {
            plugin.debugLog("[VersionChecker] Error during GitHub version check: " + e.getMessage());
            return null;
        }
    }

    private String getLatestVersionFromModrinth() {
        plugin.debugLog("[VersionChecker] Starting Modrinth version check, querying URL: " + MODRINTH_API_LATEST_VERSION);
        try {
            var url = URI.create(MODRINTH_API_LATEST_VERSION).toURL();
            var connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "NakedAndAfraid-Plugin");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            int responseCode = connection.getResponseCode();
            plugin.debugLog("[VersionChecker] Modrinth HTTP response code: " + responseCode);

            if (responseCode != 200) {
                plugin.debugLog("[VersionChecker] Failed to fetch Modrinth version, non-200 response: " + responseCode);
                return null;
            }

            var json = JsonParser.parseReader(new InputStreamReader(connection.getInputStream()));
            var version = json.getAsJsonArray().get(0).getAsJsonObject().get("version_number").getAsString();
            plugin.debugLog("[VersionChecker] Successfully parsed Modrinth latest version: " + version);
            return version;
        } catch (Exception e) {
            plugin.debugLog("[VersionChecker] Error during Modrinth version check: " + e.getMessage());
            return null;
        }
    }

    public boolean isOutdated(String currentVersion) {
        var latest = getLatestVersion();
        plugin.debugLog("[VersionChecker] Comparing current version " + currentVersion + " with latest version " + (latest != null ? latest : "null"));
        if (latest == null) {
            plugin.debugLog("[VersionChecker] No latest version available, assuming not outdated");
            return false;
        }
        var normalizedLatest = latest.startsWith("v") ? latest.substring(1) : latest;
        var normalizedCurrent = currentVersion.startsWith("v") ? currentVersion.substring(1) : currentVersion;
        boolean outdated = !normalizedLatest.equalsIgnoreCase(normalizedCurrent);
        plugin.debugLog("[VersionChecker] Outdated check result: " + outdated);
        return outdated;
    }
}