# 017 — The two-pinned-maps conflict golden was overtaken by a named lane

Task Identifier: `017-pin-conflict-golden-bisected`
Ticket ID: none (no external ticket exists for this task)
Branch: `pm/graph-pin-conflict-bisect-017` (off `e897e15ddf`)
Status: **diagnosis only — no golden, assertion or layout code changed**

## 1. Question

Task 016's first complete campaign run reported
`Two-pinned-maps conflict count changed: expected 1 but measured 11`. The golden
`TWO_PINNED_MAPS_EXPECTED_CONFLICTS = 1` came from `01d3900b28` and is asserted **only** by the
diagnostic, which had never completed a run before 016. Two readings were recorded: a stale golden, or a
pinned-map conflict regression. This task decides which.

## 2. Method

A probe (`/tmp/tpp`, no repository change) reproduces the diagnostic's measurement point exactly: the
first submit frame of a sample, from the generation-1 projection diffed against the generation-0
baseline, for `GeneratedWorkspace.forScenario("two-pinned-maps")`. It prints only the rigid conflict
count, so it runs in seconds instead of a fifty-minute campaign.

| Check | Result |
|---|---|
| Determinism at `e897e15ddf` (HEAD) | **10/10 runs = 11** — not a flake |
| Probe at HEAD | `CONFLICTS=11`, matching the campaign's "measured 11" |
| Probe at `01d3900b28`, the commit that set the golden | **`CONFLICTS=1`** — the golden was correct when written |
| Ancestry | `01d3900b28` is an ancestor of HEAD, 399 commits back, so `git bisect` is applicable |

`git bisect run` with the probe as the predicate (exit 0 when the count is 1):

```
STEP a55bb91ad9: 1     STEP 75b66ee306: 20    STEP b7b7c9a04f: 20
STEP 4b153d0e91: 20    STEP 7f4e4d0e9b: 1     STEP 16341929e0: 1
STEP bdcc7fccea: 20    STEP 7921731ec4: 1     STEP fb1ad75775: 20
```

Every `1` is an ancestor of the verdict and the last one (`7921731ec4`) is its parent, so the predicate is
monotonic along the bisected chain and the verdict is sound:

**`fb1ad75775` — `feat(graph): seed and separate anchors from packed footprints
[2026-10-13-graph-cluster-initial-proximity]` — took the count from 1 to 20.** Later commits brought it to
11.

## 3. What the eleven actually are

`BoundaryConflict` is distinguished by hull pair, kind, reason and blocking pins. Printing all four for
every entry:

| Property | Value across the 11 |
|---|---|
| `reason` | `IMMOVABLE_SIDES` for all — the two pins are the reason separation fails |
| `kind` | 2 × `SIBLING_CROSSING`, 9 × `SIBLING_CONTAINMENT` |
| first hull | map 1 `structuralPath=[0]`, identical in all 11 |
| second hull | **11 different paths** on map 2: `[0] [1] [3] [4] [7] [8] [11] [15] [19] …` |
| blocking pins | exactly `m00-n0001` and `m01-n0001`, each attributed to its own map, in all 11 |

So these are **eleven distinct, correctly formed conflicts** over eleven different enclosure pairs, not
duplicates and not mis-attributed. An earlier reading in this session that they were duplicate reports was
wrong: `distinct()` is identity-based because `BoundaryConflict` defines no `equals`, so it could not have
shown duplicates either way; printing the hull pairs is what settled it.

The diagnostic's other assertions for this scenario — cross-map exactly-two-pins, pins from their own map,
both pin identities retained — **hold for all eleven**. Only the count moved.

## 4. Conclusion

**The golden was overtaken by a deliberate layout change, not by a defect in conflict reporting.** The
cluster-initial-proximity lane changed how enclosures are seeded and separated, so map 1's first enclosure
now fails to separate from eleven of map 2's enclosures instead of one, and the two immovable pins are what
make each failure unresolvable. The count went 1 → 20 → 11 as later lanes tuned that behaviour.

Whether eleven is *acceptable* for this scenario is a layout-quality judgement, not a test question: this
scenario is deliberately adversarial (two immovable pins), so conflicts are the expected signal and their
number is a measure of how much separation two fixed anchors prevent.

## 5. Recommendation (needs the layout owner)

The exact-count golden is the wrong shape for a number that tracks layout tuning — it has already moved
three times and is the only thing standing between the campaign and its first green run. Two options:

- **Re-derive** `TWO_PINNED_MAPS_EXPECTED_CONFLICTS` to 11, recording in the constant that the count is a
  calibration, that `01d3900b28` measured 1, that `fb1ad75775` moved it, and that it tracks layout tuning
  rather than a fixed contract.
- **Replace the exact count with the behaviour that has product meaning**, which the diagnostic already
  asserts per entry: every conflict is `IMMOVABLE_SIDES`, involves exactly the two pins, attributes them to
  their own maps and retains both identities — plus a loose upper bound so an unbounded explosion still
  fails. This is the stronger check, because it survives legitimate tuning and still catches a real
  mis-attribution.

This task does neither. Editing the expectation to make the run green is exactly the move that would
destroy the only evidence that anything changed, and the choice between "re-derive" and "assert the shape"
belongs to whoever owns the layout's separation contract.

## 6. State

No repository file was modified except this record: no golden, no assertion, no layout or production code.
The bisect worktree used for the run was removed; the probe lives in `/tmp/tpp` and is reproducible from
§2's description.
