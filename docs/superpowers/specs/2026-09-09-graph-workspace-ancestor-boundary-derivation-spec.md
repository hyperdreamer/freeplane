# Technical Specification: Graph Workspace Structural Boundaries and Group Vertices

- Date: 2026-09-09
- Status: Draft after review amendments
- Approved design: `docs/superpowers/specs/2026-09-09-graph-workspace-ancestor-boundary-derivation-design.md`
- Target worktree: `/data/home/guest/Development/freeplane/.worktrees/graph-workspace`
- Target module: `freeplane_plugin_graph`

## 1. Purpose and Scope

Restore the distinction between graph vertices and structural boundaries in Graph Workspace.

A non-root source node carrying `<graph_group version="1"/>` is a graph vertex. Unmarked source ancestors at raw depths 1 and 2 may become visual enclosure boundaries when they contain at least one reachable graph vertex. The map root remains a map-frame enclosure. No `.fpg` persistence schema, `freeplane_api` type, or OSGi export changes are required.

This change corrects the behavior introduced by commit `5337227086`, which converted marked nodes into enclosures and skipped unmarked ancestors. It replaces that path with one authoritative projection implementation; the obsolete group-only projection helpers are removed rather than retained as a fallback.

## 2. Existing Contracts to Preserve

The following public types and method signatures remain unchanged except for the documented count behavior of `GraphProjection.projectedNodeCount()`:

- `GraphProjection`: `generation()`, `nodes()`, `enclosures()`, `edges()`, `relationshipResolutions()`, `pins()`, `prominence()`, `projectedNodeCount()`, and `projectedEdgeCount()`.
- `ProjectedNode`: `key()`, `source()`, `label()`, `mapName()`, and `graphGroup()`.
- `ProjectedEnclosure`: `hullKey()`, `endpointKeys()`, `labels()`, `mapName()`, `parentHull()`, `directNodes()`, `directEnclosures()`, `mapRoot()`, and `boundaryTier()`.
- `ProjectedEndpointKey`: the existing node/enclosure discriminator.
- `PinProjection`, `LayoutPositions`, `GraphGeometry`, and `BoundaryTier`.

`GraphProjection.projectedNodeCount()` must report `nodes().size()`, because projected graph nodes are again stored in `GraphProjection.nodes()`; it must not count non-root enclosures.

`SourceNodeKey` already qualifies persistent node identity with `MapReferenceId`. `EnclosureKey` and `ProjectedNodeKey` retain that qualification. Existing SHA-256 identity encoding for layout particles is preserved.

## 3. Normative Projection Semantics

### 3.1 Source Depth

For each `MapSnapshot`, raw source depth is the exact number of parent-child edges from `map.root()`:

- depth 0: map root;
- depth 1: direct child of the map root;
- depth 2: direct child of a depth-1 node;
- depth 3 and greater: deeper descendants.

Every source edge increments depth. A single-child or otherwise pass-through node is never skipped when calculating depth. Transparent deeper nodes may be omitted from the published enclosure tree, but they are traversed at their actual depth and cannot change a descendant's depth.

### 3.2 Marker Precedence

For a node outside an excluded subtree:

1. The map root is always treated as the map container. A marker on the root does not turn it into a graph vertex.
2. A marked non-root node becomes exactly one `ProjectedNode` and its subtree is collapsed for projection and endpoint traversal. No descendant of that marked node produces another node or enclosure.
3. An unmarked node can produce a structural enclosure only under the depth and active-map rules below.
4. An unmarked node at depth 3 or greater is transparent. Its reachable marked descendants are returned to the nearest retained ancestor enclosure.

`NodeSnapshot.excluded()` is the effective snapshot-time exclusion value. `MapSnapshotFactory` computes it with inherited exclusion (`ancestorExcluded || NodeVisibility.isHidden(node) || SummaryNode.isHidden(node)`), so an excluded ancestor prunes its complete subtree. Fold state itself does not prune a node unless represented by that snapshot exclusion rule.

### 3.3 Active-Map Mode and Boundary Tiers

The mode count is the number of active `MapReference` registrations in the immutable `WorkspaceDocument` held by `ProjectionInput`. Workspace-domain validation makes registration IDs unique. Loading, missing, and other unavailable states still count as active when their registration is active.

| Active registrations | Map root | Raw depth 1 | Raw depth 2 | Raw depth 3+ |
| --- | --- | --- | --- | --- |
| 0 | no map projection | none | none | none |
| 1 | retained, `SUPPRESSED` | retained, `EMPHATIC` | retained, `SUBTLE` | transparent |
| 2 or more | retained, `EMPHATIC` | retained, `SUBTLE` | transparent | transparent |

Only available map snapshots are projected. Therefore an active registration whose snapshot is loading or unavailable contributes to the mode count but does not produce a map root until a later available snapshot is accepted. An available map with no marked nodes still produces its root enclosure: it is an empty suppressed frame in single-map mode and an empty emphatic frame in multi-map mode. With zero active registrations, the projection is built with the existing `GraphProjection` factory and all published lists/maps are empty.

A retained depth-1 or depth-2 enclosure is emitted only if its effective subtree contains at least one marked node. Empty structural branches are pruned. The root is the sole exception and is retained even when empty.

### 3.4 Boundary Identity and Hierarchy

Each retained structural enclosure uses a singleton `EnclosureKey` for its source node. The map root, each retained depth-1 enclosure, and each retained single-map depth-2 enclosure have distinct hull keys, labels, and layout anchors.

- The root has `parentHull() == Optional.empty()`.
- A retained depth-2 enclosure has the nearest retained depth-1 hull as its parent.
- A retained depth-1 enclosure has the map-root hull as its parent.
- `directNodes()` contains marked vertices directly assigned to that enclosure, in source depth-first order.
- `directEnclosures()` contains retained child enclosures in source sibling order.
- A source node cannot occur in more than one published enclosure endpoint or as both a node and an enclosure endpoint.
- Visible depth-1 and depth-2 boundaries are never merged by unary compression. A one-child chain retains separate hulls and labels. No generic compression may remove a retained Level 1 or Level 2 source node.

For single-map mode, marked descendants beneath a retained depth-2 source node are assigned to that depth-2 enclosure after transparent depth-3+ traversal. For multi-map mode, marked descendants beneath depth-2 and deeper containers are assigned directly to the nearest depth-1 enclosure; no depth-2 enclosure is emitted.

A marked node directly under the root is placed in the root's `directNodes()`. A marked node directly under a depth-1 source node is placed either in that depth-1 enclosure (multi-map mode) or in a retained depth-2 enclosure when one exists (single-map mode). Mixed direct nodes and child enclosures are valid in a root or depth-1 enclosure.

## 4. Semantic Endpoints, Relationships, and Pins

Structural boundaries and graph vertices are distinct in every downstream contract.

### 4.1 Endpoint Resolution

The endpoint traversal uses the immutable `NodeSnapshot` tree and records these outcomes:

- A marked non-root source node resolves to `ProjectedEndpointKey.ofNode(ProjectedNodeKey.of(sourceKey))`.
- Any descendant reached below a marked node resolves to that same marked node endpoint, preserving collapsed-group semantics.
- An unmarked node outside a marked ancestor has no semantic graph endpoint, even when it is the source of a retained boundary.
- Excluded nodes retain the existing recoverable exclusion outcome.
- The map root and layout-only enclosure anchors are never semantic graph endpoints.

Enclosure endpoint keys remain available for boundary labels, navigation, selection, and enclosure hit testing through the existing `ProjectedEndpointKey` union. They are not accepted as semantic edge endpoints.

### 4.2 Connectors and Relationships

Native connectors and workspace-owned cross-map relationships produce a `ProjectedEdge` only when both resolved endpoints are node endpoints and they are not equal. Connections to unmarked structural ancestors, excluded nodes, unavailable maps, or descendants collapsed into no visible group are omitted from the edge list or retain their existing unresolved relationship status. A descendant of a visible marked group resolves to that group, so a connector from the descendant to another visible group remains an edge between the two group vertices.

Semantic edges are the only edges used for graph degree, outgoing reach, prominence, and relationship display. Private layout hierarchy/containment links are not `ProjectedEdge` values.

### 4.3 Pins

A pin is active only when its exact persistent `NodeReference` matches a visible `ProjectedNode`. Pins targeting an unmarked structural ancestor, the map root, an excluded node, or an unprojected descendant remain dormant. Structural boundaries are never converted into pin targets. Existing pin coordinates and persistence remain unchanged.

## 5. Implementation Details

### 5.1 `ProjectionEngine`

Replace the current `projectRoot`/`projectGroups`/`projectGroup` path with one private traversal implementation. It may use private `ExactNode` and `ExactEnclosure` elements or an equivalent private accumulator; no new public fragment type is introduced.

The implementation must:

1. Traverse every snapshot node at its raw depth.
2. Stop traversal below a marked non-root node for projection, while retaining the existing endpoint traversal mapping of descendants to that group node.
3. Retain the root and eligible depth-1/depth-2 structural enclosures according to the active-map mode.
4. Pass marked nodes from transparent deeper containers to the nearest retained enclosure.
5. Build `ProjectedNode` and `ProjectedEnclosure` records with exact `parentHull`, `directNodes`, and `directEnclosures` relationships.
6. Index node endpoints separately from enclosure endpoints.
7. Keep relationship resolution and native connector filtering deterministic and compatible with the endpoint rules above.
8. Remove obsolete group-only helper methods and all code that assumes marked groups are enclosures.

`GraphProjection.projectedNodeCount()` is updated in the same change to return the actual projected node count.

### 5.2 `GraphStreamLayoutEngine` and `TypedSpringBox`

Restore the pre-group-only node-particle path while retaining the current boundary-size and typed-force improvements:

- Create a `DesiredParticle.node(ProjectedNodeKey, radius)` for each projected group node.
- Preserve `encodeNode(key)` and `identifier("node-", sha256(identity))`; do not introduce textual `node:<sourceKey>` IDs.
- Create private anchor particles for each enclosure using the existing `encodeAnchor`/`identifier("anchor-", ...)` scheme.
- Add private `ForceKind.CONTAINMENT` links from each enclosure anchor to its `directNodes()` and private `ForceKind.HIERARCHY` links between parent and child enclosure anchors.
- Add relationship force links only for semantic `ProjectedEdge` values. Private force links must not alter `GraphProjection.edges()`, prominence, or graph degree.
- Publish both node and anchor positions in `LayoutPositions`.
- Remove obsolete particles when the current projection no longer contains them.

`BoundarySizes` is evaluated before force settling. It must not depend on runtime node coordinates. Its direct-node estimate uses fixed conservative node-radius/label bounds and direct-node count; child-enclosure estimates remain recursive. `GraphGeometryEngine` is authoritative for the final hull after settled positions are available. All existing finite-coordinate validation and layout calibration contracts remain in force.

### 5.3 Geometry, Canvas, and Window

- `GraphGeometryEngine` computes node geometry for every `ProjectedNode` and computes each enclosure hull from both `directNodes()` and `directEnclosures()` bottom-up. A one-item or one-child enclosure must still produce the existing finite, non-degenerate hull shape.
- A suppressed root retains layout geometry for containment but is not painted or hit-tested.
- `GraphPainter` restores node discs, node labels, node highlights, and node pin rendering. Structural enclosure hulls use the normal map/theme hull styling; marked group vertices use the existing node styling. Suppressed hulls remain omitted from paint and label placement.
- `GraphHitIndex` checks node entries before hull entries. `GraphInteractionController` pin/unpin gestures operate only on node endpoints; connecting is restricted to semantic node endpoints. Boundary selection/navigation continues through enclosure endpoints where supported by existing behavior.
- `GraphWorkspaceWindow` counts projected nodes from `projection.nodes()` for map/status rows and restores selected-node pin/unpin coordinates from `NodeGeometry`, not an enclosure label anchor.
- `ProjectedEndpointVisibility` must continue to include visible node endpoints and non-suppressed enclosure endpoints while excluding suppressed enclosure endpoints.

### 5.4 Lifecycle and Snapshot Isolation

Projection consumes only `ProjectionInput`, `WorkspaceDocument`, `MapSnapshot`, and `NodeSnapshot`; no live `MapModel` or `NodeModel` reads are introduced. The existing `WorkspaceMapCoordinator` snapshot construction remains the source boundary.

The existing `GraphUpdateCoordinator` generation-aware publication path remains the synchronization boundary. A canvas state is accepted only when its generation and projection match the current accepted batch. A later projection replaces the complete layout/geometry state, so removed depth-2 hulls and node particles cannot remain in the current state. No new persistence or concurrency API is required.

## 6. Compatibility and Migration

- No `.fpg` XML element, attribute, version, or migration changes.
- Existing `<graph_group version="1"/>` markers retain their current additive map persistence behavior.
- No `freeplane_api` source, bytecode, or export changes.
- Derived structural boundaries are recreated from each current map snapshot and are never serialized.
- Existing workspaces remain readable. Their graph view gains structural ancestor context; their source maps and stored pins/relationships are not rewritten by projection.

## 7. Exact Implementation Allowlist

All implementation and test edits remain in this worktree and are limited to these paths.

### Production

- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/projection/ProjectionEngine.java`
- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/projection/GraphProjection.java`
- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/GraphStreamLayoutEngine.java`
- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphHitIndex.java`
- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPainter.java`
- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphInteractionController.java`
- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java`

`ProjectedEndpointVisibility.java` is an explicit verification target; it may be edited only if the restored node/enclosure projection requires a behavior correction.

### Tests

- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/projection/AncestorBoundaryDerivationShould.java` (new)
- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/projection/StructuralProjectionShould.java`
- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/projection/GroupOnlyProjectionShould.java`
- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/projection/EndpointResolutionShould.java`
- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/projection/EnclosureTierShould.java`
- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/projection/ProjectionDeterminismShould.java`
- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/projection/ProminenceCalculatorShould.java`
- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/LayoutWorkerShould.java`
- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/TypedForcesShould.java`
- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundarySeparationShould.java`
- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java`
- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphInteractionControllerShould.java`
- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceModelAcceptanceShould.java`
- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java`
- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/projection/ProjectionPureReloadShould.java`

## 8. Falsifiable Test Requirements

The new/updated tests must assert behavior through existing public seams rather than inspect private implementation details:

1. For the `Axiomatic_Set_Theory.mm` shape, one active map publishes four `ProjectedNode`s and four enclosures: suppressed root, emphatic `ZFC`, subtle `Axioms`, and subtle `Basic Definitions and Theorems`. The three axiom nodes are in `Axioms.directNodes()` and the theorem is in the other Level 2 boundary.
2. A strict-depth chain `root -> level1 -> level2 -> level3 -> group` emits Level 1 and Level 2 boundaries, does not skip either source node, and makes the level-3 container transparent.
3. A branch with no reachable group marker emits no structural boundary, while an available empty map still emits its root frame.
4. Marked root children and marked Level 1/Level 2 nodes remain `ProjectedNode`s and collapse their descendants.
5. With two active registrations, roots are emphatic, Level 1 boundaries are subtle, and no Level 2 boundary is emitted.
6. Projection output is deterministic under equivalent snapshot/map insertion order; direct-node and child-enclosure order follows source order.
7. Connectors/relationships to structural boundaries are not semantic edges; descendants of marked groups resolve to the marked node; pins activate only for exact marked nodes.
8. `projectedNodeCount()` equals `projection.nodes().size()` and status/map rows do not count structural enclosures as graph nodes.
9. Layout frames cover every projected node and enclosure, retain finite positions, and restore containment links without adding semantic edges.
10. Geometry and canvas tests prove node discs/labels/hit entries are present, node hits win over enclosing hull hits, suppressed roots are not interactive, and stale removed hulls are absent after a newer generation replaces the state.

## 9. Verification Gates

Use the repository's required Java 21 toolchain in the requested worktree:

```bash
cd /data/home/guest/Development/freeplane/.worktrees/graph-workspace
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  gradle :freeplane_plugin_graph:test -PTestLoggingFull --rerun-tasks
git diff --check
```

The implementation plan must run focused projection/layout/canvas tests before the full module suite and must report exact test counts and failures. Manual acceptance opens `/home/henry/Documents/999.notebooks/01.math/math.fpg` and verifies the `ZFC` outer boundary, the two Level 2 boundaries, and the four marked graph vertices.
