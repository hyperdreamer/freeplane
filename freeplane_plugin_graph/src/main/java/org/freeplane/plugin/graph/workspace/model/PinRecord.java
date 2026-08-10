package org.freeplane.plugin.graph.workspace.model;

import java.util.List;
import java.util.Objects;

public final class PinRecord {
    private final NodeReference node;
    private final double x;
    private final double y;
    private final List<UnknownXml> unknownXml;

    private PinRecord(final NodeReference node, final double x, final double y,
            final List<UnknownXml> unknownXml) {
        this.node = Objects.requireNonNull(node, "node");
        requireFinite(x, "x");
        requireFinite(y, "y");
        this.x = x;
        this.y = y;
        this.unknownXml = UnknownXml.forRecord(unknownXml);
    }

    public static PinRecord of(final NodeReference node, final double x, final double y,
            final List<UnknownXml> unknownXml) {
        return new PinRecord(node, x, y, unknownXml);
    }

    public NodeReference node() {
        return node;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public List<UnknownXml> unknownXml() {
        return unknownXml;
    }

    private static void requireFinite(final double value, final String name) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PinRecord)) {
            return false;
        }
        final PinRecord that = (PinRecord) other;
        return Double.compare(x, that.x) == 0 && Double.compare(y, that.y) == 0
            && node.equals(that.node) && unknownXml.equals(that.unknownXml);
    }

    @Override
    public int hashCode() {
        return Objects.hash(node, x, y, unknownXml);
    }

    @Override
    public String toString() {
        return "PinRecord{" + "node=" + node + ", x=" + x + ", y=" + y
            + ", unknownXml=" + unknownXml + '}';
    }
}
