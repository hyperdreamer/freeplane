package org.freeplane.plugin.graph.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.geometry.LayoutPositions;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.NodeProminence;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;

public final class NodeSeparationProjection {
    public static final double MIN_GAP = 6.0;
    static final int MAX_PASSES = 64;
    static final double RELAXATION = 0.5;
    static final double SPATIAL_CELL = 34.0;
    private static final double NODE_RADIUS = 8.0;
    private static final double DEFAULT_SCALE = 1.0;
    private static final double COINCIDENT_AXIS_X = 1.0;
    private static final double COINCIDENT_AXIS_Y = 0.0;
    private static final double COINCIDENT_DISTANCE = 1.0;

    /** Pure. Never mutates the arguments. */
    public NodeSeparationResult project(final GraphProjection projection, final LayoutPositions positions,
            final Set<ProjectedNodeKey> pinned) {
        Objects.requireNonNull(projection, "projection");
        Objects.requireNonNull(positions, "positions");
        Objects.requireNonNull(pinned, "pinned");
        validateFinite(positions.nodes(), "node");
        validateFinite(positions.anchors(), "anchor");

        final List<ProjectedNodeKey> keys = new ArrayList<ProjectedNodeKey>(positions.nodes().keySet());
        final int count = keys.size();
        final double[] x = new double[count];
        final double[] y = new double[count];
        final double[] radius = new double[count];
        final boolean[] isPinned = new boolean[count];
        for (int index = 0; index < count; index++) {
            final ProjectedNodeKey key = keys.get(index);
            final LayoutPoint point = positions.nodes().get(key);
            x[index] = point.x();
            y[index] = point.y();
            radius[index] = NODE_RADIUS * prominenceScale(projection, key);
            isPinned[index] = pinned.contains(key);
        }

        int passes = 0;
        for (int pass = 1; pass <= MAX_PASSES; pass++) {
            passes = pass;
            final Map<Long, List<Integer>> cells = new HashMap<Long, List<Integer>>();
            for (int index = 0; index < count; index++) {
                insert(cells, index, x[index], y[index]);
            }
            boolean moved = false;
            for (int first = 0; first < count; first++) {
                final List<Integer> candidates = candidates(cells, x[first], y[first]);
                Collections.sort(candidates);
                for (final Integer candidate : candidates) {
                    final int second = candidate.intValue();
                    if (second <= first) {
                        continue;
                    }
                    final double need = radius[first] + radius[second] + MIN_GAP;
                    double dx = x[second] - x[first];
                    double dy = y[second] - y[first];
                    double distance = Math.hypot(dx, dy);
                    if (distance == 0.0) {
                        dx = COINCIDENT_AXIS_X;
                        dy = COINCIDENT_AXIS_Y;
                        distance = COINCIDENT_DISTANCE;
                    }
                    if (distance >= need || isPinned[first] && isPinned[second]) {
                        continue;
                    }
                    final double penetration = need - distance;
                    final double unitX = dx / distance;
                    final double unitY = dy / distance;
                    if (isPinned[first]) {
                        move(cells, second, x, y, penetration * unitX, penetration * unitY);
                    }
                    else if (isPinned[second]) {
                        move(cells, first, x, y, -penetration * unitX, -penetration * unitY);
                    }
                    else {
                        final double half = RELAXATION * penetration;
                        move(cells, first, x, y, -half * unitX, -half * unitY);
                        move(cells, second, x, y, half * unitX, half * unitY);
                    }
                    moved = true;
                }
            }
            if (!moved) {
                break;
            }
        }

        final Map<ProjectedNodeKey, LayoutPoint> projected =
            new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        for (int index = 0; index < count; index++) {
            if (!Double.isFinite(x[index]) || !Double.isFinite(y[index])) {
                throw new IllegalArgumentException("Projected coordinates must be finite");
            }
            projected.put(keys.get(index), LayoutPoint.of(x[index], y[index]));
        }
        final LayoutPositions result = LayoutPositions.of(projected, positions.anchors());
        return NodeSeparationResult.of(result, residualViolations(x, y, radius, count), passes);
    }

    private static void move(final Map<Long, List<Integer>> cells, final int index, final double[] x,
            final double[] y, final double deltaX, final double deltaY) {
        remove(cells, index, x[index], y[index]);
        x[index] += deltaX;
        y[index] += deltaY;
        insert(cells, index, x[index], y[index]);
    }

    private static void insert(final Map<Long, List<Integer>> cells, final int index, final double x,
            final double y) {
        final Long key = Long.valueOf(cellKey(cell(x), cell(y)));
        List<Integer> bucket = cells.get(key);
        if (bucket == null) {
            bucket = new ArrayList<Integer>();
            cells.put(key, bucket);
        }
        bucket.add(Integer.valueOf(index));
    }

    private static void remove(final Map<Long, List<Integer>> cells, final int index, final double x,
            final double y) {
        final List<Integer> bucket = cells.get(Long.valueOf(cellKey(cell(x), cell(y))));
        if (bucket != null) {
            bucket.remove(Integer.valueOf(index));
        }
    }

    private static List<Integer> candidates(final Map<Long, List<Integer>> cells, final double x,
            final double y) {
        final int cellX = cell(x);
        final int cellY = cell(y);
        final List<Integer> result = new ArrayList<Integer>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                final List<Integer> bucket = cells.get(Long.valueOf(cellKey(cellX + dx, cellY + dy)));
                if (bucket != null) {
                    result.addAll(bucket);
                }
            }
        }
        return result;
    }

    private static long cellKey(final int cellX, final int cellY) {
        return (long) cellX << 32 ^ (long) cellY & 0xffffffffL;
    }

    private static int cell(final double value) {
        return (int) Math.floor(value / SPATIAL_CELL);
    }

    private static int residualViolations(final double[] x, final double[] y, final double[] radius,
            final int count) {
        int violations = 0;
        for (int first = 0; first < count; first++) {
            for (int second = first + 1; second < count; second++) {
                final double need = radius[first] + radius[second] + MIN_GAP;
                if (Math.hypot(x[second] - x[first], y[second] - y[first]) < need) {
                    violations++;
                }
            }
        }
        return violations;
    }

    private static double prominenceScale(final GraphProjection projection, final ProjectedNodeKey key) {
        final NodeProminence prominence = projection.prominence().get(key);
        return prominence == null ? DEFAULT_SCALE : prominence.scale();
    }

    private static <K> void validateFinite(final Map<K, LayoutPoint> values, final String kind) {
        for (final LayoutPoint point : values.values()) {
            if (!Double.isFinite(point.x()) || !Double.isFinite(point.y())) {
                throw new IllegalArgumentException("Layout positions must be finite");
            }
        }
    }
}
