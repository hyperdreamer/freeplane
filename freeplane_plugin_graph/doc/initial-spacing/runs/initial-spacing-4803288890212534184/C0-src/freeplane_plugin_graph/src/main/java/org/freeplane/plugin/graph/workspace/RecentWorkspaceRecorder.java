package org.freeplane.plugin.graph.workspace;

import java.util.Objects;

public final class RecentWorkspaceRecorder implements WorkspaceStoreListener, AutoCloseable {
    private final RecentWorkspaceList recentWorkspaces;
    private final ListenerRegistration registration;

    public RecentWorkspaceRecorder(final GraphWorkspaceStore store, final RecentWorkspaceList recentWorkspaces) {
        this.recentWorkspaces = Objects.requireNonNull(recentWorkspaces, "recentWorkspaces");
        this.registration = Objects.requireNonNull(store, "store").addListener(this);
    }

    @Override
    public void onWorkspaceStoreEvent(final WorkspaceStoreEvent event) {
        if (event.type() == WorkspaceStoreEvent.Type.IDENTITY_CHANGED) {
            event.identityChange().ifPresent(change -> recentWorkspaces.record(change.newPath()));
        }
    }

    @Override
    public void close() {
        registration.close();
    }
}
