package org.freeplane.plugin.graph.layout.graphstream;

import static org.assertj.core.api.Assertions.*;
import static org.freeplane.plugin.graph.layout.graphstream.InitialSpacingMetrics.*;
import java.lang.reflect.Method;
import java.util.*;
import org.junit.Test;
import org.freeplane.plugin.graph.geometry.*;
import org.freeplane.plugin.graph.layout.*;
import org.freeplane.plugin.graph.projection.*;

public class InitialSpacingSeedShould {
    @Test public void preferTwentyFourWhileKeepingTheSafetyAndSiblingGaps() {
        close(GraphStreamLayoutEngine.PREFERRED_NODE_GAP, 24.0);
        close(NodeSeparationProjection.MIN_GAP, 6.0);
        close(GraphStreamLayoutEngine.BoundarySizes.SIBLING_GAP, 8.0);
        GraphProjection p = InitialSpacingFixtures.single(4, 1L, false, Collections.<PinProjection>emptyList());
        double old = analyticRadius(p, p.enclosures().get(0), 6.0);
        double now = analyticRadius(p, p.enclosures().get(0), 24.0);
        close(old, 15.556349186104047); close(now, 28.284271247461902);
        GraphStreamLayoutEngine.BoundarySizes sizes = new GraphStreamLayoutEngine.BoundarySizes(p);
        close(sizes.contentRingRadius(p.enclosures().get(0).hullKey()), now);
    }

    @Test public void preserveTheAnalyticSeedConsumersAndFreshDeterminism() throws Exception {
        for (String name : Arrays.asList("reference", "S1", "cross", "mixed")) {
            GraphProjection p = InitialSpacingFixtures.named(name);
            LayoutPositions previous = null;
            for (int run = 0; run < 2; run++) {
                try (GraphStreamLayoutEngine engine = new GraphStreamLayoutEngine(LayoutCalibration.spikeDefaults())) {
                    LayoutFrame raw = engine.apply(InitialSpacingFixtures.request(p, p));
                    InitialSpacingConsumers.raw(p, raw, 24.0);
                    InitialSpacingConsumers.topology(p, engine, 24.0, 20.0);
                    if (previous != null) assertThat(raw.positions()).isEqualTo(previous);
                    previous = raw.positions();
                    for (Metric metric : measure(p, raw.positions()).values()) {
                        assertThat(metric.minimum).isGreaterThanOrEqualTo(24.0 - TOLERANCE);
                        assertThat(metric.median).isGreaterThanOrEqualTo(24.0 - TOLERANCE);
                    }
                }
            }
        }
    }

    @Test public void coverEmptySingletonTwoNodeLongLabelAndPinnedSeeds() throws Exception {
        for (int count : new int[] {0, 1, 2}) {
            GraphProjection p = InitialSpacingFixtures.single(count, 1L, false, Collections.<PinProjection>emptyList());
            check(p);
            if (count == 2) close(new GraphStreamLayoutEngine.BoundarySizes(p)
                .contentRingRadius(p.enclosures().get(0).hullKey()), 20.0);
        }
        GraphProjection base = InitialSpacingFixtures.single(4, 1L, false, Collections.<PinProjection>emptyList());
        char[] chars = new char[500]; Arrays.fill(chars, 'W');
        ProjectedEnclosure original = base.enclosures().get(0);
        ProjectedEnclosure longLabel = InitialSpacingFixtures.enclosure(InitialSpacingFixtures.A, "sub",
            new String(chars), original.parentHull(), original.directNodes(), original.directEnclosures(), false, BoundaryTier.SUBTLE);
        GraphProjection longP = InitialSpacingFixtures.projection(1L, base.nodes(),
            Arrays.asList(longLabel, base.enclosures().get(1)), base.edges(), base.pins());
        check(longP);
        close(new GraphStreamLayoutEngine.BoundarySizes(longP).contentRingRadius(longLabel.hullKey()), 28.284271247461902);
        PinProjection pin = InitialSpacingFixtures.pin(InitialSpacingFixtures.key(InitialSpacingFixtures.A, "n0"), 25.0, 35.0);
        GraphProjection pinned = InitialSpacingFixtures.single(4, 1L, false, Collections.singletonList(pin));
        check(pinned);
        try (LayoutWorker worker = new LayoutWorker(LayoutCalibration.spikeDefaults())) {
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(600);
            LayoutFrame frame = InitialSpacingFrameAssertions.awaitWithinDeadline(worker.submit(
                InitialSpacingFixtures.request(pinned, pinned)), deadline);
            InitialSpacingFrameAssertions.check(pinned, frame, pinned.pins(), false);
            for (int i = 0; i < 10; i++) {
                frame = InitialSpacingFrameAssertions.awaitWithinDeadline(worker.step(), deadline);
                InitialSpacingFrameAssertions.check(pinned, frame, pinned.pins(), false);
            }
        }
    }

    private static void check(GraphProjection p) throws Exception {
        try (GraphStreamLayoutEngine engine = new GraphStreamLayoutEngine(LayoutCalibration.spikeDefaults())) {
            LayoutFrame frame = engine.apply(InitialSpacingFixtures.request(p, p));
            InitialSpacingConsumers.raw(p, frame, 24.0);
            InitialSpacingConsumers.topology(p, engine, 24.0, 20.0);
        }
    }

    @Test public void sameMapNodeNodeRest() throws Exception { matrix(false, "nn"); }
    @Test public void sameMapNodeEnclosureRest() throws Exception { matrix(false, "ne"); }
    @Test public void sameMapEnclosureEnclosureRest() throws Exception { matrix(false, "ee"); }
    @Test public void crossMapNodeNodeRest() throws Exception { matrix(true, "nn"); }
    @Test public void crossMapNodeEnclosureRest() throws Exception { matrix(true, "ne"); }
    @Test public void crossMapEnclosureEnclosureRest() throws Exception { matrix(true, "ee"); }

    @Test public void keepTheRelationshipGapStrictlyBelowTheRingGap() throws Exception {
        try {
            java.lang.reflect.Field field = GraphStreamLayoutEngine.class.getDeclaredField("PREFERRED_EDGE_GAP");
            field.setAccessible(true);
            close(field.getDouble(null), 20.0);
            assertThat(field.getDouble(null)).isLessThan(GraphStreamLayoutEngine.PREFERRED_NODE_GAP);
        }
        catch (NoSuchFieldException absent) {
            throw new AssertionError("Missing 20 wu PREFERRED_EDGE_GAP relationship contract", absent);
        }
    }

    @Test public void boundOnlyDirectSingleEnclosureRelationshipsByTheFourNodeRing() throws Exception {
        GraphProjection defaultNodes = InitialSpacingFixtures.single(4, 1L, false, Collections.<PinProjection>emptyList());
        ringBound(defaultNodes, 8.0, 36.0, 40.0);

        // Sixteen distinct outgoing targets give the hub its real capped 14 wu radius.
        // Only the first four nodes belong to this one ring; the remaining targets are unenclosed.
        GraphProjection base = InitialSpacingFixtures.single(17, 1L, false, Collections.<PinProjection>emptyList());
        List<ProjectedNodeKey> direct = new ArrayList<ProjectedNodeKey>();
        for (int i = 0; i < 4; i++) direct.add(base.nodes().get(i).key());
        List<ProjectedEdge> edges = new ArrayList<ProjectedEdge>();
        for (int i = 1; i < 17; i++) edges.add(InitialSpacingFixtures.nativeEdge(i,
            InitialSpacingFixtures.endpoint(direct.get(0)), InitialSpacingFixtures.endpoint(base.nodes().get(i).key()), true));
        ProjectedEnclosure ring = InitialSpacingFixtures.enclosure(InitialSpacingFixtures.A, "ring", "Ring",
            Optional.<EnclosureHullKey>empty(), direct, Collections.<EnclosureHullKey>emptyList(), true, BoundaryTier.SUBTLE);
        GraphProjection mixed = InitialSpacingFixtures.projection(1L, base.nodes(), Collections.singletonList(ring),
            edges, Collections.<PinProjection>emptyList());
        close(radius(mixed, direct.get(0)), 14.0);
        close(radius(mixed, direct.get(1)), 8.0);
        ringBound(mixed, 14.0, 42.0, 52.0);
    }

    @SuppressWarnings("unchecked")
    private static void ringBound(GraphProjection p, double maxRadius, double rest, double chord) throws Exception {
        ProjectedEnclosure ring = p.enclosures().get(0);
        assertThat(ring.directNodes()).hasSize(4);
        assertThat(p.pins()).isEmpty();
        for (ProjectedNodeKey node : ring.directNodes()) {
            int memberships = 0;
            for (ProjectedEnclosure e : p.enclosures()) if (e.directNodes().contains(node)) memberships++;
            assertThat(memberships).isEqualTo(1);
        }
        GraphStreamLayoutEngine.BoundarySizes sizes = new GraphStreamLayoutEngine.BoundarySizes(p);
        close(2.0 * sizes.contentRingRadius(ring.hullKey()) * Math.sin(Math.PI / 4.0), chord);
        try (GraphStreamLayoutEngine engine = new GraphStreamLayoutEngine(LayoutCalibration.spikeDefaults())) {
            engine.apply(InitialSpacingFixtures.request(p, p));
            Map<String, GraphStreamLayoutEngine.ParticleState> particles =
                (Map<String, GraphStreamLayoutEngine.ParticleState>) InitialSpacingConsumers.field(engine, "particles");
            Object box = InitialSpacingConsumers.field(engine, "springBox");
            Map<String, GraphStreamLayoutEngine.ForceLink> links =
                (Map<String, GraphStreamLayoutEngine.ForceLink>) InitialSpacingConsumers.field(box, "typedLinks");
            int checked = 0;
            for (GraphStreamLayoutEngine.ForceLink link : links.values()) {
                if (link.kind != GraphStreamLayoutEngine.ForceKind.RELATIONSHIP) continue;
                if (!ring.directNodes().contains(particles.get(link.firstId).nodeKey)
                        || !ring.directNodes().contains(particles.get(link.secondId).nodeKey)) continue;
                close(link.restLength, rest);
                assertThat(link.restLength).isLessThanOrEqualTo(2.0 * maxRadius + 20.0);
                assertThat(2.0 * maxRadius + 20.0).isLessThan(chord);
                checked++;
            }
            assertThat(checked).isGreaterThan(0);
        }
    }

    private static void matrix(boolean cross, String pair) throws Exception {
        for (boolean reverse : new boolean[] {false, true}) {
            GraphProjection p = InitialSpacingFixtures.matrix(cross, pair, reverse);
            assertThat(p.edges()).hasSize(1);
            ProjectedEdge edge = p.edges().get(0);
            int nodeEndpoints = (edge.first().isNode() ? 1 : 0) + (edge.second().isNode() ? 1 : 0);
            assertThat(nodeEndpoints).isEqualTo(pair.equals("nn") ? 2 : pair.equals("ne") ? 1 : 0);
            check(p);
        }
    }

    @Test public void uniformLargerDiscsStillUseAnExactTwentyFourRawGap() throws Exception {
        GraphProjection base = InitialSpacingFixtures.single(4, 1L, false, Collections.<PinProjection>emptyList());
        Map<ProjectedEdgeKey, List<EdgeContributor>> contributors = new LinkedHashMap<ProjectedEdgeKey, List<EdgeContributor>>();
        for (int i = 0; i < 4; i++) for (int j = 1; j <= 2; j++) {
            ProjectedEdge edge = InitialSpacingFixtures.nativeEdge(j - 1,
                InitialSpacingFixtures.endpoint(base.nodes().get(i).key()),
                InitialSpacingFixtures.endpoint(base.nodes().get((i + j) % 4).key()), true);
            if (!contributors.containsKey(edge.key())) contributors.put(edge.key(), new ArrayList<EdgeContributor>());
            contributors.get(edge.key()).addAll(edge.contributors());
        }
        List<ProjectedEdge> edges = new ArrayList<ProjectedEdge>();
        for (Map.Entry<ProjectedEdgeKey, List<EdgeContributor>> entry : contributors.entrySet())
            edges.add(ProjectedEdge.of(entry.getKey(), entry.getValue()));
        GraphProjection p = InitialSpacingFixtures.projection(1L, base.nodes(), base.enclosures(), edges, base.pins());
        for (ProjectedNode node : p.nodes()) close(radius(p, node.key()), 9.6);
        check(p);
        try (GraphStreamLayoutEngine engine = new GraphStreamLayoutEngine(LayoutCalibration.spikeDefaults())) {
            LayoutFrame raw = engine.apply(InitialSpacingFixtures.request(p, p));
            for (Metric metric : measure(p, raw.positions()).values()) close(metric.minimum, 24.0);
        }
    }

    @Test public void packEveryParentlessEnclosureExactlyOnce() throws Exception {
        GraphProjection base = InitialSpacingFixtures.matrix(false, "nn", false);
        List<ProjectedEnclosure> roots = new ArrayList<ProjectedEnclosure>();
        for (ProjectedEnclosure e : base.enclosures()) if (!e.directNodes().isEmpty())
            roots.add(ProjectedEnclosure.of(e.hullKey(), e.endpointKeys(), e.labels(), e.mapName(),
                Optional.<EnclosureHullKey>empty(), e.directNodes(), e.directEnclosures(), false, e.boundaryTier()));
        GraphProjection p = InitialSpacingFixtures.projection(1L, base.nodes(), roots,
            Collections.<ProjectedEdge>emptyList(), Collections.<PinProjection>emptyList());
        assertThat(roots).hasSize(2);
        check(p);
        assertThat(new GraphStreamLayoutEngine.BoundarySizes(p).topLevelPlacements()).hasSize(2);
    }

    @Test public void nestedFixturesExposeAnOffsetAndStillUseIndependentConsumers() throws Exception {
        for (GraphProjection p : Arrays.asList(InitialSpacingFixtures.reference(1L), InitialSpacingFixtures.reparented(2L))) {
            check(p);
            boolean nonzero = false;
            GraphStreamLayoutEngine.BoundarySizes sizes = new GraphStreamLayoutEngine.BoundarySizes(p);
            for (ProjectedEnclosure e : p.enclosures()) {
                GraphStreamLayoutEngine.BoundarySizes.Footprint f = sizes.footprint(e.hullKey());
                nonzero |= f.anchorX != 0.0 || f.anchorY != 0.0;
            }
            assertThat(nonzero).isTrue();
        }
    }

    @Test public void useConfiguredRectanglesForBoundaryRepulsion() throws Exception {
        InitialSpacingConsumers.repulsion();
    }

    @Test public void distinguishOneUlpsContactFromRealSiblingUnderGap() throws Exception {
        Method clear = ClusterPacking.class.getDeclaredMethod("isClear", double.class, double.class,
            ClusterPacking.ClusterItem.class, List.class, double.class);
        clear.setAccessible(true);
        ClusterPacking.ClusterItem item = new ClusterPacking.ClusterItem(null, 10.0, 10.0, 0.0, 0.0, 0);
        List<ClusterPacking.ClusterPlacement> placed = Collections.singletonList(new ClusterPacking.ClusterPlacement(item, 0.0, 0.0));
        assertThat((Boolean) clear.invoke(null, Math.nextDown(18.0), 0.0, item, placed, 8.0)).isTrue();
        assertThat((Boolean) clear.invoke(null, 18.0 - 2e-9, 0.0, item, placed, 8.0)).isFalse();
        assertThat((Boolean) clear.invoke(null, 18.0 - 5e-10, 18.0 - 2e-9, item, placed, 8.0)).isTrue();
        assertThat((Boolean) clear.invoke(null, 18.0 - 2e-9, 18.0 - 2e-9, item, placed, 8.0)).isFalse();
        System.out.println("EPSILON_NECESSITY = NOT_ESTABLISHED (predicate sensitivity only; no C0 slot-failure witness)");
    }
}
