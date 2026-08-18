# Graph Workspace Batch E Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.
> Steps use checkbox (`- [ ]`) syntax for readability; controller state is
> canonical.

**Goal:** Implement backlog Tasks 25-27 as three separately committed Graph
Workspace canvas deliverables: immutable painting, interaction intents, and
keyboard-accessible virtual children.

**Architecture:** Task 1 creates a full-bleed Swing canvas that renders only
immutable `CanvasState` and EDT-local viewport/paint state. Task 2 adds a
geometry-backed hit index, safe search model, and transient gesture controller
that emits intent values rather than changing workspace or map state. Task 3
adds deterministic endpoint traversal and a package-private Swing accessibility
context with virtual children; it reuses the same immutable geometry as painting
and hit testing.

**Tech Stack:** Java 8 source/bytecode, Gradle, JUnit 4, AssertJ, Mockito,
Swing/AWT, existing immutable Graph Workspace projection/layout/geometry values.

## Global Constraints

- Follow `AGENTS.md`: Java source and target compatibility are 8, UTF-8 encoding, four-space indentation, JUnit 4/AssertJ/Mockito tests, and use `gradle`, never Maven or `gradlew`.
- Use exactly `~/.sdkman/candidates/java/21.0.8-zulu`; set `JAVA_HOME` to that path and prepend `$JAVA_HOME/bin` to `PATH` for every Gradle command. Verify the path exists before implementation and never substitute another JDK.
- This plan implements only original backlog Tasks 25, 26, and 27, in that order. Each plan task is one backlog task, has its own red-green cycle, exact allowlist, index check, staged-name check, and commit. Do not combine commits.
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
- Tasks 25-27 have no backlog-prescribed mutant. Do not invent a production mutant or modify files outside the allowlist. Still run the focused red and green commands and `git diff --check` for each task.
- Before each task commit, require `test -z "$(git diff --cached --name-only)"`, stage only the explicit paths, compare the staged names exactly with the allowlist, and use the stated `2026-08-10-graph-workspace:` commit subject. Preserve the pre-existing untracked `.codegraph/` directory.

## Task 1: Backlog Task 25 - Paint viewport, themes, and adaptive detail

**Implementer tier:** Capable

**Files:**
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphCanvas.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPainter.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphViewport.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphTheme.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/RenderingLevel.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/AdaptiveRenderingPolicy.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPaintState.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphViewportShould.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/AdaptiveRenderingPolicyShould.java`

**Interfaces:**
- Consumes: immutable `CanvasState`; `GraphProjection` ordered `nodes()`, `enclosures()`, `edges()`, `pins()`, and `prominence()`; `LayoutFrame.positions()`; `GraphGeometry` node/hull/label geometry; `NodeGeometry`, `HullGeometry`, `LabelPlacement`, `LayoutPoint`; `ProjectedEndpointKey`, `ProjectedNodeKey`, `ProjectedEdge`, `BoundaryTier`; `Viewport`; and `DisplaySettings.CanvasTheme`.
- Produces:
```java
public final class GraphCanvas extends JComponent {
    public void setCanvasState(CanvasState state);
    public void setPaintState(GraphPaintState state);
    public void setViewport(GraphViewport viewport);
    public GraphViewport viewport();
    public void fitGraph();
    public void resetZoom();
}
public final class GraphViewport {
    public static GraphViewport of(double centerX, double centerY, double zoom);
    public static GraphViewport from(Viewport persisted);
    public double centerX();
    public double centerY();
    public double zoom();
    public Viewport toPersisted();
    public boolean overlaps(double minX, double minY, double maxX, double maxY, Dimension size);
}
public final class GraphPaintState {
    public static GraphPaintState empty();
    public GraphPaintState withSelection(ProjectedEndpointKey selected);
    public GraphPaintState withHover(ProjectedEndpointKey hovered);
    public GraphPaintState withSearchMatches(Set<ProjectedEndpointKey> matches);
    public Optional<ProjectedEndpointKey> selection();
    public Optional<ProjectedEndpointKey> hover();
    public Set<ProjectedEndpointKey> searchMatches();
}
public final class AdaptiveRenderingPolicy {
    public RenderingLevel forCounts(int nodes, int edges);
    public boolean exceedsEngineeringTarget(int nodes, int edges);
}
```
- Defines: package-private `GraphPainter` with one `paint(Graphics2D, CanvasState, GraphPaintState, GraphViewport, Dimension, GraphTheme, RenderingLevel)` entry point; package-private viewport helpers `toScreen`, `toWorld`, `panPixels`, and `zoomAround` for later canvas-package interaction code; package-private `GraphCanvas.canvasState()`, `paintState()`, `setTheme(GraphTheme)`, `theme()`, `panByPixels`, and `zoomAround` helpers. These helpers do not expose mutable state outside the canvas package.
- Defines: package-private `GraphPaintState.ConnectionPreview` value plus `connectionPreview()` and `dimUnrelated()` accessors with default empty/false state. Task 25 exposes no public preview mutator, but `GraphPainter` must consume these stable accessors now so Task 26 can add transient preview/dim mutations without modifying `GraphPainter` outside its allowlist.
- Defines: `RenderingLevel` exactly as `FULL`, `DENSE`, `OVER_TARGET`. `GraphTheme` resolves `FOLLOW_FREEPLANE`, `LIGHT`, and `DARK` from the persisted enum and `UIManager` colors, provides contrasting canvas/label/selection/hover/search/warning colors plus the approved map palette, and never mutates Swing defaults. It may accept caller-supplied map colors for later window wiring but retains a deterministic approved-palette fallback for isolated canvas tests.

- [ ] **Step 1: Write the failing viewport and adaptive-policy tests**

Create `GraphViewportShould` in package `org.freeplane.plugin.graph.canvas`. Write a test that constructs `GraphViewport.of(20.0, -10.0, 2.0)`, converts a world `LayoutPoint` to a 200-by-100 component point and back through the package-visible transform helpers, and asserts round-trip equality within `1e-9`. Write validation tests asserting `of` rejects NaN/infinite center coordinates and zero, negative, NaN, and infinite zoom. Assert `from(Viewport.of(...))` preserves the three values and `toPersisted()` carries empty unknown XML. Test `overlaps` with a viewport whose visible world rectangle intersects a known world bounds rectangle and one whose rectangle is disjoint. The malformed persisted values test must construct `Viewport` directly and assert that the domain value rejects them before `GraphViewport.from` is reached.

Create `AdaptiveRenderingPolicyShould` with exact boundary cases: `(499, 0)` is `FULL`, `(500, 0)` and `(2000, 5000)` are `DENSE`, `(2001, 0)`, `(0, 5001)`, and `(2001, 5001)` are `OVER_TARGET`; `exceedsEngineeringTarget` is false at `(2000, 5000)` and true when either count is one higher. Assert negative counts reject rather than silently selecting a level.

- [ ] **Step 2: Write the failing offscreen paint tests**

Create `GraphCanvasPaintShould` using only local fixture builders in that file. Build a deterministic two-map `CanvasState` with two `ProjectedNode` values, one emphatic and one subtle `ProjectedEnclosure`, `NodeGeometry` circles, `HullGeometry` polygons, `LabelPlacement` values, an edge with a contributor multiplicity cue and arrowheads, and a `LayoutFrame` containing matching positions. Paint a sized `GraphCanvas` into `BufferedImage.TYPE_INT_ARGB`.

Assert all of the following observable behavior:

- opaque background and nonblank output;
- paint order keeps an edge visible behind an overlapping painted node, and the emphatic hull stroke/fill differs from the subtle hull;
- edge endpoints terminate at `GraphGeometry.edgeAttachment(...)`, arrowheads appear at the contributor-directed ends, and multiplicity appears only when `hasMultiplicityCue()` is true;
- an enlarged `NodeGeometry` radius produces a correspondingly larger painted node bound, without recomputing a scale in the painter;
- normal labels draw at `FULL`, unselected normal labels are reduced at `DENSE`/`OVER_TARGET`, and selected, hovered, and search-matched labels remain painted at all three levels;
- `GraphPaintState` selection/hover/search-match values change only highlight/dimming layers and defensive-copy a supplied mutable match set;
- forced light and dark themes both produce readable, distinct foreground/background pixels; and
- an above-target state still accepts `setPaintState`, `setViewport`, and paints selected content rather than disabling the component.

Add a finite non-overlapping persisted viewport scenario: install a state with known geometry, call `setViewport(GraphViewport.of(100000.0, 100000.0, 1.0))`, assert `viewport().overlaps(...)` is false, call `fitGraph()`, then assert the fitted viewport overlaps the union of visible node/hull bounds. Test `resetZoom()` returns the documented unit zoom and origin center. These tests must fail because every Task 25 class is absent.

- [ ] **Step 3: Run Task 25 red and verify the failure cause**

Run:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.canvas.GraphViewportShould' --tests 'org.freeplane.plugin.graph.canvas.GraphCanvasPaintShould' --tests 'org.freeplane.plugin.graph.canvas.AdaptiveRenderingPolicyShould' -PTestLoggingFull
```

Confirm the command fails for the missing Task 25 canvas classes or their intended APIs, not for an unrelated baseline failure. If an unrelated failure occurs, record it and do not write production code until the failure is isolated.

- [ ] **Step 4: Implement immutable viewport, policy, theme, and paint state**

Implement `GraphViewport` as a final value. Require finite `centerX`/`centerY` and finite positive `zoom`. Use the transform `screenX = width / 2.0 + (worldX - centerX) * zoom` and its symmetric Y form; inverse transforms reverse it. `panPixels(dx, dy)` changes center by `(-dx / zoom, -dy / zoom)`. `zoomAround(pointer, factor, size)` rejects nonpositive/nonfinite factors and preserves the world coordinate beneath the pointer. `overlaps` validates finite ordered world bounds and returns whether the visible world rectangle intersects them.

Implement `RenderingLevel` and `AdaptiveRenderingPolicy` with the exact thresholds from Global Constraints. Implement immutable `GraphPaintState` with `Optional` selection/hover, an insertion-ordered unmodifiable match set, a package-private empty `ConnectionPreview` field, and a false dim flag. Reject null endpoint/match entries. `withSelection`, `withHover`, and `withSearchMatches` preserve the default preview/dim values. It must not yet expose Task 26 preview or dim mutators.

Implement `GraphTheme` as a value derived from `DisplaySettings.CanvasTheme`: follow the active Look and Feel using `UIManager` colors with stable light/dark fallback values, and expose all colors/strokes/fonts through immutable accessors. Never change `UIManager` state. Retain an approved multi-hue palette for map rendering; do not add user map-color editing.

- [ ] **Step 5: Implement full-bleed immutable painting and canvas state**

Implement `GraphCanvas` as an opaque, focusable, full-bleed `JComponent` with empty `GraphPaintState`, unit-origin `GraphViewport`, and a resolved follow-Freeplane theme. `setCanvasState`, `setPaintState`, `setViewport`, and internal theme/viewport gesture methods validate inputs, replace values atomically on the EDT, and call `repaint`; they never mutate their input. In `setCanvasState`, preserve a valid current viewport. `fitGraph` finds the finite union of all visible node and non-suppressed hull bounds; when nonempty, it centers on that union and selects a positive zoom that fits it with 10 percent canvas padding. With an empty state or zero component dimensions, leave the current viewport unchanged. `resetZoom` sets center `(0, 0)` and zoom `1.0`.

Implement package-private `GraphPainter` with no print/export path. Create a copied `Graphics2D`, enable antialiasing, fill the full component bounds, install the viewport transform, and paint deterministic projection order. Skip suppressed hulls. Paint hull fills/strokes and label placements first; use emphatic versus subtle theme strokes/fills based only on `BoundaryTier`. For every projected edge, obtain boundary points with `GraphGeometry.edgeAttachment`, paint one line, paint arrowheads only when the edge says an arrow belongs at that endpoint, and draw a small multiplicity cue only when `hasMultiplicityCue()` is true. Paint nodes from `NodeGeometry.center()`/`radius()` and never recalculate prominence. Paint labels last with `FULL`/`DENSE`/`OVER_TARGET` visibility rules; force labels for selected, hovered, and matched endpoints. Paint selection, hover, and search-match outlines/dimming after base shapes without changing geometry. Also consult the default-empty `connectionPreview()` and false `dimUnrelated()` accessors so Task 26 can activate a preview line and unrelated-content dimming without a `GraphPainter` edit. Dispose the copied graphics in `finally`.

- [ ] **Step 6: Run Task 25 green and the module compilation gate**

Run the Step 3 focused command again and confirm all three classes pass. Then run:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:compileJava :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.geometry.*Should' --tests 'org.freeplane.plugin.graph.projection.ProminenceCalculatorShould' -PTestLoggingFull
```

Confirm existing geometry/prominence behavior remains green and no compile warning or error is introduced.

- [ ] **Step 7: Commit Backlog Task 25 with the exact allowlist**

Run `git diff --check`. Assert the index is empty, then stage exactly these ten paths:

```bash
git add -- freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphCanvas.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPainter.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphViewport.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphTheme.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/RenderingLevel.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/AdaptiveRenderingPolicy.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPaintState.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphViewportShould.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/AdaptiveRenderingPolicyShould.java
```

Require `git diff --cached --name-only` to contain exactly those ten paths, then commit:

```bash
git commit -m "2026-08-10-graph-workspace: Paint the graph canvas"
```

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
- Consumes: every Task 1 canvas type; immutable `CanvasState`, projection edge/contributor/pin values, and prominence-scaled `GraphGeometry`; `RelationshipDirection`; `ContributorKey`; `ProjectedEdgeKey`; `ProjectedEndpointKey`; and AWT mouse/key events.
- Produces:
```java
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
```
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

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.canvas.GraphInteractionControllerShould' --tests 'org.freeplane.plugin.graph.canvas.GraphSearchModelShould' -PTestLoggingFull
```

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

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.canvas.GraphViewportShould' --tests 'org.freeplane.plugin.graph.canvas.GraphCanvasPaintShould' --tests 'org.freeplane.plugin.graph.canvas.AdaptiveRenderingPolicyShould' --tests 'org.freeplane.plugin.graph.canvas.GraphInteractionControllerShould' --tests 'org.freeplane.plugin.graph.canvas.GraphSearchModelShould' -PTestLoggingFull
```

Confirm Task 26 did not change Task 25 transform, immutable paint state, adaptive rendering, or full-bleed paint behavior.

- [ ] **Step 6: Commit Backlog Task 26 with the exact allowlist**

Run `git diff --check`. Assert the index is empty, then stage exactly these ten paths:

```bash
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
```

Require `git diff --cached --name-only` to contain exactly those ten paths, then commit:

```bash
git commit -m "2026-08-10-graph-workspace: Add graph interaction intents"
```

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
```java
public enum TraversalDirection { UP, DOWN, LEFT, RIGHT }
public final class GraphTraversalOrder {
    public List<ProjectedEndpointKey> tabOrder(CanvasState state);
    public Optional<ProjectedEndpointKey> nearest(CanvasState state,
        ProjectedEndpointKey from, TraversalDirection direction);
}
```
- Defines: package-private `AccessibleGraphCanvas extends AccessibleContext` and implements `AccessibleComponent` as the root context returned from `GraphCanvas.getAccessibleContext()`. Its virtual endpoint children implement `Accessible`, `AccessibleAction`, and `AccessibleComponent` as needed but are not `JComponent` instances. The context and children retain no mutable snapshot; every query reads the canvas's current immutable state and paint state.
- Extends: `GraphCanvas` with a cached `AccessibleGraphCanvas`, `getAccessibleContext()` override, package-visible endpoint-bounds/name/description/action helpers, and a controller registration hook. Extends `GraphInteractionController` with package-visible traversal/activation methods used by canvas actions; existing Task 2 public methods remain source compatible.

- [ ] **Step 1: Write the failing traversal and accessibility tests**

Create `AccessibleGraphCanvasShould` with a local fixture containing three node endpoints, a non-suppressed enclosure endpoint, a suppressed enclosure endpoint, pins, distinct map names, safe labels, and a node whose `NodeProminence` is zero plus one whose value is three. Place the geometry so two candidates are equidistant in a requested direction and use endpoint keys whose natural order chooses the deterministic winner.

Assert `GraphTraversalOrder.tabOrder` returns every visible node and non-suppressed enclosure endpoint exactly once in `ProjectedEndpointKey` order and never includes the suppressed endpoint. Assert `nearest` excludes the source, filters strictly to the requested left/right/up/down half-plane, chooses minimum squared distance, and breaks exact distance ties by `ProjectedEndpointKey.compareTo`; it returns empty for missing source geometry or no candidate in the half-plane. Verify it uses node centers and hull label anchors from `GraphGeometry`, so an enlarged node's accessible bounds and directional location agree with painted/hit geometry.

Install a controller and dispatch keyboard events to assert Tab/Shift-Tab move selection through tab order, selected unmodified arrows emit a `ChangeSelection` to the traversal result, no-selection unmodified arrows pan without changing selection, Shift arrows pan at the accelerated Task 2 delta regardless of selection, Enter emits `OpenSourceNode` for the selection, and Escape cancels preview before clearing selection. Reuse Task 2 connection-preview setup in the local fixture rather than adding a shared helper.

Obtain `canvas.getAccessibleContext()` and assert its accessible children represent only visible endpoints without per-node Swing components. For a node child, assert role/name include full safe label and owning map name, description includes selected/pinned state and `3 visible outgoing targets`, accessible bounds equal the viewport-transformed prominence-scaled `NodeGeometry` bounds, and accessible actions can select/open through the controller. For the zero-reach node, assert description omits an outgoing-target phrase. For every child/context text, assert no source exclusion placeholder, raw/transformed text, map color hexadecimal value, or prominence scale such as `1.75` appears. Assert changing canvas state and viewport updates child count/bounds/text from the current immutable state rather than retaining stale virtual children.

- [ ] **Step 2: Run Task 27 red and verify the failure cause**

Run:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.canvas.AccessibleGraphCanvasShould' -PTestLoggingFull
```

Confirm the failure is the absent traversal/accessibility production API, not a Task 25 or 26 regression.

- [ ] **Step 3: Implement deterministic traversal order**

Implement `TraversalDirection` and `GraphTraversalOrder`. Construct visible endpoint positions from `GraphGeometry`: node endpoint maps to `NodeGeometry.center()`, each non-suppressed enclosure endpoint maps to its hull's `labelAnchor()`. Do not include geometry-less endpoints. `tabOrder` adds these keys to an ordered list and sorts using `ProjectedEndpointKey.compareTo`; return an unmodifiable list.

For `nearest`, require nonnull arguments, resolve the source position, filter candidates using strict half-plane comparisons (`x <`, `x >`, `y <`, `y >`), and compare squared distances without taking square roots. On equal distance choose the lower `ProjectedEndpointKey`; return `Optional.empty()` when no candidate qualifies. World coordinates retain screen axis direction because Task 1's transform uses positive zoom with unflipped Y; do not introduce a separate coordinate system or viewport-dependent ordering.

- [ ] **Step 4: Implement keyboard traversal and virtual accessibility**

Update `GraphInteractionController` so selected unmodified arrows call `GraphTraversalOrder.nearest` and emit one `ChangeSelection` for a result; no result leaves selection unchanged. Preserve Task 2 behavior for no-selection and Shift arrows exactly. Add Tab/Shift-Tab cycling over `tabOrder`, Enter source opening, and Escape priority of preview cancellation before selection clear. Update `GraphCanvas` to install the accessible context lazily and to delegate virtual-child activation to the installed controller without accessing a store/map.

Implement package-private `AccessibleGraphCanvas` by extending `AccessibleContext` and implementing `AccessibleComponent` for the canvas root. Its `getAccessibleChildrenCount` and `getAccessibleChild` derive the current traversal list on demand. Each virtual child is an `Accessible` object with `AccessibleContext`; it exposes a role suitable for an actionable graph endpoint, `AccessibleComponent` bounds transformed by the current viewport, and `AccessibleAction` entries for selection and source opening. Use a node's full safe label plus map name for the accessible name. Build its description from label, map name, current selection/pin state, endpoint type/action availability, and, for nodes only, `NodeProminence.visibleOutgoingTargets()` when greater than zero. Never emit scale, color, raw source text, or an excluded/suppressed endpoint. The virtual child objects must be lightweight wrappers, not Swing components, and must re-resolve the current endpoint snapshot on every accessibility method to avoid stale data.

- [ ] **Step 5: Run Task 27 green and the full canvas regression suite**

Run the Step 2 focused command, then run:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.canvas.*Should' -PTestLoggingFull
```

Confirm all Task 25, 26, and 27 canvas tests pass together, including immutable paint, enlarged-node hit bounds, safe search, interaction uninstall, deterministic traversal, and accessible virtual children.

- [ ] **Step 6: Commit Backlog Task 27 with the exact allowlist**

Run `git diff --check`. Assert the index is empty, then stage exactly these six paths:

```bash
git add -- freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/TraversalDirection.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphTraversalOrder.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/AccessibleGraphCanvas.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphCanvas.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphInteractionController.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/AccessibleGraphCanvasShould.java
```

Require `git diff --cached --name-only` to contain exactly those six paths, then commit:

```bash
git commit -m "2026-08-10-graph-workspace: Make the graph keyboard accessible"
```

### Plan Verification

After all three task commits, run the full graph-plugin suite from the exact JDK:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test -PTestLoggingFull
```

Report the focused and full-suite results, all three commit IDs, and any unrelated pre-existing checkout state without reverting it.
