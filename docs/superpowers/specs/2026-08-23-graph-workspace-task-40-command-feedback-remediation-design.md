# Graph Workspace Task 40 Command Feedback Remediation Design

## Context

The Task 40 verification-recovery run ended in terminal `FINAL_BLOCKED` at revision 22. Its command acceptance source and native undo correction are preserved at commit `d19856a2202b44be0f5d1bc06001eec19d6f91d8`. The final reviewer opened load-bearing finding `F-3`: Scenario 16 proves internal null-ID rejection and retry mechanics, but production does not display the required instruction to open and save the map once, and the acceptance fixture assigns an ID by calling `NodeModel.createID()` directly instead of crossing the production file-save serialization boundary.

The user approved a fresh successor scope that may modify the narrow production window, English resource, and tests required to resolve `F-3`. The terminal predecessor run remains immutable.

## Goal

Make an ID-less native connector rejection visibly instruct the user to open and save the map once, then prove that an ordinary production file serialization assigns the persistent node ID used by a successful retry.

## Architecture

`GraphWorkspaceWindowModel` receives a `Consumer<String>` command-message sink at its core constructor boundary. `GraphWorkspaceWindow` supplies a sink backed by Freeplane's established `ViewController.out(String)` status line. Existing package-level model constructors retain test ergonomics by delegating with a no-op sink, while focused tests can inject a recorder.

After `GraphWorkspaceHandle.execute(...)` returns and the window refreshes its presentation, the model formats the result's existing `messageKey` and `messageArguments` through `TextUtils`. It publishes only `GraphCommandResult.Status.REJECTED` and `GraphCommandResult.Status.NO_OP`. Successful `APPLIED` results remain silent so high-frequency viewport and display commands cannot repeatedly overwrite the Freeplane status line.

The command result model, executor result key, Graph Workspace status bar, and command routing interfaces do not change.

## User-Facing Copy

The existing English fallback resource changes from:

```text
The connector target must have a saved identifier.
```

to:

```text
The connector target has no saved identifier. Open and save the map once, then retry.
```

The message remains under `graph_workspace.connector.target_requires_saved_id`. It is ASCII and therefore compatible with the repository's properties-file encoding and translation formatting workflow. Other locales continue to use the existing English Graph Workspace fallback behavior; no new resource key is introduced.

## Scenario 16 Data Flow

1. The connector command receives a transient target path whose `NodeModel` has no ID.
2. `FreeplaneMapCommandExecutor` returns the existing rejected result before native connector, undo, map, or workspace mutation.
3. `GraphWorkspaceWindowModel` localizes that result and sends the explicit instruction to the injected Freeplane status-line sink.
4. The acceptance fixture serializes the map through production `MapWriter.writeMapAsXml(..., MapWriter.Mode.FILE, CopiedNodeSet.ALL_NODES, false)` to its real temporary `.mm` path.
5. Production `NodeWriter` assigns and writes the ordinary node ID as part of file serialization. The fixture marks the completed successful save state only after serialization returns.
6. The acceptance test verifies the target ID is non-null, the persisted XML contains that same ID, and the source key is persistent.
7. The reissued connector command succeeds with the writer-assigned ID. Existing native actor count, `canUndo`, transaction depth, map state, connector state, workspace identity, and workspace history assertions remain.

`MapWriter` is the exact production ID-assignment seam used by normal map file saves. The acceptance fixture does not pull interactive file choosers, locking dialogs, backup policy, or global `MFileManager` state into a headless command test.

## Error Handling

A null command result emits no message. `APPLIED` results emit no message. A `REJECTED` or `NO_OP` result is localized with all existing message arguments and passed to the sink exactly once. Sink failures are not swallowed because silently losing user feedback would hide a production defect.

The successful-save acceptance path marks the map saved only after production serialization completes. An exception from serialization fails the test and does not proceed to retry. The design does not change production save failure behavior.

## Files

Product changes are restricted to:

- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java`
- `freeplane/src/viewer/resources/translations/Resources_en.properties`
- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java`
- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceCommandAcceptanceShould.java`

The successor design and implementation plan are additional process records. No other production, API, build, dependency, resource, shared-fixture, or test file may change.

## Test Design

Focused window-model coverage injects a recording sink and proves:

- a rejected result is localized and emitted exactly once;
- message arguments are preserved through formatting;
- a no-op result is localized and emitted exactly once;
- an applied result emits nothing.

Scenario 16 additionally proves the real English fallback contains `Open and save the map once, then retry`, the target has no ID before serialization, production `MapWriter.Mode.FILE` serialization with `CopiedNodeSet.ALL_NODES` assigns the ID, the persisted `.mm` bytes contain that ID, and the retry succeeds without weakening atomic rejection checks.

Required disposable mutants are:

- remove command-message publication: focused window test turns red;
- publish `APPLIED` results: focused window test turns red;
- restore the old translation text: Scenario 16 turns red;
- bypass `MapWriter` and call `target.createID()` directly: Scenario 16 turns red because the expected serialized ID is absent;
- disable `NodeWriter` ID creation for `Mode.FILE`: Scenario 16 turns red at the ID/persisted XML boundary.

Verification uses Zulu Java 21 with Java 8-compatible source APIs and a data-backed Task 40 temp root. Gradle commands run serially. Required gates are focused window-model and command-acceptance tests, the full graph-plugin suite, `gradle format_translation`, ASCII and malformed-escape checks for properties files, source mutants, `git diff --check`, and exact file-scope inspection.

## SDD Recovery

A fresh one-task deterministic SDD run carries predecessor finding `F-3`; it does not reopen or edit the terminal predecessor state. The implementation task uses the `Capable` tier because it crosses production presentation, localization, serialization-backed acceptance, and mutation verification. Frontier task review and mandatory Frontier final review inspect the complete original merge-base-to-new-HEAD range. Completion requires `F-3` source-backed reconciliation and no regression to fixed `F-2` native undo atomicity.
