# Graph Workspace Ancestor Boundary Derivation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.
> Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore automatic Level 1 and Level 2 structural ancestor boundaries while retaining only nodes marked by `<graph_group version="1"/>` as topological Graph Workspace vertices.

**Architecture:** `ProjectionEngine` becomes the single authority for a depth-preserving source traversal. It emits a `ProjectedNode` at every included group marker, stops descent below that marker, and emits only the structural ancestor `ProjectedEnclosure` tiers allowed for the active-map count. The resulting node and enclosure collections drive endpoint resolution, pin projection, layout, painting, accessibility, search, and window selection through their established internal contracts. Boundary state stays transient and no graph-file persistence behavior changes.

**Tech Stack:** Java 8 source and bytecode target; Java 21 Zulu toolchain; Gradle; JUnit 4; AssertJ; Mockito; Freeplane OSGi plugin architecture; GraphStream 1.3.

## Global Constraints

- Work only in `/data/home/guest/Development/freeplane/.worktrees/graph-workspace` on `feature/graph-workspace`.
- Use `JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu` and escalated `gradle`; never use `gradlew` or Maven.
- Every Gradle verification command includes `-PTestLoggingFull --rerun-tasks`.
- Keep Java source compatible with Java 8. Do not add APIs beyond Java 8.
- Do not change `freeplane_api`, OSGi manifests, dependency declarations, `.fpg` XML schemas, persistence attributes, or persistence versions.
- A raw depth is the exact number of parent-child hops from `MapSnapshot.root()`: root is 0, its direct child is 1, and so on. Never compress, skip, or reinterpret unary and pass-through source nodes while determining depth.
- A marked `NodeSnapshot.graphGroup()` is an atomic `ProjectedNode`, including a marked node that has children. Descendants below it do not become projected nodes or boundaries.
- An unmarked source node is never a `ProjectedNode`. It is a boundary only when it is non-excluded, has reachable projected content, and its raw depth is enabled by the map-count table.
- Boundary tiers are fixed: one active map has suppressed depth 0, emphatic depth 1, subtle depth 2; two or more active maps have emphatic depth 0 and subtle depth 1. All other depths are transparent. Loading, missing, and unavailable registered maps count toward the active-map mode count.
- Do not merge a retained Level 1 boundary through a unary Level 2 boundary. Transparent depths flatten only by recursively forwarding their reachable content to the nearest retained ancestor.
- Edges, relationship endpoints, connector endpoints, and pins resolve only to marked `ProjectedNode` vertices. Structural boundaries remain spatial and navigable UI targets, but never become semantic graph endpoints.
- Every task follows strict TDD: add or update a falsifiable test, run it red, make the smallest implementation change, rerun it green, run the listed focused regression set, inspect the diff, and create the specified commit.
- Respect each task's file allowlist. If a task needs a file outside its allowlist, stop and obtain a plan amendment before editing it.

## Task 1: Rebuild Strict-Depth Projection, Semantic Endpoints, and Pins

**Implementer tier:** Capable

**Files:**

- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/projection/AncestorBoundaryDerivationShould.java`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/projection/ProjectionEngine.java:1-650`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/projection/GraphProjection.java:1-130`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/projection/StructuralProjectionShould.java:1-430`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/projection/GroupOnlyProjectionShould.java:1-300`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/projection/EndpointResolutionShould.java:1-520`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/projection/EdgeProjectionShould.java:1-420`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/projection/EnclosureTierShould.java:1-260`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/projection/ProjectionDeterminismShould.java:1-300`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/projection/ProminenceCalculatorShould.java:1-320`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/projection/ProjectedEndpointVisibilityShould.java:1-260`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/projection/ProjectionPureReloadShould.java:1-360`

**Interfaces:**

- `ProjectionEngine.project(ProjectionInput)` continues to return `GraphProjection` without a public API change.
- Use the existing input factories and accessors: `MapSnapshot.of(...)`, `NodeSnapshot.of(...)`, `MapReference.of(...)`, `WorkspaceDocument.createVersion1(...).toBuilder().maps(...).build()`, and `ProjectionInput.of(...)`.
- Emit marked nodes with `ProjectedNode.of(ProjectedNodeKey.of(sourceKey), label, mapName, true)` and retain source-aware enclosure keys through `EnclosureKey.of(...)` and `EnclosureHullKey.of(...)`.
- Keep the existing `ProjectedEndpointKey`, `RelationshipResolution`, `ConnectorDescriptor`, `ConnectorSnapshot`, `PinProjection`, and `ProjectedEndpointVisibility` types unchanged.

- [ ] **Step 1: Add red strict-depth and atomization coverage using real fixture APIs**

Create `AncestorBoundaryDerivationShould` in the existing `org.freeplane.plugin.graph.projection` test package. Build fixtures with the same factories already used by `StructuralProjectionShould`; do not invent document or snapshot constructors. The local fixture helpers must create a `MapReference` with `MapReference.of(...)`, a document with `WorkspaceDocument.createVersion1(...).toBuilder().maps(...).build()`, a root snapshot with `MapSnapshot.of(...)`, and nodes with `NodeSnapshot.of(...)`.

Add these independently falsifiable tests:

- The one-map math-shape tree `root -> ZFC`, with sibling Level 2 children `Axioms` and `Basic Definitions and Theorems`, yields four marked nodes, a suppressed root, an emphatic `ZFC` hull, and those two subtle child hulls. Assert that the three axiom nodes are direct nodes of `Axioms`, the theorem is a direct node of `Basic Definitions and Theorems`, and parent hull links are exact.
- A strict-depth chain `root -> Level 1 -> Level 2 -> Level 3 -> marked Group` preserves the Level 1 and Level 2 boundaries, emits no Level 3 hull, and forwards the marked node to the Level 2 boundary. This proves a raw depth-three pass-through cannot replace or collapse the Level 2 source node.
- A marked node with marked or unmarked descendants emits exactly the marked parent node and no descendant node or descendant enclosure.
- A marked node at raw depth 1 is a node directly under the suppressed root; it is not converted into a Level 1 boundary.
- A two-registered-map projection, with only one available snapshot, still emits the available root as `EMPHATIC`, its raw Level 1 boundary as `SUBTLE`, and no raw Level 2 boundary. This proves registered active-map count rather than snapshot count selects the tier table.
- Excluded branches and non-root branches containing no reachable marked node do not emit boundaries. An available map whose root has no reachable marked node still emits exactly its map-root frame.
- `projectedNodeCount()` equals the number of marked projected nodes, not the number of visible non-root enclosures.

Run the new test before implementation and record that it fails because the current group-only traversal produces group enclosures and drops structural ancestors:

```bash
cd /data/home/guest/Development/freeplane/.worktrees/graph-workspace
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test -PTestLoggingFull --rerun-tasks --tests 'org.freeplane.plugin.graph.projection.AncestorBoundaryDerivationShould'
```

- [ ] **Step 2: Replace group-only traversal with the depth-preserving projection tree**

In `ProjectionEngine`, remove the group-only projection helpers and build a private exact structural tree from every available map root. The traversal must receive the current raw depth and the registered active-map count. It must apply this order:

1. Ignore an excluded source node and all of its descendants.
2. When the source node is marked, add one atomic exact node, add its `ProjectedNode` to the global ordered node list, and do not descend.
3. When an unmarked node is at an enabled boundary depth, make an exact enclosure, recursively project children under that enclosure, and retain the enclosure only when it has reachable direct or transitive projected content.
4. When an unmarked node is at a transparent depth, recursively project each child into the nearest retained exact ancestor without changing the child raw depth.

Create a map-root enclosure separately so root behavior is explicit. Its tier is selected by the same active-map table; a single-map root remains in the projection with `SUPPRESSED`, and a multi-map root remains with `EMPHATIC`. Do not use unary compression or any helper that changes the source-depth argument.

Flatten the exact tree in deterministic source order into `ProjectedEnclosure` values. Every retained enclosure has its own source-node-derived hull key, source label, map name, parent hull key when present, direct node keys, and direct child hull keys. A transparent node contributes no enclosure or endpoint key of its own. Preserve existing input order across maps and source order within each map.

Change `GraphProjection.projectedNodeCount()` to return `nodes().size()`.

- [ ] **Step 3: Make endpoints, relationship filtering, connector filtering, and pins node-only**

Keep enclosure endpoint keys available for spatial selection and source navigation, but separate that concern from semantic endpoint lookup. The endpoint traversal must map a marked source node and all included descendants below that marker to the marker's `ProjectedEndpointKey.ofNode(...)`. It must not map an unmarked structural ancestor, including the map root, to a semantic endpoint solely because it has a retained enclosure.

Retain existing missing, excluded, and inaccessible outcome handling. A relationship or connector that names an unmarked structural node must not create a projected edge. Its current recoverable unresolved result behavior stays intact rather than adding an outcome type. Duplicate self-loops and normal visible edges retain their existing filtering rules.

Build the pin index from `ProjectedNode` values only. A stored pin on a structural ancestor must remain dormant; a stored pin for a group marker must resolve to that marker's node key and preserve its coordinates. Continue applying only active pins to the projected pin list.

- [ ] **Step 4: Update affected projection regressions to the restored contract**

Update the listed projection tests instead of retaining assertions that made group markers into enclosure endpoints:

- `StructuralProjectionShould` and `GroupOnlyProjectionShould` must distinguish group vertices from structural enclosures and verify no ordinary unmarked leaf becomes a vertex.
- `EndpointResolutionShould` and `EdgeProjectionShould` must use node endpoint keys for group markers, prove structural references produce no edge, and preserve missing, excluded, inaccessible, duplicate, and self-loop cases.
- `EnclosureTierShould` must assert the exact single-map and multi-map raw-depth tables, including unavailable registrations in the mode count.
- `ProjectionDeterminismShould` must compare deterministic node order, enclosure order, parent links, edges, pins, and resolutions across equivalent inputs.
- `ProminenceCalculatorShould` must calculate reach over actual group node vertices and projected edges.
- `ProjectedEndpointVisibilityShould` must retain visible node endpoints and retained enclosure endpoints while suppressed roots stay hidden from normal interaction lists.
- `ProjectionPureReloadShould` must show reload generation changes do not turn a node pin or node endpoint into an enclosure endpoint.

Do not use reflection or package-private layout internals in these tests. Assert the public `GraphProjection` collections, keys, tiers, resolutions, and pin projections.

- [ ] **Step 5: Commit the projection restoration**

Run the focused projection suite:

```bash
cd /data/home/guest/Development/freeplane/.worktrees/graph-workspace
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test -PTestLoggingFull --rerun-tasks --tests 'org.freeplane.plugin.graph.projection.AncestorBoundaryDerivationShould' --tests 'org.freeplane.plugin.graph.projection.StructuralProjectionShould' --tests 'org.freeplane.plugin.graph.projection.GroupOnlyProjectionShould' --tests 'org.freeplane.plugin.graph.projection.EndpointResolutionShould' --tests 'org.freeplane.plugin.graph.projection.EdgeProjectionShould' --tests 'org.freeplane.plugin.graph.projection.EnclosureTierShould' --tests 'org.freeplane.plugin.graph.projection.ProjectionDeterminismShould' --tests 'org.freeplane.plugin.graph.projection.ProminenceCalculatorShould' --tests 'org.freeplane.plugin.graph.projection.ProjectedEndpointVisibilityShould' --tests 'org.freeplane.plugin.graph.projection.ProjectionPureReloadShould'
git diff --check
git diff -- freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/projection/ProjectionEngine.java freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/projection/GraphProjection.java
```

Commit only the task allowlist:

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/projection/ProjectionEngine.java freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/projection/GraphProjection.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/projection/AncestorBoundaryDerivationShould.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/projection/StructuralProjectionShould.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/projection/GroupOnlyProjectionShould.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/projection/EndpointResolutionShould.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/projection/EdgeProjectionShould.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/projection/EnclosureTierShould.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/projection/ProjectionDeterminismShould.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/projection/ProminenceCalculatorShould.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/projection/ProjectedEndpointVisibilityShould.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/projection/ProjectionPureReloadShould.java
git commit -m "Restore graph ancestor boundary projection"
```

## Task 2: Restore Node-Aware GraphStream Layout and Boundary Sizing

**Implementer tier:** Advanced

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/GraphStreamLayoutEngine.java:1-760`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/TypedSpringBox.java:1-420`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/LayoutWorkerShould.java:1-420`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/TypedForcesShould.java:1-380`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundarySeparationShould.java:1-380`

**Interfaces:**

- Retain `GraphStreamLayoutFactory.create(LayoutCalibration): LayoutEngine`, `LayoutEngine.apply(LayoutRequest): LayoutFrame`, and `LayoutFrame` as the test seam. Do not expose a new engine method or access private force internals from tests.
- `LayoutFrame.positions().nodes()` must contain one position for every projected node and `LayoutFrame.positions().anchors()` one position for every projected enclosure. The layout itself carries no edge collection; topology correctness is asserted through the input `GraphProjection.edges()` and resulting node positions.
- Continue honoring `PinProjection` coordinates through the existing layout request path.

- [ ] **Step 1: Add red layout tests through public layout frames**

Extend the current test fixtures with a projection containing a suppressed root, an emphatic Level 1 enclosure, a subtle Level 2 enclosure, and two direct `ProjectedNode` keys on the Level 2 enclosure. Add a node-to-node projected edge and an active pin for one node.

Add falsifiable assertions that a settled frame contains both node positions and all ancestor anchors, returns the pinned node at its pin coordinates within the existing layout tolerance, and produces a node geometry candidate within its enclosing hierarchy rather than treating nodes as boundary anchors. Add a force-behavior regression in `TypedForcesShould` that compares otherwise identical frames with and without a direct node under an enclosure; it must observe containment influence through resulting frame coordinates, not a private `hasForceLink` method.

Run these tests before implementation and confirm the current group-only engine lacks node particles and node pin positions:

```bash
cd /data/home/guest/Development/freeplane/.worktrees/graph-workspace
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test -PTestLoggingFull --rerun-tasks --tests 'org.freeplane.plugin.graph.layout.LayoutWorkerShould' --tests 'org.freeplane.plugin.graph.layout.TypedForcesShould'
```

- [ ] **Step 2: Reintroduce node particles without weakening retained boundary anchors**

Merge the historical node-particle path into the current boundary-anchor layout rather than replacing the current hierarchy and collision behavior. Restore desired-particle entries, GraphStream node identifiers, node endpoint resolution, pin lookup, and output decoding for `ProjectedNode` keys. A desired node particle carries its `ProjectedNodeKey`; a desired anchor carries its `EnclosureHullKey`. `pinFor(...)` returns a pin only for a desired node whose `nodeKey.source()` matches the active `PinProjection.source()`, never for an anchor. Register each node with the normal node radius and prominence scale, use node-to-node spring links for projected edges, and populate `LayoutFrame.positions().nodes()` from the GraphStream particle positions.

For every node listed in an enclosure's `directNodes()`, seed it around that enclosure's anchor in deterministic direct-node order. Use the same direct-node packing radius that boundary sizing uses, so a boundary begins large enough for its direct node discs. For a root-level direct node, seed it from the root anchor. Preserve the existing deterministic seed behavior for unpinned particles; an active node pin overrides its seed and remains fixed.

Keep anchors distinct from graph nodes in `TypedSpringBox`: extend its package-private particle configuration to receive the anchor flag, record that flag by GraphStream particle id, and make `addBoundaryRepulsion(...)` return immediately unless both particles are recorded anchors. Keep `TypedNodeParticle` unchanged so node particles retain ordinary native and typed force behavior. Nodes still participate in normal GraphStream forces, node-edge springs, and their enclosure containment spring, but never in boundary-vs-boundary collision separation.

- [ ] **Step 3: Size structural boundaries for both direct nodes and child boundaries**

Extend the current `BoundarySizes` calculation to account for direct node footprints as well as direct enclosure anchors. Build its private node lookup from the `GraphProjection` node list and use a deterministic ring packing calculation based on direct node count, the maximum rendered node-disc radius, conservative `SafeNodeLabel.displayText()` width and height bounds, and the existing clearance convention. The size for an enclosure must cover the larger of its direct-node packing reach and its packed direct-child-boundary reach, plus title and frame clearance. Do not make labels or raw transparent nodes create extra boundaries.

Use the same helper for node seed placement so a large direct-node set cannot start outside a leaf Level 2 boundary. Continue deriving nested boundary positions from direct enclosure relationships and keep suppressed roots participating as anchors even though they are not painted.

- [ ] **Step 4: Update layout regressions and verify**

Update `BoundarySeparationShould` for the restored node-and-ancestor hierarchy. The tests must continue to prove sibling boundary separation, settled layout convergence, and pin fidelity. Add a direct-node boundary coverage case: after the geometry stage used by the existing fixture, each direct node center lies inside its parent hull with the established tolerance. Do not assert random coordinates or test private GraphStream data structures.

Run the focused layout suite:

```bash
cd /data/home/guest/Development/freeplane/.worktrees/graph-workspace
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test -PTestLoggingFull --rerun-tasks --tests 'org.freeplane.plugin.graph.layout.LayoutWorkerShould' --tests 'org.freeplane.plugin.graph.layout.TypedForcesShould' --tests 'org.freeplane.plugin.graph.layout.BoundarySeparationShould'
git diff --check
git diff -- freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/GraphStreamLayoutEngine.java freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/TypedSpringBox.java
```

- [ ] **Step 5: Commit the layout restoration**

```bash
cd /data/home/guest/Development/freeplane/.worktrees/graph-workspace
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/GraphStreamLayoutEngine.java freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/TypedSpringBox.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/LayoutWorkerShould.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/TypedForcesShould.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundarySeparationShould.java
git commit -m "Restore graph node layout particles"
```

## Task 3: Restore Node Canvas, Accessibility, Search, and Window Interaction

**Implementer tier:** Advanced

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphHitIndex.java:1-240`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPainter.java:1-460`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphInteractionController.java:1-620`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/AccessibleGraphCanvas.java:1-760`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphSearchModel.java:1-180`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java:1-1080`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java:1-500`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphInteractionControllerShould.java:1-720`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/AccessibleGraphCanvasShould.java:1-560`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphSearchModelShould.java:1-300`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java:1-680`

**Interfaces:**

- Keep `GraphCanvas`, `CanvasState`, `GraphGeometry`, `GraphHitIndex.from(CanvasState)`, `GraphHitIndex.endpointAt(LayoutPoint)`, `GraphIntent`, `GraphWorkspaceWindow`, and Swing accessibility behavior compatible with their existing callers.
- Use existing `NodeGeometry`, `HullGeometry`, `ProjectedEndpointKey`, `ProjectedNodeKey`, and `ProjectedEndpointVisibility` types. Do not add a public UI model type.

- [ ] **Step 1: Add red UI-model tests for restored node behavior**

Update the existing fixtures so a graph contains both structural enclosure hulls and a visible marked node. Add test cases that fail on the current group-only UI path:

- `GraphCanvasPaintShould` verifies a node disc, node label, selection treatment, and pin treatment render from `NodeGeometry`, while hull painting remains tier-based and does not use the old group-color path for structural boundaries. It also verifies a suppressed root retains containment geometry but contributes no endpoint hit entry.
- `GraphInteractionControllerShould` verifies a node hit produces a node endpoint, drag/pin requests use the node key, and a connect gesture may only complete when both endpoints are nodes. A boundary may still be selected or opened, but it cannot become a semantic connect endpoint.
- `AccessibleGraphCanvasShould` verifies a visible node reports label, map name, bounds, selection state, pin state, and visible outgoing target count through the existing accessibility endpoint path.
- `GraphSearchModelShould` verifies node labels and map names are searchable alongside visible non-suppressed enclosure labels.
- `GraphWorkspaceWindowModelShould` verifies selecting a node records `selectedNode`, the pin action targets that selected node, and map rows report projected node count rather than enclosure count.

Run the focused tests before implementation and confirm that node hits, painted node content, node accessibility, node search, or selected-node pinning are absent:

```bash
cd /data/home/guest/Development/freeplane/.worktrees/graph-workspace
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test -PTestLoggingFull --rerun-tasks --tests 'org.freeplane.plugin.graph.canvas.GraphCanvasPaintShould' --tests 'org.freeplane.plugin.graph.canvas.GraphInteractionControllerShould' --tests 'org.freeplane.plugin.graph.canvas.AccessibleGraphCanvasShould' --tests 'org.freeplane.plugin.graph.canvas.GraphSearchModelShould' --tests 'org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould'
```

- [ ] **Step 2: Restore node rendering and hit priority**

In `GraphHitIndex`, index node geometry with `ProjectedEndpointKey.ofNode(node.key())` before hull entries so a node is selectable within a containing boundary. Preserve hull hit ordering for spatial boundary selection.

In `GraphPainter`, restore the established node disc, label, prominence, selection, and pin rendering from `GraphGeometry.nodes()`. Keep enclosure painting based on `BoundaryTier` and the canvas theme. Remove the group-marker color treatment that only existed when markers were rendered as hulls; a structural Level 1 or Level 2 boundary must not inherit group color because of a descendant vertex.

- [ ] **Step 3: Restore node-only graph commands and user-facing models**

In `GraphInteractionController`, start a connect gesture only from a node endpoint, derive a pin key only from a node endpoint, and reject a connect completion unless both source and target endpoint keys are nodes. Preserve boundary selection and source-open behavior. In `GraphWorkspaceWindow`, restore the node selection state, node map-row count, and selected-node pin synchronization. Its `GraphIntent.Connect` handling must also return without executing a command unless both endpoints are nodes; only then extract both node sources for `GraphCommands.connect(...)`. Keep all existing event ordering and EDT behavior.

Restore `AccessibleGraphCanvas` node endpoint information using `NodeGeometry`, `NodeProminence`, and active `PinProjection` lookup. Restore node entries in `GraphSearchModel` before enclosure entries. Continue hiding suppressed root hulls from normal accessibility/search interaction lists, while allowing their contained nodes to be visible.

- [ ] **Step 4: Commit the UI restoration**

Run the focused UI regression set:

```bash
cd /data/home/guest/Development/freeplane/.worktrees/graph-workspace
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test -PTestLoggingFull --rerun-tasks --tests 'org.freeplane.plugin.graph.canvas.GraphCanvasPaintShould' --tests 'org.freeplane.plugin.graph.canvas.GraphInteractionControllerShould' --tests 'org.freeplane.plugin.graph.canvas.AccessibleGraphCanvasShould' --tests 'org.freeplane.plugin.graph.canvas.GraphSearchModelShould' --tests 'org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould'
git diff --check
git diff -- freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphHitIndex.java freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPainter.java freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphInteractionController.java freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/AccessibleGraphCanvas.java freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphSearchModel.java freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java
```

Commit only the task allowlist:

```bash
cd /data/home/guest/Development/freeplane/.worktrees/graph-workspace
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphHitIndex.java freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPainter.java freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphInteractionController.java freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/AccessibleGraphCanvas.java freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphSearchModel.java freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphInteractionControllerShould.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/AccessibleGraphCanvasShould.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphSearchModelShould.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java
git commit -m "Restore graph node canvas interactions"
```

## Task 4: Prove Reload, Coordinator, Generated-Workspace, and Full-Module Behavior

**Implementer tier:** Capable

**Files:**

- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceModelAcceptanceShould.java:1-620`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceColdReloadShould.java:1-480`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/GraphUpdateCoordinatorShould.java:1-700`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/GeneratedWorkspace.java:1-920`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/PerformanceTripwiresShould.java:1-420`

**Interfaces:**

- This task changes test fixtures and assertions only. Do not add production compatibility paths or alter persistence.
- Use `GeneratedWorkspace` to construct deterministic structural-depth cases. Do not read `/home/henry/Documents/999.notebooks/01.math/math.fpg` from automated tests.

- [ ] **Step 1: Add red end-to-end ancestor acceptance coverage**

Add an integration acceptance scenario representing the relevant portion of the math notebook: an active map root with Level 1 `ZFC`, sibling Level 2 `Axioms` and `Basic Definitions and Theorems` boundaries, three marked axiom groups under the former, and one marked theorem group under the latter. Assert the projection supplies four group nodes, the suppressed root, the Level 1 hull, both Level 2 hulls, geometry for both node and ancestor hulls, and a node endpoint edge when the fixture provides a relationship between marked groups.

Add a companion multi-map case with a registered unavailable map. Assert the available root is emphatic, Level 1 is subtle, Level 2 is absent, and the graph remains interactable despite the unavailable registration. The test must use the production coordinator/worker lifecycle and wait through its existing deterministic test hooks rather than sleeping.

Run the acceptance and cold-reload tests red before making their expected restored-node assertions:

```bash
cd /data/home/guest/Development/freeplane/.worktrees/graph-workspace
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test -PTestLoggingFull --rerun-tasks --tests 'org.freeplane.plugin.graph.integration.GraphWorkspaceModelAcceptanceShould' --tests 'org.freeplane.plugin.graph.integration.GraphWorkspaceColdReloadShould'
```

- [ ] **Step 2: Update asynchronous and generated-workspace regressions**

Update `GraphUpdateCoordinatorShould` to assert that replacement projections retain marked node keys, node pins, and strict-depth enclosure tiers across reload/coalescing transitions, and that a newer projection without a former Level 2 boundary has neither that hull nor stale node geometry in the accepted current state. Update `GeneratedWorkspace` only as needed to express group markers and exact pass-through depth with its existing construction API; do not add persistence-specific shortcuts. Update `PerformanceTripwiresShould` alongside the fixture when a generated node or boundary count changes under the restored semantics. The unchanged performance diagnostic remains part of the full module verification.

- [ ] **Step 3: Run integration, then the entire module suite**

Run the integration and tripwire tests first:

```bash
cd /data/home/guest/Development/freeplane/.worktrees/graph-workspace
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test -PTestLoggingFull --rerun-tasks --tests 'org.freeplane.plugin.graph.integration.GraphWorkspaceModelAcceptanceShould' --tests 'org.freeplane.plugin.graph.integration.GraphWorkspaceColdReloadShould' --tests 'org.freeplane.plugin.graph.control.GraphUpdateCoordinatorShould' --tests 'org.freeplane.plugin.graph.performance.PerformanceTripwiresShould'
```

Then run the complete module suite:

```bash
cd /data/home/guest/Development/freeplane/.worktrees/graph-workspace
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test -PTestLoggingFull --rerun-tasks
git diff --check
git status --short
git log --oneline -4
```

- [ ] **Step 4: Commit integration coverage**

```bash
cd /data/home/guest/Development/freeplane/.worktrees/graph-workspace
git add freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceModelAcceptanceShould.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceColdReloadShould.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/GraphUpdateCoordinatorShould.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/GeneratedWorkspace.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/PerformanceTripwiresShould.java
git commit -m "Verify graph ancestor boundary workflow"
```

## Final Verification

- [ ] Run `JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test -PTestLoggingFull --rerun-tasks` from the graph-workspace worktree after all four commits.
- [ ] Inspect `git status --short`, `git log --oneline -4`, and `git diff origin/feature/graph-workspace...HEAD --check`.
- [ ] Manually open `/home/henry/Documents/999.notebooks/01.math/math.fpg` through Graph Workspace and confirm `ZFC` is the one-map Level 1 boundary, `Axioms` and `Basic Definitions and Theorems` are sibling Level 2 boundaries, the four marked graph groups are node discs rather than hulls, and structural boundaries never become relationship endpoints.
- [ ] Request independent final review before integration. Address only confirmed findings within this plan's scope.
