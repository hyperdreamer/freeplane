# Graph Workspace live title refresh — technical specification

Task identifier: `2026-11-01-graph-workspace-live-title-refresh`

Specification date: 2026-11-01. Status: ready for implementation review; implementation and verification have not been run by this specification authoring task.

Design authority: [2026-11-01-graph-workspace-live-title-refresh-design.md](2026-11-01-graph-workspace-live-title-refresh-design.md), approved revision r7, design commit `42cb992ceb1810bc662a81dd34e281dfbd92bf41`, SHA-256 `9f19e43599129b5d44c01b984ed65e77548008c3f2de5a12c27ad112a0176c91`. Application baseline: `8267175e52f49216ae6222bc002e48020690d67c`.

## 1. Result and scope

After a displayed node title changes, the next placement pass in an already open graph canvas must draw the current projection display text, with the best eligible face that fits its remembered slot. A title change, a face font change, or loss of face eligibility triggers face re-derivation at that slot. An unchanged eligible face with the same source title and font is re-measured and retained if its box is acceptable. If only its geometry becomes unacceptable, retention declines directly to the existing ladder.

The existing refresh chain already publishes changed labels and a new projection generation. The cache key already observes that generation. The defect is the value carried by `ScreenLabelPlacement.retained`: it copies the previous text, font and measurements after checking the previous box. Repair this method and carry its source display text in `PlacedLabel`.

This document specifies a subsequent implementation. Creating this specification authorizes no build, test, stage, commit, branch, worktree or host transition during the authoring task. The verification commands in section 9 are instructions for the implementation phase, not evidence that those commands have been executed.

## 2. Exact implementation change set

All Java paths below are relative to the repository root. Use Java 8 source-compatible constructs and the existing four-space style.

| File | Required change |
| --- | --- |
| `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/PlacedLabel.java` | Add exactly one package-private final field `sourceText`, one package-private accessor, and one mandatory constructor argument. Preserve all public declarations and existing validation. |
| `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/ScreenLabelPlacement.java` | Replace the body of `retained` with section 4. Supply source display text at all five construction sites: `enclosureLabel`, `retained`, `ladder`, `baseSlot`, `hoverOnly`. The four factories other than `retained` change only their constructor argument list. |
| `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/ScreenLabelLiveTitleRefreshShould.java` — new | Add placement, cache, metadata, geometry, obstacle and font coverage specified as T1–T7 below. Build stable identities independently of titles. Reuse the existing package-private pinned-font loader. |
| `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java` | Add the two T8 tests and their local fixture helpers. Add `sourceText` to the two existing `PlacedLabel` constructor calls; preserve all existing test assertions and helper behavior. |

No other implementation file changes are needed. In particular, do not change `ScreenLabelPlacementShould`, `ScreenLabelPlacementCache` or its key, `GraphCanvas`, `GraphPainter`, `LabelFonts`, the projection/adapter/event plumbing, layout, enclosure algorithms, translations, resources or Gradle configuration. The specification file itself is the sole file created during authoring.

### 2.1 Mandatory source assignment at every construction site

The new argument follows `truncated` and precedes `forced`.

| Construction site | Argument value |
| --- | --- |
| `enclosureLabel` | `text`, already the current enclosure label's `displayText()` |
| `retained`, on either accepted path | Current node `label().displayText()`; never `previous.sourceText()` or rendered `text` |
| `ladder` | Its `fullText` parameter, which is the current node display text; never `candidateText` |
| `baseSlot` | Its `fullText` parameter |
| `hoverOnly` | Its `fullText` parameter |
| Existing hidden-label construction in `GraphCanvasPaintShould.keepsAdjacentAboveLabelsLeaderlessAndHoverOnlyLabelsUnpainted` | `above.text()`, the text already supplied by that existing fixture |
| Existing previous-label construction in `GraphCanvasPaintShould.retainedLabel` | `node.label().displayText()`, the text already supplied by that existing fixture |
| New synthetic previous-label fixture factory | The fixture node's display text, before its title is edited |

An enclosure's metadata is populated through the common constructor only. Its placement, flags, ordering and rendering are unchanged. Do not add an old-arity constructor or an inferred-source compatibility overload.

## 3. Java declaration contracts

All production classes in this section are in `org.freeplane.plugin.graph.canvas`. Types retain their existing imports (`Font`, `Rectangle2D`, `Optional`, `ProjectedEndpointKey`, `ProjectedNodeKey`). No new public type, method or field is introduced.

### 3.1 `PlacedLabel`

Add the field alongside the existing text metadata:

```java
final String sourceText;
```

Replace the existing package-private constructor with this exact signature:

```java
PlacedLabel(final ProjectedEndpointKey endpoint, final String text, final Font font,
        final Mode mode, final Rung rung, final double anchorX, final double anchorY,
        final double width, final double height, final boolean truncated,
        final String sourceText, final boolean forced, final boolean emphaticAtAnchor,
        final boolean forcedAtBaseSlot, final boolean fullTextSlotWasFree,
        final Optional<LeaderLine> leader, final ScreenLabelPlacement.Slot slot)
```

Assign `this.sourceText = Objects.requireNonNull(sourceText, "sourceText");`. Preserve existing assignments, finite/positive geometry checks, exception text and the existing slot contract. Do not add a new empty-string policy to this constructor. The non-empty display source comes from `SafeNodeLabel`.

Add this package-private accessor:

```java
String sourceText()
```

Its body is `return sourceText;`. `PlacedLabel` remains immutable. No equality, hash-code, serialization or public API change is required; it currently has no value-equality override.

### 3.2 Changed `ScreenLabelPlacement` method bodies

These signatures stay unchanged. Only `retained` changes placement behavior; the other four acquire the constructor argument specified above.

```java
private static PlacedLabel retained(final Context context, final ProjectedNodeKey key,
        final PlacedLabel previous)

private static PlacedLabel ladder(final Context context, final ProjectedNodeKey key,
        final ProjectedEndpointKey endpoint, final String fullText, final boolean forced)

private static PlacedLabel baseSlot(final Context context, final ProjectedNodeKey key,
        final ProjectedEndpointKey endpoint, final String fullText, final boolean forced)

private static PlacedLabel hoverOnly(final Context context, final ProjectedNodeKey key,
        final ProjectedEndpointKey endpoint, final String fullText)

private static PlacedLabel enclosureLabel(final ProjectedEndpointKey endpoint,
        final String text, final Font font, final PlacedLabel.Mode mode,
        final PlacedLabel.Rung rung, final double anchorX, final double anchorY,
        final Rectangle2D size, final boolean forced, final boolean emphaticAtAnchor,
        final Optional<LeaderLine> leader)
```

No production helper is required. Resolve the remembered row by scanning the existing eight-entry `LADDER`, and use its `font(context.fonts)`, `truncating` and `far` members directly. Eligibility and candidate evaluation may be inline in `retained`. The conceptual operations in section 4 are pseudocode expansions, not additional Java APIs.

### 3.3 Existing seams used unchanged

These declarations are relevant to implementation and test construction; they are not edit targets:

```java
public List<PlacedLabel> place(final LabelPlacementRequest request,
        final List<PlacedLabel> previous, final LabelFonts fonts)

List<PlacedLabel> place(final LabelPlacementRequest request,
        final List<PlacedLabel> previous, final LabelFonts fonts,
        final List<Rectangle2D> seedObstacles)

private static PlacedLabel placeNode(final Context context, final ProjectedNodeKey key,
        final boolean forced, final PlacedLabel previous)

static double[] slotAnchor(final Slot slot, final double centerX, final double centerY,
        final double radius, final double width, final double height)

static double slotMaxWidth(final Slot slot)
static Rectangle2D screenBounds(final String text, final Font font)
static double textWidth(final String text, final Font font)
static String truncateTo(final String text, final Font font, final double limit)
static Rectangle2D rectangle(final double x, final double y,
        final double width, final double height)

private static Optional<LeaderLine> nodeLeader(final Slot slot, final double centerX,
        final double centerY, final double radius, final double anchorX,
        final double anchorY, final double width, final double height)
```

### 3.4 Existing test methods with constructor-only edits

These declarations remain exactly as they are; their only edits are the mandatory argument at the existing construction sites:

```java
@Test
public void keepsAdjacentAboveLabelsLeaderlessAndHoverOnlyLabelsUnpainted()

private static PlacedLabel retainedLabel(CanvasState state, GraphTheme theme, double zoom,
        ScreenLabelPlacement.Slot slot)
```

## 4. Retention algorithm

### 4.1 Definitions and single face table

In the following procedure, `source` means the current node's `SafeNodeLabel.displayText()`. Existing methods name this value `fullText`; that local name does **not** mean `SafeNodeLabel.fullText()`. Tooltips, search and accessibility may use the latter without affecting this contract.

The current `LADDER` remains byte-for-byte the face-policy authority:

| Declared order | Rung | Font selector | Truncating | Slot family |
| --- | --- | --- | --- | --- |
| 1 | `FULL_NEAR` | full | false | near |
| 2 | `FULL_DISPLACED` | full | false | far |
| 3 | `DENSE_NEAR` | dense | false | near |
| 4 | `DENSE_DISPLACED` | dense | false | far |
| 5 | `TRUNCATED_NEAR` | full | true | near |
| 6 | `TRUNCATED_DISPLACED` | full | true | far |
| 7 | `TRUNCATED_DENSE_NEAR` | dense | true | near |
| 8 | `TRUNCATED_DENSE_DISPLACED` | dense | true | far |

This table documents the existing array; do not create a second implementation table, rung switch, `fontFor` mapping or truncation-name inference. `HOVER_ONLY` has no row.

Near slots are `ABOVE`, `BELOW`, `RIGHT`, `LEFT`, `ABOVE_RIGHT`, `ABOVE_LEFT`, `BELOW_RIGHT`, `BELOW_LEFT`, in that existing order. Far slots are `ABOVE_FAR`, `BELOW_FAR`, `RIGHT_FAR`, `LEFT_FAR`, in that existing order. A row is eligible when its `far` value matches the remembered slot family and, if the node is currently forced, its rung is `FULL_NEAR` or `FULL_DISPLACED`. Apply this filter to **both** accepted paths.

Use `Font.equals`, not reference equality. Source equality is exact `String.equals` on display text; do not normalize it again and do not compare to `previous.text()`.

### 4.2 Entry classification

```text
retained(context, key, p):
    if p is null: return null
    if p.mode == HOVER_ONLY: return null

    source = context.nodes[key].label.displayText
    slot = p.slot
    forced = isForced(context, key)
    centerX = context.screenX(key)
    centerY = context.screenY(key)
    radius = context.radius(key)
    rememberedRow = the row in LADDER whose rung equals p.rung, if any

    eligible(row) = row has the slot's family
                    AND (!forced OR row.rung is FULL_NEAR or FULL_DISPLACED)

    keepClass = rememberedRow exists AND eligible(rememberedRow)
                AND p.sourceText.equals(source)
                AND rememberedRow.font(context.fonts).equals(p.font)

    if keepClass: execute KEEP below
    otherwise: execute RE-DERIVE below
```

A row not found is ineligible and therefore cannot enter KEEP; valid production node placements reaching this point have a row. Do not dereference a missing row. The existing null-previous and hover-only guards must run before row lookup. Valid retained node placements have a non-null slot supplied by the production factories; this change adds no recovery policy for malformed synthetic `PlacedLabel` objects.

### 4.3 Outcome KEEP

```text
row = rememberedRow
font = row.font(context.fonts)
text = row.truncating ? truncateTo(source, font, slotMaxWidth(slot)) : source
truncated = !text.equals(source)
size = screenBounds(text, font)
anchor = slotAnchor(slot, current centerX, current centerY, current radius,
                    size.width, size.height)
box = rectangle(anchor.x, anchor.y, size.width, size.height)

if !placementArea.contains(box) OR intersectsAny(context.obstacles, box):
    return null                         // DECLINE; do not enter RE-DERIVE
return EMIT(row, font, text, truncated, size, anchor, box)
```

There is **no width-cap rejection on KEEP**, for either forced or ordinary nodes. A truncating row still uses `truncateTo` and the slot's existing limit to reproduce its rendering policy. A non-truncating row may carry a box wider than the cap from an earlier forced `baseSlot`; it remains retainable after demotion. Re-measure even on KEEP. With unchanged source/font/row, this reproduces baseline dimensions and current-geometry anchors and leaders.

Geometric rejection in this class must return immediately. Trying denser rows here would introduce face changes and settling oscillations for unchanged content, contrary to the design.

### 4.4 Outcome RE-DERIVE

```text
for row in LADDER, in declared order:
    if !eligible(row): continue
    font = row.font(context.fonts)
    limit = slotMaxWidth(slot)

    if !forced AND !row.truncating AND textWidth(source, font) > limit:
        continue

    text = row.truncating ? truncateTo(source, font, limit) : source
    truncated = !text.equals(source)
    size = screenBounds(text, font)
    anchor = slotAnchor(slot, current centerX, current centerY, current radius,
                        size.width, size.height)
    box = rectangle(anchor.x, anchor.y, size.width, size.height)

    if !placementArea.contains(box): continue
    if intersectsAny(context.obstacles, box): continue
    return EMIT(row, font, text, truncated, size, anchor, box)
return null                             // DECLINE: all eligible rows exhausted
```

Only the remembered slot is tried here; do not loop over a row's slots. Forced re-derivation bypasses the non-truncating width-cap check but still requires containment and obstacle clearance. Forced rows are never dense or truncating. The ordinary fallback ladder continues to apply its own existing caps, including for forced labels; this method's forced exemption does not modify `ladder` or `fullTextCandidateWasFree`.

Ordinary non-truncating candidates use a strict `>` cap rejection, so exact equality fits. Truncating rows use the unchanged `truncateTo`: return the source if it fits; otherwise strip trailing whitespace from progressively shorter prefixes and append U+2026; retain the current three-character-plus-ellipsis fallback if no tested prefix fits. Do not add a new post-truncation cap check, alter Unicode/string slicing, replace the ellipsis, or invent a new truncation policy.

### 4.5 Shared emission and outcome DECLINE

`EMIT` denotes this inline emission, with the accepted row and measured values:

```text
leader = nodeLeader(slot, centerX, centerY, radius,
                    anchor.x, anchor.y, size.width, size.height)
add box to context.obstacles exactly once
return new PlacedLabel(
    p.endpoint, text, font, p.mode, row.rung,
    anchor.x, anchor.y, size.width, size.height,
    truncated, source, forced, p.emphaticAtAnchor,
    false, false, leader, slot)
```

Preserve `endpoint`, `mode`, `emphaticAtAnchor` and slot, not stale face assets. Recompute `forced` from the request. Set both `forcedAtBaseSlot` and `fullTextSlotWasFree` to false, matching baseline retention. The constructor argument `source` is the complete projection display text even if `text` is truncated. No previous leader, anchor or dimension is copied.

Every rejection occurs before obstacle insertion. DECLINE returns `null` and changes no context state. Existing `placeNode` then calls `ladder` with the current display text, then `baseSlot` if forced, otherwise `hoverOnly`. A forced fallback base slot may deliberately ignore cap, area or obstacle restrictions as it does today; only `baseSlot` sets `forcedAtBaseSlot = true`.

There are at most two scans of the fixed eight-entry `LADDER`. Do not add another cache, retained-placement invalidation pass, result type, policy table or mutable cross-frame state.

## 5. State transitions, ordering and checkable invariants

A `PlacedLabel` is an immutable output value. Each paint/placement request creates a fresh context. The only new history carried into a later request is the source display text in that value.

| Previous/current relationship | Retention transition | Fallback/flags |
| --- | --- | --- |
| Missing previous, or previous `HOVER_ONLY` | DECLINE before face lookup | Existing ladder and terminal path |
| Same display source, same face font, eligible row, acceptable current box | KEEP that row, re-measured | No cap check; accepted-retention flags false |
| Same display source, same face font, eligible row, unacceptable current box | DECLINE directly | Existing ladder under accumulated obstacles |
| Display source changed, including identical truncations | RE-DERIVE at remembered slot | Accept first eligible row, or DECLINE |
| Display source unchanged but face font differs | RE-DERIVE at remembered slot | Geometry-only decline rule does not apply |
| Dense/truncated ordinary face becomes forced | RE-DERIVE; old row ineligible | Full-font row only, or forced ladder/base slot |
| Forced over-cap full face becomes ordinary; source/font/eligibility and acceptable box unchanged | KEEP | `forced = false`; face/box preserved on subsequent replay |
| `fullText()` hidden tail changes but display text does not; font/eligibility unchanged | KEEP if box acceptable | Hidden tail does not trigger re-derivation |

The context starts with all visible node-disc **axis-aligned bounding squares**, using the existing minimum screen radius of 2, plus seed obstacles. Do not substitute circular intersection geometry. Radius sorting is descending and stable for ties. Placement passes remain: forced nodes, emphatic then subtle enclosures, ordinary nodes. A retained forced node precedes enclosure labels. All accepted earlier placements remain obstacles for later nodes. A declined node's fallback is not generally equal to a wholly fresh scene; removing that node alone from `previous` is the correct control.

The design's seven invariants translate to these acceptance assertions:

| Design invariant | Checkable assertion and owner |
| --- | --- |
| 1. Current content/assets | Every accepted retained output has source equal to the current projection display text; text equals that row's current rendering, font equals its current selected face, truncation equals rendering/source inequality, measurements equal `screenBounds`. T1, T4c, T4f, T5, T7. |
| 2. Stable accepted histories | For unchanged source/font and an eligible row with an acceptable box, retain the row and reproduce dimensions and current-geometry anchor/leader even after earlier retention or uncapped base placement. T3, T3b, T4b, T4d, T4e. |
| 3. Baseline obstruction fallback | With source/font/eligibility unchanged and the box blocked, compare against a run omitting only that node's previous placement while preserving other retained labels. Font/eligibility transitions instead use RE-DERIVE. T3c, T3d, T7. |
| 4. One face table and family | Review that row assets come only from `LADDER`; retained near slots emit only near rungs, far slots only displaced rungs. T4 family test and source review. |
| 5. Forced policy | No accepted forced output is dense/truncated; forced re-derivation may exceed the cap; ordinary KEEP may preserve a historical over-cap box; retention clears the base flag. T4b, T4d and existing O4 test. |
| 6. No rejected obstacle | Each T6 rejected expanded box leaves a later label free to occupy a clear portion of it; accepted expansion makes that later label move. No add before acceptance in source review. |
| 7. Internal-only addition | Exactly one package-private field and accessor added; constructor mandatory; public declarations, other algorithms and cache key unchanged; existing assertions intact. Change-set review and T9. |

## 6. Test declarations and shared fixtures

Use JUnit 4 `@Test`, AssertJ, and the existing package `org.freeplane.plugin.graph.canvas`. Every test below must have substantive assertions; no name-only tests or relaxed existing expectations. All production placement assertions find an endpoint by stable key, never by rendered title or list index.

### 6.1 New placement/cache test class

The new file declares `public class ScreenLabelLiveTitleRefreshShould`. Its exact required test method signatures are:

```java
// T1, T2
public void refreshesTheRetainedTitleWithAStableNodeIdentity()
public void refreshesTheTitleThroughTheSamePlacementCache()

// T3 and its lettered cases
public void keepsAnUnchangedFaceOnImmediateReplayAndAfterRetainedHistory()
public void keepsAValidSlotWhenAFreshPlacementWouldChooseAnother()
public void declinesAnObstructedUnchangedFaceWithoutTryingDenserRungs()
public void fallsBackUnderEarlierRetainedLabelObstacles()

// T4 main cases
public void upgradesAShortenedTitleAtItsRememberedSlot()
public void degradesAnExpandedTitleAtItsRememberedSlot()
public void reLaddersWhenNoFaceFitsTheRememberedSlot()
public void retainsOnlyFacesFromTheRememberedSlotFamily()

// T4b
public void declinesAForcedSlotInsteadOfTakingADenseFace()
public void rejectsDenseAndTruncatedFacesWhenPromotedToForced()
public void keepsAnUnchangedForcedOverCapFace()
public void rederivesAChangedForcedTitleWithoutApplyingTheSlotCap()

// T4c
public void detectsATitleEditHiddenByAnIdenticalTruncation()
public void recordsTheDisplaySourceOnTruncatedAndRetainedOutputs()
public void clearsTruncationWhenTheTitleBecomesItsPreviousRendering()
public void recordsDisplayTextWhenProjectionFullTextDiffers()
public void recordsSourceTextAtAllPlacementConstructionSites()

// T4d, T4e, T4f, T4g
public void keepsAnOverCapFaceAfterDemotionAndOnTheNextReplay()
public void keepsAnUnchangedTruncatedFaceWhenSpaceBecomesAvailable()
public void advancesTheSourceWhenTwoTitlesRenderTheSameTruncation()
public void ignoresAnEditConfinedToTheProjectionHiddenTail()

// T5
public void remeasuresWidthAfterASameFontTitleEdit()
public void remeasuresHeightAndAnchorAfterAFaceTransition()
public void recomputesBothEndsOfADiagonalLeaderAfterRetitling()

// T6
public void declinesAnExpandedBoxAtADiscWithoutLeavingAnObstacle()
public void declinesAnExpandedBoxAtAnEarlierLabelWithoutLeavingAnObstacle()
public void declinesAnExpandedBoxOutsideTheAreaWithoutLeavingAnObstacle()

// T7 and the first-placement/hover guards
public void rederivesAtTheRememberedSlotWhenTheFaceFontChanges()
public void reLaddersAPreviouslyHoverOnlyNodeFromItsCurrentTitle()
```

The class may use the following private fixture helpers, with these complete signatures. No production visibility change or reflection seam is needed:

```java
private static ProjectedNodeKey stableKey(final String id)
private static ProjectedNode node(final String id, final String fullText,
        final String displayText)
private static LabelPlacementRequest request(final long generation,
        final Rectangle2D area, final Set<ProjectedEndpointKey> forced,
        final ProjectedNode[] nodes, final double[][] screenDiscs)
private static PlacedLabel findNode(final List<PlacedLabel> labels, final String id)
private static PlacedLabel remembered(final LabelPlacementRequest request,
        final String id, final PlacedLabel.Rung rung, final Font font,
        final boolean truncating, final ScreenLabelPlacement.Slot slot,
        final boolean forcedAtBaseSlot)
private static void assertSamePlacement(final PlacedLabel actual,
        final PlacedLabel expected)
private static String repeat(final char character, final int count)
```

Helper contracts:

- `stableKey(id)` uses map `00000000-0000-0000-0000-000000000001`, `PersistedNodeId.of(id)`, `NodeReference.of`, `SourceNodeKey.persisted`, and `ProjectedNodeKey.of`. Neither title is part of the ID. `node` uses `SafeNodeLabel.of(fullText, displayText)`, map name `"Map"`, and unmarked nodes.
- `request` uses `GraphProjection.structure(generation, Arrays.asList(nodes), empty enclosures)`, ordered maps for geometry and positions, no hulls, zoom 1, viewport centre `(0,0)`, `RenderingLevel.FULL`. Each ordered `screenDiscs[i]` is `{screenX, screenY, radius}` for `nodes[i]`. Convert to world centre `(screenX - area.width/2, screenY - area.height/2)` and use that centre in **both** `NodeGeometry` and `LayoutPositions`. `LabelPlacementRequest.screenX/Y` do not add the area's minimum; all fixture areas below start at zero. Equal-radius order is node-array insertion order.
- `remembered` constructs a valid synthetic earlier placement from the supplied request's display text, supplied row/font/truncating policy and slot. Measure and anchor it using the existing package-private geometry functions; store the source display text, request's forced membership and specified base flag. Mode is `INTERIOR`, other diagnostic flags false, and the leader is empty unless a test obtains its previous value through production placement. Tests of leaders must use production-generated previous labels. It must not derive key identity from text or introduce another production policy table.
- `assertSamePlacement` compares endpoint, text, source, font, mode, rung, slot, truncation, width, height, anchors, forced/emphatic/base/full-text flags and leader presence/endpoint coordinates, not object identity. Use exact equality for immediate replay with identical inputs; use tolerance `1e-9` for independently calculated geometry and `1e-4` only for quoted rounded fixture numbers. In base-slot and demotion tests explicitly assert the intentional flag changes separately rather than comparing unequal flags as if they should match.
- `repeat` uses Java 8-compatible character-array filling, not `String.repeat`.

Use `ScreenLabelPlacementShould.fonts()` for placement geometry tests: repository Noto Sans regular/bold subset fonts, normally full 12 and dense 9. Do not copy/change that font loader or use host logical fonts for pinned geometry. Set `F = fonts()`, `W(t,f) = screenBounds(t,f).width`, `H(t,f) = screenBounds(t,f).height` in the fixture descriptions below. Assert stated width/clearance preconditions in the actual tests so broken fixture assumptions cannot become passing evidence.

### 6.2 Common geometries and titles

Unless specified, there are no seed obstacles or enclosures, nodes have radius 8, and all nodes are ordinary.

- **Open scene:** area `(0,0,1280,800)`, ordered IDs `edited`, `bravo`, `charlie`, `delta`; screen centres `(600,400)`, `(680,400)`, `(640,360)`, `(640,440)`. Edited display/full text changes `alpha` → `omega renamed`; other labels are their IDs. The edited node is first among equal radii, so its `FULL_NEAR/ABOVE` box has no earlier-label interference.
- **Strip:** area `(0,0,200,50)`, one node `edited` at `(100,32)`. Define `B = repeat('W',20)`, `A = repeat('W',50)`, `A2 = repeat('W',60)`, and `A3 = repeat('W',70)`. These display titles stay within the extractor's 80-code-point limit. Assert `W(B,F.full)>200`, `W(B,F.dense)<=200`, `W(B,F.dense)>130`, `W(A,F.dense)>200`, and that full-font truncations of A/B and A/A2 at 200 agree. B fits `DENSE_NEAR/ABOVE`; A fits `TRUNCATED_NEAR/ABOVE`. Side/diagonal boxes for these long truncated labels leave the 200-pixel strip; far vertical boxes leave its height.
- **Height band:** same strip, with seed rectangle `(0,2,200,2)`. A full-font ABOVE box starts near `1.655886` and hits the band, while a dense ABOVE box starts near `5.741915` and clears it. The initial A/A2/A3 face under this band is `TRUNCATED_DENSE_NEAR/ABOVE`. Removing the band makes `TRUNCATED_NEAR/ABOVE` free without changing source, font or eligibility. The band models a neighboring earlier box; seed obstacles are an existing placement seam, not a new product API.
- **Over-cap:** area `(0,0,1128,364)`, one forced node `edited` at `(542,182)` with display/full text `Well-Ordering Theorem of Choice and Regularity`. Its full width is approximately `274.130005`, above the 200 cap. Fresh placement reaches `baseSlot`, yielding full-font `FULL_NEAR/ABOVE`, anchor approximately `(542,159.827943)`, height `16.344114`, and base flag true. Its box is nevertheless inside the area and clear.

## 7. Required fixtures, assertions and failure discriminators

The IDs T1–T9 and every lettered variant retain their design meaning. A case may require several test methods because it has independent mechanisms. Tests described as preservation controls are expected to pass baseline behavior; do not fabricate a baseline failure for them. Tests using the new accessor naturally require the new declaration to compile; semantic baseline comparisons may use a constructor/accessor-only shim with baseline retention, described in section 9.

### T1 — placement-layer retitle

Use the open scene with generations 1 then 2 and exactly equal stable node/endpoint keys. Call the same placement instance with `previous = null` for generation 1 and the resulting list for generation 2. Assert the edited projection keys compare equal, generation advances, and the edited result has text/source `omega renamed`, not `alpha`. Assert no output text equals `alpha`; compare unchanged nodes by stable endpoint. Assert the edited result remains `FULL_NEAR/ABOVE` and its new dimensions match current full-font measurement. Baseline retention returns `alpha`; stale text is the intended failing assertion.

### T2 — cache entry point and generation isolation

Use one `ScreenLabelPlacementCache` and the same scenario, but construct generation 2 by reusing the **exact same** `GraphGeometry` and `LayoutPositions` instances from request 1 while replacing only its projection generation and edited `SafeNodeLabel`. Keep viewport, dimensions, forced set, rendering level and the `LabelFonts` instance unchanged. This isolates generation in the current identity-sensitive cache key.

Assert request positions are the same object, generation-1 replay returns the same cached list, generation 2 produces current text/source, and an immediate generation-2 replay returns that new cached list. Baseline invalidates correctly but still returns `alpha`; do not change the cache key to fix it.

### T3 — unchanged title and retained history

First replay an open-scene request immediately and assert every face, source/rendering, measurement, anchor and leader is identical. Assert the intentional diagnostic transition separately: an initial non-truncating ladder output has `fullTextSlotWasFree = true`, while a retained output clears it to false. Replay that retained list once more and compare all fields, including the now-stable diagnostic flags. Then use area `(0,0,400,50)`, one node at `(200,28)`, and title `Axiom Schema of Replacement a\u2026` with the final escape interpreted as U+2026. Its full width is approximately `193.873367`, dense width is below 200. A full ABOVE box leaves the top boundary, and the fresh ladder chooses `DENSE_NEAR/ABOVE`.

Produce that value through placement, replay it at y=28 to create a value emitted by retention, then move the node to y=32 with unchanged source/fonts/eligibility and replay the retained value. Assert dense face, rendering, width and height survive and that anchors/leader equal those obtained by applying the same face to current geometry. A `previous = null` control at y=32 chooses `FULL_NEAR/ABOVE`; assert that difference. Unconditional re-derivation would upgrade and fail. Baseline behavior is the preservation oracle.

### T3b — retention remains enabled

Area `(0,0,400,300)`, Alpha at `(200,150)`, title `Alpha`. Let its full width be w and height h. Seed two tiny rectangles, each width `0.5`, height `2`, with x=`200+w/2-0.25` and centres at the full ABOVE and BELOW anchor y values. These intersect those two initial boxes but do not intersect RIGHT. Produce the first placement through production and assert `FULL_NEAR/RIGHT`.

Move the centre one pixel left to `(199,150)`, keeping both seed rectangles unchanged. The full ABOVE box's right edge moves clear of the upper seed. Assert replay retains RIGHT and a null-previous control chooses ABOVE. Assert both are valid non-overlapping boxes. Removing retention or always starting a fresh scene fails this witness; immediate replay alone cannot provide it.

### T3c — unchanged obstruction declines, without dense retry

Area `(0,0,400,300)`, ordered ordinary nodes Alpha at `(200,150)` and Beta initially at `(160,150)`, radius 8, source titles `Alpha`, `Beta`. Produce and assert Alpha `FULL_NEAR/ABOVE` initially. Move Beta to `(200,115)` (relative y −35) with source/fonts unchanged.

Alpha's old full ABOVE interval relative to its centre is about `[-30.344114,-14]`; Beta's disc square occupies `[-43,-27]`. They intersect. A dense ABOVE interval `[-26.258085,-14]` would clear Beta, which makes this fixture reject the wrong re-derivation path.

For the moved request, compare normal replay with a control previous list that omits **only Alpha** and keeps Beta's previous value. Assert Alpha is full-font `FULL_NEAR/BELOW` in both, never dense ABOVE. Then drive both histories through Beta relative y positions `−35, −20, +35, +20`, independently feeding each run its own previous results and omitting Alpha only in the control. Assert equal endpoint, source/rendering, face, dimensions, anchor and leader on every pass and Alpha's full-font slot sequence `BELOW, BELOW, ABOVE, ABOVE`. Compare diagnostic provenance flags separately: accepted retention clears `fullTextSlotWasFree`, whereas a successful non-truncating fallback can set it. There is no earlier retained label before Alpha, so a wholly fresh control also agrees for Alpha here. A dense retry on unchanged geometric rejection fails on the first moved pass.

### T3d — accumulated obstacles are preserved on fallback

Area `(0,0,300,150)`, ordinary Alpha at `(100,100)` then Beta at `(130,120)`, radius 8. Synthetic previous values are valid full-font Alpha/RIGHT and Beta/BELOW; Beta's previous frame may use y=115 so its former BELOW box was contained. Their source titles are unchanged.

On the current request Alpha/RIGHT remains acceptable. Beta/BELOW would end near `150.344114`, outside the area. Its fallback ABOVE intersects retained Alpha/RIGHT, so it chooses `FULL_NEAR/RIGHT`. Assert equality with a control omitting only Beta's previous label and retaining Alpha. A fully fresh pass places Alpha ABOVE and Beta ABOVE; assert Beta's fresh slot differs. Resetting all previous placements or all obstacles after one decline fails this test.

### T4 — changed title derives the best face at the retained slot

Use separate tests for the following:

1. Strip, B → `Alpha`: initial `DENSE_NEAR/ABOVE` upgrades in place to `FULL_NEAR/ABOVE`, full text visible, truncation false, current source and current dimensions. Baseline keeps B/dense.
2. Strip, `Alpha` → B: full face degrades in place to `DENSE_NEAR/ABOVE`, text B, truncation false. Assert all earlier eligible near rows fail their cap or geometry checks and the selected row fits. Baseline keeps Alpha/full.
3. Area `(0,0,200,100)`, centre `(140,50)`, synthetic remembered full-font `RIGHT` for `Alpha`, changed to A. The old RIGHT box fits in the 46-pixel space beginning at x=154; every new eligible RIGHT face exceeds that available space, including both truncated fonts. Assert these preconditions, then assert fallback places the current title with `TRUNCATED_NEAR/LEFT`, not RIGHT. No stale box is retained.
4. Area `(0,0,600,300)`, centre `(300,150)`, synthetic truncated previous A in RIGHT and, in a separate replay, RIGHT_FAR. Change to `Alpha`. Assert near replay uses `FULL_NEAR/RIGHT`, far replay uses `FULL_DISPLACED/RIGHT_FAR`; assert membership in the corresponding family. An implementation walking all ladder rows without the family filter picks `FULL_NEAR` for the far slot and fails.

### T4b — forced eligibility and cap exemptions

1. Adapt T3c's initial/moved nodes, but force Alpha and edit its title `Alpha` → `Gamma` while Beta moves to relative y −35. At the remembered ABOVE slot, the full-font box is blocked while a dense box would clear. Only the full near row is eligible there. Assert the output is full-font `FULL_NEAR/BELOW` through the forced ladder (or an explicitly asserted base-slot outcome if a separate fully blocked fixture is used), text/source Gamma, forced true, and neither dense nor truncated. The specified Gamma scene selects BELOW, with base flag false. This preservation/eligibility control need not fail baseline.
2. Promote the strip's ordinary B/dense value to forced with its title unchanged. Repeat independently with the ordinary A/truncated value. Assert each promotion excludes the remembered dense/truncated row, reaches full-font `FULL_NEAR` via base slot, shows the entire source and has base flag true. The width leaves the 200-pixel area even after a cap exemption, so re-derivation cannot accept it. Baseline incorrectly retains the ordinary face.
3. Use the over-cap fixture. Assert first-pass base flag true. Replay unchanged; assert the same endpoint/mode/rung/text/font/slot/dimensions/anchor/leader, forced true, width >200, but base flag false and full-text-free flag false. Repeat once more. This pins KEEP's cap exemption and preserves the existing O4 behavior.
4. From that retained over-cap value, change the title by appending `!`, with the area still roomy and clear. Assert the new full-font ABOVE width remains >200 and inside the area, source/text advance, slot/rung are retained and base flag is **false**. This specifically tests the forced RE-DERIVE cap exemption: applying the ordinary cap would decline, run the capped forced ladder and reach base slot with flag true.

### T4c — source metadata, truncation collisions and flag transitions

The five declared tests in this group cover these independent assertions:

- In the strip, start with A and no band. Assert the actual output is `TRUNCATED_NEAR/ABOVE`, source A, source != rendered text, and its rendered text equals `truncateTo(B,F.full,200)`. Edit to B. Assert re-derivation selects `DENSE_NEAR/ABOVE`, displays all B, clears truncation and emits source B. Rendering equality at the old face must not hide the edit.
- Under the height band start with A3, shorten to A2, and assert both initial and retained outputs are `TRUNCATED_DENSE_NEAR/ABOVE`; both sources equal their respective projection display text and differ from rendering. Use the **retained A2 output**, not the initial ladder output, as previous for a third pass with unchanged A2 and the band removed. Assert it stays truncated dense. A fresh control must select full-font `TRUNCATED_NEAR/ABOVE`. This separately kills storing rendered text at the ladder site and at the retained-emission site.
- From the unbanded A output, change the title to exactly its previous rendered string, including its U+2026. Assert current text/source equals that string and truncation becomes false. A fuller full-font near row is now possible. Copying `previous.truncated()` fails.
- Provide `SafeNodeLabel.of(full,display)` with distinct values using T4g's actual 80-code-point display fixture below. Assert the stored source equals display and not full, including on a replay. This checks storage; T4g checks comparison.
- Directly assert the new accessor at all five production factories: a truncated ladder result, retained result, forced base result, hover-only result and enclosure result. For hover-only, use an ordinary Alpha node at `(2,2)` in a `4x4` area so no text box fits, and assert source Alpha. For the enclosure case reuse the existing package-private `ScreenLabelPlacementShould.placeEnclosure` fixture helper with non-empty text `Enclosure display`, no nodes and a roomy area; assert source equals that enclosure's display text. Enclosures are not retained.

### T4d — demotion keeps the historical uncapped face

Use the over-cap forced output. Replay with the forced set emptied, keeping source, fonts, eligibility and geometry unchanged, then replay that ordinary output once more. Assert forced false on both replays, base flag false, full text visible, no truncation, width >200 and exact equality of face/slot/measurements/anchor/leader to the forced placement. Compare intentional flag changes separately. A cap on KEEP fails even though ordinary RE-DERIVE would apply caps.

### T4e — newly available space does not trigger a face change

Under the height band, produce A3, shorten to A2 and retain the resulting `TRUNCATED_DENSE_NEAR/ABOVE` value. Leave A2 unchanged while removing the band. Assert source and rendering metadata are correct before and after, old face remains and the new box is acceptable. Assert a fresh control or independent full-font candidate now fits and chooses `TRUNCATED_NEAR/ABOVE`. This explicitly witnesses a neighboring box leaving while a **shortened but now unchanged** title must stay stable. Baseline preserves the face; a source/rendering comparison or bad source initialization introduces the failure.

### T4f — two equal renderings still advance the source

Unbanded strip, A → A2. Assert distinct display sources, identical full-font truncations at the retained slot, and neither source fits any earlier full-text eligible face. Assert emitted rendering and row may remain identical, but source advances to A2 and remains distinct from rendered text. Do not claim that this test alone proves RE-DERIVE was entered: visible output cannot distinguish correct re-derivation from a keep with updated metadata. T4c/T4e/T4g own trigger discrimination.

### T4g — actual projection truncation, hidden-tail comparison

Use `prefix = repeat('i',77)`, `display = prefix + "..."`, `full1 = prefix + "hidden-tail-A"`, `full2 = prefix + "hidden-tail-B"`. Each full title exceeds 80 code points; the common display has exactly 80. This follows the existing `SafeNodeLabelExtractor.MAX_DISPLAY_CODE_POINTS = 80` policy (77 prefix code points plus three ASCII dots). Construct the two `SafeNodeLabel` values explicitly; no editor model or adapter behavior change is needed.

Use pinned regular full 9 pt and dense 6.75 pt, unchanged between passes, so this actual projection-shortened display has full width <=200. Assert that precondition. Let full/dense measured heights be hf/hd. Area is `(0,0,400,40)`, initial centre `(200,14+(hf+hd)/2)`. Full ABOVE leaves the area while dense ABOVE fits; assert initial `DENSE_NEAR/ABOVE`. After an immediate retained replay, use full2 with the same display and move the centre to `(200,14+hf+1)`. Full ABOVE now fits, but dense remembered ABOVE also remains acceptable.

Assert equal stable IDs, unchanged display/fonts/eligibility, different full texts, source=display, and retained dense face. A fresh control chooses `FULL_NEAR/ABOVE`. Storing display correctly but comparing against `fullText()` instead of `displayText()` upgrades and fails. Use measured heights rather than borrowing 12-pt rounded coordinates for this fixture.

### T5 — measurements, anchor and leader are recomputed

1. Width: one isolated ordinary node in a roomy area, previous `alpha/FULL_NEAR/ABOVE`, changed `omega renamed`. Assert the strings have different full-font widths, font remains full, and emitted width equals a fresh `screenBounds(newText,F.full)` measurement within `1e-9`.
2. Height/anchor: strip `Alpha` → B. Assert full/dense measured heights differ. Assert emitted dense height equals `screenBounds(B,F.dense).height` and ABOVE anchor is `(100,32-8-6-newHeight/2)`, not the old anchor. Also assert new width and actual box match the new measurement.
3. Leader: roomy area `(0,0,600,400)`, node at `(250,200)`. Force its initial full-font `alpha` placement to `ABOVE_RIGHT` by seeding four `1x1` rectangles centred on its full-font ABOVE, BELOW, RIGHT and LEFT anchors. Each rectangle intersects its cardinal candidate while remaining disjoint from the diagonal candidate. Obtain this prior value from **production placement**, assert `FULL_NEAR/ABOVE_RIGHT` and non-empty leader, then retitle to `omega renamed` and replay without those seeds. Retention must still accept ABOVE_RIGHT and recompute both leader endpoints.

For the diagonal case independently compute `dx = r+6+w/2`, `dy = -(r+6+h/2)`, `d = hypot(dx,dy)`, `u = (dx/d,dy/d)`. The leader rim start is `C+r*u`. With clearance 3, the ray first enters the inflated label rectangle at distance `q = max((r+6-3)/u.x, -(r+6-3)/u.y)` from C; end is `C+q*u`. Assert `q<d`, `q-r>=2`, both actual endpoints equal these new values, and both differ from the old leader's endpoints. Do not call the private production leader method as the expected-value oracle, and do not use ABOVE, whose leader is intentionally empty.

### T6 — three obstacle rejection mechanisms and no phantom boxes

Each of the three tests uses the following common construction, so a rejection cannot be explained by a different face or by an already blocked old box:

- Edited node E is **forced**, radius 8, centre `(100,150)`, previous `FULL_NEAR/RIGHT`, old source `alpha`, new source `repeat('W',10)`. The old box starts at x=114 and is clear. The new full-font RIGHT box starts at the same x, reaches beyond x=200, and has vertical interval approximately `[141.827943,158.172057]`. Assert these measured preconditions. Since E is forced and RIGHT is near, **only `FULL_NEAR` is eligible at the remembered slot**; exhausting it exhausts retention.
- Later ordinary node L has text `i`, radius 8, centre `(170,150+8+6+H("i",F.full)/2)`, and a synthetic previous `FULL_NEAR/ABOVE`. Its label is centred at `(170,150)`, wholly inside a clear part of the rejected expanded RIGHT box, and clear of the old box. Its own disc is below that label and does not obstruct E's candidate.
- E's existing forced-ladder fallback is full-font ABOVE, which is free and does not overlap L's ABOVE box. Preserve L's previous value in every run. Assert the old E box is contained and obstacle-free, and the new retained-slot candidate fails for the named reason before checking results.

Individual variants:

| Test | Current scene and blocking reason | Accepted-expansion control |
| --- | --- | --- |
| Disc | Area `400x300`; ordinary B at `(215,150)`, radius 8, label `Beta`. Its disc square intersects expanded E/RIGHT but not old E/RIGHT or L's label. Order nodes E, B, L. | Move B to `(300,150)` with same source; keep E/L and other inputs fixed. |
| Earlier label | Area `400x300`; forced B radius 9 at `(220,150+9+6+H("Beta",F.full)/2)`, with valid previous full-font ABOVE label `Beta`. Radius order puts B before E. B's label, centred `(220,150)`, hits expanded E/RIGHT; its disc below it does not. L is ordinary and last. | Move B horizontally to x=300, preserving its source and ABOVE face. |
| Area | Area `200x300`, only E and L. Expanded E/RIGHT leaves the right edge while old E/RIGHT and L/ABOVE are contained; there is no other blocking obstacle there. | Widen area to `400x300` while preserving specified **screen** centres through the fixture coordinate conversion. |

For **every** rejection run assert current E text/source, full-font ABOVE fallback and base flag false; assert L still retains ABOVE. Assert L's bounds intersect the rejected expanded E/RIGHT rectangle, but intersect neither the actual blocking obstacle nor E's accepted fallback box. This is a positive witness that no rejected candidate became an obstacle. A premature obstacle add would force L away even if E's final fallback looks correct.

For **every** accepted-expansion control assert E remains RIGHT with current full-font measurements, source and base flag false. Assert L no longer retains its overlapping ABOVE box, remains a visible placed label, and its accepted bounds do not intersect E's accepted larger box. This proves accepted boxes do enter the obstacles. Do not weaken the tests to checking only non-overlap; that cannot establish rejection or absence of phantom obstacles.

### T7 — font change owns re-derivation, even with the same source

Use title Alpha, area `(0,0,400,170)`, old full font 24 pt and dense 18 pt derived from the same pinned face, current fonts the normal 12/9 pt pair. At initial centre `(200,120)`, construct a valid previous full-font BELOW label with old font. Move to `(200,135)` without changing source.

Assert its old-height BELOW box now leaves the bottom edge, the current-full-font BELOW box fits and the current-full-font ABOVE box also fits. Replay with current fonts. Assert current full font, current width/height, BELOW retained slot, current source, new anchor, and no base flag. A fresh current-font control selects ABOVE. This rules out treating the font transition as unchanged-content geometric decline; baseline rejects the old box and ladders to ABOVE instead. Do not require the same rung for every possible font-change fixture; require the first eligible fitting row under section 4.

Also cover the entry guards explicitly: null previous is exercised by every initial placement. The dedicated hover-only test starts with a tiny-area HOVER_ONLY Alpha result, then uses a roomy area and a new current title with that previous result. Assert placement succeeds from the current title through the ladder and never tries to look up a HOVER_ONLY face. It is a baseline preservation test.

### T8 — reused canvas renders live text

Add these methods to `GraphCanvasPaintShould`:

```java
@Test
public void paintsTheCurrentTitleOnAReusedCanvasLikeAnAgreeingFreshCanvas()
        throws Exception

@Test
public void paintsARefreshedRetainedFaceInsteadOfThePreviousTitleOnAReusedCanvas()
        throws Exception
```

New private fixture helpers have these signatures:

```java
private static CanvasState liveTitleState(final long generation,
        final String displayText, final LayoutPoint center)
private static GraphCanvas liveTitleCanvas(final CanvasState state, final GraphTheme theme)
private static LabelPlacementRequest liveTitleRequest(final CanvasState state)
```

The state helper uses the stable persisted ID `live-title-edited` in `FIRST_MAP` for every generation, `SafeNodeLabel.of(displayText,displayText)`, one radius-8 node, empty enclosures/edges/relationships/pins, matching generation values for projection, frame and canvas state, `EdgePaths.empty()` and `OperationalStatus.IDLE`. Geometry and positions represent the supplied fixed world centre. The canvas helper sets theme, viewport `(0,0,1)`, empty paint state and canvas state, and sets actual size to the existing `SIZE` (`240x140`) after setup. No containing scroll pane is used. Assert actual size remains SIZE across the state update; preferred-surface resizing must not change the comparison geometry. `liveTitleRequest` mirrors `GraphCanvas.labelsFor` at SIZE, empty forced set and FULL rendering.

Run all canvas setup, state replacement and painting inside `SwingUtilities.invokeAndWait` (or directly on EDT), with failures propagated to JUnit. Use the existing `paintCanvas(canvas)` for both paints of the **same** Canvas A; do not use `paint(...)`, which creates a new canvas. Set theme and viewport before the first paint only. Between paints change only CanvasState; do not force a hover, pan, zoom, font change, cache clear or fresh component.

1. **Agreeing fresh control:** world centre `(0,0)`, generation 1 alpha, generation 2 omega renamed. Paint A at generation 1 before replacing its state. Paint reused A at generation 2. Independently use direct placement to assert retained and fresh generation-2 choices agree on full-font ABOVE, text and all geometry in this one-node roomy fixture. Build Canvas B for generation 2 only with identical theme/viewport/size, paint it, and assert `differentPixels(reusedImage,freshImage) == 0`. Assert the two generation images differ so a blank or unpainted label cannot satisfy the control. Baseline reused A draws alpha and differs from B.
2. **Diverging placement control:** derive the fixture from the theme's own measured metrics. Do not hard-code character counts, pixel widths or a slot prediction.
   - Let `full = theme.labelFont()` and `dense = theme.denseLabelFont()`. Starting from `n = 20`, increase `n` until `textWidth(repeat("W", n), full) > slotMaxWidth(ABOVE)`, stopping at `n = 200`. Set `oldTitle = repeat("W", n)` and assert `textWidth(oldTitle, full) > 200` and `textWidth(oldTitle, dense) <= 200`; if the host face cannot satisfy both, fail with both measured widths in the message rather than adjusting expectations.
   - Choose the world centre from a small ordered candidate list, for example `(-40,0)`, `(-30,0)`, `(-20,0)`, `(-50,0)`, as the first candidate for which direct placement of `oldTitle` and of `alpha` at that centre yield **different faces or different slots**. Assert that some candidate satisfies this before painting, and put the faces and slots found in the message, so a host-dependent collapse is reported as a fixture failure instead of a silent non-divergence.
   - At the chosen centre assert two separate things. First, the retained generation-2 placement of `alpha`, given the generation-1 placement as previous, keeps the **remembered slot** and takes the best face that fits that slot; that is the placement of `alpha` restricted to the remembered slot, not a full fresh placement. Second, the fresh placement of `alpha` with no previous labels differs from that retained result in slot or face. Together they are what makes this control diverging.
   - Paint generation 1 on A, set generation 2, and paint A again. Build the stale control with existing `paintPlaced(generation2State, generation1Labels, theme, 1.0, SIZE, GraphPaintState.empty())`; assert reused generation-2 pixels differ from that control, and compare against `paintPlaced` with the independently computed refreshed retained generation-2 labels, asserting zero different pixels. Do **not** assert equality with a fresh canvas in this diverging fixture.

These tests use the same actual theme font instances within each comparison. They do not depend on a host-specific pixel hash. Do not modify the existing painter or expose the private canvas cache to make a test possible.

### T9 — existing module suite and assertions

The entire ordinary `:freeplane_plugin_graph:test` suite must pass. Existing `ScreenLabelPlacementShould` assertions, including `retainsASlotForAOnePixelMove`, `invalidatesAStaleSlotAndReLadders`, `resetsTheBaseSlotFlagWhenRetainingAnO4Label`, all absolute coordinate pins, leader measurements and enclosure cases remain untouched.

The two old `PlacedLabel` calls in `GraphCanvasPaintShould` gain only the source argument already specified. In particular, the old `retainedLabel` fixture supplies `FULL_DISPLACED` even for a RIGHT slot; do not rewrite that existing fixture or its assertions. RIGHT becomes eligible for the near row during re-derivation, with the same full font, dimensions, RIGHT anchor and leader; this preserves its measured leader expectations.

The ordinary test task already excludes `GraphEdgeLengthSettleShould` and `InitialSpacingAcceptanceShould` (multi-hour acceptance drivers). The full module-suite command below means all tests selected by that existing task, without adding or removing exclusions. The fix does not require launching those separate drivers or the application.

## 8. Errors, cleanup and compatibility

- Retention decline is an ordinary `null` result consumed by `placeNode`; do not throw, log an error or leave a partially inserted box.
- Preserve the initial null/hover guards, existing `SafeNodeLabel` non-empty display guarantee and `truncateTo` non-empty fallback. The new source constructor argument uses the existing null-validation idiom; valid production inputs cannot trigger it. No new checked exception, catch/retry policy, UI error state or application failure mode is introduced.
- Preserve `PlacedLabel`'s existing null checks and finite/positive geometry validation. Do not catch those programming errors and silently substitute stale data.
- Replace the stale retained-value copy path. Do not retain a second legacy execution path, a title-equality-only workaround, cache invalidation on every rename, a feature flag, a deprecated constructor or a compatibility fallback.
- No persistence migration, XML version bump or cleanup of saved workspaces is needed: `sourceText` exists only on in-memory placement outputs. The first pass after startup has no previous value and follows the existing null guard.
- All existing construction sites must compile against the single new signature, including test-only sites. No public export or OSGi change is needed because the class's public API is unchanged and source metadata stays package-private.
- Temporary baseline probes or mutant variants, if used during implementation verification, must not ship in the change. The specification authoring task creates none. No task requires host transitions, deployment, application restarts, branch operations or commits.

## 9. Implementation-phase verification commands and evidence

These commands are deliberately **not executed during specification authoring**. Run from the repository checkout that contains the subsequent implementation, using the required SDKMAN Java 21 and installed `gradle`, never `gradlew` or Maven. `--rerun-tasks` ensures a focused/full result is actually executed rather than reported solely up-to-date.

Focused new placement/cache contracts:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" \
PATH="$HOME/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH" \
gradle :freeplane_plugin_graph:test --rerun-tasks -PTestLoggingFull \
  --tests 'org.freeplane.plugin.graph.canvas.ScreenLabelLiveTitleRefreshShould'
```

Focused new and existing placement, component and live-refresh integration classes:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" \
PATH="$HOME/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH" \
gradle :freeplane_plugin_graph:test --rerun-tasks -PTestLoggingFull \
  --tests 'org.freeplane.plugin.graph.canvas.ScreenLabelLiveTitleRefreshShould' \
  --tests 'org.freeplane.plugin.graph.canvas.ScreenLabelPlacementShould' \
  --tests 'org.freeplane.plugin.graph.canvas.GraphCanvasPaintShould' \
  --tests 'org.freeplane.plugin.graph.integration.GraphWorkspaceLiveNodeRefreshShould'
```

Full ordinary graph module test suite, with **no** `--tests` filter:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" \
PATH="$HOME/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH" \
gradle :freeplane_plugin_graph:test --rerun-tasks -PTestLoggingFull
```

Read each command's actual exit result and the relevant XML files under `freeplane_plugin_graph/build/test-results/test/TEST-*.xml`. Record executed class/test counts, failures and errors separately for focused and full runs. A timeout, hang, dependency-resolution failure or missing XML is not a passing suite. Do not replace the full module suite with focused results or quote an old test count as current evidence. No acceptance-driver or launch-smoke invocation is necessary for this local placement fix.

For intended baseline failures, T1/T2 should show old alpha, T4 upgrade/degrade should keep old rendering, T4f should retain old source, T5 should expose old measurements/leader, T7 should choose the old fallback, and T8 should expose stale pixels. The new constructor/accessor cannot compile against literal baseline Java. If baseline runtime falsification is performed, apply **only** the source-metadata constructor plumbing to a disposable verification copy while keeping baseline `retained` behavior. For that baseline-only shim, its retained constructor call copies `previous.sourceText()` alongside the other previous assets; the other factories initialize the source from current display text. Then run the focused test command. Require failures at the stated semantic assertions, not compilation errors. This is an implementation-phase technique only; the authoring task must not create that copy or run it.

Preservation tests such as T3/T3b/T3c/T3d, unchanged over-cap retention/demotion, T4e with correctly initialized metadata and the hover guard can already pass baseline. Their value is catching the plausible incorrect fixes named in section 7. Do not claim every new test must fail baseline, and do not label a metadata compilation failure as a red behavioral test.

After implementation, review `git diff --check` and the exact Java diff. Confirm the four-file implementation allowlist, all seven old construction sites updated, no old-arity constructor, no second face table, and no existing assertion changes. Read-only comparison against the implementation base must show `ScreenLabelPlacementShould.java`, cache, canvas, painter and the rest of the excluded scope unchanged.

## 10. Resolved implementation ambiguities

1. **Meaning of source:** exact projection `displayText()`, not tooltip/search `fullText()`, rendered text or an ID. Actual 80-code-point hidden-tail coverage tests both storing and comparing that value.
2. **Three outcomes:** geometric rejection of an otherwise unchanged eligible source/font declines immediately; it does not enter re-derivation. Source/font/eligibility changes enter re-derivation even if the old geometry also fails.
3. **Constructor/API placement:** the single package-private final field is named `sourceText`, with mandatory argument after `truncated` and package-private accessor. No old signature or inference fallback survives.
4. **Cap scope:** KEEP has no cap rejection for any node, including forced-to-ordinary histories. Only ordinary non-truncating RE-DERIVE applies its cap. Forced RE-DERIVE skips that cap; the fallback ladder's historical caps are unchanged.
5. **Policy source:** row/font/truncation assets are read from the existing `LADDER`; only slot-family membership and the forced two-rung filter determine eligibility. No duplicated mapping is needed.
6. **Acceptance and geometry:** reuse current square-disc and rectangle intersection semantics, measure the selected rendering, then anchor it with the current centre/radius. Recompute the leader from the new box and insert only accepted boxes.
7. **Fallback comparison:** remove only the declining node from the previous list while preserving accumulated earlier retained obstacles. Whole-scene freshness is an oracle only in explicitly agreeing fixtures.
8. **Stable identity:** fixtures use persisted IDs independent of title. The cache test holds the positions object identity constant so projection generation is the only cache invalidator.
9. **Truncation flag:** derive it from current rendering/source inequality on both accepted paths. A literal ellipsis in a current title does not by itself imply placement truncation.
10. **Coverage granularity:** each design test and lettered variant is mapped above; multi-mechanism cases have separate named JUnit tests. Preservation tests need not fail baseline; claims of behavioral failure must exclude constructor compilation issues.
11. **Canvas controls:** reuse and paint one real `GraphCanvas` twice. Assert fresh-canvas equality only after proving placements agree. When retained and fresh slots differ, compare against a generation-1-label stale image and the correctly retained generation-2 image.
12. **Verification scope:** full module verification uses the existing ordinary test task and its existing multi-hour-driver exclusions. No build/test command or host transition is part of authoring this file.
13. **Canvas fixture portability:** the theme builds its faces from the logical `SansSerif` family, which resolves differently per host, and there is no production factory that accepts explicit fonts. The diverging canvas control therefore derives its title length and centre from the theme's measured metrics and asserts each precondition, instead of pinning widths or a slot; production theme behaviour is unchanged.
