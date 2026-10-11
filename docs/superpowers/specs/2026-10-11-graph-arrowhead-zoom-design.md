# Graph Workspace: arrowhead size proportional to zoom — design

Task identifier: `2026-10-11-graph-arrowhead-zoom`

## Background

In the Graph Workspace canvas, directional edges end in a small filled triangle drawn by
`GraphPainter.paintArrow`. The reported problem is that these arrowheads look awkward when
the graph is zoomed out: they stay large while the nodes shrink, so a zoomed-out view fills
with heavy dark wedges that are bigger than the nodes they point at.

The shipped rule, from the 2026-10-10 arrowhead-prominence design, is
`L = min(max(12.5, 8.68 x zoom), a x zoom)` screen px, where `a` is the available attachment
distance in world units (`d`, or `d / 2` when both ends carry a head). The `12.5` is a
screen-space floor: below the crossover `12.5 / 8.68 ~ 1.4401` the requested head length stops
scaling with zoom, so its world length `12.5 / zoom` grows as the user zooms out.

The reporter's workspace file `math.fpg` and its Dropbox sync-conflict siblings record the same
three maps at four viewport zooms: `0.14846481908261` (current `math.fpg`),
`0.37809529736878794` (`math.sync-conflict-20261011-131807-QPOKO56 (Arch-MSSD0's conflicted copy
2026-10-11).fpg`), `0.7487174552196292` (the two 2026-10-11 15:47 files) and
`2.7979997032776502` (`math.sync-conflict-20261011-131807-QPOKO56.fpg`). The reported
screenshot's head is a 13 x 10 px bounding box, which pins it to the 12.5 px floor at an
unrecorded zoom below the 1.4401 crossover, so the exact reported zoom is unknown and only
bounded above by the crossover. The defect is present at every recorded low zoom: the node
diameter is `16 x zoom .. 28 x zoom`, so the 12.5 px floor head is 1.2-2.1x the node diameter at
`0.3781` and 3.0-5.3x at `0.1485`, and it stays 12.5 px while the nodes keep shrinking. Those
disc ranges are derived from the node radius model, not pixel-measured: the screenshot's disc
pixels are heavily antialiased (only two exactly node-fill-coloured pixels appear in the whole
image, consistent with discs a few px across).

The relevant paint facts, all verified in the source:

- Node radius is `GraphGeometryEngine.BASE_RADIUS * NodeProminence.scale()` = `8.0 * scale` with
  `scale` in `[1.0, 1.75]`, so the node diameter in screen px is `16 * zoom` to `28 * zoom`.
- The head is filled with `theme.edgeColor()` and is painted before the nodes. The head tip is the
  node boundary point, so the fill does not cover the head; the `1.4`-world node ring (half-width
  `0.7`) overpaints the first `0.7 * zoom` screen px by the tip.
- The edge line and the node ring are stroked with `theme.edgeStroke()`, a `BasicStroke(1.4f)`,
  inside the world transform, so both are `1.4 * zoom` screen px wide.
- `paintEdges` already propagates `edge.arrowAtFirst()` and `edge.arrowAtSecond()` into
  `paintArrow`, and `paintArrow` already computes `length = |tip - from|` = `d`.

## Goals

- The head is a constant world size, so it scales with the graph at every zoom level: the ratio
  between a head and any given node is independent of zoom (across nodes it still varies with
  node prominence: 0.45 of the largest node diameter and 0.78 of the smallest, at every zoom).
- The `1x` appearance approved in the 2026-10-10 design is preserved exactly: 12.5 px at zoom 1.
- A head never grows past the space its edge actually has, and it keeps its arrow aspect when
  the space forces it to shrink.
- No new colour, no new setting, no new persisted state.

## Non-goals

- No change to the edge line width, node radius or prominence, label or leader geometry,
  multiplicity cue, pins, hulls, or the dim / hover / selection / highlight paths.
- No change to `showArrowheads` or to `GraphTheme` light and dark colours.
- No per-target-node sizing (heads of different sizes in one view were rejected before).
- No arrowhead shape variants (open, stealth).
- No fade-out or suppression threshold for sub-pixel heads.

## Mockup decision (collaborative design)

Decisions were taken against rendered mockups
(`docs/superpowers/specs/mockups/2026-10-11-graph-arrowhead-zoom/`), which mirror
`GraphPainter.paintArrow` triangle geometry and paint order for one-headed long edges (the cap is
not exercised: every rendered edge is long enough that `a > 12.5` world and the cap stays
unbound), and show the same world scene at the reporter's workspace zooms.

| Question | Chosen | Rejected alternatives |
| --- | --- | --- |
| Size across zoom | **B: constant world length 12.5** (`L = min(12.5, a) x zoom` px) | **A: `8.68 x zoom` px**, the shipped growth term (0.62 x max node radius): also removes the floor, but shrinks the head to 8.68 px at `1x`, below the size approved in the 2026-10-10 design. **A small screen-space floor** (for example 4 px on top of a proportional rule): keeps heads perceptible at the `0.1485` overview zoom, but restores node-sized heads there, which is the reported defect |
| Constant provenance | 12.5 world = `0.8929 x 14.0`, the maximum node radius (`BASE_RADIUS 8.0 x MAX_SCALE 1.75`); the literal 12.5 is the old 1x floor length, kept as a world constant | Tying the head to the actual projected maximum node radius (view-dependent head sizes) |

Chosen renders are in `png/` (`chosen-rule-b.png`, `before-shipped-rule.png`,
`zoom-rule-comparison.png`), rejected renders under `png/rejected/`.

## Behaviour

The head tip does not move: it stays at the geometry attachment point, pointing along the edge
line. Let `d` be the distance in world units between the two attachment points of the edge.

1. Available edge length, world units: `a = d` when only one end carries a head, and `a = d / 2`
   when both ends do. Halving keeps two heads on a short bidirectional edge from overlapping;
   they meet base to base at the midpoint.
2. Requested head length: a constant `12.5` world units, i.e. `0.8929 x` the maximum node radius
   of 14 world. In screen px this is exactly `12.5 x zoom`, with no floor and no crossover. The
   literal 12.5 is the old 1x floor length, reinterpreted as a world constant so that the
   approved 1x appearance is preserved exactly.
3. Effective head length, screen px: `L = min(12.5, a) x zoom`. The cap keeps a head inside the
   space its edge actually has, so it can never overshoot the far endpoint's boundary. Because
   both the rule and the cap are now proportional to zoom, the cap binds on exactly the same
   edges at every zoom: a single head is capped when `a < 12.5` world and a bidirectional pair
   when `d < 25` world.
4. Head half-base: `0.42 x L`, i.e. base `0.84 x L`. The aspect is always 0.84 : 1; when the cap
   shortens a head, the base shrinks with it. At an attachment distance of 6 world - the smallest
   space the cap can meet, and the pinned cap fixture `d = 6` - the capped base is 5.04 world with
   one head and 2.52 with two, i.e. 3.6x and 1.8x the 1.4-world line width. This is not the layout
   minimum: `NodeSeparationProjection.MIN_GAP = 6` is a surface gap between two nodes, so a node
   pair at that minimum has an attachment distance of at least `6 + 8 + 8 = 22` world and is
   never capped.
5. The path is built in world units: length `L / zoom`, half-base `0.42 x L / zoom`.
6. Head fill, gap between line and head, paint order and dimming: unchanged.

Resulting sizes at the reporter's workspace zooms (one-headed long edge, cap unbound):

| zoom | shipped head px | chosen head px | node diameter px | chosen ratio to the largest node |
| --- | --- | --- | --- | --- |
| 0.1485 | 12.5 | 1.86 | 2.4-4.2 | 0.45 |
| 0.378 | 12.5 | 4.73 | 6.0-10.6 | 0.45 |
| 0.75 | 12.5 | 9.38 | 12.0-21.0 | 0.45 |
| 1.0 | 12.5 | 12.5 | 16.0-28.0 | 0.45 |
| 2.798 | 24.3 | 35.0 | 44.8-78.3 | 0.45 |

At exactly `zoom = 1.0` the new rule draws the same 12.5 px head as the shipped rule. Below 1.0
heads become smaller; above 1.0 the screen size grows linearly with `zoom` (a capped head is
`a x zoom` px and an uncapped head `12.5 x zoom` px). There is no zoom clamp: `GraphViewport`
rejects only non-positive or non-finite zoom. The cap is a world-space bound on the requested
length, not a bound on the screen size.

## Architecture

The change is confined to `org.freeplane.plugin.graph.canvas.GraphPainter`.

- Two constants become one named constant replacing both obsolete literals:
  `ARROW_HEAD_WORLD_LENGTH = 12.5`, documented as `12.5 / 14.0 ~ 0.8929` of the maximum node
  radius in world units. `ARROW_HEAD_MINIMUM_LENGTH_PX` and `ARROW_HEAD_LENGTH_PER_ZOOM` are
  removed, not retained behind a fallback, per the repository's legacy-removal policy. The aspect
  constant `ARROW_HEAD_HALF_BASE_RATIO = 0.42` is unchanged.
- The package-private static helpers keep their names, units and roles, so the rule stays
  unit-testable without painting; the existing assertions that encode the floor and the 8.68
  growth term are invalidated and are replaced as described under Test strategy:
  - `static double arrowHeadLengthPx(double zoom)` -> `12.5 x zoom` (screen px).
  - `static double availableArrowHeadWorldLength(double attachmentDistanceWorld, boolean
    arrowAtFirst, boolean arrowAtSecond)` -> `arrowAtFirst && arrowAtSecond ? d / 2 : d`
    (world in, world out; unchanged).
  - `static double effectiveArrowHeadLengthPx(double zoom, double availableWorldLength)` ->
    `Math.min(arrowHeadLengthPx(zoom), availableWorldLength * zoom)` (screen px), which is
    IEEE-identical to `Math.min(12.5, availableWorldLength) x zoom` for positive zoom and keeps
    the composition in production.
  - `static double arrowHeadHalfBasePx(double lengthPx)` -> `0.42 x lengthPx` (screen px;
    unchanged).
- `paintArrow` keeps its current signature, the finite-length guard, the axis and perpendicular
  derivation, and the path construction; only the sizing call chain reads the new constant.
- `GraphTheme`, `DisplaySettings`, `CanvasTheme` and the workspace XML codec are untouched.
- There is one active rendering path: the max() floor rule is replaced, not kept alongside the
  proportional rule.

## Error handling and edge cases

- Zero-length or non-finite head direction: handled by the existing guard, which returns before
  any sizing or path work.
- Extreme zoom-out: heads become sub-pixel specks and are still drawn (1.86 px at `0.1485` for a
  maximum-radius node). This deliberately
  drops the previous design's "legible at every zoom level" goal: at overview zoom the `0.2` px
  edge lines and 2-4 px node discs are equally unreadable, and keeping heads legible there is
  what produced the reported clutter. Direction reads again as soon as the graph itself reads:
  the head passes 5 px at `zoom = 0.4`, 10 px at `0.8` and 12.5 px at `1.0`; the `0.4x` mark is a
  judgment call, not a measured threshold. There is no suppression threshold; a sub-pixel
  triangle is harmless and the rule stays branch-free.
- Extreme zoom-in: linear growth with no clamp; at `2.798` the head is 35.0 px against 78.3 px
  for the largest node, i.e. 0.45 of its diameter, the same ratio as at `1x`.
- Short edges: for `a` below the shipped growth term `8.68` world the effective length is
  unchanged at every zoom; between `8.68` and `12.5` world above the shipped `1.44` crossover the
  new rule keeps `a x zoom` instead of the shipped `8.68 x zoom`. For `a` below about `1.667`
  world units the capped base `0.84 x a` is narrower than the `1.4`-world line and the head reads
  as a small nub merged into the line; such an edge is far shorter than the target node
  separation. The crossover is on `a`, not on `d`.
- Node overlap at the tip: unchanged; the ring covers up to `0.7 x zoom` screen px.
- Enclosure endpoints: the tip can lie on a hull boundary rather than a node disc; head
  orientation and size depend only on the line direction, the zoom and `a`, so both endpoint
  kinds behave identically.
- Dimming: the head is painted under the existing `AlphaComposite 0.28f` exactly as today.
- Fractional zoom: the triangle path scales with the head length rather than being rasterised
  at a fixed size, so the head does not develop ragged edges.

## Test strategy

Extend `GraphCanvasPaintShould`; zoom variants use the existing `paintAtZoom(...)` helper and
pixel predicates keep the exact-RGB and near-colour conventions of the current tests.

1. **Rule (unit, no pixels).** `arrowHeadLengthPx` is `7.5` at 0.6, `4.725` at 0.378, `12.5` at
   1.0 and `37.5` at 3.0, and is strictly increasing in zoom; `arrowHeadHalfBasePx(12.5)` is
   `5.25`. The shipped floor values (12.5 at 0.6 and 1.44, 26.04 at 3.0) must not appear.
2. **Cap wiring (unit, no pixels).** `effectiveArrowHeadLengthPx(0.6, 100.0)` is `7.5` — this
   assertion fails on the shipped code, which returns the 12.5 floor; `(1.0, 100.0)` is `12.5`;
   `(0.6, 6.0)` is `3.5999999999999996`, compared with `within(1e-9)`; `(1.0, 3.0)` is `3.0`;
   `availableArrowHeadWorldLength(6.0, ...)` keeps its `6.0 / 3.0 / 6.0 / 6.0` assertions, so the
   bidirectional split still lives in exactly one testable place; the half-base of a capped
   length follows that length.
3. **Painted head length tracks the zoom rule.** Paint one directed edge with room at zoom 1.0
   and 3.0 and measure the head by its perpendicular width profile from the geometry attachment
   point; assert `12.5 +/- 1.5` px at 1.0 and `37.5 +/- 2.0` px at 3.0 (the shipped rule measures
   26.0 at 3.0 and fails). For the width threshold `max(3.0, 1.4 x zoom + 1.0)`, assert it is
   below `0.84 x` the *asserted* head length (10.5 px at 1.0, 31.5 px at 3.0); the existing
   `0.84 x 26.04` literal is replaced. The check that the profile just past the base falls below
   the threshold samples past the new base planes at `tipOne.x - 14` and `tipThree.x - 39`
   (just beyond 12.5 px and 37.5 px), replacing the old `tipThree.x - 28` sample, which would now
   lie inside the head where the run is about `0.84 x 28` px.
4. **Painted head shrinks when zoomed out.** Replace `paintedArrowHeadFloorHoldsAtLowZoom`: the
   measured head at 0.6 is `7.5 +/- 1.5` px, at 1.0 is `12.5 +/- 1.5` px, and the 0.6 measurement
   is strictly smaller than the 1.0 measurement. The shipped code measures 12.5 at both zooms
   and fails the strict comparison.
5. **Cap, base and bidirectional split at paint level.** The pinned enclosure-source fixture
   (`d = 6` world) at zoom 1.0 is unchanged: the rule value is 12.5 px at zoom 1.0 in both the
   old and the new rule, so the existing 6 px cap, 4 px base run and sub-threshold bidirectional
   assertions stay valid and continue to pin the call-site flag propagation. Add one capped paint
   run at a zoom above the shipped crossover, where the two rules diverge: an attachment distance
   of 10 world (the cap fixture with the target centre at world `x = 18`, tip at world `x = 10`)
   painted at zoom 3.0. The new rule draws `min(12.5, 10) x 3 = 30` px; the shipped rule draws
   `min(max(12.5, 8.68 x 3), 10 x 3) = 26.04` px, so a `30 +/- 1.5` px assertion fails on the
   shipped code and pins the capped path at a non-1.0 zoom.
6. **Theme fill.** The dark-theme interior sample at zoom 1.0 is unchanged, because the head
   length at 1.0 is unchanged; keep the existing sample offset.
7. **No regression.** `suppressAndRestoreDirectionalArrowheadsInThePaintedCanvas` and the paint
   tests not listed in items 1-6 keep passing unchanged.

## Acceptance evidence

- The mockup renderer and its renders, committed under
  `docs/superpowers/specs/mockups/2026-10-11-graph-arrowhead-zoom/`.
- The reporter's screenshot of the shipped defect, committed under
  `docs/superpowers/specs/images/2026-10-11-graph-arrowhead-zoom-reported.png`.
- Screenshots of the running application with the reporter's map (`Axiomatic_Set_Theory.mm`) in
  the light theme, captured through the repository's established headless/virtual-display
  evidence procedure, recorded with the integration revision and the build command:
  - a low-zoom capture reproducing the reported view. The exact zoom in the reported screenshot
    is not recorded, so the evidence seeds an isolated workspace copy whose viewport is the
    reported candidate `0.37809529736878794`, and the evidence records the actually applied zoom
    rather than assuming it;
  - a `zoom = 1.0` capture at the same centre, showing the pixel-identical-at-1x property.
- Where feasible, the "before" capture is taken from a copy of the shipped `BIN` before the
  change is built, at the same seeded viewports, so the acceptance record holds a same-scene
  pair.

## Design history

- Reported by the user on 2026-10-11 with a screenshot at an unrecorded low zoom; diagnosis
  located the 12.5 px screen floor as the cause of the low-zoom clutter.
- Rule chosen collaboratively from rendered mockups at the reporter's workspace zooms:
  treatment B (`12.5 x zoom`), preserving the approved 1x appearance; treatment A (`8.68 x zoom`)
  and a small-floor variant rejected.
- Frontier design review round 1 returned DEFECTS with 4 RISK and 10 NOTE findings, all folded
  in: the head : node ratio claim was scoped to the maximum-radius node, the test item 3
  measurement columns and nominal base were corrected, the workspace zoom provenance was
  corrected to the sync-conflict files and the screenshot measurements relabelled as derived,
  the acceptance-evidence mechanism was made explicit, the mockup fidelity and palette claims
  were narrowed, the cap and overpaint statements were corrected, the helper composition and the
  `0.8929` factor were corrected, and the reported-zoom uncertainty is recorded rather than
  assumed.
- The user re-confirmed treatment B on 2026-10-11 after reviewing the corrected mockup renders,
  with the small-floor variant declined.
- Frontier design review round 2 returned DEFECTS with 1 BLOCKER, 2 RISK and 4 NOTE findings: the
  committed mockups did not depict the documented rule because the renderer divided the screen
  length by zoom while drawing in screen space (fixed: the head is drawn at its screen length;
  the renders were regenerated and re-measured at 5.6/14.2/37.5 px for rule B at 0.1485/0.378/1.0
  and a constant 12.5 px for the shipped rule); the reported-zoom claim was unsupported and is now
  recorded as unknown; and the constant provenance, the stale renderer note, the 0.1485 ratio
  bound, the `MIN_GAP` conflation, and the missing non-1.0 capped paint probe were corrected.

## Rollout

No migration, no persisted state change, no user-visible setting. The change takes effect on the
next canvas paint; saved workspaces are unaffected.
