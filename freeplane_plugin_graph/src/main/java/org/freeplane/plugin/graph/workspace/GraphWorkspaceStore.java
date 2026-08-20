package org.freeplane.plugin.graph.workspace;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.freeplane.plugin.graph.workspace.io.WorkspaceXmlCodec;
import org.freeplane.plugin.graph.workspace.model.MapReference;
import org.freeplane.plugin.graph.workspace.model.Viewport;
import org.freeplane.plugin.graph.workspace.model.WorkspaceCompatibility;
import org.freeplane.plugin.graph.workspace.model.WorkspaceDocument;
import org.freeplane.plugin.graph.workspace.model.WorkspaceId;

public final class GraphWorkspaceStore implements AutoCloseable {
    private static final long AUTOSAVE_DELAY_MILLIS = 150L;
    private static final String READ_ONLY_MESSAGE_KEY = "graph_workspace.workspace.read_only";

    private final Object monitor = new Object();
    private final WorkspaceXmlCodec codec;
    private final AtomicWorkspaceWriter writer;
    private final ScheduledExecutorService scheduler;
    private final WorkspaceUriResolver uriResolver;
    private final WorkspaceHistory history;
    private final List<ListenerEntry> listeners;
    private final Deque<PublishedEvent> pendingEvents;

    private Path file;
    private WorkspaceDocument document;
    private boolean dirty;
    private boolean closed;
    private long debounceGeneration;
    private long saveGeneration;
    private ScheduledFuture<?> pendingSave;
    private boolean drainingEvents;

    private GraphWorkspaceStore(final Path file, final WorkspaceDocument document, final WorkspaceXmlCodec codec,
            final AtomicWorkspaceWriter writer, final ScheduledExecutorService scheduler,
            final WorkspaceUriResolver uriResolver) {
        this.file = Objects.requireNonNull(file, "file");
        this.document = Objects.requireNonNull(document, "document");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.writer = Objects.requireNonNull(writer, "writer");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.uriResolver = Objects.requireNonNull(uriResolver, "uriResolver");
        this.history = new WorkspaceHistory();
        this.listeners = new ArrayList<ListenerEntry>();
        this.pendingEvents = new ArrayDeque<PublishedEvent>();
    }

    public static GraphWorkspaceStore create(final Path file, final WorkspaceXmlCodec codec,
            final AtomicWorkspaceWriter writer, final ScheduledExecutorService scheduler) {
        final WorkspaceUriResolver resolver = new WorkspaceUriResolver();
        final Path canonicalFile = canonicalWorkspaceFile(file, resolver);
        if (Files.exists(canonicalFile)) {
            throw new IllegalArgumentException("Workspace file already exists: " + canonicalFile);
        }
        final WorkspaceXmlCodec checkedCodec = Objects.requireNonNull(codec, "codec");
        final AtomicWorkspaceWriter checkedWriter = Objects.requireNonNull(writer, "writer");
        final ScheduledExecutorService checkedScheduler = Objects.requireNonNull(scheduler, "scheduler");
        final WorkspaceDocument document = WorkspaceDocument.createVersion1(newWorkspaceId());
        final byte[] bytes = checkedCodec.write(document, canonicalFile);
        checkedWriter.write(canonicalFile, bytes);
        return new GraphWorkspaceStore(canonicalFile, document, checkedCodec, checkedWriter, checkedScheduler, resolver);
    }

    public static GraphWorkspaceStore open(final Path file, final WorkspaceXmlCodec codec,
            final AtomicWorkspaceWriter writer, final ScheduledExecutorService scheduler) {
        final WorkspaceUriResolver resolver = new WorkspaceUriResolver();
        final Path canonicalFile = canonicalWorkspaceFile(file, resolver);
        final WorkspaceXmlCodec checkedCodec = Objects.requireNonNull(codec, "codec");
        final WorkspaceDocument document = checkedCodec.read(canonicalFile);
        return new GraphWorkspaceStore(canonicalFile, document, checkedCodec, Objects.requireNonNull(writer, "writer"),
            Objects.requireNonNull(scheduler, "scheduler"), resolver);
    }

    public WorkspaceDocument currentDocument() {
        synchronized (monitor) {
            return document;
        }
    }

    public GraphCommandResult execute(final WorkspaceCommand command) {
        final GraphCommandResult result;
        synchronized (monitor) {
            requireOpenLocked();
            if (isReadOnlyLocked()) {
                result = readOnlyResultLocked();
            } else {
                final WorkspaceTransition transition = history.execute(Objects.requireNonNull(command, "command"),
                    document);
                result = installTransitionLocked(transition);
            }
        }
        drainEvents();
        return result;
    }

    public WorkspaceMutation executeWithCompensation(final WorkspaceCommand command) {
        final WorkspaceMutation mutation;
        synchronized (monitor) {
            requireOpenLocked();
            if (isReadOnlyLocked()) {
                final GraphCommandResult result = readOnlyResultLocked();
                mutation = new WorkspaceMutation(this, result, null, file, saveGeneration, dirty, dirty,
                    debounceGeneration, debounceGeneration, new byte[0], new byte[0]);
            } else {
                final WorkspaceCommand checkedCommand = Objects.requireNonNull(command, "command");
                final Path mutationFile = file;
                final long mutationSaveGeneration = saveGeneration;
                final boolean mutationDirtyBefore = dirty;
                final long mutationDebounceBefore = debounceGeneration;
                final byte[] beforeBytes = readPersistedBytesLocked();
                final WorkspaceHistory.HistoryMutation historyMutation =
                    history.executeWithToken(checkedCommand, document);
                final GraphCommandResult result = installTransitionLocked(historyMutation.transition());
                final boolean mutationDirty = dirty;
                final long mutationDebounceGeneration = debounceGeneration;
                final byte[] afterBytes = historyMutation.applied()
                    ? codec.write(historyMutation.after(), mutationFile) : beforeBytes;
                mutation = new WorkspaceMutation(this, result,
                    historyMutation.applied() ? historyMutation : null, mutationFile, mutationSaveGeneration,
                    mutationDirtyBefore, mutationDirty, mutationDebounceBefore, mutationDebounceGeneration,
                    beforeBytes, afterBytes);
            }
        }
        drainEvents();
        return mutation;
    }

    public GraphCommandResult updateViewport(final Viewport viewport) {
        final GraphCommandResult result;
        synchronized (monitor) {
            requireOpenLocked();
            if (isReadOnlyLocked()) {
                result = readOnlyResultLocked();
            } else {
                final Viewport value = Objects.requireNonNull(viewport, "viewport");
                if (document.viewport().equals(value)) {
                    result = GraphCommandResult.from(WorkspaceTransition.noOp(document,
                        "graph_workspace.command.no_change", "updateViewport"));
                } else {
                    final WorkspaceDocument updated = document.toBuilder().viewport(value).build();
                    document = updated;
                    dirty = true;
                    publishLocked(WorkspaceStoreEvent.documentChanged(updated));
                    resetDebounceLocked();
                    result = GraphCommandResult.from(WorkspaceTransition.applied(updated,
                        "graph_workspace.viewport.updated"));
                }
            }
        }
        drainEvents();
        return result;
    }

    public GraphCommandResult undo() {
        final GraphCommandResult result;
        synchronized (monitor) {
            requireOpenLocked();
            if (isReadOnlyLocked()) {
                result = readOnlyResultLocked();
            } else {
                result = installTransitionLocked(history.undo(document));
            }
        }
        drainEvents();
        return result;
    }

    public GraphCommandResult redo() {
        final GraphCommandResult result;
        synchronized (monitor) {
            requireOpenLocked();
            if (isReadOnlyLocked()) {
                result = readOnlyResultLocked();
            } else {
                result = installTransitionLocked(history.redo(document));
            }
        }
        drainEvents();
        return result;
    }

    public void saveNow() {
        RuntimeException failure = null;
        synchronized (monitor) {
            requireOpenLocked();
            requireWritableLocked();
            invalidateDebounceLocked();
            if (dirty) {
                try {
                    saveDirtyLocked();
                }
                catch (final RuntimeException exception) {
                    failure = exception;
                    publishLocked(WorkspaceStoreEvent.saveFailed(document, exception));
                }
            }
        }
        drainEvents();
        if (failure != null) {
            throw failure;
        }
    }

    public WorkspaceIdentityChange saveAs(final Path target) {
        RuntimeException failure = null;
        WorkspaceIdentityChange identityChange = null;
        synchronized (monitor) {
            requireOpenLocked();
            requireWritableLocked();
            final Path targetFile = canonicalWorkspaceFile(target, uriResolver);
            if (file.equals(targetFile)) {
                throw new IllegalArgumentException("Save As target must differ from the current workspace file");
            }
            invalidateDebounceLocked();
            try {
                final WorkspaceId newId = newDistinctWorkspaceId(document.id());
                final WorkspaceDocument candidate = rewriteForSaveAsLocked(targetFile, newId);
                final byte[] bytes = codec.write(candidate, targetFile);
                writer.write(targetFile, bytes);

                final Path oldFile = file;
                final WorkspaceId oldId = document.id();
                file = targetFile;
                document = candidate;
                dirty = false;
                saveGeneration++;
                history.clear();
                identityChange = new WorkspaceIdentityChange(oldFile, targetFile, oldId, newId);
                publishLocked(WorkspaceStoreEvent.identityChanged(candidate, identityChange));
                publishLocked(WorkspaceStoreEvent.saved(candidate));
            }
            catch (final RuntimeException exception) {
                failure = exception;
                publishLocked(WorkspaceStoreEvent.saveFailed(document, exception));
            }
        }
        drainEvents();
        if (failure != null) {
            throw failure;
        }
        return identityChange;
    }

    public boolean isDirty() {
        synchronized (monitor) {
            return dirty;
        }
    }

    public boolean canUndo() {
        synchronized (monitor) {
            return !closed && !isReadOnlyLocked() && history.canUndo();
        }
    }

    public boolean canRedo() {
        synchronized (monitor) {
            return !closed && !isReadOnlyLocked() && history.canRedo();
        }
    }

    public ListenerRegistration addListener(final WorkspaceStoreListener listener) {
        synchronized (monitor) {
            requireOpenLocked();
            final ListenerEntry entry = new ListenerEntry(Objects.requireNonNull(listener, "listener"));
            listeners.add(entry);
            return new StoreListenerRegistration(this, entry);
        }
    }

    public void discardAndClose() {
        synchronized (monitor) {
            if (closed) {
                return;
            }
            invalidateDebounceLocked();
            history.clear();
            dirty = false;
            closed = true;
            listeners.clear();
        }
    }

    @Override
    public void close() {
        RuntimeException failure = null;
        synchronized (monitor) {
            if (closed) {
                return;
            }
            invalidateDebounceLocked();
            if (dirty) {
                try {
                    saveDirtyLocked();
                }
                catch (final RuntimeException exception) {
                    failure = exception;
                    publishLocked(WorkspaceStoreEvent.saveFailed(document, exception));
                }
            }
            if (failure == null) {
                closed = true;
                listeners.clear();
            }
        }
        drainEvents();
        if (failure != null) {
            throw failure;
        }
    }

    private GraphCommandResult installTransitionLocked(final WorkspaceTransition transition) {
        final GraphCommandResult result = GraphCommandResult.from(transition);
        if (transition.status() == WorkspaceTransition.Status.APPLIED) {
            document = transition.after();
            dirty = true;
            publishLocked(WorkspaceStoreEvent.documentChanged(document));
            resetDebounceLocked();
        }
        return result;
    }

    private GraphCommandResult readOnlyResultLocked() {
        return GraphCommandResult.from(WorkspaceTransition.rejected(document, READ_ONLY_MESSAGE_KEY));
    }

    private void resetDebounceLocked() {
        cancelPendingSaveLocked();
        final long generation = ++debounceGeneration;
        try {
            pendingSave = scheduler.schedule(new Runnable() {
                @Override
                public void run() {
                    saveScheduled(generation);
                }
            }, AUTOSAVE_DELAY_MILLIS, TimeUnit.MILLISECONDS);
        }
        catch (final RuntimeException exception) {
            pendingSave = null;
            publishLocked(WorkspaceStoreEvent.saveFailed(document, exception));
        }
    }

    private void saveScheduled(final long generation) {
        synchronized (monitor) {
            if (generation != debounceGeneration || closed || isReadOnlyLocked() || !dirty) {
                return;
            }
            pendingSave = null;
            try {
                saveDirtyLocked();
            }
            catch (final RuntimeException exception) {
                publishLocked(WorkspaceStoreEvent.saveFailed(document, exception));
            }
        }
        drainEvents();
    }

    private void saveDirtyLocked() {
        final byte[] bytes = codec.write(document, file);
        writer.write(file, bytes);
        dirty = false;
        saveGeneration++;
        publishLocked(WorkspaceStoreEvent.saved(document));
    }

    private WorkspaceDocument rewriteForSaveAsLocked(final Path targetFile, final WorkspaceId newId) {
        final List<MapReference> rewrittenMaps = new ArrayList<MapReference>(document.maps().size());
        for (final MapReference map : document.maps()) {
            final URI rewrittenUri = uriResolver.rewriteForSaveAs(file, targetFile, map.storedUri());
            rewrittenMaps.add(MapReference.of(map.id(), map.sequence(), rewrittenUri, map.active(), map.color(),
                map.unknownXml()));
        }
        return document.toBuilder().id(newId).maps(rewrittenMaps).build();
    }

    private void invalidateDebounceLocked() {
        cancelPendingSaveLocked();
        debounceGeneration++;
    }

    private void cancelPendingSaveLocked() {
        if (pendingSave != null) {
            pendingSave.cancel(false);
            pendingSave = null;
        }
    }

    private void publishLocked(final WorkspaceStoreEvent event) {
        pendingEvents.addLast(new PublishedEvent(event, new ArrayList<ListenerEntry>(listeners)));
    }

    private void drainEvents() {
        synchronized (monitor) {
            if (drainingEvents) {
                return;
            }
            drainingEvents = true;
        }
        while (true) {
            final PublishedEvent event;
            synchronized (monitor) {
                event = pendingEvents.pollFirst();
                if (event == null) {
                    drainingEvents = false;
                    return;
                }
            }
            for (final ListenerEntry listener : event.listeners()) {
                try {
                    listener.listener().onWorkspaceStoreEvent(event.event());
                }
                catch (final RuntimeException ignored) {
                }
            }
        }
    }

    private GraphCommandResult compensate(final WorkspaceMutation mutation) {
        GraphCommandResult result;
        synchronized (monitor) {
            if (mutation.successfulCompensation != null) {
                return mutation.successfulCompensation;
            }
            if (closed || mutation.historyMutation == null || !mutation.file.equals(file)
                    || document != mutation.historyMutation.after()) {
                result = compensationRejectedLocked();
            } else {
                byte[] currentBytes = null;
                try {
                    currentBytes = readPersistedBytesLocked();
                }
                catch (final RuntimeException exception) {
                    // Keep the mutation retryable when the persisted evidence is temporarily unavailable.
                }
                if (currentBytes == null) {
                    result = compensationIncompleteLocked();
                } else if (!Arrays.equals(currentBytes, mutation.beforeBytes)
                        && !Arrays.equals(currentBytes, mutation.afterBytes)) {
                    result = compensationRejectedLocked();
                } else if (Arrays.equals(currentBytes, mutation.beforeBytes)
                        && !mutation.restoreBeforeBytesVerified()
                        && (dirty != mutation.dirtyAfter || saveGeneration != mutation.saveGeneration
                            || debounceGeneration < mutation.debounceAfter)) {
                    result = compensationRejectedLocked();
                } else if (Arrays.equals(currentBytes, mutation.afterBytes)
                        && (saveGeneration < mutation.saveGeneration || debounceGeneration < mutation.debounceAfter
                            || (dirty && !mutation.dirtyAfter))) {
                    result = compensationRejectedLocked();
                } else {
                    final boolean rewrite = !Arrays.equals(currentBytes, mutation.beforeBytes);
                    boolean restoreWriteFailed = false;
                    try {
                        if (rewrite) {
                            try {
                                writeAndVerifyLocked(mutation.beforeBytes);
                            }
                            catch (final RuntimeException exception) {
                                restoreWriteFailed = true;
                                throw exception;
                            }
                        }
                        final WorkspaceTransition transition = history.compensate(mutation.historyMutation, document);
                        if (transition.status() != WorkspaceTransition.Status.APPLIED) {
                            if (rewrite) {
                                writeAndVerifyLocked(mutation.afterBytes);
                            }
                            result = GraphCommandResult.from(transition);
                        } else {
                            document = transition.after();
                            if (rewrite) {
                                saveGeneration++;
                            }
                            invalidateDebounceLocked();
                            dirty = mutation.dirtyBefore;
                            publishLocked(WorkspaceStoreEvent.documentChanged(document));
                            if (dirty) {
                                resetDebounceLocked();
                            } else {
                                publishLocked(WorkspaceStoreEvent.saved(document));
                            }
                            result = GraphCommandResult.from(transition);
                            mutation.successfulCompensation = result;
                        }
                    }
                    catch (final RuntimeException exception) {
                        if (restoreWriteFailed) {
                            mutation.recordVerifiedRestoreProgress(readPersistedBytesIfAvailableLocked());
                        }
                        result = compensationIncompleteLocked();
                    }
                }
            }
        }
        drainEvents();
        return result;
    }

    private GraphCommandResult compensationRejectedLocked() {
        return GraphCommandResult.from(WorkspaceTransition.rejected(document,
            "graph_workspace.history.compensation_conflict"));
    }

    private GraphCommandResult compensationIncompleteLocked() {
        return GraphCommandResult.from(WorkspaceTransition.rejected(document,
            "graph_workspace.history.compensation_incomplete"));
    }

    private byte[] readPersistedBytesLocked() {
        try {
            return Files.readAllBytes(file);
        }
        catch (final Exception exception) {
            throw new WorkspaceSaveException(file, exception);
        }
    }

    private byte[] readPersistedBytesIfAvailableLocked() {
        try {
            return readPersistedBytesLocked();
        }
        catch (final RuntimeException exception) {
            return null;
        }
    }

    private void writeAndVerifyLocked(final byte[] bytes) {
        writer.write(file, Arrays.copyOf(bytes, bytes.length));
        if (!Arrays.equals(readPersistedBytesLocked(), bytes)) {
            throw new WorkspaceSaveException(file, new IllegalStateException("Persisted workspace bytes mismatch"));
        }
    }

    private void removeListener(final ListenerEntry entry) {
        synchronized (monitor) {
            listeners.remove(entry);
        }
    }

    private boolean isReadOnlyLocked() {
        return document.compatibility() == WorkspaceCompatibility.READ_ONLY_NEWER;
    }

    private void requireOpenLocked() {
        if (closed) {
            throw new IllegalStateException("Graph workspace store is closed");
        }
    }

    private void requireWritableLocked() {
        if (isReadOnlyLocked()) {
            throw new IllegalStateException("Graph workspace store is read-only");
        }
    }

    private static Path canonicalWorkspaceFile(final Path file, final WorkspaceUriResolver resolver) {
        final Path canonicalFile = resolver.canonical(Objects.requireNonNull(file, "file"));
        final Path name = canonicalFile.getFileName();
        if (name == null || !name.toString().endsWith(".fpg")) {
            throw new IllegalArgumentException("Workspace file must use the .fpg extension: " + file);
        }
        return canonicalFile;
    }

    private static WorkspaceId newWorkspaceId() {
        return WorkspaceId.of(UUID.randomUUID());
    }

    private static WorkspaceId newDistinctWorkspaceId(final WorkspaceId oldId) {
        WorkspaceId candidate;
        do {
            candidate = newWorkspaceId();
        } while (candidate.equals(oldId));
        return candidate;
    }

    public static final class WorkspaceMutation {
        private final GraphWorkspaceStore store;
        private final GraphCommandResult result;
        private final WorkspaceHistory.HistoryMutation historyMutation;
        private final Path file;
        private final long saveGeneration;
        private final boolean dirtyBefore;
        private final boolean dirtyAfter;
        private final long debounceBefore;
        private final long debounceAfter;
        private final byte[] beforeBytes;
        private final byte[] afterBytes;
        private GraphCommandResult successfulCompensation;
        private boolean restoreBeforeBytesVerified;

        private WorkspaceMutation(final GraphWorkspaceStore store, final GraphCommandResult result,
                final WorkspaceHistory.HistoryMutation historyMutation, final Path file, final long saveGeneration,
                final boolean dirtyBefore, final boolean dirtyAfter, final long debounceBefore,
                final long debounceAfter, final byte[] beforeBytes, final byte[] afterBytes) {
            this.store = Objects.requireNonNull(store, "store");
            this.result = Objects.requireNonNull(result, "result");
            this.historyMutation = historyMutation;
            this.file = Objects.requireNonNull(file, "file");
            this.saveGeneration = saveGeneration;
            this.dirtyBefore = dirtyBefore;
            this.dirtyAfter = dirtyAfter;
            this.debounceBefore = debounceBefore;
            this.debounceAfter = debounceAfter;
            this.beforeBytes = Arrays.copyOf(beforeBytes, beforeBytes.length);
            this.afterBytes = Arrays.copyOf(afterBytes, afterBytes.length);
        }

        private void recordVerifiedRestoreProgress(final byte[] persistedBytes) {
            if (persistedBytes != null && Arrays.equals(persistedBytes, beforeBytes)) {
                restoreBeforeBytesVerified = true;
            }
        }

        private boolean restoreBeforeBytesVerified() {
            return restoreBeforeBytesVerified;
        }

        public GraphCommandResult result() {
            return result;
        }

        public GraphCommandResult compensateIfCurrent() {
            return store.compensate(this);
        }
    }

    private static final class ListenerEntry {
        private final WorkspaceStoreListener listener;

        private ListenerEntry(final WorkspaceStoreListener listener) {
            this.listener = listener;
        }

        private WorkspaceStoreListener listener() {
            return listener;
        }
    }

    private static final class PublishedEvent {
        private final WorkspaceStoreEvent event;
        private final List<ListenerEntry> listeners;

        private PublishedEvent(final WorkspaceStoreEvent event, final List<ListenerEntry> listeners) {
            this.event = event;
            this.listeners = Collections.unmodifiableList(new ArrayList<ListenerEntry>(listeners));
        }

        private WorkspaceStoreEvent event() {
            return event;
        }

        private List<ListenerEntry> listeners() {
            return listeners;
        }
    }

    private static final class StoreListenerRegistration implements ListenerRegistration {
        private final GraphWorkspaceStore store;
        private final ListenerEntry entry;

        private StoreListenerRegistration(final GraphWorkspaceStore store, final ListenerEntry entry) {
            this.store = store;
            this.entry = entry;
        }

        @Override
        public void close() {
            store.removeListener(entry);
        }
    }
}
