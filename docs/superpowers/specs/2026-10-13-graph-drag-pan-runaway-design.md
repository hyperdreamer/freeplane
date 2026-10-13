# Graph drag-pan runaway: design

- Date: 2026-10-13
- Topic: `graph-drag-pan-runaway`
- Status: approved design (pending frontier review)
- Delivery target: `refs/heads/plugin/graph-workspace` at `9b8259bcd1cb2c8d37121e63bcac0107386fed0d`
- PM run: `pm-run-20261013-120033-112c97fc`

## Background

The Graph Workspace renders the projected map on a large `GraphCanvas` that is the
view of a `JScrollPane` (`GraphWorkspaceWindow`). Dragging an empty area of the
canvas pans the view. Users report that dragging an empty area, especially when
zoomed in, easily moves all nodes out of the visible area.

## Verified defect

A deterministic reproduction harness (archived with the PM run) models the real
AWT drag pipeline: the canvas sits in a `JScrollPane`, and each dispatched
`MouseEvent` uses the component coordinates AWT would compute for the physical
pointer position, i.e. component coordinate = viewport point + current scroll
position (confirmed against the JDK: `JViewport.getViewPosition()` returns
`-view.getLocation()`).

Measured from the fitted center with single 60-pixel empty-area drags:

| Zoom | Drag direction | Cursor travel | Content travel | Max single-event step |
| --- | --- | --- | --- | --- |
| 1.0 | right (runaway side) | 60 px | 94.5 px (1.6x) | 9 px |
| 1.0 | left | 60 px | 53.5 px (0.9x) | 1 px |
| 1.5 | right (runaway side) | 60 px | 1664 px (28x) | 56 px |
| 1.5 | left | 60 px | 29 px (0.5x) | 1 px |
| 5.0 | right (runaway side) | 60 px | 1711 px (28x) | 58 px |
| 5.0 | left | 60 px | 29 px (0.5x) | 1 px |

The runaway direction accelerates arithmetically: the content step grows by about
one pixel per mouse event, so travel is quadratic in cursor movement. The opposite
direction is damped to roughly half speed. The defect is present at zoom 1.0 and
catastrophic at zoom 1.5 and above, matching the reported zoom dependence.

## Root cause

Two existing mechanisms interact:

1. `GraphInteractionController.handleMouseDragged` measures the pan delta from
   `MouseEvent.getX()/getY()`, which are **component coordinates**. AWT recomputes
   them at dispatch time from the pointer's on-screen position and the component's
   current on-screen location. The `JViewport` moves the canvas (`view.setLocation(-scrollX, -scrollY)`)
   whenever the scroll position changes, so a stationary pointer acquires a
   different component coordinate as the view scrolls under it.

2. `GraphCanvas.panByPixels` changes the viewport center, and `resizeSurface`
   recomputes the scrollable surface anchored to that center. The surface size
   therefore changes by approximately twice the pan distance, and `resizeSurface`
   recenters the scroll position by `(newWidth - oldWidth) / 2` to keep the nominal
   center equal to the visible center.

When the recentering scroll direction aligns with the drag, the scroll jump is
included in the next component-coordinate delta, which is then panned again: a
positive feedback loop that integrates the cursor step each event. When the
directions oppose, the jump cancels the next delta and the pan runs at half speed.
`panByPixels` itself (`center - dx/zoom`) and `resizeSurface`'s centering invariant
are both correct in isolation; only the delta measurement space is wrong.

## Goals

1. An empty-area drag moves the graph so that the content under the pointer stays
   under the pointer: 1:1 tracking in both directions at every zoom, with no
   per-event amplification.
2. No other behavior change: same scrollable surface, scrollbars, zoom anchoring,
   fit/reset, arrow-key panning, pin/connect gestures, and visual appearance.

## Non-goals

- Reworking the scrollable surface or removing scrollbars.
- Adding pan bounds or clamping behavior.
- Changing wheel zoom, toolbar zoom, `fitGraph`, or `resetZoom`.
- Any visual redesign (no mockups apply: there is no new visual state, layout, or
  styling; acceptance is geometric and measurable).

## Design

### Interface change

Add one package-private method to `GraphCanvas`, named in the existing vocabulary
(`worldAt`, `visibleViewport`, `panByPixels`):

```java
/**
 * Returns the component point expressed in viewport coordinates, which are
 * independent of the view's current scroll position. AWT computes
 * MouseEvent component coordinates against the component's current on-screen
 * location, so this space is what keeps gesture deltas equal to physical
 * pointer movement while the viewport scrolls the canvas under the pointer.
 * Returns the given point unchanged when the canvas has no JViewport ancestor.
 */
Point viewportPoint(Point componentPoint)
```

Conversion: `viewportPoint = componentPoint - viewport.getViewPosition()`, using
the nearest `JViewport` ancestor. Both operands are integer pixel coordinates, so
`java.awt.Point` is the right carrier; no floating point is introduced.

The scroll mechanics stay private to `GraphCanvas`: callers ask for a stable
pointer coordinate, they never manipulate the view position.

### Controller change

`GraphInteractionController` computes the pointer once per event through
`canvas.viewportPoint(event.getPoint())` and uses it in every place that currently
reads `event.getX()/getY()` for gesture geometry:

- `beginSelect` and `beginConnect` store the press point in the drag state.
- `handleMouseDragged` derives `dx`/`dy`, the drag-threshold distance, and the
  `lastX/lastY` update from the stable point.
- `finishSelect` derives its click-versus-drag threshold distance from the stable
  point.

`DragState` field types stay `int`. `panByPixels` and all viewport/surface code are
unchanged.

### Data flow

```
pointer event
  -> AWT component coordinates (shift with the canvas under the pointer)
  -> canvas.viewportPoint(...)             (removes the scroll component)
  -> cursor delta = physical pointer movement
  -> canvas.panByPixels(delta)
  -> viewport center shift
  -> resizeSurface(surface size + scroll recentering)
  -> next event's component coordinates shift, but not the stable coordinates
```

### Preserved invariants

1. Cursor deltas equal physical pointer movement regardless of scrolling.
2. `resizeSurface` still recenters the viewport so the nominal center equals the
   visible center; this is what viewport persistence through `visibleViewport()`
   relies on, and it keeps the nominal-center mapping exact.
3. Without a `JViewport` ancestor, `viewportPoint` is the identity, so behavior is
   exactly as before (existing bare-canvas tests).

Deliberate boundary: `worldAt(event.getPoint())` (hit testing, pin anchoring) and
`zoomAround(event.getPoint(), factor)` stay in raw component space. The scroll
offset cancels algebraically in the nominal-center mapping
(`world = center + (component - width/2) / zoom` with
`component = physical pointer + viewPosition`), so converting them would break
click hit-testing once the user moves the scrollbars. A regression test must click
an endpoint in a scrolled viewport to pin this boundary.

## Edge cases and error handling

- **No viewport** (bare canvas in unit tests, or an unparented canvas): identity
  mapping; no exception.
- **Pointer leaves the canvas during a drag**: viewport coordinates may be
  negative or exceed the component size; they remain valid integers and the delta
  is still the physical movement.
- **Multiple nested viewports**: the nearest ancestor defines the space.
- **Programmatic scrolls during a gesture** (e.g. `resizeSurface` from a settled
  canvas state): produce zero pointer delta when the pointer does not move; this is
  the mechanism pin test.
- No new failure modes, no fallback paths, no compatibility shims.

## Test strategy

1. **Regression test (falsifiable).** Host the canvas in a `JScrollPane` at a zoom
   where the surface exceeds the viewport extent. Dispatch press plus successive
   one-pixel `MOUSE_DRAGGED` events whose component coordinates follow the real
   AWT mapping (`component = viewport point + scroll position`), for both drag
   directions. Assert:
   - total content displacement is at least cursor displacement minus the 3 px
     click-versus-drag dead zone (`DRAG_THRESHOLD_PIXELS`) and at most cursor
     displacement (from the first post-threshold event, tracking is 1:1 within one
     pixel);
   - no single event moves the content by more than 1.5 px (cursor step plus
     integer rounding);
   - the viewport center changes monotonically in the drag direction;
   - one vertical or diagonal drag at one zoom level uses the same assertions, so
     a y-axis sign error cannot pass horizontal-only coverage.
   The content metric is the surface position of a fixed world point:
   `W/2 + (world - center) * zoom - viewPosition` (not `visibleViewport()`, which
   is derived from the scroll position). Scrollbar appearance or disappearance can
   change the viewport extent mid-drag, so the assertions must not assume a
   constant extent; the zoom 1.0 case that crosses the horizontal scrollbar
   threshold must be included.
   On current code this test fails (1664 px content travel for 60 px cursor travel
   in the runaway direction), which will be demonstrated as the red phase.
2. **Mechanism pin test.** After the drag threshold has been crossed (so that a pan
   would fire), programmatically scroll the viewport by a known amount while the
   physical pointer stays fixed, then dispatch the next drag event with its
   component coordinates rebuilt from the unchanged pointer position plus the new
   view position. Assert the nominal `canvas.viewport()` center is unchanged: the
   injected scroll must not produce any pan, independently of displacement
   tolerances. Asserting `visibleViewport()` would be wrong here because the test
   itself scrolls the viewport.
3. **Unit test for `viewportPoint`.** Offset applied when a `JViewport` ancestor
   exists; identity otherwise.
4. **Scrolled-viewport boundary test.** With the viewport scrolled away from the
   recentered position, clicking a projected node still hits its endpoint and
   double-click still emits the open intent, pinning that `worldAt` and
   `zoomAround` remain component-space.
5. **Existing suite.** `GraphInteractionControllerShould`, `GraphCanvasPaintShould`,
   `AccessibleGraphCanvasShould`, and the full `freeplane_plugin_graph` test suite
   must stay green.
6. **Real-application acceptance evidence (audit).** Under Xvfb, perform a Robot
   drag in an empty area on a real Graph Workspace window at a zoom above 1.0 and
   measure the content displacement against the pointer displacement from
   before/after screenshots; the two must match within a few pixels. This is audit
   evidence, not a shipped artifact.

## Acceptance criteria

- The regression and mechanism tests pass, and both fail against the pre-fix
  implementation.
- Empty-area drag tracks the cursor 1:1 in both directions at zoom 1.0, 1.5, and
  5.0 in the harness model, allowing for the intentional 3 px click-versus-drag
  dead zone at gesture start and one pixel of integer rounding per event.
- Full plugin test suite green.
- Real-application drag measurement matches pointer travel.
- No change to files or behavior outside the two involved classes and tests.
