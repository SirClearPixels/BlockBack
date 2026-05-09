# Changelog

All notable changes to BlockBack are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
