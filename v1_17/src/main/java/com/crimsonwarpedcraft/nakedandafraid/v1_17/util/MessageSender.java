// V1_17/MessageSender

package com.crimsonwarpedcraft.nakedandafraid.v1_17.util;

import org.bukkit.entity.Player;
import java.lang.reflect.Method;

public class MessageSender {

    private static boolean adventureSupported = false;
    private static Class<?> componentClass;
    private static Class<?> textColorClass;
    private static Class<?> clickEventClass;
    private static Method playerSendMethod;

    static {
        try {
            componentClass = Class.forName("net.kyori.adventure.text.Component");
            textColorClass = Class.forName("net.kyori.adventure.text.format.NamedTextColor");
            clickEventClass = Class.forName("net.kyori.adventure.text.event.ClickEvent");
            playerSendMethod = Player.class.getMethod("sendMessage", componentClass);
            adventureSupported = true;
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            adventureSupported = false;
        }
    }

    public static void sendOutdated(Player player, String currentVersion, String latestVersion) {
        if (adventureSupported) {
            try {
                Object darkRed = textColorClass.getField("DARK_RED").get(null);
                Object gold = textColorClass.getField("GOLD").get(null);

                Object prefix = componentClass.getMethod("text", CharSequence.class, textColorClass).invoke(null, "[NakedAndAfraid] ", darkRed);
                Object message = componentClass.getMethod("text", CharSequence.class, textColorClass)
                        .invoke(null, "You're using Version " + currentVersion + ", however, Version " + latestVersion + " is available.", gold);
                Object combined = componentClass.getMethod("append", componentClass).invoke(prefix, message);
                playerSendMethod.invoke(player, combined);

                Object linkPrefix = componentClass.getMethod("text", CharSequence.class, textColorClass).invoke(null, "[NakedAndAfraid] ", darkRed);
                Object downloadText = componentClass.getMethod("text", CharSequence.class, textColorClass).invoke(null, "Download it here: ", gold);
                Object urlText = componentClass.getMethod("text", CharSequence.class, textColorClass).invoke(null, "https://modrinth.com/plugin/naked-and-afraid-plugin/versions", gold);
                Object clickEvent = clickEventClass.getMethod("openUrl", String.class).invoke(null, "https://modrinth.com/plugin/naked-and-afraid-plugin/versions");
                urlText = componentClass.getMethod("clickEvent", clickEventClass).invoke(urlText, clickEvent);
                Object combined2 = componentClass.getMethod("append", componentClass).invoke(linkPrefix, downloadText);
                combined2 = componentClass.getMethod("append", componentClass).invoke(combined2, urlText);

                playerSendMethod.invoke(player, combined2);

            } catch (Exception ignored) {
                player.sendMessage("§4[NakedAndAfraid] §6You're using Version " + currentVersion +
                        ", however, Version " + latestVersion + " is available.");
                player.sendMessage("§4[NakedAndAfraid] §6Download it here: https://modrinth.com/plugin/naked-and-afraid-plugin/versions");
            }
        } else {
            player.sendMessage("§4[NakedAndAfraid] §6You're using Version " + currentVersion +
                    ", however, Version " + latestVersion + " is available.");
            player.sendMessage("§4[NakedAndAfraid] §6Download it here: https://modrinth.com/plugin/naked-and-afraid-plugin/versions");
        }
    }
}
