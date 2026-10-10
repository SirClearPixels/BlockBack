# BlockBack

[![Modrinth](https://img.shields.io/badge/Modrinth-00AF5C?style=for-the-badge&logo=modrinth&logoColor=white)](https://modrinth.com/plugin/blockback)
[![Modrinth Downloads](https://img.shields.io/modrinth/dt/blockback?style=flat-square&logo=modrinth&color=00AF5C&label=Modrinth)](https://modrinth.com/plugin/blockback)

[![Spigot](https://img.shields.io/badge/Spigot-ED8106?style=for-the-badge&logo=spigotmc&logoColor=white)](https://www.spigotmc.org/resources/blockback.126328/)
[![Spigot Downloads](https://img.shields.io/spiget/downloads/126328?style=flat-square&logo=spigotmc&color=ED8106&label=Spigot)](https://www.spigotmc.org/resources/blockback.126328/)
[![Spigot Rating](https://img.shields.io/spiget/stars/126328?style=flat-square&logo=spigotmc&color=ED8106)](https://www.spigotmc.org/resources/blockback.126328/)

[![Hangar](https://img.shields.io/badge/Hangar-004C99?style=for-the-badge&logo=papermc&logoColor=white)](https://hangar.papermc.io/SirClearPixels/BlockBack)
[![Hangar Downloads](https://img.shields.io/hangar/dt/BlockBack?style=flat-square&logo=papermc&color=004C99&label=Hangar)](https://hangar.papermc.io/SirClearPixels/BlockBack)

BlockBack is a feature-rich Minecraft plugin that enhances your gameplay by allowing you to rebark logs, dig up paths, and un-till farmland with simple right-clicks. Version 1.2.0 introduces player toggles, custom sounds, and extensive configuration options!

## Features

### Core Functionality
- **BarkBack**: Restore bark on stripped logs by right-clicking with an axe
- **PathBack**: Convert path blocks back to dirt by right-clicking with a shovel  
- **FarmBack**: Revert farmland to dirt by right-clicking with a hoe
- **CopperBack**: Advance copper oxidation one stage with sneak + right-click using an axe (permission and player toggle default off)

### New in Version 1.2.0
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

Sneak + right-click with an axe advances unwaxed copper one oxidation stage. Fully oxidized copper stops there; waxed copper stays waxed. These enabled sneak gestures consume the axe action even when nothing changes. Ordinary right-click retains vanilla scraping, unwaxing and statue revival. Disable `/copperback` to restore vanilla sneak interactions too.

All complete copper oxidation families available on the running server are recognized, including newer chests, bars, chains, lanterns, golem statues and lightning rods when present. Doors and double copper chests update their matching halves together. Main-hand axes take priority over an offhand axe; an offhand axe works when the main hand has no axe. Denied block or item interactions are left unchanged.

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

## Compatibility

- **Minecraft Version**: 1.21.1 (fully tested)
- **Java Version**: Java 21 or higher
- **Server Software**: Paper, Spigot, or compatible forks


### Contributing

Contributions are welcome!


### Inspiration

https://github.com/Velvi42/BarkBack


### License

This project is licensed under the GNU General Public License v3.0 See the LICENSE file for details.
