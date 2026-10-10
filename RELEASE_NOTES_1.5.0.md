# BlockBack 1.5.0 — CopperBack

CopperBack adds control over copper oxidation alongside BarkBack, PathBack and FarmBack.

- Sneak + right-click with an axe to advance oxidation one stage. Fully oxidized copper stops there.
- Normal right-click keeps vanilla axe scraping, wax removal and statue revival. Enabled sneak use on waxed copper leaves its wax intact.
- Supports all 15 current oxidation families when present on the server, including copper chests, statues, bars, chains, lanterns and lightning rods.
- Includes persistent `/copperback` toggles, `/blockback` status and configurable `copperback` sounds.
- Fixes other player preferences resetting when a feature is toggled after cache eviction.

## Enable CopperBack

Grant the player `blockback.copper` through your permission plugin, then have them run `/copperback`. Both permission and player preference default to off. Run the command again to disable CopperBack and restore vanilla sneak interactions.

## Build and verification

The release JAR is `target/BlockBack-1.5.0.jar`. It targets Java 21 bytecode and the existing Spigot 1.21.1 API baseline. The CopperBack implementation passes 20 automated tests against both Spigot 1.21.1 and 26.3; the newer API checks use Java 25.

Live Folia/protection-plugin behavior and actual chest/statue data preservation still require server testing. Automated snapshot tests are not live server acceptance. The wider planned server-control milestone is not completed by this release version change.
