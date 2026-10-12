package org.freeplane.plugin.graph.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.freeplane.plugin.graph.geometry.GeometryTextMetrics;
import org.freeplane.plugin.graph.geometry.GraphGeometry;
import org.freeplane.plugin.graph.geometry.HullGeometry;
import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.geometry.LayoutPositions;
import org.freeplane.plugin.graph.geometry.NodeGeometry;
import org.freeplane.plugin.graph.projection.CanonicalKeys;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.EnclosureKey;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.PinProjection;
import org.freeplane.plugin.graph.projection.ProjectedEdge;
import org.freeplane.plugin.graph.projection.ProjectedEdgeKey;
import org.freeplane.plugin.graph.projection.ProjectedEnclosure;
import org.freeplane.plugin.graph.projection.ProjectedEndpointKey;
import org.freeplane.plugin.graph.projection.ProjectedNode;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;

public final class EdgeConflictCorrection {
    static final double EDGE_NODE_CLEARANCE = 3.0;
    static final double DRAWN_BASE_RADIUS = 8.0;
    static final double CONFLICT_CELL_SIZE = 48.0;
    static final double CONFLICT_MIN_CELL_SIZE = 3.0;
    static final int CONFLICT_CELL_CAP = 64;
    static final int CONFLICT_MAX_SUBDIVISION_DEPTH = 4;
    static final int OUTERMOST_CELL_INDEX = Integer.MAX_VALUE;
    static final double ROUTE_EPSILON = 1e-9;

    private static final double OUTERMOST_CELL_INDEX_DOUBLE = OUTERMOST_CELL_INDEX;

    private long indexInsertedItems;
    private long indexPairChecks;
    private int indexSubdivisions;
    private boolean indexBudgetExhausted;

    public EdgeConflictCorrection() {
    }

    public EdgeConflictResult apply(final GraphProjection projection, final LayoutPositions positions,
            final GeometryTextMetrics metrics, final List<PinProjection> pins,
            final Set<ProjectedEndpointKey> visibleEndpoints,
            final BoundarySeparationResult boundaryResult) {
        Objects.requireNonNull(projection, "projection");
        Objects.requireNonNull(positions, "positions");
        Objects.requireNonNull(metrics, "metrics");
        Objects.requireNonNull(pins, "pins");
        Objects.requireNonNull(visibleEndpoints, "visibleEndpoints");
        Objects.requireNonNull(boundaryResult, "boundaryResult");
        final IndexedDetection detection = detect(projection, positions, boundaryResult.geometry(),
            visibleEndpoints);
        final EdgeConflictDiagnostics diagnostics = new EdgeConflictDiagnostics(
            detection.detection().keys().size(),
            detection.detection().crossings(),
            detection.detection().edgeOverNodes(),
            0,
            0,
            detection.detection().keys(),
            Collections.<EdgeConflictDiagnostics.Repair>emptyList(),
            0,
            0,
            false,
            indexBudgetExhausted,
            indexInsertedItems,
            indexPairChecks,
            indexSubdivisions);
        return new EdgeConflictResult(positions, diagnostics, new EdgeConflictTimings(0L, 0L, 0L, 0L),
            boundaryResult.diagnostics(), boundaryResult.nodeResidualViolations(),
            Collections.<String, LayoutPoint>emptyMap());
    }

    static Detection detectConflicts(final GraphProjection projection, final LayoutPositions positions,
            final GraphGeometry geometry, final Set<ProjectedEndpointKey> visibleEndpoints) {
        return detectConflicts(projection, positions, geometry, visibleEndpoints, new ConflictIndex());
    }

    IndexedDetection detect(final GraphProjection projection, final LayoutPositions positions,
            final GraphGeometry geometry, final Set<ProjectedEndpointKey> visibleEndpoints) {
        final ConflictIndex index = new ConflictIndex();
        final Detection detection = detectConflicts(projection, positions, geometry, visibleEndpoints, index);
        indexInsertedItems += index.insertedItems;
        indexPairChecks += index.pairChecks;
        indexSubdivisions += index.subdivisions;
        indexBudgetExhausted |= index.budgetExhausted;
        return new IndexedDetection(detection, index.insertedItems, index.pairChecks, index.subdivisions,
            index.budgetExhausted);
    }

    private static Detection detectConflicts(final GraphProjection projection, final LayoutPositions positions,
            final GraphGeometry geometry, final Set<ProjectedEndpointKey> visibleEndpoints,
            final ConflictIndex index) {
        Objects.requireNonNull(projection, "projection");
        Objects.requireNonNull(positions, "positions");
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(visibleEndpoints, "visibleEndpoints");
        final List<Chord> chords = chords(projection, positions, geometry, visibleEndpoints);
        if (chords.isEmpty()) {
            return Detection.empty();
        }
        final List<Disc> discs = discs(projection, geometry);
        final double originX = originX(chords, discs);
        final double originY = originY(chords, discs);
        int level = 0;
        IndexPass pass = collect(level, chords, discs, originX, originY);
        while (hasOverCap(pass) && level < CONFLICT_MAX_SUBDIVISION_DEPTH) {
            index.subdivisions += overCapCount(pass);
            level++;
            pass = collect(level, chords, discs, originX, originY);
        }
        if (hasOverCap(pass)) {
            index.budgetExhausted = true;
        }
        if (!pass.outermost.isEmpty()) {
            index.budgetExhausted = true;
        }
        index.insertedItems += pass.insertedItems;
        final DetectionAccumulator accumulator = new DetectionAccumulator(index);
        for (final List<Primitive> bucket : pass.cells.values()) {
            for (int first = 0; first < bucket.size(); first++) {
                for (int second = first + 1; second < bucket.size(); second++) {
                    accumulator.examine(bucket.get(first), bucket.get(second));
                }
            }
        }
        for (final Primitive primitive : pass.outermost) {
            if (primitive.chord != null) {
                for (final Chord other : chords) {
                    if (other != primitive.chord) {
                        index.insertedItems++;
                        accumulator.examineChords(primitive.chord, other);
                    }
                }
                for (final Disc disc : discs) {
                    index.insertedItems++;
                    accumulator.examineChordDisc(primitive.chord, disc);
                }
            }
            else {
                for (final Chord chord : chords) {
                    index.insertedItems++;
                    accumulator.examineChordDisc(chord, primitive.disc);
                }
            }
        }
        return new Detection(new ArrayList<String>(accumulator.keys), accumulator.crossings,
            accumulator.edgeOverNodes);
    }

    private static List<Chord> chords(final GraphProjection projection, final LayoutPositions positions,
            final GraphGeometry geometry, final Set<ProjectedEndpointKey> visibleEndpoints) {
        final Map<EnclosureKey, EnclosureHullKey> hullKeys = enclosureHullKeys(projection, geometry);
        final List<ProjectedEdge> ordered = new ArrayList<ProjectedEdge>(projection.edges());
        Collections.sort(ordered, new Comparator<ProjectedEdge>() {
            @Override
            public int compare(final ProjectedEdge first, final ProjectedEdge second) {
                return CanonicalKeys.edge(first.key()).compareTo(CanonicalKeys.edge(second.key()));
            }
        });
        final List<Chord> result = new ArrayList<Chord>();
        for (final ProjectedEdge edge : ordered) {
            if (!visibleEndpoints.contains(edge.first()) || !visibleEndpoints.contains(edge.second())) {
                continue;
            }
            final LayoutPoint firstAnchor = anchor(edge.first(), hullKeys, positions, geometry);
            final LayoutPoint secondAnchor = anchor(edge.second(), hullKeys, positions, geometry);
            if (firstAnchor == null || secondAnchor == null) {
                continue;
            }
            try {
                final LayoutPoint first = geometry.edgeAttachment(edge.first(), secondAnchor);
                final LayoutPoint second = geometry.edgeAttachment(edge.second(), firstAnchor);
                if (isFinite(first) && isFinite(second)) {
                    result.add(new Chord(edge.key(), first, second, nodeKey(edge.first()),
                        nodeKey(edge.second())));
                }
            }
            catch (final IllegalArgumentException exception) {
                // A partially resolved endpoint has no drawable attachment.
            }
        }
        return result;
    }

    private static List<Disc> discs(final GraphProjection projection, final GraphGeometry geometry) {
        final List<ProjectedNode> ordered = new ArrayList<ProjectedNode>(projection.nodes());
        Collections.sort(ordered, new Comparator<ProjectedNode>() {
            @Override
            public int compare(final ProjectedNode first, final ProjectedNode second) {
                return CanonicalKeys.node(first.key()).compareTo(CanonicalKeys.node(second.key()));
            }
        });
        final List<Disc> result = new ArrayList<Disc>();
        for (final ProjectedNode node : ordered) {
            final NodeGeometry nodeGeometry = geometry.nodes().get(node.key());
            if (nodeGeometry != null) {
                result.add(new Disc(node.key(), nodeGeometry.center(), nodeGeometry.radius()));
            }
        }
        return result;
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

    private static ProjectedNodeKey nodeKey(final ProjectedEndpointKey endpoint) {
        return endpoint.isNode() ? endpoint.node().get() : null;
    }

    private static double originX(final List<Chord> chords, final List<Disc> discs) {
        double minimum = Double.POSITIVE_INFINITY;
        for (final Chord chord : chords) {
            minimum = Math.min(minimum, Math.min(chord.first.x(), chord.second.x()));
        }
        for (final Disc disc : discs) {
            minimum = Math.min(minimum, disc.center.x() - disc.radius - EDGE_NODE_CLEARANCE);
        }
        return minimum;
    }

    private static double originY(final List<Chord> chords, final List<Disc> discs) {
        double minimum = Double.POSITIVE_INFINITY;
        for (final Chord chord : chords) {
            minimum = Math.min(minimum, Math.min(chord.first.y(), chord.second.y()));
        }
        for (final Disc disc : discs) {
            minimum = Math.min(minimum, disc.center.y() - disc.radius - EDGE_NODE_CLEARANCE);
        }
        return minimum;
    }

    private static IndexPass collect(final int level, final List<Chord> chords, final List<Disc> discs,
            final double originX, final double originY) {
        final double cellSize = CONFLICT_CELL_SIZE / Math.scalb(1.0, level);
        final IndexPass pass = new IndexPass();
        for (final Chord chord : chords) {
            insertChord(pass, chord, originX, originY, cellSize);
        }
        for (final Disc disc : discs) {
            insertDisc(pass, disc, originX, originY, cellSize);
        }
        return pass;
    }

    private static void insertChord(final IndexPass pass, final Chord chord, final double originX,
            final double originY, final double cellSize) {
        final int firstX = cellIndex(chord.first.x(), originX, cellSize);
        final int firstY = cellIndex(chord.first.y(), originY, cellSize);
        final int secondX = cellIndex(chord.second.x(), originX, cellSize);
        final int secondY = cellIndex(chord.second.y(), originY, cellSize);
        if (firstX == OUTERMOST_CELL_INDEX || firstY == OUTERMOST_CELL_INDEX
                || secondX == OUTERMOST_CELL_INDEX || secondY == OUTERMOST_CELL_INDEX) {
            pass.outermost.add(new Primitive(chord));
            return;
        }
        final Primitive primitive = new Primitive(chord);
        final double deltaX = chord.second.x() - chord.first.x();
        final double deltaY = chord.second.y() - chord.first.y();
        final int stepX = deltaX > 0.0 ? 1 : deltaX < 0.0 ? -1 : 0;
        final int stepY = deltaY > 0.0 ? 1 : deltaY < 0.0 ? -1 : 0;
        double tMaxX = stepX == 0 ? Double.POSITIVE_INFINITY
            : boundaryTime(chord.first.x(), originX, firstX, stepX, cellSize, deltaX);
        double tMaxY = stepY == 0 ? Double.POSITIVE_INFINITY
            : boundaryTime(chord.first.y(), originY, firstY, stepY, cellSize, deltaY);
        final double tDeltaX = stepX == 0 ? Double.POSITIVE_INFINITY : cellSize / Math.abs(deltaX);
        final double tDeltaY = stepY == 0 ? Double.POSITIVE_INFINITY : cellSize / Math.abs(deltaY);
        int cellX = firstX;
        int cellY = firstY;
        while (true) {
            addCell(pass, primitive, cellX, cellY);
            if (cellX == secondX && cellY == secondY) {
                return;
            }
            if (tMaxX < tMaxY) {
                cellX += stepX;
                tMaxX += tDeltaX;
            }
            else if (tMaxY < tMaxX) {
                cellY += stepY;
                tMaxY += tDeltaY;
            }
            else {
                cellX += stepX;
                cellY += stepY;
                tMaxX += tDeltaX;
                tMaxY += tDeltaY;
            }
        }
    }

    private static double boundaryTime(final double start, final double origin, final int cell,
            final int step, final double cellSize, final double delta) {
        final double boundary = origin + (double) (step > 0 ? cell + 1 : cell) * cellSize;
        final double distance = step > 0 ? boundary - start : start - boundary;
        return distance / Math.abs(delta);
    }

    private static void insertDisc(final IndexPass pass, final Disc disc, final double originX,
            final double originY, final double cellSize) {
        final double inflation = disc.radius + EDGE_NODE_CLEARANCE;
        final double minX = disc.center.x() - inflation;
        final double maxX = disc.center.x() + inflation;
        final double minY = disc.center.y() - inflation;
        final double maxY = disc.center.y() + inflation;
        final int firstX = cellIndex(minX, originX, cellSize);
        final int firstY = cellIndex(minY, originY, cellSize);
        final int lastX = cellIndex(maxX, originX, cellSize);
        final int lastY = cellIndex(maxY, originY, cellSize);
        if (firstX == OUTERMOST_CELL_INDEX || firstY == OUTERMOST_CELL_INDEX
                || lastX == OUTERMOST_CELL_INDEX || lastY == OUTERMOST_CELL_INDEX) {
            pass.outermost.add(new Primitive(disc));
            return;
        }
        final Primitive primitive = new Primitive(disc);
        for (int cellX = firstX; cellX <= lastX; cellX++) {
            final double cellMinX = originX + (double) cellX * cellSize;
            final double cellMaxX = cellMinX + cellSize;
            for (int cellY = firstY; cellY <= lastY; cellY++) {
                final double cellMinY = originY + (double) cellY * cellSize;
                final double cellMaxY = cellMinY + cellSize;
                if (squareDistance(disc.center, cellMinX, cellMinY, cellMaxX, cellMaxY) <= inflation) {
                    addCell(pass, primitive, cellX, cellY);
                }
            }
        }
    }

    private static double squareDistance(final LayoutPoint center, final double minX, final double minY,
            final double maxX, final double maxY) {
        final double dx = Math.max(0.0, Math.max(minX - center.x(), center.x() - maxX));
        final double dy = Math.max(0.0, Math.max(minY - center.y(), center.y() - maxY));
        return Math.hypot(dx, dy);
    }

    private static void addCell(final IndexPass pass, final Primitive primitive, final int cellX,
            final int cellY) {
        final Long key = Long.valueOf(cellKey(cellX, cellY));
        List<Primitive> bucket = pass.cells.get(key);
        if (bucket == null) {
            bucket = new ArrayList<Primitive>();
            pass.cells.put(key, bucket);
        }
        bucket.add(primitive);
        pass.insertedItems++;
    }

    private static long cellKey(final int cellX, final int cellY) {
        return ((long) cellX << 32) | (cellY & 0xffffffffL);
    }

    private static int cellIndex(final double value, final double origin, final double cellSize) {
        final double quotient = (value - origin) / cellSize;
        if (!(quotient > 0.0)) {
            return 0;
        }
        if (quotient >= OUTERMOST_CELL_INDEX_DOUBLE) {
            return OUTERMOST_CELL_INDEX;
        }
        return (int) Math.floor(quotient);
    }

    private static boolean hasOverCap(final IndexPass pass) {
        for (final List<Primitive> bucket : pass.cells.values()) {
            if (bucket.size() > CONFLICT_CELL_CAP) {
                return true;
            }
        }
        return false;
    }

    private static int overCapCount(final IndexPass pass) {
        int count = 0;
        for (final List<Primitive> bucket : pass.cells.values()) {
            if (bucket.size() > CONFLICT_CELL_CAP) {
                count++;
            }
        }
        return count;
    }

    private static boolean properlyCross(final LayoutPoint a, final LayoutPoint b, final LayoutPoint c,
            final LayoutPoint d) {
        final int exponent = commonExponent(a, b, c, d);
        return orientationSign(exponent, a, b, c) * orientationSign(exponent, a, b, d) < 0
            && orientationSign(exponent, c, d, a) * orientationSign(exponent, c, d, b) < 0;
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

    private static double segmentDistance(final LayoutPoint first, final LayoutPoint second,
            final LayoutPoint point) {
        final double edgeX = second.x() - first.x();
        final double edgeY = second.y() - first.y();
        if (edgeX == 0.0 && edgeY == 0.0) {
            return Math.hypot(point.x() - first.x(), point.y() - first.y());
        }
        final int exponent = Math.max(coordinateExponent(edgeX), coordinateExponent(edgeY));
        final double scaledX = Math.scalb(edgeX, -exponent);
        final double scaledY = Math.scalb(edgeY, -exponent);
        final double towardX = Math.scalb(point.x() - first.x(), -exponent);
        final double towardY = Math.scalb(point.y() - first.y(), -exponent);
        final double lengthSquared = scaledX * scaledX + scaledY * scaledY;
        if (!(lengthSquared > 0.0) || !Double.isFinite(lengthSquared)) {
            return Math.hypot(point.x() - first.x(), point.y() - first.y());
        }
        final double projection = (towardX * scaledX + towardY * scaledY) / lengthSquared;
        if (projection <= 0.0) {
            return Math.hypot(point.x() - first.x(), point.y() - first.y());
        }
        if (projection >= 1.0) {
            return Math.hypot(point.x() - second.x(), point.y() - second.y());
        }
        final double cross = scaledX * towardY - scaledY * towardX;
        return Math.scalb(Math.abs(cross) / Math.hypot(scaledX, scaledY), exponent);
    }

    private static boolean isFinite(final LayoutPoint point) {
        return point != null && Double.isFinite(point.x()) && Double.isFinite(point.y());
    }

    static final class Detection {
        private static final Detection EMPTY = new Detection(Collections.<String>emptyList(), 0, 0);

        private final List<String> keys;
        private final int crossings;
        private final int edgeOverNodes;

        private Detection(final List<String> keys, final int crossings, final int edgeOverNodes) {
            this.keys = Collections.unmodifiableList(new ArrayList<String>(keys));
            this.crossings = crossings;
            this.edgeOverNodes = edgeOverNodes;
        }

        static Detection empty() {
            return EMPTY;
        }

        List<String> keys() {
            return keys;
        }

        int crossings() {
            return crossings;
        }

        int edgeOverNodes() {
            return edgeOverNodes;
        }
    }

    static final class ConflictIndex {
        private long insertedItems;
        private long pairChecks;
        private int subdivisions;
        private boolean budgetExhausted;

        ConflictIndex() {
        }

        long insertedItems() {
            return insertedItems;
        }

        long pairChecks() {
            return pairChecks;
        }

        int subdivisions() {
            return subdivisions;
        }

        boolean budgetExhausted() {
            return budgetExhausted;
        }
    }

    static final class IndexedDetection {
        private final Detection detection;
        private final long insertedItems;
        private final long pairChecks;
        private final int subdivisions;
        private final boolean budgetExhausted;

        private IndexedDetection(final Detection detection, final long insertedItems, final long pairChecks,
                final int subdivisions, final boolean budgetExhausted) {
            this.detection = detection;
            this.insertedItems = insertedItems;
            this.pairChecks = pairChecks;
            this.subdivisions = subdivisions;
            this.budgetExhausted = budgetExhausted;
        }

        Detection detection() {
            return detection;
        }

        long insertedItems() {
            return insertedItems;
        }

        long pairChecks() {
            return pairChecks;
        }

        int subdivisions() {
            return subdivisions;
        }

        boolean budgetExhausted() {
            return budgetExhausted;
        }
    }

    private static final class Chord {
        private final ProjectedEdgeKey key;
        private final LayoutPoint first;
        private final LayoutPoint second;
        private final ProjectedNodeKey firstNode;
        private final ProjectedNodeKey secondNode;

        private Chord(final ProjectedEdgeKey key, final LayoutPoint first, final LayoutPoint second,
                final ProjectedNodeKey firstNode, final ProjectedNodeKey secondNode) {
            this.key = key;
            this.first = first;
            this.second = second;
            this.firstNode = firstNode;
            this.secondNode = secondNode;
        }
    }

    private static final class Disc {
        private final ProjectedNodeKey key;
        private final LayoutPoint center;
        private final double radius;

        private Disc(final ProjectedNodeKey key, final LayoutPoint center, final double radius) {
            this.key = key;
            this.center = center;
            this.radius = radius;
        }
    }

    private static final class Primitive {
        private final Chord chord;
        private final Disc disc;

        private Primitive(final Chord chord) {
            this.chord = chord;
            this.disc = null;
        }

        private Primitive(final Disc disc) {
            this.chord = null;
            this.disc = disc;
        }
    }

    private static final class IndexPass {
        private final Map<Long, List<Primitive>> cells = new TreeMap<Long, List<Primitive>>();
        private final List<Primitive> outermost = new ArrayList<Primitive>();
        private long insertedItems;
    }

    private static final class DetectionAccumulator {
        private final ConflictIndex index;
        private final Set<String> examined = new HashSet<String>();
        private final TreeSet<String> keys = new TreeSet<String>();
        private int crossings;
        private int edgeOverNodes;

        private DetectionAccumulator(final ConflictIndex index) {
            this.index = index;
        }

        private void examine(final Primitive first, final Primitive second) {
            if (first.chord != null && second.chord != null) {
                examineChords(first.chord, second.chord);
            }
            else if (first.chord != null && second.disc != null) {
                examineChordDisc(first.chord, second.disc);
            }
            else if (first.disc != null && second.chord != null) {
                examineChordDisc(second.chord, first.disc);
            }
        }

        private void examineChords(final Chord first, final Chord second) {
            final String key = CanonicalKeys.edgePair(first.key, second.key);
            if (!examined.add(key)) {
                return;
            }
            index.pairChecks++;
            if (properlyCross(first.first, first.second, second.first, second.second) && keys.add(key)) {
                crossings++;
            }
        }

        private void examineChordDisc(final Chord chord, final Disc disc) {
            if (disc.key.equals(chord.firstNode) || disc.key.equals(chord.secondNode)) {
                return;
            }
            final String key = CanonicalKeys.edgeNodePair(chord.key, disc.key);
            if (!examined.add(key)) {
                return;
            }
            index.pairChecks++;
            if (segmentDistance(chord.first, chord.second, disc.center) - disc.radius
                    < EDGE_NODE_CLEARANCE - ROUTE_EPSILON && keys.add(key)) {
                edgeOverNodes++;
            }
        }
    }
}
