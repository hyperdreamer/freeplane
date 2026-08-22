# Graph Workspace Batch I Performance Remediation Design

**Status:** Approved on 2026-08-22 after the original Batch I SDD run reached `FINAL_BLOCKED` and the user approved the explicit label contract with refreshed fixture hashes.

## Goal

Clear the carried final-review findings F-2 through F-8 without reopening or editing the terminal original SDD run. The successor preserves the accepted F-1 synchronization repair, makes the Task 37 diagnostic contracts executable, regenerates the performance evidence from a genuinely executed strict invocation, and records the resulting workload bytes and measurements in a machine-auditable report.

## Starting point and carried evidence

The successor starts from the clean feature worktree at `af50e54455b620f7def0e7019bfbfede6da90b75`, on branch `2026-08-10-graph-workspace-batch-i-performance`, with merge base `48cfed5511f4fdbdc7f79ffe46b0cc4a4494c056`. The original run at `.superpowers/sdd/2026-08-21-graph-workspace-batch-i-performance/` remains terminal `FINAL_BLOCKED` and is read-only evidence. A new plan, run root, task reports, and final review are required.

Carried finding F-1 is resolved and must remain resolved: empty-diff synchronization is allowed only with matching workspace identity, synchronized-generation provenance, prior request, pins, and complete particle/anchor/link coverage; disposal invalidates the synchronization watermark.

The successor carries F-2 through F-8:

- F-2: `graphStrictPerformance` is not a declared Gradle task input, so strict execution can be `UP-TO-DATE`.
- F-3: direct `apply` and `step` frame coverage is not compared with the projection's exact node and anchor key sets.
- F-4: the diagnostic has no injectable `NanoClock` entry point.
- F-5: accepted first-frame publication uses `OperationalStatus.IDLE` instead of `SETTLING`.
- F-6: prior completion evidence refers to the calibration commit and stale measurements rather than the report-bearing HEAD; the successor must generate new completion evidence from its final report-bearing commit.
- F-7: visible leaf labels do not implement the required full/display contract.
- F-8: skewed generation encodes, but does not assert, exact map-0 80 percent ownership of nodes and enclosures.

## Label and fixture adjudication

The explicit label contract is authoritative. Generated visible leaves will use `SafeNodeLabel.of("node-full-<id>", "node-<id>")`, where `<id>` is the deterministic persisted identifier such as `m00-n0001`. The three fixture files are regenerated from that model and their new SHA-256 values are recorded in the successor report. Existing pinned hashes are historical evidence for the superseded bytes and must not be retained as if they described the corrected workload.

## Scope and boundaries

Task 1 modifies exactly four paths:

- `freeplane_plugin_graph/build.gradle`
- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/GeneratedWorkspace.java`
- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/GraphWorkspacePerformanceDiagnostic.java`
- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/PerformanceTripwiresShould.java`

Task 2 modifies exactly one deliverable path:

- `docs/superpowers/specs/2026-08-10-graph-workspace-performance-report.md`

Generated ledgers, fixtures, archived command output, and SDD reports remain evidence artifacts. The successor does not modify the original run's state, progress, reports, or event files. No production API, GraphStream dependency, runtime implementation, or prior accepted calibration file is changed.

The public observation boundary remains the existing one: deterministic input construction, projection, diff, public layout, geometry/correction, labels, immutable canvas state creation, real EDT state installation, and inherited public painting. Coordinator debounce/stale discard, canvas stale-state rejection, and private painter timing remain excluded from claims.

## Task 1 design

The Gradle `graphPerformanceDiagnostic` `JavaExec` task will declare `project.hasProperty('graphStrictPerformance')` as an explicit task input and pass the same boolean to the Java system property. A strict evidence command will use `--rerun-tasks` and archive output showing the JavaExec task executed, so a cached `UP-TO-DATE` result cannot substantiate strict claims.

`GraphWorkspacePerformanceDiagnostic` will store the interface type `NanoClock`, retain the real checked relative clock for `main`, and expose the package-private constructor `(Path outputDirectory, boolean strict, NanoClock clock)`. The supplied clock is used for process deadlines, accepted timestamps, and all stage endpoints. A package-private exact-frame validator will compare the frame node key set with `projection.nodes().key()` values and the anchor key set with `projection.enclosures().hullKey()` values, while retaining failed-frame and finite-coordinate checks. Direct `apply`, direct `step`, and worker frames will invoke it with their appropriate stage.

The accepted first-frame `CanvasState` will use `OperationalStatus.SETTLING`. The repaint graphics will set `RenderingHints.KEY_RENDERING` to `RenderingHints.VALUE_RENDER_SPEED` before invoking the production paint path, preserving the fixed rendering setup.

`GeneratedWorkspace` will emit the exact full/display labels, and validation will count actual projected node and enclosure ownership by map. For `SKEWED_REFERENCE`, it will assert map 0 owns exactly `4 * total / 5` projected nodes and enclosures, equivalently `map0Count * 5 == total * 4`, and that those counts equal the scenario allocation arrays. A focused tripwire will prove that an altered allocation is rejected. Existing skewed empty-bucket behavior, connector counts, pins, fixture names, and deterministic ordering remain enforced.

`PerformanceTripwiresShould` will add falsifiable tests for the strict Gradle input contract, exact frame coverage rejection and acceptance, injectable clock construction, `SETTLING` first-frame source behavior, the full/display label contract, and the exact skewed 80 percent allocation. Tests will remain within the existing test-file allowlist.

## Task 2 design

After the Task 1 commit is reviewed and accepted, Task 2 will run a genuinely uncached strict diagnostic from the Task 1 HEAD, archive the full command output and exit status, copy the generated ledger byte-for-byte to `strict-baseline-ledger.csv`, and record the new fixture hashes. It will rerun the strict diagnostic after report generation from the report-bearing HEAD, again with `--rerun-tasks`, and use that final ledger as authoritative evidence.

The report will be rewritten from the final artifacts. It will name the merge base, Task 37 predecessor, existing calibration and F-1 repair commits, Task 1 remediation commit, report-bearing commit, final HEAD, commands, environment, fixture hashes, ledger hashes, exact CSV schema, all 72 scenario/stage rows, thresholds, failures/discards, lifecycle checks, cleanup results, public observation exclusions, label adjudication, and residual machine-specific timing risk. It will state that the prior pinned fixture hashes were superseded by the explicitly approved label correction. The new Task 2 implementer report produced by the successor run will point to the final report-bearing HEAD and final measurements rather than the stale original report.

## Verification and acceptance

Task 1 must pass focused tripwires, the graph-plugin test and check gates, Java 8 compatibility and bundle checks, deterministic generator and serialization checks, and a normal diagnostic with exactly four output files. The strict input must change Gradle task inputs, and a strict run must show execution rather than `UP-TO-DATE`.

Task 2 must produce a fresh strict-passing 72-row ledger with zero failures/discards, updated fixture hashes consistent with the corrected labels, a report matching the final ledger byte-for-byte at the row level, clean staged allowlists for both implementation commits, `git diff --check`, and a fresh independent Frontier final review over the original merge base through the successor HEAD. Completion is allowed only after every carried finding is reconciled and no load-bearing residual remains.
