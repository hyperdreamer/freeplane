# Epoch-2 acceptance: the reported workspace does not converge

Status: **ACCEPTANCE FAILS. Do not deliver on the strength of a clear mid-settle frame.**

Task identifier: `2026-10-26-edge-node-crossing-residual`

## Question 1 — why did the two harnesses disagree about idle?

They did not disagree about protocol. They measured different inputs.

The archived probe run of 2026-10-26 recorded `projection: nodes=28 ... settle: steps=178 idle=true`. The same
probe source, re-run against the current maps on 2026-10-28, records:

```
map[1] Axiomatic_Set_Theory.mm      sourceNodes=  7455 projectedNodes= 204 connectors=1445 graphGroups=11
map[2] Point-set_Topology.mm        sourceNodes=  9025 projectedNodes= 247 connectors=1229 graphGroups=11
map[3] Real_Anaylsis.mm             sourceNodes=  9834 projectedNodes= 150 connectors=1474 graphGroups=7
projection: nodes=29 edges=12 enclosures=7 pins=0
```

Map 1 grew from 171 projected nodes and 10 graph groups to **204 and 11**. The added disc is
`ID_1271861869`, a node whose text is `Theorem`, whose details read `Equivalent Definitions`, and which carries
`<graph_group version="1"/>`. No archived disc disappeared. The difference is the input, not the harness: the
archived 28-disc graph converged in 178 steps; the current 29-disc graph does not converge at all.

Evidence: `doc/edge-node-crossing/epoch2/non-convergence-trace.txt`.

## Question 2 — is the crossing a settled-state or a mid-settle artifact?

It is a mid-settle artifact, because this graph never reaches a settled state.

The probe drives the unchanged production `LayoutWorker` with `LayoutCalibration.spikeDefaults()` and the
unchanged idle predicate, and reports the trajectory:

| Step | idle | residualViolations | verified |
| ---: | --- | ---: | --- |
| 2 000 | false | 2 | true |
| 4 000 | false | 3 | true |
| 8 000 | false | 1 | true |
| 12 000 | false | 2 | true |
| 18 000 | false | 1 | true |
| 24 000 | false | 0 | true |
| 26 000 | false | 1 | true |
| 30 000 | false | 3 | true |

The residual violation count oscillates between 0 and 3 and never reaches 0 and stays there. This is a limit
cycle, not slow convergence: 30 000 frames with no approach to idle, and the 600-second bounds reported by the
acceptance lane were simply where an unbounded oscillation was cut off.

## What this means for the reported defect

1. **The user's canvas is a frame from a perpetual oscillation.** Any screenshot of it captures a moment, not a
   settled result. An edge crossing a disc can therefore appear and then disappear without any code change, and
   the same is true of its repair.
2. **The two changes delivered by this run cannot fix the reported symptom.** The routing control-position group
   and the relocation ring expansion both operate correctly inside a frame — the audit shows clear published
   paths at the sampled frames, and `residual = 0` is reached at step 24 000 — but neither can make a
   non-convergent layout stable, and neither is what causes the disc to sit on an edge in a given frame.
3. **The acceptance lane's `REPLAY VERDICT: all published paths clear` is not a pass.** It was measured on a
   frame of an oscillating system, on input that differs from the archived baseline. It must not be promoted to
   acceptance.

## Why the acceptance cannot be closed by re-baselining

Re-freezing the expectations on the current maps would not help: there is no settled frame to freeze. The only
honest acceptance record for this input is the failure above. Closing MAJOR-2 therefore requires a **new,
separate defect to be fixed first**: the layout must converge for this workspace.

## Recommended next task — not authorised by this record

`LayoutWorker`/`LayoutSettleLoop` non-convergence on a 29-disc, 7-enclosure, 12-edge workspace. The narrowest
useful experiment: determine which pair or enclosure keeps moving, and whether the newly added group
`ID_1271861869` is sufficient by itself to cause the cycle (remove it from a copy of map 1 and re-run the same
probe). If removing that one group restores convergence in ~178 steps, the defect is a specific interaction
between that group's enclosure membership and the boundary-separation or edge-conflict correction, not a general
layout instability.

This record does not authorise that work. It records that delivery of the current changes cannot be justified by
the evidence available.
