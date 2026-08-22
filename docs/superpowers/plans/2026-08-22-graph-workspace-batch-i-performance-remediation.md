# Graph Workspace Batch I Performance Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.
> The controller dispatches one fresh child at a time and reviews each task
> before the next task starts.

**Goal:** Resolve carried Graph Workspace Batch I performance findings F-2 through F-8, regenerate corrected workload fixtures and strict evidence, and finish with a report that reconciles the original terminal block without reopening it.

**Architecture:** Task 1 repairs the existing public diagnostic and deterministic generator in the four excluded Task 37 paths. It makes strict-mode provenance an actual Gradle input, validates exact layout coverage, injects the diagnostic clock, publishes the required settling status, and enforces corrected labels and skewed allocation. Task 2 runs the repaired diagnostic from the Task 1 commit and rewrites only the performance report from fresh artifacts; previously accepted production calibration and F-1 synchronization repair remain unchanged.

**Tech Stack:** Java 8-compatible test sources, Java 21 Zulu build JDK, Gradle `JavaExec`, JUnit 4, AssertJ, Swing/AWT headless rendering, existing GraphStream 1.3 layout boundary, and the deterministic SDD controller.

## Global Constraints

- Work only in `/data/home/guest/Development/freeplane/.worktrees/graph-workspace-batch-i-performance` on branch `2026-08-10-graph-workspace-batch-i-performance`; the base checkout and the original run at `.superpowers/sdd/2026-08-21-graph-workspace-batch-i-performance/` are read-only.
- Start implementation from the clean planning HEAD that contains this committed plan; its parent remediation HEAD is `af50e54455b620f7def0e7019bfbfede6da90b75`, and the merge base remains `48cfed5511f4fdbdc7f79ffe46b0cc4a4494c056`; preserve the accepted F-1 synchronization repair in `GraphStreamLayoutEngine.java`.
- Execute Task 1 completely, review and accept its commit, and verify its commit before changing the Task 2 report path.
- Build every Gradle command with `JAVA_HOME=/home/guest/.sdkman/candidates/java/21.0.8-zulu`, prepend `$JAVA_HOME/bin` to `PATH`, and invoke `gradle`, never `gradlew`.
- Keep Java source and bytecode compatible with the repository Java 8 target; do not add records, `var`, `List.of`, `Path.of`, or Java 9+ APIs.
- Task 1 may modify exactly `freeplane_plugin_graph/build.gradle`, `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/GeneratedWorkspace.java`, `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/GraphWorkspacePerformanceDiagnostic.java`, and `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/PerformanceTripwiresShould.java`.
- Task 2 may modify exactly `docs/superpowers/specs/2026-08-10-graph-workspace-performance-report.md`; generated ledgers, fixtures, and successor SDD evidence remain uncommitted artifacts under the ignored build or run directories.
- The explicit visible-leaf contract is `SafeNodeLabel.of("node-full-<id>", "node-<id>")`, with `<id>` equal to the deterministic persisted identifier such as `m00-n0001`; corrected fixture bytes and SHA-256 values supersede the historical pinned hashes.
- The fixed diagnostic seed is `20260810`; the reference workload remains 20 active maps, 2,000 visible nodes, 1,200 enclosures, 5,000 edge keys/contributors, 3,500 native contributors, 1,500 cross-map contributors, 2,000 containment links, 1,180 hierarchy links, 3,200 particles, and 8,180 springs.
- Stage names remain exactly `snapshot`, `projection`, `diff`, `mutation`, `force`, `correction`, `hull`, `label`, `full-worker`, `edt-swap`, `repaint`, and `accepted-batch-first-frame`; the first-frame path uses `LayoutWorker.submit(request)`, never `step`.
- Strict reference ceilings remain force p95 `<= 50 ms`, full-worker p95 `<= 100 ms`, accepted-batch-first-frame p95/p99 `<= 150/300 ms`, and EDT-swap p95 `<= 2 ms`; normal thresholds remain exactly five times those ceilings.
- The diagnostic must retain immutable `LayoutFrame` and `CanvasState` publication, O(N+E) rebuild/synchronization behavior, SpringBox quality `0.10`, ordered force multipliers, aggregate cross-map displacement cap `0.005`, rigid pinned-map correction, deterministic seeds, labels, pins, hulls, arrows, dimming, accessibility rendering, and lifecycle checks.
- The diagnostic must continue to measure only the public observation boundary and must not claim coordinator debounce/stale discard, canvas stale-state rejection, or private painter timing.
- Every implementation commit subject begins with `2026-08-10-graph-workspace:`. Before each commit, assert an empty index, stage only the task allowlist, compare `git diff --cached --name-only` with the allowlist, run `git diff --cached --check`, and verify names with `git show --name-only --format= HEAD`.
- Do not edit this plan after SDD initialization. A needed plan correction requires a new approved successor plan.

## Task 1: Repair Task 37 Diagnostic Contracts and Workload Invariants

**Implementer tier:** Capable

**Files:**

- Modify: `freeplane_plugin_graph/build.gradle:113-124`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/GeneratedWorkspace.java:1-end`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/GraphWorkspacePerformanceDiagnostic.java:1-end`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/PerformanceTripwiresShould.java:1-end`

**Interfaces:**

- Consumes: existing `NanoClock`, `GraphProjection`, `LayoutFrame`, `LayoutPositions`, `ProjectedNodeKey`, `EnclosureHullKey`, `CanvasState`, `GraphGeometry`, `OperationalStatus`, and `PerformanceMeasurements.Stage` types.
- Produces: `GeneratedWorkspace.assertSkewedMapAllocationContract(GeneratedWorkspace.Scenario scenario, int[] actualNodesByMap, int[] actualEnclosuresByMap): void`, package-private and throwing `IllegalArgumentException` for invalid arguments or `IllegalStateException` for an incorrect skewed allocation.
- Produces: package-private `GraphWorkspacePerformanceDiagnostic(Path outputDirectory, boolean strict, NanoClock clock)`; `main` continues to use `new RelativeNanoClock()` and the supplied constructor stores the interface instance for every deadline and measurement.
- Produces: package-private `GraphWorkspacePerformanceDiagnostic.clock(): NanoClock` for the same-package tripwire, plus package-private `GraphWorkspacePerformanceDiagnostic.validateFrameCoverage(LayoutFrame frame, GraphProjection projection, String operation): void`, which rejects missing or extra node/anchor keys before finite-coordinate checks; `requireUsableFrame` uses this validator for direct apply, direct step, and worker frames.
- Produces: package-private `GraphWorkspacePerformanceDiagnostic.acceptedFirstFrameState(long generation, GraphProjection projection, LayoutFrame frame, GraphGeometry geometry): CanvasState`, which constructs `CanvasState` with `OperationalStatus.SETTLING` and is the method used by the accepted-batch composition.
- Preserves the existing `graphPerformanceDiagnostic` output contract: only `two-map.fpg`, `three-map.fpg`, `reference-2000-5000.fpg`, and `performance-ledger.csv` are emitted in `build/graph-performance`.

### Step 1: Establish red evidence and add falsifiable tests

- [ ] Confirm the clean successor starting point, current HEAD, and Java toolchain, then run the existing focused class before edits:

```bash
cd /data/home/guest/Development/freeplane/.worktrees/graph-workspace-batch-i-performance
env JAVA_HOME=/home/guest/.sdkman/candidates/java/21.0.8-zulu PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.performance.PerformanceTripwiresShould' -PTestLoggingFull
```

- [ ] Add tests in `PerformanceTripwiresShould` for the corrected contracts. The tests must: assert every generated projected visible node has `fullText()` equal to `"node-full-" + persistedReference.nodeId().value()` and `displayText()` equal to `"node-" + persistedReference.nodeId().value()`; call `GeneratedWorkspace.assertSkewedMapAllocationContract` with `[1600, 21, ..., 22]` and `[960, 12, ..., 24]` and assert success; alter map-0 to `1599` and assert rejection; construct a `GraphProjection` with one projected node and one enclosure plus an empty `LayoutFrame` and assert `validateFrameCoverage` rejects it; construct an empty projection and empty frame and assert exact coverage accepts it; construct the diagnostic with a counting `NanoClock` and assert `clock()` returns the same supplied interface; and construct an empty `CanvasState` through `acceptedFirstFrameState` and assert its status is `OperationalStatus.SETTLING`.
- [ ] Use existing public factories for the tiny coverage/status fixtures: `GraphProjection.structure(0L, nodes, enclosures)`, `ProjectedNode.of(ProjectedNodeKey.of(SourceNodeKey.persisted(NodeReference.of(...))), SafeNodeLabel.of("full", "display"), "map", false)`, `ProjectedEnclosure.of(...)` with a single `EnclosureHullKey`, `LayoutPositions.of(Collections.emptyMap(), Collections.emptyMap())`, `LayoutFrame.of(0L, positions, false)`, and `GraphGeometry.of(Collections.emptyMap(), Collections.emptyMap())`. Keep the test setup Java 8-compatible and within this one existing test file.
- [ ] Run the focused test class and record the expected red result: compilation or assertion failures for the not-yet-present constructor, validators, status helper, label change, and skew assertion. Do not implement first and call a test that never failed a red phase.

### Step 2: Make strict-mode provenance an actual Gradle input

- [ ] In the `graphPerformanceDiagnostic` `JavaExec` registration, bind one local boolean to `project.hasProperty('graphStrictPerformance')`, declare `inputs.property('graphStrictPerformance', strictPerformance)`, and pass `strictPerformance.toString()` to `systemProperty 'graphStrictPerformance', ...`. Keep the existing `dependsOn`, test runtime classpath, main class, headless/locale properties, output directory, and output argument unchanged.
- [ ] Verify the input distinction with two real Gradle invocations from a clean output directory. First run normal mode, then run strict mode with `--rerun-tasks`; capture each output and require the strict output to contain an executed `:freeplane_plugin_graph:graphPerformanceDiagnostic` line rather than `UP-TO-DATE`. The command shape is:

```bash
rm -rf freeplane_plugin_graph/build/graph-performance
mkdir -p /tmp/graph-workspace-remediation-task-1
env JAVA_HOME=/home/guest/.sdkman/candidates/java/21.0.8-zulu PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:graphPerformanceDiagnostic -PTestLoggingFull > /tmp/graph-workspace-remediation-task-1/normal.txt 2>&1
env JAVA_HOME=/home/guest/.sdkman/candidates/java/21.0.8-zulu PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:graphPerformanceDiagnostic -PgraphStrictPerformance --rerun-tasks -PTestLoggingFull > /tmp/graph-workspace-remediation-task-1/strict.txt 2>&1
rg -n ':freeplane_plugin_graph:graphPerformanceDiagnostic' /tmp/graph-workspace-remediation-task-1/strict.txt
```

- [ ] Preserve nonzero diagnostic failures. A command that reports `UP-TO-DATE` for the strict invocation is not valid evidence even if the process exits zero.

### Step 3: Implement exact frame validation, injectable clock, status, and paint setup

- [ ] Change the diagnostic field type from `RelativeNanoClock` to `NanoClock`; replace the two-argument private constructor with package-private `GraphWorkspacePerformanceDiagnostic(Path outputDirectory, boolean strict, NanoClock clock)`, validate the argument with `Objects.requireNonNull`, set `processStart = clock.nanoTime()`, and add package-private `NanoClock clock()` only for the same-package tripwire. `main` must call the three-argument constructor with `new RelativeNanoClock()`.
- [ ] Add package-private `acceptedFirstFrameState(...)` and use it at the accepted-batch publication call site. The returned state must be created exactly with `CanvasState.of(generation, projection, frame, geometry, OperationalStatus.SETTLING)`; no alternate first-frame status may remain on that path.
- [ ] Add package-private `validateFrameCoverage(...)`. Build `HashSet<ProjectedNodeKey>` from `projection.nodes()`, `HashSet<EnclosureHullKey>` from `projection.enclosures()`, compare both sets with `frame.positions().nodes().keySet()` and `frame.positions().anchors().keySet()`, and throw an `IllegalArgumentException` naming the operation and whether node or anchor coverage differs. Preserve exact equality rather than accepting subsets or supersets.
- [ ] Update `requireUsableFrame` to accept the current `GraphProjection`, call `validateFrameCoverage`, retain `null`/failed checks and finite node/anchor coordinate checks, and pass the correct stage when creating `DiagnosticFailure`. Update all worker, direct-apply, and direct-step call sites. Do not change the GraphStream boundary or layout algorithm.
- [ ] In `repaintBounded`, set `graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED)` immediately after creating the `Graphics2D` and before `canvas.paint(graphics)`, then retain disposal and checksum logic.
- [ ] Run the focused tests and confirm the new clock, coverage, status, and rendering contracts pass while the generator label/allocation tests remain red until Step 4.

### Step 4: Implement corrected labels and exact skewed map-0 allocation

- [ ] In `GeneratedWorkspace.buildSnapshot`, replace the visible leaf label construction with `SafeNodeLabel.of("node-full-" + reference.nodeId().value(), "node-" + reference.nodeId().value())`; keep persisted source keys, tree order, enclosure labels, connector endpoints, and all other deterministic generation unchanged.
- [ ] In `GeneratedWorkspace.validate`, count projected node and enclosure ownership by the canonical map index for every `GraphProjection` element, then call `assertSkewedMapAllocationContract` for `SKEWED_REFERENCE`. The helper must verify the two arrays have length 20, their totals are 2,000 and 1,200, map 0 has 1,600 nodes and 960 enclosures, and `map0 * 5 == total * 4` for both dimensions. For non-skewed scenarios, reject a call to the helper with `IllegalArgumentException` so the contract cannot be silently applied to the wrong variant.
- [ ] Preserve the documented skewed final-map empty enclosure buckets `[22, 23]`; do not change the scenario arrays, contributor allocation, pin behavior, or projection count validation while adding the ownership assertion.
- [ ] Run the focused tripwires. Require the label test, helper boundary test, exact coverage test, all existing generator tests, and serialization tests to pass. Compute the three corrected fixture hashes with:

```bash
sha256sum freeplane_plugin_graph/build/graph-performance/two-map.fpg freeplane_plugin_graph/build/graph-performance/three-map.fpg freeplane_plugin_graph/build/graph-performance/reference-2000-5000.fpg
```

- [ ] Run the normal diagnostic with `--rerun-tasks`, verify six scenarios times twelve stages equals 72 rows, zero failure/discard counts, all normal rows pass, and the output directory has exactly four regular files. The updated labels must produce fixture bytes different from the historical hashes; record the new values for Task 2.

### Step 5: Run complete Task 1 gates and commit exactly four paths

- [ ] Run the focused test class, complete graph-plugin tests/check, and Java 8/bundle verification with full logging:

```bash
env JAVA_HOME=/home/guest/.sdkman/candidates/java/21.0.8-zulu PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.performance.PerformanceTripwiresShould' -PTestLoggingFull
env JAVA_HOME=/home/guest/.sdkman/candidates/java/21.0.8-zulu PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test :freeplane_plugin_graph:check -PTestLoggingFull
```

- [ ] Verify the strict run is genuinely executed after the label and contract changes:

```bash
env JAVA_HOME=/home/guest/.sdkman/candidates/java/21.0.8-zulu PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:graphPerformanceDiagnostic -PgraphStrictPerformance --rerun-tasks -PTestLoggingFull
```

Archive the command output, exit status, fixture hashes, ledger SHA-256, and the exact 72-row CSV under the successor SDD run root. Do not call a cached result strict evidence.
- [ ] Before staging, require `git status --short`, `git diff --check`, and an empty index. Stage exactly the four Task 1 paths, assert `git diff --cached --name-only` equals:

```text
freeplane_plugin_graph/build.gradle
freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/GeneratedWorkspace.java
freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/GraphWorkspacePerformanceDiagnostic.java
freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/PerformanceTripwiresShould.java
```

- [ ] Commit with `git commit -m "2026-08-10-graph-workspace: Repair graph performance diagnostic contracts"`, verify the commit contains exactly those four paths and no Task 2 report, and write the implementer report with the commit SHA, tests, fresh strict evidence, updated hashes, and no unresolved correctness or scope concern.

## Task 2: Regenerate Strict Evidence and Reconcile the Performance Report

**Implementer tier:** Capable

**Files:**

- Modify: `docs/superpowers/specs/2026-08-10-graph-workspace-performance-report.md:1-end`

**Interfaces:**

- Consumes: the accepted Task 1 commit, the existing calibration and F-1 repair commits, `freeplane_plugin_graph/build/graph-performance/performance-ledger.csv`, the successor SDD run root, and the exact four-path Task 1 allowlist result.
- Produces: a byte-preserved successor baseline ledger at the successor SDD run root, a fresh final `performance-ledger.csv` with 72 rows and zero failures/discards, corrected fixture SHA-256 values, and a report whose embedded rows match the final ledger exactly.
- Produces completion evidence in the successor Task 2 implementer report that names the report source HEAD, report-bearing commit SHA, final ledger hash, final fixture hashes, and the exact command output proving strict execution. The report document records the non-self-referential report source HEAD and points to this completion evidence for the report-bearing commit.
- Does not modify any of the Task 1 source paths, the six accepted calibration/synchronization source paths, the original blocked run, unrelated backlog files, or generated build outputs in Git.

### Step 1: Verify the Task 1 handoff and capture a fresh strict baseline

- [ ] Confirm the worktree is clean, the current commit is the accepted Task 1 commit, its subject begins with `2026-08-10-graph-workspace:`, and `git show --name-only --format= HEAD` lists exactly the four Task 1 paths. Stop before report edits if this handoff is not true.
- [ ] Run the repaired diagnostic with strict mode and forced execution from the Task 1 HEAD. Capture stdout/stderr, exit status, fixture hashes, ledger hash, and the full task output under the successor SDD run root. Use the exact command form:

```bash
env JAVA_HOME=/home/guest/.sdkman/candidates/java/21.0.8-zulu PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:graphPerformanceDiagnostic -PgraphStrictPerformance --rerun-tasks -PTestLoggingFull
sha256sum freeplane_plugin_graph/build/graph-performance/two-map.fpg freeplane_plugin_graph/build/graph-performance/three-map.fpg freeplane_plugin_graph/build/graph-performance/reference-2000-5000.fpg freeplane_plugin_graph/build/graph-performance/performance-ledger.csv
```

- [ ] Require the output to show an executed `:freeplane_plugin_graph:graphPerformanceDiagnostic` task and never use `UP-TO-DATE` output as evidence. Require exit zero, exactly 72 rows, all strict reference ceilings passing, zero failures, zero discards, and four regular output files.
- [ ] Copy `freeplane_plugin_graph/build/graph-performance/performance-ledger.csv` byte-for-byte to `strict-baseline-ledger.csv` in the successor SDD run root after the command completes. Hash both files and record the equality. Keep this archive outside `build/graph-performance` because the diagnostic clears that directory at the start of each run.

### Step 2: Rewrite the report from the fresh artifacts

- [ ] After archiving the baseline, run the same forced strict diagnostic a second time from the unchanged Task 1 source HEAD. Treat this second execution as the authoritative final measurement, archive its output, exit status, final ledger hash, corrected fixture hashes, and exact task execution line, and require all 72 rows and strict ceilings to pass. The report source HEAD is still the accepted Task 1 commit because the second run makes no source changes.
- [ ] Replace the stale report contents with a report that identifies merge base `48cfed5511f4fdbdc7f79ffe46b0cc4a4494c056`, the original Task 37 commit `8950e2fe0fc4209f3860d0608e8d694623f516a6`, calibration commit `bfb21b5b045bd6872d633c162a7ad6781165244c`, synchronization repair commit `6816f546b2ea28dbe6714fb99401c86482625238`, the accepted Task 1 remediation commit, the report source HEAD used for the final measurement, and the successor Task 2 report-bearing commit in completion evidence. State that the original report and original SDD run are superseded evidence, not edited.
- [ ] Record the actual Java/Gradle/OS/CPU/RAM/JVM environment, headless and font settings, timestamp, exact forced strict command, captured exit status, task execution line, output artifact paths, baseline/final ledger hashes, corrected fixture hashes, and the exact CSV header. State explicitly that the old fixture hashes were replaced because the user approved the full/display label contract.
- [ ] Include the complete 72-row final ledger byte-for-byte in a fenced CSV block and a separate baseline comparison. Each row must contain scenario, stage, warm-up count, measured count, p50, p95, p99, maximum, normal threshold, strict threshold, failure count, discard count, and pass. Do not round, reorder, or manually transcribe values differently from the final CSV.
- [ ] Document all twelve stage boundaries, relative monotonic clock definition, injectable-clock test contract, five-second operation bounds, ten-minute process deadline, exact node/anchor coverage, `SETTLING` accepted first-frame state, corrected labels, exact skewed 80 percent assertion, fixture serialization, pin/conflict outcomes, lifecycle probes, cleanup/thread checks, and the retained F-1 synchronization provenance.
- [ ] State the public observation exclusions exactly: coordinator debounce, coordinator stale-generation/closed-publication discard, canvas stale-state rejection, and private painter internals are not measured or claimed. Record residual machine-specific timing risk without changing strict thresholds.

### Step 3: Verify report/ledger consistency and commit exactly one path

- [ ] Parse the final CSV with a structured script or existing command-line tools. Assert the exact header, 72 rows in scenario/stage order, six scenario count pairs, all zero failure/discard fields, applicable thresholds, strict reference passes, and byte equality between the CSV embedded in the report and the final ledger. Assert the three fixture hashes in the report equal the fresh `sha256sum` output.
- [ ] Run `git diff --check`, assert the index is empty, stage only `docs/superpowers/specs/2026-08-10-graph-workspace-performance-report.md`, and verify the staged name list contains exactly that one path.
- [ ] Commit with `git commit -m "2026-08-10-graph-workspace: Reconcile graph performance evidence"`. Record the resulting commit SHA in the successor Task 2 implementer report together with the report source HEAD, final ledger hash, final fixture hashes, and the forced strict execution output. Do not rewrite the report after this commit; the report source HEAD plus external completion evidence avoids a self-referential commit hash.
- [ ] Re-run `git show --name-only --format= HEAD`, `git status --short`, and `git diff --check` after the report commit. Verify the report-bearing commit changes only the report path and that the final ledger and fixtures still correspond to the unchanged Task 1 source HEAD used for the authoritative second strict run. Do not run another timing diagnostic after committing the report, because that could produce different machine-dependent percentile values without changing the measured source.

### Step 4: Completion evidence for the successor run

- [ ] Write the Task 2 implementer report with `STATUS: DONE`, exact source changes, all focused and full graph-plugin gates, forced strict command exit status, task execution evidence, baseline/final ledger equality or explained timing rerun, corrected fixture hashes, report source HEAD, report-bearing commit SHA, and a fresh final review handoff.
- [ ] Verify the cumulative successor implementation changes from the planning HEAD recorded at SDD initialization through the final HEAD contain exactly the five approved successor deliverable paths: the four Task 1 source paths and the report path. The planning commit is intentionally outside this implementation range; the earlier accepted calibration and synchronization files remain in history but are not modified by the successor.
- [ ] Require a fresh Frontier final review over merge base `48cfed5511f4fdbdc7f79ffe46b0cc4a4494c056` through the successor final HEAD. The final review must reconcile F-1 as resolved, F-2 through F-8 as absent or resolved with evidence, and report `SPEC: PASS` plus `QUALITY: APPROVED` with no load-bearing finding before completion.

## Plan completion gate

- [ ] Report the successor Task 1 and Task 2 commit SHAs, the corrected fixture and ledger hashes, the exact forced strict command and execution evidence, complete graph-plugin gate results, exact cumulative allowlist, original-run preservation, and any remaining machine-specific performance risk. Do not claim completion from a cached Gradle result, a stale ledger, or a report that does not match the final authoritative CSV.
