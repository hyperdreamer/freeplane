# Graph Workspace: directional arrowhead prominence — design

Task identifier: `2026-10-10-graph-arrowhead-prominence`

## Background

In the Graph Workspace canvas, directional edges end in a small filled triangle drawn by
`GraphPainter.paintArrow`. The reported problem is that these arrowheads are difficult to see,
so the direction of a relation is effectively invisible in a normal view (reported screenshot:
two edges arriving at the `zfc` node).

Two properties of the current rendering cause this:

1. **The head is barely longer than it is wide.** Length is `8.0 / zoom` and the base is
   `7.0 / zoom`, so the head is a squat wedge rather than a pointer.
2. **The head is a constant screen size while the nodes scale with zoom.** Node radius is
   `GraphGeometryEngine.BASE_RADIUS * NodeProminence.scale()` = `8.0 * scale`, `scale` in
   `[1.0, 1.75]`, and nodes are drawn under the viewport zoom, while the head divides by `zoom`.
   Zooming in therefore makes the head *smaller* relative to the node it points at — exactly when
   the user is looking closely.

The relevant paint facts, all verified in the source:

- The edge line and the node ring are stroked with `theme.edgeStroke()`, a `BasicStroke(1.4f)`,
  inside the world transform. Both are therefore `1.4 * zoom` screen px wide. The ring covers
  `0.7 * zoom` screen px of the head tip.
- The tip is the geometry attachment point of the target endpoint: a node boundary
  (`NodeGeometry.boundaryToward`) or, for an edge ending at an enclosure, the nearest hull point
  (`GraphGeometry.edgeAttachment` → `hull.nearestBoundaryPoint`).
- Edges are painted before nodes, so the node disc and ring paint over the last `0.7 * zoom` px of
  the tip — and over any part of a head that overshoots a short edge into the source node.
- The head is filled with `theme.edgeColor()` and inherits the active composite, in particular
  `AlphaComposite 0.28f` in the dim-unrelated state.

## Goals

- A directional arrowhead is unambiguously legible at every zoom level, in the light and in the
  dark canvas theme, at the default edge colour.
- The apparent weight of all arrowheads in one view stays uniform: no head looks more important
  than another because of the node it points at.
- A head never grows past the space its edge actually has, and it keeps its arrow aspect when the
  space forces it to shrink.
- No new colour, no new setting, no new persisted state.

## Non-goals

- **No gap or outline between the edge line and the head.** The head stays directly attached to the
  line, as today.
- No new display setting, preference, translation key or persisted XML attribute.
- No change to the `showArrowheads` toggle, `paintMultiplicity`, edge colour selection, the node
  ring, or the dim / hover / selection / highlight paths.
- No change to the connector adapter or projection: which edges are directional, and at which end,
  is untouched.
- No arrowhead shape variants (open, stealth) and no per-target-node sizing.

## Mockup decision (collaborative design)

Decisions were taken against rendered mockups
(`docs/superpowers/specs/mockups/2026-10-10-graph-arrowhead-prominence/`), which reproduce
`GraphPainter.paintArrow` geometry, `GraphTheme` light and dark colours, the `1.4 * zoom` edge and
ring strokes, and the real paint order (edges and heads first, node fill and ring afterwards).

| Question | Chosen | Rejected alternatives |
| --- | --- | --- |
| Scope | **Fixed visual tuning**, no new UI | User-adjustable head size or style in Display settings (settings model, XML codec, schema bump) |
| Treatment | **B: larger head only**, filled with the edge colour, no gap | Canvas-colour halo around the head (enclosure fills are `GraphTheme.hullFill`, an opaque background-blended colour painted *before* the edges, so the halo cuts a light notch out of fills and border strokes and, under the same 0.28 dim composite, keeps only ~8% of the line/background contrast); emphasis ink for the head (a second edge ink with no meaning in the design language, needs a dark-theme twin); stealth concave head (wider shape vocabulary, ragged at fractional zoom) |
| Size across zoom | **Constant floor, growing with zoom** (`max(12.5, 8.68 × zoom)` px), identical for every head in a view | Constant screen size at all zooms (the symptom returns at high zoom); scaling with the target node radius (heads of different sizes in one view) |

## Behaviour

The head tip does not move: it stays at the geometry attachment point described above, pointing
along the edge line. Let `d` be the distance in world units between the two attachment points of the
edge, and let `a = d` when only one end carries a head and `a = d / 2` when both ends do.

1. Requested head length, screen px: `max(12.5, 8.68 × zoom)`. The floor holds the head at 12.5 px
   up to zoom 1.44; above that it grows linearly with the node. `8.68` is `0.62 × 14`, the maximum
   node radius in world units.
2. Effective head length, screen px: `L = min(max(12.5, 8.68 × zoom), a × zoom)`. The cap keeps a
   head inside the space its edge actually has, so it can never overshoot the far endpoint's
   boundary; `a = d / 2` for a bidirectional edge makes the two heads meet base to base instead of
   overlapping.
3. Head half-base, screen px: `0.42 × L`, i.e. base `0.84 × L`. The aspect is always 0.84 : 1: when
   the cap shortens a head, the base shrinks with it instead of turning the head into a bar. At the
   tightest layout condition (`d = 6` world, both ends arrowed, 0.6× zoom) the base is 2.52 world
   units, still 1.8× the 1.4-world-unit line, so the head remains a visible pointer.
4. The path is built in world units: length `L / zoom`, half-base `0.42 × L / zoom`.
5. Head fill: unchanged, `theme.edgeColor()`. Gap between line and head: none, as today; the line is
   drawn under the head.

Resulting head lengths on an edge with room: 12.5 px at 0.6× and 1.0×, 13.9 px at 1.6×, 26 px at 3×.

## Architecture

The change is confined to `org.freeplane.plugin.graph.canvas.GraphPainter`.

- Three package-private static helpers, in screen px, hold the whole rule and are unit-testable
  without painting:
  - `static double arrowHeadLengthPx(double zoom)` → `max(12.5, 8.68 × zoom)`
  - `static double effectiveArrowHeadLengthPx(double zoom, double availableWorldLength)` →
    `min(arrowHeadLengthPx(zoom), availableWorldLength × zoom)`
  - `static double arrowHeadHalfBasePx(double lengthPx)` → `0.42 × lengthPx`
- Named constants replace the inline literals: the 12.5 px floor, the `8.68` growth term
  (documented as `0.62 × 14`) and the `0.42` half-base ratio.
- `paintEdges` computes `edge.arrowAtFirst() && edge.arrowAtSecond()` once and passes it to both
  `paintArrow` calls, so the call sites do change. `paintArrow` already computes
  `length = |tip − from|`, which is exactly `d`; it derives `a` from that and the shared flag, then
  `L`, then the world-space length and half-base from `L`.
- The finite-length guard, the axis and perpendicular derivation, and the path construction are
  unchanged. The guard runs before sizing, so a degenerate direction never reaches the cap.
- `GraphTheme`, `DisplaySettings`, `CanvasTheme` and the workspace XML codec are untouched.

There is one active rendering path: the former literals are replaced, not retained behind a
fallback, per the repository's legacy-removal policy.

## Error handling and edge cases

- Zero-length or non-finite head direction: handled by the existing guard, which returns before any
  sizing or path work; the cap cannot be reached with `d = 0`.
- Dimming: the head is painted under the existing `AlphaComposite 0.28f` exactly as today; with no
  halo there is no additional dim interaction.
- Enclosure endpoints: the tip can lie on a hull boundary rather than a node disc; head orientation
  and size depend only on the line direction, the zoom and `a`, so both endpoint kinds behave
  identically.
- Node overlap at the tip: unchanged; the ring covers up to `0.7 * zoom` screen px (0.42 px at 0.6×,
  2.1 px at 3×).
- Small zoom: the floor keeps heads legible, at the cost of proportion. Below the 1.44 crossover the
  head is 12.5 px against a minimum node diameter of `16 * zoom` px — 1.30× at 0.6× and 0.78× at 1.0×
  — so at low zoom a head on a small node reads as slightly larger than the node itself. This is the
  deliberate trade-off for legibility, and it is bounded by the cap above.
- Large zoom: the head grows linearly and unbounded (there is no zoom clamp in `GraphViewport`).
  At 3× the head is 26 px against a minimum node radius of 24 px (48 px diameter), i.e. about 54% of
  the smallest node's diameter, and 31% of the largest (42 px radius).
- Short edges: the cap shortens the head and the base together, keeping the aspect. The previous
  literal `8.0 / zoom` head already overshot at low zoom; that is what the cap fixes, and the
  overhang it removes was itself partly hidden by the source node disc painted on top.
- Fractional zoom: the triangle path scales with the head length rather than being rasterised at a
  fixed size, so the head does not develop ragged edges.

## Test strategy

Extend `GraphCanvasPaintShould`. Zoom variants use the existing `paintAtZoom(...)` helper;
`paint(...)` hard-codes zoom 1.0. Pixel predicates use exact RGB matching (`colorPixelsIn`), not the
near-colour variant.

1. **Rule (unit, no pixels).** `arrowHeadLengthPx` is 12.5 at zooms 0.6, 1.0 and 1.44, is 26.04 at
   zoom 3.0, and increases strictly above the exact crossover `12.5 / 8.68 ≈ 1.4401` (sample 1.45).
   `arrowHeadHalfBasePx(12.5)` is 5.25.
2. **Cap and aspect (unit, no pixels).** `effectiveArrowHeadLengthPx(1.0, 100)` is 12.5, so the rule
   binds when there is room; `effectiveArrowHeadLengthPx(0.6, 6.0)` is 3.6, so the cap binds when
   there is not; `effectiveArrowHeadLengthPx(1.0, 3.0)` is 3.0, the bidirectional `d / 2` case; and
   `arrowHeadHalfBasePx(effectiveArrowHeadLengthPx(0.6, 6.0))` is 1.512, pinning that the base
   follows the effective length rather than the requested one. These are pure-function assertions:
   they fail if the cap is removed or if the base ignores it. A pixel-level cap test is deliberately
   not used, because the source node disc overpaints an overshoot and a scan of the final image would
   measure `min(L, a × zoom)` with or without the cap.
3. **Painted head length.** Paint one directed edge with room, at zoom 1.0, and measure the head by
   its width profile: walking back from the tip along the edge axis, count the perpendicular run of
   exactly edge-coloured pixels at each distance; the head length is the largest distance whose width
   is at least `max(3.0, 1.4 × zoom + 1.0)` px. That threshold is required because the line is
   `1.4 × zoom` px wide (4.2 px at 3×, which a fixed 3 px threshold would treat as head). Assert
   12.5 ± 1.5 px at zoom 1.0 and 26 ± 2 px at zoom 3.0, with the node radius reaching 42 px inside
   the 240 × 140 px test image; also assert the threshold stays below the head's base width
   (`0.84 × L`, i.e. 10.5 px at 1× and 21.9 px at 3×) and that the measured profile just beyond the
   head base falls below the threshold.
4. **Floor.** The measured head length at 0.6× equals the 1.0× measurement within 1.5 px, and both
   are at least 11 px. The previous 8 px head fails this at zoom 1.0, so the assertion discriminates.
5. **Growth.** The measured head length at 3× is at least 1.8× the 1.0× measurement.
6. **Theme fill.** Painting the same fixture with the dark theme leaves the head interior matching
   `GraphTheme`'s dark edge colour, not the node fill or ring colour.
7. **No regression.** `suppressAndRestoreDirectionalArrowheadsInThePaintedCanvas` keeps passing
   unchanged, and so does every existing paint test, since the head is the only changed geometry.

## Acceptance evidence

- The mockup renderer and its renders committed under
  `docs/superpowers/specs/mockups/2026-10-10-graph-arrowhead-prominence/`, chosen renders in `png/`
  and rejected alternatives in `png/rejected/`, matching the layout the documented regeneration
  command produces.
- One screenshot of the running application showing a directed edge in the Graph Workspace with the
  larger arrowheads, captured through the repository's established headless/virtual-display evidence
  procedure.

## Design history

- Round 1 review raised 9 findings. The treatment was changed from "larger head plus canvas-colour
  halo" to "larger head only", because the halo paints canvas colour over enclosure fills and border
  strokes, which are painted before the edges, and loses most of its contrast under dimming.
- Round 2 review raised 6 findings: helper units (screen vs world), a width threshold that measured
  the line instead of the head, the ring width under zoom, the low-zoom size ratio against small
  nodes, heads protruding behind the source node on short edges, and mockup hygiene.
- Round 3 review raised 7 findings: the space cap was untestable at pixel level, the bidirectional
  cap overlapped instead of meeting base to base, the cap broke the head aspect, the pixel predicate
  was unpinned, the crossover precision, the mockup directory layout, and contradictory wording
  about the `paintArrow` signature. All are addressed above.

## Rollout

No migration, no persisted state change, no user-visible setting. The change takes effect on the
next canvas paint; saved workspaces are unaffected.
