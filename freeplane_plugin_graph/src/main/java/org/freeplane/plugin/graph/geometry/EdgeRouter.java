package org.freeplane.plugin.graph.geometry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.freeplane.plugin.graph.layout.BoundaryInvariants;
import org.freeplane.plugin.graph.projection.CanonicalKeys;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.EnclosureKey;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.ProjectedEdge;
import org.freeplane.plugin.graph.projection.ProjectedEdgeKey;
import org.freeplane.plugin.graph.projection.ProjectedEnclosure;
import org.freeplane.plugin.graph.projection.ProjectedEndpointKey;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;

/**
 * Publish-time bounded edge routing (spec 6). Edges are processed in ascending
 * {@code CanonicalKeys.edge} order; for each edge the straight attachment chord is accepted
 * whenever it clears the pinned clearance rules of spec 6.4, otherwise the live bow and
 * same-side dogleg rungs of spec 6.3 are tried in the pinned order. An edge whose rungs all
 * fail falls back to its straight chord and is reported through {@code edgesFallback}. The
 * obstacle set is every other visible edge (published path when already processed, straight
 * chord when not) plus every projected node disc the edge is not attached to; hulls are not
 * obstacles.
 */
public final class EdgeRouter {
    /** World units between a routed path and any projected node disc it is not attached to. */
    public static final double EDGE_NODE_CLEARANCE = 3.0;
    /** World units between two routed paths, outside the spec 6.4 fan region. */
    public static final double EDGE_CLEARANCE = 3.0;
    /** Absolute bound on a single bow/dogleg excursion (spec 6.3). */
    public static final double MAX_BEND = 96.0;
    /** Maximum sampled path length over the anchor chord (spec 1.4). */
    public static final double ROUTE_LENGTH_CAP = 1.25;
    /** Effective radius of an enclosure endpoint inside the fan region (spec 6.4). */
    public static final double FAN_HULL_RADIUS = 16.0;
    /** The only measurement epsilon; it can never mask a real clearance violation. */
    public static final double ROUTE_EPSILON = 1e-9;
    /** Fallback radius for a node endpoint whose drawn geometry is unavailable. */
    public static final double DRAWN_BASE_RADIUS = 8.0;

    /**
     * Live bow offsets as fractions of the anchor chord (spec 1.4): their analytic ratios
     * are 1.014804, 1.057116 and 1.147794, all at or below {@link #ROUTE_LENGTH_CAP}.
     */
    private static final double[] BOW_FRACTIONS = {0.15, -0.15, 0.30, -0.30, 0.50, -0.50};

    /**
     * Dogleg offsets; the 0.50 rung (analytic ratio 1.535184) is skipped by the
     * {@link #ROUTE_LENGTH_CAP} liveness test, exactly as spec 1.4 requires.
     */
    private static final double[] DOGLEG_FRACTIONS = {0.15, -0.15, 0.30, -0.30, 0.50, -0.50};

    /** Below this squared length a sample segment is treated as a point. */
    private static final double SEGMENT_EPSILON = 1e-30;
    /**
     * Coordinate bound below which a direct orientation product cannot overflow: differences
     * stay at most {@code 2 * limit}, products at most {@code 4 * limit^2 < Double.MAX_VALUE}.
     */
    private static final double FAST_ORIENTATION_LIMIT = 1e150;
    /** Cells per axis of the per-frame router query grid (design 9). */
    private static final int GRID_DIMENSION = 128;
    /** Largest cell index after clamping; keeps the traversal and its step bound finite. */
    private static final int MAX_CELL_INDEX = 4 * GRID_DIMENSION;
    /** Above this box size a disc is checked directly instead of being inserted into cells. */
    private static final int MAX_GRID_CELLS_PER_PRIMITIVE = 4096;

    public EdgeRouter() {
    }

    public EdgePaths route(final GraphProjection projection, final LayoutPositions positions,
            final GraphGeometry geometry, final Set<ProjectedEndpointKey> visibleEndpoints) {
        Objects.requireNonNull(projection, "projection");
        Objects.requireNonNull(positions, "positions");
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(visibleEndpoints, "visibleEndpoints");
        final long indexStart = System.nanoTime();
        final Map<EnclosureKey, EnclosureHullKey> hullKeys = enclosureHullKeys(projection, geometry);
        final Map<ProjectedNodeKey, List<EnclosureHullKey>> nodeHulls =
            BoundaryInvariants.nodeHullsByKey(projection);
        final Map<EnclosureHullKey, EnclosureHullKey> parents = BoundaryInvariants.parents(projection);
        final List<Resolved> resolved = new ArrayList<Resolved>();
        for (final ProjectedEdge edge : orderedEdges(projection)) {
            final Resolved value = resolve(edge, hullKeys, positions, geometry, visibleEndpoints);
            if (value != null) {
                resolved.add(value);
            }
        }
        final QueryGrid grid = new QueryGrid(resolved, geometry);
        final Set<String> detectedCrossings = grid.crossingPairs(false);
        final long indexEnd = System.nanoTime();

        final long selectionStart = System.nanoTime();
        final Map<ProjectedEdgeKey, EdgePath> byEdge = new LinkedHashMap<ProjectedEdgeKey, EdgePath>();
        int edgesStraight = 0;
        int edgesBow = 0;
        int edgesDogleg = 0;
        int edgesFallback = 0;
        for (int index = 0; index < resolved.size(); index++) {
            final Resolved current = resolved.get(index);
            if (clears(current.straight, current.edge, grid, geometry)) {
                grid.publish(current.edge.key(), current.straight);
                byEdge.put(current.edge.key(), current.straight);
                edgesStraight++;
                continue;
            }
            final EdgePath bow = bestRung(current, grid, geometry, BOW_FRACTIONS, true);
            if (bow != null) {
                grid.publish(current.edge.key(), bow);
                byEdge.put(current.edge.key(), bow);
                edgesBow++;
                continue;
            }
            final EdgePath dogleg = bestRung(current, grid, geometry, DOGLEG_FRACTIONS, false);
            if (dogleg != null) {
                grid.publish(current.edge.key(), dogleg);
                byEdge.put(current.edge.key(), dogleg);
                edgesDogleg++;
                continue;
            }
            grid.publish(current.edge.key(), current.straight);
            byEdge.put(current.edge.key(), current.straight);
            edgesFallback++;
        }
        final long selectionEnd = System.nanoTime();

        final long residualStart = System.nanoTime();
        final Set<String> residualCrossings = grid.crossingPairs(true);
        final int hullPenetrations = grid.hullPenetrations(nodeHulls, parents);
        final long residualEnd = System.nanoTime();

        final EdgeRouteDiagnostics diagnostics = new EdgeRouteDiagnostics(edgesStraight, edgesBow,
            edgesDogleg, edgesFallback, detectedCrossings.size(),
            new ArrayList<String>(residualCrossings), hullPenetrations);
        final EdgeRouteTimings timings = new EdgeRouteTimings(indexEnd - indexStart,
            selectionEnd - selectionStart, residualEnd - residualStart);
        return EdgePaths.of(byEdge, diagnostics, timings);
    }

    public static boolean properlyCross(final EdgePath first, final EdgePath second) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        return properlyCrossSamples(first.samples(), second.samples());
    }

    private static EdgePath bestRung(final Resolved current, final QueryGrid grid,
            final GraphGeometry geometry, final double[] fractions, final boolean bow) {
        final double chordLength = distance(current.firstAnchor, current.secondAnchor);
        if (chordLength == 0.0) {
            return null;
        }
        final LayoutPoint normal = chordNormal(current.firstAnchor, current.secondAnchor);
        for (final double fraction : fractions) {
            final double offset = fraction * chordLength;
            if (Math.abs(offset) > MAX_BEND) {
                continue;
            }
            final EdgePath candidate = bow
                ? bowPath(current, normal, offset, geometry)
                : doglegPath(current, normal, offset, geometry);
            if (candidate == null || analyticRatio(fraction, bow) > ROUTE_LENGTH_CAP
                    || candidate.length() > ROUTE_LENGTH_CAP * chordLength) {
                continue;
            }
            if (clears(candidate, current.edge, grid, geometry)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * The pinned analytic ratio of a rung over its anchor chord (spec 1.4): a bow with
     * control offset {@code f * chordLength} is a quadratic, a same-side dogleg with bends
     * at {@code 1/3} and {@code 2/3} is its three legs.
     */
    private static double analyticRatio(final double fraction, final boolean bow) {
        final double magnitude = Math.abs(fraction);
        if (bow) {
            final double doubled = 2.0 * magnitude;
            return (Math.sqrt(1.0 + doubled * doubled)
                + Math.log(doubled + Math.sqrt(doubled * doubled + 1.0)) / doubled) / 2.0;
        }
        return 2.0 * Math.sqrt(1.0 / 9.0 + magnitude * magnitude) + 1.0 / 3.0;
    }

    private static EdgePath bowPath(final Resolved current, final LayoutPoint normal, final double offset,
            final GraphGeometry geometry) {
        final LayoutPoint midpoint = LayoutPoint.of(
            (current.firstAnchor.x() + current.secondAnchor.x()) / 2.0,
            (current.firstAnchor.y() + current.secondAnchor.y()) / 2.0);
        final LayoutPoint control = LayoutPoint.of(midpoint.x() + normal.x() * offset,
            midpoint.y() + normal.y() * offset);
        final LayoutPoint start = attachment(current.edge.first(), control, geometry);
        final LayoutPoint end = attachment(current.edge.second(), control, geometry);
        if (start == null || end == null) {
            return null;
        }
        return EdgePath.bow(start, control, end);
    }

    private static EdgePath doglegPath(final Resolved current, final LayoutPoint normal, final double offset,
            final GraphGeometry geometry) {
        final double chordX = current.secondAnchor.x() - current.firstAnchor.x();
        final double chordY = current.secondAnchor.y() - current.firstAnchor.y();
        final LayoutPoint bend1 = LayoutPoint.of(
            current.firstAnchor.x() + chordX / 3.0 + normal.x() * offset,
            current.firstAnchor.y() + chordY / 3.0 + normal.y() * offset);
        final LayoutPoint bend2 = LayoutPoint.of(
            current.firstAnchor.x() + 2.0 * chordX / 3.0 + normal.x() * offset,
            current.firstAnchor.y() + 2.0 * chordY / 3.0 + normal.y() * offset);
        final LayoutPoint start = attachment(current.edge.first(), bend1, geometry);
        final LayoutPoint end = attachment(current.edge.second(), bend2, geometry);
        if (start == null || end == null) {
            return null;
        }
        return EdgePath.dogleg(start, bend1, bend2, end);
    }

    private static LayoutPoint chordNormal(final LayoutPoint first, final LayoutPoint second) {
        final double dx = second.x() - first.x();
        final double dy = second.y() - first.y();
        final double length = Math.hypot(dx, dy);
        if (length == 0.0) {
            return LayoutPoint.of(0.0, 0.0);
        }
        return LayoutPoint.of(-dy / length, dx / length);
    }

    private static LayoutPoint attachment(final ProjectedEndpointKey endpoint, final LayoutPoint toward,
            final GraphGeometry geometry) {
        try {
            final LayoutPoint point = geometry.edgeAttachment(endpoint, toward);
            return isFinite(point) ? point : null;
        }
        catch (final IllegalArgumentException exception) {
            return null;
        }
    }

    private static boolean clears(final EdgePath candidate, final ProjectedEdge edge,
            final QueryGrid grid, final GraphGeometry geometry) {
        if (!clearsDiscs(candidate, edge, grid, geometry)) {
            return false;
        }
        for (final ProjectedEdgeKey key : grid.pathsNear(candidate.samples())) {
            if (key.equals(edge.key())) {
                continue;
            }
            final Resolved obstacle = grid.resolved(key);
            final EdgePath obstaclePath = grid.pathFor(key);
            if (obstacle == null || obstaclePath == null) {
                continue;
            }
            if (!clearsPath(candidate, edge, obstacle.edge, obstaclePath, geometry)) {
                return false;
            }
        }
        return true;
    }

    private static boolean clearsDiscs(final EdgePath candidate, final ProjectedEdge edge,
            final QueryGrid grid, final GraphGeometry geometry) {
        final Set<ProjectedNodeKey> attached = attachedNodes(edge);
        final double limit = EDGE_NODE_CLEARANCE - ROUTE_EPSILON;
        final List<LayoutPoint> samples = candidate.samples();
        for (final ProjectedNodeKey key : grid.discsNear(samples)) {
            if (attached.contains(key)) {
                continue;
            }
            final NodeGeometry node = geometry.nodes().get(key);
            if (node == null) {
                continue;
            }
            final double threshold = node.radius() + limit;
            for (int index = 0; index + 1 < samples.size(); index++) {
                if (pointSegmentDistance(node.center(), samples.get(index),
                        samples.get(index + 1)) < threshold) {
                    return false;
                }
            }
        }
        return true;
    }

    private static Set<ProjectedNodeKey> attachedNodes(final ProjectedEdge edge) {
        final Set<ProjectedNodeKey> attached = new LinkedHashSet<ProjectedNodeKey>();
        if (edge.first().isNode()) {
            attached.add(edge.first().node().get());
        }
        if (edge.second().isNode()) {
            attached.add(edge.second().node().get());
        }
        return attached;
    }

    private static boolean clearsPath(final EdgePath candidate, final ProjectedEdge edge,
            final ProjectedEdge obstacleEdge, final EdgePath obstaclePath,
            final GraphGeometry geometry) {
        if (properlyCrossSamples(candidate.samples(), obstaclePath.samples())) {
            return false;
        }
        final double limit = EDGE_CLEARANCE - ROUTE_EPSILON;
        final List<Disc> region = fanRegion(candidate, edge, obstacleEdge, obstaclePath, geometry);
        if (region.isEmpty()) {
            return polylineDistance(candidate.samples(), obstaclePath.samples()) >= limit;
        }
        return outsideDistance(candidate.samples(), obstaclePath.samples(), region) >= limit;
    }

    /**
     * The spec 6.4 fan region for two paths sharing an endpoint: the union of two discs,
     * centred at the two paths' own attachments at the shared endpoint, with radius
     * {@code endpointClearanceRadius(X) + EDGE_CLEARANCE}. Empty when the paths share no
     * endpoint.
     */
    private static List<Disc> fanRegion(final EdgePath candidate, final ProjectedEdge edge,
            final ProjectedEdge obstacleEdge, final EdgePath obstaclePath,
            final GraphGeometry geometry) {
        final List<Disc> region = new ArrayList<Disc>();
        addFanDiscs(region, edge.first(), candidate.start(), obstacleEdge, obstaclePath, geometry);
        addFanDiscs(region, edge.second(), candidate.end(), obstacleEdge, obstaclePath, geometry);
        return region;
    }

    private static void addFanDiscs(final List<Disc> region, final ProjectedEndpointKey endpoint,
            final LayoutPoint attachment, final ProjectedEdge obstacleEdge,
            final EdgePath obstaclePath, final GraphGeometry geometry) {
        if (!endpoint.equals(obstacleEdge.first()) && !endpoint.equals(obstacleEdge.second())) {
            return;
        }
        final double radius = endpointClearanceRadius(endpoint, geometry) + EDGE_CLEARANCE;
        region.add(new Disc(attachment, radius));
        if (endpoint.equals(obstacleEdge.first())) {
            region.add(new Disc(obstaclePath.start(), radius));
        }
        if (endpoint.equals(obstacleEdge.second())) {
            region.add(new Disc(obstaclePath.end(), radius));
        }
    }

    private static double endpointClearanceRadius(final ProjectedEndpointKey endpoint,
            final GraphGeometry geometry) {
        if (endpoint.isNode()) {
            final NodeGeometry node = geometry.nodes().get(endpoint.node().get());
            return node == null ? DRAWN_BASE_RADIUS : node.radius();
        }
        return FAN_HULL_RADIUS;
    }

    /**
     * The minimum distance over every point pair of the two sample polylines that has at
     * least one point strictly outside the union of the region discs. The two directed
     * minima of spec 6.4 together equal this value.
     */
    private static double outsideDistance(final List<LayoutPoint> first, final List<LayoutPoint> second,
            final List<Disc> region) {
        final int exponent = polylineExponent(first, second);
        final List<LayoutPoint> scaledFirst = scaleAll(first, exponent);
        final List<LayoutPoint> scaledSecond = scaleAll(second, exponent);
        final List<Disc> scaledRegion = scaleDiscs(region, exponent);
        double best = Double.POSITIVE_INFINITY;
        best = Math.min(best, outsideToPolyline(scaledFirst, scaledSecond, scaledRegion));
        best = Math.min(best, outsideToPolyline(scaledSecond, scaledFirst, scaledRegion));
        return Math.scalb(best, exponent);
    }

    private static double outsideToPolyline(final List<LayoutPoint> source, final List<LayoutPoint> other,
            final List<Disc> region) {
        double best = Double.POSITIVE_INFINITY;
        for (int index = 0; index + 1 < source.size(); index++) {
            final LayoutPoint start = source.get(index);
            final LayoutPoint end = source.get(index + 1);
            for (final double[] interval : outsideIntervals(start, end, region)) {
                best = Math.min(best, subPolylineDistance(
                    interpolate(start, end, interval[0]), interpolate(start, end, interval[1]), other));
            }
        }
        return best;
    }

    private static double subPolylineDistance(final LayoutPoint start, final LayoutPoint end,
            final List<LayoutPoint> other) {
        double best = Double.POSITIVE_INFINITY;
        for (int index = 0; index + 1 < other.size(); index++) {
            best = Math.min(best, segmentDistanceUnscaled(start, end, other.get(index),
                other.get(index + 1)));
        }
        return best;
    }

    /** The parts of segment {@code a..b} strictly outside every region disc, as t intervals. */
    private static List<double[]> outsideIntervals(final LayoutPoint a, final LayoutPoint b,
            final List<Disc> region) {
        final double dx = b.x() - a.x();
        final double dy = b.y() - a.y();
        final double lengthSquared = dx * dx + dy * dy;
        final List<double[]> inside = new ArrayList<double[]>();
        for (final Disc disc : region) {
            final double fx = a.x() - disc.center.x();
            final double fy = a.y() - disc.center.y();
            if (lengthSquared <= SEGMENT_EPSILON) {
                if (fx * fx + fy * fy <= disc.radius * disc.radius) {
                    inside.add(new double[] {0.0, 1.0});
                }
                continue;
            }
            final double bCoefficient = 2.0 * (fx * dx + fy * dy);
            final double cCoefficient = fx * fx + fy * fy - disc.radius * disc.radius;
            final double discriminant = bCoefficient * bCoefficient - 4.0 * lengthSquared * cCoefficient;
            if (discriminant <= 0.0) {
                continue;
            }
            final double root = Math.sqrt(discriminant);
            final double entry = (-bCoefficient - root) / (2.0 * lengthSquared);
            final double exit = (-bCoefficient + root) / (2.0 * lengthSquared);
            if (exit < 0.0 || entry > 1.0) {
                continue;
            }
            inside.add(new double[] {Math.max(0.0, entry), Math.min(1.0, exit)});
        }
        Collections.sort(inside, new Comparator<double[]>() {
            @Override
            public int compare(final double[] first, final double[] second) {
                return Double.compare(first[0], second[0]);
            }
        });
        final List<double[]> outside = new ArrayList<double[]>();
        double cursor = 0.0;
        for (final double[] interval : inside) {
            if (interval[0] > cursor) {
                outside.add(new double[] {cursor, interval[0]});
            }
            cursor = Math.max(cursor, interval[1]);
        }
        if (cursor < 1.0) {
            outside.add(new double[] {cursor, 1.0});
        }
        return outside;
    }

    private static LayoutPoint interpolate(final LayoutPoint start, final LayoutPoint end, final double t) {
        if (t <= 0.0) {
            return start;
        }
        if (t >= 1.0) {
            return end;
        }
        return LayoutPoint.of(start.x() + t * (end.x() - start.x()),
            start.y() + t * (end.y() - start.y()));
    }

    private static double polylineDistance(final List<LayoutPoint> first, final List<LayoutPoint> second) {
        double best = Double.POSITIVE_INFINITY;
        for (int a = 0; a + 1 < first.size(); a++) {
            for (int b = 0; b + 1 < second.size(); b++) {
                best = Math.min(best, segmentDistance(first.get(a), first.get(a + 1), second.get(b),
                    second.get(b + 1)));
            }
        }
        return best;
    }

    private static double pointSegmentDistance(final LayoutPoint point, final LayoutPoint start,
            final LayoutPoint end) {
        final int exponent = commonExponent(point, start, end);
        return Math.scalb(pointSegmentDistanceUnscaled(scale(point, exponent),
            scale(start, exponent), scale(end, exponent)), exponent);
    }

    private static double pointSegmentDistanceUnscaled(final LayoutPoint point, final LayoutPoint start,
            final LayoutPoint end) {
        final double dx = end.x() - start.x();
        final double dy = end.y() - start.y();
        final double lengthSquared = dx * dx + dy * dy;
        if (lengthSquared <= SEGMENT_EPSILON) {
            return Math.hypot(point.x() - start.x(), point.y() - start.y());
        }
        final double t = clamp(((point.x() - start.x()) * dx + (point.y() - start.y()) * dy)
            / lengthSquared);
        return Math.hypot(point.x() - (start.x() + t * dx), point.y() - (start.y() + t * dy));
    }

    private static double segmentDistance(final LayoutPoint firstStart, final LayoutPoint firstEnd,
            final LayoutPoint secondStart, final LayoutPoint secondEnd) {
        final int exponent = commonExponent(firstStart, firstEnd, secondStart, secondEnd);
        return Math.scalb(segmentDistanceUnscaled(scale(firstStart, exponent), scale(firstEnd, exponent),
            scale(secondStart, exponent), scale(secondEnd, exponent)), exponent);
    }

    private static double segmentDistanceUnscaled(final LayoutPoint p1, final LayoutPoint q1,
            final LayoutPoint p2, final LayoutPoint q2) {
        final double d1x = q1.x() - p1.x();
        final double d1y = q1.y() - p1.y();
        final double d2x = q2.x() - p2.x();
        final double d2y = q2.y() - p2.y();
        final double rx = p1.x() - p2.x();
        final double ry = p1.y() - p2.y();
        final double firstLengthSquared = d1x * d1x + d1y * d1y;
        final double secondLengthSquared = d2x * d2x + d2y * d2y;
        final double secondProjection = d2x * rx + d2y * ry;
        double s;
        double t;
        if (firstLengthSquared <= SEGMENT_EPSILON && secondLengthSquared <= SEGMENT_EPSILON) {
            return Math.hypot(rx, ry);
        }
        if (firstLengthSquared <= SEGMENT_EPSILON) {
            s = 0.0;
            t = clamp(secondProjection / secondLengthSquared);
        }
        else {
            final double firstProjection = d1x * rx + d1y * ry;
            if (secondLengthSquared <= SEGMENT_EPSILON) {
                t = 0.0;
                s = clamp(-firstProjection / firstLengthSquared);
            }
            else {
                final double cross = d1x * d2x + d1y * d2y;
                final double denominator = firstLengthSquared * secondLengthSquared - cross * cross;
                s = denominator == 0.0 ? 0.0 : clamp((cross * secondProjection
                    - firstProjection * secondLengthSquared) / denominator);
                t = (cross * s + secondProjection) / secondLengthSquared;
                if (t < 0.0) {
                    t = 0.0;
                    s = clamp(-firstProjection / firstLengthSquared);
                }
                else if (t > 1.0) {
                    t = 1.0;
                    s = clamp((cross - firstProjection) / firstLengthSquared);
                }
            }
        }
        final double firstX = p1.x() + d1x * s;
        final double firstY = p1.y() + d1y * s;
        final double secondX = p2.x() + d2x * t;
        final double secondY = p2.y() + d2y * t;
        return Math.hypot(firstX - secondX, firstY - secondY);
    }

    private static double clamp(final double value) {
        if (value < 0.0) {
            return 0.0;
        }
        if (value > 1.0) {
            return 1.0;
        }
        return value;
    }

    private static int polylineExponent(final List<LayoutPoint> first, final List<LayoutPoint> second) {
        int exponent = Integer.MIN_VALUE;
        exponent = Math.max(exponent, polylineExponent(first));
        exponent = Math.max(exponent, polylineExponent(second));
        return exponent == Integer.MIN_VALUE ? 0 : exponent;
    }

    private static int polylineExponent(final List<LayoutPoint> points) {
        int exponent = Integer.MIN_VALUE;
        for (final LayoutPoint point : points) {
            exponent = Math.max(exponent, coordinateExponent(point.x()));
            exponent = Math.max(exponent, coordinateExponent(point.y()));
        }
        return exponent;
    }

    private static List<LayoutPoint> scaleAll(final List<LayoutPoint> points, final int exponent) {
        final List<LayoutPoint> result = new ArrayList<LayoutPoint>(points.size());
        for (final LayoutPoint point : points) {
            result.add(scale(point, exponent));
        }
        return result;
    }

    private static List<Disc> scaleDiscs(final List<Disc> discs, final int exponent) {
        final List<Disc> result = new ArrayList<Disc>(discs.size());
        for (final Disc disc : discs) {
            result.add(new Disc(scale(disc.center, exponent), Math.scalb(disc.radius, -exponent)));
        }
        return result;
    }

    private static LayoutPoint scale(final LayoutPoint point, final int exponent) {
        return LayoutPoint.of(Math.scalb(point.x(), -exponent), Math.scalb(point.y(), -exponent));
    }

    /**
     * Spec 6.9 containment: an endpoint is contained by a hull iff (a) an enclosure
     * endpoint is one of the hull's endpoint keys, (b) the endpoint source node's
     * enclosure path contains the hull, or (c) the hull is an ancestor of a hull on that
     * path.
     */
    private static boolean containedByHull(final ProjectedEndpointKey endpoint,
            final EnclosureHullKey hullKey, final Map<ProjectedNodeKey, List<EnclosureHullKey>> nodeHulls,
            final Map<EnclosureHullKey, EnclosureHullKey> parents) {
        if (endpoint.isEnclosure() && hullKey.endpointKeys().contains(endpoint.enclosure().get())) {
            return true;
        }
        final ProjectedNodeKey sourceNode = endpoint.isNode() ? endpoint.node().get()
            : ProjectedNodeKey.of(endpoint.enclosure().get().source());
        final List<EnclosureHullKey> directHulls = nodeHulls.get(sourceNode);
        if (directHulls == null) {
            return false;
        }
        for (final EnclosureHullKey direct : directHulls) {
            for (EnclosureHullKey current = direct; current != null; current = parents.get(current)) {
                if (current.equals(hullKey)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean crossesPolygon(final List<LayoutPoint> samples, final List<LayoutPoint> polygon) {
        final int count = polygon.size();
        for (int vertex = 0; vertex < count; vertex++) {
            final LayoutPoint start = polygon.get(vertex);
            final LayoutPoint end = polygon.get((vertex + 1) % count);
            for (int index = 0; index + 1 < samples.size(); index++) {
                if (properlyCrossSegments(samples.get(index), samples.get(index + 1), start, end)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Resolved resolve(final ProjectedEdge edge,
            final Map<EnclosureKey, EnclosureHullKey> hullKeys, final LayoutPositions positions,
            final GraphGeometry geometry, final Set<ProjectedEndpointKey> visibleEndpoints) {
        if (!visibleEndpoints.contains(edge.first()) || !visibleEndpoints.contains(edge.second())) {
            return null;
        }
        final LayoutPoint firstAnchor = anchor(edge.first(), hullKeys, positions, geometry);
        final LayoutPoint secondAnchor = anchor(edge.second(), hullKeys, positions, geometry);
        if (!isFinite(firstAnchor) || !isFinite(secondAnchor)) {
            return null;
        }
        final LayoutPoint firstAttachment = attachment(edge.first(), secondAnchor, geometry);
        final LayoutPoint secondAttachment = attachment(edge.second(), firstAnchor, geometry);
        if (firstAttachment == null || secondAttachment == null) {
            return null;
        }
        return new Resolved(edge, firstAnchor, secondAnchor,
            EdgePath.straight(firstAttachment, secondAttachment));
    }

    private static LayoutPoint anchor(final ProjectedEndpointKey endpoint,
            final Map<EnclosureKey, EnclosureHullKey> hullKeys, final LayoutPositions positions,
            final GraphGeometry geometry) {
        if (endpoint.isNode()) {
            final NodeGeometry node = geometry.nodes().get(endpoint.node().get());
            return node == null ? null : node.center();
        }
        final EnclosureHullKey hullKey = hullKeys.get(endpoint.enclosure().get());
        if (hullKey == null) {
            return null;
        }
        final LayoutPoint layoutAnchor = positions.anchors().get(hullKey);
        if (isFinite(layoutAnchor)) {
            return layoutAnchor;
        }
        final HullGeometry hull = geometry.hulls().get(hullKey);
        final LayoutPoint labelAnchor = hull == null ? null : hull.labelAnchor();
        return isFinite(labelAnchor) ? labelAnchor : null;
    }

    private static Map<EnclosureKey, EnclosureHullKey> enclosureHullKeys(final GraphProjection projection,
            final GraphGeometry geometry) {
        final Map<EnclosureKey, EnclosureHullKey> hullKeys =
            new LinkedHashMap<EnclosureKey, EnclosureHullKey>();
        for (final ProjectedEnclosure enclosure : projection.enclosures()) {
            final EnclosureHullKey hullKey = enclosure.hullKey();
            if (geometry.hulls().get(hullKey) == null) {
                continue;
            }
            for (final EnclosureKey endpoint : enclosure.endpointKeys()) {
                hullKeys.put(endpoint, hullKey);
            }
        }
        return hullKeys;
    }

    private static List<ProjectedEdge> orderedEdges(final GraphProjection projection) {
        final Map<String, ProjectedEdge> ordered = new TreeMap<String, ProjectedEdge>();
        for (final ProjectedEdge edge : projection.edges()) {
            ordered.put(CanonicalKeys.edge(edge.key()), edge);
        }
        return new ArrayList<ProjectedEdge>(ordered.values());
    }

    private static boolean properlyCrossSamples(final List<LayoutPoint> first,
            final List<LayoutPoint> second) {
        for (int a = 0; a + 1 < first.size(); a++) {
            for (int b = 0; b + 1 < second.size(); b++) {
                if (properlyCrossSegments(first.get(a), first.get(a + 1), second.get(b),
                        second.get(b + 1))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int orientationSign(final int exponent, final LayoutPoint p, final LayoutPoint q,
            final LayoutPoint r) {
        final double px = Math.scalb(p.x(), -exponent);
        final double py = Math.scalb(p.y(), -exponent);
        final double qx = Math.scalb(q.x(), -exponent);
        final double qy = Math.scalb(q.y(), -exponent);
        final double rx = Math.scalb(r.x(), -exponent);
        final double ry = Math.scalb(r.y(), -exponent);
        final double cross = (qx - px) * (ry - py) - (qy - py) * (rx - px);
        return cross == 0.0 ? 0 : Double.compare(cross, 0.0);
    }

    /**
     * The exponent of the dominant coordinate magnitude: every scaled coordinate then has
     * magnitude below 2, so no scaled product can overflow. Zeros carry no magnitude and
     * must not sink the exponent to {@code Math.getExponent(0.0) == -1023}.
     */
    private static int commonExponent(final LayoutPoint... points) {
        int exponent = Integer.MIN_VALUE;
        for (final LayoutPoint point : points) {
            exponent = Math.max(exponent, coordinateExponent(point.x()));
            exponent = Math.max(exponent, coordinateExponent(point.y()));
        }
        return exponent == Integer.MIN_VALUE ? 0 : exponent;
    }

    private static int coordinateExponent(final double value) {
        return value == 0.0 ? Integer.MIN_VALUE : Math.getExponent(value);
    }

    private static boolean properlyCrossSegments(final LayoutPoint a, final LayoutPoint b,
            final LayoutPoint c, final LayoutPoint d) {
        if (isFastOrientationSafe(a) && isFastOrientationSafe(b)
                && isFastOrientationSafe(c) && isFastOrientationSafe(d)) {
            return orientationSignDirect(a, b, c) * orientationSignDirect(a, b, d) < 0
                && orientationSignDirect(c, d, a) * orientationSignDirect(c, d, b) < 0;
        }
        final int exponent = commonExponent(a, b, c, d);
        return orientationSign(exponent, a, b, c) * orientationSign(exponent, a, b, d) < 0
            && orientationSign(exponent, c, d, a) * orientationSign(exponent, c, d, b) < 0;
    }

    private static boolean isFastOrientationSafe(final LayoutPoint point) {
        return Math.abs(point.x()) <= FAST_ORIENTATION_LIMIT
            && Math.abs(point.y()) <= FAST_ORIENTATION_LIMIT;
    }

    private static int orientationSignDirect(final LayoutPoint p, final LayoutPoint q,
            final LayoutPoint r) {
        final double cross = (q.x() - p.x()) * (r.y() - p.y())
            - (q.y() - p.y()) * (r.x() - p.x());
        return cross == 0.0 ? 0 : Double.compare(cross, 0.0);
    }

    private static double distance(final LayoutPoint first, final LayoutPoint second) {
        return Math.hypot(second.x() - first.x(), second.y() - first.y());
    }

    private static boolean isFinite(final LayoutPoint point) {
        return point != null && Double.isFinite(point.x()) && Double.isFinite(point.y());
    }

    /** One routed edge with its published-length anchors and its straight attachment chord. */
    private static final class Resolved {
        private final ProjectedEdge edge;
        private final LayoutPoint firstAnchor;
        private final LayoutPoint secondAnchor;
        private final EdgePath straight;

        private Resolved(final ProjectedEdge edge, final LayoutPoint firstAnchor,
                final LayoutPoint secondAnchor, final EdgePath straight) {
            this.edge = edge;
            this.firstAnchor = firstAnchor;
            this.secondAnchor = secondAnchor;
            this.straight = straight;
        }
    }

    /**
     * The per-frame router query index of design 9: one uniform grid over disc bounding boxes
     * and path sample segments, built once per {@code route} call and reused for every
     * clearance and residual query of that published frame. Paths and hull polygon edges are
     * inserted into every cell their segments traverse, discs into every cell of their inflated
     * bounding box, and a proximity query widens the traversed cells by the smallest ring that
     * still shares a cell with every primitive within the pinned clearance. Published paths are
     * inserted as they are decided, so a query sees the already-published path of an earlier
     * edge and the straight chord of a later one.
     */
    private static final class QueryGrid {
        private final List<Resolved> resolved;
        private final GraphGeometry geometry;
        private final Map<ProjectedEdgeKey, Integer> idsByKey =
            new LinkedHashMap<ProjectedEdgeKey, Integer>();
        private final Resolved[] resolvedById;
        private final ProjectedEdgeKey[] keysById;
        private final EdgePath[] straightById;
        private final EdgePath[] publishedById;
        private final Map<Long, List<Integer>> pathCells = new HashMap<Long, List<Integer>>();
        private final Map<Long, LinkedHashSet<ProjectedNodeKey>> discCells =
            new HashMap<Long, LinkedHashSet<ProjectedNodeKey>>();
        private final Map<Long, LinkedHashSet<EnclosureHullKey>> hullCells =
            new HashMap<Long, LinkedHashSet<EnclosureHullKey>>();
        private final LinkedHashSet<ProjectedNodeKey> oversizedDiscs =
            new LinkedHashSet<ProjectedNodeKey>();
        private final LinkedHashSet<EnclosureHullKey> oversizedHulls =
            new LinkedHashSet<EnclosureHullKey>();
        private final double originX;
        private final double originY;
        private final double cellSize;
        private final int pathRing;
        /** Reused candidate stamp array; {@code seen[id] == edge + 1} means already gathered. */
        private final int[] seen;
        /** Per-id stamp array for one {@code collectIds} call; reset lazily by stamp value. */
        private final int[] collectedStamp;
        private int nextCollectStamp = 1;
        private int[] stack;

        private QueryGrid(final List<Resolved> resolved, final GraphGeometry geometry) {
            this.resolved = resolved;
            this.geometry = geometry;
            resolvedById = new Resolved[resolved.size()];
            keysById = new ProjectedEdgeKey[resolved.size()];
            straightById = new EdgePath[resolved.size()];
            publishedById = new EdgePath[resolved.size()];
            seen = new int[resolved.size()];
            collectedStamp = new int[resolved.size()];
            stack = new int[Math.max(16, resolved.size())];
            for (int index = 0; index < resolved.size(); index++) {
                final Resolved value = resolved.get(index);
                resolvedById[index] = value;
                keysById[index] = value.edge.key();
                straightById[index] = value.straight;
                idsByKey.put(value.edge.key(), Integer.valueOf(index));
            }
            final double[] bounds = frameBounds(resolved, geometry);
            if (bounds == null) {
                originX = 0.0;
                originY = 0.0;
                cellSize = 1.0;
            }
            else {
                originX = bounds[0];
                originY = bounds[1];
                final double extent = Math.max(bounds[2] - bounds[0], bounds[3] - bounds[1]);
                final double candidate = extent > 0.0 ? extent / (double) GRID_DIMENSION : 0.0;
                cellSize = Double.isFinite(candidate) && candidate > 0.0 ? candidate : 1.0;
            }
            pathRing = Math.max(1,
                (int) Math.floor((EDGE_CLEARANCE - ROUTE_EPSILON) / cellSize) + 1);
            insertDiscs();
            insertHulls();
            for (int index = 0; index < resolved.size(); index++) {
                insertPath(keysById[index], straightById[index]);
            }
        }

        private void publish(final ProjectedEdgeKey key, final EdgePath path) {
            final Integer id = idsByKey.get(key);
            if (id != null) {
                publishedById[id.intValue()] = path;
            }
            insertPath(key, path);
        }

        private Resolved resolved(final ProjectedEdgeKey key) {
            final Integer id = idsByKey.get(key);
            return id == null ? null : resolvedById[id.intValue()];
        }

        private EdgePath pathFor(final ProjectedEdgeKey key) {
            final Integer id = idsByKey.get(key);
            return id == null ? null : pathFor(id.intValue());
        }

        private EdgePath pathFor(final int id) {
            final EdgePath publishedPath = publishedById[id];
            return publishedPath != null ? publishedPath : straightById[id];
        }

        private LinkedHashSet<ProjectedNodeKey> discsNear(final List<LayoutPoint> samples) {
            final LinkedHashSet<ProjectedNodeKey> found = new LinkedHashSet<ProjectedNodeKey>();
            collect(samples, discCells, found, 0);
            found.addAll(oversizedDiscs);
            return found;
        }

        private LinkedHashSet<ProjectedEdgeKey> pathsNear(final List<LayoutPoint> samples) {
            final LinkedHashSet<ProjectedEdgeKey> found = new LinkedHashSet<ProjectedEdgeKey>();
            for (final Integer id : collectIds(samples, pathRing)) {
                found.add(keysById[id.intValue()]);
            }
            return found;
        }

        /**
         * Every unordered pair of resolved edges whose paths properly cross, found through the
         * grid: for each edge in canonical order the ids in the cells its samples traverse are
         * deduplicated with a stamp array, then each pair is tested exactly once with the
         * sign-only predicate of spec 3.2. The traversal steps one axis at a time, so two
         * segments crossing at a cell corner still share the corner cell.
         */
        private Set<String> crossingPairs(final boolean publishedPaths) {
            Arrays.fill(seen, 0);
            final Set<String> crossings = new TreeSet<String>();
            for (int first = 0; first < resolvedById.length; first++) {
                final EdgePath firstPath = publishedPaths ? pathFor(first) : straightById[first];
                if (firstPath == null) {
                    continue;
                }
                final int stamp = first + 1;
                int count = 0;
                for (final Integer candidate : collectIds(firstPath.samples(), 0)) {
                    final int second = candidate.intValue();
                    if (second == first || seen[second] == stamp) {
                        continue;
                    }
                    seen[second] = stamp;
                    if (count == stack.length) {
                        final int[] grown = new int[stack.length * 2];
                        System.arraycopy(stack, 0, grown, 0, stack.length);
                        stack = grown;
                    }
                    stack[count++] = second;
                }
                for (int index = 0; index < count; index++) {
                    final int second = stack[index];
                    if (second <= first) {
                        continue;
                    }
                    final EdgePath secondPath = publishedPaths ? pathFor(second)
                        : straightById[second];
                    if (secondPath == null) {
                        continue;
                    }
                    if (properlyCrossSamples(firstPath.samples(), secondPath.samples())) {
                        crossings.add(CanonicalKeys.edgePair(keysById[first], keysById[second]));
                    }
                }
            }
            return crossings;
        }

        private int hullPenetrations(final Map<ProjectedNodeKey, List<EnclosureHullKey>> nodeHulls,
                final Map<EnclosureHullKey, EnclosureHullKey> parents) {
            int penetrations = 0;
            for (final Resolved current : resolved) {
                final EdgePath path = pathFor(current.edge.key());
                final LinkedHashSet<EnclosureHullKey> hulls = new LinkedHashSet<EnclosureHullKey>();
                collect(path.samples(), hullCells, hulls, 0);
                hulls.addAll(oversizedHulls);
                for (final EnclosureHullKey hullKey : hulls) {
                    final HullGeometry hull = geometry.hulls().get(hullKey);
                    if (hull == null) {
                        continue;
                    }
                    if (containedByHull(current.edge.first(), hullKey, nodeHulls, parents)
                            || containedByHull(current.edge.second(), hullKey, nodeHulls, parents)) {
                        continue;
                    }
                    if (crossesPolygon(path.samples(), hull.exactPolygon())) {
                        penetrations++;
                    }
                }
            }
            return penetrations;
        }

        private void insertDiscs() {
            final double limit = EDGE_NODE_CLEARANCE - ROUTE_EPSILON;
            for (final Map.Entry<ProjectedNodeKey, NodeGeometry> entry : geometry.nodes().entrySet()) {
                final NodeGeometry node = entry.getValue();
                if (!isFinite(node.center())) {
                    continue;
                }
                final double radius = node.radius() + limit;
                final double minX = node.center().x() - radius;
                final double minY = node.center().y() - radius;
                final double maxX = node.center().x() + radius;
                final double maxY = node.center().y() + radius;
                if (cellCount(minX, minY, maxX, maxY) > MAX_GRID_CELLS_PER_PRIMITIVE) {
                    oversizedDiscs.add(entry.getKey());
                }
                else {
                    insertBox(discCells, entry.getKey(), minX, minY, maxX, maxY);
                }
            }
        }

        private void insertHulls() {
            for (final Map.Entry<EnclosureHullKey, HullGeometry> entry : geometry.hulls().entrySet()) {
                final List<LayoutPoint> polygon = entry.getValue().exactPolygon();
                if (polygon.isEmpty()) {
                    continue;
                }
                if (!allFinite(polygon)) {
                    oversizedHulls.add(entry.getKey());
                    continue;
                }
                for (int index = 0; index < polygon.size(); index++) {
                    insertPolygonSegment(entry.getKey(), polygon.get(index),
                        polygon.get((index + 1) % polygon.size()));
                }
            }
        }

        private void insertPath(final ProjectedEdgeKey key, final EdgePath path) {
            final Integer id = idsByKey.get(key);
            if (id == null) {
                return;
            }
            final List<LayoutPoint> samples = path.samples();
            for (int index = 0; index + 1 < samples.size(); index++) {
                insertPathSegment(id.intValue(), samples.get(index), samples.get(index + 1));
            }
        }

        private void insertPathSegment(final int id, final LayoutPoint start, final LayoutPoint end) {
            if (!isFinite(start) || !isFinite(end)) {
                return;
            }
            for (final Long cell : traversedCellKeys(start, end)) {
                addPathValue(pathCells, cell.longValue(), id);
            }
        }

        private void insertPolygonSegment(final EnclosureHullKey key, final LayoutPoint start,
                final LayoutPoint end) {
            if (!isFinite(start) || !isFinite(end)) {
                return;
            }
            for (final Long cell : traversedCellKeys(start, end)) {
                addValue(hullCells, cell.longValue(), key);
            }
        }

        private <T> void insertBox(final Map<Long, LinkedHashSet<T>> cells, final T value,
                final double minX, final double minY, final double maxX, final double maxY) {
            final int firstX = cellX(minX);
            final int lastX = cellX(maxX);
            final int firstY = cellY(minY);
            final int lastY = cellY(maxY);
            for (int x = firstX; x <= lastX; x++) {
                for (int y = firstY; y <= lastY; y++) {
                    addValue(cells, pack(x, y), value);
                }
            }
        }

        private <T> void collect(final List<LayoutPoint> samples,
                final Map<Long, LinkedHashSet<T>> cells, final Set<T> found, final int ring) {
            for (int index = 0; index + 1 < samples.size(); index++) {
                final LayoutPoint start = samples.get(index);
                final LayoutPoint end = samples.get(index + 1);
                if (!isFinite(start) || !isFinite(end)) {
                    continue;
                }
                for (final Long cell : traversedCellKeys(start, end)) {
                    for (int dx = -ring; dx <= ring; dx++) {
                        for (int dy = -ring; dy <= ring; dy++) {
                            final LinkedHashSet<T> members = cells.get(Long.valueOf(
                                pack(clampCell(unpackX(cell.longValue()) + dx),
                                    clampCell(unpackY(cell.longValue()) + dy))));
                            if (members != null) {
                                found.addAll(members);
                            }
                        }
                    }
                }
            }
        }

        /** The distinct path ids in the cell ring of the sample polyline, in first-seen order. */
        private List<Integer> collectIds(final List<LayoutPoint> samples, final int ring) {
            final List<Integer> found = new ArrayList<Integer>();
            if (nextCollectStamp == Integer.MAX_VALUE) {
                Arrays.fill(collectedStamp, 0);
                nextCollectStamp = 1;
            }
            final int stamp = nextCollectStamp++;
            for (int index = 0; index + 1 < samples.size(); index++) {
                final LayoutPoint start = samples.get(index);
                final LayoutPoint end = samples.get(index + 1);
                if (!isFinite(start) || !isFinite(end)) {
                    continue;
                }
                for (final Long cell : traversedCellKeys(start, end)) {
                    for (int dx = -ring; dx <= ring; dx++) {
                        for (int dy = -ring; dy <= ring; dy++) {
                            final List<Integer> members = pathCells.get(Long.valueOf(
                                pack(clampCell(unpackX(cell.longValue()) + dx),
                                    clampCell(unpackY(cell.longValue()) + dy))));
                            if (members != null) {
                                for (final Integer member : members) {
                                    final int id = member.intValue();
                                    if (collectedStamp[id] != stamp) {
                                        collectedStamp[id] = stamp;
                                        found.add(member);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return found;
        }

        /**
         * The cells the closed segment {@code start..end} touches, walked by the standard
         * unit-cell DDA. Only one axis is stepped at a time, so a segment passing exactly
         * through a cell corner visits the corner cell (and its two axial neighbours), which is
         * also the cell an endpoint on that corner is indexed into; two segments crossing at a
         * corner therefore always share a cell without inflating every traversal.
         */
        private List<Long> traversedCellKeys(final LayoutPoint start, final LayoutPoint end) {
            final List<Long> cells = new ArrayList<Long>();
            final int startX = cellX(start.x());
            final int startY = cellY(start.y());
            final int endX = cellX(end.x());
            final int endY = cellY(end.y());
            int x = startX;
            int y = startY;
            cells.add(Long.valueOf(pack(x, y)));
            final int stepX = endX > startX ? 1 : (endX < startX ? -1 : 0);
            final int stepY = endY > startY ? 1 : (endY < startY ? -1 : 0);
            if (stepX == 0 && stepY == 0) {
                return cells;
            }
            final double dx = end.x() - start.x();
            final double dy = end.y() - start.y();
            final double offsetX = (start.x() - originX) - (double) startX * cellSize;
            final double offsetY = (start.y() - originY) - (double) startY * cellSize;
            double tMaxX = stepX == 0 ? Double.POSITIVE_INFINITY
                : stepX > 0 ? (cellSize - offsetX) / dx : offsetX / -dx;
            double tMaxY = stepY == 0 ? Double.POSITIVE_INFINITY
                : stepY > 0 ? (cellSize - offsetY) / dy : offsetY / -dy;
            final double tDeltaX = stepX == 0 ? Double.POSITIVE_INFINITY : Math.abs(cellSize / dx);
            final double tDeltaY = stepY == 0 ? Double.POSITIVE_INFINITY : Math.abs(cellSize / dy);
            final int maximumSteps = 2 * MAX_CELL_INDEX + 8;
            int steps = 0;
            while ((x != endX || y != endY) && steps < maximumSteps) {
                if (tMaxX < tMaxY) {
                    x += stepX;
                    tMaxX += tDeltaX;
                }
                else {
                    y += stepY;
                    tMaxY += tDeltaY;
                }
                cells.add(Long.valueOf(pack(x, y)));
                steps++;
            }
            return cells;
        }

        private int cellX(final double coordinate) {
            return cellIndex(coordinate - originX);
        }

        private int cellY(final double coordinate) {
            return cellIndex(coordinate - originY);
        }

        private int cellIndex(final double offset) {
            if (!Double.isFinite(offset)) {
                return 0;
            }
            final double scaled = offset / cellSize;
            if (!(scaled > 0.0)) {
                return 0;
            }
            if (scaled >= (double) MAX_CELL_INDEX) {
                return MAX_CELL_INDEX;
            }
            return (int) Math.floor(scaled);
        }

        private int cellCount(final double minX, final double minY, final double maxX,
                final double maxY) {
            final long width = (long) cellX(maxX) - (long) cellX(minX) + 1L;
            final long height = (long) cellY(maxY) - (long) cellY(minY) + 1L;
            final long count = width * height;
            return count > (long) Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) count;
        }

        private static double[] frameBounds(final List<Resolved> resolved,
                final GraphGeometry geometry) {
            final double[] bounds = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
            for (final Resolved value : resolved) {
                for (final LayoutPoint point : value.straight.samples()) {
                    extend(bounds, point);
                }
            }
            for (final Map.Entry<ProjectedNodeKey, NodeGeometry> entry : geometry.nodes().entrySet()) {
                final NodeGeometry node = entry.getValue();
                if (!isFinite(node.center())) {
                    continue;
                }
                bounds[0] = Math.min(bounds[0], node.center().x() - node.radius());
                bounds[1] = Math.min(bounds[1], node.center().y() - node.radius());
                bounds[2] = Math.max(bounds[2], node.center().x() + node.radius());
                bounds[3] = Math.max(bounds[3], node.center().y() + node.radius());
            }
            for (final Map.Entry<EnclosureHullKey, HullGeometry> entry : geometry.hulls().entrySet()) {
                for (final LayoutPoint point : entry.getValue().exactPolygon()) {
                    extend(bounds, point);
                }
            }
            return bounds[0] > bounds[2] ? null : bounds;
        }

        private static void extend(final double[] bounds, final LayoutPoint point) {
            if (!isFinite(point)) {
                return;
            }
            if (point.x() < bounds[0]) {
                bounds[0] = point.x();
            }
            if (point.y() < bounds[1]) {
                bounds[1] = point.y();
            }
            if (point.x() > bounds[2]) {
                bounds[2] = point.x();
            }
            if (point.y() > bounds[3]) {
                bounds[3] = point.y();
            }
        }

        private static boolean allFinite(final List<LayoutPoint> points) {
            for (final LayoutPoint point : points) {
                if (!isFinite(point)) {
                    return false;
                }
            }
            return true;
        }

        private static void addPathValue(final Map<Long, List<Integer>> cells, final long key,
                final int value) {
            final Long boxed = Long.valueOf(key);
            List<Integer> members = cells.get(boxed);
            if (members == null) {
                members = new ArrayList<Integer>();
                cells.put(boxed, members);
            }
            members.add(Integer.valueOf(value));
        }

        private static <T> void addValue(final Map<Long, LinkedHashSet<T>> cells,
                final long key, final T value) {
            final Long boxed = Long.valueOf(key);
            LinkedHashSet<T> members = cells.get(boxed);
            if (members == null) {
                members = new LinkedHashSet<T>();
                cells.put(boxed, members);
            }
            members.add(value);
        }

        private static int clampCell(final int value) {
            if (value < 0) {
                return 0;
            }
            if (value > MAX_CELL_INDEX) {
                return MAX_CELL_INDEX;
            }
            return value;
        }

        private static long pack(final int x, final int y) {
            return ((long) x << 32) | (y & 0xffffffffL);
        }

        private static int unpackX(final long key) {
            return (int) (key >> 32);
        }

        private static int unpackY(final long key) {
            return (int) key;
        }
    }

    /** One fan-exemption disc of spec 6.4. */
    private static final class Disc {
        private final LayoutPoint center;
        private final double radius;

        private Disc(final LayoutPoint center, final double radius) {
            this.center = center;
            this.radius = radius;
        }
    }
}
