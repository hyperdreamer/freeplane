package org.freeplane.plugin.graph.workspace;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

import org.freeplane.plugin.graph.workspace.model.WorkspaceDocument;

public final class WorkspaceHistory {
    private static final String COMPENSATION_CONFLICT = "graph_workspace.history.compensation_conflict";

    private final Deque<HistoryEntry> undo = new ArrayDeque<HistoryEntry>();
    private final Deque<HistoryEntry> redo = new ArrayDeque<HistoryEntry>();
    private Object redoIdentity = new Object();
    private long revision;
    private long nextEntryId;

    public WorkspaceTransition execute(final WorkspaceCommand command, final WorkspaceDocument current) {
        return executeInternal(command, current).transition;
    }

    HistoryMutation executeWithToken(final WorkspaceCommand command, final WorkspaceDocument current) {
        return executeInternal(command, current);
    }

    public WorkspaceTransition undo(final WorkspaceDocument current) {
        Objects.requireNonNull(current, "current");
        if (undo.isEmpty()) {
            return WorkspaceTransition.noOp(current, "graph_workspace.history.nothing_to_undo");
        }
        final HistoryEntry entry = undo.pop();
        final WorkspaceDocument restored = withCurrentEnvelope(entry.before, current);
        redo.push(entry);
        redoIdentity = new Object();
        revision++;
        return WorkspaceTransition.applied(restored, "graph_workspace.history.undone");
    }

    public WorkspaceTransition redo(final WorkspaceDocument current) {
        Objects.requireNonNull(current, "current");
        if (redo.isEmpty()) {
            return WorkspaceTransition.noOp(current, "graph_workspace.history.nothing_to_redo");
        }
        final HistoryEntry entry = redo.pop();
        final WorkspaceDocument restored = withCurrentEnvelope(entry.after, current);
        undo.push(entry);
        redoIdentity = new Object();
        revision++;
        return WorkspaceTransition.applied(restored, "graph_workspace.history.redone");
    }

    WorkspaceTransition compensate(final HistoryMutation mutation, final WorkspaceDocument current) {
        Objects.requireNonNull(current, "current");
        if (mutation == null || mutation.entry == null || mutation.revision != revision
                || undo.peek() != mutation.entry || current != mutation.after
                || redoIdentity != mutation.executionRedoIdentity || !redo.isEmpty()) {
            return WorkspaceTransition.rejected(current, COMPENSATION_CONFLICT);
        }
        undo.pop();
        redo.clear();
        for (final HistoryEntry entry : mutation.priorRedoEntries) {
            redo.addLast(entry);
        }
        redoIdentity = mutation.priorRedoIdentity;
        revision++;
        final WorkspaceDocument restored = withCurrentEnvelope(mutation.before, current);
        return WorkspaceTransition.applied(restored, "graph_workspace.history.compensated");
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
        redoIdentity = new Object();
        revision++;
    }

    private HistoryMutation executeInternal(final WorkspaceCommand command, final WorkspaceDocument current) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(current, "current");
        final WorkspaceTransition transition = Objects.requireNonNull(command.apply(current), "transition");
        if (transition.status() != WorkspaceTransition.Status.APPLIED) {
            return new HistoryMutation(transition, null, current, transition.after(), revision,
                redoIdentity, Collections.<HistoryEntry>emptyList(), redoIdentity);
        }

        final Object priorRedoIdentity = redoIdentity;
        final List<HistoryEntry> priorRedoEntries = new ArrayList<HistoryEntry>(redo);
        final HistoryEntry entry = new HistoryEntry(++nextEntryId, current, transition.after());
        undo.push(entry);
        redo.clear();
        redoIdentity = new Object();
        revision++;
        return new HistoryMutation(transition, entry, current, transition.after(), revision,
            priorRedoIdentity, priorRedoEntries, redoIdentity);
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

    static final class HistoryMutation {
        private final WorkspaceTransition transition;
        private final HistoryEntry entry;
        private final WorkspaceDocument before;
        private final WorkspaceDocument after;
        private final long revision;
        private final Object priorRedoIdentity;
        private final List<HistoryEntry> priorRedoEntries;
        private final Object executionRedoIdentity;

        private HistoryMutation(final WorkspaceTransition transition, final HistoryEntry entry,
                final WorkspaceDocument before, final WorkspaceDocument after, final long revision,
                final Object priorRedoIdentity, final List<HistoryEntry> priorRedoEntries,
                final Object executionRedoIdentity) {
            this.transition = Objects.requireNonNull(transition, "transition");
            this.entry = entry;
            this.before = Objects.requireNonNull(before, "before");
            this.after = Objects.requireNonNull(after, "after");
            this.revision = revision;
            this.priorRedoIdentity = Objects.requireNonNull(priorRedoIdentity, "priorRedoIdentity");
            this.priorRedoEntries = Collections.unmodifiableList(new ArrayList<HistoryEntry>(priorRedoEntries));
            this.executionRedoIdentity = Objects.requireNonNull(executionRedoIdentity, "executionRedoIdentity");
        }

        WorkspaceTransition transition() {
            return transition;
        }

        WorkspaceDocument after() {
            return after;
        }

        boolean applied() {
            return entry != null;
        }
    }

    private static final class HistoryEntry {
        @SuppressWarnings("unused")
        private final long id;
        private final WorkspaceDocument before;
        private final WorkspaceDocument after;

        private HistoryEntry(final long id, final WorkspaceDocument before, final WorkspaceDocument after) {
            this.id = id;
            this.before = Objects.requireNonNull(before, "before");
            this.after = Objects.requireNonNull(after, "after");
        }
    }
}
