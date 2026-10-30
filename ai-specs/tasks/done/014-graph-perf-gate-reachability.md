# 014 — The raw-reference wall-clock gate: reachable and meaningful

Task Identifier: `014-graph-perf-gate-reachability`
Ticket ID: none (no external ticket exists for this task)
Base commit: `0fb4221622` (`plugin/graph-workspace`)
Worktree: `.worktrees/perf-gate-014`, branch `pm/graph-perf-gate-014`
Predecessors: `010-make-graph-test-suite-meaningful` §10, `013-graph-test-hygiene-open-items` §9

## 1. Problem

`GraphWorkspacePerformanceDiagnostic.verifyRawReferenceFrameBound()` is the single owner of
`RAW_REFERENCE_MAX_FRAME_NANOS`. Task 013 §9 established, by running it, that the gate had **never
produced a verdict**: the diagnostic spends its entire 40-minute `PROCESS_DEADLINE_NANOS` budget in the
measurement campaign that precedes the gate, dies at `runScenario`, and never reaches `run() :195`.

A second defect is independent of the first. `rawReferenceMaxFrameNanos()` takes the maximum over **all**
recorded frames, and the first frame is always the maximum because it pays class loading and first-pass
JIT for the whole worker/geometry/routing path. Task 010 §1.3 measured that on two commits: frame 0 was
5 056–6 043 ms in six interleaved runs, frames 1–7 were 690–1 510 ms. So even if the gate had been
reachable it could only ever fail, and a gate that can only fail reports nothing.

The note carried by task 010 §5 named the repair: *bound steady-state frames, keyset-indexed to the
fixture, rather than raising or lowering the 5 s constant*. This task does that.

## 2. Measurements taken before the change

Fixture only, current code, one run on this host (throwaway probe in `/tmp`, production untouched):

| Quantity | Value |
|---|---|
| projection keyset | **2000 nodes / 5000 edges** |
| frame 0 | **5 824 ms** — over the 5 000 ms bound |
| frames 1–7 | 988, 865, 824, 831, 885, 900, 910 ms |
| whole fixture (8 frames) | **12.3 s** |
| maximum over all frames | 5 824 ms (fails) |
| maximum over frames 1–7 | 988 ms (passes with 5× headroom) |

The two maxima differ by a factor of six and lie on opposite sides of the bound, which is exactly the
diagnosis: the constant is not wrong, the measurement was.

## 3. Design

Four changes, all in test-scope files. `RAW_REFERENCE_MAX_FRAME_NANOS` is **unchanged** — nothing is
loosened or tightened.

**D1 — the measured window excludes start-up.** `RAW_REFERENCE_WARMUP_FRAMES = 1`. The bound applies to
frames `[1, RAW_REFERENCE_FRAMES)`; every frame is still recorded, and the failure and success messages
list all of them with the warm-up frame marked. Rationale in the code: the bound exists to catch a layout
regression, and frame 0 measures start-up, not layout.

**D2 — the gate runs before the campaign.** `verifyRawReferenceFrameBound()` moves from after
`runLifecycleProbes` to immediately after `configureCanvas()`. A ~12-second deterministic gate must not
be sequenced behind a multi-hour sweep whose own deadline can expire first; that ordering is what made
the gate's verdict unobservable. It also makes the diagnostic fail fast: a breached bound now aborts in
seconds instead of after the campaign.

**D3 — the workload is pinned.** The bound is a statement about a keyset, so the gate fails if the
fixture stops producing the `2000`-node/`5000`-edge reference projection, naming the recalibration it
needs. `PerformanceTripwiresShould.generateTheReferenceStructuralCounts` pins the *generator's* counts;
this pins the *projection* the bound is measured on, which is not the same statement.

**D4 — the measured window cannot be empty, and a green run says what it measured.** The maximum over no
frames is zero, which would pass silently, so `measuredFrames <= 0` is an explicit failure. On success the
gate prints one line naming the keyset, the warm-up exclusion, the maximum, the bound and the per-frame
durations — because when the campaign that follows cannot finish, that line is the *only* wall-clock
evidence the tool produces.

`rawReferenceMaxFrameNanos()` keeps its old meaning (all frames, diagnosis only) and
`rawReferenceMeasuredMaxFrameNanos()` is added for the quantity the gate asserts.

## 4. Specification

### S1 — the gate executes on every run
`gradle :freeplane_plugin_graph:graphPerformanceDiagnostic` reaches
`verifyRawReferenceFrameBound()` and produces a verdict before any campaign sample runs.

### S2 — the verdict is correct for a healthy tree
On this host the gate reports a measured maximum inside the bound, and the reported maximum is over
frames 1–7 only, with the warm-up frame marked in the printed list.

### S3 — the assertion is live
Reducing `RAW_REFERENCE_MAX_FRAME_NANOS` below the measured steady-state cost makes the gate fail, fast,
with a message that lists every frame and names the bound. The failure must occur before the campaign.

### S4 — the keyset pin is live
Changing the calibrated keyset makes the gate fail with the recalibration message rather than measuring a
different workload silently.

### S5 — the warm-up exclusion is pinned by a test, in both directions
A stall confined to the warm-up frame passes the gate; a stall that persists into the measured window
fails it. Both are deterministic, using a synthetic clock; no sleep, no wall-clock threshold.

### S6 — no constant is moved and no production file changes
`RAW_REFERENCE_MAX_FRAME_NANOS`, `PROCESS_DEADLINE_NANOS`, `OPERATION_TIMEOUT_NANOS`,
`WARMUP_SAMPLES`, `TIMED_SAMPLES`, the scenario counts and every assertion in
`PerformanceTripwiresShould` other than the accessor swap are unchanged.
`freeplane_plugin_graph/src/main/java/**` is byte-identical to `0fb4221622`.

### S7 — the suite still passes
`freeplane_plugin_graph/scripts/run-suite-on-xvfb.sh` exits 0 with the new test included.

## 5. Out of scope, and why it is not silently dropped

The measurement campaign itself needs far more than `PROCESS_DEADLINE_NANOS`. From the constants: six
scenarios, 450 measured samples, each running four label-placement triggers of 20 warm-up + 100 timed
samples — **216 000 label placements** plus 950 layout frames. That is a data-quality campaign, not a
gate, and 40 minutes is not its budget. Raising `PROCESS_DEADLINE_NANOS` would not weaken any assertion
(it is a hang guard), but it would turn a red job into a four-hour red job.

The honest options, needing an owner decision, are recorded in §7 rather than implemented here.

## 6. Files

| File | Change |
|---|---|
| `freeplane_plugin_graph/src/test/java/.../performance/GraphWorkspacePerformanceDiagnostic.java` | D1–D4 |
| `freeplane_plugin_graph/src/test/java/.../performance/PerformanceTripwiresShould.java` | warm-up-exclusion test; accessor swap |
| `ai-specs/tasks/done/014-…md`, `014-…-plan.md` | this record and its plan |

No production file, no fixture, no Gradle task added.

## 7. Open decision (not this task)

`graphPerformanceDiagnostic` remains red on this host **after** the gate passes, because the campaign
exceeds its own process deadline. Two candidate answers, both owner-level:

- **Split the tool.** Keep `graphPerformanceDiagnostic` as the campaign with a deadline sized to it, and
  add a separate task that runs only the raw-reference gate — ~15 s, so CI gets a real wall-clock gate.
  Cost: one more Gradle task and a gate-only mode.
- **Size the deadline to the campaign.** Change `PROCESS_DEADLINE_NANOS` only, and accept that the task
  is a long-running campaign that happens to contain a fast gate.

This task deliberately does neither: it fixes the gate and makes its verdict observable, which both
answers depend on.

## 8. Verification

See the plan's Task 4 and the evidence below.

### 8.1 S1/S2 — the gate runs and reports, end-to-end

```
gradle :freeplane_plugin_graph:graphPerformanceDiagnostic   (270s window, campaign still running)
Raw reference complete-frame bound ok: 2000 nodes/5000 edges, warm-up frame(s) 1 excluded,
max 1563371122 ns <= 5000000000 ns;
frames=[4602827844(warm-up),1027331515,1139292429,1563371122,1150620726,1101878510,1057030096,780777765]
```

Before this change the same task ran 40 m 35 s and died at `runScenario:300` without ever reaching the
gate (013 §9). The verdict is now produced while the campaign is still running, and the printed list shows
the warm-up frame over the bound and the measured maximum 3.2× inside it.

### 8.2 S3 — the bound is live

A copy of the diagnostic with `RAW_REFERENCE_MAX_FRAME_NANOS = 1_000_000L`, compiled aside and run
against the same fixture:

```
DiagnosticFailure: Raw reference frame 1403251581 ns exceeds the 1000000 ns complete-frame bound over
the 7 measured frame(s); frames=[6149073258(warm-up),1403251581,968221326,...]
```

### 8.3 S4 — the keyset pin is live

A copy with `RAW_REFERENCE_NODES = 1999`:

```
DiagnosticFailure: Raw reference bound is calibrated for a 1999-node/5000-edge projection but the
fixture produced 2000 nodes and 5000 edges; recalibrate RAW_REFERENCE_MAX_FRAME_NANOS before trusting
this gate
```

### 8.4 S5 — both directions of the warm-up exclusion, deterministically

`PerformanceTripwiresShould.excludesOnlyTheWarmUpFrameFromTheCompleteFrameBound` stalls only the first
frame through the existing synthetic clock and asserts that the all-frame maximum is over the bound, the
measured maximum is inside it, and `verifyRawReferenceFrameBound()` does not throw. Its sibling
`rejectsAFrameWhoseRoutingExceedsTheCompleteFrameBound` stalls every frame and still fails the gate.

### 8.5 S6/S7

```
git diff --exit-code 0fb4221622 -- freeplane_plugin_graph/src/main/java   → exit 0
freeplane_plugin_graph/scripts/run-suite-on-xvfb.sh                        → exit 0
    1459 tests, 0 failures, 4 skipped, 13m40s
```

`RAW_REFERENCE_MAX_FRAME_NANOS` is still asserted to be exactly `5_000_000_000L` by
`PerformanceTripwiresShould`, so the constant cannot drift unnoticed.
