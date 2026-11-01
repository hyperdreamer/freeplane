# Graph workspace — a retitled node keeps drawing its previous title

Task identifier: `2026-11-01-graph-workspace-live-title-refresh`

Status: **approved by the user on 2026-11-01.** The user reported "After editing a node's content in the
mindmap, the graph-workspace does not immediately reflect the updated node title", confirmed with a
screenshot showing the disc labelled `Theorem` while the hover bar read
`Theorem: closure in subspace - Point-set_Topology`, chose fix approach **1** (keep the slot, refresh the
content) from three presented options, and chose to re-derive the label face on a title change rather than keep
the remembered face. This document is the design authority for the technical specification that follows it.

Baseline: `plugin/graph-workspace` at `8267175e52f49216ae6222bc002e48020690d67c`. All investigation ran in the
PM integration worktree; the user's delivery checkout was read only and never built.

Revision history:

- r1 (`9a0cebe64a`) — first complete design. Review round 1: **CHANGES_REQUIRED**, 3 blocking, 3 non-blocking.
- r2 (`032337648c`) — applied round 1, adopted unconditional face re-derivation. Review round 2:
  **CHANGES_REQUIRED**, 3 blocking, 1 non-blocking. Unconditional re-derivation was shown to change the face of
  an unchanged title when geometry shifts, and to offer forced labels dense or truncated faces.
- r3 (`4d27b3d9e2`) — kept the remembered face while the *drawn string* was unchanged. Review round 3:
  **CHANGES_REQUIRED**, 3 blocking, 2 non-blocking. The drawn string turned out to be the wrong signal: it
  conflated an obstructed label with a retitled one, it changed an uncapped remembered box on the forced-to-
  ordinary transition, and it hid a real title edit whose truncation rendered identically.
- r4 (this revision) — the remembered label now carries the projection text it was derived from, so "did the
  title change?" is answered exactly. A changed title is re-derived; an unchanged title is either kept or falls
  back to the ordinary ladder exactly as today. Section 12 records every finding from all three rounds.

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
2. Retention keeps doing its real job: an unchanged title keeps exactly the placement decision it makes today.
3. A changed title takes the best face the label is eligible for at the slot it already occupies, so a title
   that shrank is no longer drawn small or truncated.
4. A label whose title did not change never changes its face while the layout moves, and never changes its
   fallback behaviour either.
5. Every way a label can change is covered: a different title, a longer or shorter title that changes the
   truncation, and a different font or theme.
6. The rule is stated once and tested at the layer that owns it, plus at the component that renders it.

## 5. Non-goals

- No change to the map event plumbing, the debounce, the projection, the layout, or the cache key.
- No change to enclosure labels. `ScreenLabelPlacement.place` recomputes them from the current projection on
  every pass; they are never retained.
- No visual redesign: no new fonts, no new slot preference order, no new truncation rules, no new leader rules.
- No change to hover, tooltip, search or accessibility text; they already read live data.
- No change to the forced-rung policy: a forced label uses only the full-font rungs, and the forced base slot
  ignores the width cap.
- No claim that a reused placement always equals a fresh one; see 6.8 and T8.

## 6. Design

### 6.1 The contract

> Retention preserves where the label is placed, never what it says. The slot is carried forward. The text,
> the face, every measurement and the leader are recomputed from the current projection, the current theme and
> the current obstacle set. Whether the remembered face survives is decided by comparing the **title** the
> label was built from with the current title, never by comparing the pixels that happen to come out.

The decision has exactly three outcomes:

- **keep** — the title and font are unchanged, the remembered face is eligible, and its box is still
  acceptable. The result is byte-identical to today's.
- **re-derive** — the title changed, or the font changed, or the remembered face is no longer eligible. The
  best eligible face for the retained slot is chosen, and the label keeps its slot whenever one fits.
- **decline** — the title, font and face are all unchanged but the box no longer fits. Retention returns no
  placement and the ordinary ladder runs, exactly as today.

Three properties follow, and all three are testable:

- **P1 — unchanged title behaves exactly as today.** When the title, the font and the remembered face are all
  unchanged and the remembered box is still acceptable, the emitted label has the same rung, text, width,
  height, anchor and leader as today's retention. When that box is no longer acceptable, the outcome is today's
  outcome: the label is re-placed by the ordinary ladder. Changed geometry may therefore move a label, as it
  does today; what is promised is that no new style change appears.
- **P2 — a changed title never keeps a stale face.** Any change to the title, including one whose truncation
  renders the same string, re-derives the face for the retained slot.
- **P3 — no face oscillation.** Because the keep decision depends only on the title, the font and the
  remembered face, none of which move during settling, a settling layout can never flip a label between faces.

### 6.2 Retention, restated

Given a previous `PlacedLabel p` for a node whose current display text is `fullText`:

**Step 0 — guards.**
1. If no previous label exists, decline. `placeNode` calls retention on the first placement too.
2. If `p.mode()` is `HOVER_ONLY`, decline. Both guards exist today and stay.

**Step 1 — eligibility.** `forced = isForced(context, key)`. A face is eligible for this label only when all
hold:
1. its `far` flag matches the class of the retained slot (`NEAR_SLOTS` or `FAR_SLOTS`);
2. when `forced`, the face is `FULL_NEAR` or `FULL_DISPLACED`. This mirrors the forced ladder, which offers
   forced labels nothing else.

Eligibility governs both the keep path and the re-derivation path.

**Step 2 — keep.** The remembered face is kept when `p.sourceText().equals(fullText)` and
`fontFor(p.rung()).equals(p.font())` and `p.rung()` is eligible. Resolve the face's assets from the `LADDER`
table (font selector and truncating policy), compute `text` from the current title and that policy, measure it,
and compute the anchor from the current centre, the current radius and that box. If the box lies inside the
placement area and intersects no obstacle already in the context, emit it (step 4). There is **no** width-cap
test on this path: the kept box is exactly the box the label has today, and today it is accepted.

**Step 3 — re-derive the face.** Otherwise, if the title changed, or the font changed, or the remembered face
is not eligible, walk the `LADDER` table in its declared order, considering **only** `slot` and only faces
eligible under step 1:
1. Resolve the face's assets from the table. The table stays the single source of truth for rung → font and
   rung → truncating.
2. Skip a non-truncating face for a non-forced label when `textWidth(fullText, font) > slotMaxWidth(slot)`.
   Forced labels skip this width test, because the forced base-slot policy is deliberately uncapped.
3. Compute `text` and `truncated = !text.equals(fullText)` for the face's truncating policy, measure it, and
   compute the anchor and box as in step 2.
4. Accept the first face whose box is inside the placement area and intersects no existing obstacle.

**Step 4 — emit, or decline.** On acceptance from step 2 or step 3, add the box to the obstacles and emit a
`PlacedLabel` carrying `p.endpoint()`, `text`, `font`, `p.mode()`, the accepted `rung`, the anchor, the measured
width and height, `truncated`, `fullText` as the new source text, `forced`, `p.emphaticAtAnchor()`,
`forcedAtBaseSlot = false`, `fullTextSlotWasFree = false`, `slot`, and a leader recomputed with
`nodeLeader(slot, centre, radius, box)`.

If step 2 does not keep and step 3 accepts nothing, return no placement. The caller then continues with the
ordinary ladder, exactly as today, and `baseSlot` still serves a forced label that fits no face.

Every rejection happens before anything is added to the obstacle list, so a declined retention leaves no trace.

### 6.3 What "the title changed" means

`PlacedLabel` gains one package-private field, `sourceText`: the projection display text the label was derived
from. Every construction site sets it — the ladder, the forced base slot, hover-only, enclosure labels and
retention itself.

This is required rather than convenient. Without it, the only available signal is the drawn string, and a
truncated face can render the same prefix plus ellipsis for two different titles. Review round 3 showed that
inference changing an unchanged label and, separately, hiding a real title change whose truncation rendered
identically. Carrying the source text answers the question exactly, and it removes the need for any
"rendering-equivalent" exception.

The field is internal to the `canvas` package, with a package-private constructor and a package-private
accessor. No public API changes.

### 6.4 Why the old anchor cannot simply be kept

The anchor is derived from the box size, and the box size changes when the text or the face changes. Keeping
the old anchor while drawing a differently sized text would centre the new text on the old anchor and silently
widen or narrow the label's footprint without accounting for it in the obstacles, so a neighbouring label could
end up drawn over the retitled one. Recomputing the anchor from the new box keeps obstacle accounting honest.

### 6.5 Passes and ordering

`place(...)` runs three passes, and retention participates in the node passes only:

1. **Forced nodes** — `placeForced`, radius descending; each node attempts retention, then the forced ladder,
   then `baseSlot`. Retention here runs **before** enclosure placement.
2. **Enclosures** — emphatic tier first, then subtle. Not retained; always recomputed.
3. **Ordinary nodes** — `placeNodes`, radius descending; each node attempts retention, then the ladder, then
   hover-only.

Obstacles accumulate across all three passes in that order. Any test that asserts what a later label may
overlap must respect this ordering.

### 6.6 Interaction with the ladder

When retention declines, `placeNode` continues exactly as it does today: `ladder(...)`, then `baseSlot(...)`
when forced, then `hoverOnly(...)`. The ladder keeps its own slot order and caps, so a label that cannot fit its
old slot moves through the same rungs a fresh placement would use.

### 6.7 The forced-label policy

Two rules constrain forced labels, and both are invariants rather than accidents:

1. **Eligible faces.** The forced ladder offers only `FULL_NEAR` and `FULL_DISPLACED`. Step 1 applies the same
   filter to the keep path and to re-derivation, so retention can never leave a forced label in a dense or
   truncated face, including when a visible label is promoted to forced without its title changing.
2. **Uncapped base slot.** `ScreenLabelPlacementShould.resetsTheBaseSlotFlagWhenRetainingAnO4Label` forces the
   ~274 px `LONG_NAMES[0]` label into an `ABOVE` slot whose cap is 200 px. The baseline takes `baseSlot`, which
   ignores the cap and sets `forcedAtBaseSlot = true`; retention then clears that flag while keeping mode, rung,
   anchor, width and height. Step 2 keeps that behaviour for an unchanged title, and step 3.2 keeps the uncapped
   policy whenever a forced label is re-derived.

   The same exemption protects a label that stops being forced. Its remembered box may be wider than the cap
   because `baseSlot` allowed it; the keep path accepts it without a cap test, so demotion alone never changes
   the drawing. Only a title change re-derives the face, and only then do the ordinary caps apply.

### 6.8 What may visibly change, and what may not

| Situation | Before | After |
| --- | --- | --- |
| Title unchanged, box still acceptable | retained face and box | identical face, box, leader and numbers |
| Title unchanged, a neighbour moved so the box no longer fits | retention declines, ladder re-places | identical to today: the same decline and the same ladder outcome |
| Title unchanged, label stops being forced | box kept, flag updated | identical box and face; still kept |
| Title changed, same face still draws it | stale text drawn | new text, same face, re-measured box in the same slot |
| Title changed, needs a denser or truncated face | stale text drawn | new text in a denser or truncated face, still in the same slot |
| Title changed, and truncation hid the change | stale text drawn | re-derived; a shorter title that now fits is drawn in full |
| Title changed, nothing eligible fits the slot | stale text drawn | ordinary ladder, possibly another slot |
| Theme or font changed | retained label keeps the old face and size | face re-derived and measured with the current theme |
| Label was hover-only | re-placed from the projection | unchanged |
| Visible dense label becomes forced | dense face retained | full-font face, via the forced ladder |

A retained placement does **not** always equal a fresh placement, and this design does not claim otherwise. A
fresh pass chooses the first face-and-slot pair that is free in ladder order and may prefer a roomier slot over
the retained one.

## 7. Recorded decisions and mockup

Approaches presented and chosen:

1. keep the slot, refresh the content — chosen;
2. guard on text equality only — rejected: it leaves the stale-font and stale-measurement path open;
3. clear remembered placements when any label differs — rejected: it discards placement stability for the whole
   picture after every edit and still misses font changes.

The face question was put to the user after round 1 and answered **re-derive**, so a title that shrinks does not
keep a smaller face forever. Rounds 2 and 3 established that the trigger must be the *title*, not the drawn
string: the drawn string cannot distinguish an obstructed label from a retitled one, and it cannot see a title
change that truncation hides. Carrying the source text is what makes the user's choice exact, so this revision
implements the decision rather than documenting a limitation of it.

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
- `truncated` is computed as `!text.equals(fullText)` on both accepted paths, so a title that becomes exactly
  its previous rendered string clears the flag instead of copying it.
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
the same cache instance. Assert the returned list carries the new text.

### T3 — unchanged title keeps today's decision

Immediate replay of identical input returns the same face, anchor, width and height for every node. Then a
replay at a final geometry that follows an earlier retention at a different geometry must keep the remembered
face when its box is still acceptable — the round-2 counterexample.

### T3b — retention is still switched on

A small movement for which the previous slot is still valid but a fresh placement would choose differently.
Assert the retained result keeps the previous slot, and assert with a `previous == null` control that the fresh
placement chooses the other slot.

### T3c — an obstructed, unchanged label falls back exactly as today

The round-3 counterexample: two ordinary nodes, one moved onto the other's remembered box, title and font
unchanged. On the **first** pass after the move, assert that the retained pass and a `previous == null` pass
produce the same face and slot for the affected node — a full-font `BELOW` in the traced fixture, never a
dense `ABOVE`. Also assert that repeating the neighbouring positions (`-35, -20, +35, +20`) never toggles the
face.

### T4 — the face follows a changed title

- A long title replaced by a short one upgrades the face in place.
- A short title replaced by a long one that still fits a denser face at that slot degrades in place.
- A title so long that no eligible face fits the slot declines and is re-placed by the ordinary ladder.
- A retained near slot never takes a displaced face, and a retained far slot never takes a near face.

### T4b — forced labels keep the forced-rung policy

- A forced label whose changed title no longer fits any full-font face must not take a dense or truncated face;
  assert it was placed by the forced ladder or the base slot, using `invalidatesAStaleSlotAndReLadders` as the
  model.
- A visible dense or truncated label is promoted to forced with its title unchanged: assert that no dense or
  truncated face survives, and that the keep path did not bypass eligibility.
- An unchanged forced over-cap label keeps its face and numbers.

### T4c — a title change that truncation hides is still a change

The round-3 counterexample: a truncated long title is retitled to a shorter title that truncates to the same
rendered string but fits a dense face in full. Assert the label is re-derived to the dense face with the whole
title visible, and that the truncated flag is false.

### T4d — demotion never changes the drawing

A forced over-cap label becomes ordinary with its title unchanged: assert the same face, box and anchor as the
forced pass, on that pass and on the next, and that no cap-driven truncation appears.

### T5 — measurement follows the content

Three assertions, because a plausible half-fix refreshes text, font and width while carrying the old height,
anchor and leader:

1. **Width.** A same-font title edit in an `ABOVE` slot: the emitted width equals an independent
   `screenBounds` measurement of the new text.
2. **Height and anchor.** A changed title that moves the label to a face whose measured height differs (a dense
   face, or a truncated face with a different string). Assert the emitted height equals the newly measured
   height and the anchor equals the value derived from it.
3. **Leader.** A diagonal-slot label carrying a non-empty leader, retitled so its box changes: assert the
   leader's start and end are the newly computed values.

### T6 — obstacle accounting for a changed box

Three rejection cases, one per obstacle kind: an expanded box that meets a disc, one that meets an earlier
label, and one that leaves the placement area. Each fixture must make the **old** box acceptable and exhaust
every eligible face at the retained slot. For each, assert that retention declined, that the rejected box was
**not** added to the obstacles (a later label may still occupy that space), and that after an accepted larger
box a later ordinary label does not overlap it.

### T7 — a font change is not carried over

The same text placed with two different `LabelFonts`: the second pass measures with the current face. Assert
the emitted `font()` equals the current theme face and the width matches the current face's measurement. The
face may change, because it is re-derived when the font differs.

### T8 — a reused canvas draws the current title

Component-level, using `paintCanvas(canvas)`; the higher-level `paint(...)` helper constructs a fresh canvas per
call and would not exercise retention. Canvas A receives generation 1, paints, then receives generation 2 and
paints. Two assertions:

- On a fixture where the retained and fresh choices provably agree, canvas A's image is pixel-identical to a
  freshly constructed canvas that receives generation 2 only.
- On a fixture where they do not agree (a shortened title that upgrades its face), canvas A's generation-2
  image must differ from a control that paints the generation-2 state with the generation-1 label list, which
  is exactly what a stale implementation draws.

### T9 — the existing suite is unchanged, except two constructor call sites

The whole `freeplane_plugin_graph` test suite must pass, and no existing assertion may be changed. The two
`PlacedLabel` construction sites in `GraphCanvasPaintShould` gain the new `sourceText` argument, passing the
text they already use; their assertions stay untouched. The pinned absolute-coordinate assertions in
`ScreenLabelPlacementShould` — including `retainsASlotForAOnePixelMove`, `invalidatesAStaleSlotAndReLadders` and
`resetsTheBaseSlotFlagWhenRetainingAnO4Label` — must pass unmodified.

## 10. Invariants

1. `ScreenLabelPlacement.retained` never emits a `PlacedLabel` whose `text()`, `font()`, `truncated()` or
   `sourceText()` disagrees with the current projection, the current theme and the accepted face.
2. An unchanged title with an acceptable remembered box keeps the remembered face and measurements, for every
   history, including a face produced by an earlier retention or by `baseSlot`.
3. An unchanged title whose remembered box is no longer acceptable produces the same placement as a fresh
   pass, because retention declines.
4. `LADDER` remains the only face table. No second rung→font or rung→truncating mapping is introduced, and a
   retained slot never takes a face from the other slot family.
5. Forced labels never take a dense or truncated face, on the keep path or on re-derivation, and they remain
   uncapped. `forcedAtBaseSlot` is false on a retained label and true only from `baseSlot`.
6. A declined retention adds nothing to the obstacle list.
7. `PlacedLabel` gains exactly one package-private field with a package-private accessor. No public or other
   API changes; enclosure placement, the cache key, the projection, the layout and the painter are untouched.

## 11. Risks

1. **Visible style change after a title edit.** A retitled label may switch to a denser or truncated face while
   staying in its slot. That is intended. T4 and T4c pin it.
2. **Style flicker while settling.** Deciding the face from the drawn string would let a moved box change an
   unchanged label's face. Step 2 decides from the source text, and T3c pins the transition.
3. **Visible movement after a title edit.** When no eligible face fits the retained slot, the label moves.
   T4's third case pins it.
4. **Obstacle-accounting regressions.** Changing what is added to the obstacle list can move neighbouring
   labels. T3 and T3c constrain unchanged content, T6 constrains changed content, and T9 constrains every pinned
   fixture.
5. **Forced-policy drift.** A future change could let forced labels take a dense face, or apply the cap on the
   keep path. Invariant 5, T4b and T4d name both rules.
6. **A half-fix that refreshes text but keeps geometry.** T5's height, anchor and leader assertions exist
   because text and width assertions cannot catch it.
7. **A half-fix that forgets to set `sourceText`.** A construction site that leaves it at the rendered text
   reintroduces the truncation collision silently. Invariant 1 and T4c pin it.
8. **Over-claiming the fresh-canvas property.** A retained placement is not always a fresh placement. T8
   asserts equality only where the two provably agree.
9. **Scope creep into enclosure labels.** Enclosure labels have no retention path; section 5 excludes them.

## 12. Review findings and their resolution

### Round 1 (`reports/design-review-1.md`)

| # | Severity | Finding | Resolution |
| --- | --- | --- | --- |
| B1 | blocking | The width check contradicted the existing forced over-cap base-slot test, and "step 6 is the only survival test" was wrong | 6.7 records the uncapped forced policy; 6.2 names every rejecting step; invariant 5 and T4b pin it |
| B2 | blocking | 6.1 said only the slot survives while 6.2 carried the rung, which also broke the universal fresh-canvas claim | 6.1 restated; 6.8 and T8 restrict the fresh-canvas claim |
| B3 | blocking | Tests could not catch copied dimensions or obstacle-accounting errors | T5 asserts measurements; T6 asserts the three rejection cases, no phantom obstacle, and later-label respect |
| N1 | non-blocking | Identical-input replay does not prove retention is enabled | T3b adds a retention witness with a fresh-placement control |
| N2 | non-blocking | The ordering paragraph omitted retention during the forced pass | 6.5 describes the three passes in their real order |
| N3 | non-blocking | The empty-text guarantee was attributed to the wrong class | Section 8 attributes it to `SafeNodeLabel` and separates `PlacedLabel`'s geometry validation |

### Round 2 (`reports/design-review-2.md`)

| # | Severity | Finding | Resolution |
| --- | --- | --- | --- |
| R2-1 | blocking | Re-derivation offered dense and truncated faces to forced labels | Step 1 restricts forced labels to `FULL_NEAR` and `FULL_DISPLACED`; 6.7 and invariant 5 state it; T4b pins it |
| R2-2 | blocking | P1 was false for a face that an earlier retention produced: an unchanged title could change face when geometry shifted | Step 2 keeps the remembered face while the title and font are unchanged; P3 states the no-flicker rule; T3 replays that history |
| R2-3 | blocking | T5's `ABOVE` fixture could not distinguish recomputed height, anchor and leader | T5 asserts width separately and requires a face transition with a changed height plus a diagonal leader case |
| R2-4 | non-blocking | The procedure dereferenced the previous label without the existing null guard | Step 0 states both guards; section 8 repeats why both stay |

### Round 3 (`reports/design-review-3.md`)

| # | Severity | Finding | Resolution |
| --- | --- | --- | --- |
| R3-1 | blocking | An obstructed but render-identical label entered re-derivation and changed face, producing a repeatable oscillation with unchanged content | Step 2 keeps only an acceptable box; an obstructed unchanged label declines to the ordinary ladder as today; T3c pins the first pass after the context change |
| R3-2 | blocking | The keep path's cap test truncated an unchanged box after forced → ordinary | No cap test on the keep path; caps apply only when re-deriving; 6.7 and T4d pin the demotion history |
| R3-3 | blocking | Rendering equality hid a real title edit whose truncation rendered identically, so the changed-title promise was false | `PlacedLabel` carries `sourceText`; the trigger is the title, never the pixels; 6.3, invariant 1 and T4c |
| R3-4 | non-blocking | T4b did not prove eligibility on the keep path | Eligibility governs both paths; T4b adds a promotion test asserting no dense or truncated face survives |
| R3-5 | non-blocking | The keep path did not define `truncated` | `truncated = !text.equals(fullText)` on both paths; section 8 and T4c pin it |
