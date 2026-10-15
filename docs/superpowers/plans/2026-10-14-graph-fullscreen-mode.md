# Graph Workspace Full-Screen Mode Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.

**Goal:** Add the approved Linux/Windows F11 and View-menu presentation mode while preserving the current graph view, restoring effective chrome and frame state, and executing no workspace command during transitions or late geometry.

**Architecture:** New package-private presentation contracts, a hint, and a mode state machine belong to `core-mode`; the existing window/model file owns concrete frame mechanics, chrome/sidebar state, and a private viewport-hold delegate in `window-integration`. The delegate prevents `PresentationChrome.capture()` and `PresentationViewport.capture()` from accidentally sharing one implementation. Recovery follows the surviving frame; viewport correction uses an immutable transition baseline, generation-checked canvas completion, and a bounded quiet-window chain.

**Tech Stack:** Java 8-compatible production and tests, Swing/AWT on the EDT, Java 21.0.8-zulu for Gradle, JUnit 4, AssertJ, Mockito, and injectable Swing timers/schedulers.

**Task identifier:** `2026-10-14-graph-fullscreen-mode`
**Authoritative contracts:** `docs/superpowers/specs/2026-10-14-graph-fullscreen-mode-spec.md`; approved context: `docs/superpowers/specs/2026-10-14-graph-fullscreen-mode-design.md`, revision 13. Implement the specification's exact executable contracts where the context is abbreviated.
**Planning boundary:** This plan was authored from those two documents and one recent plan, without inspecting implementation source. File regions below use the specification's baseline line references where supplied and exact method anchors elsewhere; resolve current line positions before editing rather than assuming baseline offsets survived.

## Global Constraints

- Module: `freeplane_plugin_graph` only, plus the existing English viewer translation resource.
- Production source remains Java 8-compatible and uses four-space indentation. All transition work runs on the EDT. No exception from a presentation transition may escape to the EDT.
- Use Java `~/.sdkman/candidates/java/21.0.8-zulu` and `gradle`, not `gradlew` or Maven. Before each task's Gradle commands use `export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"` and `export PATH="$JAVA_HOME/bin:$PATH"`; run from the lane worktree root. Add `-PTestLoggingFull --console=plain` for test evidence. Use the harness's escalation mechanism if required for Gradle execution.
- The default hint delay is 3,000 ms. A quiet interval is 200 ms with no frame-bounds change and no viewport event. Never disarm before 2,500 ms from the transition's last geometry operation; the absolute cap is 5,000 ms and logs settlement failure.
- `GraphPresentationMode` has a constructor-injected `supported` flag. The production overload supplies `!Compat.isMacOsX()`. On macOS the View item is disabled and unchecked; F11 and mode Escape fallback are no-ops with no frame, chrome, viewport, hint, or workspace-command operation.
- No `Compat.setFullScreenOnMac` call, EAWT listener, native macOS bridge, second presentation execution path, menu accelerator, root-pane Escape binding, toolbar full-screen button, configurable shortcut, persistence change, auto-fit, re-layout, or re-settle is allowed.
- The only `GraphCanvas` public additions are `public void runWhenViewportSettled(Runnable callback)` and `public boolean isViewportPositioningActive()`. Preserve all other API, listeners, events, sizing, rendering, gesture geometry, and pending-clamp behavior. Do not add a scrollbar listener.
- Preserve the existing main map window's F11 behavior, core `FrameController`, `FrameResynchronizer.install(this)`, `show()` closed guard, workspace XML, command routing, graph layout, `updateMapRows`, and persisted presentation data.
- Every no-mutation assertion observes `GraphWorkspaceHandle.execute(GraphCommand)` after fixture initialization. Presentation entry/exit and late geometry emit zero commands of any type in writable and read-only workspaces. Prove the oracle rejects injected display and viewport commands through that same boundary.
- Escape still emits the baseline `GraphIntent.ChangeSelection(empty)` when no preview exists and consumes the event unconditionally. Only a press starting with neither preview nor selection invokes the fallback; that existing selection intent is distinct from a presentation-generated workspace command.
- Frame recovery makes one requested raw operation and, only if it fails, one inverse raw operation. No mode retry is permitted. Catch `RuntimeException` and `Error` at the transition boundary and log primary and secondary failures without allowing logger failure to escape recovery.
- Effective sidebar override survives restoration, refresh, revalidate, resize, automatic divider-property changes, and no-gesture focus loss. Only the explicit user boundaries or an actual change of stored sidebar display values release it; close/discard removes it. Never call `discard()` merely to finish a successful restore because it removes the override.
- Allowed new production paths are `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/ScreenFillSurface.java`, `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/PresentationChrome.java`, `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/PresentationViewport.java`, `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/PresentationHint.java`, and `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphPresentationMode.java`.
- Allowed new test paths are `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphPresentationModeShould.java` and `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWindowChromeShould.java`. Keep helpers nested or file-local within these or the other allowed files; create no extra probe file.
- Allowed existing production paths are `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java`, `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/MapSidebarPanel.java`, `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphCanvas.java`, and `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphInteractionController.java`.
- Allowed existing test paths are `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java`, `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/MapSidebarPanelShould.java`, and `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphInteractionControllerShould.java`. The sole resource path is `freeplane/src/viewer/resources/translations/Resources_en.properties`; run `gradle format_translation` after editing it and include no other translation change.
- Each task follows a failing-behavior test, minimal implementation, focused green run, and reviewable commit. Do not claim a compilation failure is the behavioral red phase: introduce the specified signatures first where needed, then run the failing assertion. Leave plan checkboxes unticked during execution.
- Commit subjects end with `[2026-10-14-graph-fullscreen-mode]`; stage only the task's exact Files paths. Evidence, screenshots, mutation copies, and acceptance logs go to an untracked external run directory such as `/tmp/graph-fullscreen-mode-evidence`; do not expand the tracked-file allowlist.

### File ownership and execution graph

| Lane | Exclusively owned files | Responsibility |
| --- | --- | --- |
| `core-mode` | Five new production window-package files; `GraphPresentationModeShould.java`; `GraphCanvas.java`; `GraphInteractionController.java`; `GraphInteractionControllerShould.java` | Contracts, hint, state machine/exception boundary, canvas settle API, Escape fallback |
| `window-integration` | `GraphWorkspaceWindow.java`; `MapSidebarPanel.java`; `GraphWindowChromeShould.java`; `GraphWorkspaceWindowModelShould.java`; `MapSidebarPanelShould.java`; `Resources_en.properties` | Concrete frame recovery, model chrome/sidebar and viewport hold, menu/keys/close, resources and display acceptance |

Task graph: `1 -> 2 -> 3` on `core-mode`; `1 -> 4 -> 5 -> 6` on `window-integration`, with additional edges `2 -> 5` and `3 -> 5`. Task 4 can run concurrently with Tasks 2–3 after Task 1 is integrated. Tasks sharing a lane are serialized; file ownership is disjoint between lanes. Tier spelling uses the validator's canonical TitleCase values.

### Contract-to-task coverage

| Specification | Implementation and proof |
| --- | --- |
| §§1, 2.2–2.5, 8 | Task 1: exact contracts, immutable frame state, hint and test seams; Task 5: concrete raw frame mechanics |
| §§2.1, 2.6, 3, 6.1–9, 7.1 | Task 2: all six survival branches, exception boundary, unavailable gate, guard and close |
| §§2.7–2.8, 7.5, 8.3 | Task 3: Escape ordering and the two canvas additions, completion safety |
| §§2.3, 2.10–2.11, 4, 6.11, 6.14–16, 7.2, 7.7 | Task 4: effective chrome snapshot, sidebar override, all release/non-release boundaries and pre-mutation guards |
| §§2.4, 2.9–2.10, 5, 6.10, 6.12–13, 6.17–20, 6.23, 6.25, 7.3–7.4, 7.6 | Task 5: viewport arithmetic/classification/publish order/generations/settling, concrete frame recovery, F11/menu and real-close wiring |
| §§6.21–24, 7.6, 7.8, 8.2, 9 | Tasks 4–5: mutation probes at ownership boundaries; Task 6: translations, display-required tests, full-suite and real-application acceptance evidence |

## Task 1: Define presentation contracts and implement the exit hint

**Implementer tier:** Capable
**Lane:** core-mode
**Lane hint:** core-mode
**Category:** contracts-and-hint
**Depends on:** none

**Files:**

- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/ScreenFillSurface.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/PresentationChrome.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/PresentationViewport.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/PresentationHint.java`
- Create/Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphPresentationModeShould.java`

**Interfaces:**

- Consumes Swing `JLayeredPane`, `JPanel`, `JLabel`, `javax.swing.Timer`, `javax.swing.event.ChangeEvent`; existing `org.freeplane.plugin.graph.canvas.GraphViewport`.
- Produces the three package-private interfaces below, with `FrameState` and `Survival` nested only in `ScreenFillSurface`.
- Produces package-private final `PresentationHint` with `PresentationHint(JLayeredPane host, String text)`, `PresentationHint(JLayeredPane host, String text, int delayMillis, TimerFactory timerFactory)`, `void show()`, `void hide()`, `void dispose()`, and nested `@FunctionalInterface interface TimerFactory { Timer create(int delayMillis, Runnable expiry); }`. Both constructors require non-null host/text/factory; the production delay is 3,000 ms.
- Produces headless tests and nested recording-timer helpers in `GraphPresentationModeShould`; Task 2 extends this same file sequentially. The final hint is tested as a real Swing object with the injected factory, not subclassed or mocked as an extensible fake.

### Steps

- [ ] Create the interface declarations with these exact members; the window/model implementations arrive in Tasks 4–5. `FrameState` owns a private final copy of the bounds and never exposes it.

```java
interface ScreenFillSurface {
    enum Survival { RESTORED_WINDOWED, REENTERED_FULLSCREEN, UNKNOWN_FRAME }
    final class FrameState {
        private final Rectangle bounds;
        private final int extendedState;
        FrameState(Rectangle bounds, int extendedState) {
            this.bounds = new Rectangle(Objects.requireNonNull(bounds, "bounds"));
            this.extendedState = extendedState;
        }
        Rectangle bounds() { return new Rectangle(bounds); }
        int extendedState() { return extendedState; }
    }
    FrameState captureFrameState();
    Survival applyScreenFill(FrameState previous);
    Survival restoreFrameState(FrameState previous);
}

interface PresentationChrome {
    void capture();
    void hide();
    void restore();
    void discard();
}

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

Use `java.awt.Rectangle`, `java.util.Objects`, `javax.swing.event.ChangeEvent`, and `org.freeplane.plugin.graph.canvas.GraphViewport` imports in their owning files. All reside in `org.freeplane.plugin.graph.window`.

- [ ] Add `frameStateDefensivelyCopiesBoundsAndPreservesExtendedState`, `hintShowRestartsInjectedTimerAndExpiryHidesPanel`, `hintHideStopsTimerAndDisposeMakesExpiryInert`, and `hintNeverAcceptsFocusOrInterceptsMouse` to `GraphPresentationModeShould`. Run `gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.window.GraphPresentationModeShould' -PTestLoggingFull --console=plain`. Initially use minimal hint method bodies so these assertions fail for visibility/timer behavior, not missing symbols. The bounds test mutates both the constructor argument and an accessor result and still expects the original rectangle and exact extended state.

```java
@Test
public void frameStateDefensivelyCopiesBoundsAndPreservesExtendedState() {
    Rectangle original = new Rectangle(11, 22, 900, 700);
    ScreenFillSurface.FrameState saved =
        new ScreenFillSurface.FrameState(original, Frame.MAXIMIZED_BOTH);
    original.x = 999;
    saved.bounds().width = 1;
    assertThat(saved.bounds()).isEqualTo(new Rectangle(11, 22, 900, 700));
    assertThat(saved.extendedState()).isEqualTo(Frame.MAXIMIZED_BOTH);
}
```

The timer factory records `expiry` in an `AtomicReference<Runnable>` and returns a recording `Timer` whose `restart()`/`stop()` increment counters without real time. Construct a real `JLayeredPane` sized `1000 x 700`; inspect the installed panel via `host.getComponents()`. Call `show()` twice, require two restarts, run the recorded expiry on the EDT, and require invisibility. After `dispose()`, call the saved expiry and `show()`; require no component reinstallation and no timer restart. Assert the panel and label are non-focusable and `contains(...)` is false.

- [ ] Implement the panel and lifecycle. Add the panel once to a layer above canvas content, top-center it from the host width and preferred size, and update placement through a host component-resize listener. `dispose()` removes that listener as well as the panel. Make the transparent hit-test override explicit:

```java
private final JPanel panel = new JPanel() {
    @Override public boolean contains(int x, int y) { return false; }
};
// The default factory creates a non-repeating javax.swing.Timer:
// new Timer(delayMillis, event -> expiry.run()), then setRepeats(false).
void show() {
    if (disposed) return;
    placeAtTopCenter();
    panel.setVisible(true);
    timer.restart();
}
void hide() {
    panel.setVisible(false);
    timer.stop();
}
void dispose() {
    if (disposed) return;
    disposed = true;
    timer.stop();
    host.removeComponentListener(hostResizeListener);
    host.remove(panel);
    host.revalidate();
    host.repaint();
}
```

Initialize the panel hidden and the timer callback as `() -> { if (!disposed) hide(); }`; setting a captured callback to run after disposal must not touch or re-add the panel. Keep the panel/label non-focusable. All calls in tests execute via `SwingUtilities.invokeAndWait`; do not use sleeps.

- [ ] Re-run the complete `GraphPresentationModeShould` class with the command above; require green and record its executed count. Review that no frame or native platform code was introduced.
- [ ] Review `git diff --check`, stage exactly the five Files paths, and commit with the exact message `Add presentation contracts and transient exit hint [2026-10-14-graph-fullscreen-mode]`.

## Task 2: Implement the mode state machine and exception boundary

**Implementer tier:** Frontier
**Lane:** core-mode
**Lane hint:** core-mode
**Category:** mode-and-recovery
**Depends on:** Task 1

**Files:**

- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphPresentationMode.java`
- Modify/Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphPresentationModeShould.java` (append state-machine fixtures/tests after Task 1's hint tests)

**Interfaces:**

- Consumes Task 1's `ScreenFillSurface.captureFrameState(): FrameState`, `applyScreenFill(FrameState): Survival`, `restoreFrameState(FrameState): Survival`; `FrameState.bounds(): Rectangle`, `extendedState(): int`; enum `RESTORED_WINDOWED`, `REENTERED_FULLSCREEN`, `UNKNOWN_FRAME`.
- Consumes Task 1's `PresentationChrome.capture()/hide()/restore()/discard(): void`; `PresentationViewport.capture()/compensate()/disarm()/windowClosed(): void`, `classify(ChangeEvent): void`, `canvasPublication(): void`, `toolbarPublication(GraphViewport): void`; `PresentationHint.show()/hide()/dispose(): void`.
- Produces package-private final `GraphPresentationMode` with both exact constructors below and package-private methods `boolean isAvailable()`, `void toggle()`, `boolean isActive()`, `void requestExit()`, `void windowClosed()`.

```java
GraphPresentationMode(ScreenFillSurface surface, PresentationChrome chrome,
        PresentationViewport viewport, PresentationHint hint,
        JCheckBoxMenuItem menuItem, Runnable focusCanvas,
        Consumer<String> log, boolean supported);
GraphPresentationMode(ScreenFillSurface surface, PresentationChrome chrome,
        PresentationViewport viewport, PresentationHint hint,
        JCheckBoxMenuItem menuItem, Runnable focusCanvas,
        Consumer<String> log);
```

All dependencies are non-null. The second constructor delegates with `!Compat.isMacOsX()`. `isAvailable()` returns `supported` exactly; no platform bridge is installed.

### Steps

- [ ] Add a minimal compilable mode shell and a nested `ModeFixture` in `GraphPresentationModeShould`. The fixture owns real `JCheckBoxMenuItem` and `PresentationHint` objects, a recording timer factory, fake `ScreenFillSurface`, recording `PresentationChrome` and `PresentationViewport`, `List<String> calls`, `List<String> logs`, and a focus callback recording `focus`. Define `ModeFixture(boolean supported)`, `GraphPresentationMode mode`, `ScreenFillSurface.Survival enterResult`, `exitResult`, and independent one-shot `RuntimeException`/`Error` hooks for each collaborator operation. Each fake method records its name before its hook/result. The surface records `captureFrame`, `applyScreenFill`, and `restoreFrameState` separately; it never silently retries on behalf of the mode.
- [ ] Implement the following exact named tests with the observable sequence/oracle in the table. Run each on the EDT and assert failures are behavioral before replacing the shell. Use `assertThatCode(() -> fixture.mode.toggle()).doesNotThrowAnyException()` for failure paths, including injected `AssertionError`; inspect logs as well as final state.

| Test method | Required assertions |
| --- | --- |
| `enterCapturesBeforeMutationAndActivatesAfterFullscreenSurvival` | Order `captureViewport, captureFrame, captureChrome, hideChrome, applyScreenFill, compensate, focus`, then timer restart; checked item/active mode; captures precede every mutation |
| `enterRestoresChromeAndStaysInactiveWhenFrameRecoveryLeavesWindowed` | `RESTORED_WINDOWED`; `restoreChrome` occurs before `compensate`; no hint show, menu unchecked, inactive |
| `enterRestoresChromeAndStaysInactiveWhenFrameSurvivalIsUnknown` | `UNKNOWN_FRAME`; same restore-before-compensate order plus warning |
| `exitRestoresChromeAndDeactivatesAfterWindowedSurvival` | Entry, clear calls, exit; fresh `captureViewport`, one `restoreFrameState`, `restoreChrome`, `compensate`, hint stopped, unchecked/inactive, focus; no new frame snapshot |
| `exitKeepsFullscreenOwnershipWhenRecoveryReentersFullscreen` | Exit result `REENTERED_FULLSCREEN`; zero `restoreChrome`, hidden chrome, checked/active, compensation and focus, stopped hint, failed-exit log; retry exits normally using the same saved frame |
| `exitRestoresChromeAndDeactivatesWhenSurvivalIsUnknown` | Restore chrome then compensate, hidden hint, unchecked/inactive, warning |
| `frameRecoveryAttemptsAtMostOneInverseOperation` | A recording surface emulates one requested raw call and one inverse; both fail -> unknown; the mode calls its surface method exactly once and never retries. Task 5 separately proves the real production recovery helper uses the bounded algorithm |
| `failureBeforeFrameApplicationRestoresChromeAndCompensates` | Independently fail viewport capture, frame capture, chrome capture before usable snapshot, and partial hide; no `applyScreenFill`; restore best-effort, compensate, hide hint, uncheck/inactive; discard only unusable capture; primary and secondary failures logged |
| `failureAfterFullscreenEntryKeepsChromeHiddenAndModeActive` | Fail compensation, hint timer restart, menu bookkeeping, and focus after fullscreen survival, one hook at a time; never restore frame/chrome; checked/active, exit remains usable; failed hint hidden |
| `failureDuringExitBookkeepingNeverEscapesToTheEdt` | For each exit survival, inject failures in restore, compensation, hint hide, menu, focus, and logger, including a secondary recovery failure; no exception escapes; surviving-state table still holds; transition guard resets |
| `unavailableModeDoesNothingAndLeavesMenuUnchecked` | `supported=false`; call toggle/requestExit repeatedly; disabled/unchecked, inactive; no surface/chrome/viewport/hint/focus/workspace effect |
| `requestExitDoesNothingWhenInactive` | Empty collaborator call log; no capture or hint action |
| `closeDisposesHintDiscardsSnapshotAndInvalidatesCallbacks` | Close active mode twice; collaborators close/discard once; unchecked/disabled; saved timer/settle callbacks and all later entry points inert; also close reentrantly inside a surface call and require CLOSED is never overwritten |
| `rapidToggleDuringTransitionRunsOnlyOneTransition` | Surface callback recursively invokes toggle/requestExit; exactly one protected surface call and no second capture; next independent toggle works |

For the partial-capture test, the fake tracks whether capture installed a usable snapshot. Its capture commits atomically, as Task 4's model implementation will; failure before that point leaves nothing usable and is discarded. Test restoration failure per component in Task 4, not by making the mode aware of component internals.

- [ ] Implement `State { INACTIVE, ACTIVE, CLOSED }`, `boolean transitioning`, `FrameState previous`, `boolean chromeSnapshotUsable`, and the exception boundary. Set `chromeSnapshotUsable` only after atomic chrome capture returns; retain it across a completed restore until discard/close. A later failed capture must not discard an older usable snapshot that still protects the restored layout. Commit the surviving state before risky presentation bookkeeping so failures cannot lose fullscreen ownership. Use this branch table for both the normal path and recovery; never infer survival from the requested direction:

| Direction | Survival | Chrome before compensation | Final state/menu/hint |
| --- | --- | --- | --- |
| enter | REENTERED_FULLSCREEN | hidden | ACTIVE / checked / shown if no hint failure |
| enter | RESTORED_WINDOWED | restore | INACTIVE / unchecked / hidden |
| enter | UNKNOWN_FRAME | best-effort restore | INACTIVE / unchecked / hidden; warning |
| exit | RESTORED_WINDOWED | restore | INACTIVE / unchecked / hidden |
| exit | REENTERED_FULLSCREEN | remains hidden | ACTIVE / checked / hidden; failed-exit log |
| exit | UNKNOWN_FRAME | best-effort restore | INACTIVE / unchecked / hidden; warning |

```java
boolean isAvailable() { return supported; }
boolean isActive() { return state == State.ACTIVE; }
void toggle() {
    if (!supported || state == State.CLOSED || transitioning) return;
    transition(state == State.INACTIVE);
}
void requestExit() {
    if (!supported || state != State.ACTIVE || transitioning) return;
    transition(false);
}
```

`transition(boolean entering)` sets the guard and places the complete sequence in one `try/catch (RuntimeException | Error failure)/finally`. Initialize known survival to windowed for entry and fullscreen for an exit that has not yet called the surface. Entry runs `viewport.capture(); previous = surface.captureFrameState(); chrome.capture(); chrome.hide(); survival = surface.applyScreenFill(previous);`. Exit runs fresh `viewport.capture(); survival = surface.restoreFrameState(previous);`. Check `state != CLOSED` after every collaborator that may reentrantly close the mode. Select ownership from survival; restore chrome on windowed/unknown branches before `viewport.compensate()`. Focus only after selecting the surviving state. Show the hint only on successful entry; hide it on every exit attempt or failed entry. Update the check item from actual state, never its pre-click selected value.

Recovery uses independently guarded attempts for restore, compensation, hint hide, menu state, focus and logging, so a secondary exception does not skip later cleanup. It does not invoke another surface method. Entry failures before frame application restore usable chrome and compensate, remain inactive, and discard an unusable partial capture. An exit capture failure before frame restoration keeps the already known fullscreen ownership. Preserve `previous` after every surviving-fullscreen result so retry exit can restore the original window; clear the frame reference after completed windowed/unknown transition. Retain restored chrome's usable snapshot/override until next capture or real close rather than calling `discard()` on normal exit.

- [ ] Make logging best-effort without recursive logging: format primary and recovery exception class/message and stack information into the injected `Consumer<String>`; catch any exception/error thrown by the logger. Always clear `transitioning` in `finally`. `windowClosed()` first makes CLOSED terminal, then individually attempts `hint.dispose()`, `viewport.windowClosed()`, `chrome.discard()`, clearing `previous`, unchecking/disabling the menu. It is idempotent even when one cleanup hook fails. An unsupported mode performs no entry/exit collaborator operation; real close may dispose already constructed resources.
- [ ] Run `gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.window.GraphPresentationModeShould' -PTestLoggingFull --console=plain`. Require every Task 1 and Task 2 test green; record the failure-injection matrix and operation order. Replace the mode's surface call temporarily in an external mutant copy with an extra retry and require `frameRecoveryAttemptsAtMostOneInverseOperation` to fail; replacing survival-driven ownership with requested direction must fail the entry-recovery and failed-exit cases. Do not commit mutant changes.
- [ ] Review `git diff --check`, stage exactly the two Files paths, and commit with the exact message `Implement presentation survival state machine and exception boundary [2026-10-14-graph-fullscreen-mode]`.

## Task 3: Add canvas settle completion and the Escape fallback

**Implementer tier:** Capable
**Lane:** core-mode
**Lane hint:** core-mode
**Category:** canvas-lifecycle-and-input
**Depends on:** Tasks 1, 2

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphCanvas.java` (`viewportPositioningDepth`/`pendingViewportClamps` completion paths and new methods adjacent to the existing viewport-query methods; preserve `visibleViewport` baseline region `:118-133`)
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphInteractionController.java:370-390` (baseline Escape branch; add nullable field and setter beside existing callback configuration)
- Modify/Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphInteractionControllerShould.java` (Escape regression tests and nested canvas-completion probes)

**Interfaces:**

- Consumes existing canvas counters `viewportPositioningDepth: int`, `pendingViewportClamps: int`, and EDT helper `onEdt`; keep every increment/decrement, clamp, surface-sizing and existing combined `isProgrammaticViewportChange()` behavior intact.
- Produces exactly `public void GraphCanvas.runWhenViewportSettled(Runnable callback)` and `public boolean GraphCanvas.isViewportPositioningActive()`; one private pending `Runnable` and a private drain helper are implementation details, not additional public API.
- Produces `private Runnable escapeFallback; public void GraphInteractionController.setEscapeFallback(Runnable fallback)` accepting null. Consumes `canvas.paintState().selection(): Optional<ProjectedEndpointKey>`, existing `cancelPreview()`, `setSelectionVisual(...)`, and `emit(GraphIntent)`.

### Steps

- [ ] Add the method signatures, initially with no completion behavior. In the existing canvas-package test file add `viewportSettledCallbackRunsImmediatelyOnEdt`, `viewportSettledCallbackWaitsForPositioningAndPendingClamps`, `viewportSettledRegistrationReplacesEarlierCallback`, `viewportSettledCallbackExceptionDoesNotEscapeCompletion`, `viewportPositioningAccessorExcludesPendingClamps`, and `viewportSettledCallbackRejectsNull`. Add a private test helper that uses reflection to access the existing private begin/end positioning methods and clamp-completion runnable/counters; do not add a production test-only setter. Trigger actual existing decrement/completion paths, not a test replica of the drain algorithm. Run the focused test command below and record assertion failures before adding callback draining.

Use a recording `List<String>` and `SwingUtilities.invokeAndWait`; register `first`, replace it with `second` with both counters positive, complete positioning, and require no callback while a clamp remains; complete the last clamp through its real runnable and require exactly `second`. Assert the callback observes both counters at zero. For the accessor test, force depth zero and pending clamp positive, require `isViewportPositioningActive() == false`; positive depth requires true. Register a callback that throws `AssertionError` and require the real final completion operation returns without escaping. Register from a worker thread, flush EDT, and assert execution occurs on EDT. Null rejection must be deterministic even when called off EDT.

- [ ] Implement the additions using the existing `onEdt` execution convention. The callback replacement and drain logic is:

```java
public boolean isViewportPositioningActive() {
    return viewportPositioningDepth > 0;
}
public void runWhenViewportSettled(Runnable callback) {
    final Runnable value = Objects.requireNonNull(callback, "callback");
    onEdt(() -> {
        pendingViewportSettledCallback = value;
        drainViewportSettledCallback();
    });
}
private void drainViewportSettledCallback() {
    if (viewportPositioningDepth != 0 || pendingViewportClamps != 0) return;
    final Runnable callback = pendingViewportSettledCallback;
    pendingViewportSettledCallback = null;
    if (callback == null) return;
    try {
        callback.run();
    }
    catch (RuntimeException | Error failure) {
        try {
            java.util.logging.Logger.getLogger(GraphCanvas.class.getName()).log(
                java.util.logging.Level.WARNING, "Viewport settle callback failed", failure);
        }
        catch (RuntimeException | Error loggingFailure) {
            // A diagnostic failure must not escape the canvas completion path.
        }
    }
}
```

Call the drain helper immediately after the final decrement in both existing completion paths, including exceptional clamp completion, so a clamp with no viewport event still completes the callback. The JDK logger above records callback failures and contains a secondary logger failure; it requires no new dependency or public API. Do not include `pendingViewportClamps` in the new positioning accessor and do not alter the old combined accessor. Clear the stored callback before invoking it so reentrant registration cannot run the old callback twice.

- [ ] Add the four exact Escape tests: `escapeFallbackRunsWhenPressStartsWithNoPreviewAndNoSelection`, `escapeFallbackDoesNotRunWhenPressStartsWithPreview`, `escapeFallbackDoesNotRunWhenPressStartsWithSelection`, and `escapeIsConsumedForPreviewSelectionAndFallbackCases`. Keep `cancelConnectionPreviewBeforeClearingSelection` byte-for-byte unchanged. Install an `AtomicInteger` fallback, dispatch actual `KEY_PRESSED/VK_ESCAPE` events through the existing fixture, assert invocation count, consumed flag, and baseline emitted intents. Use separate preview/selection fixtures so the pre-clear state is observable. Explicitly test `setEscapeFallback(null)` restores baseline behavior. Confirm red with the focused command before replacing the Escape branch.

```java
private Runnable escapeFallback;
public void setEscapeFallback(Runnable fallback) {
    escapeFallback = fallback;
}
// Inside the existing Escape branch only:
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

- [ ] Run `gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.canvas.GraphInteractionControllerShould' --tests 'org.freeplane.plugin.graph.window.GraphPresentationModeShould' -PTestLoggingFull --console=plain`. Require all tests green. In an external mutant copy move `hadSelection` after the clear and require `escapeFallbackDoesNotRunWhenPressStartsWithSelection` to fail. Remove the drain call after final clamp completion and require `viewportSettledCallbackWaitsForPositioningAndPendingClamps` to fail; prove its clamp really completes without a final viewport event.
- [ ] Review the canvas diff: only the two public members, private pending callback/drain helper, and completion notifications are added; no geometry/gesture/sizing change. Review `git diff --check`, stage the three Files paths, and commit with the exact message `Add canvas settle completion and ordered Escape fallback [2026-10-14-graph-fullscreen-mode]`.

## Task 4: Preserve effective chrome and sidebar user ownership

**Implementer tier:** Capable
**Lane:** window-integration
**Lane hint:** window-integration
**Category:** chrome-and-sidebar
**Depends on:** Task 1

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java:450-486` (model/sidebar wiring), `:711-817` (`applySidebarSettings` and guard region), `:1237-1302` (divider/property/focus listeners), `:1355-1365` (maps-sidebar menu action); also model fields, `applyPresentation`, `setSidebarCollapsed`, `commitSidebarWidth`, and `resetSidebarWidth` by exact method anchor
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/MapSidebarPanel.java:35-47` (remove only the rail restore action listener; retain collapsed notifications)
- Create/Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWindowChromeShould.java`
- Modify/Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/MapSidebarPanelShould.java` (panel-owned listener and programmatic notification cases)

`GraphWorkspaceWindowModel` is declared inside `GraphWorkspaceWindow.java`; do not create a new model source file. This task must compile and pass without `GraphPresentationMode` or the Task 3 canvas additions, allowing concurrency with the other lane after Task 1.

**Interfaces:**

- Consumes Task 1's `PresentationChrome` with `void capture()`, `void hide()`, `void restore()`, `void discard()`.
- Produces `GraphWorkspaceWindowModel implements PresentationChrome`; this `capture()` is chrome-only. Task 5 supplies the separate `PresentationViewport` delegate.
- Produces package-private model method `void sidebarToggledFromUser(boolean collapsed)`, with override release before the existing `setSidebarCollapsed(boolean)` mutation/commit path.
- Preserves model methods `void applyPresentation(GraphWorkspacePresentation value)`, `void applySidebarSettings(DisplaySettings settings)`, `void setSidebarCollapsed(boolean collapsed)`, `void commitSidebarWidth(int location)`, `void resetSidebarWidth()`, `void commitSidebarDisplaySettings(int width, boolean hidden)`, and the existing `runWithSidebarGuard` mechanism.
- Preserves `MapSidebarPanel.rail()`, `isCollapsed(): boolean`, `setCollapsed(boolean)`, and `CollapsedListener` programmatic notifications. Produces one model-owned listener on `sidebar.rail().restoreButton()`; the panel installs none.
- Private immutable `SidebarOverride` fields are `boolean collapsed`, `int dividerLocation`, `int dividerSize`, `boolean enabled`. Private immutable `ChromeSnapshot` additionally owns the five component visibility booleans. `boolean presentationHidden` is transient UI state; neither type is serialized.

### Steps

- [ ] Create a nested `ChromeFixture(boolean writable)` in `GraphWindowChromeShould` using the real package-private model, real menu/toolbar/sidebar/settings/status components, a real laid-out `JSplitPane`, and a recording handle at `GraphWorkspaceHandle.execute(GraphCommand)`. Adapt the existing model-test constructor fixture rather than introducing a new production constructor API. Record a command checkpoint after initialization; assert the full executed-command list after that checkpoint, not a display-command-only filter. Deliver callbacks on the EDT and paint the split pane to a `BufferedImage` to force real layout-driven divider notifications.
- [ ] Add the exact first thirteen §7.2 tests below, plus `changedStoredSidebarSettingsReleaseOverrideButIdenticalRefreshDoesNot`, `restoreContinuesAfterOneComponentFails`, and `sidebarMutationGuardsReturnBeforeAnyFieldOrComponentChange`. Add the three exact §7.7 tests to `MapSidebarPanelShould`. Confirm red with the focused command below before implementing guards/override behavior.

| Test method | Concrete setup and oracle |
| --- | --- |
| `hideMakesAllChromeInvisibleAndRemovesDividerSize` | Capture, hide twice, layout/paint; all five chrome components invisible, divider size 0, graph scroll pane fills split width |
| `restoreReturnsVisibilityDividerStateAndCollapsedStateExactly` | Exercise expanded and collapsed rail, nondefault divider, disabled split, initially hidden settings; capture/hide/restore gives exact visibility/location/size/enabled/collapsed; toolbar settings toggle matches settings visibility |
| `captureUsesEffectiveStateWhenSettingsDifferFromDisplaySettings` | Effective state A uses divider 317, size 9, disabled and collapsed; persisted settings B uses width 241 and expanded. Restore must yield A, including maps-menu selection and applied width |
| `automaticRefreshLayoutAndFocusLossCannotOverwriteSidebarOverride` | After restoring A differing from B, deliver same-value presentation refresh, canvas-state delivery, revalidate, resize and a painted layout divider change, no-gesture focus loss; override still present and A restored, zero commands |
| `expandedCollapseButtonReleasesOverrideAndCommitsOnce` | Expanded A restored; click actual collapse button; override removed before mutation, normal collapsed state, exactly one writable display commit |
| `collapsedRailRestoreButtonReleasesOverrideAndCommitsOnce` | Collapsed A restored; click actual rail button; exactly one model user path, expanded, one writable display commit |
| `mapsSidebarMenuToggleReleasesOverrideAndCommitsOnce` | Click actual View maps-sidebar item; same single commit and release-before-mutation |
| `dividerPressReleasesOverrideBeforeDrag` | Dispatch left press and drag/release; at the first gesture-active write override is already null; preserve ordinary minimum/clamp behavior and one commit |
| `dividerDoubleClickReleasesOverrideBeforeReset` | First action after restore is actual left double-click; default width restored, one commit; no duplicate release/drag completion commit |
| `keyboardDividerActionReleasesOverrideBeforeMutation` | Invoke selectMin/selectMax/negativeIncrement/positiveIncrement actions separately after restoring A; delegate sees no override; normal mutation and one commit each |
| `dividerPropertyChangeDoesNotReleaseOverride` | Fire actual layout-induced DIVIDER_LOCATION_PROPERTY without any gesture; override survives; zero command |
| `noGestureFocusLossDoesNotReleaseOverrideOrCommit` | No active mouse/keyboard gesture; dispatch focus loss after restore; A and session fields unchanged, no commit |
| `zeroWidthSplitPaneRestoresOnFirstLayout` | Restore while split width is zero, deliver automatic refresh, then first real layout; A applied and override retained throughout |
| `changedStoredSidebarSettingsReleaseOverrideButIdenticalRefreshDoesNot` | Re-deliver stored B unchanged: no release; change stored width or hidden flag separately: release before applying new settings. Do not compare new settings against effective A |
| `restoreContinuesAfterOneComponentFails` | Inject a throwing visibility/split restoration operation once; later components still restored, hidden marker cleared in finally, log contains failure |
| `sidebarMutationGuardsReturnBeforeAnyFieldOrComponentChange` | Invoke each of setSidebarCollapsed/commitSidebarWidth/resetSidebarWidth with hidden marker or installed override; assert components, applied width, pending/gesture/session fields and executed commands unchanged |
| `railRestoreButtonHasNoPanelOwnedListener` | Standalone MapSidebarPanel has no constructor-owned restore action listener |
| `programmaticCollapsedListenerRemainsUnchanged` | Programmatic setCollapsed still gives the baseline collapsed notification, without user release or command |
| `modelOwnsRailRestoreUserPath` | After model construction exactly one restore user action path exists, targeting sidebarToggledFromUser(false); no duplicate panel path |

Repeat user release cases for read-only mode: retain baseline session-only behavior and zero persisted commands. Programmatic notifications are not user actions. The three no-command enter/exit tests from §7.2 are added in Task 5 when the real viewport/mode integration exists.

- [ ] Implement effective capture atomically: build a local immutable snapshot from `isVisible()` of menu bar/toolbar/sidebar/settings/status, split enabled/divider size/effective divider location, and `sidebar.isCollapsed()`. Only after all reads succeed, install the snapshot and matching override. If capture fails before there is a usable snapshot, discard the partial state so the mode's pre-frame fallback is safe. Preserve a previously usable restored snapshot if it still protects the layout. Hiding sets the marker before touching components and is idempotent.

```java
public void hide() {
    presentationHidden = true;
    menuBar.setVisible(false);
    toolbar.setVisible(false);
    sidebar.setVisible(false);
    splitPane.setDividerSize(0);
    settingsPanel.setVisible(false);
    toolbar.setSettingsVisible(false);
    statusSlot.setVisible(false);
}
void sidebarToggledFromUser(boolean collapsed) {
    sidebarOverride = null;
    setSidebarCollapsed(collapsed);
}
```

`restore()` independently guards/logs each component restoration, so one exception/error does not skip later components. Release `presentationHidden` in `finally`. Restore split enabled/collapsed/divider size/location, maps-sidebar menu selected state, `appliedSidebarWidth`, minimum sizes and pending/gesture flags under `runWithSidebarGuard`; synchronize `toolbar.setSettingsVisible(settingsPanel.isVisible())` after attempting settings restoration. Keep a usable snapshot after restore; retain the override until authorized release. `discard()` clears snapshot, hidden marker and override without publishing.

- [ ] Replace the sidebar decision before any sizing or component mutation; keep existing settings calculations as the third branch:

```java
void applySidebarSettings(DisplaySettings settings) {
    if (presentationHidden) return;
    if (sidebarOverride != null) {
        applySidebarOverride(sidebarOverride);
        return;
    }
    applyCurrentSidebarSettings(settings);
}
```

Here `applyCurrentSidebarSettings(DisplaySettings)` is a private extraction of the pre-existing body, not a parallel fallback implementation. `applySidebarOverride(SidebarOverride)` uses `runWithSidebarGuard` and restores every captured sidebar field, selected state, applied width, minimum sizes, and pending flags. With zero split width retain the override and let the first layout event re-apply it; defer only the physical divider positioning. In `applyPresentation`, compare incoming width/hidden with the previously stored presentation's width/hidden before replacing the stored value. Release only for an actual change of those stored values, then continue existing canvas/theme/settings work and call `applySidebarSettings`.

- [ ] Put `if (presentationHidden || sidebarOverride != null) return;` as the first executable line of `setSidebarCollapsed`, `commitSidebarWidth`, and `resetSidebarWidth`, before any field/session/Swing change or display command. Keep existing writable/read-only, minimum-width and single-commit logic after the guard. `commitSidebarDisplaySettings` must be reachable only past the guard.
- [ ] Rewire expanded collapse, rail restore and View maps-sidebar to `sidebarToggledFromUser(true)`, `sidebarToggledFromUser(false)`, and `sidebarToggledFromUser(!sidebar.isCollapsed())`. Remove only `rail.restoreButton().addActionListener(event -> setCollapsed(false))` from the panel constructor. Keep its shared `CollapsedListener` programmatic and release-free.
- [ ] At divider left-button press, clear the override before setting `sidebarGestureActive`. At left double-click clear before `resetSidebarWidth` and use the existing reset completion path without a second drag commit. Wrap split-pane action-map entries `selectMin`, `selectMax`, `negativeIncrement`, and `positiveIncrement`: retain the original `Action`, clear the override, invoke it once, then finish through the existing width commit path once. Use a private keyboard-action guard if necessary to prevent the location-property callback duplicating the commit. Preserve the delegate's enabled state and Action metadata; install each wrapper once. Do not release in the divider-location property listener or no-gesture focus-loss branch.
- [ ] Run `gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.window.GraphWindowChromeShould' --tests 'org.freeplane.plugin.graph.window.MapSidebarPanelShould' --tests 'org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould' -PTestLoggingFull --console=plain`. Require green, including existing sidebar commit tests. In external mutant copies release from DIVIDER_LOCATION_PROPERTY and remove each pre-mutation guard independently; the automatic-refresh/property/no-gesture/guard tests must fail. A refresh that re-delivers B is mandatory to make the A-versus-B oracle falsifiable.
- [ ] Review `git diff --check`, stage exactly the four Files paths, and commit with the exact message `Preserve effective chrome and sidebar ownership across presentation [2026-10-14-graph-fullscreen-mode]`.

## Task 5: Implement viewport hold and wire concrete window transitions

**Implementer tier:** Frontier
**Lane:** window-integration
**Lane hint:** window-integration
**Category:** viewport-and-window-integration
**Depends on:** Tasks 1, 2, 3, 4

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java:219-270` (`show`/real close), `:450-486` (viewport listeners/direct publications), `:818-863` (existing viewport publishing), `:1074-1105` (root-pane key installation), and View menu after Settings; add the exact frame surface methods, model-owned viewport delegate, scheduler and frame-bounds observation in this file
- Modify/Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWindowChromeShould.java` (viewport/quiet-window probes, command-boundary tests and nested fake scheduler)
- Modify/Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java` (headless menu/key/recovery integration tests; preserve existing menu-list assertions with the one deliberate new item)

**Interfaces:**

- Consumes `ScreenFillSurface.FrameState(Rectangle, int)`, defensive `bounds(): Rectangle`, `extendedState(): int`, and `ScreenFillSurface.Survival` values `RESTORED_WINDOWED`, `REENTERED_FULLSCREEN`, `UNKNOWN_FRAME`.
- Consumes `GraphPresentationMode(ScreenFillSurface, PresentationChrome, PresentationViewport, PresentationHint, JCheckBoxMenuItem, Runnable, Consumer<String>)` and its injected-boolean overload; `isAvailable(): boolean`, `toggle(): void`, `isActive(): boolean`, `requestExit(): void`, `windowClosed(): void`.
- Consumes model `PresentationChrome.capture()/hide()/restore()/discard(): void` from Task 4, and Task 1's `PresentationViewport.capture()/compensate()/disarm()/windowClosed(): void`, `classify(javax.swing.event.ChangeEvent): void`, `canvasPublication(): void`, `toolbarPublication(org.freeplane.plugin.graph.canvas.GraphViewport): void`.
- Consumes `PresentationHint(JLayeredPane, String)` with `show()/hide()/dispose(): void`; `GraphInteractionController.setEscapeFallback(Runnable): void`; `GraphCanvas.runWhenViewportSettled(Runnable): void`, `isViewportPositioningActive(): boolean`, `viewport(): GraphViewport`, `visibleViewport(): GraphViewport`, `setViewport(GraphViewport): void`, `getSize(): Dimension`.
- Produces `GraphWorkspaceWindow implements ScreenFillSurface` with public overrides `FrameState captureFrameState()`, `Survival applyScreenFill(FrameState previous)`, `Survival restoreFrameState(FrameState previous)`, and package-private `void rawEnterFullScreen()`, `void rawRestoreWindowed(FrameState previous)`. Interface overrides are public as required by Java; only the raw methods are package-private.
- Produces static package-private nested `GraphWorkspaceWindow.FrameTransitions` with `FrameTransitions(Runnable enterRaw, Consumer<FrameState> restoreRaw, Consumer<String> log)`, `Survival applyScreenFill(FrameState previous)`, and `Survival restoreFrameState(FrameState previous)`. The real surface delegates to this same bounded helper; tests instantiate it without constructing a JFrame. This is the sole recovery path, not a test replica or second production transition path.
- Produces private model-owned `ViewportHold implements PresentationViewport`; expose package-private `PresentationViewport presentationViewport()` returning the single delegate. The model still owns canvas, scroll pane, `suppressViewportEvents`, `lastPublishedViewport`, and `applyingViewport`.
- Produces nested package-private model interfaces `PresentationScheduler { long nowMillis(); ScheduledWork schedule(int delayMillis, Runnable callback); }` and `ScheduledWork { void cancel(); }`, package-private `void setPresentationScheduler(PresentationScheduler scheduler)` usable only before a hold is armed/closed, and `void presentationFrameGeometryChanged(Rectangle bounds)`. Default time is monotonic milliseconds from `System.nanoTime()`; default scheduling is a one-shot EDT `javax.swing.Timer`.
- Produces package-private model `GraphPresentationMode createPresentationMode(ScreenFillSurface surface, JLayeredPane host, Runnable focusCanvas, Consumer<String> log)`: called once after menu creation; constructs the translated hint/mode with the model as chrome and `presentationViewport()` as viewport, installs the Escape fallback and menu listener, and returns the same mode retained by the window.
- Produces package-private static `void GraphWorkspaceWindow.installPresentationKeys(JRootPane root, GraphPresentationMode mode)` for one root-pane binding and headless inspection. Model-owned viewport assignments run through private `void applyModelViewport(Runnable application)` with applying/suppression guards; user publications use private `void emitViewportIfChanged(GraphViewport value)`, extracted from the existing emission/dedupe body so command conversion/routing stays identical.

### Steps

- [ ] Add a nested `ViewportProbe` in `GraphWindowChromeShould`, reusing Task 4's real model/command fixture. It holds real `GraphCanvas`, `JScrollPane`, `JViewport`, model delegate, and `FakePresentationScheduler implements PresentationScheduler`. The fake stores `(deadline, sequence, callback, cancelled)` records; `advanceTo(long millis)` runs due callbacks in deadline/sequence order on the EDT without sleeping. Keep cancelled callbacks callable from the test to prove generation checks rather than merely proving cancellation flags. Observe private armed/generation/baseline state through test reflection; add no public probe API.

Use a large non-origin graph: nodes spanning at least `(-2048,-1536)` to `(2048,1536)`, anchor `GraphViewport.of(123.5, -87.25, 1.5)`, nonzero in-range scroll `(713,419)`. After initialization and pending work drain, checkpoint commands and record actual canvas size/extent/position. For parity cases set/lay out real component sizes until each asserted combination is reached, capture only after asserting their parity, then change extent and canvas size independently. Preserve the initial zoom exactly. The position oracle is `round(P0 + ((W1-W0)-(E1-E0))/2)` on each axis; the spec's §7.3 `P0 = ...` notation is interpreted as the target `P1` defined in §5.1.

- [ ] Add all twenty-two exact viewport tests below. A test must assert the triggering event/counter/geometry actually occurred and check both resulting view and commands; do not settle for invoking a fake classifier with unchanged geometry. Run the focused chrome class and record behavioral red assertions before implementing hold behavior.

| Test method | Required mechanism and oracle |
| --- | --- |
| `preservesCenterForEvenCanvasAndEvenExtentGeometry` | Force even/even x and y sizes, hide/show sidebar/settings and resize, assert actual target and visible-center error <= 0.5 device pixel per axis |
| `preservesCenterForOddCanvasAndEvenExtentGeometry` | Force odd/even, same view/zoom/zero-command assertion |
| `preservesCenterForEvenCanvasAndOddExtentGeometry` | Force even/odd, same assertion |
| `preservesCenterForOddCanvasAndOddExtentGeometry` | Force odd/odd, same assertion |
| `compensatesLateExtentChangeAfterTransitionReturns` | Return from transition, then add 200 pixels to extent while armed; baseline center unchanged, zero command |
| `recomputesAbsolutelyAfterOddShrinkWithoutCompounding` | Baseline position 37, extent +1 -> exact target 36.5 rounded to 37, then surface -1 -> exact target 36; force the canvas integer compensation/clamp path and require absolute target rather than accumulated result 37 |
| `logsClampResidualAndPinsContentEdge` | Near-scroll-limit and smaller-than-extent/empty-graph cases; requested target outside range -> applied edge, residual/requested/applied log, unchanged anchor/zoom, zero command |
| `unchangedCanvasDeliveryDoesNotPublishOrMoveCenter` | Deliver actual unchanged CanvasState after transition; effective center/zoom stable, zero command |
| `classifiesAnchorChangeAsUserBeforePositioningMarker` | Change anchor via pan/wheel/fit/reset while depth positive, require one baseline user viewport publication and disarm before further geometry |
| `classifiesScrollbarDragAsUserWhenExtentAndSurfaceAreUnchanged` | Move view position with stable extent/surface/anchor; one publication, disarmed, no snapback |
| `classifiesKeyboardScrollAsUserWhenPendingClampExistsWithoutActivePositioning` | Assert pending clamps >0 and positioning depth ==0; invoke real keyboard scroll; publication/disarm, no snapback |
| `ignoresActiveProgrammaticPositioningAndKeepsHoldArmed` | Unchanged anchor with depth >0; zero command, armed, one settle registration |
| `toolbarPublicationPublishesBeforeDisarm` | Toolbar zoom/fit/reset without requiring a viewport event; one command reaches handle before cache reconcile/disarm, later geometry cannot restore old view |
| `canvasPublicationPublishesBeforeDedupeReconcileAndDisarm` | Direct canvas callback changes view; observe handle call before cache/armed change; one command |
| `settleCallbackCorrectsAfterClampWithoutFinalViewportEvent` | Force real odd-shrink sequence whose interim target moves 37 -> 37.5 -> 38; complete clamp with no final change event; completion callback corrects to absolute target 37 |
| `settleCallbackRegistrationIsIdempotentPerGeneration` | Multiple active-positioning events in one generation cause one pending registration/correction, not multiple callbacks |
| `settleCallbackRunsImmediatelyWhenAlreadySettled` | Both canvas counters zero at registration; correction occurs on EDT without another event/timer |
| `userDisarmCancelsSettleCallback` | Publish user view, disarm, then complete saved callback; view stays user's view and no extra command |
| `closeCancelsSettleCallbackAndIgnoresLateGeneration` | Close with pending correction, invoke saved callback and old-generation callback after new capture; no correction/rearm/publication |
| `modelAppliedViewportDoesNotPublishOrDisarm` | Model initial/setViewport application during hold; event ignored by applying guard even when anchor differs; armed, no command |
| `exitRecapturesCurrentViewAfterEntryHoldDisarmed` | Pan/zoom while active, record changed view, exit; fresh baseline preserves changed center/zoom through late exit geometry |
| `publishedViewportIsNotSwallowedByReconcileBeforeDisarm` | Force user view differing from lastPublishedViewport; inspect actual handle argument/count, require one emission before reconcile |

The general non-compounding test uses a different concrete arithmetic fixture from the settle-callback `37 -> 37.5 -> 38` probe; keep both. The settle probe must drive the actual canvas clamp queue and prove no final viewport event, so removing completion re-correction fails it.

- [ ] Add the exact quiet-window tests `holdNeverDisarmsBeforeTwoThousandFiveHundredMilliseconds`, `holdDisarmsOnFirstQuietWindowAtOrAfterPlatformHorizon`, `holdReleasesAtFiveSecondCapAndLogsSettlementFailure`, `newTransitionCancelsPreviousQuietWindowGeneration`, `closeCancelsQuietWindowGeneration`, `disarmReconcilesWithoutPublishing`, and `subsequentIndependentResizeUsesBaselineAfterHoldDisarms`. With origin `t=0`, no events must leave the hold armed at 2499 ms; at 2500 ms it may disarm only if the preceding 200 ms were quiet. An event at 2450 ms keeps it armed at 2500 and 2649, then allows disarm at 2650. Repeated frame/viewport events spaced <200 ms through 5000 force capped disarm with generation and last-event timestamps logged. Move frame bounds without changing extent to prove frame observation participates. New capture and close invalidate retained old scheduled callbacks. After normal disarm an independent scroll/resize follows the pre-feature publication behavior.

- [ ] Implement delegate capture, absolute arithmetic and cache reconciliation. Fields include `armed`, `closed`, monotonically increasing `generation`, defensive baseline `Point P0`, `Dimension E0/W0`, immutable `GraphViewport A0`, last-observed extent/surface (distinct from the immutable baseline), reentrancy flag, baseline suppression state, last transition operation/frame/viewport times, quiet handle, and registered settle generation. Model `applyingViewport` is separate from the canvas positioning marker. On re-capture cancel the old generation first, capture the current view afresh, and preserve the suppression state from before the first armed hold rather than saving an already-held true flag.

```java
private int targetAxis(int p0, int w0, int e0, int w1, int e1) {
    double requested = (double) p0
        + (((double) w1 - w0) - ((double) e1 - e0)) / 2.0;
    long rounded = Math.round(requested);
    int maximum = Math.max(0, w1 - e1);
    int applied = (int) Math.max(0L, Math.min((long) maximum, rounded));
    if (rounded != applied) {
        logClamp(requested, rounded, applied, requested - applied);
    }
    return applied;
}
```

`logClamp(double requested, long rounded, int applied, double residual)` logs those values through the existing message sink. The double cast precedes every difference; the final cast follows range clamp. Apply both target axes under suppression/reentrancy protection, never call `canvas.setViewport` or change zoom from compensation, and set `lastPublishedViewport = canvas.visibleViewport()` only after applying. Compensating again derives from the same P0/E0/W0, not the last applied position. Keep suppression effective throughout an armed hold and correctly balance existing nested suppression scopes when disarming.

- [ ] Replace the viewport change listener with `presentationViewport().classify(event)` after existing model listener setup. Timestamp every viewport event for quiet observation, including ignored model/programmatic events. Under an active hold classify in the exact order: applyingViewport -> ignore; anchor differs from A0 -> user publish/reconcile/disarm; active positioning with same anchor -> keep armed/register settle; stable anchor/observed extent/observed surface -> user scroll publish/reconcile/disarm; changed extent/surface -> absolute compensation and stay armed. Compare size stability with the immediately previously observed geometry, not permanently with E0/W0; refresh observation after handling the event. Ignore reentrant events caused by the delegate's own position write. Never use `isProgrammaticViewportChange()` for active-positioning classification.

Refactor only the existing emission/dedupe body into `emitViewportIfChanged(GraphViewport value)`; it retains the baseline writable/read-only checks, conversion to the existing command type, and handle routing. Ordinary `publishExternalViewport` remains suppressed for model/geometry work; a classified user event deliberately calls this common emission body despite the hold's suppression. No duplicate command implementation is introduced.

- [ ] Update direct `publishCanvasViewport` and `publishToolbarViewport(GraphViewport value)` to first reject model applications, then publish through the common emission body before calling `canvasPublication()` or `toolbarPublication(value)`. Those delegate methods reconcile the cache then disarm; they never publish a second command. Suppression for geometry must not swallow genuine direct user entry. Wrap `applyInitialViewport` and every model-owned `canvas.setViewport` in `applyModelViewport(() -> ...)`:

```java
private void applyModelViewport(Runnable application) {
    boolean previousApplying = applyingViewport;
    applyingViewport = true;
    try {
        runWithViewportEventsSuppressed(application);
    }
    finally {
        applyingViewport = previousApplying;
    }
}
```

No cache prewrite may precede the command boundary on a user path. After user disarm, preserve the just-published user view; neither cancellation nor reconciliation compensates it back.

- [ ] Register the settle correction once for the current generation when active positioning is observed; mark the generation registered before calling `canvas.runWhenViewportSettled(...)` because it may fire immediately. The callback requires open model, armed hold and matching generation, then re-runs absolute compensation. It does not recursively register another already-settled callback. Disarm/close/new capture invalidate the generation registration and replace the canvas's pending callback with one static non-capturing `Runnable NO_SETTLE_CORRECTION = () -> { };` via `runWhenViewportSettled(NO_SETTLE_CORRECTION)`. Never pass null and never add a cancellation method to GraphCanvas. This removes the callback's reference to the model while preserving the two-member canvas API. Stale saved callbacks independently check generation/armed/closed even after cancellation.

`disarm()` reconciles `lastPublishedViewport` first, cancels current quiet/settle work, then clears armed state and releases only the hold's suppression ownership. `windowClosed()` first invalidates generation/open state, cancels all work, reconciles without publishing, and disarms permanently. Calling it from both mode cleanup and model close is safe and idempotent.

- [ ] Implement the single quiet-window chain using injected scheduler and frame-bounds observation. Public delegate `compensate()` called by the mode marks the last explicit transition geometry operation after frame/chrome finalization; geometry-event and settle-callback compensation share the same absolute calculation without restarting the horizon/cap. Seed the origin in capture so early-failure paths still have bounded cleanup. Settle callbacks invoke compensation under a private callback-origin flag to avoid treating callback correction as a new transition operation.

Observe frame moves/resizes in a single window component listener calling `model.presentationFrameGeometryChanged(getBounds())`; compare a defensive saved Rectangle and update `lastFrameEventAt` only when bounds actually differ. All viewport events update `lastViewportEventAt`. Each scheduled callback captures generation and returns if stale/closed/disarmed. Its decision is:

```text
horizon = lastTransitionGeometryAt + 2500
cap = lastTransitionGeometryAt + 5000
quietAt = max(lastFrameEventAt, lastViewportEventAt) + 200
if now >= horizon and now >= quietAt: disarm normally
else if now >= cap: log settlement failure(generation, frame/event times); disarm
else schedule next observation for min(cap, max(horizon, quietAt)), at least 1 ms ahead
```

Keep only one scheduled observation handle. No automatic event resets the absolute cap. Ensure frame/component listeners and timers are removed/stopped before model registrations close. Keep the hold armed through FrameResynchronizer's ten 200 ms retries; at/after the cap report failure rather than silent success.

- [ ] Implement the concrete surface and its injectable bounded helper. Capture `new FrameState(getBounds(), getExtendedState())` before any mutation. `FrameTransitions.applyScreenFill` invokes the injected enter once; on RuntimeException/Error logs it and invokes restore once; return `REENTERED_FULLSCREEN` on primary success, `RESTORED_WINDOWED` on recovery success, and `UNKNOWN_FRAME` when both fail. `restoreFrameState` is symmetric. Log primary and inverse exceptions, containing logger failures. The window surface methods delegate to that same helper constructed with `this::rawEnterFullScreen` and `this::rawRestoreWindowed`.

```java
void rawEnterFullScreen() {
    Rectangle graphics = getGraphicsConfiguration().getBounds();
    dispose();
    setUndecorated(true);
    setExtendedState(Frame.MAXIMIZED_BOTH);
    setBounds(graphics);
    if (Compat.isWindowsOS()) setResizable(false);
    setVisible(true);
    validate();
}
void rawRestoreWindowed(FrameState previous) {
    dispose();
    setUndecorated(false);
    setBounds(previous.bounds());
    setExtendedState(previous.extendedState());
    if (Compat.isWindowsOS()) setResizable(false);
    setResizable(true);
    setVisible(true);
    validate();
}
```

Raw operations never invoke each other or the close lifecycle. Keep `show()` checking the existing closed flag, so a reentrant close cannot re-show the frame.

- [ ] Construct the full-screen `JCheckBoxMenuItem` after Settings with name `graph-workspace-full-screen-menu-item` and translation key `graph_workspace.action.full_screen`; add no accelerator. Create mode only after the menu/model exists, using `new PresentationHint(host, translatedExitHint)` where `translatedExitHint` comes from `graph_workspace.hint.full_screen_exit`. Pass `() -> canvas().requestFocusInWindow()` as Runnable and the existing command-message sink. Install `interactionController.setEscapeFallback(presentationMode::requestExit)` in the model factory and attach one menu listener to `presentationMode.toggle()`.

`installPresentationKeys` places one `KeyStroke.getKeyStroke(KeyEvent.VK_F11, 0)` mapping in `root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)` and one ActionMap entry, named `graph-workspace-toggle-full-screen`. Its action checks `mode.isAvailable()` then calls `mode.toggle()` once. Install no canvas F11 listener, menu accelerator or root Escape path. Add the frame listener once and preserve existing history key setup and FrameResynchronizer installation.

In `closeOnEdt()`, set `closed` first, call `presentationMode.windowClosed()` before frame disposal, then close model/registrations and dispose using existing real-close ordering. The model also closes its viewport delegate before other registrations. Do not bind mode lifecycle to WINDOW_CLOSED or temporary dispose. Mode close must remain exactly once from the real path; delegated viewport cleanup is idempotent.

- [ ] Add exact headless methods `fullScreenMenuItemHasExpectedNameAndTranslationKey`, `fullScreenMenuItemIsDisabledAndUncheckedWhenUnavailable`, `f11IsBoundOnceOnFocusedWindowAndNoMenuAcceleratorExists`, `f11UnavailableActionDoesNothing`, and `modelFrameRecoveryReportsObservableSurvivalAndBoundedCalls` to `GraphWorkspaceWindowModelShould`. Use a real `JRootPane` and actual binding helper with supported=true/false injected modes to avoid requiring JFrame headlessly. Assert the View item position after Settings and the deliberate updated menu list, rather than dropping the existing list assertion.

For concrete recovery instantiate `GraphWorkspaceWindow.FrameTransitions` with injected raw callbacks maintaining observable `(decorated, bounds, extendedState, visible)` state. Test requested success, primary failure/inverse success, and both failures for both directions, asserting 1/2 calls, correct order, resulting observable state/Survival, and logs. Disable inverse recovery in a mutant of the actual helper and require this test to fail. Use a display-backed spy in Task 6 to pin actual raw setter order as well; the fake alone cannot prove raw JFrame order.

- [ ] Complete exact §7.2 tests `presentationEnterAndExitEmitNoWorkspaceCommandInWritableMode`, `presentationEnterAndExitEmitNoWorkspaceCommandInReadOnlyMode`, and `displaySettingsAndViewportCommandMutantsFailTheNoCommandAssertion` in `GraphWindowChromeShould`. Use real model chrome and viewport collaborators with the actual mode and a fake frame surface; entry, exit and late geometry must leave the handle's post-initialization command list empty. Do not replace the model with a no-op fake. Inject one display command and then one viewport command through the same `GraphWorkspaceHandle.execute` boundary; each must cause the original zero-command assertion to throw `AssertionError` in a controlled falsifiability test. The mutation oracle must observe all command types, not just these two filters.

- [ ] Run `gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.window.GraphWindowChromeShould' --tests 'org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould' --tests 'org.freeplane.plugin.graph.window.MapSidebarPanelShould' --tests 'org.freeplane.plugin.graph.window.GraphPresentationModeShould' --tests 'org.freeplane.plugin.graph.canvas.GraphInteractionControllerShould' -PTestLoggingFull --console=plain`. Record method-level results, target/actual P/E/W, center/zoom, event and command counts, clamp residuals, callback registrations, scheduler timestamps, and generation invalidation evidence.
- [ ] In external worktree copies mutate each mechanism independently, run the owning class with `--rerun-tasks`, and require the following named failure before accepting green evidence:

| Mutant | Must fail |
| --- | --- |
| Accumulate correction from last position | `recomputesAbsolutelyAfterOddShrinkWithoutCompounding` |
| Use isProgrammaticViewportChange instead of active-positioning accessor | `classifiesKeyboardScrollAsUserWhenPendingClampExistsWithoutActivePositioning` |
| Cache/reconcile before user emission | `publishedViewportIsNotSwallowedByReconcileBeforeDisarm` |
| Omit toolbar direct disarm | `toolbarPublicationPublishesBeforeDisarm` |
| Omit canvas direct disarm | `canvasPublicationPublishesBeforeDedupeReconcileAndDisarm` |
| Omit settle re-correction | `settleCallbackCorrectsAfterClampWithoutFinalViewportEvent` |
| Remove 2500 ms minimum | `holdNeverDisarmsBeforeTwoThousandFiveHundredMilliseconds` |
| Remove 5000 ms cap | `holdReleasesAtFiveSecondCapAndLogsSettlementFailure` |
| Add second F11 menu-accelerator path | `f11IsBoundOnceOnFocusedWindowAndNoMenuAcceleratorExists`; display one-press test in Task 6 |
| Remove concrete inverse raw operation | `modelFrameRecoveryReportsObservableSurvivalAndBoundedCalls` |

Task 3 and Task 4 own the remaining Escape, sidebar-release and pre-mutation-guard mutants; do not edit their lane's files here. Keep mutation changes out of commits and preserve the original passing implementation.

- [ ] Review `git diff --check`, stage exactly the three Files paths, and commit with the exact message `Hold graph viewport through bounded presentation transitions [2026-10-14-graph-fullscreen-mode]`.

## Task 6: Translate controls and verify display acceptance

**Implementer tier:** Advanced
**Lane:** window-integration
**Lane hint:** window-integration
**Category:** translations-and-acceptance
**Depends on:** Tasks 1, 2, 3, 4, 5

**Files:**

- Modify: `freeplane/src/viewer/resources/translations/Resources_en.properties` (the existing `graph_workspace.*` group)
- Modify/Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java` (display-required methods and nested display fixture; preserve Task 5's headless tests)

All acceptance logs/images/init scripts are external untracked artifacts under `/tmp/graph-fullscreen-mode-evidence`, not additional repository files. This task consumes production from Task 5 and does not modify it.

**Interfaces:**

- Consumes `GraphPresentationMode.isAvailable(): boolean`, `isActive(): boolean`, `toggle(): void`, `requestExit(): void`, `windowClosed(): void`; the real root-pane F11 action installed through `GraphWorkspaceWindow.installPresentationKeys(JRootPane, GraphPresentationMode): void`; `ScreenFillSurface` frame state/survival contract; real window `closeOnEdt()` lifecycle and existing guarded `show()`.
- Consumes `PresentationHint(JLayeredPane, String, int, TimerFactory)` and `TimerFactory.create(int, Runnable): javax.swing.Timer` for independently display-hosted hint testing. For the hint created by the real window, the display fixture obtains its existing timer through test reflection and injects a short `setInitialDelay(25)` before the entry-triggered restart; do not add a production timing setter or second mode instance. Task 1 already proves the constructor-injected timer seam deterministically.
- Consumes real canvas `viewport()/visibleViewport(): GraphViewport`, interaction-controller Escape fallback and normal pan/zoom paths, concrete frame methods and existing command capture at `GraphWorkspaceHandle.execute(GraphCommand)`.
- Produces exactly these English key/value pairs:

```properties
graph_workspace.action.full_screen=Full Screen
graph_workspace.hint.full_screen_exit=Esc or F11 exits full screen
```

- Produces nested `DisplayFixture implements AutoCloseable` in the existing test file, with real graph window/model/canvas, recording command handle, captured bounds/extended state, `Robot`, and `pressF11(): void`, `pressEscape(): void`, `awaitEdt(): void`, `close(): void`. Helpers create the real window using the existing window-model test construction path, flush EDT outside EDT, and dispose in finally; no production test fixture or new tracked test file is introduced.

### Steps

- [ ] Add exact display-required methods below. Begin each with `Assume.assumeFalse(GraphicsEnvironment.isHeadless())`; keep all non-display state-machine/exception/command/viewport tests active headlessly. Use real Robot key presses while the canvas is focused, `SwingUtilities.invokeAndWait` for component access, and bounded condition polling outside the EDT with a 5,000 ms test timeout. Focus assertions await actual focus ownership; do not replace a real press with a direct toggle call in the one-press test.

| Test method | Required assertions |
| --- | --- |
| `f11MakesWindowUndecoratedAndFillsCurrentScreen` | Capture live current graphics bounds; press F11 once; frame undecorated, screen bounds equal, all chrome hidden, checked/active, visible-center/zoom preserved |
| `f11RestoresBoundsAndExtendedStateFromNormalWindow` | Capture normal bounds/state; enter and exit with actual F11; decorated, exact captured bounds/state, effective chrome/sidebar restored, same current center/zoom |
| `f11RestoresBoundsAndExtendedStateFromMaximizedWindow` | Begin maximized, capture the exact extended-state bits before entry; exit restores them exactly and the restored bounds equal the maximized geometry of the frame's graphics configuration (the pre-entry AWT bounds may be a stale X11/KWin cache value and are not the acceptance target) |
| `escapeExitsWhenCanvasHasNoPreviewOrSelection` | Active mode and canvas with neither; press Escape, event consumed and inactive/restored; separate preview/selection assertions remain owned by Task 3 |
| `panDuringPresentationIsPreservedThroughExit` | Real pan and wheel zoom while active; changed visible view becomes exit baseline, survives exit and late geometry; checkpoint command count after user actions and require transition adds zero |
| `canvasHasFocusAfterEntryAndExit` | After each surviving transition, await canvas.isFocusOwner rather than merely counting callback invocation |
| `oneF11PressPerformsExactlyOneToggle` | One key press/release causes exactly one protected frame operation and one active-state change; a second press exits once; repeat with menu bar hidden |
| `temporaryDisposeDoesNotClosePresentationMode` | Enter/exit cycle includes both temporary dispose calls; mode, chrome, hint and hold stay operational until real close; next cycle works |
| `closeDuringTransitionLeavesDisposedWindowWithoutError` | Inject real close from the concrete frame-transition seam; final frame not displayable, mode terminal, no re-show, no uncaught EDT error, late callbacks inert |
| `hintShowsOnEntryAndIsRemovedAfterInjectedDelay` | Observe the real layered-pane hint on entry using controlled timer timing; trigger the injected 25 ms expiry and require invisibility, then exit/close require no visible/orphan component; old callback cannot reshow it |

Add `rawFrameOperationsFollowSpecifiedOrder` as a display-required concrete-surface probe: use the existing Mockito spy support to record real JFrame setter calls while delegating them, assert the Task 5 enter/restore sequence exactly including the Windows resizable calls. The spy records an explicit ordered operation list; swapping two raw operations in an external mutant must fail this test. The headless recovery helper test separately covers bounded inverse attempts.

- [ ] Run the class headlessly to retain existing coverage and observe the new tests are skipped, not claimed as display evidence. Edit the English resource with the two exact ASCII entries and run formatting:

```bash
gradle format_translation --console=plain
rg -n '^graph_workspace\.(action\.full_screen|hint\.full_screen_exit)=' freeplane/src/viewer/resources/translations/Resources_en.properties
git diff --name-only
```

Require each exact key once and exactly the specified values. Translation resources remain ISO-8859-1 with non-ASCII expressed as Unicode escapes; these new entries are ASCII. Review formatter output and retain only the allowed English resource change; preserve unrelated baseline/user changes. Do not include another translation in the commit. The headless resource/menu test must now resolve the actual English text, not only a missing-key fallback.

- [ ] Run the complete focused set after resource formatting, then the full graph-plugin suite once:

```bash
gradle :freeplane_plugin_graph:test \
  --tests 'org.freeplane.plugin.graph.window.GraphPresentationModeShould' \
  --tests 'org.freeplane.plugin.graph.window.GraphWindowChromeShould' \
  --tests 'org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould' \
  --tests 'org.freeplane.plugin.graph.window.MapSidebarPanelShould' \
  --tests 'org.freeplane.plugin.graph.canvas.GraphInteractionControllerShould' \
  -PTestLoggingFull --console=plain
gradle :freeplane_plugin_graph:test -PTestLoggingFull --console=plain
```

Require BUILD SUCCESSFUL and record executed/skipped counts from `freeplane_plugin_graph/build/test-results/test/TEST-*.xml`. Check all names required by specification §7 are represented in the relevant class and report any skipped required display case separately. Preserve impacted existing tests, especially `cancelConnectionPreviewBeforeClearingSelection`, menu item order, and ordinary sidebar commit behavior.

- [ ] Run the display-required class on a real available non-headless X11 display with a window manager. Ensure a Gradle test JVM setting cannot silently force it headless: create external `/tmp/graph-fullscreen-mode-evidence/display-tests.gradle` with the exact init script below, then run the command. This does not modify repository build files.

```groovy
gradle.projectsEvaluated {
    allprojects {
        tasks.withType(org.gradle.api.tasks.testing.Test).configureEach {
            jvmArgs = (jvmArgs ?: []).findAll { !it.startsWith('-Djava.awt.headless=') }
            systemProperty 'java.awt.headless', 'false'
        }
    }
}
```

```bash
gradle --init-script /tmp/graph-fullscreen-mode-evidence/display-tests.gradle \
  :freeplane_plugin_graph:test \
  --tests 'org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould' \
  --rerun-tasks -PTestLoggingFull --console=plain
```

Require all ten required display methods and the raw-order probe to execute with zero skips, not just a successful Gradle status. Do not equate bare Xvfb without a window manager with X11/KWin popup/maximize evidence. If a required platform/display is unavailable, retain green headless evidence and report the missing runtime verification as an acceptance blocker with the exact affected probes; do not mark it passed.

- [ ] Build runnable artifacts using `gradle dist --console=plain`, then launch `BIN/freeplane.sh` on the real X11/KWin display with an isolated user profile using the launcher's existing supported profile option. Capture the following external acceptance record and images; use existing app/test tooling rather than introducing a new plugin test command or changing product UI:

1. Record initial frame bounds/extended state, all effective chrome/sidebar values, visible center/zoom, command checkpoint and writable/read-only status. Press F11 and capture an undecorated current-screen canvas with the exact exit hint visible; wait for the normal 3,000 ms hint expiry and capture again.
2. First cycle: make no viewport user action; Escape with empty selection/preview restores bounds/chrome/current center/zoom. Record handle command count immediately after exit and after late geometry/the complete 2,500 ms settling horizon; no added command of any type. Repeat for read-only.
3. Separate cycle: pan and zoom while active, record the resulting view and user-command checkpoint, then exit; the changed view remains through exit and late geometry with zero added transition commands. Do not require zero total commands across genuine user pan/zoom.
4. Open a real graph popup after entry and after exit, record its pointer-relative position and normal dismissal under X11/KWin; verify FrameResynchronizer remains installed. Probe a second monitor by moving the live frame there before entry and compare with that frame's current GraphicsConfiguration bounds. Record platform/monitor details and screenshots.
5. Confirm parentless purge/close/contributor dialogs survive temporary dispose; a focused modal dialog owns keys, so F11 is not expected underneath it. Confirm real close stops timers/hold callbacks and cannot re-show the window.
6. Record injected-failure results from Tasks 2 and 5, including at most one inverse, actual surviving frame/chrome/menu state, secondary logs, and no EDT exception. Include numerical viewport parity/late-extent/odd-shrink/clamp/pending-clamp/publish-order/settle/quiet-window outputs and the named mutation failures from Tasks 3–5.
7. On Linux/Windows record supported behavior. For macOS v1 record the injected negative-gate tests and, when a macOS runtime is available, verify disabled/unchecked item, no-op F11/fallback and no native/frame/chrome/viewport/hint/command effect. Native macOS fullscreen remains explicitly deferred and is not an implementation gap.
8. Any settlement failure at/beyond the 5,000 ms cap or geometry residual beyond the promised horizon is recorded as failure with generation/timestamps/residual, never as silent preservation success. Distinguish a logged, contract-permitted scroll-boundary clamp from an unreported center drift.

- [ ] Complete the specification coverage audit against the table before Task 1 and the test-name tables in Tasks 2–6. Review `git diff --check`, resource values, lane file ownership and no extra canvas/public/native/accelerator paths. Stage exactly the two Files paths and commit with the exact message `Translate full-screen controls and verify display acceptance [2026-10-14-graph-fullscreen-mode]`. Include in the task report the focused/full-suite/display counts, commands and results, evidence directory, and any runtime acceptance blocker; do not claim implementation acceptance while required display evidence remains missing.
