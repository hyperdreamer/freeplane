package org.freeplane.plugin.graph.control;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
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
import org.freeplane.plugin.graph.adapter.MapLeaseManager;
import org.freeplane.plugin.graph.command.DefaultContributorDeletionHandler;
import org.freeplane.plugin.graph.command.DefaultPurgeCommandHandler;
import org.freeplane.plugin.graph.command.FreeplaneMapCommandExecutor;
import org.freeplane.plugin.graph.command.GraphCommandRouter;
import org.freeplane.plugin.graph.command.SourceNavigation;
import org.freeplane.plugin.graph.command.ViewMaterializationTracker;
import org.freeplane.plugin.graph.projection.ProjectionEngine;
import org.freeplane.plugin.graph.workspace.AtomicWorkspaceWriter;
import org.freeplane.plugin.graph.workspace.GraphWorkspaceStore;
import org.freeplane.plugin.graph.workspace.ListenerRegistration;
import org.freeplane.plugin.graph.workspace.WorkspaceUriResolver;
import org.freeplane.plugin.graph.workspace.io.WorkspaceMigrationRegistry;
import org.freeplane.plugin.graph.workspace.io.WorkspaceXmlCodec;

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

        SessionResources(final GraphWorkspaceStore store, final GraphUpdateCoordinator updates,
                final MapLeaseManager leaseManager, final GraphCommandRouter router,
                final ScheduledExecutorService scheduler, final boolean newlyCreated) {
            this(store, null, updates, leaseManager, router, scheduler, newlyCreated);
        }

        SessionResources(final GraphWorkspaceStore store, final WorkspaceMapCoordinator maps,
                final GraphUpdateCoordinator updates, final MapLeaseManager leaseManager,
                final GraphCommandRouter router, final ScheduledExecutorService scheduler,
                final boolean newlyCreated) {
            this.store = Objects.requireNonNull(store, "store");
            this.maps = maps;
            this.updates = Objects.requireNonNull(updates, "updates");
            this.leaseManager = Objects.requireNonNull(leaseManager, "leaseManager");
            this.router = Objects.requireNonNull(router, "router");
            this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
            this.newlyCreated = newlyCreated;
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
                    existing.focus();
                    return existing.handle;
                }
                continue;
            }
            return finishOpen(session, path, create);
        }
    }

    private GraphWorkspaceHandle finishOpen(final Session session, final Path path, final boolean create) {
        SessionResources resources = null;
        try {
            final SessionResources openedResources = Objects.requireNonNull(
                sessionFactory.open(path, session.id, create), "session resources");
            resources = openedResources;
            session.resources = openedResources;
            final WorkspaceCloseController closeController = new SessionCloseController(session);
            final DefaultGraphWorkspaceHandle handle = new DefaultGraphWorkspaceHandle(openedResources.router,
                openedResources.updates, closeController, session.monitor);
            session.handle = handle;
            final GraphWorkspaceViewBinding binding = new GraphWorkspaceViewBinding() {
                @Override
                public CanvasState currentCanvasState() {
                    return openedResources.updates.currentState();
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
        catch (RuntimeException failure) {
            rollbackSession(session, resources, create, failure);
            throw new GraphWorkspaceOpenException(path, failure);
        }
    }

    private void rollbackSession(final Session session, final SessionResources resources,
            final boolean create, final RuntimeException original) {
        if (resources != null && SwingUtilities.isEventDispatchThread()) {
            final Thread thread = new Thread(() -> {
                RuntimeException failure = original;
                try {
                    resources.store.discardAndClose();
                }
                catch (RuntimeException cleanupFailure) {
                    failure = recordFailure(failure, cleanupFailure);
                }
                failure = recordFailure(failure, closeRemainingResources(resources));
                final RuntimeException completedFailure = failure;
                SwingUtilities.invokeLater(() -> finishRollback(session, resources, create, completedFailure));
            }, "freeplane-graph-workspace-open-rollback");
            thread.setDaemon(true);
            thread.start();
            return;
        }
        RuntimeException failure = original;
        if (resources != null) {
            try {
                cleanupResources(resources);
            }
            catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
        }
        finishRollback(session, resources, create, failure);
    }

    private void finishRollback(final Session session, final SessionResources resources,
            final boolean create, final RuntimeException original) {
        RuntimeException failure = original;
        try {
            if (session.view != null) {
                session.view.close();
            }
        }
        catch (RuntimeException viewFailure) {
            failure.addSuppressed(viewFailure);
        }
        deleteNewWorkspace(session.path, resources != null ? resources.newlyCreated : create, failure);
        session.failOpen();
        synchronized (monitor) {
            openSessions.remove(session.id);
            sessions.unregister(session.id);
        }
    }

    private static void cleanupResources(final SessionResources resources) {
        RuntimeException failure = null;
        try {
            resources.store.discardAndClose();
        }
        catch (RuntimeException exception) {
            failure = recordFailure(failure, exception);
        }
        failure = recordFailure(failure, closeRemainingResources(resources));
        if (failure != null) {
            throw failure;
        }
    }

    private static RuntimeException closeRemainingResources(final SessionResources resources) {
        return closeRemainingResources(resources.updates, resources.maps, resources.leaseManager, resources.scheduler);
    }

    private static RuntimeException closeRemainingResources(final GraphUpdateCoordinator updates,
            final WorkspaceMapCoordinator maps, final MapLeaseManager leaseManager,
            final ScheduledExecutorService scheduler) {
        RuntimeException failure = null;
        try {
            closeUpdatesOffEdt(updates);
        }
        catch (RuntimeException exception) {
            failure = recordFailure(failure, exception);
        }
        if (maps != null) {
            try {
                maps.close();
            }
            catch (RuntimeException exception) {
                failure = recordFailure(failure, exception);
            }
        }
        try {
            leaseManager.close();
        }
        catch (RuntimeException exception) {
            failure = recordFailure(failure, exception);
        }
        try {
            scheduler.shutdownNow();
        }
        catch (RuntimeException exception) {
            failure = recordFailure(failure, exception);
        }
        return failure;
    }

    private static void closeRemainingResourcesAsync(final SessionResources resources,
            final TeardownCompletion completion) {
        final Thread thread = new Thread(() -> {
            final RuntimeException failure = closeRemainingResources(resources);
            SwingUtilities.invokeLater(() -> completion.complete(failure));
        }, "freeplane-graph-workspace-close");
        thread.setDaemon(true);
        thread.start();
    }

    private static void closeRemainingResourcesAsync(final GraphUpdateCoordinator updates,
            final WorkspaceMapCoordinator maps, final MapLeaseManager leaseManager,
            final ScheduledExecutorService scheduler) {
        final Thread thread = new Thread(() -> closeRemainingResources(updates, maps, leaseManager, scheduler),
            "freeplane-graph-workspace-factory-rollback");
        thread.setDaemon(true);
        thread.start();
    }

    private interface TeardownCompletion {
        void complete(RuntimeException failure);
    }

    private static void deleteNewWorkspace(final Path path, final boolean newlyCreated,
            final RuntimeException failure) {
        if (!newlyCreated) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        }
        catch (IOException deleteFailure) {
            failure.addSuppressed(deleteFailure);
        }
    }

    private static RuntimeException recordFailure(final RuntimeException prior, final RuntimeException next) {
        if (prior == null) {
            return next;
        }
        prior.addSuppressed(next);
        return prior;
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
        synchronized (session.monitor) {
            if (session.beginCloseLocked()) {
                return true;
            }
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
            closeSessionAsynchronously(session);
            return true;
        }
        return finishClose(session, closeRemainingResources(session.resources), true);
    }

    private void closeSessionAsynchronously(final Session session) {
        closeRemainingResourcesAsync(session.resources, failure -> finishClose(session, failure, false));
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
        private GraphWorkspaceView view;
        private boolean closingView;

        private Session(final WorkspaceSessionId id, final Path path) {
            this.id = id;
            this.path = path;
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

        private void focus() {
            synchronized (monitor) {
                if (view != null) {
                    view.focus();
                }
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
            try {
                final WorkspaceXmlCodec codec = new WorkspaceXmlCodec(
                    new WorkspaceMigrationRegistry(Collections.emptyList()));
                final AtomicWorkspaceWriter writer = AtomicWorkspaceWriter.standard();
                store = create ? GraphWorkspaceStore.create(path, codec, writer, scheduler)
                    : GraphWorkspaceStore.open(path, codec, writer, scheduler);
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
                return new SessionResources(store, maps, updates, leaseManager, router, scheduler, create);
            }
            catch (RuntimeException failure) {
                if (store != null) {
                    try {
                        store.discardAndClose();
                    }
                    catch (RuntimeException cleanupFailure) {
                        failure.addSuppressed(cleanupFailure);
                    }
                }
                if (updates != null && SwingUtilities.isEventDispatchThread()) {
                    closeRemainingResourcesAsync(updates, maps, leaseManager, scheduler);
                }
                else {
                    if (updates != null) {
                        failure = recordFailure(failure, closeRemainingResources(updates, maps, leaseManager, scheduler));
                    }
                    else if (maps != null) {
                        try {
                            maps.close();
                        }
                        catch (RuntimeException cleanupFailure) {
                            failure.addSuppressed(cleanupFailure);
                        }
                        if (leaseManager != null) {
                            try {
                                leaseManager.close();
                            }
                            catch (RuntimeException cleanupFailure) {
                                failure.addSuppressed(cleanupFailure);
                            }
                        }
                        scheduler.shutdownNow();
                    }
                    else {
                        if (leaseManager != null) {
                            try {
                                leaseManager.close();
                            }
                            catch (RuntimeException cleanupFailure) {
                                failure.addSuppressed(cleanupFailure);
                            }
                        }
                        scheduler.shutdownNow();
                    }
                }
                throw failure;
            }
        }
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
