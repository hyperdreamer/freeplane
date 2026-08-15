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
        final LayoutPoint origin = polygon.get(0);
        final List<TaggedPoint> relative = new ArrayList<TaggedPoint>();
        for (final LayoutPoint point : polygon) {
            relative.add(new TaggedPoint(TaggedSum.difference(point.x(), origin.x()),
                TaggedSum.difference(point.y(), origin.y())));
        }
        final TaggedSum twiceArea = new TaggedSum();
        final TaggedSum firstMomentX = new TaggedSum();
        final TaggedSum firstMomentY = new TaggedSum();
        for (int index = 0; index < relative.size(); index++) {
            final TaggedPoint first = relative.get(index);
            final TaggedPoint second = relative.get((index + 1) % relative.size());
            final TaggedSum cross = TaggedSum.subtract(
                TaggedSum.multiply(first.x, second.y), TaggedSum.multiply(second.x, first.y));
            twiceArea.add(cross);
            final TaggedSum xSum = TaggedSum.added(first.x, second.x);
            final TaggedSum ySum = TaggedSum.added(first.y, second.y);
            firstMomentX.add(TaggedSum.multiply(xSum, cross));
            firstMomentY.add(TaggedSum.multiply(ySum, cross));
        }
        final TaggedSum denominator = TaggedSum.multiply(twiceArea, TaggedSum.of(3.0));
        final double centroidX = TaggedSum.divideAndRound(origin.x(), firstMomentX, denominator);
        final double centroidY = TaggedSum.divideAndRound(origin.y(), firstMomentY, denominator);
        return LayoutPoint.of(centroidX, centroidY);
    }

    private static final class TaggedPoint {
        private final TaggedSum x;
        private final TaggedSum y;

        private TaggedPoint(final TaggedSum x, final TaggedSum y) {
            this.x = x;
            this.y = y;
        }
    }

    private static final class TaggedTerm {
        private final double value;
        private final int exponent;

        private TaggedTerm(final double value, final int exponent) {
            this.value = value;
            this.exponent = exponent;
        }
    }

    private static final class ScaledPair {
        private final double sum;
        private final double error;
        private final int exponent;

        private ScaledPair(final double sum, final double error, final int exponent) {
            this.sum = sum;
            this.error = error;
            this.exponent = exponent;
        }
    }

    private static final class TaggedSum {
        private static final double SPLITTER = 134217729.0;
        private double[] values = new double[16];
        private int[] exponents = new int[16];
        private double[] scratchValues = new double[16];
        private int[] scratchExponents = new int[16];
        private int size;

        private static TaggedSum of(final double value) {
            final TaggedSum result = new TaggedSum();
            result.addDouble(value);
            return result;
        }

        private static TaggedSum difference(final double value, final double origin) {
            final TaggedSum result = new TaggedSum();
            result.addDouble(value);
            result.addDouble(-origin);
            return result;
        }

        private static TaggedSum added(final TaggedSum first, final TaggedSum second) {
            final TaggedSum result = first.copy();
            result.add(second);
            return result;
        }

        private static TaggedSum subtract(final TaggedSum first, final TaggedSum second) {
            final TaggedSum result = first.copy();
            result.add(second, -1.0);
            return result;
        }

        private static TaggedSum multiply(final TaggedSum first, final TaggedSum second) {
            final TaggedSum result = new TaggedSum();
            for (int firstIndex = 0; firstIndex < first.size; firstIndex++) {
                for (int secondIndex = 0; secondIndex < second.size; secondIndex++) {
                    addProduct(result, first.values[firstIndex], first.exponents[firstIndex],
                        second.values[secondIndex], second.exponents[secondIndex]);
                }
            }
            return result;
        }

        private static double divideAndRound(final double origin, final TaggedSum numerator,
                final TaggedSum denominator) {
            final TaggedSum result = new TaggedSum();
            TaggedSum remainder = numerator.copy();
            final TaggedTerm denominatorEstimate = denominator.normalized();
            if (denominatorEstimate == null) {
                throw new IllegalArgumentException("Zero polygon area");
            }
            final TaggedSum absoluteDenominator = denominator.sign() < 0
                ? denominator.negated() : denominator.copy();
            while (true) {
                final double candidate = addAndRound(origin, result);
                final int direction = roundingDirection(origin, candidate, numerator, denominator,
                    absoluteDenominator);
                if (direction == 0) {
                    return candidate;
                }
                final TaggedTerm remainderEstimate = remainder.normalized();
                if (remainderEstimate == null) {
                    return candidate;
                }
                final double quotientValue = remainderEstimate.value / denominatorEstimate.value;
                final int quotientExponent = remainderEstimate.exponent - denominatorEstimate.exponent;
                final TaggedTerm quotient = normalize(quotientValue, quotientExponent);
                if (quotient == null) {
                    return candidate;
                }
                result.addTerm(quotient.value, quotient.exponent);
                final TaggedSum product = multiplyByTerm(denominator, quotient.value, quotient.exponent);
                remainder = subtract(remainder, product);
            }
        }

        private static int roundingDirection(final double origin, final double candidate,
                final TaggedSum numerator, final TaggedSum denominator,
                final TaggedSum absoluteDenominator) {
            final TaggedSum offset = difference(candidate, origin);
            final TaggedSum residual = subtract(numerator, multiply(denominator, offset));
            final int residualSign = residual.sign();
            if (residualSign == 0) {
                return 0;
            }
            final int direction = residualSign * denominator.sign();
            final double neighbor = direction > 0 ? Math.nextUp(candidate) : Math.nextDown(candidate);
            final TaggedSum halfGap = difference(neighbor, candidate).scaled(0.5);
            final TaggedSum threshold = multiply(absoluteDenominator, halfGap);
            final int comparison = residual.compareMagnitude(threshold);
            // Preserve the even candidate when the represented leading terms are identical;
            // lower terms still decide cases whose leading terms differ.
            final int residualLast = residual.size - 1;
            final int thresholdLast = threshold.size - 1;
            final boolean sameLeadingMagnitude = residual.exponents[residualLast] == threshold.exponents[thresholdLast]
                && Double.compare(Math.abs(residual.values[residualLast]),
                    Math.abs(threshold.values[thresholdLast])) == 0;
            final boolean evenCandidate = (Double.doubleToRawLongBits(candidate) & 1L) == 0L;
            if (comparison < 0
                    || (evenCandidate && (comparison == 0
                        || (comparison > 0 && sameLeadingMagnitude)))) {
                return 0;
            }
            return direction;
        }

        private static TaggedSum multiplyByTerm(final TaggedSum sum, final double value,
                final int exponent) {
            final TaggedSum result = new TaggedSum();
            for (int index = 0; index < sum.size; index++) {
                addProduct(result, sum.values[index], sum.exponents[index], value, exponent);
            }
            return result;
        }

        private static double addAndRound(final double origin, final TaggedSum offset) {
            final TaggedSum result = of(origin);
            result.add(offset);
            return result.roundedDouble();
        }

        private static void addProduct(final TaggedSum result, final double first, final int firstExponent,
                final double second, final int secondExponent) {
            final double product = first * second;
            final double firstSplit = SPLITTER * first;
            final double firstHigh = firstSplit - (firstSplit - first);
            final double firstLow = first - firstHigh;
            final double secondSplit = SPLITTER * second;
            final double secondHigh = secondSplit - (secondSplit - second);
            final double secondLow = second - secondHigh;
            final double error = ((firstHigh * secondHigh - product) + firstHigh * secondLow
                + firstLow * secondHigh) + firstLow * secondLow;
            final int exponent = firstExponent + secondExponent;
            result.addTerm(product, exponent);
            result.addTerm(error, exponent);
        }

        private void add(final TaggedSum other) {
            add(other, 1.0);
        }

        private void add(final TaggedSum other, final double sign) {
            for (int index = 0; index < other.size; index++) {
                addTerm(sign * other.values[index], other.exponents[index]);
            }
        }

        private void addDouble(final double value) {
            if (value == 0.0) {
                return;
            }
            final long bits = Double.doubleToRawLongBits(value);
            final long fraction = bits & 0x000fffffffffffffL;
            final int biasedExponent = (int) ((bits >>> 52) & 0x7ffL);
            final long significand;
            final int exponent;
            if (biasedExponent == 0) {
                significand = fraction;
                exponent = -1074;
            }
            else {
                significand = fraction | 0x0010000000000000L;
                exponent = biasedExponent - 1023 - 52;
            }
            addTerm((bits < 0 ? -1.0 : 1.0) * (double) significand, exponent);
        }

        private void addTerm(final double value, final int exponent) {
            final TaggedTerm term = normalize(value, exponent);
            if (term == null) {
                return;
            }
            ensureCapacity(size + 1);
            int outputSize = 0;
            TaggedTerm carry = term;
            for (int index = 0; index < size; index++) {
                final TaggedTerm current = new TaggedTerm(values[index], exponents[index]);
                if (carry == null) {
                    scratchValues[outputSize] = current.value;
                    scratchExponents[outputSize++] = current.exponent;
                }
                else if (Math.abs(carry.exponent - current.exponent) > 1073) {
                    if (carry.exponent < current.exponent) {
                        scratchValues[outputSize] = carry.value;
                        scratchExponents[outputSize++] = carry.exponent;
                        carry = current;
                    }
                    else {
                        scratchValues[outputSize] = current.value;
                        scratchExponents[outputSize++] = current.exponent;
                    }
                }
                else {
                    final ScaledPair pair = twoSum(carry.value, carry.exponent,
                        current.value, current.exponent);
                    final TaggedTerm error = normalize(pair.error, pair.exponent);
                    if (error != null) {
                        scratchValues[outputSize] = error.value;
                        scratchExponents[outputSize++] = error.exponent;
                    }
                    carry = normalize(pair.sum, pair.exponent);
                }
            }
            if (carry != null) {
                scratchValues[outputSize] = carry.value;
                scratchExponents[outputSize++] = carry.exponent;
            }
            size = outputSize;
            final double[] oldValues = values;
            values = scratchValues;
            scratchValues = oldValues;
            final int[] oldExponents = exponents;
            exponents = scratchExponents;
            scratchExponents = oldExponents;
            sortByExponent();
        }

        private void ensureCapacity(final int required) {
            if (required <= values.length) {
                return;
            }
            int capacity = values.length * 2;
            while (capacity < required) {
                capacity *= 2;
            }
            final double[] newValues = new double[capacity];
            final int[] newExponents = new int[capacity];
            final double[] newScratchValues = new double[capacity];
            final int[] newScratchExponents = new int[capacity];
            for (int index = 0; index < size; index++) {
                newValues[index] = values[index];
                newExponents[index] = exponents[index];
            }
            values = newValues;
            exponents = newExponents;
            scratchValues = newScratchValues;
            scratchExponents = newScratchExponents;
        }

        private void sortByExponent() {
            for (int index = 1; index < size; index++) {
                final double value = values[index];
                final int exponent = exponents[index];
                int insertion = index;
                while (insertion > 0 && exponents[insertion - 1] > exponent) {
                    values[insertion] = values[insertion - 1];
                    exponents[insertion] = exponents[insertion - 1];
                    insertion--;
                }
                values[insertion] = value;
                exponents[insertion] = exponent;
            }
        }

        private TaggedSum copy() {
            final TaggedSum result = new TaggedSum();
            result.ensureCapacity(size);
            for (int index = 0; index < size; index++) {
                result.values[index] = values[index];
                result.exponents[index] = exponents[index];
            }
            result.size = size;
            return result;
        }

        private TaggedSum negated() {
            final TaggedSum result = copy();
            for (int index = 0; index < result.size; index++) {
                result.values[index] = -result.values[index];
            }
            return result;
        }

        private int sign() {
            final TaggedTerm term = normalized();
            return term == null ? 0 : (term.value < 0.0 ? -1 : 1);
        }

        private int compareMagnitude(final TaggedSum other) {
            final TaggedSum left = sign() < 0 ? negated() : copy();
            final TaggedSum right = other.sign() < 0 ? other.negated() : other.copy();
            return subtract(left, right).sign();
        }

        private double roundedDouble() {
            double candidate = approximateDouble();
            while (true) {
                final TaggedSum residual = subtract(this, of(candidate));
                final int residualSign = residual.sign();
                if (residualSign == 0) {
                    return candidate;
                }
                final double neighbor = residualSign > 0 ? Math.nextUp(candidate) : Math.nextDown(candidate);
                final TaggedSum gap = residualSign > 0
                    ? difference(neighbor, candidate) : difference(candidate, neighbor);
                final int comparison = residual.compareMagnitude(gap.scaled(0.5));
                if (comparison < 0 || (comparison == 0 && (Double.doubleToRawLongBits(candidate) & 1L) == 0L)) {
                    return candidate;
                }
                candidate = neighbor;
            }
        }

        private TaggedSum scaled(final double factor) {
            final TaggedSum result = new TaggedSum();
            for (int index = 0; index < size; index++) {
                result.addTerm(values[index] * factor, exponents[index]);
            }
            return result;
        }

        private double approximateDouble() {
            final TaggedTerm term = normalized();
            if (term == null) {
                return 0.0;
            }
            return Math.scalb(term.value, term.exponent);
        }

        private TaggedTerm normalized() {
            if (size == 0) {
                return null;
            }
            final int largestExponent = exponents[size - 1];
            double sum = 0.0;
            double compensation = 0.0;
            for (int index = size - 1; index >= 0; index--) {
                final int difference = exponents[index] - largestExponent;
                if (difference < -1074) {
                    continue;
                }
                final double term = Math.scalb(values[index], difference);
                final double corrected = term - compensation;
                final double next = sum + corrected;
                compensation = (next - sum) - corrected;
                sum = next;
            }
            final double estimate = sum - compensation;
            return normalize(estimate, largestExponent);
        }

        private static TaggedTerm normalize(final double value, final int exponent) {
            if (value == 0.0) {
                return null;
            }
            final double absolute = Math.abs(value);
            final int floorExponent = Math.getExponent(absolute);
            if (floorExponent == -1023) {
                final long fraction = Double.doubleToRawLongBits(absolute) & 0x000fffffffffffffL;
                final int highestBit = 63 - Long.numberOfLeadingZeros(fraction);
                return new TaggedTerm(Math.scalb(value, 1073 - highestBit),
                    exponent - 1073 + highestBit);
            }
            return new TaggedTerm(Math.scalb(value, -floorExponent - 1), exponent + floorExponent + 1);
        }

        private static ScaledPair twoSum(final double first, final int firstExponent,
                final double second, final int secondExponent) {
            final int exponent = Math.max(firstExponent, secondExponent);
            final double scaledFirst = Math.scalb(first, firstExponent - exponent);
            final double scaledSecond = Math.scalb(second, secondExponent - exponent);
            final double sum = scaledFirst + scaledSecond;
            final double secondVirtual = sum - scaledFirst;
            final double firstVirtual = sum - secondVirtual;
            final double secondRoundoff = scaledSecond - secondVirtual;
            final double firstRoundoff = scaledFirst - firstVirtual;
            return new ScaledPair(sum, firstRoundoff + secondRoundoff, exponent);
        }
    }
}
