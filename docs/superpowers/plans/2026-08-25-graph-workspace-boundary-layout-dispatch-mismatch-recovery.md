# Graph Workspace Boundary Layout Dispatch-Mismatch Recovery Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to execute this plan task-by-task.

**Goal:** Independently certify the committed dynamic scroll-surface candidate after the terminal dispatch mismatch, then finish the approved small-workspace layout spread.

**Architecture:** Task 1 is a source-read-only audit of the exact five-file Task 2 range and is followed by an ordinary independent review gate. Task 2 changes only the GraphStream seed envelope and its focused regression. Every child receives a complete renderer-produced role envelope through a byte-stable short pointer, so no result is admitted without a verified dispatch transcript.

**Tech Stack:** Java 8-compatible source, Zulu Java 21.0.8, Gradle, JUnit 4, AssertJ, Mockito, Swing `JScrollPane` and `JViewport`, and the private GraphStream 1.3 layout adapter.

## Global Constraints

- Use Java 8-compatible production APIs and run Gradle with `JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu`; use `gradle`, never Maven or the Gradle wrapper.
- Preserve terminal predecessor `.superpowers/sdd/2026-08-25-graph-workspace-boundary-layout` byte-for-byte. Do not edit or use its reports, prompts, transcripts, state, or audit projection as successor correctness evidence.
- Do not modify the pre-existing untracked `docs/superpowers/plans/2026-08-25-graph-workspace-boundary-layout.md`; it is not a successor deliverable.
- Task 1 audits immutable source range `9a551a937d4643f41db0b93f71123f209f1f5b38..106c6374dd10ada5f3e9f1f88593e45f22cf0558`, which contains only `d570bfbe33d235303a824e36a0057fcd7db23229`, `dd9dcf4bc8d88a88dddd090c1827c5dea7db6d6d`, and `106c6374dd10ada5f3e9f1f88593e45f22cf0558` after the reviewed Task 1 base.
- Task 1 initial audit is source-read-only. A reviewer-authorized fix may modify only its five explicit audited paths; no build, dependency, translation, resource, persistence-schema, source-map, relationship, or shared-fixture change is allowed.
- Task 2 may modify only `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/GraphStreamLayoutEngine.java` and `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/TypedForcesShould.java`.
- Preserve exact projected enclosure endpoint identity, relationship resolution, world-coordinate hit testing, dragging, pins, prominence radii, map correction, deterministic seed derivation, force quality, and saved viewport semantics.
- One active map renders depth 1 emphatically and depth 2 subtly while suppressing depths 0 and 3+; two or more active maps render depth 0 emphatically and depth 1 subtly while suppressing depths 2+.
- Persist each complete `sdd-state render-prompt` output as a role envelope, dispatch only a short ASCII pointer without a trailing newline, compare candidate/pointer bytes before spawn, record the returned session ID immediately, and compare raw child-first-message/pointer bytes before report admission. Any mismatch is terminal for this successor run.
- Before every source-changing commit, require an empty index, stage exactly the allowlisted paths, run `git diff --cached --check`, and use a subject beginning `2026-08-10-graph-workspace:`.
- Final Frontier review covers `7cbbf60cab81ed4189327a374f44ddecd420a51d..HEAD`; finish with `git diff --check` clean and the full `:freeplane_plugin_graph:test` suite green.

## Task 1: Audit the committed dynamic scroll surface

**Implementer tier:** Capable

**Files:**
- Read-only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphCanvas.java:1-end`
- Read-only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java:350-700`
- Read-only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/WorkspaceToolbar.java:60-70,240-250,315-336`
- Read-only: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java:820-end`
- Read-only: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java:180-360,730-900`
- Read-only: `docs/superpowers/specs/2026-08-25-graph-workspace-boundary-layout-design.md:1-end`
- Read-only: `docs/superpowers/specs/2026-08-25-graph-workspace-boundary-layout-dispatch-mismatch-recovery-design.md:1-end`
- Modify only in a controller-authorized fix round: the five production/test paths listed above
- Initial audit output: the normal implementer report under the successor run root only; no source or Git commit

**Interfaces:**
- Consumes immutable range `9a551a937d4643f41db0b93f71123f209f1f5b38..106c6374dd10ada5f3e9f1f88593e45f22cf0558`, the approved boundary-layout design, `GraphCanvas.visibleViewport()`, `GraphCanvas.isProgrammaticViewportChange()`, `GraphWorkspaceWindowModel.publishExternalViewport()`, and toolbar viewport callbacks.
- Produces a fresh bounded audit report with source identity, behavior, test, and falsifiability evidence while leaving all source, test, index, and commit bytes unchanged.

- [ ] **Step 1: Pin immutable candidate identity and source scope**

Record `HEAD`, index status, and untracked files. Require `106c6374dd10ada5f3e9f1f88593e45f22cf0558` to be an ancestor of `HEAD`; verify its parent is `dd9dcf4bc8d88a88dddd090c1827c5dea7db6d6d` and its subject is `2026-08-25-graph-workspace-boundary-layout: Prove viewport persistence`.

Verify the exact linear range and one five-path allowlist:

```bash
git log --format='%H %P %s' --reverse 9a551a937d4643f41db0b93f71123f209f1f5b38..106c6374dd10ada5f3e9f1f88593e45f22cf0558
git diff --name-status 9a551a937d4643f41db0b93f71123f209f1f5b38..106c6374dd10ada5f3e9f1f88593e45f22cf0558
git diff --check 9a551a937d4643f41db0b93f71123f209f1f5b38..106c6374dd10ada5f3e9f1f88593e45f22cf0558
```

Use `git show 106c6374dd:<path> | sha256sum` to verify the five exact SHA-256 values recorded in the recovery design. Confirm no successor source path differs from `106c6374dd` and that the predecessor state's phase remains `DISPATCH_MISMATCH_BLOCKED`, without reading its child report or transcript.

- [ ] **Step 2: Audit scroll-surface and persistence behavior at production boundaries**

Trace the candidate through `GraphCanvas`, `GraphWorkspaceWindowModel`, and `WorkspaceToolbar`. Verify all of the following with concrete file:line evidence:

- `visibleViewport()` maps a scroll pane view position and extent to world coordinates while `viewport()` remains the rendering anchor;
- canvas surface dimensions include both extrema relative to the current rendering anchor, add a fixed world margin on both sides before zoom scaling, exclude suppressed hulls, preserve the minimum surface, and retain world-coordinate painting/hit testing;
- Fit Graph uses the containing `JViewport` extent and toolbar zoom, fit, and reset start from `visibleViewport()`;
- only external viewport changes can publish persistence commands; synchronous resizing, deferred post-revalidate clamping, and toolbar reset/fit/zoom positioning are guarded;
- existing tests prove scroll integration, visible-world persistence rather than anchor persistence, off-center extrema reachability, and no duplicate or transient persistence command.

Record any real residual as a concrete source/test location and impact. Do not infer correctness from predecessor reports.

- [ ] **Step 3: Re-run focused and full graph-plugin verification**

Run fresh verification with the prescribed JDK:

```bash
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test --tests '*GraphViewportShould' --tests '*GraphWorkspaceWindowModelShould' --tests '*GraphCanvasPaintShould' --tests '*GraphInteractionControllerShould' -PTestLoggingFull --rerun-tasks
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test -PTestLoggingFull --rerun-tasks
```

Record exact exit statuses and any test totals available from the reports. A failure is a load-bearing audit result, not an environmental waiver.

- [ ] **Step 4: Prove the two event-suppression regressions are falsifiable without changing the active worktree**

Create disposable archives from `106c6374dd10ada5f3e9f1f88593e45f22cf0558`, delete them after each probe, and keep the active source untouched.

For deferred resize, change only `GraphCanvas.isProgrammaticViewportChange()` in the archive from `viewportPositioningDepth > 0 || pendingViewportClamps > 0` to `viewportPositioningDepth > 0`, then run only `GraphWorkspaceWindowModelShould.suppressesDeferredSurfaceResizeViewportPersistenceAfterExternalScroll`. Require it to fail because the deferred clamp emits an unwanted viewport command.

For reset, change only the archive's `GraphWorkspaceWindow.java` binding from `toolbar.setViewportOperation(this::runWithViewportEventsSuppressed);` to `toolbar.setViewportOperation(action -> action.run());`, then run only `GraphWorkspaceWindowModelShould.suppressesResetViewportPersistenceAfterExternalScroll`. Require it to fail because reset callback positioning emits extra viewport commands.

Report the exact red failure summaries, delete archive/log residue, and re-verify active source hashes, `HEAD`, and source/index status.

- [ ] **Step 5: Write the audit report and preserve source**

Write exactly one report at the dispatched report path. Return `DONE` only if the identity, behavior, focused/full test, and both falsifiability gates succeed. Include `CHANGES: no source changes`, all source hashes, exact commands/results, red probe summaries, residual findings if any, and explicit confirmation that predecessor child output was not used as evidence.

Confirm no source/test/index changes, no new source commit, no temporary archive/log residue, and that only the normal audit report exists beneath the successor run root. A later controller-authorized fix may change only the five audited paths.

## Task 2: Spread small graph layout initialization

**Implementer tier:** Standard

**Files:**
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/GraphStreamLayoutEngine.java:38-43,325-328`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/TypedForcesShould.java:54-74,230-260`

**Interfaces:**
- Consumes `GraphStreamLayoutEngine`, `LayoutRequest`, `GraphProjection`, identity-derived `initialPosition(...)`, and `LayoutFrame.positions().nodes()`.
- Produces the same package-private layout implementation with one `INITIAL_POSITION_SPREAD = 50.0` world-unit seed scale for every graph size.

- [ ] **Step 1: Add a red small-workspace initial-position regression**

Add `smallWorkspaceInitialPositionsAreNotCollapsedIntoTheOrigin` next to `produceIdenticalFramesForEqualRequests`. Use `baseline(1)` and call `engine.apply(request(WORKSPACE_ONE, projection, projection, Collections.<PinProjection>emptyList()))` without calling `step()`.

Add a small helper that calculates the greatest Euclidean distance between distinct values in `frame.positions().nodes()`. Assert the greatest initial node distance is greater than `1.0`. Keep the existing equal-request determinism test unchanged, so the new assertion proves a material spread without weakening stable seed behavior.

- [ ] **Step 2: Run the focused test and confirm red**

Run:

```bash
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test --tests '*TypedForcesShould' -PTestLoggingFull
```

Require the new assertion to fail against the current `0.002` initial square. Do not change the assertion or threshold to make the old implementation pass.

- [ ] **Step 3: Replace the size-dependent seed envelope**

Remove `LARGE_WORKSPACE_NODE_THRESHOLD`, `DEFAULT_INITIAL_POSITION_SPREAD`, and `LARGE_WORKSPACE_INITIAL_POSITION_SPREAD`. Add exactly:

```java
private static final double INITIAL_POSITION_SPREAD = 50.0;
```

Make `initialPositionSpread(final GraphProjection projection)` return `INITIAL_POSITION_SPREAD` for every projection. Preserve SHA-256 seed bytes, random ordering, particle topology, pins, force quality `0.10`, cross-map displacement cap, prominence radius, and reset behavior.

- [ ] **Step 4: Commit the verified two-path change**

Run:

```bash
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test --tests '*TypedForcesShould' --tests '*GraphStreamBoundaryShould' -PTestLoggingFull
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test -PTestLoggingFull
git diff --check
```

Require an empty index before staging. Stage exactly the two allowlisted paths, run `git diff --cached --check`, verify the staged name list, then commit:

```bash
git commit -m "2026-08-10-graph-workspace: Spread small graph layouts"
```

Write the normal report with the observed red result, green commands, changed-file list, and commit SHA.
