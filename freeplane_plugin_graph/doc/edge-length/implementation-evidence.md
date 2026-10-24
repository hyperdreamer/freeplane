# Edge length and label connector implementation evidence

Task identifier: `2026-10-23-graph-edge-length-and-label-connector`.

The five-task serial implementation is ready for the parent lane review. Focused contracts, all three graph-suite modes, bundle verification, D2 fresh relaxations, and both image tasks passed. The retained 40-minute fixed-geometry performance replay remains outstanding under the explicit feasibility direction; this record makes no replay-pass claim.

## Provenance and scope

- Delivery base: `448c0d679d2b1d46563eaf079352e1eefb254305`.
- Measured candidate commit: `9365406458efab5eeb29fe8cef8b5b4a5574c643`. The two candidate-pin refreshes described below were pending test-only changes during measurement and are included in this evidence commit. Production sources already matched this candidate commit.
- Branch: `pm/graph-workspace-spacing-name-indicator/runs/pm-run-20261023-124832-af041fe9/epoch-2/serial/attempt-1`.
- Run UTC: `20261024T145923Z`.
- Delivery checkout was read-only. The detached baseline worktree was `/tmp/freeplane-graph-edge-baseline`; the PM integration worktree was reference-only. No subagents were dispatched.

The production diff against delivery base contains exactly:

- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/GraphStreamLayoutEngine.java`
- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPainter.java`

`PREFERRED_EDGE_GAP=20.0` applies only to node/node relationship rest lengths. `PREFERRED_NODE_GAP=24.0`, `MIN_GAP=6.0`, and `SIBLING_GAP=8.0` retain their values. Enclosure-endpoint relationship gaps remain 6.0. Radius, hull, routing, pin, persistence, hit-test, leader-placement, and zoom policies retain their previous implementation. Java 8 source/target compatibility and dependencies are unchanged.

The painter uses `ARROW_HEAD_RATIO=0.30`, `MIN_ARROW_HEAD_WORLD_LENGTH=4.0`, `MAX_ARROW_HEAD_WORLD_LENGTH=12.5`, `MIN_ARROW_HEAD_AVAILABILITY=8.0`, and `ARROW_HEAD_HALF_BASE_RATIO=0.42`. Runs below 8 wu keep the plain line. Leader strokes are 1 screen pixel with 1.2/2.8 pixel dashes, use muted ink, and restore label ink for text; the positive world-stroke floor is `1e-6`. The obsolete `arrowHeadLengthPx` helper was removed.

## Task commits

- `aeb7348b6542ee39187e9382715da660ab9712ae` — test(graph): amend frozen initial-spacing harness for edge length (2026-10-23-graph-edge-length-and-label-connector)
- `680943503e05a0c5a56d6f2e1bd4ad305fdc9351` — test(graph): pin endpoint-specific relationship rest lengths (2026-10-23-graph-edge-length-and-label-connector)
- `af9d4ce0d4b9e84b4d13fb276f9578d9be15d915` — feat(graph): implement edge length and label connector styling (2026-10-23-graph-edge-length-and-label-connector)
- `9365406458efab5eeb29fe8cef8b5b4a5574c643` — test(graph): measure settled edge gaps against delivery base (2026-10-23-graph-edge-length-and-label-connector)

Task 5 uses `test(graph): archive edge length acceptance evidence (2026-10-23-graph-edge-length-and-label-connector)`. Its SHA is reported after committing; embedding its own SHA here would be self-referential.

## Verification environment and exact commands

All Java/Gradle invocations used Java 21.0.8 Zulu and `gradle`:

```sh
export JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu
export PATH="$JAVA_HOME/bin:$PATH"
export JAVA_TOOL_OPTIONS=-Djava.awt.headless=true
PYTHONPYCACHEPREFIX=/tmp/graph-edge-pycache python3 -m py_compile freeplane_plugin_graph/src/test/resources/initial-spacing/run-initial-spacing.py
```

The Mode 1 exclusion script `/tmp/graph-headless.gradle` was:

```groovy
allprojects {
    tasks.withType(Test).configureEach {
        filter {
            excludeTestsMatching 'org.freeplane.plugin.graph.canvas.AccessibleGraphCanvasShould'
            excludeTestsMatching 'org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould'
            excludeTestsMatching 'org.freeplane.plugin.graph.window.GraphPluginIntegrationShould'
        }
    }
}
```

The logging script `/tmp/graph-test-events.gradle` was:

```groovy
allprojects {
    tasks.withType(Test).configureEach {
        testLogging {
            events 'passed', 'skipped', 'failed'
            exceptionFormat 'full'
            info {
                events 'passed', 'skipped', 'failed'
                exceptionFormat 'full'
            }
        }
    }
}
```

The explicit `info` logging block is necessary for per-method status events. The initial Mode 1 attempt was cancelled (exit 143) when its log lacked those events, then replaced by the complete run below.

| Invocation | Exit | Selected | Passed | Failed | Skipped | Seconds |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| task2-red | 1 | 16 | 9 | 7 | 0 | 39.24 |
| task3-green-layout | 0 | 21 | 21 | 0 | 0 | 16.51 |
| task3-paint-final-with-wide-render | 0 | 85 | 85 | 0 | 0 | 18.29 |
| task4-d2-worker | 0 | 1 | 1 | 0 | 0 | 535.92 |
| task5-focused-layout-first | 1 | 17 | 16 | 1 | 0 | 182.01 |
| task5-focused-layout-final | 0 | 17 | 17 | 0 | 0 | 407.33 |
| task5-focused-paint | 0 | 85 | 85 | 0 | 0 | 19.58 |
| task5-mode1-final | 0 | 1298 | 1296 | 0 | 2 | 1834.99 |
| task5-mode2-baseline | 0 | 143 | 142 | 0 | 1 | 38.37 |
| task5-mode2-candidate | 0 | 143 | 142 | 0 | 1 | 36.29 |
| task5-mode1b | 0 | 1 | 1 | 0 | 0 | 14.25 |
| task5-bundle | 0 | 0 | 0 | 0 | 0 | 12.13 |
| task5-d2-final | 0 | 1 | 1 | 0 | 0 | 509.68 |
| task5-c2 | 0 | 1 | 1 | 0 | 0 | 12.90 |
| task5-ui | 0 | 0 | 0 | 0 | 0 | 13.54 |

`task2-red` compiled and recorded the expected 7 missing-contract failures before production implementation. `task5-focused-layout-first` failed on the obsolete candidate S1 pin; the final focused run passed after the independently measured candidate-only refresh.

Exact Gradle commands (the label identifies the table row):

`task2-red`:

```sh
gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.graphstream.InitialSpacingSeedShould
```

`task3-green-layout`:

```sh
gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.graphstream.InitialSpacingSeedShould --tests org.freeplane.plugin.graph.layout.graphstream.InitialSpacingLifecycleShould
```

`task3-paint-final-with-wide-render`:

```sh
gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.canvas.GraphCanvasPaintShould --tests org.freeplane.plugin.graph.canvas.ScreenLabelPlacementShould
```

`task4-d2-worker`:

```sh
gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.graphstream.GraphEdgeLengthSettleShould
```

`task5-focused-layout-first`:

```sh
gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.graphstream.InitialSpacingSeedShould --tests org.freeplane.plugin.graph.layout.graphstream.InitialSpacingAcceptanceShould
```

`task5-focused-layout-final`:

```sh
gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.graphstream.InitialSpacingSeedShould --tests org.freeplane.plugin.graph.layout.graphstream.InitialSpacingAcceptanceShould
```

`task5-focused-paint`:

```sh
gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.canvas.GraphCanvasPaintShould --tests org.freeplane.plugin.graph.canvas.ScreenLabelPlacementShould
```

`task5-mode1-final`:

```sh
gradle -I /tmp/graph-headless.gradle -I /tmp/graph-test-events.gradle :freeplane_plugin_graph:test --info
```

`task5-mode2-baseline`:

```sh
timeout --signal=TERM --kill-after=10s 20m gradle -I /tmp/graph-test-events.gradle :freeplane_plugin_graph:test --info --tests org.freeplane.plugin.graph.canvas.AccessibleGraphCanvasShould --tests org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould --tests org.freeplane.plugin.graph.window.GraphPluginIntegrationShould
```

`task5-mode2-candidate`:

```sh
timeout --signal=TERM --kill-after=10s 20m gradle -I /tmp/graph-test-events.gradle :freeplane_plugin_graph:test --info --tests org.freeplane.plugin.graph.canvas.AccessibleGraphCanvasShould --tests org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould --tests org.freeplane.plugin.graph.window.GraphPluginIntegrationShould
```

`task5-mode1b`:

```sh
gradle -I /tmp/graph-test-events.gradle :freeplane_plugin_graph:test --info --tests org.freeplane.plugin.graph.window.GraphPluginIntegrationShould.passesTheSharedListIntoTheCreatedHeadlessView
```

`task5-bundle`:

```sh
gradle :freeplane_plugin_graph:verifyGraphBundle --info
```

`task5-d2-final`:

```sh
gradle -I /tmp/graph-test-events.gradle :freeplane_plugin_graph:test --info --tests org.freeplane.plugin.graph.layout.graphstream.GraphEdgeLengthSettleShould
```

`task5-c2`:

```sh
gradle -I /tmp/graph-test-events.gradle :freeplane_plugin_graph:test --info --tests org.freeplane.plugin.graph.canvas.GraphCanvasPaintShould.writesC2EdgeLengthEvidenceImage
```

`task5-ui`:

```sh
gradle :freeplane_plugin_graph:graphUiEvidence --info
```

For both Mode 2 runs, `JAVA_TOOL_OPTIONS` was unset. A fresh Xvfb display was allocated with `Xvfb -displayfd 3 -screen 0 1600x1200x24`; Openbox was started with `openbox --sm-disable`. The delivery-base run preceded the candidate run on that same isolated display. Both were bounded by the displayed 20-minute timeout; the display and window-manager processes were terminated afterward. The baseline command ran with cwd `/tmp/freeplane-graph-edge-baseline`; candidate commands ran in this lane worktree.

Every Mode 1, Mode 1b, baseline Mode 2 and candidate Mode 2 method status was reconciled as a multiset between `--info` events and XML. The aggregate `InitialSpacingShould` labels inner methods differently in the log; their inner class and method were normalized while preserving both direct and aggregate executions. These are executed-method counts, not discovery counts.

Mode 1 skipped only `WorkspaceUriResolverShould.rejectEncodedLeadingBackslashInRelativeUriOnWindows` and `WorkspaceUriResolverShould.storeAbsoluteUriWhenRootsDiffer`, the documented platform assumptions. Both Mode 2 runs skipped only `GraphPluginIntegrationShould.passesTheSharedListIntoTheCreatedHeadlessView`; Mode 1b passed it. All 142 headful methods passed on both baseline and candidate. The known F11 restore failure did not reproduce under this Xvfb/Openbox setup. The three window-test source files matched delivery base.

## D1 archive and candidate pin refresh

- Run ID: `initial-spacing-16689860371923369256`.
- Archive: `freeplane_plugin_graph/doc/initial-spacing/runs/initial-spacing-16689860371923369256`.
- All 29 files are direct byte-identical copies: 26 C0/candidate fixture receipts plus `manifest.json`, `comparison.json`, and `acceptance.md`.
- Frozen C0 source comparison, literal overlays, controls, expected pins and operational bounds passed. Historical C0 archives were preserved.
- Candidate endpoint targets are `nn=36.0`, `ne=42.28427124746190`, and `ee=62.568542494923804`.

| Candidate pin | Prior | Measured | Step change | Origin |
| --- | ---: | ---: | ---: | --- |
| S1 | 58 | 41 | -29.31% | `initial-spacing-9087999385746809203`, initial focused run |
| cross | 3455 | 3103 | -10.19% | `task5-candidate-cross-pin`, fresh probe using the same run's frozen C0 cross receipt |

The S1 speedup exceeds 25% and is recorded as a finding. The allowed candidate-only refresh updates the driver pins and `TypedForcesShould.TWO_MAP_FIRST_IDLE_STEP`; the C0 expectations remain S1=84, reference=42, cross=756. The complete final focused and Mode 1 comparisons independently reproduced the refreshed candidate values. The archived candidate rows include `FIRST_IDLE S1 41` and `FIRST_IDLE cross 3103`.

## D2 fresh-relaxation archive

- Archive: `freeplane_plugin_graph/doc/edge-length/runs/20261024T145923Z-9365406458ef`.
- All 25 files are direct byte-identical copies: manifest, comparison, summary, and 22 arm receipts for 11 fixtures.
- Both arms ran in separate fresh JVMs with `-Xms128m -Xmx512m -Djava.awt.headless=true`, identical JDK/runtime/calibration/input hashes, and actual `LayoutWorker` published frames.
- All delivery production Java sources were materialized and compiled together in an isolated class directory. Engine and painter code-source locations were checked in each arm.
- Source hashes were independently recomputed against current candidate files and `git show` delivery blobs; all test-source and jar hashes were checked too. Pair distances, analytic endpoint-radius-adjusted rim gaps, type minima/medians, leader lengths, and frame sequences were independently recomputed from the receipts.

| Fixture | Relationships per arm | Delivery first idle | Candidate first idle |
| --- | ---: | ---: | ---: |
| reference | 0 | 37 | 37 |
| S1 | 2 | 58 | 41 |
| cross | 4 | 3455 | 3103 |
| mixed | 2 | 77 | 40 |
| nested-three-level | 2 | 109 | 127 |
| pinned-neighbours | 2 | 166 | 173 |
| multi-membership | 2 | 191 | 107 |
| unenclosed-nn | 1 | 9 | 41 |
| node-enclosure | 1 | 7 | 7 |
| enclosure-enclosure | 1 | 7 | 7 |
| wide-diagonal-label-z163 | 1 | 9 | 9 |

All 22 arms reached first idle and retained 100 follow-up frames. Each arm has one routing-work row for every apply/step frame, exactly `first_idle_step + 101` rows. The raw receipts retain every pair at first idle and +100, positions, RMS/max displacement, straight-chord and routed conflicts, routing work, calibration and runtime. Reference has no relationship pairs and reports `NOT_APPLICABLE` aggregates.

There are 12 reported nonuniform (lopsided) first-idle gap observations and no sub-6 wu pair gaps at either snapshot. S1 minimum first-idle rim gap changes from 7.0211 to 20.7007 wu. Candidate mixed-prominence gaps are 19.4985 and 29.2105 wu; candidate multi-membership gaps are 7.0782 and 21.4615 wu. These values are measurements: rest length is a force target, and the same-enclosure chord proof does not establish a settled-distance bound for the exclusions.

Both cross arms become non-idle at +100, so first idle is not presented as permanent equilibrium. The fixed wide-label scene records a longest leader of 27 px in `ABOVE_FAR` for both arms. Its fixed 20 wu edge run is 32.6 px at zoom 1.63; relaxed positions are reported separately. No connector cap or numeric performance/equilibrium gate was added.

Every pair, baseline comparison, type aggregate and observation is retained in [the D2 summary](runs/20261024T145923Z-9365406458ef/summary.md) and its adjacent raw arm receipts.

## Visual evidence

The C2 production-paint capture at zoom 1.63 was visually reviewed: the 20 wu run carries a 6 wu filled head and the 6 wu run is a complete plain line. The C3 workspace render was regenerated and visually reviewed. Its committed projection has no edges; that image does not witness C2. Screenshots are visual evidence and impose no acceptance threshold.

| Image | SHA-256 |
| --- | --- |
| `docs/superpowers/specs/images/2026-10-23-graph-edge-length-and-label-connector-c2.png` | `b976d1db50021ff092f1a5b97580bac9bb5d6039a06335c911ab8e6b7527c440` |
| `docs/superpowers/specs/images/2026-08-10-graph-workspace-implemented.png` | `7700977532f0e7dc4d05856969c010197f84fdf2ad0126d7f8bad1ec0a542ac4` |
| `docs/superpowers/specs/images/2026-08-10-graph-group-marker-implemented.png` | `95146c0a0636da2564a01854850eef22c3a44b5231c88b60a4d50ba9cbf93ea3` |

The marker image remained byte-identical to the preserved pre-task image after `graphUiEvidence`.

## Outstanding validation and plan defects

- The retained replay `timeout --signal=TERM 40m gradle :freeplane_plugin_graph:graphPerformanceDiagnostic` was deliberately deferred under the explicit feasibility direction. Neither the candidate replay nor its fallback baseline replay was run. This fixed-geometry diagnostic remains outstanding and is separate from the completed D2 fresh relaxations.
- The projection rollback SHA `697d11f03c9503936edee93bdea3b782ad231072c` has 41 characters and does not resolve. The valid 40-character commit is `697d11f03c9503936edee93bdea3b782ad231072`. The immutable projection/spec were not edited.
- Task 2 omitted `InitialSpacingLifecycleShould.java` from its file list even though the topology helper signature migration requires updating that call site. The necessary call-site change was included in Task 2.
- Task 5 lists evidence paths but its allowed candidate-pin-refresh policy also requires changes in the driver and `TypedForcesShould.java`. Those two test-only refreshes are recorded above; no fixture, threshold, force, budget, timeout or C0 oracle was changed.
- The clarified task grouping was honored: Task 3 contains both layout and painter production edits; Task 4 implements D2. The remaining fresh independent lane review belongs to the parent workflow.

Final source and staged scope checks use `git diff --check`, `git diff --cached --check`, and the exact two-file production diff against delivery base. Only the listed evidence paths and required test changes are included in the evidence commit.
