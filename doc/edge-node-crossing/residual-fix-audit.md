# Edge-node crossing residual fix — read-only frontier implementation audit

## VERDICT

`CHANGES REQUIRED`.

The implementation matches the approved routing and relocation contracts at the source level. The protected source blocks and settled reference are byte-identical to the delivery base, and mutation probes A–E were caught. Approval is blocked by two substantive findings: the required per-ring deduplication guard has a surviving mutation, and the recorded real-workspace acceptance is still pending on different input and non-idle runs. Two lower-severity hygiene findings are also recorded.

Audit scope:

- Base: `bfaed5611d170b5b0b3dc93919685bd6c54035c5`
- Head: `6a92e53acc`
- Module: `freeplane_plugin_graph`
- Runtime/source target: Java 8
- Audited production files: `EdgeRouter.java` and `EdgeConflictCorrection.java`
- Working tree source was kept read-only. All mutations were applied only in `/tmp/freeplane-edge-audit-mutants`.

## CONTRACT FIDELITY

### Routing

`EdgeRouter.java:138-185` preserves the canonical edge loop, straight-path attempt, midpoint bow ladder, dogleg ladder, and fallback branches. The control-position group is appended after the dogleg group at `EdgeRouter.java:178-185`.

`EdgeRouter.java:315-342` iterates `CONTROL_FRACTIONS` in the specified order and `OFFSET_FRACTIONS` in the specified signed order. Its gates are ordered as required: maximum bend at lines 324-327, analytic ratio at lines 328-330, sampled candidate length at lines 331-335, and the full `clears(...)` predicate at lines 336-340. The new group calls `clears(...)`; the existing implementation of that predicate delegates to `clearsDiscs(...)` and then checks edge paths at `EdgeRouter.java:449-471`, which is the required full-clearance path.

The protected `EdgeRouter` symbols were compared against the base source and were unchanged: `BOW_FRACTIONS`, `DOGLEG_FRACTIONS`, `bestRung`, `analyticRatio`, `bowPath`, `doglegPath`, `MAX_BEND`, `ROUTE_LENGTH_CAP`, and `ROUTE_EPSILON`. The new control-position ratio implementation is separate from those protected symbols.

The ratio test at `EdgeRouterShould.java:23-45` independently invokes all 24 control/offset sign combinations and compares them with the normative table at a `1e-9` tolerance. The off-midpoint fixture at `EdgeRouterShould.java:47-64` checks the selected control point, sampled clearance, route shape, and route counters. The ordering fixtures at `EdgeRouterShould.java:67-112` caught both ordering mutations described below.

### Relocation

`EdgeConflictCorrection.java:142-152` retains the stage-0 pool and enters stage 1 only after stage 0 fails to accept. `acceptedThisRound` guards the stage loop, so a successful stage-0 repair cannot leak into stage-1 expansion. The `DETECTION_STOP`, `verificationWorkAtCap()`, attempt-cap, and `reconcileStopFlags()` branches remain present at `EdgeConflictCorrection.java:184-201`, `225-251`, and `792-803`.

`EdgeConflictCorrection.java:374-403` records the selected family together with its originating conflict. `EdgeConflictCorrection.java:652-671` expands only those recorded families, skips crossing conflicts at lines 659-661, and creates a fresh `CandidateIdentity` set for each ring at line 657. The ring target is `disc.radius + EDGE_NODE_CLEARANCE + ring * MAX_CLEARANCE_STEP` at `EdgeConflictCorrection.java:588-590`, with rings 1 through 4 selected at lines 656-657.

The `Candidate` and `CandidateIdentity` class bodies are byte-identical to the base. Their extracted SHA-256 values are respectively `c4380c75f7c9eaf7a7860891cc223363168273b904bfd523167d88a91951fae1` and `c954d09458fd2aa1f89a02f7ab075a8ba5dd6dd99f2720b7cd177207c7579f8b` in both revisions.

### Protected artifacts and change scope

`freeplane_plugin_graph/src/test/resources/performance/settled-reference.csv` is unchanged from the base. Its required SHA-256 is present: `e9e988b7967ea6bb4f503dfb4c5af7d3e74a3328e28e0987e71ff0f0b8ac0578`.

The range contains 25 changed paths and no deletions. The only modified production files are the two audited Java files. The other existing files changed in the range are the declared routing/relocation tests and fixture; added files are declared test resources, probes, replay sources, specifications, plans, and acceptance evidence. No unapproved production file was found.

## FINDINGS

### MAJOR-1 — Per-ring deduplication has a surviving mutation

**Location:** `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/EdgeConflictCorrection.java:652-667`; related test `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/EdgeConflictCorrectionShould.java:496-503`.

**Evidence:** In the disposable checkout, mutation F replaced the per-ring guard

```java
if (retained.add(new CandidateIdentity(candidate))) {
    expanded.add(candidate);
}
```

with unconditional `expanded.add(candidate)`. The named `deduplicatesIdenticalAlternativesFromOverlappingChords` test remained green, and the complete `EdgeConflictCorrectionShould` class remained green with 70 tests. The disposable checkout was restored and was clean afterward.

The existing overlapping-chords test exercises the earlier global ring-0 family deduplication, so it does not construct two selected families that collide only during a later ring. The surviving mutant can therefore add duplicate later-ring candidates and consume verification work more than once, potentially changing bounded-search outcomes.

**Required correction:** Add a falsifiable fixture with at least two selected edge-over-node families whose ring-1 or later candidates share a `CandidateIdentity` while their retained ring-0 candidates are distinct. Assert the exact candidate/verification outcome and rerun the exact mutant so removal of the per-ring guard fails. Keep the production per-ring guard and its per-ring set semantics.

### MAJOR-2 — Recorded workspace acceptance is pending and does not reproduce the archived input

**Location:** `freeplane_plugin_graph/doc/edge-node-crossing-residual/workspace-acceptance.md:3,42-71,172-177`.

**Evidence:** The committed acceptance record reports 204 projected set-theory nodes, 11 graph groups, and 29 discs, versus the archived 171, 10, and 28. It identifies the extra disc as `1/ID_1271861869`. Neither the candidate nor delivery-base replay reached idle before the unchanged 600-second bound: the current candidate stopped at 32,388 steps and the delivery base at 31,680. The current published paths were clear, but the record explicitly states that this does not establish the original 178-step replay or Goals 1/2 preservation.

The `RealWorkspaceCrossingShould` feature run was otherwise structurally valid but skipped when `GRAPH_REPORTED_MAP_ROOT` was absent. That skip cannot serve as recorded-workspace acceptance evidence.

**Required correction:** Reproduce the archived map state and exact 171/10/28 input, then rerun both candidate and delivery-base controls under the unchanged replay protocol. The candidate must reach the recorded settled boundary and preserve the exact archived geometry, repair, counter, and Goal 1/2 assertions before this acceptance item can be closed. If the archived input is unavailable, the acceptance authority must explicitly re-scope the requirement and publish new frozen expectations; the current live replay cannot be promoted to a pass.

### MINOR-1 — Newly nested production block is not indented to the repository’s four-space style

**Location:** `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/EdgeConflictCorrection.java:153-201`.

**Evidence:** The newly added stage `for` loop begins at line 146, but the body of its nested `while` remains at the former indentation level: line 155 is indented 16 spaces, the same as the `while` at line 153, rather than 20 spaces. The nested body through line 196 and the stage-loop close at line 201 have the same structural indentation problem. Java semantics are unaffected, but this violates the repository’s four-space style in changed production code.

**Required correction:** Re-indent the nested `while` body and the corresponding closing brace to reflect the added stage loop, then run the normal formatting/diff checks.

### MINOR-2 — New specification contains trailing whitespace

**Location:** `docs/superpowers/specs/2026-10-26-edge-node-crossing-residual-spec.md:3-6`.

**Evidence:** `git diff --check` exits 2 and reports trailing whitespace on the four new header lines. The defect is documentation-only and does not affect runtime behavior.

**Required correction:** Remove the trailing whitespace and rerun `git diff --check`.

## MUTATION PROBES

All probes were run from the detached disposable checkout `/tmp/freeplane-edge-audit-mutants`, never from the audited worktree. A first control-position probe attempt was discarded because Gradle was launched from the audited checkout; only the corrected disposable rerun is counted.

| Probe | Disposable mutation | Relevant test(s) | Result |
| --- | --- | --- | --- |
| A | Move the control-position group before the midpoint bow ladder | `EdgeRouterShould.rejectsMovingControlPositionGroupAheadOfMidpointBows` | **CAUGHT**; named test failed at its expected path assertion. |
| B | Move the control-position group before the dogleg ladder | `EdgeRouterShould.keepsTheDoglegLadderAheadOfTheControlPositionGroup` | **CAUGHT**; named test failed at its expected dogleg assertion. |
| C | Replace the control-position final `clears(...)` call with `clearsDiscs(...)` | `EdgeRouterShould.usesTheFullClearPredicateForControlPositionCandidates` | **CAUGHT**; named test failed at its expected straight/fallback assertion. |
| D | Remove `family.conflict.isCrossing()` from ring expansion | `EdgeConflictCorrectionShould.rejectsACandidateThatIntroducesANewCrossing` | **CAUGHT**; named test failed at the crossing assertion. |
| E | Remove the `!acceptedThisRound` stage-loop guard | `EdgeConflictCorrectionShould.startsTheNextRoundWithFreshStageZeroSelection` | **CAUGHT**; named test failed at the expected round/verification result. |
| F | Remove the per-ring `CandidateIdentity` dedup guard | `deduplicatesIdenticalAlternativesFromOverlappingChords`, then all `EdgeConflictCorrectionShould` tests | **SURVIVED**; the named test and all 70 correction tests passed. This is MAJOR-1. |

## NOTES

### Focused test evidence

The recorded unmutated disposable feature runs passed:

- `EdgeRouterShould`: 30 tests, 0 failures, 0 errors, 0 skipped.
- `EdgeConflictCorrectionShould`: 70 tests, 0 failures, 0 errors, 0 skipped.
- `PerformanceTripwiresShould`: 17 tests, 0 failures, 0 errors, 0 skipped.
- `RealWorkspaceCrossingShould`: 1 test, 0 failures, 0 errors, 1 skip because `GRAPH_REPORTED_MAP_ROOT` was unset.
- `verifyGraphBundle`: exit 0 with the Java 8 bytecode/OSGi gate.

The ratio table, off-midpoint clearance, control ordering, max-bend, full-clear predicate, stage-1 search, stop boundary, A1/A2/A3 search record, and settled-reference preservation checks are therefore present and independently falsifiable at the focused-test level. The A2/A3 test evaluates all 59 blocker samples, five ring positions, and six targets per position, for 1,770 observations. Its committed result is the explicitly declared search-limit outcome rather than a claimed A2/A3 witness.

### Full-suite and environment limitations

The recorded parent module run contains four inherited failures and is not a clean full-suite acceptance result: two headless `AccessibleGraphCanvasShould` `JFrame` tests, the historical `InitialSpacingAcceptanceShould` driver, and its `InitialSpacingShould` wrapper. The historical driver rejects the already-landed `EdgeRouter.java` production difference before this feature’s test changes can affect it. The recorded parent totals are 1,453 tests, 4 failures, and 18 skips; the final acceptance record reports 1,455 tests, 4 failures, and 19 skips after the two added tests. These failures were attributed to the parent/environment or historical driver, not to the residual-fix changes.

The current workspace replay’s path-clearance audit is useful evidence for the non-idle final frame: it checks all 12 paths and 324 disc pairs and reports zero clearance violations. It is not evidence of the archived settled replay, as documented at `workspace-acceptance.md:73-86`.

### Repository policy

The changed Java files contain no debug output, compatibility flag, commented-out executable branch, or second active implementation path. The only matching explanatory comment in the changed production/test scan is the finite-search explanation at `EdgeConflictCorrectionShould.java:449-451`. The build configuration retains Java 8 source/target compatibility, and the feature tests use JUnit 4. The eight implementation commits are linear, imperative/task-scoped, and include the task identifier in their subjects. The audited worktree was clean before the report was created and all disposable mutation sources were restored.

## SEVERITY COUNTS

- BLOCKER: 0
- MAJOR: 2
- MINOR: 2
- NOTE: 0

AUDIT VERDICT: CHANGES REQUIRED
