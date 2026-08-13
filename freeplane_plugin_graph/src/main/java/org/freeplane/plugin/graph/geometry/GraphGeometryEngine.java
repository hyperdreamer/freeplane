package org.freeplane.plugin.graph.geometry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.ProjectedEnclosure;
import org.freeplane.plugin.graph.projection.ProjectedNode;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;

public final class GraphGeometryEngine {
    private static final double BASE_RADIUS = 8.0;
    private static final double HULL_CLEARANCE = 16.0;
    private static final double DIAGONAL = Math.sqrt(0.5);
    private static final double[][] NORMALS = {
        {1.0, 0.0},
        {DIAGONAL, DIAGONAL},
        {0.0, 1.0},
        {-DIAGONAL, DIAGONAL},
        {-1.0, 0.0},
        {-DIAGONAL, -DIAGONAL},
        {0.0, -1.0},
        {DIAGONAL, -DIAGONAL},
    };

    public GraphGeometry computeHulls(final GraphProjection projection, final LayoutPositions positions) {
        Objects.requireNonNull(projection, "projection");
        Objects.requireNonNull(positions, "positions");
        final Map<ProjectedNodeKey, ProjectedNode> nodesByKey =
            new LinkedHashMap<ProjectedNodeKey, ProjectedNode>();
        for (final ProjectedNode node : projection.nodes()) {
            if (nodesByKey.put(node.key(), node) != null) {
                throw new IllegalArgumentException("Duplicate projected node key " + node.key());
            }
        }
        final Map<EnclosureHullKey, ProjectedEnclosure> enclosuresByKey =
            new LinkedHashMap<EnclosureHullKey, ProjectedEnclosure>();
        for (final ProjectedEnclosure enclosure : projection.enclosures()) {
            if (enclosuresByKey.put(enclosure.hullKey(), enclosure) != null) {
                throw new IllegalArgumentException("Duplicate projected enclosure hull key "
                    + enclosure.hullKey());
            }
        }
        if (!positions.nodes().keySet().equals(nodesByKey.keySet())) {
            throw new IllegalArgumentException("Layout positions must cover exactly the projected nodes");
        }
        if (!positions.anchors().keySet().equals(enclosuresByKey.keySet())) {
            throw new IllegalArgumentException("Layout anchors must cover exactly the projected enclosures");
        }
        if (!projection.prominence().keySet().equals(nodesByKey.keySet())) {
            throw new IllegalArgumentException("Prominence must cover exactly the projected nodes");
        }
        final Map<ProjectedNodeKey, NodeGeometry> nodeGeometry =
            new LinkedHashMap<ProjectedNodeKey, NodeGeometry>();
        for (final ProjectedNode node : projection.nodes()) {
            final LayoutPoint center = positions.nodes().get(node.key());
            final double radius = BASE_RADIUS * projection.prominence().get(node.key()).scale();
            nodeGeometry.put(node.key(), NodeGeometry.of(center, radius));
        }
        final Map<EnclosureHullKey, HullGeometry> computed =
            new LinkedHashMap<EnclosureHullKey, HullGeometry>();
        final Set<EnclosureHullKey> complete = new HashSet<EnclosureHullKey>();
        final Set<EnclosureHullKey> visiting = new HashSet<EnclosureHullKey>();
        for (final ProjectedEnclosure enclosure : projection.enclosures()) {
            computeHull(enclosure.hullKey(), enclosuresByKey, positions, nodeGeometry, computed, complete,
                visiting);
        }
        final Map<EnclosureHullKey, HullGeometry> hulls = new LinkedHashMap<EnclosureHullKey, HullGeometry>();
        for (final ProjectedEnclosure enclosure : projection.enclosures()) {
            hulls.put(enclosure.hullKey(), computed.get(enclosure.hullKey()));
        }
        return GraphGeometry.of(nodeGeometry, hulls);
    }

    private static void computeHull(final EnclosureHullKey hullKey,
            final Map<EnclosureHullKey, ProjectedEnclosure> enclosuresByKey, final LayoutPositions positions,
            final Map<ProjectedNodeKey, NodeGeometry> nodeGeometry,
            final Map<EnclosureHullKey, HullGeometry> computed, final Set<EnclosureHullKey> complete,
            final Set<EnclosureHullKey> visiting) {
        if (complete.contains(hullKey)) {
            return;
        }
        if (!visiting.add(hullKey)) {
            throw new IllegalStateException("Enclosure cycle detected at " + hullKey);
        }
        final ProjectedEnclosure enclosure = enclosuresByKey.get(hullKey);
        for (final EnclosureHullKey childKey : enclosure.directEnclosures()) {
            if (!enclosuresByKey.containsKey(childKey)) {
                throw new IllegalArgumentException("Enclosure references missing child hull " + childKey);
            }
            computeHull(childKey, enclosuresByKey, positions, nodeGeometry, computed, complete, visiting);
        }
        for (final ProjectedNodeKey nodeKey : enclosure.directNodes()) {
            if (!nodeGeometry.containsKey(nodeKey)) {
                throw new IllegalArgumentException("Enclosure references missing node " + nodeKey);
            }
        }
        final double[] supports = new double[8];
        final boolean empty = enclosure.directNodes().isEmpty() && enclosure.directEnclosures().isEmpty();
        for (int index = 0; index < 8; index++) {
            final double nx = NORMALS[index][0];
            final double ny = NORMALS[index][1];
            double maxSupport = Double.NEGATIVE_INFINITY;
            if (empty) {
                final LayoutPoint anchor = positions.anchors().get(hullKey);
                maxSupport = nx * anchor.x() + ny * anchor.y();
            }
            else {
                for (final ProjectedNodeKey nodeKey : enclosure.directNodes()) {
                    final NodeGeometry geometry = nodeGeometry.get(nodeKey);
                    maxSupport = Math.max(maxSupport,
                        nx * geometry.center().x() + ny * geometry.center().y() + geometry.radius());
                }
                for (final EnclosureHullKey childKey : enclosure.directEnclosures()) {
                    for (final LayoutPoint vertex : computed.get(childKey).exactPolygon()) {
                        maxSupport = Math.max(maxSupport, nx * vertex.x() + ny * vertex.y());
                    }
                }
            }
            supports[index] = maxSupport + HULL_CLEARANCE;
        }
        final List<LayoutPoint> polygon = clipHalfPlanes(supports);
        final LayoutPoint labelAnchor;
        if (empty) {
            labelAnchor = positions.anchors().get(hullKey);
        }
        else {
            labelAnchor = centroid(polygon);
        }
        computed.put(hullKey, HullGeometry.of(polygon, labelAnchor));
        visiting.remove(hullKey);
        complete.add(hullKey);
    }

    private static List<LayoutPoint> clipHalfPlanes(final double[] supports) {
        final double minX = -supports[4];
        final double maxX = supports[0];
        final double minY = -supports[6];
        final double maxY = supports[2];
        List<LayoutPoint> polygon = new ArrayList<LayoutPoint>();
        polygon.add(LayoutPoint.of(minX, minY));
        polygon.add(LayoutPoint.of(maxX, minY));
        polygon.add(LayoutPoint.of(maxX, maxY));
        polygon.add(LayoutPoint.of(minX, maxY));
        for (int index = 0; index < 8; index++) {
            polygon = clipHalfPlane(polygon, NORMALS[index][0], NORMALS[index][1], supports[index]);
        }
        return polygon;
    }

    private static List<LayoutPoint> clipHalfPlane(final List<LayoutPoint> polygon, final double nx,
            final double ny, final double limit) {
        final List<LayoutPoint> clipped = new ArrayList<LayoutPoint>();
        for (int index = 0; index < polygon.size(); index++) {
            final LayoutPoint previous = polygon.get((index + polygon.size() - 1) % polygon.size());
            final LayoutPoint current = polygon.get(index);
            final boolean previousInside = nx * previous.x() + ny * previous.y() <= limit;
            final boolean currentInside = nx * current.x() + ny * current.y() <= limit;
            if (previousInside) {
                if (currentInside) {
                    clipped.add(current);
                }
                else {
                    clipped.add(intersection(previous, current, nx, ny, limit));
                }
            }
            else if (currentInside) {
                clipped.add(intersection(previous, current, nx, ny, limit));
                clipped.add(current);
            }
        }
        return clipped;
    }

    private static LayoutPoint intersection(final LayoutPoint previous, final LayoutPoint current, final double nx,
            final double ny, final double limit) {
        final double dx = current.x() - previous.x();
        final double dy = current.y() - previous.y();
        final double denominator = nx * dx + ny * dy;
        final double t = (limit - nx * previous.x() - ny * previous.y()) / denominator;
        final double clamped = Math.max(0.0, Math.min(1.0, t));
        return LayoutPoint.of(previous.x() + clamped * dx, previous.y() + clamped * dy);
    }

    private static LayoutPoint centroid(final List<LayoutPoint> polygon) {
        double twiceArea = 0.0;
        double sumX = 0.0;
        double sumY = 0.0;
        for (int index = 0; index < polygon.size(); index++) {
            final LayoutPoint first = polygon.get(index);
            final LayoutPoint second = polygon.get((index + 1) % polygon.size());
            final double cross = first.x() * second.y() - second.x() * first.y();
            twiceArea += cross;
            sumX += (first.x() + second.x()) * cross;
            sumY += (first.y() + second.y()) * cross;
        }
        return LayoutPoint.of(sumX / (3.0 * twiceArea), sumY / (3.0 * twiceArea));
    }
}
