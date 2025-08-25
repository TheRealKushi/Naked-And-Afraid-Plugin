# NakedAndAfraid Plugin
![Release](https://img.shields.io/github/v/release/TheRealKushi/Naked-And-Afraid-Plugin) 
![Build](https://github.com/TheRealKushi/Naked-And-Afraid-Plugin/actions/workflows/main.yml/badge.svg)

A Minecraft survival plugin for PaperMC/Spigot/Folia/Purpur servers which adds all the features from the Naked and Afraid Minecraft SMP.

[![Modrinth](https://img.shields.io/badge/Modrinth-NakedAndAfraid-blue?logo=modrinth)](https://modrinth.com/plugin/naked-and-afraid-plugin)
[![CurseForge](https://img.shields.io/badge/CurseForge-NakedAndAfraid-orange?logo=curseforge)](https://www.curseforge.com/minecraft/bukkit-plugins/naked-and-afraid-plugin)
[![Spigot](https://img.shields.io/badge/Spigot-NakedAndAfraid-red?logo=spigotmc)](https://www.spigotmc.org/resources/naked-and-afraid-plugin.128063/)
---

## Features

### Gameplay Mechanics ⚔️
- **Custom Spawns:** Define multiple spawn points for players, where they can be easily teleported to using the plugin commands.
- **Teams & Team Management:** Create teams, add/remove players, and manage team blocks, where players can right-click to have their nametag colored the color of their corresponding team.
- **Chat & Tab Restrictions:** Disable chat and hide players from the tab list.
- **Armor Damage:** Players take a customizable amount of damage every interval of time if they wear any piece of armor.
- **Join/Quit Suppression:** Hide join and quit messages from the chat.
- **Permissions:** Choose who can use the plugin's commands by setting the permissions.

### Commands 📜
- `/nf help` – Displays a paginated help menu.
- `/nf reloadconfig` – Reload the plugin configuration.
- `/nf spawn ...` – Manage custom spawns, including their name, coordinates and target player.
- `/nf team ...` – Manage teams, with creation, deletion, renaming, team-block setting, etc.
- `/nf user ...` – Manage individual players, setting or removing their teams.

### Admin Tools 🛠
- Configurable plugin settings via `config.yml`.
- Tab list hiding requires **ProtocolLib**.
- Easy-to-use commands for spawn and team management.
- Teams info stored in `teams.yml` file.
- Spawns info stored in `spawns.yml` file.

---

## Installation

1. Download the latest JAR from either [Modrinth](https://modrinth.com/plugin/naked-and-afraid-plugin), [CurseForge](https://www.curseforge.com/minecraft/bukkit-plugins/naked-and-afraid-plugin) or [Spigot](https://www.spigotmc.org/resources/naked-and-afraid-plugin.128063/).
2. Place the JAR into your server’s `plugins` folder.
3. Start the server to generate default `config.yml`.
4. Edit `config.yml` to adjust settings for your server.
5. Restart the server or run `/nf reloadconfig` to apply changes.

---

## Configuration

Example configuration options in `config.yml`:

```yaml
############################################################
# +------------------------------------------------------+ #
# |                       Notes                          | #
# +------------------------------------------------------+ #
############################################################

# This is the config file for the Naked And Afraid plugin.
# This config was generated for version 1.0.0.

# If you want to use special characters in this document, such as accented letters, you MUST save the file as UTF-8, not ANSI.
# If you receive an error when the plugin loads, ensure that:
#   - No tabs are present: YAML only allows spaces
#   - Indents are correct: YAML hierarchy is based entirely on indentation
#   - You have "escaped" all apostrophes in your text: If you want to write "don't", for example, write "don''t" instead (note the doubled apostrophe)
#   - Text with symbols is enclosed in single or double quotation marks

############################################################
# +------------------------------------------------------+ #
# |         Naked And Afraid (Global Settings)           | #
# +------------------------------------------------------+ #
############################################################

# A toggle to either enable or disable the chat during gameplay, only allowing OPs to chat.
disable-chat: true

# A toggle to either enable or disable the tab list for all players.
disable-tab: true

# A toggle to either enable or disable the player join and quit messages.
disable-join-quit-messages: true

armor-damage:
  # A toggle to either enable or disable the function that deals damage when players wear an armor piece.
  # Offhand will not be accounted in damaging process.
  enabled: true
  # The amount of hearts the plugin will deal damage (1 heart = 1.0).
  damage-amount: 1.0
  # Value that determines the interval of ticks (1 second = 20 ticks) that the plugin will deal damage to players.
  damage-interval-ticks: 20

# Toggles the debug mode, which provides useful info that can be important for troubleshooting errors.
debug-mode: false

############################################################
# +------------------------------------------------------+ #
# |               Naked And Afraid (Spawn)               | #
# +------------------------------------------------------+ #
############################################################

# Value that determines the maximum amount of spawns that can be created.
max-spawns: 20
# Value that determines whether the cooldown will be enabled on player teleport.
enable-countdown: true
# Value that determines the duration of the cooldown (in seconds).
countdown-duration: 10
# Color Value that determines the cooldown bossbar's color - see Minecraft Formatting Codes for help.
countdown-color: RED
# Value that determines whether the player will be frozen in place until cooldown is over.
countdown-freeze: true
# Message shown in the countdown bossbar. Use {time} as a placeholder for remaining seconds.
countdown-message: "Game starts in {time}"
# Whether teleport happens immediately (false) or after countdown ends (true).
teleport-on-countdown-end: false
# Value that determines what order the teleport command will use if there are multiple spawns set to the same person.
# Can be either FIRST, LAST or RANDOM.
multiple-spawn-priority: FIRST

############################################################
# +------------------------------------------------------+ #
# |               Naked And Afraid (Teams)               | #
# +------------------------------------------------------+ #
############################################################

# Value that defines the maximum amount of teams that can be created.
max-teams: 5
# The block that is used as a Team's Block, and where players have to click to receive their colored nametag.
team-block: LODESTONE
```

## License
This project is licensed under the GNU GENERAL PUBLIC LICENSE. See [LICENSE](LICENSE) for details.

## Reporting Vulnerabilites
To report **Security Vulnerabilites**, please see [SECURITY.md](SECURITY.md) for details.

## Building from Source

Clone the repository and build with Gradle:

```bash
git clone https://github.com/TheRealKushi/Naked-And-Afraid-Plugin.git
cd Naked-And-Afraid-Plugin
./gradlew build
```

## Contributing
Your contributions are welcome! Please fork the repo, make your changes, and submit a pull request.
Follow the [CODE_OF_CONDUCT](CODE_OF_CONDUCT.md) and [CONTRIBUTING](CONTRIBUTING.md) guidelines.
