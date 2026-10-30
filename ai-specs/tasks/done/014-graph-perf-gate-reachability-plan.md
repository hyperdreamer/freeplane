# 014 plan — the raw-reference wall-clock gate: reachable and meaningful

Source record: `ai-specs/tasks/done/014-graph-perf-gate-reachability.md`
Base commit: `0fb4221622`

## Global Constraints

- No production source change. `freeplane_plugin_graph/src/main/java/**` stays byte-identical to
  `0fb4221622`.
- No constant is moved: `RAW_REFERENCE_MAX_FRAME_NANOS`, `PROCESS_DEADLINE_NANOS`,
  `OPERATION_TIMEOUT_NANOS`, `WARMUP_SAMPLES`, `TIMED_SAMPLES` and the scenario counts are unchanged.
  The task bounds the right measurement instead of moving the number.
- No new Gradle task and no gate-only mode: the tool keeps one execution path. The campaign's own
  process deadline is recorded as an open decision, not "fixed" by adding a partial-run mode.
- Java 8 source level, 4-space indent, UTF-8. `JAVA_HOME=~/.sdkman/candidates/java/21.0.8-zulu`.
  Use `gradle`, never `gradlew` or Maven.
- Every verification step records its exact command and its exact output.

## Task 1: Bound the steady-state frames and pin the keyset

**Implementer tier:** Advanced

Files: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/GraphWorkspacePerformanceDiagnostic.java`

- Add `RAW_REFERENCE_WARMUP_FRAMES = 1`, documenting the measured reason (frame 0 pays class loading and
  first-pass JIT; 5 824 ms against 824-988 ms for the rest) and that the bound exists to catch a layout
  regression rather than start-up cost.
- Add `RAW_REFERENCE_NODES = 2000` and `RAW_REFERENCE_EDGES = 5000` as the calibrated keyset, and record
  the measured projection's counts in `runRawReferenceFixture()`.
- Add `rawReferenceMeasuredMaxFrameNanos()`, keeping `rawReferenceMaxFrameNanos()` as the all-frames
  maximum for diagnosis only.
- Rewrite `verifyRawReferenceFrameBound()`: run the fixture, fail if the projection is no longer the
  calibrated keyset, fail if the measured window is empty, compare the measured maximum against the
  unchanged bound, and on success print one line naming the keyset, the exclusion, the maximum, the bound
  and every frame duration with the warm-up marked.
- Clear the frame list at the start of each fixture run so the measured window belongs to one run.

Verification: compiles; `PerformanceTripwiresShould` passes unchanged apart from the accessor swap.

## Task 2: Run the gate before the campaign

**Implementer tier:** Standard

Files: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/GraphWorkspacePerformanceDiagnostic.java`

- Move the `verifyRawReferenceFrameBound()` call from after `runLifecycleProbes(...)` to immediately
  after `configureCanvas()`, with a comment giving the reason: a 12-second deterministic gate must not be
  sequenced behind a multi-hour sweep whose own deadline can expire first, because that is what made the
  verdict unobservable.

Verification: a run of the task prints the gate verdict while the campaign is still running.

## Task 3: Pin the warm-up exclusion with deterministic tests

**Implementer tier:** Advanced

Files: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/PerformanceTripwiresShould.java`

- Add `excludesOnlyTheWarmUpFrameFromTheCompleteFrameBound`: with the existing synthetic clock, stall only
  the first frame and assert that the all-frame maximum exceeds the bound, the measured maximum does not,
  and `verifyRawReferenceFrameBound()` does not throw.
- Point `assertRawReferenceFrameBound` at the measured accessor so the deterministic test asserts the
  quantity the gate asserts.
- Leave `rejectsAFrameWhoseRoutingExceedsTheCompleteFrameBound` unchanged: it stalls every frame and must
  still fail the gate, which pins the other direction.

Verification: both tests pass; together they pin the exclusion in both directions without a sleep or a
wall-clock threshold.

## Task 4: Verify end-to-end and record the result

**Implementer tier:** Standard

Files: none in the worktree; the task record only.

- Run `gradle :freeplane_plugin_graph:graphPerformanceDiagnostic` with a bounded window and confirm the
  gate verdict is printed before any campaign sample completes; record the printed line verbatim.
- Mutation A: compile a copy of the diagnostic with `RAW_REFERENCE_MAX_FRAME_NANOS = 1_000_000L` and
  confirm it fails fast with the frame list.
- Mutation B: compile a copy with `RAW_REFERENCE_NODES = 1999` and confirm it fails with the calibration
  message.
- Confirm production byte-identity against the base and run the module suite via
  `scripts/run-suite-on-xvfb.sh`; record tests, failures, skips and duration.
- Record the campaign's true scale (six scenarios, 450 measured samples, 216 000 label placements) and the
  open decision about `PROCESS_DEADLINE_NANOS` rather than resolving it here.

Verification: the record's §8 contains every command with its output, and the plan gate exits 0.
