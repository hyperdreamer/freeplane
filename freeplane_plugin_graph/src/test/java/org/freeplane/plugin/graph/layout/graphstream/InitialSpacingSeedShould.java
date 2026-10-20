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
                    InitialSpacingConsumers.topology(p, engine, 24.0);
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
            InitialSpacingConsumers.topology(p, engine, 24.0);
        }
    }

    @Test public void sameMapNodeNodeRest() throws Exception { matrix(false, "nn"); }
    @Test public void sameMapNodeEnclosureRest() throws Exception { matrix(false, "ne"); }
    @Test public void sameMapEnclosureEnclosureRest() throws Exception { matrix(false, "ee"); }
    @Test public void crossMapNodeNodeRest() throws Exception { matrix(true, "nn"); }
    @Test public void crossMapNodeEnclosureRest() throws Exception { matrix(true, "ne"); }
    @Test public void crossMapEnclosureEnclosureRest() throws Exception { matrix(true, "ee"); }

    private static void matrix(boolean cross, String pair) throws Exception {
        for (boolean reverse : new boolean[] {false, true}) check(InitialSpacingFixtures.matrix(cross, pair, reverse));
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
