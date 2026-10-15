# Graph Workspace — Full-Screen Presentation Mode (design)

- Date: 2026-10-14
- Topic slug: `graph-fullscreen-mode`
- Status: Approved design (user-approved 2026-10-14); revision 13 applies design-review passes 1–12, the user-approved platform scope A(b), and the user-selected strict viewport contract (option S)
- Scope: bundled plugin `freeplane_plugin_graph` only; no Freeplane core behavior change (the only shared file touched is the translation resource that already owns the `graph_workspace.*` keys)
- Platform scope: **Linux and Windows only in v1.** The View item is disabled and F11 is a no-op on macOS; native macOS fullscreen is a deferred follow-up (§4.3).
- Code baseline: `bec1900495` (`plugin/graph-workspace`); every `file:line` reference below is to that commit
- Review evidence: reports `design-review-epoch-1-attempt-{2,3,4,5,6}.md` and `design-review-epoch-1-revision-{6,7,8,9,10,11,12}.md` (passes 1–12). Pass-12 findings: the disarm wording contradicted the quiet-window rule, and the classification's programmatic check counted pending clamps, which could suppress a user scroll during the clamp interval. Revision 13 reconciles the wording and classifies on the positioning-depth accessor only.

## 1. Background

The Graph Workspace window (`GraphWorkspaceWindow`, a standalone top-level `JFrame`) is where
workspace graphs are inspected and edited. Its chrome occupies a substantial part of the
window: a menu bar (File / Edit / View / Maps), a toolbar (`WorkspaceToolbar`), a maps sidebar
inside a horizontal `JSplitPane` (`MapSidebarPanel` + the canvas scroll pane), an optional
settings panel docked east (`WorkspaceSettingsPanel`), and a status bar (`GraphStatusBar`).

Freeplane already has a full screen mode for the mind-map window: `ToggleFullScreenAction`
(F11) makes the main frame undecorated and screen-filling and hides its UI components
(`FrameController.setFullScreen`, `freeplane/src/main/java/org/freeplane/features/ui/FrameController.java:526`).
That mechanism is bound to the main frame and to the main-window map view manager; it does not
apply to plugin-owned windows.

A search of `freeplane_plugin_graph/src` finds no `fullscreen` / `full screen` / `full-screen`
identifier, so this is a new feature. The graph view already supports keyboard interaction
bound on the frame's root pane (`GraphWorkspaceWindow.java:1074` `installWorkspaceHistoryKeys`)
and `F11` is unused in the plugin.

## 2. Goals and non-goals

### Goals

1. **Enter presentation mode with F11** (or the View menu): the graph window becomes undecorated
   and fills the usable screen area of the display it currently occupies (the window manager may
   constrain it to the work area), and all chrome is hidden so the canvas is the only
   visible content.
2. **Exit with F11 or Esc** and restore the exact prior state: window bounds and maximized state,
   each chrome element's visibility, the effective sidebar divider state, and the View menu item
   state. For a window whose saved extended state includes maximized-both, the exact extended state
   must be restored and acceptance asserts that the frame covers the work area of its graphics
   configuration and stays within the screen, within the documented X11 AWT coordinate-cache
   tolerance (window managers own maximized outer geometry); the exact captured-bounds requirement
   applies to non-maximized windows.
3. **Preserve the graph view exactly** across enter/exit: the effective visible center and zoom do
   not change (exact when the integer scroll position allows it, otherwise within half a device
   pixel; a scroll-boundary clamp is the only documented deviation), and no auto-fit is performed.
4. **Never touch the workspace document**: entering or leaving presentation mode must not execute
   any workspace command — display, viewport, or otherwise — and must not modify persisted
   settings, including for geometry that settles after the transition returns.
5. **Fail safely and transactionally**: every failure path leaves the window in a fully
   consistent, still-usable state, with bounded recovery, and no exception escapes to the event
   dispatch thread.

### Non-goals

- No change to the map window's F11 behavior and no refactor of the core `FrameController`.
- No persistence of full-screen state across sessions.
- No auto-fit on entry, no toolbar button, no configurable shortcut.
- No change to any file format, workspace document, or graph interaction semantics other than the
  documented escape fallback (§4.8).
- No re-layout or re-settle of the graph on entry/exit; the layout loop is untouched.
- **No native macOS fullscreen in v1** and no macOS presentation mode at all: the feature is
  gated off there (approved scope A(b)); the async native contract is deferred to a follow-up.

## 3. Behavior contract

| Aspect | Behavior |
| --- | --- |
| Platform | **Linux and Windows.** On macOS `isAvailable()` is false: the View ▸ Full Screen item is disabled and F11 does nothing. |
| Enter | **F11** toggles on; **View ▸ Full Screen** check item toggles on (no toolbar button). The frame becomes undecorated and fills the usable screen area of the display it currently occupies (constrained to the work area where the window manager requires it). Menu bar, toolbar, maps sidebar, settings panel and status bar become invisible. The canvas receives focus. A hint `Esc or F11 exits full screen` appears and fades after ~3 s. |
| Active | The canvas is the only visible content. Zoom, pan, selection, connect, keyboard navigation, context menus and the existing Ctrl+Z / Ctrl+Y bindings behave exactly as before. The effective visible center and zoom are unchanged from the moment of entry; user pan/zoom during presentation is normal and is preserved through exit. |
| Exit | **Esc** when there is neither a connection preview nor a selection, or **F11**, or the View menu item. The frame is decorated again with the exact previous bounds and extended state; every chrome element returns to its prior visibility; the sidebar keeps its effective divider location, size and collapsed/rail state; the effective visible center and zoom are preserved through the geometry change; the hint is removed; the menu item is unchecked. |
| Workspace | Zero mutation. No `GraphCommands.display(...)`, no `GraphCommands.viewport(...)`, no `setSidebarCollapsed`, no `commitSidebarWidth`, no `commitSidebarDisplaySettings`, no dirty flag — including for geometry that settles after the transition returns. Read-only workspaces behave identically. |

### 3.1 Enter (Linux, Windows)

1. If the mode is unavailable (macOS), do nothing and return (§4.3).
2. Capture the scroll baseline and arm the viewport compensation hold (§4.6).
3. Capture the frame state (bounds, extended state) and the effective chrome state **before any
   mutation**.
4. Hide the chrome.
5. Apply the screen-fill transition (§4.2), which is bounded and returns an explicit survival
   result. The whole capture/hide/apply sequence is one exception boundary (§4.1).
6. **Map the survival result before compensating**, so every geometry change is final first:
   - `REENTERED_FULLSCREEN` → the chrome stays hidden; compensate against the fullscreen geometry
     (§4.6); focus the canvas; show the hint; check the menu item; report active.
   - `RESTORED_WINDOWED` or `UNKNOWN_FRAME` → restore the chrome first, then compensate against the
     restored geometry, then report inactive (a warning is logged for the unknown case).
7. The compensation hold stays armed through the late geometry that can follow the transition
   (window-manager placement, resynchronizer work, revalidate, surface resizing), and is disarmed
   only when a genuine user viewport action is classified, when the single quiet-window disarm rule
   permits it (never before the platform horizon, §4.6), or on close. On the failure branches keep
   the hint hidden and the menu item unchecked.
8. Exception fallback follows the surviving surface state, never a blanket rollback:
   - a failure before `applyScreenFill` (viewport capture, chrome capture, partial hide) → restore
     the chrome best-effort, compensate against the restored geometry, report inactive, log;
   - a failure after `REENTERED_FULLSCREEN` (compensation, hint or menu bookkeeping) → **keep
     fullscreen ownership**: the chrome stays hidden, the menu item is checked, the mode reports
     active, the hint failure is logged, and the exit path stays available; the frame state is
     never restored;
   - a failure on the `RESTORED_WINDOWED`/`UNKNOWN_FRAME` branches after frame recovery → restore
     the chrome, compensate, report inactive and log.
   No exception reaches the EDT, and no additional frame operation is attempted beyond the
   surface's bounded recovery.

### 3.2 Exit (Linux, Windows)

1. Capture **or re-capture** the scroll baseline (the user's current view, including any pan/zoom
   made during presentation) and arm the hold. The exit baseline is always freshly captured, so it
   is correct even when an earlier user action disarmed the entry hold.
2. Restore the frame state, with the explicit result `RESTORED_WINDOWED`, `REENTERED_FULLSCREEN`
   (the inverse succeeded), or `UNKNOWN_FRAME` (both failed).
3. **Branch on the survival result before restoring any chrome**, and compensate only after the
   final geometry of the branch:
   - `RESTORED_WINDOWED` → restore the chrome from the snapshot (best-effort per component,
     presentation-hidden marking released in `finally`), compensate against the restored geometry,
     hide the hint, uncheck the menu item, report inactive, focus the canvas.
   - `REENTERED_FULLSCREEN` → keep the chrome hidden, keep the menu item checked, compensate
     against the still-fullscreen geometry, keep the hint hidden, stay active, focus the canvas and
     log the failed exit (the user can retry; F11 remains the exit control).
   - `UNKNOWN_FRAME` → restore the chrome and compensate, hide the hint, uncheck the menu item,
     report inactive, log a warning.
4. The hold remains armed under the same single disarm rule as entry — it is released only by a
   classified user viewport action, by the quiet-window rule (never before the platform horizon,
   §4.6), or on close; unexpected failures inside the boundary are logged and the bookkeeping still
   completes; no exception propagates to the EDT.

## 4. Design

### 4.1 Units and responsibilities

Five small units plus one callback on an existing class. All live in
`org.freeplane.plugin.graph.window` unless noted.

**`ScreenFillSurface`** (new interface, with nested `FrameState` and `Survival` types)

```java
enum Survival { RESTORED_WINDOWED, REENTERED_FULLSCREEN, UNKNOWN_FRAME }

interface ScreenFillSurface {
    FrameState captureFrameState();
    Survival applyScreenFill(FrameState previous);     // never throws; one bounded recovery
    Survival restoreFrameState(FrameState previous);   // never throws; one bounded recovery
}
```

- Raw operations `rawEnterFullScreen()` and `rawRestoreWindowed()` perform exactly the frame
  sequence, may throw, and **never call each other**.
- `applyScreenFill`: run `rawEnterFullScreen()`; on failure run `rawRestoreWindowed()` **once**;
  return `REENTERED_FULLSCREEN`, `RESTORED_WINDOWED` (inverse succeeded), or `UNKNOWN_FRAME` (both
  failed). `restoreFrameState` is symmetric. At most two raw operations per protected call; the
  mode never retries frame operations itself, and failures are logged, never thrown.
- Implemented by `GraphWorkspaceWindow`, which is the `JFrame` itself.

**`PresentationChrome`** (new interface)

- `void capture()` — record the effective state and install the transient sidebar override
  (§4.5). Called immediately before hiding.
- `void hide()` — hide all chrome and mark the presentation hidden (idempotent).
- `void restore()` — re-apply the captured state exactly, best-effort per component, releasing the
  presentation-hidden marking in a `finally` block. The override stays installed (§4.5).
- `void discard()` — drop the snapshot and the override (window close or unusable capture).
- Implemented by `GraphWorkspaceWindowModel`.

**`PresentationViewport`** (new interface, §4.6)

- `void capture()` — record the exact baseline `P0`, `E0`, `W0` and the anchor `A0 = canvas.viewport()`,
  and arm the hold, holding viewport publication suppressed while it is armed. It is called at
  **entry and again at exit**, so the exit baseline is the user's current view even if an earlier
  user action disarmed the entry hold.
- `void compensate()` — idempotent and re-runnable: recompute and apply the **absolute** compensated
  position from the baseline against the current geometry, reconcile the publication dedupe cache
  without a command, and log a scroll-boundary clamp. It also runs once more when the canvas
  reports that its programmatic positioning has fully settled (§4.6).
- `void classify(ChangeEvent)` / direct publication entry points — user viewport actions disarm:
  the canvas's `publishCanvasViewport` and the toolbar's `publishToolbarViewport` treat their own
  entry as a user action (publish first, then reconcile and disarm), and the viewport change
  listener classifies scroll/geometry changes per §4.6.
- Settle-callback lifecycle: `runWhenViewportSettled` registration is idempotent per transition
  generation (a new registration replaces the previous one and fires immediately if the canvas is
  already settled), and it is cancelled by `disarm()` and by `windowClosed()`; the callback is
  generation-checked, so a stale registration can neither re-correct nor re-arm after a new
  transition or close.
- Implemented by `GraphWorkspaceWindowModel`, which already owns the scroll viewport, the canvas,
  the scrollbars and the `suppressViewportEvents` publication flag.

**`PresentationHint`** (new class)

- Hosted by the frame's layered pane, top-centre, above the canvas.
- `void show()` / `void hide()` / `void dispose()`.
- A non-focusable, mouse-transparent panel (`contains(int,int)` returns `false`) so it can never
  intercept canvas gestures; hidden by a timer after 3 000 ms by default.
- The delay and the timer seam are injectable so tests are deterministic and do not sleep.

**`GraphPresentationMode`** (new class)

- `boolean isAvailable()` — constructor-injected `supported` flag, defaulting to
  `!Compat.isMacOsX()`; false disables everything (§4.3).
- `void toggle()`, `boolean isActive()`, `void requestExit()`, `void windowClosed()`.
- Owns the transition state machine, the exception boundary around every transition and the
  snapshot lifecycle (created at capture, restore reference cleared after a successful restore,
  override retained until user intent, discarded on close).
- **Exception boundary:** every transition step — viewport capture, chrome capture, chrome hide,
  frame application, chrome restore, viewport compensation — runs inside one `try` whose fallback
  follows the **actual surviving surface state** as specified in §3.1–§3.2 (in particular, a
  failure after `REENTERED_FULLSCREEN` retains fullscreen ownership instead of rolling the window
  back). On failure the mode restores what it changed (chrome and viewport best-effort), logs the
  failure and any secondary rollback failure, and never lets an exception reach the EDT.
  Ownership of a partially installed snapshot: kept when the chrome was restored (it protects the
  restored layout), discarded only when no usable capture was installed.
- Closed exactly once from the window's real close path (§4.10); close cancels timers, discards the
  snapshot, disarms the viewport hold and ignores late callbacks.

**`GraphInteractionController`** (modified, package `org.freeplane.plugin.graph.canvas`)

- Gains an optional escape fallback: `void setEscapeFallback(Runnable)` (default `null`).
- The fallback is invoked only when an Escape key press started with neither a connection preview
  nor a selection (§4.8). The existing preview-cancel / selection-clear behavior, the emitted
  `GraphIntent.ChangeSelection(empty)` and the event consumption are unchanged, so the pinned
  `GraphInteractionControllerShould.cancelConnectionPreviewBeforeClearingSelection` behavior holds.

### 4.2 Frame mechanics (Linux, Windows)

Mirrors the proven core sequence (`FrameController.setFullScreenOnNonMac`). Raw operations (never
call each other, may throw):

```
rawEnterFullScreen():
    Rectangle screenBounds = getGraphicsConfiguration().getBounds();
    dispose();
    setUndecorated(true);
    setExtendedState(Frame.MAXIMIZED_BOTH);
    setBounds(screenBounds);
    if (Compat.isWindowsOS()) setResizable(false);
    setVisible(true);
    validate();

rawRestoreWindowed(previous):
    dispose();
    setUndecorated(false);
    setBounds(previous.bounds());
    setExtendedState(previous.extendedState());
    if (Compat.isWindowsOS()) setResizable(false);
    setResizable(true);
    setVisible(true);
    validate();
```

`setVisible(true)` flows through the existing `show()` override, guarded by the window's `closed`
flag (`GraphWorkspaceWindow.java:219`), so a window being closed cannot be re-shown by the
transition. The frame state is captured before `dispose()` because the graphics configuration is
read from the live window. The temporary `dispose()` calls are transition mechanics, not close
events (§4.10). Viewport compensation runs after these geometry changes and continues for every
later geometry event while the hold is armed (§4.6).

### 4.3 Platform availability gate (macOS deferred)

- `GraphPresentationMode.isAvailable()` returns false on macOS v1. The constructor takes the
  supported flag (default `!Compat.isMacOsX()`), so tests can exercise both branches headlessly.
- When unavailable: `toggle()` and `requestExit()` are no-ops (a single debug log), the View ▸
  Full Screen menu item is created but disabled and unchecked, and the F11 root-pane action checks
  availability before acting. No frame operation, chrome change, hint, viewport hold or command is
  ever performed.
- No `Compat.setFullScreenOnMac` call and no EAWT fullscreen listener are registered anywhere in
  v1. The deferred follow-up keeps the `ScreenFillSurface` seam, so a native macOS
  implementation can be added later as its own feature (its observation contract — will-enter/
  will-exit timing, asynchronous completion, pre-geometry viewport capture — is explicitly out of
  scope here).

### 4.4 Chrome visibility

`capture()` records the effective state and installs the sidebar override (§4.5):

| Captured | Source |
| --- | --- |
| Menu bar, toolbar, maps sidebar, settings panel, status bar visibility | `isVisible()` of each component |
| Split enabled state and divider size | `splitPane.isEnabled()`, `splitPane.getDividerSize()` |
| Effective divider location | `splitPane.getDividerLocation()` |
| Sidebar collapsed/rail state | `sidebar.isCollapsed()` |

Hide (all idempotent, after `capture()`): `menuBar.setVisible(false)`,
`toolbar.setVisible(false)`, `sidebar.setVisible(false)` + `splitPane.setDividerSize(0)`,
`settingsPanel.setVisible(false)` + `toolbar.setSettingsVisible(false)`,
`statusSlot.setVisible(false)`, and the presentation-hidden marking.

Restore: clear the presentation-hidden marking in `finally`; restore every captured visibility;
set `toolbar.setSettingsVisible(settingsPanel.isVisible())`; inside `runWithSidebarGuard`
(`GraphWorkspaceWindow.java:711`) restore `splitPane.setEnabled(...)`, `sidebar.setCollapsed(...)`,
`splitPane.setDividerSize(...)`, `splitPane.setDividerLocation(...)`,
`mapsSidebarMenuItem.setSelected(...)` and `appliedSidebarWidth`; clear `sidebarApplyPending` and
`sidebarGestureActive`. Each component's restoration is independently best-effort; a failure is
logged and does not stop the others. A zero-width split pane needs no special handling: the
sidebar override (§4.5) makes the first real layout event apply the captured state.

Verified experimentally: with the left component invisible, `BasicSplitPaneUI` gives the right
component the full split width, and re-showing the component plus restoring the divider state
restores the divider location unchanged.

### 4.5 Sidebar override (exact-restore and command safety)

- `capture()` installs a **transient effective sidebar override** containing the collapsed flag,
  divider location, divider size and enabled state.
- The model's `applySidebarSettings` uses this rule:
  1. presentation hidden → do nothing;
  2. override present → apply the override (not `DisplaySettings`);
  3. otherwise → apply `DisplaySettings` (today's behavior).
- **Release requires genuine sidebar input ownership**, at these boundaries:
  1. the three toggle controls, routed through one model method `sidebarToggledFromUser(boolean)`:
     the expanded-sidebar collapse button (`GraphWorkspaceWindow.java:467`), the collapsed rail
     restore button (`MapSidebarPanel.java:35`; the panel's own listener is removed and the model
     wires this button through the same method), and the View menu maps-sidebar item
     (`GraphWorkspaceWindow.java:1355`). The panel's shared `CollapsedListener`
     (`MapSidebarPanel.java:41-47`) stays a programmatic notification and never releases.
  2. **divider input, at its action boundaries** — never from the location property:
     - a left-button press on the divider in the existing `MouseListener`
       (`GraphWorkspaceWindow.java:1237-1246`) releases before `sidebarGestureActive` is set
       (mouse drag);
     - a left-button double-click on the divider releases before `resetSidebarWidth()` is invoked
       (the double-click branch is in the same listener);
     - the split pane's keyboard divider actions (`selectMin`, `selectMax` and the divider's
       arrow-key actions) are wrapped so the wrapper releases before invoking the original action.
     The `DIVIDER_LOCATION_PROPERTY` listener (`GraphWorkspaceWindow.java:1259`) stays
     **release-free**: automatic Swing layout (for example `BasicSplitPaneUI` re-distributing
     space during a resize) can fire it without any user action, so it must never release the
     override. The no-gesture focus-loss path does not change the divider location, so it never
     releases either.
  3. an actual change of the sidebar-relevant display settings (`mapSidebarWidth` or
     `mapSidebarHidden` differs from the previously stored presentation in `applyPresentation`).
- **Command safety before mutation:** `commitSidebarWidth`, `resetSidebarWidth` and
  `setSidebarCollapsed` early-return before any mutation or command while the presentation is
  hidden or the override is installed; `commitSidebarDisplaySettings` is reachable only past that
  guard. The user actions in (1)–(3) release first, so genuine actions commit normally; automatic
  layout-driven location changes and the no-gesture focus-loss path
  (`GraphWorkspaceWindow.java:1302`) cannot mutate or emit a command while the override is
  installed.
- Consequence: a display update or a queued resize/focus event during or after the transition
  re-applies the captured state and cannot overwrite it, no transition- or layout-generated event
  can emit a display command, and the next genuine user action releases the override and commits
  normally.

### 4.6 Viewport preservation (armed compensation hold; no viewport command)

Frame and chrome geometry changes alter the scroll viewport extent. `GraphCanvas.visibleViewport`
(`GraphCanvas.java:118-133`) derives the visible world center from the scroll position and the
extent: `C = A + (P + E/2 − W/2)/z`, where `A` is the canvas anchor, `P` the integer scroll
position, `E` the extent, `W` the canvas surface size and `z` the zoom. Geometry-driven extent
changes move `C`, and the `JViewport` change listener (`GraphWorkspaceWindow.java:462`) would
publish `GraphCommands.viewport(...)` for a writable workspace (`:818-845`, executed at `:863`).
Geometry keeps arriving after a transition returns (window-manager placement, `FrameResynchronizer`
deferred resynchronization, revalidate, and `resizeSurface`'s own compensation), so a one-shot
settle plus release cannot fence it. The mechanism is therefore a **hold**, armed across the
transition, and disarmed by genuine user viewport input or by the quiet-window disarm rule below:

- **Arm:** `PresentationViewport.capture()` records the exact baseline `P0`, `E0`, `W0` and the
  baseline anchor `A0 = canvas.viewport()` at transition start, sets the armed flag, and holds the
  model's `suppressViewportEvents` for the duration.
- **Compensate (idempotent, absolute):** `compensate()` recomputes the scroll position from the
  **original baseline** and the current geometry:
  `P1 = round(P0 + ((W1 − W0) − (E1 − E0)) / 2)` per axis (the exact inverse of the center
  formula, so a pure surface-size change and a pure extent change both leave `C` unchanged),
  clamped to `[0, max(0, W1 − E1)]`, and applies it while suppression is held. Because the value is
  recomputed from the baseline rather than accumulated, the intermediate integer truncation inside
  `resizeSurface` (which adjusts by `ΔW/2`) cannot compound: the applied position stays within half
  a device pixel of the original target, always. `lastPublishedViewport` is reconciled to
  `visibleViewport()` without emitting a command; a clamp is logged. A reentrancy guard prevents
  the position write from recursing into itself.
- **Event classification while armed** — ordered rule, model-application guard first, then anchor:
  0. the model is applying a viewport itself (an `applyingViewport` guard around
     `applyInitialViewport` and the model's `canvas.setViewport` calls): ignore; never publish,
     never disarm;
  1. the **anchor** changed relative to `A0` (`canvas.viewport()` differs) → a genuine user
     viewport action (pan, wheel zoom, fit, reset, toolbar zoom): **publish through the normal path
     first**, then reconcile and disarm — never pre-cache the value, or the dedupe would suppress
     the very command the user action must persist;
  2. `canvas.isViewportPositioningActive()` is true (the canvas's positioning marker is active;
     **not** the combined flag that also counts pending clamps) and the anchor is unchanged → the
     canvas's own programmatic positioning: ignore, keep the hold armed, and schedule the post-clamp
     re-correction. Using the combined `isProgrammaticViewportChange()` here would misclassify a
     user scrollbar or keyboard scroll that arrives during a pending-clamp interval as
     programmatic, suppress it, and then snap it back by compensation;
  3. otherwise the extent and surface size are unchanged → a genuine user scroll (scrollbar,
     keyboard, wheel over the pane): publish first, then reconcile and disarm;
  4. otherwise the extent or surface size changed → geometry: compensate and keep the hold armed.
  Checking the anchor before the programmatic flag is what makes a user pan whose positioning runs
  under the canvas marker still classify as user input.
- **Direct publication paths disarm:** `publishCanvasViewport` (`GraphWorkspaceWindow.java:463`)
  and `publishToolbarViewport` (`:486`) are user viewport actions at their own entry point: while
  the hold is armed they publish the value first, then reconcile and disarm. This covers toolbar
  zoom/fit/reset, which can change the anchor without producing a `JViewport` event, so a later
  geometry change cannot snap the user's toolbar view back.
- **Post-clamp absolute re-correction:** the canvas exposes an explicit settle callback
  (`GraphCanvas.runWhenViewportSettled(Runnable)`, §9) that fires once both its positioning depth
  and its pending clamp count reach zero. The model registers it whenever the canvas's programmatic
  positioning is observed while the hold is armed, and the callback runs `compensate()` once more
  (generation-checked). The canvas's marked integer compensation adjusts by `ΔW/2`, which can leave
  a half-pixel residual after an odd surface shrink (the pass-7 probe: 37 → 37.5 → 38); the
  re-correction recomputes absolutely from the baseline and restores the exact target where the
  geometry permits it (≤ half a device pixel otherwise). A getter poll is not sufficient: clamp
  work can complete without a final viewport event, so the callback is the completion signal.
- **Disarm rule (single definition):** geometry can arrive for the whole horizon over which the
  platform itself retries (`FrameResynchronizer`: up to 10 attempts at 200 ms). After the mode's
  last geometry operation the hold disarms when a 200 ms window passes with no frame-bounds change
  and no viewport event, but **never before 2 500 ms** have elapsed; if no quiet window occurs by
  5 000 ms, the hold is released anyway and the settlement failure is logged. On disarm the mode
  reconciles `lastPublishedViewport` first, so releasing emits nothing. A new transition or window
  close cancels the chain (generation-stamped); the rule is the same for entry and exit.
- **Clamp case:** when the content is smaller than the new extent, or the captured position lies
  outside the new scroll range, `P1` clamps and the content edge stays pinned; this is logged and
  is the only documented deviation. Zoom never changes.
- The captured target is always the view at the start of the transition: at entry, the pre-entry
  view; at exit, the user's current view (including pan/zoom performed while presentation mode was
  active). The view is preserved *through* geometry changes, never rolled back to entry values.
- **Stated residual:** a geometry straggler beyond the platform's own retry horizon (2 500 ms) is
  a settlement failure recorded by the acceptance evidence rather than silently accepted; within
  the horizon no geometry event can move the center or publish.

### 4.7 Keys and menu item

- F11 is bound once, on the root pane's `WHEN_IN_FOCUSED_WINDOW` input map, to a single action that
  checks availability and calls `GraphPresentationMode.toggle()`; the same spring as the existing
  workspace history keys (`GraphWorkspaceWindow.java:1074`). The canvas key listener does not
  consume F11.
- **Single-path invariant:** one F11 press performs exactly one toggle. The View ▸ Full Screen menu
  item does **not** register an accelerator of its own, because a menu accelerator would either be
  a second path or stop working while the menu bar is hidden; the F11 binding remains authoritative
  in both windowed and full-screen states. A test pins the invariant.
- The View menu contains a `JCheckBoxMenuItem` named `graph-workspace-full-screen-menu-item` with
  text key `graph_workspace.action.full_screen`, placed after the Settings item. Its selected state
  follows the mode; it is disabled when `isAvailable()` is false.
- Esc is not bound on the root pane: the canvas owns Escape (§4.8).

### 4.8 Escape semantics

The current handler (`GraphInteractionController.java:370`) consumes Escape and either cancels a
connection preview or clears the selection. The new handler makes the decision explicit:

```java
final boolean hadPreview = previewSource != null;
final boolean hadSelection = canvas.paintState().selection().isPresent();   // BEFORE any clear
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
```

- Esc with a pending connection preview cancels the preview; full screen is unaffected.
- Esc with a selection clears the selection; full screen is unaffected.
- Esc with nothing to cancel calls `GraphPresentationMode.requestExit()`, which exits only when the
  mode is active and does nothing otherwise (including on macOS, where it is unavailable).
- The event is consumed in all cases, exactly as today, and the pinned
  `cancelConnectionPreviewBeforeClearingSelection` behavior is preserved.

### 4.9 Mode state machine

States: `INACTIVE`, `ACTIVE`, `CLOSED`.

```
INACTIVE --enter--> [applyScreenFill -> Survival:
                        REENTERED_FULLSCREEN -> ACTIVE (compensate, hint, menu)
                        RESTORED_WINDOWED    -> INACTIVE (chrome restored, then compensate)
                        UNKNOWN_FRAME        -> INACTIVE (chrome restored, then compensate, warning logged)]
ACTIVE   --exit-->  [restoreFrameState -> Survival:
                        RESTORED_WINDOWED    -> INACTIVE (chrome restored, then compensate, hint/menu cleared)
                        REENTERED_FULLSCREEN -> ACTIVE   (chrome stays hidden, menu stays checked, compensate, logged)
                        UNKNOWN_FRAME        -> INACTIVE (chrome restored, then compensate, warning logged)]
*        --windowClosed--> CLOSED (cancel hint timer, disarm viewport hold, discard snapshot)
```

- The viewport hold is armed by every transition and stays armed after it returns; it is disarmed
  by genuine user viewport input (canvas/toolbar view actions or scrollbar/keyboard scrolls), by
  the quiet-window disarm rule (never before the platform horizon), or by window close (§4.6).
- `toggle()` targets the opposite of the current state; transitions are idempotent and all run on
  the event dispatch thread.
- When `isAvailable()` is false, every entry point is a no-op and the state stays `INACTIVE`.

### 4.10 Lifecycle contract

- `GraphPresentationMode.windowClosed()` is invoked **only** from the real close path
  `GraphWorkspaceWindow.closeOnEdt` (`GraphWorkspaceWindow.java:262`), which already sets the
  window's `closed` flag, closes the model and disposes the frame. It is **not** attached to the
  AWT `WINDOW_CLOSED` event or to a `WindowListener`, so the temporary `dispose()` calls of the
  frame transition can never tear the mode down.
- On close the mode cancels the hint timer, discards the snapshot and the override, disarms the
  viewport hold and moves to `CLOSED`; every later callback is ignored.
- The window's existing `closed` flag continues to guard `show()`.

## 5. Data flow

```
F11 / View menu item
        |
        v
GraphPresentationMode.toggle()  --(isAvailable()==false)--> no-op
        |
        v
  viewport.capture() (baseline P0/E0/W0, hold armed) + chrome.capture()
  chrome.hide()
  applyScreenFill(previous) -> Survival
     REENTERED_FULLSCREEN -> compensate, hint.show(), menu checked, ACTIVE
     RESTORED_WINDOWED    -> chrome.restore(), compensate, INACTIVE
     UNKNOWN_FRAME        -> chrome.restore(), compensate, INACTIVE + warning

exit:
  viewport.capture() re-captures the current view baseline and (re)arms the hold
  restoreFrameState(previous) -> Survival
     RESTORED_WINDOWED    -> chrome.restore(), compensate, hint.hide(), menu unchecked, INACTIVE
     REENTERED_FULLSCREEN -> compensate, chrome stays hidden, menu stays checked, ACTIVE + log
     UNKNOWN_FRAME        -> chrome.restore(), compensate, hint.hide(), menu unchecked, INACTIVE + warning

while armed: viewport change -> classify (model-application guard first, then anchor):
   model-applied viewport (applyingViewport guard)    -> ignore
   anchor changed (user pan/zoom/fit/toolbar)         -> publish, reconcile, disarm
   programmatic canvas positioning, anchor unchanged  -> ignore; schedule post-clamp re-correction
   extent/surface unchanged (user scrollbar/keyboard) -> publish, reconcile, disarm
   extent or surface changed (geometry)               -> compensate (never publish)
post-clamp: canvas settle callback fires -> absolute re-correction from the baseline
toolbar/canvas direct publication while armed -> publish, reconcile, disarm
settled:    quiet 200 ms window, never before 2500 ms (5000 ms cap with logged failure) -> reconcile, disarm
close:      disarm (generation cancels the horizon chain and the settle registration)

Esc (canvas) -> GraphInteractionController
   hadPreview/hadSelection snapshotted first
   neither -> GraphPresentationMode.requestExit() -> exit when active
```

## 6. Edge cases

1. **Double toggle / rapid F11**: transitions are idempotent; an unavailable platform never
   changes state.
2. **Exit without entry**: no-op.
3. **Window closed while the hold is armed**: `windowClosed()` cancels the hint timer, disarms the
   hold, discards the snapshot and ignores late callbacks.
4. **Settings panel hidden before entry**: the snapshot keeps it hidden after exit, and the toolbar
   toggle stays consistent.
5. **Maps sidebar collapsed (rail) before entry**: the captured collapsed flag and effective
   divider location are restored directly; all user controls release the override and work
   normally afterwards.
6. **Maximized or otherwise extended window**: the extended state bits are restored exactly. The
   window manager owns maximized outer geometry, so acceptance asserts that the restored frame
   covers the work area of its graphics configuration and stays within the screen, within the
   documented X11 AWT coordinate-cache tolerance (the native y may differ from the AWT-cached y
   by the work-area inset); non-maximized windows keep the exact captured-bounds comparison.
7. **Multiple monitors**: the screen bounds come from the frame's current
   `GraphicsConfiguration`, so the window fills the monitor it is on.
8. **Automatic layout, resize or no-gesture focus loss while the override is installed**: the
   override survives (no release without genuine sidebar input) and no command is emitted; only an
   authorized user action releases it.
9. **Late geometry after the transition returns** (window-manager placement, `FrameResynchronizer`
   deferred resynchronization, revalidate, `resizeSurface`): while the hold is armed, each change is
   classified; geometry is re-compensated against the original baseline so the center does not
   drift and no viewport command is published, while a changed anchor or a user scroll is
   classified as user input and publishes normally.
10. **Failure at any transition step**, including capture, partial hide, frame steps, chrome
    restore and viewport compensation: the exception boundary follows the surviving surface state —
    before frame application it rolls back; after `REENTERED_FULLSCREEN` it keeps fullscreen
    ownership (chrome hidden, menu checked, active); on windowed/unknown outcomes it restores the
    chrome and then compensates. Recovery uses at most one inverse raw frame attempt; secondary
    failures are logged, never thrown.
11. **Read-only workspace**: identical behavior; presentation mode executes no command.
12. **Parentless dialogs**: the close/purge dialogs are created with a null owner
    (`GraphWorkspaceWindow.java:1024`), so the transition `dispose()` cannot dispose them. A
    focused modal dialog receives the keystrokes, so F11 cannot fire under it.
13. **Zero-width split pane during restore**: the override makes the first layout event apply the
    captured state.
14. **Popup menu placement**: X11/KWin coordinate-cache resynchronization is already installed via
    `FrameResynchronizer` (`GraphWorkspaceWindow.java:151`); the acceptance evidence must confirm
    that popups still appear correctly after an enter/exit cycle.
15. **macOS**: the View item is disabled and F11 is a no-op; no frame, chrome, viewport or
    workspace effect, and no EAWT/native bridge call is made.
16. **Content smaller than the new extent, or a captured position outside the new scroll range**:
    the compensation clamps and the content edge stays pinned; the deviation is logged. No parity
    padding or surface re-anchoring is performed.
17. **Genuine user viewport input while armed** (pan, wheel zoom, fit, reset, toolbar zoom, a
    scrollbar drag or a keyboard scroll): the event is classified by the anchor/extent rule, the
    hold disarms, and the resulting viewport is published exactly as in the baseline windowed
    behavior — the user's view is never snapped back.
18. **Quiet-window disarm rule**: after the mode's last geometry operation the hold disarms only
    when a 200 ms window passes with no frame-bounds change and no viewport event, never before the
    2 500 ms platform horizon (mirroring `FrameResynchronizer`'s ten 200 ms retries), and at the
    latest by the 5 000 ms cap, which logs a settlement failure; disarm reconciles the dedupe cache
    first, so releasing it emits nothing and baseline window-resize behavior resumes exactly.

## 7. Mockups and accepted user choices

Screens presented through the brainstorming visual companion (files under
`docs/superpowers/specs/mockups/2026-10-14-graph-fullscreen-mode/`):

| Screen | Question | Chosen |
| --- | --- | --- |
| `presentation-chrome.html` | What stays visible in presentation mode? | **A — Canvas only** (menu, toolbar, sidebar, settings, status hidden) |
| `viewport-on-entry.html` | What happens to zoom/position on F11? | **A — Keep the exact view**; exit returns precisely |
| `exit-hint.html` | Show an exit hint on entry? | **A — Fade after ~3 s** |

Clarifying answers given in the terminal: presentation semantics **B** (screen-fill *and* hide
chrome, matching the map view's F11 convention); controls **F11 toggle + Esc exit + View-menu check
item**, no toolbar button; platform scope **A(b)** — Linux/Windows in v1, native macOS fullscreen
deferred.

The mockup README carries a note that the accepted “exit returns precisely to the previous view”
wording means the view is preserved *through* the transition (including pans performed while in
presentation mode), per the normative contract above.

## 8. Test strategy

### 8.1 New tests

**`GraphPresentationModeShould`** (headless; fake surface with observable frame state, fake chrome,
fake viewport, fake hint, injectable availability)

- Enter: viewport baseline and frame state captured before mutation, chrome captured, chrome
  hidden, surface applied, viewport compensated once after the final geometry, canvas focused, hint
  shown, active.
- **Survival-result mapping**: `REENTERED_FULLSCREEN` → active; `RESTORED_WINDOWED` on enter →
  inactive with chrome and viewport restored; `UNKNOWN_FRAME` on enter → inactive, restored,
  warning logged. Exit: `RESTORED_WINDOWED` → inactive with hint hidden and menu unchecked;
  `REENTERED_FULLSCREEN` → **stays active with chrome hidden and menu checked, hides the hint and
  compensates the captured current viewport exactly once, with no chrome restoration**;
  `UNKNOWN_FRAME` → inactive, restored, warning.
- **Bounded recovery**: a fake that fails raw enter and raw restore records exactly two raw frame
  operations, returns `UNKNOWN_FRAME`, and the mode returns without an EDT exception.
- **Exception boundary**: injected failures before a usable capture, during chrome capture, after a
  partial hide, during chrome restore and during viewport compensation: the fake's observable
  frame/chrome/viewport/menu state matches the surviving-state rules (including the
  retained-fullscreen enter case), no exception escapes, and secondary rollback failures are
  logged. A failed enter whose inverse succeeds restores chrome **before** the compensation and
  leaves the mode inactive.
- **Availability gate**: with `supported=false` (default on macOS), `toggle()`/`requestExit()` are
  no-ops, the menu item is disabled and unchecked, no surface/chrome/viewport/hint call is made,
  and the state stays inactive.
- `requestExit()` exits only when active; `windowClosed()` disposes the hint, disarms the hold,
  discards the snapshot and makes every later entry point a no-op.

**`GraphWindowChromeShould`** (headless; real Swing components and a real `JSplitPane`)

- After `hide()`: menu bar, toolbar, sidebar, settings panel and status slot are invisible; the
  split divider size is 0; the canvas scroll pane occupies the full split width.
- After `restore()`: every component is back to its captured visibility; divider location, divider
  size, collapsed state, enabled state and View menu item match the pre-entry state exactly.
- **Override survival with A different from B**: capture effective state A while the persisted
  settings say B; drive an automatic presentation refresh, a canvas-state delivery, a no-gesture
  focus-loss, a resize/pending path **and an automatic layout-induced divider-location change**
  (painted split pane); assert the captured A survives and **zero** commands were executed. Then,
  as separate cases, release with each of: the three toggle controls, a divider mouse press/drag,
  a divider double-click, and a keyboard divider action — each applies normally with exactly one
  commit. A double-click as the first action after restoration restores the default width with
  exactly one writable commit, and session-only behavior is kept for read-only workspaces.
- **Viewport preservation (armed hold)**: with a large, panned, non-origin fixture:
  (a) hide/show the sidebar and settings and resize the frame for **both canvas/extent parities**;
  (b) deliver a **late extent change after the transition method returns** (the pass-7 probe: a
  +200 px extent change must leave the captured center unchanged and emit zero commands);
  (c) apply an odd surface shrink after an initial rounded compensation (the probe that moved
  37→38 must stay at 37, proving absolute recomputation with no compounding);
  (d) a target near the old scroll limit with the graph fitting the new extent, and an empty graph
  (clamp logged, content edge pinned);
  (e) an otherwise unchanged canvas-state delivery after the transition leaves the center stable
  and emits nothing.
  Every case asserts the effective center and **zero** commands of any type on
  `GraphWorkspaceHandle.execute`.
- **Classification while armed**: a user pan, wheel zoom, fit/reset and a toolbar zoom/fit/reset
  (the anchor changes, and the publication callback fires after the mutation) each disarm and
  publish exactly one viewport command — the user's view is never snapped back; a scrollbar drag
  and a keyboard scroll (extent/surface unchanged) do the same; a programmatic canvas clamp is
  ignored and never disarms; after disarm, further external scroll changes publish as baseline.
- **Post-clamp re-correction**: the pass-7 odd-extent/odd-shrink probe (37 → 37.5 → 38) ends at 37
  because the canvas's settle callback triggers the absolute re-correction, including when the
  clamp completes without a final viewport event.
- **Settle-callback lifecycle**: registration is idempotent per transition, fires immediately when
  the canvas is already settled, is cancelled by user disarm and by close (no callback afterwards,
  no leaked registration), and a stale-generation callback is a no-op.
- **Toolbar path disarm**: a toolbar zoom/fit/reset while armed publishes exactly one command and
  disarms, and a later geometry change does not snap the view back (no JViewport event is needed).
- **Pending-clamp overlap**: a user scrollbar or keyboard scroll delivered while the canvas has
  pending clamps but no active positioning (`isViewportPositioningActive()` false) is classified as
  user input, publishes exactly one command and disarms, and is never snapped back by compensation.
- **Quiet-window disarm rule**: with a fake scheduler, the hold disarms on the first quiet 200 ms
  window at or after 2 500 ms, never earlier, and at the 5 000 ms cap with a logged settlement
  failure if no quiet window occurs.
- **Exit after a user action**: a pan or zoom during presentation (which disarms the entry hold)
  followed by exit re-captures the current view as the exit baseline and preserves it across the
  exit geometry.
- **Publish-before-disarm ordering**: a user pan or scroll while armed emits exactly one viewport
  command (the value is published before the dedupe baseline is updated); the previous
  reconcile-first ordering would have swallowed it.
- **Platform horizon disarm (fake scheduler)**: the hold stays armed through the full 2 500 ms
  horizon, disarms only after a quiet final window, releasing it emits nothing, and a subsequent
  independent window resize behaves as baseline.
- **Model-applied viewport**: a programmatic viewport application by the model while armed
  (applyInitialViewport / setViewport) neither disarms the hold nor publishes.
- Zero-width restore applies on the first layout, including after an intervening automatic refresh.
- **No-command assertion on the real boundary**: record `GraphWorkspaceHandle.execute(GraphCommand)`,
  checkpoint after fixture initialization, run a real model/chrome/viewport enter → exit, deliver
  late geometry while armed, and assert no additional command of any type in both writable and
  read-only modes. Prove falsifiability by injecting `GraphCommands.display(...)` and
  `GraphCommands.viewport(...)` through the same boundary and verifying the test fails.

**`GraphInteractionControllerShould`** (headless; extends the existing class)

- `cancelConnectionPreviewBeforeClearingSelection` remains green unchanged.
- The escape fallback is invoked when the key press starts with neither a preview nor a selection.
- The fallback is **not** invoked when the press starts with a preview, nor when it starts with a
  selection (proving the pre-clear snapshot).
- Escape is consumed in every case.

**`GraphWorkspaceWindowModelShould`** (existing class; mix of headless and display-required)

- Headless: the View ▸ Full Screen item exists with the expected name and text key, is disabled
  and unchecked when availability is false, toggles and tracks the selected state when available;
  F11 is bound on the root pane's `WHEN_IN_FOCUSED_WINDOW` input map to a single action that
  no-ops when unavailable.
- **Concrete-surface rollback seam**: the raw frame operations are injectable (or an equivalent
  concrete harness), so disabling the real recovery or reordering raw steps fails a test; the
  injected failure leaves observable frame/decoration state that is asserted, including the exact
  bounded raw-call count and the returned `Survival`.
- Display-required (`Assume.assumeFalse(GraphicsEnvironment.isHeadless())`, matching existing
  tests): F11 makes the frame undecorated and screen-filling; F11 or Esc restores the exact
  bounds/extended state (including from a maximized state) and the same effective visible
  center; a pan performed while full screen is preserved through exit; the canvas has focus in both
  states; one F11 press performs exactly one toggle.
- Display-required: the mode, chrome and hint survive both temporary `dispose()` calls of an
  enter/exit cycle, and a close during a transition leaves a disposed window without errors.
- Display-required: the hint becomes visible on entry and is removed after its delay (injected
  short delay) and immediately on exit.

### 8.2 Impacted existing tests

- `GraphInteractionControllerShould.cancelConnectionPreviewBeforeClearingSelection` — must stay
  green unchanged.
- `GraphWorkspaceWindowModelShould` — the new menu item changes the View menu item list; any test
  that asserts menu contents or item order is updated deliberately, not weakened. Existing sidebar
  tests around `commitSidebarWidth`/`setSidebarCollapsed` remain valid because user controls and
  divider input still commit normally; only automatic and transition-generated events are guarded.
- `MapSidebarPanelShould` — the rail restore button is rewired through the model's user-toggle
  path; panel-level tests assert the shared programmatic listener is unchanged.

### 8.3 Acceptance evidence

- Real-application X11 evidence: enter via F11 from a graph window, screenshot the undecorated
  screen-filling canvas with the hint visible; wait for the hint to fade; exit with Esc; screenshot
  the restored window with all chrome, the same bounds and the same visible graph center. Confirm
  popup placement after the cycle and that no viewport command was persisted. Follow the existing
  UI-evidence capture workflow used for previous graph features.

## 9. Files

New (all under `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/`):

- `ScreenFillSurface.java` (interface + `FrameState` + `Survival`)
- `PresentationChrome.java` (interface)
- `PresentationViewport.java` (interface)
- `PresentationHint.java`
- `GraphPresentationMode.java`

Modified:

- `freeplane_plugin_graph/.../window/GraphWorkspaceWindow.java` — raw frame operations and bounded
  recovery, hint host, F11 binding, View menu item (availability-gated), model wiring,
  `windowClosed` from `closeOnEdt`, sidebar override hooks and input-boundary releases, viewport
  baseline/compensation implementation, armed-hold event classification in the viewport change
  listener (model-application guard, then anchor, post-clamp re-correction), the
  generation-stamped settling-horizon chain with an injectable scheduler, and the anchor snapshot
  captured at arm time. No canvas change and no scrollbar listener are needed.
- `freeplane_plugin_graph/.../canvas/GraphCanvas.java` — add the public, generation-agnostic
  settle callback `runWhenViewportSettled(Runnable)` that fires once positioning depth and pending
  clamps are both zero (the post-clamp completion signal), and the public
  `isViewportPositioningActive()` accessor (positioning depth > 0) used for event classification.
- `freeplane_plugin_graph/.../window/MapSidebarPanel.java` — remove the panel's own rail-restore
  listener so the model's `sidebarToggledFromUser` path owns all user controls (one wiring path).
- `freeplane_plugin_graph/.../canvas/GraphInteractionController.java` — optional escape fallback
  with the pre-clear snapshot.
- `freeplane/src/viewer/resources/translations/Resources_en.properties` — new keys
  `graph_workspace.action.full_screen`, `graph_workspace.hint.full_screen_exit`
  (`gradle format_translation` afterwards per repository policy).

No other `GraphCanvas` change is required: the compensation uses the scroll viewport, the canvas
size and the model's own suppression flag, plus the settle callback above.

Tests:

- New: `window/GraphPresentationModeShould.java`, `window/GraphWindowChromeShould.java`.
- Modified: `window/GraphWorkspaceWindowModelShould.java`, `window/MapSidebarPanelShould.java`,
  `canvas/GraphInteractionControllerShould.java`.

Documentation: this design doc and the mockups directory.

## 10. Risks

1. **Undecorated/dispose transition on X11/KWin** can confuse AWT's coordinate caches; the frame
   already installs `FrameResynchronizer`, but popup placement after an enter/exit cycle must be
   verified in the acceptance evidence (edge case 14).
2. **Viewport preservation** is an armed compensation hold with anchor-first event classification,
   direct-publication disarm (canvas and toolbar paths), an explicit canvas settle callback for the
   post-clamp absolute re-correction, and a settling horizon mirroring the platform's own retry
   pattern (2 500 ms, quiet-final-window disarm). The tests pin both parities, a late extent change,
   the odd-shrink compounding probe, the scroll-boundary clamp, publish-before-disarm ordering, the
   toolbar path, exit re-arming, the horizon with a fake scheduler and zero commands. A geometry
   straggler beyond the platform's own horizon is a settlement failure recorded by the acceptance
   evidence, not silently accepted.
3. **Single F11 binding path** must be proven at implementation time (one press = one toggle, works
   while the menu bar is hidden). A later accelerator display must come with a test proving no
   double path.
4. **The sidebar override** is released only from genuine input boundaries; automatic Swing layout
   and no-gesture focus loss must never release it. The survival, release and no-command tests
   protect this, and future sidebar changes must keep the three-step rule in §4.5.
5. **Hint timer** must not fire after disposal; the fake-scheduler test plus the guard in
   `PresentationHint` cover it.
6. **Recovery paths** are bounded to one inverse attempt per protected call and are exercised by
   injected failures with observable state and asserted call counts; a real platform failure cannot
   be forced on demand, so acceptance evidence documents the normal path while injections pin the
   contract.
7. **Deferred macOS support** is intentionally absent: the design must not accumulate dormant
   native-bridge code. The `isAvailable()` seam plus the documented follow-up keep the v1 surface
   small.
