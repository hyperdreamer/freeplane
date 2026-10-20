package org.freeplane.plugin.graph.layout.graphstream;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.freeplane.plugin.graph.projection.EnclosureHullKey;

/**
 * Deterministic V1 rectangle packer: the single placement authority for seeding
 * and footprints. Pure and stateless; contacts are metadata no force reads.
 */
final class ClusterPacking {
    static final double CANDIDATE_EPSILON = 1e-9;
    static final double CONTACT_GAP_TOLERANCE = 0.75;

    private static final Comparator<ClusterItem> ITEM_ORDER = new Comparator<ClusterItem>() {
        @Override
        public int compare(final ClusterItem first, final ClusterItem second) {
            if ((first.anchorKey == null) != (second.anchorKey == null)) {
                return first.anchorKey == null ? -1 : 1;
            }
            final int byArea = Double.compare(second.width * second.height, first.width * first.height);
            if (byArea != 0) {
                return byArea;
            }
            return Integer.compare(first.order, second.order);
        }
    };

    private ClusterPacking() {
    }

    static List<ClusterPlacement> packItems(final List<ClusterItem> items, final double gap) {
        Objects.requireNonNull(items, "items");
        requireFiniteNonNegative(gap, "gap");
        final List<ClusterItem> ordered = new ArrayList<ClusterItem>(items.size());
        final Set<EnclosureHullKey> seen = new LinkedHashSet<EnclosureHullKey>();
        for (final ClusterItem item : items) {
            Objects.requireNonNull(item, "item");
            requirePositiveFinite(item.width, "width");
            requirePositiveFinite(item.height, "height");
            if (item.anchorKey != null && !seen.add(item.anchorKey)) {
                throw new IllegalArgumentException("duplicate anchor key " + item.anchorKey);
            }
            ordered.add(item);
        }
        if (ordered.isEmpty()) {
            return new ArrayList<ClusterPlacement>();
        }
        Collections.sort(ordered, ITEM_ORDER);
        final List<ClusterPlacement> placed = new ArrayList<ClusterPlacement>(ordered.size());
        for (final ClusterItem item : ordered) {
            placed.add(place(item, placed, gap));
        }
        return placed;
    }

    private static ClusterPlacement place(final ClusterItem item, final List<ClusterPlacement> placed,
            final double gap) {
        if (placed.isEmpty()) {
            return new ClusterPlacement(item, 0.0, 0.0);
        }
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        final List<Double> xs = new ArrayList<Double>();
        final List<Double> ys = new ArrayList<Double>();
        for (final ClusterPlacement p : placed) {
            xs.add(Double.valueOf(p.x - (p.item.width + item.width) * 0.5 - gap));
            xs.add(Double.valueOf(p.x + (p.item.width + item.width) * 0.5 + gap));
            ys.add(Double.valueOf(p.y - (p.item.height + item.height) * 0.5 - gap));
            ys.add(Double.valueOf(p.y + (p.item.height + item.height) * 0.5 + gap));
            xs.add(Double.valueOf(p.x));
            ys.add(Double.valueOf(p.y));
            minX = Math.min(minX, p.x - p.item.width * 0.5);
            maxX = Math.max(maxX, p.x + p.item.width * 0.5);
            minY = Math.min(minY, p.y - p.item.height * 0.5);
            maxY = Math.max(maxY, p.y + p.item.height * 0.5);
        }
        xs.add(Double.valueOf(minX - item.width * 0.5 - gap));
        xs.add(Double.valueOf(maxX + item.width * 0.5 + gap));
        xs.add(Double.valueOf(minX + item.width * 0.5));
        xs.add(Double.valueOf(maxX - item.width * 0.5));
        ys.add(Double.valueOf(minY - item.height * 0.5 - gap));
        ys.add(Double.valueOf(maxY + item.height * 0.5 + gap));
        ys.add(Double.valueOf(minY + item.height * 0.5));
        ys.add(Double.valueOf(maxY - item.height * 0.5));

        final List<Double> sortedX = dedupeSorted(xs);
        final List<Double> sortedY = dedupeSorted(ys);
        ClusterPlacement best = null;
        double bestArea = 0.0;
        double bestRadius = 0.0;
        int bestIndex = 0;
        int index = 0;
        for (final Double xValue : sortedX) {
            for (final Double yValue : sortedY) {
                index++;
                final double x = xValue.doubleValue();
                final double y = yValue.doubleValue();
                if (!isClear(x, y, item, placed, gap)) {
                    continue;
                }
                final double area = assemblyArea(x, y, item, placed);
                final double radius = Math.hypot(x, y);
                if (!isBetter(area, radius, index, best != null, bestArea, bestRadius, bestIndex)) {
                    continue;
                }
                best = new ClusterPlacement(item, x, y);
                bestArea = area;
                bestRadius = radius;
                bestIndex = index;
            }
        }
        if (best == null) {
            throw new IllegalStateException("Cluster packing found no free slot");
        }
        recordContacts(best, placed, gap);
        return best;
    }

    private static List<Double> dedupeSorted(final List<Double> values) {
        Collections.sort(values);
        final List<Double> result = new ArrayList<Double>(values.size());
        for (final Double value : values) {
            if (result.isEmpty()
                    || Math.abs(value.doubleValue() - result.get(result.size() - 1).doubleValue())
                        > CANDIDATE_EPSILON) {
                result.add(value);
            }
        }
        return result;
    }

    private static boolean isClear(final double x, final double y, final ClusterItem item,
            final List<ClusterPlacement> placed, final double gap) {
        for (final ClusterPlacement p : placed) {
            final double clearanceX = Math.abs(x - p.x) - (item.width + p.item.width) * 0.5;
            final double clearanceY = Math.abs(y - p.y) - (item.height + p.item.height) * 0.5;
            if (clearanceX < gap - CANDIDATE_EPSILON && clearanceY < gap - CANDIDATE_EPSILON) {
                return false;
            }
        }
        return true;
    }

    private static double assemblyArea(final double x, final double y, final ClusterItem item,
            final List<ClusterPlacement> placed) {
        double minX = x - item.width * 0.5;
        double maxX = x + item.width * 0.5;
        double minY = y - item.height * 0.5;
        double maxY = y + item.height * 0.5;
        for (final ClusterPlacement p : placed) {
            minX = Math.min(minX, p.x - p.item.width * 0.5);
            maxX = Math.max(maxX, p.x + p.item.width * 0.5);
            minY = Math.min(minY, p.y - p.item.height * 0.5);
            maxY = Math.max(maxY, p.y + p.item.height * 0.5);
        }
        return (maxX - minX) * (maxY - minY);
    }

    private static boolean isBetter(final double area, final double radius, final int index,
            final boolean found, final double bestArea, final double bestRadius, final int bestIndex) {
        if (!found) {
            return true;
        }
        if (area < bestArea - CANDIDATE_EPSILON) {
            return true;
        }
        if (Math.abs(area - bestArea) > CANDIDATE_EPSILON) {
            return false;
        }
        if (radius < bestRadius - CANDIDATE_EPSILON) {
            return true;
        }
        return Math.abs(radius - bestRadius) <= CANDIDATE_EPSILON && index < bestIndex;
    }

    private static void recordContacts(final ClusterPlacement placement, final List<ClusterPlacement> placed,
            final double gap) {
        for (final ClusterPlacement p : placed) {
            final double boxGapX = Math.abs(placement.x - p.x)
                - (placement.item.width + p.item.width) * 0.5;
            final double boxGapY = Math.abs(placement.y - p.y)
                - (placement.item.height + p.item.height) * 0.5;
            if ((boxGapX <= gap + CONTACT_GAP_TOLERANCE && boxGapY < -CONTACT_GAP_TOLERANCE)
                    || (boxGapY <= gap + CONTACT_GAP_TOLERANCE && boxGapX < -CONTACT_GAP_TOLERANCE)) {
                placement.contacts.add(p.item);
                p.contacts.add(placement.item);
            }
        }
    }

    private static void requirePositiveFinite(final double value, final String name) {
        if (!(value > 0.0) || !Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be positive and finite");
        }
    }

    private static void requireFiniteNonNegative(final double value, final String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }

    static final class ClusterItem {
        final EnclosureHullKey anchorKey;
        final double width;
        final double height;
        final double anchorX;
        final double anchorY;
        final int order;

        ClusterItem(final EnclosureHullKey anchorKey, final double width, final double height,
                final double anchorX, final double anchorY, final int order) {
            this.anchorKey = anchorKey;
            this.width = width;
            this.height = height;
            this.anchorX = anchorX;
            this.anchorY = anchorY;
            this.order = order;
        }
    }

    static final class ClusterPlacement {
        final ClusterItem item;
        final double x;
        final double y;
        final List<ClusterItem> contacts = new ArrayList<ClusterItem>();

        ClusterPlacement(final ClusterItem item, final double x, final double y) {
            this.item = item;
            this.x = x;
            this.y = y;
        }
    }
}
