package com.crimsonwarpedcraft.nakedandafraid.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

public class VersionChecker {

    private static final String GITHUB_API_LATEST_RELEASE = "https://api.github.com/repos/TheRealKushi/NakedAndAfraid/releases/latest";

    public static String getLatestVersion() {
        try {
            URL url = new URI(GITHUB_API_LATEST_RELEASE).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json");
            connection.setRequestProperty("User-Agent", "NakedAndAfraid-Plugin");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            if (connection.getResponseCode() != 200) return null;

            JsonElement json = JsonParser.parseReader(new InputStreamReader(connection.getInputStream()));
            return json.getAsJsonObject().get("tag_name").getAsString();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static boolean isOutdated(String currentVersion) {
        String latest = getLatestVersion();
        if (latest == null) return false;
        return !latest.equalsIgnoreCase(currentVersion);
    }
}
