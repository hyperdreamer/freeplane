# Graph Workspace Node Separation — Design

- Date: 2026-09-12 (revision 3)
- Topic: graph-node-separation
- PM run: `pm-run-20260912-012815-01038a52`
- Delivery target: `refs/heads/plugin/graph-workspace` at `/data/home/guest/Development/freeplane`
- Integration branch: `pm/graph-node-separation/run-pm-run-20260912-012815-01038a52`
- Evidence: `docs/superpowers/specs/mockups/2026-09-12-node-separation/` (seven PNG panels plus the generator that produces them)
- Review history: revision 1 → 1 blocker / 5 majors / 6 minors (`reports/design-review-attempt-1.md`);
  revision 2 → 1 blocker / 6 majors / 6 minors (`reports/design-review-attempt-2.md`). Revision 3 resolves
  the attempt-2 findings; §11 maps every finding from both attempts to its resolution.

## Revision 3 changelog

1. **Cross-map guarantee rebuilt.** Revision 2's arithmetic was a unit error (hull clearance is measured
   from the disc *edge*), and its proposed fix — widening the tier-correction gap — did not address the
   real hazard, which is pairwise half-delta summing in `MapTierCorrection`. I1 is now enforced **after**
   the correction by a final verification pass, and the multi-map hull defect is explicitly out of scope.
   (was BLOCKER 1)
2. The committed generator and its PNGs are regenerated from a single source, with a reproducibility check
   (regenerate outside the repository and byte-compare). Panel 01 was previously not reproducible from the
   committed generator. (was MAJOR 1)
3. The red-phase fixture is named and buildable: two radius-14 prominent nodes or literal raw positions at
   24.902994. The math-notebook fixture cannot be red — it has one connector, so no node is prominent.
   (was MAJOR 2)
4. The truncation fixture is named and measured: six 40–49-character labels, 500×300 viewport → 5 truncated.
   (was MAJOR 3)
5. The displacement bound is corrected: structural maximum ≈109 px, measured maximum 78.7 px, with an
   explicit 90 px cap that is now demonstrably reachable. (was MAJOR 4)
6. Placement gains an obstacle index, a numeric budget, and a performance stage. (was MAJOR 5)
7. Placement is pan-invariant: the placement area is derived from graph bounds, not from the window, so pan
   neither invalidates the cache nor enters the key; the painter clips. The key gains font/theme identity
   and an explicit single-entry `previous`. (was MAJOR 6)
8. Test-file attribution corrected; `LabelPlacement.Mode` relocation stated; the k=1 mechanism sentence
   corrected; I1 scoped to frames with no residual; the geometry-independence test turned into a paint-path
   wiring assertion. (were m1–m6)

## 1. Problem

Nodes inside an enclosure are placed too close to one another and sometimes overlap
visually. Reported from the workspace canvas: three cramped nodes in
"Basic Definitions and Theorems" with colliding text, and two nodes behind the
"Axioms" enclosure with a label drawn across a disc.

### 1.1 The disc invariant is violated by construction

`TypedSpringBox.REST_LENGTH = 24.0` (`TypedSpringBox.java:18`) is used for **both**
relationship links (node↔node) and containment links (node→enclosure anchor), at
exactly `GraphStreamLayoutEngine.java:230` and `:239` and nowhere else.

A node's rendered radius is `GraphStreamLayoutEngine.NODE_RADIUS = 8.0` (`:40`)
times its prominence scale, capped at `NodeProminence.MAX_SCALE = 1.75`
(`NodeProminence.java:6,36`), so a prominent node renders at radius 14.
`TypedNodeParticle.scaleRepulsion` (`:84-89`) scales the repulsion a node receives
by `separationRadius / 8`, so the two-body equilibrium is not the rest length:
solving `0.05·(d − 24) = 16·1.75 / d²` gives **d = 24.90299406686857**, and an
independent reflection probe against the shipped `plugin-1.13.4.jar` measured
**24.90299406686856**. Two connected prominent nodes therefore overlap by
**3.097** against the disc sum 28 and fall **9.097 short** of the invariant floor
of 34 used here.

No rule anywhere in the layout prevents disc overlap: GraphStream's repulsion is a
soft `K2·w/d²` force (`SpringBoxNodeParticle.repulsionN2`, bytecode-verified —
skipped entirely when `d == 0`; `K2 = 16.0` at `TypedSpringBox.java:20,36`), and
the only overlap correction in the pipeline translates whole maps uniformly.

### 1.2 Text collision is the larger visible defect and is not a layout problem

Node labels are painted by `GraphPainter.paintLabels` at a fixed slot centred
above the disc (`y = center.y − radius − 8/zoom`) with **no collision avoidance at
any rendering level**.

Enclosure labels go through `LabelPlacementEngine`, and the review established
what that engine actually guarantees:

- Its only obstacle set is the enclosure labels already placed
  (`LabelPlacementEngine.java:134, 157, 276`). There is **no disc obstacle at any
  zoom**, and node labels are not considered.
- `interior()` may return an **expanded hull**, capped at
  `MAX_INTERIOR_EXPANSION = 8.0` (`:22, 162-196`); every other candidate is an arc
  or exterior slot on the same hull.
- It measures with a fixed 12 pt `Dialog` box in the zoom-independent pipeline
  (`LayoutSettleLoop.defaultMetrics`, `:960-962`) while the painter draws
  `size/zoom` world units (`GraphPainter.java:296-298`), so reserved space is too
  small below 100 % zoom.

Two structural facts make label collision a screen-space problem: labels are
painted at constant **screen** size while discs scale with zoom, so disc
non-overlap is zoom-invariant (`D ≥ r_i + r_j`) while label non-overlap
(`D·zoom ≥ labelWidth`) is not; and therefore no world-space invariant can express
"text never overlaps".

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
  on every published frame with no residual violation (§4 I1), for any
  combination of pins.
- **G2** Node text never overlaps other node text, enclosure text, or any disc, at
  the zoom being rendered.
- **G3** When no slot exists, degradation is explicit, ordered, and deterministic.
- **G4** The **label placement result must not influence geometry**: changing zoom,
  forced state, or placement outcome leaves disc positions and hull polygons
  identical. (Revision 1 claimed "label width must not drive geometry", which was
  already false; `BoundarySizes` stays label-aware — see N4.)
- **G5** No persisted-format change: positions, pins, and workspace XML untouched.

## 3. Non-goals

- **N1** No change to `K2`, the solver, or the settle/idle policy. A repulsion
  increase cannot guarantee G1, and per-frame force manipulation previously caused
  the settle defect.
- **N2** No label-aware layout expansion: reserving label boxes in physics grows a
  map like ZFC by roughly 2–3×, couples AWT metrics into the solver, and still
  fails at low zoom because text is screen-constant.
- **N3** No zoom-driven label fading.
- **N4** `BoundarySizes` stays label-aware for anchor sizing and top-tier ring
  placement; it over-reserves and is harmless. This is why G4 is scoped to the
  placement result.
- **N5 — new in revision 3.** `MapTierCorrection`'s pairwise half-delta summing can
  leave sibling root hulls overlapping in ≥3-map configurations (falsifiers in
  §5.2). This is a **pre-existing defect, out of scope**: the design neither fixes
  nor worsens it, and a test pins the current behaviour so it cannot change
  silently. The user-facing rule the design owns is I1 (discs), which does not
  depend on hull geometry.

## 4. Contract

- **I1 — disc separation (world space).**
  For every pair of projected nodes `i ≠ j`: `distance(c_i, c_j) ≥ r_i + r_j + MIN_GAP`
  on every published frame whose `residualViolations == 0` (all fixtures). The
  residual counter exists because density can defeat any bounded pass; when it is
  non-zero the frame is still published and the violation is reported, never
  hidden (§7). `MIN_GAP = 6.0` world units, declared once in production
  (`org.freeplane.plugin.graph.layout.NodeSeparation.MIN_GAP`). Rationale: two
  8-unit-radius discs (the minimum rendered size) stay visibly distinct without
  loosening the layout noticeably; 6 also exceeds the 2-unit margin that any
  projection displacement adds cross-map.
  **Exception:** two *pinned* nodes may overlap; a pin is user intent and the
  layout never moves a pinned particle.
- **I2 — text separation (screen space, per rendered frame).**
  Every drawn label rectangle is disjoint from every other drawn label rectangle
  and from every disc rectangle, at the zoom being rendered. Rectangles come from
  the **painter's own font resolver**, so reserved space equals painted ink for
  every face the painter can select (12 pt, 9 pt dense, 7 pt over-target, 15 pt
  bold emphatic, each derived by `size/zoom` with the painter's clamp).
- **I3 — degradation ladder (ordered).**
  `full@painterFont → full@displaced → denseFont → displaced denseFont →
  truncated → hover-only`. Forced labels (selected, hovered, related) and emphatic
  enclosure labels are placement inputs, placed first, never drawn below full text.
- **I4 — stability.**
  Placement is deterministic in `(request, previous)`; a label keeps its previous
  slot while that slot remains collision-free.

## 5. Architecture

```
physics particles  (GraphStream)
      │
      ▼ raw LayoutPositions
NodeSeparationProjection   §5.2: push-apart to satisfy I1                  (world space)
      │
      ▼ NodeSeparationResult
GraphGeometryEngine        discs + hulls from the projected positions      (existing)
      │
      ▼
MapTierCorrection          whole-map translation, unchanged                (existing)
      │
      ▼
I1 re-verification + final projection   §5.2: nothing may undo I1 after this
      │
      ▼ published LayoutPositions + GraphGeometry (hulls recomputed by LayoutSettleLoop:543)
ScreenLabelPlacement       §5.3: node AND enclosure labels, one obstacle set (screen space)
      │
      ▼
GraphPainter               draws exactly the placed labels                 (existing, simplified)
```

### 5.1 Springs that agree with the invariant

- **Relationship links:** rest length becomes `r_i + r_j + MIN_GAP` — the quantity
  I1 requires. This removes the 24 < 28 defect at its source.
- **Containment links:** rest length becomes a disc-derived ring radius so that `k`
  direct children on a ring of radius `R` satisfy I1 between neighbours:
  `R(k) = (2·max_r + MIN_GAP) / (2·sin(π/k))` for `k ≥ 2`, `R = 0` for `k = 1`.
  Verified: the adjacent chord of a regular k-gon is `2R·sin(π/k)`.
  For `k = 1` the child converges **onto** the anchor — the spring with
  `restLength = 0` pulls an off-centre child inward
  (`TypedSpringBox.addTypedAttraction`, `:96-108`) and the zero-distance fallback
  is the only separator; the pair is not a disc pair, so I1 does not apply and no
  `NaN` is produced. (Revision 2 stated this mechanism backwards.)
- **Seeds:** `Seeds.nodePosition` (`GraphStreamLayoutEngine.java:670-687`, currently
  `sizes.directNodeRingRadius(parentKey)` at `:682`) calls the *same disc-derived
  helper* as the spring, so seed and spring agree by construction and the 7.9×
  inward transient between the label-aware seed (≈134 for "Axiom of Choice") and
  `R(2) = 17` disappears. `BoundarySizes.directNodeRingRadius` keeps its label-aware
  formula for `directNodeReach → sizeOf → boundaryRadius` and `topRingPosition`, so
  nothing is orphaned and N4 stays coherent.

`REST_LENGTH` becomes unused and is deleted. `LayoutCalibration` multipliers are
unchanged; anchor-to-anchor hierarchy links keep
`GROUP_SPACING` / `SUB_GROUP_SPACING`. Settled positions change, so seeds, step
counts and fixture expectations move (§10).

### 5.2 `NodeSeparationProjection` (new, world space)

```java
final class NodeSeparationResult { LayoutPositions positions; int residualViolations; }
NodeSeparationResult project(GraphProjection projection, LayoutPositions raw, Set<ProjectedNodeKey> pinned);
```

- Deterministic pairwise relaxation in a fixed order (nodes sorted by projected
  key); a violating pair separates by half the penetration each, a pinned node
  absorbs none.
- Uniform spatial hash, `O(n + k·pairs)`, bounded passes, early exit. Non-finite
  positions are rejected with `IllegalArgumentException` (finiteness guards live in
  `GraphStreamLayoutEngine.java:324,336` and `PerceptualIdlePolicy.java:95`; revision
  1 cited `GraphGeometryEngine`, which was wrong).
- Stateless: a correction is never fed back into the solver.

**Cross-map handling, rebuilt in revision 3.**

Revision 2 claimed touching hulls leave facing discs 32 apart while I1 needs 34, so
the tier correction had to add `MIN_GAP`. That was a **unit error**:
`HULL_CLEARANCE` is measured from the disc **edge** (`GraphGeometryEngine.java:23,
225-233`: support = `n·c + radius`, then `+ HULL_CLEARANCE`), so touching hulls give
a 32-unit *edge* gap where I1 needs a 6-unit edge gap. Hull separation alone is
therefore ample, and widening it fixes nothing.

The real hazard is that `MapTierCorrection` computes independent half-deltas per
pair in one pass and skips zero translations (`MapTierCorrection.java:61-79`), so a
later pair can push a map back toward an earlier one. Falsifiers recorded during
review: maps at `(0,0), (40,0), (−40,0)` are left at `(0,0), (50,0), (−50,0)`, so
hulls A and B still overlap by 10; and a faithful re-implementation over random
6–7 single-node-map configurations whose raw pairs all already satisfied I1 ≥ 34
produced post-correction minimum centre distances of 31.788, 30.742 and 32.659.

Resolution, in keeping with the user's rule that discs must never overlap:

- The projection runs **before** geometry and the tier correction, so the correction
  sees final map shapes.
- After the correction, I1 is **re-verified on the corrected positions** and, if any
  pair violates it (only cross-map pairs can), a **final projection pass** runs on
  those pairs. Nothing downstream may undo I1.
- Hulls are recomputed from the published positions by the existing consumer
  (`LayoutSettleLoop.java:543-548`), so painted hulls stay consistent with the
  positions that are actually drawn.
- The final pass displacement is bounded by the I1 deficit (≤ 2.2 units in the
  reviewer's sweep) and is recorded in the frame's residual counter when the bound
  cannot be met.
- The residual pass is cheap: it is only reached when a violation exists, and one
  sweep of the spatial hash finds all of them.

Hull separation between maps is then as good as one correction pass provides; per
N5 its ≥3-map limitation is pre-existing and pinned by a test rather than fixed.

### 5.3 `ScreenLabelPlacement` (new, screen space)

```java
final class LabelPlacementRequest {
    GraphProjection projection;
    GraphGeometry geometry;
    double zoom;
    Rectangle2D placementArea;              // graph bounds + margin, NOT the window
    Set<ProjectedEndpointKey> forced;       // selected, hovered, related
    RenderingLevel renderingLevel;
}
List<PlacedLabel> place(LabelPlacementRequest request, List<PlacedLabel> previous, LabelFonts fonts);
```

- **Pan-invariant placement area.** The area is derived from the graph's bounds plus
  a margin, in screen space at the current zoom — never from the window rectangle.
  Placement therefore does not change when the user pans; the painter translates by
  the pan delta and clips. This removes an entire class of cache bugs and keeps
  labels from being moved merely because the user scrolled.
- **Forced state and rendering level are inputs**, so I3 is enforceable and the
  cache can invalidate on hover/selection. The painter loses its independent node
  forced bypass (`GraphPainter.java:301-305`); forced labels are reserved by
  placement.
- **`LabelFonts` is the painter's resolver, extracted and shared**:
  `GraphPainter.labelFont` (`GraphPainter.java:289-300`) — emphatic 15 bold, forced
  12, level 12/9/7, then `deriveFont(size/zoom)` with its `max(1.0f, …)` clamp. One
  function reproduces all four faces and the zoom derivation for both painter and
  placement.
- **No hull growth.** Enclosure labels are placed by this pass;
  `LabelPlacementEngine`, `LabelPlacement`, `GraphGeometry.labels()`, hull expansion
  and `MAX_INTERIOR_EXPANSION` are deleted. `LabelPlacement.Mode`
  (INTERIOR/ARC/EXTERIOR/HOVER_ONLY) is **moved onto the new result type** so it can
  survive as the placement mode.
- **Priority:** forced labels (selected → hovered → related) → enclosure labels
  (emphatic → subtle) → node labels by descending disc radius. Boundary captions are
  reserved before node text because a caption migrating around the hull is more
  disorienting than a node label that moves, and captions carry grouping meaning.
- **Enclosure label slots:** interior at the hull label anchor when the screen
  rectangle fits inside the hull inset by `BOUNDARY_PADDING`; otherwise arc slots on
  the longest hull edges just inside; otherwise exterior slots with a leader line;
  otherwise hover-only — except emphatic enclosure labels, which are required.
- **Node label slots:** eight near slots (above, below, left, right, four diagonals)
  at a 6 px gap, then four displaced slots at 30 px plus half the label box, all in
  screen pixels. Candidate offsets are `r + 30 + max(w,h)/2`, so the structural
  maximum is ≈109 px for a 130 px-wide label; `MAX_LABEL_OFFSET_PX = 90` is enforced
  as a candidate rejection, which **does** bind for wide labels in displaced slots
  (measured maximum on the long-label fixture is 78.7 px, so the cap is reachable
  and the test is not vacuous — revision 2's "≈52 px" bound was simply wrong).
- **Obstacle index.** Candidate tests run against a uniform screen-space grid of
  placed rectangles, not a linear list, so a frame costs
  `O(labels × rungs × slots)` plus grid queries.
- Leader lines are drawn for every slot other than directly above/below the disc.

### 5.4 Painting and caching

- The canvas paint layer owns a `ScreenLabelPlacementCache`; `CanvasState` is
  immutable and published at layout time (`LayoutSettleLoop.java:548, 723`), while
  zoom and paint state live in the paint layer (`GraphCanvas.paintComponent`,
  `:358-365`), which is where the cache and its `previous` must live.
- **Key:** projection generation, positions identity, zoom, placement-area geometry,
  forced-set digest, rendering level, and font/theme identity (so a theme or font
  change cannot reuse rectangles measured with the old faces). **Pan is deliberately
  absent** because placement is pan-invariant.
- **Stateful-of-previous contract:** `place` is deterministic in
  `(request, previous)`; the cache holds exactly one previous assignment and is
  invalidated whenever any key component changes.
- Accessibility, search and tooltips read `ProjectedEnclosure.labels()` and node
  labels from the projection (`AccessibleGraphCanvas.java:494`,
  `GraphSearchModel.java:61`, `ContributorInspector.java:300`) and are unaffected.

## 6. Degradation rule and its measured limits

Measured with the committed generator (panel 07 and the reachability probe), all
with the enclosure label in the obstacle set:

| fixture | viewport | result | collisions |
|---|---|---|---|
| 12 nodes, short names | 1128×364 … 420×240 | full 11, hidden 1 | 0 / 0 |
| 12 nodes, short names | 280×170 | full 7, dense 4, hidden 1 | 0 / 0 |
| 12 nodes, short names | 200×130 | full 2, dense 4, hidden 6 | 0 / 0 |
| **6 nodes, 40–49-char names** | **1128×364** | **dense 1, truncated 5** | 0 / 0 |
| **6 nodes, 40–49-char names** | **500×300** | **dense 1, truncated 5** | 0 / 0 |
| **6 nodes, 40–49-char names** | **200×130** | **full 1, truncated 2, hidden 3** | 0 / 0 |

So truncation is reachable, but only with long labels: for short names it fires
**0 times** at every viewport size, and displacement-first versus truncation-first
ladders produce identical placements at 200×130–1128×364. The mechanism is that a
blocked slot is blocked by occupancy, not by label width, so shortening text does
not create space. Recorded so it is not "fixed" later by mistake:

- Truncation is the terminal rung before hover-only; §8.3.3 pins it with the
  measured 40–49-character fixture.
- Displacement carries the load for ordinary names: mean offset 47 px, maximum
  78.7 px, leader crossings 0 on the 12-node fixture.
- Hover-only marks hidden labels with a dashed ring.

## 7. Error handling

- **Coincident particles:** displacement along a deterministic key-derived axis,
  never `NaN`; a coincident label/disc pair counts as a collision.
- **Pin-versus-pin overlap:** accepted and documented; pins are restored from the
  workspace file.
- **Unsolvable density:** after the bounded passes, `residualViolations` is reported
  through `NodeSeparationResult` and a `LayoutFrame` field alongside `conflicts` and
  `idle` (`LayoutFrame.java:16-30`). The frame is still published and the failure is
  observable in tests and logs.
- **Non-finite geometry:** rejected at the projection boundary.

## 8. Test strategy

Each test names the mechanism it can falsify; red-phase preconditions are mandatory
and a test that would pass against a no-op implementation is not accepted.

### 8.1 `NodeSeparationProjectionShould` (pure function)

1. **Red phase.** Build the red fixture explicitly: two nodes with prominence
   scale 1.75 (radius 14) at literal raw positions 24.902994 apart, or a
   purpose-built projection whose two nodes both have prominence 14.
   `assertThat(violationCount(raw)).isPositive()` then
   `assertThat(project(...).residualViolations).isZero()` and
   `violationCount(result.positions) == 0`. The math-notebook fixture **cannot** be
   red — it has one connector (`GraphWorkspaceModelAcceptanceShould.java:821-833`),
   so no node is prominent (radius 8, floor 22, settled ≈25.3).
2. Half/half displacement for a movable pair; with one pinned, only the partner
   moves and the pinned coordinate is unchanged bit-for-bit.
3. Already-satisfied positions unchanged — paired with test 1 so it cannot pass
   vacuously.
4. Coincident particles separate deterministically; no `NaN`; repeated runs
   byte-identical; iteration order does not change the result.
5. **Numeric bound:** 2000-node / 5000-edge fixture, ≤ 8 ms p99 and ≤ 4 passes,
   with headroom over the measured value; `PerformanceTripwiresShould` asserts the
   same bound.
6. Residual path: an over-dense fixture reports `residualViolations > 0` and still
   returns finite positions.

### 8.2 Frame-pipeline tests

1. **Per-frame I1** for every published frame with `residualViolations == 0`, on a
   fixture where the projection is asserted to displace at least one node.
2. **Ordering:** recompute hulls from the published positions and assert sibling
   root hulls remain separated for the existing two-map fixture.
3. Omitted-fix guard: with the projection disabled the per-frame test fails
   (local toggle in the fixture).
4. **Three-map falsifier (N5 pin):** maps at `(0,0), (40,0), (−40,0)` are asserted
   to reproduce today's post-correction geometry (`(0,0), (50,0), (−50,0)`, hulls
   A/B overlapping by 10). This pins the pre-existing defect so the design cannot
   silently change or be credited with fixing it, and documents the boundary of the
   I1 guarantee.

### 8.3 `ScreenLabelPlacementShould`

1. **Painted-ink I2.** Render each label alone into a transparent layer with the
   painter's resolver, then assert pairwise bitmap intersection is empty and label
   bitmaps miss disc bitmaps. This measures ink, catching the 15 pt bold and 7 pt
   over-target faces that rectangle-only checks would miss.
2. Zoom {0.25, 0.5, 1.0, 2.0} × level {FULL, DENSE, OVER_TARGET}, at least one
   emphatic enclosure: no intersections.
3. **Ladder order and reachability.** On the measured long-label fixture
   (six 40–49-character names, 500×300) assert at least one truncated label and that
   no label is truncated while a full-text slot was free; on the short-name fixture
   assert no truncation at 1128×364.
4. Forced labels: present, full, placed first, and re-placing on hover never
   produces an intersection — including a hover-only label becoming forced.
5. Stickiness: a 1 px position change preserves assignments; an invalidated slot is
   re-placed and does not oscillate while valid.
6. Displacement bound: every label within `MAX_LABEL_OFFSET_PX = 90`, with the
   measured maximum (78.7 px) recorded in the test.
7. **Pan invariance (wiring):** translating the viewport by a pan delta leaves the
   placement list identical up to translation, and the painted result after pan
   matches the pre-pan painting translated.
8. Enclosure labels: interior when it fits, exterior with a leader when it does not,
   emphatic never hover-only, and hull polygons identical either way.
9. **Geometry independence as a wiring assertion:** run placement through the real
   paint path and assert the published `CanvasState` geometry is identical before
   and after zoom, forced-set, and placement changes. (Revision 2's phrasing was
   vacuous: no `place` implementation mutates geometry.)

### 8.4 Performance

The placement pass moves into the paint path, so it must be budgeted, not merely
"fast". The reference report already measures the **enclosure-only** label pass at
p95 123,308,455 ns / max 143,041,802 ns
(`docs/superpowers/specs/2026-08-10-graph-workspace-performance-report.md:191`,
`GraphWorkspacePerformanceDiagnostic.java:354-358`). Requirements:

- Obstacle grid by construction (§5.3).
- A placement stage on the 2000-node / 5000-edge workload with a recorded p95
  budget, measured at settle, on zoom, on forced-set change, and on pan (which must
  be a translate-only path, not a re-placement).
- The stage replaces the current label measurement in
  `GraphWorkspacePerformanceDiagnostic`; its baseline is re-recorded deliberately.

### 8.5 Regression

Full module suite. Removal surface: **five** test files reference
`LabelPlacement` / `LabelPlacementEngine` / the 3-arg `GraphGeometry.of` —
`LabelPlacementShould` (replaced by §8.3), `GraphCanvasPaintShould`,
`GraphInteractionControllerShould`, `GraphWorkspaceModelAcceptanceShould`,
`GraphWorkspacePerformanceDiagnostic` — and four more
(`ReferenceRepulsionFixture`, `GroupOnlyProjectionShould`,
`ProjectionDeterminismShould`, `StructuralProjectionShould`) touch
`SafeNodeLabel` / `enclosure.labels()` and are churn-affected only. Production call
sites to migrate are `LayoutSettleLoop.java:543`, `:721` and `LabelAssembler:983`
(the only reader of `GraphGeometry.labels()` is `GraphPainter.java:262`). Settle/idle
must not regress: the two-map fixture's idle frame count is compared against a
recorded baseline.

## 9. Mockups

`docs/superpowers/specs/mockups/2026-09-12-node-separation/` — generator and PNGs
are committed together and regenerating the generator outside the repository
reproduces every committed PNG byte-for-byte.

| panel | today | proposed |
|---|---|---|
| 01-disc-invariant | settled centres 24.90, overlap 3.10, I1 shortfall 9.10 | centres 34.00 → 0 overlaps |
| 02-sparse | 2 label/label collisions, discs 46 and 60 apart | 0 collisions, 3 full labels |
| 03-dense | 13 label/label + 17 label/disc pairs | 0 collisions, 11 full, 1 hover-only |
| 04-zoom | — | 11 of 12 placed at 100 %, 8 at 50 % |
| 05-cramped-window | 13 + 17 collisions | 0 collisions |
| 06-ladder-truncate-vs-displace | — | (a) and (b) identical at every viewport size |
| 07-truncation-reachable | — | long labels: 5 truncated at 500×300, 2 truncated + 3 hidden at 200×130 |

**Honesty notes.** These are a *simulation* of the described algorithms, not the
production renderer. Panel 01 is generated from the solved equilibrium
(24.90299407, matching the measured 24.90299406686856) and reports both the disc-sum
threshold (28) and the I1 floor (34). Panels 02/03/05 use synthetic scenes and say
so. The reachability probe includes the enclosure-label obstacle and prints
collision counts.

## 10. Risks

- **Fixture churn.** Disc-derived rest lengths *and* disc-derived seeds change
  settled positions, step counts, position snapshots and performance hashes.
  `ReferenceRepulsionFixture`, `GraphWorkspacePerformanceDiagnostic` and
  `PerformanceTripwiresShould` expectations move and must be re-recorded
  deliberately.
- **Hull shrinking.** Removing interior expansion shrinks boundaries by up to
  8 units where a label previously forced growth — the accepted cost of the user
  decision that labels never drive geometry.
- **Removal surface.** Deleting `LabelPlacementEngine` touches five placement-aware
  test files plus four churn-affected ones; the alternative (two placement paths in
  two coordinate spaces) was rejected as worse.
- **Placement cost.** Linear-in-obstacles placement would multiply an already
  123 ms p95 enclosure pass; hence the obstacle grid and the §8.4 budget.
- **Multi-map hull overlap (N5).** Pre-existing, out of scope, pinned by the
  three-map falsifier.
- **Two rungs that rarely fire.** Truncation needs 40+ character labels; hover-only
  needs a cramped viewport. Both are kept deliberately so the user rule "show text
  rather than hide it" holds, and must not be removed without re-running the probe.

## 11. Review findings → resolution

### Attempt 1

| finding | resolution |
|---|---|
| B1 placement interface (forced state, level, fonts, painter bypass) | §5.3; §8.3.1; §8.3.4 |
| M1 ordering vs hull separation | §5.2 (projection before geometry and correction) |
| M2 equilibrium misstated | §1.1 corrected to 24.902994; panel 01 regenerated from the solved value |
| M3 seed/spring disagreement | §5.1 shared disc-derived helper |
| M4 enclosure labels misdescribed; hull growth deleted | §1.2 corrected; §5.3 enclosure slot model; §8.3.8 |
| M5 non-falsifiable tests | §8 rewritten |
| m1–m6 | §5.2 result type; §5.2 guard citation; §5.4 cache; §4 `MIN_GAP`; §2 G4 narrowed; §6 probe |

### Attempt 2

| finding | resolution |
|---|---|
| **BLOCKER 1 cross-map guarantee** | §5.2 rebuilt: unit error removed, gap widening dropped, I1 re-verified after the correction with a final projection pass; N5 scopes out the pre-existing multi-map hull defect; §8.2.4 pins it |
| MAJOR 1 panel 01 not reproducible from the committed generator | §9: generator and PNGs regenerated from one source with a reproducibility check (revision 3 commit) |
| MAJOR 2 red phase impossible on the math-notebook fixture | §8.1.1 names a buildable red fixture (two prominence-14 nodes / literal 24.902994 positions) |
| MAJOR 3 truncation fixture unnamed and measured at zero | §6 and §8.3.3 name the 40–49-character fixture, measured 5 truncated at 500×300 |
| MAJOR 4 displacement bound false (52 px) | §5.3 corrects the structural bound (≈109 px), keeps a reachable 90 px cap, records the measured 78.7 px; §8.3.6 asserts it |
| MAJOR 5 no placement cost budget | §5.3 obstacle grid; §8.4 budgets and stages the pass against the recorded 123 ms p95 baseline |
| MAJOR 6 cache key omits pan, fonts, previous | §5.3/§5.4 pan-invariant placement area, font identity in the key, single-entry `previous`, §8.3.7 pan test |
| m1 probe omitted the enclosure obstacle | §6 table regenerated with the obstacle and collision counts |
| m2 `LabelPlacement.Mode` cannot survive its own deletion | §5.3 moves the enum onto the new result type |
| m3 test-file list misattributed | §8.5 corrected to five placement-aware files plus four churn-affected |
| m4 k=1 mechanism stated backwards | §5.1 corrected: the spring pulls the child to the anchor |
| m5 I1 "every frame" vs published residuals | §4 scopes I1 to frames with `residualViolations == 0` |
| m6 geometry-independence test vacuous | §8.3.9 turned into a paint-path wiring assertion |
