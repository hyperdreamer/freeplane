package org.freeplane.plugin.graph.geometry;

import java.util.Objects;

public final class NodeGeometry {
    private static final double SPLIT_SAFE_LIMIT = 1.3e301;

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
        final Product ySquare = exactProduct(unitY, unitY);
        final Sum squareSum = exactSum(xSquare.high, ySquare.high);
        final double lengthLead = Math.sqrt(squareSum.sum);
        final Product lengthSquare = exactProduct(lengthLead, lengthLead);
        final double squareTail = ((squareSum.sum - lengthSquare.high) - lengthSquare.low + squareSum.error
            + xSquare.low + ySquare.low)
            + (2.0 * (unitX * residualX + unitY * residualY) + residualX * residualX + residualY * residualY);
        final double lengthTail = squareTail / (2.0 * lengthLead);
        if (Math.abs(unitX) >= Math.abs(unitY)) {
            final double boundaryX = dominantCoordinate(center.x(), radius, unitX, residualX, unitY, residualY,
                lengthLead, lengthTail);
            final double boundaryY = minorCoordinate(center.y(), radius, dy, dominantExponent, lengthLead,
                lengthTail);
            return LayoutPoint.of(boundaryX, boundaryY);
        }
        final double boundaryX = minorCoordinate(center.x(), radius, dx, dominantExponent, lengthLead,
            lengthTail);
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
        final Product otherSquare = exactProduct(otherUnit, otherUnit);
        final double otherTail = 2.0 * otherUnit * otherResidual + otherResidual * otherResidual;
        final double quotient = otherSquare.high / normProduct.high;
        final Product quotientProduct = exactProduct(quotient, normProduct.high);
        final double quotientResidual = ((otherSquare.high - quotientProduct.high) - quotientProduct.low
            + otherSquare.low + otherTail) - quotient * productTail;
        final double quotientTail = quotientResidual / normProduct.high;
        final double quotientRemainder = quotientResidual - quotientTail * normProduct.high;
        final double quotientTail2 = quotientRemainder / normProduct.high;
        final Product radiusQuotient = exactProduct(radius, quotient);
        final double main = sign * radius;
        final Sum first = exactSum(main, -sign * radiusQuotient.high);
        final double smallTerms = -sign * radiusQuotient.low - sign * radius * quotientTail
            - sign * radius * quotientTail2;
        final Sum second = exactSum(first.sum, smallTerms);
        return addExact(centerCoordinate, second.sum, first.error + second.error);
    }

    private static double minorCoordinate(final double centerCoordinate, final double radius,
            final Difference difference, final int dominantExponent, final double lengthLead,
            final double lengthTail) {
        final double scaledRadius = Math.scalb(radius, difference.exponent - dominantExponent);
        final double quotient = difference.significand / lengthLead;
        final Product quotientProduct = exactProduct(quotient, lengthLead);
        final double quotientResidual = ((difference.significand - quotientProduct.high) - quotientProduct.low)
            - quotient * lengthTail;
        final double quotientTail = quotientResidual / lengthLead;
        final double quotientRemainder = quotientResidual - quotientTail * lengthLead;
        final double quotientTail2 = quotientRemainder / lengthLead;
        final Product scaledProduct = exactProduct(scaledRadius, quotient);
        final double residualRatio = residualRatioTerm(radius, difference.residual, dominantExponent,
            lengthLead + lengthTail);
        final double smallTerms = scaledRadius * quotientTail + scaledRadius * quotientTail2 + residualRatio;
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

    private static Product exactProduct(final double first, final double second) {
        if (Math.abs(first) <= SPLIT_SAFE_LIMIT && Math.abs(second) <= SPLIT_SAFE_LIMIT) {
            return exactProductUnscaled(first, second);
        }
        final Product scaled = exactProductUnscaled(Math.scalb(first, -54), Math.scalb(second, -54));
        return new Product(Math.scalb(scaled.high, 108), Math.scalb(scaled.low, 108));
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
