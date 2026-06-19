package com.crimsonwarpedcraft.nakedandafraid.common;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * Contract that every version-specific {@code NakedAndAfraid} class must satisfy.
 * <p>
 * {@code Main} casts the reflectively-loaded class to this interface once
 */
public interface PluginDelegate {

    /** Called by {@code Main#onEnable}. */
    void onEnable();

    /** Called by {@code Main#onDisable}. */
    void onDisable();

    /**
     * Called by {@code Main#onCommand}.
     *
     * @return {@code true} if the command was handled successfully
     */
    boolean onCommand(CommandSender sender, Command command, String label, String[] args);

    /**
     * Called by {@code Main#onTabComplete}.
     *
     * @return list of tab-completion candidates, or {@code null} to fall back to default
     */
    List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args);
}