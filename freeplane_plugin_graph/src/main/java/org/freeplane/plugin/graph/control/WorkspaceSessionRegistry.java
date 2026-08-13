package org.freeplane.plugin.graph.control;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.freeplane.plugin.graph.workspace.WorkspaceIdentityChange;
import org.freeplane.plugin.graph.workspace.WorkspaceUriResolver;

public final class WorkspaceSessionRegistry {
    private final Object monitor = new Object();
    private final WorkspaceUriResolver uriResolver = new WorkspaceUriResolver();
    private final Map<Path, WorkspaceSessionId> committedByPath = new HashMap<Path, WorkspaceSessionId>();
    private final Map<WorkspaceSessionId, Path> pathBySession = new HashMap<WorkspaceSessionId, Path>();
    private final Map<Path, Token> pendingByPath = new HashMap<Path, Token>();
    private final Map<WorkspaceSessionId, Token> pendingBySession = new HashMap<WorkspaceSessionId, Token>();
    private RekeyObservation rekeyObservation;

    public boolean register(final WorkspaceSessionId id, final Path path) {
        Objects.requireNonNull(id, "id");
        final Path canonicalPath = uriResolver.canonical(path);
        synchronized (monitor) {
            final Path existing = pathBySession.get(id);
            if (existing != null) {
                if (existing.equals(canonicalPath)) {
                    return true;
                }
                throw new IllegalStateException("Session ID is already registered for another workspace path");
            }
            if (committedByPath.containsKey(canonicalPath)) {
                return false;
            }
            if (pendingByPath.containsKey(canonicalPath)) {
                return false;
            }
            committedByPath.put(canonicalPath, id);
            pathBySession.put(id, canonicalPath);
            return true;
        }
    }

    public Optional<WorkspaceSessionId> owner(final Path path) {
        final Path canonicalPath = uriResolver.canonical(path);
        synchronized (monitor) {
            final WorkspaceSessionId committed = committedByPath.get(canonicalPath);
            if (committed != null) {
                return Optional.of(committed);
            }
            final Token pending = pendingByPath.get(canonicalPath);
            if (pending != null) {
                return Optional.of(pending.sessionId);
            }
            return Optional.empty();
        }
    }

    public WorkspacePathReservation reserveSaveAs(final WorkspaceSessionId id, final Path target) {
        Objects.requireNonNull(id, "id");
        final Path canonicalTarget = uriResolver.canonical(target);
        synchronized (monitor) {
            final Path sessionPath = pathBySession.get(id);
            if (sessionPath == null) {
                throw new IllegalStateException("Session is not registered");
            }
            if (sessionPath.equals(canonicalTarget)) {
                throw new IllegalArgumentException("Save As target must differ from the current workspace path");
            }
            if (pendingBySession.containsKey(id)) {
                throw new IllegalStateException("Session already has an active Save As reservation");
            }
            if (committedByPath.containsKey(canonicalTarget)) {
                throw new IllegalStateException("Save As target is already committed to another session");
            }
            if (pendingByPath.containsKey(canonicalTarget)) {
                throw new IllegalStateException("Save As target is already reserved");
            }
            final Token token = new Token(this, id, canonicalTarget);
            pendingByPath.put(canonicalTarget, token);
            pendingBySession.put(id, token);
            return token;
        }
    }

    public void unregister(final WorkspaceSessionId id) {
        Objects.requireNonNull(id, "id");
        synchronized (monitor) {
            final Path sessionPath = pathBySession.remove(id);
            if (sessionPath != null) {
                committedByPath.remove(sessionPath);
            }
            final Token pending = pendingBySession.remove(id);
            if (pending != null) {
                if (pendingByPath.get(pending.target) == pending) {
                    pendingByPath.remove(pending.target);
                }
                pending.closed = true;
            }
        }
    }

    RekeyObservation rekeyObservation() {
        synchronized (monitor) {
            return rekeyObservation;
        }
    }

    private void commitToken(final Token token, final WorkspaceIdentityChange change) {
        final Path canonicalOld = uriResolver.canonical(change.oldPath());
        final Path canonicalNew = uriResolver.canonical(change.newPath());
        synchronized (monitor) {
            if (token.closed) {
                throw new IllegalStateException("Save As reservation is closed");
            }
            if (token.committed) {
                throw new IllegalStateException("Save As reservation is already committed");
            }
            if (pendingBySession.get(token.sessionId) != token) {
                throw new IllegalStateException("Save As reservation is no longer active");
            }
            if (!pathBySession.get(token.sessionId).equals(canonicalOld)) {
                throw new IllegalArgumentException(
                    "Identity change old path does not match the session workspace path");
            }
            if (!token.target.equals(canonicalNew)) {
                throw new IllegalArgumentException("Identity change new path does not match the reserved target");
            }
            committedByPath.put(canonicalNew, token.sessionId);
            rekeyObservation = new RekeyObservation(committedByPath.get(canonicalOld),
                committedByPath.get(canonicalNew));
            committedByPath.remove(canonicalOld);
            pendingByPath.remove(token.target);
            pendingBySession.remove(token.sessionId);
            pathBySession.put(token.sessionId, canonicalNew);
            token.committed = true;
        }
    }

    private void closeToken(final Token token) {
        synchronized (monitor) {
            if (token.closed || token.committed) {
                return;
            }
            if (pendingBySession.get(token.sessionId) == token) {
                pendingBySession.remove(token.sessionId);
            }
            if (pendingByPath.get(token.target) == token) {
                pendingByPath.remove(token.target);
            }
            token.closed = true;
        }
    }

    static final class RekeyObservation {
        private final WorkspaceSessionId oldPathOwner;
        private final WorkspaceSessionId newPathOwner;

        private RekeyObservation(final WorkspaceSessionId oldPathOwner, final WorkspaceSessionId newPathOwner) {
            this.oldPathOwner = oldPathOwner;
            this.newPathOwner = newPathOwner;
        }

        WorkspaceSessionId oldPathOwner() {
            return oldPathOwner;
        }

        WorkspaceSessionId newPathOwner() {
            return newPathOwner;
        }
    }

    private static final class Token implements WorkspacePathReservation {
        private final WorkspaceSessionRegistry registry;
        private final WorkspaceSessionId sessionId;
        private final Path target;
        private boolean closed;
        private boolean committed;

        private Token(final WorkspaceSessionRegistry registry, final WorkspaceSessionId sessionId,
                final Path target) {
            this.registry = registry;
            this.sessionId = sessionId;
            this.target = target;
        }

        @Override
        public void commit(final WorkspaceIdentityChange change) {
            registry.commitToken(this, Objects.requireNonNull(change, "change"));
        }

        @Override
        public void close() {
            registry.closeToken(this);
        }
    }
}
