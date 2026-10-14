package org.freeplane.plugin.graph.performance;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
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
import org.freeplane.plugin.graph.canvas.LabelFonts;
import org.freeplane.plugin.graph.canvas.LabelPlacementRequest;
import org.freeplane.plugin.graph.canvas.RenderingLevel;
import org.freeplane.plugin.graph.canvas.ScreenLabelPlacementCache;
import org.freeplane.plugin.graph.control.AcceptedBatch;
import org.freeplane.plugin.graph.control.CanvasState;
import org.freeplane.plugin.graph.control.ChangeKind;
import org.freeplane.plugin.graph.control.LayoutSettleLoop;
import org.freeplane.plugin.graph.control.NanoClock;
import org.freeplane.plugin.graph.control.OperationalStatus;
import org.freeplane.plugin.graph.geometry.AwtGeometryTextMetrics;
import org.freeplane.plugin.graph.geometry.EdgePaths;
import org.freeplane.plugin.graph.geometry.EdgeRouteDiagnostics;
import org.freeplane.plugin.graph.geometry.EdgeRouter;
import org.freeplane.plugin.graph.geometry.GeometryTextMetrics;
import org.freeplane.plugin.graph.geometry.GraphGeometry;
import org.freeplane.plugin.graph.geometry.GraphGeometryEngine;
import org.freeplane.plugin.graph.geometry.HullIntersection;
import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.geometry.LayoutPositions;
import org.freeplane.plugin.graph.layout.BoundaryConflict;
import org.freeplane.plugin.graph.layout.BoundarySeparationCorrection;
import org.freeplane.plugin.graph.layout.BoundarySeparationDiagnostics;
import org.freeplane.plugin.graph.layout.BoundarySeparationResult;
import org.freeplane.plugin.graph.layout.EdgeConflictCorrection;
import org.freeplane.plugin.graph.layout.EdgeConflictDiagnostics;
import org.freeplane.plugin.graph.layout.EdgeConflictResult;
import org.freeplane.plugin.graph.layout.LayoutCalibration;
import org.freeplane.plugin.graph.layout.LayoutEngine;
import org.freeplane.plugin.graph.layout.LayoutFrame;
import org.freeplane.plugin.graph.layout.LayoutRequest;
import org.freeplane.plugin.graph.layout.LayoutWorker;
import org.freeplane.plugin.graph.layout.graphstream.GraphStreamLayoutFactory;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.PinProjection;
import org.freeplane.plugin.graph.projection.ProjectedEndpointKey;
import org.freeplane.plugin.graph.projection.ProjectedEndpointVisibility;
import org.freeplane.plugin.graph.projection.ProjectedNode;
import org.freeplane.plugin.graph.projection.ProjectionDiff;
import org.freeplane.plugin.graph.projection.ProjectionEngine;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.freeplane.plugin.graph.workspace.model.DisplaySettings;
import org.freeplane.plugin.graph.workspace.model.WorkspaceId;

public final class GraphWorkspacePerformanceDiagnostic {
    public static final long OPERATION_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(5L);
    public static final long PROCESS_DEADLINE_NANOS = TimeUnit.MINUTES.toNanos(40L);
    public static final int CANVAS_WIDTH = 1024;
    public static final int CANVAS_HEIGHT = 768;
    public static final String OUTPUT_DIRECTORY_NAME = "graph-performance";
    static final int WARMUP_SAMPLES = 20;
    static final int TIMED_SAMPLES = 100;
    static final int RAW_REFERENCE_FRAMES = 8;
    /** Complete worker/geometry/routing frame bound, retained by the Step 9 audit fix. */
    static final long RAW_REFERENCE_MAX_FRAME_NANOS = 5_000_000_000L;
    private static final int PLACEMENT_TRIGGER_COUNT = 4;
    private static final int FORCED_SAMPLE_SIZE = 8;
    private static final double PLACEMENT_AREA_WIDTH = 1128.0;
    private static final double PLACEMENT_AREA_HEIGHT = 364.0;

    /**
     * Golden conflict count for the TWO_PINNED_MAPS scenario under generalized enforcement.
     * Captured from the first measured run and updated only from a measured run; see the task step.
     */
    private static final int TWO_PINNED_MAPS_EXPECTED_CONFLICTS = 1;

    private final Path outputDirectory;
    private final boolean strict;
    private final NanoClock clock;
    private final RawReferenceRouter rawReferenceRouter;
    private final long processStart;
    private final ExecutorService boundedOperations;
    private final GraphGeometryEngine geometryEngine = new GraphGeometryEngine();
    private final BoundarySeparationCorrection correctionEngine = new BoundarySeparationCorrection();
    private final ProjectionEngine projectionEngine = new ProjectionEngine();
    private final GeometryTextMetrics textMetrics;
    private final GraphCanvas canvas;
    private GraphTheme theme;
    private final List<PerformanceMeasurements.Summary> ledgerRows =
        new ArrayList<PerformanceMeasurements.Summary>();
    private final List<SettledReferenceSample> settledReferenceSamples =
        new ArrayList<SettledReferenceSample>();
    private final List<RawReferenceFrame> rawReferenceFrames =
        new ArrayList<RawReferenceFrame>();
    private boolean preCorrectionOverlapChecked;
    private boolean placementTriggersSampled;

    interface RawReferenceRouter {
        EdgePaths route(GraphProjection projection, LayoutPositions positions, GraphGeometry geometry,
            Set<ProjectedEndpointKey> visible);
    }

    GraphWorkspacePerformanceDiagnostic(final Path outputDirectory, final boolean strict, final NanoClock clock) {
        this(outputDirectory, strict, clock, new EdgeRouter()::route);
    }

    GraphWorkspacePerformanceDiagnostic(final Path outputDirectory, final boolean strict, final NanoClock clock,
            final RawReferenceRouter rawReferenceRouter) {
        this.outputDirectory = outputDirectory;
        this.rawReferenceRouter = Objects.requireNonNull(rawReferenceRouter, "rawReferenceRouter");
        this.strict = strict;
        this.clock = Objects.requireNonNull(clock, "clock");
        processStart = this.clock.nanoTime();
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
            new GraphWorkspacePerformanceDiagnostic(output, strict, new RelativeNanoClock()).run();
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
        placementTriggersSampled = false;
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
        requireUsableFrame(workerFrame, current, "full worker", PerformanceMeasurements.Stage.FULL_WORKER);
        validatePinConflicts(generated, workerFrame);
        validateBoundaryDiagnostics(workerFrame.boundaryDiagnostics(), PerformanceMeasurements.Stage.FULL_WORKER);

        final long workerHullStart = clock.nanoTime();
        final GraphGeometry workerHull = geometryEngine.computeHulls(current, workerFrame.positions(), textMetrics);
        final long workerHullEnd = clock.nanoTime();

        final CanvasState state = acceptedFirstFrameState(generation, current, workerFrame, workerHull);
        final long swapStart = clock.nanoTime();
        setCanvasStateBounded(state);
        final long swapEnd = clock.nanoTime();
        measurements.recordDuration(PerformanceMeasurements.Stage.EDT_SWAP, swapStart, swapEnd, warmup);
        final long acceptedEnd = clock.nanoTime();
        measurements.recordDuration(PerformanceMeasurements.Stage.ACCEPTED_BATCH_FIRST_FRAME,
            accepted.acceptedAtNanos(), acceptedEnd, warmup);
        if (workerHullEnd < workerHullStart) {
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
        measurePlacement(current, workerHull, workerFrame.positions(), warmup, measurements);
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
            requireUsableFrame(applied, projection, "direct apply", PerformanceMeasurements.Stage.MUTATION);

            final GraphGeometry rawHull = geometryEngine.computeHulls(projection, applied.positions(), textMetrics);
            if (!preCorrectionOverlapChecked
                    && generated.scenario() == GeneratedWorkspace.Scenario.TWO_PINNED_MAPS) {
                assertPinnedRootOverlap(projection, rawHull);
                preCorrectionOverlapChecked = true;
            }

            final BoundarySeparationResult corrected = correctionEngine.apply(projection, applied.positions(),
                textMetrics, request.pins());
            recordTiming(measurements, PerformanceMeasurements.Stage.SEPARATION,
                corrected.timings().separationNanos(), warmup);
            recordTiming(measurements, PerformanceMeasurements.Stage.HULL, corrected.timings().hullNanos(),
                warmup);
            recordTiming(measurements, PerformanceMeasurements.Stage.CORRECTION,
                corrected.timings().planNanos() + corrected.timings().applyNanos(), warmup);
            validateBoundaryDiagnostics(corrected.diagnostics(), PerformanceMeasurements.Stage.CORRECTION);

            final long forceStart = clock.nanoTime();
            final LayoutFrame forced = engine.step();
            final long forceEnd = clock.nanoTime();
            measurements.recordDuration(PerformanceMeasurements.Stage.FORCE, forceStart, forceEnd, warmup);
            requireUsableFrame(forced, projection, "direct force step", PerformanceMeasurements.Stage.FORCE);
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

    private static void recordTiming(final PerformanceMeasurements measurements,
            final PerformanceMeasurements.Stage stage, final long nanos, final boolean warmup) {
        if (warmup) {
            measurements.recordWarmup(stage, nanos);
        }
        else {
            measurements.recordMeasured(stage, nanos);
        }
    }

    private static void validateBoundaryDiagnostics(final BoundarySeparationDiagnostics diagnostics,
            final PerformanceMeasurements.Stage stage) {
        if (diagnostics.rounds() < 0
                || diagnostics.rounds() > BoundarySeparationCorrection.MAX_DISPLACEMENT_ROUNDS) {
            throw new DiagnosticFailure(stage, "Boundary displacement rounds outside the production bound: "
                + diagnostics.rounds());
        }
        if (!diagnostics.boundaryVerified() && !diagnostics.boundaryCovered()) {
            throw new DiagnosticFailure(stage,
                "Boundary diagnostics are neither verified nor covered; rounds=" + diagnostics.rounds()
                    + " detected=" + diagnostics.hullViolationsDetected()
                    + " residual=" + diagnostics.hullResidualViolations()
                    + " residualPairs=" + diagnostics.residualHullPairs()
                    + " displacementRms=" + diagnostics.displacementRms()
                    + " displacementMax=" + diagnostics.displacementMax()
                    + " worstMapDisplacement=" + diagnostics.worstMapDisplacement());
        }
    }

    private void measurePlacement(final GraphProjection projection, final GraphGeometry geometry,
            final LayoutPositions positions, final boolean warmup,
            final PerformanceMeasurements scenarioMeasurements) {
        final LabelFonts fonts = LabelFonts.from(GraphTheme.resolve(DisplaySettings.CanvasTheme.LIGHT));
        recordSettleFramePlacement(projection, geometry, positions, warmup, scenarioMeasurements, fonts);
        if (placementTriggersSampled) {
            return;
        }
        final Rectangle2D area = new Rectangle2D.Double(0.0, 0.0, PLACEMENT_AREA_WIDTH,
            PLACEMENT_AREA_HEIGHT);
        final Set<ProjectedEndpointKey> noPins = Collections.<ProjectedEndpointKey>emptySet();
        measurePlacementTrigger("positions-identity", scenarioMeasurements, projection, geometry, positions,
            noPins, fonts, area, warmup, 0);
        measurePlacementTrigger("zoom", scenarioMeasurements, projection, geometry, positions, noPins, fonts,
            area, warmup, 1);
        measurePlacementTrigger("viewport", scenarioMeasurements, projection, geometry, positions, noPins,
            fonts, area, warmup, 2);
        measurePlacementTrigger("forced-set", scenarioMeasurements, projection, geometry, positions, noPins,
            fonts, area, warmup, 3);
        if (!warmup) {
            placementTriggersSampled = true;
        }
    }

    /** One cold-cache placement for the settle frame, recorded as the scenario's placement sample. */
    private void recordSettleFramePlacement(final GraphProjection projection, final GraphGeometry geometry,
            final LayoutPositions positions, final boolean warmup,
            final PerformanceMeasurements measurements, final LabelFonts fonts) {
        final ScreenLabelPlacementCache cache = new ScreenLabelPlacementCache();
        final LabelPlacementRequest request = LabelPlacementRequest.of(projection, geometry, positions, 1.0,
            0.0, 0.0, new Rectangle2D.Double(0.0, 0.0, PLACEMENT_AREA_WIDTH, PLACEMENT_AREA_HEIGHT),
            Collections.<ProjectedEndpointKey>emptySet(), RenderingLevel.FULL);
        final long start = clock.nanoTime();
        cache.place(request, fonts);
        final long end = clock.nanoTime();
        measurements.recordDuration(PerformanceMeasurements.Stage.PLACEMENT, start, end, warmup);
    }

    private void measurePlacementTrigger(final String trigger,
            final PerformanceMeasurements scenarioMeasurements, final GraphProjection projection,
            final GraphGeometry geometry, final LayoutPositions positions,
            final Set<ProjectedEndpointKey> forced, final LabelFonts fonts, final Rectangle2D area,
            final boolean warmup, final int triggerIndex) {
        if (warmup) {
            return;
        }
        final ScreenLabelPlacementCache cache = new ScreenLabelPlacementCache();
        final PerformanceMeasurements triggerMeasurements = new PerformanceMeasurements(
            scenarioSuffix(scenarioMeasurements, trigger), WARMUP_SAMPLES, TIMED_SAMPLES);
        for (int sample = 0; sample < WARMUP_SAMPLES + TIMED_SAMPLES; sample++) {
            final boolean timed = sample >= WARMUP_SAMPLES;
            final double zoom = triggerIndex == 1 ? 1.0 + sample * 1e-4 : 1.0;
            final double centerX = triggerIndex == 2 ? sample * 0.5 : 0.0;
            final double centerY = triggerIndex == 2 ? -sample * 0.25 : 0.0;
            final LayoutPositions triggerPositions = triggerIndex == 0
                ? copyPositions(positions) : positions;
            final Set<ProjectedEndpointKey> triggerForced = triggerIndex == 3
                ? forcedForSample(projection, sample) : forced;
            final LabelPlacementRequest request = LabelPlacementRequest.of(projection, geometry,
                triggerPositions, zoom, centerX, centerY, area, triggerForced, RenderingLevel.FULL);
            final long start = clock.nanoTime();
            cache.place(request, fonts);
            final long end = clock.nanoTime();
            if (timed) {
                triggerMeasurements.recordMeasured(PerformanceMeasurements.Stage.PLACEMENT, end - start);
            }
            else {
                triggerMeasurements.recordWarmup(PerformanceMeasurements.Stage.PLACEMENT, end - start);
            }
        }
        ledgerRows.add(triggerMeasurements.summary(PerformanceMeasurements.Stage.PLACEMENT));
    }

    private static String scenarioSuffix(final PerformanceMeasurements scenarioMeasurements,
            final String trigger) {
        return scenarioMeasurements.scenario() + ":placement-" + trigger;
    }

    private static LayoutPositions copyPositions(final LayoutPositions positions) {
        return LayoutPositions.of(
            new LinkedHashMap<ProjectedNodeKey, LayoutPoint>(positions.nodes()),
            new LinkedHashMap<EnclosureHullKey, LayoutPoint>(positions.anchors()));
    }

    private static Set<ProjectedEndpointKey> forcedForSample(final GraphProjection projection, final int sample) {
        final int parity = sample % 2;
        final Set<ProjectedEndpointKey> forced = new LinkedHashSet<ProjectedEndpointKey>();
        int nodeIndex = 0;
        for (final ProjectedNode node : projection.nodes()) {
            if (nodeIndex >= FORCED_SAMPLE_SIZE) {
                break;
            }
            if (nodeIndex % 2 == parity) {
                forced.add(ProjectedEndpointKey.ofNode(node.key()));
            }
            nodeIndex++;
        }
        return forced;
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
            if (recordedByScenario(stage) && (measurements.warmupCount(stage) != generated.warmupCount()
                    || measurements.measuredCount(stage) != generated.measuredCount())) {
                throw new DiagnosticFailure(stage, "Scenario sample count is incomplete");
            }
        }
    }

    /**
     * The two Revision 8 edge stages are measured by {@link #runSettledReferenceFixture()}
     * on the committed settled-position fixture, never by the per-scenario loop; the raw
     * reference frames are only validated for bounded, flag-visible behaviour.
     */
    private static boolean recordedByScenario(final PerformanceMeasurements.Stage stage) {
        return stage != PerformanceMeasurements.Stage.EDGE_CONFLICT
            && stage != PerformanceMeasurements.Stage.EDGE_ROUTING;
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
        if (frame.conflicts().size() != TWO_PINNED_MAPS_EXPECTED_CONFLICTS) {
            throw new DiagnosticFailure(PerformanceMeasurements.Stage.FULL_WORKER,
                "Two-pinned-maps conflict count changed: expected " + TWO_PINNED_MAPS_EXPECTED_CONFLICTS
                    + " but measured " + frame.conflicts().size());
        }
        for (final BoundaryConflict conflict : frame.conflicts()) {
            if (conflict.firstMap().equals(conflict.secondMap())) {
                for (final PinProjection pin : conflict.blockingPins()) {
                    if (!pin.source().mapReferenceId().equals(conflict.firstMap())) {
                        throw new DiagnosticFailure(PerformanceMeasurements.Stage.FULL_WORKER,
                            "A same-map conflict listed a pin from another map");
                    }
                }
                continue;
            }
            if (conflict.blockingPins().size() != 2
                    || !conflict.blockingPins().get(0).source().nodeId().value().equals("m00-n0001")
                    || !conflict.blockingPins().get(1).source().nodeId().value().equals("m01-n0001")) {
                throw new DiagnosticFailure(PerformanceMeasurements.Stage.FULL_WORKER,
                    "Two-pinned-maps conflict did not retain both pin identities");
            }
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
            if (recordedByScenario(stage)) {
                ledgerRows.add(measurements.summary(stage,
                    PerformanceMeasurements.normalThresholdNanos(measurements.scenario(), stage),
                    PerformanceMeasurements.strictThresholdNanos(measurements.scenario(), stage), strict));
            }
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
        final int scenarioCount = GeneratedWorkspace.Scenario.values().length;
        final int scenarioStages = PerformanceMeasurements.Stage.values().length - 2;
        final int scenarioRows = scenarioCount * scenarioStages;
        final int placementTriggerRows = scenarioCount * PLACEMENT_TRIGGER_COUNT;
        if (ledgerRows.size() != scenarioRows + placementTriggerRows) {
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
        final long end = clock.nanoTime() + TimeUnit.SECONDS.toNanos(5L);
        while (clock.nanoTime() < end) {
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
                            graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
                                RenderingHints.VALUE_RENDER_SPEED);
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

    NanoClock clock() {
        return clock;
    }

    /**
     * Runs the Revision 8 A7a measurement: the two new stages on the committed
     * settled-position fixture, plus the production worker and first-frame gates. Every
     * sample is exhaustion-free; the calibration run records the counted work.
     */
    PerformanceMeasurements runSettledReferenceFixture() {
        final SettledReferenceFixture fixture = SettledReferenceFixture.load();
        final GeneratedWorkspace.Scenario scenario = GeneratedWorkspace.Scenario.REFERENCE_2000_5000;
        final PerformanceMeasurements measurements = new PerformanceMeasurements(scenario.wireName(),
            scenario.warmupCount(), scenario.measuredCount());
        final Set<ProjectedEndpointKey> visible = ProjectedEndpointVisibility.visibleEndpoints(
            fixture.projection().nodes(), fixture.projection().enclosures());
        final LayoutWorker worker = fixture.newReplayWorker();
        try {
            final int total = scenario.warmupCount() + scenario.measuredCount();
            for (int index = 0; index < total; index++) {
                checkDeadline();
                final boolean warmup = index < scenario.warmupCount();
                final long sampleStart = clock.nanoTime();
                final long workerStart = clock.nanoTime();
                final LayoutFrame workerFrame = await(worker.submit(fixture.request()),
                    "settled reference worker submit");
                final long workerEnd = clock.nanoTime();
                measurements.recordDuration(PerformanceMeasurements.Stage.FULL_WORKER,
                    workerStart, workerEnd, warmup);
                requireUsableFrame(workerFrame, fixture.projection(), "settled reference worker",
                    PerformanceMeasurements.Stage.FULL_WORKER);

                final GraphGeometry workerHull = geometryEngine.computeHulls(fixture.projection(),
                    workerFrame.positions(), textMetrics);
                final BoundarySeparationResult settledBoundary = correctionEngine.apply(
                    fixture.projection(), workerFrame.positions(), textMetrics, fixture.pins());
                final long edgeConflictStart = clock.nanoTime();
                final EdgeConflictResult settledEdge = new EdgeConflictCorrection().apply(
                    fixture.projection(), settledBoundary.positions(), textMetrics, fixture.pins(),
                    visible, settledBoundary);
                final long edgeConflictEnd = clock.nanoTime();
                measurements.recordDuration(PerformanceMeasurements.Stage.EDGE_CONFLICT,
                    edgeConflictStart, edgeConflictEnd, warmup);
                if (settledEdge.diagnostics().detectionTruncated()
                        || settledEdge.diagnostics().verificationBudgetExhausted()
                        || settledEdge.diagnostics().indexBudgetExhausted()) {
                    measurements.recordDiscard(PerformanceMeasurements.Stage.EDGE_CONFLICT);
                }

                final long edgeRoutingStart = clock.nanoTime();
                final EdgePaths settledPaths = new EdgeRouter().route(fixture.projection(),
                    workerFrame.positions(), workerHull, visible);
                final long edgeRoutingEnd = clock.nanoTime();
                measurements.recordDuration(PerformanceMeasurements.Stage.EDGE_ROUTING,
                    edgeRoutingStart, edgeRoutingEnd, warmup);
                if (settledPaths.diagnostics().routeBudgetExhausted()
                        || settledPaths.diagnostics().detectionTruncated()) {
                    measurements.recordDiscard(PerformanceMeasurements.Stage.EDGE_ROUTING);
                }

                final CanvasState state = acceptedFirstFrameState(index + 1L, fixture.projection(),
                    workerFrame, workerHull);
                setCanvasStateBounded(state);
                final long sampleEnd = clock.nanoTime();
                measurements.recordDuration(PerformanceMeasurements.Stage.ACCEPTED_BATCH_FIRST_FRAME,
                    sampleStart, sampleEnd, warmup);

                if (!warmup) {
                    settledReferenceSamples.add(new SettledReferenceSample(settledEdge.diagnostics(),
                        settledPaths.diagnostics()));
                }
            }
            return measurements;
        }
        finally {
            worker.close();
        }
    }

    /**
     * Runs the Revision 8 A7b measurement: bounded, flag-visible behaviour on the raw
     * divergent reference frames. No millisecond threshold is asserted here.
     */
    void runRawReferenceFixture() {
        final GeneratedWorkspace workspace = GeneratedWorkspace.forScenario("reference-2000-5000");
        final GraphProjection projection = workspace.projection();
        final LayoutRequest request = LayoutRequest.of(workspace.document().id(), projection,
            ProjectionDiff.between(projection, projection), projection.pins());
        final Set<ProjectedEndpointKey> visible = ProjectedEndpointVisibility.visibleEndpoints(
            projection.nodes(), projection.enclosures());
        final LayoutWorker worker = new LayoutWorker(LayoutCalibration.spikeDefaults());
        try {
            for (int index = 0; index < RAW_REFERENCE_FRAMES; index++) {
                final long start = clock.nanoTime();
                final LayoutFrame frame = await(worker.submit(request), "raw reference worker submit");
                requireUsableFrame(frame, projection, "raw reference frame",
                    PerformanceMeasurements.Stage.FULL_WORKER);
                final GraphGeometry hull = geometryEngine.computeHulls(projection, frame.positions(),
                    textMetrics);
                final EdgePaths paths = rawReferenceRouter.route(projection, frame.positions(), hull,
                    visible);
                final long end = clock.nanoTime();
                final long duration = PerformanceMeasurements.checkedDuration(start, end);
                rawReferenceFrames.add(new RawReferenceFrame(duration,
                    frame.edgeConflictDiagnostics(), paths.diagnostics()));
            }
        }
        finally {
            worker.close();
        }
    }

    List<SettledReferenceSample> settledReferenceSamples() {
        return Collections.unmodifiableList(settledReferenceSamples);
    }

    List<RawReferenceFrame> rawReferenceFrames() {
        return Collections.unmodifiableList(rawReferenceFrames);
    }

    long rawReferenceMaxFrameNanos() {
        long maximum = 0L;
        for (final RawReferenceFrame frame : rawReferenceFrames) {
            maximum = Math.max(maximum, frame.durationNanos());
        }
        return maximum;
    }

    public static final class SettledReferenceSample {
        private final EdgeConflictDiagnostics edgeDiagnostics;
        private final EdgeRouteDiagnostics routeDiagnostics;

        private SettledReferenceSample(final EdgeConflictDiagnostics edgeDiagnostics,
                final EdgeRouteDiagnostics routeDiagnostics) {
            this.edgeDiagnostics = edgeDiagnostics;
            this.routeDiagnostics = routeDiagnostics;
        }

        public EdgeConflictDiagnostics edgeDiagnostics() { return edgeDiagnostics; }
        public EdgeRouteDiagnostics routeDiagnostics() { return routeDiagnostics; }
    }

    public static final class RawReferenceFrame {
        private final long durationNanos;
        private final EdgeConflictDiagnostics edgeDiagnostics;
        private final EdgeRouteDiagnostics routeDiagnostics;

        private RawReferenceFrame(final long durationNanos,
                final EdgeConflictDiagnostics edgeDiagnostics,
                final EdgeRouteDiagnostics routeDiagnostics) {
            this.durationNanos = durationNanos;
            this.edgeDiagnostics = edgeDiagnostics;
            this.routeDiagnostics = routeDiagnostics;
        }

        public long durationNanos() { return durationNanos; }
        public EdgeConflictDiagnostics edgeDiagnostics() { return edgeDiagnostics; }
        public EdgeRouteDiagnostics routeDiagnostics() { return routeDiagnostics; }
    }

    CanvasState acceptedFirstFrameState(final long generation, final GraphProjection projection,
            final LayoutFrame frame, final GraphGeometry geometry) {
        return CanvasState.of(generation, projection, frame, geometry,
            new EdgeRouter().route(projection, frame.positions(), geometry,
                ProjectedEndpointVisibility.visibleEndpoints(projection.nodes(), projection.enclosures())),
            OperationalStatus.SETTLING);
    }

    void validateFrameCoverage(final LayoutFrame frame, final GraphProjection projection,
            final String operation) {
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(projection, "projection");
        Objects.requireNonNull(operation, "operation");
        final Set<ProjectedNodeKey> expectedNodeKeys = new HashSet<ProjectedNodeKey>();
        for (final org.freeplane.plugin.graph.projection.ProjectedNode node : projection.nodes()) {
            expectedNodeKeys.add(node.key());
        }
        if (!expectedNodeKeys.equals(frame.positions().nodes().keySet())) {
            throw new IllegalArgumentException(operation + " node coverage differs");
        }
        final Set<EnclosureHullKey> expectedAnchorKeys = new HashSet<EnclosureHullKey>();
        for (final org.freeplane.plugin.graph.projection.ProjectedEnclosure enclosure : projection.enclosures()) {
            expectedAnchorKeys.add(enclosure.hullKey());
        }
        if (!expectedAnchorKeys.equals(frame.positions().anchors().keySet())) {
            throw new IllegalArgumentException(operation + " anchor coverage differs");
        }
    }

    private void requireUsableFrame(final LayoutFrame frame, final GraphProjection projection,
            final String operation, final PerformanceMeasurements.Stage stage) {
        if (frame == null || frame.failed()) {
            throw new DiagnosticFailure(stage, operation + " returned a failed frame");
        }
        try {
            validateFrameCoverage(frame, projection, operation);
        }
        catch (final IllegalArgumentException failure) {
            throw new DiagnosticFailure(stage, failure.getMessage(), failure);
        }
        for (final org.freeplane.plugin.graph.geometry.LayoutPoint point : frame.positions().nodes().values()) {
            if (!Double.isFinite(point.x()) || !Double.isFinite(point.y())) {
                throw new DiagnosticFailure(stage,
                    operation + " returned a non-finite node position");
            }
        }
        for (final org.freeplane.plugin.graph.geometry.LayoutPoint point : frame.positions().anchors().values()) {
            if (!Double.isFinite(point.x()) || !Double.isFinite(point.y())) {
                throw new DiagnosticFailure(stage,
                    operation + " returned a non-finite enclosure position");
            }
        }
        final EdgeConflictDiagnostics edgeDiagnostics = frame.edgeConflictDiagnostics();
        if (edgeDiagnostics == null) {
            throw new DiagnosticFailure(stage, operation + " returned no edge-conflict diagnostics");
        }
        // The pinned Revision 7 production bounds (the constants are package-private in the
        // layout package; these literals are the specification pins).
        if (edgeDiagnostics.rounds() > 4 || edgeDiagnostics.verifications() > 64) {
            throw new DiagnosticFailure(stage,
                operation + " edge-conflict diagnostics exceed the production bounds: rounds="
                    + edgeDiagnostics.rounds() + " verifications=" + edgeDiagnostics.verifications());
        }
    }

    private void checkDeadline() {
        final long elapsed = PerformanceMeasurements.checkedDuration(processStart, clock.nanoTime());
        if (elapsed > PROCESS_DEADLINE_NANOS) {
            throw new DiagnosticFailure(null, "Forty-minute diagnostic process deadline exceeded");
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
