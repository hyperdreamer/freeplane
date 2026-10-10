# Graph Workspace: directional arrowhead prominence — design

Task identifier: `2026-10-10-graph-arrowhead-prominence`

## Background

In the Graph Workspace canvas, directional edges end in a small filled triangle drawn by
`GraphPainter.paintArrow`. The reported problem is that these arrowheads are difficult to see,
so the direction of a relation is effectively invisible in a normal view (see the reported
screenshot: two edges arriving at the `zfc` node).

Three properties of the current rendering combine to cause this:

1. **The head is barely longer than it is wide.** Length is `8.0 / zoom` and the base is
   `7.0 / zoom`, so the head is a squat wedge rather than a pointer.
2. **The head has no separation from the line it terminates.** It is filled with the same
   colour as the edge line (`theme.edgeColor()`, `BasicStroke(1.4f)`), so the base plane merges
   into the line and reads as a local thickening rather than a direction marker.
3. **The head is a constant screen size while the nodes scale with zoom.** Node radius is
   `8.0 * prominenceScale` world units (up to 14) and is drawn under the viewport zoom, while
   the head is divided by `zoom`. Zooming in therefore makes the head *smaller* relative to the
   node it points at — exactly when the user is looking closely.

`paintArrow` draws with the edge stroke's own composite: under the "dim unrelated" state the
whole edge and head are painted at `AlphaComposite 0.28f`, and nodes (fill + 1.4 px ring) are
painted *after* the edges, so the head tip sits flush against the ring and is partially covered
by it.

## Goals

- A directional arrowhead is unambiguously legible at every zoom level, in the light and in the
  dark canvas theme, at the default edge colour.
- The head keeps reading as a direction marker where the edge line terminates, and where a
  crossing edge or enclosure border passes near the tip.
- The apparent weight of all arrowheads in one view stays uniform: no head looks more important
  than another because of the node it points at.

## Non-goals

- No new display setting, preference, translation key or persisted XML attribute. Arrowhead
  visibility is not user-tunable in this change.
- No change to the `showArrowheads` toggle, to `paintMultiplicity`, to edge colour selection,
  to the node ring, or to the dim/hover/selection/highlight paths.
- No change to the connector adapter or projection: which edges are directional, and at which
  end, is untouched.
- No arrowhead shape variants (open, stealth) and no per-target-node sizing.

## Mockup decision (collaborative design)

Decisions were taken against rendered mockups
(`docs/superpowers/specs/mockups/2026-10-10-graph-arrowhead-prominence/`), which reproduce
`GraphPainter.paintArrow` geometry, `GraphTheme` light and dark colours, and the real paint
order (edges and heads first, node fill and ring afterwards).

| Question | Chosen | Rejected alternatives |
| --- | --- | --- |
| Scope | **Fixed visual tuning**, no new UI | User-adjustable head size or style in Display settings (settings model, XML codec, schema bump) |
| Treatment | **Larger head plus a canvas-colour halo**, filled with the edge colour | Larger only (line still merges into the head); emphasis ink for the head (second edge ink with no meaning in the design language, needs a dark-theme twin); stealth concave head (keeps a fixed shape vocabulary, degrades at fractional zoom) |
| Size across zoom | **Constant floor, growing with zoom** (`max(12.5, 8.68 × zoom)` px), identical for every head in a view | Constant screen size at all zooms (symptom returns at high zoom); scaling with the target node radius (heads of different sizes in one view) |

The halo is stroked in `theme.background()`, so it is correct in both themes without a second
colour and without per-theme tuning.

## Behaviour

For every directional edge, the existing head tip stays exactly where it is today: at the
geometry attachment point on the target node's boundary, pointing along the edge line.

- Head length: `max(12.5, 8.68 × zoom)` screen px, expressed inside the world transform as
  `max(12.5 / zoom, 8.68)` world units. The floor keeps the head at 12.5 px up to zoom 1.44;
  above that it grows linearly with the node.
- Head base: `0.84 × length` (10.5 px at the floor).
- Head fill: unchanged, `theme.edgeColor()`.
- Halo: the same closed triangular path stroked in `theme.background()` at `0.18 × length`
  (2.25 px at the floor), drawn immediately before the fill, with round caps and joins.

Resulting sizes: 12.5 px at 0.6× and 1.0×, 13.9 px at 1.6×, 26 px at 3× — against a maximum
node radius of 22.4 px at 1.6× and 42 px at 3×.

## Architecture

The change is confined to `org.freeplane.plugin.graph.canvas.GraphPainter`.

- `paintArrow` gains one parameter carrying the canvas colour, so the halo uses the theme that
  is already resolved for the frame: `paintArrow(graphics, tip, from, color, background, zoom)`.
- Both call sites in `paintEdges` pass `theme.background()`; they are the only callers.
- The finite-length guard, the axis and perpendicular derivation, and the path construction are
  unchanged; the halo is one additional `draw` of the same path before the existing `fill`.
- `GraphTheme`, `DisplaySettings`, `CanvasTheme` and the workspace XML codec are untouched.

There is one active rendering path: the former constants are replaced, not retained behind a
fallback, per the repository's legacy-removal policy.

## Error handling and edge cases

- Zero-length or non-finite head direction: unchanged early return, so the halo cannot be drawn
  for a degenerate path.
- Dimming: the head and its halo are painted under the existing `AlphaComposite 0.28f`, so the
  halo blends 28% toward the canvas colour behind the line. The gap remains visible in the dimmed
  state; no special case is needed.
- Node overlap at the tip: nodes and rings are still painted after the edges, so the ring still
  wins at the tip and the head remains flush against the boundary. The halo is drawn under the
  node, so it never draws over a ring.
- Small zoom: the floor prevents the head from shrinking below today's visibility threshold and
  keeps renderings at fractional zooms free of sub-pixel wedges.
- Large zoom: the head grows linearly and stays well inside the node-relative budget
  (26 px against a 42 px node radius at 3×), so it cannot dominate the node.
- Extremely long or extremely short edges are unaffected: head size depends on zoom only, never
  on edge length.

## Test strategy

Extend `GraphCanvasPaintShould` using its existing `paint(...)` and `differentPixels(...)`
helpers.

1. **Halo gap is real.** Paint one directed edge at zoom 1.0 and assert that the pixel 0.5 px
   behind the head base on the edge axis (13 px behind the tip, inside the halo band
   `[11.375, 13.625]`) is the canvas colour within 8 per channel, while the pixel 6 px further
   back (19 px behind the tip) is still the edge colour. Without the halo the first sample is
   edge colour, so the assertion is falsifiable.
2. **Floor.** Measure the head length in screen px (span of edge-coloured pixels along the axis
   from the tip, tolerance 1.5 px for the ring overlapping the tip) at 0.6× and at 1.0×; both
   measurements are equal within 1.5 px and both are at least 11 px. The previous 8 px head
   fails this.
3. **Growth.** The measured head length at 3× is at least 1.8× the 1.0× measurement (26 px
   expected against the 12.5 px floor).
4. **Theme correctness.** Painting the same fixture with the dark theme leaves the gap pixel equal
   to the dark canvas colour.
5. **No regression.** `suppressAndRestoreDirectionalArrowheadsInThePaintedCanvas` keeps passing
   unchanged.

## Acceptance evidence

- The mockup renderer and the chosen renders committed under
  `docs/superpowers/specs/mockups/2026-10-10-graph-arrowhead-prominence/`.
- One screenshot of the running application showing a directed edge in the Graph Workspace with
  visibly larger, halo-separated arrowheads, captured through the repository's established
  headless/virtual-display evidence procedure.

## Rollout

No migration, no persisted state change, no user-visible setting. The change takes effect on the
next canvas paint; saved workspaces are unaffected.
