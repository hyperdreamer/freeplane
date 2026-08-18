# Graph Workspace Batch E Design

**Task Identifier:** 2026-08-10-graph-workspace
**Batch:** E, Tasks 25-27
**Status:** Approved 2026-08-18

## Goal

Complete the Graph Workspace canvas layer in three independently reviewable task
boundaries: paint immutable graph state, translate pointer/search actions into
intents, and expose deterministic keyboard traversal and Swing accessibility.
The implementation must consume the existing immutable projection, layout, and
geometry pipeline without introducing a second presentation model or mutating
workspace/map state from Swing code.

## Scope And Boundaries

The backlog is implemented strictly as three separate deliverables. Each task
keeps its exact file allowlist, completes its own test-first cycle, stages only
those paths, and uses the task-specific commit subject from the backlog. Task
25 is complete before Task 26 starts, and Task 26 is complete before Task 27
starts.

The approved scope does not add files outside the three backlog allowlists,
change `freeplane_api`, expose GraphStream types, change source-map ownership,
or add a second sizing, search, geometry, or accessibility model. Existing
unrelated work in the checkout is preserved.

## Architecture

### Existing Immutable Inputs

The canvas consumes `CanvasState`, whose projection, layout frame, geometry,
and operational status are immutable. Projected node prominence is already
published by `GraphProjection` and applied by the existing geometry pipeline.
Canvas painting, hit testing, traversal, and accessibility use the resulting
geometry and never inspect Freeplane models or recompute prominence from raw
links.

### Task 25: Paint Viewport, Themes, And Adaptive Detail

`GraphCanvas` is the Swing owner of the latest immutable `CanvasState`,
`GraphPaintState`, `GraphViewport`, theme, and rendering policy. Its setters
are EDT-local, replace whole values, and request repaint. It does not mutate
workspace state, map state, layout state, or the immutable inputs.

`GraphViewport` validates finite center coordinates and positive finite zoom,
converts to and from persisted `Viewport`, maps world coordinates to component
coordinates, and computes whether the visible world rectangle overlaps graph
bounds. A syntactically valid but non-overlapping persisted viewport causes
`GraphCanvas.fitGraph()` to be used. Malformed persisted viewport values remain
rejected by the domain/XML layers before canvas construction.

`GraphTheme` owns the fixed colors, strokes, fonts, and contrast choices needed
by painting. `RenderingLevel` and `AdaptiveRenderingPolicy` choose deterministic
levels from projected node and edge counts. Counts above 2,000 nodes or 5,000
edges produce a warning/detail reduction but do not disable editing or remove
content from the projection.

`GraphPainter` remains package-private. It paints to a normal `JComponent` or
an offscreen `BufferedImage` in a stable order: enclosures and labels, projected
edges and direction/multiplicity cues, projected nodes, then transient states
and labels. Node and enclosure shapes, attachment points, and bounds come from
`GraphGeometry`, so prominence-scaled bounds are the same bounds later used by
hit testing and accessibility. Rendering levels suppress only optional detail;
selected, hovered, and matched labels remain visible as required by the task.

`GraphPaintState` is immutable and supports selection, hover, and search-match
sets. Each `with...` operation defensively copies its inputs and returns a new
state. Task 26 extends this state with connection preview and dim state without
changing the immutable canvas-state pipeline.

### Task 26: Hit Testing, Search, And Interaction Intents

`GraphHitIndex` indexes the currently published node and enclosure geometry.
Node hit regions are derived from the prominence-scaled geometry that painting
uses, including enlarged regions that would not be hit by an unscaled bound.
Hit results are deterministic and distinguish nodes, enclosure endpoints, and
edges where the task contract requires inspection.

`GraphInteractionController` installs and removes the canvas listeners,
maintains only transient interaction state, and emits `GraphIntent` values to
registered `GraphInteractionListener` instances. It supports source opening,
pinning and unpinning, connection preview/cancel/commit, edge inspection and
deletion requests, and selection changes. It never executes commands or mutates
the workspace store, map models, or coordinator.

Pointer zoom is centered on the pointer. Empty-canvas dragging pans. When no
endpoint is selected, unmodified arrow keys pan; selected unmodified arrows are
reserved for Task 27 traversal; Shift-arrows always perform accelerated pan.
Connection preview is transient and is cancelled by Escape. Uninstall removes
all listeners and prevents later callbacks.

`GraphSearchModel` searches the full safe label text and owning map name from
projected immutable values. It returns endpoint matches for dimming and keeps
full safe text available for hover tooltip content. It does not use transformed
or unreachable source content.

### Task 27: Keyboard Traversal And Accessible Virtual Children

`GraphTraversalOrder` provides deterministic tab order from `CanvasState` and a
nearest-endpoint query. `nearest` filters candidates to the requested
screen-space half-plane, then compares squared distance and uses
`ProjectedEndpointKey` order as the tie-breaker. It uses the current geometry
and layout positions, not collection iteration order.

`GraphInteractionController` routes selected unmodified arrows through
`GraphTraversalOrder`; with no selection it emits pan behavior. Shift-arrows
always emit accelerated pan regardless of selection. Enter and Escape retain the
source-open and transient-cancel behavior from Task 26.

`AccessibleGraphCanvas` is a package-private context implementation exposed by
`GraphCanvas` through Swing accessibility APIs. It presents virtual children
for projected nodes and enclosure endpoints instead of creating one Swing
component per endpoint. Each child exposes an appropriate role, full safe label,
owning map name, selection/hover/pin state, and available actions. Accessible
bounds use the same published geometry as painting and hit testing.

A node's accessible description appends the distinct visible outgoing-target
count after its label and owning map name only when the count is nonzero. It
never announces the prominence scale factor. Excluded content is absent, and
color alone is never the only carrier of map identity or state.

## Data Flow

1. `GraphUpdateCoordinator` publishes an immutable `CanvasState`.
2. `GraphCanvas` replaces its current state and repaints on the EDT.
3. `GraphPainter`, `GraphHitIndex`, traversal, and accessibility read the same
   immutable projection, layout, and geometry snapshot.
4. User input changes only transient `GraphPaintState` or controller state and
   produces immutable `GraphIntent` notifications.
5. Higher-level command routing, workspace history, source-map actors, and
   layout control remain outside the canvas package.

No canvas operation reads a mutable Freeplane model, writes a workspace file,
executes a map command, or exposes a GraphStream class.

## Testing Strategy

Each task follows red-green-refactor and proves the new tests fail before
production implementation is written.

- Task 25 uses focused viewport, transform, adaptive-policy, theme, and
  offscreen `BufferedImage` tests. It verifies layer order, arrows and
  multiplicity, labels, transient states, prominence-scaled bounds, exact
  rendering tiers, warning thresholds, finite viewport behavior, fit fallback,
  and editability above the engineering target.
- Task 26 uses synthetic canvas events and immutable fixtures to verify exact
  node/enclosure/edge hits, enlarged-node hit regions, selection/hover/dim,
  pointer zoom, pan/traversal key reservations, connection preview and Escape,
  intent payloads, search over full safe text, tooltip text, pin/unpin behavior,
  inspection/deletion intents, and uninstall cleanup.
- Task 27 verifies tab order, half-plane nearest selection and deterministic
  ties, selected versus unselected arrow behavior, Shift-pan behavior,
  Enter/Escape, virtual-child role/name/description/bounds/state/actions,
  safe-label confidentiality, nonzero outgoing-target descriptions, silent
  zero-reach descriptions, and absence of scale factors from accessible text.

The exact backlog mutation and staging rules remain in force for each task.
After each task's focused tests pass, the task allowlist is verified before its
commit; the next task begins from that committed state.

## Alternatives Rejected

A canvas-centric monolith is rejected because it would mix painting, hit
semantics, command routing, and accessibility and make the three backlog tasks
hard to review independently. A new presentation-model layer is rejected
because it duplicates the immutable `CanvasState`/`GraphGeometry` contract and
would require files outside the approved allowlists. The layered design keeps
one source of truth for geometry and one intent boundary for mutations.

## Verification

Before claiming Batch E complete, run the focused test suite for each task at
its task boundary, the full `freeplane_plugin_graph` test suite, and the
required build/translation checks applicable to the changed files. Confirm
that each task commit contains exactly its listed paths and that no unrelated
working-tree changes were reverted. Report any unavailable or failing
verification explicitly.
