# Graph Workspace Batch E V17 Final Findings Remediation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.

**Goal:** Resolve V16 final-review findings F-8 and F-9 so geometry-less active pins and stale connection previews cannot remain visible or emit invalid connection intents after immutable canvas-state replacement.

**Architecture:** This successor preserves V16 as terminal evidence and carries F-8/F-9 as open findings. `GraphPaintState.ConnectionPreview` will retain its source endpoint, allowing both the controller and painter to validate the same immutable current-state eligibility. `GraphCanvas` will notify its installed controller after publishing a replacement state; the controller clears an invalid preview and validates both endpoints immediately before emitting `GraphIntent.Connect`. The painter independently skips active pins and connection previews whose source lacks current projection visibility or geometry.

**Tech Stack:** Java 8 source compatibility, Zulu 21.0.8 runtime, Gradle, JUnit 4, AssertJ, Swing/AWT offscreen painting, immutable Graph Workspace projection and geometry values.

## Global Constraints

- Follow `AGENTS.md`: Java 8 source compatibility, UTF-8, four-space indentation, JUnit 4/AssertJ/Mockito, and Gradle only.
- Use exactly `JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"` with `$JAVA_HOME/bin` prepended to `PATH` for every Gradle command.
- Work only in `/data/home/guest/Development/freeplane/.worktrees/graph-batch-e-recovery` on branch `2026-08-10-graph-workspace-batch-e-recovery`; do not create, switch, remove, or merge a Git worktree.
- Preserve V16 at `.superpowers/sdd/2026-08-19-graph-workspace-batch-e-v16-rereview-report-recovery` as terminal `FINAL_BLOCKED`, plus every earlier Batch E run root, `.codegraph/`, ignored artifacts, and unrelated user state. Never reset, rewrite, clean, checkout, stash, revert, amend, or discard them.
- V17 carries V16 findings F-8 and F-9. Its fresh final review must cover merge base `02f02355d9a33851a8a4417c4610d1897f716a50` through final V17 `HEAD` and reconcile F-1 through F-9 individually.
- The only source/test paths V17 Task 1 may modify are `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPaintState.java`, `GraphCanvas.java`, `GraphInteractionController.java`, `GraphPainter.java`, `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java`, and `GraphInteractionControllerShould.java`.
- Do not modify dependencies, build files, resources, translations, persistence/XML codecs, public API modules, projection code, `MapModel`/`NodeModel` access, GraphStream boundaries, or the nine concrete public nested `GraphIntent` types.
- Suppressed, removed, or geometry-less endpoints are never painted, hit, traversed, exposed through accessibility, retained as a valid connection-preview source, or emitted in `GraphIntent.Connect`. Valid finite `LayoutPoint` coordinates, including values near `Double.MAX_VALUE`, remain accepted and unclamped.
- Preserve existing valid active-pin painting, valid source-to-distinct-target connection previews and `Connect` intents, Escape cancellation before selection clearing, valid selected-arrow traversal, stale/no-selection arrow panning, Shift acceleration, Tab/Shift+Tab, Enter validation, immutable ownership boundaries, and canvas independence from map mutation.
- `GraphPaintState` remains immutable: every replacement returns a new value, all stored endpoint/point values are non-null, and no mutable source state leaks across the controller/canvas boundary.
- Every Gradle command uses a bounded temporary log; inspect concise output or JUnit XML counts, then remove only logs and disposable probes created by that command.
- Before the source commit, require an empty index, `git diff --check`, an exact staged allowlist, and commit subject `2026-08-10-graph-workspace: Repair stale graph canvas state`. Do not stage the plan, SDD run artifacts, or unrelated files with the source commit.
- Every child receives a renderer-produced persisted envelope through a byte-stable pointer prompt. Compare the completed child transcript's first user message byte-for-byte with the stored prompt before admitting its report; a mismatch or missing required report is terminal for that run.

## Task 1: Make Transient Canvas State Current-Snapshot Safe

**Implementer tier:** Capable

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPaintState.java:1-end`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphCanvas.java:45-60`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphInteractionController.java:35-530`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPainter.java:45-380`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java:120-190,620-end`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphInteractionControllerShould.java:150-360,650-end`

**Interfaces:**

- Consumes: `CanvasState`, `GraphGeometry`, `ProjectedEndpointVisibility.visibleEndpoints(...)`, `GraphTraversalOrder.tabOrder(CanvasState)`, `GraphHitIndex.endpointAt(LayoutPoint)`, `PinProjection`, `ProjectedEndpointKey`, `GraphPaintState.ConnectionPreview`, and existing `GraphIntent.Connect` behavior.
- Produces: package-private immutable `ConnectionPreview.of(ProjectedEndpointKey source, LayoutPoint from, LayoutPoint to)` and `source()` access; package-private `GraphInteractionController.canvasStateChanged(CanvasState)` called by `GraphCanvas` after publishing a replacement state; and paint/controller behavior that rejects an unavailable preview source at both the display and intent boundaries.
- Preserves: existing `GraphPaintState` selection/hover/search APIs, valid active-pin rendering, valid connection gesture semantics, offscreen painter ordering, finite-coordinate arithmetic, and all unrelated interaction contracts.

### Step 1: Establish the V17 baseline and confirm the two root causes

- [ ] Confirm branch `2026-08-10-graph-workspace-batch-e-recovery`, current `HEAD`, empty index, clean tracked worktree, and `git diff --check`. Record that V16 is terminal `FINAL_BLOCKED` with F-8/F-9 open, but do not modify V16 artifacts.
- [ ] Read `GraphPaintState`, `GraphCanvas.setCanvasState`, controller `beginConnect`, `updatePreview`, `finishConnect`, and `cancelPreview`, plus painter `paintPins` and `paintConnectionPreview`. Confirm F-8's active pin path uses pin coordinates without a current `NodeGeometry` check, and F-9's preview stores a source only in controller fields while painter state holds coordinates only.
- [ ] Read the existing `paintOnlyActivePins`, valid connection, self-connection, Escape, and offscreen paint tests. Reuse their fixture helpers and image comparisons rather than adding production test-only APIs.

### Step 2: Write falsifiable regressions before changing production code

- [ ] In `GraphCanvasPaintShould`, add an offscreen F-8 regression that creates an active pin for an otherwise projected node, removes only that node's current `NodeGeometry`, and compares the output against an otherwise-identical no-pin state. Assert the images have zero differing pixels and no pin-color pixels at the old pin coordinate. Keep a current-geometry active-pin control that still paints.
- [ ] In `GraphCanvasPaintShould`, add a F-9 defense-in-depth regression that constructs `GraphPaintState.ConnectionPreview.of(source, from, to)` for a source endpoint that is no longer current in a replacement state. Cover a removed node source, a suppressed enclosure source, and a geometry-less node source. Paint each state and assert the preview-color segment is absent, while the existing current-source preview control remains visible.
- [ ] In `GraphInteractionControllerShould`, add a state-replacement gesture regression for each source category: start a valid connection preview on the initial current state, replace canvas state so the source is removed, suppressed, or geometry-less while a distinct target remains current, then release over the target. Assert the preview is cleared immediately, no `GraphIntent.Connect` is emitted, and no stale source remains selected. Keep the existing valid distinct-endpoint connection assertion as the control.
- [ ] Run the two focused suites before production changes and confirm the new assertions fail for the expected stale pin/preview behavior, not a fixture or compilation error:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --rerun-tasks --tests 'org.freeplane.plugin.graph.canvas.GraphCanvasPaintShould' --tests 'org.freeplane.plugin.graph.canvas.GraphInteractionControllerShould' -PTestLoggingFull
```

### Step 3: Make preview state identify its source and cancel invalid state replacement

- [ ] Change `GraphPaintState.ConnectionPreview` to require and retain a non-null `ProjectedEndpointKey source` in addition to its finite `from` and `to` points. Update its factory and all callers so a preview never exists without the source whose eligibility it represents. Keep the class package-private and immutable.
- [ ] Change controller preview creation to pass `previewSource` into `ConnectionPreview.of(...)`. Add a package-private `canvasStateChanged(CanvasState state)` hook that checks the currently retained `previewSource` against `GraphTraversalOrder.tabOrder(state)`. If the source is absent because it was removed, suppressed, or lacks current geometry, call the existing `cancelPreview()` path before repaint; do not emit an intent.
- [ ] Change `GraphCanvas.setCanvasState(...)` to publish the non-null immutable state, invoke the currently installed controller hook if present, and then repaint on the EDT. Do not expose the controller hook publicly and do not change `GraphCanvas` ownership of state.
- [ ] In `finishConnect`, obtain the current canvas state and validate both retained source and hit-tested target against current traversal eligibility before emitting `GraphIntent.Connect`. Always clear the preview first. A null state, unavailable source, unavailable target, or self-target must emit nothing.

### Step 4: Defend paint surfaces against stale state and preserve current interactions

- [ ] Change `GraphPainter.paintPins(...)` to skip a pin unless it is active, names a projected node, that node endpoint is in `ProjectedEndpointVisibility.visibleEndpoints(...)`, and the current `GraphGeometry.nodes()` contains its `NodeGeometry`. Continue drawing an eligible pin at its recorded pin coordinates with existing dimming/theme behavior.
- [ ] Change `GraphPainter.paintConnectionPreview(...)` to receive the current `CanvasState` and skip a preview unless its retained source is in the projection-visible endpoint set and has current node or hull geometry through the same anchor rules used for canvas edges. Do not reconstruct fallback geometry or clamp finite preview coordinates.
- [ ] Keep controller invalidation and painter validation independent. A stale `GraphPaintState` must not paint merely because a controller callback was delayed, and a valid source must retain current preview painting and connection behavior.

### Step 5: Verify red-green behavior and the full module boundary

- [ ] Rerun the focused suites with `--rerun-tasks`, inspect the relevant JUnit XML, and record tests/skips/failures/errors. Confirm the new removed, suppressed, and geometry-less preview regressions pass, valid pin and valid connection controls still pass, and no stale `Connect` intent is emitted.

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --rerun-tasks --tests 'org.freeplane.plugin.graph.canvas.GraphCanvasPaintShould' --tests 'org.freeplane.plugin.graph.canvas.GraphInteractionControllerShould' -PTestLoggingFull
```

- [ ] Run every canvas `*Should` suite and inspect aggregate XML results. Confirm no finite-coordinate, hit-index, accessibility, Escape, keyboard, viewport, or valid interaction regression:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --rerun-tasks --tests 'org.freeplane.plugin.graph.canvas.*Should' -PTestLoggingFull
```

- [ ] Run the complete graph-plugin suite with `--rerun-tasks`, inspect aggregate JUnit XML counts, and remove only temporary logs/probes created by these commands:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --rerun-tasks -PTestLoggingFull
```

### Step 6: Commit the bounded remediation and report evidence

- [ ] Recheck current branch and `HEAD`, `git status --porcelain=v1 --untracked-files=all`, empty index, and `git diff --check`. Stage exactly the six Task 1 source/test paths and compare `git diff --cached --name-only` against the Files allowlist.
- [ ] Commit exactly:

```bash
git add -- freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPaintState.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphCanvas.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphInteractionController.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPainter.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphInteractionControllerShould.java
git commit -m "2026-08-10-graph-workspace: Repair stale graph canvas state"
```

- [ ] Write the required implementer report with `STATUS: DONE`, the full commit SHA, exact focused/canvas/full-suite commands and XML counts, the staged-path check, retained V16 F-8/F-9 IDs, and any concrete concern. Return `DONE` only after the bounded source commit and report exist.
