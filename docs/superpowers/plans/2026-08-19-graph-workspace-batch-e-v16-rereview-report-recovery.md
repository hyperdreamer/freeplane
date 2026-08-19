# Graph Workspace Batch E V16 Re-review Report Recovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.

**Goal:** Independently audit and certify the committed Batch E accessibility and stale-arrow changes after V15's scoped re-review stopped without its required report, then obtain fresh task and whole-branch review.

**Architecture:** V16 preserves V15 as a terminal, non-admissible run. Its only task is a no-source-change audit of the exact committed production range through `461d78fbaa6803568c8bfe5bd4ba7b34d4a15d88`, with a new isolated pre-fix probe and fresh test evidence. A fresh task reviewer decides the audit independently; after task completion, a Frontier reviewer audits the whole branch from the fixed merge base.

**Tech Stack:** Java 8 source compatibility, Zulu 21.0.8 runtime, Gradle, JUnit 4, AssertJ, Mockito, Swing/AWT accessibility, immutable Graph Workspace projection and geometry values.

## Global Constraints

- Follow `AGENTS.md`: Java 8 source/target compatibility, UTF-8, four-space indentation, JUnit 4/AssertJ/Mockito, and Gradle only.
- Use exactly `JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"` with `$JAVA_HOME/bin` prepended to `PATH` for every Gradle command.
- Work only in `/data/home/guest/Development/freeplane/.worktrees/graph-batch-e-recovery` on branch `2026-08-10-graph-workspace-batch-e-recovery`; do not create, switch, or remove another Git worktree.
- Preserve every earlier Batch E run root, especially V15 at `.superpowers/sdd/2026-08-19-graph-workspace-batch-e-v15-inherited-task2-recovery`, `.codegraph/`, ignored artifacts, and unrelated user state byte-for-byte; never reset, rewrite, clean, checkout, stash, revert, amend, or discard them.
- V15 is terminal in `DISPATCH_MISMATCH_BLOCKED` because scoped reviewer `01a0187e-05c8-72f1-bd63-2fd9b715c8ad` became idle without its required report. Its reports and partial transcript are provenance only and are not evidence for V16 approval.
- Audit source commit range `1eda9627e0c02153ec786fb1e74c7e81b83cc1f0..461d78fbaa6803568c8bfe5bd4ba7b34d4a15d88`; the source paths changed in that range are exactly `AccessibleGraphCanvas.java`, `GraphInteractionController.java`, and `AccessibleGraphCanvasShould.java` under `freeplane_plugin_graph/src`.
- Task 1 is audit-only: do not modify source, tests, build files, dependencies, resources, plans, specs, run roots, or Git history. If a defect is discovered, record it with exact evidence for the independent task review rather than changing code.
- Suppressed, missing, or geometry-less endpoints are never painted, hit, traversed, or exposed through accessibility. Valid finite `LayoutPoint` coordinates, including values near `Double.MAX_VALUE`, remain accepted and unclamped.
- Preserve valid selected unmodified-arrow traversal, valid-selection no-candidate consumption, no-selection and stale-selection arrow panning, Shift acceleration, Tab and Shift+Tab cycling, Enter validation, Escape preview ordering, immutable ownership boundaries, and the existing nine concrete public `GraphIntent` nested types.
- Projection code remains independent of Swing and `GraphGeometry`; canvas code does not access `MapModel`, `NodeModel`, transformers, files, map mutation, or GraphStream.
- Every Gradle command uses a bounded temporary log; inspect concise tails or JUnit XML counts and remove only logs/probes created by the command after evidence is recorded.
- Every child receives a renderer-produced full role envelope through a persisted byte-stable pointer prompt. Compare the completed transcript's first user message byte-for-byte with the stored prompt before admitting its report; a mismatch or missing required report is terminal for the run.
- The final Frontier review covers merge base `02f02355d9a33851a8a4417c4610d1897f716a50` through final `HEAD`, reconciles carried Batch E findings F-1 through F-7, reruns `gradle :freeplane_plugin_graph:test -PTestLoggingFull`, and requires a clean worktree and index.

## Task 1: Independently Audit Committed Accessibility Recovery

**Implementer tier:** Advanced

**Files:**

- Inspect only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/AccessibleGraphCanvas.java:1-end`
- Inspect only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphInteractionController.java:1-end`
- Inspect only: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/AccessibleGraphCanvasShould.java:1-end`
- Create only: the controller-dispatched audit report under the V16 ignored run root.

**Interfaces:**

- Consumes: committed range `1eda9627e0c02153ec786fb1e74c7e81b83cc1f0..461d78fbaa6803568c8bfe5bd4ba7b34d4a15d88`; `ProjectedEndpointVisibility.visibleEndpoints(List<ProjectedNode>, List<ProjectedEnclosure>)`; `GraphTraversalOrder.tabOrder(CanvasState)` and `nearest(...)`; `GraphCanvas.getParent()`; Swing `AccessibleContext` parent/child APIs; and existing `setSelectionVisual(...)`, `selectEndpoint(...)`, and `panForArrow(...)` controller behavior.
- Produces: one audit-only report with independent Git-range evidence, isolated pre-fix RED evidence, fresh focused/canvas/full-suite XML evidence, and a precise `DONE`, `DONE_WITH_CONCERNS`, or `BLOCKED` status. It produces no source/test changes and no source commit.
- Preserves: all committed source and test bytes, all previous SDD artifacts, virtual endpoint parent/index behavior, projection-visible plus geometry-backed virtual endpoint availability, stale-arrow panning behavior, valid keyboard behavior, and the clean index.

### Step 1: Pin the continuation audit boundary

- [ ] Record branch, current `HEAD`, merge base, `git status --porcelain=v1 --untracked-files=all`, empty index, and `git diff --check`.
- [ ] Verify `461d78fbaa6803568c8bfe5bd4ba7b34d4a15d88` is an ancestor of current `HEAD`. Verify `git diff --name-only 1eda9627e0c02153ec786fb1e74c7e81b83cc1f0 461d78fbaa6803568c8bfe5bd4ba7b34d4a15d88` lists exactly the three declared source/test paths. Verify `git diff --name-only 461d78fbaa6803568c8bfe5bd4ba7b34d4a15d88 HEAD` contains no source/test path.
- [ ] Read the exact source and test changes directly. Do not cite V15 reports or transcript assertions as evidence.

### Step 2: Independently prove the retained-child regression is falsifiable

- [ ] Create a unique temporary archive from exact pre-fix production `1eda9627e0c02153ec786fb1e74c7e81b83cc1f0`, then copy only the active committed `AccessibleGraphCanvasShould.java` from `461d78fbaa6803568c8bfe5bd4ba7b34d4a15d88` into the archive. Do not copy the current `AccessibleGraphCanvas.java` or `GraphInteractionController.java`, and do not modify the active worktree.
- [ ] Run the focused suite in the archive under Zulu 21, capture output to a bounded log, and inspect its XML:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle -p "$PROBE" :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.canvas.AccessibleGraphCanvasShould' -PTestLoggingFull
```

- [ ] Verify the archive compiles and the new `makeRetainedVirtualChildUnavailableAfterCurrentProjectionRemovesEndpoint` assertion fails because pre-fix production returns `Visible enclosure label - Map Enclosure` instead of `Unavailable graph endpoint`; do not treat a compile or setup failure as RED evidence.
- [ ] Inspect the fixed `EndpointAccessible.current()` path: its `ProjectedEndpointVisibility.visibleEndpoints(...)` check must occur before node/enclosure detail lookup and before the enclosure missing-hull fallback. Inspect the test path: it must acquire a visible retained child, assert availability, force projection removal in the currentness read, and assert unavailable name, description, actions, component state/bounds, and accessibility state. Inspect the separate current-geometry disappearance regression.
- [ ] Delete only the temporary archive and log created in this step after recording XML counts and the expected F-1 failure message; recheck active `HEAD`, status, index, and source hashes.

### Step 3: Freshly verify the committed behavior without modifying it

- [ ] Run the active focused suite under Zulu 21 with a new bounded log, inspect `TEST-org.freeplane.plugin.graph.canvas.AccessibleGraphCanvasShould.xml`, and record its tests/skips/failures/errors counts:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --rerun-tasks --tests 'org.freeplane.plugin.graph.canvas.AccessibleGraphCanvasShould' -PTestLoggingFull
```

- [ ] Run every canvas `*Should` suite with a separate bounded log and aggregate the matching JUnit XML counts:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --rerun-tasks --tests 'org.freeplane.plugin.graph.canvas.*Should' -PTestLoggingFull
```

- [ ] Run the complete graph-plugin suite with a separate bounded log and aggregate all graph-plugin JUnit XML counts:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --rerun-tasks -PTestLoggingFull
```

- [ ] Remove only temporary logs created in this step. Recheck branch, `HEAD`, `git status --porcelain=v1 --untracked-files=all`, empty index, `git diff --check`, and that no source/test file has changed from current `HEAD`.

### Step 4: Report the no-source-change audit

- [ ] Write exactly one report at the dispatched report path. For a passing audit use `STATUS: DONE`, name `COMMIT: none (audit-only)`, state that no source/test file changed, record the exact range, archive XML counts and F-1 failure message, active XML counts, commands, current `HEAD`, and clean index/status evidence.
- [ ] If an unmet requirement or failed verification is found, make no source change. Use `STATUS: DONE_WITH_CONCERNS` with a non-empty `correctness` concern that names the exact path, line, failed command or observation, and required correction; use `BLOCKED` only when evidence cannot be obtained.
- [ ] Return exactly the status token matching the written report. Do not stage, commit, amend, or modify any worktree path.

### Step 5: Commit Boundary

- [ ] Confirm the audit intentionally creates no source commit: the only permitted tracked commit is the controller coordinator's separate V16 plan checkpoint, already present before dispatch. Leave the index empty and make no Git mutation.
