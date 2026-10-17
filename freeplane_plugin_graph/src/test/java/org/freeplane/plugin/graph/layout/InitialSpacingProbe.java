package org.freeplane.plugin.graph.layout;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.geom.Dimension2D;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.freeplane.plugin.graph.geometry.*;
import org.freeplane.plugin.graph.projection.*;
import org.freeplane.plugin.graph.projection.input.*;
import org.freeplane.plugin.graph.workspace.model.*;

public final class InitialSpacingProbe {
    private static final long BUDGET = TimeUnit.SECONDS.toNanos(30L);
    private static final MapReferenceId A = MapReferenceId.of("00000000-0000-0000-0000-000000000001");
    private static final MapReferenceId B = MapReferenceId.of("00000000-0000-0000-0000-000000000002");
    private static final GeometryTextMetrics METRICS = new AwtGeometryTextMetrics(
        new Font("Dialog", Font.PLAIN, 12), new FontRenderContext(null, true, true));

    private static NodeReference ref(MapReferenceId map, String id) {
        return NodeReference.of(map, PersistedNodeId.of(id));
    }
    private static ProjectedNodeKey key(MapReferenceId map, String id) {
        return ProjectedNodeKey.of(SourceNodeKey.persisted(ref(map, id)));
    }
    private static EnclosureHullKey hull(MapReferenceId map, String id) {
        return EnclosureHullKey.of(Collections.singletonList(
            EnclosureKey.of(SourceNodeKey.persisted(ref(map, id)))));
    }
    private static String id(ProjectedNodeKey key) {
        return key.source().persistedReference().get().nodeId().value();
    }
    private static void row(PrintWriter out, Object... values) {
        for (int i = 0; i < values.length; i++) {
            if (i != 0) out.print('\t');
            out.print(values[i]);
        }
        out.println();
    }
    private static PrintWriter writer(Path out, String file) throws Exception {
        return new PrintWriter(Files.newBufferedWriter(out.resolve(file), StandardCharsets.UTF_8));
    }
    private static GraphProjection fixture(String name) {
        if (!Arrays.asList("NR8", "SM8", "XM4x2").contains(name))
            throw new IllegalArgumentException("Unknown frozen fixture " + name);
        List<ProjectedNode> nodes = new ArrayList<ProjectedNode>();
        List<ProjectedEnclosure> enclosures = new ArrayList<ProjectedEnclosure>();
        List<ProjectedEdge> edges = new ArrayList<ProjectedEdge>();
        int maps = name.equals("XM4x2") ? 2 : 1;
        int count = maps == 2 ? 4 : 8;
        for (int m = 0; m < maps; m++) {
            MapReferenceId map = m == 0 ? A : B;
            String prefix = m == 0 ? "A" : "B";
            List<ProjectedNodeKey> direct = new ArrayList<ProjectedNodeKey>();
            for (int n = 0; n < count; n++) {
                String node = prefix + n;
                ProjectedNodeKey k = key(map, node);
                direct.add(k);
                nodes.add(ProjectedNode.of(k, SafeNodeLabel.of(node, node), "Map " + prefix, true));
            }
            String root = prefix + "_root";
            EnclosureHullKey h = hull(map, root);
            enclosures.add(ProjectedEnclosure.of(h, h.endpointKeys(),
                Collections.singletonList(SafeNodeLabel.of(root, root)), "Map " + prefix,
                Optional.<EnclosureHullKey>empty(), direct,
                Collections.<EnclosureHullKey>emptyList(), true, BoundaryTier.EMPHATIC));
        }
        if (name.equals("SM8")) {
            for (int n = 0; n < 8; n++) {
                ProjectedEndpointKey from = ProjectedEndpointKey.ofNode(key(A, "A" + n));
                ProjectedEndpointKey to = ProjectedEndpointKey.ofNode(key(A, "A" + ((n + 1) % 8)));
                ConnectorDescriptor d = ConnectorDescriptor.of(SourceNodeKey.persisted(ref(A, "A" + n)),
                    ref(A, "A" + ((n + 1) % 8)), false, true, "", "", "");
                edges.add(ProjectedEdge.of(ProjectedEdgeKey.of(from, to), Collections.singletonList(
                    EdgeContributor.nativeConnector(ConnectorSnapshot.of(0, d), from, to))));
            }
        }
        if (name.equals("XM4x2")) {
            ProjectedEndpointKey from = ProjectedEndpointKey.ofNode(key(A, "A2"));
            ProjectedEndpointKey to = ProjectedEndpointKey.ofNode(key(B, "B0"));
            GraphRelationshipRecord r = GraphRelationshipRecord.of(RelationshipId.of(
                "00000000-0000-0000-0000-000000016031"), 1L, ref(A, "A2"), ref(B, "B0"),
                RelationshipDirection.FORWARD, Collections.<UnknownXml>emptyList());
            edges.add(ProjectedEdge.of(ProjectedEdgeKey.of(from, to), Collections.singletonList(
                EdgeContributor.graphRelationship(r, from, to))));
        }
        return GraphProjection.projected(1L, nodes, enclosures, edges,
            Collections.<RelationshipResolution>emptyList(), Collections.<PinProjection>emptyList());
    }
    private static WorkspaceId workspace(String name) {
        String suffix = name.equals("NR8") ? "1601" : name.equals("SM8") ? "1602" : "1603";
        return WorkspaceId.of("00000000-0000-0000-0000-00000000" + suffix);
    }
    private static void manifest(Path out, String name, GraphProjection p) throws Exception {
        try (PrintWriter w = writer(out, "inputs.tsv")) {
            row(w, "fixture", name, "generation", 1, "workspace", workspace(name).value());
            row(w, "calibration", 1.0, 0.30, 1.0, "zoom", 1.0, "pins", 0, "resolutions", 0);
            row(w, "metrics", "Dialog", "PLAIN", 12, "identityFRC", true, true, "locale", "en_US", "UTC");
            row(w, "idle", 8, 0.05, 0.10, "stepLimit", 2000, "phaseNanos", BUDGET, "diff", "0->1");
            for (int i = 0; i < p.nodes().size(); i++) {
                ProjectedNode n = p.nodes().get(i);
                NodeProminence v = p.prominence().get(n.key());
                int outgoing = name.equals("SM8") || name.equals("XM4x2") && id(n.key()).equals("A2") ? 1 : 0;
                if (v.scale() != 1.0 || v.visibleOutgoingTargets() != outgoing)
                    throw new AssertionError("Frozen prominence changed");
                row(w, "node", i, CanonicalKeys.node(n.key()), n.label().displayText(), n.mapName(),
                    n.graphGroup(), v.visibleOutgoingTargets(), v.scale(), "radius", 8.0,
                    n.key().mapReferenceId().value(), id(n.key()));
            }
            for (int i = 0; i < p.enclosures().size(); i++) {
                ProjectedEnclosure e = p.enclosures().get(i);
                row(w, "enclosure", i, CanonicalKeys.hull(e.hullKey()), e.mapName(), e.mapRoot(),
                    e.boundaryTier(), "parent=none", "children=none", e.labels().get(0).displayText(),
                    e.mapReferenceId().value(), e.labels().get(0).displayText());
                for (int j = 0; j < e.directNodes().size(); j++)
                    row(w, "direct", i, j, CanonicalKeys.node(e.directNodes().get(j)));
                Dimension2D d = METRICS.measure(e.labels().get(0).displayText(), e.boundaryTier());
                row(w, "text", i, d.getWidth(), d.getHeight());
            }
            for (int i = 0; i < p.edges().size(); i++) {
                ProjectedEdge e = p.edges().get(i);
                EdgeContributor c = e.contributors().get(0);
                row(w, "edge", i, CanonicalKeys.edge(e.key()), c.key(),
                    CanonicalKeys.endpoint(c.projectedSource()), CanonicalKeys.endpoint(c.projectedTarget()),
                    c.arrowAtSource(), c.arrowAtTarget(), "labels=empty", "unknownXml=empty",
                    c.graphRelationship().isPresent() ? "sequence=1" : "occurrence=0");
            }
            if (w.checkError()) throw new IllegalStateException("Input receipt write failed");
        }
    }
    private static LayoutFrame await(CompletionStage<LayoutFrame> stage, long deadline) throws Exception {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0L) throw new TimeoutException("Phase deadline before wait");
        LayoutFrame frame = stage.toCompletableFuture().get(remaining, TimeUnit.NANOSECONDS);
        if (System.nanoTime() - deadline > 0L) throw new TimeoutException("Phase deadline after wait");
        if (frame == null || frame.failed() || !frame.verified())
            throw new AssertionError("Failed or unverified corrected worker frame");
        return frame;
    }
    private static double gap(NodeGeometry a, NodeGeometry b) {
        if (a == null || b == null || !Double.isFinite(a.center().x()) || !Double.isFinite(a.center().y())
                || !Double.isFinite(b.center().x()) || !Double.isFinite(b.center().y())
                || !Double.isFinite(a.radius()) || !Double.isFinite(b.radius())
                || a.radius() <= 0.0 || b.radius() <= 0.0)
            throw new AssertionError("Missing or non-finite node geometry");
        double dx = a.center().x() - b.center().x();
        double dy = a.center().y() - b.center().y();
        double distance = Math.hypot(dx, dy);
        double value = distance - a.radius() - b.radius();
        if (!Double.isFinite(dx) || !Double.isFinite(dy) || !Double.isFinite(distance) || !Double.isFinite(value))
            throw new AssertionError("Non-finite gap");
        return value;
    }
    private static double[] nearestGaps(ProjectedEnclosure e, GraphGeometry g) {
        if (e.directNodes().size() < 2) throw new AssertionError("Changed frozen population");
        double[] nearest = new double[e.directNodes().size()];
        for (int i = 0; i < nearest.length; i++) {
            nearest[i] = Double.POSITIVE_INFINITY;
            for (int j = 0; j < nearest.length; j++) {
                if (i != j) nearest[i] = Math.min(nearest[i],
                    gap(g.nodes().get(e.directNodes().get(i)), g.nodes().get(e.directNodes().get(j))));
            }
            if (!Double.isFinite(nearest[i])) throw new AssertionError("Invalid nearest gap");
        }
        return nearest;
    }
    private static void finiteMotion(double... values) {
        for (double value : values)
            if (!Double.isFinite(value) || value < 0.0) throw new AssertionError("Invalid motion diagnostic");
    }
    private static void emit(GraphProjection p, LayoutFrame f, String phase, int ordinal, int steps,
            long started, PrintWriter frames, PrintWriter nodes, PrintWriter anchors, PrintWriter edges) {
        Set<ProjectedNodeKey> nodeKeys = new LinkedHashSet<ProjectedNodeKey>();
        for (ProjectedNode n : p.nodes()) nodeKeys.add(n.key());
        Set<EnclosureHullKey> hullKeys = new LinkedHashSet<EnclosureHullKey>();
        for (ProjectedEnclosure e : p.enclosures()) hullKeys.add(e.hullKey());
        if (!f.positions().nodes().keySet().equals(nodeKeys) || !f.positions().anchors().keySet().equals(hullKeys))
            throw new AssertionError("Exact position coverage mismatch");
        GraphGeometry g = new GraphGeometryEngine().computeHulls(p, f.positions(), METRICS);
        if (!g.nodes().keySet().equals(nodeKeys) || !g.hulls().keySet().equals(hullKeys))
            throw new AssertionError("Exact geometry coverage mismatch");
        Set<ProjectedEndpointKey> visible = ProjectedEndpointVisibility.visibleEndpoints(p.nodes(), p.enclosures());
        EdgeConflictCorrection.Detection conflicts = EdgeConflictCorrection.detectConflicts(p, f.positions(), g, visible);
        List<EdgePath> paths = new ArrayList<EdgePath>(new EdgeRouter().route(p, f.positions(), g, visible).byEdge().values());
        int painted = 0;
        for (int i = 0; i < paths.size(); i++)
            for (int j = i + 1; j < paths.size(); j++)
                if (EdgeRouter.properlyCross(paths.get(i), paths.get(j))) painted++;
        int boundary = new BoundaryInvariants().detect(p, g).size();
        int collision = NodeSeparationProjection.violatingPairs(p, f.positions()).size();
        for (ProjectedEnclosure e : p.enclosures()) {
            double[] nearest = nearestGaps(e, g);
            double[] sorted = nearest.clone();
            Arrays.sort(sorted);
            int mid = sorted.length / 2;
            double median = sorted.length % 2 == 0 ? 0.5 * sorted[mid - 1] + 0.5 * sorted[mid] : sorted[mid];
            if (!Double.isFinite(median)) throw new AssertionError("Non-finite median");
            for (int i = 0; i < e.directNodes().size(); i++) {
                ProjectedNodeKey k = e.directNodes().get(i);
                NodeGeometry n = g.nodes().get(k);
                if (n == null || n.radius() != 8.0) throw new AssertionError("Missing node or changed radius");
                row(nodes, phase, ordinal, k.mapReferenceId().value(), id(k),
                    e.labels().get(0).displayText(), n.center().x(), n.center().y(), n.radius(),
                    nearest[i], sorted[0], median);
            }
            LayoutPoint a = f.positions().anchors().get(e.hullKey());
            if (a == null || !Double.isFinite(a.x()) || !Double.isFinite(a.y()))
                throw new AssertionError("Missing or non-finite anchor");
            row(anchors, phase, ordinal, e.mapReferenceId().value(), e.labels().get(0).displayText(), a.x(), a.y());
        }
        for (ProjectedEdge e : p.edges()) {
            NodeGeometry a = g.nodes().get(e.first().node().get());
            NodeGeometry b = g.nodes().get(e.second().node().get());
            row(edges, phase, ordinal, CanonicalKeys.edge(e.key()), gap(a, b) + a.radius() + b.radius(), gap(a, b));
        }
        BoundarySeparationDiagnostics b = f.boundaryDiagnostics();
        EdgeConflictDiagnostics e = f.edgeConflictDiagnostics();
        finiteMotion(f.idle().rms(), f.idle().max(), b.displacementRms(), b.displacementMax(),
            b.deltaRms(), b.deltaMax());
        boolean endpointSafe = !f.failed() && f.verified() && b.boundaryVerified() && b.boundaryCovered()
            && f.residualViolations() == 0 && b.hullResidualViolations() == 0 && e.conflictsResidual().isEmpty()
            && !e.verificationBudgetExhausted() && !e.indexBudgetExhausted() && !e.detectionTruncated()
            && boundary == 0 && collision == 0 && conflicts.crossings() == 0
            && conflicts.edgeOverNodes() == 0 && painted == 0;
        if (phase.equals("first-idle")) endpointSafe &= f.idle().idle()
            && f.idle().consecutiveStableFrames() >= 8 && f.idle().rms() <= 0.05 && f.idle().max() <= 0.10;
        long validationElapsed = System.nanoTime() - started;
        row(frames, phase, ordinal, steps, f.stepIndex(), validationElapsed, f.failed(),
            f.verified(), f.idle().idle(), f.idle().consecutiveStableFrames(), f.idle().rms(), f.idle().max(),
            f.residualViolations(), b.hullResidualViolations(), b.boundaryVerified(), b.boundaryCovered(),
            e.conflictsResidual().size(), e.verificationBudgetExhausted(), e.indexBudgetExhausted(),
            e.detectionTruncated(), boundary, collision, conflicts.crossings(), conflicts.edgeOverNodes(), painted,
            b.displacementRms(), b.displacementMax(), b.deltaRms(), b.deltaMax(), endpointSafe);
        frames.flush(); nodes.flush(); anchors.flush(); edges.flush();
        if (frames.checkError() || nodes.checkError() || anchors.checkError() || edges.checkError())
            throw new IllegalStateException("Measurement receipt write failed");
        if (!phase.equals("pre-idle") && !endpointSafe) throw new AssertionError("Unsafe endpoint; see frame receipt");
    }
    private static void run(String name, Path out) throws Exception {
        Files.createDirectories(out);
        GraphProjection p = fixture(name);
        manifest(out, name, p);
        GraphProjection empty = GraphProjection.projected(0L, Collections.<ProjectedNode>emptyList(),
            Collections.<ProjectedEnclosure>emptyList(), Collections.<ProjectedEdge>emptyList(),
            Collections.<RelationshipResolution>emptyList(), Collections.<PinProjection>emptyList());
        try (PrintWriter frames = writer(out, "frames.tsv"); PrintWriter nodes = writer(out, "nodes.tsv");
             PrintWriter anchors = writer(out, "anchors.tsv"); PrintWriter edges = writer(out, "edges.tsv")) {
            row(frames, "phase", "ordinal", "steps", "engineStep", "validationElapsedNanos", "failed", "verified",
                "idle", "stable", "rms", "max", "nodeResidual", "hullResidual", "boundaryVerified", "boundaryCovered",
                "edgeResidual", "verificationExhausted", "indexExhausted", "detectionTruncated", "actualBoundary",
                "actualCollision", "chordCrossings", "edgeOverNodes", "paintedCrossings", "correctionRms",
                "correctionMax", "correctionDeltaRms", "correctionDeltaMax", "endpointSafe");
            row(nodes, "phase", "ordinal", "map", "node", "enclosure", "x", "y", "radius",
                "nearestGap", "enclosureMinimum", "enclosureMedian");
            row(anchors, "phase", "ordinal", "map", "enclosure", "x", "y");
            row(edges, "phase", "ordinal", "edge", "centreDistance", "discGap");
            long started = System.nanoTime();
            LayoutWorker worker = new LayoutWorker(LayoutCalibration.spikeDefaults());
            // On failure main exits the JVM; do not block forever closing a stalled worker.
            LayoutFrame f = await(worker.submit(LayoutRequest.of(workspace(name), p,
                ProjectionDiff.between(empty, p), Collections.<PinProjection>emptyList())), started + BUDGET);
            int steps = 0;
            while (true) {
                emit(p, f, f.idle().idle() ? "first-idle" : "pre-idle", steps, steps, started,
                    frames, nodes, anchors, edges);
                if (System.nanoTime() - started > BUDGET) throw new TimeoutException("First-idle validation deadline");
                if (f.idle().idle()) break;
                if (steps == 2000) throw new AssertionError("No first idle within 2000 worker steps");
                steps++;
                f = await(worker.step(), started + BUDGET);
            }
            long diagnosticStarted = System.nanoTime();
            for (int offset = 1; offset <= 100; offset++) {
                f = await(worker.step(), diagnosticStarted + BUDGET);
                emit(p, f, "post-idle", offset, steps + offset, diagnosticStarted, frames, nodes, anchors, edges);
                if (System.nanoTime() - diagnosticStarted > BUDGET)
                    throw new TimeoutException("Post-idle diagnostic deadline");
            }
            worker.close();
        }
    }
    public static void main(String[] args) {
        try {
            if (args.length != 2) throw new IllegalArgumentException("fixture output-directory");
            run(args[0], Paths.get(args[1]));
        }
        catch (Throwable failure) {
            failure.printStackTrace(System.err);
            System.exit(1);
        }
    }
}
