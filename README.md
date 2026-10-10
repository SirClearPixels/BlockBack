# BlockBack

[![Modrinth](https://img.shields.io/badge/Modrinth-00AF5C?style=for-the-badge&logo=modrinth&logoColor=white)](https://modrinth.com/plugin/blockback)
[![Modrinth Downloads](https://img.shields.io/modrinth/dt/blockback?style=flat-square&logo=modrinth&color=00AF5C&label=Modrinth)](https://modrinth.com/plugin/blockback)

[![Spigot](https://img.shields.io/badge/Spigot-ED8106?style=for-the-badge&logo=spigotmc&logoColor=white)](https://www.spigotmc.org/resources/blockback.126328/)
[![Spigot Downloads](https://img.shields.io/spiget/downloads/126328?style=flat-square&logo=spigotmc&color=ED8106&label=Spigot)](https://www.spigotmc.org/resources/blockback.126328/)
[![Spigot Rating](https://img.shields.io/spiget/stars/126328?style=flat-square&logo=spigotmc&color=ED8106)](https://www.spigotmc.org/resources/blockback.126328/)

[![Hangar](https://img.shields.io/badge/Hangar-004C99?style=for-the-badge&logo=papermc&logoColor=white)](https://hangar.papermc.io/SirClearPixels/BlockBack)
[![Hangar Downloads](https://img.shields.io/hangar/dt/BlockBack?style=flat-square&logo=papermc&color=004C99&label=Hangar)](https://hangar.papermc.io/SirClearPixels/BlockBack)

BlockBack lets players restore bark, turn paths and farmland back into dirt, and control copper oxidation. Version 1.5.0 adds CopperBack: sneak + left-click with an axe in your main hand to advance oxidation. All right-clicks, including sneak + right-click, keep vanilla behavior.

## Features

### Core Functionality
- **BarkBack**: Restore bark on stripped logs by right-clicking with an axe
- **PathBack**: Convert path blocks back to dirt by right-clicking with a shovel  
- **FarmBack**: Revert farmland to dirt by right-clicking with a hoe
- **CopperBack**: Advance copper oxidation one stage with sneak + left-click using a main-hand axe (permission and player toggle default off)

### Player Settings and Compatibility
- **Individual Feature Toggles**: Players can enable/disable each feature independently
- **Persistent Settings**: Player preferences are saved and persist across server restarts
- **Custom Sound Effects**: Fully configurable sounds for each action
- **Full Wood Support**: Works with all wood types including Cherry, Mangrove, and Bamboo
- **Advanced Configuration**: Customize sounds, permissions, and more

## Installation

1. Download the latest release of BlockBack from the [Releases](https://github.com/SirClearPixels/BlockBack/releases) page
2. Place the downloaded .jar file into your server's `plugins` directory
3. Restart your Minecraft server to load the plugin
4. (Optional) Configure sounds in `plugins/BlockBack/sounds.yml`

## Usage

### Basic Actions
- **Rebark Logs**: Right-click stripped logs while holding an axe
- **Restore Paths**: Right-click path blocks while holding a shovel
- **Un-Till Farmland**: Right-click farmland while holding a hoe

### CopperBack

Grant `blockback.copper` using your permission plugin, then have the player run `/copperback` to enable it. Both the permission and player preference default to off.

Sneak + left-click with an axe in your main hand advances unwaxed copper one oxidation stage. Fully oxidized copper stops there; waxed copper stays waxed. With CopperBack enabled and permission granted, this gesture consumes the attack even when nothing changes. Left-clicking without sneaking breaks blocks normally. An offhand axe never activates CopperBack.

All right-clicks and sneak + right-clicks retain vanilla behavior, including axe scraping, unwaxing, statue revival and interactions with copper chests, doors and trapdoors.

All complete copper oxidation families available on the running server are recognized, including newer chests, bars, chains, lanterns, golem statues and lightning rods when present. Doors and double copper chests update their matching halves together. Denied block or item interactions are left unchanged.

### Commands
- `/blockback` - View the status of all your BlockBack features
- `/barkback` - Toggle BarkBack feature on/off for yourself
- `/pathback` - Toggle PathBack feature on/off for yourself
- `/farmback` - Toggle FarmBack feature on/off for yourself
- `/copperback` - Toggle CopperBack feature on/off for yourself (requires `blockback.copper`)
- `/blockback reload` - Reload configuration files (requires permission)

## Configuration

### sounds.yml
Customize sound effects for each feature:
```yaml
barkback:
  enabled: true
  sound: ITEM_AXE_STRIP
  volume: 1.0
  pitch: 1.0
  category: BLOCKS

pathback:
  enabled: true
  sound: ITEM_SHOVEL_FLATTEN
  volume: 1.0
  pitch: 1.0
  category: BLOCKS

farmback:
  enabled: true
  sound: ITEM_HOE_TILL
  volume: 1.0
  pitch: 1.0
  category: BLOCKS
copperback:
  enabled: true
  sound: ITEM_AXE_SCRAPE
  volume: 1.0
  pitch: 1.0
  category: BLOCKS
```

The `copperback` section defaults to these values when absent from an existing file. CopperBack plays its sound only after a successful stage change.

### config.yml and update notices

BlockBack checks published stable GitHub releases asynchronously at startup and every 24 hours. When a newer release is found, the console receives one notice per version, and players with `blockback.update` receive the installed version, available version and [Modrinth download page](https://modrinth.com/plugin/blockback) when they join. This permission defaults to operators and respects explicit grants and denials.

In-game notices use the logo-inspired green palette: bold emerald (`#35FF87`) for the BlockBack prefix and new version, soft mint (`#B8F5CB`) for the message, muted sage (`#91AF9B`) for the installed version, and green (`#00D978`) for the release link. Console notices remain plain text.

```yaml
update-checker:
  enabled: true
```

Set `update-checker.enabled` to `false` in `plugins/BlockBack/config.yml` to stop checks and notices, then apply the change with `/blockback reload`. Re-enabling starts a fresh check. Joins use the latest completed check: an admin who joins before startup discovery finishes can receive the notice on a later join. Failed checks clear the cached notice until a successful check recovers.

The checker informs admins and never installs files. Older installed jars without this feature cannot receive these notifications retroactively; install 1.5.0 or later to enable discovery.

### Optional tool durability

Set these independent server settings in `plugins/BlockBack/config.yml`, then run `/blockback reload`:

```yaml
tool-durability:
  barkback: false
  pathback: false
  farmback: false
  copperback: false
```

All four default to `false`, including missing keys in existing installations. Set a feature to `true` to attempt one normal durability use on the tool that successfully performed its BlockBack action. Creative players and unbreakable tools are exempt; Unbreaking reduces wear normally. Bukkit item-damage events can cancel or adjust the damage, and item-break events are dispatched when a tool breaks. Paired copper doors or chests cost one use for the whole action. Sounds can be disabled without changing durability behavior.

These controls are separate from player toggles and permissions. Ineligible, denied, unchanged or failed actions cost nothing. Normal Minecraft tool use, copper scraping, wax removal and mining retain vanilla durability behavior. CopperBack still requires sneak + left-click with a main-hand axe; restorations use the actual interaction hand.

BlockBack respects either interaction result channel being denied. Bukkit does not identify the source of a block-only denial, so a predictively denied no-op is conservatively left unchanged along with protected interactions. Live Paper/Folia checks remain needed for protection plugins, actual break feedback, Unbreaking, both hands and reload; automated event-handler tests use API-contract fakes.

### Player Data
Player preferences are automatically saved in `players.yml` and include:
- Individual feature toggles (BarkBack, PathBack, FarmBack, CopperBack)
- Settings persist across server restarts
- Automatic backup system maintains data integrity

## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `blockback.use` | Use the /blockback command | All players |
| `blockback.bark` | Use BarkBack feature | All players |
| `blockback.path` | Use PathBack feature | All players |
| `blockback.farm` | Use FarmBack feature | All players |
| `blockback.copper` | Enable and use CopperBack | Nobody; grant explicitly |
| `blockback.reload` | Reload configuration | Operators |
| `blockback.update` | Receive published-release update notices on join | Operators |

## Compatibility

- **Minecraft API Baseline**: Spigot 1.21.1; runtime copper support adapts to the available oxidation families
- **Java Version**: Java 21 or higher
- **Server Software**: Paper, Spigot, or compatible forks


### Contributing

Contributions are welcome!


### Inspiration

https://github.com/Velvi42/BarkBack


### License

This project is licensed under the GNU General Public License v3.0 See the LICENSE file for details.
