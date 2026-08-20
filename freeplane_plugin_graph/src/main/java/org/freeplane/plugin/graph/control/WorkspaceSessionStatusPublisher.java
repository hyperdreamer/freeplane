package org.freeplane.plugin.graph.control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.freeplane.plugin.graph.command.GraphCommandRouter;
import org.freeplane.plugin.graph.command.MapUndoTarget;
import org.freeplane.plugin.graph.workspace.GraphCommandResult;
import org.freeplane.plugin.graph.workspace.GraphWorkspaceStore;
import org.freeplane.plugin.graph.workspace.ListenerRegistration;
import org.freeplane.plugin.graph.workspace.WorkspaceStoreEvent;
import org.freeplane.plugin.graph.workspace.WorkspaceStoreListener;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;

final class WorkspaceSessionStatusPublisher {
    private final Object monitor = new Object();
    private final GraphWorkspaceStore store;
    private final GraphCommandRouter router;
    private final Set<MapReferenceId> dirtySourceMaps = new LinkedHashSet<MapReferenceId>();
    private final List<ListenerEntry> listeners = new ArrayList<ListenerEntry>();
    private final boolean noOp;
    private WorkspaceSessionStatus status;
    private ListenerRegistration storeRegistration;
    private boolean closed;

    WorkspaceSessionStatusPublisher(final GraphWorkspaceStore store, final GraphCommandRouter router) {
        this.store = Objects.requireNonNull(store, "store");
        this.router = Objects.requireNonNull(router, "router");
        this.noOp = false;
        this.status = WorkspaceSessionStatus.of(store.isDirty(), store.canUndo(), store.canRedo(), false,
            dirtySourceMaps, Optional.<MapUndoTarget>empty());
        this.storeRegistration = Objects.requireNonNull(store.addListener(new WorkspaceStoreListener() {
            @Override
            public void onWorkspaceStoreEvent(final WorkspaceStoreEvent event) {
                publishStoreEvent(event);
            }
        }), "store listener registration");
    }

    private WorkspaceSessionStatusPublisher() {
        this.store = null;
        this.router = null;
        this.noOp = true;
        this.status = WorkspaceSessionStatus.empty();
    }

    static WorkspaceSessionStatusPublisher noOp() {
        return new WorkspaceSessionStatusPublisher();
    }

    WorkspaceSessionStatus currentSessionStatus() {
        synchronized (monitor) {
            return status;
        }
    }

    ListenerRegistration addListener(final WorkspaceSessionStatusListener listener) {
        final WorkspaceSessionStatusListener value = Objects.requireNonNull(listener, "listener");
        if (noOp) {
            return NoOpRegistration.INSTANCE;
        }
        synchronized (monitor) {
            requireOpenLocked();
            final ListenerEntry entry = new ListenerEntry(value);
            listeners.add(entry);
            return new ListenerRemoval(this, entry);
        }
    }

    void recordCommandResult(final GraphCommandResult result) {
        if (noOp) {
            return;
        }
        final GraphCommandResult value = Objects.requireNonNull(result, "result");
        final Optional<MapUndoTarget> undoTarget = currentMapUndoTarget();
        final Set<MapReferenceId> commandDirtyMaps = Objects.requireNonNull(value.dirtySourceMaps(),
            "dirtySourceMaps");
        final List<WorkspaceSessionStatusListener> recipients;
        final WorkspaceSessionStatus next;
        synchronized (monitor) {
            if (closed) {
                return;
            }
            dirtySourceMaps.addAll(commandDirtyMaps);
            status = WorkspaceSessionStatus.of(status.workspaceDirty(), status.workspaceUndoAvailable(),
                status.workspaceRedoAvailable(), status.saveFailed(), dirtySourceMaps, undoTarget);
            next = status;
            recipients = listenerSnapshotLocked();
        }
        publish(next, recipients);
    }

    void close() {
        if (noOp) {
            return;
        }
        final ListenerRegistration registration;
        synchronized (monitor) {
            if (closed) {
                return;
            }
            closed = true;
            listeners.clear();
            registration = storeRegistration;
            storeRegistration = null;
        }
        if (registration != null) {
            registration.close();
        }
    }

    private Optional<MapUndoTarget> currentMapUndoTarget() {
        final Optional<MapUndoTarget> target = router.currentMapUndoTarget();
        return target == null ? Optional.<MapUndoTarget>empty() : target;
    }

    private void publishStoreEvent(final WorkspaceStoreEvent event) {
        if (event == null || noOp) {
            return;
        }
        final WorkspaceStoreEvent.Type type = event.type();
        if (type == null) {
            return;
        }
        final List<WorkspaceSessionStatusListener> recipients;
        final WorkspaceSessionStatus next;
        synchronized (monitor) {
            if (closed) {
                return;
            }
            boolean failed = status.saveFailed();
            if (type == WorkspaceStoreEvent.Type.SAVE_FAILED) {
                failed = true;
            }
            else if (type == WorkspaceStoreEvent.Type.SAVED) {
                failed = false;
            }
            status = WorkspaceSessionStatus.of(store.isDirty(), store.canUndo(), store.canRedo(), failed,
                dirtySourceMaps, status.sourceMapUndoTarget());
            next = status;
            recipients = listenerSnapshotLocked();
        }
        publish(next, recipients);
    }

    private void removeListener(final ListenerEntry entry) {
        synchronized (monitor) {
            listeners.remove(entry);
        }
    }

    private List<WorkspaceSessionStatusListener> listenerSnapshotLocked() {
        final List<WorkspaceSessionStatusListener> snapshot =
            new ArrayList<WorkspaceSessionStatusListener>(listeners.size());
        for (final ListenerEntry entry : listeners) {
            snapshot.add(entry.listener);
        }
        return Collections.unmodifiableList(snapshot);
    }

    private static void publish(final WorkspaceSessionStatus status,
            final List<WorkspaceSessionStatusListener> recipients) {
        for (final WorkspaceSessionStatusListener listener : recipients) {
            try {
                listener.onWorkspaceSessionStatus(status);
            }
            catch (final RuntimeException ignored) {
            }
        }
    }

    private void requireOpenLocked() {
        if (closed) {
            throw new IllegalStateException("Workspace session status publisher is closed");
        }
    }

    private static final class ListenerEntry {
        private final WorkspaceSessionStatusListener listener;

        private ListenerEntry(final WorkspaceSessionStatusListener listener) {
            this.listener = listener;
        }
    }

    private static final class ListenerRemoval implements ListenerRegistration {
        private final WorkspaceSessionStatusPublisher publisher;
        private final ListenerEntry entry;
        private boolean closed;

        private ListenerRemoval(final WorkspaceSessionStatusPublisher publisher, final ListenerEntry entry) {
            this.publisher = publisher;
            this.entry = entry;
        }

        @Override
        public synchronized void close() {
            if (!closed) {
                closed = true;
                publisher.removeListener(entry);
            }
        }
    }

    private static final class NoOpRegistration implements ListenerRegistration {
        private static final NoOpRegistration INSTANCE = new NoOpRegistration();

        @Override
        public void close() {
        }
    }
}
