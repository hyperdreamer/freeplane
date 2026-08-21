package org.freeplane.plugin.graph.performance;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.swing.SwingUtilities;

import org.freeplane.plugin.graph.canvas.GraphCanvas;
import org.freeplane.plugin.graph.canvas.GraphPaintState;
import org.freeplane.plugin.graph.canvas.GraphTheme;
import org.freeplane.plugin.graph.canvas.GraphViewport;
import org.freeplane.plugin.graph.control.AcceptedBatch;
import org.freeplane.plugin.graph.control.CanvasState;
import org.freeplane.plugin.graph.control.ChangeKind;
import org.freeplane.plugin.graph.control.LayoutSettleLoop;
import org.freeplane.plugin.graph.control.NanoClock;
import org.freeplane.plugin.graph.control.OperationalStatus;
import org.freeplane.plugin.graph.geometry.AwtGeometryTextMetrics;
import org.freeplane.plugin.graph.geometry.GeometryTextMetrics;
import org.freeplane.plugin.graph.geometry.GraphGeometry;
import org.freeplane.plugin.graph.geometry.GraphGeometryEngine;
import org.freeplane.plugin.graph.geometry.HullIntersection;
import org.freeplane.plugin.graph.geometry.LayoutPositions;
import org.freeplane.plugin.graph.layout.LayoutCalibration;
import org.freeplane.plugin.graph.layout.LayoutConflict;
import org.freeplane.plugin.graph.layout.LayoutEngine;
import org.freeplane.plugin.graph.layout.LayoutFrame;
import org.freeplane.plugin.graph.layout.LayoutRequest;
import org.freeplane.plugin.graph.layout.LayoutWorker;
import org.freeplane.plugin.graph.layout.MapTierCorrection;
import org.freeplane.plugin.graph.layout.graphstream.GraphStreamLayoutFactory;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.ProjectionDiff;
import org.freeplane.plugin.graph.projection.ProjectionEngine;
import org.freeplane.plugin.graph.workspace.model.DisplaySettings;
import org.freeplane.plugin.graph.workspace.model.WorkspaceId;

public final class GraphWorkspacePerformanceDiagnostic {
    public static final long OPERATION_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(5L);
    public static final long PROCESS_DEADLINE_NANOS = TimeUnit.MINUTES.toNanos(10L);
    public static final int CANVAS_WIDTH = 1024;
    public static final int CANVAS_HEIGHT = 768;
    public static final String OUTPUT_DIRECTORY_NAME = "graph-performance";

    private final Path outputDirectory;
    private final boolean strict;
    private final RelativeNanoClock clock;
    private final long processStart;
    private final ExecutorService boundedOperations;
    private final GraphGeometryEngine geometryEngine = new GraphGeometryEngine();
    private final MapTierCorrection correctionEngine = new MapTierCorrection();
    private final ProjectionEngine projectionEngine = new ProjectionEngine();
    private final GeometryTextMetrics textMetrics;
    private final GraphCanvas canvas;
    private GraphTheme theme;
    private final List<PerformanceMeasurements.Summary> ledgerRows =
        new ArrayList<PerformanceMeasurements.Summary>();
    private boolean preCorrectionOverlapChecked;

    private GraphWorkspacePerformanceDiagnostic(final Path outputDirectory, final boolean strict) {
        this.outputDirectory = outputDirectory;
        this.strict = strict;
        clock = new RelativeNanoClock();
        processStart = clock.nanoTime();
        boundedOperations = Executors.newCachedThreadPool(new DaemonThreadFactory("graph-performance-bound"));
        textMetrics = new AwtGeometryTextMetrics(new Font("Dialog", Font.PLAIN, 12),
            new FontRenderContext(new AffineTransform(), false, false));
        canvas = new GraphCanvas();
        theme = GraphTheme.resolve(DisplaySettings.CanvasTheme.LIGHT);
    }

    public static void main(final String[] args) throws Exception {
        final Path output = args.length == 0 ? Paths.get("build", OUTPUT_DIRECTORY_NAME)
            : Paths.get(args[0]);
        final boolean strict = Boolean.parseBoolean(System.getProperty("graphStrictPerformance", "false"));
        try {
            new GraphWorkspacePerformanceDiagnostic(output, strict).run();
        }
        catch (final Throwable failure) {
            failure.printStackTrace(System.err);
            if (failure instanceof Exception) {
                throw (Exception) failure;
            }
            if (failure instanceof Error) {
                throw (Error) failure;
            }
            throw new RuntimeException(failure);
        }
    }

    private void run() throws Exception {
        Throwable failure = null;
        try {
            prepareOutputDirectory();
            configureCanvas();
            final List<GeneratedWorkspace> workspaces = GeneratedWorkspace.all();
            writeRequiredFixtures(workspaces);
            for (final GeneratedWorkspace workspace : workspaces) {
                checkDeadline();
                configureScenarioCanvas(workspace);
                runScenario(workspace);
            }
            runLifecycleProbes(workspaces.get(0));
            writeLedger();
            verifyOutputNames();
            verifyWorkerCleanup();
            if (!allRowsPass()) {
                throw new DiagnosticFailure(null, "One or more performance ledger rows failed");
            }
        }
        catch (final Throwable caught) {
            failure = caught;
            throw caught;
        }
        finally {
            boundedOperations.shutdownNow();
            try {
                if (!boundedOperations.awaitTermination(5L, TimeUnit.SECONDS) && failure == null) {
                    throw new IllegalStateException("Bounded operation executor did not terminate");
                }
            }
            catch (final InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                if (failure == null) {
                    throw new IllegalStateException("Interrupted while cleaning up diagnostic executors",
                        interrupted);
                }
            }
        }
    }

    private void prepareOutputDirectory() throws IOException {
        Files.createDirectories(outputDirectory);
        deleteChildren(outputDirectory);
    }

    private void configureCanvas() throws Exception {
        invokeBounded(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                SwingUtilities.invokeAndWait(new Runnable() {
                    @Override
                    public void run() {
                        canvas.setSize(CANVAS_WIDTH, CANVAS_HEIGHT);
                        canvas.setDoubleBuffered(false);
                        canvas.setTheme(theme);
                        canvas.setPaintState(GraphPaintState.empty());
                        canvas.setViewport(GraphViewport.of(0.0, 0.0, 1.0));
                        canvas.setShowArrowheads(true);
                        canvas.setDimUnrelated(false);
                    }
                });
                return null;
            }
        }, "canvas setup");
    }

    private void configureScenarioCanvas(final GeneratedWorkspace workspace) {
        theme = GraphTheme.resolve(DisplaySettings.CanvasTheme.LIGHT, workspace.document().maps());
        invokeBounded(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                SwingUtilities.invokeAndWait(new Runnable() {
                    @Override
                    public void run() {
                        canvas.setTheme(theme);
                        canvas.setPaintState(GraphPaintState.empty());
                        canvas.setViewport(GraphViewport.of(0.0, 0.0, 1.0));
                        canvas.setShowArrowheads(true);
                        canvas.setDimUnrelated(false);
                    }
                });
                return null;
            }
        }, "scenario canvas setup");
    }

    private void writeRequiredFixtures(final List<GeneratedWorkspace> workspaces) throws IOException {
        for (final GeneratedWorkspace workspace : workspaces) {
            if (workspace.scenario() == GeneratedWorkspace.Scenario.TWO_MAP
                    || workspace.scenario() == GeneratedWorkspace.Scenario.THREE_MAP_CLUSTERED
                    || workspace.scenario() == GeneratedWorkspace.Scenario.REFERENCE_2000_5000) {
                workspace.writeFixture(outputDirectory);
            }
        }
        final Set<String> names = regularFileNames();
        final Set<String> expected = new HashSet<String>(Arrays.asList(
            "two-map.fpg", "three-map.fpg", "reference-2000-5000.fpg"));
        if (!names.equals(expected)) {
            throw new IllegalStateException("Fixture names differ from the allowlist: " + names);
        }
    }

    private void runScenario(final GeneratedWorkspace generated) {
        final GeneratedWorkspace.Scenario scenario = generated.scenario();
        preCorrectionOverlapChecked = false;
        final PerformanceMeasurements measurements = new PerformanceMeasurements(scenario.wireName(),
            scenario.warmupCount(), scenario.measuredCount());
        final GraphProjection previousBase = projectionEngine.project(
            org.freeplane.plugin.graph.projection.input.ProjectionInput.of(0L, generated.document(),
                generated.snapshots(), generated.availability()));
        GraphProjection previous = previousBase;
        final LayoutWorker worker = new LayoutWorker(LayoutCalibration.spikeDefaults());
        try {
            final int total = scenario.warmupCount() + scenario.measuredCount();
            for (int index = 0; index < total; index++) {
                checkDeadline();
                final boolean warmup = index < scenario.warmupCount();
                final long generation = index + 1L;
                try {
                    previous = runSample(generated, previous, worker, measurements, generation, warmup);
                }
                catch (final DiagnosticFailure failure) {
                    measurements.recordFailure(failure.stage == null
                        ? PerformanceMeasurements.Stage.SNAPSHOT : failure.stage);
                    throw failure;
                }
            }
            validateScenarioLifecycle(generated, worker, measurements);
            appendRows(measurements);
        }
        finally {
            worker.close();
        }
    }

    private GraphProjection runSample(final GeneratedWorkspace generated, final GraphProjection previous,
            final LayoutWorker worker, final PerformanceMeasurements measurements, final long generation,
            final boolean warmup) {
        final PerformanceMeasurements.Stage snapshotStage = PerformanceMeasurements.Stage.SNAPSHOT;
        final long snapshotStart = clock.nanoTime();
        final org.freeplane.plugin.graph.projection.input.ProjectionInput input =
            org.freeplane.plugin.graph.projection.input.ProjectionInput.of(generation, generated.document(),
                generated.snapshots(), generated.availability());
        final long snapshotEnd = clock.nanoTime();
        measurements.recordDuration(snapshotStage, snapshotStart, snapshotEnd, warmup);

        final AcceptedBatch accepted = new AcceptedBatch(generation, clock.nanoTime(),
            EnumSet.of(ChangeKind.STRUCTURE, ChangeKind.RELATIONSHIP));
        final long projectionStart = clock.nanoTime();
        final GraphProjection current = projectionEngine.project(input);
        final long projectionEnd = clock.nanoTime();
        measurements.recordDuration(PerformanceMeasurements.Stage.PROJECTION, projectionStart, projectionEnd,
            warmup);

        final long diffStart = clock.nanoTime();
        final ProjectionDiff diff = ProjectionDiff.between(previous, current);
        final long diffEnd = clock.nanoTime();
        measurements.recordDuration(PerformanceMeasurements.Stage.DIFF, diffStart, diffEnd, warmup);
        final LayoutRequest request = LayoutRequest.of(generated.document().id(), current, diff, current.pins());

        final long fullWorkerStart = clock.nanoTime();
        final LayoutFrame workerFrame = await(worker.submit(request), "layout worker submit");
        final long fullWorkerEnd = clock.nanoTime();
        measurements.recordDuration(PerformanceMeasurements.Stage.FULL_WORKER, fullWorkerStart, fullWorkerEnd,
            warmup);
        requireUsableFrame(workerFrame, "full worker");
        validatePinConflicts(generated, workerFrame);

        final long workerHullStart = clock.nanoTime();
        final GraphGeometry workerHull = geometryEngine.computeHulls(current, workerFrame.positions());
        final long workerHullEnd = clock.nanoTime();
        final long workerLabelStart = clock.nanoTime();
        final GraphGeometry workerGeometry = textMetrics == null ? workerHull
            : new org.freeplane.plugin.graph.geometry.LabelPlacementEngine().place(current, workerHull,
                textMetrics);
        final long workerLabelEnd = clock.nanoTime();

        final CanvasState state = CanvasState.of(generation, current, workerFrame, workerGeometry,
            OperationalStatus.IDLE);
        final long swapStart = clock.nanoTime();
        setCanvasStateBounded(state);
        final long swapEnd = clock.nanoTime();
        measurements.recordDuration(PerformanceMeasurements.Stage.EDT_SWAP, swapStart, swapEnd, warmup);
        final long acceptedEnd = clock.nanoTime();
        measurements.recordDuration(PerformanceMeasurements.Stage.ACCEPTED_BATCH_FIRST_FRAME,
            accepted.acceptedAtNanos(), acceptedEnd, warmup);
        if (workerHullEnd < workerHullStart || workerLabelEnd < workerLabelStart) {
            throw new DiagnosticFailure(PerformanceMeasurements.Stage.ACCEPTED_BATCH_FIRST_FRAME,
                "Worker geometry timing moved backwards");
        }

        final PaintResult painted = repaintBounded();
        measurements.recordDuration(PerformanceMeasurements.Stage.REPAINT, painted.startNanos,
            painted.endNanos, warmup);
        if (painted.nonBackgroundChecksum == 0L) {
            throw new DiagnosticFailure(PerformanceMeasurements.Stage.REPAINT,
                "Canvas paint produced no non-background pixel checksum");
        }

        runDirectProbe(generated, current, request, measurements, warmup);
        return current;
    }

    private void runDirectProbe(final GeneratedWorkspace generated, final GraphProjection projection,
            final LayoutRequest request, final PerformanceMeasurements measurements, final boolean warmup) {
        final LayoutEngine engine = GraphStreamLayoutFactory.create(LayoutCalibration.spikeDefaults());
        try {
            final long mutationStart = clock.nanoTime();
            final LayoutFrame applied = engine.apply(request);
            final long mutationEnd = clock.nanoTime();
            measurements.recordDuration(PerformanceMeasurements.Stage.MUTATION, mutationStart, mutationEnd,
                warmup);
            requireUsableFrame(applied, "direct apply");

            final GraphGeometry rawHull = geometryEngine.computeHulls(projection, applied.positions());
            if (!preCorrectionOverlapChecked
                    && generated.scenario() == GeneratedWorkspace.Scenario.TWO_PINNED_MAPS) {
                assertPinnedRootOverlap(projection, rawHull);
                preCorrectionOverlapChecked = true;
            }

            final long correctionStart = clock.nanoTime();
            final MapTierCorrection.CorrectionResult corrected = correctionEngine.apply(projection,
                applied.positions(), rawHull);
            final long correctionEnd = clock.nanoTime();
            measurements.recordDuration(PerformanceMeasurements.Stage.CORRECTION, correctionStart,
                correctionEnd, warmup);

            final long hullStart = clock.nanoTime();
            final GraphGeometry correctedHull = geometryEngine.computeHulls(projection, corrected.positions());
            final long hullEnd = clock.nanoTime();
            measurements.recordDuration(PerformanceMeasurements.Stage.HULL, hullStart, hullEnd, warmup);

            final long labelStart = clock.nanoTime();
            new org.freeplane.plugin.graph.geometry.LabelPlacementEngine().place(projection, correctedHull,
                textMetrics);
            final long labelEnd = clock.nanoTime();
            measurements.recordDuration(PerformanceMeasurements.Stage.LABEL, labelStart, labelEnd, warmup);

            final long forceStart = clock.nanoTime();
            final LayoutFrame forced = engine.step();
            final long forceEnd = clock.nanoTime();
            measurements.recordDuration(PerformanceMeasurements.Stage.FORCE, forceStart, forceEnd, warmup);
            requireUsableFrame(forced, "direct force step");
        }
        catch (final DiagnosticFailure failure) {
            throw failure;
        }
        catch (final RuntimeException failure) {
            throw new DiagnosticFailure(PerformanceMeasurements.Stage.MUTATION,
                "Direct public layout probe failed", failure);
        }
        finally {
            try {
                engine.close();
            }
            catch (final RuntimeException failure) {
                throw new DiagnosticFailure(PerformanceMeasurements.Stage.MUTATION,
                    "Direct layout engine cleanup failed", failure);
            }
        }
    }

    private void validateScenarioLifecycle(final GeneratedWorkspace generated, final LayoutWorker worker,
            final PerformanceMeasurements measurements) {
        if (worker.lastValidFrame() == null || worker.lastValidFrame().failed()) {
            throw new DiagnosticFailure(PerformanceMeasurements.Stage.FULL_WORKER,
                "Scenario worker did not retain a valid frame");
        }
        if (generated.scenario() == GeneratedWorkspace.Scenario.TWO_PINNED_MAPS
                && !preCorrectionOverlapChecked) {
            throw new DiagnosticFailure(PerformanceMeasurements.Stage.CORRECTION,
                "Pinned-map pre-correction overlap was not observed");
        }
        for (final PerformanceMeasurements.Stage stage : PerformanceMeasurements.Stage.values()) {
            if (measurements.warmupCount(stage) != generated.warmupCount()
                    || measurements.measuredCount(stage) != generated.measuredCount()) {
                throw new DiagnosticFailure(stage, "Scenario sample count is incomplete");
            }
        }
    }

    private void validatePinConflicts(final GeneratedWorkspace generated, final LayoutFrame frame) {
        final GeneratedWorkspace.Scenario scenario = generated.scenario();
        if (scenario == GeneratedWorkspace.Scenario.ONE_PINNED_MAP && !frame.conflicts().isEmpty()) {
            throw new DiagnosticFailure(PerformanceMeasurements.Stage.FULL_WORKER,
                "One-pinned-map unexpectedly produced rigid conflicts");
        }
        if (scenario != GeneratedWorkspace.Scenario.TWO_PINNED_MAPS) {
            return;
        }
        if (frame.conflicts().size() != 1) {
            throw new DiagnosticFailure(PerformanceMeasurements.Stage.FULL_WORKER,
                "Two-pinned-maps must produce exactly one rigid conflict, got "
                    + frame.conflicts().size());
        }
        final LayoutConflict conflict = frame.conflicts().get(0);
        if (conflict.blockingPins().size() != 2
                || !conflict.blockingPins().get(0).source().nodeId().value().equals("m00-n0001")
                || !conflict.blockingPins().get(1).source().nodeId().value().equals("m01-n0001")) {
            throw new DiagnosticFailure(PerformanceMeasurements.Stage.FULL_WORKER,
                "Two-pinned-maps conflict did not retain both pin identities");
        }
    }

    private void assertPinnedRootOverlap(final GraphProjection projection, final GraphGeometry geometry) {
        org.freeplane.plugin.graph.projection.ProjectedEnclosure first = null;
        org.freeplane.plugin.graph.projection.ProjectedEnclosure second = null;
        for (final org.freeplane.plugin.graph.projection.ProjectedEnclosure enclosure : projection.enclosures()) {
            if (!enclosure.mapRoot()) {
                continue;
            }
            if (enclosure.mapReferenceId().equals(generatedMapId(0))) {
                first = enclosure;
            }
            else if (enclosure.mapReferenceId().equals(generatedMapId(1))) {
                second = enclosure;
            }
        }
        if (first == null || second == null) {
            throw new DiagnosticFailure(PerformanceMeasurements.Stage.CORRECTION,
                "Pinned root hulls are missing");
        }
        final org.freeplane.plugin.graph.geometry.LayoutPoint translation =
            HullIntersection.minimumSeparatingTranslation(geometry.hulls().get(first.hullKey()),
                geometry.hulls().get(second.hullKey()));
        if (translation.x() == 0.0 && translation.y() == 0.0) {
            throw new DiagnosticFailure(PerformanceMeasurements.Stage.CORRECTION,
                "Pinned root hulls did not overlap before correction");
        }
    }

    private org.freeplane.plugin.graph.workspace.model.MapReferenceId generatedMapId(final int index) {
        return org.freeplane.plugin.graph.workspace.model.MapReferenceId.of(String.format(
            java.util.Locale.ROOT, "00000000-0000-0000-0000-%012d", index + 1));
    }

    private void runLifecycleProbes(final GeneratedWorkspace generated) {
        final GraphProjection projection = generated.projection();
        final ProjectionDiff diff = ProjectionDiff.between(projection, projection);
        final AcceptedBatch mismatched = new AcceptedBatch(projection.generation() + 1L, clock.nanoTime(),
            EnumSet.of(ChangeKind.STRUCTURE));
        final LayoutSettleLoop loop = new LayoutSettleLoop(generated.document().id());
        try {
            boolean rejected = false;
            try {
                loop.start(mismatched, projection, diff, new org.freeplane.plugin.graph.control.CanvasStateListener() {
                    @Override
                    public void onCanvasState(final CanvasState state) {
                        throw new AssertionError("Generation mismatch must not publish a state");
                    }
                });
            }
            catch (final IllegalArgumentException expected) {
                rejected = true;
            }
            if (!rejected) {
                throw new DiagnosticFailure(null,
                    "LayoutSettleLoop accepted a mismatched batch/projection generation");
            }
        }
        finally {
            loop.close();
        }

        final LayoutWorker closedWorker = new LayoutWorker(LayoutCalibration.spikeDefaults());
        try {
            closedWorker.close();
            if (!closedWorker.submit(LayoutRequest.of(generated.document().id(), projection, diff,
                projection.pins())).toCompletableFuture().isCompletedExceptionally()) {
                throw new DiagnosticFailure(null, "Closed LayoutWorker accepted submit");
            }
            if (!closedWorker.step().toCompletableFuture().isCompletedExceptionally()) {
                throw new DiagnosticFailure(null, "Closed LayoutWorker accepted step");
            }
        }
        finally {
            closedWorker.close();
        }
    }

    private void appendRows(final PerformanceMeasurements measurements) {
        for (final PerformanceMeasurements.Stage stage : PerformanceMeasurements.Stage.values()) {
            ledgerRows.add(measurements.summary(stage,
                PerformanceMeasurements.normalThresholdNanos(measurements.scenario(), stage),
                PerformanceMeasurements.strictThresholdNanos(measurements.scenario(), stage), strict));
        }
    }

    private void writeLedger() throws IOException {
        final StringBuilder csv = new StringBuilder();
        csv.append(PerformanceMeasurements.CSV_HEADER).append('\n');
        for (final PerformanceMeasurements.Summary row : ledgerRows) {
            csv.append(row.csvRow()).append('\n');
        }
        Files.write(outputDirectory.resolve("performance-ledger.csv"),
            csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private boolean allRowsPass() {
        if (ledgerRows.size() != GeneratedWorkspace.Scenario.values().length
                * PerformanceMeasurements.Stage.values().length) {
            return false;
        }
        for (final PerformanceMeasurements.Summary row : ledgerRows) {
            if (!row.pass()) {
                return false;
            }
        }
        return true;
    }

    private void verifyOutputNames() throws IOException {
        final Set<String> names = regularFileNames();
        final Set<String> expected = new HashSet<String>(Arrays.asList(
            "two-map.fpg", "three-map.fpg", "reference-2000-5000.fpg", "performance-ledger.csv"));
        if (!names.equals(expected)) {
            throw new IllegalStateException("Diagnostic output names differ from the allowlist: " + names);
        }
    }

    private Set<String> regularFileNames() throws IOException {
        final Set<String> result = new HashSet<String>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(outputDirectory)) {
            for (final Path file : files) {
                if (Files.isRegularFile(file)) {
                    result.add(file.getFileName().toString());
                }
            }
        }
        return result;
    }

    private void verifyWorkerCleanup() throws InterruptedException {
        final long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5L);
        while (System.nanoTime() < end) {
            boolean workerAlive = false;
            for (final Thread thread : Thread.getAllStackTraces().keySet()) {
                if (thread.isAlive() && thread.getName().startsWith("freeplane-graph-layout-worker-")) {
                    workerAlive = true;
                    break;
                }
            }
            if (!workerAlive) {
                return;
            }
            Thread.sleep(10L);
        }
        throw new IllegalStateException("A graph layout worker thread survived cleanup");
    }

    private PaintResult repaintBounded() {
        return invokeBounded(new Callable<PaintResult>() {
            @Override
            public PaintResult call() throws Exception {
                final PaintResult[] result = new PaintResult[1];
                SwingUtilities.invokeAndWait(new Runnable() {
                    @Override
                    public void run() {
                        final BufferedImage image = new BufferedImage(CANVAS_WIDTH, CANVAS_HEIGHT,
                            BufferedImage.TYPE_INT_ARGB);
                        final long start = clock.nanoTime();
                        final Graphics2D graphics = image.createGraphics();
                        try {
                            canvas.paint(graphics);
                        }
                        finally {
                            graphics.dispose();
                        }
                        final long end = clock.nanoTime();
                        long nonBackgroundChecksum = 0L;
                        final int background = theme.background().getRGB();
                        for (int y = 0; y < image.getHeight(); y++) {
                            for (int x = 0; x < image.getWidth(); x++) {
                                final int pixel = image.getRGB(x, y);
                                if (pixel != background) {
                                    nonBackgroundChecksum = nonBackgroundChecksum * 0x100000001b3L
                                        ^ (pixel & 0xffffffffL);
                                }
                            }
                        }
                        result[0] = new PaintResult(start, end, nonBackgroundChecksum);
                    }
                });
                return result[0];
            }
        }, "canvas repaint");
    }

    private void setCanvasStateBounded(final CanvasState state) {
        invokeBounded(new Callable<Void>() {
            @Override
            public Void call() {
                canvas.setCanvasState(state);
                return null;
            }
        }, "canvas state swap");
    }

    private <T> T invokeBounded(final Callable<T> operation, final String description) {
        final Future<T> future = boundedOperations.submit(operation);
        try {
            return future.get(OPERATION_TIMEOUT_NANOS, TimeUnit.NANOSECONDS);
        }
        catch (final InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            throw new DiagnosticFailure(null, "Interrupted during " + description, interrupted);
        }
        catch (final TimeoutException timeout) {
            future.cancel(true);
            throw new DiagnosticFailure(null, "Five-second timeout during " + description, timeout);
        }
        catch (final ExecutionException failed) {
            final Throwable cause = failed.getCause();
            if (cause instanceof DiagnosticFailure) {
                throw (DiagnosticFailure) cause;
            }
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new DiagnosticFailure(null, description + " failed", cause);
        }
    }

    private <T> T await(final CompletionStage<T> stage, final String description) {
        try {
            return stage.toCompletableFuture().get(OPERATION_TIMEOUT_NANOS, TimeUnit.NANOSECONDS);
        }
        catch (final InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new DiagnosticFailure(PerformanceMeasurements.Stage.FULL_WORKER,
                "Interrupted during " + description, interrupted);
        }
        catch (final TimeoutException timeout) {
            throw new DiagnosticFailure(PerformanceMeasurements.Stage.FULL_WORKER,
                "Five-second timeout during " + description, timeout);
        }
        catch (final ExecutionException failed) {
            throw new DiagnosticFailure(PerformanceMeasurements.Stage.FULL_WORKER,
                description + " failed", unwrap(failed));
        }
        catch (final CompletionException failed) {
            throw new DiagnosticFailure(PerformanceMeasurements.Stage.FULL_WORKER,
                description + " failed", unwrap(failed));
        }
    }

    private static Throwable unwrap(final Throwable failure) {
        Throwable current = failure;
        while ((current instanceof ExecutionException || current instanceof CompletionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static void requireUsableFrame(final LayoutFrame frame, final String operation) {
        if (frame == null || frame.failed()) {
            throw new DiagnosticFailure(PerformanceMeasurements.Stage.FULL_WORKER,
                operation + " returned a failed frame");
        }
        for (final org.freeplane.plugin.graph.geometry.LayoutPoint point : frame.positions().nodes().values()) {
            if (!Double.isFinite(point.x()) || !Double.isFinite(point.y())) {
                throw new DiagnosticFailure(PerformanceMeasurements.Stage.FULL_WORKER,
                    operation + " returned a non-finite node position");
            }
        }
        for (final org.freeplane.plugin.graph.geometry.LayoutPoint point : frame.positions().anchors().values()) {
            if (!Double.isFinite(point.x()) || !Double.isFinite(point.y())) {
                throw new DiagnosticFailure(PerformanceMeasurements.Stage.FULL_WORKER,
                    operation + " returned a non-finite enclosure position");
            }
        }
    }

    private void checkDeadline() {
        final long elapsed = PerformanceMeasurements.checkedDuration(processStart, clock.nanoTime());
        if (elapsed > PROCESS_DEADLINE_NANOS) {
            throw new DiagnosticFailure(null, "Ten-minute diagnostic process deadline exceeded");
        }
    }

    private static void deleteChildren(final Path directory) throws IOException {
        try (DirectoryStream<Path> children = Files.newDirectoryStream(directory)) {
            for (final Path child : children) {
                if (Files.isDirectory(child)) {
                    deleteChildren(child);
                }
                Files.deleteIfExists(child);
            }
        }
    }

    public static final class RelativeNanoClock implements NanoClock {
        private final long origin = System.nanoTime();
        private long last;

        @Override
        public synchronized long nanoTime() {
            final long now = System.nanoTime();
            final long elapsed;
            try {
                elapsed = Math.subtractExact(now, origin);
            }
            catch (final ArithmeticException overflow) {
                throw new IllegalStateException("Monotonic clock subtraction overflow", overflow);
            }
            if (elapsed < 0L || elapsed < last) {
                throw new IllegalStateException("Monotonic clock moved backwards");
            }
            last = elapsed;
            return elapsed;
        }
    }

    private static final class PaintResult {
        private final long startNanos;
        private final long endNanos;
        private final long nonBackgroundChecksum;

        private PaintResult(final long startNanos, final long endNanos, final long nonBackgroundChecksum) {
            this.startNanos = startNanos;
            this.endNanos = endNanos;
            this.nonBackgroundChecksum = nonBackgroundChecksum;
        }
    }

    private static final class DaemonThreadFactory implements ThreadFactory {
        private final String prefix;
        private int next;

        private DaemonThreadFactory(final String prefix) {
            this.prefix = prefix;
        }

        @Override
        public synchronized Thread newThread(final Runnable command) {
            final Thread thread = new Thread(command, prefix + (++next));
            thread.setDaemon(true);
            return thread;
        }
    }

    private static final class DiagnosticFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final PerformanceMeasurements.Stage stage;

        private DiagnosticFailure(final PerformanceMeasurements.Stage stage, final String message) {
            super(message);
            this.stage = stage;
        }

        private DiagnosticFailure(final PerformanceMeasurements.Stage stage, final String message,
                final Throwable cause) {
            super(message, cause);
            this.stage = stage;
        }
    }
}
