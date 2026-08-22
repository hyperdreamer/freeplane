# Graph Workspace Batch I Performance Report

Date: 2026-08-22T17:18:12+08:00
Status: PASS on a genuinely executed strict diagnostic from the accepted successor Task 1 source with explicit option-state evidence

## Provenance

- Merge base: `48cfed5511f4fdbdc7f79ffe46b0cc4a4494c056`
- Original Task 37 implementation: `8950e2fe0fc4209f3860d0608e8d694623f516a6`
- Original Task 2 calibration: `bfb21b5b045bd6872d633c162a7ad6781165244c`
- Accepted synchronization repair: `6816f546b2ea28dbe6714fb99401c86482625238`
- Successor planning commit: `cc8c4d23b45710e64a783b62a3537a008fca0739`
- Successor Task 1 diagnostic-contract commit: `0f13c8d77e17dd63c2a8cd78834062a6c9fb3ef3`
- Successor Task 1 fixture-serialization correction: `7d3b7bd0c373133c1f1d65de7a60c2ede0f636e1`
- Report source HEAD used for the fresh baseline and authoritative final strict measurements: `7d3b7bd0c373133c1f1d65de7a60c2ede0f636e1`
- The report-only commit SHA is recorded in the successor task-2 fix report after the report-only commit. This report records the non-self-referential source HEAD rather than its own commit hash.
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
- Both fresh captures were launched with `taskset -c 6,7` to reduce unrelated scheduler contention. This affinity is recorded as measurement provenance; no source, threshold, fixture, or CSV schema was changed.
- The exact option state recorded for both captures is:

```text
JAVA_TOOL_OPTIONS=<unset>
_JAVA_OPTIONS=<unset>
GRADLE_OPTS=<unset>
JAVA_OPTS=<unset>
JAVA_HOME=/home/guest/.sdkman/candidates/java/21.0.8-zulu
PATH_PREFIX=/home/guest/.sdkman/candidates/java/21.0.8-zulu/bin
LANG=en_US.UTF-8
```

The baseline values are freshly captured, not reconstructed from the earlier run. The complete snapshots are in `evidence/attempt-11-baseline-options/option-state.txt` and `evidence/attempt-12-affinity-final-options/option-state.txt`; each also records the full `PATH`, `TMPDIR`, Java version, Gradle version, and affinity.

## Commands And Evidence

The fresh baseline and authoritative final runs used this explicit command form from a detached checkout at source HEAD `7d3b7bd0c373133c1f1d65de7a60c2ede0f636e1`:

```bash
taskset -c 6,7 env -u JAVA_TOOL_OPTIONS -u _JAVA_OPTIONS -u GRADLE_OPTS -u JAVA_OPTS JAVA_HOME=/home/guest/.sdkman/candidates/java/21.0.8-zulu PATH="$JAVA_HOME/bin:$PATH" LANG=en_US.UTF-8 gradle :freeplane_plugin_graph:graphPerformanceDiagnostic -PgraphStrictPerformance --rerun-tasks -PTestLoggingFull
```

The captured command files contain the expanded `PATH` and run-specific `TMPDIR` used by each process.

Evidence directories are under:

- Fresh baseline: `.superpowers/sdd/2026-08-22-graph-workspace-batch-i-performance-remediation/evidence/attempt-11-baseline-options/`
- Authoritative final: `.superpowers/sdd/2026-08-22-graph-workspace-batch-i-performance-remediation/evidence/attempt-12-affinity-final-options/`
- Required byte-identical final-ledger mirror: `.superpowers/sdd/2026-08-22-graph-workspace-batch-i-performance-remediation/evidence/attempt-10-affinity-final/strict-final-ledger.csv`
- Run-root final archive: `.superpowers/sdd/2026-08-22-graph-workspace-batch-i-performance-remediation/strict-final-ledger.csv`
- Complete failed-retry index: `.superpowers/sdd/2026-08-22-graph-workspace-batch-i-performance-remediation/evidence/failed-retry-index.csv`

The fresh baseline started at `2026-08-22T16:59:56+08:00`, finished at `2026-08-22T17:08:42+08:00`, exited `0`, and its output contains `> Task :freeplane_plugin_graph:graphPerformanceDiagnostic`. The authoritative final started at `2026-08-22T17:09:20+08:00`, finished at `2026-08-22T17:18:12+08:00`, exited `0`, and its output contains the same executed task line.

Neither capture contains an `UP-TO-DATE` result for the diagnostic task. Both source checkouts remained at the stated source HEAD with clean status.

The fresh baseline ledger has SHA-256:

`80c0046b03264cefacdc49847f1276dab4302b8d67b448ba4482177a404f0ca4`

The authoritative final ledger is archived at the final evidence path, copied to the run-root `strict-final-ledger.csv`, and mirrored at the required attempt-10 path. It has SHA-256:

`838e06165eba05128212a013b217be35b3b24faed3f82752c5cbcd0596b98568`

The authoritative final build ledger, the final evidence ledger, the run-root archive, and the required attempt-10 mirror are byte-identical at the final hash. `evidence/attempt-12-affinity-final-options/final-ledger-provenance.txt`, `evidence/attempt-10-affinity-final/strict-final-ledger.sha256`, and `evidence/attempt-10-affinity-final/strict-final-ledger-provenance.txt` record the independent hash and `cmp` checks.

Complete failed retry ledgers for attempts 1, 3, 4, 5, 6, and 7 remain archived and are indexed only in `evidence/failed-retry-index.csv`. Each has exactly 72 rows in the canonical order, zero failure/discard counts, and one failed accepted-batch-first-frame p95 gate. Attempt 8 is different: its execution metadata and recorded output hash remain in `evidence/attempt-8-controlled/`, but its CSV was not archived. `evidence/attempt-8-controlled/ARCHIVE-STATUS.txt` explicitly excludes attempt 8 from all report comparisons; no attempt-8 ledger is fabricated or relied upon.

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

The baseline below is the fresh successful strict execution from the unchanged Task 1 source. The final values are from the authoritative fresh controlled run. Values are nanoseconds.

| Scenario and stage | Baseline p95 | Final p95 | Baseline p99 | Final p99 | Strict limit | Final pass |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| reference-2000-5000 / force | 17331400 | 17856495 | 18486976 | 20426653 | 50000000 p95 | true |
| reference-2000-5000 / full-worker | 14633780 | 4808974 | 15239307 | 7040320 | 100000000 p95 | true |
| reference-2000-5000 / edt-swap | 246671 | 250822 | 311461 | 332873 | 2000000 p95 | true |
| reference-2000-5000 / accepted-batch-first-frame | 129648354 | 131536204 | 135273453 | 138409773 | 150000000 p95 / 300000000 p99 | true |

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

- force p95 `17856495` ns, p99 `20426653` ns, strict p95 ceiling `50000000` ns
- full-worker p95 `4808974` ns, p99 `7040320` ns, strict p95 ceiling `100000000` ns
- accepted-batch-first-frame p95 `131536204` ns, p99 `138409773` ns, strict ceilings `150000000`/`300000000` ns
- edt-swap p95 `250822` ns, strict p95 ceiling `2000000` ns

Normal thresholds remain exactly five times the strict thresholds: force `250000000`, full-worker `500000000`, accepted-first-frame `750000000`/`1500000000`, and EDT swap `10000000` ns. Non-gated stages use `-1` thresholds in the CSV.

## Final Ledger

The following block is copied byte-for-byte from the authoritative final ledger:

```csv
scenario,stage,warmupCount,measuredCount,p50Nanos,p95Nanos,p99Nanos,maxNanos,normalThresholdNanos,strictThresholdNanos,failureCount,discardCount,pass
two-map,snapshot,20,30,26550,34808,36706,36706,-1,-1,0,0,true
two-map,projection,20,30,1851885,4356682,5047880,5047880,-1,-1,0,0,true
two-map,diff,20,30,749251,1731870,1814368,1814368,-1,-1,0,0,true
two-map,mutation,20,30,4876420,6331804,6724729,6724729,-1,-1,0,0,true
two-map,force,20,30,2389635,3731581,7048945,7048945,250000000,-1,0,0,true
two-map,correction,20,30,256932,624284,1152290,1152290,-1,-1,0,0,true
two-map,hull,20,30,120010,166000,185630,185630,-1,-1,0,0,true
two-map,label,20,30,3878017,6568461,7237887,7237887,-1,-1,0,0,true
two-map,full-worker,20,30,1199542,1882223,2871418,2871418,500000000,-1,0,0,true
two-map,edt-swap,20,30,381587,1359721,2324990,2324990,10000000,-1,0,0,true
two-map,repaint,20,30,24807885,27692058,27950893,27950893,-1,-1,0,0,true
two-map,accepted-batch-first-frame,20,30,8862536,14823637,16404386,16404386,750000000,-1,0,0,true
three-map-clustered,snapshot,20,30,36574,50921,83914,83914,-1,-1,0,0,true
three-map-clustered,projection,20,30,1896368,4302678,5489837,5489837,-1,-1,0,0,true
three-map-clustered,diff,20,30,583290,1305181,1569125,1569125,-1,-1,0,0,true
three-map-clustered,mutation,20,30,5212982,10987883,12673358,12673358,-1,-1,0,0,true
three-map-clustered,force,20,30,3696403,4029684,5212663,5212663,250000000,-1,0,0,true
three-map-clustered,correction,20,30,183208,318802,1106556,1106556,-1,-1,0,0,true
three-map-clustered,hull,20,30,118740,196667,217830,217830,-1,-1,0,0,true
three-map-clustered,label,20,30,5014176,6426228,6712188,6712188,-1,-1,0,0,true
three-map-clustered,full-worker,20,30,1225617,2466247,4248464,4248464,500000000,-1,0,0,true
three-map-clustered,edt-swap,20,30,328855,893154,1267769,1267769,10000000,-1,0,0,true
three-map-clustered,repaint,20,30,41042751,43818284,45521183,45521183,-1,-1,0,0,true
three-map-clustered,accepted-batch-first-frame,20,30,9630749,14425456,32514756,32514756,750000000,-1,0,0,true
reference-2000-5000,snapshot,400,300,25191,34046,42150,63796,-1,-1,0,0,true
reference-2000-5000,projection,400,300,10898654,12161679,15233301,21483789,-1,-1,0,0,true
reference-2000-5000,diff,400,300,4915271,5814720,6847624,9273617,-1,-1,0,0,true
reference-2000-5000,mutation,400,300,43472577,46816841,58266743,66848672,-1,-1,0,0,true
reference-2000-5000,force,400,300,15789085,17856495,20426653,23537864,250000000,50000000,0,0,true
reference-2000-5000,correction,400,300,1224541,1493602,1865109,2004850,-1,-1,0,0,true
reference-2000-5000,hull,400,300,1066135,1276958,1650961,28145098,-1,-1,0,0,true
reference-2000-5000,label,400,300,95338781,123308455,129974040,143041802,-1,-1,0,0,true
reference-2000-5000,full-worker,400,300,4064476,4808974,7040320,16195894,500000000,100000000,0,0,true
reference-2000-5000,edt-swap,400,300,191162,250822,332873,374034,10000000,2000000,0,0,true
reference-2000-5000,repaint,400,300,254458805,270424760,286274952,471270534,-1,-1,0,0,true
reference-2000-5000,accepted-batch-first-frame,400,300,117391345,131536204,138409773,151583653,750000000,150000000,0,0,true
skewed-reference,snapshot,20,30,24204,32713,32789,32789,-1,-1,0,0,true
skewed-reference,projection,20,30,11868927,12807921,16169421,16169421,-1,-1,0,0,true
skewed-reference,diff,20,30,4850085,5684414,5966443,5966443,-1,-1,0,0,true
skewed-reference,mutation,20,30,44538395,49074213,51720898,51720898,-1,-1,0,0,true
skewed-reference,force,20,30,17238644,19690372,21717721,21717721,250000000,-1,0,0,true
skewed-reference,correction,20,30,1266523,1471080,1914167,1914167,-1,-1,0,0,true
skewed-reference,hull,20,30,1013961,1315903,1655426,1655426,-1,-1,0,0,true
skewed-reference,label,20,30,76364384,134478974,139642335,139642335,-1,-1,0,0,true
skewed-reference,full-worker,20,30,4197542,4579594,4765265,4765265,500000000,-1,0,0,true
skewed-reference,edt-swap,20,30,196745,266679,418918,418918,10000000,-1,0,0,true
skewed-reference,repaint,20,30,368585506,381641215,394660108,394660108,-1,-1,0,0,true
skewed-reference,accepted-batch-first-frame,20,30,99113915,138972460,145306228,145306228,750000000,-1,0,0,true
one-pinned-map,snapshot,20,30,11316,17309,19277,19277,-1,-1,0,0,true
one-pinned-map,projection,20,30,759723,1227829,1247999,1247999,-1,-1,0,0,true
one-pinned-map,diff,20,30,310541,483104,536144,536144,-1,-1,0,0,true
one-pinned-map,mutation,20,30,3157122,3365841,3454527,3454527,-1,-1,0,0,true
one-pinned-map,force,20,30,1842137,2102053,3087864,3087864,250000000,-1,0,0,true
one-pinned-map,correction,20,30,73229,90244,103490,103490,-1,-1,0,0,true
one-pinned-map,hull,20,30,62104,70004,81059,81059,-1,-1,0,0,true
one-pinned-map,label,20,30,3098900,3619076,3990186,3990186,-1,-1,0,0,true
one-pinned-map,full-worker,20,30,402666,616825,631915,631915,500000000,-1,0,0,true
one-pinned-map,edt-swap,20,30,183059,243363,257177,257177,10000000,-1,0,0,true
one-pinned-map,repaint,20,30,20638367,24541331,35434409,35434409,-1,-1,0,0,true
one-pinned-map,accepted-batch-first-frame,20,30,4867415,6908654,7656802,7656802,750000000,-1,0,0,true
two-pinned-maps,snapshot,20,30,13330,17220,29393,29393,-1,-1,0,0,true
two-pinned-maps,projection,20,30,815030,1064080,1341289,1341289,-1,-1,0,0,true
two-pinned-maps,diff,20,30,336667,540241,569881,569881,-1,-1,0,0,true
two-pinned-maps,mutation,20,30,3203027,3544753,3899050,3899050,-1,-1,0,0,true
two-pinned-maps,force,20,30,1997620,2105766,3487193,3487193,250000000,-1,0,0,true
two-pinned-maps,correction,20,30,104614,171066,196078,196078,-1,-1,0,0,true
two-pinned-maps,hull,20,30,64689,130492,157878,157878,-1,-1,0,0,true
two-pinned-maps,label,20,30,3364519,3679651,4108041,4108041,-1,-1,0,0,true
two-pinned-maps,full-worker,20,30,441623,540505,638284,638284,500000000,-1,0,0,true
two-pinned-maps,edt-swap,20,30,191857,267870,302560,302560,10000000,-1,0,0,true
two-pinned-maps,repaint,20,30,17489793,19582472,19658521,19658521,-1,-1,0,0,true
two-pinned-maps,accepted-batch-first-frame,20,30,5490785,6199189,8732881,8732881,750000000,-1,0,0,true
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

The final accepted-batch-first-frame p95 is `131536204` ns, leaving `18463796` ns below the strict `150000000` ns ceiling; p99 is `138409773` ns, leaving `161590227` ns below the `300000000` ns ceiling. These margins are machine-specific observations, not a portability guarantee. Scheduler jitter, CPU frequency, JVM warm-up, font stack, background load, or slower CI hardware can move a percentile. The strict thresholds remain unchanged. Rerun the same uncached diagnostic on target CI hardware before treating the measured margin as portable.
