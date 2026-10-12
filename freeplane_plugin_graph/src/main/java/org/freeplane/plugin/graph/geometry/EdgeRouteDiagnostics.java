package org.freeplane.plugin.graph.geometry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable route diagnostics (spec 2.4, 6.9): accepted shape counts, the number of
 * straight-chord crossings detected before routing, the residual drawn-path crossings in
 * ascending canonical edge-pair order, and the number of (edge, hull) penetrations.
 */
public final class EdgeRouteDiagnostics {
    private final int edgesStraight;
    private final int edgesBow;
    private final int edgesDogleg;
    private final int edgesFallback;
    private final int pathCrossingsDetected;
    private final List<String> pathCrossingsResidual;
    private final int pathHullPenetrations;

    public EdgeRouteDiagnostics(final int edgesStraight, final int edgesBow, final int edgesDogleg,
            final int edgesFallback, final int pathCrossingsDetected, final List<String> pathCrossingsResidual,
            final int pathHullPenetrations) {
        requireNonnegative(edgesStraight, "edgesStraight");
        requireNonnegative(edgesBow, "edgesBow");
        requireNonnegative(edgesDogleg, "edgesDogleg");
        requireNonnegative(edgesFallback, "edgesFallback");
        requireNonnegative(pathCrossingsDetected, "pathCrossingsDetected");
        requireNonnegative(pathHullPenetrations, "pathHullPenetrations");
        this.edgesStraight = edgesStraight;
        this.edgesBow = edgesBow;
        this.edgesDogleg = edgesDogleg;
        this.edgesFallback = edgesFallback;
        this.pathCrossingsDetected = pathCrossingsDetected;
        this.pathCrossingsResidual = freeze(pathCrossingsResidual);
        this.pathHullPenetrations = pathHullPenetrations;
    }

    public static EdgeRouteDiagnostics empty() {
        return new EdgeRouteDiagnostics(0, 0, 0, 0, 0, Collections.<String>emptyList(), 0);
    }

    public int edgesStraight() {
        return edgesStraight;
    }

    public int edgesBow() {
        return edgesBow;
    }

    public int edgesDogleg() {
        return edgesDogleg;
    }

    public int edgesFallback() {
        return edgesFallback;
    }

    public int pathCrossingsDetected() {
        return pathCrossingsDetected;
    }

    public List<String> pathCrossingsResidual() {
        return pathCrossingsResidual;
    }

    public int pathHullPenetrations() {
        return pathHullPenetrations;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof EdgeRouteDiagnostics)) {
            return false;
        }
        final EdgeRouteDiagnostics that = (EdgeRouteDiagnostics) other;
        return edgesStraight == that.edgesStraight && edgesBow == that.edgesBow
            && edgesDogleg == that.edgesDogleg && edgesFallback == that.edgesFallback
            && pathCrossingsDetected == that.pathCrossingsDetected
            && pathHullPenetrations == that.pathHullPenetrations
            && pathCrossingsResidual.equals(that.pathCrossingsResidual);
    }

    @Override
    public int hashCode() {
        return Objects.hash(edgesStraight, edgesBow, edgesDogleg, edgesFallback, pathCrossingsDetected,
            pathCrossingsResidual, pathHullPenetrations);
    }

    private static void requireNonnegative(final int value, final String role) {
        if (value < 0) {
            throw new IllegalArgumentException(role + " must be nonnegative but was " + value);
        }
    }

    private static List<String> freeze(final List<String> values) {
        Objects.requireNonNull(values, "pathCrossingsResidual");
        final List<String> copy = new ArrayList<String>(values.size());
        for (final String value : values) {
            copy.add(Objects.requireNonNull(value, "pathCrossingsResidual entry"));
        }
        return Collections.unmodifiableList(copy);
    }
}
