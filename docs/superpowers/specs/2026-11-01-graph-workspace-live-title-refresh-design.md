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

Revision history:

- r1 (commit `9a0cebe64a`) — first complete design. Reviewed by an independent frontier session
  (`01a1965e-d9c7-76d3-a572-6af434bda92d`), verdict **CHANGES_REQUIRED**, three blocking and three
  non-blocking findings, recorded in the PM run's `reports/design-review-1.md`.
- r2 (this revision) — applies all six findings and resolves the open style question in favour of
  re-deriving the rung for the retained slot. It also adds a rung/slot class pairing guard (6.2 step 3.1a) that
  the PM found while applying the findings: a rung's `far` flag decides which slot family the ladder pairs it
  with, so re-derivation must not combine a near slot with a displaced rung. Section 12 records each finding and
  its resolution.

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
`PlacedLabel` **as a value**:

```java
return new PlacedLabel(previous.endpoint(), previous.text(), previous.font(), previous.mode(),
    previous.rung(), anchor[0], anchor[1], previous.width(), previous.height(),
    previous.truncated(), isForced(context, key), previous.emphaticAtAnchor(),
    false, false, nodeLeader(previous.slot(), centerX, centerY, radius, anchor[0], anchor[1],
        previous.width(), previous.height()),
    previous.slot());
```

It carries over the endpoint, the text, the font, the mode, the rung, the width, the height, the truncation
flag, the slot and `emphaticAtAnchor`; it recomputes the anchor, the forced flag and the leader, and it clears
`forcedAtBaseSlot` and `fullTextSlotWasFree`. Its only rejection tests are that the **old** box, at the
**new** centre and radius, is still inside the placement area and does not intersect the discs or the labels
already placed.

Nothing compares the carried text, font, width, height or truncation flag with the current
`node.label().displayText()` and the current theme. That omission is the defect.

`ScreenLabelPlacementCache` does not cause the staleness: its key includes the projection generation, so a title
edit invalidates the key and placement runs again. The cache then passes the previous list as `previous`, and
the retained branch reinstates the old text.

The retention exists for a real purpose: it holds a label in its slot across settling frames and small pans, so
labels do not jump while the layout moves. It must therefore be repaired, not removed.

## 4. Goals

1. A title edited in the mind map is drawn with the new text in an already-open graph workspace, with no
   reopen, hover, zoom or pan.
2. Retention keeps doing its real job: an unchanged title keeps exactly the placement it has today, with no
   jitter, no movement, and no change to any pinned fixture.
3. Every way a label's rendered content can change is covered: a different title, a longer or shorter title that
   changes the truncation, and a different font or theme.
4. A changed title is redrawn in the slot it already occupies whenever any rung fits that slot, so labels stop
   walking to another slot merely because their text grew.
5. The rule is stated once and tested at the layer that owns it, plus at the component that renders it.

## 5. Non-goals

- No change to the map event plumbing, the debounce, the projection, the layout, or the cache key.
- No change to enclosure labels. `ScreenLabelPlacement.place` recomputes them from the current projection on
  every pass; they are never retained.
- No visual redesign: no new fonts, no new slot preference order, no new truncation rules, no new leader rules.
- No change to hover, tooltip, search or accessibility text; they already read live data.
- No change to the forced base-slot policy, which deliberately ignores the slot width cap.
- No claim that a reused placement always equals a fresh one; see 6.7 and T6.

## 6. Design

### 6.1 The contract

> Retention preserves where the label is placed, never what it says. The slot and the fact that the label
> occupies it are carried forward. The text, the rung, the font, the truncation, every measurement and the
> leader are recomputed from the current projection, the current theme, and the current obstacle set on every
> pass.

Two properties follow, and both are testable:

- **P1 — unchanged content is byte-identical.** For an unchanged title and unchanged geometry, the rung
  re-derived for the retained slot equals the rung the label has today, so every pinned fixture keeps its exact
  numbers. The reason is structural: the ladder settled on that rung for that slot only after rejecting every
  earlier rung at that slot, and re-derivation walks the same table in the same order.
- **P2 — changed content never keeps a stale style.** A retitled label takes the best rung that fits its
  retained slot, which may be denser or truncated, and it leaves the slot only when no rung fits there at all.

### 6.2 Retention, restated

Given a previous `PlacedLabel p` for a node whose current display text is `fullText`:

1. If `p.mode()` is `HOVER_ONLY`, do not retain. This guard exists today and stays.
2. `slot = p.slot()`. `forced = isForced(context, key)`.
3. Walk the `LADDER` table in its declared order, considering **only** `slot` as the candidate slot:
   1. Resolve the rung's assets from the table: the font selector (full or dense) and the truncating policy.
      The table is the single source of truth, so this rule cannot drift from the ladder.
   1a. Skip a rung whose `far` flag does not match the class of `slot`. A rung's `far` flag decides whether the
       ladder pairs it with `NEAR_SLOTS` or `FAR_SLOTS`, so a near slot must never acquire a displaced rung, or
       the other way round. Without this guard the retained label could take a rung-and-slot pairing the ladder
       never produces.
   2. `font` is the current theme's face for that selector.
   3. Let `limit = slotMaxWidth(slot)`. When the rung is not truncating, non-forced labels require
      `textWidth(fullText, font) <= limit`; otherwise this rung is skipped. **Forced labels skip this test**,
      because the forced base-slot policy is deliberately uncapped (see 6.6).
   4. `text` is `fullText` for a non-truncating rung and `truncateTo(fullText, font, limit)` for a truncating
      rung. `truncated = !text.equals(fullText)`.
   5. Measure `text` at `font` and compute the anchor with the current centre, the current radius, and this
      rung's box size.
   6. If the box lies inside the placement area and intersects none of the obstacles already in the context,
      accept this rung: add the box to the obstacles and emit a `PlacedLabel` carrying `p.endpoint()`, `text`,
      `font`, `p.mode()`, the accepted `rung`, the anchor, the measured width and height, `truncated`,
      `forced`, `p.emphaticAtAnchor()`, `forcedAtBaseSlot = false`, `fullTextSlotWasFree = false`, `slot`, and a
      leader recomputed with `nodeLeader(slot, centre, radius, box)`.
   7. Otherwise continue with the next rung.
4. If no rung is accepted at `slot`, return no placement. The caller then continues with the ordinary ladder,
   exactly as today, and `baseSlot` still serves a forced label that fits no rung.

Rejection can therefore happen at step 1, at step 3.3 for a non-forced label, at step 3.6, or at step 4. Each
rejection happens before anything is added to the obstacle list, so a declined retention leaves no trace.

### 6.3 Why the old anchor cannot simply be kept

The anchor is derived from the box size, and the box size changes when the text or the rung changes. Keeping the
old anchor while drawing a differently sized text would centre the new text on the old anchor and silently
widen or narrow the label's footprint without accounting for it in the obstacles, so a neighbouring label could
end up drawn over the retitled one. Recomputing the anchor from the new box keeps obstacle accounting honest.

### 6.4 Passes and ordering

`place(...)` runs three passes, and retention participates in the node passes only:

1. **Forced nodes** — `placeForced`, radius descending; each node attempts retention, then the forced ladder,
   then `baseSlot`. Retention here runs **before** enclosure placement.
2. **Enclosures** — emphatic tier first, then subtle. Not retained; always recomputed.
3. **Ordinary nodes** — `placeNodes`, radius descending; each node attempts retention, then the ladder, then
   hover-only.

Obstacles accumulate across all three passes in that order. Any test that asserts what a later label may
overlap must respect this ordering.

### 6.5 Interaction with the ladder

When retention is declined at the retained slot, `placeNode` continues exactly as it does today: `ladder(...)`,
then `baseSlot(...)` when forced, then `hoverOnly(...)`. The ladder keeps its own slot order and caps, so a
label that cannot fit its old slot moves through the same rungs a fresh placement would use.

### 6.6 The forced base-slot exception

`ScreenLabelPlacementShould.resetsTheBaseSlotFlagWhenRetainingAnO4Label` forces the ~274 px
`LONG_NAMES[0]` label into an `ABOVE` slot whose cap is 200 px. The baseline takes `baseSlot`, which ignores
the cap, and sets `forcedAtBaseSlot = true`; retention then clears that flag while keeping mode, rung, anchor,
width and height.

This design keeps that behaviour unchanged. A forced label skips the width cap in step 3.3, so the first rung
`FULL_NEAR` is accepted at the retained slot with the same measurements as today, and `forcedAtBaseSlot` is
cleared exactly as the existing test requires. The sentence "the forced policy ignores the slot cap" is an
invariant, not an accident, and it is recorded in section 10.

### 6.7 What may visibly change, and what may not

| Situation | Before | After |
| --- | --- | --- |
| Title unchanged | retained slot, rung, box | identical slot, rung, box, leader, and numbers |
| Title changed, same rung still fits | stale text drawn | new text, same rung, re-measured box in the same slot |
| Title changed, needs a denser or truncated rung | stale text drawn | new text in a denser or truncated rung, still in the same slot |
| Title changed, nothing fits the slot | stale text drawn | ordinary ladder, possibly another slot |
| Theme or font changed | retained label keeps the old face and size | re-derived rung measured with the current face |
| Label was hover-only | re-placed from the projection | unchanged |
| Forced label over the cap | uncapped full text | unchanged, uncapped full text |

A retained placement does **not** always equal a fresh placement, and this design does not claim otherwise. A
fresh pass chooses the first rung-and-slot pair that is free in ladder order and may prefer a roomier slot over
the retained one. What is guaranteed is that the retained label carries current content, and that its style is
the best one available at the slot it keeps.

## 7. Recorded decisions and mockup

Approach **1** was chosen by the user from three presented options:

1. keep the slot, refresh the content — chosen;
2. guard on text equality only — rejected: it leaves the stale-font and stale-measurement path open;
3. clear remembered placements when any label differs — rejected: it discards placement stability for the whole
   picture after every edit and still misses font changes.

The rung question was put to the user after the first review and resolved as **re-derive**: for an unchanged
title P1 keeps the result identical, and for a changed title the label degrades its style in place rather than
walking to another slot. The PM recommended the same, once P1 removed the fixture-churn risk.

No mockup is attached. This change introduces no new visual design and offers no competing layouts to choose
between: fonts, slot order, truncation and leader rules are untouched, and the only visual difference is that a
title reflects its current text. The user approved that reasoning when approving the design.

## 8. Error handling

Rendering-only change. No new failure modes and no new exceptions.

- A declined retention returns no placement; the existing ladder path handles it. `placeNode` already
  guarantees an outcome through `baseSlot` or `hoverOnly`.
- `LADDER` has no entry for `HOVER_ONLY`, and the `mode() == HOVER_ONLY` guard at step 1 returns first. Both
  checks stay; neither may be dropped.
- Text is never empty. `SafeNodeLabel` guarantees a non-empty display text, which is the guarantee this design
  relies on. `PlacedLabel` separately rejects null text and non-finite or non-positive geometry, which the
  re-measured box always satisfies.
- Truncation always yields a non-empty string (`truncateTo` falls back to three characters plus an ellipsis).

## 9. Test strategy

New tests live in `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/`. Each test has one
reason to fail. Fixtures must keep node identity stable while their displayed label changes; the existing
`key(name)` helper derives identity from the title, so these tests construct requests explicitly.

### T1 — the reported bug, at the placement layer

Two projections over the same nodes with the same persisted identities and generation 1 then 2; the edited
node's label changes from `alpha` to `omega renamed`; pass the first pass's labels as `previous`. Assert the
second pass places `omega renamed` and no longer places `alpha`.

### T2 — the production entry point

The same scenario driven through `ScreenLabelPlacementCache`: place for generation 1, then for generation 2 on
the same cache instance. Assert the returned list carries the new text. This pins that the cache layer cannot
mask the fix and that its generation key still triggers recomputation.

### T3 — unchanged content is byte-identical

Two passes over identical data: the second pass, given the first pass's labels, must return the same slot, rung,
anchor, width and height for every node. This is P1, and it guards against "fixing" the bug by disabling
retention or by re-deciding the rung.

### T3b — retention is still switched on

A retention witness: a small movement for which the previous slot is still valid but a fresh placement would
choose a different slot. Assert the retained result keeps the previous slot, and assert with a
`previous == null` control on the same geometry that the fresh placement chooses the different slot. Without
this, an implementation with retention removed still passes T3.

### T4 — style is re-derived for the retained slot

- A long title replaced by a short one must upgrade the rung in place rather than keeping the dense or
  truncated style.
- A short title replaced by a long one that still fits a denser rung at that slot must degrade in place and
  keep the slot.
- A title so long that no rung fits the slot must decline retention and let the ordinary ladder place it
  elsewhere.
- A retained near slot must never acquire a displaced rung, and a retained far slot must never acquire a near
  rung. Assert the `far` class of the accepted rung against the slot's family in every re-derivation case.

### T5 — measurement follows the content

A same-font title edit in an `ABOVE` slot: assert the emitted width and height equal an independent
`screenBounds` measurement of the new text, that the anchor is recomputed from that height, that the truncation
flag matches the text, and that the leader is recomputed. Text assertions alone cannot catch copied
dimensions, and an `ABOVE` anchor does not change with width, so this test must assert the numbers directly.

### T6 — obstacle accounting for a changed box

Three rejection cases, one per obstacle kind: an expanded box that meets a disc, one that meets an earlier
label, and one that leaves the placement area. For each, assert that retention declined, that the rejected box
was **not** added to the obstacles (a later label may still occupy that space), and that after an accepted
larger box a later ordinary label does not overlap it.

### T7 — a font change is not carried over

The same text placed with two different `LabelFonts`: the second pass must measure with the current face.
Assert the emitted `font()` equals the current theme face for the re-derived rung, and the width matches the
current face's measurement.

### T8 — a reused canvas draws the current title

Component-level, using `paintCanvas(canvas)`; the higher-level `paint(...)` helper constructs a fresh canvas per
call and would not exercise retention. Canvas A receives generation 1, paints, then receives generation 2 and
paints. Two assertions:

- On a fixture where the retained and fresh choices provably agree, canvas A's image is pixel-identical to a
  freshly constructed canvas that receives generation 2 only.
- On a fixture where they do not agree (a shortened title that upgrades its rung), canvas A's generation-2
  image must differ from a control that paints the generation-2 state with the generation-1 label list. That
  control is exactly what a stale implementation draws, so the assertion fails before the fix and passes after
  it, and it is the assertion that pins the reported bug at the pixel level.

### T9 — the existing suite is unchanged

The whole `freeplane_plugin_graph` test suite must pass, and no existing test may be edited to accommodate the
fix. The pinned absolute-coordinate assertions in `ScreenLabelPlacementShould` cover unchanged-content placement,
including `retainsASlotForAOnePixelMove` and `resetsTheBaseSlotFlagWhenRetainingAnO4Label`.

## 10. Invariants

1. `ScreenLabelPlacement.retained` never emits a `PlacedLabel` whose `text()`, `font()` or `truncated()` differs
   from what the current projection, the current theme and the accepted rung produce.
2. For unchanged content and geometry, the retained label is byte-identical to today's, including its rung.
3. `LADDER` remains the only rung table. No second rung→font or rung→truncating mapping is introduced, and a
   retained slot never takes a rung from the other slot family.
4. Forced labels remain uncapped. `forcedAtBaseSlot` is false on a retained label and true only from
   `baseSlot`.
5. A declined retention adds nothing to the obstacle list.
6. No public or package API changes. Only the body of `retained` and, if needed, one private helper changes.
7. Enclosure placement, the cache key, the projection, the layout and the painter are untouched.

## 11. Risks

1. **Visible style change after an edit.** A retitled label may switch to a denser or truncated face while
   staying in its slot. That is intended: the alternative is a drawing that contradicts the data. T4 pins it.
2. **Visible movement after an edit.** When no rung fits the retained slot, the label moves. T4's third case
   pins it.
3. **Obstacle-accounting regressions.** Changing what is added to the obstacle list can move neighbouring
   labels. T3 constrains unchanged content, T6 constrains changed content, and T9 constrains every pinned
   fixture.
4. **The forced exception drifting.** A future change could apply the cap to forced labels and silently break
   `resetsTheBaseSlotFlagWhenRetainingAnO4Label`. T9 keeps that test, and invariant 4 names the rule.
5. **Over-claiming the fresh-canvas property.** A retained placement is not always a fresh placement. T8
   asserts equality only where the two provably agree, and 6.7 states the limit.
6. **Scope creep into enclosure labels.** Enclosure labels have no retention path; touching them would widen the
   change without evidence. Section 5 excludes them.

## 12. Review round 1 findings and their resolution

| # | Severity | Finding | Resolution |
| --- | --- | --- | --- |
| B1 | blocking | The width check contradicted the existing forced over-cap base-slot test, and "step 6 is the only survival test" was wrong | 6.2 step 3.3 skips the cap for forced labels; 6.6 records the policy; 6.2 names every rejecting step; invariant 4 and risk 4 pin it |
| B2 | blocking | 6.1 said only the slot survives while 6.2 carried the rung, which also broke the universal fresh-canvas claim | 6.1 restated; the rung is now re-derived (section 7); 6.7 and T8 restrict the fresh-canvas claim |
| B3 | blocking | Tests could not catch copied dimensions or obstacle-accounting errors | T5 asserts measurements; T6 asserts the three rejection cases, no phantom obstacle, and later-label respect |
| N1 | non-blocking | Identical-input replay does not prove retention is enabled | T3b adds a retention witness with a fresh-placement control |
| N2 | non-blocking | The ordering paragraph omitted retention during the forced pass | 6.4 describes the three passes in their real order |
| N3 | non-blocking | The empty-text guarantee was attributed to the wrong class | Section 8 attributes it to `SafeNodeLabel` and separates `PlacedLabel`'s geometry validation |
