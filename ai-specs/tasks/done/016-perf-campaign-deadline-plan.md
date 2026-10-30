# 016 plan — size the performance campaign's process deadline

Source record: `ai-specs/tasks/done/016-perf-campaign-deadline.md`
Base commit: `e388469400`

## Global Constraints

- No performance assertion changes: `RAW_REFERENCE_MAX_FRAME_NANOS`, the per-stage thresholds,
  `TWO_PINNED_MAPS_EXPECTED_CONFLICTS`, the scenario counts, `WARMUP_SAMPLES`, `TIMED_SAMPLES` and
  `allRowsPass()` stay exactly as they are.
- No blanket relaxation of an existing bound beyond the guard itself. No long-running campaign is ever
  allowed to finish by lowering a threshold.
- No production file, no fixture, no Gradle task. Test-scope only.
- Java 8 source level, 4-space indent, UTF-8. `JAVA_HOME=~/.sdkman/candidates/java/21.0.8-zulu`.
  Use `gradle`, never `gradlew` or Maven.
- Any check the completed campaign fails is a finding to be reported and triaged, never tuned away.
- Every verification step records its exact command and output.

## Task 1: Size the guard from the measured cost and report progress

**Implementer tier:** Advanced

Files: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/GraphWorkspacePerformanceDiagnostic.java`

- Measure the campaign's real cost before choosing a number: instrument per-sample progress, run it,
  read the rate, and project the total. Record the numbers in the javadoc of the constant.
- Raise `PROCESS_DEADLINE_NANOS` from 40 to 120 minutes and document that it is a liveness guard, not an
  assertion, with the measured figures as the justification for the margin.
- Print one line per scenario with its own duration and the budget consumed, because a silent hour is
  indistinguishable from the hang the guard exists to detect.
- Do not touch any other constant or assertion.

Verification: compiles; the measured projection is captured in the record before the change is made.

## Task 2: Run the campaign to completion and record what it reports

**Implementer tier:** Standard

Files: none in the worktree; the task record only.

- Run the full diagnostic with a hard timeout well above the new guard and capture the complete output.
- Confirm the sweep reaches the last scenario, the guard never fires, and the gate verdict is the first
  line of output.
- Record every scenario's duration verbatim, and record whatever the campaign's own checks then say
  without modifying them.
- If a check fails, classify it as far as the evidence allows (stale golden versus behaviour change) and
  state that the classification is not resolved here.

Verification: the record contains the gate line, every scenario line, the terminal failure and the total
duration, and no golden or threshold was edited to obtain them.
