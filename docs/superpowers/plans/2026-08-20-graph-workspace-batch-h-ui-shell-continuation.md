# Graph Workspace Batch H UI Shell Continuation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.
>
> This is a fresh audit run after the prior run reached
> `DISPATCH_MISMATCH_BLOCKED`. The prior run is retained unchanged for audit
> history. The valid Task 34 source correction is already committed at
> `211574f2447debb645e610cb05a5843915056c3d` over
> `9699f864b37253c365958d5b0244ad907ec5341c`.

**Goal:** Carry forward and independently approve the Task 34 shell correction, then implement Graph Workspace UI Tasks 35 and 36 in order: operational dialogs/status surfaces, and Freeplane/plugin entry-point wiring.

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
- Every implementation task stages only its exact Files paths, never a directory, and each task commit starts `2026-08-10-graph-workspace:` with an imperative subject.
- The blocked predecessor run at `.superpowers/sdd/2026-08-20-graph-workspace-batch-h-ui-shell-amended` is audit history only. Do not use its inadmissible re-review verdict as evidence.

## Task 1: Audit and independently review the Task 34 correction

**Implementer tier:** Capable

**Files:**
- Inspect only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/GraphWorkspaceViewBinding.java`
- Inspect only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/DefaultGraphWorkspaceController.java`
- Inspect only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java`
- Inspect only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/SwingGraphWorkspaceViewFactory.java`
- Inspect only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/MapListPanel.java`
- Inspect only: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java`
- Inspect only: Git range `9699f864b37253c365958d5b0244ad907ec5341c..211574f2447debb645e610cb05a5843915056c3d`

**Required behavior:**
- Make no source, test, resource, or build-file changes. This task audits the already committed correction.
- Verify that the exact correction range contains only the Task 34 allowlisted paths, has the required commit prefix, and leaves the worktree source-clean.
- Independently inspect the code and tests for the carried findings F-1, F-2, and F-3. Verify that registration snapshots drive unavailable rows, delayed viewport handling reaches the first non-empty state exactly once, and the headless shell model executes core behavior without JFrame construction or `setVisible`.
- Verify the required Java 21 toolchain, focused tests in normal and explicit headless mode, the full `freeplane_plugin_graph` suite, and diff checks when the environment permits. Treat the prior fixer report as a claim and establish evidence from the range and commands.
- Do not amend or recreate commit `211574f2447debb645e610cb05a5843915056c3d`.

**Test requirements:**
- Run the focused `GraphWorkspaceWindowModelShould` tests normally and with `-Djava.awt.headless=true`, using `JAVA_HOME=$HOME/.sdkman/candidates/java/21.0.8-zulu`.
- Run `gradle :freeplane_plugin_graph:test -PTestLoggingFull` with the exact JDK and inspect `git diff --check 9699f864b37253c365958d5b0244ad907ec5341c..211574f2447debb645e610cb05a5843915056c3d`.
- Report the audit outcome without creating a commit. The later task review is the independent approval gate over the same exact range.

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
