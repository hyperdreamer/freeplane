# Graph Workspace Batch E Dispatch-Mismatch Continuation Implementation Plan V5

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to execute this plan task-by-task.

**Goal:** Freshly certify the corrected Task 26 interaction/search range and implement Backlog Task 27 keyboard traversal and accessible virtual children.

**Architecture:** Task 1 freshly audits the Task 26 range and implements the verified finite-coordinate edge-hit correction in the same bounded task, followed by the normal independent review. It never rewrites the existing Task 26 commits. Task 2 layers deterministic traversal and lightweight Swing accessibility over the existing immutable projection, layout, geometry, paint, and interaction values. Every task is independently reviewed before the next task or final review.

**Tech Stack:** Java 8 source/target compatibility, Java 21.0.8-zulu runtime, Gradle, JUnit 4, AssertJ, Mockito, Swing/AWT, immutable Graph Workspace projection/layout/geometry values.

## Global Constraints

- Follow `AGENTS.md`: Java source and target compatibility are 8, UTF-8 encoding, four-space indentation, JUnit 4/AssertJ/Mockito tests, and use `gradle`, never Maven or `gradlew`.
- Use exactly `~/.sdkman/candidates/java/21.0.8-zulu`; set `JAVA_HOME` to that path and prepend `$JAVA_HOME/bin` to `PATH` for every Gradle command. Verify the path exists before implementation and never substitute another JDK.
- The branch already contains valid carry-forward commits `56eee93d9c5432182519a23a886f181658defa8c`, `8d54ecda2157c06baa9b765cc92eb2a82e834506`, `54cab57876bb73bde13945bbbb8493ed7d34ab66`, and the bounded test-only correction `8ef6d2e88043ae406a49e07aa2b0608c40c62f76`; do not reset, rewrite, or recommit them.
- Preserve the terminal predecessor run rooted at `.superpowers/sdd/2026-08-18-graph-workspace-batch-e-continuation`, the blocked dispatch-recovery run rooted at `.superpowers/sdd/2026-08-18-graph-workspace-batch-e-dispatch-recovery`, the terminal task-blocked recovery run rooted at `.superpowers/sdd/2026-08-18-graph-workspace-batch-e-task-blocked-recovery`, the terminal v2 dispatch-mismatch run rooted at `.superpowers/sdd/2026-08-18-graph-workspace-batch-e-dispatch-mismatch-continuation-v2`, the terminal v3 dispatch-mismatch run rooted at `.superpowers/sdd/2026-08-18-graph-workspace-batch-e-dispatch-mismatch-continuation-v3`, and the terminal v4 dispatch-mismatch run rooted at `.superpowers/sdd/v4`; their states, reports, prompts, child transcripts, and audit projections are byte-preserved diagnostic history only and must not be edited or cited as continuation evidence.
- Preserve the pre-existing untracked `.codegraph/` directory and all unrelated user changes or ignored artifacts. Never clean or revert them as part of this plan.
- Every child must read only its dispatched brief and the listed paths. The controller-rendered prompt bytes are authoritative: persist them before spawn, pass them byte-for-byte, record the returned session immediately, and compare the completed child's first user message byte-for-byte before admitting its report. A mismatch is terminal for that run and is never reissued or adopted.
- Keep each implementation or fix within its explicit allowlist. Do not add a shared fixture, build change, translation, resource, dependency, new exported package, `freeplane_api` change, print/export API, compatibility fallback, source-model access, or workspace command.
- `CanvasState`, `GraphProjection`, `LayoutFrame`, `GraphGeometry`, `GraphViewport`, and `GraphPaintState` remain immutable values at their ownership boundaries; canvas mutation is EDT-local only.
- Canvas code never reads a Freeplane `MapModel`/`NodeModel`, calls a transformer, executes a workspace command, changes a map, writes a file, or exposes a GraphStream type.
- Geometry is the sole source for rendered bounds, hit bounds, traversal positions, and accessible bounds. Use `NodeGeometry` and `HullGeometry` already produced by the prominence-aware geometry pipeline; never recompute node scale or infer it from raw relationships.
- Safe text comes only from `ProjectedNode.label().fullText()` / `displayText()` and `ProjectedEnclosure.labels()`; never use source models, transformed text, hidden descendants, excluded content, or raw formulas. Use `Locale.ROOT` for case-insensitive search normalization.
- Preserve exactly two enclosure visual tiers from `BoundaryTier`: emphatic and subtle. Do not render `BoundaryTier.SUPPRESSED` as a visible, hittable, traversable, or accessible endpoint.
- Adaptive target limits remain exact: warn when projected nodes are greater than 2,000 or projected edges are greater than 5,000; counts above either target remain editable and no endpoint, intent, search result, navigation, inspection, or accessible child is disabled by count.
- Use automatic rendering levels `FULL`, `DENSE`, and `OVER_TARGET` with the existing Task 25 behavior; selected, hovered, and search-matched labels remain visible at every level, and emphatic enclosure labels use their dedicated font at every level.
- The keyboard rule is exact: selected unmodified arrows traverse, no-selection unmodified arrows pan, and Shift+arrow always accelerates pan. Enter opens the selected source endpoint. Escape cancels transient connection state before clearing selection.
- `GraphIntent` has exactly the nine existing public concrete nested types: `OpenSourceNode`, `Pin`, `Unpin`, `UnpinAll`, `Connect`, `InspectEdge`, `DeleteContributor`, `DeleteAllContributors`, and `ChangeSelection`.
- Accessible descriptions append `NodeProminence.visibleOutgoingTargets()` only when nonzero. They never state a scale factor. Map identity must be present in text as well as color; excluded and suppressed content must be absent.
- Tasks 25-27 have no backlog-prescribed mutant. Do not invent a production mutant. Read-only archive probes may mutate only disposable files under `/tmp` and must be deleted afterward.
- Redirect every long-running Gradle or disposable-archive command's stdout and stderr to a temporary log, inspect only a short tail and the Gradle XML result, and delete the log before reporting. Never paste compiler-warning logs into the child transcript.
- Before every source-changing commit, require an empty index, stage only the task allowlist, compare staged names exactly, run `git diff --check`, and use a commit subject beginning `2026-08-10-graph-workspace:`. Never stage `.codegraph/` or unrelated paths.
- The final Frontier review must cover the complete branch from merge base `02f02355d9a33851a8a4417c4610d1897f716a50` through final `HEAD` and must use fresh evidence, not predecessor reports.

## Task 1: Audit And Correct Task 26 Finite Edge Hit Testing

**Implementer tier:** Capable

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphHitIndex.java:1-end`
- Read-only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPainter.java:1-end`
- Read-only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphSearchModel.java:1-end`
- Read-only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphInteractionController.java:1-end`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphInteractionControllerShould.java:1-end`
- Read-only: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphSearchModelShould.java:1-end`
- Read-only: `docs/superpowers/specs/2026-08-18-graph-workspace-batch-e-dispatch-mismatch-v5-continuation-design.md:1-end`
- The only source deliverable is the bounded edge-hit correction and its regression test; no other source/test path may change. Write the normal implementer report under the dispatched run root. Use targeted `rg`/line reads; do not invoke CodeGraph or read predecessor run artifacts.

**Interfaces:**

- Consumes: the committed Task 26 source/test range `8d54ecda2157c06baa9b765cc92eb2a82e834506..54cab57876bb73bde13945bbbb8493ed7d34ab66`, the bounded test-only correction `8ef6d2e88043ae406a49e07aa2b0608c40c62f76`, `GraphPainter`'s layout-anchor behavior, the immutable `CanvasState`/projection/geometry values, and finite near-limit coordinate support in `LayoutPoint`/geometry tests.
- Produces: a bounded audit report, one focused edge-hit regression test, a minimal overflow-safe `GraphHitIndex` correction, exact test/probe evidence, and a required commit.
- Must preserve: `HEAD`, index, branch, all source/test bytes, all predecessor run roots, and all unrelated checkout state.

- [ ] **Step 1: Establish the immutable carry-forward baseline**

From the active worktree, record `git rev-parse HEAD`, `git status --porcelain=v1 --untracked-files=all`, `git merge-base --is-ancestor 8ef6d2e88043ae406a49e07aa2b0608c40c62f76 HEAD`, `git diff --name-status 8d54ecda2157c06baa9b765cc92eb2a82e834506 54cab57876bb73bde13945bbbb8493ed7d34ab66`, `git diff --name-status 54cab57876bb73bde13945bbbb8493ed7d34ab66 8ef6d2e88043ae406a49e07aa2b0608c40c62f76`, and `git diff --name-status 8ef6d2e88043ae406a49e07aa2b0608c40c62f76 HEAD`. Confirm the fixed endpoint is an ancestor of `HEAD`, the Task 26 range changes exactly the three original paths, the bounded correction changes exactly `GraphSearchModelShould.java`, and all later changes are controller documentation only with no source/test path. Confirm every involved implementation commit uses the required `2026-08-10-graph-workspace:` prefix. Do not stage, edit, checkout, reset, or commit anything.

- [ ] **Step 2: Run the fresh focused and compatibility gates**

Run both commands with Zulu 21 and record exit status and concrete test results. Redirect each command's stdout and stderr to a uniquely named `/tmp` log, print only its exit status and the final 20 log lines, and remove the log immediately. Do not stream compiler warnings into the child transcript.

````bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.canvas.GraphInteractionControllerShould' --tests 'org.freeplane.plugin.graph.canvas.GraphSearchModelShould' -PTestLoggingFull
````

````bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.canvas.GraphViewportShould' --tests 'org.freeplane.plugin.graph.canvas.GraphCanvasPaintShould' --tests 'org.freeplane.plugin.graph.canvas.AdaptiveRenderingPolicyShould' --tests 'org.freeplane.plugin.graph.canvas.GraphInteractionControllerShould' --tests 'org.freeplane.plugin.graph.canvas.GraphSearchModelShould' -PTestLoggingFull
````

A build failure caused by the environment is not silently treated as a code pass; report the exact failure and stop the audit at the missing evidence.

- [ ] **Step 3: Write and prove the extreme-span edge regression first**

Add one focused test in `GraphInteractionControllerShould` using valid finite near-limit node centers that produce an edge spanning approximately `-8.0e307` to `8.0e307`. Assert an on-segment query within a small tolerance still resolves the edge and an off-segment query, such as `(0.0, 100.0)` with tolerance `1.0`, is empty. Run the focused test before changing production code and record the expected failure caused by the current `Line2D.ptSegDistSq` `NaN`/`best == null` path. Keep the test fixture valid under existing graph relationship/map invariants.

- [ ] **Step 4: Implement the minimal overflow-safe hit correction and finish the audit**

Replace the overflow-prone squared `Line2D` distance use in `GraphHitIndex.edgeAt` with an overflow-safe point-to-segment distance comparison that supports all finite `LayoutPoint` coordinates. Reject non-finite distance results before comparing with tolerance, compare finite actual distances without squaring overflow, preserve deterministic nearest-edge/key tie ordering, and retain ordinary-coordinate behavior. Do not clamp or reject finite geometry. Then complete the Task 26 source audit: layout anchors take precedence over hull label anchors, suppressed hulls are excluded, nodes precede hulls, all listed interaction branches are independently asserted, and safe search indexes only projected safe values with the corrected projected-node source sentinel.

- [ ] **Step 5: Run green verification and write the implementation report**

Run the focused interaction/search command and the named viewport/paint/adaptive compatibility selection with Zulu 21, redirecting output to bounded temporary logs. Confirm the new extreme-span test, the existing anchor test, and all search/interaction tests pass. Re-run the two disposable Task 26 falsifiability probes: predecessor `GraphHitIndex` must fail the anchor test, and the unsafe source-identity search mutation must fail the corrected search suite. Delete all archives/logs. Inspect the actual diff and status, and report exact counts, root-cause evidence, and probe results.

- [ ] **Step 6: Commit the bounded Task 1 correction with the exact allowlist**

Require an empty index, stage exactly these two paths, and verify the staged names:

````bash
test -z "$(git diff --cached --name-only)"
git add -- freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphHitIndex.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphInteractionControllerShould.java
````

Run `git diff --check`, then commit with a subject beginning `2026-08-10-graph-workspace:`. Include the full commit SHA in the report. The independent reviewer will inspect the complete carry-forward range plus this bounded correction; do not stage any audit artifact or documentation path.

## Task 2: Implement Backlog Task 27 Keyboard Traversal And Accessible Virtual Children

**Implementer tier:** Advanced

**Files:**

- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/TraversalDirection.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphTraversalOrder.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/AccessibleGraphCanvas.java`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphCanvas.java:1-end`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphInteractionController.java:1-end`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/AccessibleGraphCanvasShould.java`

**Interfaces:**

- Consumes: all valid Task 25 and Task 26 canvas values; `CanvasState`; immutable projected node/enclosure/pin/prominence values; `GraphGeometry`; `NodeGeometry`; `HullGeometry`; `ProjectedEndpointKey`; `GraphIntent`; `GraphInteractionController`; and Swing accessibility APIs.
- Produces:

````java
public enum TraversalDirection { UP, DOWN, LEFT, RIGHT }
public final class GraphTraversalOrder {
    public List<ProjectedEndpointKey> tabOrder(CanvasState state);
    public Optional<ProjectedEndpointKey> nearest(CanvasState state,
        ProjectedEndpointKey from, TraversalDirection direction);
}
````

- Defines: package-private `AccessibleGraphCanvas extends AccessibleContext` and implements `AccessibleComponent` as the root context returned from `GraphCanvas.getAccessibleContext()`. Its virtual endpoint children implement `Accessible`, `AccessibleAction`, and `AccessibleComponent` as needed but are not `JComponent` instances. The context and children retain no mutable snapshot; every query reads the canvas's current immutable state and paint state.
- Extends: `GraphCanvas` with a cached `AccessibleGraphCanvas`, `getAccessibleContext()` override, package-visible endpoint-bounds/name/description/action helpers, and a controller registration hook. Extends `GraphInteractionController` with package-visible traversal/activation methods used by canvas actions; existing Task 26 public methods and the Task 25 public theme binding remain source compatible.

- [ ] **Step 1: Write the failing traversal and accessibility tests**

Create `AccessibleGraphCanvasShould` with a local fixture containing three node endpoints, a non-suppressed enclosure endpoint, a suppressed enclosure endpoint, pins, distinct map names, safe labels, and a node whose `NodeProminence` is zero plus one whose value is three. Place the geometry so two candidates are equidistant in a requested direction and use endpoint keys whose natural order chooses the deterministic winner.

Assert `GraphTraversalOrder.tabOrder` returns every visible node and non-suppressed enclosure endpoint exactly once in `ProjectedEndpointKey` order and never includes the suppressed endpoint. Assert `nearest` excludes the source, filters strictly to the requested left/right/up/down half-plane, chooses minimum squared distance, and breaks exact distance ties by `ProjectedEndpointKey.compareTo`; it returns empty for missing source geometry or no candidate in the half-plane. Verify it uses node centers and hull label anchors from `GraphGeometry`, so an enlarged node's accessible bounds and directional location agree with painted/hit geometry.

Install a controller and dispatch keyboard events to assert Tab/Shift-Tab move selection through tab order, selected unmodified arrows emit a `ChangeSelection` to the traversal result, no-selection unmodified arrows pan without changing selection, Shift arrows pan at the accelerated Task 2 delta regardless of selection, Enter emits `OpenSourceNode` for the selection, and Escape cancels preview before clearing selection. Reuse Task 2 connection-preview setup in the local fixture rather than adding a shared helper.

Obtain `canvas.getAccessibleContext()` and assert its accessible children represent only visible endpoints without per-node Swing components. For a node child, assert role/name include full safe label and owning map name, description includes selected/pinned state and `3 visible outgoing targets`, accessible bounds equal the viewport-transformed prominence-scaled `NodeGeometry` bounds, and accessible actions can select/open through the controller. For the zero-reach node, assert description omits an outgoing-target phrase. For every child/context text, assert no source exclusion placeholder, raw/transformed text, map color hexadecimal value, or prominence scale such as `1.75` appears. Assert changing canvas state and viewport updates child count/bounds/text from the current immutable state rather than retaining stale virtual children.

- [ ] **Step 2: Run Task 27 red and verify the failure cause**

Run:

````bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.canvas.AccessibleGraphCanvasShould' -PTestLoggingFull
````

Confirm the failure is the absent traversal/accessibility production API, not a Task 25 or Task 26 regression.

- [ ] **Step 3: Implement deterministic traversal order**

Implement `TraversalDirection` and `GraphTraversalOrder`. Construct visible endpoint positions from `GraphGeometry`: node endpoint maps to `NodeGeometry.center()`, each non-suppressed enclosure endpoint maps to its hull's `labelAnchor()`. Do not include geometry-less endpoints. `tabOrder` adds these keys to an ordered list and sorts using `ProjectedEndpointKey.compareTo`; return an unmodifiable list.

For `nearest`, require nonnull arguments, resolve the source position, filter candidates using strict half-plane comparisons (`x <`, `x >`, `y <`, `y >`), and compare squared distances without taking square roots. On equal distance choose the lower `ProjectedEndpointKey`; return `Optional.empty()` when no candidate qualifies. World coordinates retain screen axis direction because Task 1's transform uses positive zoom with unflipped Y; do not introduce a separate coordinate system or viewport-dependent ordering.

- [ ] **Step 4: Implement keyboard traversal and virtual accessibility**

Update `GraphInteractionController` so selected unmodified arrows call `GraphTraversalOrder.nearest` and emit one `ChangeSelection` for a result; no result leaves selection unchanged. Preserve Task 26 behavior for no-selection and Shift arrows exactly. Add Tab/Shift-Tab cycling over `tabOrder`, Enter source opening, and Escape priority of preview cancellation before selection clear. Update `GraphCanvas` to install the accessible context lazily and to delegate virtual-child activation to the installed controller without accessing a store/map.

Implement package-private `AccessibleGraphCanvas` by extending `AccessibleContext` and implementing `AccessibleComponent` for the canvas root. Its `getAccessibleChildrenCount` and `getAccessibleChild` derive the current traversal list on demand. Each virtual child is an `Accessible` object with `AccessibleContext`; it exposes a role suitable for an actionable graph endpoint, `AccessibleComponent` bounds transformed by the current viewport, and `AccessibleAction` entries for selection and source opening. Use a node's full safe label plus map name for the accessible name. Build its description from label, map name, current selection/pin state, endpoint type/action availability, and, for nodes only, `NodeProminence.visibleOutgoingTargets()` when greater than zero. Never emit scale, color, raw source text, or an excluded/suppressed endpoint. The virtual child objects must be lightweight wrappers, not Swing components, and must re-resolve the current endpoint snapshot on every accessibility method to avoid stale data.

- [ ] **Step 5: Run Task 27 green and the full canvas regression suite**

Run the focused command again, then run:

````bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.canvas.*Should' -PTestLoggingFull
````

Confirm all Task 25, Task 26, and Task 27 canvas tests pass together, including immutable paint, persisted map-color binding, emphatic typography, enlarged-node hit bounds, safe search, interaction uninstall, deterministic traversal, and accessible virtual children.

- [ ] **Step 6: Commit Backlog Task 27 with the exact allowlist**

Run `git diff --check`. Require an empty index, then stage exactly these six paths:

````bash
test -z "$(git diff --cached --name-only)"
git add -- freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/TraversalDirection.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphTraversalOrder.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/AccessibleGraphCanvas.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphCanvas.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphInteractionController.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/AccessibleGraphCanvasShould.java
````

Require `git diff --cached --name-only` to contain exactly those six paths, then commit:

````bash
git commit -m "2026-08-10-graph-workspace: Make the graph keyboard accessible"
````

Report the commit SHA and exact focused/full canvas test results.

### Final Verification

After the carry-forward audit, any bounded Task 1 fix/re-review loop, and Task 2 complete, the controller's Frontier final review must cover the entire branch from merge base `02f02355d9a33851a8a4417c4610d1897f716a50` through final `HEAD`, independently reconcile the layout-anchor and safe-search boundaries, and run:

````bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test -PTestLoggingFull
````

The final report must name the carry-forward Task 25/Task 26 commit IDs, any bounded search-fixture fix commit, the Task 27 commit, focused and full-suite results, the preserved terminal predecessor runs, and any unrelated pre-existing checkout state without reverting it.
