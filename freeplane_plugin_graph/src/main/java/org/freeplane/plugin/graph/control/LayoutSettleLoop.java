package org.freeplane.plugin.graph.control;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.SwingUtilities;

import org.freeplane.plugin.graph.adapter.EdtExecutor;
import org.freeplane.plugin.graph.geometry.AwtGeometryTextMetrics;
import org.freeplane.plugin.graph.geometry.GeometryTextMetrics;
import org.freeplane.plugin.graph.geometry.GraphGeometry;
import org.freeplane.plugin.graph.geometry.GraphGeometryEngine;
import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.geometry.LayoutPositions;
import org.freeplane.plugin.graph.layout.LayoutCalibration;
import org.freeplane.plugin.graph.layout.LayoutConflict;
import org.freeplane.plugin.graph.layout.LayoutFrame;
import org.freeplane.plugin.graph.layout.LayoutRequest;
import org.freeplane.plugin.graph.layout.LayoutWorker;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.ProjectedEnclosure;
import org.freeplane.plugin.graph.projection.ProjectedNode;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.freeplane.plugin.graph.projection.ProjectionDiff;
import org.freeplane.plugin.graph.workspace.model.WorkspaceId;

public final class LayoutSettleLoop implements AutoCloseable {
    private static final AtomicInteger CONTINUATION_IDS = new AtomicInteger();
    private final Object monitor = new Object();
    private final WorkspaceId workspace;
    private final FrameStepper worker;
    private final GraphGeometryEngine geometryEngine;
    private final GeometryTextMetrics metrics;
    private final EdtExecutor edt;
    private final LabelAssembler labels;
    private final ExecutorService continuationExecutor = createContinuationExecutor();

    private Run currentRun;
    private long token;
    private long highestAcceptedGeneration = Long.MIN_VALUE;
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
            if (accepted.generation() <= highestAcceptedGeneration) {
                return currentRun == null ? CompletableFuture.completedFuture(null) : currentRun.result;
            }
            highestAcceptedGeneration = accepted.generation();
            invalidateCurrentLocked();
            paused = false;
            run = new Run(++token, accepted, value, callback, request);
            run.frameInFlight = true;
            currentRun = run;
        }
        submit(run);
        return run.result;
    }

    public void pause() {
        synchronized (monitor) {
            if (closed) {
                return;
            }
            paused = true;
            if (currentRun != null && (currentRun.frameInFlight || currentRun.publicationInFlight)) {
                currentRun.discardOnPause = true;
            }
        }
        worker.pause();
    }

    public void restart() {
        final Run run;
        final boolean shouldStep;
        synchronized (monitor) {
            if (closed) {
                return;
            }
            final boolean wasPaused = paused;
            paused = false;
            run = currentRun;
            shouldStep = run != null && isCurrentLocked(run) && !run.result.isDone()
                && !run.frameInFlight && !run.publicationInFlight;
            if (shouldStep) {
                run.restartRequested = false;
                run.frameInFlight = true;
            }
            else if (wasPaused && run != null && isCurrentLocked(run) && !run.result.isDone()) {
                run.restartRequested = true;
            }
        }
        try {
            worker.restart();
        }
        catch (RuntimeException failure) {
            if (shouldStep) {
                dispatchFrameFailure(run, failure);
                return;
            }
            throw failure;
        }
        if (shouldStep) {
            requestClaimedStep(run);
        }
    }

    void reset() {
        final Run run;
        synchronized (monitor) {
            if (closed || currentRun == null) {
                return;
            }
            final Run previous = currentRun;
            final LayoutRequest request = LayoutRequest.of(workspace, previous.projection,
                previous.request.diff(), previous.request.pins());
            invalidateCurrentLocked();
            paused = false;
            run = new Run(++token, previous.batch, previous.projection, previous.listener, request);
            run.frameInFlight = true;
            currentRun = run;
        }
        try {
            worker.restart();
        }
        catch (RuntimeException failure) {
            dispatchFrameFailure(run, failure);
            return;
        }
        submit(run);
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
        try {
            worker.close();
        }
        finally {
            continuationExecutor.shutdown();
        }
    }

    private void handleFrame(final Run run, final LayoutFrame frame, final Throwable failure) {
        final boolean resume;
        final boolean discarded;
        synchronized (monitor) {
            if (!isCurrentLocked(run)) {
                run.frameInFlight = false;
                run.result.complete(null);
                return;
            }
            if (run.discardOnPause || paused) {
                run.frameInFlight = false;
                run.discardOnPause = false;
                resume = resumeAfterDiscardLocked(run);
                discarded = true;
            }
            else {
                resume = false;
                discarded = false;
            }
        }
        if (resume) {
            requestClaimedStep(run);
            return;
        }
        if (discarded) {
            return;
        }
        if (failure != null || frame == null || frame.failed()) {
            fail(run, frame);
            return;
        }
        try {
            final GraphGeometry geometry = labels.place(run.projection,
                geometryEngine.computeHulls(run.projection, frame.positions()), metrics);
            final OperationalStatus status = isEmpty(run.projection)
                ? OperationalStatus.EMPTY
                : frame.idle().idle() ? OperationalStatus.IDLE : OperationalStatus.SETTLING;
            final CanvasState state = CanvasState.of(run.batch.generation(), run.projection, frame, geometry, status);
            publish(run, state, frame.idle().idle());
        }
        catch (RuntimeException exception) {
            fail(run, frame);
        }
    }

    private void publish(final Run run, final CanvasState state, final boolean idle) {
        final CompletableFuture<Void> published = new CompletableFuture<Void>();
        final boolean resume;
        final boolean publishOnEdt;
        synchronized (monitor) {
            if (!isCurrentLocked(run)) {
                run.frameInFlight = false;
                run.result.complete(null);
                resume = false;
                publishOnEdt = false;
            }
            else if (paused || run.discardOnPause) {
                run.frameInFlight = false;
                run.discardOnPause = false;
                resume = resumeAfterDiscardLocked(run);
                publishOnEdt = false;
            }
            else {
                resume = false;
                run.frameInFlight = false;
                run.publicationInFlight = true;
                publishOnEdt = true;
            }
        }
        if (resume) {
            requestClaimedStep(run);
            return;
        }
        if (!publishOnEdt) {
            return;
        }
        try {
            published.whenCompleteAsync((ignored, failure) -> finishPublication(run, idle, failure),
                continuationExecutor);
        }
        catch (RejectedExecutionException rejected) {
            synchronized (monitor) {
                run.publicationInFlight = false;
                run.result.complete(null);
            }
            return;
        }
        try {
            edt.execute(new Runnable() {
                @Override
                public void run() {
                    synchronized (monitor) {
                        if (!isCurrentLocked(run) || paused || run.discardOnPause) {
                            published.complete(null);
                            return;
                        }
                    }
                    try {
                        run.listener.onCanvasState(state);
                    }
                    catch (RuntimeException ignored) {
                        // Listener failures must not terminate the settling loop.
                    }
                    published.complete(null);
                }
            });
        }
        catch (RuntimeException failure) {
            published.completeExceptionally(failure);
        }
    }

    private void finishPublication(final Run run, final boolean idle, final Throwable failure) {
        final boolean shouldStep;
        synchronized (monitor) {
            run.publicationInFlight = false;
            if (!isCurrentLocked(run)) {
                run.result.complete(null);
                return;
            }
            if (failure != null) {
                run.result.complete(null);
                return;
            }
            if (run.discardOnPause) {
                run.discardOnPause = false;
                shouldStep = resumeAfterDiscardLocked(run);
                if (!shouldStep) {
                    return;
                }
            }
            else if (paused) {
                return;
            }
            else if (idle) {
                run.result.complete(null);
                return;
            }
            else {
                shouldStep = !run.frameInFlight;
                if (shouldStep) {
                    run.frameInFlight = true;
                }
            }
        }
        if (shouldStep) {
            requestClaimedStep(run);
        }
    }

    private void submit(final Run run) {
        synchronized (monitor) {
            if (!isCurrentLocked(run)) {
                run.frameInFlight = false;
                run.result.complete(null);
                return;
            }
        }
        try {
            final CompletionStage<LayoutFrame> submitted = Objects.requireNonNull(worker.submit(run.request),
                "worker submit result");
            submitted.whenCompleteAsync((frame, failure) -> handleFrame(run, frame, failure),
                continuationExecutor);
        }
        catch (RuntimeException failure) {
            dispatchFrameFailure(run, failure);
        }
    }

    private void requestClaimedStep(final Run run) {
        synchronized (monitor) {
            if (!isCurrentLocked(run) || paused || run.discardOnPause) {
                run.frameInFlight = false;
                if (isCurrentLocked(run) && (paused || run.discardOnPause)) {
                    run.restartRequested = true;
                }
                run.discardOnPause = false;
                if (!isCurrentLocked(run)) {
                    run.result.complete(null);
                }
                return;
            }
        }
        try {
            final CompletionStage<LayoutFrame> next = Objects.requireNonNull(worker.step(),
                "worker step result");
            next.whenCompleteAsync((frame, failure) -> handleFrame(run, frame, failure),
                continuationExecutor);
        }
        catch (RuntimeException failure) {
            dispatchFrameFailure(run, failure);
        }
    }

    private void dispatchFrameFailure(final Run run, final Throwable failure) {
        try {
            continuationExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    handleFrame(run, null, failure);
                }
            });
        }
        catch (RejectedExecutionException rejected) {
            run.result.complete(null);
        }
    }

    private void fail(final Run run, final LayoutFrame source) {
        final boolean resume;
        final boolean discarded;
        synchronized (monitor) {
            if (!isCurrentLocked(run)) {
                run.frameInFlight = false;
                run.result.complete(null);
                return;
            }
            if (paused || run.discardOnPause) {
                run.frameInFlight = false;
                run.discardOnPause = false;
                resume = resumeAfterDiscardLocked(run);
                discarded = true;
            }
            else {
                resume = false;
                discarded = false;
            }
        }
        if (resume) {
            requestClaimedStep(run);
            return;
        }
        if (discarded) {
            return;
        }
        final LayoutFrame failed = failedFrame(run, source);
        try {
            final GraphGeometry geometry = labels.place(run.projection,
                geometryEngine.computeHulls(run.projection, failed.positions()), metrics);
            final CanvasState state = CanvasState.of(run.batch.generation(), run.projection, failed, geometry,
                OperationalStatus.FAILED);
            publish(run, state, true);
        }
        catch (RuntimeException exception) {
            synchronized (monitor) {
                run.frameInFlight = false;
                run.result.complete(null);
            }
        }
    }

    private LayoutFrame failedFrame(final Run run, final LayoutFrame source) {
        LayoutFrame retained = null;
        try {
            retained = worker.lastValidFrame();
        }
        catch (RuntimeException ignored) {
            // A failed worker may not have a readable retained frame.
        }
        final boolean retainedUsable = retained != null && covers(run.projection, retained.positions());
        final LayoutPositions positions = retainedUsable ? retained.positions() : fallbackPositions(run.projection);
        final long index = source != null ? source.stepIndex() : retained == null ? 0L : retained.stepIndex();
        if (retained == null) {
            return LayoutFrame.of(index, positions, true);
        }
        final List<LayoutConflict> conflicts = retained.conflicts();
        return LayoutFrame.withDiagnostics(LayoutFrame.of(index, positions, true), conflicts, retained.idle());
    }

    private static boolean covers(final GraphProjection projection, final LayoutPositions positions) {
        if (positions == null || positions.nodes().size() != projection.nodes().size()
                || positions.anchors().size() != projection.enclosures().size()) {
            return false;
        }
        for (ProjectedNode node : projection.nodes()) {
            if (!positions.nodes().containsKey(node.key())) {
                return false;
            }
        }
        for (ProjectedEnclosure enclosure : projection.enclosures()) {
            if (!positions.anchors().containsKey(enclosure.hullKey())) {
                return false;
            }
        }
        return true;
    }

    private static LayoutPositions fallbackPositions(final GraphProjection projection) {
        final Map<ProjectedNodeKey, LayoutPoint> nodes = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        long slot = 0L;
        for (ProjectedNode node : projection.nodes()) {
            nodes.put(node.key(), fallbackPoint(slot++));
        }
        final Map<EnclosureHullKey, LayoutPoint> anchors = new LinkedHashMap<EnclosureHullKey, LayoutPoint>();
        for (ProjectedEnclosure enclosure : projection.enclosures()) {
            anchors.put(enclosure.hullKey(), fallbackPoint(slot++));
        }
        return LayoutPositions.of(nodes, anchors);
    }

    private static LayoutPoint fallbackPoint(final long slot) {
        final double x = (slot % 32L) * 64.0;
        final double y = (slot / 32L) * 64.0;
        return LayoutPoint.of(x, y);
    }

    private static boolean isEmpty(final GraphProjection projection) {
        return projection.nodes().isEmpty() && projection.enclosures().isEmpty() && projection.edges().isEmpty();
    }

    private boolean isCurrentLocked(final Run run) {
        return !closed && currentRun == run && token == run.token;
    }

    private boolean resumeAfterDiscardLocked(final Run run) {
        if (!isCurrentLocked(run) || paused || run.result.isDone() || !run.restartRequested
                || run.frameInFlight || run.publicationInFlight) {
            return false;
        }
        run.restartRequested = false;
        run.frameInFlight = true;
        return true;
    }

    private void invalidateCurrentLocked() {
        if (currentRun != null) {
            currentRun.result.complete(null);
            currentRun = null;
        }
    }

    private void requireOpenLocked() {
        if (closed) {
            throw new IllegalStateException("Layout settle loop is closed");
        }
    }

    private static ExecutorService createContinuationExecutor() {
        final int id = CONTINUATION_IDS.incrementAndGet();
        return Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(final Runnable command) {
                final Thread thread = new Thread(command, "freeplane-graph-layout-continuation-" + id);
                thread.setDaemon(true);
                return thread;
            }
        });
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
        private boolean frameInFlight;
        private boolean publicationInFlight;
        private boolean discardOnPause;
        private boolean restartRequested;

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
