# BlockBack

BlockBack is a feature-rich Minecraft plugin that enhances your gameplay by allowing you to rebark logs, dig up paths, and un-till farmland with simple right-clicks. Version 1.2.0 introduces player toggles, custom sounds, and extensive configuration options!

## Features

### Core Functionality
- **BarkBack**: Restore bark on stripped logs by right-clicking with an axe
- **PathBack**: Convert path blocks back to dirt by right-clicking with a shovel  
- **FarmBack**: Revert farmland to dirt by right-clicking with a hoe

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

### Commands
- `/blockback` - View the status of all your BlockBack features
- `/barkback` - Toggle BarkBack feature on/off for yourself
- `/pathback` - Toggle PathBack feature on/off for yourself
- `/farmback` - Toggle FarmBack feature on/off for yourself
- `/blockback reload` - Reload configuration files (requires permission)

## Configuration

### sounds.yml
Customize sound effects for each feature:
```yaml
bark-back:
  enabled: true
  sound: ITEM_AXE_STRIP
  volume: 1.0
  pitch: 1.0
  category: BLOCKS

path-back:
  enabled: true
  sound: ITEM_SHOVEL_FLATTEN
  volume: 1.0
  pitch: 1.0
  category: BLOCKS

farm-back:
  enabled: true
  sound: ITEM_HOE_TILL
  volume: 1.0
  pitch: 1.0
  category: BLOCKS
```

### Player Data
Player preferences are automatically saved in `players.yml` and include:
- Individual feature toggles (BarkBack, PathBack, FarmBack)
- Settings persist across server restarts
- Automatic backup system maintains data integrity

## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `blockback.use` | Use the /blockback command | All players |
| `blockback.bark` | Use BarkBack feature | All players |
| `blockback.path` | Use PathBack feature | All players |
| `blockback.farm` | Use FarmBack feature | All players |
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
