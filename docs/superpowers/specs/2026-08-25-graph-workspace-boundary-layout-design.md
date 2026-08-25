# Graph Workspace Boundary And Dynamic Layout Design

- Date: 2026-08-25
- Task: 2026-08-10-graph-workspace
- Status: Approved implementation direction
- Amends: `docs/superpowers/specs/2026-08-10-graph-workspace-design.md`

## Problem

The current projection assigns only visual styles to enclosure depth and emits every projected enclosure to the painter. As a result, a deep mind-map hierarchy paints a curved boundary at every parent level. The current small-workspace layout also initializes all particles inside a `0.002` world-unit square, so nodes begin in a central pile and can remain visually compressed while the force solver settles.

The product requirement is two visible parent-boundary levels, selected according to the number of active mindmap registrations, with a graph surface that can grow beyond the window instead of forcing the visible graph into a small canvas.

## Boundary Visibility

Boundary depth counts projected enclosure hulls, not individual source nodes inside a collapsed unary chain. A maximal unary chain remains one `ProjectedEnclosure` and has one boundary depth.

The active mindmap count is the existing `ProjectionEngine.activeRegistrationCount` value. It includes active registrations whose snapshots are Loading, Missing, or otherwise temporarily unavailable, so availability changes do not restyle an unrelated map.

For one active mindmap:

- depth 0, the map-root enclosure, is `SUPPRESSED`;
- depth 1, the root's first projected child enclosure, is `EMPHATIC`;
- depth 2 is `SUBTLE`;
- depth 3 and deeper are `SUPPRESSED`.

For two or more active mindmaps:

- depth 0, each map-root enclosure, is `EMPHATIC`;
- depth 1 is `SUBTLE`;
- depth 2 and deeper are `SUPPRESSED`.

A map whose root is a projected graph node has no root enclosure and therefore contributes no boundary at that depth. If a root unary chain is combined into one hull, the combined root hull follows the same depth-0 rule; it is suppressed in a one-map workspace and emphatic in a multi-map workspace.

Suppressed enclosures remain in the immutable projection and retain their exact endpoint keys. This preserves relationship resolution, endpoint identity, stable diffs, and source navigation. They are excluded from visible endpoint sets, painting, labels, hit testing, traversal, accessibility, and visible graph bounds. Existing layout and geometry coverage may retain structural anchors so the projection contract does not change; no suppressed endpoint is presented as interactable.

The two visible levels are therefore a rendering/interaction policy, not destructive projection filtering.

## Dynamic Graph Surface

The graph area will wrap `GraphCanvas` in a standard `JScrollPane`. The canvas remains the world-rendering component and continues to support mouse pan, zoom, hit testing, and persisted `GraphViewport` coordinates.

The canvas preferred size is recomputed from the current visible geometry after each accepted canvas state:

1. include projected node bounds;
2. include only non-suppressed hull bounds;
3. add a fixed world margin on every side, converted using the current zoom;
4. clamp each dimension to the current graph viewport extent and the existing minimum canvas size;
5. call `revalidate()` and repaint only when the preferred size changes.

The scroll pane shows horizontal or vertical scrollbars only when the computed surface exceeds the available graph area. The scroll surface is not a second graph model. Its position is synchronized with the world viewport so scrolling exposes the corresponding world region and does not invalidate pointer conversion or saved viewport state.

When the surface grows or shrinks, the current visible world center remains anchored. Initial layout keeps the existing policy: a valid remembered viewport is restored; otherwise Fit Graph is applied after the first usable geometry. Fit Graph uses the scroll pane's visible extent rather than the full surface size. Explicit zoom, pan, drag, and scrollbar movement all preserve the same world-coordinate mapping.

The surface update must avoid oscillation during settling. It may grow immediately when new visible bounds exceed the current surface and may shrink only after a complete accepted generation, never on an intermediate stale frame. Scroll position changes caused by a state update must not emit a spurious viewport command.

## Force Layout

The layout remains force-directed through the existing private GraphStream adapter. No radial or tree layout is introduced, and relationship, containment, hierarchy, pin, map-correction, and deterministic-seed contracts remain unchanged.

The small-workspace initial spread is raised from `0.002` to the existing large-workspace scale of `50.0` world units. This removes the deterministic central pile-up while preserving stable key-based positions and the existing typed repulsion. The surface and scrollbars provide access to the resulting world bounds; Fit Graph remains available for users who want the whole graph visible at once.

Node radii, hull padding, prominence scaling, and force calibration are otherwise unchanged in this correction. The layout must not move active pins or shrink a node to avoid contact.

## Testing

Add or amend focused tests for:

- one active map: root suppressed, first-level emphatic, second-level subtle, deeper levels suppressed;
- multiple active maps: root emphatic, first-level subtle, deeper levels suppressed;
- unary root chains and branching enclosures using projected hull depth;
- suppressed enclosures remaining exact projection endpoints while absent from visible endpoint sets;
- visibility consumers excluding suppressed hulls from paint, hit testing, traversal, accessibility, and Fit Graph bounds;
- dynamic canvas preferred-size growth from visible geometry, minimum-size clamping, and scrollbar presence only when needed;
- viewport center preservation while the canvas surface changes;
- Fit Graph using the visible scroll-pane extent;
- deterministic small-workspace initial positions with a spread materially larger than the old `0.002` square;
- existing deterministic layout, pin, prominence, geometry, interaction, and window-shell regressions.

Verification will run the focused projection/layout/canvas/window suites, then the complete `:freeplane_plugin_graph:test` task with the repository's Java 21 toolchain. `git diff --check` must be clean.

## Scope

Production changes are limited to projection boundary-depth assignment, canvas visibility/surface sizing, graph-window scroll integration, and the small-workspace initial spread. No persistence schema, public graph projection identity, relationship semantics, or source-map behavior changes.
