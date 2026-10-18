package org.freeplane.plugin.graph.layout.graphstream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.freeplane.plugin.graph.layout.graphstream.SpacingTestSupport.*;
import java.util.*;
import org.freeplane.plugin.graph.geometry.*;
import org.freeplane.plugin.graph.layout.*;
import org.freeplane.plugin.graph.projection.*;
import org.freeplane.plugin.graph.workspace.model.*;
import org.graphstream.graph.implementations.MultiGraph;
import org.graphstream.ui.geom.Vector3;
import org.junit.Test;

public class InitialSpacingShould {
    private static final double EPS = 1e-9;
    private static LayoutEngine engine() { return GraphStreamLayoutFactory.create(LayoutCalibration.spikeDefaults()); }
    private static double distance(LayoutPoint a, LayoutPoint b) { return Math.hypot(a.x() - b.x(), a.y() - b.y()); }
    private static WorkspaceId workspace(boolean cross) { return WorkspaceId.of(cross
        ? "00000000-0000-0000-0000-000000001606" : "00000000-0000-0000-0000-000000001605"); }
    @Test public void pinLiteralFreshRingsAndFootprints() {
        int[] counts = {0, 1, 2, 4, 8};
        double[] rings = {0, 0, 20, 28.284271247461902, 52.26251859505506};
        double[] widths = {160, 64, 104, 120.5685424949238, 168.52503719011014};
        for (int c = 0; c < counts.length; c++) {
            GraphProjection p = ring(1, counts[c], false);
            ProjectedEnclosure h = p.enclosures().get(0);
            GraphStreamLayoutEngine.BoundarySizes sizes = new GraphStreamLayoutEngine.BoundarySizes(p);
            assertThat(sizes.contentRingRadius(h.hullKey())).isCloseTo(rings[c], offset(EPS));
            assertThat(sizes.footprint(h.hullKey()).width).isCloseTo(widths[c], offset(EPS));
            assertThat(sizes.footprint(h.hullKey()).height).isCloseTo(counts[c] == 0 ? 40 : widths[c], offset(EPS));
            try (LayoutEngine e = engine()) {
                LayoutFrame f = e.apply(request(WORKSPACE, empty(0), p, p.pins()));
                assertThat(f.positions().nodes()).hasSize(counts[c]);
                LayoutPoint anchor = f.positions().anchors().get(h.hullKey());
                for (int i = 0; i < counts[c]; i++) {
                    LayoutPoint v = f.positions().nodes().get(h.directNodes().get(i));
                    double angle = 2 * Math.PI * i / counts[c];
                    assertThat(v.x()).isCloseTo(anchor.x() + rings[c] * Math.cos(angle), offset(EPS));
                    assertThat(v.y()).isCloseTo(anchor.y() + rings[c] * Math.sin(angle), offset(EPS));
                    if (counts[c] > 1) assertThat(distance(v, f.positions().nodes().get(
                        h.directNodes().get((i + 1) % counts[c]))) - 16).isCloseTo(24, offset(EPS));
                }
                requireTargets(inspect(e), rings[c], false);
            }
        }
        try (LayoutEngine e = engine()) {
            LayoutFrame f = e.apply(request(WORKSPACE, empty(0), empty(1), Collections.<PinProjection>emptyList()));
            assertThat(f.positions().nodes()).isEmpty(); assertThat(f.positions().anchors()).isEmpty();
            assertThat(inspect(e).links).isEmpty();
        }
    }
    @Test public void computeMixedProminenceAndConservativeAdjacentGaps() {
        GraphProjection p = ring(1, 3, true);
        double[] radii = {9.6, 8, 8}, gaps = {25.6, 27.2, 25.6};
        try (LayoutEngine e = engine()) {
            LayoutFrame f = e.apply(request(WORKSPACE, empty(0), p, p.pins()));
            for (int i = 0; i < 3; i++) {
                ProjectedNodeKey key = p.nodes().get(i).key();
                assertThat(p.prominence().get(key).visibleOutgoingTargets()).isEqualTo(i == 0 ? 2 : 0);
                assertThat(inspect(e).radii.get(key)).isCloseTo(radii[i], offset(EPS));
                assertThat(distance(f.positions().nodes().get(key), f.positions().anchors().get(p.enclosures().get(0).hullKey())))
                    .isCloseTo(24.941531628991836, offset(EPS));
                assertThat(distance(f.positions().nodes().get(key), f.positions().nodes().get(p.nodes().get((i + 1) % 3).key()))
                    - radii[i] - radii[(i + 1) % 3]).isCloseTo(gaps[i], offset(EPS));
            }
            requireTargets(inspect(e), 24.941531628991836, false, 23.6, 23.6);
        }
    }
    @Test public void registerAllEndpointAndMapVariantsAndConsumeTheirActualRests() {
        for (boolean cross : new boolean[] {false, true}) {
            GraphProjection p = targets(cross);
            try (LayoutEngine e = engine()) {
                e.apply(request(workspace(cross), empty(0), p, p.pins()));
                requireTargets(inspect(e), 20, cross, 22, 34, 46);
                if (!cross) {
                    assertThat(attraction(registered(e, "RELATIONSHIP", 22), 30)).isCloseTo(0.4, offset(EPS));
                    assertThat(attraction(new GraphStreamLayoutEngine.ForceLink("x", "y",
                        GraphStreamLayoutEngine.ForceKind.RELATIONSHIP, false, 40), 30)).isCloseTo(-0.5, offset(EPS));
                    assertThat(attraction(new GraphStreamLayoutEngine.ForceLink("x", "y",
                        GraphStreamLayoutEngine.ForceKind.RELATIONSHIP, false, 40), 30) - attraction(
                        registered(e, "RELATIONSHIP", 22), 30)).isCloseTo(-0.9, offset(EPS));
                    assertThat(attraction(registered(e, "CONTAINMENT", 20), 15)).isCloseTo(-0.25, offset(EPS));
                    assertThat(attraction(new GraphStreamLayoutEngine.ForceLink("x", "y",
                        GraphStreamLayoutEngine.ForceKind.CONTAINMENT, false, 11), 15)).isCloseTo(0.20, offset(EPS));
                }
                e.step(); requireTargets(inspect(e), 20, cross, 22, 34, 46);
                e.reset();
                LayoutFrame reset = e.apply(request(workspace(cross), p, p, p.pins()));
                requireTargets(inspect(e), 20, cross, 22, 34, 46);
                try (LayoutEngine fresh = engine()) {
                    assertThat(reset.positions()).isEqualTo(fresh.apply(request(workspace(cross), empty(0), p, p.pins())).positions());
                }
            }
        }
    }
    @SuppressWarnings("unchecked") private static GraphStreamLayoutEngine.ForceLink registered(LayoutEngine e, String kind, double rest) {
        TypedSpringBox box = (TypedSpringBox) read(e, "springBox");
        for (GraphStreamLayoutEngine.ForceLink link : ((Map<String, GraphStreamLayoutEngine.ForceLink>) read(box, "typedLinks")).values())
            if (link.kind.name().equals(kind) && Math.abs(link.restLength - rest) < EPS) return link;
        throw new AssertionError("missing registered " + kind + " rest " + rest);
    }
    @SuppressWarnings("unchecked") private static double attraction(GraphStreamLayoutEngine.ForceLink link, double distance) {
        MultiGraph graph = new MultiGraph("consumption");
        TypedSpringBox box = new TypedSpringBox(LayoutCalibration.spikeDefaults(), new Random(1));
        graph.addSink(box);
        graph.addNode(link.firstId); graph.addNode(link.secondId);
        box.setParticlePosition(link.firstId, 0, 0); box.setParticlePosition(link.secondId, distance, 0);
        graph.addEdge("test", link.firstId, link.secondId, false); box.registerLink("test", link);
        TypedNodeParticle particle = ((Map<String, TypedNodeParticle>) read(box, "typedParticles")).get(link.firstId);
        assertThat(particle).isNotNull();
        Vector3 displacement = new Vector3(), cross = new Vector3();
        box.addTypedAttraction(particle, displacement, cross);
        graph.removeSink(box);
        assertThat(displacement.at(1)).isCloseTo(0, offset(EPS)); assertThat(cross.at(0)).isCloseTo(0, offset(EPS));
        return displacement.at(0);
    }
    @Test public void retainAllSurvivorsAcrossRealTopologyAndProminenceChanges() {
        GraphProjection before = ring(1, 2, false);
        GraphProjection mixed = ring(2, 3, true);
        // Keep the same persisted u0/u1 identities: mixed edges must change real prominence, not the node IDs.
        List<ProjectedNode> nodes = new ArrayList<ProjectedNode>();
        for (int i = 0; i < 3; i++) nodes.add(ProjectedNode.of(node(A, "u" + i),
            org.freeplane.plugin.graph.projection.input.SafeNodeLabel.of("u" + i, "u" + i), "Map A", true));
        List<ProjectedEdge> edges = Arrays.asList(
            edge(ProjectedEndpointKey.ofNode(node(A,"u0")), ProjectedEndpointKey.ofNode(node(A,"u1")), true,16041),
            edge(ProjectedEndpointKey.ofNode(node(A,"u0")), ProjectedEndpointKey.ofNode(node(A,"u2")), true,16042));
        GraphProjection after = projection(2, nodes, ring(2,3,false).enclosures(), edges, mixed.pins());
        GraphProjection removed = projection(3, nodes.subList(0,2), before.enclosures(), edges.subList(0,1), before.pins());
        assertThat(ProjectionDiff.between(before, after).isEmpty()).isFalse();
        assertThat(ProjectionDiff.between(after, removed).isEmpty()).isFalse();
        try (LayoutEngine e = engine(); LayoutEngine repeat = engine()) {
            e.apply(request(WORKSPACE, empty(0), before, before.pins()));
            repeat.apply(request(WORKSPACE, empty(0), before, before.pins()));
            LayoutFrame retained = e.step(); repeat.step();
            LayoutFrame updated = e.apply(request(WORKSPACE, before, after, after.pins()));
            LayoutFrame repeated = repeat.apply(request(WORKSPACE, before, after, after.pins()));
            assertThat(updated.positions()).isEqualTo(repeated.positions());
            for (ProjectedNodeKey key : retained.positions().nodes().keySet())
                assertThat(updated.positions().nodes().get(key)).isEqualTo(retained.positions().nodes().get(key));
            assertThat(updated.positions().anchors()).isEqualTo(retained.positions().anchors());
            requireTargets(inspect(e), 24.941531628991836, false, 23.6, 23.6);
            assertThat(inspect(e).radii.get(node(A,"u0"))).isCloseTo(9.6,offset(EPS));
            e.apply(request(WORKSPACE, after, removed, removed.pins()));
            assertThat(inspect(e).positions.nodes()).doesNotContainKey(node(A,"u2"));
            assertThat(inspect(e).radii.get(node(A,"u0"))).isEqualTo(8.0);
            requireTargets(inspect(e), 20, false, 22);
        }
    }
    @Test public void honorConflictingPinsWithoutHidingResiduals() throws Exception {
        GraphProjection p = ring(1,2,false);
        List<PinProjection> pins = Arrays.asList(pin(node(A,"u0"),0,0),pin(node(A,"u1"),20,0));
        LayoutRequest r = request(WORKSPACE,empty(0),p,pins);
        try (LayoutEngine e = engine()) {
            checkPins(e.apply(r)); checkPins(e.step());
            e.reset(); checkPins(e.apply(r));
            checkPins(e.apply(request(WORKSPACE,p,ring(2,3,false),pins)));
        }
        try (LayoutWorker worker = new LayoutWorker(LayoutCalibration.spikeDefaults())) {
            LayoutFrame f = worker.submit(r).toCompletableFuture().get(5,java.util.concurrent.TimeUnit.SECONDS);
            checkPins(f); assertThat(f.failed()).isFalse(); assertThat(f.verified()).isTrue();
            assertThat(f.residualViolations()).isGreaterThan(0);
            assertThat(NodeSeparationProjection.violatingPairs(p,f.positions())).isNotEmpty();
            checkPins(worker.step().toCompletableFuture().get(5,java.util.concurrent.TimeUnit.SECONDS));
        }
    }
    private static void checkPins(LayoutFrame f) {
        assertThat(f.positions().nodes().get(node(A,"u0"))).isEqualTo(LayoutPoint.of(0,0));
        assertThat(f.positions().nodes().get(node(A,"u1"))).isEqualTo(LayoutPoint.of(20,0));
    }
    @Test public void preserveZeroEndpointRadiusAndPackedHierarchyRules() {
        ProjectedNodeKey a=node(A,"a"), b=node(A,"b");
        EnclosureHullKey root=hull(A,"parent"), child=hull(A,"child");
        GraphProjection p=projection(1,Arrays.asList(
            ProjectedNode.of(a,org.freeplane.plugin.graph.projection.input.SafeNodeLabel.of("a","a"),"Map A",true),
            ProjectedNode.of(b,org.freeplane.plugin.graph.projection.input.SafeNodeLabel.of("b","b"),"Map A",true)),
            Arrays.asList(enclosure(A,"parent",null,Collections.<ProjectedNodeKey>emptyList(),Collections.singletonList(child)),
                enclosure(A,"child",root,Collections.singletonList(a),Collections.<EnclosureHullKey>emptyList()),
                enclosure(A,"other",null,Collections.singletonList(b),Collections.<EnclosureHullKey>emptyList())),
            Arrays.asList(edge(ProjectedEndpointKey.ofNode(b),ProjectedEndpointKey.ofEnclosure(child.endpointKeys().get(0)),false,16041),
                edge(ProjectedEndpointKey.ofEnclosure(root.endpointKeys().get(0)),ProjectedEndpointKey.ofEnclosure(child.endpointKeys().get(0)),false,16042)),
            Collections.<PinProjection>emptyList());
        try(LayoutEngine e=engine()) {
            e.apply(request(WORKSPACE,empty(0),p,p.pins())); requireTargets(inspect(e),0,false,6,14);
            List<Link> hierarchy=new ArrayList<Link>();
            for(Link l:inspect(e).links) if(l.kind.equals("HIERARCHY")) hierarchy.add(l);
            assertThat(hierarchy).hasSize(1); assertThat(hierarchy.get(0).restLength).isEqualTo(0);
        }
    }
    @Test public void expandRecursiveContentWithoutChangingTheEightUnitGutter() {
        GraphProjection base=targets(true);
        EnclosureHullKey root=hull(A,"outer"),left=hull(A,"left");
        GraphProjection p=projection(1,base.nodes(),Arrays.asList(
            enclosure(A,"outer",null,Arrays.asList(node(A,"p0"),node(A,"p1")),Collections.singletonList(left)),
            enclosure(A,"left",root,Collections.<ProjectedNodeKey>emptyList(),Collections.<EnclosureHullKey>emptyList()),
            base.enclosures().get(1)),base.edges(),base.pins());
        GraphStreamLayoutEngine.BoundarySizes sizes=new GraphStreamLayoutEngine.BoundarySizes(p);
        assertThat(sizes.contentRingRadius(root)).isEqualTo(20);
        assertThat(sizes.footprint(hull(B,"right")).width).isEqualTo(104);
        checkPacking(sizes.placements(root),sizes.footprint(root));
        checkPacking(sizes.topLevelPlacements(),null);
        try(LayoutEngine e=engine()) {
            LayoutFrame f=e.apply(request(WORKSPACE,empty(0),p,p.pins()));
            assertThat(f.positions().anchors()).hasSize(3);
            for(Link link:inspect(e).links) if(link.kind.equals("HIERARCHY"))
                assertThat(link.restLength).isCloseTo(distance(f.positions().anchors().get((EnclosureHullKey)link.first),
                    f.positions().anchors().get((EnclosureHullKey)link.second)),offset(EPS));
        }
    }
    private static void checkPacking(List<ClusterPacking.ClusterPlacement> placed,GraphStreamLayoutEngine.BoundarySizes.Footprint footprint) {
        assertThat(placed).hasSize(2);
        double minX=Double.POSITIVE_INFINITY,maxX=Double.NEGATIVE_INFINITY,minY=minX,maxY=maxX;
        for(ClusterPacking.ClusterPlacement p:placed) {
            minX=Math.min(minX,p.x-p.item.width/2);maxX=Math.max(maxX,p.x+p.item.width/2);
            minY=Math.min(minY,p.y-p.item.height/2);maxY=Math.max(maxY,p.y+p.item.height/2);
        }
        ClusterPacking.ClusterPlacement a=placed.get(0),b=placed.get(1);
        double gapX=Math.abs(a.x-b.x)-(a.item.width+b.item.width)/2;
        double gapY=Math.abs(a.y-b.y)-(a.item.height+b.item.height)/2;
        assertThat(Math.max(gapX,gapY)).isGreaterThanOrEqualTo(8-EPS);
        if(footprint!=null) {
            assertThat(footprint.width).isCloseTo(maxX-minX+32,offset(EPS));
            assertThat(footprint.height).isCloseTo(maxY-minY+32,offset(EPS));
            assertThat(footprint.anchorX).isCloseTo(-(minX+maxX)/2,offset(EPS));
            assertThat(footprint.anchorY).isCloseTo(-(minY+maxY)/2,offset(EPS));
            boolean content=false;
            for(ClusterPacking.ClusterPlacement p:placed) if(p.item.width==72&&p.item.height==72) content=true;
            assertThat(content).as("literal radius-20 direct-content square").isTrue();
        }
    }
}
