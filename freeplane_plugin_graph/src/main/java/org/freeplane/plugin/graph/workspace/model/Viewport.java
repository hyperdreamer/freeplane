package org.freeplane.plugin.graph.workspace.model;

import java.util.List;
import java.util.Objects;

public final class Viewport {
    private final double centerX;
    private final double centerY;
    private final double zoom;
    private final List<UnknownXml> unknownXml;

    private Viewport(final double centerX, final double centerY, final double zoom,
            final List<UnknownXml> unknownXml) {
        requireFinite(centerX, "centerX");
        requireFinite(centerY, "centerY");
        requireFinite(zoom, "zoom");
        if (zoom <= 0) {
            throw new IllegalArgumentException("zoom must be positive");
        }
        this.centerX = centerX;
        this.centerY = centerY;
        this.zoom = zoom;
        this.unknownXml = UnknownXml.forRecord(unknownXml);
    }

    public static Viewport of(final double centerX, final double centerY, final double zoom,
            final List<UnknownXml> unknownXml) {
        return new Viewport(centerX, centerY, zoom, unknownXml);
    }

    public double centerX() {
        return centerX;
    }

    public double centerY() {
        return centerY;
    }

    public double zoom() {
        return zoom;
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
        if (!(other instanceof Viewport)) {
            return false;
        }
        final Viewport that = (Viewport) other;
        return Double.compare(centerX, that.centerX) == 0 && Double.compare(centerY, that.centerY) == 0
            && Double.compare(zoom, that.zoom) == 0 && unknownXml.equals(that.unknownXml);
    }

    @Override
    public int hashCode() {
        return Objects.hash(centerX, centerY, zoom, unknownXml);
    }

    @Override
    public String toString() {
        return "Viewport{" + "centerX=" + centerX + ", centerY=" + centerY + ", zoom=" + zoom
            + ", unknownXml=" + unknownXml + '}';
    }
}
