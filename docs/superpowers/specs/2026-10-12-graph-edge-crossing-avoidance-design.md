# Graph Workspace edge-crossing avoidance — Design

- Date: 2026-10-12 (revision 6)
- Topic: `graph-edge-crossing-avoidance`
- PM run: `pm-run-20261012-021203-0d02a300`
- Delivery target: `refs/heads/plugin/graph-workspace` at `/data/home/guest/Development/freeplane`
- Integration branch: `pm/graph-edge-crossing-avoidance/run-pm-run-20261012-021203-0d02a300`
- User request (verbatim): *"New Feature: Edges should avoid crossing each other."*, with a screenshot of the
  Graph Workspace showing a cluster of discs in which the two straight edges cross inside their arrowheads.
- Review status: **revision 6, design review closed**. Attempt 1: 3 blockers, 10 majors, 8 minors.
  Attempt 2: 0 blockers, 10 majors, 8 minors. Attempt 3: 0 blockers, 9 majors, 8 minors. Attempt 4:
  0 blockers, 2 majors, 16 minors. Attempt 5: **0 blockers, 0 majors, 8 minors; specification-ready: yes**.
  §12 maps every finding; the eight remaining minors are specification-pinning items recorded there.
- Mockup evidence: `docs/superpowers/specs/mockups/2026-10-12-graph-edge-crossing-avoidance/`
  - `EdgeRoutingMockups.java` — generator, mirrors `GraphPainter` / `GraphTheme` / `NodeGeometry` rules
  - `edge-crossing-mockups.png` — panels A1–A4, B1–B2, C1–C2
  - `README.md` — fixture provenance and mockup limits

## 1. Problem

### 1.1 The reported defect

In the reported screenshot two straight projected edges cross at a single point, and the arrowhead of one
edge sits on that point, so the junction renders as a dark star. Measured from the screenshot (521×447 px,
disc-stroke blob centroids, disc radius 18.5 px):

| node | center |
| --- | --- |
| top | (272, 170) |
| left | (240, 208) |
| right | (307, 202) |
| bottom | (275, 240) |

The crossing pair is top↔bottom against left↔right: the two **diagonals of four discs in convex
position**. Direct edge lengths are 33.6 px and 30.8 px; the crossing lies 5 px above the top→bottom
arrowhead base and 3 px left of the left→right arrowhead base.

### 1.2 Mechanism

1. `GraphPainter.paintEdges` (`GraphPainter.java:122-155`) draws each `ProjectedEdge` as
   `Line2D(first, second)` where the endpoints are `GraphGeometry.edgeAttachment(...)` — the rim point on
   the centre→centre ray (`NodeGeometry.boundaryToward`). The painter does not detect intersections.
2. The only line-crossing rule today is leader-vs-leader, asserted by the test helper `leaderCrossings`
   (`ScreenLabelPlacementShould.java:1195`).
3. The layout is a force simulation (`GraphStreamLayoutEngine`) followed by post-layout corrections that
   publish verified geometry. `NodeSeparationProjection` is invoked from inside
   `BoundarySeparationCorrection.separate` (`BoundarySeparationCorrection.java:110-113`), which
   `LayoutWorker.accept` applies to every engine frame (`LayoutWorker.java:286-291`).
4. A crossing of two edges whose endpoints lie in convex position cannot be removed by any bounded edge
   detour: the chords are the diagonals of the endpoint quadrilateral, so one path would have to leave the
   quadrilateral and wrap an outer disc. Measured for the reported fixture: the wrap is ≈114 px, 3.4× the
   direct length, and threads the 12.7 px gap between adjacent discs (mockup panel A3).

### 1.3 Why the geometry problem is not only a routing problem

For four endpoints in convex position, two paths joining opposite pairs must cross unless one of them
leaves the convex hull of the four discs; the same holds for a non-planar graph anywhere in the canvas.
Any solution therefore has two parts: a mechanism that changes *where the endpoints are*, and a bounded
fallback that keeps the renderer honest when no legal move exists.

## 2. Goals

- **G1** No crossing survives between two edges when the bounded repair search of §5 finds a relocation
  that passes the acceptance test. G1 is deliberately exact: it claims the enumerated candidate set, not
  the existence of a mathematically possible relocation.
- **G2** An edge never passes over a disc it is not attached to when a relocation or a bounded detour from
  the §6 ladder clears it.
- **G3** Every accepted relocation preserves the pinned invariants: pinned nodes stay fixed, disc gaps stay
  at or above `NodeSeparationProjection.MIN_GAP` (`NodeSeparationProjection.java:20`), the
  `BoundarySeparationCorrection.detect` predicate set stays satisfied, and each node keeps its enclosure
  membership.
- **G4** The published positions, paths and diagnostics are a pure function of
  `(projection, boundary-corrected positions, metrics, active pins, visibleEndpoints, geometry)`; no
  cross-frame state participates.
- **G5** Straight edges stay the default. An edge is routed only when a bounded detour from §6.3 provably
  clears every obstacle of §6.2.
- **G6** Residual conflicts are counted and published, never hidden: relocation residuals in
  `EdgeConflictDiagnostics`, drawn-path crossings in `EdgeRouteDiagnostics` (§6.10), fallback paths in
  `edgesFallback`.
- **G7** The existing interaction contract is unchanged otherwise: selection, hover, dimming, search, pins,
  viewport, hit-testing, undo, persistence, and the 2,000-node/5,000-edge commitment.

## 3. Non-goals

- Enclosure-endpoint relocation. A crossing that involves an enclosure boundary cannot be fixed by moving
  an enclosure endpoint; it is left to the bounded detour or falls back to straight (§5.1).
- Enclosure outlines as router obstacles. A routed edge may cross an unrelated enclosure boundary, exactly
  as straight edges do today; the number of such penetrations is reported (§6.9) but not prevented. See
  §8 for why.
- Changing any edge's stroke, colour, dash pattern, arrowhead size rule, or multiplicity cue beyond the
  path-aware clarifications of §6.6-§6.7.
- Persisting relocated positions or routed paths in the `.fpg` file. Both are derived geometry.
- Global crossing minimisation (NP-hard); this design is a bounded, strictly monotone local repair with a
  documented residual.
- Screen-space label placement, label rectangles, and leader lines. An edge may still pass under a name,
  exactly as the label/leader-clearance design declared out of scope.
- Force-level crossing pressure. The force simulation is not modified.
- Animating a relocation. A swap is applied to the published frame; the existing idle policy and
  displacement deltas govern the frame sequence (§8).

## 4. Decision

### 4.1 Mechanism: relocation first, bounded detour second

The user chose, in order: routing over moving nodes (Q1 B), obstacles = other edges + unrelated discs
(Q2 B), route vocabulary = single bow then two-bend dogleg (Q3 B); then, after the mockups showed the
around-disc wrap (A3) and the hop (A4), rejected both as ugly and asked to **relocate nodes as well**;
and finally chose **(a)** for the residual case: bounded bow/dogleg where it clears, else straight.

| treatment | panel | decision |
| --- | --- | --- |
| straight edges, crossing unchanged | A1 | today's behaviour, the defect |
| bounded bow / dogleg | A2, B2 | **accepted** (secondary mechanism) |
| around-disc wrap | A3 | **rejected** (3.4× detour, threads 13 px gaps) |
| hop / bridge at the crossing | A4 | **rejected** |
| node relocation by swap | C2 | **accepted** (primary mechanism) |

Mockup limits, stated so the evidence is not over-read: panel A2 shows the largest bow inside the 0.35×chord
mockup cap, not the full §6.3 ladder; panel B2 shows a dogleg inside both the bend and route-length caps.
The mockup is an illustration of the treatments, not a proof of the algorithm. Panel C2's caption states
that disc gaps stay verified against `MIN_GAP` after a swap, not that they are unchanged.

### 4.2 Relocation: a new verified correction stage

`layout/EdgeConflictCorrection` is applied by `LayoutWorker.accept` immediately after
`BoundarySeparationCorrection`, so it starts from the boundary stage's output and its residual
diagnostics — a frame whose disc and hull residual state is already known — and its output is the frame
that is published.

```
raw engine frame
  -> BoundarySeparationCorrection                   (unchanged; result also carries its terminal geometry)
  -> EdgeConflictCorrection                         (new, §5)
  -> merged displacement map                        (boundary + edge stages)
  -> idle observation + displacement deltas         (on the final positions)
  -> published LayoutFrame + boundary/edge diagnostics
```

`BoundarySeparationResult` gains a `geometry()` accessor for the terminal `GraphGeometry` it already
computes (`BoundarySeparationCorrection.java:106-155`), so the edge stage never repeats a full hull
computation for its first detection: it starts from that geometry and updates only the affected hulls with
`GraphGeometryEngine.recomputeHulls(..., changedHulls(...))` per candidate batch.

`EdgeConflictResult` carries the published positions, `EdgeConflictDiagnostics`, `EdgeConflictTimings` and
an `appliedDisplacements` map in the same canonical format as
`BoundarySeparationResult.appliedDisplacements()`; `LayoutWorker.accept` merges both maps before
`idlePolicy.observe` and before `previousAppliedDisplacements` is updated
(`LayoutWorker.java:294-303`), so a relocation is motion and appears in the delta accounting.

The correction never re-enters the force simulation; it is a pure function of the verified positions.
A published frame therefore satisfies the disc and hull invariants of the boundary stage plus the
edge-conflict guarantees of this stage, and the boundary diagnostics reported on the published frame are
measured on the published positions (§5.5).

### 4.3 Routing: a publish-time pass, not part of the geometry cache

`geometry/EdgeRouter` runs in `LayoutSettleLoop` at publish, after
`geometryEngine.computeHulls(run.projection, frame.positions(), metrics)` (`LayoutSettleLoop.java:547`)
and before `CanvasState.of(...)`. It is a pure pass over the final positions and hull geometry, and its
immutable `EdgePaths` result is a new `CanvasState` field consumed by `GraphPainter` and the edge hit-test.

The alternative — routing inside `GraphGeometryEngine.computeHulls`/`recomputeHulls` — was rejected: it
would break the pinned recompute-equality contract (`GraphGeometryEngine.java:118-135`,
`GraphGeometryEngineShould.recomputeHullsReusesUnaffectedHullsAndMatchesTheFullComputation`), it would
route intermediate frames that the boundary correction discards (`BoundarySeparationCorrection.java:110-122`),
and it would multiply per-frame work.

Per the repository's legacy-removal policy there is exactly one active execution path: the painter draws
`EdgePaths` for every edge (a straight edge is a two-point path). Straight-line attachment logic stays only
as the internal builder of the default path.

## 5. Relocation contract

### 5.1 Conflict detection

Conflict detection uses the **straight drawn chords** — the rim-to-rim segments the painter computes
before routing (today's painter geometry; after §4.3 the painter draws published paths, while the
relocation stage continues to analyse the straight chords). A **conflict** is one of:

1. **C1 edge–edge crossing:** two projected edges, **both with both endpoints visible**
   (`ProjectedEndpointVisibility.visibleEndpoints`), whose open drawn chords properly intersect. Two edges
   sharing an endpoint are never a C1 conflict, because two straight segments with a common node endpoint
   intersect only at that endpoint. For a shared *hull* endpoint the two chords attach at different hull
   boundary points; by the hull's convexity both attachments lie on the boundary of the same convex region
   and the chords cannot cross away from the hull unless one chord passes through the hull, which the
   detection predicate records as a C2 conflict instead. Touch-only intersections (zero-length or collinear
   contact) are not conflicts; the specification pins the exact predicate and its epsilon.
2. **C2 edge-over-node:** an edge **with both endpoints visible** whose open drawn chord passes within
   `EDGE_NODE_CLEARANCE` of an unrelated projected node's disc. Visibility is part of the predicate so the
   stage can never relocate a node for an edge the painter does not draw. The candidate for a C2 conflict
   moves the **passed-over node** and exists whenever that node is projected and unpinned, independently
   of whether the conflicting edge has an enclosure endpoint at its other end. If the passed-over node is
   itself a descendant of one of the edge's enclosure endpoints, moving it changes that hull and therefore
   the chord; the candidate is still evaluated through the whole-batch verification (§5.3), which measures
   the post-move geometry, and the round loop re-detects, so the fixed point needs no special case.

Ordering is by a new public canonical helper `projection/CanonicalKeys`: `CanonicalKeys.endpoint(...)`,
`.nodePair(...)`, `.edgePair(...)` and `.hull(...)` carry **all** key encoding, including the hull-key
encoding that is package-private today (`layout/CanonicalLayoutKeys`). `layout` and `geometry` callers
delegate to the public helper; no encoding is duplicated (repository policy: one active encoding path). No
iteration over `HashMap` order participates in any decision.

### 5.2 Repair candidates and their deterministic order

Repairs move **node centres**; attachments are re-derived afterwards (§6.5). Candidate scopes are exact:

- an **all-node C1** conflict (all four endpoints projected nodes) gets the swaps and mirrors below;
- a **mixed C1** conflict that involves an enclosure endpoint gets **no** candidates: candidates are
  generated only for conflicts where every moved endpoint is a node, and any candidate that would move an
  enclosure endpoint is discarded before evaluation;
- a **C2** conflict gets the passed-over node's translation (§5.1), moved only if that node is unpinned.

Enclosure **anchors** (`LayoutPositions.anchors()`) are never modified by this stage. Moving a descendant
changes the recomputed hull geometry only. Unlike the boundary stage, which averages moved subtree node
deltas into enclosure anchors (`BoundarySeparationCorrection.java:621-670`), this stage deliberately leaves
every anchor at its pre-batch value; A3 re-checks hull containment with those unchanged anchors. Mixed C1
conflicts without a movable node endpoint are left to §6 routing and counted as residuals.

For an all-node C1 conflict, the four endpoints are in convex position (a proper crossing implies it).
Order the four endpoint keys `K0 < K1 < K2 < K3` lexicographically; rotate the cyclic order so it starts at
`K0`, giving positions `P0..P3`. Candidates, in this fixed order:

1. **Adjacent swap `P0↔P1`** — exchange the two nodes' centre positions. Both edges become hull edges of
   the quad, so the crossing disappears.
2. **Adjacent swap `P2↔P3`** — the other adjacent pair.
3. **Mirror `M(P0)`, `M(P1)`, `M(P2)`, `M(P3)`** — four separate single-node candidates in ascending
   index order. `M(Pi)` reflects `Pi`'s centre across the supporting line of `Pi`'s **opposite chord**
   (the crossing chord not incident to `Pi`) and pushes it `CROSSING_CLEARANCE` further along the unit
   normal of that line that points **from the chord toward the reflected point** (the direction of
   increasing distance from the chord's line). `CROSSING_CLEARANCE` must exceed the largest drawn radius
   (§13), so the mirrored disc leaves the chord's line entirely and the repair is not cosmetic.

A C2 conflict's candidate is the translation of the offending node along the lexicographically smaller
unit normal of the chord (the unit vector `(-dy, dx)/len` or its exact negation, whichever `(nx, ny)` sorts
first) until the chord first clears the disc by `EDGE_NODE_CLEARANCE`.

Candidates that would move a pinned node, a node whose enclosure membership differs from its swap partner,
or any enclosure endpoint are discarded before evaluation. Enclosure endpoints never move (§3).

### 5.3 Legality, retries and the monotone acceptance rule

Candidates are planned in the order of §5.2 and greedily combined into a batch whose moved-node sets are
pairwise disjoint. The batch is applied to a copy of the positions and verified as a whole:

- **A1 monotonicity, pair-set form:** let `C_in` and `C_out` be the conflict pair-key sets before and after
  the batch. Accept only if `C_out ⊆ C_in` (no new pair of either kind) **and** `|C_out| < |C_in|`.
- **A2 disc separation:** no disc pair falls below `NodeSeparationProjection.MIN_GAP` that was not already
  below it, using the same predicate as `NodeSeparationProjection`.
- **A3 hull invariants, subset form:** let `V_in` be the hull-violation **pair-key set** of the current
  (pre-batch) positions and `V_out` that of the candidate positions, both produced by the shared
  `BoundaryInvariants.detect` predicate set — sibling overlap, sibling containment in both directions,
  ancestor-hull escape, with the same suppressed/ambiguous/related exclusions — evaluated on the hull map
  produced by `GraphGeometryEngine.recomputeHulls(..., changedHulls(movedNodes))` from the boundary stage's
  terminal geometry (§4.2). Accept only if `V_out ⊆ V_in` **compared by pair keys**, so pre-existing
  residuals that the boundary stage legally publishes (`boundaryCovered()`) never veto a repair, and no new
  sibling/containment/escape violation can pass. The per-round baseline `V_in` is re-derived from the
  current positions, not frozen from the boundary stage. The predicate set is shared through
  `layout/BoundaryInvariants`, which also owns the terminal classification entry point (§5.5).
- **A4 membership:** every moved node keeps its enclosure path.

**Rejection-retry semantics (pinned).** When a batch is rejected, the stage re-evaluates the rejected
batch's candidates **individually** in canonical order and accepts the first single candidate that passes
A1-A4. Only if none passes does it discard the whole batch and continue the greedy selection over the
remaining unused candidates. Every verification attempt (batch or single) is counted; the total is bounded
by `MAX_VERIFICATIONS_PER_FRAME`, and when that bound is hit the stage stops, reports its residual and sets
`verificationBudgetExhausted`. A viable single repair can never be discarded because a sibling in its batch failed.

Accepted batches repeat until no batch is accepted, `MAX_RELOCATION_ROUNDS` is reached, or no conflicts
remain. Because A1 is a strict set-decrease, the conflict set shrinks with every accepted round and the
loop terminates.

### 5.4 Determinism

The stage is a pure function of
`(projection, positions, metrics, pins, visibleEndpoints, boundaryTerminalGeometry)`. There is no
cross-frame state and no hysteresis: the draft's "previous repair set" was removed because it contradicted
G4. Consecutive frames can differ only because their inputs differ. Timings are not part of the diagnostics
(they live in `EdgeConflictTimings`, mirroring `BoundarySeparationTimings`).

### 5.5 Verification of the published positions

When at least one batch is accepted, the stage **rebuilds the boundary residual state for the published
positions** by replacing only the residual-dependent fields of the boundary record. The rebuilt record
copies every stage-level field from the boundary stage's record — `rounds`, `hullViolationsDetected`,
`appliedDisplacements` (the boundary stage's own map; the edge stage's displacements travel separately in
`EdgeConflictResult.appliedDisplacements`, §4.2) and the displacement/delta statistics — and recomputes
`conflicts`, `residualHullPairs` and `hullResidualViolations` from:
`BoundaryInvariants.detect` on the final hulls, then the same terminal classification the boundary stage
runs (`BoundarySeparationCorrection.java:157-163`: a terminal plan that produces the `BoundaryConflict`
records with their reasons; the plan never moves anything), and finally
`NodeSeparationProjection.project` on the published positions for the node residual, **with node keys
sorted canonically first** so the diagnostic is independent of map iteration order. To close the drift
claim, both `detect` and the terminal classification live in `BoundaryInvariants`
(`BoundaryInvariants.describe(projection, positions, metrics, pins, geometry, baselineResidualKeys)`),
and `BoundaryInvariantsShould` characterizes detection **and** classification against
`BoundarySeparationCorrection`'s pre-extraction output.

The rebuilt record is the published frame's boundary record. The publish gate itself is unchanged and is
evaluated at the boundary stage (`BoundarySeparationCorrection.java:169-171`, `LayoutWorker.java:288-290`)
before the edge stage runs; A3 independently guarantees that no new hull violation can appear afterwards,
so no post-relocation re-gating is required. When no batch is accepted, positions are unchanged and the
boundary diagnostics already describe the published frame.

The rebuild's cost — one `detect`, one terminal plan and one `NodeSeparationProjection.project` per frame
that changed positions — is budgeted in the `edge-conflict` stage tier (§9), not hidden.

### 5.6 Diagnostics and frame plumbing

A new immutable `EdgeConflictDiagnostics` (no timings) is attached to the published `LayoutFrame`:

- `conflictsDetected`, `crossingsDetected`, `edgeOverNodeDetected`;
- `crossingsResolved`, `edgeOverNodeResolved`;
- `conflictsResidual` with their canonical pair keys;
- `repairsApplied` (kind, node keys, from/to centres, batch index);
- `rounds`, `verifications`, `verificationBudgetExhausted`, `indexBudgetExhausted` (§9's distinct
  index-work flag and the counted work).

`LayoutFrame` gains
`withDiagnostics(frame, int residualViolations, BoundarySeparationDiagnostics, EdgeConflictDiagnostics,
IdleMeasurement)` plus an `edgeConflictDiagnostics()` accessor. The edge stage is unconditional (§4.2,
§11), so the old three-argument overload is **removed** rather than retained as a parallel path;
`LayoutWorker.java:297-299` is updated in the same change. `LayoutWorker`'s failure frame
(`LayoutWorker.java:375`) and `LayoutSettleLoop`'s failure publish (`LayoutSettleLoop.java:761`) retain the
last valid edge diagnostics exactly as they retain boundary diagnostics today.

### 5.7 Failure behaviour

A runtime failure inside the correction is wrapped in `EdgeConflictException` and surfaces through the
existing `LayoutWorker` failure path exactly like `BoundarySeparationException`; the canvas publishes the
previous frame. An unsatisfiable conflict is a residual, never an error.

## 6. Routing contract

### 6.1 Placement and inputs

`geometry/EdgeRouter.route(projection, positions, geometry, visibleEndpoints)` returns an immutable
`EdgePaths` (one `EdgePath` per edge with both endpoints visible) plus `EdgeRouteDiagnostics`. It runs on
the settle thread at publish, never in paint (the `edt-swap` stage is pinned at 2 ms,
`PerformanceMeasurements.STRICT_EDT_SWAP_NANOS`), and its result is cached per published frame. **Every**
`CanvasState` construction in `LayoutSettleLoop` — the normal publish (`LayoutSettleLoop.java:551`) and
the failure publish (`LayoutSettleLoop.java:725-727`) — routes the frame's own positions through the
router, so all `CanvasState.of(...)` call sites move to a single factory taking `EdgePaths`; test fixtures
that construct `CanvasState` directly are updated in the same change.

### 6.2 Processing order and the complete obstacle model

Edges are processed in canonical `CanonicalKeys.edgePair` order. For edge `e`, the obstacle set is
**every other projected edge whose two endpoints are visible**:

- the **published path** of every already-processed edge — an accepted candidate or a straight fallback —
  and
- the **straight chord** of every not-yet-processed edge (its current geometry, since it has not been
  routed yet),
- plus every disc except `e`'s own endpoint discs.

Canonical order decides only *which* edge pays a detour; it never decides what counts as an obstacle. A
straight fallback is itself an obstacle for later edges, so the later edge can route around it.

### 6.3 Candidate ladder

For each edge, in order:

1. **Straight chord** — the default, accepted whenever it clears §6.4.
2. **Single bow** — quadratic Bézier with the control point on the chord normal at the midpoint, at
   offsets `+0.15, -0.15, +0.30, -0.30, +0.50, -0.50` × chord length (ascending magnitude, `+` before `-`),
   skipping offsets whose absolute offset exceeds `MAX_BEND` world units.
3. **Two-bend dogleg** — the same offsets and sign order, with **both bends on the same signed side**:
   `bend1` at `(1/3, +s·a)` and `bend2` at `(2/3, +s·a)` in chord-normal coordinates, where `a` is the
   offset magnitude and `s` the candidate sign. (An alternating-bend reading would give path-length ratio
   ≈2.256 and is not part of the ladder.)

Acceptance additionally requires `pathLength ≤ ROUTE_LENGTH_CAP × chordLength`, where `chordLength` is the
anchor-to-anchor distance (node centres, or hull anchors) and `pathLength` is the sampled published path
length. A rung is **live** iff its analytic ratio is `≤` the pinned cap (table in §13); acceptance is
measured per candidate on the sampled length, so a live rung can still fail on a specific geometry. If
nothing clears, the edge falls back to the straight chord.

### 6.4 Clearance rules

- **Discs:** every path point and every path segment keeps `EDGE_NODE_CLEARANCE` from every disc it is not
  attached to.
- **Other paths:** `EDGE_CLEARANCE` from every other path, with the **fan exemption**. For two paths that
  share an endpoint `X`, each path has its own attachment point at `X` (they differ, because
  `edgeAttachment` depends on the other endpoint, `GraphGeometry.java:46-58`). The exemption region is the
  **union of two discs**, each centred on one path's own attachment at `X` with radius
  `endpointClearanceRadius(X) + EDGE_CLEARANCE`, where `endpointClearanceRadius` is the node's drawn radius
  for a node endpoint, or the pinned `FAN_HULL_RADIUS` for an enclosure endpoint. Inside that region the
  two paths may approach each other freely: the **distance** requirement is waived there. A **proper
  crossing of two published paths is never accepted anywhere**, inside or outside the region — the two fan
  paths leave the shared disc in different directions and crossing next to their shared node is exactly the
  visible defect this feature removes. Outside the region the `EDGE_CLEARANCE` distance requirement applies
  as well. This is deliberately stricter than §5.1's chord-level C1 exemption: straight chords sharing a
  node endpoint cannot cross away from the node, but two bowed paths can, and the router forbids it.
- **Hulls:** not obstacles (§3); `EdgeRouteDiagnostics` reports `pathHullPenetrations`, counted per path
  against hulls that contain neither endpoint.

### 6.5 Path model and construction order

`EdgePath` is immutable with value equality (no `Comparable`) and one of three shapes:

- **straight:** the two attachments;
- **bow:** the quadratic control point plus the attachments; the path is stored as the control point, and
  sampled **once per published frame** at the pinned maximum sagitta tolerance. The **same sample list** is
  used for clearance checking, hit-testing and painting, so the geometry that is verified is the geometry
  that is drawn, and the tolerance is bounded against the clearances (§13).
- **dogleg:** four points `a, bend1, bend2, b` (the two bends at `(1/3, 2/3)` of the chord on the same
  signed side); the rounded corner is a stroke property (`BasicStroke` join), not path geometry.

Construction order removes any circularity:

1. the candidate shape is computed between the endpoint **anchors** (node centres, or the hull anchors for
   enclosure endpoints) using the same coordinates the layout publishes;
2. the departure attachment is the rim (or hull boundary) point **facing** the first waypoint, the arrival
   attachment the rim point facing the last waypoint;
3. the published path starts and ends at those attachments.

`GraphGeometry.edgeAttachment(endpoint, toward)` keeps its current signature and direction-based semantics
for its other callers (connection preview, tests); the path builder calls it for the terminal waypoints.
The painter then has no straight-line edge branch at all.

### 6.6 Arrowheads

The arrival head is oriented along the last path segment. Its size keeps the existing rule and floor
(`GraphPainter.java:164-205`, `GraphCanvasPaintShould.java:1020-1039`) with the cap taken from the
**terminal segment length**:

- dogleg: the length of the final leg `bend2 → arrival attachment`;
- bow: the length of the segment `control → arrival attachment` (the control point gives the curve's end
  tangent, so the head lies along the curve without overshooting the bend);
- straight: the attachment-to-attachment distance, i.e. today's value.

When both ends carry heads the terminal length is halved, as today. The specification pins the exact
formula and updates the pinned arrowhead tests.

### 6.7 Multiplicity cue

Drawn at the **arc-length midpoint** of the path (for the straight path this is the current position).

### 6.8 Hit-testing

The edge hit-test measures the minimum distance to the sampled path (segment distance for
straight/dogleg, the §6.5 sample list for a bow). The edge hit-test assertions live in
`GraphInteractionControllerShould` (the main `edgeAt` coverage, lines 66-152 and 345-355) and
`GraphCanvasPaintShould` (lines 228, 248); both are extended.

### 6.9 Diagnostics

`EdgeRouteDiagnostics`: `edgesStraight`, `edgesBow`, `edgesDogleg`, `edgesFallback`,
`pathCrossingsDetected`, `pathCrossingsResidual` with pair keys, `pathHullPenetrations`. Timings live in a
separate `EdgeRouteTimings`.

### 6.10 The authoritative residual

Two counters exist and are never conflated:

- `EdgeConflictDiagnostics` counts **chord conflicts** (C1/C2) and is the relocation stage's record;
- `EdgeRouteDiagnostics.pathCrossingsResidual` counts **every proper intersection of the published drawn
  paths**, with no region exclusion. The §6.4 fan region affects only the router's **clearance acceptance
  rule**; it never removes a visible crossing from the residual count. This is the residual the user sees.

G6 is satisfied when the drawn-path residual is reported and equals the paths that no relocation or detour
could clear.

## 7. Invariants

- **I1** No pinned projected node changes position in the published frame (active pins only, exactly as
  `BoundarySeparationCorrection` filters them).
- **I2** The published frame never introduces a disc gap below `MIN_GAP` that the boundary stage had
  resolved, and never introduces a `BoundaryInvariants` violation.
- **I3** Every accepted relocation batch satisfies A1's pair-set monotonicity: no new conflict pair of
  either kind, strict decrease of the total.
- **I4** Every **accepted ladder candidate** satisfies §6.4's clearance rules and never exceeds
  `ROUTE_LENGTH_CAP`. A straight fallback is not a candidate: it may pass over discs and other paths, and
  is reported through `edgesFallback` and `pathCrossingsResidual`.
- **I5** An edge whose straight chord clears all obstacles is straight.
- **I6** Determinism: identical
  `(projection, positions, metrics, pins, visibleEndpoints, geometry)` produce identical positions, paths
  and diagnostics, across runs and across input order permutations.
- **I7** One active edge-geometry path; no legacy straight-render branch survives behind a flag.
- **I8** Routing never runs on the EDT and never inside the geometry cache.

## 8. Interaction with existing behaviour

- **Pins.** Active pins are immovable; a conflict whose every candidate moves a pinned node becomes a
  residual and is routed if a detour clears, else drawn straight. Pinning captures different coordinates
  depending on the path: the node context menu pins the **published** `geometry.center()` (i.e. the
  post-relocation position, `GraphInteractionController.java:510`), while a drag-release pin records the
  **mouse world point** (`GraphInteractionController.java:474`). Both are intended — the user pins what
  they see or drop — and both are documented in the specification.
- **Idle policy and displacement deltas.** The edge stage runs before `idlePolicy.observe` and before
  `previousCorrectedPositions`/`previousAppliedDisplacements` are updated
  (`LayoutWorker.java:294-303`), and its `appliedDisplacements` are merged with the boundary stage's
  (§4.2), so a relocation counts as motion and cannot be published while the frame is labelled idle.
- **Enclosures.** A relocated node keeps its enclosure path; hull shapes change only through their own
  descendants, and `BoundaryInvariants` re-verifies them. Hull outlines are deliberately not router
  obstacles (§3): every edge into an enclosed node must cross that hull's boundary, and an unrelated hull
  is translucent, so treating hulls as obstacles would force straight fallbacks without removing a visible
  crossing.
- **Prominence and zoom.** Radii use `BASE_RADIUS × prominence.scale()`, the `GraphGeometryEngine` rule.
- **Undo and persistence.** Relocations are view geometry, not model mutations; nothing is routed through
  `GraphCommand` and nothing new is written to the `.fpg`.

## 9. Performance

The engineering commitment is unchanged: 2,000 projected nodes and 5,000 projected edges stay interactive.
The harness is the existing gate, not a new one: `PerformanceMeasurements.Stage` (wire names pinned by
`PerformanceTripwiresShould.exposeTheCanonicalStageOrderAndThresholds`,
`PerformanceTripwiresShould.java:63-72`), `STRICT_FORCE_NANOS = 50 ms`,
`STRICT_FULL_WORKER_NANOS = 100 ms`, `STRICT_FIRST_FRAME_P95_NANOS = 150 ms`,
`STRICT_FIRST_FRAME_P99_NANOS = 300 ms` (`PerformanceMeasurements.java:17-20`).

- Two stages are added to the enum and to the pinned stage list in the same commit: `edge-conflict` (the
  relocation stage **plus** the §5.5 rebuild) and `edge-routing` (the publish-time router). Their strict
  thresholds are pinned by the specification from the first measurement, subject to the hard constraint
  that on the reference 2000/5000 fixture the boundary stage plus both new stages stay inside
  `STRICT_FULL_WORKER_NANOS` (100 ms) and the first-frame p95/p99 gates. There is no feature flag (§11), so
  a budget miss blocks the feature and forces a design revision; the specification names the tripwire test
  that must move with the threshold.
- The conflict index is a uniform grid over **both** conflict classes:
  - each drawn chord is inserted into every cell its segment actually traverses (not its bounding box), and
  - each disc inflated by `EDGE_NODE_CLEARANCE` is inserted into every cell it overlaps,
  and C1/C2 pairs are enumerated once through canonical pair-key sets. With bounding-box insertion a hub
  cell would hold all `E` chords (O(E²)); with traversal insertion a cell holds only the chords that
  genuinely pass through it, and a long chord costs O(traversed cells) instead of O(bbox area). C2 is
  O(chord–disc pairs sharing a cell), not O(E×N).
  - **Cell-key encoding:** indices are 64-bit integers derived from the frame's normalised coordinates with
    the cell size; the specification pins the encoding and an extreme-span fallback (coordinates outside
    the representable index range are clamped to the outermost ring, whose chords are then checked
    directly against that ring's cells) and names the geometry-suite extremes as a test case, so extreme
    spans cannot silently collapse the grid to a single hub cell.
  - **Enumeration cap:** the per-cell cap is a **work budget on inserted items** (chords plus inflated
    discs), not on pairs — counting pairs would require the quadratic enumeration the cap exists to bound.
    When a cell's inserted-item count exceeds the pinned cap the cell **subdivides**, never truncates; when
    subdivision is exhausted at the pinned minimum cell size the remaining pairs are still enumerated (no
    pair is ever dropped) and the frame sets `indexBudgetExhausted` with the counted work. Verification
    exhaustion from §5.3 sets `verificationBudgetExhausted` instead; the two flags stay distinct in
    `EdgeConflictDiagnostics`.
  - **Cell origin and normalisation:** cell index `i = floor((x - originX) / cellSize)` with the frame's
    minimum coordinate as origin; the specification pins the encoding and the outermost-ring fallback.
- Per frame the detection runs at most `1 + accepted rounds + rejected-batch verifications + 1 rebuild`
  times when conflicts exist, bounded by `MAX_VERIFICATIONS_PER_FRAME`; it runs once when no conflicts
  exist. A3 verification reuses `recomputeHulls(..., changedHulls(...))` per candidate batch; the only
  full hull computations per frame are the boundary stage's own and the publish-time one in
  `LayoutSettleLoop` (§4.3), not an extra one inside the edge-conflict stage.
- The router uses a grid over disc and path bounding boxes for its clearance queries and caches its result
  per published frame.

## 10. Test strategy

### 10.1 Committed reproducer and red phase

The reported cluster is committed as a headless fixture with the measured coordinates of §1.1 (a
`ProjectionScenario`-style fixture; the external `Point-set_Topology.mm` is not committed). The red phase
is scaffold-then-red, as the previous runs used: a behaviour-preserving scaffold commit introduces the
`EdgeConflictCorrection`/`EdgeRouter` seams with today's straight rendering and no relocation; the new
tests run red against that scaffold; then the implementation commit turns them green. At least one test
pins "no painted-path crossings on the reported fixture" and is demonstrated failing on the scaffold.

### 10.2 Named mutation probes

Each mechanism is probed by breaking exactly it and showing the covering test fails:

1. drop the strict set-decrease in A1 (accept equal counts);
2. drop the pair-set inclusion (allow a new conflict pair);
3. drop the A2 disc-gap veto;
4. drop the A3 hull veto;
5. drop the fan exemption in §6.4;
6. drop the not-yet-processed-chord obstacles in §6.2;
7. invert the canonical candidate order in §5.2;
8. remove the `ROUTE_LENGTH_CAP` fallback;
9. re-introduce the endpoint-to-endpoint arrowhead cap in place of the terminal-segment cap;
10. replace the rejection-retry single-candidate pass with a whole-batch discard;
11. drop the C2 visibility filter (§5.1);
12. allow an alternating-bend dogleg (§6.3);
13. drop the §5.5 rebuilt-record field preservation (copy the boundary displacements as empty);
14. replace the §4.2 terminal-geometry reuse with a fresh full `computeHulls` and show the budget tripwire
    catches the difference;
15. remove the §9 outermost-ring fallback and show the extreme-span fixture collapses to one hub cell;
16. flip the §5.2 C2 normal direction and show the disc does not clear the chord.

### 10.3 Test suites

- `EdgeConflictCorrectionShould` — convex-quad swap accepted and the crossing removed (both adjacent pairs
  evaluated); mirror candidates evaluated individually; mixed/hull-endpoint conflicts produce no candidate
  and no node movement; pinned endpoint blocks the swap; rejection of one batch still allows later disjoint
  repairs, including the single-candidate retry; enclosure membership; disc-gap veto;
  hull-nesting-without-overlap veto; pair-set monotonicity; determinism across runs and across permuted
  input order; residual reporting; round and verification bounds; `visibleEndpoints` participation.
- `EdgeRouterShould` — straight when clear; bow for a disc on the chord; dogleg when a bow is blocked;
  later-chord obstacle (the attempt-1 reviewer's counterexample, inverted into a test); fan exemption
  region; clearance invariants including the sampled-polyline tolerance bound; `ROUTE_LENGTH_CAP` fallback
  and rung exclusion; attachment tangency; terminal-segment arrowhead cap; canonical order; determinism and
  order permutation.
- `GraphCanvasPaintShould` — routed path painted through the production call site using the same sample
  list that was verified; arrowhead along the arrival tangent; multiplicity at the arc-length midpoint;
  selection/hover/dimming unchanged.
- `GraphInteractionControllerShould` and `GraphCanvasPaintShould` — routed-edge hit-test nearest point;
  straight-path behaviour unchanged.
- `LayoutWorkerShould` / `BoundarySeparationShould` — composition: boundary diagnostics on the published
  frame are rebuilt for the published positions after an accepted batch, with stage-level fields (rounds,
  displacement statistics) preserved and residual fields recomputed; node-key canonicalisation makes the
  rebuilt node residual order-independent; edge diagnostics appear on the frame and on failure frames; a
  failing relocation produces a failed frame, never a broken publish; idle/delta accounting includes the
  merged relocation displacements.
- `BoundaryInvariantsShould` — the extracted predicate set is the one `BoundarySeparationCorrection` used
  before the extraction (characterization test on a frozen fixture set, including the hull-nesting case).
- `PerformanceTripwiresShould` — the two new stages and their thresholds; the reference 2000/5000 fixture
  stays inside `STRICT_FULL_WORKER_NANOS` and the first-frame p95/p99 bounds; a hub/star fixture and the
  geometry-suite extreme coordinates exercise the grid encoding and the subdivision cap.

## 11. Rollout and legacy removal

- No feature flag: the correction and router are active by default, consistent with the previous graph
  features.
- The straight-line drawing branch in `GraphPainter.paintEdges` is replaced by the path draw in the same
  change; the edge hit-test's segment distance is replaced by path distance. No parallel implementation is
  retained.

## 12. Review history

### Attempt 1 — 3 blockers, 10 majors, 8 minors

| # | Finding | Resolution |
| --- | --- | --- |
| B1 | Router ignores later edges' chords; diagnostics never count drawn-path crossings | §6.2 obstacles = all other edges (paths + chords); §6.9/§6.10 drawn-path residual authoritative; probe 6 |
| B2 | A1 (total decrease) does not imply I4 (no new conflicts) | §5.3 A1 pair-set inclusion + strict decrease; I3; probe 2 |
| B3 | Hysteresis and timings contradict G4/I7 | §5.4 removes cross-frame state; timings split into `EdgeConflictTimings`; order-permutation test |
| M4 | Stage stops on first rejected batch | §5.3 rejection-retry; G1 restated as the enumerated search |
| M5 | A3 predicate list incomplete | §5.3 A3 reuses the extracted `BoundaryInvariants`; characterization test |
| M6 | Routing inside `GraphGeometryEngine` breaks recompute equality, runs on discarded frames | §4.3/§6.1 router moved to publish; `CanvasState` carries `EdgePaths` |
| M7 | Performance claims unfalsifiable; active list O(E²); pinned stage list | §9 rewritten (real thresholds, two new stages, traversal-rasterised grid, pass budget) |
| M8 | Hulls absent from the obstacle model | §3/§6.4 hulls explicitly non-obstacles + `pathHullPenetrations` + rationale |
| M9 | Arrowhead rule undefined for polylines | §6.6 terminal-segment cap; probe 9 |
| M10 | Candidate order not deterministic; `P0` undefined; rim/centre conflation | §5.2 canonical rotation, centres move, ordered candidates |
| M11 | Fan exemption only in detection | §6.4 fan exemption in path clearance + fan regression test |
| M12 | Path/attachment construction circular | §6.5 three-step construction order |
| M13 | No red-phase anchor, no committed reproducer, wrong hit-test suite, unnamed mutations | §10.1 scaffold-then-red; §10.2 probes; §6.8 suites named |
| m14-m21 | Frame plumbing, idle ordering, path precision, citations, pins, mockup captions, baselines, tolerances | §4.2, §5.6, §6.5-§6.7, §8, §13 |

### Attempt 2 — 0 blockers, 10 majors, 8 minors (revision 3)

F1 complete boundary residual rebuild; F2 rejection retry; F3 verification count; F4 traversal grid;
F5 fan region; F6 mirror candidates and C2 tie-break; F7 merged displacements; F8 obstacle sentence;
F9 two-shape cap invariant; F10 hull-map reuse; F11 false stability claim; F12-16 wording/citation/plumbing;
F17 enclosure-endpoint discard; F18 sampled-quadratic and terminal-length definitions. All verified
resolved in attempt 3.

### Attempt 3 — 0 blockers, 9 majors, 8 minors (revision 4)

| # | Finding | Resolution in revision 4 |
| --- | --- | --- |
| M1 | C1 candidate machinery undefined once a conflict endpoint is a hull | §5.2 scopes swaps/mirrors to all-node conflicts; mixed conflicts have no candidate, are routed and counted (§5.1) |
| M2 | §5.5's rebuilt diagnostics not constructible; dropping `appliedDisplacements` would break the delta path | §5.5 copies every stage-level field (rounds, violations detected, boundary displacements, delta stats) and recomputes only residual lists/conflicts via `detect` + terminal plan |
| M3 | Boundary terminal hull map not exposed; edge stage would full-compute hulls per frame, unbudgeted | §4.2 `BoundarySeparationResult.geometry()`; §5.5/§9 budget the rebuild; A3 reuses `recomputeHulls` |
| M4 | Grid indexes chords only; C2 has no index and degrades to O(E×N) | §9 inserts inflated discs into the same grid; bound extended to chord–disc pairs |
| M5 | Per-cell cap semantics undefined; truncation would silently drop pairs | §9 cap subdivides, never truncates; residual pairs are enumerated; `capExhausted` recorded |
| M6 | I4 contradicts the straight fallback | §7 I4 restricted to accepted candidates; fallback reported via `edgesFallback`/`pathCrossingsResidual` |
| M7 | C2 lacks the visibility filter; input tuple omits `visibleEndpoints` | §5.1 C2 requires both endpoints visible; G4/§5.4 include `visibleEndpoints` |
| M8 | Enclosure `endpointRadius` unpinned; "union of two discs" was one disc | §6.4 union of two discs, each at that path's own attachment, radius `endpointClearanceRadius(X) + EDGE_CLEARANCE`; `FAN_HULL_RADIUS` pinned in §13 |
| M9 | Cap bound rounded; rung-removal clause wrong for lower caps | §6.3 excludes any rung whose ratio exceeds the cap; §13 lists the exact ratios (bow 1.014804/1.057116/1.147794; dogleg 1.064390/1.230242/1.535184) |
| m1 | Verification accounting overstated | §5.3 counts every attempt against `MAX_VERIFICATIONS_PER_FRAME`; no `2 ×` claim |
| m2 | Sampling tolerance never bounded against clearances | §6.5 single sample list for verify/hit/paint; §13 bounds sagitta ≤ min clearance / 2 |
| m3 | "Minimal translation" vs lexicographic normal | §5.2 "along the lexicographically smaller unit normal … first clears" |
| m4 | Shared-endpoint chord exemption not justified for hull endpoints | §5.1 convexity argument; anything else is a C2 conflict |
| m5 | Dogleg sign convention ambiguous | §6.3 both bends same signed side; probe 12 |
| m6 | Mockup caption "disc gaps unchanged" | §4.1 + README reworded to "stay verified against `MIN_GAP`" |
| m7 | Residual re-run order-dependent | §5.5 canonical node-key sort before `project`; order-independence test |
| m8 | Grid cell-key encoding saturates at extreme spans | §9 encoding + outermost-ring fallback + geometry-suite extremes test |

### Attempt 4 — 0 blockers, 2 majors, 16 minors (revision 5)

| # | Finding | Resolution in revision 5 |
| --- | --- | --- |
| MAJOR-1 | §6.4/§6.10 contradict on fan-exemption crossings, hiding a visible residual | §6.4 waives only the *distance* requirement inside the fan region and forbids proper crossings anywhere; §6.10 counts **every** proper intersection of published paths, with no region exclusion |
| MAJOR-2 | §5.3 A3 has no acceptance predicate | A3 restated in subset form: `V_out ⊆ V_in` compared by hull-violation pair keys, baseline re-derived per round; pre-existing covered residuals never veto, new violations never pass |
| m1 | C2 scope wording contradicted the all-node sentence; descendant fixed point | §5.1/§5.2: C2 moves the passed-over node independently of the edge's other endpoint; whole-batch verification handles the hull fixed point, no special case |
| m2 | `CanonicalKeys` extraction cannot build `edgePair` for hull endpoints | §5.1: all encoding (including hull keys) moves to the public `CanonicalKeys`; no duplicated encoding |
| m3 | Drift claim backed only for detection | §5.5 names `BoundaryInvariants.describe(...)` and extends the characterization test to the terminal classification |
| m4 | Mirror direction and `CROSSING_CLEARANCE` bound unstated | §5.2 push direction pinned; §13 requires `CROSSING_CLEARANCE >` the largest drawn radius |
| m5 | Rung-cap boundary inconsistent (`exceeds` vs `≥`) | §6.3/§13: a rung is live iff its analytic ratio is `≤` the cap |
| m6 | Cap/encoding semantics not implementable as written; one shared flag | §9: item-count trigger, cell origin and normalisation, `indexBudgetExhausted` vs `verificationBudgetExhausted` |
| m7 | Mutation-probe claim no longer true for four mechanisms | §10.2 probes 13-16 added |
| m8 | Obstacle bullet excludes straight fallbacks | §6.2: the **published** path of already-processed edges, accepted or fallback |
| m9 | Failure-publish data path for `EdgePaths` unspecified | §6.1: every `CanvasState` construction routes the frame's own positions through a single factory |
| m10 | Relocation vs enclosure anchors unstated | §5.2: anchors are never modified; only recomputed hull geometry changes |
| m11 | Retained three-argument `withDiagnostics` is a legacy parallel path | §5.6: the overload is removed |
| m12 | Rebuild gate sentence misdescribes the gate | §5.5: rebuilt record is informational; the gate stays at the boundary stage and A3 guarantees no new violation |
| m13 | `pathLength`/`chordLength` basis unpinned | §6.3: anchor-to-anchor `chordLength`, sampled `pathLength`, per-candidate acceptance |
| m14 | Pin-coordinate claim only half true | §8: context-menu vs drag-release capture distinguished |
| m15 | "Only full hull computation" false | §9: scoped to the edge-conflict stage; the publish-time full compute is named |
| m16 | §5.1/§13 describe a different geometry than §6.5 builds | §5.1: straight chords for relocation vs published paths for painting; §13 bound reworded as a fidelity bound |

### Attempt 4 verdict

`0 blockers, 2 majors, 16 minors; specification-ready: no`. Revision 5 applies the two majors and all
design-level minors; the remaining spec-pinning items (numeric constants, live-rung list, grid encoding
detail) are §13's mandate for the specification.

### Attempt 5 — 0 blockers, 0 majors, 8 minors; specification-ready: yes

MAJOR-1 (fan-exemption vs `pathCrossingsResidual`) and MAJOR-2 (A3 subset predicate) verified resolved, and
m1-m16 verified resolved with no regression. The eight remaining minors are specification-pinning mandates,
not design defects:

| # | Specification mandate |
| --- | --- |
| N1 | Pin the C2 conflict identity key (edge key + passed-over node key) so A1's set comparison can distinguish two C2 conflicts on one edge |
| N2 | Define `BoundaryInvariants.describe`'s `baselineResidualKeys` parameter (role, format) or drop it |
| N3 | Name the accessor that carries the rebuilt `BoundarySeparationDiagnostics` out of `EdgeConflictResult` into `LayoutFrame.withDiagnostics` |
| N4 | Use one pair of exhaustion-flag names across §5.3/§5.6/§9/§13: `verificationBudgetExhausted`, `indexBudgetExhausted` (applied in revision 6 for the design; the spec re-checks every mention) |
| N5 | Update the remaining `CanvasState` construction sites (`GraphUpdateCoordinator.java:136,561`, `CanvasState.withStatus`) to the `EdgePaths`-taking factory |
| N6 | Update the unnamed three-argument `withDiagnostics` call sites (`LayoutWorker.EMPTY_FAILED_FRAME`, `LayoutSettleLoopShould.java:1203,1410`, `GraphUpdateCoordinatorShould.java:1194,1604,1646`) |
| N7 | Add negative tests for the §9 subdivision cap/cell-key encoding and for §6.4's "a proper crossing is never accepted anywhere" rule |
| N8 | State explicitly (and test) that the edge stage leaves enclosure anchors at their pre-batch values, unlike the boundary stage's subtree averaging (applied in revision 6 for the design) |

## 13. Tolerance and constant invariants (values pinned by the specification)

| constant | unit | invariant |
| --- | --- | --- |
| `EDGE_NODE_CLEARANCE` | world units | `> 0` and `≤ MIN_GAP / 2` so a routed path can pass between two discs at the minimum gap |
| `EDGE_CLEARANCE` | world units | `> 0` and `≤ MIN_GAP / 2` for the same reason |
| `CROSSING_CLEARANCE` | world units | `> 0` **and `>` the largest drawn node radius**, so a mirrored disc leaves the opposite chord's line entirely; large enough that a mirrored endpoint is strictly outside the opposite chord under the detection epsilon |
| `MAX_BEND` | world units | `> 0`; bounds the bow/dogleg offsets independently of the ladder fractions |
| `ROUTE_LENGTH_CAP` | ratio | defines the live ladder: a rung is live iff its analytic ratio is `≤` the cap. Measured ratios: bow 0.15/0.30/0.50 = 1.014804/1.057116/1.147794; same-side dogleg 0.15/0.30/0.50 = 1.064390/1.230242/1.535184 |
| detection epsilon | world units | pinned; the crossing predicate is exact-sign based, the epsilon covers only degenerate touch |
| `MAX_RELOCATION_ROUNDS` | count | `≥ 1` (draft 4); each accepted round strictly shrinks the conflict set |
| `MAX_VERIFICATIONS_PER_FRAME` | count | bounds every A1-A4 verification attempt including the §5.3 single-candidate retries; on exhaustion the stage stops with `verificationBudgetExhausted` (distinct from §9's `indexBudgetExhausted`) |
| bow sampling tolerance | world units | maximum sagitta of the sampled polyline; `≤ min(EDGE_NODE_CLEARANCE, EDGE_CLEARANCE) / 2`. It is a **fidelity bound**, not a safety margin: §6.5 makes the same sample list both verified and painted, so the bound keeps the drawn curve indistinguishable from the verified one at the clearances in play |
| `FAN_HULL_RADIUS` | world units | `> 0`; the hull-endpoint part of `endpointClearanceRadius` (§6.4) |
| conflict-index cell size | world units | `> 0`; with the 64-bit cell-key encoding and outermost-ring fallback of §9 |
| conflict-index per-cell work cap | inserted-item count | triggers cell subdivision, never truncation (§9); exhaustion sets `indexBudgetExhausted` |
