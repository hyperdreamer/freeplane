# Graph Workspace Batch E Dispatch-Mismatch Continuation Implementation Plan V8

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic subagent-driven-development controller to execute this plan task-by-task.

**Goal:** Re-certify the committed finite-coordinate edge-hit correction after the V7 review dispatch mismatch, correct any independently reproduced defect, and then implement Backlog Task 27 keyboard traversal and accessible virtual children.

**Architecture:** V8 continues in the existing Batch E recovery worktree from the valid Task 1 commit. Its first task is a no-source-change carry-forward audit followed by a fresh independent review. Only a state-machine-directed fix round may change the Task 1 allowlist. Once Task 1 is approved, Task 2 layers deterministic traversal and lightweight Swing accessibility over the existing immutable projection, layout, geometry, paint, and interaction values. Every task and the complete branch receive independent review.

**Tech Stack:** Java 8 source/target compatibility, Java 21.0.8-zulu runtime, Gradle, JUnit 4, AssertJ, Mockito, Swing/AWT, immutable Graph Workspace projection/layout/geometry values.

## Global Constraints

- Follow `AGENTS.md`: Java source and target compatibility are 8, UTF-8 encoding, four-space indentation, JUnit 4/AssertJ/Mockito tests, and use `gradle`, never Maven or `gradlew`.
- Use exactly `~/.sdkman/candidates/java/21.0.8-zulu`; set `JAVA_HOME` to that path and prepend `$JAVA_HOME/bin` to `PATH` for every Gradle command. Verify the path exists before implementation and never substitute another JDK.
- Preserve the accepted carry-forward commits `56eee93d9c5432182519a23a886f181658defa8c`, `8d54ecda2157c06baa9b765cc92eb2a82e834506`, `54cab57876bb73bde13945bbbb8493ed7d34ab66`, `8ef6d2e88043ae406a49e07aa2b0608c40c62f76`, and `e740e9c741f1f2aa6db4c0567e1957bf0416a63d`; do not reset, rewrite, or recommit them.
- Preserve every terminal predecessor root, including `.superpowers/sdd/v5`, `.superpowers/sdd/v6`, and `.superpowers/sdd/v7`, byte-for-byte. Their states, reports, prompts, transcripts, and audit artifacts are diagnostic history only. Do not cite a blocked-run report as fresh evidence.
- Continue in `/data/home/guest/Development/freeplane/.worktrees/graph-batch-e-recovery`; do not create another Git worktree. Preserve the pre-existing untracked `.codegraph/` directory, ignored artifacts, and unrelated user changes. Never clean, reset, checkout, stash, or revert them.
- Every child reads only its dispatched brief and listed paths. Render prompts with `sdd-state render-prompt`, persist the complete rendered bytes, pass those bytes verbatim to `spawn_subsession`, record the returned session immediately, and compare the completed child's first user message byte-for-byte before admitting its report. Any mismatch is terminal for that run and is never reissued or adopted.
- Keep every source change inside the task allowlist. Do not add dependencies, resources, translations, exported APIs, source-model access, workspace commands, print/export APIs, or compatibility fallbacks.
- `CanvasState`, `GraphProjection`, `LayoutFrame`, `GraphGeometry`, `GraphViewport`, and `GraphPaintState` remain immutable at ownership boundaries; canvas mutation is EDT-local only.
- Canvas code never reads a Freeplane `MapModel`/`NodeModel`, calls a transformer, executes a workspace command, changes a map, writes a file, or exposes a GraphStream type.
- Geometry is the sole source for rendered bounds, hit bounds, traversal positions, and accessible bounds. Use existing `NodeGeometry` and `HullGeometry`; never recompute prominence or infer scale from raw relationships.
- Safe text comes only from projected labels and projected enclosure labels; use `Locale.ROOT` for case-insensitive search normalization. Excluded and suppressed content must remain absent.
- Preserve exactly the emphatic and subtle visible enclosure tiers. `BoundaryTier.SUPPRESSED` is never visible, hittable, traversable, or accessible.
- Adaptive target limits and Task 25 rendering behavior remain exact. Counts above targets do not disable endpoints, intents, search results, navigation, inspection, or accessible children.
- The keyboard rule is exact: selected unmodified arrows traverse, no-selection unmodified arrows pan, and Shift+arrow always accelerates pan. Enter opens the selected source endpoint. Escape cancels transient connection state before clearing selection.
- `GraphIntent` retains exactly its nine existing public concrete nested types.
- Accessible descriptions append `NodeProminence.visibleOutgoingTargets()` only when nonzero, never state a scale factor, and include map identity as text as well as color.
- Tasks 25-27 have no backlog-prescribed mutant. Do not invent a production mutant. Disposable archive probes may mutate only `/tmp` files and must be deleted.
- Redirect every long Gradle or disposable-probe command to a temporary log, inspect a short tail and XML results, and remove the log before reporting.
- Before any source-changing commit, require an empty index, stage exactly the task allowlist, run `git diff --check`, verify staged names, and use a subject beginning `2026-08-10-graph-workspace:`.
- The final Frontier review covers the complete branch from merge base `02f02355d9a33851a8a4417c4610d1897f716a50` through final `HEAD` using fresh evidence.

## Task 1: Audit And Re-certify Committed Finite Edge Hit Testing

**Implementer tier:** Capable

**Files:**

- Read-only audit: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphHitIndex.java:1-end`
- Read-only audit: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPainter.java:1-end`
- Read-only audit: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphSearchModel.java:1-end`
- Read-only audit: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphInteractionController.java:1-end`
- Read-only audit: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphInteractionControllerShould.java:1-end`
- Read-only audit: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphSearchModelShould.java:1-end`
- Read-only audit: `docs/superpowers/specs/2026-08-19-graph-workspace-batch-e-dispatch-mismatch-v8-recovery-design.md:1-end`
- Review-directed fix allowlist only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphHitIndex.java` and `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphInteractionControllerShould.java`

**Interfaces:**

- Audit the exact committed source/test range `8d54ecda2157c06baa9b765cc92eb2a82e834506..e740e9c741f1f2aa6db4c0567e1957bf0416a63d`, including the bounded safe-search correction `8ef6d2e88043ae406a49e07aa2b0608c40c62f76`.
- The initial implementer produces an audit report and makes no source, index, or `HEAD` changes. A reviewer-directed fixer may modify only the two explicit fix paths if a fresh review opens a load-bearing finding.
- Fresh evidence must independently establish finite ordinary and extreme edge-hit behavior, cancellation residuals, subnormal offsets, zero-length segments, projection endpoint clamping, tolerance and nearest-edge ordering, non-finite rejection, layout-anchor precedence, suppressed-hull exclusion, node-before-hull ordering, all Task 26 interaction intents, and projection-only safe search.

- [ ] **Step 1: Establish the clean committed baseline without mutation**

Record the current `HEAD`, branch, empty index, full status, and exact source range. Confirm the source baseline contains `e740e9c741f1f2aa6db4c0567e1957bf0416a63d` and that no source path is dirty. Inspect the two files changed by that commit and verify the earlier Task 26 and safe-search commit path ranges. Do not stage, commit, reset, checkout, clean, or modify source.

- [ ] **Step 2: Run fresh focused and compatibility gates**

Use exactly Zulu 21 and bounded logs for:

````bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.canvas.GraphInteractionControllerShould' --tests 'org.freeplane.plugin.graph.canvas.GraphSearchModelShould' -PTestLoggingFull
````

````bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.canvas.GraphViewportShould' --tests 'org.freeplane.plugin.graph.canvas.GraphCanvasPaintShould' --tests 'org.freeplane.plugin.graph.canvas.AdaptiveRenderingPolicyShould' --tests 'org.freeplane.plugin.graph.canvas.GraphInteractionControllerShould' --tests 'org.freeplane.plugin.graph.canvas.GraphSearchModelShould' -PTestLoggingFull
````

Record concrete result counts. A missing environment gate is reported as missing evidence, never as a pass.

- [ ] **Step 3: Audit the finite arithmetic and falsifiability**

Read the actual `ScaledValue` operations and `pointToSegmentDistance` call path. Check independently meaningful finite differences, cancellation in projection and cross products, exponent bookkeeping, positive subnormal preservation, zero-length handling, endpoint clamping, finite tolerance comparison, non-finite rejection, and deterministic nearest-edge ties. Run focused disposable probes or tests under `/tmp` where needed. Confirm the existing regressions would fail against a relevant predecessor and that all new evidence is on the real `GraphHitIndex` path. Do not modify source during this audit.

- [ ] **Step 4: Audit Task 26 interaction and safe-search boundaries**

Verify layout-anchor precedence and painted enclosure edge segments in `GraphPainter`/`GraphHitIndex`, node-before-hull and suppressed-hull ordering, every interaction intent branch and uninstall behavior, and projection-only search with safe projected labels/map identity. Check that fixture sentinels are inserted and no source model or transformed text leaks through. Record exact findings, if any, with file and line evidence.

- [ ] **Step 5: Write the no-source-change audit report**

Inspect status and the source diff again. Write exactly one implementer report with `STATUS: DONE` when the audit evidence is complete, or `BLOCKED`/`NEEDS_CONTEXT` only when required evidence is genuinely unavailable. The normal audit has no commit. Do not claim approval; independent review follows.

- [ ] **Step 6: Review-directed fix gate (only after a load-bearing review finding)**

If and only if the state machine opens a fix round, the fresh fixer may add the smallest falsifiable regression and correction in the two explicit Task 1 paths. Before committing, require an empty index, stage exactly those paths, run `git diff --check`, verify names, use a `2026-08-10-graph-workspace:` subject, and report the full SHA. A fix round must not alter any other path or rewrite the committed baseline.

## Task 2: Implement Backlog Task 27 Keyboard Traversal And Accessible Virtual Children

**Implementer tier:** Advanced

**Files:**

- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/TraversalDirection.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphTraversalOrder.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/AccessibleGraphCanvas.java`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphCanvas.java:1-end`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphInteractionController.java:1-end`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/AccessibleGraphCanvasShould.java`

**Interfaces:**

- Consume the valid Task 25/26 canvas values, `CanvasState`, immutable projected node/enclosure/pin/prominence values, `GraphGeometry`, `NodeGeometry`, `HullGeometry`, `ProjectedEndpointKey`, `GraphIntent`, `GraphInteractionController`, and Swing accessibility APIs.
- Produce `public enum TraversalDirection { UP, DOWN, LEFT, RIGHT }` and `public final class GraphTraversalOrder` with `tabOrder(CanvasState)` and `nearest(CanvasState, ProjectedEndpointKey, TraversalDirection)` returning the specified collection types.
- Define package-private `AccessibleGraphCanvas extends AccessibleContext` and implements `AccessibleComponent` as the root context returned from `GraphCanvas.getAccessibleContext()`. Virtual endpoint children implement `Accessible`, `AccessibleAction`, and `AccessibleComponent` as needed without becoming `JComponent` instances.
- Keep contexts and children free of mutable snapshots. Every query resolves the canvas's current immutable state and paint state. Existing public Task 25/26 APIs remain source compatible.

- [ ] **Step 1: Write falsifiable traversal and accessibility tests**

Create `AccessibleGraphCanvasShould` with a local fixture containing visible node endpoints, a non-suppressed enclosure endpoint, a suppressed enclosure endpoint, pins, distinct map names, safe labels, a zero-reach node, and a node with three visible outgoing targets. Place candidates with equal directional distance and keys whose natural order determines the winner.

Assert `tabOrder` includes every visible node and non-suppressed enclosure exactly once in `ProjectedEndpointKey` order and excludes suppressed endpoints. Assert `nearest` excludes the source, uses strict half-plane filtering, minimum squared distance, deterministic key ties, and empty results for missing source geometry or no candidate. Use node centers and hull label anchors from `GraphGeometry` so traversal and accessible bounds agree with painted/hit geometry.

Install a controller and test Tab/Shift-Tab cycling, selected unmodified arrows emitting one `ChangeSelection`, no-selection arrows panning without selection changes, accelerated Shift arrows regardless of selection, Enter emitting `OpenSourceNode`, and Escape cancelling preview before clearing selection. Test the accessible root and virtual children for current child count, safe name/map identity, role/actions, selected/pinned state, prominence text only when nonzero, viewport-transformed geometry bounds, activation behavior, and live updates without stale snapshots. Assert no excluded placeholder, raw/transformed text, color hex, scale factor, or suppressed endpoint appears.

- [ ] **Step 2: Run the red tests and verify the failure cause**

Run:

````bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.canvas.AccessibleGraphCanvasShould' -PTestLoggingFull
````

Confirm the failure is the absent Task 27 production API and not a Task 25/26 regression.

- [ ] **Step 3: Implement deterministic traversal order**

Implement `TraversalDirection` and `GraphTraversalOrder`. Resolve visible node positions from `NodeGeometry.center()` and non-suppressed enclosure positions from hull `labelAnchor()` in `GraphGeometry`; omit geometry-less endpoints. Sort `tabOrder` by `ProjectedEndpointKey.compareTo` and return an unmodifiable list. For `nearest`, require nonnull inputs, exclude the source, apply strict directional half-plane comparisons, compare squared distances without square roots, break equal distances by key, and return empty when appropriate. Preserve the existing positive-Y world-axis convention.

- [ ] **Step 4: Implement keyboard behavior and virtual accessibility**

Update `GraphInteractionController` so selected unmodified arrows use `GraphTraversalOrder.nearest` and emit `ChangeSelection`, while no-selection and Shift-arrow paths preserve Task 26 pan semantics. Add Tab/Shift-Tab cycling, Enter source opening, and Escape preview-cancellation priority. Update `GraphCanvas` with a lazy cached accessible context and package-visible helpers/controller registration without store or map access.

Implement the root `AccessibleGraphCanvas` and lightweight virtual children. Derive current children on demand, expose an actionable graph-endpoint role and selection/open actions, transform geometry with the current viewport, and build safe names/descriptions from projected labels, map names, endpoint type, selection/pin state, and nonzero `NodeProminence.visibleOutgoingTargets()`. Never expose scale, color, raw source text, excluded content, or suppressed endpoints.

- [ ] **Step 5: Run focused and full canvas verification**

Run the focused Task 27 command again, then:

````bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.canvas.*Should' -PTestLoggingFull
````

Confirm Task 25, Task 26, and Task 27 canvas tests pass together, including immutable paint, map-color binding, typography, enlarged-node bounds, safe search, interaction uninstall, traversal, and accessibility.

- [ ] **Step 6: Commit Task 27 with the exact allowlist**

Run `git diff --check`, require an empty index, stage exactly the six Task 27 paths listed above, verify staged names, and commit with:

````bash
git commit -m "2026-08-10-graph-workspace: Make the graph keyboard accessible"
````

Report the full SHA and exact focused/full results. Do not stage SDD artifacts or unrelated paths.

### Final Verification

After Task 1 audit/review and any bounded fix/re-review, and after Task 2 is complete, dispatch a fresh Frontier final reviewer over the complete branch from merge base `02f02355d9a33851a8a4417c4610d1897f716a50` through final `HEAD`. The reviewer must run:

````bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test -PTestLoggingFull
````

The final report must name the carry-forward Task 25/26 commits, the safe-search correction, any V8 Task 1 fix and Task 2 commit, focused/full results, preserved terminal runs, and unrelated pre-existing state without reverting it.
