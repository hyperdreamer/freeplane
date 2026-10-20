package org.freeplane.plugin.graph.window;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import javax.swing.JCheckBoxMenuItem;

import org.freeplane.core.util.Compat;

final class GraphPresentationMode {
    private enum State {
        INACTIVE, ACTIVE, CLOSED
    }

    private final ScreenFillSurface surface;
    private final PresentationChrome chrome;
    private final PresentationViewport viewport;
    private final PresentationHint hint;
    private final JCheckBoxMenuItem menuItem;
    private final Runnable focusCanvas;
    private final Consumer<String> log;
    private final boolean supported;

    private State state = State.INACTIVE;
    private boolean transitioning;
    private ScreenFillSurface.FrameState previous;
    private boolean chromeSnapshotUsable;

    GraphPresentationMode(final ScreenFillSurface surface, final PresentationChrome chrome,
            final PresentationViewport viewport, final PresentationHint hint,
            final JCheckBoxMenuItem menuItem, final Runnable focusCanvas,
            final Consumer<String> log, final boolean supported) {
        this.surface = Objects.requireNonNull(surface, "surface");
        this.chrome = Objects.requireNonNull(chrome, "chrome");
        this.viewport = Objects.requireNonNull(viewport, "viewport");
        this.hint = Objects.requireNonNull(hint, "hint");
        this.menuItem = Objects.requireNonNull(menuItem, "menuItem");
        this.focusCanvas = Objects.requireNonNull(focusCanvas, "focusCanvas");
        this.log = Objects.requireNonNull(log, "log");
        this.supported = supported;
        menuItem.setEnabled(supported);
        menuItem.setSelected(false);
    }

    GraphPresentationMode(final ScreenFillSurface surface, final PresentationChrome chrome,
            final PresentationViewport viewport, final PresentationHint hint,
            final JCheckBoxMenuItem menuItem, final Runnable focusCanvas,
            final Consumer<String> log) {
        this(surface, chrome, viewport, hint, menuItem, focusCanvas, log, !Compat.isMacOsX());
    }

    boolean isAvailable() {
        return supported;
    }

    void toggle() {
        if (!supported || state == State.CLOSED || transitioning) {
            return;
        }
        transition(state == State.INACTIVE);
    }

    boolean isActive() {
        return state == State.ACTIVE;
    }

    void requestExit() {
        if (!supported || state != State.ACTIVE || transitioning) {
            return;
        }
        transition(false);
    }

    void windowClosed() {
        if (state == State.CLOSED) {
            return;
        }
        state = State.CLOSED;
        transitioning = false;
        final List<FailureRecord> failures = new ArrayList<FailureRecord>();
        attempt("dispose hint", hint::dispose, failures);
        attempt("close viewport", viewport::windowClosed, failures);
        attempt("discard chrome", () -> {
            chrome.discard();
            chromeSnapshotUsable = false;
        }, failures);
        previous = null;
        attempt("uncheck menu", () -> menuItem.setSelected(false), failures);
        attempt("disable menu", () -> menuItem.setEnabled(false), failures);
        reportFailures("presentation close", null, failures);
    }

    private void transition(final boolean entering) {
        if (!supported || state == State.CLOSED || transitioning) {
            return;
        }
        transitioning = true;
        final TransitionProgress progress = new TransitionProgress(entering);
        try {
            if (entering) {
                enter(progress);
            }
            else {
                exit(progress);
            }
        }
        catch (RuntimeException | Error failure) {
            recover(progress, failure);
        }
        finally {
            transitioning = false;
        }
    }

    private void enter(final TransitionProgress progress) {
        viewport.capture();
        if (isClosed()) {
            return;
        }
        final ScreenFillSurface.FrameState captured =
            Objects.requireNonNull(surface.captureFrameState(), "frame state");
        if (isClosed()) {
            return;
        }
        previous = captured;
        progress.chromeCaptureAttempted = true;
        chrome.capture();
        if (isClosed()) {
            return;
        }
        chromeSnapshotUsable = true;
        chrome.hide();
        if (isClosed()) {
            return;
        }
        progress.survival = ScreenFillSurface.Survival.UNKNOWN_FRAME;
        progress.survival = Objects.requireNonNull(surface.applyScreenFill(previous), "survival");
        if (isClosed()) {
            return;
        }
        finishEntry(progress);
    }

    private void exit(final TransitionProgress progress) {
        viewport.capture();
        if (isClosed()) {
            return;
        }
        final ScreenFillSurface.FrameState saved = Objects.requireNonNull(previous, "previous frame state");
        progress.survival = ScreenFillSurface.Survival.UNKNOWN_FRAME;
        progress.survival = Objects.requireNonNull(surface.restoreFrameState(saved), "survival");
        if (isClosed()) {
            return;
        }
        finishExit(progress);
    }

    private void finishEntry(final TransitionProgress progress) {
        if (progress.survival == ScreenFillSurface.Survival.REENTERED_FULLSCREEN) {
            state = State.ACTIVE;
            menuItem.setSelected(true);
            if (isClosed()) {
                return;
            }
            viewport.compensate();
            if (isClosed()) {
                return;
            }
            focusCanvas.run();
            if (isClosed()) {
                return;
            }
            hint.show();
            return;
        }

        state = State.INACTIVE;
        chrome.restore();
        if (isClosed()) {
            return;
        }
        viewport.compensate();
        if (isClosed()) {
            return;
        }
        hint.hide();
        if (isClosed()) {
            return;
        }
        menuItem.setSelected(false);
        if (isClosed()) {
            return;
        }
        if (progress.survival == ScreenFillSurface.Survival.UNKNOWN_FRAME) {
            logMessage("presentation entry ended with UNKNOWN_FRAME");
        }
        previous = null;
    }

    private void finishExit(final TransitionProgress progress) {
        if (progress.survival == ScreenFillSurface.Survival.REENTERED_FULLSCREEN) {
            state = State.ACTIVE;
            viewport.compensate();
            if (isClosed()) {
                return;
            }
            hint.hide();
            if (isClosed()) {
                return;
            }
            menuItem.setSelected(true);
            if (isClosed()) {
                return;
            }
            focusCanvas.run();
            if (isClosed()) {
                return;
            }
            logMessage("presentation exit failed: frame re-entered fullscreen");
            return;
        }

        state = State.INACTIVE;
        chrome.restore();
        if (isClosed()) {
            return;
        }
        viewport.compensate();
        if (isClosed()) {
            return;
        }
        hint.hide();
        if (isClosed()) {
            return;
        }
        menuItem.setSelected(false);
        if (isClosed()) {
            return;
        }
        focusCanvas.run();
        if (isClosed()) {
            return;
        }
        if (progress.survival == ScreenFillSurface.Survival.UNKNOWN_FRAME) {
            logMessage("presentation exit ended with UNKNOWN_FRAME");
        }
        previous = null;
    }

    private void recover(final TransitionProgress progress, final Throwable primary) {
        if (isClosed()) {
            return;
        }
        final List<FailureRecord> failures = new ArrayList<FailureRecord>();
        final boolean ownsFullscreen = progress.survival == ScreenFillSurface.Survival.REENTERED_FULLSCREEN;
        if (ownsFullscreen) {
            state = State.ACTIVE;
            attemptIfOpen("compensate", viewport::compensate, failures);
            attemptIfOpen("hide hint", hint::hide, failures);
            attemptIfOpen("check menu", () -> menuItem.setSelected(true), failures);
            attemptIfOpen("focus canvas", () -> focusCanvas.run(), failures);
            reportFailures("presentation transition", primary, failures);
            return;
        }

        state = State.INACTIVE;
        attemptIfOpen("restore chrome", chrome::restore, failures);
        attemptIfOpen("compensate", viewport::compensate, failures);
        attemptIfOpen("hide hint", hint::hide, failures);
        attemptIfOpen("uncheck menu", () -> menuItem.setSelected(false), failures);
        if (progress.entering && progress.chromeCaptureAttempted && !chromeSnapshotUsable) {
            attemptIfOpen("discard unusable chrome capture", () -> {
                chrome.discard();
                chromeSnapshotUsable = false;
            }, failures);
        }
        if (!progress.entering) {
            attemptIfOpen("focus canvas", () -> focusCanvas.run(), failures);
        }
        previous = null;
        if (progress.survival == ScreenFillSurface.Survival.UNKNOWN_FRAME) {
            safeLog("presentation transition has UNKNOWN_FRAME survival");
        }
        reportFailures("presentation transition", primary, failures);
    }

    private boolean isClosed() {
        return state == State.CLOSED;
    }

    private void logMessage(final String message) {
        log.accept(message);
    }

    private void attemptIfOpen(final String operation, final ThrowingAction action,
            final List<FailureRecord> failures) {
        if (!isClosed()) {
            attempt(operation, action, failures);
        }
    }

    private void attempt(final String operation, final ThrowingAction action,
            final List<FailureRecord> failures) {
        try {
            action.run();
        }
        catch (RuntimeException | Error failure) {
            failures.add(new FailureRecord(operation, failure));
        }
    }

    private void reportFailures(final String prefix, final Throwable primary,
            final List<FailureRecord> failures) {
        if (primary != null) {
            safeLog(prefix + " failed: " + describe(primary));
        }
        for (FailureRecord failure : failures) {
            safeLog(prefix + " recovery " + failure.operation + " failed: " + describe(failure.throwable));
        }
    }

    private void safeLog(final String message) {
        try {
            log.accept(message);
        }
        catch (RuntimeException | Error ignored) {
            // Logging is part of the recovery boundary and cannot recurse into it.
        }
    }

    private static String describe(final Throwable failure) {
        final StringBuilder message = new StringBuilder(failure.getClass().getName());
        if (failure.getMessage() != null) {
            message.append(": ").append(failure.getMessage());
        }
        final StackTraceElement[] stack = failure.getStackTrace();
        if (stack.length > 0) {
            message.append(" at ").append(stack[0]);
        }
        return message.toString();
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run();
    }

    private static final class FailureRecord {
        final String operation;
        final Throwable throwable;

        FailureRecord(final String operation, final Throwable throwable) {
            this.operation = operation;
            this.throwable = throwable;
        }
    }

    private static final class TransitionProgress {
        final boolean entering;
        ScreenFillSurface.Survival survival;
        boolean chromeCaptureAttempted;

        TransitionProgress(final boolean entering) {
            this.entering = entering;
            survival = entering
                ? ScreenFillSurface.Survival.RESTORED_WINDOWED
                : ScreenFillSurface.Survival.REENTERED_FULLSCREEN;
        }
    }
}
