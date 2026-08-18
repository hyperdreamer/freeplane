# Graph Workspace Batch E Task-Blocked Recovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.

**Goal:** Repair the two load-bearing Task 25 canvas findings, then implement Backlog Tasks 26 and 27 for graph interaction and accessibility.

**Architecture:** Task 1 keeps the canvas immutable-value boundary but makes persisted map colors explicit in `GraphTheme` and binds the resulting theme through the real `GraphCanvas` update seam. It also gives emphatic enclosure labels a dedicated font before ordinary LOD selection. Task 2 adds snapshot-based hit testing, safe search, and immutable interaction intents; Task 3 layers deterministic keyboard traversal and virtual Swing accessibility on those values. Each task is independently tested, reviewed, and committed before the next task starts.

**Tech Stack:** Java 8 source/target compatibility, Java 21.0.8-zulu runtime, Gradle, JUnit 4, AssertJ, Mockito, Swing/AWT, immutable Graph Workspace projection/layout/geometry values.

## Global Constraints

- Follow `AGENTS.md`: Java source and target compatibility are 8, UTF-8 encoding, four-space indentation, JUnit 4/AssertJ/Mockito tests, and use `gradle`, never Maven or `gradlew`.
- Use exactly `~/.sdkman/candidates/java/21.0.8-zulu`; set `JAVA_HOME` to that path and prepend `$JAVA_HOME/bin` to `PATH` for every Gradle command. Verify the path exists before implementation and never substitute another JDK.
- The source baseline for the recovery is `c7d4e898e48b0f5d6aab1bc333d182b844941ac9`. The current branch also contains only the committed recovery design documents before implementation. Task 1 is the four-file source/test remediation below; Task 2 and Task 3 are the original Backlog Tasks 26 and 27 in order. Do not combine implementation commits.
- Preserve the terminal run rooted at `.superpowers/sdd/2026-08-18-graph-workspace-batch-e-continuation` and the blocked dispatch-recovery run rooted at `.superpowers/sdd/2026-08-18-graph-workspace-batch-e-dispatch-recovery`; their states, reports, prompts, child transcripts, and audit projections are byte-preserved historical evidence only and must not be edited or cited as successor evidence.
- Preserve the pre-existing untracked `.codegraph/` directory. Do not revert unrelated user changes or ignored artifacts.
- Every child must read only its dispatched task brief and the listed paths. The controller-rendered prompt bytes are authoritative. A child whose first message differs from the stored rendered prompt is inadmissible.
- Keep implementation changes within the active task's explicit allowlist. Do not add a shared test fixture, build change, translation, resource, new exported package, `freeplane_api` change, print/export API, or compatibility fallback.
- Except for the explicitly required public `GraphCanvas.setTheme(GraphTheme)` binding in Task 1, preserve the approved public canvas interfaces. `CanvasState`, `GraphProjection`, `LayoutFrame`, `GraphGeometry`, `GraphViewport`, and `GraphPaintState` remain immutable values at their ownership boundary. Canvas mutation is EDT-local only.
- Canvas code never reads a Freeplane `MapModel`/`NodeModel`, calls a transformer, executes a workspace command, changes a map, writes a file, or exposes a GraphStream type. `GraphTheme` receives immutable registered `MapReference` values at the ownership boundary and stores only copied visual values.
- Geometry is the sole source for rendered bounds, hit bounds, traversal positions, and accessible bounds. Use `NodeGeometry` and `HullGeometry` already produced by the prominence-aware geometry pipeline; never recompute node scale or infer it from raw relationships.
- Safe text comes only from `ProjectedNode.label().fullText()` / `displayText()` and `ProjectedEnclosure.labels()`; never use source models, transformed text, hidden descendants, excluded content, or raw formulas. Use `Locale.ROOT` for case-insensitive search normalization.
- Preserve exactly two enclosure visual tiers from `BoundaryTier`: emphatic and subtle. Do not render `BoundaryTier.SUPPRESSED` as a visible, hittable, traversable, or accessible endpoint.
- Adaptive target limits are exact: warn when projected nodes are greater than 2,000 or projected edges are greater than 5,000. Counts above either target remain editable; rendering detail may degrade, but no endpoint, intent, search result, navigation, inspection, or accessible child is disabled by the count.
- Use automatic rendering levels: `FULL` below 500 projected nodes when not above target, `DENSE` from 500 through 2,000 nodes when not above target, and `OVER_TARGET` when either engineering limit is exceeded. Selected, hovered, and search-matched labels remain visible at every level. Emphatic enclosure labels remain visible at every level with their dedicated font.
- The keyboard rule is exact: selected unmodified arrows traverse, no-selection unmodified arrows pan, and Shift+arrow always accelerates pan. Enter opens the selected source endpoint. Escape cancels transient connection state before clearing selection.
- `GraphIntent` has exactly these public concrete nested types: `OpenSourceNode`, `Pin`, `Unpin`, `UnpinAll`, `Connect`, `InspectEdge`, `DeleteContributor`, `DeleteAllContributors`, and `ChangeSelection`. Connection preview and Escape cancellation are transient paint/controller state, never extra intent types.
- Accessible descriptions append a node's `NodeProminence.visibleOutgoingTargets()` only when nonzero. They never state a scale factor. Map identity must be present in text as well as color; excluded and suppressed content must be absent.
- Tasks 25-27 have no backlog-prescribed mutant. Do not invent a production mutant or modify files outside the active allowlist. Still run the focused red and green commands and `git diff --check` for each implementation task.
- Before each implementation task commit, require `test -z "$(git diff --cached --name-only)"`, stage only the explicit paths, compare the staged names exactly with the allowlist, and use a commit subject beginning `2026-08-10-graph-workspace:`. Do not stage `.codegraph/` or any unrelated path.

## Task 1: Remediate Task 25 persistent map colors and emphatic typography

**Implementer tier:** Capable

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphTheme.java:1-end`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPainter.java:1-end`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphCanvas.java:1-end`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java:1-end`

**Interfaces:**

- Consumes: immutable `MapReference` values and `MapReferenceId` values, `DisplaySettings.CanvasTheme`, `BoundaryTier`, `RenderingLevel`, existing `CanvasState`, `GraphPaintState`, `GraphGeometry`, and the current Task 25 canvas implementation at source baseline `c7d4e898e48b0f5d6aab1bc333d182b844941ac9`.
- Produces: `public static GraphTheme resolve(CanvasTheme requested, List<MapReference> registeredMaps)`; the existing no-argument `GraphTheme.resolve(CanvasTheme)` for empty canvases only; `public Font emphaticLabelFont()`; and a public `GraphCanvas.setTheme(GraphTheme theme)` that retains the existing EDT update and repaint behavior.
- Removes: the `resolve(CanvasTheme, List<Color>)` overload, `mapPalette()`, the local palette field/constants, and all ID-hash-derived map color selection. A missing color for a requested non-suppressed map ID throws `IllegalStateException` with the ID in the message.
- Preserves: package-private `GraphPainter` and its full-bleed `JComponent`/offscreen painting boundary, normal node/subtle label LOD behavior, selection/hover/search forced visibility, dormant-pin omission, geometry-authoritative bounds, and the public Task 25 methods other than the explicitly public theme setter.

- [ ] **Step 1: Write the failing Task 25 remediation tests**

Update only `GraphCanvasPaintShould` before changing production files. Keep local fixtures self-contained. Use two valid UUID map IDs whose `String.hashCode()` values have the same `Math.floorMod(hash, 6)` result, such as IDs ending in `...0001` and `...0007`, and assign them different approved persisted colors through `MapReference.of`. Build a `registeredMaps()` list and route every enclosure-paint fixture through `GraphTheme.resolve(CanvasTheme.LIGHT, registeredMaps())` and a `GraphCanvas` whose theme is installed with `setTheme` before `setCanvasState`.

Add assertions that the collision pair paints different fill and stroke treatments matching the two `MapReference.color()` values, that a newly reconstructed theme from equivalent registered references produces identical treatments, and that reflection finds `GraphCanvas.setTheme(GraphTheme.class)` as public. Paint a state containing a map absent from the registered list and assert the specified `IllegalStateException`; do not accept a default palette color.

Extend the label fixture with unforced emphatic, unforced subtle, ordinary node, selected/hovered/search-matched ordinary nodes, and a `SUPPRESSED` enclosure whose hull and label are isolated from other ink. At zoom 1, assert `theme.emphaticLabelFont().isBold()` and a size strictly larger than `theme.labelFont().getSize2D()`. For each of `FULL`, `DENSE`, and `OVER_TARGET`, compare isolated emphatic glyph bounds and `FontMetrics` against the zoom-adjusted dedicated font and against the normal font, proving the dedicated font is selected rather than merely leaving pixels visible. Assert emphatic labels remain visible at all three levels, subtle and ordinary node labels retain their existing level-specific behavior, forced ordinary labels remain visible with the normal full-detail font at `OVER_TARGET`, and the suppressed hull and label produce no pixels even when forced by selection, hover, and search state.

Update the existing palette/theme helper tests to use registered `MapReference` values rather than a caller color list. Retain the existing transform, arrows, multiplicity, active-pin, viewport, adaptive-threshold, opaque-background, and geometry-authority assertions. Do not add a production test hook or a shared fixture file.

- [ ] **Step 2: Run the red test and verify the failure cause**

Run:

````bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.canvas.GraphCanvasPaintShould' -PTestLoggingFull
````

Run this after the test-only edit and before modifying `GraphTheme.java`, `GraphPainter.java`, or `GraphCanvas.java`. Confirm the failure is caused by the absent `List<MapReference>` resolver/public binding/emphatic font behavior on the untouched source baseline, not by an unrelated build or environment problem. Record the exact compiler or assertion failure in the implementer report.

- [ ] **Step 3: Implement immutable persisted map-color resolution**

Replace the palette-based `GraphTheme` map state with an unmodifiable defensive copy of a `LinkedHashMap<MapReferenceId, Color>`. Remove `APPROVED_PALETTE`, the color-list resolver, `mapPalette()`, and the `Math.floorMod` lookup. Parse each already-validated canonical `MapReference.color()` string exactly once into an AWT `Color` while resolving; reject null references and duplicate IDs rather than silently replacing an assignment. Keep the no-argument resolver backed by an empty map.

Make `hullFill` and `hullStroke` look up the requested ID in the copied map assignment and throw `IllegalStateException` when no color exists. Preserve the existing tier-specific blend weights, hull stroke styles, background/foreground theme selection, and all non-map colors. Add the dedicated emphatic font as a bold, larger base font than the normal full-detail font and expose it through `emphaticLabelFont()`; do not make the normal `labelFont(RenderingLevel)` return it.

- [ ] **Step 4: Implement font precedence and the real canvas binding seam**

Change only the existing `GraphCanvas.setTheme(GraphTheme)` visibility to public; keep its null validation, EDT dispatch, stored immutable theme, and repaint behavior unchanged. Do not import or read a workspace store or source map model in `GraphCanvas`.

In `GraphPainter`, skip suppressed enclosures before hull, label, and highlight work. For each non-suppressed enclosure, compute whether it is emphatic and make the label-font helper choose `theme.emphaticLabelFont()` first for that tier, then apply the same viewport zoom compensation. Only non-emphatic labels may choose between forced normal full-detail and ordinary `FULL`/`DENSE`/`OVER_TARGET` fonts. Keep the emphatic-required visibility rule ahead of ordinary LOD suppression. Preserve the current forced selection/hover/search behavior for node and subtle labels and preserve all geometry and paint ordering.

- [ ] **Step 5: Run the green focused and compatibility gates**

Run the focused canvas suite:

````bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.canvas.GraphViewportShould' --tests 'org.freeplane.plugin.graph.canvas.GraphCanvasPaintShould' --tests 'org.freeplane.plugin.graph.canvas.AdaptiveRenderingPolicyShould' -PTestLoggingFull
````

Then run the geometry/prominence compatibility gate:

````bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:compileJava :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.geometry.*Should' --tests 'org.freeplane.plugin.graph.projection.ProminenceCalculatorShould' -PTestLoggingFull
````

Both commands must pass with no failures. Inspect `git diff --check`, `git diff --stat`, and `git status --porcelain`; the only source/test changes may be the four Task 1 paths.

- [ ] **Step 6: Commit Task 1 with the exact four-file allowlist**

Require an empty index before staging:

````bash
test -z "$(git diff --cached --name-only)"
git add -- freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphTheme.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPainter.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphCanvas.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java
````

Require `git diff --cached --name-only` to contain exactly those four paths, then commit:

````bash
git commit -m "2026-08-10-graph-workspace: Remediate graph canvas themes"
````

Report the resulting commit SHA and the focused/compatibility test results. Do not stage or modify any Task 26/27 path.

## Task 2: Backlog Task 26 - Add hit testing, search, and interaction intents

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

- Consumes: every Task 1 canvas type, including the public `GraphCanvas.setTheme(GraphTheme)` binding; immutable `CanvasState`, projection edge/contributor/pin values, prominence-scaled `GraphGeometry`; `RelationshipDirection`; `ContributorKey`; `ProjectedEdgeKey`; `ProjectedEndpointKey`; and AWT mouse/key events.
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
- Extends: `GraphPaintState` by adding package-visible `withConnectionPreview(ConnectionPreview)` and `withoutConnectionPreview()` factories plus `withDimUnrelated(boolean)`. Task 1 already provides the immutable `ConnectionPreview` value and default accessors, so these additions only make the previously inert rendering state reachable. Existing Task 1 public methods and equality semantics remain valid.
- Extends: `GraphCanvas` only with package-visible current-state/hit-index/repaint/tooltip helpers required by the controller. Preserve the public Task 1 theme binding and all other public methods.

- [ ] **Step 1: Write the failing hit-testing, search, and intent tests**

Create `GraphSearchModelShould` with a local immutable `CanvasState` fixture containing: a node with a safe display label shorter than its full safe label, one separate map name, one enclosure label, and an excluded label absent from the projection. Assert case-insensitive `Locale.ROOT` search finds full safe node text and map names, never relies on display-only truncation, returns results in deterministic endpoint order, treats blank query as an empty match set, defensively returns an unmodifiable set, and never returns excluded/unreachable text. Assert `tooltip` includes the full safe label and owning map name, not a transformed/raw source string.

Create `GraphInteractionControllerShould` with a local canvas fixture at a fixed size and an event-recording listener. Build a node whose `NodeGeometry` radius is enlarged by prominence and choose a world point that falls inside the enlarged circle but outside an unscaled base-radius circle. Assert `GraphHitIndex.endpointAt` returns that node, then assert deterministic node-over-hull precedence and stable endpoint ordering for overlaps. Add a straight projected edge fixture and assert `edgeAt` resolves within world tolerance but not beyond it.

Use dispatched synthetic `MouseEvent`, `MouseWheelEvent`, and `KeyEvent` objects on the installed canvas to assert: click emits `ChangeSelection`; hover installs a hover state and dim state; double-click emits `OpenSourceNode`; pointer-centered wheel zoom preserves the pointer's world coordinate; empty drag pans; selected unmodified arrows do not pan and are reserved for Task 27 traversal; no-selection arrows pan; Shift-arrow accelerates pan regardless of selection; a SELECT drag from an unpinned node emits `Pin` using release world coordinates; a context action over a pinned node emits `Unpin`; explicit controller helper/context path emits `UnpinAll`; CONNECT drag paints preview state, release on a different endpoint emits one `Connect` with the current `RelationshipDirection`, and Escape clears preview without emitting `Connect`; right-click on an edge emits `InspectEdge`; contributor actions emit exact `DeleteContributor` and `DeleteAllContributors` payloads; and uninstall removes every listener and stops later intent callbacks. Assert no gesture calls a workspace store or map API because the controller receives neither.

- [ ] **Step 2: Run Task 26 red and verify the failure cause**

Run:

````bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.canvas.GraphInteractionControllerShould' --tests 'org.freeplane.plugin.graph.canvas.GraphSearchModelShould' -PTestLoggingFull
````

Confirm the tests fail because Task 26 types/methods do not exist yet, not because Task 1 focused tests regressed. If Task 1 fails, repair the baseline within Task 1 only before beginning Task 2 implementation.

- [ ] **Step 3: Implement immutable hit index and safe search model**

Implement `GraphHitIndex` from one `CanvasState` snapshot. Add node entries in `projection.nodes()` order using the exact `NodeGeometry.contains` shape, so a prominence-enlarged node has the same clickable region as its painted shape. Add one enclosure entry for each non-suppressed `ProjectedEnclosure.endpointKeys()` value using the matching hull's containment region; exact endpoints sharing one collapsed hull remain individually addressable in stable endpoint order. Query nodes before hulls and resolve ties by `ProjectedEndpointKey.compareTo`. Build edge segments from `GraphGeometry.edgeAttachment` and use squared distance to a bounded line segment for `edgeAt`; sort edge candidates by `ProjectedEdgeKey` on ties. Do not inspect raw models or derive separate geometry.

Implement `GraphSearchModel` by indexing only current projected node/enclosure safe values. Normalize query/text with `Locale.ROOT`; match full safe label text and map name; blank queries return an empty ordered set. Build tooltip text from the same safe full label/map pair. Never search contributor source labels, map models, exclusion placeholders, or unprojected descendants.

- [ ] **Step 4: Implement intents, transient state, and gesture translation**

Implement `GraphIntent` immutable nested values with value equality and defensive list copies. Reject self-connect in the interaction layer by clearing preview without emitting `Connect`; command-level duplicate/coverage validation remains outside this task. `InteractionTool.SELECT` is the default and `RelationshipDirection.FORWARD` is the default. The controller accepts one listener in its constructor, does not expose a listener list, and dispatches a fully constructed intent synchronously on the EDT.

Extend `GraphPaintState` immutably with preview and dim values. A preview stores only a visible source endpoint and a current finite world pointer. Escape first removes preview; otherwise it clears the selection via `ChangeSelection(Optional.empty())`. Preserve selection/hover/search matches through every `with...` operation.

Implement `GraphInteractionController.install` with one set of mouse, motion, wheel, and key listeners; reject a second installed canvas until `uninstall`. On state replacement or each event, rebuild a `GraphHitIndex` from the current immutable canvas state. Convert component points through Task 1 viewport helpers. SELECT click changes selection; double-click opens the endpoint; hover changes hover/dim state and tooltip; empty drag pans; selected node drag emits `Pin` on release; context edge/node actions emit inspection/unpin/delete intents. CONNECT press begins preview only on a hit endpoint, drag updates its world pointer, release emits `Connect` only for a distinct endpoint, and Escape cancels. Zoom uses a positive bounded factor around the pointer. Implement Task 26's arrow behavior exactly: no selected endpoint means pan, Shift always accelerated pan, and selected unmodified arrows are consumed without pan so Task 3 can replace that path with traversal. `uninstall` removes all listeners, clears cursor/preview state, and makes later event callbacks no-ops.

- [ ] **Step 5: Run Task 26 green and the Task 1 compatibility gate**

Run the Step 2 focused command, then run:

````bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.canvas.GraphViewportShould' --tests 'org.freeplane.plugin.graph.canvas.GraphCanvasPaintShould' --tests 'org.freeplane.plugin.graph.canvas.AdaptiveRenderingPolicyShould' --tests 'org.freeplane.plugin.graph.canvas.GraphInteractionControllerShould' --tests 'org.freeplane.plugin.graph.canvas.GraphSearchModelShould' -PTestLoggingFull
````

Confirm Task 2 did not change Task 1 transform, immutable paint state, adaptive rendering, map-color binding, emphatic typography, or full-bleed paint behavior.

- [ ] **Step 6: Commit Backlog Task 26 with the exact allowlist**

Run `git diff --check`. Assert the index is empty, then stage exactly these ten paths:

````bash
test -z "$(git diff --cached --name-only)"
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

## Task 3: Backlog Task 27 - Expose keyboard traversal and accessible virtual children

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
- Extends: `GraphCanvas` with a cached `AccessibleGraphCanvas`, `getAccessibleContext()` override, package-visible endpoint-bounds/name/description/action helpers, and a controller registration hook. Extends `GraphInteractionController` with package-visible traversal/activation methods used by canvas actions; existing Task 2 public methods and the Task 1 public theme binding remain source compatible.

- [ ] **Step 1: Write the failing traversal and accessibility tests**

Create `AccessibleGraphCanvasShould` with a local fixture containing three node endpoints, a non-suppressed enclosure endpoint, a suppressed enclosure endpoint, pins, distinct map names, safe labels, and a node whose `NodeProminence` is zero plus one whose value is three. Place the geometry so two candidates are equidistant in a requested direction and use endpoint keys whose natural order chooses the deterministic winner.

Assert `GraphTraversalOrder.tabOrder` returns every visible node and non-suppressed enclosure endpoint exactly once in `ProjectedEndpointKey` order and never includes the suppressed endpoint. Assert `nearest` excludes the source, filters strictly to the requested left/right/up/down half-plane, chooses minimum squared distance, and breaks exact distance ties by `ProjectedEndpointKey.compareTo`; it returns empty for missing source geometry or no candidate in the half-plane. Verify it uses node centers and hull label anchors from `GraphGeometry`, so an enlarged node's accessible bounds and directional location agree with painted/hit geometry.

Install a controller and dispatch keyboard events to assert Tab/Shift-Tab move selection through tab order, selected unmodified arrows emit a `ChangeSelection` to the traversal result, no-selection unmodified arrows pan without changing selection, Shift arrows pan at the accelerated Task 2 delta regardless of selection, Enter emits `OpenSourceNode` for the selection, and Escape cancels preview before clearing selection. Reuse Task 2 connection-preview setup in the local fixture rather than adding a shared helper.

Obtain `canvas.getAccessibleContext()` and assert its accessible children represent only visible endpoints without per-node Swing components. For a node child, assert role/name include full safe label and owning map name, description includes selected/pinned state and `3 visible outgoing targets`, accessible bounds equal the viewport-transformed prominence-scaled `NodeGeometry` bounds, and accessible actions can select/open through the controller. For the zero-reach node, assert description omits an outgoing-target phrase. For every child/context text, assert no source exclusion placeholder, raw/transformed text, map color hexadecimal value, or prominence scale such as `1.75` appears. Assert changing canvas state and viewport updates child count/bounds/text from the current immutable state rather than retaining stale virtual children.

- [ ] **Step 2: Run Task 3 red and verify the failure cause**

Run:

````bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.canvas.AccessibleGraphCanvasShould' -PTestLoggingFull
````

Confirm the failure is the absent traversal/accessibility production API, not a Task 1 or Task 2 regression.

- [ ] **Step 3: Implement deterministic traversal order**

Implement `TraversalDirection` and `GraphTraversalOrder`. Construct visible endpoint positions from `GraphGeometry`: node endpoint maps to `NodeGeometry.center()`, each non-suppressed enclosure endpoint maps to its hull's `labelAnchor()`. Do not include geometry-less endpoints. `tabOrder` adds these keys to an ordered list and sorts using `ProjectedEndpointKey.compareTo`; return an unmodifiable list.

For `nearest`, require nonnull arguments, resolve the source position, filter candidates using strict half-plane comparisons (`x <`, `x >`, `y <`, `y >`), and compare squared distances without taking square roots. On equal distance choose the lower `ProjectedEndpointKey`; return `Optional.empty()` when no candidate qualifies. World coordinates retain screen axis direction because Task 1's transform uses positive zoom with unflipped Y; do not introduce a separate coordinate system or viewport-dependent ordering.

- [ ] **Step 4: Implement keyboard traversal and virtual accessibility**

Update `GraphInteractionController` so selected unmodified arrows call `GraphTraversalOrder.nearest` and emit one `ChangeSelection` for a result; no result leaves selection unchanged. Preserve Task 2 behavior for no-selection and Shift arrows exactly. Add Tab/Shift-Tab cycling over `tabOrder`, Enter source opening, and Escape priority of preview cancellation before selection clear. Update `GraphCanvas` to install the accessible context lazily and to delegate virtual-child activation to the installed controller without accessing a store/map.

Implement package-private `AccessibleGraphCanvas` by extending `AccessibleContext` and implementing `AccessibleComponent` for the canvas root. Its `getAccessibleChildrenCount` and `getAccessibleChild` derive the current traversal list on demand. Each virtual child is an `Accessible` object with `AccessibleContext`; it exposes a role suitable for an actionable graph endpoint, `AccessibleComponent` bounds transformed by the current viewport, and `AccessibleAction` entries for selection and source opening. Use a node's full safe label plus map name for the accessible name. Build its description from label, map name, current selection/pin state, endpoint type/action availability, and, for nodes only, `NodeProminence.visibleOutgoingTargets()` when greater than zero. Never emit scale, color, raw source text, or an excluded/suppressed endpoint. The virtual child objects must be lightweight wrappers, not Swing components, and must re-resolve the current endpoint snapshot on every accessibility method to avoid stale data.

- [ ] **Step 5: Run Task 3 green and the full canvas regression suite**

Run the Step 2 focused command, then run:

````bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.canvas.*Should' -PTestLoggingFull
````

Confirm all Task 1, Task 2, and Task 3 canvas tests pass together, including immutable paint, persisted map-color binding, emphatic typography, enlarged-node hit bounds, safe search, interaction uninstall, deterministic traversal, and accessible virtual children.

- [ ] **Step 6: Commit Backlog Task 27 with the exact allowlist**

Run `git diff --check`. Assert the index is empty, then stage exactly these six paths:

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

### Final Verification

After the carry-forward audit and all three implementation tasks complete, the controller's Frontier final review must cover the entire branch from merge base `02f02355d9a33851a8a4417c4610d1897f716a50` through final `HEAD`, reconcile F-1 and F-2, and run the full graph-plugin suite:

````bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test -PTestLoggingFull
````

The final report must name the Task 1, Task 2, and Task 3 commit IDs, focused and full-suite results, the preserved blocked-run recovery, and any unrelated pre-existing checkout state without reverting it.
