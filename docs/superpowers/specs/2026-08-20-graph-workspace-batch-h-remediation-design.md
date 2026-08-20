# Graph Workspace Batch H Remediation Design

## Context

The Batch H Task 3 entry-point/UI commit passed its focused and full plugin gates, but the independent final review identified four defects that the original immutable Task 3 allowlist could not repair:

- the composed window creates a `GraphCanvas` without persisted map colors, so enclosure painting can fail when `GraphTheme.mapColor` has no assignment;
- the window initializes `WorkspaceSettingsPanel` from defaults and does not apply persisted display settings to rendering or viewport policy;
- `GraphModeExtension.close()` drops the controller reference without closing the controller-owned sessions and resources;
- read-only menu items can remain enabled while their delegated toolbar/map controls are disabled.

The predecessor run is terminal evidence. This design defines the explicitly expanded successor scope.

## Design

### Immutable presentation snapshot

Add a plugin-internal immutable presentation snapshot at the `GraphWorkspaceViewBinding` seam. The snapshot contains:

- persisted `DisplaySettings`;
- an ordered immutable map palette containing only `MapReferenceId` and the validated persisted color;
- the existing immutable viewport and safe map-row values remain available through the binding.

The production binding constructs the snapshot from the current `WorkspaceDocument`. Swing code never reads a store, router, map model, or Freeplane controller. The snapshot is refreshed after document-changing commands and store events, and a view listener receives immutable replacements.

`GraphWorkspaceWindowModel` uses the snapshot as the single presentation input:

- initialize `WorkspaceSettingsPanel` from persisted settings;
- install `GraphTheme.resolve(settings.canvasTheme(), palette)` before the first non-empty canvas state;
- pass `showArrowheads` into the painter and synchronize `dimUnrelatedNodes` into `GraphPaintState`;
- use `rememberViewport` in initial fitting and re-fit when the setting is disabled;
- retain the existing captured-generation and safe-data dialog contracts.

The renderer keeps its existing drawing model. Its interface gains only the display values it must actually render; no raw workspace document is exposed to the canvas.

### Deterministic extension shutdown

Make `DefaultGraphWorkspaceController` own an idempotent shutdown operation. It snapshots live sessions under the controller monitor, marks shutdown state, and for each session performs discard-close resource teardown, status publisher closure, view closure, registry removal, and handle closure. Resource teardown remains off the EDT; view/component teardown is marshalled to the EDT as required. Shutdown waits for every owned session to settle and aggregates failures without abandoning later sessions.

`GraphModeExtension.close()` calls this operation before removing the mode extension and clears the controller only after shutdown returns. User-triggered save/retry/discard close behavior remains unchanged and is not routed through extension shutdown.

### Shared menu actions

Build the File, View, and Maps menu entries from the same actions used by the toolbar/map-list controls, or from small actions that delegate to those controls while mirroring their enabled state. The model updates these actions whenever read-only or history state changes. A read-only window therefore disables mutation menu entries visibly while leaving non-mutating view/layout commands available.

## Error handling and invariants

- Missing or inconsistent palette entries are rejected at snapshot construction; a valid persisted map registration always has a color before the canvas is shown.
- Snapshot replacements are immutable and ordered deterministically.
- Shutdown is idempotent, attempts all sessions after an individual failure, and never leaves an owned session registered after successful return.
- Settings commands preserve all fields by starting from the displayed immutable settings rather than defaults.
- `Ctrl+Z` and `Ctrl+Y` remain workspace-only. Source-map undo remains separately named and independently enabled.
- Tests remain headless and never call `setVisible`.

## Verification

Write regression tests before production changes and observe them fail. The focused tests cover:

1. a real `GraphCanvas` paint with a visible enclosure and persisted map palette;
2. persisted settings initialization, settings command updates, theme/dimming/arrowhead state, and viewport behavior;
3. extension shutdown with an open fake session, including publisher/view/resource cleanup and idempotence;
4. read-only File/View/Maps menu enablement alongside toolbar/map-list controls.

Then run the focused remediation suites, the full graph plugin suite, `:freeplane:compileJava`, translation formatting/resource checks, `git diff --check`, exact scoped-file checks, and a final independent review over the complete branch.

## Scope

The successor plan explicitly allows the binding snapshot, controller shutdown, canvas/theme/painter state, extension/window/menu changes, and dedicated regression tests. It does not change `freeplane_api`, `MapView`, unrelated translations, or the graph group map-actor scope.
