package org.freeplane.plugin.graph.control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.freeplane.plugin.graph.adapter.EdtExecutor;
import org.freeplane.plugin.graph.adapter.MapAdapterEvent;
import org.freeplane.plugin.graph.adapter.MapAdapterListener;
import org.freeplane.plugin.graph.adapter.MapLeaseManager;
import org.freeplane.plugin.graph.geometry.GraphGeometry;
import org.freeplane.plugin.graph.geometry.LayoutPositions;
import org.freeplane.plugin.graph.layout.LayoutFrame;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.ProjectionDiff;
import org.freeplane.plugin.graph.projection.ProjectionEngine;
import org.freeplane.plugin.graph.projection.input.ProjectionInput;
import org.freeplane.plugin.graph.workspace.GraphWorkspaceStore;
import org.freeplane.plugin.graph.workspace.ListenerRegistration;
import org.freeplane.plugin.graph.workspace.WorkspaceStoreEvent;
import org.freeplane.plugin.graph.workspace.WorkspaceStoreListener;

public final class GraphUpdateCoordinator implements AutoCloseable {
    private final Object monitor = new Object();
    private final RebuildPipeline pipeline;
    private final ProjectionBatcher batcher;
    private final LayoutSettleLoop settleLoop;
    private final EdtExecutor edt;
    private final WorkspaceMapCoordinator ownedMaps;
    private final ThreadLocal<Boolean> acceptingBatch = new ThreadLocal<Boolean>();
    private final List<CanvasStateListener> canvasListeners = new ArrayList<CanvasStateListener>();
    private final List<GraphProjectionListener> projectionListeners = new ArrayList<GraphProjectionListener>();
    private static final GraphProjection EMPTY_PROJECTION = GraphProjection.projected(0L,
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList());

    private ListenerRegistration storeListenerRegistration;
    private ListenerRegistration adapterListenerRegistration;
    private GraphProjection projection;
    private CanvasState state;
    private long acceptedGeneration = -1L;
    private boolean started;
    private boolean pending;
    private boolean closed;

    public GraphUpdateCoordinator(final WorkspaceMapCoordinator maps, final ProjectionEngine projectionEngine,
            final LayoutSettleLoop settleLoop) {
        this(maps, null, null, projectionEngine, settleLoop);
    }

    public GraphUpdateCoordinator(final WorkspaceMapCoordinator maps, final GraphWorkspaceStore store,
            final MapLeaseManager leaseManager, final ProjectionEngine projectionEngine,
            final LayoutSettleLoop settleLoop) {
        final WorkspaceMapCoordinator value = Objects.requireNonNull(maps, "maps");
        final ProjectionEngine engine = Objects.requireNonNull(projectionEngine, "projectionEngine");
        final LayoutSettleLoop loop = Objects.requireNonNull(settleLoop, "settleLoop");
        if (store != null || leaseManager != null) {
            try {
                Objects.requireNonNull(store, "store");
                Objects.requireNonNull(leaseManager, "leaseManager");
            }
            catch (RuntimeException failure) {
                try {
                    value.close();
                }
                catch (RuntimeException cleanupFailure) {
                    failure = recordShutdownFailure(failure, cleanupFailure);
                }
                try {
                    loop.close();
                }
                catch (RuntimeException cleanupFailure) {
                    failure = recordShutdownFailure(failure, cleanupFailure);
                }
                throw failure;
            }
        }
        this.pipeline = new LivePipeline(value, engine);
        this.batcher = new ProjectionBatcher(this::acceptBatch);
        this.settleLoop = loop;
        this.edt = new SwingEdtExecutor();
        this.ownedMaps = value;
        initializeState();
        if (store != null) {
            registerSourceListeners(store, leaseManager);
        }
    }

    GraphUpdateCoordinator(final RebuildPipeline pipeline, final ProjectionBatcher batcher,
            final LayoutSettleLoop settleLoop, final EdtExecutor edt, final GraphWorkspaceStore store,
            final MapLeaseManager leaseManager) {
        this.pipeline = Objects.requireNonNull(pipeline, "pipeline");
        this.batcher = Objects.requireNonNull(batcher, "batcher");
        this.settleLoop = Objects.requireNonNull(settleLoop, "settleLoop");
        this.edt = Objects.requireNonNull(edt, "edt");
        this.ownedMaps = null;
        initializeState();
        try {
            registerSourceListeners(Objects.requireNonNull(store, "store"),
                Objects.requireNonNull(leaseManager, "leaseManager"));
        }
        catch (RuntimeException failure) {
            if (store == null || leaseManager == null) {
                try {
                    shutdownResources(null, null);
                }
                catch (RuntimeException cleanupFailure) {
                    failure = recordShutdownFailure(failure, cleanupFailure);
                }
            }
            throw failure;
        }
    }

    private void initializeState() {
        this.projection = emptyProjection();
        final LayoutFrame initialLayout = LayoutFrame.of(0L,
            LayoutPositions.of(Collections.emptyMap(), Collections.emptyMap()), false);
        this.state = CanvasState.of(0L, projection, initialLayout,
            GraphGeometry.of(Collections.emptyMap(), Collections.emptyMap()), OperationalStatus.LOADING);
    }
    private void registerSourceListeners(final GraphWorkspaceStore store, final MapLeaseManager leaseManager) {
        final WorkspaceStoreListener storeListener = new WorkspaceStoreListener() {
            @Override
            public void onWorkspaceStoreEvent(final WorkspaceStoreEvent event) {
                handleWorkspaceStoreEvent(event);
            }
        };
        final MapAdapterListener adapterListener = new MapAdapterListener() {
            @Override
            public void onMapAdapterEvent(final MapAdapterEvent event) {
                handleMapAdapterEvent(event);
            }
        };
        ListenerRegistration storeRegistration = null;
        ListenerRegistration adapterRegistration = null;
        try {
            storeRegistration = Objects.requireNonNull(store.addListener(storeListener), "store listener registration");
            adapterRegistration = Objects.requireNonNull(leaseManager.addListener(adapterListener),
                "adapter listener registration");
            storeListenerRegistration = storeRegistration;
            adapterListenerRegistration = adapterRegistration;
        }
        catch (RuntimeException failure) {
            try {
                shutdownResources(storeRegistration, adapterRegistration);
            }
            catch (RuntimeException cleanupFailure) {
                failure = recordShutdownFailure(failure, cleanupFailure);
            }
            throw failure;
        }
    }

    private void handleWorkspaceStoreEvent(final WorkspaceStoreEvent event) {
        if (event == null) {
            return;
        }
        final WorkspaceStoreEvent.Type type = event.type();
        if (type == WorkspaceStoreEvent.Type.DOCUMENT_CHANGED || type == WorkspaceStoreEvent.Type.IDENTITY_CHANGED) {
            requestSourceRebuild(ChangeKind.STRUCTURE);
        }
    }

    private void handleMapAdapterEvent(final MapAdapterEvent event) {
        if (event != null) {
            requestSourceRebuild(ChangeKind.MAP_STATE);
        }
    }

    private void requestSourceRebuild(final ChangeKind kind) {
        try {
            requestRebuild(kind);
        }
        catch (IllegalStateException ignored) {
            // A source can race coordinator shutdown after it has delivered its last event.
        }
    }

    private static void closeRegistration(final ListenerRegistration registration) {
        if (registration != null) {
            registration.close();
        }
    }

    public void start() {
        final CanvasState initial;
        final long generation;
        synchronized (monitor) {
            requireOpenLocked();
            if (started) {
                return;
            }
            started = true;
            initial = state;
            generation = acceptedGeneration;
        }
        publishCanvasState(initial, generation);
        requestRebuild(ChangeKind.MAP_STATE);
    }

    public CanvasState currentState() {
        synchronized (monitor) {
            return state;
        }
    }

    public GraphProjection currentProjection() {
        synchronized (monitor) {
            return projection;
        }
    }

    public ListenerRegistration addCanvasStateListener(final CanvasStateListener listener) {
        final CanvasStateListener value = Objects.requireNonNull(listener, "listener");
        synchronized (monitor) {
            requireOpenLocked();
            canvasListeners.add(value);
            return new ListenerRemoval(new Runnable() {
                @Override
                public void run() {
                    removeCanvasListener(value);
                }
            });
        }
    }

    public ListenerRegistration addProjectionListener(final GraphProjectionListener listener) {
        final GraphProjectionListener value = Objects.requireNonNull(listener, "listener");
        synchronized (monitor) {
            requireOpenLocked();
            projectionListeners.add(value);
            return new ListenerRemoval(new Runnable() {
                @Override
                public void run() {
                    removeProjectionListener(value);
                }
            });
        }
    }

    public void requestRebuild(final ChangeKind kind) {
        final ChangeKind value = Objects.requireNonNull(kind, "kind");
        synchronized (monitor) {
            requireOpenLocked();
            pending = true;
        }
        try {
            batcher.request(value);
        }
        catch (RuntimeException failure) {
            synchronized (monitor) {
                pending = false;
            }
            throw failure;
        }
    }

    public boolean hasPendingChanges() {
        synchronized (monitor) {
            return !closed && (pending || batcher.hasPendingChanges());
        }
    }

    public void pauseLayout() {
        synchronized (monitor) {
            if (closed) {
                return;
            }
        }
        settleLoop.pause();
    }

    public void restartLayout() {
        synchronized (monitor) {
            if (closed) {
                return;
            }
        }
        settleLoop.restart();
    }

    public void resetLayout() {
        synchronized (monitor) {
            if (closed) {
                return;
            }
        }
        settleLoop.reset();
    }

    @Override
    public void close() {
        final ListenerRegistration storeRegistration;
        final ListenerRegistration adapterRegistration;
        synchronized (monitor) {
            if (closed) {
                return;
            }
            closed = true;
            pending = false;
            canvasListeners.clear();
            projectionListeners.clear();
            storeRegistration = storeListenerRegistration;
            adapterRegistration = adapterListenerRegistration;
            storeListenerRegistration = null;
            adapterListenerRegistration = null;
        }
        if (edt.isEdt() || Boolean.TRUE.equals(acceptingBatch.get())) {
            deferShutdown(storeRegistration, adapterRegistration);
        }
        else {
            shutdownResources(storeRegistration, adapterRegistration);
        }
    }

    void acceptBatch(final AcceptedBatch batch) {
        final AcceptedBatch value = Objects.requireNonNull(batch, "batch");
        final Boolean previous = acceptingBatch.get();
        acceptingBatch.set(Boolean.TRUE);
        try {
            acceptBatchInternal(value);
        }
        finally {
            if (previous == null) {
                acceptingBatch.remove();
            }
            else {
                acceptingBatch.set(previous);
            }
        }
    }

    private void deferShutdown(final ListenerRegistration storeRegistration,
            final ListenerRegistration adapterRegistration) {
        final Thread shutdownThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    shutdownResources(storeRegistration, adapterRegistration);
                }
                catch (RuntimeException ignored) {
                    // An asynchronous close cannot report a shutdown failure to its caller.
                }
            }
        }, "freeplane-graph-update-coordinator-shutdown");
        shutdownThread.setDaemon(true);
        shutdownThread.start();
    }

    private void shutdownResources(final ListenerRegistration storeRegistration,
            final ListenerRegistration adapterRegistration) {
        RuntimeException failure = null;
        try {
            closeRegistration(storeRegistration);
        }
        catch (RuntimeException exception) {
            failure = recordShutdownFailure(failure, exception);
        }
        try {
            closeRegistration(adapterRegistration);
        }
        catch (RuntimeException exception) {
            failure = recordShutdownFailure(failure, exception);
        }
        try {
            if (ownedMaps != null) {
                ownedMaps.close();
            }
        }
        catch (RuntimeException exception) {
            failure = recordShutdownFailure(failure, exception);
        }
        try {
            batcher.close();
        }
        catch (RuntimeException exception) {
            failure = recordShutdownFailure(failure, exception);
        }
        try {
            settleLoop.close();
        }
        catch (RuntimeException exception) {
            failure = recordShutdownFailure(failure, exception);
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static RuntimeException recordShutdownFailure(final RuntimeException prior,
            final RuntimeException next) {
        if (prior == null) {
            return next;
        }
        prior.addSuppressed(next);
        return prior;
    }

    private void acceptBatchInternal(final AcceptedBatch batch) {
        final GraphProjection previous;
        synchronized (monitor) {
            if (closed || batch.generation() <= acceptedGeneration) {
                return;
            }
            acceptedGeneration = batch.generation();
            pending = false;
            previous = projection;
        }
        final GraphProjection next;
        try {
            next = Objects.requireNonNull(pipeline.rebuild(batch, previous), "pipeline projection");
            if (next.generation() != batch.generation()) {
                throw new IllegalArgumentException("Pipeline projection generation is stale");
            }
        }
        catch (RuntimeException failure) {
            publishFailure(batch.generation());
            return;
        }
        final ProjectionDiff diff;
        synchronized (monitor) {
            if (closed || batch.generation() != acceptedGeneration) {
                return;
            }
            projection = next;
            diff = ProjectionDiff.between(previous, next);
        }
        publishProjection(next, batch.generation());
        try {
            settleLoop.start(batch, next, diff, state -> publishCanvasState(state, batch.generation()));
        }
        catch (RuntimeException failure) {
            publishFailure(batch.generation());
        }
    }

    private void publishProjection(final GraphProjection next, final long generation) {
        try {
            edt.execute(new Runnable() {
                @Override
                public void run() {
                    final List<GraphProjectionListener> listeners;
                    synchronized (monitor) {
                        if (closed || generation != acceptedGeneration || projection != next) {
                            return;
                        }
                        listeners = new ArrayList<GraphProjectionListener>(projectionListeners);
                    }
                    for (GraphProjectionListener listener : listeners) {
                        synchronized (monitor) {
                            if (closed || generation != acceptedGeneration || projection != next) {
                                return;
                            }
                        }
                        try {
                            listener.onGraphProjection(next);
                        }
                        catch (RuntimeException ignored) {
                            // One observer must not suppress later ordered observers.
                        }
                    }
                }
            });
        }
        catch (RuntimeException ignored) {
            // Closing an EDT executor can race an accepted batch; no state may be published after close.
        }
    }

    private void publishCanvasState(final CanvasState next, final long generation) {
        Objects.requireNonNull(next, "state");
        try {
            edt.execute(new Runnable() {
                @Override
                public void run() {
                    final List<CanvasStateListener> listeners;
                    synchronized (monitor) {
                        if (closed || generation != acceptedGeneration || next.generation() != projection.generation()
                                || next.generation() < state.generation()) {
                            return;
                        }
                        state = next;
                        listeners = new ArrayList<CanvasStateListener>(canvasListeners);
                    }
                    for (CanvasStateListener listener : listeners) {
                        synchronized (monitor) {
                            if (closed || generation != acceptedGeneration || state != next
                                    || projection != next.projection() || next.generation() != projection.generation()
                                    || next.generation() < state.generation()) {
                                return;
                            }
                        }
                        try {
                            listener.onCanvasState(next);
                        }
                        catch (RuntimeException ignored) {
                            // One observer must not suppress later ordered observers.
                        }
                    }
                }
            });
        }
        catch (RuntimeException ignored) {
            // Closing an EDT executor can race a publication; stale state is discarded.
        }
    }

    private void publishFailure(final long generation) {
        final CanvasState failed;
        synchronized (monitor) {
            if (closed || generation != acceptedGeneration) {
                return;
            }
            if (state.generation() == projection.generation() && state.projection() == projection) {
                failed = state.withStatus(OperationalStatus.FAILED);
            }
            else {
                failed = CanvasState.of(projection.generation(), projection, state.layout(), state.geometry(),
                    OperationalStatus.FAILED);
            }
        }
        publishCanvasState(failed, generation);
    }

    private void removeCanvasListener(final CanvasStateListener listener) {
        synchronized (monitor) {
            canvasListeners.remove(listener);
        }
    }

    private void removeProjectionListener(final GraphProjectionListener listener) {
        synchronized (monitor) {
            projectionListeners.remove(listener);
        }
    }

    private void requireOpenLocked() {
        if (closed) {
            throw new IllegalStateException("Graph update coordinator is closed");
        }
    }

    private static GraphProjection emptyProjection() {
        return EMPTY_PROJECTION;
    }

    interface RebuildPipeline {
        GraphProjection rebuild(AcceptedBatch batch, GraphProjection previous);
    }

    private static final class LivePipeline implements RebuildPipeline {
        private final WorkspaceMapCoordinator maps;
        private final ProjectionEngine projectionEngine;

        private LivePipeline(final WorkspaceMapCoordinator maps, final ProjectionEngine projectionEngine) {
            this.maps = maps;
            this.projectionEngine = projectionEngine;
        }

        @Override
        public GraphProjection rebuild(final AcceptedBatch batch, final GraphProjection previous) {
            final ProjectionInput input = maps.capture(batch);
            return projectionEngine.project(input);
        }
    }

    private static final class ListenerRemoval implements ListenerRegistration {
        private final Runnable removal;
        private boolean closed;

        private ListenerRemoval(final Runnable removal) {
            this.removal = removal;
        }

        @Override
        public synchronized void close() {
            if (!closed) {
                closed = true;
                removal.run();
            }
        }
    }

    private static final class SwingEdtExecutor implements EdtExecutor {
        @Override
        public <T> T call(final java.util.concurrent.Callable<T> task) {
            Objects.requireNonNull(task, "task");
            if (isEdt()) {
                try {
                    return task.call();
                }
                catch (Exception failure) {
                    throw new IllegalStateException("EDT task failed", failure);
                }
            }
            throw new UnsupportedOperationException("Graph updates use asynchronous EDT publication");
        }

        @Override
        public void execute(final Runnable task) {
            Objects.requireNonNull(task, "task");
            if (isEdt()) {
                task.run();
            }
            else {
                javax.swing.SwingUtilities.invokeLater(task);
            }
        }

        @Override
        public boolean isEdt() {
            return javax.swing.SwingUtilities.isEventDispatchThread();
        }
    }
}
