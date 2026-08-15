package org.freeplane.plugin.graph.geometry;

import java.util.Objects;
import java.util.Optional;

public final class LabelPlacement {
    public enum Mode {
        INTERIOR,
        ARC,
        EXTERNAL,
        HOVER_ONLY
    }

    private final String displayText;
    private final Mode mode;
    private final LayoutPoint anchor;
    private final double width;
    private final double height;
    private final Optional<LayoutPoint> leaderStart;
    private final double minX;
    private final double minY;
    private final double maxX;
    private final double maxY;

    private LabelPlacement(final String displayText, final Mode mode, final LayoutPoint anchor,
            final double width, final double height, final Optional<LayoutPoint> leaderStart) {
        this.displayText = requireDisplayText(displayText);
        this.mode = Objects.requireNonNull(mode, "mode");
        this.anchor = Objects.requireNonNull(anchor, "anchor");
        if (!Double.isFinite(width) || !(width > 0.0)) {
            throw new IllegalArgumentException("Label width must be finite and positive");
        }
        if (!Double.isFinite(height) || !(height > 0.0)) {
            throw new IllegalArgumentException("Label height must be finite and positive");
        }
        this.width = width;
        this.height = height;
        this.leaderStart = Objects.requireNonNull(leaderStart, "leaderStart");
        if ((mode == Mode.EXTERNAL) != leaderStart.isPresent()) {
            throw new IllegalArgumentException("Only external labels have leader starts");
        }
        final double halfWidth = width * 0.5;
        final double halfHeight = height * 0.5;
        this.minX = lowerBound(anchor.x(), halfWidth);
        this.maxX = upperBound(anchor.x(), halfWidth);
        this.minY = lowerBound(anchor.y(), halfHeight);
        this.maxY = upperBound(anchor.y(), halfHeight);
        if (!Double.isFinite(minX) || !Double.isFinite(minY)
                || !Double.isFinite(maxX) || !Double.isFinite(maxY)
                || !(minX < maxX) || !(minY < maxY)) {
            throw new IllegalArgumentException("Label bounds must be finite and positive");
        }
    }

    public static LabelPlacement of(final String displayText, final Mode mode, final LayoutPoint anchor,
            final double width, final double height, final Optional<LayoutPoint> leaderStart) {
        return new LabelPlacement(displayText, mode, anchor, width, height, leaderStart);
    }

    public String displayText() {
        return displayText;
    }

    public Mode mode() {
        return mode;
    }

    public LayoutPoint anchor() {
        return anchor;
    }

    public double width() {
        return width;
    }

    public double height() {
        return height;
    }

    public Optional<LayoutPoint> leaderStart() {
        return leaderStart;
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

    private static double lowerBound(final double center, final double halfExtent) {
        final double bound = center - halfExtent;
        return bound < center ? bound : Math.nextDown(center);
    }

    private static double upperBound(final double center, final double halfExtent) {
        final double bound = center + halfExtent;
        return bound > center ? bound : Math.nextUp(center);
    }

    private static String requireDisplayText(final String value) {
        Objects.requireNonNull(value, "displayText");
        if (value.isEmpty()) {
            throw new IllegalArgumentException("displayText must not be empty");
        }
        return value;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof LabelPlacement)) {
            return false;
        }
        final LabelPlacement that = (LabelPlacement) other;
        return displayText.equals(that.displayText) && mode == that.mode && anchor.equals(that.anchor)
            && Double.compare(width, that.width) == 0 && Double.compare(height, that.height) == 0
            && leaderStart.equals(that.leaderStart);
    }

    @Override
    public int hashCode() {
        return Objects.hash(displayText, mode, anchor, width, height, leaderStart);
    }

    @Override
    public String toString() {
        return "LabelPlacement{" + "mode=" + mode + ", anchor=" + anchor
            + ", width=" + width + ", height=" + height
            + ", leaderStartPresent=" + leaderStart.isPresent() + '}';
    }
}
