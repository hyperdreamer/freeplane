# Graph Workspace Implementation Verification

Date: 2026-08-24

This document records the implementation-phase verification for Graph Workspace Task 42. The source base for this run was `8f7c11f8797bbebd307c97d5bce5a8ad620d74b0`. The task changes are limited to the Gradle smoke wiring, three executable probes, this report, and the two declared PNG artifacts.

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
- `graphUiEvidence`: constructed the real `GraphWorkspaceWindowModel` shell on the EDT, attached the menu bar and content to one root panel, loaded deterministic two-map state, dispatched toolbar, search, mouse, wheel, and keyboard events, painted both view sizes, asserted nonblank output and sibling containment, and wrote both PNGs.
- `freeplaneLaunchSmoke`: started `BIN/freeplane.sh`, waited for the graph bundle ACTIVE log, used a disposable user directory, and recorded `exitCode=0`, `termRequired=false` in `build/freeplane-launch-smoke/result.properties`. Normal shutdown completed within the required 15 seconds.

The strict performance command passed:

```text
gradle --no-daemon --no-parallel :freeplane_plugin_graph:graphPerformanceDiagnostic -PgraphStrictPerformance -PTestLoggingFull
```

It produced `freeplane_plugin_graph/build/graph-performance/performance-ledger.csv` with six scenarios and twelve stages each: 72 rows, all `pass=true`, `failureCount=0`, and `discardCount=0`. The reference `reference-2000-5000` workload used 400 warm-up and 300 measured samples. Its gated p95 values were force `17.278 ms` (strict limit `50 ms`), full worker `9.396 ms` (strict limit `100 ms`), EDT swap `0.656 ms` (strict limit `2 ms`), and accepted-batch-first-frame `127.925 ms` (strict limit `150 ms`). The complete performance evidence is [the existing performance report](2026-08-10-graph-workspace-performance-report.md); the current generated ledger is the task output under `freeplane_plugin_graph/build/graph-performance/`.

The two acceptance classes passed with zero failures and zero errors:

```text
gradle --no-daemon --no-parallel :freeplane_plugin_graph:test \
  --tests 'org.freeplane.plugin.graph.integration.GraphWorkspaceModelAcceptanceShould' \
  --tests 'org.freeplane.plugin.graph.integration.GraphWorkspaceCommandAcceptanceShould' \
  -PTestLoggingFull
```

The result was 18 model tests plus 14 command tests. The command/acceptance classes cover all 29 numbered scenarios below (Scenario 22 has two separately named assertions).

The required module command was attempted:

```text
gradle :freeplane_plugin_graph:clean :freeplane_plugin_graph:check \
  :freeplane_plugin_graph:test :freeplane_plugin_graph:build -PTestLoggingFull
```

Compilation, bundle verification, and test-class construction completed, but the full test task did not return within the 1,800-second command bound. A serial no-daemon rerun with a 3,600-second bound also did not return. The completed focused classes and acceptance classes have no assertion failures; the unresolved full-suite issue is recorded as a verification concern rather than represented as a pass.

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
- [Graph Group marker implementation](images/2026-08-10-graph-group-marker-implemented.png): 900 x 900 RGBA, SHA-256 `a7f039fb8c27ca80293f7365d5ccb24cb9e6148f960a15731729688664f37b33`.

## Additional Verification

The following checks were run or are covered by the scoped gates:

- Exact GraphStream checksums, license, notice, manifest, and class-file compatibility: PASS through `verifyGraphBundle` and the OSGi probe.
- GraphStream boundary and no-`LayoutRunner` architecture tests: PASS in the completed graph-plugin test runs.
- `graphOsgiSmoke`, `graphUiEvidence`, and `freeplaneLaunchSmoke`: PASS.
- Full strict performance ledger: PASS, 72/72 rows.
- Translation formatting and the repository-wide test/build commands remain separate follow-up gates because the full graph-plugin test task did not terminate within the command bounds.

## Residual Risk

Performance timings are machine-specific despite the generous normal and strict thresholds; another host may have different repaint and label costs. The full graph-plugin test task needs a separate lifecycle investigation because the test worker did not terminate within one hour even though the focused adapter and acceptance classes completed successfully. No generated OSGi framework storage remains in the source tree; the smoke uses a temporary `-Forg.osgi.framework.storage` location and deletes it after stop.
