# Graph Edge Length and Label Connector Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.
> Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the approved 20 wu node-to-node relationship gap, proportional and availability-gated arrowheads, and dotted muted label connectors without changing layout geometry or public behavior outside the approved contract.

**Architecture:** Keep the production diff to `GraphStreamLayoutEngine.java` and `GraphPainter.java`. Update the frozen C0 harness before those edits, prove seed topology and paint behavior with focused tests, then compare fresh settled relaxations against delivery base `448c0d679d2b1d46563eaf079352e1eefb254305`. Finish with the mandatory two-mode graph suite, evidence captures, bundle check, and retained performance replay.

**Tech Stack:** Java 8-compatible source, Java 21.0.8-zulu runtime, Gradle, JUnit 4, AssertJ, Python 3 standard library, GraphStream 1.3, AWT/Swing, and Xvfb. Add no dependencies.

**Normative inputs:** `docs/superpowers/specs/2026-10-23-graph-edge-length-and-label-connector-spec.md` and `docs/superpowers/specs/2026-10-23-graph-edge-length-and-label-connector-design.md`. Their approved epoch-2 authority is `PREFERRED_EDGE_GAP = 20.0`, superseding epoch 1's 24 wu relationship gap. The implementation checkout starts at `697d11f03c9503936edee93bdea3b782ad231072c`; delivery-base comparisons use `448c0d679d2b1d46563eaf079352e1eefb254305`; frozen D1 C0 remains `cb8782fb3742f378b2907198fd2343292415da95`.

**File map:** Production owns only `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/GraphStreamLayoutEngine.java` and `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPainter.java`. Layout contract tests and their probe are in `InitialSpacingConsumers.java`, `InitialSpacingSeedShould.java`, and `InitialSpacingProbe.java`. Painter and label tests are in `GraphCanvasPaintShould.java` and `ScreenLabelPlacementShould.java`. The D1 harness is `InitialSpacingAcceptanceShould.java` and `src/test/resources/initial-spacing/run-initial-spacing.py`. D2 is implemented by `GraphEdgeLengthSettleShould.java`; committed evidence is archived under `freeplane_plugin_graph/doc/edge-length/` and the image paths specified in D0. Task lane ownership is sequential and file-disjoint: `harness` owns only D1 entrypoint/script files; `tests` owns layout tests/probe and D2 test code; `production` owns the two production files plus painter/label test call sites whose package-private method signatures change with production; `evidence` owns generated reports and captures. No lane works concurrently with another.

## Global Constraints

- Treat the approved spec as the contract. Implement every pinned production edit, test migration, new test, harness amendment, validation gate, and evidence step; add or remove no product scope.
- The production diff relative to delivery base `448c0d679d2b1d46563eaf079352e1eefb254305` must contain exactly `layout/graphstream/GraphStreamLayoutEngine.java` and `canvas/GraphPainter.java` under `src/main`.
- Use `PREFERRED_EDGE_GAP = 20.0`; leave `PREFERRED_NODE_GAP = 24.0`, `NodeSeparationProjection.MIN_GAP = 6.0`, `BoundarySizes.SIBLING_GAP = 8.0`, node radii, prominence, hull geometry, routing, leader geometry, pins, persistence, hit testing, and zoom policy unchanged.
- Use Java 8-compatible syntax, UTF-8, four-space indentation, and `*Should` test class names. Add no dependencies, public APIs, settings, configuration, compatibility fallback, or replacement for the deleted `arrowHeadLengthPx` helper.
- Do not edit a fixture, threshold, force, budget, timeout, or C0 oracle to obtain a pass. Record a discrepancy and stop/escalate its gate.
- Every task carries exactly one `**Implementer tier:**` line with a TitleCase value (`Economy`, `Fast`, `Standard`, `Advanced`, `Capable`, `Frontier`), which is the only form the installed SDD parser accepts. A lowercase value is rejected by `validate-plan` as a malformed tier field and blocks the deterministic controller.
- All automated Java/Gradle commands use `/home/henry/.sdkman/candidates/java/21.0.8-zulu` and `gradle`, never `gradlew` or Maven. Set `JAVA_TOOL_OPTIONS=-Djava.awt.headless=true` for headless tests. Use the real Xvfb mode for the excluded AWT-window tests.
- Execute the full graph suite in both prescribed modes. Mode 1 excludes only `AccessibleGraphCanvasShould`, `GraphWorkspaceWindowModelShould`, and `GraphPluginIntegrationShould` with a Gradle init script; Mode 2 runs exactly those classes under Xvfb. Reconcile executed-method counts from `--info`, not discovery counts. Preserve the two platform-conditional `WorkspaceUriResolverShould` assumptions.
- D1 must keep the C0 arm byte-reproducible and its limits unchanged. D1's cumulative C0 overlay compares exactly three source files (`GraphStreamLayoutEngine.java`, existing `ClusterPacking.java` overlay, and new `GraphPainter.java` overlay); the separate production diff against delivery base is exactly two files.
- D2 compares delivery base, not D1 C0. Keep each relaxation in a fresh equivalent JVM/worker with identical JDK, heap/runtime settings, fixture inputs, and calibration. `restLength` is a force target, not a settled-distance assertion.
- Commit each task with the conventional subject specified in that task; each subject includes `2026-10-23-graph-edge-length-and-label-connector`.

## Task 1: Amend the frozen D1 harness

**Files:**
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/InitialSpacingAcceptanceShould.java`
- Modify: `freeplane_plugin_graph/src/test/resources/initial-spacing/run-initial-spacing.py`

**Interfaces:**
- Consumes: the existing C0 commit and literal `engine` and `packing` overlays. Keep the existing Java probe invocation and its timeout; migrate its topology helper call in Task 2.
- Produces: a C0-byte-frozen D1 comparison that recognizes precisely the approved candidate source overlays and targets `nn = 36.0` while retaining `ne = 42.28427124746190` and `ee = 62.568542494923804`.

**Implementer tier:** Advanced
**Lane:** harness
**Depends on:** none. This amendment must precede Task 3, which changes the candidate source bytes checked by this harness.

**Purpose / acceptance:** Make the existing fresh-JVM D1 acceptance test capable of checking the full approved implementation delta without weakening its historical control. Keep the test's headless assertion, 14-hour outer bound, and fresh-JVM serialization.

- [ ] Preserve `BASE = 'cb8782fb3742f378b2907198fd2343292415da95'`, the C0 branch, `FROZEN` entries (`ReferenceRepulsionFixture.java`, `TypedForcesShould.java`, `ClusterProximityShould.java`), complete source-tree comparison, all fixture/control accounting, and every existing C0 pin and limit byte-for-byte.
- [ ] Keep the existing `ClusterPacking.java` C0 overlay. Extend the existing engine overlay with exactly the topology replacement that selects `PREFERRED_EDGE_GAP` only when both relationship endpoints are nodes. Add exactly one new `GraphPainter.java` overlay containing the approved C2/C3 literal changes. Each literal replacement must assert `expected.count(old) == 1` before replacement. Keep indentation exact. The candidate overlay set against frozen C0 must be exactly `GraphStreamLayoutEngine.java`, `ClusterPacking.java`, and `GraphPainter.java`.
- [ ] Separately assert the implementation diff relative to delivery base is exactly the two production files in Global Constraints. Do not drop the historical `ClusterPacking.java` overlay to make the C0 changed-source set look like the delivery-base set.
- [ ] Change only the candidate `nn` REST target from `22.0` to `36.0`; keep `ne` and `ee` targets above unchanged. Keep the C0/candidate probe argument migration with Task 2 so the helper signature and caller change are committed together.
- [ ] Retain candidate settling pins `S1=58`, `reference=37`, `cross=3455` unless a fresh amended-candidate run justifies a candidate-only refresh. Keep C0 expectations immutable. For each refreshed candidate pin, archive run ID and previous value. If candidate first-idle is slower by more than 25%, `(new-old)/old > 0.25`, stop and report a risk instead of re-pinning. If faster by more than 25%, refresh the candidate pin and record the measured first-idle as a finding. Never apply the superseded absolute-change rule.
- [ ] Keep the 6000-step and 600-second bounds, 600-second shared monotonic deadlines, control-pair accounting, exhaustion expectations, C0 column, seed-gap matrix, and all other operational limits unchanged. Do not adapt or invoke `freeplane_plugin_graph/doc/initial-spacing/collect-receipt.py`.
- [ ] Add an assertion in `InitialSpacingAcceptanceShould` that the driver produced `manifest.json`, alongside its existing `comparison.json` and `acceptance.md` checks. Preserve its existing `java.awt.headless == "true"` assertion and 14-hour process bound.
- [ ] Run `PYTHONPYCACHEPREFIX=/tmp/graph-edge-pycache python3 -m py_compile freeplane_plugin_graph/src/test/resources/initial-spacing/run-initial-spacing.py`. Do not run or archive a candidate D1 result until Task 3 has installed the exact source overlay.
- [ ] Commit as `test(graph): amend frozen initial-spacing harness for edge length (2026-10-23-graph-edge-length-and-label-connector)`.

## Task 2: Pin relationship topology contracts

**Files:**
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/InitialSpacingConsumers.java`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/InitialSpacingSeedShould.java`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/InitialSpacingProbe.java`

**Interfaces:**
- Consumes: Task 1's D1 candidate target `nn=36.0`, unchanged `ne`/`ee` targets, and existing `InitialSpacingFixtures.matrix(crossMap, pair, reverse)` fixtures.
- Produces: `InitialSpacingConsumers.topology(projection, engine, ringGap, edgeGap)` plus actual-engine topology assertions for node/node `+20.0`, enclosure-endpoint `+6.0`, unchanged containment/hierarchy links, and the local single-enclosure chord bound.

**Implementer tier:** Advanced
**Lane:** tests
**Depends on:** 1

**Purpose / acceptance:** Pin I1-I4 against actual `GraphStreamLayoutEngine` topology, not settled frames or a test-only rest-length formula. The red phase must compile and fail only on the absent/new engine behavior or constant, never from a test compilation error.

- [ ] Change `InitialSpacingConsumers.topology` to accept `(projection, engine, ringGap, edgeGap)`. For each relationship assert `firstRadius + secondRadius + (both endpoints are nodes ? edgeGap : 6.0)`; retain containment and hierarchy comparisons using their existing delivery-base formulas and the supplied ring gap. Update every call site in `InitialSpacingSeedShould` and `InitialSpacingProbe`: the probe's C0 run passes `(6.0,6.0)` and candidate passes `(24.0,20.0)`; the seed test passes the matching explicit ring and relationship gaps. Do not leave an obsolete three-argument overload.
- [ ] Update seed tests for default nodes, mixed-prominence nodes, and the cross-map node/node relationship to require `r1 + r2 + 20.0`. Assert the new package-private `PREFERRED_EDGE_GAP` is exactly `20.0` and strictly less than `PREFERRED_NODE_GAP = 24.0`; during the pre-production red phase, read the not-yet-present field reflectively so the test source still compiles, without adding any production accessor.
- [ ] Exercise `InitialSpacingFixtures.matrix(..., "ne")` and `matrix(..., "ee")` as real non-vacuous rows and require `r1 + r2 + 6.0`. Do not use the `cross` fixture as evidence for I2 because its edges are node/node. Keep `MIN_GAP == 6.0`, ring, and `contentRingRadius` assertions.
- [ ] Assert the same-enclosure four-node ring bound only for direct node pairs in one enclosure: `rest <= 2 * maxRadius + 20.0 < 2 * maxRadius + 24.0`. Include radii 8.0 and 14.0, yielding rest 42 wu and adjacent ring chord 52 wu. Do not extend this proof to cross-enclosure, cross-map, multi-membership, pinned, unenclosed, node-to-anchor, or settled pairs.
- [ ] Run `gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.graphstream.InitialSpacingSeedShould`. Before Task 3, record only expected red assertions about the missing 20 wu relationship contract/constant; after Task 3, rerun and require green.
- [ ] Commit as `test(graph): pin endpoint-specific relationship rest lengths (2026-10-23-graph-edge-length-and-label-connector)`.

## Task 3: Implement edge and connector rendering

**Files:**
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/GraphStreamLayoutEngine.java:42,236-244`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPainter.java:38-42,162-210,268-300`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/ScreenLabelPlacementShould.java`

**Interfaces:**
- Consumes: Task 2's topology oracle and the existing `GraphPainter`/`ScreenLabelPlacement` test fixtures.
- Produces: the exact C1-C3 production behavior, painter tests for I5-I11/I13, and unchanged leader geometry coverage for I10.

**Implementer tier:** Advanced
**Lane:** production
**Depends on:** 1, 2

**Purpose / acceptance:** Apply only the production edits specified in A and migrate the package-private painter-test call sites in the same atomic task. Write/update the focused assertions first, observe the expected failures against the old implementation, then apply production changes and require all affected tests to pass.

- [ ] In `GraphStreamLayoutEngine`, add `static final double PREFERRED_EDGE_GAP = 20.0` next to the unchanged `PREFERRED_NODE_GAP = 24.0`. In `topology()`, use `radius(first) + radius(second) + PREFERRED_EDGE_GAP` only when both endpoints are nodes; otherwise use `NodeSeparationProjection.MIN_GAP`. Do not alter endpoint radii, `contentRingRadius`, containment, or hierarchy links.
- [ ] Replace `ARROW_HEAD_WORLD_LENGTH` with `ARROW_HEAD_RATIO = 0.30`, `MIN_ARROW_HEAD_WORLD_LENGTH = 4.0`, `MAX_ARROW_HEAD_WORLD_LENGTH = 12.5`, and `MIN_ARROW_HEAD_AVAILABILITY = 8.0`; retain `ARROW_HEAD_HALF_BASE_RATIO = 0.42`; add `MIN_STROKE_WORLD = 1e-6`.
- [ ] Delete `arrowHeadLengthPx`. Keep `availableArrowHeadWorldLength` and bidirectional half-distance logic unchanged. Implement `effectiveArrowHeadLengthPx(zoom, available)` as `max(4.0, min(12.5, 0.30 * available)) * zoom`, without availability clipping. In `paintArrow`, return before computing/drawing the head when available terminal length is `< 8.0` wu; leave the polyline already painted by `paintEdges` intact. Keep bow handling, unit vectors, filled head shape, and half-base calculation unchanged.
- [ ] Delete the unannotated compiling `arrowHeadLengthIsProportionalToZoom` test because it calls the removed `arrowHeadLengthPx`; do not leave a dead test or legacy entry point.
- [ ] Change `leaderStroke` to accept only `zoom`. Clamp each world-space width/dash input before float conversion: `width=max(1.0/zoom,1e-6)`, `dashOn=max(1.2/zoom,1e-6)`, `dashOff=max(2.8/zoom,1e-6)`. Construct a round-capped, round-joined `BasicStroke` with miter limit `10.0f`, dash `{dashOn,dashOff}`, and phase `0.0f`.
- [ ] In the shared label paint loop, paint every node and enclosure leader with `theme.mutedColor()`, use the dotted stroke, and explicitly restore `theme.labelColor()` immediately before `drawCentered`. Preserve per-label dimming/composite behavior. Do not edit `LeaderLine`, `ScreenLabelPlacement`, any label geometry, or slot policy.
- [ ] Migrate `GraphCanvasPaintShould` and `ScreenLabelPlacementShould.leaderMask` to `GraphPainter.leaderStroke(zoom)` with no theme parameter. Keep existing leader geometry expectations unchanged in meaning.
- [ ] Pin pure-helper values `(zoom=.6, availability=6) -> 2.4 px` and `(1.0,3) -> 4.0 px`; these are helper results, not painted heads. Cover availability `{3,6,7.99,8,4/0.30,24,12.5/0.30,80}` at zooms `{0.25,1,1.63,4}`, single- and double-headed. Use exact fraction boundaries, not rounded substitutes. Verify the 12.5 wu cap at and above `12.5/0.30`, including bidirectional separation `2 * (12.5/0.30) = 83.333...` wu.
- [ ] At paint level, assert every availability below 8 wu paints only the complete polyline; at exactly 8 wu paint a 4 wu head with half-base 1.68 wu, full base 3.36 wu, and at least 4 wu uncovered shaft. For every painted head assert length `<= 0.50 * availability` and `0.84 * length >= 1.4` world units. Assert head geometry is filled, not stroked. Use pixel-length assertions only at zoom >= 2; short zoom-1 heads use shape/world geometry. Keep a zoom-4 oracle where a 4 wu head spans 16 px.
- [ ] Migrate `paintedArrowHeadCapBaseAndBidirectionalSplitAtPaintLevel`: use a 9 wu single-headed availability and 18 wu bidirectional separation, each painted head 4 wu with 3.36 wu full base at zoom 1. Replace the below-floor 6 wu cap fixture with a plain-line control. Remove old 30 px and 12.5-over-8.68 crossover expectations and adjust pixel samples around the new endpoint.
- [ ] Migrate `capsTheArrowheadAtTheTerminalSegmentOfARoutedPath`: preserve the routed-path-versus-chord distinction, use bow control `(82,9)` for the painted case, and add a 7 wu terminal-availability plain-polyline case.
- [ ] Update `keepsTheLeaderStrokeScreenConstant`: in the unclamped zoom band assert `lineWidth*zoom == 1.0`, dash entries times zoom equal `1.2` and `2.8`, and pitch equals `4.0` within `1e-6`. At zoom `1e46`, assert stroke construction and nonzero float width/dash entries; do not assert 1 px outside `[1e-6,1e6]`.
- [ ] Update `paintsTheLeaderStrokeScreenConstantThroughTheProductionCallSite` to detect muted-color pixels along the full leader allowing dash gaps, and label-color pixels in text. Cover node and enclosure leaders, light and dark themes, a leader over hull fill, and dim-unrelated alpha; do not sample a single pixel column or mistake a dash gap for a missing leader. Preserve I10 leader start/end and placement outputs.
- [ ] Add the fixed I11 production-path fixture: two radius-8 nodes at `(-18,0)` and `(18,0)`, one directed edge, 20 wu attachment run, `GraphViewport.of(0,0,1.63)`, `1128x364`, Dialog 12, `SCREEN_FRC`. Pin a previous `PlacedLabel` in `ABOVE_FAR` with measured text dimensions, `FULL_DISPLACED`, and pass it through `ScreenLabelPlacement.place`; obtain the leader only via `retained()`. Assert `INTERIOR`, a 27.0 px leader, and a 32.6 px painted edge run measured at full precision. Control: the ordinary `ABOVE` slot has no leader; separately measure the `RIGHT`/`LEFT` connector at 3.0 px. Do not use settled coordinates or hand-build `LeaderLine`.
- [ ] Add the fixed I13 production-path placement measurement using `longScene()` inputs, source-order `LONG_NAMES`, radius 8, prescribed coordinates, area `1128x364`, zoom 1.63, no stand-in, forced `LONG_NAMES[0]`, light theme, Dialog metrics, and existing `MAP`. Include an edge-bearing 20 wu run. Record every present leader's slot/length and report the longest slot, endpoint, text, and unrounded length. Assert only finite length and a reported slot; never compare it to the edge run or add an upper bound.
- [ ] Add a `@Test` method named `writesC2EdgeLengthEvidenceImage` in `GraphCanvasPaintShould` that invokes the writer and saves the two deterministic production-paint fixtures to `freeplane_plugin_graph/build/graph-edge-length-c2.png`. The method asserts no visual threshold; only an image-write error fails it. Keep this generated file under `build/` until Task 5 archives it.
- [ ] Keep the build Java 8 compatible and run focused tests with Java 21 headless:

```sh
export JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu
export PATH="$JAVA_HOME/bin:$PATH"
export JAVA_TOOL_OPTIONS=-Djava.awt.headless=true
gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.graphstream.InitialSpacingSeedShould
gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.canvas.GraphCanvasPaintShould --tests org.freeplane.plugin.graph.canvas.ScreenLabelPlacementShould
```

- [ ] Verify the only production diff against delivery base is the two authorized files; run `git diff --check`. Commit as `feat(graph): implement edge length and label connector styling (2026-10-23-graph-edge-length-and-label-connector)`.

## Task 4: Add fresh-relaxation D2 measurements

**Files:**
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/GraphEdgeLengthSettleShould.java`

**Interfaces:**
- Consumes: `InitialSpacingFixtures`, `GraphStreamLayoutEngine`, the approved production topology, and delivery-base commit `448c0d679d2b1d46563eaf079352e1eefb254305`.
- Produces: isolated baseline/candidate fixture receipts and a comparison schema that retains every relationship-pair observation and never treats rest length as equilibrium.

**Implementer tier:** Capable
**Lane:** tests
**Depends on:** 1, 2, 3

**Purpose / acceptance:** Implement D2 exactly as a measurement lane, not a new performance gate. Baseline and candidate must run in separate fresh JVM relaxations with identical JDK, heap/runtime options, fixture inputs, calibration, and worker state. Keep the existing D1 harness separate.

- [ ] Add relationship-free `reference` as a control and run existing `S1`, `cross`, and `mixed` fixtures with their real prominence. Add relationship-bearing `nested-three-level` (three enclosure levels and an edge between named direct-member nodes), `pinned-neighbours` (one pinned node and unpinned neighbours with an incident relationship), `multi-membership` (one direct member of two enclosures, incident relationship, both containment links), `unenclosed-nn`, `node-enclosure` with `matrix(...,"ne")` semantics, `enclosure-enclosure` with `matrix(...,"ee")` semantics, and `wide-diagonal-label-z163`. The last uses the exact I13 `longScene()` construction from Task 3 plus a relationship-bearing render state at zoom 1.63. Every newly named relationship-bearing fixture must contain at least one actual edge between the named endpoint types; verify the fixture edge is present before relaxation so no row is vacuous.
- [ ] Keep the existing pin-free S1/cross harness distinct from these D2 exclusion fixtures. Preserve the pinned endpoint in `pinned-neighbours`, both memberships in `multi-membership`, and actual nested containment links.
- [ ] Run baseline and candidate as separate fresh JVMs from delivery base and candidate code. Use equivalent JDK, heap/runtime flags, calibration, deterministic fixture inputs, and fresh worker/engine state. Record full commit SHAs, source hashes, class-code locations, JDK/runtime details, exact commands, and fixture input hashes.
- [ ] For every fixture record first-idle step and wall time; all settled node/node, node/enclosure, and enclosure/enclosure relationship-pair center distances and endpoint-radius-adjusted rim gaps; per-pair values plus minima and medians; edge-to-node and edge-to-edge conflict counts at first idle and after 100 further frames; and routing work per published frame. Preserve enough data to recompute every aggregate.
- [ ] For every relationship pair record I14 center-to-center distance and rim gap. Report lopsided and sub-6.0 wu gaps as observations. Never assert settled distance equals `restLength`, invent a performance limit, or infer the I3 ring bound for the exclusions.
- [ ] For `wide-diagonal-label-z163`, record every present label leader's slot and length, and the longest connector. Do not assert connector length is shorter than the relationship run.
- [ ] Run `gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.graphstream.GraphEdgeLengthSettleShould` with Java 21 and headless mode. Keep D2's runtime bound and output writer explicit in the test; do not reuse D1's 14-hour acceptance process or its C0.
- [ ] Commit as `test(graph): measure settled edge gaps against delivery base (2026-10-23-graph-edge-length-and-label-connector)`.

## Task 5: Run acceptance gates and archive evidence

**Files:**
- Create: `freeplane_plugin_graph/doc/edge-length/implementation-evidence.md`
- Create: `freeplane_plugin_graph/doc/edge-length/runs/<UTC timestamp>-<candidate SHA12>/manifest.json`
- Create: `freeplane_plugin_graph/doc/edge-length/runs/<UTC timestamp>-<candidate SHA12>/delivery-448c0d679d-<fixture>.json` for every D2 fixture
- Create: `freeplane_plugin_graph/doc/edge-length/runs/<UTC timestamp>-<candidate SHA12>/candidate-<fixture>.json` for every D2 fixture
- Create: `freeplane_plugin_graph/doc/edge-length/runs/<UTC timestamp>-<candidate SHA12>/comparison.json`
- Create: `freeplane_plugin_graph/doc/edge-length/runs/<UTC timestamp>-<candidate SHA12>/summary.md`
- Create: `freeplane_plugin_graph/doc/initial-spacing/runs/<candidate run id>/manifest.json`
- Create: `freeplane_plugin_graph/doc/initial-spacing/runs/<candidate run id>/comparison.json`
- Create: `freeplane_plugin_graph/doc/initial-spacing/runs/<candidate run id>/acceptance.md`
- Create: `freeplane_plugin_graph/doc/initial-spacing/runs/<candidate run id>/<configuration>-<fixture>.json` for every D1 executed fixture
- Create: `docs/superpowers/specs/images/2026-10-23-graph-edge-length-and-label-connector-c2.png`
- Regenerate: `docs/superpowers/specs/images/2026-08-10-graph-workspace-implemented.png`
- Preserve unchanged: `docs/superpowers/specs/images/2026-08-10-graph-group-marker-implemented.png`

**Interfaces:**
- Consumes: Task 3's focused contracts, Task 4's D2 runner, D1 acceptance outputs, the existing `graphUiEvidence` task, and `graphPerformanceDiagnostic`.
- Produces: ordered gate results, D1 and D2 archives, C2/C3 visual evidence, and a scope/reviewer record.

**Implementer tier:** Capable
**Lane:** evidence
**Depends on:** 1, 2, 3, 4

**Purpose / acceptance:** Run the spec's validation vector in order, preserve raw evidence, and distinguish fixed-geometry replay from fresh relaxation. No screenshot is an automated acceptance gate; the automated tests and evidence records must not be conflated.

- [ ] Set Java 21 and headless environment for focused tests. Run the focused layout gate: `gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.graphstream.InitialSpacingSeedShould --tests org.freeplane.plugin.graph.layout.graphstream.InitialSpacingAcceptanceShould`. Require actual topology endpoint rules, byte-frozen C0, candidate `nn=36.0`, unchanged `ne`/`ee`, and unchanged operational limits.
- [ ] Run the focused paint gate: `gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.canvas.GraphCanvasPaintShould --tests org.freeplane.plugin.graph.canvas.ScreenLabelPlacementShould`. Require the arrow, suppression, color, stroke, zoom, label-geometry, I11, and I13 contracts from Task 3.
- [ ] Create `/tmp/graph-headless.gradle` with exactly this exclusion filter:

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

Set `JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu`, prepend its `bin` to `PATH`, set `JAVA_TOOL_OPTIONS=-Djava.awt.headless=true`, and run `gradle -I /tmp/graph-headless.gradle :freeplane_plugin_graph:test --info`. This Mode 1 run includes the D1 fresh-JVM harness and every graph test other than those three classes. Do not use negative `--tests` patterns as exclusions.
- [ ] Run Mode 2 with `JAVA_TOOL_OPTIONS` unset and a bounded Xvfb lifetime:

```sh
unset JAVA_TOOL_OPTIONS
export JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu
export PATH="$JAVA_HOME/bin:$PATH"
Xvfb :99 -screen 0 1600x1200x24 &
XVFB_PID=$!
trap 'kill "$XVFB_PID" 2>/dev/null || true' EXIT
export DISPLAY=:99
gradle :freeplane_plugin_graph:test --info \
  --tests 'org.freeplane.plugin.graph.canvas.AccessibleGraphCanvasShould' \
  --tests 'org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould' \
  --tests 'org.freeplane.plugin.graph.window.GraphPluginIntegrationShould'
```

The three complete classes include the 16 methods excluded from Mode 1 and the 15 assumption-guarded methods that would otherwise silently skip.
- [ ] Reconcile Mode 1 and Mode 2 executed-method counts from `--info`; do not compare discovery counts. Confirm all methods are executed across the two runs except the two documented platform-conditional `WorkspaceUriResolverShould` assumptions. Neither mode may hide or delete a `*Should` test.
- [ ] Run `gradle :freeplane_plugin_graph:verifyGraphBundle`. Require unchanged GraphStream checksum, Java class version, bundle manifest/classpath, import/export, and license checks.
- [ ] Run D2 with `gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.graphstream.GraphEdgeLengthSettleShould`. Create a fresh `freeplane_plugin_graph/doc/edge-length/runs/` directory named exactly with a UTC timestamp, hyphen, and first 12 lowercase hex characters of the candidate commit. Archive its `manifest.json`, one `delivery-448c0d679d-<fixture>.json` and `candidate-<fixture>.json` for every fixture, `comparison.json`, and `summary.md`. Include full baseline/candidate SHAs, source/input hashes, JDK/runtime, exact commands, and measurement schema. Report all fixtures/pairs and identify lopsided or sub-6.0 wu observations. Do not write unlisted D1-style artifacts into this directory.
- [ ] Direct-copy the completed candidate D1 run output into `freeplane_plugin_graph/doc/initial-spacing/runs/`: `manifest.json`, `comparison.json`, `acceptance.md`, and one `<configuration>-<fixture>.json` per executed fixture. Preserve frozen C0 archives byte-for-byte. Do not invent `results.json`, `verification.json`, or `raw/`; do not use the historical receipt collector. Retain candidate run ID and any candidate-only pin refresh evidence.
- [ ] Capture the edge-bearing C2 render at zoom 1.63 by running the focused `GraphCanvasPaintShould` capture utility; copy `freeplane_plugin_graph/build/graph-edge-length-c2.png` to `docs/superpowers/specs/images/2026-10-23-graph-edge-length-and-label-connector-c2.png`. It must show a 20 wu single-headed run with a proportional head and a 6 wu plain line without a head. This capture is visual evidence, not an acceptance threshold. Record `sha256sum` output and constants `ARROW_HEAD_RATIO=0.30`, `MIN_ARROW_HEAD_WORLD_LENGTH=4.0`, `MAX_ARROW_HEAD_WORLD_LENGTH=12.5`, `MIN_ARROW_HEAD_AVAILABILITY=8.0`, and `ARROW_HEAD_HALF_BASE_RATIO=0.42` in `implementation-evidence.md`.

- [ ] Run `gradle :freeplane_plugin_graph:graphUiEvidence` after C3. Preserve the task's existing marker image byte-for-byte; regenerate and review `docs/superpowers/specs/images/2026-08-10-graph-workspace-implemented.png` as the C3 visual witness. State explicitly that the committed projection has no edges and this image does not witness C2. Record the workspace screenshot path and digest in `implementation-evidence.md`.
- [ ] Run the retained fixed-geometry replay `timeout --signal=TERM 40m gradle :freeplane_plugin_graph:graphPerformanceDiagnostic` with Java 21 settings. This is not D2 and performs no fresh relaxation. If the candidate reaches its 40-minute deadline, run the same task from an unmodified detached temporary checkout of delivery base `448c0d679d2b1d46563eaf079352e1eefb254305`, under `/tmp` and never `/home/guest/Development/freeplane`, using the same Java/runtime settings and deadline:

```sh
git worktree add --detach /tmp/freeplane-graph-edge-baseline 448c0d679d2b1d46563eaf079352e1eefb254305
(cd /tmp/freeplane-graph-edge-baseline && \
  export JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu && \
  export PATH="$JAVA_HOME/bin:$PATH" && \
  timeout --signal=TERM 40m gradle :freeplane_plugin_graph:graphPerformanceDiagnostic)
```

Record both outcomes. Remove the temporary worktree only after its output is preserved. A baseline deadline failure is a pre-existing environment limit, not a feature regression; do not claim the candidate replay passed.
- [ ] Run `git diff --check`; inspect `git diff --name-only` and `git diff 448c0d679d2b1d46563eaf079352e1eefb254305 -- freeplane_plugin_graph/src/main`. Require exactly the two authorized production files, all test edits under `src/test`, and evidence only at the paths listed here. Do not alter fixtures, thresholds, forces, or budgets to make any gate green.
- [ ] Commit as `test(graph): archive edge length acceptance evidence (2026-10-23-graph-edge-length-and-label-connector)`.

### Risks
- The D1 driver performs a complete frozen source comparison. A missing or mis-indented literal overlay, dropped `ClusterPacking.java` overlay, altered C0 control, or conflation of the three-file C0 overlay with the two-file delivery-base production diff blocks acceptance.
- A slower candidate first-idle value beyond 25% is a risk finding and stop condition, not permission to change forces, budgets, or C0. A faster change beyond 25% may refresh only its candidate pin with run ID and prior value recorded.
- The same-enclosure chord proof does not cover cross-map, nested, pinned, multi-membership, unenclosed, node-to-anchor, or settled pairs. D2 reports those measurements without inventing limits.
- `graphPerformanceDiagnostic` is a retained replay, not a settle test. A 40-minute timeout requires an unchanged delivery-base replay before attribution.
- Mode 1 alone misses AWT window coverage; Mode 2 alone misses the headless D1 assertion. Both are mandatory and executed-method counts must be reconciled.
- D0's existing workspace UI image is edge-free and cannot prove C2. Only the separate C2 image is relevant to arrowhead appearance; neither image substitutes for automated gates.
- The SDD controller's TitleCase-only tier grammar conflicts with the requested lowercase tier lines. Do not silently modify this plan's annotations to claim acceptance; a human/controller policy adjustment is required before deterministic dispatch.

### Rollback
The exact pre-feature rollback target is integration commit `697d11f03c9503936edee93bdea3b782ad231072c`, not delivery base `448c0d679d2b1d46563eaf079352e1eefb254305`; reverting to delivery base would also remove earlier graph spacing work. If acceptance requires abandoning this feature after its sequential task commits, make one revert commit that reverts the feature task commits in reverse dependency order (Task 5, 4, 3, 2, 1), restoring the tree to the `697d11f03c9503936edee93bdea3b782ad231072c` content. Do not reset, checkout, or rewrite history. Keep the D1/D2 artifacts only if they remain accurately labeled as failed/abandoned evidence; otherwise include their removal in the same revert commit.

### Reviewer checklist
- Confirm exact epoch-2 values: 20.0 wu relationship gap, 24.0 wu ring gap, 6.0 wu enclosure-endpoint gap; verify `PREFERRED_EDGE_GAP < PREFERRED_NODE_GAP`.
- Confirm production diff is exactly the two authorized `src/main` files and no label/layout geometry, routing, API, setting, persistence, or compatibility code changed.
- Confirm helper arithmetic and paint suppression separately: 6.0 wu head on 20 wu run; 5.04 wu full base; 32.6 px run at zoom 1.63; no head below 8 wu; 4.0 wu head at 8 wu; exact fractional branch boundaries; cap at 12.5 wu.
- Confirm leader width/dash math, text-color restoration, muted-color pixel evidence, unchanged leader geometry, I11 pinned `ABOVE_FAR` 27 px connector versus 32.6 px edge run, and I13 reported-only diagonal measurement.
- Confirm D1 C0 is byte-identical and unchanged, candidate `nn=36.0`, `ne`/`ee` unchanged, both source-set comparisons are distinct and exact, and all candidate pin movements follow the slower-only escalation rule.
- Confirm D2 uses delivery base in fresh equivalent JVMs, every named relationship-bearing fixture has a real edge, every pair is recorded, and settled distances are not asserted equal to rest lengths.
- Confirm both validation modes, count reconciliation, bundle verification, D2 receipts, retained replay baseline handling, screenshot hashes, `git diff --check`, and the exact rollback target are recorded.
- Confirm no generated D1 files were invented and no frozen C0 archives or unrelated marker image changed.

- [ ] Complete the checklist against the final diff and evidence before reporting acceptance.
