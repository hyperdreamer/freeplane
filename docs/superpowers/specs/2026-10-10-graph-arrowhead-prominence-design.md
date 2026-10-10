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
- Edges are painted before nodes, so a node disc and ring paint over the last `0.7 * zoom` px of
  the tip, and over any part of a head that overshoots a short edge into the source node. An
  enclosure does not: hulls are filled before the edges, so nothing overpaints an overshoot that
  points at an enclosure source.
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
edge.

1. Available edge length, world units: `a = d` when only one end carries a head, and `a = d / 2`
   when both ends do. Halving keeps two heads on a short bidirectional edge from overlapping: they
   meet base to base at the midpoint.
2. Requested head length, screen px: `max(12.5, 8.68 × zoom)`. The floor holds the head at 12.5 px
   up to zoom 1.44; above that it grows linearly with the node. `8.68` is `0.62 × 14`, the maximum
   node radius in world units.
3. Effective head length, screen px: `L = min(max(12.5, 8.68 × zoom), a × zoom)`. The cap keeps a
   head inside the space its edge actually has, so it can never overshoot the far endpoint's
   boundary.
4. Head half-base, screen px: `0.42 × L`, i.e. base `0.84 × L`. The aspect is always 0.84 : 1: when
   the cap shortens a head, the base shrinks with it instead of turning the head into a bar. The
   capped base is at least as wide as the `1.4 × zoom` line whenever `a ≥ 1.667` world units; at the
   target separation gap (`NodeSeparationProjection.MIN_GAP = 6` world) it is 5.04 world units with
   one head and 2.52 with two, i.e. 3.6× and 1.8× the line width.
5. The path is built in world units: length `L / zoom`, half-base `0.42 × L / zoom`.
6. Head fill: unchanged, `theme.edgeColor()`. Gap between line and head: none, as today; the line is
   drawn under the head.

Resulting head lengths on an edge with room: 12.5 px at 0.6× and 1.0×, 13.9 px at 1.6×, 26 px at 3×.

## Architecture

The change is confined to `org.freeplane.plugin.graph.canvas.GraphPainter`.

- Four package-private static helpers hold the whole rule and are unit-testable without painting:
  three return screen px and one takes and returns a world length:
  - `static double arrowHeadLengthPx(double zoom)` → `max(12.5, 8.68 × zoom)`
  - `static double availableArrowHeadWorldLength(double attachmentDistanceWorld, boolean arrowAtFirst,
    boolean arrowAtSecond)` → `arrowAtFirst && arrowAtSecond ? d / 2 : d`
  - `static double effectiveArrowHeadLengthPx(double zoom, double availableWorldLength)` →
    `min(arrowHeadLengthPx(zoom), availableWorldLength × zoom)`
  - `static double arrowHeadHalfBasePx(double lengthPx)` → `0.42 × lengthPx`
- The `d` versus `d / 2` decision exists only inside `availableArrowHeadWorldLength`, and both the
  helper itself and the wiring that feeds it are pinned: the helper by a unit assertion, the wiring
  by the two-headed paint probe in test item 5, which fails if the flags are not propagated.
- Named constants replace the inline literals: the 12.5 px floor, the `8.68` growth term
  (documented as `0.62 × 14`) and the `0.42` half-base ratio.
- The wiring is fixed: `paintArrow(graphics, tip, from, color, zoom, boolean arrowAtFirst,
  boolean arrowAtSecond)`, with `paintEdges` passing `edge.arrowAtFirst()` and `edge.arrowAtSecond()`
  at both call sites, so the call sites do change. `paintArrow` already computes
  `length = |tip − from|`, which is exactly `d`; it derives
  `a = availableArrowHeadWorldLength(length, arrowAtFirst, arrowAtSecond)`, then
  `L = effectiveArrowHeadLengthPx(zoom, a)`, then the world-space length `L / zoom` and half-base
  `arrowHeadHalfBasePx(L) / zoom`.
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
- Short edges: `NodeSeparationProjection.MIN_GAP = 6` world is a separation target, not a hard
  invariant — residual violations are recorded rather than rejected, and pinned pairs are skipped —
  so smaller `d` is reachable. For `d` below about `1.667` world units the capped base `0.84 · a`
  is narrower than the `1.4`-world-unit line and the head reads as a small nub merged into the line.
  The crossover is on `a`, not on `d`: with two heads `a = d / 2`, so a head is sub-line-width for
  `d < 1.667` world with one head and for `d < 3.333` with two. That is accepted: such an edge is far
  shorter than the target node separation, so its two endpoints are effectively touching.
- The previous literal `8.0 / zoom` head already overshot short edges at low zoom; where that
  overshoot landed inside the source node it was hidden by the node disc, and where it landed
  outside it was visible as a head protruding behind the source node.
- Fractional zoom: the triangle path scales with the head length rather than being rasterised at a
  fixed size, so the head does not develop ragged edges.

## Test strategy

Extend `GraphCanvasPaintShould`. Zoom variants use the existing `paintAtZoom(...)` helper;
`paint(...)` hard-codes zoom 1.0. Pixel predicates use exact RGB matching (`colorPixelsIn`), not the
near-colour variant, with the single exception noted in item 5, where a sub-pixel difference can only
be seen by a non-exact predicate.

1. **Rule (unit, no pixels).** `arrowHeadLengthPx` is 12.5 at zooms 0.6, 1.0 and 1.44, is 26.04 at
   zoom 3.0, and increases strictly (compared with `>`) above the exact crossover
   `12.5 / 8.68 ≈ 1.4401`, sampled at 1.45. `arrowHeadHalfBasePx(12.5)` is 5.25.
2. **Cap wiring (unit, no pixels).** `availableArrowHeadWorldLength(6.0, false, false)` is 6.0,
   `(6.0, true, true)` is 3.0 and `(6.0, true, false)` is 6.0, so the bidirectional split lives in
   exactly one testable place. `effectiveArrowHeadLengthPx(1.0, 100.0)` is 12.5, so the rule binds
   when there is room; `effectiveArrowHeadLengthPx(0.6, 6.0)` is `6.0 × 0.6`, so the cap binds when
   there is not; `effectiveArrowHeadLengthPx(1.0, 3.0)` is 3.0, the bidirectional case; and
   `arrowHeadHalfBasePx` of that capped length is `0.42 × 6.0 × 0.6`. Compare with `within(1e-9)`,
   not exact equality: `6.0 × 0.6` is `3.5999999999999996` in double arithmetic, so asserting exactly
   `3.6` would fail a correct implementation. Helper composition alone cannot pin the paint path, so
   item 5 repeats the length, base and split checks against painted pixels.
3. **Painted head length.** Paint one directed edge with room, and measure the head by its width
   profile: walking from the **geometry attachment point** outwards along the edge axis, count the
   perpendicular run of exactly edge-coloured pixels at each distance; the head length is the largest
   distance whose width is at least `max(3.0, 1.4 × zoom + 1.0)` px. The anchor matters: the ring
   hides the first `0.7 × zoom` px (2.1 px at 3×), and anchoring at the first visible pixel instead
   would under-measure by that amount. The zoom-aware threshold is required because the line is
   `1.4 × zoom` px wide (4.2 px at 3×, which a fixed 3 px threshold would treat as head). Use a
   fixture: put the head on a node of radius 14 world units (42 px at 3×) — the existing
   `fixture(double)` sets only the *first* node's radius, so the fixture must be parameterised or
   mirrored so the head lands on the 14-unit node — with an attachment distance that leaves
   the cap unbound; assert 12.5 ± 1.5 px at zoom 1.0 and 26 ± 2 px at zoom 3.0, which rejects both
   the previous 8 px head and the constant-screen-size rule (which would measure 12.5 px at 3×). Also assert the
   threshold stays below the head's base width (`0.84 × L`: 10.5 px at 1×, 21.9 px at 3×) and that
   the measured profile just beyond the head base falls below the threshold, where the continuing
   line is.
4. **Floor.** The measured head length at 0.6× equals the 1.0× measurement within 1.5 px, and both
   are at least 11 px. The previous 8 px head fails this at zoom 1.0, so the assertion discriminates.
5. **Cap, base and split at paint level.** Use a fixture whose *source* endpoint is an enclosure and
   whose target is a node, so the head sits at the node while the space behind it belongs to the
   enclosure and nothing overpaints it; `d = 6` world, zoom 1.0, node radius 8 world, threshold
   `max(3.0, 1.4 × zoom + 1.0)` = 3.0 px. A node source cannot pin this: the source disc overpaints
   any overshoot that lands inside it, so the scan would read the same value either way.
   The fixture is pinned in screen space, because these measurements are sub-pixel dependent: with
   `GraphViewport.of(0, 0, 1.0)` and a 240 × 140 px image the world origin is screen (120, 70), so put
   the edge along world `y = 0` (screen `y = 70`), the enclosure boundary at world `x = 0`
   (screen `x = 120`) and the target node centre at world `x = 14` with radius 8, its boundary — the
   head tip — at world `x = 6` (screen `x = 126`). Then `d = 6` world = 6 px and the capped base plane
   lands exactly on screen `x = 120`; a 5.04 px base covers four whole pixel rows (68–71), giving an
   exact-colour run of 4 and a measured head length of 6. Shifted by 0.25 px the same head produces no
   run ≥ 3 at all, so the alignment is part of the specification, not an implementation detail.
   - **Cap:** the measured head length is 6 ± 1.5 px. Without the cap it measures about 12 px (its base
     plane lands off-grid, so treat this as rationale for the probe, never as a tight assertion).
   - **Base follows the capped length:** the largest perpendicular run of exactly edge-coloured pixels
     at the capped base is in `[3.5, 6]` px. The capped base is 5.04 px nominal (4 px measured at the
     pinned alignment); building the half-base from the rule length would show 10.5 px nominal (about
     8 px measured) and fail the upper bound, and a slimmer base fails the lower bound.
   - **Bidirectional split:** repeat with a two-headed edge at the same `d`. Each head is capped at
     `a = d / 2 = 3` world, i.e. 3 px long with a 2.52 px base, so the exact-colour run never reaches
     the 3.0 px threshold; assert the maximum run stays below 3 px. An implementation that does not
     propagate the two arrow flags draws two 6 px heads with a 4 px run and fails. That bound alone
     would also pass a render with no head at all, so the same two-headed render must additionally
     show strictly more near-edge ink in the enclosure half of the gap (screen `x` 120–123,
     `y` 66–74) than the same edge painted with `showArrowheads` off. This one comparison must not use
     exact RGB matching: a 3 px head adds only sub-pixel coverage beyond the line, so the exact
     edge-colour count is equal in both renders. Use `nearColorPixelsIn(..., 80)` on that window and
     assert a strict `>`; the window also contains the enclosure's stroked boundary, which is present
     in both renders and, at its hull-stroke colour, lies far outside a distance of 80 from the edge
     colour. The implementer records the measured counts in the test rather than here.
6. **Theme fill.** Paint the roomy fixture of item 3 (head length 12.5 px at zoom 1.0) with the dark
   theme and assert the head interior matches `GraphTheme`'s dark edge colour rather than the node
   fill or ring colour. Sample off-axis at `+ 0.1 L = 1.25` px perpendicular, inside the clear band
   between the `0.7` px half-width of the line under the head and the `0.21 L = 2.625` px half-width
   of the head at `L / 2`: that pixel is fully inside the head and clear of the line, so exact RGB
   matching holds. A larger offset is
   antialiased — `0.2 L` leaves only `0.01 L` (0.125 px) of head outside the sample and would fail —
   and a smaller one falls back onto the line, which is edge-coloured even with no head drawn.
7. **No regression.** `suppressAndRestoreDirectionalArrowheadsInThePaintedCanvas` keeps passing
   unchanged, and so does every existing paint test, since the head is the only changed geometry.

## Acceptance evidence

- The mockup renderer and its renders committed under
  `docs/superpowers/specs/mockups/2026-10-10-graph-arrowhead-prominence/`, chosen renders in `png/`
  and rejected alternatives in `png/rejected/`, matching the layout the documented regeneration
  command produces. The chosen detail renders use the design's own length rule at the rendered zoom
  (13.9 px at 1.6×).
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
  was unpinned, crossover precision, the mockup directory layout, and contradictory wording about
  the `paintArrow` signature.
- Round 4 review raised 5 findings: the cap assertions never exercised the call-site wiring, the
  measurement anchor was unstated (which invalidated the 3× expectation), the "no pixel cap test"
  rationale was too broad because an enclosure source is not overpainted, the chosen mockup was
  rendered at the floor length rather than the rule, and the `d = 6` claim was scoped too tightly.
- Round 5 review raised 3 findings: the bidirectional split was still selected at the call site and
  untested by pixels, the capped base was pinned only by helper composition, and the sub-line-width
  crossover was stated on `d` instead of `a`.
- Round 6 review raised 6 findings: the architecture paragraph contradicted itself about the
  `paintArrow` wiring, the two-headed probe could pass with no head drawn at all, the capped base
  bound was one-sided, the theme-fill sample sat on the line under the head, the helper unit clause
  was wrong for the world-length helper, and the growth item was redundant with the zoom assertions.
- Round 7's review child emitted no verdict (it read every source file, analysed, then went idle).
  Controller verification found and fixed two residual defects: a stale "called from `paintEdges`"
  phrase that contradicted the settled wiring, and a theme-fill sample at `+0.2 L` that sat inside the
  head's antialiased edge.
- Round 8 review raised 5 findings: the bidirectional non-vacuity guard could not pass under exact
  matching (3 exact pixels with and without heads; `nonBackgroundPixelsIn` gives 10 versus 9), the
  item-2 expected doubles were not the computed doubles (`6.0 × 0.6` is `3.5999999999999996`), the
  item-6 offset was mis-described as halfway, the helper unit clause was still imprecise, and the
  item-3 radius belonged to the wrong node.
- Round 9 review (which re-implemented the design in a scratch copy and ran the real paint path)
  raised 2 findings: item 5's exact-run assertions were sub-pixel dependent, so a 0.25 px shift made
  a correct head fail them while a no-split mutant passed, and the quoted ink counts came from a
  stroke-free raster rather than the painted enclosure fixture. The fixture is now pinned in screen
  space and the ink guard is stated as a strict `>` on the near-colour predicate.
- Round 10 review reproduced the pinned fixture against the real paint path and returned CLEAN: a
  correct capped head measures 6 px with a 4 px base run, a no-split mutant measures 4 and fails, a
  0.25 px shift inverts the results (confirming the pin is load-bearing), and the near-edge ink guard
  measures 6 versus 3. Its one refinement — the uncapped reference measures 12 px, not 12.5 — is
  applied above.

## Rollout

No migration, no persisted state change, no user-visible setting. The change takes effect on the
next canvas paint; saved workspaces are unaffected.
