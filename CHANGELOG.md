# Changelog

All notable changes to BlockBack are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.5.0] - 2026-10-09

### Added
- **Admin update notices**: Asynchronous startup and daily checks for newer published stable GitHub releases, with cached join notices for `blockback.update` (default operators). Opt out through `config.yml` and `/blockback reload`; files are never installed automatically.
- **CopperBack**: Sneak + left-click with a main-hand axe advances copper oxidation one stage. Waxed and fully oxidized copper consume the gesture unchanged. Offhand axes never activate CopperBack.
- All right-clicks and sneak + right-clicks retain vanilla behavior, including scraping, wax removal, statue revival and copper block interactions. Left-clicking without sneaking breaks blocks normally.
- Runtime support for all 15 current copper oxidation families, with block-state preservation and paired updates for doors and double copper chests.
- `/copperback`, persistent player preferences, status output and configurable sounds. Both the player preference and `blockback.copper` permission default to off.
- Behavioral regression coverage for oxidation, interaction denials, main-hand activation and offhand exclusion, paired blocks, snapshot handling, commands and saved preferences.

### Fixed
- Toggling a feature after player-cache eviction preserves the player's other saved preferences.

### Verification
- The automated regression suite covers Spigot 1.21.1 and 26.3; the release retains Java 21 bytecode compatibility.
- Live server acceptance remains pending for Folia, protection plugins, inventories and tile data.
- Update-checker transport, timing, permissions, reload and shutdown are covered by deterministic tests; live join/reload/shutdown smoke checks remain separate.

## [1.4.1] - 2026-05-08

### Fixed
- **Pale Oak (1.21.3+)**: Stripped Pale Oak logs and wood now revert to their unstripped form, matching the existing behavior for all other log types.
- **Copper Axe (1.21.2+)**: The Copper Axe is now recognized as a stripping tool, so log-strip reversion triggers correctly when it is used.

### Notes
- Both materials are resolved reflectively at startup, so the plugin still compiles against the 1.21.1 API jar and remains compatible with older 1.21.x servers that do not have these blocks/items.

## [1.4.0] - Folia thread-safety

### Added
- bStats Metrics integration (plugin ID 31058).
- Folia compatibility layer (`FoliaCompat`) centralizing scheduler usage and region ownership checks.
- Region assertions wired into `EventListener` and `PlayerDataManager` for safe scheduling on Folia.

### Changed
- `FileConfiguration` access guarded by a `ReentrantReadWriteLock` for thread-safety.
- `SoundConfig` fields marked `volatile` for safe cross-thread reads.

## [1.3.0]

- Update BlockBack to version 1.3.0.

## [1.2.0]

- README and metadata updates for BlockBack 1.2.0.
