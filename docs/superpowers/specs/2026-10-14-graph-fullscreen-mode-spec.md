# Graph Workspace - Full-Screen Presentation Mode

- Date: 2026-10-14
- Topic slug: `graph-fullscreen-mode`
- Approved design: `docs/superpowers/specs/2026-10-14-graph-fullscreen-mode-design.md`, revision 13
- Authority: the approved design is authoritative for this implementation; this document makes its contracts executable
- Module: `freeplane_plugin_graph` only, plus the existing English viewer translation resource
- Code baseline: `bec1900495` (`plugin/graph-workspace`); source references in the design are against that baseline
- Status: implementation-ready

## 1. Scope, Terminology, and Non-Goals

### 1.1 Scope

Implement a presentation-mode toggle for the standalone Graph Workspace `JFrame`.
On Linux and Windows, F11 and View -> Full Screen enter and leave a screen-filling,
undecorated state in which the graph canvas is the only visible workspace content. Escape
leaves presentation mode when the canvas has neither a connection preview nor a selection.
The View item is a check item and is the authoritative visual state indicator.

Entering and leaving presentation mode must preserve the frame's prior bounds and extended
state, the effective chrome state, the sidebar divider/collapse state, the visible graph
center, and zoom. A scroll-boundary clamp is the only documented viewport deviation. The
mode must not execute a workspace command, change the workspace document, set a dirty flag,
or persist display settings. The hold continues through late Swing and window-manager
geometry until a bounded settling horizon completes.

The feature is confined to the graph plugin. It does not change the main mind-map window's
F11 behavior, `FrameController`, workspace XML, graph layout, graph interaction semantics
other than the Escape fallback defined in Section 2.7, or persisted settings.

### 1.2 Terminology

| Term | Definition |
| --- | --- |
| **presentation mode** | The active state in which the graph frame is undecorated, fills its current screen, and all Graph Workspace chrome is hidden. |
| **chrome** | The menu bar, `WorkspaceToolbar`, `MapSidebarPanel`, `WorkspaceSettingsPanel`, and `GraphStatusBar` status slot. |
| **screen fill** | The bounded non-macOS frame transition that disposes the frame, makes it undecorated, fills the current graphics configuration, and makes it visible again. |
| **survival** | The result of one protected frame operation: `REENTERED_FULLSCREEN`, `RESTORED_WINDOWED`, or `UNKNOWN_FRAME`. It describes the actual surviving frame state, not the requested operation. |
| **frame state** | An immutable snapshot of the decorated window's bounds and extended state captured before the first transition mutation. |
| **sidebar override** | A transient effective sidebar snapshot containing the collapsed/rail state, divider location, divider size, and enabled state. It takes precedence over persisted `DisplaySettings` while installed. |
| **viewport hold** | The armed presentation viewport compensation state. It captures the transition-start view and suppresses transition-generated viewport publication while geometry settles. |
| **anchor** | `canvas.viewport()`, the canvas's world-center and zoom model value. A changed anchor is classified before the canvas positioning marker and therefore identifies user pan/zoom/fit actions. |
| **surface size** | The `GraphCanvas` component width and height, denoted `W`. |
| **extent** | The `JViewport` visible extent width and height, denoted `E`. |
| **scroll position** | The integer `JViewport` view position, denoted `P`. |
| **model application** | A viewport assignment initiated by the model, including `applyInitialViewport` and model-owned `canvas.setViewport` calls, protected by `applyingViewport`. |
| **positioning-active** | `canvas.isViewportPositioningActive() == true`, meaning the canvas positioning-depth marker is positive. This deliberately excludes pending clamps without active positioning. |
| **genuine user viewport action** | A changed anchor, a scroll/keyboard/wheel movement with unchanged extent and surface size while an explicit user-scroll input marker is active, or a direct canvas/toolbar viewport publication entry. It publishes first, reconciles the dedupe cache, and disarms. |
| **quiet window** | A 200 ms interval containing neither a frame-bounds change nor a viewport change. |
| **platform horizon** | The minimum 2,500 ms settling period after the last transition geometry operation. The absolute cap is 5,000 ms. |
| **transition generation** | A monotonically increasing identifier attached to a viewport hold, settle callback, and quiet-window chain. It invalidates stale asynchronous work. |

### 1.3 Non-goals

- No change to the main map window's F11 behavior and no refactor of core `FrameController`.
- No native macOS full-screen implementation in v1, no EAWT listener, and no dormant macOS bridge.
- No persistence of full-screen state across sessions.
- No toolbar full-screen button and no configurable shortcut.
- No auto-fit, re-layout, or re-settle on entry or exit.
- No change to workspace file format, workspace commands, dirty-state behavior, or persisted
  `DisplaySettings`.
- No change to graph rendering or interaction behavior except the documented Escape fallback.
- No compatibility fallback or second presentation execution path.

### 1.4 Platform and implementation constraints

Production source remains Java 8-compatible and uses four-space indentation. All transition
work runs on the EDT. No exception from a presentation transition may escape to the EDT.

`GraphPresentationMode` has a constructor-injected `supported` flag for headless tests and
platform tests. The production constructor supplies `!Compat.isMacOsX()`. Therefore:

- Linux and Windows: `isAvailable()` is true and the mode is active.
- macOS: `isAvailable()` is false; the View item exists but is disabled and unchecked, F11
  is a no-op, Escape does not invoke the unavailable mode, and no frame, chrome, viewport,
  hint, or workspace command operation is performed.

The exact production mechanism is:

```java
boolean isAvailable() {
    return supported;
}

GraphPresentationMode(... production dependencies ...) {
    this(..., !Compat.isMacOsX());
}
```

No `Compat.setFullScreenOnMac` call and no native macOS listener is allowed in this v1.

## 2. Behavior and Exact Contracts

### 2.1 User-visible behavior

| Trigger/state | Required behavior |
| --- | --- |
| F11 while inactive on Linux/Windows | Capture the view, chrome, and frame; hide chrome; fill the current screen; compensate the viewport; focus the canvas; show the exit hint; check the View item; become active. |
| View -> Full Screen while inactive | Perform the same transition exactly once. The item is a `JCheckBoxMenuItem`; it has no accelerator. |
| F11 while active | Capture the current presentation view again, restore the decorated frame, restore captured chrome and sidebar state, compensate after final geometry, hide the hint, uncheck the item, focus the canvas, and become inactive. |
| View -> Full Screen while active | Perform the same exit transition exactly once. |
| Escape with a connection preview | Cancel the preview, consume Escape, remain in the current presentation state. |
| Escape with a selection but no preview | Clear the selection, consume Escape, remain in the current presentation state. |
| Escape with neither preview nor selection | Consume Escape and call `requestExit`; exit only if the mode is active. |
| unavailable platform | No-op for all mode entry points; item disabled and unchecked. |
| close | Close the mode exactly once from `GraphWorkspaceWindow.closeOnEdt`; cancel the hint and viewport callbacks, discard snapshots, disarm the hold, and ignore late callbacks. |

The visible center is preserved through geometry changes. User pan and zoom made during
presentation are normal user operations and are preserved through exit; exit never restores
the entry view.

### 2.2 `ScreenFillSurface`, `FrameState`, and `Survival`

All three names are declared in `ScreenFillSurface.java` as nested types of the new
package-private interface, so callers use `ScreenFillSurface.FrameState` and
`ScreenFillSurface.Survival`:

```java
interface ScreenFillSurface {
    enum Survival {
        RESTORED_WINDOWED,
        REENTERED_FULLSCREEN,
        UNKNOWN_FRAME
    }

    final class FrameState {
        FrameState(Rectangle bounds, int extendedState);
        Rectangle bounds();
        int extendedState();
    }

    FrameState captureFrameState();
    Survival applyScreenFill(FrameState previous);
    Survival restoreFrameState(FrameState previous);
}
```

`FrameState` makes a defensive copy of the supplied `Rectangle` in its constructor and returns
defensive copies from `bounds()`. `extendedState()` is the exact value returned by
`JFrame.getExtendedState()` before transition mutation. `FrameState` has no mutable fields,
no equality requirement, and no persistence representation.

`ScreenFillSurface.applyScreenFill` and `restoreFrameState` never throw. Each calls exactly
one raw operation and, when that operation throws, exactly one inverse raw operation. The
method returns the result of the surviving raw operation:

- requested operation succeeds: return `REENTERED_FULLSCREEN` for entry or
  `RESTORED_WINDOWED` for exit;
- requested operation fails and the one inverse succeeds: return the opposite survival;
- both raw operations fail: return `UNKNOWN_FRAME`.

The mode never retries a frame operation. Every failure is logged by the mode or its injected
logger with the original and recovery exception where available.

For a saved state that includes `MAXIMIZED_BOTH`, the window manager owns the final geometry: the
restore sets the saved bounds and then the saved extended state, and the window manager places the
maximized frame over its work area. Acceptance for maximized restores therefore asserts that the
frame covers the work area of its graphics configuration and stays within the screen, within the
documented X11 AWT coordinate-cache tolerance (the native y may differ from the AWT-cached y by the
work-area inset; the pre-entry AWT `getBounds()` value may itself be stale); non-maximized restores
compare against the exact captured bounds. The same work-area relationship applies to entry fill:
the frame must cover the work area and stay within the screen.

`GraphWorkspaceWindow` implements `ScreenFillSurface` and adds these package-private raw
methods, which may throw and never call one another:

```java
FrameState captureFrameState();
Survival applyScreenFill(FrameState previous);
Survival restoreFrameState(FrameState previous);
void rawEnterFullScreen();
void rawRestoreWindowed(FrameState previous);
```

`rawEnterFullScreen()` performs this exact sequence:

```text
graphics = getGraphicsConfiguration().getBounds()
dispose()
setUndecorated(true)
setExtendedState(Frame.MAXIMIZED_BOTH)
setBounds(graphics)
if (Compat.isWindowsOS()) setResizable(false)
setVisible(true)
validate()
```

`rawRestoreWindowed(previous)` performs this exact sequence:

```text
dispose()
setUndecorated(false)
setBounds(previous.bounds())
setExtendedState(previous.extendedState())
if (Compat.isWindowsOS()) setResizable(false)
setResizable(true)
setVisible(true)
validate()
```

The frame state is captured before `dispose()`. The existing `show()` override and `closed`
flag continue to prevent a closed frame from being re-shown. Temporary `dispose()` calls are
transition mechanics and must not call the close lifecycle.

### 2.3 `PresentationChrome`

`PresentationChrome` is in `org.freeplane.plugin.graph.window`:

```java
interface PresentationChrome {
    void capture();
    void hide();
    void restore();
    void discard();
}
```

`GraphWorkspaceWindowModel` implements it. `capture()` records the effective component state
and installs the transient sidebar override before any component is hidden. `hide()` is
idempotent and hides all chrome while marking the model presentation-hidden. `restore()` is
best-effort per component and releases the presentation-hidden marker in a `finally` block;
the sidebar override remains installed until genuine user intent releases it. `discard()`
drops the snapshot and removes the override. A capture failure before a usable snapshot makes
the mode discard the partial snapshot; a snapshot is retained after chrome restoration until
the transition finishes or the window closes.

The captured fields are:

- menu bar visibility;
- toolbar visibility;
- sidebar visibility;
- settings-panel visibility;
- status-slot visibility;
- `splitPane.isEnabled()`;
- `splitPane.getDividerSize()`;
- effective `splitPane.getDividerLocation()`;
- `sidebar.isCollapsed()`.

Restore must also synchronize `toolbar.setSettingsVisible(settingsPanel.isVisible())`, restore
the maps-sidebar menu selected state and `appliedSidebarWidth`, clear
`sidebarApplyPending` and `sidebarGestureActive`, and perform the split-pane fields inside
`runWithSidebarGuard`. A failure restoring one component is logged and must not prevent later
components from being restored.

### 2.4 `PresentationViewport`

`PresentationViewport` is in `org.freeplane.plugin.graph.window`:

```java
interface PresentationViewport {
    void capture();
    void compensate();
    void classify(ChangeEvent event);
    void canvasPublication();
    void toolbarPublication(GraphViewport value);
    void disarm();
    void windowClosed();
}
```

The `ChangeEvent` type is `javax.swing.event.ChangeEvent`; `GraphViewport` is
`org.freeplane.plugin.graph.canvas.GraphViewport`.

`GraphWorkspaceWindowModel` implements this contract, or delegates these methods to a
private implementation owned by the model. The model remains the owner of the scroll pane,
canvas, `suppressViewportEvents`, and `lastPublishedViewport`.

- `capture()` records `P0`, `E0`, `W0`, and `A0 = canvas.viewport()`, increments the transition
  generation, arms the hold, and keeps viewport publication suppressed while the hold is armed.
  It is called at entry and freshly at exit.
- `compensate()` is idempotent and absolute: it derives the target from the original baseline
  and current geometry, applies it under suppression, reconciles `lastPublishedViewport`
  without executing a command, logs a clamp, and registers the generation-checked settle
  correction when needed.
- `classify(ChangeEvent)` applies the ordered rule in Section 5.2.
- `canvasPublication()` and `toolbarPublication(GraphViewport)` are called at the direct
  publication entry points. The caller publishes first; these methods then reconcile and
  disarm the hold. They must not pre-cache the value before publication.
- `disarm()` reconciles the dedupe cache before clearing the armed state and cancels the quiet
  chain and settle callback for the current generation.
- `windowClosed()` invalidates the generation, cancels all scheduled work and the settle
  callback, and disarms without publishing.

### 2.5 `PresentationHint`

`PresentationHint` is a package-private final class in
`org.freeplane.plugin.graph.window`:

```java
final class PresentationHint {
    PresentationHint(JLayeredPane host, String text);
    PresentationHint(JLayeredPane host, String text, int delayMillis, TimerFactory timerFactory);
    void show();
    void hide();
    void dispose();

    @FunctionalInterface
    interface TimerFactory {
        Timer create(int delayMillis, Runnable expiry);
    }
}
```

The production constructor uses the default 3,000 ms delay and a factory backed by
`javax.swing.Timer`. The second constructor is the deterministic test seam. The hint is a
non-focusable, mouse-transparent panel installed in the frame layered pane at top center. Its
`contains(int, int)` returns `false`, so it cannot intercept canvas gestures. `show()` makes it
visible and restarts its expiry timer; `hide()` makes it invisible and stops the timer;
`dispose()` stops the timer, removes the panel from the host, and makes every later timer
callback a no-op. The text is loaded with `graph_workspace.hint.full_screen_exit` by the
window model.

### 2.6 `GraphPresentationMode`

`GraphPresentationMode` is a package-private final class in
`org.freeplane.plugin.graph.window`:

```java
final class GraphPresentationMode {
    GraphPresentationMode(ScreenFillSurface surface,
            PresentationChrome chrome,
            PresentationViewport viewport,
            PresentationHint hint,
            JCheckBoxMenuItem menuItem,
            Runnable focusCanvas,
            Consumer<String> log,
            boolean supported);

    GraphPresentationMode(ScreenFillSurface surface,
            PresentationChrome chrome,
            PresentationViewport viewport,
            PresentationHint hint,
            JCheckBoxMenuItem menuItem,
            Runnable focusCanvas,
            Consumer<String> log);

    boolean isAvailable();
    void toggle();
    boolean isActive();
    void requestExit();
    void windowClosed();
}
```

The overload without `supported` delegates with `!Compat.isMacOsX()`. All dependencies are
non-null. `menuItem` is updated only on the EDT and is disabled and unchecked whenever
`isAvailable()` is false. `focusCanvas` is called only after the surviving transition state
has been selected.

The mode owns `INACTIVE`, `ACTIVE`, and `CLOSED` state, a transition-in-progress guard, the
current `FrameState` snapshot, and the exception boundary. `toggle()` enters from inactive
and exits from active; it is a no-op while closed, unavailable, or already transitioning.
`requestExit()` exits only from active. `windowClosed()` is idempotent, cancels all mode-owned
work through the collaborators, discards the snapshot, and moves to `CLOSED`.

### 2.7 `GraphInteractionController` Escape fallback

Add the following field and setter in
`freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphInteractionController.java`:

```java
private Runnable escapeFallback;

public void setEscapeFallback(Runnable fallback) {
    escapeFallback = fallback;
}
```

`setEscapeFallback` accepts null, meaning no fallback. Replace only the Escape branch of
`handleKeyPressed` with this ordering:

```java
final boolean hadPreview = previewSource != null;
final boolean hadSelection = canvas.paintState().selection().isPresent();
if (hadPreview) {
    cancelPreview();
}
else {
    setSelectionVisual(null);
    emit(new GraphIntent.ChangeSelection(Optional.<ProjectedEndpointKey>empty()));
}
event.consume();
if (!hadPreview && !hadSelection && escapeFallback != null) {
    escapeFallback.run();
}
return;
```

The selection snapshot is taken before the clear. Existing preview cancellation, selection
clearing, `GraphIntent.ChangeSelection(empty)`, and unconditional event consumption remain
unchanged. `GraphWorkspaceWindowModel` installs `presentationMode::requestExit` as the fallback.

### 2.8 `GraphCanvas` additions

Add exactly these two public members to
`freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphCanvas.java`:

```java
public void runWhenViewportSettled(Runnable callback);
public boolean isViewportPositioningActive();
```

`isViewportPositioningActive()` returns `viewportPositioningDepth > 0`; it must not include
`pendingViewportClamps`.

`runWhenViewportSettled` requires a non-null callback and is EDT-safe through the existing
`onEdt` helper. If both `viewportPositioningDepth == 0` and `pendingViewportClamps == 0`, it
runs the callback immediately on the EDT. Otherwise it stores one pending callback. A later registration replaces the pending callback; when the two counters are both zero, the stored callback is consumed and run once. Callback execution is after the decrement of the final counter and is protected so a callback exception does not escape the canvas completion path. The model's generation check handles stale callbacks.

No other `GraphCanvas` behavior, event, listener, surface-sizing algorithm, or public API may
change.

### 2.9 `GraphWorkspaceWindow` concrete changes

Modify `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java` as follows:

1. Add fields for the `GraphPresentationMode` and any frame-transition injection seam required
   by `ScreenFillSurface`; keep `closed` as the existing real-close guard.
2. Construct the model and mode so the mode receives the model as `PresentationChrome` and
   `PresentationViewport`, a layered-pane `PresentationHint`, the full-screen menu item,
   `canvas()::requestFocusInWindow` through a `Runnable`, and the existing command message/log
   sink. Install `interactionController.setEscapeFallback(presentationMode::requestExit)`.
3. Add one `F11` binding in the root pane's `WHEN_IN_FOCUSED_WINDOW` input map and action map.
   The action checks `presentationMode.isAvailable()` and calls `toggle()` exactly once. Do
   not add a menu accelerator.
4. Add a `JCheckBoxMenuItem` named `graph-workspace-full-screen-menu-item`, using
   `graph_workspace.action.full_screen`, after the Settings item in the View menu. Its action
   calls `presentationMode.toggle()`. Its selected state follows the mode and it is disabled
   and unchecked when unavailable.
5. Implement `ScreenFillSurface` and its raw frame methods exactly as in Section 2.2.
6. Call `presentationMode.windowClosed()` from `closeOnEdt()` after setting `closed` and before
   disposing the frame. Do not attach this lifecycle to `WINDOW_CLOSED`; temporary transition
   `dispose()` calls must not close the mode.
7. Leave `FrameResynchronizer.install(this)` in place. Popup placement after a cycle is an
   acceptance probe, not a reason to add another transition path.

### 2.10 `GraphWorkspaceWindowModel` concrete changes

The model remains package-private and is changed method-by-method:

- Implement `PresentationChrome` and `PresentationViewport`.
- Add a transient presentation-hidden flag and a chrome snapshot holding every field listed in
  Section 2.3. Add a nullable sidebar override with collapsed flag, divider location, divider
  size, and enabled state.
- Add viewport-hold fields: armed flag, generation, baseline scroll `P0`, baseline extent
  `E0`, baseline surface size `W0`, baseline anchor `A0`, `applyingViewport`, last geometry
  timestamp, and the injected scheduler/quiet-window handles. The default scheduler uses EDT
  timers; tests supply a fake scheduler.
- In the constructor, wire the viewport listener to `classify(event)` after the existing model
  listener setup, wire canvas and toolbar publication callbacks, wire all three user sidebar
  controls through `sidebarToggledFromUser`, and create the mode after the menu exists.
- `applyPresentation(GraphWorkspacePresentation)` continues to update canvas/theme/settings,
  then calls `applySidebarSettings(settings)`. It must not clear the override.
- Replace `applySidebarSettings(DisplaySettings)` with the three-step rule in Section 4.2.
- Add `sidebarToggledFromUser(boolean collapsed)`, which releases the sidebar override before
  calling the existing sidebar mutation path. The shared `CollapsedListener` remains a
  programmatic notification and never releases the override.
- Add pre-mutation checks to `setSidebarCollapsed`, `commitSidebarWidth`, and
  `resetSidebarWidth`. They return before changing component state, session fields, or issuing a
  command when presentation-hidden or the sidebar override is installed. The user entry method
  releases first, so genuine user changes continue to work.
- Update `publishExternalViewport`, `publishCanvasViewport`, and `publishToolbarViewport` so
  model applications and geometry do not publish while the hold is armed; direct publication
  paths publish first and then call the corresponding `PresentationViewport` method.
- Wrap `applyInitialViewport` and every model-owned `canvas.setViewport` call in an
  `applyingViewport` guard in addition to `runWithViewportEventsSuppressed`.
- Add a `windowClosed`/`close` call to the viewport collaborator before registrations are closed.
- Do not alter `updateMapRows`, graph layout, command routing, or persisted presentation data.

### 2.11 `MapSidebarPanel` concrete changes

Modify `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/MapSidebarPanel.java`:

- Remove the constructor's `rail.restoreButton().addActionListener(event -> setCollapsed(false))`.
- Keep `CollapsedListener`, `setCollapsed`, and the programmatic listener behavior unchanged.
- Keep the existing `rail()` accessor so `GraphWorkspaceWindowModel` can attach the restore
  button to `sidebarToggledFromUser(false)`.
- The expanded collapse button is wired in the model to `sidebarToggledFromUser(true)`.
- The View menu maps-sidebar item is wired in the model to
  `sidebarToggledFromUser(!sidebar.isCollapsed())`.

No command or persisted setting may be emitted by `MapSidebarPanel` itself.

## 3. State Machine, Transition Table, and Exception Boundary

### 3.1 States and invariants

The externally observable mode states are:

```text
INACTIVE --enter--> ACTIVE
ACTIVE   --exit-->  INACTIVE
*         --close--> CLOSED
```

A transition-in-progress guard prevents a second F11/menu/escape action from entering the
same transition twice. `CLOSED` is terminal. When unavailable, the state remains `INACTIVE`.
The viewport hold may remain armed after the transition method returns; this is expected and is
not a fourth presentation state.

### 3.2 Transition table

| Current | Operation | Survival | Chrome | Hint | Menu item | Final mode |
| --- | --- | --- | --- | --- | --- | --- |
| INACTIVE | enter | `REENTERED_FULLSCREEN` | hidden | shown | checked | ACTIVE |
| INACTIVE | enter | `RESTORED_WINDOWED` | restored before compensation | hidden | unchecked | INACTIVE |
| INACTIVE | enter | `UNKNOWN_FRAME` | restored best-effort before compensation | hidden | unchecked | INACTIVE |
| ACTIVE | exit | `RESTORED_WINDOWED` | restored before compensation | hidden | unchecked | INACTIVE |
| ACTIVE | exit | `REENTERED_FULLSCREEN` | remains hidden | hidden | checked | ACTIVE |
| ACTIVE | exit | `UNKNOWN_FRAME` | restored best-effort before compensation | hidden | unchecked | INACTIVE |
| any | close | not applicable | discarded | disposed | unchecked/disabled | CLOSED |

Entry order:

1. Return immediately if unavailable, closed, or already transitioning.
2. Capture the viewport baseline and arm the hold.
3. Capture the frame state and chrome before mutation.
4. Hide chrome.
5. Call `applyScreenFill` once.
6. Map survival before compensation. On re-entry, keep chrome hidden; on a restored or unknown
   frame, restore chrome first.
7. Compensate against the final geometry. On successful entry focus the canvas, show the hint,
   check the menu item, and publish active state.

Exit order:

1. Return immediately unless active and not already transitioning.
2. Capture a fresh viewport baseline, including user pan/zoom made in presentation mode.
3. Call `restoreFrameState` once.
4. Map survival before changing chrome. On a restored window, restore chrome first; on a failed
   exit that re-entered fullscreen, keep chrome hidden and ownership active.
5. Compensate against the final geometry. Hide the hint on every exit attempt; update menu and
   mode state according to the table; focus the canvas.

### 3.3 Exception boundary and surviving-state fallbacks

The complete capture/hide/frame/restore/compensation/bookkeeping sequence is inside one
transition exception boundary. The boundary catches `RuntimeException` and `Error` where the
existing EDT policy requires logging and recovery; no failure escapes to the EDT.

- Failure before `applyScreenFill`, including viewport capture, frame capture, chrome capture, or
  partial hide: restore chrome best-effort, compensate against restored geometry, hide hint,
  uncheck the menu, report inactive, and log the primary and secondary failures.
- Entry failure after `REENTERED_FULLSCREEN`: keep fullscreen ownership. Chrome remains hidden,
  the menu remains checked, the mode remains active, the hint is hidden if it failed, and the
  exit path remains available. Never attempt frame restoration from this path.
- Entry failure after `RESTORED_WINDOWED` or `UNKNOWN_FRAME`: restore chrome best-effort,
  compensate, hide hint, uncheck the menu, report inactive, and log.
- Exit failure after `RESTORED_WINDOWED`: restore chrome best-effort, compensate, hide hint,
  uncheck the menu, report inactive, and log.
- Exit failure after `REENTERED_FULLSCREEN`: keep chrome hidden and menu checked, compensate,
  hide hint, remain active, focus the canvas, and log the failed exit. The user may retry.
- Exit failure after `UNKNOWN_FRAME`: restore chrome best-effort, compensate, hide hint,
  uncheck the menu, report inactive, and log.

`ScreenFillSurface` itself performs at most one inverse raw frame attempt. The mode does not
perform any additional raw frame operation. Chrome restoration is per component and can
continue after an individual component failure. A partially installed chrome snapshot is kept
when it protects a restored layout and discarded only when no usable capture exists.

## 4. Sidebar Override

### 4.1 Captured fields and ownership

`PresentationChrome.capture()` captures the effective state before hiding and installs one
transient override:

```text
sidebarOverride.collapsed       = sidebar.isCollapsed()
sidebarOverride.dividerLocation = splitPane.getDividerLocation()
sidebarOverride.dividerSize     = splitPane.getDividerSize()
sidebarOverride.enabled         = splitPane.isEnabled()
```

The override is not a workspace setting and is never serialized. It remains installed through
entry, exit, late refreshes, revalidate/layout events, and chrome restoration. It is released
only by a genuine sidebar user action, by `discard()` on close, or by the explicit restore
lifecycle after the user has taken ownership. Automatic events never release it.

### 4.2 Exact three-step `applySidebarSettings` rule

`applySidebarSettings(DisplaySettings settings)` must execute this decision before any sizing
or Swing mutation:

1. If the model is presentation-hidden, return without mutation.
2. Else if a sidebar override is present, apply the override fields, not `DisplaySettings`.
3. Else apply the current `DisplaySettings` values, preserving existing read-only session
   overrides and existing width/minimum calculations.

The override application uses `runWithSidebarGuard`, sets the captured collapsed state,
divider size, enabled state, effective divider location, selected View maps-sidebar state,
`appliedSidebarWidth`, minimum sizes, and pending flags. A zero-width split pane may defer the
actual first layout application, but the override remains installed until that layout event.

### 4.3 Exact release boundaries

All genuine sidebar toggle controls route through one method:

```java
void sidebarToggledFromUser(boolean collapsed);
```

That method releases the override first and only then invokes the existing mutation/commit path.
The three controls are:

1. The expanded sidebar collapse button, currently `GraphWorkspaceWindow.java:467`, wired to
   `sidebarToggledFromUser(true)`.
2. The collapsed rail restore button, currently owned by `MapSidebarPanel`/`MapSidebarRail`,
   with the panel's own restore listener removed and the model wired to
   `sidebarToggledFromUser(false)`.
3. The View menu maps-sidebar `JCheckBoxMenuItem`, currently
   `GraphWorkspaceWindow.java:1355`, wired to
   `sidebarToggledFromUser(!sidebar.isCollapsed())`.

The `MapSidebarPanel.CollapsedListener` callback remains a programmatic notification. It must
not release the override and must not be treated as a second user path.

Divider release occurs only at these action boundaries:

- A left-button press on the divider releases before `sidebarGestureActive = true` is set.
  This is the existing divider `MouseListener` at `GraphWorkspaceWindow.java:1237-1246`.
- A left-button double-click releases before `resetSidebarWidth()` is invoked.
- The split pane keyboard divider actions (`selectMin`, `selectMax`, and divider arrow-key
  actions) are wrapped so the wrapper releases before invoking the original action.

The `JSplitPane.DIVIDER_LOCATION_PROPERTY` listener remains release-free. Automatic Swing
layout can fire it during resize and must never release the override. The no-gesture focus-loss
path at `GraphWorkspaceWindow.java:1302` also remains release-free and must not commit while
the override is installed.

An actual change to `mapSidebarWidth` or `mapSidebarHidden` in a newly applied presentation,
compared with the previously stored presentation value, is also an authorized release boundary.
A presentation refresh that merely re-delivers the captured values is not.

### 4.4 Pre-mutation command guards

The following methods must return before any field, component, session setting, or command
mutation when either `presentationHidden` is true or `sidebarOverride` is present:

```java
void setSidebarCollapsed(boolean collapsed);
void commitSidebarWidth(int location);
void resetSidebarWidth();
```

`commitSidebarDisplaySettings(int width, boolean hidden)` is reachable only after these guards
and after a genuine user release. Automatic layout, refresh, revalidate, focus loss without a
gesture, and transition-generated divider events therefore cannot call
`GraphCommands.display(...)`.

The direct user method releases first, then calls `setSidebarCollapsed`; the user action is
therefore not blocked by the guard. Existing clamping, minimum-width checks, read-only session
behavior, and one-commit semantics remain unchanged after release.

## 5. Viewport Hold

### 5.1 Capture fields and compensation formula

At each transition start, `PresentationViewport.capture()` records on the EDT:

```text
P0 = graphScrollPane.getViewport().getViewPosition()  // integer x/y
E0 = graphScrollPane.getViewport().getExtentSize()    // integer width/height
W0 = canvas.getSize()                                  // integer width/height
A0 = canvas.viewport()                                 // immutable GraphViewport
```

It sets `suppressViewportEvents` for the hold, increments the transition generation, and
records the last transition geometry time. It is called once for entry and freshly once for
exit. Exit capture is never replaced by the entry baseline.

For current `W1` and `E1`, compensate each axis with the exact formula:

```text
P1 = round(P0 + ((W1 - W0) - (E1 - E0)) / 2)
```

The calculation is performed in double precision before `round`, then clamped to:

```text
0 <= P1 <= max(0, W1 - E1)
```

The clamped integer is applied under viewport-event suppression. The formula is recomputed
from the original baseline, never from the last compensated position, so an intermediate
integer adjustment cannot compound. `lastPublishedViewport` is reconciled to
`canvas.visibleViewport()` without executing a command. If the unclamped target is outside the
range, log the residual with the requested and clamped positions; the content edge remains
pinned and this is the only documented center deviation. Zoom is never changed.

### 5.2 Ordered classification rule

Every `JViewport` change received while the hold is armed is classified in this exact order:

0. **Model-application guard.** If `applyingViewport` is true, ignore the event. Do not
   publish, disarm, or schedule a user correction.
1. **Anchor.** If `canvas.viewport()` differs from `A0`, classify as genuine user pan, wheel
   zoom, fit, reset, or toolbar viewport action. Publish through the normal path first,
   reconcile the dedupe cache, and disarm.
2. **Positioning-active.** If `canvas.isViewportPositioningActive()` is true and the anchor is
   unchanged, classify as canvas-owned programmatic positioning. Ignore the event, keep the
   hold armed, and register the post-clamp settle correction for the current generation.
   Pending clamps alone do not qualify.
3. **User scroll.** If anchor, extent, and surface size are unchanged, the position moved, and
   an explicit user-scroll input marker is active, classify as scrollbar, pane-wheel, or pane-keyboard
   scrolling. Publish first, reconcile, and disarm.
4. **Geometry.** If extent or surface size changed, or the position moved without an explicit
   user-scroll input marker, classify as transition/layout geometry. Compensate against the
   original baseline and keep the hold armed. AWT `ViewportLayout` can emit position-only events
   between extent/surface changes outside the canvas positioning marker; those never publish.

The model marks scrollbar mouse presses (including arrow buttons) before the UI handles them,
keeps the marker through release/final adjustment, and treats adjusting scrollbars as user input.
Pane wheel dispatch has a transient marker; canvas wheel zoom retains its existing anchor path.
Pane arrows, Page Up/Down, Home/End have a transient marker only while the pane itself has focus,
including modified navigation bindings. Markers clear after their input dispatch/gesture.

Anchor comparison precedes the positioning marker so a user pan whose callback runs while the
canvas marker is active is still treated as user input. The implementation must not use
`canvas.isProgrammaticViewportChange()` for step 2 because it includes pending clamps and
would suppress a user scrollbar/keyboard action during a pending-clamp interval.

The direct publication methods are special entry points:

- `publishCanvasViewport` publishes its current visible viewport first, then calls
  `canvasPublication()`.
- `publishToolbarViewport(GraphViewport value)` publishes `value` first, then calls
  `toolbarPublication(value)`.

Both methods reconcile `lastPublishedViewport` and disarm after the publish. A dedupe cache
must not be updated before the command is emitted.

### 5.3 Publish, reconcile, settle, and disarm lifecycle

`compensate()` applies the absolute target and then reconciles the publication cache. It never
calls `GraphCommands.viewport(...)` and never changes the workspace document.

When step 2 observes active positioning, the model calls:

```java
canvas.runWhenViewportSettled(generationCheckedCorrection);
```

The callback is registered at most once for the current transition generation; a new
registration replaces the previous one. If the canvas is already settled, the callback runs
immediately. The callback checks that the mode is open, the hold is armed, and its captured
generation equals the current generation, then calls `compensate()` once more. This absolute
re-correction handles the odd-shrink residual sequence `37 -> 37.5 -> 38` and also handles a
clamp that completes without a final viewport event.

`disarm()` cancels the settle registration and the quiet-window chain, reconciles
`lastPublishedViewport` first, then clears the armed flag. It emits no command. User disarm
must never undo or snap back the just-published user view.

The quiet-window rule is the only timer-based disarm rule:

- Start the generation-stamped quiet observation after the last geometry operation.
- A quiet interval is 200 ms with no frame-bounds change and no viewport event.
- Never disarm before 2,500 ms have elapsed from the transition's last geometry operation,
  even if an earlier quiet window exists.
- At or after 2,500 ms, disarm on the first qualifying quiet window.
- If no qualifying quiet window occurs by 5,000 ms, disarm at the cap and log a settlement
  failure with the generation and last observed geometry/event times.

The rule is identical for entry and exit. A new transition increments the generation and
cancels the prior chain. Window close cancels it permanently. The hold remains armed through
`FrameResynchronizer`'s possible ten 200 ms retries and all late revalidate/resizing work.

### 5.4 Viewport preservation invariants

- Entry preserves the visible center and zoom from the instant before F11/menu entry.
- Exit preserves the visible center and zoom from the instant exit begins, including all user
  pan/zoom made while active.
- Geometry events publish no viewport command while armed.
- Model-owned viewport applications publish no viewport command and do not disarm.
- A user anchor change or user scroll publishes exactly as baseline and disarms.
- Within the 2,500 ms horizon, late geometry cannot move the visible center except for a logged
  scroll-boundary clamp and cannot publish a command.
- A residual beyond the horizon is a logged settlement failure, not an unreported success.

## 6. Error Handling and Edge Cases

1. **Unavailable platform:** macOS keeps the mode inactive. View item is disabled and unchecked;
   F11 and Escape fallback do nothing; no frame/chrome/viewport/hint/command operation occurs.
2. **Rapid or repeated toggle:** a transition guard makes F11/menu/escape idempotent while a
   transition is in progress. No second frame operation starts.
3. **Exit while inactive:** `requestExit()` is a no-op.
4. **Close during transition:** `closeOnEdt` sets `closed`, calls `windowClosed`, cancels hint
   and scheduler work, cancels the settle callback, disarms the hold, discards chrome/frame
   snapshots, and disposes the frame. Late callbacks see `CLOSED` or an invalid generation.
5. **Failure before frame application:** restore any captured/partially hidden chrome
   best-effort, compensate against the restored geometry, hide hint, uncheck menu, report
   inactive, and log.
6. **Failure after entering fullscreen:** retain fullscreen ownership. Do not restore frame;
   keep chrome hidden and menu checked, report active, and leave F11 available for exit.
7. **Failed exit that remains fullscreen:** keep chrome hidden and menu checked, hide hint,
   compensate against fullscreen geometry, stay active, and log so the user can retry.
8. **Unknown frame result:** restore chrome best-effort, compensate, hide hint, uncheck menu,
   report inactive, and log a warning.
9. **Bounded frame recovery:** each protected frame operation makes at most one inverse raw
   attempt. Secondary failures are logged and never retried by the mode.
10. **Clamp residual:** if content is smaller than the extent or the target exceeds the new scroll
    range, clamp to the valid range, keep the content edge pinned, and log the unclamped target,
    applied target, and residual. Do not re-anchor the canvas or change zoom.
11. **Zero-width restore:** if the split pane has width zero at restore time, retain the sidebar
    override; the first real layout event applies the captured divider/collapse state.
12. **Maximized/extended window:** capture the exact extended state bits before `dispose()` and
    restore them exactly. For a saved state that includes `MAXIMIZED_BOTH`, assert that the
    restored frame covers the work area of its graphics configuration and stays within the screen,
    within the documented X11 AWT coordinate-cache tolerance; a pixel-exact AWT bounds comparison
    is not the acceptance target (the window manager owns maximized outer geometry). Non-maximized
    windows assert the exact captured bounds.
13. **Multiple monitors:** use `getGraphicsConfiguration().getBounds()` from the live frame so
    entry fills the monitor currently occupied by the window.
14. **Hidden settings panel:** restore it hidden and keep `toolbar.setSettingsVisible` consistent.
15. **Collapsed/rail sidebar:** restore collapsed state, effective divider, divider size, and
    enabled state; the next genuine toggle releases the override and commits normally.
16. **Automatic layout and focus loss:** divider-property, resize, refresh, revalidate, and
    no-gesture focus-loss events do not release the sidebar override or emit display commands.
17. **Late geometry:** classify and compensate against the original baseline while armed;
    generation guards prevent stale work from re-correcting a later transition.
18. **Pending-clamp overlap:** a scrollbar or keyboard scroll with pending clamps but no active
    positioning is a user action under step 3, publishes, and disarms.
19. **Toolbar action while armed:** direct toolbar publication publishes first and disarms;
    subsequent geometry cannot snap the toolbar view back.
20. **User action after entry:** a pan/zoom may disarm the entry hold; exit still captures the
    current view afresh and preserves it through exit geometry.
21. **Parentless dialogs:** existing purge/close/contributor dialogs created with a null owner
    are not disposed by the temporary frame transition. A focused modal dialog receives keys,
    so F11 is not expected to fire beneath it.
22. **Popup placement:** existing `FrameResynchronizer` remains installed; X11/KWin popup
    placement must be verified after an entry/exit cycle.
23. **Read-only workspace:** behavior is identical and no command is executed.
24. **Hint lifecycle:** timer expiry after exit, close, or disposal is ignored; no disposed hint
    is re-added or shown.
25. **No workspace mutation:** no `GraphCommands.display(...)`, `GraphCommands.viewport(...)`,
    `setSidebarCollapsed` persistence, `commitSidebarWidth`, `commitSidebarDisplaySettings`,
    dirty flag, or other workspace command may result from presentation or late geometry.

## 7. Test Plan

All new tests remain Java 8-compatible and run on the EDT where Swing requires it. Tests that
assert no workspace mutation record calls at the `GraphWorkspaceHandle.execute(GraphCommand)`
boundary. Every mutation test must be falsifiable: inject the forbidden command through the
same boundary and verify the test fails.

### 7.1 `GraphPresentationModeShould`

Add `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphPresentationModeShould.java`.
Use headless fakes for `ScreenFillSurface`, `PresentationChrome`, `PresentationViewport`,
and `PresentationHint`, an injectable availability flag, an observable menu item, a fake
focus callback, and an exception-recording logger. Include these named methods:

- `enterCapturesBeforeMutationAndActivatesAfterFullscreenSurvival`
- `enterRestoresChromeAndStaysInactiveWhenFrameRecoveryLeavesWindowed`
- `enterRestoresChromeAndStaysInactiveWhenFrameSurvivalIsUnknown`
- `exitRestoresChromeAndDeactivatesAfterWindowedSurvival`
- `exitKeepsFullscreenOwnershipWhenRecoveryReentersFullscreen`
- `exitRestoresChromeAndDeactivatesWhenSurvivalIsUnknown`
- `frameRecoveryAttemptsAtMostOneInverseOperation`
- `failureBeforeFrameApplicationRestoresChromeAndCompensates`
- `failureAfterFullscreenEntryKeepsChromeHiddenAndModeActive`
- `failureDuringExitBookkeepingNeverEscapesToTheEdt`
- `unavailableModeDoesNothingAndLeavesMenuUnchecked`
- `requestExitDoesNothingWhenInactive`
- `closeDisposesHintDiscardsSnapshotAndInvalidatesCallbacks`
- `rapidToggleDuringTransitionRunsOnlyOneTransition`

The tests must assert operation order, surviving-state mapping, no exception escape, bounded
raw-call count, menu state, focus callback, and logger output.

### 7.2 `GraphWindowChromeShould`

Add `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWindowChromeShould.java`.
Use real Swing components and a real `JSplitPane`; use headless model fakes only for workspace
command capture. Include:

- `hideMakesAllChromeInvisibleAndRemovesDividerSize`
- `restoreReturnsVisibilityDividerStateAndCollapsedStateExactly`
- `captureUsesEffectiveStateWhenSettingsDifferFromDisplaySettings`
- `automaticRefreshLayoutAndFocusLossCannotOverwriteSidebarOverride`
- `expandedCollapseButtonReleasesOverrideAndCommitsOnce`
- `collapsedRailRestoreButtonReleasesOverrideAndCommitsOnce`
- `mapsSidebarMenuToggleReleasesOverrideAndCommitsOnce`
- `dividerPressReleasesOverrideBeforeDrag`
- `dividerDoubleClickReleasesOverrideBeforeReset`
- `keyboardDividerActionReleasesOverrideBeforeMutation`
- `dividerPropertyChangeDoesNotReleaseOverride`
- `noGestureFocusLossDoesNotReleaseOverrideOrCommit`
- `zeroWidthSplitPaneRestoresOnFirstLayout`
- `presentationEnterAndExitEmitNoWorkspaceCommandInWritableMode`
- `presentationEnterAndExitEmitNoWorkspaceCommandInReadOnlyMode`
- `displaySettingsAndViewportCommandMutantsFailTheNoCommandAssertion`

The override test must intentionally make captured effective state A differ from persisted
settings B and deliver automatic presentation refresh, canvas-state delivery, resize, focus
loss, and a painted layout-induced divider event. Each of the authorized releases is a separate
case and must produce normal behavior with exactly one expected commit.

### 7.3 Viewport probes and named methods

The same `GraphWindowChromeShould` class or a dedicated package-private probe class may host
real Swing viewport probes. The probes must use a large, panned, non-origin graph fixture and
cover both canvas-size/extent parity combinations:

- `preservesCenterForEvenCanvasAndEvenExtentGeometry`
- `preservesCenterForOddCanvasAndEvenExtentGeometry`
- `preservesCenterForEvenCanvasAndOddExtentGeometry`
- `preservesCenterForOddCanvasAndOddExtentGeometry`
- `compensatesLateExtentChangeAfterTransitionReturns`
- `recomputesAbsolutelyAfterOddShrinkWithoutCompounding`
- `logsClampResidualAndPinsContentEdge`
- `unchangedCanvasDeliveryDoesNotPublishOrMoveCenter`
- `classifiesAnchorChangeAsUserBeforePositioningMarker`
- `compensatesPositionOnlyLayoutEventWithoutPublishingOrDisarming`
- `classifiesScrollbarDragAsUserWhenExtentAndSurfaceAreUnchanged`
- `scrollbarPressPublishesOnceBeforeUiAdjustmentOnEitherAxis`
- `adjustingScrollbarPublishesOnceOnEitherAxisAndClearsMarker`
- `classifiesPaneWheelScrollAsUserAndClearsTransientMarker`
- `keyboardPaneNavigationPublishesOnceOnlyWhenPaneHasFocus`
- `classifiesKeyboardScrollAsUserWhenPendingClampExistsWithoutActivePositioning`
- `ignoresActiveProgrammaticPositioningAndKeepsHoldArmed`
- `toolbarPublicationPublishesBeforeDisarm`
- `canvasPublicationPublishesBeforeDedupeReconcileAndDisarm`
- `settleCallbackCorrectsAfterClampWithoutFinalViewportEvent`
- `settleCallbackRegistrationIsIdempotentPerGeneration`
- `settleCallbackRunsImmediatelyWhenAlreadySettled`
- `userDisarmCancelsSettleCallback`
- `closeCancelsSettleCallbackAndIgnoresLateGeneration`
- `modelAppliedViewportDoesNotPublishOrDisarm`
- `exitRecapturesCurrentViewAfterEntryHoldDisarmed`
- `publishedViewportIsNotSwallowedByReconcileBeforeDisarm`

Use `P0 = round(P0 + ((W1 - W0) - (E1 - E0)) / 2)` as the oracle and assert visible center
within half a device pixel except for the explicitly logged clamp.

### 7.4 Quiet-window scheduler tests

Use the injected fake scheduler and named methods:

- `holdNeverDisarmsBeforeTwoThousandFiveHundredMilliseconds`
- `holdDisarmsOnFirstQuietWindowAtOrAfterPlatformHorizon`
- `holdReleasesAtFiveSecondCapAndLogsSettlementFailure`
- `newTransitionCancelsPreviousQuietWindowGeneration`
- `closeCancelsQuietWindowGeneration`
- `disarmReconcilesWithoutPublishing`
- `subsequentIndependentResizeUsesBaselineAfterHoldDisarms`

The fake scheduler must prove the hold remains armed for the complete 2,500 ms horizon, that
a quiet 200 ms window is required after the horizon, and that the 5,000 ms cap logs failure.

### 7.5 `GraphInteractionControllerShould`

Modify `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphInteractionControllerShould.java`:

- Preserve `cancelConnectionPreviewBeforeClearingSelection` unchanged and green.
- Add `escapeFallbackRunsWhenPressStartsWithNoPreviewAndNoSelection`.
- Add `escapeFallbackDoesNotRunWhenPressStartsWithPreview`.
- Add `escapeFallbackDoesNotRunWhenPressStartsWithSelection`.
- Add `escapeIsConsumedForPreviewSelectionAndFallbackCases`.

The tests must snapshot the pre-clear selection and prove that the fallback is not invoked after
a preview cancellation or selection clear.

### 7.6 `GraphWorkspaceWindowModelShould`

Modify `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java`:

- `fullScreenMenuItemHasExpectedNameAndTranslationKey`.
- `fullScreenMenuItemIsDisabledAndUncheckedWhenUnavailable`.
- `f11IsBoundOnceOnFocusedWindowAndNoMenuAcceleratorExists`.
- `f11UnavailableActionDoesNothing`.
- `modelFrameRecoveryReportsObservableSurvivalAndBoundedCalls`.

Add display-required tests guarded with `Assume.assumeFalse(GraphicsEnvironment.isHeadless())`.
For `f11MakesWindowUndecoratedAndFillsCurrentScreen` and
`f11RestoresBoundsAndExtendedStateFromMaximizedWindow`, the bounds assertions use the work-area
relationship above (cover the work area, stay within the screen, documented X11 AWT-cache
tolerance) rather than pixel-exact AWT bounds; every other display method keeps its exact-bounds
assertion.

- `f11MakesWindowUndecoratedAndFillsCurrentScreen`.
- `f11RestoresBoundsAndExtendedStateFromNormalWindow`.
- `f11RestoresBoundsAndExtendedStateFromMaximizedWindow`.
- `escapeExitsWhenCanvasHasNoPreviewOrSelection`.
- `panDuringPresentationIsPreservedThroughExit`.
- `canvasHasFocusAfterEntryAndExit`.
- `oneF11PressPerformsExactlyOneToggle`.
- `temporaryDisposeDoesNotClosePresentationMode`.
- `closeDuringTransitionLeavesDisposedWindowWithoutError`.
- `hintShowsOnEntryAndIsRemovedAfterInjectedDelay`.

### 7.7 `MapSidebarPanelShould`

Modify `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/MapSidebarPanelShould.java`:

- `railRestoreButtonHasNoPanelOwnedListener`.
- `programmaticCollapsedListenerRemainsUnchanged`.
- `modelOwnsRailRestoreUserPath`.

The tests must distinguish programmatic `setCollapsed` notifications from user ownership and
must not accept a duplicate action path.

### 7.8 Impacted existing tests and falsifiability

Existing menu-list assertions in `GraphWorkspaceWindowModelShould` must be updated deliberately
for the new View item. Existing sidebar commit tests remain valid and must continue to prove
that genuine user actions commit normally. The existing interaction-controller test must remain
unchanged in its preview-before-selection ordering assertion.

The no-command tests are falsifiable by injecting `GraphCommands.display(...)` and
`GraphCommands.viewport(...)` through the same `GraphWorkspaceHandle.execute` boundary and
asserting failure. The viewport tests are falsifiable by mutating each mechanism independently:

- replace baseline compensation with an accumulated delta;
- classify with `isProgrammaticViewportChange()` instead of
  `isViewportPositioningActive()`;
- reconcile before publishing;
- omit direct toolbar/canvas disarm;
- omit settle callback re-correction;
- remove the 2,500 ms minimum or 5,000 ms cap;
- release the sidebar override from the divider-location property listener;
- remove one of the pre-mutation guards;
- add a second F11 accelerator path;
- make Escape invoke fallback after clearing a selection.

Each mutation must cause a named test to fail. A test that passes because no viewport event or
no clamp actually occurred is insufficient; probes must force the relevant mechanism.

## 8. Files, Translations, and Change Boundary

### 8.1 Exact new paths

Add:

- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/ScreenFillSurface.java`
- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/PresentationChrome.java`
- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/PresentationViewport.java`
- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/PresentationHint.java`
- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphPresentationMode.java`
- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphPresentationModeShould.java`
- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWindowChromeShould.java`

### 8.2 Exact modified paths

Modify:

- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java`
- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/MapSidebarPanel.java`
- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphCanvas.java`
- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphInteractionController.java`
- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java`
- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/MapSidebarPanelShould.java`
- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphInteractionControllerShould.java`
- `freeplane/src/viewer/resources/translations/Resources_en.properties`

Add these English translation keys to `Resources_en.properties`:

```properties
graph_workspace.action.full_screen=Full Screen
graph_workspace.hint.full_screen_exit=Esc or F11 exits full screen
```

Run `gradle format_translation` after the resource edit. No other translation file is part of
this feature; translated fallback behavior uses the existing resource mechanism.

### 8.3 GraphCanvas boundary

No `GraphCanvas` change beyond the two specified additions, `runWhenViewportSettled(Runnable)`
and `isViewportPositioningActive()`, is needed. The compensation uses the existing scroll
viewport, canvas size, viewport model, and the model's suppression flag. Scrollbar/pane input
markers belong to the model. No new canvas command or rendering change is allowed.

## 9. Acceptance Evidence and Open Verification Items

### 9.1 Required acceptance evidence

1. Run the focused graph-plugin tests, including every new named test and impacted existing test,
   with Java `~/.sdkman/candidates/java/21.0.8-zulu` and `gradle :freeplane_plugin_graph:test`.
2. Run the full graph-plugin test suite after the focused tests pass.
3. Capture a real X11 application evidence cycle: from a graph window press F11, record an
   undecorated screen-filling canvas with the exit hint visible, wait for the hint to fade, then
   press Escape and record the restored bounds/chrome and the same visible graph center.
4. During the real cycle, exercise a pan/zoom in presentation mode and prove the changed view is
   preserved through exit.
5. Verify a popup menu after entry and exit is correctly placed and dismisses normally under
   X11/KWin, confirming `FrameResynchronizer` remains effective.
6. Record the workspace command boundary before entry, after exit, and after late geometry; it
   must have zero additional display or viewport commands in writable and read-only modes.
7. Record the viewport probe outputs for both parity classes, the late extent event, odd shrink,
   pending-clamp overlap, publish ordering, settle callback, and quiet-window scheduler.
8. Record injected failure evidence for bounded inverse frame recovery and surviving-state
   fallbacks. A real platform failure is not required; the injection must assert call counts and
   observable frame/chrome/menu state.
9. Run `gradle format_translation` and verify the English resource contains both exact keys.

### 9.2 Open verification items

- Native macOS full-screen behavior is explicitly deferred. Only the v1 negative gate must be
  verified on macOS: disabled View item, no-op F11, no EAWT/native call, and no workspace effect.
- Runtime probes remain required for X11/KWin coordinate-cache behavior after temporary
  `dispose()` calls and for real multi-monitor screen selection.
- Display-required tests depend on an available non-headless display; headless mode must still
  cover all state-machine, exception, command-safety, and viewport-fake behavior.
- The acceptance record must report any settlement failure at or beyond the 5,000 ms cap rather
  than treating the run as a silent pass.
- No implementation may add a second frame-transition path, a menu accelerator for F11, or a
  dormant macOS implementation while closing these verification items.
