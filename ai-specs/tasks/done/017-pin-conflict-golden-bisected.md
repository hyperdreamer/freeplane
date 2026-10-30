# 017 — The two-pinned-maps conflict golden was overtaken by a named lane

Task Identifier: `017-pin-conflict-golden-bisected`
Ticket ID: none (no external ticket exists for this task)
Branch: `pm/graph-pin-conflict-bisect-017` (off `e897e15ddf`)
Status: **delivered** — bisected in §2–§4, shape assertions implemented in §5a, verified in §5b

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

The owner chose the second option, so §5a implements it. Editing the expectation to 11 was rejected for
the reason given: it would destroy the only evidence that anything changed.

## 6. State

No repository file was modified except this record: no golden, no assertion, no layout or production code.
The bisect worktree used for the run was removed; the probe lives in `/tmp/tpp` and is reproducible from
§2's description.

## 5a. What was implemented

`validatePinConflicts` no longer compares the count. The per-conflict checks moved into
`validateTwoPinnedMapConflicts(List<BoundaryConflict>)`, package-private so it can be tested, and assert:

| Check | Message on failure |
|---|---|
| `conflicts.size() <= TWO_PINNED_MAPS_MAX_CONFLICTS` (64, >3x the highest count ever measured) | above the 64 tripwire |
| every `reason() == IMMOVABLE_SIDES` | conflict reason changed |
| every `CanonicalKeys.hullPair(...)` is distinct | reported the same enclosure-pair conflict twice |
| cross-map: exactly two pins, `m00-n0001` and `m01-n0001` | did not retain both pin identities |
| same-map: every pin belongs to that map | listed a pin from another map |

The distinctness check is new and is the one that would catch a duplicate-reporting regression, which the
count alone could not distinguish from a genuine increase. `TWO_PINNED_MAPS_EXPECTED_CONFLICTS` is gone;
the history that produced 1 → 20 → 11 is recorded in the tripwire's javadoc so the next reader does not
have to bisect it again.

## 5b. Verification

| Gate | Command | Result |
|---|---|---|
| New test, pristine | `gradle :freeplane_plugin_graph:test --tests '*PerformanceTripwiresShould'` | pass |
| Mutation: distinctness check removed | disposable copy of the diagnostic, class shadowed ahead of the build classes | test **fails**, "Expecting code to raise a throwable" |
| Mutation: reason check removed | same | test **fails** |
| Mutation: tripwire 64 → 65 | same | test **fails** |
| Source integrity | `sha256sum` of the worktree file before and after the mutation battery | unchanged — the harness mutates copies, not the tree |
| Full suite via `run-suite-on-xvfb.sh` | — | **exit 0 — 1461 tests, 0 failures, 4 skipped, 14m12s** |

## 6a. The campaign now runs end to end, and reports seven failing performance rows

With the conflict check satisfied, `gradle :freeplane_plugin_graph:graphPerformanceDiagnostic` completes the
whole sweep for the first time: 52 m 58 s, all six scenarios, and
`freeplane_plugin_graph/build/graph-performance/performance-ledger.csv` written. It then fails its own
aggregate gate:

```
DiagnosticFailure: One or more performance ledger rows failed
```

7 of 102 rows breach their **normal** thresholds, not just strict:

| Row | p95 measured | p95 threshold | ratio |
|---|---|---|---|
| `skewed-reference:accepted-batch-first-frame` | 1799 ms | 750 ms | 2.40 |
| `reference-2000-5000:accepted-batch-first-frame` | 1405 ms | 750 ms | 1.87 |
| `skewed-reference:full-worker` | 892 ms | 500 ms | 1.78 |
| `reference-2000-5000:force` | 397 ms | 250 ms | 1.59 |
| `three-map-clustered:full-worker` | 782 ms | 500 ms | 1.56 |
| `three-map-clustered:accepted-batch-first-frame` | 1107 ms | 750 ms | 1.48 |
| `reference-2000-5000:full-worker` | 722 ms | 500 ms | 1.44 |

All seven are wall-clock worker/first-frame/force rows on the three largest scenarios; every other row
passes. **This is not classified here, and no threshold was touched.** Two candidate explanations, and the
evidence does not yet separate them: the thresholds may be calibrated for a quieter host than this one
(task 010 §1.3 measured the raw-reference fixture at 5.0–6.0 s on the *base* commit here, so this host is
slow for this workload regardless of recent work), or the layout lanes since `01d3900b28` cost real time.
Separating them needs a campaign run on a quiet machine and a comparison against an older commit — each run
is about fifty minutes, so that is its own task.

The ledger now exists, which is what makes that comparison possible at all: before this task the tool could
not produce its own artefact.

## 6b. A second finding: 015's re-assert changed a frame-operation contract

Running the module suite surfaced `GraphWorkspaceWindowModelShould.rawFrameOperationsFollowSpecifiedOrder`
failing. Cause: 015's guarded re-assert of the saved bounds adds one `setBounds`/`validate` pair when the
window manager has not honoured the rectangle by the time the peer is mapped, and that test asserted the
exact operation sequence. The specified sequence now has a conditional tail, so the test was updated to
assert the mandatory prefix exactly and permit at most one trailing re-assert of the *same* rectangle, with
a new test forcing that branch deterministically. Full suite green afterwards.

The same session also produced a scare worth recording: running `GraphWorkspaceWindowModelShould` through
plain `gradle` instead of `scripts/run-suite-on-xvfb.sh` made
`panDuringPresentationIsPreservedThroughExit` fail 3/3, and a single passing run with the guard compiled out
looked like proof that 015's fix was the cause. It was not: on the Xvfb script the class is 118/118 green
with the guard in place, twice. That is the live-desktop failure mode `running-the-test-suite.md` documents,
and it is a reminder that a single-green control run is not evidence when the budget it races is 20 s.

Residual, unchanged: `f11RestoresBoundsAndExtendedStateFromNormalWindow` still fails occasionally (1 of 3
Xvfb class runs here). 015 §7 warned that the guard reduces but cannot prove away a window-manager race, and
this is that race, not a new one.
