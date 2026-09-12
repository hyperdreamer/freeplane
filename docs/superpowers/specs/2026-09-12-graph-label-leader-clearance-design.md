# Graph Workspace label / leader-line clearance — Design

- Date: 2026-09-12 (revision 1)
- Review status: **pending design review** (attempt 1 not yet run)
- Topic: `graph-label-leader-clearance`
- PM run: `pm-run-20260912-212024-c7dfa6db`
- Delivery target: `refs/heads/plugin/graph-workspace` at `/data/home/guest/Development/freeplane`
- Integration branch: `pm/graph-label-leader-clearance/run-pm-run-20260912-212024-c7dfa6db`
- User request (verbatim): *"The node name should not overlap with the leader line pointing to it."*
- User decision: leader rule **option A** (keep every leader, trim it clear of the name's box) with the
  gap value left to this design inside the offered 1–3 px range. This document pins **3.0 screen px**;
  §4.4 derives the value from measurement.
- Evidence: `docs/superpowers/specs/mockups/2026-09-12-label-leader-clearance/`
  - `label-leader-clearance.png` — four panels (today, A, B, C), production-scale fixture plus two 2x junctions
  - `LabelLeaderClearanceMockups.java` — generator, reproduces the PNG and mirrors the pinned rule
  - `LeaderClearanceProbe.java` / `leader-clearance-probe.txt` — the measurements quoted in §5 and §6

## 0. Normative conventions

- "Screen space" is the coordinate system of `GraphCanvas.paintComponent`'s `Graphics2D` **before**
  `GraphPainter.worldTransform`; "world space" is the layout coordinate system. Label rectangles, slot
  offsets, `SLOT_GAP`, `DISPLACED_OFFSET` and this design's clearance are all screen space.
- "Leader" means the straight segment a placed label carries from its anchor source to its name.
- "The name's box" (the *label rectangle*) is `PlacedLabel.bounds()`: width and height from
  `font.getStringBounds(text, SCREEN_FRC)` (`ScreenLabelPlacement.screenBounds`), centred on
  `(anchorX, anchorY)` (`PlacedLabel.java:102-104`).
- "Painted ink" is a rasterized `alpha != 0` pixel at 1:1 device scale, as the existing ink harness
  measures it (`ScreenLabelPlacementShould.inkMask`, `:1413`).
- `inflate(r, g)` is the rectangle with `minX-g, minY-g, maxX+g, maxY+g`; `distance(p, rect)` is the
  Euclidean distance from a point to that rectangle, `0` on and inside its boundary.

## 1. Problem

### 1.1 The reported defect

The user reported that a node's name is crossed by the leader line that points at it. The visible
symptom is a thin stroke running through the word, e.g. `Axiom of Choice` and `Replacement Scheme` in the
reported screenshot. The stroke is *painted first and the name second*
(`GraphPainter.paintLabels`, `GraphPainter.java:247-252`), so it shows **through the gaps between
glyphs** — which is why it reads as a strikethrough rather than as a covered line. The leader is painted
in `theme.labelColor()` (`GraphPainter.java:243-252`), the same colour as the glyphs, so where it does
emerge it is indistinguishable from text ink.

### 1.2 Mechanism

1. `ScreenLabelPlacement.leaderStart(slot, centerX, centerY, radius, anchorX, anchorY)`
   (`ScreenLabelPlacement.java:555-563`) returns the **disc-rim point on the centre → anchor ray**.
2. `GraphPainter.paintLabels` draws `Line2D(leaderStart, (anchorX, anchorY))` (`:247-251`).
3. `anchorX/anchorY` is the **centre of the label rectangle** by construction
   (`PlacedLabel.bounds()` = `anchor ± width/2, height/2`, `PlacedLabel.java:102-104`).

The segment therefore always ends *inside* its own name's box: it enters the box at the face the ray
crosses and continues to the centre. The rule holds for every slot except `ABOVE`/`BELOW` (which carry no
leader, spec C12 in `ScreenLabelPlacement.java:552-554`) and on **every** placement path that can carry a
leader: the ladder (`:512`), the retained/reused placement (`:467`) and enclosure-external labels
(`:298-305`, whose start is the hull's nearest boundary point). It is systematic, not fixture-specific.

### 1.3 Why a placement-level fix is not enough by itself

The stroke is applied under the world transform (`GraphPainter.paint` → `copy.transform(worldTransform)`
→ `scale(zoom)`, `GraphPainter.java:65, 83-89`) and is *not* zoom-compensated, while the label font two
lines above the leader *is* (`deriveFont(size / zoom)`, `GraphPainter.java:240-241`). The leader's painted
screen width is therefore `1.4 · zoom` (`theme.edgeStroke()` = `BasicStroke(1.4, CAP_ROUND, JOIN_ROUND)`,
`GraphTheme.java:59`). A screen-space clearance constant can only bound the painted result if the painted
stroke weight is screen-constant too — §5.3 quantifies this.

## 2. Goals

- **G1** A name is never touched by the leader that points at it, at every zoom the app can reach.
- **G2** The leader keeps its information and its existing existence rule: it still leaves the disc rim
  (or the hull boundary) and it still exists for every slot except `ABOVE`/`BELOW`.
- **G3** The invariant is falsifiable by a deterministic, non-visual test, with a red phase that fails on
  today's code for the *right* reason.
- **G4** Placement is otherwise untouched: no change to slot selection, obstacle bookkeeping, the
  truncation ladder, hull geometry, hit-testing or label visibility levels.

## 3. Non-goals

- A leader crossing a label it does not belong to, or an edge crossing a label (out of scope; only *its
  own* name is in scope).
- Label-to-label overlap, disc-to-disc separation, hull contents — pinned by the previous
  `graph-node-separation` run and deliberately not re-opened.
- Minimising leader-leader crossings beyond keeping the existing zero-crossing assertions green.
- Changing **which** slots carry a leader (that was option C; see §4.3) and changing the anchor, the slot
  offsets, `SLOT_GAP`, `DISPLACED_OFFSET`, truncation or the obstacle model.
- HiDPI/device-scale-factor changes (the relationship between the two ink regions is scale-invariant,
  §11 R4).

## 4. Decision

### 4.1 Chosen rule: option A

Every leader keeps its start and is **trimmed so that it stops clear of the name's box**: the end becomes
the point where the ray `[start → anchor]` crosses `inflate(rect, LEADER_CLEARANCE)`. The user chose this
from the three mocked-up rules.

### 4.2 Why not option B (stop exactly on the box edge)

Measured (§6): leader ink reaches **1.5 px beyond its geometric end** at every zoom once the stroke is
screen-constant (0.7 px half-stroke with a `CAP_ROUND` cap plus antialiasing), and glyph ink reaches to
**within 0.03 px of the box's left edge** and can overhang the right edge by **0.26 px**. A zero
clearance therefore puts painted leader ink inside the box (probe: 4 ink pixel centres inside the box at
zoom 1, 8 at zoom 4) and, since the leader shares the glyph colour, a flush leader also reads as welded to
the first glyph. B is not merely less pretty: it does not satisfy G1.

### 4.3 Why not option C (drop the leader for adjacent slots)

C removes the rim → name cue that the previous run deliberately introduced for *all* non-`ABOVE`/`BELOW`
slots (C12), and on its own it does **not** fix the reported defect: displaced (`*_FAR`) leaders and
enclosure-external leaders would still end at their label's centre. Keeping C would have required A's trim
anyway for those, so C is strictly a superset of A's behaviour change with an additional contract change.

### 4.4 The clearance value: `LEADER_CLEARANCE = 3.0` screen px

Derived from three measured quantities (§6), all in device px at 1:1:

| quantity | measured |
|---|---|
| leader ink beyond the geometric end (screen-constant stroke) | `1.5` |
| worst glyph ink approach on the leader-facing side (overhang beyond the box) | `0.26` |
| one device pixel pitch (two ink regions must not share a raster pixel) | `1.0` |
| **required clearance** | `> 2.76` → pin `3.0` |

`2.0` would leave only `0.24–0.5` px between the two painted ink regions — the ink pixel *centres* stay
apart, but a sub-pixel separation does not guarantee distinct pixels for every coordinate phase, so the
painted claim of §5.2 could not be asserted safely. `3.0` gives a `1.24–1.5` px painted separation with
the same visual result (the difference between a 2 px and a 3 px gap at the mockup's junctions is
imperceptible). The value stays inside the 1–3 px range offered to the user with option A.

## 5. Contract

### 5.1 L1 — leader geometry (screen space)

For every placed label that carries a leader, with `S` = start, `C` = anchor (= rect centre) and
`R` = the label rectangle:

1. `end` is the first intersection of the ray `S → C` with `inflate(R, LEADER_CLEARANCE)`, and
   `S, end, C` are collinear in that order with `end ≠ S`.
2. `distance(end, R) ≥ LEADER_CLEARANCE`, with equality when the ray enters through a face; a corner
   entry (diagonal slots) reaches up to `LEADER_CLEARANCE · √2`.
3. The segment `[S, end]` does not intersect `inflate(R, LEADER_CLEARANCE - ε)` for any `ε > 0`; in
   particular it never touches `R`.
4. `S` is unchanged: the disc-rim point on `C - centre` for node labels; the hull's nearest boundary
   point for enclosure-external labels. `C` is unchanged.
5. If the ray's entry point into `inflate(R, LEADER_CLEARANCE)` does not lie strictly between `S` and
   `C`, the label carries **no** leader (a leader shorter than the clearance is not drawn at all).

### 5.2 L2 — painted ink separation

Within one paint pass, for every visible label, the leader's painted ink and that label's own painted
glyph ink are **disjoint rasters**: no pixel receives coverage from both. Formally, with `leaderMask` and
`inkMask` produced by the production stroke/font rules (§9.2), `overlaps(leaderMask, inkMask)` is false,
and no leader ink pixel centre lies inside `R`.

### 5.3 L3 — zoom invariance

`LEADER_CLEARANCE` is a screen-space constant, and the leader's **painted stroke weight is screen-constant
too**: the stroke used for leaders is `new BasicStroke(edgeWidth / zoom, CAP_ROUND, JOIN_ROUND)`, matching
what `paintLabels` already does for the font (`GraphPainter.java:240-241`). Without this, the painted
stroke width is `1.4 · zoom` and the contract of §5.2 breaks above zoom ≈ 1.8 (probe: at zoom 4 the ink
extends 3.5 px past the end and 8 ink pixel centres land inside the box for a 2 px clearance; 3 px still
fails there).

### 5.4 L4 — existence, unchanged in substance

A leader exists for every slot except `ABOVE` and `BELOW`, and additionally only when §5.1.5 holds.
Hover-only labels carry no leader (unchanged). `forcedAtBaseSlot` labels use `ABOVE` (unchanged).

### 5.5 L5 — no other behaviour change

`PlacedLabel` keeps every other accessor and invariant. `leaderCrossings(...)` measured on the trimmed
segment does not increase; the existing zero-crossing fixtures must stay zero.

## 6. Measured evidence

Probe: `LeaderClearanceProbe.java`, output `leader-clearance-probe.txt`, Java 21.0.8 / this host,
`KEY_ANTIALIASING=ON`, `KEY_RENDERING=VALUE_RENDER_SPEED` (the hint `GraphPainter` sets).

Leader ink reaching past its geometric end, and ink pixel centres inside the box for a given clearance
(`strokeMode 1.4` = today's stroke, `1.4/zoom` = §5.3):

| zoom | stroke | painted px | ink past end | clearance 0 | clearance 1 | clearance 2 | clearance 3 |
|---|---|---|---|---|---|---|---|
| 0.25 | 1.4 | 1.0 | 0.5 | 1 inside | 0 | 0 | 0 |
| 0.25 | 1.4/zoom | 3.0 | 1.5 | 4 inside | 1 inside | 0 | 0 |
| 1.00 | 1.4 | 3.0 | 1.5 | 4 inside | 1 inside | 0 | 0 |
| 1.00 | 1.4/zoom | 3.0 | 1.5 | 4 inside | 1 inside | 0 | 0 |
| 2.00 | 1.4 | 3.0 | 1.5 | 6 inside | 3 inside | 0 | 0 |
| 2.00 | 1.4/zoom | 3.0 | 1.5 | 4 inside | 1 inside | 0 | 0 |
| 4.00 | 1.4 | 7.0 | 3.5 | 22 inside | 15 inside | 8 inside | 3 inside |
| 4.00 | 1.4/zoom | 3.0 | 1.5 | 4 inside | 1 inside | 0 | 0 |

Glyph ink overhang beyond the logical box, `GraphPainter.drawCentered` centring, `Font.DIALOG` 12 pt,
negative = ink starts inside the box (per side, worst of the fixture texts `Axiom of Choice`, `Theorem`,
`Power Set`, `Replacement Scheme`, `Foundation / Regularity`, `Extensionality`, `ZFC`, `Axioms`,
`Basic Definitions and Theorems`, `Well-Ordering Theorem of Choice and Regularity`, `Group`,
`Transfinite Induction over Ordinal Numbers`, `Zermelo`):

| side a leader can approach from | worst measured | text |
|---|---|---|
| left (RIGHT slot) | `-0.0302` (0.03 px *inside*) | `Theorem` |
| right (LEFT slot) | `+0.2578` (0.26 px *outside*) | `Power Set` |
| top / bottom (ABOVE_\*/BELOW_\* slots) | `-0.6721` (0.67 px inside) | `Replacement Scheme` |

**Consequence for this design:** the box edge is not a safe stopping line (glyph ink can overhang it by
0.26 px), the leader's own painted end is not a safe stopping line (its ink extends 1.5 px past it), and a
sub-pixel gap cannot guarantee pixel-level separation. Both facts are why §4.2 rejects B and §4.4 pins 3 px.

## 7. Architecture

### 7.1 New value type `LeaderLine` (canvas package, **public**, immutable)

```java
public final class LeaderLine {
    public LayoutPoint start();
    public LayoutPoint end();
}
```

- Constructor is package-private and rejects: `null` points, non-finite coordinates, `start.equals(end)`.
- It carries no painting information; the stroke remains the theme's.

### 7.2 `PlacedLabel` — one optional field replaces one optional field

`Optional<LayoutPoint> leaderStart` (`PlacedLabel.java:35, 42, 62, 126-128`) becomes
`Optional<LeaderLine> leader` with `public Optional<LeaderLine> leader()`. `leaderStart()` is **deleted**
(no compatibility shim, per the repository's legacy-removal policy). Modelling start and end as one value
makes "start present, end absent" unrepresentable; a second `Optional` accessor would have required an
invariant that two fields agree.

### 7.3 `ScreenLabelPlacement` — the trim

- New constant: `static final double LEADER_CLEARANCE = 3.0;` next to `SLOT_GAP`/`DISPLACED_OFFSET`
  (`ScreenLabelPlacement.java:28-31`).
- `private static Optional<LayoutPoint> leaderStart(Slot, …)` (`:555-563`) is **replaced** by
  `private static Optional<LeaderLine> leader(Slot slot, double centerX, double centerY, double radius,
  double anchorX, double anchorY, double width, double height)`:
  1. `ABOVE`/`BELOW` → `Optional.empty()`.
  2. `S` = the existing rim point computation (unchanged arithmetic, including the `1e-6` guard).
  3. `E` = first intersection of the ray `S → C` with `inflate(R, LEADER_CLEARANCE)`, computed by the
     slab method; `Optional.empty()` when `E` is absent or not strictly between `S` and `C`.
  4. `Optional.of(new LeaderLine(S, E))`.
- Call sites updated: `retained(...)` (`:467`), `ladder(...)` (`:512`), enclosure-external
  (`:298-305`). Those three are the only leader-producing paths (`baseSlot` and `hoverOnly` use `ABOVE`
  and produce none).
- A retained or laddered label whose leader would be empty still keeps its label and simply carries no
  leader: the leader is optional and leads must not change which labels are placed.

### 7.4 `GraphPainter.paintLabels` — end point and screen-constant stroke

```java
graphics.setStroke(leaderStroke(theme, viewport.zoom()));   // once, before the label loop
...
leader.ifPresent(line -> graphics.draw(new Line2D.Double(
    worldX(line.start().x(), …), worldY(line.start().y(), …),
    worldX(line.end().x(),  …), worldY(line.end().y(),  …))));
```

- New package-private seam `static Stroke leaderStroke(GraphTheme theme, double zoom)` returning
  `new BasicStroke((float) (theme.edgeStroke().getLineWidth() / zoom),
  BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)`. The seam exists so the painted-ink test paints with the
  **production** stroke instead of a mirror of it (§9.2), and so §5.3 is unit-testable.
- The label font keeps its existing `deriveFont(size / zoom)`; only the leader stroke gains the
  compensation.

### 7.5 Deleted code

`PlacedLabel.leaderStart()`, `ScreenLabelPlacement.leaderStart(...)`, and every test that reads
`leaderStart()` — replaced by `leader()` (start and end). No parallel old/new path is retained.

## 8. Error handling and degenerate cases

| case | behaviour |
|---|---|
| `ABOVE` / `BELOW` slot | no leader (unchanged, L4) |
| hover-only label | no leader (unchanged; the base slot is `ABOVE`) |
| `S` already inside `inflate(R, LEADER_CLEARANCE)` (very short leader) | no leader; the label is still placed |
| `anchor == centre` (impossible by slot construction: anchor distance ≥ `radius + SLOT_GAP`) | guarded by the existing `Math.max(1e-6, hypot)` |
| non-finite inputs | `IllegalArgumentException` from the existing placement/`PlacedLabel` validation; unchanged |
| retained label after pan/zoom | both endpoints recomputed from the retained slot (as `leaderStart` is today, `:467`) |
| enclosure-external label | same rule; the start stays the hull's nearest boundary point |
| zoom change while the placement is cached | the cache key already contains the zoom (`ScreenLabelPlacementShould.recomputesTheLeaderStartOfRetainedLabelsAfterAZoomChange`) and the stroke is recomputed per paint pass |

## 9. Test strategy

### 9.1 Red phase 1 — geometry (`ScreenLabelPlacementShould`)

`keepsEveryLeaderClearOfItsOwnLabelBox`: over the existing dense and long fixtures, at zoom 0.25 / 1 / 2,
for every visible non-hover label with a leader assert (a) `end` lies on `[S, C]` between them,
(b) `distance(end, R) ≥ LEADER_CLEARANCE - 1e-9`, (c) `[S, end] ∩ inflate(R, LEADER_CLEARANCE - 1e-6)`
is empty, (d) `[S, end] ∩ R` is empty. **Fails on today's code** because `end == C` gives
`distance(end, R) == 0` (the current code has no `end` at all; the test is written against `leader()`).

### 9.2 Red phase 2 — painted ink (`ScreenLabelPlacementShould`)

`keepsLeaderInkOutOfItsOwnGlyphInk`: reuse the existing `inkMask(label, width, height)` (`:1413`) for the
glyph ink, add `leaderMask(label, theme, zoom, width, height)` that paints **only** the leader with
`GraphPainter.leaderStroke(theme, zoom)` under a `scale(zoom)` transform (mirroring
`GraphPainter.worldTransform`), then assert for every visible label: `overlaps(leaderMask, inkMask)` is
false, and no `leaderMask` pixel centre lies inside `R`. Run at zoom 0.25 / 1 / 2 / 4 so the same test
fails twice for two different reasons: on today's code at every zoom (leader reaches the centre), and for
a non-compensated stroke at zoom 4 (§5.3).

### 9.3 Compensation unit test (`GraphCanvasPaintShould`)

`keepsTheLeaderStrokeScreenConstant`: for zoom 0.25 / 1 / 2 / 4 assert
`GraphPainter.leaderStroke(theme, zoom).getLineWidth() * zoom == theme.edgeStroke().getLineWidth()`
(within 1e-6). This is the falsifiable form of §5.3, independent of the ink harness.

### 9.4 Updated helpers and existing expectations

- `leaderCrossings(placed)` (`:1196-1215`) measures `leader().start() → leader().end()`: it must describe
  the painted segment, not the old centre-directed one. Values stay 0 on the fixtures.
- `assertNodeLeadersAtTheRim` (`:281-310`) and `assertRetainedNodeLeadersAtTheRim` (`:312-341`) keep
  their rim/collinearity/forward assertions on `leader().start()` and gain the §5.1 assertions on
  `leader().end()`.
- `leader(label, scene, zoom)` (`:1306-1315`) measures centre → anchor and is used only as an upper bound
  against `leaderForm(...)`; the trimmed segment is shorter, so those assertions and the pinned
  `meanLeader`/`maxLeader` expectations (`:256-257`, `:271`, `:675-694`) are unchanged. This must be
  confirmed by running them, not asserted by reasoning.
- `ScreenLabelPlacementShould` lines 260-272, 290-298, 326-332, 489-494, 510-514, 770-825, 856, 891-898
  read `leaderStart()`; each becomes a `leader()` read (start-only assertions keep their pinned values).

### 9.5 Regression

`gradle :freeplane_plugin_graph:test` (whole plugin suite, including `GraphCanvasPaintShould` and
`GraphWorkspaceModelAcceptanceShould`) plus the full `gradle test` before the implementation audit.

## 10. Mockups

`docs/superpowers/specs/mockups/2026-09-12-label-leader-clearance/label-leader-clearance.png`
(1356x1338, committed with its generator):

- **Panel 1 — Today**: the leader reaches the name's centre; the red dashed box marks the name and the red
  segment marks the part of the stroke that crosses it. Both 2x junctions show the stroke inside
  `Axiom of Choice` and `Replacement Scheme`.
- **Panel 2 — Option A (chosen)**: the leader ends on the box inflated by 3 px; both junctions are clear.
- **Panel 3 — Option B (rejected, kept for the record)**: the leader ends on the bare box; the stroke
  touches the box outline and, per §6, puts painted ink inside it.
- **Panel 4 — Option C (rejected, kept for the record)**: adjacent slots carry no leader; the displaced
  leader is trimmed like A.

The generator uses the production `slotAnchor` arithmetic and the production 12 pt measurement, and
records `LEADER_CLEARANCE = 3.0` in one constant, so the mockup and §5.1 cannot drift apart. The panel
fixture also shows an `ABOVE` label with no leader and an enclosure-external `ZFC` label, which is trimmed
by the same rule.

## 11. Risks

- **R1 — near-slot stubs.** A `RIGHT`/`LEFT` leader becomes `6 - 3 = 3` px long, a diagonal near-slot
  leader ≈ 5.5 px. Short but visible; option C existed to remove them and the user rejected it. Recorded,
  not mitigated.
- **R2 — leader weight changes at zoom ≠ 1.** Today the leader is `1.4·zoom` screen px; after §5.3 it is a
  constant `1.4` screen px (thinner at high zoom, thicker at low zoom). This is required by L2 at high
  zoom and is consistent with the constant-screen label text; it is a deliberate, documented behaviour
  change, and it is the only change in this design that is visible on a zoomed-in graph.
- **R3 — two length constants that must stay ordered.** `LEADER_CLEARANCE` (3.0) must stay below the slots'
  smallest box offset (`SLOT_GAP` = 6.0), otherwise every near-slot leader disappears; the §5.1.5
  no-leader rule is the graceful degradation. No new coupling beyond that ordering, which the geometry
  test covers implicitly.
- **R4 — HiDPI.** Tests rasterize at 1:1 device scale. On a scaled device both the leader and the glyphs
  scale together, so the *relative* geometry of L2 is preserved; no test covers the scaled-back case and
  none is planned.
- **R5 — painted-ink assertions are rasterization-dependent.** The 1.5 px and 0.26 px numbers are host and
  JDK dependent in principle. The contract is geometric (L1) and the painted assertion (L2) is made with
  the production stroke and the production centring, so a JDK-level antialiasing change would surface as a
  test failure rather than a silent violation.

## 12. Points this design deliberately leaves to the specification

The specification pins: the exact slab-clipping formula and its numeric tolerance; the exact list of
updated `ScreenLabelPlacementShould` lines; the exact mask helpers' parameters and the zoom matrix of
§9.2; whether the painted test also covers the enclosure-external leader (recommended: yes, one fixture);
the `LeaderLine` constructor's exact validation messages; and the validation vectors for the implementation
lanes. Nothing here is left open that would change the contract of §5.
