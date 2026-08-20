# Graph Workspace Batch H Task 3 Report Recovery Plan

> **For agentic workers:** Use the deterministic subagent-driven-development controller for this fresh successor run. The prior run `2026-08-20-graph-workspace-batch-h-review-report-continuation` is terminal `DISPATCH_MISMATCH_BLOCKED`; preserve it unchanged and do not use its Task 3 child report or verdict as evidence.

**Goal:** Freshly certify the already committed Graph Workspace Batch H Task 3 entry-point/UI change after the prior child wrote its report outside the pinned run root, then complete an independent whole-branch final review.

**Architecture:** The valid Task 3 commit is audited without source or test mutation. A fresh task reviewer independently checks the exact eighteen-file range. After that task is approved, the controller dispatches the mandatory Frontier final review over the complete branch from the pinned merge base. Git commits, fresh verification logs, and reports in this successor run are the only admissible evidence for this run.

**Tech Stack:** Java 8 source and bytecode, Gradle multi-project build, Swing/AWT, JUnit 4, AssertJ, Mockito, Freeplane action/resource/menu conventions, SVG, and ISO-8859-1 properties resources.

## Global Constraints

- Continue in `/data/home/guest/Development/freeplane/.worktrees/graph-workspace-batch-h-ui-shell`; do not create a new worktree.
- Use `JAVA_HOME=$HOME/.sdkman/candidates/java/21.0.8-zulu` and `gradle`, never Maven or the Gradle wrapper.
- Preserve Java 8 source/target compatibility, four-space Java indentation, and repository testing conventions.
- `freeplane_plugin_graph` extends MindMap mode only, makes no `freeplane_api` changes, and neither subclasses nor replaces `MapView`.
- Freeplane `MapModel` and `NodeModel` reads remain EDT-only. Window code communicates through the established graph workspace handles, bindings, and close controller rather than stores, routers, map models, or the Freeplane controller.
- The graph window remains modeless and headless-testable. Tests must not call `setVisible`.
- Keep workspace history and source-map history distinct: `Ctrl+Z`/`Ctrl+Y` route only workspace undo/redo; source-map undo is separately named, has no graph-window shortcut, and is independently status-controlled.
- Use only safe displayed values in dialogs and controls. Preserve captured generations, contributor keys/descriptors, and relationship IDs.
- Add or modify only the English viewer translation file for translations. Never bulk-edit Weblate-managed editor translations. Run `gradle format_translation` and verify ASCII/escaped ISO-8859-1 output.
- The following prior run roots are immutable evidence only and must not be reopened, modified, or cited as approval evidence: `.superpowers/sdd/2026-08-20-graph-workspace-batch-h-review-report-continuation`, `.superpowers/sdd/2026-08-20-graph-workspace-batch-h-ui-shell-continuation`, `.superpowers/sdd/2026-08-20-graph-workspace-batch-h-ui-successor`, `.superpowers/sdd/2026-08-20-graph-workspace-batch-h-post-task-2-mismatch`, `.superpowers/sdd/2026-08-20-graph-workspace-batch-h-status-lifecycle-recovery`, `.superpowers/sdd/2026-08-20-graph-workspace-batch-h-lifecycle-ui-continuation`, `.superpowers/sdd/2026-08-20-graph-workspace-batch-h-lifecycle-audit-reissue`, `.superpowers/sdd/2026-08-20-graph-workspace-batch-h-pointer-continuation`, and `.superpowers/sdd/2026-08-20-graph-workspace-batch-h-bounded-audit-continuation`.
- The exact carried Task 3 range is `fa8eb63fe622f8aa7552d937780f21f5e37e0bd8..fb71eaa782d13dbe1b767a6d6fceef58b8caaa91`. It contains one committed UI/entry-point change. Do not amend, revert, recreate, or extend that commit during the audit.
- For every child dispatch, persist the complete renderer output as a read-only envelope beneath this fresh run root, dispatch only a short pointer, compare pointer bytes before spawn, record the returned session immediately, and compare the completed child's first user message byte-for-byte with the stored pointer before admitting a report. A mismatch or missing report is terminal for this fresh run.
- Redirect verbose build output to files beneath this fresh run root and inspect bounded summaries. The required child report must be the immediate next action after the child’s final verification command.
- The audit implementer and task reviewer must not use CodeGraph or broad unrelated source dumps. The task reviewer inspects only the exact eighteen-file range and runs the permitted focused UI/integration suites. The final reviewer independently inspects the complete branch and fresh evidence.

## Task 1: Audit the carried Task 3 entry-point and UI commit

**Implementer tier:** Capable

**Files:**
- Inspect only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/OpenGraphWorkspaceAction.java`
- Inspect only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/GraphModeExtension.java`
- Inspect only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java`
- Inspect only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/WorkspaceToolbar.java`
- Inspect only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/MapListPanel.java`
- Inspect only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/WorkspaceSettingsPanel.java`
- Inspect only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/ContributorInspector.java`
- Inspect only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/PurgeConfirmationDialog.java`
- Inspect only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphStatusBar.java`
- Inspect only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/WorkspaceCloseDialog.java`
- Inspect only: `freeplane_plugin_graph/src/main/resources/images/GraphGroup.svg`
- Inspect only: `freeplane_plugin_graph/src/main/resources/images/GraphWorkspace.svg`
- Inspect only: `freeplane/src/external/resources/xml/mindmapmodemenu.xml`
- Inspect only: `freeplane/src/viewer/resources/freeplane.properties`
- Inspect only: `freeplane/src/viewer/resources/translations/Resources_en.properties`
- Inspect only: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/UndoRoutingShould.java`
- Inspect only: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphPluginIntegrationShould.java`
- Inspect only: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java`
- Inspect only: Git range `fa8eb63fe622f8aa7552d937780f21f5e37e0bd8..fb71eaa782d13dbe1b767a6d6fceef58b8caaa91`

**Interfaces:**
- Inspect `OpenGraphWorkspaceAction`, `GraphWorkspaceController.open(Path)`, `GraphModeExtension` action lifecycle, `SwingGraphWorkspaceViewFactory`, `GraphGroupController`, `ModeController.addAction/removeAction`, the Task 2 status/command routing, viewer resource loading, menu XML placement, SVG icon mapping, workspace undo/redo, and source-map undo status.
- Produce audit evidence only. Do not modify source, tests, resources, translations, build files, or Git history, and create no commit.

- [ ] **Step 1: Pin exact Git scope and cleanliness**

Verify that the exact range contains one commit with subject `2026-08-10-graph-workspace: Wire graph workspace entry points`, changes exactly the eighteen listed paths, has no whitespace errors, and leaves the source worktree clean. Do not treat the misplaced report from the terminal predecessor as evidence.

- [ ] **Step 2: Inspect behavior against the Task 3 requirements**

Independently inspect the full exact diff and the named source paths for:

- application-scoped `OpenGraphWorkspaceAction` behavior, null chooser handling, existing/new path routing, and absence of session commands;
- `GraphModeExtension` resource loading, forwarding-controller construction cycle, action registration/removal, and preservation of direct map-scoped `GraphGroupAction` behavior;
- View/editor menu placement and icon/resource key wiring;
- localization of visible graph workspace shell text through English viewer keys, safe translation arguments, enum-backed status labels, and unchanged editor translation files;
- workspace undo/redo shortcuts and action enablement versus separately named source-map undo target enablement and routing;
- read-only behavior, status-listener updates, headless model seams, and no `setVisible` in tests;
- valid standalone SVG syntax, fixed GraphGroup coral `#DF625D`, and a distinct GraphWorkspace asset.

Record any concrete correctness, scope, or compatibility concern with file and line evidence. Do not fix it in this audit.

- [ ] **Step 3: Run fresh bounded verification without mutating the candidate**

Use the required Java runtime and redirect complete output under the fresh run root. Run the focused UI/integration suites, the full graph plugin suite, the application compile, and translation formatting/encoding checks:

```bash
mkdir -p "$RUN_ROOT/logs"
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" gradle :freeplane_plugin_graph:test \
  --tests '*UndoRoutingShould' --tests '*GraphPluginIntegrationShould' \
  --tests '*GraphWorkspaceWindowModelShould' -PTestLoggingFull \
  >"$RUN_ROOT/logs/task-1-focused.log" 2>&1
focused_status=$?
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" gradle :freeplane_plugin_graph:test -PTestLoggingFull \
  >"$RUN_ROOT/logs/task-1-full.log" 2>&1
full_status=$?
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" gradle :freeplane:compileJava \
  >"$RUN_ROOT/logs/task-1-freeplane-compile.log" 2>&1
compile_status=$?
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" gradle format_translation \
  >"$RUN_ROOT/logs/task-1-format-translation.log" 2>&1
translation_status=$?
tail -n 40 "$RUN_ROOT/logs/task-1-focused.log"
tail -n 40 "$RUN_ROOT/logs/task-1-full.log"
tail -n 40 "$RUN_ROOT/logs/task-1-freeplane-compile.log"
tail -n 40 "$RUN_ROOT/logs/task-1-format-translation.log"
test "$focused_status" -eq 0
test "$full_status" -eq 0
test "$compile_status" -eq 0
test "$translation_status" -eq 0
```

Then run the mandated translation and scope checks, `git diff --check`, and verify that the editor translation tree is unchanged and only the English viewer translation is in the carried range. Write the required audit report immediately after the final verification action. The report must state concrete test outcomes and any concern; it must not claim approval or cite any report from the predecessor run.

The fresh task reviewer must inspect only this exact range and these eighteen paths, run only the focused UI/integration suites with bounded output, write its report before optional explanation, and return independent `SPEC` and `QUALITY` verdicts. After task approval, the controller must perform a separate Frontier final review over the full branch range from merge base `b4ecf2fb2baf392c62c1add6c263d78994fb0cd2` through the final HEAD, with no unresolved load-bearing finding, before declaring completion.
