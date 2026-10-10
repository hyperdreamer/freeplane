# Graph Workspace: directional arrowhead prominence — technical specification

Task identifier: `2026-10-10-graph-arrowhead-prominence`

Source design: `docs/superpowers/specs/2026-10-10-graph-arrowhead-prominence-design.md`.
This specification implements the approved design without extending or contradicting it.

## 1. Scope

### 1.1 Production change

The whole production change is confined to
`freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPainter.java`:

1. Replace the two inline literals in `paintArrow` — `8.0 / zoom` (head length) and `3.5 / zoom`
   (half-base; the current full base is therefore `7.0 / zoom`) — with the rule of §2 and §3.
2. Add the three named constants of §2.1 and the four package-private static helpers of §2.2.
3. Change the signature of `paintArrow` to carry `boolean arrowAtFirst` and `boolean arrowAtSecond`
   (the same flags the edge carries, §2.3) and update both call sites inside `paintEdges` to pass
   `edge.arrowAtFirst()` and `edge.arrowAtSecond()`.
4. Keep exactly one rendering path: the old literals are removed, not retained behind a fallback,
   per the repository's legacy-removal policy.

No other production file changes. The test changes are confined to
`freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java`
and are specified in §6.

### 1.2 Non-goals

- No gap or outline between the edge line and the head: the head stays directly attached to the
  line as today, and the line remains drawn under the head.
- No new display setting, preference, translation key, or persisted XML attribute.
- No change to the `showArrowheads` toggle, `paintMultiplicity`, edge colour selection, the node
  ring, or the dim / hover / selection / highlight paint paths.
- No change to the connector adapter or the projection: which edges are directional, and at which
  end, is untouched.
- No arrowhead shape variants (open, stealth) and no per-target-node sizing.

## 2. Contracts

All additions live in `org.freeplane.plugin.graph.canvas.GraphPainter`. The class stays
package-private and `final`; the four helpers are package-private `static`, matching the existing
`GraphPainter.leaderStroke` test-access pattern, so same-package tests can call them directly.

### 2.1 Named constants

```java
    // Screen px. Floor that keeps a head legible below the growth crossover.
    private static final double ARROW_HEAD_MINIMUM_LENGTH_PX = 12.5;
    // 0.62 x the maximum node radius in world units:
    // GraphGeometryEngine.BASE_RADIUS 8.0 x NodeProminence.MAX_SCALE 1.75 = 14.0 world.
    private static final double ARROW_HEAD_LENGTH_PER_ZOOM = 0.62 * 14.0;
    // Head base is 0.84 x length; the aspect is preserved when the cap shrinks a head.
    private static final double ARROW_HEAD_HALF_BASE_RATIO = 0.42;
```

`ARROW_HEAD_LENGTH_PER_ZOOM` is the `8.68` of the design; `0.62 * 14.0` is written as the
derivation instead of the already-evaluated literal.

### 2.2 Package-private static helpers

```java
    // Screen px.
    static double arrowHeadLengthPx(final double zoom) {
        return Math.max(ARROW_HEAD_MINIMUM_LENGTH_PX, ARROW_HEAD_LENGTH_PER_ZOOM * zoom);
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

| Helper | Parameter units | Return unit | Exact expression returned |
| --- | --- | --- | --- |
| `arrowHeadLengthPx` | `zoom`: screen px per world unit | screen px | `max(12.5, 8.68 × zoom)` |
| `availableArrowHeadWorldLength` | `attachmentDistanceWorld`: world units; two flags | world units | `arrowAtFirst && arrowAtSecond ? d / 2.0 : d` |
| `effectiveArrowHeadLengthPx` | `zoom`: screen px per world unit; `availableWorldLength`: world units | screen px | `min(arrowHeadLengthPx(zoom), availableWorldLength × zoom)` |
| `arrowHeadHalfBasePx` | `lengthPx`: screen px | screen px | `0.42 × lengthPx` |

The `d` versus `d / 2` decision exists only inside `availableArrowHeadWorldLength`; it is never
taken inline at a call site. A head is always described by `L = effectiveArrowHeadLengthPx(...)`
screen px and the world-space coordinates `L / zoom` (length) and `arrowHeadHalfBasePx(L) / zoom`
(half-base).

### 2.3 Changed `paintArrow` signature and `paintEdges` call sites

`paintArrow` stays `private static`; only its parameter list changes:

```java
    private static void paintArrow(final Graphics2D graphics, final LayoutPoint tip, final LayoutPoint from,
            final Color color, final double zoom, final boolean arrowAtFirst, final boolean arrowAtSecond)
```

| Parameter | Unit / value | Meaning |
| --- | --- | --- |
| `graphics` | — | graphics already carrying the world transform |
| `tip` | world units | geometry attachment point of the head's endpoint |
| `from` | world units | geometry attachment point of the opposite endpoint |
| `color` | — | fill colour; `theme.edgeColor()` at both call sites |
| `zoom` | screen px per world unit | `viewport.zoom()` |
| `arrowAtFirst` | — | `edge.arrowAtFirst()` |
| `arrowAtSecond` | — | `edge.arrowAtSecond()` |

Both call sites in `paintEdges` add the two flags; their guards are unchanged:

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

The finite-length guard is evaluated before any sizing: a degenerate direction (zero or
non-finite `length`) returns from `paintArrow` before `availableArrowHeadWorldLength`,
`effectiveArrowHeadLengthPx`, or `arrowHeadHalfBasePx` are reached, so the cap can never be
reached with `d = 0`.

## 3. Algorithm — the body of the new `paintArrow`

All geometry below is computed in world units except the explicitly named px values; the world
transform is already applied to `graphics`.

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

Ordered steps:

1. `dx = tip.x() - from.x()`, `dy = tip.y() - from.y()`; both are world units.
2. `length = sqrt(dx² + dy²)`. This is `d`, the world distance between the two attachment points.
3. Guard: if `!(length > 0.0) || !Double.isFinite(length)`, return. This guard runs **before**
   sizing, so a degenerate direction never reaches the cap.
4. Axis: `unitX = dx / length`, `unitY = dy / length`; perpendicular:
   `perpendicularX = -unitY`, `perpendicularY = unitX`. Derivation is unchanged from today.
5. `availableWorldLength = availableArrowHeadWorldLength(length, arrowAtFirst, arrowAtSecond)`,
   i.e. `a = d` with one head and `a = d / 2` with two.
6. `arrowLengthPx = effectiveArrowHeadLengthPx(zoom, availableWorldLength)`, i.e.
   `L = min(max(12.5, 8.68 × zoom), a × zoom)` screen px.
7. Convert to world units: `arrowLength = L / zoom` (the head length along the edge) and
   `arrowWidth = arrowHeadHalfBasePx(L) / zoom` (the half-base perpendicular to the edge). The
   full base in world units is `2 × arrowWidth = 0.84 × a` when the cap binds and
   `0.84 × L / zoom` otherwise; the base-to-length ratio is `0.84 : 1` in both cases.
8. Build the head with a fresh `Path2D.Double` in world coordinates: `moveTo(tip)`, then
   `lineTo(tip − unit × arrowLength ± perpendicular × arrowWidth)`, then `closePath()`. The tip
   does not move: it is exactly the geometry attachment point.
9. Paint fill-only: `graphics.setColor(color)` then `graphics.fill(arrow)`. There is no `draw`,
   no stroke change, no second colour, and no composite change; the head therefore inherits the
   active `AlphaComposite` (in particular the dim `0.28f`), exactly as today.

## 4. Number tables

### 4.1 Requested head length and half-base (cap unbound)

`L = max(12.5, 8.68 × zoom)`; half-base `= 0.42 × L`; base `= 0.84 × L`.

| Zoom | `8.68 × zoom` (screen px) | Requested `L` (screen px) | Half-base `0.42 × L` (screen px) | Base `0.84 × L` (screen px) |
| --- | --- | --- | --- | --- |
| 0.6 | 5.208 | 12.5 | 5.25 | 10.5 |
| 1.0 | 8.68 | 12.5 | 5.25 | 10.5 |
| 1.44 | 12.4992 | 12.5 | 5.25 | 10.5 |
| 1.6 | 13.888 | 13.888 (13.9 rounded) | 5.83296 (5.83) | 11.66592 (11.67) |
| 3.0 | 26.04 | 26.04 | 10.9368 (10.94) | 21.8736 (21.87) |

The exact crossover is `12.5 / 8.68 ≈ 1.4400921658986176`: the floor is active for
`zoom ≤ 12.5 / 8.68`, and the length grows strictly above it.

### 4.2 Cap behaviour at `d = 6` world

At `d = 6`, `a = 6.0` with one head and `a = d / 2 = 3.0` with two; `min(max(12.5, 8.68 × zoom), a × zoom)`
is capped at every zoom for both, because `a < 8.68`. `L = a × zoom`, half-base `= 0.42 × a × zoom`,
base `= 0.84 × a × zoom`; the world-space base is `0.84 × a` at every zoom.

| Arrows | `a` (world) | Zoom | `L` (screen px) | Half-base (screen px) | Base (screen px) | Base (world) | Base ÷ line width (`1.4` world) | Aspect base : length |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| one head | 6.0 | 0.6 | 3.6 | 1.512 | 3.024 | 5.04 | 3.6 : 1 | 0.84 : 1 |
| one head | 6.0 | 1.0 | 6.0 | 2.52 | 5.04 | 5.04 | 3.6 : 1 | 0.84 : 1 |
| one head | 6.0 | 3.0 | 18.0 | 7.56 | 15.12 | 5.04 | 3.6 : 1 | 0.84 : 1 |
| two heads | 3.0 | 0.6 | 1.8 | 0.756 | 1.512 | 2.52 | 1.8 : 1 | 0.84 : 1 |
| two heads | 3.0 | 1.0 | 3.0 | 1.26 | 2.52 | 2.52 | 1.8 : 1 | 0.84 : 1 |
| two heads | 3.0 | 3.0 | 9.0 | 3.78 | 7.56 | 2.52 | 1.8 : 1 | 0.84 : 1 |

The aspect ratio is preserved in every capped row because the half-base is derived from the
capped `L`, not from `arrowHeadLengthPx(zoom)`.

## 5. Edge cases

Required behaviour, with the design's exact resulting values:

1. **Zero-length or non-finite head direction.** The existing guard
   (`!(length > 0.0) || !Double.isFinite(length)`) returns before any sizing or path work; the cap
   cannot be reached with `d = 0`. No exception, no colour change, no path.
2. **Dimming.** The head is painted under the existing `AlphaComposite 0.28f` (SRC_OVER) exactly
   as today. There is no halo, so there is no additional dim interaction.
3. **Enclosure endpoints.** The tip may lie on a hull boundary rather than a node disc. Head
   orientation and size depend only on the line direction, the zoom and `a`; node and enclosure
   endpoints behave identically.
4. **Node overlap at the tip.** Unchanged: the node ring covers up to `0.7 × zoom` screen px of
   the tip — `0.42` px at 0.6× and `2.1` px at 3×.
5. **Small zoom.** The 12.5 px floor holds heads legible at the cost of proportion. Below the
   `1.44` crossover the head is 12.5 px against a minimum node diameter of `16 × zoom` px:
   `1.30×` at 0.6× (12.5 / 9.6) and `0.78×` at 1.0× (12.5 / 16). This deliberate trade-off for
   legibility is bounded by the cap above.
6. **Large zoom.** The head grows linearly and unbounded (there is no zoom clamp in
   `GraphViewport`). At 3× the head is 26.04 px against a minimum node radius of 24 px
   (48 px diameter), about 54% of the smallest node's diameter, and 31% of the largest
   (42 px radius, 84 px diameter: 26.04 / 84 ≈ 31%).
7. **Short edges.** `NodeSeparationProjection.MIN_GAP = 6` world is a separation target, not a
   hard invariant — residual violations are recorded rather than rejected, and pinned pairs are
   skipped — so smaller `d` is reachable. For `a` below about `1.667` world units the capped base
   `0.84 × a` is narrower than the `1.4`-world-unit line and the head reads as a small nub merged
   into the line. The crossover is on `a`, not on `d`: a head is sub-line-width for
   `d < 1.667` world with one head (`a = d`) and for `d < 3.333` with two (`a = d / 2`). This is
   accepted because such an edge is far shorter than the target node separation, so its two
   endpoints are effectively touching. The old literal `8.0 / zoom` head already overshot short
   edges at low zoom; where that overshoot landed inside the source node it was hidden by the
   node disc, and where it landed outside it was visible as a head protruding behind the source
   node. The new cap replaces that behaviour: the base plane lands at the far attachment point
   with one head and at the edge midpoint with two, never beyond the far endpoint's boundary.
8. **Fractional zoom.** The triangle path scales with the head length (`L / zoom`) rather than
   being rasterised at a fixed size, so the head does not develop ragged edges.

## 6. Test specification

All seven items extend
`freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java`.
Existing helpers used below: `paint(state, paintState, theme, level)` (zoom 1.0, arrowheads on),
`paintAtZoom(state, paintState, theme, zoom)`, `paintCanvas(canvas)`, `colorPixelsIn(...)`,
`nearColorPixelsIn(...)`, `nonBackgroundPixelsIn(...)`, `differentPixels(...)`, `fixture(double)`,
`lightTheme()`.

### 6.0 Shared fixtures and new test helpers

Two fixture additions are required and are test-only.

1. A two-radius overload of the existing fixture builder. `fixture(double firstRadius)` currently
   sets only the first node's radius (the second stays 10.0). The head of `fixture(*).edge` is at
   the **second** node (`edge.arrowAtSecond()` is true), so the head must land on a 14-unit node:

```java
    private static Fixture fixture(final double firstRadius) {
        return fixture(firstRadius, 10.0);
    }

    private static Fixture fixture(final double firstRadius, final double secondRadius) {
        // existing body, with the second node radius changed from the 10.0 literal to secondRadius
        ...
        nodes.put(second.key(), NodeGeometry.of(secondLabelAnchor(second), secondRadius));
        ...
    }
```

2. Pixel-profile helpers for items 3–5, built only on the real `colorPixelsIn` helper (exact RGB
   matching). `baselineY` is the device row of the edge axis and `tipColumn` is the rounded device
   column of the geometry attachment point; the scan walks away from the tip toward the edge
   source.

```java
    private static int perpendicularRunPx(final BufferedImage image, final Color edgeColor,
            final int column, final int baselineY) {
        return colorPixelsIn(image, edgeColor, column, baselineY - 32, column + 1, baselineY + 32);
    }

    private static double measuredHeadLengthPx(final BufferedImage image, final Point2D tipScreen,
            final Color edgeColor, final double zoom) {
        final double threshold = Math.max(3.0, 1.4 * zoom + 1.0);
        final int tipColumn = (int) Math.round(tipScreen.getX());
        final int baselineY = (int) Math.round(tipScreen.getY());
        double measured = 0.0;
        for (int distance = 0; distance <= 64; distance++) {
            if (perpendicularRunPx(image, edgeColor, tipColumn - distance, baselineY) >= threshold) {
                measured = distance;
            }
        }
        return measured;
    }

    private static int maximumPerpendicularRunPx(final BufferedImage image, final Color edgeColor,
            final int firstColumn, final int lastColumn, final int baselineY) {
        int maximum = 0;
        for (int column = Math.min(firstColumn, lastColumn); column <= Math.max(firstColumn, lastColumn);
                column++) {
            maximum = Math.max(maximum, perpendicularRunPx(image, edgeColor, column, baselineY));
        }
        return maximum;
    }
```

### 6.1 Item 1 — rule (unit, no pixels)

Suggested test method: `arrowHeadLengthIsFlooredUntilTheCrossoverAndGrowsStrictlyAboveIt`.
Fixture: none; direct calls on `GraphPainter`.

```java
        assertThat(GraphPainter.arrowHeadLengthPx(0.6)).isCloseTo(12.5, within(1e-9));
        assertThat(GraphPainter.arrowHeadLengthPx(1.0)).isCloseTo(12.5, within(1e-9));
        assertThat(GraphPainter.arrowHeadLengthPx(1.44)).isCloseTo(12.5, within(1e-9));
        assertThat(GraphPainter.arrowHeadLengthPx(3.0)).isCloseTo(26.04, within(1e-9));
        assertThat(GraphPainter.arrowHeadLengthPx(1.45))
            .isGreaterThan(GraphPainter.arrowHeadLengthPx(12.5 / 8.68));
        assertThat(GraphPainter.arrowHeadHalfBasePx(12.5)).isCloseTo(5.25, within(1e-9));
```

Tolerance: `within(1e-9)` on every double; the strict growth check uses `isGreaterThan`, not
`isCloseTo`.

### 6.2 Item 2 — cap wiring (unit, no pixels)

Suggested test method: `arrowHeadRuleCapAndBidirectionalSplitAreSelectedInTheHelpers`.
Fixture: none.

```java
        assertThat(GraphPainter.availableArrowHeadWorldLength(6.0, false, false)).isCloseTo(6.0, within(1e-9));
        assertThat(GraphPainter.availableArrowHeadWorldLength(6.0, true, true)).isCloseTo(3.0, within(1e-9));
        assertThat(GraphPainter.availableArrowHeadWorldLength(6.0, true, false)).isCloseTo(6.0, within(1e-9));
        assertThat(GraphPainter.effectiveArrowHeadLengthPx(1.0, 100.0)).isCloseTo(12.5, within(1e-9));
        assertThat(GraphPainter.effectiveArrowHeadLengthPx(0.6, 6.0)).isCloseTo(6.0 * 0.6, within(1e-9));
        assertThat(GraphPainter.effectiveArrowHeadLengthPx(1.0, 3.0)).isCloseTo(3.0, within(1e-9));
        assertThat(GraphPainter.arrowHeadHalfBasePx(GraphPainter.effectiveArrowHeadLengthPx(0.6, 6.0)))
            .isCloseTo(0.42 * 6.0 * 0.6, within(1e-9));
```

Tolerance: `within(1e-9)`, never exact equality. `6.0 * 0.6` is `3.5999999999999996` in double
arithmetic, so an exact `3.6` literal would fail a correct implementation. Composition of helpers
alone cannot pin the paint path, which is why item 5 repeats the length, base and split checks
against painted pixels.

### 6.3 Item 3 — painted head length

Suggested test method: `paintedArrowHeadLengthTracksTheZoomRule`.
Fixture: `fixture(14.0, 14.0).state` — the head lands on the second node of radius 14 world units
(42 px at 3×), the attachment distance is `d = 62` world, and the cap is unbound at both tested
zooms. Paint with `paintAtZoom(state, GraphPaintState.empty(), theme, 1.0)` and
`paintAtZoom(state, GraphPaintState.empty(), theme, 3.0)`. Compute each tip screen point with
`GraphViewport.of(0.0, 0.0, zoom).toScreen(state.geometry().edgeAttachment(
fixture.edge.second(), state.geometry().nodes().get(fixture.first.key()).center()), SIZE)`; the
tip is at world (31, 0), i.e. screen (151, 70) at 1.0× and (213, 70) at 3.0×.

```java
        assertThat(measuredHeadLengthPx(imageOne, tipOne, theme.edgeColor(), 1.0))
            .isCloseTo(12.5, within(1.5));
        assertThat(measuredHeadLengthPx(imageThree, tipThree, theme.edgeColor(), 3.0))
            .isCloseTo(26.0, within(2.0));
        assertThat(Math.max(3.0, 1.4 * 1.0 + 1.0)).isLessThan(0.84 * 12.5);
        assertThat(Math.max(3.0, 1.4 * 3.0 + 1.0)).isLessThan(0.84 * 26.04);
        assertThat((double) perpendicularRunPx(imageOne, theme.edgeColor(),
            (int) Math.round(tipOne.getX()) - 14, (int) Math.round(tipOne.getY())))
            .isLessThan(Math.max(3.0, 1.4 * 1.0 + 1.0));
        assertThat((double) perpendicularRunPx(imageThree, theme.edgeColor(),
            (int) Math.round(tipThree.getX()) - 28, (int) Math.round(tipThree.getY())))
            .isLessThan(Math.max(3.0, 1.4 * 3.0 + 1.0));
```

The measurement threshold is `max(3.0, 1.4 × zoom + 1.0)` px: 3.0 at 1.0× and 5.2 at 3.0×. The
anchor is the geometry attachment point, not the first visible pixel: the ring hides the first
`0.7 × zoom` px (`2.1` px at 3×), so an anchored measurement does not under-measure. The
measured profile just beyond the head base (`tip − 14` px at 1×, `tip − 28` px at 3×) falls below
the threshold, where the continuing line is. The last two threshold checks assert the threshold
stays below the base width (`0.84 × L` = 10.5 px at 1×, 21.9 px at 3×).

Tolerance: `within(1.5)` at 1.0× and `within(2.0)` at 3.0×. These reject both the previous 8 px
head and the constant-screen-size rule (which would measure 12.5 px at 3×).

### 6.4 Item 4 — floor

Suggested test method: `paintedArrowHeadFloorHoldsAtLowZoom`.
Fixture: the same `fixture(14.0, 14.0).state` as item 3; paint at zoom 0.6 and zoom 1.0 with
`paintAtZoom`. Tip screen points: (138.6, 70) at 0.6× and (151, 70) at 1.0×.

```java
        double measuredAt06 = measuredHeadLengthPx(imageAt06, tipAt06, theme.edgeColor(), 0.6);
        double measuredAt10 = measuredHeadLengthPx(imageAt10, tipAt10, theme.edgeColor(), 1.0);
        assertThat(measuredAt06).isCloseTo(measuredAt10, within(1.5));
        assertThat(measuredAt06).isGreaterThanOrEqualTo(11.0);
        assertThat(measuredAt10).isGreaterThanOrEqualTo(11.0);
```

Tolerance: `within(1.5)` between the two measurements; both must be at least 11.0 px. The
previous 8 px head fails the 1.0× floor, so the assertion discriminates.

### 6.5 Item 5 — cap, base and split at paint level

Suggested test method: `paintedArrowHeadCapBaseAndBidirectionalSplitAtPaintLevel`.

**The fixture alignment is load-bearing.** The measurements are sub-pixel dependent, so the
fixture must reproduce exactly: viewport `GraphViewport.of(0.0, 0.0, 1.0)`, `SIZE` = 240 × 140 px
(world origin at screen (120, 70)), the edge along world `y = 0` (screen `y = 70`), the enclosure
source whose nearest boundary point is at world `x = 0` (screen `x = 120`), and the target node
centre at world `x = 14` with radius 8, so its boundary — the head tip — is at world `x = 6`
(screen `x = 126`). Then `d = 6` world = 6 px and the capped base plane lands exactly on screen
`x = 120`; the 5.04 px base covers four whole pixel rows (68–71), giving an exact-colour run of 4
and a measured head length of 6. Shifted by 0.25 px the same head produces no run ≥ 3 at all. Do
not snap, round, or translate this fixture.

Fixture builder, returning the canvas state for one-headed (`bidirectional = false`) and
two-headed (`bidirectional = true`) runs:

```java
    private static CanvasState capFixture(final boolean bidirectional) {
        ProjectedNode target = node(FIRST_MAP, "cap-target", LayoutPoint.of(14.0, 0.0));
        EnclosureKey enclosureKey = EnclosureKey.of(source(FIRST_MAP, "cap-enclosure"));
        EnclosureHullKey hullKey = EnclosureHullKey.of(Collections.singletonList(enclosureKey));
        ProjectedEndpointKey enclosureEndpoint = ProjectedEndpointKey.ofEnclosure(enclosureKey);
        ProjectedEndpointKey nodeEndpoint = ProjectedEndpointKey.ofNode(target.key());
        ProjectedEnclosure enclosure = ProjectedEnclosure.of(hullKey, Collections.singletonList(enclosureKey),
            Collections.singletonList(SafeNodeLabel.of("cap-enclosure", "cap-enclosure")), "Map",
            Optional.<EnclosureHullKey>empty(), Collections.<ProjectedNodeKey>emptyList(),
            Collections.<EnclosureHullKey>emptyList(), false, BoundaryTier.SUBTLE);

        Map<ProjectedNodeKey, NodeGeometry> nodes = new LinkedHashMap<ProjectedNodeKey, NodeGeometry>();
        nodes.put(target.key(), NodeGeometry.of(LayoutPoint.of(14.0, 0.0), 8.0));
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
        positions.put(target.key(), LayoutPoint.of(14.0, 0.0));
        Map<EnclosureHullKey, LayoutPoint> anchors = new LinkedHashMap<EnclosureHullKey, LayoutPoint>();
        anchors.put(hullKey, LayoutPoint.of(-25.0, 0.0));
        return CanvasState.of(1L, projection,
            LayoutFrame.of(1L, LayoutPositions.of(positions, anchors), false, 0),
            GraphGeometry.of(nodes, hulls), OperationalStatus.IDLE);
    }
```

A source node cannot pin this: the source disc would overpaint any overshoot that lands inside
it, so the scan would read the same value either way. The enclosure is filled before the edges,
so nothing overpaints the head body.

```java
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
        // Non-vacuity: the enclosure-side head adds near-edge ink vs. the same edge with arrowheads off.
        assertThat(nearColorPixelsIn(twoHeads, theme.edgeColor(), 120, 66, 123, 74, 80))
            .isGreaterThan(nearColorPixelsIn(plainLine, theme.edgeColor(), 120, 66, 123, 74, 80));
```

Tolerances and rationale: `within(1.5)` for the capped length; the base run bound is `[3.5, 6]`
px inclusive; the two-headed run bound is `< 3.0` px; the ink guard is a strict `>` on
`nearColorPixelsIn(..., 80)`. The window `(120, 66)–(123, 74)` is the enclosure half of the gap;
it also contains the enclosure's stroked boundary, which is present in both renders and, at its
hull-stroke colour, lies far outside a distance of 80 from the edge colour. The near-colour
predicate is required here: a 3 px head adds only sub-pixel coverage beyond the line, so the
exact edge-colour count is equal in both renders. Without the two arrow flags propagated, an
implementation draws two 6 px heads with a 4 px run and fails the `< 3.0` bound. Record the
measured near-edge counts in the test as a comment once measured; do not pin them as literals.

### 6.6 Item 6 — theme fill

Suggested test method: `paintedArrowHeadInteriorUsesTheDarkThemeEdgeColour`.
Fixture: the roomy `fixture(14.0, 14.0).state` of item 3 with the dark theme,
`GraphTheme.resolve(CanvasTheme.DARK, registeredMaps())`, painted by
`paint(state, GraphPaintState.empty(), darkTheme, RenderingLevel.FULL)` at zoom 1.0. The head
length is `L = 12.5` px and the tip is screen `(151, 70)`. Sample at `+0.1 L = 1.25` px
perpendicular to the edge at mid-head: world `(24.75, 1.25)`, i.e. pixel `(144, 71)`. That pixel
is inside the clear band between the `0.7` px half-width of the line under the head and the
`0.21 L = 2.625` px half-width of the head at `L / 2`, so exact RGB matching holds.

```java
        assertThat(image.getRGB(144, 71)).isEqualTo(darkTheme.edgeColor().getRGB());
        assertThat(image.getRGB(144, 71)).isNotEqualTo(darkTheme.nodeFill().getRGB());
        assertThat(image.getRGB(144, 71)).isNotEqualTo(darkTheme.nodeStroke().getRGB());
```

Tolerance: none; exact RGB equality is required because the sample is a fully covered pixel. A
larger offset (`0.2 L`) is antialiased — it leaves only `0.01 L = 0.125` px of head outside the
sample — and a smaller one falls back onto the line, which is edge-coloured even with no head
drawn.

### 6.7 Item 7 — no regression

No new method: `suppressAndRestoreDirectionalArrowheadsInThePaintedCanvas` keeps passing
unchanged, and so does every existing `GraphCanvasPaintShould` test, since the head is the only
changed geometry. Run the whole class (and the whole module) as in §7.

## 7. Verification commands

Repository conventions: use `gradle` (not `gradlew`), with the Java 21.0.8-zulu toolchain from
`~/.sdkman/candidates/java/21.0.8-zulu`; add `-PTestLoggingFull` for verbose failures.

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"

# Compile production and test sources.
gradle :freeplane_plugin_graph:compileJava :freeplane_plugin_graph:compileTestJava

# Run the canvas paint tests specified in §6.
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.canvas.GraphCanvasPaintShould"

# Same target with full failure logging.
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.canvas.GraphCanvasPaintShould" -PTestLoggingFull

# Full module regression gate for item 7.
gradle :freeplane_plugin_graph:test
```

Every command must exit 0 (`BUILD SUCCESSFUL`). No translation resource is touched by this
change (`GraphPainter` carries no translatable strings), so the repository's
`gradle format_translation` rule does not apply here; it would be required only if a file under
`*/resources/translations/` were edited, which this change does not do.

## 8. Acceptance evidence

- The mockup renderer and its renders committed under
  `docs/superpowers/specs/mockups/2026-10-10-graph-arrowhead-prominence/`: chosen renders in
  `png/` and rejected alternatives in `png/rejected/`, matching the layout the documented
  regeneration command produces (`java ArrowheadMockups.java png` from the mockup directory, per
  its `README.md`). The chosen detail renders use the design's own length rule at the rendered
  zoom (13.9 px at 1.6×).
- One screenshot of the running application showing a directed edge in the Graph Workspace with
  the larger arrowheads, captured through the repository's established headless/virtual-display
  evidence procedure.

## 9. Out of scope

- No display settings, preferences, translation keys, or persisted XML/schema changes; no
  `DisplaySettings`, `GraphTheme`, `CanvasTheme`, `GraphViewport`, or workspace XML codec edits.
- No arrowhead shape variants (open, stealth) and no per-target-node sizing.
- No changes to the connector adapter or projection, to which edges are directional, or to the
  dim / hover / selection / highlight paint paths.
- No gap, outline, or halo between the line and the head, and no second head colour.
- No zoom clamp and no change to `GraphCanvas.showArrowheads` semantics.
