package org.freeplane.plugin.graph.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.freeplane.plugin.graph.geometry.GraphGeometry;
import org.freeplane.plugin.graph.geometry.GraphGeometryEngine;
import org.freeplane.plugin.graph.geometry.LayoutPositions;
import org.freeplane.plugin.graph.layout.graphstream.GraphStreamLayoutFactory;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.ProjectedNode;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;

public final class LayoutWorker implements AutoCloseable {
    private static final AtomicInteger WORKER_IDS = new AtomicInteger();

    private final Supplier<LayoutEngine> engineFactory;
    private final PerceptualIdlePolicy idlePolicy;
    private final ExecutorService executor;
    private final Object lifecycleLock = new Object();
    private final Set<CompletableFuture<LayoutFrame>> pending =
        Collections.synchronizedSet(new HashSet<CompletableFuture<LayoutFrame>>());
    private final GraphGeometryEngine geometryEngine = new GraphGeometryEngine();
    private final MapTierCorrection mapCorrection = new MapTierCorrection();

    private volatile LayoutFrame lastValidFrame;
    private volatile boolean paused;
    private volatile boolean closed;
    private volatile boolean hasRequest;
    private volatile boolean failedEngine;
    private int pendingSubmits;
    private volatile Thread ownerThread;

    private LayoutEngine engine;
    private LayoutRequest currentRequest;
    private LayoutPositions previousCorrectedPositions;

    public LayoutWorker(final LayoutCalibration calibration) {
        final LayoutCalibration value = Objects.requireNonNull(calibration, "calibration");
        this.engineFactory = new Supplier<LayoutEngine>() {
            @Override
            public LayoutEngine get() {
                return GraphStreamLayoutFactory.create(value);
            }
        };
        this.idlePolicy = PerceptualIdlePolicy.spikeDefaults();
        this.executor = createExecutor();
    }

    LayoutWorker(final Supplier<LayoutEngine> engineFactory, final PerceptualIdlePolicy idlePolicy) {
        this.engineFactory = Objects.requireNonNull(engineFactory, "engineFactory");
        this.idlePolicy = Objects.requireNonNull(idlePolicy, "idlePolicy");
        this.executor = createExecutor();
    }

    public CompletionStage<LayoutFrame> submit(final LayoutRequest request) {
        final LayoutRequest value = Objects.requireNonNull(request, "request");
        return enqueue(new Work() {
            @Override
            public LayoutFrame run() {
                return runSubmit(value);
            }
        }, true);
    }

    public CompletionStage<LayoutFrame> step() {
        synchronized (lifecycleLock) {
            if (closed) {
                return rejectedStage();
            }
            if (paused || (!hasRequest && pendingSubmits == 0 && !failedEngine)) {
                return CompletableFuture.completedFuture(lastValidFrame);
            }
            return enqueue(new Work() {
                @Override
                public LayoutFrame run() {
                    return runStep();
                }
            }, false);
        }
    }

    public void pause() {
        if (!closed) {
            paused = true;
        }
    }

    public void restart() {
        if (closed) {
            return;
        }
        paused = false;
        enqueueControl(new Runnable() {
            @Override
            public void run() {
                runRestart();
            }
        });
    }

    public LayoutFrame lastValidFrame() {
        return lastValidFrame;
    }

    @Override
    public void close() {
        if (isOwnerThread()) {
            closeFromOwnerThread();
            return;
        }
        boolean scheduleClose = false;
        synchronized (lifecycleLock) {
            if (!closed) {
                closed = true;
                paused = true;
                cancelPending();
                scheduleClose = true;
            }
        }
        if (scheduleClose) {
            try {
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        closeEngine();
                    }
                });
            }
            catch (final RejectedExecutionException exception) {
                // A concurrent close owns the executor shutdown and owner-thread cleanup.
            }
            executor.shutdown();
        }
        awaitTermination();
    }

    private ExecutorService createExecutor() {
        final int workerId = WORKER_IDS.incrementAndGet();
        return Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(final Runnable command) {
                return new Thread(new Runnable() {
                    @Override
                    public void run() {
                        ownerThread = Thread.currentThread();
                        command.run();
                    }
                }, "freeplane-graph-layout-worker-" + workerId);
            }
        });
    }

    private CompletionStage<LayoutFrame> enqueue(final Work work, final boolean submit) {
        final CompletableFuture<LayoutFrame> result = new CompletableFuture<LayoutFrame>();
        synchronized (lifecycleLock) {
            if (closed) {
                result.completeExceptionally(new IllegalStateException("Layout worker is closed"));
                return result;
            }
            if (submit) {
                pendingSubmits++;
            }
            pending.add(result);
            try {
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        if (result.isCancelled()) {
                            pending.remove(result);
                            finishSubmit(submit);
                            return;
                        }
                        try {
                            result.complete(work.run());
                        }
                        catch (final RuntimeException exception) {
                            result.completeExceptionally(exception);
                        }
                        finally {
                            pending.remove(result);
                            finishSubmit(submit);
                        }
                    }
                });
            }
            catch (final RejectedExecutionException exception) {
                pending.remove(result);
                finishSubmit(submit);
                result.completeExceptionally(new IllegalStateException("Layout worker is closed", exception));
            }
        }
        return result;
    }

    private void finishSubmit(final boolean submit) {
        if (submit) {
            synchronized (lifecycleLock) {
                pendingSubmits--;
            }
        }
    }

    private void enqueueControl(final Runnable command) {
        synchronized (lifecycleLock) {
            if (closed) {
                return;
            }
            try {
                executor.execute(command);
            }
            catch (final RejectedExecutionException exception) {
                // close() won the lifecycle race.
            }
        }
    }

    private LayoutFrame runSubmit(final LayoutRequest request) {
        if (failedEngine) {
            return failedFrame(-1L);
        }
        try {
            if (engine == null) {
                engine = newEngine();
            }
            return accept(request, engine.apply(request));
        }
        catch (final RuntimeException exception) {
            failedEngine = true;
            return failedFrame(-1L);
        }
    }

    private LayoutFrame runStep() {
        if (paused) {
            return lastValidFrame;
        }
        if (failedEngine) {
            return failedFrame(-1L);
        }
        if (currentRequest == null || engine == null) {
            return lastValidFrame;
        }
        try {
            return accept(currentRequest, engine.step());
        }
        catch (final RuntimeException exception) {
            failedEngine = true;
            return failedFrame(-1L);
        }
    }

    private LayoutFrame accept(final LayoutRequest request, final LayoutFrame raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Layout engine returned no frame");
        }
        if (raw.failed()) {
            failedEngine = true;
            return failedFrame(raw.stepIndex());
        }
        validateCoverage(request.projection(), raw.positions());
        final GraphGeometry geometry = geometryEngine.computeHulls(request.projection(), raw.positions());
        final MapTierCorrection.CorrectionResult correction = mapCorrection.apply(request.projection(),
            raw.positions(), geometry, request.pins());
        final LayoutPositions corrected = correction.positions();
        final LayoutPositions before = previousCorrectedPositions == null ? corrected : previousCorrectedPositions;
        final PerceptualIdlePolicy.IdleMeasurement idle = idlePolicy.observe(before, corrected);
        final LayoutFrame decorated = LayoutFrame.withDiagnostics(
            LayoutFrame.of(raw.stepIndex(), corrected, false), correction.conflicts(), idle);
        currentRequest = request;
        hasRequest = true;
        previousCorrectedPositions = corrected;
        lastValidFrame = decorated;
        return decorated;
    }

    private void validateCoverage(final GraphProjection projection, final LayoutPositions positions) {
        final Set<ProjectedNodeKey> nodeKeys = new HashSet<ProjectedNodeKey>();
        for (final ProjectedNode node : projection.nodes()) {
            nodeKeys.add(node.key());
        }
        final Set<EnclosureHullKey> anchorKeys = new HashSet<EnclosureHullKey>();
        for (final org.freeplane.plugin.graph.projection.ProjectedEnclosure enclosure : projection.enclosures()) {
            anchorKeys.add(enclosure.hullKey());
        }
        if (!nodeKeys.equals(positions.nodes().keySet()) || !anchorKeys.equals(positions.anchors().keySet())) {
            throw new IllegalArgumentException("Layout frame positions must cover the current projection");
        }
    }

    private LayoutEngine newEngine() {
        return Objects.requireNonNull(engineFactory.get(), "engineFactory result");
    }

    private void runRestart() {
        if (closed || !failedEngine) {
            return;
        }
        closeEngine();
        currentRequest = null;
        hasRequest = false;
        failedEngine = false;
        try {
            engine = newEngine();
        }
        catch (final RuntimeException exception) {
            failedEngine = true;
        }
    }

    private LayoutFrame failedFrame(final long requestedIndex) {
        final LayoutFrame retained = lastValidFrame;
        final long index = requestedIndex >= 0L ? requestedIndex : retained == null ? 0L : retained.stepIndex();
        final LayoutPositions positions = retained == null
            ? LayoutPositions.of(Collections.<ProjectedNodeKey, org.freeplane.plugin.graph.geometry.LayoutPoint>emptyMap(),
                Collections.<EnclosureHullKey, org.freeplane.plugin.graph.geometry.LayoutPoint>emptyMap())
            : retained.positions();
        final PerceptualIdlePolicy.IdleMeasurement idle = retained == null
            ? PerceptualIdlePolicy.IdleMeasurement.initial() : retained.idle();
        return LayoutFrame.withDiagnostics(LayoutFrame.of(index, positions, true),
            Collections.<LayoutConflict>emptyList(), idle);
    }

    private void closeEngine() {
        final LayoutEngine value = engine;
        engine = null;
        if (value != null) {
            try {
                value.close();
            }
            catch (final RuntimeException exception) {
                // Closing a failed engine must not keep the worker alive.
            }
        }
    }

    private void closeFromOwnerThread() {
        synchronized (lifecycleLock) {
            if (closed) {
                executor.shutdown();
                return;
            }
            closed = true;
            paused = true;
            cancelPending();
        }
        closeEngine();
        executor.shutdown();
    }

    private void cancelPending() {
        final List<CompletableFuture<LayoutFrame>> futures;
        synchronized (pending) {
            futures = new ArrayList<CompletableFuture<LayoutFrame>>(pending);
        }
        for (final CompletableFuture<LayoutFrame> future : futures) {
            future.cancel(false);
        }
    }

    private void awaitTermination() {
        boolean interrupted = false;
        for (;;) {
            try {
                if (executor.awaitTermination(1L, TimeUnit.DAYS)) {
                    break;
                }
            }
            catch (final InterruptedException exception) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private CompletionStage<LayoutFrame> rejectedStage() {
        final CompletableFuture<LayoutFrame> result = new CompletableFuture<LayoutFrame>();
        result.completeExceptionally(new IllegalStateException("Layout worker is closed"));
        return result;
    }

    private boolean isOwnerThread() {
        return Thread.currentThread() == ownerThread;
    }

    private interface Work {
        LayoutFrame run();
    }
}
