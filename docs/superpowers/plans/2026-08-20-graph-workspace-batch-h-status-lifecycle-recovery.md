# Graph Workspace Batch H Status-Lifecycle Recovery Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.
>
> This plan follows two immutable terminal runs caused by prompt dispatch
> mismatches. Their state, reports, and prompts remain audit evidence only. The
> existing status-seam commit is carried forward and corrected through a fresh
> test-first lifecycle task.

**Goal:** Preserve live workspace status after a rejected save-close, then complete the Graph Workspace operational shell and MindMap entry points.

**Architecture:** Task 1 repairs the status publisher lifecycle so a failed save-close leaves the active binding connected and a successful close still tears it down in order. Task 2 builds headless operational dialogs and status controls on the immutable status seam. Task 3 wires application action/menu/icon/translation integration and keeps workspace and source-map undo histories distinct.

**Tech Stack:** Java 8 source and bytecode, Gradle multi-project build, Swing/AWT, JUnit 4, AssertJ, Mockito, Freeplane action/resource/menu conventions, SVG, and ISO-8859-1 properties resources.

## Global Constraints

- Follow `AGENTS.md`: Java 8 source compatibility, UTF-8 Java sources, four-space indentation, JUnit 4/AssertJ/Mockito tests, and `gradle`, never Maven or the Gradle wrapper.
- Every build command uses `JAVA_HOME=$HOME/.sdkman/candidates/java/21.0.8-zulu`; do not substitute another JDK.
- `freeplane_plugin_graph` extends MindMap mode only, adds no mode, changes no `freeplane_api` surface, and neither subclasses nor replaces `MapView`.
- Freeplane `MapModel` and `NodeModel` reads occur only on the EDT. Window code communicates only through `GraphWorkspaceHandle`, `GraphWorkspaceViewBinding`, and `WorkspaceCloseController`; it never accesses a store, router, map model, or Freeplane controller.
- The window remains modeless and headless-testable. Tests must not call `setVisible`; inspect model/component state and injected callbacks.
- Keep the standard menu bar, compact toolbar, left map list, full-bleed canvas, compact right drawer, and compact status bar. Do not add nested cards, decorative surfaces, or unstable control dimensions.
- Dialogs receive immutable displayed data and emit `GraphCommand` or `WorkspaceCloseController` methods. They never use a flat ID lookup or raw/unreachable node text.
- Purge and contributor deletion use the displayed generation, exact displayed contributor keys, and exact displayed native connector descriptors. Do not rebuild a command from a newer projection after a dialog opens.
- `Ctrl+Z` and `Ctrl+Y` in the graph window always target workspace history. Source-map undo is named separately, has no graph-window shortcut, and is disabled independently.
- Add only English viewer translations to `freeplane/src/viewer/resources/translations/Resources_en.properties`; never bulk-edit Weblate-managed editor translations. Run `gradle format_translation` and preserve ASCII/escaped ISO-8859-1 content.
- Every implementation task stages only its exact Files paths and uses an imperative `2026-08-10-graph-workspace:` commit subject.
- The terminal runs at `.superpowers/sdd/2026-08-20-graph-workspace-batch-h-ui-shell-continuation`, `.superpowers/sdd/2026-08-20-graph-workspace-batch-h-ui-successor`, and `.superpowers/sdd/2026-08-20-graph-workspace-batch-h-post-task-2-mismatch` are immutable audit history only. Do not reopen or cite their child reports as approval evidence.
- The carried status-seam commit is `912999ae0ad008b6f7b9ecf8c8019f4da568854c`; do not amend, recreate, or revert it. Task 1 must preserve its public seam while repairing failed-close recovery.
- Every dispatch must persist the renderer-produced prompt before spawn, record the returned session immediately, and compare the completed child transcript's initial user message byte-for-byte with the stored prompt before admitting its result.

## Task 1: Keep status live across failed save-close recovery

**Implementer tier:** Capable

**Files:**
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/DefaultGraphWorkspaceController.java:580-625`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/DefaultGraphWorkspaceControllerShould.java`

**Interfaces:**
- Consumes: the carried `WorkspaceSessionStatusPublisher`, `GraphWorkspaceViewBinding.currentSessionStatus()`, `addSessionStatusListener(WorkspaceSessionStatusListener)`, and `WorkspaceCloseController.saveAndClose/retrySaveAndClose` lifecycle.
- Produces: a live binding that remains connected after a failed save-close, while a successful save/discard close still closes the publisher before remaining resource cleanup.

- [ ] **Step 1: Add the failing lifecycle regression test first**

Extend `DefaultGraphWorkspaceControllerShould` with a headless test that captures the production binding, status listener, and store listener registration. Open an existing workspace, make `resources.store.close()` throw once, call `saveAndClose()`, and assert it returns false while the session owner, view, and status listener remain active. Deliver a captured `WorkspaceStoreEvent.Type.SAVE_FAILED` and assert the listener receives a snapshot with `saveFailed()` true. Restore successful store close, call `retrySaveAndClose()`, assert it returns true, and verify the publisher's store registration closes exactly once. A later store event must not reach the listener after successful close.

Run:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" gradle :freeplane_plugin_graph:test \
  --tests '*DefaultGraphWorkspaceControllerShould' -PTestLoggingFull
```

Expected before the implementation: the new test fails because `closeSession()` closes the publisher before the failing store close, so the publisher has removed its registration and rejects the status listener/event path.

- [ ] **Step 2: Move publisher teardown after successful store close**

In `DefaultGraphWorkspaceController.closeSession`, leave the publisher untouched while `store.close()` or `store.discardAndClose()` executes. On a thrown store failure, call `reopenAfterSaveFailureLocked()` and return false with the publisher still registered. Only after the store operation succeeds, call the existing failure-recording `closeSessionStatusPublisher(session)` helper; then release the session monitor and perform the remaining resource cleanup. Preserve the existing ordering for successful close and keep publisher-close failures aggregated without leaving the session in `CLOSING`.

- [ ] **Step 3: Run focused and full verification**

Run the focused regression, the full graph-plugin suite, and `git diff --check` with the mandated JDK. Confirm only the two Files paths changed. Stage exactly those paths and commit:

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/DefaultGraphWorkspaceController.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/DefaultGraphWorkspaceControllerShould.java
git commit -m "2026-08-10-graph-workspace: Preserve status after failed close"
```

## Task 2: Add headless operational dialogs and status surfaces

**Implementer tier:** Capable

**Files:**
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/ContributorInspector.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/PurgeConfirmationDialog.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphStatusBar.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/WorkspaceCloseDialog.java`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java:1-end`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/WorkspaceDialogsShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java`

**Interfaces:**
- Consumes: `CanvasState`, `OperationalStatus`, `GraphProjection`, `ProjectedEdge`, `EdgeContributor`, `RelationshipResolution`, `RelationshipStatus`, and `GraphWorkspaceViewBinding.currentSessionStatus()`/`addSessionStatusListener(WorkspaceSessionStatusListener)`.
- Produces: package-private headless models. `ContributorInspector` captures displayed generation, exact edge key, immutable contributor rows/key/optional connector descriptor. `PurgeConfirmationDialog` captures displayed generation plus immutable missing rows. `WorkspaceCloseDialog` calls only `WorkspaceCloseController`. `GraphStatusBar` consumes immutable status values and emits only `GraphCommand` via an injected callback.

- [ ] **Step 1: Write headless dialog and status tests first**

Create `WorkspaceDialogsShould` covering these cases without a visible `JFrame` or `JDialog`: status renders map availability, projected node/edge counts, selected safe endpoint text, layout state, recoverable count, missing-node count, workspace dirty/save-failed state, dirty source-map names/count, workspace history availability, and separate 2,000-node/5,000-edge warnings; Retry Save, Restart Layout, and Unpin All emit only their named `GraphCommands`; read-only disables workspace mutation controls but leaves layout restart available; contributor rows preserve safe labels, owner name, exact key, and optional native descriptor; contributor delete/purge retain displayed generation and exact displayed identity; purge excludes recoverable rows; close Retry/Discard/Cancel call only the close controller and do not complete after an unsuccessful attempt; and editor-activating command results focus the graph exactly once.

Extend `GraphWorkspaceWindowModelShould` to assert both canvas and session-status listener registrations close with the model and no test calls `setVisible`.

- [ ] **Step 2: Run focused tests red**

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" gradle :freeplane_plugin_graph:test \
  --tests '*WorkspaceDialogsShould' \
  --tests '*GraphWorkspaceWindowModelShould' -PTestLoggingFull
```

Expected before implementation: compilation or assertion failure because the operational models and routing are absent.

- [ ] **Step 3: Implement immutable surfaces and exact routing**

Build each dialog around captured immutable data and injected command/close callbacks. Thin modal wrappers may run only when graphics are available; tests use models/actions directly. `ContributorInspector` derives rows from the exact displayed `ProjectedEdge`, uses safe labels and registered map names, and retains descriptors only for native contributors. `PurgeConfirmationDialog` receives only `UNRESOLVED_MISSING_NODE` rows and preserves precomputed endpoint descriptions and relationship IDs. `GraphStatusBar` uses stable component names and fixed compact height, maps dirty source IDs to registered display names, labels node/edge warnings separately, and sends controls through the injected command callback.

In `GraphWorkspaceWindowModel`, subscribe to canvas and session-status bindings and close both registrations. Pass a private delegating `GraphWorkspaceHandle` to existing toolbar/map/settings components so every command result goes through one method; focus the graph only for `editorViewActivated()`. Handle inspect/delete/purge actions using exact current displayed generation and identities; absent values emit no command. Initial close calls `saveAndClose`; only false exposes `WorkspaceCloseDialog`, and completion closes the view only after true.

- [ ] **Step 4: Commit the verified operational UI**

Run the focused command, then `JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" gradle :freeplane_plugin_graph:test -PTestLoggingFull` and `git diff --check`. Stage exactly the seven Files paths and commit `2026-08-10-graph-workspace: Add graph workspace operational UI`.

## Task 3: Wire MindMap actions, menus, icons, translations, and distinct undo

**Implementer tier:** Capable

**Files:**
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/OpenGraphWorkspaceAction.java`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/GraphModeExtension.java:1-end`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java:1-end`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/WorkspaceToolbar.java:1-end`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/MapListPanel.java:1-end`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/WorkspaceSettingsPanel.java:1-end`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/ContributorInspector.java:1-end`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/PurgeConfirmationDialog.java:1-end`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphStatusBar.java:1-end`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/WorkspaceCloseDialog.java:1-end`
- Create: `freeplane_plugin_graph/src/main/resources/images/GraphGroup.svg`
- Create: `freeplane_plugin_graph/src/main/resources/images/GraphWorkspace.svg`
- Modify: `freeplane/src/external/resources/xml/mindmapmodemenu.xml:1-360`
- Modify: `freeplane/src/viewer/resources/freeplane.properties:1-240`
- Modify: `freeplane/src/viewer/resources/translations/Resources_en.properties:1-end`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/UndoRoutingShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphPluginIntegrationShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java`

**Interfaces:**
- Consumes: `GraphWorkspaceController.open(Path)`, `SwingGraphWorkspaceViewFactory`, `DefaultGraphWorkspaceController`, `GraphGroupController`, `ModeController.addAction/removeAction`, and the Task 1/2 status and command-routing seams.
- Produces: `OpenGraphWorkspaceAction extends AFreeplaneAction` with key `OpenGraphWorkspaceAction`; it accepts `GraphWorkspaceController` and injectable `Supplier<Path>`, opens only non-null selected paths, and creates no session command. Application scope remains separate from direct map-actor and existing-session scope.

- [ ] **Step 1: Write plugin and undo-routing tests first**

Create `UndoRoutingShould` using explicit `WorkspaceSessionStatus` values. Verify Ctrl+Z/Ctrl+Y route only `undoWorkspace()`/`redoWorkspace()`, workspace history actions disable exactly when snapshot history is empty, separately named source-map Undo includes the target map name, enables only with a present target, routes `undoSourceMap()`, and has no graph-window shortcut. Verify every toolbar/menu/status session control reaches the routed handle while retaining read-only behavior.

Create `GraphPluginIntegrationShould` with injectable chooser suppliers and mocked mode components. Verify cancelled chooser does nothing, existing/new `.fpg` paths open through the application controller, `GraphModeExtension` registers/removes the action, XML places `OpenGraphWorkspaceAction` under View and `GraphGroupAction` beside `CloudAction`, resource keys point to both SVGs, and English action labels/tooltips exist without requiring a global Freeplane application.

- [ ] **Step 2: Run focused tests red**

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" gradle :freeplane_plugin_graph:test \
  --tests '*UndoRoutingShould' \
  --tests '*GraphPluginIntegrationShould' -PTestLoggingFull
```

Expected before implementation: compilation or assertion failure because the action, resources, menu entries, and dynamic undo state are absent.

- [ ] **Step 3: Compose the application action without changing map scope**

Register the resource loader before actions. Resolve the controller/view-factory cycle in `GraphModeExtension` with a private forwarding `GraphWorkspaceController` used only by `SwingGraphWorkspaceViewFactory`; bind it to the completed `DefaultGraphWorkspaceController` before registering `OpenGraphWorkspaceAction`. Retain/remove the action during extension lifecycle and leave `GraphGroupController` as the direct map actor.

Implement `OpenGraphWorkspaceAction` with a package-visible supplier constructor for tests and production chooser `GraphWorkspaceWindow::chooseWorkspacePath`. The application controller owns workspace creation and duplicate-window focus.

- [ ] **Step 4: Localize the shell and keep histories distinct**

Replace visible Graph Workspace literals in every listed window class with English viewer `graph_workspace.*` keys through `TextUtils`; use `TextUtils.format` for names/counts. Add keys for menus, controls, tooltips, status fields, warnings, dialogs, contributor/purge descriptions, save/layout/no-op errors, and separate workspace/source-map undo labels. Add a source-map undo Action whose localized name includes `MapUndoTarget.mapName()`. Update both histories from `WorkspaceSessionStatus`; keep Ctrl+Z/Ctrl+Y bound only to workspace actions. Add valid standalone `GraphGroup.svg` with fixed coral `#DF625D`, a distinct `GraphWorkspace.svg`, icon mappings, XML placement, and no session commands outside an existing workspace window.

- [ ] **Step 5: Commit and verify the exact plugin/UI allowlist**

Run focused and full graph-plugin tests, `gradle :freeplane:compileJava`, `gradle format_translation`, ASCII validation for translation resources, an editor-translation diff guard, and `git diff --check`, always with the mandated JDK. Stage exactly the eighteen Files paths and commit `2026-08-10-graph-workspace: Wire graph workspace entry points`.
