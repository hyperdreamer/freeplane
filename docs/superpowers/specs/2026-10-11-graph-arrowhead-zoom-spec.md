# Graph Workspace: arrowhead size proportional to zoom — technical specification

Task identifier: `2026-10-11-graph-arrowhead-zoom`

Source design: `docs/superpowers/specs/2026-10-11-graph-arrowhead-zoom-design.md`.
This specification implements the approved design without extending or contradicting it. The
approved 1x appearance being preserved is the one specified by
`docs/superpowers/specs/2026-10-10-graph-arrowhead-prominence-spec.md`.

## 1. Scope

### 1.1 Production change

The whole production change is confined to
`freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPainter.java`:

1. Replace the constant block at lines 38–44: delete `ARROW_HEAD_MINIMUM_LENGTH_PX` (line 39) and
   `ARROW_HEAD_LENGTH_PER_ZOOM` (line 42); add one constant, `ARROW_HEAD_WORLD_LENGTH = 12.5`;
   keep `ARROW_HEAD_HALF_BASE_RATIO = 0.42` (line 44).
2. Replace the body of `arrowHeadLengthPx` (lines 158–161) with
   `return ARROW_HEAD_WORLD_LENGTH * zoom;`.
3. Change nothing else. In particular, keep unchanged: the package-private helpers
   `availableArrowHeadWorldLength` (line 164), `effectiveArrowHeadLengthPx` (line 170) and
   `arrowHeadHalfBasePx` (line 175); the `paintArrow` signature and body (line 179 onward); both
   `paintEdges` call sites (lines 144 and 148); the finite-length guard (line 184); the axis,
   perpendicular and path construction; the fill; and every other paint method.

No other production file changes. The test changes are confined to
`freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java`
and are specified in §7.

### 1.2 Non-goals

- No change to the edge line width, node radius or prominence, label or leader geometry,
  multiplicity cue, pins, hulls, or the dim / hover / selection / highlight paths.
- No change to `GraphCanvas.showArrowheads` (`GraphCanvas.java:63` initialises it to `true`) or to
  `GraphTheme` light and dark colours.
- No per-target-node sizing; no arrowhead shape variants (open, stealth).
- No fade-out, no suppression threshold, and no screen-space floor for sub-pixel heads.
- No new colour, setting, preference, translation key, or persisted XML attribute; no
  `DisplaySettings`, `CanvasTheme`, `GraphViewport` or workspace XML codec change.
- No zoom clamp: `GraphViewport` continues to reject only non-finite or non-positive zoom
  (`GraphViewport.java:25–28`, `"zoom must be positive"`, reached through `GraphViewport.of` at
  line 36).

### 1.3 One active rendering path

The `max(12.5, 8.68 x zoom)` rule is replaced, not retained. After the change the identifiers
`ARROW_HEAD_MINIMUM_LENGTH_PX` and `ARROW_HEAD_LENGTH_PER_ZOOM` do not occur anywhere in
`freeplane_plugin_graph/src/main/java`, and there is no fallback branch, no second constant and no
second paint path. `grep -rn "ARROW_HEAD_MINIMUM_LENGTH_PX\|ARROW_HEAD_LENGTH_PER_ZOOM"`
`freeplane_plugin_graph/src/main/java` must produce no output (§8 gate).

## 2. The rule: exact before and after

Let `d` be the world distance between the two attachment points of the edge, i.e. the
`length = sqrt(dx * dx + dy * dy)` already computed inside `paintArrow` (`GraphPainter.java:183`).
Let `a` be the available attachment distance in world units.

**Before (shipped),** `GraphPainter.java:39–44, 158–161, 170–172`:

```java
    private static final double ARROW_HEAD_MINIMUM_LENGTH_PX = 12.5;      // screen px floor
    private static final double ARROW_HEAD_LENGTH_PER_ZOOM = 0.62 * 14.0; // = 8.68 screen px per zoom
    private static final double ARROW_HEAD_HALF_BASE_RATIO = 0.42;

    static double arrowHeadLengthPx(final double zoom) {
        return Math.max(ARROW_HEAD_MINIMUM_LENGTH_PX, ARROW_HEAD_LENGTH_PER_ZOOM * zoom);
    }

    static double effectiveArrowHeadLengthPx(final double zoom, final double availableWorldLength) {
        return Math.min(arrowHeadLengthPx(zoom), availableWorldLength * zoom);
    }
```

i.e. `L = min(max(12.5, 8.68 × zoom), a × zoom)` screen px. The `12.5` is a set of screen pixels;
the world length it implies is `12.5 / zoom` and grows without bound as the user zooms out.

**After (this change):**

```java
    // World units. 12.5 / 14.0 ~ 0.8929 of the maximum node radius:
    // GraphGeometryEngine.BASE_RADIUS 8.0 x NodeProminence.MAX_SCALE 1.75 = 14.0 world.
    private static final double ARROW_HEAD_WORLD_LENGTH = 12.5;
    // Head base is 0.84 x length; the aspect is preserved when the cap shrinks a head.
    private static final double ARROW_HEAD_HALF_BASE_RATIO = 0.42;

    // Screen px.
    static double arrowHeadLengthPx(final double zoom) {
        return ARROW_HEAD_WORLD_LENGTH * zoom;
    }
```

with `a` and the composition unchanged:

- one head: `a = d`; both ends carry a head: `a = d / 2` (the bidirectional halving, §5.2).
- requested head length: a constant `12.5` world units, i.e. `arrowHeadLengthPx(zoom) = 12.5 × zoom`
  screen px; there is no floor and no crossover.
- effective head length: `L = min(12.5 × zoom, a × zoom)` screen px. For finite positive `zoom`
  this is the same double value as `min(12.5, a) × zoom` (multiplication by a positive constant is
  monotone under IEEE round-to-nearest), and the cap decision is the world comparison `a < 12.5`.
- head half-base: `0.42 × L`; base `0.84 × L`; aspect `0.84 : 1` at every zoom and for both the
  capped and the uncapped case.

The literal `12.5` is the old 1x floor length reinterpreted as a world constant. Its provenance is
documentation only: `12.5 / 14.0 ≈ 0.8928571428571429`, and `12.5 / 28.0 ≈ 0.44642857142857145` of
the largest node diameter. At exactly `zoom = 1.0` old and new are the identical arithmetic:
`min(max(12.5, 8.68 × 1.0), a × 1.0) = min(12.5, a) = min(12.5 × 1.0, a × 1.0)`.

## 3. Contracts

All additions live in `org.freeplane.plugin.graph.canvas.GraphPainter`. The class stays
package-private and `final`; the four helpers stay package-private `static`, matching the existing
`GraphPainter.leaderStroke` test-access pattern, so same-package tests can call them directly.

### 3.1 Constants

| Constant | Visibility / kind | Value | Unit | Meaning |
| --- | --- | --- | --- | --- |
| `ARROW_HEAD_WORLD_LENGTH` | `private static final double` | `12.5` | world units | requested head length, a constant world size: `12.5 / 14.0 ≈ 0.8929` of the maximum node radius `14.0` world |
| `ARROW_HEAD_HALF_BASE_RATIO` | `private static final double` | `0.42` | dimensionless | half-base per unit head length; unchanged |

Removed: `ARROW_HEAD_MINIMUM_LENGTH_PX` (was `private static final double`, `12.5`, screen px) and
`ARROW_HEAD_LENGTH_PER_ZOOM` (was `private static final double`, `0.62 * 14.0 = 8.68`, screen px
per zoom unit). They are not renamed, aliased or retained behind a fallback.

`12.5` cannot be written as `0.8929 * (BASE_RADIUS * MAX_SCALE)` in code:
`GraphGeometryEngine.BASE_RADIUS` (`GraphGeometryEngine.java:23`) and
`NodeProminence.MAX_SCALE` (`NodeProminence.java:6`) are both `private static final`, and the node
radius is only formed at `GraphGeometryEngine.java:95` and `:168` as
`BASE_RADIUS * projection.prominence().get(node.key()).scale()`. The constant is therefore the
literal `12.5`; the ratio appears in the comment only.

### 3.2 Package-private static helpers

```java
    // Screen px.
    static double arrowHeadLengthPx(final double zoom) {
        return ARROW_HEAD_WORLD_LENGTH * zoom;
    }

    // World units in, world units out.
    static double availableArrowHeadWorldLength(final double attachmentDistanceWorld,
            final boolean arrowAtFirst, final boolean arrowAtSecond) {
        return arrowAtFirst && arrowAtSecond ? attachmentDistanceWorld / 2.0 : attachmentDistanceWorld;
    }

    // Screen px.
    static double effectiveArrowHeadLengthPx(final double zoom, final double availableWorldLength) {
        return Math.min(arrowHeadLengthPx(zoom), availableWorldLength * zoom);
    }

    // Screen px.
    static double arrowHeadHalfBasePx(final double lengthPx) {
        return ARROW_HEAD_HALF_BASE_RATIO * lengthPx;
    }
```

| Helper | Argument semantics | Return unit | Exact expression returned |
| --- | --- | --- | --- |
| `arrowHeadLengthPx(zoom)` | `zoom`: screen px per world unit, positive finite in production (`GraphViewport` validation) | screen px | `12.5 × zoom` (requested length; no cap, no floor) |
| `availableArrowHeadWorldLength(d, arrowAtFirst, arrowAtSecond)` | `d`: world distance between the edge's two attachment points, `≥ 0`; the two flags are the edge's own `edge.arrowAtFirst()` / `edge.arrowAtSecond()` | world units | `arrowAtFirst && arrowAtSecond ? d / 2.0 : d` |
| `effectiveArrowHeadLengthPx(zoom, availableWorldLength)` | `zoom`: screen px per world unit; `availableWorldLength`: `a` in world units | screen px | `min(arrowHeadLengthPx(zoom), a × zoom)`, i.e. `min(12.5 × zoom, a × zoom)` |
| `arrowHeadHalfBasePx(lengthPx)` | `lengthPx`: the effective `L` in screen px | screen px | `0.42 × L` |

The `d` versus `d / 2` decision exists only inside `availableArrowHeadWorldLength`; it is never
taken inline at a call site. A head is always described by `L = effectiveArrowHeadLengthPx(...)`
screen px and the world-space coordinates `L / zoom` (length along the edge) and
`arrowHeadHalfBasePx(L) / zoom` (half-base perpendicular to the edge).

### 3.3 Exact call chain inside `paintArrow`

`paintArrow` keeps its signature (`GraphPainter.java:179–180`) and its body except for the values
that flow through `arrowHeadLengthPx`:

```java
    private static void paintArrow(final Graphics2D graphics, final LayoutPoint tip, final LayoutPoint from,
            final Color color, final double zoom, final boolean arrowAtFirst, final boolean arrowAtSecond) {
        final double dx = tip.x() - from.x();
        final double dy = tip.y() - from.y();
        final double length = Math.sqrt(dx * dx + dy * dy);
        if (!(length > 0.0) || !Double.isFinite(length)) {
            return;
        }
        final double unitX = dx / length;
        final double unitY = dy / length;
        final double perpendicularX = -unitY;
        final double perpendicularY = unitX;
        final double availableWorldLength =
            availableArrowHeadWorldLength(length, arrowAtFirst, arrowAtSecond);
        final double arrowLengthPx = effectiveArrowHeadLengthPx(zoom, availableWorldLength);
        final double arrowLength = arrowLengthPx / zoom;
        final double arrowWidth = arrowHeadHalfBasePx(arrowLengthPx) / zoom;
        final Path2D.Double arrow = new Path2D.Double();
        arrow.moveTo(tip.x(), tip.y());
        arrow.lineTo(tip.x() - unitX * arrowLength + perpendicularX * arrowWidth,
            tip.y() - unitY * arrowLength + perpendicularY * arrowWidth);
        arrow.lineTo(tip.x() - unitX * arrowLength - perpendicularX * arrowWidth,
            tip.y() - unitY * arrowLength - perpendicularY * arrowWidth);
        arrow.closePath();
        graphics.setColor(color);
        graphics.fill(arrow);
    }
```

Ordered call chain (the only ordering that may not change):

1. `dx`, `dy` in world units.
2. `length = d`, the guard `!(length > 0.0) || !Double.isFinite(length)` returns before any sizing.
3. `unitX/unitY` and `perpendicularX/perpendicularY` from the direction.
4. `availableWorldLength = availableArrowHeadWorldLength(d, arrowAtFirst, arrowAtSecond)` → `a`.
5. `arrowLengthPx = effectiveArrowHeadLengthPx(zoom, a)` → `arrowHeadLengthPx(zoom)` →
   `ARROW_HEAD_WORLD_LENGTH * zoom` → `min(requested, a × zoom)` = `L`.
6. `arrowLength = L / zoom` world; `arrowWidth = arrowHeadHalfBasePx(L) / zoom = 0.42 × L / zoom`
   world.
7. The triangle is built in world coordinates with the tip exactly at the geometry attachment
   point, then filled (`graphics.fill`) with `theme.edgeColor()` supplied by the call site. There is
   no outline draw and no composite or stroke change; the head inherits the active
   `AlphaComposite` (in particular the dim `0.28f`).

### 3.4 Unchanged call sites

`paintEdges` passes the flags at both sites (`GraphPainter.java:144–149`):

```java
            if (showArrowheads && edge.arrowAtFirst()) {
                paintArrow(graphics, first, second, theme.edgeColor(), viewport.zoom(),
                    edge.arrowAtFirst(), edge.arrowAtSecond());
            }
            if (showArrowheads && edge.arrowAtSecond()) {
                paintArrow(graphics, second, first, theme.edgeColor(), viewport.zoom(),
                    edge.arrowAtFirst(), edge.arrowAtSecond());
            }
```

`paintArrow` stays `private static`; `arrowAtFirst` and `arrowAtSecond` keep their meaning
(`edge.arrowAtFirst()`, `edge.arrowAtSecond()`); `first`/`second` are the geometry attachment points
from `GraphGeometry.edgeAttachment` (`GraphGeometry.java:46`), which is
`NodeGeometry.boundaryToward` for a node endpoint (`GraphGeometry.java:54`) and
`HullGeometry.nearestBoundaryPoint` for an enclosure endpoint (`GraphGeometry.java:60`,
`HullGeometry.java:87`).

## 4. Number tables

### 4.1 Requested head length per zoom, uncapped (edge with room)

"Uncapped" means `a ≥ 12.5` world for the new rule (one head: `d ≥ 12.5`; two heads: `d ≥ 25`) and
`a ≥ max(12.5 / zoom, 8.68)` world for the shipped rule. Node diameter is `16 × zoom` to
`28 × zoom` px, because the node radius is `GraphGeometryEngine.BASE_RADIUS 8.0 × scale` with
`scale` in `[1.0, 1.75]` (`NodeProminence.scale()`, `NodeProminence.java:30–37`, clamps at
`MAX_SCALE = 1.75`, `NodeProminence.java:6`), `BASE_RADIUS` at `GraphGeometryEngine.java:23`,
radius formed at `GraphGeometryEngine.java:95`/`:168`.

| zoom | node diameter px (`16 z` … `28 z`) | shipped `L` px (`max(12.5, 8.68 z)`) | new `L` px (`12.5 z`) | shipped `L` : largest diameter | new `L` : largest diameter |
| --- | --- | --- | --- | --- | --- |
| 0.1485 | 2.376 … 4.158 | 12.5 | 1.85625 | 3.0063 | 0.4464 |
| 0.378 | 6.048 … 10.584 | 12.5 | 4.725 | 1.1810 | 0.4464 |
| 0.75 | 12.0 … 21.0 | 12.5 | 9.375 | 0.5952 | 0.4464 |
| 1.0 | 16.0 … 28.0 | 12.5 | 12.5 | 0.4464 | 0.4464 |
| 2.798 | 44.768 … 78.344 | 24.28664 | 34.975 | 0.3100 | 0.4464 |

At every zoom the new uncapped head is `12.5 / 28.0 ≈ 0.4464` of the largest node diameter and
`12.5 / 16.0 = 0.78125` of the smallest; the shipped ratio varies from 3.0063 to 0.3100.

### 4.2 Exact recorded viewport zooms (used by the acceptance evidence, §9)

| Recorded file | exact zoom | new `L` px (uncapped) | shipped `L` px (uncapped) | node diameter px |
| --- | --- | --- | --- | --- |
| `math.fpg` | 0.14846481908261 | 1.855810238532625 | 12.5 | 2.37543710532176 … 4.1570149343130804 |
| `math.sync-conflict-20261011-131807-QPOKO56 (Arch-MSSD0's conflicted copy 2026-10-11).fpg` | 0.37809529736878794 | 4.726191217109849 | 12.5 | 6.049524757900607 … 10.586668326326063 |
| the two 2026-10-11 15:47 files | 0.7487174552196292 | 9.358968190245365 | 12.5 | 11.979479283514067 … 20.964088746149617 |
| seeded 1.0 capture (`before-100.fpg`) | 1.0 | 12.5 | 12.5 | 16.0 … 28.0 |
| `math.sync-conflict-20261011-131807-QPOKO56.fpg` | 2.7979997032776502 | 34.97499629097063 | 24.286637424450003 | 44.767995252442404 … 78.3439916917742 |

### 4.3 Capped heads at `d = 6` world (the pinned test fixture)

At `d = 6`, `a = 6` with one head and `a = d / 2 = 3` with two; both are below `8.68` and below
`12.5`, so **the shipped and the new rule produce identical values at every zoom** and the fixture
cannot distinguish them (`NodeSeparationProjection.MIN_GAP = 6.0`,
`NodeSeparationProjection.java:20`, is the nominal minimum boundary-to-boundary attachment
distance). Half-base `= 0.42 × L`, base `= 0.84 × L`, base world `= 0.84 × a`.

| Arrows | `a` world | zoom | `L` px | half-base px | base px | base world | base : `1.4`-world line | aspect |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| one head | 6.0 | 0.6 | 3.6 | 1.512 | 3.024 | 5.04 | 3.6 : 1 | 0.84 : 1 |
| one head | 6.0 | 1.0 | 6.0 | 2.52 | 5.04 | 5.04 | 3.6 : 1 | 0.84 : 1 |
| one head | 6.0 | 3.0 | 18.0 | 7.56 | 15.12 | 5.04 | 3.6 : 1 | 0.84 : 1 |
| two heads | 3.0 | 0.6 | 1.8 | 0.756 | 1.512 | 2.52 | 1.8 : 1 | 0.84 : 1 |
| two heads | 3.0 | 1.0 | 3.0 | 1.26 | 2.52 | 2.52 | 1.8 : 1 | 0.84 : 1 |
| two heads | 3.0 | 3.0 | 9.0 | 3.78 | 7.56 | 2.52 | 1.8 : 1 | 0.84 : 1 |

### 4.4 The divergent capped probe: `d = 10` world at zoom 3.0

This is the smallest fixture the test specification uses that separates the two rules at a non-1.0
zoom: the tip is at world `x = 10`, the enclosure boundary at world `x = 0`, so `a = d = 10` is
above the shipped growth term `8.68` but below the new constant `12.5`.

| Rule | requested px | capped px | measured px if rasterised as in §7.5 |
| --- | --- | --- | --- |
| shipped `min(max(12.5, 8.68 × 3.0), 10 × 3.0)` | 26.04 | 26.04 | ≈ 26 |
| new `min(12.5 × 3.0, 10 × 3.0)` | 37.5 | 30.0 | ≈ 30 |

### 4.5 Where the cap binds

The cap is active for an edge when `effectiveArrowHeadLengthPx(zoom, a) < arrowHeadLengthPx(zoom)`;
the new predicate simplifies to `a < 12.5` world, independent of `zoom`.

| zoom | shipped cap binds for `a <` (world) | new cap binds for `a <` (world) |
| --- | --- | --- |
| 0.1485 | 84.17508417508418 | 12.5 |
| 0.378 | 33.06878306878307 | 12.5 |
| 0.75 | 16.666666666666668 | 12.5 |
| 1.0 | 12.5 | 12.5 |
| 2.798 | 8.68 | 12.5 |

The shipped threshold is `12.5 / zoom` below the shipped crossover
`12.5 / 8.68 ≈ 1.4400921658986177` and `8.68` above it; the new threshold is the constant `12.5`.
At exactly `zoom = 1.0` the two thresholds coincide, so the 1x rendering is unchanged on every
edge, capped or not.

## 5. Cap contract

### 5.1 Constant binding set

`a = availableArrowHeadWorldLength(d, arrowAtFirst, arrowAtSecond)` is `d` for one head and `d / 2`
for two. The cap binds when `a < 12.5` world at every zoom:

- one head: capped for `d < 12.5` world;
- two heads: capped for `d < 25` world.

Because the rule is `12.5 × zoom` px, exactly the same edges are capped at every zoom; the cap is
not a function of zoom. A capped head has world length exactly `a` (one head: `d`; two heads:
`d / 2`); an uncapped head has world length exactly `12.5`. The cap is a world-space bound on the
requested length, not a bound on the screen size.

### 5.2 Bidirectional halving

`availableArrowHeadWorldLength` (`GraphPainter.java:164–167`) returns `d / 2.0` only when both
`arrowAtFirst` and `arrowAtSecond` are true, and `d` otherwise; the `paintArrow` call sites pass the
same two flags for both ends (`GraphPainter.java:144–149`), so the two heads of a bidirectional
edge always compute the same `a` and the same `L`. When capped they have world length `d / 2` each
and their base planes meet exactly at the edge midpoint, base to base. When uncapped
(`d > 25` world) their base planes are `12.5` world from their respective tips, so they leave
`d − 25` world of bare line between them.

### 5.3 Aspect

The half-base is derived from the effective (possibly capped) `L`, never from the requested
`12.5 × zoom`: `arrowHeadHalfBasePx(L) = 0.42 × L`, base `0.84 × L`. Therefore a capped head keeps
the triangular aspect `0.84 : 1` and shrinks in both dimensions together; its base is narrower than
the `1.4`-world line exactly when `a < 1.4 / 0.84` world, i.e. mathematically `a < 5 / 3 ≈
1.6666666666666667` world (§6.3).

### 5.4 Base-plane placement

- one head uncapped (`d > 12.5`): base plane at `12.5` world from the tip, strictly before the far
  attachment point;
- one head capped (`d < 12.5`): base plane exactly at the far attachment point, never beyond it;
- two heads uncapped (`d > 25`): base planes at `12.5` world from each tip;
- two heads capped (`d < 25`): base planes coincide at the midpoint.

## 6. Edge cases

### 6.1 Sub-pixel heads at low zoom — no suppression

At `zoom = 0.14846481908261` an uncapped head is `12.5 × 0.14846481908261 = 1.855810238532625` px
long; at `0.37809529736878794` it is `4.726191217109849` px. There is no branch, threshold or
fallback that suppresses a head for small `L`: the guard at `GraphPainter.java:184` tests only
`!(length > 0.0) || !Double.isFinite(length)`; every other finite positive `d` builds and fills a
triangle. On a low-zoom node (diameter `16 × zoom` to `28 × zoom` px) the head is smaller than
the node disc rather than node-sized; this is the intended replacement of the previous
"legible at every zoom" goal.

### 6.2 Unbounded growth with zoom

`L = 12.5 × zoom` px grows linearly with no clamp. `GraphViewport` rejects only non-finite zoom and
`zoom <= 0.0` (`GraphViewport.java:25–28`, `GraphViewport.of` at line 36); there is no maximum. At
the recorded `2.7979997032776502` the uncapped head is `34.97499629097063` px; larger zooms scale
proportionally. The world-space head length is capped at `12.5`, never the screen size.

### 6.3 Short edges and the nub crossover `a < 1.667` world

When the cap binds, the base is `0.84 × a` world, so the capped base is narrower than the
`1.4`-world edge line (`GraphTheme.java:59`,
`new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)`) exactly when
`a < 1.4 / 0.84` world, i.e. mathematically `a < 5 / 3 ≈ 1.6666666666666667` world (the design's
"about 1.667"; note `1.4 / 0.84` evaluates to `1.6666666666666665` in double, one ulp below
`5 / 3`, and nothing in production computes this threshold). Below
that crossover the head reads as a small nub merged into the line. This is reachable for
node-to-node edges only as a recorded residual violation, because `NodeSeparationProjection`
keeps a nominal `MIN_GAP = 6.0` world between node boundaries and residual violations are recorded
rather than rejected; enclosure endpoints take part in no separation pass, so nothing holds a
hull-to-node edge at or above the 6-world `MIN_GAP` - such an edge can have `d < 5/3` world (and
may equally be much longer, as the spec's `d = 10` probe shows). The crossover is on `a`, not on `d`: a head is
sub-line-width for `d < 1.667` world with one head and for `d < 3.333` world with two.

### 6.4 Enclosure endpoints

A head tip may lie on a hull boundary rather than a node disc
(`GraphGeometry.edgeAttachment` → `HullGeometry.nearestBoundaryPoint`, `GraphGeometry.java:60`).
Head orientation, length, base and fill depend only on the edge direction, `zoom` and `a`, so node
and enclosure endpoints behave identically. Hulls are painted before edges
(`GraphPainter.java:76` then `:77`), so nothing overpaints a head that points at an enclosure; a
head that points at a node is overpainted only by the node ring (§6.5).

### 6.5 Node ring overpaint

The node ring is stroked with `theme.edgeStroke()`, the `1.4`-world `BasicStroke`, centred on the
node boundary and painted after the edges (`paintEdges` at `GraphPainter.java:77`, `paintNodes` at
`:79`), so it covers the first `0.7 × zoom` screen px of the head by the tip: `0.10392537335782699`
px at `0.14846481908261`, `0.2646667081581515` px at `0.37809529736878794`, `0.7` px at `1.0`,
`2.0999999999999996 ≈ 2.1` px at `3.0`. The node fill covers only the disc interior, which starts
at the tip and lies on the far side of the head; the head does not overshoot into the disc under
either the capped or the uncapped rule (§5.4).

### 6.6 Dimming

The head is filled with the active `Graphics2D` composite (`graphics.fill(arrow)`,
`GraphPainter.java:204`) and `paintArrow` never changes it. Under the dim-unrelated state the
active composite is `AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.28f)`
(`setOpacity`, `GraphPainter.java:454–458`), so the head dims exactly as today and in the same
paint order.

### 6.7 Fractional zoom

The triangle path is built in world coordinates from `arrowLength = L / zoom` and
`arrowWidth = 0.42 × L / zoom` and then transformed by the canvas `AffineTransform`
(`worldTransform`, `GraphPainter.java:90–97`); it is not rasterised at a fixed size and `L` is not
rounded. The head therefore renders without ragged edges at any fractional zoom.

### 6.8 Degenerate direction

`d = 0` or non-finite `d` returns at the guard `GraphPainter.java:184` before
`availableArrowHeadWorldLength`, `effectiveArrowHeadLengthPx` or `arrowHeadHalfBasePx` is reached;
the cap cannot be evaluated with `d = 0`.

## 7. Test specification

All changes extend
`freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java`.
Conventions stay JUnit 4 (`@Test`, `org.junit.Test`), AssertJ (`assertThat`, `within`,
`isCloseTo`, `isBetween`, `isLessThan`), exact-RGB predicates (`colorPixelsIn`) and the existing
near-colour predicate (`nearColorPixelsIn`) where exact matching cannot see sub-pixel ink, as in
the existing cap test.

### 7.0 Reused helpers (all already present in the class)

| Helper | Current line | Role |
| --- | --- | --- |
| `paint(state, paintState, theme, level)` | 1687 | paints at zoom 1.0 via `GraphViewport.of(0.0, 0.0, 1.0)` |
| `paintAtZoom(state, paintState, theme, zoom)` | 1698 | paints at an arbitrary zoom |
| `measuredHeadLengthPx(image, tipScreen, edgeColor, zoom)` | 1048 | walks away from the attachment point; largest distance whose perpendicular exact-colour run is `≥ max(3.0, 1.4 × zoom + 1.0)` |
| `perpendicularRunPx(image, edgeColor, column, baselineY)` | 1043 | exact-colour run over `baselineY ± 32` |
| `maximumPerpendicularRunPx(image, edgeColor, firstColumn, lastColumn, baselineY)` | 1062 | largest run over a column range |
| `capFixture(bidirectional)` | 1119 | pinned `d = 6` enclosure-to-node fixture, viewport `GraphViewport.of(0.0, 0.0, 1.0)`, `SIZE = 240 × 140`, world origin screen `(120, 70)`, edge along screen `y = 70`, enclosure boundary screen `x = 120`, target node centre world `(14, 0)` radius `8.0`, head tip screen `(126, 70)` |
| `paintCanvas(canvas)` | 1709 | paints a configured `GraphCanvas` |
| `lightTheme()` | 1195 | `GraphTheme.resolve(CanvasTheme.LIGHT, registeredMaps())` |
| `fixture(firstRadius, secondRadius)` | 1725 | the two-node roomy fixture; `fixture(14.0, 14.0)` puts the head on a radius-14 node with `d = 62` world and attachment screen `(151, 70)` at 1.0 and `(213, 70)` at 3.0 |

The tolerance thresholds come from the same pinned helper: `max(3.0, 1.4 × zoom + 1.0)` is
`3.0` at 0.6 and 1.0, and `5.2` at 3.0.

### 7.1 MODIFY (rename + rewrite): `arrowHeadLengthIsFlooredUntilTheCrossoverAndGrowsStrictlyAboveIt` → `arrowHeadLengthIsProportionalToZoom`

The old name encodes the removed floor/crossover; rename it. Full body:

```java
    @Test
    public void arrowHeadLengthIsProportionalToZoom() {
        assertThat(GraphPainter.arrowHeadLengthPx(0.6)).isCloseTo(7.5, within(1e-9));
        assertThat(GraphPainter.arrowHeadLengthPx(0.378)).isCloseTo(4.725, within(1e-9));
        assertThat(GraphPainter.arrowHeadLengthPx(1.0)).isCloseTo(12.5, within(1e-9));
        assertThat(GraphPainter.arrowHeadLengthPx(1.44)).isCloseTo(18.0, within(1e-9));
        assertThat(GraphPainter.arrowHeadLengthPx(3.0)).isCloseTo(37.5, within(1e-9));
        assertThat(GraphPainter.arrowHeadLengthPx(0.5))
            .isLessThan(GraphPainter.arrowHeadLengthPx(1.0));
        assertThat(GraphPainter.arrowHeadHalfBasePx(12.5)).isCloseTo(5.25, within(1e-9));
    }
```

Tolerances `within(1e-9)` on every double. Anchors: direct calls on `GraphPainter`, no fixture.
Falsifiers on the shipped code: `12.5` at `0.6` (expects `7.5`), `12.5` at `0.378` (expects
`4.725`), `12.5` at `1.44` (expects `18.0` - just below the old floor crossover `12.5 / 8.68`),
`26.04` at `3.0` (expects
`37.5`), and `12.5 == 12.5` for the strict `isLessThan` pair. The `12.5` at 1.0 and `5.25`
half-base assertions pass on both rules and are retained as invariants.

### 7.2 MODIFY: `arrowHeadRuleCapAndBidirectionalSplitAreSelectedInTheHelpers`

Keep every existing assertion and insert two lines after the existing
`effectiveArrowHeadLengthPx(1.0, 100.0)` assertion:

```java
        assertThat(GraphPainter.effectiveArrowHeadLengthPx(0.6, 100.0)).isCloseTo(7.5, within(1e-9));
        assertThat(GraphPainter.effectiveArrowHeadLengthPx(3.0, 100.0)).isCloseTo(37.5, within(1e-9));
```

The resulting method also keeps unchanged:

```java
        assertThat(GraphPainter.availableArrowHeadWorldLength(6.0, false, false)).isCloseTo(6.0, within(1e-9));
        assertThat(GraphPainter.availableArrowHeadWorldLength(6.0, true, true)).isCloseTo(3.0, within(1e-9));
        assertThat(GraphPainter.availableArrowHeadWorldLength(6.0, true, false)).isCloseTo(6.0, within(1e-9));
        assertThat(GraphPainter.availableArrowHeadWorldLength(6.0, false, true)).isCloseTo(6.0, within(1e-9));
        assertThat(GraphPainter.effectiveArrowHeadLengthPx(1.0, 100.0)).isCloseTo(12.5, within(1e-9));
        assertThat(GraphPainter.effectiveArrowHeadLengthPx(0.6, 6.0)).isCloseTo(6.0 * 0.6, within(1e-9));
        assertThat(GraphPainter.effectiveArrowHeadLengthPx(1.0, 3.0)).isCloseTo(3.0, within(1e-9));
        assertThat(GraphPainter.arrowHeadHalfBasePx(GraphPainter.effectiveArrowHeadLengthPx(0.6, 6.0)))
            .isCloseTo(0.42 * 6.0 * 0.6, within(1e-9));
```

`6.0 * 0.6` is `3.5999999999999996` in double arithmetic; keep the expression, never the literal
`3.6`. Both flags stay pinned individually (`(6.0, true, false)` and `(6.0, false, true)` are both
`6.0`), so a helper that halves on a single flag is caught here. Falsifier on the shipped code:
`effectiveArrowHeadLengthPx(0.6, 100.0)` returns `12.5` (expects `7.5`); the `(3.0, 100.0)` line
returns `26.04` (expects `37.5`). The remaining assertions pass on both rules.

### 7.3 MODIFY: `paintedArrowHeadLengthTracksTheZoomRule`

Only the 3.0 expectations and the last sample change. Full body:

```java
    @Test
    public void paintedArrowHeadLengthTracksTheZoomRule() {
        Fixture fixture = fixture(14.0, 14.0);
        GraphTheme theme = lightTheme();
        BufferedImage imageOne = paintAtZoom(fixture.state, GraphPaintState.empty(), theme, 1.0);
        BufferedImage imageThree = paintAtZoom(fixture.state, GraphPaintState.empty(), theme, 3.0);
        Point2D tipOne = GraphViewport.of(0.0, 0.0, 1.0).toScreen(
            fixture.state.geometry().edgeAttachment(fixture.edge.second(),
                fixture.state.geometry().nodes().get(fixture.first.key()).center()),
            SIZE);
        Point2D tipThree = GraphViewport.of(0.0, 0.0, 3.0).toScreen(
            fixture.state.geometry().edgeAttachment(fixture.edge.second(),
                fixture.state.geometry().nodes().get(fixture.first.key()).center()),
            SIZE);

        assertThat(measuredHeadLengthPx(imageOne, tipOne, theme.edgeColor(), 1.0))
            .isCloseTo(12.5, within(1.5));
        assertThat(measuredHeadLengthPx(imageThree, tipThree, theme.edgeColor(), 3.0))
            .isCloseTo(37.5, within(2.0));
        assertThat(Math.max(3.0, 1.4 * 1.0 + 1.0)).isLessThan(0.84 * 12.5);
        assertThat(Math.max(3.0, 1.4 * 3.0 + 1.0)).isLessThan(0.84 * 37.5);
        assertThat((double) perpendicularRunPx(imageOne, theme.edgeColor(),
            (int) Math.round(tipOne.getX()) - 14, (int) Math.round(tipOne.getY())))
            .isLessThan(Math.max(3.0, 1.4 * 1.0 + 1.0));
        assertThat((double) perpendicularRunPx(imageThree, theme.edgeColor(),
            (int) Math.round(tipThree.getX()) - 39, (int) Math.round(tipThree.getY())))
            .isLessThan(Math.max(3.0, 1.4 * 3.0 + 1.0));
    }
```

Anchors: the geometry attachment point of the second node toward the first; tip world `(31, 0)`,
screen `(151, 70)` at 1.0 and `(213, 70)` at 3.0. The measurement threshold is `3.0` at 1.0 and
`5.2` at 3.0. Values: uncapped head `L = 12.5` px at 1.0 and `37.5` px at 3.0 (fixture `d = 62`
world, one head, cap unbound at both zooms); base `0.84 × L = 10.5` / `31.5` px. The two threshold
checks prove the scan can see the base; the two run checks sample `1.5` px past the base plane
(`tip − 14` and `tip − 39`, just beyond `12.5` and `37.5`), where only the `1.4 × zoom` line
remains. Falsifier on the shipped code: the 3.0 measurement is about `26` px (shipped capped length
`min(max(12.5, 8.68 × 3.0), 62 × 3.0) = 26.04`, cap unbound at `d = 62`), which fails `37.5 ± 2.0`. The replaced
`tipThree.x − 28` sample is removed because it would now lie inside the `37.5` px head; on the
shipped code the new `− 39` sample still passes (it is past a `26` px head), so it is an invariant,
not a falsifier. The `0.84 × 26.04` literal is replaced by `0.84 × 37.5`.

### 7.4 DELETE `paintedArrowHeadFloorHoldsAtLowZoom`; ADD `paintedArrowHeadShrinksWhenZoomedOut`

The deleted method asserts that the 0.6 and 1.0 measurements are equal within 1.5 px and both at
least 11 px; that is the shipped behaviour and must no longer hold. Replacement:

```java
    @Test
    public void paintedArrowHeadShrinksWhenZoomedOut() {
        Fixture fixture = fixture(14.0, 14.0);
        GraphTheme theme = lightTheme();
        BufferedImage imageAt06 = paintAtZoom(fixture.state, GraphPaintState.empty(), theme, 0.6);
        BufferedImage imageAt10 = paintAtZoom(fixture.state, GraphPaintState.empty(), theme, 1.0);
        LayoutPoint attachment = fixture.state.geometry().edgeAttachment(fixture.edge.second(),
            fixture.state.geometry().nodes().get(fixture.first.key()).center());
        Point2D tipAt06 = GraphViewport.of(0.0, 0.0, 0.6).toScreen(attachment, SIZE);
        Point2D tipAt10 = GraphViewport.of(0.0, 0.0, 1.0).toScreen(attachment, SIZE);

        double measuredAt06 = measuredHeadLengthPx(imageAt06, tipAt06, theme.edgeColor(), 0.6);
        double measuredAt10 = measuredHeadLengthPx(imageAt10, tipAt10, theme.edgeColor(), 1.0);
        assertThat(measuredAt06).isCloseTo(7.5, within(1.5));
        assertThat(measuredAt10).isCloseTo(12.5, within(1.5));
        assertThat(measuredAt06).isLessThan(measuredAt10);
    }
```

Anchors: same roomy fixture as §7.3; tip screen `(138.6, 70)` at 0.6 and `(151, 70)` at 1.0.
Values: `7.5` px at 0.6 (fastened by `within(1.5)`), `12.5` px at 1.0. Falsifiers on the shipped
code: `measuredAt06 ≈ 12.5` fails `isCloseTo(7.5, within(1.5))`, and `12.5 < 12.5` fails the strict
comparison. The 1.0 measurement assertion passes on both rules.

### 7.5 MODIFY: `paintedArrowHeadCapBaseAndBidirectionalSplitAtPaintLevel`, and add the `capFixture` overload

The existing `d = 6` runs stay exactly as they are (the fixture alignment at zoom 1.0 is pinned and
load-bearing; see the comment in the method). Append one run and one assertion, and add the
overload used by it. Full method:

```java
    @Test
    public void paintedArrowHeadCapBaseAndBidirectionalSplitAtPaintLevel() {
        GraphTheme theme = lightTheme();
        BufferedImage oneHead = paint(capFixture(false), GraphPaintState.empty(), theme, RenderingLevel.FULL);
        BufferedImage twoHeads = paint(capFixture(true), GraphPaintState.empty(), theme, RenderingLevel.FULL);

        GraphCanvas canvas = new GraphCanvas();
        canvas.setSize(SIZE);
        canvas.setTheme(theme);
        canvas.setCanvasState(capFixture(true));
        canvas.setPaintState(GraphPaintState.empty());
        canvas.setViewport(GraphViewport.of(0.0, 0.0, 1.0));
        canvas.setShowArrowheads(false);
        BufferedImage plainLine = paintCanvas(canvas);

        // Cap: the node-end head is 6 world = 6 px long, not 12.5.
        assertThat(measuredHeadLengthPx(oneHead, new Point2D.Double(126.0, 70.0), theme.edgeColor(), 1.0))
            .isCloseTo(6.0, within(1.5));
        // Base follows the capped length: 5.04 px nominal, 4 px measured at the pinned alignment.
        assertThat((double) maximumPerpendicularRunPx(oneHead, theme.edgeColor(), 118, 126, 70))
            .isBetween(3.5, 6.0);
        // Bidirectional split: a = d / 2 = 3 world, so each base is 2.52 px and no run reaches 3.0.
        assertThat((double) maximumPerpendicularRunPx(twoHeads, theme.edgeColor(), 114, 126, 70))
            .isLessThan(3.0);
        // Pinned-fixture measurement (recorded, not asserted): twoHeads=5, plainLine=3.
        // Non-vacuity: the enclosure-side head adds near-edge ink vs. the same edge with arrowheads off.
        assertThat(nearColorPixelsIn(twoHeads, theme.edgeColor(), 120, 66, 123, 74, 80))
            .isGreaterThan(nearColorPixelsIn(plainLine, theme.edgeColor(), 120, 66, 123, 74, 80));

        // Capped rule at a zoom above the shipped 12.5 / 8.68 ~ 1.4401 crossover:
        // d = 10 world, a = 10 world < 12.5, so L = min(12.5, 10) x 3.0 = 30 px.
        // The shipped rule draws min(max(12.5, 8.68 x 3.0), 10 x 3.0) = 26.04 px.
        BufferedImage cappedAtThree = paintAtZoom(capFixture(false, 18.0), GraphPaintState.empty(), theme, 3.0);
        assertThat(measuredHeadLengthPx(cappedAtThree, new Point2D.Double(150.0, 70.0),
            theme.edgeColor(), 3.0)).isCloseTo(30.0, within(1.5));
    }

    private static CanvasState capFixture(final boolean bidirectional) {
        return capFixture(bidirectional, 14.0);
    }

    private static CanvasState capFixture(final boolean bidirectional, final double targetCenterX) {
        ProjectedNode target = node(FIRST_MAP, "cap-target", LayoutPoint.of(targetCenterX, 0.0));
        EnclosureKey enclosureKey = EnclosureKey.of(source(FIRST_MAP, "cap-enclosure"));
        EnclosureHullKey hullKey = EnclosureHullKey.of(Collections.singletonList(enclosureKey));
        ProjectedEndpointKey enclosureEndpoint = ProjectedEndpointKey.ofEnclosure(enclosureKey);
        ProjectedEndpointKey nodeEndpoint = ProjectedEndpointKey.ofNode(target.key());
        ProjectedEnclosure enclosure = ProjectedEnclosure.of(hullKey, Collections.singletonList(enclosureKey),
            Collections.singletonList(SafeNodeLabel.of("cap-enclosure", "cap-enclosure")), "Map",
            Optional.<EnclosureHullKey>empty(), Collections.<ProjectedNodeKey>emptyList(),
            Collections.<EnclosureHullKey>emptyList(), false, BoundaryTier.SUBTLE);

        Map<ProjectedNodeKey, NodeGeometry> nodes = new LinkedHashMap<ProjectedNodeKey, NodeGeometry>();
        nodes.put(target.key(), NodeGeometry.of(LayoutPoint.of(targetCenterX, 0.0), 8.0));
        Map<EnclosureHullKey, HullGeometry> hulls = new LinkedHashMap<EnclosureHullKey, HullGeometry>();
        hulls.put(hullKey, rectangle(-50.0, -20.0, 0.0, 20.0, LayoutPoint.of(-25.0, 0.0)));

        ConnectorDescriptor descriptor = ConnectorDescriptor.of(
            SourceNodeKey.persisted(reference(FIRST_MAP, "cap-source")),
            reference(FIRST_MAP, "cap-target"), bidirectional, true, "source", "middle", "target");
        EdgeContributor contributor = EdgeContributor.nativeConnector(
            ConnectorSnapshot.of(0, descriptor), enclosureEndpoint, nodeEndpoint);
        ProjectedEdge edge = ProjectedEdge.of(ProjectedEdgeKey.of(enclosureEndpoint, nodeEndpoint),
            Collections.singletonList(contributor));
        GraphProjection projection = GraphProjection.projected(1L, Collections.singletonList(target),
            Collections.singletonList(enclosure), Collections.singletonList(edge),
            Collections.<RelationshipResolution>emptyList(), Collections.<PinProjection>emptyList());
        Map<ProjectedNodeKey, LayoutPoint> positions = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        positions.put(target.key(), LayoutPoint.of(targetCenterX, 0.0));
        Map<EnclosureHullKey, LayoutPoint> anchors = new LinkedHashMap<EnclosureHullKey, LayoutPoint>();
        anchors.put(hullKey, LayoutPoint.of(-25.0, 0.0));
        return CanvasState.of(1L, projection,
            LayoutFrame.of(1L, LayoutPositions.of(positions, anchors), false, 0),
            GraphGeometry.of(nodes, hulls), OperationalStatus.IDLE);
    }
```

The only differences from the existing body are the three `targetCenterX` uses above; the
`LayoutPoint` argument passed to `node(...)` does not affect the geometry, which is set by
`NodeGeometry.of(...)` and `positions.put(...)`.

Anchors: `(126, 70)` and `(150, 70)` are the geometry attachment points of the node endpoint (the
head tip), not the first visible pixel; at zoom 3.0 the node ring covers `2.1` px by the tip
(`0.7 × 3.0`). The `d = 10` variant puts the target node centre at world `(18, 0)`, radius `8.0`,
so the tip is world `(10, 0)` = screen `150 = 120 + 10 × 3.0`; the enclosure boundary is still
world `x = 0` = screen `120`, and the `30` px head therefore ends exactly on it. Falsifier on the
shipped code: `measuredHeadLengthPx(cappedAtThree, ...)` is about `26` (shipped capped request
`26.04`), which fails `30.0 ± 1.5`. The pre-existing `d = 6` assertions all pass on both rules
(§4.3), so they cannot fail on the shipped code; they remain because they pin the call-site flag
propagation (a missing split draws two 6 px heads with a 4 px run and fails the `< 3.0` bound) and
the capped-base aspect.

### 7.6 UNCHANGED tests

- `paintedArrowHeadInteriorUsesTheDarkThemeEdgeColour` (line 1185) stays exactly as it is: at
  zoom 1.0 the head is still `12.5` px and the sample `(144, 71)` is still inside the head between
  the line and the antialiased edge; it passes on both rules and is not a falsifier.
- `suppressAndRestoreDirectionalArrowheadsInThePaintedCanvas` (line 619) and every other test in
  the class stay unchanged and keep passing, because the head is the only changed geometry.

## 8. Verification commands

Repository conventions: use `gradle` (not `gradlew`), with the Java 21.0.8-zulu toolchain from
`~/.sdkman/candidates/java/21.0.8-zulu`; add `-PTestLoggingFull` for verbose failures.

```bash
export JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu
export PATH="$JAVA_HOME/bin:$PATH"

# Compile production and test sources.
gradle :freeplane_plugin_graph:compileJava :freeplane_plugin_graph:compileTestJava

# Run the canvas paint tests specified in §7.
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.canvas.GraphCanvasPaintShould"

# Same target with full failure logging.
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.canvas.GraphCanvasPaintShould" -PTestLoggingFull

# Full module regression gate.
gradle :freeplane_plugin_graph:test
```

Expected observable outcome: every command exits `0` with `BUILD SUCCESSFUL`, and the test class
reports zero failures with the renamed/added/changed methods of §7 present. The removed
identifiers are gone:

```bash
grep -rn "ARROW_HEAD_MINIMUM_LENGTH_PX\|ARROW_HEAD_LENGTH_PER_ZOOM" \
  freeplane_plugin_graph/src/main/java   # no output
grep -rn "ARROW_HEAD_WORLD_LENGTH" freeplane_plugin_graph/src/main/java
# exactly one declaration and one use: GraphPainter.java constant and arrowHeadLengthPx
```

No translation resource is touched (`GraphPainter` carries no translatable strings), so
`gradle format_translation` does not apply.

## 9. Acceptance evidence

Real-application captures on the reporter's map through seeded workspace copies, following the
repository's established Xvfb/virtual-display evidence procedure. All commands run from this
integration worktree unless stated otherwise.

### 9.1 Build and the before/after binary pair

The "before" binary is a copy of the shipped, pre-change `BIN` (old rule
`max(12.5, 8.68 × zoom)`); the "after" binary is built from this worktree with the change applied
and installed into `BIN`:

```bash
export JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu
export PATH="$JAVA_HOME/bin:$PATH"
RUN=/tmp/pm-arrowhead-zoom
mkdir -p "$RUN"

# 1. BEFORE the change is applied: build this worktree at its pinned pre-change revision and
#    freeze that binary. The design commits touch only docs/ (including the non-compiled mockup
#    renderer ArrowheadZoomMockups.java), so building at c6072243 or 028770a98d yields the same
#    pre-change BIN.
gradle :freeplane_plugin_graph:graphSmokeInstallation --console=plain --offline
rm -rf "$RUN/BIN-before"
cp -a BIN "$RUN/BIN-before"
cat "$RUN/BIN-before/gitinfo.txt"   # record: must show the pre-change revision

# 2. Commit the change, then build again; this worktree's BIN is the AFTER binary. Committing
#    first matters because BIN/gitinfo.txt records HEAD only, with no dirty marker.
gradle :freeplane_plugin_graph:graphSmokeInstallation --console=plain --offline
cat BIN/gitinfo.txt                 # record: the after revision, i.e. the change commit
# If the change was not committed before this build, record `git status --porcelain` and a
# worktree-diff fingerprint alongside the revision, because gitinfo.txt would otherwise equal
# the before revision.
```

Fallback only when the pre-change build was not taken: the existing copy of the guest checkout's
binary at `/tmp/pm-arrowhead-zoom/BIN` (gitinfo `d8132e6089a76f4acd9b77fe0580f8967b203da6`) is the
live-node-edit-refresh integration commit and differs from this worktree by `MapLeaseManager.java`
and its tests. Those files do not touch the arrowhead paint path, but the 1.0 pair then compares
two revisions, so the completion report must state that difference instead of claiming a
single-variable identity.

### 9.2 Seeded workspace copies, map copies and viewports

The evidence runs on copies; the user's workspace and maps are never modified. The three maps are
copied next to the seeded workspaces and the three `.fpg` URI values are rewritten to the copies:

```bash
RUN=/tmp/pm-arrowhead-zoom
SRC=/data/Dropbox/Documents/999.notebooks/01.math
mkdir -p "$RUN/maps" "$RUN/home" "$RUN/config" "$RUN/captures" "$RUN/crops" "$RUN/evidence"
cp "$SRC/00.foundation.of.math/02.set_theory/mindmap/Axiomatic_Set_Theory.mm" "$RUN/maps/"
cp "$SRC/04.abstract/01.topology/01.general_topology/mindmap/Point-set_Topology.mm" "$RUN/maps/"
cp "$SRC/01.analysis/03.real_analysis/mindmap/Real_Anaylsis.mm" "$RUN/maps/"
```

Each seeded workspace is the live `$SRC/math.fpg` with its three `uri=` values rewritten to
`file:///tmp/pm-arrowhead-zoom/maps/<file>.mm` and its `<viewport>` replaced by the centre/zoom
in the table below; the same rewrite applied to the sibling recorded files
(`math.sync-conflict-20261011-131807-QPOKO56 ....fpg`) yields the other viewports. Validated seeds
matching the table already exist at `$RUN/maps/before-01485.fpg`, `$RUN/maps/before-03781.fpg`
and `$RUN/maps/before-100.fpg` and may be reused.

The three required viewports come from the reporter's recorded workspace family (see §4.2):

| Seeded workspace file | viewport centre (world) | zoom | Source of the recorded state |
| --- | --- | --- | --- |
| `before-01485.fpg` | `-5428.751107787859, -987.5363258400164` | `0.14846481908261` | live `math.fpg` |
| `before-03781.fpg` | `-920.3025170052431, -422.47416434224544` | `0.37809529736878794` | `math.sync-conflict-20261011-131807-QPOKO56 (Arch-MSSD0's conflicted copy 2026-10-11).fpg` |
| `before-100.fpg` | `-920.3025170052431, -422.47416434224544` | `1.0` | same scene as `before-03781.fpg` at unit zoom |

Each seeded workspace is the live `math.fpg` with its three map `uri=` values rewritten to
`file:///tmp/pm-arrowhead-zoom/maps/<file>.mm` and its `<viewport>` replaced by the centre/zoom
above; it also carries `<display-settings show-arrowheads="true" canvas-theme="FOLLOW_FREEPLANE"
remember-viewport="true" dim-unrelated-nodes="true" map-sidebar-width="335"
map-sidebar-hidden="true"></display-settings>`. Seed the running application with an isolated
`HOME`/`XDG_CONFIG_HOME` whose `config/freeplane/1.12.x/auto.properties` (the version directory the
launcher reads; mirror to `1.13.x`) contains:

```
always_load_last_maps=false
create_new_map_if_no_maps_are_loaded=false
graph_workspace_recent_workspaces=/tmp/pm-arrowhead-zoom/maps/before-03781.fpg
load_last_map=false
load_last_maps=false
```

Set `graph_workspace_recent_workspaces` to the `.fpg` for the capture being taken (re-seed a fresh
copy of the file for every run, because `remember-viewport="true"` can rewrite it on exit).

### 9.3 Capture procedure

For each of the six runs (before/after × `before-01485.fpg`, `before-03781.fpg`,
`before-100.fpg`), with the binary from §9.1 (`$RUN/BIN-before` for the before runs, the
worktree `BIN` for the after runs):

```bash
Xvfb :99 -screen 0 1600x1000x24 -nolisten tcp &
BIN=/path/to/integration/worktree/BIN   # "$RUN/BIN-before" for the before runs
cd "$BIN"
DISPLAY=:99 HOME="$RUN/home" XDG_CONFIG_HOME="$RUN/config" JAVA_HOME="$JAVA_HOME" \
  JAVA_OPTS="-Dorg.freeplane.nosplash=true" ./freeplane.sh > "$RUN/app.log" 2>&1 &
```

Open the seeded workspace (`View -> Open Graph Workspace`; under Xvfb without a window manager the
menu item is clicked at its measured coordinates, not with Alt+V/Enter). Wait for the
`Graph Workspace` window and let the layout settle before capture, then

```bash
import -window "$(xdotool search --name 'Graph Workspace' | head -1)" "$RUN/captures/before-01485.png"
```

and repeat with the matching workspace file and binary. Record for each capture: the workspace
file, the binary (`BIN-before` or `BIN`), the seeded viewport, and the measured node diameter used
to confirm the actually applied zoom (`node diameter px / zoom` must fall in `16 … 28`). If the
application rewrites the viewport on exit, read the applied zoom back from the workspace file.
Raw captures land in `$RUN/captures/` (created in §9.2); the selected overview pairs are copied
into `freeplane_plugin_graph/build/evidence/2026-10-11-graph-arrowhead-zoom/` and the committed
overviews are copied from there into `docs/superpowers/specs/images/` as listed in §9.6.

### 9.4 Image diff of the 1.0 pair

The old and new rule are identical at `zoom = 1.0` on every edge, so the two 1.0 captures of the
same settled scene must be pixel-identical. Crop the same canvas-only rectangle (excluding window
chrome and any non-canvas bars) from both captures, record the crop geometry, and run:

```bash
compare -metric AE "$RUN/crops/before-100-canvas.png" "$RUN/crops/after-100-canvas.png" null: 2>&1
```

Expected output: `0 (0)` on ImageMagick 7 (7.1.2-32 was observed here) or `0` on ImageMagick 6;
the metric is written to stderr, hence `2>&1`. If it is nonzero, both runs must be re-captured
after the canvas reaches the settled/idle state (the layout must be identical, not merely
similar); a persistent nonzero result is recorded as a finding, not smoothed over. The command and
its exact output go into the completion report.

### 9.5 Pixel measurement of low-zoom heads

For the four low-zoom captures (before/after × 0.14846481908261 and 0.37809529736878794):

1. Locate one visible directional head ending at a node whose edge is long enough to be uncapped:
   the attachment distance must be at least `max(12.5 / zoom, 8.68)` world for the before rule and
   at least `12.5` world for the after rule - at the two low zooms that is `84.19503069642815`
   world at `0.14846481908261` and `33.06044821765584` world at `0.37809529736878794` for the
   before rule, and `12.5` world for the after rule. Choose such an edge and record it (the map
   node ids and its world attachment distance read from the layout, or, if that is not available,
   the tip/base pixel coordinates plus the edge's visible length in the capture); a capped short
   edge would invalidate the measurement. Crop the smallest rectangle containing
   the head plus 8 px of the line on each side; record the crop origin and size, e.g.
   `convert "$RUN/captures/after-03781.png" -crop WxH+X+Y +repage -filter point -resize 800%
   "$RUN/crops/after-03781-head-8x.png"`.
2. In the 8x nearest-neighbour crop measure, in magnified px, (a) the head length along the edge
   axis from the tip (excluding the ring-covered `0.7 × zoom` px) to the base plane, and (b) the
   base width perpendicular to the axis at the base plane; divide both by `8` and record them with
   the tip/base coordinates in original-image px.
3. Acceptance: the after captures measure `12.5 × zoom` within `± 0.5` px —
   `1.855810238532625` px at `0.14846481908261` and `4.726191217109849` px at
   `0.37809529736878794` (rounded `1.86` and `4.73`); the before captures measure
   `12.5` px within `± 0.5` px; at each zoom the after measurement is strictly smaller than the
   before measurement; and the measured base : length ratio is `0.84 ± 0.08` in the `0.3781`
   crops and in every crop whose measured head length is at least `4` px. The ratio check is not
   applied to a head below `4` px (`0.1485`), because integer magnified-pixel counts there are
   coarser than the tolerance; for that capture record the measured counts and assert only that
   the after head is strictly shorter than the before head and that both are present. The
   magnified head of both after low-zoom captures is committed so the measurement is auditable
   from the image.

### 9.6 Artefacts and record

- Raw captures, crops and logs live under `/tmp/pm-arrowhead-zoom/` (`captures/`, `crops/`,
  `evidence/`, `app.log`, `xvfb.log`); the selected overview pairs and magnified heads are copied
  into `freeplane_plugin_graph/build/evidence/2026-10-11-graph-arrowhead-zoom/`
  (git-ignored through `*/build` in `.gitignore:35`).
- Commit the overview pairs and the magnified after heads under `docs/superpowers/specs/images/`:
  `2026-10-11-graph-arrowhead-zoom-before-01485.png`,
  `2026-10-11-graph-arrowhead-zoom-after-01485.png`,
  `2026-10-11-graph-arrowhead-zoom-before-03781.png`,
  `2026-10-11-graph-arrowhead-zoom-after-03781.png`,
  `2026-10-11-graph-arrowhead-zoom-before-100.png`,
  `2026-10-11-graph-arrowhead-zoom-after-100.png`,
  `2026-10-11-graph-arrowhead-zoom-after-01485-head-8x.png`,
  `2026-10-11-graph-arrowhead-zoom-after-03781-head-8x.png`.
- The completion report records: the integration revision (`git rev-parse HEAD`) and
  `BIN/gitinfo.txt` of the after build, the build command of §9.1, the six seeded viewports and
  their `.fpg` files, the `compare -metric AE` command and its output, and the measured head
  lengths and base widths of §9.5.

## 10. Risks and open questions

1. **Literal constant, documented ratio.** `ARROW_HEAD_WORLD_LENGTH = 12.5` is a literal, not
   `0.8929 × (BASE_RADIUS × MAX_SCALE)`; the ratio cannot be expressed in code because
   `GraphGeometryEngine.BASE_RADIUS` and `NodeProminence.MAX_SCALE` are `private static final`
   (`GraphGeometryEngine.java:23`, `NodeProminence.java:6`). If the prominence range ever changes,
   the head : node ratio changes silently. This is accepted because the approved 1x appearance, not
   a live relation, defines the size; the comment records the provenance.
2. **High-zoom behaviour change is intended but large.** Above the shipped crossover
   `12.5 / 8.68 ≈ 1.4400921658986177`, an uncapped head is now `12.5 × zoom` px instead of
   `8.68 × zoom` px (at `3.0`: `37.5` vs `26.04`), and a head with `8.68 ≤ a < 12.5` world is now
   drawn at `a × zoom` instead of `8.68 × zoom`. At and below `1.0` the new rule is never larger
   than the shipped one and at `1.0` it is identical on every edge; above `1.0` it is never
   smaller, and an uncapped head grows from `12.5` px at `1.0` to `12.5 × 1.4400921658986177 ≈
   18.0012` px at the shipped crossover while the shipped rule still holds the `12.5` px floor.
3. **Rasterisation of the new capped probe.** The `d = 10` / zoom 3.0 probe puts the base plane
   exactly on the enclosure boundary at screen `x = 120.0`, where column coverage is partial; a
   correct implementation may measure `29` or `30` px. The `±1.5` tolerance covers this. If a
   correct implementation measures below `28.5`, the fault is in the anchor or the crop, not the
   rule; the anchor is the geometry attachment point `(150, 70)`, computed from
   `GraphViewport.toScreen`, never the first visible pixel.
4. **The 1.0 diff depends on settling, not on the rule.** The before/after 1.0 captures are
   pixel-identical only if both runs reach the same settled layout; the evidence must wait for the
   settled state and re-capture a nonzero diff before recording it as a divergence.
5. **Low-zoom measurement precision.** At `0.14846481908261` the new head is
   `1.855810238532625` px long; an 8x point-filter crop measures it to `± 0.5` px. The acceptance
   record states the crop geometry and the two integer magnified counts, so the `± 0.5` px claim is
   auditable rather than asserted.
6. **Optional fourth viewport.** `0.7487174552196292` is also recorded in the reporter's workspace
   family (`math.sync-conflict-20261011-154807-QPOKO56.fpg` and its sibling) and appears in the
   design's table, but this specification's required evidence set is the design's
   `0.14846481908261`, `0.37809529736878794` and `1.0`. Capturing the fourth viewport is optional
   and does not change any contract.

## 11. Out of scope

No display settings, preferences, translation keys or persisted XML/schema changes; no
`DisplaySettings`, `GraphTheme`, `CanvasTheme`, `GraphViewport` or workspace XML codec edits; no
arrowhead shape variants; no changes to the connector adapter or projection, to which edges are
directional, or to the dim / hover / selection / highlight paths; no gap, outline or halo between
the line and the head; no second head colour; no zoom clamp and no change to
`GraphCanvas.showArrowheads` semantics.
