# Graph Workspace Batch E Dispatch-Mismatch Continuation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to execute this plan task-by-task.

**Goal:** Freshly certify the valid Task 26 commits, repair the remaining safe-search test boundary if the independent audit confirms it, and implement Backlog Task 27 keyboard traversal and accessible virtual children.

**Architecture:** Task 1 is a read-only carry-forward audit of the exact committed Task 26 fix range, followed by the normal independent review and bounded fix loop if a residual finding is confirmed. It never rewrites the valid Task 26 production implementation. Task 2 layers deterministic traversal and lightweight Swing accessibility over the existing immutable projection, layout, geometry, paint, and interaction values. Every task is independently reviewed before the next task or final review.

**Tech Stack:** Java 8 source/target compatibility, Java 21.0.8-zulu runtime, Gradle, JUnit 4, AssertJ, Mockito, Swing/AWT, immutable Graph Workspace projection/layout/geometry values.

## Global Constraints

- Follow `AGENTS.md`: Java source and target compatibility are 8, UTF-8 encoding, four-space indentation, JUnit 4/AssertJ/Mockito tests, and use `gradle`, never Maven or `gradlew`.
- Use exactly `~/.sdkman/candidates/java/21.0.8-zulu`; set `JAVA_HOME` to that path and prepend `$JAVA_HOME/bin` to `PATH` for every Gradle command. Verify the path exists before implementation and never substitute another JDK.
- The branch already contains valid carry-forward commits `56eee93d9c5432182519a23a886f181658defa8c`, `8d54ecda2157c06baa9b765cc92eb2a82e834506`, and `54cab57876bb73bde13945bbbb8493ed7d34ab66`; do not reset, rewrite, or recommit them.
- Preserve the terminal predecessor run rooted at `.superpowers/sdd/2026-08-18-graph-workspace-batch-e-continuation`, the blocked dispatch-recovery run rooted at `.superpowers/sdd/2026-08-18-graph-workspace-batch-e-dispatch-recovery`, and the current terminal run rooted at `.superpowers/sdd/2026-08-18-graph-workspace-batch-e-task-blocked-recovery`; their states, reports, prompts, child transcripts, and audit projections are byte-preserved diagnostic history only and must not be edited or cited as continuation evidence.
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
- Before every source-changing commit, require an empty index, stage only the task allowlist, compare staged names exactly, run `git diff --check`, and use a commit subject beginning `2026-08-10-graph-workspace:`. Never stage `.codegraph/` or unrelated paths.
- The final Frontier review must cover the complete branch from merge base `02f02355d9a33851a8a4417c4610d1897f716a50` through final `HEAD` and must use fresh evidence, not predecessor reports.

## Task 1: Audit And Freshly Certify The Committed Task 26 Range

**Implementer tier:** Capable

**Files:**

- Read-only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphHitIndex.java:1-end`
- Read-only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPainter.java:1-end`
- Read-only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphSearchModel.java:1-end`
- Read-only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphInteractionController.java:1-end`
- Read-only: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphInteractionControllerShould.java:1-end`
- Read-only: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphSearchModelShould.java:1-end`
- Read-only: `docs/superpowers/specs/2026-08-18-graph-workspace-batch-e-dispatch-mismatch-continuation-design.md:1-end`
- No source deliverable or Git commit is permitted; write only the normal implementer report under the dispatched run root.

**Interfaces:**

- Consumes: the committed Task 26 source/test range `8d54ecda2157c06baa9b765cc92eb2a82e834506..54cab57876bb73bde13945bbbb8493ed7d34ab66`, `GraphPainter`'s layout-anchor behavior, the immutable `CanvasState`/projection/geometry values, and the exact Java/Gradle commands below.
- Produces: a bounded audit report with exact commit/file-range evidence, focused test results, read-only falsifiability probe results, and any residual finding stated concretely for the independent reviewer.
- Must preserve: `HEAD`, index, branch, all source/test bytes, all predecessor run roots, and all unrelated checkout state.

- [ ] **Step 1: Establish the immutable carry-forward baseline**

From the active worktree, record `git rev-parse HEAD`, `git status --porcelain=v1 --untracked-files=all`, and `git diff --name-status 8d54ecda2157c06baa9b765cc92eb2a82e834506 54cab57876bb73bde13945bbbb8493ed7d34ab66`. Confirm `HEAD` is `54cab57876bb73bde13945bbbb8493ed7d34ab66`, the range changes exactly these three paths, and both commits use the required `2026-08-10-graph-workspace:` prefix. Do not stage, edit, checkout, reset, or commit anything.

- [ ] **Step 2: Run the fresh focused and compatibility gates**

Run both commands with Zulu 21 and record exit status and concrete test results:

````bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.canvas.GraphInteractionControllerShould' --tests 'org.freeplane.plugin.graph.canvas.GraphSearchModelShould' -PTestLoggingFull
````

````bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.canvas.GraphViewportShould' --tests 'org.freeplane.plugin.graph.canvas.GraphCanvasPaintShould' --tests 'org.freeplane.plugin.graph.canvas.AdaptiveRenderingPolicyShould' --tests 'org.freeplane.plugin.graph.canvas.GraphInteractionControllerShould' --tests 'org.freeplane.plugin.graph.canvas.GraphSearchModelShould' -PTestLoggingFull
````

A build failure caused by the environment is not silently treated as a code pass; report the exact failure and stop the audit at the missing evidence.

- [ ] **Step 3: Prove the two regression tests are falsifiable without touching the worktree**

Create a uniquely named archive under `/tmp` from `54cab57876bb73bde13945bbbb8493ed7d34ab66`. For the anchor probe, replace only the archived `GraphHitIndex.java` with the `8d54ecda2157c06baa9b765cc92eb2a82e834506` version and run only `GraphInteractionControllerShould.hitAnEnclosureEdgeAtItsLayoutAnchoredPaintedSegment`; record the expected failure, then delete the archive.

For the search probe, create a separate disposable archive from `54cab...` and make only a temporary `GraphSearchModel.java` mutation that appends `ProjectedNode.source().toString()` to each indexed safe-text value. Run the focused `GraphSearchModelShould` tests and record whether the raw sentinel assertion changes. The current fixture is a load-bearing coverage gap if the mutant still passes because its `SourceSideFixture` sentinel is not represented by any projected node source identity. Delete the archive and every temporary file, then re-confirm the active worktree `HEAD` and status are unchanged.

- [ ] **Step 4: Inspect the committed implementation against the Task 26 contract**

Read the exact current production and test source. Confirm layout anchors take precedence over hull label anchors in `GraphHitIndex`, visible/suppressed and node-over-hull rules are preserved, all listed synthetic interaction branches have independent assertions, and safe search indexes only projected safe values. Treat an unprojected helper-only sentinel as insufficient evidence even if its absent-query assertion passes. Record file:line evidence and a precise residual finding, not a general suggestion.

- [ ] **Step 5: Write the audit report and leave no source residue**

Write exactly one implementer report at the dispatched report path with `STATUS: DONE` only when the audit commands and probes completed. Include `CHANGES: no source changes`, exact range/allowlist evidence, test commands/results, probe results, and a `RESIDUAL AUDIT FINDINGS:` section when the source-identity sentinel is disconnected. Do not include a commit SHA because this task intentionally makes no commit. Confirm `git status --porcelain=v1 --untracked-files=all` and `git rev-parse HEAD` in the report.

- [ ] **Step 6: Commit handoff with no source commit**

There is intentionally no Git commit for this read-only audit task. Before returning `DONE`, verify the index is empty, the source tree is unchanged, all `/tmp` probes are absent, and the report is the only new task deliverable. The controller's independent review will use the exact carry-forward range and the audit evidence; do not stage or commit an audit artifact.

The controller will independently review this exact range. The reviewer must not treat the predecessor blocked-run reports or mismatched transcript as evidence.

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
