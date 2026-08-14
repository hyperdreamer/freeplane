package org.freeplane.plugin.graph.geometry;

import java.util.Objects;

public final class NodeGeometry {
    private final LayoutPoint center;
    private final double radius;
    private final double minX;
    private final double minY;
    private final double maxX;
    private final double maxY;

    private NodeGeometry(final LayoutPoint center, final double radius) {
        this.center = Objects.requireNonNull(center, "center");
        if (!(radius > 0.0) || Double.isInfinite(radius)) {
            throw new IllegalArgumentException("Node radius must be finite and positive");
        }
        this.radius = radius;
        this.minX = center.x() - radius;
        this.minY = center.y() - radius;
        this.maxX = center.x() + radius;
        this.maxY = center.y() + radius;
    }

    public static NodeGeometry of(final LayoutPoint center, final double radius) {
        return new NodeGeometry(center, radius);
    }

    public LayoutPoint center() {
        return center;
    }

    public double radius() {
        return radius;
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
        final double dx = Math.abs(point.x() - center.x());
        final double dy = Math.abs(point.y() - center.y());
        final double largest = Math.max(dx, dy);
        if (largest == 0.0) {
            return true;
        }
        final double ux = dx / largest;
        final double uy = dy / largest;
        final double scaledRadius = radius / largest;
        return ux * ux + uy * uy <= scaledRadius * scaledRadius;
    }

    public LayoutPoint boundaryToward(final LayoutPoint toward) {
        Objects.requireNonNull(toward, "toward");
        final Difference dx = difference(toward.x(), center.x());
        final Difference dy = difference(toward.y(), center.y());
        if (dx.significand == 0.0 && dy.significand == 0.0) {
            return LayoutPoint.of(center.x() + radius, center.y());
        }
        final int dominantExponent = Math.max(dx.exponent, dy.exponent);
        final double unitX = Math.scalb(dx.significand, dx.exponent - dominantExponent);
        final double unitY = Math.scalb(dy.significand, dy.exponent - dominantExponent);
        final double residualX = Math.scalb(dx.residual, -dominantExponent);
        final double residualY = Math.scalb(dy.residual, -dominantExponent);
        final Product xSquare = exactProduct(unitX, unitX);
        final TaggedProduct ySquare = exactProductTagged(unitY, unitY);
        final Sum squareSum = exactSum(xSquare.high, Math.scalb(ySquare.high, ySquare.exponent));
        final double lengthLead = Math.sqrt(squareSum.sum);
        final Product lengthSquare = exactProduct(lengthLead, lengthLead);
        final double squareTail = ((squareSum.sum - lengthSquare.high) - lengthSquare.low + squareSum.error
            + xSquare.low + Math.scalb(ySquare.low, ySquare.exponent))
            + (2.0 * (unitX * residualX + unitY * residualY) + residualX * residualX + residualY * residualY);
        final double lengthTail = squareTail / (2.0 * lengthLead);
        final int squareExponent = Math.getExponent(squareTail);
        final int ySquareExponent = ySquare.exponent;
        final double scaledYHigh = Math.scalb(ySquare.high, ySquareExponent);
        final double scaledYLow = Math.scalb(ySquare.low, ySquareExponent);
        final int tailScale = Math.max(Math.min(squareExponent, ySquareExponent), squareExponent - 1000);
        final double highLoss = scaledYHigh == 0.0 ? ySquare.high
            : ySquare.high - Math.scalb(scaledYHigh, -ySquareExponent);
        final double lowLoss = scaledYLow == 0.0 ? ySquare.low
            : ySquare.low - Math.scalb(scaledYLow, -ySquareExponent);
        final double tailSignificand = Math.scalb(squareTail, -tailScale)
            + Math.scalb(highLoss, ySquareExponent - tailScale)
            + Math.scalb(lowLoss, ySquareExponent - tailScale);
        final double lengthLeadSignificand = Math.scalb(lengthLead, -Math.getExponent(lengthLead));
        final double lengthTailValue = tailSignificand / lengthLeadSignificand;
        final int lengthTailExponent = tailScale - Math.getExponent(lengthLead) - 1
            + Math.getExponent(lengthTailValue);
        final double lengthTailSignificand = Math.scalb(lengthTailValue, -Math.getExponent(lengthTailValue));
        if (Math.abs(unitX) >= Math.abs(unitY)) {
            final double boundaryX = dominantCoordinate(center.x(), radius, unitX, residualX, unitY, residualY,
                lengthLead, lengthTail);
            final double boundaryY = minorCoordinate(center.y(), radius, dy, dominantExponent, lengthLead,
                lengthTail, lengthTailSignificand, lengthTailExponent);
            return LayoutPoint.of(boundaryX, boundaryY);
        }
        final double boundaryX = minorCoordinate(center.x(), radius, dx, dominantExponent, lengthLead,
            lengthTail, lengthTailSignificand, lengthTailExponent);
        final double boundaryY = dominantCoordinate(center.y(), radius, unitY, residualY, unitX, residualX,
            lengthLead, lengthTail);
        return LayoutPoint.of(boundaryX, boundaryY);
    }

    private static double dominantCoordinate(final double centerCoordinate, final double radius, final double unit,
            final double residual, final double otherUnit, final double otherResidual, final double lengthLead,
            final double lengthTail) {
        final double sign = unit < 0.0 ? -1.0 : 1.0;
        final Sum magnitude = exactSum(sign * unit, sign * residual);
        final Sum head = exactSum(lengthLead, magnitude.sum);
        final Sum middle = exactSum(head.sum, lengthTail);
        final Sum tail = exactSum(middle.sum, magnitude.error);
        final double normSum = tail.error + middle.error + head.error;
        final Product normProduct = exactProduct(lengthLead, tail.sum);
        final double productTail = normProduct.low + lengthLead * normSum + lengthTail * tail.sum
            + lengthTail * normSum;
        final TaggedProduct scaledOther = exactProductTagged(radius, otherUnit);
        final TaggedProduct scaledSquare = exactProductTagged(scaledOther.high, otherUnit);
        final double otherTail = 2.0 * otherUnit * otherResidual + otherResidual * otherResidual;
        final double smallOther = scaledOther.low == 0.0 ? 0.0
            : Math.scalb(scaledOther.low, Math.getExponent(otherUnit) - scaledSquare.exponent)
                * Math.scalb(otherUnit, -Math.getExponent(otherUnit));
        final double smallResidual = otherTail == 0.0 ? 0.0
            : Math.scalb(radius, -Math.getExponent(radius))
                * Math.scalb(otherTail, -Math.getExponent(otherTail))
                * Math.scalb(1.0, Math.getExponent(radius) + Math.getExponent(otherTail)
                    - scaledOther.exponent - scaledSquare.exponent);
        final double scaledNumerator = scaledSquare.high + scaledSquare.low + smallOther + smallResidual;
        final double scaledDenominator = normProduct.high + productTail;
        final double quotientScaled = scaledNumerator / scaledDenominator;
        final Product quotientProduct = exactProduct(quotientScaled, normProduct.high);
        final double quotientResidual = ((scaledSquare.high - quotientProduct.high) - quotientProduct.low
            + scaledSquare.low + smallOther + smallResidual) - quotientScaled * productTail;
        final double quotientTailScaled = quotientResidual / scaledDenominator;
        final double quotientRemainder = quotientResidual - quotientTailScaled * normProduct.high;
        final double quotientTail2Scaled = quotientRemainder / scaledDenominator;
        final double main = sign * radius;
        final int correctionScale = scaledOther.exponent + scaledSquare.exponent;
        final double correctionTailSignificand = quotientTailScaled + quotientTail2Scaled;
        if (quotientScaled == 0.0) {
            if (correctionTailSignificand == 0.0) {
                return addExact(centerCoordinate, main, 0.0);
            }
            return finalSum(centerCoordinate, main, 0.0, 0, -sign * correctionTailSignificand,
                correctionScale);
        }
        final int quotientExponent = Math.getExponent(quotientScaled);
        return finalSum(centerCoordinate, main, -sign * Math.scalb(quotientScaled, -quotientExponent),
            correctionScale + quotientExponent, -sign * correctionTailSignificand, correctionScale);
    }

    private static double minorCoordinate(final double centerCoordinate, final double radius,
            final Difference difference, final int dominantExponent, final double lengthLead,
            final double lengthTail, final double lengthTailSignificand, final int lengthTailExponent) {
        final double scaledRadius = Math.scalb(radius, difference.exponent - dominantExponent);
        final double quotient = difference.significand / lengthLead;
        final Product quotientProduct = exactProduct(quotient, lengthLead);
        final double quotientResidual = ((difference.significand - quotientProduct.high) - quotientProduct.low);
        final double quotientTail = quotientResidual / lengthLead;
        final double quotientRemainder = quotientResidual - quotientTail * lengthLead;
        final double quotientTail2 = quotientRemainder / lengthLead;
        final Product scaledProduct = exactProduct(scaledRadius, quotient);
        final double residualRatio = residualRatioTerm(radius, difference.residual, dominantExponent,
            lengthLead + lengthTail);
        final double lengthCorrection = -(scaledProduct.high + scaledProduct.low)
            * (lengthTailSignificand / lengthLead) * Math.scalb(1.0, lengthTailExponent);
        final double smallTerms = scaledRadius * quotientTail + scaledRadius * quotientTail2 + residualRatio
            + lengthCorrection;
        final Sum first = exactSum(scaledProduct.high, scaledProduct.low);
        final Sum second = exactSum(first.sum, smallTerms);
        return addExact(centerCoordinate, second.sum, first.error + second.error);
    }

    private static double residualRatioTerm(final double radius, final double residual, final int dominantExponent,
            final double length) {
        if (residual == 0.0) {
            return 0.0;
        }
        final int radiusExponent = Math.getExponent(radius);
        final int residualExponent = Math.getExponent(residual);
        final int lengthExponent = Math.getExponent(length);
        final double product = Math.scalb(radius, -radiusExponent) * Math.scalb(residual, -residualExponent);
        final double quotient = product / Math.scalb(length, -lengthExponent);
        return Math.scalb(quotient, radiusExponent + residualExponent - dominantExponent - lengthExponent);
    }

    private static double finalSum(final double first, final double second, final double significand,
            final int exponent, final double tailSignificand, final int tailExponent) {
        final Sum head = exactSum(first, second);
        if (head == null) {
            return 2.0 * finalSum(first * 0.5, second * 0.5, significand, exponent - 1,
                tailSignificand, tailExponent - 1);
        }
        if (significand != 0.0 && exponent >= -1022) {
            final double high = Math.scalb(significand, exponent);
            if (high != 0.0 && !Double.isInfinite(high)) {
                final Sum sum = exactSum(head.sum, high);
                if (sum != null) {
                    return addTaggedTail(sum.sum, sum.error + head.error, tailSignificand, tailExponent);
                }
            }
        }
        return addSubnormalTaggedTerm(head.sum, head.error, significand, exponent, tailSignificand, tailExponent);
    }

    private static double addSubnormalTaggedTerm(final double base, final double residual,
            final double significand, final int exponent, final double tailSignificand,
            final int tailExponent) {
        final double highInMinimumSubnormals = Math.scalb(significand, exponent + 1074);
        long grid = (long) Math.floor(highInMinimumSubnormals);
        double remainder = highInMinimumSubnormals - grid;
        remainder += Math.scalb(tailSignificand, tailExponent + 1074);
        final long carry = (long) Math.floor(remainder);
        grid += carry;
        remainder -= carry;
        final Sum sum = exactSum(base, grid * Double.MIN_VALUE);
        if (sum == null) {
            return 2.0 * addSubnormalTaggedTerm(base * 0.5, residual * 0.5, significand, exponent - 1,
                tailSignificand, tailExponent - 1);
        }
        return addTaggedTail(sum.sum, sum.error + residual, remainder, -1074);
    }

    private static double addTaggedTail(final double value, final double residual,
            final double tailSignificand, final int tailExponent) {
        final int scaleExponent = roundingScaleExponent(value);
        final double residualInUnits = Math.scalb(residual, -scaleExponent);
        final double tailInUnits = Math.scalb(tailSignificand, tailExponent - scaleExponent);
        final double units = residualInUnits + tailInUnits;
        if (Double.isFinite(units) && Math.abs(units) <= 16.0) {
            return roundWithTail(value, units, scaleExponent);
        }
        if (tailSignificand == 0.0) {
            return value + residual;
        }
        final double tail = Math.scalb(tailSignificand, tailExponent);
        final Sum sum = exactSum(value, tail);
        if (sum != null) {
            return addTaggedTail(sum.sum, sum.error + residual, 0.0, 0);
        }
        return value + tail + residual;
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

    private static Product exactProduct(final double first, final double second) {
        final TaggedProduct tagged = exactProductTagged(first, second);
        return new Product(Math.scalb(tagged.high, tagged.exponent), Math.scalb(tagged.low, tagged.exponent));
    }

    private static TaggedProduct exactProductTagged(final double first, final double second) {
        final int firstExponent = Math.getExponent(first);
        final int secondExponent = Math.getExponent(second);
        final Product normalized = exactProductUnscaled(Math.scalb(first, -firstExponent),
            Math.scalb(second, -secondExponent));
        return new TaggedProduct(normalized.high, normalized.low, firstExponent + secondExponent);
    }

    private static Product exactProductUnscaled(final double first, final double second) {
        final double product = first * second;
        final double firstHigh = split(first);
        final double firstLow = first - firstHigh;
        final double secondHigh = split(second);
        final double secondLow = second - secondHigh;
        final double error = ((firstHigh * secondHigh - product) + firstHigh * secondLow + firstLow * secondHigh)
            + firstLow * secondLow;
        return new Product(product, error);
    }

    private static double split(final double value) {
        final double scaled = value * 134217729.0;
        return scaled - (scaled - value);
    }

    private static Sum exactSum(final double first, final double second) {
        final double sum = first + second;
        if (Double.isInfinite(sum)) {
            return null;
        }
        final double secondVirtual = sum - first;
        final double firstVirtual = sum - secondVirtual;
        final double firstRound = first - firstVirtual;
        final double secondRound = second - secondVirtual;
        return new Sum(sum, firstRound + secondRound);
    }

    private static double addExact(final double first, final double second, final double third) {
        final Sum head = exactSum(first, second);
        if (head != null) {
            final Sum tail = exactSum(head.sum, third);
            if (tail != null) {
                return tail.sum + (tail.error + head.error);
            }
        }
        final Sum halfHead = exactSum(first * 0.5, second * 0.5);
        final Sum halfMiddle = exactSum(halfHead.sum, third * 0.5);
        final Sum halfTail = exactSum(halfMiddle.sum, halfHead.error);
        return 2.0 * (halfTail.sum + (halfTail.error + halfMiddle.error));
    }

    private static Difference difference(final double first, final double second) {
        if (subtractionIsFinite(first, second)) {
            final double delta = first - second;
            if (delta == 0.0) {
                return new Difference(0.0, 0, 0.0);
            }
            final double residual;
            if (Math.abs(first) >= Math.abs(second)) {
                residual = subtractionResidual(first, second, delta);
            }
            else {
                residual = -subtractionResidual(second, first, -delta);
            }
            return new Difference(Math.scalb(delta, -Math.getExponent(delta)), Math.getExponent(delta), residual);
        }
        final double halfFirst = first * 0.5;
        final double halfSecond = second * 0.5;
        final double half = halfFirst - halfSecond;
        final double residual;
        if (Math.abs(halfFirst) >= Math.abs(halfSecond)) {
            residual = subtractionResidual(halfFirst, halfSecond, half) * 2.0;
        }
        else {
            residual = -subtractionResidual(halfSecond, halfFirst, -half) * 2.0;
        }
        return new Difference(Math.scalb(half, -Math.getExponent(half)), Math.getExponent(half) + 1, residual);
    }

    private static double subtractionResidual(final double first, final double second, final double difference) {
        final double secondVirtual = first - difference;
        final double firstVirtual = difference + secondVirtual;
        final double secondRound = secondVirtual - second;
        final double firstRound = first - firstVirtual;
        return firstRound + secondRound;
    }

    private static final class Difference {
        private final double significand;
        private final int exponent;
        private final double residual;

        Difference(final double significand, final int exponent, final double residual) {
            this.significand = significand;
            this.exponent = exponent;
            this.residual = residual;
        }
    }

    private static final class TaggedProduct {
        private final double high;
        private final double low;
        private final int exponent;

        TaggedProduct(final double high, final double low, final int exponent) {
            this.high = high;
            this.low = low;
            this.exponent = exponent;
        }
    }

    private static final class Product {
        private final double high;
        private final double low;

        Product(final double high, final double low) {
            this.high = high;
            this.low = low;
        }
    }

    private static final class Sum {
        private final double sum;
        private final double error;

        Sum(final double sum, final double error) {
            this.sum = sum;
            this.error = error;
        }
    }

    private static boolean subtractionIsFinite(final double first, final double second) {
        return (first >= 0.0 && second >= 0.0) || (first <= 0.0 && second <= 0.0)
            || Math.abs(first) <= Double.MAX_VALUE - Math.abs(second);
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof NodeGeometry)) {
            return false;
        }
        final NodeGeometry that = (NodeGeometry) other;
        return Double.compare(radius, that.radius) == 0 && center.equals(that.center);
    }

    @Override
    public int hashCode() {
        return Objects.hash(center, radius);
    }

    @Override
    public String toString() {
        return "NodeGeometry{" + "center=" + center + ", radius=" + radius + '}';
    }
}
