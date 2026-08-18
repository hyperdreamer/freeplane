# Graph Workspace Batch E Dispatch-Recovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.

**Goal:** Independently re-audit the verified Task 25 remediation commit after a terminal dispatch mismatch, then implement and verify the remaining Batch E interaction and accessibility deliverables.

**Architecture:** The first task is a fresh, read-only audit of the exact four-file Task 25 remediation range `0db0db9930b982cfd782f8f98b8a302f426974b9..c7d4e898e48b0f5d6aab1bc333d182b844941ac9`, with no evidence imported from the blocked run. The next two tasks implement the original Backlog Tasks 26 and 27 sequentially, reusing the independently verified canvas contracts. Each task has its own focused tests, exact allowlist, independent review, and commit; the controller then performs a whole-branch final review.

**Tech Stack:** Java 8 source/bytecode, Gradle, JUnit 4, AssertJ, Mockito, Swing/AWT, existing immutable Graph Workspace projection/layout/geometry values.

## Global Constraints

- Follow `AGENTS.md`: Java source and target compatibility are 8, UTF-8 encoding, four-space indentation, JUnit 4/AssertJ/Mockito tests, and use `gradle`, never Maven or `gradlew`.
- Use exactly `~/.sdkman/candidates/java/21.0.8-zulu`; set `JAVA_HOME` to that path and prepend `$JAVA_HOME/bin` to `PATH` for every Gradle command. Verify the path exists before implementation and never substitute another JDK.
- This recovery covers the verified Task 25 remediation range plus original Backlog Tasks 26 and 27 in order. Task 1 is a no-source-change audit of `0db0db9930b982cfd782f8f98b8a302f426974b9..c7d4e898e48b0f5d6aab1bc333d182b844941ac9`; it must not reroll or modify that range. Tasks 2 and 3 are the original Tasks 26 and 27. Do not combine implementation commits.
- Preserve the terminal run rooted at `.superpowers/sdd/2026-08-18-graph-workspace-batch-e-continuation`; its `DISPATCH_MISMATCH_BLOCKED` state, reports, prompts, malformed-child transcript, and audit projection are byte-preserved evidence only and must not be edited or admitted. Do not use any blocked-run report as successor evidence.
- Every child must read only the task brief and paths in its fresh dispatch context. The controller-rendered prompt bytes are authoritative. A child whose first message differs from the stored rendered prompt is inadmissible.
- Keep implementation changes within the active task's listed files. Do not add a shared test fixture, a build change, translations, resources, a new exported package, a `freeplane_api` change, a print/export API, or a compatibility fallback.
- `CanvasState`, `GraphProjection`, `LayoutFrame`, `GraphGeometry`, `GraphViewport`, and `GraphPaintState` are immutable values at their ownership boundary. Canvas mutation is EDT-local only. Canvas code never reads a Freeplane `MapModel`/`NodeModel`, calls a transformer, executes a workspace command, changes a map, writes a file, or exposes a GraphStream type.
- Geometry is the sole source for rendered bounds, hit bounds, traversal positions, and accessible bounds. Use `NodeGeometry` and `HullGeometry` already produced by the prominence-aware geometry pipeline; never recompute node scale or infer it from raw relationships.
- Safe text comes only from `ProjectedNode.label().fullText()` / `displayText()` and `ProjectedEnclosure.labels()`; never use source models, transformed text, hidden descendants, excluded content, or raw formulas. Use `Locale.ROOT` for case-insensitive search normalization.
- Preserve exactly two enclosure visual tiers from `BoundaryTier`: emphatic and subtle. Do not render `BoundaryTier.SUPPRESSED` as a visible/hittable/traversable/accessibility endpoint.
- Adaptive target limits are exact: warn when projected nodes are greater than 2,000 or projected edges are greater than 5,000. Counts above either target remain editable; rendering detail may degrade, but no endpoint, intent, search result, navigation, inspection, or accessible child is disabled by the count.
- Use automatic rendering levels: `FULL` below 500 projected nodes when not above target, `DENSE` from 500 through 2,000 nodes when not above target, and `OVER_TARGET` when either engineering limit is exceeded. Selected, hovered, and search-matched labels remain visible at every level.
- The keyboard rule is exact: selected unmodified arrows traverse, no-selection unmodified arrows pan, and Shift+arrow always accelerates pan. Enter opens the selected source endpoint. Escape cancels transient connection state before clearing selection.
- `GraphIntent` has exactly these public concrete nested types: `OpenSourceNode`, `Pin`, `Unpin`, `UnpinAll`, `Connect`, `InspectEdge`, `DeleteContributor`, `DeleteAllContributors`, and `ChangeSelection`. Connection preview and Escape cancellation are transient paint/controller state, never extra intent types.
- Accessible descriptions append a node's `NodeProminence.visibleOutgoingTargets()` only when nonzero. They never state a scale factor. Map identity must be present in text as well as color; excluded/suppressed content must be absent.
- Tasks 25-27 have no backlog-prescribed mutant. Do not invent a production mutant or modify files outside the active allowlist. Still run the focused red and green commands and `git diff --check` for each implementation task.
- Before each implementation task commit, require `test -z "$(git diff --cached --name-only)"`, stage only the explicit paths, compare the staged names exactly with the allowlist, and use the stated `2026-08-10-graph-workspace:` commit subject. Preserve the pre-existing untracked `.codegraph/` directory.

## Task 1: Independently Audit Task 25 Remediation Commit

**Implementer tier:** Capable

**Files:**
- Read only: `0db0db9930b982cfd782f8f98b8a302f426974b9..c7d4e898e48b0f5d6aab1bc333d182b844941ac9`
- Read only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/`
- Read only: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/`
- Read only: `ai-specs/tasks/backlog/006-implement-graph-workspace.md`
- Read only: `docs/superpowers/specs/2026-08-18-graph-workspace-batch-e-dispatch-recovery-design.md`
- Do not modify source, tests, the index, `HEAD`, the terminal run root, or any tracked file.

**Interfaces:**
- Audits the exact Task 25 remediation range from `0db0db9930b982cfd782f8f98b8a302f426974b9` through `c7d4e898e48b0f5d6aab1bc333d182b844941ac9`.
- The range must contain exactly the four remediation paths: `GraphPainter.java`, `GraphTheme.java`, `GraphCanvasPaintShould.java`, and `GraphViewportShould.java`. The preserved source baseline is the committed Task 25 implementation at `cd21a68a82c0914bb14f351b473e04884132284c`; the audit must verify that the remediation commit is descended from it without relying on any terminal-run report.
- Writes exactly one fresh bounded implementer report at the path supplied by the controller. A `DONE` report means the cold audit found no blocker; a `DONE_WITH_CONCERNS` report must name a non-empty concern. Do not create a source commit.

- [ ] **Step 1: Establish the immutable range and clean boundary**

Verify both SHA objects with `git cat-file -t`, inspect `git log --oneline 0db0db9930b982cfd782f8f98b8a302f426974b9..c7d4e898e48b0f5d6aab1bc333d182b844941ac9`, inspect `git diff --name-status 0db0db9930b982cfd782f8f98b8a302f426974b9..c7d4e898e48b0f5d6aab1bc333d182b844941ac9`, and confirm the changed paths are exactly the four stated remediation paths. Confirm the recovery branch has no uncommitted source or test changes. Do not read or cite the terminal run's reports, prompts, child transcript, or audit files.

- [ ] **Step 2: Cold-read the remediation against the normative contract**

Read the Task 25 sections of `ai-specs/tasks/backlog/006-implement-graph-workspace.md`, the committed Batch E design, and the actual four-file diff plus surrounding Task 25 canvas classes. Verify direct `OVER_TARGET` label suppression with selected/hovered/matched/emphatic exemptions, dormant-pin omission, stable per-map palette identity with same-map tier treatment, arrowhead direction assertions, independent viewport transform assertions, geometry-authoritative painting, exact rendering thresholds, full-bleed painting, and no print/export API. Check that the tests pin the mechanisms named by each requirement rather than merely producing image differences.

- [ ] **Step 3: Run fresh read-only verification**

Run:

````bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.canvas.GraphViewportShould' --tests 'org.freeplane.plugin.graph.canvas.GraphCanvasPaintShould' --tests 'org.freeplane.plugin.graph.canvas.AdaptiveRenderingPolicyShould' -PTestLoggingFull
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:compileJava :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.geometry.*Should' --tests 'org.freeplane.plugin.graph.projection.ProminenceCalculatorShould' -PTestLoggingFull
````

Record exact counts and results. Re-run the existing falsifiability probes if they can be executed without changing tracked files; do not create a production mutant or edit tests to force a pass. If a load-bearing defect is found, report its file and line; the controller may open a bounded fix round limited to the original ten Task 25 paths.

- [ ] **Step 4: Write the fresh audit report and verify preservation**

Before writing the report, inspect `git status --porcelain`, `git diff --stat`, and `git diff --check`. Report the exact range, four-path allowlist result, focused and compatibility results, and any findings. If clean, use `STATUS: DONE` and state `COMMIT: c7d4e898e48b0f5d6aab1bc333d182b844941ac9` as the remediation commit under audit. Confirm the terminal run root remains byte-identical and do not create, amend, stage, or modify a source commit.

## Task 2: Backlog Task 26 - Add Hit Testing, Search, and Interaction Intents

**Implementer tier:** Capable

**Files:**
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/InteractionTool.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphHitIndex.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphIntent.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphInteractionListener.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphSearchModel.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphInteractionController.java`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphCanvas.java:1-end`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPaintState.java:1-end`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphInteractionControllerShould.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphSearchModelShould.java`

**Interfaces:**
- Consumes: every Task 25 canvas type; immutable `CanvasState`, projection edge/contributor/pin values, and prominence-scaled `GraphGeometry`; `RelationshipDirection`; `ContributorKey`; `ProjectedEdgeKey`; `ProjectedEndpointKey`; and AWT mouse/key events.
- Produces:

````java
public interface GraphInteractionListener {
    void onGraphIntent(GraphIntent intent);
}
public final class GraphInteractionController {
    public GraphInteractionController(GraphInteractionListener listener);
    public void install(GraphCanvas canvas);
    public void uninstall();
    public void setTool(InteractionTool tool);
    public void setRelationshipDirection(RelationshipDirection direction);
}
````
- Defines: `InteractionTool` exactly as `SELECT` and `CONNECT`. `GraphIntent` is an abstract public base with exactly the nine concrete public nested final types named in Global Constraints. Each nested type exposes immutable getters and validates constructor/factory arguments. Use these payloads: `OpenSourceNode(ProjectedEndpointKey)`, `Pin(ProjectedNodeKey, double worldX, double worldY)`, `Unpin(ProjectedNodeKey)`, `UnpinAll()`, `Connect(ProjectedEndpointKey source, ProjectedEndpointKey target, RelationshipDirection)`, `InspectEdge(ProjectedEdgeKey)`, `DeleteContributor(ContributorKey)`, `DeleteAllContributors(ProjectedEdgeKey, List<ContributorKey>)`, and `ChangeSelection(Optional<ProjectedEndpointKey>)`.
- Defines: package-private `GraphHitIndex.from(CanvasState)`, `endpointAt(LayoutPoint)`, and `edgeAt(LayoutPoint, double worldTolerance)`. It indexes only visible node/enclosure geometry and retains deterministic projection order. `GraphSearchModel.search(CanvasState, String)` returns an unmodifiable ordered `Set<ProjectedEndpointKey>`; `tooltip(CanvasState, ProjectedEndpointKey)` returns only full safe text and owning map information.
- Extends: `GraphPaintState` by adding package-visible `withConnectionPreview(ConnectionPreview)` and `withoutConnectionPreview()` factories plus `withDimUnrelated(boolean)`. Task 25 already provides the immutable `ConnectionPreview` value and default accessors, so these additions only make the previously inert rendering state reachable. Existing Task 1 public methods and equality semantics remain valid.
- Extends: `GraphCanvas` only with package-visible current-state/hit-index/repaint/tooltip helpers required by the controller. The public Task 1 API stays source compatible.

- [ ] **Step 1: Write the failing hit-testing, search, and intent tests**

Create `GraphSearchModelShould` with a local immutable `CanvasState` fixture containing: a node with a safe display label shorter than its full safe label, one separate map name, one enclosure label, and an excluded label absent from the projection. Assert case-insensitive `Locale.ROOT` search finds full safe node text and map names, never relies on display-only truncation, returns results in deterministic endpoint order, treats blank query as an empty match set, defensively returns an unmodifiable set, and never returns excluded/unreachable text. Assert `tooltip` includes the full safe label and owning map name, not a transformed/raw source string.

Create `GraphInteractionControllerShould` with a local canvas fixture at a fixed size and an event-recording listener. Build a node whose `NodeGeometry` radius is enlarged by prominence and choose a world point that falls inside the enlarged circle but outside an unscaled base-radius circle. Assert `GraphHitIndex.endpointAt` returns that node, then assert deterministic node-over-hull precedence and stable endpoint ordering for overlaps. Add a straight projected edge fixture and assert `edgeAt` resolves within world tolerance but not beyond it.

Use dispatched synthetic `MouseEvent`, `MouseWheelEvent`, and `KeyEvent` objects on the installed canvas to assert: click emits `ChangeSelection`; hover installs a hover state and dim state; double-click emits `OpenSourceNode`; pointer-centered wheel zoom preserves the pointer's world coordinate; empty drag pans; selected unmodified arrows do not pan and are reserved for Task 27 traversal; no-selection arrows pan; Shift-arrow accelerates pan regardless of selection; a SELECT drag from an unpinned node emits `Pin` using release world coordinates; a context action over a pinned node emits `Unpin`; explicit controller helper/context path emits `UnpinAll`; CONNECT drag paints preview state, release on a different endpoint emits one `Connect` with the current `RelationshipDirection`, and Escape clears preview without emitting `Connect`; right-click on an edge emits `InspectEdge`; contributor actions emit exact `DeleteContributor` and `DeleteAllContributors` payloads; and uninstall removes every listener and stops later intent callbacks. Assert no gesture calls a workspace store or map API because the controller receives neither.

- [ ] **Step 2: Run Task 26 red and verify the failure cause**

Run:

````bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.canvas.GraphInteractionControllerShould' --tests 'org.freeplane.plugin.graph.canvas.GraphSearchModelShould' -PTestLoggingFull
````

Confirm the tests fail because Task 26 types/methods do not exist yet, not because Task 25 focused tests regressed. If Task 25 fails, repair the baseline within Task 25 only before beginning Task 26 implementation.

- [ ] **Step 3: Implement immutable hit index and safe search model**

Implement `GraphHitIndex` from one `CanvasState` snapshot. Add node entries in `projection.nodes()` order using the exact `NodeGeometry.contains` shape, so a prominence-enlarged node has the same clickable region as its painted shape. Add one enclosure entry for each non-suppressed `ProjectedEnclosure.endpointKeys()` value using the matching hull's containment region; exact endpoints sharing one collapsed hull remain individually addressable in stable endpoint order. Query nodes before hulls and resolve ties by `ProjectedEndpointKey.compareTo`. Build edge segments from `GraphGeometry.edgeAttachment` and use squared distance to a bounded line segment for `edgeAt`; sort edge candidates by `ProjectedEdgeKey` on ties. Do not inspect raw models or derive separate geometry.

Implement `GraphSearchModel` by indexing only current projected node/enclosure safe values. Normalize query/text with `Locale.ROOT`; match full safe label text and map name; blank queries return an empty ordered set. Build tooltip text from the same safe full label/map pair. Never search contributor source labels, map models, exclusion placeholders, or unprojected descendants.

- [ ] **Step 4: Implement intents, transient state, and gesture translation**

Implement `GraphIntent` immutable nested values with value equality and defensive list copies. Reject self-connect in the interaction layer by clearing preview without emitting `Connect`; command-level duplicate/coverage validation remains outside this task. `InteractionTool.SELECT` is the default and `RelationshipDirection.FORWARD` is the default. The controller accepts one listener in its constructor, does not expose a listener list, and dispatches a fully constructed intent synchronously on the EDT.

Extend `GraphPaintState` immutably with preview and dim values. A preview stores only a visible source endpoint and a current finite world pointer. Escape first removes preview; otherwise it clears the selection via `ChangeSelection(Optional.empty())`. Preserve selection/hover/search matches through every `with...` operation.

Implement `GraphInteractionController.install` with one set of mouse, motion, wheel, and key listeners; reject a second installed canvas until `uninstall`. On state replacement or each event, rebuild a `GraphHitIndex` from the current immutable canvas state. Convert component points through Task 1 viewport helpers. SELECT click changes selection; double-click opens the endpoint; hover changes hover/dim state and tooltip; empty drag pans; selected node drag emits `Pin` on release; context edge/node actions emit inspection/unpin/delete intents. CONNECT press begins preview only on a hit endpoint, drag updates its world pointer, release emits `Connect` only for a distinct endpoint, and Escape cancels. Zoom uses a positive bounded factor around the pointer. Implement Task 26's arrow behavior exactly: no selected endpoint means pan, Shift always accelerated pan, and selected unmodified arrows are consumed without pan so Task 27 can replace that path with traversal. `uninstall` removes all listeners, clears cursor/preview state, and makes later event callbacks no-ops.

- [ ] **Step 5: Run Task 26 green and the Task 25 compatibility gate**

Run the Step 2 focused command, then run:

````bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.canvas.GraphViewportShould' --tests 'org.freeplane.plugin.graph.canvas.GraphCanvasPaintShould' --tests 'org.freeplane.plugin.graph.canvas.AdaptiveRenderingPolicyShould' --tests 'org.freeplane.plugin.graph.canvas.GraphInteractionControllerShould' --tests 'org.freeplane.plugin.graph.canvas.GraphSearchModelShould' -PTestLoggingFull
````

Confirm Task 26 did not change Task 25 transform, immutable paint state, adaptive rendering, or full-bleed paint behavior.

- [ ] **Step 6: Commit Backlog Task 26 with the exact allowlist**

Run `git diff --check`. Assert the index is empty, then stage exactly these ten paths:

````bash
git add -- freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/InteractionTool.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphHitIndex.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphIntent.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphInteractionListener.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphSearchModel.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphInteractionController.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphCanvas.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPaintState.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphInteractionControllerShould.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphSearchModelShould.java
````

Require `git diff --cached --name-only` to contain exactly those ten paths, then commit:

````bash
git commit -m "2026-08-10-graph-workspace: Add graph interaction intents"
````

## Task 3: Backlog Task 27 - Expose Keyboard Traversal and Accessible Virtual Children

**Implementer tier:** Advanced

**Files:**
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/TraversalDirection.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphTraversalOrder.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/AccessibleGraphCanvas.java`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphCanvas.java:1-end`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphInteractionController.java:1-end`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/AccessibleGraphCanvasShould.java`

**Interfaces:**
- Consumes: all Task 1 and Task 2 canvas values; `CanvasState`; immutable projection node/enclosure/pin/prominence values; `GraphGeometry`; `NodeGeometry`; `HullGeometry`; `ProjectedEndpointKey`; and Swing accessibility APIs.
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
- Extends: `GraphCanvas` with a cached `AccessibleGraphCanvas`, `getAccessibleContext()` override, package-visible endpoint-bounds/name/description/action helpers, and a controller registration hook. Extends `GraphInteractionController` with package-visible traversal/activation methods used by canvas actions; existing Task 2 public methods remain source compatible.

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

Confirm the failure is the absent traversal/accessibility production API, not a Task 25 or 26 regression.

- [ ] **Step 3: Implement deterministic traversal order**

Implement `TraversalDirection` and `GraphTraversalOrder`. Construct visible endpoint positions from `GraphGeometry`: node endpoint maps to `NodeGeometry.center()`, each non-suppressed enclosure endpoint maps to its hull's `labelAnchor()`. Do not include geometry-less endpoints. `tabOrder` adds these keys to an ordered list and sorts using `ProjectedEndpointKey.compareTo`; return an unmodifiable list.

For `nearest`, require nonnull arguments, resolve the source position, filter candidates using strict half-plane comparisons (`x <`, `x >`, `y <`, `y >`), and compare squared distances without taking square roots. On equal distance choose the lower `ProjectedEndpointKey`; return `Optional.empty()` when no candidate qualifies. World coordinates retain screen axis direction because Task 1's transform uses positive zoom with unflipped Y; do not introduce a separate coordinate system or viewport-dependent ordering.

- [ ] **Step 4: Implement keyboard traversal and virtual accessibility**

Update `GraphInteractionController` so selected unmodified arrows call `GraphTraversalOrder.nearest` and emit one `ChangeSelection` for a result; no result leaves selection unchanged. Preserve Task 2 behavior for no-selection and Shift arrows exactly. Add Tab/Shift-Tab cycling over `tabOrder`, Enter source opening, and Escape priority of preview cancellation before selection clear. Update `GraphCanvas` to install the accessible context lazily and to delegate virtual-child activation to the installed controller without accessing a store/map.

Implement package-private `AccessibleGraphCanvas` by extending `AccessibleContext` and implementing `AccessibleComponent` for the canvas root. Its `getAccessibleChildrenCount` and `getAccessibleChild` derive the current traversal list on demand. Each virtual child is an `Accessible` object with `AccessibleContext`; it exposes a role suitable for an actionable graph endpoint, `AccessibleComponent` bounds transformed by the current viewport, and `AccessibleAction` entries for selection and source opening. Use a node's full safe label plus map name for the accessible name. Build its description from label, map name, current selection/pin state, endpoint type/action availability, and, for nodes only, `NodeProminence.visibleOutgoingTargets()` when greater than zero. Never emit scale, color, raw source text, or an excluded/suppressed endpoint. The virtual child objects must be lightweight wrappers, not Swing components, and must re-resolve the current endpoint snapshot on every accessibility method to avoid stale data.

- [ ] **Step 5: Run Task 27 green and the full canvas regression suite**

Run the Step 2 focused command, then run:

````bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.canvas.*Should' -PTestLoggingFull
````

Confirm all Task 25, 26, and 27 canvas tests pass together, including immutable paint, enlarged-node hit bounds, safe search, interaction uninstall, deterministic traversal, and accessible virtual children.

- [ ] **Step 6: Commit Backlog Task 27 with the exact allowlist**

Run `git diff --check`. Assert the index is empty, then stage exactly these six paths:

````bash
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

### Plan Verification

After the carry-forward audit and both implementation tasks complete, run the full graph-plugin suite from the exact JDK:

````bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test -PTestLoggingFull
````

Report the preserved Task 25 commit ID, the Task 26 and Task 27 commit IDs, the focused and full-suite results, the prior blocked-run recovery, and any unrelated pre-existing checkout state without reverting it.
