package org.freeplane.plugin.graph.geometry;

import java.awt.geom.Dimension2D;

import org.freeplane.plugin.graph.projection.BoundaryTier;

public interface GeometryTextMetrics {
    Dimension2D measure(String displayText, BoundaryTier tier);
}
