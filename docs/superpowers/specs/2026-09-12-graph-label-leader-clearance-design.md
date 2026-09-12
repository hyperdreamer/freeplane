# Graph Workspace label / leader-line clearance — Design

- Date: 2026-09-12 (revision 2)
- Review status: **revision 2, awaiting review attempt 2.** Attempt 1 reported
  `1 blocker, 4 majors, 5 minors`; §13 maps every finding to its resolution.
- Topic: `graph-label-leader-clearance`
- PM run: `pm-run-20260912-212024-c7dfa6db`
- Delivery target: `refs/heads/plugin/graph-workspace` at `/data/home/guest/Development/freeplane`
- Integration branch: `pm/graph-label-leader-clearance/run-pm-run-20260912-212024-c7dfa6db`
- User request (verbatim): *"The node name should not overlap with the leader line pointing to it."*
- User decision: leader rule **option A** (keep every leader, trim it clear of the name's box), with the
  gap value left to this design inside the offered 1–3 px range. §4.4 pins **3.0 screen px** as a
  *margin* choice over the smallest value that passes measurement.
- Evidence: `docs/superpowers/specs/mockups/2026-09-12-label-leader-clearance/`
  - `label-leader-clearance.png` — four panels (today, A, B, C), production-scale fixture plus two 2x junctions
  - `LabelLeaderClearanceMockups.java` — generator, reproduces the PNG and mirrors the pinned rule
  - `LeaderClearanceProbe.java` / `leader-clearance-probe.txt` — the measurements quoted in §4.4 and §6
    (sha256 of the probe output: `de9cb7f143c3c929c574a11a4672c63c530f2836d970915ae9bb612222a2959a`,
    reproduced byte-identically by review attempt 1)

## Revision 2 changelog

1. **The enclosure-external leader gets its own seam.** `EXTERNAL` labels are constructed with
   `Slot.ABOVE` **plus** an explicit leader start (`ScreenLabelPlacement.java:303-305`, `:337-339`), so a
   slot-keyed trim helper returns `Optional.empty()` for exactly the labels it was supposed to serve. §7.3
   now specifies a start-taking core and a slot-keyed node wrapper, and L4 states both leader sources.
   (was BLOCKER `external-leader-helper`)
2. **The guarantee is bounded by the painter's font clamp.** `paintLabels` draws the glyph at
   `max(1.0, size/zoom)` world units (`GraphPainter.java:241-242`), so above `zoom = size` the painted
   glyph is *larger* than the box the placement measured; §2/§5.2 now state the bound (`zoom ≤ 9`, the
   smallest base font in use) and record the pre-existing clamp defect as out of scope with its measured
   size. (was MAJOR `font-clamp-zoom`)
3. **3.0 px is presented as margin, not as necessity.** Attempt 1's independent pixel sweeps found no
   shared glyph/leader pixel at 2.0 px for the fixture texts, all approach directions and 32×8 sub-pixel
   phases; §4.4 now says so and no longer presents a continuous-extent bound as measured evidence. The
   unmeasured "zoom ≈ 1.8" break point is replaced by the measured one. (was MAJOR `option-b-evidence`)
4. **The red phases are defined as scaffold-then-red, not "fails on today's code".** Tests written against
   `leader()` cannot run against the unmodified tree at all. §9 now prescribes a behaviour-preserving
   scaffold commit (today's geometry behind the new API), the red run against that scaffold, then the
   green commit, plus a mutation check for the stroke compensation. (was MAJOR `red-phase-not-falsifiable`)
5. **`MIN_VISIBLE_LEADER` stops the enclosure-external dot.** `EXTERNAL_GAP = 4` minus a 3 px clearance
   leaves a 1 px leader; §5.6 pins a 2 px minimum visible length, and §11 R1 records the consequence
   (first-lane enclosure-external labels carry no leader) and the rejected alternative (moving the label,
   which would invalidate the previous run's pinned enclosure fixtures). (was MAJOR `external-stub`)
6. The zero-clearance probe numbers are cited correctly (4 centres at zoom 1, 22 at zoom 4; 8 is the
   2 px row). (was MINOR `zero-clearance-number`)
7. Four shifted source citations corrected. (was MINOR `citations-source`)
8. §9.4's test references are replaced by the grep output, including the previously omitted
   `ScreenLabelPlacementShould.java:275`. (was MINOR `citations-tests`)
9. L3's alternative is now considered and rejected with a reason. (was MINOR `l3-alternative`)
10. The painted test uses a production-faithful glyph mask; the legacy harness mask is left untouched and
    its 0.156 px baseline approximation is recorded. (was MINOR `inkmask-fidelity`)

## 0. Normative conventions

- "Screen space" is the coordinate system of `GraphCanvas.paintComponent`'s `Graphics2D` **before**
  `GraphPainter.worldTransform`; "world space" is the layout coordinate system. Label rectangles, slot
  offsets, `SLOT_GAP`, `DISPLACED_OFFSET` and this design's clearance are all screen space.
- "Leader" means the straight segment a placed label carries from its anchor source to its name.
- "The name's box" (the *label rectangle*, `R`) is `PlacedLabel.bounds()`: width and height from
  `font.getStringBounds(text, SCREEN_FRC)` (`ScreenLabelPlacement.screenBounds`, `:704-706`), centred on
  `(anchorX, anchorY)` (`PlacedLabel.java:102-104`). It is measured with the **base** theme font at its
  base size, independent of zoom.
- "Painted ink" is a rasterized `alpha != 0` pixel at 1:1 device scale.
- `inflate(r, g)` is the rectangle with `minX-g, minY-g, maxX+g, maxY+g`; `distance(p, rect)` is the
  Euclidean distance from a point to that rectangle, `0` on and inside its boundary.

## 1. Problem

### 1.1 The reported defect

The user reported that a node's name is crossed by the leader line that points at it. The visible symptom
is a thin stroke running through the word, e.g. `Axiom of Choice` and `Replacement Scheme` in the reported
screenshot. The stroke is *painted first and the name second*
(`GraphPainter.paintLabels`, `GraphPainter.java:247-252`), so it shows **through the gaps between
glyphs** — which is why it reads as a strikethrough rather than as a covered line. The leader is painted
in `theme.labelColor()` (`GraphPainter.java:243-252`), the same colour as the glyphs, so where it emerges
it is indistinguishable from text ink.

### 1.2 Mechanism

1. `ScreenLabelPlacement.leaderStart(slot, centerX, centerY, radius, anchorX, anchorY)`
   (`ScreenLabelPlacement.java:555-565`) returns the **disc-rim point on the centre → anchor ray**.
2. `GraphPainter.paintLabels` draws `Line2D(leaderStart, (anchorX, anchorY))` (`:247-251`).
3. `anchorX/anchorY` is the **centre of the label rectangle** by construction
   (`PlacedLabel.bounds()` = `anchor ± width/2, height/2`, `PlacedLabel.java:102-104`).

The segment therefore always ends *inside* its own name's box: it enters the box at the face the ray
crosses and continues to the centre. There are three leader-producing paths and **all three** end at that
centre through the single draw site:

| path | start | site |
|---|---|---|
| ladder | disc-rim point on the centre → anchor ray | `ScreenLabelPlacement.java:512` |
| retained placement (pan/zoom) | recomputed rim point | `ScreenLabelPlacement.java:467` |
| enclosure-external | hull's nearest boundary point to the anchor | `ScreenLabelPlacement.java:298-305` |

`ABOVE`/`BELOW` slots carry no leader (`:557`), and neither do `baseSlot` (`:575-577`), `hoverOnly`
(`:586-588`) or the enclosure `ARC`/`INTERIOR` paths (`:266-267`, `:217-218`, `:325-326`), all of which
pass `Optional.empty()`. The defect is systematic, not fixture-specific.

### 1.3 Why a placement-level fix is not enough by itself

The stroke is applied under the world transform (`GraphPainter.paint` → `copy.transform(...)` at
`GraphPainter.java:64`, `worldTransform` at `:83-89` with `transform.scale(zoom, zoom)` at `:87`) and is
*not* zoom-compensated, while the label font directly above it *is*
(`deriveFont(Math.max(1.0f, size / zoom))`, `GraphPainter.java:241-242`). The leader's painted screen width
is therefore `1.4 · zoom` (`theme.edgeStroke()` = `BasicStroke(1.4, CAP_ROUND, JOIN_ROUND)`,
`GraphTheme.java:59`). A screen-space clearance constant can only bound the painted result if the painted
stroke weight is screen-constant too — §5.3 quantifies this, and §4.4 measures where it breaks.

## 2. Goals

- **G1** A name is never touched by the leader that points at it, for every zoom at or below the painter's
  font clamp (`zoom ≤ 9`, the smallest base font in use — §5.2 and §11 R6).
- **G2** The leader keeps its information and its existence rule: node labels in every slot except
  `ABOVE`/`BELOW` keep a leader, enclosure-external labels keep theirs while it stays informative (§5.6),
  and every start stays where it is today (disc rim, or hull boundary).
- **G3** The invariant is falsifiable by deterministic, non-visual tests with a genuine red phase (§9).
- **G4** Placement is otherwise untouched: no change to slot selection, obstacle bookkeeping, the
  truncation ladder, hull geometry, hit-testing, label visibility levels, anchors or `EXTERNAL_GAP`.

## 3. Non-goals

- A leader crossing a label it does not belong to, or an edge crossing a label (only *its own* name is in
  scope).
- Label-to-label overlap, disc-to-disc separation, hull contents — pinned by the previous
  `graph-node-separation` run and deliberately not re-opened.
- Minimising leader-leader crossings beyond keeping the existing zero-crossing assertions green.
- Changing **which** slots carry a leader (that was option C, rejected — §4.3) and changing anchors, slot
  offsets, `SLOT_GAP`, `DISPLACED_OFFSET`, `EXTERNAL_GAP`, truncation or the obstacle model.
- **The painter's font clamp above `zoom = basePt`** (`GraphPainter.java:241-242`). Above it the painted
  glyph is larger than the box the placement measured, so *every* placement property (label-to-label
  spacing, disc avoidance, this design's clearance) is invalid there, not just the leader. Measured by
  review attempt 1: 12 pt base at zoom 20 → painted width 151.8 px against a 91.1 px placement box
  (30.4 px per side); 9 pt at zoom 12 → 11.4 px per side; 15 pt at zoom 20 → 19.0 px per side. Fixing it
  is a separate change (it requires the placement font rule to follow the painted one), and G1/L2 are
  explicitly bounded to `zoom ≤ 9` instead.
- HiDPI/device scale factors (the relationship between the two ink regions is scale-invariant, §11 R4).

## 4. Decision

### 4.1 Chosen rule: option A

Every leader keeps its start and is **trimmed so that it stops clear of the name's box**: the end becomes
the point where the ray `[start → anchor]` crosses `inflate(R, LEADER_CLEARANCE)`. The user chose this from
the three mocked-up rules.

### 4.2 Why not option B (stop exactly on the box edge)

Measured (§6): leader ink reaches **1.5 px beyond its geometric end** (0.7 px half-stroke with a
`CAP_ROUND` cap plus antialiasing), and glyph ink reaches to **within 0.03 px of the box's leader-facing
left edge**, overhanging the right edge by as much as **0.26 px**. A zero clearance therefore puts painted
leader ink inside the box: the probe reports 4 ink pixel centres inside the box at zoom 1 and 22 at zoom 4
for today's stroke, and review attempt 1's independent gap-0 sweep found actual shared glyph/leader pixels.
Since the leader shares the glyph colour, a flush leader also reads as welded to the first glyph. B fails G1.

### 4.3 Why not option C (drop the leader for adjacent slots)

C removes the rim → name cue that the previous run deliberately introduced for *all* non-`ABOVE`/`BELOW`
slots (C12), and on its own it does **not** fix the reported defect: displaced (`*_FAR`) leaders and
enclosure-external leaders would still end at their label's centre. Keeping C would have required A's trim
anyway for those, so C is A plus an additional contract change for no benefit.

### 4.4 The clearance value: `LEADER_CLEARANCE = 3.0` screen px (a margin choice)

Measured facts (§6):

| quantity | measured |
|---|---|
| leader ink beyond the geometric end, screen-constant stroke | `1.5` px |
| worst glyph ink overhang on a leader-facing side | `0.26` px |
| clearance 2.0 px: ink pixel centres inside `R`, all zooms, both stroke modes | `0` |
| clearance 2.0 px: minimum distance from a leader ink pixel centre to `R` | `0.5` px |
| clearance 1.0 px: ink pixel centres inside `R` | `1`–`15` (zoom-dependent) |
| clearance 0.0 px: ink pixel centres inside `R` | `4` (zoom 1) – `22` (zoom 4) |

**2.0 px is the smallest value the measurements clear, and it does clear them**: review attempt 1 repeated
the probe byte-identically and ran additional independent pixel sweeps (13 fixture texts × 360 approach
directions, and a 32×8 sub-pixel phase sweep at 2.0/2.5/3.0 px) with `worstOverlap = 0` in every cell.

**3.0 px is pinned as margin.** The separation at 2.0 px is 0.5 px of pixel-centre distance, i.e. half a
device pixel: it holds for the measured texts, this host and this JDK, but it is not robust to font
substitution, changed antialiasing or a different rasterizer. 3.0 px buys a further device pixel of margin
for a visually identical result (the difference is imperceptible at the mockup's junctions) and stays
inside the 1–3 px range offered to the user with option A. If a future measurement shows a fixture where
2.0 px suffices but 3.0 px does not, the rule — not the number — is wrong.

Where the screen-constant stroke is absent (today's `1.4 · zoom`), the same probe shows the break is
**measured between zoom 2 and zoom 4**: at zoom 2 the uncompensated stroke still leaves 0 ink pixel centres
inside `R` for a 3 px clearance, at zoom 4 it leaves 3 (and 8 for a 2 px clearance). This is the measured
basis for §5.3, replacing revision 1's unmeasured "zoom ≈ 1.8" claim.

## 5. Contract

### 5.1 L1 — leader geometry (screen space)

For every placed label that carries a leader, with `S` = start, `C` = anchor (= rect centre) and `R` = the
label rectangle:

1. `end` is the first intersection of the ray `S → C` with `inflate(R, LEADER_CLEARANCE)`, and `S, end, C`
   are collinear in that order with `end ≠ S`.
2. `distance(end, R) ≥ LEADER_CLEARANCE`, with equality when the ray enters through a face; a corner entry
   (diagonal slots) reaches up to `LEADER_CLEARANCE · √2`.
3. The segment `[S, end]` does not intersect `inflate(R, LEADER_CLEARANCE − ε)` for any `ε > 0`; in
   particular it never touches `R`.
4. `S` and `C` are unchanged: the disc-rim point on the `C − centre` ray for node labels; the hull's
   nearest boundary point for enclosure-external labels.
5. If the ray's entry point into `inflate(R, LEADER_CLEARANCE)` does not lie strictly between `S` and `C`,
   the label carries **no** leader (§5.6 also applies).
6. The values of `LEADER_CLEARANCE` (3.0) and `MIN_VISIBLE_LEADER` (2.0) must stay below `SLOT_GAP` (6.0)
   and `EXTERNAL_GAP` (4.0) respectively, otherwise the corresponding slots lose their leaders by L1.5;
   the existing rim assertions (`ScreenLabelPlacementShould.java:293`, `:329`) fail loudly if that happens.

### 5.2 L2 — painted ink separation

Within one paint pass, for every visible label, the leader's painted ink and that label's own painted glyph
ink are **disjoint rasters**: no pixel receives coverage from both. Formally, with `leaderMask` and
`glyphMask` produced by the production stroke and the production font/centring rules (§9.2),
`overlaps(leaderMask, glyphMask)` is false, and no leader ink pixel centre lies inside `R`.

L2 holds for `zoom ≤ 9`. Above that the painter's font clamp makes the painted glyph bigger than `R`
(§3), so the assertion is scoped: the tests run at zoom 0.25 / 1 / 2 / 4 and the limit is recorded rather
than silently assumed. The bound `9` is the smallest base size a leader-carrying label can be painted at:
the theme's three label fonts are 12 / 9 / 15 pt (`GraphTheme.java:66-68`) and they are the only ones
`LabelFonts` exposes (`LabelFonts.java:19`); `GraphCanvasPaintShould` asserts that no carried placement
font is 7 pt (`:280-284`), so no smaller base size exists and 9 pt (the dense font, which leader-carrying
node labels do use) is the binding case.

### 5.3 L3 — zoom invariance of the painted stroke

`LEADER_CLEARANCE` is a screen-space constant, and the leader's **painted stroke weight is screen-constant
too**: the stroke used for leaders is `new BasicStroke(edgeWidth / zoom, CAP_ROUND, JOIN_ROUND)`, matching
what `paintLabels` already does for the font (`GraphPainter.java:241-242`). Measured consequence of
omitting it: the ink past the end becomes `0.7 · zoom + AA`, which exceeds a 3 px clearance between zoom 2
and zoom 4 (§4.4).

*Alternative considered and rejected:* keep today's zoom-scaled stroke and make the clearance
zoom-dependent (`0.7 · zoom + 1.8`). It preserves the leader's current on-screen weight at zoom ≠ 1, but at
`zoom ≥ 6` the required clearance exceeds `SLOT_GAP`, so near-slot leaders would silently disappear as the
user zooms in — the leader set would become zoom-dependent, and the constant-screen label text (which the
font derivation above already guarantees) would be pointed at by a stroke of unrelated weight.

### 5.4 L4 — leader sources (restated)

A leader exists for:
1. every node label whose slot is not `ABOVE`/`BELOW`, computed from the disc rim; and
2. every enclosure-external label, whose start is supplied explicitly as the hull's nearest boundary point
   (`ScreenLabelPlacement.java:298-305`) even though the label's `Slot` field is `ABOVE` (`:337-339`).

Both are subject to L1 and §5.6. Hover-only labels carry no leader; `baseSlot` labels use `ABOVE`.

### 5.5 L5 — no other behaviour change

`PlacedLabel` keeps every other accessor and invariant. Leader-leader crossings measured on the trimmed
segment do not increase; the existing zero-crossing fixtures must stay zero.

### 5.6 L6 — minimum visible leader (`MIN_VISIBLE_LEADER = 2.0` px)

A leader is drawn **iff its trimmed length is at least `MIN_VISIBLE_LEADER`** (2.0 px, screen space);
otherwise the label carries no leader. This is a screen constant* of the same kind as the clearance.

Rationale and consequence: a 1.4 px round-capped stroke shorter than 2 px renders as a detached blob rather
than as a leader. The binding case is the enclosure-external label, whose first candidate lane places its
box `EXTERNAL_GAP = 4` px from the hull, leaving `4 − 3 = 1` px after trimming: **first-lane
enclosure-external labels therefore carry no leader**, while lane ≥ 1 (≥ `h + 4` px of room) keeps a
properly trimmed one. Node labels are unaffected: the shortest trimmed node leader is the axis-aligned near
slot at `SLOT_GAP − LEADER_CLEARANCE = 3.0` px, and a diagonal near slot enters the inflated box at least
`(SLOT_GAP − LEADER_CLEARANCE)·√2 ≈ 4.24` px from the rim. §11 R1 records this as a deliberate, visible
consequence, and the mockup generator implements the same rule.

## 6. Measured evidence

Probe: `LeaderClearanceProbe.java`, output `leader-clearance-probe.txt`, Java 21.0.8 / this host,
`KEY_ANTIALIASING=ON`, `KEY_RENDERING=VALUE_RENDER_SPEED` (the hints `GraphPainter` sets). Review attempt 1
reproduced the output byte-identically (`diff` empty, matching sha256).

Leader ink past its geometric end, and ink pixel centres inside the box for a given clearance
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
negative = ink starts inside the box, over the fixture texts `Axiom of Choice`, `Theorem`, `Power Set`,
`Replacement Scheme`, `Foundation / Regularity`, `Extensionality`, `ZFC`, `Axioms`,
`Basic Definitions and Theorems`, `Well-Ordering Theorem of Choice and Regularity`, `Group`,
`Transfinite Induction over Ordinal Numbers`, `Zermelo`:

| side a leader can approach from | worst measured | text |
|---|---|---|
| left (RIGHT slot) | `-0.0302` (0.03 px *inside*) | `Theorem` |
| right (LEFT slot) | `+0.2578` (0.26 px *outside*) | `Power Set` |
| top / bottom (ABOVE_\*/BELOW_\* slots) | `-0.6721` (0.67 px inside) | `Replacement Scheme` |

Review attempt 1 additionally measured, independently of this probe: 13 fixture texts × 360 approach
directions, and a 32×8 sub-pixel phase sweep, at gaps 2.0 / 2.5 / 3.0 px → `worstOverlap = 0` in every
cell; and gap 1.0 px → actual shared pixels (§4.2). Its font-clamp measurement (12 pt base at zoom 20 →
30.4 px per-side overhang of painted glyph over the placement box) is quoted in §3.

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

### 7.3 `ScreenLabelPlacement` — two seams, because there are two start sources

```java
/** Core trim: any start (disc rim, hull boundary). */
private static Optional<LeaderLine> leader(LayoutPoint start, double anchorX, double anchorY,
        double width, double height);

/** Node labels: computes the rim start, then delegates to the core. */
private static Optional<LeaderLine> nodeLeader(Slot slot, double centerX, double centerY,
        double radius, double anchorX, double anchorY, double width, double height);
```

- `nodeLeader` returns `Optional.empty()` for `ABOVE`/`BELOW` (`ScreenLabelPlacement.java:557`) and
  otherwise computes the existing rim point (unchanged arithmetic, including the `1e-6` guard) and
  delegates to `leader(...)`. Call sites: the ladder (`:512`) and the retained path (`:467`).
- `leader(...)` implements L1 and L6: slab clipping of the ray against `inflate(R, LEADER_CLEARANCE)`,
  `Optional.empty()` when the entry is absent or not strictly between `S` and `C`, and
  `Optional.empty()` when the surviving length is below `MIN_VISIBLE_LEADER`. The enclosure-external path
  (`:298-305`) calls `leader(hullBoundaryPoint, anchorX, anchorY, width, height)` directly — it must not go
  through `nodeLeader`, whose `Slot.ABOVE` would return empty (BLOCKER, revision 2 §1).
- New constants next to `SLOT_GAP`/`DISPLACED_OFFSET` (`ScreenLabelPlacement.java:28-31`):
  `static final double LEADER_CLEARANCE = 3.0;` and `static final double MIN_VISIBLE_LEADER = 2.0;`.
- A retained or laddered label whose leader is empty still keeps its label and simply carries no leader:
  the leader is optional and this change must not alter which labels are placed.

### 7.4 `GraphPainter.paintLabels` — end point and screen-constant stroke

```java
final Stroke leaderStroke = leaderStroke(theme, viewport.zoom());   // once, before the label loop
...
graphics.setStroke(leaderStroke);
graphics.draw(new Line2D.Double(
    worldX(line.start().x(), viewport, size), worldY(line.start().y(), viewport, size),
    worldX(line.end().x(),   viewport, size), worldY(line.end().y(),   viewport, size)));
```

- New package-private seam `static Stroke leaderStroke(GraphTheme theme, double zoom)` returning
  `new BasicStroke((float) (theme.edgeStroke().getLineWidth() / zoom),
  BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)`. The seam exists so the painted-ink test paints with the
  **production** stroke instead of a mirror of it (§9.2), and so §5.3 is unit-testable. `zoom > 0` is
  enforced by `GraphViewport`, so the division is safe.
- The label font keeps its existing `deriveFont(size / zoom)`; only the leader stroke gains the compensation.
- Nothing downstream can inherit the stroke: `paintHighlights` and `paintConnectionPreview` run after
  `paintLabels` and set their own.

### 7.5 Deleted code

`PlacedLabel.leaderStart()`, `ScreenLabelPlacement.leaderStart(...)`, and every test read of
`leaderStart()` — replaced by `leader()`. No parallel old/new path is retained.

## 8. Error handling and degenerate cases

| case | behaviour |
|---|---|
| `ABOVE` / `BELOW` node slot | no leader (unchanged, L4.1) |
| enclosure-external with `Slot.ABOVE` and an explicit start | leader from the hull boundary, trimmed (L4.2) |
| hover-only label, non-forced | no leader (unchanged) |
| `baseSlot` forced label | `ABOVE`, no leader (unchanged) |
| `S` already inside `inflate(R, LEADER_CLEARANCE)` (e.g. first-lane enclosure-external) | no leader; the label is still placed |
| trimmed length below `MIN_VISIBLE_LEADER` | no leader; the label is still placed (§5.6) |
| `anchor == centre` (impossible: anchor distance ≥ `radius + SLOT_GAP`) | guarded by the existing `Math.max(1e-6, hypot)` |
| non-finite inputs | `IllegalArgumentException` from existing placement/`PlacedLabel` validation (`PlacedLabel.java:49-51`); unchanged |
| retained label after pan/zoom | both endpoints recomputed from the retained slot (as `leaderStart` is today, `:467`); the cache key already contains zoom/centre/size |
| zoom above the font clamp (9 for the dense font) | out of scope, §3; the leader geometry is still correct, the painted glyph is not `R`-sized |

## 9. Test strategy

### 9.0 How the red phase is produced (revision 2)

Tests written against `leader()`/`leaderStroke(...)` cannot compile against the unmodified tree, and a
compile error is not a behavioural red phase. The implementation therefore proceeds in three commits:

1. **Scaffold (behaviour-preserving).** Add `LeaderLine`, `PlacedLabel.leader()` with `end = C` (today's
   geometry), and `GraphPainter.leaderStroke(theme, zoom)` returning the **uncompensated**
   `theme.edgeStroke()`. The full existing suite must stay green — this is the green baseline.
2. **Red.** Add §9.1 and §9.2 against the scaffold. Both fail on the scaffold for their stated reasons
   (L1.2 fails because `distance(end, R) = 0`; the compensation test fails because the stroke is not
   screen-constant; the painted test fails at every zoom because the leader reaches the centre).
3. **Green.** Implement the trim, the minimum visible length and the compensation; all tests pass.

The mutation check for the *painted* clause is separate and stays available afterwards: reverting only the
stroke compensation must fail §9.2 at zoom 4 (measured: 3 ink pixel centres inside `R` at a 3 px
clearance), and reverting only the trim must fail §9.1 and §9.2 at every zoom.

### 9.1 Geometry test (`ScreenLabelPlacementShould`)

`keepsEveryLeaderClearOfItsOwnLabelBox`: over the existing dense and long fixtures plus one
enclosure-external fixture, at zoom 0.25 / 1 / 2 / 4, for every visible non-hover label with a leader
assert (a) `end` lies on `[S, C]` strictly between them, (b) `distance(end, R) ≥ LEADER_CLEARANCE − 1e-9`,
(c) `[S, end] ∩ inflate(R, LEADER_CLEARANCE − 1e-6)` is empty, (d) `[S, end] ∩ R` is empty, (e)
`|end − S| ≥ MIN_VISIBLE_LEADER` whenever a leader is present. The enclosure fixture is new: the existing
`denseScene()`/`longScene()` contain nodes only (`ScreenLabelPlacementShould.java:932-948`) and the rim
helpers skip enclosures (`:284`, `:316`).

### 9.2 Painted-ink test (`ScreenLabelPlacementShould`)

`keepsLeaderInkOutOfItsOwnGlyphInk`: for every visible label, at zoom 0.25 / 1 / 2 / 4,
(a) `overlaps(leaderMask, glyphMask)` is false and (b) no `leaderMask` pixel centre lies inside `R`.

- `leaderMask(label, theme, zoom, width, height)` paints **only** the leader with
  `GraphPainter.leaderStroke(theme, zoom)` under an `AffineTransform` that mirrors
  `GraphPainter.worldTransform` (`translate(size/2) → scale(zoom) → translate(−centre)`), so it uses the
  production stroke, not a mirror of it.
- `glyphMask(label, zoom, width, height)` is a **new, production-faithful** mask: font
  `label.font().deriveFont(max(1.0f, size / zoom))`, centring by
  `font.getStringBounds(text, SCREEN_FRC)` width and `LineMetrics` baseline, and the same `scale(zoom)`
  transform — i.e. the rule of `GraphPainter.drawCentered` (`:267-271`).
- The legacy `inkMask` (`:1413-1442`) is **not** modified: it draws `label.font()` with a
  `FontMetrics`-derived baseline, which differs from the painter's `LineMetrics` baseline by 0.156 px on
  this host, and it backs the previous run's pinned overhang/min-gap expectations. Its approximation is
  recorded here rather than silently inherited; the new test uses `glyphMask`.

### 9.3 Compensation unit test (`GraphCanvasPaintShould`)

`keepsTheLeaderStrokeScreenConstant`: for zoom 0.25 / 1 / 2 / 4 assert
`GraphPainter.leaderStroke(theme, zoom).getLineWidth() * zoom == theme.edgeStroke().getLineWidth()`
(within 1e-6). This is the falsifiable form of §5.3, independent of the ink harness.

### 9.4 Updated helpers and existing expectations

`grep -rn "leaderStart" freeplane_plugin_graph/src` (review attempt 1, complete list) returns
`PlacedLabel.java:35, 42, 62, 126, 127`; `ScreenLabelPlacement.java:337, 339, 467, 512, 555`;
`GraphPainter.java:247, 249, 250`; and in `ScreenLabelPlacementShould.java` the sites at `:260-267`,
`:270`, `:275`, `:290`, `:293`, `:298`, `:326`, `:329`, `:330`, `:489-494`, `:510`, `:770`, `:785`, `:797-799`,
`:813`, `:825`, `:856`, `:891`, `:898`, `:1199`, `:1202`. (`:275` was omitted by revision 1.)

- Each becomes a `leader()` read; the rim/start assertions keep their pinned values, and start-only
  assertions such as `:290/:293` keep their presence/absence semantics.
- `leaderCrossings(placed)` (`:1196-1214`) measures `leader().start() → leader().end()`: it must describe
  the painted segment. Values stay 0 on the fixtures.
- `assertNodeLeadersAtTheRim` (`:281-310`) and `assertRetainedNodeLeadersAtTheRim` (`:312-342`) keep their
  rim/collinearity/forward assertions on `leader().start()` and gain the §9.1 assertions on `end`.
- `leader(label, scene, zoom)` (`:1306-1315`) computes `hypot(anchor − centre)` and never reads
  `leaderStart`; it is the basis of `meanLeader` (`:1317-1328`), `maxLeader` (`:1330-1344`),
  `assertBounds` (`:675-694`) and the pinned expectations at `:91-92`, `:207`, `:253-254`, `:276`,
  `:694-695`. Since anchors and placement do not change, those pins stay valid — to be confirmed by
  running them, not by reasoning.
- No reference to `leaderStart` exists outside `org.freeplane.plugin.graph.canvas`;
  `GraphWorkspaceModelAcceptanceShould` uses `PlacedLabel` only through `text()`/`mode()`/
  `emphaticAtAnchor()` (`:299-302`) and needs no change.

### 9.5 Regression

`gradle :freeplane_plugin_graph:test` (including `GraphCanvasPaintShould` and
`GraphWorkspaceModelAcceptanceShould`), then the full `gradle test` before the implementation audit.

## 10. Mockups

`docs/superpowers/specs/mockups/2026-09-12-label-leader-clearance/label-leader-clearance.png`
(1356x1338, committed with its generator):

- **Panel 1 — Today**: the leader reaches the name's centre; the red dashed box marks the name and the red
  segment marks the part of the stroke that crosses it. Both 2x junctions show the stroke inside
  `Axiom of Choice` and `Replacement Scheme`.
- **Panel 2 — Option A (chosen)**: the leader ends on the box inflated by 3 px; both junctions are clear,
  and the first-lane enclosure-external `ZFC` leader is omitted because it would be 1 px long (§5.6).
- **Panel 3 — Option B (rejected, kept for the record)**: the leader ends on the bare box.
- **Panel 4 — Option C (rejected, kept for the record)**: adjacent slots carry no leader; the displaced
  leader is trimmed like A.

The generator uses the production `slotAnchor` arithmetic and the production 12 pt measurement, and holds
`OPTION_A_GAP = 3.0` and `MIN_VISIBLE_LEADER = 2.0` in single constants, so the mockup and §5 cannot drift
apart. The fixture also shows an `ABOVE` label with no leader.

## 11. Risks

- **R1 — short leaders and the enclosure-external case.** An axis-aligned near-slot leader becomes
  `SLOT_GAP − LEADER_CLEARANCE = 3.0` px (diagonals ≥ 4.24 px), and **first-lane enclosure-external labels
  lose their leader entirely** (`EXTERNAL_GAP 4 − 3 = 1` px, below `MIN_VISIBLE_LEADER`, §5.6). Recorded,
  not mitigated: option C (the alternative that removes short leaders by design) was considered and
  rejected, and *moving* the external label out by `LEADER_CLEARANCE` was rejected because it changes label
  placement, can push a candidate lane outside the placement area or into another obstacle, and would
  invalidate the previous run's pinned enclosure fixtures. The mockup shows the omission.
- **R2 — leader weight changes at zoom ≠ 1.** Today the leader is `1.4·zoom` screen px; after §5.3 it is a
  constant `1.4` screen px (thinner when zoomed in, thicker when zoomed out). Required by L2, consistent
  with the constant-screen label text, and the only zoom-visible change in this design. §5.3 records the
  rejected alternative and why it is worse.
- **R3 — two length constants that must stay ordered.** `LEADER_CLEARANCE` (3.0) must stay below
  `SLOT_GAP` (6.0) and below the external room (`EXTERNAL_GAP`, 4.0); §5.1.6 states the relation and the
  existing rim assertions fail loudly if it is broken. No new coupling beyond that ordering.
- **R4 — HiDPI.** Tests rasterize at 1:1 device scale. On a scaled device both the leader and the glyphs
  scale together, so the relative geometry of L2 is preserved; no scaled-back test is planned.
- **R5 — painted-ink assertions are rasterization-dependent.** The 1.5 px and 0.26 px numbers are host and
  JDK dependent in principle, which is exactly why §4.4 pins 3.0 px as margin instead of the smallest
  measured-passing value. §9.2 uses the production stroke (`leaderStroke`) and a production-faithful glyph
  mask, so a JDK antialiasing change surfaces as a test failure rather than a silent violation.
- **R6 — the font clamp bounds G1.** Above `zoom = 9` the painted glyph exceeds `R` (measured, §3), so the
  clearance guarantee lapses there. This is a pre-existing defect of the placement model, recorded and out
  of scope; the design does not claim otherwise, and the tests state their zoom range.

## 12. Points this design deliberately leaves to the specification

The exact slab-clipping formula and its numeric tolerance; the exact definition of "strictly between `S`
and `C`"; the `LeaderLine` constructor's validation messages; the enumeration of §9.4's migrated lines; the
new enclosure-external fixture's geometry and pinned numbers; the mask helpers' signatures and the zoom
matrix; and the validation vectors for the implementation lanes. Nothing here is left open that would
change the contract of §5.

## 13. Review findings → resolution

Attempt 1: 1 blocker, 4 majors, 5 minors.

| id | severity | resolution |
|---|---|---|
| `external-leader-helper` | BLOCKER | §7.3 now specifies `leader(start, anchor, w, h)` (core) and `nodeLeader(slot, …)` (node wrapper); the enclosure-external path calls the core with the hull boundary point. §5.4/L4 states both sources and that `EXTERNAL` labels are constructed with `Slot.ABOVE`. |
| `font-clamp-zoom` | MAJOR | G1/§5.2 bounded to `zoom ≤ 9`; §3 records the clamp as an explicit non-goal with the reviewer's measured overhangs; §9.2 tests at 0.25/1/2/4 and states the bound. |
| `option-b-evidence` | MAJOR | §4.4 restated: 2.0 px is the smallest value that passes every measured check (including the reviewer's independent sweeps), 3.0 px is margin; the continuous-extent bound is no longer presented as measured evidence; the "zoom ≈ 1.8" claim is replaced by the measured break between zoom 2 and 4. |
| `red-phase-not-falsifiable` | MAJOR | §9.0 prescribes scaffold → red → green, plus the two mutation checks; §9.1/§9.2 no longer claim to fail on today's code. |
| `external-stub` | MAJOR | §5.6 adds `MIN_VISIBLE_LEADER = 2.0` px and states the consequence for first-lane external labels; R1 records it and the rejected alternative; §9.1 adds an enclosure-external fixture; the mockup implements the rule. |
| `zero-clearance-number` | MINOR | §4.2 cites 4 centres at zoom 1 and 22 at zoom 4 for zero clearance; 8 is cited in §4.4 as the 2 px row. |
| `citations-source` | MINOR | Fixed: `GraphPainter.java:64`, `:241-242`, `ScreenLabelPlacement.java:555-565`, and `:557` for the ABOVE/BELOW condition. |
| `citations-tests` | MINOR | §9.4 replaced with the reviewer's grep output, including `:275`, `:1199`, `:1202`; `assertRetainedNodeLeadersAtTheRim` `:312-342`; `leaderCrossings` `:1196-1214`; `meanLeader`/`maxLeader` named as users of `leader(...)`. |
| `l3-alternative` | MINOR | §5.3 compares the zoom-dependent-clearance alternative and rejects it (at `zoom ≥ 6` the required clearance exceeds `SLOT_GAP`, so the leader set would become zoom-dependent). |
| `inkmask-fidelity` | MINOR | §9.2 introduces a production-faithful `glyphMask`, leaves the legacy `inkMask` untouched, and records its 0.156 px baseline approximation and why it is not migrated. |
