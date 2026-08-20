# Graph Workspace Batch H Bounded Audit Continuation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.
>
> This successor preserves the terminal pointer-continuation run as immutable
> evidence and bounds child command output so a required report is written before
> any optional narrative. Commit `50d0c3bd1502158b19f3ea459cc6cfe5044525d0` remains
> an unreviewed carry-forward candidate.

**Goal:** Independently certify failed save-close status recovery, then complete the Graph Workspace operational shell and MindMap entry points.

**Architecture:** Task 1 audits the exact two-file lifecycle correction without changing source and lets a fresh review gate decide whether it is carried forward. Task 2 builds headless operational dialogs and status controls on the audited immutable status seam. Task 3 wires application actions, menus, icons, translations, and distinct workspace/source-map undo behavior. Every child receives a renderer-produced full role envelope through a short persisted pointer prompt; audit commands write bounded logs under the run root and the required report is written before optional narrative.

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
- Every source implementation task stages only its exact Files paths and uses an imperative `2026-08-10-graph-workspace:` commit subject.
- The terminal runs at `.superpowers/sdd/2026-08-20-graph-workspace-batch-h-ui-shell-continuation`, `.superpowers/sdd/2026-08-20-graph-workspace-batch-h-ui-successor`, `.superpowers/sdd/2026-08-20-graph-workspace-batch-h-post-task-2-mismatch`, `.superpowers/sdd/2026-08-20-graph-workspace-batch-h-status-lifecycle-recovery`, `.superpowers/sdd/2026-08-20-graph-workspace-batch-h-lifecycle-ui-continuation`, `.superpowers/sdd/2026-08-20-graph-workspace-batch-h-lifecycle-audit-reissue`, and `.superpowers/sdd/2026-08-20-graph-workspace-batch-h-pointer-continuation` are immutable audit history only. Do not reopen, modify, or cite their child reports as approval evidence.
- Treat `50d0c3bd1502158b19f3ea459cc6cfe5044525d0` as an unreviewed carry-forward candidate. Do not amend, recreate, or revert it; Task 1 and its fresh task review establish whether its exact range can be retained.
- For every dispatch, persist the full `sdd-state render-prompt` output as a read-only role envelope under the fresh run root. Persist and dispatch a short pointer prompt naming that envelope and requiring the child to read it before acting. Byte-compare the candidate pointer with the stored pointer before spawn, record the returned session immediately, and compare the completed child transcript's initial user message byte-for-byte with the stored pointer before admitting the child report. A mismatch or missing required report is terminal for that run.
- Keep child output bounded. For Gradle or other verbose commands, redirect complete stdout/stderr to files beneath the run root, inspect only exit status plus short `tail`/summary output, and do not paste full logs into the child transcript. Write the required report artifact immediately after verification and before any optional explanation; the report is the only completion evidence.

### File Structure

- `control/WorkspaceSessionStatus.java` carries immutable save/history/source-map status from the control layer to the view.
- `control/WorkspaceSessionStatusPublisher.java` is package-private session ownership for store events and command results.
- `window/GraphStatusBar.java` and the dialog classes are thin views over immutable values and injected callbacks.
- `window/OpenGraphWorkspaceAction.java` is application-scoped; the existing `GraphGroupAction` remains a direct map actor.

## Task 1: Audit the carried failed-close lifecycle correction

**Implementer tier:** Capable

**Files:**
- Inspect only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/DefaultGraphWorkspaceController.java`
- Inspect only: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/DefaultGraphWorkspaceControllerShould.java`
- Inspect only: Git range `4ef35f187444a9a4bdab726c5f6a14dcab335fd9..50d0c3bd1502158b19f3ea459cc6cfe5044525d0`

**Interfaces:**
- Consumes: `DefaultGraphWorkspaceController.closeSession`, `WorkspaceSessionStatusPublisher`, `WorkspaceCloseController.saveAndClose/retrySaveAndClose`, the captured store listener registration, and the exact committed range `4ef35f187444a9a4bdab726c5f6a14dcab335fd9..50d0c3bd1502158b19f3ea459cc6cfe5044525d0`.
- Produces: audit evidence only. It makes no source, test, resource, or build-file change and creates no commit.

- [ ] **Step 1: Establish exact carry-forward scope and repository cleanliness**

Verify that `4ef35f187444a9a4bdab726c5f6a14dcab335fd9..50d0c3bd1502158b19f3ea459cc6cfe5044525d0` contains exactly the required lifecycle commit, has the required `2026-08-10-graph-workspace:` subject, and changes only the two listed paths. Inspect the full diff, run `git diff --check 4ef35f187444a9a4bdab726c5f6a14dcab335fd9..50d0c3bd1502158b19f3ea459cc6cfe5044525d0`, and confirm `git status --short` has no source changes. Do not use any terminal child report as evidence.

- [ ] **Step 2: Independently inspect the failure and success lifecycles**

Verify that `closeSession()` leaves `WorkspaceSessionStatusPublisher` registered while `store.close()` or `discardAndClose()` can throw, calls `reopenAfterSaveFailureLocked()` on store failure, and closes the publisher only after a successful store operation. Verify that the new regression observes a live status binding and `SAVE_FAILED` delivery after the first failed save-close, then exactly one publisher-registration close and no later status delivery after retry succeeds. Verify the existing successful close-order test now expects store close before publisher close, followed by updates, leases, scheduler, and view teardown, without leaving `CLOSING` state on failure.

- [ ] **Step 3: Re-run independent verification without mutating the candidate**

Run both fresh suites with the required JDK. Redirect complete output to files beneath the run root, preserve each exit code, and report only bounded summaries and concrete test totals:

```bash
mkdir -p "$RUN_ROOT/logs"
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" gradle :freeplane_plugin_graph:test \
  --tests '*DefaultGraphWorkspaceControllerShould' -PTestLoggingFull --rerun-tasks \
  >"$RUN_ROOT/logs/task-1-focused.log" 2>&1
focused_status=$?
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" gradle :freeplane_plugin_graph:test \
  -PTestLoggingFull --rerun-tasks \
  >"$RUN_ROOT/logs/task-1-full.log" 2>&1
full_status=$?
tail -n 40 "$RUN_ROOT/logs/task-1-focused.log"
tail -n 40 "$RUN_ROOT/logs/task-1-full.log"
test "$focused_status" -eq 0
test "$full_status" -eq 0
```

Write the required report immediately after these checks. Report concrete results, the exact inspected range, and any finding. Do not alter the two source files or create a commit. The following fresh task-reviewer gate independently approves or rejects this exact range.

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
- Consumes: `CanvasState`, `OperationalStatus`, `GraphProjection`, `ProjectedEdge`, `EdgeContributor`, `RelationshipResolution`, `RelationshipStatus`, and the audited `GraphWorkspaceViewBinding.currentSessionStatus()`/`addSessionStatusListener(WorkspaceSessionStatusListener)` methods.
- Produces: package-private headless models. `ContributorInspector` captures displayed generation, exact edge key, immutable contributor rows/key/optional connector descriptor. `PurgeConfirmationDialog` captures displayed generation plus immutable missing rows. `WorkspaceCloseDialog` calls only `WorkspaceCloseController`. `GraphStatusBar` consumes immutable status values and emits only `GraphCommand` via an injected callback.

- [ ] **Step 1: Write headless dialog and status tests first**

Create `WorkspaceDialogsShould` covering all cases below without a visible `JFrame` or `JDialog`:

- Status renders map availability, projected node/edge counts, selected safe endpoint text, layout state, recoverable count, missing-node count, workspace dirty/save-failed state, dirty source-map names/count, workspace history availability, and the 2,000-node and 5,000-edge warnings independently.
- Retry Save emits `GraphCommands.retrySave()`, Restart Layout emits `GraphCommands.restartLayout()`, and Unpin All emits `GraphCommands.unpinAll()`. Read-only disables workspace mutation controls while leaving layout restart available.
- Contributor rows contain only safe source/middle/target labels, known owner display name, exact key, and optional displayed descriptor. Delete one emits `deleteContributor(displayedGeneration, key, displayedDescriptor)`; delete all emits `deleteAllContributors(displayedGeneration, edgeKey, displayedKeys, displayedNativeDescriptorMap)`.
- Purge contains only `UNRESOLVED_MISSING_NODE` records, preserves both precomputed endpoint descriptions and relationship IDs, disables empty purge, and emits `purge(displayedGeneration, displayedRelationshipIds)` without substituting a later generation.
- Close Retry calls `retrySaveAndClose`, Discard calls `discardAndClose`, Cancel calls `cancelClose`, and an unsuccessful retry/discard does not invoke completion.
- An editor-activating command result calls the injected graph-focus callback exactly once after routing; a non-activating result does not.

Extend `GraphWorkspaceWindowModelShould` to assert status-listener registration closes with the canvas registration; tests still must not call `setVisible`.

- [ ] **Step 2: Run the focused tests red**

Run:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" gradle :freeplane_plugin_graph:test \
  --tests '*WorkspaceDialogsShould' \
  --tests '*GraphWorkspaceWindowModelShould' -PTestLoggingFull
```

Expected before implementation: compilation or assertion failure because the operational models and routing are absent.

- [ ] **Step 3: Implement immutable surfaces and exact routing**

Build each dialog around captured immutable data and injected command/close callbacks. Thin modal wrappers may run only when graphics are available; tests use models/actions directly.

`ContributorInspector` derives rows from the exact displayed `ProjectedEdge`, uses `SafeNodeLabel.displayText()` and registered map display names only, and preserves descriptors only for native contributors. `PurgeConfirmationDialog` receives rows already filtered to `UNRESOLVED_MISSING_NODE`; derive safe endpoint descriptions from immutable resolution/projection data and registered map names, never include recoverable rows. `GraphStatusBar` uses stable component names and compact fixed height, maps dirty source IDs to registered display names, labels node/edge warnings separately, and sends its three controls only through the injected command callback.

In `GraphWorkspaceWindowModel`, subscribe to canvas and session-status bindings and close both registrations. Pass a private delegating `GraphWorkspaceHandle` to existing toolbar/map/settings components so every command result goes through one method; invoke the supplied graph-focus callback only when `editorViewActivated()` is true. Handle `InspectEdge`, `DeleteContributor`, and `DeleteAllContributors` by locating the exact current edge/contributor in `currentState` and using current displayed generation; absent values emit no command. Add a purge entry point from only current missing-node resolutions. Initial close calls `saveAndClose`; only a false result exposes `WorkspaceCloseDialog`, whose retry/discard completion closes the view only after true.

- [ ] **Step 4: Commit the verified operational UI**

Run the focused command in Step 2, then run:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" gradle :freeplane_plugin_graph:test -PTestLoggingFull
git diff --check
```

Stage only the seven Files paths and commit:

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/ContributorInspector.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/PurgeConfirmationDialog.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphStatusBar.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/WorkspaceCloseDialog.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/WorkspaceDialogsShould.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java
git commit -m "2026-08-10-graph-workspace: Add graph workspace operational UI"
```

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
- Consumes: `GraphWorkspaceController.open(Path)`, `SwingGraphWorkspaceViewFactory`, `DefaultGraphWorkspaceController`, `GraphGroupController`, `ModeController.addAction/removeAction`, audited lifecycle/status APIs, and Task 2 status/command routing.
- Produces: `OpenGraphWorkspaceAction extends AFreeplaneAction` with key `OpenGraphWorkspaceAction`; it accepts `GraphWorkspaceController` and injectable `Supplier<Path>`, opens only non-null selected paths, and creates no session command.
- Produces: application scope for Open Graph Workspace, direct map-actor scope for `GraphGroupAction`, and existing-session scope for window controls. No action crosses scope silently.

- [ ] **Step 1: Write plugin and undo-routing tests first**

Create `UndoRoutingShould` using explicit `WorkspaceSessionStatus` values. Verify `Ctrl+Z` and `Ctrl+Y` route only `undoWorkspace()` and `redoWorkspace()`; workspace toolbar/Edit actions use localized labels and disable exactly when snapshot history is empty; separately named source-map Undo includes the target map name, enables only for a present `canUndo()` target, routes `undoSourceMap()`, and has no graph-window shortcut; every toolbar/menu/status session control reaches the routed handle while retaining read-only behavior.

Create `GraphPluginIntegrationShould` with injectable chooser suppliers and mocked mode components. Verify `OpenGraphWorkspaceAction` ignores cancelled chooser and opens both existing/new `.fpg` paths through the application controller; `GraphModeExtension` registers/removes it with the existing graph group controller/painter; XML places `OpenGraphWorkspaceAction` in View and `GraphGroupAction` directly beside `CloudAction`; resource keys point to both SVGs; and action names/tooltips exist in `Resources_en.properties`. Scope Mockito static `TextUtils` mocks around headless tests so no test requires a global Freeplane application.

- [ ] **Step 2: Run the focused tests red**

Run:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" gradle :freeplane_plugin_graph:test \
  --tests '*UndoRoutingShould' \
  --tests '*GraphPluginIntegrationShould' -PTestLoggingFull
```

Expected before implementation: compilation or assertion failure because the action, resources, menu entries, and dynamic undo state are absent.

- [ ] **Step 3: Compose the application action without changing map scope**

Register the resource loader before actions. Resolve the controller/view-factory construction cycle in `GraphModeExtension` with a private forwarding `GraphWorkspaceController` used only by `SwingGraphWorkspaceViewFactory`; bind it to the completed `DefaultGraphWorkspaceController` before registering `OpenGraphWorkspaceAction`. Retain/remove that action during extension lifecycle. Leave `GraphGroupController` and its direct map action unchanged.

Implement `OpenGraphWorkspaceAction` with a package-visible supplier constructor for tests and production chooser `GraphWorkspaceWindow::chooseWorkspacePath`. The application controller, not this action, owns new/existing workspace creation and duplicate-window focus.

- [ ] **Step 4: Localize the shell and keep histories distinct**

Replace visible Graph Workspace string literals in every listed window class with English viewer `graph_workspace.*` keys through `TextUtils`; use `TextUtils.format` for map names/counts. Add keys for menus, controls, tooltips, status fields, warnings, dialog copy, contributor/purge descriptions, save/layout/no-op errors, and separate workspace/source-map undo labels. Pass only safe display values as translation arguments.

Add a source-map undo Action whose localized name includes `MapUndoTarget.mapName()`. Update workspace undo/redo and source-map action enabled state when `WorkspaceSessionStatus` changes. Keep `Ctrl+Z`/`Ctrl+Y` bound only to workspace actions.

Add valid standalone `GraphGroup.svg` using fixed coral `#DF625D` without accent substitution, and a distinct valid `GraphWorkspace.svg`. Add icon mappings in `freeplane.properties`. Add `GraphGroupAction` immediately after `CloudAction` in the editor toolbar row and `OpenGraphWorkspaceAction` under View. Do not add a menu item that invokes session commands outside an existing workspace window.

- [ ] **Step 5: Commit and verify the exact plugin/UI allowlist**

Run all of these commands:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" gradle :freeplane_plugin_graph:test \
  --tests '*UndoRoutingShould' \
  --tests '*GraphPluginIntegrationShould' -PTestLoggingFull
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" gradle :freeplane_plugin_graph:test -PTestLoggingFull
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" gradle :freeplane:compileJava
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" gradle format_translation
file freeplane/src/editor/resources/translations/Resources_*.properties freeplane/src/viewer/resources/translations/Resources_en.properties | grep -v "ASCII text"
test -z "$(git diff --name-only -- freeplane/src/editor/resources/translations)"
test "$(git diff --name-only -- freeplane/src/viewer/resources/translations)" = "freeplane/src/viewer/resources/translations/Resources_en.properties"
git diff --check
```

Stage only the eighteen Files paths and commit:

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/OpenGraphWorkspaceAction.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/GraphModeExtension.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/WorkspaceToolbar.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/MapListPanel.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/WorkspaceSettingsPanel.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/ContributorInspector.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/PurgeConfirmationDialog.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphStatusBar.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/WorkspaceCloseDialog.java \
  freeplane_plugin_graph/src/main/resources/images/GraphGroup.svg \
  freeplane_plugin_graph/src/main/resources/images/GraphWorkspace.svg \
  freeplane/src/external/resources/xml/mindmapmodemenu.xml \
  freeplane/src/viewer/resources/freeplane.properties \
  freeplane/src/viewer/resources/translations/Resources_en.properties \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/UndoRoutingShould.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphPluginIntegrationShould.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java
git commit -m "2026-08-10-graph-workspace: Wire graph workspace entry points"
```
