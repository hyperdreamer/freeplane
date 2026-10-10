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

Where the head is drawn: the tip is the geometry attachment point of the target endpoint, which is
a node boundary (`NodeGeometry.boundaryToward`) or, for an edge that ends at an enclosure, the
nearest hull boundary point (`GraphGeometry.edgeAttachment` → `hull.nearestBoundaryPoint`). Edges
are painted before nodes, so a node disc and its 1.4 px ring paint over at most the last 0.7 px of
the tip. The head is filled with `theme.edgeColor()` and inherits whatever composite is active, in
particular `AlphaComposite 0.28f` in the dim-unrelated state.

## Goals

- A directional arrowhead is unambiguously legible at every zoom level, in the light and in the
  dark canvas theme, at the default edge colour.
- The apparent weight of all arrowheads in one view stays uniform: no head looks more important
  than another because of the node it points at.
- No new colour, no new setting, no new persisted state.

## Non-goals

- **No gap or outline between the edge line and the head.** The head stays directly attached to the
  line, as it is today.
- No new display setting, preference, translation key or persisted XML attribute.
- No change to the `showArrowheads` toggle, `paintMultiplicity`, edge colour selection, the node
  ring, or the dim / hover / selection / highlight paths.
- No change to the connector adapter or projection: which edges are directional, and at which end,
  is untouched.
- No arrowhead shape variants (open, stealth) and no per-target-node sizing.

## Mockup decision (collaborative design)

Decisions were taken against rendered mockups
(`docs/superpowers/specs/mockups/2026-10-10-graph-arrowhead-prominence/`), which reproduce
`GraphPainter.paintArrow` geometry, `GraphTheme` light and dark colours, and the real paint order
(edges and heads first, node fill and ring afterwards).

| Question | Chosen | Rejected alternatives |
| --- | --- | --- |
| Scope | **Fixed visual tuning**, no new UI | User-adjustable head size or style in Display settings (settings model, XML codec, schema bump) |
| Treatment | **B: larger head only**, filled with the edge colour, no gap | Canvas-colour halo around the head (rejected after review: enclosure fills are `GraphTheme.hullFill`, an opaque background-blended colour painted *before* the edges, so the halo cuts a light notch out of fills and border strokes and, being drawn under the same 0.28 dim composite, keeps only ~8% of the line/background contrast); emphasis ink for the head (second edge ink with no meaning in the design language, needs a dark-theme twin); stealth concave head (wider shape vocabulary, ragged at fractional zoom) |
| Size across zoom | **Constant floor, growing with zoom** (`max(12.5, 8.68 × zoom)` px), identical for every head in a view | Constant screen size at all zooms (the symptom returns at high zoom); scaling with the target node radius (heads of different sizes in one view) |

## Behaviour

The head tip does not move: it stays at the geometry attachment point described above, pointing
along the edge line.

- Head length, world units: `max(12.5 / zoom, 8.68)` — that is `max(12.5, 8.68 × zoom)` screen px.
  The floor holds the head at 12.5 screen px up to zoom 1.44; above that it grows linearly with the
  node. `8.68` is `0.62 × 14`, the maximum node radius in world units.
- Head half-base, world units: `0.42 × length` (base `0.84 × length`; 10.5 screen px at the floor).
- Head fill: unchanged, `theme.edgeColor()`.
- Gap between line and head: none, as today; the line is drawn under the head.

Resulting head lengths: 12.5 px at 0.6× and 1.0×, 13.9 px at 1.6×, 26 px at 3×.

## Architecture

The change is confined to `org.freeplane.plugin.graph.canvas.GraphPainter`.

- The sizing rule is extracted into two package-private static helpers so it is unit-testable
  without painting:
  `static double arrowHeadLengthWorld(double zoom)` and
  `static double arrowHeadHalfBaseWorld(double worldLength)`.
- Named constants replace the inline literals: the 12.5 px floor, the `8.68` world-unit growth
  term (documented as `0.62 × 14`) and the `0.42` half-base ratio.
- `paintArrow` keeps its current signature `(graphics, tip, from, color, zoom)`; no canvas colour is
  needed, so neither call site in `paintEdges` changes.
- The finite-length guard, the axis and perpendicular derivation, and the path construction are
  unchanged.
- `GraphTheme`, `DisplaySettings`, `CanvasTheme` and the workspace XML codec are untouched.

There is one active rendering path: the former literals are replaced, not retained behind a
fallback, per the repository's legacy-removal policy.

## Error handling and edge cases

- Zero-length or non-finite head direction: unchanged early return.
- Dimming: the head is painted under the existing `AlphaComposite 0.28f` exactly as today; with no
  halo there is no additional dim interaction to reason about.
- Enclosure endpoints: the tip can lie on a hull boundary rather than a node disc; the head
  orientation and size depend only on the line direction and zoom, so both endpoints behave
  identically.
- Node overlap at the tip: unchanged; nodes and rings are still painted after the edges, so at most
  the last 0.7 px of the tip is covered.
- Small zoom: the floor prevents sub-pixel wedges and keeps renderings at fractional zoom free of
  ragged edges, because the triangle path scales with the head length rather than being rasterised
  at a fixed size.
- Large zoom: the head grows linearly and unbounded (there is no zoom clamp in `GraphViewport`).
  At 3× the head is 26 px against a minimum node radius of 24 px (48 px diameter), i.e. about 54% of
  the smallest node's diameter — proportionate; against the largest node (42 px radius) it is 31%.
- Extremely long or short edges are unaffected: head size depends on zoom only, never edge length.

## Test strategy

Extend `GraphCanvasPaintShould`. Zoom variants must use the existing `paintAtZoom(...)` helper;
`paint(...)` hard-codes zoom 1.0.

1. **Sizing rule (unit).** `arrowHeadLengthWorld` returns 12.5 at zoom 1.0, 0.6 and at the 1.44
   crossover, returns 26.04 at zoom 3.0, and is strictly increasing above the crossover.
   `arrowHeadHalfBaseWorld(12.5)` is 5.25.
2. **Painted head length.** Paint one directed edge at zoom 1.0 and measure the head by its width
   profile: walking back from the tip along the edge axis, count the perpendicular run of
   edge-coloured pixels at each distance; the head length is the largest distance whose width is at
   least 3 px. Assert it is 12.5 ± 1.5 px. This is falsifiable: the previous 8 px head measures
   about 8 px and fails the assertion, and because the measurement keys on the base width it is not
   confused with the 1.4 px line continuing past the head.
3. **Floor.** The measured head length at 0.6× equals the 1.0× measurement within 1.5 px, and both
   are at least 11 px.
4. **Growth.** The measured head length at 3× is at least 1.8× the 1.0× measurement.
5. **Theme fill.** Painting the same fixture with the dark theme leaves the head interior matching
   `GraphTheme`'s dark edge colour, not the node fill or ring colour.
6. **No regression.** `suppressAndRestoreDirectionalArrowheadsInThePaintedCanvas` keeps passing
   unchanged, and so does every existing paint test, since the head is the only changed geometry.

## Acceptance evidence

- The mockup renderer and the chosen renders committed under
  `docs/superpowers/specs/mockups/2026-10-10-graph-arrowhead-prominence/`.
- One screenshot of the running application showing a directed edge in the Graph Workspace with the
  larger arrowheads, captured through the repository's established headless/virtual-display
  evidence procedure.

## Rollout

No migration, no persisted state change, no user-visible setting. The change takes effect on the
next canvas paint; saved workspaces are unaffected.
