# Graph Workspace Boundary Separation Evidence

## Real-case regression
- Frozen fixture: full-precision positions and captured MST from the 2026-09-12 probe capture.
- Before: siblingOverlap=true, mst=(-13.54808605241621, 0.0).
- After: rounds=1, hullResidualViolations=0, both pins exact, mst=(0.0, 0.0), ancestor containment holds.
- Test: `:freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.BoundarySeparationCorrectionShould`.
- Recorded by source-plan Task 12 (`frozenRealCaseViolatesBeforeAndSettlesInOneRoundAfterCorrection`,
  plus `canonicalKeysUseTheCapturedRealHullStrings`); class result on the full-suite run: 33 tests,
  0 failures, 0 errors.

## Pipeline adversarial fixture (red before / green after)
- Raw engine settle after the seeding request: siblingOverlap=true, mst=(-13.54808605241621, 0.0), the unpinned Regularity node defines the Axioms facing edge.
- First published frame after the final request: hullViolationsDetected=1, rounds=1, boundaryVerified=true; later frames rounds<=2, verified||covered, I6 bijection.
- Idle reached with rms/max and delta within the pinned slack; worstMapDisplacement recorded.
- Test: `:freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.BoundarySeparationShould`.
- Recorded by source-plan Tasks 17 and 18 (`reproducedCasePipelineFixtureCrossesAtTheRawSettle`,
  `reproducedCasePipelineFixturePublishesRepairedFrames`,
  `reproducedCaseSettleSequenceReachesIdleWithStableCorrectionDeltas`); class result on the
  full-suite run: 17 tests, 0 failures, 0 errors.

## Section 7 measurements
1. rounds <= 2 on every corpus frame; reproduced case rounds == 1.
   Measured: worst `rounds()` over the settle-sequence corpus = 1 (idle reached at step 8 of the
   1000-step budget); the reproduced real-case fixture settles in 1 round; the first published
   pipeline frame after the final request reports 1 round.
2. Terminal cleanliness: idle frame hullResidualViolations == 0; no ANCESTOR_ESCAPE residual; no zero-MST overlap counted.
   Measured: the idle frame has `hullResidualViolations() == 0` with `residualHullPairs()` empty; no
   `ANCESTOR_ESCAPE`/`STRUCTURAL_ESCAPE` residual was produced by any corpus frame; zero-MST overlaps
   are not overlap violations and were not counted.
3. Stability: idle rms <= 0.0505, max <= 0.10; deltaRms <= 0.05, deltaMax <= 0.10; no ROUND_LIMIT conflict; no BoundarySeparationException.
   Measured: idle `rms = 0.01809045649992739` (<= 0.0505), idle `max = 0.04541312821384812`
   (<= 0.10), `deltaRms = deltaMax = 0.0015358157166929232` (<= 0.05 / 0.10); `ROUND_LIMIT` never
   appears on the corpus and no `BoundarySeparationException` occurs.
4. Cross-map compactness: worstMapDisplacement = 13.570231901702556.
5. Strict performance gate: `reference-2000-5000` full-worker p50/p95/p99/max = 29,121,155 / 52,334,029 / 81,235,776 / 84,632,594 ns; correction overhead split = plan p50/p95/max = 91,294,643 / 123,866,731 / 155,878,250 ns, apply p50/p95/max = 0 / 0 / 0 ns.
   Recorded ledger `freeplane_plugin_graph/build/graph-performance/performance-ledger.csv`
   (`reference-2000-5000,full-worker,400,300,29121155,52334029,81235776,84632594,500000000,100000000,0,0,true`;
   ledger SHA-256 `735a466bd46837223d22a0793d4020af130d2a2fba322a120f91c05280ec2ef3`). The row passes
   on the strict-budget limb; the supplementary baseline comparison is 0.67% above the 51,985,400 ns
   baseline. `accepted-batch-first-frame` (p95 81,250,040 ns) also passes its 150,000,000 ns strict
   budget. `force` and `edt-swap` remain the documented pre-existing environment waivers (they fail
   the same way on the untouched baseline `aa6ac48b02`), not boundary-separation regressions. The
   ledger's `correction` stage meters the plan + apply split recorded in the focused profile in
   `docs/superpowers/plans/2026-09-12-graph-boundary-separation-performance.md`.
- Escalation decision: no force-side clearance change.

## Full-suite verification
- Command: `gradle :freeplane_plugin_graph:test` (Zulu 21.0.8, Gradle 9.0.0).
- Result: BUILD SUCCESSFUL; 83 test classes, 1001 tests, 0 failures, 0 errors, 3 environment skips
  (`GraphPluginIntegrationShould` class assumption; `WorkspaceUriResolverShould` Windows-path and
  two-filesystem-roots assumptions).
- Required classes all present and green: `BoundarySeparationCorrectionShould` (33 tests),
  `BoundaryConflictShould`, `BoundarySeparationDiagnosticsShould`, `CanonicalLayoutKeysShould`,
  `BoundaryInvariantAssertionsShould`, `LayoutWorkerShould`, `LayoutSettleLoopShould`,
  `GraphUpdateCoordinatorShould`, `GraphWorkspaceCommandAcceptanceShould`, `GraphStreamBoundaryShould`,
  `BoundarySeparationShould` (17 tests), `PerformanceTripwiresShould`.

## Manual acceptance checklist (operator)
- [ ] Full build, then `BIN/freeplane.sh`.
- [ ] Open the workspace containing `math.fpg`, open Graph Workspace, select the ZFC map.
- [ ] Inspect the `ZFC -> Basic Definitions and Theorems` region: the Axioms and Basic Definitions hulls must not cross.
- [ ] Confirm the layout reaches idle and both pins are at their stored positions.
