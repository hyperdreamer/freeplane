# Graph Workspace Group-Only Boundaries Design

- Date: 2026-08-27
- Status: Approved for implementation
- Scope: Change the Graph Workspace so only group-marked nodes appear in the graph (each rendered as a boundary), and guarantee that sibling boundaries never overlap.

## Context

The Graph Workspace currently projects **every node of every available map**: non-group containers become hulls, leaves and group-marked nodes become circles, and the map root becomes an outer frame. On a real map (the reported screenshot: 5 topics plus a dense pile of nodes), this produces a crammed, unreadable graph: the layout-calibration remediation (`2026-08-27-graph-workspace-layout-calibration-remediation`, commit `6c029d4ccf`) fixed node spacing, anchor rings, and hull-ballooning, but the graph still shows all the ordinary nodes the user does not want to see, and sibling boundaries can still touch when group content is large.

The user's requirements (confirmed through design dialogue):

1. **The Graph Group marker is the toggle.** A node appears in the graph if and only if it carries the Graph Group marker. If no node is marked, the graph view shows no nodes. Toggling the marker in the map view adds/removes the boundary live through the existing generation pipeline; no new UI.
2. **The graph is purely boundaries.** Each group-marked node renders as a boundary; its subtree is never rendered. Non-group nodes (containers and leaves) are never projected.
3. **Map root frames remain.** Each available map keeps its root container as a frame per the existing boundary-tiering rules (emphatic with 2+ active registrations; root suppressed and first-level boundaries promoted with one registration). A map with zero group markers shows only its empty frame.
4. **Sibling boundaries never overlap (hard guarantee).** No two sibling boundaries may intersect — including two root frames of different maps. Nested containment (a group inside a group, groups inside their root frame) is the normal structure, not an overlap. The guarantee holds even with extreme group counts; the graph simply spreads wider.

## Design

Four areas change: the projection (group-only emission), the layout (guarantee by construction), the canvas/geometry (boundary shapes), and the test suites. Preserved invariants are listed at the end.

### 1. Projection: group-marked nodes only (`ProjectionEngine`)

`projectNode` becomes:

- excluded -> not projected (unchanged)
- **graphGroup -> enclosure**: the node becomes an `ExactEnclosure` (hull); its subtree is **never traversed or rendered**
- anything else -> not projected (no container hulls, no leaf circles, no group-less structure)

Consequences:

- The hull tree is now: map root (frame) -> group-marked boundaries -> nested group-marked boundaries. Existing unary-chain compression (`compress`) still merges nested group chains into one hull key; boundary tiering is unchanged.
- `ProjectedNode` (circle) emission disappears; `directNodes` is always empty. Node particles, circle-node rendering, and node prominence become dead and are removed per the legacy-removal policy. Exact dead-code extent is verified during implementation; anything still referenced stays. (`ProjectedNodeKey` remains where endpoints/records still reference it.)
- **Edges**: a native connector or cross-map relationship renders only when both endpoints are visible boundaries (group-marked nodes). Endpoints resolving to non-group nodes vanish (existing endpoint-traversal machinery already resolves enclosure endpoints; only the visible set shrinks).
- **Pins**: a pin record whose target is a group-marked node pins the boundary's position (active). A pin targeting any other node becomes dormant via the existing dormant-pin machinery (the record persists; it activates if the node is later marked). "Any pinned projected boundary makes its map immovable" replaces the node-based rule for map-tier separation.
- **Live toggle**: marking/unmarking a node changes the map snapshot, which flows through the generation pipeline and updates the graph like any other map edit (no new code path).

### 2. Layout: sibling non-overlap guaranteed by construction (`GraphStreamLayoutEngine`, `TypedSpringBox`)

Inputs shrink to anchors (root frames + group boundaries), edges between them, and pins. No node particles.

**Boundary extents (deterministic, off-EDT):** each boundary gets a conservative extent estimated from its label text:

```
width  = label.length * CHAR_WIDTH_UPPER_BOUND + 2 * PADDING
height = 2 * (CHAR_HEIGHT_UPPER_BOUND + PADDING)
```

`CHAR_WIDTH_UPPER_BOUND`/`CHAR_HEIGHT_UPPER_BOUND` are fixed constants chosen to be upper bounds of the rendered label metrics at the canvas font (the canvas measures with AWT on the EDT; the layout runs off-EDT and must not measure), so the estimate is always >= the painted shape. Extents are computed bottom-up: a frame's extent derives from its descendant rings (see below), so frames can only be as large as their content. Exact constant values (`GAP`, char bounds, padding) are validated during planning with the headless probe methodology used by the calibration cycle.

**Ring packing (the guarantee):** for each anchor, its ordered `directEnclosures()` siblings are placed on a ring centered on the parent anchor, angle `2 * PI * siblingIndex / siblingCount` (existing deterministic order):

- Ring radius: `R = (maxSiblingWidth + GAP) / (2 * sin(PI / N))` where `maxSiblingWidth` is the widest sibling extent on that ring. The adjacent chord `2R * sin(PI / N) = maxSiblingWidth + GAP` bounds every sibling's width, so same-ring siblings mathematically cannot touch.
- Child-ring bound: a child ring radius plus its largest descendant extent must stay within `siblingSpacing / 2 - GAP`, so rings around adjacent siblings cannot interleave. Rings recursively satisfy this at every level.
- Top level: each map's root frame is a sibling on the top ring (multi-map workspaces), same rule.
- Ring radii grow with N, so extreme group counts spread wider instead of overlapping. Seeds remain deterministic per workspace/identity (SHA-256 identity seeding preserved).

**Settle dynamics:** a hull-hull repulsion term acts on boundary anchors when they come within extent-sum + GAP, keeping them apart while the existing hierarchy springs (rest 100 at depth 1, 60 deeper) hold structure. Root frames grow via convex closure of their descendants, which cannot overlap what they contain. Idle thresholds are re-derived for the new particle counts (measured with the existing probe methodology).

**The one documented override:** a **pin forces its boundary's position** and may collide with siblings — the user explicitly overriding the layout (unchanged from today). Everything unpinned is guaranteed separated.

### 3. Canvas & geometry (boundary shapes)

- Each group-marked node paints as a coral (`#DF625D`) marker-style boundary — octagon-like outline sized from its label via `GraphGeometryEngine` metrics, centered on the anchor. No circle nodes anywhere.
- Root frames render as today (tiered emphasis), `HullGeometry` wrapping the descendant boundary shapes.
- `HullGeometry` keeps its convex-closure path for frames; a leaf group boundary's polygon is the label-sized marker shape. `HullIntersection` gains a sibling-overlap predicate used by tests (no canvas runtime cost).
- Interactions keep their semantics with boundary targets: click selects a boundary; double-click navigates to the source node (existing `SourceNavigation` enclosure path); the connect tool draws edges between boundaries; keyboard traversal and arrow-key rules unchanged; selection and pin gestures target boundaries.
- Empty state: a workspace with no group markers shows only the root frames.

### 4. Testing & verification

**Projection suite (new):** non-group nodes never emitted (containers, leaves, hidden subtrees); group-marked nodes become hulls; nested groups stay nested; unary chains compress; root frames always present with tiering unchanged; live toggle on/off through the pipeline; edges only between visible boundaries; pins active on group targets and dormant otherwise.

**Layout suite (extend `TypedForcesShould`):** seed-time non-overlap across sibling rings for a matrix of maps (deep nesting, long labels, 1-200 groups); settle-time non-overlap after the full settle; ring radius scales with N; determinism across runs; pinned-override behavior pinned by a test.

**Canvas/geometry suite:** boundary shape from label metrics; frame wraps descendant boundaries; no node painting; `HullIntersection` sibling predicate unit tests.

**Performance gate:** the 2,000-node / 5,000-edge projection traversal gate stays (the adapter still walks whole maps for availability/identity); layout cost now scales with boundary count.

**Falsifiability mutants (one per mechanism, disposable):**
1. A non-group node sneaks into the projection -> projection test fails
2. Ring radius regresses to the mean (instead of max) sibling width -> seed-time overlap test fails
3. Boundary repulsion removed -> a settle-time overlap case fails
4. Pin binding reverted to node keys -> pin tests fail

## Preserved invariants

- SHA-256 identity encoding and deterministic seed derivation; particle ordering; pin handling; solver quality `0.10`; cross-map aggregate cap `0.005`; the `chooseNodePosition` no-op.
- Availability semantics (INACTIVE/LOADING/AVAILABLE/MISSING/...), boundary tiering by active registration count, hidden/summary exclusion, safe-label conversion, confidentiality rules.
- Map-tier separation (unpinned maps translate uniformly; pinned boundary makes its map immovable; two blocked sides retain pins and report Unpin).
- Workspace persistence: pin records and relationships keep their current records; dormant pins remain stored.
- The engineering target of 2,000 projected nodes / 5,000 edges still bounds projection traversal; warn when exceeded (unchanged).

## Known limits

- Pins may override separation (documented above).
- The conservative extent estimate wastes some space for narrow labels (constant upper bound); acceptable for the guarantee.
- Cross-map aggregate budget refinement remains deferred (per-pair map classification in the Barnes-Hut path would break the perf budget).

## Scope

Implementation touches the graph plugin only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/{projection,layout,geometry,canvas,workspace}` as verified during implementation planning, plus tests in `freeplane_plugin_graph/src/test/...`. The plan will fix the exact three-path-style allowlist per task. No `freeplane_api` surface changes; no new UI; no persistence format change.
