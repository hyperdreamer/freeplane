# Graph Workspace Node Separation — Design

- Date: 2026-09-12 (revision 4)
- Topic: graph-node-separation
- PM run: `pm-run-20260912-012815-01038a52`
- Delivery target: `refs/heads/plugin/graph-workspace` at `/data/home/guest/Development/freeplane`
- Integration branch: `pm/graph-node-separation/run-pm-run-20260912-012815-01038a52`
- Evidence: `docs/superpowers/specs/mockups/2026-09-12-node-separation/` (seven panels plus the generator that produces them)
- Review history: r1 → 1 blocker / 5 majors / 6 minors; r2 → 1 blocker / 6 majors / 6 minors;
  r3 → 1 blocker / 7 majors / 6 minors. Revision 4 resolves the attempt-3 findings; §11 maps every
  finding from all three attempts.

## Revision 4 changelog

1. **The pipeline now has exactly one enforcing stage, at the end, followed by verification.** Revision 3
   ran a projection before geometry *and* a final pass, then verified before the final pass — so the
   published positions were never the object that was verified, and the final pass could create
   intra-map violations the design never re-checked. (was BLOCKER 1, MAJOR 1)
2. The false "≤ 2.2 units" displacement bound and the "(only cross-map pairs can)" claim are deleted; the
   pass budget is stated and non-convergence is reported on the published frame.
3. N5 restated: the design does not claim to protect hull separation end to end, and the ≥3-map behaviour
   is pinned by an end-to-end test rather than by a claim about `MapTierCorrection` in isolation.
4. §8.2.4's pinned three-map numbers now name hand-built hulls (±30), because a literal "one node per map"
   fixture produces different geometry. (was MAJOR 2)
5. Relationship rest length is defined by **endpoint kind**, so links whose endpoint is an enclosure anchor
   are covered; `REST_LENGTH` is still deleted. (was MAJOR 3)
6. §8.4 states a numeric budget and measures it on the path where placement actually runs. (was MAJOR 4)
7. The obstacle grid is seeded with disc rectangles as well as placed labels. (was MAJOR 5)
8. The 90 px cap is replaced by the structural bound, which is provable and testable; the unverifiable
   "cap is reachable" claim is gone. (was MAJOR 6)
9. The placement area is the painted surface in graph-anchored coordinates, with inward slot preference near
   its edge, so a placed label cannot be silently clipped. (was MAJOR 7)
10. Minors: offset attribution corrected (77.9 dense / 78.7 long-label), `previous` retained across key
    changes, the unreachable 7 pt face removed from I2, a numeric projection budget with the recorded
    baseline, the prominence-14 fixture defined by edge construction, and emphatic enclosure labels given a
    defined terminal behaviour that never throws in the paint path.

## 1. Problem

Nodes inside an enclosure are placed too close to one another and sometimes overlap
visually. Reported from the workspace canvas: three cramped nodes in
"Basic Definitions and Theorems" with colliding text, and two nodes behind the
"Axioms" enclosure with a label drawn across a disc.

### 1.1 The disc invariant is violated by construction

`TypedSpringBox.REST_LENGTH = 24.0` (`TypedSpringBox.java:18`) is used for **both**
relationship links and containment links, at exactly
`GraphStreamLayoutEngine.java:230` and `:239` and nowhere else. Relationship links
are built from `endpointId(...)`, which may return an enclosure-anchor id rather
than a node id (`ProjectionEngine.java:250`), so "relationship" does not imply
node↔node — revision 3 stated this wrongly.

A node's rendered radius is `GraphStreamLayoutEngine.NODE_RADIUS = 8.0` (`:40`)
times its prominence scale, capped at `NodeProminence.MAX_SCALE = 1.75`
(`NodeProminence.java:6,36`), so a prominent node renders at radius 14.
`TypedNodeParticle.scaleRepulsion` (`:84-89`) scales the repulsion a node receives
by `separationRadius / 8`, so the two-body equilibrium is not the rest length:
solving `0.05·(d − 24) = 16·1.75 / d²` gives **d = 24.90299406686857**, and a
reflection probe against the shipped `plugin-1.13.4.jar` measured
**24.90299406686856**. Two connected prominent nodes therefore overlap by **3.097**
against the disc sum 28 and fall **9.097 short** of the invariant floor 34.

No rule anywhere in the layout prevents disc overlap: repulsion is a soft
`K2·w/d²` force (`SpringBoxNodeParticle.repulsionN2`, bytecode-verified — skipped
entirely at `d == 0`; `K2 = 16.0`), and the only overlap correction translates
whole maps uniformly.

### 1.2 Text collision is the larger visible defect and is not a layout problem

Node labels are painted by `GraphPainter.paintLabels` at a fixed slot centred above
the disc with **no collision avoidance at any rendering level**. Enclosure labels go
through `LabelPlacementEngine`, which (established in review) avoids only other
*enclosure* labels (`LabelPlacementEngine.java:134, 157, 276`), never discs and never
node labels, may expand the drawn hull by up to `MAX_INTERIOR_EXPANSION = 8.0`
(`:22, 162-196`), and measures with a fixed 12 pt box in the zoom-independent
pipeline (`LayoutSettleLoop.defaultMetrics`, `:960-962`) while the painter draws
`size/zoom` world units, so it under-reserves below 100 % zoom.

Labels are painted at constant **screen** size while discs scale with zoom: disc
non-overlap is zoom-invariant (`D ≥ r_i + r_j`), label non-overlap
(`D·zoom ≥ labelWidth`) is not. No world-space invariant can express "text never
overlaps".

### 1.3 Prior art checked, and rejected

Obsidian's graph view documents node size as a function of incoming references and a
**text fade threshold** that fades labels by zoom; the Obsidian team explicitly
declined label collision detection. Freeplane's node name *is* the node's content,
so fading it is a poor fit — but the evidence settles *where* collision must be
solved: in screen space.

## 2. Goals

- **G1** On every published frame, either node discs satisfy
  `distance(c_i, c_j) ≥ r_i + r_j + MIN_GAP` for every pair, or the frame carries a
  non-zero `residualViolations` count that makes the failure observable. The
  residual is computed **after** the last stage that moves any position.
- **G2** Node text never overlaps other node text, enclosure text, or any disc, at
  the zoom being rendered, and is never silently clipped.
- **G3** When no slot exists, degradation is explicit, ordered, and deterministic.
- **G4** The label placement result must not influence geometry: changing zoom,
  forced state, or placement outcome leaves disc positions and hull polygons
  identical.
- **G5** No persisted-format change: positions, pins, and workspace XML untouched.

## 3. Non-goals

- **N1** No change to `K2`, the solver, or the settle/idle policy.
- **N2** No label-aware layout expansion.
- **N3** No zoom-driven label fading.
- **N4** `BoundarySizes` stays label-aware for anchor sizing and top-tier ring
  placement; it over-reserves and is harmless. G4 is scoped to the placement result.
- **N5 — restated in revision 4.** Multi-map **hull** separation is not guaranteed by
  this design. `MapTierCorrection`'s pairwise half-delta summing has ≥3-map
  limitations, and the final projection can move a node so that a hull recomputed
  from the published positions overlaps a neighbour's. The design therefore makes no
  claim to fix or protect hull separation; it pins the **end-to-end** behaviour with
  a measured test (§8.2.4) so it cannot change silently. The user-facing rule the
  design owns is I1, which is guaranteed independently of hull geometry.

## 4. Contract

- **I1 — disc separation (world space), verified after the last mutation.**
  Every published frame is produced by the pipeline in §5.2; `residualViolations` is
  computed on those exact published positions, counting every pair with
  `distance < r_i + r_j + MIN_GAP`. G1 holds by construction. `MIN_GAP = 6.0` world
  units, declared once (`org.freeplane.plugin.graph.layout.NodeSeparation.MIN_GAP`):
  two 8-unit-radius discs stay visibly distinct without loosening the layout
  noticeably.
  **Exception:** two *pinned* nodes may overlap; a pin is user intent and the layout
  never moves a pinned particle. A pinned/movable pair is resolved entirely by
  moving the movable node.
- **I2 — text separation (screen space, per rendered frame).**
  Every drawn label rectangle is disjoint from every other drawn label rectangle and
  from every disc rectangle, at the zoom being rendered, and lies inside the painted
  surface. Rectangles are computed from the **font the painter will use**, which the
  placement result carries; the painter paints that font verbatim.
  The faces that can actually paint text are 12 pt, 9 pt dense, and 15 pt bold
  emphatic: `GraphPainter.shouldPaintLabel` (`:301-305`) suppresses non-forced
  labels at `OVER_TARGET`, so the theme's 7 pt face never paints and is outside I2.
- **I3 — degradation ladder (ordered).**
  `full@paintedFont → full@displaced → denseFont → displaced denseFont → truncated →
  hover-only`. Forced labels (selected, hovered, related) are placement inputs, placed
  first, never drawn below full text. Emphatic enclosure labels are required: they are
  placed first and, when no slot exists, are forced at the hull label anchor as a
  **documented I2 exception** rather than throwing — the paint path never throws.
- **I4 — stability.** Placement is deterministic in `(request, previous)`; a label
  keeps its previous slot while that slot remains collision-free.

## 5. Architecture

```
physics particles  (GraphStream)
      │
      ▼ raw LayoutPositions
GraphGeometryEngine        discs + hulls from raw positions              (existing)
      │
      ▼
MapTierCorrection          whole-map translation, unchanged              (existing)
      │
      ▼
NodeSeparationProjection   §5.2 THE enforcing stage: all pairs, pinned-aware, bounded
      │
      ▼ verification of I1 on these exact positions -> residualViolations
LayoutFrame                published positions + residual + conflicts + idle
      │
      ▼ hulls recomputed from published positions (LayoutSettleLoop:543)  (existing)
ScreenLabelPlacement       §5.3: node AND enclosure labels, one obstacle set
      │
      ▼
GraphPainter               paints exactly the placed labels and their fonts
```

### 5.1 Springs that agree with the invariant

- **Rest length by endpoint kind.** `radiusOf(endpoint)` is the disc radius for a
  node endpoint and the **content ring radius** `R(k)` for an enclosure-anchor
  endpoint (the radius at which that anchor's own `k` direct children orbit, so a
  relationship to a collapsed boundary is comparable to a relationship to a node).
  A relationship link's rest length is `radiusOf(first) + radiusOf(second) + MIN_GAP`,
  which covers node–node, node–anchor and anchor–anchor links with one rule.
- **Containment links** (anchor → direct child): rest length `R(k)`, the same
  disc-derived ring radius, with
  `R(k) = (2·max_r + MIN_GAP) / (2·sin(π/k))` for `k ≥ 2` and `R = 0` for `k = 1`.
  Verified: the adjacent chord of a regular k-gon is `2R·sin(π/k)`. For `k = 1` the
  child converges onto the anchor — the spring with `restLength = 0` pulls an
  off-centre child inward (`TypedSpringBox.addTypedAttraction`, `:96-108`) and the
  zero-distance fallback is the only separator; the pair is not a disc pair, so I1
  does not apply and no `NaN` is produced.
- **Seeds:** `Seeds.nodePosition` (`GraphStreamLayoutEngine.java:670-687`) calls the
  same disc-derived helper as the spring, so the label-aware seed (≈134 for
  "Axiom of Choice") and `R(2) = 17` no longer disagree by 7.9×.
  `BoundarySizes.directNodeRingRadius` keeps its label-aware formula for
  `directNodeReach → sizeOf → boundaryRadius` and `topRingPosition`, so nothing is
  orphaned (N4).

`REST_LENGTH` becomes unused and is deleted. `LayoutCalibration` multipliers are
unchanged; anchor-to-anchor hierarchy links keep `GROUP_SPACING` / `SUB_GROUP_SPACING`.
Settled positions change, so seeds, step counts and fixture expectations move (§10).

### 5.2 `NodeSeparationProjection` — the single enforcing stage

```java
final class NodeSeparationResult { LayoutPositions positions; int residualViolations; }
NodeSeparationResult project(GraphProjection projection, LayoutPositions positions, Set<ProjectedNodeKey> pinned);
```

- **Placement in the pipeline.** It runs **after** the tier correction and before
  publication. Anything that moves positions runs earlier; nothing that moves
  positions runs later. Revision 3's two-pass arrangement is gone: it verified before
  the last mutation, so the published positions were never the verified object, and
  its second pass could create intra-map violations it never re-checked.
- **Algorithm.** Deterministic pairwise relaxation in a fixed order (nodes sorted by
  projected key); a violating pair separates by half the penetration each; a pinned
  node absorbs none and its partner takes all. Uniform spatial hash,
  `O(n + k·pairs)`, bounded passes, early exit.
- **Budget and honesty about non-convergence.** Passes are capped at a fixed constant
  and the relaxation factor is fixed, so a two-sided configuration can in principle
  fail to converge within the cap. There is no claimed displacement bound: after the
  final pass the code **recomputes all-pair violations** and stores that count in
  `NodeSeparationResult.residualViolations`, which becomes the published frame's
  residual. Revision 3's "≤ 2.2 units" figure was an arithmetic error and is deleted.
- **Verification site.** `LayoutWorker.accept` calls the projection, then recomputes
  the residual from the returned positions, then constructs the frame, so
  `residualViolations` always describes the positions being published —
  `LayoutFrame` gains the field alongside `conflicts` and `idle` (`LayoutFrame.java:16-30`).
- Non-finite positions are rejected with `IllegalArgumentException` (finiteness guards
  live in `GraphStreamLayoutEngine.java:324,336` and `PerceptualIdlePolicy.java:95`).
- Stateless: a correction is never fed back into the solver.

**Cross-map behaviour.** `HULL_CLEARANCE` is measured from the disc **edge**
(`GraphGeometryEngine.java:23, 225-233`), so hull separation alone is ample for small
gaps; the hazard is that `MapTierCorrection` sums independent half-deltas per pair and
skips zero translations (`MapTierCorrection.java:61-79`), which can move a map back
toward another (`(0,0),(40,0),(−40,0)` → `(0,0),(50,0),(−50,0)`; a re-implementation
over random 6–7 single-node-map configurations with all raw pairs ≥ 34 produced
post-correction minima of 31.788, 30.742, 32.659). Because the projection runs last
and covers **all** pairs, its result — not the correction — is what I1 reports, and
the residual is measured on the published positions. Hull separation is out of scope
by N5.

### 5.3 `ScreenLabelPlacement` (new, screen space)

```java
final class LabelPlacementRequest {
    GraphProjection projection;
    GraphGeometry geometry;
    double zoom;
    Rectangle2D placementArea;              // painted surface, graph-anchored
    Set<ProjectedEndpointKey> forced;       // selected, hovered, related
    RenderingLevel renderingLevel;
}
List<PlacedLabel> place(LabelPlacementRequest request, List<PlacedLabel> previous, LabelFonts fonts);
```

- **Pan invariance and the painted surface.** The placement area is the canvas's
  painted surface expressed in graph-anchored screen coordinates, not the window and
  not an unbounded "bounds + margin". Panning therefore does not invalidate placement;
  the painter translates and clips. A candidate outside the painted surface is
  **rejected**, so a placed label is never silently clipped; near the surface edge,
  slots pointing inward are tried first. (Revision 3 used a graph-bounds margin that
  could exceed the canvas's own `WORLD_MARGIN = 80` world-unit surface
  (`GraphCanvas.java:36, 403-406, 441-442`) and be clipped without a marker.)
- **Forced state and rendering level are inputs**; the painter loses its independent
  node forced bypass (`GraphPainter.java:301-305`). The placement result **carries the
  font it used**, and the painter paints that font verbatim, so I2 compares like with
  like; `LabelFonts` is the extracted painter resolver (`GraphPainter.labelFont`,
  `:289-300`: emphatic 15 bold, forced 12, level 12/9, `deriveFont(size/zoom)` with the
  painter's clamp).
- **Obstacle grid seeded with discs.** The uniform screen-space grid holds screen
  rectangles for **every disc** and every enclosure-label reservation, and placed
  labels are inserted as they are accepted. Candidate tests are grid queries, so the
  frame cost is `O(labels × rungs × slots)` plus queries — without the disc seed the
  cost claim would be false for 2000 nodes.
- **No hull growth.** `LabelPlacementEngine`, `LabelPlacement`,
  `GraphGeometry.labels()`, hull expansion and `MAX_INTERIOR_EXPANSION` are deleted;
  `LabelPlacement.Mode` moves onto the new result type so it survives as the placement
  mode.
- **Priority:** forced labels (selected → hovered → related) → enclosure labels
  (emphatic → subtle) → node labels by descending disc radius.
- **Enclosure slots:** interior at the hull label anchor when the rectangle fits and
  no disc occupies it; otherwise arc slots on the longest hull edges; otherwise
  exterior slots with a leader line; otherwise hover-only — exempt for emphatic
  labels, which are forced at the anchor as a documented I2 exception (I3).
- **Node slots:** eight near slots at a 6 px gap, then four displaced slots at 30 px
  plus half the label box. Offsets are screen pixels, so displacement is bounded
  **by construction** at `max_r + 30 + max(w,h)/2` ≈ 110 px for the widest slot and
  label; measured maximum is 77.9 px on the dense fixture and 78.7 px on the
  long-label fixture. Revision 3's 90 px cap was removed: no committed fixture
  rejected a candidate, so it was an unverifiable rule.
- Leader lines are drawn for every slot other than directly above/below the disc.

### 5.4 Painting and caching

- The canvas paint layer owns a `ScreenLabelPlacementCache`; `CanvasState` is
  immutable and published at layout time (`LayoutSettleLoop.java:548, 723`) while zoom
  and paint state live in the paint layer (`GraphCanvas.paintComponent`, `:358-365`).
- **Key:** projection generation, positions identity, zoom, placement-area geometry,
  forced-set digest, rendering level, font/theme identity. Pan is deliberately absent
  (pan invariance, §5.3).
- **`previous` is retained across key changes.** The key decides when rectangles must
  be *recomputed*; the previous assignment is kept and re-validated against the new
  obstacles so I4 stickiness survives a small layout change. (Revision 3 said the
  cache is "invalidated whenever any key component changes", which made §4 I4 and
  §8.3.5 unimplementable.)
- Accessibility, search and tooltips read projection labels
  (`AccessibleGraphCanvas.java:494`, `GraphSearchModel.java:61`,
  `ContributorInspector.java:300`) and are unaffected.

## 6. Degradation rule and its measured limits

Measured with the committed generator (which reproduces every committed panel
byte-for-byte), all with the enclosure label in the obstacle set:

| fixture | viewport | result | collisions |
|---|---|---|---|
| 12 nodes, short names | 1128×364 … 420×240 | full 11, hidden 1 | 0 / 0 |
| 12 nodes, short names | 280×170 | full 7, dense 4, hidden 1 | 0 / 0 |
| 12 nodes, short names | 200×130 | full 2, dense 4, hidden 6 | 0 / 0 |
| **6 nodes, 40–49-char names** | 1128×364 / 500×300 | dense 1, truncated 5 | 0 / 0 |
| **6 nodes, 40–49-char names** | 200×130 | full 1, truncated 2, hidden 3 | 0 / 0 |

Truncation is reachable only with long labels; for short names it fires 0 times at
every size, and displacement-first versus truncation-first ladders produce identical
placements from 200×130 to 1128×364, because a blocked slot is blocked by occupancy
rather than by label width. Recorded so it is not "fixed" later by mistake:

- Truncation is the terminal rung before hover-only and is pinned by the measured
  40–49-character fixture (§8.3.3).
- Displacement carries the load for ordinary names: dense fixture mean offset 48.05 px
  and maximum **77.9 px**; long-label fixture maximum **78.7 px**; leader crossings 0
  on the dense fixture.
- Hover-only marks hidden labels with a dashed ring.

## 7. Error handling

- **Coincident particles:** deterministic key-derived axis, never `NaN`; a coincident
  label/disc pair counts as a collision.
- **Pin-versus-pin overlap:** accepted and documented.
- **Non-convergence:** the residual is recomputed after the final pass and published;
  frames are never dropped, so a violation is observable rather than hidden.
- **Non-finite geometry:** rejected at the projection boundary.
- **Emphatic enclosure labels:** forced at the anchor when no slot exists, with the I2
  exception recorded in the frame's placement report; never an exception in the paint
  path.

## 8. Test strategy

### 8.1 `NodeSeparationProjectionShould` (pure function)

1. **Red phase.** Build prominence through the public path: a node with ≥14 visible
   outgoing targets reaches scale 1.75 and radius 14 (`ProminenceCalculator`;
   `GraphProjection` exposes no caller-supplied prominence, so the fixture must be
   edge-constructed). Assert `violationCount(raw) > 0` on that fixture (or on literal
   raw positions 24.902994 apart), then `residualViolations == 0` and
   `violationCount(result.positions) == 0`. The math-notebook fixture cannot be red.
2. Half/half displacement; with one pinned, only the partner moves, pinned coordinate
   bit-identical.
3. Already-satisfied positions unchanged — paired with test 1 so it cannot pass
   vacuously.
4. Coincident particles separate deterministically; no `NaN`; repeated runs
   byte-identical; iteration order irrelevant.
5. **Numeric bound:** 2000-node / 5000-edge fixture, p95 ≤ 20 ms and ≤ the pass cap,
   recorded against the existing projection stage's measured p95 12,161,679 ns /
   p99 15,233,301 ns (`docs/superpowers/specs/2026-08-10-graph-workspace-performance-report.md:187`).
   The measured value is recorded in the test.
6. Residual path: an over-dense two-sided fixture (a node between two pinned nodes)
   reports `residualViolations > 0`, returns finite positions, and the published frame
   carries the same count.

### 8.2 Frame-pipeline tests

1. **Per-frame I1 on published positions**, asserted together with
   `residualViolations == 0`, on a fixture where the projection is asserted to displace
   at least one node.
2. **Verification ordering:** the residual in the published `LayoutFrame` equals an
   independently recomputed all-pair count on those positions (falsifies verifying
   before the last mutation).
3. Omitted-fix guard: with the projection disabled the per-frame test fails.
4. **End-to-end three-map pin (N5).** With hand-built ±30 hulls
   (`HullGeometry.of`, as `MapTierCorrectionShould` already does with `square()`) at
   `(0,0)`, `(40,0)`, `(−40,0)` — a literal one-node-per-map fixture gives different
   numbers because default prominence is radius 8, half-extent 24 — assert the
   pipeline's published positions and recomputed hull overlap, so the pre-existing
   behaviour and the projection's effect on it are both pinned.

### 8.3 `ScreenLabelPlacementShould`

1. **Painted-ink I2.** Render each label alone into a transparent layer with the font
   the result carries, then assert pairwise bitmap intersection is empty and label
   bitmaps miss disc bitmaps.
2. Zoom {0.25, 0.5, 1.0, 2.0} × level {FULL, DENSE, OVER_TARGET}, at least one emphatic
   enclosure: no intersections. OVER_TARGET asserts that non-forced labels are absent
   rather than placed, since the 7 pt face cannot paint.
3. **Ladder order and reachability.** Long-label fixture (six 40–49-character names,
   500×300): at least one truncated label, and no label truncated while a full-text slot
   was free. Short-name fixture at 1128×364: no truncation.
4. Forced labels: present, full, placed first, and re-placing on hover never
   intersects — including a hover-only label becoming forced.
5. Stickiness: a 1 px position change preserves assignments (the cache keeps `previous`
   across key changes); an invalidated slot is re-placed and does not oscillate while
   valid.
6. **Structural displacement bound:** every label within
   `max_r + 30 + max(w,h)/2` of its disc, with the measured maximum recorded
   (77.9 px dense, 78.7 px long-label).
7. **Pan invariance (wiring):** translating the viewport by a pan delta leaves the
   placement list identical up to translation, and the painted result matches the
   pre-pan painting translated.
8. Enclosure labels: interior when it fits and no disc occupies it, exterior with a
   leader otherwise, emphatic forced at the anchor when nothing fits, and hull polygons
   identical either way.
9. **No silent clipping:** every placed label rectangle lies inside the painted surface,
   asserted against the canvas's surface bounds (the failure revision 3 could not
   detect).
10. **Geometry independence (wiring):** run placement through the real paint path and
    assert the published `CanvasState` geometry is identical before and after zoom,
    forced-set, and placement changes.

### 8.4 Performance

Placement now runs in the paint path, so it must be budgeted there:

- **Budget:** placement p95 ≤ 20 ms on the 2000-node / 5000-edge workload, measured on
  the path where it runs (paint/EDT), at settle, on zoom, and on forced-set change;
  pan must be a translate-only path with no re-placement. Non-conformance fails the
  stage.
- **Baseline:** the worker-side enclosure-only pass measured p95 123,308,455 ns /
  max 143,041,802 ns (`.../2026-08-10-graph-workspace-performance-report.md:191`,
  `GraphWorkspacePerformanceDiagnostic.java:354-358`). That stage measured a different
  execution path; the new stage is added on the paint path and the old enclosure-stage
  measurement is removed rather than reinterpreted.
- Obstacle grid seeded with discs and labels by construction (§5.3).

### 8.5 Regression

Full module suite. Removal surface: **five** test files reference `LabelPlacement` /
`LabelPlacementEngine` / the 3-arg `GraphGeometry.of` (`LabelPlacementShould` —
replaced by §8.3 — `GraphCanvasPaintShould`, `GraphInteractionControllerShould`,
`GraphWorkspaceModelAcceptanceShould`, `GraphWorkspacePerformanceDiagnostic`), and four
more (`ReferenceRepulsionFixture`, `GroupOnlyProjectionShould`,
`ProjectionDeterminismShould`, `StructuralProjectionShould`) are churn-affected.
Production call sites: `LayoutSettleLoop.java:543`, `:721`, `LabelAssembler:983`; the
only reader of `GraphGeometry.labels()` is `GraphPainter.java:262`. Settle/idle must not
regress: the two-map fixture's idle frame count is compared against a recorded baseline.

## 9. Mockups

Generator and PNGs are committed together; regenerating the generator outside the
repository reproduces every committed panel byte-for-byte (verified in review).

| panel | today | proposed |
|---|---|---|
| 01-disc-invariant | settled 24.90, overlap 3.10, I1 shortfall 9.10 | centres 34.00 → 0 overlaps |
| 02-sparse | 2 label/label collisions | 0 collisions, 3 full labels |
| 03-dense | 13 label/label + 17 label/disc pairs | 0 collisions, 11 full, 1 hover-only |
| 04-zoom | — | 11 of 12 placed at 100 %, 8 at 50 % |
| 05-cramped-window | 13 + 17 collisions | 0 collisions |
| 06-ladder-truncate-vs-displace | — | (a) and (b) identical at every size |
| 07-truncation-reachable | — | long labels: 5 truncated at 500×300 |

**Honesty notes.** These are a *simulation* of the described algorithms, not the
production renderer. Panel 01 is generated from the solved equilibrium
(24.90299407) and reports both thresholds. Panels 02/03/05 are synthetic scenes. The
reachability probe includes the enclosure obstacle and prints collision counts.

## 10. Risks

- **Fixture churn.** Disc-derived rest lengths and seeds change settled positions, step
  counts, snapshots and performance hashes; `ReferenceRepulsionFixture`,
  `GraphWorkspacePerformanceDiagnostic` and `PerformanceTripwiresShould` expectations
  move and must be re-recorded deliberately.
- **Hull separation (N5).** Neither guaranteed nor claimed; pinned end-to-end.
- **Hull shrinking.** Removing interior expansion shrinks boundaries by up to 8 units
  where a label previously forced growth.
- **Removal surface.** Five placement-aware test files plus four churn-affected ones.
- **Placement cost.** The paint-path budget is the riskiest new number; it is measured
  on the real path (§8.4) rather than inferred.
- **Two rungs that rarely fire.** Truncation needs 40+ character labels; hover-only
  needs a cramped surface. Both are deliberate; do not remove without re-running the
  probe.

## 11. Review findings → resolution

### Attempt 1

| finding | resolution |
|---|---|
| B1 placement interface | §5.3 (forced, level, shared fonts, painter bypass removed); §8.3.1/4 |
| M1 ordering vs hull separation | §5.2 single enforcing stage at the end |
| M2 equilibrium misstated | §1.1 (24.902994); panel 01 regenerated |
| M3 seed/spring disagreement | §5.1 shared disc-derived helper |
| M4 enclosure labels, hull growth | §1.2 corrected; §5.3 slot model; §8.3.8 |
| M5 non-falsifiable tests | §8 rewritten |
| m1–m6 | §5.2 result type/guards; §5.4 cache; §4 `MIN_GAP`; §2 G4; §6 probe |

### Attempt 2

| finding | resolution |
|---|---|
| BLOCKER 1 cross-map guarantee | §5.2: unit error removed, gap widening dropped, projection runs last over all pairs, residual measured on published positions; N5 scoped |
| MAJOR 1 panel 01 not reproducible | §9 generator+PNG regenerated, byte-comparison verified in review |
| MAJOR 2 red phase impossible | §8.1.1 edge-constructed prominence fixture |
| MAJOR 3 truncation fixture unnamed | §6/§8.3.3 measured 40–49-character fixture |
| MAJOR 4 displacement bound false | §5.3 structural bound; cap removed in r4 (MAJOR 6 below) |
| MAJOR 5 no placement budget | §8.4 numeric budget on the paint path |
| MAJOR 6 cache key/pan/fonts | §5.3 pan-invariant painted surface; §5.4 key + retained `previous` |
| m1–m6 | §6 probe; §5.3 `Mode` relocation; §8.5 file list; §5.1 k=1; §4 I1 scoping; §8.3.10 wiring test |

### Attempt 3

| finding | resolution |
|---|---|
| **BLOCKER 1 verification before the last mutation; false bound** | §5.2 single final enforcing stage, residual recomputed after it and published; "(only cross-map pairs can)" and "≤ 2.2 units" deleted; §8.2.2 falsifies the ordering |
| MAJOR 1 final pass can invalidate hulls; N5 "neither fixes nor worsens" false | §3 N5 restated: no hull-separation claim, end-to-end pin in §8.2.4; §5.2 documents the consequence |
| MAJOR 2 §8.2.4 numbers need unstated hull size | §8.2.4 names hand-built ±30 hulls and why a literal fixture differs |
| MAJOR 3 rest length undefined for anchor endpoints | §5.1 rest length by endpoint kind (`radiusOf` = disc or content ring radius); §1.1 corrected |
| MAJOR 4 budget not numeric, wrong measurement site | §8.4 p95 ≤ 20 ms on the paint path with the recorded 12.16 ms projection baseline |
| MAJOR 5 obstacle grid omits discs | §5.3 grid seeded with discs and enclosure reservations |
| MAJOR 6 90 px cap unreachable | §5.3 cap removed; structural bound stated and asserted (§8.3.6) |
| MAJOR 7 placement area can exceed the painted surface | §5.3 placement area = painted surface, inward slot preference, out-of-surface candidates rejected; §8.3.9 asserts no clipping |
| m1 max offset misattributed | §6 states 77.9 dense / 78.7 long-label |
| m2 cache invalidation vs stickiness | §5.4 `previous` retained across key changes |
| m3 7 pt face unreachable | §4 I2 excludes it; §8.3.2 asserts non-forced labels are absent at OVER_TARGET |
| m4 projection budget without measurement | §8.1.5 records the 12.16 ms p95 baseline and sets ≤ 20 ms |
| m5 prominence-14 fixture not buildable as stated | §8.1.1 defines it by edge construction (≥14 visible outgoing targets) |
| m6 emphatic enclosure terminal behaviour undefined | §4 I3/§7: forced at the hull anchor as a documented I2 exception, never throws |
