# Graph Workspace Node Separation — Design

- Date: 2026-09-12
- Topic: graph-node-separation
- PM run: `pm-run-20260912-012815-01038a52`
- Delivery target: `refs/heads/plugin/graph-workspace` at `/data/home/guest/Development/freeplane`
- Integration branch: `pm/graph-node-separation/run-pm-run-20260912-012815-01038a52`
- Evidence: `docs/superpowers/specs/mockups/2026-09-12-node-separation/` (six PNG panels plus the generator that produced them)

## 1. Problem

Nodes inside an enclosure are placed too close to one another and sometimes overlap
visually. Reported from the workspace canvas: three cramped nodes in
"Basic Definitions and Theorems" with colliding text, and two nodes behind the
"Axioms" enclosure with a label drawn across a disc.

Investigation (read-only, code + headless simulation) separates two independent
defects that the screenshot merges into one impression.

### 1.1 The disc invariant is violated by construction

`TypedSpringBox.REST_LENGTH = 24.0` is used for **both** relationship links
(node↔node) and containment links (node→enclosure anchor)
(`GraphStreamLayoutEngine` lines 228-239). A node's rendered radius is
`GraphStreamLayoutEngine.NODE_RADIUS (8.0) × prominence scale`, and
`NodeProminence.MAX_SCALE = 1.75`, so a prominent node renders at radius 14. Two
connected prominent nodes therefore settle at centre distance 24 while needing
28: **they must overlap by 4 units**. No rule anywhere in the layout prevents
disc overlap; GraphStream's repulsion is a soft `K2·w/d²` force
(`SpringBoxNodeParticle.repulsionN2`, `K2 = REPULSION_FACTOR = 16.0`) that is
skipped entirely when two particles coincide, and the only overlap correction in
the pipeline (`HullIntersection.minimumSeparatingTranslation`, applied by
`MapTierCorrection`) translates whole maps uniformly and so cannot repair
intra-map crowding.

Reproduced in mockup panel 01: centres 24 apart, radii 14 + 14, overlap 4 units.

### 1.2 Text collision is the larger visible defect and is not a layout problem

Node labels are painted by `GraphPainter.paintLabels` at a fixed slot centred
above the disc (`y = center.y − radius − 8/zoom`) with **no collision avoidance
at any rendering level**. Enclosure labels do go through
`LabelPlacementEngine` (candidate slots, disc avoidance, leader lines).

Panel 03 shows the consequence: in a 12-node scene with **zero disc overlaps**,
today's renderer produces **13 label/label collision pairs and 17 label/disc
collision pairs**. Panel 02 shows the same effect in a sparse scene where the
discs are 46 and 60 units apart.

Two structural facts make the label problem a screen-space problem, not a
geometry problem:

1. Labels are painted at font size `size/zoom` world units, i.e. **constant
   screen size** (`GraphPainter.labelFont`), while discs scale with zoom. Disc
   non-overlap (`D ≥ r_i + r_j`) is therefore zoom-invariant; label non-overlap
   (`D·zoom ≥ labelWidth`) is not.
2. `LabelPlacementEngine` currently measures with a fixed 12 pt `Dialog` box in
   the zoom-independent geometry pipeline (`LayoutSettleLoop.defaultMetrics`).
   Because labels are painted larger in world units when `zoom < 1`, the existing
   engine **under-reserves below 100 % zoom** and enclosure labels can already
   collide with discs there. This is a latent defect of the same family.

### 1.3 Prior art checked, and rejected

Obsidian's graph view was examined as a reference because the user asked for it.
Its primary documentation states that node size encodes incoming references
(never label length), and exposes a **text fade threshold** that fades labels by
zoom; the Obsidian team explicitly declined label collision detection on the
forum ("we don't have any plans to fix this ... zoom in more"). Obsidian's
`Repel force` / `Link distance` are global sliders with no per-node radius or
label awareness. Freeplane's node name *is* the node's content, so silently
fading it is a poor fit; but the Obsidian evidence is what settled the question
of *where* label collision must be solved: in screen space.

## 2. Goals

- **G1** Rendered node discs never overlap: centre distance ≥ `r_i + r_j + MIN_GAP`
  on every published layout frame, for any combination of pins.
- **G2** Node text never overlaps other node text, enclosure text, or any disc, at
  the zoom and viewport being rendered.
- **G3** When the viewport cannot hold every label, degradation is explicit,
  ordered, and deterministic — never a silent pile-up.
- **G4** The layout stays compact. Label width must not drive graph geometry.
- **G5** No persisted-format change: positions, pins, and workspace XML are
  untouched.

## 3. Non-goals

- **N1** No change to the repulsion constant `K2`, the force solver, or the
  settle/idle policy. A repulsion increase cannot *guarantee* G1 and per-frame
  force manipulation previously caused the CPU-burning settle defect.
- **N2** No label-aware layout expansion (the rejected "option B"): reserving full
  label boxes in the physics grows a map like ZFC by roughly 2–3×, couples AWT
  text metrics into the solver, and still fails at low zoom because text is
  screen-constant.
- **N3** Zoom-driven label fading (Obsidian's mechanism) is not introduced.
- **N4** The known inconsistency between `BoundarySizes` (label-aware anchor
  radius) and `GraphGeometryEngine.computeHull` (disc-radius hull) is left as is.
  It over-reserves space and is harmless; changing it would churn the boundary
  fixtures for no user-visible gain.

## 4. Contract

Four invariants. I1 and I2 are hard; I3 and I4 are deterministic behaviour rules.

- **I1 — disc separation (world space, every published frame).**
  For every pair of projected nodes `i ≠ j`: `distance(c_i, c_j) ≥ r_i + r_j + MIN_GAP`,
  where `r = NODE_RADIUS × prominence scale`.
  **Exception:** two *pinned* nodes may overlap, because a pin is explicit user
  intent and the layout never moves a pinned particle. A pinned node never moves;
  its neighbours are displaced instead.
- **I2 — text separation (screen space, per rendered frame).**
  Every drawn label rectangle is disjoint from every other drawn label rectangle
  and from every disc rectangle. Placement is recomputed per zoom and viewport.
- **I3 — degradation ladder (ordered).**
  `full@12 → full@12 displaced → full@9 → full@9 displaced → truncated@12 →
  truncated@9 → hover-only`. Truncation and hover-only are *terminal* rungs; a
  forced label (selected, hovered, or related) is never drawn below full text.
- **I4 — stability.**
  Placement is a pure function of (positions, zoom, viewport, previous
  assignment). A label keeps its previous slot while that slot remains
  collision-free, so settling and dragging do not make labels jump between slots.

## 5. Architecture

Two mechanisms, one per invariant, each at the layer that owns the required
coordinate space.

```
physics particles  (GraphStream)
      │
      ▼ raw LayoutPositions
MapTierCorrection        whole-map translation, sibling hull separation   (existing)
      │
      ▼
NodeSeparationProjection  NEW: per-node push-apart to satisfy I1            (world space)
      │
      ▼ published LayoutPositions
GraphGeometryEngine       discs + hulls                                   (existing)
      │
      ▼
ScreenLabelPlacement      NEW: node labels AND enclosure labels, one obstacle set (screen space)
      │
      ▼
GraphPainter              draws labels from placements                     (existing, simplified)
```

### 5.1 Geometry: springs that agree with the invariant

`NodeSeparationProjection` guarantees I1, but the solver should already be close to
satisfying it, otherwise the projection does corrective work on every frame and
the published positions inherit the solver's oscillation. Two rest lengths change
(single execution path; the constant-24 behaviour is removed, not kept as a
fallback):

- **Relationship links:** rest length becomes `r_i + r_j + MIN_GAP` — the same
  quantity the invariant requires. This alone removes the 24 < 28 defect.
- **Containment links:** rest length becomes a disc-derived ring radius sized so
  that `k` direct children at radius `R` satisfy I1 between neighbours:
  `R(k) = (2·max_r + MIN_GAP) / (2·sin(π/k))` for `k ≥ 2`, and `R = 0` for `k = 1`.
  This mirrors the existing `BoundarySizes.directNodeRingRadius` seed formula,
  which is currently label-aware while the physics is not — the seed and the
  spring are brought into agreement using disc radii only. The label-aware
  `BoundarySizes` value stays in place for anchor sizing (see N4).

`LayoutCalibration` multipliers are unchanged, and hierarchy links between
enclosure anchors keep `GROUP_SPACING` / `SUB_GROUP_SPACING`. `REST_LENGTH`
becomes unused and is deleted rather than left as a fallback.

### 5.2 `NodeSeparationProjection` (new, world space)

Pure function; no history, no randomisation:

```
LayoutPositions project(GraphProjection projection, LayoutPositions positions, Set<ProjectedNodeKey> pinned)
```

- Deterministic pairwise relaxation in a fixed order (nodes sorted by projected
  key), displacing both nodes of a violating pair by half the penetration each;
  a pinned node absorbs none of the displacement and its partner takes all of it.
- Uniform spatial hash so the cost is `O(n + k·pairs)` rather than `O(n²)`;
  bounded passes with early exit once no violation remains.
- Non-finite inputs are rejected with `IllegalArgumentException`, matching the
  guard style already used by `GraphGeometryEngine`.
- Returns positions with **zero** violations for every non-exempt pair. If a
  residual survives the bounded passes (pathological density), the frame carries
  an explicit `NodeSeparationResidual` diagnostic instead of silently publishing
  overlapping discs; this is observable and asserted in tests.

The projection deliberately holds no per-frame state: a correction applied at
frame *n* is not fed back into the solver, so it cannot re-introduce the
oscillation that the settle work removed. I1 is therefore recomputed from the
solver's own state every frame and stays stable at equilibrium.

Ordering: the projection runs **last**, after `MapTierCorrection`. A whole-map
translation preserves intra-map distances but can bring two maps' discs closer
across a hull boundary; running the projection after it means nothing downstream
can re-break I1.

### 5.3 `ScreenLabelPlacement` (new, screen space)

Pure function over zoom and viewport:

```
List<PlacedLabel> place(GraphProjection, GraphGeometry, double zoom, Rectangle2D viewport,
                        List<PlacedLabel> previous, GeometryTextMetrics screenMetrics)
```

- **One obstacle set for both label kinds.** Node labels and enclosure labels are
  placed by this single pass, so a node label never lands on "Basic Definitions
  and Theorems" and vice versa. This is why enclosure labelling moves out of the
  world-space pipeline (I2 requires zoom; the current engine cannot see it).
- **Priority order:** forced labels first (selected, then hovered, then related),
  then descending node radius, then enclosure emphatic labels, then the rest.
  Forced labels are placed before any other claim on space and never degrade
  below full text.
- **Candidate slots:** eight near slots (above, below, left, right, four
  diagonals) at a 6 px gap from the disc, then four displaced slots at a larger
  offset. Slots are tried in a fixed order, which makes placement deterministic.
- **Displacement cap:** `MAX_LABEL_OFFSET_PX = 90` (screen pixels) bounds how far
  a label may travel from its disc. Measured on the fixtures, mean offset is
  47 px and leader lines never cross, so the cap does not bite in normal use; it
  exists to stop runaway displacement at low zoom.
- **Leader lines** are drawn for every slot other than directly above/below the
  disc.
- **Sizing metric:** the same 12 pt/9 pt `SANS_SERIF` faces the painter draws,
  converted to screen space, so reserved space matches painted ink at every zoom.

### 5.4 Painting

`GraphPainter.paintLabels` consumes the placement list and draws text, leader
lines, and hover-only markers. The fixed-slot code path is **removed** (one active
path, per repository policy) rather than kept behind a flag. Hidden labels are
absent from the list; `AccessibleGraphCanvas` continues to expose node and
enclosure text from the projection, so accessibility does not depend on
placement.

Placement is cached on (positions generation, zoom, viewport size) and recomputed
only when one of those changes, so painting stays allocation-free per repaint.

## 6. Degradation rule and its measured limits

Ladder rung occupancy was measured over viewport sizes from 1128×364 down to
200×130 with a greedy first-fit placement and a 12-node fixture:

| viewport | result |
|---|---|
| 1128×364 … 420×240 | full 11, hidden 1, 0 collisions |
| 280×170 | full 7, dense font 4, hidden 1 |
| 200×130 | full 2, dense font 4, hidden 6 |

`truncated` was **0 in every run**, and displacement-first versus
truncation-first ladders produced **identical** placements at every size. The
reason is mechanical: when a slot is blocked it is blocked by occupancy (a disc
or another label), not by the label being too wide, so shortening text does not
create space. Probing the hidden labels for a genuinely free slot that a minimal
truncated label could occupy returned 0 salvageable labels at every realistic
size, and 1 of 6 only in the 200×130 window.

Consequences, recorded so they are not "fixed" later by mistake:

- Truncation is retained **only** as the terminal rung before hover-only, where it
  recovers labels in very small windows. It is expected to fire rarely.
- Displacement carries the load; it is cheap (mean offset 47 px, 0 leader
  crossings) and preserves the full name, which is the node's content.
- Hover-only marks hidden labels with a dashed ring so the state is visible
  rather than silent.

## 7. Error handling

- **Degenerate coincident particles:** the projection displaces along a
  deterministic axis derived from the projected key ordering (never `NaN`);
  the label pass treats a coincident label/disc pair as a collision.
- **Pin-versus-pin overlap:** accepted, documented, and not reported as a defect;
  the layout cannot satisfy user intent and a rule at the same time, and pins are
  restored from the workspace file.
- **Unsolvable density:** after the bounded passes, residual violations surface as
  a frame diagnostic; the frame is still published (never dropped) and the
  invariant failure is observable in tests and logs.
- **Non-finite geometry:** rejected at the projection boundary, consistent with
  `GraphGeometryEngine`'s existing guards.

## 8. Test strategy

All tests are JUnit 4 + AssertJ, matching the module's conventions.

1. `NodeSeparationProjectionShould` (new, pure function)
   - I1 holds for every pair after projection on the math-notebook fixture, the
     two-map fixture, and a deterministic dense fixture.
   - a violating pair moves both nodes by half the penetration; with one node
     pinned, only the other moves.
   - already-satisfied positions are returned unchanged (no drift).
   - coincident particles separate deterministically and never produce `NaN`.
   - repeated invocation on the same input returns identical output (determinism),
     and threading/iteration order does not change the result.
   - cost stays within a bound for a 2000-node fixture (spatial hash, early exit).
2. Wiring test on the published frame (`LayoutWorkerShould` / acceptance)
   - the falsifiable G1 test: drive a settle run for the reported fixture and
     assert `distance ≥ r_i + r_j + MIN_GAP` **for every published frame**, not
     only for the settled one.
   - after the full pipeline (`MapTierCorrection` → projection), sibling hulls are
     still separated (guards the ordering decision in §5.2).
3. `NodeLabelPlacementShould` (new, screen space)
   - I2 holds at zoom ∈ {0.25, 0.5, 1.0, 2.0} for the math-notebook fixture: no
     label/label, label/disc, or node-label/enclosure-label intersection.
   - the ladder is ordered: a label is only truncated when no near or displaced
     full-text slot exists, and only hidden when no truncated slot exists.
   - forced labels are always present, full, and placed first.
   - I4: a 1 px position change keeps prior assignments; a label whose slot becomes
     invalid is re-placed and never jumps while its slot stays valid.
   - the displaced offset never exceeds `MAX_LABEL_OFFSET_PX`.
   - the latent enclosure-label defect is pinned by a regression test at zoom 0.5
     that fails against the world-space engine.
4. Regression
   - full module suite, with particular attention to `BoundarySeparationShould`,
   - `HullIntersectionShould`, `TypedForcesShould`, `MapTierCorrectionShould`,
     `PerceptualIdlePolicyShould`, `LayoutWorkerShould`,
     `GraphWorkspaceModelAcceptanceShould` (including the math-notebook scenario),
     and the UI-evidence harness.
   - settle/idle is unchanged: the previously fixed non-idle defect must not
     reappear, asserted by the existing idle-policy tests plus a frame-count
     comparison on the two-map fixture.

## 9. Mockups

`docs/superpowers/specs/mockups/2026-09-12-node-separation/` — produced by the
generator in the same directory using production constants and AWT 12 pt metrics;
the "proposed" panels are output of the algorithms described above, not
hand-drawn.

| panel | today | proposed |
|---|---|---|
| 01-disc-invariant | centres 24, need 28 → overlap 4 | centres 34 → 0 overlaps |
| 02-sparse | 2 label collisions, discs fine | 0 collisions, 3 full labels |
| 03-dense | 13 label/label + 17 label/disc pairs | 0 collisions, 11 full, 1 hover-only |
| 04-zoom | — | 11 of 12 placed at 100 %, 8 at 50 % |
| 05-far-zoom-ladder | 13 + 17 collisions | 0 collisions in a cramped window |
| 06-ladder-truncate-vs-displace | — | (a) and (b) identical at every viewport size |

## 10. Risks

- **Degenerate single-child containment.** `R = 0` places a lone direct child on
  its anchor. The pair is not a disc pair, so I1 does not apply, and GraphStream
  skips repulsion at distance zero, so no `NaN` is produced; the hull is then
  computed around the child. Flagged because it is the one coincident-particle
  case the change introduces deliberately.
- **Fixture churn.** Disc-derived rest lengths change settled positions, so
  boundary and performance fixtures may need new expected values. The idle policy
  thresholds are untouched, but step counts in diagnostics may shift.
- **Projection/solver interaction.** The projection is intentionally stateless; if
  the solver oscillates, published positions oscillate with it. The wiring test
  asserts I1 per frame and the idle tests guard against a settle regression.
- **Painting cost.** Placement is cached by (positions, zoom, viewport); a missing
  cache key would show up as per-repaint allocation in the UI-evidence harness.
- **Two rungs that rarely fire.** Truncation and, at roomy zoom, hover-only are
  measured as near-dead (§6). They are kept deliberately and must not be removed
  without re-running the reachability probe.
