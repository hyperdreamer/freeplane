# Graph Boundary Separation Performance Evidence

## Profile (Task 1)

Command: gradle :freeplane_plugin_graph:boundaryCorrectionProfile --rerun-tasks -PTestLoggingFull

Workload: reference-2000-5000, 2000 nodes, 1200 enclosures.

| stage | p50 (ns) | p95 (ns) | max (ns) |
| --- | ---: | ---: | ---: |
| workerTotal | 117554631 | 175256982 | 248790004 |
| directTotal | 112112466 | 160243257 | 189848521 |
| separation | 19356054 | 34452048 | 59581100 |
| hull | 791680 | 1744186 | 2702654 |
| plan | 91294643 | 123866731 | 155878250 |
| apply | 0 | 0 | 0 |

JFR command: ~/.sdkman/candidates/java/21.0.8-zulu/bin/jfr view hot-methods freeplane_plugin_graph/build/boundary-correction-profile/correction-profile.jfr

| rank | hot method | samples | share |
| --- | --- | ---: | ---: |
| 1 | org.freeplane.plugin.graph.geometry.HullGeometry.signOfSum(double[], int[], int, double[], int[]) | 637 | 14.78% |
| 2 | java.util.Objects.equals(Object, Object) | 565 | 13.11% |
| 3 | org.graphstream.ui.layout.springbox.Energies.clearEnergies() | 484 | 11.23% |
| 4 | java.lang.Double.isFinite(double) | 413 | 9.58% |
| 5 | org.freeplane.plugin.graph.geometry.HullGeometry.mergeComponent(double[], int[], int, double, int) | 332 | 7.70% |
| 6 | org.freeplane.plugin.graph.workspace.model.MapReferenceId.equals(Object) | 264 | 6.12% |
| 7 | java.util.HashMap.getNode(Object) | 141 | 3.27% |
| 8 | java.util.UUID.equals(Object) | 128 | 2.97% |
| 9 | java.util.Collections$UnmodifiableCollection$1.next() | 120 | 2.78% |
| 10 | java.lang.Math.scalb(double, int) | 117 | 2.71% |

Hotspot list:
- `BoundarySeparationCorrection.detect` child/parent containment scan: 1,180 enforced child/parent pairs with 8
  polygon vertices each, i.e. 9,440 `HullGeometry.contains` invocations per correction call. Its exact-arithmetic
  point-in-polygon leaf work is the top JFR cost (`HullGeometry.signOfSum` 14.78%, `HullGeometry.mergeComponent`
  7.70%, `Math.scalb` 2.71%). The scan lands in the `plan` stage (p50 91.3 ms, p95 123.9 ms).
  Attribution note (audit I-1, fixed in the audit-fix wave): this Task 1 `plan` figure was measured
  before the timing split fix, when `planStart` was captured above the hull computation, so it includes
  `computeHulls`; per spec R13 the `plan` stage meters `detect` + candidate evaluation + conflict
  construction only. The corrected remediated split is recorded under "Corrected stage attribution"
  below.
- `BoundarySeparationCorrection.detect` enforced-set membership: `enforced.contains(child)` and
  `enforced.contains(parent)` scan a 1,180-element `ArrayList<EnclosureHullKey>` with 2 lookups per each of the
  1,200 enclosures. `EnclosureHullKey.equals` compares unmodifiable `SourceNodeKey` lists element-wise, measured
  as `java.util.Objects.equals` 13.11% (455 samples under `ArrayList.contains` -> `EnclosureHullKey.equals` ->
  `UnmodifiableList.equals`) plus `EnclosureKey.equals` 1.58%.
- The `MapReferenceId.equals` 6.12% and `java.util.UUID.equals` 2.97% rows belong to that same
  `ArrayList.contains` membership scan rather than to hash lookups: 252 of the 264 `MapReferenceId.equals` samples
  and all 128 `UUID.equals` samples sit under `BoundarySeparationCorrection.detect` -> `ArrayList.contains` ->
  `EnclosureHullKey.equals` -> `EnclosureKey.equals` -> `SourceNodeKey.equals`. The remaining `MapReferenceId.equals`
  samples are 6 in `ProjectionEngine.project`, 4 in the GraphStream `Seeds.center` list `indexOf`, and 2 in
  `ProjectionDiff.between`. The separate `HashMap.getNode` row (3.27%) holds the hash-lookup cost (e.g.
  `BoundarySizes.sizeOf`, `BoundarySeparationCorrection.separate`/`detect`, `GraphStreamLayoutEngine.replaceLinks`).
- `NodeSeparationProjection.residualViolations` all-pairs distance scan: 2000*1999/2 = 1,999,000 `Math.hypot`
  calls per correction call, measured as `Double.isFinite` 9.58% plus `StrictMath.hypot` 1.69% (410 of 413
  `Double.isFinite` samples attributed to this loop). This is the `separation` stage, p50 19.4 ms, p95 34.5 ms.
- `NodeSeparationProjection.validateFinite` walks the 3,200 node + anchor positions through unmodifiable
  collection wrappers: `Collections$UnmodifiableCollection$1.next` 2.78% (92 of 120 samples).
- GraphStream spring-box layout `Energies.clearEnergies` 11.23%: 484 samples under `BarnesHutLayout.moveNode` ->
  `TypedSpringBox.setParticlePosition`, the per-move energy reset over the 3,200-particle graph. This cost sits in
  the layout engine (`LayoutEngine.apply` and worker submit), outside the measured correction stages.

## Strict gate (Task 4)

Command: gradle :freeplane_plugin_graph:graphPerformanceDiagnostic -PgraphStrictPerformance --rerun-tasks -PTestLoggingFull

Result: exit code 1 after a complete run; the non-zero exit comes from the two
pre-existing environment waivers below and is not a boundary-separation
regression.

Ledger: freeplane_plugin_graph/build/graph-performance/performance-ledger.csv
Ledger SHA-256: c8af6527b0a97e6eb620c1cb717218e93170f2c60ca9f907875a2bb4011d0991
Archived ledger: archives/latest-strict-gate/performance-ledger.csv (sha256 in
`performance-ledger.csv.sha256`, run summary in `RESULT.md`)
Baseline ledger: archives/diagnostics/baseline-strict-gate/baseline-performance-ledger.csv
Baseline ledger SHA-256: 8bd8eb5fb440a7ced1e3586ad5658af8945a3824ba258a91b01d3c485516163a
Pre-remediation feature ledger SHA-256: 5d7a2aeec4891d30e30ede52a0c1f2023bacbd2d3cdd1c8b56a370de0e8c88f3

| scenario / stage | baseline p95 (ns) | pre-remediation p95 (ns) | remediated p50 (ns) | remediated p95 (ns) | remediated p99 (ns) | strict budget (ns) | verdict |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| reference-2000-5000 / full-worker | 51985400 | 137663516 | 28918550 | 53250728 | 77344782 | 100000000 | PASS |
| reference-2000-5000 / accepted-batch-first-frame | 76344593 | 160930981 | 49217235 | 85176227 | 102554476 | 150000000 | PASS |
| reference-2000-5000 / correction (plan + apply) | 2211368 | 99143459 | 3673673 | 8971692 | 9503972 | - | diagnostic only |
| reference-2000-5000 / force | 268972130 | 178961459 | 165256049 | 248247407 | 331383344 | 50000000 | waiver |
| reference-2000-5000 / edt-swap | 2700023 | 2398572 | 1328936 | 2630228 | 3066557 | 2000000 | waiver |

Latest ledger rows, verbatim from the archived performance-ledger.csv (one line per row, no surrounding fence):

reference-2000-5000,force,400,300,165256049,248247407,331383344,421955193,250000000,50000000,0,0,false
reference-2000-5000,correction,400,300,3673673,8971692,9503972,17312519,-1,-1,0,0,true
reference-2000-5000,full-worker,400,300,28918550,53250728,77344782,83424105,500000000,100000000,0,0,true
reference-2000-5000,edt-swap,400,300,1328936,2630228,3066557,4723134,10000000,2000000,0,0,false
reference-2000-5000,accepted-batch-first-frame,400,300,49217235,85176227,102554476,119437192,750000000,150000000,0,0,true

Environment waivers: `force` and `edt-swap` fail on the untouched baseline
commit `aa6ac48b02` with the same or worse p95, on a machine where the boundary
correction is absent; they are environment conditions and not regressions of
this feature segment.

Baseline comparison (supplementary, not part of the pass criterion): the
remediated `full-worker` p95 is 53,250,728 ns, 84.4 ms down from the pre-remediation 137.7 ms
and 2.4% above its 51,985,400 ns baseline (within run-to-run variance); the
remediated `accepted-batch-first-frame` p95 is 85,176,227 ns, 75.8 ms below the pre-remediation
160.9 ms and 11.6% above its 76,344,593 ns baseline. Both rows pass on the
strict-budget limb of the criterion below. Run wall time was 37m15s (start
2026-09-13T14:52:19Z, end 2026-09-13T15:29:34Z), within the extended 40-minute
process deadline; no deadline abort occurred.

Pass criterion: `full-worker` and `accepted-batch-first-frame` are at or below
their baseline p95 values (51,985,400 ns and 76,344,593 ns) or at least under
their strict budgets (100,000,000 ns and 150,000,000 ns). No threshold,
workload, sample count, or measurement mapping changed; the only diagnostic
change in this segment is `PROCESS_DEADLINE_NANOS` from 10 to 40 minutes.

### Corrected stage attribution (audit I-1)

The audit found that `planStart` was captured above the hull computation, so the
Task 1/Task 2 `plan` figures quoted above include `computeHulls` and
`CORRECTION = plan + apply` double-counted hull time. Commit `39ce5d065e`
moves `planStart` below the `hullNanos` accumulation so the four stages are
disjoint per spec C10/R13; correction decisions are byte-identical (only
`System.nanoTime()` capture points moved) and a test now pins that the hull
measurement time is not in `plan`. Re-measured at that commit with the focused
profile (`gradle :freeplane_plugin_graph:boundaryCorrectionProfile
--rerun-tasks -PTestLoggingFull`; log
`archives/latest-strict-gate/boundary-correction-profile.log`, samples
`archives/latest-strict-gate/correction-profile.csv`, sha256
`85e4780c57c2118125f78c08faae246791461c0c175471569159fce7d8290370`):

| stage | p50 (ns) | p95 (ns) | max (ns) |
| --- | ---: | ---: | ---: |
| separation | 19867433 | 38153158 | 50131627 |
| hull | 931140 | 1553285 | 2934923 |
| plan | 3837962 | 7926286 | 11365830 |
| apply | 0 | 0 | 0 |

`CORRECTION = plan + apply` is the strict-gate `correction` row above
(p50/p95/p99/max = 3,673,673 / 8,971,692 / 9,503,972 / 17,312,519 ns, ledger
line recorded verbatim). The earlier remediated Task 2 split (plan p50
4,512,020 ns) was measured with the pre-fix attribution and also included hull;
the corrected remediated split is the table above.
