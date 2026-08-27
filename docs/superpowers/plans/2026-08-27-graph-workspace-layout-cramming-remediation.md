# Graph Workspace Layout Cramming Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.
> Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop GraphStream's `chooseNodePosition` from teleporting freshly seeded layout particles onto their neighbours at link insertion, so the Graph Workspace force layout no longer collapses into one pile.

**Architecture:** Override `chooseNodePosition(NodeParticle, NodeParticle)` in `TypedSpringBox` as a no-op so the engine's deterministic 50.0-unit seeds survive link insertion, and add two `TypedForcesShould` regressions that assert published positions move less than 1.0 world unit between consecutive frames (apply to first step, and step to step across a topology change).

**Tech Stack:** Java 8 source target, JUnit 4, AssertJ, Gradle, GraphStream gs-core 1.3.

## Global Constraints

- Work only in `/data/home/guest/Development/freeplane/.worktrees/graph-layout-cramming-remediation` on branch `graph-layout-cramming-remediation`, based on the local `main` HEAD that contains the committed design document and plan.
- The deliverable code diff may modify only `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/TypedSpringBox.java` and `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/TypedForcesShould.java`.
- Preserve SHA-256 identity encoding, deterministic seeds, particle ordering, links, pins, solver quality, prominence radii, reset behavior, and all public layout interfaces. Do not modify `TypedNodeParticle`, `GraphStreamLayoutEngine`, projection code, canvas code, or persistence code.
- Use public `LayoutEngine`, `LayoutRequest`, `LayoutFrame`, and `PinProjection` in tests; do not access GraphStream particle internals and do not add a test-only production seam.
- Use `JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu` for every Gradle invocation; use `gradle`, never Maven or the Gradle wrapper.
- Follow TDD: the two new regressions must fail under the current implementation before the production change is added, and the failure must be the measured ~36.7 world-unit teleport, not a compile error.
- Every task commit starts `2026-08-27-graph-workspace-layout-cramming-remediation:` and uses an imperative subject.
- Before every source-changing commit: assert the index is empty, stage exactly the allowlisted paths, run `git diff --cached --check`, compare `git diff --cached --name-only` to the exact allowlist, and abort on any extra or missing path.

## Task 1: Keep seeded particle positions when links are added

**Implementer tier:** Standard

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/TypedSpringBox.java:27-38`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/TypedForcesShould.java:54-74,277-300`

**Interfaces:**

- Consumes: `LayoutEngine.apply(LayoutRequest): LayoutFrame` and `LayoutEngine.step(): LayoutFrame`; the existing package-local test helpers `baseline(long)`, `expanded(long)`, `request(WorkspaceId, GraphProjection, GraphProjection, List<PinProjection>)`, `distance(LayoutPoint, LayoutPoint)`, and the constant `WORKSPACE_ONE`, all already present in `TypedForcesShould`.
- Produces: `TypedSpringBox.chooseNodePosition(NodeParticle, NodeParticle)` as a no-op override, and two regressions `firstStepDoesNotTeleportSeededParticlesOntoTheirNeighbours` and `aTopologyChangeDoesNotTeleportRetainedParticles` using the new helper `greatestMovementBetween(LayoutFrame, LayoutFrame)`.

- [ ] **Step 1: Add the two failing regressions and the movement helper**

Insert the two tests directly after `smallWorkspaceInitialPositionsAreNotCollapsedIntoTheOrigin` (which ends at line 74), and add the helper `greatestMovementBetween` directly after `greatestDistanceBetweenDistinctPositions` (which ends at line 286):

```java
    @Test
    public void firstStepDoesNotTeleportSeededParticlesOntoTheirNeighbours() {
        GraphProjection projection = baseline(1);

        try (LayoutEngine engine = GraphStreamLayoutFactory.create(LayoutCalibration.spikeDefaults())) {
            LayoutFrame applied = engine.apply(request(WORKSPACE_ONE, projection, projection,
                Collections.<PinProjection>emptyList()));
            LayoutFrame stepped = engine.step();

            assertThat(greatestMovementBetween(applied, stepped)).isLessThan(1.0);
        }
    }

    @Test
    public void aTopologyChangeDoesNotTeleportRetainedParticles() {
        GraphProjection baseline = baseline(1);
        GraphProjection expanded = expanded(2);

        try (LayoutEngine engine = GraphStreamLayoutFactory.create(LayoutCalibration.spikeDefaults())) {
            engine.apply(request(WORKSPACE_ONE, baseline, baseline, Collections.<PinProjection>emptyList()));
            engine.step();
            LayoutFrame before = engine.apply(request(WORKSPACE_ONE, baseline, expanded,
                Collections.<PinProjection>emptyList()));
            LayoutFrame after = engine.step();

            assertThat(greatestMovementBetween(before, after)).isLessThan(1.0);
        }
    }
```

and the helper:

```java
    private static double greatestMovementBetween(LayoutFrame first, LayoutFrame second) {
        double greatest = 0.0;
        for (ProjectedNodeKey key : first.positions().nodes().keySet()) {
            greatest = Math.max(greatest, distance(first.positions().nodes().get(key),
                second.positions().nodes().get(key)));
        }
        for (EnclosureHullKey key : first.positions().anchors().keySet()) {
            greatest = Math.max(greatest, distance(first.positions().anchors().get(key),
                second.positions().anchors().get(key)));
        }
        return greatest;
    }
```

`ProjectedNodeKey` and `EnclosureHullKey` are already imported in `TypedForcesShould`; `distance(LayoutPoint, LayoutPoint)` already exists in the same class. The local variable in the second test intentionally shadows the fixture method name `baseline`.

- [ ] **Step 2: Run the focused suite and confirm both regressions fail**

Run:

```bash
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test --tests '*TypedForcesShould' -PTestLoggingFull
```

Expected: 13 pass, 2 fail. Both new tests must fail with a measured movement around 36.7 world units (the teleport), not with a compile error. Do not change the assertions or threshold to make the current implementation pass.

- [ ] **Step 3: Add the no-op override to `TypedSpringBox`**

Between the constructor (which ends at line 31) and `newNodeParticle` (which starts at line 33), insert exactly:

```java
    @Override
    protected void chooseNodePosition(final NodeParticle first, final NodeParticle second) {
        // Keep the engine's deterministic seeded positions. The default
        // implementation teleports a degree-1 endpoint onto its already-connected
        // neighbour at edge insertion, collapsing freshly seeded particles into a pile.
    }
```

`NodeParticle` is already imported (`org.graphstream.ui.layout.springbox.NodeParticle`). Do not change any other production code.

- [ ] **Step 4: Run the focused suite and confirm all 15 tests pass**

Run the Step 2 command again.

Expected: PASS, 15 tests, 0 failures.

- [ ] **Step 5: Falsifiability probe (disposable, no worktree residue)**

Record the SHA-256 of the modified production file (`sha256sum freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/TypedSpringBox.java`) and copy the file to `/tmp/typed-spring-box-fixed.java`. Restore the original with `git checkout -- freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/TypedSpringBox.java`, run the Step 2 command, and confirm the two new tests fail again (movement around 36.7 world units) while the other 13 pass. Restore the fixed file from the copy, verify `sha256sum` equals the recorded value, rerun the Step 2 command green, and confirm `git status --short` shows exactly the two allowlisted paths modified and nothing else. Delete the `/tmp` copy after verification.

- [ ] **Step 6: Run the full graph-plugin suite and verify scope**

Run:

```bash
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test -PTestLoggingFull
git diff --check
git diff --name-only
```

Expected: `BUILD SUCCESSFUL` with zero failed tests; `git diff --check` produces no output; the modified-file list contains exactly the two allowlisted paths.

- [ ] **Step 7: Commit exactly the two allowlisted paths**

```bash
test -z "$(git diff --cached --name-only)"
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/TypedSpringBox.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/TypedForcesShould.java
git diff --cached --check
git diff --cached --name-only
git commit -m "2026-08-27-graph-workspace-layout-cramming-remediation: Keep seeded layout positions when links are added"
```

Expected staged names: exactly the two allowlisted paths.
