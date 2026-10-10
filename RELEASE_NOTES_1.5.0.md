# BlockBack 1.5.0 — CopperBack

CopperBack adds control over copper oxidation alongside BarkBack, PathBack and FarmBack.

- Sneak + left-click with an axe in your main hand to advance oxidation one stage. Waxed and fully oxidized copper consume this gesture unchanged. Offhand axes never activate CopperBack.
- All right-clicks and sneak + right-clicks keep vanilla behavior, including axe scraping, wax removal, statue revival and interactions with copper chests, doors and trapdoors. Left-clicking without sneaking breaks blocks normally.
- Supports all 15 current oxidation families when present on the server, including copper chests, statues, bars, chains, lanterns and lightning rods.
- Includes persistent `/copperback` toggles, `/blockback` status and configurable `copperback` sounds.
- Fixes other player preferences resetting when a feature is toggled after cache eviction.
- Checks newer published stable GitHub releases at startup and daily. Operators and players granted `blockback.update` receive cached update notices on join; disable through `config.yml` and apply with `/blockback reload`. The checker never installs files, and older jars without it cannot notify retroactively.

## Enable CopperBack

Grant the player `blockback.copper` through your permission plugin, then have them run `/copperback`. Both permission and player preference default to off. Run the command again to disable CopperBack.

## Build and verification

The release JAR is `target/BlockBack-1.5.0.jar`. It targets Java 21 bytecode and the existing Spigot 1.21.1 API baseline. The automated regression suite covers both Spigot 1.21.1 and 26.3; the newer API checks use Java 25.

Live Folia/protection-plugin behavior and actual chest/statue data preservation still require server testing. Automated snapshot tests are not live server acceptance. The wider planned server-control milestone is not completed by this release version change.
