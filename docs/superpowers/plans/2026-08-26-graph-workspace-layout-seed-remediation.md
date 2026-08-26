# Graph Workspace Layout Seed Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.
> Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Retain a uniform 50.0-unit Graph Workspace initial layout spread while making the seed-sensitive GraphStream force tests deterministic and behavior-focused.

**Architecture:** Replace the size-dependent initial-position envelope with one `INITIAL_POSITION_SPREAD = 50.0` production constant. Keep the solver, identity-derived random seeds, topology, and public layout API unchanged. Arrange force-test geometry through two public `LayoutEngine.apply(LayoutRequest)` calls with different active-pin lists, then compare one-step behavior from known coordinates; compare hierarchy-link distance deltas rather than seed-dependent absolute distances.

**Tech Stack:** Java 8 source target, JUnit 4, AssertJ, Gradle, GraphStream.

**File Structure:**

- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/GraphStreamLayoutEngine.java`: owns deterministic particle initialization and the uniform initial-position spread.
- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/TypedForcesShould.java`: owns GraphStream layout behavior tests using only `LayoutEngine`, `LayoutRequest`, `LayoutFrame`, and `PinProjection`.

## Global Constraints

- Work only in `/data/home/guest/Development/freeplane/.worktrees/graph-layout-seed-remediation` on branch `graph-layout-seed-remediation`, based on commit `40c619ecdf9086c40f84e94c212671ac1d91d0b1` plus the committed approved design document.
- The deliverable code diff may modify only `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/GraphStreamLayoutEngine.java` and `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/TypedForcesShould.java`.
- Keep one production policy: `private static final double INITIAL_POSITION_SPREAD = 50.0;`; remove the threshold and both old size-dependent spread constants and do not retain a compatibility fallback.
- Preserve SHA-256 identity encoding, deterministic random derivation, particle ordering, links, pins, solver quality, prominence radii, reset behavior, and all public layout interfaces.
- Use public `LayoutEngine`, `LayoutRequest`, `LayoutFrame`, and `PinProjection` in tests; do not access GraphStream particle internals and do not add a test-only production seam.
- Use `JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu` for every Gradle invocation.
- Follow TDD: the small-workspace spread regression must fail under the old 0.002 spread before changing production code.
- Do not use predecessor reports, prompts, or transcripts as correctness evidence; establish all test and mutation evidence in this worktree or disposable worktrees created from it.
- Do not change SDD state, audit artifacts, documentation, dependencies, projection behavior, canvas behavior, persistence, `TypedNodeParticle`, or `TypedSpringBox` during the task.

## Task 1: Stabilize Uniform Initial Spread Force Tests

**Implementer tier:** Standard

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/GraphStreamLayoutEngine.java:38-336`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/TypedForcesShould.java:44-411`

**Interfaces:**

- Consumes: `LayoutEngine.apply(LayoutRequest): LayoutFrame`, `LayoutEngine.step(): LayoutFrame`, and `PinProjection.active(PinRecord, ProjectedNodeKey)`; an `apply` with the same projection but a changed pin list must resynchronize the current particle positions and frozen state through the public interface.
- Produces: a `GraphStreamLayoutEngine` implementation whose newly created particles always use the same 50.0-unit initial spread, independent of `GraphProjection.projectedNodeCount()`.
- Produces: `TypedForcesShould` regressions that prove small projections start spread beyond one world unit, an unpinned positioned particle moves, prominence changes positioned-node separation, and a hierarchy attraction changes anchor separation relative to a peer topology.

- [ ] **Step 1: Write the red regression and deterministic force fixtures in `TypedForcesShould`**

Add this no-step small-workspace regression after `deriveDistinctDeterministicSeedsForDifferentWorkspaces`:

```java
@Test
public void spreadSmallWorkspaceNodePositionsBeyondOneWorldUnitBeforeStepping() {
    GraphProjection projection = baseline(1);

    try (LayoutEngine engine = GraphStreamLayoutFactory.create(LayoutCalibration.spikeDefaults())) {
        LayoutFrame frame = engine.apply(request(WORKSPACE_ONE, projection, projection,
            Collections.<PinProjection>emptyList()));

        assertThat(greatestPairwiseDistance(frame.positions().nodes().values())).isGreaterThan(1.0);
    }
}
```

Replace `moveAnUnpinnedParticleThroughTheGraphStreamSolver` with a public two-apply arrangement. First pin both particles at `(0.0, 0.0)` and `(20.0, 0.0)`, then apply the identical projection while retaining only the second pin before stepping:

```java
List<PinProjection> positioningPins = Arrays.asList(pin(firstNode.key(), 0.0, 0.0),
    pin(secondNode.key(), 20.0, 0.0));
List<PinProjection> activePins = Collections.singletonList(pin(secondNode.key(), 20.0, 0.0));

try (LayoutEngine engine = GraphStreamLayoutFactory.create(LayoutCalibration.spikeDefaults())) {
    engine.apply(request(WORKSPACE_ONE, projection, projection, positioningPins));
    LayoutFrame before = engine.apply(request(WORKSPACE_ONE, projection, projection, activePins));
    LayoutFrame after = engine.step();

    assertThat(after.failed()).isFalse();
    assertThat(distance(before.positions().nodes().get(firstNode.key()),
        after.positions().nodes().get(firstNode.key()))).isGreaterThan(0.0);
}
```

Replace `increaseSeparationForHigherProminence` so the source is initially pinned at `(24.0, 0.0)` and then released, while the neighbor remains pinned at `(0.0, 0.0)`. For the high-prominence topology, retain the existing target pins at `(100.0, 100.0)` and `(100.0, -100.0)`. Call a new helper with both the temporary positioning pins and the pins retained for the solver step:

```java
LayoutFrame low = frameAfterPositioningAndOneStep(WORKSPACE_ONE, lowProminence,
    Arrays.asList(pin(source, 24.0, 0.0), pin(neighbor, 0.0, 0.0)),
    Collections.singletonList(pin(neighbor, 0.0, 0.0)));
LayoutFrame high = frameAfterPositioningAndOneStep(WORKSPACE_ONE, highProminence,
    Arrays.asList(pin(source, 24.0, 0.0), pin(neighbor, 0.0, 0.0),
        pin(key(MAP_ONE, "target-one"), 100.0, 100.0),
        pin(key(MAP_ONE, "target-two"), 100.0, -100.0)),
    Arrays.asList(pin(neighbor, 0.0, 0.0), pin(key(MAP_ONE, "target-one"), 100.0, 100.0),
        pin(key(MAP_ONE, "target-two"), 100.0, -100.0)));

assertThat(distance(high.positions().nodes().get(source), high.positions().nodes().get(neighbor)))
    .isGreaterThan(distance(low.positions().nodes().get(source), low.positions().nodes().get(neighbor)));
```

Rename `increaseAnchorSeparationWhenEnclosuresHaveAHierarchyLink` to `reduceAnchorDistanceChangeWhenEnclosuresHaveAHierarchyLink`. Change `hierarchyProjection` so both variants contain `hierarchy-parent-node` and `hierarchy-child-node` as their respective enclosure direct nodes. Pin those nodes at the same two coordinates in both variants, capture each anchor distance before and after one solver step, and compare deltas:

```java
List<PinProjection> pins = Arrays.asList(pin(key(MAP_ONE, "hierarchy-parent-node"), 0.0, 0.0),
    pin(key(MAP_ONE, "hierarchy-child-node"), 24.0, 0.0));
double nestedChange = anchorDistanceChangeAfterOneStep(WORKSPACE_ONE, nested, pins,
    hierarchyParentHull(), hierarchyChildHull());
double peerChange = anchorDistanceChangeAfterOneStep(WORKSPACE_ONE, peers, pins,
    hierarchyParentHull(), hierarchyChildHull());

assertThat(nestedChange).isLessThan(peerChange);
```

Add only these private test helpers:

```java
private static LayoutFrame frameAfterPositioningAndOneStep(WorkspaceId workspace, GraphProjection projection,
        List<PinProjection> positioningPins, List<PinProjection> activePins) {
    try (LayoutEngine engine = GraphStreamLayoutFactory.create(LayoutCalibration.spikeDefaults())) {
        engine.apply(request(workspace, projection, projection, positioningPins));
        engine.apply(request(workspace, projection, projection, activePins));
        return engine.step();
    }
}

private static double anchorDistanceChangeAfterOneStep(WorkspaceId workspace, GraphProjection projection,
        List<PinProjection> pins, EnclosureHullKey first, EnclosureHullKey second) {
    try (LayoutEngine engine = GraphStreamLayoutFactory.create(LayoutCalibration.spikeDefaults())) {
        LayoutFrame before = engine.apply(request(workspace, projection, projection, pins));
        LayoutFrame after = engine.step();
        return distance(after.positions().anchors().get(first), after.positions().anchors().get(second))
            - distance(before.positions().anchors().get(first), before.positions().anchors().get(second));
    }
}

private static double greatestPairwiseDistance(Iterable<LayoutPoint> points) {
    List<LayoutPoint> values = new ArrayList<LayoutPoint>();
    for (LayoutPoint point : points) {
        values.add(point);
    }
    double greatest = 0.0;
    for (int first = 0; first < values.size(); first++) {
        for (int second = first + 1; second < values.size(); second++) {
            greatest = Math.max(greatest, distance(values.get(first), values.get(second)));
        }
    }
    return greatest;
}
```

For `hierarchyProjection`, retain the existing parent/child hull IDs and hierarchy toggle; add the two direct nodes to the returned projection and assign each to the matching enclosure's `directNodes` list. The nested and peer variants must have the same particle identities, pins, and starting coordinates; only the child `parentHull` presence differs.

- [ ] **Step 2: Run the red test suite and inspect the failure**

Run:

```bash
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test --tests '*TypedForcesShould' -PTestLoggingFull
```

Expected: the new `spreadSmallWorkspaceNodePositionsBeyondOneWorldUnitBeforeStepping` assertion fails because the current 0.002-unit small-workspace envelope cannot produce a pairwise node distance greater than `1.0`. The altered public pin fixtures must compile and their assertions must not be the reason the suite is red. Do not change production code until this failure has been observed and recorded.

- [ ] **Step 3: Replace the production size-dependent spread with one uniform constant**

In `GraphStreamLayoutEngine`, replace the three old declarations:

```java
private static final int LARGE_WORKSPACE_NODE_THRESHOLD = 1000;
private static final double DEFAULT_INITIAL_POSITION_SPREAD = 0.002;
private static final double LARGE_WORKSPACE_INITIAL_POSITION_SPREAD = 50.0;
```

with exactly:

```java
private static final double INITIAL_POSITION_SPREAD = 50.0;
```

In `synchronize`, pass `INITIAL_POSITION_SPREAD` directly to `initialPosition(...)` when a particle is first created. Delete the local `initialPositionSpread` variable and delete `initialPositionSpread(final GraphProjection projection)` entirely. Do not change `initialPosition`, `seedBytes`, SHA-256 derivation, or any force calculation.

- [ ] **Step 4: Run the focused green suite**

Run:

```bash
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test --tests '*TypedForcesShould' -PTestLoggingFull
```

Expected: all `TypedForcesShould` tests pass, including the new small-workspace spread regression and the three deterministic force assertions.

- [ ] **Step 5: Prove the regressions are falsifiable in disposable worktrees**

Create disposable detached worktrees from the current task HEAD plus uncommitted task diff by committing no code first; instead, make a temporary patch of the active two-file diff, create a detached worktree at the task base, apply the patch there, and run each mutation only in a disposable copy. Never mutate the active task worktree for this step.

Run these four probes independently, resetting or recreating the disposable worktree after each probe:

1. Change `INITIAL_POSITION_SPREAD` from `50.0` to `0.002`; run only `spreadSmallWorkspaceNodePositionsBeyondOneWorldUnitBeforeStepping`; it must fail.
2. Restore the unpinned-particle test's old single-apply empty-pin setup (`before = engine.apply(...emptyList())`, then `after = engine.step()`) and run only `moveAnUnpinnedParticleThroughTheGraphStreamSolver`; it must fail under the 50.0-unit production spread.
3. Restore the prominence test's old `frameAfterOneStep` calls with only the neighbor/target pins, removing the temporary source-position pins, and run only `increaseSeparationForHigherProminence`; it must fail under the 50.0-unit production spread.
4. Restore the hierarchy test's old no-pin absolute post-step anchor-distance comparison and run only `reduceAnchorDistanceChangeWhenEnclosuresHaveAHierarchyLink`; it must fail under the 50.0-unit production spread.

For every probe, record the command and the expected assertion failure in the task report. Remove every disposable worktree afterward. Then run `git diff --check` and `git status --short` in the active worktree to prove the probes did not alter it.

- [ ] **Step 6: Run focused and module verification, inspect scope, and commit**

Run:

```bash
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test --tests '*TypedForcesShould' -PTestLoggingFull
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test --tests '*GraphStreamBoundaryShould' -PTestLoggingFull
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test -PTestLoggingFull
git diff --check
git diff --name-only
```

Expected: all test commands exit 0, `git diff --check` produces no output, and the modified-file list contains exactly the two task allowlist paths. Then commit only those paths:

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/GraphStreamLayoutEngine.java \
    freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/TypedForcesShould.java
git commit -m "2026-08-26-graph-workspace-layout-seed-remediation: Stabilize force tests"
```

After the commit, run `git status --short` and include its output, the commit SHA, all verification commands, test counts, and each mutation-probe result in the bounded implementer report.
