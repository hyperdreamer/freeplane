package org.freeplane.plugin.graph.window;

import java.awt.Rectangle;
import java.util.Objects;

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
