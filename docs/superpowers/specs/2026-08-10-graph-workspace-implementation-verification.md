# Graph Workspace Implementation Verification

Date: 2026-08-24

This document records the implementation-phase verification for Graph Workspace Task 42 and its correction round. The source base for the implementation was `8f7c11f8797bbebd307c97d5bce5a8ad620d74b0`; the correction round was run from implementation commit `f8e5f23be3a1f084bf4e9f03b0a73a9f4839ab84`. The task changes are limited to the Gradle smoke wiring, three executable probes, this report, and the two declared PNG artifacts.

## Environment

- Host: `Arch-MSSD0`, Linux `7.1.4-arch1-1`, `x86_64`.
- CPU: 22 logical CPUs; memory: 62 GiB RAM, 63 GiB swap.
- Java: `/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin/java`, Zulu OpenJDK `21.0.8+9-LTS`.
- Gradle: `9.0.0`.
- Source and target compatibility: Java 8 (class-file major 52).
- Locale: `user.language=en`, `user.country=US`.
- UI probes: `java.awt.headless=true`; the launch probe also uses the production shell script and an isolated temporary user/configuration directory.

## Bundle And License

The built bundle has these verified headers:

- `Bundle-SymbolicName: org.freeplane.plugin.graph`
- `Bundle-Activator: org.freeplane.plugin.graph.Activator`
- `Bundle-ClassPath: .,lib/plugin-1.13.4.jar,lib/gs-core-1.3.jar,lib/pherd-1.0.jar,lib/mbox2-1.0.jar`
- `Require-Bundle: org.freeplane.core`
- no generated `Import-Package` or `Export-Package` header; the configured instruction remains `nothing.*` and the graph bundle uses private packages.

Only the approved unchanged GraphStream artifacts are embedded:

| Artifact | SHA-256 |
| --- | --- |
| `gs-core-1.3.jar` | `2d6a6f92f86c624fcbf468fc7e9cb9c8e3fb7e14c72ad578edb04cc36b0b66cd` |
| `pherd-1.0.jar` | `9e74f3702d13756faece5987147c937c09b6837a38ed32199f59c26697b94230` |
| `mbox2-1.0.jar` | `3c2db334867211f385a2d62d061818268443f361381f78bbc53f9e897e145983` |

`verifyGraphBundle` checks the exact three-artifact set, class-file major version, the canonical `META-INF/LICENSES/LGPL-3.0.txt` text, and `META-INF/NOTICE.graphstream.txt` attribution, checksums, and source link. No `gs-ui`, Scala, wrapper bundle, launcher modification, or new package export was added.

## Commands And Results

The exact smoke command from the task brief passed:

```text
gradle :freeplane_plugin_graph:graphOsgiSmoke :freeplane_plugin_graph:graphUiEvidence :freeplane_plugin_graph:freeplaneLaunchSmoke -PTestLoggingFull
```

The probes also passed independently:

- `graphOsgiSmoke`: used the actual `BIN/framework.jar` and `BIN/props.xargs`; installed the core and graph bundle directories; observed the graph bundle `ACTIVE`; verified the three embedded jar entries; loaded GraphStream classes from `gs-core`, `pherd`, and `mbox2`; created a two-node/one-edge graph; calibrated SpringBox at quality `0.10`; stopped the framework; and observed no live thread whose name starts with `freeplane-graph-`.
- `graphUiEvidence`: constructed the real `GraphWorkspaceWindowModel` shell on the EDT, attached the menu bar and content to one root panel, loaded deterministic two-map state, dispatched toolbar, search, mouse, wheel, and keyboard events, painted and validated the 1280 x 800 desktop workspace, then validated a 900 x 900 narrow workspace in memory. The marker artifact is generated separately by invoking the production `GraphGroupMarkerPainter` with a real marked `NodeModel` and a mocked `NodeView`; exact coral pixels and enclosure geometry are asserted before the PNG is written.
- `freeplaneLaunchSmoke`: started `BIN/freeplane.sh` without `org.freeplane.exit_on_start`, passed the production `-XQuitAction` menu request, used a disposable profile with automatic map creation disabled, waited for the graph bundle `Started:` log, observed child/process-table termination within the normal 15-second bound, and recorded `normalQuitRequested=true`, `termRequired=false`, `exitCode=0`, and `childProcessTerminated=true`. No parent-JVM child-thread enumeration is used.

The strict performance command passed:

```text
gradle --no-daemon --no-parallel :freeplane_plugin_graph:graphPerformanceDiagnostic -PgraphStrictPerformance -PTestLoggingFull
```

It produced `freeplane_plugin_graph/build/graph-performance/performance-ledger.csv` with six scenarios and twelve stages each: 72 rows, all `pass=true`, `failureCount=0`, and `discardCount=0`. The reference `reference-2000-5000` workload used 400 warm-up and 300 measured samples. Its gated p95 values were force `16.830455 ms` (strict limit `50 ms`), full worker `7.142015 ms` (strict limit `100 ms`), EDT swap `0.630658 ms` (strict limit `2 ms`), and accepted-batch-first-frame `128.784724 ms` (strict limit `150 ms`). The complete performance evidence is [the existing performance report](2026-08-10-graph-workspace-performance-report.md); the current generated ledger is the task output under `freeplane_plugin_graph/build/graph-performance/`.

The two acceptance classes passed with zero failures and zero errors:

```text
gradle --no-daemon --no-parallel :freeplane_plugin_graph:test \
  --tests 'org.freeplane.plugin.graph.integration.GraphWorkspaceModelAcceptanceShould' \
  --tests 'org.freeplane.plugin.graph.integration.GraphWorkspaceCommandAcceptanceShould' \
  -PTestLoggingFull
```

The result was 18 model tests plus 14 command tests. The command/acceptance classes cover all 29 numbered scenarios below (Scenario 22 has two separately named assertions).

The required module command was rerun after the corrections:

```text
gradle --no-daemon --no-parallel :freeplane_plugin_graph:clean :freeplane_plugin_graph:check \
  :freeplane_plugin_graph:test :freeplane_plugin_graph:build -PTestLoggingFull
```

Compilation, bundle verification, and test-class construction completed, but the command timed out at 420 seconds while `:freeplane_plugin_graph:test` was running. A focused reproduction completed in 25 seconds with `WorkspaceDialogsShould`: 9 tests, 0 passed, 9 failed, 0 skipped. Every failure is the same pre-existing setup defect: production `TextUtils`/resource lookup dereferences `Controller.getCurrentController()` while the test fixture has not installed a controller. The first and correction-round full-suite runs therefore remain unresolved; no test was excluded, relabeled, or hidden.

The prescribed repository-wide `gradle test -PTestLoggingFull` was also bounded at 600 seconds and timed out while `:freeplane_plugin_graph:test` was running. This finding cannot be repaired within the immutable seven-file allowlist without changing unrelated test/production setup, so final verification status is `BLOCKED` on F-4.

## Scenario Results

| Scenario | Acceptance assertion | Result |
| ---: | --- | --- |
| 1 | `GraphWorkspaceModelAcceptanceShould.scenario01_reopenRestoresMapsViewportPinsColorsAndSettings` | PASS |
| 2 | `GraphWorkspaceModelAcceptanceShould.scenario02_projectsOnlyStructuralLeavesAndActiveGroupsIncludingHiddenOnlyChildEnclosure` | PASS |
| 3 | `GraphWorkspaceModelAcceptanceShould.scenario03_preservesRequiredEnclosuresAndInteriorFixtureLabels` | PASS |
| 4 | `GraphWorkspaceModelAcceptanceShould.scenario04_outerMarkerSuppressesInnerMarkerUntilOuterIsRemoved` | PASS |
| 5 | `GraphWorkspaceModelAcceptanceShould.scenario05_consolidatesDuplicateConnectorsWithoutChangingTheirRecords` | PASS |
| 6 | `GraphWorkspaceModelAcceptanceShould.scenario06_unionsOppositeDirectedContributorsIntoTwoArrowheads` | PASS |
| 7 | `GraphWorkspaceModelAcceptanceShould.scenario07_omitsConnectorsCollapsedInsideAnActiveGroup` | PASS |
| 8 | `GraphWorkspaceCommandAcceptanceShould.scenario08RoutesSameMapNativeConnectorAndMapUndoThroughTheHandle` | PASS |
| 9 | `GraphWorkspaceCommandAcceptanceShould.scenario09RejectsCrossMapNativeConnectorsAndStoresOnlyFpgRelationships` | PASS |
| 10 | `GraphWorkspaceModelAcceptanceShould.scenario10_removedMapMakesRelationshipDormantThenReactivatesItExactly` | PASS |
| 11 | `GraphWorkspaceCommandAcceptanceShould.scenario11RoutesEndpointDeletionToMapUndoAndReactivatesTheMap` | PASS |
| 12 | `GraphWorkspaceModelAcceptanceShould.scenario12_ungroupedRootRelationshipAttachesToItsAncestorEnclosure` | PASS |
| 13 | `GraphWorkspaceModelAcceptanceShould.scenario13_reopenedPinRemainsFixedWhileItsNeighborSettles` | PASS |
| 14 | `GraphWorkspaceCommandAcceptanceShould.scenario14SupportsPanZoomFitResetSearchHoverSelectOpenAndInspect` | PASS |
| 15 | `GraphWorkspaceCommandAcceptanceShould.consumesRecordedStrictPerformanceAcceptanceResult` plus the strict diagnostic | PASS |
| 16 | `GraphWorkspaceCommandAcceptanceShould.scenario16RejectsIdlessPersistentCommandAtomicallyThenAcceptsNormalSavedId` | PASS |
| 17 | `GraphWorkspaceCommandAcceptanceShould.scenario17KeepsDenseThreeMapSourcesDistinct` | PASS |
| 18 | `GraphWorkspaceModelAcceptanceShould.scenario18_suppressesTheOnlyMapRootAndPromotesItsFirstLevelEnclosures` | PASS |
| 19 | `GraphWorkspaceModelAcceptanceShould.scenario19_secondActiveMapRestylesWithoutLoadingOrMissingFlicker` | PASS |
| 20 | `GraphWorkspaceCommandAcceptanceShould.scenario20RetainsPinnedConflictUntilExplicitUnpin` | PASS |
| 21 | `GraphWorkspaceCommandAcceptanceShould.scenario21DoesNotLeakLockedContentAndRejectsLockedRelationshipPurge` | PASS |
| 22 | `GraphWorkspaceCommandAcceptanceShould.scenario22RejectsStalePendingAndChangedContributorRequestsBeforeMutation` and `scenario22RejectsStaleAndPendingPurgeThenUndoesMissingPurge` | PASS |
| 23 | `GraphWorkspaceModelAcceptanceShould.scenario23_cloneMarkerCompositionCollapsesEveryCloneAndUnmarkingRestoresThem` | PASS |
| 24 | `GraphWorkspaceCommandAcceptanceShould.scenario24CreatesAtMostOneEditorViewPerMapAndReusesIt` | PASS |
| 25 | `GraphWorkspaceCommandAcceptanceShould.scenario25ProvidesMultiplicityCueOrDuplicateNoOpReason` | PASS |
| 26 | `GraphWorkspaceModelAcceptanceShould.scenario26_reopensMovedWorkspaceWithItsRelativeMapsTree` | PASS |
| 27 | `GraphWorkspaceModelAcceptanceShould.scenario27_stockReaderPreservesMarkerUntilTheGraphReaderRestoresIt` | PASS |
| 28 | `GraphWorkspaceModelAcceptanceShould.scenario28_keepsOneCoralMarkerAppearanceAcrossAllFourCloudShapes` | PASS |
| 29 | `GraphWorkspaceModelAcceptanceShould.scenario29_rendersNestedInactiveMarkerVisibleAndMuted` | PASS |

## Evidence Images

The declared image artifacts were generated by `graphUiEvidence` and verified as PNG files:

- [Graph Workspace implementation](images/2026-08-10-graph-workspace-implemented.png): 1280 x 800 RGBA, SHA-256 `82e15b923cb4f1fc888472d6b259bb97afd651f08235d8e65ba1ccaacb1370c9`.
- [Graph Group marker implementation](images/2026-08-10-graph-group-marker-implemented.png): 900 x 900 RGBA, SHA-256 `95146c0a0636da2564a01854850eef22c3a44b5231c88b60a4d50ba9cbf93ea3`. The artifact contains 1,743 exact opaque `#DF625D` pixels; measured coral bounds are `314 x 134 +293 +293`, enclosing the fixture coordinates from `(300,300)` through `(600,420)`.

## Additional Verification

The following checks were run or are covered by the scoped gates:

- Exact GraphStream checksums, license, notice, manifest, and class-file compatibility: PASS through `verifyGraphBundle` and the OSGi probe.
- GraphStream boundary and no-`LayoutRunner` architecture tests: PASS in the completed graph-plugin test runs.
- `graphOsgiSmoke`, `graphUiEvidence`, and `freeplaneLaunchSmoke`: PASS on the exact combined smoke command; all three task bodies executed despite existing outputs.
- Focused marker painter: PASS; the production painter test class and the correction evidence probe completed with no failures.
- Focused acceptance classes: PASS, 18 model tests plus 14 command tests, 0 failures, 0 errors, 0 skipped.
- Full strict performance ledger: PASS, 72/72 rows.
- `gradle format_translation`, translation diff, ASCII filter, `:freeplane:compileJava`, and the forbidden production scan: PASS.
- Full graph-plugin and repository-wide test gates: BLOCKED by the unresolved `WorkspaceDialogsShould` setup failures/timeouts described above.

## Residual Risk

Performance timings are machine-specific despite the generous normal and strict thresholds; another host may have different repaint and label costs. The full graph-plugin test task remains blocked by nine deterministic `WorkspaceDialogsShould` failures caused by a null `Controller.getCurrentController()` in test setup, and by the full-task timeout after those failures. That issue is outside the immutable correction allowlist. No generated OSGi framework storage remains in the source tree; the smoke uses a temporary `-Forg.osgi.framework.storage` location and deletes it after stop.
