package org.freeplane.plugin.graph.control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.freeplane.plugin.graph.adapter.EdtExecutor;
import org.freeplane.plugin.graph.geometry.GraphGeometry;
import org.freeplane.plugin.graph.geometry.LayoutPositions;
import org.freeplane.plugin.graph.layout.LayoutFrame;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.ProjectionDiff;
import org.freeplane.plugin.graph.projection.ProjectionEngine;
import org.freeplane.plugin.graph.projection.input.ProjectionInput;
import org.freeplane.plugin.graph.workspace.ListenerRegistration;

public final class GraphUpdateCoordinator implements AutoCloseable {
    private final Object monitor = new Object();
    private final RebuildPipeline pipeline;
    private final ProjectionBatcher batcher;
    private final LayoutSettleLoop settleLoop;
    private final EdtExecutor edt;
    private final List<CanvasStateListener> canvasListeners = new ArrayList<CanvasStateListener>();
    private final List<GraphProjectionListener> projectionListeners = new ArrayList<GraphProjectionListener>();

    private GraphProjection projection;
    private CanvasState state;
    private long acceptedGeneration = -1L;
    private boolean started;
    private boolean pending;
    private boolean closed;

    public GraphUpdateCoordinator(final WorkspaceMapCoordinator maps, final ProjectionEngine projectionEngine,
            final LayoutSettleLoop settleLoop) {
        this.pipeline = new LivePipeline(Objects.requireNonNull(maps, "maps"),
            Objects.requireNonNull(projectionEngine, "projectionEngine"));
        this.batcher = new ProjectionBatcher(this::acceptBatch);
        this.settleLoop = Objects.requireNonNull(settleLoop, "settleLoop");
        this.edt = new SwingEdtExecutor();
        initializeState();
    }

    GraphUpdateCoordinator(final RebuildPipeline pipeline, final ProjectionBatcher batcher,
            final LayoutSettleLoop settleLoop, final EdtExecutor edt) {
        this.pipeline = Objects.requireNonNull(pipeline, "pipeline");
        this.batcher = Objects.requireNonNull(batcher, "batcher");
        this.settleLoop = Objects.requireNonNull(settleLoop, "settleLoop");
        this.edt = Objects.requireNonNull(edt, "edt");
        initializeState();
    }

    private void initializeState() {
        this.projection = emptyProjection();
        final LayoutFrame initialLayout = LayoutFrame.of(0L,
            LayoutPositions.of(Collections.emptyMap(), Collections.emptyMap()), false);
        this.state = CanvasState.of(0L, projection, initialLayout,
            GraphGeometry.of(Collections.emptyMap(), Collections.emptyMap()), OperationalStatus.LOADING);
    }


    public void start() {
        synchronized (monitor) {
            requireOpenLocked();
            if (started) {
                return;
            }
            started = true;
        }
        publishCanvasState(state);
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
        restartLayout();
    }

    @Override
    public void close() {
        synchronized (monitor) {
            if (closed) {
                return;
            }
            closed = true;
            pending = false;
            canvasListeners.clear();
            projectionListeners.clear();
        }
        batcher.close();
        settleLoop.close();
    }

    void acceptBatch(final AcceptedBatch batch) {
        acceptBatchInternal(Objects.requireNonNull(batch, "batch"));
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
            publishFailure();
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
        publishProjection(next);
        try {
            settleLoop.start(batch, next, diff, this::publishCanvasState);
        }
        catch (RuntimeException failure) {
            publishFailure();
        }
    }

    private void publishProjection(final GraphProjection next) {
        try {
            edt.execute(new Runnable() {
                @Override
                public void run() {
                    synchronized (monitor) {
                        if (closed || projection != next) {
                            return;
                        }
                        for (GraphProjectionListener listener
                                : new ArrayList<GraphProjectionListener>(projectionListeners)) {
                            if (closed) {
                                return;
                            }
                            listener.onGraphProjection(next);
                        }
                    }
                }
            });
        }
        catch (RuntimeException ignored) {
            // Closing an EDT executor can race an accepted batch; no state may be published after close.
        }
    }

    private void publishCanvasState(final CanvasState next) {
        Objects.requireNonNull(next, "state");
        try {
            edt.execute(new Runnable() {
                @Override
                public void run() {
                    synchronized (monitor) {
                        if (closed || next.generation() != projection.generation()
                                || next.generation() < state.generation()) {
                            return;
                        }
                        state = next;
                        for (CanvasStateListener listener
                                : new ArrayList<CanvasStateListener>(canvasListeners)) {
                            if (closed) {
                                return;
                            }
                            listener.onCanvasState(next);
                        }
                    }
                }
            });
        }
        catch (RuntimeException ignored) {
            // Closing an EDT executor can race a publication; stale state is discarded.
        }
    }

    private void publishFailure() {
        final CanvasState retained;
        synchronized (monitor) {
            if (closed) {
                return;
            }
            retained = state.withStatus(OperationalStatus.FAILED);
        }
        publishCanvasState(retained);
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
        return GraphProjection.projected(0L, Collections.emptyList(), Collections.emptyList(),
            Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
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
