# Graph edge length and label connector design

Task identifier: `2026-10-23-graph-edge-length-and-label-connector`

Status: approved by the user on 2026-10-23. The user chose the spacing as a combination of
options B and D from the arrowhead comparison — option D's compact span (the shared gap stays
24 wu) with option B's head-to-shaft balance (a proportional head) — and connector option **B**
for the label indicator. Both choices are recorded with their mockups below.

Source and delivery target: `plugin/graph-workspace` at
`448c0d679d2b1d46563eaf079352e1eefb254305`. All investigation, mockup rendering and proposed
changes are isolated in the PM integration worktree; the delivery checkout was only read.

## Problem and goals

The user reports two defects in the Graph Workspace canvas:

1. "The default edge length still looks too short; only the arrow is visible. It is even shorter
   than the line used to indicate the node name."
2. "Please use a different style for the name/type indicator; otherwise, it looks like an edge."

The user clarified the second report with a cropped screenshot: the element under complaint is the
connector joining a label to its node disc — not the label text. In the screenshot the displaced
label above a disc is joined by a solid vertical stroke, and the labels placed to the right of
discs are joined by short solid horizontal strokes. Both read as relationship edges.

Goals:

- A relationship edge is visibly a line with an arrowhead at its end, never an arrowhead alone,
  at any gap, zoom, or node prominence.
- At zoom 1 and above, a relationship edge's drawn run is longer than the connector attached to
  either of its endpoints, so the two cannot be confused by length. The run is world-space
  (`gap × zoom`) while connector placement is screen-space and zoom-invariant at ≤ 27 px, so the
  inequality holds above roughly 0.92 zoom; below that the run shrinks with the world and this
  design accepts it.
- A label connector reads as an annotation, not as a graph relationship.
- The layout is not made to fight itself: relationship springs must never demand more room than
  the containment ring already provides.
- No change to hull sizes, node radii, prominence, label placement geometry, zoom policy,
  persistence, pins or projection semantics.

Non-goals: arrowhead shape, hull stroke weights, label font ladder, connector *geometry*, and any
change to enclosure label styling.

## Reproduced causes

All values below are read from the delivery base at `448c0d679d`, and layout units are model
coordinates.

**Cause 1 — the relationship rest length is a collision clearance, not a preferred length.**
`GraphStreamLayoutEngine.topology` builds each relationship spring as

```text
restLength = radius(first) + radius(second) + NodeSeparationProjection.MIN_GAP
```

with `MIN_GAP = 6.0` and `NODE_RADIUS = 8.0`. For two default nodes that is `8 + 8 + 6 = 22`
centre-to-centre, so the drawn line between the two disc rims is exactly **6 wu**. The earlier
`graph-initial-spacing` work raised `PREFERRED_NODE_GAP` to 24.0, but that constant feeds only
`BoundarySizes.contentRingRadius` for enclosure rings; relationship springs still use `MIN_GAP`.

**Cause 2 — the arrowhead is a fixed 12.5 wu and is clipped to the run.**
`GraphPainter.paintArrow` computes `effectiveArrowHeadLengthPx(zoom, available)` as
`min(ARROW_HEAD_WORLD_LENGTH * zoom, available * zoom)`. With `ARROW_HEAD_WORLD_LENGTH = 12.5` and
a 6 wu run, the head is clipped to 6 wu and **fills the entire edge** — the arrow *is* the line.
At the reported screenshot's zoom (disc diameter 26 px, so zoom ≈ 1.63) that is a 9.8 px run and a
9.8 px head. The head base is `0.42 × length × 2 = 10.5` wu against a 16 wu disc: **66% of the
disc diameter**, which is why the head reads as the dominant element even when a shaft exists.

**Cause 3 — the label connector is drawn with the relationship stroke.**
`GraphPainter.paintLabels` draws the leader line with `theme.labelColor()` and
`leaderStroke(theme, zoom)`, whose width is `edgeStroke.getLineWidth() / zoom` — the same 1.4 px
solid stroke, same cap and same join as a relationship edge, in a near-identical colour. Light
theme: `labelColor = (31, 38, 48)` versus `edgeColor = (73, 84, 100)`. Nothing but colour
distinguishes "this stroke points at a label" from "this stroke is a relationship".

Measured consequence at the reported zoom: the edge run is 9.8 px while the connector above it is
27 px, which is exactly the user's "shorter than the line used to indicate the node name".

## Visual options and approved choices

The mockups below are rendered from the real pipeline — `GraphGeometryEngine` → `EdgeRouter` →
`GraphPainter` — compiled against the delivery base with the candidate constants passed in. Two
fidelity controls were asserted: when the current style is selected, the connector panel is
pixel-identical to production's own leader rendering (**0 differing pixels**), and when the fixed
12.5 wu head is selected the arrow panel is pixel-identical to production's own head (**0 differing
pixels**). Only the decision under test varies between panels.

### Edge length options (background)

![Relationship edge length options](mockups/2026-10-23-graph-edge-length-and-label-connector/gap-options.png)

Disc-rim to disc-rim gaps of 6 (current), 24, 40 and 56 wu with today's fixed head. This set
established the constraint that decided the outcome: the gap is not a free parameter. An enclosure
ring places adjacent members `2·maxR + G` apart, so raising `G` multiplies hull size, and
`GraphCanvas.fitGraph()` then divides the zoom by the larger world span — the discs shrink on
screen by the same factor. Gap 24 is the only value that needs no hull growth at all, because it
is already `PREFERRED_NODE_GAP`.

### Approved spacing — gap 24 with a proportional head

![Approved spacing](mockups/2026-10-23-graph-edge-length-and-label-connector/spacing-approved.png)

The user asked to combine option B's balance with option D's span, where B was "gap 56 with a 9 wu
head" and D was "gap 24 with a 9 wu head" in the preceding arrowhead comparison. Because the gap
multiplies the hulls while the head-to-shaft ratio does not, the combination is reached on the head
dial. Panel letters below are local to this image and do not refer back to that comparison:

- **A** — gap 24, fixed 9 wu head (option D as first shown).
- **B** — gap 24, head = 30% of the run = 7.2 wu. **Approved.**
- **C** — gap 40, head = 12 wu; the same ratio at a larger span.
- **D** — gap 56, head = 12.5 wu; the ratio rule reaches today's cap, so the head never grows.

The approved point keeps `PREFERRED_NODE_GAP` at 24.0, so hull size, fit zoom and disc size are
unchanged from the delivery base, while the relationship edge is lengthened 22 → 40 wu
centre-to-centre and the head drops from 65.6% to 37.8% of the disc diameter. Panel B of this image
is the approved spacing; it is the combination of the earlier B and D, not either of them alone.

### Approved label connector

![Label connector options](mockups/2026-10-23-graph-edge-length-and-label-connector/connector-options.png)

Rendered at the approved spacing. **Option B — dotted, `mutedColor`, 1.0 px — approved.**

Option A is today's behaviour: a continuous stroke in `labelColor` at the relationship weight, the
line that reads as an edge. Option C (hairline plus an endpoint dot) is a viable second choice.
Option D (a plate behind the text) was rejected because the plate fills with `theme.background()`,
which would punch a canvas-coloured hole through an enclosure fill; making it correct there needs a
hull-tinted fill and more design than this change warrants.

## Approved architecture and exact change set

The approved design touches three production sites and adds no new class. Both defects are fixed
by construction rather than by tuning: the relationship spring is bounded by the containment ring,
and the arrowhead is bounded by the run it sits on.

### C1 — relationship springs use the preferred gap, bounded by the ring

In `layout.graphstream.GraphStreamLayoutEngine.topology`, the relationship `ForceLink` rest length
becomes:

```text
gap  = (edge.first is a node AND edge.second is a node) ? PREFERRED_NODE_GAP : NodeSeparationProjection.MIN_GAP
rest = radius(first) + radius(second) + gap
```

`PREFERRED_NODE_GAP` stays `24.0`. `NodeSeparationProjection.MIN_GAP` stays `6.0` and remains the
minimum clearance used by `NodeSeparationProjection`, `EdgeConflictCorrection` and the
hull-endpoint rest length. Containment and hierarchy rest lengths are untouched.

Why enclosure endpoints keep `MIN_GAP`: a hull endpoint's "radius" is already its content ring,
which encodes the preferred spacing. Adding the preferred gap there would place the node a further
24 wu outside its own ring, so the relationship spring would pull against the containment spring
that holds it on the ring. Keeping the additive gap node-only avoids double counting.

Why this cannot fight the ring: for two direct members of one enclosure, each radius is at most
`maxR`, so `rest = r₁ + r₂ + G ≤ 2·maxR + G`, and `2·maxR + G` is exactly the chord between
adjacent ring members, by construction of `contentRingRadius`. A relationship spring therefore can
never demand more room than the ring already provides. This is a property to assert, not a tuning
result.

### C2 — proportional arrowhead

In `canvas.GraphPainter`:

```text
ARROW_HEAD_RATIO              = 0.30
MIN_ARROW_HEAD_WORLD_LENGTH   = 4.0
MAX_ARROW_HEAD_WORLD_LENGTH   = 12.5      (replaces ARROW_HEAD_WORLD_LENGTH)
ARROW_HEAD_HALF_BASE_RATIO    = 0.42      (unchanged)
```

`effectiveArrowHeadLengthPx(zoom, available)` becomes
`min(clamp(0.30 · available, 4.0, 12.5), available) · zoom`, keeping the existing clipping by the
terminal segment. The `arrowAtFirst && arrowAtSecond` halving of `available` is unchanged, as is the
bow handling; `paintArrow` keeps using the same unit vectors and half-base ratio.

Consequences: a 24 wu run gets a 7.2 wu head (30% of the run, base 6.05 wu = 37.8% of the disc);
any run of 41.67 wu or more keeps today's 12.5 wu head, so long edges are pixel-identical to the
base; a run below 13.33 wu gets the 4.0 wu floor, which guarantees a visible shaft even in the
degenerate case that produced this report.

### C3 — dotted, muted label connector

In `canvas.GraphPainter`:

- `paintLabels` draws the leader with `theme.mutedColor()`; the label text keeps
  `theme.labelColor()`.
- `leaderStroke` becomes `leaderStroke(zoom)` — the theme parameter is removed because the colour
  is now chosen at the call site — and returns
  `new BasicStroke(1.0/zoom, CAP_ROUND, JOIN_ROUND, 10.0, {1.2/zoom, 2.8/zoom}, 0.0)`.

Dividing width and dash lengths by `zoom` keeps the connector a 1.0 px hairline with a 4.0 px dash
pitch at every zoom, exactly as the current stroke keeps a constant screen width. Leader *geometry*
(`ScreenLabelPlacement`, `LeaderLine`) is untouched, so hit-testing, retained placement and the
existing geometry tests are unaffected.

Light theme `mutedColor = (120, 130, 142)` against `labelColor = (31, 38, 48)`; dark theme
`mutedColor = (151, 163, 178)` against `labelColor = (240, 244, 248)`. The connector therefore loses
both the edge's colour and the text's weight, which is what makes it read as an annotation.

## Invariants

| Id | Invariant |
| --- | --- |
| I1 | Every relationship link whose endpoints are both nodes has `restLength = r₁ + r₂ + 24.0`, where `r` is the disc radius from `radiusById`. |
| I2 | Every relationship link with at least one enclosure endpoint has the same `restLength` as the delivery base. |
| I3 | For any two direct members of one enclosure, the relationship `restLength` is `≤ 2·maxR + 24.0`, the ring chord. |
| I4 | Containment and hierarchy rest lengths are unchanged. |
| I5 | For every painted arrowhead, `length ≤ min(12.5, availableRun)` at every zoom, and `length ≥ min(4.0, availableRun)`. |
| I6 | An edge with `availableRun ≥ 41.67` wu paints the same head as the delivery base. |
| I7 | Head half-base remains `0.42 × length`. |
| I8 | The leader stroke paints 1.0 screen px wide with a 4.0 screen px dash pitch at every zoom. |
| I9 | Label text is painted in `theme.labelColor()`; the leader is painted in `theme.mutedColor()`. |
| I10 | `ScreenLabelPlacement` leader geometry is unchanged. |

## Test strategy

Production-scale unit tests, in the existing modules and naming style:

1. `GraphStreamLayoutEngine` — I1, I2, I3, I4. Fixtures: two default nodes; a mixed-prominence
   pair; a pair inside a four-node enclosure comparing `restLength` against
   `BoundarySizes.contentRingRadius`-derived chord; the existing cross-map fixture, which already
   contains enclosure endpoints, asserting those links are unchanged (I2).
2. `GraphPainter` — I5, I6, I7 over run lengths `{3, 6, 13.33, 24, 41.67, 80}` at zooms
   `{0.25, 1, 1.63, 4}`. Assert the head geometry in world units, not device pixels, plus one
   device-pixel probe at zoom 4 proving the fold into `paintArrow`.
3. `GraphPainter` — I8, I9, I10. A pixel probe over the leader segment asserting `mutedColor`-near
   ink with gaps (the dotted pattern must produce both ink and non-ink samples along the segment)
   and `labelColor`-near ink in the text box; a geometry assertion that `PlacedLabel.leader()` is
   unchanged.

Existing tests that must change, and why:

| Test | Change |
| --- | --- |
| `GraphCanvasPaintShould.keepsTheLeaderStrokeScreenConstant` | Assert 1.0 screen px and a `{1.2, 2.8}` dash array after `× zoom`, instead of equality with the 1.4 px edge stroke. |
| `GraphCanvasPaintShould.paintsTheLeaderStrokeScreenConstantThroughTheProductionCallSite` | Detect leader ink with `mutedColor`, and count ink across the whole segment rather than in one column, because a single column can land in a dash gap. |
| `InitialSpacingConsumers.topology` | Node-to-node links assert `first + second + 24.0`; enclosure-endpoint links keep `+ 6.0`. |
| `InitialSpacingSeedShould` | Any relationship-rest assertion moves to the new gap. Its ring assertions stay at 24.0. |

The `graph-initial-spacing` acceptance gate is deliberately untouched: `PREFERRED_NODE_GAP` stays
24.0, so its 24.0 seed-gap expectations, `BoundaryFootprintShould`'s `contentRingRadius == 20.0`
and the archived spacing receipts remain valid. This is the concrete reason the approved spacing
was preferred over the 40 and 56 wu options.

Verification gates: the full `freeplane_plugin_graph` suite, then the existing edge-conflict and
2000–5000-node performance gates, because relationship springs move node equilibrium positions even
though no layout constant changed.

## Risks

1. **Settled geometry moves.** Rest length 22 → 40 changes where unpinned nodes come to rest, so
   recorded settle snapshots, idle-step counts and conflict counts may change. Mitigation: the
   existing deterministic fixtures and the performance gates are the tripwire; the change moves
   the relationship rest *toward* the ring chord, so containment/relationship disagreement is
   reduced rather than increased.
2. **Longer paths cost more routing work.** Longer edges cross more cells and can raise
   edge–node and edge–edge checks per frame. Mitigation: measure with the existing gates; no new
   budget is introduced by this design.
3. **Thin dotted connectors on dense maps.** A 1.0 px muted dotted stroke is lighter than today's
   solid line. Mitigation: the connector is screen-constant, so it stays 1.0 px at every zoom, and
   option C (hairline with an endpoint dot) remains available if the dotted form proves too light
   in practice.

## Out of scope

Arrowhead shape or direction; the multiplicity cue; hull stroke weights and tiers; the label font
ladder and truncation; connector geometry, clearance and slot selection; node radius, prominence
and separation; persistence and workspace XML; pins.
