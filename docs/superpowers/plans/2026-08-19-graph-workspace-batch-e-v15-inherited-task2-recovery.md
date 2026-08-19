# Graph Workspace Batch E V15 Inherited Task 2 Recovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.

**Goal:** Finish, independently verify, and commit the exact inherited V14 Task 2 accessibility hierarchy and stale-arrow implementation, then certify the whole Batch E branch.

**Architecture:** V15 treats the three dirty V14 Task 2 paths as quarantined implementation input, not as an admitted result. A fresh implementer proves the tests against committed pre-fix production in an isolated temporary archive, validates the inherited implementation in place, commits exactly the allowlist, and passes fresh task and whole-branch review.

**Tech Stack:** Java 8 source compatibility, Zulu 21.0.8 runtime, Gradle, JUnit 4, AssertJ, Mockito, Swing/AWT accessibility, immutable Graph Workspace projection and geometry values.

## Global Constraints

- Follow `AGENTS.md`: Java 8 source/target compatibility, UTF-8, four-space indentation, JUnit 4/AssertJ/Mockito, and Gradle only.
- Use exactly `JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"` with `$JAVA_HOME/bin` prepended to `PATH` for every Gradle command.
- Work only in `/data/home/guest/Development/freeplane/.worktrees/graph-batch-e-recovery` on branch `2026-08-10-graph-workspace-batch-e-recovery`; do not create, switch, or remove another Git worktree.
- Preserve V14's terminal run root, every earlier Batch E run root, `.codegraph/`, ignored artifacts, and unrelated user state byte-for-byte; never reset, rewrite, clean, checkout, stash, revert, or discard them.
- V14 Task 1 endpoint visibility was admitted by fresh audit and review. V14 Task 2 stopped without a report or commit and is not approval evidence. The V15 implementation must rely on direct current-source inspection, an isolated pre-fix probe, fresh tests, the new commit, and fresh reviews.
- The committed source baseline is `efaaa8ded5e988eb8c4e9dd6c11cb186da02ac94`. V15 starts with exactly three unstaged inherited paths and an empty index; their initial binary diff SHA-256 is `845b208626004d4abcee08ee54869ebb6353f308a64d0bb6b38aa2daee6bdf37`. A V15 documentation-only checkpoint above that baseline is permitted and must not change the inherited source diff.
- Keep implementation changes inside `AccessibleGraphCanvas.java`, `GraphInteractionController.java`, and `AccessibleGraphCanvasShould.java`. Do not modify projection, painter, hit-index, traversal, geometry, persistence, dependencies, resources, translations, public APIs, or any other source/test path.
- Suppressed, missing, or geometry-less endpoints are never painted, hit, traversed, or exposed through accessibility. Valid finite `LayoutPoint` coordinates, including values near `Double.MAX_VALUE`, remain accepted and unclamped.
- Preserve valid selected unmodified-arrow traversal, valid-selection no-candidate consumption, no-selection and stale-selection arrow panning, Shift acceleration, Tab and Shift+Tab cycling, Enter validation, Escape preview ordering, immutable ownership boundaries, and the existing nine concrete public `GraphIntent` nested types.
- Projection code remains independent of Swing and `GraphGeometry`; canvas code does not access `MapModel`, `NodeModel`, transformers, files, map mutation, or GraphStream.
- Every Gradle command uses bounded temporary logs; inspect concise tails or JUnit XML counts and remove disposable logs and probes afterward.
- Every child receives a renderer-produced full role envelope through a persisted byte-stable pointer prompt. Compare the completed transcript's first user message byte-for-byte with the stored prompt before admitting its report; a mismatch or missing required report is terminal for the run.
- Before the source commit require an empty index, `git diff --check`, an exact staged three-path allowlist, and subject `2026-08-10-graph-workspace: Repair graph accessibility fallback`. Do not stage plans, specs, run roots, or unrelated files in the source commit.
- The final Frontier review covers merge base `02f02355d9a33851a8a4417c4610d1897f716a50` through final `HEAD`, reconciles V11 findings F-1 through F-7, reruns `gradle :freeplane_plugin_graph:test -PTestLoggingFull`, and requires a clean worktree and index.

## Task 1: Complete Inherited Accessibility Hierarchy And Stale Arrow Recovery

**Implementer tier:** Advanced

**Files:**

- Modify inherited: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/AccessibleGraphCanvas.java:1-end`
- Modify inherited: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphInteractionController.java:1-end`
- Test inherited: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/AccessibleGraphCanvasShould.java:1-end`

**Interfaces:**

- Consumes: the exact inherited three-file diff relative to `efaaa8ded5e988eb8c4e9dd6c11cb186da02ac94`; admitted `ProjectedEndpointVisibility.visibleEndpoints(List<ProjectedNode>, List<ProjectedEnclosure>)`; `GraphTraversalOrder.tabOrder(CanvasState)` and `nearest(...)`; `GraphCanvas.getParent()`; Swing `AccessibleContext` parent/child APIs; and existing `setSelectionVisual(...)`, `selectEndpoint(...)`, and `panForArrow(...)` controller behavior.
- Produces: dynamic root `getAccessibleParent()` and `getAccessibleIndexInParent()`; virtual endpoint currentness requiring projection visibility and current geometry; stale unmodified-arrow fallback that clears local visual selection and executes ordinary panning without traversal/open intents; focused falsifiable regressions; and one exact three-file commit.
- Preserves: all files outside the allowlist, virtual endpoint parent/index behavior, safe current accessibility text/actions/bounds, valid selected-arrow traversal, valid-selection no-candidate consumption, no-selection and Shift panning, Tab/Shift+Tab, Enter, Escape, immutable canvas state, and the existing public intent types.

### Step 1: Establish and preserve the inherited baseline

- [ ] Record branch, `HEAD`, `git status --porcelain=v1 --untracked-files=all`, empty index, and `git diff --check`. Confirm the only dirty paths are the three files above and that their initial binary diff from committed source has SHA-256 `845b208626004d4abcee08ee54869ebb6353f308a64d0bb6b38aa2daee6bdf37`. The V15 documentation checkpoint may be above `efaaa8ded5`, but no other source/test path may differ.
- [ ] Read the complete inherited diff and current surrounding implementations. Independently verify the intended live parent/index resolution, projection-visible plus geometry-backed virtual endpoint availability, and stale-arrow fall-through. Treat V14's stopped child transcript as provenance only; do not cite its test or code claims as proof.
- [ ] Do not reset, clean, checkout, stash, revert, or rewrite the inherited files. Correct only a concrete defect demonstrated by direct inspection or fresh tests, and remain inside the three-file allowlist.

### Step 2: Independently prove the inherited tests are falsifiable

- [ ] Require the active index to remain empty. Create a unique temporary directory under `/tmp`, populate it with `git archive HEAD`, and copy only the active `AccessibleGraphCanvasShould.java` into the archived checkout. Do not copy either modified production file and do not modify the active worktree.
- [ ] In the temporary checkout, run the focused suite under exactly Zulu 21 with a bounded log:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle -p "$PROBE" :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.canvas.AccessibleGraphCanvasShould' -PTestLoggingFull
```

- [ ] Inspect the temporary JUnit XML. Confirm the pre-fix production fails the new live hierarchy and stale-arrow assertions for the intended reasons while existing controls compile and run. A compile/setup failure is not valid RED evidence. Remove the temporary checkout and log, then confirm active `HEAD`, index, and three-path diff are unchanged.

### Step 3: Validate and, only if necessary, correct the inherited implementation

- [ ] In `AccessibleGraphCanvas`, verify `getAccessibleParent()` resolves `canvas.getParent()` on every call and returns it only when it implements `Accessible`. Verify `getAccessibleIndexInParent()` resolves the live parent context, enumerates current children, recognizes either the canvas object or this root context, and otherwise returns `-1`.
- [ ] Verify each retained virtual endpoint resolves current projection visibility before node/enclosure details and retains existing current `NodeGeometry`/`HullGeometry`, safe-text, bounds, state, and action checks. Suppressed, removed, and geometry-less retained endpoints must be unavailable.
- [ ] In `GraphInteractionController`, verify Shift arrows always take accelerated panning. For an unmodified arrow, a current selection in traversal order retains nearest selection and consumes even when no directional candidate exists; a null state or selection absent from current order clears only visual selection and falls through to ordinary panning without emitting traversal or open intents.
- [ ] Run the active focused suite under Zulu 21 with a new bounded log and inspect all XML counts:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.canvas.AccessibleGraphCanvasShould' -PTestLoggingFull
```

### Step 4: Run complete current verification

- [ ] Run every canvas `*Should` suite under Zulu 21 and inspect all matching XML files:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.canvas.*Should' -PTestLoggingFull
```

- [ ] Run the complete graph-plugin suite under Zulu 21 and inspect the concise tail plus aggregate XML counts:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test -PTestLoggingFull
```

- [ ] Remove all disposable logs and probes. Recheck the complete three-file diff, exact dirty path list, empty index, and `git diff --check`. Confirm no plan, spec, run-root, build-output, or unrelated path will enter the source commit.

### Step 5: Commit exactly the inherited deliverable and report it

- [ ] Stage exactly:

```bash
git add -- freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/AccessibleGraphCanvas.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphInteractionController.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/AccessibleGraphCanvasShould.java
```

- [ ] Compare `git diff --cached --name-only` byte-for-byte with the three-path allowlist and run `git diff --cached --check`. Commit exactly:

```bash
git commit -m "2026-08-10-graph-workspace: Repair graph accessibility fallback"
```

- [ ] Write exactly one implementer report with `STATUS: DONE`, the full commit SHA, isolated RED evidence, exact focused/canvas/full-suite counts, staged allowlist evidence, and any concrete concern. Return `DONE` only after the commit and report both exist.
