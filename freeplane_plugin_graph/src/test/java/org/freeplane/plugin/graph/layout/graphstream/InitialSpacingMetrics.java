package org.freeplane.plugin.graph.layout.graphstream;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.freeplane.plugin.graph.geometry.*;
import org.freeplane.plugin.graph.layout.*;
import org.freeplane.plugin.graph.projection.*;

final class InitialSpacingMetrics {
    static final double TOLERANCE = 1e-6;

    static final class Metric {
        final double minimum;
        final double median;
        final List<Double> nearest;
        Metric(List<Double> values) {
            if (values.size() < 2) throw new IllegalArgumentException("Metric needs two direct nodes");
            nearest = Collections.unmodifiableList(new ArrayList<Double>(values));
            List<Double> sorted = new ArrayList<Double>(values);
            for (double value : sorted) finite(value);
            Collections.sort(sorted);
            minimum = sorted.get(0);
            int n = sorted.size();
            median = n % 2 == 1 ? sorted.get(n / 2)
                : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
            finite(median);
        }
    }

    static double radius(GraphProjection projection, ProjectedNodeKey key) {
        double result = 8.0 * (projection.prominence().containsKey(key)
            ? projection.prominence().get(key).scale() : 1.0);
        finite(result);
        assertThat(result).isGreaterThan(0.0);
        return result;
    }

    static void finite(double value) {
        assertThat(Double.isFinite(value)).as("finite value %s", value).isTrue();
    }

    static void close(double actual, double expected) {
        finite(actual);
        finite(expected);
        assertThat(actual).isCloseTo(expected, within(TOLERANCE));
    }

    static double distance(LayoutPoint a, LayoutPoint b) {
        assertThat(a).isNotNull();
        assertThat(b).isNotNull();
        return Math.hypot(a.x() - b.x(), a.y() - b.y());
    }

    static Map<EnclosureHullKey, Metric> measure(GraphProjection projection, LayoutPositions positions) {
        Set<ProjectedNodeKey> requested = new LinkedHashSet<ProjectedNodeKey>();
        for (ProjectedNode node : projection.nodes()) assertThat(requested.add(node.key())).isTrue();
        assertThat(positions.nodes().keySet()).isEqualTo(requested);
        Set<ProjectedNodeKey> memberships = new LinkedHashSet<ProjectedNodeKey>();
        Set<ProjectedNodeKey> pinned = new LinkedHashSet<ProjectedNodeKey>();
        for (PinProjection pin : projection.pins()) if (pin.active()) pinned.add(pin.projectedNode().get());
        Map<EnclosureHullKey, Metric> result = new LinkedHashMap<EnclosureHullKey, Metric>();
        for (ProjectedEnclosure enclosure : projection.enclosures()) {
            List<ProjectedNodeKey> direct = enclosure.directNodes();
            boolean hasPin = false;
            for (ProjectedNodeKey key : direct) {
                assertThat(requested).contains(key);
                assertThat(memberships.add(key)).as("unique direct membership %s", key).isTrue();
                LayoutPoint point = positions.nodes().get(key);
                assertThat(point).isNotNull();
                finite(point.x());
                finite(point.y());
                radius(projection, key);
                hasPin |= pinned.contains(key);
            }
            if (direct.size() < 2 || hasPin) continue;
            List<Double> values = new ArrayList<Double>();
            for (ProjectedNodeKey first : direct) {
                double nearest = Double.POSITIVE_INFINITY;
                for (ProjectedNodeKey second : direct) {
                    if (!first.equals(second)) nearest = Math.min(nearest,
                        distance(positions.nodes().get(first), positions.nodes().get(second))
                        - radius(projection, first) - radius(projection, second));
                }
                finite(nearest);
                values.add(nearest);
            }
            assertThat(result.put(enclosure.hullKey(), new Metric(values))).isNull();
        }
        return result;
    }

    static double analyticRadius(GraphProjection projection, ProjectedEnclosure enclosure, double gap) {
        if (enclosure.directNodes().size() <= 1) return 0.0;
        double maximum = 0.0;
        for (ProjectedNodeKey key : enclosure.directNodes()) maximum = Math.max(maximum, radius(projection, key));
        return (2.0 * maximum + gap) / (2.0 * Math.sin(Math.PI / enclosure.directNodes().size()));
    }
}
