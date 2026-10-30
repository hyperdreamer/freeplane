# 018 — The graph worker's performance thresholds are breached by a per-frame edge stage, twice

Task Identifier: `018-perf-thresholds-classified`
Ticket ID: none (no external ticket exists for this task)
Branch: `pm/graph-perf-thresholds-018` (off the then-current tip `088ede2c28`)
Status: **measured and classified — no threshold, golden or layout code changed**

## 1. Question

Task 017 §6a reported that the campaign, running end to end for the first time, failed 7 of 102 ledger rows on
the machine where it was developed, and deliberately did not classify them: either the thresholds were
calibrated for a faster host, or the recent layout work genuinely costs more. This task classifies them by
running both campaigns **on the same machine**, one at HEAD and one at `01d3900b28`, the commit where the
performance goldens were pinned.

## 2. Method

Two complete campaigns on a 16-core / 15.7 GB host with a mean load average of 2.99-3.00, each in its own
worktree, each writing its own `performance-ledger.csv`:

| Campaign | Commit | Duration | Rows failed |
|---|---|---|---|
| HEAD | `30813b2486` | 50 m 20 s | **6 / 102** |
| Pinned goldens | `01d3900b28` | 46 m 08 s | **1 / 102** |

Both hosts are equivalent for this workload: HEAD needed 3.80 s per reference sample here against 3.92 s on
the machine that first produced the failures, and the campaign totals differ by 5%.

**Trap correction.** The brief for this measurement said `01d3900b28`'s process deadline was 40 minutes and
needed raising to 120. It was actually **10 minutes** (`TimeUnit.MINUTES.toNanos(10L)`); only that constant
was changed, uncommitted and commented. The 40-minute value is `30813b2486`'s. The measurement was
unaffected because the constant is a liveness guard.

**Validation correction.** The "did it actually run" check proposed in the brief — look for the raw-reference
gate line and one `Scenario … took` line per scenario — does not exist at `01d3900b28`: the gate was added by
014 and the progress lines by 016. Trust for the old run rests instead on the ledger having all 102 rows
across 6 scenarios and 950 samples, and on the only failure being the ledger-row gate. The 5 s operation
timeout in that commit did not fire.

## 3. Result

| row | p95 HEAD | p95 `01d3900b28` | ratio | threshold | HEAD | old |
|---|---|---|---|---|---|---|
| `three-map-clustered:accepted-batch-first-frame` | 814.0 ms | 15.1 ms | **53.9x** | 750 ms | fail | pass |
| `three-map-clustered:full-worker` | 609.0 ms | 12.0 ms | **50.6x** | 500 ms | fail | pass |
| `reference-2000-5000:accepted-batch-first-frame` | 1176.0 ms | 159.6 ms | **7.37x** | 750 ms | fail | pass |
| `skewed-reference:accepted-batch-first-frame` | 1017.9 ms | 176.8 ms | **5.76x** | 750 ms | fail | pass |
| `reference-2000-5000:full-worker` | 596.0 ms | 137.5 ms | **4.34x** | 500 ms | fail | pass |
| `skewed-reference:full-worker` | 475.2 ms | 157.0 ms | 3.03x | 500 ms | **pass** | pass |
| `reference-2000-5000:force` | 420.3 ms | 257.1 ms | 1.63x | 250 ms | fail | **fail** |

**Verdict: a real cost increase, not host calibration.** 101 of 102 rows pass at the pinned-goldens commit on
this machine, so the thresholds were achievable here, and the host is not the variable.

The decomposition matters more than the total, because the seven rows are not one phenomenon:

1. **Six rows regressed**, all in the layout worker stages (`full-worker`, `accepted-batch-first-frame`).
2. **One row was already failing** at the pinned commit: `reference-2000-5000:force` measured 257.1 ms
   against a 250 ms threshold. That is a pre-existing calibration miss of 3%, unrelated to any recent work,
   and re-deriving it is a separate and much smaller question.
3. **The cost is localised.** Stages that touch no layout moved by 1.08x (repaint), 1.19x (projection),
   1.21x (diff), 1.09x (placement). Only the layout stages moved 3.5-54x. Nothing else got slower, and some
   things got faster: `correction` is 0.05x and `skewed-reference`'s `placement`/`repaint` are 0.13x/0.27x of
   the old commit's.

## 4. Mechanism

`LayoutWorker` now runs an edge stage inside every published frame:

```java
// LayoutWorker:309
final EdgeConflictResult edge = edgeConflictCorrection.apply(request.projection(),
    boundary.positions(), defaultMetrics(), request.pins(), visible, boundary);
final LayoutPositions corrected = edge.positions();
final Map<String, LayoutPoint> merged = mergeDisplacements(boundary.appliedDisplacements(),
    edge.appliedDisplacements());
```

`EdgeConflictCorrection` was introduced by `cc3b38818c` ("add EdgeConflict value types and the inert correction
scaffold") and composed into the frame path by `3df752845a` ("compose the edge stage and carry edge
diagnostics on the frame"), both in the `2026-10-12-graph-edge-crossing-avoidance` lane — that is, well after
the goldens at `01d3900b28` were pinned. Every frame also pays endpoint-visibility computation and
displacement merging.

## 5. The number worth chasing first

Three-map-clustered has 3 maps and 180 nodes, and it pays **~600 ms per frame** where it used to pay 12 ms.
The reference scenario has 2000 nodes and 5000 edges and pays ~460 ms more. A 180-node scenario paying *more*
per frame than a 2000-node one means the cost is not scaling with the graph: it is closer to a fixed
per-frame overhead, which is both the largest ratio and the most likely to be avoidable.

That is a hypothesis from two numbers, not a measurement. It is recorded because it changes where to look: not
"make the correction faster on big graphs", but "why does a small graph pay a fixed ~600 ms".

## 6. Options, none of them taken here

1. **Re-derive the six thresholds** with the per-frame edge stage accepted as part of the contract, recording
   the old and new values and the commit that moved them (the same treatment 017 gave the conflict golden).
   Unblocks a green campaign run and makes future regressions visible against the new baseline.
2. **Make the edge stage incremental or conditional** — for example skip it when the boundary result and the
   conflict set are unchanged from the previous frame — then re-measure. Highest payoff if the ~600 ms fixed
   cost is real, and the only option that recovers the time rather than relabelling it.
3. **Treat the thresholds as advisory** for the worker stages and keep only the non-layout ones as gates.

Options 1 and 2 are not exclusive: 2 first, then 1 with whatever cost remains. Doing 1 without 2 would bake a
50x regression on small maps into the definition of "passing".

## 7. Environment notes from the measurement

- **Syncthing was consuming 236% CPU on the measuring host, hashing a tree that contained the repository, and
  would have scanned the measured `build/` directory.** It was paused for both campaigns and resumed after.
  The same daemon runs on the development machine, where it was observed at 175-236% during the campaign that
  first produced the failures — which explains why those absolute numbers are uniformly higher (reference
  `full-worker` 722 ms there against 596 ms here) and why one row passed there and failed here. The ratios
  are unaffected because both sides of this comparison were measured under the same controlled conditions.
- `.worktrees/pin-conflict-017` and `pm/graph-pin-conflict-bisect-017` disappearing during the runs was task
  017's own cleanup after its merge into `plugin/graph-workspace`, not interference.
- The worktrees, the Gradle daemon and all processes from this measurement were cleaned up; the main tree was
  left clean.

## 8. Two caveats on the measured numbers

**The measured "HEAD" is ambiguous between two commits.** The comparison used the tip of
`plugin/graph-workspace` at measurement time; it does not record its object id. Two commits landed on the
branch during the measurement window — `5ab54f26c4` ("pin the label fixture face instead of the host
SansSerif", 22:52) and `088ede2c28` ("ci: bump actions/setup-java to v6", 22:52) — so the HEAD campaign may
have run on `30813b2486` or on `088ede2c28`.

This is not a cosmetic ambiguity: `5ab54f26c4` changes **production** `LabelFonts` as well as adding test
fonts, and text metrics are passed into every layout stage
(`boundaryCorrection.apply(..., defaultMetrics(), ...)` and
`edgeConflictCorrection.apply(..., defaultMetrics(), ...)` in `LayoutWorker`). A font swap can move the
absolute cost of exactly the stages that regressed.

It is implausible that a font change turns 12 ms into 609 ms, so the **classification stands**: 101/102 rows
passing at the pinned commit on the same machine, and non-layout stages moving 1.08-1.21x while layout stages
move 3.5-54x, cannot be explained by which font the fixture used. But any *absolute* number quoted from §3
should be re-confirmed on the current tip before it is used to set a threshold.

**Absolute numbers are host-contended, ratios are not.** §7 records Syncthing at 236% CPU on the measuring
host and 175-236% on the development machine. The two sides of this comparison were measured on the same
controlled host, so the ratios are trustworthy; the absolute values are a ceiling, not a floor.

## 9. What would make this actionable

In order of cost:

1. **Re-run the HEAD side once on the current tip** (~50 min) to remove the ambiguity in §8 and to see whether
   `5ab54f26c4` moved the worker stages. Cheap, and it makes every number in §3 reproducible.
2. **Answer the §5 question**: why does a 180-node scenario pay ~600 ms per frame where a 2000-node scenario
   pays ~460 ms more than before? If it is a fixed per-frame overhead, that is the cheapest recovery available
   and it needs a profile, not a threshold change.
3. Only then decide between re-deriving the six thresholds and making the edge stage incremental (§6).
