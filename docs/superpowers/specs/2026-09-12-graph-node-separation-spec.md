# Graph Workspace Node Separation — Implementation Specification

- Date: 2026-09-12
- Status: implementation-ready specification, derived from the approved design
  `docs/superpowers/specs/2026-09-12-graph-node-separation-design.md` (revision 5.1,
  approved by review attempt 5 — 0 blockers, 2 majors, 9 minors applied).
- Review history: `/data/home/henry-arch/.local/state/pi/project-manager/runs/0233ff88d3cc54bbd61550c61fa917d6e2a5717f8a03be4e9aa63dcd47dd1cfa/pm-run-20260912-012815-01038a52/reports/design-review-attempt-{1..5}.md`.
- This specification does not modify the design. Where the design leaves a value, order,
  encoding or fixture open, this document pins it and states its provenance. Contradictions
  and gaps that cannot be resolved from the approved design are collected in §8 "Open points"
  and are not silently resolved.
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
- Label rectangles are screen-space logical bounds of the base theme font (§1, "label
  measurement convention"), never world-space font metrics.

## 1. Pinned constants

| # | Constant | Value | Kind | Source / justification |
|---|---|---|---|---|
| C1 | `MIN_GAP` | `6.0` world units | **[P]** design §4 | Declared once as `NodeSeparationProjection.MIN_GAP`. Adopted verbatim. Generator constant is `MIN_GAP = 6.0` (`NodeSeparationMockups.java:29`); the disc floor is `14+14+6 = 34`; the red-phase fixture depends on `24 < 34`. No measurement contradicts it; it is a design parameter, not a measured optimum. |
| C2 | `MAX_PASSES` | `64` | **[P]** design §5.2, confirmed by measurement | Adopted. Reference implementation (Appendix A.4) convergence passes: dense12 = 1, long6 = 1, solvable pair = 2, pinned-pinned adjacent = 2, sandwich = 64 (non-convergent by construction, §5.2 S1). No solvable fixture needed more than 2 passes, so 64 is sufficient and bounds the worst case. Worst-case work bound is derived: `MAX_PASSES · C(n,2)` = `64 · 1,999,000 = 127,936,000` pair tests at the 2000-node perf fixture. |
| C3 | `RELAXATION` and its arithmetic | `0.5`; displacement per movable node `= RELAXATION · penetration`; with one pinned the movable partner takes the **full** penetration; pinned-pinned pairs take none | **[D]** from the design's own evidence | The design's phrase "separates by half the penetration, scaled by `RELAXATION = 0.5`" is arithmetically ambiguous (see §8 O1). This specification pins the mockup's arithmetic: `push = (need − d)/2` per movable node (`NodeSeparationMockups.java:separate`). Under this reading the solvable pair converges in 2 passes; under the alternative "total = 0.5·penetration" reading it needs 52 passes (Appendix A.4), which would make the design's own "same pass budget" convergence fixture fragile. The pinned reading also matches §8.1.2 "half/half displacement; with one pinned, only the partner moves". |
| C4 | `SPATIAL_CELL` | `34.0` world units; 3×3 cell query; candidate `j` visited in ascending order | **[D]** | `2·MAX_RENDERED_NODE_RADIUS + MIN_GAP = 2·14 + 6 = 34` (`GraphStreamLayoutEngine.java:492`). Any two centres closer than `r_i+r_j+MIN_GAP ≤ 34` lie in cells with index difference ≤ 1, so a 3×3 query is complete. The ascending-`j` order preserves the fixture expectations in §5.2/§5.6. |
| C5 | Node radius in the projection | `8.0 · projection.prominence().get(key).scale()`, default scale `1.0` when absent | **[D]** | `GraphStreamLayoutEngine.java:199-201` uses the same lookup for particle radius. |
| C6 | Projection residual semantics | count of unordered pairs `i<j` with `hypot < r_i+r_j+MIN_GAP`, strict `<`, over returned node positions; includes pinned-pinned pairs; anchors excluded | **[P]** design §5.2 | Recomputable independently; §8.2.2 asserts equality with an independent recomputation. |
| C7 | Projection wall-clock p95 budget | **not pinned** | **[N]** | The `NodeSeparationProjection` stage does not exist in the repository, so no production baseline can be recorded without a code change; design §8.1.5 requires exactly that baseline first. Reference-implementation calibration on this host (Java 21.0.8, Linux, 22 cores, Appendix A.4): 2000-node legal grid, 1 pass: p95 5,130,482 ns; 2000-node random cloud in a 2000² area: p95 220,769,319 ns; 2000-node dense cluster, 64 passes, all pairs violating: p95 411,584,311 ns. These are algorithm-only numbers, **not** a stage budget. Implementation duty: add the stage to `GraphWorkspacePerformanceDiagnostic`, record its baseline, then fix the threshold. |
| C8 | `SLOT_GAP` | `6.0` screen px | **[P]** design §5.3 | Generator `slotAnchor(..., 6.0)`; the fixture tables in §5.4–§5.6 reproduce only with 6.0. |
| C9 | `DISPLACED_OFFSET` | `30.0` screen px | **[P]** design §5.3 | Generator far anchors add `30.0`; fixtures reproduce only with 30.0. |
| C10 | Per-slot maximum widths | vertical (ABOVE/BELOW/ABOVE_FAR/BELOW_FAR) `200` px; horizontal (RIGHT/LEFT/RIGHT_FAR/LEFT_FAR) `130` px; diagonal (ABOVE_LEFT/ABOVE_RIGHT/BELOW_LEFT/BELOW_RIGHT) `150` px | **[P]** design §5.3, confirmed by measurement | Generator `slotMaxWidth`. Binding evidence [M]: long fixture names measure 246.722–299.606 px and truncate; dense fixture maximum full-text width is 132.601 px ("Foundation / Regularity"), which exceeds the horizontal cap and is therefore placed vertically — exactly what the generator does. |
| C11 | Ladder order and slot order | see §2.4/§3 I3 | **[M]** from the committed generator | `LADDER_DISPLACEMENT_FIRST` and `NEAR_SLOTS`/`FAR_SLOTS` (`NodeSeparationMockups.java`); the fixture tables in §5.4–§5.6 depend on them. |
| C12 | Leader rule | leader line for every slot except `ABOVE` and `BELOW` | **[M]** from the committed generator | `drawScene`: `if (slot == ABOVE || slot == BELOW) continue;`. The design's wording "other than directly above/below" is imprecise for the displaced far slots; this specification pins the generator's observable rule. |
| C13 | Label measurement convention | `screenBounds(text, font) = font.getStringBounds(text, SCREEN_FRC)` with `SCREEN_FRC = new FontRenderContext(null, true, true)`, using the base theme font at its base size (12/9/15 pt); rect anchor `(x,y)` with `w = screenBounds.width`, `h = screenBounds.height` | **[D]** + **[M]**, design §5.3 | Verified equivalent (max delta 2.747e-3 px, Appendix A.2) to `base.deriveFont(size/zoom).getStringBounds(worldFRC) · zoom`, which is what the painter draws. The rejected literal reading "base metrics × zoom" would reserve `91.104675 · 0.25 = 22.776 px` for "Axiom of Choice" at zoom 0.25 while the painter paints 90 px of ink — the §1.2 under-reserve defect. |
| C14 | Placed-ink containment rule | the painter must centre with the double-precision `getStringBounds` width; ink must lie inside the placed rectangle | **[M]** | Current `GraphPainter.drawCentered` (`GraphPainter.java:307-312`) centres on the integer `FontMetrics.stringWidth`; measured ink then exceeds the placement rectangle by up to 0.57 px (Appendix A.2). With double-precision centring the measured overhang is ≤0.55 px of antialias fringe; fixture minimum label–label gaps are 1.6559 px (dense) and 7.6559 px (long), so the painted bitmaps remain disjoint. The painter change is part of the migration inventory (§6). |
| C15 | Placement-stage p95 threshold | **not pinned** | **[N]** | The paint-path placement stage does not exist, so the baseline design §8.4 demands cannot be recorded without the code change. The sampling method, population, warm-up and cache states are pinned in §4.6. |
| C16 | Sampling population / warm-up / cache states | population: paint-path placement calls that **miss** the cache, split into four triggers — (a) positions-identity change, (b) zoom change, (c) viewport origin/size change, (d) forced-set change; warm-up 20 discarded samples per trigger; 100 timed samples per trigger; cache state at each timed sample: cold for the measured key, `previous` retained from the immediately preceding call; statistic: nearest-rank p95 per trigger | **[P]** method from design §8.4; numbers pinned here | Warm cache hits are excluded (no work). Pan and zoom each cost one re-placement and are budgeted as one stage. The numeric thresholds follow C15. |

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

1. Build the ordered node list from `positions.nodes()` iteration order (a `LinkedHashMap`;
   this is the deterministic projection order, see §8 O2 for the design's "sorted by
   projected key" wording). Let `n` be its size.
2. `radius[i] = 8.0 · prominence_i.scale()` with default `1.0` (C5). `pinned` is the set of
   pinned node keys; keys not present in `positions` are ignored.
3. For `pass = 1..MAX_PASSES`:
   1. Insert all nodes into a uniform grid with cell size `SPATIAL_CELL` (C4).
   2. For `i = 0..n-1`, query the 3×3 cells around node `i`; for each candidate node `j`
      with `j > i` in the ordered list and ascending `j`, compute
      `d = hypot(x_j−x_i, y_j−y_i)`, `need = radius_i + radius_j + MIN_GAP`.
      If `d == 0`, use the deterministic axis `(1,0)` when `i` precedes `j`, else `(−1,0)`,
      and `d = 1`. If `d >= need`, continue.
      - both pinned: continue (no movement; the pair remains in the residual).
      - exactly one pinned: the movable node moves by the **full** penetration along the
        centre line, away from the pinned node.
      - both movable: each moves by `RELAXATION · penetration` along the centre line, away
        from the other (total relative separation increases by exactly `penetration`).
   3. If the pass moved nothing, stop.
4. Recompute the residual over **all** node pairs on the returned positions (C6); return
   `NodeSeparationResult.of(projectedPositions, residual)`.
5. Anchor entries are copied through unchanged (I1 covers node pairs only; the design does
   not define anchor movement — §8 O3). Every input coordinate is validated finite; a
   non-finite input throws `IllegalArgumentException` (design §5.2). Every output coordinate
   is re-validated finite; otherwise `IllegalArgumentException`.
6. `project` is stateless; it never feeds anything back into the solver.

### 2.2 `org.freeplane.plugin.graph.layout.NodeSeparationResult` (new, **public**)

```java
public final class NodeSeparationResult {
    public static NodeSeparationResult of(LayoutPositions positions, int residualViolations);
    public LayoutPositions positions();
    public int residualViolations();   // invariant: >= 0
}
```

Public because `org.freeplane.plugin.graph.control.LayoutSettleLoop` must call the projection
(review attempt 5, MINOR 2). Never carries `LayoutFrame.UNVERIFIED`.

### 2.3 `LayoutFrame` — residual field

`LayoutFrame.java` fields currently at `:13-17`, factories at `:33-43`.

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

- Constructor invariant: `residualViolations >= UNVERIFIED`; published-frame tests require
  `verified()` and `residualViolations()` equal to an independent all-pair recomputation.
- `withDiagnostics` preserves the raw frame's residual.
- `UNVERIFIED` is the encoding of the design's "explicitly marked unknown" (design §5.2 table,
  `EMPTY_FAILED_FRAME` row). It is allowed only for engine-internal frames; `accept` replaces
  it. `EMPTY_FAILED_FRAME` uses `0`, which is the recomputed value for empty positions.

Frame-factory inventory (all `LayoutFrame.of` sites, verified):

| Site | Residual requirement |
|---|---|
| `GraphStreamLayoutEngine.java:346` (engine) | `UNVERIFIED`; always re-wrapped by `LayoutWorker.accept` (`:280-282`), never published |
| `LayoutWorker.java:34-35` (`EMPTY_FAILED_FRAME`) | `0` (recomputed over empty positions) |
| `LayoutWorker.java:292-293` (normal) | real residual from `NodeSeparationResult` |
| `LayoutWorker.java:346` (failure, retained frame) | retained frame's `residualViolations()` |
| `LayoutSettleLoop.java:744` (worker-failure fallback, `retained == null`) | real residual from projecting `fallbackPositions` |
| `LayoutSettleLoop.java:747` (worker-failure fallback) | retained residual when the retained frame is usable; otherwise the residual of the projected `fallbackPositions` |
| `GraphUpdateCoordinator.java:134-136` (initial empty frame) | `0` |
| `GraphUpdateCoordinator.java:561` (failure republish) | preserved from `state.layout()` |

This is the corrected, complete publication-path enumeration (the design's table omitted the
engine factory and cited the republish as a factory; review attempt 5, MINOR 1).

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
itself does not carry an obstacle set.

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
    public boolean emphaticAtAnchor();          // the I2 exception of design §4 I3/§7
    public Optional<LayoutPoint> leaderStart(); // screen; present iff a leader is drawn
}
```

The painter paints `text()` with `font()` verbatim (derived by `/zoom` under the world
transform) and must not re-select a font; `LabelPlacement.Mode` is deleted with
`LabelPlacement` and only this enum survives. Single-line labels: `height()` is one line high
(design §4 I2).

### 2.6 `LabelFonts` (new, `org.freeplane.plugin.graph.canvas`, public)

```java
public final class LabelFonts {
    public static LabelFonts from(GraphTheme theme);
    public Font full();       // theme.labelFont()        = SansSerif plain 12 (GraphTheme.java:67)
    public Font dense();      // theme.denseLabelFont()   = SansSerif plain 9  (GraphTheme.java:69)
    public Font emphatic();   // theme.emphaticLabelFont()= SansSerif bold 15  (GraphTheme.java:68)
}
```

The theme's 7 pt `overTargetLabelFont` (`GraphTheme.java:70`) is never selected: at
`RenderingLevel.OVER_TARGET` placement returns only forced labels (and required emphatic
enclosure labels), so no label carries the 7 pt face. The `labelFont(RenderingLevel)` overload
and `overTargetLabelFont` become unreferenced after the painter migration (§6).

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
reproduce):

1. Disc obstacles: for every `ProjectedNode` with geometry, the screen bounding square of the
   disc, centre `(W/2 + zoom·(x − centerX), H/2 + zoom·(y − centerY))`, half-extent
   `max(2.0, r·zoom)`.
2. Enclosure-label reservations and previously accepted labels join the obstacle set.
3. Process labels in priority order: forced (selected → hovered → related/search) → enclosure
   labels (emphatic → subtle) → node labels by descending disc radius (stable for equal radii,
   in geometry order).
4. For a label with a `previous` entry: recompute the previous slot's anchor at the current
   centre with the previous text/font/rung; if that rectangle is inside `placementArea` and
   collision-free, keep it. Otherwise fall through to the ladder.
5. Ladder order (rung → candidate slots):
   1. `FULL_NEAR` — full text, base font, slots `ABOVE, BELOW, RIGHT, LEFT, ABOVE_RIGHT, ABOVE_LEFT, BELOW_RIGHT, BELOW_LEFT`
   2. `FULL_DISPLACED` — full text, base font, slots `ABOVE_FAR, BELOW_FAR, RIGHT_FAR, LEFT_FAR`
   3. `DENSE_NEAR` — full text, dense font, near slots
   4. `DENSE_DISPLACED` — full text, dense font, far slots
   5. `TRUNCATED_NEAR` — truncated, base font, near slots
   6. `TRUNCATED_DISPLACED` — truncated, base font, far slots
   7. `TRUNCATED_DENSE_NEAR` — truncated, dense font, near slots
   8. `TRUNCATED_DENSE_DISPLACED` — truncated, dense font, far slots
6. Candidate acceptance: `textWidth(text, font) <= slotMaxWidth(slot)` for non-truncating
   rungs; for truncating rungs `truncateTo(text, font, slotMaxWidth(slot))`; the rectangle must
   lie inside `placementArea` and intersect no obstacle. Accepted rectangles join the obstacle
   set.
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

9. Terminal rungs. An enclosure label: `INTERIOR` when its rectangle is inside the hull
   polygon (`HullGeometry.contains` on all four corners), inside `placementArea`, and
   collision-free; otherwise `EXTERNAL` with a leader (`leaderStart = hull.nearestBoundaryPoint(anchor)`);
   an **emphatic** enclosure label that finds nothing is placed at `hull.labelAnchor()` with
   `emphaticAtAnchor = true` (the I2 exception, recorded on the result, never thrown).
   A subtle enclosure label with nothing left is `HOVER_ONLY`. A non-forced node label with
   nothing left is `HOVER_ONLY` (no obstacle). Forced node labels have no terminal rule in the
   design — see §8 O4.
10. `mode = HOVER_ONLY` labels are returned but are not obstacles and are not painted except
    when forced by paint state.

Derived displacement quantities (used by the design §8.3.6 assertion; `r' = max(2, r·zoom)`,
`w`/`h` the placed logical box):

| Slot | leader length (disc centre → anchor) | corner support (disc centre → farthest corner) |
|---|---|---|
| `ABOVE`, `BELOW` | `r' + 6 + h/2` | `hypot(w/2, r' + 6 + h)` |
| `ABOVE_FAR`, `BELOW_FAR` | `r' + 30 + h/2` | `hypot(w/2, r' + 30 + h)` |
| `RIGHT`, `LEFT` | `r' + 6 + w/2` | `hypot(r' + 6 + w, h/2)` |
| `RIGHT_FAR`, `LEFT_FAR` | `r' + 30 + w/2` | `hypot(r' + 30 + w, h/2)` |
| diagonal | `hypot(r' + 6 + w/2, r' + 6 + h/2)` | `hypot(r' + 6 + w, r' + 6 + h)` |

Structural maxima at zoom 1 with the C10 caps (`h = 16.344114`): `r'=8` → corner support
`≤ 168.199 px` (horizontal far); `r'=14` → `≤ 174.192 px` (horizontal far, vs `173.842 px`
diagonal and `116.796 px` vertical far). The design's single formula must not be used (O7).

### 2.8 `ScreenLabelPlacementCache` key

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
   The measurable consequence is tested by painting ink: each label's ink bitmap must lie
   inside its rectangle and pairwise label ink bitmaps must not intersect, at zooms 0.25, 1.0
   and 2.0. The only sanctioned violation is the emphatic-enclosure exception of I3,
   recorded on the `PlacedLabel` (`emphaticAtAnchor == true`). Labels are single-line, so
   `height` is one line.

**I3 — degradation ladder (ordered).**
`FULL_NEAR → FULL_DISPLACED → DENSE_NEAR → DENSE_DISPLACED → TRUNCATED_NEAR →
TRUNCATED_DISPLACED → TRUNCATED_DENSE_NEAR → TRUNCATED_DENSE_DISPLACED → HOVER_ONLY`.
Forced labels (selected, hovered, related) are placement inputs, placed first, and never below
full text. Emphatic enclosure labels are required; when no slot exists they are forced at the
hull label anchor as the I2 exception, recorded **on the placement result**
(`emphaticAtAnchor`) and never thrown from the paint path. At `RenderingLevel.OVER_TARGET`
only forced labels and required emphatic enclosure labels are placed; all other labels are
absent from the result (the 7 pt face therefore never paints).

**I4 — stability.** `place(request, previous, fonts)` is deterministic: identical inputs give
byte-identical results (same anchors, sizes, fonts, texts, slots). A label keeps its previous
slot while that slot remains collision-free at the current centre/zoom/area; an invalidated
slot is re-placed through the ladder, and re-running with the new result as `previous` is a
fixed point while the new slot remains valid.

## 4. Pipeline, state transitions and error behaviour

### 4.1 Frame lifecycle

| Stage | Producer | Positions | Residual |
|---|---|---|---|
| solver frame | `GraphStreamLayoutEngine` | particle positions | `UNVERIFIED` |
| acceptance | `LayoutWorker.accept` (`:276-300`) | validate coverage → compute hulls → `MapTierCorrection` → **`NodeSeparationProjection.project`** | recomputed |
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
`LABEL("label")` stage (`PerformanceMeasurements.java:27-39`; the removed measurement is the
`GraphWorkspacePerformanceDiagnostic` worker-side label timing at `:355-358`). The stage is
initially diagnostic-only (`-1`); the threshold is set from the recorded baseline (C15).

## 5. Fixtures and expected values

All fixture coordinates below are world coordinates of the committed mockup generator unless
stated otherwise; convert to screen with §0's mapping. Fonts are the screen-space base fonts
`full = SansSerif plain 12`, `dense = SansSerif plain 9`; line heights measured for this
environment are `h(12)=16.344114`, `h(9)=12.258085` px.

### 5.1 Red-phase prominence fixture (`NodeSeparationProjectionShould`)

Construction (edge-constructed prominence only; `GraphProjection` exposes no caller-supplied
prominence):

- Two prominent nodes `p1`, `p2` in the same map, each with **14 distinct visible outgoing
  FORWARD relationship targets** (28 target nodes total; targets placed on a far grid with
  spacing ≥ 100 so no other pair violates).
- `NodeProminence.scaleFor(14) = 1.75` → radius `14.0`; verified `scaleFor(13) = 1.740088`
  (radius 13.920704), hence the threshold "≥ 14" is exact.
- Raw positions: `p1 = (0,0)`, `p2 = (24,0)`.

Expected:
- `violationCount(raw) == 1`.
- After `project`: `p1 = (−5,0)`, `p2 = (29,0)`, both radii 14, centre distance exactly
  `34.0`; `result.residualViolations() == 0`; `violationCount(result.positions()) == 0`;
  passes = 2. The already-satisfied half of the test asserts that an input with distance
  `≥ 34` is returned unchanged.

### 5.2 Sandwich, pinned-pinned and solvable fixtures (`NodeSeparationProjectionShould`)

Keys must sort in the listed order under the pinned comparator (same map UUID, ascending
persisted node id). All radii are 8.

| Fixture | Input (world) | Pins | Expected |
|---|---|---|---|
| **S1 sandwich (non-convergence)** | `a=(0,0)`, `b=(40,0)`, `m=(20,0)` | `a`, `b` | 64 passes (no early exit); `a=(0,0)`, `b=(40,0)` bit-identical; `m=(18,0)`; `residualViolations == 1` (pair `a`–`m`); all positions finite; the published frame carries the same count (design §8.1.6) |
| **S2 pinned-pinned exception** | `a=(0,0)`, `b=(20,0)`, `m=(10,0)` | `a`, `b` | 2 passes; `a=(0,0)`, `b=(20,0)` bit-identical; `m=(42,0)`; `residualViolations == 1` (pair `a`–`b`); pinned pair untouched and counted (design §8.1.2) |
| **S3 solvable convergence** | `a=(0,0)`, `b=(20,0)` | none | 2 passes; `a=(−1,0)`, `b=(21,0)`; `residualViolations == 0`; distance exactly 22 |

The S1 numbers are derived under the pinned C3 arithmetic and ascending pair order; changing
either changes `m` (measured alternatives for order `m,a,b` give different outcomes, so the
comparator is part of the fixture).

### 5.3 Three-map correction characterization (design §8.2.4)

- Three root hulls: squares of half-extent **30** centred at `(0,0)`, `(40,0)`, `(−40,0)`;
  one node per map at the hull centre; no pins.
- Expected correction output (verified against the real `HullGeometry` + `HullIntersection`,
  Appendix A.3): `(0,0)`, `(50,0)`, `(−50,0)`; recomputed A/B `minimumSeparatingTranslation`
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

Expected placement at zoom 1, viewport 1128×364, enclosure obstacle
`(−76, −79.619987, 148.289871, 13.619987)` in world-centred coordinates (the committed
generator's `hullLabelRect` for this scene):

| order | name | slot | font | text | anchor (world-centred) | size (w×h) |
|---|---|---|---|---|---|---|
| 1 | Axiom of Choice | ABOVE | 12 | full | (−17, −56.172057) | 91.104675×16.344114 |
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
label/disc collisions 0. Other measured rows of the same scene: 420×240 and 280×170 →
`full 11 / hidden 1` and `full 7 / dense 4 / hidden 1`; 200×130 → `full 2 / dense 4 /
hidden 6`; zoom 0.25 (1128×364) places 6; zoom 2.0 places 11. Mean leader length at
1128×364 zoom 1 is 48.046026 px, maximum 77.894615 px, leader crossings 0.

### 5.5 Long-label truncation fixture

Six nodes, all radius 8, positions `((i%3−1)·22, (i/3)·22)` for `i=0..5`; node 0 is selected.
Names and measured 12 pt widths:

| # | name | width (px) |
|---|---|---|
| 0 | Well-Ordering Theorem of Choice and Regularity | 274.130005 |
| 1 | Axiom Schema of Replacement and Comprehension | 292.586090 |
| 2 | Transfinite Induction over Ordinal Numbers | 246.722 | 
| 3 | Cardinal Arithmetic under the Continuum Hypothesis | 299.606 |
| 4 | Ultrafilter Lemma and Boolean Prime Ideal Theorem | 295.478 |
| 5 | Kuratowski Zorn Lemma for Partially Ordered Sets | 283.034 |

Expected histograms (zoom 1; enclosure variant adds the obstacle rect
`(−41, −39.619987, 148.289871, 13.619987)`):

| viewport | without enclosure obstacle | with enclosure obstacle | max leader | collisions |
|---|---|---|---|---|
| 1128×364 | dense 1, truncated 5, hover-only 0 | dense 0, truncated 6, hover-only 0 | 78.6565 px | 0 / 0 |
| 500×300 | dense 1, truncated 5, hover-only 0 | dense 0, truncated 6, hover-only 0 | 78.6565 px | 0 / 0 |
| 200×130 | full 1, truncated 2, hover-only 3 | full 1, truncated 1, hover-only 4 | 46.1721 px | 0 / 0 |

The `full 1` at 200×130 is the selected node's forced label placed by the mockup's
unchecked panic fallback (274.130 px wide on a 200 px surface): it is not I2-conformant and
is the subject of §8 O4.

Per-label expectations at 1128×364 without enclosure (zoom 1, world-centred):

| # | slot | font | text | anchor | size |
|---|---|---|---|---|---|
| 0 | ABOVE | 12 | `Well-Ordering Theorem of Choice…` | (−22, −22.172057) | 198.493454×16.344114 |
| 1 | ABOVE_FAR | 12 | `Axiom Schema of Replacement a…` | (0, −46.172057) | 193.873367×16.344114 |
| 2 | BELOW_FAR | 9 | full | (22, 44.129043) | 185.041336×12.258085 |
| 3 | LEFT | 12 | `Cardinal Arithmetic u…` | (−100.392456, 22) | 128.784912×16.344114 |
| 4 | BELOW_FAR | 12 | `Ultrafilter Lemma and Boolean Pr…` | (0, 68.172057) | 198.541412×16.344114 |
| 5 | RIGHT | 12 | `Kuratowski Zorn Lem…` | (100.656464, 22) | 129.312927×16.344114 |

### 5.6 One-pixel stickiness fixture (`ScreenLabelPlacementShould`)

**Pan case.** Dense 12-node fixture, 1128×364, enclosure obstacle as in §5.4. Pan the viewport
origin by `(+1, 0)` px, re-place with `previous` = the original result. Expected: all 11
placed labels keep slot, text and font; histogram unchanged; second re-application with the
new result as `previous` is identical (idempotent).

**Invalidation case.** Dense fixture, viewport 200×130 (world-centred `(−100,−65,200,130)`),
enclosure obstacle `(−76, −79.619987, 148.289871, 13.619987)`. Baseline placement (zoom 1):

| name | slot | font | anchor | size |
|---|---|---|---|---|
| Axiom of Choice | ABOVE | 12 | (−17, −56.172057) | 91.104675×16.344114 |
| Theorem | BELOW_FAR | 9 | (−51, 16.129043) | 38.295258×12.258085 |
| Pairing | LEFT | 9 | (−79.976112, 0) | 29.952225×12.258085 |
| Infinity | RIGHT | 9 | (79.877113, 0) | 29.754227×12.258085 |
| Separation | BELOW | 12 | (−51, 56.172057) | 61.260422×16.344114 |
| Comprehension | BELOW | 9 | (17, 54.129043) | 67.716476×12.258085 |

Move `Infinity` from `(51,0)` to `(51,−1)` and re-place with `previous` = baseline. Expected:
exactly one assignment changes — `Infinity` becomes `BELOW`, font 9, anchor
`(51, 19.129043)`, size unchanged; every other label identical; collisions 0/0; re-running
with the new result as `previous` is a fixed point.

### 5.7 Painted-ink zoom matrix (`ScreenLabelPlacementShould`)

- Fixture: dense 12-node scene (or the long scene) at zoom `z ∈ {0.25, 1.0, 2.0}`; render each
  `PlacedLabel` alone with `label.font()` at its screen position, antialiasing on, into a
  transparent layer; compute the ink bounding box.
- Expected: ink ⊆ `label.bounds()` for every label; pairwise ink bitmaps are disjoint; ink
  misses every disc bitmap of radius `max(2, r·z)`.
- Measured zoom invariance: `"Axiom of Choice"` in 12 pt paints `90×10` px of ink at zooms
  0.25, 0.5, 1.0, 2.0 and 4.0 (the painter's constant screen size). Logical bounds are
  `91.104675×16.344114` px at every zoom. With double-precision centring the residual
  overhang is antialias fringe ≤0.55 px; fixture minimum label–label rectangle gaps are
  1.6559 px (dense, zoom 1) and 7.6559 px (long, zoom 1), so bitmap intersections stay empty.
- The current integer-`stringWidth` centring (`GraphPainter.java:307-312`) is non-conformant
  (measured overhang up to 0.57 px); §6 makes the double-precision change mandatory.

### 5.8 Enclosure-label fixture (derivable part)

Square hull 100×100 centred at `(0,0)` with `labelAnchor = (0,0)`; one emphatic enclosure
label whose screen rectangle fits inside the hull and is collision-free. Expected: mode
`INTERIOR`, anchor exactly `(0,0)`, `emphaticAtAnchor == false`, `leaderStart` empty, and
`geometry.hulls()` unchanged before/after placement. When the anchor rectangle is blocked by
a disc, `mode == EXTERNAL` with a leader from `hull.nearestBoundaryPoint(anchor)`; the exact
anchor is not derivable from the approved design (no exterior candidate order is specified)
and must be recorded by the first implementation run. Emphatic terminal: block every
candidate; expected `emphaticAtAnchor == true`, anchor exactly `(0,0)`, no exception.

## 6. Removal and migration inventory

**Delete**

| Path | Surface |
|---|---|
| `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/LabelPlacementEngine.java` | entire file (560 lines), incl. `MAX_INTERIOR_EXPANSION` (`:22`), hull expansion (interior `:153-249`), `ARC_GAP`, `EXTERNAL_GAP`, `SUBTLE_EXTERNAL_CANDIDATE_BUDGET` |
| `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/LabelPlacement.java` | entire file (139 lines); `Mode` moves to `PlacedLabel` |
| `GraphGeometry` label surface | `GraphGeometry.java` label field (`:16`) and constructor parameter (`:26`), 3-arg `of` (`:46-49`), `labels()` (`:60-62`), `copyLabels` (`:101-117`), label parts of `equals/hashCode/toString` |
| `TypedSpringBox.REST_LENGTH` | `TypedSpringBox.java:18`; other uses at `GraphStreamLayoutEngine.java:230` and `:239` |
| worker-side label stage | `GraphWorkspacePerformanceDiagnostic.java:293-297` and `:355-358`; `PerformanceMeasurements.Stage.LABEL` (`:35`) replaced by `PLACEMENT("placement")` at the same enum position |

**Update**

| Path | Change |
|---|---|
| `.../layout/LayoutFrame.java` | residual field, factories, accessor, validation (§2.3) |
| `.../layout/LayoutWorker.java` | project after correction in `accept` (`:285-293`); `EMPTY_FAILED_FRAME` residual (`:34-35`); retained residual in `failedFrame` (`:340-348`) |
| `.../control/LayoutSettleLoop.java` | delete `LabelAssembler` (`:982-990`); remove `labels.place` at `:543-544` and `:721-722` (keep `computeHulls`); `failedFrame` (`:732-749`) routes `fallbackPositions` (`:933-948`) through the projection and picks the residual per §4.3 |
| `.../control/GraphUpdateCoordinator.java` | initial frame residual `0` (`:133-137`); `publishFailure` preserves residual (`:551-566`) |
| `.../layout/graphstream/GraphStreamLayoutEngine.java` | relationship rest length at `:230` = `radiusOf(first)+radiusOf(second)+MIN_GAP`; containment rest length at `:239` = `R(k)`; new `radiusOf`/`R(k)` helpers from `projection.prominence()`; `Seeds.nodePosition` `:682` uses the disc-derived ring radius `R(k)` = `(2·max_r+MIN_GAP)/(2·sin(π/k))`, `R(k)=0` for `k≤1`; `directNodeRingRadius` stays for `directNodeReach` (`:558`) per N4 |
| `.../canvas/GraphPainter.java` | rewrite `paintLabels` (`:231-287`) to consume `List<PlacedLabel>` and paint the carried font; delete `labelFont` (`:289-298`) and `shouldPaintLabel` (`:301-305`); `drawCentered` (`:307-312`) centres on double bounds (C14); the only `GraphGeometry.labels()` reader (`:262`) disappears |
| `.../canvas/GraphCanvas.java` | own `ScreenLabelPlacementCache`; build `LabelPlacementRequest` in the paint path (`:358-365`) from `canvasState`, `viewport`, `paintState`, `theme`; pass placements to `GraphPainter` |
| `.../canvas/GraphTheme.java` | `labelFont(RenderingLevel)` (`:262-270`) and `overTargetLabelFont` (`:254-256`) become unreferenced; remove once the tree has no callers |
| `.../projection/NodeProminence` | unchanged (MAX_SCALE reference only) |

**New files** (all under `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/`):
`layout/NodeSeparationProjection.java`, `layout/NodeSeparationResult.java`,
`canvas/ScreenLabelPlacement.java`, `canvas/LabelPlacementRequest.java`,
`canvas/PlacedLabel.java`, `canvas/LabelFonts.java`, `canvas/ScreenLabelPlacementCache.java`.

**Test migration** (all under `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/`):

| File | Required work |
|---|---|
| `geometry/LabelPlacementShould.java` | replaced by `canvas/ScreenLabelPlacementShould.java` (design §8.3) |
| `canvas/GraphCanvasPaintShould.java` | 3-arg `GraphGeometry.of` at `:238,:325,:665,:684,:888,:971,:1225,:1253,:1354`; `LabelPlacement.Mode` uses |
| `canvas/GraphInteractionControllerShould.java` | 3-arg `GraphGeometry.of` at `:588` |
| `window/GraphWorkspaceWindowModelShould.java` | 3-arg `GraphGeometry.of` at `:1911,:1979` |
| `window/WorkspaceDialogsShould.java` | 3-arg `GraphGeometry.of` at `:440,:483` |
| `integration/GraphWorkspaceModelAcceptanceShould.java` | `LabelPlacement`/`LabelPlacementEngine` references |
| `performance/GraphWorkspacePerformanceDiagnostic.java` | remove worker-side label measurement `:293-297,:355-358`; add paint-path `PLACEMENT` stage |
| `performance/PerformanceTripwiresShould.java` | stage list `:64-66` (`"label"` → `"placement"`); threshold assertions `:67-79`; fixture hashes `:131-137` do **not** move (workspace XML contains no positions) — §8 O5 |
| `layout/ReferenceRepulsionFixture.java`, `projection/GroupOnlyProjectionShould.java`, `projection/ProjectionDeterminismShould.java`, `projection/StructuralProjectionShould.java` | churn-affected settled expectations (design §8.5) |
| `layout/TypedForcesShould.java` | two-map idle gate `:382` (`twoMapWorkspaceSettlesToIdle`); re-record the baseline before the change (design §8.5 says `LayoutSettleLoopShould`; §8 O6 corrects the location) |
| `layout/MapTierCorrectionShould.java` | add the ±30 hull characterization of §5.3 (existing helper `square()` builds half-extent 1) |
| 13 files constructing `LayoutFrame` | `AccessibleGraphCanvasShould`, `GraphCanvasPaintShould`, `GraphInteractionControllerShould`, `GraphSearchModelShould`, `ContributorDeletionPlanShould`, `GraphUpdateCoordinatorShould`, `LayoutSettleLoopShould`, `GraphWorkspaceCommandAcceptanceShould`, `LayoutWorkerShould`, `PerformanceTripwiresShould`, `GraphWorkspaceUiEvidence`, `GraphWorkspaceWindowModelShould`, `WorkspaceDialogsShould` |
| new | `layout/NodeSeparationProjectionShould`, `canvas/ScreenLabelPlacementShould`, frame-pipeline assertions in `control/LayoutSettleLoopShould`/`layout/LayoutWorkerShould` (design §8.2) |

Additional churn candidates must be enumerated by running the module suite after the change;
the spec-authoring sandbox cannot run Gradle (design §10 fixture churn).

## 7. Acceptance criteria

1. **I1.** `NodeSeparationProjectionShould` passes §5.1–§5.2; every published-frame test
   asserts `residualViolations == 0` on the displacing fixture of §5.1.
2. **Verification closure.** For a normal frame and for a worker-failure fallback frame the
   published residual equals an independent all-pair recomputation on the published
   positions; `EMPTY_FAILED_FRAME` and the initial empty frame carry `0`; retained frames
   carry the retained residual. The omitted-fix guard fails when the projection is disabled.
3. **I2/I3/I4.** `ScreenLabelPlacementShould` passes §5.4–§5.8, including the painted-ink
   matrix at zooms 0.25/1.0/2.0, the OVER_TARGET filter, forced-label behaviour, the
   truncation histogram, the 1 px stickiness fixture and the viewport-change recomputation.
4. **Persistence.** No workspace XML, pin or position is written by the change; the
   `PerformanceTripwiresShould` fixture bytes and hashes are unchanged.
5. **Removal.** No reference to `LabelPlacement`, `LabelPlacementEngine`,
   `GraphGeometry.labels()`, the 3-arg `GraphGeometry.of`, `TypedSpringBox.REST_LENGTH` or
   `MAX_INTERIOR_EXPANSION` remains in main or test sources.
6. **Settle/idle.** `TypedForcesShould.twoMapWorkspaceSettlesToIdle` re-baselined before the
   change; after the change the recorded idle frame count and the 100 stable frames
   (rms ≤ 0.05, max ≤ 0.10) still hold.
7. **Performance.** The `PLACEMENT` stage exists in the diagnostic and records per-trigger
   p95 with the C16 method; the projection stage baseline is recorded; thresholds are set
   from those baselines (C7/C15). Until then performance gates for the two new stages are
   diagnostic-only.
8. **Build.** `gradle :freeplane_plugin_graph:test` (and the full suite) green with Java 21
   from `~/.sdkman/candidates/java/21.0.8-zulu`; Java 8 target preserved.

## 8. Open points

These are contradictions or gaps found while turning revision 5.1 into an implementable
specification. The specification pins a working resolution where one is derivable and states
where implementation is blocked.

- **O1 — `RELAXATION` arithmetic is ambiguous.** Design §5.2: "a violating pair separates by
  half the penetration, scaled by `RELAXATION = 0.5`". Reading A (pinned here): each movable
  node moves `0.5·penetration`, one pinned partner moves `1.0·penetration`. Reading B: the
  pair's total displacement is `0.5·penetration`. Reading B needs 52 passes for a two-body
  fixture (measured) and would make §8.1.6's convergence fixture depend on the cap rather
  than on convergence. Pinned: A (C3).
- **O2 — "nodes sorted by projected key" is not implementable as written.**
  `ProjectedNodeKey` (`ProjectedNodeKey.java`) implements neither `Comparable` nor a natural
  order. Pinned: process nodes in `LayoutPositions.nodes()` iteration order (the immutable
  `LinkedHashMap` copy preserves the deterministic projection order); pair candidates are
  visited in ascending index order. If the implementation prefers an explicit comparator, the
  §5.2 fixture keys are chosen so that ascending `(map UUID, persisted node id)` reproduces
  the same order, but the comparator must be total and hash-independent.
- **O3 — anchors are undefined in the projection.** `project` receives `LayoutPositions`
  (nodes and anchors) and a `Set<ProjectedNodeKey>`, but I1 and the residual cover node pairs
  only. Pinned: anchors are copied through unchanged. No anchor/node or anchor/anchor
  separation is claimed; extend the contract before relying on it.
- **O4 — forced node labels have no terminal rule, and the mockup's fallback violates I2.**
  Design I2 lists only the emphatic-enclosure exception, yet I3 requires forced labels never
  below full text. The committed generator resolves the conflict with an unchecked fallback
  (full text at gap 5, no collision or surface test); measured consequence: the selected
  274.130 px label on the 200×130 long-label surface extends outside `(0,0,200,130)` (§5.5).
  This specification does not adopt that fallback and does not invent a replacement. The
  implementation is blocked on a design decision for this single case (candidates: place at
  the base slot and record an I2 exception flag on the result, or allow a truncated forced
  label). All acceptance fixtures avoid the case.
- **O5 — the design's fixture-hash claim is wrong.** Design §8.5 says
  `PerformanceTripwiresShould.java:64-67` "carries fixture hashes that move". Verified:
  `:64-66` is the exact `Stage.names()` assertion (which does move when `LABEL` becomes
  `PLACEMENT`), while the fixture SHA-256 values are at `:131-137` and hash workspace XML that
  contains no layout positions, so they must **not** move (G5). The spec treats the two
  separately in §6.
- **O6 — idle-baseline location.** Design §8.5 places the two-map idle-frame baseline in
  `LayoutSettleLoopShould`; verified: the two-map settle/idle frame-count gate is
  `layout/TypedForcesShould.java:382` (`twoMapWorkspaceSettlesToIdle`). The spec uses the
  verified location.
- **O7 — single-form corner bound is false.** Design §5.3 bounds "the label rectangle's
  farthest corner" by `max(2, r·zoom) + 30 + halfDiagonal`. Verified counterexample: a
  maximal horizontal label (`r = 14`, `w = 130`, `h = 16.344114`) in a near RIGHT slot has
  support `hypot(14+6+130, 8.172) = 150.222 px`, above the design's 144 headline and far
  above the per-slot half-diagonal value. Measured supports 138.7 px (dense) and 143.5 px
  (long) are real, but the design's formula is not an upper bound. Pinned exact per-slot
  closed forms (§2.7 derived displacement table) and the measured maxima of §5.4/§5.5; the
  §8.3.6 assertion must use the per-slot form, not the design's single formula.
- **O8 — label-measurement space wording is self-contradictory.** Design §5.3 says
  "base font metrics multiplied by zoom, equivalently the base font measured in a
  screen-space FRC". These are not equivalent: the former reserves `91.104675·zoom` px
  (22.776 px at zoom 0.25) while the painter paints ≈90 px of ink. Pinned: screen-space
  bounds of the base font in an identity FRC; equivalence to the painter's derived font is
  measured (Appendix A.2).
- **O9 — paintable ink vs. the reserved rectangle.** The design's I2/§8.3.1 test is only
  satisfiable if reserved rectangles contain painted ink. The current painter centres on the
  integer `FontMetrics.stringWidth` (`GraphPainter.java:307-312`); measured ink overhang up
  to 0.57 px. Pinned: double-precision centring (C14) plus the painted-ink assertion;
  per-glyph overhangs beyond the logical box must be measured and covered rather than
  ignored.
- **O10 — enclosure-label exterior placement is unspecified.** The design deletes
  `LabelPlacementEngine` and requires EXTERNAL with a leader but pins no candidate order,
  lane spacing or budget. `INTERIOR` and the emphatic terminal anchor are pinned (§5.8);
  the exterior anchor must be recorded by the first implementation run.
- **O11 — design naming/citation slips carried into implementation.** The design names both
  `NodeSeparation.MIN_GAP` (design §4) and `NodeSeparationProjection` (design §5.2); this
  specification uses `NodeSeparationProjection.MIN_GAP`. `TypedSpringBox.java:84-89`
  (`scaleRepulsion`) is actually `TypedNodeParticle.java:84-89`; `LabelPlacementEngine.java`
  collision checks are at `:136,:157,:276` (not `:134`); `LayoutFrame` fields are at `:13-17`
  (design cites `:16-30`); the "six frame factories" table actually lists seven construction
  sites. These are documentation corrections only; the affected behaviour is pinned in §2–§6.
- **O12 — the design's "inward slots first, fixed per-edge order" is not what the mockup
  evidence implements.** The committed generator uses one global slot order (§2.7 step 5).
  Pinned: the global order, because all §5 fixture values depend on it. If per-edge ordering
  is required, every placement table in §5 must be re-measured.
- **O13 — projection/placement wall-clock thresholds.** C7/C15 cannot be pinned in a
  read-only specification: both stages must first exist and be measured on the target host,
  as the design itself requires. Reference calibration is provided; no threshold is invented.

## Appendix A — Verification log

Environment: `~/.sdkman/candidates/java/21.0.8-zulu` (Java 21.0.8, Linux, 22 cores),
headless. Working copies of probes under `/tmp`; no repository file other than this
specification was written during verification.

1. **Mockup reproducibility.** `java -Djava.awt.headless=true NodeSeparationMockups.java /tmp/nsspec/mockups`;
   all seven PNGs md5-identical to the committed panels (`012b309e…`, `46ee846c…`,
   `246c36e2…`, `f8c1d2c1…`, `b6ff1064…`, `e23b18be…`, `91927d30…`). Generator stdout
   reproduces every §6/§9 row quoted in the design (dense rows, ladder comparison, long-label
   rows, zoom counts).
2. **Font/space measurements.** `FontProbe`: `h(12)=16.344114`, `h(9)=12.258085`,
   `h(15 bold)=20.430143`; `"Axiom of Choice"` 12 pt logical width `91.104675`;
   screen-FRC vs. derived-world×zoom max delta `2.747e-3` px at zooms 0.25–4; painted ink
   `90×10` px at zooms 0.25/0.5/1/2/4; ink/logical ≤ 1.0 with double centring except antialias
   fringe ≤ 0.55 px.
3. **Correction characterization.** `TierProbe` compiled with the real `HullGeometry` +
   `HullIntersection`: pair translations `(20,0)`, `(−20,0)`, `(0,0)`; final
   `(0,50,−50)`; A/B MST `(10,0)`, overlap 10, `siblingOverlap == true`; ±24 → `(0,44,−44)`;
   ±1 → no-op.
4. **Projection reference implementation.** `ProjectionProbe`/`PerfProbe`: solvable pair
   `(0,0),(20,0)` → `(−1,0),(21,0)`, residual 0, 2 passes; red-phase pair (r=14, d=24) →
   `(−5,0),(29,0)`, residual 0, 2 passes; S1/sandwich r8 20/40 → `m=(18,0)`, residual 1,
   64 passes; S2/pinned-pinned r8 0/20/10 → `m=(42,0)`, residual 1, 2 passes; variant-B
   (total-0.5) needs 52 passes for the solvable pair; dense12 (spacing 34) 1 pass residual 0;
   long6 1 pass residual 0; instrumented perf p95: legal grid `5,130,482 ns`, random area-2000
   `220,769,319 ns`, dense 64-pass cluster `411,584,311 ns`.
5. **Placement fixture tables.** Instrumented copy of the committed generator
   (`/tmp/nsspec/genprobe`): §5.4/§5.5 tables, histograms, mean `48.046026`, maxima
   `77.894615`/`78.6565`, enclosure-variant counts, stickiness cases (pan preserved;
   `Infinity` RIGHT→BELOW), minimum label gaps `1.6559`/`7.6559` px, and the inflation probe
   showing 0.5–2.0 px obstacle margins change the dense histogram (therefore no margin is
   pinned; containment is achieved by C14 instead).
6. **Equilibrium.** 50-digit decimal Newton/bisection on `0.05·d³ − 1.2·d² − 28 = 0`:
   `24.9029940668685734910019045934556374779294569050956460555884`; float 24.902994066868573;
   overlap 3.0970059331314265, shortfall 9.0970059331314265.
7. **Repository citations.** All `file:line` values in §2–§6 re-checked with `grep -n`/`sed`
   against the integration worktree on the day of writing (see the table rows in §2.3 and §6).
