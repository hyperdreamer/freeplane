# Graph Workspace Node Separation — Implementation Specification

- Date: 2026-09-12
- Status: implementation-ready specification, derived from the approved design
  `docs/superpowers/specs/2026-09-12-graph-node-separation-design.md` (revision 5.2,
  approved for specification drafting by review attempt 5 — 0 blockers, 2 majors, 9 minors —
  and revised to apply the specification reviewer's design-level findings).
- Review history: `/data/home/henry-arch/.local/state/pi/project-manager/runs/0233ff88d3cc54bbd61550c61fa917d6e2a5717f8a03be4e9aa63dcd47dd1cfa/pm-run-20260912-012815-01038a52/reports/design-review-attempt-{1..5}.md`.
- This specification does not modify the design. Where the design leaves a value, order,
  encoding or fixture open, this document pins it and states its provenance. All former open
  points are resolved and recorded in §8. O4 (the terminal rule for a forced node label with
  no full-text slot) is resolved by the user default: the label is placed at its base slot,
  records the I2 exception on the placement result (`forcedAtBaseSlot()`), and contributes
  its rectangle to the obstacle set; §5.5, §5.7 and §5.10 re-derive every fixture the forced
  label affects.
- Every number, coordinate and `file:line` cited below was verified against the repository or
  produced by a command run for this specification (Appendix A). Values marked **[M]** are
  measured, **[D]** derived by arithmetic or from measured data, **[P]** proposed by the design
  and adopted here, and **[N]** not pinnable without a code change (explicitly deferred).

## 0. Normative conventions

- "Published frame" means a `LayoutFrame` that reaches `CanvasState` through
  `GraphUpdateCoordinator.publishCanvasState`; engine-internal frames are never published.
- "Screen space" is the coordinate system of `GraphCanvas.paintComponent`'s `Graphics2D`
  before `GraphPainter.worldTransform`. "World space" is the layout coordinate system.
- The world→screen mapping is fixed:
  `sx = W/2 + zoom·(wx − centerX)`, `sy = H/2 + zoom·(wy − centerY)` (from
  `GraphPainter.worldTransform`, `GraphPainter.java:83-89`), with the painted surface
  `(0,0,W,H)`. Fixtures in §5 that were measured in the world-centered convention of the
  committed mockup generator (viewport `(−W/2,−H/2,W,H)`, `ox=oy=0`) are translated by
  `(+W/2,+H/2)`; the translation changes nothing else.
- Node disc radius in screen space is `r' = max(2.0, r·zoom)` where
  `r = NODE_RADIUS · prominence.scale()`, `NODE_RADIUS = 8.0`
  (`GraphStreamLayoutEngine.java:40`), `scale ≤ NodeProminence.MAX_SCALE = 1.75`
  (`NodeProminence.java:6`), hence `r ≤ 14.0`. The `max(2, ·)` floor is the committed
  mockup rule; it is adopted because the committed panels reproduce with it.
- Label rectangles are screen-space logical bounds of the base theme font (C13), never
  world-space font metrics.
- I2 is a rectangle-level contract. Antialiased glyph ink extends beyond the measured
  logical rectangle by a bounded fringe (C14); the specification therefore asserts painted
  ink only through pairwise bitmap disjointness and disc disjointness, never containment.

## 1. Pinned constants

| # | Constant | Value | Kind | Source / justification |
|---|---|---|---|---|
| C1 | `MIN_GAP` | `6.0` world units | **[P]** design §4 | Declared once as `NodeSeparationProjection.MIN_GAP`. Adopted verbatim. Generator constant is `MIN_GAP = 6.0` (`NodeSeparationMockups.java:32`); the disc floor is `14+14+6 = 34`; the red-phase fixture depends on `24 < 34`. No measurement contradicts it; it is a design parameter, not a measured optimum. |
| C2 | `MAX_PASSES` | `64` | **[P]** design §5.2, confirmed by measurement | Adopted. Pass counts are observable through `NodeSeparationResult.passes()` (§2.2). Reference implementation (Appendix A.5) convergence passes: dense12 = 1, long6 = 1, red-phase pair = 2, solvable pair = 2, coincident pair = 3, pinned-pinned adjacent = 2, sandwich = 64 (non-convergent by construction, §5.2 S1). No solvable fixture needed more than 3 passes, so 64 is sufficient and bounds the worst case. Worst-case work bound is derived: `MAX_PASSES · C(n,2)` = `64 · 1,999,000 = 127,936,000` pair tests at the 2000-node perf fixture. |
| C3 | `RELAXATION` and its arithmetic | `0.5`; displacement per movable node `= RELAXATION · penetration`; with one pinned the movable partner takes the **full** penetration; pinned-pinned pairs take none | **[D]** from the design's own evidence | The design's phrase "separates by half the penetration, scaled by `RELAXATION = 0.5`" is arithmetically ambiguous (see §8 O1). This specification pins the mockup's arithmetic: `push = (need − d)/2` per movable node (`NodeSeparationMockups.java:separate`). Under this reading the solvable pair converges in 2 passes; under the alternative "total = 0.5·penetration" reading it needs 50 passes (Appendix A.5; an earlier draft said 52, which is not reproducible), which would make the design's own "same pass budget" convergence fixture fragile. The pinned reading also matches design §8.1.2 "half/half displacement; with one pinned, only the partner moves". |
| C4 | `SPATIAL_CELL` | `34.0` world units; 3×3 cell query; candidate `j` visited in ascending order | **[D]** | `2·MAX_RENDERED_NODE_RADIUS + MIN_GAP = 2·14 + 6 = 34` (`GraphStreamLayoutEngine.java:492`). Any two centres closer than `r_i+r_j+MIN_GAP ≤ 34` lie in cells with index difference ≤ 1, so a 3×3 query is complete. The ascending-`j` order preserves the fixture expectations in §5.2. |
| C5 | Node radius in the projection | `8.0 · projection.prominence().get(key).scale()`, default scale `1.0` when absent | **[D]** | `GraphStreamLayoutEngine.java:199-201` uses the same lookup for particle radius. |
| C6 | Projection residual semantics | count of unordered pairs `i<j` with `hypot < r_i+r_j+MIN_GAP`, strict `<`, over returned node positions; includes pinned-pinned pairs; anchors excluded | **[P]** design §5.2 | Recomputable independently; design §8.2.2 asserts equality with an independent
recomputation (acceptance 2 in §7). |
| C7 | Projection wall-clock p95 budget | **not pinned** | **[N]** | The `NodeSeparationProjection` stage does not exist in the repository, so no production baseline can be recorded without a code change; design §8.1.5 requires exactly that baseline first. Reference-implementation calibration on this host (Java 21.0.8, Linux, 22 cores, Appendix A.5): 2000-node legal grid, 1 pass: p95 5,130,482 ns; 2000-node random cloud in a 2000² area: p95 220,769,319 ns; 2000-node dense cluster, 64 passes, all pairs violating: p95 411,584,311 ns. These are algorithm-only numbers, **not** a stage budget. Implementation duty: add the stage to `GraphWorkspacePerformanceDiagnostic`, record its baseline, then fix the threshold. |
| C8 | `SLOT_GAP` | `6.0` screen px | **[P]** design §5.3 | Generator `slotAnchor(..., 6.0)`; the fixture tables in §5.4–§5.6 reproduce only with 6.0. |
| C9 | `DISPLACED_OFFSET` | `30.0` screen px | **[P]** design §5.3 | Generator far anchors add `30.0`; fixtures reproduce only with 30.0. |
| C10 | Per-slot maximum widths | vertical (ABOVE/BELOW/ABOVE_FAR/BELOW_FAR) `200` px; horizontal (RIGHT/LEFT/RIGHT_FAR/LEFT_FAR) `130` px; diagonal (ABOVE_LEFT/ABOVE_RIGHT/BELOW_LEFT/BELOW_RIGHT) `150` px | **[P]** design §5.3, confirmed by measurement | Generator `slotMaxWidth`. Binding evidence [M]: long fixture names measure 246.722–299.606 px at 12 pt and truncate; dense fixture maximum full-text width is 132.601 px ("Foundation / Regularity"), which exceeds the horizontal cap and is therefore placed vertically — exactly what the generator does. |
| C11 | Ladder order and slot order | see §2.7 step 5 and §3 I3 | **[M]** from the committed generator, now also stated in design revision 5.2 | `LADDER_DISPLACEMENT_FIRST` and `NEAR_SLOTS`/`FAR_SLOTS` (`NodeSeparationMockups.java:68-77`, `:186-188`); the fixture tables in §5.4–§5.6 depend on them. Design revision 4's "inward slots first, fixed per-edge order" wording was removed in revision 5.2, which adopts the same single global order; there is no per-edge ordering rule (see §8 O12). |
| C12 | Leader rule | leader line for every slot except `ABOVE` and `BELOW` | **[M]** from the committed generator | `drawScene`: `if (slot == ABOVE || slot == BELOW) continue;`. The design's wording "other than directly above/below" is imprecise for the displaced far slots; this specification pins the generator's observable rule. |
| C13 | Label measurement convention | `screenBounds(text, font) = font.getStringBounds(text, SCREEN_FRC)` with `SCREEN_FRC = new FontRenderContext(null, true, true)`, using the base theme font at its base size (12/9/15 pt); rect anchor `(x,y)` with `w = screenBounds.width`, `h = screenBounds.height` | **[D]** + **[M]**, design §5.3 | Verified equivalent (max delta 2.747e-3 px, Appendix A.2) to `base.deriveFont(size/zoom).getStringBounds(worldFRC) · zoom`, which is what the painter draws. `getStringBounds` height equals `getLineMetrics("Ag").getHeight()` exactly (Appendix A.2). The rejected literal reading "base metrics × zoom" would reserve `91.104675 · 0.25 = 22.776 px` for "Axiom of Choice" at zoom 0.25 while the painter paints ≈90 px of ink — the design §1.2 under-reserve defect. |
| C14 | Painted-ink overhang | the painter centres on the double-precision `getStringBounds` width; glyph ink may extend beyond the logical rectangle by an antialias fringe of at most `0.7578` px per side; **no ink-containment assertion is made** | **[M]** | Measured over the §5.7 fixture set at the real pinned anchors: per-fixture maximum overhang `0.2151` px (long, stand-in variant) to `0.7578` px (dense z=2); no single-side overhang exceeded `0.7578` px (Appendix A.3). Minimum label–label rectangle gap over the same fixtures is `1.655886` px (dense, zoom 1), and `2 · 0.7578 = 1.5156 < 1.655886`, so painted bitmaps remain pairwise disjoint under the double-precision centring and a containment test would be false. The current integer-`FontMetrics.stringWidth` centring (`GraphPainter.java:307-312`) differs from the fractional-metric C13 width by up to `4.601` px (`2.3005` px per side) on the fixture text ("Foundation / Regularity", 12 pt: `132.600922` vs `stringWidth` `128`), and its measured per-side counterfactual overhang reaches `0.7837` px over the set (dense z=2, "Extensionality"), while the integer/fractional width difference alone displaces the ink centre by up to `2.3005` px; because two adjacent labels can each overhang toward the other, the two-sided worst case is `2 · (0.7578 + 2.3005) = 6.117` px, far above `1.655886` px. The double-precision centring change is therefore mandatory and part of §6. |
| C15 | Placement-stage p95 threshold | **not pinned** | **[N]** | The paint-path placement stage does not exist, so the baseline design §8.4 demands cannot be recorded without the code change. The sampling method, population, warm-up and cache states are pinned in §4.6. |
| C16 | Sampling population / warm-up / cache states | population: paint-path placement calls that **miss** the cache, split into four triggers — (a) positions-identity change, (b) zoom change, (c) viewport origin/size change, (d) forced-set change; warm-up 20 discarded samples per trigger; 100 timed samples per trigger; cache state at each timed sample: cold for the measured key, `previous` retained from the immediately preceding call; statistic: nearest-rank p95 per trigger | **[P]** method from design §8.4; numbers pinned here | Warm cache hits are excluded (no work). Pan and zoom each cost one re-placement and are budgeted as one stage. The numeric thresholds follow C15. |
| C17 | Enclosure placement constants | `ARC_GAP = 1.0` px; `EXTERNAL_GAP = 4.0` px; `SUBTLE_EXTERNAL_CANDIDATE_BUDGET = 8`; plus the finite per-edge lane bound of §2.8 Tier C | **[D]** inherited from the deleted `LabelPlacementEngine` constants (`:22-25`) per design revision 5.2 | The old engine's `MAX_INTERIOR_EXPANSION = 8.0` (`:22`) is **not** inherited: the design deletes hull growth. The engine's `EPSILON = 1e-9` (`:26`) is **not** inherited either — the pinned enclosure rule of §2.8 does not use it. The three constants are the ones the pinned rule inherits; the per-edge lane bound is derived in §2.8 Tier C. |

## 2. Interfaces and data structures

All new types are deterministic, immutable where stated, and validated in their constructors.

### 2.1 `org.freeplane.plugin.graph.layout.NodeSeparationProjection` (new, **public**)

```java
public final class NodeSeparationProjection {
    public static final double MIN_GAP = 6.0;      // C1; single declaration site
    static final int MAX_PASSES = 64;              // C2
    static final double RELAXATION = 0.5;          // C3
    static final double SPATIAL_CELL = 34.0;       // C4

    /** Pure. Never mutates the argument. */
    public NodeSeparationResult project(GraphProjection projection, LayoutPositions positions,
            Set<ProjectedNodeKey> pinned);
}
```

Algorithm, pinned to make §5 fixtures derivable:

1. Build the ordered node list from `positions.nodes()` iteration order (an immutable
   `LinkedHashMap` copy, `LayoutPositions.java:34`; this is the deterministic projection
   order, see §8 O2 for the design's "sorted by projected key" wording). Let `n` be its size.
2. `radius[i] = 8.0 · prominence_i.scale()` with default `1.0` (C5). `pinned` is the set of
   pinned node keys; keys not present in `positions` are ignored.
3. For `pass = 1..MAX_PASSES`:
   1. Insert all nodes into a uniform grid with cell size `SPATIAL_CELL` (C4).
   2. For `i = 0..n-1`, query the 3×3 cells around node `i`; for each candidate node `j`
      with `j > i` in the ordered list and ascending `j`, compute
      `d = hypot(x_j−x_i, y_j−y_i)`, `need = radius_i + radius_j + MIN_GAP`.
      If `d == 0`, use the deterministic axis `(1,0)` (the pair is always visited as `i < j`)
      and `d = 1`. If `d >= need`, continue.
      - both pinned: continue (no movement; the pair remains in the residual).
      - exactly one pinned: the movable node moves by the **full** penetration along the
        centre line, away from the pinned node.
      - both movable: each moves by `RELAXATION · penetration` along the centre line, away
        from the other (total relative separation increases by exactly `penetration`).
   3. If the pass moved nothing, stop.
4. Recompute the residual over **all** node pairs on the returned positions (C6); return
   `NodeSeparationResult.of(projectedPositions, residual, passes)` where `passes` is the
   number of iterations executed in step 3 (including the final pass that moved nothing).
5. Anchor entries are copied through unchanged (I1 covers node pairs only; the design does
   not define anchor movement — §8 O3). Every input coordinate is validated finite; a
   non-finite input throws `IllegalArgumentException` (design §5.2). Every output coordinate
   is re-validated finite; otherwise `IllegalArgumentException`.
6. `project` is stateless; it never feeds anything back into the solver.

### 2.2 `org.freeplane.plugin.graph.layout.NodeSeparationResult` (new, **public**)

```java
public final class NodeSeparationResult {
    public static NodeSeparationResult of(LayoutPositions positions, int residualViolations,
            int passes);
    public LayoutPositions positions();
    public int residualViolations();   // invariant: >= 0
    public int passes();               // invariant: 1 <= passes() <= MAX_PASSES
}
```

`passes()` is the test-visible counter required by the convergence fixtures (§5.1, §5.2): it
distinguishes "converged early" from "ran to the cap" at the same residual. It counts every
executed pass, including the final pass that moved nothing. Public because
`org.freeplane.plugin.graph.control.LayoutSettleLoop` must call the projection, so the
`layout` types reachable from `control` are declared public (design §5.2). Never carries
`LayoutFrame.UNVERIFIED`.

### 2.3 `LayoutFrame` — residual field

`LayoutFrame.java` fields currently at `:13-17`, factories at `:33-36` (`of`) and `:38-42`
(`withDiagnostics`).

```java
public final class LayoutFrame {
    public static final int UNVERIFIED = -1;
    public static LayoutFrame of(long stepIndex, LayoutPositions positions, boolean failed);
    public static LayoutFrame of(long stepIndex, LayoutPositions positions, boolean failed,
            int residualViolations);
    public static LayoutFrame withDiagnostics(LayoutFrame raw, List<LayoutConflict> conflicts,
            PerceptualIdlePolicy.IdleMeasurement idle);
    public int residualViolations();
    public boolean verified();          // residualViolations() >= 0
}
```

- The three-argument `of` is defined as the four-argument `of` with
  `residualViolations = UNVERIFIED`, and it is for engine-internal frames only
  (`GraphStreamLayoutEngine.java:346`); `LayoutWorker.accept` rewraps those frames, so they
  are never published. Any call site that publishes must use the four-argument factory with
  the real residual.
- Constructor invariant: `residualViolations >= UNVERIFIED`; published-frame tests require
  `verified()` and `residualViolations()` equal to an independent all-pair recomputation.
- `withDiagnostics` preserves the raw frame's residual.
- `UNVERIFIED` is the encoding of the design's "explicitly marked unknown" (design §5.2 table,
  `EMPTY_FAILED_FRAME` row). It is allowed only for engine-internal frames; `accept` replaces
  it. `EMPTY_FAILED_FRAME` uses `0`, which is the recomputed value for empty positions.

Publication paths and their residual requirements (all `LayoutFrame.of`/`withDiagnostics`
sites, verified). The last row is a republish, not a factory:

| Site | Residual requirement |
|---|---|
| `GraphStreamLayoutEngine.java:346` (engine factory, 3-arg) | `UNVERIFIED`; always re-wrapped by `LayoutWorker.accept` (`:280-282`), never published |
| `LayoutWorker.java:34-35` (`EMPTY_FAILED_FRAME`) | `0` (recomputed over empty positions); 4-arg factory |
| `LayoutWorker.java:292-293` (normal) | real residual from `NodeSeparationResult`; 4-arg factory |
| `LayoutWorker.java:346` (failure, retained frame) | retained frame's `residualViolations()`; 4-arg factory |
| `LayoutSettleLoop.java:744` (worker-failure fallback, `retained == null`) | real residual from projecting `fallbackPositions`; 4-arg factory |
| `LayoutSettleLoop.java:747` (worker-failure fallback) | retained residual when the retained frame is usable; otherwise the residual of the projected `fallbackPositions`; 4-arg factory |
| `GraphUpdateCoordinator.java:134-136` (initial empty frame) | `0` (recomputed over empty positions); 4-arg factory |
| `GraphUpdateCoordinator.java:561` (**republish**, not a factory) | preserves the residual already carried by `state.layout()` |

This is the corrected, complete publication-path enumeration (the design's §5.2 table
identifies the construction sites without distinguishing the republish; the engine factory
at `GraphStreamLayoutEngine.java:346` is re-wrapped by `accept` before publication, and
`GraphUpdateCoordinator.java:561` republishes rather than constructs).

### 2.4 `LabelPlacementRequest` (new, `org.freeplane.plugin.graph.canvas`, public)

```java
public final class LabelPlacementRequest {
    public static LabelPlacementRequest of(GraphProjection projection, GraphGeometry geometry,
            double zoom, Rectangle2D placementArea, Set<ProjectedEndpointKey> forced,
            RenderingLevel renderingLevel);
    public GraphProjection projection();
    public GraphGeometry geometry();
    public double zoom();                       // > 0
    public Rectangle2D placementArea();         // screen space, inside the painted surface
    public Set<ProjectedEndpointKey> forced();  // immutable
    public RenderingLevel renderingLevel();
}
```

`LabelPlacementRequest` is the input of one placement pass. Obstacles are derived inside the
pass from `geometry` (discs) and from enclosure labels placed in the same pass; the request
itself does not carry an obstacle set. `RenderingLevel` affects only the result filter
(`OVER_TARGET`, §3 I3); it does not change which fonts the ladder selects.

### 2.5 `PlacedLabel` — the placement result (new, `org.freeplane.plugin.graph.canvas`, public)

```java
public final class PlacedLabel {
    public enum Mode { INTERIOR, ARC, EXTERNAL, HOVER_ONLY }   // moved from LabelPlacement.Mode
    public enum Rung { FULL_NEAR, FULL_DISPLACED, DENSE_NEAR, DENSE_DISPLACED,
                       TRUNCATED_NEAR, TRUNCATED_DISPLACED, TRUNCATED_DENSE_NEAR,
                       TRUNCATED_DENSE_DISPLACED, HOVER_ONLY }

    public ProjectedEndpointKey endpoint();     // unique within a placement result
    public String text();                       // possibly truncated
    public Font font();                         // base-size screen font actually used
    public Mode mode();
    public Rung rung();
    public double anchorX();                    // screen
    public double anchorY();                    // screen
    public double width();                      // screen logical bounds width
    public double height();                     // screen logical bounds height
    public Rectangle2D bounds();                // anchor-centred screen rectangle
    public boolean truncated();
    public boolean forced();
    public boolean emphaticAtAnchor();          // I2 exception: emphatic enclosure at the hull anchor
    public boolean forcedAtBaseSlot();          // I2 exception: O4 forced label at its base slot
    public boolean fullTextSlotWasFree();       // predicate input for §5.10
    public Optional<LayoutPoint> leaderStart(); // screen; present iff a leader is drawn
}
```

The painter paints `text()` with `font()` verbatim (derived by `/zoom` under the world
transform) and must not re-select a font; `LabelPlacement.Mode` is deleted with
`LabelPlacement` and only this enum survives. Single-line labels: `height()` is one line high
(design §4 I2). `Mode.ARC` has a producer: an enclosure label placed in an arc slot on a hull
edge (§2.8, Tier B), including the case where the hull anchor rectangle collides.
`fullTextSlotWasFree()` records whether,
when the label was processed, any full-text rung candidate the label was allowed to try
(rungs 1–2 for forced labels, rungs 1–4 for non-forced labels of §2.7 step 5: full font or
dense font, near or far) was inside `placementArea` and collision-free against the
then-current obstacle set; it is necessarily `false` for every `truncated()` label (the §5.10
ordering assertion) and for every O4 base-slot label. `emphaticAtAnchor()` and
`forcedAtBaseSlot()` are the two I2 exceptions of §3 I2 recorded on the result (design §4
I3/§7; O4); both are never thrown from the paint path, and a label with either flag is
still a placed label that contributes its rectangle to the obstacle set.

### 2.6 `LabelFonts` (new, `org.freeplane.plugin.graph.canvas`, public)

```java
public final class LabelFonts {
    public static LabelFonts from(GraphTheme theme);
    public Font full();       // theme.labelFont()        = SansSerif plain 12 (GraphTheme.java:67)
    public Font dense();      // theme.denseLabelFont()   = SansSerif plain 9  (GraphTheme.java:69)
    public Font emphatic();   // theme.emphaticLabelFont()= SansSerif bold 15  (GraphTheme.java:68)
}
```

Node labels use `full()` for rungs 1–2 and 5–6 and `dense()` for rungs 3–4 and 7–8 (§2.7
step 5). Enclosure labels use `full()` when subtle and `emphatic()` when emphatic; they never
use `dense()` and never truncate (§2.8). The theme's 7 pt `overTargetLabelFont`
(`GraphTheme.java:70`) is never selected: at `RenderingLevel.OVER_TARGET` placement returns
only forced labels (and required emphatic enclosure labels), so no label carries the 7 pt
face. The `labelFont(RenderingLevel)` overload (`GraphTheme.java:262-270`) and
`overTargetLabelFont` (`GraphTheme.java:258-260`) become unreferenced after the painter
migration (§6).

### 2.7 `ScreenLabelPlacement` and `ScreenLabelPlacementCache`

```java
public final class ScreenLabelPlacement {
    public List<PlacedLabel> place(LabelPlacementRequest request, List<PlacedLabel> previous,
            LabelFonts fonts);
}

public final class ScreenLabelPlacementCache {
    public List<PlacedLabel> place(LabelPlacementRequest request, LabelFonts fonts);
}
```

`place` is deterministic in `(request, previous)`. `previous` may be `null`/empty.

Placement algorithm (pinned; it is the committed generator's algorithm, which §5 fixtures
reproduce, extended with the retention rule of I4 and the enclosure rule of §2.8):

1. Disc obstacles: for every `ProjectedNode` with geometry, the screen bounding square of the
   disc, centre `(W/2 + zoom·(x − centerX), H/2 + zoom·(y − centerY))`, half-extent
   `max(2.0, r·zoom)`.
2. Enclosure-label reservations, seed obstacles supplied to the package-private test overload
   (§2.7 step 13), and previously accepted labels join the obstacle set.
3. Process labels in priority order:
   1. forced labels, then
   2. enclosure labels (emphatic → subtle, in `projection.enclosures()` order and, within an
      enclosure, `endpointKeys()` order), then
   3. non-forced node labels.
   Within the forced group and within the non-forced node-label group, order is descending
   disc radius, ties broken by the `LayoutPositions.nodes()` iteration order of §2.1 step 1
   (this is what §8 O2 pins; "geometry order" in earlier drafts means exactly this order).
   The design's category sub-order "selected → hovered → related" is dropped because the
   request carries a `Set` and cannot recover the categories; the pinned total order is the
   one every §5 fixture reproduces (§8 O14).
4. Retention (I4). For a label with a `previous` entry whose `mode != HOVER_ONLY`: recompute
   the previous slot's anchor at the current centre with the previous text/font/rung; if that
   rectangle is inside `placementArea` and collision-free, keep it (same slot, text, font and
   rung). Otherwise, or when there is no usable previous entry, run the ladder. A `previous`
   `HOVER_ONLY` entry is not an obstacle and is not retained; it is re-laddered.
5. Ladder order (rung → candidate slots):
   1. `FULL_NEAR` — full text, `full()` font, slots `ABOVE, BELOW, RIGHT, LEFT, ABOVE_RIGHT, ABOVE_LEFT, BELOW_RIGHT, BELOW_LEFT`
   2. `FULL_DISPLACED` — full text, `full()` font, slots `ABOVE_FAR, BELOW_FAR, RIGHT_FAR, LEFT_FAR`
   3. `DENSE_NEAR` — full text, `dense()` font, near slots
   4. `DENSE_DISPLACED` — full text, `dense()` font, far slots
   5. `TRUNCATED_NEAR` — truncated, `full()` font, near slots
   6. `TRUNCATED_DISPLACED` — truncated, `full()` font, far slots
   7. `TRUNCATED_DENSE_NEAR` — truncated, `dense()` font, near slots
   8. `TRUNCATED_DENSE_DISPLACED` — truncated, `dense()` font, far slots
   **Forced labels use only rungs 1–2.** If neither accepts, the O4 rule applies: the label
   is placed at its base slot — the `ABOVE` slot of rung 1 at `SLOT_GAP` (C8), with anchor
   and size per step 8 — without testing `placementArea` or collisions. The result records the
   I2 exception (`forcedAtBaseSlot() == true`), and the base-slot rectangle joins the obstacle
   set exactly like an accepted candidate, so later labels avoid it. Non-forced labels use all
   eight rungs.
6. Candidate acceptance: `textWidth(text, font) <= slotMaxWidth(slot)` for non-truncating
   rungs; for truncating rungs `truncateTo(text, font, slotMaxWidth(slot))`; the rectangle must
   lie inside `placementArea` and intersect no obstacle. Accepted rectangles, and the O4
   base-slot rectangle, join the obstacle set. The `fullTextSlotWasFree()` flag of §2.5 is set
   from the union of the full-text rungs the label tried (rungs 1–2 for forced labels, rungs
   1–4 otherwise).
7. `truncateTo`: if the full text fits, return it; otherwise try cut lengths from
   `len−1` down to `3`, candidate `text.substring(0,cut).stripTrailing() + "…"`, first that
   fits; fallback `text.substring(0, min(3,len)) + "…"`.
8. Slot anchors (screen, `r' = max(2, r·zoom)`, `w`/`h` the measured logical box; `gap = C8`,
   `displaced = C9`):

   | Slot | anchor |
   |---|---|
   | `ABOVE` | `(cx, cy − r' − gap − h/2)` |
   | `BELOW` | `(cx, cy + r' + gap + h/2)` |
   | `RIGHT` | `(cx + r' + gap + w/2, cy)` |
   | `LEFT` | `(cx − r' − gap − w/2, cy)` |
   | `ABOVE_FAR` | `(cx, cy − r' − displaced − h/2)` |
   | `BELOW_FAR` | `(cx, cy + r' + displaced + h/2)` |
   | `RIGHT_FAR` | `(cx + r' + displaced + w/2, cy)` |
   | `LEFT_FAR` | `(cx − r' − displaced − w/2, cy)` |
   | `ABOVE_RIGHT` | `(cx + r' + gap + w/2, cy − r' − gap − h/2)` |
   | `ABOVE_LEFT` | `(cx − r' − gap − w/2, cy − r' − gap − h/2)` |
   | `BELOW_RIGHT` | `(cx + r' + gap + w/2, cy + r' + gap + h/2)` |
   | `BELOW_LEFT` | `(cx − r' − gap − w/2, cy + r' + gap + h/2)` |

9. Candidate slots are tried in the fixed order given in step 5; a candidate outside
   `placementArea` is rejected and the next candidate is tried. There is no per-edge ordering
   rule and no inward-first rule (C11, §8 O12).
10. Terminal rules. Enclosure labels follow §2.8, including `INTERIOR`, `ARC`, `EXTERNAL`,
    `HOVER_ONLY` and the emphatic-anchor exception. A non-forced node label with nothing left
    is `HOVER_ONLY` (no obstacle, not painted except when forced). A forced node label with
    nothing left in rungs 1–2 takes the O4 base-slot rule of step 5.
11. `RenderingLevel.OVER_TARGET` filters the result: only forced labels and required emphatic
    enclosure labels are returned; all other labels are absent. `FULL` and `DENSE` produce the
    same placement result (the level does not select fonts; `PlacedLabel.font()` does).
12. `mode = HOVER_ONLY` labels are returned but are not obstacles and are not painted except
    when forced by paint state.
13. Test seam. `ScreenLabelPlacement` additionally exposes the package-private overload
    `place(request, previous, fonts, seedObstacles)`; the public three-argument overload
    delegates with an empty list. `seedObstacles` are extra screen-space obstacle rectangles
    used only by the fixtures that exercise the generator-derived stand-in of §5.4–§5.7
    (`hullLabelRect(scene, zoom, 0, 0)`); production never passes them and they are never
    returned as labels.

Derived displacement quantities (used by the design §8.3.6 assertion, restated in §5.10;
`r' = max(2, r·zoom)`, `w`/`h` the placed logical box). Enclosure labels are exempt (their
anchor rules are §2.8):

| Slot | leader length (disc centre → anchor) | corner support (disc centre → farthest corner) |
|---|---|---|
| `ABOVE`, `BELOW` | `r' + 6 + h/2` | `hypot(w/2, r' + 6 + h)` |
| `ABOVE_FAR`, `BELOW_FAR` | `r' + 30 + h/2` | `hypot(w/2, r' + 30 + h)` |
| `RIGHT`, `LEFT` | `r' + 6 + w/2` | `hypot(r' + 6 + w, h/2)` |
| `RIGHT_FAR`, `LEFT_FAR` | `r' + 30 + w/2` | `hypot(r' + 30 + w, h/2)` |
| diagonal | `hypot(r' + 6 + w/2, r' + 6 + h/2)` | `hypot(r' + 6 + w, r' + 6 + h)` |

Structural maxima at zoom 1 with the C10 caps (`h = 16.344114`): `r'=8` → corner support
`≤ 168.199 px` (horizontal far); `r'=14` → `≤ 174.192 px` (horizontal far, vs `173.842 px`
diagonal and `116.796 px` vertical far). The design's single-form bound must not be used
(§8 O7); the fixture assertion uses the per-slot forms above.

### 2.8 Enclosure-label placement rule (pinned)

The design (revision 5.2) states the shape of the enclosure path and delegates the constants
to this specification; the rule below inherits the deleted `LabelPlacementEngine`'s constants
(C17) and drops only hull growth (`MAX_INTERIOR_EXPANSION`). Enclosure labels are processed
after forced labels and before node labels (§2.7 step 3). For each enclosure label:

- **Fonts.** Subtle enclosure labels use `LabelFonts.full()`; emphatic enclosure labels use
  `LabelFonts.emphatic()`. Enclosure labels are never truncated and never use `dense()`.
- **Tier A — `INTERIOR`.** Candidate rectangle centred on `hull.labelAnchor()`. Accept when it
  lies inside `placementArea`, lies inside the hull polygon (all four corners,
  `HullGeometry.contains`) and intersects no obstacle. Result mode `INTERIOR`, `leaderStart`
  empty. **No hull growth**: the hull is never expanded, unlike the deleted engine's
  `MAX_INTERIOR_EXPANSION` path.
- **Tier B — `ARC`.** Candidates are the hull's edges (`HullGeometry.exactPolygon()` in
  canonical order), sorted by (population ascending, edge length descending, edge index
  ascending). The population of an edge is the number of occupied rectangles whose projection
  on the edge's tangent overlaps `[minT, maxT]` and whose projection on the edge's outward
  normal overlaps `[support − depth, support + depth]`, where
  `depth = height + ARC_GAP` and `support` is the hull's support along that edge's outward
  unit normal, `support = max { outwardNormal·v : v ∈ hull.exactPolygon() }` (for a convex
  hull this equals `outwardNormal·start`; the definition is restated here because the deleted
  `LabelPlacementEngine.Edge.support` is gone). For the chosen edge, candidate anchor =
  `midpoint(start,end) + inwardNormal · (height/2 + ARC_GAP)`. Accept the first candidate that
  lies inside `placementArea`, lies inside the hull (all four corners) and intersects no
  obstacle. Result mode `ARC`, `leaderStart` empty. This is the producer of
  `PlacedLabel.Mode.ARC`.
- **Tier C — `EXTERNAL`.** Edges are visited in canonical polygon order; for each edge, lanes
  `lane = 0, 1, 2, …` give candidate anchor =
  `midpoint(start,end) + outwardNormal · (height/2 + EXTERNAL_GAP + lane · (height + EXTERNAL_GAP))`.
  A candidate is accepted when the anchor is outside the hull, the candidate rectangle lies
  inside `placementArea`, and the rectangle intersects no obstacle. The lane loop is **finite**
  by construction: because `height + EXTERNAL_GAP > 0` the candidate moves strictly outward,
  so an edge is abandoned at the first lane whose candidate rectangle lies entirely outside
  `placementArea` on the outward side (the rectangle's inward edge beyond the area's outward
  extent along the outward normal); all later lanes are strictly farther out and cannot be
  accepted. No edge evaluates more than
  `ceil((placementArea.width + placementArea.height) / (height + EXTERNAL_GAP)) + 2` lanes,
  which bounds the whole search for any `placementArea` of finite extent. For **subtle**
  labels the search also stops after `SUBTLE_EXTERNAL_CANDIDATE_BUDGET = 8` candidates in
  total (edge order, then lane order). For **emphatic** labels all edges are searched until
  acceptance or until every edge is abandoned by the outward-side rule; the deleted engine's
  "until the lane distance stops increasing" floating-point guard is **not** inherited.
  `leaderStart = hull.nearestBoundaryPoint(anchor)`; the result mode is `EXTERNAL` and
  `leaderStart` is present.
- **Terminal rules.** A subtle enclosure label with no accepted candidate is `HOVER_ONLY` at
  `hull.labelAnchor()`, with `leaderStart` empty and not an obstacle. An emphatic enclosure
  label whose finite lane search is exhausted without acceptance is placed at
  `hull.labelAnchor()` with `emphaticAtAnchor = true`, `leaderStart` empty and no exception —
  the I2 exception of design §4 I3, recorded on the result and never thrown. Exhaustion of the
  finite search is the only way the emphatic terminal rule is reached; the search cannot loop
  on floating-point progress.
- Suppressed enclosures (`BoundaryTier.SUPPRESSED`) have no labels at all.

### 2.9 Spring rest lengths, seeds and calibration (design §5.1)

The design's §5.1 details are restated here because the specification pins them:

- **`max_r`** of an enclosure anchor is the largest disc radius among that anchor's direct
  node children (`radius = 8.0 · prominence scale`), or `0` when it has none.
- **Content ring radius**: `R(k) = (2·max_r + MIN_GAP) / (2·sin(π/k))` for `k ≥ 2`, and
  `R(k) = 0` for `k ≤ 1` (the `k = 0` empty-enclosure ring is undefined but never needed).
  Verified: the adjacent chord of a regular k-gon is `2R·sin(π/k)`.
- **Relationship links**: rest length `radiusOf(first) + radiusOf(second) + MIN_GAP`, where
  `radiusOf` is the disc radius for a node endpoint and `R(k)` for an enclosure-anchor
  endpoint. This covers node–node, node–anchor and anchor–anchor links with one rule
  (`GraphStreamLayoutEngine.java:230`). `TypedSpringBox.REST_LENGTH` becomes unused and is
  deleted.
- **Containment links** (anchor → direct child) use the same `R(k)`
  (`GraphStreamLayoutEngine.java:239`). For `k = 1` the child converges onto the anchor: the
  spring with `restLength = 0` pulls an off-centre child inward (`TypedSpringBox.java:91`,
  behaviour at `:105-111`) and the zero-distance fallback is the only separator. The pair is
  not a disc pair, so I1 does not apply and no `NaN` arises.
- **Calibration multipliers are unchanged.** `LayoutCalibration.containment()`,
  `hierarchy()` and `sameMap()` keep their current values and ordering; anchor-to-anchor
  hierarchy links keep `GROUP_SPACING` / `SUB_GROUP_SPACING`.
- **Seeds** (`GraphStreamLayoutEngine.java:670-687`, currently
  `sizes.directNodeRingRadius(parentKey)` at `:682`) call the same disc-derived helper, so the
  label-aware seed (≈134 for "Axiom of Choice") and `R(2) = 17` no longer disagree by 7.9×.
  `BoundarySizes.directNodeRingRadius` stays for
  `directNodeReach → sizeOf → boundaryRadius` and `topRingPosition` (N4; `:558`).

Settled positions change, so seeds, step counts and fixture expectations move (design §10).

### 2.10 `ScreenLabelPlacementCache` key

Key equality pins the design's §5.4 list:

| Key component | Type / equality |
|---|---|
| projection generation | `long`, `==` |
| positions identity | `LayoutPositions` by reference (`==`) — the design says "positions identity" |
| zoom | `double`, `Double.compare` |
| viewport origin and size | `int x, y` and `int width, height`, `==` |
| forced set | immutable `Set<ProjectedEndpointKey>`; set equality (order-independent "digest" semantics) |
| rendering level | `RenderingLevel` enum identity |
| font/theme identity | the three `java.awt.Font` values from `LabelFonts` (`Font.equals`) |

`ScreenLabelPlacementCache` keeps the last key and the last `List<PlacedLabel>` regardless of
key; on a miss it calls `ScreenLabelPlacement.place(request, previous, fonts)` with the
retained list, so I4 stickiness survives layout changes. Cache hits return the cached list.
The forced set is a `Set`, and the processing order of §2.7 step 3 is derived deterministically
from it (descending disc radius, then `LayoutPositions.nodes()` order), so order-independent
set equality is a sufficient key; no sequence component is required (§8 O14).

## 3. Contracts I1–I4, restated as checkable statements

**I1 — disc separation (world space).** For a published frame with positions `P`:
`∀ i<j ∈ nodes(P): hypot(c_i − c_j) ≥ r_i + r_j + MIN_GAP`, **or** `residualViolations > 0`,
where `residualViolations` is exactly the number of violating pairs (C6). Exception:
pinned-pinned pairs are never moved by `NodeSeparationProjection` and are counted in the
residual even when the projection converges otherwise. Checkable: an independent all-pair
recomputation over the published positions must equal `LayoutFrame.residualViolations()`
(design §8.2.2); the red-phase fixture of §5.1 asserts `residualViolations == 0` after a
violating input was displaced.

**I2 — text separation (screen space, per rendered frame).** For every rendered frame at zoom
`z` and painted surface `A`:
1. every drawn label rectangle is disjoint from every other drawn label rectangle
   (`Rectangle2D.intersects == false`, strict);
2. every drawn label rectangle is disjoint from every disc's screen bounding rectangle;
3. every drawn label rectangle is inside `A` (never silently clipped);
4. rectangles are the screen-space logical bounds of the carried base font (C13).
   The measurable consequence is tested by painting ink: pairwise label ink bitmaps must not
   intersect, and each label's ink bitmap must not intersect any disc bitmap, at zooms 0.25,
   1.0 and 2.0. **Ink is not asserted to lie inside the rectangle** — antialiased glyph ink
   extends beyond the logical box by the C14 fringe, measured at 0.2151–0.7578 px per side
   over the fixture set, while the minimum fixture rectangle gap is 1.655886 px (C14,
   Appendix A.3). The sanctioned I2 exceptions are the emphatic-enclosure exception and the
   forced base-slot exception, recorded on the `PlacedLabel` (`emphaticAtAnchor == true`,
   `forcedAtBaseSlot == true`); a flagged label is exempt from clauses 1–3, remains a placed
   label and contributes its rectangle to the obstacle set. Labels are single-line, so
   `height` is one line.

**I3 — degradation ladder (ordered).**
`FULL_NEAR → FULL_DISPLACED → DENSE_NEAR → DENSE_DISPLACED → TRUNCATED_NEAR →
TRUNCATED_DISPLACED → TRUNCATED_DENSE_NEAR → TRUNCATED_DENSE_DISPLACED → HOVER_ONLY`.
Forced labels (selected, hovered, related) are placement inputs, placed first, and are
restricted to the two full-text rungs (`FULL_NEAR`, `FULL_DISPLACED`); a forced label never
uses the dense or truncated rungs. A forced label whose two full-text rungs do not accept is
the O4 case: it is placed at its base slot with the I2 exception recorded on the result
(`forcedAtBaseSlot`), and its rectangle joins the obstacle set (§2.7 step 5). Emphatic
enclosure labels are required; when no slot exists they are forced at
the hull label anchor as the I2 exception, recorded **on the placement result**
(`emphaticAtAnchor`) and never thrown from the paint path. At `RenderingLevel.OVER_TARGET`
only forced labels and required emphatic enclosure labels are placed; all other labels are
absent from the result (the 7 pt face therefore never paints).

**I4 — stability.** `place(request, previous, fonts)` is deterministic: identical inputs give
byte-identical results (same anchors, sizes, fonts, texts, slots). A label keeps its previous
slot while that slot remains collision-free and inside `placementArea` at the current
centre/zoom/area (§2.7 step 4); an invalidated slot is re-placed through the ladder, and
re-running with the new result as `previous` is a fixed point while the new slot remains
valid.

## 4. Pipeline, state transitions and error behaviour

### 4.1 Frame lifecycle

| Stage | Producer | Positions | Residual |
|---|---|---|---|
| solver frame | `GraphStreamLayoutEngine` | particle positions | `UNVERIFIED` |
| acceptance | `LayoutWorker.accept` (`:276-300`) | validate coverage → compute hulls → `MapTierCorrection` → **`NodeSeparationProjection.project`** → `PerceptualIdlePolicy.observe` | recomputed |
| idle observation | `LayoutWorker.accept` (`:291`) | observes `(previousProjected, projected)` | unchanged |
| publication state | `LayoutSettleLoop` (`:543-549`, `:721-723`) | hulls recomputed from **published** positions; no label placement here | carried |
| paint placement | `GraphCanvas.paintComponent` (`:358-365`) | nothing in the geometry | n/a |

`LayoutWorker.accept` order is normative: `MapTierCorrection` first, then
`NodeSeparationProjection`, then `PerceptualIdlePolicy.observe`, then frame construction.
Nothing that moves positions runs after the projection; hulls published to the canvas are
recomputed from the projected positions (`LayoutSettleLoop`), so I1 is the user-facing rule
and hull overlap (design N5) is not claimed.

### 4.2 Pinned set

`LayoutWorker.accept` derives the pinned set from `request.pins()`:
`{p.projectedNode().get() : p.active()}` (`PinProjection.active()`, `projectedNode()`).
`LayoutSettleLoop` uses the same derivation from `run.request.pins()` for the fallback path.

### 4.3 Worker-failure and fallback paths

1. **Engine/worker failure.** `LayoutWorker.runSubmit`/`runStep` catch `RuntimeException`
   (including a projection `IllegalArgumentException`) → `failedEngine = true` →
   `failedFrame`. `failedFrame` returns `EMPTY_FAILED_FRAME` (residual `0`) when nothing was
   ever accepted, otherwise a failed frame with the retained positions and the retained
   residual (`LayoutWorker.java:340-348`).
2. **Settle-loop failure.** `LayoutSettleLoop.failedFrame` (`:732-749`):
   - retained frame usable (covers the projection) → retained positions and retained residual;
   - otherwise (or `retained == null`) → `fallbackPositions` (`:933-948`) is routed through
     `NodeSeparationProjection` with the current pinned set and the resulting residual is used.
   The frame is still published (status `FAILED`); frames are never dropped.
3. **Failure republish.** `GraphUpdateCoordinator.publishFailure` (`:551-566`) publishes
   `state.layout()` (or `state.withStatus(FAILED)`) and preserves its residual.
4. **Engine-internal frames** are never published: `accept` re-wraps `raw.failed()` frames
   (`LayoutWorker.java:280-282`).

### 4.4 Non-finite geometry

Every coordinate in the input positions and in the returned positions is checked
`Double.isFinite`; a violation throws `IllegalArgumentException` at the projection boundary.
This mirrors the existing guards at `GraphStreamLayoutEngine.java:324` and `:336-337` and
`PerceptualIdlePolicy.java:95-99`. Existing frame construction already rejects non-finite
coordinates (`LayoutFrame.java:73-80`).

### 4.5 Non-convergence reporting

After the last pass the residual is recomputed over all pairs and published. A non-converging
configuration yields `residualViolations > 0`; no exception, no dropped frame. The residual
always describes the published positions, including pinned-pinned pairs and any two-sided
configuration that did not settle. Fixture: §5.2 S1.

### 4.6 Placement performance measurement

The population, warm-up, samples and cache states are C16. Each timed sample is a cache miss
on the measured trigger with `previous` retained. Recorded and gated per scenario
(`two-map`, `three-map-clustered`, `reference-2000-5000`) after the new
`PerformanceMeasurements.Stage.PLACEMENT("placement")` stage replaces the removed worker-side
`LABEL("label")` stage (`PerformanceMeasurements.java:27-39`; the removed measurements are the
`GraphWorkspacePerformanceDiagnostic` worker-side label timing at `:293-297` and the
direct-probe label timing at `:354-358`). The stage is initially diagnostic-only (`-1`); the
threshold is set from the recorded baseline (C15).

## 5. Fixtures and expected values

All fixture coordinates below are world coordinates of the committed mockup generator unless
stated otherwise; convert to screen with §0's mapping. Fonts are the screen-space base fonts
`full = SansSerif plain 12`, `dense = SansSerif plain 9`; line heights measured for this
environment are `h(12)=16.344114`, `h(9)=12.258085`, `h(10)=13.619987`, `h(15 bold)=20.430143`
px. Pass counts are asserted through `NodeSeparationResult.passes()` (§2.2).

### 5.1 Red-phase prominence fixture (`NodeSeparationProjectionShould`)

Construction (edge-constructed prominence only; `GraphProjection` exposes no caller-supplied
prominence):

- Two prominent nodes `p1`, `p2` in the same map, each with **14 distinct visible outgoing
  FORWARD relationship targets** (28 target nodes total; targets placed on a far grid with
  spacing ≥ 100 so no other pair violates).
- `NodeProminence.of(14).scale() = 1.75` → radius `14.0`; verified `NodeProminence.of(13).scale() = 1.740088`
  (radius 13.920704), hence the threshold "≥ 14" is exact.
- Raw positions: `p1 = (0,0)`, `p2 = (24,0)`.

Expected:
- `violationCount(raw) == 1`.
- After `project`: `p1 = (−5,0)`, `p2 = (29,0)`, both radii 14, centre distance exactly
  `34.0`; `result.residualViolations() == 0`; `result.passes() == 2`;
  `violationCount(result.positions()) == 0`. The already-satisfied half of the test asserts
  that an input with distance `≥ 34` is returned unchanged.

### 5.2 Sandwich, pinned-pinned, solvable and coincident fixtures (`NodeSeparationProjectionShould`)

Keys must sort in the listed order under the pinned comparator (same map UUID, ascending
persisted node id). All radii are 8, so `need = 22`.

| Fixture | Input (world) | Pins | Expected |
|---|---|---|---|
| **S1 sandwich (non-convergence)** | `a=(0,0)`, `b=(40,0)`, `m=(20,0)` | `a`, `b` | 64 passes (the cap, no early exit); `a=(0,0)`, `b=(40,0)` bit-identical; `m=(18,0)`; `residualViolations == 1` (pair `a`–`m`); all positions finite; the published frame carries the same count (design §8.1.6) |
| **S2 pinned-pinned exception** | `a=(0,0)`, `b=(20,0)`, `m=(10,0)` | `a`, `b` | 2 passes; `a=(0,0)`, `b=(20,0)` bit-identical; `m=(42,0)`; `residualViolations == 1` (pair `a`–`b`); pinned pair untouched and counted (design §8.1.2) |
| **S3 solvable convergence** | `a=(0,0)`, `b=(20,0)` | none | 2 passes; `a=(−1,0)`, `b=(21,0)`; `residualViolations == 0`; distance exactly 22 |
| **S4 coincident particles** | `a=(0,0)`, `b=(0,0)` | none | 3 passes; the deterministic axis `(1,0)` separates them to `a=(−11,0)`, `b=(11,0)`; `residualViolations == 0`; two repeated runs are byte-identical (design §8.1.4) |

The S1/S2 numbers depend on the processing order of §2.1 step 1 and on C3. Verified: with the
pinned order `a,b,m` S1 ends at `m=(18,0)` and S2 at `m=(42,0)`, and with order `b,a,m` S1
ends at `m=(22,0)` and S2 at `m=(−22,0)` (Appendix A.5). The `b,a,m` values are **not** the
fixture: the fixture keys are constructed so the pinned comparator yields the listed order.
(An earlier draft claimed order `m,a,b` changes the outcome; it does not, and that claim is
withdrawn.)

### 5.3 Three-map correction characterization (design §8.2.4)

- Three root hulls: squares of half-extent **30** centred at `(0,0)`, `(40,0)`, `(−40,0)`;
  one node per map at the hull centre; no pins.
- Expected correction output (verified against the real `HullGeometry` + `HullIntersection`,
  Appendix A.4): `(0,0)`, `(50,0)`, `(−50,0)`; recomputed A/B `minimumSeparatingTranslation`
  `= (10,0)`; `siblingOverlap(A,B) == true`; hence hull overlap **10**.
- Secondary characterization: half-extent **24** gives `(0,44,−44)`; half-extent **1** is a
  no-op. The literal one-node-per-map default half-extent is 24 (design §8.2.4), so the ±30
  fixture must hand-build its hulls.
- No assertion is made about hull overlap after the projection (design N5).

### 5.4 Dense 12-node fixture

World positions (spacing 34; radius 14 for indices 0,3,6,9, else 8; index 1 is selected):

| # | name | world | # | name | world |
|---|---|---|---|---|---|
| 0 | Theorem | (−51,−34) | 6 | Power Set | (17,0) |
| 1 | Axiom of Choice *(selected)* | (−17,−34) | 7 | Infinity | (51,0) |
| 2 | Replacement Scheme | (17,−34) | 8 | Separation | (−51,34) |
| 3 | Extensionality | (51,−34) | 9 | Foundation / Regularity | (−17,34) |
| 4 | Pairing | (−51,0) | 10 | Comprehension | (17,34) |
| 5 | Union | (−17,0) | 11 | Well-Ordering | (51,34) |

Expected placement at zoom 1, viewport 1128×364, with the enclosure obstacle
`(−76, −79.619987, 148.289871, 13.619987)` in world-centred coordinates. This rectangle is
the committed generator's synthetic `hullLabelRect(dense, 1, 0, 0)`, **not** the output of
the §2.8 pinned enclosure rule; it is a generator-derived stand-in injected through the
package-private `seedObstacles` seam of §2.7 step 13, and the assertions below scope it to
"node labels avoid this fixed rectangle". For any zoom `z` the stand-in is
`hullLabelRect(scene, z, 0, 0)`, defined in world-centred screen coordinates by
`x = min_i(z·x_i − z·r_i) − 11`, `y = min_i(z·y_i − z·r_i) − 18 − h10`, width
`148.289871 = textWidth("Basic Definitions and Theorems", 10 pt)`, height
`13.619987 = h10` (the 10 pt line height). This is the only per-zoom definition used by
§5.7; no scaled zoom-1 rectangle is used:

| order | name | slot | font | text | anchor (world-centred) | size (w×h) |
|---|---|---|---|---|---|---|
| 1 | Axiom of Choice *(forced, rung 1)* | ABOVE | 12 | full | (−17, −56.172057) | 91.104675×16.344114 |
| 2 | Theorem | LEFT | 12 | full | (−96.530182, −34) | 51.060364×16.344114 |
| 3 | Extensionality | RIGHT | 12 | full | (110.216270, −34) | 78.432541×16.344114 |
| 4 | Power Set | RIGHT_FAR | 12 | full | (89.242210, 0) | 56.484421×16.344114 |
| 5 | Foundation / Regularity | BELOW | 12 | full | (−17, 62.172057) | 132.600922×16.344114 |
| 6 | Replacement Scheme | ABOVE_RIGHT | 12 | full | (91.672424, −56.172057) | 121.344849×16.344114 |
| 7 | Pairing | LEFT | 12 | full | (−84.968140, 0) | 39.936279×16.344114 |
| 8 | Infinity | BELOW_RIGHT | 12 | full | (84.836136, 22.172057) | 39.672272×16.344114 |
| 9 | Separation | LEFT | 12 | full | (−95.630211, 34) | 61.260422×16.344114 |
| 10 | Comprehension | BELOW_FAR | 12 | full | (17, 80.172057) | 90.288651×16.344114 |
| 11 | Well-Ordering | BELOW_RIGHT | 12 | full | (104.654289, 56.172057) | 79.308578×16.344114 |
| — | Union | — | — | HOVER_ONLY | — | — |

Histogram `full 11 | dense 0 | truncated 0 | hover-only 1`; label/label collisions 0;
label/disc collisions 0. Other measured rows of the same scene (same stand-in obstacle):
420×240 → `full 11 / hidden 1`; 280×170 → `full 7 / dense 4 / hidden 1`; 200×130 →
`full 2 / dense 4 / hidden 6`. Mean leader length at 1128×364 zoom 1 is 48.046026 px,
maximum 77.894615 px, leader crossings 0; the zoom and level matrix is §5.9 and the measured
per-slot bounds are in §5.10.

### 5.5 Long-label truncation fixture

Six nodes, all radius 8, positions `((i%3−1)·22, (i/3)·22)` for `i=0..5`; node 0 is selected
and therefore **forced**. Names and measured 12 pt widths:

| # | name | width (px) |
|---|---|---|
| 0 | Well-Ordering Theorem of Choice and Regularity | 274.130005 |
| 1 | Axiom Schema of Replacement and Comprehension | 292.586090 |
| 2 | Transfinite Induction over Ordinal Numbers | 246.722 |
| 3 | Cardinal Arithmetic under the Continuum Hypothesis | 299.606 |
| 4 | Ultrafilter Lemma and Boolean Prime Ideal Theorem | 295.478 |
| 5 | Kuratowski Zorn Lemma for Partially Ordered Sets | 283.034 |

Node 0's full text exceeds every C10 cap, so under I3 (forced labels use only rungs 1–2) no
rung accepts it: it takes the O4 base-slot rule (`ABOVE` at `SLOT_GAP`), records
`forcedAtBaseSlot() == true`, and its rectangle joins the obstacle set. The five non-forced
labels re-derive around it, as below. The enclosure variant adds the generator-derived
stand-in rectangle `(−41, −39.619987, 148.289871, 13.619987)` `=
hullLabelRect(longScene, 1, 0, 0)` (not the §2.8 rule; injected through the §2.7 step 13 seam).

Per-label expectations at 1128×364 without the stand-in (zoom 1, world-centred; the forced
row is the O4 exception and is not collision-tested):

| # | name | slot | font | text | anchor | size |
|---|---|---|---|---|---|---|
| 0 | Well-Ordering *(forced, O4 base slot)* | ABOVE | 12 | full | (−22, −22.172057) | 274.130005×16.344114 |
| 1 | Axiom Schema | ABOVE_FAR | 12 | `Axiom Schema of Replacement a…` | (0, −46.172057) | 193.873367×16.344114 |
| 2 | Transfinite | BELOW_FAR | 9 | full | (22, 44.129043) | 185.041336×12.258085 |
| 3 | Cardinal | LEFT | 12 | `Cardinal Arithmetic u…` | (−100.392456, 22) | 128.784912×16.344114 |
| 4 | Ultrafilter | BELOW_FAR | 12 | `Ultrafilter Lemma and Boolean Pr…` | (0, 68.172057) | 198.541412×16.344114 |
| 5 | Kuratowski | RIGHT | 12 | `Kuratowski Zorn Lem…` | (100.656464, 22) | 129.312927×16.344114 |

Per-label expectations at 1128×364 with the stand-in:

| # | name | slot | font | text | anchor | size |
|---|---|---|---|---|---|---|
| 0 | Well-Ordering *(forced, O4 base slot)* | ABOVE | 12 | full | (−22, −22.172057) | 274.130005×16.344114 |
| 1 | Axiom Schema | BELOW_FAR | 12 | `Axiom Schema of Replacement a…` | (0, 46.172057) | 193.873367×16.344114 |
| 2 | Transfinite | RIGHT | 12 | `Transfinite Induction…` | (99.558441, 0) | 127.116882×16.344114 |
| 3 | Cardinal | LEFT | 12 | `Cardinal Arithmetic u…` | (−100.392456, 22) | 128.784912×16.344114 |
| 4 | Ultrafilter | BELOW_FAR | 12 | `Ultrafilter Lemma and Boolean Pr…` | (0, 68.172057) | 198.541412×16.344114 |
| 5 | Kuratowski | RIGHT | 12 | `Kuratowski Zorn Lem…` | (100.656464, 22) | 129.312927×16.344114 |

Histograms (all six labels; the forced row counts as full and base-slot):

| viewport | without stand-in | with stand-in | max leader | collisions |
|---|---|---|---|---|
| 1128×364 | full 1 (base slot), dense 1, truncated 4, hover-only 0 | full 1 (base slot), dense 0, truncated 5, hover-only 0 | 78.6565 px | 0 / 0 |
| 500×300 | full 1 (base slot), dense 1, truncated 4, hover-only 0 | full 1 (base slot), dense 0, truncated 5, hover-only 0 | 78.6565 px | 0 / 0 |
| 200×130 | full 1 (base slot), truncated 2, hover-only 3 | full 1 (base slot), truncated 1, hover-only 4 | 46.1721 px | 0 / 0 |

Note the truncation strings are those produced by `truncateTo` (C10 cap per slot) and the
ellipsis is `…`. The 200×130 `full 1` row of earlier drafts was the mockup's unchecked
forced-label fallback (gap 5.0); it is replaced by the pinned O4 base slot at `SLOT_GAP` and
is asserted.

### 5.6 Stickiness and invalidation fixtures (`ScreenLabelPlacementShould`)

**Pan case.** Dense 12-node fixture, 1128×364, enclosure stand-in as in §5.4. Pan the viewport
origin by `(+1, 0)` px, re-place with `previous` = the original result. Expected: all 11
placed labels keep slot, text and font; histogram unchanged; second re-application with the
new result as `previous` is identical (idempotent).

**One-pixel retention case.** Dense fixture, viewport 200×130 (world-centred
`(−100,−65,200,130)`), enclosure stand-in `(−76, −79.619987, 148.289871, 13.619987)`.
Baseline placement (zoom 1):

| name | slot | font | anchor | size |
|---|---|---|---|---|
| Axiom of Choice *(forced)* | ABOVE | 12 | (−17, −56.172057) | 91.104675×16.344114 |
| Theorem | BELOW_FAR | 9 | (−51, 16.129043) | 38.295258×12.258085 |
| Pairing | LEFT | 9 | (−79.976112, 0) | 29.952225×12.258085 |
| Infinity | RIGHT | 9 | (79.877113, 0) | 29.754227×12.258085 |
| Separation | BELOW | 12 | (−51, 56.172057) | 61.260422×16.344114 |
| Comprehension | BELOW | 9 | (17, 54.129043) | 67.716476×12.258085 |

Move `Infinity` from `(51,0)` to `(51,−1)` and re-place with `previous` = baseline. Under the
pinned retention rule (I4 / §2.7 step 4) **Infinity keeps its previous slot**: expected
`RIGHT`, font 9, anchor `(79.877113, −1)`, size unchanged `29.754227×12.258085`; every other
label is bit-identical to the baseline; collisions 0/0; re-running with the new result as
`previous` is a fixed point. (A fresh placement — no `previous` — would produce `BELOW`; that
is not the fixture.)

**Invalidation case.** Two nodes `Alpha` (selected, radius 8) at `(0,0)` and `Beta`
(radius 8) at `(−40,0)`; viewport 400×300 (world-centred `(−200,−150,400,300)`); no
enclosure. Baseline (zoom 1): `Alpha` `ABOVE` anchor `(0, −22.172057)`, size
`32.292221×16.344114`; `Beta` `ABOVE` anchor `(−40, −22.172057)`, size
`25.632172×16.344114`. Move `Beta` to `(0,−35)` (its disc now intersects `Alpha`'s previous
rectangle) and re-place with `previous` = baseline. Expected: `Alpha`'s previous slot is
invalidated by the newly arrived disc, so `Alpha` is re-placed through the ladder to `BELOW`
anchor `(0, 22.172057)` size `32.292221×16.344114`; `Beta` retains `ABOVE`, recomputed at its
new centre to anchor `(0, −57.172057)` size `25.632172×16.344114`; collisions 0/0; re-running
with the new result as `previous` is a fixed point.

### 5.7 Painted-ink zoom matrix (`ScreenLabelPlacementShould`)

- Fixture: the §5.4 dense 12-node scene with its enclosure stand-in, at
  `z ∈ {0.25, 1.0, 2.0}` (at `z=2` the forced label is the O4 base-slot exception and is
  included, 11 labels); plus the §5.5 long fixture at `z=1` with and without the stand-in;
  render each `PlacedLabel` alone with `label.font()` at its screen position in one common
  global screen coordinate frame, antialiasing on, into a transparent layer; compute the ink
  bitmap (non-transparent pixels). All expected values use the generator's per-zoom stand-in
  reading of §5.4, `hullLabelRect(scene, z, 0, 0)`; the scaled zoom-1 rectangle reading is
  **not** used.
- Expected: pairwise ink bitmaps are disjoint (no shared pixel); ink misses every disc bitmap
  of radius `max(2, r·z)`. There is **no** ink-containment assertion (C14). Labels carrying an
  I2 exception (`emphaticAtAnchor`, `forcedAtBaseSlot`) are exempt from the rectangle-level I2
  clauses but are included in this measured fixture set.
- Per-fixture measurements at the real anchors (Appendix A.3), under the per-zoom stand-in
  above:

  | fixture | stand-in | placed | forced label | max per-side overhang | min rect gap |
  |---|---|---|---|---|---|
  | dense z=0.25 | `hullLabelRect(dense, 0.25, 0, 0)` | 6 | BELOW_FAR `(−4.25, 31.672057)` | 0.4958 | 2.155886 |
  | dense z=1.0 | `hullLabelRect(dense, 1, 0, 0)` | 11 | ABOVE `(−17, −56.172057)` | 0.5156 | 1.655886 |
  | dense z=2.0 | `hullLabelRect(dense, 2, 0, 0)` | 11 | ABOVE `(−34, −98.172057)`, O4 base-slot | 0.7578 | 19.921654 |
  | long z=1 none | — | 6 | ABOVE `(−22, −22.172057)`, O4 base-slot | 0.4793 | 7.655886 |
  | long z=1 stand-in | `hullLabelRect(long, 1, 0, 0)` | 6 | ABOVE `(−22, −22.172057)`, O4 base-slot | 0.2151 | 5.655886 |

  Dense hidden labels are `{Power Set, Replacement Scheme, Union, Separation, Comprehension,
  Well-Ordering}` at z=0.25, `{Union}` at z=1.0 and `{Replacement Scheme}` at z=2.0; the long
  fixture has no hidden labels. The O4 base-slot labels (dense z=2 and long z=1, with and
  without the stand-in) are the I2 exceptions in this measured set; the dense z=2 one is the
  only label whose rectangle intersects a disc rectangle.
- No single-side overhang exceeds `0.7578` px; the minimum label–label rectangle gap over the
  fixture set is `1.655886` px (dense z=1); `2 · 0.7578 = 1.5156 < 1.655886`, which is why the
  painted bitmaps stay disjoint. The integer-`stringWidth` counterfactual and its two-sided
  gap argument are pinned in C14; it is not part of this matrix.

### 5.8 Enclosure-label fixture (`ScreenLabelPlacementShould`)

Hull: square half-extent 50 centred at `(0,0)`, `labelAnchor = (0,0)`, vertices as in the
existing `square()` helper convention; canonical polygon verified as
`(−50,−50),(50,−50),(50,50),(−50,50)` (Appendix A.7). Emphatic label text "Axioms" has
`w = 55.065384`, `h = 20.430143`; emphatic text "Basic Definitions and Theorems" has
`w = 235.291611`.

- **Interior.** "Axioms", no obstacles: mode `INTERIOR`, anchor exactly `(0,0)`, the
  rectangle inside the hull, `emphaticAtAnchor == false`, `leaderStart` empty.
- **Arc.** "Axioms", one disc obstacle at `(0,0)` radius 8: the interior rectangle collides,
  so the arc tier runs; all four edge populations are 0, the bottom edge (index 0) wins the
  tie, and the candidate anchor is `(0, −38.784928)` (`−50 + ARC_GAP + h/2 =
  −38.78492832183838`; `h/2 + ARC_GAP = 11.215072` rounded, `11.215071678` exact, inward from
  the bottom edge midpoint `(0,−50)`). Expected mode `ARC`, anchor `(0, −38.784928)`,
  `leaderStart` empty.
- **External.** "Basic Definitions and Theorems" (width 235.291611 > 2·50, so it cannot fit
  inside the hull), no obstacles, `placementArea = (−160,−160,320,320)`: expected mode
  `EXTERNAL`, anchor `(0, −64.215072)` (bottom edge, lane 0:
  `h/2 + EXTERNAL_GAP = 14.215072` outward), `leaderStart = (0, −50)`.
- **Emphatic terminal.** "Basic Definitions and Theorems" with
  `placementArea = (−60,−60,120,120)`: every exterior candidate rectangle lies outside the
  area, the arc/interior tiers fail on width, so the finite lane rule of §2.8 Tier C abandons
  the four edges after 16 candidates in total (bottom 2, right 6, top 2, left 6; the per-edge
  cap for this area is
  `ceil((120+120)/(20.430143+4))+2 = 12`), and the emphatic terminal rule applies: anchor
  exactly `(0,0)`, `emphaticAtAnchor == true`, `leaderStart` empty, no exception.
- **Subtle terminal.** The same scene with a subtle label: the search stops after the
  `SUBTLE_EXTERNAL_CANDIDATE_BUDGET = 8` candidates without acceptance; mode `HOVER_ONLY` at
  `(0,0)`, not an obstacle, `leaderStart` empty.
- In every case `geometry.hulls()` is unchanged before and after placement.

### 5.9 Zoom × rendering-level matrix (`ScreenLabelPlacementShould`)

Fixture: dense 12-node scene (§5.4), viewport 1128×364, **without** the enclosure stand-in
(the stand-in is a node-label obstacle for §5.4/§5.7; the matrix isolates the level filter).
`FULL` and `DENSE` produce identical results (§2.7 step 11):

| zoom | `FULL` / `DENSE` | forced label (selected, rung 1–2) |
|---|---|---|
| 0.25 | 8 placed (full 7, dense 1), hover-only: Pairing, Union, Infinity, Well-Ordering | ABOVE, anchor `(−4.25, −24.672057)` |
| 0.5 | 10 placed (full 10), hover-only: Union, Well-Ordering | ABOVE, anchor `(−8.5, −35.172057)` |
| 1.0 | 11 placed (full 11), hover-only: Union | ABOVE, anchor `(−17, −56.172057)` |
| 2.0 | 11 placed (full 11), hover-only: Replacement | ABOVE_FAR, anchor `(−34, −122.172057)` |

`OVER_TARGET`: exactly the forced label above (1 label) at zooms 0.25/0.5/1.0/2.0, and every
non-forced label is absent. At every combination, label/label and label/disc rectangle
intersections are 0.

**Emphatic-enclosure cell (design §8.3.2).** The matrix also runs the §5.8 hull scene —
hull half-extent 50 at `(0,0)`, `labelAnchor = (0,0)` — with one non-forced node `Theorem`
(radius 8) at `(0,−80)`, `placementArea = 1128×364` (world-centred), zoom 1, and no other
obstacles. With an emphatic `Axioms` label: `FULL` and `DENSE` return exactly two labels —
the enclosure `INTERIOR` at `(0,0)` (font 15 bold, `55.065384×20.430143`,
`emphaticAtAnchor == false`, `leaderStart` empty) and the node `ABOVE` at
`(0, −102.172057)` (font 12, `51.060364×16.344114`); `OVER_TARGET` returns exactly the
emphatic enclosure and the node label is absent. With a subtle `Axioms` label instead
(font 12, `41.340302×16.344114`, `INTERIOR` at `(0,0)`): `FULL`/`DENSE` return the two
labels, and `OVER_TARGET` returns **no** labels (subtle enclosures are not required). In
every cell the returned rectangles are pairwise disjoint.

### 5.10 Ordering predicate, forced transitions, bounds and wiring

- **Truncatable-while-a-full-slot-was-free predicate.** For the §5.5 fixtures (1128×364 and
  500×300, with and without the stand-in), every `truncated()` label must have
  `fullTextSlotWasFree() == false` at the moment it was processed: if any full-text rung
  candidate had been inside the area and collision-free, the ladder would have placed the
  full text first. Measured: all four truncated labels (five with the stand-in) report
  `false`. The predicate is observed through `PlacedLabel.fullTextSlotWasFree()` (§2.5).
- **Hover-only label becoming forced.** Dense fixture at 200×130 (the §5.6 scene): `Union`,
  `Well-Ordering`, `Extensionality`, `Power Set`, `Foundation / Regularity` and
  `Replacement Scheme` are hover-only. Force `Well-Ordering` (add it to the request's forced
  set, keep `previous` = baseline): expected `Well-Ordering` placed `BELOW`, font 12, anchor
  `(51, 56.172057)`; `Comprehension` loses its previous `BELOW` slot to the forced label and
  becomes `HOVER_ONLY`; the other five placed labels (`Axiom of Choice`, `Theorem`, `Pairing`,
  `Infinity`, `Separation`) keep their slots; collisions 0/0; re-running with the new result
  as `previous` is a fixed point. The forced label never intersects anything (design §8.3.4).
- **Per-slot displacement and support bounds.** At zooms 1 and 2, every placed label of the
  §5.4 and §5.5 fixtures must satisfy the leader-length and corner-support form of §2.7 for
  its own `(slot, w, h, r')`. Measured maxima (Appendix A.6):

  | fixture | zoom | labels | max leader (px) | max corner support (px) |
  |---|---|---|---|---|
  | dense 12 | 1.0 | 11 | 77.894615 | 138.704698 |
  | dense 12 | 2.0 | 11 | 73.611911 | 118.655013 |
  | long (stand-in, includes the base-slot label) | 1.0 | 6 | 78.656464 | 143.545734 |
  | long (stand-in, includes the base-slot label) | 2.0 | 5 | 86.656464 | 151.533443 |

  The design's single-form bound must not be used (design revision 5.2 deleted it after the
  150.2 px counterexample; §8 O7).
- **Geometry independence through the paint path (design §8.3.10).** Run placement through
  the real paint path (`GraphCanvas.paintComponent` → `GraphPainter`) for the dense fixture
  and assert the published `CanvasState` geometry (node and hull maps) is bit-identical
  before and after a zoom change, a forced-set change and a placement recomputation, while
  the returned `List<PlacedLabel>` changes as specified. This pins G4/I2: placement never
  feeds back into geometry.

## 6. Removal and migration inventory

**Delete**

| Path | Surface |
|---|---|
| `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/LabelPlacementEngine.java` | entire file (560 lines), incl. `MAX_INTERIOR_EXPANSION` (`:22`), `ARC_GAP`/`EXTERNAL_GAP`/`SUBTLE_EXTERNAL_CANDIDATE_BUDGET` (`:23-25`, constants inherited in §2.8), hull expansion (interior `:153-249`) |
| `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/LabelPlacement.java` | entire file (143 lines); `Mode` moves to `PlacedLabel` |
| `GraphGeometry` label surface | `GraphGeometry.java` label field (`:16`) and constructor parameter (`:26`), 3-arg `of` (`:46-49`), `labels()` (`:60-62`), `copyLabels` (`:101-115`), label parts of `equals/hashCode/toString` |
| `TypedSpringBox.REST_LENGTH` | `TypedSpringBox.java:18`; other uses at `GraphStreamLayoutEngine.java:230` and `:239` |
| worker-side label stage | `GraphWorkspacePerformanceDiagnostic.java:293-297` and `:354-358`; `PerformanceMeasurements.Stage.LABEL` (`:35`) replaced by `PLACEMENT("placement")` at the same enum position |

**Update**

| Path | Change |
|---|---|
| `.../layout/LayoutFrame.java` | residual field, factories (3-arg default `UNVERIFIED`; 4-arg for published residuals), accessor, validation (§2.3) |
| `.../layout/LayoutWorker.java` | project after correction in `accept` (`:285-293`); `EMPTY_FAILED_FRAME` residual `0` with the 4-arg factory (`:34-35`); retained residual in `failedFrame` (`:340-348`) |
| `.../control/LayoutSettleLoop.java` | delete `LabelAssembler` (`:982-990`); remove `labels.place` at `:543-544` and `:721-722` (keep `computeHulls`); `failedFrame` (`:732-749`) routes `fallbackPositions` (`:933-948`) through the projection and picks the residual per §4.3 |
| `.../control/GraphUpdateCoordinator.java` | initial frame residual `0` (`:133-137`); `publishFailure` preserves residual (`:551-566`) |
| `.../layout/graphstream/GraphStreamLayoutEngine.java` | relationship rest length at `:230` = `radiusOf(first)+radiusOf(second)+MIN_GAP`; containment rest length at `:239` = `R(k)`; new `radiusOf`/`R(k)` helpers from `projection.prominence()`; `Seeds.nodePosition` `:682` uses the disc-derived ring radius `R(k)` = `(2·max_r+MIN_GAP)/(2·sin(π/k))`, `R(k)=0` for `k≤1`; `directNodeRingRadius` stays for `directNodeReach` (`:558`) per N4; calibration multipliers and hierarchy rest lengths unchanged (§2.9) |
| `.../canvas/GraphPainter.java` | rewrite `paintLabels` (`:231-287`) to consume `List<PlacedLabel>` and paint the carried font; delete `labelFont` (`:289-298`) and `shouldPaintLabel` (`:301-305`); `drawCentered` (`:307-312`) centres on double bounds (C14); the only `GraphGeometry.labels()` reader (`:262`) disappears |
| `.../canvas/GraphCanvas.java` | own `ScreenLabelPlacementCache`; build `LabelPlacementRequest` in the paint path (`:358-365`) from `canvasState`, `viewport`, `paintState`, `theme`; pass placements to `GraphPainter` |
| `.../canvas/GraphTheme.java` | `labelFont(RenderingLevel)` (`:262-270`) and `overTargetLabelFont` (`:258-260`) become unreferenced; remove once the tree has no callers |
| `.../projection/NodeProminence` | unchanged (MAX_SCALE reference only) |

**New files** (all under `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/`):
`layout/NodeSeparationProjection.java`, `layout/NodeSeparationResult.java`,
`canvas/ScreenLabelPlacement.java`, `canvas/LabelPlacementRequest.java`,
`canvas/PlacedLabel.java`, `canvas/LabelFonts.java`, `canvas/ScreenLabelPlacementCache.java`.

**Test migration** (all under `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/`):

| File | Required work |
|---|---|
| `geometry/LabelPlacementShould.java` | replaced by `canvas/ScreenLabelPlacementShould.java` (design §8.3) |
| `canvas/GraphCanvasPaintShould.java` | 3-arg `GraphGeometry.of` at `:238,:325,:665,:684,:888,:971,:1225,:1253,:1354`; `LabelPlacement.Mode` uses; rewrite the label-font/forced-pixel assertions against carried `PlacedLabel.font()` and placement results: `useDedicatedEmphaticLabelsAndPreserveLevelSpecificVisibility` (`:263-292`), `assertForcedOrdinaryLabelsVisible` (`:705-709`), `assertEmphaticGlyphUsesDedicatedFont` (`:711-726`), `assertForcedOrdinaryGlyphsUseFullDetailFont` (`:728-733`), `assertGlyphUsesFullDetailFont` (`:735-746`); the 7 pt `overTargetLabelFont` is never selected (§2.6) |
| `canvas/GraphInteractionControllerShould.java` | 3-arg `GraphGeometry.of` at `:588` |
| `window/GraphWorkspaceWindowModelShould.java` | 3-arg `GraphGeometry.of` at `:1911,:1979` |
| `window/WorkspaceDialogsShould.java` | 3-arg `GraphGeometry.of` at `:440,:483` |
| `integration/GraphWorkspaceModelAcceptanceShould.java` | `LabelPlacement`/`LabelPlacementEngine` references |
| `performance/GraphWorkspacePerformanceDiagnostic.java` | remove worker-side label measurements `:293-297` and `:354-358`; add paint-path `PLACEMENT` stage |
| `performance/PerformanceTripwiresShould.java` | stage list `:64-66` (`"label"` → `"placement"`); threshold assertions `:67-79`; the fixture golden comparison of §7.4 at `:131-145` |
| `layout/GraphStreamBoundaryShould.java` | extend `publicLayoutTypes()` (`:84-87`) with `NodeSeparationProjection` and `NodeSeparationResult` so the GraphStream-free public-signature check covers the new public layout types |
| `layout/ReferenceRepulsionFixture.java`, `projection/GroupOnlyProjectionShould.java`, `projection/ProjectionDeterminismShould.java`, `projection/StructuralProjectionShould.java` | churn-affected settled expectations (design §8.5) |
| `layout/TypedForcesShould.java` | two-map idle gate `:382` (`twoMapWorkspaceSettlesToIdle`); re-record the baseline before the change (design §8.5 says `LayoutSettleLoopShould`; §8 O6 corrects the location) |
| `layout/MapTierCorrectionShould.java` | add the ±30 hull characterization of §5.3 (existing helper `square()` builds half-extent 1) |
| 13 files constructing `LayoutFrame` | `AccessibleGraphCanvasShould`, `GraphCanvasPaintShould`, `GraphInteractionControllerShould`, `GraphSearchModelShould`, `ContributorDeletionPlanShould`, `GraphUpdateCoordinatorShould`, `LayoutSettleLoopShould`, `GraphWorkspaceCommandAcceptanceShould`, `LayoutWorkerShould`, `PerformanceTripwiresShould`, `GraphWorkspaceUiEvidence`, `GraphWorkspaceWindowModelShould`, `WorkspaceDialogsShould` |
| new | `layout/NodeSeparationProjectionShould`, `canvas/ScreenLabelPlacementShould`, frame-pipeline assertions in `control/LayoutSettleLoopShould`/`layout/LayoutWorkerShould` (design §8.2) |

Additional churn candidates must be enumerated by running the module suite after the change;
the spec-authoring sandbox cannot run Gradle (design §10 fixture churn).

## 7. Acceptance criteria (traceable to design §8)

1. **I1 (design §8.1.1–§8.1.3, §8.2.1–§8.2.4).**
   `NodeSeparationProjectionShould` passes §5.1–§5.2 and asserts the pass counts through
   `NodeSeparationResult.passes()`; every published-frame test asserts
   `residualViolations == 0` on the displacing fixture of §5.1. Coincident-particle
   determinism (design §8.1.4) is §5.2 S4.
2. **Verification closure (design §8.2.2).** For a normal frame and for a worker-failure
   fallback frame the published residual equals an independent all-pair recomputation on the
   published positions; `EMPTY_FAILED_FRAME` and the initial empty frame carry `0`; retained
   frames carry the retained residual. The omitted-fix guard (design §8.2.3) fails when the
   projection is disabled.
3. **I2/I3/I4 (design §8.3.1–§8.3.10).** `ScreenLabelPlacementShould` passes §5.4–§5.10:
   painted-ink matrix §5.7 (design §8.3.1); zoom × level matrix §5.9 (design §8.3.2);
   ordering predicate §5.10 (design §8.3.3); hover-only becoming forced §5.10 (design
   §8.3.4); stickiness and invalidation §5.6 (design §8.3.5); per-slot bounds at zooms 1
   and 2 §5.10 (design §8.3.6); viewport-change recomputation §5.6 pan case (design §8.3.7);
   enclosure fixture §5.8 (design §8.3.8); no silent clipping is asserted by the §5.4–§5.5
   tables and the `placementArea` checks (design §8.3.9); geometry independence through the
   paint path §5.10 (design §8.3.10). Forced-label no-slot cases assert the O4 base-slot rule
   with the recorded I2 exception and the base-slot rectangle in the obstacle set (§5.5's
   forced rows, §5.7's dense z=2 row, §5.10).
4. **Persistence.** No workspace XML, pin or position is written by the change. The
   `PerformanceTripwiresShould` fixture test must additionally pin the **current bytes**:
   record the SHA-256 of each generated fixture (`two-map.fpg`, `three-map.fpg`,
   `reference-2000-5000.fpg`) as a golden constant after the change and assert equality; the
   existing `isNotEqualTo(historical)` assertion (`:145`) alone cannot detect a second
   change and is not sufficient.
5. **Removal.** No reference to the deleted types and members — the type `LabelPlacement`,
   the type `LabelPlacementEngine`, the `GraphGeometry.labels()` method, the 3-arg
   `GraphGeometry.of`, the field `TypedSpringBox.REST_LENGTH`, and
   `LabelPlacementEngine.MAX_INTERIOR_EXPANSION` — remains in main or test sources. The new
   names `LabelPlacementRequest`, `ScreenLabelPlacement`, `ScreenLabelPlacementCache` and
   `PlacedLabel` are expected and must not be matched by the removal check.
6. **Settle/idle (design §8.5).** `TypedForcesShould.twoMapWorkspaceSettlesToIdle` (`:382`)
   re-baselined before the change; after the change the recorded idle frame count and the 100
   stable frames (rms ≤ 0.05, max ≤ 0.10) still hold.
7. **Performance (design §8.4).** The `PLACEMENT` stage exists in the diagnostic and records
   per-trigger p95 with the C16 method; the projection stage baseline is recorded; thresholds
   are set from those baselines (C7/C15). Until then performance gates for the two new stages
   are diagnostic-only.
8. **Build.** `gradle :freeplane_plugin_graph:test` (and the full suite) green with Java 21
   from `~/.sdkman/candidates/java/21.0.8-zulu`; Java 8 target preserved.

## 8. Resolutions and open points

The approved design is revision 5.2. Where an earlier revision left a contradiction, the spec
records the resolution; where revision 5.2 already resolves it, this specification adopts the
design's resolution directly. O1–O3 and O5–O14 are resolved historical notes kept for
traceability; **no open point remains** (O4 is resolved by the user default).

- **O1 — `RELAXATION` arithmetic is ambiguous in design §5.2.** Design §5.2: "a violating pair
  separates by half the penetration, scaled by `RELAXATION = 0.5`". Reading A (pinned here):
  each movable node moves `0.5·penetration`, one pinned partner moves `1.0·penetration`.
  Reading B: the pair's total displacement is `0.5·penetration`. Reading B needs 50 passes
  for a two-body fixture (re-measured; an earlier draft said 52, which is not reproducible)
  and would make the design's own convergence fixture depend on the cap rather than on
  convergence. Pinned: A (C3). This is an implementation
  detail below the design's abstraction; the design's §8.1.2 wording ("half/half
  displacement; with one pinned, only the partner moves") matches it.
- **O2 — "nodes sorted by projected key" is not implementable as written.**
  `ProjectedNodeKey` (`ProjectedNodeKey.java`) implements neither `Comparable` nor a natural
  order. Pinned: process nodes in `LayoutPositions.nodes()` iteration order (the immutable
  `LinkedHashMap` copy preserves the deterministic projection order); pair candidates are
  visited in ascending index order. This is also the pinned tie order for equal radii in §2.7
  step 3. If the implementation prefers an explicit comparator, the §5.2 fixture keys are
  chosen so that ascending `(map UUID, persisted node id)` reproduces the same order, but the
  comparator must be total and hash-independent.
- **O3 — anchors are undefined in the projection.** `project` receives `LayoutPositions`
  (nodes and anchors) and a `Set<ProjectedNodeKey>`, but I1 and the residual cover node pairs
  only. Pinned: anchors are copied through unchanged. No anchor/node or anchor/anchor
  separation is claimed; extend the contract before relying on it.
- **O4 — forced node labels have no terminal rule in the design (resolved by user default).**
  Design I2 lists only the emphatic-enclosure exception, yet I3 requires forced labels never
  below full text. The user default resolves the conflict: a forced node label that accepts
  neither full-text rung is placed at its base slot — `ABOVE` at `SLOT_GAP` (C8), unchecked —
  and records the I2 exception on the placement result (`forcedAtBaseSlot() == true`). The
  placed rectangle contributes to the obstacle set exactly like an accepted candidate, so
  later labels avoid it. The committed generator's unchecked fallback used gap `5.0`; this
  specification pins `SLOT_GAP = 6.0` (the ladder gap). Consequences were re-derived under
  this rule: §5.5's forced rows, histograms and the five non-forced placements (Axiom Schema
  moved from ABOVE to ABOVE_FAR without the stand-in, Cardinal from BELOW to LEFT, Transfinite
  to BELOW_FAR/RIGHT), §5.7's dense z=2 and long-fixture rows and their ink/gap measurements,
  §5.10's label counts, and the two-sided C14 counterfactual. On the short side the base-slot
  rectangle can intersect a disc rectangle and leave `placementArea`; both are covered by the
  recorded I2 exception, and the rectangle-level clauses of I2 are exempt for the flagged
  label.
- **O5 — the design's fixture-hash claim is wrong.** Design §8.5 says
  `PerformanceTripwiresShould.java:64-67` "carries fixture hashes that move". Verified:
  `:64-66` is the exact `Stage.names()` assertion (which does move when `LABEL` becomes
  `PLACEMENT`), while the fixture SHA-256 values are at `:131-136` and hash workspace XML
  that contains no layout positions. The current test only asserts `isNotEqualTo(historical)`
  (`:145`); §6/§7.4 require recording the current bytes as golden values.
- **O6 — idle-baseline location.** Design §8.5 places the two-map idle-frame baseline in
  `LayoutSettleLoopShould`; verified: the two-map settle/idle frame-count gate is
  `layout/TypedForcesShould.java:382` (`twoMapWorkspaceSettlesToIdle`). The spec uses the
  verified location.
- **O7 — the single-form corner bound is false and has been deleted from the design.**
  Design revision 5.1 bounded "the label rectangle's farthest corner" by
  `max(2, r·zoom) + 30 + halfDiagonal`; the counterexample (a maximal horizontal label
  `r=14, w=130, h=16.344114` in a near RIGHT slot has support
  `hypot(14+6+130, 8.172) = 150.222 px`) falsified it, and design revision 5.2 deletes the
  single form. Pinned: the exact per-slot leader/support closed forms of §2.7, asserted per
  label at zooms 1 and 2 with the measured maxima of §5.10.
- **O8 — label-measurement space wording was self-contradictory; design revision 5 adopts
  the screen-space reading.** Design revision 5's "base font metrics multiplied by zoom,
  equivalently the base font measured in a screen-space FRC" is not equivalence: the former
  reserves `91.104675·zoom` px (22.776 px at zoom 0.25) while the painter paints ≈90 px of
  ink. Pinned and confirmed by design revision 5 (unchanged in revision 5.2): screen-space
  bounds of the base font in an identity FRC (C13); equivalence to the painter's derived font
  is measured (Appendix A.2).
- **O9 — paintable ink vs. the reserved rectangle was unresolved; design revision 5.2 now
  states the rectangle-level contract.** Revision 5.1's I2 test was unsatisfiable if read as
  ink containment: the current painter centres on the integer `FontMetrics.stringWidth`
  (`GraphPainter.java:307-312`) and measured ink overhang is 0.22–0.76 px per side over the
  fixture set (re-measured at the real anchors; C14). Pinned and confirmed by design revision
  5.2: double-precision centring (C14) plus pairwise-ink-disjointness and disc-disjointness
  assertions, with no containment assertion; the integer-centring counterfactual and its
  two-sided bound are in C14.
- **O10 — enclosure-label exterior placement is now pinned.** Design revision 5.2 states the
  enclosure slot model and delegates the constants to this specification. Pinned: the rule of
  §2.8 (anchor, fonts, interior/arc/exterior tiers, edge ordering and population, lane
  spacing, subtle candidate budget, leader start) and the derivable fixture of §5.8. The
  §5.4/§5.5 "with enclosure" rows use the generator's synthetic stand-in rectangle and are
  scoped accordingly.
- **O11 — design naming/citation slips carried into implementation.** The design names both
  `NodeSeparation.MIN_GAP` (design §4) and `NodeSeparationProjection` (design §5.2); this
  specification uses `NodeSeparationProjection.MIN_GAP`. `TypedSpringBox.java:84-89`
  (`scaleRepulsion`) is actually `TypedNodeParticle.java:84-89`; `LabelPlacementEngine.java`
  collision checks are at `:157,:276,:366` (not `:134`); `LayoutFrame` fields are at `:13-17`
  and factories at `:33-36`/`:38-42` (design cites `:16-30`); `LabelPlacement.java` is 143
  lines (not 139); `NodeSeparationMockups.java` declares `MIN_GAP` at `:32` (not `:29`);
  `GraphTheme`'s `overTargetLabelFont` accessor is at `:258-260` (not `:254-256`); and the
  `GraphWorkspacePerformanceDiagnostic` label timings are `:293-297` and `:354-358`. These
  are documentation corrections only; the affected behaviour is pinned in §2–§6.
- **O12 — slot order (resolved: design revision 5.2 agrees with this specification).**
  Design revision 4 said "inward slots are tried first, in a fixed per-edge order"; the
  committed generator and every fixture value in §5 implement one global slot order, and
  design revision 5.2 deletes the inward-first wording for exactly that reason: out-of-surface
  candidates are rejected, so the ladder falls through to inward slots without a separate
  per-edge rule. Pinned: the single global order of §2.7 step 5. There is no contradiction,
  and no fixture needs re-measuring.
- **O13 — projection/placement wall-clock thresholds.** C7/C15 cannot be pinned in a
  read-only specification: both stages must first exist and be measured on the target host,
  as the design itself requires. Reference calibration is provided; no threshold is invented.
- **O14 — forced-label category sub-order dropped.** Design §5.3 orders forced labels
  "selected → hovered → related", but `LabelPlacementRequest.forced()` is a `Set`, so the
  categories cannot be recovered and the cache key treats the set as order-independent.
  Pinned: forced labels are processed first as one group in descending disc radius, ties by
  `LayoutPositions.nodes()` order, exactly as the §5 fixtures reproduce (every §5 fixture has
  one forced label, so no fixture value changes); the cache key keeps set equality because
  the order is derived deterministically from the set (§2.7 step 3, §2.10).

## Appendix A — Verification log

Environment: `~/.sdkman/candidates/java/21.0.8-zulu` (Java 21.0.8, Linux, 22 cores),
headless. Working copies of probes under `/tmp`; no repository file other than this
specification was written during verification.

1. **Mockup reproducibility.** `java -Djava.awt.headless=true NodeSeparationMockups.java /tmp/nsspec/mockups`;
   all seven PNGs regenerate from the committed generator and the stdout rows quoted here
   (dense rows, ladder comparison, long-label rows, zoom counts) reproduce. The generator is
   unchanged by this specification.
2. **Font/space measurements.** `FontProbe`: `h(12)=16.344114`, `h(9)=12.258085`,
   `h(10)=13.619987`, `h(15 bold)=20.430143`; `"Axiom of Choice"` 12 pt logical width
   `91.104675`; `getStringBounds` height equals `getLineMetrics("Ag").getHeight()` for 12/9/15
   pt; screen-FRC vs. derived-world×zoom max delta `2.747e-3` px at zooms 0.25–4. `"Axioms"`
   15 pt bold `55.065384`; `"Basic Definitions and Theorems"` 15 pt bold `235.291611`;
   `"Basic Definitions and Theorems"` 10 pt `148.289871` (stand-in width).
3. **Painted-ink measurement.** Instrumented screen-space renders of the pinned placements
   with double-precision centring at the real anchors (`+W/2,+H/2` translation) and the
   per-zoom stand-in of §5.4/§5.7: maximum per-side overhang `0.7578` px (dense z=2,
   "Power Set"); per-fixture maxima dense z=0.25 `0.4958`, dense z=1 `0.5156`, dense z=2
   `0.7578`, long z=1 none `0.4793`, long z=1 with stand-in `0.2151`. Bitmap checks:
   pairwise ink overlap 0 px and ink/disc overlap 0 px for the §5.7 fixtures, including the
   O4 base-slot label at dense z=2. The integer-`stringWidth` counterfactual measures
   `0.7837` px per side (dense z=2, "Extensionality") and its integer width differs from the
   fractional-metric C13 width by up to `4.601` px (`2.3005` px per side, "Foundation /
   Regularity" 12 pt), so the two-sided worst case is `2·(0.7578+2.3005) = 6.117` px.
   Minimum label–label rectangle gaps: dense z=0.25 `2.155886`, dense z=1 `1.655886`, dense
   z=2 `19.921654`, long z=1 none `7.655886` (`7.827943` excluding the O4 label), long z=1
   with stand-in `5.655886`; the asserted §5.7 set's minimum is `1.655886`.
4. **Correction characterization.** A probe (`CorrectionProbe`) compiled with the real
   `HullGeometry` + `HullIntersection`: ±30 pair translations `(20,0)`, `(−20,0)`, `(0,0)`;
   final `(0,50,−50)`; recomputed A/B translation `(10,0)`, hull overlap 10,
   `siblingOverlap == true`; ±24 translations `(8,0)`, `(−8,0)`, `(0,0)` → `(0,44,−44)`;
   ±1 → no-op.
5. **Projection reference implementation.** `ProjectionProbe`/`PerfProbe` and an independent
   Python re-implementation: solvable pair `(0,0),(20,0)` → `(−1,0),(21,0)`, residual 0,
   2 passes; red-phase pair (r=14, d=24) → `(−5,0),(29,0)`, residual 0, 2 passes; S1 sandwich
   order `a,b,m` → `m=(18,0)`, residual 1, 64 passes, order `b,a,m` → `m=(22,0)`; S2 order
   `a,b,m` → `m=(42,0)`, residual 1, 2 passes, order `b,a,m` → `m=(−22,0)`; coincident pair
   → `(−11,0),(11,0)`, residual 0, 3 passes; variant-B (total-0.5) needs 50 passes for the
   solvable pair; dense12 (spacing 34) 1 pass residual 0; long6 1 pass residual 0;
   instrumented perf p95: legal grid `5,130,482 ns`, random area-2000 `220,769,319 ns`,
   dense 64-pass cluster `411,584,311 ns`.
6. **Placement fixture tables.** Instrumented copy of the committed generator
   (`/tmp/nsfix2/FixProbe*.java`): the §5.4/§5.5 tables and histograms under the pinned O4
   base-slot rule (forced label at `ABOVE` with `SLOT_GAP`, contributing to the obstacle
   set), mean `48.046026`, maxima `77.894615`/`78.656464`, zoom counts and per-zoom placements
   of §5.7/§5.9 (per-zoom stand-in reading `hullLabelRect(scene,z,0,0)`; the scaled zoom-1
   rectangle reading would give 8 labels at z=0.25 instead of 6 and is not used), stickiness cases
   (pan preserved; Infinity RIGHT retained at `(79.877113,−1)`; Alpha re-placed BELOW and
   Beta retained after the invalidation move), hover-only→forced (`Well-Ordering` → BELOW
   `(51,56.172057)`, `Comprehension` → hover-only), per-slot support maxima
   (`138.704698`/`118.655013` dense z1/z2, `143.545734`/`151.533443` long z1/z2), and the
   `fullTextSlotWasFree == false` predicate for every truncated label (4 without, 5 with the
   stand-in at 1128×364/500×300; 2 without, 1 with at 200×130).
7. **Enclosure rule.** A probe compiled against the real `HullGeometry`/`LayoutPoint`:
   canonical square polygon `(−50,−50),(50,−50),(50,50),(−50,50)`; interior anchor `(0,0)`;
   arc anchor `(0,−38.784928)` (`−38.78492832183838` exact); exterior anchor `(0,−64.215072)`
   with leader `(0,−50)`; the 120×120 placement area abandons all four edges after 16 exterior
   candidates (bottom 2, right 6, top 2, left 6; per-edge cap 12) under the finite lane rule
   and then takes the emphatic terminal rule. The deleted engine's "until the lane distance
   stops increasing" form would iterate to floating-point stagnation (about `1e16` lanes);
   the pinned rule is finite for every finite `placementArea`.
8. **Repository citations.** All `file:line` values in §2–§6 re-checked with `grep -n`/`sed`
   against the integration worktree on the day of writing (see the table rows in §2.3 and §6).
