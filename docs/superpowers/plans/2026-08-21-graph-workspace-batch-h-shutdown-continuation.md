# Graph Workspace Shutdown Continuation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.

**Goal:** Complete the Freeplane Graph Workspace shutdown correction after an interrupted fixer, proving that extension shutdown is deadlock-free, concurrent callers observe one teardown result, and cleanup failures and lifecycle races are covered by falsifiable tests.

**Architecture:** Keep shutdown ownership in `DefaultGraphWorkspaceController` and let `GraphModeExtension.close()` invoke that concrete controller before clearing graph actions and extensions. Coordinate asynchronous user-close completion with shutdown without waiting on the EDT, while making all shutdown callers observe one completion outcome and continuing cleanup across every owned session. Preserve the existing modeless, headless-testable UI and user save/retry/discard close path.

**Tech Stack:** Java 8-compatible Freeplane plugin code, Swing EDT coordination, JUnit 4, AssertJ, Mockito, Gradle with Zulu Java 21 for verification.

## Global Constraints

- Continue in `/data/home/guest/Development/freeplane/.worktrees/graph-workspace-batch-h-ui-shell`; do not create a worktree.
- Use `JAVA_HOME=$HOME/.sdkman/candidates/java/21.0.8-zulu` with `gradle`; never Maven or the Gradle wrapper.
- Preserve Java 8 source/target compatibility, UTF-8 source encoding, four-space Java indentation, and repository testing conventions.
- The predecessor run `.superpowers/sdd/2026-08-20-graph-workspace-batch-h-remediation` remains terminal `DISPATCH_MISMATCH_BLOCKED` and the prior provider-stop successor `.superpowers/sdd/2026-08-21-graph-workspace-batch-h-provider-stop-successor` remains terminal `TASK_BLOCKED`; preserve both run roots unchanged.
- The immediately preceding scope-expanded successor `.superpowers/sdd/2026-08-21-graph-workspace-batch-h-scope-expanded-successor` is terminal `TASK_BLOCKED` because its fixer stopped after an uncommitted partial edit. Preserve its state, ledger, events, prompts, reports, and transcript unchanged.
- The current worktree intentionally carries an uncommitted partial correction in `DefaultGraphWorkspaceController.java` and `DefaultGraphWorkspaceControllerShould.java`. Inspect and retain that work as input; do not reset, discard, stash, or overwrite it merely to obtain a clean baseline. Its last attempted correction moved a shutdown wait decision outside the controller monitor after a focused review caught a self-introduced nested-monitor deadlock, but it was never verified or committed.
- The only source paths this plan may modify are `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/DefaultGraphWorkspaceController.java`, `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/GraphModeExtension.java`, `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/DefaultGraphWorkspaceControllerShould.java`, and `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphPluginIntegrationShould.java`.
- Do not modify `freeplane_api`, `MapView`, editor translations, `GraphCanvas.java`, `GraphCanvasPaintShould.java`, or unrelated graph group/map-actor behavior.
- Keep the graph window modeless and headless-testable; tests must not call `setVisible`. Keep `GraphPaintState` transient and immutable, preserve the persisted dimming gate, keep workspace undo/redo distinct from source-map undo, and keep `MapModel`/`NodeModel` reads EDT-only.
- Shutdown must be idempotent, reject opens after shutdown begins, attempt cleanup for every owned session, aggregate failures, and preserve user save/retry/discard behavior. A shutdown call made on the EDT during an in-progress asynchronous user close must return without deadlocking the EDT. Concurrent shutdown callers must await the same completed teardown result, including its failure.
- Keep the concrete controller for lifecycle shutdown while passing the forwarding controller to `SwingGraphWorkspaceViewFactory` and `OpenGraphWorkspaceAction`. `GraphModeExtension.close()` must shut down before removing graph extensions/actions and before nulling references, while remaining safe for partial installation and repeated close.
- Every source-changing commit must have an imperative subject beginning `2026-08-10-graph-workspace:`. The implementer must commit only the four allowlisted source/test paths.
- Redirect complete verification output below the active run root. Never hand-edit `state.json` or `progress.md`; `state.json` is canonical and `progress.md` is derived.

## Task 1: Complete deterministic shutdown correction

**Implementer tier:** Capable

**Files:**
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/DefaultGraphWorkspaceController.java`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/GraphModeExtension.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/DefaultGraphWorkspaceControllerShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphPluginIntegrationShould.java`

**Interfaces:**
- Consumes the existing `DefaultGraphWorkspaceController.open`, `openSessions`, `WorkspaceSessionRegistry`, `Session`, `SessionResources`, `GraphWorkspaceView.close`, `closeSession`, `GraphModeExtension.close`, and the forwarding-controller construction already present at `c522fee6fc788355d18fa551ed7bc0cae54c2673`.
- Produces an idempotent controller shutdown operation whose successful return means every session owned when shutdown began has completed discard cleanup, has a closed handle and view, and is no longer registered; a failure is observed by every caller after all owned sessions have been attempted.

### Finding obligations

The independent review pinned these load-bearing findings in the prior run and all must be resolved in this task:

- `F-1` (`DefaultGraphWorkspaceController.java:858` in `c522fee6fc`): `Session.beginShutdown()` waited unconditionally while a user close was `CLOSING`, but the existing EDT close path posts `finishClose()` to the EDT. A shutdown invoked on the EDT in that interval must coordinate with the in-progress close without waiting for the callback on the same EDT.
- `F-2` (`DefaultGraphWorkspaceController.java:327` in `c522fee6fc`): a second caller returned immediately when `shutdownStarted` was true. Distinguish in-progress from completed shutdown and make later callers await the single teardown result, including the first failure, without duplicate cleanup.
- `F-3` (`DefaultGraphWorkspaceControllerShould.java:927` in `c522fee6fc`): one injected failure and no suppressed-failure assertion or deterministic opening/EDT-close race left the behavior non-falsifiable. Add at least two cleanup failures, assert primary and suppressed failures, and add deterministic barriers for an opening session and an EDT-initiated user close racing shutdown.

### Required implementation and tests

- [ ] Read the prior task brief, implementer report, review report, fix package, and the current dirty diff before editing. Preserve the existing partial changes only where they satisfy the contracts; do not repeat the failed unconditional `beginShutdown()` wait or the immediate-return `shutdownStarted` guard.
- [ ] Establish or retain red evidence for each newly strengthened regression. A focused test must fail when the mechanism under test is removed or disabled, not merely pass because the existing asynchronous close/coalescing path makes the assertion vacuous. Restore the intended implementation before the green run.
- [ ] Under the controller monitor, atomically mark shutdown started, reject new opens, and snapshot every currently owned session. Represent shutdown-in-progress separately from completed shutdown so exactly one teardown owner performs cleanup and every other caller waits outside the monitor for the same completion signal and then returns or throws the same stored failure.
- [ ] Ensure a shutdown call made on the EDT never waits for an EDT callback that is required to complete the current user close. Coordinate with the existing single-owner close transition by arranging continuation or waiting off the EDT, while retaining the existing user-triggered save/retry/discard behavior for `closeSession(session, discard)`.
- [ ] For every snapshotted session, attempt discard-close cleanup in the established safe order: transition closing, close store/status resources, perform off-EDT updates/maps/leases/scheduler cleanup, close the view on the EDT, mark the handle closed, remove the session from `openSessions`, and unregister its session ID. Continue through all sessions after any failure and aggregate the primary failure plus later failures as suppressed exceptions. Do not perform duplicate teardown on repeated shutdown calls.
- [ ] Preserve and verify the extension ordering: `GraphModeExtension.close()` invokes concrete-controller shutdown before removing graph extensions/actions and before clearing references, and partial installation or repeated close remains safe. Do not call `setVisible` in tests.
- [ ] Add a multi-session failure test that injects at least two independent cleanup failures, asserts cleanup of later sessions still occurs, identifies the expected primary failure, and asserts the later failure appears in `getSuppressed()`.
- [ ] Add a deterministic concurrent opening/shutdown test using a barrier or latch around session admission/cleanup. Assert that an open racing shutdown is rejected or ends in a closed, unregistered handle and that shutdown does not return with an owned session still registered.
- [ ] Add a deterministic EDT-initiated user-close/shutdown race test. Start the existing asynchronous user close, block at a known completion barrier, invoke shutdown from the EDT, release the close barrier, and assert the EDT returns, teardown completes, the registry is empty, and no handle remains usable. Keep the test headless and bounded; do not rely on sleeps or wall-clock timing.
- [ ] Run the focused suites with complete output redirected under the active run root:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" gradle :freeplane_plugin_graph:test \
  --tests '*DefaultGraphWorkspaceControllerShould' \
  --tests '*GraphPluginIntegrationShould' -PTestLoggingFull \
  >"$RUN_ROOT/logs/shutdown-focused.log" 2>&1
```

- [ ] Run the full graph-plugin suite and app compilation with the same `JAVA_HOME`, redirecting complete logs below `$RUN_ROOT/logs`, and inspect the bounded tails:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" gradle :freeplane_plugin_graph:test -PTestLoggingFull \
  >"$RUN_ROOT/logs/graph-plugin-full.log" 2>&1
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" gradle :freeplane:compileJava \
  >"$RUN_ROOT/logs/freeplane-compile.log" 2>&1
```

- [ ] Run `git diff --check`, verify the complete diff from the continuation base contains only the four allowlisted source/test paths, verify no forbidden path or `setVisible` call was introduced, inspect the final commit contents, and commit only those four paths with an imperative subject beginning `2026-08-10-graph-workspace:`.
- [ ] Immediately after the final verification and commit, write the implementer report at the controller-provided report path using the required `STATUS`, `CHANGES`, `TESTS`, and `COMMIT` fields. Do not make another tool call or send narrative before writing that report.
