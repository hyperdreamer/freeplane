package org.freeplane.plugin.graph.probe;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.freeplane.plugin.graph.geometry.AwtGeometryTextMetrics;
import org.freeplane.plugin.graph.geometry.EdgePath;
import org.freeplane.plugin.graph.geometry.EdgePaths;
import org.freeplane.plugin.graph.geometry.EdgeRouter;
import org.freeplane.plugin.graph.geometry.GraphGeometry;
import org.freeplane.plugin.graph.geometry.GraphGeometryEngine;
import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.geometry.LayoutPositions;
import org.freeplane.plugin.graph.geometry.NodeGeometry;
import org.freeplane.plugin.graph.layout.EdgeConflictDiagnostics;
import org.freeplane.plugin.graph.layout.LayoutCalibration;
import org.freeplane.plugin.graph.layout.LayoutFrame;
import org.freeplane.plugin.graph.layout.LayoutRequest;
import org.freeplane.plugin.graph.layout.LayoutWorker;
import org.freeplane.plugin.graph.projection.CanonicalKeys;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.ProjectedEdge;
import org.freeplane.plugin.graph.projection.ProjectedEndpointKey;
import org.freeplane.plugin.graph.projection.ProjectedEndpointVisibility;
import org.freeplane.plugin.graph.projection.ProjectedNode;
import org.freeplane.plugin.graph.projection.ProjectionDiff;
import org.freeplane.plugin.graph.projection.ProjectionEngine;
import org.freeplane.plugin.graph.projection.input.ConnectorDescriptor;
import org.freeplane.plugin.graph.projection.input.ConnectorSnapshot;
import org.freeplane.plugin.graph.projection.input.MapAvailability;
import org.freeplane.plugin.graph.projection.input.MapSnapshot;
import org.freeplane.plugin.graph.projection.input.NodeSnapshot;
import org.freeplane.plugin.graph.projection.input.ProjectionInput;
import org.freeplane.plugin.graph.projection.input.SafeNodeLabel;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.DisplaySettings;
import org.freeplane.plugin.graph.workspace.model.GraphRelationshipRecord;
import org.freeplane.plugin.graph.workspace.model.MapReference;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;
import org.freeplane.plugin.graph.workspace.model.PinRecord;
import org.freeplane.plugin.graph.workspace.model.UnknownXml;
import org.freeplane.plugin.graph.workspace.model.Viewport;
import org.freeplane.plugin.graph.workspace.model.WorkspaceDocument;
import org.freeplane.plugin.graph.workspace.model.WorkspaceId;

/**
 * Read-only diagnostic probe: loads the user's real graph workspace and its three maps, runs the
 * unchanged production layout pipeline, and reports every published edge path that comes closer
 * than {@code radius + EDGE_NODE_CLEARANCE} to a node disc it is not attached to.
 *
 * <p>Acceptance replay source, compiled from this documentation directory only for explicit runs.
 * Not part of the delivered bundle or the ordinary CI test set.
 */
public final class RealWorkspaceCrossingProbe {
    private static final List<UnknownXml> NO_UNKNOWN_XML = Collections.emptyList();
    private static final String[] MAP_COLORS = {"#4E79A7", "#F28E2B", "#59A14F"};
    private static final Pattern TOKEN = Pattern.compile(
        "<(/?)(node|arrowlink|graph_group)\\b([^>]*?)(/?)>", Pattern.DOTALL);
    private static final Pattern ATTR = Pattern.compile("([A-Za-z_][-A-Za-z0-9_]*)=\"([^\"]*)\"",
        Pattern.DOTALL);

    private RealWorkspaceCrossingProbe() {
    }

    public static void main(final String[] args) throws Exception {
        replay(Paths.get(args.length > 0 ? args[0]
            : "/home/henry/Documents/999.notebooks/01.math"));
    }

    static ReplayEvidence replay(final Path mathRoot) throws Exception {
        final ReplayEvidence evidence = new ReplayEvidence();
        evidence.sourceCounts = new int[3][];
        final Path[] mapFiles = {
            mathRoot.resolve("00.foundation.of.math/02.set_theory/mindmap/Axiomatic_Set_Theory.mm"),
            mathRoot.resolve(
                "04.abstract/01.topology/01.general_topology/mindmap/Point-set_Topology.mm"),
            mathRoot.resolve("01.analysis/03.real_analysis/mindmap/Real_Anaylsis.mm")
        };

        final List<MapReference> registrations = new ArrayList<MapReference>();
        final List<MapSnapshot> snapshots = new ArrayList<MapSnapshot>();
        final Map<MapReferenceId, MapAvailability> availability =
            new LinkedHashMap<MapReferenceId, MapAvailability>();

        for (int index = 0; index < mapFiles.length; index++) {
            final MapReferenceId mapId = MapReferenceId.of(String.format(Locale.ROOT,
                "00000000-0000-0000-0000-%012d", Integer.valueOf(index + 1)));
            registrations.add(MapReference.of(mapId, index + 1L, mapFiles[index].toUri(), true,
                MAP_COLORS[index], NO_UNKNOWN_XML));
            availability.put(mapId, MapAvailability.AVAILABLE);
            final SourceMap map = SourceMap.load(mapFiles[index]);
            final Set<PersistedNodeId> ids = new LinkedHashSet<PersistedNodeId>();
            final NodeSnapshot rootSnapshot = map.toSnapshot(mapId, new ArrayList<Integer>(), false,
                ids);
            final MapSnapshot snapshot = MapSnapshot.of(mapId, index + 1,
                mapFiles[index].getFileName().toString(), rootSnapshot, ids,
                false).withConnectors(map.toConnectors(mapId));
            snapshots.add(snapshot);
            evidence.sourceCounts[index] = new int[] {map.total(), map.projected(),
                snapshot.connectors().size(), map.graphGroups()};
            System.out.printf(Locale.ROOT,
                "map[%d] %-28s sourceNodes=%6d projectedNodes=%4d connectors=%4d graphGroups=%d%n",
                Integer.valueOf(index + 1), mapFiles[index].getFileName(),
                Integer.valueOf(map.total()), Integer.valueOf(map.projected()),
                Integer.valueOf(snapshot.connectors().size()),
                Integer.valueOf(map.graphGroups()));
        }

        final WorkspaceId workspaceId = WorkspaceId.of(UUID.nameUUIDFromBytes(
            "real-workspace-probe".getBytes(StandardCharsets.UTF_8)));
        final WorkspaceDocument document = WorkspaceDocument.createVersion1(workspaceId)
            .toBuilder()
            .maps(registrations)
            .relationships(new ArrayList<GraphRelationshipRecord>())
            .pins(new ArrayList<PinRecord>())
            .viewport(Viewport.of(0.0, 0.0, 1.0, NO_UNKNOWN_XML))
            .displaySettings(DisplaySettings.of(true, DisplaySettings.CanvasTheme.FOLLOW_FREEPLANE,
                true, true, DisplaySettings.DEFAULT_MAP_SIDEBAR_WIDTH, false, NO_UNKNOWN_XML))
            .unknownXml(NO_UNKNOWN_XML)
            .build();

        evidence.workspaceId = document.id();
        evidence.relationshipCount = document.relationships().size();
        evidence.availableMaps = Collections.frequency(
            new ArrayList<MapAvailability>(availability.values()), MapAvailability.AVAILABLE);
        final ProjectionInput input = ProjectionInput.of(1L, document, snapshots, availability);
        final GraphProjection projection = new ProjectionEngine().project(input);
        System.out.printf(Locale.ROOT,
            "projection: nodes=%d edges=%d enclosures=%d pins=%d%n",
            Integer.valueOf(projection.nodes().size()), Integer.valueOf(projection.edges().size()),
            Integer.valueOf(projection.enclosures().size()),
            Integer.valueOf(projection.pins().size()));

        final AwtGeometryTextMetrics metrics = new AwtGeometryTextMetrics(
            new Font("Dialog", Font.PLAIN, 12), new FontRenderContext(null, true, true));
        final LayoutRequest request = LayoutRequest.of(workspaceId, projection,
            ProjectionDiff.between(projection, projection), projection.pins());
        final LayoutFrame settled = settle(request, evidence);
        if (settled == null || settled.failed()) {
            throw new AssertionError("Reported workspace did not publish a successful frame");
        }
        evidence.projection = projection;
        evidence.settledFrame = settled;
        evidence.settledPositions = settled.positions();
        evidence.correction = settled.edgeConflictDiagnostics();
        evidence.idle = settled.idle().idle();
        evidence.residualViolations = settled.residualViolations();
        final Set<ProjectedEndpointKey> visible = ProjectedEndpointVisibility.visibleEndpoints(
            projection.nodes(), projection.enclosures());

        System.out.println();
        System.out.println("=== projected discs (settled positions) ===");
        for (final ProjectedNode node : projection.nodes()) {
            final NodeGeometry nodeGeometry = new GraphGeometryEngine()
                .computeHulls(projection, settled.positions(), metrics).nodes().get(node.key());
            System.out.printf(Locale.ROOT, "  %-42s label=%-46s center=%s r=%s group=%s%n",
                node.key().source().persistent()
                    ? node.key().source().persistedReference().get().nodeId().toString() : "?",
                cut(node.label().displayText()),
                nodeGeometry == null ? "?" : nodeGeometry.center().toString(),
                nodeGeometry == null ? "?" : Double.valueOf(nodeGeometry.radius()),
                Boolean.valueOf(node.graphGroup()));
        }
        System.out.println();
        System.out.println("=== projected edges ===");
        for (final ProjectedEdge projectedEdge : projection.edges()) {
            System.out.println("  " + projectedEdge.key() + "  " + describe(projection, projectedEdge));
        }

        final EdgeConflictDiagnostics edge = settled.edgeConflictDiagnostics();
        System.out.println();
        System.out.println("=== EdgeConflictCorrection (node relocation) on the settled frame ===");
        System.out.printf(Locale.ROOT,
            "  conflictsDetected=%d (crossings=%d edgeOverNode=%d)%n",
            Integer.valueOf(edge.conflictsDetected()), Integer.valueOf(edge.crossingsDetected()),
            Integer.valueOf(edge.edgeOverNodeDetected()));
        System.out.printf(Locale.ROOT, "  crossingsResolved=%d edgeOverNodeResolved=%d residual=%d%n",
            Integer.valueOf(edge.crossingsResolved()),
            Integer.valueOf(edge.edgeOverNodeResolved()),
            Integer.valueOf(edge.conflictsResidual().size()));
        System.out.printf(Locale.ROOT, "  rounds=%d verifications=%d repairs=%d%n",
            Integer.valueOf(edge.rounds()), Integer.valueOf(edge.verifications()),
            Integer.valueOf(edge.repairsApplied().size()));
        System.out.printf(Locale.ROOT,
            "  detectionTruncated=%s indexBudgetExhausted=%s verificationBudgetExhausted=%s%n",
            Boolean.valueOf(edge.detectionTruncated()),
            Boolean.valueOf(edge.indexBudgetExhausted()),
            Boolean.valueOf(edge.verificationBudgetExhausted()));
        for (final EdgeConflictDiagnostics.Repair repair : edge.repairsApplied()) {
            for (final org.freeplane.plugin.graph.projection.ProjectedNodeKey key : repair.nodeKeys()) {
                final LayoutPoint from = repair.fromCentres().get(key);
                final LayoutPoint to = repair.toCentres().get(key);
                System.out.printf(Locale.ROOT,
                    "  repair %s batch=%d node=%s move=(%.3f,%.3f) |d|=%.3f%n",
                    repair.kind(), Integer.valueOf(repair.batchIndex()),
                    key.source().persistent()
                        ? key.source().persistedReference().get().nodeId().toString() : "?",
                    Double.valueOf(to.x() - from.x()), Double.valueOf(to.y() - from.y()),
                    Double.valueOf(Math.hypot(to.x() - from.x(), to.y() - from.y())));
            }
        }
        for (final String residual : edge.conflictsResidual()) {
            System.out.println("  residual: " + residual);
        }
        System.out.printf(Locale.ROOT,
            "  indexInsertedItems=%d indexPairChecks=%d verificationWork=%d subdivisions=%d%n",
            Long.valueOf(edge.indexInsertedItems()), Long.valueOf(edge.indexPairChecks()),
            Long.valueOf(edge.verificationWork()), Integer.valueOf(edge.indexSubdivisions()));

        final GraphGeometry geometry = new GraphGeometryEngine().computeHulls(projection,
            settled.positions(), metrics);
        final EdgePaths paths = new EdgeRouter().route(projection, settled.positions(), geometry,
            visible);
        evidence.settledGeometry = geometry;
        evidence.routedGeometry = geometry;
        evidence.paths = paths;
        System.out.println();
        System.out.println("=== EdgeRouter on the settled (post-relocation) frame ===");
        System.out.printf(Locale.ROOT, "  straights=%d bows=%d doglegs=%d fallbacks=%d%n",
            Integer.valueOf(paths.diagnostics().edgesStraight()),
            Integer.valueOf(paths.diagnostics().edgesBow()),
            Integer.valueOf(paths.diagnostics().edgesDogleg()),
            Integer.valueOf(paths.diagnostics().edgesFallback()));
        System.out.printf(Locale.ROOT,
            "  routeBudgetExhausted=%s detectionTruncated=%s obstacleChecks=%d pairChecks=%d indexItems=%d%n",
            Boolean.valueOf(paths.diagnostics().routeBudgetExhausted()),
            Boolean.valueOf(paths.diagnostics().detectionTruncated()),
            Long.valueOf(paths.diagnostics().routeObstacleChecks()),
            Long.valueOf(paths.diagnostics().routePairChecks()),
            Long.valueOf(paths.diagnostics().routeIndexItems()));

        System.out.println();
        System.out.println("=== geometry of the reported edge ===");
        final GraphGeometry plain = new GraphGeometryEngine().computeHulls(projection,
            settled.positions(), metrics);
        for (final ProjectedEdge projectedEdge : projection.edges()) {
            if (!isReportedEdge(projectedEdge)) {
                continue;
            }
            final LayoutPoint first = anchorOf(projectedEdge.first(), plain);
            final LayoutPoint second = anchorOf(projectedEdge.second(), plain);
            final double chord = Math.hypot(second.x() - first.x(), second.y() - first.y());
            System.out.printf(Locale.ROOT, "  chord length between attachment points = %.3f wu%n",
                Double.valueOf(chord));
            System.out.printf(Locale.ROOT,
                "  required clearance = r + 3 = 11 wu; max bow apex = 0.25*L = %.3f wu;"
                    + " max dogleg offset = 0.30*L = %.3f wu%n",
                Double.valueOf(0.25 * chord), Double.valueOf(0.30 * chord));
            for (final ProjectedNode node : projection.nodes()) {
                if (isEndpoint(projectedEdge, node)) {
                    continue;
                }
                final NodeGeometry g = plain.nodes().get(node.key());
                if (g == null) {
                    continue;
                }
                final double perp = perpendicularDistance(g.center(), first, second);
                final double toFirst = Math.hypot(g.center().x() - first.x(),
                    g.center().y() - first.y());
                final double toSecond = Math.hypot(g.center().x() - second.x(),
                    g.center().y() - second.y());
                final double nearestAllowed = g.radius() + EdgeRouter.EDGE_NODE_CLEARANCE;
                if (Math.min(Math.min(perp, toFirst), toSecond) < nearestAllowed + 40.0) {
                    System.out.printf(Locale.ROOT,
                        "  disc %s r=%.2f  perpOffset=%.3f  distToFirstAnchor=%.3f"
                            + "  distToSecondAnchor=%.3f  required=%.3f  routable=%s%n",
                        node.key().source().persistent()
                            ? node.key().source().persistedReference().get().nodeId().toString() : "?",
                        Double.valueOf(g.radius()), Double.valueOf(perp), Double.valueOf(toFirst),
                        Double.valueOf(toSecond), Double.valueOf(nearestAllowed),
                        Boolean.valueOf(toFirst > nearestAllowed && toSecond > nearestAllowed
                            && perp > nearestAllowed));
                }
            }
        }

        System.out.println();
        System.out.println("=== published-path clearance audit (radius + 3 wu) ===");
        final List<String> violations = audit(projection, geometry, paths, evidence);
        evidence.clearanceViolations = Collections.unmodifiableList(violations);
        System.out.printf(Locale.ROOT, "  edges violating node clearance: %d of %d%n",
            Integer.valueOf(violations.size()), Integer.valueOf(projection.edges().size()));
        for (final String violation : violations) {
            System.out.println("   - " + violation);
        }
        System.out.printf(Locale.ROOT, "  auditedEdges=%d auditedDiscPairs=%d%n",
            Integer.valueOf(evidence.auditedEdges), Integer.valueOf(evidence.auditedDiscPairs));
        printPrecisionEvidence(evidence);
        System.out.println("REPLAY VERDICT: " + (violations.isEmpty()
            && evidence.auditedEdges == projection.edges().size()
            ? "all published paths clear" : violations.size() + " edges violate clearance: "
                + violations));
        return evidence;
    }

    private static boolean isReportedEdge(final ProjectedEdge edge) {
        final String first = edge.first().toString();
        final String second = edge.second().toString();
        return (first.contains("000000000001") && second.contains("000000000001"))
            && edge.toString().contains("ProjectedEdgeKey");
    }

    private static LayoutPoint anchorOf(final ProjectedEndpointKey endpoint,
            final GraphGeometry geometry) {
        if (endpoint.toString().contains("kind=node")) {
            for (final java.util.Map.Entry<org.freeplane.plugin.graph.projection.ProjectedNodeKey,
                    NodeGeometry> entry : geometry.nodes().entrySet()) {
                if (ProjectedEndpointKey.ofNode(entry.getKey()).equals(endpoint)) {
                    return entry.getValue().center();
                }
            }
        }
        return LayoutPoint.of(0.0, 0.0);
    }

    private static double perpendicularDistance(final LayoutPoint point, final LayoutPoint first,
            final LayoutPoint second) {
        final double dx = second.x() - first.x();
        final double dy = second.y() - first.y();
        final double length = Math.hypot(dx, dy);
        if (length <= 0.0) {
            return Math.hypot(point.x() - first.x(), point.y() - first.y());
        }
        return Math.abs((point.x() - first.x()) * dy - (point.y() - first.y()) * dx) / length;
    }

    private static String cut(final String value) {
        final String single = value.replace('\n', ' ');
        return single.length() <= 44 ? single : single.substring(0, 41) + "...";
    }

    private static String describe(final GraphProjection projection, final ProjectedEdge edge) {
        final StringBuilder text = new StringBuilder();
        text.append(labelOf(projection, edge.first())).append(" -> ")
            .append(labelOf(projection, edge.second()));
        return text.toString();
    }

    private static String labelOf(final GraphProjection projection, final ProjectedEndpointKey endpoint) {
        for (final ProjectedNode node : projection.nodes()) {
            if (ProjectedEndpointKey.ofNode(node.key()).equals(endpoint)) {
                return (node.key().source().persistent()
                    ? node.key().source().persistedReference().get().nodeId().toString() : "?")
                    + "[" + cut(node.label().displayText()) + "]";
            }
        }
        return endpoint.toString();
    }

    private static List<String> audit(final GraphProjection projection, final GraphGeometry geometry,
            final EdgePaths paths, final ReplayEvidence evidence) {
        final List<String> result = new ArrayList<String>();
        for (final ProjectedEdge edge : projection.edges()) {
            final EdgePath path = paths.path(edge.key()).orElse(null);
            if (path == null) {
                result.add("no path for " + CanonicalKeys.edge(edge.key()));
                continue;
            }
            double worst = 0.0;
            double minimumSurfaceClearance = Double.POSITIVE_INFINITY;
            String worstNode = null;
            for (final ProjectedNode node : projection.nodes()) {
                if (isEndpoint(edge, node)) {
                    continue;
                }
                final NodeGeometry nodeGeometry = geometry.nodes().get(node.key());
                if (nodeGeometry == null) {
                    throw new AssertionError("Missing routed disc: " + CanonicalKeys.node(node.key()));
                }
                final double surfaceClearance = distanceToPath(path, nodeGeometry.center())
                    - nodeGeometry.radius();
                evidence.auditedDiscPairs++;
                minimumSurfaceClearance = Math.min(minimumSurfaceClearance, surfaceClearance);
                final double clearance = surfaceClearance - EdgeRouter.EDGE_NODE_CLEARANCE;
                if (!Double.isFinite(surfaceClearance)) {
                    throw new AssertionError("Non-finite clearance for " + CanonicalKeys.edge(edge.key()));
                }
                if (clearance < -1.0e-9 && clearance < worst) {
                    worst = clearance;
                    worstNode = CanonicalKeys.node(node.key());
                }
            }
            evidence.auditedEdges++;
            evidence.minimumSurfaceClearances.put(CanonicalKeys.edge(edge.key()),
                Double.valueOf(minimumSurfaceClearance));
            if (worstNode != null) {
                result.add(String.format(Locale.ROOT,
                    "%s shape=%s disc=%s shortfall=%.17gwu",
                    CanonicalKeys.edge(edge.key()), path.shape(), worstNode, Double.valueOf(-worst)));
            }
        }
        return result;
    }

    private static void printPrecisionEvidence(final ReplayEvidence evidence) {
        System.out.println();
        System.out.println("=== full-precision replay signatures (hexadecimal doubles) ===");
        for (final ProjectedNode node : evidence.projection.nodes()) {
            final NodeGeometry geometry = evidence.routedGeometry.nodes().get(node.key());
            System.out.println("NODE " + CanonicalKeys.node(node.key()) + " centre="
                + hex(geometry.center()) + " radius=" + Double.toHexString(geometry.radius()));
        }
        for (final EdgeConflictDiagnostics.Repair repair : evidence.correction.repairsApplied()) {
            for (final org.freeplane.plugin.graph.projection.ProjectedNodeKey key : repair.nodeKeys()) {
                System.out.println("REPAIR " + repair.kind() + " batch=" + repair.batchIndex()
                    + " node=" + CanonicalKeys.node(key) + " from=" + hex(repair.fromCentres().get(key))
                    + " to=" + hex(repair.toCentres().get(key)));
            }
        }
        for (final ProjectedEdge edge : evidence.projection.edges()) {
            final String key = CanonicalKeys.edge(edge.key());
            final EdgePath path = evidence.paths.path(edge.key()).get();
            System.out.println("PATH " + key + " shape=" + path.shape() + " defining="
                + hex(path.definingPoints()) + " samples=" + hex(path.samples())
                + " minimumSurfaceClearance="
                + Double.toHexString(evidence.minimumSurfaceClearances.get(key).doubleValue()));
        }
    }

    private static String hex(final LayoutPoint point) {
        return "(" + Double.toHexString(point.x()) + "," + Double.toHexString(point.y()) + ")";
    }

    private static String hex(final List<LayoutPoint> points) {
        final List<String> result = new ArrayList<String>();
        for (final LayoutPoint point : points) {
            result.add(hex(point));
        }
        return result.toString();
    }

    static final class ReplayEvidence {
        GraphProjection projection;
        LayoutFrame settledFrame;
        LayoutPositions settledPositions;
        GraphGeometry settledGeometry;
        EdgeConflictDiagnostics correction;
        GraphGeometry routedGeometry;
        EdgePaths paths;
        int steps;
        boolean idle;
        int residualViolations;
        List<String> clearanceViolations;
        int auditedEdges;
        int auditedDiscPairs;
        int[][] sourceCounts;
        int availableMaps;
        WorkspaceId workspaceId;
        int relationshipCount;
        final Map<String, Double> minimumSurfaceClearances = new LinkedHashMap<String, Double>();
    }

    private static boolean isEndpoint(final ProjectedEdge edge, final ProjectedNode node) {
        return edge.first().equals(ProjectedEndpointKey.ofNode(node.key()))
            || edge.second().equals(ProjectedEndpointKey.ofNode(node.key()));
    }

    private static double distanceToPath(final EdgePath path, final LayoutPoint point) {
        final List<LayoutPoint> samples = path.samples();
        double best = Double.MAX_VALUE;
        for (int index = 1; index < samples.size(); index++) {
            best = Math.min(best,
                distanceToSegment(point, samples.get(index - 1), samples.get(index)));
        }
        return best;
    }

    private static double distanceToSegment(final LayoutPoint point, final LayoutPoint first,
            final LayoutPoint second) {
        final double dx = second.x() - first.x();
        final double dy = second.y() - first.y();
        final double lengthSquared = dx * dx + dy * dy;
        if (lengthSquared <= 0.0) {
            return Math.hypot(point.x() - first.x(), point.y() - first.y());
        }
        double t = ((point.x() - first.x()) * dx + (point.y() - first.y()) * dy) / lengthSquared;
        t = Math.max(0.0, Math.min(1.0, t));
        return Math.hypot(point.x() - (first.x() + t * dx), point.y() - (first.y() + t * dy));
    }

    private static LayoutFrame settle(final LayoutRequest request, final ReplayEvidence evidence)
            throws Exception {
        final LayoutWorker worker = new LayoutWorker(LayoutCalibration.spikeDefaults());
        try {
            LayoutFrame frame = worker.submit(request).toCompletableFuture().get();
            final long deadline = System.nanoTime() + 600_000_000_000L;
            int steps = 0;
            while (frame != null && !frame.failed() && !frame.idle().idle()
                    && System.nanoTime() < deadline) {
                frame = worker.step().toCompletableFuture().get();
                steps++;
                if (steps % 1000 == 0) {
                    System.out.printf(Locale.ROOT, "  ... step %d%n", Integer.valueOf(steps));
                }
            }
            evidence.steps = steps;
            System.out.printf(Locale.ROOT, "settle: steps=%d idle=%s residualViolations=%d%n",
                Integer.valueOf(steps),
                Boolean.valueOf(frame != null && frame.idle().idle()),
                Integer.valueOf(frame == null ? -1 : frame.residualViolations()));
            return frame;
        }
        finally {
            worker.close();
        }
    }

    private static final class SourceMap {
        private final List<SourceNode> roots = new ArrayList<SourceNode>();
        private int graphGroupCount;

        private static SourceMap load(final Path file) throws Exception {
            final String data = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            final SourceMap map = new SourceMap();
            final List<SourceNode> stack = new ArrayList<SourceNode>();
            final Map<String, SourceNode> byId = new LinkedHashMap<String, SourceNode>();
            final Matcher matcher = TOKEN.matcher(data);
            while (matcher.find()) {
                final boolean closing = !matcher.group(1).isEmpty();
                final String name = matcher.group(2);
                final boolean selfClosing = !matcher.group(4).isEmpty();
                if ("node".equals(name)) {
                    if (closing) {
                        if (!stack.isEmpty()) {
                            stack.remove(stack.size() - 1);
                        }
                        continue;
                    }
                    final Map<String, String> attributes = attributes(matcher.group(3));
                    final SourceNode node = new SourceNode();
                    node.id = attributes.get("ID");
                    final String text = attributes.get("TEXT");
                    node.text = unescape(text == null ? "" : text);
                    node.folded = "true".equals(attributes.get("FOLDED"));
                    final SourceNode parent = stack.isEmpty() ? null : stack.get(stack.size() - 1);
                    node.parentHidden = parent != null && parent.hidden;
                    node.hidden = node.parentHidden || node.folded;
                    if (parent == null) {
                        map.roots.add(node);
                    }
                    else {
                        parent.children.add(node);
                    }
                    if (node.id != null) {
                        byId.put(node.id, node);
                    }
                    if (!selfClosing) {
                        stack.add(node);
                    }
                }
                else if ("arrowlink".equals(name)) {
                    if (stack.isEmpty()) {
                        continue;
                    }
                    final Map<String, String> attributes = attributes(matcher.group(3));
                    final SourceNode owner = stack.get(stack.size() - 1);
                    owner.arrows.add(attributes.get("DESTINATION"));
                }
                else if ("graph_group".equals(name)) {
                    if (!stack.isEmpty()) {
                        stack.get(stack.size() - 1).graphGroup = true;
                    }
                }
            }
            final List<SourceNode> all = new ArrayList<SourceNode>();
            for (final SourceNode root : map.roots) {
                root.collect(all);
            }
            map.total = all.size();
            for (final SourceNode node : all) {
                if (node.graphGroup) {
                    map.graphGroupCount++;
                }
                if (!node.hidden) {
                    map.projected++;
                }
            }
            // Summary-node emulation: an unfolded, childless, text-empty node is hidden.
            for (final SourceNode node : all) {
                if (!node.hidden && node.children.isEmpty() && node.text.isEmpty()) {
                    node.summaryHidden = true;
                    map.hiddenBySummary++;
                }
            }
            return map;
        }

        private final List<SourceNode> all = new ArrayList<SourceNode>();
        private int total;
        private int projected;
        private int hiddenBySummary;

        private int total() {
            return total;
        }

        private int projected() {
            return projected;
        }

        private int graphGroups() {
            return graphGroupCount;
        }

        private NodeSnapshot toSnapshot(final MapReferenceId mapId, final List<Integer> path,
                final boolean ancestorExcluded, final Set<PersistedNodeId> ids) {
            final SourceNode root = roots.isEmpty() ? new SourceNode() : roots.get(0);
            return snapshot(root, mapId, path, ancestorExcluded, ids);
        }

        private NodeSnapshot snapshot(final SourceNode node, final MapReferenceId mapId,
                final List<Integer> path, final boolean ancestorExcluded,
                final Set<PersistedNodeId> ids) {
            final boolean excluded = ancestorExcluded || node.summaryHidden;
            final SourceNodeKey key = node.id != null
                ? SourceNodeKey.persisted(NodeReference.of(mapId, PersistedNodeId.of(node.id)))
                : SourceNodeKey.transientPath(mapId, new ArrayList<Integer>(path));
            if (node.id != null && !ids.contains(PersistedNodeId.of(node.id))) {
                ids.add(PersistedNodeId.of(node.id));
            }
            final List<NodeSnapshot> children = new ArrayList<NodeSnapshot>();
            for (int index = 0; index < node.children.size(); index++) {
                final List<Integer> childPath = new ArrayList<Integer>(path);
                childPath.add(Integer.valueOf(index));
                children.add(snapshot(node.children.get(index), mapId, childPath, excluded, ids));
            }
            final SafeNodeLabel label = excluded ? SafeNodeLabel.of("Node", "Node")
                : SafeNodeLabel.of(displayText(node.text), displayText(node.text));
            return NodeSnapshot.of(key, label, node.children.isEmpty(),
                !excluded && node.graphGroup, excluded, children);
        }

        private List<ConnectorSnapshot> toConnectors(final MapReferenceId mapId) {
            final List<SourceNode> ordered = new ArrayList<SourceNode>();
            for (final SourceNode root : roots) {
                root.collect(ordered);
            }
            final Set<String> knownIds = new LinkedHashSet<String>();
            for (final SourceNode node : ordered) {
                if (node.id != null) {
                    knownIds.add(node.id);
                }
            }
            final Map<String, Integer> occurrences = new LinkedHashMap<String, Integer>();
            final List<ConnectorSnapshot> result = new ArrayList<ConnectorSnapshot>();
            for (final SourceNode node : ordered) {
                if (node.id == null || node.summaryHidden) {
                    continue;
                }
                for (final String destination : node.arrows) {
                    if (destination == null || destination.equals(node.id)) {
                        continue;
                    }
                    if (!knownIds.contains(destination)) {
                        continue;
                    }
                    final Integer previous = occurrences.get(node.id);
                    final int occurrence = previous == null ? 0 : previous.intValue();
                    occurrences.put(node.id, Integer.valueOf(occurrence + 1));
                    result.add(ConnectorSnapshot.of(occurrence, ConnectorDescriptor.of(
                        SourceNodeKey.persisted(
                            NodeReference.of(mapId, PersistedNodeId.of(node.id))),
                        NodeReference.of(mapId, PersistedNodeId.of(destination)),
                        false, true, "", "", "")));
                }
            }
            return result;
        }
    }

    private static final class SourceNode {
        private String id;
        private String text = "";
        private boolean folded;
        private boolean hidden;
        private boolean parentHidden;
        private boolean summaryHidden;
        private boolean graphGroup;
        private final List<SourceNode> children = new ArrayList<SourceNode>();
        private final List<String> arrows = new ArrayList<String>();

        private void collect(final List<SourceNode> out) {
            out.add(this);
            for (final SourceNode child : children) {
                child.collect(out);
            }
        }
    }

    private static Map<String, String> attributes(final String source) {
        final Map<String, String> result = new LinkedHashMap<String, String>();
        final Matcher matcher = ATTR.matcher(source);
        while (matcher.find()) {
            result.put(matcher.group(1), matcher.group(2));
        }
        return result;
    }

    private static String displayText(final String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.toLowerCase(Locale.ROOT).contains("<html")) {
            value = value.replaceAll("<br\\s*/?>", " ").replaceAll("<[^>]*>", " ");
            value = value.replace("&nbsp;", " ");
        }
        if (value.startsWith("\\latex") && value.length() > 6
                && Character.isWhitespace(value.charAt(6))) {
            value = value.substring(6).trim();
        }
        value = value.replaceAll("\\s+", " ").trim();
        return value.isEmpty() ? "Node" : value;
    }

    private static String unescape(final String value) {
        return value.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
            .replace("&apos;", "'").replace("&amp;", "&").replace("&#xa;", "\n");
    }
}
