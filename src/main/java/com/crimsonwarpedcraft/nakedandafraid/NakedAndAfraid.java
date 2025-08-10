package com.crimsonwarpedcraft.nakedandafraid;

import com.crimsonwarpedcraft.nakedandafraid.listeners.*;
import com.crimsonwarpedcraft.nakedandafraid.spawn.SpawnManager;
import com.crimsonwarpedcraft.nakedandafraid.util.TeleportHelper;
import io.papermc.lib.PaperLib;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

/**
 * Main plugin class for Naked And Afraid.
 */
public class NakedAndAfraid extends JavaPlugin {

  private ChatRestrictionListener chatRestrictionListener;
  private ArmorDamageListener armorDamageListener;
  private JoinQuitMessageSuppressor joinQuitMessageSuppressor;
  private TabListClearer tabListClearer;

  private final String PACK_URL = "https://github.com/TheRealKushi/Deathcraft-Assets/releases/download/release-1.0.2/NakedAndAfraid-1.0.2.zip";
  private static final String PACK_HASH = "3e930d7cbfbd7dcb113dcc79820abe13e1601835";

  private SpawnManager spawnManager;
  private TeleportHelper teleportHelper;

  @Override
  public void onEnable() {
    PaperLib.suggestPaper(this);
    saveDefaultConfig();
    reloadListeners();

    getServer().getPluginManager().registerEvents(new GlobalDeathSoundListener(this), this);

    spawnManager = new SpawnManager(this);
    spawnManager.loadSpawns();

    teleportHelper = new TeleportHelper(this);

    if (getConfig().getBoolean("disable-tab", true)) {
      tabListClearer = new TabListClearer(this);
      getServer().getPluginManager().registerEvents(tabListClearer, this);
      tabListClearer.startClearing();
      getLogger().info("Naked And Afraid - Tab List Clearing Enabled.");
    } else {
      tabListClearer = null;
    }

    ConsoleCommandSender console = Bukkit.getServer().getConsoleSender();

    console.sendMessage(" ");
    console.sendMessage(Component.text("__   ____  _______ ").color(NamedTextColor.GOLD));
    console.sendMessage(Component.text("| \\ | | | | ____||").color(NamedTextColor.GOLD));
    console.sendMessage(Component.text("|  \\| | | | ||_   ").color(NamedTextColor.GOLD));
    console.sendMessage(Component.text("| |\\| | | | __||  ").color(NamedTextColor.GOLD));
    console.sendMessage(Component.text("|_| \\_|_| |_||    ").color(NamedTextColor.GOLD));
    console.sendMessage(Component.empty());
    console.sendMessage(Component.text("NakedAndAfraid Plugin ").color(NamedTextColor.GOLD)
            .append(Component.text("v" + this.getDescription().getVersion()).color(NamedTextColor.DARK_RED)));
    console.sendMessage(Component.text("Running on ").color(NamedTextColor.YELLOW)
            .append(Component.text(Bukkit.getServer().getName()).color(NamedTextColor.AQUA))
            .append(Component.text(" " + Bukkit.getServer().getVersion()).color(NamedTextColor.WHITE)));
    console.sendMessage(Component.empty());
    console.sendMessage(Component.text("Plugin enabled successfully!").color(NamedTextColor.GREEN));
    console.sendMessage(Component.empty());
  }

  /**
   * Reload listeners based on config settings.
   */
  public void reloadListeners() {
    // Unregister old listeners if any
    if (chatRestrictionListener != null) {
      getServer().getPluginManager().callEvent(
              new org.bukkit.event.server.PluginDisableEvent(this)
      );
    }
    if (armorDamageListener != null) {
      getServer().getPluginManager().callEvent(
              new org.bukkit.event.server.PluginDisableEvent(this)
      );
    }
    if (joinQuitMessageSuppressor != null) {
      getServer().getPluginManager().callEvent(
              new org.bukkit.event.server.PluginDisableEvent(this)
      );
    }

    // Register listeners based on config
    if (getConfig().getBoolean("disable-chat", true)) {
      chatRestrictionListener = new ChatRestrictionListener();
      getServer().getPluginManager().registerEvents(chatRestrictionListener, this);
      getLogger().info("Naked And Afraid - Chat Restriction Enabled.");
    } else {
      chatRestrictionListener = null;
    }

    if (getConfig().getBoolean("disable-tab", true)) {
      if (tabListClearer == null) {
        tabListClearer = new TabListClearer(this);
        getServer().getPluginManager().registerEvents(tabListClearer, this);
        tabListClearer.startClearing();
      }
    } else {
      tabListClearer = null;
    }

    if (getConfig().getBoolean("armor-damage.enabled", true)) {
      armorDamageListener = new ArmorDamageListener(this);
      getServer().getPluginManager().registerEvents(armorDamageListener, this);
      getLogger().info("Naked And Afraid - Armor Damage Enabled.");
    } else {
      armorDamageListener = null;
    }

    if (getConfig().getBoolean("disable-join-quit-messages", true)) {
      joinQuitMessageSuppressor = new JoinQuitMessageSuppressor();
      getServer().getPluginManager().registerEvents(joinQuitMessageSuppressor, this);
      getLogger().info("Naked And Afraid - Message Disabling Enabled.");
    } else {
      joinQuitMessageSuppressor = null;
    }
  }

  @EventHandler
  public void onPlayerJoin(PlayerJoinEvent event) {
    Player player = event.getPlayer();

    try {
      ResourcePackInfo packInfo = ResourcePackInfo.resourcePackInfo()
              .uri(new URI(PACK_URL))   // Set URL here
              .hash(PACK_HASH)          // Set hash here
              .build();

      ResourcePackRequest request = ResourcePackRequest.resourcePackRequest()
              .packs(packInfo)
              .replace(true)
              .required(true)
              .prompt(Component.text("Please accept the NakedAndAfraid resource pack!"))
              .build();

      player.sendResourcePacks(request);
    } catch (URISyntaxException e) {
      getLogger().severe("Invalid resource pack URL: " + PACK_URL);
      getLogger().log(Level.SEVERE, "Exception while sending resource pack", e);
    }
  }

  @Override
  public boolean onCommand(final @NotNull CommandSender sender,
                           final @NotNull Command command,
                           final String label,
                           final String @NotNull [] args) {

    if (!(label.equalsIgnoreCase("nf") || label.equalsIgnoreCase("nakedafraid"))) {
      return false;
    }

    if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
      sendHelpMessage(sender);
      return true;
    }

    if (args[0].equalsIgnoreCase("reloadconfig")) {
      if (!sender.hasPermission("nakedandafraid.reload")) {
        sender.sendMessage("§cYou don't have permission to do that.");
        return true;
      }
      reloadConfig();
      spawnManager.loadSpawns();
      sender.sendMessage("§aNaked and Afraid config reloaded.");
      return true;
    }

    if (args[0].equalsIgnoreCase("spawn")) {
      return spawnManager.handleCommand(sender, args);
    }

    sender.sendMessage("§cUnknown subcommand. Use /nf help for commands.");
    return true;
  }

  public TeleportHelper getTeleportHelper() {
    return teleportHelper;
  }

  @Override
  public List<String> onTabComplete(@NotNull CommandSender sender,
                                    Command command,
                                    @NotNull String alias,
                                    String @NotNull [] args) {
    if (command.getName().equalsIgnoreCase("nf") || command.getName().equalsIgnoreCase("nakedafraid")) {
      if (args.length == 1) {
        // Root-level completions: help, reloadconfig
        List<String> completions = new ArrayList<>();
        completions.add("help");
        if (sender.hasPermission("nakedandafraid.reload")) {
          completions.add("reloadconfig");
        }
        completions.add("spawn"); // Add spawn as a root subcommand
        return completions;
      }

      // Handle tab completion for /nf spawn ...
      if (args.length >= 1 && args[0].equalsIgnoreCase("spawn")) {
        List<String> spawnSubs = List.of("create", "rename", "remove", "list", "tp", "tpall");

        if (args.length == 2) {
          // Suggest spawn subcommands
          String partial = args[1].toLowerCase();
          List<String> result = new ArrayList<>();
          for (String sub : spawnSubs) {
            if (sub.startsWith(partial)) result.add(sub);
          }
          return result;
        }

        // For subcommands that require spawn names:
        SpawnManager spawnManager = this.spawnManager;
        if (spawnManager == null) return Collections.emptyList();

        if (args.length == 3) {
          String sub = args[1].toLowerCase();

          if (sub.equals("rename") || sub.equals("remove") || sub.equals("tp")) {
            // Suggest spawn names for 3rd argument
            String partial = args[2].toLowerCase();
            List<String> matchingSpawns = new ArrayList<>();
            for (String spawnName : spawnManager.getSpawns().keySet()) {
              if (spawnName.toLowerCase().startsWith(partial)) matchingSpawns.add(spawnName);
            }
            return matchingSpawns;
          }

          if (sub.equals("create")) {
            // 3rd arg is spawn name (free text), no suggestions
            return Collections.emptyList();
          }
        }

        // 4th argument (optional target player) for create or tp
        if (args.length == 4) {
          String sub = args[1].toLowerCase();
          if (sub.equals("create") || sub.equals("tp")) {
            String partialPlayer = args[3].toLowerCase();
            List<String> players = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
              if (p.getName().toLowerCase().startsWith(partialPlayer)) {
                players.add(p.getName());
              }
            }
            return players;
          }
        }

        return Collections.emptyList();
      }
    }
    return Collections.emptyList();
  }

  /**
   * Sends help message for /nf commands.
   *
   * @param sender The command sender.
   */
  private void sendHelpMessage(final CommandSender sender) {
    sender.sendMessage("§6==== NakedAndAfraid Help ====");
    sender.sendMessage("§e/nf help §7- Show this help message");
    sender.sendMessage("§e/nf reloadconfig §7- Reload the plugin config");
    sender.sendMessage("§e/nf §7- Alias for /nf help");
  }
}