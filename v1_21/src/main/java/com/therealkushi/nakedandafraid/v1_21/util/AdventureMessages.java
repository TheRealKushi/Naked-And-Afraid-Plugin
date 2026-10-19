package com.therealkushi.nakedandafraid.v1_21.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.plugin.Plugin;

/**
 * All direct references to {@code net.kyori.adventure.*} types live in this
 * class and only this class.
 * <p>
 * Why this needs to be a separate class rather than a guarded branch inside
 * {@code NakedAndAfraid}: the JVM verifier resolves every type referenced in
 * a class's method signatures/bodies when that class is <em>loaded and
 * linked</em>, not lazily per-branch at runtime. If {@code NakedAndAfraid}
 * itself imported {@code Component} anywhere - even inside an
 * {@code if (adventureAvailable)} block - loading the {@code NakedAndAfraid}
 * class would throw {@code NoClassDefFoundError} on CraftBukkit/Spigot
 * (which don't bundle Adventure) before a single line of that guard logic
 * ever ran. Isolating Adventure usage here means {@code NakedAndAfraid}'s
 * own bytecode only ever references plain Bukkit types, and this class is
 * only loaded the first time one of its methods is actually invoked - which
 * only happens after the caller has confirmed Adventure is present.
 */
public final class AdventureMessages {

    private AdventureMessages() { }

    public static void sendError(CommandSender sender, String text) {
        sender.sendMessage(Component.text(text).color(NamedTextColor.RED));
    }

    public static void sendSuccess(CommandSender sender, String text) {
        sender.sendMessage(Component.text(text).color(NamedTextColor.GREEN));
    }

    public static void sendRaw(CommandSender sender, String legacyText) {
        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize(legacyText));
    }

    public static void sendConsoleBanner(ConsoleCommandSender console, Plugin plugin) {
        console.sendMessage(Component.text("__   ____  _______ ").color(NamedTextColor.GOLD));
        console.sendMessage(Component.text("| \\ | | | | ____||").color(NamedTextColor.GOLD));
        console.sendMessage(Component.text("|  \\| | | | ||_   ").color(NamedTextColor.GOLD));
        console.sendMessage(Component.text("| |\\| | | | __||  ").color(NamedTextColor.GOLD));
        console.sendMessage(Component.text("|_| \\_|_| |_||    ").color(NamedTextColor.GOLD));
        console.sendMessage(Component.empty());
        console.sendMessage(
                Component.text("NakedAndAfraid Plugin ").color(NamedTextColor.GOLD)
                        .append(Component.text(
                                        "v" + plugin.getDescription().getVersion())
                                .color(NamedTextColor.DARK_RED)));
        console.sendMessage(
                Component.text("Running on ").color(NamedTextColor.YELLOW)
                        .append(Component.text(
                                plugin.getServer().getName()).color(NamedTextColor.AQUA))
                        .append(Component.text(
                                " " + plugin.getServer().getVersion()).color(NamedTextColor.WHITE)));
        console.sendMessage(Component.empty());
        console.sendMessage(
                Component.text("NakedAndAfraid Plugin enabled successfully!")
                        .color(NamedTextColor.GREEN));
        console.sendMessage(Component.empty());
    }

    public static void sendOutdatedWarning(ConsoleCommandSender console, Plugin plugin,
                                           String current, String latest) {
        String warning = PlainTextComponentSerializer.plainText().serialize(
                Component.text("[NakedAndAfraid] ").color(NamedTextColor.GOLD)
                        .append(Component.text(
                                        "There is a new version available: "
                                                + latest + " (Current: " + current + ")")
                                .color(NamedTextColor.RED)));
        plugin.getLogger().warning(warning);
        console.sendMessage(
                Component.text("[NakedAndAfraid] ").color(NamedTextColor.GOLD)
                        .append(Component.text("Download it here: ").color(NamedTextColor.RED))
                        .append(Component.text(
                                        "https://modrinth.com/plugin/naked-and-afraid-plugin/versions")
                                .color(NamedTextColor.RED)
                                .clickEvent(ClickEvent.openUrl(
                                        "https://modrinth.com/plugin/naked-and-afraid-plugin/versions"))));
    }
}