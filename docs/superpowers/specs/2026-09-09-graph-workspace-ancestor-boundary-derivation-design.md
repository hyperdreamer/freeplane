# Graph Workspace Structural Boundaries And Group Vertices Design

- Date: 2026-09-09
- Status: Design review pending
- Scope: Restore structural ancestor boundaries in Graph Workspace while retaining only explicitly marked groups as graph vertices.

## Background

The group-only projection introduced by `5337227086` turns every marked node into an enclosure and skips every unmarked ancestor. In a single-map workspace this leaves group-marked nodes directly below the suppressed map root, so their structural context disappears.

`math.fpg` demonstrates the problem in `Axiomatic_Set_Theory.mm`:

```text
Axiomatic Set Theory                 root
`- ZFC                               level 1
   |- Axioms                         level 2
   |  |- Fundation / Regularity      graph group
   |  |- Replacement Scheme          graph group
   |  `- Axiom of Choice             graph group
   `- Basic Definitions and Theorems level 2
      `- Theorem                     graph group
```

The desired single-map view has an emphatic `ZFC` boundary containing subtle `Axioms` and `Basic Definitions and Theorems` boundaries. The marked nodes are graph vertices inside those boundaries.

## Decisions

### Graph Vertices

A non-root node marked with `<graph_group version="1"/>` is one `ProjectedNode`:

- It is rendered, selectable, navigable, pinnable, and participates in GraphStream layout.
- It is the only kind of source node that may be a graph endpoint.
- Its descendants are collapsed into it for endpoint resolution. A connector or relationship to a descendant resolves to the marked ancestor's `ProjectedNode`.
- An unmarked node outside a marked ancestor is not a graph endpoint.

The map root always remains a structural map container. A marker on the root does not turn the map root into a graph vertex.

### Structural Boundaries

`ProjectedEnclosure` represents a structural boundary, not a graph vertex:

- An unmarked Level 1 or Level 2 source node produces an enclosure only when its reachable subtree contains at least one group vertex.
- Boundaries have `parentHull`, `directNodes`, and `directEnclosures`; they do not have graph degree, prominence, pins, or semantic graph edges.
- The map root remains an enclosure for every available active map. An empty map root is retained so multi-map workspaces still show a map frame; it is invisible in single-map mode because its tier is suppressed.

### Source Depth

Depth is the exact parent-child hop count from the map root. No pass-through node is skipped or compressed.

- Root: depth 0.
- Direct root child: depth 1.
- Direct child of a depth-1 node: depth 2.
- All deeper nodes: depth 3 or greater.

Generic unary compression must not merge or remove a visible depth-1 or depth-2 boundary. Deeper unmarked containers are transparent: their reachable group vertices are attached to the nearest visible ancestor boundary.

### Boundary Tiers

The mode is determined by the number of active map registrations in the immutable `ProjectionInput`, including registrations that are loading or unavailable.

| Active maps | Root | Depth 1 | Depth 2 and deeper |
| --- | --- | --- | --- |
| 0 | No projection | No projection | No projection |
| 1 | Suppressed | Emphatic | Depth 2 subtle; depth 3+ omitted |
| 2 or more | Emphatic | Subtle | Omitted |

In single-map mode, a depth-2 enclosure contains all reachable group vertices below that branch. In multi-map mode, reachable group vertices at depth 2 or deeper attach directly to their depth-1 enclosure; no depth-2 hull is emitted.

A group marker on a depth-1 or depth-2 node takes precedence over structural-boundary derivation: that source node is a graph vertex and its subtree is collapsed.

## Projection Model

`ProjectionEngine` becomes the single authoritative projection path. The group-only methods `projectGroups` and `projectGroup` are removed.

The implementation uses private traversal elements equivalent to the pre-group-only model:

- `ExactNode` wraps a marked non-root `ProjectedNode`.
- `ExactEnclosure` wraps a retained root, depth-1, or single-map depth-2 structural node.
- A transparent deeper unmarked container contributes its reachable `ExactNode` values to the nearest retained enclosure.

The published records retain the existing public model:

- `GraphProjection.nodes()` contains exactly group-marked non-root nodes.
- `GraphProjection.enclosures()` contains map roots and retained structural boundaries.
- Each enclosure's `directNodes()` contains directly contained group vertices.
- Each enclosure's `directEnclosures()` contains retained child hulls.
- Every non-root enclosure has exactly one `parentHull`; source hierarchy cannot contain a cycle.

`SourceNodeKey`, `ProjectedNodeKey`, and `EnclosureKey` already carry `MapReferenceId`, so nodes with the same persistent ID in different maps remain distinct. Existing source child order is retained within each direct-child list.

## Endpoints And Pins

Endpoint traversal follows the existing collapsed-group semantics, adapted to node endpoints:

- A marked node resolves to `ProjectedEndpointKey.ofNode(...)`.
- A non-excluded descendant of a marked node resolves to that same node endpoint.
- A reachable unmarked node outside a marked ancestor has no projected endpoint.
- An excluded node and its descendants retain the existing recoverable exclusion outcome.
- Native connectors and cross-map relationships emit only when both source endpoints resolve to visible group vertices. There is no implicit retargeting to a structural boundary.
- A pin is active only when it targets a visible group vertex. Pins on structural ancestors or unprojected descendants remain dormant.

## Layout, Geometry, And Canvas

The implementation restores the node path removed by the group-only change while keeping the current boundary-anchor path:

- `GraphStreamLayoutEngine` restores `ProjectedNode` particles, node endpoint resolution, node positions, active node pins, and containment force links from enclosing anchors to direct node particles.
- Anchor particles remain private layout objects. Their hierarchy and containment links are not `GraphProjection` semantic edges and do not affect graph degree or prominence.
- `BoundarySizes` accounts for both direct node footprints and child boundary footprints. Existing conservative ring packing continues to size and seed boundary anchors; direct node footprints participate in the parent boundary's extent.
- `GraphGeometryEngine` already computes hulls from `directNodes()` and `directEnclosures()`. It will be exercised again with nonempty node geometry.
- `GraphPainter` already paints projected nodes. `GraphHitIndex` and interaction paths restore node hit targets before hull hit targets, preserving direct manipulation of group vertices.
- A suppressed root maintains layout geometry but is not painted or hit tested.

No workspace XML format changes are needed. Boundaries remain derived state and are not persisted.

## Lifecycle

The implementation continues to use the existing immutable `MapSnapshot` and `ProjectionInput` pipeline. It must not read live `NodeModel` state during projection.

The existing `GraphUpdateCoordinator` generation checks are retained: each published `CanvasState` must match the current accepted generation and projection. Changing active registrations, map availability, source structure, exclusion state, graph-group markers, labels, connectors, relationships, or pins causes a new projection generation. A newer state replaces the complete prior canvas state; stale layout frames cannot republish removed hulls or node geometry.

## Verification

Add focused regressions before production changes:

1. Projection fixture matching the `ZFC -> Axioms` and `Basic Definitions and Theorems` hierarchy:
   - one active map produces a suppressed root, emphatic `ZFC`, two subtle child boundaries, and four group vertices;
   - the three axiom vertices belong to `Axioms`; the theorem belongs to `Basic Definitions and Theorems`.
2. Strict-depth fixture proving a single-child intermediate node is not skipped.
3. Pruning fixture proving a Level 1 or Level 2 branch without a reachable group vertex emits no hull.
4. Group-marker precedence fixture proving a marked Level 1 or Level 2 node is a single vertex and collapses its descendants.
5. Multi-map fixture proving roots are emphatic, depth-1 boundaries are subtle, and no depth-2 hulls are emitted.
6. Endpoint and pin fixtures proving descendants resolve to their marked ancestor, while unmarked structural ancestors do not become endpoints or active pins.
7. Layout and geometry fixtures proving direct nodes appear in `LayoutPositions.nodes()`, remain contained by their current hulls, and restore node hit testing.
8. A generation replacement fixture proving a later mode change cannot leave a stale depth-2 hull in the current canvas state.

Verification gates:

```bash
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  gradle :freeplane_plugin_graph:test -PTestLoggingFull --rerun-tasks

git diff --check
```

Manual acceptance opens `/home/henry/Documents/999.notebooks/01.math/math.fpg` and confirms that the single active map renders `ZFC`, `Axioms`, and `Basic Definitions and Theorems` as described above.
