package org.freeplane.plugin.graph.layout.graphstream;

import java.util.Objects;

import org.freeplane.plugin.graph.layout.LayoutCalibration;
import org.freeplane.plugin.graph.layout.LayoutEngine;

public final class GraphStreamLayoutFactory {
    private GraphStreamLayoutFactory() {
    }

    public static LayoutEngine create(final LayoutCalibration calibration) {
        return new GraphStreamLayoutEngine(Objects.requireNonNull(calibration, "calibration"));
    }
}
