---
phase: quick-261009-t73-gesture
status: complete
completed: 2026-10-09
live_player_acceptance: pending
---

# CopperBack gesture follow-up

The user found that sneak-right oxidation displaced vanilla scraping on interactable copper. They rejected an extra offhand item or mode switch and approved sneak + left-click with a main-hand axe instead. This supersedes the original quick task's right-click and offhand oxidation contract.

## Changes

- Only eligible main-hand sneak-left attacks advance oxidation. All right-click events pass through without changing either event result, including sneak-right on doors, trapdoors, chests and statues.
- Both interaction channels are denied before conversion; waxed/terminal and failed conversions consume the attack without success sound.
- A narrowly scoped BlockBreakEvent guard shares the gesture eligibility and only cancels breaks, covering mining begun before sneaking. It never advances oxidation. Non-sneaking, disabled, unpermitted, non-copper and non-axe cases keep normal breaking.
- README, changelog, release notes and Javadoc describe the new controls. Version remains 1.5.0. Saved GSD settings remain unchanged.

## Automated and source evidence

- Revised tests produced 9 expected failures against the old handler, then passed after implementation.
- Offline JDK 21 baseline `mvn -o -q clean verify`: 23 tests passed (22 CopperBack, 1 wood), no failures/errors/skips.
- Offline JDK 25 `mvn -o -q clean test -Dspigot.version=26.3-R0.1-SNAPSHOT`: 23 passed. Final artifact rebuilt against baseline 1.21.1; all 11 production classes use Java 21 bytecode (major 65).
- Tests independently cover right-click event flags, main-hand-only activation, both game modes, consumption before parsing, terminal/waxed cases, break guard eligibility and the mining-before-sneaking transition. API fakes are not actual client input.
- Inspected Paper 26.3 build 143 dispatch: PlayerInteractEvent cancellation is checked before Creative destruction and Survival mining. Earlier STOP/delayed mining can bypass this event, motivating the break guard. Current block state is broadcast/acknowledged on the cancelled attack path.

## Deployment evidence

- JAR: `target/BlockBack-1.5.0.jar`, 72,821 bytes.
- SHA-256: `3C4999DFF2A504994D81D050B569107E6A4E0BB47AD3D255A3E335881BAEC535`; installed JAR hash matches.
- Dedicated server: `C:\Dev\TestServers\BlockBack`, localhost `127.0.0.1:25568`, Paper 26.3 build 143.
- Graceful shutdown saved players/worlds. Prior JAR, instructions and fixture builder retained in `backups\before-sneak-left-20261009-234247`.
- Fresh startup loaded BlockBack 1.5.0 and reached `Done` at 23:43:02 local. Existing Windows OSHI performance-counter warning persists; no BlockBack startup error.
- Ten control signs were updated through a sign-only function. Entrance sign NBT confirms `MAIN-HAND AXE` / `Sneak + LEFT`. No sample blocks or inventories were reset. World save confirmed at 23:43:26; temporary forced chunks removed.
- `TESTING.txt` and the dormant fixture builder now use the new gesture. Console remains attached to exec session 50890 for this app session.

## Pending player acceptance

Try sneak-left on copper in Creative and Survival: advance exactly one stage without breaking, dropping items or opening the block. Sneak-right should scrape/unwax interactable copper normally. Confirm terminal/waxed no-op, held attack, already-started mining, chest contents and paired door/chest behavior. Protection-plugin and live Folia checks remain pending; this follow-up does not close the wider milestone.
