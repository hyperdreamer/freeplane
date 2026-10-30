# 016 — The performance campaign's process deadline is undersized

Task Identifier: `016-perf-campaign-deadline`
Ticket ID: none (no external ticket exists for this task)
Base commit: `e388469400`
Worktree: `.worktrees/perf-campaign-016`, branch `pm/graph-perf-campaign-016`
Predecessor: `014-graph-perf-gate-reachability` §7

## 1. Problem

Task 014 made the raw-reference wall-clock gate reachable and falsifiable, and left one decision open:
`graphPerformanceDiagnostic` still exits non-zero on this host because the measurement campaign that
follows the gate exceeds `PROCESS_DEADLINE_NANOS` (40 minutes). 014 §7 offered a split (a fast gate task
plus a campaign task) or a resize of the deadline, and recorded that the choice needed the measured cost
of the campaign.

## 2. Measurement

Progress-instrumented run (`System.out` probe, since reverted), cut after 10 minutes:

| Scenario | Samples | Cost | Per sample |
|---|---|---|---|
| `two-map` | 50 | 25 s | 0.51 s |
| `three-map-clustered` | 50 | 37 s | 0.76 s |
| `reference-2000-5000` | 153 of 700 in 600 s | **3.92 s/sample** | dominates |
| projected tail (`skewed-reference`, `one-pinned-map`, `two-pinned-maps`) | 150 | ~117 s | — |
| **projected total** | 950 | **≈49 min** | — |

So the guard was undersized by about 25%, not by orders of magnitude. That settles the 014 §7 decision:
the campaign is one coherent measurement whose only problem is a bound set just below its real cost.
Splitting the tool would have kept a campaign that can never produce its ledger, which is the tool's
actual purpose.

## 3. Design

**D1 — size the guard from the measurement.** `PROCESS_DEADLINE_NANOS` becomes 120 minutes. This is a
liveness guard, not an assertion: a wedged run never completes, so any finite bound catches it, and the
bound only has to exceed plausible slowness. 45 minutes for the reference scenario alone on a moderately
loaded host puts 40 minutes squarely in "reports the host as a defect" territory, which is the failure
mode this repository has already paid for three times (`DISPLAY_SETTLE_BUDGET_MILLIS`,
`OPERATION_TIMEOUT_NANOS`, `EDT_LIVENESS_BUDGET_MILLIS`). 120 minutes is 2.4× the measured cost.

**D2 — report per-scenario progress.** A silent hour is indistinguishable from the hang the guard exists
to detect, which makes a two-hour bound untolerable in practice. One line per scenario with its own cost
and the budget consumed.

Neither change touches any performance assertion: `allRowsPass()`, the per-stage thresholds, the
structural pins and the goldens are untouched.

## 4. Specification

### S1 — the campaign completes the sweep inside the guard
A full run reaches the end of all six scenarios without the deadline firing, and the per-scenario lines
account for the elapsed time.

### S2 — the gate still runs first and reports
The run's first measurement is still `verifyRawReferenceFrameBound()` and its verdict is printed
(014 S1/S2 unchanged).

### S3 — no performance assertion changes
`RAW_REFERENCE_MAX_FRAME_NANOS`, the stage thresholds, `TWO_PINNED_MAPS_EXPECTED_CONFLICTS`, the scenario
counts, `WARMUP_SAMPLES`/`TIMED_SAMPLES` and `allRowsPass()` are untouched.

### S4 — progress is observable
Each scenario reports its own duration and the budget consumed.

### S5 — whatever the campaign reports is recorded, not tuned
If the completed run fails a check, the failure is a finding to be reported and triaged. Changing a
golden so the run goes green is forbidden here.

## 5. Verification

Full run, `gradle :freeplane_plugin_graph:graphPerformanceDiagnostic`, 50 m 47 s:

```
Raw reference complete-frame bound ok: 2000 nodes/5000 edges, warm-up frame(s) 1 excluded,
    max 1271335679 ns <= 5000000000 ns; frames=[5973171061(warm-up),...]
Scenario two-map took 29s; 44s of 7200s used
Scenario three-map-clustered took 41s; 86s of 7200s used
Scenario reference-2000-5000 took 2722s; 2809s of 7200s used
Scenario skewed-reference took 204s; 3013s of 7200s used
Scenario one-pinned-map took 19s; 3033s of 7200s used
```

**S1 holds**: every scenario completed, the slowest by a factor of 16 over the projection, total 3050 s
against a 7200 s guard — the deadline never fired, which is the whole point of the change.
**S2 holds**: the gate's verdict is the first line of the run.

## 6. The run then fails a check that has never been checked — and it is a finding, not a regression here

```
DiagnosticFailure: Two-pinned-maps conflict count changed: expected 1 but measured 11
```

This is `TWO_PINNED_MAPS_EXPECTED_CONFLICTS`, a golden introduced by `01d3900b28`
("map boundary correction timings and re-derive pin goldens"). It is asserted **only** by this
diagnostic (`grep` finds no use in `PerformanceTripwiresShould` or any other suite class), and this
diagnostic has never completed a run — so the golden has been unverified since it was set, while the
layout and edge-conflict code went through the residual-routing and ring-relocation lanes.

Two readings, and this task deliberately does not choose between them:

1. the golden is stale and 11 is the current true value, or
2. pinned-map conflict handling regressed from 1 to 11.

**Neither the expectation nor the layout was touched.** Setting `TWO_PINNED_MAPS_EXPECTED_CONFLICTS` to 11
would make the run green and destroy the only evidence that anything changed; that is the failure mode
this record exists to avoid. Deciding between (1) and (2) needs a comparison against the commit that set
the golden and against the remote lineage, which is a layout-owner question with its own plan.

## 7. Files

| File | Change |
|---|---|
| `freeplane_plugin_graph/src/test/java/.../performance/GraphWorkspacePerformanceDiagnostic.java` | D1, D2 |
| `ai-specs/tasks/done/016-…md`, `016-…-plan.md` | this record and its plan |

No production file, no fixture, no golden, no threshold, no Gradle task.

## 8. Honest limits

- The 120-minute value is derived from one measured run on a moderately loaded host plus a 2.4× margin.
  It is a liveness guard, so being generous costs nothing except patience; being too tight costs a false
  failure, and that is what it just cost.
- The campaign remains a fifty-minute instrument. It is not CI material even now. If a fast wall-clock
  gate in CI is wanted, 014 §7's split is still the way to get it — it is now optional, not a
  prerequisite for the campaign working.
- The run exits non-zero for a reason unrelated to this change (§6). The tool is honest about it; the
  task does not paper over it.
