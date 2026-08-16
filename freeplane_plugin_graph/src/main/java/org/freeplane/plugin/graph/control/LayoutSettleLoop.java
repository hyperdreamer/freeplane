package org.freeplane.plugin.graph.control;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.swing.SwingUtilities;

import org.freeplane.plugin.graph.adapter.EdtExecutor;
import org.freeplane.plugin.graph.geometry.AwtGeometryTextMetrics;
import org.freeplane.plugin.graph.geometry.GeometryTextMetrics;
import org.freeplane.plugin.graph.geometry.GraphGeometry;
import org.freeplane.plugin.graph.geometry.GraphGeometryEngine;
import org.freeplane.plugin.graph.layout.LayoutCalibration;
import org.freeplane.plugin.graph.layout.LayoutFrame;
import org.freeplane.plugin.graph.layout.LayoutRequest;
import org.freeplane.plugin.graph.layout.LayoutWorker;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.ProjectionDiff;
import org.freeplane.plugin.graph.workspace.model.WorkspaceId;

public final class LayoutSettleLoop implements AutoCloseable {
    private final Object monitor = new Object();
    private final WorkspaceId workspace;
    private final FrameStepper worker;
    private final GraphGeometryEngine geometryEngine;
    private final GeometryTextMetrics metrics;
    private final EdtExecutor edt;
    private final LabelAssembler labels;

    private Run currentRun;
    private CanvasState lastState;
    private long token;
    private boolean paused;
    private boolean closed;

    public LayoutSettleLoop(final WorkspaceId workspace) {
        this(workspace, new WorkerStepper(new LayoutWorker(LayoutCalibration.spikeDefaults())),
            new GraphGeometryEngine(), defaultMetrics(), new SwingEdtExecutor());
    }

    LayoutSettleLoop(final WorkspaceId workspace, final FrameStepper worker,
            final GraphGeometryEngine geometryEngine, final EdtExecutor edt) {
        this(workspace, worker, geometryEngine, defaultMetrics(), edt);
    }

    LayoutSettleLoop(final WorkspaceId workspace, final FrameStepper worker,
            final GraphGeometryEngine geometryEngine, final GeometryTextMetrics metrics, final EdtExecutor edt) {
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.worker = Objects.requireNonNull(worker, "worker");
        this.geometryEngine = Objects.requireNonNull(geometryEngine, "geometryEngine");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.edt = Objects.requireNonNull(edt, "edt");
        this.labels = new LabelAssembler();
    }

    public CompletionStage<Void> start(final AcceptedBatch batch, final GraphProjection projection,
            final ProjectionDiff diff, final CanvasStateListener listener) {
        final AcceptedBatch accepted = Objects.requireNonNull(batch, "batch");
        final GraphProjection value = Objects.requireNonNull(projection, "projection");
        final ProjectionDiff change = Objects.requireNonNull(diff, "diff");
        final CanvasStateListener callback = Objects.requireNonNull(listener, "listener");
        if (accepted.generation() != value.generation()) {
            throw new IllegalArgumentException("Batch and projection generations must match");
        }
        final LayoutRequest request = LayoutRequest.of(workspace, value, change, value.pins());
        final Run run;
        synchronized (monitor) {
            requireOpenLocked();
            token++;
            if (currentRun != null) {
                currentRun.result.complete(null);
            }
            paused = false;
            run = new Run(token, accepted, value, callback, request);
            currentRun = run;
        }
        CompletionStage<LayoutFrame> submitted;
        try {
            submitted = worker.submit(request);
        }
        catch (RuntimeException failure) {
            fail(run);
            return run.result;
        }
        submitted.whenComplete((frame, failure) -> handleFrame(run, frame, failure));
        return run.result;
    }

    public void pause() {
        synchronized (monitor) {
            if (closed) {
                return;
            }
            paused = true;
        }
        worker.pause();
    }

    public void restart() {
        final Run run;
        synchronized (monitor) {
            if (closed) {
                return;
            }
            paused = false;
            run = currentRun;
        }
        worker.restart();
        if (run != null && !run.result.isDone()) {
            requestStep(run);
        }
    }

    @Override
    public void close() {
        synchronized (monitor) {
            if (closed) {
                return;
            }
            closed = true;
            token++;
            if (currentRun != null) {
                currentRun.result.complete(null);
                currentRun = null;
            }
        }
        worker.close();
    }

    private void handleFrame(final Run run, final LayoutFrame frame, final Throwable failure) {
        if (!isCurrent(run)) {
            run.result.complete(null);
            return;
        }
        if (failure != null || frame == null || frame.failed()) {
            fail(run);
            return;
        }
        final GraphGeometry geometry;
        try {
            geometry = labels.place(run.projection, geometryEngine.computeHulls(run.projection, frame.positions()), metrics);
        }
        catch (RuntimeException exception) {
            fail(run);
            return;
        }
        final OperationalStatus status = frame.idle().idle() ? OperationalStatus.IDLE : OperationalStatus.SETTLING;
        final CanvasState state = CanvasState.of(run.batch.generation(), run.projection, frame, geometry, status);
        publish(run, state, frame.idle().idle());
    }

    private void publish(final Run run, final CanvasState state, final boolean idle) {
        final CompletableFuture<Void> published = new CompletableFuture<Void>();
        try {
            edt.execute(new Runnable() {
                @Override
                public void run() {
                    if (!isCurrent(run)) {
                        published.complete(null);
                        return;
                    }
                    try {
                        synchronized (monitor) {
                            if (closed || currentRun != run || token != run.token) {
                                published.complete(null);
                                return;
                            }
                            lastState = state;
                            run.listener.onCanvasState(state);
                        }
                        published.complete(null);
                    }
                    catch (RuntimeException failure) {
                        published.completeExceptionally(failure);
                    }
                }
            });
        }
        catch (RuntimeException failure) {
            published.completeExceptionally(failure);
        }
        published.whenComplete((ignored, failure) -> {
            if (!isCurrent(run)) {
                run.result.complete(null);
            }
            else if (failure != null) {
                fail(run);
            }
            else if (idle) {
                run.result.complete(null);
            }
            else {
                boolean shouldStep;
                synchronized (monitor) {
                    shouldStep = !paused && !closed;
                }
                if (shouldStep) {
                    requestStep(run);
                }
            }
        });
    }

    private void requestStep(final Run run) {
        if (!isCurrent(run)) {
            run.result.complete(null);
            return;
        }
        CompletionStage<LayoutFrame> next;
        try {
            next = worker.step();
        }
        catch (RuntimeException failure) {
            fail(run);
            return;
        }
        next.whenComplete((frame, failure) -> handleFrame(run, frame, failure));
    }

    private void fail(final Run run) {
        if (!isCurrent(run)) {
            run.result.complete(null);
            return;
        }
        final CanvasState retained;
        synchronized (monitor) {
            retained = lastState;
        }
        if (retained == null) {
            run.result.complete(null);
            return;
        }
        publish(run, retained.withStatus(OperationalStatus.FAILED), true);
    }

    private boolean isCurrent(final Run run) {
        synchronized (monitor) {
            return !closed && currentRun == run && token == run.token;
        }
    }

    private void requireOpenLocked() {
        if (closed) {
            throw new IllegalStateException("Layout settle loop is closed");
        }
    }

    private static GeometryTextMetrics defaultMetrics() {
        return new AwtGeometryTextMetrics(new Font("Dialog", Font.PLAIN, 12),
            new FontRenderContext(null, true, true));
    }

    interface FrameStepper {
        CompletionStage<LayoutFrame> submit(LayoutRequest request);
        CompletionStage<LayoutFrame> step();
        void pause();
        void restart();
        LayoutFrame lastValidFrame();
        void close();
    }

    private static final class LabelAssembler {
        private final org.freeplane.plugin.graph.geometry.LabelPlacementEngine engine =
            new org.freeplane.plugin.graph.geometry.LabelPlacementEngine();

        private GraphGeometry place(final GraphProjection projection, final GraphGeometry geometry,
                final GeometryTextMetrics metrics) {
            return engine.place(projection, geometry, metrics);
        }

        private GraphGeometry place(final GraphProjection projection, final GraphGeometry geometry) {
            return place(projection, geometry, defaultMetrics());
        }
    }

    private static final class Run {
        private final long token;
        private final AcceptedBatch batch;
        private final GraphProjection projection;
        private final CanvasStateListener listener;
        private final LayoutRequest request;
        private final CompletableFuture<Void> result = new CompletableFuture<Void>();

        private Run(final long token, final AcceptedBatch batch, final GraphProjection projection,
                final CanvasStateListener listener, final LayoutRequest request) {
            this.token = token;
            this.batch = batch;
            this.projection = projection;
            this.listener = listener;
            this.request = request;
        }
    }

    private static final class WorkerStepper implements FrameStepper {
        private final LayoutWorker worker;

        private WorkerStepper(final LayoutWorker worker) {
            this.worker = worker;
        }

        @Override
        public CompletionStage<LayoutFrame> submit(final LayoutRequest request) {
            return worker.submit(request);
        }

        @Override
        public CompletionStage<LayoutFrame> step() {
            return worker.step();
        }

        @Override
        public void pause() {
            worker.pause();
        }

        @Override
        public void restart() {
            worker.restart();
        }

        @Override
        public LayoutFrame lastValidFrame() {
            return worker.lastValidFrame();
        }

        @Override
        public void close() {
            worker.close();
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
            throw new UnsupportedOperationException("Layout settle loop only executes asynchronous EDT work");
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
