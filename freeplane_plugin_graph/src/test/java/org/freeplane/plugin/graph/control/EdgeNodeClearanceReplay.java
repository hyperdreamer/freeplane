package org.freeplane.plugin.graph.control;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

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
import org.freeplane.plugin.graph.projection.RelationshipResolution;
import org.freeplane.plugin.graph.projection.input.ConnectorDescriptor;
import org.freeplane.plugin.graph.projection.input.ConnectorSnapshot;
import org.freeplane.plugin.graph.projection.input.SafeNodeLabel;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;

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

        final double minimum = minimumClearance(fixture, geometry, paths);
        require(minimum >= REQUIRED_CLEARANCE - CLEARANCE_TOLERANCE,
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
        require(minimum != Double.POSITIVE_INFINITY, "E3 oracle measured no path segment");
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
            appendDouble(builder, node.radius());
        }
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
