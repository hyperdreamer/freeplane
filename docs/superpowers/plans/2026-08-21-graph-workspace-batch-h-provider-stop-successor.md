# Graph Workspace Batch H Provider-Stop Successor Plan

> This is a successor plan for the terminal provider-stop run
> `.superpowers/sdd/2026-08-20-graph-workspace-batch-h-remediation`. Use the
> deterministic subagent-driven-development controller from a fresh ignored run
> root. Do not reopen or mutate the blocked run.

## Global Constraints

- Continue in `/data/home/guest/Development/freeplane/.worktrees/graph-workspace-batch-h-ui-shell`; do not create a new worktree.
- Use `JAVA_HOME=$HOME/.sdkman/candidates/java/21.0.8-zulu` and `gradle`, never Maven or the Gradle wrapper.
- Preserve Java 8 source/target compatibility, four-space Java indentation, and repository testing conventions.
- The blocked predecessor run `.superpowers/sdd/2026-08-20-graph-workspace-batch-h-remediation` is terminal `DISPATCH_MISMATCH_BLOCKED` because its Task 2 provider stopped before source edits or a report. Preserve its state, ledger, pointer, envelope, transcript, and absence of report unchanged; do not admit any blocked-run Task 2 evidence and do not reissue its prompt in place.
- The carried Task 1 presentation commit is `7e381428707cb1259b5eb163451d2bf2a535fc14`, based on `1ff2afd3b11ed8981c17f79af5d9931a878c9251`, and changes exactly the ten presentation paths listed in the predecessor plan. Audit that range afresh in Task 1 without reverting, amending, or rewriting it.
- Task 1 is audit-only and must leave the source tree and `HEAD` unchanged. Its report and review are new evidence; the predecessor Task 1 report and review are context only.
- Task 2 implements deterministic extension shutdown only in the four listed paths. Do not broaden its allowlist or alter user-triggered close/save semantics.
- `freeplane_plugin_graph` makes no `freeplane_api` changes and neither subclasses nor replaces `MapView`.
- Freeplane `MapModel` and `NodeModel` reads remain EDT-only. Swing communicates through the existing graph workspace seams.
- The graph window remains modeless and headless-testable. Tests must not call `setVisible`.
- Presentation snapshots remain immutable safe display values. Workspace history and source-map history remain distinct.
- Every implementation task writes or validates tests first, runs its focused red gate where source behavior is added, runs scoped verification, and writes its report immediately after final verification. Every source-changing commit uses an imperative subject beginning `2026-08-10-graph-workspace:`.
- For every dispatch, render and persist the complete role envelope beneath the fresh run root, dispatch only its short pointer, byte-compare pointer files before spawn, record the returned session immediately, and compare the completed transcript's first user message byte-for-byte with the stored pointer before admitting the report.
- Redirect verbose verification output beneath the active run root. Never hand-edit `state.json` or `progress.md`.
- The predecessor finding IDs F-2, F-3, F-4, and F-5 remain carried obligations for the final Frontier review.

## Task 1: Audit the carried immutable presentation correction

**Implementer tier:** Capable

**Files:**
- Inspect only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/GraphWorkspacePresentation.java`
- Inspect only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/GraphWorkspaceViewBinding.java`
- Inspect only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/DefaultGraphWorkspaceController.java`
- Inspect only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphTheme.java`
- Inspect only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphCanvas.java`
- Inspect only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPainter.java`
- Inspect only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java`
- Inspect only: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java`
- Inspect only: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/GraphWorkspacePresentationShould.java`
- Inspect only: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java`

**Interfaces:**
- Audits exact source range `1ff2afd3b11ed8981c17f79af5d9931a878c9251..7e381428707cb1259b5eb163451d2bf2a535fc14`.
- Confirms the Task 1 brief requirements: immutable presentation values, persisted palette/rendering, display settings, viewport policy, menu enablement, headless paint coverage, and ten-file scope.
- Produces a new audit report with no source edits and no commit.

### Step 1: Establish the carried baseline

Verify that `HEAD` is exactly `7e381428707cb1259b5eb163451d2bf2a535fc14`, the current worktree has no source changes, and the exact ten-file range from `1ff2afd3b11ed8981c17f79af5d9931a878c9251` is unchanged. Read the predecessor Task 1 brief only for requirements; do not treat its report as evidence.

### Step 2: Run bounded audit verification

Run the focused presentation/canvas/window tests and `git diff --check` against the carried range, redirecting output below the successor run root. Verify that the tests are headless and that no forbidden path changed. Do not edit production or test source. Write the audit-only implementer report immediately after the final command; the report must say `DONE`, list the carried commit and verification, and state that `HEAD` and source files were unchanged.

### Step 3: Review the carried range independently

The task reviewer must inspect exactly `1ff2afd3b11ed8981c17f79af5d9931a878c9251..7e381428707cb1259b5eb163451d2bf2a535fc14`, run the permitted focused suites, and issue independent `SPEC` and `QUALITY` verdicts. Any finding is handled through the successor's normal bounded review/fix loop; do not alter the audit-only task into a source implementation task.

### Step 4: Commit (no source commit)

Confirm `git status --porcelain` is unchanged and do not stage or commit any source file. The only deliverables from this audit task are its bounded report and the independent review evidence under the successor run root.

## Task 2: Implement deterministic extension shutdown

**Implementer tier:** Capable

**Files:**
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/DefaultGraphWorkspaceController.java:250-760`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/GraphModeExtension.java:1-120`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/DefaultGraphWorkspaceControllerShould.java:1-end`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphPluginIntegrationShould.java:1-end`

**Interfaces:**
- Consumes `DefaultGraphWorkspaceController.open`, `openSessions`, `WorkspaceSessionRegistry`, `Session`, `SessionResources`, existing teardown helpers, `GraphWorkspaceView.close`, `GraphModeExtension.close`, and the Task 1 presentation binding.
- Produces idempotent `DefaultGraphWorkspaceController.shutdown()` or an equivalently explicit package-visible lifecycle method used by `GraphModeExtension`; on successful return no owned session remains registered, and the extension invokes shutdown before clearing graph references.

### Step 1: Write shutdown regression tests first

Extend the named controller tests with injected resources and fake views proving that shutdown discards/closes every owned session resource, closes the view, unregisters the session, makes the handle unusable, is idempotent, and continues cleanup across failures before reporting an aggregate failure. Cover shutdown with no sessions and repeated calls. Extend the integration test to prove `GraphModeExtension.close()` invokes controller shutdown before removing graph extension/action references. Keep tests headless and never call `setVisible`.

### Step 2: Run the focused red gate

Run:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" gradle :freeplane_plugin_graph:test \
  --tests '*DefaultGraphWorkspaceControllerShould' \
  --tests '*GraphPluginIntegrationShould' -PTestLoggingFull \
  >"$RUN_ROOT/logs/task-2-red.log" 2>&1
red_status=$?
tail -n 60 "$RUN_ROOT/logs/task-2-red.log"
test "$red_status" -ne 0
```

The red result must identify the missing shutdown seam or extension lifecycle call, not a test setup typo. Do not modify production code before this gate.

### Step 3: Implement idempotent controller shutdown

Under the controller monitor, atomically mark shutdown and snapshot every live session. Reject new `open` calls once shutdown begins. For each snapshot session, perform discard-close semantics without a user save dialog: transition closing, close the store and status publisher in the established safe order, close updates/maps/leases/scheduler off the EDT, close the view on the EDT, mark the handle closed, remove the session from `openSessions`, and unregister its session ID. Wait for EDT cleanup before returning. Aggregate failures, continue later sessions, and make repeated shutdown calls perform no teardown. Leave user-triggered `closeSession` and save/retry/discard semantics unchanged before shutdown.

### Step 4: Wire extension lifecycle and verify

Keep the concrete controller for lifecycle shutdown while passing the forwarding controller to the view factory and open action. Make `GraphModeExtension.close()` invoke shutdown before removing graph extensions/actions and before nulling references. Preserve safe partial-installation cleanup.

Run focused lifecycle tests, the full graph plugin suite, and `:freeplane:compileJava`, with output under `$RUN_ROOT/logs`.

### Step 5: Commit and report

Run `git diff --check`, verify only the four Task 2 paths changed relative to the Task 2 base `7e381428707cb1259b5eb163451d2bf2a535fc14`, verify no editor translation or `freeplane_api` path changed, stage exactly the four paths, commit with the required subject prefix, and write the implementer report immediately after final verification and commit.

## Final successor verification

After both task reviews and any bounded fix/re-review rounds, dispatch the mandatory Frontier final reviewer over the complete branch range from merge base `b4ecf2fb2baf392c62c1add6c263d78994fb0cd2` through final `HEAD`. It must reconcile predecessor findings F-2 through F-5, the blocked provider-stop evidence, the fresh Task 1 audit, and the Task 2 shutdown implementation. Before completion, run:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" gradle :freeplane_plugin_graph:test -PTestLoggingFull
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" gradle :freeplane:compileJava
git diff --check
```

Also verify a clean worktree, required commit subjects, no editor translation changes, no forbidden API changes, and audit status `OK` for the successor run.
