---
gsd_state_version: 1.0
milestone: v1.5.0
milestone_name: Server-Owner Controls & CopperBack
status: planning
stopped_at: Phase 5 context gathered
last_updated: "2026-10-10T02:49:16Z"
last_activity: 2026-10-09
progress:
  total_phases: 7
  completed_phases: 1
  total_plans: 3
  completed_plans: 3
  percent: 14
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-05-30)

**Core value:** Players can undo accidental block modifications instantly with a right-click
**Current focus:** Phase 5 — admin commands + tab completion

## Current Position

Phase: 5
Plan: Not started
Status: Ready to plan
Last activity: 2026-10-09 - CopperBack quick task 261009-t73 rebuilt as version 1.5.0 at the user's request; 20 tests passed; live-server acceptance pending.

Progress: [----------] 0% (0/7 phases complete)

## Quick Tasks Completed

| Date       | Slug                  | Summary                                                       |
|------------|-----------------------|---------------------------------------------------------------|
| 2026-05-02 | bstats-integration    | Wire bStats Metrics (plugin ID 31058) with 4 custom charts    |
| 2026-10-09 | [261009-t73-copperback](./quick/261009-t73-add-copperback-oxidation-control-while-p/261009-t73-SUMMARY.md) | Sneak-axe forward oxidation with vanilla normal use preserved; 20 tests pass on Spigot 1.21.1 and 26.3; code through `03fa7dd`; live acceptance pending. |

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Full milestone decision history archived in milestones/v1.4.0-ROADMAP.md.

**v1.5.0 key decisions:**

- ConfigManager follows SoundConfig volatile-immutable-snapshot pattern (NOT ReadWriteLock) — gate hot path must be lock-free across Folia region threads
- WorldGuard bypass check via `player.hasPermission("worldguard.region.bypass.*")` — SessionManager.hasBypass() has Folia scheduler incompatibility
- All WorldGuard type references confined to WorldGuardHook.java — prevents NoClassDefFoundError on servers without WorldGuard
- MockBukkit deferred — paper-api classpath conflict with spigot-api in test scope; all v1.5 test targets are pure-Java logic
- CopperBack defaults to false (`blockback.copper: false` in plugin.yml) — perk/rank framing distinct from bark/path/farm which default to true
- `blockback.admin` defaults to op (consistent with `blockback.reload`)

### Research Flags Open

- **Phase 6 (WorldGuardHook)**: Live-Folia + WorldGuard validation required before shippable. MEDIUM confidence on RegionQuery.testState() Folia thread-safety. SessionManager path explicitly avoided.
- **Phase 8 (CopperBack)**: BlockData-string substitution (slab/stairs/trapdoor/door state) requires mandatory unit test coverage. Door both-halves update requires manual server testing.

Quick task `261009-t73` implements the bounded CopperBack gameplay, persistent toggle and sound support with automated coverage for runtime families, doors, double chests and tile snapshot handling. Live Folia/protection/inventory acceptance and the wider COPPER-06 server-control integration remain open; Phase 8 and the roadmap are not marked complete. The requested one-session GPT-6 Astra ultra override used custom agents; saved GSD model settings were unchanged.

### Pending Todos

- Plan Phase 4 via `/gsd:plan-phase 4`

### Blockers/Concerns

None active. Roadmap defined. Research complete (HIGH confidence overall, MEDIUM on WG/Folia).

## Session Continuity

Last session: 2026-06-15T03:07:51.795Z
Stopped at: Phase 5 context gathered
Resume file: .planning/phases/05-admin-commands-tab-completion/05-CONTEXT.md
Next action: `/gsd:plan-phase 4`
