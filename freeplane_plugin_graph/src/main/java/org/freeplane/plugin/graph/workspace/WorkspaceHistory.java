package org.freeplane.plugin.graph.workspace;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

import org.freeplane.plugin.graph.workspace.model.WorkspaceDocument;

public final class WorkspaceHistory {
    private final Deque<WorkspaceDocument> undo = new ArrayDeque<WorkspaceDocument>();
    private final Deque<WorkspaceDocument> redo = new ArrayDeque<WorkspaceDocument>();

    public WorkspaceTransition execute(final WorkspaceCommand command, final WorkspaceDocument current) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(current, "current");
        final WorkspaceTransition transition = Objects.requireNonNull(command.apply(current), "transition");
        if (transition.status() == WorkspaceTransition.Status.APPLIED) {
            undo.push(current);
            redo.clear();
        }
        return transition;
    }

    public WorkspaceTransition undo(final WorkspaceDocument current) {
        Objects.requireNonNull(current, "current");
        if (undo.isEmpty()) {
            return WorkspaceTransition.noOp(current, "graph_workspace.history.nothing_to_undo");
        }
        final WorkspaceDocument previous = undo.peek();
        final WorkspaceDocument restored = withCurrentEnvelope(previous, current);
        undo.pop();
        redo.push(current);
        return WorkspaceTransition.applied(restored, "graph_workspace.history.undone");
    }

    public WorkspaceTransition redo(final WorkspaceDocument current) {
        Objects.requireNonNull(current, "current");
        if (redo.isEmpty()) {
            return WorkspaceTransition.noOp(current, "graph_workspace.history.nothing_to_redo");
        }
        final WorkspaceDocument next = redo.peek();
        final WorkspaceDocument restored = withCurrentEnvelope(next, current);
        redo.pop();
        undo.push(current);
        return WorkspaceTransition.applied(restored, "graph_workspace.history.redone");
    }

    public boolean canUndo() {
        return !undo.isEmpty();
    }

    public boolean canRedo() {
        return !redo.isEmpty();
    }

    public void clear() {
        undo.clear();
        redo.clear();
    }

    private static WorkspaceDocument withCurrentEnvelope(final WorkspaceDocument historical,
            final WorkspaceDocument current) {
        return historical.toBuilder()
            .id(current.id())
            .sourceFormatVersion(current.sourceFormatVersion())
            .compatibility(current.compatibility())
            .viewport(current.viewport())
            .build();
    }
}
