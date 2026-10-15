package org.freeplane.plugin.graph.window;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.awt.Frame;
import java.awt.Rectangle;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JLabel;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.ChangeEvent;

import org.freeplane.plugin.graph.canvas.GraphViewport;
import org.junit.Test;

public class GraphPresentationModeShould {
    @Test
    public void frameStateDefensivelyCopiesBoundsAndPreservesExtendedState() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Rectangle original = new Rectangle(11, 22, 900, 700);
            ScreenFillSurface.FrameState saved =
                new ScreenFillSurface.FrameState(original, Frame.MAXIMIZED_BOTH);
            original.x = 999;
            saved.bounds().width = 1;
            assertThat(saved.bounds()).isEqualTo(new Rectangle(11, 22, 900, 700));
            assertThat(saved.extendedState()).isEqualTo(Frame.MAXIMIZED_BOTH);
        });
    }

    @Test
    public void hintShowRestartsInjectedTimerAndExpiryHidesPanel() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            HintFixture fixture = new HintFixture();
            JPanel panel = fixture.panel();
            assertThat(panel.isVisible()).isFalse();
            fixture.hint.show();
            fixture.hint.show();
            assertThat(fixture.timer.restarts).isEqualTo(2);
            assertThat(fixture.delay).isEqualTo(3000);
            assertThat(panel.isVisible()).isTrue();
            assertThat(panel.getX()).isEqualTo((1000 - panel.getWidth()) / 2);
            assertThat(JLayeredPane.getLayer(panel)).isGreaterThan(JLayeredPane.DEFAULT_LAYER);
            fixture.host.setSize(1200, 700);
            for (ComponentListener listener : fixture.host.getComponentListeners()) {
                listener.componentResized(new ComponentEvent(fixture.host, ComponentEvent.COMPONENT_RESIZED));
            }
            assertThat(panel.getX()).isEqualTo((1200 - panel.getWidth()) / 2);
            fixture.expiry.get().run();
            assertThat(panel.isVisible()).isFalse();
            fixture.hint.dispose();
        });
    }

    @Test
    public void hintHideStopsTimerAndDisposeMakesExpiryInert() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            HintFixture fixture = new HintFixture();
            fixture.hint.show();
            fixture.hint.hide();
            assertThat(fixture.panel().isVisible()).isFalse();
            assertThat(fixture.timer.stops).isEqualTo(1);
            Runnable lateExpiry = fixture.expiry.get();
            fixture.hint.dispose();
            fixture.hint.dispose();
            int stops = fixture.timer.stops;
            lateExpiry.run();
            fixture.hint.show();
            assertThat(fixture.host.getComponentCount()).isZero();
            assertThat(fixture.host.getComponentListeners()).isEmpty();
            assertThat(fixture.timer.stops).isEqualTo(stops);
            assertThat(fixture.timer.restarts).isEqualTo(1);
        });
    }

    @Test
    public void hintNeverAcceptsFocusOrInterceptsMouse() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            HintFixture fixture = new HintFixture();
            fixture.hint.show();
            JPanel panel = fixture.panel();
            JLabel label = (JLabel) panel.getComponent(0);
            assertThat(panel.isFocusable()).isFalse();
            assertThat(label.isFocusable()).isFalse();
            assertThat(label.getText()).isEqualTo("Press Esc to exit full screen");
            assertThat(panel.contains(panel.getWidth() / 2, panel.getHeight() / 2)).isFalse();
            fixture.hint.dispose();
        });
    }

    @Test
    public void enterCapturesBeforeMutationAndActivatesAfterFullscreenSurvival() throws Exception {
        onEdt(() -> {
            ModeFixture fixture = new ModeFixture(true);
            fixture.mode.toggle();
            assertThat(fixture.mode.isActive()).isTrue();
            assertThat(fixture.menu.isSelected()).isTrue();
            assertOrder(fixture.calls, "captureViewport", "captureFrame", "captureChrome",
                "hideChrome", "applyScreenFill", "compensate", "focus", "restartHintTimer");
        });
    }

    @Test
    public void enterRestoresChromeAndStaysInactiveWhenFrameRecoveryLeavesWindowed() throws Exception {
        onEdt(() -> {
            ModeFixture fixture = new ModeFixture(true);
            fixture.surface.enterResult = ScreenFillSurface.Survival.RESTORED_WINDOWED;
            fixture.mode.toggle();
            assertThat(fixture.mode.isActive()).isFalse();
            assertThat(fixture.menu.isSelected()).isFalse();
            assertOrder(fixture.calls, "applyScreenFill", "restoreChrome", "compensate");
            assertThat(fixture.timer.restarts).isEqualTo(0);
        });
    }

    @Test
    public void enterRestoresChromeAndStaysInactiveWhenFrameSurvivalIsUnknown() throws Exception {
        onEdt(() -> {
            ModeFixture fixture = new ModeFixture(true);
            fixture.surface.enterResult = ScreenFillSurface.Survival.UNKNOWN_FRAME;
            fixture.mode.toggle();
            assertThat(fixture.mode.isActive()).isFalse();
            assertThat(fixture.menu.isSelected()).isFalse();
            assertOrder(fixture.calls, "applyScreenFill", "restoreChrome", "compensate");
            assertThat(fixture.logs).anyMatch(value -> value.contains("UNKNOWN_FRAME"));
        });
    }

    @Test
    public void exitRestoresChromeAndDeactivatesAfterWindowedSurvival() throws Exception {
        onEdt(() -> {
            ModeFixture fixture = new ModeFixture(true);
            fixture.mode.toggle();
            fixture.calls.clear();
            fixture.surface.exitResult = ScreenFillSurface.Survival.RESTORED_WINDOWED;
            fixture.mode.toggle();
            assertThat(fixture.mode.isActive()).isFalse();
            assertThat(fixture.menu.isSelected()).isFalse();
            assertThat(fixture.surface.captureCalls).isEqualTo(1);
            assertThat(fixture.calls).doesNotContain("captureFrame");
            assertOrder(fixture.calls, "captureViewport", "restoreFrame", "restoreChrome",
                "compensate", "stopHintTimer", "menuUnchecked", "focus");
        });
    }

    @Test
    public void exitKeepsFullscreenOwnershipWhenRecoveryReentersFullscreen() throws Exception {
        onEdt(() -> {
            ModeFixture fixture = new ModeFixture(true);
            fixture.mode.toggle();
            fixture.calls.clear();
            fixture.surface.exitResult = ScreenFillSurface.Survival.REENTERED_FULLSCREEN;
            fixture.mode.toggle();
            assertThat(fixture.mode.isActive()).isTrue();
            assertThat(fixture.menu.isSelected()).isTrue();
            assertThat(fixture.calls).doesNotContain("restoreChrome");
            assertThat(fixture.logs).anyMatch(value -> value.contains("exit"));
            fixture.surface.exitResult = ScreenFillSurface.Survival.RESTORED_WINDOWED;
            fixture.mode.toggle();
            assertThat(fixture.mode.isActive()).isFalse();
            assertThat(fixture.surface.restoreFrames).hasSize(2);
            assertThat(fixture.surface.restoreFrames.get(0)).isSameAs(fixture.surface.restoreFrames.get(1));
        });
    }

    @Test
    public void exitRestoresChromeAndDeactivatesWhenSurvivalIsUnknown() throws Exception {
        onEdt(() -> {
            ModeFixture fixture = new ModeFixture(true);
            fixture.mode.toggle();
            fixture.calls.clear();
            fixture.surface.exitResult = ScreenFillSurface.Survival.UNKNOWN_FRAME;
            fixture.mode.toggle();
            assertThat(fixture.mode.isActive()).isFalse();
            assertThat(fixture.menu.isSelected()).isFalse();
            assertOrder(fixture.calls, "restoreFrame", "restoreChrome", "compensate", "stopHintTimer");
            assertThat(fixture.logs).anyMatch(value -> value.contains("UNKNOWN_FRAME"));
        });
    }

    @Test
    public void frameRecoveryAttemptsAtMostOneInverseOperation() throws Exception {
        onEdt(() -> {
            ModeFixture fixture = new ModeFixture(true);
            fixture.surface.failBothRawOperations = true;
            fixture.mode.toggle();
            assertThat(fixture.surface.rawOperations).containsExactly("rawEnter", "rawRestore");
            assertThat(fixture.surface.applyCalls).isEqualTo(1);
            assertThat(fixture.surface.restoreCalls).isEqualTo(0);
            assertThat(fixture.mode.isActive()).isFalse();
        });
    }

    @Test
    public void failureBeforeFrameApplicationRestoresChromeAndCompensates() throws Exception {
        List<String> failures = Arrays.asList("viewport", "frame", "chromeCapture", "chromeHide");
        for (String failure : failures) {
            final String selectedFailure = failure;
            onEdt(() -> {
                ModeFixture fixture = new ModeFixture(true);
                fixture.failBeforeFrame(selectedFailure);
                assertNoEscape(() -> fixture.mode.toggle());
                assertThat(fixture.mode.isActive()).isFalse();
                assertThat(fixture.menu.isSelected()).isFalse();
                assertThat(fixture.surface.applyCalls).isEqualTo(0);
                assertOrder(fixture.calls, "restoreChrome", "compensate", "stopHintTimer");
                assertThat(fixture.logs).isNotEmpty();
                if ("chromeCapture".equals(selectedFailure)) {
                    assertThat(fixture.chrome.discardCalls).isEqualTo(1);
                }
            });
        }
    }

    @Test
    public void failureAfterFullscreenEntryKeepsChromeHiddenAndModeActive() throws Exception {
        List<String> failures = Arrays.asList("compensate", "timer", "menu", "focus");
        for (String failure : failures) {
            final String selectedFailure = failure;
            onEdt(() -> {
                ModeFixture fixture = new ModeFixture(true);
                fixture.failAfterFullscreenEntry(selectedFailure);
                assertNoEscape(() -> fixture.mode.toggle());
                assertThat(fixture.mode.isActive()).isTrue();
                assertThat(fixture.menu.isSelected()).isTrue();
                assertThat(fixture.calls).doesNotContain("restoreChrome");
                fixture.surface.exitResult = ScreenFillSurface.Survival.RESTORED_WINDOWED;
                assertNoEscape(fixture.mode::requestExit);
                assertThat(fixture.mode.isActive()).isFalse();
            });
        }
    }

    @Test
    public void failureDuringExitBookkeepingNeverEscapesToTheEdt() throws Exception {
        for (ScreenFillSurface.Survival survival : ScreenFillSurface.Survival.values()) {
            List<String> failures = survival == ScreenFillSurface.Survival.REENTERED_FULLSCREEN
                ? Arrays.asList("compensate", "timer", "menu", "focus", "logger", "secondary")
                : Arrays.asList("restore", "compensate", "timer", "menu", "focus", "logger", "secondary");
            for (String failure : failures) {
                onEdt(() -> {
                    ModeFixture fixture = new ModeFixture(true);
                    fixture.mode.toggle();
                    fixture.calls.clear();
                    fixture.surface.exitResult = survival;
                    fixture.failDuringExit(failure);
                    assertNoEscape(fixture.mode::requestExit);
                    boolean active = survival == ScreenFillSurface.Survival.REENTERED_FULLSCREEN;
                    assertThat(fixture.mode.isActive()).as("%s / %s", survival, failure).isEqualTo(active);
                    assertThat(fixture.menu.isSelected()).isEqualTo(active);
                    assertThat(fixture.host.getComponent(0).isVisible()).isFalse();
                    assertThat(fixture.logs).anyMatch(value -> value.contains("java.lang."));
                    assertThat(fixture.surface.restoreCalls).isEqualTo(1);
                    assertThat(fixture.chrome.hidden).isEqualTo(active);
                    if ("secondary".equals(failure)) {
                        assertThat(fixture.logs).anyMatch(value -> value.contains("primary compensation"));
                        assertThat(fixture.logs).anyMatch(value -> value.contains("secondary hint"));
                        assertThat(fixture.calls).contains("focus");
                    }
                    fixture.surface.exitResult = ScreenFillSurface.Survival.RESTORED_WINDOWED;
                    fixture.mode.toggle();
                    assertThat(fixture.mode.isActive()).isEqualTo(!active);
                });
            }
        }
    }

    @Test
    public void exitCaptureFailureKeepsFullscreenSnapshotForRetry() throws Exception {
        onEdt(() -> {
            ModeFixture fixture = new ModeFixture(true);
            fixture.mode.toggle();
            fixture.calls.clear();
            fixture.viewport.captureFailure.failNext(new AssertionError("exit capture"));
            assertNoEscape(fixture.mode::requestExit);
            assertThat(fixture.mode.isActive()).isTrue();
            assertThat(fixture.menu.isSelected()).isTrue();
            assertThat(fixture.calls).doesNotContain("restoreFrame", "restoreChrome");
            assertThat(fixture.host.getComponent(0).isVisible()).isFalse();
            fixture.mode.requestExit();
            assertThat(fixture.mode.isActive()).isFalse();
            assertThat(fixture.surface.restoreFrames).containsExactly(fixture.surface.capturedFrame);
        });
    }

    @Test
    public void failedLaterChromeCaptureRetainsOlderUsableSnapshot() throws Exception {
        onEdt(() -> {
            ModeFixture fixture = new ModeFixture(true);
            fixture.mode.toggle();
            fixture.mode.requestExit();
            assertThat(fixture.chrome.snapshotUsable).isTrue();
            assertThat(fixture.chrome.discardCalls).isZero();
            fixture.chrome.captureFailure.failNext(new AssertionError("later capture"));
            assertNoEscape(fixture.mode::toggle);
            assertThat(fixture.chrome.snapshotUsable).isTrue();
            assertThat(fixture.chrome.discardCalls).isZero();
            assertThat(fixture.mode.isActive()).isFalse();
            fixture.mode.toggle();
            assertThat(fixture.mode.isActive()).isTrue();
        });
    }

    @Test
    public void closeDuringRecoveryStopsFurtherTransitionCallbacks() throws Exception {
        onEdt(() -> {
            ModeFixture windowed = new ModeFixture(true);
            windowed.surface.captureFailure.failNext(new AssertionError("capture"));
            windowed.chrome.onRestore = windowed.mode::windowClosed;
            assertNoEscape(windowed.mode::toggle);
            assertThat(windowed.calls).doesNotContain("compensate", "focus", "applyScreenFill");
            assertThat(windowed.calls.subList(windowed.calls.indexOf("menuDisabled") + 1,
                windowed.calls.size())).isEmpty();
            assertThat(windowed.mode.isActive()).isFalse();

            ModeFixture fullscreen = new ModeFixture(true);
            fullscreen.menu.selectedFailure.failNext(new RuntimeException("menu"));
            fullscreen.viewport.onCompensate = fullscreen.mode::windowClosed;
            assertNoEscape(fullscreen.mode::toggle);
            assertThat(fullscreen.calls.subList(fullscreen.calls.indexOf("menuDisabled") + 1,
                fullscreen.calls.size())).isEmpty();
            assertThat(fullscreen.menu.isSelected()).isFalse();
            assertThat(fullscreen.mode.isActive()).isFalse();
            int closedCalls = fullscreen.calls.size();
            fullscreen.mode.toggle();
            fullscreen.mode.requestExit();
            assertThat(fullscreen.calls).hasSize(closedCalls);
        });
    }

    @Test
    public void unavailableModeDoesNothingAndLeavesMenuUnchecked() throws Exception {
        onEdt(() -> {
            ModeFixture fixture = new ModeFixture(false);
            fixture.calls.clear();
            fixture.mode.toggle();
            fixture.mode.requestExit();
            fixture.mode.toggle();
            assertThat(fixture.mode.isAvailable()).isFalse();
            assertThat(fixture.mode.isActive()).isFalse();
            assertThat(fixture.menu.isEnabled()).isFalse();
            assertThat(fixture.menu.isSelected()).isFalse();
            assertThat(fixture.calls).isEmpty();
        });
    }

    @Test
    public void requestExitDoesNothingWhenInactive() throws Exception {
        onEdt(() -> {
            ModeFixture fixture = new ModeFixture(true);
            fixture.mode.requestExit();
            assertThat(fixture.calls).isEmpty();
        });
    }

    @Test
    public void closeDisposesHintDiscardsSnapshotAndInvalidatesCallbacks() throws Exception {
        onEdt(() -> {
            ModeFixture fixture = new ModeFixture(true);
            fixture.mode.toggle();
            Runnable lateExpiry = fixture.timer.expiry;
            fixture.mode.windowClosed();
            int callsAfterClose = fixture.calls.size();
            fixture.mode.windowClosed();
            lateExpiry.run();
            fixture.mode.toggle();
            fixture.mode.requestExit();
            assertThat(fixture.mode.isActive()).isFalse();
            assertThat(fixture.chrome.discardCalls).isEqualTo(1);
            assertThat(fixture.viewport.windowClosedCalls).isEqualTo(1);
            assertThat(fixture.host.getComponentCount()).isZero();
            assertThat(fixture.menu.isSelected()).isFalse();
            assertThat(fixture.menu.isEnabled()).isFalse();
            assertThat(fixture.calls.size()).isEqualTo(callsAfterClose);

            ModeFixture reentrant = new ModeFixture(true);
            reentrant.surface.onApply = reentrant.mode::windowClosed;
            reentrant.mode.toggle();
            assertThat(reentrant.mode.isActive()).isFalse();
            assertThat(reentrant.chrome.discardCalls).isEqualTo(1);
        });
    }

    @Test
    public void rapidToggleDuringTransitionRunsOnlyOneTransition() throws Exception {
        onEdt(() -> {
            ModeFixture fixture = new ModeFixture(true);
            fixture.surface.onApply = () -> {
                fixture.mode.toggle();
                fixture.mode.requestExit();
            };
            fixture.mode.toggle();
            assertThat(fixture.surface.applyCalls).isEqualTo(1);
            assertThat(fixture.mode.isActive()).isTrue();
            fixture.surface.onApply = null;
            fixture.mode.toggle();
            assertThat(fixture.surface.restoreCalls).isEqualTo(1);
            assertThat(fixture.mode.isActive()).isFalse();
        });
    }

    private static void onEdt(final Runnable action) throws Exception {
        SwingUtilities.invokeAndWait(action);
    }

    private static void assertNoEscape(final ThrowingAction action) {
        assertThatCode(action::run).doesNotThrowAnyException();
    }

    private static void assertOrder(final List<String> calls, final String... expected) {
        int index = -1;
        for (String name : expected) {
            int next = calls.subList(index + 1, calls.size()).indexOf(name);
            assertThat(next).as("operation %s after %s in %s", name,
                index < 0 ? "start" : calls.get(index), calls).isGreaterThanOrEqualTo(0);
            index += next + 1;
        }
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run();
    }

    private static final class Failure {
        Throwable next;

        void failNext(final Throwable value) {
            next = value;
        }

        void throwIfPresent() {
            Throwable value = next;
            next = null;
            if (value == null) {
                return;
            }
            if (value instanceof RuntimeException) {
                throw (RuntimeException) value;
            }
            if (value instanceof Error) {
                throw (Error) value;
            }
            throw new AssertionError(value);
        }
    }

    private static final class ModeFixture {
        final List<String> calls = new ArrayList<String>();
        final List<String> logs = new ArrayList<String>();
        final JLayeredPane host = new JLayeredPane();
        final RecordingTimer timer = new RecordingTimer(calls);
        final PresentationHint hint;
        final RecordingMenuItem menu = new RecordingMenuItem(calls);
        final RecordingSurface surface = new RecordingSurface(calls);
        final RecordingChrome chrome = new RecordingChrome(calls);
        final RecordingViewport viewport = new RecordingViewport(calls);
        final Failure focusFailure = new Failure();
        final Failure loggerFailure = new Failure();
        final GraphPresentationMode mode;

        ModeFixture(final boolean supported) {
            host.setSize(1000, 700);
            hint = new PresentationHint(host, "Press Esc to exit full screen", 3000,
                (delay, expiry) -> {
                    timer.expiry = expiry;
                    timer.delay = delay;
                    return timer;
                });
            mode = new GraphPresentationMode(surface, chrome, viewport, hint, menu,
                () -> {
                    calls.add("focus");
                    focusFailure.throwIfPresent();
                }, message -> {
                    logs.add(message);
                    loggerFailure.throwIfPresent();
                }, supported);
            calls.clear();
            logs.clear();
        }

        void failBeforeFrame(final String name) {
            if ("viewport".equals(name)) {
                viewport.captureFailure.failNext(new RuntimeException("viewport capture"));
            }
            else if ("frame".equals(name)) {
                surface.captureFailure.failNext(new AssertionError("frame capture"));
            }
            else if ("chromeCapture".equals(name)) {
                chrome.captureFailure.failNext(new RuntimeException("chrome capture"));
            }
            else if ("chromeHide".equals(name)) {
                chrome.hideFailure.failNext(new RuntimeException("chrome hide"));
            }
        }

        void failAfterFullscreenEntry(final String name) {
            if ("compensate".equals(name)) {
                viewport.compensateFailure.failNext(new RuntimeException("compensate"));
            }
            else if ("timer".equals(name)) {
                timer.restartFailure.failNext(new RuntimeException("timer"));
            }
            else if ("menu".equals(name)) {
                menu.selectedFailure.failNext(new RuntimeException("menu"));
            }
            else if ("focus".equals(name)) {
                focusFailure.failNext(new RuntimeException("focus"));
            }
        }

        void failDuringExit(final String name) {
            if ("restore".equals(name)) {
                chrome.restoreFailure.failNext(new RuntimeException("restore"));
            }
            else if ("compensate".equals(name)) {
                viewport.compensateFailure.failNext(new RuntimeException("compensate"));
            }
            else if ("timer".equals(name)) {
                timer.stopFailure.failNext(new RuntimeException("timer"));
            }
            else if ("menu".equals(name)) {
                menu.selectedFailure.failNext(new RuntimeException("menu"));
            }
            else if ("focus".equals(name)) {
                focusFailure.failNext(new RuntimeException("focus"));
            }
            else if ("logger".equals(name)) {
                viewport.compensateFailure.failNext(new RuntimeException("compensate"));
                loggerFailure.failNext(new Error("logger"));
            }
            else if ("secondary".equals(name)) {
                viewport.compensateFailure.failNext(new RuntimeException("primary compensation"));
                timer.stopFailure.failNext(new AssertionError("secondary hint"));
            }
        }
    }

    private static final class RecordingSurface implements ScreenFillSurface {
        final List<String> calls;
        final Failure captureFailure = new Failure();
        final Failure applyFailure = new Failure();
        final Failure restoreFailure = new Failure();
        final List<FrameState> restoreFrames = new ArrayList<FrameState>();
        final List<String> rawOperations = new ArrayList<String>();
        boolean failBothRawOperations;
        Survival enterResult = Survival.REENTERED_FULLSCREEN;
        Survival exitResult = Survival.RESTORED_WINDOWED;
        Runnable onApply;
        int captureCalls;
        int applyCalls;
        int restoreCalls;
        FrameState capturedFrame;

        RecordingSurface(final List<String> calls) {
            this.calls = calls;
        }

        @Override
        public FrameState captureFrameState() {
            calls.add("captureFrame");
            captureCalls++;
            captureFailure.throwIfPresent();
            capturedFrame = new FrameState(new Rectangle(10, 20, 300, 200), 0);
            return capturedFrame;
        }

        @Override
        public Survival applyScreenFill(final FrameState previous) {
            calls.add("applyScreenFill");
            applyCalls++;
            if (onApply != null) {
                onApply.run();
            }
            applyFailure.throwIfPresent();
            if (failBothRawOperations) {
                rawOperations.add("rawEnter");
                rawOperations.add("rawRestore");
                return Survival.UNKNOWN_FRAME;
            }
            return enterResult;
        }

        @Override
        public Survival restoreFrameState(final FrameState previous) {
            calls.add("restoreFrame");
            restoreCalls++;
            restoreFrames.add(previous);
            restoreFailure.throwIfPresent();
            return exitResult;
        }
    }

    private static final class RecordingChrome implements PresentationChrome {
        final List<String> calls;
        final Failure captureFailure = new Failure();
        final Failure hideFailure = new Failure();
        final Failure restoreFailure = new Failure();
        final Failure discardFailure = new Failure();
        Runnable onRestore;
        boolean snapshotUsable;
        boolean hidden;
        int discardCalls;

        RecordingChrome(final List<String> calls) {
            this.calls = calls;
        }

        @Override
        public void capture() {
            calls.add("captureChrome");
            captureFailure.throwIfPresent();
            snapshotUsable = true;
        }

        @Override
        public void hide() {
            calls.add("hideChrome");
            hidden = true;
            hideFailure.throwIfPresent();
        }

        @Override
        public void restore() {
            calls.add("restoreChrome");
            if (onRestore != null) {
                onRestore.run();
            }
            restoreFailure.throwIfPresent();
            if (snapshotUsable) {
                hidden = false;
            }
        }

        @Override
        public void discard() {
            calls.add("discardChrome");
            discardCalls++;
            snapshotUsable = false;
            discardFailure.throwIfPresent();
        }
    }

    private static final class RecordingViewport implements PresentationViewport {
        final List<String> calls;
        final Failure captureFailure = new Failure();
        final Failure compensateFailure = new Failure();
        final Failure disarmFailure = new Failure();
        final Failure closeFailure = new Failure();
        Runnable onCompensate;
        int windowClosedCalls;

        RecordingViewport(final List<String> calls) {
            this.calls = calls;
        }

        @Override
        public void capture() {
            calls.add("captureViewport");
            captureFailure.throwIfPresent();
        }

        @Override
        public void compensate() {
            calls.add("compensate");
            if (onCompensate != null) {
                onCompensate.run();
            }
            compensateFailure.throwIfPresent();
        }

        @Override
        public void classify(final ChangeEvent event) {
            calls.add("classify");
        }

        @Override
        public void canvasPublication() {
            calls.add("canvasPublication");
        }

        @Override
        public void toolbarPublication(final GraphViewport value) {
            calls.add("toolbarPublication");
        }

        @Override
        public void disarm() {
            calls.add("disarm");
            disarmFailure.throwIfPresent();
        }

        @Override
        public void windowClosed() {
            calls.add("windowClosed");
            windowClosedCalls++;
            closeFailure.throwIfPresent();
        }
    }

    private static final class RecordingMenuItem extends JCheckBoxMenuItem {
        private static final long serialVersionUID = 1L;
        final List<String> calls;
        final Failure selectedFailure = new Failure();
        final Failure enabledFailure = new Failure();

        RecordingMenuItem(final List<String> calls) {
            this.calls = calls;
        }

        @Override
        public void setSelected(final boolean selected) {
            if (calls != null) {
                calls.add(selected ? "menuChecked" : "menuUnchecked");
                selectedFailure.throwIfPresent();
            }
            super.setSelected(selected);
        }

        @Override
        public void setEnabled(final boolean enabled) {
            if (calls != null) {
                calls.add(enabled ? "menuEnabled" : "menuDisabled");
                enabledFailure.throwIfPresent();
            }
            super.setEnabled(enabled);
        }
    }

    private static final class HintFixture {
        final JLayeredPane host = new JLayeredPane();
        final AtomicReference<Runnable> expiry = new AtomicReference<Runnable>();
        final RecordingHintTimer timer = new RecordingHintTimer();
        final PresentationHint hint;
        int delay;

        HintFixture() {
            host.setSize(1000, 700);
            hint = new PresentationHint(host, "Press Esc to exit full screen", 3000, (millis, callback) -> {
                delay = millis;
                expiry.set(callback);
                return timer;
            });
        }

        JPanel panel() {
            assertThat(host.getComponents()).hasSize(1);
            return (JPanel) host.getComponent(0);
        }
    }

    private static final class RecordingHintTimer extends Timer {
        int restarts;
        int stops;

        RecordingHintTimer() {
            super(3000, null);
        }

        @Override
        public void restart() {
            restarts++;
        }

        @Override
        public void stop() {
            stops++;
        }
    }

    private static final class RecordingTimer extends Timer {
        final List<String> calls;
        final Failure restartFailure = new Failure();
        final Failure stopFailure = new Failure();
        Runnable expiry;
        int delay;
        int restarts;
        int stops;

        RecordingTimer(final List<String> calls) {
            super(3000, null);
            this.calls = calls;
        }

        @Override
        public void restart() {
            calls.add("restartHintTimer");
            restarts++;
            restartFailure.throwIfPresent();
        }

        @Override
        public void stop() {
            calls.add("stopHintTimer");
            stops++;
            stopFailure.throwIfPresent();
        }
    }
}
