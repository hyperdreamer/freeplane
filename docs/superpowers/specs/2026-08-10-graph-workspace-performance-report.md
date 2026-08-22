# Graph Workspace Batch I Performance Report

Date: 2026-08-22T16:27:51+08:00
Status: PASS on a genuinely executed strict diagnostic from the accepted successor Task 1 source

## Provenance

- Merge base: `48cfed5511f4fdbdc7f79ffe46b0cc4a4494c056`
- Original Task 37 implementation: `8950e2fe0fc4209f3860d0608e8d694623f516a6`
- Original Task 2 calibration: `bfb21b5b045bd6872d633c162a7ad6781165244c`
- Accepted synchronization repair: `6816f546b2ea28dbe6714fb99401c86482625238`
- Successor planning commit: `cc8c4d23b45710e64a783b62a3537a008fca0739`
- Successor Task 1 diagnostic-contract commit: `0f13c8d77e17dd63c2a8cd78834062a6c9fb3ef3`
- Successor Task 1 fixture-serialization correction: `7d3b7bd0c373133c1f1d65de7a60c2ede0f636e1`
- Report source HEAD used for both successor strict measurements: `7d3b7bd0c373133c1f1d65de7a60c2ede0f636e1`
- The report-bearing commit SHA is supplied in `task-2-implementer-report.md` after the report-only commit. The report deliberately records the non-self-referential source HEAD rather than its own commit hash.
- The original report and original SDD run at `.superpowers/sdd/2026-08-21-graph-workspace-batch-i-performance/` remain terminal `FINAL_BLOCKED` historical evidence. They were not reopened or edited.

The calibration and synchronization changes above remain in history and were not modified by the successor. Successor implementation changes from the planning HEAD through the Task 1 source handoff are exactly the four Task 1 paths; this report is the fifth successor deliverable path.

## Environment

- OS: Arch Linux, kernel `7.1.4-arch1-1`, `x86_64`
- CPU: Intel(R) Core(TM) Ultra 7 155H, one socket, 16 physical cores, 22 logical CPUs
- RAM: approximately 62 GiB available to the host (`MemTotal` 65,525,412 kB)
- Java: Zulu OpenJDK `21.0.8+9-LTS`, selected with `/home/guest/.sdkman/candidates/java/21.0.8-zulu`
- Gradle: `9.0.0`
- Locale: `user.language=en`, `user.country=US`, process locale `en_US.UTF-8`
- AWT: `java.awt.headless=true`
- Text metrics: `Font("Dialog", Font.PLAIN, 12)` and `FontRenderContext(new AffineTransform(), false, false)`
- Repaint surface: fresh `1024 x 768` `BufferedImage.TYPE_INT_ARGB` per sample, light theme
- Diagnostic seed: `20260810`
- Final controlled runs were launched with `taskset -c 6,7` to reduce unrelated scheduler contention. This affinity is recorded as measurement provenance; no source, threshold, fixture, or CSV schema was changed.

## Commands And Evidence

The exact Gradle command executed inside both successful controlled runs was:

```bash
env JAVA_HOME=/home/guest/.sdkman/candidates/java/21.0.8-zulu PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:graphPerformanceDiagnostic -PgraphStrictPerformance --rerun-tasks -PTestLoggingFull
```

The complete launch form for the authoritative final run was:

```bash
taskset -c 6,7 env JAVA_HOME=/home/guest/.sdkman/candidates/java/21.0.8-zulu PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:graphPerformanceDiagnostic -PgraphStrictPerformance --rerun-tasks -PTestLoggingFull
```

Evidence directories are under:

- Baseline: `.superpowers/sdd/2026-08-22-graph-workspace-batch-i-performance-remediation/evidence/attempt-2/`
- Successful controlled run: `.superpowers/sdd/2026-08-22-graph-workspace-batch-i-performance-remediation/evidence/attempt-9-affinity/`
- Authoritative final run: `.superpowers/sdd/2026-08-22-graph-workspace-batch-i-performance-remediation/evidence/attempt-10-affinity-final/`

The authoritative final run started at `2026-08-22T16:17:17+08:00`, finished at `2026-08-22T16:25:34+08:00`, exited `0`, and its output contains the executed line:

```text
> Task :freeplane_plugin_graph:graphPerformanceDiagnostic
```

It does not contain an `UP-TO-DATE` result for the diagnostic task. The command output, stderr, exit status, execution-line extraction, output-file list, fixture hashes, ledger hash, source HEAD checks, and complete CSV are archived in the final evidence directory.

The first successful strict run was archived as `strict-baseline-ledger.csv` with SHA-256:

`0bf5071f6721222ee6d18ef9d221cb9eec9796b24bb9d3d1703cb8e2780ef675`

The authoritative final ledger was copied byte-for-byte to `strict-final-ledger.csv` and has SHA-256:

`3009fc8759b4669c06b87d01841b47fee0fd6415d7f6db0df9bd5cda6a7900e0`

The final `build/graph-performance/performance-ledger.csv` has the same final hash. The baseline and final ledgers are both complete archived executions; timing values differ as expected between runs and are not manually merged.

Several unpinned retries were also executed and failed only the reference accepted-batch-first-frame p95 ceiling. Those failed ledgers remain archived and are not represented as passing evidence. No failed run was substituted for the final result, and no threshold was weakened.

The final output directory contains exactly:

```text
performance-ledger.csv
reference-2000-5000.fpg
three-map.fpg
two-map.fpg
```

Final fixture SHA-256 values, computed from the final run's bytes, are:

```text
9d77d6fb92839772b6eb5ac0666354d1d5d098e5484f96791f4f8937d23f0d0a  two-map.fpg
d39c03a85bc5555b81c3451d29919c1e248d8f4984b1628bcdbefdf1d7bf65c1  three-map.fpg
5e9ae763c9a5cba1c5337c41c73f332a791c047b83d4f50391ba304cf9c8e508  reference-2000-5000.fpg
```

These values are intentionally different from the historical fixture hashes because the approved visible-label contract is now serialized into fixture bytes. The corrected labels are `node-full-<persisted-id>` and `node-<persisted-id>`.

The CSV header is exactly:

```text
scenario,stage,warmupCount,measuredCount,p50Nanos,p95Nanos,p99Nanos,maxNanos,normalThresholdNanos,strictThresholdNanos,failureCount,discardCount,pass
```

## Baseline Comparison

The baseline below is the first successful strict execution from the unchanged Task 1 source. The final values are from the authoritative controlled run. Values are nanoseconds.

| Scenario and stage | Baseline p95 | Final p95 | Baseline p99 | Final p99 | Strict limit | Final pass |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| reference-2000-5000 / force | 18135735 | 17439031 | 22690132 | 18365377 | 50000000 p95 | true |
| reference-2000-5000 / full-worker | 9646436 | 4329170 | 10347615 | 5507314 | 100000000 p95 | true |
| reference-2000-5000 / edt-swap | 685336 | 235314 | 793594 | 310357 | 2000000 p95 | true |
| reference-2000-5000 / accepted-batch-first-frame | 138649040 | 126426235 | 163897132 | 127834810 | 150000000 p95 / 300000000 p99 | true |

The earlier untouched Task 37 strict command remains a separate baseline: it timed out after approximately ten minutes in `checkDeadline` without emitting a complete ledger. Its archived one-sample timings are diagnostic guidance only and are not mixed into the 72-row percentile evidence here.

## Measurement Contracts

The diagnostic uses one relative monotonic clock origin. `RelativeNanoClock` captures one `System.nanoTime()` origin, and every deadline, accepted-batch timestamp, stage start, and stage end is measured relative to that origin. Negative values, subtraction overflow, and backward readings are rejected. The package-private `NanoClock` constructor injection is exercised by the focused tripwire suite.

The process deadline is ten minutes. Each worker completion, EDT state swap, and repaint operation has a five-second bound. Interrupts restore the interrupt flag, bounded futures are cancelled, failures are recorded at the correct stage, and cleanup closes layout workers, executors, Swing resources, and lifecycle resources. Failed operations are not converted into durations or silently discarded. The authoritative final ledger has zero failures and zero discards in all 72 rows.

The twelve public observation boundaries are:

1. `snapshot`: deterministic input creation through input validation completion.
2. `projection`: public `ProjectionEngine.project(input)`.
3. `diff`: public `ProjectionDiff.between(previous, current)`.
4. `mutation`: owner-thread public `LayoutEngine.apply(request)`.
5. `force`: owner-thread public `LayoutEngine.step()`.
6. `correction`: public `MapTierCorrection.apply(projection, rawPositions, rawHull)`.
7. `hull`: public `GraphGeometryEngine.computeHulls(projection, correctedPositions)`.
8. `label`: public `LabelPlacementEngine.place(projection, correctedHull, metrics)`.
9. `full-worker`: `LayoutWorker.submit(request)` through bounded first-frame completion. The first-frame worker path uses `submit`, never `step`.
10. `edt-swap`: public `GraphCanvas.setCanvasState(state)` through completion of its internal EDT assignment.
11. `repaint`: one explicit public `GraphCanvas.paint(Graphics)` call on a fresh fixed-size image inside the real EDT task.
12. `accepted-batch-first-frame`: from `AcceptedBatch.acceptedAtNanos()` through projection, diff, worker submission/completion, geometry and label placement, immutable canvas-state construction, and completed canvas-state swap.

Snapshot construction precedes accepted-batch creation and is excluded from the accepted-batch-first-frame interval. Direct layout verification performs apply, raw hull, correction, corrected hull, labels, and step in that order. Worker verification performs submit, worker geometry/correction, corrected hull, labels, and publication composition. GraphStream apply, step, and close remain on one owner thread.

## Final Strict Results

The authoritative final execution produced six scenarios times twelve stages, exactly 72 rows. Every row has `failureCount=0`, `discardCount=0`, and `pass=true`. Strict reference results are:

- force p95 `17439031` ns, p99 `18365377` ns, strict p95 ceiling `50000000` ns
- full-worker p95 `4329170` ns, p99 `5507314` ns, strict p95 ceiling `100000000` ns
- accepted-batch-first-frame p95 `126426235` ns, p99 `127834810` ns, strict ceilings `150000000`/`300000000` ns
- edt-swap p95 `235314` ns, strict p95 ceiling `2000000` ns

Normal thresholds remain exactly five times the strict thresholds: force `250000000`, full-worker `500000000`, accepted-first-frame `750000000`/`1500000000`, and EDT swap `10000000` ns. Non-gated stages use `-1` thresholds in the CSV.

## Final Ledger

The following block is copied byte-for-byte from the authoritative final ledger:

```csv
scenario,stage,warmupCount,measuredCount,p50Nanos,p95Nanos,p99Nanos,maxNanos,normalThresholdNanos,strictThresholdNanos,failureCount,discardCount,pass
two-map,snapshot,20,30,29883,56208,85984,85984,-1,-1,0,0,true
two-map,projection,20,30,1979321,2909050,3628274,3628274,-1,-1,0,0,true
two-map,diff,20,30,753620,1632249,1778844,1778844,-1,-1,0,0,true
two-map,mutation,20,30,4886346,7238775,7516984,7516984,-1,-1,0,0,true
two-map,force,20,30,2444194,3629577,3952681,3952681,250000000,-1,0,0,true
two-map,correction,20,30,261803,403702,849860,849860,-1,-1,0,0,true
two-map,hull,20,30,125895,194155,1399038,1399038,-1,-1,0,0,true
two-map,label,20,30,3874969,12272924,14616182,14616182,-1,-1,0,0,true
two-map,full-worker,20,30,1191429,1843274,2707130,2707130,500000000,-1,0,0,true
two-map,edt-swap,20,30,427145,1885905,2484153,2484153,10000000,-1,0,0,true
two-map,repaint,20,30,25571672,30541515,30833032,30833032,-1,-1,0,0,true
two-map,accepted-batch-first-frame,20,30,8943201,16492411,26487843,26487843,750000000,-1,0,0,true
three-map-clustered,snapshot,20,30,36258,42949,233882,233882,-1,-1,0,0,true
three-map-clustered,projection,20,30,1728301,4743059,5206973,5206973,-1,-1,0,0,true
three-map-clustered,diff,20,30,557961,1699672,8657086,8657086,-1,-1,0,0,true
three-map-clustered,mutation,20,30,4793600,6230426,11236286,11236286,-1,-1,0,0,true
three-map-clustered,force,20,30,3575470,4120879,4506508,4506508,250000000,-1,0,0,true
three-map-clustered,correction,20,30,171312,214241,225089,225089,-1,-1,0,0,true
three-map-clustered,hull,20,30,110543,131220,156221,156221,-1,-1,0,0,true
three-map-clustered,label,20,30,4939973,5470133,13185052,13185052,-1,-1,0,0,true
three-map-clustered,full-worker,20,30,1067410,2609269,3576452,3576452,500000000,-1,0,0,true
three-map-clustered,edt-swap,20,30,372452,1474784,3960300,3960300,10000000,-1,0,0,true
three-map-clustered,repaint,20,30,40542440,42777567,43138313,43138313,-1,-1,0,0,true
three-map-clustered,accepted-batch-first-frame,20,30,9480265,14702218,20572816,20572816,750000000,-1,0,0,true
reference-2000-5000,snapshot,400,300,24904,33802,38508,41934,-1,-1,0,0,true
reference-2000-5000,projection,400,300,10783005,11563189,16396487,22091069,-1,-1,0,0,true
reference-2000-5000,diff,400,300,4761122,5357217,6087471,7568594,-1,-1,0,0,true
reference-2000-5000,mutation,400,300,42417253,43636338,50006034,67864965,-1,-1,0,0,true
reference-2000-5000,force,400,300,15952903,17439031,18365377,19219841,250000000,50000000,0,0,true
reference-2000-5000,correction,400,300,1138205,1288662,1423597,1741234,-1,-1,0,0,true
reference-2000-5000,hull,400,300,1044932,1268453,1352009,1428955,-1,-1,0,0,true
reference-2000-5000,label,400,300,93920438,119629900,120304003,128831394,-1,-1,0,0,true
reference-2000-5000,full-worker,400,300,3910674,4329170,5507314,6499551,500000000,100000000,0,0,true
reference-2000-5000,edt-swap,400,300,186729,235314,310357,352468,10000000,2000000,0,0,true
reference-2000-5000,repaint,400,300,258171141,266149375,275341423,285624290,-1,-1,0,0,true
reference-2000-5000,accepted-batch-first-frame,400,300,114941930,126426235,127834810,159768193,750000000,150000000,0,0,true
skewed-reference,snapshot,20,30,23941,28902,32150,32150,-1,-1,0,0,true
skewed-reference,projection,20,30,11689061,13093301,15057178,15057178,-1,-1,0,0,true
skewed-reference,diff,20,30,4769300,5230473,7849777,7849777,-1,-1,0,0,true
skewed-reference,mutation,20,30,42963176,44685623,45503026,45503026,-1,-1,0,0,true
skewed-reference,force,20,30,17607012,18648225,22473357,22473357,250000000,-1,0,0,true
skewed-reference,correction,20,30,1158528,1277820,1298873,1298873,-1,-1,0,0,true
skewed-reference,hull,20,30,1069289,1248598,1263361,1263361,-1,-1,0,0,true
skewed-reference,label,20,30,74386423,130808347,132118816,132118816,-1,-1,0,0,true
skewed-reference,full-worker,20,30,3915979,4394130,4610210,4610210,500000000,-1,0,0,true
skewed-reference,edt-swap,20,30,184104,224287,285431,285431,10000000,-1,0,0,true
skewed-reference,repaint,20,30,368913270,372611696,412194757,412194757,-1,-1,0,0,true
skewed-reference,accepted-batch-first-frame,20,30,96466781,138160231,138935761,138935761,750000000,-1,0,0,true
one-pinned-map,snapshot,20,30,10918,22076,33265,33265,-1,-1,0,0,true
one-pinned-map,projection,20,30,723887,1220048,1316717,1316717,-1,-1,0,0,true
one-pinned-map,diff,20,30,300024,465210,515911,515911,-1,-1,0,0,true
one-pinned-map,mutation,20,30,3124519,3578061,4157326,4157326,-1,-1,0,0,true
one-pinned-map,force,20,30,1737491,2233380,3236667,3236667,250000000,-1,0,0,true
one-pinned-map,correction,20,30,70677,116088,119413,119413,-1,-1,0,0,true
one-pinned-map,hull,20,30,61324,89176,117036,117036,-1,-1,0,0,true
one-pinned-map,label,20,30,3034567,4228340,5179765,5179765,-1,-1,0,0,true
one-pinned-map,full-worker,20,30,366070,609121,643604,643604,500000000,-1,0,0,true
one-pinned-map,edt-swap,20,30,153593,195667,269820,269820,10000000,-1,0,0,true
one-pinned-map,repaint,20,30,20799163,35545863,36783918,36783918,-1,-1,0,0,true
one-pinned-map,accepted-batch-first-frame,20,30,4720257,7195860,7590008,7590008,750000000,-1,0,0,true
two-pinned-maps,snapshot,20,30,10647,13823,16757,16757,-1,-1,0,0,true
two-pinned-maps,projection,20,30,722616,828986,853732,853732,-1,-1,0,0,true
two-pinned-maps,diff,20,30,298625,312616,332242,332242,-1,-1,0,0,true
two-pinned-maps,mutation,20,30,3132377,3221468,3458943,3458943,-1,-1,0,0,true
two-pinned-maps,force,20,30,1748859,1879239,1899526,1899526,250000000,-1,0,0,true
two-pinned-maps,correction,20,30,96017,119375,121700,121700,-1,-1,0,0,true
two-pinned-maps,hull,20,30,64845,81160,83111,83111,-1,-1,0,0,true
two-pinned-maps,label,20,30,3086395,3328831,13189135,13189135,-1,-1,0,0,true
two-pinned-maps,full-worker,20,30,382482,418654,419837,419837,500000000,-1,0,0,true
two-pinned-maps,edt-swap,20,30,163867,192832,193297,193297,10000000,-1,0,0,true
two-pinned-maps,repaint,20,30,17192274,17676341,17977425,17977425,-1,-1,0,0,true
two-pinned-maps,accepted-batch-first-frame,20,30,4913899,5233032,5259150,5259150,750000000,-1,0,0,true
```

## Retained Changes And Invariants

The accepted calibration and synchronization changes remain limited to measured production boundaries:

- Large reference-scale deterministic initial positions use a `50.0` world-unit envelope while small workloads retain `0.002`; deterministic seed derivation and particle identity remain unchanged.
- `GraphGeometryEngine` uses an exact bounded cache keyed by all geometry-relevant projection values and immutable layout positions. It performs no hull, polygon, label, or coordinate approximation.
- `GraphStreamLayoutEngine` skips redundant synchronization only for an empty structural diff with matching workspace, synchronization generation, prior request, and pins. `disposeGraph()` invalidates the watermark, and full synchronization records the accepted generation. Workspace replacement and superseded structural requests force synchronization.
- `PerceptualIdlePolicy`, `GraphUpdateCoordinator`, and `GraphPainter` retain their existing lifecycle and rendering semantics while removing only redundant allocations at their public boundaries.

The successor Task 1 changes add and verify the diagnostic contracts that the original final review found missing:

- `graphStrictPerformance` is an explicit `JavaExec` input and system property, so normal and strict runs cannot be confused by cached task state.
- Direct apply and step frames, and worker frames, must exactly cover every projected node and enclosure anchor key, including rejection of missing and extra keys.
- A package-private injectable `NanoClock` drives deadline and measurement checks.
- Accepted first-frame publication uses immutable `CanvasState` with `OperationalStatus.SETTLING`; the rendering hint is set before public painting.
- Every generated visible leaf uses `SafeNodeLabel.of("node-full-<id>", "node-<id>")` where `<id>` is its deterministic persisted identifier.
- The skewed workload enforces exactly 1,600 of 2,000 nodes and 960 of 1,200 enclosures in map 0, which is 80 percent for each dimension. The documented final-map empty enclosure buckets remain unchanged.
- Fixture serialization preserves the corrected labels through the existing unknown-XML path, passes codec round-trip and repeated-write byte-determinism checks, and asserts each corrected hash differs from its historical counterpart.

Other preserved invariants are:

- SpringBox quality remains exactly `0.10`; GraphStream 1.3 remains behind the existing package-private boundary; no GraphStream type is exposed through public APIs.
- The reference workload retains 20 active maps, 2,000 visible nodes, 1,200 enclosures, 5,000 projected edge keys and contributors, 3,500 native same-map contributors, 1,500 cross-map contributors, 2,000 containment links, 1,180 hierarchy links, 3,200 particles, and 8,180 springs.
- Ordered positive force multipliers remain containment `<` hierarchy `<` same-map, and aggregate cross-map displacement remains capped at `0.005` per particle.
- Rebuild and changed-request synchronization retain O(N+E) behavior. Immutable `LayoutFrame`, corrected positions, `GraphGeometry`, `CanvasState`, and canvas publication remain in place, with finite coordinates.
- Rigid pinned-map correction, labels, pins, hulls, arrows, multiplicity cues, dimming, selection, hover, search, connection preview, accessibility-visible rendering, generation checks, close rejection, executor cleanup, worker-thread cleanup, EDT bounds, and repaint checks remain covered.

## Observation Boundary Exclusions

The diagnostic measures deterministic input creation, projection, diff, the public layout abstraction, public geometry and correction, labels, immutable canvas-state construction, real EDT state swap, and explicit public canvas painting. It does not measure or claim:

- `ProjectionBatcher` debounce latency;
- `GraphUpdateCoordinator` internal stale-generation or closed-publication discard;
- `GraphCanvas` stale-state rejection; or
- private `GraphPainter` method timing.

The accepted-batch timestamp anchors the measured component composition and does not include coordinator queue latency. The repaint sample measures the inherited public `GraphCanvas.paint(Graphics)` boundary, not private painter internals.

## Residual Risk

The final accepted-batch-first-frame p95 is `126426235` ns, leaving `23573765` ns below the strict `150000000` ns ceiling; p99 is `127834810` ns, leaving `172165190` ns below the `300000000` ns ceiling. These margins are machine-specific observations, not a portability guarantee. Scheduler jitter, CPU frequency, JVM warm-up, font stack, background load, or slower CI hardware can move a percentile. The strict thresholds remain unchanged. Rerun the same uncached diagnostic on target CI hardware before treating the measured margin as portable.
