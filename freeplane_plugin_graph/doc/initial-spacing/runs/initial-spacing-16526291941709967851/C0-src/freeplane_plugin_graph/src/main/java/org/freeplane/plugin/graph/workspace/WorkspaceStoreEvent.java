package org.freeplane.plugin.graph.workspace;

import java.util.Objects;
import java.util.Optional;

import org.freeplane.plugin.graph.workspace.model.WorkspaceDocument;

public final class WorkspaceStoreEvent {
    public enum Type {
        DOCUMENT_CHANGED,
        IDENTITY_CHANGED,
        SAVED,
        SAVE_FAILED
    }

    private final Type type;
    private final WorkspaceDocument document;
    private final WorkspaceIdentityChange identityChange;
    private final Throwable error;

    private WorkspaceStoreEvent(final Type type, final WorkspaceDocument document,
            final WorkspaceIdentityChange identityChange, final Throwable error) {
        this.type = Objects.requireNonNull(type, "type");
        this.document = Objects.requireNonNull(document, "document");
        this.identityChange = identityChange;
        this.error = error;
    }

    static WorkspaceStoreEvent documentChanged(final WorkspaceDocument document) {
        return new WorkspaceStoreEvent(Type.DOCUMENT_CHANGED, document, null, null);
    }

    static WorkspaceStoreEvent identityChanged(final WorkspaceDocument document,
            final WorkspaceIdentityChange identityChange) {
        return new WorkspaceStoreEvent(Type.IDENTITY_CHANGED, document,
            Objects.requireNonNull(identityChange, "identityChange"), null);
    }

    static WorkspaceStoreEvent saved(final WorkspaceDocument document) {
        return new WorkspaceStoreEvent(Type.SAVED, document, null, null);
    }

    static WorkspaceStoreEvent saveFailed(final WorkspaceDocument document, final Throwable error) {
        return new WorkspaceStoreEvent(Type.SAVE_FAILED, document, null, Objects.requireNonNull(error, "error"));
    }

    public Type type() {
        return type;
    }

    public WorkspaceDocument document() {
        return document;
    }

    public Optional<WorkspaceIdentityChange> identityChange() {
        return Optional.ofNullable(identityChange);
    }

    public Optional<Throwable> error() {
        return Optional.ofNullable(error);
    }
}
