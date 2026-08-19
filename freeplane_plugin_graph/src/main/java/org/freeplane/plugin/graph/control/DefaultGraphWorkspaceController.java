package org.freeplane.plugin.graph.control;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardCopyOption;
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
import org.freeplane.plugin.graph.workspace.WorkspaceSaveException;
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
            final Session existing;
            synchronized (monitor) {
                final Optional<WorkspaceSessionId> owner = sessions.owner(path);
                if (!owner.isPresent()) {
                    return openNew(path);
                }
                existing = openSessions.get(owner.get());
                if (existing == null) {
                    throw new GraphWorkspaceOpenException(path,
                        new IllegalStateException("Workspace registry owner has no live session"));
                }
            }
            if (existing.awaitOpen()) {
                existing.focus();
                return existing.handle;
            }
        }
    }

    private GraphWorkspaceHandle openNew(final Path path) {
        final boolean create = !Files.exists(path);
        final WorkspaceSessionId sessionId = WorkspaceSessionId.of(UUID.randomUUID());
            final SessionResources resources;
            try {
                resources = Objects.requireNonNull(sessionFactory.open(path, sessionId, create),
                    "session resources");
            }
            catch (RuntimeException failure) {
                deleteNewWorkspace(path, create, failure);
                throw new GraphWorkspaceOpenException(path, failure);
            }

            if (!sessions.register(sessionId, path)) {
                rollbackResources(resources, path, failure("Workspace path became owned while opening", null));
                final Optional<WorkspaceSessionId> registeredOwner = sessions.owner(path);
                if (registeredOwner.isPresent()) {
                    final Session existing = openSessions.get(registeredOwner.get());
                    if (existing != null) {
                        existing.focus();
                        return existing.handle;
                    }
                }
                throw new GraphWorkspaceOpenException(path,
                    new IllegalStateException("Unable to reserve workspace path"));
            }

            final Session session = new Session(sessionId, path, resources);
            final WorkspaceCloseController closeController = new SessionCloseController(session);
            final DefaultGraphWorkspaceHandle handle = new DefaultGraphWorkspaceHandle(resources.router,
                resources.updates, closeController, session.monitor);
            session.handle = handle;
            final GraphWorkspaceViewBinding binding = new GraphWorkspaceViewBinding() {
                @Override
                public CanvasState currentCanvasState() {
                    return resources.updates.currentState();
                }

                @Override
                public ListenerRegistration addCanvasStateListener(final CanvasStateListener listener) {
                    return resources.updates.addCanvasStateListener(listener);
                }
            };
            try {
                session.view = Objects.requireNonNull(viewFactory.create(handle, binding, closeController),
                    "workspace view");
                openSessions.put(sessionId, session);
                session.view.show();
                return handle;
            }
            catch (RuntimeException failure) {
                rollbackSession(session, failure);
                throw new GraphWorkspaceOpenException(path, failure);
            }
    }

    private void rollbackSession(final Session session, final RuntimeException original) {
        RuntimeException failure = original;
        try {
            cleanupResources(session.resources);
        }
        catch (RuntimeException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
        try {
            if (session.view != null) {
                session.view.close();
            }
        }
        catch (RuntimeException viewFailure) {
            failure.addSuppressed(viewFailure);
        }
        openSessions.remove(session.id);
        sessions.unregister(session.id);
        deleteNewWorkspace(session.path, session.resources.newlyCreated, failure);
    }

    private void rollbackResources(final SessionResources resources, final Path path,
            final RuntimeException original) {
        RuntimeException failure = original;
        try {
            cleanupResources(resources);
        }
        catch (RuntimeException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
        deleteNewWorkspace(path, resources.newlyCreated, failure);
    }

    private static void cleanupResources(final SessionResources resources) {
        RuntimeException failure = null;
        try {
            resources.store.discardAndClose();
        }
        catch (RuntimeException exception) {
            failure = recordFailure(failure, exception);
        }
        try {
            closeUpdatesOffEdt(resources.updates);
        }
        catch (RuntimeException exception) {
            failure = recordFailure(failure, exception);
        }
        if (resources.maps != null) {
            try {
                resources.maps.close();
            }
            catch (RuntimeException exception) {
                failure = recordFailure(failure, exception);
            }
        }
        try {
            resources.leaseManager.close();
        }
        catch (RuntimeException exception) {
            failure = recordFailure(failure, exception);
        }
        try {
            resources.scheduler.shutdownNow();
        }
        catch (RuntimeException exception) {
            failure = recordFailure(failure, exception);
        }
        if (failure != null) {
            throw failure;
        }
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

    private static RuntimeException failure(final String message, final Throwable cause) {
        return cause == null ? new IllegalStateException(message) : new IllegalStateException(message, cause);
    }

    private static RuntimeException recordFailure(final RuntimeException prior, final RuntimeException next) {
        if (prior == null) {
            return next;
        }
        prior.addSuppressed(next);
        return prior;
    }

    private static void closeUpdatesOffEdt(final GraphUpdateCoordinator updates) {
        if (!SwingUtilities.isEventDispatchThread()) {
            updates.close();
            return;
        }
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        final Thread thread = new Thread(() -> {
            try {
                updates.close();
            }
            catch (Throwable exception) {
                failure.set(exception);
            }
        }, "freeplane-graph-workspace-close");
        thread.setDaemon(true);
        thread.start();
        try {
            thread.join();
        }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while closing graph workspace updates", interrupted);
        }
        final Throwable exception = failure.get();
        if (exception == null) {
            return;
        }
        if (exception instanceof RuntimeException) {
            throw (RuntimeException) exception;
        }
        throw new IllegalStateException("Unable to close graph workspace updates", exception);
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
            if (!discard) {
                try {
                    session.resources.store.close();
                }
                catch (RuntimeException failure) {
                    session.reopenAfterSaveFailureLocked();
                    return false;
                }
            }
            else {
                try {
                    session.resources.store.discardAndClose();
                }
                catch (RuntimeException failure) {
                    session.reopenAfterSaveFailureLocked();
                    return false;
                }
            }

            RuntimeException failure = null;
            try {
                closeUpdatesOffEdt(session.resources.updates);
            }
            catch (RuntimeException exception) {
                failure = recordFailure(failure, exception);
            }
            try {
                session.resources.leaseManager.close();
            }
            catch (RuntimeException exception) {
                failure = recordFailure(failure, exception);
            }
            try {
                session.resources.scheduler.shutdownNow();
            }
            catch (RuntimeException exception) {
                failure = recordFailure(failure, exception);
            }
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
            session.finishCloseLocked();
            if (failure != null) {
                throw failure;
            }
            return true;
        }
    }

    private static final class Session {
        private enum State {
            OPEN, CLOSING, CLOSED
        }

        private final Object monitor = new Object();
        private final WorkspaceSessionId id;
        private final Path path;
        private final SessionResources resources;
        private State state = State.OPEN;
        private Thread closingThread;
        private DefaultGraphWorkspaceHandle handle;
        private GraphWorkspaceView view;
        private boolean closingView;

        private Session(final WorkspaceSessionId id, final Path path, final SessionResources resources) {
            this.id = id;
            this.path = path;
            this.resources = resources;
        }

        private boolean awaitOpen() {
            synchronized (monitor) {
                while (state == State.CLOSING) {
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

        private void focus() {
            synchronized (monitor) {
                if (view != null) {
                    view.focus();
                }
            }
        }

        private boolean beginCloseLocked() {
            while (state == State.CLOSING && closingThread != Thread.currentThread()) {
                try {
                    monitor.wait();
                }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while closing graph workspace", interrupted);
                }
            }
            if (state == State.CLOSED || state == State.CLOSING) {
                return true;
            }
            state = State.CLOSING;
            closingThread = Thread.currentThread();
            return false;
        }

        private void reopenAfterSaveFailureLocked() {
            state = State.OPEN;
            closingThread = null;
            monitor.notifyAll();
        }

        private void closeView() {
            synchronized (monitor) {
                if (view == null || closingView) {
                    return;
                }
                closingView = true;
            }
            try {
                view.close();
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
                final AtomicWorkspaceWriter writer = new DefaultWriter();
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
                if (updates != null) {
                    try {
                        closeUpdatesOffEdt(updates);
                    }
                    catch (RuntimeException cleanupFailure) {
                        failure.addSuppressed(cleanupFailure);
                    }
                }
                else if (maps != null) {
                    try {
                        maps.close();
                    }
                    catch (RuntimeException cleanupFailure) {
                        failure.addSuppressed(cleanupFailure);
                    }
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
                throw failure;
            }
        }
    }

    private static final class DefaultWriter implements AtomicWorkspaceWriter {
        @Override
        public void write(final Path target, final byte[] bytes) throws WorkspaceSaveException {
            Path temporary = null;
            try {
                final Path absoluteTarget = target.toAbsolutePath().normalize();
                final Path parent = absoluteTarget.getParent();
                if (parent == null) {
                    throw new IllegalArgumentException("Workspace target must have a parent directory: " + target);
                }
                temporary = Files.createTempFile(parent, "." + absoluteTarget.getFileName() + ".", ".tmp");
                Files.write(temporary, bytes, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
                Files.move(temporary, absoluteTarget, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            }
            catch (Exception failure) {
                if (temporary != null) {
                    try {
                        Files.deleteIfExists(temporary);
                    }
                    catch (IOException cleanupFailure) {
                        failure.addSuppressed(cleanupFailure);
                    }
                }
                throw new WorkspaceSaveException(target, failure);
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
