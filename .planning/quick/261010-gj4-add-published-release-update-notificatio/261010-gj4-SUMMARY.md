---
phase: quick-261010-gj4
plan: "01"
subsystem: updates
tags: [java21, github-releases, gson, folia, junit]
requires:
  - phase: quick-261009-t73
    provides: Existing 1.5.0 release baseline, commands and API-contract fixtures
provides:
  - Bounded asynchronous published stable release checks at startup and daily
  - Cached permission-gated admin join notices and reloadable opt-out
  - Deterministic transport, event, reload and lifecycle regression coverage
affects: [release-1.5.0, admin-configuration]
actuals:
  tokens: 10739
  tasks: 3
  commits: 5
tech-stack:
  added: []
  patterns: [immutable volatile offer, lifecycle generation invalidation, bounded HTTP body subscriber]
key-files:
  created:
    - src/main/java/us/ironcladnetwork/blockback/UpdateChecker.java
    - src/main/resources/config.yml
    - src/test/java/us/ironcladnetwork/blockback/UpdateCheckerTest.java
  modified:
    - src/main/java/us/ironcladnetwork/blockback/Blockback.java
    - src/main/java/us/ironcladnetwork/blockback/CommandManager.java
    - src/main/resources/plugin.yml
    - README.md
    - CHANGELOG.md
    - RELEASE_NOTES_1.5.0.md
key-decisions:
  - "Wait on complete bounded HTTP response for at most the remaining 10-second request budget; request timeout alone is insufficient for a stalled body."
  - "Use HttpClient.shutdownNow and executor.shutdownNow without waiting on the server thread."
  - "Expected polling failures log short FINE diagnostics; available versions log once at INFO per plugin lifetime."
  - "Read config only on initialization/reload callers; invalid enabled values warn and use true without disabling gameplay."
requirements-completed: [QUICK-261010-GJ4]
coverage:
  - id: D1
    description: "Published stable offers and cached authorized join notices"
    requirement: QUICK-261010-GJ4
    verification:
      - kind: integration
        ref: "UpdateCheckerTest#publishedReleaseFlowsThroughCheckCacheAndRealJoinHandler"
        status: pass
      - kind: unit
        ref: "UpdateCheckerTest#numericVersionsAndMalformedPublicationAreConservative"
        status: pass
    human_judgment: false
  - id: D2
    description: "Daily scheduling, reload opt-out, shutdown and transport bounds"
    requirement: QUICK-261010-GJ4
    verification:
      - kind: integration
        ref: "UpdateCheckerTest (12 deterministic tests), Spigot 1.21.1 and 26.3 clean verify"
        status: pass
    human_judgment: false
  - id: D3
    description: "Live Spigot/Paper/Folia operator, permission grant/denial, reload and shutdown behavior"
    verification: []
    human_judgment: true
    rationale: "API-contract fakes cannot establish live server or Folia acceptance; parent owns runtime testing."
duration: 12min
completed: 2026-10-10
status: complete
---

# Phase quick-261010-gj4 Plan 01: Published-release update notices Summary

**BlockBack 1.5.0 now checks stable GitHub releases asynchronously at startup and daily, with cached admin join notices, live opt-out and bounded shutdown.**

## Orchestrator runtime and release follow-up

- Reviewed the implementation, lifecycle wiring, packaging and existing deterministic test results. No further source changes were needed.
- Ran the packaged JDK transport against the real unauthenticated public GitHub endpoint: HTTP 200 in 812 ms. A simulated installed version of 1.0.0 produced the expected 1.4.1 release notice and fixed GitHub URL. Actual version 1.5.0 produced no offer, correctly ignoring the still-unpublished 1.5.0 draft. This standalone probe is real network evidence, not player-chat evidence.
- Stopped the existing Paper test server cleanly, retaining its world and player data. Prior JAR backed up to `C:\Dev\TestServers\BlockBack\backups\before-update-checker-20261010-121136`.
- Installed the final JAR with matching SHA-256 and restarted Paper 26.3 build 143 on `127.0.0.1:25568`. BlockBack 1.5.0 loaded successfully; `Done` at 12:11:56 America/Chicago on 2026-10-10. New `plugins/BlockBack/config.yml` contains `update-checker.enabled: true`. No checker initialization warning occurred. Existing Windows OSHI performance-counter warning is unchanged.
- Server remains running with console session 25893. The current published version is older than the installed version, so no admin update message is expected yet. In-game permission/chat delivery and live Folia behavior remain pending; join/reload/cancellation are covered by deterministic API tests.
- The existing v1.5.0 GitHub release was rechecked as a draft before release alignment. Preserve draft status while refreshing its source tag, notes and attached JAR; do not publish a fake release for testing.

## Accomplishments

### Follow-up: logo-inspired RGB notices (2026-10-10)

- Admin join notices now use bold emerald `#35FF87` for the prefix/new version, mint `#B8F5CB` for message text, sage `#91AF9B` for the installed version and green `#00D978` for the release URL. Console output stays plain text; chat formatting resets after the notice.
- Extended the existing join-handler regression to verify RGB formatting, reset termination and content parity with plain console output. All 35 tests pass on both Spigot 26.3/Java 25 and the final Spigot 1.21.1/Java 21 build.
- Latest artifact supersedes the earlier checksum below: `target/BlockBack-1.5.0.jar`, SHA256 `49136599C78108F136F507F10F15CD290136F5AC18189BAC759C53D4E4A81718`.
- Deployed the matching JAR to the existing test server after a clean shutdown and backup. Paper 26.3 loaded BlockBack successfully at 12:40:52 America/Chicago; console session 19956. Actual player-visible color acceptance remains pending because no newer published release currently exists.
- Keep v1.5.0 unpublished while aligning the existing draft, tag and attached JAR with this follow-up.

- Fixed unauthenticated HTTPS GET for the public project repository; redirects disabled, 10-second total budget including body completion, 64-KiB streamed byte limit independent of Content-Length, and explicit cancellation on timeout/interruption.
- Explicit publication and stable-version validation, overflow-safe numeric ordering, ignored build metadata precedence, and fixed release links constructed from validated tags. Equal, older, draft, prerelease, unpublished, malformed and unsupported versions produce no offer.
- One owned daemon polling scheduler; repeated enabled reloads preserve its task. Disable cancels and clears the offer; re-enable creates a new generation. Closed/retired workers cannot publish or announce late results. Failed polls clear stale offers and recover on later polls.
- `blockback.update` defaults to operators and uses `hasPermission` for explicit grants/denials. Startup workers never reference players or Bukkit config. Each authorized join uses the latest completed cache; earlier joins do not cause HTTP or delayed player delivery.
- Default `config.yml` is saved without overwriting existing files. The existing permission-checked reload branch reloads sound/player data and invokes the narrow config callback. Existing constructors remain compatible.
- README, existing 1.5.0 changelog and release notes explain opt-out, timing, cached join limitations, no file installation and lack of retroactive notices in older jars.

## Task Commits

1. **Task 1 RED:** `89a278e` — test(quick261010-gj4-01): cover release discovery and authorized join notices
2. **Task 1 GREEN:** `314fa3e` — feat(quick261010-gj4-01): notify authorized joins of published stable updates
3. **Task 2 RED:** `debb956` — test(quick261010-gj4-01): cover daily polling reload races and bounded HTTP failures
4. **Task 2 GREEN:** `6215da1` — feat(quick261010-gj4-01): add daily checks and reloadable opt-out with race-safe shutdown
5. **Task 3:** `7dc7559` — docs(quick261010-gj4-01): document 1.5.0 update notices and rollout limits

Planning artifacts and STATE/ROADMAP updates are intentionally left to the parent orchestrator. No planning files were staged or committed by this executor.

## Verification

All Maven commands used the installed Maven executable, `-o -q` and `-Dmaven.repo.local=C:/Users/eburt/.m2/repository`. No packages were downloaded or dependencies added.

| Verification | Result |
| --- | --- |
| Task 1 RED targeted test compile | Failed as expected: UpdateChecker absent |
| Task 1 GREEN targeted test | 2 passed |
| Tracer feedback targeted rerun after commit | 2 passed |
| Task 2 RED targeted test compile | Failed as expected: applyEnabled and reload callback constructor absent |
| Task 2 full suite | 35 passed: 22 CopperBack + 1 WoodCompatibility + 12 UpdateChecker |
| Java 25, `-Dspigot.version=26.3-R0.1-SNAPSHOT clean verify` | 35 passed; 0 failures, errors or skips |
| Java 21, default baseline `clean verify` (last) | 35 passed; 0 failures, errors or skips |
| Packaged plugin.yml | Version 1.5.0; blockback.update default op |
| Packaged config.yml | update-checker.enabled true |
| Packaged UpdateChecker.class | Major version 65 (Java 21) |
| git diff --check | Passed |
| Stub/TODO/FIXME/skip scan of new implementation/tests | None |

Tests exercise the production poll, bounded subscriber, timed full-body transport wait, real PlayerJoinEvent handler and actual CommandManager reload branch. A real injected scheduler confirms off-caller-thread network work and no delivery to a player who joined before completion. Manual schedulers and bounded latches cover timing, repeated enable, deduplication, failure recovery and disable/re-enable/shutdown races without public requests or arbitrary sleeps. These are API-contract tests, not live player/Folia evidence.

Final retained baseline artifact: `target/BlockBack-1.5.0.jar`, 87,508 bytes, SHA256 `FBD5FDE9E160612DD7BAAF7BE5BE048D8C98627AA58A83E7F9005CB538297F8A`.

Before either Maven clean, the target was verified to resolve exactly to `C:\Dev\BlockBack\target`. The earlier release artifact was preserved at `C:\Users\eburt\AppData\Local\Temp\BlockBack-1.5.0-before-update-407afa4b-d5a6-4551-b575-77dd289639ec.jar`, SHA256 `3C4999DFF2A504994D81D050B569107E6A4E0BB47AD3D255A3E335881BAEC535`.

## Decisions Made

Use the existing transitive Gson API and Java 21 HTTP/concurrency facilities. Keep the checker scoped to release discovery, with no downloads or new dependencies. Console notices are deduplicated by numeric version across toggles; expected outages use FINE diagnostics to avoid routine console spam. Joins synchronize briefly with disable so a retired cached notice cannot be sent after disable returns. Shutdown requests cancellation without waiting for executor or HTTP termination.

## Deviations from Plan

- Parent explicitly extended the documentation deliverables to include RELEASE_NOTES_1.5.0.md. The existing release title, CopperBack content and acceptance caveats were preserved.
- Local Maven's default dependency-cache resolution under the restricted environment initially failed. Explicitly selecting the existing user cache and running the authorized checks with escalation fixed access; no installs/substitutions were attempted.

No architectural or gameplay deviations. Actuals are realized Git diff characters divided by four (ceiling), on the estimate's scale.

## Deferred Runtime Acceptance

Live operator/default permission, explicit permission grant/denial joins, config disable/re-enable and shutdown smoke checks on supported Spigot/Paper/Folia remain for the parent. No live acceptance, deployment, GitHub release/tag/asset mutation, push or model-setting change was performed. This quick task does not mark the wider server-control milestone or unrelated roadmap work complete.

## Documentation Sources

The implementation was checked against [Oracle Java 21 HttpClient](https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/HttpClient.html), [BodySubscriber](https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/HttpResponse.BodySubscriber.html) and [GitHub release endpoint documentation](https://docs.github.com/en/rest/releases/releases#get-the-latest-release). Context7 MCP/CLI was unavailable; official primary documentation was used.

## Self-Check: PASSED

New production/config/test artifacts and this SUMMARY exist on disk. All five listed commit objects exist. Final baseline packaging and 35-test suite passed. Repository status is clean outside ignored planning artifacts.
