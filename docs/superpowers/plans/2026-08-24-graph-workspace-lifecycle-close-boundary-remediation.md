# Graph Workspace Lifecycle Close-Boundary Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.
> Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Graph Workspace lifecycle integration probe deterministically
prove that queued projection listeners are silent after the coordinator's
logical close boundary.

**Architecture:** The delivered change is confined to
`doesNotDeliverCallbacksAfterClose`: it uses the existing map-lifecycle
listener baseline as an observable teardown barrier between handle-closing and
release of its blocked EDT callback. The test keeps the existing real
controller/map/lease path and closes the blocking latch in a `finally` path.
A temporary one-mechanism source mutant proves the strengthened probe detects a
missing per-listener closed-state guard; production source is restored before
all green verification and is never committed.

**Tech Stack:** Java 8 source compatibility, JUnit 4, AssertJ, Freeplane
headless integration fixtures, Gradle with Zulu Java 21.

## Global Constraints

- Work only in `/data/home/guest/Development/freeplane/.worktrees/graph-workspace-lifecycle-close-boundary-remediation` on branch `graph-workspace-lifecycle-close-boundary-remediation`, based on design commit `607fee2c02`.
- Use Java from `/home/henry/.sdkman/candidates/java/21.0.8-zulu`; source and target remain Java 8, UTF-8, and four-space indentation.
- Invoke `gradle`, never `gradlew` or Maven, and run Gradle commands from the intended worktree with `--no-daemon --no-parallel`.
- The delivered code diff may modify only `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceLifecycleShould.java`.
- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/GraphUpdateCoordinator.java` may be changed only for the verification mutation named below; restore it exactly to `HEAD` before any green gate, never stage it, and never commit it.
- Do not change production close ordering, APIs, resources, build configuration, test timeouts, or unrelated tests.
- Reuse existing bounded latches, joins, and `GraphWorkspaceIntegrationSupport.awaitCondition`; do not add sleeps, retry-on-failure behavior, reflection, or a new synchronization seam.
- Keep all existing post-close map mutation, resource-baseline, callback-counter, and closed-handle assertions.
- Do not edit this plan after SDD initialization; the controller pins its digest.

## Task 1: Stabilize the lifecycle close-boundary probe

**Implementer tier:** Capable

**Files:**

- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceLifecycleShould.java:233-368`
- Verification-only temporary mutation, restore before green verification: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/GraphUpdateCoordinator.java:439-464`

**Interfaces:**

- Consumes: `GraphWorkspaceIntegrationSupport.ResourceBaseline baseline`, whose package-visible `int mapLifecycleListeners` is the pre-open listener count.
- Consumes: `GraphWorkspaceIntegrationSupport.awaitCondition(BooleanSupplier condition, long timeoutMillis, String failureMessage)`, which provides the test's existing bounded condition wait.
- Consumes: `GraphUpdateCoordinator.publishProjection(GraphProjection next, long generation)`, whose per-listener monitor guard currently returns when `closed || generation != acceptedGeneration || projection != next`.
- Produces: a deterministic `GraphWorkspaceLifecycleShould.doesNotDeliverCallbacksAfterClose()` acceptance probe that proves the second pre-captured projection listener cannot run after the coordinator's logical closed state is active.

- [ ] **Step 1: Establish the inherited ordering and the non-vacuous teardown signal**

Read the current `doesNotDeliverCallbacksAfterClose` probe and confirm its
inherited sequence is: block the first projection listener, start the close
thread, wait only for command rejection, release the first listener, then join
the close thread. Confirm that it has no wait for
`freeplane.mapLifecycleListenerCount()` to return to the baseline.

Run the focused inherited test once to capture the starting behavior. Use:

```bash
cd /data/home/guest/Development/freeplane/.worktrees/graph-workspace-lifecycle-close-boundary-remediation
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
PATH=/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH \
gradle --no-daemon --no-parallel :freeplane_plugin_graph:test \
  --tests org.freeplane.plugin.graph.integration.GraphWorkspaceLifecycleShould \
  --rerun-tasks -PTestLoggingFull
```

Record whether this non-deterministic inherited probe passes or reproduces its
known stale-callback failure. Do not treat a passing inherited run as evidence
that its scheduling race is fixed.

- [ ] **Step 2: Write the strengthened deterministic acceptance probe**

In `doesNotDeliverCallbacksAfterClose`, immediately after
`GraphWorkspaceIntegrationSupport.awaitProjection(handle, 1)`, add a
non-vacuous assertion before listeners are registered:

```java
assertThat(freeplane.mapLifecycleListenerCount())
    .as("workspace map lifecycle listener before close")
    .isGreaterThan(baseline.mapLifecycleListeners);
```

Keep the existing close-thread creation and command-rejection wait. Replace the
unconditional release/join sequence with a bounded coordinator-boundary wait
and failure-safe release:

```java
try {
    GraphWorkspaceIntegrationSupport.awaitCondition(() -> {
        try {
            callbackHandle.execute(GraphCommands.viewport(Viewport.of(15, 16, 1.7,
                Collections.emptyList())));
            return false;
        }
        catch (IllegalStateException expected) {
            return true;
        }
    }, 5000L, "close did not cross the handle closing boundary");
    GraphWorkspaceIntegrationSupport.awaitCondition(
        () -> freeplane.mapLifecycleListenerCount() == baseline.mapLifecycleListeners,
        5000L, "close did not reach the graph update coordinator callback boundary");
}
finally {
    releaseFirstProjection.countDown();
    closing.join(10000L);
}
assertThat(closing.isAlive()).as("close thread completed").isFalse();
assertThat(closeFailure.get()).isNull();
```

Place this block where the inherited test currently awaits rejection, counts
down `releaseFirstProjection`, and joins `closing`. Retain every assertion
after it unchanged. The `finally` must count down the latch before joining so
a failed boundary assertion cannot strand the EDT in the first listener.

- [ ] **Step 3: Prove the new probe is falsifiable with a one-mechanism RED mutation**

With the test change present but uncommitted, temporarily modify only the
per-listener guard in `GraphUpdateCoordinator.publishProjection`:

```java
synchronized (monitor) {
    if (generation != acceptedGeneration || projection != next) {
        return;
    }
}
```

This replaces only the inner guard immediately before
`listener.onGraphProjection(next)`; leave the earlier outer guard unchanged.
Run the focused test command from Step 1. Expected result: FAIL at
`"stale projection listener after close"`, because the captured second listener
runs after the first listener is released.

Inspect the diff to confirm the only production change is this temporary inner
condition. Restore `GraphUpdateCoordinator.java` exactly from `HEAD`, verify
that it has no remaining diff, and do not stage it:

```bash
git diff -- freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/GraphUpdateCoordinator.java
git restore --source=HEAD -- freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/GraphUpdateCoordinator.java
git diff --exit-code -- freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/GraphUpdateCoordinator.java
```

- [ ] **Step 4: Run green gates on the restored production source**

Run the focused class five independent times with fresh task execution. Each
run must pass; these are repeated measurements, not retries after a failure:

```bash
cd /data/home/guest/Development/freeplane/.worktrees/graph-workspace-lifecycle-close-boundary-remediation
for run in 1 2 3 4 5; do
  JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  PATH=/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH \
  gradle --no-daemon --no-parallel :freeplane_plugin_graph:test \
    --tests org.freeplane.plugin.graph.integration.GraphWorkspaceLifecycleShould \
    --rerun-tasks -PTestLoggingFull || exit $?
done
```

Then run the clean graph-plugin gate:

```bash
cd /data/home/guest/Development/freeplane/.worktrees/graph-workspace-lifecycle-close-boundary-remediation
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
PATH=/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH \
gradle --no-daemon --no-parallel \
  :freeplane_plugin_graph:clean \
  :freeplane_plugin_graph:check \
  :freeplane_plugin_graph:test \
  :freeplane_plugin_graph:build -PTestLoggingFull
```

Finally run the repository test suite:

```bash
cd /data/home/guest/Development/freeplane/.worktrees/graph-workspace-lifecycle-close-boundary-remediation
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
PATH=/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH \
gradle --no-daemon --no-parallel test -PTestLoggingFull
```

Expected result for every green command: successful Gradle exit, no JUnit
failures or errors, and no changed production source.

- [ ] **Step 5: Inspect scope and commit the test-only remediation**

Run:

```bash
cd /data/home/guest/Development/freeplane/.worktrees/graph-workspace-lifecycle-close-boundary-remediation
git diff --check
git diff -- freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceLifecycleShould.java
git diff --exit-code -- freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/GraphUpdateCoordinator.java
git status --short
```

Require the delivered source diff to contain only the planned lifecycle test
change; the design and implementation-plan documents are already committed
planning artifacts. Stage and commit only the test fixture:

```bash
git add freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceLifecycleShould.java
git commit -m "2026-08-24-graph-workspace: Stabilize lifecycle close boundary probe"
```

In the implementer report, include the inherited focused-test observation, the
mutation's expected failure text, all five focused green results, the graph
plugin and full-suite results, the exact commit SHA, and confirmation that
`GraphUpdateCoordinator.java` has no diff.
