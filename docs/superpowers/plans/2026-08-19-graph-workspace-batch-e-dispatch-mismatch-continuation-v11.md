# Graph Workspace Batch E V11 Recovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.

**Goal:** Finish, independently validate, and commit the inherited Task 27 graph
keyboard traversal and virtual accessibility implementation, then certify the
whole Batch E branch.

**Architecture:** V11 treats the six dirty Task 27 paths left by the V10 child as
quarantined implementation input, not as a completed result. One fresh Advanced
implementer takes ownership of that narrow diff, validates it against the existing
immutable canvas and geometry contracts, makes only demonstrated corrections, and
commits exactly the six paths. Fresh Frontier task and final reviews provide the
independent evidence V10 could not produce.

**Tech Stack:** Java 8 source target, Zulu 21.0.8 runtime, Gradle, JUnit 4,
AssertJ, Mockito, Swing/AWT accessibility, and immutable Graph Workspace canvas
projection values.

## Global Constraints

- Follow `AGENTS.md`: Java 8 source/target compatibility, UTF-8, four-space indentation, JUnit 4/AssertJ/Mockito, and Gradle only.
- Use exactly `JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"` with `$JAVA_HOME/bin` prepended to `PATH` for every Gradle command.
- Work only in `/data/home/guest/Development/freeplane/.worktrees/graph-batch-e-recovery` on branch `2026-08-10-graph-workspace-batch-e-recovery`; do not create another Git worktree.
- Preserve committed `HEAD` baseline `5cb7dfd07bf701ae9a5b8e774031ab9338189995` and accepted commits `56eee93d9c5432182519a23a886f181658defa8c`, `8d54ecda2157c06baa9b765cc92eb2a82e834506`, `54cab57876bb73bde13945bbbb8493ed7d34ab66`, `8ef6d2e88043ae406a49e07aa2b0608c40c62f76`, and `e740e9c741f1f2aa6db4c0567e1957bf0416a63d`; never reset, rewrite, or recommit them.
- Preserve V5 through V10 terminal run roots byte-for-byte, including V10's stopped child artifacts; they are diagnostic history only and are never fresh evidence.
- The initial worktree is intentionally dirty only in the six Task 27 paths listed below. Preserve `.codegraph/`, ignored artifacts, and unrelated user state. Never clean, reset, checkout, stash, revert, or discard those inherited files.
- The inherited Task 27 diff is not an admitted result. Fresh evidence must come from direct current-source inspection, fresh command output, the new commit, and fresh review; do not cite V10's reportless child transcript as proof.
- Keep source changes inside the explicit six-file allowlist. Do not add dependencies, resources, translations, shared fixtures, source-model access, workspace commands, exports outside Task 27, print/export APIs, or compatibility fallbacks.
- `CanvasState`, `GraphProjection`, `LayoutFrame`, `GraphGeometry`, `GraphViewport`, and `GraphPaintState` remain immutable at ownership boundaries; canvas mutation remains EDT-local and canvas code never accesses `MapModel`, `NodeModel`, a transformer, a map mutation, files, or GraphStream.
- Geometry alone supplies rendered, hit, traversal, and accessible bounds. Suppressed or geometry-less endpoints are never visible, hittable, traversable, or accessible. Safe text comes only from projected labels and projected enclosure labels.
- Preserve exact keyboard behavior: selected unmodified arrows traverse; no-selection arrows pan; Shift arrows always accelerate pan; Tab and Shift+Tab cycle visible endpoints; Enter opens selection; Escape cancels preview before clearing selection.
- `GraphIntent` retains exactly its nine existing public concrete nested types. Accessibility includes map identity and nonzero `visibleOutgoingTargets()` but never scale factors, color hexadecimal values, raw text, excluded content, or suppressed endpoints.
- For every child, persist a renderer-produced full role envelope under the run root, dispatch a short persisted pointer prompt whose candidate bytes match the stored file, record the returned session, and compare its first user message byte-for-byte before admitting a result. A mismatch is terminal and never reissued.
- Use bounded Gradle logs and delete disposable `/tmp` probes. Before source commit require an empty index, exact staged allowlist, `git diff --check`, and a subject beginning `2026-08-10-graph-workspace:`.
- The final Frontier review covers merge base `02f02355d9a33851a8a4417c4610d1897f716a50` through final `HEAD`, runs `gradle :freeplane_plugin_graph:test -PTestLoggingFull` under Zulu 21, and reports clean final status.

## Task 1: Complete Inherited Task 27 Keyboard Traversal And Virtual Accessibility

**Implementer tier:** Advanced

**Files:**

- Create or modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/TraversalDirection.java:1-end`
- Create or modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphTraversalOrder.java:1-end`
- Create or modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/AccessibleGraphCanvas.java:1-end`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphCanvas.java:1-end`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphInteractionController.java:1-end`
- Create or modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/AccessibleGraphCanvasShould.java:1-end`

**Interfaces:**

- Consumes: the six inherited unstaged paths, immutable `CanvasState`, `GraphGeometry`, `NodeGeometry`, `HullGeometry`, `ProjectedEndpointKey`, current `GraphViewport`, current `GraphPaintState`, `GraphIntent`, and `GraphInteractionController`.
- Produces: public `TraversalDirection { UP, DOWN, LEFT, RIGHT }`; public `GraphTraversalOrder.tabOrder(CanvasState)` and `nearest(CanvasState, ProjectedEndpointKey, TraversalDirection)`; package-private `AccessibleGraphCanvas extends AccessibleContext implements AccessibleComponent`; lazy `GraphCanvas.getAccessibleContext()`; controller keyboard traversal/activation hooks; and a focused `AccessibleGraphCanvasShould` suite.
- Must preserve: the six-path inherited diff as the starting point, all Task 25/26 behavior, immutable projection ownership, current viewport conversion, and the existing `GraphIntent` public type set.

- [ ] **Step 1: Establish the intentional inherited baseline without admitting it**

From the assigned worktree, record `HEAD`, `git status --porcelain`, empty index,
and the exact dirty path list. Confirm it is `5cb7dfd07bf701ae9a5b8e774031ab9338189995`
plus exactly the six allowlisted Task 27 paths. Inspect the complete current diff,
the new tests, and the affected canvas/controller types. Do not reset, checkout,
clean, stash, or discard the inherited source. If another source path is dirty or
the index is nonempty, return `BLOCKED` with the exact discrepancy.

- [ ] **Step 2: Independently validate the traversal and accessibility contracts**

Read the inherited production implementation and tests directly. Confirm tab order
uses only node centers and non-suppressed hull label anchors, omits missing geometry,
sorts keys, and selects directional candidates by strict half-plane, squared
distance, and key ties. Confirm virtual accessibility resolves current canvas data
per call; uses viewport-transformed geometry; exposes only safe label/map identity,
selection/pin/currentness, nonzero outgoing-target wording, and controller-backed
select/open actions; and does not create `JComponent` endpoint children.

Confirm keyboard dispatch preserves no-selection and Shift pan behavior while
selected arrows traverse, Tab cycles, Enter opens, and Escape cancels preview before
clearing selection. Correct only a concrete discrepancy found in this direct audit.
Do not rebuild the feature, broaden fixtures, or rely on the predecessor child
transcript as test evidence.

- [ ] **Step 3: Run fresh focused and canvas verification**

Run the following commands under Zulu 21 with stdout/stderr redirected to temporary
logs. Inspect concise tails and JUnit XML counts, report exact results, and delete
the logs afterward:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.canvas.AccessibleGraphCanvasShould' -PTestLoggingFull
```

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.canvas.*Should' -PTestLoggingFull
```

If the focused test does not prove a stated Task 27 behavior, add the smallest
falsifiable assertion in `AccessibleGraphCanvasShould` and rerun both commands.
A failing environment command is not a pass and must be reported as blocking.

- [ ] **Step 4: Commit exactly the completed Task 27 deliverable and report it**

Inspect the actual diff and require an empty index. Run `git diff --check`, stage
exactly these six paths, compare the staged path list against the allowlist, and
commit exactly:

```bash
git add -- freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/TraversalDirection.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphTraversalOrder.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/AccessibleGraphCanvas.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphCanvas.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphInteractionController.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/AccessibleGraphCanvasShould.java
git commit -m "2026-08-10-graph-workspace: Make the graph keyboard accessible"
```

Write the required report with the full commit SHA, exact test counts, final staged
path check, and any concrete concern. Return `DONE` only after the commit and
report both exist.
