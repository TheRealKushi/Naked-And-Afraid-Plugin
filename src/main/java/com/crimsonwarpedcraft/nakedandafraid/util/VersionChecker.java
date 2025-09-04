package com.crimsonwarpedcraft.nakedandafraid.util;

import com.crimsonwarpedcraft.nakedandafraid.NakedAndAfraid;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

public class VersionChecker {

    private static final String GITHUB_API_LATEST_RELEASE = "https://api.github.com/repos/TheRealKushi/NakedAndAfraid/releases/latest";
    private final NakedAndAfraid plugin;

    public VersionChecker(NakedAndAfraid plugin) {
        this.plugin = plugin;
        plugin.debugLog("[VersionChecker] Initialized VersionChecker");
    }

    public String getLatestVersion() {
        plugin.debugLog("[VersionChecker] Starting version check, querying URL: " + GITHUB_API_LATEST_RELEASE);
        try {
            URL url = new URI(GITHUB_API_LATEST_RELEASE).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json");
            connection.setRequestProperty("User-Agent", "NakedAndAfraid-Plugin");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            int responseCode = connection.getResponseCode();
            plugin.debugLog("[VersionChecker] HTTP response code: " + responseCode);

            if (responseCode != 200) {
                plugin.debugLog("[VersionChecker] Failed to fetch version, non-200 response: " + responseCode);
                return null;
            }

            JsonElement json = JsonParser.parseReader(new InputStreamReader(connection.getInputStream()));
            String version = json.getAsJsonObject().get("tag_name").getAsString();
            plugin.debugLog("[VersionChecker] Successfully parsed latest version: " + version);
            return version;
        } catch (Exception e) {
            plugin.debugLog("[VersionChecker] Error during version check: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public boolean isOutdated(String currentVersion) {
        String latest = getLatestVersion();
        plugin.debugLog("[VersionChecker] Comparing current version " + currentVersion + " with latest version " + (latest != null ? latest : "null"));
        if (latest == null) {
            plugin.debugLog("[VersionChecker] No latest version available, assuming not outdated");
            return false;
        }
        boolean outdated = !latest.equalsIgnoreCase(currentVersion);
        plugin.debugLog("[VersionChecker] Outdated check result: " + outdated);
        return outdated;
    }
}