package com.crimsonwarpedcraft.nakedandafraid.entrypoint;

import com.crimsonwarpedcraft.nakedandafraid.common.PluginDelegate;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Plugin entrypoint — loaded by Bukkit for all server versions.
 * <p>
 * Detects the running Minecraft version and reflectively instantiates the
 * matching {@code NakedAndAfraid} implementation.  Every version-specific
 * class implements {@link PluginDelegate}, so after the one-time reflection
 * call all further dispatch is a normal interface call — no per-method
 * {@code getMethod().invoke()} chains, no raw {@code Object} casts.
 */
public class Main extends JavaPlugin {

    private PluginDelegate delegate;

    @Override
    public void onEnable() {
        String version = Bukkit.getBukkitVersion().split("-")[0];
        getLogger().info("[NakedAndAfraid] Detected server version: " + version);

        try {
            String className = resolveClassName(version);
            if (className == null) {
                getLogger().severe("[NakedAndAfraid] Unsupported server version: " + version);
                getServer().getPluginManager().disablePlugin(this);
                return;
            }

            // One reflective call to construct the version-specific class;
            // everything else goes through the typed PluginDelegate interface.
            Class<?> implClass = Class.forName(className);
            delegate = (PluginDelegate) implClass
                    .getConstructor(org.bukkit.plugin.Plugin.class)
                    .newInstance(this);

            delegate.onEnable();
            getLogger().info("[NakedAndAfraid] Loaded implementation: " + className);

        } catch (Exception e) {
            getLogger().severe("[NakedAndAfraid] Failed to initialize plugin: "
                    + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (delegate != null) {
            delegate.onDisable();
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             String @NotNull [] args) {
        if (delegate != null) {
            return delegate.onCommand(sender, command, label, args);
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command command,
                                      @NotNull String alias,
                                      String @NotNull [] args) {
        if (delegate != null) {
            return delegate.onTabComplete(sender, command, alias, args);
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // Version routing
    // -----------------------------------------------------------------------

    /**
     * Maps a Bukkit version string to the fully-qualified name of the
     * matching {@code NakedAndAfraid} implementation class.
     *
     * @param version e.g. {@code "1.21.1"}, {@code "1.17"}, {@code "1.8.8"}
     * @return the class name, or {@code null} if the version is unsupported
     */
    private static String resolveClassName(String version) {
        if (version.matches("1\\.[89](\\.[0-9])?")
                || version.matches("1\\.(1[0-6])(\\.[0-9])?")) {
            return "com.crimsonwarpedcraft.nakedandafraid.v1_8.NakedAndAfraid";
        }

        if (version.matches("1\\.(1[7-8])(\\.[0-2])?")) {
            return "com.crimsonwarpedcraft.nakedandafraid.v1_17.NakedAndAfraid";
        }

        if (version.matches("1\\.(19|[2-9][0-9])(\\.[0-9]{1,2})?")) {
            return "com.crimsonwarpedcraft.nakedandafraid.v1_21.NakedAndAfraid";
        }

        return null;
    }
}