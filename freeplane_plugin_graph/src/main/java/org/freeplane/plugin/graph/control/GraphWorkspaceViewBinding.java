package org.freeplane.plugin.graph.control;

import org.freeplane.plugin.graph.workspace.ListenerRegistration;
import org.freeplane.plugin.graph.workspace.model.Viewport;

public interface GraphWorkspaceViewBinding {
    CanvasState currentCanvasState();
    Viewport currentViewport();
    default boolean isReadOnly() {
        return false;
    }
    ListenerRegistration addCanvasStateListener(CanvasStateListener listener);
}
