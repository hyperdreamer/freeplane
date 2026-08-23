# Graph Workspace Task 40 Command Feedback Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic subagent-driven-development controller to implement this plan task-by-task.

**Goal:** Resolve Task 40 final finding F-3 by displaying the normal-save instruction for rejected ID-less connector commands and proving the retry crosses production map-file serialization.

**Architecture:** Inject a command-message sink into `GraphWorkspaceWindowModel`; the real `GraphWorkspaceWindow` routes it to Freeplane's existing `ViewController.out(String)` status line. Localize and publish only `REJECTED` and `NO_OP` results. Extend Scenario 16 to serialize the native map through the existing production `MapWriter` seam with `Mode.FILE` and `CopiedNodeSet.ALL_NODES`, then retry using the writer-assigned ID.

**Tech Stack:** Java 8-compatible source APIs, Zulu Java 21.0.8, Gradle, JUnit 4, AssertJ, Mockito inline static mocking, Swing/AWT, Freeplane `ViewController`, `TextUtils`, `MapWriter`, and native `MMapModel`/`NodeModel` fixtures.

## Global Constraints

- Use only `/data/home/guest/Development/freeplane/.worktrees/graph-workspace-batch-j-task-40` on branch `2026-08-10-graph-workspace-task-40-command-acceptance`.
- Use Zulu Java 21 at `/home/guest/.sdkman/candidates/java/21.0.8-zulu` and `gradle`; do not use Maven or the Gradle wrapper.
- Use `TMPDIR=/data/home/guest/.tmp/freeplane-graph-batch-j-task-40` and `-Djava.io.tmpdir=/data/home/guest/.tmp/freeplane-graph-batch-j-task-40`; do not use host `/tmp`.
- Preserve terminal predecessor run `batch-j-task-40-verification-recovery` at `FINAL_BLOCKED`; carry fixed F-2 and open F-3 without editing its state or ledger.
- Product changes are limited to `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java`, `freeplane/src/viewer/resources/translations/Resources_en.properties`, `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java`, and `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceCommandAcceptanceShould.java`.
- Do not modify `GraphCommandResult`, command executors, `GraphStatusBar`, `MFileManager`, `MapWriter`, `NodeWriter`, build files, dependencies, shared fixtures, other resources, other tests, or translations.
- Keep all final product source Java 8-compatible; use no newer language syntax or APIs.
- Serialize Gradle verification; before each Gradle command require `/data/home/guest/.tmp/freeplane-graph-batch-j-task-40/task41-verification-clear` or an equivalent persisted completion marker for the now-complete Task 41 lane.
- Before committing, require a clean index, exactly the four allowlisted product paths staged, `git diff --cached --check`, and a subject beginning `2026-08-10-graph-workspace:`.

## Task 1: Expose Command Feedback And Prove Normal Save Retry

**Implementer tier:** Capable

**Files:**
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java:1-end`
- Modify: `freeplane/src/viewer/resources/translations/Resources_en.properties:817`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java:1-end`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceCommandAcceptanceShould.java:1-end`
- Read-only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/GraphCommandResult.java:1-end`
- Read-only: `freeplane/src/main/java/org/freeplane/features/ui/ViewController.java:40-114`
- Read-only: `freeplane/src/main/java/org/freeplane/features/map/MapWriter.java:43-140`
- Read-only: `freeplane/src/main/java/org/freeplane/features/map/NodeWriter.java:120-140`
- Read-only: terminal predecessor artifacts under `.superpowers/sdd/batch-j-task-40-verification-recovery/`

**Interfaces:**
- Consume `GraphCommandResult.status()`, `messageKey()`, and `messageArguments()` without changing `GraphCommandResult`.
- Consume `TextUtils.format(String, Object...)` to localize an existing result key and arguments.
- Consume `ViewController.out(String)` through the real window's injected `Consumer<String>` sink.
- Consume the existing `MapWriter.writeMapAsXml(MapModel, Writer, MapWriter.Mode.FILE, CopiedNodeSet.ALL_NODES, false)` production serialization seam; `NodeWriter` assigns missing IDs during `Mode.FILE` serialization.
- Produce a four-file product correction, a committed acceptance-test result, and one implementer report at the successor run root.

- [ ] **Step 1: Pin the predecessor and write falsifiable red tests**

Record the current HEAD `3bd156a2c5` descendant of `d19856a2202b44be0f5d1bc06001eec19d6f91d8`, the source hashes, the four-file allowlist, and the open F-3 evidence in the run report context. In `GraphWorkspaceWindowModelShould`, add a recording `Consumer<String>` fixture path and tests that execute mocked rejected, no-op, and applied `GraphCommandResult` values through the model. The rejected and no-op cases must expect one localized message, while the applied case must expect no message. Use `WorkspaceTransition.rejected(...)`, `WorkspaceTransition.noOp(...)`, and `WorkspaceTransition.applied(...)` or equivalent real result construction, and configure the existing static `TextUtils` mock so formatted arguments are observable. Make the tests fail against the current model because no command-message sink exists.

- [ ] **Step 2: Add the status-line sink and result filtering**

Add `java.util.function.Consumer` to the window model boundary. Preserve existing package-level constructor call sites by delegating them to a canonical constructor with a no-op sink. Update the real `GraphWorkspaceWindow` construction to pass a sink that calls the current Freeplane `ViewController.out(String)`. In `GraphWorkspaceWindowModel.executeCommand`, keep the existing handle execution, presentation refresh, viewport handling, and focus behavior, then publish `TextUtils.format(result.messageKey(), result.messageArguments().toArray())` exactly once only when the result status is `REJECTED` or `NO_OP`. Do not publish for `null` or `APPLIED`. Do not swallow sink failures. Run the focused `GraphWorkspaceWindowModelShould` tests and record green counts.

- [ ] **Step 3: Pin the required English instruction before changing the resource**

Extend Scenario 16's acceptance assertions to read the real `freeplane/src/viewer/resources/translations/Resources_en.properties` through the existing repository-file helper and require the value for `graph_workspace.connector.target_requires_saved_id` to contain the exact instruction `Open and save the map once, then retry`. Keep the assertion independent of the test's `TextUtils` static mock. Run the focused command-acceptance test and observe the expected red result against the old copy.

- [ ] **Step 4: Update and format the localized copy**

Change only the English value for `graph_workspace.connector.target_requires_saved_id` to `The connector target has no saved identifier. Open and save the map once, then retry.` Run `gradle format_translation` with the mandated Zulu Java 21 and data-backed temporary directory. Confirm the focused resource assertion is green, the properties file remains ASCII text, and no malformed `\\uXXXX` escape or unrelated translation diff was introduced.

- [ ] **Step 5: Replace direct ID creation with production map serialization**

In `GraphWorkspaceCommandAcceptanceShould`, add the minimal imports and fixture wiring needed to use the existing controller-owned `MapWriter`: configure the test `MMapController` seam with its existing `ModeController` and a `WriteManager`, obtain a `MapWriter` from that seam, and register/use the map writer exactly as `MapController` does for the `map` element and attributes. Add a native fixture save helper that writes the target map to its real temporary `.mm` path with UTF-8 and calls `writeMapAsXml(map, writer, MapWriter.Mode.FILE, CopiedNodeSet.ALL_NODES, false)`. In `NativeNodes.saveTargetNormally`, remove the direct `target.createID()` call; invoke the helper, assert serialization completes, then mark the map saved and return a persistent source key built from the writer-assigned `target.getID()`.

Keep Scenario 16's pre-rejection assertions for null target ID, empty connectors, saved map, zero native undo calls, zero actor count, `canUndo() == false`, transaction depth zero, unchanged workspace identity, and workspace undo unavailable. After serialization, assert the target ID is non-null and the persisted `.mm` bytes contain that exact ID before executing the successful retry. Preserve all existing connector and dirty-state assertions after retry.

- [ ] **Step 6: Run focused verification and mutation probes**

Require the Task 41 completion marker, then run serial focused tests for `GraphWorkspaceWindowModelShould` and `GraphWorkspaceCommandAcceptanceShould` with `--no-daemon --no-parallel -PTestLoggingFull --rerun-tasks`, Zulu Java 21, and the Task 40 temp root. Collect XML totals and confirm no failures or errors. In disposable copies only, run these falsifiable mutants and restore/delete every archive and log afterward:

- remove command-message publication; the rejected-message window test must fail;
- publish `APPLIED` results; the applied-silence window test must fail;
- restore the old translation value; Scenario 16 must fail its instruction assertion;
- replace the production writer call with direct `target.createID()` and omit serialization; Scenario 16 must fail because the persisted XML lacks the assigned ID;
- disable `NodeWriter` ID creation for `MapWriter.Mode.FILE`; Scenario 16 must fail at the ID/persisted-byte boundary.

Recheck the active source hashes after mutant cleanup.

- [ ] **Step 7: Run the full graph-plugin verification and repository gates**

Run the full `:freeplane_plugin_graph:test` suite serially with the mandated environment and `--rerun-tasks`. Aggregate every XML suite and record exact suites, tests, skipped, failures, and errors. Scan relevant XML and standard error for hidden `SEVERE: Exception in thread`, `AWT-EventQueue-0`, or `Method not implemented` failures. Run `gradle format_translation` once more if needed, then verify `file freeplane/src/viewer/resources/translations/Resources_*.properties | grep -v "ASCII text"` is empty for the touched resource set, `git diff --check` passes, and the four-file product diff contains no unrelated changes. Confirm the fixed F-2 native undo assertions remain present and green.

- [ ] **Step 8: Commit and report**

Inspect `git status --porcelain=v1`, the exact staged diff, source hashes, and cached whitespace. Stage exactly the four allowlisted product files, leave process plan/spec artifacts unstaged unless the successor plan requires a separate process commit, and commit with `2026-08-10-graph-workspace: Expose command feedback`. Write exactly one implementer report with `STATUS`, changes, focused/full XML totals, status-line red/green evidence, writer and translation mutants, hidden-background scan, hashes, exact scope, commit SHA, and concerns. Return `DONE` only when the report and commit satisfy the role contract; use `DONE_WITH_CONCERNS` only for a non-load-bearing observational note.
