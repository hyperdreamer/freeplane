package org.freeplane.plugin.graph.control;

public interface WorkspaceCloseController {
    /** Returns false only when the synchronous save failed and the session remains open. */
    boolean saveAndClose();
    boolean retrySaveAndClose();
    boolean discardAndClose();
    void cancelClose();
}
