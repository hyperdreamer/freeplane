# Graph Workspace Node Separation — Design

- Date: 2026-09-12 (revision 5.2)
- Review status: **approved for specification drafting** (review attempt 5: 0 blockers, 2 majors, 9 minors).
  Revision 5.2 applies the specification reviewer's design-level findings: it removes revision 4's
  inward-slot wording and revision 5.1's false single-form corner bound, adds the enclosure-label slot model,
  and states I2 as a rectangle-level contract with its measured ink fringe.
- Topic: graph-node-separation
- PM run: `pm-run-20260912-012815-01038a52`
- Delivery target: `refs/heads/plugin/graph-workspace` at `/data/home/guest/Development/freeplane`
- Integration branch: `pm/graph-node-separation/run-pm-run-20260912-012815-01038a52`
- Evidence: `docs/superpowers/specs/mockups/2026-09-12-node-separation/` (seven panels, generator reproduces them byte-for-byte)
- Review history: r1 → 1B/5M/6m; r2 → 1B/6M/6m; r3 → 1B/7M/6m; r4 → 2B/7M/9m.
  Revision 5 resolves the attempt-4 findings and, in response to the pattern those findings show,
  **deliberately shrinks the claim surface**: constants, thresholds and exhaustive fixtures move to §12
  and are pinned by the specification, not asserted here. §11 maps every finding from all four attempts.

## Revision 5 changelog

1. **Verification is closed over every publication path.** Revision 4 claimed that every published frame
   is verified, but six frame-construction sites exist and the failure path synthesizes positions with no
   residual. §5.2 now enumerates them and defines residual propagation for each. (was BLOCKER 1)
2. **The displacement bound is stated in screen pixels**, because the slots use the on-screen radius:
   `max(2, r·zoom) + slotOffset + halfBox`. Revision 4 mixed the world radius with screen pixels. (was BLOCKER 2)
3. **Pan invariance is dropped.** Placement is recomputed when the viewport changes and the viewport is in
   the cache key; the earlier "pan never invalidates" claim contradicted the pan-dependent painted surface. (was MAJOR 3)
4. N5 and §8.2.4 no longer claim to pin the projection's hull effect; only the pre-projection correction
   behaviour is pinned, with exact expected values. (was MAJOR 1)
5. Endpoint-kind rest length completed: `R(k) = 0` for `k ≤ 1`, `max_r` defined. (was MAJOR 2)
6. Pinned-pinned pairs are defined (never moved, counted in the residual) and "I1 is guaranteed" is
   reworded to what is true. (was MAJOR 4)
7. Proposed projection constants are named (`MAX_PASSES = 64`, `RELAXATION = 0.5`) and the convergence
   test is restated in terms of them. (was MAJOR 5)
8. The measurement source for label rectangles is pinned to the painter's own transform and FRC, with
   assertions at zoom 0.25 and 2.0. (was MAJOR 6)
9. The placement performance budget is defined as a *method* with a pre-change baseline, and the threshold
   moves to §12 rather than being invented here. (was MAJOR 7)
10. Nine minors: citations corrected, the emphatic-label exception is recorded on the placement result,
    the red-phase fixture uses only edge-constructed prominence, per-slot width limits are stated,
    the unreproducible sweep minima are deleted, the idle baseline is named, the "free slot" predicate is
    defined, and the single-line label assumption is stated.

## 1. Problem

Nodes inside an enclosure are placed too close to one another and sometimes overlap
visually: three cramped nodes in "Basic Definitions and Theorems" with colliding text,
and two nodes behind the "Axioms" enclosure with a label drawn across a disc.

### 1.1 The disc invariant is violated by construction

`TypedSpringBox.REST_LENGTH = 24.0` (`TypedSpringBox.java:18`) is used for relationship
links and containment links at `GraphStreamLayoutEngine.java:230` and `:239` and nowhere
else. Relationship links are built by `endpointId` (`GraphStreamLayoutEngine.java:295`),
which can return an enclosure-anchor id, so "relationship" does not imply node↔node.

A node renders at radius `NODE_RADIUS (8.0) × prominence scale` (`:40`,
`NodeProminence.MAX_SCALE = 1.75`), so a prominent node renders at radius 14.
`TypedNodeParticle.scaleRepulsion` (`:84-89`) scales the repulsion a node receives by
`separationRadius / 8`, so the two-body equilibrium is not the rest length: solving
`0.05·(d − 24) = 16·1.75 / d²` gives **d = 24.90299406686857**; a reflection probe
against the shipped `plugin-1.13.4.jar` measured **24.90299406686856**. Two connected
prominent nodes overlap by **3.097** against the disc sum 28, falling **9.097 short** of
the invariant floor 34.

Nothing in the layout prevents disc overlap: repulsion is a soft `K2·w/d²` force
(`K2 = 16.0`, bytecode-verified, skipped entirely at `d == 0`), and the only overlap
correction translates whole maps uniformly.

### 1.2 Text collision is the larger visible defect and is not a layout problem

Node labels are painted by `GraphPainter.paintLabels` at a fixed slot centred above the
disc with **no collision avoidance at any rendering level**. Enclosure labels go through
`LabelPlacementEngine`, which avoids only other enclosure labels
(`LabelPlacementEngine.java:134, 157, 276`), never discs or node labels, may expand the
drawn hull by up to `MAX_INTERIOR_EXPANSION = 8.0` (`:22, 162-196`), and measures with a
fixed 12 pt box in the zoom-independent pipeline (`LayoutSettleLoop.defaultMetrics`,
`:960-962`) while the painter draws `size/zoom` world units, under-reserving below 100 %.

Labels are painted at constant **screen** size while discs scale with zoom: disc
non-overlap is zoom-invariant, label non-overlap is not. No world-space invariant can
express "text never overlaps".

### 1.3 Prior art checked, and rejected

Obsidian's graph view sizes nodes by incoming references and fades labels by zoom with a
documented **text fade threshold**; its team explicitly declined label collision
detection. Freeplane's node name *is* its content, so fading is a poor fit — but the
evidence settles *where* collision must be solved: in screen space.

## 2. Goals

- **G1** On every published frame, either every node pair satisfies
  `distance ≥ r_i + r_j + MIN_GAP`, or the frame reports a non-zero residual that makes the
  failure observable. The residual describes the positions being published.
- **G2** Node text never overlaps other node text, enclosure text, or any disc, at the zoom
  being rendered, and is never silently clipped.
- **G3** When no slot exists, degradation is explicit, ordered, and deterministic.
- **G4** The label placement result must not influence geometry.
- **G5** No persisted-format change: positions, pins, and workspace XML untouched.

## 3. Non-goals

- **N1** No change to `K2`, the solver, or the settle/idle policy.
- **N2** No label-aware layout expansion.
- **N3** No zoom-driven label fading.
- **N4** `BoundarySizes` stays label-aware for anchor sizing and top-tier ring placement.
- **N5** **Hull separation is neither fixed nor guaranteed.** `MapTierCorrection`'s
  pairwise half-delta summing has ≥3-map limitations, and the projection can move a node so
  that a hull recomputed from published positions overlaps a neighbour's. The design pins
  only the correction's own pre-projection behaviour (§8.2.4). The user-facing rule is I1,
  which does not depend on hull geometry.

## 4. Contract

- **I1 — disc separation (world space).** For every pair of projected nodes
  `distance(c_i, c_j) ≥ r_i + r_j + MIN_GAP`, where `MIN_GAP = 6.0` world units, declared
  once (`org.freeplane.plugin.graph.layout.NodeSeparation.MIN_GAP`). Two *pinned* nodes may
  overlap; the layout never moves a pinned particle, and any pair still violating after the
  enforcing stage is counted in the residual.
- **I2 — text separation (screen space, per rendered frame).** Every drawn label rectangle
  is disjoint from every other label rectangle and from every disc rectangle, lies inside
  the painted surface, and is measured with the painter's own transform and font render
  context (§5.3). The faces that can paint text are 12 pt, 9 pt dense and 15 pt bold
  emphatic; the theme's 7 pt face never paints because `shouldPaintLabel`
  (`GraphPainter.java:301-305`) suppresses non-forced labels at `OVER_TARGET`.
  Labels are single-line, so a label box is one line high.
  I2 is a **rectangle-level contract**: antialiased glyph ink can extend beyond a measured logical box
  (measured 0.44–0.76 px over the fixture set), while the measured fixtures keep minimum rectangle gaps
  above twice that fringe (1.66 px at zoom 1), so painted text stays visually disjoint. The specification
  pins the fringe and the assertion; I2 does **not** require ink ⊆ rectangle, which is unachievable and
  which this design never required.
- **I3 — degradation ladder (ordered).** `full@paintedFont → full@displaced → denseFont →
  displaced denseFont → truncated → hover-only`. Forced labels (selected, hovered, related)
  are placement inputs, placed first, never below full text. Emphatic enclosure labels are
  required; when no slot exists they are forced at the hull label anchor as a documented I2
  exception, recorded **on the placement result** (not on a published frame, since placement
  happens after publication) and never thrown from the paint path.
- **I4 — stability.** Placement is deterministic in `(request, previous)`; a label keeps its
  previous slot while that slot remains collision-free.

## 5. Architecture

```
physics particles  (GraphStream)
      │
      ▼ raw LayoutPositions           ──┐
GraphGeometryEngine (hulls)             │ rev 5: verification is closed over
MapTierCorrection (whole maps)          │ every frame factory, not only this one
NodeSeparationProjection (enforcing)    │
      │                                 │
      ▼ verified positions + residual  ─┘
LayoutFrame / CanvasState publication
      │
      ▼ hulls recomputed from published positions
ScreenLabelPlacement (screen space) → GraphPainter
```

### 5.1 Springs that agree with the invariant

- **Rest length by endpoint kind.** `radiusOf(endpoint)` is the disc radius for a node
  endpoint, and for an enclosure-anchor endpoint the **content ring radius** `R(k)` of that
  anchor's `k` direct node children, where
  `R(k) = (2·max_r + MIN_GAP) / (2·sin(π/k))` for `k ≥ 2` and `R(k) = 0` for `k ≤ 1`
  (`k = 0` is the empty-enclosure case, whose ring is undefined but never needed).
  `max_r` is the largest disc radius among that anchor's direct node children (0 when there
  are none). A relationship link's rest length is
  `radiusOf(first) + radiusOf(second) + MIN_GAP`, covering node–node, node–anchor and
  anchor–anchor links with one rule. Verified: the adjacent chord of a regular k-gon is
  `2R·sin(π/k)`.
- **Containment links** (anchor → direct child) use the same `R(k)`. For `k = 1` the child
  converges onto the anchor: the spring with `restLength = 0` pulls an off-centre child
  inward (`TypedSpringBox.java:91`, behaviour at `:105-111`) and the zero-distance fallback
  is the only separator. The pair is not a disc pair, so I1 does not apply and no `NaN`
  arises.
- **Seeds** (`GraphStreamLayoutEngine.java:670-687`, currently
  `sizes.directNodeRingRadius(parentKey)` at `:682`) call the same disc-derived helper, so
  the label-aware seed (≈134 for "Axiom of Choice") and `R(2) = 17` no longer disagree by
  7.9×. `BoundarySizes.directNodeRingRadius` keeps its label-aware formula for
  `directNodeReach → sizeOf → boundaryRadius` and `topRingPosition` (N4).

`REST_LENGTH` becomes unused and is deleted. `LayoutCalibration` multipliers are unchanged;
anchor-to-anchor hierarchy links keep `GROUP_SPACING` / `SUB_GROUP_SPACING`. Settled
positions change, so seeds, step counts and fixture expectations move (§10).

### 5.2 `NodeSeparationProjection` — the enforcing stage

```java
final class NodeSeparationResult { LayoutPositions positions; int residualViolations; }
NodeSeparationResult project(GraphProjection projection, LayoutPositions positions, Set<ProjectedNodeKey> pinned);
```

- **Placement.** After `MapTierCorrection`, before every path that publishes. Nothing that
  moves positions runs later.
- **Algorithm.** Deterministic pairwise relaxation in a fixed order (nodes sorted by
  projected key); a violating pair separates by half the penetration, scaled by
  `RELAXATION = 0.5`; a pinned node absorbs none, its movable partner takes all; a
  **pinned-pinned pair is never moved and is counted in the residual**. Uniform spatial
  hash, early exit, `MAX_PASSES = 64` passes. Both constants are proposed here and pinned by
  the specification (§12); they exist so the convergence test can be written down.
- **Residual.** After the last pass the code recomputes **all-pair** violations on the
  returned positions and stores that count. The residual therefore always describes the
  positions being published, including pinned-pinned pairs and any two-sided configuration
  that did not converge. No displacement bound is claimed: revision 3's "≤ 2.2 units" was an
  arithmetic error and is deleted.
- **Non-finite positions** are rejected with `IllegalArgumentException` (guards live in
  `GraphStreamLayoutEngine.java:324,336`, `PerceptualIdlePolicy.java:95`).
- **Stateless**, so a correction is never fed back into the solver.

**Verification is closed over every publication path (revision 5).** There are seven
`LayoutFrame.of` call sites, but all published frames originate from `LayoutWorker.accept`,
`LayoutWorker.failedFrame`, `LayoutSettleLoop.failedFrame` or the empty initial frame, and
`GraphUpdateCoordinator.publishFailure` republishes `state.layout()`. The worker-failure path
synthesizes positions rather than projecting them:

| site | requirement |
|---|---|
| `LayoutWorker.java:292-293` (normal) | projection runs; residual from `NodeSeparationResult` |
| `LayoutWorker.java:346` (failure, retained frame) | carries the retained frame's residual |
| `LayoutWorker` `EMPTY_FAILED_FRAME` (`:34-35`) | residual recomputed or explicitly marked unknown, never silently 0 |
| `LayoutSettleLoop.java:744` / `:747` (worker-failure fallback) | `fallbackPositions` routes through `NodeSeparationProjection` before publication, so the frame carries a real residual |
| `GraphUpdateCoordinator.java:134-136` (initial frame) | positions come from the projected initial layout or the projection is applied |
| `GraphUpdateCoordinator.java:561` (failure republish) | republishes `state.layout()`, so the residual preserved is the one already verified |

`LayoutFrame` gains the residual field beside `conflicts` and `idle`
(`LayoutFrame.java:16-30`); a frame may not be constructed without one.
`GraphStreamLayoutEngine.java:346` also builds a frame, but engine frames always pass through
`accept`, which rewraps failed frames (`:280-282`), so it never publishes unverified positions.
`NodeSeparationResult` must be reachable from the `control` package that publishes frames
(public, or moved into `layout`). §8.2.2 asserts
equality between the published residual and an independent recomputation for a normal frame
**and** for a fallback frame.

**Cross-map behaviour.** `HULL_CLEARANCE` is measured from the disc edge
(`GraphGeometryEngine.java:212`), so hull separation alone is ample for small gaps; the
hazard is `MapTierCorrection`'s pairwise half-delta summing with zero-translation skipping
(`MapTierCorrection.java:61-79`), which can under-separate maps — the collinear case
`(0,0),(40,0),(−40,0)` with ±30 hulls ends at `(0,0),(50,0),(−50,0)`, leaving hulls A and B
overlapping by 10. The hazard is real; revision 4's quoted sweep minima were not reproducible
from any committed artifact and are deleted rather than replaced with unverifiable numbers. Because the projection runs last and covers all pairs, its result — not the
correction — is what G1 reports. Hull consequences are out of scope (N5).

### 5.3 `ScreenLabelPlacement` (new, screen space)

```java
final class LabelPlacementRequest {
    GraphProjection projection; GraphGeometry geometry; double zoom;
    Rectangle2D placementArea;              // painted surface, screen space
    Set<ProjectedEndpointKey> forced; RenderingLevel renderingLevel;
}
List<PlacedLabel> place(LabelPlacementRequest request, List<PlacedLabel> previous, LabelFonts fonts);
```

- **Placement area = the painted surface**, in screen coordinates at the current zoom.
  Candidates outside it are rejected, so a placed label is never silently clipped. Slot order is the single
  global order pinned by the specification; near a surface edge, out-of-surface candidates are rejected and
  the ladder falls through to inward slots, so no separate per-edge ordering rule is needed. (Revision 5.2
  removes revision 4's "inward slots are tried first, in a fixed per-edge order", which contradicted the
  mockup evidence and every fixture table measured from it.)
  **Pan and zoom are inputs**: panning changes the surface and therefore the key, so
  placement is recomputed. Revision 4's pan-invariance claim is dropped — it contradicted
  the pan-dependent surface (`GraphCanvas.java:392-412, 439-449`).
- **Measurement source pinned, including the space convention.** The painter derives
  `size/zoom` under a zoom transform (`GraphPainter.java:64, 83-89, 289-298`), so painted ink is
  ≈ 12 px on screen at every zoom. Placement therefore measures **screen-space** boxes: base
  font metrics multiplied by zoom, equivalently the base font measured in a screen-space
  `FontRenderContext` — never the world-size font measured under identity, which would
  reproduce exactly the under-reserve defect of §1.2. The placement result **carries the font it used** and the painter paints
  that font verbatim. Revision 4 left "carry the font" doing work it cannot do on its own.
- **Forced state and rendering level are inputs**; the painter loses its independent node
  forced bypass (`GraphPainter.java:301-305`).
- **Obstacle grid** seeded with screen rectangles for every disc and every enclosure-label
  reservation, with placed labels inserted as accepted; candidate tests are grid queries.
- **No hull growth.** `LabelPlacementEngine`, `LabelPlacement`, `GraphGeometry.labels()`,
  hull expansion and `MAX_INTERIOR_EXPANSION` are deleted; `LabelPlacement.Mode` moves onto
  the new result type.
- **Priority:** forced (selected → hovered → related) → enclosure labels (emphatic → subtle)
  → node labels by descending disc radius.
- **Enclosure-label slots.** Interior at the hull label anchor when the rectangle fits and no disc occupies
  it; otherwise arc slots on the longest hull edges; otherwise exterior slots with a leader line; otherwise
  hover-only — exempt for emphatic labels, which are forced at the anchor as the documented I2 exception.
  The specification pins the anchor, the per-tier fonts, the exterior candidate normals, lane spacing, the
  candidate budget, and the leader start point (nearest boundary point of the anchor's hull), inheriting the
  deleted engine's constants rather than re-deriving them. (Added in revision 5.2: revision 5 left the
  enclosure path only implicitly specified, which the specification review flagged.)
- **Slots and limits (design parameters).** Eight near slots at a 6 px gap; four displaced
  slots at 30 px. Maximum slot widths: 200 px above/below, 130 px left/right, 150 px
  diagonal. Displacement and rectangle support are bounded **by construction**, because every offset and
  width is a screen-space constant; the exact per-slot closed forms — which depend on the slot's direction,
  gap and width limit — are pinned by the specification, with measured maxima of 78.7 px leader displacement
  and 143.5 px rectangle support. Revision 5.2 deletes revision 5.1's single-form
  `r' + 30 + halfDiagonal` corner bound, which the specification falsified with a 150.2 px counterexample.
  There is no separate cap: revision 3's 90 px rule was unreachable and is not replaced.
- Leader lines are drawn for every slot other than directly above/below the disc.

### 5.4 Painting and caching

- The canvas paint layer owns a `ScreenLabelPlacementCache`; `CanvasState` is immutable and
  published at layout time (`LayoutSettleLoop.java:548, 723`), while zoom, pan and paint
  state live in the paint layer (`GraphCanvas.paintComponent`, `:358-365`).
- **Key:** projection generation, positions identity, zoom, viewport origin and size, forced-set
  digest, rendering level, font/theme identity.
- **`previous` is retained across key changes**: the key decides when rectangles must be
  recomputed, while the previous assignment is kept and re-validated so I4 stickiness
  survives a small layout change.
- Accessibility, search and tooltips read projection labels
  (`AccessibleGraphCanvas.java:494`, `GraphSearchModel.java:61`,
  `ContributorInspector.java:300`) and are unaffected.

## 6. Degradation rule and its measured limits

Measured with the committed generator (byte-reproducible). The enclosure label is in the
obstacle set for the short-name rows; the long-label rows are shown with and without it, because
including it changes those counts:

| fixture | viewport | result | collisions |
|---|---|---|---|
| 12 nodes, short names | 1128×364 … 420×240 | full 11, hidden 1 | 0 / 0 |
| 12 nodes, short names | 280×170 | full 7, dense 4, hidden 1 | 0 / 0 |
| 12 nodes, short names | 200×130 | full 2, dense 4, hidden 6 | 0 / 0 |
| 6 nodes, 40–49-char names | 1128×364 / 500×300 | dense 1, truncated 5 (6 with the enclosure obstacle) | 0 / 0 |
| 6 nodes, 40–49-char names | 200×130 | full 1, truncated 2, hidden 3 (4 with the enclosure obstacle) | 0 / 0 |

Truncation is reachable only with long labels; for short names it fires 0 times at every
size, and displacement-first versus truncation-first ladders produce identical placements
from 200×130 to 1128×364, because a blocked slot is blocked by occupancy rather than by
label width. Recorded so it is not "fixed" later by mistake. Displacement carries the load
for ordinary names: dense fixture mean offset 48.05 px, maximum 77.9 px; long-label fixture
maximum 78.7 px; leader crossings 0 on the dense fixture. The mean and maximum come from an
instrumented probe; the committed generator prints the histogram, collision counts and the
long-label maximum. Hover-only marks hidden labels
with a dashed ring.

## 7. Error handling

- **Coincident particles:** deterministic key-derived axis, never `NaN`; a coincident
  label/disc pair counts as a collision.
- **Pinned-pinned overlap:** never moved, counted in the residual, documented.
- **Non-convergence:** residual recomputed after the final pass and published; frames are
  never dropped, so a violation is observable rather than hidden.
- **Non-finite geometry:** rejected at the projection boundary.
- **Emphatic enclosure labels:** forced at the anchor with the I2 exception recorded on the
  placement result; never an exception in the paint path.

## 8. Test strategy

Section 8 states the falsifiable *shape* of each test. Exact fixtures, coordinates, constants
and thresholds that are not measured here are specification inputs (§12).

### 8.1 `NodeSeparationProjectionShould` (pure function)

1. **Red phase.** Edge-construct prominence (a node needs ≥14 visible outgoing targets for
   scale 1.75 / radius 14; `GraphProjection` exposes no caller-supplied prominence), assert
   `violationCount(raw) > 0`, then `residualViolations == 0` and
   `violationCount(result.positions) == 0`. The math-notebook fixture cannot be red.
2. Half/half displacement; with one pinned, only the partner moves, pinned coordinate
   bit-identical; a pinned-pinned pair is untouched and counted in the residual.
3. Already-satisfied positions unchanged — paired with test 1 so it cannot pass vacuously.
4. Coincident particles separate deterministically; no `NaN`; repeated runs byte-identical.
5. **Perf bound:** 2000-node / 5000-edge fixture with a numeric p95 budget (spec input),
   recorded against the existing `ProjectionEngine.project` stage (p95 12,161,679 ns /
   p99 15,233,301 ns, `docs/superpowers/specs/2026-08-10-graph-workspace-performance-report.md:185`,
   measured at `GraphWorkspacePerformanceDiagnostic.java:271-273`), which is a *different* stage
   from the new separation pass, so the new stage records its own baseline first. The pass count
   is bounded by `MAX_PASSES`.
6. **Non-convergence:** the two-pinned sandwich fixture (spec input) reports
   `residualViolations > 0` for the stated `MAX_PASSES`/`RELAXATION`, returns finite
   positions, and the published frame carries the same count; a solvable fixture asserts
   convergence to zero in the same pass budget.

### 8.2 Frame-pipeline tests

1. **Per-frame I1 on published positions**, asserted with `residualViolations == 0`, on a
   fixture where the projection is asserted to displace at least one node.
2. **Verification closure:** for a normal frame **and** for a worker-failure fallback frame,
   the published residual equals an independent all-pair recomputation on those positions.
3. Omitted-fix guard: with the projection disabled the per-frame test fails.
4. **Correction characterization (N5).** For hand-built ±30 hulls at `(0,0), (40,0), (−40,0)`
   assert the exact correction result `(0,0), (50,0), (−50,0)` and recomputed A/B hull overlap
   `10`. A literal one-node-per-map fixture gives different numbers (default prominence is
   radius 8, half-extent 24). **No claim is made about hull overlap after the projection**, and
   no fixture is asserted for it.

### 8.3 `ScreenLabelPlacementShould`

1. **Painted-ink I2:** render each label alone into a transparent layer using the font the
   result carries, assert pairwise bitmap intersection is empty and label bitmaps miss disc
   bitmaps, at zoom 0.25, 1.0 and 2.0 (the zoom-2 case is where revision 4's bound broke).
2. Zoom {0.25, 0.5, 1.0, 2.0} × level {FULL, DENSE, OVER_TARGET}, at least one emphatic
   enclosure: no intersections; at OVER_TARGET assert non-forced labels are absent rather
   than placed.
3. **Ladder order and reachability.** Long-label fixture (six 40–49-char names, 500×300):
   at least one truncated label. Predicate for the ordering assertion: a label is
   "truncatable while a full slot was free" if, at the moment it is processed, some candidate
   slot would accept the full text without intersecting the obstacle set as it then stands.
   Short-name fixture at 1128×364: no truncation.
4. Forced labels: present, full, placed first, and re-placing on hover never intersects —
   including a hover-only label becoming forced.
5. Stickiness: a 1 px position change preserves assignments; an invalidated slot is re-placed
   and does not oscillate while valid.
6. **Displacement bound:** every label within `max(2, r·zoom) + 30 + halfBox` of its disc,
   asserted at zoom 1 and zoom 2, with measured maxima recorded.
7. **Viewport change:** changing zoom or panning recomputes placement, and the recomputed
   placement still satisfies test 2 (revision 4's pan-invariance claim is gone).
8. Enclosure labels: interior when it fits and no disc occupies it, exterior with a leader
   otherwise, emphatic forced at the anchor when nothing fits, hull polygons identical either
   way.
9. **No silent clipping:** every placed label rectangle lies inside the painted surface,
   asserted against the canvas's surface bounds.
10. **Geometry independence (wiring):** run placement through the real paint path and assert
    the published `CanvasState` geometry is identical before and after zoom, forced-set and
    placement changes.

### 8.4 Performance

Method (thresholds are spec inputs):

- Extend `GraphWorkspacePerformanceDiagnostic` with a **paint-path placement stage**;
  record a pre-change baseline for that stage before fixing a p95 threshold.
- Defined population: settle-frame cache misses, zoom changes, pan changes, forced-set
  changes; stated warm-up and sample count; cache state stated per measurement.
- Pan and zoom are expected to cost one re-placement per change, so their budget is the
  same stage budget, not a translate-only exemption.
- The old worker-side enclosure-only measurement (p95 123,308,455 ns / max 143,041,802 ns,
  `.../2026-08-10-graph-workspace-performance-report.md:191`) is removed rather than
  reinterpreted; it measured a different execution path.

### 8.5 Regression

Full module suite. Removal surface: **seven** test files reference `LabelPlacement` /
`LabelPlacementEngine` / the 3-arg `GraphGeometry.of` (`LabelPlacementShould` — replaced by
§8.3 — `GraphCanvasPaintShould`, `GraphInteractionControllerShould`,
`GraphWorkspaceModelAcceptanceShould`, `GraphWorkspacePerformanceDiagnostic`,
`GraphWorkspaceWindowModelShould`, `WorkspaceDialogsShould`); four more
(`ReferenceRepulsionFixture`, `GroupOnlyProjectionShould`, `ProjectionDeterminismShould`,
`StructuralProjectionShould`) are churn-affected, thirteen files call `LayoutFrame` factories,
and `PerformanceTripwiresShould.java:64-67` carries fixture hashes that move. Production call sites:
`LayoutSettleLoop.java:544`, `:721`, `LabelAssembler` (`:982`); the only reader of
`GraphGeometry.labels()` is `GraphPainter.java:262`. Settle/idle must not regress: the
two-map fixture's idle frame count in `LayoutSettleLoopShould` is compared against a
baseline recorded in that test before the change.

## 9. Mockups

Generator and PNGs are committed together and regenerate byte-for-byte (verified in review).

| panel | today | proposed |
|---|---|---|
| 01-disc-invariant | settled 24.90, overlap 3.10, I1 shortfall 9.10 | centres 34.00 → 0 overlaps |
| 02-sparse | 2 label/label collisions | 0 collisions, 3 full labels |
| 03-dense | 13 label/label + 17 label/disc pairs | 0 collisions, 11 full, 1 hover-only |
| 04-zoom | — | 11 of 12 placed at 100 %, 8 at 50 % |
| 05-cramped-window | 13 + 17 collisions | 0 collisions |
| 06-ladder-truncate-vs-displace | — | (a) and (b) identical at every size |
| 07-truncation-reachable | — | long labels: 5 truncated at 500×300 |

These are a *simulation* of the described algorithms, not the production renderer. Panel 01
is generated from the solved equilibrium (24.90299407). Panels 02/03/05 are synthetic scenes.

## 10. Risks

- **Fixture churn:** disc-derived rest lengths and seeds change settled positions, snapshots
  and performance hashes; expectations move and must be re-recorded deliberately.
- **Hull separation (N5):** neither guaranteed nor claimed; only the correction's own
  behaviour is pinned.
- **Hull shrinking:** removing interior expansion shrinks boundaries by up to 8 units.
- **Removal surface:** five placement-aware test files plus four churn-affected ones.
- **Placement cost on the paint path** is the riskiest new number; §8.4 measures it before a
  threshold is set.
- **Two rungs that rarely fire:** truncation needs 40+ character labels, hover-only needs a
  cramped surface; both are deliberate.

## 11. Review findings → resolution

**Attempt 1 (1B/5M/6m).** B1 placement interface → §5.3, §8.3.1/8.3.4. M1 ordering →
§5.2. M2 equilibrium → §1.1, panel 01. M3 seeds → §5.1. M4 enclosure labels/hull growth →
§1.2, §5.3, §8.3.8. M5 non-falsifiable tests → §8. m1–m6 → result type, guard citation,
cache semantics, `MIN_GAP`, G4 narrowing, probe.

**Attempt 2 (1B/6M/6m).** B1 cross-map unit error → §5.2. M1 panel reproducibility → §9.
M2 red phase → §8.1.1. M3 truncation fixture → §6, §8.3.3. M4 false bound → §5.3. M5 no
budget → §8.4. M6 cache key → §5.4. m1–m6 → probe, `Mode` relocation, file list, k=1,
I1 scoping, wiring test.

**Attempt 3 (1B/7M/6m).** B1 verification before the last mutation, false bound → §5.2
single enforcing stage, residual recomputed after it, bound deleted, §8.2.2. M1 hull
invalidation/N5 → §3 N5 restated, §8.2.4 pins only the correction. M2 pinned three-map
numbers → §8.2.4 hand-built ±30 hulls. M3 anchor endpoints → §5.1 endpoint-kind rule.
M4 budget → §8.4 method. M5 obstacle grid omits discs → §5.3. M6 unreachable cap → §5.3
structural bound. M7 placement area vs painted surface → §5.3. m1–m6 → offset attribution,
`previous` retention, 7 pt face excluded, projection baseline, prominence fixture,
emphatic terminal behaviour.

**Attempt 4 (2B/7M/9m).**
| finding | resolution |
|---|---|
| **BLOCKER 1** verification not closed over all publication paths | §5.2 table enumerates all six frame factories, requires a residual on each, routes `fallbackPositions` through the projection, and §8.2.2 tests a failure frame |
| **BLOCKER 2** bound mixed world radius with screen pixels | §5.3 states `max(2, r·zoom) + 30 + halfBox`, states the per-slot width limits, and §8.3.6 asserts at zoom 1 and 2 |
| MAJOR 1 §8.2.4 not falsifiable, hull effect not exercised | §8.2.4 asserts exact positions and overlap 10 for the correction only, and makes no post-projection hull claim (N5) |
| MAJOR 2 `R(k)`/`max_r` undefined for `k = 0` | §5.1: `R(k) = 0` for `k ≤ 1`, `max_r` defined per anchor |
| MAJOR 3 pan vs painted surface contradiction | §5.3/§5.4 pan is an input and pan invariance is dropped; §8.3.7 tests viewport-change recomputation |
| MAJOR 4 pinned-pinned undefined; "guaranteed" overstated | §4/§5.2/§7: pinned-pinned never moved, counted in the residual; G1 reworded |
| MAJOR 5 unnamed constants | §5.2 `MAX_PASSES = 64`, `RELAXATION = 0.5` proposed; §8.1.6 states the sandwich outcome in terms of them; §12 pins them |
| MAJOR 6 font alone cannot reproduce the rectangle | §5.3 pins the measurement source (painter FRC/transform, scaled by zoom) and §8.3.1 asserts ink at 0.25/2.0 |
| MAJOR 7 unmeasurable budget | §8.4 defines the method, population and baseline; the threshold moves to §12 |
| m1 citations | corrected throughout (perf report `:185`/`:191`, `GraphGeometryEngine.java:212`, `TypedSpringBox.java:91`, `GraphStreamLayoutEngine.java:295`, `LayoutSettleLoop.java:544`, `LabelAssembler` `:982`) |
| m2 emphatic exception has no frame to record into | §4 I3/§7 record it on the placement result |
| m3 red-phase alternative weakens the fix | §8.1.1 uses only the edge-constructed fixture |
| m4 "≈110 px" not derivable | §5.3 states the slot width limits and the exact bound |
| m5 unreproducible sweep minima | §5.2 deletes the numbers and states the hazard qualitatively |
| m6 unnamed idle baseline | §8.5 names `LayoutSettleLoopShould` and records the baseline in that test |
| m7 "free slot" predicate undefined | §8.3.3 defines the predicate |
| m8 single-line box assumption | §4 I2 states labels are single-line |
| m9 `p95 ≤ 20 ms and ≤ the pass cap` unit mix | replaced by §8.1.5 (pass count) and §12 (threshold) |

## 12. Specification inputs (deliberately not asserted here)

The specification pins these with exact values, fixtures and thresholds. `MIN_GAP`,
`MAX_PASSES` and `RELAXATION` are already proposed in §4 and §5.2, so the specification
confirms or replaces them against measurement rather than inventing them; the rest are
genuinely unmeasured here:

- `MIN_GAP`, `MAX_PASSES`, `RELAXATION` and the projection's p95 budget.
- The placement stage's p95 threshold, sampling population, warm-up and cache states, with
  the pre-change baseline recorded first.
- Slot width limits, gap and displaced offset if they differ from §5.3's design parameters.
- The two-pinned sandwich fixture coordinates and the solvable convergence fixture.
- The long-label truncation fixture and the 1 px stickiness fixture coordinates.
- Idle and performance baselines re-recorded after the change.
