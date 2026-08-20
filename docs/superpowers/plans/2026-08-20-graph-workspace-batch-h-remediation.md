# Graph Workspace Batch H Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.

**Goal:** Repair the final-review defects in Graph Workspace presentation, rendering, read-only menus, and extension shutdown while preserving the existing entry-point, dialog, lifecycle, and separate undo contracts.

**Architecture:** Add one immutable plugin-internal presentation snapshot at `GraphWorkspaceViewBinding`, carrying persisted display settings and validated map colors without exposing stores or map models to Swing. Apply that snapshot through `GraphWorkspaceWindowModel` and `GraphCanvas`, and add an explicit idempotent shutdown seam to `DefaultGraphWorkspaceController` used by `GraphModeExtension`.

**Tech Stack:** Java 8 source and bytecode, Java Swing/AWT, Gradle, JUnit 4, AssertJ, Mockito, immutable value objects, buffered-image paint probes, and the existing Freeplane graph workspace control/canvas modules.

## Global Constraints

- Continue in `/data/home/guest/Development/freeplane/.worktrees/graph-workspace-batch-h-ui-shell`; do not create a new worktree.
- Use `JAVA_HOME=$HOME/.sdkman/candidates/java/21.0.8-zulu` and `gradle`, never Maven or the Gradle wrapper.
- Preserve Java 8 source/target compatibility, four-space Java indentation, and repository testing conventions.
- `freeplane_plugin_graph` extends MindMap mode only, makes no `freeplane_api` changes, and neither subclasses nor replaces `MapView`.
- Freeplane `MapModel` and `NodeModel` reads remain EDT-only. Swing code communicates through `GraphWorkspaceHandle`, `GraphWorkspaceViewBinding`, and `WorkspaceCloseController`, never directly through stores, routers, map models, or the Freeplane controller.
- The graph window remains modeless and headless-testable. Tests must not call `setVisible`.
- Keep workspace history and source-map history distinct: `Ctrl+Z`/`Ctrl+Y` route only workspace undo/redo; source-map undo is separately named, has no graph-window shortcut, and is independently status-controlled.
- Presentation snapshots contain only immutable safe display values: `DisplaySettings`, `MapReferenceId`, validated color strings, viewport, and existing safe map rows. Do not expose `WorkspaceDocument`, `MapReference`, raw node text, stores, or map models to Swing.
- Shutdown is idempotent, attempts cleanup for every owned session, and does not change user save-and-close semantics.
- Do not modify `freeplane_api`, `MapView`, Weblate-managed editor translations, or unrelated graph group/map-actor behavior.
- The predecessor run `.superpowers/sdd/2026-08-20-graph-workspace-batch-h-task3-report-recovery` is terminal `FINAL_BLOCKED`; preserve its state, reports, and final-review findings unchanged. The carried finding IDs are F-2, F-3, F-4, and F-5.
- Every implementation task writes tests first, runs the focused red gate, implements the smallest correction, runs focused and full verification, stages only its exact listed paths, and commits with an imperative `2026-08-10-graph-workspace:` subject.
- For every dispatch, persist the complete renderer output as a read-only role envelope beneath the fresh run root, dispatch only a short pointer, byte-compare pointer files before spawn, record the returned session immediately, and compare the completed child transcript's first user message byte-for-byte with the stored pointer before admitting its report.
- Redirect verbose Gradle output beneath the active run root. After each child's final verification command, the required report must be the immediate next action before any optional narrative or command.

## Task 1: Apply immutable presentation state to the canvas and shell

**Implementer tier:** Capable

**Files:**
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/GraphWorkspacePresentation.java`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/GraphWorkspaceViewBinding.java:1-end`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/DefaultGraphWorkspaceController.java:300-385`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphTheme.java:70-270`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphCanvas.java:20-250`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPainter.java:35-150`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java:246-1065`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java:1-end`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java:1-end`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/GraphWorkspacePresentationShould.java`

**Interfaces:**
- Consumes: `DisplaySettings`, `MapReferenceId`, `MapReference.color()`, `WorkspaceDocument.displaySettings()`, `WorkspaceDocument.maps()`, `GraphWorkspaceViewBinding.currentViewport()`, `GraphWorkspaceViewBinding.currentMapRows()`, `GraphCommandResult`, and the existing `GraphCanvas`/`GraphPainter` paint path.
- Produces: immutable `GraphWorkspacePresentation` with `displaySettings()` and ordered immutable `mapColors()` entries; `GraphWorkspaceViewBinding.currentPresentation()`; `GraphTheme.resolve(CanvasTheme, Map<MapReferenceId, String>)`; `GraphCanvas.setShowArrowheads(boolean)` and `GraphCanvas.setDimUnrelated(boolean)`; and a window model that applies persisted presentation values and keeps menu enabled state synchronized with read-only/history state.

### Step 1: Add regression tests before production changes

Extend `GraphCanvasPaintShould` with a real `BufferedImage` paint case containing a visible enclosure whose map ID has a persisted approved color. Construct the theme from the new map-color input and assert that painting completes and produces non-background pixels. Add an assertion that arrowheads are absent when `showArrowheads` is false and present when it is true using the existing directional-edge fixture.

Create `GraphWorkspacePresentationShould` covering immutable defensive copies, deterministic map-color ordering, rejection of null/duplicate IDs and invalid colors, and preservation of all `DisplaySettings` fields.

Extend `GraphWorkspaceWindowModelShould` with headless tests that provide a non-default `currentPresentation()` and assert:

- the settings panel starts with the persisted theme, arrowhead, dimming, and remember-viewport values rather than defaults;
- a display command routed through the existing handle updates the canvas theme and dimming/arrowhead state without replacing unrelated settings with defaults;
- a visible-enclosure canvas paint through the model does not throw;
- disabling `rememberViewport` applies the fit policy;
- File/View/Maps menu items mirror read-only state, while non-mutating fit/reset/settings controls and workspace/source-map history retain their independent enablement rules.

Keep all tests headless and do not call `setVisible`.

### Step 2: Run the focused red gate

Run the new and extended tests before implementing production changes:

```bash
mkdir -p "$RUN_ROOT/logs"
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" gradle :freeplane_plugin_graph:test \
  --tests '*GraphWorkspacePresentationShould' \
  --tests '*GraphCanvasPaintShould' \
  --tests '*GraphWorkspaceWindowModelShould' -PTestLoggingFull \
  >"$RUN_ROOT/logs/task-1-red.log" 2>&1
red_status=$?
tail -n 60 "$RUN_ROOT/logs/task-1-red.log"
test "$red_status" -ne 0
```

Confirm the failure is caused by the absent presentation binding/rendering behavior or the missing model wiring, not by a test typo. Do not change production code before this red result.

### Step 3: Implement the immutable presentation seam

Implement `GraphWorkspacePresentation` as a final immutable value with a nested immutable map-color entry containing `MapReferenceId` and the validated color string. Copy and validate all collections at construction, preserve stable input order, and expose unmodifiable views.

Extend `GraphWorkspaceViewBinding` with a default `currentPresentation()` returning `DisplaySettings.defaults()` and an empty palette so existing headless adapters remain source-compatible. In `DefaultGraphWorkspaceController`'s production binding, return a fresh presentation derived from the current workspace document: copy `document.displaySettings()` and each persisted `MapReference.id()/color()` without exposing the document.

Add a `GraphTheme.resolve` overload accepting the immutable ID-to-color palette. Reuse the existing theme palette and color blending behavior, but use the persisted colors for every registered map. Keep existing `List<MapReference>` overloads and tests working where they are still used.

### Step 4: Wire rendering and settings through the window model

In `GraphWorkspaceWindowModel`, capture `binding.currentPresentation()` before creating `WorkspaceSettingsPanel`, initialize the panel from its `DisplaySettings`, and apply the presentation before the first canvas repaint. On every routed command result and canvas-state update, refresh the immutable presentation and apply:

- `GraphTheme.resolve(settings.canvasTheme(), palette)` to the canvas;
- `showArrowheads` to the canvas/painter;
- `dimUnrelatedNodes` to the canvas paint state;
- `rememberViewport` to initial-fit behavior, fitting once when the setting is disabled and retaining the persisted viewport when enabled.

Do not bypass `GraphWorkspaceHandle` for settings commands. Preserve the existing editor-focus callback behavior and separate undo actions.

Add stable menu-item fields or shared actions for File, View, and Maps entries. Update their enabled states from the same `readOnly`, workspace-history, and source-map-history state that controls the underlying toolbar/map-list controls. Read-only must visibly disable save/save-as/add/remove/retry map and other mutation entries, while fit, zoom, reset, and inspection/view controls remain available.

Update `GraphCanvas` and `GraphPainter` with the smallest rendering-state interface needed for arrowhead visibility and dimming. The painter must not call `GraphTheme.mapColor` with an unregistered ID in a valid production presentation.

### Step 5: Verify, scope-check, and commit Task 1

Run the focused suites again, then the full plugin suite:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" gradle :freeplane_plugin_graph:test \
  --tests '*GraphWorkspacePresentationShould' \
  --tests '*GraphCanvasPaintShould' \
  --tests '*GraphWorkspaceWindowModelShould' -PTestLoggingFull \
  >"$RUN_ROOT/logs/task-1-focused.log" 2>&1
focused_status=$?
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" gradle :freeplane_plugin_graph:test -PTestLoggingFull \
  >"$RUN_ROOT/logs/task-1-full.log" 2>&1
full_status=$?
tail -n 50 "$RUN_ROOT/logs/task-1-focused.log"
tail -n 50 "$RUN_ROOT/logs/task-1-full.log"
test "$focused_status" -eq 0
test "$full_status" -eq 0
```

Run `git diff --check`, verify that only the ten listed Task 1 paths changed relative to the task base, verify no test calls `setVisible`, and verify no `freeplane_api` or editor translation path changed. Stage exactly those ten paths and commit:

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/GraphWorkspacePresentation.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/GraphWorkspaceViewBinding.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/DefaultGraphWorkspaceController.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphTheme.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphCanvas.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPainter.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/GraphWorkspacePresentationShould.java
```

Use an imperative commit subject beginning `2026-08-10-graph-workspace:` and write the required implementer report immediately after final verification and commit.

## Task 2: Make extension shutdown deterministic

**Implementer tier:** Capable

**Files:**
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/DefaultGraphWorkspaceController.java:250-760`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/GraphModeExtension.java:1-120`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/DefaultGraphWorkspaceControllerShould.java:1-end`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphPluginIntegrationShould.java:1-end`

**Interfaces:**
- Consumes: `DefaultGraphWorkspaceController.open`, private `openSessions`, `WorkspaceSessionRegistry`, `Session`, `SessionResources`, `closeRemainingResources`, `GraphWorkspaceView.close`, `GraphModeExtension.close`, and the Task 1 presentation binding.
- Produces: idempotent `DefaultGraphWorkspaceController.shutdown()` (or an equivalently explicit package-visible lifecycle method used by `GraphModeExtension`) that leaves no owned session registered when it returns successfully; `GraphModeExtension.close()` invokes it before clearing the controller reference.

### Step 1: Write shutdown regression tests first

Extend `DefaultGraphWorkspaceControllerShould` with injected `SessionFactory` resources and a fake `GraphWorkspaceView` to prove:

- after opening one session, shutdown discards/closes its store, closes the status publisher, update coordinator, map coordinator, lease manager, scheduler, and view, removes the session from the registry, and marks the handle unusable;
- shutdown is idempotent and does not close the same resource twice;
- when cleanup for one session throws, shutdown attempts the remaining sessions and reports the aggregate failure only after all owned sessions have been removed or settled;
- calling shutdown before any session and calling it twice are harmless.

Extend `GraphPluginIntegrationShould` with an extension lifecycle test using the existing mocked mode/resource setup and a package-private controller factory or injection seam. Open a fake session, call `GraphModeExtension.close()`, and assert the controller shutdown occurs before the graph extension/action references are removed.

Tests must remain headless and must not call `setVisible`.

### Step 2: Run the focused red gate

Run:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" gradle :freeplane_plugin_graph:test \
  --tests '*DefaultGraphWorkspaceControllerShould' \
  --tests '*GraphPluginIntegrationShould' -PTestLoggingFull \
  >"$RUN_ROOT/logs/task-2-red.log" 2>&1
red_status=$?
tail -n 60 "$RUN_ROOT/logs/task-2-red.log"
test "$red_status" -ne 0
```

The expected red result is the missing shutdown seam or the extension not invoking it. Correct test setup failures before production edits, but do not weaken the behavioral assertions.

### Step 3: Implement idempotent controller shutdown

Add a controller-owned lifecycle flag and an explicit shutdown operation. Under the controller monitor, atomically mark shutdown and snapshot all live sessions. Prevent new `open` calls once shutdown begins. For every snapshot session, perform discard-close semantics without invoking a user-facing save dialog: transition the session to closing, close the store/status publisher in the existing safe order, close updates/maps/leases/scheduler off the EDT, close the view on the EDT, mark the handle closed, remove the session from `openSessions`, and unregister its `WorkspaceSessionId`.

Use existing teardown helpers where their ordering and error behavior match the requirement. If a helper is asynchronous on the EDT, make shutdown wait on a bounded completion mechanism or route the whole cleanup through a worker while synchronously joining from the caller; do not return while owned sessions or background resources remain live. Aggregate failures, continue cleanup for later sessions, and make repeated shutdown calls return without repeating teardown.

Keep `closeSession` and `WorkspaceCloseController.saveAndClose/retrySaveAndClose/discardAndClose` behavior unchanged for user-triggered window close. Do not close unrelated sessions or alter duplicate-window focus behavior before shutdown begins.

### Step 4: Wire extension lifecycle and verify

Store the concrete controller needed for lifecycle shutdown while continuing to pass the forwarding controller only to `SwingGraphWorkspaceViewFactory` and `OpenGraphWorkspaceAction`. In `GraphModeExtension.close()`, invoke controller shutdown before removing the graph group extension and before nulling references. Ensure partial installation cleanup remains safe when any registration is absent.

Run the focused lifecycle/integration suites, the full graph plugin suite, and the application compile with output redirected beneath the run root:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" gradle :freeplane_plugin_graph:test \
  --tests '*DefaultGraphWorkspaceControllerShould' \
  --tests '*GraphPluginIntegrationShould' -PTestLoggingFull \
  >"$RUN_ROOT/logs/task-2-focused.log" 2>&1
focused_status=$?
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" gradle :freeplane_plugin_graph:test -PTestLoggingFull \
  >"$RUN_ROOT/logs/task-2-full.log" 2>&1
full_status=$?
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" gradle :freeplane:compileJava \
  >"$RUN_ROOT/logs/task-2-freeplane-compile.log" 2>&1
compile_status=$?
tail -n 50 "$RUN_ROOT/logs/task-2-focused.log"
tail -n 50 "$RUN_ROOT/logs/task-2-full.log"
tail -n 50 "$RUN_ROOT/logs/task-2-freeplane-compile.log"
test "$focused_status" -eq 0
test "$full_status" -eq 0
test "$compile_status" -eq 0
```

Run `git diff --check`, verify only the four listed paths changed for Task 2, and commit with an imperative `2026-08-10-graph-workspace:` subject. Write the required report immediately after the final verification and commit.

### Final successor verification

After Task 2 review and any bounded task fix/re-review rounds, the mandatory Frontier final reviewer must inspect the complete branch range from merge base `b4ecf2fb2baf392c62c1add6c263d78994fb0cd2` through final `HEAD`, reconcile predecessor findings F-2 through F-5, and run bounded final verification. Before declaring completion, run:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" gradle :freeplane_plugin_graph:test -PTestLoggingFull
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" gradle :freeplane:compileJava
git diff --check
```

Also verify the worktree is clean, the required commits have the `2026-08-10-graph-workspace:` prefix, no editor translations changed, and all predecessor findings are either demonstrably resolved or terminally blocked with explicit evidence.
