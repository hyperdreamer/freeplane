# Deferred Restart Claim Recovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.

**Goal:** Prevent a pause followed by restart from stranding a current `LayoutSettleLoop` run when a stale queued start, reset, or step command releases its frame claim.

**Architecture:** Keep `queueRestart()` as the only path that physically restarts the `FrameStepper`. After a successful physical restart, a new private reconciliation path claims exactly one deferred continuation only when the same run and revision remain current, running, and idle at the logical layer. It chooses `submit` for an unsubmitted request and `step` for a submitted request, leaving active frame/publication completion recovery unchanged.

**Tech Stack:** Java 8 source and bytecode, Java 21.0.8 Zulu runtime, Gradle, JUnit 4, AssertJ, `CompletableFuture`, `CompletionStage`, and the existing `ManualLifecycleDispatcher` test seam.

## Global Constraints

- Use `/home/henry/.sdkman/candidates/java/21.0.8-zulu` and `gradle`, never Maven or the Gradle wrapper.
- Work only in `/data/home/guest/Development/freeplane/.worktrees/graph-batch-d-pause-restart-recovery` on branch `graph-batch-d-pause-restart-recovery`.
- The committed baseline is `fc1c4c81646e9a2dcd5273812a02b3682405e1f9`; preserve the successor range from merge base `9248c6e227bb82fab8e6139f46db37b62174309f` through the final branch HEAD.
- The approved design is `docs/superpowers/specs/2026-08-17-deferred-restart-claim-recovery-design.md`; do not modify that specification.
- Modify only `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/LayoutSettleLoop.java` and `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/LayoutSettleLoopShould.java`. Do not modify `GraphUpdateCoordinator.java`, `LayoutWorker.java`, `ProjectionBatcher.java`, adapter fixtures, build files, plans, specifications, or SDD artifacts.
- Preserve Java 8 compatibility, all public `LayoutSettleLoop` signatures, accepted-generation ordering, one-frame-claim semantics, EDT-only canvas delivery, listener failure isolation, failure-frame handling, empty-state behavior, and the existing EDT/lifecycle/external close policy.
- `queueRestart()` remains the only lifecycle command that invokes `FrameStepper.restart()`. No `FrameStepper` method may be invoked outside the loop-owned lifecycle dispatcher.
- A stale queued command may release its claim, but must never directly schedule recovery before the worker has physically restarted. A failed physical restart must not schedule a recovery operation.
- Every regression uses `ManualLifecycleDispatcher` ordering, explicit futures, and `try/finally` cleanup. Do not use `Thread.sleep`, polling, or a timing-dependent assertion.
- The child must write the regression tests before production code, observe the intended baseline assertion failures, apply the smallest production change, prove the tests with the named one-mechanism mutant, restore exact source bytes, run focused and full graph-module tests, and commit only the two source/test files.
- Task commits start with `2026-08-10-graph-workspace:` and use imperative subjects.
- Carry predecessor findings F-1 and F-2 as resolved evidence and F-3 as the target finding into task and final review. The final reviewer must inspect the complete branch range, not only this task commit.

## Task 1: Recover a deferred claim after physical restart

**Implementer tier:** Advanced

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/LayoutSettleLoop.java:145-305, 600-692`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/LayoutSettleLoopShould.java:630-740, 1377-1780`

**Interfaces:**

- Consumes: `LayoutSettleLoop.restart()`, `queueRestart(Run, long, boolean)`, `claimIsCurrentAndRunning(Run, long)`, `submitClaimed(Run, long)`, `stepClaimed(Run, long)`, `Run.frameInFlight`, `Run.publicationInFlight`, `Run.restartRequested`, `Run.requestSubmitted`, and the `monitor`/`controlRevision` lifecycle ownership already present in `LayoutSettleLoop`.
- Consumes: package-private `LifecycleDispatcher`, `FrameStepper`, `ManualLifecycleDispatcher`, `RecordingStepper`, `ImmediateEdt`, `closeFromExternalThread(...)`, and the existing projection/frame helpers in `LayoutSettleLoopShould`.
- Produces: unchanged public `LayoutSettleLoop` API. Privately, a post-restart reconciliation that either claims no work, exactly one `submit`, or exactly one `step` after a successful physical restart.

- [ ] **Step 1: Add three deterministic, baseline-compilable F-3 regressions**

  Extend `LayoutSettleLoopShould` with the following methods. Reuse the synchronized manual dispatcher and the existing external-close helper so no test blocks on an undrained lifecycle queue.

```java
@Test
public void submitsAfterRestartWhenPauseSupersedesQueuedStartDispatch() {
    ManualLifecycleDispatcher dispatcher = new ManualLifecycleDispatcher();
    RecordingStepper stepper = new RecordingStepper(dispatcher);
    LayoutSettleLoop loop = new LayoutSettleLoop(WORKSPACE, stepper,
        new GraphGeometryEngine(), new ImmediateEdt(), dispatcher);
    GraphProjection projection = populatedProjection(481L);
    CompletableFuture<CanvasState> publication = new CompletableFuture<CanvasState>();

    try {
        CompletionStage<Void> completion = loop.start(batch(481L), projection,
            ProjectionDiff.between(emptyProjection(480L), projection), publication::complete);
        loop.pause();
        loop.restart();
        dispatcher.runAll();

        assertThat(stepper.submitCount()).isEqualTo(1);
        assertThat(stepper.stepCount()).isZero();
        assertThat(stepper.operations()).containsExactly("restart", "submit");
        assertThat(await(publication).status()).isEqualTo(OperationalStatus.IDLE);
        await(completion);
    }
    finally {
        closeFromExternalThread(loop, dispatcher, stepper);
    }
}
```

  Add `submitsAfterRestartWhenPauseSupersedesQueuedResetDispatch` using the same fixture. First settle generation `482L` through `dispatcher.runAll()`. Then call `reset(loop)`, `pause()`, and `restart()` before draining the reset command. Give the listener a `List<CanvasState>` and complete a `CompletableFuture<CanvasState>` only when the list receives its second state. After `dispatcher.runAll()`, assert `submitCount() == 2`, `stepCount() == 0`, and `operations()` is exactly `"restart", "submit", "restart", "submit"` before awaiting the second `IDLE` state. The stale reset command may skip physical `reset()`, but it must not issue a stale submit or step.

  Add a synchronized test-only switch to `RecordingStepper` so the first `submit()` can return a non-idle frame while every subsequent `step()` remains idle. For example, add `returnNonIdleFirstSubmit()` and use its flag only when constructing the first submitted `LayoutFrame`; preserve all existing tests' idle default.

```java
@Test
public void stepsAfterRestartWhenPauseSupersedesQueuedStepDispatch() {
    ManualLifecycleDispatcher dispatcher = new ManualLifecycleDispatcher();
    RecordingStepper stepper = new RecordingStepper(dispatcher);
    stepper.returnNonIdleFirstSubmit();
    LayoutSettleLoop loop = new LayoutSettleLoop(WORKSPACE, stepper,
        new GraphGeometryEngine(), new ImmediateEdt(), dispatcher);
    GraphProjection projection = populatedProjection(483L);
    CompletableFuture<CanvasState> idlePublication = new CompletableFuture<CanvasState>();

    try {
        CompletionStage<Void> completion = loop.start(batch(483L), projection,
            ProjectionDiff.between(emptyProjection(482L), projection), state -> {
                if (state.status() == OperationalStatus.IDLE) {
                    idlePublication.complete(state);
                }
            });
        dispatcher.runNext();
        dispatcher.runNext();
        dispatcher.runNext();
        loop.pause();
        loop.restart();
        dispatcher.runAll();

        assertThat(stepper.submitCount()).isEqualTo(1);
        assertThat(stepper.stepCount()).isEqualTo(1);
        assertThat(stepper.operations()).containsExactly("restart", "submit", "restart", "step");
        assertThat(await(idlePublication).status()).isEqualTo(OperationalStatus.IDLE);
        await(completion);
    }
    finally {
        closeFromExternalThread(loop, dispatcher, stepper);
    }
}
```

  The three `runNext()` calls above execute, in order, the queued initial start, its completed-frame handler, and `finishPublication()`, leaving the first follow-up step queued but undrained. Assertions must be against completion/publication and recorded physical operations, not merely a fake method count. The expected unfixed result is an absent replacement operation: the relevant assertion expects `1` and observes `0`, before any `await(completion)` timeout is reached.

- [ ] **Step 2: Run and record the red phase**

  Run the three new tests against unchanged production code:

```bash
export JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu
export PATH="$JAVA_HOME/bin:$PATH"
gradle :freeplane_plugin_graph:test \
  --tests '*LayoutSettleLoopShould.submitsAfterRestartWhenPauseSupersedesQueuedStartDispatch' \
  --tests '*LayoutSettleLoopShould.submitsAfterRestartWhenPauseSupersedesQueuedResetDispatch' \
  --tests '*LayoutSettleLoopShould.stepsAfterRestartWhenPauseSupersedesQueuedStepDispatch' \
  -PTestLoggingFull --rerun-tasks
```

  Require behavioral assertion failures for all three methods because the baseline physically restarts but performs no replacement `submit` or `step`. A compilation error, a test-fixture deadlock, a timeout before the explicit count/operation assertion, or an unrelated test failure is not valid red evidence. Record the exact observed failure lines in the implementer report.

- [ ] **Step 3: Add the smallest post-restart reconciliation**

  In `queueRestart()`, retain the current pre-restart current/revision check, the physical `worker.restart()` call, the existing `shouldStep` claim path, and its existing restart-failure behavior. For the `shouldStep == false` path only, call `resumeAfterRestart(run, revision)` after `worker.restart()` returns successfully:

```java
if (shouldStep) {
    if (hasSubmittedRequest(run)) {
        stepClaimed(run, revision);
    }
    else {
        submitClaimed(run, revision);
    }
}
else {
    resumeAfterRestart(run, revision);
}
```

  Add this private helper adjacent to `queueRestart()`:

```java
private void resumeAfterRestart(final Run run, final long revision) {
    final boolean submit;
    synchronized (monitor) {
        if (!isCurrentAndRunningLocked(run, revision) || !run.restartRequested
                || run.frameInFlight || run.publicationInFlight) {
            return;
        }
        run.restartRequested = false;
        claimFrameLocked(run, revision);
        submit = !run.requestSubmitted;
    }
    if (submit) {
        submitClaimed(run, revision);
    }
    else {
        stepClaimed(run, revision);
    }
}
```

  Do not schedule this helper from `releaseClaimLocked()`, `resumeAfterDiscardLocked()`, or any completion callback. Do not claim a second frame when the `restart()` caller already supplied `shouldStep == true`. If physical `worker.restart()` throws, preserve the current behavior and do not invoke the new helper. Let `submitClaimed()` and `stepClaimed()` retain their existing revalidation and stale-claim release behavior.

- [ ] **Step 4: Run focused green verification and prove the regressions are falsifiable**

  Re-run the Step 2 command. All three new tests must pass, including their completion/publication assertions and their exact `submit` versus `step` operation assertions.

  Then back up `LayoutSettleLoop.java` outside the repository, record its SHA-256, and temporarily make only the new post-restart reconciliation helper return without claiming or dispatching work. Run the same three test methods. They must fail because each expected replacement operation remains absent; no unrelated test must fail. Restore the backup byte-for-byte, verify the SHA-256 is identical to the pre-mutation value, and rerun the Step 2 command successfully. Do not commit the mutant or backup.

- [ ] **Step 5: Run compatibility verification and commit the exact allowlist**

  Run focused lifecycle controls, then the entire graph plugin test suite:

```bash
export JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu
export PATH="$JAVA_HOME/bin:$PATH"
gradle :freeplane_plugin_graph:test \
  --tests '*LayoutSettleLoopShould' \
  --tests '*LayoutWorkerShould' \
  --tests '*GraphUpdateCoordinatorShould' \
  -PTestLoggingFull --rerun-tasks
gradle :freeplane_plugin_graph:test -PTestLoggingFull --rerun-tasks
node --input-type=module <<'NODE'
import { readFileSync, readdirSync } from 'node:fs';
const directory = 'freeplane_plugin_graph/build/test-results/test';
const totals = { tests: 0, failures: 0, errors: 0, skipped: 0 };
for (const name of readdirSync(directory).filter((file) => /^TEST-.*\.xml$/.test(file))) {
  const text = readFileSync(`${directory}/${name}`, 'utf8');
  const match = text.match(/<testsuite\b[^>]*>/);
  if (match === null) continue;
  for (const key of Object.keys(totals)) {
    const value = match[0].match(new RegExp(`${key}="(\\d+)"`));
    totals[key] += value === null ? 0 : Number(value[1]);
  }
}
console.log(JSON.stringify(totals));
if (totals.failures !== 0 || totals.errors !== 0) process.exit(1);
NODE

git diff --check
test -z "$(git diff --cached --name-only)"
git add -- \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/LayoutSettleLoop.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/LayoutSettleLoopShould.java
test "$(git diff --cached --name-only | wc -l)" -eq 2
git diff --cached --name-only
git diff --cached --check
git commit -m "2026-08-10-graph-workspace: Recover deferred restart claims"
git show --stat --oneline HEAD
```

  Require zero JUnit XML failures and errors. Confirm the staged paths equal the two allowlisted files exactly, the plan/spec/SDD artifacts are unstaged, and the implementer report includes the red evidence, green commands, expected mutant failures, XML totals, and commit SHA.
