# Graph Workspace Batch H - UI Shell Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.

**Goal:** Implement Graph Workspace UI Tasks 34, 35, and 36 in order: the modeless Swing shell, operational dialogs/status surfaces, and Freeplane/plugin entry-point wiring.

**Architecture:** The window is a thin Swing composition root over the Task 33 `GraphWorkspaceHandle`, `GraphWorkspaceViewBinding`, and `WorkspaceCloseController` interfaces. Panel models and dialog models remain headless-testable and communicate through immutable view data plus `GraphCommand` or close-controller calls. Plugin actions and menu resources are wired only after the complete window surface exists.

**Tech Stack:** Java 8 source/bytecode, Gradle multi-project build, Swing/AWT, JUnit 4, AssertJ, Mockito, Freeplane resource/action/menu conventions, SVG icons, and ISO-8859-1 translation resources formatted by `gradle format_translation`.

## Global Constraints

- Follow `AGENTS.md`: Java source and target compatibility are 8, encoding is UTF-8, indentation is 4 spaces, tests use JUnit 4/AssertJ/Mockito, and build commands use escalated `gradle`, not Maven or the Gradle wrapper.
- Implementation builds require Java at `~/.sdkman/candidates/java/21.0.8-zulu`; verify that exact JDK and never silently substitute another JDK.
- The bundled module is `freeplane_plugin_graph` with symbolic name `org.freeplane.plugin.graph`; it extends MindMap mode only, adds no mode, changes no `freeplane_api` surface, and neither subclasses nor replaces `MapView`.
- Every cross-package type named in an Interfaces block is public with the exact signature shown. Implementation classes used only within one package remain package-private. The graph bundle exports no package.
- Freeplane `MapModel` and `NodeModel` reads occur only on the EDT. Session window code submits through `GraphWorkspaceHandle`, `GraphWorkspaceController`, and `WorkspaceCloseController`; it never mutates stores or controllers directly.
- The window is modeless and headless-testable. Tests must not call `setVisible`; use component/model inspection and injected callbacks instead.
- UI composition uses a standard menu bar, compact toolbar, left map list, full-bleed canvas, compact right settings drawer, and status bar. Do not create nested cards, unstable fixed-format controls, or a decorative landing surface.
- Initial construction applies the persisted finite viewport through `GraphCanvas.setViewport`. If its visible world rectangle does not overlap current graph bounds, call `fitGraph`; malformed or non-finite persisted values are rejected by the workspace layer and are not normalized in the window.
- Dialogs receive immutable view models and emit `GraphCommand` or close-controller methods. They do not access `GraphWorkspaceStore`, map models, or the Freeplane controller directly.
- Purge and contributor deletion carry the displayed generation. UI confirmation must preserve the displayed generation and exact contributor identity so command handlers can reject stale state.
- `Ctrl+Z` and `Ctrl+Y` in the graph window target workspace history. Explicit source-map undo is a separate session command and is never silently bound to the same keys.
- Add only English source translations to viewer `Resources_en.properties`; do not bulk-edit Weblate-managed editor translations. Run `gradle format_translation` and ASCII validation.
- Every task stages only its exact Files paths, never a directory, and each task commit starts `2026-08-10-graph-workspace:` with an imperative subject.

## Task 1: Source Task 34 - Build the modeless Swing workspace shell and panels

**Implementer tier:** Capable

**Files:**
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/SwingGraphWorkspaceViewFactory.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/MapListPanel.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/WorkspaceToolbar.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/WorkspaceSettingsPanel.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java`

**Interfaces:**
- `GraphWorkspaceViewFactory.create(GraphWorkspaceHandle handle, GraphWorkspaceViewBinding binding, WorkspaceCloseController close)` returns a hidden, unpublished `GraphWorkspaceView` until construction succeeds.
- `GraphWorkspaceWindow` is package-private and extends `JFrame`; its public cross-package surface is through the Task 33 `GraphWorkspaceView` interface.
- The toolbar receives the application-level `GraphWorkspaceController` for Open and the existing `GraphWorkspaceHandle` for all session controls.
- The map list consumes immutable map-row state and emits session `GraphCommand` intents for Add, Remove, Retry, and Locate.
- The canvas is the existing `GraphCanvas` and receives the current immutable `CanvasState`; no Swing class reads mutable Freeplane models.

**Required behavior:**
- Compose a modeless window with standard menu bar, compact toolbar, left map list, full-bleed `GraphCanvas`, compact right settings panel, and status slot reserved for Task 35.
- Include the approved controls and settings: workspace open/save, add/remove maps, selection mode, connection mode, relationship direction, search, settings, zoom in/out, fit graph, reset zoom, pin/unpin controls, and display settings represented by the existing graph command/state types.
- Show map row states for active, loading, missing, read-only, retryable, and selected maps with stable dimensions and projected-node counts. Disable controls that cannot operate in read-only sessions.
- Apply the active Look and Feel through normal Swing defaults; do not introduce a separate theme system.
- Application Open calls `GraphWorkspaceController.open(Path)`; all other session actions route through the existing handle. The window must not mutate a store or controller directly.
- On construction, apply the workspace's persisted viewport through `GraphCanvas.setViewport`. If the finite visible world rectangle does not overlap current graph bounds, call `fitGraph` before the window is shown.
- `show()`, `focus()`, and `close()` must be safe for the modeless lifecycle expected by Task 33. Tests must never invoke `setVisible`.

**Test requirements:**
- Write headless tests first and run them red before implementing production classes.
- Assert menu/toolbar/map-list/settings/status-slot composition, no nested cards, stable component dimensions, approved controls/settings/map-row states, current Look and Feel, and read-only disabling.
- Assert application Open uses the application controller while session controls use the handle.
- Assert persisted viewport application and Fit Graph fallback for a valid finite non-overlapping viewport; assert no fallback for an overlapping viewport.
- Assert the factory does not publish/show a partially constructed window and that test construction does not call `setVisible`.

**Steps:**
- [ ] Create the failing headless model/window tests covering each required behavior and run `gradle :freeplane_plugin_graph:test --tests '*GraphWorkspaceWindowModelShould' -PTestLoggingFull`; confirm failure is caused by absent UI classes or behavior.
- [ ] Implement the minimum panel models, Swing composition, factory, routing callbacks, viewport application, and lifecycle methods needed by those tests.
- [ ] Run the focused test until green, then run `gradle :freeplane_plugin_graph:test -PTestLoggingFull` and inspect the exact six-file diff.
- [ ] Assert the index is empty, stage only the six listed paths, compare staged names to the allowlist, and commit:
  `2026-08-10-graph-workspace: Build the graph workspace shell`.

## Task 2: Source Task 35 - Add status, contributor, purge, and close dialogs

**Implementer tier:** Capable

**Files:**
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/ContributorInspector.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/PurgeConfirmationDialog.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphStatusBar.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/WorkspaceCloseDialog.java`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/WorkspaceDialogsShould.java`

**Interfaces:**
- Dialogs receive immutable view models and emit `GraphCommand` or `WorkspaceCloseController` methods; no dialog accesses a store, map model, or application controller.
- `ContributorInspector` receives the immutable contributor list for one projected edge and emits an exact contributor deletion command or an explicit delete-all command with the displayed generation.
- `PurgeConfirmationDialog` receives immutable missing-node records, displays both endpoint descriptions, and emits one purge command with the displayed generation.
- `GraphStatusBar` receives immutable operational status and exposes every status field without owning mutable state.
- `WorkspaceCloseDialog` emits exactly `saveAndClose`, `retrySaveAndClose`, `discardAndClose`, or `cancelClose` on the supplied `WorkspaceCloseController`.

**Required behavior:**
- Extend the Task 34 window with the status bar and dialog entry points while preserving its shell routing and headless lifecycle.
- Display map state, projected node/edge counts, selected endpoints, layout state, unresolved recoverable count, unresolved missing-node count, save errors, unsaved source-map changes, and warning states for either engineering limit.
- Provide Retry/Restart/Unpin operational actions through session commands, preserving read-only disabling and exact command scope.
- Show safe contributor labels and owners. Never expose unreachable node text or resolve contributor identity through a flat ID lookup in the UI.
- Show generation-bound contributor deletion and purge confirmation. Purge is disabled when there are no recoverable missing-node records and lists both endpoints for every record it can delete. Recoverable unresolved records are not presented as purgeable.
- Provide close Retry/Discard/Cancel behavior. Save failure leaves the window/session open; Retry calls the retry method; Discard calls discard; Cancel leaves the session open.
- When an editor/source-map action activates a map, restore graph-window focus after activation through the existing callback seam.

**Test requirements:**
- Write focused headless dialog/status tests first and run them red with `gradle :freeplane_plugin_graph:test --tests '*WorkspaceDialogsShould' -PTestLoggingFull`.
- Assert every status field and either warning limit, Retry/Restart/Unpin routing, safe labels/owners, generation-bound delete/purge, purge disabled/no recoverable/list-both-endpoints behavior, and close Retry/Discard/Cancel.
- Assert stale generation and pending-change data are carried unchanged into emitted commands rather than silently re-read by the dialog.
- Assert editor activation restores graph focus and that the window remains hidden in tests.
- Run the focused tests, then the full plugin suite, inspect the exact six-file diff, stage only the allowlist, and commit:
  `2026-08-10-graph-workspace: Add graph workspace operational UI`.

## Task 3: Source Task 36 - Wire plugin actions, menus, icons, i18n, and undo keys

**Implementer tier:** Capable

**Files:**
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/GraphModeExtension.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/OpenGraphWorkspaceAction.java`
- Create: `freeplane_plugin_graph/src/main/resources/images/GraphGroup.svg`
- Create: `freeplane_plugin_graph/src/main/resources/images/GraphWorkspace.svg`
- Modify: `freeplane/src/external/resources/xml/mindmapmodemenu.xml`
- Modify: `freeplane/src/viewer/resources/freeplane.properties`
- Modify: `freeplane/src/viewer/resources/translations/Resources_en.properties`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/UndoRoutingShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphPluginIntegrationShould.java`

**Interfaces:**
- `OpenGraphWorkspaceAction` uses the application-level `GraphWorkspaceController` and opens an existing or newly selected `.fpg` workspace through the normal chooser/action path.
- `GraphGroupAction` remains a direct map actor and is not routed through a workspace session.
- Window `Ctrl+Z`/`Ctrl+Y` emit workspace session commands; explicit source-map undo emits a session command that identifies the map and never conflates the two histories.
- Every session toolbar/menu control routes through the existing `GraphWorkspaceHandle`, and plugin registration supplies the Task 33 controller, persistence, painter, view factory, and actions.

**Required behavior:**
- Register the controller/view/persistence/painter/actions through `GraphModeExtension` using existing plugin extension patterns without changing `freeplane_api`.
- Add the Graph Workspace action to the approved View/menu location and add Graph Group beside the existing cloud/clone controls with meaningful action names and tooltips.
- Add the two SVG icons with the approved fixed Graph Group coral treatment and a distinct Graph Workspace icon. Keep resources valid and loadable by the existing icon factory.
- Add only the required English viewer property and translation keys, including menu labels, tooltips, status/dialog text, undo names, warnings, and no-op/error messages. Do not edit Weblate-managed editor translation files.
- Keep action scope explicit: application Open, main-editor Graph Group, and existing-session controls use their respective interfaces.
- Wire undo key/menu enabled states so workspace undo/redo and source-map undo are visibly distinct and disabled when their corresponding history is empty.

**Test requirements:**
- Write integration and undo-routing tests first and run them red with `gradle :freeplane_plugin_graph:test --tests '*UndoRoutingShould' --tests '*GraphPluginIntegrationShould' -PTestLoggingFull`.
- Assert plugin gating/resources, existing/new workspace chooser behavior, Graph Group placement beside cloud/clone, tooltips and action scopes, menu enabled names, undo key routing, and every session control's handle route.
- Run focused tests, then `gradle :freeplane_plugin_graph:test -PTestLoggingFull`, `gradle :freeplane:compileJava`, and `gradle format_translation`.
- Validate translations with:
  `file freeplane/src/editor/resources/translations/Resources_*.properties freeplane/src/viewer/resources/translations/Resources_en.properties | grep -v "ASCII text"`
  and
  `test -z "$(git diff --name-only -- freeplane/src/editor/resources/translations)"`.
- Validate that the only changed viewer translation path is `freeplane/src/viewer/resources/translations/Resources_en.properties`.
- Assert the index is empty, stage only the nine distinct paths listed above (`mindmapmodemenu.xml` is one path), compare staged names to the allowlist, and commit:
  `2026-08-10-graph-workspace: Wire graph workspace entry points`.
