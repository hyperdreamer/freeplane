package org.freeplane.plugin.graph.geometry;

import java.awt.Shape;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class HullGeometry {
    private static final double EPSILON = 1e-9;
    private static final double CORNER_SMOOTHING_TANGENT = 4.0;

    private final List<LayoutPoint> exactPolygon;
    private final LayoutPoint labelAnchor;
    private final Path2D.Double smoothPath;
    private final double minX;
    private final double minY;
    private final double maxX;
    private final double maxY;

    private HullGeometry(final List<LayoutPoint> exactPolygon, final LayoutPoint labelAnchor) {
        this.exactPolygon = canonicalize(exactPolygon);
        this.labelAnchor = Objects.requireNonNull(labelAnchor, "labelAnchor");
        double minimumX = Double.POSITIVE_INFINITY;
        double minimumY = Double.POSITIVE_INFINITY;
        double maximumX = Double.NEGATIVE_INFINITY;
        double maximumY = Double.NEGATIVE_INFINITY;
        for (final LayoutPoint point : this.exactPolygon) {
            minimumX = Math.min(minimumX, point.x());
            minimumY = Math.min(minimumY, point.y());
            maximumX = Math.max(maximumX, point.x());
            maximumY = Math.max(maximumY, point.y());
        }
        this.minX = minimumX;
        this.minY = minimumY;
        this.maxX = maximumX;
        this.maxY = maximumY;
        this.smoothPath = buildSmoothPath(this.exactPolygon);
    }

    public static HullGeometry of(final List<LayoutPoint> exactPolygon, final LayoutPoint labelAnchor) {
        return new HullGeometry(exactPolygon, labelAnchor);
    }

    public List<LayoutPoint> exactPolygon() {
        return exactPolygon;
    }

    public LayoutPoint labelAnchor() {
        return labelAnchor;
    }

    public Shape smoothPath() {
        return (Path2D.Double) smoothPath.clone();
    }

    public double minX() {
        return minX;
    }

    public double minY() {
        return minY;
    }

    public double maxX() {
        return maxX;
    }

    public double maxY() {
        return maxY;
    }

    public boolean contains(final LayoutPoint point) {
        Objects.requireNonNull(point, "point");
        final int count = exactPolygon.size();
        for (int index = 0; index < count; index++) {
            final LayoutPoint start = exactPolygon.get(index);
            final LayoutPoint end = exactPolygon.get((index + 1) % count);
            if (classifyOrientation(start, end, point) < 0) {
                return false;
            }
        }
        return true;
    }

    public LayoutPoint nearestBoundaryPoint(final LayoutPoint toward) {
        Objects.requireNonNull(toward, "toward");
        LayoutPoint bestPoint = null;
        final int count = exactPolygon.size();
        for (int index = 0; index < count; index++) {
            final LayoutPoint start = exactPolygon.get(index);
            final LayoutPoint end = exactPolygon.get((index + 1) % count);
            final LayoutPoint candidate = nearestPointOnSegment(start, end, toward);
            if (bestPoint == null || improvesSquaredDistance(candidate, bestPoint, toward)) {
                bestPoint = candidate;
            }
        }
        return bestPoint;
    }

    private static LayoutPoint nearestPointOnSegment(final LayoutPoint start, final LayoutPoint end,
            final LayoutPoint toward) {
        final ScaledExpansion edgeX = ScaledExpansion.difference(end.x(), start.x());
        final ScaledExpansion edgeY = ScaledExpansion.difference(end.y(), start.y());
        final ScaledExpansion lengthSquared = edgeX.product(edgeX).add(edgeY.product(edgeY));
        if (lengthSquared.signum() <= 0) {
            return start;
        }
        final ScaledExpansion towardX = ScaledExpansion.difference(toward.x(), start.x());
        final ScaledExpansion towardY = ScaledExpansion.difference(toward.y(), start.y());
        final ScaledExpansion projection = towardX.product(edgeX).add(towardY.product(edgeY));
        if (projection.signum() <= 0) {
            return start;
        }
        if (projection.compareTo(lengthSquared) >= 0) {
            return end;
        }
        final double px = projectedCoordinate(start.x(), edgeX, projection, lengthSquared);
        final double py = projectedCoordinate(start.y(), edgeY, projection, lengthSquared);
        if (!Double.isFinite(px) || !Double.isFinite(py)) {
            return start;
        }
        return LayoutPoint.of(px, py);
    }

    private static double projectedCoordinate(final double start, final ScaledExpansion edge,
            final ScaledExpansion projection, final ScaledExpansion lengthSquared) {
        // Keep cancellation in start + edge * (projection / lengthSquared) until final rounding.
        return ScaledExpansion.of(start).product(lengthSquared).add(edge.product(projection))
            .quotient(lengthSquared).finiteValue();
    }

    private static boolean improvesSquaredDistance(final LayoutPoint candidate, final LayoutPoint best,
            final LayoutPoint toward) {
        final ScaledMagnitude candidateDistance = ScaledMagnitude.between(candidate, toward);
        final ScaledMagnitude bestDistance = ScaledMagnitude.between(best, toward);
        return candidateDistance.improves(bestDistance);
    }

    private static final class ScaledMagnitude {
        private final ScaledExpansion squared;

        private ScaledMagnitude(final ScaledExpansion squared) {
            this.squared = squared;
        }

        static ScaledMagnitude between(final LayoutPoint first, final LayoutPoint second) {
            final ScaledExpansion dx = ScaledExpansion.difference(first.x(), second.x());
            final ScaledExpansion dy = ScaledExpansion.difference(first.y(), second.y());
            return new ScaledMagnitude(dx.product(dx).add(dy.product(dy)));
        }

        boolean improves(final ScaledMagnitude best) {
            return best.squared.subtract(squared).compareTo(ScaledExpansion.of(EPSILON)) > 0;
        }
    }

    private static final class ScaledExpansion {
        private double[] components;
        private int[] exponents;
        private int count;

        private ScaledExpansion() {
            components = new double[32];
            exponents = new int[32];
        }

        static ScaledExpansion of(final double value) {
            final ScaledExpansion expansion = new ScaledExpansion();
            expansion.addComponent(value, 0);
            return expansion;
        }

        static ScaledExpansion difference(final double first, final double second) {
            return of(first).add(of(-second));
        }

        private static ScaledExpansion scaled(final double value, final int exponent) {
            final ScaledExpansion expansion = new ScaledExpansion();
            expansion.addComponent(value, exponent);
            return expansion;
        }

        ScaledExpansion add(final ScaledExpansion other) {
            final ScaledExpansion sum = copy();
            for (int index = 0; index < other.count; index++) {
                sum.addComponent(other.components[index], other.exponents[index]);
            }
            return sum;
        }

        ScaledExpansion subtract(final ScaledExpansion other) {
            final ScaledExpansion difference = copy();
            for (int index = 0; index < other.count; index++) {
                difference.addComponent(-other.components[index], other.exponents[index]);
            }
            return difference;
        }

        ScaledExpansion product(final ScaledExpansion other) {
            final ScaledExpansion product = new ScaledExpansion();
            for (int first = 0; first < count; first++) {
                for (int second = 0; second < other.count; second++) {
                    final double[] factors = twoProduct(components[first], other.components[second]);
                    final int exponent = exponents[first] + other.exponents[second];
                    product.addComponent(factors[0], exponent);
                    product.addComponent(factors[1], exponent);
                }
            }
            return product;
        }

        ScaledExpansion quotient(final ScaledExpansion denominator) {
            final ScaledExpansion quotient = new ScaledExpansion();
            for (int correction = 0; correction < 4; correction++) {
                final ScaledExpansion remainder = subtract(denominator.product(quotient));
                if (remainder.count == 0) {
                    break;
                }
                final int exponent = remainder.exponents[0] - denominator.exponents[0];
                final double factor = remainder.scaledAtLeadingExponent()
                    / denominator.scaledAtLeadingExponent();
                quotient.addComponent(factor, exponent);
            }
            return quotient;
        }

        ScaledExpansion inverseSquareRoot() {
            final int scale = Math.floorDiv(exponents[0], 2);
            final ScaledExpansion inverse = scaled(1.0 / Math.sqrt(scaledAtExponent(2 * scale)), -scale);
            final ScaledExpansion adjustment = ScaledExpansion.of(3.0)
                .subtract(product(inverse.product(inverse)));
            return inverse.product(adjustment).product(ScaledExpansion.of(0.5));
        }

        int signum() {
            return count == 0 ? 0 : components[0] > 0.0 ? 1 : -1;
        }

        int compareTo(final ScaledExpansion other) {
            return subtract(other).signum();
        }

        double finiteValue() {
            if (count == 0) {
                return 0.0;
            }
            if (exponents[0] > Double.MAX_EXPONENT) {
                return Math.copySign(Double.POSITIVE_INFINITY, components[0]);
            }
            if (exponents[0] <= -1022) {
                return subnormalValue();
            }
            final double value = Math.scalb(components[0], exponents[0]);
            return roundWithTail(value, tailInUnits(roundingScaleExponent(value)), roundingScaleExponent(value));
        }

        private ScaledExpansion copy() {
            final ScaledExpansion copy = new ScaledExpansion();
            copy.ensureCapacity(count);
            System.arraycopy(components, 0, copy.components, 0, count);
            System.arraycopy(exponents, 0, copy.exponents, 0, count);
            copy.count = count;
            return copy;
        }

        private double scaledAtLeadingExponent() {
            return scaledAtExponent(exponents[0]);
        }

        private double scaledAtExponent(final int targetExponent) {
            double value = 0.0;
            double residual = 0.0;
            for (int index = 0; index < count; index++) {
                final double[] sum = twoSum(value,
                    Math.scalb(components[index], exponents[index] - targetExponent));
                value = sum[0];
                residual += sum[1];
            }
            return value + residual;
        }

        private double subnormalValue() {
            final double leadingInMinimumSubnormals = Math.scalb(components[0], exponents[0] + 1074);
            final double grid = Math.floor(leadingInMinimumSubnormals);
            double units = leadingInMinimumSubnormals - grid;
            for (int index = 1; index < count; index++) {
                units += Math.scalb(components[index], exponents[index] + 1074);
            }
            return roundWithTail(grid * Double.MIN_VALUE, units, -1074);
        }

        private double tailInUnits(final int scale) {
            double units = 0.0;
            for (int index = 1; index < count; index++) {
                units += Math.scalb(components[index], exponents[index] - scale);
            }
            return units;
        }

        private void addComponent(double value, int exponent) {
            while (value != 0.0) {
                if (Math.abs(value) < 0x1.0p-1022) {
                    value = Math.scalb(value, 1074);
                    exponent -= 1074;
                }
                final int scale = Math.getExponent(value);
                if (scale != 0) {
                    value = Math.scalb(value, -scale);
                    exponent += scale;
                }
                int partner = -1;
                for (int index = 0; index < count; index++) {
                    if (Math.abs(exponents[index] - exponent) <= 52) {
                        partner = index;
                        break;
                    }
                }
                if (partner < 0) {
                    insert(value, exponent);
                    return;
                }
                final double partnerValue = components[partner];
                final int partnerExponent = exponents[partner];
                double first = value;
                double second = partnerValue;
                int firstExponent = exponent;
                int secondExponent = partnerExponent;
                if (secondExponent > firstExponent) {
                    first = partnerValue;
                    second = value;
                    firstExponent = partnerExponent;
                    secondExponent = exponent;
                }
                final double[] sum = twoSum(first, Math.scalb(second, secondExponent - firstExponent));
                remove(partner);
                if (sum[0] == 0.0) {
                    value = sum[1];
                    exponent = firstExponent;
                }
                else {
                    addComponent(sum[1], firstExponent);
                    value = sum[0];
                    exponent = firstExponent;
                }
            }
        }

        private void insert(final double value, final int exponent) {
            ensureCapacity(count + 1);
            int position = count;
            while (position > 0 && exponents[position - 1] < exponent) {
                components[position] = components[position - 1];
                exponents[position] = exponents[position - 1];
                position--;
            }
            components[position] = value;
            exponents[position] = exponent;
            count++;
        }

        private void ensureCapacity(final int required) {
            if (required <= components.length) {
                return;
            }
            final int capacity = Math.max(required, components.length * 2);
            final double[] expandedComponents = new double[capacity];
            final int[] expandedExponents = new int[capacity];
            System.arraycopy(components, 0, expandedComponents, 0, count);
            System.arraycopy(exponents, 0, expandedExponents, 0, count);
            components = expandedComponents;
            exponents = expandedExponents;
        }

        private void remove(final int index) {
            for (int position = index; position < count - 1; position++) {
                components[position] = components[position + 1];
                exponents[position] = exponents[position + 1];
            }
            count--;
        }
    }

    private static int roundingScaleExponent(final double value) {
        final int exponent = Math.getExponent(value);
        return exponent >= -1022 ? exponent - 52 : -1074;
    }

    private static double roundWithTail(double value, double units, final int scaleExponent) {
        for (int step = 0; step < 32; step++) {
            if (units == 0.0) {
                return value;
            }
            final boolean upward = units > 0.0;
            final double gap = Math.scalb(1.0, neighborGapExponent(value, upward) - scaleExponent);
            final double halfGap = gap * 0.5;
            if (Math.abs(units) < halfGap || Math.abs(units) == halfGap
                    && (Double.doubleToRawLongBits(value) & 1L) == 0L) {
                return value;
            }
            value = Math.nextAfter(value, upward ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY);
            units += upward ? -gap : gap;
        }
        return value + Math.scalb(units, scaleExponent);
    }

    private static int neighborGapExponent(final double value, final boolean upward) {
        final int exponent = Math.getExponent(value);
        if (exponent < -1022) {
            return -1074;
        }
        final long fraction = Double.doubleToRawLongBits(Math.abs(value)) & 0x000fffffffffffffL;
        final boolean towardZero = upward == (value < 0.0);
        if (towardZero && fraction == 0L && exponent > -1022) {
            return exponent - 53;
        }
        return exponent - 52;
    }

    private static List<LayoutPoint> canonicalize(final List<LayoutPoint> polygon) {
        Objects.requireNonNull(polygon, "exactPolygon");
        final List<LayoutPoint> vertices = new ArrayList<LayoutPoint>(polygon.size());
        for (final LayoutPoint point : polygon) {
            vertices.add(Objects.requireNonNull(point, "exactPolygon entry"));
        }
        if (vertices.size() >= 2 && vertices.get(vertices.size() - 1).equals(vertices.get(0))) {
            vertices.remove(vertices.size() - 1);
        }
        boolean changed;
        do {
            changed = removeDegenerate(vertices);
        }
        while (changed);
        if (vertices.size() < 3) {
            throw new IllegalArgumentException("A hull polygon needs at least three unique vertices");
        }
        boolean hasPositive = false;
        boolean hasNegative = false;
        for (int index = 0; index < vertices.size(); index++) {
            final int orientation = classifyOrientation(vertices.get(index),
                vertices.get((index + 1) % vertices.size()),
                vertices.get((index + 2) % vertices.size()));
            if (orientation > 0) {
                hasPositive = true;
            }
            else if (orientation < 0) {
                hasNegative = true;
            }
        }
        if (hasPositive && hasNegative) {
            throw new IllegalArgumentException("A hull polygon must be strictly convex");
        }
        if (!isSimple(vertices)) {
            throw new IllegalArgumentException("A hull polygon must not be self-intersecting");
        }
        if (hasNegative) {
            Collections.reverse(vertices);
        }
        int start = 0;
        for (int index = 1; index < vertices.size(); index++) {
            if (compare(vertices.get(index), vertices.get(start)) < 0) {
                start = index;
            }
        }
        if (start > 0) {
            Collections.rotate(vertices, -start);
        }
        return Collections.unmodifiableList(vertices);
    }

    private static boolean removeDegenerate(final List<LayoutPoint> vertices) {
        boolean changed = false;
        for (int index = vertices.size() - 1; index >= 0; index--) {
            if (vertices.size() < 3) {
                break;
            }
            final LayoutPoint previous = vertices.get((index + vertices.size() - 1) % vertices.size());
            final LayoutPoint current = vertices.get(index);
            final LayoutPoint next = vertices.get((index + 1) % vertices.size());
            if (current.equals(previous)) {
                vertices.remove(index);
                changed = true;
            }
            else if (classifyOrientation(previous, current, next) == 0) {
                if (!isBetween(previous, current, next)) {
                    throw new IllegalArgumentException("A hull polygon must not backtrack along an edge");
                }
                vertices.remove(index);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean isSimple(final List<LayoutPoint> polygon) {
        final int count = polygon.size();
        for (int first = 0; first < count; first++) {
            final LayoutPoint a = polygon.get(first);
            final LayoutPoint b = polygon.get((first + 1) % count);
            for (int second = first + 1; second < count; second++) {
                if (second == first + 1 || (first == 0 && second == count - 1)) {
                    continue;
                }
                final LayoutPoint c = polygon.get(second);
                final LayoutPoint d = polygon.get((second + 1) % count);
                if (segmentsIntersect(a, b, c, d)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean segmentsIntersect(final LayoutPoint a, final LayoutPoint b,
            final LayoutPoint c, final LayoutPoint d) {
        final int abC = classifyOrientation(a, b, c);
        final int abD = classifyOrientation(a, b, d);
        final int cdA = classifyOrientation(c, d, a);
        final int cdB = classifyOrientation(c, d, b);
        if (abC == 0 || abD == 0 || cdA == 0 || cdB == 0) {
            return (abC == 0 && onSegment(a, b, c)) || (abD == 0 && onSegment(a, b, d))
                || (cdA == 0 && onSegment(c, d, a)) || (cdB == 0 && onSegment(c, d, b));
        }
        return abC * abD < 0 && cdA * cdB < 0;
    }

    private static boolean onSegment(final LayoutPoint a, final LayoutPoint b, final LayoutPoint point) {
        return isBetween(a, point, b);
    }

    private static boolean isBetween(final LayoutPoint first, final LayoutPoint point, final LayoutPoint second) {
        return point.x() <= Math.max(first.x(), second.x()) && point.x() >= Math.min(first.x(), second.x())
            && point.y() <= Math.max(first.y(), second.y()) && point.y() >= Math.min(first.y(), second.y());
    }

    private static Path2D.Double buildSmoothPath(final List<LayoutPoint> polygon) {
        final int count = polygon.size();
        final double[] cuts = new double[count];
        for (int index = 0; index < count; index++) {
            final LayoutPoint previous = polygon.get((index + count - 1) % count);
            final LayoutPoint current = polygon.get(index);
            final LayoutPoint next = polygon.get((index + 1) % count);
            final double inLength = distance(previous, current);
            final double outLength = distance(current, next);
            cuts[index] = Math.min(CORNER_SMOOTHING_TANGENT, Math.min(inLength * 0.5, outLength * 0.5));
        }
        final Path2D.Double path = new Path2D.Double();
        for (int index = 0; index < count; index++) {
            final LayoutPoint current = polygon.get(index);
            final LayoutPoint next = polygon.get((index + 1) % count);
            final LayoutPoint start = pointAlong(current, polygon.get((index + count - 1) % count), cuts[index]);
            final LayoutPoint end = pointAlong(current, next, cuts[index]);
            if (index == 0) {
                path.moveTo(start.x(), start.y());
            }
            else {
                path.lineTo(start.x(), start.y());
            }
            path.quadTo(current.x(), current.y(), end.x(), end.y());
        }
        path.closePath();
        return path;
    }

    private static double distance(final LayoutPoint first, final LayoutPoint second) {
        if (!subtractionIsFinite(second.x(), first.x()) || !subtractionIsFinite(second.y(), first.y())) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.hypot(second.x() - first.x(), second.y() - first.y());
    }

    private static LayoutPoint pointAlong(final LayoutPoint from, final LayoutPoint to, final double distance) {
        if (!(distance > 0.0)) {
            return from;
        }
        final ScaledExpansion edgeX = ScaledExpansion.difference(to.x(), from.x());
        final ScaledExpansion edgeY = ScaledExpansion.difference(to.y(), from.y());
        final ScaledExpansion squaredLength = edgeX.product(edgeX).add(edgeY.product(edgeY));
        if (squaredLength.signum() <= 0) {
            return from;
        }
        final ScaledExpansion cut = ScaledExpansion.of(distance);
        final ScaledExpansion inverseLength = squaredLength.inverseSquareRoot();
        final ScaledExpansion displacementX = edgeX.product(cut).product(inverseLength);
        final ScaledExpansion displacementY = edgeY.product(cut).product(inverseLength);
        final ScaledExpansion displacementSquared = displacementX.product(displacementX)
            .add(displacementY.product(displacementY));
        final ScaledExpansion maximumSquared = ScaledExpansion.of(CORNER_SMOOTHING_TANGENT)
            .product(ScaledExpansion.of(CORNER_SMOOTHING_TANGENT));
        if (displacementSquared.compareTo(maximumSquared) > 0) {
            return from;
        }
        final double x = ScaledExpansion.of(from.x()).add(displacementX).finiteValue();
        final double y = ScaledExpansion.of(from.y()).add(displacementY).finiteValue();
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            return from;
        }
        if (outwardRoundingStepExceedsSmoothingTangent(from.x(), displacementX, x)
                || outwardRoundingStepExceedsSmoothingTangent(from.y(), displacementY, y)) {
            return from;
        }
        return LayoutPoint.of(x, y);
    }

    private static boolean outwardRoundingStepExceedsSmoothingTangent(final double from,
            final ScaledExpansion displacement, final double rounded) {
        final int direction = displacement.signum();
        if (direction == 0) {
            return false;
        }
        final ScaledExpansion unrounded = ScaledExpansion.of(from).add(displacement);
        final ScaledExpansion roundingError = ScaledExpansion.of(rounded).subtract(unrounded);
        if (roundingError.signum() != direction) {
            return false;
        }
        // An outward rounding step crosses the adjacent binary64 coordinate. If
        // that step exceeds four, the published coordinate alone is overlong.
        return neighborGapExponent(from, direction > 0) > Math.getExponent(CORNER_SMOOTHING_TANGENT);
    }

    private static boolean subtractionIsFinite(final double first, final double second) {
        return (first >= 0.0 && second >= 0.0) || (first <= 0.0 && second <= 0.0)
            || Math.abs(first) <= Double.MAX_VALUE - Math.abs(second);
    }

    private static int classifyOrientation(final LayoutPoint previous, final LayoutPoint current,
            final LayoutPoint next) {
        final double[] firstX = scaledDifference(current.x(), previous.x());
        final double[] firstY = scaledDifference(current.y(), previous.y());
        final double[] secondX = scaledDifference(next.x(), current.x());
        final double[] secondY = scaledDifference(next.y(), current.y());
        final double[] terms = new double[17];
        final int[] exponents = new int[17];
        int termCount = 0;
        termCount = addProductTerms(terms, exponents, termCount, firstX, secondY, 1.0);
        termCount = addProductTerms(terms, exponents, termCount, firstY, secondX, -1.0);
        final double[] components = new double[256];
        final int[] componentExponents = new int[256];
        terms[termCount] = -EPSILON;
        exponents[termCount] = 0;
        if (signOfSum(terms, exponents, termCount + 1, components, componentExponents) > 0) {
            return 1;
        }
        terms[termCount] = EPSILON;
        exponents[termCount] = 0;
        if (signOfSum(terms, exponents, termCount + 1, components, componentExponents) < 0) {
            return -1;
        }
        return 0;
    }

    private static double[] scaledDifference(final double first, final double second) {
        final double difference = first - second;
        final double[] exact;
        final double extraScale;
        if (Double.isFinite(difference)) {
            exact = twoDiff(first, second);
            extraScale = 0.0;
        }
        else {
            exact = twoDiff(first * 0.5, second * 0.5);
            extraScale = 1.0;
        }
        final double major = exact[0];
        if (major == 0.0) {
            return new double[] { 0.0, 0.0, 0.0, 0.0 };
        }
        final double minor = exact[1];
        final int majorScale = Math.max(0, Math.getExponent(major) - 508);
        int minorScale = 0;
        if (minor != 0.0) {
            minorScale = Math.max(0, Math.getExponent(minor) - 508);
        }
        return new double[] { Math.scalb(major, -majorScale), majorScale + extraScale,
            Math.scalb(minor, -minorScale), minorScale + extraScale };
    }

    private static int addProductTerms(final double[] terms, final int[] exponents, int termCount,
            final double[] first, final double[] second, final double sign) {
        termCount = addProductTerm(terms, exponents, termCount, first[0], (int) first[1],
            second[0], (int) second[1], sign);
        termCount = addProductTerm(terms, exponents, termCount, first[0], (int) first[1],
            second[2], (int) second[3], sign);
        termCount = addProductTerm(terms, exponents, termCount, first[2], (int) first[3],
            second[0], (int) second[1], sign);
        termCount = addProductTerm(terms, exponents, termCount, first[2], (int) first[3],
            second[2], (int) second[3], sign);
        return termCount;
    }

    private static int addProductTerm(final double[] terms, final int[] exponents, int termCount,
            final double first, final int firstScale, final double second, final int secondScale,
            final double sign) {
        double firstFactor = first;
        int firstFactorScale = firstScale;
        double secondFactor = second;
        int secondFactorScale = secondScale;
        if (Math.abs(firstFactor) < 0x1.0p-500) {
            firstFactor = Math.scalb(firstFactor, 700);
            firstFactorScale -= 700;
        }
        if (Math.abs(secondFactor) < 0x1.0p-500) {
            secondFactor = Math.scalb(secondFactor, 700);
            secondFactorScale -= 700;
        }
        final double[] product = twoProduct(firstFactor, secondFactor);
        terms[termCount] = sign * product[0];
        exponents[termCount] = firstFactorScale + secondFactorScale;
        termCount++;
        terms[termCount] = sign * product[1];
        exponents[termCount] = firstFactorScale + secondFactorScale;
        termCount++;
        return termCount;
    }

    private static int signOfSum(final double[] terms, final int[] exponents, final int count,
            final double[] components, final int[] componentExponents) {
        int componentCount = 0;
        for (int index = 0; index < count; index++) {
            componentCount = mergeComponent(components, componentExponents, componentCount,
                terms[index], exponents[index]);
        }
        for (int index = 0; index < componentCount; index++) {
            if (components[index] > 0.0) {
                return 1;
            }
            if (components[index] < 0.0) {
                return -1;
            }
        }
        return 0;
    }

    private static int mergeComponent(final double[] components, final int[] componentExponents,
            int componentCount, double value, int exponent) {
        while (value != 0.0) {
            if (Math.abs(value) < 0x1.0p-1022) {
                value = Math.scalb(value, 1074);
                exponent -= 1074;
            }
            final int scale = Math.getExponent(value);
            if (scale != 0) {
                value = Math.scalb(value, -scale);
                exponent += scale;
            }
            int partner = -1;
            for (int index = 0; index < componentCount; index++) {
                if (Math.abs(componentExponents[index] - exponent) <= 52) {
                    partner = index;
                    break;
                }
            }
            if (partner < 0) {
                int position = componentCount;
                while (position > 0 && componentExponents[position - 1] < exponent) {
                    position--;
                }
                for (int index = componentCount; index > position; index--) {
                    components[index] = components[index - 1];
                    componentExponents[index] = componentExponents[index - 1];
                }
                components[position] = value;
                componentExponents[position] = exponent;
                return componentCount + 1;
            }
            final double partnerValue = components[partner];
            final int partnerExponent = componentExponents[partner];
            double first = value;
            double second = partnerValue;
            int firstExponent = exponent;
            int secondExponent = partnerExponent;
            if (secondExponent > firstExponent) {
                first = partnerValue;
                second = value;
                firstExponent = partnerExponent;
                secondExponent = exponent;
            }
            final double aligned = Math.scalb(second, secondExponent - firstExponent);
            final double[] sum = twoSum(first, aligned);
            for (int index = partner; index < componentCount - 1; index++) {
                components[index] = components[index + 1];
                componentExponents[index] = componentExponents[index + 1];
            }
            componentCount--;
            if (sum[0] == 0.0) {
                value = 0.0;
            }
            else {
                componentCount = mergeComponent(components, componentExponents, componentCount,
                    sum[1], firstExponent);
                value = sum[0];
                exponent = firstExponent;
            }
        }
        return componentCount;
    }

    private static double[] twoSum(final double first, final double second) {
        final double sum = first + second;
        final double secondVirtual = sum - first;
        final double firstResidual = first - (sum - secondVirtual);
        final double secondResidual = second - secondVirtual;
        return new double[] { sum, firstResidual + secondResidual };
    }

    private static double[] twoDiff(final double first, final double second) {
        if (Math.abs(first) >= Math.abs(second)) {
            final double difference = first - second;
            return new double[] { difference, (first - difference) - second };
        }
        final double difference = second - first;
        return new double[] { -difference, -((second - difference) - first) };
    }

    private static double[] twoProduct(final double first, final double second) {
        final double splitter = 134217729.0;
        final double firstHigh = splitter * first - (splitter * first - first);
        final double firstLow = first - firstHigh;
        final double secondHigh = splitter * second - (splitter * second - second);
        final double secondLow = second - secondHigh;
        final double product = first * second;
        final double error = (firstHigh * secondHigh - product) + firstHigh * secondLow
            + firstLow * secondHigh + firstLow * secondLow;
        return new double[] { product, error };
    }

    private static int compare(final LayoutPoint first, final LayoutPoint second) {
        int result = Double.compare(first.x(), second.x());
        if (result != 0) {
            return result;
        }
        return Double.compare(first.y(), second.y());
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof HullGeometry)) {
            return false;
        }
        final HullGeometry that = (HullGeometry) other;
        return exactPolygon.equals(that.exactPolygon) && labelAnchor.equals(that.labelAnchor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(exactPolygon, labelAnchor);
    }

    @Override
    public String toString() {
        return "HullGeometry{" + "vertexCount=" + exactPolygon.size() + '}';
    }
}
