# Graph workspace — a retitled node keeps drawing its previous title

Task identifier: `2026-11-01-graph-workspace-live-title-refresh`

Status: **approved by the user on 2026-11-01.** The user reported "After editing a node's content in the
mindmap, the graph-workspace does not immediately reflect the updated node title", confirmed with a
screenshot showing the disc labelled `Theorem` while the hover bar read
`Theorem: closure in subspace - Point-set_Topology`, and chose fix approach **1** (keep the slot, refresh the
content) from the three presented. This document is the design authority for the technical specification that
follows it.

Baseline: `plugin/graph-workspace` at `8267175e52f49216ae6222bc002e48020690d67c`. All investigation ran in the
PM integration worktree; the user's delivery checkout was read only and never built.

## 1. Reported defect

A node's title is edited in the mind map while the graph workspace window is open. The graph data updates, and
the hover information bar shows the new title, but the title drawn beside the node keeps the pre-edit text. It
stays wrong until something unrelated invalidates the screen placement: a zoom, a pan that pushes the label out
of the drawing area, a hover that forces a different label, a label/label clash, or a font or theme change.

The user's screenshot shows the two halves of the defect at once: the disc is labelled `Theorem`, and hovering
that disc reports `Theorem: closure in subspace - Point-set_Topology`.

## 2. Measured evidence

### 2.1 The refresh path already works

`GraphWorkspaceLiveNodeRefreshShould` covers a plain text edit, position retention across a same-length edit, a
second editor model for the same file, and undo. All four tests pass on the baseline commit:

```text
$ gradle :freeplane_plugin_graph:test \
    --tests 'org.freeplane.plugin.graph.integration.GraphWorkspaceLiveNodeRefreshShould'
BUILD SUCCESSFUL in 1m 5s
```

The event chain is intact: `MapLeaseManager.NodeChangeListener` → `handleMapChanged` →
`MapAdapterEvent` → `GraphUpdateCoordinator.handleMapAdapterEvent` →
`requestSourceRebuild(ChangeKind.MAP_STATE)` → `ProjectionBatcher` (150 ms debounce) → `ProjectionEngine` →
a new `GraphProjection` generation carrying the new `SafeNodeLabel`.

`ProjectedNodeKey` is the `SourceNodeKey` (map id plus persisted node id), so a title edit keeps node identity;
`ProjectedNode.equals` includes the label, so `ProjectionDiff.changedNodes` reports the edit and the settle loop
publishes a new `CanvasState`.

### 2.2 The drawn title is stale

Archived probe: `reports/design-context/PmStaleLabelProbe.java` and `probe-output.txt` in the PM run root
(`run pm-run-20261101-064156-4fca4349`), each hashed. The probe builds two projections over the same four nodes
with stable persisted node identities, generation 1 labelled `alpha` in the edited slot and generation 2
labelled `omega renamed`, and places labels twice.

```text
PROBE cacheFirst:     [alpha/INTERIOR, bravo/INTERIOR, charlie/INTERIOR, delta/INTERIOR]
PROBE cacheSecond:    [alpha/INTERIOR, bravo/INTERIOR, charlie/INTERIOR, delta/INTERIOR]
PROBE directRetained: [alpha/INTERIOR, bravo/INTERIOR, charlie/INTERIOR, delta/INTERIOR]
```

Generation 2 carries `omega renamed`, yet both the production cache path (`ScreenLabelPlacementCache.place`)
and the direct placement call (`ScreenLabelPlacement.place(request, previous, fonts)`) return `alpha`. The
reported symptom is reproduced at the placement layer with no GUI.

### 2.3 Which readers are stale and which are correct

| Reader | Source of the text | State |
| --- | --- | --- |
| Drawn label (`GraphPainter.paintLabels`) | `PlacedLabel.text()` from `ScreenLabelPlacement` | **stale** |
| Hover information bar, tooltips | `ProjectedNode.label().fullText()` read at query time | correct |
| Search (`GraphSearchModel`) | projection, read at query time | correct |
| Accessible surface (`AccessibleGraphCanvas`) | projection, read at query time | correct |
| Disc radius and hull geometry | `GraphGeometryEngine` over the current projection | correct |

## 3. Root cause

`ScreenLabelPlacement.retained(Context, ProjectedNodeKey, PlacedLabel)` reuses the previous frame's
`PlacedLabel` **verbatim**:

```java
return new PlacedLabel(previous.endpoint(), previous.text(), previous.font(), previous.mode(),
    previous.rung(), anchor[0], anchor[1], previous.width(), previous.height(),
    previous.truncated(), isForced(context, key), previous.emphaticAtAnchor(),
    false, false, nodeLeader(previous.slot(), centerX, centerY, radius, anchor[0], anchor[1],
        previous.width(), previous.height()),
    previous.slot());
```

It validates only that the **old** box, at the **new** centre and radius, is still inside the placement area and
does not intersect the discs or the labels already placed. The text, the font, the measured width and height,
and the `truncated` flag are copied from the previous frame and are never compared with the current
`node.label().displayText()`.

`ScreenLabelPlacementCache` does not cause the staleness: its key includes the projection generation, so a title
edit invalidates the key and placement runs again. The cache then passes the previous list as `previous`, and
the retained branch reinstates the old text.

The retention exists for a real purpose: it holds a label in its slot across settling frames, so labels do not
jump while the layout moves. It must therefore be repaired, not removed.

## 4. Goals

1. A title edited in the mind map is drawn with the new text in an already-open graph workspace, with no
   reopen, hover, zoom or pan.
2. Retention keeps doing its real job: an unchanged title keeps exactly the placement it has today, with no
   jitter and no movement.
3. Every way a label's rendered content can change is covered by one rule: a different title, a longer or
   shorter title that changes the truncation, and a different font or theme.
4. The rule is stated once and tested at the layer that owns it, plus at the component that renders it.

## 5. Non-goals

- No change to the map event plumbing, the debounce, the projection, the layout, or the cache key.
- No change to enclosure labels. `ScreenLabelPlacement.place` recomputes them from the current projection on
  every pass; they are never retained.
- No visual redesign: no new fonts, no new slot preference order, no new truncation rules, no new leader rules.
- No change to hover, tooltip, search or accessibility text; they already read live data.
- No attempt to make a retitled label stay in the same slot when the new text does not fit there.

## 6. Design

### 6.1 The contract

> Retention preserves the slot decision, never the content. The text, the font, and every measurement come from
> the current projection and the current theme on every pass; only the choice of slot is carried forward.

### 6.2 Retention, restated

Given a previous `PlacedLabel p` for a node whose current display text is `fullText`:

1. Resolve the rung's assets from the existing `LADDER` table: the `LadderRung` whose `rung` equals `p.rung()`.
   If no rung matches (`HOVER_ONLY`), do not retain. The table is the single source of truth for
   rung → font selector (full or dense) and rung → truncating, so this rule cannot drift from the ladder.
2. `font` is the current theme's full or dense face for that rung.
3. `limit = slotMaxWidth(p.slot())`. If the rung is not truncating, the whole `fullText` must fit the limit at
   `font`; otherwise do not retain, because the ladder would not offer that rung for this text either.
4. `text` is `fullText` for a non-truncating rung, and `truncateTo(fullText, font, limit)` for a truncating
   rung. `truncated` is `!text.equals(fullText)`.
5. Measure `text` at `font` and compute the anchor with the current centre, the current radius, and the new box
   size.
6. Keep the slot only if the new box is inside the placement area and does not intersect any obstacle already
   in the context. On acceptance, add the new box to the obstacles and emit a `PlacedLabel` carrying
   `p.endpoint()`, the new text, the new font, `p.mode()` (always `INTERIOR` here, because `HOVER_ONLY`
   returns early), `p.rung()`, the new anchor and the new width and height, the new `truncated` flag,
   `forced = isForced(context, key)`, `p.emphaticAtAnchor()` (false for node labels),
   `forcedAtBaseSlot = false`, `fullTextSlotWasFree = false`, `p.slot()`, and a leader line recomputed for the
   new box with `nodeLeader(p.slot(), centre, radius, newBox)`.
7. Otherwise return no placement and let the caller continue with `ladder(...)`.

Steps 1-5 are recomputation. Step 6 is the only survival test, and it is the same test retention applies today.

### 6.3 Why the old anchor cannot simply be kept

The anchor is derived from the box size, and the box size changes when the text changes. Keeping the old anchor
while drawing a wider text would centre the new text on the old anchor and silently widen the label's footprint
without accounting for it in the obstacles, so a neighbouring label could end up drawn over the retitled one.
Recomputing the anchor keeps obstacle accounting honest.

### 6.4 Ordering and obstacles

Retention is attempted in the same position in the pipeline as today: after the forced labels and the enclosure
labels, in the radius-descending node order. A rejected retention therefore leaves no obstacle behind, and the
ladder that follows starts from the same obstacle set the previous frame's rejection would have produced.

### 6.5 Interaction with the ladder

When retention is rejected, `placeNode` continues exactly as it does today: `ladder(...)`, then `baseSlot(...)`
when forced, then `hoverOnly(...)`. A retitled label that no longer fits its slot therefore moves through the
same rungs, in the same order, with the same caps. The only new outcome is that a stale text can never win.

### 6.6 What may visibly change, and what may not

| Situation | Before | After |
| --- | --- | --- |
| Title unchanged | retained slot and box | identical slot, box, and leader |
| Title changed, new text fits the retained slot | stale text drawn, old box | new text drawn, newly measured box in the same slot |
| Title changed, new text does not fit | stale text drawn | re-placed through the ladder, possibly in another slot |
| Theme or font changed | retained label keeps the old face and size | re-measured with the current face |
| Label was hover-only | re-placed from the projection | unchanged |

## 7. Recorded decision and mockup

Approach **1** was chosen by the user from three presented options:

1. keep the slot, refresh the content — chosen;
2. guard on text equality only — rejected: it leaves the stale-font and stale-measurement path open;
3. clear remembered placements when any label differs — rejected: it discards placement stability for the whole
   picture after every edit and still misses font changes.

No mockup is attached. This change introduces no new visual design and offers no competing layouts to choose
between: fonts, slot order, truncation and leader rules are untouched, and the only visual difference is that a
title reflects its current text. The user approved that reasoning when approving the design.

## 8. Error handling

Rendering-only change. No new failure modes and no new exceptions.

- A rejected retention returns no placement; the existing ladder path handles it. `placeNode` already
  guarantees an outcome through `baseSlot` or `hoverOnly`.
- `LADDER` has no entry for `HOVER_ONLY`, and the existing `mode() == HOVER_ONLY` guard already returns before
  the rung lookup. Both checks stay; neither may be dropped.
- Truncation always yields a non-empty string (`truncateTo` falls back to three characters plus an ellipsis), so
  a retained label can never be constructed with empty text, which `PlacedLabel` rejects.

## 9. Test strategy

New tests live in `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/`. Each test has one
reason to fail.

### T1 — the reported bug, at the placement layer

Two projections over the same nodes with the same persisted identities and generation 1 then 2; the edited
node's label changes from `alpha` to `omega renamed`; pass the first pass's labels as `previous`. Assert the
second pass places `omega renamed` and no longer places `alpha`.

### T2 — the production entry point

The same scenario driven through `ScreenLabelPlacementCache`: place for generation 1, then for generation 2 on
the same cache instance. Assert the returned list carries the new text. This pins that the cache layer cannot
mask the fix and that its generation key still triggers recomputation.

### T3 — stability is preserved

Two passes over identical data: the second pass, given the first pass's labels, must return the same slot,
anchor, width and height for every node. This is the guard against "fixing" the bug by disabling retention.

### T4 — truncation follows the new text

A long title replaced by a short one, and a short title replaced by a long one, with labels that need the
truncating rungs. Assert the drawn text equals `truncateTo(currentText, font, limit)` for the retained rung: no
stale ellipsis, no stale tail.

### T5 — a font change is not carried over

The same text placed with two different `LabelFonts`: the second pass must measure with the current face. Assert
the emitted `font()` equals the current theme face for that rung, and the width matches the current face's
measurement rather than the previous one.

### T6 — a reused canvas renders what a fresh canvas renders

Component-level, using the existing `GraphCanvas` paint helpers. Canvas A receives generation 1, paints, then
receives generation 2 and paints. Canvas B, freshly constructed, receives generation 2 only and paints. Assert
the two images are pixel-identical. This is the invariant the user actually cares about: an already-open window
shows what a freshly opened window would show. It is also the test that would have caught the reported bug,
because every current test asserts the projection and none asserts the rendered result.

### T7 — the existing suite is unchanged

The whole `freeplane_plugin_graph` test suite must pass. The pinned absolute-coordinate assertions in
`ScreenLabelPlacementShould` cover unchanged-text placement, including the retained-leader assertions
(`assertRetainedNodeLeadersAtTheRim`), and must not be edited to accommodate the fix.

## 10. Invariants

1. `ScreenLabelPlacement.retained` never emits a `PlacedLabel` whose `text()` differs from what the current
   projection and the retained rung produce.
2. Retention still reuses the previous slot when the content is unchanged; T3 pins it.
3. `LADDER` remains the only rung table. No second rung→font or rung→truncating mapping is introduced.
4. No public or package API changes. Only the body of `retained` and, if needed, one private helper changes.
5. Enclosure placement, the cache key, the projection, the layout and the painter are untouched.

## 11. Risks

1. **Visible movement after an edit.** If the new title does not fit the retained slot, the label moves. That is
   intended: the alternative is a drawing that contradicts the data. T4 and T6 pin the new placement rather than
   the old one.
2. **Obstacle-accounting regressions.** Changing what is added to the obstacle list can move neighbouring
   labels. T3 and T7 constrain it: unchanged content must produce unchanged numbers, and the pinned fixtures
   must still match.
3. **Truncation drift under a font change.** A truncating rung re-truncates with the current font, which can
   produce a different cut point. T5 asserts the current face's measurement, not a copied one.
4. **Scope creep into enclosure labels.** Enclosure labels have no retention path; touching them would widen the
   change without evidence. Section 5 excludes them.
