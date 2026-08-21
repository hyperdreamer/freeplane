# Graph Workspace Batch H Scope-Expanded Successor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.

**Goal:** Correct the carried graph dimming state bug and complete deterministic graph extension shutdown under an explicitly expanded, reviewable scope.

**Architecture:** Keep persisted display preferences separate from immutable transient paint interaction state. Reuse the existing `GraphPainter` two-input dimming gate, then add controller-owned shutdown that coordinates session resource teardown and extension lifecycle cleanup through the existing graph workspace seams.

**Tech Stack:** Java 8-compatible Freeplane plugin code, Swing/EDT lifecycle boundaries, JUnit 4, AssertJ, Mockito, Gradle, and the deterministic subagent-driven-development controller.

## Global Constraints

- Continue in `/data/home/guest/Development/freeplane/.worktrees/graph-workspace-batch-h-ui-shell`; do not create a worktree.
- Use `JAVA_HOME=$HOME/.sdkman/candidates/java/21.0.8-zulu` with `gradle`; never Maven or the Gradle wrapper.
- Preserve Java 8 source/target compatibility, UTF-8 source encoding, four-space Java indentation, and repository testing conventions.
- The predecessor run `.superpowers/sdd/2026-08-20-graph-workspace-batch-h-remediation` remains terminal `DISPATCH_MISMATCH_BLOCKED`; preserve its state, ledger, pointer, envelope, transcript, and missing Task 2 report unchanged.
- The prior provider-stop successor `.superpowers/sdd/2026-08-21-graph-workspace-batch-h-provider-stop-successor` remains terminal `TASK_BLOCKED`; preserve its state, ledger, review report, finding ledger, pointer, envelopes, and transcript unchanged.
- Finding `F-1` is carried as a load-bearing obligation and must be corrected in the two explicitly allowlisted presentation paths before Task 2 proceeds. Carried findings `F-2`, `F-3`, `F-4`, and `F-5` remain final-review obligations.
- The only source paths this plan may modify are `GraphCanvas.java`, `GraphCanvasPaintShould.java`, `DefaultGraphWorkspaceController.java`, `GraphModeExtension.java`, `DefaultGraphWorkspaceControllerShould.java`, and `GraphPluginIntegrationShould.java` under `freeplane_plugin_graph`.
- Do not modify `freeplane_api`, `MapView`, editor translations, or unrelated graph group/map-actor behavior. The plugin neither subclasses nor replaces `MapView`.
- Keep the graph window modeless and headless-testable; tests must not call `setVisible`.
- Keep `GraphPaintState` transient and immutable. The persisted dim preference is a rendering gate, not interaction state. Workspace undo/redo and source-map undo remain distinct.
- Swing communicates through `GraphWorkspaceHandle`, `GraphWorkspaceViewBinding`, and `WorkspaceCloseController`; `MapModel` and `NodeModel` reads remain EDT-only.
- Every source-changing commit uses an imperative subject beginning `2026-08-10-graph-workspace:`.
- Every dispatch persists a complete immutable role envelope and short pointer beneath the fresh run root, byte-compares the pointer before spawn, records the returned session immediately, and admits only a completed transcript whose first user message matches the stored pointer byte-for-byte.
- Redirect verbose verification output beneath the active run root. Never hand-edit `state.json` or `progress.md`; `state.json` is canonical and `progress.md` is derived.

## Task 1: Correct the carried dimming state boundary

**Implementer tier:** Capable

**Files:**
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphCanvas.java:68-76,223-231`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java:1-end`

**Interfaces:**
- Consumes the existing `GraphCanvas.setPaintState(GraphPaintState)`, `GraphCanvas.setDimUnrelated(boolean)`, and `GraphPainter.paint(..., boolean showArrowheads, boolean dimUnrelatedEnabled)` seam. `GraphPainter` already computes effective dimming from both `GraphPaintState.dimUnrelated()` and the persisted option; do not modify `GraphPainter.java`.
- Produces a canvas that preserves transient `GraphPaintState` when either display preference changes, while the existing painter gate still enables dimming only when both the transient trigger and persisted option are true.

### Step 1: Add falsifiable offscreen regression tests

- [ ] Inspect the existing `GraphCanvasPaintShould` fixture, image, and pixel-comparison helpers. Add one test using the real `GraphCanvas` path with the default enabled option and `GraphPaintState.empty()`; assert a visible node's rendered color is the undimmed theme color or that its image matches the explicit no-dimming baseline.
- [ ] Add one test with an active transient dim trigger and the persisted option enabled; assert an unrelated visible node is dimmed and the related endpoint is not.
- [ ] Add one test with the same active trigger and the persisted option disabled; assert the unrelated node is not dimmed. Keep all tests offscreen and do not call `setVisible`.

### Step 2: Run the required red gate

- [ ] Run the new focused class before changing production code:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" gradle :freeplane_plugin_graph:test \
  --tests '*GraphCanvasPaintShould' -PTestLoggingFull \
  >"$RUN_ROOT/logs/task-1-red.log" 2>&1
red_status=$?
tail -n 80 "$RUN_ROOT/logs/task-1-red.log"
test "$red_status" -ne 0
```

- [ ] Confirm the failure is the uncorrected persisted/transient dimming behavior, not a fixture, compilation, or headless-environment error. Do not change production code before this gate.

### Step 3: Apply the minimal production correction

- [ ] Change `GraphCanvas.setPaintState` to assign the supplied immutable `GraphPaintState` unchanged; do not gate or rewrite its transient `dimUnrelated` flag based on the persisted preference.
- [ ] Change `GraphCanvas.setDimUnrelated` to update only the persisted `dimUnrelated` option and request repaint; do not call `paintState.withDimUnrelated(...)`.
- [ ] Leave the existing `GraphPainter` overload and its `paintState.dimUnrelated() && dimUnrelatedEnabled` calculation unchanged. Run the focused presentation tests and confirm the three regression cases pass.

### Step 4: Verify scope, commit, and report

- [ ] Run `JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" gradle :freeplane_plugin_graph:test --tests '*GraphCanvasPaintShould' --tests '*GraphWorkspacePresentationShould' --tests '*GraphWorkspaceWindowModelShould' -PTestLoggingFull`, redirecting the full log below the run root.
- [ ] Run `git diff --check`, verify the source diff from the Task 1 base contains exactly `GraphCanvas.java` and `GraphCanvasPaintShould.java`, and verify no `setVisible` call was added.
- [ ] Stage exactly those two paths and commit with an imperative subject beginning `2026-08-10-graph-workspace:`. Inspect `git status --porcelain` and the commit before writing the implementer report immediately after the final verification action.

## Task 2: Implement deterministic extension shutdown

**Implementer tier:** Capable

**Files:**
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/DefaultGraphWorkspaceController.java:250-760`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/GraphModeExtension.java:1-120`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/DefaultGraphWorkspaceControllerShould.java:1-end`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphPluginIntegrationShould.java:1-end`

**Interfaces:**
- Consumes `DefaultGraphWorkspaceController.open`, `openSessions`, `WorkspaceSessionRegistry`, `Session`, `SessionResources`, existing teardown helpers, `GraphWorkspaceView.close`, `GraphModeExtension.close`, and the Task 1 presentation binding.
- Produces an idempotent `DefaultGraphWorkspaceController.shutdown()` or equivalently explicit package-visible lifecycle method used by `GraphModeExtension`; after successful return no owned session remains registered, and extension shutdown occurs before graph references, extensions, or actions are cleared.

### Step 1: Write shutdown regression tests first

- [ ] Extend `DefaultGraphWorkspaceControllerShould` with injected resources and fake views proving shutdown discards/closes every owned session resource, closes each view, unregisters each session, makes each handle unusable, handles zero sessions, and performs no teardown on repeated calls.
- [ ] Add a failure test with at least two live sessions where one cleanup operation throws; assert later session cleanup still runs and the returned failure aggregates the cleanup failure.
- [ ] Extend `GraphPluginIntegrationShould` to prove `GraphModeExtension.close()` invokes controller shutdown before removing graph extension/action references, including safe partial-installation cleanup. Keep tests headless and never call `setVisible`.

### Step 2: Run the focused red gate

- [ ] Run this command before modifying production code:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" gradle :freeplane_plugin_graph:test \
  --tests '*DefaultGraphWorkspaceControllerShould' \
  --tests '*GraphPluginIntegrationShould' -PTestLoggingFull \
  >"$RUN_ROOT/logs/task-2-red.log" 2>&1
red_status=$?
tail -n 60 "$RUN_ROOT/logs/task-2-red.log"
test "$red_status" -ne 0
```

- [ ] Confirm the red result identifies the missing shutdown seam or extension lifecycle call rather than a test setup typo. Do not modify production code before this gate.

### Step 3: Implement idempotent controller shutdown

- [ ] Under the controller monitor, atomically mark shutdown and snapshot every live session; reject new `open` calls once shutdown begins.
- [ ] For each snapshot session, perform discard-close semantics without a user save dialog: transition closing, close the store and status publisher in the established safe order, close updates/maps/leases/scheduler off the EDT, close the view on the EDT, mark the handle closed, remove the session from `openSessions`, and unregister its session ID.
- [ ] Wait for EDT cleanup before returning, continue cleanup for later sessions after failures, aggregate failures, and make repeated shutdown calls no-ops. Leave user-triggered `closeSession` save/retry/discard behavior unchanged before shutdown.

### Step 4: Wire extension lifecycle and verify

- [ ] Keep the concrete controller for lifecycle shutdown while passing the forwarding controller to the view factory and open action. Make `GraphModeExtension.close()` invoke shutdown before removing graph extensions/actions and before nulling references. Preserve safe partial-installation cleanup.
- [ ] Run the focused lifecycle suites, the full graph plugin suite, and `:freeplane:compileJava`, redirecting complete logs below `$RUN_ROOT/logs` and inspecting bounded tails.

### Step 5: Verify scope, commit, and report

- [ ] Run `git diff --check`, verify only the four Task 2 paths changed relative to the Task 2 base (the approved Task 1 commit), verify no editor translation or `freeplane_api` path changed, and inspect the complete diff.
- [ ] Stage exactly the four Task 2 paths and commit with the required `2026-08-10-graph-workspace:` subject. Inspect status and commit contents, then write the implementer report immediately after the final verification and commit.

## Final successor verification

After both task reviews and any bounded fix/re-review rounds, dispatch the mandatory Frontier final reviewer over the complete branch range from merge base `b4ecf2fb2baf392c62c1add6c263d78994fb0cd2` through final `HEAD`. The final review must inspect the fresh `F-1` correction, reconcile predecessor findings `F-2` through `F-5`, confirm the two terminal predecessor/successor runs were preserved, and verify all source-scope and lifecycle constraints.

Before completion, run:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" gradle :freeplane_plugin_graph:test -PTestLoggingFull
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" gradle :freeplane:compileJava
git diff --check
```

Also verify a clean worktree, required commit subjects, no editor translation changes, no forbidden API changes, no `setVisible` calls in graph tests, and successor audit status `OK`.
