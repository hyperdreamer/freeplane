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

- r1 (commit `9a0cebe64a`) — first complete design. Review round 1 (`01a1965e-d9c7-76d3-a572-6af434bda92d`):
  **CHANGES_REQUIRED**, three blocking, three non-blocking. Recorded in the PM run's
  `reports/design-review-1.md`.
- r2 (commit `032337648c`) — applied round 1 and adopted unconditional re-derivation of the rung.
  Review round 2 (`01a1967a-3c07-76d3-a572-6b1598463ebb`): **CHANGES_REQUIRED**, three blocking, one
  non-blocking. Unconditional re-derivation was shown to change the style of an unchanged title when geometry
  shifts, and to let forced labels take dense or truncated faces.
- r3 (this revision) — keeps re-derivation for a changed title, and keeps the remembered style while the drawn
  text and face are unchanged. Forced labels are restricted to the full-font rungs. Section 12 records every
  finding from both rounds and its resolution.

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
2. Retention keeps doing its real job: while a label would draw the same text in the same face, it keeps exactly
   the placement it has today, with no jitter, no movement, and no change to any pinned fixture.
3. Every way a label's rendered content can change is covered: a different title, a longer or shorter title that
   changes the truncation, and a different font or theme.
4. A changed title is redrawn in the slot it already occupies whenever any rung the label is eligible for fits
   that slot, so labels stop walking to another slot merely because their text grew.
5. A label whose title did not change never changes its face while the layout moves.
6. The rule is stated once and tested at the layer that owns it, plus at the component that renders it.

## 5. Non-goals

- No change to the map event plumbing, the debounce, the projection, the layout, or the cache key.
- No change to enclosure labels. `ScreenLabelPlacement.place` recomputes them from the current projection on
  every pass; they are never retained.
- No visual redesign: no new fonts, no new slot preference order, no new truncation rules, no new leader rules.
- No change to hover, tooltip, search or accessibility text; they already read live data.
- No change to the forced-rung policy: a forced label uses only the full-font rungs, and the forced base slot
  ignores the width cap.
- No claim that a reused placement always equals a fresh one; see 6.7 and T8.

## 6. Design

### 6.1 The contract

> Retention preserves where the label is placed, never what it says. The slot and the fact that the label
> occupies it are carried forward. The text and every measurement are recomputed from the current projection,
> the current theme, and the current obstacle set on every pass. The face — the rung — is kept while the label
> would draw the same text in the same face, and is re-derived for the slot as soon as that is no longer true.

Three properties follow, and all three are testable:

- **P1 — an unchanged rendering is byte-identical.** When the current title at the remembered rung produces the
  same drawn text in the same font, the remembered rung is kept and the measurements recomputed from it, so the
  label keeps its exact numbers. This holds for every history, including a rung that an earlier retention
  produced, because the test is on the rendering rather than on how the rung was reached.
- **P2 — changed content never keeps a stale face.** When the drawn text or the face would differ, the rung is
  re-derived for the retained slot, so a title that grew takes a denser or truncated rung and a title that
  shrank takes a fuller one.
- **P3 — no style oscillation while the content is unchanged.** Because P1 keeps the remembered rung, a settling
  layout that shifts boxes cannot flip a label between faces.

### 6.2 Retention, restated

Given a previous `PlacedLabel p` for a node whose current display text is `fullText`:

**Step 0 — guards.**
1. If no previous label exists, decline. `placeNode` calls retention on the first placement too.
2. If `p.mode()` is `HOVER_ONLY`, decline. Both guards exist today and stay.

**Step 1 — eligibility.** `forced = isForced(context, key)`. A candidate rung is eligible for this label only
when all hold:
1. its `far` flag matches the class of the retained slot (`NEAR_SLOTS` or `FAR_SLOTS`);
2. when `forced`, the rung is `FULL_NEAR` or `FULL_DISPLACED`. This mirrors the forced ladder, which offers
   forced labels nothing else.

**Step 2 — stability check.** Resolve the remembered rung's assets from the `LADDER` table: font selector and
truncating policy. `font` is the current theme's face for that selector. Let `limit = slotMaxWidth(slot)`.
- `text` is `fullText` for a non-truncating rung, and `truncateTo(fullText, font, limit)` for a truncating one.
- The remembered rung is kept when `text.equals(p.text())` and `font.equals(p.font())` and, for a non-forced
  label on a non-truncating rung, `textWidth(text, font) <= limit`.
- Measure `text` at `font`, compute the anchor from the current centre, current radius and this box, and keep
  the rung when the box is inside the placement area and intersects no obstacle already in the context.

**Step 3 — re-derive the face.** Otherwise walk the `LADDER` table in its declared order, considering **only**
`slot` and only rungs eligible under step 1:
1. Resolve the rung's assets from the table. The table stays the single source of truth for rung → font and
   rung → truncating, so this rule cannot drift from the ladder.
2. Skip a non-truncating rung for a non-forced label when `textWidth(fullText, font) > limit`. Forced labels
   skip this width test, because the forced base-slot policy is deliberately uncapped.
3. Compute `text` and `truncated = !text.equals(fullText)` for the rung's truncating policy, measure it, and
   compute the anchor and box as in step 2.
4. Accept the first rung whose box is inside the placement area and intersects no existing obstacle.

**Step 4 — emit or decline.** On acceptance, add the box to the obstacles and emit a `PlacedLabel` carrying
`p.endpoint()`, `text`, `font`, `p.mode()`, the accepted `rung`, the anchor, the measured width and height,
`truncated`, `forced`, `p.emphaticAtAnchor()`, `forcedAtBaseSlot = false`, `fullTextSlotWasFree = false`,
`slot`, and a leader recomputed with `nodeLeader(slot, centre, radius, box)`.

If neither step 2 nor step 3 accepts a rung, return no placement. The caller then continues with the ordinary
ladder, exactly as today, and `baseSlot` still serves a forced label that fits no rung.

Every rejection happens before anything is added to the obstacle list, so a declined retention leaves no trace.

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

### 6.6 The forced-label policy

Two rules constrain forced labels, and both are invariants rather than accidents:

1. **Eligible rungs.** The forced ladder offers only `FULL_NEAR` and `FULL_DISPLACED`. Re-derivation applies the
   same filter, so a forced label can never be drawn in a dense or truncated face by retention. Without this,
   a forced label whose full-font box clashes at its slot would silently shrink instead of moving.
2. **Uncapped base slot.** `ScreenLabelPlacementShould.resetsTheBaseSlotFlagWhenRetainingAnO4Label` forces the
   ~274 px `LONG_NAMES[0]` label into an `ABOVE` slot whose cap is 200 px. The baseline takes `baseSlot`, which
   ignores the cap and sets `forcedAtBaseSlot = true`; retention then clears that flag while keeping mode, rung,
   anchor, width and height. Step 3.2 above keeps that behaviour: a forced label skips the width test, and the
   first eligible full-font rung is accepted with today's measurements, while `forcedAtBaseSlot` is cleared.

### 6.7 What may visibly change, and what may not

| Situation | Before | After |
| --- | --- | --- |
| Title unchanged, geometry unchanged | retained slot, rung, box | identical slot, rung, box, leader, and numbers |
| Title unchanged, geometry shifted | retained rung, old box if it still fits | identical rung and numbers; no style change |
| Title changed, same rung still draws it | stale text drawn | new text, same rung, re-measured box in the same slot |
| Title changed, needs a denser or truncated rung | stale text drawn | new text in a denser or truncated rung, still in the same slot |
| Title changed, nothing eligible fits the slot | stale text drawn | ordinary ladder, possibly another slot |
| Theme or font changed | retained label keeps the old face and size | rung re-derived and measured with the current face |
| Label was hover-only | re-placed from the projection | unchanged |
| Forced label, title unchanged | uncapped full text | unchanged, uncapped full text |
| Forced label, title changed and no full rung fits | stale text drawn | ordinary forced ladder, then base slot |

A retained placement does **not** always equal a fresh placement, and this design does not claim otherwise. A
fresh pass chooses the first rung-and-slot pair that is free in ladder order and may prefer a roomier slot over
the retained one. What is guaranteed is that the retained label carries current content, that its face is the
best one available at the slot it keeps whenever its content changed, and that it does not change face when its
content did not.

## 7. Recorded decisions and mockup

Approaches presented and chosen:

1. keep the slot, refresh the content — chosen;
2. guard on text equality only — rejected: it leaves the stale-font and stale-measurement path open;
3. clear remembered placements when any label differs — rejected: it discards placement stability for the whole
   picture after every edit and still misses font changes.

The face question was put to the user after round 1 and answered **re-derive**, so that a title which shrinks
does not keep a smaller face forever. Round 2 proved that unconditional re-derivation also changes the face of a
title that did not change at all, because the remembered rung may have been reached under different geometry.
This revision therefore keeps the user's intent — a changed title gets the best face for its slot — and bounds
it: the remembered face is kept exactly while the label would draw the same text in the same font, and re-derived
whenever it would not. That is the only difference between r2's rule and this one, and it is the difference that
removes style flicker.

No mockup is attached. This change introduces no new visual design and offers no competing layouts to choose
between: fonts, slot order, truncation and leader rules are untouched, and the only visual difference is that a
title reflects its current text. The user approved that reasoning when approving the design.

## 8. Error handling

Rendering-only change. No new failure modes and no new exceptions.

- A declined retention returns no placement; the existing ladder path handles it. `placeNode` already
  guarantees an outcome through `baseSlot` or `hoverOnly`.
- `LADDER` has no entry for `HOVER_ONLY`, and step 0 returns before any lookup. Both the null-previous guard and
  the hover-only guard stay; neither may be dropped, because first placement calls retention without a previous
  label.
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

### T3 — an unchanged rendering is byte-identical

Two passes over identical data: the second pass, given the first pass's labels, must return the same slot, rung,
anchor, width and height for every node. Repeat for a label that was itself produced by an earlier retention at
a different geometry and is replayed at the final geometry, which is the round-2 counterexample. This is P1, and
it guards against "fixing" the bug by disabling retention or by re-deciding the face on every pass.

### T3b — retention is still switched on

A retention witness: a small movement for which the previous slot is still valid but a fresh placement would
choose a different slot. Assert the retained result keeps the previous slot, and assert with a
`previous == null` control on the same geometry that the fresh placement chooses the different slot. Without
this, an implementation with retention removed still passes T3.

### T4 — the face follows changed content

- A long title replaced by a short one must upgrade the rung in place rather than keeping the dense or
  truncated face.
- A short title replaced by a long one that still fits a denser rung at that slot must degrade in place and keep
  the slot.
- A title so long that no eligible rung fits the slot must decline retention and let the ordinary ladder place
  it elsewhere.
- A retained near slot never takes a displaced rung, and a retained far slot never takes a near rung.

### T4b — forced labels keep the forced-rung policy

- A forced label whose title changed and whose full-font box no longer fits its slot must **not** take a dense
  or truncated rung. Assert that retention declined and that the ordinary forced ladder or base slot placed it,
  using `invalidatesAStaleSlotAndReLadders` as the model.
- An unchanged forced over-cap label keeps its face and numbers, per 6.6.

### T5 — measurement follows the content

Three assertions, because a plausible half-fix refreshes text, font and width while carrying the old height,
anchor and leader:

1. **Width.** A same-font title edit in an `ABOVE` slot: the emitted width equals an independent
   `screenBounds` measurement of the new text.
2. **Height and anchor.** A changed title that moves the label to a rung whose measured height differs (for
   example a dense rung, or a truncated rung with a different string). Assert the emitted height equals the
   newly measured height and the anchor equals the value derived from it. A same-font `ABOVE` edit cannot show
   this, because its height does not change and its anchor does not depend on width.
3. **Leader.** A diagonal-slot label carrying a non-empty leader, retitled so its box changes: assert the
   leader's start and end are the newly computed values, not the carried ones.

### T6 — obstacle accounting for a changed box

Three rejection cases, one per obstacle kind: an expanded box that meets a disc, one that meets an earlier
label, and one that leaves the placement area. Each fixture must make the **old** box acceptable and exhaust
every eligible rung at the retained slot, so that declining is the only possible outcome. For each, assert that
retention declined, that the rejected box was **not** added to the obstacles (a later label may still occupy
that space), and that after an accepted larger box a later ordinary label does not overlap it.

### T7 — a font change is not carried over

The same text placed with two different `LabelFonts`: the second pass must measure with the current face.
Assert the emitted `font()` equals the current theme face and the width matches the current face's measurement.
The rung may change, because the face is re-derived when the font differs.

### T8 — a reused canvas draws the current title

Component-level, using `paintCanvas(canvas)`; the higher-level `paint(...)` helper constructs a fresh canvas per
call and would not exercise retention. Canvas A receives generation 1, paints, then receives generation 2 and
paints. Two assertions:

- On a fixture where the retained and fresh choices provably agree, canvas A's image is pixel-identical to a
  freshly constructed canvas that receives generation 2 only.
- On a fixture where they do not agree (a shortened title that upgrades its rung), canvas A's generation-2
  image must differ from a control that paints the generation-2 state with the generation-1 label list. That
  control is exactly what a stale implementation draws, so the assertion fails before the fix and passes after
  it.

### T9 — the existing suite is unchanged

The whole `freeplane_plugin_graph` test suite must pass, and no existing test may be edited to accommodate the
fix. The pinned absolute-coordinate assertions in `ScreenLabelPlacementShould` cover unchanged-content placement,
including `retainsASlotForAOnePixelMove`, `invalidatesAStaleSlotAndReLadders` and
`resetsTheBaseSlotFlagWhenRetainingAnO4Label`.

## 10. Invariants

1. `ScreenLabelPlacement.retained` never emits a `PlacedLabel` whose `text()`, `font()` or `truncated()` differs
   from what the current projection, the current theme and the accepted rung produce.
2. When the remembered rung still draws the current title in the current font, the emitted label has the same
   rung and the same measured box as today. This holds for every history.
3. `LADDER` remains the only rung table. No second rung→font or rung→truncating mapping is introduced, and a
   retained slot never takes a rung from the other slot family.
4. Forced labels never take a dense or truncated rung, and they remain uncapped. `forcedAtBaseSlot` is false on
   a retained label and true only from `baseSlot`.
5. A declined retention adds nothing to the obstacle list.
6. No public or package API changes. Only the body of `retained` and, if needed, one private helper changes.
7. Enclosure placement, the cache key, the projection, the layout and the painter are untouched.

## 11. Risks

1. **Visible style change after an edit.** A retitled label may switch to a denser or truncated face while
   staying in its slot. That is intended: the alternative is a drawing that contradicts the data. T4 pins it.
2. **Style flicker while settling.** Re-deriving on every pass would let a moved box change an unchanged label's
   face. Step 2's stability check prevents it, P3 states it, and T3's replayed-history case pins it.
3. **Visible movement after an edit.** When no eligible rung fits the retained slot, the label moves. T4's third
   case pins it.
4. **Obstacle-accounting regressions.** Changing what is added to the obstacle list can move neighbouring
   labels. T3 constrains unchanged content, T6 constrains changed content, and T9 constrains every pinned
   fixture.
5. **Forced-policy drift.** A future change could let forced labels take a dense rung or apply the cap to them,
   breaking `invalidatesAStaleSlotAndReLadders` or `resetsTheBaseSlotFlagWhenRetainingAnO4Label`. Invariant 4 and
   T4b name both rules.
6. **A half-fix that refreshes text but keeps geometry.** T5's height, anchor and leader assertions exist
   specifically because text and width assertions cannot catch it.
7. **Over-claiming the fresh-canvas property.** A retained placement is not always a fresh placement. T8 asserts
   equality only where the two provably agree, and 6.7 states the limit.
8. **Scope creep into enclosure labels.** Enclosure labels have no retention path; touching them would widen the
   change without evidence. Section 5 excludes them.

## 12. Review findings and their resolution

### Round 1 (`reports/design-review-1.md`)

| # | Severity | Finding | Resolution |
| --- | --- | --- | --- |
| B1 | blocking | The width check contradicted the existing forced over-cap base-slot test, and "step 6 is the only survival test" was wrong | 6.6 records the uncapped forced policy; 6.2 names every rejecting step; invariant 4 and T4b pin it |
| B2 | blocking | 6.1 said only the slot survives while 6.2 carried the rung, which also broke the universal fresh-canvas claim | 6.1 restated; 6.7 and T8 restrict the fresh-canvas claim |
| B3 | blocking | Tests could not catch copied dimensions or obstacle-accounting errors | T5 asserts measurements; T6 asserts the three rejection cases, no phantom obstacle, and later-label respect |
| N1 | non-blocking | Identical-input replay does not prove retention is enabled | T3b adds a retention witness with a fresh-placement control |
| N2 | non-blocking | The ordering paragraph omitted retention during the forced pass | 6.4 describes the three passes in their real order |
| N3 | non-blocking | The empty-text guarantee was attributed to the wrong class | Section 8 attributes it to `SafeNodeLabel` and separates `PlacedLabel`'s geometry validation |

### Round 2 (`reports/design-review-2.md`)

| # | Severity | Finding | Resolution |
| --- | --- | --- | --- |
| R2-1 | blocking | Re-derivation offered dense and truncated rungs to forced labels, contradicting the forced ladder and `invalidatesAStaleSlotAndReLadders` | 6.2 step 1 restricts forced labels to `FULL_NEAR` and `FULL_DISPLACED`; 6.6 and invariant 4 state it; T4b pins it |
| R2-2 | blocking | P1 was false for a rung that an earlier retention produced: an unchanged title could change face and height when geometry shifted | Step 2 keeps the remembered rung while the drawn text and font are unchanged, so P1 holds for every history; P3 and risk 2 state the no-flicker rule; T3 replays that history |
| R2-3 | blocking | T5's `ABOVE` fixture could not distinguish recomputed height, anchor and leader from carried values | T5 now asserts width separately and requires a rung transition with a changed measured height plus a diagonal-slot leader case |
| R2-4 | non-blocking | The procedure dereferenced the previous label without the existing null guard | 6.2 step 0 states both guards; section 8 repeats why both stay |
