# Graph Workspace: empty-area drag pan runaway — technical specification

Task identifier: `2026-10-13-graph-drag-pan-runaway`

Source design: `docs/superpowers/specs/2026-10-13-graph-drag-pan-runaway-design.md`
(approved; commit `06110ced55`). This specification implements the approved design; it does not
extend or contradict it. All line anchors below refer to the integration worktree at
`06110ced55` unless stated otherwise.

## 1. Scope

### 1.1 Production change

The whole production change is confined to two classes:

| File | Change |
| --- | --- |
| `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphCanvas.java` | Add one package-private method, `Point viewportPoint(Point)`, immediately after `worldAt` (current lines 231–234) and before `updateTooltip` (current line 236). Nothing else changes. |
| `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphInteractionController.java` | Add `import java.awt.Point;` and change exactly four methods — `handleMouseDragged` (lines 292–315), `beginSelect` (lines 442–448), `beginConnect` (lines 450–461), `finishSelect` (lines 463–475) — as specified in §3. Nothing else changes. |

The only test file that changes is
`freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphInteractionControllerShould.java`
(§6 and §7). `GraphCanvasPaintShould.java` and `AccessibleGraphCanvasShould.java` do not change
and must keep passing.

### 1.2 Goals (restated exactly from the approved design)

1. An empty-area drag moves the graph so that the content under the pointer stays under the
   pointer: 1:1 tracking in both directions at every zoom, with no per-event amplification.
2. No other behavior change: same scrollable surface, scrollbars, zoom anchoring, fit/reset,
   arrow-key panning, pin/connect gestures, and visual appearance.

### 1.3 Non-goals (restated exactly from the approved design)

- Reworking the scrollable surface or removing scrollbars.
- Adding pan bounds or clamping behavior.
- Changing wheel zoom, toolbar zoom, `fitGraph`, or `resetZoom`.
- Any visual redesign (no mockups apply: there is no new visual state, layout, or styling;
  acceptance is geometric and measurable).

### 1.4 One active path

`viewportPoint` is a pure addition; it is the single coordinate source for gesture geometry in
`GraphInteractionController` after the change. No compatibility shim, no second delta path, no
fallback branch, and no retained old computation is introduced. `panByPixels` and every
viewport/surface method stay exactly as shipped. `worldAt` and `zoomAround` keep receiving raw
component coordinates (§2.5).

## 2. Interface contract: `GraphCanvas.viewportPoint(Point)`

### 2.1 Declaration

Package-private (no access modifier), non-static, instance method on `GraphCanvas`, placed
between `worldAt` and `updateTooltip`. Exact declaration and documentation:

```java
    /**
     * Returns the component point expressed in viewport coordinates, which are
     * independent of the view's current scroll position. AWT computes
     * MouseEvent component coordinates against the component's current on-screen
     * location, so this space is what keeps gesture deltas equal to physical
     * pointer movement while the viewport scrolls the canvas under the pointer.
     * Returns the given point unchanged when the canvas has no JViewport ancestor.
     */
    Point viewportPoint(final Point componentPoint) {
        final Point value = Objects.requireNonNull(componentPoint, "componentPoint");
        final JViewport scrollViewport = containingViewport();
        if (scrollViewport == null) {
            return value;
        }
        final Point viewPosition = scrollViewport.getViewPosition();
        return new Point(value.x - viewPosition.x, value.y - viewPosition.y);
    }
```

`javax.swing.JViewport` and `java.awt.Point` are already imported by `GraphCanvas`, so the
class's import block does not change.

### 2.2 Conversion semantics and the JViewport sign

- The viewport is the nearest `JViewport` ancestor, resolved through the existing private
  `containingViewport()` helper (`SwingUtilities.getAncestorOfClass(JViewport.class, this)`).
- `JViewport.getViewPosition()` returns the logical scroll position `P`. The JDK places the view
  at `(-P.x, -P.y)` (`view.setLocation(-P.x, -P.y)`), so a physical pointer at viewport
  coordinate `v` has component coordinate `component = v + P`, i.e. `v = component - P`. The
  conversion is therefore the componentwise subtraction

  ```
  viewportPoint.x = componentPoint.x - viewPosition.x
  viewportPoint.y = componentPoint.y - viewPosition.y
  ```

  using the `JViewport` sign as shipped: `getViewPosition()` returns the scroll position, not the
  view location. This was confirmed against the JDK and against the archived reproduction
  harness (`PanRunawayRepro.java`, corrected AWT coordinate model).
- Both operands are integer pixel coordinates; the subtraction is exact `int` arithmetic. No
  floating point is introduced. No rounding, no clamping, and no `double` conversion happens in
  this method.
- AWT recomputes `MouseEvent` component coordinates at dispatch time from the pointer's on-screen
  position and the component's current on-screen location, so while a `JViewport` scrolls the
  canvas under a stationary pointer the component coordinate changes but the viewport coordinate
  does not.

### 2.3 Identity without a viewport

When `containingViewport()` returns `null`, `viewportPoint` returns the argument instance
unchanged (`return value;`): the same object, not a copy, and no exception. This preserves the
bare-canvas behavior of all existing tests exactly.

### 2.4 Null handling

`viewportPoint(null)` throws `NullPointerException` with message `componentPoint`, produced by
`Objects.requireNonNull(componentPoint, "componentPoint")` before the ancestor lookup. This
matches the existing `GraphCanvas.worldAt` pattern (`Objects.requireNonNull(point, "point")`).

### 2.5 Callers that must NOT use it

`worldAt(Point)` and `zoomAround(Point2D, double)` stay in raw component space. The scroll offset
cancels algebraically in the nominal-center mapping:

```
worldAt(component) = center + (component - size/2) / zoom
```

With `component = physical + viewPosition`, the world point under a physical viewport pointer `v`
is `center + (v + viewPosition - size/2) / zoom = worldAt(v + viewPosition)`. Passing
`viewportPoint(component) = v` to `worldAt` would subtract `viewPosition` a second time and move
the hit point by `-viewPosition / zoom`; once the user moves the scrollbars, clicks would miss
their targets. `zoomAround` has the same cancellation: it must anchor at `v + viewPosition` so
the physical pointer stays over the same world point while zooming.

After the change, the following call sites must still read `event.getPoint()` raw (current line
numbers; the counts are the §8.2 gates):

| Method | Call | Count |
| --- | --- | --- |
| `handleMousePressed` | `canvas.worldAt(event.getPoint())` (line 221) | 1 |
| `handleMouseClicked` | `canvas.worldAt(event.getPoint())` (line 277) | 1 |
| `handleMouseDragged` | `canvas.worldAt(event.getPoint())` (line 305) | 1 |
| `handleMouseMoved` | `canvas.worldAt(event.getPoint())` (line 326) | 1 |
| `handleMouseWheel` | `canvas.zoomAround(event.getPoint(), factor)` (line 360) | 1 |
| `finishSelect` | `canvas.worldAt(event.getPoint())` (line 473) | 1 |
| `finishConnect` | `canvas.worldAt(event.getPoint())` (line 481) | 1 |
| `handleContext` | `canvas.worldAt(event.getPoint())` (line 500) | 1 |

In particular, in `handleMouseDragged` the pan delta (`dx`, `dy`) and the drag-threshold
distance come from `viewportPoint`, while the connection-preview world point
(`canvas.worldAt(event.getPoint())`) stays component space for the same reason.

### 2.6 Value domain, allocation, concurrency

- Inputs and outputs are `java.awt.Point` objects whose `x`/`y` are `int` pixel coordinates.
  Values are screen-bounded as produced by AWT or by programmatic scrolls; the method applies no
  range checks, no saturation, and no clamping (plain Java `int` subtraction).
- A new `Point` is allocated only when a viewport exists. The identity path allocates nothing.
- The method is a pure read of `componentPoint` and the current `JViewport.getViewPosition()`; it
  performs no Swing mutation and never touches the `viewport` field, the scroll position, or the
  surface. It may be called on any thread that may read Swing state, consistent with the
  controller's existing dispatch model.

## 3. Controller changes

### 3.1 Imports

Add one import to `GraphInteractionController.java` after `java.awt.KeyboardFocusManager`
(current lines 3–4):

```java
import java.awt.Point;
```

No other import changes.

### 3.2 `handleMouseDragged` (current lines 292–315)

Before:

```java
        final DragState current = drag;
        final double dx = event.getX() - current.lastX;
        final double dy = event.getY() - current.lastY;
        if (Math.hypot(event.getX() - current.startX, event.getY() - current.startY)
                >= DRAG_THRESHOLD_PIXELS) {
            current.moved = true;
        }
        current.lastX = event.getX();
        current.lastY = event.getY();
        final LayoutPoint world = canvas.worldAt(event.getPoint());
```

After:

```java
        final DragState current = drag;
        final Point pointer = canvas.viewportPoint(event.getPoint());
        final double dx = pointer.x - current.lastX;
        final double dy = pointer.y - current.lastY;
        if (Math.hypot(pointer.x - current.startX, pointer.y - current.startY)
                >= DRAG_THRESHOLD_PIXELS) {
            current.moved = true;
        }
        current.lastX = pointer.x;
        current.lastY = pointer.y;
        final LayoutPoint world = canvas.worldAt(event.getPoint());
```

Everything after `world` is unchanged: the connect branch (`updatePreview(world)`) and the pan
condition (`current.endpoint == null && current.moved` → `canvas.panByPixels(dx, dy)`).

### 3.3 `beginSelect` (current lines 442–448)

Before:

```java
        final ProjectedEndpointKey value = endpoint.orElse(null);
        final boolean pinCandidate = value != null && value.isNode()
            && !PinProjection.isPinned(state.projection(), value.node().get());
        drag = DragState.select(value, pinCandidate, event.getX(), event.getY());
```

After:

```java
        final ProjectedEndpointKey value = endpoint.orElse(null);
        final boolean pinCandidate = value != null && value.isNode()
            && !PinProjection.isPinned(state.projection(), value.node().get());
        final Point pointer = canvas.viewportPoint(event.getPoint());
        drag = DragState.select(value, pinCandidate, pointer.x, pointer.y);
```

### 3.4 `beginConnect` (current lines 450–461)

Before:

```java
        previewSource = endpoint.get();
        previewFrom = previewStart(state, previewSource, world);
        drag = DragState.connect(previewSource, event.getX(), event.getY());
        updatePreview(world);
```

After:

```java
        previewSource = endpoint.get();
        previewFrom = previewStart(state, previewSource, world);
        final Point pointer = canvas.viewportPoint(event.getPoint());
        drag = DragState.connect(previewSource, pointer.x, pointer.y);
        updatePreview(world);
```

The early `return` for a non-node endpoint and the rest of the method are unchanged.

### 3.5 `finishSelect` (current lines 463–475)

Before:

```java
        final boolean moved = current.moved
            || Math.hypot(event.getX() - current.startX, event.getY() - current.startY)
                >= DRAG_THRESHOLD_PIXELS;
```

After:

```java
        final Point pointer = canvas.viewportPoint(event.getPoint());
        final boolean moved = current.moved
            || Math.hypot(pointer.x - current.startX, pointer.y - current.startY)
                >= DRAG_THRESHOLD_PIXELS;
```

The pin-candidate guard, the `!moved || current.endpoint == null || !current.endpoint.isNode()`
guard, and the emitted pin world point (`canvas.worldAt(event.getPoint())`) are unchanged.

### 3.6 `DragState` and resulting delta semantics

`DragState` (current lines 694–724) does not change: no field is added, removed, renamed, or
retyped; the constructor and both factories keep their signatures. `startX`, `startY`, `lastX`,
`lastY` stay `int` and now hold **viewport-space pixel coordinates**; `endpoint`,
`pinCandidate`, `connect`, and `moved` keep their current meanings.

Consequences:

- `dx = pointer.x - current.lastX` and `dy = pointer.y - current.lastY` are the physical pointer
  step between two events, independent of any scroll that happens in between. `lastX`/`lastY` are
  updated from the same stable point, so a programmatic scroll with a stationary pointer yields
  `dx = dy = 0`.
- The threshold test `Math.hypot(pointer.x - current.startX, pointer.y - current.startY) >=
  DRAG_THRESHOLD_PIXELS` measures physical pixel movement; the 3 px dead zone is unchanged.
- The release-time click-versus-drag test in `finishSelect` measures the same physical distance.
- `panByPixels(dx, dy)` therefore receives the physical step each event and moves the viewport
  center by `-dx/zoom, -dy/zoom`.

### 3.7 Explicitly unchanged

The following are not touched, not renamed, and not reordered:

- `GraphCanvas.panByPixels(double, double)` and its body.
- `GraphCanvas.resizeSurface(CanvasState)`, `surfacePixels(...)`, `centerScrollView()`,
  `clampedPosition(...)`, `scheduleViewportClamp(...)`, `beginViewportPositioning()`,
  `endViewportPositioning()`, and all surface-sizing constants (`MIN_SURFACE_WIDTH`,
  `MIN_SURFACE_HEIGHT`, `WORLD_MARGIN`).
- `GraphCanvas.visibleViewport()`, `GraphCanvas.viewport()`, `GraphCanvas.setViewport(...)`,
  `fitGraph()`, `resetZoom()`.
- `GraphInteractionController.handleMouseWheel`, `panForArrow`, `handleMousePressed`,
  `handleMouseClicked`, `handleMouseMoved`, `handleMouseExited`, `handleContext`,
  `finishConnect`, `previewStart`, and every `worldAt`/`zoomAround` call site listed in §2.5.
- `DRAG_THRESHOLD_PIXELS = 3.0`, `EDGE_TOLERANCE_PIXELS`, the arrow/wheel constants, and every
  other constant.

## 4. Data structures and value domains

- `GraphCanvas.viewportPoint` carrier: `java.awt.Point` with `int x`, `int y`; input and result
  are non-null (null rejected, §2.4). Result is a fresh `Point` with a viewport, the identical
  instance without one.
- `DragState.startX/startY/lastX/lastY`: `int`, viewport-space pixels. `dx`/`dy`:
  `double` locals holding integral physical pixel steps.
- `GraphViewport`: `centerX`, `centerY`, `zoom` remain `double`; the conversion never touches
  them.
- `JViewport.getViewPosition()`: always non-null (Swing contract; default `(0, 0)`).
- No collections, no new fields, no new types.

## 5. Error behavior

- `viewportPoint(null)` → `NullPointerException` with message containing `componentPoint`;
  nothing else throws.
- No viewport ancestor → identity, same instance, no exception.
- Pointer leaves the canvas during a drag → viewport coordinates may be negative or exceed the
  component size; they remain valid `int` values and the delta is still the physical movement.
- Multiple nested viewports → the nearest ancestor defines the space (existing
  `containingViewport()` behavior).
- Programmatic scroll during a gesture (e.g. `resizeSurface` recentring after a settled canvas
  state) → zero pointer delta when the pointer does not move.
- No fallback path, no compatibility shim, no new exception type, no new log message.

## 6. Test fixtures

All additions go into
`freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphInteractionControllerShould.java`.
Conventions stay JUnit 4 (`org.junit.Test`) and AssertJ. Existing helpers are reused:
`dispatch(GraphCanvas, AWTEvent)` (line 620), `clickAt(GraphCanvas, int, int, int, int, int)`
(line 638), `wheel(GraphCanvas, int, int, int)` (line 668), and `RecordingListener` (line 678).

### 6.1 Imports to add

```java
import static org.assertj.core.api.Assertions.within;

import java.awt.Point;
import javax.swing.JScrollPane;
import org.freeplane.plugin.graph.geometry.HullGeometry;
import org.freeplane.plugin.graph.projection.ProjectedEnclosure;
```

Everything else used by the new code (`ArrayList`, `Collections`, `LinkedHashMap`, `List`, `Map`,
`Optional`, `UUID`, `Dimension`, `Point2D`, `MouseEvent`, `MouseWheelEvent`, `CanvasState`,
`OperationalStatus`, `GraphGeometry`, `LayoutPoint`, `LayoutPositions`, `NodeGeometry`,
`LayoutFrame`, `EnclosureHullKey`, `GraphProjection`, `ProjectedEdge`, `ProjectedNode`,
`ProjectedNodeKey`, `PinProjection`, `SafeNodeLabel`, `SourceNodeKey`, `MapReferenceId`,
`NodeReference`, `PersistedNodeId`) is already imported.

### 6.2 Headless `JScrollPane` hosting

Create the canvas already inside the scroll pane, lay the pane out, then publish state and
viewport, then lay out again. Exact sequence (validated; the first layout is required so that
`resizeSurface` sees a real, non-zero extent and the final layout resolves a real view position):

```java
    private static ScrolledFixture scrolledFixture(final CanvasState state, final double zoom,
            final double centerX, final double centerY, final int scrollWidth, final int scrollHeight) {
        final GraphCanvas canvas = new GraphCanvas();
        final JScrollPane scrollPane = new JScrollPane(canvas);
        final RecordingListener listener = new RecordingListener();
        final GraphInteractionController controller = new GraphInteractionController(listener);
        controller.install(canvas);
        scrollPane.setSize(scrollWidth, scrollHeight);
        scrollPane.doLayout();
        scrollPane.getViewport().doLayout();
        canvas.setCanvasState(state);
        canvas.setViewport(GraphViewport.of(centerX, centerY, zoom));
        scrollPane.doLayout();
        scrollPane.getViewport().doLayout();
        return new ScrolledFixture(canvas, scrollPane, controller, listener);
    }

    private static final class ScrolledFixture {
        private final GraphCanvas canvas;
        private final JScrollPane scrollPane;
        private final GraphInteractionController controller;
        private final RecordingListener listener;

        private ScrolledFixture(final GraphCanvas canvas, final JScrollPane scrollPane,
                final GraphInteractionController controller, final RecordingListener listener) {
            this.canvas = canvas;
            this.scrollPane = scrollPane;
            this.controller = controller;
            this.listener = listener;
        }
    }
```

Every test uninstalls the controller in a `finally` block. No `JFrame`, no `setVisible`, and no
real display is used: `setSize`/`doLayout`/`JViewport.doLayout` are sufficient to obtain an
extent and a scrollable view (validated headlessly).

### 6.3 Pan fixture state (reuses the `Fixture` construction pattern)

`panFixtureState()` follows the same construction order as `Fixture.create()` in the same file:
`MapReferenceId` → `ProjectedNodeKey`/`ProjectedNode` lists → `GraphProjection.projected` →
`GraphGeometry.of` → `LayoutFrame.of(LayoutPositions.of(...))` → `CanvasState.of(...,
OperationalStatus.IDLE)`. Six nodes, radius `20.0`, map id
`00000000-0000-0000-0000-0000000000ff`:

```java
    private static final String PAN_MAP_ID = "00000000-0000-0000-0000-0000000000ff";
    private static final double PAN_NODE_RADIUS = 20.0;
    private static final double[][] PAN_NODE_CENTERS = {
        { -300.0, -80.0 }, { -150.0, 60.0 }, { 0.0, -60.0 },
        { 120.0, 80.0 }, { 300.0, 0.0 }, { 60.0, 140.0 }
    };

    private static CanvasState panFixtureState() {
        final MapReferenceId map = MapReferenceId.of(UUID.fromString(PAN_MAP_ID));
        final List<ProjectedNode> nodes = new ArrayList<ProjectedNode>();
        final Map<ProjectedNodeKey, NodeGeometry> geometry =
            new LinkedHashMap<ProjectedNodeKey, NodeGeometry>();
        final Map<ProjectedNodeKey, LayoutPoint> positions =
            new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        for (int index = 0; index < PAN_NODE_CENTERS.length; index++) {
            final NodeReference reference = NodeReference.of(map,
                PersistedNodeId.of("node-" + index));
            final ProjectedNodeKey key = ProjectedNodeKey.of(SourceNodeKey.persisted(reference));
            final LayoutPoint point =
                LayoutPoint.of(PAN_NODE_CENTERS[index][0], PAN_NODE_CENTERS[index][1]);
            nodes.add(ProjectedNode.of(key, SafeNodeLabel.of("Node " + index, "Node " + index),
                "Map", false));
            geometry.put(key, NodeGeometry.of(point, PAN_NODE_RADIUS));
            positions.put(key, point);
        }
        final GraphProjection projection = GraphProjection.projected(1L, nodes,
            Collections.<ProjectedEnclosure>emptyList(), Collections.<ProjectedEdge>emptyList(),
            Collections.emptyList(), Collections.<PinProjection>emptyList());
        final GraphGeometry graphGeometry = GraphGeometry.of(geometry,
            Collections.<EnclosureHullKey, HullGeometry>emptyMap());
        final LayoutFrame frame = LayoutFrame.of(1L, LayoutPositions.of(positions,
            Collections.<EnclosureHullKey, LayoutPoint>emptyMap()), false, 0);
        return CanvasState.of(1L, projection, frame, graphGeometry, OperationalStatus.IDLE);
    }

    private static ScrolledFixture scrolledPanFixture(final double zoom) {
        return scrolledFixture(panFixtureState(), zoom, 0.0, 0.0, 900, 700);
    }
```

This state is the archived `PanRunawayRepro.graphState()` used to measure the defect, so the
recorded pre-fix numbers of §7.11 reproduce exactly.

### 6.4 Event dispatch: `component = viewport point + view position`

A test carries a physical pointer position expressed in viewport coordinates. At dispatch time
the component coordinate is rebuilt from the *current* view position, exactly as AWT does:

```java
    private static MouseEvent gestureEvent(final GraphCanvas canvas, final JScrollPane scrollPane,
            final int id, final double viewportX, final double viewportY) {
        final Point viewPosition = scrollPane.getViewport().getViewPosition();
        final int componentX = (int) Math.round(viewportX + viewPosition.x);
        final int componentY = (int) Math.round(viewportY + viewPosition.y);
        return new MouseEvent(canvas, id, System.currentTimeMillis(), 0, componentX, componentY, 1,
            false, MouseEvent.BUTTON1);
    }
```

Drags are dispatched as one `MOUSE_PRESSED` followed by `events` one-pixel `MOUSE_DRAGGED`
events and one `MOUSE_RELEASED`. Each event is dispatched with `canvas.dispatchEvent(event)`
through the existing `dispatch` helper.

### 6.5 Content metric

The content metric is the viewport position of the fixed world origin `(0, 0)`:

```
contentX = W / 2 + (0 - centerX) * zoom - viewPosition.x
contentY = H / 2 + (0 - centerY) * zoom - viewPosition.y
```

where `W`/`H` are `canvas.getWidth()`/`canvas.getHeight()`, `centerX`/`centerY`/`zoom` are
`canvas.viewport().centerX()/centerY()/zoom()`, and `viewPosition` is
`scrollPane.getViewport().getViewPosition()`. It is intentionally **not** `visibleViewport()`,
which is derived from the scroll position; the surface size and the view position both change
during the drag, and the assertions must not assume a constant extent.

```java
    private static double contentX(final GraphCanvas canvas, final JScrollPane scrollPane) {
        return canvas.getWidth() / 2.0
            + (0.0 - canvas.viewport().centerX()) * canvas.viewport().zoom()
            - scrollPane.getViewport().getViewPosition().x;
    }

    private static double contentY(final GraphCanvas canvas, final JScrollPane scrollPane) {
        return canvas.getHeight() / 2.0
            + (0.0 - canvas.viewport().centerY()) * canvas.viewport().zoom()
            - scrollPane.getViewport().getViewPosition().y;
    }
```

### 6.6 Drag-threshold dead zone

`DRAG_THRESHOLD_PIXELS` is `3.0`. With one-pixel drag steps, events 1 and 2 never pan (threshold
distance 1 and 2); the first pan fires on event 3 with `dx = 1`. `lastX`/`lastY` are updated on
every event, so the first two pixels are never recovered. For a 60-event one-pixel drag the
expected total travel is therefore 58 px, and the accepted band is `[60 - 3, 60] = [57, 60]` per
axis (absolute value; direction sign checked separately). A `1.5 + 1e-6` per-event cap covers
the one-pixel step plus the surface-recentring integer rounding plus IEEE accumulation noise.
For the diagonal y axis at zoom 1.5 the accepted total band is widened to `[57, 69]` to absorb a
measured 10 px of `resizeSurface` integer-recentring quantization; the per-event cap is
unchanged (§7.5).

## 7. Test cases

New members are added next to the existing `panWhenDraggingEmptyCanvas` test and helpers;
nothing existing is renamed or deleted.

### 7.0 Shared helpers

```java
    private static final double PAN_THRESHOLD_DEAD_ZONE_PIXELS = 3.0;
    private static final double PAN_MAX_CONTENT_STEP_PIXELS = 1.5 + 1e-6;

    private static GestureResult dragGesture(final ScrolledFixture fixture, final double directionX,
            final double directionY, final int events, final double fingerX0, final double fingerY0) {
        final GraphCanvas canvas = fixture.canvas;
        final JScrollPane scrollPane = fixture.scrollPane;
        dispatch(canvas, gestureEvent(canvas, scrollPane, MouseEvent.MOUSE_PRESSED, fingerX0, fingerY0));
        final double startX = contentX(canvas, scrollPane);
        final double startY = contentY(canvas, scrollPane);
        double previousX = startX;
        double previousY = startY;
        double maxStepX = 0.0;
        double maxStepY = 0.0;
        final List<Double> centersX = new ArrayList<Double>();
        final List<Double> centersY = new ArrayList<Double>();
        double fingerX = fingerX0;
        double fingerY = fingerY0;
        for (int index = 0; index < events; index++) {
            fingerX += directionX;
            fingerY += directionY;
            dispatch(canvas, gestureEvent(canvas, scrollPane, MouseEvent.MOUSE_DRAGGED, fingerX, fingerY));
            final double currentX = contentX(canvas, scrollPane);
            final double currentY = contentY(canvas, scrollPane);
            maxStepX = Math.max(maxStepX, Math.abs(currentX - previousX));
            maxStepY = Math.max(maxStepY, Math.abs(currentY - previousY));
            previousX = currentX;
            previousY = currentY;
            centersX.add(canvas.viewport().centerX());
            centersY.add(canvas.viewport().centerY());
        }
        dispatch(canvas, gestureEvent(canvas, scrollPane, MouseEvent.MOUSE_RELEASED, fingerX, fingerY));
        return new GestureResult(previousX - startX, previousY - startY, maxStepX, maxStepY,
            centersX, centersY);
    }

    private static final class GestureResult {
        private final double contentTravelX;
        private final double contentTravelY;
        private final double maxStepX;
        private final double maxStepY;
        private final List<Double> centersX;
        private final List<Double> centersY;

        private GestureResult(final double contentTravelX, final double contentTravelY,
                final double maxStepX, final double maxStepY, final List<Double> centersX,
                final List<Double> centersY) {
            this.contentTravelX = contentTravelX;
            this.contentTravelY = contentTravelY;
            this.maxStepX = maxStepX;
            this.maxStepY = maxStepY;
            this.centersX = centersX;
            this.centersY = centersY;
        }
    }

    private static void assertTracksCursor(final GestureResult result, final double directionX,
            final double directionY, final int events, final String description) {
        assertAxisTracks(result.contentTravelX, directionX, events, description + " x");
        assertAxisTracks(result.contentTravelY, directionY, events, description + " y");
        assertThat(result.maxStepX).as(description + " max x step")
            .isLessThanOrEqualTo(PAN_MAX_CONTENT_STEP_PIXELS);
        assertThat(result.maxStepY).as(description + " max y step")
            .isLessThanOrEqualTo(PAN_MAX_CONTENT_STEP_PIXELS);
        assertMonotonicCenter(result.centersX, directionX, description + " x");
        assertMonotonicCenter(result.centersY, directionY, description + " y");
    }

    private static void assertAxisTracks(final double travel, final double direction,
            final int events, final String description) {
        if (direction == 0.0) {
            assertThat(travel).as(description).isEqualTo(0.0);
            return;
        }
        assertThat(travel * direction).as(description)
            .isBetween(events - PAN_THRESHOLD_DEAD_ZONE_PIXELS, (double) events);
    }

    private static void assertMonotonicCenter(final List<Double> centers, final double direction,
            final String description) {
        for (int index = 1; index < centers.size(); index++) {
            if (direction > 0.0) {
                assertThat(centers.get(index)).as(description)
                    .isLessThanOrEqualTo(centers.get(index - 1));
            }
            else if (direction < 0.0) {
                assertThat(centers.get(index)).as(description)
                    .isGreaterThanOrEqualTo(centers.get(index - 1));
            }
            else {
                assertThat(centers.get(index)).as(description).isEqualTo(centers.get(index - 1));
            }
        }
        if (direction != 0.0) {
            assertThat(centers.get(centers.size() - 1)).as(description)
                .isNotEqualTo(centers.get(0));
        }
    }
```

`assertAxisTracks` tests `travel * direction` (non-negative when the sign is right) against the
inclusive band `[events - 3, events]`; the separate sign factor makes a sign inversion fail the
band. `assertMonotonicCenter` requires non-increasing centers for a positive direction
(right/down drags decrease the nominal center), non-decreasing for a negative direction, and
exact constancy for a zero direction; the last center must differ from the first.

### 7.1 Horizontal runaway-direction regression at zoom 1.5 (primary)

```java
    @Test
    public void trackTheCursorOneToOneWhenDraggingEmptyCanvasRightAtZoomOnePointFive() {
        assertRightDragTracksAtZoom(1.5);
    }

    private static void assertRightDragTracksAtZoom(final double zoom) {
        final ScrolledFixture fixture = scrolledPanFixture(zoom);
        try {
            final GestureResult result = dragGesture(fixture, 1.0, 0.0, 60, 600.0, 650.0);
            assertTracksCursor(result, 1.0, 0.0, 60, "right drag at zoom " + zoom);
        }
        finally {
            fixture.controller.uninstall();
        }
    }
```

Expected assertions: `contentTravelX ∈ [57, 60]`; `contentTravelY == 0.0`; `maxStepX ≤ 1.5 + 1e-6`;
`maxStepY ≤ 1.5 + 1e-6`; `centerX` non-increasing with `centerX[59] != centerX[0]`. Measured:
pre-fix `contentTravelX = 1664.0`, `maxStepX = 56.0` (fails the band); post-fix
`contentTravelX = 58.0`, `maxStepX = 1.0`.

### 7.2 Horizontal runaway-direction regression at zoom 1.0 (scrollbar threshold crossing)

```java
    @Test
    public void trackTheCursorOneToOneWhenDraggingEmptyCanvasRightAtZoomOne() {
        assertRightDragTracksAtZoom(1.0);
    }
```

Same assertions and helper as §7.1. This is the case whose requested surface width crosses the
viewport extent mid-drag and whose horizontal scrollbar appears; the viewport extent and the
canvas size both change, so no assertion may assume a constant extent. Measured: pre-fix
`contentTravelX = 94.5`, `maxStepX = 9.0`; post-fix `contentTravelX = 58.5`, `maxStepX = 1.5`.

### 7.3 Horizontal runaway-direction regression at zoom 5.0

```java
    @Test
    public void trackTheCursorOneToOneWhenDraggingEmptyCanvasRightAtZoomFive() {
        assertRightDragTracksAtZoom(5.0);
    }
```

Same assertions and helper. Measured: pre-fix `contentTravelX = 1711.0`, `maxStepX = 58.0`;
post-fix `contentTravelX = 58.0`, `maxStepX = 1.0`.

### 7.4 Opposite-direction regression at zoom 1.0, 1.5, and 5.0

```java
    @Test
    public void trackTheCursorOneToOneWhenDraggingEmptyCanvasLeftAtZoomOne() {
        assertLeftDragTracksAtZoom(1.0);
    }

    @Test
    public void trackTheCursorOneToOneWhenDraggingEmptyCanvasLeftAtZoomOnePointFive() {
        assertLeftDragTracksAtZoom(1.5);
    }

    @Test
    public void trackTheCursorOneToOneWhenDraggingEmptyCanvasLeftAtZoomFive() {
        assertLeftDragTracksAtZoom(5.0);
    }

    private static void assertLeftDragTracksAtZoom(final double zoom) {
        final ScrolledFixture fixture = scrolledPanFixture(zoom);
        try {
            final GestureResult result = dragGesture(fixture, -1.0, 0.0, 60, 600.0, 650.0);
            assertTracksCursor(result, -1.0, 0.0, 60, "left drag at zoom " + zoom);
        }
        finally {
            fixture.controller.uninstall();
        }
    }
```

All three zooms are required: the design acceptance spans both directions at 1.0, 1.5, and 5.0,
and the zoom-1.0 pair (right in §7.2, left here) is the case whose requested surface width
crosses the viewport extent mid-drag and whose horizontal scrollbar appears. Expected assertions
for each case: `contentTravelX ∈ [-60, -57]` (checked as `travel * -1 ∈ [57, 60]`);
`contentTravelY == 0.0`; per-event steps `≤ 1.5 + 1e-6`; `centerX` non-decreasing with
`centerX[59] != centerX[0]`. Measured pre-fix / post-fix:

- zoom 1.0: `contentTravelX = -53.5` / `-57.5` (pre-fix fails the band because `53.5 < 57`).
- zoom 1.5: `contentTravelX = -29.0` / `-58.0` (pre-fix is damped to roughly half speed).
- zoom 5.0: `contentTravelX = -29.0` / `-58.0` (pre-fix is damped to roughly half speed).

### 7.5 Diagonal case at zoom 1.5 (y-axis sign pinned with vertical scroll active)

```java
    @Test
    public void trackTheCursorOneToOneWhenDraggingEmptyCanvasDiagonallyAtZoomOnePointFive() {
        final ScrolledFixture fixture = scrolledPanFixture(1.5);
        try {
            final GestureResult result = dragGesture(fixture, 1.0, 1.0, 60, 600.0, 650.0);
            assertDiagonalTracksAtZoomOnePointFive(result);
        }
        finally {
            fixture.controller.uninstall();
        }
    }

    private static void assertDiagonalTracksAtZoomOnePointFive(final GestureResult result) {
        assertThat(result.contentTravelX).as("diagonal drag at zoom 1.5 x")
            .isBetween(57.0, 60.0);
        assertThat(result.contentTravelY).as("diagonal drag at zoom 1.5 y")
            .isBetween(57.0, 69.0);
        assertThat(result.maxStepX).as("diagonal drag at zoom 1.5 max x step")
            .isLessThanOrEqualTo(PAN_MAX_CONTENT_STEP_PIXELS);
        assertThat(result.maxStepY).as("diagonal drag at zoom 1.5 max y step")
            .isLessThanOrEqualTo(PAN_MAX_CONTENT_STEP_PIXELS);
        assertMonotonicCenter(result.centersX, 1.0, "diagonal drag at zoom 1.5 x");
        assertMonotonicCenter(result.centersY, 1.0, "diagonal drag at zoom 1.5 y");
    }
```

Zoom 1.5 is pinned for the diagonal case because vertical scroll is genuinely active there: the
surface height (720 px) exceeds the viewport extent and `viewPosition.y` changes repeatedly
during the drag (11 -> 59 overall), whereas at zoom 1.0 the canvas height equals the viewport
extent (697 = 697) and `viewPosition.y` stays 0, so a y-axis sign inversion would not be
observable. The x assertions are the same as the horizontal cases. The y total band is
`[57, 69]` rather than `[57, 60]`: measured post-fix `contentTravelY = 68.0`, i.e. `58.0`
(1:1 tracking minus the 3 px dead zone) plus a deterministic 10 px contributed by the existing
`resizeSurface` integer recentering `(newHeight - oldHeight) / 2`, which floors a 3 px height
delta to 1 px of recentering and a 1 px delta to 0 (at zoom 1.5 the height request alternates
3 px / 1 px deltas from IEEE rounding around integer values); the 69 px upper bound is the
measured 68.0 plus 1 px of rounding tolerance. The per-event `maxStepY <= 1.5 + 1e-6` assertion
still proves the fix removed per-event amplification, and a y-sign inversion (which compounds
through the scroll feedback to `contentTravelY = 1679.0` pre-fix) is far outside the band.
Measured pre-fix `contentTravelX = 1664.0`, `contentTravelY = 1679.0`, `maxStepX = 56.0`,
`maxStepY = 56.0`; post-fix `contentTravelX = 58.0`, `contentTravelY = 68.0`,
`maxStepX = 1.0`, `maxStepY = 1.5`.

### 7.6 Mechanism pin test

```java
    @Test
    public void doNotPanWhenAProgrammaticScrollMovesTheViewportUnderAStationaryPointer() {
        final ScrolledFixture fixture = scrolledPanFixture(1.5);
        try {
            final GraphCanvas canvas = fixture.canvas;
            final JScrollPane scrollPane = fixture.scrollPane;
            final double beforeDragCenterX = canvas.viewport().centerX();
            dispatch(canvas, gestureEvent(canvas, scrollPane, MouseEvent.MOUSE_PRESSED, 600.0, 650.0));
            dispatch(canvas, gestureEvent(canvas, scrollPane, MouseEvent.MOUSE_DRAGGED, 610.0, 650.0));
            final double centerX = canvas.viewport().centerX();
            final double centerY = canvas.viewport().centerY();
            assertThat(centerX).isNotEqualTo(beforeDragCenterX);

            final Point viewPosition = scrollPane.getViewport().getViewPosition();
            final Dimension extent = scrollPane.getViewport().getExtentSize();
            final int maxX = Math.max(0, canvas.getWidth() - extent.width);
            final int maxY = Math.max(0, canvas.getHeight() - extent.height);
            final int deltaX = Math.min(40, maxX - viewPosition.x);
            final int deltaY = Math.min(20, maxY - viewPosition.y);
            assertThat(deltaX).isGreaterThan(0);
            assertThat(deltaY).isGreaterThan(0);
            scrollPane.getViewport().setViewPosition(
                new Point(viewPosition.x + deltaX, viewPosition.y + deltaY));
            assertThat(scrollPane.getViewport().getViewPosition())
                .isEqualTo(new Point(viewPosition.x + deltaX, viewPosition.y + deltaY));

            dispatch(canvas, gestureEvent(canvas, scrollPane, MouseEvent.MOUSE_DRAGGED, 610.0, 650.0));

            assertThat(canvas.viewport().centerX()).isEqualTo(centerX);
            assertThat(canvas.viewport().centerY()).isEqualTo(centerY);
        }
        finally {
            fixture.controller.uninstall();
        }
    }
```

The drag threshold is crossed first (asserted by `centerX != beforeDragCenterX`), so a pan would
fire if any delta were computed. The viewport is then scrolled programmatically by an in-range
amount, and the next event's component coordinates are rebuilt from the unchanged physical
pointer plus the new view position. The nominal `canvas.viewport()` center must be exactly
unchanged. `visibleViewport()` is deliberately not asserted here because the test itself moves
the scroll position. Measured in the red staging (viewportPoint only, controller pre-fix): the
injected scroll produced a pan on both axes, moving `centerX` from `-6.666666666666667` to
`-40.0` and `centerY` from `0.0` to `-13.333333333333334`; post-fix both centers are exactly
unchanged (`centerX = -6.666666666666667`, `centerY = 0.0`).

### 7.7 `viewportPoint` offset unit test

```java
    @Test
    public void applyTheViewPositionWhenConvertingComponentPointToViewportCoordinates() {
        final ScrolledFixture fixture = scrolledPanFixture(1.5);
        try {
            fixture.scrollPane.getViewport().setViewPosition(new Point(60, 20));
            assertThat(fixture.scrollPane.getViewport().getViewPosition())
                .isEqualTo(new Point(60, 20));
            assertThat(fixture.canvas.viewportPoint(new Point(200, 150)))
                .isEqualTo(new Point(140, 130));
        }
        finally {
            fixture.controller.uninstall();
        }
    }
```

`(60, 20)` is inside the clamp range for the hosted pan fixture at zoom 1.5 (validated), so it
is a stable, known view position. The offset is applied componentwise and no zoom or center
term enters the conversion.

### 7.8 `viewportPoint` identity unit test

```java
    @Test
    public void returnTheSamePointWhenConvertingWithoutAViewportAncestor() {
        final GraphCanvas canvas = new GraphCanvas();
        final Point point = new Point(7, 9);
        assertThat(canvas.viewportPoint(point)).isSameAs(point);
    }
```

`isSameAs` pins the no-viewport path as an identity that returns the argument instance, not a
copy.

### 7.9 `viewportPoint` null unit test

```java
    @Test
    public void rejectANullComponentPoint() {
        final GraphCanvas canvas = new GraphCanvas();
        assertThatThrownBy(() -> canvas.viewportPoint(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("componentPoint");
    }
```

### 7.10 Scrolled-viewport hit-test boundary test (`worldAt` stays component space)

Uses the existing `Fixture` state (node `(-40, 0)` radius `20.0` is `fixture.firstEndpoint`),
hosted in a small scroll pane so the view is scrollable, then scrolled away from the recentered
position.

```java
    @Test
    public void hitTheEndpointUnderThePointerWhenTheViewportIsScrolled() {
        final Fixture fixture = Fixture.create();
        final ScrolledFixture scrolled = scrolledFixture(fixture.state, 1.0, 0.0, 0.0, 300, 200);
        try {
            final GraphCanvas canvas = scrolled.canvas;
            final JScrollPane scrollPane = scrolled.scrollPane;
            scrollPane.getViewport().setViewPosition(new Point(100, 80));
            assertThat(scrollPane.getViewport().getViewPosition()).isEqualTo(new Point(100, 80));

            final Dimension size = new Dimension(canvas.getWidth(), canvas.getHeight());
            final Point2D node = canvas.viewport().toScreen(-40.0, 0.0, size);
            final int x = (int) Math.round(node.getX());
            final int y = (int) Math.round(node.getY());
            dispatch(canvas, clickAt(canvas, MouseEvent.MOUSE_CLICKED, x, y, 1, MouseEvent.BUTTON1));
            assertThat(scrolled.listener.intents).containsExactly(
                new GraphIntent.ChangeSelection(Optional.of(fixture.firstEndpoint)),
                new GraphIntent.RevealSourceNode(fixture.firstEndpoint));

            dispatch(canvas, clickAt(canvas, MouseEvent.MOUSE_CLICKED, x, y, 2, MouseEvent.BUTTON1));
            assertThat(scrolled.listener.intents).containsExactly(
                new GraphIntent.ChangeSelection(Optional.of(fixture.firstEndpoint)),
                new GraphIntent.RevealSourceNode(fixture.firstEndpoint),
                new GraphIntent.OpenSourceNode(fixture.firstEndpoint));

            final LayoutPoint before = canvas.viewport().toWorld((double) x, (double) y, size);
            dispatch(canvas, wheel(canvas, x, y, -1));
            final LayoutPoint after = canvas.viewport().toWorld((double) x, (double) y, size);
            assertThat(canvas.viewport().zoom()).isGreaterThan(1.0);
            assertThat(after.x()).isCloseTo(before.x(), within(1e-9));
            assertThat(after.y()).isCloseTo(before.y(), within(1e-9));
        }
        finally {
            scrolled.controller.uninstall();
        }
    }
```

The click point is the nominal canvas screen position of the node at world `(-40, 0)` (validated
`(360, 280)` for the 800 x 560 hosted canvas at zoom 1.0). The click must still hit because the
controller computes the hit from raw component space; if `worldAt` were fed
`viewportPoint(component)` it would be off by `viewPosition / zoom = (100, 80)` world units and
hit nothing. The double-click must still emit `OpenSourceNode`. The wheel probe additionally
pins that `zoomAround(event.getPoint(), factor)` keeps the world point under the physical
component position invariant after a zoom, which fails if `zoomAround` is converted to viewport
space. This test passes pre-fix and post-fix and exists to prevent the boundary from being
"fixed" too.

### 7.11 Recorded red-phase and green-phase measurements

The red phase is staged explicitly, because the test class calls `canvas.viewportPoint(...)`:
apply only §2.1 (`GraphCanvas.viewportPoint`), keep the four `GraphInteractionController` sites
unchanged at `event.getX()/getY()`, then run the focused `GraphInteractionControllerShould`
class. The class compiles; the §7.7–§7.10 `viewportPoint`/boundary tests pass, and the §7.1–§7.6
drag tests and the mechanism pin test fail with the pre-fix numbers below. Applying §3 on top
turns the same class green.

Measured by materializing the staged spec in a throwaway copy of this revision and running the
test methods above on Java 21.0.8-zulu (identical fixture, 900 x 700 scroll pane, 60 one-pixel
events, finger start `(600, 650)`):

| Test | Pre-fix content travel | Pre-fix max event step | Post-fix content travel | Post-fix max event step |
| --- | --- | --- | --- | --- |
| right, zoom 1.0 | 94.5 | 9.0 | 58.5 | 1.5 |
| right, zoom 1.5 | 1664.0 | 56.0 | 58.0 | 1.0 |
| right, zoom 5.0 | 1711.0 | 58.0 | 58.0 | 1.0 |
| left, zoom 1.0 | -53.5 | 1.0 | -57.5 | 1.0 |
| left, zoom 1.5 | -29.0 | 1.0 | -58.0 | 1.0 |
| left, zoom 5.0 | -29.0 | 1.0 | -58.0 | 1.0 |
| diagonal, zoom 1.5 | 1664.0 (x) / 1679.0 (y) | 56.0 (x) / 56.0 (y) | 58.0 (x) / 68.0 (y) | 1.0 (x) / 1.5 (y) |
| mechanism pin, zoom 1.5 | `centerX` -6.666666666666667 -> -40.0; `centerY` 0.0 -> -13.333333333333334 | - | both centers exactly unchanged | - |

The pre-fix 1.5 runaway value is the design's recorded 1664 px for a 60 px cursor drag; the
number reproduces exactly with this fixture and harness model. The diagonal post-fix y travel of
68.0 px is the §7.5 surface-recentring quantization (58.0 px tracking plus 10 px), not delta
amplification: `maxStepY = 1.5`. The full `freeplane_plugin_graph` test task (including these
additions) passes on the materialized fix.

## 8. Build and verification commands, acceptance criteria, audit evidence

### 8.1 Commands

Repository conventions: use `gradle` (not `gradlew`/Maven) with the Java 21.0.8-zulu toolchain
from `~/.sdkman/candidates/java/21.0.8-zulu`; add `-PTestLoggingFull` for verbose failures.

```bash
export JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu
export PATH="$JAVA_HOME/bin:$PATH"

# Compile production and test sources.
gradle :freeplane_plugin_graph:compileJava :freeplane_plugin_graph:compileTestJava

# Run the interaction tests specified in §7.
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.canvas.GraphInteractionControllerShould"

# Same target with full failure logging.
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.canvas.GraphInteractionControllerShould" -PTestLoggingFull

# Full module regression gate (the two unchanged canvas test classes included).
gradle :freeplane_plugin_graph:test
```

Expected observable outcome: every command exits `0` with `BUILD SUCCESSFUL`; the focused class
reports zero failures with the §7 methods present.

### 8.2 Static gates

```bash
# One declaration in GraphCanvas, no other production use of the old delta reads.
grep -n "Point viewportPoint" freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphCanvas.java   # exactly 1
grep -n "viewportPoint(event.getPoint())" freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphInteractionController.java  # exactly 4
grep -n "event.getX()\|event.getY()" freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphInteractionController.java  # no output

# The component-space boundary is intact: 7 worldAt(component) call sites and 1 zoomAround(component).
grep -c "worldAt(event.getPoint())" freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphInteractionController.java  # 7
grep -c "zoomAround(event.getPoint()" freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphInteractionController.java  # 1

# Exactly the §9 files changed.
git diff --stat
```

`git diff` must show only the three §9 files, with no edits inside `panByPixels`,
`resizeSurface`, `surfacePixels`, `centerScrollView`, `clampedPosition`, or
`scheduleViewportClamp`.

### 8.3 Acceptance criteria

- The regression tests (right at 1.0/1.5/5.0, left at 1.0/1.5/5.0, diagonal at 1.5) pass, and
  each fails against the pre-fix implementation with the §7.11 values (the §7.11 staging applies
  only `viewportPoint` and leaves the controller pre-fix).
- The mechanism pin test passes and fails against the pre-fix implementation.
- The `viewportPoint` unit tests and the scrolled-viewport boundary test pass.
- Empty-area drag tracks the cursor 1:1 in both directions at zoom 1.0, 1.5, and 5.0 in the
  harness model, allowing for the intentional 3 px click-versus-drag dead zone at gesture start
  and one pixel of integer rounding per event.
- Full `freeplane_plugin_graph` test suite green.
- No change to files or behavior outside the two production classes and the one test class of
  §9.

### 8.4 Real-application acceptance evidence (audit)

This is audit evidence, not a shipped artifact. Follow the repository's established
virtual-display procedure (`capture-freeplane-real-app-ui-evidence` skill):

1. Rebuild the distribution from this worktree:

   ```bash
   gradle :freeplane_plugin_graph:graphSmokeInstallation --console=plain --offline
   ```

2. Seed an isolated `HOME`/`XDG_CONFIG_HOME` with a Graph Workspace (`.fpg`) whose viewport has
   `zoom > 1.0` (e.g. 1.5) and whose map (`.mm`) has a large node area, plus the
   `graph_workspace_recent_workspaces` `auto.properties` entry.
3. Start `Xvfb` and launch `BIN/freeplane.sh` with the isolated environment; open the Graph
   Workspace window and wait until the graph window and its maps are loaded.
4. With `java.awt.Robot`, record the initial pointer position, capture the window rectangle,
   then press in an empty area and drag the physical pointer by 60 px in small steps (at least
   one step per pixel), release, and capture again. Measure the on-screen displacement of a
   fixed content landmark (a node disc center or a hull corner) between the two captures and the
   physical pointer displacement over the same interval.
5. The two displacements must match within 5 px (3 px dead zone plus capture rounding). At
   zoom 1.5 the pre-fix behavior instead moves content by roughly 1664 px for a 60 px pointer
   drag.
6. Store the screenshot pair, the measured numbers, the app log excerpt, and the binary revision
   under `freeplane_plugin_graph/build/evidence/` (git-ignored) and record them in the completion
   report; nothing from this procedure is committed.

## 9. File change list

Exactly these files change; nothing else:

1. `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphCanvas.java` —
   add the §2.1 `viewportPoint` method after `worldAt`.
2. `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphInteractionController.java` —
   add `import java.awt.Point;` and change `handleMouseDragged`, `beginSelect`, `beginConnect`,
   and `finishSelect` per §3.
3. `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphInteractionControllerShould.java` —
   add the §6 imports/fixtures/helpers and the §7 test methods.

Unchanged (must stay green): `GraphCanvasPaintShould.java`,
`AccessibleGraphCanvasShould.java`, and every other source and test file.
