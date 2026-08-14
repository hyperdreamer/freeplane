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
                lengthLead, lengthTailSignificand, lengthTailExponent);
            final double boundaryY = minorCoordinate(center.y(), radius, dy, dominantExponent, lengthLead,
                lengthTail, lengthTailSignificand, lengthTailExponent);
            return LayoutPoint.of(boundaryX, boundaryY);
        }
        final double boundaryX = minorCoordinate(center.x(), radius, dx, dominantExponent, lengthLead,
            lengthTail, lengthTailSignificand, lengthTailExponent);
        final double boundaryY = dominantCoordinate(center.y(), radius, unitY, residualY, unitX, residualX,
            lengthLead, lengthTailSignificand, lengthTailExponent);
        return LayoutPoint.of(boundaryX, boundaryY);
    }

    private static double dominantCoordinate(final double centerCoordinate, final double radius, final double unit,
            final double residual, final double otherUnit, final double otherResidual, final double lengthLead,
            final double lengthTailSignificand, final int lengthTailExponent) {
        final double sign = unit < 0.0 ? -1.0 : 1.0;
        final TaggedExpansion length = singleTermExpansion(lengthLead);
        length.addTerm(lengthTailSignificand, lengthTailExponent);
        final TaggedExpansion magnitude = singleTermExpansion(sign * unit);
        magnitude.addTerm(sign * residual, 0);
        final TaggedExpansion normSum = length.copy();
        normSum.addExpansion(magnitude, 1.0);
        final TaggedExpansion normProduct = multiplyExpansions(length, normSum);
        final TaggedExpansion other = singleTermExpansion(otherUnit);
        other.addTerm(otherResidual, 0);
        final TaggedExpansion scaledOther = multiplyExpansions(singleTermExpansion(radius), other);
        final TaggedExpansion scaledSquare = multiplyExpansions(scaledOther, other);
        final TaggedQuotient correction = new TaggedQuotient(scaledSquare, normProduct);
        return finalSum(centerCoordinate, sign * radius, correction, sign < 0.0 ? 1 : -1);
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

    private static double finalSum(final double first, final double second, final TaggedQuotient correction,
            final int correctionSign) {
        final TaggedExpansion sum = singleTermExpansion(first);
        sum.addTerm(second, 0);
        while (true) {
            final TaggedTerm term = correction.next();
            if (term != null) {
                sum.addTerm(correctionSign * term.significand, term.exponent);
            }
            final Rounding rounding = roundExpansion(sum, correctionSign * correction.remainderSign(),
                correction.remainderUpperExponent());
            if (rounding.determined) {
                return rounding.value;
            }
        }
    }

    private static Rounding roundExpansion(final TaggedExpansion value, final int unknownSign,
            final int unknownUpperExponent) {
        final double candidate;
        if (value.size == 0) {
            candidate = 0.0;
        }
        else if (value.exponents[0] >= -1022 && value.exponents[0] <= 1023) {
            candidate = Math.scalb(value.significands[0], value.exponents[0]);
        }
        else if (value.exponents[0] < -1022) {
            final double units = Math.scalb(value.significands[0], value.exponents[0] + 1074);
            candidate = (long) units * Double.MIN_VALUE;
        }
        else {
            candidate = value.sign() > 0 ? Double.MAX_VALUE : -Double.MAX_VALUE;
        }
        final TaggedExpansion remainder = value.copy();
        remainder.addTerm(-candidate, 0);
        return roundWithTail(candidate, remainder, unknownSign, unknownUpperExponent);
    }

    private static Rounding roundWithTail(double value, final TaggedExpansion remainder,
            final int unknownSign, final int unknownUpperExponent) {
        while (true) {
            final int direction = signWithUnknown(remainder, unknownSign, unknownUpperExponent);
            if (direction == Integer.MIN_VALUE) {
                return new Rounding(false, 0.0);
            }
            if (direction == 0) {
                return new Rounding(true, value);
            }
            final int gapExponent = neighborGapExponent(value, direction > 0);
            final TaggedExpansion boundary = remainder.copy();
            boundary.addTerm(-direction, gapExponent - 1);
            final int comparison = compareWithUnknown(boundary, direction, unknownSign, unknownUpperExponent);
            if (comparison == Integer.MIN_VALUE) {
                return new Rounding(false, 0.0);
            }
            if (comparison < 0 || comparison == 0
                    && (Double.doubleToRawLongBits(value) & 1L) == 0L) {
                return new Rounding(true, value);
            }
            final double next = Math.nextAfter(value,
                direction > 0 ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY);
            if (Double.isInfinite(next)) {
                return new Rounding(true, next);
            }
            remainder.addTerm(-direction, gapExponent);
            value = next;
        }
    }

    private static int signWithUnknown(final TaggedExpansion value, final int unknownSign,
            final int unknownUpperExponent) {
        if (value.size == 0) {
            return unknownSign;
        }
        if (unknownSign == 0 || value.exponents[0] > unknownUpperExponent) {
            return value.sign();
        }
        return Integer.MIN_VALUE;
    }

    private static int compareWithUnknown(final TaggedExpansion value, final int direction,
            final int unknownSign, final int unknownUpperExponent) {
        if (value.size == 0) {
            return direction * unknownSign;
        }
        if (unknownSign == 0 || value.exponents[0] > unknownUpperExponent) {
            return direction * value.sign();
        }
        return Integer.MIN_VALUE;
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

    private static TaggedExpansion singleTermExpansion(final double value) {
        final TaggedExpansion expansion = new TaggedExpansion();
        expansion.addTerm(value, 0);
        return expansion;
    }

    private static TaggedExpansion multiplyExpansions(final TaggedExpansion first, final TaggedExpansion second) {
        final TaggedExpansion product = new TaggedExpansion();
        for (int firstIndex = 0; firstIndex < first.size; firstIndex++) {
            for (int secondIndex = 0; secondIndex < second.size; secondIndex++) {
                final Product term = exactProductUnscaled(first.significands[firstIndex],
                    second.significands[secondIndex]);
                final int exponent = first.exponents[firstIndex] + second.exponents[secondIndex];
                product.addTerm(term.high, exponent);
                product.addTerm(term.low, exponent);
            }
        }
        return product;
    }

    private static TaggedExpansion multiplyByTerm(final TaggedExpansion value, final double significand,
            final int exponent) {
        final TaggedExpansion product = new TaggedExpansion();
        for (int index = 0; index < value.size; index++) {
            final Product term = exactProductUnscaled(value.significands[index], significand);
            final int productExponent = value.exponents[index] + exponent;
            product.addTerm(term.high, productExponent);
            product.addTerm(term.low, productExponent);
        }
        return product;
    }

    private static int binaryExponent(final double value) {
        final long bits = Double.doubleToRawLongBits(Math.abs(value));
        final int exponent = (int) ((bits >>> 52) & 0x7ffL);
        if (exponent != 0) {
            return exponent - 1023;
        }
        final long fraction = bits & 0x000fffffffffffffL;
        return -1074 + 63 - Long.numberOfLeadingZeros(fraction);
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

    private static final class TaggedExpansion {
        private double[] significands = new double[8];
        private int[] exponents = new int[8];
        private int size;

        TaggedExpansion copy() {
            final TaggedExpansion copy = new TaggedExpansion();
            copy.ensureCapacity(size);
            System.arraycopy(significands, 0, copy.significands, 0, size);
            System.arraycopy(exponents, 0, copy.exponents, 0, size);
            copy.size = size;
            return copy;
        }

        void addTerm(final double significand, final int exponent) {
            if (significand == 0.0) {
                return;
            }
            final int significandExponent = binaryExponent(significand);
            addNormalized(Math.scalb(significand, -significandExponent), exponent + significandExponent);
        }

        void addExpansion(final TaggedExpansion other, final double multiplier) {
            for (int index = 0; index < other.size; index++) {
                addTerm(multiplier * other.significands[index], other.exponents[index]);
            }
        }

        int sign() {
            return size == 0 ? 0 : significands[0] < 0.0 ? -1 : 1;
        }

        private void addNormalized(final double significand, final int exponent) {
            for (int index = 0; index < size; index++) {
                if (Math.abs(exponents[index] - exponent) <= 52) {
                    final double existing = significands[index];
                    final int existingExponent = exponents[index];
                    remove(index);
                    final int scale = Math.max(existingExponent, exponent);
                    final Sum sum = exactSum(Math.scalb(existing, existingExponent - scale),
                        Math.scalb(significand, exponent - scale));
                    if (sum.sum != 0.0) {
                        addTerm(sum.sum, scale);
                    }
                    if (sum.error != 0.0) {
                        addTerm(sum.error, scale);
                    }
                    return;
                }
            }
            ensureCapacity(size + 1);
            int insertion = 0;
            while (insertion < size && exponents[insertion] > exponent) {
                insertion++;
            }
            System.arraycopy(significands, insertion, significands, insertion + 1, size - insertion);
            System.arraycopy(exponents, insertion, exponents, insertion + 1, size - insertion);
            significands[insertion] = significand;
            exponents[insertion] = exponent;
            size++;
        }

        private void remove(final int index) {
            System.arraycopy(significands, index + 1, significands, index, size - index - 1);
            System.arraycopy(exponents, index + 1, exponents, index, size - index - 1);
            size--;
        }

        private void ensureCapacity(final int capacity) {
            if (capacity <= significands.length) {
                return;
            }
            final int newCapacity = Math.max(capacity, significands.length * 2);
            final double[] newSignificands = new double[newCapacity];
            final int[] newExponents = new int[newCapacity];
            System.arraycopy(significands, 0, newSignificands, 0, size);
            System.arraycopy(exponents, 0, newExponents, 0, size);
            significands = newSignificands;
            exponents = newExponents;
        }
    }

    private static final class TaggedQuotient {
        private final TaggedExpansion denominator;
        private final TaggedExpansion residual;

        TaggedQuotient(final TaggedExpansion numerator, final TaggedExpansion denominator) {
            this.denominator = denominator;
            this.residual = numerator;
        }

        TaggedTerm next() {
            if (residual.size == 0) {
                return null;
            }
            double significand = residual.significands[0] / denominator.significands[0];
            int exponent = residual.exponents[0] - denominator.exponents[0];
            final int significandExponent = binaryExponent(significand);
            significand = Math.scalb(significand, -significandExponent);
            exponent += significandExponent;
            residual.addExpansion(multiplyByTerm(denominator, significand, exponent), -1.0);
            return new TaggedTerm(significand, exponent);
        }

        int remainderSign() {
            return residual.sign();
        }

        int remainderUpperExponent() {
            return residual.size == 0 ? Integer.MIN_VALUE
                : residual.exponents[0] - denominator.exponents[0] + 2;
        }
    }

    private static final class TaggedTerm {
        private final double significand;
        private final int exponent;

        TaggedTerm(final double significand, final int exponent) {
            this.significand = significand;
            this.exponent = exponent;
        }
    }

    private static final class Rounding {
        private final boolean determined;
        private final double value;

        Rounding(final boolean determined, final double value) {
            this.determined = determined;
            this.value = value;
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
