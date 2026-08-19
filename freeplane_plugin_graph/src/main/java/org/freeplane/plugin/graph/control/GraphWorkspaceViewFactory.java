package org.freeplane.plugin.graph.control;

public interface GraphWorkspaceViewFactory {
    /** The returned view must remain unpublished and hidden until this method returns successfully. */
    GraphWorkspaceView create(GraphWorkspaceHandle handle, GraphWorkspaceViewBinding binding,
            WorkspaceCloseController close);
}
