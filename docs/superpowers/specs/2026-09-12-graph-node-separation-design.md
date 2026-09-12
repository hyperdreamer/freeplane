# Graph Workspace Node Separation — Design

- Date: 2026-09-12 (revision 2, supersedes revision 1 at commit `3f2565b258`)
- Topic: graph-node-separation
- PM run: `pm-run-20260912-012815-01038a52`
- Delivery target: `refs/heads/plugin/graph-workspace` at `/data/home/guest/Development/freeplane`
- Integration branch: `pm/graph-node-separation/run-pm-run-20260912-012815-01038a52`
- Evidence: `docs/superpowers/specs/mockups/2026-09-12-node-separation/` (six PNG panels plus the generator that produced them)
- Review history: revision 1 was reviewed by an independent frontier reviewer and returned
  **1 blocker, 5 majors, 6 minors** (`$STATE_ROOT/reports/design-review-attempt-1.md`). Revision 2
  resolves every finding; §11 maps each finding to its resolution.

## Revision 2 changelog

1. Settled equilibrium corrected to `24.902994` (measured, not the rest length 24); panel 01 regenerated
   from that value. (was MAJOR 2)
2. Projection moved **before** hull computation and the tier correction; the cross-map guarantee is now
   carried by a widened tier-correction gap instead of an unjustified ordering claim. (was MAJOR 1)
3. Seeds share the disc-derived ring, so the seed and the spring genuinely agree. (was MAJOR 3)
4. Enclosure labels: hull growth removed entirely; the label is placed inside when it fits and outside
   with a leader otherwise. `LabelPlacementEngine` / `GraphGeometry.labels()` are deleted. (was MAJOR 4,
   user decision 2026-09-12: labels never drive geometry)
5. Placement interface now takes forced state and rendering level, is measured with the painter's real
   fonts, and the painter loses its independent forced-label bypass. (was BLOCKER 1)
6. Test strategy rewritten around red-phase preconditions, numeric bounds, a fixture that reaches the
   truncation rung, and painted-ink verification. (was MAJOR 5)
7. `MAX_LABEL_OFFSET_PX` deleted: displacement is bounded by construction.
8. `MIN_GAP` pinned; G4 narrowed; result type, cache owner, citations and mockup notes corrected.
   (were MINOR 1-6)

## 1. Problem

Nodes inside an enclosure are placed too close to one another and sometimes overlap
visually. Reported from the workspace canvas: three cramped nodes in
"Basic Definitions and Theorems" with colliding text, and two nodes behind the
"Axioms" enclosure with a label drawn across a disc.

Investigation separates two independent defects that the screenshot merges.

### 1.1 The disc invariant is violated by construction

`TypedSpringBox.REST_LENGTH = 24.0` (`TypedSpringBox.java:18`) is used for **both**
relationship links (node↔node) and containment links (node→enclosure anchor), at
exactly `GraphStreamLayoutEngine.java:230` and `:239` and nowhere else.

A node's rendered radius is `GraphStreamLayoutEngine.NODE_RADIUS = 8.0` (`:40`)
times its prominence scale, capped at `NodeProminence.MAX_SCALE = 1.75`
(`NodeProminence.java:6,36`), so a prominent node renders at radius 14.
`TypedNodeParticle.scaleRepulsion` (`:84-89`) additionally scales the repulsion a
node receives by `separationRadius / 8`, so the two-body equilibrium is not the
rest length. Solving `0.05·(d − 24) = 16·1.75 / d²` gives **d = 24.902994**; an
independent reflection probe against the shipped
`BIN/plugins/org.freeplane.plugin.graph/lib/plugin-1.13.4.jar` measured
**24.90299406686856**. Two connected prominent nodes therefore overlap by
**3.097** against the disc sum of 28, and fall **9.097 short** of the invariant
floor of 34 used in this design.

No rule anywhere in the layout prevents disc overlap. GraphStream's repulsion is a
soft `K2·w/d²` force (`SpringBoxNodeParticle.repulsionN2`, bytecode-verified:
skipped entirely when `d == 0`; `K2 = REPULSION_FACTOR = 16.0`,
`TypedSpringBox.java:20,36`), and the only overlap correction in the pipeline
(`HullIntersection.minimumSeparatingTranslation` via `MapTierCorrection`)
translates whole maps uniformly, so it cannot repair intra-map crowding.

### 1.2 Text collision is the larger visible defect and is not a layout problem

Node labels are painted by `GraphPainter.paintLabels` at a fixed slot centred
above the disc (`y = center.y − radius − 8/zoom`) with **no collision avoidance at
any rendering level**.

Enclosure labels go through `LabelPlacementEngine`, but the review corrected the
description of what that engine guarantees:

- Its only obstacle set is the enclosure labels already placed — `collides()` is
  called with the placed-label list (`LabelPlacementEngine.java:134, 157, 276`).
  There is **no disc obstacle at any zoom**; the engine works on hulls and label
  rectangles. Node labels are not considered at all.
- `interior()` may return an **expanded hull**, capped at
  `MAX_INTERIOR_EXPANSION = 8.0` (`:22, 162-196`), and every other candidate is an
  arc or exterior slot on the same hull.

So today's enclosure labelling keeps its label inside (or beside) a possibly
grown boundary, while node text collides with everything. The latent
zoom-under-reserve defect is real but separate: the engine measures with a fixed
12 pt `Dialog` box in the zoom-independent pipeline
(`LayoutSettleLoop.defaultMetrics`, `:960-962`) while the painter draws
`size/zoom` world units (`GraphPainter.java:296-298`), so reserved space is too
small below 100 % zoom.

Two structural facts make label collision a screen-space problem:

1. Labels are painted at constant **screen** size while discs scale with zoom.
   Disc non-overlap (`D ≥ r_i + r_j`) is zoom-invariant; label non-overlap
   (`D·zoom ≥ labelWidth`) is not.
2. Therefore a world-space invariant cannot express "text never overlaps".

### 1.3 Prior art checked, and rejected

Obsidian's graph view documents node size as a function of incoming references
(never label length) and a **text fade threshold** that fades labels by zoom; the
Obsidian team explicitly declined label collision detection on the forum. Its
`Repel force` / `Link distance` are global sliders with no per-node radius or
label awareness. Freeplane's node name *is* the node's content, so fading it is a
poor fit — but the Obsidian evidence settles *where* collision must be solved: in
screen space.

## 2. Goals

- **G1** Rendered node discs never overlap: centre distance ≥ `r_i + r_j + MIN_GAP`
  on every published layout frame, for any combination of pins.
- **G2** Node text never overlaps other node text, enclosure text, or any disc, at
  the zoom and viewport being rendered.
- **G3** When the viewport cannot hold every label, degradation is explicit,
  ordered, and deterministic — never a silent pile-up.
- **G4** *Narrowed in revision 2.* The **label placement result must not influence
  geometry**: changing zoom, viewport, forced state, or placement outcome leaves
  disc positions and hull polygons byte-identical. (Revision 1 claimed "label
  width must not drive geometry", which was already false: `BoundarySizes`
  remains label-aware for anchor sizing — see N4.)
- **G5** No persisted-format change: positions, pins, and workspace XML are
  untouched.

## 3. Non-goals

- **N1** No change to the repulsion constant `K2`, the force solver, or the
  settle/idle policy. A repulsion increase cannot guarantee G1, and per-frame force
  manipulation previously caused the CPU-burning settle defect.
- **N2** No label-aware layout expansion: reserving full label boxes in the physics
  grows a map like ZFC by roughly 2–3×, couples AWT text metrics into the solver,
  and still fails at low zoom because text is screen-constant.
- **N3** Zoom-driven label fading (Obsidian's mechanism) is not introduced.
- **N4** `BoundarySizes` stays label-aware for anchor sizing and top-tier ring
  placement. It over-reserves space and is harmless; changing it would churn the
  boundary fixtures for no user-visible gain. This is why G4 is scoped to the
  placement result rather than to label text.

## 4. Contract

- **I1 — disc separation (world space, every published frame).**
  For every pair of projected nodes `i ≠ j`: `distance(c_i, c_j) ≥ r_i + r_j + MIN_GAP`.
  `MIN_GAP = 6.0` world units, declared once in production
  (`org.freeplane.plugin.graph.layout.NodeSeparation.MIN_GAP`) so the projection,
  the springs and the tests cannot diverge. Rationale: two 8-unit-radius discs
  (the minimum rendered size) stay visibly distinct without making the layout
  noticeably looser than today; 6 also exceeds the 2-unit cross-map margin derived
  in §5.2.
  **Exception:** two *pinned* nodes may overlap, because a pin is explicit user
  intent and the layout never moves a pinned particle. A pinned node never moves;
  its neighbours are displaced instead.
- **I2 — text separation (screen space, per rendered frame).**
  Every drawn label rectangle is disjoint from every other drawn label rectangle
  and from every disc rectangle. Placement is recomputed per zoom and viewport.
  Rectangles are computed from the **painter's own font resolver**, so reserved
  space equals painted ink for every font the painter can choose, including 15 pt
  bold emphatic and 7 pt over-target (`GraphTheme.java:67-70`).
- **I3 — degradation ladder (ordered).**
  `full@painterFont → full@displaced → denseFont → displaced denseFont →
  truncated → hover-only`. Forced labels (selected, hovered, related) and
  emphatic enclosure labels are placed first and are never drawn below full text;
  they are inputs to placement, not a paint-time override.
- **I4 — stability.**
  Placement is a deterministic function of the request (positions, zoom, viewport,
  forced set, rendering level) plus the previous assignment. A label keeps its
  previous slot while that slot remains collision-free, so settling and dragging
  do not make labels jump between slots.

## 5. Architecture

```
physics particles  (GraphStream)
      │
      ▼ raw LayoutPositions
NodeSeparationProjection   NEW §5.2: per-node push-apart to satisfy I1   (world space)
      │
      ▼ NodeSeparationResult (positions + residual count)
GraphGeometryEngine        discs + hulls, from the PROJECTED positions   (existing)
      │
      ▼
MapTierCorrection          whole-map translation; required hull gap widened by MIN_GAP  (existing, adjusted)
      │
      ▼ published LayoutPositions + GraphGeometry
ScreenLabelPlacement       NEW §5.3: node AND enclosure labels, one obstacle set  (screen space)
      │
      ▼
GraphPainter               draws exactly the placed labels                (existing, simplified)
```

### 5.1 Springs that agree with the invariant

`NodeSeparationProjection` guarantees I1, but the solver should already be close
to satisfying it, otherwise the projection does corrective work every frame and
published positions inherit the solver's oscillation. Three changes, one
execution path, no fallbacks (per repository policy):

- **Relationship links:** rest length becomes `r_i + r_j + MIN_GAP`, the same
  quantity I1 requires. This removes the 24 < 28 defect at its source.
- **Containment links:** rest length becomes a disc-derived ring radius sized so
  that `k` direct children on a ring of radius `R` satisfy I1 between neighbours:
  `R(k) = (2·max_r + MIN_GAP) / (2·sin(π/k))` for `k ≥ 2`, `R = 0` for `k = 1`.
  Verified: adjacent regular-k-gon chord is `2R·sin(π/k)`, so the inequality holds
  by construction. `k = 1` is safe (the typed-attraction fallback at
  `TypedSpringBox.java:99-103` handles `d == 0`, repulsion is skipped there, and
  the spring at `d > restLength` pulls the child off the anchor, so the child does
  not remain coincident).
- **Seeds:** `Seeds.nodePosition` (`GraphStreamLayoutEngine.java:682`) currently
  uses the **label-aware** `directNodeRingRadius` (formula `:519-540`), which for
  "Axiom of Choice" is ≈134 against the new `R(2) = 17` — a 5–7× inward transient
  on every map open. Revision 2 changes the seed to call the *same disc-derived
  helper* as the spring, so seed and spring agree by construction. This is the
  only change in this design that is made for solver-trajectory reasons rather
  than for an invariant.

`REST_LENGTH` becomes unused and is deleted. `LayoutCalibration` multipliers are
unchanged, and hierarchy links between anchors keep
`GROUP_SPACING` / `SUB_GROUP_SPACING`.

Accepted consequences (§10): settled positions change, so seeds, step counts and
fixture expectations move.

### 5.2 `NodeSeparationProjection` (new, world space)

Pure function; no history, no randomisation:

```java
final class NodeSeparationResult {
    LayoutPositions positions;
    int residualViolations;      // 0 for every fixture; observable, never silent
}

NodeSeparationResult project(GraphProjection projection, LayoutPositions raw,
                             Set<ProjectedNodeKey> pinned);
```

- Deterministic pairwise relaxation in a fixed order (nodes sorted by projected
  key); a violating pair is separated by half the penetration each. A pinned node
  absorbs none of the displacement and its partner takes all of it.
- Uniform spatial hash, so cost is `O(n + k·pairs)` rather than `O(n²)`; bounded
  passes with early exit when no violation remains.
- **Non-finite positions are rejected** with `IllegalArgumentException`. (Revision
  1 cited `GraphGeometryEngine` for this guard style, which was wrong — that class
  validates coverage and keys; finiteness guards live in
  `GraphStreamLayoutEngine.frame` and `PerceptualIdlePolicy.validateFinite`.)
- **Ordering (changed in revision 2).** The projection runs **before**
  `GraphGeometryEngine` computes hulls and before `MapTierCorrection`, so the tier
  correction sees the final map shapes and cannot be invalidated by later hull
  growth. This protects the existing sibling-hull contract that
  `BoundarySeparationShould` establishes and which `LayoutSettleLoop:543` and
  `GraphPainter:99` draw.
- **Cross-map pairs.** A uniform translation preserves intra-map distances but can
  reduce cross-map ones, and revision 1's claim that the hull clearance made this
  impossible was wrong: discs sit `HULL_CLEARANCE = 16` inside their hulls
  (`GraphGeometryEngine.java:23`), so touching hulls leave facing discs 32 apart
  while I1 can require `2·14 + 6 = 34`. The fix is in the tier correction, not in
  node positions: `MapTierCorrection` now separates hulls by at least `MIN_GAP`
  instead of merely making them touch, so cross-map discs are at least
  `2·16 + 6 = 38` apart. Guarantee: `38 ≥ 34`.
- The projection holds no per-frame state, so a correction is never fed back into
  the solver and cannot re-introduce the oscillation the settle work removed.

`LayoutFrame` gains the residual count alongside `conflicts` and `idle`, so the
diagnostic in §7 has somewhere to live.

### 5.3 `ScreenLabelPlacement` (new, screen space)

Pure function of an explicit request plus the previous assignment:

```java
final class LabelPlacementRequest {
    GraphProjection projection;
    GraphGeometry geometry;
    double zoom;
    Rectangle2D screenViewport;
    Set<ProjectedEndpointKey> forced;   // selected, hovered, related
    RenderingLevel renderingLevel;      // FULL | DENSE | OVER_TARGET
}

List<PlacedLabel> place(LabelPlacementRequest request, List<PlacedLabel> previous, LabelFonts fonts);
```

Changes from revision 1, all required by the review:

- **Forced state and rendering level are inputs.** Revision 1's interface had
  neither, so it could not honour I3 and its cache could not invalidate on hover
  or selection.
- **`LabelFonts` is the painter's font resolver, extracted and shared.**
  `GraphPainter.labelFont(...)` and the theme's four faces (12 SansSerif, 9 dense,
  7 over-target, 15 bold emphatic) become one function used by painter and
  placement alike. Revision 1's "12 pt/9 pt" claim silently mis-measured emphatic
  and over-target labels.
- **The painter loses its independent forced bypass** for node labels
  (`GraphPainter.java:301-305` keeps only the enclosure `required` case, which
  becomes a placement input). Forced labels are placed and reserved first, so
  hovering can never paint text into unreserved space.
- **No hull growth.** The enclosure label is placed by the same pass as node
  labels; `LabelPlacementEngine`, `LabelPlacement`, `GraphGeometry.labels()`,
  `HullGeometry` expansion and `MAX_INTERIOR_EXPANSION` are deleted.
  `GraphPainter:262` is the only production consumer, so the removal surface is
  that call site plus nine test files (§8.4).
- **Priority order:** forced labels (selected → hovered → related) → enclosure
  labels (emphatic → subtle) → node labels by descending disc radius. Boundary
  captions are reserved before node text deliberately: a caption that migrates
  around the hull is more disorienting than a node label that moves, and captions
  carry grouping meaning. (The mockups already used this order; revision 1's text
  contradicted them.)
- **Enclosure label slots:** interior at the hull label anchor when the screen
  rectangle fits inside the hull inset by `BOUNDARY_PADDING`; otherwise arc slots
  along the longest hull edges just inside; otherwise exterior slots just outside
  an edge with a leader line; otherwise hover-only — except emphatic enclosure
  labels, which are required and always drawn. `LabelPlacement.Mode`
  (INTERIOR/ARC/EXTERIOR/HOVER_ONLY) survives as the placement result's mode.
- **Node label slots:** eight near slots (above, below, left, right, four
  diagonals) at a 6 px gap from the disc, then four displaced slots at 30 px plus
  half the label box. All offsets are **screen-space constants**, so the maximum
  offset is bounded by construction (≈52 px for the largest label). The revision-1
  `MAX_LABEL_OFFSET_PX = 90` rule is deleted as unreachable.
- **Leader lines** are drawn for every slot other than directly above/below the
  disc.

### 5.4 Painting and caching

- `GraphPainter.paintLabels` draws exactly the placement list: text, leader lines,
  hover-only markers. No second decision path.
- **Cache owner:** the canvas paint layer owns a `ScreenLabelPlacementCache`
  (zoom and pan are paint-time; `CanvasState` is immutable and published at layout
  time). Key = (projection generation, positions identity, zoom, screen viewport
  size, forced-set digest, rendering level). Any component change invalidates; a
  layout publication invalidates positions. Revision 1's key omitted `previous`,
  forced state and rendering level.
- **Purity vs stickiness (clarified):** `place` is deterministic in
  `(request, previous)`. The engine is therefore *stateful across calls* through
  the cache, and that state is exactly one previous assignment. Tests pin both
  halves: determinism for a fixed `previous`, and stickiness across a small
  position change.
- Accessibility, search and tooltips read `ProjectedEnclosure.labels()` and node
  labels from the projection (`AccessibleGraphCanvas.java:494`,
  `GraphSearchModel.java:61`, `ContributorInspector.java:300`) and are unaffected;
  verified in review.

## 6. Degradation rule and its measured limits

Measured with the generator over viewport sizes from 1128×364 down to 200×130 on a
12-node fixture, with the enclosure label **in the obstacle set** (revision 1's
probe omitted it):

| viewport | result | collisions |
|---|---|---|
| 1128×364 … 420×240 | full 11, hidden 1 | 0 / 0 |
| 280×170 | full 7, dense font 4, hidden 1 | 0 / 0 |
| 200×130 | full 2, dense font 4, hidden 6 | 0 / 0 |

`truncated` was **0 in every run**, and displacement-first versus
truncation-first ladders produced **identical** placements at every size: when a
slot is blocked it is blocked by occupancy, not by the label being too wide, so
shortening text does not create space. Probing hidden labels for a genuinely free
slot that a minimal truncated label could occupy returned 0 salvageable labels at
every realistic size and 1 of 6 only at 200×130.

Recorded so it is not "fixed" later by mistake:

- Truncation is the terminal rung before hover-only and is expected to fire
  rarely; it is retained because the user's rule is "show text rather than hide
  it", and §8.3 pins it with a purpose-built cramped fixture.
- Displacement carries the load: mean label offset 47 px, leader crossings 0 on the
  12-node fixture.
- Hover-only marks hidden labels with a dashed ring, so the state is visible.

## 7. Error handling

- **Degenerate coincident particles:** displacement along a deterministic axis
  derived from the projected key order (never `NaN`); a coincident label/disc pair
  counts as a collision.
- **Pin-versus-pin overlap:** accepted and documented; the layout cannot satisfy
  user intent and the invariant simultaneously, and pins are restored from the
  workspace file.
- **Unsolvable density:** after the bounded passes the residual count is reported
  through `NodeSeparationResult.residualViolations` and the frame field. The frame
  is still published (never dropped) and the failure is observable in tests and
  logs, rather than silently publishing overlapping discs.
- **Non-finite geometry:** rejected at the projection boundary (§5.2).

## 8. Test strategy

Every test below states the mechanism it can falsify. Red-phase preconditions are
mandatory: a test that would pass against a no-op implementation is not accepted.

### 8.1 `NodeSeparationProjectionShould` (new, pure function)

1. **Red phase, then green.** On the math-notebook fixture,
   `assertThat(violationCount(raw)).isPositive()` before projecting (the two
   connected prominent nodes settle at 24.903 against a floor of 34), then
   `assertThat(violationCount(result.positions)).isZero()`.
2. Half/half displacement for a movable pair; with one node pinned only the
   partner moves, and the pinned coordinate is unchanged bit-for-bit.
3. Already-satisfied positions are returned unchanged — paired with test 1 so it
   cannot pass vacuously.
4. Coincident particles separate deterministically; no `NaN`; repeated runs are
   byte-identical, and iteration order does not change the result.
5. **Numeric cost bound:** on a 2000-node / 5000-edge fixture the projection
   completes within a recorded bound (target ≤ 8 ms p99, ≤ 4 passes) with
   headroom over the measured value; the bound is a constant in the test, not a
   description. `PerformanceTripwiresShould` gains the same bound.
6. Residual path: a synthetic over-dense fixture reports
   `residualViolations > 0` and still returns finite positions.

### 8.2 Frame-pipeline tests (falsifies the wiring and the ordering)

1. **Per-frame I1:** drive the math-notebook fixture through a settle loop and
   assert I1 for **every published frame**, not only the settled one.
2. **Ordering:** recompute hulls from the published positions and assert sibling
   root hulls remain separated; the fixture must include a frame where the
   projection displaces at least one node, asserted explicitly, otherwise the test
   is vacuous.
3. Omitted-fix guard: with the projection disabled the per-frame test must fail —
   asserted by a local toggle in the test fixture.
4. `MapTierCorrectionShould` gains a case asserting the widened `MIN_GAP` hull gap.

### 8.3 `ScreenLabelPlacementShould` (new, screen space)

1. **Painted-ink I2.** Render each label alone into a transparent layer using the
   painter's font resolver, then assert pairwise bitmap intersection is empty and
   that label bitmaps do not intersect disc bitmaps. This measures ink, not the
   engine's own rectangles, so it catches the 15 pt bold / 7 pt over-target
   mismatch that revision 1 would have missed.
2. Zoom sweep {0.25, 0.5, 1.0, 2.0} × rendering level {FULL, DENSE, OVER_TARGET},
   including at least one emphatic enclosure: no intersections at any combination.
3. **Ladder order and reachability.** On the cramped 200×130 fixture assert at
   least one truncated and at least one hover-only label, and assert that no label
   is truncated while a full-text slot was free; on the roomy fixture assert no
   truncation.
4. Forced labels: selected/hovered/related labels are present, full, placed
   before others, and re-placing on hover never produces an intersection. Includes
   the case that broke revision 1: a hover-only label becoming forced.
5. Stickiness: a 1 px position change preserves every assignment; a label whose
   slot becomes invalid is re-placed and never oscillates while its slot stays
   valid.
6. Displacement bound: every node label lies within the maximum slot offset
   (≈52 px) of its disc.
7. **Geometry independence (G4):** changing zoom, viewport, or the forced set
   leaves disc positions and hull polygons identical.
8. Enclosure label: interior when it fits, exterior with a leader when it does
   not, emphatic never hover-only, and hull polygons are identical either way
   (labels never grow hulls).

### 8.4 Regression

Full module suite. Particular attention to the nine test files that consume label
placements or hull expansion and must be migrated: `LabelPlacementShould`
(replaced by 8.3), `GraphCanvasPaintShould`, `GraphInteractionControllerShould`,
`GraphWorkspaceModelAcceptanceShould`, `ReferenceRepulsionFixture`,
`GraphWorkspacePerformanceDiagnostic`, `GroupOnlyProjectionShould`,
`ProjectionDeterminismShould`, `StructuralProjectionShould`; plus
`BoundarySeparationShould`, `HullIntersectionShould`, `TypedForcesShould`,
`MapTierCorrectionShould`, `PerceptualIdlePolicyShould`, `LayoutWorkerShould` and
the UI-evidence harness. Settle/idle must not regress: the two-map fixture's idle
frame count is compared against a recorded baseline.

## 9. Mockups

`docs/superpowers/specs/mockups/2026-09-12-node-separation/` — produced by the
generator in the same directory using production constants and AWT metrics; the
"proposed" panels are output of the described algorithms, not hand-drawn.

| panel | today | proposed |
|---|---|---|
| 01-disc-invariant | settled centres 24.90, overlap 3.10, I1 shortfall 9.10 | centres 34.00 → 0 overlaps |
| 02-sparse | 2 label/label collisions, discs 46 and 60 apart | 0 collisions, 3 full labels |
| 03-dense | 13 label/label + 17 label/disc pairs | 0 collisions, 11 full, 1 hover-only |
| 04-zoom | — | 11 of 12 placed at 100 %, 8 at 50 % |
| 05-cramped-window | 13 + 17 collisions | 0 collisions |
| 06-ladder-truncate-vs-displace | — | (a) and (b) identical at every viewport size |

**Honesty notes (from review).** These are a *simulation*, not the production
renderer; the generator models the fixed-slot painter and the proposed algorithms.
Panel 01 previously drew the rest-length configuration (24) and is regenerated
from the solved equilibrium (24.90299407, matching the measured 24.90299406686856);
its revision-1 caption mixed the disc-sum threshold (28) with the I1 floor (34) and
now reports both. Panels 02/03/05 use synthetic scenes and their captions say so.
The §6 probe previously omitted the enclosure obstacle and printed no collision
count; it now includes both and reports 0/0 collisions.

## 10. Risks

- **Fixture churn is larger than revision 1 admitted.** Disc-derived rest lengths
  *and* disc-derived seeds change settled positions, step counts, position
  snapshots and performance hashes. `ReferenceRepulsionFixture` and
  `GraphWorkspacePerformanceDiagnostic` expectations will move, and the
  `PerformanceTripwiresShould` fixture hashes must be re-recorded deliberately,
  not silently.
- **Hull shrinking.** Removing interior expansion shrinks boundaries by up to
  8 units where a label previously forced growth. Fixtures asserting expanded
  hulls change; this is the accepted cost of the user decision that labels never
  drive geometry.
- **Larger removal surface.** Deleting `LabelPlacementEngine` touches nine test
  files. The alternative (keeping two placement paths in two coordinate spaces)
  was rejected as worse.
- **Projection/solver interaction.** The projection is stateless, so if the solver
  oscillates, published positions oscillate with it; the per-frame I1 test and the
  idle baseline guard this.
- **Two rungs that rarely fire.** Truncation and, at roomy zoom, hover-only are
  near-dead (§6). They are kept deliberately so the user rule "show text rather
  than hide it" holds in cramped windows, and must not be removed without
  re-running the reachability probe.
- **Degenerate single-child containment.** `R = 0` places a lone child on its
  anchor at the seed; the spring then pulls it off the anchor, and the pair is not
  a disc pair, so I1 does not apply and no `NaN` is produced.

## 11. Review findings → resolution

| finding | resolution |
|---|---|
| B1 placement interface (forced state, rendering level, painter fonts, painter bypass) | §5.3 rewritten: `LabelPlacementRequest` carries both; `LabelFonts` shared with the painter; painter bypass deleted; §8.3.1 measures ink; §8.3.4 pins the hover-promotion case |
| M1 ordering vs hull separation | §5.2: projection before hull computation and tier correction; cross-map guarantee moved to a widened tier-correction gap; §8.2.2/8.2.3 falsify it |
| M2 equilibrium misstated | §1.1 corrected to 24.902994 (solved and measured); panel 01 regenerated; both thresholds reported |
| M3 seed/spring disagreement | §5.1: seeds call the same disc-derived helper; churn accepted in §10 |
| M4 enclosure labels misdescribed; hull growth deleted | §1.2 corrected; user decision: hull growth removed; §5.3 defines the enclosure slot model; §8.3.8 pins it |
| M5 non-falsifiable tests | §8 rewritten: red-phase preconditions, numeric bounds, truncation-reachability fixture, painted-ink measurement, geometry-independence test |
| m1 result type vs residual | §5.2 `NodeSeparationResult` + frame field |
| m2 wrong guard citation | §5.2 cites `GraphStreamLayoutEngine.frame` / `PerceptualIdlePolicy.validateFinite` |
| m3 purity vs stickiness, cache key/owner | §5.4: cache owner, full key, explicit stateful contract |
| m4 MIN_GAP undefined | §4: `MIN_GAP = 6.0` world units, single production constant, rationale |
| m5 G4 already false | §2 G4 narrowed to the placement result; N4 documents the exception |
| m6 probe omitted the enclosure obstacle; panel priority contradicted the doc | §6 table regenerated with the obstacle and collision counts; §5.3 priority rule aligned with the mockups |
