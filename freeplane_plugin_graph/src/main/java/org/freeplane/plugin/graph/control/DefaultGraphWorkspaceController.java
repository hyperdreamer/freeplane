package org.freeplane.plugin.graph.control;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import org.freeplane.features.mode.ModeController;
import org.freeplane.plugin.graph.adapter.EdtExecutor;
import org.freeplane.plugin.graph.adapter.MapLease;
import org.freeplane.plugin.graph.adapter.MapLeaseManager;
import org.freeplane.plugin.graph.adapter.MapOperationalState;
import org.freeplane.plugin.graph.command.DefaultContributorDeletionHandler;
import org.freeplane.plugin.graph.command.DefaultPurgeCommandHandler;
import org.freeplane.plugin.graph.command.FreeplaneMapCommandExecutor;
import org.freeplane.plugin.graph.command.GraphCommandRouter;
import org.freeplane.plugin.graph.command.SourceNavigation;
import org.freeplane.plugin.graph.command.ViewMaterializationTracker;
import org.freeplane.plugin.graph.projection.ProjectionEngine;
import org.freeplane.plugin.graph.projection.input.MapAvailability;
import org.freeplane.plugin.graph.workspace.AtomicWorkspaceWriter;
import org.freeplane.plugin.graph.workspace.GraphWorkspaceStore;
import org.freeplane.plugin.graph.workspace.ListenerRegistration;
import org.freeplane.plugin.graph.workspace.WorkspaceUriResolver;
import org.freeplane.plugin.graph.workspace.io.WorkspaceMigrationRegistry;
import org.freeplane.plugin.graph.workspace.io.WorkspaceXmlCodec;
import org.freeplane.plugin.graph.workspace.model.MapReference;
import org.freeplane.plugin.graph.workspace.model.WorkspaceDocument;

public final class DefaultGraphWorkspaceController implements GraphWorkspaceController {
    interface SessionFactory {
        SessionResources open(Path path, WorkspaceSessionId sessionId, boolean create);
    }

    static final class SessionResources {
        final GraphWorkspaceStore store;
        final WorkspaceMapCoordinator maps;
        final GraphUpdateCoordinator updates;
        final MapLeaseManager leaseManager;
        final GraphCommandRouter router;
        final ScheduledExecutorService scheduler;
        final boolean newlyCreated;
        final WorkspaceCreationOwnership creationOwnership;

        SessionResources(final GraphWorkspaceStore store, final GraphUpdateCoordinator updates,
                final MapLeaseManager leaseManager, final GraphCommandRouter router,
                final ScheduledExecutorService scheduler, final boolean newlyCreated) {
            this(store, null, updates, leaseManager, router, scheduler, newlyCreated, null);
        }

        SessionResources(final GraphWorkspaceStore store, final WorkspaceMapCoordinator maps,
                final GraphUpdateCoordinator updates, final MapLeaseManager leaseManager,
                final GraphCommandRouter router, final ScheduledExecutorService scheduler,
                final boolean newlyCreated) {
            this(store, maps, updates, leaseManager, router, scheduler, newlyCreated, null);
        }

        SessionResources(final GraphWorkspaceStore store, final WorkspaceMapCoordinator maps,
                final GraphUpdateCoordinator updates, final MapLeaseManager leaseManager,
                final GraphCommandRouter router, final ScheduledExecutorService scheduler,
                final boolean newlyCreated, final WorkspaceCreationOwnership creationOwnership) {
            this.store = Objects.requireNonNull(store, "store");
            this.maps = maps;
            this.updates = Objects.requireNonNull(updates, "updates");
            this.leaseManager = Objects.requireNonNull(leaseManager, "leaseManager");
            this.router = Objects.requireNonNull(router, "router");
            this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
            this.newlyCreated = newlyCreated;
            this.creationOwnership = creationOwnership;
        }
    }

    private static final class WorkspaceCreationOwnership {
        private final Path path;
        private final Object fileKey;
        private final byte[] contentDigest;

        private WorkspaceCreationOwnership(final Path path, final Object fileKey, final byte[] contentDigest) {
            this.path = path;
            this.fileKey = fileKey;
            this.contentDigest = contentDigest;
        }

        private static WorkspaceCreationOwnership capture(final Path path) {
            try {
                final BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
                if (!attributes.isRegularFile() || attributes.fileKey() == null) {
                    return null;
                }
                return new WorkspaceCreationOwnership(path, attributes.fileKey(), digest(Files.readAllBytes(path)));
            }
            catch (IOException | SecurityException failure) {
                return null;
            }
        }

        private boolean matches(final Path candidate) {
            try {
                final BasicFileAttributes attributes = Files.readAttributes(candidate, BasicFileAttributes.class);
                if (!attributes.isRegularFile()) {
                    return false;
                }
                if (fileKey != null && !fileKey.equals(attributes.fileKey())) {
                    return false;
                }
                return Arrays.equals(contentDigest, digest(Files.readAllBytes(candidate)));
            }
            catch (IOException | SecurityException failure) {
                return false;
            }
        }

        private RuntimeException removeIfOwned() {
            Path quarantineDirectory = null;
            Path quarantinedPath = null;
            try {
                final Path parent = path.getParent();
                if (parent == null) {
                    return null;
                }
                quarantineDirectory = Files.createTempDirectory(parent, ".freeplane-rollback-");
                quarantinedPath = quarantineDirectory.resolve(path.getFileName());
                Files.move(path, quarantinedPath, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                if (!matches(quarantinedPath)) {
                    return restore(quarantinedPath, quarantineDirectory);
                }
                Files.delete(quarantinedPath);
                Files.delete(quarantineDirectory);
                return null;
            }
            catch (IOException | SecurityException failure) {
                RuntimeException result = new IllegalStateException(
                    "Unable to remove newly-created graph workspace safely", failure);
                if (quarantinedPath != null && Files.exists(quarantinedPath)) {
                    result = recordFailure(result, restore(quarantinedPath, quarantineDirectory));
                }
                else if (quarantineDirectory != null) {
                    result = recordFailure(result, deleteQuarantineDirectory(quarantineDirectory));
                }
                return result;
            }
        }

        private RuntimeException restore(final Path quarantinedPath, final Path quarantineDirectory) {
            RuntimeException failure = null;
            try {
                Files.move(quarantinedPath, path);
            }
            catch (IOException | SecurityException restoreFailure) {
                failure = new IllegalStateException("Unable to restore graph workspace after rollback", restoreFailure);
            }
            failure = recordFailure(failure, deleteQuarantineDirectory(quarantineDirectory));
            return failure;
        }

        private static RuntimeException deleteQuarantineDirectory(final Path quarantineDirectory) {
            try {
                Files.deleteIfExists(quarantineDirectory);
                return null;
            }
            catch (IOException | SecurityException failure) {
                return new IllegalStateException("Unable to remove graph workspace rollback state", failure);
            }
        }

        private static RuntimeException recordFailure(final RuntimeException failure,
                final RuntimeException additionalFailure) {
            if (additionalFailure == null) {
                return failure;
            }
            if (failure == null) {
                return additionalFailure;
            }
            failure.addSuppressed(additionalFailure);
            return failure;
        }

        private static byte[] digest(final byte[] content) {
            try {
                return MessageDigest.getInstance("SHA-256").digest(content);
            }
            catch (NoSuchAlgorithmException failure) {
                throw new IllegalStateException("SHA-256 is unavailable", failure);
            }
        }
    }

    private static final class ResourceSet {
        private final GraphWorkspaceStore store;
        private final WorkspaceMapCoordinator maps;
        private final GraphUpdateCoordinator updates;
        private final MapLeaseManager leaseManager;
        private final ScheduledExecutorService scheduler;
        private final boolean newlyCreated;
        private final WorkspaceCreationOwnership creationOwnership;

        private ResourceSet(final GraphWorkspaceStore store, final WorkspaceMapCoordinator maps,
                final GraphUpdateCoordinator updates, final MapLeaseManager leaseManager,
                final ScheduledExecutorService scheduler, final boolean newlyCreated,
                final WorkspaceCreationOwnership creationOwnership) {
            this.store = store;
            this.maps = maps;
            this.updates = updates;
            this.leaseManager = leaseManager;
            this.scheduler = scheduler;
            this.newlyCreated = newlyCreated;
            this.creationOwnership = creationOwnership;
        }

        private static ResourceSet from(final SessionResources resources, final Path path) {
            return new ResourceSet(resources.store, resources.maps, resources.updates, resources.leaseManager,
                resources.scheduler, resources.newlyCreated,
                resources.creationOwnership != null ? resources.creationOwnership
                    : resources.newlyCreated ? WorkspaceCreationOwnership.capture(path) : null);
        }

        private static ResourceSet from(final SessionResources resources) {
            return new ResourceSet(resources.store, resources.maps, resources.updates, resources.leaseManager,
                resources.scheduler, resources.newlyCreated, resources.creationOwnership);
        }
    }

    private static final class SessionConstructionException extends RuntimeException {
        private final ResourceSet resources;

        private SessionConstructionException(final RuntimeException cause, final ResourceSet resources) {
            super("Graph workspace resources could not be constructed", cause);
            this.resources = resources;
        }
    }

    private final Object monitor = new Object();
    private final WorkspaceSessionRegistry sessions;
    private final SessionFactory sessionFactory;
    private final GraphWorkspaceViewFactory viewFactory;
    private final WorkspaceUriResolver uriResolver = new WorkspaceUriResolver();
    private final Map<WorkspaceSessionId, Session> openSessions =
        new HashMap<WorkspaceSessionId, Session>();

    public DefaultGraphWorkspaceController(final ModeController modeController,
            final GraphWorkspaceViewFactory viewFactory) {
        this(new WorkspaceSessionRegistry(), modeController, viewFactory);
    }

    private DefaultGraphWorkspaceController(final WorkspaceSessionRegistry sessions,
            final ModeController modeController, final GraphWorkspaceViewFactory viewFactory) {
        this(sessions, new ProductionSessionFactory(modeController, sessions), viewFactory);
    }

    DefaultGraphWorkspaceController(final WorkspaceSessionRegistry sessions, final SessionFactory sessionFactory,
            final GraphWorkspaceViewFactory viewFactory) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.sessionFactory = Objects.requireNonNull(sessionFactory, "sessionFactory");
        this.viewFactory = Objects.requireNonNull(viewFactory, "viewFactory");
    }

    @Override
    public GraphWorkspaceHandle open(final Path workspaceFile) {
        final Path path = uriResolver.canonical(Objects.requireNonNull(workspaceFile, "workspaceFile"));
        while (true) {
            Session existing = null;
            Session session = null;
            boolean create = false;
            synchronized (monitor) {
                final Optional<WorkspaceSessionId> owner = sessions.owner(path);
                if (owner.isPresent()) {
                    existing = openSessions.get(owner.get());
                    if (existing == null) {
                        throw new GraphWorkspaceOpenException(path,
                            new IllegalStateException("Workspace registry owner has no live session"));
                    }
                }
                else {
                    final WorkspaceSessionId sessionId = WorkspaceSessionId.of(UUID.randomUUID());
                    if (!sessions.register(sessionId, path)) {
                        continue;
                    }
                    create = !Files.exists(path);
                    session = new Session(sessionId, path);
                    openSessions.put(sessionId, session);
                }
            }
            if (existing != null) {
                if (SwingUtilities.isEventDispatchThread() && (existing.isOpening() || existing.isClosing())) {
                    throw new GraphWorkspaceOpenException(path,
                        new IllegalStateException("Workspace is still being opened or closed"));
                }
                if (existing.awaitOpen()) {
                    if (existing.focus()) {
                        return existing.handle;
                    }
                    continue;
                }
                continue;
            }
            return finishOpen(session, path, create);
        }
    }

    private GraphWorkspaceHandle finishOpen(final Session session, final Path path, final boolean create) {
        SessionResources resources = null;
        ResourceSet cleanup = null;
        try {
            final SessionResources openedResources = Objects.requireNonNull(
                sessionFactory.open(path, session.id, create), "session resources");
            resources = openedResources;
            cleanup = ResourceSet.from(openedResources, path);
            session.resources = openedResources;
            final WorkspaceSessionStatusPublisher sessionStatusPublisher = new WorkspaceSessionStatusPublisher(
                openedResources.store, openedResources.router);
            session.sessionStatusPublisher = sessionStatusPublisher;
            final WorkspaceCloseController closeController = new SessionCloseController(session);
            final DefaultGraphWorkspaceHandle handle = new DefaultGraphWorkspaceHandle(openedResources.router,
                openedResources.updates, closeController, sessionStatusPublisher, session.monitor);
            session.handle = handle;
            final GraphWorkspaceViewBinding binding = new GraphWorkspaceViewBinding() {
                @Override
                public CanvasState currentCanvasState() {
                    return openedResources.updates.currentState();
                }

                @Override
                public org.freeplane.plugin.graph.workspace.model.Viewport currentViewport() {
                    return openedResources.store.currentDocument().viewport();
                }

                @Override
                public List<GraphWorkspaceViewBinding.MapRegistration> currentMapRows() {
                    return mapRows(openedResources.store.currentDocument(), openedResources.maps);
                }

                @Override
                public boolean isReadOnly() {
                    return openedResources.store.currentDocument().compatibility()
                        == org.freeplane.plugin.graph.workspace.model.WorkspaceCompatibility.READ_ONLY_NEWER;
                }

                @Override
                public WorkspaceSessionStatus currentSessionStatus() {
                    return sessionStatusPublisher.currentSessionStatus();
                }

                @Override
                public ListenerRegistration addSessionStatusListener(final WorkspaceSessionStatusListener listener) {
                    return sessionStatusPublisher.addListener(listener);
                }

                @Override
                public ListenerRegistration addCanvasStateListener(final CanvasStateListener listener) {
                    return openedResources.updates.addCanvasStateListener(listener);
                }
            };
            session.view = Objects.requireNonNull(viewFactory.create(handle, binding, closeController),
                "workspace view");
            session.view.show();
            session.publishOpen();
            return handle;
        }
        catch (SessionConstructionException failure) {
            final RuntimeException cause = (RuntimeException) failure.getCause();
            rollbackSession(session, failure.resources, cause);
            throw new GraphWorkspaceOpenException(path, cause);
        }
        catch (RuntimeException failure) {
            rollbackSession(session, cleanup != null ? cleanup : resources == null ? null : ResourceSet.from(resources),
                failure);
            throw new GraphWorkspaceOpenException(path, failure);
        }
    }

    private void rollbackSession(final Session session, final ResourceSet resources,
            final RuntimeException original) {
        final RuntimeException publisherCloseFailure = closeSessionStatusPublisher(session);
        if (resources != null && SwingUtilities.isEventDispatchThread()) {
            final ResourceSet cleanup = resources;
            final Thread thread = new Thread(() -> {
                RuntimeException cleanupFailure = recordFailure(publisherCloseFailure, cleanupResources(cleanup));
                SwingUtilities.invokeLater(() -> finishRollback(session, cleanup, original, true, cleanupFailure));
            }, "freeplane-graph-workspace-open-rollback");
            thread.setDaemon(true);
            thread.start();
            return;
        }
        RuntimeException cleanupFailure = publisherCloseFailure;
        if (resources != null) {
            cleanupFailure = recordFailure(cleanupFailure, cleanupResources(resources));
        }
        finishRollback(session, resources, original, false, cleanupFailure);
    }

    private void finishRollback(final Session session, final ResourceSet resources,
            final RuntimeException original, final boolean reportCleanupFailure,
            final RuntimeException initialCleanupFailure) {
        RuntimeException cleanupFailure = initialCleanupFailure;
        try {
            if (session.view != null) {
                session.view.close();
            }
        }
        catch (RuntimeException viewFailure) {
            cleanupFailure = recordFailure(cleanupFailure, viewFailure);
        }
        cleanupFailure = recordFailure(cleanupFailure, deleteNewWorkspace(resources));
        session.failOpen();
        synchronized (monitor) {
            openSessions.remove(session.id);
            sessions.unregister(session.id);
        }
        if (cleanupFailure != null) {
            if (reportCleanupFailure) {
                throw cleanupFailure;
            }
            original.addSuppressed(cleanupFailure);
        }
    }

    private static RuntimeException cleanupResources(final ResourceSet resources) {
        if (resources == null) {
            return null;
        }
        RuntimeException failure = null;
        if (resources.store != null) {
            try {
                resources.store.discardAndClose();
            }
            catch (RuntimeException exception) {
                failure = recordFailure(failure, exception);
            }
        }
        failure = recordFailure(failure, closeRemainingResources(resources));
        return failure;
    }

    private static RuntimeException closeRemainingResources(final ResourceSet resources) {
        if (resources == null) {
            return null;
        }
        return closeRemainingResources(resources.updates, resources.maps, resources.leaseManager, resources.scheduler);
    }

    private static RuntimeException closeRemainingResources(final GraphUpdateCoordinator updates,
            final WorkspaceMapCoordinator maps, final MapLeaseManager leaseManager,
            final ScheduledExecutorService scheduler) {
        RuntimeException failure = null;
        if (updates != null) {
            try {
                closeUpdatesOffEdt(updates);
            }
            catch (RuntimeException exception) {
                failure = recordFailure(failure, exception);
            }
        }
        else if (maps != null) {
            try {
                maps.close();
            }
            catch (RuntimeException exception) {
                failure = recordFailure(failure, exception);
            }
        }
        if (leaseManager != null) {
            try {
                leaseManager.close();
            }
            catch (RuntimeException exception) {
                failure = recordFailure(failure, exception);
            }
        }
        if (scheduler != null) {
            try {
                scheduler.shutdownNow();
            }
            catch (RuntimeException exception) {
                failure = recordFailure(failure, exception);
            }
        }
        return failure;
    }

    private static void closeRemainingResourcesAsync(final ResourceSet resources,
            final TeardownCompletion completion) {
        final Thread thread = new Thread(() -> {
            final RuntimeException failure = closeRemainingResources(resources);
            SwingUtilities.invokeLater(() -> completion.complete(failure));
        }, "freeplane-graph-workspace-close");
        thread.setDaemon(true);
        thread.start();
    }

    private static void closeRemainingResourcesAsync(final SessionResources resources,
            final TeardownCompletion completion) {
        closeRemainingResourcesAsync(ResourceSet.from(resources), completion);
    }

    private interface TeardownCompletion {
        void complete(RuntimeException failure);
    }

    private static RuntimeException deleteNewWorkspace(final ResourceSet resources) {
        if (resources == null || !resources.newlyCreated) {
            return null;
        }
        if (resources.creationOwnership == null) {
            return new IllegalStateException("Unable to verify ownership of newly-created graph workspace");
        }
        return resources.creationOwnership.removeIfOwned();
    }

    private static RuntimeException recordFailure(final RuntimeException prior, final RuntimeException next) {
        if (next == null) {
            return prior;
        }
        if (prior == null) {
            return next;
        }
        prior.addSuppressed(next);
        return prior;
    }

    private static RuntimeException closeSessionStatusPublisher(final Session session) {
        try {
            session.closeSessionStatusPublisher();
            return null;
        }
        catch (RuntimeException failure) {
            return failure;
        }
    }

    private static void closeUpdatesOffEdt(final GraphUpdateCoordinator updates) {
        if (SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Graph workspace updates must close off the EDT");
        }
        updates.close();
    }

    private final class SessionCloseController implements WorkspaceCloseController {
        private final Session session;

        private SessionCloseController(final Session session) {
            this.session = session;
        }

        @Override
        public boolean saveAndClose() {
            return closeSession(session, false);
        }

        @Override
        public boolean retrySaveAndClose() {
            return closeSession(session, false);
        }

        @Override
        public boolean discardAndClose() {
            return closeSession(session, true);
        }

        @Override
        public void cancelClose() {
        }
    }

    private boolean closeSession(final Session session, final boolean discard) {
        RuntimeException publisherCloseFailure = null;
        synchronized (session.monitor) {
            if (session.beginCloseLocked()) {
                return true;
            }
            publisherCloseFailure = closeSessionStatusPublisher(session);
            try {
                if (discard) {
                    session.resources.store.discardAndClose();
                }
                else {
                    session.resources.store.close();
                }
            }
            catch (RuntimeException failure) {
                session.reopenAfterSaveFailureLocked();
                return false;
            }
        }
        if (SwingUtilities.isEventDispatchThread()) {
            closeSessionAsynchronously(session, publisherCloseFailure);
            return true;
        }
        return finishClose(session, recordFailure(publisherCloseFailure,
            closeRemainingResources(ResourceSet.from(session.resources))), true);
    }

    private void closeSessionAsynchronously(final Session session, final RuntimeException publisherCloseFailure) {
        closeRemainingResourcesAsync(session.resources,
            failure -> finishClose(session, recordFailure(publisherCloseFailure, failure), true));
    }

    private boolean finishClose(final Session session, final RuntimeException initialFailure,
            final boolean reportFailure) {
        RuntimeException failure = initialFailure;
        try {
            session.closeView();
        }
        catch (RuntimeException exception) {
            failure = recordFailure(failure, exception);
        }
        synchronized (monitor) {
            if (openSessions.get(session.id) == session) {
                openSessions.remove(session.id);
            }
            sessions.unregister(session.id);
        }
        synchronized (session.monitor) {
            session.finishCloseLocked();
        }
        if (failure != null && reportFailure) {
            throw failure;
        }
        return true;
    }

    private static final class Session {
        private enum State {
            OPENING, OPEN, CLOSING, CLOSED
        }

        private final Object monitor = new Object();
        private final WorkspaceSessionId id;
        private final Path path;
        private SessionResources resources;
        private State state = State.OPENING;
        private Thread closingThread;
        private DefaultGraphWorkspaceHandle handle;
        private WorkspaceSessionStatusPublisher sessionStatusPublisher;
        private GraphWorkspaceView view;
        private boolean closingView;

        private Session(final WorkspaceSessionId id, final Path path) {
            this.id = id;
            this.path = path;
        }

        private void closeSessionStatusPublisher() {
            final WorkspaceSessionStatusPublisher publisher;
            synchronized (monitor) {
                publisher = sessionStatusPublisher;
            }
            if (publisher != null) {
                publisher.close();
            }
        }

        private boolean awaitOpen() {
            synchronized (monitor) {
                while (state == State.OPENING || state == State.CLOSING) {
                    try {
                        monitor.wait();
                    }
                    catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Interrupted while opening graph workspace", interrupted);
                    }
                }
                return state == State.OPEN;
            }
        }

        private boolean isOpening() {
            synchronized (monitor) {
                return state == State.OPENING;
            }
        }

        private boolean isClosing() {
            synchronized (monitor) {
                return state == State.CLOSING;
            }
        }

        private void publishOpen() {
            synchronized (monitor) {
                state = State.OPEN;
                monitor.notifyAll();
            }
        }

        private void failOpen() {
            synchronized (monitor) {
                state = State.CLOSED;
                if (handle != null) {
                    handle.markClosed();
                }
                monitor.notifyAll();
            }
        }

        private boolean focus() {
            synchronized (monitor) {
                if (state != State.OPEN || view == null) {
                    return false;
                }
                view.focus();
                return true;
            }
        }

        private boolean beginCloseLocked() {
            while (state == State.CLOSING && closingThread != Thread.currentThread()) {
                if (SwingUtilities.isEventDispatchThread()) {
                    return true;
                }
                try {
                    monitor.wait();
                }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while closing graph workspace", interrupted);
                }
            }
            if (state == State.CLOSED || state == State.CLOSING || state != State.OPEN) {
                return true;
            }
            state = State.CLOSING;
            closingThread = Thread.currentThread();
            if (handle != null) {
                handle.markClosing();
            }
            return false;
        }

        private void reopenAfterSaveFailureLocked() {
            state = State.OPEN;
            closingThread = null;
            if (handle != null) {
                handle.reopenAfterCloseFailure();
            }
            monitor.notifyAll();
        }

        private void closeView() {
            final GraphWorkspaceView value;
            synchronized (monitor) {
                if (view == null || closingView) {
                    return;
                }
                closingView = true;
                value = view;
            }
            try {
                value.close();
            }
            finally {
                synchronized (monitor) {
                    closingView = false;
                }
            }
        }

        private void finishCloseLocked() {
            state = State.CLOSED;
            closingThread = null;
            if (handle != null) {
                handle.markClosed();
            }
            monitor.notifyAll();
        }
    }

    private static final class ProductionSessionFactory implements SessionFactory {
        private final ModeController modeController;
        private final WorkspaceSessionRegistry sessions;

        private ProductionSessionFactory(final ModeController modeController,
                final WorkspaceSessionRegistry sessions) {
            this.modeController = Objects.requireNonNull(modeController, "modeController");
            this.sessions = Objects.requireNonNull(sessions, "sessions");
        }

        @Override
        public SessionResources open(final Path path, final WorkspaceSessionId sessionId, final boolean create) {
            final ScheduledExecutorService scheduler = newStoreScheduler();
            GraphWorkspaceStore store = null;
            MapLeaseManager leaseManager = null;
            WorkspaceMapCoordinator maps = null;
            GraphUpdateCoordinator updates = null;
            WorkspaceCreationOwnership creationOwnership = null;
            try {
                final WorkspaceXmlCodec codec = new WorkspaceXmlCodec(
                    new WorkspaceMigrationRegistry(Collections.emptyList()));
                final AtomicWorkspaceWriter writer = AtomicWorkspaceWriter.standard();
                store = create ? GraphWorkspaceStore.create(path, codec, writer, scheduler)
                    : GraphWorkspaceStore.open(path, codec, writer, scheduler);
                if (create) {
                    creationOwnership = WorkspaceCreationOwnership.capture(path);
                }
                leaseManager = new MapLeaseManager(path, modeController);
                maps = new WorkspaceMapCoordinator(store, leaseManager);
                updates = new GraphUpdateCoordinator(maps, store, leaseManager, new ProjectionEngine(),
                    new LayoutSettleLoop(store.currentDocument().id()));
                final EdtExecutor edt = new SwingEdtExecutor();
                final FreeplaneMapCommandExecutor mapCommands = new FreeplaneMapCommandExecutor(store, maps::find,
                    modeController, edt, new ViewMaterializationTracker(modeController));
                final SourceNavigation navigation = new SourceNavigation(store, maps::find, modeController, edt);
                final DefaultPurgeCommandHandler purge = new DefaultPurgeCommandHandler(updates, store, edt);
                final DefaultContributorDeletionHandler deletion =
                    new DefaultContributorDeletionHandler(updates, store, mapCommands, edt);
                final GraphCommandRouter router = new GraphCommandRouter(store, maps, mapCommands, navigation,
                    updates, sessions, sessionId, purge, deletion);
                updates.start();
                return new SessionResources(store, maps, updates, leaseManager, router, scheduler, create,
                    creationOwnership);
            }
            catch (RuntimeException failure) {
                throw new SessionConstructionException(failure,
                    new ResourceSet(store, maps, updates, leaseManager, scheduler, create, creationOwnership));
            }
        }
    }

    private static List<GraphWorkspaceViewBinding.MapRegistration> mapRows(final WorkspaceDocument document,
            final WorkspaceMapCoordinator maps) {
        final WorkspaceDocument value = Objects.requireNonNull(document, "workspace document");
        final List<GraphWorkspaceViewBinding.MapRegistration> rows =
            new ArrayList<GraphWorkspaceViewBinding.MapRegistration>(value.maps().size());
        for (final MapReference reference : value.maps()) {
            rows.add(GraphWorkspaceViewBinding.MapRegistration.of(reference.id(), displayIdentity(reference),
                availabilityFor(reference, maps)));
        }
        return Collections.unmodifiableList(rows);
    }

    private static MapAvailability availabilityFor(final MapReference reference,
            final WorkspaceMapCoordinator maps) {
        if (!reference.active()) {
            return MapAvailability.INACTIVE;
        }
        if (maps == null) {
            return MapAvailability.LOADING;
        }
        final Optional<MapLease> lease = maps.find(reference.id());
        if (!lease.isPresent()) {
            return MapAvailability.LOADING;
        }
        try {
            final MapOperationalState state = lease.get().state();
            if (state == null) {
                return MapAvailability.UNREADABLE;
            }
            switch (state) {
            case LOADING:
                return MapAvailability.LOADING;
            case AVAILABLE:
                return MapAvailability.AVAILABLE;
            case MISSING:
                return MapAvailability.MISSING;
            case UNREADABLE:
                return MapAvailability.UNREADABLE;
            case PASSWORD_REQUIRED:
                return MapAvailability.PASSWORD_REQUIRED;
            case RELOAD_REQUIRED:
                return MapAvailability.RELOAD_REQUIRED;
            default:
                return MapAvailability.UNREADABLE;
            }
        }
        catch (RuntimeException failure) {
            return MapAvailability.UNREADABLE;
        }
    }

    private static String displayIdentity(final MapReference reference) {
        final String path = reference.storedUri().getPath();
        final String value = path == null || path.isEmpty() ? reference.storedUri().toString() : path;
        final int separator = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'));
        return separator >= 0 && separator < value.length() - 1 ? value.substring(separator + 1) : value;
    }

    private static ScheduledExecutorService newStoreScheduler() {
        return Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(final Runnable task) {
                final Thread thread = new Thread(task, "freeplane-graph-workspace-store");
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    private static final class SwingEdtExecutor implements EdtExecutor {
        @Override
        public <T> T call(final Callable<T> task) {
            Objects.requireNonNull(task, "task");
            if (isEdt()) {
                try {
                    return task.call();
                }
                catch (RuntimeException exception) {
                    throw exception;
                }
                catch (Exception exception) {
                    throw new IllegalStateException("EDT task failed", exception);
                }
            }
            final AtomicReference<T> result = new AtomicReference<T>();
            final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
            try {
                SwingUtilities.invokeAndWait(() -> {
                    try {
                        result.set(task.call());
                    }
                    catch (Throwable exception) {
                        failure.set(exception);
                    }
                });
            }
            catch (Exception exception) {
                throw new IllegalStateException("Unable to execute task on the EDT", exception);
            }
            final Throwable exception = failure.get();
            if (exception == null) {
                return result.get();
            }
            if (exception instanceof RuntimeException) {
                throw (RuntimeException) exception;
            }
            throw new IllegalStateException("EDT task failed", exception);
        }

        @Override
        public void execute(final Runnable task) {
            Objects.requireNonNull(task, "task");
            if (isEdt()) {
                task.run();
            }
            else {
                SwingUtilities.invokeLater(task);
            }
        }

        @Override
        public boolean isEdt() {
            return SwingUtilities.isEventDispatchThread();
        }
    }
}
