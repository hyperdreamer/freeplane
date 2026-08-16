package org.freeplane.plugin.graph.control;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
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
    private static final AtomicInteger LIFECYCLE_IDS = new AtomicInteger();

    private final Object monitor = new Object();
    private final WorkspaceId workspace;
    private final FrameStepper worker;
    private final GraphGeometryEngine geometryEngine;
    private final GeometryTextMetrics metrics;
    private final EdtExecutor edt;
    private final LabelAssembler labels;
    private final LifecycleDispatcher lifecycle;

    private Run currentRun;
    private CompletableFuture<Void> physicalClose;
    private long token;
    private long controlRevision;
    private long highestAcceptedGeneration = Long.MIN_VALUE;
    private boolean paused;
    private boolean closed;

    public LayoutSettleLoop(final WorkspaceId workspace) {
        this(workspace, new WorkerStepper(LayoutCalibration.spikeDefaults()), new GraphGeometryEngine(), defaultMetrics(),
            new SwingEdtExecutor(), createLifecycleDispatcher());
    }

    LayoutSettleLoop(final WorkspaceId workspace, final FrameStepper worker,
            final GraphGeometryEngine geometryEngine, final EdtExecutor edt) {
        this(workspace, worker, geometryEngine, defaultMetrics(), edt, createLifecycleDispatcher());
    }

    LayoutSettleLoop(final WorkspaceId workspace, final FrameStepper worker,
            final GraphGeometryEngine geometryEngine, final GeometryTextMetrics metrics, final EdtExecutor edt) {
        this(workspace, worker, geometryEngine, metrics, edt, createLifecycleDispatcher());
    }

    LayoutSettleLoop(final WorkspaceId workspace, final FrameStepper worker,
            final GraphGeometryEngine geometryEngine, final EdtExecutor edt, final LifecycleDispatcher lifecycle) {
        this(workspace, worker, geometryEngine, defaultMetrics(), edt, lifecycle);
    }

    LayoutSettleLoop(final WorkspaceId workspace, final FrameStepper worker,
            final GraphGeometryEngine geometryEngine, final GeometryTextMetrics metrics, final EdtExecutor edt,
            final LifecycleDispatcher lifecycle) {
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.worker = Objects.requireNonNull(worker, "worker");
        this.geometryEngine = Objects.requireNonNull(geometryEngine, "geometryEngine");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.edt = Objects.requireNonNull(edt, "edt");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
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
        final List<CompletableFuture<Void>> completed = new ArrayList<CompletableFuture<Void>>();
        final Run run;
        final long revision;
        synchronized (monitor) {
            requireOpenLocked();
            if (accepted.generation() <= highestAcceptedGeneration) {
                return currentRun == null ? CompletableFuture.completedFuture(null) : currentRun.result;
            }
            highestAcceptedGeneration = accepted.generation();
            revision = ++controlRevision;
            terminalizeCurrentLocked(completed);
            paused = false;
            run = new Run(++token, accepted, value, callback, request);
            claimFrameLocked(run, revision);
            currentRun = run;
        }
        completeRuns(completed);
        queueStart(run, revision);
        return run.result;
    }

    public void pause() {
        final Run run;
        final long revision;
        synchronized (monitor) {
            if (closed) {
                return;
            }
            revision = ++controlRevision;
            paused = true;
            run = currentRun;
            if (isLiveLocked(run) && (run.frameInFlight || run.publicationInFlight)) {
                run.discardOnPause = true;
            }
        }
        if (run != null) {
            queuePause(run, revision);
        }
    }

    public void restart() {
        final Run run;
        final long revision;
        final boolean shouldStep;
        synchronized (monitor) {
            if (closed) {
                return;
            }
            revision = ++controlRevision;
            final boolean wasPaused = paused;
            paused = false;
            run = currentRun;
            shouldStep = isLiveLocked(run) && !run.frameInFlight && !run.publicationInFlight;
            if (shouldStep) {
                run.discardOnPause = false;
                run.restartRequested = false;
                claimFrameLocked(run, revision);
            }
            else if (wasPaused && isLiveLocked(run)) {
                run.restartRequested = true;
            }
        }
        if (run != null) {
            queueRestart(run, revision, shouldStep);
        }
    }

    void reset() {
        final List<CompletableFuture<Void>> completed = new ArrayList<CompletableFuture<Void>>();
        final Run run;
        final long revision;
        synchronized (monitor) {
            if (closed || currentRun == null) {
                return;
            }
            final Run previous = currentRun;
            final LayoutRequest request = LayoutRequest.of(workspace, previous.projection,
                previous.request.diff(), previous.request.pins());
            revision = ++controlRevision;
            terminalizeLocked(previous, completed);
            paused = false;
            run = new Run(++token, previous.batch, previous.projection, previous.listener, request);
            claimFrameLocked(run, revision);
            currentRun = run;
        }
        completeRuns(completed);
        queueReset(run, revision);
    }

    @Override
    public void close() {
        final boolean asynchronousCaller = edt.isEdt() || lifecycle.isLifecycleThread();
        final List<CompletableFuture<Void>> completed = new ArrayList<CompletableFuture<Void>>();
        final CompletableFuture<Void> closeFuture;
        final boolean queueClose;
        synchronized (monitor) {
            if (closed) {
                closeFuture = physicalClose;
                queueClose = false;
            }
            else {
                closed = true;
                ++controlRevision;
                terminalizeCurrentLocked(completed);
                currentRun = null;
                closeFuture = new CompletableFuture<Void>();
                physicalClose = closeFuture;
                queueClose = true;
            }
        }
        completeRuns(completed);
        if (queueClose) {
            try {
                lifecycle.execute(new Runnable() {
                    @Override
                    public void run() {
                        closeWorker(closeFuture);
                    }
                });
            }
            catch (RuntimeException failure) {
                closeFuture.completeExceptionally(failure);
            }
        }
        if (!asynchronousCaller && closeFuture != null) {
            awaitClose(closeFuture);
        }
    }

    private void queueStart(final Run run, final long revision) {
        queueLifecycle(new Runnable() {
            @Override
            public void run() {
                runStart(run, revision);
            }
        });
    }

    private void runStart(final Run run, final long revision) {
        if (!claimIsCurrentAndRunning(run, revision)) {
            return;
        }
        try {
            worker.restart();
        }
        catch (RuntimeException failure) {
            handleFrame(run, null, failure);
            return;
        }
        if (!claimIsCurrentAndRunning(run, revision)) {
            return;
        }
        submitClaimed(run, revision);
    }

    private void queuePause(final Run run, final long revision) {
        queueLifecycle(new Runnable() {
            @Override
            public void run() {
                if (!isCurrentAndPaused(run, revision)) {
                    return;
                }
                try {
                    worker.pause();
                }
                catch (RuntimeException failure) {
                    handleFrame(run, null, failure);
                }
            }
        });
    }

    private void queueRestart(final Run run, final long revision, final boolean shouldStep) {
        queueLifecycle(new Runnable() {
            @Override
            public void run() {
                if (!isCurrentAndRunning(run, revision)) {
                    if (shouldStep) {
                        releaseClaim(run);
                    }
                    return;
                }
                try {
                    worker.restart();
                }
                catch (RuntimeException failure) {
                    if (shouldStep) {
                        handleFrame(run, null, failure);
                    }
                    return;
                }
                if (shouldStep) {
                    if (hasSubmittedRequest(run)) {
                        stepClaimed(run, revision);
                    }
                    else {
                        submitClaimed(run, revision);
                    }
                }
            }
        });
    }

    private void queueReset(final Run run, final long revision) {
        queueLifecycle(new Runnable() {
            @Override
            public void run() {
                if (!claimIsCurrentAndRunning(run, revision)) {
                    return;
                }
                try {
                    worker.reset();
                }
                catch (RuntimeException failure) {
                    handleFrame(run, null, failure);
                    return;
                }
                if (!claimIsCurrentAndRunning(run, revision)) {
                    return;
                }
                try {
                    worker.restart();
                }
                catch (RuntimeException failure) {
                    handleFrame(run, null, failure);
                    return;
                }
                if (!claimIsCurrentAndRunning(run, revision)) {
                    return;
                }
                submitClaimed(run, revision);
            }
        });
    }

    private void submitClaimed(final Run run, final long revision) {
        if (!claimIsCurrentAndRunning(run, revision)) {
            return;
        }
        try {
            final CompletionStage<LayoutFrame> submitted = Objects.requireNonNull(worker.submit(run.request),
                "worker submit result");
            recordSubmittedRequest(run);
            submitted.whenCompleteAsync((frame, failure) -> handleFrame(run, frame, failure), lifecycle);
        }
        catch (RuntimeException failure) {
            handleFrame(run, null, failure);
        }
    }

    private void stepClaimed(final Run run, final long revision) {
        if (!claimIsCurrentAndRunning(run, revision)) {
            return;
        }
        try {
            final CompletionStage<LayoutFrame> next = Objects.requireNonNull(worker.step(), "worker step result");
            next.whenCompleteAsync((frame, failure) -> handleFrame(run, frame, failure), lifecycle);
        }
        catch (RuntimeException failure) {
            handleFrame(run, null, failure);
        }
    }

    private void handleFrame(final Run run, final LayoutFrame frame, final Throwable failure) {
        final List<CompletableFuture<Void>> completed = new ArrayList<CompletableFuture<Void>>();
        boolean resume = false;
        boolean discarded = false;
        long resumeRevision = 0L;
        synchronized (monitor) {
            if (!isLiveLocked(run)) {
                run.frameInFlight = false;
                terminalizeLocked(run, completed);
            }
            else if (run.discardOnPause || paused) {
                run.frameInFlight = false;
                run.discardOnPause = false;
                resume = resumeAfterDiscardLocked(run);
                if (resume) {
                    resumeRevision = run.claimRevision;
                }
                discarded = true;
            }
        }
        completeRuns(completed);
        if (resume) {
            queueStep(run, resumeRevision);
            return;
        }
        if (discarded || !isLive(run)) {
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
        final List<CompletableFuture<Void>> completed = new ArrayList<CompletableFuture<Void>>();
        boolean resume = false;
        boolean publishOnEdt = false;
        long resumeRevision = 0L;
        synchronized (monitor) {
            if (!isLiveLocked(run)) {
                run.frameInFlight = false;
                terminalizeLocked(run, completed);
            }
            else if (paused || run.discardOnPause) {
                run.frameInFlight = false;
                run.discardOnPause = false;
                resume = resumeAfterDiscardLocked(run);
                if (resume) {
                    resumeRevision = run.claimRevision;
                }
            }
            else {
                run.frameInFlight = false;
                run.publicationInFlight = true;
                publishOnEdt = true;
            }
        }
        completeRuns(completed);
        if (resume) {
            queueStep(run, resumeRevision);
            return;
        }
        if (!publishOnEdt) {
            return;
        }
        try {
            published.whenCompleteAsync((ignored, failure) -> finishPublication(run, idle, failure), lifecycle);
        }
        catch (RejectedExecutionException rejected) {
            terminalize(run);
            return;
        }
        try {
            edt.execute(new Runnable() {
                @Override
                public void run() {
                    synchronized (monitor) {
                        if (!isLiveLocked(run) || paused || run.discardOnPause) {
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
        final List<CompletableFuture<Void>> completed = new ArrayList<CompletableFuture<Void>>();
        boolean shouldStep = false;
        long stepRevision = 0L;
        synchronized (monitor) {
            run.publicationInFlight = false;
            if (!isLiveLocked(run)) {
                terminalizeLocked(run, completed);
            }
            else if (failure != null) {
                terminalizeLocked(run, completed);
            }
            else if (run.discardOnPause) {
                run.discardOnPause = false;
                shouldStep = resumeAfterDiscardLocked(run);
                if (shouldStep) {
                    stepRevision = run.claimRevision;
                }
            }
            else if (!paused && idle) {
                terminalizeLocked(run, completed);
            }
            else if (!paused && !run.frameInFlight) {
                claimFrameLocked(run, controlRevision);
                shouldStep = true;
                stepRevision = run.claimRevision;
            }
        }
        completeRuns(completed);
        if (shouldStep) {
            queueStep(run, stepRevision);
        }
    }

    private void queueStep(final Run run, final long revision) {
        queueLifecycle(new Runnable() {
            @Override
            public void run() {
                stepClaimed(run, revision);
            }
        });
    }

    private void fail(final Run run, final LayoutFrame source) {
        final List<CompletableFuture<Void>> completed = new ArrayList<CompletableFuture<Void>>();
        boolean resume = false;
        boolean discarded = false;
        long resumeRevision = 0L;
        synchronized (monitor) {
            if (!isLiveLocked(run)) {
                run.frameInFlight = false;
                terminalizeLocked(run, completed);
            }
            else if (paused || run.discardOnPause) {
                run.frameInFlight = false;
                run.discardOnPause = false;
                resume = resumeAfterDiscardLocked(run);
                if (resume) {
                    resumeRevision = run.claimRevision;
                }
                discarded = true;
            }
        }
        completeRuns(completed);
        if (resume) {
            queueStep(run, resumeRevision);
            return;
        }
        if (discarded || !isLive(run)) {
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
            terminalize(run);
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

    private void closeWorker(final CompletableFuture<Void> closeFuture) {
        try {
            worker.close();
            closeFuture.complete(null);
        }
        catch (RuntimeException failure) {
            closeFuture.completeExceptionally(failure);
        }
        finally {
            lifecycle.shutdown();
        }
    }

    private void awaitClose(final CompletableFuture<Void> closeFuture) {
        try {
            closeFuture.join();
        }
        catch (CompletionException failure) {
            final Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw failure;
        }
    }

    private boolean claimIsCurrentAndRunning(final Run run, final long revision) {
        synchronized (monitor) {
            if (isCurrentAndRunningLocked(run, revision) && run.frameInFlight
                    && run.claimRevision == revision) {
                return true;
            }
            releaseClaimLocked(run);
            return false;
        }
    }

    private boolean isCurrentAndRunning(final Run run, final long revision) {
        synchronized (monitor) {
            return isCurrentAndRunningLocked(run, revision);
        }
    }

    private boolean hasSubmittedRequest(final Run run) {
        synchronized (monitor) {
            return run.requestSubmitted;
        }
    }

    private void recordSubmittedRequest(final Run run) {
        synchronized (monitor) {
            run.requestSubmitted = true;
        }
    }

    private boolean isCurrentAndRunningLocked(final Run run, final long revision) {
        return isCurrentRevisionLocked(run, revision) && !paused;
    }

    private boolean isCurrentAndPaused(final Run run, final long revision) {
        synchronized (monitor) {
            return isCurrentRevisionLocked(run, revision) && paused;
        }
    }

    private boolean isLive(final Run run) {
        synchronized (monitor) {
            return isLiveLocked(run);
        }
    }

    private boolean isLiveLocked(final Run run) {
        return run != null && !run.terminal && isCurrentLocked(run);
    }

    private boolean isCurrentRevisionLocked(final Run run, final long revision) {
        return isLiveLocked(run) && controlRevision == revision;
    }

    private boolean isCurrentLocked(final Run run) {
        return !closed && currentRun == run && token == run.token;
    }

    private boolean resumeAfterDiscardLocked(final Run run) {
        if (!isLiveLocked(run) || paused || !run.restartRequested || run.frameInFlight || run.publicationInFlight) {
            return false;
        }
        run.restartRequested = false;
        claimFrameLocked(run, controlRevision);
        return true;
    }

    private void claimFrameLocked(final Run run, final long revision) {
        run.frameInFlight = true;
        run.claimRevision = revision;
    }

    private void releaseClaim(final Run run) {
        synchronized (monitor) {
            releaseClaimLocked(run);
        }
    }

    private void releaseClaimLocked(final Run run) {
        if (run == null) {
            return;
        }
        run.frameInFlight = false;
        if (isLiveLocked(run) && paused) {
            run.restartRequested = true;
        }
    }

    private void terminalize(final Run run) {
        final List<CompletableFuture<Void>> completed = new ArrayList<CompletableFuture<Void>>();
        synchronized (monitor) {
            terminalizeLocked(run, completed);
        }
        completeRuns(completed);
    }

    private void terminalizeCurrentLocked(final List<CompletableFuture<Void>> completed) {
        if (currentRun != null) {
            terminalizeLocked(currentRun, completed);
        }
    }

    private void terminalizeLocked(final Run run, final List<CompletableFuture<Void>> completed) {
        if (run != null && !run.terminal) {
            run.terminal = true;
            run.frameInFlight = false;
            run.publicationInFlight = false;
            completed.add(run.result);
        }
    }

    private static void completeRuns(final List<CompletableFuture<Void>> completed) {
        for (CompletableFuture<Void> result : completed) {
            result.complete(null);
        }
    }

    private void queueLifecycle(final Runnable command) {
        try {
            lifecycle.execute(command);
        }
        catch (RejectedExecutionException rejected) {
            // Closing the loop terminalizes all current runs before its dispatcher stops.
        }
    }

    private void requireOpenLocked() {
        if (closed) {
            throw new IllegalStateException("Layout settle loop is closed");
        }
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

    private static LifecycleDispatcher createLifecycleDispatcher() {
        return new ExecutorLifecycleDispatcher();
    }

    private static GeometryTextMetrics defaultMetrics() {
        return new AwtGeometryTextMetrics(new Font("Dialog", Font.PLAIN, 12),
            new FontRenderContext(null, true, true));
    }

    interface LifecycleDispatcher extends Executor {
        boolean isLifecycleThread();
        void shutdown();
    }

    interface FrameStepper {
        CompletionStage<LayoutFrame> submit(LayoutRequest request);
        CompletionStage<LayoutFrame> step();
        void pause();
        void restart();
        default void reset() {
            throw new UnsupportedOperationException("Frame stepper does not support reset");
        }
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
    }

    private static final class Run {
        private final long token;
        private final AcceptedBatch batch;
        private final GraphProjection projection;
        private final CanvasStateListener listener;
        private final LayoutRequest request;
        private final CompletableFuture<Void> result = new CompletableFuture<Void>();
        private long claimRevision;
        private boolean frameInFlight;
        private boolean publicationInFlight;
        private boolean discardOnPause;
        private boolean restartRequested;
        private boolean requestSubmitted;
        private boolean terminal;

        private Run(final long token, final AcceptedBatch batch, final GraphProjection projection,
                final CanvasStateListener listener, final LayoutRequest request) {
            this.token = token;
            this.batch = batch;
            this.projection = projection;
            this.listener = listener;
            this.request = request;
        }
    }

    private static final class ExecutorLifecycleDispatcher implements LifecycleDispatcher {
        private final ExecutorService executor;
        private final ThreadLocal<Boolean> lifecycleThread = new ThreadLocal<Boolean>();

        private ExecutorLifecycleDispatcher() {
            final int id = LIFECYCLE_IDS.incrementAndGet();
            executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
                @Override
                public Thread newThread(final Runnable command) {
                    final Thread thread = new Thread(command, "freeplane-graph-layout-lifecycle-" + id);
                    thread.setDaemon(true);
                    return thread;
                }
            });
        }

        @Override
        public void execute(final Runnable command) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    final Boolean previous = lifecycleThread.get();
                    lifecycleThread.set(Boolean.TRUE);
                    try {
                        command.run();
                    }
                    finally {
                        if (previous == null) {
                            lifecycleThread.remove();
                        }
                        else {
                            lifecycleThread.set(previous);
                        }
                    }
                }
            });
        }

        @Override
        public boolean isLifecycleThread() {
            return Boolean.TRUE.equals(lifecycleThread.get());
        }

        @Override
        public void shutdown() {
            executor.shutdown();
        }
    }

    private static final class WorkerStepper implements FrameStepper {
        private final LayoutCalibration calibration;
        private LayoutWorker worker;

        private WorkerStepper(final LayoutCalibration calibration) {
            this.calibration = Objects.requireNonNull(calibration, "calibration");
            this.worker = new LayoutWorker(calibration);
        }

        @Override
        public synchronized CompletionStage<LayoutFrame> submit(final LayoutRequest request) {
            return worker.submit(request);
        }

        @Override
        public synchronized CompletionStage<LayoutFrame> step() {
            return worker.step();
        }

        @Override
        public synchronized void pause() {
            worker.pause();
        }

        @Override
        public synchronized void restart() {
            worker.restart();
        }

        @Override
        public synchronized void reset() {
            worker.close();
            worker = new LayoutWorker(calibration);
        }

        @Override
        public synchronized LayoutFrame lastValidFrame() {
            return worker.lastValidFrame();
        }

        @Override
        public synchronized void close() {
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
