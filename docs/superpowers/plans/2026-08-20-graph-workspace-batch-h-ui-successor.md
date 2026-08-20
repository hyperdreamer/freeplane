# Graph Workspace Batch H UI Successor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.
>
> This successor supersedes no prior evidence. The terminal continuation run at
> `.superpowers/sdd/2026-08-20-graph-workspace-batch-h-ui-shell-continuation`
> and the earlier amended run remain immutable audit records. Start from
> `4865b15f9fa01ef119533ac5219e93e80ab0aba9`; retain the valid Task 34 source
> correction at `211574f2447debb645e610cb05a5843915056c3d`.

**Goal:** Make the Task 34 viewport regression falsifiable, then complete Graph Workspace status/dialog surfaces and MindMap plugin entry points without exposing stores or map models to Swing.

**Architecture:** Task 1 changes only the delayed-layout test. Task 2 adds an immutable control-layer `WorkspaceSessionStatus` snapshot and listener seam. Task 3 makes the window consume canvas state and that snapshot through headless-testable dialog/status models. Task 4 composes the Freeplane action, menu, icons, translations, and distinct workspace/source-map undo state.

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
- Every task stages only its exact Files paths and uses an imperative `2026-08-10-graph-workspace:` commit subject.
- Do not modify, reopen, or cite blocked predecessor ledgers as approval evidence. This successor gets a distinct run root, digest, prompts, reports, and review ranges.

### File Structure

- `control/WorkspaceSessionStatus.java` carries immutable save/history/source-map status from the control layer to the view.
- `control/WorkspaceSessionStatusPublisher.java` is package-private session ownership for store events and command results.
- `window/GraphStatusBar.java` and the dialog classes are thin views over immutable values and injected callbacks.
- `window/OpenGraphWorkspaceAction.java` is application-scoped; the existing `GraphGroupAction` remains a direct map actor.

## Task 1: Make the delayed-layout viewport regression falsifiable

**Implementer tier:** Advanced

**Files:**
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java:201-226`

**Interfaces:**
- Consumes: package-private `GraphWorkspaceWindowModel.acceptCanvasState(CanvasState)`, `completeInitialLayout()`, and the existing `Fixture.modelWithoutLayout()`, `emptyState()`, and `nodeState(MapReferenceId, LayoutPoint)` helpers.
- Produces: one headless regression proving that an out-of-range non-empty state received before layout completion fits exactly once at completion.

- [ ] **Step 1: Replace the delayed-layout test with the real ordering**

Use persisted viewport `(0.0, 0.0, 1.0)`, construct through `modelWithoutLayout()` with `emptyState()`, deliver `nodeState(ACTIVE_ID, LayoutPoint.of(10_000.0, 10_000.0))`, then call `completeInitialLayout()`. Assert the viewport remains centered at `0.0` before completion and is centered at `10_000.0` with zoom greater than `1.0` after completion. Deliver a later state centered at `20_000.0` and assert both viewport center coordinates remain `10_000.0`.

```java
@Test
public void fitsAnOutOfRangeStateDeliveredBeforeInitialLayoutExactlyOnce() {
    Viewport persisted = Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml());
    Fixture fixture = fixture(persisted, emptyState(),
        Collections.<GraphWorkspaceViewBinding.MapRegistration>emptyList(), false);
    GraphWorkspaceWindowModel model = fixture.modelWithoutLayout();

    model.acceptCanvasState(nodeState(ACTIVE_ID, LayoutPoint.of(10_000.0, 10_000.0)));
    assertThat(model.canvas().viewport().centerX()).isEqualTo(0.0);
    model.completeInitialLayout();
    assertThat(model.canvas().viewport().centerX()).isEqualTo(10_000.0);
    assertThat(model.canvas().viewport().centerY()).isEqualTo(10_000.0);
    assertThat(model.canvas().viewport().zoom()).isGreaterThan(1.0);

    model.acceptCanvasState(nodeState(ACTIVE_ID, LayoutPoint.of(20_000.0, 20_000.0)));
    assertThat(model.canvas().viewport().centerX()).isEqualTo(10_000.0);
    assertThat(model.canvas().viewport().centerY()).isEqualTo(10_000.0);
    model.close();
}
```

- [ ] **Step 2: Establish the intact baseline**

Run:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" gradle :freeplane_plugin_graph:test \
  --tests '*GraphWorkspaceWindowModelShould' -PTestLoggingFull
```

Expected: PASS. This is a coverage correction over already-correct production behavior.

- [ ] **Step 3: Prove the test is falsifiable with a disposable mutation**

Copy `GraphWorkspaceWindow.java` to `/tmp/GraphWorkspaceWindow.java.before-delayed-layout-probe`. Temporarily remove only `applyInitialViewport(currentState);` from `GraphWorkspaceWindowModel.completeInitialLayout()`. Re-run Step 2. Expected: the new test FAILS after completion because the center remains `0.0`, not `10_000.0`. Restore the copied file immediately and verify:

```bash
git diff --exit-code -- freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java
```

The disposable production mutation must never be staged or committed.

- [ ] **Step 4: Commit only the verified test correction**

Re-run Step 2, run `git diff --check`, verify the only changed path is the test file, and commit:

```bash
git add freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java
git commit -m "2026-08-10-graph-workspace: Cover delayed graph viewport fitting"
```

## Task 2: Publish immutable workspace session status to the view

**Implementer tier:** Capable

**Files:**
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/WorkspaceSessionStatus.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/WorkspaceSessionStatusListener.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/WorkspaceSessionStatusPublisher.java`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/GraphWorkspaceViewBinding.java:1-end`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/GraphWorkspaceStore.java:187-212`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/DefaultGraphWorkspaceHandle.java:1-end`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/DefaultGraphWorkspaceController.java:286-376`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/WorkspaceSessionStatusShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/DefaultGraphWorkspaceControllerShould.java`

**Interfaces:**
- Consumes: `GraphWorkspaceStore.isDirty()`, store history availability, `WorkspaceStoreEvent`, `GraphCommandResult.dirtySourceMaps()`, and `GraphCommandRouter.currentMapUndoTarget()`.
- Produces: public `WorkspaceSessionStatus.empty()`, `WorkspaceSessionStatus.of(boolean workspaceDirty, boolean workspaceUndoAvailable, boolean workspaceRedoAvailable, boolean saveFailed, Set<MapReferenceId> dirtySourceMaps, Optional<MapUndoTarget> sourceMapUndoTarget)`, and getters with those exact names. The listener is `void onWorkspaceSessionStatus(WorkspaceSessionStatus status)`.
- Extends `GraphWorkspaceViewBinding` with `WorkspaceSessionStatus currentSessionStatus()` and `ListenerRegistration addSessionStatusListener(WorkspaceSessionStatusListener listener)`. Default implementations return an empty snapshot and a no-op registration; the production binding overrides both.

- [ ] **Step 1: Write focused status tests first**

Create `WorkspaceSessionStatusShould` to assert every snapshot field, deterministic defensive immutable copying of dirty map IDs, and preservation of `Optional<MapUndoTarget>`. With mock store/router dependencies, capture the publisher store listener and drive `DOCUMENT_CHANGED`, `SAVE_FAILED`, and `SAVED`. Assert dirty/history fields refresh, failure becomes true only after `SAVE_FAILED`, a later `SAVED` clears it, and previously reported dirty source-map IDs remain. Drive a command result containing two dirty maps plus a router undo target and assert listeners receive the exact union/target.

Extend `DefaultGraphWorkspaceControllerShould` to capture the production binding, assert non-null status snapshots and delegated listener registrations, and assert close/rollback closes the publisher registration without exposing store or router through the binding.

- [ ] **Step 2: Run focused status tests red**

Run `JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" gradle :freeplane_plugin_graph:test --tests '*WorkspaceSessionStatusShould' --tests '*DefaultGraphWorkspaceControllerShould' -PTestLoggingFull`.

Expected before implementation: compilation or assertion failure because the snapshot/publisher API is absent.

- [ ] **Step 3: Implement the narrow publisher**

Implement `WorkspaceSessionStatus` with null checks and deterministic immutable map-ID copying. Add synchronized `GraphWorkspaceStore.canUndo()` and `canRedo()`; both return false after close or for a read-only document because their commands would reject.

Implement package-private `WorkspaceSessionStatusPublisher` as the only store/router bridge. It registers one store listener, owns its registration, initializes from current dirty/history values with no failure/no dirty source maps, and publishes immutable snapshots to a copied listener list while isolating listener failures. On document/identity/saved/save-failed events it re-reads store dirty/history values; it marks failure on `SAVE_FAILED` and clears failure only on `SAVED`. After a completed handle command it unions exact `dirtySourceMaps()` and refreshes source-map undo target only through `GraphCommandRouter.currentMapUndoTarget()`. `close()` closes the store registration and clears listeners exactly once.

Give `DefaultGraphWorkspaceHandle` a production publisher dependency. After a router command returns a result, record it before returning. Preserve its existing public constructor with a no-op publisher path for focused tests. In `DefaultGraphWorkspaceController.finishOpen`, create the publisher, delegate the binding methods to it, retain it on the session, and close it before store cleanup on rollback and normal close. Do not add a status method to `GraphWorkspaceHandle`, expose a store/router, or inspect a map model from this publisher.

- [ ] **Step 4: Commit the verified exact status seam**

Run the Step 2 command, then run `JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" gradle :freeplane_plugin_graph:test -PTestLoggingFull` and `git diff --check`. Stage only the nine Files paths and commit `2026-08-10-graph-workspace: Publish workspace session status`.

## Task 3: Add headless operational dialogs and status surfaces

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
- Consumes: `CanvasState`, `OperationalStatus`, `GraphProjection`, `ProjectedEdge`, `EdgeContributor`, `RelationshipResolution`, `RelationshipStatus`, and Task 2 session-status binding methods.
- Produces: package-private headless models. `ContributorInspector` captures displayed generation, exact edge key, immutable contributor rows/key/optional connector descriptor. `PurgeConfirmationDialog` captures displayed generation plus immutable missing rows. `WorkspaceCloseDialog` calls only `WorkspaceCloseController`. `GraphStatusBar` consumes immutable status values and emits only `GraphCommand` via an injected callback.

- [ ] **Step 1: Write headless dialog/status tests first**

Create `WorkspaceDialogsShould` covering all of these cases without a visible `JFrame` or `JDialog`:

- Status renders map availability, projected node/edge counts, selected safe endpoint text, layout state, recoverable count, missing-node count, workspace dirty/save-failed state, dirty source-map names/count, workspace history availability, and either the 2,000-node or 5,000-edge warning independently.
- Retry Save emits `GraphCommands.retrySave()`, Restart Layout emits `GraphCommands.restartLayout()`, and Unpin All emits `GraphCommands.unpinAll()`. Read-only disables workspace mutation controls while leaving layout restart available.
- Contributor rows contain only safe source/middle/target labels, known owner display name, exact key, and optional displayed descriptor. Delete one emits `deleteContributor(displayedGeneration, key, displayedDescriptor)`; delete all emits `deleteAllContributors(displayedGeneration, edgeKey, displayedKeys, displayedNativeDescriptorMap)`.
- Purge contains only `UNRESOLVED_MISSING_NODE` records, preserves both precomputed endpoint descriptions and relationship IDs, disables empty purge, and emits `purge(displayedGeneration, displayedRelationshipIds)` without substituting a later generation.
- Close Retry calls `retrySaveAndClose`, Discard calls `discardAndClose`, Cancel calls `cancelClose`, and an unsuccessful retry/discard does not invoke completion.
- An editor-activating command result calls the injected graph-focus callback exactly once after routing; a non-activating result does not.

Extend `GraphWorkspaceWindowModelShould` to assert status listener registration closes with the canvas registration and the tests still never need `setVisible`.

- [ ] **Step 2: Run the focused tests red**

Run `JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" gradle :freeplane_plugin_graph:test --tests '*WorkspaceDialogsShould' --tests '*GraphWorkspaceWindowModelShould' -PTestLoggingFull`.

Expected before implementation: compilation or assertion failure because the operational types/routing are absent.

- [ ] **Step 3: Implement immutable surfaces and exact routing**

Build each dialog around captured immutable data and an injected command/close callback. Thin modal wrappers may run only when graphics are available; tests use models/actions directly.

`ContributorInspector` derives rows from the exact displayed `ProjectedEdge`, uses `SafeNodeLabel.displayText()` and registered map display names only, and preserves descriptors only for native contributors. `PurgeConfirmationDialog` receives rows already filtered to `UNRESOLVED_MISSING_NODE`; derive safe endpoint descriptions from immutable resolution/projection data and registered map names, never include recoverable rows. `GraphStatusBar` has stable component names and compact fixed height, maps dirty source IDs to registered display names, labels node/edge warnings separately, and sends its three controls only through the injected command callback.

In `GraphWorkspaceWindowModel`, subscribe to canvas and session-status bindings and close both registrations. Pass a private delegating `GraphWorkspaceHandle` to existing toolbar/map/settings components so every command result goes through one method; invoke the supplied graph-focus callback only when `editorViewActivated()` is true. Handle `InspectEdge`, `DeleteContributor`, and `DeleteAllContributors` by locating the exact current edge/contributor in `currentState` and using current displayed generation; absent values emit no command. Add a purge entry point from only current missing-node resolutions. Initial close calls `saveAndClose`; only a false result exposes `WorkspaceCloseDialog`, whose retry/discard completion closes the view only after true.

- [ ] **Step 4: Commit the verified operational UI**

Run the Step 2 command, then run `JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" gradle :freeplane_plugin_graph:test -PTestLoggingFull` and `git diff --check`. Stage only the seven Files paths and commit `2026-08-10-graph-workspace: Add graph workspace operational UI`.

## Task 4: Wire MindMap actions, menus, icons, translations, and distinct undo

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
- Consumes: `GraphWorkspaceController.open(Path)`, `SwingGraphWorkspaceViewFactory`, `DefaultGraphWorkspaceController`, `GraphGroupController`, `ModeController.addAction/removeAction`, and Task 2/3 status and command routing.
- Produces: `OpenGraphWorkspaceAction extends AFreeplaneAction` with key `OpenGraphWorkspaceAction`; it accepts `GraphWorkspaceController` and injectable `Supplier<Path>`, opens only non-null selected paths, and creates no session command.
- Produces: application scope for Open Graph Workspace, direct map-actor scope for `GraphGroupAction`, and existing-session scope for window controls. No action crosses scope silently.

- [ ] **Step 1: Write plugin and undo-routing tests first**

Create `UndoRoutingShould` using explicit `WorkspaceSessionStatus` values. Verify `ctrl Z` and `ctrl Y` route only `undoWorkspace()` and `redoWorkspace()`; workspace toolbar/Edit actions use localized labels and disable exactly when snapshot history is empty; separately named source-map Undo includes target map name, enables only for a present `canUndo()` target, routes `undoSourceMap()`, and has no graph-window shortcut; every toolbar/menu/status session control reaches the routed handle while retaining read-only behavior.

Create `GraphPluginIntegrationShould` with injectable chooser suppliers and mocked mode components. Verify `OpenGraphWorkspaceAction` ignores cancelled chooser and opens both existing/new `.fpg` paths through the application controller; `GraphModeExtension` registers/removes it with the existing graph group controller/painter; XML places `OpenGraphWorkspaceAction` in View and `GraphGroupAction` directly beside `CloudAction`; resource keys point to both SVGs; and action names/tooltips exist in `Resources_en.properties`. Scope Mockito static `TextUtils` mocks around headless tests so no test requires a global Freeplane application.

- [ ] **Step 2: Run the focused tests red**

Run `JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" gradle :freeplane_plugin_graph:test --tests '*UndoRoutingShould' --tests '*GraphPluginIntegrationShould' -PTestLoggingFull`.

Expected before implementation: compilation or assertion failure because the action, resources, menu entries, and dynamic undo state are absent.

- [ ] **Step 3: Compose the application action without changing map scope**

Register the resource loader before actions. Resolve the controller/view-factory construction cycle in `GraphModeExtension` with a private forwarding `GraphWorkspaceController` used only by `SwingGraphWorkspaceViewFactory`; bind it to the completed `DefaultGraphWorkspaceController` before registering `OpenGraphWorkspaceAction`. Retain/remove that action during extension lifecycle. Leave `GraphGroupController` and its direct map action unchanged.

Implement `OpenGraphWorkspaceAction` with a package-visible supplier constructor for tests and production chooser `GraphWorkspaceWindow::chooseWorkspacePath`. The application controller, not this action, owns new/existing workspace creation and duplicate-window focus.

- [ ] **Step 4: Localize the shell and keep histories distinct**

Replace visible Graph Workspace string literals in every listed window class with English viewer `graph_workspace.*` keys through `TextUtils`; use `TextUtils.format` for map names/counts. Add keys for menus, controls, tooltips, status fields, warnings, dialog copy, contributor/purge descriptions, save/layout/no-op errors, and separate workspace/source-map undo labels. Pass only safe display values as translation arguments.

Add a source-map undo Action whose localized name includes `MapUndoTarget.mapName()`. Update workspace undo/redo and source-map action enabled state when `WorkspaceSessionStatus` changes. Keep `Ctrl+Z`/`Ctrl+Y` bound only to workspace actions.

Add valid standalone `GraphGroup.svg` using fixed coral `#DF625D` without accent substitution, and a distinct valid `GraphWorkspace.svg`. Add icon mappings in `freeplane.properties`. Add `GraphGroupAction` immediately after `CloudAction` in the editor toolbar row and `OpenGraphWorkspaceAction` under View. Do not add a menu item that invokes session commands outside an existing workspace window.

- [ ] **Step 5: Commit the verified exact plugin/UI allowlist**

Run the Step 2 command, then run all of these commands:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" gradle :freeplane_plugin_graph:test -PTestLoggingFull
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" gradle :freeplane:compileJava
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" gradle format_translation
file freeplane/src/editor/resources/translations/Resources_*.properties freeplane/src/viewer/resources/translations/Resources_en.properties | grep -v "ASCII text"
test -z "$(git diff --name-only -- freeplane/src/editor/resources/translations)"
test "$(git diff --name-only -- freeplane/src/viewer/resources/translations)" = "freeplane/src/viewer/resources/translations/Resources_en.properties"
git diff --check
```

Stage only the eighteen Files paths and commit `2026-08-10-graph-workspace: Wire graph workspace entry points`.
