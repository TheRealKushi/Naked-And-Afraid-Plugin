package com.crimsonwarpedcraft.nakedandafraid.common.util;

import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;

/**
 * Checks GitHub and Modrinth for the latest plugin release.
 * <p>
 * Depends only on Gson and the {@link PluginLogger} abstraction, so it
 * compiles at Java-8 level and is safe to share across all version modules.
 */
public class VersionChecker {

    private static final String GITHUB_API_LATEST_RELEASE =
            "https://api.github.com/repos/TheRealKushi/Naked-And-Afraid-Plugin/releases/latest";
    private static final String MODRINTH_API_LATEST_VERSION =
            "https://api.modrinth.com/v2/project/naked-and-afraid-plugin/version";

    private final PluginLogger logger;
    private final String githubToken;

    /**
     * @param logger      a {@link PluginLogger} – any {@code NakedAndAfraid} subclass satisfies this
     * @param githubToken optional GitHub personal access token; pass {@code null} to skip auth
     */
    public VersionChecker(PluginLogger logger, String githubToken) {
        this.logger = logger;
        this.githubToken = githubToken;
        logger.debugLog("[VersionChecker] Initialized VersionChecker" +
                (githubToken != null ? " with GitHub API token" : ""));
    }

    /** Returns the latest version string, trying GitHub first then Modrinth. */
    public String getLatestVersion() {
        String version = getLatestVersionFromGitHub();
        if (version != null) {
            return version;
        }
        return getLatestVersionFromModrinth();
    }

    private String getLatestVersionFromGitHub() {
        logger.debugLog("[VersionChecker] Starting GitHub version check, querying URL: " + GITHUB_API_LATEST_RELEASE);
        try {
            java.net.URL url = URI.create(GITHUB_API_LATEST_RELEASE).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("User-Agent", "NakedAndAfraid-Plugin");
            if (githubToken != null) {
                connection.setRequestProperty("Authorization", "Bearer " + githubToken);
                logger.debugLog("[VersionChecker] Using GitHub API token for authentication");
            }
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            int responseCode = connection.getResponseCode();
            logger.debugLog("[VersionChecker] GitHub HTTP response code: " + responseCode);

            if (responseCode != 200) {
                logger.debugLog("[VersionChecker] Failed to fetch GitHub version, non-200 response: " + responseCode);
                return null;
            }

            com.google.gson.JsonElement json =
                    JsonParser.parseReader(new InputStreamReader(connection.getInputStream()));
            String version = json.getAsJsonObject().get("tag_name").getAsString();
            logger.debugLog("[VersionChecker] Successfully parsed GitHub latest version: " + version);
            return version;
        } catch (Exception e) {
            logger.debugLog("[VersionChecker] Error during GitHub version check: " + e.getMessage());
            return null;
        }
    }

    private String getLatestVersionFromModrinth() {
        logger.debugLog("[VersionChecker] Starting Modrinth version check, querying URL: " + MODRINTH_API_LATEST_VERSION);
        try {
            java.net.URL url = URI.create(MODRINTH_API_LATEST_VERSION).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "NakedAndAfraid-Plugin");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            int responseCode = connection.getResponseCode();
            logger.debugLog("[VersionChecker] Modrinth HTTP response code: " + responseCode);

            if (responseCode != 200) {
                logger.debugLog("[VersionChecker] Failed to fetch Modrinth version, non-200 response: " + responseCode);
                return null;
            }

            com.google.gson.JsonElement json =
                    JsonParser.parseReader(new InputStreamReader(connection.getInputStream()));
            String version = json.getAsJsonArray().get(0).getAsJsonObject()
                    .get("version_number").getAsString();
            logger.debugLog("[VersionChecker] Successfully parsed Modrinth latest version: " + version);
            return version;
        } catch (Exception e) {
            logger.debugLog("[VersionChecker] Error during Modrinth version check: " + e.getMessage());
            return null;
        }
    }

    /** Returns {@code true} if the latest known version differs from {@code currentVersion}. */
    public boolean isOutdated(String currentVersion) {
        String latest = getLatestVersion();
        logger.debugLog("[VersionChecker] Comparing current version " + currentVersion +
                " with latest version " + (latest != null ? latest : "null"));
        if (latest == null) {
            logger.debugLog("[VersionChecker] No latest version available, assuming not outdated");
            return false;
        }
        String normalizedLatest  = latest.startsWith("v")        ? latest.substring(1)        : latest;
        String normalizedCurrent = currentVersion.startsWith("v") ? currentVersion.substring(1) : currentVersion;
        boolean outdated = !normalizedLatest.equalsIgnoreCase(normalizedCurrent);
        logger.debugLog("[VersionChecker] Outdated check result: " + outdated);
        return outdated;
    }
}