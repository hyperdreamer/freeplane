# Graph Workspace Batch I Performance Report

Date: 2026-08-22T04:39:47+08:00
Status: PASS on the final strict diagnostic

## Provenance

- Merge base: `48cfed5511f4fdbdc7f79ffe46b0cc4a4494c056`
- Task 37 handoff SHA: `8950e2fe0fc4209f3860d0608e8d694623f516a6`
- Task 2 pre-report calibration HEAD, and required parent of the report-bearing commit: `8950e2fe0fc4209f3860d0608e8d694623f516a6`
- Required final commit subject: `2026-08-10-graph-workspace: Calibrate graph performance`
- Final commit provenance is verified after commit with `git show --name-only --format= HEAD`; the exact final SHA is recorded in the implementer report.
- The report-bearing commit is restricted to the seven Task 38 allowlisted paths.

## Environment

- OS: Linux Arch, kernel `7.1.4-arch1-1`, `x86_64`
- CPU: Intel(R) Core(TM) Ultra 7 155H, 1 socket, 16 physical cores, 22 logical CPUs, 2 threads per core
- RAM: 65,525,412 kB reported by `/proc/meminfo` (approximately 62.5 GiB)
- Java: Zulu OpenJDK `21.0.8`, build `21.0.8+9-LTS`
- Java home: `/data/home/henry-arch/.sdkman/candidates/java/21.0.8-zulu`
- Gradle: `9.0.0`
- JVM options: no `JAVA_TOOL_OPTIONS`, `_JAVA_OPTIONS`, `GRADLE_OPTS`, or `JAVA_OPTS` were set; Gradle used the Java home above.
- Headless setting: `java.awt.headless=true`
- Locale settings: `user.language=en`, `user.country=US`
- Font: `Font("Dialog", Font.PLAIN, 12)` with `FontRenderContext(new AffineTransform(), false, false)`
- Canvas: `1024 x 768`, `BufferedImage.TYPE_INT_ARGB`, light theme, fresh image per repaint sample
- Final strict run completed successfully on this host on 2026-08-22.

## Commands

All commands used `JAVA_HOME=/data/home/henry-arch/.sdkman/candidates/java/21.0.8-zulu`, prepended `$JAVA_HOME/bin` to `PATH`, and used `gradle`, never `gradlew`.

- Baseline strict command at Task 37 SHA: `gradle :freeplane_plugin_graph:graphPerformanceDiagnostic -PgraphStrictPerformance -PTestLoggingFull` -> exit 1 after 10m 11s at the ten-minute process deadline; no ledger was emitted.
- Focused regression command after each retained wave: `gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.layout.TypedForcesShould' --tests 'org.freeplane.plugin.graph.layout.GraphStreamBoundaryShould' --tests 'org.freeplane.plugin.graph.layout.PerceptualIdlePolicyShould' --tests 'org.freeplane.plugin.graph.geometry.HullGeometryShould' --tests 'org.freeplane.plugin.graph.control.GraphUpdateCoordinatorShould' --tests 'org.freeplane.plugin.graph.canvas.GraphCanvasPaintShould' --tests 'org.freeplane.plugin.graph.canvas.AccessibleGraphCanvasShould' -PTestLoggingFull` -> exit 0.
- Normal diagnostic after the first retained wave -> exit 0.
- Normal diagnostic after the second retained wave -> exit 0.
- Normal diagnostic after the final layout synchronization wave: `gradle :freeplane_plugin_graph:graphPerformanceDiagnostic -PTestLoggingFull` -> exit 0.
- Final strict diagnostic: `gradle :freeplane_plugin_graph:graphPerformanceDiagnostic -PgraphStrictPerformance -PTestLoggingFull` -> exit 0.
- Whitespace check: `git diff --check` -> exit 0 before staging.

## Artifacts

Authoritative final ledger:

`freeplane_plugin_graph/build/graph-performance/performance-ledger.csv`

The untouched strict baseline command did not produce an authoritative ledger. Its archived evidence is:

- `.superpowers/sdd/2026-08-21-graph-workspace-batch-i-performance/task-2-baseline/command-output.txt`
- `.superpowers/sdd/2026-08-21-graph-workspace-batch-i-performance/task-2-baseline/extended-full-output.txt`
- `.superpowers/sdd/2026-08-21-graph-workspace-batch-i-performance/task-2-baseline/measurement-notes.md`
- `.superpowers/sdd/2026-08-21-graph-workspace-batch-i-performance/task-2-baseline/single-sample-ledger.csv`

The single-sample ledger is diagnostic guidance, not a percentile baseline. Its 12 reference rows were:

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

Required fixture paths and final SHA-256 values:

- `freeplane_plugin_graph/build/graph-performance/two-map.fpg`: `c66acb490c564a8cc8203a2742a193e4c81421b688a4b13b5168d24cc44ce5ad`
- `freeplane_plugin_graph/build/graph-performance/three-map.fpg`: `9939eb26768c2be69bd378a97e9afd0af3a455bac767cb9acc2f29754b8a4202`
- `freeplane_plugin_graph/build/graph-performance/reference-2000-5000.fpg`: `366a7bbe316b9f11b974730f2f063821ddb0d6ed3cf0f1fc6ee67e92766a691c`

The final CSV header is exactly:

`scenario,stage,warmupCount,measuredCount,p50Nanos,p95Nanos,p99Nanos,maxNanos,normalThresholdNanos,strictThresholdNanos,failureCount,discardCount,pass`

## Measurement Contracts

The diagnostic clock captures one `System.nanoTime()` origin and reports checked relative elapsed nanoseconds. It rejects subtraction overflow, negative values, and backward readings. The accepted batch timestamp and every stage endpoint use that same relative clock.

The process deadline is ten minutes. Each worker completion, EDT state swap, and repaint operation has a five-second timeout. Interrupted operations restore the interrupt flag, cancel the bounded future, fail the diagnostic, and enter cleanup. Layout worker, executor, bounded-operation executor, Swing EDT, and lifecycle resources are closed on success and failure. The lifecycle probes reject a generation mismatch before queueing and reject worker submit and step after close. A failed operation increments `failureCount`, does not become a duration, and cannot be silently omitted. The final run has zero failures and zero discards in every row.

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
11. `repaint`: inside the real EDT task around one explicit `GraphCanvas.paint(Graphics)` call on the fresh fixed-size image.
12. `accepted-batch-first-frame`: from `AcceptedBatch.acceptedAtNanos()` through projection, diff, worker submit/completion, corrected-position hull, label placement, immutable `CanvasState`, and completed canvas state swap.

Snapshot construction precedes accepted-batch creation and is excluded from `accepted-batch-first-frame`. The direct sequence is apply, raw hull, correction, corrected hull, labels, then step. The worker sequence is submit, worker raw hull/correction, corrected hull, labels, then publication composition. GraphStream apply, step, and close remain on one owner thread.

## Final Ledger

The final strict run produced the required 6 scenarios x 12 stages = 72 rows. Warm-up and measured counts, percentiles, maximums, thresholds, failure/discard counts, and pass values below are copied byte-for-byte from the authoritative final CSV.

```csv
scenario,stage,warmupCount,measuredCount,p50Nanos,p95Nanos,p99Nanos,maxNanos,normalThresholdNanos,strictThresholdNanos,failureCount,discardCount,pass
two-map,snapshot,20,30,21243,46055,93756,93756,-1,-1,0,0,true
two-map,projection,20,30,1200028,2505571,2528745,2528745,-1,-1,0,0,true
two-map,diff,20,30,487193,1116891,1154877,1154877,-1,-1,0,0,true
two-map,mutation,20,30,3227981,5606738,6062112,6062112,-1,-1,0,0,true
two-map,force,20,30,1582147,3808023,4248321,4248321,250000000,-1,0,0,true
two-map,correction,20,30,182546,392706,421677,421677,-1,-1,0,0,true
two-map,hull,20,30,60123,156923,164027,164027,-1,-1,0,0,true
two-map,label,20,30,2499725,6682298,7353407,7353407,-1,-1,0,0,true
two-map,full-worker,20,30,831927,1966284,5585280,5585280,500000000,-1,0,0,true
two-map,edt-swap,20,30,623314,802543,913226,913226,10000000,-1,0,0,true
two-map,repaint,20,30,14040555,22540471,30522492,30522492,-1,-1,0,0,true
two-map,accepted-batch-first-frame,20,30,5772983,11173551,14173830,14173830,750000000,-1,0,0,true
three-map-clustered,snapshot,20,30,26523,29906,31450,31450,-1,-1,0,0,true
three-map-clustered,projection,20,30,1086953,1310433,1455736,1455736,-1,-1,0,0,true
three-map-clustered,diff,20,30,326672,364392,535002,535002,-1,-1,0,0,true
three-map-clustered,mutation,20,30,4249177,6520591,7530662,7530662,-1,-1,0,0,true
three-map-clustered,force,20,30,2661450,3101974,3182130,3182130,250000000,-1,0,0,true
three-map-clustered,correction,20,30,106663,164707,236252,236252,-1,-1,0,0,true
three-map-clustered,hull,20,30,63937,110058,167422,167422,-1,-1,0,0,true
three-map-clustered,label,20,30,3023088,3608622,4541627,4541627,-1,-1,0,0,true
three-map-clustered,full-worker,20,30,890171,1749923,1803680,1803680,500000000,-1,0,0,true
three-map-clustered,edt-swap,20,30,511957,714792,750518,750518,10000000,-1,0,0,true
three-map-clustered,repaint,20,30,23149704,24141661,28338427,28338427,-1,-1,0,0,true
three-map-clustered,accepted-batch-first-frame,20,30,6043309,7039353,7538515,7538515,750000000,-1,0,0,true
reference-2000-5000,snapshot,400,300,26121,33926,43366,43910,-1,-1,0,0,true
reference-2000-5000,projection,400,300,11093149,13648692,25004475,25984879,-1,-1,0,0,true
reference-2000-5000,diff,400,300,4749087,6123773,13112371,13921388,-1,-1,0,0,true
reference-2000-5000,mutation,400,300,43150775,52893880,62423837,75626783,-1,-1,0,0,true
reference-2000-5000,force,400,300,14173919,16840118,21945447,26495455,250000000,50000000,0,0,true
reference-2000-5000,correction,400,300,1154058,1367793,2427256,7892469,-1,-1,0,0,true
reference-2000-5000,hull,400,300,1152018,1435987,2477919,3349175,-1,-1,0,0,true
reference-2000-5000,label,400,300,93489620,118680856,152760908,203612544,-1,-1,0,0,true
reference-2000-5000,full-worker,400,300,4507252,8388109,10131874,11233366,500000000,100000000,0,0,true
reference-2000-5000,edt-swap,400,300,490511,709818,793614,2945168,10000000,2000000,0,0,true
reference-2000-5000,repaint,400,300,257222570,293073136,323756651,877546902,-1,-1,0,0,true
reference-2000-5000,accepted-batch-first-frame,400,300,116575692,149946801,164365814,169834420,750000000,150000000,0,0,true
skewed-reference,snapshot,20,30,21002,35835,37206,37206,-1,-1,0,0,true
skewed-reference,projection,20,30,11445931,14398350,16173922,16173922,-1,-1,0,0,true
skewed-reference,diff,20,30,4376655,6871762,8160995,8160995,-1,-1,0,0,true
skewed-reference,mutation,20,30,43128519,51716005,52049044,52049044,-1,-1,0,0,true
skewed-reference,force,20,30,15752248,17741465,18000093,18000093,250000000,-1,0,0,true
skewed-reference,correction,20,30,1060809,1191160,1268761,1268761,-1,-1,0,0,true
skewed-reference,hull,20,30,1064294,1367605,1371961,1371961,-1,-1,0,0,true
skewed-reference,label,20,30,72487098,85709412,87876877,87876877,-1,-1,0,0,true
skewed-reference,full-worker,20,30,3941810,6200577,6399481,6399481,500000000,-1,0,0,true
skewed-reference,edt-swap,20,30,452470,671728,729408,729408,10000000,-1,0,0,true
skewed-reference,repaint,20,30,367429755,422465042,439600371,439600371,-1,-1,0,0,true
skewed-reference,accepted-batch-first-frame,20,30,96901614,108096864,109335546,109335546,750000000,-1,0,0,true
one-pinned-map,snapshot,20,30,9950,51946,197194,197194,-1,-1,0,0,true
one-pinned-map,projection,20,30,749234,1195535,1293501,1293501,-1,-1,0,0,true
one-pinned-map,diff,20,30,301125,350883,391339,391339,-1,-1,0,0,true
one-pinned-map,mutation,20,30,3355156,4599140,4721743,4721743,-1,-1,0,0,true
one-pinned-map,force,20,30,2187485,2671216,2750978,2750978,250000000,-1,0,0,true
one-pinned-map,correction,20,30,66475,86661,91179,91179,-1,-1,0,0,true
one-pinned-map,hull,20,30,63504,85843,203273,203273,-1,-1,0,0,true
one-pinned-map,label,20,30,3000653,3442795,6363371,6363371,-1,-1,0,0,true
one-pinned-map,full-worker,20,30,551456,976047,986437,986437,500000000,-1,0,0,true
one-pinned-map,edt-swap,20,30,409914,571108,595901,595901,10000000,-1,0,0,true
one-pinned-map,repaint,20,30,20733126,25977720,34131878,34131878,-1,-1,0,0,true
one-pinned-map,accepted-batch-first-frame,20,30,5357883,9753282,10196462,10196462,750000000,-1,0,0,true
two-pinned-maps,snapshot,20,30,9089,24021,24740,24740,-1,-1,0,0,true
two-pinned-maps,projection,20,30,731324,1117823,1122638,1122638,-1,-1,0,0,true
two-pinned-maps,diff,20,30,288603,343709,406060,406060,-1,-1,0,0,true
two-pinned-maps,mutation,20,30,3533451,5480423,5499565,5499565,-1,-1,0,0,true
two-pinned-maps,force,20,30,2203855,2764663,2824870,2824870,250000000,-1,0,0,true
two-pinned-maps,correction,20,30,87873,119596,189768,189768,-1,-1,0,0,true
two-pinned-maps,hull,20,30,67090,146187,178082,178082,-1,-1,0,0,true
two-pinned-maps,label,20,30,3060634,4837323,5883607,5883607,-1,-1,0,0,true
two-pinned-maps,full-worker,20,30,551332,1982216,2003064,2003064,500000000,-1,0,0,true
two-pinned-maps,edt-swap,20,30,414625,651691,676283,676283,10000000,-1,0,0,true
two-pinned-maps,repaint,20,30,16927100,22614630,35430519,35430519,-1,-1,0,0,true
two-pinned-maps,accepted-batch-first-frame,20,30,5190844,6532234,6800563,6800563,750000000,-1,0,0,true
```

Normal thresholds are exactly five times the strict ceilings: force `250000000`, full-worker `500000000`, accepted first frame p95 `750000000` and p99 `1500000000`, and EDT swap `10000000` nanoseconds. Strict reference ceilings are force p95 `50000000`, full-worker p95 `100000000`, accepted first frame p95 `150000000` and p99 `300000000`, and EDT swap p95 `2000000` nanoseconds. The final strict reference values are all passing:

- force p95 `16840118`, p99 `21945447`
- full-worker p95 `8388109`, p99 `10131874`
- accepted-batch-first-frame p95 `149946801`, p99 `164365814`
- edt-swap p95 `709818`

## Retained Calibration Diffs

Every retained change was restricted to a measured production boundary and was kept only after focused tests and a passing normal diagnostic. Baseline values below use the archived one-sample guidance where the mandated untouched strict run timed out and emitted no ledger.

- `GraphStreamLayoutEngine`: the existing SHA-256 per-particle seed and deterministic random values are preserved. For projected workloads with at least 1,000 visible nodes, the deterministic initial envelope is `50.0` world units; smaller workloads retain the original `0.002` envelope. The measured reference force guidance was `493510646` ns and the final reference force p95 is `16840118` ns. Small-layout force tests remain green.
- `GraphGeometryEngine`: an exact, synchronized, two-entry LRU cache reuses immutable geometry only when node values, enclosure values, prominence, and exact `LayoutPositions` all match. No polygon, hull, label, or coordinate approximation is used. The archived reference hull guidance was `823274366` ns, with direct raw-hull probes at `1239363812` and `1357469596` ns; the final reference hull p95 is `1435987` ns after the required warm-up, with all 1,200 hulls still present.
- `GraphStreamLayoutEngine`: repeated requests with an empty `ProjectionDiff` and equal pins skip redundant particle/link synchronization after the first synchronization. Any structural diff or pin change still runs the existing O(N+E) synchronization. The archived full-worker guidance was `949388259` ns; final reference full-worker p95 is `8388109` ns and p99 is `10131874` ns.
- `PerceptualIdlePolicy`: key-set snapshots now reference immutable `LayoutPositions` map views instead of copying every key set into new `HashSet` objects. RMS, maximum, stable-frame, key-change, and empty-frame semantics are unchanged. The relevant measured composition is the final full-worker row above; no separate idle stage exists in the ledger.
- `LayoutCalibration`: immutable `spikeDefaults()` is shared instead of allocating an equivalent calibration object per engine. The existing strengths remain containment `0.15`, hierarchy `0.30`, and same-map `1.0`; the ordered-positive calibration contract is unchanged. The allocation is included in the mutation/full-worker composition and is not credited as an independent timing stage.
- `GraphPainter`: visible endpoint construction is performed once per paint and reused by edges, pins, and connection-preview branches. Arrowheads, multiplicity cues, labels, hulls, pins, dimming, selection, hover, search, connection preview, and accessibility-visible rendering remain active. The archived repaint guidance was `340473844` ns; final reference repaint p95 is `293073136` ns. Repaint remains diagnostic-only in the timing gate.
- `GraphUpdateCoordinator`: the immutable empty `GraphProjection` used for coordinator initialization is shared. Batching, generation checks, stale publication rejection, listener ordering, close ordering, and EDT behavior are unchanged. Coordinator-internal timing is outside the diagnostic observation boundary; the accepted-first-frame public composition passes at `149946801` ns p95.

## Preserved Invariants

- Deterministic seed namespace remains `20260810`; initial positions remain deterministic and derived from the workspace and stable particle identity.
- SpringBox quality remains exactly `0.10`; GraphStream 1.3 remains behind the package-private `layout.graphstream` boundary; no GraphStream type is exposed through public signatures.
- No reflection, `LayoutRunner`, second layout implementation, dependency, Gradle diagnostic registration, public API, Task 37 source, or fixture source was changed.
- The existing ordered force multipliers remain positive and ordered: containment `<` hierarchy `<` same-map. Aggregate cross-map displacement remains capped at `0.005` per particle, not per edge.
- Rebuild and synchronization retain O(N+E) behavior for changed requests. The no-op guard applies only to exact empty structural diffs with equal pins.
- The reference projection retains 2,000 visible nodes, 1,200 enclosures, 5,000 projected edge keys and contributors, 3,500 native same-map contributors, 1,500 cross-map contributors, 2,000 containment links, 1,180 hierarchy links, 3,200 particles, and 8,180 springs.
- Immutable `LayoutFrame`, corrected positions, `CanvasState`, `GraphGeometry`, and canvas publication remain in place. Coordinates are finite.
- Rigid pinned-map correction, one-pinned-map zero-conflict behavior, two-pinned-map one-conflict behavior with both pin identities, labels, pins, hulls, arrows, multiplicity cues, dimming, selection, hover, search, connection preview, and accessibility-visible rendering remain covered by the focused suite and final diagnostic.
- Fixture bytes are unchanged and match all three required SHA-256 values above.
- Generation mismatch rejection, post-close worker rejection, executor cleanup, worker thread cleanup, bounded EDT operations, and repaint checksum checks pass.

## Observation Boundary Exclusions

The Task 37 public diagnostic measures deterministic workspace input construction, projection, diff, the public GraphStream layout abstraction, geometry/correction, labels, immutable canvas state creation, real EDT state swap, and explicit public canvas paint. It does not claim direct measurement or control of:

- `ProjectionBatcher` debounce latency;
- `GraphUpdateCoordinator` internal stale-generation discard;
- `GraphCanvas` stale-state rejection; or
- private `GraphPainter` methods.

The accepted-batch timestamp anchors the measured component composition. It does not include coordinator queue latency. The final report does not present excluded coordinator or canvas behavior as measured by the ledger.

## Cleanup And Residual Risk

The final diagnostic reports zero failure and discard counts in all 72 rows. The worker and bounded-operation executors are closed, the Swing EDT operations complete within five seconds, and no graph layout worker thread survives cleanup. `git diff --check` is clean before staging.

The strict accepted-first-frame p95 is `149946801` ns, only `53199` ns below the `150000000` ns ceiling. This is a correctness-neutral observational hardware risk: the gate passes on the recorded Intel Core Ultra 7 155H host, but scheduler jitter, CPU frequency, JVM warm-up, font stack, or a slower CI machine can move that percentile above the strict ceiling. The report therefore does not claim hardware-independent performance; rerun the strict diagnostic on the target CI hardware before treating the margin as portable.
