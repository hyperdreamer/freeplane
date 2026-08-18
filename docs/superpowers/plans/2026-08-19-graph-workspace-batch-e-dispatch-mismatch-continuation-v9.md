# Graph Workspace Batch E Dispatch-Mismatch Continuation Implementation Plan V9

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic subagent-driven-development controller to execute this plan task-by-task.

**Goal:** Independently certify the committed finite-coordinate edge-hit correction, repair any freshly reproduced arithmetic defect through a bounded review fix round, and complete Backlog Task 27 keyboard traversal and accessibility.

**Architecture:** V9 continues in the existing recovery worktree from the valid source commit `e740e9c741f1f2aa6db4c0567e1957bf0416a63d`. Task 1 begins with a read-only audit implementer and a fresh Frontier review. Only an admitted review finding may authorize a fixer to change the two Task 1 paths. Task 2 starts only after Task 1 approval and adds traversal/accessibility over existing immutable canvas values. The whole branch receives a final Frontier review.

## Global Constraints

- Follow `AGENTS.md`: Java 8 source/target, UTF-8, four-space indentation, JUnit 4/AssertJ/Mockito, and Gradle only.
- Use exactly `~/.sdkman/candidates/java/21.0.8-zulu` with `JAVA_HOME` and `$JAVA_HOME/bin` prepended for every Gradle command.
- Preserve accepted source commits `56eee93d9c5432182519a23a886f181658defa8c`, `8d54ecda2157c06baa9b765cc92eb2a82e834506`, `54cab57876bb73bde13945bbbb8493ed7d34ab66`, `8ef6d2e88043ae406a49e07aa2b0608c40c62f76`, and `e740e9c741f1f2aa6db4c0567e1957bf0416a63d`; do not rewrite or recommit them.
- Preserve all predecessor SDD roots, especially V5, V6, V7, and V8, byte-for-byte. Their blocked reports and child outputs are diagnostic history, not fresh evidence.
- Continue in `/data/home/guest/Development/freeplane/.worktrees/graph-batch-e-recovery`; do not create another Git worktree. Preserve `.codegraph/`, ignored artifacts, and unrelated user state. Never clean, reset, checkout, stash, or revert.
- Every child reads only its brief and listed paths. Render with `sdd-state render-prompt`, persist the bytes, and pass them verbatim. Before `spawn_subsession`, write the exact candidate prompt bytes to a disposable file and run `cmp` against the persisted rendered prompt. Record the returned session immediately; compare the completed first user message byte-for-byte before admitting the report. Any mismatch is terminal and never reissued.
- Keep changes within each task allowlist. Do not add dependencies, resources, source-model access, workspace commands, or compatibility fallbacks.
- Canvas values remain immutable at ownership boundaries and canvas mutation remains EDT-local. Canvas code never reads `MapModel`/`NodeModel`, transforms text, changes maps, writes files, or exposes GraphStream.
- Geometry is the source for rendering, hit bounds, traversal positions, and accessibility. Suppressed enclosures are never visible, hittable, traversable, or accessible.
- Safe text comes only from projected labels and enclosure labels; normalize search with `Locale.ROOT`. Preserve map identity text, prominence wording, Task 25 rendering levels, adaptive limits, and all existing Task 26 behavior.
- Keyboard behavior is exact: selected unmodified arrows traverse; no-selection arrows pan; Shift arrows accelerate pan; Enter opens the selected source; Escape cancels preview before clearing selection.
- `GraphIntent` retains its nine existing public nested types. Accessible descriptions mention nonzero `visibleOutgoingTargets()` but never scale factors or raw/excluded text.
- Use bounded Gradle logs and delete temporary probes/logs. Do not invent production mutants.
- Before any source commit, require an empty index, exact staged allowlist, `git diff --check`, and a subject beginning `2026-08-10-graph-workspace:`.
- Final Frontier review covers merge base `02f02355d9a33851a8a4417c4610d1897f716a50` through final `HEAD` with fresh evidence.

## Task 1: Audit And Re-certify Committed Finite Edge Hit Testing

**Implementer tier:** Capable

**Files:**

- Read-only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphHitIndex.java:1-end`
- Read-only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPainter.java:1-end`
- Read-only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphSearchModel.java:1-end`
- Read-only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphInteractionController.java:1-end`
- Read-only: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphInteractionControllerShould.java:1-end`
- Read-only: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphSearchModelShould.java:1-end`
- Read-only: `docs/superpowers/specs/2026-08-19-graph-workspace-batch-e-dispatch-mismatch-v9-recovery-design.md:1-end`
- Review-directed fix allowlist: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphHitIndex.java`, `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphInteractionControllerShould.java`

**Interfaces:** Audit source range `8d54ecda2157c06baa9b765cc92eb2a82e834506..e740e9c741f1f2aa6db4c0567e1957bf0416a63d`, including safe-search correction `8ef6d2e88043ae406a49e07aa2b0608c40c62f76`. The initial implementer is strictly read-only and produces an audit report without a commit. A fixer may change only the two explicit paths after a load-bearing review finding.

- [ ] **Step 1: Establish the clean source baseline**

Record current `HEAD`, branch, index, status, and exact source range. Confirm `e740e9c741f1f2aa6db4c0567e1957bf0416a63d` is present and no source path is dirty. Verify the prior commit path ranges and subjects. Do not mutate Git or source.

- [ ] **Step 2: Run fresh focused gates**

With Zulu 21 and bounded logs, run:

````bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.canvas.GraphInteractionControllerShould' --tests 'org.freeplane.plugin.graph.canvas.GraphSearchModelShould' -PTestLoggingFull
````

````bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.canvas.GraphViewportShould' --tests 'org.freeplane.plugin.graph.canvas.GraphCanvasPaintShould' --tests 'org.freeplane.plugin.graph.canvas.AdaptiveRenderingPolicyShould' --tests 'org.freeplane.plugin.graph.canvas.GraphInteractionControllerShould' --tests 'org.freeplane.plugin.graph.canvas.GraphSearchModelShould' -PTestLoggingFull
````

Record exact counts and failures; missing environment evidence is not a pass.

- [ ] **Step 3: Audit arithmetic and falsifiable edge cases**

Inspect the real `GraphHitIndex.edgeAt` path and exercise ordinary finite coordinates, mixed magnitudes, near-limit spans, projection/cross-product cancellation, positive subnormal residuals, zero-length segments, endpoint clamping, zero and finite tolerances, non-finite rejection, nearest-edge ordering, and key ties. Use disposable `/tmp` probes where needed. Establish whether rounded intermediate products can erase a positive finite distance. Do not modify source during this task.

- [ ] **Step 4: Audit Task 26 boundaries**

Verify layout-anchor precedence, painted enclosure segments, node-before-hull ordering, suppressed-hull exclusion, all interaction intents/uninstall behavior, and projection-only safe search. Confirm projected labels/map names and inserted fixture sentinels, with no source-model or transformed-text access.

- [ ] **Step 5: Write the audit report**

Inspect status and source diffs. Write exactly one implementer report with `STATUS: DONE` when evidence is complete. The report must state that no source/index/HEAD changes were made and must list exact test/probe results and any suspected defect. Do not claim independent review approval.

- [ ] **Step 6: Review-directed fix commit gate**

Only if the state machine opens a fix round may a fresh fixer add the smallest regression/correction in the two Task 1 paths. Require empty index, exact staged names, `git diff --check`, and a `2026-08-10-graph-workspace:` commit subject. Report the full SHA; do not touch any other path.

## Task 2: Implement Backlog Task 27 Keyboard Traversal And Accessible Virtual Children

**Implementer tier:** Advanced

**Files:**

- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/TraversalDirection.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphTraversalOrder.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/AccessibleGraphCanvas.java`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphCanvas.java:1-end`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphInteractionController.java:1-end`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/AccessibleGraphCanvasShould.java`

**Interfaces:** Add the specified public `TraversalDirection` and `GraphTraversalOrder` APIs; add package-private virtual Swing accessibility rooted at `GraphCanvas.getAccessibleContext()` without creating per-endpoint Swing components or retaining mutable snapshots. Preserve existing public Task 25/26 APIs and use only immutable projection/geometry/viewport/paint state.

- [ ] **Step 1: Write falsifiable traversal/accessibility tests**

Use a local fixture with visible nodes, a non-suppressed enclosure, a suppressed enclosure, pins, distinct map names, safe labels, zero and nonzero prominence, and equidistant directional candidates. Assert deterministic tab order and nearest half-plane/tie behavior, selected/no-selection/Shift arrows, Tab cycling, Enter, Escape preview priority, virtual child roles/actions/names/descriptions/bounds, live state updates, and exclusion of suppressed/raw/color/scale text.

- [ ] **Step 2: Run the red test**

````bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.canvas.AccessibleGraphCanvasShould' -PTestLoggingFull
````

Confirm failure is the missing Task 27 API, not a prior regression.

- [ ] **Step 3: Implement deterministic traversal**

Map node endpoints to `NodeGeometry.center()` and non-suppressed enclosure endpoints to hull `labelAnchor()` from `GraphGeometry`; omit geometry-less endpoints. Sort tab order by key. For nearest, exclude source, filter strict directional half-planes, compare squared distances, and break ties by key.

- [ ] **Step 4: Implement keyboard and accessibility**

Extend controller behavior for traversal, Tab/Shift-Tab, Enter, and Escape while preserving pan semantics. Add lazy root accessibility and lightweight virtual children that resolve current state on every query, transform current geometry, expose safe projected text/map identity, actions, selection/pin state, and nonzero visible outgoing targets only.

- [ ] **Step 5: Run green focused and full canvas tests**

Run the focused test again, then `gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.canvas.*Should' -PTestLoggingFull` under Zulu 21 with bounded logs. Confirm Task 25/26/27 suites pass together.

- [ ] **Step 6: Commit Task 27 with exact allowlist**

Stage exactly the six Task 2 paths listed above after empty-index and `git diff --check` gates, then commit with `2026-08-10-graph-workspace: Make the graph keyboard accessible`. Report full SHA and counts.

### Final Verification

Dispatch a fresh Frontier final reviewer over the complete merge-base-to-HEAD branch. It must run `JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test -PTestLoggingFull` with bounded logs, reconcile all carry-forward and new commits, and report clean state, preserved terminal roots, exact results, and no unresolved findings.
