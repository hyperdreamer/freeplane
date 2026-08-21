# Graph Workspace Batch H Scope-Expanded Successor Specification

## Context

The provider-stop successor run `.superpowers/sdd/2026-08-21-graph-workspace-batch-h-provider-stop-successor` is terminal at `TASK_BLOCKED` after its independent Task 1 reviewer reproduced load-bearing finding `F-1`. The pinned plan marked the presentation files as audit-only, so that run could not legally correct the finding. The user approved a fresh scope-expanded plan and run; the blocked run and its artifacts remain sealed evidence.

Finding `F-1` is in `GraphCanvas.setDimUnrelated(boolean)`. The method currently copies the persisted preference into `GraphPaintState`, which is supposed to hold transient selection, hover, search, and connection-preview interaction state. With the default preference enabled and no active interaction trigger, the painter receives a dim flag and renders the whole graph at dim opacity.

## Approved Scope

The fresh run may modify exactly these source paths:

- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphCanvas.java`
- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java`
- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/DefaultGraphWorkspaceController.java`
- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/GraphModeExtension.java`
- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/DefaultGraphWorkspaceControllerShould.java`
- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphPluginIntegrationShould.java`

The first two paths correct `F-1`. The last four paths implement the previously planned deterministic extension shutdown. No other source path is authorized. Plan/spec files and ignored SDD run artifacts are process records, not source-scope expansion.

## Presentation Correction

`GraphPaintState.dimUnrelated` remains an interaction-state flag. `GraphCanvas.setPaintState` stores the supplied immutable state without replacing its dim flag, and `setDimUnrelated` changes only the persisted rendering option without changing the current paint state. The existing `GraphPainter.paint` overload already computes the effective dimming gate as both the transient state flag and the persisted option, so `GraphPainter.java` does not need to change.

Headless offscreen tests must establish all three cases through the canvas path:

1. With the default option enabled and no selection, hover, or search match, visible graph content is not dimmed.
2. With an active transient dim trigger and the option enabled, unrelated content is dimmed while related content remains emphasized.
3. With the same active trigger and the option disabled, unrelated content is not dimmed.

The tests must not call `setVisible` and must use the existing fixture and pixel-comparison conventions.

## Shutdown Correction

`DefaultGraphWorkspaceController.shutdown()` is idempotent and closes every owned live session using discard semantics without a user save dialog. It marks the controller closed under its monitor, rejects new opens, snapshots sessions, performs store/status/update/map/lease/scheduler cleanup in the established safe order, closes views on the EDT, unregisters sessions, waits for EDT cleanup, continues after individual failures, and reports an aggregate failure. Repeated shutdown calls do no teardown. Existing user-triggered `closeSession` save/retry/discard behavior remains unchanged.

`GraphModeExtension.close()` invokes controller shutdown before removing graph extensions/actions or clearing references, including during partial installation cleanup.

## Verification and Completion

The fresh run must use the deterministic SDD state machine, persist exact dispatch envelopes and pointer correlations, preserve the terminal predecessor and blocked successor runs, and keep all tests headless. It must run the focused presentation and lifecycle suites, the full `freeplane_plugin_graph` suite, `:freeplane:compileJava`, `git diff --check`, exact source-scope checks, and an independent Frontier final review over merge base `b4ecf2fb2baf392c62c1add6c263d78994fb0cd2` through final `HEAD`. The final review must reconcile `F-1` and carried findings `F-2` through `F-5`.
