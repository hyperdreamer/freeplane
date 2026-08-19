package org.freeplane.plugin.graph.control;

import org.freeplane.plugin.graph.workspace.ListenerRegistration;

public interface GraphWorkspaceViewBinding {
    CanvasState currentCanvasState();
    ListenerRegistration addCanvasStateListener(CanvasStateListener listener);
}
