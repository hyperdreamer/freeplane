# Graph Workspace Label / Leader-Line Clearance — Implementation Specification

- Date: 2026-09-12
- Status: implementation-ready specification, derived from the approved design
  `docs/superpowers/specs/2026-09-12-graph-label-leader-clearance-design.md` (revision 3;
  design review attempt 1 = 1 blocker / 4 majors / 5 minors, attempt 2 = **0 blockers** / 2 majors /
  5 minors, revision 3 applies all of them and was approved for specification drafting).
- Review history: `/data/home/henry-arch/.local/state/pi/project-manager/runs/0233ff88d3cc54bbd61550c61fa917d6e2a5717f8a03be4e9aa63dcd47dd1cfa/pm-run-20260912-212024-c7dfa6db/reports/design-review-attempt-{1,2}.md`.
- Specification review history: attempt 1 (fresh frontier reviewer) reported **0 blockers**, 1 major and
  5 minors; this revision applies all six. That reviewer independently reproduced the `grep` census of 45
  `leaderStart` references, the §3.2 diagonal table, the §3.3 enclosure outcomes, the §4.3 mask results
  and both mutation-check shapes, and confirmed the green baseline
  (`gradle :freeplane_plugin_graph:test` → BUILD SUCCESSFUL in 1 m 13 s). Report:
  `…/reports/spec-review-attempt-1.md`.
- This specification does not modify the design. Where the design leaves a value, formula, message,
  fixture or validation vector open (design §12), this document pins it and states its provenance.
  All seven former open points are resolved and recorded in §7. The behavioural contract of the
  design §5 is adopted verbatim: the 3.0 px clearance, the 2.0 px minimum visible leader, the
  screen-constant stroke and the enclosure-external behaviour are fixed.
- Every number, coordinate and `file:line` cited below was verified against this integration worktree
  or produced by a command run for this specification (Appendix A). Values marked **[M]** are
  measured, **[D]** derived by arithmetic or from measured data, **[P]** proposed by the design and
  adopted here, and **[N]** not pinnable without a code change (explicitly deferred, with the exact
  capturing command and the comparison it must satisfy).

## 0. Normative conventions

- "Screen space" is the coordinate system of `GraphCanvas.paintComponent`'s `Graphics2D` **before**
  `GraphPainter.worldTransform`; "world space" is the layout coordinate system. Label rectangles,
  slot offsets, `SLOT_GAP`, `DISPLACED_OFFSET`, `EXTERNAL_GAP` and this change's constants are all
  screen space.
- "Leader" means the straight segment a placed label carries from its anchor source to its name.
- "The name's box" (the *label rectangle*, `R`) is `PlacedLabel.bounds()`: width and height from
  `font.getStringBounds(text, SCREEN_FRC)` (`ScreenLabelPlacement.screenBounds`,
  `ScreenLabelPlacement.java:704-706`), centred on `(anchorX, anchorY)` (`PlacedLabel.java:102-104`).
- "Painted ink" is a rasterized `alpha != 0` pixel at 1:1 device scale, i.e. a pixel whose ARGB
  alpha byte `(getRGB(x, y) >>> 24)` is non-zero.
- `inflate(r, g)` is the rectangle with `minX-g, minY-g, maxX+g, maxY+g`; `distance(p, rect)` is the
  Euclidean distance from a point to that rectangle, `0` on and inside its boundary.
- **Entry parameter.** For a start `S`, an anchor `C` and the inflated box
  `B = inflate(R, LEADER_CLEARANCE)`, the *entry parameter* is the unique `t` with
  `S + t·(C − S) ∈ ∂B` and `S + t'·(C − S) ∉ B` for `0 ≤ t' < t`. "Strictly between `S` and `C`"
  means `0 < t < 1`. §2.3 pins the slab computation of `t`.
- **Mask frame (new).** The §4.3 masks and the §4.2 painted assertions rasterize into a `W×H`
  `TYPE_INT_ARGB` image whose screen frame is the placement area, with the fixture viewport centre
  `(0, 0)` (every fixture used here places with centre `(0, 0)`: `ScreenLabelPlacementShould.request`
  and `enclosureRequest` both build the request with `centerX = centerY = 0.0`). The screen→world
  mapping inside a mask is therefore `(sx − W/2)/zoom, (sy − H/2)/zoom`, which is the production
  `GraphPainter.worldX/worldY` with `viewport.centerX() = viewport.centerY() = 0`
  (`GraphPainter.java:257-265`).
- **Alpha threshold (new).** Both masks treat a pixel as ink iff `(image.getRGB(x, y) >>> 24) != 0`;
  a pixel's *centre* is `(x + 0.5, y + 0.5)`. This is the threshold of the pre-existing `inkMask`
  (`ScreenLabelPlacementShould.java:1436`) and of design §0's "painted ink".

## 1. Pinned constants

| # | Constant | Value | Kind | Source / justification |
|---|---|---|---|---|
| C1 | `LEADER_CLEARANCE` | `3.0` screen px | **[P]** design §4.4 | Declared in `ScreenLabelPlacement` (package-private `static final double`). The design pins 3.0 as a margin over the smallest measured-passing value (2.0); the contract fixes it at 3.0. |
| C2 | `MIN_VISIBLE_LEADER` | `2.0` screen px | **[P]** design §5.6 | Declared in `ScreenLabelPlacement`. A trimmed leader shorter than this is not drawn. |
| C3 | `SLOT_GAP` | `6.0` screen px | **[M]** | `ScreenLabelPlacement.java:28`; unchanged. |
| C4 | `DISPLACED_OFFSET` | `30.0` screen px | **[M]** | `ScreenLabelPlacement.java:29`; unchanged. |
| C5 | `EXTERNAL_GAP` | `4.0` screen px | **[M]** | `ScreenLabelPlacement.java:31`; unchanged. |
| C6 | `ARC_GAP` | `1.0` screen px | **[M]** | `ScreenLabelPlacement.java:30`; unchanged (cited because the enclosure path of §2.3 uses it). |
| C7 | Leader stroke rule | `new BasicStroke((float) (theme.edgeStroke().getLineWidth() / zoom), CAP_ROUND, JOIN_ROUND)`, i.e. a **screen-constant** `1.4` px painted width | **[P]** design §5.3 | `theme.edgeStroke()` is `BasicStroke(1.4f, CAP_ROUND, JOIN_ROUND)` (`GraphTheme.java:59`); painted screen width today is `1.4 · zoom` because it is applied under `worldTransform` (`GraphPainter.java:64`, `:83-89`). |
| C8 | Painted leader width at zoom `z` | before the change `1.4·z`; after it a constant `1.4` screen px (`1.4/zoom` world units) | **[M]** + **[D]** | Design §5.3; committed probe (`leader-clearance-probe.txt`): with today's stroke `paintedPx` = 1.000 / 3.000 / 3.000 / 7.000 and `overhang` = 0.500 / 1.500 / 1.500 / 3.500 at zoom 0.25 / 1 / 2 / 4; with `1.4/zoom` `paintedPx` = 3.000 and `overhang` = 1.500 at every zoom. |
| C9 | Base label fonts | `SansSerif` plain **12** pt (`labelFont`), plain **9** pt (`denseLabelFont`), bold **15** pt (`emphaticLabelFont`) | **[M]** | `GraphTheme.java:66-68`; `LabelFonts` exposes exactly these three through `full()`/`dense()`/`emphatic()` (`LabelFonts.java:22-31`). |
| C10 | Zoom bound of the guarantee | **9** | **[D]** | `paintLabels` draws `label.font().deriveFont(Math.max(1.0f, size/zoom))` (`GraphPainter.java:241-242`), so the painted glyph equals the placement box only while `zoom ≤ size`. `9` is the smallest base size a leader-carrying label can use (the DENSE rungs carry leaders, `ScreenLabelPlacement.java:645-650`); above it the painted glyph is larger than `R` and every placement property lapses (design §3, out of scope). |
| C11 | Ordering that preserves node leaders | `LEADER_CLEARANCE + MIN_VISIBLE_LEADER ≤ SLOT_GAP` (`3 + 2 ≤ 6`) | **[D]** | Design §5.1.6/R3. The axis-aligned near slot keeps exactly `SLOT_GAP − LEADER_CLEARANCE = 3.0` px, and `3.0 ≥ MIN_VISIBLE_LEADER`. The enclosure-external source has no equivalent guarantee (design §5.6). |
| C12 | Painted ink beyond the geometric leader end | `1.5` px at 1:1 with a 1.4 px round-capped stroke | **[M]** | Committed probe (`leader-clearance-probe.txt`, rows `overhang = 1.500`); 0.7 px half-stroke + antialiasing. |
| C13 | Worst leader-facing glyph ink overhang | `+0.2578` px (right side) / `-0.0302` px (left side, i.e. ink starts 0.03 px *inside* the box) | **[M]** | Committed probe glyph table (`Power Set` right `0.2578`, `Theorem` left `-0.0302`). `C12 + C13 = 1.7578 < 3.0`, the margin that motivates C1. |
| C14 | Font line heights | `h(12) = 16.344114`, `h(9) = 12.258085`, `h(15 bold) = 20.430143` px | **[M]** | Appendix A.5 font probe; the existing tests pin the same heights (e.g. `16.344114` at `ScreenLabelPlacementShould.java:62`, `12.258085` at `:529`, `20.430143` at `:768`). |
| C15 | Test zoom matrix | `0.25, 1.0, 2.0, 4.0` | **[P]** design §9.2/§9.3 | All four are `≤ 9`, so inside C10. |
| C16 | `FontMetrics` vs `LineMetrics` baseline delta (SansSerif 12 pt) | `0.156033` px | **[M]** | Appendix A.5; the reason the legacy `inkMask` (`FontMetrics` baseline, `ScreenLabelPlacementShould.java:1425-1426`) is not reused for the new painted assertion (design §9.2). |

## 2. Interfaces and data structures

### 2.1 `LeaderLine` (new, `org.freeplane.plugin.graph.canvas`, **public**, immutable)

New file `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/LeaderLine.java`:

```java
package org.freeplane.plugin.graph.canvas;

import java.util.Objects;

import org.freeplane.plugin.graph.geometry.LayoutPoint;

public final class LeaderLine {
    private final LayoutPoint start;
    private final LayoutPoint end;

    LeaderLine(final LayoutPoint start, final LayoutPoint end) {
        final LayoutPoint checkedStart = Objects.requireNonNull(start, "start");
        final LayoutPoint checkedEnd = Objects.requireNonNull(end, "end");
        if (!Double.isFinite(checkedStart.x()) || !Double.isFinite(checkedStart.y())
                || !Double.isFinite(checkedEnd.x()) || !Double.isFinite(checkedEnd.y())) {
            throw new IllegalArgumentException("Leader line coordinates must be finite");
        }
        if (checkedStart.equals(checkedEnd)) {
            throw new IllegalArgumentException("Leader line endpoints must differ");
        }
        this.start = checkedStart;
        this.end = checkedEnd;
    }

    public LayoutPoint start() {
        return start;
    }

    public LayoutPoint end() {
        return end;
    }
}
```

- Visibility: the class is **public** (design §7.1); the constructor is **package-private**, so only
  `ScreenLabelPlacement` can create lines and "start present, end absent" is unrepresentable.
- Validation messages, pinned verbatim: `"start"` / `"end"` (null, from `Objects.requireNonNull`),
  `"Leader line coordinates must be finite"`, `"Leader line endpoints must differ"`.
- The finiteness branch is defence in depth: `LayoutPoint`'s constructor already rejects non-finite
  coordinates (`LayoutPoint.java:10-11`), so it is unreachable through a `LayoutPoint`. The order is
  null → finiteness → `equals`.
- No `equals`, `hashCode` or `toString` is added: no test compares `LeaderLine` values, and
  `PlacedLabel` is not a value type either (`PlacedLabel.java` declares none).

### 2.2 `PlacedLabel` — one optional field replaces one optional field

`PlacedLabel.java` (five edits; no other member changes):

| Line | Current | Replacement |
|---|---|---|
| `:35` | `private final Optional<LayoutPoint> leaderStart;` | `private final Optional<LeaderLine> leader;` |
| `:42` | `... final boolean fullTextSlotWasFree, final Optional<LayoutPoint> leaderStart,` | `... final boolean fullTextSlotWasFree, final Optional<LeaderLine> leader,` |
| `:62` | `this.leaderStart = Objects.requireNonNull(leaderStart, "leaderStart");` | `this.leader = Objects.requireNonNull(leader, "leader");` |
| `:126-127` | `public Optional<LayoutPoint> leaderStart() { return leaderStart; }` | `public Optional<LeaderLine> leader() { return leader; }` |
| `:8` | `import org.freeplane.plugin.graph.geometry.LayoutPoint;` | **deleted** (only the old field/accessor used it) |

`leaderStart()` is deleted outright — no compatibility shim (repository legacy-removal policy,
design §7.5). The constructor parameter keeps its position, so every `new PlacedLabel` call site is
edited only for the value it passes, never for the argument order.

### 2.3 `ScreenLabelPlacement` — two seams, two constants, three call sites

New constants immediately after `EXTERNAL_GAP` (`ScreenLabelPlacement.java:31`):

```java
static final double LEADER_CLEARANCE = 3.0;
static final double MIN_VISIBLE_LEADER = 2.0;
```

They are package-private `static final` (like `SLOT_GAP`) so `ScreenLabelPlacementShould` reads them
directly. Replace the deleted `leaderStart` method (`ScreenLabelPlacement.java:555-565`) with the two
seams:

```java
/** Core trim: any start (disc rim, hull boundary). End = first entry of [start -> anchor] into
 *  inflate(R, LEADER_CLEARANCE), when that entry is strictly between start and the centre and the
 *  surviving segment is at least MIN_VISIBLE_LEADER long. */
private static Optional<LeaderLine> leader(final LayoutPoint start, final double anchorX,
        final double anchorY, final double width, final double height) {
    final double dx = anchorX - start.x();
    final double dy = anchorY - start.y();
    final double minX = anchorX - width * 0.5 - LEADER_CLEARANCE;
    final double maxX = anchorX + width * 0.5 + LEADER_CLEARANCE;
    final double minY = anchorY - height * 0.5 - LEADER_CLEARANCE;
    final double maxY = anchorY + height * 0.5 + LEADER_CLEARANCE;
    double entry = Double.NEGATIVE_INFINITY;
    if (dx > 0.0) {
        entry = Math.max(entry, (minX - start.x()) / dx);
    }
    else if (dx < 0.0) {
        entry = Math.max(entry, (maxX - start.x()) / dx);
    }
    if (dy > 0.0) {
        entry = Math.max(entry, (minY - start.y()) / dy);
    }
    else if (dy < 0.0) {
        entry = Math.max(entry, (maxY - start.y()) / dy);
    }
    if (!(entry > 0.0) || !(entry < 1.0)) {
        return Optional.empty();
    }
    final LayoutPoint end = LayoutPoint.of(start.x() + entry * dx, start.y() + entry * dy);
    if (Math.hypot(end.x() - start.x(), end.y() - start.y()) < MIN_VISIBLE_LEADER) {
        return Optional.empty();
    }
    return Optional.of(new LeaderLine(start, end));
}

/** Node labels: computes the rim start, then delegates to the core. */
private static Optional<LeaderLine> nodeLeader(final Slot slot, final double centerX,
        final double centerY, final double radius, final double anchorX, final double anchorY,
        final double width, final double height) {
    if (slot == Slot.ABOVE || slot == Slot.BELOW) {
        return Optional.empty();
    }
    final double dx = anchorX - centerX;
    final double dy = anchorY - centerY;
    final double distance = Math.max(1e-6, Math.hypot(dx, dy));
    final LayoutPoint start = LayoutPoint.of(centerX + dx / distance * radius,
        centerY + dy / distance * radius);
    return leader(start, anchorX, anchorY, width, height);
}
```

**Slab-clipping algebra (normative).** With `S = (sx, sy)` the start, `C = (ax, ay)` the anchor
(the rectangle centre), `R = [ax − w/2, ax + w/2] × [ay − h/2, ay + h/2]`, `g = LEADER_CLEARANCE`:

```
dx = ax − sx,  dy = ay − sy
bMinX = ax − w/2 − g,  bMaxX = ax + w/2 + g
bMinY = ay − h/2 − g,  bMaxY = ay + h/2 + g
entryX = (dx > 0) ? (bMinX − sx)/dx : (dx < 0) ? (bMaxX − sx)/dx : −∞
entryY = (dy > 0) ? (bMinY − sy)/dy : (dy < 0) ? (bMaxY − sy)/dy : −∞
t      = max(entryX, entryY)
end    = (sx + t·dx, sy + t·dy)
```

`t` is the entry parameter of §0 (the segment from `S` to `C` enters the convex box `inflate(R, g)`
at the maximum of the per-axis near-face crossings; `C` is strictly interior because `w > 0`,
`h > 0` — `PlacedLabel.java:49-51`). The four degenerate returns, in evaluation order:

1. **Entry absent** — `dx == 0 && dy == 0` leaves `t = −∞`; `!(t > 0.0)` → `Optional.empty()`
   (also covers `S == C`). `S` and `C` collinear/identical cannot otherwise arise: the node rim
   uses the existing `Math.max(1e-6, hypot)` guard and the anchor is at least `radius + SLOT_GAP`
   from the disc centre.
2. **`S` on or inside `inflate(R, g)`** — every near-face crossing is `≤ 0`, so `t ≤ 0` →
   `Optional.empty()` (L1.5). This is the enclosure-external first-lane case (§3.3).
3. **Entry not strictly between `S` and `C`** — `t ≥ 1` → `Optional.empty()` (L1.5). `t < 1` always
   holds when `C` is strictly interior; the guard is defensive.
4. **Surviving length below `MIN_VISIBLE_LEADER`** — `hypot(end − S) < MIN_VISIBLE_LEADER` →
   `Optional.empty()` (L6). The length is computed **from the stored `end` and `S`** in exactly this
   order, so that the §4.2 assertion recomputes the bit-identical value.
5. **`ABOVE` / `BELOW` node slots** — `nodeLeader` returns `Optional.empty()` before any arithmetic
   (the deleted method's `ScreenLabelPlacement.java:557` condition, unchanged).

**Numeric tolerance.** The implementation uses exact comparisons (`> 0.0`, `< 1.0`, `< 2.0`) and no
epsilon. This is outcome-preserving: an `S` exactly on the inflated boundary gives `t = 0` (empty) or
a round-off `t = ε` whose length is `ε·|SC| < MIN_VISIBLE_LEADER` (empty by return 4); both are
"no leader". The tests carry the design's tolerances (`LEADER_CLEARANCE − 1e-9` for the distance,
`LEADER_CLEARANCE − 1e-6` for the inner wrapper).

**Call-site edits** (all in `ScreenLabelPlacement`, verified with `grep -n`):

| Line | Current | Replacement |
|---|---|---|
| `:337` | `... final Optional<LayoutPoint> leaderStart) {` (`enclosureLabel`) | `... final Optional<LeaderLine> leader) {` |
| `:339` | `... false, false, leaderStart, Slot.ABOVE);` | `... false, false, leader, Slot.ABOVE);` |
| `:298-305` (external path) | computes `leader` as the hull boundary point and returns `Optional.of(leader)` | keep the `leaderWorld` computation, bind the screen-mapped boundary point to a local named `start` (renamed from the current `leader` so it cannot shadow the seam name), and return `leader(start, anchorX, anchorY, size.getWidth(), size.getHeight())`; pass that result to `enclosureLabel`. It must **not** call `nodeLeader` (whose `Slot.ABOVE` returns empty — the design's blocker fix). |
| `:467` (retained path) | `leaderStart(previous.slot(), centerX, centerY, radius, anchor[0], anchor[1])` | `nodeLeader(previous.slot(), centerX, centerY, radius, anchor[0], anchor[1], previous.width(), previous.height())` |
| `:512` (ladder path) | `leaderStart(slot, centerX, centerY, radius, anchor[0], anchor[1])` | `nodeLeader(slot, centerX, centerY, radius, anchor[0], anchor[1], size.getWidth(), size.getHeight())` |

The other `new PlacedLabel` sites pass `Optional.<LeaderLine>empty()` instead of
`Optional.<LayoutPoint>empty()`: `interiorEnclosureLabel` (`:218`), `arcEnclosureLabel` (`:267`),
`anchorTerminal` (`:327`, `:331`), `baseSlot` (`:577`), `hoverOnly` (`:588`). A retained or laddered
label whose leader is empty still keeps its label and carries no leader (design §7.3); no placement
decision may change as a result.

### 2.4 `GraphPainter` — end point and screen-constant stroke

`GraphPainter.java` (same package, no new import: `BasicStroke` is already imported at `:4`):

1. Add the new package-private seam **directly after `paintLabels`** (before `worldX`):

```java
static BasicStroke leaderStroke(final GraphTheme theme, final double zoom) {
    return new BasicStroke((float) (theme.edgeStroke().getLineWidth() / zoom),
        BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
}
```

The concrete return type `BasicStroke` is required by §4.4's `getLineWidth()`; the design's
`Stroke`-typed declaration is equivalent, and this specification pins `BasicStroke` to avoid adding
a `java.awt.Stroke` import. `zoom > 0` is guaranteed by `GraphViewport` (`GraphViewport.java:25-27`),
so the division is safe. No additional validation.

2. In `paintLabels`, compute the stroke **once before the label loop** and draw the carried segment:

```java
final BasicStroke leaderStroke = leaderStroke(theme, viewport.zoom());
for (final PlacedLabel label : labels) {
    ...
    if (label.leader().isPresent()) {
        final LeaderLine line = label.leader().get();
        graphics.setStroke(leaderStroke);
        graphics.draw(new Line2D.Double(
            worldX(line.start().x(), viewport, size), worldY(line.start().y(), viewport, size),
            worldX(line.end().x(), viewport, size), worldY(line.end().y(), viewport, size)));
    }
    ...
}
```

Replacing `GraphPainter.java:247-250` (the `leaderStart` call, the `theme.edgeStroke()` set and the
`Line2D` ending at `anchorX/anchorY`). The font derivation above it (`:241-242`) is unchanged.
Nothing downstream can inherit the stroke: `paintHighlights`/`paintConnectionPreview` run after
`paintLabels` and set their own strokes when they draw (`GraphPainter.java:275-331`, `:333-355`).

### 2.5 Deleted code

`PlacedLabel.leaderStart()`, `ScreenLabelPlacement.leaderStart(...)`, and every test read of
`leaderStart()` — replaced by `leader()`. No parallel old/new path is retained. The removal gate in
§6 requires `grep -rn leaderStart freeplane_plugin_graph/src` to be empty afterwards.

## 3. Fixtures and pinned geometry

### 3.1 Font metrics

All values [M] (Appendix A.5), base theme fonts of `GraphTheme.resolve(CanvasTheme.LIGHT)`:
`h(12) = 16.344114`, `h(9) = 12.258085`, `h(15 bold) = 20.430143` px, and the widths used below.

| text | font | width (px) | height (px) |
|---|---|---|---|
| Axiom of Choice | 12 | 91.104675 | 16.344114 |
| Theorem | 12 | 51.060364 | 16.344114 |
| Replacement Scheme | 12 | 121.344849 | 16.344114 |
| Extensionality | 12 | 78.432541 | 16.344114 |
| Pairing | 12 | 39.936279 | 16.344114 |
| Power Set | 12 | 56.484421 | 16.344114 |
| Infinity | 12 | 39.672272 | 16.344114 |
| Separation | 12 | 61.260422 | 16.344114 |
| Foundation / Regularity | 12 | 132.600922 | 16.344114 |
| Comprehension | 12 | 90.288651 | 16.344114 |
| Well-Ordering | 12 | 79.308578 | 16.344114 |
| Transfinite Induction over Ordinal Numbers | 12 | 246.721756 | 16.344114 |
| Basic Definitions and Theorems | 15 bold | 235.291611 | 20.430143 |
| Axioms | 15 bold | 55.065384 | 20.430143 |
| Axioms | 12 | 41.340302 | 16.344114 |
| ZFC | 12 | 20.676147 | 16.344114 |
| I | 12 | 4.068024 | 16.344114 |

The remaining long-scene 12 pt widths are pinned by the existing tests and reproduce (Appendix A.5).

### 3.2 Trimmed node-leader lengths

**Closed forms.** With `r' = max(2, nodeRadius · zoom)`, `w`/`h` the placed box, and
`u = r' + SLOT_GAP + w/2`, `v = r' + SLOT_GAP + h/2`:

| slot class | trimmed length `|end − S|` |
|---|---|
| `RIGHT`, `LEFT` (axis-aligned near) | `SLOT_GAP − LEADER_CLEARANCE = 3.000000` exactly |
| `RIGHT_FAR`, `LEFT_FAR`, `ABOVE_FAR`, `BELOW_FAR` (axis-aligned far) | `DISPLACED_OFFSET − LEADER_CLEARANCE = 27.000000` exactly |
| `ABOVE_RIGHT`, `ABOVE_LEFT`, `BELOW_RIGHT`, `BELOW_LEFT` (diagonal) | `t·hypot(u, v)` with `t = max(u⁻¹, v⁻¹)·(r' + SLOT_GAP − LEADER_CLEARANCE) − r'/hypot(u,v)`; equivalently the entry is through the face with the **smaller** slot offset |
| `ABOVE`, `BELOW` | no leader |

The diagonal form is the maximum of the two face-entry distances divided by `hypot(u, v)` — i.e. the
x-face entry distance `(r' + 3)/u − r'/hypot(u, v)` and the y-face entry distance
`(r' + 3)/v − r'/hypot(u, v)` — then multiplied back by `hypot(u, v)`. These are *not* the §2.3 slab
parameters `t`: the §2.3 parameter is the entry distance divided by `hypot(u, v)` scaled by
`hypot(u, v)/(hypot(u, v) − r')`, so plugging the expressions below into §2.3's definition gives a
length ~6.7 % short at the smallest `r'`. Use the `t·hypot(u, v)` form of this section, not §2.3's
parameter, when checking the pinned diagonal rows.
`t_x = (r' + 3)/u − r'/hypot(u, v)` and `t_y = (r' + 3)/v − r'/hypot(u, v)` (with
`SLOT_GAP − LEADER_CLEARANCE = 3`). All four rows are **[D]** from C1, C3–C5 and the §2.3 slab
formula.
Every near slot is `≥ 3.0 > MIN_VISIBLE_LEADER` and every far slot is `≥ 27.0`, so no node leader is
dropped by L6 or L1.5 [D]. A corner entry has `distance(end, R)` up to `LEADER_CLEARANCE·√2 =
4.242641`; every fixture row below enters through a face (`distance(end, R) = 3.000000`).

**Per-label values [M]** (Appendix A.6; dense/long scenes with their stand-in, viewport 1128×364,
forced `Axiom of Choice` / `LongNames[0]` respectively). These are the `leader()`-present sets the
new tests iterate; the per-zoom counts are the non-vacuity pins of §4.2.

| zoom | fixture | label | slot | `|end − S|` |
|---|---|---|---|---|
| 0.25 | dense | Axiom of Choice | BELOW_FAR | 27.000000 |
| 0.25 | dense | Theorem | LEFT | 3.000000 |
| 0.25 | dense | Extensionality | RIGHT | 3.000000 |
| 0.25 | dense | Foundation / Regularity | BELOW_FAR | 27.000000 |
| 0.25 | dense | Pairing | BELOW_LEFT | 7.545160 |
| 0.25 | dense | Infinity | BELOW_RIGHT | 7.515335 |
| 0.25 | long | Transfinite Induction over Ordinal Numbers | BELOW_FAR | 27.000000 |
| 1.0 | dense | Theorem | LEFT | 3.000000 |
| 1.0 | dense | Extensionality | RIGHT | 3.000000 |
| 1.0 | dense | Power Set | RIGHT_FAR | 27.000000 |
| 1.0 | dense | Replacement Scheme | ABOVE_RIGHT | 30.645073 |
| 1.0 | dense | Pairing | LEFT | 3.000000 |
| 1.0 | dense | Infinity | BELOW_RIGHT | 12.069780 |
| 1.0 | dense | Separation | LEFT | 3.000000 |
| 1.0 | dense | Comprehension | BELOW_FAR | 27.000000 |
| 1.0 | dense | Well-Ordering | BELOW_RIGHT | 20.802243 |
| 1.0 | long | Axiom Schema of Replacement a… | BELOW_FAR | 27.000000 |
| 1.0 | long | Transfinite Induction… | RIGHT | 3.000000 |
| 1.0 | long | Cardinal Arithmetic u… | LEFT | 3.000000 |
| 1.0 | long | Ultrafilter Lemma and Boolean Pr… | BELOW_FAR | 27.000000 |
| 1.0 | long | Kuratowski Zorn Lem… | RIGHT | 3.000000 |
| 2.0 | dense | Separation | LEFT | 3.000000 |
| 2.0 | dense | Comprehension | ABOVE_RIGHT | 30.355020 |
| 2.0 | long | Transfinite Induction… | RIGHT | 3.000000 |
| 2.0 | long | Ultrafilter Lemma and Boolean Pr… | BELOW_FAR | 27.000000 |
| 2.0 | long | Kuratowski Zorn Lem… | RIGHT | 3.000000 |
| 4.0 | long | Transfinite Induction… | RIGHT | 3.000000 |
| 4.0 | long | Ultrafilter Lemma and B… | BELOW_RIGHT | 58.666739 |
| 4.0 | long | Kuratowski Zorn Lem… | RIGHT | 3.000000 |

Dense at zoom 4 has **no** leader-carrying label (all twelve labels are `ABOVE`/`BELOW`); the long
fixture supplies three. Per-zoom totals over both fixtures: **7 / 14 / 5 / 3** [M], used by §4.2.

`leaderCrossings` on the **trimmed** segments (`start → end`) is **0** for dense z=1, dense z=0.25,
long z=1 and long z=2 [M] (Appendix A.7). This is also forced: `[S, end]` is a sub-segment of
today's `[S, C]`, so a proper crossing of the new segments would be a proper crossing of the old
ones, and the existing zero-crossing pins cannot regress [D].

### 3.3 Enclosure-external fixtures (lane 0 and lane 1)

Hull: square half-extent 50 centred at world `(0,0)` (the existing `enclosureRequest` fixture,
`ScreenLabelPlacementShould.java:996-1047`); placement area 320×320, viewport centre `(0,0)`;
label `"Basic Definitions and Theorems"`, emphatic (15 pt bold, `235.291611 × 20.430143`).
Edge 0 (canonical polygon order) is the screen top edge `(110,110)→(210,110)` with outward normal
`(0,-1)`; lane `k` places the anchor at `h/2 + EXTERNAL_GAP + k·(h + EXTERNAL_GAP)` along the normal
from the edge midpoint, and the leader start is the hull's nearest boundary point (the midpoint).

| fixture | how lane 0 is rejected for lane 1 | anchor (area-centred) | start | end | `|end − S|` | outcome |
|---|---|---|---|---|---|---|
| **lane 0** (existing) | – | `(0.000000, -64.215072)` | `(0.000000, -50.000000)` | `(0.000000, -51.000000)` | `1.000000` | **no leader** (L6: 1.0 < 2.0) |
| **lane 1** (new) | seed obstacle `Rectangle2D.Double(100.0, 84.0, 120.0, 24.0)` in screen space rejects the lane-0 candidate and is clear of lane 1 | `(0.000000, -88.645215)` | `(0.000000, -50.000000)` | `(0.000000, -75.430143)` | `25.430143` | **leader** (`distance(end, R) = 3.000000`) |

All values [M] (Appendix A.6). The lane-0 anchor sits `h/2 + EXTERNAL_GAP = 14.215072` from the
edge, so the box's near face is `4.000000` from the hull edge; minus `LEADER_CLEARANCE` leaves the
`1.000000` stub [D: `4 − 3 = 1`]. Lane 1's near face is `h + 2·EXTERNAL_GAP = 28.430143` from the
edge, minus 3 → `25.430143` [D].

The lane-1 seed `(100, 84, 120, 24)` intersects the lane-0 candidate
(`y ∈ [85.569856, 106.0]`) and not the lane-1 candidate (`y ∈ [61.139713, 81.569857]`, since
`81.569857 < 84`) [M]. The generic external consequence is **conditional** (design §5.6/R1): the
room is `h/2 + EXTERNAL_GAP − (w/2·|n_x| + h/2·|n_y|)`; a horizontal normal with the narrow name `I`
(4.068024 × 16.344114 at 12 pt) gives room `10.138045` → trimmed `7.138045` (**kept**), while `ZFC`
(20.676147 wide) gives room `1.833983 < 3` → **leaderless by L1.5** [M]. The lane-0 fixture above is
the vertical-normal case of the design's consequence.

**Traceability note.** These two `I`/`ZFC` numbers correct the design §5.6 rationale, which quotes the
narrow name's room as `9.25` and `ZFC` as “≈30 px wide”. The design's rule (room
`= h/2 + EXTERNAL_GAP − (w/2·|n_x| + h/2·|n_y|)`) is unchanged and its conclusion (“the outcome is
conditional”) is unchanged; only the illustrative values were approximate. Re-derivation: `h/2 =
8.172057` (15 pt bold `20.430143/2`), `EXTERNAL_GAP = 4`, the probe widths `I = 4.068024` and
`ZFC = 20.676147` (Appendix A.5), horizontal normal so `|n_x| = 1, |n_y| = 0`: `8.172057 + 4 − 4.068024/2
= 10.138045` and `8.172057 + 4 − 20.676147/2 = 1.833983`. §7 records this as the specification's only
numeric divergence from the approved design.

### 3.4 Painted-ink reference values

Mirror of the §4.3 masks against the §3.2/§3.3 fixtures (Appendix A.8; the mirror replicates the
pinned seam bodies, and the implementing lane confirms the same values through the production seam):

| state | fixtures | leader ink pixel centres inside `R` | `overlaps(leaderMask, glyphMask)` labels |
|---|---|---|---|
| post-green (compensated stroke, trimmed ends) | dense + long at 0.25/1/2/4, enclosure lane 1 at 1 | **0** in every cell | **0** in every cell |
| compensation reverted (M2) | dense/long 0.25/1/2 → 0; **long z=4 → 9** (3 each for Transfinite / Ultrafilter / Kuratowski) | 0 at 0.25/1/2; **9 at z=4** | 0 (clause (a) does not fire) |
| trim reverted (M1) | every leader-carrying label at 0.25/1/2/4 | nonzero in every cell (e.g. dense z=0.25: 28 for Axiom of Choice; dense z=1: 28–121 per label) | nonzero for every leader-carrying label, with one exception: the `BELOW_FAR` cell of `Ultrafilter Lemma and Boolean Pr…` at z=1 and z=2 is overlap-free (its stroke lands between glyphs). `Axiom Schema of Replacement a…` at z=1 still overlaps — do not treat it as exempt |

The M2 zoom-4 rows are the measured basis of §5.5: `paintedPx = 7.000`, ink `3.500` px past the
3 px clearance → 0.5 px of coverage inside `R` [M, committed probe `4.00 1.4 7.000 3.500 0.000 3 3.0`
and Appendix A.8]. `Glyph ink` never reaches the leader on the green fixtures: the worst
leader-facing overhang is `0.2578` px (C13) against a `1.5` px painted leader overhang (C12) and a
3 px clearance.

## 4. Test specifications

### 4.1 Migration of every existing `leaderStart` reader

Derived again with `grep -rn leaderStart freeplane_plugin_graph/src` (45 lines; Appendix A.1), not
from the design's list. All edits below are in `ScreenLabelPlacementShould.java` unless marked.

**Production (scaffold commit, §5.1):** `PlacedLabel.java:35, 42, 62, 126-127`;
`ScreenLabelPlacement.java:337, 339, 467, 512, 555` (replaced by the two seams of §2.3);
`GraphPainter.java:247, 249, 250`. All edits are enumerated in §2.2–§2.4.

**Test file (32 lines).** Presence/absence reads become `leader()`; coordinate reads become
`leader().get().start()`.

| Line | Current text | Replacement |
|---|---|---|
| `:260` | `assertThat(theorem.leaderStart()).isPresent();` | `assertThat(theorem.leader()).isPresent();` |
| `:261` | `assertThat(theorem.leaderStart().get().x())...` | `assertThat(theorem.leader().get().start().x())...` |
| `:262` | `assertThat(theorem.leaderStart().get().y())...` | `assertThat(theorem.leader().get().start().y())...` |
| `:265` | `assertThat(powerSet.leaderStart()).isPresent();` | `assertThat(powerSet.leader()).isPresent();` |
| `:266` | `assertThat(powerSet.leaderStart().get().x())...` | `assertThat(powerSet.leader().get().start().x())...` |
| `:267` | `assertThat(powerSet.leaderStart().get().y())...` | `assertThat(powerSet.leader().get().start().y())...` |
| `:270` | `assertThat(replacement.leaderStart()).isPresent();` | `assertThat(replacement.leader()).isPresent();` |
| `:275` | `assertThat(longForced.leaderStart()).isEmpty();` | `assertThat(longForced.leader()).isEmpty();` |
| `:290` | `assertThat(label.leaderStart()).as(...).isEmpty();` | `assertThat(label.leader()).as(...).isEmpty();` |
| `:293` | `assertThat(label.leaderStart()).as(...).isPresent();` | `assertThat(label.leader()).as(...).isPresent();` then insert `assertLeaderClearsBox(label);` |
| `:298` | `LayoutPoint start = label.leaderStart().get();` | `LayoutPoint start = label.leader().get().start();` |
| `:326` | `assertThat(label.leaderStart()).as(...).isEmpty();` | `assertThat(label.leader()).as(...).isEmpty();` |
| `:329` | `assertThat(label.leaderStart()).as(...).isPresent();` | `assertThat(label.leader()).as(...).isPresent();` then insert `assertLeaderClearsBox(label);` |
| `:330` | `LayoutPoint start = label.leaderStart().get();` | `LayoutPoint start = label.leader().get().start();` |
| `:489` | `assertThat(theorem.leaderStart()).isPresent();` | `assertThat(theorem.leader()).isPresent();` |
| `:490` | `assertThat(theorem.leaderStart().get().x())` | `assertThat(theorem.leader().get().start().x())` |
| `:492` | `.isCloseTo(beforePan.leaderStart().get().x() - pan, ...)` | `.isCloseTo(beforePan.leader().get().start().x() - pan, ...)` |
| `:493` | `assertThat(theorem.leaderStart().get().y())...` | `assertThat(theorem.leader().get().start().y())...` |
| `:494` | `.isCloseTo(beforePan.leaderStart().get().y(), ...)` | `.isCloseTo(beforePan.leader().get().start().y(), ...)` |
| `:510` | `... && label.leaderStart().isPresent())` | `... && label.leader().isPresent())` |
| `:770` | `assertThat(label.leaderStart()).isEmpty();` | `assertThat(label.leader()).isEmpty();` |
| `:785` | `assertThat(label.leaderStart()).isEmpty();` | `assertThat(label.leader()).isEmpty();` |
| `:797` | `assertThat(label.leaderStart()).isPresent();` | **green commit:** `assertThat(label.leader()).isEmpty();` |
| `:798-800` | start-coordinate pins of the lane-0 enclosure label (one statement spanning three physical lines: `:799` is the `assertThat(...)` continuation and `:800` its `.isCloseTo(-50.0, …)` terminator, which does not contain the token `leaderStart`) | **green commit:** deleted (moved to §4.5's lane-1 test) |
| `:813` | `assertThat(label.leaderStart()).isEmpty();` | `assertThat(label.leader()).isEmpty();` |
| `:825` | `assertThat(label.leaderStart()).isEmpty();` | `assertThat(label.leader()).isEmpty();` |
| `:856` | `assertThat(enclosure.leaderStart()).isEmpty();` | `assertThat(enclosure.leader()).isEmpty();` |
| `:891` | `assertThat(emphatic.leaderStart()).isEmpty();` | `assertThat(emphatic.leader()).isEmpty();` |
| `:898` | `assertThat(subtle.leaderStart()).isEmpty();` | `assertThat(subtle.leader()).isEmpty();` |
| `:1199` | `... || !label.leaderStart().isPresent()) {` | `... || !label.leader().isPresent()) {` |
| `:1202` | `LayoutPoint start = label.leaderStart().get();` | `LayoutPoint start = label.leader().get().start();` |

**`placesEmphaticEnclosureLabelsExternallyWithALeader` (`:789-800`).** Concrete green-commit edits:
keep `mode`, `anchorX`, `anchorY` pins (`:794-796`); flip `:797` to
`assertThat(label.leader()).isEmpty();`; delete `:798-800` (the whole y-assertion, whose terminator on
`:800` carries no `leaderStart` token — deleting only `:798-799` leaves it dangling and the file does not
compile); do **not** rename the method (the design's
§9.4 migration list references it by name). Its placement pins are the evidence that L6 changes the
leader only, never the label position.

**`leaderCrossings` (`:1196-1214`)** must describe the painted segment:
`:1199 → label.leader().isPresent()`, `:1202 → label.leader().get().start()`, and the segment
endpoint `label.anchorX()/anchorY()` becomes `label.leader().get().end().x()/y()`. Values stay **0**
on every fixture (§3.2), and cannot increase for the structural reason there.

**The two rim assertions** (`assertNodeLeadersAtTheRim` `:281-310`,
`assertRetainedNodeLeadersAtTheRim` `:312-342`) keep their rim/collinearity/forward arithmetic on
`leader().get().start()` and gain the §9.1 assertions by the `assertLeaderClearsBox(label);`
insertions at `:293` and `:329`. Their pins (rim radius, collinearity, forward) are unchanged.
**Commit split:** the accessor migration of this section is the scaffold commit; the two
`assertLeaderClearsBox` insertions are part of the **red** commit (§5.2), because on the scaffold
`end == C` and they would break the scaffold's "existing suite stays green" gate. They add §9.1
coverage to the pan/zoom-retention fixtures at no extra test cost.

**Unchanged pins.** `leader(label, scene, zoom)` (`:1306-1315`) computes
`hypot(anchor − centre)` and never touches the leader; `meanLeader` (`:1317-1328`), `maxLeader`
(`:1330-1344`), `assertBounds` (`:675-694`) and the pinned expectations at `:91-92`, `:207`,
`:253-254`, `:276`, `:694-695` therefore stay valid: `48.046026` / `77.894615` (dense z=1),
`78.6565` / `78.656464` (long), and the `assertBounds` maxima `77.894615 / 138.704698`,
`73.611911 / 118.655013`, `78.656464 / 143.545734`, `86.656464 / 151.533443`. They are confirmed by
running the suite, not by reasoning (§5.1/§5.3). The legacy `inkMask` (`:1413-1442`) and
`keepsPaintedInkSeparatedAcrossTheZoomMatrix` are **not** modified (design §9.2).

### 4.2 `keepsEveryLeaderClearOfItsOwnLabelBox` (new, `ScreenLabelPlacementShould`)

Fixtures: the existing dense and long scenes with their per-zoom stand-in, viewport 1128×364, forced
`Axiom of Choice` / `LONG_NAMES[0]`, at zoom `0.25 / 1.0 / 2.0 / 4.0`; plus the §3.3 enclosure lane-0
and lane-1 fixtures at zoom 1. Assertion order inside the shared helper is **(b) then (a) then (c)
then (d) then (e)**, pinned so that the red phase fails on L1.2 (design §9.0.2) rather than on the
`t < 1` clause.

```java
@Test
public void keepsEveryLeaderClearOfItsOwnLabelBox() {
    double[] zooms = { 0.25, 1.0, 2.0, 4.0 };
    int[] expectedLeaders = { 7, 14, 5, 3 };
    for (int index = 0; index < zooms.length; index++) {
        double zoom = zooms[index];
        int leaders = assertLeadersClear(denseScene(), zoom, standIn(denseScene(), zoom),
            "Axiom of Choice");
        leaders += assertLeadersClear(longScene(), zoom, standIn(longScene(), zoom), LONG_NAMES[0]);
        assertThat(leaders).as("leader-carrying labels at zoom " + zoom)
            .isEqualTo(expectedLeaders[index]);
    }

    Rectangle2D area = area(320.0, 320.0);
    List<PlacedLabel> lane0 = placeEnclosure("Basic Definitions and Theorems", true, area,
        Collections.<SceneNode>emptyList(), RenderingLevel.FULL);
    assertThat(lane0).hasSize(1);
    assertThat(lane0.get(0).mode()).isEqualTo(PlacedLabel.Mode.EXTERNAL);
    assertThat(lane0.get(0).anchorY() - area.getHeight() * 0.5).isCloseTo(-64.215072, within(1e-6));
    assertThat(lane0.get(0).leader()).isEmpty();

    assertLeaderClearsBox(secondLaneEnclosureLabel(area));
}
```

The per-zoom count assertion is the non-vacuity guard required by the §5.4/§5.5 mutation evidence
("reverting only the trim must fail §9.1 and §9.2 at every zoom"): dense alone is leaderless at
zoom 4, the union is not. The counts are pinned in §3.2.

```java
private static int assertLeadersClear(List<SceneNode> scene, double zoom, Rectangle2D standIn,
        String forcedName) {
    Rectangle2D area = area(1128.0, 364.0);
    List<PlacedLabel> placed = place(scene, zoom, area, standIn, forced(forcedName),
        RenderingLevel.FULL, null);
    int leaders = 0;
    for (PlacedLabel label : placed) {
        if (label.mode() == PlacedLabel.Mode.HOVER_ONLY || !label.leader().isPresent()) {
            continue;
        }
        assertLeaderClearsBox(label);
        leaders++;
    }
    return leaders;
}

static void assertLeaderClearsBox(PlacedLabel label) {
    assertThat(label.leader()).as(label.text()).isPresent();
    LeaderLine line = label.leader().get();
    LayoutPoint start = line.start();
    LayoutPoint end = line.end();
    double anchorX = label.anchorX();
    double anchorY = label.anchorY();
    Rectangle2D box = label.bounds();

    // (b) distance(end, R) >= LEADER_CLEARANCE - 1e-9
    double gapX = Math.max(0.0, Math.max(box.getMinX() - end.x(), end.x() - box.getMaxX()));
    double gapY = Math.max(0.0, Math.max(box.getMinY() - end.y(), end.y() - box.getMaxY()));
    assertThat(Math.hypot(gapX, gapY)).as(label.text() + " end clearance")
        .isGreaterThanOrEqualTo(ScreenLabelPlacement.LEADER_CLEARANCE - 1e-9);

    // (a) end lies on [S, C] strictly between them
    double ux = anchorX - start.x();
    double uy = anchorY - start.y();
    double t = ((end.x() - start.x()) * ux + (end.y() - start.y()) * uy) / (ux * ux + uy * uy);
    assertThat(t).as(label.text() + " entry parameter").isStrictlyBetween(0.0, 1.0);
    assertThat(end.x()).as(label.text() + " collinear x").isCloseTo(start.x() + t * ux, within(1e-9));
    assertThat(end.y()).as(label.text() + " collinear y").isCloseTo(start.y() + t * uy, within(1e-9));

    // (c) [S, end] does not meet inflate(R, LEADER_CLEARANCE - 1e-6)
    double slack = ScreenLabelPlacement.LEADER_CLEARANCE - 1e-6;
    Rectangle2D inner = new Rectangle2D.Double(box.getMinX() - slack, box.getMinY() - slack,
        box.getWidth() + 2.0 * slack, box.getHeight() + 2.0 * slack);
    assertThat(inner.intersectsLine(start.x(), start.y(), end.x(), end.y()))
        .as(label.text() + " inner clearance").isFalse();

    // (d) [S, end] does not meet R
    assertThat(box.intersectsLine(start.x(), start.y(), end.x(), end.y()))
        .as(label.text() + " box intersection").isFalse();

    // (e) |end - S| >= MIN_VISIBLE_LEADER
    assertThat(Math.hypot(end.x() - start.x(), end.y() - start.y()))
        .as(label.text() + " visible leader")
        .isGreaterThanOrEqualTo(ScreenLabelPlacement.MIN_VISIBLE_LEADER);
}
```

Tolerances: `1e-9` for the clearance and the collinearity residue, `1e-6` for the inner wrapper
(design §9.1). `Rectangle2D.intersectsLine` tests the rectangle interior; the `1e-6` slack makes any
boundary touch impossible. The exact per-label values this test sees are §3.2/§3.3.

### 4.3 `keepsLeaderInkOutOfItsOwnGlyphInk` (new, `ScreenLabelPlacementShould`)

Fixtures: the same dense/long matrix at zoom `0.25 / 1.0 / 2.0 / 4.0` (mask `1128×364`) plus the
§3.3 lane-1 enclosure fixture at zoom 1 (mask `320×320`). Clause order **(b) then (a)**, pinned for
the same red-phase reason (leader ink at the label centre is guaranteed on the scaffold, glyph
overlap is not).

```java
@Test
public void keepsLeaderInkOutOfItsOwnGlyphInk() {
    GraphTheme theme = GraphTheme.resolve(CanvasTheme.LIGHT);
    for (double zoom : new double[] { 0.25, 1.0, 2.0, 4.0 }) {
        assertPaintedSeparation(theme, denseScene(), zoom, standIn(denseScene(), zoom),
            "Axiom of Choice");
        assertPaintedSeparation(theme, longScene(), zoom, standIn(longScene(), zoom), LONG_NAMES[0]);
    }
    assertEnclosurePaintedSeparation(theme, area(320.0, 320.0));
}

private static void assertPaintedSeparation(GraphTheme theme, List<SceneNode> scene, double zoom,
        Rectangle2D standIn, String forcedName) {
    Rectangle2D area = area(1128.0, 364.0);
    List<PlacedLabel> placed = place(scene, zoom, area, standIn, forced(forcedName),
        RenderingLevel.FULL, null);
    int width = (int) area.getWidth();
    int height = (int) area.getHeight();
    for (PlacedLabel label : placed) {
        if (label.mode() == PlacedLabel.Mode.HOVER_ONLY || !label.leader().isPresent()) {
            continue;
        }
        boolean[] leaderMask = leaderMask(label, theme, zoom, width, height);
        boolean[] glyphMask = glyphMask(label, zoom, width, height);
        Rectangle2D box = label.bounds();
        int inside = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (leaderMask[y * width + x] && box.contains(x + 0.5, y + 0.5)) {
                    inside++;
                }
            }
        }
        assertThat(inside).as(label.text() + " leader ink centres inside the box at zoom " + zoom)
            .isZero();
        assertThat(overlaps(leaderMask, glyphMask))
            .as(label.text() + " leader/glyph ink overlap at zoom " + zoom).isFalse();
    }
}

private static void assertEnclosurePaintedSeparation(GraphTheme theme, Rectangle2D area) {
    PlacedLabel label = secondLaneEnclosureLabel(area);
    int width = (int) area.getWidth();
    int height = (int) area.getHeight();
    boolean[] leaderMask = leaderMask(label, theme, 1.0, width, height);
    boolean[] glyphMask = glyphMask(label, 1.0, width, height);
    Rectangle2D box = label.bounds();
    int inside = 0;
    for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
            if (leaderMask[y * width + x] && box.contains(x + 0.5, y + 0.5)) {
                inside++;
            }
        }
    }
    assertThat(inside).as("enclosure lane-1 leader ink centres inside the box").isZero();
    assertThat(overlaps(leaderMask, glyphMask)).as("enclosure lane-1 leader/glyph ink").isFalse();
}
```

**`leaderMask(label, theme, zoom, width, height)` — exact painting rules.**

```java
static boolean[] leaderMask(PlacedLabel label, GraphTheme theme, double zoom, int width, int height) {
    java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(width, height,
        java.awt.image.BufferedImage.TYPE_INT_ARGB);
    java.awt.Graphics2D graphics = image.createGraphics();
    try {
        graphics.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
            java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING,
            java.awt.RenderingHints.VALUE_RENDER_SPEED);
        java.awt.geom.AffineTransform world = new java.awt.geom.AffineTransform();
        world.translate(width * 0.5, height * 0.5);
        world.scale(zoom, zoom);
        graphics.transform(world);
        graphics.setColor(java.awt.Color.BLACK);
        graphics.setStroke(GraphPainter.leaderStroke(theme, zoom));
        LeaderLine line = label.leader().get();
        graphics.draw(new java.awt.geom.Line2D.Double(
            (line.start().x() - width * 0.5) / zoom, (line.start().y() - height * 0.5) / zoom,
            (line.end().x() - width * 0.5) / zoom, (line.end().y() - height * 0.5) / zoom));
    }
    finally {
        graphics.dispose();
    }
    return inkPixels(image, width, height);
}
```

- Transform: `translate(W/2, H/2) · scale(zoom)` — `GraphPainter.worldTransform`
  (`GraphPainter.java:83-89`) with the fixture's zero viewport centre; the drawn endpoints are the
  production `worldX/worldY` of the screen-space segment.
- Stroke: the **production** seam `GraphPainter.leaderStroke(theme, zoom)`, not a mirror.
- Colour: black (irrelevant to alpha); no dimming, default `SRC_OVER`.
- Hints: exactly the two hints `GraphPainter.paint` sets (`:57-58`).

**`glyphMask(label, zoom, width, height)` — production-faithful glyph mask.**

```java
static boolean[] glyphMask(PlacedLabel label, double zoom, int width, int height) {
    java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(width, height,
        java.awt.image.BufferedImage.TYPE_INT_ARGB);
    java.awt.Graphics2D graphics = image.createGraphics();
    try {
        graphics.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
            java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,
            java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING,
            java.awt.RenderingHints.VALUE_RENDER_SPEED);
        java.awt.geom.AffineTransform world = new java.awt.geom.AffineTransform();
        world.translate(width * 0.5, height * 0.5);
        world.scale(zoom, zoom);
        graphics.transform(world);
        java.awt.Font font = label.font().deriveFont(
            Math.max(1.0f, label.font().getSize2D() / (float) zoom));
        graphics.setFont(font);
        graphics.setColor(java.awt.Color.BLACK);
        Rectangle2D bounds = font.getStringBounds(label.text(), ScreenLabelPlacement.SCREEN_FRC);
        java.awt.font.LineMetrics metrics = font.getLineMetrics(label.text(),
            ScreenLabelPlacement.SCREEN_FRC);
        float baseline = (metrics.getAscent() - metrics.getDescent()) * 0.5f;
        graphics.drawString(label.text(),
            (float) ((label.anchorX() - width * 0.5) / zoom - bounds.getWidth() * 0.5),
            (float) ((label.anchorY() - height * 0.5) / zoom + baseline));
    }
    finally {
        graphics.dispose();
    }
    return inkPixels(image, width, height);
}

static boolean[] inkPixels(java.awt.image.BufferedImage image, int width, int height) {
    boolean[] mask = new boolean[width * height];
    for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
            if ((image.getRGB(x, y) >>> 24) != 0) {
                mask[y * width + x] = true;
            }
        }
    }
    return mask;
}
```

- Font: exactly `label.font().deriveFont(Math.max(1.0f, size / (float) zoom))`, the painter's
  derivation (`GraphPainter.java:241-242`).
- Centring: `getStringBounds(text, SCREEN_FRC)` width and the `LineMetrics` baseline
  `(ascent − descent)/2`, the rule of `GraphPainter.drawCentered` (`:267-273`).
- `KEY_TEXT_ANTIALIASING = VALUE_TEXT_ANTIALIAS_ON` is set for determinism (it matches the legacy
  `inkMask`, `:1417-1420`, and the production default on this host).
- The legacy `inkMask` is **not** modified and not reused; its `FontMetrics` baseline differs by
  `0.156033` px (C16).

### 4.4 `keepsTheLeaderStrokeScreenConstant` (new, `GraphCanvasPaintShould`)

Add `import static org.assertj.core.api.Assertions.within;` to `GraphCanvasPaintShould.java`
(`lightTheme()` already exists at `:948`):

```java
@Test
public void keepsTheLeaderStrokeScreenConstant() {
    GraphTheme theme = lightTheme();
    for (double zoom : new double[] { 0.25, 1.0, 2.0, 4.0 }) {
        assertThat((double) GraphPainter.leaderStroke(theme, zoom).getLineWidth() * zoom)
            .as("leader stroke screen width at zoom " + zoom)
            .isCloseTo(theme.edgeStroke().getLineWidth(), within(1e-6));
    }
}
```

This is the falsifiable form of §1 C7, independent of the ink harness; the expected screen widths are
`1.4` px at every zoom and the world widths are `5.6 / 1.4 / 0.7 / 0.35` [D].

### 4.5 `placesEmphaticEnclosureLabelsExternallyInTheSecondLaneWithATrimmedLeader` and the lane-1 helper (new, `ScreenLabelPlacementShould`)

```java
@Test
public void placesEmphaticEnclosureLabelsExternallyInTheSecondLaneWithATrimmedLeader() {
    Rectangle2D area = area(320.0, 320.0);
    PlacedLabel label = secondLaneEnclosureLabel(area);

    assertThat(label.mode()).isEqualTo(PlacedLabel.Mode.EXTERNAL);
    assertThat(label.anchorX() - area.getWidth() * 0.5).isCloseTo(0.0, within(1e-6));
    assertThat(label.anchorY() - area.getHeight() * 0.5).isCloseTo(-88.645215, within(1e-6));
    assertThat(label.width()).isCloseTo(235.291611, within(1e-6));
    assertThat(label.height()).isCloseTo(20.430143, within(1e-6));
    assertThat(label.leader()).isPresent();
    LayoutPoint start = label.leader().get().start();
    LayoutPoint end = label.leader().get().end();
    assertThat(start.x() - area.getWidth() * 0.5).isCloseTo(0.0, within(1e-6));
    assertThat(start.y() - area.getHeight() * 0.5).isCloseTo(-50.0, within(1e-6));
    assertThat(end.x() - area.getWidth() * 0.5).isCloseTo(0.0, within(1e-6));
    assertThat(end.y() - area.getHeight() * 0.5).isCloseTo(-75.430143, within(1e-6));
    assertThat(Math.hypot(end.x() - start.x(), end.y() - start.y()))
        .isCloseTo(25.430143, within(1e-6));
    assertLeaderClearsBox(label);
}

static PlacedLabel secondLaneEnclosureLabel(Rectangle2D area) {
    LabelPlacementRequest request = enclosureRequest("Basic Definitions and Theorems", true, area,
        Collections.<SceneNode>emptyList(), RenderingLevel.FULL);
    Rectangle2D seed = new Rectangle2D.Double(100.0, 84.0, 120.0, 24.0);
    List<PlacedLabel> placed = new ScreenLabelPlacement().place(request, null, fonts(),
        Collections.singletonList(seed));
    return findEnclosure(placed);
}
```

The lane-0 fixture (existing `placesEmphaticEnclosureLabelsExternallyWithALeader`) keeps its
`anchorY = -64.215072` pin and flips to `leader().isEmpty()`; its `start` pins move here (the start
coordinate is `(0, -50)` in both lanes; the new pin is the trimmed `end`). §3.3 has the numbers.

## 5. Implementation order, evidence and mutation checks

The design's §9.0 order is normative: **scaffold → red → green**, then the two mutation checks.
Do not squash the steps; each has its own observable evidence.

### 5.1 Step 1 — scaffold (behaviour-preserving commit)

Contents: §2.1 `LeaderLine`; §2.2 `PlacedLabel.leader()`; §2.3 `leader(...)`/`nodeLeader(...)`
**returning today's geometry** — the scaffold `leader` body is
`return Optional.of(new LeaderLine(start, LayoutPoint.of(anchorX, anchorY)));` — plus both constants
declared at their final values and unused; §2.4 `leaderStroke` returning the **uncompensated**
`theme.edgeStroke()`; §2.4's `paintLabels` end-point rewrite; §4.1's migration of all 32 test reads
with `placesEmphaticEnclosureLabelsExternallyWithALeader` still asserting
`assertThat(label.leader()).isPresent()`. The two `assertLeaderClearsBox` insertions of §4.1 are
deliberately **not** in the scaffold (they are red-commit work, §5.2).

Observable evidence (all required in the step's report):
1. `gradle :freeplane_plugin_graph:compileJava :freeplane_plugin_graph:compileTestJava` succeeds.
2. `gradle :freeplane_plugin_graph:test` → **BUILD SUCCESSFUL** (the full pre-existing suite is the
   green baseline; baseline measured for this specification: BUILD SUCCESSFUL in 1m 11s, §A.3).
3. `grep -rn leaderStart freeplane_plugin_graph/src` → **no matches**.

### 5.2 Step 2 — red commit

Add §4.2 `keepsEveryLeaderClearOfItsOwnLabelBox`, §4.3 `keepsLeaderInkOutOfItsOwnGlyphInk`, §4.4
`keepsTheLeaderStrokeScreenConstant`, §4.5's
`placesEmphaticEnclosureLabelsExternallyInTheSecondLaneWithATrimmedLeader` +
`secondLaneEnclosureLabel`, the §4.2/§4.3 helpers, and the two `assertLeaderClearsBox` insertions in
the rim helpers (§4.1). No production change.

Observable evidence: `gradle :freeplane_plugin_graph:test --tests '*ScreenLabelPlacementShould'
--tests '*GraphCanvasPaintShould' -PTestLoggingFull` → **BUILD FAILED**; the failures must be
exactly these, and no test outside this list may fail:
- `keepsEveryLeaderClearOfItsOwnLabelBox` — first failure at zoom 0.25 on dense `Axiom of Choice`
  (BELOW_FAR): the scaffold `end == C` gives `distance(end, R) = 0 < LEADER_CLEARANCE − 1e-9`
  (clause (b), design §9.0.2's "L1.2 fails"); the lane-0 absence clause likewise fails.
- `keepsLeaderInkOutOfItsOwnGlyphInk` — first failure on the same label: leader ink at the label
  centre gives `inside > 0` (clause (b)); [N] the exact count in this rasterization — capture it from
  the failure output and require `inside > 0` (the §3.4 M1 mirror measures 28 for this label at
  zoom 0.25).
- `keepsTheLeaderStrokeScreenConstant` — fails at zoom 0.25: `1.4·0.25 = 0.35` against `1.4`.
- `placesEmphaticEnclosureLabelsExternallyInTheSecondLaneWithATrimmedLeader` — fails on the `end`
  pin: the scaffold end is the anchor `(0, -88.645215)`, expected `(0, -75.430143)`.
- the four existing rim-calling tests that gain `assertLeaderClearsBox`:
  `drawsNodeLeadersForEverySlotExceptAboveAndBelow`, `keepsPlacementsStickyAcrossAPan`,
  `recomputesTheLeaderStartOfRetainedLabelsAfterALargePan`,
  `recomputesTheLeaderStartOfRetainedLabelsAfterAZoomChange` — each fails on clause (b) of the first
  leader it checks.
The pre-existing `placesEmphaticEnclosureLabelsExternallyWithALeader` still passes (its flip belongs
to §5.3).

### 5.3 Step 3 — green commit

Implement §2.3's trim/minimum (replace the scaffold body with the slab code), §2.4's compensated
`leaderStroke`, and flip `ScreenLabelPlacementShould.java:797-800` per §4.1 (the whole two-line y-assertion,
including the `:800` terminator that carries no `leaderStart` token). No other change.

Observable evidence:
1. `gradle :freeplane_plugin_graph:test --tests '*ScreenLabelPlacementShould' --tests
   '*GraphCanvasPaintShould'` → BUILD SUCCESSFUL.
2. `gradle :freeplane_plugin_graph:test` → BUILD SUCCESSFUL (all pins of §4.1, including
   `meanLeader`/`maxLeader`/`assertBounds`, still hold).
3. `gradle test` (full repository) → BUILD SUCCESSFUL (design §9.5).
4. `grep -rn leaderStart freeplane_plugin_graph/src` → no matches.
5. The §3.4 post-green column: every fixture reports `inside = 0` and no overlap; the §3.2 counts
   `7 / 14 / 5 / 3` hold.

### 5.4 Mutation check M1 — revert the trim only

Temporary edit (never committed): make `leader(...)` return `end = C`, i.e. the scaffold body of
§5.1; leave the constants, the compensated `leaderStroke` and the flipped lane-0 pin in place.
(The scaffold body also drops the L6 check; with `end = C` the surviving length is large, so L6
would pass trivially anyway.)

Run `gradle :freeplane_plugin_graph:test --tests '*ScreenLabelPlacementShould' --tests
'*GraphCanvasPaintShould' -PTestLoggingFull`. Must fail, and the §3.4 M1 column is the expected
shape:
- `keepsEveryLeaderClearOfItsOwnLabelBox` fails at **every** zoom (0.25/1/2/4): clause (b)
  `distance(end, R) = 0`, clause (a) `t = 1` / the collinearity residue, and the lane-0 absence
  clause (the un-trimmed lane-0 leader is present again).
- `keepsLeaderInkOutOfItsOwnGlyphInk` fails at **every** zoom: clause (b) `inside > 0` (dense
  z=0.25 `Axiom of Choice` = 28 in the mirror; dense z=1 28–121 per label) and clause (a) overlaps
  fire for all leaders except one: the `BELOW_FAR` cell of `Ultrafilter Lemma and Boolean Pr…` at z=1
  and z=2 is overlap-free because its un-trimmed stroke lands between glyphs. The other long
  `BELOW_FAR` label, `Axiom Schema of Replacement a…` at z=1, **does** overlap — do not treat it as
  exempt, and the z=4 long leaders also overlap.
- The four existing rim-calling tests of §5.2 fail at their first §9.1 assertion.
- `placesEmphaticEnclosureLabelsExternallyInTheSecondLaneWithATrimmedLeader` fails on the `end` pin;
  the flipped `placesEmphaticEnclosureLabelsExternallyWithALeader` fails because its lane-0 leader is
  present again.
- `keepsTheLeaderStrokeScreenConstant` passes (the compensation is untouched).
Then revert and re-run to confirm green.

### 5.5 Mutation check M2 — revert the compensation only

Temporary edit (never committed): `GraphPainter.leaderStroke(theme, zoom)` returns
`theme.edgeStroke()` (the uncompensated stroke); leave the trim and `MIN_VISIBLE_LEADER` in place.

Run the same command. Must fail:
- `keepsTheLeaderStrokeScreenConstant` fails at **0.25 / 2 / 4** (`0.35`, `2.8`, `5.6` against `1.4`;
  zoom 1 passes trivially).
- `keepsLeaderInkOutOfItsOwnGlyphInk` fails at **zoom 4 only**: the uncompensated stroke paints
  `7.000` px with `3.500` px overhang against the 3 px clearance, so clause (b) fires for the three
  long-fixture leaders (`inside = 3` each, `9` total in the mirror, §3.4); clause (a) does not fire.
  Zooms 0.25/1/2 pass (0 inside).
- `keepsEveryLeaderClearOfItsOwnLabelBox` passes (geometry is untouched).
Then revert and re-run to confirm green.

## 6. Acceptance and validation vectors

| # | command | artefact | must be true |
|---|---|---|---|
| V1 | `gradle :freeplane_plugin_graph:compileJava :freeplane_plugin_graph:compileTestJava` | main + test sources | exit 0; no new compile warnings caused by unused imports or unused constants |
| V2 | `gradle :freeplane_plugin_graph:test --tests '*ScreenLabelPlacementShould' --tests '*GraphCanvasPaintShould'` | the two specified classes | BUILD SUCCESSFUL; the four new methods of §4.2–§4.5 pass; every migrated pin of §4.1 (`meanLeader 48.046026`, `maxLeader 77.894615`, `maxLeader 78.656464`, `assertBounds` maxima, `leaderCrossings == 0`) still passes; `keepsPaintedInkSeparatedAcrossTheZoomMatrix` still passes unchanged |
| V3 | `gradle :freeplane_plugin_graph:test` | the whole plugin module (incl. `GraphWorkspaceModelAcceptanceShould`) | BUILD SUCCESSFUL; no test outside the named files changes behaviour |
| V4 | `gradle test` | full repository | BUILD SUCCESSFUL before the implementation audit (design §9.5) |
| V5 | `grep -rn leaderStart freeplane_plugin_graph/src` | removal gate | **no output** |
| V6 | `grep -rn "LEADER_CLEARANCE\|MIN_VISIBLE_LEADER" freeplane_plugin_graph/src/main` | constants gate | exactly the two declarations plus their §2.3 uses; both values `3.0` / `2.0`. The ordering `LEADER_CLEARANCE + MIN_VISIBLE_LEADER ≤ SLOT_GAP` (C11) is **not** readable from this grep; it is enforced behaviourally by `keepsEveryLeaderClearOfItsOwnLabelBox` (near-slot leaders must exist and be at least `MIN_VISIBLE_LEADER` long) and by the existing rim-presence assertions `ScreenLabelPlacementShould.java:293` / `:329` |
| V7 | M1 (§5.4) | mutation evidence | fails `keepsEveryLeaderClearOfItsOwnLabelBox` and `keepsLeaderInkOutOfItsOwnGlyphInk` at 0.25/1/2/4; passes `keepsTheLeaderStrokeScreenConstant` |
| V8 | M2 (§5.5) | mutation evidence | fails `keepsTheLeaderStrokeScreenConstant` at 0.25/2/4 and `keepsLeaderInkOutOfItsOwnGlyphInk` at 4; passes `keepsEveryLeaderClearOfItsOwnLabelBox` |

## 7. Resolutions of the design's §12 open points

| design §12 open point | resolution |
|---|---|
| Exact slab-clipping formula and numeric tolerance | §2.3: the max-of-near-face-crossings formula, `end = S + t·(C−S)`, exact comparisons, and the proof that no epsilon is needed (boundary cases fall to L6). Test tolerances `1e-9` / `1e-6` as in design §9.1(c). |
| Exact definition of "strictly between `S` and `C`" | §0/§2.3: `0 < t < 1` for the entry parameter `t`; the stored segment is `[S, end]`. |
| `LeaderLine` validation messages | §2.1, verbatim. |
| Enumeration of §9.4's migrated lines | §4.1: all 45 `grep` lines (32 test, 13 production), with the green-only flip of `:797-800` (`:800` completes the y-assertion whose `:799` line pairs with it). |
| The new enclosure-external fixture geometry and pinned numbers | §3.3 / §4.5: hull ±50, area 320×320, lane-1 anchor `(0, -88.645215)`, start `(0, -50)`, end `(0, -75.430143)`, `|end − S| = 25.430143`, seed obstacle `(100, 84, 120, 24)`. |
| Mask helper signatures and the zoom matrix | §4.3: `leaderMask(label, theme, zoom, width, height)` / `glyphMask(label, zoom, width, height)`, the mask frame, fonts, transform, baseline and alpha threshold; zoom matrix `0.25/1/2/4` (C15). |
| Validation vectors for the implementation lanes | §6 V1–V8, plus the per-step evidence of §5. |
| Numeric divergences from the approved design | one, recorded in §3.3: the design §5.6 rationale quotes the narrow name `I`'s enclosure room as `9.25` and `ZFC` as “≈30 px wide”; the re-derived values are `10.138045` and `20.676147` (room `1.833983`). The design's rule and its conditional conclusion are unchanged, so the design's contract is not modified — only its illustrative arithmetic is corrected here. |

## Appendix A — Verification log

Environment: Gradle 9.0.0 (`gradle --version`: launcher JVM **21.0.8**, Azul 21.0.8+9-LTS), plugin
tests on the worktree's `build/` outputs, headless. Standalone probes were run outside the repository
(`/tmp/lc-spec`); no repository file other than this specification and the pre-existing committed
artefacts was written. Probe sources are **not committed**; measurements attributed to them are
reproducible with the listed commands.

1. **`grep -rn leaderStart freeplane_plugin_graph/src`** (45 lines) — 32 in
   `src/test/java/org/freeplane/plugin/graph/canvas/ScreenLabelPlacementShould.java` at lines
   260, 261, 262, 265, 266, 267, 270, 275, 290, 293, 298, 326, 329, 330, 489, 490, 492, 493, 494,
   510, 770, 785, 797, 798, 799, 813, 825, 856, 891, 898, 1199, 1202; 5 in `PlacedLabel.java`
   (35, 42, 62, 126, 127); 5 in `ScreenLabelPlacement.java` (337, 339, 467, 512, 555); 3 in
   `GraphPainter.java` (247, 249, 250). No reference outside `org.freeplane.plugin.graph.canvas`.
   This supersedes the design's §9.4 ranges and includes `:275`.
2. **Compilation** — `gradle :freeplane_plugin_graph:compileTestJava -q`: exit 0 (38.5 s).
3. **Baseline test runs** —
   `gradle :freeplane_plugin_graph:test --tests '*ScreenLabelPlacementShould' --tests '*GraphCanvasPaintShould'`
   → BUILD SUCCESSFUL (20 s); `gradle :freeplane_plugin_graph:test` → BUILD SUCCESSFUL (1 m 11 s).
4. **Input hashes** —
   design `a6eadc3bee1f32e7a63733067cd4d0a20a90d262d4bbbe0c81549cca4cb6197a`;
   `leader-clearance-probe.txt` `de9cb7f143c3c929c574a11a4672c63c530f2836d970915ae9bb612222a2959a`
   (matches the design's quoted hash); `LeaderClearanceProbe.java`
   `721f78323ed3d28f6258b468a3bc7a29807d724a3c982be53533141427c3bb45`;
   `LabelLeaderClearanceMockups.java`
   `8ee21c3223e02f1c986f72141be1dc7265ec7bf9dae191e79c0cd11627831c1e`;
   `label-leader-clearance.png`
   `faa146a907f56596c7e89c6bddc8925327d7d7bce37ee886c2cae8b8f5563579`.
5. **Font probe** (`FontSpecProbe.java`, compiled against `build/classes/java/{main,test}` and the
   Gradle test runtime classpath; `/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin/java
   -Djava.awt.headless=true`, classpath from the `printTestRuntimeCp` init script):
   `h(12)=16.344114`, `h(9)=12.258085`, `h(15 bold)=20.430143`; all §3.1 widths; `FontMetrics` vs
   `LineMetrics` full-12 baseline delta `-0.156033`; `Font.SANS_SERIF` and `Font.DIALOG` agree on
   every fixture width on this host.
   Exact command forms used for every probe in items 5–8:
   `gradle -I /tmp/cp-init.gradle :freeplane_plugin_graph:printTestRuntimeCp -q | sed 's/^TESTCP=//' > /tmp/graph-test-cp.txt`;
   `CP=$(cat /tmp/graph-test-cp.txt) && /home/henry/.sdkman/candidates/java/21.0.8-zulu/bin/javac
   -nowarn -cp "$CP:/tmp/lc-spec/classes" -d /tmp/lc-spec/classes /tmp/lc-spec/<Probe>.java`;
   `/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin/java -Djava.awt.headless=true -cp
   "/tmp/lc-spec/classes:$CP" org.freeplane.plugin.graph.canvas.<Probe>`.
6. **Trim probe** (`LeaderTrimProbe.java` + `SpecTableProbe.java`, same classpath; the
   `LeaderLine`/trim is mirrored from §2.3, placement and slots come from the production code):
   - every axis-aligned near leader `|end − S| = 3.000000`, every axis-aligned far leader
     `27.000000`, `distance(end, R) = 3.000000` for every fixture label;
   - the diagonal values of §3.2 (`7.545160`, `7.515335`, `30.645073`, `12.069780`, `20.802243`,
     `30.355020`, `58.666739`);
   - enclosure lane 0 `end = (0, -51.000000)`, `|end − S| = 1.000000` → dropped; lane 1 anchor
     `(0, -88.645215)`, `end = (0, -75.430143)`, `|end − S| = 25.430143` → kept.
7. **Crossing probe** (`CrossingProbe.java`): trimmed-segment proper crossings dense z=1 → 0,
   dense z=0.25 → 0, long z=1 → 0, long z=2 → 0.
8. **Painted mirror probe** (`PaintedMutantProbe.java`, replicates the §4.3 masks and the §2.3/§2.4
   seam bodies): post-green `inside = 0`, `overlapLabels = 0` for every fixture/zoom; M2
   (uncompensated) long z=4 → `inside = 3` each for `Transfinite Induction…`, `Ultrafilter Lemma and
   B…`, `Kuratowski Zorn Lem…` (`9` total), `overlapLabels = 0`; M1 (untrimmed) nonzero `inside` and
   overlaps at 0.25/1/2/4 (dense z=0.25 `Axiom of Choice` `inside = 28`, z=1 per label 28–121).
   These are mirrors; §5.4/§5.5 are the production commands that must reproduce the failure classes.
9. **Repository citations.** All `file:line` values in §0–§5 re-checked with `grep -n`/`sed` on the
   day of writing, including the corrected `halfNormal` citation (`ScreenLabelPlacement.java:284-285`,
   not the design's `:283-284`) and the unchanged `ScreenLabelPlacement.java:28-31`.
10. **Unpinnable items [N].** The exact nonzero `inside`/overlap counts under the red scaffold and
    under M2 in the production rasterization: captured by the §5.2/§5.4/§5.5 runs with
    `-PTestLoggingFull` and
    compared against `> 0` (and against the §3.4 mirror values `28` at dense z=0.25 and `3` per
    long-fixture label at z=4). The full-repository `gradle test` (V4) was **not** run for this
    specification — only `:freeplane_plugin_graph:test` was, per the authoring scope — so its
    baseline is likewise deferred to the implementation lane. No other value in this specification
    is deferred.
