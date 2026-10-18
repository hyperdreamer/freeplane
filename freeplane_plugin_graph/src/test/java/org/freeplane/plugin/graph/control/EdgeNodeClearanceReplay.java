package org.freeplane.plugin.graph.control;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JViewport;
import javax.swing.RepaintManager;
import javax.swing.SwingUtilities;

import org.freeplane.plugin.graph.adapter.EdtExecutor;
import org.freeplane.plugin.graph.canvas.GraphCanvas;
import org.freeplane.plugin.graph.canvas.GraphViewport;
import org.freeplane.plugin.graph.canvas.PlacedLabel;
import org.freeplane.plugin.graph.canvas.RenderingLevel;
import org.freeplane.plugin.graph.geometry.AwtGeometryTextMetrics;
import org.freeplane.plugin.graph.geometry.GraphGeometryEngine;
import org.freeplane.plugin.graph.geometry.HullGeometry;
import org.freeplane.plugin.graph.geometry.EdgeRouter;
import org.freeplane.plugin.graph.geometry.EdgePath;
import org.freeplane.plugin.graph.geometry.EdgePaths;
import org.freeplane.plugin.graph.geometry.EdgeRouteDiagnostics;
import org.freeplane.plugin.graph.geometry.GraphGeometry;
import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.geometry.LayoutPositions;
import org.freeplane.plugin.graph.geometry.NodeGeometry;
import org.freeplane.plugin.graph.layout.EdgeConflictDiagnostics;
import org.freeplane.plugin.graph.layout.LayoutEngine;
import org.freeplane.plugin.graph.layout.LayoutFrame;
import org.freeplane.plugin.graph.layout.LayoutRequest;
import org.freeplane.plugin.graph.layout.LayoutWorker;
import org.freeplane.plugin.graph.layout.PerceptualIdlePolicy;
import org.freeplane.plugin.graph.projection.CanonicalKeys;
import org.freeplane.plugin.graph.projection.EdgeContributor;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.PinProjection;
import org.freeplane.plugin.graph.projection.ProjectedEdge;
import org.freeplane.plugin.graph.projection.ProjectedEdgeKey;
import org.freeplane.plugin.graph.projection.ProjectedEnclosure;
import org.freeplane.plugin.graph.projection.ProjectedEndpointKey;
import org.freeplane.plugin.graph.projection.ProjectedNode;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.freeplane.plugin.graph.projection.ProjectionDiff;
import org.freeplane.plugin.graph.projection.RelationshipResolution;
import org.freeplane.plugin.graph.projection.input.ConnectorDescriptor;
import org.freeplane.plugin.graph.projection.input.ConnectorSnapshot;
import org.freeplane.plugin.graph.projection.input.SafeNodeLabel;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;
import org.freeplane.plugin.graph.workspace.model.WorkspaceId;

/**
 * Test helper for the E3 column fixture of the edge/node clearance specification (§6.4): the
 * exact raw replay inputs, a replay {@link LayoutEngine} whose frames carry the unchanged column,
 * an adapter onto the real installed {@link LayoutWorker}, the independent final-path oracle and
 * exact-bit state digest.
 *
 * <p>This source is compiled into the module's test output only. It references nothing but JDK
 * types and installed production classes, reports failures as {@link AssertionError}, and is
 * additionally defined into the running application's control loader by the test-only evidence
 * agent, so it must stay Java 8 compatible and must never be packaged into the graph bundle.</p>
 */
public final class EdgeNodeClearanceReplay {
    /** Stable workspace identity of the replay run (spec §8.4; Task 5 and Task 7 share it). */
    static final String WORKSPACE_ID = "00000000-0000-0000-0000-000000000100";

    private static final MapReferenceId MAP =
        MapReferenceId.of("00000000-0000-0000-0000-000000000001");
    private static final long GENERATION = 1L;
    private static final double ACTUAL_RADIUS = 8.0;
    private static final double REQUIRED_CLEARANCE = 3.0;
    /** The only measurement epsilon, matching the placement and routing measurements. */
    private static final double CLEARANCE_TOLERANCE = 1e-9;

    private static final int A_INDEX = 0;
    private static final int B_INDEX = 1;
    private static final int N_INDEX = 2;

    private static final String[] NODE_IDS = {
        "A", "B", "N", "O1", "O2", "O3", "O4", "O5", "O6",
    };

    /** E3 raw centres (spec §6.4): A, B, N, then the six remaining column nodes. */
    private static final double[] NODE_CENTRES = {
        -100.0, 0.0,
        100.0, 0.0,
        0.0, 0.0,
        0.0, -22.0,
        0.0, -44.0,
        0.0, -66.0,
        0.0, 22.0,
        0.0, 44.0,
        0.0, 66.0,
    };

    /** The accepted E3 placement: N is the only moved node. */
    private static final LayoutPoint N_REPAIRED = LayoutPoint.of(-25.0, -11.0);
    private static final LayoutPoint STRAIGHT_START = LayoutPoint.of(-92.0, 0.0);
    private static final LayoutPoint STRAIGHT_END = LayoutPoint.of(92.0, 0.0);

    private EdgeNodeClearanceReplay() {
    }

    /** The exact E3 projection: nine persisted nodes and the sole native {@code A -> B} edge. */
    static GraphProjection projection() {
        final List<ProjectedNodeKey> keys = new ArrayList<ProjectedNodeKey>(NODE_IDS.length);
        final List<ProjectedNode> nodes = new ArrayList<ProjectedNode>(NODE_IDS.length);
        for (int index = 0; index < NODE_IDS.length; index++) {
            final String id = NODE_IDS[index];
            final ProjectedNodeKey key = ProjectedNodeKey.of(SourceNodeKey.persisted(
                NodeReference.of(MAP, PersistedNodeId.of(id))));
            keys.add(key);
            nodes.add(ProjectedNode.of(key, SafeNodeLabel.of(id, id), "map", false));
        }
        final ProjectedEndpointKey first = ProjectedEndpointKey.ofNode(keys.get(A_INDEX));
        final ProjectedEndpointKey second = ProjectedEndpointKey.ofNode(keys.get(B_INDEX));
        final ConnectorDescriptor descriptor = ConnectorDescriptor.of(
            first.node().get().source(),
            NodeReference.of(second.mapReferenceId(), PersistedNodeId.of(NODE_IDS[B_INDEX])),
            false, false, "source", "middle", "target");
        final ProjectedEdge edge = ProjectedEdge.of(ProjectedEdgeKey.of(first, second),
            Collections.singletonList(EdgeContributor.nativeConnector(
                ConnectorSnapshot.of(0, descriptor), first, second)));
        return GraphProjection.projected(GENERATION, nodes,
            Collections.<ProjectedEnclosure>emptyList(), Collections.singletonList(edge),
            Collections.<RelationshipResolution>emptyList(), Collections.<PinProjection>emptyList());
    }

    /**
     * The raw E3 centres: {@code N} stays at the chord centre and every column node keeps its
     * original coordinate, so the replay engine never hands a repaired frame to the worker.
     */
    static LayoutPositions rawPositions() {
        final Map<ProjectedNodeKey, LayoutPoint> nodes =
            new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        final List<ProjectedNodeKey> keys = nodeKeys();
        for (int index = 0; index < keys.size(); index++) {
            nodes.put(keys.get(index),
                LayoutPoint.of(NODE_CENTRES[index * 2], NODE_CENTRES[index * 2 + 1]));
        }
        return LayoutPositions.of(nodes, Collections.<EnclosureHullKey, LayoutPoint>emptyMap());
    }

    /** A real {@link LayoutWorker} adapter backed by the raw E3 replay engine. */
    static LayoutSettleLoop.FrameStepper newStepper() {
        return new ReplayStepper();
    }

    /**
     * The complete E3 acceptance assertion of spec §§6.1 and 6.4 against the first state the settle
     * loop actually published: exact movement, diagnostics, geometry agreement, expected straight
     * edge coverage with real rim attachments, retained caps and exhaustive same-state clearance.
     */
    static void assertE3(final CanvasState state) {
        require(state != null, "E3 canvas state is missing");
        require(state.status() != OperationalStatus.FAILED,
            "E3 publication failed with status " + state.status());
        final LayoutFrame layout = state.layout();
        require(!layout.failed(), "E3 layout frame is marked failed");
        require(layout.verified(), "E3 layout frame carries no verified residual");
        require(layout.residualViolations() == 0,
            "E3 layout residual violations: " + layout.residualViolations());
        require(state.generation() == GENERATION,
            "E3 generation " + state.generation() + " differs from " + GENERATION);
        require(projection().equals(state.projection()), "E3 projection differs from the fixture");

        final GraphProjection fixture = projection();
        final LayoutPositions raw = rawPositions();
        final LayoutPositions published = layout.positions();
        require(raw.anchors().equals(published.anchors()), "E3 anchors changed");

        final List<ProjectedNodeKey> keys = nodeKeys();
        for (int index = 0; index < keys.size(); index++) {
            final ProjectedNodeKey key = keys.get(index);
            final LayoutPoint actual = published.nodes().get(key);
            require(actual != null, "E3 published positions miss " + NODE_IDS[index]);
            requireFinite(actual, "E3 published position " + NODE_IDS[index]);
            final LayoutPoint expected = index == N_INDEX ? N_REPAIRED
                : raw.nodes().get(key);
            require(expected.equals(actual), "E3 position of " + NODE_IDS[index] + " is " + actual
                + " instead of " + expected);
        }

        final GraphGeometry geometry = state.geometry();
        require(geometry.nodes().keySet().equals(new LinkedHashSet<ProjectedNodeKey>(keys)),
            "E3 geometry does not cover exactly the fixture nodes");
        for (final ProjectedNodeKey key : keys) {
            final NodeGeometry node = geometry.nodes().get(key);
            require(node.radius() == ACTUAL_RADIUS,
                "E3 actual radius of " + CanonicalKeys.node(key) + " is " + node.radius());
            require(node.center().equals(published.nodes().get(key)),
                "E3 geometry centre of " + CanonicalKeys.node(key) + " differs from the layout");
        }

        assertCorrection(layout.edgeConflictDiagnostics(), keys);

        final EdgePaths paths = state.edgePaths();
        final Set<ProjectedEdgeKey> expectedEdges = new LinkedHashSet<ProjectedEdgeKey>();
        for (final ProjectedEdge edge : fixture.edges()) {
            expectedEdges.add(edge.key());
        }
        require(paths.byEdge().keySet().equals(expectedEdges),
            "E3 path coverage is " + paths.byEdge().keySet() + " instead of " + expectedEdges);
        final ProjectedEdge edge = fixture.edges().get(0);
        final EdgePath straight = paths.byEdge().get(edge.key());
        require(straight.shape() == EdgePath.Shape.STRAIGHT,
            "E3 path shape is " + straight.shape());
        require(straight.samples().size() == 2, "E3 straight path sample count is "
            + straight.samples().size());
        require(straight.samples().equals(Arrays.asList(STRAIGHT_START, STRAIGHT_END)),
            "E3 straight samples are " + straight.samples());
        require(straight.length() == 184.0, "E3 straight length is " + straight.length());
        final LayoutPoint startAttachment = geometry.edgeAttachment(edge.first(),
            geometry.nodes().get(edge.second().node().get()).center());
        final LayoutPoint endAttachment = geometry.edgeAttachment(edge.second(),
            geometry.nodes().get(edge.first().node().get()).center());
        require(startAttachment.equals(STRAIGHT_START),
            "E3 first rim attachment is " + startAttachment);
        require(endAttachment.equals(STRAIGHT_END), "E3 second rim attachment is " + endAttachment);
        require(straight.samples().get(0).equals(startAttachment)
            && straight.samples().get(1).equals(endAttachment),
            "E3 straight path does not attach to the actual rims");

        final EdgeRouteDiagnostics route = paths.diagnostics();
        require(route.edgesStraight() == 1 && route.edgesBow() == 0 && route.edgesDogleg() == 0
                && route.edgesFallback() == 0,
            "E3 route classification is straight/bow/dogleg/fallback " + route.edgesStraight() + "/"
                + route.edgesBow() + "/" + route.edgesDogleg() + "/" + route.edgesFallback());
        require(route.pathCrossingsDetected() == 0 && route.pathCrossingsResidual().isEmpty(),
            "E3 route reports path crossings");
        require(route.pathHullPenetrations() == 0, "E3 route reports hull penetrations");
        require(!route.routeBudgetExhausted(), "E3 route reports budget exhaustion");
        require(!route.detectionTruncated(), "E3 route reports truncated detection");

        requireClearance(fixture, geometry, paths);
    }

    static void requireClearance(final GraphProjection projection, final GraphGeometry geometry, final EdgePaths paths) {
        final double minimum = minimumClearance(projection, geometry, paths);
        require(Double.isFinite(minimum) && minimum >= REQUIRED_CLEARANCE - CLEARANCE_TOLERANCE,
            "E3 minimum clearance " + minimum + " is below " + REQUIRED_CLEARANCE);
    }

    /**
     * The independent final-path oracle of spec §6.1 over the same published geometry: exact
     * expected edge coverage, finite positions and samples, and the clamped point/closed-segment
     * distance minus the actual radius of every unrelated node disc. Only the edge's own attached
     * node endpoints are excluded; fallback paths are checked exactly like any other shape.
     */
    static double minimumClearance(final GraphProjection projection, final GraphGeometry geometry,
            final EdgePaths paths) {
        require(projection != null && geometry != null && paths != null,
            "E3 clearance comparison is missing its state");
        final Set<ProjectedEdgeKey> expectedEdges = new LinkedHashSet<ProjectedEdgeKey>();
        for (final ProjectedEdge edge : projection.edges()) {
            expectedEdges.add(edge.key());
        }
        require(paths.byEdge().keySet().equals(expectedEdges), "E3 oracle path coverage is "
            + paths.byEdge().keySet() + " instead of " + expectedEdges);
        final Set<ProjectedNodeKey> expectedNodes = new LinkedHashSet<ProjectedNodeKey>();
        for (final ProjectedNode node : projection.nodes()) {
            expectedNodes.add(node.key());
        }
        require(geometry.nodes().keySet().equals(expectedNodes), "E3 oracle node geometry coverage differs");
        for (final Map.Entry<ProjectedNodeKey, NodeGeometry> entry : geometry.nodes().entrySet()) {
            require(entry.getValue() != null, "E3 oracle has no node geometry");
            requireFinite(entry.getValue().center(),
                "E3 oracle centre of " + CanonicalKeys.node(entry.getKey()));
            final double radius = entry.getValue().radius();
            require(Double.isFinite(radius) && radius > 0.0,
                "E3 oracle radius of " + CanonicalKeys.node(entry.getKey()) + " is " + radius);
        }
        double minimum = Double.POSITIVE_INFINITY;
        for (final Map.Entry<ProjectedEdgeKey, EdgePath> entry : paths.byEdge().entrySet()) {
            final List<LayoutPoint> samples = entry.getValue().samples();
            require(samples.size() >= 2, "E3 oracle path " + entry.getKey() + " has fewer than "
                + "two samples");
            for (final LayoutPoint sample : samples) {
                requireFinite(sample, "E3 oracle sample of " + entry.getKey());
            }
            final Set<ProjectedNodeKey> attached = attachedNodes(projection, entry.getKey());
            for (int index = 1; index < samples.size(); index++) {
                final LayoutPoint from = samples.get(index - 1);
                final LayoutPoint to = samples.get(index);
                for (final Map.Entry<ProjectedNodeKey, NodeGeometry> node
                        : geometry.nodes().entrySet()) {
                    if (attached.contains(node.getKey())) {
                        continue;
                    }
                    minimum = Math.min(minimum, segmentDistance(node.getValue().center(), from, to)
                        - node.getValue().radius());
                }
            }
        }
        require(Double.isFinite(minimum), "E3 oracle measured no finite path segment clearance");
        return minimum;
    }

    /**
     * A deterministic digest of the retained published frame: sorted canonical node keys with the
     * exact normalised bits of every centre and radius, sorted canonical edge keys with their shape
     * classification and every exact-bit sample. Explicit counts and length prefixes keep distinct
     * states from colliding through ambiguous concatenation.
     */
    static String stateDigest(final CanvasState state) {
        require(state != null, "E3 digest needs a published state");
        final List<ProjectedNodeKey> keys = new ArrayList<ProjectedNodeKey>(
            state.layout().positions().nodes().keySet());
        Collections.sort(keys, new java.util.Comparator<ProjectedNodeKey>() {
            @Override
            public int compare(final ProjectedNodeKey first, final ProjectedNodeKey second) {
                return CanonicalKeys.node(first).compareTo(CanonicalKeys.node(second));
            }
        });
        final List<ProjectedEdgeKey> edgeKeys = new ArrayList<ProjectedEdgeKey>(
            state.edgePaths().byEdge().keySet());
        Collections.sort(edgeKeys, new java.util.Comparator<ProjectedEdgeKey>() {
            @Override
            public int compare(final ProjectedEdgeKey first, final ProjectedEdgeKey second) {
                return CanonicalKeys.edge(first).compareTo(CanonicalKeys.edge(second));
            }
        });
        final StringBuilder builder = new StringBuilder();
        appendLong(builder, state.generation());
        builder.append("nodes:").append(keys.size()).append(';');
        for (final ProjectedNodeKey key : keys) {
            final String name = CanonicalKeys.node(key);
            appendText(builder, name);
            final LayoutPoint point = state.layout().positions().nodes().get(key);
            require(point != null, "E3 digest has no published centre for " + name);
            appendDouble(builder, point.x());
            appendDouble(builder, point.y());
            final NodeGeometry node = state.geometry().nodes().get(key);
            require(node != null, "E3 digest has no geometry for " + name);
            appendDouble(builder, node.center().x());
            appendDouble(builder, node.center().y());
            appendDouble(builder, node.radius());
        }
        final EdgeRouteDiagnostics classification = state.edgePaths().diagnostics();
        appendLong(builder, classification.edgesStraight());
        appendLong(builder, classification.edgesBow());
        appendLong(builder, classification.edgesDogleg());
        appendLong(builder, classification.edgesFallback());
        builder.append("edges:").append(edgeKeys.size()).append(';');
        for (final ProjectedEdgeKey key : edgeKeys) {
            appendText(builder, CanonicalKeys.edge(key));
            final EdgePath path = state.edgePaths().byEdge().get(key);
            appendText(builder, path.shape().name());
            builder.append(path.samples().size()).append(';');
            for (final LayoutPoint sample : path.samples()) {
                appendDouble(builder, sample.x());
                appendDouble(builder, sample.y());
            }
        }
        return sha256(builder.toString());
    }

    /** Posts a cancellable request; the coordinator never blocks the EDT on a future. */
    static <T> T onEdt(final Callable<T> operation, final long deadlineNanos) throws Exception {
        remaining(deadlineNanos);
        if (SwingUtilities.isEventDispatchThread()) {
            return operation.call();
        }
        final FutureTask<T> request = new FutureTask<T>(() -> {
            remaining(deadlineNanos);
            return operation.call();
        });
        SwingUtilities.invokeLater(request);
        try {
            return await(request, deadlineNanos);
        }
        catch (Exception | Error failure) {
            // FutureTask.run() is a no-op after cancellation, including a task still on the queue.
            request.cancel(false);
            throw failure;
        }
    }

    private static long remaining(final long deadlineNanos) throws TimeoutException {
        final long value = deadlineNanos - System.nanoTime();
        if (value <= 0L) {
            throw new TimeoutException("E3 evidence deadline expired");
        }
        return value;
    }

    private static <T> T await(final Future<T> task, final long deadlineNanos) throws Exception {
        try {
            return task.get(remaining(deadlineNanos), TimeUnit.NANOSECONDS);
        }
        catch (ExecutionException failure) {
            throw propagate(unwrap(failure));
        }
        catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw failure;
        }
    }

    private static Throwable unwrap(final Throwable failure) {
        Throwable cause = failure;
        while ((cause instanceof ExecutionException || cause instanceof InvocationTargetException
                || cause instanceof java.util.concurrent.CompletionException) && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    private static Exception propagate(final Throwable failure) {
        if (failure instanceof Error) {
            throw (Error) failure;
        }
        return failure instanceof Exception ? (Exception) failure : new Exception(failure);
    }

    /** Immutable copies of every mutable AWT value, plus the two actual retained references. */
    static final class Snapshot {
        private final CanvasState modelState;
        private final CanvasState canvasState;
        private final long generation;
        private final String digest;
        private final Rectangle window;
        private final Rectangle visibleCanvas;
        private final Rectangle2D.Double drawing;
        private final GraphViewport viewport;
        private final Dimension canvasSize;
        private final Point canvasLocation;
        private final Point viewPosition;

        Snapshot(final CanvasState modelState, final CanvasState canvasState, final long generation,
                final String digest, final Rectangle window, final Rectangle visibleCanvas,
                final Rectangle2D drawing, final GraphViewport viewport, final Dimension canvasSize,
                final Point canvasLocation, final Point viewPosition) {
            this.modelState = Objects.requireNonNull(modelState, "modelState");
            this.canvasState = Objects.requireNonNull(canvasState, "canvasState");
            this.generation = generation;
            this.digest = Objects.requireNonNull(digest, "digest");
            this.window = new Rectangle(window);
            this.visibleCanvas = new Rectangle(visibleCanvas);
            this.drawing = new Rectangle2D.Double(drawing.getX(), drawing.getY(),
                drawing.getWidth(), drawing.getHeight());
            this.viewport = Objects.requireNonNull(viewport, "viewport");
            this.canvasSize = new Dimension(canvasSize);
            this.canvasLocation = new Point(canvasLocation);
            this.viewPosition = new Point(viewPosition);
        }
    }

    static void requireSameSnapshot(final Snapshot before, final Snapshot after) {
        require(before.modelState == before.canvasState && after.modelState == after.canvasState
                && before.modelState == after.modelState && before.canvasState == after.canvasState,
            "E3 snapshot state references changed");
        require(before.generation == after.generation, "E3 snapshot generation changed");
        require(before.digest.equals(after.digest), "E3 snapshot digest changed");
        require(before.window.equals(after.window) && before.visibleCanvas.equals(after.visibleCanvas)
                && before.drawing.equals(after.drawing), "E3 snapshot screen bounds changed");
        require(before.viewport.equals(after.viewport) && before.canvasSize.equals(after.canvasSize)
                && before.canvasLocation.equals(after.canvasLocation) && before.viewPosition.equals(after.viewPosition),
            "E3 snapshot viewport changed");
    }

    /**
     * Entry point defined by the evidence agent into the installed control loader. Task 7 supplies
     * the fresh launch/readiness properties; only its outer launcher may publish result.json.
     *
     * <p>UTF-8 launch.properties keys: attemptId, launchEpochMillis, deadlineEpochMillis,
     * repositoryRoot, sourceOid, workingDiffSha256, javaVersion, command, agentJarSha256,
     * graphBundleSha256, mapFixtureSha256 and workspaceFixtureSha256. graphBundleSha256 hashes
     * the installed BIN/plugins/org.freeplane.plugin.graph/lib/plugin-&lt;version&gt;.jar bytes.
     * Other launch provenance is copied verbatim. agent-readiness.properties must contain the same attemptId and
     * actionReady/helperReady .disableProperty=true and .securityManagerAbsent=true checks.
     * Other agent readiness/loader fields are copied into the numeric artifact.</p>
     */
    public static void run(final Object window, final Path attemptDirectory) throws Exception {
        require(!SwingUtilities.isEventDispatchThread(), "E3 coordinator must run off EDT");
        final Evidence evidence = new Evidence(window, attemptDirectory.toAbsolutePath().normalize());
        Throwable failure = null;
        try {
            evidence.capture();
        }
        catch (Throwable problem) {
            failure = unwrap(problem);
        }
        finally {
            final boolean interrupted = Thread.interrupted();
            try {
                failure = evidence.cleanup(failure);
            }
            catch (Throwable cleanupFailure) {
                failure = combine(failure, unwrap(cleanupFailure));
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        if (failure != null) {
            try {
                Files.deleteIfExists(evidence.directory.resolve("e3-window.png"));
            }
            catch (Throwable deletion) {
                failure = combine(failure, deletion);
            }
        }
        try {
            final Map<String, Object> result = record("schemaVersion", 1, "attemptId", evidence.attemptId,
                "stage", evidence.stage, "result", failure == null ? "PASS" : "FAIL",
                "error", errorRecord(failure), "cleanup", evidence.cleanupResult,
                "provenance", propertiesRecord(evidence.launch),
                "screenshotSha256", evidence.imageHash, "stateSha256", evidence.stateHash);
            atomicJson(evidence.directory.resolve("helper-result.json"), result);
        }
        catch (Throwable staging) {
            failure = combine(failure, staging);
        }
        if (failure != null) {
            throw propagate(failure);
        }
    }

    private static final class Evidence {
        private final Object suppliedWindow;
        private final Path directory;
        private final ExecutorService captureExecutor = Executors.newSingleThreadExecutor(daemonThreads("e3-capture"));
        // Independent slots let controller cleanup still start if physical loop close stalls.
        private final ExecutorService cleanupExecutor = Executors.newFixedThreadPool(2, daemonThreads("e3-cleanup"));
        private final AtomicBoolean accepting = new AtomicBoolean(true);
        private final AtomicBoolean firstCallback = new AtomicBoolean();
        private final AtomicReference<Throwable> asynchronousFailure = new AtomicReference<Throwable>();
        private final CompletableFuture<CanvasState> first = new CompletableFuture<CanvasState>();
        private final Map<String, Object> cleanupResult = new LinkedHashMap<String, Object>();
        private Properties launch = new Properties();
        private Properties readiness = new Properties();
        private String attemptId;
        private String stage = "launch-contract";
        private String imageHash;
        private String stateHash;
        private long deadline;
        private JFrame window;
        private Object model;
        private Object binding;
        private GraphCanvas canvas;
        private Object applicationController;
        private LayoutSettleLoop loop;
        private CanvasState retained;
        private CanvasState original;
        private Map<String, Object> installedClasses;

        Evidence(final Object suppliedWindow, final Path directory) {
            this.suppliedWindow = suppliedWindow;
            this.directory = directory;
        }

        void capture() throws Exception {
            launch = readProperties(directory.resolve("launch.properties"));
            attemptId = property(launch, "attemptId");
            final long launched = Long.parseLong(property(launch, "launchEpochMillis"));
            final long expires = Long.parseLong(property(launch, "deadlineEpochMillis"));
            final long now = System.currentTimeMillis();
            require(launched <= now && expires > launched && expires - launched <= 180_000L,
                "E3 launch deadline is malformed");
            require(expires > now, "E3 launch deadline already expired");
            deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(expires - now);
            readiness = readProperties(directory.resolve("agent-readiness.properties"));
            require(attemptId.equals(property(readiness, "attemptId")), "E3 readiness belongs to another attempt");
            require("21".equals(System.getProperty("java.specification.version")), "E3 evidence requires Java 21");
            require(property(launch, "sourceOid").matches("[0-9a-f]{40}"), "E3 source OID is malformed");
            for (final String hash : Arrays.asList("workingDiffSha256", "agentJarSha256", "graphBundleSha256",
                    "mapFixtureSha256", "workspaceFixtureSha256")) {
                require(property(launch, hash).matches("[0-9a-f]{64}"), "E3 launch hash is malformed: " + hash);
            }
            property(launch, "javaVersion");
            property(launch, "command");
            property(launch, "repositoryRoot");
            require(Files.getLastModifiedTime(directory.resolve("agent-readiness.properties")).toMillis() >= launched,
                "E3 readiness predates the launch");
            for (final String check : Arrays.asList("actionReady", "helperReady")) {
                require("true".equals(property(readiness, check + ".disableProperty")),
                    "E3 SecurityManager disable property failed at " + check);
                require("true".equals(property(readiness, check + ".securityManagerAbsent")),
                    "E3 SecurityManager was present at " + check);
            }
            require("true".equals(System.getProperty(
                "org.freeplane.main.application.FreeplaneSecurityManager.disable"))
                && System.getSecurityManager() == null, "E3 helper security prerequisite failed");
            for (final String name : Arrays.asList("e3-window.png", "e3-state.json", "helper-result.json", "result.json")) {
                require(!Files.exists(directory.resolve(name)), "E3 attempt contains stale artifact " + name);
            }

            stage = "installed-window-binding";
            onEdt(() -> {
                require(suppliedWindow != null && suppliedWindow.getClass().getName().equals(
                    "org.freeplane.plugin.graph.window.GraphWorkspaceWindow"), "E3 requires the installed graph window");
                window = (JFrame) suppliedWindow;
                require(window.isShowing() && "graph-workspace-window".equals(window.getName()),
                    "E3 graph window is not showing with the required name");
                model = field(window, "model");
                canvas = (GraphCanvas) field(model, "canvas");
                binding = field(model, "binding");
                original = (CanvasState) field(model, "currentState");
                applicationController = field(model, "applicationController");
                installedClasses = installedClasses(window.getClass().getClassLoader(), launch,
                    window.getClass(), model.getClass(), canvas.getClass(), binding.getClass(),
                    field(canvas, "painter").getClass(), field(window, "presentationMode").getClass(),
                    EdgeNodeClearanceReplay.class, LayoutSettleLoop.class, LayoutWorker.class, EdgeRouter.class);
                final Object registration = field(model, "canvasRegistration");
                invoke(registration, "close", new Class<?>[0]);
                return null;
            }, deadline);
            // A separate posted request drains pre-existing ordinary-controller deliveries.
            onEdt(() -> null, deadline);
            final Method accept = declaredMethod(model.getClass(), "acceptCanvasState", CanvasState.class);
            final GraphProjection projection = projection();
            final AwtGeometryTextMetrics metrics = new AwtGeometryTextMetrics(new Font("Dialog", Font.PLAIN, 12),
                new FontRenderContext(null, true, true));
            loop = new LayoutSettleLoop(WorkspaceId.of(WORKSPACE_ID), newStepper(), new GraphGeometryEngine(),
                metrics, new EvidenceEdt(this));
            stage = "first-worker-publication";
            final long publicationDeadline = System.nanoTime() + Math.min(remaining(deadline),
                TimeUnit.SECONDS.toNanos(5L));
            loop.start(new AcceptedBatch(GENERATION, 1L, EnumSet.of(ChangeKind.STRUCTURE)), projection,
                ProjectionDiff.between(GraphProjection.projected(0L, Collections.<ProjectedNode>emptyList(),
                    Collections.<ProjectedEnclosure>emptyList(), Collections.<ProjectedEdge>emptyList(),
                    Collections.<RelationshipResolution>emptyList(), Collections.<PinProjection>emptyList()), projection),
                state -> {
                    try {
                        require(accepting.get(), "E3 callback arrived after attempt failure");
                        remaining(publicationDeadline);
                        require(SwingUtilities.isEventDispatchThread(), "E3 publication did not arrive on EDT");
                        require(firstCallback.compareAndSet(false, true), "E3 received an additional publication");
                        loop.pause();
                        accept.invoke(model, state);
                        first.complete(state);
                    }
                    catch (Throwable problem) {
                        failAsync(unwrap(problem));
                    }
                }).whenComplete((ignored, problem) -> {
                    if (problem != null) {
                        failAsync(unwrap(problem));
                    }
                });
            retained = await(first, publicationDeadline);
            assertE3(retained);
            checkAsync();
            stage = "fit-and-settle";
            final CompletableFuture<Void> settled = new CompletableFuture<Void>();
            onEdt(() -> {
                window.toFront();
                window.requestFocus();
                window.validate();
                window.doLayout();
                canvas.fitGraph();
                canvas.runWhenViewportSettled(() -> {
                    try {
                        require(accepting.get(), "E3 viewport callback expired");
                        remaining(deadline);
                        settled.complete(null);
                    }
                    catch (Throwable problem) {
                        settled.completeExceptionally(unwrap(problem));
                    }
                });
                return null;
            }, deadline);
            await(settled, deadline);
            onEdt(() -> null, deadline);
            stage = "paint-and-capture";
            onEdt(() -> {
                window.getRootPane().paintImmediately(window.getRootPane().getVisibleRect());
                canvas.paintImmediately(canvas.getVisibleRect());
                RepaintManager.currentManager(window).paintDirtyRegions();
                Toolkit.getDefaultToolkit().sync();
                return null;
            }, deadline);
            onEdt(() -> null, deadline);
            final Snapshot before = onEdt(this::snapshot, deadline);
            final Future<BufferedImage> capture = captureExecutor.submit(() -> {
                remaining(deadline);
                final Robot robot = new Robot();
                robot.waitForIdle();
                remaining(deadline);
                return robot.createScreenCapture(new Rectangle(before.window));
            });
            final BufferedImage image;
            try {
                image = await(capture, deadline);
            }
            finally {
                capture.cancel(true);
            }
            final Snapshot after = onEdt(this::snapshot, deadline);
            requireSameSnapshot(before, after);
            checkAsync();
            assertE3(retained);
            require(image.getWidth() == before.window.width && image.getHeight() == before.window.height,
                "E3 Robot capture dimensions changed");
            stage = "numeric-artifacts";
            atomicImage(directory.resolve("e3-window.png"), image);
            imageHash = fileHash(directory.resolve("e3-window.png"));
            final Map<String, Object> state = stateRecord(this, before, after);
            atomicJson(directory.resolve("e3-state.json"), state);
            stateHash = fileHash(directory.resolve("e3-state.json"));
            checkAsync();
            stage = "capture-verified";
        }

        Snapshot snapshot() throws Exception {
            checkAsync();
            require(window.isShowing() && canvas.isShowing(), "E3 window/canvas disappeared");
            final CanvasState modelState = (CanvasState) field(model, "currentState");
            final CanvasState canvasState = (CanvasState) invoke(canvas, "canvasState", new Class<?>[0]);
            require(modelState == retained && canvasState == retained, "E3 retained state was overwritten");
            final JViewport viewport = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, canvas);
            require(viewport != null && viewport.isShowing(), "E3 canvas has no showing scroll viewport");
            final Rectangle visible = viewport.getVisibleRect();
            final Point viewportLocation = viewport.getLocationOnScreen();
            visible.translate(viewportLocation.x, viewportLocation.y);
            final Rectangle windowBounds = window.getBounds();
            require(visible.width > 0 && visible.height > 0 && windowBounds.contains(visible),
                "E3 visible graph region is not inside the window");
            final Point origin = canvas.getLocationOnScreen();
            final Rectangle2D drawing = drawingBounds(canvas, retained, origin);
            require(visible.contains(drawing), "E3 whole drawing is outside the visible graph region: " + drawing);
            return new Snapshot(modelState, canvasState, retained.generation(), stateDigest(retained),
                windowBounds, visible, drawing, canvas.viewport(), canvas.getSize(), origin, viewport.getViewPosition());
        }

        void failAsync(final Throwable problem) {
            asynchronousFailure.compareAndSet(null, problem);
            first.completeExceptionally(problem);
        }

        void checkAsync() throws Exception {
            final Throwable problem = asynchronousFailure.get();
            if (problem != null) {
                throw propagate(problem);
            }
        }

        Throwable cleanup(final Throwable operationFailure) {
            Throwable failure = null;
            accepting.set(false);
            final long cleanupDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10L);
            if (loop != null) {
                final Future<?> close = cleanupExecutor.submit(loop::close);
                try {
                    await(close, cleanupDeadline);
                    cleanupResult.put("loopCloseReturned", true);
                }
                catch (Throwable problem) {
                    cleanupResult.put("loopCloseReturned", false);
                    failure = combine(failure, unwrap(problem));
                    close.cancel(true);
                }
            }
            if (window != null) {
                try {
                    onEdt(() -> {
                        invoke(window, "close", new Class<?>[0]);
                        return null;
                    }, cleanupDeadline);
                    cleanupResult.put("windowClosed", true);
                }
                catch (Throwable problem) {
                    cleanupResult.put("windowClosed", false);
                    failure = combine(failure, unwrap(problem));
                }
                final Future<?> shutdown = cleanupExecutor.submit(() -> {
                    Object controller = applicationController;
                    if (controller == null && model != null) {
                        controller = onEdt(() -> field(model, "applicationController"), cleanupDeadline);
                    }
                    require(controller != null, "E3 has no application controller to shut down");
                    final ClassLoader loader = window.getClass().getClassLoader();
                    if (controller.getClass().getName().equals(
                            "org.freeplane.plugin.graph.GraphModeExtension$ForwardingGraphWorkspaceController")) {
                        final Object forwarding = controller;
                        require(forwarding.getClass().getClassLoader() == loader, "E3 forwarding controller loader changed");
                        controller = onEdt(() -> field(forwarding, "delegate"), cleanupDeadline);
                    }
                    require(controller != null && controller.getClass() == DefaultGraphWorkspaceController.class
                            && controller.getClass().getClassLoader() == loader,
                        "E3 shutdown requires the real installed DefaultGraphWorkspaceController");
                    ((DefaultGraphWorkspaceController) controller).shutdown();
                    return null;
                });
                try {
                    await(shutdown, cleanupDeadline);
                    cleanupResult.put("controllerShutdownReturned", true);
                }
                catch (Throwable problem) {
                    cleanupResult.put("controllerShutdownReturned", false);
                    failure = combine(failure, unwrap(problem));
                    shutdown.cancel(true);
                }
                try {
                    final boolean disposed = onEdt(() -> !window.isDisplayable() && !window.isShowing(), cleanupDeadline);
                    cleanupResult.put("windowDisposed", disposed);
                    require(disposed, "E3 graph window survived cleanup");
                }
                catch (Throwable problem) {
                    failure = combine(failure, unwrap(problem));
                }
            }
            failure = stopExecutor(captureExecutor, "captureExecutorStopped", cleanupDeadline, cleanupResult, failure);
            failure = stopExecutor(cleanupExecutor, "cleanupExecutorStopped", cleanupDeadline, cleanupResult, failure);
            final Throwable asynchronous = asynchronousFailure.get();
            if (asynchronous != null) {
                failure = combine(failure, asynchronous);
            }
            cleanupResult.put("passed", failure == null);
            return combine(operationFailure, failure);
        }
    }

    private static final class EvidenceEdt implements EdtExecutor {
        private final Evidence evidence;

        EvidenceEdt(final Evidence evidence) {
            this.evidence = evidence;
        }

        @Override
        public <T> T call(final Callable<T> operation) {
            try {
                return onEdt(operation, evidence.deadline);
            }
            catch (Exception problem) {
                evidence.failAsync(problem);
                throw new IllegalStateException("E3 timed EDT request failed", problem);
            }
        }

        @Override
        public boolean isEdt() {
            return SwingUtilities.isEventDispatchThread();
        }

        @Override
        public void execute(final Runnable operation) {
            SwingUtilities.invokeLater(() -> {
                try {
                    // Cleanup deliveries must still run after the capture deadline; their callers
                    // have a separate timed physical-close wait. Listener binding checks accepting.
                    operation.run();
                }
                catch (Throwable problem) {
                    evidence.failAsync(unwrap(problem));
                }
            });
        }
    }

    private static Rectangle2D drawingBounds(final GraphCanvas canvas, final CanvasState state,
            final Point origin) throws Exception {
        final GraphViewport viewport = canvas.viewport();
        Rectangle2D.Double bounds = null;
        for (final NodeGeometry node : state.geometry().nodes().values()) {
            final double x = screenX(node.center().x(), viewport, canvas, origin);
            final double y = screenY(node.center().y(), viewport, canvas, origin);
            final double radius = node.radius() * viewport.zoom();
            bounds = include(bounds, x - radius, y - radius, x + radius, y + radius);
        }
        for (final HullGeometry hull : state.geometry().hulls().values()) {
            final Rectangle2D world = hull.smoothPath().getBounds2D();
            bounds = include(bounds, screenX(world.getMinX(), viewport, canvas, origin),
                screenY(world.getMinY(), viewport, canvas, origin),
                screenX(world.getMaxX(), viewport, canvas, origin),
                screenY(world.getMaxY(), viewport, canvas, origin));
        }
        for (final EdgePath path : state.edgePaths().byEdge().values()) {
            for (final LayoutPoint sample : path.samples()) {
                final double x = screenX(sample.x(), viewport, canvas, origin);
                final double y = screenY(sample.y(), viewport, canvas, origin);
                bounds = include(bounds, x, y, x, y);
            }
        }
        final Object policy = field(canvas, "renderingPolicy");
        final RenderingLevel level = (RenderingLevel) invoke(policy, "forCounts",
            new Class<?>[] {int.class, int.class}, state.projection().projectedNodeCount(), state.projection().edges().size());
        final Object placed = invoke(canvas, "labelsFor",
            new Class<?>[] {CanvasState.class, Dimension.class, RenderingLevel.class}, state, canvas.getSize(), level);
        require(placed instanceof List<?>, "E3 real label placement is unavailable");
        for (final Object value : (List<?>) placed) {
            final PlacedLabel label = (PlacedLabel) value;
            if (label.mode() == PlacedLabel.Mode.HOVER_ONLY) {
                continue;
            }
            final Rectangle2D rect = label.bounds();
            bounds = include(bounds, origin.x + rect.getMinX(), origin.y + rect.getMinY(),
                origin.x + rect.getMaxX(), origin.y + rect.getMaxY());
            if (label.leader().isPresent()) {
                final LayoutPoint start = label.leader().get().start();
                final LayoutPoint end = label.leader().get().end();
                bounds = include(bounds, origin.x + Math.min(start.x(), end.x()),
                    origin.y + Math.min(start.y(), end.y()), origin.x + Math.max(start.x(), end.x()),
                    origin.y + Math.max(start.y(), end.y()));
            }
        }
        require(bounds != null, "E3 has no drawing bounds");
        // Include the actual painted stroke, rather than accepting a disc/path clipped at the rim.
        return new Rectangle2D.Double(bounds.x - 2.0, bounds.y - 2.0, bounds.width + 4.0, bounds.height + 4.0);
    }

    private static double screenX(final double world, final GraphViewport viewport,
            final GraphCanvas canvas, final Point origin) {
        return origin.x + canvas.getWidth() * 0.5 + (world - viewport.centerX()) * viewport.zoom();
    }

    private static double screenY(final double world, final GraphViewport viewport,
            final GraphCanvas canvas, final Point origin) {
        return origin.y + canvas.getHeight() * 0.5 + (world - viewport.centerY()) * viewport.zoom();
    }

    private static Rectangle2D.Double include(final Rectangle2D.Double bounds, final double minX,
            final double minY, final double maxX, final double maxY) {
        require(Double.isFinite(minX) && Double.isFinite(minY) && Double.isFinite(maxX) && Double.isFinite(maxY)
                && minX <= maxX && minY <= maxY, "E3 drawing has nonfinite or unordered bounds");
        if (bounds == null) {
            return new Rectangle2D.Double(minX, minY, maxX - minX, maxY - minY);
        }
        bounds.add(minX, minY);
        bounds.add(maxX, maxY);
        return bounds;
    }

    private static Map<String, Object> installedClasses(final ClassLoader loader, final Properties launch,
            final Class<?>... classes) throws Exception {
        require(loader != null && EdgeNodeClearanceReplay.class.getClassLoader() == loader,
            "E3 helper was not defined into the installed window loader");
        final Class<?> framework = Class.forName("org.osgi.framework.FrameworkUtil", false, loader);
        final Class<?> bundleType = Class.forName("org.osgi.framework.Bundle", false, loader);
        final Path installedRoot = java.nio.file.Paths.get(property(launch, "repositoryRoot")).toRealPath().resolve("BIN").toRealPath();
        final Map<String, Object> records = new LinkedHashMap<String, Object>();
        Long graphBundleId = null;
        for (final Class<?> type : classes) {
            require(type.getClassLoader() == loader && Class.forName(type.getName(), false, loader) == type,
                "E3 wrong installed class identity/loader: " + type.getName());
            final Object bundle = framework.getMethod("getBundle", Class.class).invoke(null, type);
            require(bundle != null, "E3 class has no installed OSGi bundle: " + type.getName());
            final long id = ((Long) bundleType.getMethod("getBundleId").invoke(bundle)).longValue();
            final String symbolic = (String) bundleType.getMethod("getSymbolicName").invoke(bundle);
            require("org.freeplane.plugin.graph".equals(symbolic), "E3 class resolves outside graph bundle");
            require(graphBundleId == null || graphBundleId.longValue() == id, "E3 classes span different graph bundles");
            graphBundleId = Long.valueOf(id);
            final String location = (String) bundleType.getMethod("getLocation").invoke(bundle);
            final String version = bundleType.getMethod("getVersion").invoke(bundle).toString();
            final Path jar = installedGraphJar(installedRoot, location, version);
            final String bundleHash = fileHash(jar);
            require(property(launch, "graphBundleSha256").equals(bundleHash), "E3 installed bundle hash differs from launch provenance");
            final java.security.CodeSource source = type.getProtectionDomain().getCodeSource();
            final URL resource = type.getResource(type.getSimpleName() + ".class");
            records.put(type.getName(), record("className", type.getName(), "loader", reference(loader),
                "loaderClass", loader.getClass().getName(), "sameWindowLoader", true,
                "codeSource", source == null || source.getLocation() == null ? null : source.getLocation().toExternalForm(),
                "classResource", resource == null ? null : resource.toExternalForm(), "bundleId", id,
                "bundleSymbolicName", symbolic, "bundleVersion", version,
                "bundleState", bundleType.getMethod("getState").invoke(bundle), "bundleLocation", location,
                "installedPluginJar", jar.toString(), "bundleSha256", bundleHash,
                "manifestSha256", fileHash(jar.getParent().getParent().resolve("META-INF/MANIFEST.MF"))));
        }
        return records;
    }

    static Path installedGraphJar(final Path installedRoot, final String location, final String version) throws Exception {
        final String fileLocation = location.startsWith("reference:") ? location.substring("reference:".length()) : location;
        final URI uri = URI.create(fileLocation);
        require("file".equals(uri.getScheme()), "E3 installed graph bundle location is not a file: " + location);
        final Path bundle = java.nio.file.Paths.get(uri).toRealPath();
        final Path expected = installedRoot.toRealPath().resolve("plugins/org.freeplane.plugin.graph").toRealPath();
        require(bundle.equals(expected) && Files.isDirectory(bundle)
                && Files.isRegularFile(bundle.resolve("META-INF/MANIFEST.MF")),
            "E3 graph classes did not originate in the installed BIN bundle: " + bundle);
        final Path jar = bundle.resolve("lib/plugin-" + version + ".jar").toRealPath();
        require(jar.startsWith(bundle) && Files.isRegularFile(jar), "E3 installed graph plugin Jar is missing: " + jar);
        return jar;
    }

    private static Map<String, Object> stateRecord(final Evidence evidence, final Snapshot before,
            final Snapshot after) throws Exception {
        final CanvasState state = evidence.retained;
        assertE3(state);
        final Map<String, Object> raw = new LinkedHashMap<String, Object>();
        final LayoutPositions rawPositions = rawPositions();
        final GraphGeometry rawGeometry = new GraphGeometryEngine().computeHulls(state.projection(), rawPositions,
            new AwtGeometryTextMetrics(new Font("Dialog", Font.PLAIN, 12), new FontRenderContext(null, true, true)));
        final double rawMinimum = minimumClearance(state.projection(), rawGeometry, state.edgePaths());
        require(rawMinimum == -8.0 && rawGeometry.hulls().isEmpty(), "E3 raw replay is not the obstructed column");
        final List<Object> rawGaps = new ArrayList<Object>();
        final List<ProjectedNode> projectedNodes = state.projection().nodes();
        for (int first = 0; first < projectedNodes.size(); first++) {
            for (int second = first + 1; second < projectedNodes.size(); second++) {
                final ProjectedNodeKey a = projectedNodes.get(first).key();
                final ProjectedNodeKey b = projectedNodes.get(second).key();
                final NodeGeometry firstNode = rawGeometry.nodes().get(a);
                final NodeGeometry secondNode = rawGeometry.nodes().get(b);
                final double gap = Math.hypot(firstNode.center().x() - secondNode.center().x(),
                    firstNode.center().y() - secondNode.center().y()) - firstNode.radius() - secondNode.radius() - 6.0;
                require(gap >= -CLEARANCE_TOLERANCE, "E3 raw replay contains a node-gap violation");
                rawGaps.add(record("first", CanonicalKeys.node(a), "second", CanonicalKeys.node(b), "gapAboveRequired", gap));
            }
        }
        final List<Object> corrected = new ArrayList<Object>();
        for (final ProjectedNode node : projectedNodes) {
            final String key = CanonicalKeys.node(node.key());
            final Map<String, Object> rawNode = pointRecord(rawPositions.nodes().get(node.key()));
            final double rawRadius = rawGeometry.nodes().get(node.key()).radius();
            require(rawRadius == ACTUAL_RADIUS, "E3 raw replay radius differs from production geometry");
            rawNode.put("radius", rawRadius);
            rawNode.put("radiusBits", bits(rawRadius));
            rawNode.put("id", node.key().source().persistedReference().get().nodeId().value());
            raw.put(key, rawNode);
            final NodeGeometry geometry = state.geometry().nodes().get(node.key());
            corrected.add(record("key", key, "id", node.key().source().persistedReference().get().nodeId().value(),
                "center", pointRecord(geometry.center()), "radius", geometry.radius(), "radiusBits", bits(geometry.radius())));
        }
        final List<String> expected = new ArrayList<String>();
        for (final ProjectedEdge edge : state.projection().edges()) {
            expected.add(CanonicalKeys.edge(edge.key()));
        }
        Collections.sort(expected);
        final List<String> actual = new ArrayList<String>();
        final List<Object> paths = new ArrayList<Object>();
        final List<Object> checks = new ArrayList<Object>();
        for (final Map.Entry<ProjectedEdgeKey, EdgePath> entry : state.edgePaths().byEdge().entrySet()) {
            final String edge = CanonicalKeys.edge(entry.getKey());
            actual.add(edge);
            final List<Object> samples = new ArrayList<Object>();
            for (final LayoutPoint sample : entry.getValue().samples()) {
                samples.add(pointRecord(sample));
            }
            paths.add(record("edgeKey", edge, "shape", entry.getValue().shape().name(),
                "classification", state.edgePaths().diagnostics().edgesFallback() == 0 ? entry.getValue().shape().name() : "FALLBACK",
                "samples", samples, "length", entry.getValue().length()));
            final Set<ProjectedNodeKey> attached = attachedNodes(state.projection(), entry.getKey());
            for (int segment = 1; segment < entry.getValue().samples().size(); segment++) {
                final LayoutPoint from = entry.getValue().samples().get(segment - 1);
                final LayoutPoint to = entry.getValue().samples().get(segment);
                for (final Map.Entry<ProjectedNodeKey, NodeGeometry> node : state.geometry().nodes().entrySet()) {
                    if (attached.contains(node.getKey())) {
                        continue;
                    }
                    final double distance = segmentDistance(node.getValue().center(), from, to);
                    final double clearance = distance - node.getValue().radius();
                    require(Double.isFinite(clearance) && clearance >= REQUIRED_CLEARANCE - CLEARANCE_TOLERANCE,
                        "E3 per-segment/disc clearance failed");
                    checks.add(record("edgeKey", edge, "segmentIndex", segment - 1,
                        "nodeKey", CanonicalKeys.node(node.getKey()), "from", pointRecord(from), "to", pointRecord(to),
                        "center", pointRecord(node.getValue().center()), "radius", node.getValue().radius(),
                        "distance", distance, "clearance", clearance));
                }
            }
        }
        Collections.sort(actual);
        require(actual.equals(expected), "E3 artifact edge coverage changed");
        final Map<String, Object> security = record("actionReady", securityRecord(evidence.readiness, "actionReady"),
            "helperReady", securityRecord(evidence.readiness, "helperReady"));
        final CanvasState original = evidence.original;
        return record("schemaVersion", 1, "attemptId", evidence.attemptId,
            "fixture", record("seed", 20261016, "workspace", WORKSPACE_ID, "map", MAP.value(),
                "generation", GENERATION, "rawCenters", raw, "pins", Collections.emptyList(),
                "anchors", Collections.emptyMap(), "enclosures", Collections.emptyList()),
            "securityChecks", security, "installedClasses", evidence.installedClasses,
            "provenance", propertiesRecord(evidence.launch), "agentReadiness", propertiesRecord(evidence.readiness),
            "preReplay", original == null ? null : record("generation", original.generation(),
                "digest", stateDigest(original), "status", original.status().name(),
                "correction", correctionRecord(original.layout().edgeConflictDiagnostics()), "routing", routeRecord(original.edgePaths())),
            "rawReplay", record("suppliesCorrectedFrame", false, "obstructingNode", "N",
                "center", pointRecord(rawPositions.nodes().get(nodeKeys().get(N_INDEX))),
                "straightClearance", rawMinimum, "nodeGapChecks", rawGaps, "hullPairCount", 0),
            "retainedState", record("before", snapshotRecord(before), "after", snapshotRecord(after),
                "digest", before.digest, "exactSameReference", true, "firstCallbackCount", 1,
                "status", state.status().name(), "stepIndex", state.layout().stepIndex(),
                "failed", state.layout().failed(), "verified", state.layout().verified(),
                "residualViolations", state.layout().residualViolations()),
            "correctedNodes", corrected, "edgeCoverage", record("expected", expected, "actual", actual),
            "paths", paths, "correction", correctionRecord(state.layout().edgeConflictDiagnostics()),
            "boundary", numericDiagnostics(state.layout().boundaryDiagnostics()),
            "routing", routeRecord(state.edgePaths()), "clearance", record("tolerance", CLEARANCE_TOLERANCE,
                "required", REQUIRED_CLEARANCE, "minimum", minimumClearance(state.projection(), state.geometry(), state.edgePaths()),
                "segmentDiscChecks", checks),
            "screen", record("window", rectangleRecord(before.window), "visibleCanvas", rectangleRecord(before.visibleCanvas),
                "drawing", rectangleRecord(before.drawing), "viewport", viewportRecord(before)),
            "screenshotSha256", evidence.imageHash);
    }

    private static Map<String, Object> correctionRecord(final EdgeConflictDiagnostics diagnostics) {
        final List<Object> repairs = new ArrayList<Object>();
        for (final EdgeConflictDiagnostics.Repair repair : diagnostics.repairsApplied()) {
            final List<String> keys = new ArrayList<String>();
            final Map<String, Object> from = new LinkedHashMap<String, Object>();
            final Map<String, Object> to = new LinkedHashMap<String, Object>();
            for (final ProjectedNodeKey node : repair.nodeKeys()) {
                final String key = CanonicalKeys.node(node);
                keys.add(key);
                from.put(key, pointRecord(repair.fromCentres().get(node)));
                to.put(key, pointRecord(repair.toCentres().get(node)));
            }
            repairs.add(record("kind", repair.kind().name(), "nodeKeys", keys, "fromCentres", from,
                "toCentres", to, "batchIndex", repair.batchIndex()));
        }
        return record("conflictsDetected", diagnostics.conflictsDetected(), "crossingsDetected", diagnostics.crossingsDetected(),
            "edgeOverNodeDetected", diagnostics.edgeOverNodeDetected(), "crossingsResolved", diagnostics.crossingsResolved(),
            "edgeOverNodeResolved", diagnostics.edgeOverNodeResolved(), "conflictsResidual", diagnostics.conflictsResidual(),
            "repairsApplied", repairs, "rounds", diagnostics.rounds(), "verifications", diagnostics.verifications(),
            "verificationBudgetExhausted", diagnostics.verificationBudgetExhausted(),
            "indexBudgetExhausted", diagnostics.indexBudgetExhausted(), "detectionTruncated", diagnostics.detectionTruncated(),
            "indexInsertedItems", diagnostics.indexInsertedItems(), "indexPairChecks", diagnostics.indexPairChecks(),
            "verificationWork", diagnostics.verificationWork(), "indexSubdivisions", diagnostics.indexSubdivisions());
    }

    private static Map<String, Object> routeRecord(final EdgePaths paths) throws Exception {
        final EdgeRouteDiagnostics route = paths.diagnostics();
        final Map<String, Object> result = numericDiagnostics(route);
        result.put("pathCrossingsResidual", route.pathCrossingsResidual());
        result.put("timings", numericDiagnostics(paths.timings()));
        return result;
    }

    /** Include all scalar diagnostic getters without stringifying counters or dropping flags. */
    private static Map<String, Object> numericDiagnostics(final Object diagnostics) throws Exception {
        final Map<String, Object> result = new java.util.TreeMap<String, Object>();
        for (final Method method : diagnostics.getClass().getDeclaredMethods()) {
            if (Modifier.isPublic(method.getModifiers()) && !Modifier.isStatic(method.getModifiers())
                    && method.getParameterTypes().length == 0 && !method.getName().equals("hashCode")
                    && (method.getReturnType().isPrimitive() || method.getReturnType() == String.class)
                    && !method.getName().equals("toString")) {
                result.put(method.getName(), method.invoke(diagnostics));
            }
        }
        return result;
    }

    private static Map<String, Object> securityRecord(final Properties properties, final String stage) {
        final Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (final String key : properties.stringPropertyNames()) {
            if (key.startsWith(stage + ".")) {
                result.put(key.substring(stage.length() + 1), properties.getProperty(key));
            }
        }
        result.put("disableProperty", properties.getProperty(stage + ".disableProperty"));
        result.put("securityManagerAbsent", Boolean.valueOf(properties.getProperty(stage + ".securityManagerAbsent")));
        return result;
    }

    private static Map<String, Object> snapshotRecord(final Snapshot snapshot) {
        return record("modelStateReference", reference(snapshot.modelState), "canvasStateReference", reference(snapshot.canvasState),
            "modelIsRetained", true, "canvasIsRetained", true, "generation", snapshot.generation, "digest", snapshot.digest,
            "window", rectangleRecord(snapshot.window), "visibleCanvas", rectangleRecord(snapshot.visibleCanvas),
            "drawing", rectangleRecord(snapshot.drawing), "viewport", viewportRecord(snapshot));
    }

    private static Map<String, Object> viewportRecord(final Snapshot snapshot) {
        return record("centerX", snapshot.viewport.centerX(), "centerY", snapshot.viewport.centerY(), "zoom", snapshot.viewport.zoom(),
            "centerXBits", bits(snapshot.viewport.centerX()), "centerYBits", bits(snapshot.viewport.centerY()),
            "zoomBits", bits(snapshot.viewport.zoom()), "canvasWidth", snapshot.canvasSize.width, "canvasHeight", snapshot.canvasSize.height,
            "canvasScreenX", snapshot.canvasLocation.x, "canvasScreenY", snapshot.canvasLocation.y,
            "viewPositionX", snapshot.viewPosition.x, "viewPositionY", snapshot.viewPosition.y);
    }

    private static Map<String, Object> rectangleRecord(final Rectangle2D rectangle) {
        return record("x", rectangle.getX(), "y", rectangle.getY(), "width", rectangle.getWidth(), "height", rectangle.getHeight());
    }

    private static Map<String, Object> pointRecord(final LayoutPoint point) {
        requireFinite(point, "E3 artifact point");
        return record("x", point.x(), "y", point.y(), "xBits", bits(point.x()), "yBits", bits(point.y()));
    }

    private static String bits(final double value) {
        return Long.toHexString(Double.doubleToLongBits(value == 0.0 ? 0.0 : value));
    }

    private static Object field(final Object target, final String name) throws Exception {
        final Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static Method declaredMethod(final Class<?> type, final String name, final Class<?>... parameterTypes)
            throws Exception {
        final Method method = type.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method;
    }

    private static Object invoke(final Object target, final String name, final Class<?>[] parameterTypes,
            final Object... arguments) throws Exception {
        try {
            return declaredMethod(target.getClass(), name, parameterTypes).invoke(target, arguments);
        }
        catch (InvocationTargetException failure) {
            throw propagate(unwrap(failure));
        }
    }

    private static Properties readProperties(final Path file) throws Exception {
        require(Files.isRegularFile(file), "E3 property file is missing: " + file);
        final Properties properties = new Properties();
        try (java.io.Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }

    private static String property(final Properties properties, final String name) {
        final String value = properties.getProperty(name);
        require(value != null && !value.trim().isEmpty(), "E3 launch/readiness property is missing: " + name);
        return value;
    }

    private static Map<String, Object> propertiesRecord(final Properties properties) {
        final Map<String, Object> result = new java.util.TreeMap<String, Object>();
        for (final String key : properties.stringPropertyNames()) {
            result.put(key, properties.getProperty(key));
        }
        return result;
    }

    private static ThreadFactory daemonThreads(final String name) {
        return operation -> {
            final Thread thread = new Thread(operation, name);
            thread.setDaemon(true);
            return thread;
        };
    }

    private static Throwable stopExecutor(final ExecutorService executor, final String field, final long deadline,
            final Map<String, Object> result, Throwable failure) {
        executor.shutdownNow();
        try {
            require(executor.awaitTermination(remaining(deadline), TimeUnit.NANOSECONDS),
                "E3 " + field + " timed out");
            result.put(field, true);
        }
        catch (Throwable problem) {
            result.put(field, false);
            failure = combine(failure, unwrap(problem));
        }
        return failure;
    }

    private static Throwable combine(final Throwable first, final Throwable next) {
        if (first == null) {
            return next;
        }
        if (next != null && first != next) {
            first.addSuppressed(next);
        }
        return first;
    }

    private static Map<String, Object> errorRecord(final Throwable failure) {
        if (failure == null) {
            return null;
        }
        final java.io.StringWriter stack = new java.io.StringWriter();
        failure.printStackTrace(new java.io.PrintWriter(stack));
        return record("type", failure.getClass().getName(), "message", failure.getMessage(), "stackTrace", stack.toString());
    }

    private static String reference(final Object value) {
        return value.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(value));
    }

    private static Map<String, Object> record(final Object... fields) {
        require(fields.length % 2 == 0, "E3 JSON record needs key/value pairs");
        final Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (int index = 0; index < fields.length; index += 2) {
            result.put((String) fields[index], fields[index + 1]);
        }
        return result;
    }

    /** JDK-only JSON encoding; paths, error text and control characters all pass through quoting. */
    static String json(final Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String) {
            final StringBuilder text = new StringBuilder("\"");
            final String string = (String) value;
            for (int index = 0; index < string.length(); index++) {
                final char character = string.charAt(index);
                if (character == '"' || character == '\\') {
                    text.append('\\').append(character);
                }
                else if (character < 0x20 || Character.isSurrogate(character)) {
                    text.append(String.format("\\u%04x", Integer.valueOf(character)));
                }
                else {
                    text.append(character);
                }
            }
            return text.append('"').toString();
        }
        if (value instanceof Number) {
            require(Double.isFinite(((Number) value).doubleValue()), "E3 JSON number is not finite");
            return value.toString();
        }
        if (value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof Map<?, ?>) {
            final StringBuilder object = new StringBuilder("{");
            for (final Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                require(entry.getKey() instanceof String, "E3 JSON key is not text");
                if (object.length() > 1) {
                    object.append(',');
                }
                object.append(json(entry.getKey())).append(':').append(json(entry.getValue()));
            }
            return object.append('}').toString();
        }
        if (value instanceof Iterable<?>) {
            final StringBuilder array = new StringBuilder("[");
            for (final Object element : (Iterable<?>) value) {
                if (array.length() > 1) {
                    array.append(',');
                }
                array.append(json(element));
            }
            return array.append(']').toString();
        }
        throw new AssertionError("E3 unsupported JSON value " + value.getClass().getName());
    }

    private static void atomicJson(final Path file, final Object content) throws Exception {
        final byte[] bytes = (json(content) + "\n").getBytes(StandardCharsets.UTF_8);
        final Path temporary = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".tmp");
        try {
            Files.write(temporary, bytes);
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE);
        }
        finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void atomicImage(final Path file, final BufferedImage image) throws Exception {
        final Path temporary = Files.createTempFile(file.getParent(), "e3-window", ".png.tmp");
        try {
            require(ImageIO.write(image, "png", temporary.toFile()), "E3 PNG encoder is unavailable");
            final BufferedImage saved = ImageIO.read(temporary.toFile());
            require(saved != null && saved.getWidth() == image.getWidth() && saved.getHeight() == image.getHeight(),
                "E3 saved PNG is malformed");
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE);
        }
        finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String fileHash(final Path file) throws Exception {
        final MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream stream = Files.newInputStream(file)) {
            final byte[] buffer = new byte[8192];
            int count;
            while ((count = stream.read(buffer)) != -1) {
                digest.update(buffer, 0, count);
            }
        }
        final StringBuilder result = new StringBuilder();
        for (final byte value : digest.digest()) {
            result.append(String.format("%02x", Integer.valueOf(value & 0xff)));
        }
        return result.toString();
    }

    private static void assertCorrection(final EdgeConflictDiagnostics diagnostics,
            final List<ProjectedNodeKey> keys) {
        require(diagnostics.edgeOverNodeDetected() == 1,
            "E3 detected C2 conflicts: " + diagnostics.edgeOverNodeDetected());
        require(diagnostics.edgeOverNodeResolved() == 1,
            "E3 resolved C2 conflicts: " + diagnostics.edgeOverNodeResolved());
        require(diagnostics.crossingsDetected() == 0 && diagnostics.crossingsResolved() == 0,
            "E3 reports crossing conflicts");
        require(diagnostics.rounds() == 1, "E3 rounds: " + diagnostics.rounds());
        require(diagnostics.verifications() == 3,
            "E3 verifications: " + diagnostics.verifications());
        require(diagnostics.verificationWork() == 252L,
            "E3 verification work: " + diagnostics.verificationWork());
        require(diagnostics.conflictsResidual().isEmpty(),
            "E3 residual conflicts: " + diagnostics.conflictsResidual());
        require(!diagnostics.detectionTruncated(), "E3 correction detection truncated");
        require(!diagnostics.verificationBudgetExhausted(),
            "E3 correction verification budget exhausted");
        require(!diagnostics.indexBudgetExhausted(), "E3 correction index budget exhausted");
        require(diagnostics.verifications() <= 64,
            "E3 verifications exceed the retained cap");
        require(diagnostics.verificationWork() <= 65_536L,
            "E3 verification work exceeds the retained cap");
        require(diagnostics.indexInsertedItems() <= 262_144L,
            "E3 index items exceed the retained cap");
        require(diagnostics.indexPairChecks() <= 131_072L,
            "E3 index pair checks exceed the retained cap");
        require(diagnostics.conflictsDetected() <= 4_096,
            "E3 detected keys exceed the retained cap");

        require(diagnostics.repairsApplied().size() == 1,
            "E3 repairs applied: " + diagnostics.repairsApplied().size());
        final EdgeConflictDiagnostics.Repair repair = diagnostics.repairsApplied().get(0);
        final ProjectedNodeKey obstructing = keys.get(N_INDEX);
        require(repair.kind() == EdgeConflictDiagnostics.RepairKind.TRANSLATION,
            "E3 repair kind is " + repair.kind());
        require(repair.nodeKeys().equals(Collections.singletonList(obstructing)),
            "E3 repair nodes are " + repair.nodeKeys());
        require(repair.batchIndex() == 0, "E3 repair batch index is " + repair.batchIndex());
        require(LayoutPoint.of(0.0, 0.0).equals(repair.fromCentres().get(obstructing)),
            "E3 repair source is " + repair.fromCentres().get(obstructing));
        require(N_REPAIRED.equals(repair.toCentres().get(obstructing)),
            "E3 repair target is " + repair.toCentres().get(obstructing));
    }

    private static Set<ProjectedNodeKey> attachedNodes(final GraphProjection projection,
            final ProjectedEdgeKey key) {
        final Set<ProjectedNodeKey> attached = new LinkedHashSet<ProjectedNodeKey>();
        for (final ProjectedEdge edge : projection.edges()) {
            if (!edge.key().equals(key)) {
                continue;
            }
            if (edge.first().isNode()) {
                attached.add(edge.first().node().get());
            }
            if (edge.second().isNode()) {
                attached.add(edge.second().node().get());
            }
        }
        return attached;
    }

    /** Clamped point-to-closed-segment distance; a zero-length segment is a point distance. */
    private static double segmentDistance(final LayoutPoint point, final LayoutPoint from,
            final LayoutPoint to) {
        final double dx = to.x() - from.x();
        final double dy = to.y() - from.y();
        final double lengthSquared = dx * dx + dy * dy;
        final double projection = lengthSquared == 0.0 ? 0.0 : Math.max(0.0, Math.min(1.0,
            ((point.x() - from.x()) * dx + (point.y() - from.y()) * dy) / lengthSquared));
        return Math.hypot(point.x() - (from.x() + projection * dx),
            point.y() - (from.y() + projection * dy));
    }

    private static List<ProjectedNodeKey> nodeKeys() {
        final GraphProjection fixture = projection();
        final List<ProjectedNodeKey> keys = new ArrayList<ProjectedNodeKey>(fixture.nodes().size());
        for (final ProjectedNode node : fixture.nodes()) {
            keys.add(node.key());
        }
        return keys;
    }

    private static void appendLong(final StringBuilder builder, final long value) {
        builder.append(value).append(';');
    }

    private static void appendText(final StringBuilder builder, final String value) {
        builder.append(value.length()).append(':').append(value).append(';');
    }

    private static void appendDouble(final StringBuilder builder, final double value) {
        final double normalized = value == 0.0 ? 0.0 : value;
        builder.append(Long.toHexString(Double.doubleToLongBits(normalized))).append(';');
    }

    private static String sha256(final String content) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] value = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            final StringBuilder builder = new StringBuilder(value.length * 2);
            for (final byte element : value) {
                builder.append(String.format("%02x", Integer.valueOf(element & 0xff)));
            }
            return builder.toString();
        }
        catch (final NoSuchAlgorithmException exception) {
            throw new AssertionError("SHA-256 is unavailable", exception);
        }
    }

    private static void requireFinite(final LayoutPoint point, final String role) {
        require(Double.isFinite(point.x()) && Double.isFinite(point.y()),
            role + " is not finite: " + point);
    }

    private static void require(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    /** A raw E3 replay engine: every frame keeps the unmodified column. */
    private static final class RawReplayEngine implements LayoutEngine {
        private long index;
        private boolean initialized;

        @Override
        public LayoutFrame apply(final LayoutRequest request) {
            final LayoutPositions raw = rawPositions();
            final Set<ProjectedNodeKey> expected = new LinkedHashSet<ProjectedNodeKey>();
            for (final ProjectedNode node : request.projection().nodes()) {
                expected.add(node.key());
            }
            if (!expected.equals(raw.nodes().keySet())
                    || !request.projection().enclosures().isEmpty()) {
                throw new IllegalArgumentException("E3 replay request coverage mismatch");
            }
            index = 0L;
            initialized = true;
            return LayoutFrame.of(index, raw, false);
        }

        @Override
        public LayoutFrame step() {
            if (!initialized) {
                throw new IllegalStateException("E3 replay has no request");
            }
            return LayoutFrame.of(++index, rawPositions(), false);
        }

        @Override
        public void reset() {
            index = 0L;
            initialized = false;
        }

        @Override
        public void close() {
            reset();
        }
    }

    private static LayoutWorker freshWorker() {
        final Supplier<LayoutEngine> factory = new Supplier<LayoutEngine>() {
            @Override
            public LayoutEngine get() {
                return new RawReplayEngine();
            }
        };
        return new LayoutWorker(factory, PerceptualIdlePolicy.spikeDefaults());
    }

    /** The real-worker adapter; reset closes and recreates the worker with the same factory. */
    private static final class ReplayStepper implements LayoutSettleLoop.FrameStepper {
        private LayoutWorker worker = freshWorker();

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
        public synchronized LayoutFrame lastValidFrame() {
            return worker.lastValidFrame();
        }

        @Override
        public synchronized void reset() {
            worker.close();
            worker = freshWorker();
        }

        @Override
        public synchronized void close() {
            worker.close();
        }
    }
}
