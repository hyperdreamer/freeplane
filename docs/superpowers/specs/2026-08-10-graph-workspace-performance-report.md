# Graph Workspace Batch I Performance Report

Date: 2026-08-22T06:05:38+08:00
Status: PASS on the post-fix strict diagnostic

## Provenance

- Merge base: `48cfed5511f4fdbdc7f79ffe46b0cc4a4494c056`
- Task 37 handoff SHA: `8950e2fe0fc4209f3860d0608e8d694623f516a6`
- Task 2 calibration commit: `bfb21b5b045bd6872d633c162a7ad6781165244c`
- Task 2 synchronization repair commit: `6816f546b2ea28dbe6714fb99401c86482625238`
- The repair commit is the fixed HEAD used for the post-fix strict run. It changes only `GraphStreamLayoutEngine.java` and was independently re-reviewed as resolved.
- The report update is based on fixed HEAD `6816f546b2ea28dbe6714fb99401c86482625238`; its report-bearing commit SHA is recorded in `task-2-implementer-report.md` and final completion evidence.
- The calibration commit contains exactly the seven Task 2 allowlisted paths. The repair and report commits also remain within that allowlist.

## Environment

- OS: Linux Arch, kernel `7.1.4-arch1-1`, `x86_64`
- CPU: Intel(R) Core(TM) Ultra 7 155H, 1 socket, 16 physical cores, 22 logical CPUs, 2 threads per core
- RAM: 65,525,412 kB reported by `/proc/meminfo` (approximately 62.5 GiB)
- Java: Zulu OpenJDK `21.0.8`, build `21.0.8+9-LTS`
- Java home: `/home/guest/.sdkman/candidates/java/21.0.8-zulu`
- Gradle: `9.0.0`
- JVM options: no `JAVA_TOOL_OPTIONS`, `_JAVA_OPTIONS`, `GRADLE_OPTS`, or `JAVA_OPTS` were set
- Headless setting: `java.awt.headless=true`
- Locale settings: `user.language=en`, `user.country=US`
- Font: `Font("Dialog", Font.PLAIN, 12)` with `FontRenderContext(new AffineTransform(), false, false)`
- Canvas: `1024 x 768`, `BufferedImage.TYPE_INT_ARGB`, light theme, fresh image per repaint sample

## Commands And Evidence

All commands used the required Zulu 21 Java home, prepended `$JAVA_HOME/bin` to `PATH`, and used `gradle`, never `gradlew`.

- Untouched strict baseline at Task 37 SHA: `gradle :freeplane_plugin_graph:graphPerformanceDiagnostic -PgraphStrictPerformance -PTestLoggingFull` -> exit 1 after 10m 11s at the ten-minute process deadline; no authoritative ledger was emitted. Evidence is archived under `.superpowers/sdd/2026-08-21-graph-workspace-batch-i-performance/task-2-baseline/`.
- Post-fix strict run at `6816f546b2ea28dbe6714fb99401c86482625238`: `gradle :freeplane_plugin_graph:graphPerformanceDiagnostic -PgraphStrictPerformance -PTestLoggingFull` -> exit 0 in 7s. Output is `post-fix-strict-diagnostic-output.txt`; status is `post-fix-strict-status.txt`.
- Focused synchronization tests from the fix round: `gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.TypedForcesShould --tests org.freeplane.plugin.graph.layout.GraphStreamBoundaryShould --tests org.freeplane.plugin.graph.layout.LayoutWorkerShould -PTestLoggingFull` -> exit 0. The fixer also ran `:freeplane_plugin_graph:check` -> exit 0.
- Complete final graph gate: `gradle :freeplane_plugin_graph:test :freeplane_plugin_graph:check -PTestLoggingFull` -> exit 0 in 7s, including `verifyGraphBundle`.
- Whitespace checks: `git diff --check` -> exit 0 before the report update and before commit.

## Artifacts

Authoritative final ledger:

`freeplane_plugin_graph/build/graph-performance/performance-ledger.csv`

Post-fix archived ledger:

`.superpowers/sdd/2026-08-21-graph-workspace-batch-i-performance/post-fix-strict-ledger.csv`

The authoritative and archived post-fix ledger SHA-256 is:

`2076cae9dbc2760f8766b7cc58ca4826ff9d176dad7a58dee0f83032ea570e7e`

The untouched strict baseline emitted no authoritative ledger. Its evidence is:

- `.superpowers/sdd/2026-08-21-graph-workspace-batch-i-performance/task-2-baseline/command-output.txt`
- `.superpowers/sdd/2026-08-21-graph-workspace-batch-i-performance/task-2-baseline/extended-full-output.txt`
- `.superpowers/sdd/2026-08-21-graph-workspace-batch-i-performance/task-2-baseline/measurement-notes.md`
- `.superpowers/sdd/2026-08-21-graph-workspace-batch-i-performance/task-2-baseline/single-sample-ledger.csv`

Required fixture SHA-256 values, unchanged after calibration and synchronization repair:

- `freeplane_plugin_graph/build/graph-performance/two-map.fpg`: `c66acb490c564a8cc8203a2742a193e4c81421b688a4b13b5168d24cc44ce5ad`
- `freeplane_plugin_graph/build/graph-performance/three-map.fpg`: `9939eb26768c2be69bd378a97e9afd0af3a455bac767cb9acc2f29754b8a4202`
- `freeplane_plugin_graph/build/graph-performance/reference-2000-5000.fpg`: `366a7bbe316b9f11b974730f2f063821ddb0d6ed3cf0f1fc6ee67e92766a691c`

The final output directory contains only `two-map.fpg`, `three-map.fpg`, `reference-2000-5000.fpg`, and `performance-ledger.csv`.

The CSV header is exactly:

`scenario,stage,warmupCount,measuredCount,p50Nanos,p95Nanos,p99Nanos,maxNanos,normalThresholdNanos,strictThresholdNanos,failureCount,discardCount,pass`

## Baseline Guidance

The mandatory untouched strict run timed out without a ledger. The archived one-sample reference ledger is diagnostic guidance, not percentile evidence:

```csv
scenario,stage,warmupCount,measuredCount,p50Nanos,p95Nanos,p99Nanos,maxNanos,normalThresholdNanos,strictThresholdNanos,failureCount,discardCount,pass
reference-2000-5000,snapshot,1,1,87452,87452,87452,87452,-1,-1,0,0,true
reference-2000-5000,projection,1,1,25384890,25384890,25384890,25384890,-1,-1,0,0,true
reference-2000-5000,diff,1,1,10544152,10544152,10544152,10544152,-1,-1,0,0,true
reference-2000-5000,mutation,1,1,59816373,59816373,59816373,59816373,-1,-1,0,0,true
reference-2000-5000,force,1,1,493510646,493510646,493510646,493510646,250000000,50000000,0,0,false
reference-2000-5000,correction,1,1,4105834,4105834,4105834,4105834,-1,-1,0,0,true
reference-2000-5000,hull,1,1,823274366,823274366,823274366,823274366,-1,-1,0,0,true
reference-2000-5000,label,1,1,67991890,67991890,67991890,67991890,-1,-1,0,0,true
reference-2000-5000,full-worker,1,1,949388259,949388259,949388259,949388259,500000000,100000000,0,0,false
reference-2000-5000,edt-swap,1,1,1438640,1438640,1438640,1438640,10000000,2000000,0,0,true
reference-2000-5000,repaint,1,1,340473844,340473844,340473844,340473844,-1,-1,0,0,true
reference-2000-5000,accepted-batch-first-frame,1,1,1899309230,1899309230,1899309230,1899309230,750000000,150000000,0,0,false
```

## Measurement Contracts

The diagnostic clock captures one `System.nanoTime()` origin and reports checked relative elapsed nanoseconds. It rejects subtraction overflow, negative values, and backward readings. The accepted-batch timestamp and every stage endpoint use that same relative clock.

The process deadline is ten minutes. Each worker completion, EDT state swap, and repaint operation has a five-second timeout. Interrupted operations restore the interrupt flag, cancel the bounded future, fail the diagnostic, and enter cleanup. Layout workers, executors, bounded-operation executors, Swing EDT operations, and lifecycle resources are closed on success and failure. A failed operation increments `failureCount`, does not become a duration, and cannot be silently omitted. The post-fix run has zero failures and zero discards in all 72 rows.

The twelve stage boundaries are:

1. `snapshot`: before constructing `ProjectionInput` through validation completion.
2. `projection`: immediately around `ProjectionEngine.project(input)`.
3. `diff`: immediately around `ProjectionDiff.between(previous, current)`.
4. `mutation`: immediately around owner-thread `LayoutEngine.apply(request)`.
5. `force`: immediately around owner-thread `LayoutEngine.step()`.
6. `correction`: immediately around public `MapTierCorrection.apply(projection, rawPositions, rawHull)`.
7. `hull`: immediately around one public `GraphGeometryEngine.computeHulls(projection, correctedPositions)` call.
8. `label`: immediately around one public `LabelPlacementEngine.place(projection, correctedHull, metrics)` call.
9. `full-worker`: from `LayoutWorker.submit(request)` through bounded first-frame completion; the diagnostic uses `submit`, never `step`.
10. `edt-swap`: around the non-EDT `GraphCanvas.setCanvasState(state)` call through completion of its internal EDT assignment.
11. `repaint`: inside the real EDT task around one explicit `GraphCanvas.paint(Graphics)` call on a fresh fixed-size image.
12. `accepted-batch-first-frame`: from `AcceptedBatch.acceptedAtNanos()` through projection, diff, worker submit/completion, corrected-position hull, label placement, immutable `CanvasState`, and completed canvas state swap.

Snapshot construction precedes accepted-batch creation and is excluded from `accepted-batch-first-frame`. The direct sequence is apply, raw hull, correction, corrected hull, labels, then step. The worker sequence is submit, worker raw hull/correction, corrected hull, labels, then publication composition. GraphStream apply, step, and close remain on one owner thread.

## Final Ledger

The post-fix strict run produced the required 6 scenarios x 12 stages = 72 rows. The following rows are copied from the authoritative post-fix CSV without changing the CSV schema:

```csv
scenario,stage,warmupCount,measuredCount,p50Nanos,p95Nanos,p99Nanos,maxNanos,normalThresholdNanos,strictThresholdNanos,failureCount,discardCount,pass
two-map,snapshot,20,30,21159,62602,74046,74046,-1,-1,0,0,true
two-map,projection,20,30,1176796,2652169,2701667,2701667,-1,-1,0,0,true
two-map,diff,20,30,480967,1253913,1525404,1525404,-1,-1,0,0,true
two-map,mutation,20,30,3600090,5785085,5978320,5978320,-1,-1,0,0,true
two-map,force,20,30,1617394,4259792,4335918,4335918,250000000,-1,0,0,true
two-map,correction,20,30,185915,379245,412103,412103,-1,-1,0,0,true
two-map,hull,20,30,68907,200223,248798,248798,-1,-1,0,0,true
two-map,label,20,30,2341176,8491486,8629675,8629675,-1,-1,0,0,true
two-map,full-worker,20,30,1223329,1865312,1947586,1947586,500000000,-1,0,0,true
two-map,edt-swap,20,30,695129,864052,903825,903825,10000000,-1,0,0,true
two-map,repaint,20,30,14103561,18026318,32018132,32018132,-1,-1,0,0,true
two-map,accepted-batch-first-frame,20,30,6246897,11999398,12571143,12571143,750000000,-1,0,0,true
three-map-clustered,snapshot,20,30,27929,55638,156536,156536,-1,-1,0,0,true
three-map-clustered,projection,20,30,1102967,1798086,2436201,2436201,-1,-1,0,0,true
three-map-clustered,diff,20,30,342912,595748,1168281,1168281,-1,-1,0,0,true
three-map-clustered,mutation,20,30,4475138,6044028,6074334,6074334,-1,-1,0,0,true
three-map-clustered,force,20,30,2853899,3639689,5408208,5408208,250000000,-1,0,0,true
three-map-clustered,correction,20,30,108334,141181,147471,147471,-1,-1,0,0,true
three-map-clustered,hull,20,30,72593,105691,123330,123330,-1,-1,0,0,true
three-map-clustered,label,20,30,3015058,3242796,4194155,4194155,-1,-1,0,0,true
three-map-clustered,full-worker,20,30,904911,1254569,1304301,1304301,500000000,-1,0,0,true
three-map-clustered,edt-swap,20,30,603825,718596,733155,733155,10000000,-1,0,0,true
three-map-clustered,repaint,20,30,23216613,27836207,40488700,40488700,-1,-1,0,0,true
three-map-clustered,accepted-batch-first-frame,20,30,6273333,7886667,10185731,10185731,750000000,-1,0,0,true
reference-2000-5000,snapshot,400,300,25572,32891,37180,41020,-1,-1,0,0,true
reference-2000-5000,projection,400,300,10801508,11978512,14909043,16510535,-1,-1,0,0,true
reference-2000-5000,diff,400,300,4463668,5256967,6429717,8402756,-1,-1,0,0,true
reference-2000-5000,mutation,400,300,42385815,46914422,54386569,63098702,-1,-1,0,0,true
reference-2000-5000,force,400,300,13769612,16018403,17845161,20779256,250000000,50000000,0,0,true
reference-2000-5000,correction,400,300,1084696,1201854,1539013,2259067,-1,-1,0,0,true
reference-2000-5000,hull,400,300,1072236,1220400,1638949,2535520,-1,-1,0,0,true
reference-2000-5000,label,400,300,90584575,101225447,119131194,145356943,-1,-1,0,0,true
reference-2000-5000,full-worker,400,300,4138672,6884845,9996945,10441393,500000000,100000000,0,0,true
reference-2000-5000,edt-swap,400,300,492962,650097,770055,820785,10000000,2000000,0,0,true
reference-2000-5000,repaint,400,300,250976539,274071228,306389922,340782555,-1,-1,0,0,true
reference-2000-5000,accepted-batch-first-frame,400,300,112882031,126485138,144605526,158613515,750000000,150000000,0,0,true
skewed-reference,snapshot,20,30,24327,33746,35636,35636,-1,-1,0,0,true
skewed-reference,projection,20,30,11387355,12230096,12510476,12510476,-1,-1,0,0,true
skewed-reference,diff,20,30,4197845,4640155,4924724,4924724,-1,-1,0,0,true
skewed-reference,mutation,20,30,42401984,49319887,50070875,50070875,-1,-1,0,0,true
skewed-reference,force,20,30,15359283,17100232,17133523,17133523,250000000,-1,0,0,true
skewed-reference,correction,20,30,1056114,1201192,1238069,1238069,-1,-1,0,0,true
skewed-reference,hull,20,30,1031427,1158410,1239325,1239325,-1,-1,0,0,true
skewed-reference,label,20,30,71751582,81846633,87209702,87209702,-1,-1,0,0,true
skewed-reference,full-worker,20,30,4011342,8160231,9290697,9290697,500000000,-1,0,0,true
skewed-reference,edt-swap,20,30,496336,641635,682257,682257,10000000,-1,0,0,true
skewed-reference,repaint,20,30,366109466,388779441,423629373,423629373,-1,-1,0,0,true
skewed-reference,accepted-batch-first-frame,20,30,94275927,106839848,111478090,111478090,750000000,-1,0,0,true
one-pinned-map,snapshot,20,30,10787,21331,31009,31009,-1,-1,0,0,true
one-pinned-map,projection,20,30,715309,1234657,1708683,1708683,-1,-1,0,0,true
one-pinned-map,diff,20,30,291495,316883,974217,974217,-1,-1,0,0,true
one-pinned-map,mutation,20,30,3621466,4719063,5634668,5634668,-1,-1,0,0,true
one-pinned-map,force,20,30,2248540,2761824,6943248,6943248,250000000,-1,0,0,true
one-pinned-map,correction,20,30,82403,97316,290070,290070,-1,-1,0,0,true
one-pinned-map,hull,20,30,67037,97794,158958,158958,-1,-1,0,0,true
one-pinned-map,label,20,30,2939114,3412836,3909946,3909946,-1,-1,0,0,true
one-pinned-map,full-worker,20,30,580528,830344,913086,913086,500000000,-1,0,0,true
one-pinned-map,edt-swap,20,30,422499,639389,648401,648401,10000000,-1,0,0,true
one-pinned-map,repaint,20,30,20184976,23745276,28699682,28699682,-1,-1,0,0,true
one-pinned-map,accepted-batch-first-frame,20,30,5148147,9006231,9689677,9689677,750000000,-1,0,0,true
two-pinned-maps,snapshot,20,30,10711,11432,12000,12000,-1,-1,0,0,true
two-pinned-maps,projection,20,30,726459,1137450,1184344,1184344,-1,-1,0,0,true
two-pinned-maps,diff,20,30,286225,328739,338237,338237,-1,-1,0,0,true
two-pinned-maps,mutation,20,30,3422060,4904338,5019727,5019727,-1,-1,0,0,true
two-pinned-maps,force,20,30,2183480,2711733,2719089,2719089,250000000,-1,0,0,true
two-pinned-maps,correction,20,30,83896,92399,99920,99920,-1,-1,0,0,true
two-pinned-maps,hull,20,30,68452,88577,91490,91490,-1,-1,0,0,true
two-pinned-maps,label,20,30,3058684,3252020,6919692,6919692,-1,-1,0,0,true
two-pinned-maps,full-worker,20,30,513418,718819,726073,726073,500000000,-1,0,0,true
two-pinned-maps,edt-swap,20,30,447032,638052,643253,643253,10000000,-1,0,0,true
two-pinned-maps,repaint,20,30,16698589,18086874,18209367,18209367,-1,-1,0,0,true
two-pinned-maps,accepted-batch-first-frame,20,30,5127162,5668279,7255281,7255281,750000000,-1,0,0,true
```

Strict reference ceilings and post-fix results:

- force p95 `16018403` ns and p99 `17845161` ns; ceilings `50000000` ns and normal threshold `250000000` ns
- full-worker p95 `6884845` ns and p99 `9996945` ns; ceiling `100000000` ns and normal threshold `500000000` ns
- accepted-batch-first-frame p95 `126485138` ns and p99 `144605526` ns; ceilings `150000000`/`300000000` ns and normal thresholds `750000000`/`1500000000` ns
- edt-swap p95 `650097` ns; ceiling `2000000` ns and normal threshold `10000000` ns

Normal thresholds are exactly five times the strict ceilings. Non-gated stage thresholds are explicitly `-1` in every row.

## Retained Calibration And Repair Diffs

Every retained calibration change was restricted to a measured production boundary and was kept only after focused tests and a passing diagnostic. The baseline values below are the archived one-sample guidance because the mandated untouched strict run timed out.

- `GraphStreamLayoutEngine`: the existing SHA-256 per-particle seed and deterministic random values are preserved. For projected workloads with at least 1,000 visible nodes, the deterministic initial envelope is `50.0` world units; smaller workloads retain `0.002`. Baseline force guidance was `493510646` ns; post-fix reference force p95 is `16018403` ns.
- `GraphGeometryEngine`: an exact synchronized two-entry LRU cache reuses immutable geometry only when node values, enclosure values, prominence, and exact `LayoutPositions` all match. No polygon, hull, label, or coordinate approximation is used. Baseline hull guidance was `823274366` ns; post-fix reference hull p95 is `1220400` ns, with all 1,200 hulls present.
- `GraphStreamLayoutEngine`: repeated requests with an empty `ProjectionDiff` and equal pins skip redundant particle/link synchronization only when the accepted workspace matches, `beforeGeneration` equals the engine's last synchronized generation, and the prior request has the same workspace and pins. `disposeGraph()` invalidates the synchronization watermark; full synchronization records the accepted projection generation. Workspace replacement and superseded structural requests therefore force synchronization. Baseline full-worker guidance was `949388259` ns; post-fix reference full-worker p95 is `6884845` ns.
- `PerceptualIdlePolicy`: immutable `LayoutPositions` map views replace per-check key-set copies. RMS, maximum, stable-frame, key-change, and empty-frame semantics are unchanged.
- `LayoutCalibration`: immutable `spikeDefaults()` is shared instead of allocating an equivalent calibration object per engine. Existing strengths remain containment `0.15`, hierarchy `0.30`, and same-map `1.0`; ordered-positive calibration is unchanged.
- `GraphPainter`: visible endpoint construction is performed once per paint and reused by edges, pins, and connection-preview branches. Arrowheads, multiplicity cues, labels, hulls, pins, dimming, selection, hover, search, connection preview, and accessibility-visible rendering remain active. Baseline repaint guidance was `340473844` ns; post-fix reference repaint p95 is `274071228` ns. Repaint is diagnostic-only and not a strict gate.
- `GraphUpdateCoordinator`: the immutable empty `GraphProjection` used for initialization is shared. Batching, generation checks, stale publication rejection, listener ordering, close ordering, and EDT behavior are unchanged. Coordinator-internal timing is outside the observation boundary.

## Preserved Invariants

- Deterministic seed namespace remains `20260810`; initial positions remain deterministic and derive from workspace and stable particle identity.
- SpringBox quality remains exactly `0.10`; GraphStream 1.3 remains behind the package-private `layout.graphstream` boundary; no GraphStream type is exposed through public signatures.
- No reflection, `LayoutRunner`, second layout implementation, dependency, Gradle diagnostic registration, public API, Task 37 source, or fixture source was changed.
- Ordered force multipliers remain positive and ordered: containment `<` hierarchy `<` same-map. Aggregate cross-map displacement remains capped at `0.005` per particle, not per edge.
- Rebuild and synchronization retain O(N+E) behavior for changed requests. The no-op guard is restricted to proven equivalent empty diffs with matching workspace, generation provenance, and pins.
- The reference projection retains 2,000 visible nodes, 1,200 enclosures, 5,000 projected edge keys and contributors, 3,500 native same-map contributors, 1,500 cross-map contributors, 2,000 containment links, 1,180 hierarchy links, 3,200 particles, and 8,180 springs.
- Immutable `LayoutFrame`, corrected positions, `CanvasState`, `GraphGeometry`, and canvas publication remain in place. Coordinates are finite.
- Rigid pinned-map correction, one-pinned-map zero-conflict behavior, two-pinned-map one-conflict behavior with both pin identities, labels, pins, hulls, arrows, multiplicity cues, dimming, selection, hover, search, connection preview, and accessibility-visible rendering remain covered by the focused suite and diagnostic.
- Fixture bytes are unchanged and match all three required SHA-256 values.
- Generation mismatch rejection, post-close worker rejection, executor cleanup, worker-thread cleanup, bounded EDT operations, and repaint checksum checks pass.

## Observation Boundary Exclusions

The Task 37 public diagnostic measures deterministic workspace input construction, projection, diff, the public GraphStream layout abstraction, geometry/correction, labels, immutable canvas state creation, real EDT state swap, and explicit public canvas paint. It does not claim direct measurement or control of:

- `ProjectionBatcher` debounce latency;
- `GraphUpdateCoordinator` internal stale-generation discard;
- `GraphCanvas` stale-state rejection; or
- private `GraphPainter` methods.

The accepted-batch timestamp anchors the measured component composition. It does not include coordinator queue latency. The final report does not present excluded coordinator or canvas behavior as measured by the ledger.

## Cleanup And Residual Risk

The post-fix diagnostic reports zero failure and discard counts in all 72 rows. Worker and bounded-operation executors are closed, Swing EDT operations complete within five seconds, and no graph layout worker thread survives cleanup. The final output directory contains only the four required artifacts.

The post-fix accepted-first-frame p95 is `126485138` ns, leaving `23514862` ns below the `150000000` ns ceiling; p99 is `144605526` ns below `300000000` ns. This is still a correctness-neutral, machine-specific performance risk: scheduler jitter, CPU frequency, JVM warm-up, font stack, or slower CI hardware can move a percentile. Rerun the strict diagnostic on target CI hardware before treating the margin as portable.
