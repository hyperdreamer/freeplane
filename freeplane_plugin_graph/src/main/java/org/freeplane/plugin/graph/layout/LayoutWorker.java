package org.freeplane.plugin.graph.layout;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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

import org.freeplane.plugin.graph.geometry.AwtGeometryTextMetrics;
import org.freeplane.plugin.graph.geometry.GeometryTextMetrics;
import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.geometry.LayoutPositions;
import org.freeplane.plugin.graph.layout.graphstream.GraphStreamLayoutFactory;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.ProjectedEndpointKey;
import org.freeplane.plugin.graph.projection.ProjectedEndpointVisibility;
import org.freeplane.plugin.graph.projection.ProjectedNode;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;

public final class LayoutWorker implements AutoCloseable {
    private static final AtomicInteger WORKER_IDS = new AtomicInteger();
    private static final LayoutFrame EMPTY_FAILED_FRAME = LayoutFrame.withDiagnostics(
        LayoutFrame.of(0L, LayoutPositions.of(
            Collections.<ProjectedNodeKey, LayoutPoint>emptyMap(),
            Collections.<EnclosureHullKey, LayoutPoint>emptyMap()), true, 0),
        0, BoundarySeparationDiagnostics.empty(), EdgeConflictDiagnostics.empty(),
        PerceptualIdlePolicy.IdleMeasurement.initial());

    private final Supplier<LayoutEngine> engineFactory;
    private final PerceptualIdlePolicy idlePolicy;
    private final ExecutorService executor;
    private final Object lifecycleLock = new Object();
    private final Set<CompletableFuture<LayoutFrame>> pending =
        Collections.synchronizedSet(new HashSet<CompletableFuture<LayoutFrame>>());
    private final BoundarySeparationCorrection boundaryCorrection = new BoundarySeparationCorrection();
    private final EdgeConflictCorrection edgeConflictCorrection = new EdgeConflictCorrection();

    private volatile LayoutFrame lastValidFrame = EMPTY_FAILED_FRAME;
    private volatile boolean paused;
    private volatile boolean closed;
    private volatile boolean hasRequest;
    private volatile boolean failedEngine;
    private int pendingSubmits;
    private volatile Thread ownerThread;

    private LayoutEngine engine;
    private LayoutRequest currentRequest;
    private LayoutPositions previousCorrectedPositions;
    /**
     * Frames this request has run without reaching idle. A layout that is merely slow still converges inside the
     * budget; one that does not converge at all keeps producing corrections that the relaxation undoes. Seeding is
     * therefore withheld until the budget is spent, so no layout that settles on its own is ever perturbed.
     */
    static final long SEED_ESCALATION_FRAMES = 6000L;
    private long framesSinceRequest;
    private boolean seedEscalated;
    private Map<String, LayoutPoint> previousAppliedDisplacements = Collections.emptyMap();

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

    public LayoutWorker(final Supplier<LayoutEngine> engineFactory, final PerceptualIdlePolicy idlePolicy) {
        this.engineFactory = Objects.requireNonNull(engineFactory, "engineFactory");
        this.idlePolicy = Objects.requireNonNull(idlePolicy, "idlePolicy");
        this.executor = createExecutor();
    }

    public CompletionStage<LayoutFrame> submit(final LayoutRequest request) {
        final LayoutRequest value = Objects.requireNonNull(request, "request");
        return enqueue(new Work() {
            @Override
            public LayoutFrame run() {
                framesSinceRequest = 0L;
                seedEscalated = false;
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
        framesSinceRequest = 0L;
        seedEscalated = false;
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
        final BoundarySeparationResult boundary = boundaryCorrection.apply(request.projection(),
            raw.positions(), defaultMetrics(), request.pins());
        if (!boundary.diagnostics().boundaryVerified() && !boundary.diagnostics().boundaryCovered()) {
            throw new BoundarySeparationException("Boundary separation did not cover every residual pair");
        }
        final Set<ProjectedEndpointKey> visible = ProjectedEndpointVisibility.visibleEndpoints(
            request.projection().nodes(), request.projection().enclosures());
        final EdgeConflictResult edge = edgeConflictCorrection.apply(request.projection(),
            boundary.positions(), defaultMetrics(), request.pins(), visible, boundary);
        final LayoutPositions corrected = edge.positions();
        final Map<String, LayoutPoint> merged = mergeDisplacements(boundary.appliedDisplacements(),
            edge.appliedDisplacements());
        if (engine != null) {
            seedIfNotConverging(corrected);
        }
        final LayoutPositions before = previousCorrectedPositions == null ? corrected
            : previousCorrectedPositions;
        final PerceptualIdlePolicy.IdleMeasurement idle = idlePolicy.observe(before, corrected);
        final double[] deltas = displacementDeltas(previousAppliedDisplacements, merged);
        final LayoutFrame decorated = LayoutFrame.withDiagnostics(
            LayoutFrame.of(raw.stepIndex(), corrected, false, edge.nodeResidualViolations()),
            edge.nodeResidualViolations(),
            edge.boundaryDiagnostics().withDeltas(deltas[0], deltas[1]), edge.diagnostics(), idle);
        previousAppliedDisplacements = merged;
        currentRequest = request;
        hasRequest = true;
        previousCorrectedPositions = corrected;
        lastValidFrame = decorated;
        return decorated;
    }

    private void seedIfNotConverging(final LayoutPositions corrected) {
        framesSinceRequest++;
        if (!seedEscalated && framesSinceRequest > SEED_ESCALATION_FRAMES) {
            seedEscalated = true;
        }
        if (seedEscalated) {
            engine.seedPositions(corrected.nodes());
        }
    }

    private static Map<String, LayoutPoint> mergeDisplacements(final Map<String, LayoutPoint> boundary,
            final Map<String, LayoutPoint> edge) {
        final Map<String, LayoutPoint> result = new java.util.TreeMap<String, LayoutPoint>();
        for (final Map.Entry<String, LayoutPoint> entry : boundary.entrySet()) {
            result.put(entry.getKey(), entry.getValue());
        }
        for (final Map.Entry<String, LayoutPoint> entry : edge.entrySet()) {
            final LayoutPoint previous = result.get(entry.getKey());
            result.put(entry.getKey(), previous == null ? entry.getValue()
                : LayoutPoint.of(previous.x() + entry.getValue().x(),
                    previous.y() + entry.getValue().y()));
        }
        return new java.util.LinkedHashMap<String, LayoutPoint>(result);
    }

    private static double[] displacementDeltas(final Map<String, LayoutPoint> previous,
            final Map<String, LayoutPoint> current) {
        final Set<String> keys = new LinkedHashSet<String>(previous.keySet());
        keys.addAll(current.keySet());
        if (keys.isEmpty()) {
            return new double[] {0.0, 0.0};
        }
        double sumSquares = 0.0;
        double maximum = 0.0;
        for (final String key : keys) {
            final LayoutPoint before = previous.get(key);
            final LayoutPoint after = current.get(key);
            final double deltaX = (after == null ? 0.0 : after.x()) - (before == null ? 0.0 : before.x());
            final double deltaY = (after == null ? 0.0 : after.y()) - (before == null ? 0.0 : before.y());
            final double squared = deltaX * deltaX + deltaY * deltaY;
            sumSquares += squared;
            maximum = Math.max(maximum, Math.sqrt(squared));
        }
        return new double[] {Math.sqrt(sumSquares / keys.size()), maximum};
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

    private static GeometryTextMetrics defaultMetrics() {
        return new AwtGeometryTextMetrics(new Font("Dialog", Font.PLAIN, 12),
            new FontRenderContext(null, true, true));
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
        previousAppliedDisplacements = Collections.emptyMap();
        try {
            engine = newEngine();
        }
        catch (final RuntimeException exception) {
            failedEngine = true;
        }
    }

    private LayoutFrame failedFrame(final long requestedIndex) {
        final LayoutFrame retained = lastValidFrame;
        if (retained == EMPTY_FAILED_FRAME) {
            return EMPTY_FAILED_FRAME;
        }
        final long index = requestedIndex >= 0L ? requestedIndex : retained.stepIndex();
        return LayoutFrame.withDiagnostics(
            LayoutFrame.of(index, retained.positions(), true, retained.residualViolations()),
            retained.residualViolations(), retained.boundaryDiagnostics(),
            retained.edgeConflictDiagnostics(), retained.idle());
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
