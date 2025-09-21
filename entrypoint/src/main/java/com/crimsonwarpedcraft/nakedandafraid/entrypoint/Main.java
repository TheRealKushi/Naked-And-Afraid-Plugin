package com.crimsonwarpedcraft.nakedandafraid.entrypoint;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class Main extends JavaPlugin {

    private Object delegate;

    @Override
    public void onEnable() {
        String version = Bukkit.getBukkitVersion().split("-")[0];
        getLogger().info("[NakedAndAfraid] Detected server version: " + version);

        try {
            String className;
            if (version.matches("1\\.(1[2-6])(\\.[0-5])?")) {
                className = "com.crimsonwarpedcraft.nakedandafraid.v1_8.NakedAndAfraid"; // 1.12–1.16.5
            } else if (version.matches("1\\.(1[7-8])(\\.[0-1])?")) {
                className = "com.crimsonwarpedcraft.nakedandafraid.v1_17.NakedAndAfraid"; // 1.17–1.18.2
            } else if (version.matches("1\\.(1[9]|2[0-1])(\\.[0-8])?")) {
                className = "com.crimsonwarpedcraft.nakedandafraid.v1_21.NakedAndAfraid"; // 1.19–1.21.8
            } else {
                getLogger().severe("[NakedAndAfraid] Unsupported server version: " + version);
                getServer().getPluginManager().disablePlugin(this);
                return;
            }

            Class<?> pluginClass = Class.forName(className);
            delegate = pluginClass.getConstructor(org.bukkit.plugin.Plugin.class).newInstance(this); // Pass 'this' as Plugin
            pluginClass.getMethod("onEnable").invoke(delegate); // Call the version-specific onEnable()
            getLogger().info("[NakedAndAfraid] Loaded implementation for version: " + version);
        } catch (Exception e) {
            getLogger().severe("[NakedAndAfraid] Failed to initialize plugin: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (delegate != null) {
            try {
                delegate.getClass().getMethod("onDisable").invoke(delegate); // Call the version-specific onDisable()
            } catch (Exception e) {
                getLogger().severe("[NakedAndAfraid] Failed to shut down plugin: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (delegate != null) {
            try {
                return (boolean) delegate.getClass().getMethod("onCommand", CommandSender.class, Command.class, String.class, String[].class)
                        .invoke(delegate, sender, command, label, args);
            } catch (Exception e) {
                getLogger().severe("[NakedAndAfraid] Failed to process command: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String @NotNull [] args) {
        if (delegate != null) {
            try {
                return (List<String>) delegate.getClass().getMethod("onTabComplete", CommandSender.class, Command.class, String.class, String[].class)
                        .invoke(delegate, sender, command, alias, args);
            } catch (Exception e) {
                getLogger().severe("[NakedAndAfraid] Failed to process tab completion: " + e.getMessage());
                e.printStackTrace();
                return null;
            }
        }
        return null;
    }
}