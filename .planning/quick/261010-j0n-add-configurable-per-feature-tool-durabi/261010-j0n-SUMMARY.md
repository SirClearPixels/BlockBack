---
phase: quick-261010-j0n
plan: "01"
subsystem: gameplay
tags: [bukkit, spigot, folia, durability, configuration]
requires:
  - phase: quick-261009-t73
    provides: CopperBack gesture and paired conversions
  - phase: quick-261010-gj4
    provides: Existing reload callback and optional update checker
provides:
  - Four independent false-by-default tool durability settings
  - Successful-action wear with Bukkit damage and break callback guards
  - Serialized configuration reload and immutable Folia-visible settings
  - Baseline 1.5.0 artifact verified against both supported API targets
affects: [server-owner-controls, gameplay, release-1.5.0]
actuals:
  tokens: 13140
  tasks: 3
  commits: 5
tech-stack:
  added: []
  patterns: [volatile immutable settings snapshot, synchronous region-local item events, guarded inventory writeback]
key-files:
  created:
    - src/main/java/us/ironcladnetwork/blockback/ToolDurability.java
    - src/test/java/us/ironcladnetwork/blockback/ToolDurabilityTest.java
  modified:
    - src/main/java/us/ironcladnetwork/blockback/Blockback.java
    - src/main/java/us/ironcladnetwork/blockback/EventListener.java
    - src/main/java/us/ironcladnetwork/blockback/CopperBack.java
    - src/main/resources/config.yml
    - README.md
    - CHANGELOG.md
    - RELEASE_NOTES_1.5.0.md
key-decisions:
  - "Four exact tool-durability feature switches default false, including absent or invalid settings."
  - "Serialized reload publishes one immutable complete snapshot independently of optional update checking."
  - "Either denied interaction result remains denied; block-only predictive denial has no provenance and is conservatively respected."
  - "Only confirmed successful actions charge; copper pairs charge once and all accepted copper gestures remain intact."
requirements-completed: [QUICK-DURABILITY]
duration: approximately 13min
completed: 2026-10-10
status: complete
---

# Quick Task 261010-j0n: Optional Tool Durability Summary

**Independent server switches apply one normal tool use after successful BarkBack, PathBack, FarmBack or CopperBack actions, with reloadable immutable settings and guarded Bukkit item events.**

## Accomplishments

- Added `tool-durability.barkback`, `.pathback`, `.farmback` and `.copperback`, all Boolean false. Existing configs need no rewrite; invalid values remain false with at most one warning per invalid key during configuration loading.
- Configuration loads before listeners and outside optional update initialization. The existing `/blockback reload` callback serializes configuration read/publication and atomically replaces all four settings for Folia event threads.
- Ordinary damage attempts respect Creative, unbreakable items and Unbreaking. Custom maximum durability, metadata, cancelled/nonpositive/modified item damage, integer overflow, one-item break consumption, stacked tools and baseline client break effects are handled synchronously.
- Real production interaction handlers charge only confirmed material/axis restoration or `CopperBack.Result.CHANGED`. Main/offhand restorations use their actual hand; copper remains main-hand sneak-left-click only. No wear is applied for unchanged/failed/denied/ineligible actions, copper rollbacks, terminal/waxed copper, right-click copper actions or break guards.
- Held stack, selected main-hand slot and inventory contents are checked after external callbacks. Replacements, removal, metadata/amount changes and slot changes prevent stale writeback; event items are never unconditionally restored from clones.
- Updated all three owner documents with the exact config example, reload, permissions/preferences distinction and honest runtime acceptance boundaries.

## Task Commits

1. **Task 1 RED:** `0d65c22` — production BarkBack restores axis/material but fails expected wear 1 versus actual 0.
2. **Task 1 GREEN:** `a4ef91e` — shared durability, settings, initialization/reload and confirmed BarkBack path. All 11 tests passed, proving the tracer before expansion.
3. **Task 2 RED:** `7a26297` — real PathBack/FarmBack and copper-door-pair tests fail expected wear 1 versus actual 0.
4. **Task 2 GREEN:** `aaf452b` — remaining handlers, no-cost gates, sound correctness and 18 durability tests plus all 35 existing regressions.
5. **Task 3:** `c0b1d5b` — owner documentation after two clean successful API builds.

Planning metadata commit and STATE bookkeeping are intentionally owned by the orchestrator. This executor did not stage PLAN, SUMMARY, STATE or ROADMAP, and did not push, retag, deploy, alter the test server or edit a GitHub release.

## Verification

Both offline clean verify builds exited zero using the existing Maven cache:

| API / JDK | CopperBack | ToolDurability | UpdateChecker | WoodCompatibility | Total | Failures / errors / skips |
|---|---:|---:|---:|---:|---:|---|
| Spigot 26.3-R0.1-SNAPSHOT / Java 25 | 22 | 18 | 12 | 1 | 53 | 0 / 0 / 0 |
| Spigot 1.21.1-R0.1-SNAPSHOT / Java 21, final build | 22 | 18 | 12 | 1 | 53 | 0 / 0 / 0 |

Commands: `mvn.cmd -o -q -Dmaven.repo.local=C:/Users/eburt/.m2/repository -Dspigot.version=26.3-R0.1-SNAPSHOT clean verify`, then restore `JAVA_HOME=C:/Program Files/Java/jdk-21` and run baseline `clean verify` with the same cache argument. These are API-contract fake tests with real Bukkit event objects and production handlers, not live Minecraft acceptance. Copper chest pair checks execute when that family exists on 26.3; the absent baseline family condition returns without reporting a skipped JUnit test.

Final artifact: `target/BlockBack-1.5.0.jar`

SHA-256: `51A8B7E5ACB524B3BD1CA3F3F3A33E66BA4C91F97A691D669462E40CA98ED549`

Inspected packaged `plugin.yml`: version `1.5.0`, API version `1.21`, Folia support true. Packaged `config.yml` contains all four false defaults and the existing update-checker setting. All three ToolDurability classes are present; its classfile major version is 65 (Java 21). The final target is the baseline build. No dependencies or version files changed. `git diff --check` passed and focused commits contain no tracked deletions.

Before each clean the exact target was checked as `C:/Dev/BlockBack/target`. The prior JAR and user-facing preview artifacts were preserved under `%TEMP%/BlockBack-durability-261010-j0n`; both preview files were restored after each build, with hashes identical to their backups. The old JAR hash is `862FB5AAF2AC62A214727BA413E063CAC25C42BD6F6E63A1ECA79F02CA87B94C`. Build logs remain in that backup directory as `verify-26.3.log` and `verify-1.21.1.log`.

## Interaction Result Evidence and Limitation

Read-only bytecode inspection of the cached `C:/Dev/TestServers/BlockBack/versions/26.3/paper-26.3.jar` confirmed `ServerPlayerGameMode.useItemOn` begins with both denial flags false for ordinary survival block use, sets the block flag for spectator interactions lacking an appropriate menu/portal, and sets the item flag for item cooldown. `CraftEventFactory.callPlayerInteractEvent` independently maps these flags to `useInteractedBlock(DENY)` and `useItemInHand(DENY)` before dispatch. Baseline PlayerInteractEvent source defines deprecated `isCancelled()` from the block result alone.

Accordingly the restoration listener runs at HIGHEST, evaluates both result channels and never changes DENY to ALLOW. Ordinary eligible survival events remain handled. If a server emits a block-only predictive DENY for a no-op, Bukkit exposes no provenance distinguishing it from protection denial: BlockBack leaves that interaction unchanged and charges nothing. No source/protection heuristic was invented. CopperBack's existing stricter either-DENY gate remains intact.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Copper sound exceptions could hide a completed conversion from durability integration.**
- Found during Task 2.
- `CopperBack.handle` called its success sound callback before returning CHANGED; a thrown sound error could leave an oxidized block without charging.
- With explicit orchestrator ownership approval, added a narrow post-success sound exception catch in `CopperBack.java`. No conversion or gesture behavior changed.
- Regression verifies a real completed conversion plus a throwing callback still costs once. Included in `aaf452b`.

**2. [Rule 3 - Blocking] Shared API-contract server setup could not redefine Bukkit's singleton.**
- Found when running the full test group during Task 2.
- New tests reuse the existing registry when present and clean up only their own isolated registry when run alone, so both targeted and full suites work. No existing test helper or production global state was changed.
- Included in `aaf452b`.

No architectural deviations, authentication gates, dependency installs, unrun automated verifications or known production stubs remain. No new security surface lies outside the plan's configuration, interaction and item-callback trust boundaries.

## Remaining Live Acceptance

Live Paper/Folia checks are still needed for survival and Creative tools; Unbreaking; unbreakable and custom-durability items; visible break animation/sound; both interaction hands; actual protection-plugin denials; paired copper conversion; and `/blockback reload` with update checks enabled/disabled. Inventory-sensitive chest/statue data and wider Folia/protection acceptance from earlier tasks remain separate. The running server was left untouched for orchestrator deployment and acceptance handling.

This quick requirement is complete at the implementation and automated-verification level. It does not mark wider milestone phases or player-observed runtime acceptance complete.

## Self-Check: PASSED

## Orchestrator follow-up

- Reviewed the completed source and integration changes; retained the tested implementation.
- Backed up the previous server JAR/config, installed the matching baseline JAR and added the four explicit false settings to the existing test config without replacing other values or world/player data.
- Paper 26.3 build 143 enabled BlockBack 1.5.0 and reached Done at 13:58:59 America/Chicago on 2026-10-10, console session 21216, address `127.0.0.1:25568`. Startup is verified; actual player wear, break feedback and Folia acceptance remain pending.
- Updated STATE tracking and prepared the existing v1.5.0 GitHub draft with the durability settings and 53-test evidence. Keep the release unpublished while aligning its source tag and JAR.

Confirmed both created source/test files, the on-disk SUMMARY, final baseline JAR and all five task commits exist. Working tree is clean; the ignored SUMMARY remains on disk for orchestrator bookkeeping.
