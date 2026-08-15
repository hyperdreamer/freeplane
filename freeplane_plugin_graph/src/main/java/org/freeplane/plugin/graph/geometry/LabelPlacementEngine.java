package org.freeplane.plugin.graph.geometry;

import java.awt.geom.Dimension2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.freeplane.plugin.graph.projection.BoundaryTier;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.EnclosureKey;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.ProjectedEnclosure;

public final class LabelPlacementEngine {
    private static final double MAX_INTERIOR_EXPANSION = 8.0;
    private static final double ARC_GAP = 1.0;
    private static final double EXTERNAL_GAP = 4.0;
    private static final int SUBTLE_EXTERNAL_CANDIDATE_BUDGET = 8;
    private static final double EPSILON = 1e-9;

    private final GeometryTextMetrics configuredMetrics;

    public LabelPlacementEngine() {
        this(null);
    }

    public LabelPlacementEngine(final GeometryTextMetrics metrics) {
        configuredMetrics = metrics;
    }

    public GraphGeometry place(final GraphProjection projection, final GraphGeometry geometry,
            final GeometryTextMetrics metrics) {
        Objects.requireNonNull(projection, "projection");
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(metrics, "metrics");

        final Map<EnclosureHullKey, ProjectedEnclosure> enclosuresByKey = indexEnclosures(projection);
        if (!geometry.hulls().keySet().equals(enclosuresByKey.keySet())) {
            throw new IllegalArgumentException("Projection enclosures and geometry hulls must match exactly");
        }
        final List<Rectangle> occupied = new ArrayList<Rectangle>();
        final Map<EnclosureHullKey, HullGeometry> originalHulls =
            new LinkedHashMap<EnclosureHullKey, HullGeometry>(geometry.hulls());
        final Map<EnclosureHullKey, HullGeometry> updatedHulls =
            new LinkedHashMap<EnclosureHullKey, HullGeometry>(geometry.hulls());
        final Map<EnclosureKey, LabelPlacement> labels = new LinkedHashMap<EnclosureKey, LabelPlacement>();
        final Set<EnclosureKey> processedKeys = new HashSet<EnclosureKey>();

        for (final ProjectedEnclosure enclosure : projection.enclosures()) {
            if (enclosure.boundaryTier() == BoundaryTier.SUPPRESSED) {
                continue;
            }
            final HullGeometry originalHull = originalHulls.get(enclosure.hullKey());
            final List<EnclosureKey> endpoints = enclosure.endpointKeys();
            final List<org.freeplane.plugin.graph.projection.input.SafeNodeLabel> sourceLabels = enclosure.labels();
            if (endpoints.size() != sourceLabels.size()) {
                throw new IllegalArgumentException("Enclosure endpoint and label counts must match");
            }
            for (int index = 0; index < endpoints.size(); index++) {
                final EnclosureKey endpoint = endpoints.get(index);
                if (!processedKeys.add(endpoint)) {
                    throw new IllegalArgumentException("A projected enclosure key must be unique");
                }
                final HullGeometry hull = updatedHulls.get(enclosure.hullKey());
                final String displayText = sourceLabels.get(index).displayText();
                final Dimensions dimensions = measure(metrics, displayText, enclosure.boundaryTier());
                final PlacementResult result = choosePlacement(hull, originalHull, displayText,
                    enclosure.boundaryTier(), dimensions.width, dimensions.height, occupied);
                if (result == null) {
                    throw new IllegalStateException("Unable to place label " + endpoint);
                }
                updatedHulls.put(enclosure.hullKey(), result.hull);
                labels.put(endpoint, result.placement);
                if (result.placement.mode() != LabelPlacement.Mode.HOVER_ONLY) {
                    occupied.add(Rectangle.of(result.placement));
                }
            }
        }
        return GraphGeometry.of(geometry.nodes(), updatedHulls, labels);
    }

    public GraphGeometry place(final GraphGeometry geometry, final GraphProjection projection,
            final GeometryTextMetrics metrics) {
        return place(projection, geometry, metrics);
    }

    public GraphGeometry place(final GraphProjection projection, final GraphGeometry geometry) {
        if (configuredMetrics == null) {
            throw new IllegalStateException("A text metrics implementation is required");
        }
        return place(projection, geometry, configuredMetrics);
    }

    public GraphGeometry place(final GraphGeometry geometry, final GraphProjection projection) {
        return place(projection, geometry);
    }

    private static Map<EnclosureHullKey, ProjectedEnclosure> indexEnclosures(final GraphProjection projection) {
        final Map<EnclosureHullKey, ProjectedEnclosure> result =
            new LinkedHashMap<EnclosureHullKey, ProjectedEnclosure>();
        for (final ProjectedEnclosure enclosure : projection.enclosures()) {
            if (result.put(enclosure.hullKey(), enclosure) != null) {
                throw new IllegalArgumentException("Duplicate projected enclosure hull key "
                    + enclosure.hullKey());
            }
        }
        return result;
    }

    private static Dimensions measure(final GeometryTextMetrics metrics, final String displayText,
            final BoundaryTier tier) {
        final Dimension2D dimension = metrics.measure(displayText, tier);
        if (dimension == null) {
            throw new IllegalArgumentException("Text metrics must not be null");
        }
        final double width = dimension.getWidth();
        final double height = dimension.getHeight();
        if (!Double.isFinite(width) || !Double.isFinite(height) || !(width > 0.0) || !(height > 0.0)) {
            throw new IllegalArgumentException("Text metrics must be finite and positive");
        }
        return new Dimensions(width, height);
    }

    private static PlacementResult choosePlacement(final HullGeometry hull, final HullGeometry originalHull,
            final String displayText, final BoundaryTier tier, final double width, final double height,
            final List<Rectangle> occupied) {
        final PlacementResult interior = interior(hull, originalHull, displayText, width, height, occupied);
        if (interior != null) {
            return interior;
        }
        final PlacementResult arc = arc(hull, displayText, width, height, occupied);
        if (arc != null) {
            return arc;
        }
        final PlacementResult external = external(hull, displayText, tier, width, height, occupied);
        if (external != null) {
            return external;
        }
        if (tier == BoundaryTier.SUBTLE) {
            return new PlacementResult(LabelPlacement.of(displayText, LabelPlacement.Mode.HOVER_ONLY,
                hull.labelAnchor(), width, height, Optional.<LayoutPoint>empty()), hull);
        }
        throw new IllegalStateException("No finite external lane is available for an emphatic label");
    }

    private static PlacementResult interior(final HullGeometry hull, final HullGeometry originalHull,
            final String displayText, final double width, final double height,
            final List<Rectangle> occupied) {
        final Rectangle candidate = Rectangle.centered(hull.labelAnchor(), width, height);
        if (candidate == null || collides(candidate, occupied)) {
            return null;
        }
        final List<Edge> edges = edges(originalHull);
        final double[] limits = new double[edges.size()];
        boolean expansionRequired = false;
        for (int index = 0; index < edges.size(); index++) {
            final Edge edge = edges.get(index);
            final double required = support(candidate, edge);
            final double currentSupport = support(hull, edge);
            final double requiredExtra = Math.max(0.0, required - edge.support);
            final double currentExtra = Math.max(0.0, currentSupport - edge.support);
            final double cap = edge.support + MAX_INTERIOR_EXPANSION;
            if (!finite(required) || !finite(currentSupport) || !finite(requiredExtra)
                    || !finite(currentExtra) || !finite(cap)
                    || requiredExtra > MAX_INTERIOR_EXPANSION + EPSILON
                    || currentExtra > MAX_INTERIOR_EXPANSION + EPSILON) {
                return null;
            }
            final double requested = Math.max(edge.support, Math.max(currentSupport, required));
            if (!finite(requested) || requested > cap + EPSILON) {
                return null;
            }
            limits[index] = requested > cap ? cap : requested;
            if (required > currentSupport + EPSILON) {
                expansionRequired = true;
            }
        }
        if (!expansionRequired) {
            if (!inside(candidate, hull)) {
                return null;
            }
            return new PlacementResult(LabelPlacement.of(displayText, LabelPlacement.Mode.INTERIOR,
                hull.labelAnchor(), width, height, Optional.<LayoutPoint>empty()), hull);
        }
        final HullGeometry expanded = expandedHull(originalHull, edges, limits);
        if (expanded == null || !inside(candidate, expanded)) {
            return null;
        }
        return new PlacementResult(LabelPlacement.of(displayText, LabelPlacement.Mode.INTERIOR,
            hull.labelAnchor(), width, height, Optional.<LayoutPoint>empty()), expanded);
    }

    private static double support(final Rectangle rectangle, final Edge edge) {
        final double first = edge.outwardX * rectangle.minX + edge.outwardY * rectangle.minY;
        final double second = edge.outwardX * rectangle.minX + edge.outwardY * rectangle.maxY;
        final double third = edge.outwardX * rectangle.maxX + edge.outwardY * rectangle.minY;
        final double fourth = edge.outwardX * rectangle.maxX + edge.outwardY * rectangle.maxY;
        if (!finite(first) || !finite(second) || !finite(third) || !finite(fourth)) {
            return Double.NaN;
        }
        return Math.max(Math.max(first, second), Math.max(third, fourth));
    }

    private static double support(final HullGeometry hull, final Edge edge) {
        double result = Double.NEGATIVE_INFINITY;
        for (final LayoutPoint vertex : hull.exactPolygon()) {
            final double value = edge.outwardX * vertex.x() + edge.outwardY * vertex.y();
            if (!finite(value)) {
                return Double.NaN;
            }
            result = Math.max(result, value);
        }
        return result;
    }

    private static HullGeometry expandedHull(final HullGeometry original, final List<Edge> edges,
            final double[] limits) {
        final List<LayoutPoint> vertices = new ArrayList<LayoutPoint>(edges.size());
        for (int index = 0; index < edges.size(); index++) {
            final Edge first = edges.get(index);
            final Edge second = edges.get((index + 1) % edges.size());
            final double denominator = first.outwardX * second.outwardY
                - first.outwardY * second.outwardX;
            if (!Double.isFinite(denominator) || denominator == 0.0) {
                return null;
            }
            final double x = (limits[index] * second.outwardY
                - first.outwardY * limits[(index + 1) % edges.size()]) / denominator;
            final double y = (first.outwardX * limits[(index + 1) % edges.size()]
                - limits[index] * second.outwardX) / denominator;
            if (!Double.isFinite(x) || !Double.isFinite(y)) {
                return null;
            }
            vertices.add(LayoutPoint.of(x, y));
        }
        try {
            return HullGeometry.of(vertices, original.labelAnchor());
        }
        catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static PlacementResult arc(final HullGeometry hull, final String displayText,
            final double width, final double height, final List<Rectangle> occupied) {
        final List<EdgeScore> candidates = new ArrayList<EdgeScore>();
        final List<Edge> edges = edges(hull);
        for (final Edge edge : edges) {
            candidates.add(new EdgeScore(edge, population(edge, occupied, height)));
        }
        Collections.sort(candidates, new Comparator<EdgeScore>() {
            @Override
            public int compare(final EdgeScore first, final EdgeScore second) {
                int result = Integer.compare(first.population, second.population);
                if (result != 0) {
                    return result;
                }
                result = Double.compare(second.edge.length, first.edge.length);
                if (result != 0) {
                    return result;
                }
                return Integer.compare(first.edge.index, second.edge.index);
            }
        });
        for (final EdgeScore score : candidates) {
            final LayoutPoint anchor = offset(midpoint(score.edge.start, score.edge.end),
                score.edge.inwardX, score.edge.inwardY, height * 0.5 + ARC_GAP);
            final Rectangle candidate = Rectangle.centered(anchor, width, height);
            if (candidate == null || !inside(candidate, hull) || collides(candidate, occupied)) {
                continue;
            }
            return new PlacementResult(LabelPlacement.of(displayText, LabelPlacement.Mode.ARC,
                anchor, width, height, Optional.<LayoutPoint>empty()), hull);
        }
        return null;
    }

    private static int population(final Edge edge, final List<Rectangle> occupied, final double height) {
        final double depth = height + ARC_GAP;
        int population = 0;
        final double edgeMinT = Math.min(edge.start.x() * edge.tangentX + edge.start.y() * edge.tangentY,
            edge.end.x() * edge.tangentX + edge.end.y() * edge.tangentY);
        final double edgeMaxT = Math.max(edge.start.x() * edge.tangentX + edge.start.y() * edge.tangentY,
            edge.end.x() * edge.tangentX + edge.end.y() * edge.tangentY);
        final double minNormal = edge.support - depth;
        final double maxNormal = edge.support + depth;
        for (final Rectangle rectangle : occupied) {
            final double[] tangent = rectangle.projection(edge.tangentX, edge.tangentY);
            final double[] normal = rectangle.projection(edge.outwardX, edge.outwardY);
            if (overlaps(tangent[0], tangent[1], edgeMinT, edgeMaxT)
                    && overlaps(normal[0], normal[1], minNormal, maxNormal)) {
                population++;
            }
        }
        return population;
    }

    private static PlacementResult external(final HullGeometry hull, final String displayText,
            final BoundaryTier tier, final double width, final double height,
            final List<Rectangle> occupied) {
        final List<Edge> edges = edges(hull);
        if (tier == BoundaryTier.EMPHATIC) {
            return emphaticExternal(hull, displayText, width, height, occupied, edges);
        }
        int candidates = 0;
        for (final Edge edge : edges) {
            for (int lane = 0; lane < SUBTLE_EXTERNAL_CANDIDATE_BUDGET
                    && candidates < SUBTLE_EXTERNAL_CANDIDATE_BUDGET; lane++) {
                candidates++;
                final double distance = height * 0.5 + EXTERNAL_GAP
                    + lane * (height + EXTERNAL_GAP);
                final PlacementResult placement = externalCandidate(hull, displayText, width, height, occupied,
                    offset(midpoint(edge.start, edge.end), edge.outwardX, edge.outwardY, distance));
                if (placement != null) {
                    return placement;
                }
            }
        }
        return null;
    }

    private static PlacementResult emphaticExternal(final HullGeometry hull, final String displayText,
            final double width, final double height, final List<Rectangle> occupied, final List<Edge> edges) {
        final double firstDistance = height * 0.5 + EXTERNAL_GAP;
        final double laneStep = height + EXTERNAL_GAP;
        if (!finite(firstDistance) || !finite(laneStep) || !(laneStep > 0.0)) {
            return null;
        }
        for (final Edge edge : edges) {
            final LayoutPoint midpoint = midpoint(edge.start, edge.end);
            double distance = firstDistance;
            while (finite(distance)) {
                final LayoutPoint anchor = offset(midpoint, edge.outwardX, edge.outwardY, distance);
                if (anchor == null) {
                    break;
                }
                final PlacementResult placement = externalCandidate(hull, displayText, width, height, occupied,
                    anchor);
                if (placement != null) {
                    return placement;
                }
                final double nextDistance = distance + laneStep;
                // A rounded no-progress sum cannot produce a distinct further lane.
                if (!finite(nextDistance) || nextDistance <= distance) {
                    break;
                }
                distance = nextDistance;
            }
        }
        return null;
    }

    private static PlacementResult externalCandidate(final HullGeometry hull, final String displayText,
            final double width, final double height, final List<Rectangle> occupied, final LayoutPoint anchor) {
        if (anchor == null || hull.contains(anchor)) {
            return null;
        }
        final Rectangle rectangle = Rectangle.centered(anchor, width, height);
        if (rectangle == null || collides(rectangle, occupied)) {
            return null;
        }
        final LayoutPoint leaderStart = hull.nearestBoundaryPoint(anchor);
        if (leaderStart == null || !hull.contains(leaderStart)
                || !finite(leaderStart.x()) || !finite(leaderStart.y())) {
            return null;
        }
        return new PlacementResult(LabelPlacement.of(displayText, LabelPlacement.Mode.EXTERNAL,
            anchor, width, height, Optional.of(leaderStart)), hull);
    }

    private static List<Edge> edges(final HullGeometry hull) {
        final List<LayoutPoint> polygon = hull.exactPolygon();
        final List<Edge> edges = new ArrayList<Edge>(polygon.size());
        for (int index = 0; index < polygon.size(); index++) {
            edges.add(Edge.of(index, polygon.get(index), polygon.get((index + 1) % polygon.size())));
        }
        return edges;
    }

    private static LayoutPoint midpoint(final LayoutPoint first, final LayoutPoint second) {
        final double x = first.x() * 0.5 + second.x() * 0.5;
        final double y = first.y() * 0.5 + second.y() * 0.5;
        return finite(x) && finite(y) ? LayoutPoint.of(x, y) : null;
    }

    private static LayoutPoint offset(final LayoutPoint point, final double unitX, final double unitY,
            final double distance) {
        if (point == null || !finite(unitX) || !finite(unitY) || !finite(distance)) {
            return null;
        }
        final double x = point.x() + unitX * distance;
        final double y = point.y() + unitY * distance;
        return finite(x) && finite(y) ? LayoutPoint.of(x, y) : null;
    }

    private static boolean inside(final Rectangle rectangle, final HullGeometry hull) {
        return hull.contains(LayoutPoint.of(rectangle.minX, rectangle.minY))
            && hull.contains(LayoutPoint.of(rectangle.minX, rectangle.maxY))
            && hull.contains(LayoutPoint.of(rectangle.maxX, rectangle.minY))
            && hull.contains(LayoutPoint.of(rectangle.maxX, rectangle.maxY));
    }

    private static boolean collides(final Rectangle candidate, final List<Rectangle> occupied) {
        for (final Rectangle rectangle : occupied) {
            if (candidate.intersects(rectangle)) {
                return true;
            }
        }
        return false;
    }

    private static boolean overlaps(final double firstMin, final double firstMax,
            final double secondMin, final double secondMax) {
        return firstMin <= secondMax && secondMin <= firstMax;
    }

    private static boolean finite(final double value) {
        return Double.isFinite(value);
    }

    private static final class Dimensions {
        private final double width;
        private final double height;

        private Dimensions(final double width, final double height) {
            this.width = width;
            this.height = height;
        }
    }

    private static final class PlacementResult {
        private final LabelPlacement placement;
        private final HullGeometry hull;

        private PlacementResult(final LabelPlacement placement, final HullGeometry hull) {
            this.placement = placement;
            this.hull = hull;
        }
    }

    private static final class EdgeScore {
        private final Edge edge;
        private final int population;

        private EdgeScore(final Edge edge, final int population) {
            this.edge = edge;
            this.population = population;
        }
    }

    private static final class Edge {
        private final int index;
        private final LayoutPoint start;
        private final LayoutPoint end;
        private final double outwardX;
        private final double outwardY;
        private final double inwardX;
        private final double inwardY;
        private final double tangentX;
        private final double tangentY;
        private final double length;
        private final double support;

        private Edge(final int index, final LayoutPoint start, final LayoutPoint end,
                final double outwardX, final double outwardY, final double length) {
            this.index = index;
            this.start = start;
            this.end = end;
            this.outwardX = outwardX;
            this.outwardY = outwardY;
            this.inwardX = -outwardX;
            this.inwardY = -outwardY;
            this.tangentX = (end.x() - start.x()) / length;
            this.tangentY = (end.y() - start.y()) / length;
            this.length = length;
            this.support = outwardX * start.x() + outwardY * start.y();
        }

        private static Edge of(final int index, final LayoutPoint start, final LayoutPoint end) {
            final double dx = end.x() - start.x();
            final double dy = end.y() - start.y();
            final double length = Math.hypot(dx, dy);
            if (!finite(length) || !(length > 0.0)) {
                throw new IllegalArgumentException("Hull edges must be finite and nonzero");
            }
            final double outwardX = dy / length;
            final double outwardY = -dx / length;
            if (!finite(outwardX) || !finite(outwardY)) {
                throw new IllegalArgumentException("Hull edge normals must be finite");
            }
            return new Edge(index, start, end, outwardX, outwardY, length);
        }
    }

    private static final class Rectangle {
        private final double minX;
        private final double minY;
        private final double maxX;
        private final double maxY;

        private Rectangle(final double minX, final double minY, final double maxX, final double maxY) {
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
        }

        private static Rectangle centered(final LayoutPoint anchor, final double width, final double height) {
            if (anchor == null || !finite(width) || !finite(height) || !(width > 0.0) || !(height > 0.0)) {
                return null;
            }
            final double halfWidth = width * 0.5;
            final double halfHeight = height * 0.5;
            final double minX = anchor.x() - halfWidth;
            final double minY = anchor.y() - halfHeight;
            final double maxX = anchor.x() + halfWidth;
            final double maxY = anchor.y() + halfHeight;
            if (!finite(minX) || !finite(minY) || !finite(maxX) || !finite(maxY)
                    || minX > maxX || minY > maxY) {
                return null;
            }
            return new Rectangle(minX, minY, maxX, maxY);
        }

        private static Rectangle of(final LabelPlacement placement) {
            return new Rectangle(placement.minX(), placement.minY(), placement.maxX(), placement.maxY());
        }

        private boolean intersects(final Rectangle other) {
            return minX < other.maxX && other.minX < maxX
                && minY < other.maxY && other.minY < maxY;
        }

        private double[] projection(final double unitX, final double unitY) {
            final double first = minX * unitX + minY * unitY;
            final double second = minX * unitX + maxY * unitY;
            final double third = maxX * unitX + minY * unitY;
            final double fourth = maxX * unitX + maxY * unitY;
            return new double[] { Math.min(Math.min(first, second), Math.min(third, fourth)),
                Math.max(Math.max(first, second), Math.max(third, fourth)) };
        }
    }
}
