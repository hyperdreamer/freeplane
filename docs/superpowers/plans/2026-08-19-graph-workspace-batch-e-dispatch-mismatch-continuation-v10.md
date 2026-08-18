# Graph Workspace Batch E Dispatch-Mismatch Continuation Implementation Plan V10

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic subagent-driven-development controller to execute this plan task-by-task.

**Goal:** Correct finite edge-hit cancellation failures with fresh evidence, independently certify Task 26 interaction/search behavior, and implement Backlog Task 27 keyboard traversal and accessible virtual children.

**Architecture:** Continue in the existing recovery worktree. Task 1 independently reproduces and fixes bounded `GraphHitIndex` arithmetic only in its two-path allowlist, then receives a fresh Frontier review and any state-machine-directed re-review. Task 2 layers deterministic traversal and lightweight virtual accessibility on immutable canvas values. The complete branch receives a fresh Frontier final review.

## Global Constraints

- Follow `AGENTS.md`: Java 8 source/target compatibility, UTF-8, four-space indentation, JUnit 4/AssertJ/Mockito, and Gradle only.
- Use exactly `~/.sdkman/candidates/java/21.0.8-zulu` with `JAVA_HOME` and `$JAVA_HOME/bin` prepended for every Gradle command.
- Preserve accepted commits `56eee93d9c5432182519a23a886f181658defa8c`, `8d54ecda2157c06baa9b765cc92eb2a82e834506`, `54cab57876bb73bde13945bbbb8493ed7d34ab66`, `8ef6d2e88043ae406a49e07aa2b0608c40c62f76`, and `e740e9c741f1f2aa6db4c0567e1957bf0416a63d`; never reset, rewrite, or recommit them.
- Preserve terminal predecessor roots including V5 through V9 byte-for-byte. Their reports and child outputs are diagnostic history, never fresh evidence.
- Continue in `/data/home/guest/Development/freeplane/.worktrees/graph-batch-e-recovery`; do not create another Git worktree. Preserve `.codegraph/`, ignored artifacts, and unrelated user state. Never clean, reset, checkout, stash, or revert.
- For each child, persist the full `sdd-state render-prompt` output as a read-only role envelope under the run root. The actual `spawn_subsession` prompt is a short persisted pointer to that envelope because the API has no file-valued prompt input. Before spawning, `cmp` the candidate pointer bytes against the persisted pointer. The child must read the envelope before acting. Record the returned session immediately and compare its first user message with the persisted pointer byte-for-byte before admitting its report. Any mismatch is terminal and never reissued.
- Keep source changes inside each explicit allowlist. Do not add dependencies, resources, translations, exported APIs beyond Task 27, source-model access, workspace commands, print/export APIs, or compatibility fallbacks.
- `CanvasState`, `GraphProjection`, `LayoutFrame`, `GraphGeometry`, `GraphViewport`, and `GraphPaintState` remain immutable at ownership boundaries; canvas mutation remains EDT-local. Canvas code never reads `MapModel`/`NodeModel`, calls a transformer, changes a map, writes files, or exposes GraphStream.
- Geometry is the sole source for rendered bounds, hit bounds, traversal positions, and accessible bounds. Suppressed enclosures are never visible, hittable, traversable, or accessible. Safe text comes only from projected labels and projected enclosure labels; use `Locale.ROOT` for search normalization.
- Preserve Task 25 adaptive rendering levels/limits and Task 26 interactions. Keyboard behavior is exact: selected arrows traverse, no-selection arrows pan, Shift arrows accelerate pan, Enter opens selection, and Escape cancels preview before clearing selection.
- `GraphIntent` retains its nine existing public nested types. Accessibility includes map identity and nonzero `visibleOutgoingTargets()` but never scale factors, color hex, raw text, excluded content, or suppressed endpoints.
- Use bounded Gradle logs and delete disposable `/tmp` probes. Do not invent production mutants.
- Before source commits require an empty index, exact staged allowlist, `git diff --check`, and a `2026-08-10-graph-workspace:` subject.
- Final Frontier review covers merge base `02f02355d9a33851a8a4417c4610d1897f716a50` through final `HEAD` using fresh evidence.

## Task 1: Correct Finite Edge Hit Cancellation

**Implementer tier:** Capable

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphHitIndex.java:1-end`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphInteractionControllerShould.java:1-end`
- Read-only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPainter.java:1-end`
- Read-only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphInteractionController.java:1-end`
- Read-only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphSearchModel.java:1-end`
- Read-only: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphSearchModelShould.java:1-end`
- Read-only: `docs/superpowers/specs/2026-08-19-graph-workspace-batch-e-dispatch-mismatch-v10-recovery-design.md:1-end`

**Interfaces:** Consume the committed Task 26 range plus `e740e9c741f1f2aa6db4c0567e1957bf0416a63d`. Produce a minimal Java-8-compatible point-to-segment correction and regression evidence. Preserve valid finite coordinate behavior, geometry/anchor selection, tolerance semantics, deterministic tie selection, and all Task 26 interaction/search boundaries.

- [ ] **Step 1: Establish the source baseline and run pre-change gates**

Record `HEAD`, status, empty index, source range paths, and exact two-file diff history without mutation. Under Zulu 21 and bounded logs, run focused interaction/search and named viewport/paint/adaptive selections. Inspect XML counts. Stop if the environment prevents evidence.

- [ ] **Step 2: Add a falsifiable cancellation regression**

Use the real `GraphHitIndex.from(CanvasState).edgeAt` path with finite coordinates whose exact cross product is positive but whose separately rounded binary64 products can cancel. Assert the point is rejected at zero tolerance and below its true positive distance, and accepted only at a sufficient tolerance. First prove failure against the pre-correction `e740e9c741` production snapshot with a disposable `/tmp` copy; do not change production source before a focused red result.

- [ ] **Step 3: Implement cancellation-safe finite arithmetic**

Repair projection and cross-product calculations so intermediate product rounding cannot erase meaningful finite residuals. Use a minimal Java-8-compatible approach, such as exact binary expansion or compensated product/residual accumulation, consistently where needed for endpoint projection and perpendicular distance. Preserve all finite operands, avoid squared overflow/common-coordinate scaling, distinguish positive subnormal distance from zero, reject non-finite final distances, and retain nearest-edge/key tie behavior.

- [ ] **Step 4: Run focused finite and Task 26 verification**

Run the cancellation regression green and rerun extreme-span, mixed-magnitude, tiny-offset, positive-subnormal, zero-length, endpoint, anchor, interaction, and safe-search contracts through existing/added tests. Run focused interaction/search and named compatibility selections with exact test counts. Inspect actual diff and status; remove temporary artifacts.

- [ ] **Step 5: Commit the bounded correction**

Require empty index, stage exactly `GraphHitIndex.java` and `GraphInteractionControllerShould.java`, verify names, run `git diff --check`, and commit with a subject beginning `2026-08-10-graph-workspace:`. Write a report containing red/green evidence, exact test counts, arithmetic rationale, and full SHA.

## Task 2: Implement Backlog Task 27 Keyboard Traversal And Accessible Virtual Children

**Implementer tier:** Advanced

**Files:**

- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/TraversalDirection.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphTraversalOrder.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/AccessibleGraphCanvas.java`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphCanvas.java:1-end`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphInteractionController.java:1-end`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/AccessibleGraphCanvasShould.java`

**Interfaces:** Create public `TraversalDirection { UP, DOWN, LEFT, RIGHT }` and public `GraphTraversalOrder` with `tabOrder(CanvasState)` and `nearest(CanvasState, ProjectedEndpointKey, TraversalDirection)`. Add package-private `AccessibleGraphCanvas extends AccessibleContext` implementing `AccessibleComponent` as `GraphCanvas`'s root, with lightweight virtual endpoint children implementing required `Accessible`, `AccessibleAction`, and `AccessibleComponent` interfaces without `JComponent` children or mutable snapshots.

- [ ] **Step 1: Write falsifiable traversal and accessibility tests**

Create a local fixture with visible nodes, visible/suppressed enclosures, pins, distinct map names, safe labels, zero/nonzero prominence, and equidistant directional candidates. Assert stable visible key tab order, strict directional nearest selection/ties/missing geometry, keyboard traversal/pan/activation/escape behavior, virtual child currentness/actions/text/bounds, and excluded raw/color/scale/suppressed content.

- [ ] **Step 2: Run red Task 27 test**

Run `gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.canvas.AccessibleGraphCanvasShould' -PTestLoggingFull` under Zulu 21 and establish missing Task 27 API is the failure.

- [ ] **Step 3: Implement traversal**

Use node centers and non-suppressed hull label anchors from `GraphGeometry`. Omit geometry-less endpoints. Sort tab order by key. For nearest exclude source, apply strict half-planes, compare squared distances, and break equal distances by key.

- [ ] **Step 4: Implement keyboard and virtual accessibility**

Preserve pan semantics while adding selected-arrow traversal, Tab/Shift-Tab cycling, Enter and Escape rules. Add lazy root context and virtual children resolving current immutable state each call, geometry transformed by current viewport, safe projected label/map identity/actions/state text, and prominence wording only when nonzero.

- [ ] **Step 5: Run green focused and canvas suite**

Rerun the focused Task 27 test and the full `org.freeplane.plugin.graph.canvas.*Should` selection under Zulu 21, with bounded logs and XML inspection.

- [ ] **Step 6: Commit Task 27 exactly**

After empty-index and `git diff --check` gates, stage exactly the six listed paths and commit `2026-08-10-graph-workspace: Make the graph keyboard accessible`. Report full SHA and counts.

### Final Verification

After both task reviews and any bounded fix/re-review, a fresh Frontier final reviewer covers the entire branch and runs `JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test -PTestLoggingFull`. The final report names all carry-forward/new commits, exact results, preserved terminal roots, and clean state.
