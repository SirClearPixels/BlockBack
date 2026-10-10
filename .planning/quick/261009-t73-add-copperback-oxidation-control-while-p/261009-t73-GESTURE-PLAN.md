---
phase: quick-261009-t73-gesture
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - src/main/java/us/ironcladnetwork/blockback/CopperBack.java
  - src/main/java/us/ironcladnetwork/blockback/EventListener.java
  - src/test/java/us/ironcladnetwork/blockback/CopperBackTest.java
  - README.md
autonomous: true
requirements: [COPPER-01, COPPER-02, COPPER-04, TEST-04]
must_haves:
  truths:
    - "Sneak + left-click with a main-hand axe advances eligible copper one stage and consumes the attack, including Creative."
    - "Every right-click and sneak-right-click remains vanilla; ordinary left-click still breaks blocks normally."
    - "An enabled oxidation gesture on waxed or fully oxidized copper consumes the attack without mutation or sound."
    - "Protection denials, permission/default-off preferences, state preservation and rollback remain intact."
  artifacts:
    - path: src/main/java/us/ironcladnetwork/blockback/CopperBack.java
      provides: Main-hand sneak-left activation and attack cancellation
    - path: src/test/java/us/ironcladnetwork/blockback/CopperBackTest.java
      provides: Gesture and pass-through regression coverage
  key_links:
    - from: EventListener.onCopperBlockClick
      to: CopperBack.handle
      via: Existing HIGHEST-priority PlayerInteractEvent listener
---

<objective>
Replace CopperBack's conflicting sneak-right gesture with the user-approved sneak + LEFT_CLICK_BLOCK gesture. Right-click, including sneaking on interactable copper, must remain entirely vanilla. This supersedes only the old gesture/hand contract in 261009-t73-PLAN.md; preserve the completed feature and current version/settings. The user rejected offhand-item requirements and extra mode switching.
</objective>

<context>
@.planning/quick/261009-t73-add-copperback-oxidation-control-while-p/261009-t73-PLAN.md
@src/main/java/us/ironcladnetwork/blockback/CopperBack.java
@src/main/java/us/ironcladnetwork/blockback/EventListener.java
@src/test/java/us/ironcladnetwork/blockback/CopperBackTest.java
@README.md

Current CopperBack.handle accepts RIGHT_CLICK_BLOCK, then denies both uses before conversion and suppresses a second offhand axe event. LEFT_CLICK_BLOCK attacks are main-hand only. Existing test Fixture.click and several hand/passthrough tests encode the superseded right-click contract. Keep the existing conversion/snapshot/paired-block algorithm and settings unchanged. Parent owns build, deployment, test-ground signs/TESTING.txt and final tracking. Parent's inspected Paper bytecode checks event.isCancelled at offsets 286–299 and returns before Creative destruction at 300–323 and Survival mining at 435 onward; this establishes fresh-attack cancellation ordering. The already-mining edge remains a separate check. Do not modify model defaults, milestone requirements or ROADMAP.md.
</context>

<tasks>
<task type="tracer" tdd="true">
  <name>Task 1: Switch the production gesture and prove cancellation/passthrough</name>
  <files>src/main/java/us/ironcladnetwork/blockback/CopperBack.java, src/main/java/us/ironcladnetwork/blockback/EventListener.java, src/test/java/us/ironcladnetwork/blockback/CopperBackTest.java</files>
  <behavior>
    - Eligible main-hand sneak-left changes one stage, returns the successful result and leaves the interaction cancelled before the attack can damage/break the block.
    - Right-click with sneak true and false leaves material, update count, sound count and both original event result flags unchanged for ordinary, interactable, waxed and terminal copper.
    - Non-sneaking left-click, offhand-only axe, an offhand event, missing permission, disabled preference, non-axe, non-copper and air actions pass through unchanged.
    - Waxed/terminal sneak-left is consumed without conversion, damage or sound; parse/update failure remains consumed and retains rollback behavior.
    - Both axes equipped still produce one advancement through the main hand. Existing denial tests and all family/property/door/chest/persistence tests remain applicable.
  </behavior>
  <action>
Write the revised behavioral tests first. Change the activation predicate to Action.LEFT_CLICK_BLOCK and EquipmentSlot.HAND. Remove obsolete offhand oxidation/deduplication behavior: an offhand axe neither enables nor modifies the gesture. Check eligibility and existing block/item DENY states before acquiring the attack. Cancel the eligible attack before terminal/waxed handling or conversion, explicitly preserving both use-result denials and isCancelled semantics. Keep normal right-click completely outside CopperBack's cancellation and sound paths, including when sneaking and both hands hold axes.

Change Fixture.click to generate main-hand left-click attacks and replace the old eachHandCombinationAdvancesOncePerGestureUntilTerminal assumptions with main-hand-only coverage. Rewrite ordinaryClicksLeaveBothHandsAndAllCopperVanillaActionsUntouched and ordinaryClicksAndIneligiblePlayersPassThroughUnchanged so they independently test right-click and sneak-right, plus ordinary left-click; do not merely rename tests after changing one shared fixture. Preserve the existing protection-denial and conversion failure tests. EventListener's existing general right-click handler must retain BarkBack/PathBack/FarmBack behavior.

Use the parent's Paper dispatch/bytecode evidence to establish that cancelling PlayerInteractEvent prevents the Creative destruction path. If that evidence or a live check demonstrates a remaining break path, add the smallest correctly scoped break/damage guard and regression test; do not preemptively cancel all copper BlockBreakEvents. No stage wrapping, alternate mode, cooldown or gesture memory is needed.
  </action>
  <verify>
    <automated>Parent/executor: with JDK 21 run mvn -q "-Dtest=CopperBackTest,WoodCompatibilityTest" test, then mvn -q verify. With the installed Temurin 25 run the current-API suite using "-Dspigot.version=26.3-R0.1-SNAPSHOT". Inspect each exit status independently.</automated>
    <human-check>Using the updated JAR, test eligible sneak-left in Survival and Creative, including a held attack, terminal copper, waxed copper and a chest containing items. Also begin ordinary mining and then sneak while holding attack to examine an already-active break. An acquired oxidation gesture must not break the target, drop items or duplicate contents. Releasing sneak must restore normal left-click breaking.</human-check>
  </verify>
  <done>Main-hand sneak-left is the only oxidation entry point; all right-clicks pass through; consumed attacks are cancelled; revised behavioral regressions pass without changing material/state/persistence behavior.</done>
</task>

<task type="auto">
  <name>Task 2: Align usage text and verify the deployed gesture</name>
  <files>README.md</files>
  <action>
Update active README feature/usage text to sneak + left-click with a main-hand axe. State that ordinary left-click breaks normally, right-click and sneak-right remain vanilla, and waxed/fully oxidized oxidation gestures are consumed without change. Remove the obsolete instruction to disable CopperBack to restore vanilla sneak-right interactions. Preserve the current version and configuration documentation.

Parent updates the existing test-ground instructions and deploys through the already-established server workflow. The server currently has a connected player; parent coordinates any restart without this planner touching the running server. Verify the exact deployed artifact hash, then collect real input evidence for copper stairs/blocks, doors, trapdoors, chests and statues: sneak-left advances; right/sneak-right follow the same scrape/unwax/open/revive behavior as vanilla. Include permission/toggle-disabled normal attacks and a protection-denied location. Record automated results separately from actual client-input observations; a synthetic event alone does not prove Creative break prevention or vanilla interaction behavior.
  </action>
  <verify>
    <automated>Parent: inspect README CopperBack usage with rg -n 'CopperBack|Sneak|sneak|left-click|right-click' README.md and verify the built/deployed JAR hashes match.</automated>
    <human-check>ClearPixels confirms the revised gesture and normal right/sneak-right interactions in the existing test ground; document any Survival/Creative or protection checks not actually performed.</human-check>
  </verify>
  <done>Active instructions match the approved gesture and the final report states which automated, source-dispatch and live-input checks passed.</done>
</task>
</tasks>

<source_audit>
The new direct user decision supersedes COPPER-01's historical right-click wording and preserves COPPER-02's vanilla-interaction intent. Task 1 covers activation, hand semantics, protection and waxed/terminal behavior; Task 2 covers instructions and TEST-04 runtime acceptance. All other CopperBack behavior remains in the existing implementation.
</source_audit>

<output>
Parent records the gesture follow-up results in this quick session's existing summary/testing artifacts. This planner creates only this follow-up plan and performs no code changes, test execution, server operations or commits.
</output>
