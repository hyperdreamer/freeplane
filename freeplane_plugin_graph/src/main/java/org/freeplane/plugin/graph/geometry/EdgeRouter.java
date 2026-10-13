package org.freeplane.plugin.graph.geometry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
        final Map<ProjectedEdgeKey, EdgePath> straightChords =
            new LinkedHashMap<ProjectedEdgeKey, EdgePath>();
        for (final Resolved value : resolved) {
            straightChords.put(value.edge.key(), value.straight);
        }
        final Set<String> detectedCrossings = crossingPairKeys(straightChords);
        final long indexEnd = System.nanoTime();

        final long selectionStart = System.nanoTime();
        final Map<ProjectedEdgeKey, EdgePath> byEdge = new LinkedHashMap<ProjectedEdgeKey, EdgePath>();
        int edgesStraight = 0;
        int edgesBow = 0;
        int edgesDogleg = 0;
        int edgesFallback = 0;
        for (int index = 0; index < resolved.size(); index++) {
            final Resolved current = resolved.get(index);
            final List<PathObstacle> obstacles = new ArrayList<PathObstacle>();
            for (int previous = 0; previous < index; previous++) {
                final Resolved earlier = resolved.get(previous);
                obstacles.add(new PathObstacle(earlier.edge, byEdge.get(earlier.edge.key())));
            }
            for (int later = index + 1; later < resolved.size(); later++) {
                final Resolved next = resolved.get(later);
                obstacles.add(new PathObstacle(next.edge, next.straight));
            }
            if (clears(current.straight, current.edge, obstacles, geometry)) {
                byEdge.put(current.edge.key(), current.straight);
                edgesStraight++;
                continue;
            }
            final EdgePath bow = bestRung(current, obstacles, geometry, BOW_FRACTIONS, true);
            if (bow != null) {
                byEdge.put(current.edge.key(), bow);
                edgesBow++;
                continue;
            }
            final EdgePath dogleg = bestRung(current, obstacles, geometry, DOGLEG_FRACTIONS, false);
            if (dogleg != null) {
                byEdge.put(current.edge.key(), dogleg);
                edgesDogleg++;
                continue;
            }
            byEdge.put(current.edge.key(), current.straight);
            edgesFallback++;
        }
        final long selectionEnd = System.nanoTime();

        final long residualStart = System.nanoTime();
        final Set<String> residualCrossings = crossingPairKeys(byEdge);
        final int hullPenetrations = hullPenetrations(resolved, byEdge, geometry, nodeHulls, parents);
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

    private static EdgePath bestRung(final Resolved current, final List<PathObstacle> obstacles,
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
            if (clears(candidate, current.edge, obstacles, geometry)) {
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
            final List<PathObstacle> obstacles, final GraphGeometry geometry) {
        if (!clearsDiscs(candidate, edge, geometry)) {
            return false;
        }
        for (final PathObstacle obstacle : obstacles) {
            if (!clearsPath(candidate, edge, obstacle, geometry)) {
                return false;
            }
        }
        return true;
    }

    private static boolean clearsDiscs(final EdgePath candidate, final ProjectedEdge edge,
            final GraphGeometry geometry) {
        final Set<ProjectedNodeKey> attached = attachedNodes(edge);
        final double limit = EDGE_NODE_CLEARANCE - ROUTE_EPSILON;
        final List<LayoutPoint> samples = candidate.samples();
        for (final Map.Entry<ProjectedNodeKey, NodeGeometry> entry : geometry.nodes().entrySet()) {
            if (attached.contains(entry.getKey())) {
                continue;
            }
            final NodeGeometry node = entry.getValue();
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
            final PathObstacle obstacle, final GraphGeometry geometry) {
        if (properlyCrossSamples(candidate.samples(), obstacle.path.samples())) {
            return false;
        }
        final double limit = EDGE_CLEARANCE - ROUTE_EPSILON;
        final List<Disc> region = fanRegion(candidate, edge, obstacle, geometry);
        if (region.isEmpty()) {
            return polylineDistance(candidate.samples(), obstacle.path.samples()) >= limit;
        }
        return outsideDistance(candidate.samples(), obstacle.path.samples(), region) >= limit;
    }

    /**
     * The spec 6.4 fan region for two paths sharing an endpoint: the union of two discs,
     * centred at the two paths' own attachments at the shared endpoint, with radius
     * {@code endpointClearanceRadius(X) + EDGE_CLEARANCE}. Empty when the paths share no
     * endpoint.
     */
    private static List<Disc> fanRegion(final EdgePath candidate, final ProjectedEdge edge,
            final PathObstacle obstacle, final GraphGeometry geometry) {
        final List<Disc> region = new ArrayList<Disc>();
        addFanDiscs(region, edge.first(), candidate.start(), obstacle, geometry);
        addFanDiscs(region, edge.second(), candidate.end(), obstacle, geometry);
        return region;
    }

    private static void addFanDiscs(final List<Disc> region, final ProjectedEndpointKey endpoint,
            final LayoutPoint attachment, final PathObstacle obstacle, final GraphGeometry geometry) {
        if (!endpoint.equals(obstacle.edge.first()) && !endpoint.equals(obstacle.edge.second())) {
            return;
        }
        final double radius = endpointClearanceRadius(endpoint, geometry) + EDGE_CLEARANCE;
        region.add(new Disc(attachment, radius));
        if (endpoint.equals(obstacle.edge.first())) {
            region.add(new Disc(obstacle.path.start(), radius));
        }
        if (endpoint.equals(obstacle.edge.second())) {
            region.add(new Disc(obstacle.path.end(), radius));
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

    private static int hullPenetrations(final List<Resolved> resolved,
            final Map<ProjectedEdgeKey, EdgePath> byEdge, final GraphGeometry geometry,
            final Map<ProjectedNodeKey, List<EnclosureHullKey>> nodeHulls,
            final Map<EnclosureHullKey, EnclosureHullKey> parents) {
        int penetrations = 0;
        for (final Resolved current : resolved) {
            final EdgePath path = byEdge.get(current.edge.key());
            for (final Map.Entry<EnclosureHullKey, HullGeometry> entry : geometry.hulls().entrySet()) {
                final EnclosureHullKey hullKey = entry.getKey();
                if (containedByHull(current.edge.first(), hullKey, nodeHulls, parents)
                        || containedByHull(current.edge.second(), hullKey, nodeHulls, parents)) {
                    continue;
                }
                if (crossesPolygon(path.samples(), entry.getValue().exactPolygon())) {
                    penetrations++;
                }
            }
        }
        return penetrations;
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

    private static Set<String> crossingPairKeys(final Map<ProjectedEdgeKey, EdgePath> byEdge) {
        final List<ProjectedEdgeKey> keys = new ArrayList<ProjectedEdgeKey>(byEdge.keySet());
        final Set<String> crossings = new TreeSet<String>();
        for (int first = 0; first < keys.size(); first++) {
            for (int second = first + 1; second < keys.size(); second++) {
                if (properlyCross(byEdge.get(keys.get(first)), byEdge.get(keys.get(second)))) {
                    crossings.add(CanonicalKeys.edgePair(keys.get(first), keys.get(second)));
                }
            }
        }
        return crossings;
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
        final int exponent = commonExponent(a, b, c, d);
        return orientationSign(exponent, a, b, c) * orientationSign(exponent, a, b, d) < 0
            && orientationSign(exponent, c, d, a) * orientationSign(exponent, c, d, b) < 0;
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

    /** An already-decided obstacle for the current edge: its edge and its drawn path. */
    private static final class PathObstacle {
        private final ProjectedEdge edge;
        private final EdgePath path;

        private PathObstacle(final ProjectedEdge edge, final EdgePath path) {
            this.edge = edge;
            this.path = path;
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
