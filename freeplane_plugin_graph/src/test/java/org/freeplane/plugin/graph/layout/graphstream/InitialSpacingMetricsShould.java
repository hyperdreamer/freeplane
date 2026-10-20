package org.freeplane.plugin.graph.layout.graphstream;

import static org.assertj.core.api.Assertions.*;
import static org.freeplane.plugin.graph.layout.graphstream.InitialSpacingMetrics.*;
import java.util.*;
import org.junit.Test;
import org.freeplane.plugin.graph.geometry.*;
import org.freeplane.plugin.graph.layout.*;
import org.freeplane.plugin.graph.projection.*;

public class InitialSpacingMetricsShould {
    @Test public void countOneNearestGapPerDirectNodeAndUseAnEvenMedian() {
        GraphProjection p = InitialSpacingFixtures.single(4, 1L, false, Collections.<PinProjection>emptyList());
        Map<ProjectedNodeKey, LayoutPoint> nodes = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        double[] x = {0.0, 30.0, 70.0, 120.0};
        for (int i = 0; i < 4; i++) nodes.put(p.nodes().get(i).key(), LayoutPoint.of(x[i], 0.0));
        Map<EnclosureHullKey, Metric> result = measure(p, LayoutPositions.of(nodes, Collections.<EnclosureHullKey, LayoutPoint>emptyMap()));
        assertThat(result).hasSize(1);
        Metric m = result.values().iterator().next();
        assertThat(m.nearest).containsExactly(14.0, 14.0, 24.0, 34.0);
        close(m.minimum, 14.0); close(m.median, 19.0);
        close(new Metric(Arrays.asList(9.0, 1.0, 5.0)).median, 5.0);
        Map<ProjectedNodeKey, LayoutPoint> shifted = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        for (Map.Entry<ProjectedNodeKey, LayoutPoint> entry : nodes.entrySet())
            shifted.put(entry.getKey(), LayoutPoint.of(entry.getValue().x() + 10000.0, entry.getValue().y() - 100000.0));
        assertThat(measure(p, LayoutPositions.of(shifted, Collections.<EnclosureHullKey, LayoutPoint>emptyMap()))
            .values().iterator().next().nearest).isEqualTo(m.nearest);
    }

    @Test public void rejectNonfiniteMetricMissingNodesAndDuplicateMembership() {
        assertThatThrownBy(() -> new Metric(Arrays.asList(1.0, Double.NaN))).isInstanceOf(AssertionError.class);
        GraphProjection p = InitialSpacingFixtures.single(4, 1L, false, Collections.<PinProjection>emptyList());
        assertThatThrownBy(() -> measure(p, LayoutPositions.of(Collections.<ProjectedNodeKey, LayoutPoint>emptyMap(), Collections.<EnclosureHullKey, LayoutPoint>emptyMap()))).isInstanceOf(AssertionError.class);
        GraphProjection duplicate = InitialSpacingFixtures.projection(1L, p.nodes(), Arrays.asList(
            p.enclosures().get(0), InitialSpacingFixtures.enclosure(InitialSpacingFixtures.A, "other", "Other",
                Optional.<EnclosureHullKey>empty(), p.enclosures().get(0).directNodes(),
                Collections.<EnclosureHullKey>emptyList(), true, BoundaryTier.SUBTLE)),
            p.edges(), p.pins());
        Map<ProjectedNodeKey, LayoutPoint> points = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        for (ProjectedNode n : p.nodes()) points.put(n.key(), LayoutPoint.of(0.0, 0.0));
        assertThatThrownBy(() -> measure(duplicate, LayoutPositions.of(points,
            Collections.<EnclosureHullKey, LayoutPoint>emptyMap()))).isInstanceOf(AssertionError.class);
    }

    @Test public void excludePinnedAndSingletonScopesAndFreezeFixtureIdentity() throws Exception {
        PinProjection pin = InitialSpacingFixtures.pin(InitialSpacingFixtures.key(InitialSpacingFixtures.A, "n0"), 25.0, 35.0);
        GraphProjection p = InitialSpacingFixtures.single(4, 1L, false, Collections.singletonList(pin));
        Map<ProjectedNodeKey, LayoutPoint> points = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        for (ProjectedNode n : p.nodes()) points.put(n.key(), LayoutPoint.of(25.0, 35.0));
        assertThat(measure(p, LayoutPositions.of(points, Collections.<EnclosureHullKey, LayoutPoint>emptyMap()))).isEmpty();
        GraphProjection s1 = InitialSpacingFixtures.named("S1");
        assertThat(s1.nodes()).hasSize(4); assertThat(s1.enclosures()).hasSize(2); assertThat(s1.edges()).hasSize(2);
        for (ProjectedEdge e : s1.edges()) assertThat(e.contributors().get(0).connectorDescriptor()).isPresent();
        assertThat(InitialSpacingFixtures.named("cross").nodes()).hasSize(24);
        assertThat(InitialSpacingFixtures.named("mixed").prominence().values())
            .extracting(value -> value.scale()).contains(1.0).anyMatch(value -> value > 1.0);
        assertThat(InitialSpacingFixtures.single(0, 1L, false, Collections.<PinProjection>emptyList()).nodes()).isEmpty();
        for (int n : new int[] {0, 1}) {
            GraphProjection small = InitialSpacingFixtures.single(n, 1L, false, Collections.<PinProjection>emptyList());
            Map<ProjectedNodeKey, LayoutPoint> positions = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
            for (ProjectedNode node : small.nodes()) positions.put(node.key(), LayoutPoint.of(0.0, 0.0));
            assertThat(measure(small, LayoutPositions.of(positions, Collections.<EnclosureHullKey, LayoutPoint>emptyMap()))).isEmpty();
        }
    }
}
