# FRONTIER specification: graph-edge-length-and-label-connector

Status: implementation contract dated 2026-10-23. This specification authorizes only the production edits in A and the test/evidence changes needed to verify them. It does not implement those edits.

## A — Authority, scope and exact production diff

Amended under epoch 2 (2026-10-23): the relationship gap is `PREFERRED_EDGE_GAP = 20.0`, a new
constant strictly below the untouched ring constant `PREFERRED_NODE_GAP = 24.0`; every derived number
below is computed for 20 wu. The design's section H records the measured reason.

The sole design authority is [the approved design](2026-10-23-graph-edge-length-and-label-connector-design.md), approved on 2026-10-23, amended under epoch 2, and present at `d1e23895e9`. The epoch-2 amendment (design section H) supersedes the epoch-1 gap of 24 wu and is authoritative where the two disagree. Its decisions are closed. This specification turns those decisions into implementation and verification contracts; it does not reopen alternatives. Source locations below are the delivery-base locations at `448c0d679d2b1d46563eaf079352e1eefb254305`. The integration head has no production or test changes relative to that base, so the listed source anchors identify the same code.

Production paths are relative to `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/`. Test paths are relative to `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/`. Layout distances are world units (wu); screen measurements are pixels (px).

| Item | Exact contract | Authority / baseline source |
| --- | --- | --- |
| Production-file allowlist | Change exactly `layout/graphstream/GraphStreamLayoutEngine.java` and `canvas/GraphPainter.java` under `src/main`. No other `src/main` file, module, public API, setting, or configuration changes. | Approved design, C1-C3; delivery base `448c0d679d` |
| Relationship rest length | In `GraphStreamLayoutEngine.topology()`, use `radius(first) + radius(second) + PREFERRED_EDGE_GAP` only when both projected endpoints are nodes. Otherwise retain `radius(first) + radius(second) + NodeSeparationProjection.MIN_GAP`. **Add** `static final double PREFERRED_EDGE_GAP = 20.0;` beside the existing `PREFERRED_NODE_GAP` at `GraphStreamLayoutEngine.java:42`. `PREFERRED_NODE_GAP` stays `24.0` and keeps its one existing use in `contentRingRadius`; `MIN_GAP` remains `6.0`. The two constants are deliberately separate so `PREFERRED_EDGE_GAP < PREFERRED_NODE_GAP` is an explicit, testable inequality. | `GraphStreamLayoutEngine.java:41-42,236-244`; design C1 and H |
| Arrowhead constants and rule | Replace `ARROW_HEAD_WORLD_LENGTH` with `ARROW_HEAD_RATIO = 0.30`, `MIN_ARROW_HEAD_WORLD_LENGTH = 4.0`, `MAX_ARROW_HEAD_WORLD_LENGTH = 12.5`, and `MIN_ARROW_HEAD_AVAILABILITY = 8.0`. Keep `ARROW_HEAD_HALF_BASE_RATIO = 0.42`. Pure helper length is `clamp(0.30 * available, 4.0, 12.5) * zoom`. `paintArrow` suppresses the head when availability is `< 8.0` wu; it does not clip head length to availability. Keep the bidirectional half-distance and bow handling. Delete `arrowHeadLengthPx`. | `GraphPainter.java:38-42,162-210`; C2 |
| Leader stroke and paint | Every node and enclosure leader uses `theme.mutedColor()` and the dotted, round-capped stroke defined in C3 below. Restore `theme.labelColor()` before drawing every label's text. Change the helper signature to `leaderStroke(zoom)`; remove its theme argument. | `GraphPainter.java:268-300`; C3 |
| Untouched behavior | Do not change containment/hierarchy rest lengths, node radii, prominence, hull or label geometry, slot selection, path routing, relationship direction/multiplicity, pin behavior, persistence, hit testing, or zoom policy. No compatibility path or user setting. | Approved design C1-C3; source anchors in this table |

The exact C1 expression replacement at `GraphStreamLayoutEngine.java:242-243` is:

```java
// Before
radiusOf(first, radiusById) + radiusOf(second, radiusById)
    + NodeSeparationProjection.MIN_GAP));

// After
radiusOf(first, radiusById) + radiusOf(second, radiusById)
    + (edge.first().isNode() && edge.second().isNode()
        ? PREFERRED_EDGE_GAP : NodeSeparationProjection.MIN_GAP)));
```

Do not modify the existing `PREFERRED_NODE_GAP` declaration. The relationship endpoint radii remain those in `radiusById`, including node disc radius or enclosure content-ring radius.

The exact `GraphPainter` class-level constant block at lines 38-42 becomes:

```java
// World units.
private static final double ARROW_HEAD_RATIO = 0.30;
private static final double MIN_ARROW_HEAD_WORLD_LENGTH = 4.0;
private static final double MAX_ARROW_HEAD_WORLD_LENGTH = 12.5;
private static final double MIN_ARROW_HEAD_AVAILABILITY = 8.0;
// Head base is 0.84 x length; the aspect is preserved when the cap shrinks a head.
private static final double ARROW_HEAD_HALF_BASE_RATIO = 0.42;
private static final double MIN_STROKE_WORLD = 1e-6;
```

At `GraphPainter.java:162-180`, delete `arrowHeadLengthPx` and replace the effective-length helper with the pure clamp below. Keep `availableArrowHeadWorldLength` unchanged and keep `arrowHeadHalfBasePx` at the same 0.42 ratio.

```java
// Screen px.
static double effectiveArrowHeadLengthPx(final double zoom, final double availableWorldLength) {
    return Math.max(MIN_ARROW_HEAD_WORLD_LENGTH,
        Math.min(MAX_ARROW_HEAD_WORLD_LENGTH, ARROW_HEAD_RATIO * availableWorldLength)) * zoom;
}
```

At `GraphPainter.java:196-199`, add the availability decision after calculating the existing `availableWorldLength` and before calculating the head length:

```java
if (availableWorldLength < MIN_ARROW_HEAD_AVAILABILITY) {
    return;
}
final double arrowLengthPx = effectiveArrowHeadLengthPx(zoom, availableWorldLength);
```

This early return skips only the filled head; `paintEdges` has already drawn the complete polyline. Keep `arrowHeadHalfBasePx(arrowLengthPx)`, the unit-vector/perpendicular calculation, and fill operation unchanged. Do not put suppression inside `effectiveArrowHeadLengthPx`.

In `paintLabels`, keep the current per-label dimming and alpha behavior. Set `theme.mutedColor()` immediately before drawing the optional leader; set `theme.labelColor()` immediately before `drawCentered`. The relevant sequence is:

```java
if (label.leader().isPresent()) {
    final LeaderLine line = label.leader().get();
    graphics.setColor(theme.mutedColor());
    graphics.setStroke(leaderStroke);
    graphics.draw(new Line2D.Double(
        worldX(line.start().x(), viewport, size), worldY(line.start().y(), viewport, size),
        worldX(line.end().x(), viewport, size), worldY(line.end().y(), viewport, size)));
}
graphics.setColor(theme.labelColor());
drawCentered(graphics, font, label.text(), anchorX, anchorY);
```

Replace `leaderStroke(final GraphTheme theme, final double zoom)` at `GraphPainter.java:297-300` with:

```java
static BasicStroke leaderStroke(final double zoom) {
    final float width = (float) Math.max(1.0 / zoom, MIN_STROKE_WORLD);
    final float dashOn = (float) Math.max(1.2 / zoom, MIN_STROKE_WORLD);
    final float dashOff = (float) Math.max(2.8 / zoom, MIN_STROKE_WORLD);
    return new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10.0f,
        new float[] { dashOn, dashOff }, 0.0f);
}
```

Clamp each world-space float input before conversion. For accepted zooms in `[1e-6, 1e6]`, the stroke is 1.0 px wide and its dash pitch is 4.0 px. Outside that band the clamp intentionally changes the screen-space width/pitch; assert constructibility rather than 1.0 px. At zoom `1e46`, all three values remain nonzero after conversion to float. Do not change `LeaderLine`, `ScreenLabelPlacement`, or connector geometry.

**Required arithmetic:** `0.30 * 20 = 6.0`; `0.84 * 6.0 = 5.04`; `5.04 / 16 = 0.315`; `4.0 / 0.30 = 13.333...`; `12.5 / 0.30 = 41.666...`; `2 * 41.666... = 83.333...`; `20 * 1.63 = 32.6`; `0.84 * 4.0 = 3.36`. The superseded epoch-1 figures at a 24 wu run were `0.30 * 24 = 7.2`, base `6.048`, `0.378` of the disc; they appear nowhere as a requirement. Test branch boundaries using the fractions `4.0 / 0.30` and `12.5 / 0.30`, not rounded decimal substitutes.

C1's ring-bound invariant is local: for two direct nodes in the same single enclosure, `rest = r1 + r2 + 20 <= 2 * maxRadius + 20 < 2 * maxRadius + 24`, the adjacent-ring chord. With radii 8 and 14, rest is 42 wu and the chord is 52 wu; the margin is `2*max(r1,r2) + 24 - (r1+r2+20) = |r1-r2| + 4 >= 4`. Do not claim the bound for cross-enclosure, cross-map, unenclosed, multi-membership, pinned, node-to-anchor, or settled pairs. A node-to-enclosure or enclosure-to-enclosure relationship keeps additive `MIN_GAP`; adding the relationship gap there would double-count the content-ring spacing.

## B — Test changes and invariant coverage

All test additions and edits stay under `freeplane_plugin_graph/src/test/` and use `*Should` class names. Test paths in the table are relative to the graph module's test source root. Do not add production accessors to facilitate tests.

| Test file / existing test | Required change and numeric oracle |
| --- | --- |
| `canvas/GraphCanvasPaintShould.java`: `keepsTheLeaderStrokeScreenConstant` | Replace comparison with `theme.edgeStroke()` by `lineWidth * zoom == 1.0` and dash entries times zoom equal `{1.2, 2.8}` (within `1e-6`) for zooms in the unclamped band. |
| `canvas/GraphCanvasPaintShould.java`: `paintsTheLeaderStrokeScreenConstantThroughTheProductionCallSite` | At the production paint call site, detect `mutedColor`-near pixels along the whole leader segment, allowing dash gaps; verify the label text remains `labelColor`-near. Do not count a single pixel column, which may land in a gap. |
| `canvas/GraphCanvasPaintShould.java`: arrow helper probes around lines 1398-1405 | Assert `(zoom=.6, availability=6)` returns `2.4 px`, from `clamp(1.8,4,12.5)*.6`; `(1.0,3)` returns `4.0 px`, not 3.0. Assert those are helper values only and that paint suppresses the head below 8 wu. Keep half-base expectations attached to painted lengths. Cover availabilities `{3, 6, 7.99, 8, 4/0.30, 24, 12.5/0.30, 80}` at zooms `{0.25, 1, 1.63, 4}`, single and double headed. |
| `canvas/GraphCanvasPaintShould.java`: `arrowHeadLengthIsProportionalToZoom` | Delete this unannotated but compiling helper because it calls the removed `arrowHeadLengthPx`; do not leave a legacy entry point or a dead test. |
| `canvas/GraphCanvasPaintShould.java`: `paintedArrowHeadCapBaseAndBidirectionalSplitAtPaintLevel` | Replace the 6 wu default `capFixture` expectations (now no head) with a 9 wu single-headed availability and an 18 wu bidirectional separation (9 wu availability per head). At zoom 1, each painted head is 4.0 wu and its full base is 3.36 wu; the below-floor control has no head and leaves its full line. Remove the old 30 px/12.5-over-8.68 crossover assertions and recalculate any pixel sampling around the new endpoint. Keep a separate zoom-4 pixel oracle, where a 4 wu head spans 16 px. |
| `canvas/GraphCanvasPaintShould.java`: `capsTheArrowheadAtTheTerminalSegmentOfARoutedPath` | Keep the routed-path-versus-chord distinction. Move the bow control from `(82, 7)` to `(82, 9)` for the painted-head case; add a 7 wu terminal-availability case asserting no head and the full polyline. |
| `canvas/GraphCanvasPaintShould.java`: paint-level arrow tests | For every availability below 8 wu, assert the painted path is the polyline only. At 8 wu assert a 4 wu head, half-base `0.42 * 4 = 1.68 wu` (full base 3.36 wu), and at least 4 wu of uncovered shaft. For all painted availability values assert head length `<= 0.50 * availability` and full base `0.84 * headLength >= 1.4 wu`. Pixel-length oracle is permitted only at zoom >= 2; short heads at zoom 1 are asserted as shape/world-unit geometry. |
| `canvas/ScreenLabelPlacementShould.java`: `leaderMask` around line 1736 | Call `GraphPainter.leaderStroke(zoom)` with no theme parameter. Keep the existing leader geometry assertions byte-for-byte in meaning. |
| `layout/graphstream/InitialSpacingConsumers.java`: `topology` | The shared helper receives the **ring** gap and must also be told the relationship gap, because the two are now different constants. Change the signature to `topology(projection, engine, ringGap, edgeGap)` and assert `first + second + (bothEndpointsAreNodes ? edgeGap : 6.0)`; the containment assertions keep using `ringGap`. `InitialSpacingProbe` passes `ringGap = 6.0, edgeGap = 6.0` for C0 and `ringGap = 24.0, edgeGap = PREFERRED_EDGE_GAP = 20.0` for the candidate. Reusing the ring gap for the relationship row produces `8 + 8 + 24 = 40` against a production `36` and fails five seed assertions plus D1, which is exactly what the epoch-2 materialisation measured. |
| `layout/graphstream/InitialSpacingSeedShould.java` | Update node-to-node relationship assertions from `r1 + r2 + 6.0` to `r1 + r2 + 20.0` for the candidate, and keep `r1 + r2 + 6.0` as the C0 expectation; preserve enclosure-endpoint `+6.0`, `MIN_GAP == 6.0`, ring, and `contentRingRadius` assertions. Add direct node/node, node/enclosure (`matrix(..., "ne")`), and enclosure/enclosure (`matrix(..., "ee")`) checks so no endpoint-type row is vacuous. |
| `layout/graphstream/InitialSpacingAcceptanceShould.java` and `src/test/resources/initial-spacing/run-initial-spacing.py` | Apply the frozen-harness amendment in C below. Keep the C0 arm and its limits unchanged. |
| `layout/graphstream/GraphEdgeLengthSettleShould.java` (new `*Should` class) | Implement the D2 fresh-relaxation comparison against delivery base `448c0d679d`, with all fixtures and receipts in D below. It must exercise a relationship edge for every new relationship-bearing fixture and must report I14 pairs without asserting that settled distance equals rest length. |

The implementation test matrix shall cover all invariants. I5a-I5c are three subclaims of I5, not new invariant IDs.

| Id | Required assertion and test home |
| --- | --- |
| I1 | Every node/node relationship rest is `r1 + r2 + 20.0`, from the actual engine topology, and the constant used is `PREFERRED_EDGE_GAP`, not `PREFERRED_NODE_GAP`. Also assert `PREFERRED_EDGE_GAP < PREFERRED_NODE_GAP`. Cover default and mixed-prominence nodes and the cross-map node/node edge. `InitialSpacingSeedShould` / `InitialSpacingConsumers`. |
| I2 | A relationship with at least one enclosure endpoint keeps `r1 + r2 + 6.0`; exercise both `matrix(..., "ne")` and `matrix(..., "ee")`. Do not cite the `cross` fixture for this, since its relationships are node/node. |
| I3 | Same-enclosure direct node pairs satisfy `rest <= 2 * maxRadius + 20.0`; include a four-node ring and radii 8/14 (rest 42 against chord 52). Exclude cross-enclosure, cross-map, multi-membership, pinned, and unenclosed pairs from this proof. |
| I4 | Containment and hierarchy link rest lengths are identical to delivery-base formulas; assert on topology rather than inferring from a settled frame. |
| I5a | A requested head is painted only when terminal availability is at least 8.0 wu; at exactly 8.0 it is painted with 4.0 wu length. At 3, 6, and 7.99, assert no head and full polyline. |
| I5b | For every painted head, length is at most half the available run, leaving at least half a shaft uncovered. The below-8 case is a plain line, so the whole run is uncovered. Scope this assertion to available run >= 8 for a painted head. |
| I5c | At the 4.0 wu floor, full base is 3.36 wu, greater than the 1.4 px/world stroke width; assert `0.84 * length >= edgeStroke.getLineWidth()` in world geometry. |
| I6 | At availability `12.5 / 0.30`, the helper returns 12.5 wu; above it, the cap remains 12.5 wu. For a bidirectional edge, use separation `2 * (12.5 / 0.30) = 83.333... wu`. |
| I7 | Half-base remains `0.42 * headLength`; heads remain filled shapes, never stroked. |
| I8 | On zoom `[1e-6, 1e6]`, leader width is 1.0 screen px and dash pitch is 4.0 px with entries 1.2 and 2.8 px. At extreme accepted zoom `1e46`, construction succeeds and float width/dash entries are nonzero. Do not assert 1 px outside the unclamped band. |
| I9 | Pixel probes show every leader (node and enclosure) in `mutedColor` and every label in `labelColor`, with paint restored before text. Cover light/dark theme, over a hull fill, and dim-unrelated alpha. |
| I10 | `PlacedLabel.leader()` start/end and the `ScreenLabelPlacement` placement outputs are unchanged; do not modify leader geometry to satisfy a paint test. |
| I11 | Fixed render fixture below: two default nodes at center distance 36 wu, edge attachment run 20 wu, zoom 1.63, and an axis-aligned `ABOVE_FAR` label. Assert 32.6 px edge run exceeds 27 px connector. A separate `RIGHT`/`LEFT` sample is 3 px. This is a fixed render claim, not a settled-layout claim. |
| I12 | D1 C0 sources, `nn = 22.0`, C0 pins, control accounting and every operational limit remain frozen. Only candidate overlays, candidate `nn = 36.0`, and candidate settling pins may change. |
| I13 | Fixed wide-label placement fixture below at zoom 1.63: record the longest connector over all placed labels, its measured pixel length, and its slot name. Assert only that a finite length and slot are reported; never assert it is shorter than the edge run. |
| I14 | For every D2 fixture and every relationship pair, record settled center-to-center distance and resulting rim gap from candidate and delivery base. Report lopsided and sub-6.0 wu pairs as findings. Do not assert settled distance equals `restLength`. |

**I11 fixed render fixture.** Construct two unprominent nodes with radii 8.0 wu, centers `(-18.0, 0.0)` and `(18.0, 0.0)` — separated by `2 * 8.0 + PREFERRED_EDGE_GAP = 36.0` wu — and one directed relationship edge. Its straight rendered attachment points are `(-10.0, 0.0)` and `(10.0, 0.0)`, giving a 20 wu run. Render at `GraphViewport.of(0.0, 0.0, 1.63)` in a `1128 x 364` image with `Dialog` 12 and `ScreenLabelPlacement.SCREEN_FRC`.

The connector must come from production placement, and production does not offer a displaced slot for free: `placeNode` tries `retained`, then `ladder` over `LadderRung`/`NEAR_SLOTS` first (`ScreenLabelPlacement.java:486`), so with an ordinary short label and no obstacles it returns the near `ABOVE` slot and no leader at all. The fixture therefore **pins a previous `PlacedLabel` in `Slot.ABOVE_FAR`** for the first node — with its text, font, `Rung.FULL_DISPLACED`, measured width and height — and passes it as the `previous` list to `ScreenLabelPlacement.place`, which returns it through `retained()` and recomputes the leader via `nodeLeader(previous.slot(), ...)`. Deterministic, production-path, and no production accessor is added. The test then asserts:

- the returned label's `slot()` is `Slot.ABOVE_FAR` and its `mode()` is `Mode.INTERIOR`;
- the leader is present and its length is exactly `DISPLACED_OFFSET - LEADER_CLEARANCE = 30.0 - 3.0 = 27.0` screen px;
- the same fixture with an ordinary `ABOVE` slot yields no leader at all, which is the contrast that makes the 27 px row non-vacuous;
- the painted edge run is `20.0 * 1.63 = 32.6` px, measured from the rendered image, and exceeds the 27.0 px connector.

Record both measurements at full precision before any formatted display. Do not use settled coordinates, and do not hand-construct the `LeaderLine` — it must come from `retained()`.

**I13 fixed render fixture.** Use the existing deterministic `ScreenLabelPlacementShould.longScene()` inputs: `LONG_NAMES` in source order, radius 8.0, node coordinates `((index % 3) - 1) * 22.0` and `(index / 3) * 22.0`, area `1128 x 364`, zoom 1.63, no stand-in, forced label `LONG_NAMES[0]`, light theme, `Dialog` font metrics, and the existing fixed `MAP` identifier. Include an edge-bearing two-node run of 20 wu in the render state. From the production placed labels, measure every present `LeaderLine`, select the largest by Euclidean screen length, and record its slot, endpoint, text, and unrounded length. The test must not impose an upper bound or compare that value to the run. This is the wide diagonal-placement measurement; it reports a diagonal result if selected and does not change the slot policy to force one.

## C — D1 frozen harness amendment

The `InitialSpacingAcceptanceShould` test runs `src/test/resources/initial-spacing/run-initial-spacing.py` in fresh JVMs. That driver pins every `src/main/java` file against its frozen C0 plus literal overlays and pins relationship-rest targets. Amend it in the same implementation change as C1-C3; otherwise the ordinary module suite fails. Keep the C0 control byte-reproducible.

Two different file sets must be kept apart, and the earlier draft of this spec conflated them:

- **Implementation diff, relative to the delivery base `448c0d679d`:** exactly `GraphStreamLayoutEngine.java` (topology rest length) and `GraphPainter.java` (C2 and C3). Nothing else in `src/main/java` changes. This is what A authorises and what the review diff is judged on.
- **Cumulative C0 overlay, relative to the frozen C0 commit:** `GraphStreamLayoutEngine.java`, `ClusterPacking.java` and `GraphPainter.java`. `ClusterPacking.java` already differs from C0 — the previous feature changed its clearance comparison to subtract `CANDIDATE_EPSILON`, and the driver already carries that overlay at `run-initial-spacing.py:83`. That overlay must be **kept**. Only `GraphPainter.java` is a new overlay entry, and `GraphStreamLayoutEngine.java` gains a second replacement in the same file (the ring-gap constant from the previous feature is already there).

So: keep every existing overlay, add the painter overlay, add the topology replacement to the existing engine overlay, and expect the cumulative changed-source set to name exactly those three files. Dropping the packing overlay to make the set look like two files would break the driver's byte comparison against C0.

The literal replacements must represent exactly the final production diffs in A. In particular, the new engine replacement is exactly:

```text
OLD: radiusOf(first, radiusById) + radiusOf(second, radiusById)
     + NodeSeparationProjection.MIN_GAP));
NEW: radiusOf(first, radiusById) + radiusOf(second, radiusById)
     + (edge.first().isNode() && edge.second().isNode()
         ? PREFERRED_EDGE_GAP : NodeSeparationProjection.MIN_GAP)));
```

The `painter` overlay performs the following exact baseline-to-candidate literal replacements and no others. Whitespace and source text inside each OLD block are the baseline literal; the NEW block is the complete replacement.

Indentation is part of the literal. The blocks below are printed de-indented for readability only, so
apply them at the source's own indentation before matching: class-level declarations and comments take
the four spaces of members of `GraphPainter`, and statements inside `paintArrow`/`paintLabels`/
`leaderStroke` take eight. Every overlay must assert `expected.count(old) == 1` before replacing, in
the same style the driver already uses for its two existing overlays, so a mis-indented block fails
loudly instead of silently matching nothing.

1. Replace the class constant block:

```java
// OLD
// World units. 12.5 / 14.0 ~ 0.8929 of the maximum node radius:
// GraphGeometryEngine.BASE_RADIUS 8.0 x NodeProminence.MAX_SCALE 1.75 = 14.0 world.
private static final double ARROW_HEAD_WORLD_LENGTH = 12.5;
// Head base is 0.84 x length; the aspect is preserved when the cap shrinks a head.
private static final double ARROW_HEAD_HALF_BASE_RATIO = 0.42;

// NEW
// World units.
private static final double ARROW_HEAD_RATIO = 0.30;
private static final double MIN_ARROW_HEAD_WORLD_LENGTH = 4.0;
private static final double MAX_ARROW_HEAD_WORLD_LENGTH = 12.5;
private static final double MIN_ARROW_HEAD_AVAILABILITY = 8.0;
// Head base is 0.84 x length; the aspect is preserved when the cap shrinks a head.
private static final double ARROW_HEAD_HALF_BASE_RATIO = 0.42;
private static final double MIN_STROKE_WORLD = 1e-6;
```

2. Replace the complete arrow helper block, deleting the old helper:

```java
// OLD
// Screen px.
static double arrowHeadLengthPx(final double zoom) {
    return ARROW_HEAD_WORLD_LENGTH * zoom;
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

// NEW
// World units in, world units out.
static double availableArrowHeadWorldLength(final double attachmentDistanceWorld,
        final boolean arrowAtFirst, final boolean arrowAtSecond) {
    return arrowAtFirst && arrowAtSecond ? attachmentDistanceWorld / 2.0 : attachmentDistanceWorld;
}

// Screen px.
static double effectiveArrowHeadLengthPx(final double zoom, final double availableWorldLength) {
    return Math.max(MIN_ARROW_HEAD_WORLD_LENGTH,
        Math.min(MAX_ARROW_HEAD_WORLD_LENGTH, ARROW_HEAD_RATIO * availableWorldLength)) * zoom;
}

// Screen px.
static double arrowHeadHalfBasePx(final double lengthPx) {
    return ARROW_HEAD_HALF_BASE_RATIO * lengthPx;
}
```

3. In `paintArrow`, replace the existing two-line availability/length sequence with:

```java
// OLD
final double availableWorldLength =
    availableArrowHeadWorldLength(terminalSegmentLength, arrowAtFirst, arrowAtSecond);
final double arrowLengthPx = effectiveArrowHeadLengthPx(zoom, availableWorldLength);

// NEW
final double availableWorldLength =
    availableArrowHeadWorldLength(terminalSegmentLength, arrowAtFirst, arrowAtSecond);
if (availableWorldLength < MIN_ARROW_HEAD_AVAILABILITY) {
    return;
}
final double arrowLengthPx = effectiveArrowHeadLengthPx(zoom, availableWorldLength);
```

4. In `paintLabels`, apply these two exact independent literal replacements:

```java
// OLD
final BasicStroke leaderStroke = leaderStroke(theme, viewport.zoom());

// NEW
final BasicStroke leaderStroke = leaderStroke(viewport.zoom());
```

```java
// OLD
            graphics.setColor(theme.labelColor());
            final double anchorX = worldX(label.anchorX(), viewport, size);
            final double anchorY = worldY(label.anchorY(), viewport, size);
            if (label.leader().isPresent()) {
                final LeaderLine line = label.leader().get();
                graphics.setStroke(leaderStroke);
                graphics.draw(new Line2D.Double(
                    worldX(line.start().x(), viewport, size), worldY(line.start().y(), viewport, size),
                    worldX(line.end().x(), viewport, size), worldY(line.end().y(), viewport, size)));
            }
            drawCentered(graphics, font, label.text(), anchorX, anchorY);

// NEW
            graphics.setColor(theme.labelColor());
            final double anchorX = worldX(label.anchorX(), viewport, size);
            final double anchorY = worldY(label.anchorY(), viewport, size);
            if (label.leader().isPresent()) {
                final LeaderLine line = label.leader().get();
                graphics.setColor(theme.mutedColor());
                graphics.setStroke(leaderStroke);
                graphics.draw(new Line2D.Double(
                    worldX(line.start().x(), viewport, size), worldY(line.start().y(), viewport, size),
                    worldX(line.end().x(), viewport, size), worldY(line.end().y(), viewport, size)));
            }
            graphics.setColor(theme.labelColor());
            drawCentered(graphics, font, label.text(), anchorX, anchorY);
```

The first `labelColor` assignment remains before the leader; the added second assignment restores the text color after the optional muted leader.

5. Replace the complete leader helper:

```java
// OLD
static BasicStroke leaderStroke(final GraphTheme theme, final double zoom) {
    return new BasicStroke((float) (theme.edgeStroke().getLineWidth() / zoom),
        BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
}

// NEW
static BasicStroke leaderStroke(final double zoom) {
    final float width = (float) Math.max(1.0 / zoom, MIN_STROKE_WORLD);
    final float dashOn = (float) Math.max(1.2 / zoom, MIN_STROKE_WORLD);
    final float dashOff = (float) Math.max(2.8 / zoom, MIN_STROKE_WORLD);
    return new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10.0f,
        new float[] { dashOn, dashOff }, 0.0f);
}
```

After overlaying C0 with the candidate allowlist, the driver's comparison must accept exactly the three overlays listed above. The implementation diff, measured separately from the delivery base, must name exactly the two files A authorises; both assertions belong in the change, because the first guards the frozen control and the second guards scope. Preserve the driver's complete-source-tree comparison. Do not adapt or invoke `freeplane_plugin_graph/doc/initial-spacing/collect-receipt.py`.

Change the REST matrix target from scalar `nn = 22.0` on both configurations to the C0/candidate tuple `(22.0, 36.0)`. Leave `ne = 42.28427124746190` and `ee = 62.568542494923804` unchanged. Those values preserve the `+ MIN_GAP` contract for enclosure endpoints.

The current candidate settling pins are `{'S1': (84, 58), 'reference': (42, 37), 'cross': (756, 3455)}` in the driver; `TypedForcesShould.TWO_MAP_FIRST_IDLE_STEP = 3455` pins the cross first-idle value again. Refresh candidate values only, and only from a fresh recorded run of the amended candidate. Record the run id and previous value alongside each refreshed value. The escalation rule is asymmetric, because the two directions mean different things: if the candidate settles **slower**, `(new - old) / old > 0.25`, stop and escalate it as a risk finding instead of re-pinning, since slower settling is the instability this gate exists to catch; if it settles **faster**, `(old - new) / old > 0.25`, refresh the pin and record the change as a finding with the measured first-idle. Faster settling is not a regression, and the epoch-1 rule that escalated on absolute change fired on S1 (`58 -> 37`, -36%) for no good reason. C0 pins and C0 expectations are immutable. Keep the 6000-step and 600-second bounds, control-pair accounting, exhaustion expectations, C0 column, seed-gap matrix, and all other operational limits byte-identical.

Archive a direct copy of each amended run output under `freeplane_plugin_graph/doc/initial-spacing/runs/`: `manifest.json`, `comparison.json`, `acceptance.md`, and one `<configuration>-<fixture>.json` for every executed fixture. The driver writes no `results.json`, `verification.json`, or `raw/` directory; do not invent them. Preserve existing frozen C0 archives unchanged. Do not use the unreferenced historical receipt collector.

## D — D0 UI evidence and D2 settle evidence

### D0 — UI evidence boundary

The committed `GraphWorkspaceUiEvidence` projection has an empty edge list and installs `EdgePaths.empty()`. Its screenshots cannot show an arrowhead and therefore cannot witness C2. Regenerate its committed screenshot, including `docs/superpowers/specs/images/2026-08-10-graph-workspace-implemented.png`, after C3; use that regenerated screenshot as the C3 visual witness. This regeneration is evidence, not a test assertion.

C2 requires a separate edge-bearing render capture at zoom 1.63 under `docs/superpowers/specs/images/2026-10-23-graph-edge-length-and-label-connector-c2.png`. It must show a 20 wu single-headed run with the proportional head and a 6 wu run as a plain line with no head. Record the PNG's computed SHA-256 digest and the constants `ARROW_HEAD_RATIO=0.30`, `MIN_ARROW_HEAD_WORLD_LENGTH=4.0`, `MAX_ARROW_HEAD_WORLD_LENGTH=12.5`, `MIN_ARROW_HEAD_AVAILABILITY=8.0`, and `ARROW_HEAD_HALF_BASE_RATIO=0.42` in the implementation evidence record. The capture is evidence, not a gate. Do not claim the empty-edge committed screenshot witnesses C2. This spec-only commit does not create either screenshot.

### D2 — fresh relaxation and retained replay gate

The D2 comparison baseline is delivery base `448c0d679d2b1d46563eaf079352e1eefb254305`, not the historical D1 C0 `cb8782fb3742f378b2907198fd2343292415da95`. D1 C0 still has a 6 wu seed gap and cannot isolate this change. Run baseline and candidate with identical JDK, heap/runtime settings, fixture inputs, calibration, and fresh JVM/worker state. Record both commit identities, source hashes, class-code locations, JDK details, commands, and fixture input hashes.

Run the existing relationship-free `reference`, `S1`, `cross`, and `mixed` fixtures, plus every fixture below. New fixtures must carry at least one relationship edge between the named endpoint types so a result cannot pass vacuously. Keep the existing S1/cross pin-free harness separate; D2 must explicitly exercise the exclusions it does not cover.

| D2 fixture | Required construction and reason |
| --- | --- |
| `reference` | Existing relationship-free reference fixture; preserve as a control row. |
| `S1` | Existing same-map native-connector fixture. |
| `cross` | Existing two-map node/node relationship fixture. |
| `mixed` | Existing mixed-prominence fixture, retaining its real prominence values. |
| `nested-three-level` | Three enclosure levels, with a relationship between named direct-member nodes; measures compounded ring demand. |
| `pinned-neighbours` | One pinned node and unpinned neighbours, with a relationship between them; measure both endpoints and do not assume the pinned endpoint responds. |
| `multi-membership` | One node that is a direct member of two enclosures, with a relationship edge incident to it; retain both containment links and measure both rings. |
| `unenclosed-nn` | Two unenclosed nodes with a node/node relationship; no ring-bound claim applies. |
| `node-enclosure` | A node-to-enclosure relationship using `InitialSpacingFixtures.matrix(..., "ne")` semantics; verify the +6 endpoint rule. |
| `enclosure-enclosure` | An enclosure-to-enclosure relationship using `InitialSpacingFixtures.matrix(..., "ee")` semantics; verify the +6 endpoint rule. |
| `wide-diagonal-label-z163` | A fixed wide-label placement at zoom 1.63 with a relationship edge; record longest leader and slot as I13 requires. |

`GraphEdgeLengthSettleShould` runs the baseline and candidate as separate fresh JVM relaxations. Each fixture records: first-idle step and wall time; settled center-distance and rim-gap minima and medians for node/node, node/enclosure, and enclosure/enclosure relationship pairs; edge-to-node and edge-to-edge conflict counts at first idle and after 100 further frames; and routing work per published frame. Preserve per-pair values so minima/medians can be recomputed. For each relationship pair also record the I14 center-to-center distance and the endpoint-radius-adjusted rim gap. For the diagonal label fixture record every present leader's slot and length and the longest connector; never impose a connector-shorter-than-run assertion.

Use a fresh output directory for each execution under `freeplane_plugin_graph/doc/edge-length/runs/`, named with the UTC run timestamp followed by a hyphen and the first 12 lowercase hexadecimal characters of the candidate commit. Archive `manifest.json`, one `delivery-448c0d679d-<fixture>.json` and one `candidate-<fixture>.json` per fixture, `comparison.json`, and `summary.md`. `manifest.json` includes the full baseline/candidate SHAs, source/input hashes, JDK/runtime details, exact commands, and measurement schema. Report every pair and fixture; identify lopsided or sub-6.0 wu gaps as findings. Do not introduce new numeric performance limits, claim settled distances equal rest lengths, or substitute D1 C0. The existing `graphPerformanceDiagnostic` remains a retained routing/conflict replay regression gate; it runs no relaxation and is not D2.

## E — Validation vector

Run the following after implementation, in order. Commands are from the repository root and use `gradle`, not the wrapper. This specification task itself runs no build.

**Two execution modes are required, not one.** The module contains two tests with contradictory
AWT requirements, and this is a pre-existing conflict that this change does not create:

- `InitialSpacingAcceptanceShould`'s first assertion requires `java.awt.headless == "true"` in the test
  worker (`InitialSpacingAcceptanceShould.java:14`).
- `AccessibleGraphCanvasShould` constructs `JFrame`s and throws `HeadlessException` when that property
  is true (`:235`, `:328`, `:495`); it needs a real display.

Neither mode alone can pass the module, so the validation vector below is executed twice with an
explicit selector split. Skipping the accessibility tests is not permitted: coverage must be
preserved, only relocated.

**Mode 1 — headless, everything except the display-dependent tests.** Gradle's `--tests` patterns are
all *includes*; a `!`-prefixed pattern does not exclude anything (measured: the module still ran 1432
tests and failed twice with `HeadlessException`). Use a real exclusion through an init script:

```bash
cat > /tmp/graph-headless.gradle <<'SCRIPT'
allprojects {
    tasks.withType(Test).configureEach {
        filter {
            excludeTestsMatching 'org.freeplane.plugin.graph.canvas.AccessibleGraphCanvasShould'
            excludeTestsMatching 'org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould'
            excludeTestsMatching 'org.freeplane.plugin.graph.window.GraphPluginIntegrationShould'
        }
    }
}
SCRIPT
export JAVA_HOME=~/.sdkman/candidates/java/21.0.8-zulu
export PATH="$JAVA_HOME/bin:$PATH"
export JAVA_TOOL_OPTIONS=-Djava.awt.headless=true
gradle -I /tmp/graph-headless.gradle :freeplane_plugin_graph:test
```

**Mode 2 — headful under a virtual display, exactly the excluded classes.** `AccessibleGraphCanvasShould`
is not guarded by a headless assumption and simply throws; `GraphWorkspaceWindowModelShould` and
`GraphPluginIntegrationShould` guard 15 methods with `Assume.assumeFalse(GraphicsEnvironment.isHeadless())`,
which would otherwise **skip silently** and drop coverage. Selecting the whole classes in this mode runs
every method, including the 16 that Mode 1 deliberately excludes:

```bash
unset JAVA_TOOL_OPTIONS
Xvfb :99 -screen 0 1600x1200x24 &
export DISPLAY=:99
gradle :freeplane_plugin_graph:test \
  --tests 'org.freeplane.plugin.graph.canvas.AccessibleGraphCanvasShould' \
  --tests 'org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould' \
  --tests 'org.freeplane.plugin.graph.window.GraphPluginIntegrationShould'
```

The two modes together must execute every method the single-mode run would, and the executed-method
counts must be reconciled against `--info` output rather than compared as discovery counts, because
discovery includes assumption-skipped methods. Two `WorkspaceUriResolverShould` skips are
platform-conditional (`Windows path semantics`, `>= 2 file-system roots`) and are independent of AWT.

Both modes must pass. `JAVA_TOOL_OPTIONS` is what reaches the test worker: the Gradle `test` task does
not set `java.awt.headless`, and the four `systemProperty 'java.awt.headless', 'true'` entries in
`freeplane_plugin_graph/build.gradle` (`:116`, `:130`, `:163`, `:175`) belong to the `JavaExec` tasks
`graphPerformanceDiagnostic`, `boundaryCorrectionProfile`, `graphOsgiSmoke` and `graphUiEvidence`.
There is no `gradle.properties` setting it either.

| Gate | Command / selector | Acceptance statement |
| --- | --- | --- |
| Focused layout contracts (Mode 1) | `gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.graphstream.InitialSpacingSeedShould --tests org.freeplane.plugin.graph.layout.graphstream.InitialSpacingAcceptanceShould` | Engine endpoint-type rest lengths pass; the amended D1 harness completes with byte-frozen C0, candidate `nn=36.0`, preserved `ne`/`ee`, and unchanged limits. |
| Focused painter and label contracts (Mode 1) | `gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.canvas.GraphCanvasPaintShould --tests org.freeplane.plugin.graph.canvas.ScreenLabelPlacementShould` | Arrow, suppression, color, stroke, zoom, label geometry, I11 (pinned `ABOVE_FAR` retention fixture) and I13 tests pass. |
| Full graph module suite (D1 included) | Mode 1 then Mode 2, exactly as the prelude defines them, with the executed-method counts reconciled | Both modes pass: Mode 1 covers every graph test including the fresh-JVM D1 harness; Mode 2 covers the AWT-window tests under Xvfb. No test may be deleted or skipped, and no undiscovered-test relaxation may hide a missing `*Should` test. |
| Bundle verification | `gradle :freeplane_plugin_graph:verifyGraphBundle` | Existing GraphStream checksum, Java class-version, bundle manifest/classpath, import/export, and license checks pass unchanged. |
| D2 | `gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.graphstream.GraphEdgeLengthSettleShould` | All listed fixtures run against delivery base and candidate in fresh equivalent environments; all receipts listed in D are archived; every relationship pair is represented; observed findings are reported without invented bounds. |
| Retained replay regression | `gradle :freeplane_plugin_graph:graphPerformanceDiagnostic` | Existing fixed-geometry routing/conflict diagnostic passes as a regression gate; it is not represented as settle evidence. **If it fails on its own 40-minute process deadline, do not attribute that to this feature**: re-run it on an unmodified delivery-base checkout first and record both runs. A deadline failure on the unchanged baseline is a pre-existing environment limit, not a regression, and must be reported as such rather than absorbed. |
| Candidate review | `git diff --check` and review of `git diff --name-only` | No whitespace errors; production diff contains only the two files in A; tests and evidence stay within the paths authorized here. |

The build file also defines `boundaryCorrectionProfile`, `graphSmokeInstallation`, `graphOsgiSmoke`, `graphUiEvidence`, and `freeplaneLaunchSmoke`; run them only where the implementation's ordinary acceptance workflow calls for them. `graphUiEvidence` can refresh C3 screenshots but cannot witness C2. Do not substitute those tasks for the ordered full-suite, D2, and retained-replay gates.

## E2 — Epoch-2 pivot record

The relationship gap is `PREFERRED_EDGE_GAP = 20.0`, not the ring gap. This is a user decision taken
after the epoch-1 contract was materialised and executed, and the evidence is design section H. Three
consequences are normative here:

1. `PREFERRED_EDGE_GAP` is a new constant; `PREFERRED_NODE_GAP` is not touched. The containment bound
   is `r1 + r2 + 20 <= 2 * maxR + 20 < 2 * maxR + 24` (the ring chord), so it holds by an explicit
   inequality, and a test must assert `PREFERRED_EDGE_GAP < PREFERRED_NODE_GAP`.
2. The D1 `nn` candidate target is `36.0` (`8 + 8 + 20`) and `ne`/`ee` stay unchanged.
3. The two-mode execution split in E is mandatory, and the escalation rule is slower-only. Both are
   consequences of measured facts, not of preference.

## F — Superseded statements and interpretation

This section resolves descriptive prose and earlier contracts; it does not alter the approved design's normative decisions.

1. Any earlier graph-initial-spacing statement that every relationship rest adds `NodeSeparationProjection.MIN_GAP` is superseded for node/node relationships only. The new node/node additive gap is the new constant `PREFERRED_EDGE_GAP = 20.0`, which is not the ring constant `PREFERRED_NODE_GAP = 24.0`; the earlier `+ MIN_GAP = 6.0` rule remains normative for relationships with at least one enclosure endpoint, for collision correction, and for the public `MIN_GAP` constant.
2. The design's causal discussion, option comparisons, mockup labels, and rounded diagrams describe motivation or rejected alternatives. They do not authorize changes to prominence, radii, labels, enclosure geometry, routing, or settings. The formulas and exact constants in A are normative.
3. The epoch-1 rule that escalated any candidate first-idle change beyond 25% is superseded: only slower settling escalates (E and design H). Any remaining sentence in either document that escalates on absolute change is superseded by this one.
4. The phrase that a relationship is longer than a label connector is not a universal or settled-layout assertion. I11 is only the fixed 20 wu, zoom-1.63, axis-aligned render comparison, which holds above about zoom 1.35 (`27 / 20`). Diagonal connector lengths are measured and reported by I13 without an inequality.
4. `restLength` is a force target, not an equilibrium promise. I3 proves only the same-enclosure direct-node chord bound. Cross-map, nested, pinned, multi-membership, unenclosed, and settled results are measured under D2; no inference extends I3 to those cases.
5. Helper head length below 8 wu is still defined by the clamp, but it is never painted. The suppression decision belongs only in `paintArrow`; a helper result of 4.0 px for availability 3.0 is not a painted head and does not license an availability clip.
6. A 1 px connector applies only in the stated unclamped zoom band. At extreme finite positive zooms, the minimum-world clamp guarantees `BasicStroke` construction; it does not preserve a 1 px screen width or 4 px pitch there.
7. The frozen historical D1 C0 is only D1's control. D2 must compare against delivery base `448c0d679d`; the existing fixed-geometry performance diagnostic remains a replay regression, not a relaxation measurement.
8. Existing committed `GraphWorkspaceUiEvidence` screenshots have no edges and cannot witness arrowhead behavior. Its regenerated screenshot witnesses C3 only; the separate edge-bearing D0 capture is required for visual C2 evidence, but is not an automated acceptance gate.

## G — Scope and implementation constraints

Use Java 8-compatible syntax, UTF-8 source, and four-space indentation. Tests are named `*Should`. Do not change user settings, configuration, persistence, public API, or code outside A. Do not add a compatibility fallback or retain the deleted arrow helper. Do not modify `ScreenLabelPlacement` or any label/connector geometry. Keep the approved design as the sole authority; if source reality prevents implementing a pinned statement, report that exact item rather than silently weakening the contract.
